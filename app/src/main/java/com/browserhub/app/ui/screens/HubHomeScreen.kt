package com.browserhub.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.R
import com.browserhub.app.data.local.BookmarkEntity
import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.ui.theme.FlagshipEmeraldDarkBg
import com.browserhub.app.ui.theme.FlagshipVioletDarkBg
import com.browserhub.app.ui.theme.HubAccentList
import com.browserhub.app.ui.theme.LocalBrowserColors

private val quickSearchChips = listOf("world news", "weather today", "trending videos", "ai tools")

@Composable
fun HubHomeScreen(
    isIncognito: Boolean,
    shieldsEnabled: Boolean,
    isVpnConnected: Boolean,
    searchEngine: SearchEngine,
    speedDialSites: List<BookmarkEntity>,
    tabCount: Int,
    enabledExtensionCount: Int,
    showAddShortcutDialog: Boolean,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenVpn: () -> Unit,
    onOpenVideoGrabber: () -> Unit,
    onOpenAskAi: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenTabVault: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onShowAddShortcut: (Boolean) -> Unit,
    onAddShortcut: (String, String) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    comfortableUi: Boolean = true,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    var editSpeedDial by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        browserColors.hubGlowPrimary.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.maxDimension * 0.42f
                ),
                radius = size.maxDimension * 0.42f,
                center = Offset(size.width * 0.5f, size.height * 0.14f)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = if (comfortableUi) 16.dp else 12.dp,
                    vertical = if (comfortableUi) 18.dp else 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (comfortableUi) 20.dp else 14.dp)
            ) {
                // Hero Globe + Gradient BrowserHub Wordmark + 3 Status Badges
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            browserColors.hubGlowPrimary.copy(alpha = 0.25f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Transparent,
                                border = BorderStroke(
                                    1.2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Language,
                                        contentDescription = stringResource(R.string.cd_app_logo),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isIncognito) "Incognito Vault" else "BrowserHub",
                            style = TextStyle(
                                brush = Brush.linearGradient(
                                    listOf(browserColors.cyan, browserColors.violet)
                                ),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isIncognito) {
                                "Private session — zero history or cookies kept"
                            } else {
                                "Fast, private, ad-free & AI-ready"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPillBadge(
                                text = if (shieldsEnabled) "AD-BLOCK ON" else "AD-BLOCK OFF",
                                bgColor = if (shieldsEnabled) FlagshipEmeraldDarkBg else MaterialTheme.colorScheme.surface,
                                textColor = if (shieldsEnabled) browserColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                borderColor = (if (shieldsEnabled) browserColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.4f)
                            )
                            StatusPillBadge(
                                text = "AI READY",
                                bgColor = MaterialTheme.colorScheme.primaryContainer,
                                textColor = MaterialTheme.colorScheme.primary,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            StatusPillBadge(
                                text = "NO TRACKING",
                                bgColor = FlagshipVioletDarkBg,
                                textColor = browserColors.violet,
                                borderColor = browserColors.violet.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                // Elevated Dark Search Card
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Search bar inside card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, browserColors.cardBorder, RoundedCornerShape(14.dp))
                                    .padding(start = 12.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = searchInput,
                                        onValueChange = { searchInput = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                if (searchInput.isNotBlank()) {
                                                    onSubmitQuery(searchInput)
                                                    searchInput = ""
                                                }
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("start_card_search_input"),
                                        decorationBox = { inner ->
                                            if (searchInput.isEmpty()) {
                                                Text(
                                                    text = "Search ${searchEngine.shortName} or type any URL...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                }
                                Surface(
                                    onClick = {
                                        if (searchInput.isNotBlank()) {
                                            onSubmitQuery(searchInput)
                                            searchInput = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("start_card_search_go")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Engine Selection Row
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(SearchEngine.entries, key = { it.name }) { engine ->
                                    val selected = engine == searchEngine
                                    Surface(
                                        onClick = { onSelectSearchEngine(engine) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                                        ),
                                        modifier = Modifier.testTag("home_engine_${engine.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(engine.brandColor.copy(alpha = 0.22f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = engine.badgeLetter,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = engine.brandColor
                                                )
                                            }
                                            Text(
                                                text = engine.shortName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick search suggestion chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(quickSearchChips) { query ->
                                    Surface(
                                        onClick = { onSubmitQuery(query) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.background,
                                        border = BorderStroke(1.dp, browserColors.cardBorder)
                                    ) {
                                        Text(
                                            text = query,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // QUICK WEBSITES 4x2 Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "QUICK WEBSITES",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { editSpeedDial = !editSpeedDial },
                                    modifier = Modifier.testTag("hub_edit_shortcuts_button")
                                ) {
                                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (editSpeedDial) "Done" else "Edit", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = { onShowAddShortcut(true) },
                                    modifier = Modifier.testTag("hub_add_shortcut_button")
                                ) {
                                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pin", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 600.dp) 4 else 4
                            val rows = speedDialSites.chunked(columns)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                rows.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowItems.forEach { site ->
                                            QuickWebsiteTile(
                                                site = site,
                                                isEditing = editSpeedDial,
                                                onClick = { onOpenUrl(site.url) },
                                                onDelete = { onDeleteShortcut(site.id) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // BROWSER TOOLS & PROTECTION (Screenshot 2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "BROWSER TOOLS & PROTECTION",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val tools = listOf(
                            ToolGridSpec("Extensions Hub", "$enabledExtensionCount active", Icons.Outlined.Extension, MaterialTheme.colorScheme.primary, onOpenExtensions),
                            ToolGridSpec("Built-in VPN", if (isVpnConnected) "ON • Encrypted" else "OFF • Free DoH", Icons.Outlined.VpnLock, browserColors.emerald, onOpenVpn),
                            ToolGridSpec("Media Grabber", "Video • Audio • Photo", Icons.Outlined.VideoLibrary, browserColors.emerald, onOpenVideoGrabber),
                            ToolGridSpec("BrowserHub AI", "No key • Instant", Icons.Outlined.AutoAwesome, MaterialTheme.colorScheme.primary, onOpenAskAi),
                            ToolGridSpec("Customization", "7 Studio Themes", Icons.Outlined.Palette, browserColors.violet, onOpenStudio),
                            ToolGridSpec("Tab Vault", "$tabCount open tabs", Icons.Outlined.Tab, browserColors.amber, onOpenTabVault),
                            ToolGridSpec("Games Hub", "18 Playable", Icons.Outlined.SportsEsports, browserColors.crimson, onOpenGames),
                            ToolGridSpec("Speed Benchmark", "JS / Wasm / GPU", Icons.Outlined.Speed, browserColors.emerald, onOpenBenchmark),
                            ToolGridSpec("Bookmarks", "Saved pages", Icons.Outlined.Bookmarks, browserColors.amber, onOpenBookmarks),
                            ToolGridSpec("History", "Visited sites", Icons.Outlined.History, MaterialTheme.colorScheme.primary, onOpenHistory)
                        )

                        tools.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { tool ->
                                    Surface(
                                        onClick = tool.onClick,
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, browserColors.cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(tool.accent.copy(alpha = 0.14f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = tool.icon,
                                                    contentDescription = null,
                                                    tint = tool.accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tool.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = tool.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tool.accent,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddShortcutDialog) {
        AddShortcutDialog(
            onDismiss = { onShowAddShortcut(false) },
            onSave = onAddShortcut
        )
    }
}

private data class ToolGridSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun StatusPillBadge(
    text: String,
    bgColor: Color,
    textColor: Color,
    borderColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun QuickWebsiteTile(
    site: BookmarkEntity,
    isEditing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = modifier.testTag("speed_dial_tile_${site.id}")
    ) {
        Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DomainMonogram(
                    label = site.title,
                    accentIndex = site.accentIndex,
                    sizeDp = 36
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = site.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            AnimatedVisibility(
                visible = isEditing,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(24.dp)
                        .testTag("delete_speed_dial_${site.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_delete_item),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DomainMonogram(
    label: String,
    accentIndex: Int,
    sizeDp: Int = 36,
    modifier: Modifier = Modifier
) {
    val accent = HubAccentList[accentIndex.coerceIn(0, HubAccentList.lastIndex)]
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}

@Composable
private fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_shortcut_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dialog_field_title)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shortcut_title_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.dialog_field_url)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shortcut_url_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.testTag("shortcut_save_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("shortcut_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
