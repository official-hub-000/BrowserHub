package com.browserhub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.browserhub.app.theme.RomKit

/*
 * Shape language, per the GamersHub spec (Step 4):
 *
 *   panels / cards      18 dp   RomKit.R18
 *   primary buttons     16 dp   RomKit.R16
 *   ghost buttons       12 dp   RomKit.R12
 *   pills               full   RomKit.RPill
 *   small wells         10 dp   RomKit.R10
 *
 * The Material3 Shapes slots are mapped onto those five values so any component
 * that reads MaterialTheme.shapes lands on the GC corner language. Asymmetric
 * CutCornerShape is Game-Space-only in GamersHub and is deliberately absent
 * from this phone shell.
 */
val BrowserHubShapes = Shapes(
    extraSmall = RomKit.R10,
    small = RomKit.R12,
    medium = RomKit.R16,
    large = RomKit.R18,
    extraLarge = RoundedCornerShape(22.dp)
)
