import React from 'react';
import {
  Palette,
  X,
  Cpu,
  Download,
  Terminal,
  Smartphone,
  BookOpen,
  Search,
} from 'lucide-react';
import type {
  BrowserCustomization,
  DownloadItem,
  SearchEngineId,
  ThemePresetId,
} from '../types/browser';
import { THEME_PRESETS } from '../data/presets';
import { AndroidHybridBridge } from '../bridge/androidHybridBridge';

interface CustomizeDrawerProps {
  customization: BrowserCustomization;
  downloads: DownloadItem[];
  onUpdateCustomization: (patch: Partial<BrowserCustomization>) => void;
  onOpenDevTools: () => void;
  onClose: () => void;
}

export const CustomizeDrawer: React.FC<CustomizeDrawerProps> = ({
  customization,
  downloads,
  onUpdateCustomization,
  onOpenDevTools,
  onClose,
}) => {
  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex justify-end">
      <div className="w-full max-w-md h-full bg-slate-950 border-l border-slate-800 flex flex-col shadow-2xl">
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-900/80">
          <div className="flex items-center gap-2.5">
            <Palette className="w-5 h-5 text-cyan-400" />
            <div>
              <div className="text-sm font-bold text-white">
                Customization Studio &amp; Power Tools
              </div>
              <div className="text-[11px] text-slate-400">
                100/100 Engine, Theme, Ergonomics &amp; 16-Thread Downloads
              </div>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 cursor-pointer">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-5">
          {/* Ergonomic Layout & Frame */}
          <div className="space-y-2">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5 text-cyan-400" />
              Ergonomics &amp; Viewport Shell
            </div>
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => onUpdateCustomization({ addressBarPosition: 'bottom' })}
                className={`p-2.5 rounded-2xl border text-xs font-semibold cursor-pointer ${
                  customization.addressBarPosition === 'bottom'
                    ? 'bg-cyan-500/20 border-cyan-400 text-cyan-200'
                    : 'bg-slate-900 border-slate-800 text-slate-400'
                }`}
              >
                Bottom Bar (One-Handed)
              </button>
              <button
                onClick={() => onUpdateCustomization({ addressBarPosition: 'top' })}
                className={`p-2.5 rounded-2xl border text-xs font-semibold cursor-pointer ${
                  customization.addressBarPosition === 'top'
                    ? 'bg-cyan-500/20 border-cyan-400 text-cyan-200'
                    : 'bg-slate-900 border-slate-800 text-slate-400'
                }`}
              >
                Classic Top Omnibox
              </button>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() =>
                  onUpdateCustomization({ phoneFrameMode: !customization.phoneFrameMode })
                }
                className={`p-2.5 rounded-2xl border text-xs font-semibold cursor-pointer ${
                  customization.phoneFrameMode
                    ? 'bg-emerald-500/20 border-emerald-400 text-emerald-200'
                    : 'bg-slate-900 border-slate-800 text-slate-300'
                }`}
              >
                {customization.phoneFrameMode ? '📱 Android Bezel Frame' : '🖥️ Full Viewport'}
              </button>
              <button
                onClick={() => {
                  onOpenDevTools();
                  onClose();
                }}
                className="p-2.5 rounded-2xl border border-purple-500/40 bg-purple-500/15 text-purple-200 text-xs font-bold inline-flex items-center justify-center gap-1.5 cursor-pointer"
              >
                <Terminal className="w-3.5 h-3.5" />
                Desktop DevTools
              </button>
            </div>
          </div>

          {/* Theme Studio */}
          <div className="space-y-2">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
              Theme Studio (incl. True 0-Watt OLED)
            </div>
            <div className="grid grid-cols-1 gap-1.5">
              {(Object.keys(THEME_PRESETS) as ThemePresetId[]).map((tId) => {
                const preset = THEME_PRESETS[tId];
                const active = customization.theme === tId;
                return (
                  <button
                    key={tId}
                    onClick={() => onUpdateCustomization({ theme: tId })}
                    className={`flex items-center justify-between px-3 py-2 rounded-xl border text-xs cursor-pointer ${
                      active
                        ? 'border-cyan-400 bg-cyan-500/15 text-white font-bold'
                        : 'border-slate-800 bg-slate-900/70 text-slate-300'
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <span
                        className="w-3.5 h-3.5 rounded-full border border-white/25"
                        style={{ backgroundColor: preset.accentBg }}
                      />
                      {preset.name}
                    </span>
                    <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-400">
                      {preset.badge}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Hybrid Rendering Engine Selector */}
          <div className="space-y-2">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Cpu className="w-3.5 h-3.5 text-emerald-400" />
              Android Hybrid Rendering Engine
            </div>
            <div className="grid grid-cols-3 gap-1.5 text-[11px]">
              {(
                [
                  { id: 'hybrid-auto', label: 'Hybrid Auto' },
                  { id: 'geckoview-136', label: 'GeckoView 136' },
                  { id: 'chromium-blink-134', label: 'Blink 134' },
                ] as const
              ).map((eng) => (
                <button
                  key={eng.id}
                  onClick={async () => {
                    await AndroidHybridBridge.switchEngine(eng.id);
                    onUpdateCustomization({ engine: eng.id });
                  }}
                  className={`py-2 px-2 rounded-xl border font-semibold cursor-pointer ${
                    customization.engine === eng.id
                      ? 'bg-emerald-500/20 border-emerald-400 text-emerald-200'
                      : 'bg-slate-900 border-slate-800 text-slate-400'
                  }`}
                >
                  {eng.label}
                </button>
              ))}
            </div>
          </div>

          {/* Search Engine */}
          <div className="space-y-2">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Search className="w-3.5 h-3.5 text-amber-400" />
              Default Search Provider
            </div>
            <div className="grid grid-cols-3 gap-1.5 text-[11px]">
              {(
                [
                  'duckduckgo',
                  'brave',
                  'startpage',
                  'github-live',
                  'searxng',
                  'google',
                ] as SearchEngineId[]
              ).map((se) => (
                <button
                  key={se}
                  onClick={() => onUpdateCustomization({ searchEngine: se })}
                  className={`py-1.5 px-2 rounded-xl border uppercase font-semibold cursor-pointer ${
                    customization.searchEngine === se
                      ? 'bg-amber-500/20 border-amber-400 text-amber-200'
                      : 'bg-slate-900 border-slate-800 text-slate-400'
                  }`}
                >
                  {se}
                </button>
              ))}
            </div>
          </div>

          {/* Reader Mode Typography */}
          <div className="space-y-2">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <BookOpen className="w-3.5 h-3.5 text-cyan-400" />
              Reader Mode Paper &amp; Font
            </div>
            <div className="flex items-center gap-2">
              {(['amoled', 'sepia', 'slate'] as const).map((rt) => (
                <button
                  key={rt}
                  onClick={() => onUpdateCustomization({ readerTheme: rt })}
                  className={`flex-1 py-1.5 rounded-xl border text-xs capitalize cursor-pointer ${
                    customization.readerTheme === rt
                      ? 'border-cyan-400 bg-cyan-500/20 text-white'
                      : 'border-slate-800 bg-slate-900 text-slate-400'
                  }`}
                >
                  {rt}
                </button>
              ))}
              {(['sans', 'serif', 'mono'] as const).map((rf) => (
                <button
                  key={rf}
                  onClick={() => onUpdateCustomization({ readerFont: rf })}
                  className={`flex-1 py-1.5 rounded-xl border text-xs capitalize cursor-pointer ${
                    customization.readerFont === rf
                      ? 'border-purple-400 bg-purple-500/20 text-white'
                      : 'border-slate-800 bg-slate-900 text-slate-400'
                  }`}
                >
                  {rf}
                </button>
              ))}
            </div>
          </div>

          {/* 16-Thread Download Accelerator */}
          <div className="space-y-2">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Download className="w-3.5 h-3.5 text-emerald-400" />
              16-Thread Download Accelerator ({downloads.length})
            </div>
            {downloads.map((dl) => {
              const pct = Math.min(100, Math.round((dl.downloadedMb / dl.sizeMb) * 100));
              return (
                <div
                  key={dl.id}
                  className="rounded-2xl bg-slate-900 border border-slate-800 p-3 space-y-1.5 text-xs"
                >
                  <div className="flex items-center justify-between font-semibold text-white">
                    <span className="truncate">{dl.filename}</span>
                    <span className="text-emerald-400 shrink-0 ml-2">{pct}%</span>
                  </div>
                  <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-cyan-400 to-emerald-400 transition-all"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-400">
                    <span>
                      {dl.downloadedMb.toFixed(1)} / {dl.sizeMb} MB • {dl.threads} Threads
                    </span>
                    <span>
                      {dl.status === 'completed' ? '✓ Verified' : `${dl.speedMbps} MB/s`}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};
