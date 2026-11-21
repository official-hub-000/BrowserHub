package com.browserhub.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.theme.OledTextSecondary

@Composable
fun QuickTabStrip(
    tabs: List<BrowserTab>,
    activeTabId: String,
    activeWebViewIds: Set<String>,
    closedTabCount: Int,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onReopenClosedTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(activeTabId, tabs.size) {
        val activeIdx = tabs.indexOfFirst { it.id == activeTabId }
        if (activeIdx >= 0) {
            runCatching { listState.animateScrollToItem(activeIdx) }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(0.5.dp, browserColors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_tab_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    val isWarm = tab.id in activeWebViewIds
                    val bgColor by animateColorAsState(
                        targetValue = when {
                            isActive -> MaterialTheme.colorScheme.primaryContainer
                            tab.isIncognito -> browserColors.incognitoBadgeBg.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tab_chip_bg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isActive) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        } else {
                            browserColors.cardBorder
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tab_chip_border"
                    )

                    Surface(
                        onClick = { onSelectTab(tab.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = BorderStroke(if (isActive) 1.2.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .animateItem()
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("quick_tab_chip_${tab.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tab.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.8.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (tab.isIncognito) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = browserColors.violet,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else if (tab.isSleeping) {
                                Icon(
                                    imageVector = Icons.Outlined.Bedtime,
                                    contentDescription = null,
                                    tint = browserColors.emerald,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isWarm) browserColors.emerald
                                            else if (isActive) MaterialTheme.colorScheme.primary
                                            else OledTextSecondary.copy(alpha = 0.5f)
                                        )
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = tab.title.ifBlank { if (tab.isOnHub) "New Tab" else "Page" },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(102.dp)
                            )
                            IconButton(
                                onClick = { onCloseTab(tab.id) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("quick_tab_close_${tab.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close tab",
                                    tint = OledTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = closedTabCount > 0,
                enter = expandHorizontally() + fadeIn() + scaleIn(initialScale = 0.85f),
                exit = shrinkHorizontally() + fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Surface(
                    onClick = onReopenClosedTab,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, browserColors.amber.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .testTag("quick_tab_undo_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Reopen closed tab",
                            tint = browserColors.amber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Undo",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = browserColors.amber
                        )
                    }
                }
            }

            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .testTag("quick_tab_new_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New tab",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
