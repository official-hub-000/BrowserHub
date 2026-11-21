package com.browserhub.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.browserhub.app.R

/*
 * Font families.
 *
 * The names are kept (SpaceGroteskFontFamily, PlusJakartaSansFontFamily,
 * JetBrainsMonoFontFamily) because screens reference them, but all three now
 * resolve to the GamersHub type system: Oxanium for display/body, monospace for
 * labels and numbers. Oxanium is a variable font (wght 200-800, verified from
 * the fvar table), so the weight slots below are real instances rather than
 * synthetic bolding.
 *
 * This mirrors GameCorner.kt's GC.Display / GC.Body split: chunky Black/Bold/
 * SemiBold for headings, Normal/Medium for running text.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun oxanium(weight: Int, w: FontWeight) = Font(
    R.font.oxanium,
    weight = w,
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
        androidx.compose.ui.text.font.FontVariation.weight(weight)
    )
)

/** Display family - GC.Display equivalent (Black/Bold/SemiBold/Medium). */
val SpaceGroteskFontFamily = FontFamily(
    oxanium(500, FontWeight.Medium),
    oxanium(600, FontWeight.SemiBold),
    oxanium(700, FontWeight.Bold),
    oxanium(800, FontWeight.Black)
)

/** Body family - GC.Body equivalent (Normal/Medium/SemiBold/Bold). */
val PlusJakartaSansFontFamily = FontFamily(
    oxanium(400, FontWeight.Normal),
    oxanium(500, FontWeight.Medium),
    oxanium(600, FontWeight.SemiBold),
    oxanium(700, FontWeight.Bold)
)

/** Mono family - used for every number, unit, status word and label. */
val JetBrainsMonoFontFamily = FontFamily.Monospace

val BrowserHubTypography = Typography(
    // GC.Display Black, 30 sp / -0.5 tracking
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    // GC.Display Bold, 20 sp / -0.2 tracking
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    // GC.Display SemiBold, 15 sp
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    // GC.MonoValue (12 sp, Medium, no tracking) - numbers, units, button text
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // GC.LabelSm (9.5 sp, +0.4 tracking) - dense secondary mono labels
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    ),
    // GC.Label: mono 10 sp, +1.0 tracking. Every section header, chip label
    // and eyebrow in the app reads through this slot. Uppercase is applied at
    // the call site (text.uppercase()), exactly as GamersHub's SectionLabel
    // does it - Compose TextStyle has no text-transform property.
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp
    )
)
