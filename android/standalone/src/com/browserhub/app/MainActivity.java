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

    private static final Set<String> AD_TRACKER_DOMAINS = new HashSet<String>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "connect.facebook.net",
            "taboola.com", "outbrain.com", "criteo.com", "adnxs.com",
            "rubiconproject.com", "pubmatic.com", "openx.net", "adsrvr.org",
            "scorecardresearch.com", "hotjar.com", "clarity.ms", "popads.net",
            "exoclick.com", "fingerprintjs.io", "moatads.com", "quantserve.com",
            "amazon-adsystem.com", "adcolony.com", "applovin.com", "unityads.unity3d.com"
    ));

    private FrameLayout rootLayout;
    private WebView shellWebView;
    private WebView browserWebView;
    private String mobileDefaultUa = "";

    private volatile boolean adblockEnabled = true;
    private volatile boolean darkReaderEnabled = false;
    private volatile boolean cookieKillerEnabled = true;
    private volatile String activeCustomCss = "";
    private volatile String extractedPageText = "";

    private String lastLoadedUrl = "";
    private boolean lastDesktopState = false;
    private int lastTopPx = -1;
    private int lastBottomPx = -1;
    private String lastFindQuery = "";
    private String pendingVpnNode = "ch-zurich";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window win = getWindow();
        if (win != null) {
            win.setStatusBarColor(Color.parseColor("#05070D"));
            win.setNavigationBarColor(Color.parseColor("#05070D"));
        }

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#05070D"));

        // 1. Shell WebView (React 19 BrowserHub Chrome)
        shellWebView = new WebView(this);
        shellWebView.setBackgroundColor(Color.parseColor("#05070D"));
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
                if (adblockEnabled && !request.isForMainFrame()) {
                    Uri u = request.getUrl();
                    String host = u != null && u.getHost() != null ? u.getHost().toLowerCase() : "";
                    if (isAdOrTrackerHost(host)) {
                        notifyShellAdBlocked(host);
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
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Throwable ignored) {
                }
                return true;
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
                    activeCustomCss, 100, "", true
            );
            notifyShellUrlChanged(dataUrl, dataUrl);
        }
    }

    private boolean isAdOrTrackerHost(String host) {
        if (host.isEmpty()) return false;
        for (String blocked : AD_TRACKER_DOMAINS) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
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

    private void notifyShellAdBlocked(final String host) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                shellWebView.evaluateJavascript(
                        "if(window.__onNativeAdBlocked){window.__onNativeAdBlocked('" + escapeJs(host) + "');}",
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
            final boolean visible
    ) {
        this.adblockEnabled = adblockOn;
        this.darkReaderEnabled = darkReaderOn;
        this.cookieKillerEnabled = cookieKillerOn;
        this.activeCustomCss = customCss != null ? customCss : "";

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!visible || url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    browserWebView.setVisibility(View.GONE);
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
                    } else {
                        browserWebView.findAllAsync(q);
                    }
                }

                browserWebView.setVisibility(View.VISIBLE);

                if (!url.equals(lastLoadedUrl)) {
                    lastLoadedUrl = url;
                    browserWebView.loadUrl(url);
                } else if (uaChanged) {
                    browserWebView.reload();
                } else {
                    injectExtensionsAndExtractText(browserWebView);
                }
            }
        });
    }

    public void setBrowserVisibleOnly(final boolean visible) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                browserWebView.setVisibility(visible ? View.VISIBLE : View.GONE);
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

    @Override
    public void onBackPressed() {
        if (browserWebView != null && browserWebView.getVisibility() == View.VISIBLE && browserWebView.canGoBack()) {
            browserWebView.goBack();
            return;
        }
        shellWebView.evaluateJavascript(
                "(function(){return window.__onAndroidBackPressed ? window.__onAndroidBackPressed() : false;})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!"true".equals(value)) {
                            MainActivity.super.onBackPressed();
                        }
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
