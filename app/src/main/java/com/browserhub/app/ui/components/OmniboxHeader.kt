package com.browserhub.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.R
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.data.model.UrlHelper
import com.browserhub.app.ui.theme.FlagshipEmeraldDarkBg
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.theme.OledTextSecondary

@Composable
fun OmniboxHeader(
    tab: BrowserTab,
    searchEngine: SearchEngine,
    shieldsEnabled: Boolean,
    shieldCount: Int,
    applyStatusBarPadding: Boolean,
    onSubmitQuery: (String) -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenSearchEnginePicker: () -> Unit,
    onOpenShields: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    val displayUrl = if (tab.isOnHub) "" else tab.url
    // Key only on tab.id so editing never resets mid-focus
    var textFieldValue by remember(tab.id) {
        mutableStateOf(TextFieldValue(displayUrl))
    }

    LaunchedEffect(tab.id, displayUrl, isFocused) {
        if (!isFocused) {
            if (textFieldValue.text != displayUrl) {
                textFieldValue = TextFieldValue(displayUrl)
            }
        } else {
            val len = textFieldValue.text.length
            if (len > 0 && textFieldValue.selection == TextRange.Zero) {
                textFieldValue = textFieldValue.copy(selection = TextRange(0, len))
            }
        }
    }

    val isSecure = !tab.isOnHub && UrlHelper.isSecureUrl(tab.url)
    val hostText = remember(tab.url, tab.isOnHub) {
        if (tab.isOnHub) "" else UrlHelper.extractHost(tab.url).ifBlank { tab.url }
    }
    val showCollapsedHost = !isFocused && !tab.isOnHub

    val omniboxBorderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else browserColors.cardBorder,
        label = "omniboxBorder"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Search Engine Selector Pill
                Surface(
                    onClick = onOpenSearchEnginePicker,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("omnibox_engine_picker")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(searchEngine.brandColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = searchEngine.badgeLetter,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = searchEngine.brandColor
                            )
                        }
                        Text(
                            text = searchEngine.shortName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Choose Search Engine",
                            tint = OledTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Center URL / Search Omnibox Pill (Single persistent BasicTextField prevents focus crash)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = omniboxBorderColor,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            runCatching { focusRequester.requestFocus() }
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            tab.isIncognito -> Icons.Outlined.Security
                            tab.isOnHub -> Icons.Outlined.Search
                            isSecure -> Icons.Outlined.Lock
                            else -> Icons.Outlined.Public
                        },
                        contentDescription = null,
                        tint = when {
                            tab.isIncognito -> browserColors.incognitoBadgeContent
                            isSecure -> browserColors.emerald
                            else -> OledTextSecondary
                        },
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val query = textFieldValue.text.trim()
                                    if (query.isNotEmpty()) {
                                        focusManager.clearFocus()
                                        onSubmitQuery(query)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    val nowFocused = focusState.isFocused
                                    if (nowFocused != isFocused) {
                                        isFocused = nowFocused
                                        if (nowFocused) {
                                            textFieldValue = TextFieldValue(
                                                text = displayUrl,
                                                selection = TextRange(0, displayUrl.length)
                                            )
                                        }
                                    }
                                }
                                .testTag("omnibox_input"),
                            decorationBox = { innerTextField ->
                                if (showCollapsedHost) {
                                    Text(
                                        text = hostText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(modifier = Modifier.alpha(0f)) {
                                        innerTextField()
                                    }
                                } else {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = if (tab.isIncognito) {
                                                stringResource(R.string.omnibox_incognito_placeholder)
                                            } else {
                                                "Search ${searchEngine.shortName} or type any URL..."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OledTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    if (isFocused && textFieldValue.text.isNotEmpty()) {
                        IconButton(
                            onClick = { textFieldValue = TextFieldValue("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp)
                                .testTag("omnibox_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_clear_input),
                                tint = OledTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else if (!tab.isOnHub) {
                        IconButton(
                            onClick = onReloadOrStop,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp)
                                .testTag("omnibox_reload_button")
                        ) {
                            Icon(
                                imageVector = if (tab.isLoading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                                contentDescription = stringResource(
                                    if (tab.isLoading) R.string.cd_stop_loading else R.string.cd_reload
                                ),
                                tint = OledTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Right Shield Counter Pill
                Surface(
                    onClick = onOpenShields,
                    shape = RoundedCornerShape(14.dp),
                    color = if (shieldsEnabled) FlagshipEmeraldDarkBg else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (shieldsEnabled) browserColors.emerald.copy(alpha = 0.5f) else browserColors.cardBorder
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("omnibox_shields_button")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "BrowserHub Shields",
                            tint = if (shieldsEnabled) browserColors.emerald else OledTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = shieldCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (shieldsEnabled) browserColors.emerald else OledTextSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !tab.isOnHub && tab.isLoading && tab.progress in 1..99,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { (tab.progress / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}
