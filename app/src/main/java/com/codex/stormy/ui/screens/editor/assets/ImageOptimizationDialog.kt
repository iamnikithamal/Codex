package com.codex.stormy.ui.screens.editor.assets

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.stormy.ui.theme.PoppinsFontFamily
import com.codex.stormy.utils.FileUtils
import com.codex.stormy.utils.ImageOptimizer
import kotlinx.coroutines.launch
import java.io.File

/**
 * Optimization mode options
 */
enum class OptimizationMode {
    RESIZE, COMPRESS, CONVERT
}

/**
 * Dialog for image optimization with preview and options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageOptimizationDialog(
    imageFile: File,
    projectPath: String,
    onDismiss: () -> Unit,
    onOptimized: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var imageInfo by remember { mutableStateOf<ImageOptimizer.ImageInfo?>(null) }
    var selectedMode by remember { mutableStateOf(OptimizationMode.RESIZE) }
    var quality by remember { mutableFloatStateOf(85f) }
    var selectedSizePreset by remember { mutableStateOf(ImageOptimizer.SizePreset.MEDIUM) }
    var customWidth by remember { mutableStateOf("") }
    var customHeight by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("JPEG") }
    var maintainAspectRatio by remember { mutableStateOf(true) }

    // Processing state
    var isProcessing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImageOptimizer.OptimizationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load image info
    LaunchedEffect(imageFile) {
        imageInfo = ImageOptimizer.getImageInfo(imageFile)
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoSizeSelectLarge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Optimize Image",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image preview and info
                ImagePreviewCard(
                    imageFile = imageFile,
                    imageInfo = imageInfo
                )

                // Mode selector
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OptimizationMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = OptimizationMode.entries.size
                            ),
                            label = {
                                Text(
                                    text = when (mode) {
                                        OptimizationMode.RESIZE -> "Resize"
                                        OptimizationMode.COMPRESS -> "Compress"
                                        OptimizationMode.CONVERT -> "Convert"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                // Mode-specific options
                when (selectedMode) {
                    OptimizationMode.RESIZE -> {
                        ResizeOptions(
                            selectedPreset = selectedSizePreset,
                            onPresetSelected = { selectedSizePreset = it },
                            customWidth = customWidth,
                            customHeight = customHeight,
                            onCustomWidthChange = { customWidth = it },
                            onCustomHeightChange = { customHeight = it },
                            maintainAspectRatio = maintainAspectRatio,
                            originalWidth = imageInfo?.width ?: 0,
                            originalHeight = imageInfo?.height ?: 0
                        )

                        QualitySlider(
                            quality = quality,
                            onQualityChange = { quality = it }
                        )
                    }
                    OptimizationMode.COMPRESS -> {
                        QualitySlider(
                            quality = quality,
                            onQualityChange = { quality = it }
                        )

                        Text(
                            text = "Compression reduces file size while maintaining dimensions. Lower quality = smaller file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OptimizationMode.CONVERT -> {
                        FormatSelector(
                            selectedFormat = selectedFormat,
                            onFormatSelected = { selectedFormat = it }
                        )

                        QualitySlider(
                            quality = quality,
                            onQualityChange = { quality = it }
                        )
                    }
                }

                // Processing/Result state
                AnimatedVisibility(
                    visible = isProcessing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ProcessingIndicator()
                }

                result?.let { optResult ->
                    if (optResult.success) {
                        OptimizationResultCard(result = optResult)
                    }
                }

                error?.let { errorMsg ->
                    ErrorCard(message = errorMsg)
                }
            }
        },
        confirmButton = {
            if (result?.success == true) {
                TextButton(
                    onClick = {
                        result?.outputFile?.let { onOptimized(it) }
                        onDismiss()
                    }
                ) {
                    Text("Done")
                }
            } else {
                TextButton(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            error = null
                            result = null

                            val outputExtension = when {
                                selectedMode == OptimizationMode.CONVERT -> selectedFormat.lowercase()
                                else -> imageFile.extension
                            }
                            val outputFile = File(
                                imageFile.parentFile,
                                "${imageFile.nameWithoutExtension}_optimized.$outputExtension"
                            )

                            val optimizationResult = when (selectedMode) {
                                OptimizationMode.RESIZE -> {
                                    val maxDim = if (selectedSizePreset == ImageOptimizer.SizePreset.ORIGINAL) {
                                        customWidth.toIntOrNull() ?: customHeight.toIntOrNull() ?: 0
                                    } else {
                                        selectedSizePreset.maxDimension
                                    }
                                    ImageOptimizer.optimizeImage(
                                        inputFile = imageFile,
                                        outputFile = outputFile,
                                        maxDimension = maxDim,
                                        quality = quality.toInt()
                                    )
                                }
                                OptimizationMode.COMPRESS -> {
                                    ImageOptimizer.compressImage(
                                        inputFile = imageFile,
                                        outputFile = outputFile,
                                        quality = quality.toInt()
                                    )
                                }
                                OptimizationMode.CONVERT -> {
                                    val format = when (selectedFormat) {
                                        "PNG" -> Bitmap.CompressFormat.PNG
                                        "WEBP" -> Bitmap.CompressFormat.WEBP_LOSSY
                                        else -> Bitmap.CompressFormat.JPEG
                                    }
                                    ImageOptimizer.convertFormat(
                                        inputFile = imageFile,
                                        outputFile = outputFile,
                                        format = format,
                                        quality = quality.toInt()
                                    )
                                }
                            }

                            isProcessing = false
                            if (optimizationResult.success) {
                                result = optimizationResult
                            } else {
                                error = optimizationResult.errorMessage ?: "Optimization failed"
                            }
                        }
                    },
                    enabled = !isProcessing && imageInfo != null
                ) {
                    Text("Optimize")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImagePreviewCard(
    imageFile: File,
    imageInfo: ImageOptimizer.ImageInfo?
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preview
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = imageFile.name,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = imageFile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (imageInfo != null) {
                    Text(
                        text = "${imageInfo.resolution} • ${imageInfo.formattedSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizeOptions(
    selectedPreset: ImageOptimizer.SizePreset,
    onPresetSelected: (ImageOptimizer.SizePreset) -> Unit,
    customWidth: String,
    customHeight: String,
    onCustomWidthChange: (String) -> Unit,
    onCustomHeightChange: (String) -> Unit,
    maintainAspectRatio: Boolean,
    originalWidth: Int,
    originalHeight: Int
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Size",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedPreset.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ImageOptimizer.SizePreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.displayName) },
                        onClick = {
                            onPresetSelected(preset)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Custom size inputs (shown when Original is selected)
        AnimatedVisibility(visible = selectedPreset == ImageOptimizer.SizePreset.ORIGINAL) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customWidth,
                    onValueChange = onCustomWidthChange,
                    label = { Text("Width") },
                    placeholder = { Text("$originalWidth") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Text("×", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = customHeight,
                    onValueChange = onCustomHeightChange,
                    label = { Text("Height") },
                    placeholder = { Text("$originalHeight") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun QualitySlider(
    quality: Float,
    onQualityChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quality",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${quality.toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = quality,
            onValueChange = onQualityChange,
            valueRange = 10f..100f,
            steps = 8
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Smaller file",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Higher quality",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSelector(
    selectedFormat: String,
    onFormatSelected: (String) -> Unit
) {
    val formats = listOf("JPEG", "PNG", "WEBP")
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Output Format",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedFormat,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                formats.forEach { format ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(format)
                                Text(
                                    text = when (format) {
                                        "JPEG" -> "Best for photos, smaller files"
                                        "PNG" -> "Lossless, supports transparency"
                                        "WEBP" -> "Modern format, great compression"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onFormatSelected(format)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingIndicator() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Text(
            text = "Optimizing...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptimizationResultCard(result: ImageOptimizer.OptimizationResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Optimization Complete",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Original",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FileUtils.formatFileSize(result.originalSize),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    result.originalDimensions?.let { (w, h) ->
                        Text(
                            text = "${w}×${h}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Optimized",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FileUtils.formatFileSize(result.optimizedSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    result.optimizedDimensions?.let { (w, h) ->
                        Text(
                            text = "${w}×${h}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Saved ${FileUtils.formatFileSize(result.savedBytes)} (${String.format("%.1f", result.savedPercentage)}%)",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
