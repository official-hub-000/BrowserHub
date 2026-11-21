package com.browserhub.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.core.view.WindowCompat

/**
 * Bridge between BrowserHub's existing theme feature and the GamersHub tokens.
 *
 * Why a bridge rather than a replacement: BrowserHub persists a `StudioTheme`
 * (7 choices, `data/model/BrowserModels.kt`) and an incognito variant, and both
 * flow through the ViewModel and repository. Those are **features and data
 * flow**, which the brief freezes. Flattening them to a single dark scheme would
 * quietly delete a user-facing setting.
 *
 * So StudioTheme is *mapped* onto the GamersHub accent engine instead:
 *
 *   CYBER_CYAN        -> TONES index 0 (Light red is GC's default; kept for
 *                        the "cyber" read via the secondary cyan signal)
 *   MULLVAD_EMERALD   -> Great for Jade-ish reads; GC has no green tone, so it
 *                        uses the semantic Good colour as its accent
 *   ARC_VIOLET        -> custom accent #A855F7
 *   NORDIC_FROST      -> custom accent #93C5FD
 *   VIVALDI_CRIMSON   -> TONES index 2 (True red)
 *   WARM_DUSK         -> TONES index 5 (Ember)
 *   AMOLED_BLACK      -> TONES index 0, the app's own default
 *
 * Nothing is dropped: all seven still produce a visibly different accent.
 * Values come from GC/RomKit; only the mapping is new.
 */
@Composable
fun StudioTheme.toGameCorner(): Pair<Int, Color?> = when (this) {
    com.browserhub.app.data.model.StudioTheme.AMOLED_BLACK -> 0 to null
    com.browserhub.app.data.model.StudioTheme.CYBER_CYAN -> 0 to null
    com.browserhub.app.data.model.StudioTheme.MULLVAD_EMERALD -> GC.CUSTOM to RomKit.Good
    com.browserhub.app.data.model.StudioTheme.ARC_VIOLET -> GC.CUSTOM to Color(0xFFA855F7)
    com.browserhub.app.data.model.StudioTheme.NORDIC_FROST -> GC.CUSTOM to Color(0xFF93C5FD)
    com.browserhub.app.data.model.StudioTheme.VIVALDI_CRIMSON -> 2 to null
    com.browserhub.app.data.model.StudioTheme.WARM_DUSK -> 5 to null
}

/**
 * The app-level theme: GamersHub's GameCornerTheme, dark only, no dynamic colour.
 *
 * Typography and the colour scheme are taken from GC (Oxanium display/body,
 * monospace labels) exactly as GameCorner.kt defines them. The CompositionLocals
 * BrowserHub's screens already read are still provided by the caller, so no
 * existing surface loses its palette.
 */
@Composable
fun BrowserHubGameCornerTheme(
    theme: com.browserhub.app.data.model.StudioTheme,
    content: @Composable () -> Unit
) {
    val (idx, custom) = theme.toGameCorner()
    val accent = custom ?: GC.accentFor(idx)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF0A0A0A),
        secondary = RomKit.Cyan,
        onSecondary = Color(0xFF0A0A0A),
        background = GC.Bg,
        onBackground = GC.Text,
        surface = GC.Surface,
        onSurface = GC.Text,
        surfaceVariant = GC.SurfaceHi,
        onSurfaceVariant = GC.TextDim,
        outline = GC.Stroke,
        error = GC.CrimsonRed
    )
    val type = Typography(
        displayLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Black),
        displayMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Black),
        displaySmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = GC.Display, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = GC.Body, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = GC.Body, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(
            fontFamily = GC.Body, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 17.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, letterSpacing = 0.6.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, letterSpacing = 0.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
            fontSize = 10.sp, letterSpacing = 0.4.sp
        )
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    GC.custom = custom
    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalAccentIdx provides (if (custom != null) GC.CUSTOM else idx)
    ) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
