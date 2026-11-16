import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  RotateCw,
  ShieldCheck,
  ShieldOff,
  Lock,
  Sparkles,
  Puzzle,
  Monitor,
  Smartphone,
  BookOpen,
  Languages,
  Flame,
  SlidersHorizontal,
  Search,
  X,
  Columns2,
  Crosshair,
  PictureInPicture2,
  Wifi,
  BatteryCharging,
  ZoomIn,
  ZoomOut,
  Download,
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
  GLOBAL_VPN_NODES,
  INITIAL_EXTENSIONS,
  THEME_PRESETS,
} from './data/presets';
import { SUPPORTED_LANGUAGES } from './utils/translator';
import { WebViewport } from './components/WebViewport';
import { ShieldsPanel } from './components/ShieldsPanel';
import { VpnModal } from './components/VpnModal';
import { ExtensionsModal } from './components/ExtensionsModal';
import { AiCopilotDrawer } from './components/AiCopilotDrawer';
import { TabsDrawer } from './components/TabsDrawer';
import { CustomizeDrawer } from './components/CustomizeDrawer';
import { DevToolsPanel } from './components/DevToolsPanel';
import { PlayStoreDownloadModal } from './components/PlayStoreDownloadModal';
import { AndroidHybridBridge } from './bridge/androidHybridBridge';

function deriveTitleFromUrl(url: string): string {
  if (url === 'browserhub://start') return 'BrowserHub Start';
  if (url === 'browserhub://benchmark') return '100/100 Speed & Privacy Benchmark';
  if (url === 'browserhub://architecture') return 'Hybrid Android Architecture';
  if (url.includes('techpulse.news')) return 'TechPulse — Hybrid AI & Privacy Browsers';
  if (url.includes('youtube.com')) return 'StreamTube 4K — 120Hz Browser Engineering';
  if (url.includes('news.ycombinator.com')) return 'Hacker News';
  if (url.includes('wikipedia.org')) return 'Web browser — Wikipedia';
  if (url.startsWith('gh-search:')) return `GitHub Search: ${url.replace('gh-search:', '')}`;
  try {
    const u = new URL(url.startsWith('http') ? url : `https://${url}`);
    return u.hostname + (u.pathname !== '/' ? u.pathname : '');
  } catch {
    return url;
  }
}

function cleanTrackingUrl(raw: string, stripEnabled: boolean): { url: string; strippedCount: number } {
  if (!stripEnabled || !raw.includes('?')) return { url: raw, strippedCount: 0 };
  try {
    const u = new URL(raw.startsWith('http') ? raw : `https://${raw}`);
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

export function App() {
  // Core Tabs State
  const [tabs, setTabs] = useState<BrowserTab[]>([
    {
      id: 'tab-1',
      title: 'BrowserHub Start',
      url: 'browserhub://start',
      isIncognito: false,
      isPinned: true,
      isDesktopMode: false,
      isReaderMode: false,
      isHibernated: false,
      groupId: 'main',
      history: ['browserhub://start'],
      historyIndex: 0,
      adsBlocked: 12,
      trackersBlocked: 29,
      bytesSavedKb: 840,
      translatedLang: null,
      bilingualTranslate: false,
      zappedSelectors: [],
      zoomLevel: 100,
      memoryMb: 42,
    },
    {
      id: 'tab-2',
      title: 'TechPulse — Hybrid AI & Privacy Browsers',
      url: 'https://techpulse.news/ai-privacy-browsers-2026',
      isIncognito: false,
      isPinned: false,
      isDesktopMode: false,
      isReaderMode: false,
      isHibernated: false,
      groupId: 'research',
      history: ['https://techpulse.news/ai-privacy-browsers-2026'],
      historyIndex: 0,
      adsBlocked: 7,
      trackersBlocked: 14,
      bytesSavedKb: 1420,
      translatedLang: null,
      bilingualTranslate: false,
      zappedSelectors: [],
      zoomLevel: 100,
      memoryMb: 54,
    },
    {
      id: 'tab-3',
      title: 'StreamTube 4K — 120Hz Browser Engineering',
      url: 'https://www.youtube.com/watch?v=bh-100x',
      isIncognito: false,
      isPinned: false,
      isDesktopMode: false,
      isReaderMode: false,
      isHibernated: true,
      groupId: 'media',
      history: ['https://www.youtube.com/watch?v=bh-100x'],
      historyIndex: 0,
      adsBlocked: 4,
      trackersBlocked: 11,
      bytesSavedKb: 2680,
      translatedLang: null,
      bilingualTranslate: false,
      zappedSelectors: [],
      zoomLevel: 100,
      memoryMb: 68,
    },
  ]);

  const [activeTabId, setActiveTabId] = useState('tab-1');
  const [splitTabId, setSplitTabId] = useState<string | null>(null);
  const [omniboxText, setOmniboxText] = useState('browserhub://start');
  const [omniboxFocused, setOmniboxFocused] = useState(false);

  // Shields, VPN, Extensions, Customization
  const [shields, setShields] = useState<ShieldState>({
    enabled: true,
    blockAds: true,
    blockTrackers: true,
    blockCookieBanners: true,
    stripTrackingParams: true,
    antiFingerprinting: true,
    httpsEverywhere: true,
    blockCryptominers: true,
    turboDataSaver: true,
    totalAdsBlocked: 1428,
    totalTrackersBlocked: 4912,
    totalDataSavedMb: 186.4,
    totalTimeSavedSec: 94,
  });

  const [vpn, setVpn] = useState<VpnState>({
    connected: !AndroidHybridBridge.hasJniBridge(),
    connecting: false,
    selectedNodeId: 'ch-zurich',
    protocol: 'WireGuard',
    dohProvider: 'mullvad',
    killSwitch: true,
    blockWebRtcLeak: true,
    splitTunnelBypassLocal: false,
    encryptedMb: 42.8,
  });

  const [extensions, setExtensions] = useState<BrowserExtension[]>(INITIAL_EXTENSIONS);

  const [customization, setCustomization] = useState<BrowserCustomization>({
    theme: 'cyber-cyan',
    customAccentHex: '#22d3ee',
    addressBarPosition: 'bottom',
    engine: 'hybrid-auto',
    searchEngine: 'duckduckgo',
    phoneFrameMode: false,
    uiDensity: 'comfortable',
    showBookmarksBar: true,
    showVirtualCursorInDesktop: true,
    biometricLockIncognito: true,
    readerFont: 'serif',
    readerTheme: 'amoled',
    readerFontSize: 17,
  });

  const [downloads, setDownloads] = useState<DownloadItem[]>([
    {
      id: 'dl-init-1',
      filename: 'uBlock0_1.62.0.firefox.signed.xpi',
      url: 'https://addons.mozilla.org/ublock-origin',
      sizeMb: 3.8,
      downloadedMb: 3.8,
      threads: 16,
      speedMbps: 28.4,
      status: 'completed',
      category: 'extension',
    },
  ]);

  // Modals & Interactive States
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
  const [showTranslateMenu, setShowTranslateMenu] = useState(false);
  const [showDevTools, setShowDevTools] = useState(false);
  const [showFindBar, setShowFindBar] = useState(false);
  const [findQuery, setFindQuery] = useState('');
  const [zapperMode, setZapperMode] = useState(false);
  const [fireSweepAnim, setFireSweepAnim] = useState(false);
  const [pipTitle, setPipTitle] = useState<string | null>(null);
  const [livePageTexts, setLivePageTexts] = useState<Record<string, string>>({});
  const [showPlayStoreModal, setShowPlayStoreModal] = useState(false);
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
  const activeVpnNode =
    GLOBAL_VPN_NODES.find((n) => n.id === vpn.selectedNodeId) || GLOBAL_VPN_NODES[0];

  useEffect(() => {
    if (!omniboxFocused) {
      setOmniboxText(activeTab.url);
    }
  }, [activeTab.url, omniboxFocused]);

  const anyModalOpen =
    showShields ||
    showVpn ||
    showExtensions ||
    aiDrawerState.open ||
    showTabs ||
    showCustomize ||
    showTranslateMenu ||
    showPlayStoreModal;

  const isNativeRealWebUrl = (u: string) => {
    if (!AndroidHybridBridge.hasJniBridge()) return false;
    if (!u.startsWith('http://') && !u.startsWith('https://')) return false;
    if (
      u.includes('techpulse.news') ||
      u.includes('search.browserhub.local') ||
      u.includes('youtube.com/watch?v=bh-100x')
    ) {
      return false;
    }
    return true;
  };

  // Sync real Android Native WebView viewport & live callbacks when installed as APK!
  useEffect(() => {
    if (!AndroidHybridBridge.hasJniBridge() || !window.BrowserHubNative) return;

    window.BrowserHubNative.setIncognitoLock?.(activeTab.isIncognito);

    const shouldShowNativeWeb =
      isNativeRealWebUrl(activeTab.url) &&
      !anyModalOpen &&
      !activeTab.isReaderMode &&
      !splitTab &&
      !omniboxFocused;

    const el = viewportContainerRef.current;
    const rect = el ? el.getBoundingClientRect() : { top: 56, bottom: window.innerHeight - 64 };
    const topCss = Math.max(0, Math.round(rect.top));
    const bottomCss = Math.max(0, Math.round(window.innerHeight - rect.bottom));

    const darkReaderOn = extensions.some((e) => e.enabled && e.effectKey === 'dark-reader');
    const cookieKillerOn =
      (shields.enabled && shields.blockCookieBanners) ||
      extensions.some((e) => e.enabled && e.effectKey === 'cookie-killer');
    const adblockOn =
      (shields.enabled && shields.blockAds) ||
      extensions.some((e) => e.enabled && e.effectKey === 'ublock');
    const customCss = extensions
      .filter((e) => e.enabled && e.customCss)
      .map((e) => e.customCss)
      .join('\n');

    window.BrowserHubNative.syncWebViewport?.(
      activeTab.url,
      topCss,
      bottomCss,
      Math.max(1, window.innerHeight),
      activeTab.isDesktopMode,
      adblockOn,
      darkReaderOn,
      cookieKillerOn,
      customCss,
      activeTab.zoomLevel,
      showFindBar ? findQuery : '',
      shouldShowNativeWeb
    );
  }, [
    activeTab.url,
    activeTab.isDesktopMode,
    activeTab.isIncognito,
    activeTab.isReaderMode,
    activeTab.zoomLevel,
    anyModalOpen,
    omniboxFocused,
    splitTab,
    shields.enabled,
    shields.blockAds,
    shields.blockCookieBanners,
    extensions,
    showFindBar,
    findQuery,
    showDevTools,
    customization.addressBarPosition,
  ]);

  useEffect(() => {
    if (!AndroidHybridBridge.hasJniBridge()) return;

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

    window.__onNativeAdBlocked = () => {
      setTabs((prev) =>
        prev.map((t) =>
          t.id === activeTabId
            ? { ...t, adsBlocked: t.adsBlocked + 1, bytesSavedKb: t.bytesSavedKb + 38 }
            : t
        )
      );
      setShields((prev) => ({
        ...prev,
        totalAdsBlocked: prev.totalAdsBlocked + 1,
        totalDataSavedMb: Number((prev.totalDataSavedMb + 0.04).toFixed(2)),
      }));
    };

    window.__onAndroidBackPressed = () => {
      if (anyModalOpen) {
        setShowShields(false);
        setShowVpn(false);
        setShowExtensions(false);
        setAiDrawerState({ open: false });
        setShowTabs(false);
        setShowCustomize(false);
        setShowTranslateMenu(false);
        setShowPlayStoreModal(false);
        return true;
      }
      if (window.BrowserHubNative?.nativeGoBack?.()) {
        return true;
      }
      return false;
    };
  }, [activeTabId, anyModalOpen]);

  // Animate active downloads with 16-thread acceleration!
  useEffect(() => {
    const hasActive = downloads.some((d) => d.status === 'downloading');
    if (!hasActive) return;
    const interval = setInterval(() => {
      setDownloads((prev) =>
        prev.map((d) => {
          if (d.status !== 'downloading') return d;
          const nextMb = Math.min(d.sizeMb, Number((d.downloadedMb + d.speedMbps * 0.45).toFixed(1)));
          return {
            ...d,
            downloadedMb: nextMb,
            status: nextMb >= d.sizeMb ? 'completed' : 'downloading',
          };
        })
      );
    }, 350);
    return () => clearInterval(interval);
  }, [downloads]);

  // Omnibox Instant Calculator Preview
  const instantMathResult = useMemo(() => {
    const expr = omniboxText.trim();
    if (!/^[0-9+\-*/().\s%]{3,}$/.test(expr) || !/[+\-*/%]/.test(expr)) return null;
    try {
      const val = Function(`"use strict"; return (${expr});`)();
      return typeof val === 'number' && Number.isFinite(val) ? String(Number(val.toFixed(6))) : null;
    } catch {
      return null;
    }
  }, [omniboxText]);

  // Dynamic Network & Shields Logs for DevTools
  const networkLogs = useMemo<NetworkLogEntry[]>(
    () => [
      {
        id: 'n1',
        domain: activeTab.url.slice(0, 38),
        url: activeTab.url,
        type: 'document',
        status: 'allowed',
        latencyMs: 19,
        sizeKb: 42,
        timestamp: 'now',
      },
      {
        id: 'n2',
        domain: 'pagead2.googlesyndication.com',
        url: 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js',
        type: 'ad-banner',
        status: shields.enabled && shields.blockAds ? 'blocked' : 'allowed',
        ruleMatched: shields.enabled && shields.blockAds ? '||googlesyndication.com^$3p' : undefined,
        latencyMs: 0,
        sizeKb: 418,
        timestamp: 'now',
      },
      {
        id: 'n3',
        domain: 'bat.bing.com / analytics.js',
        url: 'https://bat.bing.com/bat.js',
        type: 'tracker',
        status: shields.enabled && shields.blockTrackers ? 'blocked' : 'allowed',
        ruleMatched: shields.enabled && shields.blockTrackers ? 'EasyPrivacy: ||bat.bing.com^' : undefined,
        latencyMs: 0,
        sizeKb: 96,
        timestamp: 'now',
      },
      {
        id: 'n4',
        domain: `doh.${vpn.dohProvider}.dns`,
        url: `https://doh.${vpn.dohProvider}.dns/dns-query`,
        type: 'xhr',
        status: 'allowed',
        latencyMs: 8,
        sizeKb: 2,
        timestamp: 'now',
      },
    ],
    [activeTab.url, shields.enabled, shields.blockAds, shields.blockTrackers, vpn.dohProvider]
  );

  const updateTab = (tabId: string, updater: (t: BrowserTab) => BrowserTab) => {
    setTabs((prev) => prev.map((t) => (t.id === tabId ? updater(t) : t)));
  };

  const navigateTab = (targetTabId: string, rawInput: string) => {
    let targetUrl = rawInput.trim();
    if (!targetUrl) return;

    const isProtocol =
      targetUrl.startsWith('http://') ||
      targetUrl.startsWith('https://') ||
      targetUrl.startsWith('browserhub://') ||
      targetUrl.startsWith('gh-search:');
    const looksLikeDomain =
      !targetUrl.includes(' ') &&
      /\.[a-z]{2,}(\/|$)/i.test(targetUrl);

    if (!isProtocol) {
      if (looksLikeDomain) {
        targetUrl = `https://${targetUrl}`;
      } else if (AndroidHybridBridge.hasJniBridge()) {
        const q = encodeURIComponent(targetUrl);
        if (customization.searchEngine === 'brave') {
          targetUrl = `https://search.brave.com/search?q=${q}`;
        } else if (customization.searchEngine === 'startpage') {
          targetUrl = `https://www.startpage.com/sp/search?query=${q}`;
        } else if (customization.searchEngine === 'google') {
          targetUrl = `https://www.google.com/search?q=${q}`;
        } else if (customization.searchEngine === 'github-live') {
          targetUrl = `https://github.com/search?q=${q}&type=repositories`;
        } else {
          targetUrl = `https://duckduckgo.com/?q=${q}`;
        }
      } else if (customization.searchEngine === 'github-live') {
        targetUrl = `gh-search:${targetUrl}`;
      } else {
        targetUrl = `https://search.browserhub.local/?q=${encodeURIComponent(targetUrl)}`;
      }
    }

    const cleaned = cleanTrackingUrl(
      targetUrl,
      shields.enabled && shields.stripTrackingParams
    );
    targetUrl = cleaned.url;

    const newTitle = deriveTitleFromUrl(targetUrl);
    const blockedAdsDelta = shields.enabled && shields.blockAds ? 4 : 0;
    const blockedTrackersDelta =
      (shields.enabled && shields.blockTrackers ? 7 : 0) + cleaned.strippedCount;

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
        adsBlocked: t.adsBlocked + blockedAdsDelta,
        trackersBlocked: t.trackersBlocked + blockedTrackersDelta,
        bytesSavedKb: t.bytesSavedKb + (blockedAdsDelta > 0 ? 520 : 0),
      };
    });

    if (blockedAdsDelta + blockedTrackersDelta > 0) {
      setShields((prev) => ({
        ...prev,
        totalAdsBlocked: prev.totalAdsBlocked + blockedAdsDelta,
        totalTrackersBlocked: prev.totalTrackersBlocked + blockedTrackersDelta,
        totalDataSavedMb: Number((prev.totalDataSavedMb + 0.5).toFixed(1)),
        totalTimeSavedSec: prev.totalTimeSavedSec + 1,
      }));
    }

    if (vpn.connected) {
      setVpn((prev) => ({
        ...prev,
        encryptedMb: Number((prev.encryptedMb + 0.6).toFixed(1)),
      }));
    }
  };

  const handleGoBack = () => {
    if (isNativeRealWebUrl(activeTab.url) && window.BrowserHubNative?.nativeGoBack?.()) {
      return;
    }
    if (activeTab.historyIndex <= 0) return;
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

  const handleNewTab = (incognito: boolean, groupId: BrowserTab['groupId'] = 'main') => {
    const id = `tab-${Date.now()}`;
    const newTab: BrowserTab = {
      id,
      title: incognito ? 'Incognito Vault Start' : 'BrowserHub Start',
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
          title: 'BrowserHub Start',
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
    setTimeout(() => setFireSweepAnim(false), 1200);
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

  // Address & Control Toolbar Component (renders at Bottom OR Top!)
  const AddressToolbar = (
    <div
      style={{
        backgroundColor: activeTab.isIncognito ? '#160926' : themeStyle.bgBar,
        borderColor: themeStyle.border,
      }}
      className={`px-2.5 py-2 border-y flex flex-col gap-1.5 shrink-0 z-20 transition-colors`}
    >
      {/* Instant Calculator Result Banner */}
      {instantMathResult && (
        <div className="px-3 py-1 rounded-xl bg-cyan-500/15 border border-cyan-500/30 text-xs flex items-center justify-between text-cyan-200">
          <span>Instant Omnibox Calculator:</span>
          <strong className="font-mono text-sm text-emerald-300">= {instantMathResult}</strong>
        </div>
      )}

      {/* Element Zapper Active Banner */}
      {zapperMode && (
        <div className="px-3 py-1.5 rounded-xl bg-rose-500/20 border border-rose-500/50 text-xs flex items-center justify-between text-rose-200">
          <span className="inline-flex items-center gap-1.5 font-semibold">
            <Crosshair className="w-3.5 h-3.5 text-rose-400 animate-spin" />
            Element Zapper Active — Tap any block on the page to vaporize it!
          </span>
          <button
            onClick={() => setZapperMode(false)}
            className="px-2 py-0.5 rounded bg-rose-500 text-white font-bold text-[11px] cursor-pointer"
          >
            Done
          </button>
        </div>
      )}

      {/* Main Omnibox Row */}
      <div className="flex items-center gap-1.5">
        <button
          onClick={handleGoBack}
          disabled={activeTab.historyIndex <= 0}
          className="p-2 rounded-xl hover:bg-white/10 disabled:opacity-30 text-slate-200 cursor-pointer"
          title="Back"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>

        <button
          onClick={handleGoForward}
          disabled={activeTab.historyIndex >= activeTab.history.length - 1}
          className="hidden sm:inline-flex p-2 rounded-xl hover:bg-white/10 disabled:opacity-30 text-slate-200 cursor-pointer"
          title="Forward"
        >
          <ArrowRight className="w-4 h-4" />
        </button>

        <button
          onClick={() => navigateTab(activeTab.id, activeTab.url)}
          className="p-2 rounded-xl hover:bg-white/10 text-slate-200 cursor-pointer"
          title="Reload"
        >
          <RotateCw className="w-4 h-4" />
        </button>

        {/* Omnibox Pill */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            navigateTab(activeTab.id, omniboxText);
            (document.activeElement as HTMLElement | null)?.blur();
          }}
          className="flex-1 flex items-center gap-1.5 rounded-2xl bg-black/45 border border-white/12 focus-within:border-cyan-400 px-2.5 py-1.5 min-w-0"
        >
          {/* Shields Button + Counter */}
          <button
            type="button"
            onClick={() => setShowShields(true)}
            title="Direct Ad Blocker & Anti-Fingerprinting Shields"
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-[11px] font-bold shrink-0 cursor-pointer ${
              shields.enabled
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                : 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
            }`}
          >
            {shields.enabled ? (
              <ShieldCheck className="w-3.5 h-3.5" />
            ) : (
              <ShieldOff className="w-3.5 h-3.5" />
            )}
            <span>{activeTab.adsBlocked + activeTab.trackersBlocked}</span>
          </button>

          <input
            value={omniboxText}
            onFocus={(e) => {
              setOmniboxFocused(true);
              e.currentTarget.select();
            }}
            onBlur={() => setOmniboxFocused(false)}
            onChange={(e) => setOmniboxText(e.target.value)}
            placeholder="Search or type URL..."
            className="flex-1 bg-transparent text-xs sm:text-sm text-white placeholder-slate-400 focus:outline-none min-w-0"
          />

          {/* Reader Mode Quick Button */}
          {activeTab.url !== 'browserhub://start' && (
            <button
              type="button"
              onClick={() =>
                updateTab(activeTab.id, (t) => ({ ...t, isReaderMode: !t.isReaderMode }))
              }
              title="Toggle Reader View & TTS"
              className={`p-1 rounded-lg cursor-pointer ${
                activeTab.isReaderMode ? 'bg-cyan-500 text-slate-950' : 'text-slate-400 hover:text-white'
              }`}
            >
              <BookOpen className="w-3.5 h-3.5" />
            </button>
          )}

          {/* 1-Tap Translate Menu Trigger */}
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowTranslateMenu((v) => !v)}
              title="Zero-Key Local Page Translator (14 Languages)"
              className={`p-1 rounded-lg cursor-pointer ${
                activeTab.translatedLang
                  ? 'bg-indigo-500 text-white'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Languages className="w-3.5 h-3.5" />
            </button>

            {showTranslateMenu && (
              <div
                className={`absolute right-0 ${
                  customization.addressBarPosition === 'bottom' ? 'bottom-9' : 'top-9'
                } w-64 rounded-2xl bg-slate-950 border border-slate-700 shadow-2xl p-2.5 z-50 space-y-2`}
              >
                <div className="flex items-center justify-between text-[11px] font-bold text-slate-300 px-1">
                  <span>Local Neural Translate</span>
                  {activeTab.translatedLang && (
                    <button
                      type="button"
                      onClick={() => {
                        updateTab(activeTab.id, (t) => ({ ...t, translatedLang: null }));
                        setShowTranslateMenu(false);
                      }}
                      className="text-rose-400 hover:underline cursor-pointer"
                    >
                      Original
                    </button>
                  )}
                </div>

                <label className="flex items-center justify-between px-2 py-1 rounded-lg bg-slate-900 text-[11px] text-slate-300 cursor-pointer">
                  <span>Bilingual Side-by-Side</span>
                  <input
                    type="checkbox"
                    checked={activeTab.bilingualTranslate}
                    onChange={(e) =>
                      updateTab(activeTab.id, (t) => ({
                        ...t,
                        bilingualTranslate: e.target.checked,
                      }))
                    }
                  />
                </label>

                <div className="grid grid-cols-2 gap-1 max-h-48 overflow-y-auto">
                  {SUPPORTED_LANGUAGES.map((lang) => (
                    <button
                      key={lang.code}
                      type="button"
                      onClick={() => {
                        updateTab(activeTab.id, (t) => ({ ...t, translatedLang: lang.code }));
                        setShowTranslateMenu(false);
                      }}
                      className={`px-2 py-1.5 rounded-xl text-left text-[11px] flex items-center gap-1.5 cursor-pointer ${
                        activeTab.translatedLang === lang.code
                          ? 'bg-indigo-500 text-white font-bold'
                          : 'hover:bg-slate-900 text-slate-200'
                      }`}
                    >
                      <span>{lang.flag}</span>
                      <span className="truncate">{lang.nativeName}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </form>

        {/* Desktop Mode 1-Tap Toggle */}
        <button
          onClick={() =>
            updateTab(activeTab.id, (t) => ({ ...t, isDesktopMode: !t.isDesktopMode }))
          }
          title="Toggle Full Desktop Mode"
          className={`p-2 rounded-xl border transition cursor-pointer ${
            activeTab.isDesktopMode
              ? 'bg-cyan-500/20 border-cyan-400 text-cyan-300'
              : 'border-transparent hover:bg-white/10 text-slate-300'
          }`}
        >
          {activeTab.isDesktopMode ? (
            <Monitor className="w-4 h-4" />
          ) : (
            <Smartphone className="w-4 h-4" />
          )}
        </button>

        {/* Built-In VPN Pill */}
        <button
          onClick={() => setShowVpn(true)}
          title="Built-in Zero-Log VPN (WireGuard / Multi-Hop / Tor)"
          className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl text-xs font-bold border transition cursor-pointer ${
            vpn.connected
              ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300'
              : 'bg-slate-800/80 border-slate-700 text-slate-400'
          }`}
        >
          <Lock className="w-3.5 h-3.5" />
          <span className="hidden md:inline">{vpn.connected ? activeVpnNode.flag : 'VPN'}</span>
        </button>

        {/* Universal Extensions Button */}
        <button
          onClick={() => setShowExtensions(true)}
          title="Universal Extensions (.CRX / .XPI / Userscripts)"
          className="relative p-2 rounded-xl hover:bg-white/10 text-purple-300 cursor-pointer"
        >
          <Puzzle className="w-4 h-4" />
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 rounded-full bg-purple-500 text-[9px] font-bold text-white flex items-center justify-center">
            {extensions.filter((e) => e.enabled).length}
          </span>
        </button>

        {/* Zero-Key Local AI Copilot */}
        <button
          onClick={() => setAiDrawerState({ open: true, mode: 'summarize' })}
          title="Zero-Key Private Local AI Copilot"
          className="p-2 rounded-xl bg-gradient-to-br from-cyan-500/25 to-emerald-500/25 border border-cyan-400/40 text-cyan-200 hover:opacity-90 cursor-pointer"
        >
          <Sparkles className="w-4 h-4" />
        </button>

        {/* Tabs Switcher Counter */}
        <button
          onClick={() => setShowTabs(true)}
          title="Tabs, Incognito Vault & Split-Screen"
          className={`px-2.5 py-1 rounded-xl border-2 text-xs font-extrabold cursor-pointer ${
            activeTab.isIncognito
              ? 'border-purple-400 text-purple-200 bg-purple-950/60'
              : 'border-slate-400 text-white hover:border-cyan-400'
          }`}
        >
          {tabs.length}
        </button>

        {/* DuckDuckGo Style Fire Button */}
        <button
          onClick={handleFireBurnAll}
          title="Fire Button: Instant Session & Tracker Incineration"
          className="p-2 rounded-xl hover:bg-orange-500/20 text-orange-400 cursor-pointer"
        >
          <Flame className="w-4 h-4" />
        </button>

        {/* Customize & Power Menu */}
        <button
          onClick={() => setShowCustomize(true)}
          title="Theme Studio, Engine, Downloads & DevTools"
          className="p-2 rounded-xl hover:bg-white/10 text-slate-200 cursor-pointer"
        >
          <SlidersHorizontal className="w-4 h-4" />
        </button>
      </div>

      {/* Compact Utility Sub-Strip (Quick Find, Zoom, Split Indicator, Phone Bezel Toggle) */}
      <div className="flex items-center justify-between text-[11px] text-slate-400 px-1 pt-0.5">
        <div className="flex items-center gap-2 overflow-x-auto">
          <button
            onClick={() => setShowPlayStoreModal(true)}
            className="px-2 py-0.5 rounded-md bg-emerald-500/20 border border-emerald-400/40 text-emerald-300 font-bold inline-flex items-center gap-1 hover:bg-emerald-500/30 cursor-pointer shrink-0"
          >
            <Download className="w-3 h-3" /> Download / Play Store
          </button>
          <span className="opacity-30">•</span>
          <button
            onClick={() => setShowFindBar((v) => !v)}
            className="hover:text-cyan-300 inline-flex items-center gap-1 cursor-pointer shrink-0"
          >
            <Search className="w-3 h-3" /> Find in Page
          </button>
          <span className="opacity-30">•</span>
          <button
            onClick={() => setZapperMode((v) => !v)}
            className={`inline-flex items-center gap-1 cursor-pointer shrink-0 ${
              zapperMode ? 'text-rose-400 font-bold' : 'hover:text-rose-300'
            }`}
          >
            <Crosshair className="w-3 h-3" /> Element Zapper
          </button>
          <span className="opacity-30">•</span>
          <button
            onClick={() => setShowDevTools((v) => !v)}
            className="hover:text-purple-300 cursor-pointer shrink-0"
          >
            {showDevTools ? 'Hide DevTools' : 'DevTools'}
          </button>
          {splitTab && (
            <>
              <span className="opacity-30">•</span>
              <button
                onClick={() => setSplitTabId(null)}
                className="text-purple-300 font-semibold inline-flex items-center gap-1 cursor-pointer shrink-0"
              >
                <Columns2 className="w-3 h-3" /> Exit Split View
              </button>
            </>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() =>
              updateTab(activeTab.id, (t) => ({
                ...t,
                zoomLevel: Math.max(75, t.zoomLevel - 10),
              }))
            }
            className="hover:text-white cursor-pointer"
            title="Zoom Out"
          >
            <ZoomOut className="w-3 h-3" />
          </button>
          <span className="font-mono text-[10px]">{activeTab.zoomLevel}%</span>
          <button
            onClick={() =>
              updateTab(activeTab.id, (t) => ({
                ...t,
                zoomLevel: Math.min(150, t.zoomLevel + 10),
              }))
            }
            className="hover:text-white cursor-pointer"
            title="Zoom In"
          >
            <ZoomIn className="w-3 h-3" />
          </button>
          <button
            onClick={() =>
              setCustomization((prev) => ({
                ...prev,
                phoneFrameMode: !prev.phoneFrameMode,
              }))
            }
            className="ml-1 px-2 py-0.5 rounded bg-white/8 hover:bg-white/15 text-cyan-300 text-[10px] font-semibold cursor-pointer"
          >
            {customization.phoneFrameMode ? 'Full Screen' : 'Phone Frame'}
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div
      style={{ backgroundColor: themeStyle.bgShell }}
      className={`h-full w-full flex items-center justify-center overflow-hidden ${
        customization.phoneFrameMode ? 'p-2 sm:p-4 bg-slate-950' : ''
      }`}
    >
      {/* Outer Shell (Either Fullscreen Responsive OR Realistic Android 15 Flagship Bezel) */}
      <div
        style={{ backgroundColor: themeStyle.bgShell }}
        className={`flex flex-col overflow-hidden relative transition-all ${
          customization.phoneFrameMode
            ? 'w-full max-w-[430px] h-full max-h-[900px] rounded-[2.5rem] border-[6px] border-slate-800 shadow-2xl shadow-cyan-950/50'
            : 'w-full h-full'
        }`}
      >
        {/* Android 15 Status Bar when in Phone Frame Mode */}
        {customization.phoneFrameMode && (
          <div className="px-5 py-1.5 bg-black/80 text-[11px] text-slate-300 flex items-center justify-between shrink-0 select-none">
            <div className="flex items-center gap-2 font-semibold">
              <span>12:00</span>
              <span className="px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-300 text-[9px]">
                120Hz
              </span>
              {vpn.connected && (
                <span className="text-emerald-400 text-[10px]">🔑 {activeVpnNode.flag}</span>
              )}
            </div>
            <div className="w-16 h-3.5 rounded-full bg-black border border-slate-800" />
            <div className="flex items-center gap-1.5">
              <Wifi className="w-3.5 h-3.5 text-emerald-400" />
              <BatteryCharging className="w-4 h-4 text-emerald-400" />
            </div>
          </div>
        )}

        {/* Top Bar Placement */}
        {customization.addressBarPosition === 'top' && AddressToolbar}

        {/* Optional Find-in-Page Bar */}
        {showFindBar && (
          <div className="px-3 py-1.5 bg-slate-900 border-b border-slate-800 flex items-center gap-2 shrink-0 z-20">
            <Search className="w-3.5 h-3.5 text-amber-400" />
            <input
              autoFocus
              value={findQuery}
              onChange={(e) => setFindQuery(e.target.value)}
              placeholder="Highlight words on page..."
              className="flex-1 bg-transparent text-xs text-white focus:outline-none"
            />
            <button
              onClick={() => {
                setFindQuery('');
                setShowFindBar(false);
              }}
              className="p-1 text-slate-400 hover:text-white cursor-pointer"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {/* Main Browsing Viewport (Single Tab OR Dual Split-Screen!) */}
        <div
          ref={viewportContainerRef}
          className={`flex-1 min-h-0 relative ${
            splitTab ? 'grid grid-rows-2 sm:grid-rows-1 sm:grid-cols-2 divide-y sm:divide-y-0 sm:divide-x divide-cyan-500/40' : 'flex'
          }`}
        >
          <div className="h-full w-full min-h-0 overflow-hidden">
            <WebViewport
              tab={activeTab}
              shields={shields}
              vpn={vpn}
              extensions={extensions}
              customization={customization}
              zapperMode={zapperMode}
              findQuery={findQuery}
              onNavigate={(u) => navigateTab(activeTab.id, u)}
              onZapElement={(zapId) =>
                updateTab(activeTab.id, (t) => ({
                  ...t,
                  zappedSelectors: [...t.zappedSelectors, zapId],
                  adsBlocked: t.adsBlocked + 1,
                }))
              }
              onOpenAi={(mode, query) => setAiDrawerState({ open: true, mode, query })}
              onOpenVpn={() => setShowVpn(true)}
              onOpenExtensions={() => setShowExtensions(true)}
              onToggleDesktop={() =>
                updateTab(activeTab.id, (t) => ({ ...t, isDesktopMode: !t.isDesktopMode }))
              }
              onOpenNewIncognito={() => handleNewTab(true)}
              onTriggerDownload={handleTriggerDownload}
              onEnablePip={(title) => setPipTitle(title)}
              onOpenPlayStore={() => setShowPlayStoreModal(true)}
              onLiveContentLoaded={(summaryText, pageTitle) => {
                setLivePageTexts((prev) => ({ ...prev, [activeTab.id]: summaryText }));
                updateTab(activeTab.id, (t) => ({ ...t, title: pageTitle }));
              }}
            />
          </div>

          {splitTab && (
            <div className="h-full w-full min-h-0 overflow-hidden relative">
              <div className="absolute top-2 right-2 z-20 px-2 py-0.5 rounded-full bg-purple-600/90 text-white text-[10px] font-bold">
                Split Pane 2
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
                onZapElement={(zapId) =>
                  updateTab(splitTab.id, (t) => ({
                    ...t,
                    zappedSelectors: [...t.zappedSelectors, zapId],
                  }))
                }
                onOpenAi={(mode, query) => setAiDrawerState({ open: true, mode, query })}
                onOpenVpn={() => setShowVpn(true)}
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

          {/* Floating Background Video Picture-in-Picture (PiP) Mini-Player */}
          {pipTitle && (
            <div className="absolute bottom-3 right-3 z-30 w-60 rounded-2xl bg-slate-950/95 border border-cyan-400/50 shadow-2xl p-2.5 space-y-1.5">
              <div className="flex items-center justify-between text-[10px] text-cyan-300 font-bold">
                <span className="inline-flex items-center gap-1">
                  <PictureInPicture2 className="w-3 h-3" /> PiP Active Across Tabs
                </span>
                <button
                  onClick={() => setPipTitle(null)}
                  className="text-slate-400 hover:text-white cursor-pointer"
                >
                  <X className="w-3 h-3" />
                </button>
              </div>
              <div className="h-20 rounded-xl bg-gradient-to-br from-indigo-900 via-slate-900 to-cyan-950 flex items-center justify-center p-2 text-center text-[11px] font-semibold text-white">
                ▶ {pipTitle}
              </div>
            </div>
          )}

          {/* Fire Button Incineration Overlay Animation */}
          {fireSweepAnim && (
            <div className="absolute inset-0 z-50 bg-gradient-to-t from-orange-600 via-rose-600/90 to-transparent flex flex-col items-center justify-center text-white animate-fire-sweep pointer-events-none">
              <Flame className="w-16 h-16 text-amber-200 mb-2" />
              <div className="text-lg font-black tracking-wide">
                INCINERATING TRACKERS, COOKIES &amp; RAM...
              </div>
            </div>
          )}
        </div>

        {/* Desktop-Class Mobile DevTools Drawer */}
        {showDevTools && (
          <DevToolsPanel
            tab={activeTab}
            networkLogs={networkLogs}
            onClose={() => setShowDevTools(false)}
          />
        )}

        {/* Bottom Ergonomic Bar Placement */}
        {customization.addressBarPosition === 'bottom' && AddressToolbar}

        {/* Android Gesture Pill when in Phone Frame Mode */}
        {customization.phoneFrameMode && (
          <div className="py-1.5 bg-black flex justify-center shrink-0">
            <div className="w-28 h-1 rounded-full bg-slate-600" />
          </div>
        )}
      </div>

      {/* Modals & Drawers */}
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

      {showPlayStoreModal && (
        <PlayStoreDownloadModal
          deferredInstallPrompt={deferredInstallPrompt}
          onClose={() => setShowPlayStoreModal(false)}
        />
      )}
    </div>
  );
}
export default App;
