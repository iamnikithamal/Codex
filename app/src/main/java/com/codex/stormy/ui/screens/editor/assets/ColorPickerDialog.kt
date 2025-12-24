package com.codex.stormy.ui.screens.editor.assets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.PoppinsFontFamily
import kotlin.math.roundToInt

/**
 * Color format options
 */
enum class ColorFormat(val displayName: String) {
    HEX("HEX"),
    RGB("RGB"),
    RGBA("RGBA"),
    HSL("HSL"),
    TAILWIND("Tailwind")
}

/**
 * Color palette categories
 */
enum class ColorPalette(val displayName: String) {
    MATERIAL("Material"),
    TAILWIND("Tailwind"),
    FLAT_UI("Flat UI"),
    SOCIAL("Social"),
    GRADIENTS("Gradients"),
    CUSTOM("Custom")
}

/**
 * Data class for color with various format representations
 */
data class ColorValue(
    val color: Color,
    val hex: String,
    val rgb: String,
    val rgba: String,
    val hsl: String,
    val tailwindClass: String? = null,
    val name: String? = null
)

/**
 * Color Picker Dialog with palettes and custom color selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (ColorValue, ColorFormat) -> Unit,
    initialColor: Color = Color.Blue
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    // State
    var selectedPalette by remember { mutableStateOf(ColorPalette.MATERIAL) }
    var selectedFormat by remember { mutableStateOf(ColorFormat.HEX) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hexInput by remember { mutableStateOf(colorToHex(initialColor)) }

    // HSV values for custom picker
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var alpha by remember { mutableFloatStateOf(1f) }

    // Current color value
    val currentColorValue by remember(selectedColor, alpha) {
        derivedStateOf {
            createColorValue(selectedColor, alpha)
        }
    }

    // Update hex input when color changes
    LaunchedEffect(selectedColor) {
        hexInput = colorToHex(selectedColor)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Color Picker",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Palette tabs
            ScrollableTabRow(
                selectedTabIndex = ColorPalette.entries.indexOf(selectedPalette),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                ColorPalette.entries.forEach { palette ->
                    Tab(
                        selected = selectedPalette == palette,
                        onClick = { selectedPalette = palette },
                        text = {
                            Text(
                                text = palette.displayName,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (selectedPalette == palette) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Color preview and current value
            ColorPreviewCard(
                colorValue = currentColorValue,
                selectedFormat = selectedFormat,
                onCopy = {
                    val value = getColorValueForFormat(currentColorValue, selectedFormat)
                    clipboardManager.setText(AnnotatedString(value))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Format selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorFormat.entries.take(4).forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { selectedFormat = format },
                        label = {
                            Text(
                                text = format.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            // Content based on selected palette
            when (selectedPalette) {
                ColorPalette.CUSTOM -> {
                    CustomColorPicker(
                        hue = hue,
                        saturation = saturation,
                        brightness = brightness,
                        alpha = alpha,
                        onHueChange = {
                            hue = it
                            selectedColor = hsvToColor(hue, saturation, brightness)
                        },
                        onSaturationChange = {
                            saturation = it
                            selectedColor = hsvToColor(hue, saturation, brightness)
                        },
                        onBrightnessChange = {
                            brightness = it
                            selectedColor = hsvToColor(hue, saturation, brightness)
                        },
                        onAlphaChange = { alpha = it },
                        hexInput = hexInput,
                        onHexInputChange = {
                            hexInput = it
                            hexToColor(it)?.let { color ->
                                selectedColor = color
                                val hsv = colorToHsv(color)
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2]
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                ColorPalette.GRADIENTS -> {
                    GradientPalette(
                        onGradientSelected = { gradient ->
                            // For gradients, copy the CSS directly
                            clipboardManager.setText(AnnotatedString(gradient))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    ColorSwatchGrid(
                        colors = getColorsForPalette(selectedPalette),
                        selectedColor = selectedColor,
                        onColorSelected = { colorValue ->
                            selectedColor = colorValue.color
                            val hsv = colorToHsv(colorValue.color)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Action button
            Surface(
                onClick = {
                    onColorSelected(currentColorValue, selectedFormat)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use This Color",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPreviewCard(
    colorValue: ColorValue,
    selectedFormat: ColorFormat,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color preview
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorValue.color)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = colorValue.name ?: "Custom Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = getColorValueForFormat(colorValue, selectedFormat),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CustomColorPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    alpha: Float,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    hexInput: String,
    onHexInputChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hex input
        OutlinedTextField(
            value = hexInput,
            onValueChange = { if (it.length <= 7) onHexInputChange(it.uppercase()) },
            label = { Text("HEX Color") },
            placeholder = { Text("#FF0000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        // Hue slider
        SliderWithLabel(
            label = "Hue",
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
            displayValue = "${hue.roundToInt()}°",
            gradientColors = listOf(
                Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
        )

        // Saturation slider
        SliderWithLabel(
            label = "Saturation",
            value = saturation,
            onValueChange = onSaturationChange,
            valueRange = 0f..1f,
            displayValue = "${(saturation * 100).roundToInt()}%",
            gradientColors = listOf(
                Color.White,
                hsvToColor(hue, 1f, brightness)
            )
        )

        // Brightness slider
        SliderWithLabel(
            label = "Brightness",
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = 0f..1f,
            displayValue = "${(brightness * 100).roundToInt()}%",
            gradientColors = listOf(
                Color.Black,
                hsvToColor(hue, saturation, 1f)
            )
        )

        // Alpha slider
        SliderWithLabel(
            label = "Opacity",
            value = alpha,
            onValueChange = onAlphaChange,
            valueRange = 0f..1f,
            displayValue = "${(alpha * 100).roundToInt()}%",
            gradientColors = listOf(
                hsvToColor(hue, saturation, brightness).copy(alpha = 0f),
                hsvToColor(hue, saturation, brightness)
            )
        )
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    gradientColors: List<Color>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(gradientColors))
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun ColorSwatchGrid(
    colors: List<ColorValue>,
    selectedColor: Color,
    onColorSelected: (ColorValue) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(colors) { colorValue ->
            ColorSwatch(
                colorValue = colorValue,
                isSelected = colorValue.color == selectedColor,
                onClick = { onColorSelected(colorValue) }
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    colorValue: ColorValue,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(colorValue.color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.2f
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Selected",
                modifier = Modifier.size(20.dp),
                tint = if (colorValue.color.luminance() > 0.5f) Color.Black else Color.White
            )
        }
    }
}

@Composable
private fun GradientPalette(
    onGradientSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gradients = getGradients()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(gradients) { gradient ->
            Card(
                onClick = { onGradientSelected(gradient.css) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(gradient.brush)
                    )
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = gradient.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap to copy CSS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Data class for gradients
data class GradientItem(
    val name: String,
    val brush: Brush,
    val css: String
)

// Helper functions
private fun colorToHex(color: Color): String {
    val red = (color.red * 255).roundToInt()
    val green = (color.green * 255).roundToInt()
    val blue = (color.blue * 255).roundToInt()
    return String.format("#%02X%02X%02X", red, green, blue)
}

private fun hexToColor(hex: String): Color? {
    return try {
        val cleanHex = hex.removePrefix("#")
        if (cleanHex.length == 6) {
            Color(android.graphics.Color.parseColor("#$cleanHex"))
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val hsv = floatArrayOf(hue, saturation, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
        hsv
    )
    return hsv
}

private fun createColorValue(color: Color, alpha: Float = 1f): ColorValue {
    val r = (color.red * 255).roundToInt()
    val g = (color.green * 255).roundToInt()
    val b = (color.blue * 255).roundToInt()
    val a = alpha

    return ColorValue(
        color = color.copy(alpha = a),
        hex = colorToHex(color),
        rgb = "rgb($r, $g, $b)",
        rgba = "rgba($r, $g, $b, ${String.format("%.2f", a)})",
        hsl = colorToHsl(color),
        tailwindClass = findTailwindClass(color),
        name = findColorName(color)
    )
}

private fun colorToHsl(color: Color): String {
    val r = color.red
    val g = color.green
    val b = color.blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2

    return if (max == min) {
        "hsl(0, 0%, ${(l * 100).roundToInt()}%)"
    } else {
        val d = max - min
        val s = if (l > 0.5f) d / (2 - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6
            g -> ((b - r) / d + 2) / 6
            else -> ((r - g) / d + 4) / 6
        }
        "hsl(${(h * 360).roundToInt()}, ${(s * 100).roundToInt()}%, ${(l * 100).roundToInt()}%)"
    }
}

private fun getColorValueForFormat(colorValue: ColorValue, format: ColorFormat): String {
    return when (format) {
        ColorFormat.HEX -> colorValue.hex
        ColorFormat.RGB -> colorValue.rgb
        ColorFormat.RGBA -> colorValue.rgba
        ColorFormat.HSL -> colorValue.hsl
        ColorFormat.TAILWIND -> colorValue.tailwindClass ?: colorValue.hex
    }
}

private fun findTailwindClass(color: Color): String? {
    // Map common colors to Tailwind classes
    val tailwindColors = mapOf(
        0xFFEF4444.toInt() to "red-500",
        0xFFF97316.toInt() to "orange-500",
        0xFFEAB308.toInt() to "yellow-500",
        0xFF22C55E.toInt() to "green-500",
        0xFF3B82F6.toInt() to "blue-500",
        0xFF8B5CF6.toInt() to "violet-500",
        0xFFF43F5E.toInt() to "rose-500",
        0xFF6366F1.toInt() to "indigo-500",
        0xFF0EA5E9.toInt() to "sky-500",
        0xFF14B8A6.toInt() to "teal-500"
    )

    val argb = color.toArgb()
    return tailwindColors[argb]?.let { "bg-$it" }
}

private fun findColorName(color: Color): String? {
    // Basic color name mapping
    val colorNames = mapOf(
        0xFFFF0000.toInt() to "Red",
        0xFF00FF00.toInt() to "Green",
        0xFF0000FF.toInt() to "Blue",
        0xFFFFFF00.toInt() to "Yellow",
        0xFFFF00FF.toInt() to "Magenta",
        0xFF00FFFF.toInt() to "Cyan",
        0xFFFFFFFF.toInt() to "White",
        0xFF000000.toInt() to "Black"
    )
    return colorNames[color.toArgb()]
}

// Color helper extension
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

// Palette data
private fun getColorsForPalette(palette: ColorPalette): List<ColorValue> {
    return when (palette) {
        ColorPalette.MATERIAL -> getMaterialColors()
        ColorPalette.TAILWIND -> getTailwindColors()
        ColorPalette.FLAT_UI -> getFlatUIColors()
        ColorPalette.SOCIAL -> getSocialColors()
        else -> emptyList()
    }
}

private fun getMaterialColors(): List<ColorValue> {
    val colors = listOf(
        // Red
        0xFFFFEBEE, 0xFFFFCDD2, 0xFFEF9A9A, 0xFFE57373, 0xFFEF5350, 0xFFF44336, 0xFFE53935, 0xFFD32F2F, 0xFFC62828, 0xFFB71C1C,
        // Pink
        0xFFFCE4EC, 0xFFF8BBD0, 0xFFF48FB1, 0xFFF06292, 0xFFEC407A, 0xFFE91E63, 0xFFD81B60, 0xFFC2185B, 0xFFAD1457, 0xFF880E4F,
        // Purple
        0xFFF3E5F5, 0xFFE1BEE7, 0xFFCE93D8, 0xFFBA68C8, 0xFFAB47BC, 0xFF9C27B0, 0xFF8E24AA, 0xFF7B1FA2, 0xFF6A1B9A, 0xFF4A148C,
        // Blue
        0xFFE3F2FD, 0xFFBBDEFB, 0xFF90CAF9, 0xFF64B5F6, 0xFF42A5F5, 0xFF2196F3, 0xFF1E88E5, 0xFF1976D2, 0xFF1565C0, 0xFF0D47A1,
        // Green
        0xFFE8F5E9, 0xFFC8E6C9, 0xFFA5D6A7, 0xFF81C784, 0xFF66BB6A, 0xFF4CAF50, 0xFF43A047, 0xFF388E3C, 0xFF2E7D32, 0xFF1B5E20,
        // Yellow
        0xFFFFFDE7, 0xFFFFF9C4, 0xFFFFF59D, 0xFFFFF176, 0xFFFFEE58, 0xFFFFEB3B, 0xFFFDD835, 0xFFFBC02D, 0xFFF9A825, 0xFFF57F17,
        // Orange
        0xFFFFF3E0, 0xFFFFE0B2, 0xFFFFCC80, 0xFFFFB74D, 0xFFFFA726, 0xFFFF9800, 0xFFFB8C00, 0xFFF57C00, 0xFFEF6C00, 0xFFE65100,
        // Grey
        0xFFFAFAFA, 0xFFF5F5F5, 0xFFEEEEEE, 0xFFE0E0E0, 0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575, 0xFF616161, 0xFF424242, 0xFF212121
    )

    return colors.map { colorLong ->
        val color = Color(colorLong)
        createColorValue(color)
    }
}

private fun getTailwindColors(): List<ColorValue> {
    val colors = listOf(
        // Slate
        0xFFF8FAFC to "slate-50", 0xFFF1F5F9 to "slate-100", 0xFFE2E8F0 to "slate-200",
        0xFFCBD5E1 to "slate-300", 0xFF94A3B8 to "slate-400", 0xFF64748B to "slate-500",
        0xFF475569 to "slate-600", 0xFF334155 to "slate-700", 0xFF1E293B to "slate-800", 0xFF0F172A to "slate-900",
        // Red
        0xFFFEF2F2 to "red-50", 0xFFFEE2E2 to "red-100", 0xFFFECACA to "red-200",
        0xFFFCA5A5 to "red-300", 0xFFF87171 to "red-400", 0xFFEF4444 to "red-500",
        0xFFDC2626 to "red-600", 0xFFB91C1C to "red-700", 0xFF991B1B to "red-800", 0xFF7F1D1D to "red-900",
        // Blue
        0xFFEFF6FF to "blue-50", 0xFFDBEAFE to "blue-100", 0xFFBFDBFE to "blue-200",
        0xFF93C5FD to "blue-300", 0xFF60A5FA to "blue-400", 0xFF3B82F6 to "blue-500",
        0xFF2563EB to "blue-600", 0xFF1D4ED8 to "blue-700", 0xFF1E40AF to "blue-800", 0xFF1E3A8A to "blue-900",
        // Green
        0xFFF0FDF4 to "green-50", 0xFFDCFCE7 to "green-100", 0xFFBBF7D0 to "green-200",
        0xFF86EFAC to "green-300", 0xFF4ADE80 to "green-400", 0xFF22C55E to "green-500",
        0xFF16A34A to "green-600", 0xFF15803D to "green-700", 0xFF166534 to "green-800", 0xFF14532D to "green-900",
        // Yellow
        0xFFFEFCE8 to "yellow-50", 0xFFFEF9C3 to "yellow-100", 0xFFFEF08A to "yellow-200",
        0xFFFDE047 to "yellow-300", 0xFFFACC15 to "yellow-400", 0xFFEAB308 to "yellow-500",
        0xFFCA8A04 to "yellow-600", 0xFFA16207 to "yellow-700", 0xFF854D0E to "yellow-800", 0xFF713F12 to "yellow-900",
        // Purple
        0xFFFAF5FF to "purple-50", 0xFFF3E8FF to "purple-100", 0xFFE9D5FF to "purple-200",
        0xFFD8B4FE to "purple-300", 0xFFC084FC to "purple-400", 0xFFA855F7 to "purple-500",
        0xFF9333EA to "purple-600", 0xFF7E22CE to "purple-700", 0xFF6B21A8 to "purple-800", 0xFF581C87 to "purple-900"
    )

    return colors.map { (colorLong, name) ->
        val color = Color(colorLong)
        createColorValue(color).copy(name = name, tailwindClass = "bg-$name")
    }
}

private fun getFlatUIColors(): List<ColorValue> {
    val colors = listOf(
        0xFF1ABC9C to "Turquoise",
        0xFF16A085 to "Green Sea",
        0xFF2ECC71 to "Emerald",
        0xFF27AE60 to "Nephritis",
        0xFF3498DB to "Peter River",
        0xFF2980B9 to "Belize Hole",
        0xFF9B59B6 to "Amethyst",
        0xFF8E44AD to "Wisteria",
        0xFF34495E to "Wet Asphalt",
        0xFF2C3E50 to "Midnight Blue",
        0xFFF1C40F to "Sun Flower",
        0xFFF39C12 to "Orange",
        0xFFE74C3C to "Alizarin",
        0xFFC0392B to "Pomegranate",
        0xFFECF0F1 to "Clouds",
        0xFFBDC3C7 to "Silver",
        0xFF95A5A6 to "Concrete",
        0xFF7F8C8D to "Asbestos"
    )

    return colors.map { (colorLong, name) ->
        val color = Color(colorLong)
        createColorValue(color).copy(name = name)
    }
}

private fun getSocialColors(): List<ColorValue> {
    val colors = listOf(
        0xFF1877F2 to "Facebook",
        0xFF1DA1F2 to "Twitter",
        0xFFE4405F to "Instagram",
        0xFF0A66C2 to "LinkedIn",
        0xFFFF0000 to "YouTube",
        0xFF25D366 to "WhatsApp",
        0xFF1DB954 to "Spotify",
        0xFFFF4500 to "Reddit",
        0xFF000000 to "TikTok",
        0xFF5865F2 to "Discord",
        0xFFE60023 to "Pinterest",
        0xFF00AFF0 to "Skype",
        0xFFBD081C to "Pinterest Red",
        0xFF6441A5 to "Twitch",
        0xFF4267B2 to "Facebook Blue",
        0xFF0084FF to "Messenger"
    )

    return colors.map { (colorLong, name) ->
        val color = Color(colorLong)
        createColorValue(color).copy(name = name)
    }
}

private fun getGradients(): List<GradientItem> {
    return listOf(
        GradientItem(
            "Ocean Blue",
            Brush.horizontalGradient(listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))),
            "linear-gradient(90deg, #2193b0 0%, #6dd5ed 100%)"
        ),
        GradientItem(
            "Purple Dream",
            Brush.horizontalGradient(listOf(Color(0xFFcc2b5e), Color(0xFF753a88))),
            "linear-gradient(90deg, #cc2b5e 0%, #753a88 100%)"
        ),
        GradientItem(
            "Sunset",
            Brush.horizontalGradient(listOf(Color(0xFFff512f), Color(0xFFf09819))),
            "linear-gradient(90deg, #ff512f 0%, #f09819 100%)"
        ),
        GradientItem(
            "Forest",
            Brush.horizontalGradient(listOf(Color(0xFF134E5E), Color(0xFF71B280))),
            "linear-gradient(90deg, #134E5E 0%, #71B280 100%)"
        ),
        GradientItem(
            "Royal",
            Brush.horizontalGradient(listOf(Color(0xFF141E30), Color(0xFF243B55))),
            "linear-gradient(90deg, #141E30 0%, #243B55 100%)"
        ),
        GradientItem(
            "Peach",
            Brush.horizontalGradient(listOf(Color(0xFFED4264), Color(0xFFFFEDBC))),
            "linear-gradient(90deg, #ED4264 0%, #FFEDBC 100%)"
        ),
        GradientItem(
            "Aurora",
            Brush.horizontalGradient(listOf(Color(0xFFEECDA3), Color(0xFFEF629F))),
            "linear-gradient(90deg, #EECDA3 0%, #EF629F 100%)"
        ),
        GradientItem(
            "Midnight",
            Brush.horizontalGradient(listOf(Color(0xFF232526), Color(0xFF414345))),
            "linear-gradient(90deg, #232526 0%, #414345 100%)"
        ),
        GradientItem(
            "Lime",
            Brush.horizontalGradient(listOf(Color(0xFFA8E063), Color(0xFF56AB2F))),
            "linear-gradient(90deg, #A8E063 0%, #56AB2F 100%)"
        ),
        GradientItem(
            "Frost",
            Brush.horizontalGradient(listOf(Color(0xFF000428), Color(0xFF004e92))),
            "linear-gradient(90deg, #000428 0%, #004e92 100%)"
        )
    )
}
