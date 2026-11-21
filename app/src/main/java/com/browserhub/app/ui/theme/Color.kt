package com.browserhub.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.browserhub.app.theme.GC
import com.browserhub.app.theme.RomKit

/*
 * Colour token layer.
 *
 * These names are referenced by ~26 UI files, so the names are kept exactly as
 * they were and only the VALUES are repointed at the GamersHub design system
 * (theme/GameCorner.kt + theme/RomKit.kt). That is how the re-skin reaches
 * every screen without editing every call site, and without any surface
 * silently keeping a BrowserHub hue.
 *
 * Mapping rule, applied throughout: BrowserHub's decorative palette
 * (OLED near-blacks, Flagship cyan/violet/emerald) is replaced by GC's warm
 * near-black surfaces and single accent. Colors that carry MEANING rather than
 * style - secure / warning / danger - keep their meaning but take GC's semantic
 * values, per the brief: semantic signals are status only, never decoration.
 */

// ---- Surfaces: GC warm near-black, no pure black, no hue drift --------------
val OledBlack = GC.Bg            // was #000000 pure black; GC forbids it
val OledElevatedBg = GC.Bg       // #05060A
val OledCardSurface = GC.Surface // #0D1017
val OledCardVariant = GC.SurfaceHi // #141926
val OledCardHigh = GC.TagBg      // #1A2030
val OledBorderSubtle = GC.Stroke // #232B3D

// ---- Text -------------------------------------------------------------------
val OledTextPrimary = GC.Text    // #F2F5FA
val OledTextSecondary = GC.TextDim // #8A94A8
val OledTextMuted = GC.TextSoft  // #B6BFD0

// ---- Accents ----------------------------------------------------------------
// GC has one accent family (the red/ember tones) plus semantic signals. The old
// multi-hue brand accents therefore collapse onto those, which is the point of
// the re-skin: one accent, not five competing ones.
val FlagshipCyan = RomKit.Cyan            // #35E1F2 - GC's sanctioned secondary
val FlagshipCyanDarkBg = GC.SurfaceHi
val FlagshipEmerald = RomKit.Good         // #38E08B - "secure" signal
val FlagshipEmeraldBright = RomKit.Good
val FlagshipEmeraldDarkBg = GC.SurfaceHi
val FlagshipViolet = GC.LightRedDeep      // decorative violet -> accent pair (cyan hi)
val FlagshipVioletDarkBg = GC.TONES[0].bed
val FlagshipCrimson = RomKit.Bad          // #FF5C6C - "danger" signal
val FlagshipCrimsonDarkBg = GC.TONES[1].bed
val FlagshipAmber = RomKit.Warn           // #FFC857 - "warning" signal
val FlagshipAmberDarkBg = GC.TONES[5].bed
val FlagshipFrost = GC.TextSoft
val FlagshipFireOrange = GC.LightRed      // #35E1F2 - the accent itself (cyan)
val FlagshipFireRed = RomKit.EmberDeep    // #D95300

// ---- Incognito ---------------------------------------------------------------
// Incognito stays a distinct mode (it is a feature, not a skin), but its values
// move into GC's warm-black family so it does not read as a foreign palette.
val IncognitoBgDark = GC.Bg
val IncognitoSurfaceDark = GC.Surface
val IncognitoSurfaceVariantDark = GC.SurfaceHi
val IncognitoPrimaryDark = GC.LightRed   // cyan, matching the default accent
val IncognitoContainerDark = GC.TONES[0].bed

// ---- Compatibility aliases (used across screens) -----------------------------
val SecureMintDark = RomKit.Good
val WarningAmberDark = RomKit.Warn
val DangerRoseDark = RomKit.Bad

/** Accent list for the studio theme picker - kept as a list, now GC tones. */
val HubAccentList = listOf(
    GC.LightRed,          // cyan - the default, listed first
    RomKit.Ember,
    RomKit.Good,
    GC.LightRedDeep,
    RomKit.Bad,
    RomKit.Warn
)
