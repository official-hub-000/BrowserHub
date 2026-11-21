package com.browserhub.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.browserhub.app.data.model.StudioTheme

@Immutable
data class BrowserSemanticColors(
    val secure: Color,
    val warning: Color,
    val cyan: Color,
    val emerald: Color,
    val violet: Color,
    val crimson: Color,
    val amber: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val hubGlowPrimary: Color,
    val hubGlowSecondary: Color,
    val incognitoBadgeBg: Color,
    val incognitoBadgeContent: Color,
    val isDark: Boolean = true
)

val LocalBrowserColors = staticCompositionLocalOf {
    BrowserSemanticColors(
        secure = FlagshipEmerald,
        warning = FlagshipAmber,
        cyan = FlagshipCyan,
        emerald = FlagshipEmerald,
        violet = FlagshipViolet,
        crimson = FlagshipCrimson,
        amber = FlagshipAmber,
        cardBg = OledCardSurface,
        cardBorder = OledBorderSubtle,
        hubGlowPrimary = FlagshipCyan,
        hubGlowSecondary = FlagshipViolet,
        incognitoBadgeBg = IncognitoContainerDark,
        incognitoBadgeContent = IncognitoPrimaryDark,
        isDark = true
    )
}

private data class ThemeSpec(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val glowPrimary: Color,
    val glowSecondary: Color
)

private fun specForTheme(theme: StudioTheme): ThemeSpec = when (theme) {
    StudioTheme.AMOLED_BLACK -> ThemeSpec(
        primary = FlagshipCyan,
        onPrimary = Color(0xFF001F29),
        primaryContainer = FlagshipCyanDarkBg,
        secondary = FlagshipEmerald,
        background = OledBlack,
        surface = OledCardSurface,
        surfaceVariant = OledCardVariant,
        surfaceHigh = OledCardHigh,
        outline = OledBorderSubtle,
        glowPrimary = FlagshipCyan,
        glowSecondary = FlagshipViolet
    )
    StudioTheme.CYBER_CYAN -> ThemeSpec(
        primary = Color(0xFF0EA5E9),
        onPrimary = Color(0xFF001C2E),
        primaryContainer = Color(0xFF0A2A3D),
        secondary = FlagshipCyan,
        background = Color(0xFF02070D),
        surface = Color(0xFF08101A),
        surfaceVariant = Color(0xFF0E1826),
        surfaceHigh = Color(0xFF162436),
        outline = Color(0xFF1E324A),
        glowPrimary = FlagshipCyan,
        glowSecondary = Color(0xFF0EA5E9)
    )
    StudioTheme.MULLVAD_EMERALD -> ThemeSpec(
        primary = FlagshipEmerald,
        onPrimary = Color(0xFF022014),
        primaryContainer = FlagshipEmeraldDarkBg,
        secondary = Color(0xFF34D399),
        background = Color(0xFF010805),
        surface = Color(0xFF06110C),
        surfaceVariant = Color(0xFF0B1C14),
        surfaceHigh = Color(0xFF12291E),
        outline = Color(0xFF1B3B2B),
        glowPrimary = FlagshipEmerald,
        glowSecondary = FlagshipCyan
    )
    StudioTheme.ARC_VIOLET -> ThemeSpec(
        primary = FlagshipViolet,
        onPrimary = Color(0xFF1B0633),
        primaryContainer = FlagshipVioletDarkBg,
        secondary = FlagshipCyan,
        background = Color(0xFF04020A),
        surface = Color(0xFF0B0816),
        surfaceVariant = Color(0xFF130E24),
        surfaceHigh = Color(0xFF1C1533),
        outline = Color(0xFF2D214D),
        glowPrimary = FlagshipViolet,
        glowSecondary = FlagshipCyan
    )
    StudioTheme.NORDIC_FROST -> ThemeSpec(
        primary = FlagshipFrost,
        onPrimary = Color(0xFF082233),
        primaryContainer = Color(0xFF15293B),
        secondary = Color(0xFF93C5FD),
        background = Color(0xFF091018),
        surface = Color(0xFF0F1824),
        surfaceVariant = Color(0xFF162232),
        surfaceHigh = Color(0xFF1F2E42),
        outline = Color(0xFF2B3F59),
        glowPrimary = FlagshipFrost,
        glowSecondary = Color(0xFF93C5FD)
    )
    StudioTheme.VIVALDI_CRIMSON -> ThemeSpec(
        primary = FlagshipCrimson,
        onPrimary = Color(0xFF330611),
        primaryContainer = FlagshipCrimsonDarkBg,
        secondary = FlagshipFireOrange,
        background = Color(0xFF080204),
        surface = Color(0xFF12070A),
        surfaceVariant = Color(0xFF1C0C11),
        surfaceHigh = Color(0xFF291219),
        outline = Color(0xFF421C27),
        glowPrimary = FlagshipCrimson,
        glowSecondary = FlagshipFireOrange
    )
    StudioTheme.WARM_DUSK -> ThemeSpec(
        primary = FlagshipAmber,
        onPrimary = Color(0xFF291802),
        primaryContainer = FlagshipAmberDarkBg,
        secondary = FlagshipFireOrange,
        background = Color(0xFF080502),
        surface = Color(0xFF120D05),
        surfaceVariant = Color(0xFF1C1408),
        surfaceHigh = Color(0xFF291E0D),
        outline = Color(0xFF3D2D14),
        glowPrimary = FlagshipAmber,
        glowSecondary = FlagshipFireOrange
    )
}

@Composable
fun BrowserHubTheme(
    studioTheme: StudioTheme = StudioTheme.AMOLED_BLACK,
    isIncognito: Boolean = false,
    content: @Composable () -> Unit
) {
    val spec = specForTheme(studioTheme)
    val baseScheme = darkColorScheme(
        primary = spec.primary,
        onPrimary = spec.onPrimary,
        primaryContainer = spec.primaryContainer,
        onPrimaryContainer = OledTextPrimary,
        secondary = spec.secondary,
        onSecondary = Color(0xFF001F29),
        secondaryContainer = spec.surfaceHigh,
        onSecondaryContainer = OledTextPrimary,
        background = spec.background,
        onBackground = OledTextPrimary,
        surface = spec.surface,
        onSurface = OledTextPrimary,
        surfaceVariant = spec.surfaceVariant,
        onSurfaceVariant = OledTextSecondary,
        surfaceContainerHigh = spec.surfaceHigh,
        outline = spec.outline,
        error = FlagshipCrimson,
        onError = Color(0xFF330611),
        errorContainer = FlagshipCrimsonDarkBg,
        onErrorContainer = Color(0xFFFDA4AF)
    )

    val colorScheme = if (isIncognito) {
        baseScheme.copy(
            primary = IncognitoPrimaryDark,
            primaryContainer = IncognitoContainerDark,
            background = IncognitoBgDark,
            surface = IncognitoSurfaceDark,
            surfaceVariant = IncognitoSurfaceVariantDark
        )
    } else {
        baseScheme
    }

    val semanticColors = BrowserSemanticColors(
        secure = FlagshipEmerald,
        warning = FlagshipAmber,
        cyan = FlagshipCyan,
        emerald = FlagshipEmerald,
        violet = FlagshipViolet,
        crimson = FlagshipCrimson,
        amber = FlagshipAmber,
        cardBg = colorScheme.surface,
        cardBorder = colorScheme.outline,
        hubGlowPrimary = if (isIncognito) IncognitoPrimaryDark else spec.glowPrimary,
        hubGlowSecondary = spec.glowSecondary,
        incognitoBadgeBg = IncognitoContainerDark,
        incognitoBadgeContent = IncognitoPrimaryDark,
        isDark = true
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

    CompositionLocalProvider(LocalBrowserColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BrowserHubTypography,
            shapes = BrowserHubShapes,
            content = content
        )
    }
}

/**
 * Semantic + incognito layer, kept after the GamersHub re-skin.
 *
 * BrowserHub's screens read `LocalBrowserColors` for semantic signals (secure /
 * warning / cyan / emerald / violet / crimson / amber) and for the incognito
 * badge colours. Those are consumed by ~11 files, so the values are preserved
 * here and injected inside GameCornerTheme rather than removed.
 *
 * Only the decorative tokens now come from GC; the semantic signals keep their
 * established meanings, which the brief freezes ("semantic signals: status
 * only, never decoration").
 */
@Composable
fun BrowserHubSemanticLayer(isIncognito: Boolean, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val semanticColors = BrowserSemanticColors(
        secure = FlagshipEmerald,
        warning = FlagshipAmber,
        cyan = FlagshipCyan,
        emerald = FlagshipEmerald,
        violet = FlagshipViolet,
        crimson = FlagshipCrimson,
        amber = FlagshipAmber,
        cardBg = scheme.surface,
        cardBorder = scheme.outline,
        hubGlowPrimary = if (isIncognito) IncognitoPrimaryDark else scheme.primary,
        hubGlowSecondary = scheme.secondary,
        incognitoBadgeBg = IncognitoContainerDark,
        incognitoBadgeContent = IncognitoPrimaryDark,
        isDark = true
    )
    CompositionLocalProvider(LocalBrowserColors provides semanticColors) {
        if (isIncognito) {
            // Incognito keeps a distinct surface tint while staying inside the
            // dark-only, no-dynamic-colour rule of the new system.
            MaterialTheme(
                colorScheme = scheme.copy(
                    primary = IncognitoPrimaryDark,
                    primaryContainer = IncognitoContainerDark,
                    background = IncognitoBgDark,
                    surface = IncognitoSurfaceDark,
                    surfaceVariant = IncognitoSurfaceVariantDark
                ),
                content = content
            )
        } else {
            content()
        }
    }
}
