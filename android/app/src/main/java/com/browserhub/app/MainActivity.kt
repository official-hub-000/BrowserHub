package com.browserhub.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.browserhub.app.ai.LocalNeuralEngine
import com.browserhub.app.bridge.BrowserHubJavascriptBridge
import com.browserhub.app.engine.HybridBrowserEngine
import com.browserhub.app.extensions.UniversalExtensionManager
import com.browserhub.app.privacy.HubShieldEngine
import com.browserhub.app.vpn.HubVpnService
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var shellWebView: WebView
    private lateinit var browserWebView: WebView
    private lateinit var hybridEngine: HybridBrowserEngine
    private lateinit var shieldEngine: HubShieldEngine
    private lateinit var extensionManager: UniversalExtensionManager
    private lateinit var neuralEngine: LocalNeuralEngine

    private var mobileDefaultUa: String = ""
    private var lastLoadedUrl: String = ""
    private var lastDesktopState: Boolean = false
    private var lastTopPx: Int = -1
    private var lastBottomPx: Int = -1
    private var lastFindQuery: String = ""
    private var pendingVpnIntent: Intent? = null

    @Volatile var darkReaderEnabled: Boolean = false
    @Volatile var cookieKillerEnabled: Boolean = true
    @Volatile var activeCustomCss: String = ""
    @Volatile var extractedPageText: String = ""

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingVpnIntent?.let { startService(it) }
        }
        pendingVpnIntent = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val highestRefresh = display?.supportedModes?.maxByOrNull { it.refreshRate }
            if (highestRefresh != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = highestRefresh.modeId
                }
            }
        }

        shieldEngine = HubShieldEngine()
        hybridEngine = HybridBrowserEngine(applicationContext, shieldEngine)
        extensionManager = UniversalExtensionManager(applicationContext, hybridEngine)
        neuralEngine = LocalNeuralEngine(applicationContext)

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // 1. Shell WebView (React 19 BrowserHub Chrome)
        shellWebView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }

            addJavascriptInterface(
                BrowserHubJavascriptBridge(
                    activity = this@MainActivity,
                    engine = hybridEngine,
                    shield = shieldEngine,
                    extensions = extensionManager,
                    neural = neuralEngine
                ),
                "BrowserHubNative"
            )
        }

        // 2. Native Real-Web Browser WebView
        browserWebView = WebView(this).apply {
            visibility = View.GONE
            mobileDefaultUa = settings.userAgentString + " BrowserHub/1.0"
            settings.userAgentString = mobileDefaultUa
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                try {
                    val req = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        addRequestHeader("User-Agent", userAgent)
                        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                        setTitle(filename)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    }
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    dm?.enqueue(req)
                    Toast.makeText(this@MainActivity, "Downloading file...", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                }
            }

            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    if (!request.isForMainFrame && shieldEngine.shouldBlockNetworkRequest(request.url.toString())) {
                        val host = request.url.host.orEmpty()
                        notifyShellAdBlocked(host)
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    lastLoadedUrl = url
                    notifyShellUrlChanged(url, view.title ?: url)
                    injectExtensionsAndExtractText(view)
                }
            }
        }

        rootLayout.addView(
            shellWebView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        rootLayout.addView(
            browserWebView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        setContentView(rootLayout)
        shellWebView.loadUrl("https://appassets.androidplatform.net/assets/public/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (browserWebView.visibility == View.VISIBLE && browserWebView.canGoBack()) {
                    browserWebView.goBack()
                    return
                }
                shellWebView.evaluateJavascript(
                    "(function(){return window.__onAndroidBackPressed ? window.__onAndroidBackPressed() : false;})()"
                ) { res ->
                    if (res != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun injectExtensionsAndExtractText(view: WebView) {
        val css = StringBuilder()
        if (darkReaderEnabled) {
            css.append("html { filter: invert(0.92) hue-rotate(180deg) !important; background: #111 !important; } ")
                .append("img, video, picture, canvas, iframe { filter: invert(1) hue-rotate(180deg) !important; } ")
        }
        if (cookieKillerEnabled) {
            css.append("#onetrust-consent-sdk, .fc-consent-root, .cc-window, [id*='cookie-banner'], [class*='cookie-consent'] { display: none !important; } ")
        }
        if (activeCustomCss.isNotEmpty()) {
            css.append(activeCustomCss)
        }
        if (css.isNotEmpty()) {
            val safeCss = css.toString().replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
            view.evaluateJavascript(
                "(function(){var el=document.getElementById('__bh_ext_css');if(!el){el=document.createElement('style');el.id='__bh_ext_css';document.head.appendChild(el);}el.textContent='$safeCss';})();",
                null
            )
        }
        view.evaluateJavascript(
            "(function(){return (document.title||'') + '. ' + (document.body ? document.body.innerText.substring(0, 10000) : '');})()"
        ) { value ->
            if (!value.isNullOrBlank()) {
                extractedPageText = value.replace("\\n", " ").replace("\\\"", "\"")
            }
        }
    }

    private fun notifyShellUrlChanged(url: String, title: String) {
        runOnUiThread {
            val su = escapeJs(url)
            val st = escapeJs(title)
            shellWebView.evaluateJavascript(
                "if(window.__onNativeUrlChanged){window.__onNativeUrlChanged('$su','$st');}",
                null
            )
        }
    }

    private fun notifyShellAdBlocked(host: String) {
        runOnUiThread {
            val sh = escapeJs(host)
            shellWebView.evaluateJavascript(
                "if(window.__onNativeAdBlocked){window.__onNativeAdBlocked('$sh');}",
                null
            )
        }
    }

    fun syncNativeBrowserViewport(
        url: String,
        topCssPx: Float,
        bottomCssPx: Float,
        winHeightCss: Float,
        isDesktop: Boolean,
        adblockOn: Boolean,
        darkReaderOn: Boolean,
        cookieKillerOn: Boolean,
        customCss: String,
        zoomPct: Int,
        findQuery: String,
        visible: Boolean
    ) {
        shieldEngine.adblockEnabled = adblockOn
        this.darkReaderEnabled = darkReaderOn
        this.cookieKillerEnabled = cookieKillerOn
        this.activeCustomCss = customCss

        runOnUiThread {
            if (!visible || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                browserWebView.visibility = View.GONE
                return@runOnUiThread
            }

            val totalH = if (rootLayout.height > 0) rootLayout.height else resources.displayMetrics.heightPixels
            val safeWinH = if (winHeightCss > 10f) winHeightCss else 800f
            val topPx = ((topCssPx / safeWinH) * totalH).roundToInt().coerceAtLeast(0)
            val bottomPx = ((bottomCssPx / safeWinH) * totalH).roundToInt().coerceAtLeast(0)

            if (topPx != lastTopPx || bottomPx != lastBottomPx) {
                lastTopPx = topPx
                lastBottomPx = bottomPx
                browserWebView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(0, topPx, 0, bottomPx)
                }
            }

            browserWebView.settings.textZoom = zoomPct.coerceIn(50, 200)

            val uaChanged = isDesktop != lastDesktopState
            if (uaChanged) {
                lastDesktopState = isDesktop
                browserWebView.settings.userAgentString =
                    if (isDesktop) HybridBrowserEngine.DESKTOP_USER_AGENT else mobileDefaultUa
            }

            if (findQuery != lastFindQuery) {
                lastFindQuery = findQuery
                if (findQuery.isEmpty()) browserWebView.clearMatches() else browserWebView.findAllAsync(findQuery)
            }

            browserWebView.visibility = View.VISIBLE
            if (url != lastLoadedUrl) {
                lastLoadedUrl = url
                browserWebView.loadUrl(url)
            } else if (uaChanged) {
                browserWebView.reload()
            } else {
                injectExtensionsAndExtractText(browserWebView)
            }
        }
    }

    fun setBrowserVisibleOnly(visible: Boolean) {
        runOnUiThread {
            browserWebView.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun goBackBrowser(): Boolean {
        if (browserWebView.visibility == View.VISIBLE && browserWebView.canGoBack()) {
            runOnUiThread { if (browserWebView.canGoBack()) browserWebView.goBack() }
            return true
        }
        return false
    }

    fun goForwardBrowser(): Boolean {
        if (browserWebView.visibility == View.VISIBLE && browserWebView.canGoForward()) {
            runOnUiThread { if (browserWebView.canGoForward()) browserWebView.goForward() }
            return true
        }
        return false
    }

    fun reloadBrowser() {
        runOnUiThread { if (browserWebView.visibility == View.VISIBLE) browserWebView.reload() }
    }

    fun shredBrowserWebView() {
        runOnUiThread {
            WebStorage.getInstance().deleteAllData()
            browserWebView.stopLoading()
            browserWebView.clearCache(true)
            browserWebView.clearHistory()
            browserWebView.loadUrl("about:blank")
            browserWebView.visibility = View.GONE
            lastLoadedUrl = ""
            extractedPageText = ""
        }
    }

    fun requestConsentAndStartVpn(nodeId: String, protocol: String, dohUrl: String) {
        runOnUiThread {
            val serviceIntent = Intent(this, HubVpnService::class.java).apply {
                action = HubVpnService.ACTION_CONNECT
                putExtra(HubVpnService.EXTRA_NODE_ID, nodeId)
                putExtra(HubVpnService.EXTRA_PROTOCOL, protocol)
                putExtra(HubVpnService.EXTRA_DOH_PROVIDER, dohUrl)
            }

            val prefs = getSharedPreferences("browserhub_play_compliance", MODE_PRIVATE)
            val acceptedDisclosure = prefs.getBoolean("vpn_disclosure_accepted", false)

            val launchSystemPrepare = {
                val prepareIntent = VpnService.prepare(this)
                if (prepareIntent != null) {
                    pendingVpnIntent = serviceIntent
                    vpnPermissionLauncher.launch(prepareIntent)
                } else {
                    startService(serviceIntent)
                }
            }

            if (!acceptedDisclosure) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.vpn_disclosure_title)
                    .setMessage(R.string.vpn_disclosure_message)
                    .setPositiveButton("Agree & Continue") { _, _ ->
                        prefs.edit().putBoolean("vpn_disclosure_accepted", true).apply()
                        launchSystemPrepare()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                launchSystemPrepare()
            }
        }
    }

    fun setIncognitoScreenshotLock(locked: Boolean) {
        runOnUiThread {
            if (locked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    private fun escapeJs(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", " ")
}
