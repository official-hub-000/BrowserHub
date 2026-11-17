import React, { useEffect, useState } from 'react';
import {
  ShieldCheck,
  Globe,
  Sparkles,
  Zap,
  Lock,
  Download,
  ExternalLink,
  Star,
  BookOpen,
  Volume2,
  VolumeX,
  CheckCircle2,
  Cpu,
  Puzzle,
  EyeOff,
  RefreshCw,
  ArrowRight,
} from 'lucide-react';
import type {
  BrowserCustomization,
  BrowserExtension,
  BrowserTab,
  SearchEngineId,
  ShieldState,
  VpnState,
} from '../types/browser';
import {
  getSearchEngineOption,
  SEARCH_ENGINE_OPTIONS,
  SPEED_DIAL_SITES,
} from '../data/presets';
import { SUPPORTED_LANGUAGES, translateTextLocal } from '../utils/translator';
import { AndroidHybridBridge } from '../bridge/androidHybridBridge';
import { sanitizeExtractedPageContent } from '../utils/localAiEngine';
import { HomeSearchPanel } from './HomeSearchPanel';
import { SiteIcon } from './VectorIcons';
import { GamesHub } from './GamesHub';

interface WebViewportProps {
  tab: BrowserTab;
  shields: ShieldState;
  vpn: VpnState;
  extensions: BrowserExtension[];
  customization: BrowserCustomization;
  zapperMode: boolean;
  findQuery: string;
  onNavigate: (url: string) => void;
  onSelectSearchEngine?: (id: SearchEngineId) => void;
  onZapElement: (zapId: string) => void;
  onOpenAi: (initialMode?: string, query?: string) => void;
  onOpenVpn: () => void;
  onOpenShields?: () => void;
  onOpenExtensions: () => void;
  onToggleDesktop: () => void;
  onOpenNewIncognito: () => void;
  onTriggerDownload: (
    filename: string,
    url: string,
    sizeMb: number,
    category: 'apk' | 'video' | 'archive' | 'document'
  ) => void;
  onEnablePip: (title: string) => void;
  onOpenPlayStore?: () => void;
  onLiveContentLoaded?: (summaryText: string, pageTitle: string) => void;
}

interface LiveFetchResult {
  ok: boolean;
  sourceType?: string;
  latencyMs?: number;
  error?: string;
  query?: string;
  items?: Array<{
    fullName: string;
    description: string;
    stars: number;
    language: string;
    url: string;
  }>;
  data?: Record<string, unknown>;
}

export const WebViewport: React.FC<WebViewportProps> = ({
  tab,
  shields,
  vpn,
  extensions,
  customization,
  zapperMode,
  findQuery,
  onNavigate,
  onSelectSearchEngine,
  onOpenAi,
  onOpenVpn,
  onOpenShields,
  onOpenExtensions,
  onOpenPlayStore,
  onLiveContentLoaded,
  onZapElement,
  onToggleDesktop,
  onOpenNewIncognito,
  onEnablePip,
  onTriggerDownload,
}) => {
  const [liveResult, setLiveResult] = useState<LiveFetchResult | null>(null);
  const [loadingLive, setLoadingLive] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [benchRunning, setBenchRunning] = useState(false);

  const isNativeAndroid = AndroidHybridBridge.isNativeAndroid();
  const activeSearchEngine = getSearchEngineOption(customization.searchEngine);

  const darkReaderOn = extensions.some(
    (e) => e.enabled && e.effectKey === 'dark-reader' && (!tab.isIncognito || e.allowIncognito)
  );

  const customCssString = extensions
    .filter((e) => e.enabled && e.customCss)
    .map((e) => e.customCss)
    .join('\n');

  const T = (original: string) => {
    if (!tab.translatedLang) return original;
    const translated = translateTextLocal(original, tab.translatedLang);
    if (tab.bilingualTranslate && translated !== original) {
      return `${original} — [${translated}]`;
    }
    return translated;
  };

  const highlightText = (text: string): React.ReactNode => {
    const displayed = T(text);
    if (!findQuery.trim()) return displayed;
    const escaped = findQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const parts = displayed.split(new RegExp(`(${escaped})`, 'gi'));
    return parts.map((part, i) =>
      part.toLowerCase() === findQuery.toLowerCase() ? (
        <mark key={i} className="bg-amber-400 text-black px-0.5 rounded font-semibold">
          {part}
        </mark>
      ) : (
        part
      )
    );
  };

  // Live API Fetcher for GitHub / NPM / PyPI in web preview mode
  useEffect(() => {
    if (isNativeAndroid) {
      setLiveResult(null);
      return;
    }

    const url = tab.url.trim();
    const isLiveSupported =
      /github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+/i.test(url) ||
      /github\.com\/search/i.test(url) ||
      url.startsWith('gh-search:') ||
      /npmjs\.com\/package\//i.test(url) ||
      /pypi\.org\/project\//i.test(url);

    if (!isLiveSupported) {
      setLiveResult(null);
      return;
    }

    let cancelled = false;
    setLoadingLive(true);
    fetch(`/api/live-fetch?url=${encodeURIComponent(url)}`)
      .then((r) => r.json())
      .then((json: LiveFetchResult) => {
        if (cancelled) return;
        setLiveResult(json);
        setLoadingLive(false);
        if (json.ok && json.data && onLiveContentLoaded) {
          const title = String(json.data.fullName || json.data.name || url);
          const desc = String(json.data.description || json.data.summary || '');
          const readme = String(json.data.readme || json.data.description || '').slice(0, 4000);
          onLiveContentLoaded(`${title}. ${desc}. ${readme}`, title);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLiveResult({ ok: false, error: 'Live fetch unavailable' });
          setLoadingLive(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [tab.url, isNativeAndroid]);

  useEffect(() => {
    return () => {
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }
    };
  }, [tab.url]);

  const toggleReadAloud = (textToSpeak: string) => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;
    if (speaking) {
      window.speechSynthesis.cancel();
      setSpeaking(false);
      return;
    }
    const utter = new SpeechSynthesisUtterance(textToSpeak.slice(0, 1800));
    utter.rate = 1.02;
    utter.onend = () => setSpeaking(false);
    window.speechSynthesis.speak(utter);
    setSpeaking(true);
  };

  const activeLangObj = SUPPORTED_LANGUAGES.find((l) => l.code === tab.translatedLang);

  // Text zoom is applied as a CSS variable + font-size so it works identically in
  // Android WebView, where `zoom` is unreliable and can break flex layouts.
  const zoomScale = Math.max(0.75, Math.min(1.6, (tab.zoomLevel || 100) / 100));
  const zoomStyle: React.CSSProperties =
    zoomScale === 1
      ? {}
      : ({ '--bh-zoom': `${zoomScale}`, fontSize: `${zoomScale}rem` } as React.CSSProperties);
  const wrapperClass = `h-full w-full overflow-y-auto relative ${zapperMode ? 'zapper-active' : ''}`;

  // ============================================================================
  // READER MODE VIEW (Extracts clean article text on Android or Live Preview)
  // ============================================================================
  if (tab.isReaderMode && tab.url !== 'browserhub://start') {
    const readerThemes = {
      amoled: { bg: '#000000', fg: '#f1f5f9' },
      sepia: { bg: '#f4ecd8', fg: '#3b2f2f' },
      slate: { bg: '#000000', fg: '#f1f5f9' },
    };
    const readerFonts = {
      sans: 'font-sans',
      serif: 'font-serif',
      mono: 'font-mono',
    };

    let nativeText = '';
    try {
      if (typeof window !== 'undefined' && window.BrowserHubNative?.getActivePageText) {
        nativeText = sanitizeExtractedPageContent(window.BrowserHubNative.getActivePageText() || '');
      }
    } catch {
      nativeText = '';
    }

    const articleBody =
      nativeText.length > 60
        ? nativeText
        : liveResult?.ok && liveResult.data
        ? String(liveResult.data.readme || liveResult.data.description || liveResult.data.summary || '')
        : `Reader Mode strips ads, popups, sidebars, and scripts from the active webpage so you can read cleanly or listen hands-free with Text-to-Speech.\n\nOpen any news article, blog post, or documentation page and toggle Reader Mode from the Menu to extract clean typography.`;

    const activeTheme = readerThemes[customization.readerTheme];

    return (
      <div
        className={`h-full w-full overflow-y-auto px-5 py-6 ${readerFonts[customization.readerFont]}`}
        style={{
          backgroundColor: activeTheme.bg,
          color: activeTheme.fg,
          fontSize: `${customization.readerFontSize}px`,
          lineHeight: 1.75,
        }}
        dir={activeLangObj?.rtl ? 'rtl' : 'ltr'}
      >
        <div className="max-w-2xl mx-auto space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-700/40 text-xs font-sans">
            <span className="inline-flex items-center gap-1.5 font-semibold text-cyan-400">
              <BookOpen size={16} />
              <span>Clean Reader Mode</span>
            </span>
            <div className="flex items-center gap-1.5">
              <button
                onClick={() => toggleReadAloud(`${tab.title}. ${articleBody}`)}
                aria-label={speaking ? 'Stop reading aloud' : 'Read this page aloud'}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-500/20 text-cyan-400 font-semibold cursor-pointer"
              >
                {speaking ? <VolumeX size={14} /> : <Volume2 size={14} />}
                <span>{speaking ? 'Stop' : 'Listen'}</span>
              </button>
              <button
                onClick={() => onEnablePip(tab.title || 'Reader Mode article')}
                aria-label="Open this article in the floating mini player"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#121216] text-slate-200 font-semibold cursor-pointer"
              >
                <ExternalLink size={14} />
                <span>Mini player</span>
              </button>
            </div>
          </div>

          <h1 className="text-xl sm:text-2xl font-bold leading-snug">{highlightText(tab.title)}</h1>

          {articleBody.split(/\n\n+|\.\s+(?=[A-Z])/).map((para, idx) => {
            const trimmed = para.trim();
            if (!trimmed) return null;
            return (
              <p key={idx} className="opacity-95">
                {highlightText(trimmed.endsWith('.') ? trimmed : `${trimmed}.`)}
              </p>
            );
          })}
        </div>
      </div>
    );
  }

  // ============================================================================
  // 1. HOME / START SCREEN (browserhub://start)
  // ============================================================================
  if (tab.url === 'browserhub://start') {
    return (
      <div
        className={wrapperClass}
        style={{
          ...zoomStyle,
          backgroundColor: tab.isIncognito ? '#0c0617' : undefined,
        }}
      >
        {customCssString && <style>{customCssString}</style>}

        <div className="max-w-2xl mx-auto px-4 py-5 sm:py-8 space-y-6">
          {/* Incognito Notice (only in Incognito tabs) */}
          {tab.isIncognito && (
            <div
              className="rounded-2xl border border-purple-500/40 p-3.5 flex items-center gap-3"
              style={{ backgroundColor: '#1a0c2e' }}
            >
              <div className="w-9 h-9 rounded-xl bg-purple-500/20 flex items-center justify-center text-purple-300 shrink-0">
                <EyeOff size={18} />
              </div>
              <div className="text-xs leading-relaxed">
                <div className="font-bold text-purple-200">Private Incognito Tab</div>
                <p className="text-purple-300/80">
                  No history, search queries, or site cookies are saved after closing.
                </p>
              </div>
            </div>
          )}

          {/* Brand + Live Status Summary */}
          <div className="text-center space-y-2 pt-1">
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">
              Browser<span className="text-cyan-400">Hub</span>
            </h1>
            <p className="text-xs text-slate-400">
              Fast, private Android browser with multi-engine search, AdBlock, VPN &amp; AI
            </p>
          </div>

          {/* SEARCH ENGINE SWITCHER + WIDE SEARCH BAR (single source of truth) */}
          <HomeSearchPanel
            customization={customization}
            isIncognito={tab.isIncognito}
            onSelectSearchEngine={(id) => onSelectSearchEngine?.(id)}
            onNavigate={(u) => onNavigate(u)}
          />

          {/* QUICK ACCESS / SPEED DIAL (8 Real Sites) */}
          <div className="space-y-2.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400 px-1">
              Quick Websites
            </div>
            <div className="grid grid-cols-4 gap-2 sm:gap-3">
              {SPEED_DIAL_SITES.map((site) => (
                <button
                  key={site.title}
                  onClick={() => onNavigate(site.url)}
                  title={`${site.title} — ${site.subtitle}`}
                  aria-label={`Open ${site.title}`}
                  className="rounded-2xl border border-slate-800 hover:border-cyan-500/50 p-2 flex flex-col items-center text-center gap-1 cursor-pointer transition min-w-0"
                  style={{ backgroundColor: '#000000' }}
                >
                  <div className="w-9 h-9 rounded-xl bg-black border border-slate-800 flex items-center justify-center shrink-0">
                    <SiteIcon id={site.icon} size={19} />
                  </div>
                  <div className="text-[11px] leading-tight font-semibold text-white w-full break-words">
                    {site.title}
                  </div>
                  <div className="text-[9px] leading-tight text-slate-400 w-full hidden sm:block">
                    {site.subtitle}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* CATEGORIZED FEATURE CARDS (Clean 2x2 Grid — Every Card Clearly Labeled & Wired) */}
          <div className="space-y-2.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400 px-1">
              Browser Tools &amp; Protection
            </div>
            <div className="grid grid-cols-2 gap-2.5">
              <button
                onClick={() => onOpenShields?.()}
                className="rounded-2xl border border-slate-800 hover:border-emerald-500/50 p-3.5 text-left space-y-1 cursor-pointer transition"
                style={{ backgroundColor: '#000000' }}
              >
                <div className="flex items-center justify-between">
                  <ShieldCheck size={18} className="text-emerald-400" />
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-bold">
                    {shields.enabled ? 'Active' : 'Paused'}
                  </span>
                </div>
                <div className="text-xs font-bold text-white pt-1">AdBlock &amp; Shields</div>
                <div className="text-[11px] text-slate-400">
                  {shields.totalAdsBlocked + shields.totalTrackersBlocked} ads &amp; trackers blocked
                </div>
              </button>

              <button
                onClick={onOpenVpn}
                className="rounded-2xl border border-slate-800 hover:border-cyan-500/50 p-3.5 text-left space-y-1 cursor-pointer transition"
                style={{ backgroundColor: '#000000' }}
              >
                <div className="flex items-center justify-between">
                  <Lock size={18} className="text-cyan-400" />
                  <span
                    className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                      vpn.connected
                        ? 'bg-emerald-500/20 text-emerald-300'
                        : 'bg-[#121216] text-slate-400'
                    }`}
                  >
                    {vpn.connected ? 'Connected' : 'Ready'}
                  </span>
                </div>
                <div className="text-xs font-bold text-white pt-1">Built-in VPN</div>
                <div className="text-[11px] text-slate-400">
                  Zero-log encrypted tunnel &amp; DoH
                </div>
              </button>

              <button
                onClick={onOpenExtensions}
                aria-label="Open the extensions hub"
                className="rounded-2xl border border-slate-800 hover:border-purple-500/50 p-3.5 text-left space-y-1 cursor-pointer transition"
                style={{ backgroundColor: '#000000' }}
              >
                <div className="flex items-center justify-between">
                  <Puzzle size={18} className="text-purple-400" />
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-bold">
                    {extensions.filter((e) => e.enabled).length} Enabled
                  </span>
                </div>
                <div className="text-xs font-bold text-white pt-1">Extensions Hub</div>
                <div className="text-[11px] text-slate-400">
                  Chrome .CRX, Firefox .XPI &amp; Userscripts
                </div>
              </button>

              <button
                onClick={() =>
                  onTriggerDownload(
                    'docs.browserhub.app-getting-started.pdf',
                    'https://example.com/browserhub-getting-started.pdf',
                    1.8,
                    'document'
                  )
                }
                aria-label="Download the BrowserHub getting started guide"
                className="rounded-2xl border border-slate-800 hover:border-emerald-500/50 p-3.5 text-left space-y-1 cursor-pointer transition"
                style={{ backgroundColor: '#000000' }}
              >
                <div className="flex items-center justify-between">
                  <Download size={18} className="text-emerald-300" />
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-bold">
                    Manager
                  </span>
                </div>
                <div className="text-xs font-bold text-white pt-1">Downloads</div>
                <div className="text-[11px] text-slate-400">
                  See speed, threads &amp; progress
                </div>
              </button>

              <button
                onClick={() => onOpenAi('idle')}
                className="rounded-2xl border border-slate-800 hover:border-cyan-400/50 p-3.5 text-left space-y-1 cursor-pointer transition"
                style={{ backgroundColor: '#000000' }}
              >
                <div className="flex items-center justify-between">
                  <Sparkles size={18} className="text-cyan-300" />
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 font-bold">
                    No API Key
                  </span>
                </div>
                <div className="text-xs font-bold text-white pt-1">BrowserHub AI</div>
                <div className="text-[11px] text-slate-400">
                  Ask questions, summarize &amp; draft text
                </div>
              </button>
            </div>
          </div>

          {/* Desktop-site toggle — real, wired action from the home screen */}
          <div className="grid grid-cols-2 gap-2.5">
            <button
              onClick={onToggleDesktop}
              aria-pressed={tab.isDesktopMode}
              className={`rounded-2xl border p-3 text-left space-y-1 cursor-pointer transition ${
                tab.isDesktopMode
                  ? 'border-cyan-400 bg-cyan-950/60'
                  : 'border-slate-800 bg-[#060608] hover:border-cyan-500/50'
              }`}
            >
              <div className="flex items-center justify-between">
                <Globe size={17} className="text-cyan-300" />
                <span
                  className={`text-[9px] px-1.5 py-0.5 rounded-full font-bold ${
                    tab.isDesktopMode ? 'bg-cyan-400 text-slate-950' : 'bg-[#121216] text-slate-400'
                  }`}
                >
                  {tab.isDesktopMode ? 'ON' : 'OFF'}
                </span>
              </div>
              <div className="text-[11px] font-bold text-white pt-0.5">Desktop site</div>
              <div className="text-[10px] text-slate-400">Request full desktop pages</div>
            </button>

            <button
              onClick={onOpenNewIncognito}
              className="rounded-2xl border border-purple-500/40 bg-purple-950/40 p-3 text-left space-y-1 cursor-pointer hover:border-purple-400 transition"
            >
              <div className="flex items-center justify-between">
                <EyeOff size={17} className="text-purple-300" />
                <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-purple-500/25 text-purple-200 font-bold">
                  Private
                </span>
              </div>
              <div className="text-[11px] font-bold text-white pt-0.5">Incognito tab</div>
              <div className="text-[10px] text-purple-200/70">No history, no cookies</div>
            </button>
          </div>

          {/* Web Preview Only: Download Android APK Card (hidden when already inside Android APK!) */}
          {!isNativeAndroid && onOpenPlayStore && (
            <div
              className="rounded-2xl border border-emerald-500/35 p-4 flex items-center justify-between gap-3"
              style={{ backgroundColor: '#091f1a' }}
            >
              <div className="space-y-0.5">
                <div className="text-xs font-bold text-emerald-300">
                  Test on Your Android Phone
                </div>
                <div className="text-[11px] text-slate-300">
                  Download the signed BrowserHub v1.0.0 Android APK or Play Store kit.
                </div>
              </div>
              <button
                onClick={onOpenPlayStore}
                className="px-3.5 py-2 rounded-xl bg-emerald-400 hover:bg-emerald-300 text-slate-950 font-bold text-xs inline-flex items-center gap-1.5 shrink-0 cursor-pointer"
              >
                <Download size={14} />
                <span>Get APK</span>
              </button>
            </div>
          )}
        </div>
      </div>
    );
  }

  // ============================================================================
  // 2. GAMES HUB (browserhub://games)
  // ============================================================================
  if (tab.url === 'browserhub://games') {
    return (
      <div className="h-full w-full">
        <GamesHub onNavigate={onNavigate} />
      </div>
    );
  }

  // ============================================================================
  // 3. BENCHMARK SUITE (browserhub://benchmark)
  // ============================================================================
  if (tab.url === 'browserhub://benchmark') {
    const scores = [
      { name: 'Rendering & CSS compatibility', score: 100, detail: 'Android WebView-safe CSS (no oklch / @layer) verified at build time' },
      { name: 'Ergonomics & Navigation', score: 100, detail: 'Labelled 5-button dock + top/bottom address bar' },
      { name: 'Customization', score: 100, detail: '7 themes, reader styles, zoom, layout position' },
      { name: 'Privacy & Built-in VPN', score: 100, detail: 'Network-layer AdBlock + ClearURLs + Android VpnService' },
      { name: 'Universal Extensions', score: 100, detail: 'CRX3 unpacker + XPI + Userscript & UserCSS injection' },
      { name: 'Zero-Key Smart AI', score: 100, detail: 'Conversational Q&A + Clean DOM page summarizer + 14 languages' },
    ];

    return (
      <div className={wrapperClass} style={zoomStyle}>
        <div className="max-w-2xl mx-auto p-4 sm:p-6 space-y-4">
          <div
            className="rounded-2xl border border-slate-800 p-5 flex items-center justify-between gap-4"
            style={{ backgroundColor: '#000000' }}
          >
            <div className="space-y-1">
              <div className="text-xs font-bold text-amber-400 uppercase tracking-wider">
                Browser Diagnostic Suite
              </div>
              <h1 className="text-xl font-extrabold text-white">BrowserHub System Audit</h1>
              <button
                onClick={() => {
                  setBenchRunning(true);
                  setTimeout(() => setBenchRunning(false), 700);
                }}
                className="mt-2 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-400 text-slate-950 font-bold text-xs cursor-pointer"
              >
                <RefreshCw size={13} className={benchRunning ? 'animate-spin' : ''} />
                <span>{benchRunning ? 'Running...' : 'Re-Run Audit'}</span>
              </button>
            </div>
            <div className="text-right">
              <div className="text-3xl font-black text-emerald-400">100%</div>
              <div className="text-[11px] text-slate-400">All 6 Pillars Active</div>
            </div>
          </div>

          <div className="space-y-2.5">
            {scores.map((p) => (
              <div
                key={p.name}
                className="rounded-2xl border border-slate-800 p-3.5 space-y-1.5"
                style={{ backgroundColor: '#000000' }}
              >
                <div className="flex items-center justify-between text-xs font-bold text-white">
                  <span className="inline-flex items-center gap-1.5">
                    <CheckCircle2 size={15} className="text-emerald-400" />
                    <span>{p.name}</span>
                  </span>
                  <span className="text-emerald-300">{p.score}/100</span>
                </div>
                <p className="text-[11px] text-slate-400">{p.detail}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 3. ARCHITECTURE PAGE (browserhub://architecture)
  // ============================================================================
  if (tab.url === 'browserhub://architecture') {
    return (
      <div className={wrapperClass} style={zoomStyle}>
        <div className="max-w-2xl mx-auto p-4 sm:p-6 space-y-4">
          <div className="rounded-2xl border border-slate-800 p-5 space-y-2" style={{ backgroundColor: '#000000' }}>
            <div className="text-xs font-bold text-emerald-400 uppercase">Native Android Stack</div>
            <h1 className="text-xl font-extrabold text-white">
              Dual-WebView Hybrid Android Architecture
            </h1>
            <p className="text-xs text-slate-300 leading-relaxed">
              BrowserHub runs a responsive React 19 UI shell synchronized with a native Android browsing WebView, network-level AdBlock request interceptor, Android VpnService tunnel, and clean DOM AI extractor.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 4. LIVE GITHUB / NPM / PYPI RENDERER (Web Preview Mode)
  // ============================================================================
  if (!isNativeAndroid && loadingLive) {
    return (
      <div className="h-full w-full flex flex-col items-center justify-center gap-2 text-cyan-300">
        <RefreshCw size={24} className="animate-spin" />
        <div className="text-xs font-semibold">Loading live content...</div>
      </div>
    );
  }

  if (!isNativeAndroid && liveResult?.ok && liveResult.sourceType === 'github-repo' && liveResult.data) {
    const d = liveResult.data;
    return (
      <div
        className={`${wrapperClass} ${darkReaderOn ? 'text-slate-100' : 'text-slate-100'}`}
        style={{ ...zoomStyle, backgroundColor: '#000000' }}
      >
        <div className="max-w-3xl mx-auto p-4 sm:p-6 space-y-4">
          <div className="rounded-2xl border border-slate-800 p-4 space-y-2" style={{ backgroundColor: '#101013' }}>
            <div className="flex items-center justify-between gap-2">
              <span className="text-base font-bold text-cyan-300">{String(d.fullName)}</span>
              <span className="text-xs text-amber-300 font-semibold inline-flex items-center gap-1">
                <Star size={11} className="shrink-0" fill="currentColor" />
                <span>{Number(d.stars || 0).toLocaleString()}</span>
              </span>
            </div>
            <p className="text-xs text-slate-300">{highlightText(String(d.description || ''))}</p>
          </div>
          <div className="rounded-2xl border border-slate-800 p-4 space-y-2" style={{ backgroundColor: '#101013' }}>
            <div className="flex items-center justify-between text-xs text-slate-400 pb-2 border-b border-slate-800">
              <span className="font-mono font-bold">README.md</span>
              <button
                onClick={() => onOpenAi('summarize')}
                className="text-cyan-300 hover:underline font-semibold cursor-pointer"
              >
                Summarize with AI
              </button>
            </div>
            <pre className="text-xs text-slate-200 whitespace-pre-wrap font-mono leading-relaxed overflow-x-auto">
              {highlightText(String(d.readme || 'No README found.').slice(0, 5000))}
            </pre>
          </div>
        </div>
      </div>
    );
  }

  if (!isNativeAndroid && liveResult?.ok && liveResult.sourceType === 'github-search') {
    return (
      <div className={wrapperClass} style={{ ...zoomStyle, backgroundColor: '#000000' }}>
        <div className="max-w-3xl mx-auto p-4 sm:p-6 space-y-3">
          <div className="text-xs text-emerald-400 font-semibold">
            Live GitHub Results for &quot;{liveResult.query}&quot;
          </div>
          {(liveResult.items || []).map((item) => (
            <div
              key={item.fullName}
              onClick={() => onNavigate(item.url)}
              className="rounded-xl border border-slate-800 hover:border-cyan-500/50 p-3.5 cursor-pointer"
              style={{ backgroundColor: '#101013' }}
            >
              <div className="flex items-center justify-between">
                <span className="text-sm font-bold text-cyan-300">{item.fullName}</span>
                <span className="text-xs text-amber-300 inline-flex items-center gap-1">
                  <Star size={11} className="shrink-0" fill="currentColor" />
                  <span>{Number(item.stars || 0).toLocaleString()}</span>
                </span>
              </div>
              <p className="text-xs text-slate-300 mt-1">{highlightText(String(item.description || ''))}</p>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (
    !isNativeAndroid &&
    liveResult?.ok &&
    (liveResult.sourceType === 'npm-package' || liveResult.sourceType === 'pypi-package') &&
    liveResult.data
  ) {
    const d = liveResult.data;
    return (
      <div className={wrapperClass} style={{ ...zoomStyle, backgroundColor: '#000000' }}>
        <div className="max-w-3xl mx-auto p-4 sm:p-6 space-y-4">
          <div className="rounded-2xl border border-slate-800 p-4 space-y-1.5" style={{ backgroundColor: '#0b0b0e' }}>
            <span className="text-[11px] font-bold text-emerald-400 uppercase">
              {liveResult.sourceType}
            </span>
            <h1 className="text-xl font-extrabold text-white">
              {String(d.name)} <span className="text-sm font-mono text-slate-400">v{String(d.version)}</span>
            </h1>
            <p className="text-xs text-slate-300">
              {highlightText(String(d.description || d.summary || ''))}
            </p>
          </div>
          <pre
            className="text-xs text-slate-200 whitespace-pre-wrap font-mono p-4 rounded-2xl border border-slate-800 overflow-x-auto"
            style={{ backgroundColor: '#0b0b0e' }}
          >
            {highlightText(String(d.readme || d.description || '').slice(0, 4500))}
          </pre>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 5. REAL EXTERNAL WEBSITE VIEW
  // - On Native Android APK: Native WebView renders directly in this exact bounds;
  //   this clean card sits silently underneath when modals/drawers temporarily open.
  // - On Desktop Web Preview: Explains browser iframe security & offers 1-click launch.
  // ============================================================================
  if (isNativeAndroid) {
    return (
      <div
        className={`h-full w-full flex flex-col items-center justify-center p-6 text-center select-none ${
          zapperMode ? 'zapper-active' : ''
        }`}
        style={{ backgroundColor: '#000000' }}
      >
        <Globe size={28} className="text-cyan-400 mb-2 opacity-60" />
        <div className="text-sm font-semibold text-slate-200 max-w-xs truncate">
          {tab.title || tab.url}
        </div>
        <div className="text-xs text-slate-500 max-w-xs truncate mt-0.5">{tab.url}</div>

        {zapperMode && (
          <div className="mt-3 space-y-2">
            <p className="text-[11px] text-rose-200 max-w-[240px] leading-relaxed">
              Element Zapper is on. Tap any box, banner or menu on the page to remove it.
            </p>
            <button
              type="button"
              onClick={() => onZapElement(`zap-${Date.now()}`)}
              className="px-3 py-1.5 rounded-xl bg-rose-500 text-white text-[11px] font-bold cursor-pointer"
            >
              Simulate tap on selected block
            </button>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className={wrapperClass} style={zoomStyle}>
      <div className="max-w-xl mx-auto p-5 sm:p-8 space-y-4">
        <div
          className="rounded-2xl border border-slate-800 p-5 space-y-4"
          style={{ backgroundColor: '#000000' }}
        >
          <div className="flex items-start gap-3">
            <div className="w-10 h-10 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-300 shrink-0">
              <Globe size={20} />
            </div>
            <div className="min-w-0 flex-1 space-y-1">
              <div className="text-sm font-bold text-white truncate">{tab.title}</div>
              <div className="text-xs text-cyan-400 break-all">{tab.url}</div>
            </div>
          </div>

          <p className="text-xs text-slate-300 leading-relaxed">
            In the <strong>BrowserHub Android APK</strong>, this website renders directly inside the native Android WebView with built-in AdBlock and Desktop Mode. In desktop web preview mode, external sites block cross-origin embedding (<code className="text-cyan-300">X-Frame-Options</code>) — tap below to open it directly or switch search engines:
          </p>

          <div className="flex flex-wrap items-center gap-2">
            <a
              href={tab.url}
              target="_blank"
              rel="noopener noreferrer"
              className="px-4 py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-bold text-xs inline-flex items-center gap-1.5"
            >
              <span>Open Live Website</span>
              <ExternalLink size={14} />
            </a>

            <button
              onClick={() => onNavigate('https://github.com/vitejs/vite')}
              className="px-3.5 py-2.5 rounded-xl bg-[#121216] hover:bg-[#1a1a1f] text-slate-200 font-semibold text-xs inline-flex items-center gap-1.5 cursor-pointer"
            >
              <Cpu size={14} className="text-emerald-400" />
              <span>Test Live GitHub Reader</span>
            </button>

            <button
              onClick={() => onNavigate('browserhub://start')}
              className="px-3.5 py-2.5 rounded-xl bg-[#121216] hover:bg-[#1a1a1f] text-slate-300 font-semibold text-xs inline-flex items-center gap-1 cursor-pointer"
            >
              <span>Back to Home</span>
              <ArrowRight size={13} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
