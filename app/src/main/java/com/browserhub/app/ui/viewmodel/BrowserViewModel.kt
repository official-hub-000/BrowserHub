package com.browserhub.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.browserhub.app.ai.AiAttemptLog
import com.browserhub.app.ai.AiChatMessage
import com.browserhub.app.ai.AiEngineOption
import com.browserhub.app.ai.AiGenerationEvent
import com.browserhub.app.ai.AiImageAspectOption
import com.browserhub.app.ai.AiImageModelOption
import com.browserhub.app.ai.AiImageQualityOption
import com.browserhub.app.ai.AiQuickNavigationAction
import com.browserhub.app.ai.AiStudioMode
import com.browserhub.app.ai.AiVoiceOption
import com.browserhub.app.ai.BrowserAiEngine
import com.browserhub.app.ai.BrowserAiMediaEngine
import com.browserhub.app.ai.BrowserLiveContextSnapshot
import com.browserhub.app.ai.GeneratedAudioResult
import com.browserhub.app.ai.GeneratedImageResult
import com.browserhub.app.ai.GeneratedVideoResult
import com.browserhub.app.browser.BenchmarkReport
import com.browserhub.app.browser.DevToolsLogEntry
import com.browserhub.app.browser.PageSuperpowersEngine
import com.browserhub.app.browser.PageToolsEngine
import com.browserhub.app.browser.TranslateLanguage
import com.browserhub.app.data.local.BookmarkEntity
import com.browserhub.app.data.local.BrowserDatabase
import com.browserhub.app.data.local.ExtensionEntity
import com.browserhub.app.data.local.HistoryEntity
import com.browserhub.app.data.model.AddressBarPosition
import com.browserhub.app.data.model.BrowserSettings
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.data.model.PageSuperpowerState
import com.browserhub.app.data.model.PageXRayReport
import com.browserhub.app.data.model.ReaderFont
import com.browserhub.app.data.model.ReaderPaper
import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.data.model.ShieldsConfig
import com.browserhub.app.data.model.StudioTheme
import com.browserhub.app.data.model.TabWorkspace
import com.browserhub.app.data.model.UrlHelper
import com.browserhub.app.data.repository.BrowserRepository
import com.browserhub.app.diagnostics.DiagnosticsEngine
import com.browserhub.app.diagnostics.DiagnosticsReport
import com.browserhub.app.downloads.AcceleratedDownloadTask
import com.browserhub.app.downloads.DetectedMediaItem
import com.browserhub.app.downloads.DownloadAndMediaEngine
import com.browserhub.app.downloads.MediaKind
import com.browserhub.app.extensions.ExtensionSourceBrowser
import com.browserhub.app.extensions.ExtensionStoreDirectory
import com.browserhub.app.extensions.StoreExtensionMatch
import com.browserhub.app.extensions.WebExtensionEngine
import com.browserhub.app.extensions.WebExtensionJsBridge
import com.browserhub.app.games.BrowserGame
import com.browserhub.app.games.GamesCatalog
import com.browserhub.app.privacy.ShieldBlockKind
import com.browserhub.app.privacy.ShieldsEngine
import com.browserhub.app.privacy.ShortlinkBypassEngine
import com.browserhub.app.privacy.YouTubeStreamResolver
import com.browserhub.app.privacy.ResolvedYouTubeStream
import com.browserhub.app.ui.components.NativeVideoPlayerState
import com.browserhub.app.vpn.BrowserHubVpnService
import com.browserhub.app.vpn.NetworkCheckResult
import com.browserhub.app.vpn.ParsedProxySpec
import com.browserhub.app.vpn.ProxyExitMode
import com.browserhub.app.vpn.VirtualRegion
import com.browserhub.app.vpn.VpnProvider
import com.browserhub.app.vpn.VpnProxyParser
import com.browserhub.app.vpn.VpnStateHolder
import com.browserhub.app.vpn.VpnStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

sealed interface BrowserOverlay {
    data object None : BrowserOverlay
    data object SearchEnginePicker : BrowserOverlay
    data object ShieldsUp : BrowserOverlay
    data object TabVault : BrowserOverlay
    data object AskAi : BrowserOverlay
    data object ControlMenu : BrowserOverlay
    data object CustomizationStudio : BrowserOverlay
    data object Extensions : BrowserOverlay
    data object PageTranslator : BrowserOverlay
    data object VideoGrabber : BrowserOverlay
    data object AboutDiagnostics : BrowserOverlay
    data object GamesHub : BrowserOverlay
    data object SpeedBenchmark : BrowserOverlay
    data object Vpn : BrowserOverlay
    data object Bookmarks : BrowserOverlay
    data object History : BrowserOverlay
    data object PageSuperpowers : BrowserOverlay
}

data class FindInPageState(
    val isActive: Boolean = false,
    val query: String = "",
    val activeMatchOrdinal: Int = 0,
    val numberOfMatches: Int = 0
)

sealed interface WebCommand {
    data class LoadUrl(val tabId: String, val url: String) : WebCommand
    data class GoBack(val tabId: String) : WebCommand
    data class GoForward(val tabId: String) : WebCommand
    data class Reload(val tabId: String) : WebCommand
    data class StopLoading(val tabId: String) : WebCommand
    data class FindAll(val tabId: String, val query: String) : WebCommand
    data class FindNext(val tabId: String, val forward: Boolean) : WebCommand
    data class ClearFind(val tabId: String) : WebCommand
    data class ClearCache(val tabId: String) : WebCommand
    data class DestroyTab(val tabId: String) : WebCommand
    data class NotifyStoreExtensionInstalled(
        val tabId: String,
        val extensionId: String,
        val extensionName: String,
        val installedIds: Set<String>
    ) : WebCommand
    data class SyncStoreExtensionState(
        val tabId: String,
        val installedIds: Set<String>,
        val clearInstalling: Boolean = false
    ) : WebCommand
}

data class ExtensionPageRequest(
    val extensionId: String,
    val isOptionsPage: Boolean = false
)

data class AiUiBundle(
    val selectedEngine: AiEngineOption = AiEngineOption.AUTO,
    val studioMode: AiStudioMode = AiStudioMode.CHAT,
    val messages: List<AiChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val attemptLogs: List<AiAttemptLog> = emptyList(),
    val selectedImageModel: AiImageModelOption = AiImageModelOption.FLUX_ULTRA_FREE,
    val selectedImageQuality: AiImageQualityOption = AiImageQualityOption.ULTRA_4K,
    val selectedImageAspect: AiImageAspectOption = AiImageAspectOption.SQUARE_1_1,
    val isGeneratingImage: Boolean = false,
    val imageError: String? = null,
    val generatedImage: GeneratedImageResult? = null,
    val selectedVoice: AiVoiceOption = AiVoiceOption.KORE,
    val aiScriptAudioMode: Boolean = false,
    val isGeneratingAudio: Boolean = false,
    val audioError: String? = null,
    val generatedAudio: GeneratedAudioResult? = null,
    val isGeneratingVideo: Boolean = false,
    val videoProgressMessage: String = "",
    val videoError: String? = null,
    val generatedVideo: GeneratedVideoResult? = null
)

data class ToolsUiBundle(
    val selectedWorkspace: TabWorkspace = TabWorkspace.ALL,
    val splitSecondaryTabId: String? = null,
    val readerHtmlByTab: Map<String, String> = emptyMap(),
    val devToolsOpen: Boolean = false,
    val devToolsLogs: List<DevToolsLogEntry> = emptyList(),
    val benchmarkReport: BenchmarkReport? = null,
    val isBenchmarkRunning: Boolean = false,
    val diagnosticsReport: DiagnosticsReport = DiagnosticsReport(),
    val isTranslating: Boolean = false,
    val activeTranslationLang: TranslateLanguage? = null,
    val bilingualTranslation: Boolean = false,
    val isProbingMedia: Boolean = false,
    val mediaProbeError: String? = null,
    val isBypassingLink: Boolean = false,
    val activeGame: BrowserGame? = null,
    val closedTabs: List<BrowserTab> = emptyList(),
    val tabSearchQuery: String = "",
    val pageSuperpowers: PageSuperpowerState = PageSuperpowerState(),
    val pageXRayReport: PageXRayReport? = null,
    val isInspectingXRay: Boolean = false,
    val nativeVideoPlayer: NativeVideoPlayerState = NativeVideoPlayerState()
)

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val activeTabId: String = "",
    val activeWebViewIds: Set<String> = emptySet(),
    val overlay: BrowserOverlay = BrowserOverlay.None,
    val findInPage: FindInPageState = FindInPageState(),
    val settings: BrowserSettings = BrowserSettings(),
    val shields: ShieldsConfig = ShieldsConfig(),
    val speedDialSites: List<BookmarkEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val extensions: List<ExtensionEntity> = emptyList(),
    val isCurrentUrlBookmarked: Boolean = false,
    val showAddShortcutDialog: Boolean = false,
    val isInstallingExtension: Boolean = false,
    val activeExtensionPage: ExtensionPageRequest? = null,
    val vpnStats: VpnStats = VpnStats(),
    val ai: AiUiBundle = AiUiBundle(),
    val tools: ToolsUiBundle = ToolsUiBundle(),
    val detectedMedia: List<DetectedMediaItem> = emptyList(),
    val downloads: List<AcceleratedDownloadTask> = emptyList()
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val splitSecondaryTab: BrowserTab?
        get() = tools.splitSecondaryTabId?.let { secId -> tabs.firstOrNull { it.id == secId } }

    val activeShieldCount: Int
        get() = activeTab.adsBlocked + activeTab.trackersStopped

    val detectedStoreExtension: StoreExtensionMatch?
        get() = if (activeTab.isOnHub) null else ExtensionStoreDirectory.detectStoreExtension(activeTab.url)

    val isOnExtensionStore: Boolean
        get() = !activeTab.isOnHub && ExtensionStoreDirectory.isExtensionStoreHost(activeTab.url)

    val installedExtensionIds: Set<String>
        get() = extensions.map { it.id }.toSet()

    val isCurrentStoreExtensionInstalled: Boolean
        get() = detectedStoreExtension?.let { match ->
            ExtensionStoreDirectory.isExtensionInstalled(match, installedExtensionIds)
        } == true

    val isCurrentUrlShortlink: Boolean
        get() = !activeTab.isOnHub && ShortlinkBypassEngine.isShortlinkOrWrapperUrl(activeTab.url)

    val activeExtensionForDialog: ExtensionEntity?
        get() = activeExtensionPage?.let { req -> extensions.firstOrNull { it.id == req.extensionId } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = BrowserDatabase.getInstance(application).browserDao()
    private val repository = BrowserRepository(context = application, dao = dao)
    val extensionEngine = WebExtensionEngine(context = application, dao = dao)
    val aiEngine = BrowserAiEngine()
    val aiMediaEngine = BrowserAiMediaEngine(context = application)
    val downloadAndMediaEngine = DownloadAndMediaEngine(context = application, scope = viewModelScope)

    private val initialTab = BrowserTab(title = "New Tab")
    private val _tabs = MutableStateFlow(listOf(initialTab))
    private val _activeTabId = MutableStateFlow(initialTab.id)
    private val _activeWebViewIds = MutableStateFlow<Set<String>>(emptySet())
    private val _overlay = MutableStateFlow<BrowserOverlay>(BrowserOverlay.None)
    private val _findInPage = MutableStateFlow(FindInPageState())
    private val _showAddShortcutDialog = MutableStateFlow(false)
    private val _isInstallingExtension = MutableStateFlow(false)
    private val _activeExtensionPage = MutableStateFlow<ExtensionPageRequest?>(null)
    private val _elementZapperActive = MutableStateFlow(false)

    private val _aiState = MutableStateFlow(AiUiBundle())
    private val _toolsState = MutableStateFlow(ToolsUiBundle())
    private var lastWebProbeJson: String? = null
    private val lastReaderJsonByTab = mutableMapOf<String, String>()
    private val autoBypassedUrls = hashSetOf<String>()
    private var aiJob: Job? = null
    private var aiImageJob: Job? = null
    private var aiAudioJob: Job? = null
    private var aiVideoJob: Job? = null

    val extensionJsBridge = WebExtensionJsBridge(
        dao = dao,
        onOpenNewTab = { url ->
            viewModelScope.launch { openNewTab(isIncognito = false, initialUrl = url) }
        },
        onRequestStoreInstall = { url ->
            viewModelScope.launch { installExtensionFromUrl(url) }
        },
        onOpenOptionsPage = { extId ->
            viewModelScope.launch { openExtensionOptions(extId) }
        },
        onElementZappedCallback = { tag ->
            val currentId = _activeTabId.value
            _tabs.update { list ->
                list.map { tab ->
                    if (tab.id == currentId) {
                        tab.copy(adsBlocked = tab.adsBlocked + 1, kbStripped = tab.kbStripped + 6)
                    } else tab
                }
            }
            _toastMessages.tryEmit("Zapped <${tag.lowercase()}> element")
        },
        onDevToolsLogCallback = { level, message ->
            _toolsState.update { st ->
                st.copy(devToolsLogs = (listOf(DevToolsLogEntry(level, message)) + st.devToolsLogs).take(120))
            }
        },
        onYouTubeBotWallCallback = { pageUrl ->
            handleYouTubeBotWall(pageUrl)
        },
        onBypassDestinationCallback = { destUrl ->
            onDomBypassDestinationFound(destUrl)
        },
        onNativeVideoPlayerCallback = { pageUrl ->
            openNativeYouTubePlayer(pageUrl)
        }
    )

    private val _commands = MutableSharedFlow<WebCommand>(extraBufferCapacity = 32)
    val commands = _commands.asSharedFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toastMessages = _toastMessages.asSharedFlow()

    private val webViews = mutableMapOf<String, WebView>()

    private val currentUrlFlow = combine(_tabs, _activeTabId) { tabs, activeId ->
        val tab = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
        if (tab.isOnHub) "" else tab.url
    }

    private val isCurrentBookmarkedFlow = currentUrlFlow.flatMapLatest { url ->
        if (url.isBlank()) flowOf(false) else repository.observeIsBookmarked(url)
    }

    val settingsFlow: StateFlow<BrowserSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BrowserSettings()
    )

    val shieldsFlow: StateFlow<ShieldsConfig> = combine(
        repository.shieldsConfig,
        _elementZapperActive
    ) { cfg, zapper ->
        cfg.copy(elementZapperActive = zapper)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ShieldsConfig()
    )

    private val baseStateFlow = combine(
        _tabs,
        _activeTabId,
        _overlay,
        _findInPage,
        combine(_showAddShortcutDialog, _activeWebViewIds) { showDialog, wvIds -> showDialog to wvIds }
    ) { tabs, activeId, overlay, find, dialogAndWebViews ->
        BrowserUiState(
            tabs = tabs,
            activeTabId = activeId,
            activeWebViewIds = dialogAndWebViews.second,
            overlay = overlay,
            findInPage = find,
            showAddShortcutDialog = dialogAndWebViews.first
        )
    }

    private val extensionUiFlow = combine(
        repository.extensions,
        _isInstallingExtension,
        _activeExtensionPage
    ) { exts, installing, pageReq ->
        Triple(exts, installing, pageReq)
    }

    private val dataStateFlow = combine(
        settingsFlow,
        shieldsFlow,
        repository.speedDialSites,
        repository.savedBookmarks,
        repository.history,
        isCurrentBookmarkedFlow
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        DataBundle(
            settings = arr[0] as BrowserSettings,
            shields = arr[1] as ShieldsConfig,
            speedDial = arr[2] as List<BookmarkEntity>,
            bookmarks = arr[3] as List<BookmarkEntity>,
            history = arr[4] as List<HistoryEntity>,
            isBookmarked = arr[5] as Boolean
        )
    }

    private val mediaDownloadsFlow = combine(
        downloadAndMediaEngine.detectedMedia,
        downloadAndMediaEngine.downloads
    ) { media, dls -> media to dls }

    val uiState: StateFlow<BrowserUiState> = combine(
        baseStateFlow,
        dataStateFlow,
        extensionUiFlow,
        VpnStateHolder.stats,
        _aiState,
        _toolsState,
        mediaDownloadsFlow
    ) { arr ->
        val base = arr[0] as BrowserUiState
        val data = arr[1] as DataBundle
        @Suppress("UNCHECKED_CAST")
        val extUi = arr[2] as Triple<List<ExtensionEntity>, Boolean, ExtensionPageRequest?>
        val vpn = arr[3] as VpnStats
        val ai = arr[4] as AiUiBundle
        val tools = arr[5] as ToolsUiBundle
        @Suppress("UNCHECKED_CAST")
        val mediaPair = arr[6] as Pair<List<DetectedMediaItem>, List<AcceleratedDownloadTask>>

        base.copy(
            settings = data.settings,
            shields = data.shields,
            speedDialSites = data.speedDial,
            bookmarks = data.bookmarks,
            history = data.history,
            isCurrentUrlBookmarked = data.isBookmarked,
            extensions = extUi.first,
            isInstallingExtension = extUi.second,
            activeExtensionPage = extUi.third,
            vpnStats = vpn,
            ai = ai,
            tools = tools,
            detectedMedia = mediaPair.first,
            downloads = mediaPair.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BrowserUiState(tabs = listOf(initialTab), activeTabId = initialTab.id)
    )

    private data class DataBundle(
        val settings: BrowserSettings,
        val shields: ShieldsConfig,
        val speedDial: List<BookmarkEntity>,
        val bookmarks: List<BookmarkEntity>,
        val history: List<HistoryEntity>,
        val isBookmarked: Boolean
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultSpeedDialSeeded()
            extensionEngine.ensureDefaultExtensionsSeeded()
            refreshDiagnostics()
        }
    }

    fun getOrCreateWebView(tabId: String): WebView? = webViews[tabId]

    fun registerWebView(tabId: String, webView: WebView) {
        webViews[tabId] = webView
        _activeWebViewIds.value = webViews.keys.toSet()
    }

    private fun removeAndDestroyWebView(tabId: String) {
        webViews.remove(tabId)?.destroy()
        _activeWebViewIds.value = webViews.keys.toSet()
    }

    fun submitOmniboxInput(rawInput: String) {
        val url = UrlHelper.normalizeInputToUrl(rawInput, settingsFlow.value.searchEngine)
        if (url.isBlank()) return
        if (url.equals("browserhub://start", ignoreCase = true)) {
            navigateToHubHome()
            return
        }
        openUrlInActiveTab(url)
    }

    fun openUrlInActiveTab(rawUrl: String) {
        val cfg = shieldsFlow.value
        val sanitized = if (cfg.shieldsEnabled) {
            ShieldsEngine.sanitizeNavigationUrl(
                rawUrl = rawUrl,
                stripTrackingParameters = cfg.trackingParamStripper,
                upgradeHttps = cfg.httpsOnlyUpgrades
            )
        } else rawUrl

        val currentId = _activeTabId.value
        _overlay.value = BrowserOverlay.None
        _findInPage.value = FindInPageState()
        _toolsState.update { st ->
            st.copy(
                readerHtmlByTab = st.readerHtmlByTab - currentId,
                activeTranslationLang = null,
                pageSuperpowers = st.pageSuperpowers.copy(autoScrollSpeed = 0)
            )
        }
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == currentId) {
                    tab.copy(
                        url = sanitized,
                        isOnHub = false,
                        isLoading = true,
                        isSleeping = false,
                        isReaderMode = false,
                        progress = 10,
                        errorMessage = null,
                        adsBlocked = 0,
                        trackersStopped = 0,
                        kbStripped = 0
                    )
                } else tab
            }
        }
        _commands.tryEmit(WebCommand.LoadUrl(currentId, sanitized))
    }

    fun openNewTab(
        isIncognito: Boolean = false,
        initialUrl: String? = null,
        workspace: TabWorkspace = TabWorkspace.MAIN
    ) {
        val defaultDesktop = settingsFlow.value.defaultDesktopMode
        val sanitized = initialUrl?.takeIf { it.isNotBlank() }?.let { raw ->
            val cfg = shieldsFlow.value
            if (cfg.shieldsEnabled) {
                ShieldsEngine.sanitizeNavigationUrl(raw, cfg.trackingParamStripper, cfg.httpsOnlyUpgrades)
            } else raw
        }
        val effectiveWorkspace = if (workspace == TabWorkspace.ALL) TabWorkspace.MAIN else workspace
        val newTab = BrowserTab(
            title = if (isIncognito) "Incognito Tab" else "New Tab",
            url = sanitized.orEmpty(),
            isIncognito = isIncognito,
            isDesktopMode = defaultDesktop,
            isOnHub = sanitized.isNullOrBlank(),
            isLoading = !sanitized.isNullOrBlank(),
            workspace = effectiveWorkspace,
            estimatedRamMb = if (sanitized.isNullOrBlank()) 28 else 46
        )
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
        _overlay.value = BrowserOverlay.None
        _findInPage.value = FindInPageState()
        if (!sanitized.isNullOrBlank()) {
            _commands.tryEmit(WebCommand.LoadUrl(newTab.id, sanitized))
        }
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            val now = System.currentTimeMillis()
            _activeTabId.value = tabId
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(isSleeping = false, lastActiveAtMs = now) else it }
            }
            _overlay.value = BrowserOverlay.None
            _findInPage.value = FindInPageState()
        }
    }

    fun switchToAdjacentTab(next: Boolean) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) return
        val idx = currentTabs.indexOfFirst { it.id == _activeTabId.value }.takeIf { it >= 0 } ?: 0
        val targetIdx = if (next) (idx + 1) % currentTabs.size else (idx - 1 + currentTabs.size) % currentTabs.size
        selectTab(currentTabs[targetIdx].id)
    }

    fun duplicateTab(tabId: String) {
        val source = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val clone = source.copy(
            id = UUID.randomUUID().toString(),
            isSleeping = false,
            isLoading = !source.isOnHub && source.url.isNotBlank(),
            progress = if (source.isOnHub) 0 else 10,
            lastActiveAtMs = System.currentTimeMillis()
        )
        _tabs.update { current ->
            val idx = current.indexOfFirst { it.id == tabId }
            if (idx >= 0) {
                current.toMutableList().apply { add(idx + 1, clone) }
            } else {
                current + clone
            }
        }
        _activeTabId.value = clone.id
        _overlay.value = BrowserOverlay.None
        if (!clone.isOnHub && clone.url.isNotBlank()) {
            _commands.tryEmit(WebCommand.LoadUrl(clone.id, clone.url))
        }
        _toastMessages.tryEmit("Duplicated tab: ${clone.title}")
    }

    fun reopenLastClosedTab() {
        val closed = _toolsState.value.closedTabs.firstOrNull() ?: return
        _toolsState.update { it.copy(closedTabs = it.closedTabs.drop(1)) }
        val restored = closed.copy(
            id = UUID.randomUUID().toString(),
            isSleeping = false,
            isLoading = !closed.isOnHub && closed.url.isNotBlank(),
            progress = if (closed.isOnHub) 0 else 10,
            lastActiveAtMs = System.currentTimeMillis()
        )
        _tabs.update { it + restored }
        _activeTabId.value = restored.id
        _overlay.value = BrowserOverlay.None
        if (!restored.isOnHub && restored.url.isNotBlank()) {
            _commands.tryEmit(WebCommand.LoadUrl(restored.id, restored.url))
        }
        _toastMessages.tryEmit("Reopened: ${restored.title.ifBlank { "Tab" }}")
    }

    fun closeOtherTabs(keepTabId: String) {
        val toClose = _tabs.value.filter { it.id != keepTabId && !it.isPinned }
        if (toClose.isEmpty()) return
        toClose.forEach { tab ->
            removeAndDestroyWebView(tab.id)
            _commands.tryEmit(WebCommand.DestroyTab(tab.id))
        }
        val archived = toClose.filter { !it.isOnHub && it.url.isNotBlank() && !it.isIncognito }
        _toolsState.update { st ->
            st.copy(
                splitSecondaryTabId = if (st.splitSecondaryTabId == keepTabId) st.splitSecondaryTabId else null,
                closedTabs = (archived + st.closedTabs).take(15)
            )
        }
        val closingIds = toClose.map { it.id }.toSet()
        _tabs.update { current -> current.filterNot { it.id in closingIds } }
        if (_activeTabId.value in closingIds) {
            _activeTabId.value = keepTabId
        }
        _toastMessages.tryEmit("Closed ${toClose.size} other ${if (toClose.size == 1) "tab" else "tabs"}")
    }

    fun updateTabSearchQuery(query: String) {
        _toolsState.update { it.copy(tabSearchQuery = query) }
    }

    fun toggleTabPin(tabId: String) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) it.copy(isPinned = !it.isPinned) else it }
        }
    }

    fun toggleTabSleep(tabId: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val nextSleeping = !tab.isSleeping
        if (nextSleeping && tabId != _activeTabId.value) {
            removeAndDestroyWebView(tabId)
        }
        _tabs.update { list ->
            list.map {
                if (it.id == tabId) {
                    it.copy(
                        isSleeping = nextSleeping,
                        estimatedRamMb = if (nextSleeping) 6 else 28
                    )
                } else it
            }
        }
    }

    fun selectWorkspace(workspace: TabWorkspace) {
        _toolsState.update { it.copy(selectedWorkspace = workspace) }
    }

    fun setTabWorkspace(tabId: String, workspace: TabWorkspace) {
        if (workspace == TabWorkspace.ALL) return
        _tabs.update { list ->
            list.map { if (it.id == tabId) it.copy(workspace = workspace) else it }
        }
    }

    fun closeTab(tabId: String) {
        val closingTab = _tabs.value.firstOrNull { it.id == tabId }
        removeAndDestroyWebView(tabId)
        _commands.tryEmit(WebCommand.DestroyTab(tabId))
        _toolsState.update { st ->
            val nextClosed = if (closingTab != null && !closingTab.isOnHub && closingTab.url.isNotBlank() && !closingTab.isIncognito) {
                (listOf(closingTab) + st.closedTabs).take(15)
            } else st.closedTabs
            st.copy(
                splitSecondaryTabId = if (st.splitSecondaryTabId == tabId) null else st.splitSecondaryTabId,
                readerHtmlByTab = st.readerHtmlByTab - tabId,
                closedTabs = nextClosed
            )
        }
        _tabs.update { current ->
            val oldIdx = current.indexOfFirst { it.id == tabId }
            val remaining = current.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "New Tab", isDesktopMode = settingsFlow.value.defaultDesktopMode)
                _activeTabId.value = fresh.id
                listOf(fresh)
            } else {
                if (_activeTabId.value == tabId) {
                    val nextIdx = oldIdx.coerceIn(0, remaining.lastIndex)
                    _activeTabId.value = remaining[nextIdx].id
                }
                remaining
            }
        }
    }

    fun closeAllTabs(recordInClosedHistory: Boolean = true) {
        val currentTabs = _tabs.value
        currentTabs.forEach { tab ->
            removeAndDestroyWebView(tab.id)
            _commands.tryEmit(WebCommand.DestroyTab(tab.id))
        }
        val fresh = BrowserTab(title = "New Tab", isDesktopMode = settingsFlow.value.defaultDesktopMode)
        _tabs.value = listOf(fresh)
        _activeTabId.value = fresh.id
        _toolsState.update { st ->
            val nextClosed = if (recordInClosedHistory) {
                val archivable = currentTabs.filter { !it.isOnHub && it.url.isNotBlank() && !it.isIncognito }
                (archivable + st.closedTabs).take(15)
            } else emptyList()
            st.copy(
                splitSecondaryTabId = null,
                readerHtmlByTab = emptyMap(),
                closedTabs = nextClosed
            )
        }
        _overlay.value = BrowserOverlay.None
        _findInPage.value = FindInPageState()
    }

    fun fireWipeData() {
        viewModelScope.launch {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            repository.clearHistory()
            downloadAndMediaEngine.clearMediaList()
            _aiState.update { it.copy(messages = emptyList()) }
            _toolsState.update {
                it.copy(
                    splitSecondaryTabId = null,
                    readerHtmlByTab = emptyMap(),
                    devToolsLogs = emptyList(),
                    activeTranslationLang = null,
                    closedTabs = emptyList(),
                    pageXRayReport = null,
                    pageSuperpowers = PageSuperpowerState()
                )
            }
            _elementZapperActive.value = false
            closeAllTabs(recordInClosedHistory = false)
            _toastMessages.tryEmit("Fire Wipe complete — tabs, cookies, storage & history burned")
        }
    }

    fun navigateToHubHome() {
        val currentId = _activeTabId.value
        _findInPage.value = FindInPageState()
        _overlay.value = BrowserOverlay.None
        _elementZapperActive.value = false
        _toolsState.update { it.copy(readerHtmlByTab = it.readerHtmlByTab - currentId, activeTranslationLang = null) }
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == currentId) {
                    tab.copy(
                        isOnHub = true,
                        isLoading = false,
                        isReaderMode = false,
                        progress = 0,
                        errorMessage = null,
                        title = if (tab.isIncognito) "Incognito Tab" else "New Tab",
                        estimatedRamMb = 28
                    )
                } else tab
            }
        }
    }

    fun goBack(): Boolean {
        val active = uiState.value.activeTab
        if (active.isReaderMode) {
            toggleReaderMode()
            return true
        }
        if (active.isOnHub) return false
        if (active.canGoBack) {
            _commands.tryEmit(WebCommand.GoBack(active.id))
            return true
        }
        navigateToHubHome()
        return true
    }

    fun goForward() {
        val active = uiState.value.activeTab
        if (active.isOnHub && active.url.isNotBlank()) {
            _tabs.update { list ->
                list.map { if (it.id == active.id) it.copy(isOnHub = false) else it }
            }
            return
        }
        if (active.canGoForward) {
            _commands.tryEmit(WebCommand.GoForward(active.id))
        } else {
            _toastMessages.tryEmit("Nothing forward in tab history")
        }
    }

    fun reloadOrStop() {
        val active = uiState.value.activeTab
        if (active.isOnHub) {
            refreshDiagnostics()
            _toastMessages.tryEmit("Start Page refreshed")
            return
        }
        if (active.isLoading) {
            _commands.tryEmit(WebCommand.StopLoading(active.id))
        } else {
            _tabs.update { list ->
                list.map { if (it.id == active.id) it.copy(errorMessage = null) else it }
            }
            _commands.tryEmit(WebCommand.Reload(active.id))
        }
    }

    fun toggleDesktopMode() {
        val active = uiState.value.activeTab
        val nextDesktop = !active.isDesktopMode
        _tabs.update { list ->
            list.map { if (it.id == active.id) it.copy(isDesktopMode = nextDesktop) else it }
        }
        if (!active.isOnHub) {
            _commands.tryEmit(WebCommand.Reload(active.id))
        }
    }

    fun toggleSplitScreen() {
        val currentSec = _toolsState.value.splitSecondaryTabId
        if (currentSec != null) {
            _toolsState.update { it.copy(splitSecondaryTabId = null) }
            _toastMessages.tryEmit("Split Screen OFF")
            return
        }
        val activeId = _activeTabId.value
        val other = _tabs.value.firstOrNull { it.id != activeId }
        val targetId = if (other != null) {
            other.id
        } else {
            val secondTab = BrowserTab(
                title = "Split Tab",
                url = "https://duckduckgo.com",
                isOnHub = false,
                isLoading = true
            )
            _tabs.update { it + secondTab }
            secondTab.id
        }
        _toolsState.update { it.copy(splitSecondaryTabId = targetId) }
        _overlay.value = BrowserOverlay.None
        _toastMessages.tryEmit("Split Screen ON")
    }

    fun onShieldBlocked(tabId: String, kind: ShieldBlockKind, kbSaved: Int) {
        if (kind == ShieldBlockKind.NONE) return
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        adsBlocked = tab.adsBlocked + if (kind == ShieldBlockKind.AD) 1 else 0,
                        trackersStopped = tab.trackersStopped + if (kind == ShieldBlockKind.TRACKER) 1 else 0,
                        kbStripped = tab.kbStripped + kbSaved
                    )
                } else tab
            }
        }
    }

    fun onPopupBlocked(tabId: String, blockedUrl: String = "") {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(adsBlocked = tab.adsBlocked + 1, kbStripped = tab.kbStripped + 24)
                } else tab
            }
        }
        if (blockedUrl.isNotBlank()) {
            _toolsState.update { st ->
                st.copy(
                    devToolsLogs = (listOf(DevToolsLogEntry("SHIELD", "Blocked popup: ${blockedUrl.take(96)}")) + st.devToolsLogs).take(120)
                )
            }
        }
    }

    fun bypassCurrentPage() {
        val active = uiState.value.activeTab
        if (active.isOnHub || active.url.isBlank()) {
            _toastMessages.tryEmit("Open or paste a shortlink URL first")
            return
        }
        bypassUrl(active.url)
    }

    fun bypassUrl(rawInput: String) {
        val trimmed = UrlHelper.normalizeShortcutUrl(rawInput.trim())
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            _toastMessages.tryEmit("Enter a valid shortlink URL (http/https)")
            return
        }
        viewModelScope.launch {
            _toolsState.update { it.copy(isBypassingLink = true) }
            try {
                val result = ShortlinkBypassEngine.bypassUrl(trimmed)
                var finalTarget = result.resolvedUrl
                var method = result.method

                val active = uiState.value.activeTab
                val wv = webViews[active.id]
                if ((finalTarget == trimmed || ShortlinkBypassEngine.isShortlinkOrWrapperUrl(finalTarget)) &&
                    wv != null && !active.isOnHub
                ) {
                    val domUrl = evalJsSuspend(wv, ShortlinkBypassEngine.SHORTLINK_DOM_BYPASS_JS).orEmpty().trim()
                    if (domUrl.startsWith("http://", true) || domUrl.startsWith("https://", true)) {
                        finalTarget = ShortlinkBypassEngine.unwrapNestedTargets(domUrl)
                        method = "DOM Countdown & Ad-Wrapper Bypass"
                    }
                }

                if (finalTarget.isNotBlank() && finalTarget != trimmed) {
                    autoBypassedUrls.add(trimmed)
                    openUrlInActiveTab(finalTarget)
                    _toastMessages.tryEmit("Bypassed via $method")
                } else if (wv != null && !active.isOnHub && active.url == trimmed) {
                    _toastMessages.tryEmit("Timers accelerated on page — wait a moment or tap destination")
                } else {
                    _toastMessages.tryEmit("Direct link opened (no further server redirect exposed)")
                    openUrlInActiveTab(trimmed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastMessages.tryEmit("Bypass error: ${e.message ?: "Network unreachable"}")
            } finally {
                _toolsState.update { it.copy(isBypassingLink = false) }
            }
        }
    }

    fun onAutoBypassIfEnabled(tabId: String, url: String) {
        val shields = shieldsFlow.value
        if (!shields.shieldsEnabled || !shields.autoBypassShortlinks) return
        if (tabId != _activeTabId.value) return
        if (url in autoBypassedUrls) return
        if (!ShortlinkBypassEngine.isShortlinkOrWrapperUrl(url)) return
        autoBypassedUrls.add(url)
        webViews[tabId]?.evaluateJavascript(ShortlinkBypassEngine.SHORTLINK_DOM_BYPASS_JS, null)
        bypassUrl(url)
    }

    fun onDomBypassDestinationFound(rawDestinationUrl: String) {
        viewModelScope.launch {
            val cleaned = ShortlinkBypassEngine.unwrapNestedTargets(rawDestinationUrl.trim())
            val active = uiState.value.activeTab
            if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) {
                if (cleaned != active.url && !active.isOnHub) {
                    openUrlInActiveTab(cleaned)
                    _toastMessages.tryEmit("Bypassed countdown / locker to destination")
                }
            }
        }
    }

    fun openNativeYouTubePlayer(pageUrl: String) {
        val videoId = YouTubeStreamResolver.extractVideoId(pageUrl)
        if (videoId == null) {
            _toastMessages.tryEmit("Open a YouTube video first to play in Native HD")
            return
        }
        viewModelScope.launch {
            _toolsState.update {
                it.copy(
                    nativeVideoPlayer = NativeVideoPlayerState(
                        isVisible = true,
                        videoId = videoId,
                        title = uiState.value.activeTab.title.ifBlank { "YouTube HD" },
                        pageUrl = pageUrl,
                        isResolving = true
                    )
                )
            }
            val streams = YouTubeStreamResolver.resolveAllPlayableStreams(videoId)
            if (streams.isEmpty()) {
                _toolsState.update {
                    it.copy(
                        nativeVideoPlayer = it.nativeVideoPlayer.copy(
                            isResolving = false,
                            errorMessage = "Could not extract a non-DRM stream for this video."
                        )
                    )
                }
            } else {
                val primary = streams.first()
                downloadAndMediaEngine.recordSniffedMediaUrl(
                    url = primary.streamUrl,
                    tabUrl = pageUrl,
                    kindHint = MediaKind.VIDEO,
                    titleHint = primary.title,
                    qualityHint = if (primary.isHls) "Ad-Free HLS" else "Ad-Free MP4"
                )
                _toolsState.update {
                    it.copy(
                        nativeVideoPlayer = NativeVideoPlayerState(
                            isVisible = true,
                            videoId = videoId,
                            title = primary.title,
                            pageUrl = pageUrl,
                            streams = streams,
                            activeStreamIndex = 0,
                            isResolving = false,
                            errorMessage = null
                        )
                    )
                }
            }
        }
    }

    fun switchNativeVideoSource(index: Int) {
        _toolsState.update { st ->
            val current = st.nativeVideoPlayer
            if (current.streams.isEmpty()) st
            else st.copy(
                nativeVideoPlayer = current.copy(
                    activeStreamIndex = index.coerceIn(0, current.streams.lastIndex),
                    errorMessage = null
                )
            )
        }
    }

    fun closeNativeVideoPlayer() {
        _toolsState.update { it.copy(nativeVideoPlayer = NativeVideoPlayerState()) }
    }

    fun cleanYouTubeFeedPrompts() {
        val tabId = _activeTabId.value
        webViews[tabId]?.evaluateJavascript(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS, null)
        _toastMessages.tryEmit("YouTube sign-in & ad prompts cleared")
    }

    fun handleYouTubeBotWall(pageUrl: String, userTriggered: Boolean = false) {
        val tabId = _activeTabId.value
        val videoId = YouTubeStreamResolver.extractVideoId(pageUrl)
        if (videoId == null) {
            webViews[tabId]?.evaluateJavascript(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS, null)
            if (userTriggered) {
                _toastMessages.tryEmit("YouTube sign-in & ad prompts cleared")
            }
            return
        }
        viewModelScope.launch {
            val streams = YouTubeStreamResolver.resolveAllPlayableStreams(videoId)
            val mp4Stream = streams.firstOrNull { !it.isHls }
            val resolved = mp4Stream ?: streams.firstOrNull()
            val wv = webViews[tabId] ?: return@launch
            val noCookieUrl = YouTubeStreamResolver.buildNoCookieEmbedUrl(videoId)
            val js = YouTubeStreamResolver.buildInPageBypassPlayerJs(
                videoId = videoId,
                streamUrl = resolved?.streamUrl,
                title = resolved?.title,
                isHlsStream = resolved?.isHls == true,
                forceReplace = userTriggered || mp4Stream != null,
                noCookieUrl = noCookieUrl
            )
            wv.evaluateJavascript(js, null)
            if (resolved != null) {
                downloadAndMediaEngine.recordSniffedMediaUrl(
                    url = resolved.streamUrl,
                    tabUrl = pageUrl,
                    kindHint = MediaKind.VIDEO,
                    titleHint = resolved.title,
                    qualityHint = if (resolved.isHls) "Ad-Free HLS" else "Ad-Free MP4"
                )
                resolved.audioUrl?.let { aud ->
                    downloadAndMediaEngine.recordSniffedMediaUrl(
                        url = aud,
                        tabUrl = pageUrl,
                        kindHint = MediaKind.AUDIO,
                        titleHint = "${resolved.title} (Audio)",
                        qualityHint = "Audio Track"
                    )
                }
                if (userTriggered && resolved.isHls) {
                    _toolsState.update {
                        it.copy(
                            nativeVideoPlayer = NativeVideoPlayerState(
                                isVisible = true,
                                videoId = videoId,
                                title = resolved.title,
                                pageUrl = pageUrl,
                                streams = streams,
                                activeStreamIndex = 0,
                                isResolving = false
                            )
                        )
                    }
                }
                _toastMessages.tryEmit("YouTube Ad-Free Player active • ${resolved.sourceName}")
            } else if (userTriggered) {
                _toastMessages.tryEmit("Switched to Direct In-Page Clean Player")
            }
        }
    }

    fun toggleElementZapper() {
        val active = uiState.value.activeTab
        if (active.isOnHub) {
            _toastMessages.tryEmit("Open any website first to launch Element Zapper")
            return
        }
        val next = !_elementZapperActive.value
        _elementZapperActive.value = next
        _overlay.value = BrowserOverlay.None
        val wv = webViews[active.id]
        if (wv != null) {
            wv.evaluateJavascript(
                if (next) ShieldsEngine.ELEMENT_ZAPPER_ACTIVATE_JS else ShieldsEngine.ELEMENT_ZAPPER_DEACTIVATE_JS,
                null
            )
        }
        _toastMessages.tryEmit(
            if (next) "uBlock Element Zapper ON — tap any ad or box on page" else "Element Zapper OFF"
        )
    }

    fun toggleReaderMode() {
        val active = uiState.value.activeTab
        if (active.isOnHub) {
            _toastMessages.tryEmit("Open an article or webpage first for Reader Mode")
            return
        }
        val turnOn = !active.isReaderMode
        if (!turnOn) {
            _tabs.update { list -> list.map { if (it.id == active.id) it.copy(isReaderMode = false) else it } }
            _toolsState.update { it.copy(readerHtmlByTab = it.readerHtmlByTab - active.id) }
            return
        }
        val wv = webViews[active.id] ?: return
        viewModelScope.launch {
            val rawJson = evalJsSuspend(wv, PageToolsEngine.EXTRACT_READER_JSON_JS)
                ?: "{\"title\":\"\",\"blocks\":[]}"
            lastReaderJsonByTab[active.id] = rawJson
            val st = settingsFlow.value
            val html = PageToolsEngine.buildReaderHtml(
                rawJson = rawJson,
                sourceUrl = active.url,
                paper = st.readerPaper,
                font = st.readerFont,
                zoomPercent = st.pageTextZoom
            )
            _toolsState.update { it.copy(readerHtmlByTab = it.readerHtmlByTab + (active.id to html)) }
            _tabs.update { list -> list.map { if (it.id == active.id) it.copy(isReaderMode = true) else it } }
            _overlay.value = BrowserOverlay.None
        }
    }

    private fun rebuildActiveReaderIfNeeded(
        paper: ReaderPaper = settingsFlow.value.readerPaper,
        font: ReaderFont = settingsFlow.value.readerFont,
        zoomPercent: Int = settingsFlow.value.pageTextZoom
    ) {
        val active = uiState.value.activeTab
        if (!active.isReaderMode) return
        val rawJson = lastReaderJsonByTab[active.id] ?: return
        val html = PageToolsEngine.buildReaderHtml(
            rawJson = rawJson,
            sourceUrl = active.url,
            paper = paper,
            font = font,
            zoomPercent = zoomPercent
        )
        _toolsState.update { it.copy(readerHtmlByTab = it.readerHtmlByTab + (active.id to html)) }
    }

    fun setBilingualTranslation(enabled: Boolean) {
        _toolsState.update { it.copy(bilingualTranslation = enabled) }
    }

    fun translateActivePage(language: TranslateLanguage) {
        val active = uiState.value.activeTab
        if (active.isOnHub) {
            _toastMessages.tryEmit("Open a webpage first to translate it")
            return
        }
        val wv = webViews[active.id] ?: return
        val bilingual = _toolsState.value.bilingualTranslation
        viewModelScope.launch {
            _toolsState.update { it.copy(isTranslating = true) }
            try {
                val rawArr = evalJsSuspend(wv, PageToolsEngine.EXTRACT_TRANSLATABLE_BLOCKS_JS) ?: "[]"
                val jsonArr = JSONArray(rawArr)
                val texts = buildList {
                    for (i in 0 until jsonArr.length()) {
                        val s = jsonArr.optString(i)
                        if (s.isNotBlank()) add(s)
                    }
                }
                if (texts.isEmpty()) {
                    _toastMessages.tryEmit("No translatable text blocks found on this page")
                    return@launch
                }
                val translated = PageToolsEngine.translateStrings(texts, language, aiEngine)
                val applyJs = PageToolsEngine.buildApplyTranslationJs(translated, bilingual)
                evalJsSuspend(wv, applyJs)
                _toolsState.update { it.copy(activeTranslationLang = language) }
                _overlay.value = BrowserOverlay.None
                _toastMessages.tryEmit("Translated page into ${language.nativeName}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastMessages.tryEmit("Translation failed: ${e.message ?: "Network error"}")
            } finally {
                _toolsState.update { it.copy(isTranslating = false) }
            }
        }
    }

    fun onWebPageStarted(tabId: String, url: String) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url,
                        isLoading = true,
                        progress = 15,
                        errorMessage = null,
                        isOnHub = false,
                        isReaderMode = false
                    )
                } else tab
            }
        }
    }

    fun onWebPageProgress(tabId: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        progress = progress,
                        isLoading = progress in 1..99,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else tab
            }
        }
    }

    fun onWebPageTitle(tabId: String, title: String) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId && !tab.isOnHub) {
                    tab.copy(title = title.ifBlank { UrlHelper.extractHost(tab.url) })
                } else tab
            }
        }
    }

    fun onWebPageFinished(
        tabId: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        var tabIsIncognito = false
        var tabIsOnHub = false
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tabIsIncognito = tab.isIncognito
                    tabIsOnHub = tab.isOnHub
                    if (tab.isOnHub) {
                        tab
                    } else {
                        tab.copy(
                            url = url,
                            title = title.ifBlank { UrlHelper.extractHost(url) },
                            isLoading = false,
                            progress = 100,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            estimatedRamMb = 44
                        )
                    }
                } else tab
            }
        }
        if (!tabIsIncognito && !tabIsOnHub && url.startsWith("http")) {
            viewModelScope.launch {
                repository.recordHistory(title, url)
            }
        }
        webViews[tabId]?.let { wv ->
            if (_toolsState.value.devToolsOpen || settingsFlow.value.desktopDevToolsEnabled) {
                wv.evaluateJavascript(PageToolsEngine.DEVTOOLS_HOOK_JS, null)
            }
            applyActiveSuperpowersToWebView(wv)
        }
    }

    fun onWebSpaUrlChanged(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
        if (url.isBlank() || url.equals("about:blank", ignoreCase = true)) return
        var tabIsIncognito = false
        var changed = false
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId && !tab.isOnHub) {
                    if (tab.url != url) changed = true
                    tabIsIncognito = tab.isIncognito
                    tab.copy(
                        url = url,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else tab
            }
        }
        if (changed) {
            onAutoBypassIfEnabled(tabId, url)
            val wv = webViews[tabId]
            if (wv != null) {
                val payload = buildExtensionScriptForPage(
                    url = url,
                    runAtStages = setOf("document_end", "document_idle"),
                    isIncognito = tabIsIncognito
                )
                if (!payload.isNullOrBlank()) {
                    wv.evaluateJavascript(payload, null)
                }
            }
        }
    }

    fun onWebPageError(tabId: String, description: String) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId && !tab.isOnHub) {
                    tab.copy(isLoading = false, progress = 0, errorMessage = description)
                } else tab
            }
        }
    }

    fun buildExtensionScriptForPage(url: String, runAtStages: Set<String>, isIncognito: Boolean): String? {
        return extensionEngine.buildInjectionPayloadForUrl(
            extensions = uiState.value.extensions,
            pageUrl = url,
            targetRunAt = runAtStages,
            isIncognito = isIncognito
        )
    }

    // AI Chat, BrowserHub System Help & Creative Studio
    fun selectAiEngine(option: AiEngineOption) {
        _aiState.update { it.copy(selectedEngine = option) }
    }

    fun selectAiStudioMode(mode: AiStudioMode) {
        _aiState.update { it.copy(studioMode = mode) }
    }

    fun selectAiImageModel(model: AiImageModelOption) {
        _aiState.update { it.copy(selectedImageModel = model, imageError = null) }
    }

    fun selectAiImageQuality(quality: AiImageQualityOption) {
        _aiState.update { it.copy(selectedImageQuality = quality) }
    }

    fun selectAiImageAspect(aspect: AiImageAspectOption) {
        _aiState.update { it.copy(selectedImageAspect = aspect) }
    }

    fun selectAiVoice(voice: AiVoiceOption) {
        _aiState.update { it.copy(selectedVoice = voice) }
    }

    fun setAiScriptAudioMode(enabled: Boolean) {
        _aiState.update { it.copy(aiScriptAudioMode = enabled) }
    }

    fun openAiWithSummarize() {
        _aiState.update { it.copy(studioMode = AiStudioMode.CHAT) }
        _overlay.value = BrowserOverlay.AskAi
        sendAiPrompt("Summarize this page cleanly with key bullet points.")
    }

    private fun buildLiveContextSnapshot(): BrowserLiveContextSnapshot {
        val state = uiState.value
        val active = state.activeTab
        val vpn = state.vpnStats
        return BrowserLiveContextSnapshot(
            activeTabTitle = active.title,
            activeTabUrl = if (active.isOnHub) "" else active.url,
            isOnHubHome = active.isOnHub,
            isIncognito = active.isIncognito,
            isDesktopMode = active.isDesktopMode,
            isReaderMode = active.isReaderMode,
            openTabsCount = state.tabs.size,
            searchEngineName = state.settings.searchEngine.displayName,
            themeName = state.settings.studioTheme.title,
            addressBarPosition = state.settings.addressBarPosition.label,
            shieldsEnabled = state.shields.shieldsEnabled,
            adsBlockedTotal = state.tabs.sumOf { it.adsBlocked },
            trackersStoppedTotal = state.tabs.sumOf { it.trackersStopped },
            autoBypassShortlinks = state.shields.autoBypassShortlinks,
            noCaptchaShield = state.shields.noCaptchaShield,
            vpnConnected = vpn.isConnected,
            vpnProviderName = vpn.provider.displayName,
            virtualRegionLabel = vpn.virtualRegion.displayName,
            proxyExitModeLabel = vpn.proxyExitMode.displayName,
            activeProxyEndpoint = vpn.activeProxyEndpoint,
            installedExtensionCount = state.extensions.size,
            installedExtensionNames = state.extensions.map { it.name },
            detectedMediaCount = state.detectedMedia.size,
            activeDownloadsCount = state.downloads.size
        )
    }

    fun sendAiPrompt(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return
        aiJob?.cancel()

        val userMsg = AiChatMessage(id = UUID.randomUUID().toString(), isUser = true, text = prompt)
        val assistantMsgId = UUID.randomUUID().toString()
        val placeholderMsg = AiChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "Thinking…",
            engineUsed = _aiState.value.selectedEngine.displayName
        )

        val historyBefore = _aiState.value.messages
        _aiState.update { st ->
            st.copy(
                messages = st.messages + userMsg + placeholderMsg,
                isGenerating = true
            )
        }

        aiJob = viewModelScope.launch {
            val active = uiState.value.activeTab
            val liveSnapshot = buildLiveContextSnapshot()
            val pageExcerpt = if (!active.isOnHub && webViews[active.id] != null) {
                evalJsSuspend(webViews[active.id]!!, PageToolsEngine.EXTRACT_PAGE_TEXT_JS).orEmpty()
            } else ""

            aiEngine.streamPrompt(
                userPrompt = prompt,
                pageTitle = active.title,
                pageUrl = if (active.isOnHub) "" else active.url,
                pageExcerpt = pageExcerpt,
                liveContext = liveSnapshot,
                engineOption = _aiState.value.selectedEngine,
                history = historyBefore,
                onAttemptRecorded = { attempt ->
                    _aiState.update { st ->
                        st.copy(attemptLogs = (listOf(attempt) + st.attemptLogs).take(10))
                    }
                }
            ).collect { event ->
                when (event) {
                    is AiGenerationEvent.Chunk -> {
                        _aiState.update { st ->
                            st.copy(
                                messages = st.messages.map {
                                    if (it.id == assistantMsgId) it.copy(text = event.accumulatedText, engineUsed = event.provider)
                                    else it
                                }
                            )
                        }
                    }
                    is AiGenerationEvent.Completed -> {
                        _aiState.update { st ->
                            st.copy(
                                isGenerating = false,
                                messages = st.messages.map {
                                    if (it.id == assistantMsgId) {
                                        it.copy(
                                            text = event.finalText,
                                            engineUsed = event.provider,
                                            isError = false,
                                            suggestedActions = event.actions
                                        )
                                    } else it
                                }
                            )
                        }
                    }
                    is AiGenerationEvent.Failed -> {
                        _aiState.update { st ->
                            st.copy(
                                isGenerating = false,
                                messages = st.messages.map {
                                    if (it.id == assistantMsgId) it.copy(text = event.errorMessage, engineUsed = event.provider, isError = true)
                                    else it
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fun executeAiQuickAction(action: AiQuickNavigationAction) {
        when (action) {
            AiQuickNavigationAction.OPEN_VPN -> openOverlay(BrowserOverlay.Vpn)
            AiQuickNavigationAction.OPEN_EXTENSIONS -> openOverlay(BrowserOverlay.Extensions)
            AiQuickNavigationAction.OPEN_SHIELDS -> openOverlay(BrowserOverlay.ShieldsUp)
            AiQuickNavigationAction.OPEN_MEDIA_GRABBER -> openVideoGrabberAndSniff()
            AiQuickNavigationAction.OPEN_STUDIO -> openOverlay(BrowserOverlay.CustomizationStudio)
            AiQuickNavigationAction.OPEN_TRANSLATOR -> openOverlay(BrowserOverlay.PageTranslator)
            AiQuickNavigationAction.OPEN_TABS -> openOverlay(BrowserOverlay.TabVault)
            AiQuickNavigationAction.OPEN_DEVTOOLS -> {
                closeOverlay()
                toggleDevToolsPane()
            }
            AiQuickNavigationAction.OPEN_GAMES -> openOverlay(BrowserOverlay.GamesHub)
            AiQuickNavigationAction.OPEN_BENCHMARK -> openOverlay(BrowserOverlay.SpeedBenchmark)
            AiQuickNavigationAction.OPEN_DIAGNOSTICS -> openOverlay(BrowserOverlay.AboutDiagnostics)
            AiQuickNavigationAction.OPEN_BOOKMARKS -> openOverlay(BrowserOverlay.Bookmarks)
            AiQuickNavigationAction.OPEN_HISTORY -> openOverlay(BrowserOverlay.History)
            AiQuickNavigationAction.TOGGLE_READER -> {
                closeOverlay()
                toggleReaderMode()
            }
            AiQuickNavigationAction.TOGGLE_ZAPPER -> {
                closeOverlay()
                toggleElementZapper()
            }
        }
    }

    fun generateAiImage(rawPrompt: String, overrideModel: AiImageModelOption? = null) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return
        aiImageJob?.cancel()
        val chosenModel = overrideModel ?: _aiState.value.selectedImageModel
        if (overrideModel != null) {
            _aiState.update { it.copy(selectedImageModel = overrideModel) }
        }
        val quality = _aiState.value.selectedImageQuality
        val aspect = _aiState.value.selectedImageAspect

        _aiState.update { it.copy(isGeneratingImage = true, imageError = null) }
        aiImageJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val result = aiMediaEngine.generateImage(
                prompt = prompt,
                modelOption = chosenModel,
                qualityOption = quality,
                aspectOption = aspect
            )
            val latency = System.currentTimeMillis() - startedAt
            result.onSuccess { img ->
                _aiState.update { st ->
                    st.copy(
                        isGeneratingImage = false,
                        generatedImage = img,
                        imageError = null,
                        attemptLogs = (listOf(
                            AiAttemptLog(System.currentTimeMillis(), "Image (${chosenModel.displayName})", "OK", img.resolutionLabel, latency)
                        ) + st.attemptLogs).take(10)
                    )
                }
            }.onFailure { err ->
                _aiState.update { st ->
                    st.copy(
                        isGeneratingImage = false,
                        imageError = err.message ?: "Image generation failed.",
                        attemptLogs = (listOf(
                            AiAttemptLog(System.currentTimeMillis(), "Image (${chosenModel.displayName})", "ERROR", err.message ?: "Error", latency)
                        ) + st.attemptLogs).take(10)
                    )
                }
            }
        }
    }

    fun saveGeneratedImageToDevice() {
        val current = _aiState.value.generatedImage ?: return
        viewModelScope.launch {
            aiMediaEngine.saveBitmapToDevice(current.bitmap, "BrowserHub_4K")
                .onSuccess { location ->
                    _aiState.update { st ->
                        st.copy(generatedImage = st.generatedImage?.copy(savedLocation = location))
                    }
                    _toastMessages.tryEmit("Saved image to $location")
                }
                .onFailure { err ->
                    _toastMessages.tryEmit(err.message ?: "Could not save image")
                }
        }
    }

    fun generateAiAudio(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return
        aiAudioJob?.cancel()
        val voice = _aiState.value.selectedVoice
        val scriptFirst = _aiState.value.aiScriptAudioMode

        _aiState.update { it.copy(isGeneratingAudio = true, audioError = null) }
        aiAudioJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val result = aiMediaEngine.generateAudio(
                prompt = prompt,
                voiceOption = voice,
                expandTopicToScript = scriptFirst
            )
            val latency = System.currentTimeMillis() - startedAt
            result.onSuccess { audio ->
                _aiState.update { st ->
                    st.copy(
                        isGeneratingAudio = false,
                        generatedAudio = audio,
                        audioError = null,
                        attemptLogs = (listOf(
                            AiAttemptLog(System.currentTimeMillis(), "Audio TTS (${voice.voiceName})", "OK", "${audio.durationSeconds}s WAV", latency)
                        ) + st.attemptLogs).take(10)
                    )
                }
            }.onFailure { err ->
                _aiState.update { st ->
                    st.copy(
                        isGeneratingAudio = false,
                        audioError = err.message ?: "Audio generation failed.",
                        attemptLogs = (listOf(
                            AiAttemptLog(System.currentTimeMillis(), "Audio TTS (${voice.voiceName})", "ERROR", err.message ?: "Error", latency)
                        ) + st.attemptLogs).take(10)
                    )
                }
            }
        }
    }

    fun saveGeneratedAudioToDevice() {
        val current = _aiState.value.generatedAudio ?: return
        viewModelScope.launch {
            val fileName = "BrowserHub_Voice_${System.currentTimeMillis()}.wav"
            aiMediaEngine.saveFileToDownloads(current.wavFilePath, fileName, "audio/wav")
                .onSuccess { location ->
                    _aiState.update { st ->
                        st.copy(generatedAudio = st.generatedAudio?.copy(savedLocation = location))
                    }
                    _toastMessages.tryEmit("Saved audio to $location")
                }
                .onFailure { err ->
                    _toastMessages.tryEmit(err.message ?: "Could not save audio")
                }
        }
    }

    fun generateAiVideoReel(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return
        aiVideoJob?.cancel()
        val aspect = _aiState.value.selectedImageAspect
        val frameModel = _aiState.value.selectedImageModel
        val voice = _aiState.value.selectedVoice

        _aiState.update {
            it.copy(
                isGeneratingVideo = true,
                videoProgressMessage = "Starting AI Video Reel pipeline…",
                videoError = null
            )
        }
        aiVideoJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val result = aiMediaEngine.generateVideoReel(
                prompt = prompt,
                aspectOption = aspect,
                frameModel = frameModel,
                voiceOption = voice,
                onProgress = { msg ->
                    _aiState.update { it.copy(videoProgressMessage = msg) }
                }
            )
            val latency = System.currentTimeMillis() - startedAt
            result.onSuccess { vid ->
                _aiState.update { st ->
                    st.copy(
                        isGeneratingVideo = false,
                        videoProgressMessage = "",
                        generatedVideo = vid,
                        videoError = null,
                        attemptLogs = (listOf(
                            AiAttemptLog(System.currentTimeMillis(), "Video Reel (${frameModel.displayName})", "OK", "${vid.durationSeconds}s MP4", latency)
                        ) + st.attemptLogs).take(10)
                    )
                }
            }.onFailure { err ->
                _aiState.update { st ->
                    st.copy(
                        isGeneratingVideo = false,
                        videoProgressMessage = "",
                        videoError = err.message ?: "Video generation failed.",
                        attemptLogs = (listOf(
                            AiAttemptLog(System.currentTimeMillis(), "Video Reel (${frameModel.displayName})", "ERROR", err.message ?: "Error", latency)
                        ) + st.attemptLogs).take(10)
                    )
                }
            }
        }
    }

    fun saveGeneratedVideoToDevice() {
        val current = _aiState.value.generatedVideo ?: return
        viewModelScope.launch {
            val fileName = "BrowserHub_Reel_${System.currentTimeMillis()}.mp4"
            aiMediaEngine.saveFileToDownloads(current.mp4FilePath, fileName, "video/mp4")
                .onSuccess { location ->
                    _aiState.update { st ->
                        st.copy(generatedVideo = st.generatedVideo?.copy(savedLocation = location))
                    }
                    _toastMessages.tryEmit("Saved MP4 to $location")
                }
                .onFailure { err ->
                    _toastMessages.tryEmit(err.message ?: "Could not save MP4")
                }
        }
    }

    fun clearAiConversation() {
        aiJob?.cancel()
        _aiState.update { it.copy(messages = emptyList(), isGenerating = false) }
    }

    // Media Grabber (Video, Audio, Photos) & 16-Thread Downloader
    fun openVideoGrabberAndSniff() {
        _overlay.value = BrowserOverlay.VideoGrabber
        rescanActivePageMedia()
    }

    fun rescanActivePageMedia() {
        val active = uiState.value.activeTab
        val wv = if (!active.isOnHub) webViews[active.id] else null
        if (wv != null) {
            viewModelScope.launch {
                val raw = evalJsSuspend(wv, DownloadAndMediaEngine.PROBE_PAGE_MEDIA_JS) ?: "[]"
                downloadAndMediaEngine.recordExtractedDomPayload(raw, active.url)
            }
        }
    }

    fun inspectPastedVideoUrl(url: String) {
        viewModelScope.launch {
            _toolsState.update { it.copy(isProbingMedia = true, mediaProbeError = null) }
            try {
                val items = downloadAndMediaEngine.extractMediaFromLink(url)
                val first = items.first()
                if (items.size == 1 && first.isDrmProtected) {
                    _toolsState.update { it.copy(mediaProbeError = first.drmReason) }
                } else if (items.size == 1) {
                    _toastMessages.tryEmit("Detected ${first.kind.label}: ${first.formatBadge} (${first.sizeLabel})")
                } else {
                    val cleanCount = items.count { !it.isDrmProtected }
                    _toastMessages.tryEmit("Extracted $cleanCount Video / Audio / Photo items")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toolsState.update { it.copy(mediaProbeError = e.message ?: "Inspection failed") }
            } finally {
                _toolsState.update { it.copy(isProbingMedia = false) }
            }
        }
    }

    fun downloadAllPhotosOnPage() {
        val photos = uiState.value.detectedMedia.filter { it.kind == MediaKind.PHOTO && !it.isDrmProtected }.take(12)
        if (photos.isEmpty()) {
            _toastMessages.tryEmit("No photos detected yet — browse a page first")
            return
        }
        photos.forEach { photo ->
            downloadAndMediaEngine.startAcceleratedDownload(
                url = photo.url,
                suggestedName = photo.title,
                isHlsHint = false,
                mimeTypeHint = photo.mimeType
            )
        }
        _toastMessages.tryEmit("Saving ${photos.size} photos to Downloads/BrowserHub")
    }

    fun startAcceleratedDownload(
        url: String,
        fileName: String? = null,
        isHls: Boolean = false,
        mimeTypeHint: String? = null
    ) {
        val task = downloadAndMediaEngine.startAcceleratedDownload(url, fileName, isHls, mimeTypeHint)
        _toastMessages.tryEmit("16-Thread Accelerator started: ${task.fileName}")
    }

    fun cancelDownload(taskId: String) {
        downloadAndMediaEngine.cancelDownload(taskId)
    }

    // DevTools, Benchmarks, Diagnostics, Games
    fun toggleDevToolsPane() {
        val next = !_toolsState.value.devToolsOpen
        _toolsState.update { it.copy(devToolsOpen = next) }
        _overlay.value = BrowserOverlay.None
        if (next) {
            val active = uiState.value.activeTab
            webViews[active.id]?.evaluateJavascript(PageToolsEngine.DEVTOOLS_HOOK_JS, null)
        }
    }

    fun evaluateDevToolsExpression(js: String) {
        val active = uiState.value.activeTab
        val wv = webViews[active.id]
        if (active.isOnHub || wv == null) {
            _toolsState.update { st ->
                st.copy(devToolsLogs = listOf(DevToolsLogEntry("WARN", "Open any webpage to evaluate JS")) + st.devToolsLogs)
            }
            return
        }
        viewModelScope.launch {
            val result = evalJsRawSuspend(wv, js)
            _toolsState.update { st ->
                val next = listOf(
                    DevToolsLogEntry("OUT", result),
                    DevToolsLogEntry("IN", "> $js")
                ) + st.devToolsLogs
                st.copy(devToolsLogs = next.take(120))
            }
        }
    }

    fun clearDevToolsLogs() {
        _toolsState.update { it.copy(devToolsLogs = emptyList()) }
    }

    fun runSpeedBenchmark() {
        val active = uiState.value.activeTab
        val existingWv = webViews[active.id]
        viewModelScope.launch {
            _toolsState.update { it.copy(isBenchmarkRunning = true) }
            try {
                val wv = existingWv ?: WebView(getApplication<Application>()).apply {
                    settings.javaScriptEnabled = true
                }
                val raw = evalJsSuspend(wv, PageToolsEngine.BENCHMARK_JS)
                if (existingWv == null) wv.destroy()
                if (!raw.isNullOrBlank()) {
                    val obj = JSONObject(raw)
                    _toolsState.update {
                        it.copy(
                            benchmarkReport = BenchmarkReport(
                                totalScore = obj.optInt("score", 500),
                                jsOpsMs = obj.optDouble("jsMs", 0.0),
                                domRenderMs = obj.optDouble("domMs", 0.0),
                                canvas2dMs = obj.optDouble("canvasMs", 0.0),
                                wasmAvailable = obj.optBoolean("wasm", true),
                                webGlRenderer = obj.optString("webgl", "WebGL 2")
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                _toastMessages.tryEmit("Benchmark error")
            } finally {
                _toolsState.update { it.copy(isBenchmarkRunning = false) }
            }
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            val wv = webViews[_activeTabId.value]
            if (wv != null && lastWebProbeJson == null) {
                lastWebProbeJson = evalJsSuspend(wv, DiagnosticsEngine.CAPABILITY_PROBE_JS)
            }
            val exts = uiState.value.extensions
            val working = exts.count { it.enabled }
            val vpn = VpnStateHolder.stats.value
            val report = DiagnosticsEngine.collectNativeDiagnostics(
                context = getApplication(),
                extensionsWorking = working,
                extensionsTotal = exts.size,
                gamesCount = GamesCatalog.games.size,
                vpnConnected = vpn.isConnected,
                vpnProviderName = vpn.provider.displayName,
                webProbeJson = lastWebProbeJson
            )
            _toolsState.update { it.copy(diagnosticsReport = report) }
        }
    }

    fun launchGame(game: BrowserGame) {
        _toolsState.update { it.copy(activeGame = game) }
    }

    fun closeGame() {
        _toolsState.update { it.copy(activeGame = null) }
    }

    // Extensions management
    fun installExtensionFromLocalUri(uri: Uri) {
        viewModelScope.launch {
            _isInstallingExtension.value = true
            try {
                val installed = extensionEngine.installFromLocalUri(uri)
                _toastMessages.tryEmit("Added ${installed.name} v${installed.version} to BrowserHub")
                notifyPageAfterExtensionInstall(installed.id, installed.name)
            } catch (e: Exception) {
                _toastMessages.tryEmit("Install failed: ${e.message ?: "Invalid extension package"}")
            } finally {
                _isInstallingExtension.value = false
            }
        }
    }

    fun installExtensionFromUrl(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return
        val match = ExtensionStoreDirectory.detectStoreExtension(trimmed)
        val normalizedUrl = if (match != null) trimmed else UrlHelper.normalizeShortcutUrl(trimmed)
        viewModelScope.launch {
            _isInstallingExtension.value = true
            try {
                val installed = extensionEngine.installFromStoreOrPackageUrl(
                    rawUrl = normalizedUrl,
                    sourceBrowserHint = match?.sourceBrowser,
                    suggestedId = match?.extensionId
                )
                _toastMessages.tryEmit("Added ${installed.name} v${installed.version} to BrowserHub")
                notifyPageAfterExtensionInstall(installed.id, installed.name)
            } catch (e: Exception) {
                _toastMessages.tryEmit("Install failed: ${e.message ?: "Could not fetch package"}")
                resetStoreInstallingIfNeeded()
            } finally {
                _isInstallingExtension.value = false
            }
        }
    }

    fun installFromStoreMatch(
        downloadUrl: String,
        sourceBrowser: ExtensionSourceBrowser,
        suggestedId: String? = null
    ) {
        viewModelScope.launch {
            _isInstallingExtension.value = true
            try {
                val activeUrl = uiState.value.activeTab.url
                val targetUrl = if (ExtensionStoreDirectory.detectStoreExtension(activeUrl)?.extensionId == suggestedId) {
                    activeUrl
                } else {
                    downloadUrl
                }
                val installed = extensionEngine.installFromStoreOrPackageUrl(
                    rawUrl = targetUrl,
                    sourceBrowserHint = sourceBrowser,
                    suggestedId = suggestedId
                )
                _toastMessages.tryEmit("Added ${installed.name} (${sourceBrowser.label}) to BrowserHub")
                notifyPageAfterExtensionInstall(installed.id, installed.name)
            } catch (e: Exception) {
                _toastMessages.tryEmit("Download failed: ${e.message ?: "Could not fetch package"}")
                resetStoreInstallingIfNeeded()
            } finally {
                _isInstallingExtension.value = false
            }
        }
    }

    fun installBuiltInStarterExtension(templateId: String) {
        viewModelScope.launch {
            _isInstallingExtension.value = true
            try {
                val installed = extensionEngine.installBuiltInTemplate(templateId)
                _toastMessages.tryEmit("Added ${installed.name} to BrowserHub")
                notifyPageAfterExtensionInstall(installed.id, installed.name)
            } catch (e: Exception) {
                _toastMessages.tryEmit("Install failed: ${e.message}")
            } finally {
                _isInstallingExtension.value = false
            }
        }
    }

    fun installStudioCustomScript(
        name: String,
        version: String,
        matchPattern: String,
        runAt: String,
        jsCode: String,
        cssCode: String
    ) {
        viewModelScope.launch {
            _isInstallingExtension.value = true
            try {
                val installed = extensionEngine.installCustomStudioExtension(
                    name = name,
                    version = version,
                    matchPattern = matchPattern,
                    runAt = runAt,
                    jsCode = jsCode,
                    cssCode = cssCode
                )
                _toastMessages.tryEmit("Installed Studio script: ${installed.name}")
            } catch (e: Exception) {
                _toastMessages.tryEmit("Studio build failed: ${e.message}")
            } finally {
                _isInstallingExtension.value = false
            }
        }
    }

    private fun notifyPageAfterExtensionInstall(extensionId: String, extensionName: String) {
        val active = uiState.value.activeTab
        if (active.isOnHub) return
        val nextIds = uiState.value.installedExtensionIds + extensionId
        if (ExtensionStoreDirectory.isExtensionStoreHost(active.url)) {
            _commands.tryEmit(
                WebCommand.NotifyStoreExtensionInstalled(
                    tabId = active.id,
                    extensionId = extensionId,
                    extensionName = extensionName,
                    installedIds = nextIds
                )
            )
        } else {
            _commands.tryEmit(WebCommand.Reload(active.id))
        }
    }

    private fun resetStoreInstallingIfNeeded() {
        val active = uiState.value.activeTab
        if (!active.isOnHub && ExtensionStoreDirectory.isExtensionStoreHost(active.url)) {
            _commands.tryEmit(
                WebCommand.SyncStoreExtensionState(
                    tabId = active.id,
                    installedIds = uiState.value.installedExtensionIds,
                    clearInstalling = true
                )
            )
        }
    }

    fun toggleExtensionEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setExtensionEnabled(id, enabled)
            val active = uiState.value.activeTab
            if (!active.isOnHub && !ExtensionStoreDirectory.isExtensionStoreHost(active.url)) {
                _commands.tryEmit(WebCommand.Reload(active.id))
            }
        }
    }

    fun toggleExtensionIncognito(id: String, allowed: Boolean) {
        viewModelScope.launch {
            repository.setExtensionAllowInIncognito(id, allowed)
        }
    }

    fun uninstallExtension(extension: ExtensionEntity) {
        viewModelScope.launch {
            extensionEngine.uninstallExtension(extension)
            if (_activeExtensionPage.value?.extensionId == extension.id) {
                _activeExtensionPage.value = null
            }
            _toastMessages.tryEmit("Removed ${extension.name}")
            val active = uiState.value.activeTab
            if (!active.isOnHub) {
                if (ExtensionStoreDirectory.isExtensionStoreHost(active.url)) {
                    _commands.tryEmit(
                        WebCommand.SyncStoreExtensionState(
                            tabId = active.id,
                            installedIds = uiState.value.installedExtensionIds - extension.id
                        )
                    )
                } else {
                    _commands.tryEmit(WebCommand.Reload(active.id))
                }
            }
        }
    }

    fun openInstalledStoreExtension(match: StoreExtensionMatch) {
        val normId = ExtensionStoreDirectory.normalizeExtensionId(match.extensionId)
        val ext = uiState.value.extensions.firstOrNull {
            it.id == match.extensionId || it.id == normId
        }
        if (ext != null && !ext.popupPath.isNullOrBlank()) {
            openExtensionPopup(ext.id)
        } else {
            openOverlay(BrowserOverlay.Extensions)
        }
    }

    fun openExtensionPopup(extensionId: String) {
        _activeExtensionPage.value = ExtensionPageRequest(extensionId, isOptionsPage = false)
    }

    fun openExtensionOptions(extensionId: String) {
        _activeExtensionPage.value = ExtensionPageRequest(extensionId, isOptionsPage = true)
    }

    fun closeExtensionPopup() {
        _activeExtensionPage.value = null
    }

    // VPN & Location Shield
    fun selectVpnProvider(provider: VpnProvider) {
        val wasConnected = VpnStateHolder.stats.value.isConnected
        VpnStateHolder.setProvider(provider)
        if (wasConnected) {
            BrowserHubVpnService.start(getApplication(), provider)
            _toastMessages.tryEmit("Switched Privacy VPN to ${provider.displayName}")
        }
    }

    fun selectVirtualRegion(region: VirtualRegion) {
        VpnStateHolder.setVirtualRegion(region)
        val active = uiState.value.activeTab
        if (VpnStateHolder.stats.value.isConnected && !active.isOnHub) {
            _commands.tryEmit(WebCommand.Reload(active.id))
        }
        _toastMessages.tryEmit(
            if (region == VirtualRegion.OFF) {
                "Browser Location Mask turned OFF"
            } else {
                "Location Shield set to ${region.displayName} (${region.timezoneId})"
            }
        )
    }

    fun configureProxyExit(mode: ProxyExitMode, customInput: String? = null) {
        viewModelScope.launch {
            when (mode) {
                ProxyExitMode.DIRECT_DOH -> {
                    VpnStateHolder.setProxyExitConfig(
                        mode = ProxyExitMode.DIRECT_DOH,
                        customProxyUrl = customInput,
                        activeEndpoint = null
                    )
                    _toastMessages.tryEmit("Exit route: Encrypted DoH + Browser Location Mask")
                    refreshNetworkIpCheck()
                }
                ProxyExitMode.ORBOT_TOR -> {
                    val reachablePort = withContext(Dispatchers.IO) {
                        when {
                            isLocalPortOpen("127.0.0.1", 8118) -> "http://127.0.0.1:8118"
                            isLocalPortOpen("127.0.0.1", 9050) -> "socks5://127.0.0.1:9050"
                            else -> null
                        }
                    }
                    if (reachablePort == null) {
                        _toastMessages.tryEmit("Orbot / Tor not detected on 127.0.0.1:8118 or 9050 — start Orbot app first or keep DoH mode")
                        return@launch
                    }
                    VpnStateHolder.setProxyExitConfig(
                        mode = ProxyExitMode.ORBOT_TOR,
                        activeEndpoint = reachablePort
                    )
                    _toastMessages.tryEmit("Orbot / Tor Exit Proxy armed ($reachablePort)")
                    refreshNetworkIpCheck()
                }
                ProxyExitMode.CUSTOM_PROXY -> {
                    val raw = customInput?.trim().orEmpty()
                    val parsed = VpnProxyParser.parse(raw)
                    if (parsed == null) {
                        _toastMessages.tryEmit("Enter proxy as socks5://host:port or http://host:port")
                        return@launch
                    }
                    val reachable = withContext(Dispatchers.IO) {
                        isLocalPortOpen(parsed.host, parsed.port, timeoutMs = 2500)
                    }
                    if (!reachable) {
                        _toastMessages.tryEmit("Proxy ${parsed.host}:${parsed.port} did not respond — check server & port")
                        return@launch
                    }
                    VpnStateHolder.setProxyExitConfig(
                        mode = ProxyExitMode.CUSTOM_PROXY,
                        customProxyUrl = parsed.proxyConfigUrl,
                        activeEndpoint = parsed.proxyConfigUrl
                    )
                    _toastMessages.tryEmit("Custom Exit Proxy active: ${parsed.proxyConfigUrl}")
                    refreshNetworkIpCheck()
                }
            }
        }
    }

    fun refreshNetworkIpCheck() {
        viewModelScope.launch {
            VpnStateHolder.setNetworkChecking(true)
            val currentStats = VpnStateHolder.stats.value
            val activeSpec: ParsedProxySpec? = if (currentStats.isConnected && !currentStats.activeProxyEndpoint.isNullOrBlank()) {
                VpnProxyParser.parse(currentStats.activeProxyEndpoint)
            } else null

            val result = withContext(Dispatchers.IO) {
                fetchPublicIpInfo(activeSpec)
            }
            VpnStateHolder.setNetworkCheckResult(result)
        }
    }

    private fun isLocalPortOpen(host: String, port: Int, timeoutMs: Int = 900): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchPublicIpInfo(proxySpec: ParsedProxySpec?): NetworkCheckResult {
        val viaLabel = proxySpec?.let { "Exit Proxy (${it.proxyConfigUrl})" } ?: "Direct ISP / Carrier WAN"
        return try {
            val url = URL("https://ipinfo.io/json")
            val conn = (if (proxySpec != null) {
                url.openConnection(proxySpec.toJavaNetProxy())
            } else {
                url.openConnection()
            }) as HttpURLConnection
            conn.apply {
                connectTimeout = 5500
                readTimeout = 5500
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
            }
            try {
                if (conn.responseCode !in 200..299) {
                    return NetworkCheckResult(
                        publicIp = "Unavailable",
                        locationLabel = "HTTP ${conn.responseCode}",
                        ispLabel = "",
                        viaProxyLabel = viaLabel,
                        error = "Could not query IP check endpoint"
                    )
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val ip = json.optString("ip").ifBlank { "Unknown" }
                val city = json.optString("city")
                val region = json.optString("region")
                val country = json.optString("country")
                val org = json.optString("org")
                val loc = listOf(city, region, country).filter { it.isNotBlank() }.joinToString(", ")
                    .ifBlank { "Unknown Location" }
                NetworkCheckResult(
                    publicIp = ip,
                    locationLabel = loc,
                    ispLabel = org,
                    viaProxyLabel = viaLabel
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkCheckResult(
                publicIp = "Check Failed",
                locationLabel = e.localizedMessage ?: "Network error",
                ispLabel = "",
                viaProxyLabel = viaLabel,
                error = e.localizedMessage
            )
        }
    }

    fun updateVpnPrivacyToggles(
        blockWebTrackers: Boolean? = null,
        blockThirdPartyCookies: Boolean? = null,
        webRtcLeakProtection: Boolean? = null
    ) {
        VpnStateHolder.updatePrivacyToggles(
            blockWebTrackers = blockWebTrackers,
            blockThirdPartyCookies = blockThirdPartyCookies,
            webRtcLeakProtection = webRtcLeakProtection
        )
    }

    fun stopVpn() {
        BrowserHubVpnService.stop(getApplication())
    }

    // Page Superpowers (AMOLED Force Dark Web, Auto-Scroll, Video Speed, Copy Unlock, Night Dimmer, Page X-Ray)
    fun openPageSuperpowersAndInspect() {
        _overlay.value = BrowserOverlay.PageSuperpowers
        inspectLivePageXRay()
    }

    fun inspectLivePageXRay() {
        val active = uiState.value.activeTab
        val wv = if (!active.isOnHub) webViews[active.id] else null
        if (wv == null) {
            _toolsState.update {
                it.copy(
                    isInspectingXRay = false,
                    pageXRayReport = PageXRayReport(
                        url = "browserhub://start",
                        title = "BrowserHub Start Page",
                        privacyRiskLabel = "Zero Trackers"
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _toolsState.update { it.copy(isInspectingXRay = true) }
            try {
                val rawJson = evalJsSuspend(wv, PageSuperpowersEngine.INSPECT_PAGE_XRAY_JS).orEmpty()
                val report = PageSuperpowersEngine.parseXRayReport(rawJson, active.url)
                _toolsState.update { it.copy(pageXRayReport = report) }
            } finally {
                _toolsState.update { it.copy(isInspectingXRay = false) }
            }
        }
    }

    fun toggleForceDarkWeb() {
        val next = !_toolsState.value.pageSuperpowers.forceDarkWeb
        _toolsState.update { st ->
            st.copy(pageSuperpowers = st.pageSuperpowers.copy(forceDarkWeb = next))
        }
        webViews[_activeTabId.value]?.evaluateJavascript(PageSuperpowersEngine.buildForceDarkWebJs(next), null)
        _toastMessages.tryEmit(if (next) "AMOLED Force Dark Web ON" else "AMOLED Force Dark Web OFF")
    }

    fun setAutoScrollSpeed(speedLevel: Int) {
        val clamped = speedLevel.coerceIn(0, 3)
        _toolsState.update { st ->
            st.copy(pageSuperpowers = st.pageSuperpowers.copy(autoScrollSpeed = clamped))
        }
        val active = uiState.value.activeTab
        if (!active.isOnHub) {
            webViews[active.id]?.evaluateJavascript(PageSuperpowersEngine.buildAutoScrollJs(clamped), null)
        }
        _toastMessages.tryEmit(if (clamped == 0) "Auto-Scroll paused" else "Teleprompter Auto-Scroll: ${clamped}x")
    }

    fun setWebVideoSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        _toolsState.update { st ->
            st.copy(pageSuperpowers = st.pageSuperpowers.copy(videoSpeed = clamped))
        }
        val active = uiState.value.activeTab
        if (!active.isOnHub) {
            webViews[active.id]?.evaluateJavascript(PageSuperpowersEngine.buildVideoSpeedJs(clamped), null)
        }
        _toastMessages.tryEmit("HTML5 Video Speed set to ${clamped}x")
    }

    fun toggleCopyUnlock() {
        val next = !_toolsState.value.pageSuperpowers.copyUnlockEnabled
        _toolsState.update { st ->
            st.copy(pageSuperpowers = st.pageSuperpowers.copy(copyUnlockEnabled = next))
        }
        val active = uiState.value.activeTab
        if (!active.isOnHub) {
            webViews[active.id]?.evaluateJavascript(PageSuperpowersEngine.buildCopyUnlockJs(next), null)
        }
        _toastMessages.tryEmit(if (next) "Copy & Right-Click Unlocker ON" else "Copy Unlocker OFF")
    }

    fun toggleNightTint() {
        val next = !_toolsState.value.pageSuperpowers.nightTintEnabled
        _toolsState.update { st ->
            st.copy(pageSuperpowers = st.pageSuperpowers.copy(nightTintEnabled = next))
        }
        _toastMessages.tryEmit(if (next) "Blue-Light Night Dimmer ON" else "Night Dimmer OFF")
    }

    private fun applyActiveSuperpowersToWebView(webView: WebView) {
        val sp = _toolsState.value.pageSuperpowers
        if (sp.forceDarkWeb) {
            webView.evaluateJavascript(PageSuperpowersEngine.buildForceDarkWebJs(true), null)
        }
        if (sp.copyUnlockEnabled) {
            webView.evaluateJavascript(PageSuperpowersEngine.buildCopyUnlockJs(true), null)
        }
        if (sp.videoSpeed != 1.0f) {
            webView.evaluateJavascript(PageSuperpowersEngine.buildVideoSpeedJs(sp.videoSpeed), null)
        }
        if (sp.autoScrollSpeed > 0) {
            webView.evaluateJavascript(PageSuperpowersEngine.buildAutoScrollJs(sp.autoScrollSpeed), null)
        }
    }

    // Overlays & Settings
    fun openOverlay(overlay: BrowserOverlay) {
        _overlay.value = overlay
        if (overlay == BrowserOverlay.AboutDiagnostics) {
            refreshDiagnostics()
        } else if (overlay == BrowserOverlay.PageSuperpowers) {
            inspectLivePageXRay()
        }
    }

    fun closeOverlay() {
        _overlay.value = BrowserOverlay.None
    }

    fun toggleBookmarkForCurrentPage(savedMsg: String, removedMsg: String) {
        val active = uiState.value.activeTab
        if (active.isOnHub || active.url.isBlank()) return
        viewModelScope.launch {
            val added = repository.toggleBookmark(active.title, active.url)
            _toastMessages.tryEmit(if (added) savedMsg else removedMsg)
        }
    }

    fun showAddShortcutDialog(show: Boolean) {
        _showAddShortcutDialog.value = show
    }

    fun addSpeedDialShortcut(title: String, url: String) {
        viewModelScope.launch {
            repository.addSpeedDialSite(title, url)
            _showAddShortcutDialog.value = false
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { repository.deleteHistoryItem(id) }
    }

    fun clearHistory(clearedMsg: String) {
        viewModelScope.launch {
            repository.clearHistory()
            _toastMessages.tryEmit(clearedMsg)
        }
    }

    fun clearCacheAndCookies(clearedMsg: String) {
        viewModelScope.launch {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            _commands.tryEmit(WebCommand.ClearCache(_activeTabId.value))
            _toastMessages.tryEmit(clearedMsg)
        }
    }

    fun setFindInPageActive(active: Boolean) {
        val tabId = _activeTabId.value
        if (active) {
            _overlay.value = BrowserOverlay.None
            _findInPage.value = FindInPageState(isActive = true)
        } else {
            _findInPage.value = FindInPageState(isActive = false)
            _commands.tryEmit(WebCommand.ClearFind(tabId))
        }
    }

    fun updateFindQuery(query: String) {
        val tabId = _activeTabId.value
        _findInPage.update { it.copy(query = query) }
        if (query.isBlank()) {
            _findInPage.update { it.copy(activeMatchOrdinal = 0, numberOfMatches = 0) }
            _commands.tryEmit(WebCommand.ClearFind(tabId))
        } else {
            _commands.tryEmit(WebCommand.FindAll(tabId, query))
        }
    }

    fun findNext(forward: Boolean) {
        val tabId = _activeTabId.value
        if (_findInPage.value.query.isNotBlank()) {
            _commands.tryEmit(WebCommand.FindNext(tabId, forward))
        }
    }

    fun onFindResultsUpdated(activeOrdinal: Int, totalMatches: Int) {
        _findInPage.update {
            it.copy(
                activeMatchOrdinal = if (totalMatches > 0) activeOrdinal + 1 else 0,
                numberOfMatches = totalMatches
            )
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { repository.setSearchEngine(engine) }
    }

    fun setStudioTheme(theme: StudioTheme) {
        viewModelScope.launch { repository.setStudioTheme(theme) }
    }

    fun setAddressBarPosition(position: AddressBarPosition) {
        viewModelScope.launch { repository.setAddressBarPosition(position) }
    }

    fun toggleAddressBarPosition() {
        val next = if (settingsFlow.value.addressBarPosition == AddressBarPosition.TOP) {
            AddressBarPosition.BOTTOM
        } else {
            AddressBarPosition.TOP
        }
        setAddressBarPosition(next)
    }

    fun setComfortableUi(enabled: Boolean) {
        viewModelScope.launch { repository.setComfortableUi(enabled) }
    }

    fun setDesktopDevToolsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setDesktopDevToolsEnabled(enabled) }
    }

    fun setReaderPaper(paper: ReaderPaper) {
        viewModelScope.launch {
            repository.setReaderPaper(paper)
            rebuildActiveReaderIfNeeded(paper = paper)
        }
    }

    fun setReaderFont(font: ReaderFont) {
        viewModelScope.launch {
            repository.setReaderFont(font)
            rebuildActiveReaderIfNeeded(font = font)
        }
    }

    fun adjustPageTextZoom(delta: Int) {
        val next = (settingsFlow.value.pageTextZoom + delta).coerceIn(50, 200)
        viewModelScope.launch {
            repository.setPageTextZoom(next)
            rebuildActiveReaderIfNeeded(zoomPercent = next)
        }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setJavaScriptEnabled(enabled) }
    }

    fun setBlockPopups(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockPopups(enabled) }
    }

    fun setDefaultDesktopMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDefaultDesktopMode(enabled) }
    }

    fun updateShieldsConfig(transform: (ShieldsConfig) -> ShieldsConfig) {
        viewModelScope.launch { repository.updateShields(transform) }
    }

    fun showToast(message: String) {
        _toastMessages.tryEmit(message)
    }

    private suspend fun evalJsSuspend(webView: WebView, script: String): String? =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { raw ->
                if (!cont.isActive) return@evaluateJavascript
                val decoded = if (raw == null || raw == "null") {
                    null
                } else if (raw.startsWith("\"") && raw.endsWith("\"")) {
                    runCatching { JSONArray("[$raw]").getString(0) }.getOrElse { raw }
                } else {
                    raw
                }
                cont.resume(decoded)
            }
        }

    private suspend fun evalJsRawSuspend(webView: WebView, script: String): String =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { raw ->
                if (cont.isActive) cont.resume(raw ?: "undefined")
            }
        }

    override fun onCleared() {
        super.onCleared()
        aiJob?.cancel()
        aiImageJob?.cancel()
        aiAudioJob?.cancel()
        aiVideoJob?.cancel()
        webViews.values.forEach { it.destroy() }
        webViews.clear()
        _activeWebViewIds.value = emptySet()
    }
}
