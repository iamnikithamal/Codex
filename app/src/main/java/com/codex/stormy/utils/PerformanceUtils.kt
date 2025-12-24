package com.codex.stormy.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Performance utilities for CodeX IDE.
 * Provides debouncing, throttling, lazy loading, and profiling tools.
 */
object PerformanceUtils {

    // Profiling enabled flag
    private var profilingEnabled = false

    // Performance metrics storage
    private val metrics = ConcurrentHashMap<String, PerformanceMetric>()

    /**
     * Enable or disable performance profiling.
     */
    fun setProfilingEnabled(enabled: Boolean) {
        profilingEnabled = enabled
        if (!enabled) {
            metrics.clear()
        }
    }

    /**
     * Check if profiling is enabled.
     */
    fun isProfilingEnabled(): Boolean = profilingEnabled

    /**
     * Get all recorded performance metrics.
     */
    fun getMetrics(): Map<String, PerformanceMetric> = metrics.toMap()

    /**
     * Get a specific metric.
     */
    fun getMetric(name: String): PerformanceMetric? = metrics[name]

    /**
     * Clear all metrics.
     */
    fun clearMetrics() {
        metrics.clear()
    }
}

/**
 * Performance metric data class.
 */
data class PerformanceMetric(
    val name: String,
    val totalTime: AtomicLong = AtomicLong(0),
    val callCount: AtomicLong = AtomicLong(0),
    val minTime: AtomicLong = AtomicLong(Long.MAX_VALUE),
    val maxTime: AtomicLong = AtomicLong(0)
) {
    val averageTime: Double
        get() = if (callCount.get() > 0) {
            totalTime.get().toDouble() / callCount.get()
        } else 0.0

    fun record(durationMs: Long) {
        totalTime.addAndGet(durationMs)
        callCount.incrementAndGet()
        minTime.updateAndGet { minOf(it, durationMs) }
        maxTime.updateAndGet { maxOf(it, durationMs) }
    }

    override fun toString(): String {
        return "$name: avg=${averageTime.toLong()}ms, " +
               "min=${minTime.get()}ms, max=${maxTime.get()}ms, " +
               "calls=${callCount.get()}"
    }
}

/**
 * Measure and log execution time of a block.
 * Only records metrics when profiling is enabled.
 */
inline fun <T> measureAndLog(name: String, block: () -> T): T {
    return if (PerformanceUtils.isProfilingEnabled()) {
        val result: T
        val duration = measureTimeMillis {
            result = block()
        }

        val metrics = PerformanceUtils.getMetrics().toMutableMap()
        val metric = metrics.getOrPut(name) { PerformanceMetric(name) }
        metric.record(duration)

        android.util.Log.d("Performance", "$name took ${duration}ms")
        result
    } else {
        block()
    }
}

/**
 * Suspend version of measureAndLog.
 */
suspend inline fun <T> measureAndLogSuspend(name: String, crossinline block: suspend () -> T): T {
    return if (PerformanceUtils.isProfilingEnabled()) {
        val result: T
        val duration = measureTimeMillis {
            result = block()
        }

        android.util.Log.d("Performance", "$name took ${duration}ms")
        result
    } else {
        block()
    }
}

/**
 * Debouncer class for efficient event handling.
 * Delays execution until a quiet period has elapsed.
 */
class Debouncer(
    private val scope: CoroutineScope,
    private val delayMs: Long = 300L
) {
    private var job: Job? = null
    private val mutex = Mutex()

    /**
     * Debounce a suspending action.
     */
    fun debounce(action: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                job?.cancel()
                job = scope.launch {
                    delay(delayMs)
                    action()
                }
            }
        }
    }

    /**
     * Cancel any pending debounced action.
     */
    fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * Throttler class for rate-limiting operations.
 * Ensures minimum time between executions.
 */
class Throttler(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 100L
) {
    private var lastExecutionTime = 0L
    private var pendingAction: (suspend () -> Unit)? = null
    private var job: Job? = null
    private val mutex = Mutex()

    /**
     * Throttle a suspending action.
     * Executes immediately if enough time has passed, otherwise schedules for later.
     */
    fun throttle(action: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val timeSinceLastExecution = now - lastExecutionTime

                if (timeSinceLastExecution >= intervalMs) {
                    lastExecutionTime = now
                    action()
                } else {
                    pendingAction = action
                    if (job == null || job?.isCompleted == true) {
                        job = scope.launch {
                            delay(intervalMs - timeSinceLastExecution)
                            mutex.withLock {
                                lastExecutionTime = System.currentTimeMillis()
                                pendingAction?.invoke()
                                pendingAction = null
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Cancel any pending throttled action.
     */
    fun cancel() {
        job?.cancel()
        job = null
        pendingAction = null
    }
}

/**
 * Coalescer for batching multiple rapid updates into single operations.
 * Useful for file system watchers or rapid state changes.
 */
class UpdateCoalescer<T>(
    private val scope: CoroutineScope,
    private val delayMs: Long = 500L,
    private val onCoalesced: suspend (List<T>) -> Unit
) {
    private val pendingUpdates = mutableListOf<T>()
    private var job: Job? = null
    private val mutex = Mutex()

    /**
     * Add an update to be coalesced.
     */
    fun addUpdate(update: T) {
        scope.launch {
            mutex.withLock {
                pendingUpdates.add(update)
                job?.cancel()
                job = scope.launch {
                    delay(delayMs)
                    mutex.withLock {
                        if (pendingUpdates.isNotEmpty()) {
                            val updates = pendingUpdates.toList()
                            pendingUpdates.clear()
                            onCoalesced(updates)
                        }
                    }
                }
            }
        }
    }

    /**
     * Force immediate processing of pending updates.
     */
    suspend fun flush() {
        mutex.withLock {
            job?.cancel()
            if (pendingUpdates.isNotEmpty()) {
                val updates = pendingUpdates.toList()
                pendingUpdates.clear()
                onCoalesced(updates)
            }
        }
    }

    /**
     * Cancel and clear all pending updates.
     */
    fun cancel() {
        job?.cancel()
        scope.launch {
            mutex.withLock {
                pendingUpdates.clear()
            }
        }
    }
}

/**
 * Lazy loader for paginated data with prefetching.
 */
class LazyLoader<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int = 50,
    private val prefetchThreshold: Int = 10,
    private val loadPage: suspend (page: Int, size: Int) -> List<T>
) {
    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var currentPage = 0
    private var isLoadingPage = false
    private val mutex = Mutex()

    /**
     * Load initial page of data.
     */
    suspend fun loadInitial() {
        mutex.withLock {
            currentPage = 0
            _items.value = emptyList()
            _hasMore.value = true
        }
        loadNextPage()
    }

    /**
     * Load next page of data.
     */
    suspend fun loadNextPage() {
        mutex.withLock {
            if (isLoadingPage || !_hasMore.value) return

            isLoadingPage = true
            _isLoading.value = true
        }

        try {
            val newItems = loadPage(currentPage, pageSize)

            mutex.withLock {
                _items.value = _items.value + newItems
                _hasMore.value = newItems.size >= pageSize
                currentPage++
            }
        } finally {
            mutex.withLock {
                isLoadingPage = false
                _isLoading.value = false
            }
        }
    }

    /**
     * Check if prefetch is needed based on visible item position.
     */
    fun checkPrefetch(visibleItemIndex: Int) {
        val totalItems = _items.value.size
        if (visibleItemIndex >= totalItems - prefetchThreshold && _hasMore.value && !isLoadingPage) {
            scope.launch {
                loadNextPage()
            }
        }
    }

    /**
     * Refresh all data.
     */
    suspend fun refresh() {
        loadInitial()
    }
}

/**
 * Memoization helper for expensive computations.
 * Thread-safe with automatic cache invalidation.
 */
class Memoizer<K, V>(
    private val maxSize: Int = 100,
    private val ttlMs: Long = 5 * 60 * 1000L, // 5 minutes default
    private val compute: suspend (K) -> V
) {
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long
    ) {
        fun isValid(ttlMs: Long): Boolean {
            return System.currentTimeMillis() - timestamp < ttlMs
        }
    }

    private val cache = object : LinkedHashMap<K, CacheEntry<V>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheEntry<V>>?): Boolean {
            return size > maxSize
        }
    }

    private val mutex = Mutex()

    /**
     * Get memoized value or compute if not cached.
     */
    suspend fun get(key: K): V {
        mutex.withLock {
            cache[key]?.let { entry ->
                if (entry.isValid(ttlMs)) {
                    return entry.value
                } else {
                    cache.remove(key)
                }
            }
        }

        val value = compute(key)

        mutex.withLock {
            cache[key] = CacheEntry(value, System.currentTimeMillis())
        }

        return value
    }

    /**
     * Invalidate a specific key.
     */
    suspend fun invalidate(key: K) {
        mutex.withLock {
            cache.remove(key)
        }
    }

    /**
     * Invalidate all cached values.
     */
    suspend fun invalidateAll() {
        mutex.withLock {
            cache.clear()
        }
    }

    /**
     * Get cache size.
     */
    fun size(): Int = cache.size
}

/**
 * Chunked processor for large collections.
 * Processes items in chunks to prevent UI freezes.
 */
class ChunkedProcessor<T, R>(
    private val chunkSize: Int = 100,
    private val delayBetweenChunks: Long = 16L // ~1 frame at 60fps
) {
    /**
     * Process items in chunks, yielding between chunks for UI responsiveness.
     */
    suspend fun process(
        items: List<T>,
        transform: suspend (T) -> R,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<R> = withContext(Dispatchers.Default) {
        val results = mutableListOf<R>()
        val chunks = items.chunked(chunkSize)
        var processed = 0

        chunks.forEach { chunk ->
            chunk.forEach { item ->
                results.add(transform(item))
                processed++
            }
            onProgress?.invoke(processed, items.size)

            // Yield to allow UI updates
            if (delayBetweenChunks > 0) {
                delay(delayBetweenChunks)
            }
        }

        results
    }

    /**
     * Filter items in chunks.
     */
    suspend fun filter(
        items: List<T>,
        predicate: suspend (T) -> Boolean,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<T> = withContext(Dispatchers.Default) {
        val results = mutableListOf<T>()
        val chunks = items.chunked(chunkSize)
        var processed = 0

        chunks.forEach { chunk ->
            chunk.forEach { item ->
                if (predicate(item)) {
                    results.add(item)
                }
                processed++
            }
            onProgress?.invoke(processed, items.size)

            if (delayBetweenChunks > 0) {
                delay(delayBetweenChunks)
            }
        }

        results
    }
}

/**
 * Flow extensions for performance optimization.
 */

/**
 * Debounce flow emissions.
 */
fun <T> Flow<T>.debouncedDistinct(timeoutMs: Long = 300L): Flow<T> {
    return this
        .debounce(timeoutMs)
        .distinctUntilChanged()
}

/**
 * Conflate and process on IO dispatcher for heavy operations.
 */
fun <T, R> Flow<T>.conflateAndProcess(transform: suspend (T) -> R): Flow<R> {
    return this
        .conflate()
        .map { transform(it) }
        .flowOn(Dispatchers.IO)
}

/**
 * Sample flow at specified interval.
 */
fun <T> Flow<T>.sample(periodMs: Long): Flow<T> = flow {
    var lastEmitTime = 0L
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmitTime >= periodMs) {
            emit(value)
            lastEmitTime = currentTime
        }
    }
}

/**
 * Memory pressure watcher for adaptive caching.
 */
object MemoryPressureWatcher {
    private val _memoryPressure = MutableStateFlow(MemoryPressureLevel.NORMAL)
    val memoryPressure: StateFlow<MemoryPressureLevel> = _memoryPressure.asStateFlow()

    /**
     * Update memory pressure level.
     * Should be called from Application.onTrimMemory()
     */
    fun onTrimMemory(level: Int) {
        _memoryPressure.value = when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryPressureLevel.CRITICAL
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> MemoryPressureLevel.HIGH
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryPressureLevel.MODERATE
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryPressureLevel.LOW
            else -> MemoryPressureLevel.NORMAL
        }

        // Auto-trim caches on high memory pressure
        if (_memoryPressure.value >= MemoryPressureLevel.HIGH) {
            CacheManager.trimCaches()
        }
    }

    /**
     * Check available memory.
     */
    fun getAvailableMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        return maxMemory - usedMemory
    }
}

/**
 * Memory pressure levels.
 */
enum class MemoryPressureLevel {
    NORMAL,
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}
