package com.browserhub.app.ui.sheets

/*
 * SheetFrame — the one pop-up shell, ported verbatim from GamersHub
 * (ui/sheets/Sheets.kt). Values and structure are unchanged: scrim #99000000
 * with click-to-dismiss, bottom-anchored card, fillMaxWidth, max height 760 dp,
 * R18 clip, Panel fill, 1 dp hairline, 18 dp horizontal padding,
 * navigationBarsPadding + 18 dp bottom, top-centre 44x5 grabber.
 *
 * The vertical slide itself lives in the host (SheetHost below), matching
 * GamersHub's RootShell: AnimatedVisibility with slideInVertically { it }.
 *
 * No feature logic lives here. Every BrowserHub surface renders inside it.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.browserhub.app.theme.RomKit
import com.browserhub.app.ui.kit.Grabber

fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .clip(RomKit.R18)
                .background(RomKit.Panel)
                .border(1.dp, RomKit.Stroke, RomKit.R18)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
            content()
        }
    }
}
/**
 * The host that supplies the slide, exactly as GamersHub's RootShell does it.
 *
 * Kept separate from SheetFrame so a surface can either be wrapped here (gets
 * the motion) or rendered inline where the caller already owns an
 * AnimatedVisibility. Both paths end up in the same card.
 */
@Composable
fun SheetHost(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.fillMaxSize()
    ) {
        SheetFrame(onDismiss, content)
    }
}
