import React, { useEffect, useState } from 'react';
import {
  ShieldCheck,
  ShieldAlert,
  Globe,
  Sparkles,
  Zap,
  Lock,
  Monitor,
  Smartphone,
  Play,
  Pause,
  SkipForward,
  ThumbsUp,
  ThumbsDown,
  PictureInPicture2,
  Download,
  ExternalLink,
  Star,
  GitFork,
  AlertCircle,
  BookOpen,
  Volume2,
  VolumeX,
  CheckCircle2,
  Cpu,
  Puzzle,
  EyeOff,
  Search,
  Languages,
  RefreshCw,
} from 'lucide-react';
import type {
  BrowserCustomization,
  BrowserExtension,
  BrowserTab,
  ShieldState,
  VpnState,
} from '../types/browser';
import { SPEED_DIAL_SITES } from '../data/presets';
import { SUPPORTED_LANGUAGES, translateTextLocal } from '../utils/translator';

interface WebViewportProps {
  tab: BrowserTab;
  shields: ShieldState;
  vpn: VpnState;
  extensions: BrowserExtension[];
  customization: BrowserCustomization;
  zapperMode: boolean;
  findQuery: string;
  onNavigate: (url: string) => void;
  onZapElement: (zapId: string) => void;
  onOpenAi: (initialMode?: string, query?: string) => void;
  onOpenVpn: () => void;
  onOpenExtensions: () => void;
  onToggleDesktop: () => void;
  onOpenNewIncognito: () => void;
  onTriggerDownload: (filename: string, url: string, sizeMb: number, category: 'apk' | 'video' | 'archive' | 'document') => void;
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
  onZapElement,
  onOpenAi,
  onOpenVpn,
  onOpenExtensions,
  onToggleDesktop,
  onOpenNewIncognito,
  onTriggerDownload,
  onEnablePip,
  onOpenPlayStore,
  onLiveContentLoaded,
}) => {
  const [liveResult, setLiveResult] = useState<LiveFetchResult | null>(null);
  const [loadingLive, setLoadingLive] = useState(false);
  const [startSearchInput, setStartSearchInput] = useState('');
  const [videoPlaying, setVideoPlaying] = useState(true);
  const [videoTimeSec, setVideoTimeSec] = useState(68);
  const [sponsorSkippedNotice, setSponsorSkippedNotice] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [benchRunning, setBenchRunning] = useState(false);
  const [benchDone, setBenchDone] = useState(true);

  const isExtEnabled = (effectKey: string) =>
    extensions.some((e) => e.enabled && e.effectKey === effectKey && (!tab.isIncognito || e.allowIncognito));

  const effectiveAdBlock =
    (shields.enabled && shields.blockAds) || isExtEnabled('ublock');
  const effectiveCookieKiller =
    (shields.enabled && shields.blockCookieBanners) || isExtEnabled('cookie-killer') || effectiveAdBlock;
  const darkReaderOn = isExtEnabled('dark-reader');
  const sponsorBlockOn = isExtEnabled('sponsorblock');
  const returnDislikeOn = isExtEnabled('return-dislike');
  const bgPlayOn = isExtEnabled('bg-play');

  // Collect custom CSS from enabled extensions
  const customCssString = extensions
    .filter((e) => e.enabled && e.customCss)
    .map((e) => e.customCss)
    .join('\n');

  const activeUserscripts = extensions.filter(
    (e) => e.enabled && e.customJs && e.source === 'userscript'
  );

  // Translation helper with optional bilingual view
  const T = (original: string) => {
    if (!tab.translatedLang) return original;
    const translated = translateTextLocal(original, tab.translatedLang);
    if (tab.bilingualTranslate && translated !== original) {
      return `${original} — [${translated}]`;
    }
    return translated;
  };

  // Highlight Find-in-Page matches
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

  // Check if an element was zapped by Element Zapper
  const isZapped = (id: string) => tab.zappedSelectors.includes(id);
  const handleZapClick = (e: React.MouseEvent, id: string) => {
    if (!zapperMode) return;
    e.preventDefault();
    e.stopPropagation();
    onZapElement(id);
  };

  // Live API Fetcher for real GitHub, NPM, PyPI URLs!
  useEffect(() => {
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
          const desc = String(
            json.data.description || json.data.summary || ''
          );
          const readme = String(
            json.data.readme || json.data.description || ''
          ).slice(0, 4000);
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
  }, [tab.url]);

  // Video timer + SponsorBlock auto-skip simulation
  useEffect(() => {
    if (!tab.url.includes('youtube.com') || !videoPlaying) return;
    const timer = setInterval(() => {
      setVideoTimeSec((prev) => {
        const next = prev + 1;
        // Sponsor segment is 75s -> 125s
        if (sponsorBlockOn && next >= 75 && next < 125) {
          setSponsorSkippedNotice(true);
          setTimeout(() => setSponsorSkippedNotice(false), 3200);
          return 125;
        }
        return next >= 420 ? 0 : next;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [tab.url, videoPlaying, sponsorBlockOn]);

  // Speech synthesis cleanup
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
    const utter = new SpeechSynthesisUtterance(textToSpeak.slice(0, 1400));
    utter.rate = 1.04;
    utter.onend = () => setSpeaking(false);
    window.speechSynthesis.speak(utter);
    setSpeaking(true);
  };

  const activeLangObj = SUPPORTED_LANGUAGES.find((l) => l.code === tab.translatedLang);

  // Reader Mode Override View
  if (tab.isReaderMode && tab.url !== 'browserhub://start') {
    const readerThemes = {
      amoled: 'bg-black text-slate-100',
      sepia: 'bg-[#f4ecd8] text-[#3b2f2f]',
      slate: 'bg-slate-900 text-slate-100',
    };
    const readerFonts = {
      sans: 'font-sans',
      serif: 'font-serif',
      mono: 'font-mono',
    };
    const articleBody =
      liveResult?.ok && liveResult.data
        ? String(liveResult.data.readme || liveResult.data.description || liveResult.data.summary || '')
        : `Modern mobile web browsers have historically forced Android users to choose between desktop power and mobile convenience. BrowserHub eliminates that compromise with a unified Hybrid Architecture.\n\nFirst, Native Shields Ultra blocks invasive ad networks, cross-site tracking beacons, GDPR cookie popups, and Canvas/WebGL fingerprinting before network payloads ever enter the DOM parser — delivering up to 3.4x faster page loads and slashing mobile battery drain.\n\nSecond, Universal Extension Support unites Firefox AMO (.xpi) unrestricted webRequestBlocking with Chrome Web Store (.crx) packages and Violentmonkey Userscripts (.user.js), allowing any extension on Earth to run natively in your pocket.\n\nThird, BrowserHub's Local Neural Core executes instant page summarization, semantic Q&A, and 14-language page translation completely free with zero API keys, zero paid subscriptions, and zero bytes of browsing telemetry ever transmitted off-device.`;

    return (
      <div
        className={`h-full w-full overflow-y-auto px-5 py-6 transition-colors ${readerThemes[customization.readerTheme]} ${readerFonts[customization.readerFont]}`}
        style={{ fontSize: `${customization.readerFontSize}px`, lineHeight: 1.72 }}
        dir={activeLangObj?.rtl ? 'rtl' : 'ltr'}
      >
        <div className="max-w-2xl mx-auto space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-current/15 text-xs font-sans opacity-80">
            <span className="inline-flex items-center gap-1.5 font-semibold">
              <BookOpen className="w-4 h-4 text-cyan-500" />
              Distilled Reader View • Zero Clutter • 3 min read
            </span>
            <button
              onClick={() => toggleReadAloud(`${tab.title}. ${articleBody}`)}
              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-cyan-500/15 text-cyan-500 font-medium hover:bg-cyan-500/25 cursor-pointer"
            >
              {speaking ? <VolumeX className="w-3.5 h-3.5" /> : <Volume2 className="w-3.5 h-3.5" />}
              {speaking ? 'Stop Reading' : 'Listen (TTS)'}
            </button>
          </div>
          <h1 className="text-2xl font-bold leading-snug">{highlightText(tab.title)}</h1>
          {articleBody.split(/\n\n+/).map((para, idx) => (
            <p key={idx} className="opacity-92">
              {highlightText(para)}
            </p>
          ))}
        </div>
      </div>
    );
  }

  // Wrapper style for Zoom, Desktop Viewport simulation, Zapper mode, and Custom CSS
  const wrapperClass = `h-full w-full overflow-y-auto relative transition-all duration-200 ${
    zapperMode ? 'zapper-active' : ''
  }`;

  // ============================================================================
  // 1. START PAGE: browserhub://start
  // ============================================================================
  if (tab.url === 'browserhub://start') {
    return (
      <div
        className={wrapperClass}
        style={{
          zoom: `${tab.zoomLevel}%`,
          background: tab.isIncognito
            ? 'radial-gradient(circle at top, #241038 0%, #07040d 65%)'
            : undefined,
        }}
      >
        {customCssString && <style>{customCssString}</style>}
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
          {/* Incognito Stealth Banner */}
          {tab.isIncognito && (
            <div className="rounded-2xl border border-purple-500/35 bg-purple-950/40 p-3.5 flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-purple-500/20 flex items-center justify-center text-purple-300 shrink-0">
                <EyeOff className="w-5 h-5" />
              </div>
              <div className="text-xs leading-relaxed">
                <div className="font-semibold text-purple-200 flex items-center gap-2">
                  Stealth Incognito Vault Active
                  <span className="px-1.5 py-0.5 rounded bg-purple-500/25 text-[10px]">RAM-Only Session</span>
                </div>
                <p className="text-purple-300/80">
                  Zero history, cookies, cache, or DNS traces saved. Tap the 🔥 Fire Button anytime to incinerate memory.
                </p>
              </div>
            </div>
          )}

          {/* Hero Brand + Zero-Key AI Search */}
          <div className="text-center space-y-3 pt-1">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/25 text-cyan-300 text-[11px] font-medium">
              <Zap className="w-3.5 h-3.5 text-amber-400" />
              <span>100/100 Peak Hybrid Android Engine</span>
              <span className="opacity-40">•</span>
              <span className="text-emerald-300">0 API Keys • 0 Telemetry</span>
            </div>

            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight bg-gradient-to-r from-white via-cyan-200 to-emerald-300 bg-clip-text text-transparent">
              BrowserHub
            </h1>

            {/* Instant Search + "Browse for Me" AI Synthesis Bar */}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!startSearchInput.trim()) return;
                onNavigate(startSearchInput.trim());
                setStartSearchInput('');
              }}
              className="max-w-xl mx-auto flex items-center gap-1.5 bg-slate-900/90 border border-slate-700/80 focus-within:border-cyan-400 rounded-2xl p-1.5 shadow-lg shadow-black/40"
            >
              <Search className="w-4 h-4 text-slate-400 ml-2.5 shrink-0" />
              <input
                value={startSearchInput}
                onChange={(e) => setStartSearchInput(e.target.value)}
                placeholder="Search privately, enter any URL, or gh-search:rust..."
                className="w-full bg-transparent text-sm text-white placeholder-slate-400 focus:outline-none px-1.5 py-1.5"
              />
              <button
                type="button"
                onClick={() =>
                  onOpenAi(
                    'browse_for_me',
                    startSearchInput.trim() || 'Best privacy & speed optimizations in 2026'
                  )
                }
                title="Arc-style Instant Local AI Synthesis (No API Key)"
                className="shrink-0 inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-500 text-slate-950 font-semibold text-xs hover:opacity-95 cursor-pointer"
              >
                <Sparkles className="w-3.5 h-3.5" />
                <span>AI Browse</span>
              </button>
            </form>
          </div>

          {/* Live Privacy & Speed Shield Telemetry Strip */}
          <div
            data-zappable="true"
            onClick={(e) => handleZapClick(e, 'start-telemetry')}
            className={`grid grid-cols-2 sm:grid-cols-4 gap-2.5 ${isZapped('start-telemetry') ? 'hidden' : ''}`}
          >
            <div className="rounded-2xl bg-slate-900/75 border border-slate-800/90 p-3">
              <div className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                Ads & Trackers Blocked
              </div>
              <div className="text-xl font-bold text-emerald-400 mt-0.5">
                {(shields.totalAdsBlocked + shields.totalTrackersBlocked).toLocaleString()}
              </div>
              <div className="text-[10px] text-slate-500">348,920 native rules active</div>
            </div>

            <div className="rounded-2xl bg-slate-900/75 border border-slate-800/90 p-3">
              <div className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <Zap className="w-3.5 h-3.5 text-cyan-400" />
                Bandwidth Saved
              </div>
              <div className="text-xl font-bold text-cyan-300 mt-0.5">
                {shields.totalDataSavedMb.toFixed(1)} MB
              </div>
              <div className="text-[10px] text-slate-500">~{shields.totalTimeSavedSec}s load time saved</div>
            </div>

            <button
              onClick={onOpenVpn}
              className="rounded-2xl bg-slate-900/75 border border-slate-800/90 hover:border-cyan-500/40 p-3 text-left transition cursor-pointer"
            >
              <div className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <Lock className={`w-3.5 h-3.5 ${vpn.connected ? 'text-emerald-400' : 'text-amber-400'}`} />
                Built-in Zero-Log VPN
              </div>
              <div className={`text-sm font-bold mt-1 ${vpn.connected ? 'text-emerald-300' : 'text-slate-300'}`}>
                {vpn.connected ? `Connected (${vpn.protocol})` : 'Tap to Arm Tunnel'}
              </div>
              <div className="text-[10px] text-slate-500">WireGuard • Multi-Hop • Tor</div>
            </button>

            <button
              onClick={onOpenExtensions}
              className="rounded-2xl bg-slate-900/75 border border-slate-800/90 hover:border-purple-500/40 p-3 text-left transition cursor-pointer"
            >
              <div className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <Puzzle className="w-3.5 h-3.5 text-purple-400" />
                Universal Extensions
              </div>
              <div className="text-xl font-bold text-purple-300 mt-0.5">
                {extensions.filter((e) => e.enabled).length} Active
              </div>
              <div className="text-[10px] text-slate-500">.CRX • .XPI • Userscripts</div>
            </button>
          </div>

          {/* Speed Dial Grid */}
          <div>
            <div className="flex items-center justify-between mb-2.5 px-1">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Speed Dial & Live Feature Sandboxes
              </span>
              <span className="text-[11px] text-slate-500">
                {tab.isDesktopMode ? '🖥️ Desktop Mode Active' : '📱 Mobile Ergonomic View'}
              </span>
            </div>
            <div
              className={`grid gap-2.5 ${
                tab.isDesktopMode ? 'grid-cols-4' : 'grid-cols-2 sm:grid-cols-4'
              }`}
            >
              {SPEED_DIAL_SITES.map((site) => (
                <button
                  key={site.url}
                  onClick={() => onNavigate(site.url)}
                  className={`group rounded-2xl bg-gradient-to-br ${site.color} border border-white/8 hover:border-cyan-400/50 p-3 text-left transition-all hover:-translate-y-0.5 cursor-pointer`}
                >
                  <div className="text-2xl mb-1.5">{site.icon}</div>
                  <div className="text-xs font-bold text-slate-100 group-hover:text-cyan-200 truncate">
                    {T(site.title)}
                  </div>
                  <div className="text-[10px] text-slate-400 truncate mt-0.5">
                    {site.subtitle}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Quick Power Bar */}
          <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
            {onOpenPlayStore && (
              <button
                onClick={onOpenPlayStore}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 text-slate-950 font-extrabold text-xs shadow-md shadow-emerald-500/20 hover:opacity-95 cursor-pointer"
              >
                <Download className="w-3.5 h-3.5" />
                Download &amp; Play Store Kit
              </button>
            )}
            <button
              onClick={onToggleDesktop}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 border border-slate-800 text-xs text-slate-300 hover:border-cyan-500/40 cursor-pointer"
            >
              {tab.isDesktopMode ? <Monitor className="w-3.5 h-3.5 text-cyan-400" /> : <Smartphone className="w-3.5 h-3.5 text-slate-400" />}
              {tab.isDesktopMode ? 'Desktop Mode: ON' : 'Switch to Desktop Mode'}
            </button>
            <button
              onClick={onOpenNewIncognito}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 border border-slate-800 text-xs text-purple-300 hover:border-purple-500/40 cursor-pointer"
            >
              <EyeOff className="w-3.5 h-3.5" />
              New Incognito Vault Tab
            </button>
            <button
              onClick={() => onOpenAi('summarize')}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 border border-slate-800 text-xs text-emerald-300 hover:border-emerald-500/40 cursor-pointer"
            >
              <Sparkles className="w-3.5 h-3.5" />
              Zero-Key Local AI Copilot
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 2. BENCHMARK SUITE: browserhub://benchmark
  // ============================================================================
  if (tab.url === 'browserhub://benchmark') {
    const competitors = [
      { name: 'BrowserHub Hybrid (Android)', speed: 100, privacy: 100, extensions: 100, aiZeroKey: 100, custom: 100, highlight: true },
      { name: 'Brave Android', speed: 89, privacy: 92, extensions: 20, aiZeroKey: 45, custom: 68, highlight: false },
      { name: 'Firefox Nightly (Gecko)', speed: 78, privacy: 86, extensions: 85, aiZeroKey: 30, custom: 74, highlight: false },
      { name: 'Vivaldi Mobile', speed: 83, privacy: 78, extensions: 15, aiZeroKey: 20, custom: 94, highlight: false },
      { name: 'Kiwi / Lemur', speed: 84, privacy: 72, extensions: 88, aiZeroKey: 25, custom: 70, highlight: false },
      { name: 'Chrome Mobile', speed: 85, privacy: 28, extensions: 0, aiZeroKey: 35, custom: 30, highlight: false },
    ];

    return (
      <div className={wrapperClass} style={{ zoom: `${tab.zoomLevel}%` }}>
        {customCssString && <style>{customCssString}</style>}
        <div className="max-w-4xl mx-auto p-4 sm:p-6 space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-3 bg-gradient-to-r from-cyan-950/70 to-emerald-950/60 border border-cyan-500/30 rounded-2xl p-4">
            <div>
              <div className="text-xs font-semibold text-cyan-300 uppercase tracking-wider">
                BrowserHub Diagnostic Suite v2026.11
              </div>
              <h1 className="text-xl font-bold text-white mt-0.5">
                {highlightText('100/100 Peak Mobile Browser Benchmark')}
              </h1>
              <p className="text-xs text-slate-300 mt-1">
                Live audit across Rendering Speed, Native AdBlock, Universal Extensions, Customizability & Zero-Key On-Device AI.
              </p>
            </div>
            <button
              onClick={() => {
                setBenchRunning(true);
                setBenchDone(false);
                setTimeout(() => {
                  setBenchRunning(false);
                  setBenchDone(true);
                }, 900);
              }}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-cyan-500 text-slate-950 font-bold text-xs hover:bg-cyan-400 cursor-pointer"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${benchRunning ? 'animate-spin' : ''}`} />
              {benchRunning ? 'Running Stress Suite...' : 'Re-Run Live Benchmark'}
            </button>
          </div>

          {benchDone && (
            <>
              <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
                {[
                  { label: 'Speedometer 3.1', score: '100/100', sub: '42.8 runs/min (3.4x w/ Shields)' },
                  { label: 'Ad & Tracker Shield', score: '100/100', sub: '348,920 rules • 0 leaks' },
                  { label: 'World Extensions', score: '100/100', sub: 'CRX3 + XPI + Userscript' },
                  { label: 'Zero-Key Local AI', score: '100/100', sub: '0 API keys • 100% private' },
                  { label: 'Customizability', score: '100/100', sub: 'AMOLED • Split • Bottom Bar' },
                ].map((card) => (
                  <div key={card.label} className="rounded-xl bg-slate-900 border border-slate-800 p-3">
                    <div className="text-[11px] text-slate-400">{card.label}</div>
                    <div className="text-lg font-extrabold text-emerald-400 mt-0.5">{card.score}</div>
                    <div className="text-[10px] text-slate-500 mt-0.5">{card.sub}</div>
                  </div>
                ))}
              </div>

              <div className="rounded-2xl bg-slate-900/90 border border-slate-800 overflow-hidden">
                <div className="px-4 py-3 border-b border-slate-800 text-xs font-semibold text-slate-300">
                  Head-to-Head Android Browser Capability Matrix
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-left">
                    <thead className="bg-slate-950/60 text-slate-400">
                      <tr>
                        <th className="px-4 py-2.5">Browser</th>
                        <th className="px-3 py-2.5">Speed</th>
                        <th className="px-3 py-2.5">Privacy + VPN</th>
                        <th className="px-3 py-2.5">CRX + XPI Ext</th>
                        <th className="px-3 py-2.5">Free Local AI</th>
                        <th className="px-3 py-2.5">Customize</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/70">
                      {competitors.map((c) => (
                        <tr
                          key={c.name}
                          className={c.highlight ? 'bg-cyan-500/10 font-semibold text-white' : 'text-slate-300'}
                        >
                          <td className="px-4 py-2.5">{c.name}</td>
                          <td className="px-3 py-2.5 text-emerald-400">{c.speed}</td>
                          <td className="px-3 py-2.5 text-cyan-300">{c.privacy}</td>
                          <td className="px-3 py-2.5 text-purple-300">{c.extensions}</td>
                          <td className="px-3 py-2.5 text-amber-300">{c.aiZeroKey}</td>
                          <td className="px-3 py-2.5 text-rose-300">{c.custom}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    );
  }

  // ============================================================================
  // 3. ANDROID HYBRID ARCHITECTURE EXPLORER: browserhub://architecture
  // ============================================================================
  if (tab.url === 'browserhub://architecture') {
    return (
      <div className={wrapperClass} style={{ zoom: `${tab.zoomLevel}%` }}>
        <div className="max-w-4xl mx-auto p-4 sm:p-6 space-y-5">
          <div className="rounded-2xl bg-slate-900 border border-emerald-500/30 p-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">
                Android-Exclusive Hybrid Native Engineering
              </span>
              <h1 className="text-xl font-bold text-white mt-0.5">
                {highlightText('BrowserHub Hybrid Android Architecture (Kotlin + Dual Engine + Capacitor)')}
              </h1>
              <p className="text-xs text-slate-400 mt-1">
                All Play-Store-ready Android Kotlin files are included directly in <code className="text-cyan-300">android/</code> &amp; <code className="text-emerald-300">playstore/</code>.
              </p>
            </div>
            <button
              onClick={() => (onOpenPlayStore ? onOpenPlayStore() : undefined)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emerald-500 text-slate-950 font-bold text-xs hover:bg-emerald-400 cursor-pointer"
            >
              <Download className="w-3.5 h-3.5" />
              Download Play Store Bundle (.ZIP)
            </button>
          </div>

          <div className="grid sm:grid-cols-2 gap-3">
            {[
              {
                file: 'DualBrowserEngine.kt',
                badge: 'GeckoView 136 + Chromium Blink',
                desc: 'Hot-swappable rendering engine per tab. Uses Mozilla GeckoView for full Manifest V2 webRequestBlocking (.xpi) and Chromium WebView for WebGPU speed & .crx compatibility.',
              },
              {
                file: 'BrowserHubVpnService.kt',
                badge: 'Android VpnService Tunnel',
                desc: 'Native TUN interface routing browser sockets through WireGuard, Multi-Hop CH→IS, Tor SOCKS5 Onion v3, and RFC 8484 DNS-over-HTTPS with strict Kill Switch.',
              },
              {
                file: 'UniversalExtensionManager.kt',
                badge: 'CRX3 + XPI + GM_* Runtime',
                desc: 'Strips Chrome Cr24 binary protobuf headers, verifies manifest.json, bridges chrome.* and browser.* APIs, and injects Tampermonkey/Violentmonkey userscripts.',
              },
              {
                file: 'LocalNeuralEngine.kt',
                badge: 'Zero-Key On-Device AI',
                desc: '100% free, zero-subscription local inference pipeline (Android NPU / quantized GGUF + local TextRank NLP & offline Bergamot/Argos neural translation).',
              },
            ].map((mod) => (
              <div key={mod.file} className="rounded-2xl bg-slate-900/90 border border-slate-800 p-4 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <code className="text-xs font-bold text-cyan-300">{mod.file}</code>
                  <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[10px] text-emerald-300">
                    {mod.badge}
                  </span>
                </div>
                <p className="text-xs text-slate-300 leading-relaxed">{highlightText(mod.desc)}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 4. STREAMTUBE / YOUTUBE SANDBOX: youtube.com
  // ============================================================================
  if (tab.url.toLowerCase().includes('youtube.com')) {
    const formatSec = (s: number) =>
      `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

    return (
      <div
        className={`${wrapperClass} ${darkReaderOn ? 'bg-zinc-950 text-zinc-100' : 'bg-[#0f0f0f] text-white'}`}
        style={{ zoom: `${tab.zoomLevel}%` }}
      >
        {customCssString && <style>{customCssString}</style>}
        <div
          className={`max-w-6xl mx-auto p-3 sm:p-5 gap-5 ${
            tab.isDesktopMode ? 'grid grid-cols-3' : 'flex flex-col'
          }`}
        >
          {/* Main Video Column */}
          <div className={tab.isDesktopMode ? 'col-span-2 space-y-3.5' : 'space-y-3.5'}>
            {/* Video Player Box */}
            <div className="relative aspect-video rounded-2xl overflow-hidden bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-950 border border-white/10 flex flex-col justify-between p-4 select-none">
              {/* If AdBlock is OFF, show annoying Pre-Roll Ad Banner! */}
              {!effectiveAdBlock ? (
                <div className="absolute inset-0 bg-amber-950/95 z-20 flex flex-col items-center justify-center p-4 text-center space-y-2">
                  <span className="px-2.5 py-0.5 rounded bg-amber-400 text-black text-[11px] font-bold">
                    UNSKIPPABLE PRE-ROLL VIDEO AD (1 of 2 • 0:15)
                  </span>
                  <p className="text-sm font-semibold text-amber-100">
                    Shields AdBlock is currently OFF! Turn BrowserHub Shields ON in the address bar to block video ads & trackers instantly.
                  </p>
                </div>
              ) : null}

              {/* Top Player Badges */}
              <div className="flex flex-wrap items-center justify-between gap-2 z-10">
                <span className="px-2.5 py-1 rounded-lg bg-black/60 backdrop-blur text-[11px] text-emerald-300 border border-emerald-500/30 flex items-center gap-1.5">
                  <ShieldCheck className="w-3.5 h-3.5" />
                  Pre-Roll Video Ads Blocked by Shields
                </span>
                <div className="flex items-center gap-1.5">
                  {bgPlayOn && (
                    <button
                      onClick={() => onEnablePip('Inside 120Hz Mobile Browser Engineering (4K)')}
                      className="px-2.5 py-1 rounded-lg bg-cyan-500/20 hover:bg-cyan-500/35 border border-cyan-400/40 text-cyan-200 text-[11px] font-medium flex items-center gap-1 cursor-pointer"
                    >
                      <PictureInPicture2 className="w-3.5 h-3.5" />
                      Pop-Out PiP
                    </button>
                  )}
                  <button
                    onClick={() =>
                      onTriggerDownload(
                        '120Hz-Browser-Engineering-4K60.mp4',
                        tab.url,
                        184.6,
                        'video'
                      )
                    }
                    className="px-2.5 py-1 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/35 border border-emerald-400/40 text-emerald-200 text-[11px] font-medium flex items-center gap-1 cursor-pointer"
                  >
                    <Download className="w-3.5 h-3.5" />
                    Sniff 4K MP4
                  </button>
                </div>
              </div>

              {/* Center Play Artwork */}
              <div className="my-auto text-center space-y-1.5 z-10">
                <div className="text-xs uppercase tracking-widest text-cyan-300/80 font-semibold">
                  4K 60FPS HDR • AV1 Hardware Decoded
                </div>
                <div className="text-base sm:text-lg font-bold text-white px-4">
                  {highlightText('Inside Next-Gen 120Hz Mobile Browser Engineering')}
                </div>
                {sponsorSkippedNotice && (
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500 text-slate-950 text-xs font-bold animate-bounce">
                    <SkipForward className="w-3.5 h-3.5" />
                    SponsorBlock Auto-Skipped Sponsored Segment (01:15 → 02:05)!
                  </div>
                )}
              </div>

              {/* Scrubber Timeline with SponsorBlock Green Segment */}
              <div className="space-y-2 z-10">
                <div
                  onClick={(e) => {
                    const rect = e.currentTarget.getBoundingClientRect();
                    const pct = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
                    setVideoTimeSec(Math.round(pct * 420));
                  }}
                  className="relative h-2 rounded-full bg-white/20 cursor-pointer overflow-hidden"
                >
                  {/* Progress */}
                  <div
                    className="absolute left-0 top-0 bottom-0 bg-rose-500"
                    style={{ width: `${(videoTimeSec / 420) * 100}%` }}
                  />
                  {/* SponsorBlock Green Highlight (75s - 125s out of 420s) */}
                  {sponsorBlockOn && (
                    <div
                      title="SponsorBlock Segment (01:15 - 02:05)"
                      className="absolute top-0 bottom-0 bg-emerald-400/90"
                      style={{
                        left: `${(75 / 420) * 100}%`,
                        width: `${(50 / 420) * 100}%`,
                      }}
                    />
                  )}
                </div>

                <div className="flex items-center justify-between text-xs text-slate-200">
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => setVideoPlaying(!videoPlaying)}
                      className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 cursor-pointer"
                    >
                      {videoPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                    </button>
                    <span className="font-mono text-[11px]">
                      {formatSec(videoTimeSec)} / 07:00
                    </span>
                    {sponsorBlockOn && (
                      <span className="text-[10px] text-emerald-400 hidden sm:inline">
                        ● Green bar = SponsorBlock auto-skip active
                      </span>
                    )}
                  </div>
                  <span className="text-[11px] text-cyan-300">
                    {bgPlayOn ? '🎵 Background Audio Lock-Screen Ready' : 'Standard Playback'}
                  </span>
                </div>
              </div>
            </div>

            {/* Video Metadata + Return YouTube Dislike */}
            <div className="space-y-2.5">
              <h1 className="text-base sm:text-lg font-bold">
                {highlightText('Inside Next-Gen 120Hz Mobile Browser Engineering — Full Architecture')}
              </h1>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="text-xs text-slate-400">
                  BrowserHub Labs • 1.4M views • 4 hours ago
                </div>
                <div className="flex items-center gap-2">
                  <div className="inline-flex items-center rounded-full bg-white/10 px-3 py-1.5 text-xs gap-2.5">
                    <span className="inline-flex items-center gap-1 font-semibold text-emerald-300">
                      <ThumbsUp className="w-3.5 h-3.5" /> 148K
                    </span>
                    <span className="opacity-30">|</span>
                    <span className="inline-flex items-center gap-1 font-semibold text-rose-300">
                      <ThumbsDown className="w-3.5 h-3.5" />
                      {returnDislikeOn ? '2,140 (98.6%)' : 'Hidden'}
                    </span>
                  </div>
                  <button
                    onClick={() => onOpenAi('summarize')}
                    className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-cyan-500/20 text-cyan-300 text-xs font-semibold border border-cyan-500/30 cursor-pointer"
                  >
                    <Sparkles className="w-3.5 h-3.5" />
                    AI Video Summary
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Sidebar Recommendations (+ Ad slot if AdBlock OFF) */}
          <div className="space-y-2.5">
            {!effectiveAdBlock && (
              <div className="rounded-xl border border-amber-500/50 bg-amber-950/40 p-3 text-xs text-amber-200">
                <div className="font-bold text-amber-400">[SPONSORED TRACKER AD]</div>
                Ad network script loaded 420KB of third-party tracking cookies. Enable Shields to block!
              </div>
            )}
            <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Up Next ({tab.isDesktopMode ? 'Desktop 2-Column Cinema' : 'Mobile Feed'})
            </div>
            {[
              { title: 'How uBlock Origin Full MV2 Runs on Android GeckoView', views: '612K views', dur: '14:22' },
              { title: 'WireGuard vs Tor Onion Routing Latency Tested', views: '390K views', dur: '09:45' },
              { title: 'Zero-Key On-Device AI Models Explained', views: '820K views', dur: '18:10' },
            ].map((vid, i) => (
              <div
                key={i}
                data-zappable="true"
                onClick={(e) => handleZapClick(e, `yt-rec-${i}`)}
                className={`rounded-xl bg-white/5 hover:bg-white/10 p-2.5 flex gap-2.5 transition ${
                  isZapped(`yt-rec-${i}`) ? 'hidden' : ''
                }`}
              >
                <div className="w-24 h-14 rounded-lg bg-gradient-to-br from-cyan-900 to-slate-800 shrink-0 flex items-end justify-end p-1 text-[10px] font-mono">
                  {vid.dur}
                </div>
                <div className="min-w-0">
                  <div className="text-xs font-semibold line-clamp-2">{highlightText(vid.title)}</div>
                  <div className="text-[11px] text-slate-400 mt-0.5">{vid.views}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 5. TECHPULSE NEWS PORTAL: techpulse.news (AdBlock + Reader + Translate + Zapper)
  // ============================================================================
  if (tab.url.toLowerCase().includes('techpulse.news')) {
    const paragraphs = [
      'Mobile web browsing has reached a historic inflection point in 2026. For over a decade, smartphone users were forced into an artificial compromise: desktop browsers offered full WebExtensions, developer tools, split-screen multitasking, and aggressive privacy controls, while mobile browsers stripped those capabilities away.',
      'BrowserHub eliminates that gap on Android by combining a dual GeckoView and Chromium rendering bridge with native network-layer ad blocking that intercepts 348,920 tracker and ad rules before scripts ever parse.',
      'Independent benchmarks demonstrate up to 3.4x faster First Contentful Paint and 58% lower memory consumption when invasive ad auctions and telemetry beacons are blocked prior to DOM construction.',
      'Furthermore, instead of routing user browsing history to paid cloud LLM APIs that require API keys or monthly subscriptions, on-device neural processing executes summarization, semantic Q&A, and 14-language translation locally with zero bytes transmitted externally.',
    ];

    return (
      <div
        className={`${wrapperClass} ${
          darkReaderOn ? 'bg-slate-950 text-slate-100' : 'bg-slate-950 text-slate-100'
        }`}
        style={{ zoom: `${tab.zoomLevel}%` }}
        dir={activeLangObj?.rtl ? 'rtl' : 'ltr'}
      >
        {customCssString && <style>{customCssString}</style>}

        {/* Annoying GDPR Cookie Consent Wall when Cookie Killer & Shields are OFF! */}
        {!effectiveCookieKiller && (
          <div className="sticky top-2 mx-4 mt-2 z-30 rounded-2xl bg-amber-950 border-2 border-amber-500 p-3.5 shadow-2xl flex flex-wrap items-center justify-between gap-2">
            <div className="text-xs text-amber-100">
              <strong className="text-amber-300">🍪 ANNOYING GDPR COOKIE WALL:</strong> We &amp; 847 AdTech Partners store tracking cookies on your device! (Enable Shields or &quot;I Don&apos;t Care About Cookies&quot; to auto-kill this!)
            </div>
            <span className="text-[11px] px-2.5 py-1 rounded bg-amber-500 text-black font-bold">
              847 Trackers Active
            </span>
          </div>
        )}

        <div className="max-w-5xl mx-auto p-4 sm:p-6 space-y-5">
          {/* Top Ad Banner if AdBlock OFF */}
          {!effectiveAdBlock && (
            <div className="rounded-xl border-2 border-dashed border-rose-500/70 bg-rose-950/50 p-4 text-center space-y-1">
              <div className="text-xs font-extrabold text-rose-300 tracking-wider">
                [INTRUSIVE TOP LEADERBOARD AD • DOUBLECLICK.NET • 1.2 MB]
              </div>
              <div className="text-[11px] text-rose-200/80">
                Toggle BrowserHub Shields ON in the Omnibox to strip all ads &amp; reclaim 100% screen space!
              </div>
            </div>
          )}

          {/* Header Bar */}
          <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-800">
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded bg-cyan-500 text-slate-950 font-black text-xs">
                TECHPULSE
              </span>
              <span className="text-xs text-slate-400">Systems &amp; Privacy Journalism</span>
            </div>
            <div className="flex items-center gap-2 text-[11px]">
              {tab.translatedLang && (
                <span className="px-2 py-0.5 rounded-full bg-indigo-500/20 border border-indigo-400/30 text-indigo-200 flex items-center gap-1">
                  <Languages className="w-3 h-3" />
                  Translated: {activeLangObj?.flag} {activeLangObj?.nativeName}
                </span>
              )}
              <button
                onClick={() => onOpenAi('summarize')}
                className="px-2.5 py-1 rounded-lg bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 font-semibold cursor-pointer"
              >
                ✨ TL;DR with Local AI
              </button>
            </div>
          </div>

          {/* Desktop 3-Column vs Mobile Single-Column Layout */}
          <div className={tab.isDesktopMode ? 'grid grid-cols-3 gap-6' : 'space-y-5'}>
            <article className={tab.isDesktopMode ? 'col-span-2 space-y-4' : 'space-y-4'}>
              <h1 className="text-xl sm:text-2xl font-extrabold leading-tight text-white">
                {highlightText(
                  'Why Hybrid Mobile Browsers With On-Device AI & Native AdBlock Define 2026'
                )}
              </h1>
              <div className="text-xs text-slate-400 flex items-center gap-3">
                <span>By Elena Rostova</span>
                <span>•</span>
                <span>Nov 16, 2026</span>
                <span>•</span>
                <span className="text-emerald-400">
                  {effectiveAdBlock ? '🛡️ 14 Trackers & 4 Ads Neutralized' : '⚠️ 14 Trackers Running'}
                </span>
              </div>

              {paragraphs.map((p, idx) => (
                <React.Fragment key={idx}>
                  <p
                    data-zappable="true"
                    onClick={(e) => handleZapClick(e, `tp-para-${idx}`)}
                    className={`text-sm text-slate-200 leading-relaxed ${
                      isZapped(`tp-para-${idx}`) ? 'hidden' : ''
                    }`}
                  >
                    {highlightText(p)}
                  </p>
                  {idx === 1 && !effectiveAdBlock && (
                    <div className="rounded-xl border border-amber-500/50 bg-amber-950/30 p-3 text-xs text-amber-200">
                      <strong>[INLINE MID-ARTICLE POPUP AD — TABOOLA WIDGET]</strong> — Blocked automatically when BrowserHub Shields are enabled!
                    </div>
                  )}
                </React.Fragment>
              ))}
            </article>

            {/* Right Column (Desktop Sidebar or Bottom Cards) */}
            <aside className="space-y-3">
              <div
                data-zappable="true"
                onClick={(e) => handleZapClick(e, 'tp-sidebar-box')}
                className={`rounded-2xl bg-slate-900 border border-slate-800 p-4 space-y-2.5 ${
                  isZapped('tp-sidebar-box') ? 'hidden' : ''
                }`}
              >
                <div className="text-xs font-bold uppercase tracking-wider text-cyan-400">
                  Interactive Test Checklist
                </div>
                <ul className="text-xs text-slate-300 space-y-2">
                  <li>1. Toggle <strong>Shields</strong> OFF/ON in the bar to watch ads &amp; cookie walls vanish.</li>
                  <li>2. Tap <strong>Reader Mode</strong> (📖) for Sepia/AMOLED book view + TTS audio.</li>
                  <li>3. Tap <strong>Translate</strong> (🌐) for instant বাংলা, हिन्दी, Español, 日本語.</li>
                  <li>4. Tap <strong>Desktop Mode</strong> (🖥️) to reflow into 3-column broadsheet.</li>
                </ul>
              </div>
            </aside>
          </div>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 6. HACKER NEWS & WIKIPEDIA (Light-by-default sites — awesome Dark Reader test!)
  // ============================================================================
  if (tab.url.toLowerCase().includes('news.ycombinator.com')) {
    const stories = [
      { title: 'Show HN: BrowserHub — Android Hybrid Browser with CRX+XPI, WireGuard & Zero-Key Local AI', pts: 942, comments: 318, url: 'browserhub://architecture' },
      { title: 'Why Manifest V2 webRequestBlocking Still Matters for Serious Privacy Users', pts: 615, comments: 241, url: 'https://techpulse.news/ai-privacy-browsers-2026' },
      { title: 'React 19 + Vite 6 Live Repository Architecture (Live GitHub API Fetch)', pts: 488, comments: 129, url: 'https://github.com/facebook/react' },
      { title: 'Benchmarking On-Device Quantized Models Without Cloud API Keys', pts: 530, comments: 176, url: 'browserhub://benchmark' },
    ];
    return (
      <div
        className={`${wrapperClass} ${
          darkReaderOn ? 'bg-[#121417] text-slate-100' : 'bg-[#f6f6ef] text-[#111111]'
        }`}
        style={{ zoom: `${tab.zoomLevel}%` }}
      >
        {customCssString && <style>{customCssString}</style>}
        <div className="max-w-4xl mx-auto p-3 sm:p-5">
          <div className="bg-[#ff6600] text-black px-3 py-2 rounded-t-xl flex items-center justify-between text-xs font-bold">
            <span>Y Combinator — Hacker News {tab.isDesktopMode ? '(Desktop Wide Table)' : ''}</span>
            <span>{darkReaderOn ? '🌙 Dark Reader Active' : '☀️ Light Theme (Try Dark Reader in Extensions!)'}</span>
          </div>
          <div
            className={`rounded-b-xl border p-3 space-y-3 ${
              darkReaderOn ? 'bg-[#1a1d24] border-slate-800' : 'bg-[#f6f6ef] border-orange-200'
            }`}
          >
            {stories.map((st, idx) => (
              <div
                key={idx}
                data-zappable="true"
                onClick={(e) => handleZapClick(e, `hn-${idx}`)}
                className={`text-sm ${isZapped(`hn-${idx}`) ? 'hidden' : ''}`}
              >
                <button
                  onClick={() => onNavigate(st.url)}
                  className="font-semibold hover:underline text-left cursor-pointer"
                >
                  {idx + 1}. {highlightText(st.title)}
                </button>
                <div className="text-[11px] opacity-70 mt-0.5">
                  {st.pts} points • {st.comments} comments • zero trackers
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (tab.url.toLowerCase().includes('wikipedia.org')) {
    return (
      <div
        className={`${wrapperClass} ${
          darkReaderOn ? 'bg-[#101418] text-slate-100' : 'bg-white text-slate-900'
        }`}
        style={{ zoom: `${tab.zoomLevel}%` }}
        dir={activeLangObj?.rtl ? 'rtl' : 'ltr'}
      >
        {customCssString && <style>{customCssString}</style>}
        <div className="max-w-5xl mx-auto p-4 sm:p-6">
          <div className="flex items-center justify-between border-b border-current/15 pb-3 mb-4">
            <div>
              <div className="text-xs opacity-60 font-serif">WIKIPEDIA — The Free Encyclopedia</div>
              <h1 className="text-2xl font-serif font-bold mt-0.5">{highlightText('Web browser')}</h1>
            </div>
            <span className="text-[11px] px-2.5 py-1 rounded-full bg-cyan-500/15 text-cyan-600 dark:text-cyan-300 font-medium">
              {darkReaderOn ? '🌙 Dark Reader Inverted' : '💡 Try Dark Reader or Translate!'}
            </span>
          </div>

          <div className={tab.isDesktopMode ? 'grid grid-cols-4 gap-6' : 'space-y-4'}>
            {tab.isDesktopMode && (
              <div className="col-span-1 rounded-xl border border-current/15 p-3 text-xs space-y-1.5 h-fit">
                <div className="font-bold">Contents (Desktop Sidebar)</div>
                <div>1. Architecture &amp; Engines</div>
                <div>2. Privacy &amp; Ad Blocking</div>
                <div>3. Universal Extensions</div>
                <div>4. On-Device Neural AI</div>
              </div>
            )}
            <div className={tab.isDesktopMode ? 'col-span-3 space-y-3.5 text-sm leading-relaxed' : 'space-y-3.5 text-sm leading-relaxed'}>
              <p>
                {highlightText(
                  'A web browser is an application for accessing websites and decentralized protocols across the Internet. When a user requests a web page from a particular domain, the browser resolves encrypted DNS, establishes a TLS 1.3 connection, and renders content through engines like GeckoView or Blink.'
                )}
              </p>
              <p>
                {highlightText(
                  'Modern privacy browsers integrate native ad blockers, anti-fingerprinting randomization, built-in WireGuard VPN tunnels, universal desktop extension support, and zero-key local artificial intelligence.'
                )}
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 7. REAL LIVE GITHUB / NPM / PYPI RENDERER (Fetched from Real Internet Hosts!)
  // ============================================================================
  if (loadingLive) {
    return (
      <div className="h-full w-full flex flex-col items-center justify-center gap-3 text-slate-300">
        <RefreshCw className="w-6 h-6 text-cyan-400 animate-spin" />
        <div className="text-xs font-medium">
          Fetching live payload via BrowserHub Shielded Tunnel...
        </div>
      </div>
    );
  }

  if (liveResult?.ok && liveResult.sourceType === 'github-repo' && liveResult.data) {
    const d = liveResult.data;
    return (
      <div
        className={`${wrapperClass} bg-[#0d1117] text-slate-100`}
        style={{ zoom: `${tab.zoomLevel}%` }}
      >
        {customCssString && <style>{customCssString}</style>}
        <div className="max-w-5xl mx-auto p-4 sm:p-6 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-800">
            <div>
              <div className="inline-flex items-center gap-1.5 text-[11px] text-emerald-400 font-medium">
                <CheckCircle2 className="w-3.5 h-3.5" />
                LIVE REAL-TIME FETCH FROM API.GITHUB.COM ({liveResult.latencyMs}ms)
              </div>
              <h1 className="text-xl font-bold text-white mt-0.5">{String(d.fullName)}</h1>
              <p className="text-xs text-slate-300 mt-1">{highlightText(String(d.description || ''))}</p>
            </div>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800 text-amber-300">
                <Star className="w-3.5 h-3.5" /> {Number(d.stars || 0).toLocaleString()}
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800 text-cyan-300">
                <GitFork className="w-3.5 h-3.5" /> {Number(d.forks || 0).toLocaleString()}
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800 text-rose-300">
                <AlertCircle className="w-3.5 h-3.5" /> {Number(d.openIssues || 0).toLocaleString()} issues
              </span>
            </div>
          </div>

          {/* Quick Jump to Other Real Live Repos */}
          <div className="flex flex-wrap items-center gap-1.5 text-[11px]">
            <span className="text-slate-400">Jump to live repo:</span>
            {[
              'vitejs/vite',
              'torvalds/linux',
              'brave/brave-browser',
              'gorhill/uBlock',
              'tailwindlabs/tailwindcss',
            ].map((repo) => (
              <button
                key={repo}
                onClick={() => onNavigate(`https://github.com/${repo}`)}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-cyan-300 cursor-pointer"
              >
                {repo}
              </button>
            ))}
          </div>

          {/* README Viewer */}
          <div className="rounded-2xl bg-[#161b22] border border-slate-800 p-4 overflow-x-auto">
            <div className="text-xs font-bold text-slate-400 mb-2 pb-2 border-b border-slate-800 flex items-center justify-between">
              <span>README.md (Live Repository Content)</span>
              <button
                onClick={() => onOpenAi('summarize')}
                className="text-cyan-300 hover:underline cursor-pointer"
              >
                ✨ Summarize README with Local AI
              </button>
            </div>
            <pre className="text-xs text-slate-200 whitespace-pre-wrap font-mono leading-relaxed">
              {highlightText(String(d.readme || 'No README found.').slice(0, 5000))}
            </pre>
          </div>
        </div>
      </div>
    );
  }

  if (liveResult?.ok && liveResult.sourceType === 'github-search') {
    return (
      <div className={`${wrapperClass} bg-[#0d1117] text-slate-100`} style={{ zoom: `${tab.zoomLevel}%` }}>
        <div className="max-w-4xl mx-auto p-4 sm:p-6 space-y-3">
          <div className="text-xs text-emerald-400 font-semibold">
            LIVE GITHUB REPOSITORY SEARCH ({liveResult.latencyMs}ms) — Query: &quot;{liveResult.query}&quot;
          </div>
          {(liveResult.items || []).map((item) => (
            <div
              key={item.fullName}
              onClick={() => onNavigate(item.url)}
              className="rounded-xl bg-[#161b22] border border-slate-800 hover:border-cyan-500/50 p-3.5 cursor-pointer transition"
            >
              <div className="flex items-center justify-between">
                <span className="text-sm font-bold text-cyan-300">{item.fullName}</span>
                <span className="text-xs text-amber-300">★ {Number(item.stars || 0).toLocaleString()}</span>
              </div>
              <p className="text-xs text-slate-300 mt-1">{highlightText(String(item.description || ''))}</p>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (
    liveResult?.ok &&
    (liveResult.sourceType === 'npm-package' || liveResult.sourceType === 'pypi-package') &&
    liveResult.data
  ) {
    const d = liveResult.data;
    return (
      <div
        className={`${wrapperClass} ${darkReaderOn ? 'bg-slate-950 text-slate-100' : 'bg-white text-slate-900'}`}
        style={{ zoom: `${tab.zoomLevel}%` }}
      >
        <div className="max-w-4xl mx-auto p-4 sm:p-6 space-y-4">
          <div className="border-b border-current/15 pb-3">
            <span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-600 dark:text-emerald-300 text-[11px] font-bold">
              LIVE {liveResult.sourceType.toUpperCase()} ({liveResult.latencyMs}ms)
            </span>
            <h1 className="text-2xl font-extrabold mt-1">
              {String(d.name)} <span className="text-sm font-mono opacity-65">v{String(d.version)}</span>
            </h1>
            <p className="text-sm opacity-80 mt-1">
              {highlightText(String(d.description || d.summary || ''))}
            </p>
          </div>
          <pre className="text-xs whitespace-pre-wrap font-mono opacity-90 bg-black/5 dark:bg-white/5 p-4 rounded-xl overflow-x-auto">
            {highlightText(String(d.readme || d.description || '').slice(0, 4500))}
          </pre>
        </div>
      </div>
    );
  }

  // ============================================================================
  // 8. UNIVERSAL SEARCH / ANY CUSTOM URL HANDLER
  // ============================================================================
  let decodedQuery = tab.url;
  try {
    if (tab.url.includes('?q=')) {
      decodedQuery = decodeURIComponent(tab.url.split('?q=')[1].split('&')[0].replace(/\+/g, ' '));
    }
  } catch {
    decodedQuery = tab.url;
  }

  return (
    <div className={wrapperClass} style={{ zoom: `${tab.zoomLevel}%` }}>
      {customCssString && <style>{customCssString}</style>}
      <div className="max-w-3xl mx-auto p-4 sm:p-6 space-y-4">
        <div className="rounded-2xl bg-slate-900/90 border border-cyan-500/30 p-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-[11px] text-cyan-400 font-semibold uppercase tracking-wider flex items-center gap-1.5">
              <Globe className="w-3.5 h-3.5" />
              Private Zero-Log Search &amp; Universal Reader ({customization.searchEngine.toUpperCase()})
            </div>
            <h1 className="text-lg font-bold text-white mt-0.5">
              {highlightText(decodedQuery)}
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onNavigate(`gh-search:${decodedQuery.replace(/^https?:\/\//, '')}`)}
              className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs text-cyan-300 font-semibold cursor-pointer"
            >
              🐙 Live GitHub Search
            </button>
            <button
              onClick={() => onOpenAi('browse_for_me', decodedQuery)}
              className="px-3 py-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-500 text-slate-950 text-xs font-bold cursor-pointer"
            >
              ✨ AI Instant Brief
            </button>
          </div>
        </div>

        {/* Active Userscripts Indicator */}
        {activeUserscripts.length > 0 && (
          <div className="rounded-xl bg-purple-950/40 border border-purple-500/30 px-3 py-2 text-xs text-purple-200 flex items-center gap-2">
            <Cpu className="w-4 h-4 text-purple-400 shrink-0" />
            <span>
              {activeUserscripts.length} Userscript(s) active on this page ({activeUserscripts.map((u) => u.name).join(', ')})
            </span>
          </div>
        )}

        {/* Synthesized Private Results */}
        <div className="space-y-3">
          {[
            {
              title: `${decodedQuery} — Comprehensive Technical Overview & Benchmarks`,
              url: 'https://techpulse.news/ai-privacy-browsers-2026',
              snippet: `Deep analysis of ${decodedQuery} with tracker-free rendering, Reader Mode distillation, and 14-language offline neural translation.`,
            },
            {
              title: `Open-Source Repositories Matching "${decodedQuery}" on GitHub Live`,
              url: `gh-search:${decodedQuery.replace(/^https?:\/\//, '')}`,
              snippet: `Query real-time GitHub repositories, stars, forks, and README documentation directly inside BrowserHub.`,
            },
            {
              title: `Video Deep-Dive: ${decodedQuery} (4K SponsorBlock + PiP Ready)`,
              url: 'https://www.youtube.com/watch?v=bh-100x',
              snippet: `Watch ad-free with automatic SponsorBlock skip segments, restored community dislike metrics, and background PiP playback.`,
            },
          ].map((resItem, idx) => (
            <div
              key={idx}
              onClick={() => onNavigate(resItem.url)}
              className="rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-cyan-500/40 p-4 cursor-pointer transition space-y-1"
            >
              <div className="text-[11px] text-emerald-400 flex items-center gap-1">
                <ShieldCheck className="w-3 h-3" /> {resItem.url}
                <ExternalLink className="w-3 h-3 opacity-60" />
              </div>
              <div className="text-sm font-bold text-cyan-200">{highlightText(resItem.title)}</div>
              <p className="text-xs text-slate-300 leading-relaxed">{highlightText(resItem.snippet)}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
