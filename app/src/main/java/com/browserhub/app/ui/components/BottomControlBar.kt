package com.browserhub.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.R
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.ui.theme.LocalBrowserColors

@Composable
fun BottomControlBar(
    activeTab: BrowserTab,
    tabCount: Int,
    shieldsEnabled: Boolean,
    onHome: () -> Unit,
    onOpenShields: () -> Unit,
    onOpenAskAi: () -> Unit,
    onOpenTabVault: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = browserColors.cardBorder.copy(alpha = 0.7f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Home
                BottomBarItem(
                    label = "Home",
                    selected = activeTab.isOnHub,
                    onClick = onHome,
                    testTag = "nav_home_button"
                ) { tint ->
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = stringResource(R.string.cd_home),
                        tint = tint,
                        modifier = Modifier.size(21.dp)
                    )
                }

                // 2. Shields
                BottomBarItem(
                    label = "Shields",
                    selected = shieldsEnabled,
                    activeTint = browserColors.emerald,
                    onClick = onOpenShields,
                    testTag = "nav_shields_button"
                ) { tint ->
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "Shields",
                        tint = tint,
                        modifier = Modifier.size(21.dp)
                    )
                }

                // 3. Highlighted Centered Ask AI Pill
                Surface(
                    onClick = onOpenAskAi,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 74.dp, minHeight = 48.dp)
                        .testTag("nav_ask_ai_button")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Ask AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ask AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 4. Tabs
                BottomBarItem(
                    label = "Tabs",
                    selected = false,
                    onClick = onOpenTabVault,
                    testTag = "nav_tabs_button"
                ) { tint ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.5.dp, tint),
                        modifier = Modifier.size(21.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (tabCount > 99) "99+" else tabCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = tint
                            )
                        }
                    }
                }

                // 5. Menu
                BottomBarItem(
                    label = "Menu",
                    selected = false,
                    onClick = onOpenMenu,
                    testTag = "nav_menu_button"
                ) { tint ->
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = tint,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    selected: Boolean,
    activeTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    testTag: String,
    iconContent: @Composable (androidx.compose.ui.graphics.Color) -> Unit
) {
    val tint = if (selected) activeTint else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        iconContent(tint)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = tint
        )
    }
}

@Composable
fun BrowserNavigationRail(
    activeTab: BrowserTab,
    tabCount: Int,
    shieldsEnabled: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onOpenShields: () -> Unit,
    onOpenAskAi: () -> Unit,
    onOpenTabVault: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        IconButton(
            onClick = onHome,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_home_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = stringResource(R.string.cd_home),
                tint = if (activeTab.isOnHub) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onBack,
            enabled = !activeTab.isOnHub,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        IconButton(
            onClick = onForward,
            enabled = activeTab.canGoForward || (activeTab.isOnHub && activeTab.url.isNotBlank()),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_forward_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = stringResource(R.string.cd_forward)
            )
        }
        IconButton(
            onClick = onOpenShields,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_shields_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = "Shields",
                tint = if (shieldsEnabled) browserColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onOpenAskAi,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_ask_ai_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = "Ask AI",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_tabs_button"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onOpenTabVault,
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (tabCount > 99) "99+" else tabCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rail_menu_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.cd_more_options)
            )
        }
    }
}
