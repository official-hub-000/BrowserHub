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
    private var lastInjectedCss: String = ""
    private var lastInjectAtMs: Long = 0L
    private var pendingVpnIntent: Intent? = null

    @Volatile var darkReaderEnabled: Boolean = false
    @Volatile var cookieKillerEnabled: Boolean = true
    @Volatile var activeCustomCss: String = ""
    @Volatile private var trackerBlockEnabled: Boolean = true
    @Volatile private var antiFingerprintEnabled: Boolean = true
    @Volatile private var httpsUpgradeEnabled: Boolean = true
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

        if (antiFingerprintEnabled) {
            view.evaluateJavascript(
                "(function(){try{" +
                    "if(window.__bhFpOn){return;}window.__bhFpOn=true;" +
                    "var seed=0;for(var i=0;i<8;i++){seed=(seed*31+Math.floor(Math.random()*255))>>>0;}" +
                    "var rnd=function(){seed=(seed*1103515245+12345)>>>0;return seed/4294967296;};" +
                    "var origGet=HTMLCanvasElement.prototype.toDataURL;" +
                    "HTMLCanvasElement.prototype.toDataURL=function(){" +
                    "  try{var ctx=this.getContext('2d');" +
                    "    if(ctx){ctx.fillStyle='rgba('+Math.floor(rnd()*255)+','+Math.floor(rnd()*255)+','+Math.floor(rnd()*255)+',0.01)';" +
                    "      ctx.fillRect(0,0,this.width||1,this.height||1);}}catch(e){}" +
                    "  return origGet.apply(this,arguments);" +
                    "};" +
                    "if(navigator.hardwareConcurrency){try{Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 4+Math.floor(rnd()*4);}});}catch(e){}}" +
                    "if(navigator.deviceMemory){try{Object.defineProperty(navigator,'deviceMemory',{get:function(){return 2+Math.floor(rnd()*6);}});}catch(e){}}" +
                    "}catch(e){}})();",
                null
            )
        }

        if (httpsUpgradeEnabled) {
            view.evaluateJavascript(
                "(function(){try{" +
                    "if(location.protocol==='http:'&&!location.hostname.startsWith('localhost')" +
                    "&&!location.hostname.endsWith('.local')){" +
                    "  location.replace('https://'+location.host+location.pathname+location.search);" +
                    "}}catch(e){}})();",
                null
            )
        }
        if (css.isNotEmpty()) {
            val safeCss = css.toString().replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
            view.evaluateJavascript(
                "(function(){var el=document.getElementById('__bh_ext_css');if(!el){el=document.createElement('style');el.id='__bh_ext_css';document.head.appendChild(el);}el.textContent='$safeCss';})();",
                null
            )
        }
        // Clean DOM article extractor: drops nav/menus/ads/cookie walls so the AI and
        // Reader Mode receive real article text only. Search result pages return the
        // title only, so result listings are never mistaken for article content.
        val extractorJs = """
            (function(){
              try {
                var isSearch = /[?&](q|query|search|p)=/.test(location.search) &&
                  /google\.|bing\.com|duckduckgo\.com|search\.brave\.com|startpage\.com|ecosia\.org|yandex\.|yahoo\./.test(location.host);
                if (isSearch) { return document.title || 'Search results'; }
                var root = document.querySelector('article,main,[role=main],#rso,#links,.content,.post,.entry-content,.article-body') || document.body;
                if (!root) { return document.title || ''; }
                var clone = root.cloneNode(true);
                var junk = clone.querySelectorAll('nav,header,footer,aside,form,button,select,input,script,style,noscript,iframe,svg,time,[role=navigation],[role=menu],[role=banner],[role=search],[role=complementary],[aria-hidden=true],.nav,.menu,.sidebar,.footer,.header,.breadcrumb,.breadcrumbs,.cookie,.cookies,.consent,.ad,.ads,.advert,.advertisement,.promo,.share,.social,.related,.comments,.newsletter,.subscribe,.pagination');
                for (var i = 0; i < junk.length; i++) { if (junk[i].parentNode) { junk[i].parentNode.removeChild(junk[i]); } }
                var text = (clone.innerText || '').replace(/\s+/g, ' ').trim();
                return (document.title ? document.title + ' - ' : '') + text.substring(0, 9000);
              } catch (e) {
                return (document.title || '') + ' ' + ((document.body && document.body.innerText) || '').substring(0, 6000);
              }
            })()
        """.trimIndent()
        view.evaluateJavascript(extractorJs) { value ->
            if (!value.isNullOrBlank() && value != "null" && value.length > 3) {
                val unquoted = if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                    value.substring(1, value.length - 1)
                } else {
                    value
                }
                val cleaned = unquoted
                    .replace("\\n", "\n")
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                if (cleaned.trim().isNotEmpty()) {
                    extractedPageText = cleaned
                }
            }
        }
    }

    /**
     * Applies offline phrase translation to the real page DOM. The shell sends
     * the lexicon it already ships, so this stays zero-key and zero-network.
     * An empty language restores the pristine page.
     *
     * The injected script deliberately uses no backslash escapes (no RegExp
     * literals) so it survives Java/Kotlin string escaping unchanged.
     */
    fun applyPageTranslation(langCode: String, dictionaryJson: String) {
        runOnUiThread {
            if (browserWebView.visibility != View.VISIBLE) return@runOnUiThread
            if (langCode.isEmpty()) {
                browserWebView.evaluateJavascript(
                    "(function(){if(window.__bhTranslateOff){window.__bhTranslateOff();}})();",
                    null
                )
                return@runOnUiThread
            }
            val safeDict = dictionaryJson.ifEmpty { "{}" }
            val safeLang = langCode.replace("'", "")
            val js = "(function(){try{" +
                "var dict=" + safeDict + ";" +
                "if(window.__bhTranslateOff){window.__bhTranslateOff();}" +
                "var keys=[];for(var kk in dict){if(Object.prototype.hasOwnProperty.call(dict,kk)&&dict[kk]){keys.push(kk);}}" +
                "keys.sort(function(a,b){return b.length-a.length;});" +
                "var isWordChar=function(ch){return ch.length>0&&'abcdefghijklmnopqrstuvwxyz0123456789'.indexOf(ch)>=0;};" +
                "var convert=function(text){" +
                "  var lower=text.toLowerCase();var out='';var i=0;" +
                "  while(i<text.length){" +
                "    var hit=null;" +
                "    for(var k=0;k<keys.length;k++){" +
                "      var key=keys[k];" +
                "      if(lower.indexOf(key,i)===i){" +
                "        var before=i===0?' ':lower.charAt(i-1);" +
                "        var after=i+key.length>=lower.length?' ':lower.charAt(i+key.length);" +
                "        if(!isWordChar(before)&&!isWordChar(after)){hit=key;break;}" +
                "      }" +
                "    }" +
                "    if(hit){out+=dict[hit];i+=hit.length;}else{out+=text.charAt(i);i++;}" +
                "  }" +
                "  return out;" +
                "};" +
                "var changed=0;" +
                "var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);" +
                "var nodes=[];var n;" +
                "while((n=walker.nextNode())){" +
                "  var p=n.parentNode;if(!p)continue;" +
                "  var tag=p.nodeName;" +
                "  if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT'||tag==='TEXTAREA'||tag==='CODE'||tag==='PRE'){continue;}" +
                "  var t=n.nodeValue;if(!t||!t.trim()||t.length>800){continue;}" +
                "  nodes.push(n);" +
                "}" +
                "for(var i2=0;i2<nodes.length;i2++){" +
                "  var node=nodes[i2];var original=node.nodeValue;var next=convert(original);" +
                "  if(next!==original){node.__bhOrig=original;node.nodeValue=next;changed++;}" +
                "}" +
                "window.__bhTranslateOff=function(){" +
                "  var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);" +
                "  var m;while((m=w.nextNode())){if(m.__bhOrig!==undefined){m.nodeValue=m.__bhOrig;m.__bhOrig=undefined;}}" +
                "};" +
                "if(window.__onNativeTranslateResult){window.__onNativeTranslateResult('" + safeLang + "',changed);}" +
                "}catch(err){}})();"
            browserWebView.evaluateJavascript(js, null)
        }
    }

    private fun notifyShellFindResult(activeOrdinal: Int, totalMatches: Int) {
        runOnUiThread {
            shellWebView.evaluateJavascript(
                "if(window.__onNativeFindResult){window.__onNativeFindResult(" +
                    activeOrdinal + "," + totalMatches + ");}",
                null
            )
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
        visible: Boolean,
        trackerBlockOn: Boolean,
        antiFingerprintOn: Boolean,
        httpsUpgradeOn: Boolean
    ) {
        shieldEngine.adblockEnabled = adblockOn
        shieldEngine.trackerBlockEnabled = trackerBlockOn
        this.trackerBlockEnabled = trackerBlockOn
        this.antiFingerprintEnabled = antiFingerprintOn
        this.httpsUpgradeEnabled = httpsUpgradeOn
        this.darkReaderEnabled = darkReaderOn
        this.cookieKillerEnabled = cookieKillerOn
        this.activeCustomCss = customCss

        runOnUiThread {
            if (!visible || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                // A GONE-then-VISIBLE WebView can keep a cached touch target on
                // some OEM skins and swallow taps meant for the React shell.
                browserWebView.clearFocus()
                browserWebView.stopLoading()
                browserWebView.isFocusable = false
                browserWebView.isFocusableInTouchMode = false
                browserWebView.translationY =
                    browserWebView.height + 2f * resources.displayMetrics.density
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
                if (findQuery.isEmpty()) {
                    browserWebView.clearMatches()
                    notifyShellFindResult(0, 0)
                } else {
                    browserWebView.setFindListener { activeOrdinal, numberOfMatches, isDoneCounting ->
                        if (isDoneCounting) notifyShellFindResult(activeOrdinal, numberOfMatches)
                    }
                    browserWebView.findAllAsync(findQuery)
                }
            }

            browserWebView.isFocusable = true
            browserWebView.isFocusableInTouchMode = true
            browserWebView.translationY = 0f
            browserWebView.visibility = View.VISIBLE
            browserWebView.bringToFront()

            if (url != lastLoadedUrl) {
                lastLoadedUrl = url
                browserWebView.loadUrl(url)
            } else if (uaChanged) {
                browserWebView.reload()
            } else {
                val cssNow = activeCustomCss +
                    "|dr=" + darkReaderEnabled +
                    "|ck=" + cookieKillerEnabled +
                    "|fp=" + antiFingerprintEnabled +
                    "|https=" + httpsUpgradeEnabled
                val now = System.currentTimeMillis()
                if (cssNow != lastInjectedCss || now - lastInjectAtMs > 4000L) {
                    lastInjectedCss = cssNow
                    lastInjectAtMs = now
                    injectExtensionsAndExtractText(browserWebView)
                }
            }
        }
    }

    fun setBrowserVisibleOnly(visible: Boolean) {
        runOnUiThread {
            if (visible) {
                browserWebView.isFocusable = true
                browserWebView.isFocusableInTouchMode = true
                browserWebView.translationY = 0f
                browserWebView.visibility = View.VISIBLE
                browserWebView.bringToFront()
            } else {
                browserWebView.clearFocus()
                browserWebView.isFocusable = false
                browserWebView.isFocusableInTouchMode = false
                browserWebView.translationY =
                    browserWebView.height + 2f * resources.displayMetrics.density
                browserWebView.visibility = View.GONE
            }
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
