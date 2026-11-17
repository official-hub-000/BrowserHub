#!/usr/bin/env python3
"""
Re-applies every round-6 edit to the tracked sources.

The workspace was re-cloned from the base commit between sessions, which threw
away tracked modifications while untracked files survived. Rather than replay
them one at a time and risk a half-applied tree, this script is the single
source of truth for the round-6 changes: it asserts each anchor exists, applies
the edit, and reports every anchor it could not find so nothing fails silently.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
applied, missing = [], []


def patch(rel, old, new, label):
    path = ROOT / rel
    text = path.read_text()
    if new in text:
        applied.append(f"{label} (already present)")
        return
    if old not in text:
        missing.append(f"{rel}: {label}")
        return
    path.write_text(text.replace(old, new, 1))
    applied.append(label)


# ---------------------------------------------------------------- App.tsx ----
patch(
    "src/App.tsx",
    "import { AndroidHybridBridge } from './bridge/androidHybridBridge';",
    "import { AndroidHybridBridge } from './bridge/androidHybridBridge';\n"
    "import { classifyUrl } from './utils/urlSafety';",
    "App: import classifyUrl",
)

patch(
    "src/App.tsx",
    "  Crosshair,",
    "  Crosshair,\n  Info,",
    "App: import Info icon",
)

patch(
    "src/App.tsx",
    "  const [showPlayStoreModal, setShowPlayStoreModal] = useState(false);",
    """  const [showAboutModal, setShowAboutModal] = useState(false);
  /**
   * One-line notice for navigation outcomes the user could not otherwise see —
   * a deep link translated to its web version, or one this browser genuinely
   * cannot open. Silence there is what made a DNS error page look like a
   * broken app.
   */
  const [navNotice, setNavNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!navNotice) return undefined;
    const timer = window.setTimeout(() => setNavNotice(null), 7000);
    return () => window.clearTimeout(timer);
  }, [navNotice]);""",
    "App: about/navNotice state",
)

patch(
    "src/App.tsx",
    """    const isProtocol =
      /^https?:\\/\\//i.test(targetUrl) || targetUrl.startsWith('browserhub://');
    const looksLikeDomain =
      !targetUrl.includes(' ') && /\\.[a-z]{2,}(\\/|$)/i.test(targetUrl);

    if (!isProtocol) {
      if (looksLikeDomain) {
        targetUrl = `https://${targetUrl}`;
      } else {
        targetUrl = activeSearchEngine.buildUrl(targetUrl);
      }
    }
""",
    """    /*
     * BUG THIS FIXES (screenshot from a real device):
     *   "The web page at https://intent//play.google.com/... could not be loaded
     *    because: net::ERR_NAME_NOT_RESOLVED"
     * An `intent://` deep link was passed straight to the WebView, which tried
     * to resolve "intent" as a hostname. classifyUrl() now decides what the
     * input actually is: a web url, an internal page, an app deep link with a
     * web fallback, or something a browser genuinely cannot open.
     */
    const classified = classifyUrl(targetUrl);

    if (classified.kind === 'unsafe') {
      setNavNotice(classified.reason);
      return;
    }

    if (classified.kind === 'app-scheme') {
      if (classified.webUrl) {
        // The link asked for another app but contains a real web address —
        // follow the web address rather than failing with a DNS error.
        setNavNotice(classified.note);
        targetUrl = classified.webUrl;
      } else {
        // Nothing web to fall back to. Hand it to Android, and if that is not
        // possible say so instead of showing an unresolvable page.
        const handedOff = AndroidHybridBridge.openExternalUrl?.(classified.url) ?? false;
        setNavNotice(
          handedOff ? `${classified.note} Handed it to Android.` : classified.note
        );
        return;
      }
    } else if (classified.kind === 'search') {
      targetUrl = activeSearchEngine.buildUrl(classified.query);
    } else {
      targetUrl = classified.url;
    }
""",
    "App: route navigation through classifyUrl",
)

patch(
    "src/App.tsx",
    "import { PlayStoreDownloadModal } from './components/PlayStoreDownloadModal';",
    "import { AboutDiagnosticsModal } from './components/AboutDiagnosticsModal';",
    "App: swap modal import",
)

patch("src/App.tsx", "showPlayStoreModal;", "showAboutModal;", "App: anyModalOpen")

patch(
    "src/App.tsx",
    "        setShowPlayStoreModal(false);",
    "        setShowAboutModal(false);",
    "App: back handler closes about",
)

patch(
    "src/App.tsx",
    "onOpenPlayStore={() => setShowPlayStoreModal(true)}",
    "onOpenAbout={() => setShowAboutModal(true)}",
    "App: pass onOpenAbout",
)

patch(
    "src/App.tsx",
    """      {showPlayStoreModal && (
        <PlayStoreDownloadModal
          deferredInstallPrompt={deferredInstallPrompt}
          onClose={() => setShowPlayStoreModal(false)}
        />
      )}""",
    """      {showAboutModal && <AboutDiagnosticsModal onClose={() => setShowAboutModal(false)} />}""",
    "App: render About modal",
)

# The start page no longer receives onOpenAbout; WebViewport has no such prop.
patch(
    "src/App.tsx",
    "            onOpenAbout={() => setShowAboutModal(true)}\n",
    "",
    "App: drop stale WebViewport prop",
)

# Navigation notice UI.
patch(
    "src/App.tsx",
    """    </div>
  );
};

export default App;""",
    """      {/*
        Navigation notice. Above the dock, below dialogs, and it never blocks the
        page underneath except for its own dismiss button.
      */}
      {navNotice && (
        <div
          role="status"
          aria-live="polite"
          className="absolute left-2 right-2 z-30 flex items-start gap-2 rounded-xl border border-cyan-500/40 px-3 py-2 shadow-lg"
          style={{ bottom: 'calc(env(safe-area-inset-bottom, 0px) + 74px)', backgroundColor: '#050506' }}
        >
          <Info size={13} className="text-cyan-300 mt-0.5 shrink-0" />
          <p className="flex-1 text-[10px] leading-relaxed text-slate-200">{navNotice}</p>
          <button
            type="button"
            onClick={() => setNavNotice(null)}
            aria-label="Dismiss this notice"
            title="Dismiss"
            className="p-0.5 rounded text-slate-400 cursor-pointer shrink-0"
          >
            <X size={12} />
          </button>
        </div>
      )}
    </div>
  );
};

export default App;""",
    "App: navNotice UI",
)

# ------------------------------------------------------------- MenuHub -------
patch(
    "src/components/MenuHubModal.tsx",
    "  onOpenPlayStore: () => void;",
    """  /**
   * The old "APK & Play Store" panel is gone. Every button in it pointed at
   * `/api/download/...`, a dev-server route that does not exist in a static
   * build, so tapping it inside the installed APK could only fail. Replaced
   * with a real diagnostics panel.
   */
  onOpenAbout: () => void;""",
    "Menu: prop type",
)
patch("src/components/MenuHubModal.tsx", "  onOpenPlayStore,", "  onOpenAbout,", "Menu: destructure")
patch("src/components/MenuHubModal.tsx", "  Zap,\n  Gamepad2,", "  Zap,\n  Gamepad2,\n  Info,", "Menu: Info icon")
patch(
    "src/components/MenuHubModal.tsx",
    """              <button
                onClick={() => runAndClose(onOpenPlayStore)}
                className="p-3 rounded-xl bg-emerald-950/40 hover:bg-emerald-950/70 border border-emerald-500/40 text-left flex items-center gap-2.5 text-xs font-semibold text-emerald-300 cursor-pointer"
              >
                <Download size={16} className="text-emerald-400" />
                <span>APK &amp; Play Store</span>
              </button>""",
    """              <button
                onClick={() => runAndClose(onOpenAbout)}
                className="p-3 rounded-xl bg-black hover:bg-[#121216] border border-slate-800 text-left flex items-center gap-2.5 text-xs font-semibold text-slate-200 cursor-pointer"
              >
                <Info size={16} className="text-cyan-400" />
                <span>About &amp; Diagnostics</span>
              </button>""",
    "Menu: swap dead entry",
)

# ------------------------------------------------------------ WebViewport ----
patch(
    "src/components/WebViewport.tsx",
    "  onOpenPlayStore?: () => void;\n",
    "",
    "Viewport: drop prop type",
)
patch("src/components/WebViewport.tsx", "  onOpenPlayStore,\n", "", "Viewport: drop destructure")

# --------------------------------------------------------- extension catalog -
patch(
    "src/data/extensionCatalogue.ts",
    """    support: 'runs-here',
    works: 'Your own CSS from the Studio tab is injected into pages and applied immediately.',
    implementation: 'Author CSS injected into every document.',""",
    """    support: 'runs-here',
    works: 'Ships with a working dark stylesheet injected into every page; edit it in Studio.',
    implementation: 'Author CSS injected into every document.',
    /*
     * BUG THIS FIXES (reported as "extension not working, I can't add any"):
     * hasCustomCss() requires a non-empty `customCss`, so installing this entry
     * with no stylesheet installed an extension that provably did nothing.
     */
    customCss: `/* BrowserHub Stylus — default dark polish. Edit in the Studio tab. */
html { color-scheme: dark !important; }
body { background-color: #0b0b0e !important; }
::-webkit-scrollbar { width: 8px; height: 8px; }
::-webkit-scrollbar-track { background: #08080b; }
::-webkit-scrollbar-thumb { background: #22d3ee55; border-radius: 8px; }
::selection { background: #22d3ee44 !important; }
img, video { border-radius: 6px; }`,""",
    "catalogue: Stylus ships CSS",
)

patch(
    "src/data/extensionCatalogue.ts",
    """    support: 'runs-here',
    works: 'Your own script text is injected and executed in the page. Third-party GM_* APIs are not available.',
    implementation: 'Author script injected into the document.',""",
    """    support: 'runs-here',
    works: 'Runs a real userscript in every page. Ships with a small one you can replace in Studio.',
    implementation: 'Author script injected into the document.',
    // Same bug as Stylus above: hasUserScript() needs non-empty `customJs`.
    customJs: `/* BrowserHub userscript example — replace it in the Studio tab.
   GM_* helper APIs are not available; plain DOM code works. */
(function () {
  try {
    document.documentElement.setAttribute('data-browserhub-userscript', 'on');
    document.querySelectorAll('a').forEach(function (a) {
      a.style.touchAction = 'manipulation';
    });
  } catch (e) {
    /* a page without a document is not an error worth surfacing */
  }
})();`,""",
    "catalogue: userscript ships code",
)

# ---------------------------------------------------------- extension bridge -
patch(
    "src/bridge/androidHybridBridge.ts",
    """  /** Returns the JSON result envelope once ready, or null while pending. */
  pollAiResult?: (requestId: string) => string | null;
}""",
    """  /** Returns the JSON result envelope once ready, or null while pending. */
  pollAiResult?: (requestId: string) => string | null;
  /**
   * Hands a non-web URI (tel:, mailto:, market:, intent:) to the Android OS.
   * Returns false when nothing on the device can handle it.
   */
  openExternalUrl?: (url: string) => boolean;
}""",
    "bridge: openExternalUrl type",
)

patch(
    "src/bridge/androidHybridBridge.ts",
    """export const AndroidHybridBridge = {
  hasJniBridge(): boolean {""",
    """export const AndroidHybridBridge = {
  /**
   * Sends a deep link to Android. A WebView cannot render `tel:` or `market:`,
   * and attempting to load one produces a DNS error page, so those have to be
   * handed to the OS explicitly.
   */
  openExternalUrl(url: string): boolean {
    if (!url) return false;
    if (this.hasJniBridge() && window.BrowserHubNative?.openExternalUrl) {
      try {
        return Boolean(window.BrowserHubNative.openExternalUrl(url));
      } catch {
        return false;
      }
    }
    return false;
  },

  hasJniBridge(): boolean {""",
    "bridge: openExternalUrl impl",
)

# ------------------------------------------------------------- Java bridge ---
patch(
    "android/standalone/src/com/browserhub/app/BrowserHubBridge.java",
    "    /** Reliable clipboard write for Android WebView (execCommand often fails). */",
    """    /**
     * Hands a deep link (tel:, mailto:, market:, intent:) to the OS.
     *
     * A WebView cannot render these — attempting a load produces
     * net::ERR_NAME_NOT_RESOLVED, which is exactly the error screen reported.
     * Returns false when nothing on the device can handle the URI.
     */
    @JavascriptInterface
    public boolean openExternalUrl(String url) {
        return activity.openExternalUrl(url);
    }

    /** Reliable clipboard write for Android WebView (execCommand often fails). */""",
    "java bridge: openExternalUrl",
)

# -------------------------------------------------------------- MainActivity -
patch(
    "android/standalone/src/com/browserhub/app/MainActivity.java",
    """                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Throwable ignored) {
                }
                return true;""",
    """                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                // Every non-web scheme goes to the OS. This covers intent://,
                // market://, tel:, mailto: and friends — all of which would
                // otherwise render as a name-not-resolved error.
                openExternalUrl(uri.toString());
                return true;""",
    "java: override hands off",
)

patch(
    "android/standalone/src/com/browserhub/app/MainActivity.java",
    """            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                lastLoadedUrl = url;""",
    """            /**
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
                lastLoadedUrl = url;""",
    "java: onPageStarted guard",
)

patch(
    "android/standalone/src/com/browserhub/app/MainActivity.java",
    """                if (!url.equals(lastLoadedUrl)) {
                    lastLoadedUrl = url;
                    browserWebView.loadUrl(url);
                } else if (uaChanged) {""",
    """                if (!url.equals(lastLoadedUrl)) {
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
                } else if (uaChanged) {""",
    "java: loadUrl guard",
)

patch(
    "android/standalone/src/com/browserhub/app/MainActivity.java",
    "    public void setBrowserVisibleOnly(final boolean visible) {",
    """    /** True only for URLs a WebView can render: http(s) with a real host. */
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

    public void setBrowserVisibleOnly(final boolean visible) {""",
    "java: helpers",
)

# --------------------------------------------------------------- unpacker ----
patch(
    "src/utils/extensionUnpacker.ts",
    """    description: `Imported globally from ${
      isFirefox ? 'Firefox Add-ons (AMO)' : isUserscript ? 'Userscript Registry' : 'Chrome Web Store'
    } via BrowserHub Universal WebExtension Polyfill Bridge.`,
    icon: isFirefox ? '🦊' : isUserscript ? '⚡' : '🌐',
    enabled: true,
    allowIncognito: true,
    permissions: ['activeTab', 'storage', 'webRequest', 'scripting'],
  };
}""",
    """    /*
     * BUG THIS FIXES ("extension not working, I can't add any of them"):
     * every import produced `effectKey` undefined, so the entry appeared in the
     * Installed list while changing nothing — a label, not an extension. A
     * userscript can genuinely run here, so it is wired to custom-script and
     * ships real code. A .crx/.xpi cannot execute in WebView and says so.
     */
    description: isUserscript
      ? 'Imported userscript. Edit its code in the Studio tab — plain DOM scripts run for real in every page.'
      : `Imported ${isFirefox ? 'Firefox .xpi' : 'Chrome .crx'} package record from ${
          isFirefox ? 'Firefox Add-ons (AMO)' : 'Chrome Web Store'
        }. Android WebView has no extension engine, so its code cannot execute here — see the Working tab for native equivalents.`,
    // Vector icon ids only: the app bans emoji in the UI.
    icon: isFirefox ? 'firefox' : isUserscript ? 'code' : 'globe',
    enabled: true,
    allowIncognito: true,
    permissions: ['activeTab', 'storage', 'webRequest', 'scripting'],
    ...(isUserscript
      ? {
          effectKey: 'custom-script' as const,
          customJs: `/* Imported from ${trimmed}\\n   Replace the body with the userscript you want to run.\\n   GM_* helpers are not available; plain DOM code works. */\\n(function () {\\n  try {\\n    console.info('[BrowserHub] userscript active on', location.host);\\n  } catch (e) {}\\n})();`,
        }
      : {}),
  };
}""",
    "unpacker: real imports",
)

patch("src/utils/extensionUnpacker.ts", "        icon: format === 'XPI' ? '🦊' : '🧩',", "        icon: format === 'XPI' ? 'firefox' : 'puzzle',", "unpacker: icons 1")
patch("src/utils/extensionUnpacker.ts", "    icon: format === 'XPI' ? '🦊' : '🧩',", "    icon: format === 'XPI' ? 'firefox' : 'puzzle',", "unpacker: icons 2")
patch(
    "src/utils/extensionUnpacker.ts",
    "    description: `Sideloaded ${format} extension (${(file.size / 1024).toFixed(1)} KB) registered in Universal Extension Runtime.`,",
    "    description: `Sideloaded ${format} package (${(file.size / 1024).toFixed(1)} KB). No readable CSS or script was found inside it, so it cannot change a page here.`,",
    "unpacker: honest fallback",
)

# ---------------------------------------------------------------- VectorIcons
patch(
    "src/components/VectorIcons.tsx",
    """    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#334155" />
          <circle cx="12" cy="12" r="4.4" fill="none" stroke="#cbd5e1" strokeWidth="1.6" />
        </svg>
      );
  }
};""",
    """    /* Vector marks for imported extension sources. These ids are referenced by
       extensionUnpacker; without them an import would render a blank disc. */
    case 'firefox':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#121216" />
          <path d="M12 3.2c3.4 0 5 2.1 5 4.3 0 1.7-1.1 2.6-1.1 3.7 0 1 .8 1.4.8 2.5 0 1.7-1.7 2.4-2.9 2.4-1.4 0-2.1-.8-2.8-.8-.6 0-1.1.6-2.2.6-2.2 0-3.6-1.8-3.6-4.2 0-3.6 2.8-6.1 6.8-6.1Z" fill="#f97316" />
          <circle cx="12" cy="12" r="3" fill="#fed7aa" />
        </svg>
      );
    case 'puzzle':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#121216" />
          <path d="M9 8.5h1.8a1.2 1.2 0 1 1 2.4 0H15v1.9a1.2 1.2 0 1 1 0 2.4v1.7h-1.8a1.2 1.2 0 1 0-2.4 0H9v-1.7a1.2 1.2 0 1 0 0-2.4V8.5Z" fill="#a78bfa" />
        </svg>
      );
    case 'globe':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#0e7490" />
          <circle cx="12" cy="12" r="6.4" fill="none" stroke="#cffafe" strokeWidth="1.5" />
          <path d="M5.6 12h12.8M12 5.6c2 1.8 2 10.6 0 12.8M12 5.6c-2 1.8-2 10.6 0 12.8" stroke="#cffafe" strokeWidth="1.2" fill="none" />
        </svg>
      );
    case 'code':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#121216" />
          <path d="M9.6 8.6 6.2 12l3.4 3.4M14.4 8.6 17.8 12l-3.4 3.4" stroke="#22d3ee" strokeWidth="1.7" strokeLinecap="round" fill="none" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="11.2" fill="#334155" />
          <circle cx="12" cy="12" r="4.4" fill="none" stroke="#cbd5e1" strokeWidth="1.6" />
        </svg>
      );
  }
};""",
    "icons: firefox/puzzle/globe/code",
)

# -------------------------------------------------------------- ExtensionsModal
patch(
    "src/components/ExtensionsModal.tsx",
    """    const ext = parseGlobalStoreUrl(input);
    onInstallExtension(ext);
    setStoreUrlInput('');
    flashNotice(`Imported "${ext.name}" (${ext.format})`);
    setActiveTab('installed');""",
    """    const ext = parseGlobalStoreUrl(input);

    /*
     * A .crx/.xpi record cannot execute in Android WebView. Installing it put a
     * card in the list that changed nothing — exactly "I can't add any of them /
     * they don't work". A userscript is different: its code genuinely runs, so
     * only that case is added outright.
     */
    if (ext.effectKey === 'custom-script') {
      onInstallExtension(ext);
      setStoreUrlInput('');
      flashNotice(`Added "${ext.name}" — edit its code in the Studio tab`);
      setActiveTab('installed');
      return;
    }

    setStoreUrlInput('');
    flashNotice(
      `${ext.name} was read as a ${ext.format} package. Android WebView cannot run its code, so it was not added. ` +
        `See the Working tab for native equivalents, or paste a userscript.`
    );""",
    "modal: honest import",
)

# ---------------------------------------------------------------- checks -----
patch(
    "scripts/amoled-motion-check.mjs",
    "  'components/PlayStoreDownloadModal.tsx',",
    "  'components/AboutDiagnosticsModal.tsx',",
    "amoled: overlay list",
)

patch(
    "scripts/verify-release.sh",
    'echo "== Games hub honesty (no fake playability) =="',
    'echo "== URL safety (intent:// can never reach the WebView) =="\n'
    'npx tsx scripts/url-safety-test.mts | tail -1\n\n'
    'echo "== Games hub honesty (no fake playability) =="',
    "gate: url safety",
)

# ---------------------------------------------------------------- report -----
print(f"applied {len(applied)} edits")
for item in applied:
    print(f"  + {item}")
if missing:
    print(f"\nMISSING ANCHORS ({len(missing)}):", file=sys.stderr)
    for item in missing:
        print(f"  ! {item}", file=sys.stderr)
    sys.exit(1)
print("\nall round-6 anchors applied")
