import React from 'react';
import {
  X,
  Plus,
  EyeOff,
  Home,
  RotateCw,
  ArrowRight,
  Monitor,
  BookOpen,
  Languages,
  Search,
  Columns2,
  ZoomIn,
  ZoomOut,
  ShieldCheck,
  Lock,
  Crosshair,
  Flame,
  Puzzle,
  SlidersHorizontal,
  Terminal,
  Zap,
  Download,
  Sparkles,
  PanelBottom,
  PanelTop,
} from 'lucide-react';
import type {
  BrowserCustomization,
  BrowserTab,
  SearchEngineId,
  ShieldState,
  VpnState,
} from '../types/browser';
import { SEARCH_ENGINE_OPTIONS } from '../data/presets';
import { SearchEngineIcon } from './VectorIcons';

interface MenuHubModalProps {
  tab: BrowserTab;
  tabsCount: number;
  shields: ShieldState;
  vpn: VpnState;
  enabledExtensionsCount: number;
  customization: BrowserCustomization;
  zapperMode: boolean;
  showDevTools: boolean;
  hasSplitView: boolean;
  canGoForward: boolean;
  onClose: () => void;
  onSelectSearchEngine: (id: SearchEngineId) => void;
  onNewTab: (incognito: boolean) => void;
  onGoHome: () => void;
  onReload: () => void;
  onGoForward: () => void;
  onToggleDesktop: () => void;
  onToggleReader: () => void;
  onOpenTranslate: () => void;
  onOpenFind: () => void;
  onToggleSplit: () => void;
  onChangeZoom: (nextZoom: number) => void;
  onOpenShields: () => void;
  onOpenVpn: () => void;
  onToggleZapper: () => void;
  onFireWipe: () => void;
  onOpenExtensions: () => void;
  onOpenCustomize: () => void;
  onToggleDevTools: () => void;
  onOpenBenchmark: () => void;
  onOpenAi: (mode: string) => void;
  onToggleToolbarPosition: () => void;
  onOpenPlayStore: () => void;
}

export const MenuHubModal: React.FC<MenuHubModalProps> = ({
  tab,
  tabsCount,
  shields,
  vpn,
  enabledExtensionsCount,
  customization,
  zapperMode,
  showDevTools,
  hasSplitView,
  canGoForward,
  onClose,
  onSelectSearchEngine,
  onNewTab,
  onGoHome,
  onReload,
  onGoForward,
  onToggleDesktop,
  onToggleReader,
  onOpenTranslate,
  onOpenFind,
  onToggleSplit,
  onChangeZoom,
  onOpenShields,
  onOpenVpn,
  onToggleZapper,
  onFireWipe,
  onOpenExtensions,
  onOpenCustomize,
  onToggleDevTools,
  onOpenBenchmark,
  onOpenAi,
  onToggleToolbarPosition,
  onOpenPlayStore,
}) => {
  const runAndClose = (fn: () => void) => {
    onClose();
    fn();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
      style={{ backgroundColor: 'rgba(2, 6, 23, 0.82)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-t-3xl sm:rounded-3xl border border-slate-800 shadow-2xl flex flex-col max-h-[88vh] overflow-hidden"
        style={{ backgroundColor: '#090e1a' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          className="px-5 py-3.5 border-b border-slate-800 flex items-center justify-between shrink-0"
          style={{ backgroundColor: '#0f172a' }}
        >
          <div>
            <div className="text-sm font-bold text-white">BrowserHub Control Menu</div>
            <div className="text-[11px] text-slate-400">
              All tools &amp; settings organized by category
            </div>
          </div>
          <button aria-label="Close" title="Close"
            onClick={onClose}
            className="p-2 rounded-xl hover:bg-slate-800 text-slate-300 hover:text-white cursor-pointer"
          >
            <X size={18} />
          </button>
        </div>

        {/* Scrollable Categorized Sections */}
        <div className="flex-1 overflow-y-auto p-4 space-y-5">
          {/* CATEGORY 1: SEARCH ENGINE & NAVIGATION */}
          <section className="space-y-2.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-cyan-400 px-1">
              1. Search Engine &amp; Navigation
              <span className="ml-2 text-[10px] font-medium normal-case text-slate-400">
                {tabsCount} open tab{tabsCount === 1 ? '' : 's'}
              </span>
            </div>

            {/* Search Engine Selector Pills */}
            <div className="rounded-2xl border border-slate-800 p-3 space-y-2" style={{ backgroundColor: '#111827' }}>
              <div className="text-xs font-semibold text-slate-200">
                Default Search Engine
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
                {SEARCH_ENGINE_OPTIONS.map((eng) => {
                  const active = customization.searchEngine === eng.id;
                  return (
                    <button
                      key={eng.id}
                      onClick={() => onSelectSearchEngine(eng.id)}
                      className={`px-2.5 py-2 rounded-xl border text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition ${
                        active
                          ? 'bg-cyan-500/20 border-cyan-400 text-white'
                          : 'bg-slate-900 hover:bg-slate-800 border-slate-800 text-slate-300'
                      }`}
                    >
                      <SearchEngineIcon id={eng.id} size={13} />
                      <span className="truncate">{eng.shortLabel}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Quick Navigation Actions */}
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
              <button
                onClick={() => runAndClose(() => onNewTab(false))}
                className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-semibold text-white flex items-center gap-2 cursor-pointer"
              >
                <Plus size={16} className="text-cyan-400" />
                <span>New Tab</span>
              </button>
              <button
                onClick={() => runAndClose(() => onNewTab(true))}
                className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-semibold text-purple-300 flex items-center gap-2 cursor-pointer"
              >
                <EyeOff size={16} className="text-purple-400" />
                <span>Incognito</span>
              </button>
              <button
                onClick={() => runAndClose(onGoHome)}
                className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-semibold text-slate-200 flex items-center gap-2 cursor-pointer"
              >
                <Home size={16} className="text-emerald-400" />
                <span>Home</span>
              </button>
              <button
                onClick={() => runAndClose(onReload)}
                className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-semibold text-slate-200 flex items-center gap-2 cursor-pointer"
              >
                <RotateCw size={16} className="text-sky-400" />
                <span>Reload</span>
              </button>
              <button
                disabled={!canGoForward}
                onClick={() => runAndClose(onGoForward)}
                className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 disabled:opacity-40 border border-slate-800 text-xs font-semibold text-slate-200 flex items-center gap-2 cursor-pointer"
              >
                <ArrowRight size={16} className="text-slate-300" />
                <span>Forward</span>
              </button>
            </div>
          </section>

          {/* CATEGORY 2: PAGE TOOLS */}
          <section className="space-y-2.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-emerald-400 px-1">
              2. Page &amp; Reading Tools
            </div>

            <div className="grid grid-cols-2 gap-2 items-stretch">
              <button
                onClick={() => runAndClose(onToggleDesktop)}
                className={`p-3 rounded-xl border text-left flex items-center justify-between cursor-pointer ${
                  tab.isDesktopMode
                    ? 'bg-cyan-950/60 border-cyan-500/60 text-white'
                    : 'bg-slate-900 hover:bg-slate-800 border-slate-800 text-slate-200'
                }`}
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold">
                  <Monitor size={16} className="text-cyan-400" />
                  <span>Desktop Site</span>
                </span>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                    tab.isDesktopMode ? 'bg-cyan-400 text-slate-950' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {tab.isDesktopMode ? 'ON' : 'OFF'}
                </span>
              </button>

              <button
                onClick={() => runAndClose(onToggleReader)}
                className={`p-3 rounded-xl border text-left flex items-center justify-between cursor-pointer ${
                  tab.isReaderMode
                    ? 'bg-emerald-950/60 border-emerald-500/60 text-white'
                    : 'bg-slate-900 hover:bg-slate-800 border-slate-800 text-slate-200'
                }`}
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold">
                  <BookOpen size={16} className="text-emerald-400" />
                  <span>Reader Mode</span>
                </span>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                    tab.isReaderMode ? 'bg-emerald-400 text-slate-950' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {tab.isReaderMode ? 'ON' : 'OFF'}
                </span>
              </button>

              <button
                onClick={() => runAndClose(onOpenTranslate)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center justify-between cursor-pointer"
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold text-slate-200">
                  <Languages size={16} className="text-indigo-400" />
                  <span>Translate Page</span>
                </span>
                {tab.translatedLang && (
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500 text-white font-bold uppercase">
                    {tab.translatedLang}
                  </span>
                )}
              </button>

              <button
                onClick={() => runAndClose(onOpenFind)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center gap-2.5 text-xs font-semibold text-slate-200 cursor-pointer"
              >
                <Search size={16} className="text-amber-400" />
                <span>Find in Page</span>
              </button>

              <button
                onClick={() => runAndClose(() => onOpenAi('summarize'))}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center gap-2.5 text-xs font-semibold text-cyan-200 cursor-pointer"
              >
                <Sparkles size={16} className="text-cyan-400" />
                <span>AI Summarize Page</span>
              </button>

              <button
                onClick={() => runAndClose(onToggleSplit)}
                className={`p-3 rounded-xl border text-left flex items-center justify-between cursor-pointer ${
                  hasSplitView
                    ? 'bg-purple-950/60 border-purple-500/60 text-white'
                    : 'bg-slate-900 hover:bg-slate-800 border-slate-800 text-slate-200'
                }`}
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold">
                  <Columns2 size={16} className="text-purple-400" />
                  <span>Split Screen</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-bold">
                  {hasSplitView ? 'ON' : 'OFF'}
                </span>
              </button>
            </div>

            {/* Text Zoom Control Row */}
            <div
              className="rounded-xl border border-slate-800 px-3.5 py-2.5 flex items-center justify-between"
              style={{ backgroundColor: '#111827' }}
            >
              <span className="text-xs font-semibold text-slate-200">Page Text Zoom</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => onChangeZoom(Math.max(75, tab.zoomLevel - 10))}
                  aria-label="Decrease page text zoom"
                  title="Decrease text size"
                  className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-white cursor-pointer"
                >
                  <ZoomOut size={15} />
                </button>
                <span className="text-xs font-mono font-bold text-cyan-300 min-w-[44px] text-center">
                  {tab.zoomLevel}%
                </span>
                <button
                  onClick={() => onChangeZoom(Math.min(150, tab.zoomLevel + 10))}
                  aria-label="Increase page text zoom"
                  title="Increase text size"
                  className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-white cursor-pointer"
                >
                  <ZoomIn size={15} />
                </button>
                {tab.zoomLevel !== 100 && (
                  <button
                    onClick={() => onChangeZoom(100)}
                    className="px-2 py-1 rounded-lg bg-slate-800 text-[11px] text-slate-300 cursor-pointer"
                  >
                    Reset
                  </button>
                )}
              </div>
            </div>
          </section>

          {/* CATEGORY 3: PRIVACY, ADBLOCK & VPN */}
          <section className="space-y-2.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-amber-400 px-1">
              3. Privacy, AdBlock &amp; Security
            </div>

            <div className="grid grid-cols-2 gap-2 items-stretch">
              <button
                onClick={() => runAndClose(onOpenShields)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center justify-between cursor-pointer"
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold text-white">
                  <ShieldCheck size={16} className="text-emerald-400" />
                  <span>AdBlock Shields</span>
                </span>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                    shields.enabled ? 'bg-emerald-500/25 text-emerald-300' : 'bg-rose-500/25 text-rose-300'
                  }`}
                >
                  {shields.enabled ? 'ON' : 'OFF'}
                </span>
              </button>

              <button
                onClick={() => runAndClose(onOpenVpn)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center justify-between cursor-pointer"
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold text-white">
                  <Lock size={16} className="text-cyan-400" />
                  <span>Built-in VPN</span>
                </span>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                    vpn.connected ? 'bg-emerald-500/25 text-emerald-300' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {vpn.connected ? 'ON' : 'OFF'}
                </span>
              </button>

              <button
                onClick={() => runAndClose(onToggleZapper)}
                className={`p-3 rounded-xl border text-left flex items-center justify-between cursor-pointer ${
                  zapperMode
                    ? 'bg-rose-950/60 border-rose-500/60 text-white'
                    : 'bg-slate-900 hover:bg-slate-800 border-slate-800 text-slate-200'
                }`}
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold">
                  <Crosshair size={16} className="text-rose-400" />
                  <span>Element Zapper</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-bold">
                  {zapperMode ? 'ON' : 'OFF'}
                </span>
              </button>

              <button
                onClick={() => runAndClose(onFireWipe)}
                className="p-3 rounded-xl bg-orange-950/40 hover:bg-orange-950/70 border border-orange-500/40 text-left flex items-center gap-2.5 text-xs font-bold text-orange-300 cursor-pointer"
              >
                <Flame size={16} className="text-orange-400" />
                <span>Fire Wipe Data</span>
              </button>
            </div>
          </section>

          {/* CATEGORY 4: EXTENSIONS & DEVELOPER TOOLS */}
          <section className="space-y-2.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-purple-400 px-1">
              4. Extensions &amp; Advanced Tools
            </div>

            <div className="grid grid-cols-2 gap-2 items-stretch">
              <button
                onClick={() => runAndClose(onOpenExtensions)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center justify-between cursor-pointer"
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold text-white">
                  <Puzzle size={16} className="text-purple-400" />
                  <span>Extensions Hub</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/25 text-purple-200 font-bold">
                  {enabledExtensionsCount} Active
                </span>
              </button>

              <button
                onClick={() => runAndClose(onToggleDevTools)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center justify-between cursor-pointer"
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold text-slate-200">
                  <Terminal size={16} className="text-sky-400" />
                  <span>Web DevTools</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-bold">
                  {showDevTools ? 'ON' : 'OFF'}
                </span>
              </button>

              <button
                onClick={() => runAndClose(onOpenBenchmark)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center gap-2.5 text-xs font-semibold text-slate-200 cursor-pointer"
              >
                <Zap size={16} className="text-amber-400" />
                <span>Speed Benchmark</span>
              </button>

              <button
                onClick={() => runAndClose(onOpenPlayStore)}
                className="p-3 rounded-xl bg-emerald-950/40 hover:bg-emerald-950/70 border border-emerald-500/40 text-left flex items-center gap-2.5 text-xs font-semibold text-emerald-300 cursor-pointer"
              >
                <Download size={16} className="text-emerald-400" />
                <span>APK &amp; Play Store</span>
              </button>
            </div>
          </section>

          {/* CATEGORY 5: APPEARANCE & LAYOUT */}
          <section className="space-y-2.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400 px-1">
              5. Appearance &amp; Layout
            </div>

            <div className="grid grid-cols-2 gap-2 items-stretch">
              <button
                onClick={onToggleToolbarPosition}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center justify-between cursor-pointer"
              >
                <span className="flex items-center gap-2 min-w-0 text-[11px] font-semibold text-slate-200">
                  {customization.addressBarPosition === 'bottom' ? (
                    <PanelBottom size={16} className="text-cyan-400" />
                  ) : (
                    <PanelTop size={16} className="text-cyan-400" />
                  )}
                  <span>Address Bar</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-cyan-300 font-bold uppercase">
                  {customization.addressBarPosition}
                </span>
              </button>

              <button
                onClick={() => runAndClose(onOpenCustomize)}
                className="p-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left flex items-center gap-2.5 text-xs font-semibold text-slate-200 cursor-pointer"
              >
                <SlidersHorizontal size={16} className="text-pink-400" />
                <span>Themes &amp; Downloads</span>
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
};
