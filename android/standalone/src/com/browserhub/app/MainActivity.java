package com.browserhub.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.browserhub.app.vpn.HubVpnService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_VPN_PREPARE = 9101;
    private static final String VIRTUAL_HOST = "appassets.browserhub.local";

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";

    /** Advertising networks — gated by the "Direct Native Ad Blocker" toggle. */
    private static final Set<String> AD_DOMAINS = new HashSet<String>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "taboola.com", "outbrain.com", "criteo.com", "adnxs.com",
            "rubiconproject.com", "pubmatic.com", "openx.net", "adsrvr.org",
            "popads.net", "exoclick.com", "moatads.com",
            "amazon-adsystem.com", "adcolony.com", "applovin.com", "unityads.unity3d.com"
    ));

    /** Analytics / fingerprinting — gated by the separate tracker toggle, so a
     *  user who keeps ads visible can still block being measured. */
    private static final Set<String> TRACKER_DOMAINS = new HashSet<String>(Arrays.asList(
            "google-analytics.com", "googletagmanager.com", "connect.facebook.net",
            "scorecardresearch.com", "hotjar.com", "clarity.ms", "fingerprintjs.io",
            "quantserve.com"
    ));

    private FrameLayout rootLayout;
    private WebView shellWebView;
    private WebView browserWebView;
    private String mobileDefaultUa = "";

    private volatile boolean adblockEnabled = true;
    private volatile boolean darkReaderEnabled = false;
    private volatile boolean cookieKillerEnabled = true;
    private volatile String activeCustomCss = "";
    private volatile boolean trackerBlockEnabled = true;
    /** Script/CSS-injected shields that the shell toggles independently. */
    private volatile boolean antiFingerprintEnabled = true;
    private volatile boolean httpsUpgradeEnabled = true;
    /** Background Play extension: never pause or suspend the page's media. */
    private volatile boolean keepMediaAlive = false;
    private volatile boolean customCssActive = false;
    private volatile String extractedPageText = "";

    private String lastLoadedUrl = "";
    private boolean lastDesktopState = false;
    private int lastTopPx = -1;
    private int lastBottomPx = -1;
    private String lastFindQuery = "";
    private String lastInjectedCss = "";
    private long lastInjectAtMs = 0L;
    private String pendingVpnNode = "ch-zurich";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window win = getWindow();
        if (win != null) {
            win.setStatusBarColor(Color.parseColor("#000000"));
            win.setNavigationBarColor(Color.parseColor("#000000"));
            // Reserve space for the system bars. Without this the app is laid
            // out under the status bar and gesture area, which clips the top
            // row and pushes the bottom bar past the edge of the screen.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                win.setDecorFitsSystemWindows(true);
            } else {
                win.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                win.getDecorView().setSystemUiVisibility(0);
            }
        }

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#000000"));
        // Keep content clear of the status bar, navigation bar and notch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    android.graphics.Insets bars =
                            insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                }
            });
        }

        // 1. Shell WebView (React 19 BrowserHub Chrome)
        shellWebView = new WebView(this);
        shellWebView.setBackgroundColor(Color.parseColor("#000000"));
        WebSettings shellSettings = shellWebView.getSettings();
        shellSettings.setJavaScriptEnabled(true);
        shellSettings.setDomStorageEnabled(true);
        shellSettings.setDatabaseEnabled(true);
        shellSettings.setAllowFileAccess(false);
        shellSettings.setMediaPlaybackRequiresUserGesture(false);

        shellWebView.addJavascriptInterface(new BrowserHubBridge(this), "BrowserHubNative");
        shellWebView.setWebChromeClient(new WebChromeClient());
        shellWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri != null && VIRTUAL_HOST.equalsIgnoreCase(uri.getHost())) {
                    String path = uri.getPath();
                    if (path == null || path.isEmpty() || "/".equals(path)) {
                        path = "/index.html";
                    }
                    String assetPath = "public" + path;
                    try {
                        InputStream is = getAssets().open(assetPath);
                        String mime = guessMime(path);
                        String enc = (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) ? "UTF-8" : null;
                        Map<String, String> corsHeaders = new HashMap<String, String>();
                        corsHeaders.put("Access-Control-Allow-Origin", "*");
                        return new WebResourceResponse(mime, enc, 200, "OK", corsHeaders, is);
                    } catch (Throwable t) {
                        return new WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                new ByteArrayInputStream(new byte[0])
                        );
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleExternalViewIntent(getIntent());
            }
        });

        // 2. Native Real-Web Browser WebView
        browserWebView = new WebView(this);
        browserWebView.setVisibility(View.GONE);
        browserWebView.setClickable(true);
        WebSettings bwSettings = browserWebView.getSettings();
        mobileDefaultUa = bwSettings.getUserAgentString() + " BrowserHub/1.0";
        bwSettings.setUserAgentString(mobileDefaultUa);
        bwSettings.setJavaScriptEnabled(true);
        bwSettings.setDomStorageEnabled(true);
        bwSettings.setDatabaseEnabled(true);
        bwSettings.setUseWideViewPort(true);
        bwSettings.setLoadWithOverviewMode(true);
        bwSettings.setSupportZoom(true);
        bwSettings.setBuiltInZoomControls(true);
        bwSettings.setDisplayZoomControls(false);
        bwSettings.setMediaPlaybackRequiresUserGesture(false);
        bwSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(browserWebView, false);

        browserWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimetype);
                    req.addRequestHeader("User-Agent", userAgent);
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    req.setTitle(filename);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(req);
                        Toast.makeText(MainActivity.this, "BrowserHub Downloading: " + filename, Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable t) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Throwable ignored) {
                    }
                }
            }
        });

        browserWebView.setWebChromeClient(new WebChromeClient());
        browserWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ((adblockEnabled || trackerBlockEnabled) && !request.isForMainFrame()) {
                    Uri u = request.getUrl();
                    String host = u != null && u.getHost() != null ? u.getHost().toLowerCase() : "";
                    String list = matchBlockList(host);
                    if (list != null) {
                        notifyShellAdBlocked(host, list);
                        return new WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                new ByteArrayInputStream(new byte[0])
                        );
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                // Every non-web scheme goes to the OS. This covers intent://,
                // market://, tel:, mailto: and friends — all of which would
                // otherwise render as a name-not-resolved error.
                openExternalUrl(uri.toString());
                return true;
            }

            /**
             * A page can start a navigation without going through
             * shouldOverrideUrlLoading (for example `location.href = "intent://…"`).
             * When that happens the WebView has already begun resolving the
             * pseudo-scheme as a hostname and the user sees a DNS error. Catching
             * it here and restoring the previous page keeps that off the screen.
             */
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (url == null) return;
                if (url.startsWith("http://") || url.startsWith("https://")) return;
                view.stopLoading();
                openExternalUrl(url);
                notifyShellUrlChanged(url, "App link");
                if (lastLoadedUrl != null && isWebLoadableUrl(lastLoadedUrl)) {
                    view.loadUrl(lastLoadedUrl);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                lastLoadedUrl = url;
                String title = view.getTitle() != null ? view.getTitle() : url;
                notifyShellUrlChanged(url, title);
                injectExtensionsAndExtractText(view);
            }
        });

        rootLayout.addView(shellWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.addView(browserWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootLayout);
        shellWebView.loadUrl("https://" + VIRTUAL_HOST + "/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalViewIntent(intent);
    }

    private void handleExternalViewIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        final String dataUrl = intent.getDataString();
        if (dataUrl != null && (dataUrl.startsWith("http://") || dataUrl.startsWith("https://"))) {
            intent.setAction(null);
            syncNativeBrowserViewport(
                    dataUrl, 0f, 76f, 800f, false,
                    adblockEnabled, darkReaderEnabled, cookieKillerEnabled,
                    activeCustomCss, 100, "", true,
                    trackerBlockEnabled, antiFingerprintEnabled, httpsUpgradeEnabled,
                    keepMediaAlive, customCssActive
            );
            notifyShellUrlChanged(dataUrl, dataUrl);
        }
    }

    /** Emits a JS string literal for arbitrary text (custom CSS/JS payloads). */
    private String jsString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '<': out.append("\\u003c"); break;
                case '>': out.append("\\u003e"); break;
                default:
                    if (c < 0x20) out.append(" ");
                    else out.append(c);
            }
        }
        out.append("\"");
        return out.toString();
    }

    /** Returns the matched list name ("ads" / "trackers") or null when allowed. */
    private String matchBlockList(String host) {
        if (host == null || host.isEmpty()) return null;
        if (adblockEnabled) {
            for (String blocked : AD_DOMAINS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) return "ads";
            }
        }
        if (trackerBlockEnabled) {
            for (String blocked : TRACKER_DOMAINS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) return "trackers";
            }
        }
        return null;
    }

    private void injectExtensionsAndExtractText(WebView view) {
        StringBuilder css = new StringBuilder();
        if (darkReaderEnabled) {
            css.append("html { filter: invert(0.92) hue-rotate(180deg) !important; background: #111 !important; } ")
               .append("img, video, picture, canvas, iframe { filter: invert(1) hue-rotate(180deg) !important; } ");
        }
        if (cookieKillerEnabled) {
            css.append("#onetrust-consent-sdk, .fc-consent-root, .cc-window, [id*='cookie-banner'], [class*='cookie-consent'] { display: none !important; } ");
        }
        if (activeCustomCss != null && !activeCustomCss.isEmpty()) {
            css.append(activeCustomCss);
        }

        if (antiFingerprintEnabled) {
            view.evaluateJavascript(
                    "(function(){try{"
                            + "if(window.__bhFpOn){return;}window.__bhFpOn=true;"
                            + "var seed=0;for(var i=0;i<8;i++){seed=(seed*31+Math.floor(Math.random()*255))>>>0;}"
                            + "var rnd=function(){seed=(seed*1103515245+12345)>>>0;return seed/4294967296;};"
                            + "var origGet=HTMLCanvasElement.prototype.toDataURL;"
                            + "HTMLCanvasElement.prototype.toDataURL=function(){"
                            + "  try{var ctx=this.getContext('2d');"
                            + "    if(ctx){ctx.fillStyle='rgba('+Math.floor(rnd()*255)+','+Math.floor(rnd()*255)+','+Math.floor(rnd()*255)+',0.01)';"
                            + "      ctx.fillRect(0,0,this.width||1,this.height||1);}}catch(e){}"
                            + "  return origGet.apply(this,arguments);"
                            + "};"
                            + "if(navigator.hardwareConcurrency){try{Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 4+Math.floor(rnd()*4);}});}catch(e){}}"
                            + "if(navigator.deviceMemory){try{Object.defineProperty(navigator,'deviceMemory',{get:function(){return 2+Math.floor(rnd()*6);}});}catch(e){}}"
                            + "}catch(e){}})();",
                    null
            );
        }

        if (httpsUpgradeEnabled) {
            view.evaluateJavascript(
                    "(function(){try{"
                            + "if(location.protocol==='http:'&&!location.hostname.startsWith('localhost')"
                            + "&&!location.hostname.endsWith('.local')){"
                            + "  location.replace('https://'+location.host+location.pathname+location.search);"
                            + "}}catch(e){}})();",
                    null
            );
        }

        if (keepMediaAlive) {
            // Background Play extension: tell the page to keep media running and
            // never let the shell's visibility changes pause it.
            css.append("video, audio { visibility: visible !important; } ");
            view.evaluateJavascript(
                    "(function(){try{"
                            + "if(window.__bhBgPlayOn){return;}window.__bhBgPlayOn=true;"
                            + "if(navigator.mediaSession){try{navigator.mediaSession.setActionHandler('pause',null);}catch(e){}}"
                            + "document.addEventListener('visibilitychange',function(){"
                            + "  if(document.visibilityState==='hidden'){"
                            + "    var v=document.querySelectorAll('video,audio');"
                            + "    for(var i=0;i<v.length;i++){try{if(!v[i].paused){window.__bhBgResume=v[i];}}catch(e){}}"
                            + "  }else if(window.__bhBgResume){"
                            + "    try{if(window.__bhBgResume.paused){window.__bhBgResume.play();}}catch(e){}"
                            + "    window.__bhBgResume=null;"
                            + "  }"
                            + "});"
                            + "}catch(e){}})();",
                    null
            );
        }

        if (customCssActive && activeCustomCss != null && !activeCustomCss.isEmpty()) {
            view.evaluateJavascript(
                    "(function(){try{var el=document.getElementById('__bh_user_css');"
                            + "if(!el){el=document.createElement('style');el.id='__bh_user_css';document.head.appendChild(el);}"
                            + "el.textContent=" + jsString(activeCustomCss) + ";}catch(e){}})();",
                    null
            );
        }

        if (css.length() > 0) {
            String safeCss = css.toString()
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", " ");
            view.evaluateJavascript(
                    "(function(){var el=document.getElementById('__bh_ext_css');if(!el){el=document.createElement('style');el.id='__bh_ext_css';document.head.appendChild(el);}el.textContent='" + safeCss + "';})();",
                    null
            );
        }

        view.evaluateJavascript(
                "(function(){try{"
                        + "var isSearch=/[?&](q|query|search|p)=/.test(location.search)&&/google\\.|bing\\.com|duckduckgo\\.com|search\\.brave\\.com|startpage\\.com|ecosia\\.org|yandex\\./.test(location.host);"
                        + "if(isSearch){return (document.title||'Search results');}"
                        + "var root=document.querySelector('article,main,[role=main],#rso,#links,.content,.post,.entry-content')||document.body;"
                        + "if(!root){return document.title||'';}"
                        + "var clone=root.cloneNode(true);"
                        + "var junk=clone.querySelectorAll('nav,header,footer,aside,form,button,select,input,script,style,noscript,iframe,svg,[role=navigation],[role=menu],[role=banner],[role=search],[role=complementary],[aria-hidden=true],.nav,.menu,.sidebar,.footer,.header,.breadcrumb,.breadcrumbs,.cookie,.cookies,.consent,.ad,.ads,.advert,.advertisement,.promo,.share,.social,.related,.comments,.newsletter,.subscribe,.pagination');"
                        + "for(var i=0;i<junk.length;i++){if(junk[i].parentNode){junk[i].parentNode.removeChild(junk[i]);}}"
                        + "var text=(clone.innerText||'').replace(/\\u00a0/g,' ').replace(/[ \\t]+/g,' ').replace(/\\n{2,}/g,'\\n').trim();"
                        + "return (document.title?document.title+'\\n':'')+text.substring(0,9000);"
                        + "}catch(e){return (document.title||'')+' '+((document.body&&document.body.innerText)||'').substring(0,6000);}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String rawValue) {
                        if (rawValue == null || rawValue.length() < 3 || "null".equals(rawValue)) return;
                        String value = rawValue;
                        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        value = value
                                .replace("\\n", "\n")
                                .replace("\\/", "/")
                                .replace("\\\"", "\"");
                        if (value.trim().length() > 0) {
                            extractedPageText = value;
                        }
                    }
                }
        );
    }

    public String getExtractedPageText() {
        return extractedPageText;
    }

    /** Copies text via the Android clipboard service. */
    public void copyToClipboard(final String text) {
        if (text == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("BrowserHub", text));
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** Runs the user's enabled Userscript inside the page (re-run on change). */
    public void applyUserScripts(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (browserWebView == null || browserWebView.getVisibility() != View.VISIBLE) return;
                if (code == null || code.trim().isEmpty()) return;
                browserWebView.evaluateJavascript(
                        "try{\n" + code + "\n}catch(__bhUserScriptError){}",
                        null
                );
            }
        });
    }

    /**
     * Applies offline phrase translation to the real page DOM. The shell sends
     * the lexicon it already ships, so this stays zero-key and zero-network.
     * Passing an empty language restores the pristine page.
     *
     * The injected script deliberately uses no backslash escapes (no RegExp
     * literals) so it survives Java string escaping unchanged.
     */
    public void applyPageTranslation(final String langCode, final String dictionaryJson) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (browserWebView == null || browserWebView.getVisibility() != View.VISIBLE) return;
                if (langCode == null || langCode.isEmpty()) {
                    browserWebView.evaluateJavascript(
                            "(function(){if(window.__bhTranslateOff){window.__bhTranslateOff();}})();",
                            null
                    );
                    return;
                }
                String safeDict = dictionaryJson != null && !dictionaryJson.isEmpty()
                        ? dictionaryJson
                        : "{}";
                String safeLang = langCode.replace("'", "");
                String js = "(function(){try{"
                        + "var dict=" + safeDict + ";"
                        + "if(window.__bhTranslateOff){window.__bhTranslateOff();}"
                        + "var keys=[];for(var kk in dict){if(Object.prototype.hasOwnProperty.call(dict,kk)&&dict[kk]){keys.push(kk);}}"
                        + "keys.sort(function(a,b){return b.length-a.length;});"
                        + "var isWordChar=function(ch){return ch.length>0&&'abcdefghijklmnopqrstuvwxyz0123456789'.indexOf(ch)>=0;};"
                        + "var convert=function(text){"
                        + "  var lower=text.toLowerCase();var out='';var i=0;"
                        + "  while(i<text.length){"
                        + "    var hit=null;"
                        + "    for(var k=0;k<keys.length;k++){"
                        + "      var key=keys[k];"
                        + "      if(lower.indexOf(key,i)===i){"
                        + "        var before=i===0?' ':lower.charAt(i-1);"
                        + "        var after=i+key.length>=lower.length?' ':lower.charAt(i+key.length);"
                        + "        if(!isWordChar(before)&&!isWordChar(after)){hit=key;break;}"
                        + "      }"
                        + "    }"
                        + "    if(hit){out+=dict[hit];i+=hit.length;}else{out+=text.charAt(i);i++;}"
                        + "  }"
                        + "  return out;"
                        + "};"
                        + "var changed=0;"
                        + "var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);"
                        + "var nodes=[];var n;"
                        + "while((n=walker.nextNode())){"
                        + "  var p=n.parentNode;if(!p)continue;"
                        + "  var tag=p.nodeName;"
                        + "  if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT'||tag==='TEXTAREA'||tag==='CODE'||tag==='PRE'){continue;}"
                        + "  var t=n.nodeValue;if(!t||!t.trim()||t.length>800){continue;}"
                        + "  nodes.push(n);"
                        + "}"
                        + "for(var i2=0;i2<nodes.length;i2++){"
                        + "  var node=nodes[i2];var original=node.nodeValue;var next=convert(original);"
                        + "  if(next!==original){node.__bhOrig=original;node.nodeValue=next;changed++;}"
                        + "}"
                        + "window.__bhTranslateOff=function(){"
                        + "  var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);"
                        + "  var m;while((m=w.nextNode())){if(m.__bhOrig!==undefined){m.nodeValue=m.__bhOrig;m.__bhOrig=undefined;}}"
                        + "};"
                        + "if(window.__onNativeTranslateResult){window.__onNativeTranslateResult('" + safeLang + "',changed);}"
                        + "}catch(err){}})();";
                browserWebView.evaluateJavascript(js, null);
            }
        });
    }

    private void notifyShellFindResult(final int activeOrdinal, final int totalMatches) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                shellWebView.evaluateJavascript(
                        "if(window.__onNativeFindResult){window.__onNativeFindResult(" + activeOrdinal + "," + totalMatches + ");}",
                        null
                );
            }
        });
    }

    private void notifyShellUrlChanged(final String url, final String title) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String safeUrl = escapeJs(url);
                String safeTitle = escapeJs(title);
                shellWebView.evaluateJavascript(
                        "if(window.__onNativeUrlChanged){window.__onNativeUrlChanged('" + safeUrl + "','" + safeTitle + "');}",
                        null
                );
            }
        });
    }

    private void notifyShellAdBlocked(final String host, final String list) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                shellWebView.evaluateJavascript(
                        "if(window.__onNativeAdBlocked){window.__onNativeAdBlocked('" + escapeJs(host)
                                + "','" + list + "');}",
                        null
                );
            }
        });
    }

    public void syncNativeBrowserViewport(
            final String url,
            final float topCssPx,
            final float bottomCssPx,
            final float winHeightCss,
            final boolean isDesktop,
            final boolean adblockOn,
            final boolean darkReaderOn,
            final boolean cookieKillerOn,
            final String customCss,
            final int zoomPct,
            final String findQuery,
            final boolean visible,
            final boolean trackerBlockOn,
            final boolean antiFingerprintOn,
            final boolean httpsUpgradeOn,
            final boolean keepMediaAlive,
            final boolean customCssActive
    ) {
        this.adblockEnabled = adblockOn;
        this.trackerBlockEnabled = trackerBlockOn;
        this.antiFingerprintEnabled = antiFingerprintOn;
        this.httpsUpgradeEnabled = httpsUpgradeOn;
        this.keepMediaAlive = keepMediaAlive;
        this.customCssActive = customCssActive;
        this.darkReaderEnabled = darkReaderOn;
        this.cookieKillerEnabled = cookieKillerOn;
        this.activeCustomCss = customCss != null ? customCss : "";

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!visible || url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    // Hiding is not enough on some OEM skins (MIUI/ColorOS): a
                    // GONE-then-VISIBLE WebView can keep a cached touch target
                    // and swallow taps meant for the React shell. Drop focus,
                    // stop input, and detach it from the shell's hit testing.
                    browserWebView.clearFocus();
                    browserWebView.stopLoading();
                    browserWebView.setFocusable(false);
                    browserWebView.setFocusableInTouchMode(false);
                    browserWebView.setVisibility(View.GONE);
                    browserWebView.setTranslationY(browserWebView.getHeight() + 2 * (int) getResources().getDisplayMetrics().density);
                    return;
                }

                int totalH = rootLayout.getHeight();
                if (totalH <= 0) totalH = getResources().getDisplayMetrics().heightPixels;
                float safeWinH = winHeightCss > 10f ? winHeightCss : 800f;

                int topPx = Math.max(0, Math.round((topCssPx / safeWinH) * totalH));
                int bottomPx = Math.max(0, Math.round((bottomCssPx / safeWinH) * totalH));

                if (topPx != lastTopPx || bottomPx != lastBottomPx) {
                    lastTopPx = topPx;
                    lastBottomPx = bottomPx;
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    lp.setMargins(0, topPx, 0, bottomPx);
                    browserWebView.setLayoutParams(lp);
                }

                browserWebView.getSettings().setTextZoom(Math.max(50, Math.min(200, zoomPct)));

                boolean uaChanged = (isDesktop != lastDesktopState);
                if (uaChanged) {
                    lastDesktopState = isDesktop;
                    browserWebView.getSettings().setUserAgentString(isDesktop ? DESKTOP_UA : mobileDefaultUa);
                }

                String q = findQuery != null ? findQuery : "";
                if (!q.equals(lastFindQuery)) {
                    lastFindQuery = q;
                    if (q.isEmpty()) {
                        browserWebView.clearMatches();
                        notifyShellFindResult(0, 0);
                    } else {
                        browserWebView.setFindListener(new WebView.FindListener() {
                            @Override
                            public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
                                if (isDoneCounting) {
                                    notifyShellFindResult(activeMatchOrdinal, numberOfMatches);
                                }
                            }
                        });
                        browserWebView.findAllAsync(q);
                    }
                }

                browserWebView.setFocusable(true);
                browserWebView.setFocusableInTouchMode(true);
                browserWebView.setTranslationY(0f);
                browserWebView.setVisibility(View.VISIBLE);
                if (browserWebView.getParent() != null) {
                    browserWebView.bringToFront();
                }

                if (!url.equals(lastLoadedUrl)) {
                    /*
                     * Defence in depth for the reported net::ERR_NAME_NOT_RESOLVED
                     * screen. The shell now classifies URLs before calling this,
                     * but a deep link must NEVER reach loadUrl even if that check
                     * is bypassed: a WebView resolves "intent" as a hostname.
                     */
                    if (!isWebLoadableUrl(url)) {
                        lastLoadedUrl = url;
                        openExternalUrl(url);
                        notifyShellUrlChanged(url, "App link");
                        return;
                    }
                    lastLoadedUrl = url;
                    browserWebView.loadUrl(url);
                } else if (uaChanged) {
                    browserWebView.reload();
                } else {
                    // The shell re-syncs on every layout pass, so only re-inject
                    // when the injected payload actually changed (or every 4s at
                    // most) instead of hammering evaluateJavascript each frame.
                    String cssNow = activeCustomCss
                            + "|dr=" + darkReaderEnabled
                            + "|ck=" + cookieKillerEnabled
                            + "|fp=" + antiFingerprintEnabled
                            + "|https=" + httpsUpgradeEnabled;
                    long now = System.currentTimeMillis();
                    if (!cssNow.equals(lastInjectedCss) || now - lastInjectAtMs > 4000L) {
                        lastInjectedCss = cssNow;
                        lastInjectAtMs = now;
                        injectExtensionsAndExtractText(browserWebView);
                    }
                }
            }
        });
    }

    /**
     * Maps a UI intent to the platform's own haptic constants. Uses
     * performHapticFeedback so the device's system haptics setting is respected —
     * a user who turned vibration off never feels this.
     */
    public void performHaptic(final int strength) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int constant;
                    if (strength <= 0) {
                        constant = android.view.HapticFeedbackConstants.CLOCK_TICK;
                    } else if (strength == 1) {
                        constant = android.view.HapticFeedbackConstants.KEYBOARD_TAP;
                    } else {
                        constant = android.view.HapticFeedbackConstants.CONFIRM;
                    }
                    findFocusOrRoot().performHapticFeedback(constant);
                } catch (Throwable ignored) {
                    // Haptics are a nicety; never let one break a tap.
                }
            }
        });
    }

    private android.view.View findFocusOrRoot() {
        android.view.View focused = getCurrentFocus();
        if (focused != null) return focused;
        return rootLayout != null ? rootLayout : new android.view.View(this);
    }

    /** True only for URLs a WebView can render: http(s) with a real host. */
    private static boolean isWebLoadableUrl(final String url) {
        if (url == null) return false;
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false;
        try {
            Uri parsed = Uri.parse(u);
            String host = parsed.getHost();
            // "https://intent//play.google.com/..." parses with host "intent",
            // which has no dot and cannot resolve. Reject it here.
            return host != null && host.contains(".");
        } catch (Throwable t) {
            return false;
        }
    }

    /** True only for web URLs; everything else is handed to the OS. */
    public boolean openExternalUrl(final String url) {
        if (url == null || url.trim().isEmpty()) return false;
        final String target = url.trim();
        if (target.startsWith("http://") || target.startsWith("https://")) return false;
        final boolean[] handled = new boolean[] { false };
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent;
                    if (target.startsWith("intent:")) {
                        intent = Intent.parseUri(target, Intent.URI_INTENT_SCHEME);
                    } else {
                        intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                        handled[0] = true;
                        return;
                    }
                    // An intent:// link often carries a browser fallback.
                    String fallback = intent.getStringExtra("browser_fallback_url");
                    if (fallback != null && !fallback.isEmpty()) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        handled[0] = true;
                    }
                } catch (Throwable ignored) {
                    // Nothing on this device understands the scheme.
                }
            }
        });
        return handled[0];
    }

    public void setBrowserVisibleOnly(final boolean visible) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (visible) {
                    browserWebView.setFocusable(true);
                    browserWebView.setFocusableInTouchMode(true);
                    browserWebView.setTranslationY(0f);
                    browserWebView.setVisibility(View.VISIBLE);
                } else {
                    browserWebView.clearFocus();
                    browserWebView.setFocusable(false);
                    browserWebView.setFocusableInTouchMode(false);
                    browserWebView.setTranslationY(browserWebView.getHeight() + 2 * (int) getResources().getDisplayMetrics().density);
                    browserWebView.setVisibility(View.GONE);
                }
            }
        });
    }

    public boolean goBackBrowser() {
        final boolean[] handled = {false};
        if (browserWebView != null && browserWebView.getVisibility() == View.VISIBLE && browserWebView.canGoBack()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (browserWebView.canGoBack()) browserWebView.goBack();
                }
            });
            handled[0] = true;
        }
        return handled[0];
    }

    public boolean goForwardBrowser() {
        if (browserWebView != null && browserWebView.getVisibility() == View.VISIBLE && browserWebView.canGoForward()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (browserWebView.canGoForward()) browserWebView.goForward();
                }
            });
            return true;
        }
        return false;
    }

    public void reloadBrowser() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (browserWebView.getVisibility() == View.VISIBLE) {
                    browserWebView.reload();
                }
            }
        });
    }

    public void handleVpnToggle(final boolean enabled, final String nodeId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!enabled) {
                    Intent stop = new Intent(MainActivity.this, HubVpnService.class);
                    stop.setAction(HubVpnService.ACTION_DISCONNECT);
                    startService(stop);
                    return;
                }
                pendingVpnNode = nodeId != null ? nodeId : "ch-zurich";
                final SharedPreferences prefs = getSharedPreferences("bh_vpn_prefs", MODE_PRIVATE);
                if (!prefs.getBoolean("vpn_consent_shown", false)) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("BrowserHub Privacy VPN Disclosure")
                            .setMessage("BrowserHub uses Android VpnService exclusively when you turn on the built-in VPN to create a local zero-log privacy tunnel. Zero browsing history, DNS queries, or IP addresses are logged or shared.")
                            .setPositiveButton("Agree & Connect", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    prefs.edit().putBoolean("vpn_consent_shown", true).apply();
                                    prepareAndLaunchVpn();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    prepareAndLaunchVpn();
                }
            }
        });
    }

    private void prepareAndLaunchVpn() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN_PREPARE);
        } else {
            startVpnServiceNow();
        }
    }

    private void startVpnServiceNow() {
        Intent svc = new Intent(this, HubVpnService.class);
        svc.setAction(HubVpnService.ACTION_CONNECT);
        svc.putExtra(HubVpnService.EXTRA_NODE_ID, pendingVpnNode);
        startService(svc);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN_PREPARE && resultCode == RESULT_OK) {
            startVpnServiceNow();
        }
    }

    public void setIncognitoScreenshotLock(final boolean locked) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Window w = getWindow();
                if (w == null) return;
                if (locked) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                } else {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            }
        });
    }

    public void shredAllBrowsingData() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CookieManager cm = CookieManager.getInstance();
                cm.removeAllCookies(null);
                cm.flush();
                WebStorage.getInstance().deleteAllData();
                browserWebView.stopLoading();
                browserWebView.clearCache(true);
                browserWebView.clearHistory();
                browserWebView.loadUrl("about:blank");
                browserWebView.setVisibility(View.GONE);
                lastLoadedUrl = "";
                extractedPageText = "";
                Toast.makeText(MainActivity.this, "🔥 Incinerated cookies, cache & history!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Hardware/gesture Back.
     *
     * Order matters: an open sheet or modal is dismissed first, then browser
     * history, then the in-app tab history, and only then does the app exit.
     *
     * The previous version depended entirely on a JS callback answering "true",
     * so if that handler was ever missing the platform exited the app on the
     * first press even with a modal open. The native layers are now handled
     * here as well, and the JS answer is a signal rather than the only path.
     */
    @Override
    public void onBackPressed() {
        // 1. A page with its own history goes back on the real web view.
        if (browserWebView != null
                && browserWebView.getVisibility() == View.VISIBLE
                && browserWebView.canGoBack()) {
            browserWebView.goBack();
            return;
        }
        // 2. Otherwise let the React shell decide (modals, tab history, home).
        //
        // The shell installs its handler once at mount, but a Back press can
        // arrive before that finishes on a cold start. Ask once; if the callback
        // is not ready, ask again shortly instead of exiting under the user.
        dispatchBackToShell(0);
    }

    /**
     * Asks the React shell to handle Back, retrying briefly when the handler is
     * not installed yet.
     *
     * `attempt` 0 runs immediately; later attempts are delayed ~120 ms. Three
     * tries covers a cold start without ever making Back feel sluggish.
     */
    private void dispatchBackToShell(final int attempt) {
        if (shellWebView == null) {
            moveTaskToBack(true);
            return;
        }
        shellWebView.evaluateJavascript(
                "(function(){try{return window.__onAndroidBackPressed ? !!window.__onAndroidBackPressed() : 'nohandler';}catch(e){return 'error';}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        String result = value == null ? "" : value.trim();
                        boolean handled = result.contains("true");
                        if (handled) return;

                        boolean notReady = result.contains("nohandler") || result.contains("error");
                        if (notReady && attempt < 3) {
                            shellWebView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    dispatchBackToShell(attempt + 1);
                                }
                            }, 120L);
                            return;
                        }

                        // The shell genuinely has nothing to go back to.
                        if (shellWebView.getVisibility() == View.VISIBLE
                                && shellWebView.canGoBack()) {
                            shellWebView.goBack();
                            return;
                        }
                        moveTaskToBack(true);
                    }
                }
        );
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", " ");
    }

    private static String guessMime(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json") || lower.endsWith(".webmanifest")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
