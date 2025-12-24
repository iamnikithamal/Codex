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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Icon library provider enum
 */
enum class IconLibrary(val displayName: String, val cdnUrl: String, val prefix: String) {
    FONT_AWESOME("Font Awesome", "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css", "fa"),
    MATERIAL_ICONS("Material Icons", "https://fonts.googleapis.com/icon?family=Material+Icons", "material-icons"),
    BOOTSTRAP_ICONS("Bootstrap Icons", "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.2/font/bootstrap-icons.css", "bi"),
    HEROICONS("Heroicons", "https://unpkg.com/heroicons@2.0.18/24/outline", "heroicon"),
    FEATHER("Feather Icons", "https://unpkg.com/feather-icons@4.29.1/dist/icons", "feather")
}

/**
 * Icon category for filtering
 */
enum class IconCategory(val displayName: String) {
    ALL("All"),
    NAVIGATION("Navigation"),
    SOCIAL("Social"),
    MEDIA("Media"),
    COMMUNICATION("Communication"),
    BUSINESS("Business"),
    WEATHER("Weather"),
    DEVICES("Devices"),
    FILES("Files"),
    ARROWS("Arrows")
}

/**
 * Data class representing an icon
 */
data class IconItem(
    val name: String,
    val library: IconLibrary,
    val category: IconCategory,
    val htmlCode: String,
    val cssClass: String,
    val unicode: String? = null
)

/**
 * Icon Library Dialog for browsing and inserting icons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconLibraryDialog(
    onDismiss: () -> Unit,
    onIconSelected: (IconItem, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    // State
    var selectedLibrary by remember { mutableStateOf(IconLibrary.FONT_AWESOME) }
    var selectedCategory by remember { mutableStateOf(IconCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedIcon by remember { mutableStateOf<IconItem?>(null) }

    // Get filtered icons
    val filteredIcons = remember(selectedLibrary, selectedCategory, searchQuery) {
        getIconsForLibrary(selectedLibrary).filter { icon ->
            val matchesCategory = selectedCategory == IconCategory.ALL || icon.category == selectedCategory
            val matchesSearch = searchQuery.isEmpty() || icon.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
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
                Text(
                    text = "Icon Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Library tabs
            ScrollableTabRow(
                selectedTabIndex = IconLibrary.entries.indexOf(selectedLibrary),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                IconLibrary.entries.forEach { library ->
                    Tab(
                        selected = selectedLibrary == library,
                        onClick = { selectedLibrary = library },
                        text = {
                            Text(
                                text = library.displayName,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (selectedLibrary == library) FontWeight.Medium else FontWeight.Normal
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
                placeholder = { Text("Search icons...") },
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

            Spacer(modifier = Modifier.height(12.dp))

            // Category filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(IconCategory.ALL, IconCategory.NAVIGATION, IconCategory.SOCIAL, IconCategory.MEDIA).forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.displayName,
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

            // Icons grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredIcons.isEmpty()) {
                EmptyIconsState(searchQuery = searchQuery)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredIcons) { icon ->
                        IconGridItem(
                            icon = icon,
                            isSelected = selectedIcon == icon,
                            onClick = { selectedIcon = icon }
                        )
                    }
                }
            }
        }

        // Selected icon details
        selectedIcon?.let { icon ->
            IconDetailsBottomBar(
                icon = icon,
                onCopyHtml = {
                    clipboardManager.setText(AnnotatedString(icon.htmlCode))
                    onIconSelected(icon, icon.htmlCode)
                },
                onCopyCss = {
                    clipboardManager.setText(AnnotatedString(icon.cssClass))
                },
                onDismiss = { selectedIcon = null }
            )
        }
    }
}

@Composable
private fun EmptyIconsState(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) {
                "No icons found for \"$searchQuery\""
            } else {
                "No icons available"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IconGridItem(
    icon: IconItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
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
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon preview (using text representation)
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getIconPreviewChar(icon),
                        fontSize = 20.sp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = icon.name,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PoppinsFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun IconDetailsBottomBar(
    icon: IconItem,
    onCopyHtml: () -> Unit,
    onCopyCss: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = icon.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = icon.library.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Code preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = icon.htmlCode,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onCopyHtml,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Copy HTML",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Surface(
                    onClick = onCopyCss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Copy Class",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Get a preview character for the icon (simplified representation)
 */
private fun getIconPreviewChar(icon: IconItem): String {
    // Use unicode if available, otherwise use first letter
    return icon.unicode ?: icon.name.firstOrNull()?.uppercase() ?: "?"
}

/**
 * Get icons for a specific library
 * This provides a curated list of popular icons for each library
 */
private fun getIconsForLibrary(library: IconLibrary): List<IconItem> {
    return when (library) {
        IconLibrary.FONT_AWESOME -> getFontAwesomeIcons()
        IconLibrary.MATERIAL_ICONS -> getMaterialIcons()
        IconLibrary.BOOTSTRAP_ICONS -> getBootstrapIcons()
        IconLibrary.HEROICONS -> getHeroicons()
        IconLibrary.FEATHER -> getFeatherIcons()
    }
}

private fun getFontAwesomeIcons(): List<IconItem> {
    return listOf(
        // Navigation
        IconItem("home", IconLibrary.FONT_AWESOME, IconCategory.NAVIGATION, "<i class=\"fa-solid fa-house\"></i>", "fa-solid fa-house", "🏠"),
        IconItem("menu", IconLibrary.FONT_AWESOME, IconCategory.NAVIGATION, "<i class=\"fa-solid fa-bars\"></i>", "fa-solid fa-bars", "☰"),
        IconItem("search", IconLibrary.FONT_AWESOME, IconCategory.NAVIGATION, "<i class=\"fa-solid fa-magnifying-glass\"></i>", "fa-solid fa-magnifying-glass", "🔍"),
        IconItem("settings", IconLibrary.FONT_AWESOME, IconCategory.NAVIGATION, "<i class=\"fa-solid fa-gear\"></i>", "fa-solid fa-gear", "⚙"),
        IconItem("user", IconLibrary.FONT_AWESOME, IconCategory.NAVIGATION, "<i class=\"fa-solid fa-user\"></i>", "fa-solid fa-user", "👤"),
        IconItem("bell", IconLibrary.FONT_AWESOME, IconCategory.NAVIGATION, "<i class=\"fa-solid fa-bell\"></i>", "fa-solid fa-bell", "🔔"),
        IconItem("envelope", IconLibrary.FONT_AWESOME, IconCategory.COMMUNICATION, "<i class=\"fa-solid fa-envelope\"></i>", "fa-solid fa-envelope", "✉"),
        IconItem("cart", IconLibrary.FONT_AWESOME, IconCategory.BUSINESS, "<i class=\"fa-solid fa-cart-shopping\"></i>", "fa-solid fa-cart-shopping", "🛒"),

        // Social
        IconItem("facebook", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-facebook\"></i>", "fa-brands fa-facebook", "f"),
        IconItem("twitter", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-twitter\"></i>", "fa-brands fa-twitter", "𝕏"),
        IconItem("instagram", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-instagram\"></i>", "fa-brands fa-instagram", "📷"),
        IconItem("linkedin", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-linkedin\"></i>", "fa-brands fa-linkedin", "in"),
        IconItem("github", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-github\"></i>", "fa-brands fa-github", "🐙"),
        IconItem("youtube", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-youtube\"></i>", "fa-brands fa-youtube", "▶"),
        IconItem("tiktok", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-tiktok\"></i>", "fa-brands fa-tiktok", "♪"),
        IconItem("discord", IconLibrary.FONT_AWESOME, IconCategory.SOCIAL, "<i class=\"fa-brands fa-discord\"></i>", "fa-brands fa-discord", "💬"),

        // Media
        IconItem("play", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-play\"></i>", "fa-solid fa-play", "▶"),
        IconItem("pause", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-pause\"></i>", "fa-solid fa-pause", "⏸"),
        IconItem("stop", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-stop\"></i>", "fa-solid fa-stop", "⏹"),
        IconItem("volume", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-volume-high\"></i>", "fa-solid fa-volume-high", "🔊"),
        IconItem("image", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-image\"></i>", "fa-solid fa-image", "🖼"),
        IconItem("video", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-video\"></i>", "fa-solid fa-video", "🎬"),
        IconItem("music", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-music\"></i>", "fa-solid fa-music", "🎵"),
        IconItem("camera", IconLibrary.FONT_AWESOME, IconCategory.MEDIA, "<i class=\"fa-solid fa-camera\"></i>", "fa-solid fa-camera", "📷"),

        // Business
        IconItem("briefcase", IconLibrary.FONT_AWESOME, IconCategory.BUSINESS, "<i class=\"fa-solid fa-briefcase\"></i>", "fa-solid fa-briefcase", "💼"),
        IconItem("chart", IconLibrary.FONT_AWESOME, IconCategory.BUSINESS, "<i class=\"fa-solid fa-chart-line\"></i>", "fa-solid fa-chart-line", "📈"),
        IconItem("credit-card", IconLibrary.FONT_AWESOME, IconCategory.BUSINESS, "<i class=\"fa-solid fa-credit-card\"></i>", "fa-solid fa-credit-card", "💳"),
        IconItem("dollar", IconLibrary.FONT_AWESOME, IconCategory.BUSINESS, "<i class=\"fa-solid fa-dollar-sign\"></i>", "fa-solid fa-dollar-sign", "$"),

        // Arrows
        IconItem("arrow-up", IconLibrary.FONT_AWESOME, IconCategory.ARROWS, "<i class=\"fa-solid fa-arrow-up\"></i>", "fa-solid fa-arrow-up", "↑"),
        IconItem("arrow-down", IconLibrary.FONT_AWESOME, IconCategory.ARROWS, "<i class=\"fa-solid fa-arrow-down\"></i>", "fa-solid fa-arrow-down", "↓"),
        IconItem("arrow-left", IconLibrary.FONT_AWESOME, IconCategory.ARROWS, "<i class=\"fa-solid fa-arrow-left\"></i>", "fa-solid fa-arrow-left", "←"),
        IconItem("arrow-right", IconLibrary.FONT_AWESOME, IconCategory.ARROWS, "<i class=\"fa-solid fa-arrow-right\"></i>", "fa-solid fa-arrow-right", "→"),
        IconItem("chevron-up", IconLibrary.FONT_AWESOME, IconCategory.ARROWS, "<i class=\"fa-solid fa-chevron-up\"></i>", "fa-solid fa-chevron-up", "∧"),
        IconItem("chevron-down", IconLibrary.FONT_AWESOME, IconCategory.ARROWS, "<i class=\"fa-solid fa-chevron-down\"></i>", "fa-solid fa-chevron-down", "∨"),

        // Files
        IconItem("file", IconLibrary.FONT_AWESOME, IconCategory.FILES, "<i class=\"fa-solid fa-file\"></i>", "fa-solid fa-file", "📄"),
        IconItem("folder", IconLibrary.FONT_AWESOME, IconCategory.FILES, "<i class=\"fa-solid fa-folder\"></i>", "fa-solid fa-folder", "📁"),
        IconItem("download", IconLibrary.FONT_AWESOME, IconCategory.FILES, "<i class=\"fa-solid fa-download\"></i>", "fa-solid fa-download", "⬇"),
        IconItem("upload", IconLibrary.FONT_AWESOME, IconCategory.FILES, "<i class=\"fa-solid fa-upload\"></i>", "fa-solid fa-upload", "⬆"),

        // Communication
        IconItem("phone", IconLibrary.FONT_AWESOME, IconCategory.COMMUNICATION, "<i class=\"fa-solid fa-phone\"></i>", "fa-solid fa-phone", "📞"),
        IconItem("comment", IconLibrary.FONT_AWESOME, IconCategory.COMMUNICATION, "<i class=\"fa-solid fa-comment\"></i>", "fa-solid fa-comment", "💬"),
        IconItem("share", IconLibrary.FONT_AWESOME, IconCategory.COMMUNICATION, "<i class=\"fa-solid fa-share\"></i>", "fa-solid fa-share", "↗"),

        // Weather
        IconItem("sun", IconLibrary.FONT_AWESOME, IconCategory.WEATHER, "<i class=\"fa-solid fa-sun\"></i>", "fa-solid fa-sun", "☀"),
        IconItem("moon", IconLibrary.FONT_AWESOME, IconCategory.WEATHER, "<i class=\"fa-solid fa-moon\"></i>", "fa-solid fa-moon", "🌙"),
        IconItem("cloud", IconLibrary.FONT_AWESOME, IconCategory.WEATHER, "<i class=\"fa-solid fa-cloud\"></i>", "fa-solid fa-cloud", "☁"),

        // Devices
        IconItem("desktop", IconLibrary.FONT_AWESOME, IconCategory.DEVICES, "<i class=\"fa-solid fa-desktop\"></i>", "fa-solid fa-desktop", "🖥"),
        IconItem("laptop", IconLibrary.FONT_AWESOME, IconCategory.DEVICES, "<i class=\"fa-solid fa-laptop\"></i>", "fa-solid fa-laptop", "💻"),
        IconItem("mobile", IconLibrary.FONT_AWESOME, IconCategory.DEVICES, "<i class=\"fa-solid fa-mobile\"></i>", "fa-solid fa-mobile", "📱"),
        IconItem("tablet", IconLibrary.FONT_AWESOME, IconCategory.DEVICES, "<i class=\"fa-solid fa-tablet\"></i>", "fa-solid fa-tablet", "📱")
    )
}

private fun getMaterialIcons(): List<IconItem> {
    return listOf(
        // Navigation
        IconItem("home", IconLibrary.MATERIAL_ICONS, IconCategory.NAVIGATION, "<span class=\"material-icons\">home</span>", "material-icons", "🏠"),
        IconItem("menu", IconLibrary.MATERIAL_ICONS, IconCategory.NAVIGATION, "<span class=\"material-icons\">menu</span>", "material-icons", "☰"),
        IconItem("search", IconLibrary.MATERIAL_ICONS, IconCategory.NAVIGATION, "<span class=\"material-icons\">search</span>", "material-icons", "🔍"),
        IconItem("settings", IconLibrary.MATERIAL_ICONS, IconCategory.NAVIGATION, "<span class=\"material-icons\">settings</span>", "material-icons", "⚙"),
        IconItem("person", IconLibrary.MATERIAL_ICONS, IconCategory.NAVIGATION, "<span class=\"material-icons\">person</span>", "material-icons", "👤"),
        IconItem("notifications", IconLibrary.MATERIAL_ICONS, IconCategory.NAVIGATION, "<span class=\"material-icons\">notifications</span>", "material-icons", "🔔"),
        IconItem("shopping_cart", IconLibrary.MATERIAL_ICONS, IconCategory.BUSINESS, "<span class=\"material-icons\">shopping_cart</span>", "material-icons", "🛒"),
        IconItem("favorite", IconLibrary.MATERIAL_ICONS, IconCategory.NAVIGATION, "<span class=\"material-icons\">favorite</span>", "material-icons", "❤"),

        // Media
        IconItem("play_arrow", IconLibrary.MATERIAL_ICONS, IconCategory.MEDIA, "<span class=\"material-icons\">play_arrow</span>", "material-icons", "▶"),
        IconItem("pause", IconLibrary.MATERIAL_ICONS, IconCategory.MEDIA, "<span class=\"material-icons\">pause</span>", "material-icons", "⏸"),
        IconItem("stop", IconLibrary.MATERIAL_ICONS, IconCategory.MEDIA, "<span class=\"material-icons\">stop</span>", "material-icons", "⏹"),
        IconItem("volume_up", IconLibrary.MATERIAL_ICONS, IconCategory.MEDIA, "<span class=\"material-icons\">volume_up</span>", "material-icons", "🔊"),
        IconItem("image", IconLibrary.MATERIAL_ICONS, IconCategory.MEDIA, "<span class=\"material-icons\">image</span>", "material-icons", "🖼"),
        IconItem("videocam", IconLibrary.MATERIAL_ICONS, IconCategory.MEDIA, "<span class=\"material-icons\">videocam</span>", "material-icons", "🎬"),

        // Communication
        IconItem("email", IconLibrary.MATERIAL_ICONS, IconCategory.COMMUNICATION, "<span class=\"material-icons\">email</span>", "material-icons", "✉"),
        IconItem("phone", IconLibrary.MATERIAL_ICONS, IconCategory.COMMUNICATION, "<span class=\"material-icons\">phone</span>", "material-icons", "📞"),
        IconItem("chat", IconLibrary.MATERIAL_ICONS, IconCategory.COMMUNICATION, "<span class=\"material-icons\">chat</span>", "material-icons", "💬"),
        IconItem("share", IconLibrary.MATERIAL_ICONS, IconCategory.COMMUNICATION, "<span class=\"material-icons\">share</span>", "material-icons", "↗"),

        // Arrows
        IconItem("arrow_upward", IconLibrary.MATERIAL_ICONS, IconCategory.ARROWS, "<span class=\"material-icons\">arrow_upward</span>", "material-icons", "↑"),
        IconItem("arrow_downward", IconLibrary.MATERIAL_ICONS, IconCategory.ARROWS, "<span class=\"material-icons\">arrow_downward</span>", "material-icons", "↓"),
        IconItem("arrow_back", IconLibrary.MATERIAL_ICONS, IconCategory.ARROWS, "<span class=\"material-icons\">arrow_back</span>", "material-icons", "←"),
        IconItem("arrow_forward", IconLibrary.MATERIAL_ICONS, IconCategory.ARROWS, "<span class=\"material-icons\">arrow_forward</span>", "material-icons", "→"),
        IconItem("expand_more", IconLibrary.MATERIAL_ICONS, IconCategory.ARROWS, "<span class=\"material-icons\">expand_more</span>", "material-icons", "∨"),
        IconItem("expand_less", IconLibrary.MATERIAL_ICONS, IconCategory.ARROWS, "<span class=\"material-icons\">expand_less</span>", "material-icons", "∧"),

        // Files
        IconItem("folder", IconLibrary.MATERIAL_ICONS, IconCategory.FILES, "<span class=\"material-icons\">folder</span>", "material-icons", "📁"),
        IconItem("description", IconLibrary.MATERIAL_ICONS, IconCategory.FILES, "<span class=\"material-icons\">description</span>", "material-icons", "📄"),
        IconItem("cloud_download", IconLibrary.MATERIAL_ICONS, IconCategory.FILES, "<span class=\"material-icons\">cloud_download</span>", "material-icons", "⬇"),
        IconItem("cloud_upload", IconLibrary.MATERIAL_ICONS, IconCategory.FILES, "<span class=\"material-icons\">cloud_upload</span>", "material-icons", "⬆"),

        // Devices
        IconItem("computer", IconLibrary.MATERIAL_ICONS, IconCategory.DEVICES, "<span class=\"material-icons\">computer</span>", "material-icons", "🖥"),
        IconItem("laptop", IconLibrary.MATERIAL_ICONS, IconCategory.DEVICES, "<span class=\"material-icons\">laptop</span>", "material-icons", "💻"),
        IconItem("smartphone", IconLibrary.MATERIAL_ICONS, IconCategory.DEVICES, "<span class=\"material-icons\">smartphone</span>", "material-icons", "📱"),
        IconItem("tablet", IconLibrary.MATERIAL_ICONS, IconCategory.DEVICES, "<span class=\"material-icons\">tablet</span>", "material-icons", "📱")
    )
}

private fun getBootstrapIcons(): List<IconItem> {
    return listOf(
        IconItem("house", IconLibrary.BOOTSTRAP_ICONS, IconCategory.NAVIGATION, "<i class=\"bi bi-house\"></i>", "bi bi-house", "🏠"),
        IconItem("list", IconLibrary.BOOTSTRAP_ICONS, IconCategory.NAVIGATION, "<i class=\"bi bi-list\"></i>", "bi bi-list", "☰"),
        IconItem("search", IconLibrary.BOOTSTRAP_ICONS, IconCategory.NAVIGATION, "<i class=\"bi bi-search\"></i>", "bi bi-search", "🔍"),
        IconItem("gear", IconLibrary.BOOTSTRAP_ICONS, IconCategory.NAVIGATION, "<i class=\"bi bi-gear\"></i>", "bi bi-gear", "⚙"),
        IconItem("person", IconLibrary.BOOTSTRAP_ICONS, IconCategory.NAVIGATION, "<i class=\"bi bi-person\"></i>", "bi bi-person", "👤"),
        IconItem("bell", IconLibrary.BOOTSTRAP_ICONS, IconCategory.NAVIGATION, "<i class=\"bi bi-bell\"></i>", "bi bi-bell", "🔔"),
        IconItem("cart", IconLibrary.BOOTSTRAP_ICONS, IconCategory.BUSINESS, "<i class=\"bi bi-cart\"></i>", "bi bi-cart", "🛒"),
        IconItem("heart", IconLibrary.BOOTSTRAP_ICONS, IconCategory.NAVIGATION, "<i class=\"bi bi-heart\"></i>", "bi bi-heart", "❤"),

        IconItem("facebook", IconLibrary.BOOTSTRAP_ICONS, IconCategory.SOCIAL, "<i class=\"bi bi-facebook\"></i>", "bi bi-facebook", "f"),
        IconItem("twitter", IconLibrary.BOOTSTRAP_ICONS, IconCategory.SOCIAL, "<i class=\"bi bi-twitter\"></i>", "bi bi-twitter", "𝕏"),
        IconItem("instagram", IconLibrary.BOOTSTRAP_ICONS, IconCategory.SOCIAL, "<i class=\"bi bi-instagram\"></i>", "bi bi-instagram", "📷"),
        IconItem("linkedin", IconLibrary.BOOTSTRAP_ICONS, IconCategory.SOCIAL, "<i class=\"bi bi-linkedin\"></i>", "bi bi-linkedin", "in"),
        IconItem("github", IconLibrary.BOOTSTRAP_ICONS, IconCategory.SOCIAL, "<i class=\"bi bi-github\"></i>", "bi bi-github", "🐙"),
        IconItem("youtube", IconLibrary.BOOTSTRAP_ICONS, IconCategory.SOCIAL, "<i class=\"bi bi-youtube\"></i>", "bi bi-youtube", "▶"),

        IconItem("play", IconLibrary.BOOTSTRAP_ICONS, IconCategory.MEDIA, "<i class=\"bi bi-play\"></i>", "bi bi-play", "▶"),
        IconItem("pause", IconLibrary.BOOTSTRAP_ICONS, IconCategory.MEDIA, "<i class=\"bi bi-pause\"></i>", "bi bi-pause", "⏸"),
        IconItem("stop", IconLibrary.BOOTSTRAP_ICONS, IconCategory.MEDIA, "<i class=\"bi bi-stop\"></i>", "bi bi-stop", "⏹"),
        IconItem("volume-up", IconLibrary.BOOTSTRAP_ICONS, IconCategory.MEDIA, "<i class=\"bi bi-volume-up\"></i>", "bi bi-volume-up", "🔊"),

        IconItem("arrow-up", IconLibrary.BOOTSTRAP_ICONS, IconCategory.ARROWS, "<i class=\"bi bi-arrow-up\"></i>", "bi bi-arrow-up", "↑"),
        IconItem("arrow-down", IconLibrary.BOOTSTRAP_ICONS, IconCategory.ARROWS, "<i class=\"bi bi-arrow-down\"></i>", "bi bi-arrow-down", "↓"),
        IconItem("arrow-left", IconLibrary.BOOTSTRAP_ICONS, IconCategory.ARROWS, "<i class=\"bi bi-arrow-left\"></i>", "bi bi-arrow-left", "←"),
        IconItem("arrow-right", IconLibrary.BOOTSTRAP_ICONS, IconCategory.ARROWS, "<i class=\"bi bi-arrow-right\"></i>", "bi bi-arrow-right", "→")
    )
}

private fun getHeroicons(): List<IconItem> {
    return listOf(
        IconItem("home", IconLibrary.HEROICONS, IconCategory.NAVIGATION, "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"m2.25 12 8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25\" /></svg>", "heroicon-home", "🏠"),
        IconItem("bars-3", IconLibrary.HEROICONS, IconCategory.NAVIGATION, "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5\" /></svg>", "heroicon-bars-3", "☰"),
        IconItem("magnifying-glass", IconLibrary.HEROICONS, IconCategory.NAVIGATION, "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z\" /></svg>", "heroicon-magnifying-glass", "🔍"),
        IconItem("cog-6-tooth", IconLibrary.HEROICONS, IconCategory.NAVIGATION, "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 0 1 0 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 0 1 0-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28Z\" /></svg>", "heroicon-cog-6-tooth", "⚙"),
        IconItem("user", IconLibrary.HEROICONS, IconCategory.NAVIGATION, "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z\" /></svg>", "heroicon-user", "👤"),
        IconItem("bell", IconLibrary.HEROICONS, IconCategory.NAVIGATION, "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0\" /></svg>", "heroicon-bell", "🔔"),
        IconItem("heart", IconLibrary.HEROICONS, IconCategory.NAVIGATION, "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\" class=\"w-6 h-6\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12Z\" /></svg>", "heroicon-heart", "❤")
    )
}

private fun getFeatherIcons(): List<IconItem> {
    return listOf(
        IconItem("home", IconLibrary.FEATHER, IconCategory.NAVIGATION, "<i data-feather=\"home\"></i>", "feather-home", "🏠"),
        IconItem("menu", IconLibrary.FEATHER, IconCategory.NAVIGATION, "<i data-feather=\"menu\"></i>", "feather-menu", "☰"),
        IconItem("search", IconLibrary.FEATHER, IconCategory.NAVIGATION, "<i data-feather=\"search\"></i>", "feather-search", "🔍"),
        IconItem("settings", IconLibrary.FEATHER, IconCategory.NAVIGATION, "<i data-feather=\"settings\"></i>", "feather-settings", "⚙"),
        IconItem("user", IconLibrary.FEATHER, IconCategory.NAVIGATION, "<i data-feather=\"user\"></i>", "feather-user", "👤"),
        IconItem("bell", IconLibrary.FEATHER, IconCategory.NAVIGATION, "<i data-feather=\"bell\"></i>", "feather-bell", "🔔"),
        IconItem("heart", IconLibrary.FEATHER, IconCategory.NAVIGATION, "<i data-feather=\"heart\"></i>", "feather-heart", "❤"),
        IconItem("shopping-cart", IconLibrary.FEATHER, IconCategory.BUSINESS, "<i data-feather=\"shopping-cart\"></i>", "feather-shopping-cart", "🛒"),

        IconItem("facebook", IconLibrary.FEATHER, IconCategory.SOCIAL, "<i data-feather=\"facebook\"></i>", "feather-facebook", "f"),
        IconItem("twitter", IconLibrary.FEATHER, IconCategory.SOCIAL, "<i data-feather=\"twitter\"></i>", "feather-twitter", "𝕏"),
        IconItem("instagram", IconLibrary.FEATHER, IconCategory.SOCIAL, "<i data-feather=\"instagram\"></i>", "feather-instagram", "📷"),
        IconItem("linkedin", IconLibrary.FEATHER, IconCategory.SOCIAL, "<i data-feather=\"linkedin\"></i>", "feather-linkedin", "in"),
        IconItem("github", IconLibrary.FEATHER, IconCategory.SOCIAL, "<i data-feather=\"github\"></i>", "feather-github", "🐙"),
        IconItem("youtube", IconLibrary.FEATHER, IconCategory.SOCIAL, "<i data-feather=\"youtube\"></i>", "feather-youtube", "▶"),

        IconItem("play", IconLibrary.FEATHER, IconCategory.MEDIA, "<i data-feather=\"play\"></i>", "feather-play", "▶"),
        IconItem("pause", IconLibrary.FEATHER, IconCategory.MEDIA, "<i data-feather=\"pause\"></i>", "feather-pause", "⏸"),
        IconItem("square", IconLibrary.FEATHER, IconCategory.MEDIA, "<i data-feather=\"square\"></i>", "feather-square", "⏹"),
        IconItem("volume-2", IconLibrary.FEATHER, IconCategory.MEDIA, "<i data-feather=\"volume-2\"></i>", "feather-volume-2", "🔊"),

        IconItem("arrow-up", IconLibrary.FEATHER, IconCategory.ARROWS, "<i data-feather=\"arrow-up\"></i>", "feather-arrow-up", "↑"),
        IconItem("arrow-down", IconLibrary.FEATHER, IconCategory.ARROWS, "<i data-feather=\"arrow-down\"></i>", "feather-arrow-down", "↓"),
        IconItem("arrow-left", IconLibrary.FEATHER, IconCategory.ARROWS, "<i data-feather=\"arrow-left\"></i>", "feather-arrow-left", "←"),
        IconItem("arrow-right", IconLibrary.FEATHER, IconCategory.ARROWS, "<i data-feather=\"arrow-right\"></i>", "feather-arrow-right", "→"),
        IconItem("chevron-up", IconLibrary.FEATHER, IconCategory.ARROWS, "<i data-feather=\"chevron-up\"></i>", "feather-chevron-up", "∧"),
        IconItem("chevron-down", IconLibrary.FEATHER, IconCategory.ARROWS, "<i data-feather=\"chevron-down\"></i>", "feather-chevron-down", "∨")
    )
}
