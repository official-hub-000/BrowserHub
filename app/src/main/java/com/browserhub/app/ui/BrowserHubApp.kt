package com.browserhub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browserhub.app.R
import com.browserhub.app.data.model.AddressBarPosition
import com.browserhub.app.ui.components.BottomControlBar
import com.browserhub.app.ui.components.BrowserNavigationRail
import com.browserhub.app.ui.components.FindInPageBar
import com.browserhub.app.ui.components.NativeVideoPlayerOverlay
import com.browserhub.app.ui.components.OmniboxHeader
import com.browserhub.app.ui.components.QuickTabStrip
import com.browserhub.app.ui.components.WebViewPane
import com.browserhub.app.ui.screens.AboutDiagnosticsSheet
import com.browserhub.app.ui.screens.AskAiSheet
import com.browserhub.app.ui.screens.BookmarksOverlayScreen
import com.browserhub.app.ui.screens.ControlMenuSheet
import com.browserhub.app.ui.screens.CustomizationStudioSheet
import com.browserhub.app.ui.screens.ExtensionPageDialog
import com.browserhub.app.ui.screens.ExtensionsOverlayScreen
import com.browserhub.app.ui.screens.GamesHubSheet
import com.browserhub.app.ui.screens.HistoryOverlayScreen
import com.browserhub.app.ui.screens.HubHomeScreen
import com.browserhub.app.ui.screens.PageSuperpowersSheet
import com.browserhub.app.ui.screens.PageTranslatorSheet
import com.browserhub.app.ui.screens.SearchEnginePickerSheet
import com.browserhub.app.ui.screens.ShieldsUpSheet
import com.browserhub.app.ui.screens.SpeedBenchmarkSheet
import com.browserhub.app.ui.screens.TabVaultSheet
import com.browserhub.app.ui.screens.VideoGrabberSheet
import com.browserhub.app.ui.screens.VpnOverlayScreen
import com.browserhub.app.ui.screens.WebDevToolsPane
import com.browserhub.app.ui.theme.BrowserHubTheme
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.viewmodel.BrowserOverlay
import com.browserhub.app.ui.viewmodel.BrowserViewModel
import com.browserhub.app.theme.RomKit
import com.browserhub.app.theme.RomKitEase

@Composable
fun BrowserHubApp(viewModel: BrowserViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val activeTab = uiState.activeTab
    val secondaryTab = uiState.splitSecondaryTab
    val enabledExtensionCount = remember(uiState.extensions) {
        uiState.extensions.count { it.enabled }
    }

    BackHandler(
        enabled = uiState.overlay == BrowserOverlay.None &&
            (uiState.findInPage.isActive || !activeTab.isOnHub)
    ) {
        if (uiState.findInPage.isActive) {
            viewModel.setFindInPageActive(false)
        } else {
            viewModel.goBack()
        }
    }

    // GamersHub's GameCornerTheme now owns the app-wide palette and type.
    // Incognito keeps its own variant by swapping the accent while the rest of
    // the token set stays GC's: the surface/background values are identical in
    // both, so incognito still reads as a distinct mode without leaving the
    // design system.
    BrowserHubGameCornerTheme(
        theme = uiState.settings.studioTheme
    ) {
        BrowserHubSemanticLayer(isIncognito = activeTab.isIncognito) {
        val browserColors = LocalBrowserColors.current
        val clearedHistoryMsg = context.getString(R.string.toast_history_cleared)
        val isOmniboxTop = uiState.settings.addressBarPosition == AddressBarPosition.TOP

        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 720.dp

                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, browserColors.cyan.copy(alpha = 0.45f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = data.visuals.message,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (!useNavigationRail && uiState.overlay == BrowserOverlay.None) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (!isOmniboxTop) {
                                    AnimatedVisibility(
                                        visible = uiState.tabs.size > 1 || uiState.tools.closedTabs.isNotEmpty(),
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        QuickTabStrip(
                                            tabs = uiState.tabs,
                                            activeTabId = uiState.activeTabId,
                                            activeWebViewIds = uiState.activeWebViewIds,
                                            closedTabCount = uiState.tools.closedTabs.size,
                                            onSelectTab = { viewModel.selectTab(it) },
                                            onCloseTab = { viewModel.closeTab(it) },
                                            onNewTab = { viewModel.openNewTab() },
                                            onReopenClosedTab = { viewModel.reopenLastClosedTab() }
                                        )
                                    }
                                    OmniboxHeader(
                                        tab = activeTab,
                                        searchEngine = uiState.settings.searchEngine,
                                        shieldsEnabled = uiState.shields.shieldsEnabled,
                                        shieldCount = uiState.activeShieldCount,
                                        applyStatusBarPadding = false,
                                        onSubmitQuery = { viewModel.submitOmniboxInput(it) },
                                        onReloadOrStop = { viewModel.reloadOrStop() },
                                        onOpenSearchEnginePicker = {
                                            viewModel.openOverlay(BrowserOverlay.SearchEnginePicker)
                                        },
                                        onOpenShields = {
                                            viewModel.openOverlay(BrowserOverlay.ShieldsUp)
                                        }
                                    )
                                }
                                BottomControlBar(
                                    activeTab = activeTab,
                                    tabCount = uiState.tabs.size,
                                    shieldsEnabled = uiState.shields.shieldsEnabled,
                                    onHome = { viewModel.navigateToHubHome() },
                                    onOpenShields = { viewModel.openOverlay(BrowserOverlay.ShieldsUp) },
                                    onOpenAskAi = { viewModel.openOverlay(BrowserOverlay.AskAi) },
                                    onOpenTabVault = { viewModel.openOverlay(BrowserOverlay.TabVault) },
                                    onOpenMenu = { viewModel.openOverlay(BrowserOverlay.ControlMenu) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        if (useNavigationRail && uiState.overlay == BrowserOverlay.None) {
                            BrowserNavigationRail(
                                activeTab = activeTab,
                                tabCount = uiState.tabs.size,
                                shieldsEnabled = uiState.shields.shieldsEnabled,
                                onBack = { viewModel.goBack() },
                                onForward = { viewModel.goForward() },
                                onHome = { viewModel.navigateToHubHome() },
                                onOpenShields = { viewModel.openOverlay(BrowserOverlay.ShieldsUp) },
                                onOpenAskAi = { viewModel.openOverlay(BrowserOverlay.AskAi) },
                                onOpenTabVault = { viewModel.openOverlay(BrowserOverlay.TabVault) },
                                onOpenMenu = { viewModel.openOverlay(BrowserOverlay.ControlMenu) }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .let { if (!isOmniboxTop) it.statusBarsPadding() else it }
                        ) {
                            if (isOmniboxTop) {
                                OmniboxHeader(
                                    tab = activeTab,
                                    searchEngine = uiState.settings.searchEngine,
                                    shieldsEnabled = uiState.shields.shieldsEnabled,
                                    shieldCount = uiState.activeShieldCount,
                                    applyStatusBarPadding = true,
                                    onSubmitQuery = { viewModel.submitOmniboxInput(it) },
                                    onReloadOrStop = { viewModel.reloadOrStop() },
                                    onOpenSearchEnginePicker = {
                                        viewModel.openOverlay(BrowserOverlay.SearchEnginePicker)
                                    },
                                    onOpenShields = {
                                        viewModel.openOverlay(BrowserOverlay.ShieldsUp)
                                    }
                                )
                            }

                            AnimatedVisibility(
                                visible = (isOmniboxTop || useNavigationRail) &&
                                    (uiState.tabs.size > 1 || uiState.tools.closedTabs.isNotEmpty()) &&
                                    uiState.overlay == BrowserOverlay.None,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                QuickTabStrip(
                                    tabs = uiState.tabs,
                                    activeTabId = uiState.activeTabId,
                                    activeWebViewIds = uiState.activeWebViewIds,
                                    closedTabCount = uiState.tools.closedTabs.size,
                                    onSelectTab = { viewModel.selectTab(it) },
                                    onCloseTab = { viewModel.closeTab(it) },
                                    onNewTab = { viewModel.openNewTab() },
                                    onReopenClosedTab = { viewModel.reopenLastClosedTab() }
                                )
                            }

                            FindInPageBar(
                                state = uiState.findInPage,
                                onQueryChange = { viewModel.updateFindQuery(it) },
                                onFindPrev = { viewModel.findNext(forward = false) },
                                onFindNext = { viewModel.findNext(forward = true) },
                                onClose = { viewModel.setFindInPageActive(false) }
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                AnimatedContent(
                                    targetState = Triple(activeTab.id, activeTab.isOnHub, secondaryTab?.id),
                                    transitionSpec = {
                                        // GamersHub tab motion, exactly as RootShell.kt:
                                        // in from 1/8 width over Emphasized (420 ms) while
                                        // fading; out to -1/10 over Standard (300 ms) while
                                        // fading; one easing curve (RomKitEase) for both.
                                        (slideInHorizontally(
                                            tween(RomKit.Emphasized, easing = RomKitEase)
                                        ) { it / 8 } + fadeIn(tween(RomKit.Emphasized, easing = RomKitEase)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    tween(RomKit.Standard, easing = RomKitEase)
                                                ) { -it / 10 } + fadeOut(tween(RomKit.Standard, easing = RomKitEase))
                                            )
                                    },
                                    label = "main_tab_transition"
                                ) { (_, isOnHub, _) ->
                                    if (secondaryTab != null) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (isOnHub) {
                                                    HubHomeScreen(
                                                        isIncognito = activeTab.isIncognito,
                                                        shieldsEnabled = uiState.shields.shieldsEnabled,
                                                        isVpnConnected = uiState.vpnStats.isConnected,
                                                        searchEngine = uiState.settings.searchEngine,
                                                        speedDialSites = uiState.speedDialSites,
                                                        tabCount = uiState.tabs.size,
                                                        enabledExtensionCount = enabledExtensionCount,
                                                        showAddShortcutDialog = uiState.showAddShortcutDialog,
                                                        onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                                                        onSubmitQuery = { viewModel.submitOmniboxInput(it) },
                                                        onOpenUrl = { viewModel.openUrlInActiveTab(it) },
                                                        onOpenExtensions = { viewModel.openOverlay(BrowserOverlay.Extensions) },
                                                        onOpenVpn = { viewModel.openOverlay(BrowserOverlay.Vpn) },
                                                        onOpenVideoGrabber = { viewModel.openVideoGrabberAndSniff() },
                                                        onOpenAskAi = { viewModel.openOverlay(BrowserOverlay.AskAi) },
                                                        onOpenStudio = { viewModel.openOverlay(BrowserOverlay.CustomizationStudio) },
                                                        onOpenTabVault = { viewModel.openOverlay(BrowserOverlay.TabVault) },
                                                        onOpenGames = { viewModel.openOverlay(BrowserOverlay.GamesHub) },
                                                        onOpenBenchmark = { viewModel.openOverlay(BrowserOverlay.SpeedBenchmark) },
                                                        onOpenBookmarks = { viewModel.openOverlay(BrowserOverlay.Bookmarks) },
                                                        onOpenHistory = { viewModel.openOverlay(BrowserOverlay.History) },
                                                        onShowAddShortcut = { viewModel.showAddShortcutDialog(it) },
                                                        onAddShortcut = { title, url -> viewModel.addSpeedDialShortcut(title, url) },
                                                        onDeleteShortcut = { viewModel.deleteBookmark(it) },
                                                        comfortableUi = uiState.settings.comfortableUi
                                                    )
                                                } else {
                                                    WebViewPane(
                                                        tab = activeTab,
                                                        settings = uiState.settings,
                                                        shields = uiState.shields,
                                                        readerHtml = uiState.tools.readerHtmlByTab[activeTab.id],
                                                        viewModel = viewModel,
                                                        detectedStoreExtension = uiState.detectedStoreExtension,
                                                        isCurrentStoreExtensionInstalled = uiState.isCurrentStoreExtensionInstalled,
                                                        isOnExtensionStore = uiState.isOnExtensionStore,
                                                         isInstallingExtension = uiState.isInstallingExtension,
                                                        vpnStats = uiState.vpnStats,
                                                        pageSuperpowers = uiState.tools.pageSuperpowers,
                                                        isShortlinkDetected = uiState.isCurrentUrlShortlink,
                                                        isBypassingLink = uiState.tools.isBypassingLink,
                                                        onBypassShortlink = { viewModel.bypassUrl(it) },
                                                        onPauseAutoScroll = { viewModel.setAutoScrollSpeed(0) },
                                                        onOpenSuperpowers = { viewModel.openPageSuperpowersAndInspect() },
                                                        onInstallStoreExtension = { match ->
                                                            viewModel.installFromStoreMatch(
                                                                downloadUrl = match.downloadUrl,
                                                                sourceBrowser = match.sourceBrowser,
                                                                suggestedId = match.extensionId
                                                            )
                                                        },
                                                        onOpenInstalledExtension = { match ->
                                                            viewModel.openInstalledStoreExtension(match)
                                                        },
                                                        onRetry = { viewModel.reloadOrStop() },
                                                        collectCommands = true
                                                    )
                                                }
                                            }
                                            HorizontalDivider(thickness = 2.dp, color = browserColors.cyan.copy(alpha = 0.6f))
                                            Box(modifier = Modifier.weight(1f)) {
                                                WebViewPane(
                                                    tab = secondaryTab,
                                                    settings = uiState.settings,
                                                    shields = uiState.shields,
                                                    readerHtml = uiState.tools.readerHtmlByTab[secondaryTab.id],
                                                    viewModel = viewModel,
                                                    detectedStoreExtension = null,
                                                    isCurrentStoreExtensionInstalled = false,
                                                    isOnExtensionStore = false,
                                                    isInstallingExtension = uiState.isInstallingExtension,
                                                    vpnStats = uiState.vpnStats,
                                                    onInstallStoreExtension = {},
                                                    onOpenInstalledExtension = {},
                                                    onRetry = { viewModel.reloadOrStop() },
                                                    collectCommands = false
                                                )
                                            }
                                        }
                                    } else if (isOnHub) {
                                        HubHomeScreen(
                                            isIncognito = activeTab.isIncognito,
                                            shieldsEnabled = uiState.shields.shieldsEnabled,
                                            isVpnConnected = uiState.vpnStats.isConnected,
                                            searchEngine = uiState.settings.searchEngine,
                                            speedDialSites = uiState.speedDialSites,
                                            tabCount = uiState.tabs.size,
                                            enabledExtensionCount = enabledExtensionCount,
                                            showAddShortcutDialog = uiState.showAddShortcutDialog,
                                            onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                                            onSubmitQuery = { viewModel.submitOmniboxInput(it) },
                                            onOpenUrl = { viewModel.openUrlInActiveTab(it) },
                                            onOpenExtensions = { viewModel.openOverlay(BrowserOverlay.Extensions) },
                                            onOpenVpn = { viewModel.openOverlay(BrowserOverlay.Vpn) },
                                            onOpenVideoGrabber = { viewModel.openVideoGrabberAndSniff() },
                                            onOpenAskAi = { viewModel.openOverlay(BrowserOverlay.AskAi) },
                                            onOpenStudio = { viewModel.openOverlay(BrowserOverlay.CustomizationStudio) },
                                            onOpenTabVault = { viewModel.openOverlay(BrowserOverlay.TabVault) },
                                            onOpenGames = { viewModel.openOverlay(BrowserOverlay.GamesHub) },
                                            onOpenBenchmark = { viewModel.openOverlay(BrowserOverlay.SpeedBenchmark) },
                                            onOpenBookmarks = { viewModel.openOverlay(BrowserOverlay.Bookmarks) },
                                            onOpenHistory = { viewModel.openOverlay(BrowserOverlay.History) },
                                            onShowAddShortcut = { viewModel.showAddShortcutDialog(it) },
                                            onAddShortcut = { title, url -> viewModel.addSpeedDialShortcut(title, url) },
                                            onDeleteShortcut = { viewModel.deleteBookmark(it) },
                                            comfortableUi = uiState.settings.comfortableUi
                                        )
                                    } else {
                                        WebViewPane(
                                            tab = activeTab,
                                            settings = uiState.settings,
                                            shields = uiState.shields,
                                            readerHtml = uiState.tools.readerHtmlByTab[activeTab.id],
                                            viewModel = viewModel,
                                            detectedStoreExtension = uiState.detectedStoreExtension,
                                            isCurrentStoreExtensionInstalled = uiState.isCurrentStoreExtensionInstalled,
                                            isOnExtensionStore = uiState.isOnExtensionStore,
                                             isInstallingExtension = uiState.isInstallingExtension,
                                            vpnStats = uiState.vpnStats,
                                            pageSuperpowers = uiState.tools.pageSuperpowers,
                                            isShortlinkDetected = uiState.isCurrentUrlShortlink,
                                            isBypassingLink = uiState.tools.isBypassingLink,
                                            onBypassShortlink = { viewModel.bypassUrl(it) },
                                            onPauseAutoScroll = { viewModel.setAutoScrollSpeed(0) },
                                            onOpenSuperpowers = { viewModel.openPageSuperpowersAndInspect() },
                                            onInstallStoreExtension = { match ->
                                                viewModel.installFromStoreMatch(
                                                    downloadUrl = match.downloadUrl,
                                                    sourceBrowser = match.sourceBrowser,
                                                    suggestedId = match.extensionId
                                                )
                                            },
                                            onOpenInstalledExtension = { match ->
                                                viewModel.openInstalledStoreExtension(match)
                                            },
                                            onRetry = { viewModel.reloadOrStop() },
                                            collectCommands = true
                                        )
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = uiState.tools.pageSuperpowers.nightTintEnabled,
                                    enter = fadeIn(tween(240)),
                                    exit = fadeOut(tween(200)),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x28FF8A00))
                                    )
                                }
                            }

                            if (uiState.tools.devToolsOpen && uiState.overlay == BrowserOverlay.None) {
                                WebDevToolsPane(
                                    logs = uiState.tools.devToolsLogs,
                                    onEvalExpression = { viewModel.evaluateDevToolsExpression(it) },
                                    onClearLogs = { viewModel.clearDevToolsLogs() },
                                    onClose = { viewModel.toggleDevToolsPane() }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = uiState.overlay,
                transitionSpec = {
                    val isFullScreenTarget = targetState == BrowserOverlay.Vpn ||
                        targetState == BrowserOverlay.Bookmarks ||
                        targetState == BrowserOverlay.History
                    val isFullScreenInitial = initialState == BrowserOverlay.Vpn ||
                        initialState == BrowserOverlay.Bookmarks ||
                        initialState == BrowserOverlay.History

                    when {
                        // GamersHub overlay motion: same slide/fade pair as tabs,
                        // one easing curve, no springs anywhere in the app.
                        targetState == BrowserOverlay.None -> {
                            fadeOut(tween(RomKit.Standard, easing = RomKitEase))
                                .togetherWith(
                                    slideOutVertically(tween(RomKit.Standard, easing = RomKitEase)) { it / 10 }
                                )
                        }
                        isFullScreenTarget -> {
                            (slideInHorizontally(tween(RomKit.Emphasized, easing = RomKitEase)) { it / 8 } +
                                fadeIn(tween(RomKit.Emphasized, easing = RomKitEase)))
                                .togetherWith(fadeOut(tween(RomKit.Standard, easing = RomKitEase)))
                        }
                        isFullScreenInitial -> {
                            fadeIn(tween(RomKit.Standard, easing = RomKitEase)).togetherWith(
                                slideOutHorizontally(tween(RomKit.Standard, easing = RomKitEase)) { -it / 10 }
                            )
                        }
                        else -> {
                            (slideInVertically(tween(RomKit.Emphasized, easing = RomKitEase)) { it / 8 } +
                                fadeIn(tween(RomKit.Emphasized, easing = RomKitEase)))
                                .togetherWith(slideOutVertically(tween(RomKit.Standard, easing = RomKitEase)) { -it / 10 } +
                                    fadeOut(tween(RomKit.Standard, easing = RomKitEase)))
                        }
                    }
                },
                label = "overlay_sheet_transition"
            ) { currentOverlay ->
                when (currentOverlay) {
                    BrowserOverlay.None -> Unit

                    BrowserOverlay.SearchEnginePicker -> {
                        SearchEnginePickerSheet(
                            selectedEngine = uiState.settings.searchEngine,
                            onSelectEngine = { viewModel.setSearchEngine(it) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.ShieldsUp -> {
                        ShieldsUpSheet(
                            shields = uiState.shields,
                            activeTab = activeTab,
                            isBypassingLink = uiState.tools.isBypassingLink,
                            onBypassUrl = { viewModel.bypassUrl(it) },
                            onUpdateShields = { viewModel.updateShieldsConfig(it) },
                            onLaunchZapper = { viewModel.toggleElementZapper() },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.TabVault -> {
                        TabVaultSheet(
                            tabs = uiState.tabs,
                            activeTabId = uiState.activeTabId,
                            activeWebViewIds = uiState.activeWebViewIds,
                            selectedWorkspace = uiState.tools.selectedWorkspace,
                            closedTabsCount = uiState.tools.closedTabs.size,
                            tabSearchQuery = uiState.tools.tabSearchQuery,
                            onTabSearchQueryChange = { viewModel.updateTabSearchQuery(it) },
                            onSelectWorkspace = { viewModel.selectWorkspace(it) },
                            onSelectTab = { viewModel.selectTab(it) },
                            onTogglePin = { viewModel.toggleTabPin(it) },
                            onToggleSleep = { viewModel.toggleTabSleep(it) },
                            onDuplicateTab = { viewModel.duplicateTab(it) },
                            onCloseTab = { viewModel.closeTab(it) },
                            onReopenClosedTab = { viewModel.reopenLastClosedTab() },
                            onCloseOtherTabs = { viewModel.closeOtherTabs(it) },
                            onNewTab = { viewModel.openNewTab(isIncognito = false) },
                            onNewIncognitoTab = { viewModel.openNewTab(isIncognito = true) },
                            onFireWipe = { viewModel.fireWipeData() },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.AskAi -> {
                        AskAiSheet(
                            aiState = uiState.ai,
                            activeTab = activeTab,
                            onSelectEngine = { viewModel.selectAiEngine(it) },
                            onSelectStudioMode = { viewModel.selectAiStudioMode(it) },
                            onSendPrompt = { viewModel.sendAiPrompt(it) },
                            onQuickAction = { viewModel.executeAiQuickAction(it) },
                            onClearChat = { viewModel.clearAiConversation() },
                            onSelectImageModel = { viewModel.selectAiImageModel(it) },
                            onSelectImageQuality = { viewModel.selectAiImageQuality(it) },
                            onSelectImageAspect = { viewModel.selectAiImageAspect(it) },
                            onGenerateImage = { prompt, override -> viewModel.generateAiImage(prompt, override) },
                            onSaveImage = { viewModel.saveGeneratedImageToDevice() },
                            onSelectVoice = { viewModel.selectAiVoice(it) },
                            onToggleScriptAudio = { viewModel.setAiScriptAudioMode(it) },
                            onGenerateAudio = { viewModel.generateAiAudio(it) },
                            onSaveAudio = { viewModel.saveGeneratedAudioToDevice() },
                            onGenerateVideo = { viewModel.generateAiVideoReel(it) },
                            onSaveVideo = { viewModel.saveGeneratedVideoToDevice() },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.ControlMenu -> {
                        ControlMenuSheet(
                            activeTab = activeTab,
                            tabCount = uiState.tabs.size,
                            settings = uiState.settings,
                            shields = uiState.shields,
                            isVpnConnected = uiState.vpnStats.isConnected,
                            isSplitScreen = uiState.tools.splitSecondaryTabId != null,
                            isDevToolsOpen = uiState.tools.devToolsOpen,
                            enabledExtensionCount = enabledExtensionCount,
                            pageSuperpowers = uiState.tools.pageSuperpowers,
                            onOpenPageSuperpowers = { viewModel.openPageSuperpowersAndInspect() },
                            onToggleForceDarkWeb = { viewModel.toggleForceDarkWeb() },
                            onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                            onNewTab = { viewModel.openNewTab(isIncognito = false) },
                            onNewIncognitoTab = { viewModel.openNewTab(isIncognito = true) },
                            onHome = { viewModel.navigateToHubHome() },
                            onReload = { viewModel.reloadOrStop(); viewModel.closeOverlay() },
                            onForward = { viewModel.goForward(); viewModel.closeOverlay() },
                            onToggleDesktopSite = { viewModel.toggleDesktopMode(); viewModel.closeOverlay() },
                            onToggleReaderMode = { viewModel.toggleReaderMode() },
                            onOpenTranslator = { viewModel.openOverlay(BrowserOverlay.PageTranslator) },
                            onOpenVideoGrabber = { viewModel.openVideoGrabberAndSniff() },
                            onOpenFindInPage = { viewModel.setFindInPageActive(true) },
                            onAiSummarizePage = { viewModel.openAiWithSummarize() },
                            onToggleSplitScreen = { viewModel.toggleSplitScreen() },
                            onAdjustTextZoom = { viewModel.adjustPageTextZoom(it) },
                            onOpenShields = { viewModel.openOverlay(BrowserOverlay.ShieldsUp) },
                            onOpenVpn = { viewModel.openOverlay(BrowserOverlay.Vpn) },
                            onLaunchZapper = { viewModel.toggleElementZapper() },
                            onBypassUrl = { viewModel.bypassUrl(it) },
                            onFireWipe = { viewModel.fireWipeData() },
                            onOpenExtensions = { viewModel.openOverlay(BrowserOverlay.Extensions) },
                            onToggleDevTools = { viewModel.toggleDevToolsPane() },
                            onOpenGames = { viewModel.openOverlay(BrowserOverlay.GamesHub) },
                            onOpenBenchmark = { viewModel.openOverlay(BrowserOverlay.SpeedBenchmark) },
                            onOpenAboutDiagnostics = { viewModel.openOverlay(BrowserOverlay.AboutDiagnostics) },
                            onToggleAddressBarPosition = { viewModel.toggleAddressBarPosition() },
                            onOpenStudio = { viewModel.openOverlay(BrowserOverlay.CustomizationStudio) },
                            onOpenBookmarks = { viewModel.openOverlay(BrowserOverlay.Bookmarks) },
                            onOpenHistory = { viewModel.openOverlay(BrowserOverlay.History) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.CustomizationStudio -> {
                        CustomizationStudioSheet(
                            settings = uiState.settings,
                            webViewVersion = uiState.tools.diagnosticsReport.webViewVersion,
                            downloads = uiState.downloads,
                            onSetAddressBarPosition = { viewModel.setAddressBarPosition(it) },
                            onSetComfortableUi = { viewModel.setComfortableUi(it) },
                            onSetDesktopDevTools = { viewModel.setDesktopDevToolsEnabled(it) },
                            onSelectTheme = { viewModel.setStudioTheme(it) },
                            onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                            onSelectReaderPaper = { viewModel.setReaderPaper(it) },
                            onSelectReaderFont = { viewModel.setReaderFont(it) },
                            onStartDownload = { viewModel.startAcceleratedDownload(it) },
                            onCancelDownload = { viewModel.cancelDownload(it) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.Extensions -> {
                        ExtensionsOverlayScreen(
                            extensions = uiState.extensions,
                            isInstalling = uiState.isInstallingExtension,
                            onImportLocalPackage = { viewModel.installExtensionFromLocalUri(it) },
                            onInstallFromUrl = { viewModel.installExtensionFromUrl(it) },
                            onInstallStarterTemplate = { viewModel.installBuiltInStarterExtension(it) },
                            onInstallStudioScript = { name, ver, match, runAt, js, css ->
                                viewModel.installStudioCustomScript(name, ver, match, runAt, js, css)
                            },
                            onToggleEnabled = { id, enabled -> viewModel.toggleExtensionEnabled(id, enabled) },
                            onToggleIncognito = { id, allowed -> viewModel.toggleExtensionIncognito(id, allowed) },
                            onOpenPopup = { viewModel.openExtensionPopup(it) },
                            onOpenOptions = { viewModel.openExtensionOptions(it) },
                            onUninstall = { viewModel.uninstallExtension(it) },
                            onOpenStoreUrl = { url -> viewModel.openUrlInActiveTab(url) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.PageTranslator -> {
                        PageTranslatorSheet(
                            bilingualMode = uiState.tools.bilingualTranslation,
                            isTranslating = uiState.tools.isTranslating,
                            activeLanguage = uiState.tools.activeTranslationLang,
                            onToggleBilingual = { viewModel.setBilingualTranslation(it) },
                            onSelectLanguage = { viewModel.translateActivePage(it) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.VideoGrabber -> {
                        VideoGrabberSheet(
                            detectedMedia = uiState.detectedMedia,
                            downloads = uiState.downloads,
                            isProbing = uiState.tools.isProbingMedia,
                            probeError = uiState.tools.mediaProbeError,
                            onCheckUrl = { viewModel.inspectPastedVideoUrl(it) },
                            onRescanPage = { viewModel.rescanActivePageMedia() },
                            onSaveAllPhotos = { viewModel.downloadAllPhotosOnPage() },
                            onStartDownload = { media ->
                                viewModel.startAcceleratedDownload(
                                    url = media.url,
                                    fileName = media.title,
                                    isHls = media.isHls,
                                    mimeTypeHint = media.mimeType
                                )
                            },
                            onCancelDownload = { viewModel.cancelDownload(it) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.AboutDiagnostics -> {
                        AboutDiagnosticsSheet(
                            report = uiState.tools.diagnosticsReport,
                            aiAttempts = uiState.ai.attemptLogs,
                            onRecheck = { viewModel.refreshDiagnostics() },
                            onShowToast = { viewModel.showToast(it) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.GamesHub -> {
                        GamesHubSheet(
                            activeGame = uiState.tools.activeGame,
                            onLaunchGame = { viewModel.launchGame(it) },
                            onCloseGame = { viewModel.closeGame() },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.SpeedBenchmark -> {
                        SpeedBenchmarkSheet(
                            report = uiState.tools.benchmarkReport,
                            isRunning = uiState.tools.isBenchmarkRunning,
                            onRunBenchmark = { viewModel.runSpeedBenchmark() },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.Vpn -> {
                        VpnOverlayScreen(
                            vpnStats = uiState.vpnStats,
                            onSelectProvider = { viewModel.selectVpnProvider(it) },
                            onSelectVirtualRegion = { viewModel.selectVirtualRegion(it) },
                            onConfigureProxyExit = { mode, custom ->
                                viewModel.configureProxyExit(mode, custom)
                            },
                            onRefreshNetworkCheck = { viewModel.refreshNetworkIpCheck() },
                            onUpdateToggles = { trackers, cookies, webrtc ->
                                viewModel.updateVpnPrivacyToggles(
                                    blockWebTrackers = trackers,
                                    blockThirdPartyCookies = cookies,
                                    webRtcLeakProtection = webrtc
                                )
                            },
                            onStopVpn = { viewModel.stopVpn() },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.Bookmarks -> {
                        BookmarksOverlayScreen(
                            bookmarks = uiState.bookmarks,
                            onOpenUrl = { viewModel.openUrlInActiveTab(it) },
                            onDeleteBookmark = { viewModel.deleteBookmark(it) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.History -> {
                        HistoryOverlayScreen(
                            history = uiState.history,
                            onOpenUrl = { viewModel.openUrlInActiveTab(it) },
                            onDeleteHistoryItem = { viewModel.deleteHistoryEntry(it) },
                            onClearHistory = { viewModel.clearHistory(clearedHistoryMsg) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }

                    BrowserOverlay.PageSuperpowers -> {
                        PageSuperpowersSheet(
                            superpowers = uiState.tools.pageSuperpowers,
                            xRayReport = uiState.tools.pageXRayReport,
                            isInspectingXRay = uiState.tools.isInspectingXRay,
                            onRescanXRay = { viewModel.inspectLivePageXRay() },
                            onToggleForceDarkWeb = { viewModel.toggleForceDarkWeb() },
                            onToggleNightTint = { viewModel.toggleNightTint() },
                            onToggleCopyUnlock = { viewModel.toggleCopyUnlock() },
                            onSelectAutoScrollSpeed = { viewModel.setAutoScrollSpeed(it) },
                            onSelectVideoSpeed = { viewModel.setWebVideoSpeed(it) },
                            onDismiss = { viewModel.closeOverlay() }
                        )
                    }
                }
            }

            val pageRequest = uiState.activeExtensionPage
            val dialogExtension = uiState.activeExtensionForDialog
            if (pageRequest != null && dialogExtension != null) {
                ExtensionPageDialog(
                    extension = dialogExtension,
                    isOptionsPage = pageRequest.isOptionsPage,
                    activeTabUrl = activeTab.url,
                    activeTabTitle = activeTab.title,
                    engine = viewModel.extensionEngine,
                    jsBridge = viewModel.extensionJsBridge,
                    onDismiss = { viewModel.closeExtensionPopup() }
                )
            }

            if (uiState.tools.nativeVideoPlayer.isVisible) {
                NativeVideoPlayerOverlay(
                    state = uiState.tools.nativeVideoPlayer,
                    onSwitchSource = { viewModel.switchNativeVideoSource(it) },
                    onRetryResolve = {
                        viewModel.openNativeYouTubePlayer(uiState.tools.nativeVideoPlayer.pageUrl)
                    },
                    onDownloadStream = { stream ->
                        viewModel.startAcceleratedDownload(
                            url = stream.streamUrl,
                            fileName = "${stream.title.take(48)}.mp4",
                            isHls = stream.isHls,
                            mimeTypeHint = if (stream.isHls) "application/x-mpegURL" else "video/mp4"
                        )
                    },
                    onClose = { viewModel.closeNativeVideoPlayer() }
                )
            }
        }
    }
}
}
