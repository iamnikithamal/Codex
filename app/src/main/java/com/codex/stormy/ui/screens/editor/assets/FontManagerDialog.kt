package com.codex.stormy.ui.screens.editor.assets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Font category for filtering
 */
enum class FontCategory(val displayName: String) {
    ALL("All"),
    SANS_SERIF("Sans Serif"),
    SERIF("Serif"),
    DISPLAY("Display"),
    HANDWRITING("Handwriting"),
    MONOSPACE("Monospace")
}

/**
 * Font weight options
 */
enum class FontWeightOption(val displayName: String, val weight: Int, val cssValue: String) {
    THIN("Thin", 100, "100"),
    EXTRA_LIGHT("Extra Light", 200, "200"),
    LIGHT("Light", 300, "300"),
    REGULAR("Regular", 400, "400"),
    MEDIUM("Medium", 500, "500"),
    SEMI_BOLD("Semi Bold", 600, "600"),
    BOLD("Bold", 700, "700"),
    EXTRA_BOLD("Extra Bold", 800, "800"),
    BLACK("Black", 900, "900")
}

/**
 * Data class representing a Google Font
 */
data class GoogleFont(
    val family: String,
    val category: FontCategory,
    val variants: List<String>,
    val popularity: Int,
    val trending: Boolean = false
) {
    val importUrl: String
        get() = "https://fonts.googleapis.com/css2?family=${family.replace(" ", "+")}:wght@${variants.joinToString(";")}&display=swap"

    val cssLink: String
        get() = "<link href=\"$importUrl\" rel=\"stylesheet\">"

    val cssImport: String
        get() = "@import url('$importUrl');"

    val cssFontFamily: String
        get() = "font-family: '$family', ${getCssFallback()};"

    private fun getCssFallback(): String = when (category) {
        FontCategory.SANS_SERIF -> "sans-serif"
        FontCategory.SERIF -> "serif"
        FontCategory.MONOSPACE -> "monospace"
        FontCategory.HANDWRITING -> "cursive"
        FontCategory.DISPLAY -> "sans-serif"
        FontCategory.ALL -> "sans-serif"
    }
}

/**
 * Font Manager Dialog for browsing and using Google Fonts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontManagerDialog(
    onDismiss: () -> Unit,
    onFontSelected: (GoogleFont, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    // State
    var selectedCategory by remember { mutableStateOf(FontCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf<GoogleFont?>(null) }
    var previewSize by remember { mutableFloatStateOf(24f) }
    var previewText by remember { mutableStateOf("The quick brown fox jumps over the lazy dog") }

    // Available fonts
    val allFonts = remember { getGoogleFonts() }

    // Filtered fonts
    val filteredFonts by remember(selectedCategory, searchQuery) {
        derivedStateOf {
            allFonts.filter { font ->
                val matchesCategory = selectedCategory == FontCategory.ALL || font.category == selectedCategory
                val matchesSearch = searchQuery.isEmpty() || font.family.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesSearch
            }.sortedByDescending { it.popularity }
        }
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
                        imageVector = Icons.Outlined.FontDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Google Fonts",
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

            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = FontCategory.entries.indexOf(selectedCategory),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                FontCategory.entries.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = {
                            Text(
                                text = category.displayName,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (selectedCategory == category) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search fonts...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            // Font preview size slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preview size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Slider(
                        value = previewSize,
                        onValueChange = { previewSize = it },
                        valueRange = 12f..48f,
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        text = "${previewSize.toInt()}px",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Fonts list
            if (filteredFonts.isEmpty()) {
                EmptyFontsState(searchQuery = searchQuery)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredFonts) { font ->
                        FontCard(
                            font = font,
                            previewText = previewText,
                            previewSize = previewSize,
                            isSelected = selectedFont == font,
                            onClick = { selectedFont = font },
                            onCopyLink = {
                                clipboardManager.setText(AnnotatedString(font.cssLink))
                            },
                            onCopyImport = {
                                clipboardManager.setText(AnnotatedString(font.cssImport))
                            },
                            onCopyFamily = {
                                clipboardManager.setText(AnnotatedString(font.cssFontFamily))
                            }
                        )
                    }
                }
            }

            // Action button
            selectedFont?.let { font ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Selected: ${font.family}",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CopyButton(
                                text = "Link",
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(font.cssLink))
                                    onFontSelected(font, font.cssLink)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            CopyButton(
                                text = "Import",
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(font.cssImport))
                                    onFontSelected(font, font.cssImport)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            CopyButton(
                                text = "CSS",
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(font.cssFontFamily))
                                    onFontSelected(font, font.cssFontFamily)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFontsState(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FontDownload,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) {
                "No fonts found for \"$searchQuery\""
            } else {
                "No fonts available"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FontCard(
    font: GoogleFont,
    previewText: String,
    previewSize: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyImport: () -> Unit,
    onCopyFamily: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Font header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = font.family,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (font.trending) {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = "Trending",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "${font.category.displayName} • ${font.variants.size} weights",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onCopyLink, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy link",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Font preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = previewText,
                    style = TextStyle(
                        fontSize = previewSize.sp,
                        fontFamily = PoppinsFontFamily // Would load actual Google Font in production
                    ),
                    modifier = Modifier.padding(12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Weights preview
            AnimatedVisibility(visible = isSelected) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Available Weights",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        font.variants.take(6).forEach { weight ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = weight,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (font.variants.size > 6) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "+${font.variants.size - 6}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * Get list of popular Google Fonts
 */
private fun getGoogleFonts(): List<GoogleFont> {
    return listOf(
        // Sans Serif - Popular
        GoogleFont("Roboto", FontCategory.SANS_SERIF, listOf("100", "300", "400", "500", "700", "900"), 100, true),
        GoogleFont("Open Sans", FontCategory.SANS_SERIF, listOf("300", "400", "500", "600", "700", "800"), 95, true),
        GoogleFont("Lato", FontCategory.SANS_SERIF, listOf("100", "300", "400", "700", "900"), 90),
        GoogleFont("Montserrat", FontCategory.SANS_SERIF, listOf("100", "200", "300", "400", "500", "600", "700", "800", "900"), 92, true),
        GoogleFont("Poppins", FontCategory.SANS_SERIF, listOf("100", "200", "300", "400", "500", "600", "700", "800", "900"), 91, true),
        GoogleFont("Inter", FontCategory.SANS_SERIF, listOf("100", "200", "300", "400", "500", "600", "700", "800", "900"), 88, true),
        GoogleFont("Nunito", FontCategory.SANS_SERIF, listOf("200", "300", "400", "500", "600", "700", "800", "900"), 85),
        GoogleFont("Work Sans", FontCategory.SANS_SERIF, listOf("100", "200", "300", "400", "500", "600", "700", "800", "900"), 80),
        GoogleFont("Raleway", FontCategory.SANS_SERIF, listOf("100", "200", "300", "400", "500", "600", "700", "800", "900"), 82),
        GoogleFont("Ubuntu", FontCategory.SANS_SERIF, listOf("300", "400", "500", "700"), 75),
        GoogleFont("Quicksand", FontCategory.SANS_SERIF, listOf("300", "400", "500", "600", "700"), 70),
        GoogleFont("Mulish", FontCategory.SANS_SERIF, listOf("200", "300", "400", "500", "600", "700", "800", "900"), 68),
        GoogleFont("DM Sans", FontCategory.SANS_SERIF, listOf("400", "500", "700"), 65, true),
        GoogleFont("Manrope", FontCategory.SANS_SERIF, listOf("200", "300", "400", "500", "600", "700", "800"), 60),
        GoogleFont("Space Grotesk", FontCategory.SANS_SERIF, listOf("300", "400", "500", "600", "700"), 55, true),

        // Serif
        GoogleFont("Playfair Display", FontCategory.SERIF, listOf("400", "500", "600", "700", "800", "900"), 85, true),
        GoogleFont("Merriweather", FontCategory.SERIF, listOf("300", "400", "700", "900"), 80),
        GoogleFont("Lora", FontCategory.SERIF, listOf("400", "500", "600", "700"), 78),
        GoogleFont("PT Serif", FontCategory.SERIF, listOf("400", "700"), 72),
        GoogleFont("Noto Serif", FontCategory.SERIF, listOf("400", "700"), 70),
        GoogleFont("Source Serif Pro", FontCategory.SERIF, listOf("200", "300", "400", "600", "700", "900"), 68),
        GoogleFont("Libre Baskerville", FontCategory.SERIF, listOf("400", "700"), 65),
        GoogleFont("Cormorant Garamond", FontCategory.SERIF, listOf("300", "400", "500", "600", "700"), 60),
        GoogleFont("Crimson Text", FontCategory.SERIF, listOf("400", "600", "700"), 55),
        GoogleFont("EB Garamond", FontCategory.SERIF, listOf("400", "500", "600", "700", "800"), 52),

        // Display
        GoogleFont("Bebas Neue", FontCategory.DISPLAY, listOf("400"), 88, true),
        GoogleFont("Oswald", FontCategory.DISPLAY, listOf("200", "300", "400", "500", "600", "700"), 85),
        GoogleFont("Anton", FontCategory.DISPLAY, listOf("400"), 75),
        GoogleFont("Abril Fatface", FontCategory.DISPLAY, listOf("400"), 70),
        GoogleFont("Righteous", FontCategory.DISPLAY, listOf("400"), 65),
        GoogleFont("Alfa Slab One", FontCategory.DISPLAY, listOf("400"), 60),
        GoogleFont("Archivo Black", FontCategory.DISPLAY, listOf("400"), 58),
        GoogleFont("Passion One", FontCategory.DISPLAY, listOf("400", "700", "900"), 55),
        GoogleFont("Russo One", FontCategory.DISPLAY, listOf("400"), 50),
        GoogleFont("Bungee", FontCategory.DISPLAY, listOf("400"), 45),

        // Handwriting
        GoogleFont("Dancing Script", FontCategory.HANDWRITING, listOf("400", "500", "600", "700"), 85, true),
        GoogleFont("Pacifico", FontCategory.HANDWRITING, listOf("400"), 80),
        GoogleFont("Caveat", FontCategory.HANDWRITING, listOf("400", "500", "600", "700"), 75),
        GoogleFont("Satisfy", FontCategory.HANDWRITING, listOf("400"), 70),
        GoogleFont("Great Vibes", FontCategory.HANDWRITING, listOf("400"), 68),
        GoogleFont("Sacramento", FontCategory.HANDWRITING, listOf("400"), 65),
        GoogleFont("Kaushan Script", FontCategory.HANDWRITING, listOf("400"), 60),
        GoogleFont("Yellowtail", FontCategory.HANDWRITING, listOf("400"), 55),
        GoogleFont("Courgette", FontCategory.HANDWRITING, listOf("400"), 52),
        GoogleFont("Cookie", FontCategory.HANDWRITING, listOf("400"), 50),

        // Monospace
        GoogleFont("Fira Code", FontCategory.MONOSPACE, listOf("300", "400", "500", "600", "700"), 90, true),
        GoogleFont("JetBrains Mono", FontCategory.MONOSPACE, listOf("100", "200", "300", "400", "500", "600", "700", "800"), 88, true),
        GoogleFont("Source Code Pro", FontCategory.MONOSPACE, listOf("200", "300", "400", "500", "600", "700", "900"), 85),
        GoogleFont("Roboto Mono", FontCategory.MONOSPACE, listOf("100", "200", "300", "400", "500", "600", "700"), 82),
        GoogleFont("Ubuntu Mono", FontCategory.MONOSPACE, listOf("400", "700"), 75),
        GoogleFont("Space Mono", FontCategory.MONOSPACE, listOf("400", "700"), 70),
        GoogleFont("IBM Plex Mono", FontCategory.MONOSPACE, listOf("100", "200", "300", "400", "500", "600", "700"), 68),
        GoogleFont("Inconsolata", FontCategory.MONOSPACE, listOf("200", "300", "400", "500", "600", "700", "800", "900"), 65),
        GoogleFont("Cousine", FontCategory.MONOSPACE, listOf("400", "700"), 55),
        GoogleFont("Anonymous Pro", FontCategory.MONOSPACE, listOf("400", "700"), 50)
    )
}
