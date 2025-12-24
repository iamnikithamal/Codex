package com.codex.stormy.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Image optimization utility for managing project images
 * Provides resizing, compression, and format conversion capabilities
 */
object ImageOptimizer {

    /**
     * Image quality presets for compression
     */
    enum class Quality(val value: Int, val displayName: String) {
        LOW(50, "Low (50%)"),
        MEDIUM(70, "Medium (70%)"),
        HIGH(85, "High (85%)"),
        MAXIMUM(95, "Maximum (95%)")
    }

    /**
     * Common image size presets
     */
    enum class SizePreset(val maxDimension: Int, val displayName: String) {
        THUMBNAIL(150, "Thumbnail (150px)"),
        SMALL(320, "Small (320px)"),
        MEDIUM(640, "Medium (640px)"),
        LARGE(1024, "Large (1024px)"),
        HD(1920, "HD (1920px)"),
        ORIGINAL(0, "Original")
    }

    /**
     * Result of image optimization
     */
    data class OptimizationResult(
        val success: Boolean,
        val outputFile: File? = null,
        val originalSize: Long = 0,
        val optimizedSize: Long = 0,
        val originalDimensions: Pair<Int, Int>? = null,
        val optimizedDimensions: Pair<Int, Int>? = null,
        val errorMessage: String? = null
    ) {
        val savedBytes: Long get() = originalSize - optimizedSize
        val savedPercentage: Float get() = if (originalSize > 0) {
            ((originalSize - optimizedSize).toFloat() / originalSize * 100)
        } else 0f
    }

    /**
     * Image information
     */
    data class ImageInfo(
        val width: Int,
        val height: Int,
        val fileSize: Long,
        val format: String,
        val aspectRatio: Float
    ) {
        val resolution: String get() = "${width}x${height}"
        val formattedSize: String get() = FileUtils.formatFileSize(fileSize)
    }

    /**
     * Get image information without loading full bitmap
     */
    suspend fun getImageInfo(file: File): ImageInfo? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return@withContext null
            }

            ImageInfo(
                width = options.outWidth,
                height = options.outHeight,
                fileSize = file.length(),
                format = file.extension.uppercase(),
                aspectRatio = options.outWidth.toFloat() / options.outHeight
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Optimize image with specified settings
     */
    suspend fun optimizeImage(
        inputFile: File,
        outputFile: File,
        maxDimension: Int = 0,
        quality: Int = 85,
        outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): OptimizationResult = withContext(Dispatchers.IO) {
        try {
            // Get original dimensions
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, boundsOptions)
            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Invalid image file"
                )
            }

            // Calculate sample size for memory efficiency
            val sampleSize = calculateSampleSize(
                originalWidth,
                originalHeight,
                if (maxDimension > 0) maxDimension else max(originalWidth, originalHeight)
            )

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
                ?: return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Failed to decode image"
                )

            // Scale to exact dimensions if needed
            val scaledBitmap = if (maxDimension > 0 && (bitmap.width > maxDimension || bitmap.height > maxDimension)) {
                val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height)
                val newWidth = (bitmap.width * scale).roundToInt()
                val newHeight = (bitmap.height * scale).roundToInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                scaled
            } else {
                bitmap
            }

            // Compress and save
            FileOutputStream(outputFile).use { out ->
                scaledBitmap.compress(outputFormat, quality, out)
            }

            val result = OptimizationResult(
                success = true,
                outputFile = outputFile,
                originalSize = inputFile.length(),
                optimizedSize = outputFile.length(),
                originalDimensions = Pair(originalWidth, originalHeight),
                optimizedDimensions = Pair(scaledBitmap.width, scaledBitmap.height)
            )

            scaledBitmap.recycle()
            result
        } catch (e: Exception) {
            OptimizationResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Resize image to specific dimensions
     */
    suspend fun resizeImage(
        inputFile: File,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        maintainAspectRatio: Boolean = true,
        quality: Int = 85
    ): OptimizationResult = withContext(Dispatchers.IO) {
        try {
            // Get original dimensions
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, boundsOptions)
            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Invalid image file"
                )
            }

            // Calculate final dimensions
            val (finalWidth, finalHeight) = if (maintainAspectRatio) {
                calculateAspectRatioDimensions(
                    originalWidth, originalHeight,
                    targetWidth, targetHeight
                )
            } else {
                Pair(targetWidth, targetHeight)
            }

            // Calculate sample size
            val sampleSize = calculateSampleSize(originalWidth, originalHeight, max(finalWidth, finalHeight))

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
                ?: return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Failed to decode image"
                )

            // Scale to exact dimensions
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }

            // Determine output format based on file extension
            val format = when (outputFile.extension.lowercase()) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.JPEG
            }

            // Compress and save
            FileOutputStream(outputFile).use { out ->
                scaledBitmap.compress(format, quality, out)
            }

            val result = OptimizationResult(
                success = true,
                outputFile = outputFile,
                originalSize = inputFile.length(),
                optimizedSize = outputFile.length(),
                originalDimensions = Pair(originalWidth, originalHeight),
                optimizedDimensions = Pair(scaledBitmap.width, scaledBitmap.height)
            )

            scaledBitmap.recycle()
            result
        } catch (e: Exception) {
            OptimizationResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Compress image without resizing
     */
    suspend fun compressImage(
        inputFile: File,
        outputFile: File,
        quality: Int = 70
    ): OptimizationResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
                ?: return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Failed to decode image"
                )

            // Determine output format based on file extension
            val format = when (outputFile.extension.lowercase()) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.JPEG
            }

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(format, quality, out)
            }

            val result = OptimizationResult(
                success = true,
                outputFile = outputFile,
                originalSize = inputFile.length(),
                optimizedSize = outputFile.length(),
                originalDimensions = Pair(bitmap.width, bitmap.height),
                optimizedDimensions = Pair(bitmap.width, bitmap.height)
            )

            bitmap.recycle()
            result
        } catch (e: Exception) {
            OptimizationResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Convert image to different format
     */
    suspend fun convertFormat(
        inputFile: File,
        outputFile: File,
        format: Bitmap.CompressFormat,
        quality: Int = 85
    ): OptimizationResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
                ?: return@withContext OptimizationResult(
                    success = false,
                    errorMessage = "Failed to decode image"
                )

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(format, quality, out)
            }

            val result = OptimizationResult(
                success = true,
                outputFile = outputFile,
                originalSize = inputFile.length(),
                optimizedSize = outputFile.length(),
                originalDimensions = Pair(bitmap.width, bitmap.height),
                optimizedDimensions = Pair(bitmap.width, bitmap.height)
            )

            bitmap.recycle()
            result
        } catch (e: Exception) {
            OptimizationResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Generate thumbnail for preview
     */
    suspend fun generateThumbnail(
        inputFile: File,
        outputFile: File,
        size: Int = 150
    ): OptimizationResult {
        return optimizeImage(
            inputFile = inputFile,
            outputFile = outputFile,
            maxDimension = size,
            quality = 70,
            outputFormat = Bitmap.CompressFormat.JPEG
        )
    }

    /**
     * Batch optimize multiple images
     */
    suspend fun batchOptimize(
        files: List<File>,
        outputDir: File,
        maxDimension: Int = 1024,
        quality: Int = 85,
        onProgress: ((Int, Int, OptimizationResult) -> Unit)? = null
    ): List<OptimizationResult> = withContext(Dispatchers.IO) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        files.mapIndexed { index, file ->
            val outputFile = File(outputDir, "optimized_${file.name}")
            val result = optimizeImage(
                inputFile = file,
                outputFile = outputFile,
                maxDimension = maxDimension,
                quality = quality
            )
            onProgress?.invoke(index + 1, files.size, result)
            result
        }
    }

    /**
     * Calculate optimal sample size for memory-efficient decoding
     */
    private fun calculateSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        targetMaxDimension: Int
    ): Int {
        var sampleSize = 1
        if (originalWidth > targetMaxDimension || originalHeight > targetMaxDimension) {
            val halfWidth = originalWidth / 2
            val halfHeight = originalHeight / 2
            while ((halfWidth / sampleSize) >= targetMaxDimension &&
                   (halfHeight / sampleSize) >= targetMaxDimension) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    /**
     * Calculate dimensions that maintain aspect ratio within bounds
     */
    private fun calculateAspectRatioDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Int, Int> {
        val aspectRatio = originalWidth.toFloat() / originalHeight
        return if (aspectRatio > targetWidth.toFloat() / targetHeight) {
            // Width is the limiting factor
            Pair(targetWidth, (targetWidth / aspectRatio).roundToInt())
        } else {
            // Height is the limiting factor
            Pair((targetHeight * aspectRatio).roundToInt(), targetHeight)
        }
    }

    /**
     * Check if file is a supported image format
     */
    fun isImageFile(file: File): Boolean {
        return file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    }

    /**
     * Get recommended compression format for a file
     */
    fun getRecommendedFormat(file: File): Bitmap.CompressFormat {
        return when (file.extension.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
    }
}
