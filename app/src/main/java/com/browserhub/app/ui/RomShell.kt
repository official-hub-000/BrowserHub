package com.browserhub.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.theme.RomKit
import com.browserhub.app.theme.RomKitEase
import com.browserhub.app.theme.accentNow
import com.browserhub.app.ui.icons.GhIcons
import com.browserhub.app.ui.kit.AIcon

/**
 * The five slots of the floating bar.
 *
 * BrowserHub already had exactly five bottom-bar destinations, so this maps
 * 1:1 with nothing displaced. The labels are the app's own vocabulary — the
 * shell is GamersHub's, the content is not.
 */
enum class RomTab(val label: String, val icon: ImageVector) {
    LOBBY("Lobby", GhIcons.Home),
    SHIELDS("Shields", GhIcons.Shield),
    AI("AI", GhIcons.AutoAwesome),
    TABS("Tabs", GhIcons.Widgets),
    TOOLS("Tools", GhIcons.Tune)
}

/**
 * Floating bottom bar — the signature of the GamersHub shell.
 *
 * Values are copied from RootShell.kt: R18 panel, 1 dp hairline, 3 dp inner
 * padding, 5 dp item gap, R10 item clip, 23 dp icons, 16 dp bottom inset.
 * A selected slot expands 15 dp -> 18 dp horizontal padding and reveals its
 * label in accent; colours animate over Micro (180 ms).
 */
@Composable
fun RomBottomBar(
    selected: RomTab,
    onSelect: (RomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = accentNow()
    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
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
            RomTab.entries.forEach { t ->
                val isSelected = t == selected
                val navBg by animateColorAsState(
                    if (isSelected) RomKit.Panel2 else Color.Transparent,
                    tween(RomKit.Micro), label = "navBg"
                )
                val navTint by animateColorAsState(
                    if (isSelected) accent else RomKit.TextLo,
                    tween(RomKit.Micro), label = "navTint"
                )
                val navInter = remember { MutableInteractionSource() }
                val navPressed by navInter.collectIsPressedAsState()
                val navAlpha by animateFloatAsState(
                    if (navPressed) 0.80f else 1f, tween(RomKit.Micro), label = "navAlpha"
                )
                val hPad by animateDpAsState(
                    if (isSelected) 18.dp else 15.dp, tween(RomKit.Micro), label = "navPad"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .graphicsLayer { alpha = navAlpha }
                        .clip(RomKit.R10)
                        .background(navBg)
                        .clickable(navInter, indication = null) { onSelect(t) }
                        .padding(horizontal = hPad, vertical = 12.dp)
                ) {
                    AIcon(
                        t.icon,
                        contentDescription = t.label,
                        tint = navTint,
                        modifier = Modifier.size(23.dp)
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(9.dp))
                        Text(
                            t.label.uppercase(),
                            style = RomKit.Label.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                            color = accent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab content with the GamersHub transition: incoming slides in from 1/8 of the
 * width over Emphasized (420 ms) while fading; outgoing slides left by 1/10 over
 * Standard (300 ms) while fading. Copied from RootShell.kt unchanged except for
 * the content lambda, which is this app's.
 */
@Composable
fun RomTabContent(
    tab: RomTab,
    modifier: Modifier = Modifier,
    content: @Composable (RomTab) -> Unit
) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            (slideInHorizontally(tween(RomKit.Emphasized, easing = RomKitEase)) { it / 8 } +
                fadeIn(tween(RomKit.Emphasized, easing = RomKitEase)))
                .togetherWith(
                    slideOutHorizontally(tween(RomKit.Standard, easing = RomKitEase)) { -it / 10 } +
                        fadeOut(tween(RomKit.Standard, easing = RomKitEase))
                )
        },
        label = "tab",
        modifier = modifier.fillMaxSize()
    ) { tabTarget ->
        content(tabTarget)
    }
}
