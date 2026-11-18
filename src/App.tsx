import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  Home,
  RotateCw,
  ShieldCheck,
  ShieldOff,
  Sparkles,
  Flame,
  Search,
  X,
  Crosshair,
  Info,
  PictureInPicture2,
  ChevronDown,
  Menu,
  Layers,
  Check,
  ArrowRight,
  Monitor,
  BookOpen,
  Languages,
} from 'lucide-react';
import type {
  BrowserCustomization,
  BrowserExtension,
  BrowserTab,
  DownloadItem,
  NetworkLogEntry,
  ShieldState,
  VpnState,
} from './types/browser';
import {
  getSearchEngineOption,
  GLOBAL_VPN_NODES,
  INITIAL_EXTENSIONS,
  SEARCH_ENGINE_OPTIONS,
  THEME_PRESETS,
} from './data/presets';
import { evaluateArithmetic } from './utils/mathEngine';
import { WebViewport } from './components/WebViewport';
import { ShieldsPanel } from './components/ShieldsPanel';
import { VpnModal } from './components/VpnModal';
import { ExtensionsModal } from './components/ExtensionsModal';
import { AiCopilotDrawer } from './components/AiCopilotDrawer';
import { TabsDrawer } from './components/TabsDrawer';
import { CustomizeDrawer } from './components/CustomizeDrawer';
import { DevToolsPanel } from './components/DevToolsPanel';
import { AboutDiagnosticsModal } from './components/AboutDiagnosticsModal';
import { MenuHubModal } from './components/MenuHubModal';
import { TranslateModal } from './components/TranslateModal';
import { VideoGrabberModal } from './components/VideoGrabberModal';
import { AndroidHybridBridge } from './bridge/androidHybridBridge';
import { classifyUrl } from './utils/urlSafety';
import { SearchEngineIcon } from './components/VectorIcons';
import { getPhraseDictionary } from './utils/translator';
import {
  collectInjectedAssets,
  buildExtensionInjection,
  hasCustomCss,
  hasUserScript,
  isBackgroundPlayEnabled,
} from './utils/extensionEffects';
import { makeViewportGate } from './utils/viewportSync';

function deriveTitleFromUrl(url: string): string {
  if (url === 'browserhub://start') return 'New Tab';
  if (url === 'browserhub://benchmark') return 'System Benchmark';
  if (url === 'browserhub://architecture') return 'Hybrid Architecture';
  if (url.startsWith('gh-search:')) return `GitHub: ${url.replace('gh-search:', '')}`;
  try {
    const u = new URL(url.startsWith('http') ? url : `https://${url}`);
    const q = u.searchParams.get('q') || u.searchParams.get('query') || u.searchParams.get('search');
    if (q) {
      return `${q} — ${u.hostname.replace(/^www\./, '')}`;
    }
    return u.hostname.replace(/^www\./, '') + (u.pathname !== '/' ? u.pathname : '');
  } catch {
    return url;
  }
}

function cleanTrackingUrl(raw: string, stripEnabled: boolean): { url: string; strippedCount: number } {
  if (!stripEnabled || !raw.includes('?')) return { url: raw, strippedCount: 0 };
  try {
    const u = new URL(raw.startsWith('http') ? urlSafe(raw) : `https://${raw}`);
    const trackingKeys = [
      'utm_source',
      'utm_medium',
      'utm_campaign',
      'utm_term',
      'utm_content',
      'fbclid',
      'gclid',
      'mc_cid',
      'si',
      'igshid',
    ];
    let removed = 0;
    for (const key of trackingKeys) {
      if (u.searchParams.has(key)) {
        u.searchParams.delete(key);
        removed++;
      }
    }
    return { url: removed > 0 ? u.toString() : raw, strippedCount: removed };
  } catch {
    return { url: raw, strippedCount: 0 };
  }
}

function urlSafe(raw: string): string {
  return raw.trim();
}

/* ------------------------------------------------------------------ *
 * Settings persistence. Every user-facing preference in this browser is
 * stored on the device (localStorage) so a restart never silently resets
 * the search engine, theme, shields or extension state back to defaults.
 * ------------------------------------------------------------------ */
const STORE_KEY = 'browserhub.prefs.v1';

type StoredPrefs = {
  customization?: Partial<BrowserCustomization>;
  shields?: Partial<ShieldState>;
  vpn?: Partial<VpnState>;
  extensions?: BrowserExtension[];
};

function readStoredPrefs(): StoredPrefs {
  try {
    const raw = window.localStorage.getItem(STORE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as StoredPrefs) : {};
  } catch {
    return {};
  }
}

/** Reads a nested section and merges it over defaults (never inverts a default). */
function withStored<T extends object>(section: keyof StoredPrefs, fallback: T): T {
  const stored = readStoredPrefs()[section] as Partial<T> | undefined;
  if (!stored || typeof stored !== 'object') return fallback;
  return { ...fallback, ...stored };
}

const STORED = readStoredPrefs();

export function App() {
  // Start with 1 clean Home Tab (no pre-cluttered demo tabs)
  const [tabs, setTabs] = useState<BrowserTab[]>([
    {
      id: 'tab-1',
      title: 'New Tab',
      url: 'browserhub://start',
      isIncognito: false,
      isPinned: false,
      isDesktopMode: false,
      isReaderMode: false,
      isHibernated: false,
      groupId: 'main',
      history: ['browserhub://start'],
      historyIndex: 0,
      adsBlocked: 0,
      trackersBlocked: 0,
      bytesSavedKb: 0,
      translatedLang: null,
      bilingualTranslate: false,
      zappedSelectors: [],
      zoomLevel: 100,
      memoryMb: 28,
    },
  ]);

  const [activeTabId, setActiveTabId] = useState<string>('tab-1');
  const [splitTabId, setSplitTabId] = useState<string | null>(null);

  // Shields & Privacy State
  const [shields, setShields] = useState<ShieldState>(() =>
    withStored<ShieldState>('shields', {
    enabled: true,
    blockAds: true,
    blockTrackers: true,
    blockCookieBanners: true,
    stripTrackingParams: true,
    antiFingerprinting: true,
    httpsEverywhere: true,
    blockCryptominers: true,
    totalAdsBlocked: 0,
    totalTrackersBlocked: 0,
    totalDataSavedMb: 0,
    totalTimeSavedSec: 0,
    })
  );

  // VPN State
  const [vpn, setVpn] = useState<VpnState>(() =>
    withStored<VpnState>('vpn', {
    connected: false,
    connecting: false,
    selectedNodeId: GLOBAL_VPN_NODES[0].id,
    protocol: 'WireGuard',
    dohProvider: 'mullvad',
    killSwitch: true,
    blockWebRtcLeak: true,
    splitTunnelBypassLocal: true,
    encryptedMb: 0,
    })
  );

  // Extensions State
  const [extensions, setExtensions] = useState<BrowserExtension[]>(
    () => STORED.extensions || INITIAL_EXTENSIONS
  );

  // Customization State — clean full-bleed layout + Google default search (switchable in 1 tap)
  const [customization, setCustomization] = useState<BrowserCustomization>(() =>
    withStored<BrowserCustomization>('customization', {
    theme: 'amoled-pitch',
    customAccentHex: '#22d3ee',
    addressBarPosition: 'top',
    searchEngine: 'google',
    aiEngine: 'auto',
    uiDensity: 'comfortable',
    biometricLockIncognito: false,
    readerFont: 'sans',
    readerTheme: 'slate',
    readerFontSize: 17,
    })
  );

  const [downloads, setDownloads] = useState<DownloadItem[]>([]);
  const [networkLogs, setNetworkLogs] = useState<NetworkLogEntry[]>([
    {
      id: 'n1',
      domain: 'browserhub.local',
      url: 'browserhub://start',
      type: 'document',
      status: 'allowed',
      latencyMs: 4,
      sizeKb: 18,
      timestamp: 'Now',
    },
  ]);

  // Omnibox & Modals State
  const [omniboxText, setOmniboxText] = useState('');
  const [omniboxFocused, setOmniboxFocused] = useState(false);
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);
  const [zapperMode, setZapperMode] = useState(false);
  const [findQuery, setFindQuery] = useState('');
  const [showFindBar, setShowFindBar] = useState(false);
  const [findResult, setFindResult] = useState<{ active: number; total: number } | null>(null);
  const [translateNotice, setTranslateNotice] = useState<{
    langCode: string;
    blocksChanged: number;
  } | null>(null);

  const [showMenuHub, setShowMenuHub] = useState(false);
  const [showEnginePicker, setShowEnginePicker] = useState(false);
  const [showTranslateModal, setShowTranslateModal] = useState(false);
  const [showVideoGrabber, setShowVideoGrabber] = useState(false);
  const [showShields, setShowShields] = useState(false);
  const [showVpn, setShowVpn] = useState(false);
  const [showExtensions, setShowExtensions] = useState(false);
  const [aiDrawerState, setAiDrawerState] = useState<{
    open: boolean;
    mode?: string;
    query?: string;
  }>({ open: false });
  const [showTabs, setShowTabs] = useState(false);
  const [showCustomize, setShowCustomize] = useState(false);
  const [showDevTools, setShowDevTools] = useState(false);
  const [showAboutModal, setShowAboutModal] = useState(false);
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
  }, [navNotice]);

  const [fireSweepAnim, setFireSweepAnim] = useState(false);
  const [pipTitle, setPipTitle] = useState<string | null>(null);
  const [livePageTexts, setLivePageTexts] = useState<Record<string, string>>({});
  const [deferredInstallPrompt, setDeferredInstallPrompt] = useState<any>(null);
  const viewportContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: Event) => {
      e.preventDefault();
      setDeferredInstallPrompt(e);
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  const activeTab = useMemo(
    () => tabs.find((t) => t.id === activeTabId) || tabs[0],
    [tabs, activeTabId]
  );

  const splitTab = useMemo(
    () => (splitTabId ? tabs.find((t) => t.id === splitTabId) || null : null),
    [tabs, splitTabId]
  );

  const themeStyle = THEME_PRESETS[customization.theme];
  const activeSearchEngine = getSearchEngineOption(customization.searchEngine);

  // Sync omnibox display text cleanly (empty on Home screen so placeholder is clear!)
  useEffect(() => {
    if (!omniboxFocused) {
      setOmniboxText(activeTab.url === 'browserhub://start' ? '' : activeTab.url);
    }
  }, [activeTab.url, omniboxFocused]);

  const anyModalOpen =
    showVideoGrabber ||
    showMenuHub ||
    showEnginePicker ||
    showTranslateModal ||
    showShields ||
    showVpn ||
    showExtensions ||
    aiDrawerState.open ||
    showTabs ||
    showCustomize ||
    showAboutModal;

  const isNativeRealWebUrl = (u: string) => {
    if (!AndroidHybridBridge.hasJniBridge()) return false;
    return u.startsWith('http://') || u.startsWith('https://');
  };

  // Sync real Android Native WebView viewport & live callbacks when installed as APK.
  //
  // IMPORTANT (real-device input bug): the native browser is a SEPARATE Android
  // WebView stacked ABOVE this React shell. If its rect is ever stale it silently
  // covers the app's own chrome and swallows every tap ("covered by div...",
  // touchstart never fires, pointerdown on native page). So we re-sync after
  // EVERY render plus on resize/orientation, not just on a hand-picked dep list.
  useEffect(() => {
    if (!activeTab.translatedLang) setTranslateNotice(null);
  }, [activeTab.translatedLang]);

  // Persist preferences whenever they change (debounced to one write per idle tick).
  useEffect(() => {
    const id = window.setTimeout(() => {
      try {
        window.localStorage.setItem(
          STORE_KEY,
          JSON.stringify({
            customization,
            shields,
            vpn: {
              // Connection state is runtime-only; only choices are persisted.
              selectedNodeId: vpn.selectedNodeId,
              protocol: vpn.protocol,
              dohProvider: vpn.dohProvider,
              killSwitch: vpn.killSwitch,
              blockWebRtcLeak: vpn.blockWebRtcLeak,
              splitTunnelBypassLocal: vpn.splitTunnelBypassLocal,
            },
            extensions,
          })
        );
      } catch {
        // Storage full or disabled — the browser keeps working in memory.
      }
    }, 250);
    return () => window.clearTimeout(id);
  }, [customization, shields, vpn, extensions]);

  const nativeSyncInputs = {
    url: activeTab.url,
    isDesktop: activeTab.isDesktopMode,
    zoom: activeTab.zoomLevel,
    isIncognito: activeTab.isIncognito,
    reader: activeTab.isReaderMode,
    incognito: activeTab.isIncognito,
    shields,
    extensions,
    findQuery: showFindBar ? findQuery : '',
    anyModalOpen,
    split: splitTab,
    devtools: showDevTools,
    bottomBar: customization.addressBarPosition,
    // Shell UI that floats ABOVE the page (mini player, shutdown sweep) is
    // always underneath the native WebView, so the native view must yield.
    pipOpen: pipTitle !== null,
    fireSweep: fireSweepAnim,
    translateLang: activeTab.translatedLang,
  };
  const lastSentTranslateLang = useRef<string | null>(null);
  const lastSentUserScript = useRef<string>('');
  /** Last URL the WebExtension runtime injected into, so one nav = one run. */
  const lastRuntimeUrl = useRef('');
  /**
   * Decides whether a viewport sync is worth a JNI crossing at all. See
   * `utils/viewportSync.ts` — the logic is a tested unit, not an inline rule.
   */
  const viewportGateRef = useRef(makeViewportGate());
  const nativeSyncRef = useRef(nativeSyncInputs);
  nativeSyncRef.current = nativeSyncInputs;

  const syncNativeViewport = () => {
    if (!AndroidHybridBridge.hasJniBridge() || !window.BrowserHubNative) return;
    const inputs = nativeSyncRef.current;

    window.BrowserHubNative.setIncognitoLock?.(inputs.isIncognito);

    const shouldShowNativeWeb =
      (inputs.url.startsWith('http://') || inputs.url.startsWith('https://')) &&
      !inputs.anyModalOpen &&
      !inputs.reader &&
      !inputs.split &&
      !inputs.pipOpen &&
      !inputs.fireSweep;

    const el = viewportContainerRef.current;
    const rect = el ? el.getBoundingClientRect() : { top: 60, bottom: window.innerHeight - 60 };
    const topCss = Math.max(0, Math.round(rect.top));
    const bottomCss = Math.max(0, Math.round(window.innerHeight - rect.bottom));

    const darkReaderOn = inputs.extensions.some((e) => e.enabled && e.effectKey === 'dark-reader');
    const cookieKillerOn =
      (inputs.shields.enabled && inputs.shields.blockCookieBanners) ||
      inputs.extensions.some((e) => e.enabled && e.effectKey === 'cookie-killer');
    const adblockOn =
      (inputs.shields.enabled && inputs.shields.blockAds) ||
      inputs.extensions.some(
        (e) => e.enabled && (e.effectKey === 'ublock' || e.effectKey === 'privacy-badger')
      );
    const { css: customCss, js: customJs } = collectInjectedAssets(inputs.extensions);

    /*
     * Sideloaded WebExtensions execute here.
     *
     * Every enabled package is matched against the current page URL; if any of
     * its content scripts apply, the combined CSS and JS are sent to the real
     * page. Keyed by URL so it fires once per navigation rather than on every
     * layout pass — re-running a content script on each resize would duplicate
     * DOM nodes and is exactly the kind of thing that makes an app feel heavy.
     */
    if (shouldShowNativeWeb && inputs.url !== lastRuntimeUrl.current) {
      lastRuntimeUrl.current = inputs.url;
      const injection = buildExtensionInjection(inputs.extensions, inputs.url);
      window.BrowserHubNative.applyExtensionRuntime?.(injection.css, injection.js);
    } else if (!shouldShowNativeWeb && lastRuntimeUrl.current) {
      lastRuntimeUrl.current = '';
    }

    // User scripts are sent once per change; the native side injects them into
    // the real page so "Userscript" actually runs instead of just being stored.
    if (
      hasUserScript(inputs.extensions) &&
      customJs !== lastSentUserScript.current
    ) {
      lastSentUserScript.current = customJs;
      window.BrowserHubNative.setUserScripts?.(customJs);
    } else if (!hasUserScript(inputs.extensions) && lastSentUserScript.current) {
      lastSentUserScript.current = '';
    }

    // Offline page translation runs inside the real page. Re-sending the same
    // language is a no-op in the page script's own restore-then-apply logic.
    if (inputs.translateLang !== lastSentTranslateLang.current) {
      lastSentTranslateLang.current = inputs.translateLang;
      window.BrowserHubNative.setPageTranslation?.(
        inputs.translateLang || '',
        inputs.translateLang ? JSON.stringify(getPhraseDictionary(inputs.translateLang)) : '{}'
      );
    }

    /*
     * The call below is expensive: seventeen arguments marshalled across the
     * JNI boundary into the Android view layer, which then re-lays-out the
     * native WebView that hosts the page. Its inputs change rarely — a
     * navigation, a zoom step, a toggle, a rotation — but it used to run on
     * every frame, on a 900 ms timer for the whole session, and after every
     * render (once per token while an AI answer streamed). Skip the crossing
     * when nothing it receives actually changed.
     */
    const height = Math.max(1, window.innerHeight);
    const blockTrackers = inputs.shields.enabled && inputs.shields.blockTrackers;
    const antiFingerprinting = inputs.shields.enabled && inputs.shields.antiFingerprinting;
    const httpsEverywhere = inputs.shields.enabled && inputs.shields.httpsEverywhere;
    const backgroundPlay = isBackgroundPlayEnabled(inputs.extensions);
    const customCssEnabled = hasCustomCss(inputs.extensions);

    if (
      !viewportGateRef.current.shouldSync({
        url: inputs.url,
        topCss,
        bottomCss,
        height,
        isDesktop: inputs.isDesktop,
        adblockOn,
        darkReaderOn,
        cookieKillerOn,
        customCss,
        zoom: inputs.zoom,
        findQuery: inputs.findQuery,
        showNativeWeb: shouldShowNativeWeb,
        blockTrackers,
        antiFingerprinting,
        httpsEverywhere,
        backgroundPlay,
        hasCustomCss: customCssEnabled,
      })
    ) {
      return;
    }

    window.BrowserHubNative.syncWebViewport?.(
      inputs.url,
      topCss,
      bottomCss,
      height,
      inputs.isDesktop,
      adblockOn,
      darkReaderOn,
      cookieKillerOn,
      customCss,
      inputs.zoom,
      inputs.findQuery,
      shouldShowNativeWeb,
      blockTrackers,
      antiFingerprinting,
      httpsEverywhere,
      backgroundPlay,
      customCssEnabled
    );
  };

  // Keep a stable handle so listeners/intervals are registered exactly once.
  const syncViewportRef = useRef(syncNativeViewport);
  syncViewportRef.current = syncNativeViewport;

  /*
   * The native viewport sync is a JNI call with seventeen arguments that
   * crosses into the Android view layer. It used to run on every animation
   * frame, on a 900 ms interval forever, AND after every single React render
   * (that effect had no dependency array, so a streaming AI answer fired it
   * once per token). That is the stutter: the WebView was competing with the
   * UI thread for work whose inputs almost never change.
   *
   * Now it is coalesced to one call per frame and only fires when the measured
   * geometry differs from the last call. Real changes are still caught by
   * ResizeObserver, resize, orientation and a slower safety net.
   */

  useEffect(() => {
    if (!AndroidHybridBridge.hasJniBridge()) return;
    let frame = 0;
    const schedule = () => {
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = 0;
        syncViewportRef.current();
      });
    };

    schedule();

    const el = viewportContainerRef.current;
    const ro = typeof ResizeObserver !== 'undefined' && el ? new ResizeObserver(schedule) : null;
    if (ro && el) ro.observe(el);
    window.addEventListener('resize', schedule);
    window.addEventListener('orientationchange', schedule);
    // Safety net for a layout shift that emits no resize event at all (an OEM
    // gesture bar appearing). Two seconds is still imperceptible, and now
    // costs one deduped call instead of an unbounded stream of them.
    const safety = window.setInterval(schedule, 2000);

    return () => {
      if (frame) cancelAnimationFrame(frame);
      if (ro) ro.disconnect();
      window.removeEventListener('resize', schedule);
      window.removeEventListener('orientationchange', schedule);
      clearInterval(safety);
    };
  }, []);

  // Live callbacks from the native page (URL / title / ad-blocked counters).
  //
  // This must NOT bail out on a missing bridge for the lifetime of the app. The
  // JS interface is injected by the native host, and if React mounted before it
  // became visible the handlers were never installed — so the hardware Back
  // button found no handler and the platform exited the app instead of going
  // back. Registration is now unconditional; each handler degrades safely.
  useEffect(() => {

    window.__onNativeUrlChanged = (newUrl: string, newTitle: string) => {
      if (!newUrl || newUrl.startsWith('about:')) return;
      setTabs((prev) =>
        prev.map((t) =>
          t.id === activeTabId
            ? {
                ...t,
                url: newUrl,
                title: newTitle || deriveTitleFromUrl(newUrl),
              }
            : t
        )
      );
    };

    /*
     * The native side batches blocked requests and sends a COUNT, not a list
     * name: one blocked request per JS round-trip was saturating the main
     * thread on ad-heavy pages. Accept either shape so a count and a list name
     * both work, and add the real number rather than always adding one.
     */
    window.__onNativeAdBlocked = (domain: string, listOrCount?: string) => {
      const isTracker = listOrCount === 'trackers';
      const amount = /^\d+$/.test(listOrCount ?? '')
        ? Math.max(1, parseInt(listOrCount as string, 10))
        : 1;
      setShields((prev) => ({
        ...prev,
        totalAdsBlocked: isTracker ? prev.totalAdsBlocked : prev.totalAdsBlocked + amount,
        totalTrackersBlocked: isTracker
          ? prev.totalTrackersBlocked + amount
          : prev.totalTrackersBlocked,
      }));
      setTabs((prev) =>
        prev.map((t) =>
          t.id === activeTabId
            ? {
                ...t,
                adsBlocked: isTracker ? t.adsBlocked : t.adsBlocked + amount,
                trackersBlocked: isTracker ? t.trackersBlocked + amount : t.trackersBlocked,
              }
            : t
        )
      );
      /*
       * One row per domain, not one row per blocked request. A page can block
       * dozens of requests to the same host; pushing a row for each one both
       * churned React state and pushed the genuinely interesting entries out of
       * the 50-row window. The row carries the count instead.
       */
      const key = domain || 'ad-tracker';
      setNetworkLogs((prev) => {
        const existing = prev.findIndex((e) => e.domain === key && e.status === 'blocked');
        if (existing >= 0) {
          const merged = [...prev];
          const hit = merged[existing];
          merged[existing] = {
            ...hit,
            count: (hit.count ?? 1) + amount,
            timestamp: new Date().toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
            }),
          };
          return merged;
        }
        return [
          {
            id: `blk-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            domain: key,
            url: `https://${domain || 'tracker'}`,
            type: isTracker ? 'tracker' : 'ad-banner',
            status: 'blocked',
            ruleMatched: 'Native HubShield Filter',
            latencyMs: 0,
            sizeKb: 0,
            count: amount,
            timestamp: new Date().toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
            }),
          },
          ...prev.slice(0, 49),
        ];
      });
    };

    window.__onNativeFindResult = (activeOrdinal: number, totalMatches: number) => {
      setFindResult({ active: activeOrdinal, total: totalMatches });
    };

    window.__onNativeTranslateResult = (langCode: string, blocksChanged: number) => {
      setTranslateNotice({ langCode, blocksChanged });
    };

    return () => {
      window.__onNativeUrlChanged = undefined;
      window.__onNativeAdBlocked = undefined;
      window.__onNativeFindResult = undefined;
      window.__onNativeTranslateResult = undefined;
    };
  }, [activeTabId, activeTab.historyIndex, activeTab.url, anyModalOpen]);

  /*
   * Hardware / gesture Back — registered exactly once.
   *
   * BUG THIS FIXES ("the back button is not working", reported three rounds in a
   * row): the handler used to live inside the bridge effect above, whose
   * dependency list includes the active tab URL, history index and modal state.
   * Every navigation and every modal open therefore ran its cleanup — which set
   * `window.__onAndroidBackPressed = undefined` — and only then reinstalled it.
   * A Back press arriving during that gap found no handler at all and fell
   * through to the platform, which either did nothing or left the app.
   *
   * The handler is now installed once and reads current state from a ref, so it
   * is never torn down. Logic itself lives in backHandlerRef so it always sees
   * the latest values without re-registering.
   */
  /**
   * Every dock and toolbar control gets a physical tick. Delegated from one
   * listener rather than sprinkled through dozens of onClick handlers, so a new
   * button inherits it automatically and cannot forget.
   */
  useEffect(() => {
    if (!AndroidHybridBridge.hasJniBridge()) return undefined;
    const onPointerUp = (event: PointerEvent) => {
      const target = event.target as HTMLElement | null;
      const control = target?.closest?.('button, [role="button"], nav a');
      if (!control) return;
      if ((control as HTMLButtonElement).disabled) return;
      AndroidHybridBridge.haptic(1);
    };
    document.addEventListener('pointerup', onPointerUp, { passive: true });
    return () => document.removeEventListener('pointerup', onPointerUp);
  }, []);

  const backHandlerRef = useRef<() => boolean>(() => false);
  backHandlerRef.current = () => {
    if (anyModalOpen) {
      setShowMenuHub(false);
      setShowEnginePicker(false);
      setShowTranslateModal(false);
      setShowVideoGrabber(false);
      setShowShields(false);
      setShowVpn(false);
      setShowExtensions(false);
      setAiDrawerState({ open: false });
      setShowTabs(false);
      setShowCustomize(false);
      setShowAboutModal(false);
      setShowDevTools(false);
      setNavNotice(null);
      return true;
    }
    if (activeTab.historyIndex > 0) {
      handleGoBack();
      return true;
    }
    if (activeTab.url !== 'browserhub://start') {
      navigateTab(activeTab.id, 'browserhub://start');
      return true;
    }
    return false;
  };

  useEffect(() => {
    window.__onAndroidBackPressed = () => backHandlerRef.current();
    return () => {
      // Only removed on unmount, never on a state change.
      window.__onAndroidBackPressed = undefined;
    };
  }, []);

  // Instant Omnibox Math Calculator — same parser the AI uses, so `×`, `÷`,
  // `%` and parentheses behave identically in both places.
  const instantMathResult = useMemo(() => {
    const expr = omniboxText.trim();
    if (!expr || expr.startsWith('http') || expr.includes('://')) return null;
    return evaluateArithmetic(expr)?.value ?? null;
  }, [omniboxText]);

  const updateTab = (tabId: string, updater: (t: BrowserTab) => BrowserTab) => {
    setTabs((prev) => prev.map((t) => (t.id === tabId ? updater(t) : t)));
  };

  const navigateTab = (targetTabId: string, rawInput: string) => {
    const trimmed = rawInput.trim();
    if (!trimmed) return;
    // A new page starts untranslated: the native layer restores the previous
    // DOM on its own, and the tab-level flag must not claim otherwise.
    updateTab(targetTabId, (t) =>
      t.translatedLang ? { ...t, translatedLang: null, bilingualTranslate: false } : t
    );

    let targetUrl = trimmed;

    // "gh-search:react hooks" is a typing accelerator, not a page of its own:
    // resolve it to a real github.com search URL so nothing mock is ever shown.
    if (/^gh-search:/i.test(targetUrl)) {
      const q = targetUrl.replace(/^gh-search:/i, '').trim();
      targetUrl = q
        ? `https://github.com/search?q=${encodeURIComponent(q)}&type=repositories`
        : 'https://github.com/explore';
    }

    /*
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

    // Both the shield toggle and the ClearURLs extension strip parameters.
    const clearUrlsOn =
      (shields.enabled && shields.stripTrackingParams) ||
      extensions.some((e) => e.enabled && e.effectKey === 'clear-urls');
    const cleaned = cleanTrackingUrl(targetUrl, clearUrlsOn);
    targetUrl = cleaned.url;

    const newTitle = deriveTitleFromUrl(targetUrl);

    updateTab(targetTabId, (t) => {
      const nextHistory = [...t.history.slice(0, t.historyIndex + 1), targetUrl];
      return {
        ...t,
        url: targetUrl,
        title: newTitle,
        isHibernated: false,
        isReaderMode: false,
        history: nextHistory,
        historyIndex: nextHistory.length - 1,
        trackersBlocked: t.trackersBlocked + cleaned.strippedCount,
      };
    });

    setNetworkLogs((prev) => [
      {
        id: `nav-${Date.now()}`,
        domain: newTitle,
        url: targetUrl,
        type: 'document',
        status: cleaned.strippedCount > 0 ? 'stripped' : 'allowed',
        latencyMs: 18,
        sizeKb: 42,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      },
      ...prev.slice(0, 49),
    ]);
  };

  const handleGoBack = () => {
    if (isNativeRealWebUrl(activeTab.url) && window.BrowserHubNative?.nativeGoBack?.()) {
      return;
    }
    if (activeTab.historyIndex <= 0) {
      if (activeTab.url !== 'browserhub://start') {
        navigateTab(activeTab.id, 'browserhub://start');
      }
      return;
    }
    updateTab(activeTab.id, (t) => {
      const nextIdx = t.historyIndex - 1;
      const nextUrl = t.history[nextIdx];
      return {
        ...t,
        url: nextUrl,
        title: deriveTitleFromUrl(nextUrl),
        historyIndex: nextIdx,
      };
    });
  };

  const handleGoForward = () => {
    if (isNativeRealWebUrl(activeTab.url) && window.BrowserHubNative?.nativeGoForward?.()) {
      return;
    }
    if (activeTab.historyIndex >= activeTab.history.length - 1) return;
    updateTab(activeTab.id, (t) => {
      const nextIdx = t.historyIndex + 1;
      const nextUrl = t.history[nextIdx];
      return {
        ...t,
        url: nextUrl,
        title: deriveTitleFromUrl(nextUrl),
        historyIndex: nextIdx,
      };
    });
  };

  const handleReload = () => {
    if (isNativeRealWebUrl(activeTab.url) && window.BrowserHubNative?.nativeReload) {
      window.BrowserHubNative.nativeReload();
      return;
    }
    navigateTab(activeTab.id, activeTab.url);
  };

  const handleNewTab = (incognito: boolean, groupId: BrowserTab['groupId'] = 'main') => {
    const id = `tab-${Date.now()}`;
    const newTab: BrowserTab = {
      id,
      title: incognito ? 'Incognito Tab' : 'New Tab',
      url: 'browserhub://start',
      isIncognito: incognito,
      isPinned: false,
      isDesktopMode: false,
      isReaderMode: false,
      isHibernated: false,
      groupId,
      history: ['browserhub://start'],
      historyIndex: 0,
      adsBlocked: 0,
      trackersBlocked: 0,
      bytesSavedKb: 0,
      translatedLang: null,
      bilingualTranslate: false,
      zappedSelectors: [],
      zoomLevel: 100,
      memoryMb: 28,
    };
    setTabs((prev) => [...prev, newTab]);
    setActiveTabId(id);
  };

  const handleCloseTab = (id: string) => {
    if (tabs.length <= 1) {
      navigateTab(tabs[0].id, 'browserhub://start');
      return;
    }
    const remaining = tabs.filter((t) => t.id !== id);
    setTabs(remaining);
    if (activeTabId === id) {
      setActiveTabId(remaining[remaining.length - 1].id);
    }
    if (splitTabId === id) {
      setSplitTabId(null);
    }
  };

  const handleFireBurnAll = () => {
    setFireSweepAnim(true);
    try {
      window.BrowserHubNative?.shredSessionData?.();
    } catch {
      // ignore
    }
    setTimeout(() => {
      const cleanId = `tab-clean-${Date.now()}`;
      setTabs([
        {
          id: cleanId,
          title: 'New Tab',
          url: 'browserhub://start',
          isIncognito: false,
          isPinned: false,
          isDesktopMode: false,
          isReaderMode: false,
          isHibernated: false,
          groupId: 'main',
          history: ['browserhub://start'],
          historyIndex: 0,
          adsBlocked: 0,
          trackersBlocked: 0,
          bytesSavedKb: 0,
          translatedLang: null,
          bilingualTranslate: false,
          zappedSelectors: [],
          zoomLevel: 100,
          memoryMb: 26,
        },
      ]);
      setActiveTabId(cleanId);
      setSplitTabId(null);
      setLivePageTexts({});
      setPipTitle(null);
    }, 450);
    setTimeout(() => setFireSweepAnim(false), 1100);
  };

  const handleTriggerDownload = (
    filename: string,
    url: string,
    sizeMb: number,
    category: 'apk' | 'video' | 'archive' | 'document'
  ) => {
    const newDl: DownloadItem = {
      id: `dl-${Date.now()}`,
      filename,
      url,
      sizeMb,
      downloadedMb: 0,
      threads: 16,
      speedMbps: 34.8,
      status: 'downloading',
      category,
    };
    setDownloads((prev) => [newDl, ...prev]);
    setShowCustomize(true);
  };

  // ============================================================================
  // CLEAN SEARCH & URL BAR (Spacious, Uncluttered, Instant Search Engine Picker)
  // ============================================================================
  const SearchAddressBar = (
    <div
      style={{
        backgroundColor: activeTab.isIncognito ? '#120a1b' : themeStyle.bgBar,
        borderColor: themeStyle.border,
        gap: customization.uiDensity === 'compact' ? 4 : 6,
      }}
      className={`${
        customization.uiDensity === 'compact' ? 'px-2 py-1' : 'px-2.5 py-2'
      } border-b flex flex-col shrink-0 z-20`}
      data-density={customization.uiDensity}
    >
      {instantMathResult && (
        <div
          className="px-3 py-1.5 rounded-xl border border-cyan-500/40 text-xs flex items-center justify-between text-cyan-200"
          style={{ backgroundColor: '#08080b' }}
        >
          <span>Calculator:</span>
          <strong className="font-mono text-sm text-emerald-300">= {instantMathResult}</strong>
        </div>
      )}

      {zapperMode && (
        <div
          className="px-3 py-1.5 rounded-xl border border-rose-500/50 text-xs flex items-center justify-between text-rose-200"
          style={{ backgroundColor: '#310b19' }}
        >
          <span className="inline-flex items-center gap-1.5 font-semibold">
            <Crosshair size={14} className="text-rose-400" />
            <span>Element Zapper Active</span>
          </span>
          <button
            onClick={() => setZapperMode(false)}
            className="px-2.5 py-0.5 rounded-lg bg-rose-500 text-white font-bold text-[11px] cursor-pointer"
          >
            Done
          </button>
        </div>
      )}

      <div className="flex items-center gap-1.5">
        {/* Search Engine Selector — always labelled so it is never a mystery icon */}
        <button
          type="button"
          onClick={() => setShowEnginePicker(true)}
          title={`Search engine: ${activeSearchEngine.name} — tap to change`}
          aria-label={`Search engine: ${activeSearchEngine.name}. Tap to change.`}
          className="h-11 px-2.5 rounded-xl border border-slate-700 hover:border-cyan-400 text-xs font-semibold text-white flex items-center gap-1.5 shrink-0 cursor-pointer max-w-[104px]"
          style={{ backgroundColor: '#000000' }}
        >
          <SearchEngineIcon id={activeSearchEngine.id} size={17} />
          <span className="truncate text-slate-200">{activeSearchEngine.shortLabel}</span>
          <ChevronDown size={12} className="text-slate-400" />
        </button>

        {/* Wide Search & URL Form */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!omniboxText.trim()) return;
            navigateTab(activeTab.id, omniboxText);
            setSuggestionsOpen(false);
            (document.activeElement as HTMLElement | null)?.blur();
          }}
          className="flex-1 min-w-[96px] h-11 flex items-center gap-1.5 rounded-xl border border-slate-700 focus-within:border-cyan-400 px-2.5"
          style={{ backgroundColor: '#000000' }}
        >
          <Search size={15} className="text-slate-400 shrink-0" />
          <input
            value={omniboxText}
            onFocus={(e) => {
              setOmniboxFocused(true);
              setSuggestionsOpen(true);
              e.currentTarget.select();
            }}
            onBlur={() => {
              setOmniboxFocused(false);
              window.setTimeout(() => setSuggestionsOpen(false), 180);
            }}
            onChange={(e) => {
              setOmniboxText(e.target.value);
              setSuggestionsOpen(true);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                setSuggestionsOpen(false);
                e.currentTarget.blur();
              }
            }}
            placeholder={
              activeTab.url === 'browserhub://start'
                ? activeSearchEngine.placeholder
                : 'Search or enter address'
            }
            enterKeyHint="go"
            inputMode="search"
            aria-label="Search or enter website address"
            className="flex-1 min-w-0 bg-transparent text-[13px] text-white placeholder-slate-400 focus:outline-none py-0.5"
          />

          {omniboxText && (
            <button
              type="button"
              onClick={() => {
                setOmniboxText('');
                setSuggestionsOpen(true);
              }}
              className="p-1.5 text-slate-400 hover:text-white cursor-pointer shrink-0"
              title="Clear text"
              aria-label="Clear text"
            >
              <X size={14} />
            </button>
          )}

          {omniboxText.trim() ? (
            <button
              type="submit"
              className="p-1.5 rounded-lg bg-cyan-400 text-slate-950 cursor-pointer shrink-0"
              title="Go"
              aria-label="Go"
            >
              <ArrowRight size={14} />
            </button>
          ) : (
            activeTab.url !== 'browserhub://start' && (
              <button
                type="button"
                onClick={handleReload}
                className="p-1.5 text-slate-400 hover:text-white cursor-pointer shrink-0"
                title="Reload page"
                aria-label="Reload page"
              >
                <RotateCw size={14} />
              </button>
            )
          )}
        </form>

        {/* Shields status — labelled with a count, tap opens full panel */}
        <button
          type="button"
          onClick={() => setShowShields(true)}
          title="AdBlock & privacy shields"
          aria-label="AdBlock and privacy shields"
          className={`h-11 px-2 rounded-xl border text-xs font-bold flex flex-col items-center justify-center gap-0.5 shrink-0 cursor-pointer ${
            shields.enabled
              ? 'border-emerald-500/40 text-emerald-300'
              : 'border-rose-500/40 text-rose-300'
          }`}
          style={{ backgroundColor: shields.enabled ? '#06281e' : '#2c0b14' }}
        >
          {shields.enabled ? <ShieldCheck size={15} /> : <ShieldOff size={15} />}
          <span className="text-[9px] leading-none font-bold">
            {activeTab.adsBlocked + activeTab.trackersBlocked}
          </span>
        </button>
      </div>

      {/* Live suggestion panel — instant engine switch + real search */}
      {suggestionsOpen && (
        <div
          className="bh-pop rounded-xl border border-slate-700 overflow-hidden divide-y divide-slate-800"
          style={{ backgroundColor: '#000000' }}
        >
          {omniboxText.trim() ? (
            <>
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  navigateTab(activeTab.id, omniboxText);
                  setOmniboxText('');
                  setSuggestionsOpen(false);
                }}
                className="w-full px-3 py-2.5 flex items-center gap-2.5 text-left hover:bg-[#121216] cursor-pointer"
              >
                <Search size={15} className="text-cyan-400 shrink-0" />
                <span className="text-[13px] text-white truncate">
                  Search <strong className="font-semibold">{omniboxText}</strong> on{' '}
                  {activeSearchEngine.name}
                </span>
              </button>
              {SEARCH_ENGINE_OPTIONS.filter((e) => e.id !== customization.searchEngine)
                .slice(0, 3)
                .map((eng) => (
                  <button
                    key={eng.id}
                    type="button"
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => {
                      setCustomization((prev) => ({ ...prev, searchEngine: eng.id }));
                      navigateTab(activeTab.id, eng.buildUrl(omniboxText.trim()));
                      setOmniboxText('');
                      setSuggestionsOpen(false);
                    }}
                    className="w-full px-3 py-2 flex items-center gap-2.5 text-left hover:bg-[#121216] cursor-pointer"
                  >
                    <SearchEngineIcon id={eng.id} size={15} />
                    <span className="text-[12px] text-slate-300 truncate">
                      instead search on {eng.name}
                    </span>
                  </button>
                ))}
            </>
          ) : (
            <div className="px-3 py-2.5 space-y-1">
              <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Search with
              </div>
              <div className="flex flex-wrap gap-1.5 pt-0.5">
                {SEARCH_ENGINE_OPTIONS.map((eng) => {
                  const sel = customization.searchEngine === eng.id;
                  return (
                    <button
                      key={eng.id}
                      type="button"
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => {
                        setCustomization((prev) => ({ ...prev, searchEngine: eng.id }));
                        setSuggestionsOpen(false);
                      }}
                      className={`px-2.5 py-1.5 rounded-lg border text-[11px] font-semibold flex items-center gap-1 cursor-pointer ${
                        sel
                          ? 'bg-cyan-500/20 border-cyan-400 text-white'
                          : 'bg-black border-slate-700 text-slate-300'
                      }`}
                    >
                      <SearchEngineIcon id={eng.id} size={13} />
                      <span>{eng.shortLabel}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );

  // ============================================================================
  // CONTEXTUAL PAGE TOOL STRIP (only appears on real sites, one tap each)
  // ============================================================================
  const isRealPage =
    activeTab.url.startsWith('http://') || activeTab.url.startsWith('https://');

  const PageToolStrip = isRealPage ? (
    <div
      className="px-2.5 py-1.5 border-b border-slate-800 flex items-center gap-1.5 overflow-x-auto shrink-0"
      style={{ backgroundColor: '#000000' }}
    >
      <button
        onClick={() => updateTab(activeTab.id, (t) => ({ ...t, isReaderMode: !t.isReaderMode }))}
        className={`px-2.5 py-1 rounded-lg border text-[11px] font-semibold flex items-center gap-1 shrink-0 cursor-pointer ${
          activeTab.isReaderMode
            ? 'bg-emerald-500/20 border-emerald-400 text-emerald-200'
            : 'bg-black border-slate-700 text-slate-300'
        }`}
      >
        <BookOpen size={12} />
        <span>Reader</span>
      </button>
      <button
        onClick={() => setAiDrawerState({ open: true, mode: 'summarize' })}
        className="px-2.5 py-1 rounded-lg border border-cyan-500/40 bg-cyan-950/50 text-[11px] font-semibold text-cyan-200 flex items-center gap-1 shrink-0 cursor-pointer"
      >
        <Sparkles size={12} />
        <span>Summarize</span>
      </button>
      <button
        onClick={() =>
          updateTab(activeTab.id, (t) => ({ ...t, isDesktopMode: !t.isDesktopMode }))
        }
        className={`px-2.5 py-1 rounded-lg border text-[11px] font-semibold flex items-center gap-1 shrink-0 cursor-pointer ${
          activeTab.isDesktopMode
            ? 'bg-cyan-500/20 border-cyan-400 text-cyan-200'
            : 'bg-black border-slate-700 text-slate-300'
        }`}
      >
        <Monitor size={12} />
        <span>Desktop</span>
      </button>
      <button
        onClick={() => setShowTranslateModal(true)}
        className="px-2.5 py-1 rounded-lg border border-slate-700 bg-black text-[11px] font-semibold text-slate-300 flex items-center gap-1 shrink-0 cursor-pointer"
      >
        <Languages size={12} />
        <span>{activeTab.translatedLang ? activeTab.translatedLang.toUpperCase() : 'Translate'}</span>
      </button>
      <button
        onClick={() => setShowFindBar(true)}
        className="px-2.5 py-1 rounded-lg border border-slate-700 bg-black text-[11px] font-semibold text-slate-300 flex items-center gap-1 shrink-0 cursor-pointer"
      >
        <Search size={12} />
        <span>Find</span>
      </button>
      <button
        onClick={() => setZapperMode((v) => !v)}
        className={`px-2.5 py-1 rounded-lg border text-[11px] font-semibold flex items-center gap-1 shrink-0 cursor-pointer ${
          zapperMode
            ? 'bg-rose-500/20 border-rose-400 text-rose-200'
            : 'bg-black border-slate-700 text-slate-300'
        }`}
      >
        <Crosshair size={12} />
        <span>Zap</span>
      </button>
    </div>
  ) : null;

  // ============================================================================
  // LABELED 5-BUTTON BOTTOM NAVIGATION BAR (Zero Mystery Icons — Every Action Labeled)
  // ============================================================================
  const canGoBack = activeTab.historyIndex > 0 || activeTab.url !== 'browserhub://start';
  const enabledExtCount = extensions.filter((e) => e.enabled).length;

  const BottomNavDock = (
    <nav
      style={{
        backgroundColor: activeTab.isIncognito ? '#120a1b' : themeStyle.bgBar,
        borderColor: themeStyle.border,
      }}
      className="px-2 py-1.5 border-t grid grid-cols-5 gap-1 shrink-0 z-20 select-none"
    >
      <button
        onClick={() => {
          if (canGoBack) {
            handleGoBack();
          } else {
            navigateTab(activeTab.id, 'browserhub://start');
          }
        }}
        className="py-1.5 rounded-xl hover:bg-[#121216] text-slate-200 flex flex-col items-center justify-center gap-0.5 cursor-pointer"
      >
        {canGoBack ? <ArrowLeft size={18} /> : <Home size={18} />}
        <span className="text-[10px] font-semibold">{canGoBack ? 'Back' : 'Home'}</span>
      </button>

      <button
        onClick={() => setShowShields(true)}
        className="py-1.5 rounded-xl hover:bg-[#121216] text-slate-200 flex flex-col items-center justify-center gap-0.5 cursor-pointer"
      >
        <ShieldCheck size={18} className={shields.enabled ? 'text-emerald-400' : 'text-slate-400'} />
        <span className="text-[10px] font-semibold">Shields</span>
      </button>

      <button
        onClick={() => setAiDrawerState({ open: true, mode: 'idle' })}
        className="py-1.5 rounded-xl border border-cyan-500/40 text-cyan-200 flex flex-col items-center justify-center gap-0.5 cursor-pointer"
        style={{ backgroundColor: '#0d0d10' }}
      >
        <Sparkles size={18} className="text-cyan-300" />
        <span className="text-[10px] font-bold text-cyan-200">Ask AI</span>
      </button>

      <button
        onClick={() => setShowTabs(true)}
        className={`py-1.5 rounded-xl hover:bg-[#121216] text-slate-200 flex flex-col items-center justify-center gap-0.5 cursor-pointer ${
          tabs.length > 1 ? 'bh-dock-active' : ''
        }`}
      >
        <div className="relative flex items-center justify-center">
          <Layers size={18} className={activeTab.isIncognito ? 'text-purple-300' : 'text-slate-200'} />
          <span className="ml-1 px-1.5 py-0.2 rounded-md bg-[#121216] border border-slate-600 text-[10px] font-extrabold text-white">
            {tabs.length}
          </span>
        </div>
        <span className="text-[10px] font-semibold">Tabs</span>
      </button>

      <button
        onClick={() => setShowMenuHub(true)}
        className="py-1.5 rounded-xl hover:bg-[#121216] text-slate-200 flex flex-col items-center justify-center gap-0.5 cursor-pointer"
      >
        <Menu size={18} />
        <span className="text-[10px] font-semibold">Menu</span>
      </button>
    </nav>
  );

  return (
    <div
      style={
        {
          backgroundColor: themeStyle.bgShell,
          // The user-picked accent drives the browser's own chrome through one
          // CSS variable, so "custom accent" is a real setting, not decoration.
          '--bh-accent': customization.customAccentHex,
          borderColor: customization.customAccentHex,
        } as React.CSSProperties
      }
      className="h-full w-full flex flex-col overflow-hidden relative bh-app-shell"
    >
      {/* Top Address Bar Placement */}
      {customization.addressBarPosition === 'top' && SearchAddressBar}
      {PageToolStrip}

      {/* Real feedback from the native offline translator */}
      {translateNotice && activeTab.translatedLang && (
        <div className="bh-bar-open px-2.5 py-1 bg-[#0a0a12] border-b border-indigo-500/40 text-[10px] text-indigo-200 flex items-center justify-between shrink-0">
          <span className="inline-flex items-center gap-1.5">
            <Languages size={11} className="shrink-0" />
            <span>
              {translateNotice.blocksChanged > 0
                ? `Translated ${translateNotice.blocksChanged} text block${
                    translateNotice.blocksChanged === 1 ? '' : 's'
                  } in this page (offline lexicon).`
                : 'No known phrases on this page matched the offline lexicon — nothing was changed.'}
            </span>
          </span>
          <button
            type="button"
            onClick={() => updateTab(activeTab.id, (t) => ({ ...t, translatedLang: null }))}
            className="px-1.5 py-0.5 rounded-md bg-indigo-500/25 font-bold cursor-pointer shrink-0 ml-2"
          >
            Undo
          </button>
        </div>
      )}

      {/* Optional Find-in-Page Bar */}
      {showFindBar && (
        <div
          className="bh-bar-open px-3 py-2 border-b border-slate-800 flex items-center gap-2 shrink-0 z-20"
          style={{ backgroundColor: '#000000' }}
        >
          <Search size={15} className="text-amber-400 shrink-0" />
          <input
            autoFocus
            value={findQuery}
            onChange={(e) => setFindQuery(e.target.value)}
            placeholder="Find text on page..."
            className="flex-1 bg-transparent text-xs text-white focus:outline-none"
          />
          {findResult && findQuery.trim() !== '' && (
            <span
              className={`text-[10px] font-mono px-1.5 py-0.5 rounded-md shrink-0 ${
                findResult.total > 0
                  ? 'bg-emerald-500/15 text-emerald-300'
                  : 'bg-rose-500/15 text-rose-300'
              }`}
            >
              {findResult.total > 0
                ? `${findResult.active}/${findResult.total}`
                : 'no matches'}
            </span>
          )}
          <button
            aria-label="Close find in page"
            title="Close find in page"
            onClick={() => {
              setFindQuery('');
              setFindResult(null);
              setShowFindBar(false);
            }}
            className="p-1 text-slate-400 hover:text-white cursor-pointer"
          >
            <X size={15} />
          </button>
        </div>
      )}

      {/* Main Browsing Viewport */}
      <div
        ref={viewportContainerRef}
        className={`flex-1 min-h-0 relative ${
          splitTab
            ? 'grid grid-rows-2 sm:grid-rows-1 sm:grid-cols-2 divide-y sm:divide-y-0 sm:divide-x divide-cyan-500/40'
            : 'flex'
        }`}
      >
        <div key={activeTab.id} className="bh-viewport-enter h-full w-full min-h-0 overflow-hidden">
          <WebViewport
            tab={activeTab}
            shields={shields}
            vpn={vpn}
            extensions={extensions}
            customization={customization}
            zapperMode={zapperMode}
            findQuery={findQuery}
            onNavigate={(u) => navigateTab(activeTab.id, u)}
            onSelectSearchEngine={(id) =>
              setCustomization((prev) => ({ ...prev, searchEngine: id }))
            }
            onZapElement={(zapId) =>
              updateTab(activeTab.id, (t) => ({
                ...t,
                zappedSelectors: [...t.zappedSelectors, zapId],
                adsBlocked: t.adsBlocked + 1,
              }))
            }
            onOpenAi={(mode, query) => setAiDrawerState({ open: true, mode, query })}
            onOpenVpn={() => setShowVpn(true)}
            onOpenShields={() => setShowShields(true)}
            onOpenExtensions={() => setShowExtensions(true)}
            onToggleDesktop={() =>
              updateTab(activeTab.id, (t) => ({ ...t, isDesktopMode: !t.isDesktopMode }))
            }
            onOpenNewIncognito={() => handleNewTab(true)}
            onTriggerDownload={handleTriggerDownload}
            onEnablePip={(title) => setPipTitle(title)}
            onLiveContentLoaded={(summaryText, pageTitle) => {
              setLivePageTexts((prev) => ({ ...prev, [activeTab.id]: summaryText }));
              updateTab(activeTab.id, (t) => ({ ...t, title: pageTitle }));
            }}
          />
        </div>

        {splitTab && (
          <div className="h-full w-full min-h-0 overflow-hidden relative">
            <div className="absolute top-2 right-2 z-20 px-2.5 py-1 rounded-full bg-purple-700 text-white text-[10px] font-bold flex items-center gap-1.5">
              <span>Split View</span>
              <button onClick={() => setSplitTabId(null)} className="cursor-pointer">
                <X size={12} />
              </button>
            </div>
            <WebViewport
              tab={splitTab}
              shields={shields}
              vpn={vpn}
              extensions={extensions}
              customization={customization}
              zapperMode={zapperMode}
              findQuery={findQuery}
              onNavigate={(u) => navigateTab(splitTab.id, u)}
              onSelectSearchEngine={(id) =>
                setCustomization((prev) => ({ ...prev, searchEngine: id }))
              }
              onZapElement={(zapId) =>
                updateTab(splitTab.id, (t) => ({
                  ...t,
                  zappedSelectors: [...t.zappedSelectors, zapId],
                }))
              }
              onOpenAi={(mode, query) => setAiDrawerState({ open: true, mode, query })}
              onOpenVpn={() => setShowVpn(true)}
              onOpenShields={() => setShowShields(true)}
              onOpenExtensions={() => setShowExtensions(true)}
              onToggleDesktop={() =>
                updateTab(splitTab.id, (t) => ({ ...t, isDesktopMode: !t.isDesktopMode }))
              }
              onOpenNewIncognito={() => handleNewTab(true)}
              onTriggerDownload={handleTriggerDownload}
              onEnablePip={(title) => setPipTitle(title)}
            />
          </div>
        )}

        {fireSweepAnim && (
          <div className="absolute inset-0 z-50 bg-orange-600/90 flex flex-col items-center justify-center text-white animate-fire-sweep pointer-events-none">
            <Flame size={48} className="text-amber-200 mb-2" />
            <div className="text-base font-extrabold tracking-wide">
              CLEARING COOKIES, CACHE &amp; SESSION...
            </div>
          </div>
        )}
      </div>

      {pipTitle && (
        <div className="fixed bottom-24 right-3 z-40 w-56 rounded-2xl border border-cyan-400/50 shadow-2xl p-2.5 space-y-1.5" style={{ backgroundColor: '#000000' }}>
            <div className="flex items-center justify-between text-[10px] text-cyan-300 font-bold">
              <span className="inline-flex items-center gap-1">
                <PictureInPicture2 size={12} /> PiP Player
              </span>
              <button
                onClick={() => setPipTitle(null)}
                className="text-slate-400 hover:text-white cursor-pointer"
              >
                <X size={12} />
              </button>
            </div>
          <div className="h-16 rounded-xl bg-black flex items-center justify-center p-2 text-center text-[11px] font-semibold text-white">
            ▶ {pipTitle}
          </div>
        </div>
      )}

      {showDevTools && (
        <DevToolsPanel
          tab={activeTab}
          networkLogs={networkLogs}
          onClose={() => setShowDevTools(false)}
        />
      )}

      {/* Optional Bottom Search Bar Placement (above Bottom Nav Dock) */}
      {customization.addressBarPosition === 'bottom' && SearchAddressBar}

      {/* Permanent Ergonomic 5-Item Labeled Bottom Bar */}
      {BottomNavDock}

      {/* ==================================================================== */}
      {/* MODALS & DRAWERS                                                     */}
      {/* ==================================================================== */}

      {/* Search Engine Picker Sheet */}
      {showEnginePicker && (
        <div
          className="bh-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
          style={{ backgroundColor: 'rgba(0, 0, 0, 0.88)' }}
          onClick={() => setShowEnginePicker(false)}
        >
          <div
            className="bh-sheet w-full max-w-sm rounded-t-3xl sm:rounded-3xl border border-slate-800 shadow-2xl overflow-hidden"
            style={{ backgroundColor: '#000000' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div
              className="px-5 py-3.5 border-b border-slate-800 flex items-center justify-between"
              style={{ backgroundColor: '#000000' }}
            >
              <div>
                <div className="text-sm font-bold text-white">Choose Search Engine</div>
                <div className="text-[11px] text-slate-400">
                  Used by both the address bar and Home search
                </div>
              </div>
              <button
                onClick={() => setShowEnginePicker(false)}
                className="p-2 rounded-xl hover:bg-[#121216] text-slate-300 cursor-pointer"
              >
                <X size={18} />
              </button>
            </div>
            <div className="p-3 space-y-1.5 max-h-[70vh] overflow-y-auto">
              {SEARCH_ENGINE_OPTIONS.map((eng) => {
                const selected = customization.searchEngine === eng.id;
                return (
                  <button
                    key={eng.id}
                    onClick={() => {
                      setCustomization((prev) => ({ ...prev, searchEngine: eng.id }));
                      setShowEnginePicker(false);
                    }}
                    className={`w-full px-3.5 py-3 rounded-xl border text-left flex items-center justify-between cursor-pointer transition ${
                      selected
                        ? 'bg-cyan-500/20 border-cyan-400 text-white font-bold'
                        : 'bg-black hover:bg-[#121216] border-slate-800 text-slate-200'
                    }`}
                  >
                    <span className="flex items-center gap-3">
                      <SearchEngineIcon id={eng.id} size={20} />
                      <span>
                        <span className="block text-xs font-semibold">{eng.name}</span>
                        <span className="block text-[10px] text-slate-400">{eng.homeUrl}</span>
                      </span>
                    </span>
                    {selected && <Check size={16} className="text-cyan-400 shrink-0" />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Categorized Control Menu Hub */}
      {showMenuHub && (
        <MenuHubModal
          tab={activeTab}
          tabsCount={tabs.length}
          shields={shields}
          vpn={vpn}
          enabledExtensionsCount={enabledExtCount}
          customization={customization}
          zapperMode={zapperMode}
          showDevTools={showDevTools}
          hasSplitView={Boolean(splitTab)}
          canGoForward={activeTab.historyIndex < activeTab.history.length - 1}
          onClose={() => setShowMenuHub(false)}
          onSelectSearchEngine={(id) =>
            setCustomization((prev) => ({ ...prev, searchEngine: id }))
          }
          onNewTab={(incog) => handleNewTab(incog)}
          onGoHome={() => navigateTab(activeTab.id, 'browserhub://start')}
          onReload={handleReload}
          onGoForward={handleGoForward}
          onToggleDesktop={() =>
            updateTab(activeTab.id, (t) => ({ ...t, isDesktopMode: !t.isDesktopMode }))
          }
          onToggleReader={() =>
            updateTab(activeTab.id, (t) => ({ ...t, isReaderMode: !t.isReaderMode }))
          }
          onOpenTranslate={() => setShowTranslateModal(true)}
          onOpenVideoGrabber={() => setShowVideoGrabber(true)}
          onOpenFind={() => setShowFindBar(true)}
          onToggleSplit={() => {
            if (splitTabId) {
              setSplitTabId(null);
            } else {
              const other = tabs.find((t) => t.id !== activeTab.id);
              if (other) {
                setSplitTabId(other.id);
              } else {
                const newId = `tab-${Date.now()}`;
                const secondTab: BrowserTab = {
                  ...activeTab,
                  id: newId,
                  title: 'New Tab',
                  url: 'browserhub://start',
                  history: ['browserhub://start'],
                  historyIndex: 0,
                };
                setTabs((prev) => [...prev, secondTab]);
                setSplitTabId(newId);
              }
            }
          }}
          onChangeZoom={(nextZoom) =>
            updateTab(activeTab.id, (t) => ({ ...t, zoomLevel: nextZoom }))
          }
          onOpenShields={() => setShowShields(true)}
          onOpenVpn={() => setShowVpn(true)}
          onToggleZapper={() => setZapperMode((v) => !v)}
          onFireWipe={handleFireBurnAll}
          onOpenExtensions={() => setShowExtensions(true)}
          onOpenCustomize={() => setShowCustomize(true)}
          onToggleDevTools={() => setShowDevTools((v) => !v)}
          onOpenBenchmark={() => navigateTab(activeTab.id, 'browserhub://benchmark')}
          onOpenGames={() => navigateTab(activeTab.id, 'browserhub://games')}
          onOpenAbout={() => setShowAboutModal(true)}
          onOpenAi={(mode) => setAiDrawerState({ open: true, mode })}
          onToggleToolbarPosition={() =>
            setCustomization((prev) => ({
              ...prev,
              addressBarPosition: prev.addressBarPosition === 'top' ? 'bottom' : 'top',
            }))
          }
        />
      )}

      {/* Dedicated Clean Page Translator Modal */}
      {showTranslateModal && (
        <TranslateModal
          activeLang={activeTab.translatedLang}
          bilingual={activeTab.bilingualTranslate}
          onSelectLang={(code) =>
            updateTab(activeTab.id, (t) => ({ ...t, translatedLang: code }))
          }
          onToggleBilingual={(val) =>
            updateTab(activeTab.id, (t) => ({ ...t, bilingualTranslate: val }))
          }
          onClose={() => setShowTranslateModal(false)}
        />
      )}

      {showVideoGrabber && (
        <VideoGrabberModal
          pageUrl={activeTab.url}
          pageTitle={activeTab.title}
          onClose={() => setShowVideoGrabber(false)}
        />
      )}

      {showShields && (
        <ShieldsPanel
          tab={activeTab}
          shields={shields}
          zapperMode={zapperMode}
          onUpdateShields={(patch) => setShields((prev) => ({ ...prev, ...patch }))}
          onToggleZapper={() => setZapperMode((v) => !v)}
          onResetZapped={() =>
            updateTab(activeTab.id, (t) => ({ ...t, zappedSelectors: [] }))
          }
          onClose={() => setShowShields(false)}
        />
      )}

      {showVpn && (
        <VpnModal
          vpn={vpn}
          onUpdateVpn={(patch) => setVpn((prev) => ({ ...prev, ...patch }))}
          onClose={() => setShowVpn(false)}
        />
      )}

      {showExtensions && (
        <ExtensionsModal
          extensions={extensions}
          onNavigate={(u) => {
            setShowExtensions(false);
            navigateTab(activeTab.id, u);
          }}
          onToggleExtension={(id) =>
            setExtensions((prev) =>
              prev.map((e) => (e.id === id ? { ...e, enabled: !e.enabled } : e))
            )
          }
          onToggleIncognitoExt={(id) =>
            setExtensions((prev) =>
              prev.map((e) =>
                e.id === id ? { ...e, allowIncognito: !e.allowIncognito } : e
              )
            )
          }
          onInstallExtension={(ext) =>
            setExtensions((prev) => [ext, ...prev.filter((e) => e.id !== ext.id)])
          }
          onRemoveExtension={(id) =>
            setExtensions((prev) => prev.filter((e) => e.id !== id))
          }
          onClose={() => setShowExtensions(false)}
        />
      )}

      {aiDrawerState.open && (
        <AiCopilotDrawer
          tab={activeTab}
          livePageText={livePageTexts[activeTab.id]}
          searchEngineId={customization.searchEngine}
          aiEngine={customization.aiEngine}
          onSelectAiEngine={(id) =>
            setCustomization((prev) => ({ ...prev, aiEngine: id }))
          }
          initialMode={aiDrawerState.mode}
          initialQuery={aiDrawerState.query}
          onClose={() => setAiDrawerState({ open: false })}
        />
      )}

      {showTabs && (
        <TabsDrawer
          tabs={tabs}
          activeTabId={activeTab.id}
          splitTabId={splitTabId}
          biometricLockIncognito={customization.biometricLockIncognito}
          onSelectTab={(id) => setActiveTabId(id)}
          onNewTab={handleNewTab}
          onCloseTab={handleCloseTab}
          onTogglePin={(id) =>
            updateTab(id, (t) => ({ ...t, isPinned: !t.isPinned }))
          }
          onToggleHibernate={(id) =>
            updateTab(id, (t) => ({ ...t, isHibernated: !t.isHibernated }))
          }
          onToggleSplitTab={(id) =>
            setSplitTabId((prev) => (prev === id ? null : id))
          }
          onFireBurnAll={handleFireBurnAll}
          onClose={() => setShowTabs(false)}
        />
      )}

      {showCustomize && (
        <CustomizeDrawer
          customization={customization}
          downloads={downloads}
          onUpdateCustomization={(patch) =>
            setCustomization((prev) => ({ ...prev, ...patch }))
          }
          onOpenDevTools={() => setShowDevTools(true)}
          onClose={() => setShowCustomize(false)}
        />
      )}

      {showAboutModal && <AboutDiagnosticsModal onClose={() => setShowAboutModal(false)} />}

      {/*
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
}

export default App;
