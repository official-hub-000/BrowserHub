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
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
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
        fontSize = 15.sp,
        lineHeight = 22.sp
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
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)
