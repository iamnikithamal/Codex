package com.codex.stormy.utils

import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Comprehensive cache management system for CodeX IDE.
 * Provides memory-efficient caching for files, search results, and computed data.
 */
object CacheManager {

    // Cache configuration
    private const val DEFAULT_MAX_FILE_CACHE_SIZE = 50 // Max files in memory
    private const val DEFAULT_MAX_CONTENT_SIZE = 5 * 1024 * 1024 // 5MB total content
    private const val DEFAULT_FILE_CONTENT_MAX_SIZE = 512 * 1024 // 512KB per file
    private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

    // Cache statistics
    private var cacheHits = 0L
    private var cacheMisses = 0L

    /**
     * Memory-efficient LRU cache for file contents.
     * Automatically evicts oldest entries when memory pressure is high.
     */
    private val fileContentCache = object : LruCache<String, CachedFileContent>(DEFAULT_MAX_FILE_CACHE_SIZE) {
        override fun sizeOf(key: String, value: CachedFileContent): Int {
            // Return size in KB for more granular control
            return (value.content.length / 1024).coerceAtLeast(1)
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CachedFileContent,
            newValue: CachedFileContent?
        ) {
            if (evicted) {
                android.util.Log.d("CacheManager", "Evicted file from cache: $key")
            }
        }
    }

    /**
     * Cache for search results with automatic expiration
     */
    private val searchResultCache = ConcurrentHashMap<String, CachedSearchResult>()

    /**
     * Cache for computed values (syntax highlighting, parsing results, etc.)
     */
    private val computedCache = ConcurrentHashMap<String, CachedComputedValue>()

    /**
     * Cache for project file trees
     */
    private val fileTreeCache = ConcurrentHashMap<String, CachedFileTree>()

    // Mutex for thread-safe operations
    private val mutex = Mutex()

    // Cleanup scope
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cleanupJob: Job? = null

    init {
        startPeriodicCleanup()
    }

    // ========== File Content Cache ==========

    /**
     * Get cached file content or load it if not cached.
     * Thread-safe and memory-efficient.
     */
    suspend fun getFileContent(file: File): Result<String> = withContext(Dispatchers.IO) {
        val key = file.absolutePath

        // Check cache first
        fileContentCache.get(key)?.let { cached ->
            if (cached.isValid(file)) {
                cacheHits++
                return@withContext Result.success(cached.content)
            } else {
                fileContentCache.remove(key)
            }
        }

        cacheMisses++

        // Load from disk
        try {
            val content = file.readText()

            // Only cache if file is not too large
            if (content.length <= DEFAULT_FILE_CONTENT_MAX_SIZE) {
                val cachedContent = CachedFileContent(
                    content = content,
                    lastModified = file.lastModified(),
                    size = file.length()
                )
                fileContentCache.put(key, cachedContent)
            }

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Invalidate cached file content when file is modified.
     */
    fun invalidateFileCache(filePath: String) {
        fileContentCache.remove(filePath)
    }

    /**
     * Invalidate all files in a directory (useful after batch operations).
     */
    fun invalidateDirectoryCache(directoryPath: String) {
        val keysToRemove = fileContentCache.snapshot().keys.filter {
            it.startsWith(directoryPath)
        }
        keysToRemove.forEach { fileContentCache.remove(it) }
    }

    // ========== Search Result Cache ==========

    /**
     * Cache search results for quick repeated searches.
     */
    fun cacheSearchResult(
        projectPath: String,
        query: String,
        results: List<SearchResult>
    ) {
        val key = buildSearchKey(projectPath, query)
        searchResultCache[key] = CachedSearchResult(
            results = results,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get cached search results if available and valid.
     */
    fun getCachedSearchResults(projectPath: String, query: String): List<SearchResult>? {
        val key = buildSearchKey(projectPath, query)
        return searchResultCache[key]?.let { cached ->
            if (cached.isValid()) {
                cacheHits++
                cached.results
            } else {
                searchResultCache.remove(key)
                cacheMisses++
                null
            }
        }
    }

    /**
     * Invalidate search cache for a project (after file changes).
     */
    fun invalidateSearchCache(projectPath: String) {
        val keysToRemove = searchResultCache.keys.filter { it.startsWith(projectPath) }
        keysToRemove.forEach { searchResultCache.remove(it) }
    }

    // ========== Computed Value Cache ==========

    /**
     * Cache computed values like syntax highlighting tokens.
     */
    fun <T> cacheComputedValue(key: String, value: T, ttlMs: Long = CACHE_EXPIRY_MS) {
        computedCache[key] = CachedComputedValue(
            value = value as Any,
            timestamp = System.currentTimeMillis(),
            ttlMs = ttlMs
        )
    }

    /**
     * Get cached computed value.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getCachedComputedValue(key: String): T? {
        return computedCache[key]?.let { cached ->
            if (cached.isValid()) {
                cacheHits++
                cached.value as? T
            } else {
                computedCache.remove(key)
                cacheMisses++
                null
            }
        }
    }

    /**
     * Invalidate computed cache for specific key pattern.
     */
    fun invalidateComputedCache(keyPattern: String) {
        val keysToRemove = computedCache.keys.filter { it.contains(keyPattern) }
        keysToRemove.forEach { computedCache.remove(it) }
    }

    // ========== File Tree Cache ==========

    /**
     * Cache project file tree for quick navigation.
     */
    suspend fun cacheFileTree(projectPath: String, tree: FileTreeNode) = mutex.withLock {
        fileTreeCache[projectPath] = CachedFileTree(
            root = tree,
            timestamp = System.currentTimeMillis(),
            fileCount = countFiles(tree)
        )
    }

    /**
     * Get cached file tree if valid.
     */
    fun getCachedFileTree(projectPath: String): FileTreeNode? {
        return fileTreeCache[projectPath]?.let { cached ->
            if (cached.isValid()) {
                cacheHits++
                cached.root
            } else {
                fileTreeCache.remove(projectPath)
                cacheMisses++
                null
            }
        }
    }

    /**
     * Invalidate file tree cache for a project.
     */
    fun invalidateFileTreeCache(projectPath: String) {
        fileTreeCache.remove(projectPath)
    }

    // ========== Cache Statistics ==========

    /**
     * Get cache statistics for monitoring.
     */
    fun getCacheStats(): CacheStats {
        val totalRequests = cacheHits + cacheMisses
        val hitRate = if (totalRequests > 0) {
            (cacheHits.toDouble() / totalRequests * 100).toInt()
        } else 0

        return CacheStats(
            fileContentCacheSize = fileContentCache.size(),
            searchCacheSize = searchResultCache.size,
            computedCacheSize = computedCache.size,
            fileTreeCacheSize = fileTreeCache.size,
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            hitRatePercent = hitRate
        )
    }

    /**
     * Clear all caches. Use sparingly as this impacts performance.
     */
    fun clearAllCaches() {
        fileContentCache.evictAll()
        searchResultCache.clear()
        computedCache.clear()
        fileTreeCache.clear()
        cacheHits = 0
        cacheMisses = 0
        android.util.Log.i("CacheManager", "All caches cleared")
    }

    /**
     * Trim caches to reduce memory usage.
     */
    fun trimCaches() {
        // Remove expired entries
        val now = System.currentTimeMillis()

        searchResultCache.entries.removeIf { !it.value.isValid() }
        computedCache.entries.removeIf { !it.value.isValid() }
        fileTreeCache.entries.removeIf { !it.value.isValid() }

        // Trim LRU cache
        fileContentCache.trimToSize(fileContentCache.maxSize() / 2)

        android.util.Log.d("CacheManager", "Caches trimmed")
    }

    // ========== Private Helpers ==========

    private fun buildSearchKey(projectPath: String, query: String): String {
        return "$projectPath:search:${query.hashCode()}"
    }

    private fun countFiles(node: FileTreeNode): Int {
        return if (node.isDirectory) {
            node.children.sumOf { countFiles(it) }
        } else {
            1
        }
    }

    private fun startPeriodicCleanup() {
        cleanupJob?.cancel()
        cleanupJob = cleanupScope.launch {
            while (true) {
                delay(CACHE_EXPIRY_MS)
                trimCaches()
            }
        }
    }
}

/**
 * Cached file content with validation.
 */
data class CachedFileContent(
    val content: String,
    val lastModified: Long,
    val size: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if cache is still valid by comparing file modification time.
     */
    fun isValid(file: File): Boolean {
        return file.exists() &&
               file.lastModified() == lastModified &&
               file.length() == size
    }
}

/**
 * Cached search result with TTL.
 */
data class CachedSearchResult(
    val results: List<SearchResult>,
    val timestamp: Long
) {
    fun isValid(): Boolean {
        return System.currentTimeMillis() - timestamp < CacheManager.CACHE_EXPIRY_MS_PUBLIC
    }

    companion object {
        private const val CACHE_EXPIRY_MS = 60 * 1000L // 1 minute for search results
    }
}

/**
 * Search result data class.
 */
data class SearchResult(
    val filePath: String,
    val fileName: String,
    val lineNumber: Int,
    val lineContent: String,
    val matchStart: Int,
    val matchEnd: Int
)

/**
 * Cached computed value with custom TTL.
 */
data class CachedComputedValue(
    val value: Any,
    val timestamp: Long,
    val ttlMs: Long
) {
    fun isValid(): Boolean {
        return System.currentTimeMillis() - timestamp < ttlMs
    }
}

/**
 * Cached file tree.
 */
data class CachedFileTree(
    val root: FileTreeNode,
    val timestamp: Long,
    val fileCount: Int
) {
    fun isValid(): Boolean {
        return System.currentTimeMillis() - timestamp < CacheManager.CACHE_EXPIRY_MS_PUBLIC
    }
}

/**
 * File tree node for caching directory structure.
 */
data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val extension: String = "",
    val children: List<FileTreeNode> = emptyList()
)

/**
 * Cache statistics for monitoring.
 */
data class CacheStats(
    val fileContentCacheSize: Int,
    val searchCacheSize: Int,
    val computedCacheSize: Int,
    val fileTreeCacheSize: Int,
    val cacheHits: Long,
    val cacheMisses: Long,
    val hitRatePercent: Int
)

// Public constant for external access
val CacheManager.CACHE_EXPIRY_MS_PUBLIC: Long
    get() = 5 * 60 * 1000L
