package com.browserhub.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.browserhub.app.data.model.PageSuperpowerState
import com.browserhub.app.data.model.PageXRayReport
import com.browserhub.app.ui.theme.FlagshipCyanDarkBg
import com.browserhub.app.ui.theme.FlagshipVioletDarkBg
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.theme.OledTextSecondary
import kotlin.math.abs

@Composable
fun PageSuperpowersSheet(
    superpowers: PageSuperpowerState,
    xRayReport: PageXRayReport?,
    isInspectingXRay: Boolean,
    onRescanXRay: () -> Unit,
    onToggleForceDarkWeb: () -> Unit,
    onToggleNightTint: () -> Unit,
    onToggleCopyUnlock: () -> Unit,
    onSelectAutoScrollSpeed: (Int) -> Unit,
    onSelectVideoSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    val videoSpeeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)

    ModalSheetScrim(onDismiss = onDismiss) {
        // Header
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
                    .background(FlagshipVioletDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = browserColors.violet,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Page Superpowers & X-Ray",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Live DOM inspector, AMOLED Dark Web, Teleprompter & Speed Boost",
                    style = MaterialTheme.typography.bodySmall,
                    color = OledTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("superpowers_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = OledTextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live Page X-Ray & Privacy Risk Scanner
            item {
                val riskColor = when (xRayReport?.privacyRiskLabel) {
                    "Elevated" -> browserColors.crimson
                    "Moderate" -> browserColors.amber
                    else -> browserColors.emerald
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FlagshipCyanDarkBg.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, browserColors.cyan.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("xray_scanner_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Radar,
                                contentDescription = null,
                                tint = browserColors.cyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live Page X-Ray & Privacy Scan",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = xRayReport?.title?.ifBlank { xRayReport.url } ?: "Tap Rescan to inspect active DOM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OledTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (xRayReport != null) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = riskColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, riskColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${xRayReport.privacyRiskLabel} Risk",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = riskColor,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (xRayReport != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                XRayStatBox("Words", "${xRayReport.wordCount} (${xRayReport.readingMinutes}m)", browserColors.cyan, Modifier.weight(1f))
                                XRayStatBox("3P Scripts", "${xRayReport.thirdPartyScriptCount}/${xRayReport.scriptCount}", riskColor, Modifier.weight(1f))
                                XRayStatBox("Links/Img", "${xRayReport.linkCount}/${xRayReport.imageCount}", browserColors.violet, Modifier.weight(1f))
                                XRayStatBox("Cookies", if (xRayReport.hasCookies) "Active" else "None", if (xRayReport.hasCookies) browserColors.amber else browserColors.emerald, Modifier.weight(1f))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            FilledTonalButton(
                                onClick = onRescanXRay,
                                enabled = !isInspectingXRay,
                                modifier = Modifier.testTag("xray_rescan_button")
                            ) {
                                if (isInspectingXRay) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rescan DOM")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Display & Copy Unlocker Switches
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuperpowerToggleCard(
                        icon = Icons.Outlined.DarkMode,
                        tint = browserColors.violet,
                        title = "AMOLED Force Dark Web",
                        subtitle = "Turns harsh white websites pitch black while preserving true-color images & videos",
                        checked = superpowers.forceDarkWeb,
                        onCheckedChange = { onToggleForceDarkWeb() },
                        testTag = "superpower_force_dark_switch"
                    )
                    SuperpowerToggleCard(
                        icon = Icons.Outlined.Bedtime,
                        tint = browserColors.amber,
                        title = "Blue-Light Night Dimmer",
                        subtitle = "Warm amber optical filter over web pages for zero-strain night reading",
                        checked = superpowers.nightTintEnabled,
                        onCheckedChange = { onToggleNightTint() },
                        testTag = "superpower_night_tint_switch"
                    )
                    SuperpowerToggleCard(
                        icon = Icons.Outlined.ContentCopy,
                        tint = browserColors.emerald,
                        title = "Copy & Right-Click Unlocker",
                        subtitle = "Unlocks blocked text selection, long-press copy & context menus on strict sites",
                        checked = superpowers.copyUnlockEnabled,
                        onCheckedChange = { onToggleCopyUnlock() },
                        testTag = "superpower_copy_unlock_switch"
                    )
                }
            }

            // 3. Teleprompter Auto-Scroll
            item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SwipeVertical, contentDescription = null, tint = browserColors.cyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Teleprompter Auto-Scroll",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hands-free 60fps smooth reading scroll",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OledTextSecondary
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0 to "Off", 1 to "1x Gentle", 2 to "2x Read", 3 to "3x Fast").forEach { (lvl, label) ->
                                val selected = superpowers.autoScrollSpeed == lvl
                                val bg by animateColorAsState(
                                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "autoscroll_bg"
                                )
                                Surface(
                                    onClick = { onSelectAutoScrollSpeed(lvl) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = bg,
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("autoscroll_speed_$lvl")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp
                                            ),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Universal HTML5 Video Speed Booster
            item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FastForward, contentDescription = null, tint = browserColors.emerald, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Universal Video & Audio Speed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Overrides playback speed on YouTube, lectures & any HTML5 player",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OledTextSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${superpowers.videoSpeed}x",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(videoSpeeds) { spd ->
                                val selected = abs(superpowers.videoSpeed - spd) < 0.01f
                                Surface(
                                    onClick = { onSelectVideoSpeed(spd) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                                        .testTag("video_speed_${spd}")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Text(
                                            text = "${spd}x",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
private fun XRayStatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = OledTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SuperpowerToggleCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    testTag: String
) {
    val browserColors = LocalBrowserColors.current
    val borderColor by animateColorAsState(
        targetValue = if (checked) tint.copy(alpha = 0.6f) else browserColors.cardBorder,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sp_toggle_border"
    )
    Surface(
        onClick = onCheckedChange,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
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
                onCheckedChange = { onCheckedChange() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = tint
                )
            )
        }
    }
}
