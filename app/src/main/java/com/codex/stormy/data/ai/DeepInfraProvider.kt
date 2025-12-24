package com.codex.stormy.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * AI provider implementation for DeepInfra API
 * Uses the free/public DeepInfra chat endpoint (no API key required)
 * Based on gpt4free implementation patterns
 */
class DeepInfraProvider(
    private val apiKey: String = ""
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // Cookie jar for session management
    private val cookieJar = object : CookieJar {
        private val cookies = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.getOrPut(url.host) { mutableListOf() }.apply {
                clear()
                addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookies[url.host] ?: emptyList()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .followRedirects(true)
        .build()

    // Use the free chat endpoint by default, fall back to API if key is provided
    private val useApiEndpoint: Boolean get() = apiKey.isNotBlank()
    private val baseUrl: String get() = if (useApiEndpoint) {
        AiProvider.DEEPINFRA.baseUrl
    } else {
        "https://api.deepinfra.com/v1/openai"
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val ORIGIN = "https://deepinfra.com"
        private const val REFERER = "https://deepinfra.com/"
    }

    /**
     * Generate a unique chat ID for session tracking
     */
    private fun generateChatId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Send a chat completion request with streaming response
     */
    fun streamChatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Flow<StreamEvent> = callbackFlow {
        val request = ChatCompletionRequest(
            model = model.id,
            messages = messages,
            stream = true,
            temperature = temperature,
            maxTokens = maxTokens,
            tools = if (model.supportsToolCalls && tools != null) tools else null,
            toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null
        )

        val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        // Build request with appropriate headers based on whether we're using API key or free endpoint
        val httpRequestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("User-Agent", USER_AGENT)
            .post(requestBody)

        // Add authentication and origin headers
        if (useApiEndpoint) {
            httpRequestBuilder.addHeader("Authorization", "Bearer $apiKey")
        } else {
            // Free endpoint requires these headers for proper operation
            httpRequestBuilder
                .addHeader("Origin", ORIGIN)
                .addHeader("Referer", REFERER)
                .addHeader("X-Deepinfra-Source", "web-page")
        }

        val httpRequest = httpRequestBuilder.build()

        val eventSourceListener = object : EventSourceListener() {
            private var accumulatedContent = StringBuilder()
            private var accumulatedReasoning = StringBuilder()
            private val toolCallsMap = mutableMapOf<Int, ToolCallAccumulator>()

            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(StreamEvent.Started)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    // Emit final tool calls if any
                    if (toolCallsMap.isNotEmpty()) {
                        val toolCalls = toolCallsMap.values.map { acc ->
                            ToolCallResponse(
                                id = acc.id,
                                type = "function",
                                function = FunctionCall(
                                    name = acc.name,
                                    arguments = acc.arguments.toString()
                                )
                            )
                        }
                        trySend(StreamEvent.ToolCalls(toolCalls))
                    }
                    trySend(StreamEvent.Completed)
                    return
                }

                try {
                    val chunk = json.decodeFromString(StreamChunk.serializer(), data)
                    val delta = chunk.choices.firstOrNull()?.delta

                    delta?.content?.let { content ->
                        accumulatedContent.append(content)
                        trySend(StreamEvent.ContentDelta(content))
                    }

                    // Handle reasoning content for thinking models
                    delta?.reasoningContent?.let { reasoning ->
                        accumulatedReasoning.append(reasoning)
                        trySend(StreamEvent.ReasoningDelta(reasoning))
                    }

                    // Handle tool calls
                    delta?.toolCalls?.forEach { toolCallDelta ->
                        val index = toolCallDelta.index
                        val accumulator = toolCallsMap.getOrPut(index) {
                            ToolCallAccumulator()
                        }

                        toolCallDelta.id?.let { accumulator.id = it }
                        toolCallDelta.function?.name?.let { accumulator.name = it }
                        toolCallDelta.function?.arguments?.let {
                            accumulator.arguments.append(it)
                        }
                    }

                    // Check for finish reason
                    chunk.choices.firstOrNull()?.finishReason?.let { reason ->
                        when (reason) {
                            "stop" -> trySend(StreamEvent.FinishReason(reason))
                            "tool_calls" -> trySend(StreamEvent.FinishReason(reason))
                            "length" -> trySend(StreamEvent.FinishReason(reason))
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors for malformed chunks
                }
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMessage = when {
                    response != null && response.code == 401 -> "Invalid API key"
                    response != null && response.code == 429 -> "Rate limit exceeded. Please try again later."
                    response != null && response.code == 503 -> "Service temporarily unavailable"
                    response != null -> {
                        try {
                            val errorBody = response.body?.string()
                            val apiError = json.decodeFromString(ApiErrorResponse.serializer(), errorBody ?: "")
                            apiError.error?.message ?: "Request failed with status ${response.code}"
                        } catch (e: Exception) {
                            "Request failed with status ${response.code}"
                        }
                    }
                    t != null -> t.message ?: "Network error"
                    else -> "Unknown error"
                }
                trySend(StreamEvent.Error(errorMessage))
                channel.close()
            }
        }

        val eventSource = EventSources.createFactory(client)
            .newEventSource(httpRequest, eventSourceListener)

        awaitClose {
            eventSource.cancel()
        }
    }

    /**
     * Send a non-streaming chat completion request
     */
    suspend fun chatCompletion(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ChatCompletionRequest(
                model = model.id,
                messages = messages,
                stream = false,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = if (model.supportsToolCalls && tools != null) tools else null,
                toolChoice = if (model.supportsToolCalls && tools != null) "auto" else null
            )

            val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            // Build request with appropriate headers based on whether we're using API key or free endpoint
            val httpRequestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", USER_AGENT)
                .post(requestBody)

            // Add authentication and origin headers
            if (useApiEndpoint) {
                httpRequestBuilder.addHeader("Authorization", "Bearer $apiKey")
            } else {
                // Free endpoint requires these headers for proper operation
                httpRequestBuilder
                    .addHeader("Origin", ORIGIN)
                    .addHeader("Referer", REFERER)
                    .addHeader("X-Deepinfra-Source", "web-page")
            }

            val httpRequest = httpRequestBuilder.build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val apiError = try {
                    json.decodeFromString(ApiErrorResponse.serializer(), errorBody ?: "")
                } catch (e: Exception) {
                    null
                }
                return@withContext Result.failure(
                    IOException(apiError?.error?.message ?: "Request failed: ${response.code}")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val chatResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
            Result.success(chatResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )
}

/**
 * Events emitted during streaming
 */
sealed class StreamEvent {
    data object Started : StreamEvent()
    data class ContentDelta(val content: String) : StreamEvent()
    data class ReasoningDelta(val reasoning: String) : StreamEvent()
    data class ToolCalls(val toolCalls: List<ToolCallResponse>) : StreamEvent()
    data class FinishReason(val reason: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Completed : StreamEvent()
}
