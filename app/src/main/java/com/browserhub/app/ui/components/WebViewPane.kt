package com.browserhub.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.browserhub.app.R
import com.browserhub.app.browser.PageToolsEngine
import com.browserhub.app.data.model.BrowserSettings
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.data.model.PageSuperpowerState
import com.browserhub.app.data.model.ShieldsConfig
import com.browserhub.app.extensions.ExtensionStoreDirectory
import com.browserhub.app.extensions.StoreExtensionMatch
import com.browserhub.app.privacy.ShieldBlockKind
import com.browserhub.app.privacy.ShieldsEngine
import com.browserhub.app.privacy.ShortlinkBypassEngine
import com.browserhub.app.privacy.YouTubeStreamResolver
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.viewmodel.BrowserViewModel
import com.browserhub.app.ui.viewmodel.WebCommand
import com.browserhub.app.vpn.TrackerBlocklist
import com.browserhub.app.vpn.VirtualRegion
import com.browserhub.app.vpn.VpnStateHolder
import com.browserhub.app.vpn.VpnStats
import java.io.ByteArrayInputStream
import java.util.WeakHashMap

private const val DESKTOP_USER_AGENT = ExtensionStoreDirectory.PC_CHROME_UA

private data class AppliedWebSettingsSnapshot(
    val isIncognito: Boolean,
    val isDesktopMode: Boolean,
    val javaScriptEnabled: Boolean,
    val pageTextZoom: Int,
    val blockPopups: Boolean,
    val block3pCookies: Boolean,
    val desiredUa: String?
)

private val lastSettingsCache = WeakHashMap<WebView, AppliedWebSettingsSnapshot>()

@Composable
fun WebViewPane(
    tab: BrowserTab,
    settings: BrowserSettings,
    shields: ShieldsConfig,
    readerHtml: String?,
    viewModel: BrowserViewModel,
    detectedStoreExtension: StoreExtensionMatch?,
    isCurrentStoreExtensionInstalled: Boolean,
    isOnExtensionStore: Boolean,
    isInstallingExtension: Boolean,
    vpnStats: VpnStats,
    pageSuperpowers: PageSuperpowerState = PageSuperpowerState(),
    isShortlinkDetected: Boolean = false,
    isBypassingLink: Boolean = false,
    onBypassShortlink: (String) -> Unit = {},
    onPauseAutoScroll: () -> Unit = {},
    onOpenSuperpowers: () -> Unit = {},
    onInstallStoreExtension: (StoreExtensionMatch) -> Unit,
    onOpenInstalledExtension: (StoreExtensionMatch) -> Unit,
    onRetry: () -> Unit,
    collectCommands: Boolean = true,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    var appliedProxyEndpoint by remember { mutableStateOf<String?>(null) }
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    if (customFullscreenView != null) {
        BackHandler {
            runCatching { customViewCallback?.onCustomViewHidden() }
            customFullscreenView = null
            customViewCallback = null
        }
    }

    if (collectCommands) {
        LaunchedEffect(vpnStats.isConnected, vpnStats.activeProxyEndpoint) {
            val targetEndpoint = if (vpnStats.isConnected) vpnStats.activeProxyEndpoint else null
            if (targetEndpoint != appliedProxyEndpoint &&
                WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
            ) {
                appliedProxyEndpoint = targetEndpoint
                runCatching {
                    if (!targetEndpoint.isNullOrBlank()) {
                        val proxyConfig = ProxyConfig.Builder()
                            .addProxyRule(targetEndpoint)
                            .addDirect()
                            .build()
                        ProxyController.getInstance().setProxyOverride(proxyConfig, Runnable::run) {}
                    } else {
                        ProxyController.getInstance().clearProxyOverride(Runnable::run) {}
                    }
                }
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.commands.collect { command ->
                when (command) {
                    is WebCommand.LoadUrl -> {
                        viewModel.getOrCreateWebView(command.tabId)?.let { wv ->
                            applyStoreAwareUserAgent(wv, command.url, tab.isDesktopMode, viewModel.shieldsFlow.value)
                            wv.loadUrl(command.url)
                        }
                    }
                    is WebCommand.GoBack -> {
                        viewModel.getOrCreateWebView(command.tabId)?.let { wv ->
                            if (wv.canGoBack()) wv.goBack()
                        }
                    }
                    is WebCommand.GoForward -> {
                        viewModel.getOrCreateWebView(command.tabId)?.let { wv ->
                            if (wv.canGoForward()) wv.goForward()
                        }
                    }
                    is WebCommand.Reload -> {
                        viewModel.getOrCreateWebView(command.tabId)?.reload()
                    }
                    is WebCommand.StopLoading -> {
                        viewModel.getOrCreateWebView(command.tabId)?.stopLoading()
                    }
                    is WebCommand.FindAll -> {
                        viewModel.getOrCreateWebView(command.tabId)?.findAllAsync(command.query)
                    }
                    is WebCommand.FindNext -> {
                        viewModel.getOrCreateWebView(command.tabId)?.findNext(command.forward)
                    }
                    is WebCommand.ClearFind -> {
                        viewModel.getOrCreateWebView(command.tabId)?.clearMatches()
                    }
                    is WebCommand.ClearCache -> {
                        viewModel.getOrCreateWebView(command.tabId)?.clearCache(true)
                    }
                    is WebCommand.DestroyTab -> Unit
                    is WebCommand.NotifyStoreExtensionInstalled -> {
                        viewModel.getOrCreateWebView(command.tabId)?.let { wv ->
                            wv.evaluateJavascript(
                                ExtensionStoreDirectory.buildMarkInstalledJs(
                                    extensionId = command.extensionId,
                                    extensionName = command.extensionName,
                                    installedIds = command.installedIds
                                ),
                                null
                            )
                        }
                    }
                    is WebCommand.SyncStoreExtensionState -> {
                        viewModel.getOrCreateWebView(command.tabId)?.let { wv ->
                            wv.evaluateJavascript(
                                ExtensionStoreDirectory.buildSyncStoreStateJs(
                                    installedIds = command.installedIds,
                                    clearInstalling = command.clearInstalling
                                ),
                                null
                            )
                        }
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (tab.isIncognito) {
            Surface(
                color = browserColors.incognitoBadgeBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = browserColors.incognitoBadgeContent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.label_incognito_banner),
                        style = MaterialTheme.typography.labelSmall,
                        color = browserColors.incognitoBadgeContent
                    )
                }
            }
        }

        // Smart Store Mode + 1-Tap Extension Install Banner
        AnimatedVisibility(
            visible = detectedStoreExtension != null || isOnExtensionStore,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            if (detectedStoreExtension != null) {
                val bannerContainer = if (isCurrentStoreExtensionInstalled) {
                    browserColors.secure.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
                val bannerBorder = if (isCurrentStoreExtensionInstalled) {
                    browserColors.secure.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                }
                val bannerTitleColor = if (isCurrentStoreExtensionInstalled) {
                    browserColors.secure
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }

                Surface(
                    color = bannerContainer,
                    border = BorderStroke(1.dp, bannerBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrentStoreExtensionInstalled) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.Extension
                            },
                            contentDescription = null,
                            tint = bannerTitleColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCurrentStoreExtensionInstalled) {
                                    stringResource(R.string.label_store_banner_installed)
                                } else {
                                    "${stringResource(R.string.label_store_banner_prefix)} ${detectedStoreExtension.sourceBrowser.label}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = bannerTitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${detectedStoreExtension.sourceBrowser.label} • ${detectedStoreExtension.titleHint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isCurrentStoreExtensionInstalled) {
                            FilledTonalButton(
                                onClick = { onOpenInstalledExtension(detectedStoreExtension) },
                                modifier = Modifier.testTag("store_banner_manage_button")
                            ) {
                                Text(stringResource(R.string.action_manage_extension))
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onInstallStoreExtension(detectedStoreExtension) },
                                enabled = !isInstallingExtension,
                                modifier = Modifier.testTag("store_banner_install_button")
                            ) {
                                if (isInstallingExtension) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install")
                                }
                            }
                        }
                    }
                }
            } else if (isOnExtensionStore) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DesktopWindows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PC Store Mode active — open any extension and tap Add / Get to install directly",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 1-Click Shortlink / Ad-Wrapper Bypass Banner
        AnimatedVisibility(
            visible = isShortlinkDetected && !tab.isOnHub,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = browserColors.amber.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, browserColors.amber.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shortlink_bypass_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = browserColors.amber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shortlink / Ad-Wrapper Detected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Skip countdowns, popups & ad walls in 1 click",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onBypassShortlink(tab.url) },
                        enabled = !isBypassingLink,
                        modifier = Modifier.testTag("shortlink_bypass_button")
                    ) {
                        if (isBypassingLink) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Bypass")
                        }
                    }
                }
            }
        }

        val isYouTubeHost = remember(tab.url, tab.isOnHub) {
            !tab.isOnHub && ShieldsEngine.isYouTubeHost(tab.url)
        }
        val youtubeVideoId = remember(tab.url, isYouTubeHost) {
            if (isYouTubeHost) YouTubeStreamResolver.extractVideoId(tab.url) else null
        }
        AnimatedVisibility(
            visible = isYouTubeHost && shields.shieldsEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = browserColors.secure.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, browserColors.secure.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("youtube_adfree_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = browserColors.secure,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (youtubeVideoId != null) "Ad-Free No-Login Shield" else "No-Login YouTube Shield",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (youtubeVideoId != null) {
                        FilledTonalButton(
                            onClick = { viewModel.openNativeYouTubePlayer(tab.url) },
                            modifier = Modifier.testTag("youtube_native_play_button")
                        ) {
                            Text("Play HD", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { viewModel.handleYouTubeBotWall(tab.url, userTriggered = true) },
                            modifier = Modifier.testTag("youtube_adfree_switch_button")
                        ) {
                            Text("In-Page", maxLines = 1)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { viewModel.cleanYouTubeFeedPrompts() },
                            modifier = Modifier.testTag("youtube_clear_prompts_button")
                        ) {
                            Text("Clear Prompts", maxLines = 1)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            key(tab.id) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("webview_container"),
                    factory = { ctx ->
                        viewModel.getOrCreateWebView(tab.id)?.apply {
                            (parent as? ViewGroup)?.removeView(this)
                        } ?: createConfiguredWebView(
                            ctx = ctx,
                            tab = tab,
                            settings = settings,
                            shields = shields,
                            vpnStats = vpnStats,
                            viewModel = viewModel,
                            onShowCustomFullscreen = { view, cb ->
                                customFullscreenView = view
                                customViewCallback = cb
                            },
                            onHideCustomFullscreen = {
                                customFullscreenView = null
                                customViewCallback = null
                            }
                        ).also { created ->
                            viewModel.registerWebView(tab.id, created)
                            if (tab.url.isNotBlank()) {
                                applyStoreAwareUserAgent(created, tab.url, tab.isDesktopMode, shields)
                                created.loadUrl(tab.url)
                            }
                        }
                    },
                    update = { webView ->
                        applyRuntimeWebSettings(webView, tab, settings, shields, vpnStats)
                        val loaded = webView.url.orEmpty()
                        if (tab.url.isNotBlank() && loaded.isBlank() && tab.isLoading) {
                            applyStoreAwareUserAgent(webView, tab.url, tab.isDesktopMode, shields)
                            webView.loadUrl(tab.url)
                        }
                    }
                )
            }

            if (tab.isReaderMode && !readerHtml.isNullOrBlank()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reader_mode_webview"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            this.settings.javaScriptEnabled = false
                            loadDataWithBaseURL(tab.url, readerHtml, "text/html", "utf-8", null)
                        }
                    },
                    update = { wv ->
                        wv.loadDataWithBaseURL(tab.url, readerHtml, "text/html", "utf-8", null)
                    }
                )
            }

            if (tab.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.status_offline_or_error),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tab.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag("webview_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            val showSuperpowerHud = pageSuperpowers.autoScrollSpeed > 0 ||
                pageSuperpowers.videoSpeed != 1.0f ||
                pageSuperpowers.forceDarkWeb
            androidx.compose.animation.AnimatedVisibility(
                visible = showSuperpowerHud,
                enter = scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = scaleOut(targetScale = 0.85f) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Surface(
                    onClick = onOpenSuperpowers,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, browserColors.violet.copy(alpha = 0.6f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.testTag("superpowers_floating_hud")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Page Superpowers",
                            tint = browserColors.violet,
                            modifier = Modifier.size(16.dp)
                        )
                        val activeLabels = buildList {
                            if (pageSuperpowers.forceDarkWeb) add("Dark")
                            if (pageSuperpowers.autoScrollSpeed > 0) add("Scroll ${pageSuperpowers.autoScrollSpeed}x")
                            if (pageSuperpowers.videoSpeed != 1.0f) add("${pageSuperpowers.videoSpeed}x")
                        }.joinToString(" • ")
                        Text(
                            text = activeLabels,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (pageSuperpowers.autoScrollSpeed > 0) {
                            Surface(
                                onClick = onPauseAutoScroll,
                                shape = RoundedCornerShape(999.dp),
                                color = browserColors.amber.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, browserColors.amber.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("hud_pause_autoscroll_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Pause,
                                        contentDescription = "Pause scroll",
                                        tint = browserColors.amber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Pause",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = browserColors.amber
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (customFullscreenView != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("webview_fullscreen_video_container"),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { container ->
                        val target = customFullscreenView
                        if (target != null && target.parent !== container) {
                            (target.parent as? ViewGroup)?.removeView(target)
                            container.removeAllViews()
                            container.addView(
                                target,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    ctx: android.content.Context,
    tab: BrowserTab,
    settings: BrowserSettings,
    shields: ShieldsConfig,
    vpnStats: VpnStats,
    viewModel: BrowserViewModel,
    onShowCustomFullscreen: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    onHideCustomFullscreen: () -> Unit
): WebView {
    return WebView(ctx).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        applyRuntimeWebSettings(this, tab, settings, shields, vpnStats)
        addJavascriptInterface(viewModel.extensionJsBridge, "BrowserHubExtBridge")

        setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                viewModel.onFindResultsUpdated(activeMatchOrdinal, numberOfMatches)
            }
        }

        setDownloadListener { url, _, _, mimetype, _ ->
            if (ExtensionStoreDirectory.isExtensionPackageUrl(url)) {
                viewModel.installExtensionFromUrl(url)
                return@setDownloadListener
            }
            viewModel.startAcceleratedDownload(url, mimeTypeHint = mimetype)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString().orEmpty()
                if (reqUrl.isNotBlank()) {
                    viewModel.downloadAndMediaEngine.recordSniffedMediaUrl(reqUrl, tab.url)
                }

                val registeredUa = YouTubeStreamResolver.lookupStreamUserAgent(reqUrl)
                if (!registeredUa.isNullOrBlank()) {
                    proxyYouTubeMediaWithUa(reqUrl, registeredUa, request?.requestHeaders)?.let { return it }
                }

                val currentShields = viewModel.shieldsFlow.value
                val currentVpn = VpnStateHolder.stats.value

                if (request?.isForMainFrame == false && currentShields.shieldsEnabled) {
                    val decision = ShieldsEngine.evaluateRequest(
                        url = reqUrl,
                        nativeAdBlocker = currentShields.nativeAdBlocker,
                        analyticsTrackerShield = currentShields.analyticsTrackerShield,
                        popupNotificationBlocker = currentShields.popupNotificationBlocker
                    )
                    if (decision.kind != ShieldBlockKind.NONE) {
                        viewModel.onShieldBlocked(tab.id, decision.kind, decision.estimatedKbSaved)
                        if (currentVpn.isConnected) {
                            VpnStateHolder.incrementWebTrackerBlocked()
                        }
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                if (currentVpn.isConnected &&
                    currentVpn.blockWebTrackers &&
                    request?.isForMainFrame == false &&
                    TrackerBlocklist.isBlockedUrl(reqUrl)
                ) {
                    VpnStateHolder.incrementWebTrackerBlocked()
                    viewModel.onShieldBlocked(tab.id, ShieldBlockKind.TRACKER, 12)
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUri = request?.url ?: return false
                val targetUrl = targetUri.toString()
                if (ExtensionStoreDirectory.isExtensionPackageUrl(targetUrl)) {
                    viewModel.installExtensionFromUrl(targetUrl)
                    return true
                }
                val currentShields = viewModel.shieldsFlow.value
                if (currentShields.shieldsEnabled &&
                    (currentShields.nativeAdBlocker || currentShields.popupNotificationBlocker) &&
                    ShieldsEngine.isBlockedAdRedirect(targetUrl)
                ) {
                    viewModel.onShieldBlocked(tab.id, ShieldBlockKind.AD, 24)
                    return true
                }
                val scheme = targetUri.scheme.orEmpty().lowercase()
                if (scheme == "http" || scheme == "https" || scheme == "about") {
                    val sanitized = if (currentShields.shieldsEnabled && request.isForMainFrame) {
                        ShieldsEngine.sanitizeNavigationUrl(
                            rawUrl = targetUrl,
                            stripTrackingParameters = currentShields.trackingParamStripper,
                            upgradeHttps = currentShields.httpsOnlyUpgrades
                        )
                    } else targetUrl

                    if (view != null) {
                        applyStoreAwareUserAgent(view, sanitized, tab.isDesktopMode, currentShields)
                        if (sanitized != targetUrl) {
                            view.loadUrl(sanitized)
                            return true
                        }
                    }
                    return false
                }
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank()) {
                    viewModel.onWebPageStarted(tab.id, url)
                    if (view != null) {
                        val currentShields = viewModel.shieldsFlow.value
                        val isTrustedHost = ShieldsEngine.isTrustedGoogleOrCaptchaHost(url)
                        applyStoreAwareUserAgent(view, url, tab.isDesktopMode, currentShields)
                        val currentVpn = VpnStateHolder.stats.value

                        if (currentShields.shieldsEnabled && currentShields.noCaptchaShield) {
                            view.evaluateJavascript(ShieldsEngine.NO_CAPTCHA_SHIELD_JS, null)
                        }
                        if (currentShields.shieldsEnabled && ShieldsEngine.isYouTubeHost(url)) {
                            ensureYouTubeConsentCookies()
                            view.evaluateJavascript(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS, null)
                            if (currentShields.autoSkipAds) {
                                view.evaluateJavascript(ShieldsEngine.AD_SKIPPER_JS, null)
                            }
                        }
                        if (currentShields.shieldsEnabled && currentShields.popupNotificationBlocker) {
                            view.evaluateJavascript(ShieldsEngine.POPUP_NOTIFICATION_KILLER_JS, null)
                        }
                        if (currentShields.shieldsEnabled && currentShields.canvasHardwareSpoofing && !isTrustedHost) {
                            view.evaluateJavascript(ShieldsEngine.CANVAS_HARDWARE_SPOOF_JS, null)
                        }
                        if (currentVpn.isConnected && currentVpn.virtualRegion != VirtualRegion.OFF) {
                            val r = currentVpn.virtualRegion
                            view.evaluateJavascript(
                                ShieldsEngine.buildLocationMaskJs(r.latitude, r.longitude, r.timezoneId, r.tzOffsetMinutesWest),
                                null
                            )
                        }
                        if (currentVpn.isConnected && currentVpn.webRtcLeakProtection && !isTrustedHost) {
                            view.evaluateJavascript(TrackerBlocklist.WEBRTC_LEAK_GUARD_JS, null)
                        }
                        if (viewModel.uiState.value.tools.devToolsOpen || viewModel.settingsFlow.value.desktopDevToolsEnabled) {
                            view.evaluateJavascript(PageToolsEngine.DEVTOOLS_HOOK_JS, null)
                        }
                        if (ExtensionStoreDirectory.isExtensionStoreHost(url)) {
                            val installedIds = viewModel.uiState.value.installedExtensionIds
                            view.evaluateJavascript(
                                ExtensionStoreDirectory.buildStorePageInstallShimJs(installedIds),
                                null
                            )
                        }
                    }
                    val startPayload = viewModel.buildExtensionScriptForPage(
                        url = url,
                        runAtStages = setOf("document_start"),
                        isIncognito = tab.isIncognito
                    )
                    if (!startPayload.isNullOrBlank()) {
                        view?.evaluateJavascript(startPayload, null)
                    }
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                val updatedUrl = url.orEmpty()
                if (updatedUrl.isBlank() || isReload) return
                viewModel.onWebSpaUrlChanged(
                    tabId = tab.id,
                    url = updatedUrl,
                    canGoBack = view?.canGoBack() == true,
                    canGoForward = view?.canGoForward() == true
                )
                val currentShields = viewModel.shieldsFlow.value
                if (currentShields.shieldsEnabled && ShieldsEngine.isYouTubeHost(updatedUrl)) {
                    view?.evaluateJavascript(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS, null)
                    if (currentShields.autoSkipAds) {
                        view?.evaluateJavascript(ShieldsEngine.AD_SKIPPER_JS, null)
                    }
                }
                if (currentShields.shieldsEnabled && currentShields.autoBypassShortlinks &&
                    ShortlinkBypassEngine.isShortlinkOrWrapperUrl(updatedUrl)
                ) {
                    view?.evaluateJavascript(ShortlinkBypassEngine.SHORTLINK_DOM_BYPASS_JS, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val finalUrl = url.orEmpty()
                val title = view?.title.orEmpty()
                viewModel.onWebPageFinished(
                    tabId = tab.id,
                    url = finalUrl,
                    title = title,
                    canGoBack = view?.canGoBack() == true,
                    canGoForward = view?.canGoForward() == true
                )
                if (finalUrl.isNotBlank()) {
                    val currentVpn = VpnStateHolder.stats.value
                    val currentShields = viewModel.shieldsFlow.value
                    val isTrustedHost = ShieldsEngine.isTrustedGoogleOrCaptchaHost(finalUrl)

                    if (currentShields.shieldsEnabled && ShieldsEngine.isYouTubeHost(finalUrl)) {
                        view?.evaluateJavascript(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS, null)
                    }
                    if (currentShields.shieldsEnabled && currentShields.popupNotificationBlocker) {
                        view?.evaluateJavascript(ShieldsEngine.POPUP_NOTIFICATION_KILLER_JS, null)
                    }
                    if (currentShields.shieldsEnabled && currentShields.autoSkipAds) {
                        view?.evaluateJavascript(ShieldsEngine.AD_SKIPPER_JS, null)
                    }
                    if (currentShields.shieldsEnabled && currentShields.cookieWallKiller) {
                        view?.evaluateJavascript(ShieldsEngine.COOKIE_WALL_KILLER_JS, null)
                    }
                    if (currentVpn.isConnected && currentVpn.virtualRegion != VirtualRegion.OFF) {
                        val r = currentVpn.virtualRegion
                        view?.evaluateJavascript(
                            ShieldsEngine.buildLocationMaskJs(r.latitude, r.longitude, r.timezoneId, r.tzOffsetMinutesWest),
                            null
                        )
                    }
                    if (currentVpn.isConnected && currentVpn.webRtcLeakProtection && !isTrustedHost) {
                        view?.evaluateJavascript(TrackerBlocklist.WEBRTC_LEAK_GUARD_JS, null)
                    }
                    if (ExtensionStoreDirectory.isExtensionStoreHost(finalUrl)) {
                        val installedIds = viewModel.uiState.value.installedExtensionIds
                        view?.evaluateJavascript(
                            ExtensionStoreDirectory.buildStorePageInstallShimJs(installedIds),
                            null
                        )
                    }
                    val endPayload = viewModel.buildExtensionScriptForPage(
                        url = finalUrl,
                        runAtStages = setOf("document_end", "document_idle"),
                        isIncognito = tab.isIncognito
                    )
                    if (!endPayload.isNullOrBlank()) {
                        view?.evaluateJavascript(endPayload, null)
                    }
                    viewModel.onAutoBypassIfEnabled(tab.id, finalUrl)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    viewModel.onWebPageError(
                        tabId = tab.id,
                        description = error?.description?.toString()
                            ?: "Connection could not be established."
                    )
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.onWebPageProgress(
                    tabId = tab.id,
                    progress = newProgress,
                    canGoBack = view?.canGoBack() == true,
                    canGoForward = view?.canGoForward() == true
                )
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    viewModel.onWebPageTitle(tab.id, title)
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val hit = view?.hitTestResult
                val anchorUrl = if (
                    isUserGesture &&
                    (hit?.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                        hit?.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
                ) {
                    hit.extra?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                } else null

                if (anchorUrl != null && !ShieldsEngine.isBlockedAdRedirect(anchorUrl)) {
                    view?.loadUrl(anchorUrl)
                    return false
                }
                if (viewModel.settingsFlow.value.blockPopups || viewModel.shieldsFlow.value.popupNotificationBlocker) {
                    viewModel.onPopupBlocked(tab.id, anchorUrl.orEmpty())
                    return false
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val res = request?.resources ?: emptyArray()
                val blocked = res.filter {
                    it.contains("PROTECTED_MEDIA_ID", ignoreCase = true) ||
                        it.contains("MIDI", ignoreCase = true)
                }
                if (blocked.isNotEmpty() || viewModel.shieldsFlow.value.popupNotificationBlocker) {
                    request?.deny()
                } else {
                    super.onPermissionRequest(request)
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val vpn = VpnStateHolder.stats.value
                if (vpn.isConnected && vpn.virtualRegion != VirtualRegion.OFF) {
                    // Protect device physical GPS coordinates when Location Shield is active
                    callback?.invoke(origin, false, false)
                } else {
                    super.onGeolocationPermissionsShowPrompt(origin, callback)
                }
            }

            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.rgb(11, 15, 23))
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                onShowCustomFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                onHideCustomFullscreen()
            }

            override fun onJsBeforeUnload(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (viewModel.shieldsFlow.value.popupNotificationBlocker) {
                    result?.confirm()
                    return true
                }
                return super.onJsBeforeUnload(view, url, message, result)
            }
        }
    }
}

private fun proxyYouTubeMediaWithUa(
    url: String,
    userAgent: String,
    requestHeaders: Map<String, String>?
): WebResourceResponse? {
    return runCatching {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
            requestHeaders?.get("Range")?.let { setRequestProperty("Range", it) }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }
        val contentType = conn.contentType?.substringBefore(';')?.trim().orEmpty().ifBlank { "video/mp4" }
        val headers = linkedMapOf<String, String>(
            "Access-Control-Allow-Origin" to "*",
            "Accept-Ranges" to (conn.getHeaderField("Accept-Ranges") ?: "bytes")
        )
        conn.getHeaderField("Content-Length")?.let { headers["Content-Length"] = it }
        conn.getHeaderField("Content-Range")?.let { headers["Content-Range"] = it }

        val rawStream = conn.inputStream
        val disconnectOnClose = object : java.io.FilterInputStream(rawStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    conn.disconnect()
                }
            }
        }
        WebResourceResponse(
            contentType,
            null,
            code,
            if (code == 206) "Partial Content" else "OK",
            headers,
            disconnectOnClose
        )
    }.getOrNull()
}

private fun ensureYouTubeConsentCookies() {
    runCatching {
        val cm = CookieManager.getInstance()
        val existing = cm.getCookie("https://www.youtube.com").orEmpty()
        if (!existing.contains("SOCS=")) {
            cm.setCookie(
                "https://www.youtube.com",
                "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg; Path=/; Domain=.youtube.com; SameSite=Lax"
            )
            cm.setCookie(
                "https://www.youtube-nocookie.com",
                "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg; Path=/; Domain=.youtube-nocookie.com; SameSite=Lax"
            )
        }
        if (!existing.contains("PREF=")) {
            cm.setCookie(
                "https://www.youtube.com",
                "PREF=f6=400&hl=en&tz=UTC; Path=/; Domain=.youtube.com"
            )
        }
    }
}

private fun resolveDesiredUserAgent(
    context: android.content.Context,
    url: String,
    isDesktopMode: Boolean,
    shields: ShieldsConfig
): String? {
    val storeUa = ExtensionStoreDirectory.getStoreDesktopUserAgent(url)
    val sysUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
    return ShieldsEngine.resolveEffectiveUserAgent(
        url = url,
        isDesktopMode = isDesktopMode,
        noCaptchaShield = shields.noCaptchaShield,
        shieldsEnabled = shields.shieldsEnabled,
        systemDefaultUa = sysUa,
        storeDesktopUa = storeUa,
        desktopUserAgent = DESKTOP_USER_AGENT
    )
}

private fun applyStoreAwareUserAgent(
    webView: WebView,
    url: String,
    isDesktopMode: Boolean,
    shields: ShieldsConfig
) {
    val desiredUa = resolveDesiredUserAgent(webView.context, url, isDesktopMode, shields)
    if (desiredUa != null) {
        if (webView.settings.userAgentString != desiredUa) {
            webView.settings.userAgentString = desiredUa
        }
    } else if (webView.settings.userAgentString != null) {
        webView.settings.userAgentString = null
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun applyRuntimeWebSettings(
    webView: WebView,
    tab: BrowserTab,
    settings: BrowserSettings,
    shields: ShieldsConfig,
    vpnStats: VpnStats
) {
    val isTrustedHost = ShieldsEngine.isTrustedGoogleOrCaptchaHost(tab.url)
    val block3p = vpnStats.isConnected && vpnStats.blockThirdPartyCookies && !isTrustedHost
    val desiredUa = resolveDesiredUserAgent(webView.context, tab.url, tab.isDesktopMode, shields)
    val effectiveBlockPopups = settings.blockPopups || (shields.shieldsEnabled && shields.popupNotificationBlocker)
    val snapshot = AppliedWebSettingsSnapshot(
        isIncognito = tab.isIncognito,
        isDesktopMode = tab.isDesktopMode,
        javaScriptEnabled = settings.javaScriptEnabled,
        pageTextZoom = settings.pageTextZoom,
        blockPopups = effectiveBlockPopups,
        block3pCookies = block3p,
        desiredUa = desiredUa
    )
    if (lastSettingsCache[webView] == snapshot) {
        return
    }
    lastSettingsCache[webView] = snapshot

    val cm = CookieManager.getInstance()
    cm.setAcceptCookie(true)
    cm.setAcceptThirdPartyCookies(webView, !block3p)
    if (shields.shieldsEnabled && ShieldsEngine.isYouTubeHost(tab.url)) {
        ensureYouTubeConsentCookies()
    }

    webView.settings.apply {
        javaScriptEnabled = settings.javaScriptEnabled
        domStorageEnabled = true
        databaseEnabled = true
        textZoom = settings.pageTextZoom
        javaScriptCanOpenWindowsAutomatically = !effectiveBlockPopups
        setSupportMultipleWindows(effectiveBlockPopups)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = if (tab.isIncognito) {
            WebSettings.LOAD_NO_CACHE
        } else {
            WebSettings.LOAD_DEFAULT
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(this, emptySet())
            }
        }
        applyStoreAwareUserAgent(webView, tab.url, tab.isDesktopMode, shields)
    }
}
