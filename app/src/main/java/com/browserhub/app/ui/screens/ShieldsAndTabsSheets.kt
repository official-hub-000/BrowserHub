package com.browserhub.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.data.model.ShieldsConfig
import com.browserhub.app.data.model.TabWorkspace
import com.browserhub.app.ui.theme.FlagshipCrimsonDarkBg
import com.browserhub.app.ui.theme.FlagshipEmeraldDarkBg
import com.browserhub.app.ui.theme.FlagshipVioletDarkBg
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.theme.OledTextSecondary

@Composable
fun ModalSheetScrim(
    onDismiss: () -> Unit,
    maxHeightFraction: Float = 0.88f,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onDismiss)
    val browserColors = LocalBrowserColors.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (appeared) 0.72f else 0f,
        animationSpec = tween(200),
        label = "modal_scrim_alpha"
    )
    val sheetScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
        label = "modal_sheet_scale"
    )
    val sheetOffsetY by animateFloatAsState(
        targetValue = if (appeared) 0f else 72f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "modal_sheet_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, browserColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .graphicsLayer {
                    scaleX = sheetScale
                    scaleY = sheetScale
                    translationY = sheetOffsetY
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                content()
            }
        }
    }
}

@Composable
fun SearchEnginePickerSheet(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    ModalSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Choose Search Engine",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("search_picker_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = OledTextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchEngine.entries, key = { it.name }) { engine ->
                val isSelected = engine == selectedEngine
                Surface(
                    onClick = {
                        onSelectEngine(engine)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("picker_engine_${engine.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(engine.brandColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = engine.badgeLetter,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = engine.brandColor
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = engine.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShieldsUpSheet(
    shields: ShieldsConfig,
    activeTab: BrowserTab,
    isBypassingLink: Boolean = false,
    onBypassUrl: (String) -> Unit = {},
    onUpdateShields: ((ShieldsConfig) -> ShieldsConfig) -> Unit,
    onLaunchZapper: () -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var bypassInput by remember(activeTab.id, activeTab.url) {
        mutableStateOf(if (!activeTab.isOnHub) activeTab.url else "")
    }
    ModalSheetScrim(onDismiss = onDismiss) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FlagshipEmeraldDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = browserColors.emerald,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (shields.shieldsEnabled) "BrowserHub Shields UP" else "BrowserHub Shields DOWN",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ad, popup, CAPTCHA, shortlink & tracker shield",
                    style = MaterialTheme.typography.bodySmall,
                    color = OledTextSecondary
                )
            }
            Surface(
                onClick = { onUpdateShields { it.copy(shieldsEnabled = !it.shieldsEnabled) } },
                shape = RoundedCornerShape(999.dp),
                color = if (shields.shieldsEnabled) FlagshipEmeraldDarkBg else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (shields.shieldsEnabled) browserColors.emerald else browserColors.cardBorder
                ),
                modifier = Modifier.testTag("shields_master_toggle")
            ) {
                Text(
                    text = if (shields.shieldsEnabled) "ENABLED" else "PAUSED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (shields.shieldsEnabled) browserColors.emerald else OledTextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("shields_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = OledTextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3 Live Metrics
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShieldMetricTile("Ads Blocked (Tab)", activeTab.adsBlocked.toString(), browserColors.emerald, Modifier.weight(1f))
                    ShieldMetricTile("Trackers Stopped", activeTab.trackersStopped.toString(), browserColors.cyan, Modifier.weight(1f))
                    ShieldMetricTile("Payload Stripped", "${activeTab.kbStripped} KB", browserColors.violet, Modifier.weight(1f))
                }
            }

            // uBlock Element Zapper Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FlagshipCrimsonDarkBg.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, browserColors.crimson.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = browserColors.crimson,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "uBlock Element Zapper",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap any annoying banner, sticky popup, or ad box on the page to vaporize it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OledTextSecondary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = onLaunchZapper,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = browserColors.crimson,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("shields_launch_zapper")
                        ) {
                            Text(
                                text = if (shields.elementZapperActive) "Stop Zapper" else "Launch Zapper",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // 1-Click Shortlink / Ad-Wrapper Bypass Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, browserColors.amber.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = browserColors.amber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "1-Click Shortlink & Ad-Link Bypass",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Bypasses 90+ Ad-links, Social Unlocks, Pasters & Shorteners (Linkvertise, AdMaven/Lootlabs, Work.ink, Rekonise, Sub2Unlock, Mboost, Pastebin, Rentry, Cuty.io, Bit.ly, etc.).",
                            style = MaterialTheme.typography.bodySmall,
                            color = OledTextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = bypassInput,
                                onValueChange = { bypassInput = it },
                                placeholder = { Text("Paste any shortlink URL") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("shields_bypass_input")
                            )
                            Button(
                                onClick = {
                                    if (bypassInput.isNotBlank()) {
                                        onBypassUrl(bypassInput.trim())
                                    }
                                },
                                enabled = bypassInput.isNotBlank() && !isBypassingLink,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = browserColors.amber,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("shields_bypass_button")
                            ) {
                                if (isBypassingLink) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                                } else {
                                    Text("Bypass", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Protection Switches (10 comprehensive engines)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShieldToggleRow(
                        title = "Pop-up & Notification Spam Blocker",
                        subtitle = "Kills web push requests, popunder tabs, exit traps & 18+ click-hijack overlays",
                        checked = shields.popupNotificationBlocker,
                        onCheckedChange = { v -> onUpdateShields { it.copy(popupNotificationBlocker = v) } }
                    )
                    ShieldToggleRow(
                        title = "YouTube & Web Direct Ad-Skipper",
                        subtitle = "Strips YouTube player/feed ads, clicks Skip Ad & blocks 18+ ad networks",
                        checked = shields.autoSkipAds,
                        onCheckedChange = { v -> onUpdateShields { it.copy(autoSkipAds = v) } }
                    )
                    ShieldToggleRow(
                        title = "Zero-CAPTCHA & Bot-Gate Bypass",
                        subtitle = "Uses clean native Chrome UA & auto-switches YouTube bot checks to Ad-Free Player",
                        checked = shields.noCaptchaShield,
                        onCheckedChange = { v -> onUpdateShields { it.copy(noCaptchaShield = v) } }
                    )
                    ShieldToggleRow(
                        title = "Auto-Bypass Shortlinks",
                        subtitle = "Automatically jumps past shortlinks, ad-links, social unlocks & pasters",
                        checked = shields.autoBypassShortlinks,
                        onCheckedChange = { v -> onUpdateShields { it.copy(autoBypassShortlinks = v) } }
                    )
                    ShieldToggleRow(
                        title = "Native Ad Blocker",
                        subtitle = "Blocks Google Ads, DoubleClick, Taboola, Outbrain & ad scripts",
                        checked = shields.nativeAdBlocker,
                        onCheckedChange = { v -> onUpdateShields { it.copy(nativeAdBlocker = v) } }
                    )
                    ShieldToggleRow(
                        title = "Analytics & Tracker Shield",
                        subtitle = "Blocks Facebook Pixel, Hotjar, Mixpanel, Sentry & telemetry beacons",
                        checked = shields.analyticsTrackerShield,
                        onCheckedChange = { v -> onUpdateShields { it.copy(analyticsTrackerShield = v) } }
                    )
                    ShieldToggleRow(
                        title = "Cookie-Wall Killer",
                        subtitle = "Automatically hides intrusive GDPR cookie banners & overlays",
                        checked = shields.cookieWallKiller,
                        onCheckedChange = { v -> onUpdateShields { it.copy(cookieWallKiller = v) } }
                    )
                    ShieldToggleRow(
                        title = "Canvas & Hardware Spoofing",
                        subtitle = "Randomizes Canvas fingerprint pixels & masks hardware metrics",
                        checked = shields.canvasHardwareSpoofing,
                        onCheckedChange = { v -> onUpdateShields { it.copy(canvasHardwareSpoofing = v) } }
                    )
                    ShieldToggleRow(
                        title = "Tracking-Parameter Stripper",
                        subtitle = "Cleans ?utm_*, ?fbclid=, ?gclid=, and ?mc_eid= from navigated links",
                        checked = shields.trackingParamStripper,
                        onCheckedChange = { v -> onUpdateShields { it.copy(trackingParamStripper = v) } }
                    )
                    ShieldToggleRow(
                        title = "HTTPS-Only Upgrades",
                        subtitle = "Automatically upgrades insecure http:// navigation to https://",
                        checked = shields.httpsOnlyUpgrades,
                        onCheckedChange = { v -> onUpdateShields { it.copy(httpsOnlyUpgrades = v) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShieldMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = accent,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = OledTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShieldToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OledTextSecondary
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = browserColors.emerald
                )
            )
        }
    }
}

@Composable
fun TabVaultSheet(
    tabs: List<BrowserTab>,
    activeTabId: String,
    activeWebViewIds: Set<String> = emptySet(),
    selectedWorkspace: TabWorkspace,
    closedTabsCount: Int = 0,
    tabSearchQuery: String = "",
    onTabSearchQueryChange: (String) -> Unit = {},
    onSelectWorkspace: (TabWorkspace) -> Unit,
    onSelectTab: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleSleep: (String) -> Unit,
    onDuplicateTab: (String) -> Unit = {},
    onCloseTab: (String) -> Unit,
    onReopenClosedTab: () -> Unit = {},
    onCloseOtherTabs: (String) -> Unit = {},
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onFireWipe: () -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    val incognitoCount = remember(tabs) { tabs.count { it.isIncognito } }
    val totalRamMb = remember(tabs) { tabs.sumOf { it.estimatedRamMb } }
    val filteredTabs = remember(tabs, selectedWorkspace, tabSearchQuery) {
        val byWorkspace = if (selectedWorkspace == TabWorkspace.ALL) tabs
        else tabs.filter { it.workspace == selectedWorkspace }
        val q = tabSearchQuery.trim()
        val searched = if (q.isBlank()) byWorkspace else {
            byWorkspace.filter {
                it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true)
            }
        }
        searched.sortedByDescending { it.isPinned }
    }

    ModalSheetScrim(onDismiss = onDismiss) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tabs (${tabs.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${activeWebViewIds.size} Live WebView • $totalRamMb MB RAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = OledTextSecondary
                )
            }

            // Incognito Vault pill
            Surface(
                onClick = onNewIncognitoTab,
                shape = RoundedCornerShape(12.dp),
                color = FlagshipVioletDarkBg,
                border = BorderStroke(1.dp, browserColors.violet.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("tab_vault_incognito_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = browserColors.violet,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Incognito ($incognitoCount)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = browserColors.violet
                    )
                }
            }

            // Fire Wipe button
            Surface(
                onClick = onFireWipe,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF3B1206),
                border = BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.65f)),
                modifier = Modifier.testTag("tab_vault_fire_wipe_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Fire Wipe",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF97316)
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(34.dp)
                    .testTag("tab_vault_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = OledTextSecondary)
            }
        }

        // Tab Search + Undo Close + Close Others Bar
        if (tabs.size > 1 || closedTabsCount > 0 || tabSearchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tabSearchQuery,
                    onValueChange = onTabSearchQueryChange,
                    placeholder = { Text("Search open tabs...", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_vault_search_input")
                )
                AnimatedVisibility(
                    visible = closedTabsCount > 0,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f)
                ) {
                    Surface(
                        onClick = onReopenClosedTab,
                        shape = RoundedCornerShape(12.dp),
                        color = browserColors.amber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, browserColors.amber.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("tab_vault_undo_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Reopen closed", tint = browserColors.amber, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Undo ($closedTabsCount)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = browserColors.amber
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Workspace Filter Chips + Close Others
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TabWorkspace.entries, key = { it.name }) { ws ->
                val count = if (ws == TabWorkspace.ALL) tabs.size else tabs.count { it.workspace == ws }
                val selected = ws == selectedWorkspace
                Surface(
                    onClick = { onSelectWorkspace(ws) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                    ),
                    modifier = Modifier.testTag("workspace_chip_${ws.name.lowercase()}")
                ) {
                    Text(
                        text = if (count > 0 && ws != TabWorkspace.ALL) "${ws.label} ($count)" else ws.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else OledTextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
            if (tabs.size > 1) {
                item(key = "close_others_action") {
                    Surface(
                        onClick = { onCloseOtherTabs(activeTabId) },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, browserColors.crimson.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("tab_vault_close_others")
                    ) {
                        Text(
                            text = "Close Others",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = browserColors.crimson,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tab Cards
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredTabs, key = { it.id }) { tab ->
                val isCurrent = tab.id == activeTabId
                val isLiveWebView = tab.id in activeWebViewIds
                Surface(
                    onClick = { onSelectTab(tab.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .testTag("tab_vault_card_${tab.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (tab.isIncognito) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = browserColors.violet,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = tab.title.ifBlank { "New Tab" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (tab.isOnHub) "browserhub://start" else tab.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = OledTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = "${tab.estimatedRamMb} MB",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (tab.isSleeping) browserColors.emerald else OledTextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = tab.workspace.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = browserColors.cyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (isLiveWebView && !tab.isOnHub) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = browserColors.emerald.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "Live WebView",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            color = browserColors.emerald,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (tab.isSleeping) {
                                    Text(
                                        text = "Sleeping",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = browserColors.amber
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onDuplicateTab(tab.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(34.dp)
                                .testTag("duplicate_tab_${tab.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Duplicate tab",
                                tint = browserColors.cyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onTogglePin(tab.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = "Pin tab",
                                tint = if (tab.isPinned) MaterialTheme.colorScheme.primary else OledTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onToggleSleep(tab.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bedtime,
                                contentDescription = "Sleep tab",
                                tint = if (tab.isSleeping) browserColors.emerald else OledTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onCloseTab(tab.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(34.dp)
                                .testTag("close_tab_${tab.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close tab",
                                tint = OledTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // + New Speed Tab wide button
        Button(
            onClick = onNewTab,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .height(48.dp)
                .testTag("tab_vault_new_tab_button")
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New Speed Tab", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}
