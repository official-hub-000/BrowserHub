package com.browserhub.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.R
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.theme.RomKit
import com.browserhub.app.theme.accentNow
import com.browserhub.app.ui.icons.GhIcons
import com.browserhub.app.ui.kit.AIcon

/**
 * The floating five-slot bar — GamersHub's signature shell, carrying this app's
 * five destinations.
 *
 * Structure and values are taken from GamersHub's RootShell.kt: R18 panel with a
 * 1 dp hairline, 3 dp inner padding, 5 dp item gap, R10 item clip, 23 dp glyphs,
 * 16 dp bottom inset above the navigation bar. A selected slot animates its
 * horizontal padding 15 dp -> 18 dp and reveals its uppercase mono label in the
 * accent colour, both over Micro (180 ms).
 *
 * The public signature is unchanged, so BrowserHubApp.kt and the navigation rail
 * keep compiling and every ViewModel action is still wired exactly as before.
 * The five slots, their test tags, the tab-count badge and the Shields on/off
 * state all survive the re-skin.
 */
@Composable
fun BottomControlBar(
    activeTab: BrowserTab,
    tabCount: Int,
    shieldsEnabled: Boolean,
    onHome: () -> Unit,
    onOpenShields: () -> Unit,
    onOpenAskAi: () -> Unit,
    onOpenTabVault: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = accentNow()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RomKit.R18)
                .background(RomKit.Panel)
                .border(1.dp, RomKit.Stroke, RomKit.R18)
                .padding(3.dp)
        ) {
            // 1. Lobby (home)
            FloatingSlot(
                icon = GhIcons.Home,
                label = "Lobby",
                selected = activeTab.isOnHub,
                onClick = onHome,
                testTag = "nav_home_button"
            )

            // 2. Shields — selected state mirrors shieldsEnabled, as before
            FloatingSlot(
                icon = GhIcons.Shield,
                label = "Shields",
                selected = shieldsEnabled,
                onClick = onOpenShields,
                testTag = "nav_shields_button"
            )

            // 3. AI — the centred action, now a slot like the rest so the bar
            // reads as one instrument panel rather than a bar plus a bubble.
            FloatingSlot(
                icon = GhIcons.AutoAwesome,
                label = "AI",
                selected = false,
                onClick = onOpenAskAi,
                testTag = "nav_ask_ai_button"
            )

            // 4. Tabs — keeps the count badge
            FloatingSlot(
                icon = GhIcons.Widgets,
                label = "Tabs",
                selected = false,
                onClick = onOpenTabVault,
                testTag = "nav_tabs_button",
                badge = if (tabCount > 0) (if (tabCount > 99) "99+" else tabCount.toString()) else null
            )

            // 5. Tools (menu)
            FloatingSlot(
                icon = GhIcons.Tune,
                label = "Tools",
                selected = false,
                onClick = onOpenMenu,
                testTag = "nav_menu_button"
            )
        }
    }
}

/**
 * One slot of the floating bar. Collapsed it is an icon; selected it widens and
 * shows its label. Colours, padding and press alpha all animate over Micro.
 */
@Composable
private fun FloatingSlot(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badge: String? = null
) {
    val accent = accentNow()
    val navBg by animateColorAsState(
        if (selected) RomKit.Panel2 else Color.Transparent,
        tween(RomKit.Micro), label = "navBg"
    )
    val navTint by animateColorAsState(
        if (selected) accent else RomKit.TextLo,
        tween(RomKit.Micro), label = "navTint"
    )
    val inter = remember { MutableInteractionSource() }
    val pressed by inter.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        if (pressed) 0.80f else 1f, tween(RomKit.Micro), label = "navAlpha"
    )
    val hPad by animateDpAsState(
        if (selected) 18.dp else 15.dp, tween(RomKit.Micro), label = "navPad"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RomKit.R10)
            .background(navBg)
            .clickable(inter, indication = null, onClick = onClick)
            .padding(horizontal = hPad, vertical = 12.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AIcon(
                icon,
                contentDescription = label,
                tint = navTint,
                modifier = Modifier.size(23.dp)
            )
            if (badge != null) {
                // Count badge stays mono, per the "numbers are monospace" rule.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RomKit.RPill)
                        .background(RomKit.Panel2)
                        .border(1.dp, RomKit.Stroke, RomKit.RPill)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        style = RomKit.LabelSm,
                        color = RomKit.TextMid
                    )
                }
            }
        }
        if (selected) {
            Spacer(Modifier.width(9.dp))
            Text(
                text = label.uppercase(),
                style = RomKit.Label.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                color = accent
            )
        }
    }
}

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.font.FontWeight
import com.browserhub.app.ui.theme.LocalBrowserColors

@Composable
fun BrowserNavigationRail(
    activeTab: BrowserTab,
    tabCount: Int,
    shieldsEnabled: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onOpenShields: () -> Unit,
    onOpenAskAi: () -> Unit,
    onOpenTabVault: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        IconButton(
            onClick = onHome,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_home_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = stringResource(R.string.cd_home),
                tint = if (activeTab.isOnHub) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onBack,
            enabled = !activeTab.isOnHub,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        IconButton(
            onClick = onForward,
            enabled = activeTab.canGoForward || (activeTab.isOnHub && activeTab.url.isNotBlank()),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_forward_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = stringResource(R.string.cd_forward)
            )
        }
        IconButton(
            onClick = onOpenShields,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_shields_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = "Shields",
                tint = if (shieldsEnabled) browserColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onOpenAskAi,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_ask_ai_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = "Ask AI",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_tabs_button"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onOpenTabVault,
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (tabCount > 99) "99+" else tabCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_menu_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.cd_more_options)
            )
        }
    }
}
