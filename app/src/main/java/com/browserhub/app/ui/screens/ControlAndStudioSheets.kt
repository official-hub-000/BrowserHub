package com.browserhub.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.data.model.AddressBarPosition
import com.browserhub.app.data.model.BrowserSettings
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.data.model.PageSuperpowerState
import com.browserhub.app.data.model.ReaderFont
import com.browserhub.app.data.model.ReaderPaper
import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.data.model.ShieldsConfig
import com.browserhub.app.data.model.StudioTheme
import com.browserhub.app.downloads.AcceleratedDownloadTask
import com.browserhub.app.downloads.DownloadStatus
import com.browserhub.app.ui.theme.FlagshipEmeraldDarkBg
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.theme.OledTextSecondary

@Composable
fun ControlMenuSheet(
    activeTab: BrowserTab,
    tabCount: Int,
    settings: BrowserSettings,
    shields: ShieldsConfig,
    isVpnConnected: Boolean,
    isSplitScreen: Boolean,
    isDevToolsOpen: Boolean,
    enabledExtensionCount: Int,
    pageSuperpowers: PageSuperpowerState = PageSuperpowerState(),
    onOpenPageSuperpowers: () -> Unit = {},
    onToggleForceDarkWeb: () -> Unit = {},
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onForward: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onToggleReaderMode: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenVideoGrabber: () -> Unit,
    onOpenFindInPage: () -> Unit,
    onAiSummarizePage: () -> Unit,
    onToggleSplitScreen: () -> Unit,
    onAdjustTextZoom: (Int) -> Unit,
    onOpenShields: () -> Unit,
    onOpenVpn: () -> Unit,
    onLaunchZapper: () -> Unit,
    onBypassUrl: (String) -> Unit,
    onFireWipe: () -> Unit,
    onOpenExtensions: () -> Unit,
    onToggleDevTools: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenAboutDiagnostics: () -> Unit,
    onToggleAddressBarPosition: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var showBypassInput by remember { mutableStateOf(false) }
    var bypassInput by remember(activeTab.id, activeTab.url) {
        mutableStateOf(if (!activeTab.isOnHub) activeTab.url else "")
    }

    ModalSheetScrim(onDismiss = onDismiss) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BrowserHub Control Menu",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "All tools & settings organized by category",
                    style = MaterialTheme.typography.bodySmall,
                    color = OledTextSecondary
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("control_menu_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = OledTextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (settings.comfortableUi) 15.dp else 10.dp)
        ) {
            // 1. SEARCH ENGINE & NAVIGATION
            item {
                MenuSectionHeader("1. SEARCH ENGINE & NAVIGATION", "$tabCount open ${if (tabCount == 1) "tab" else "tabs"}", browserColors.cyan)
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Default Search Engine",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SearchEngine.entries.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { engine ->
                                    val selected = engine == settings.searchEngine
                                    Surface(
                                        onClick = { onSelectSearchEngine(engine) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(engine.brandColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = engine.badgeLetter,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = engine.brandColor
                                                )
                                            }
                                            Text(
                                                text = engine.shortName,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("New Tab", Icons.Outlined.Add, browserColors.cyan, null, onNewTab, Modifier.weight(1f))
                        MenuActionTile("Incognito", Icons.Outlined.Security, browserColors.violet, null, onNewIncognitoTab, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Home", Icons.Outlined.Home, browserColors.emerald, null, onHome, Modifier.weight(1f))
                        MenuActionTile("Reload", Icons.Outlined.Refresh, browserColors.cyan, null, onReload, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Forward", Icons.AutoMirrored.Outlined.ArrowForward, MaterialTheme.colorScheme.onSurface, null, onForward, Modifier.weight(1f))
                        MenuActionTile("Bookmarks", Icons.Outlined.Bookmarks, browserColors.amber, null, onOpenBookmarks, Modifier.weight(1f))
                    }
                }
            }

            // 2. PAGE & READING TOOLS
            item {
                MenuSectionHeader("2. PAGE & READING TOOLS", "", browserColors.emerald)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Desktop Site", Icons.Outlined.DesktopWindows, browserColors.cyan, if (activeTab.isDesktopMode) "ON" else "OFF", onToggleDesktopSite, Modifier.weight(1f), active = activeTab.isDesktopMode)
                        MenuActionTile("Reader Mode", Icons.AutoMirrored.Outlined.MenuBook, browserColors.emerald, if (activeTab.isReaderMode) "ON" else "OFF", onToggleReaderMode, Modifier.weight(1f), active = activeTab.isReaderMode)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Translate Page", Icons.Outlined.Translate, browserColors.cyan, null, onOpenTranslator, Modifier.weight(1f))
                        MenuActionTile("Media Grabber", Icons.Outlined.VideoLibrary, browserColors.emerald, "VID·AUD", onOpenVideoGrabber, Modifier.weight(1f), active = true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Find in Page", Icons.Outlined.Search, browserColors.amber, null, onOpenFindInPage, Modifier.weight(1f))
                        MenuActionTile("AI Summarize Page", Icons.Outlined.AutoAwesome, browserColors.cyan, null, onAiSummarizePage, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Split Screen", Icons.Outlined.VerticalSplit, browserColors.violet, if (isSplitScreen) "ON" else "OFF", onToggleSplitScreen, Modifier.weight(1f), active = isSplitScreen)
                        MenuActionTile("History", Icons.Outlined.History, browserColors.cyan, null, onOpenHistory, Modifier.weight(1f))
                    }
                    val anySuperpowerOn = pageSuperpowers.forceDarkWeb ||
                        pageSuperpowers.autoScrollSpeed > 0 ||
                        pageSuperpowers.videoSpeed != 1.0f ||
                        pageSuperpowers.copyUnlockEnabled ||
                        pageSuperpowers.nightTintEnabled
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile(
                            title = "Page Superpowers",
                            icon = Icons.Outlined.AutoAwesome,
                            iconTint = browserColors.violet,
                            pill = if (anySuperpowerOn) "ACTIVE" else "X-RAY",
                            onClick = onOpenPageSuperpowers,
                            modifier = Modifier.weight(1f),
                            active = anySuperpowerOn
                        )
                        MenuActionTile(
                            title = "AMOLED Dark Web",
                            icon = Icons.Outlined.DarkMode,
                            iconTint = browserColors.cyan,
                            pill = if (pageSuperpowers.forceDarkWeb) "ON" else "OFF",
                            onClick = onToggleForceDarkWeb,
                            modifier = Modifier.weight(1f),
                            active = pageSuperpowers.forceDarkWeb
                        )
                    }

                    // Page Text Zoom - 100% +
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.FormatSize, contentDescription = null, tint = browserColors.cyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Page Text Zoom",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = { onAdjustTextZoom(-10) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, browserColors.cardBorder)
                            ) {
                                Text(" − ", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                            Text(
                                text = "${settings.pageTextZoom}%",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Surface(
                                onClick = { onAdjustTextZoom(10) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, browserColors.cardBorder)
                            ) {
                                Text(" + ", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }

            // 3. PRIVACY, ADBLOCK & SECURITY
            item {
                MenuSectionHeader("3. PRIVACY, ADBLOCK & SECURITY", "", browserColors.amber)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("AdBlock Shields", Icons.Outlined.Shield, browserColors.emerald, if (shields.shieldsEnabled) "ON" else "OFF", onOpenShields, Modifier.weight(1f), active = shields.shieldsEnabled)
                        MenuActionTile("Built-in VPN", Icons.Outlined.VpnLock, browserColors.emerald, if (isVpnConnected) "ON" else "OFF", onOpenVpn, Modifier.weight(1f), active = isVpnConnected)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Element Zapper", Icons.Outlined.Bolt, browserColors.crimson, if (shields.elementZapperActive) "ON" else "OFF", onLaunchZapper, Modifier.weight(1f), active = shields.elementZapperActive)
                        MenuActionTile("Bypass Link", Icons.Outlined.LinkOff, browserColors.amber, "1-TAP", {
                            if (!activeTab.isOnHub && activeTab.url.isNotBlank()) {
                                onBypassUrl(activeTab.url)
                            } else {
                                showBypassInput = !showBypassInput
                            }
                        }, Modifier.weight(1f), active = showBypassInput)
                    }
                    AnimatedVisibility(visible = showBypassInput) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, browserColors.amber.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = bypassInput,
                                    onValueChange = { bypassInput = it },
                                    placeholder = { Text("Paste Linkvertise / GPLink / shortlink") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("menu_bypass_input")
                                )
                                Button(
                                    onClick = {
                                        if (bypassInput.isNotBlank()) {
                                            onBypassUrl(bypassInput.trim())
                                            showBypassInput = false
                                        }
                                    },
                                    enabled = bypassInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = browserColors.amber,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("menu_bypass_go_button")
                                ) {
                                    Text("Go", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    MenuActionTile("Fire Wipe Data", Icons.Outlined.LocalFireDepartment, Color(0xFFF97316), "WIPE", onFireWipe, Modifier.fillMaxWidth())
                }
            }

            // 4. EXTENSIONS & ADVANCED TOOLS
            item {
                MenuSectionHeader("4. EXTENSIONS & ADVANCED TOOLS", "", browserColors.violet)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Extensions Hub", Icons.Outlined.Extension, browserColors.cyan, "$enabledExtensionCount Active", onOpenExtensions, Modifier.weight(1f), active = enabledExtensionCount > 0)
                        MenuActionTile("Web DevTools", Icons.Outlined.Code, browserColors.amber, if (isDevToolsOpen) "ON" else "OFF", onToggleDevTools, Modifier.weight(1f), active = isDevToolsOpen)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuActionTile("Games Hub", Icons.Outlined.SportsEsports, browserColors.crimson, "18", onOpenGames, Modifier.weight(1f))
                        MenuActionTile("Speed Benchmark", Icons.Outlined.Speed, browserColors.emerald, null, onOpenBenchmark, Modifier.weight(1f))
                    }
                    MenuActionTile("About & Diagnostics", Icons.Outlined.Info, browserColors.cyan, "Probe", onOpenAboutDiagnostics, Modifier.fillMaxWidth())
                }
            }

            // 5. APPEARANCE & LAYOUT
            item {
                MenuSectionHeader("5. APPEARANCE & LAYOUT", "", browserColors.cyan)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuActionTile("Address Bar", Icons.Outlined.SwapVert, browserColors.cyan, settings.addressBarPosition.label, onToggleAddressBarPosition, Modifier.weight(1f), active = true)
                    MenuActionTile("Themes & Downloads", Icons.Outlined.Palette, browserColors.violet, "7 Themes", onOpenStudio, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MenuSectionHeader(title: String, trailing: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
        if (trailing.isNotBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = OledTextSecondary
            )
        }
    }
}

@Composable
private fun MenuActionTile(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    pill: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else browserColors.cardBorder
        ),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!pill.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else browserColors.cardBorder)
                ) {
                    Text(
                        text = pill,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = if (active) MaterialTheme.colorScheme.primary else OledTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomizationStudioSheet(
    settings: BrowserSettings,
    webViewVersion: String,
    downloads: List<AcceleratedDownloadTask>,
    onSetAddressBarPosition: (AddressBarPosition) -> Unit,
    onSetComfortableUi: (Boolean) -> Unit,
    onSetDesktopDevTools: (Boolean) -> Unit,
    onSelectTheme: (StudioTheme) -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectReaderPaper: (ReaderPaper) -> Unit,
    onSelectReaderFont: (ReaderFont) -> Unit,
    onStartDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var downloadUrlInput by remember { mutableStateOf("") }

    ModalSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Customization Studio & Power Tools",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("studio_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = OledTextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ERGONOMICS & DISPLAY
            item {
                Text(
                    text = "ERGONOMICS & DISPLAY",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = OledTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioChoiceChip(
                        label = "Bottom Bar (One-Handed)",
                        selected = settings.addressBarPosition == AddressBarPosition.BOTTOM,
                        onClick = { onSetAddressBarPosition(AddressBarPosition.BOTTOM) },
                        modifier = Modifier.weight(1f)
                    )
                    StudioChoiceChip(
                        label = "Classic Top Omnibox",
                        selected = settings.addressBarPosition == AddressBarPosition.TOP,
                        onClick = { onSetAddressBarPosition(AddressBarPosition.TOP) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Comfortable UI", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Switch(checked = settings.comfortableUi, onCheckedChange = onSetComfortableUi)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Desktop DevTools", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Switch(checked = settings.desktopDevToolsEnabled, onCheckedChange = onSetDesktopDevTools)
                        }
                    }
                }
            }

            // STUDIO THEMES (7 CURATED PALETTES)
            item {
                Text(
                    text = "STUDIO THEMES (7 CURATED PALETTES)",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = OledTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioTheme.entries.forEach { theme ->
                        val selected = theme == settings.studioTheme
                        Surface(
                            onClick = { onSelectTheme(theme) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_theme_${theme.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(theme.dotColor)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = theme.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = theme.badge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else OledTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // RENDERING ENGINE
            item {
                Text(
                    text = "RENDERING ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = OledTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Android System WebView",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$webViewVersion — Real system WebView engine running locally",
                                style = MaterialTheme.typography.bodySmall,
                                color = OledTextSecondary
                            )
                        }
                        Surface(shape = RoundedCornerShape(999.dp), color = FlagshipEmeraldDarkBg) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = browserColors.emerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // SEARCH ENGINE
            item {
                Text(
                    text = "SEARCH ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = OledTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchEngine.entries, key = { it.name }) { engine ->
                        val selected = engine == settings.searchEngine
                        Surface(
                            onClick = { onSelectSearchEngine(engine) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder)
                        ) {
                            Text(
                                text = engine.shortName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // READER MODE PAPER & FONT
            item {
                Text(
                    text = "READER MODE PAPER & FONT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = OledTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderPaper.entries.forEach { paper ->
                        StudioChoiceChip(
                            label = paper.label,
                            selected = settings.readerPaper == paper,
                            onClick = { onSelectReaderPaper(paper) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFont.entries.forEach { font ->
                        StudioChoiceChip(
                            label = font.label,
                            selected = settings.readerFont == font,
                            onClick = { onSelectReaderFont(font) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 16-THREAD DOWNLOAD ACCELERATOR
            item {
                Text(
                    text = "16-THREAD DOWNLOAD ACCELERATOR",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = OledTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = downloadUrlInput,
                                onValueChange = { downloadUrlInput = it },
                                placeholder = { Text("Paste direct file URL (ISO, ZIP, APK, MP4)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (downloadUrlInput.isNotBlank()) {
                                        onStartDownload(downloadUrlInput.trim())
                                        downloadUrlInput = ""
                                    }
                                },
                                enabled = downloadUrlInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (downloads.isEmpty()) {
                            Text(
                                text = "Parallel Range-request chunking splits large downloads into up to 16 concurrent streams and saves to Downloads/BrowserHub.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OledTextSecondary
                            )
                        } else {
                            downloads.forEach { task ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    border = BorderStroke(1.dp, browserColors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = task.fileName,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${task.threadsUsed}x",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = browserColors.cyan
                                            )
                                            if (task.status == DownloadStatus.RUNNING) {
                                                IconButton(
                                                    onClick = { onCancelDownload(task.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Close, contentDescription = "Cancel", modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                        if (task.status == DownloadStatus.RUNNING && task.totalBytes > 0) {
                                            Spacer(Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { task.progressFraction },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = when (task.status) {
                                                DownloadStatus.RUNNING -> "${task.downloadedBytes / 1024} KB / ${if (task.totalBytes > 0) "${task.totalBytes / 1024} KB" else "streaming"}"
                                                DownloadStatus.COMPLETED -> "Saved: ${task.savedLocation}"
                                                DownloadStatus.FAILED -> "Error: ${task.errorMessage}"
                                                DownloadStatus.CANCELLED -> "Cancelled"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (task.status) {
                                                DownloadStatus.COMPLETED -> browserColors.emerald
                                                DownloadStatus.FAILED -> browserColors.crimson
                                                else -> OledTextSecondary
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
        ),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}
