import React from 'react';
import {
  ShieldCheck,
  ShieldOff,
  Crosshair,
  X,
  Fingerprint,
  Link2Off,
  Cookie,
  Cpu,
  Lock,
} from 'lucide-react';
import type { BrowserTab, ShieldState } from '../types/browser';

interface ShieldsPanelProps {
  tab: BrowserTab;
  shields: ShieldState;
  zapperMode: boolean;
  onUpdateShields: (patch: Partial<ShieldState>) => void;
  onToggleZapper: () => void;
  onResetZapped: () => void;
  onClose: () => void;
}

export const ShieldsPanel: React.FC<ShieldsPanelProps> = ({
  tab,
  shields,
  zapperMode,
  onUpdateShields,
  onToggleZapper,
  onResetZapped,
  onClose,
}) => {
  const toggles: Array<{
    key: keyof ShieldState;
    label: string;
    desc: string;
    icon: React.ReactNode;
  }> = [
    {
      key: 'blockAds',
      label: 'Native Ad Blocker',
      desc: 'Blocks known ad-network requests before the page can load them',
      icon: <ShieldCheck className="w-4 h-4 text-emerald-400" />,
    },
    {
      key: 'blockTrackers',
      label: 'Analytics & Tracker Shield',
      desc: 'Blocks analytics beacons, session recorders & fingerprint scripts (separate list)',
      icon: <Cpu className="w-4 h-4 text-cyan-400" />,
    },
    {
      key: 'blockCookieBanners',
      label: 'Cookie-Wall Killer',
      desc: 'Hides common GDPR / consent overlay elements',
      icon: <Cookie className="w-4 h-4 text-amber-400" />,
    },
    {
      key: 'antiFingerprinting',
      label: 'Canvas & Hardware Spoofing',
      desc: 'Perturbs canvas pixels and reports generic CPU/memory values',
      icon: <Fingerprint className="w-4 h-4 text-purple-400" />,
    },
    {
      key: 'stripTrackingParams',
      label: 'Tracking-Parameter Stripper',
      desc: 'Removes utm_*, fbclid, gclid and similar parameters from links you open',
      icon: <Link2Off className="w-4 h-4 text-rose-400" />,
    },
    {
      key: 'httpsEverywhere',
      label: 'HTTPS-Only Upgrades',
      desc: 'Rewrites plain http:// pages to https:// before they load',
      icon: <Lock className="w-4 h-4 text-sky-400" />,
    },
  ];

  return (
    <div className="bh-overlay fixed inset-0 z-50 bg-black/65 flex items-center justify-center p-3" onClick={onClose}>
      <div className="bh-dialog w-full max-w-md rounded-3xl bg-black border border-slate-800 shadow-2xl overflow-hidden" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="p-4 bg-gradient-to-r from-emerald-950/70 to-black border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            {shields.enabled ? (
              <ShieldCheck className="w-6 h-6 text-emerald-400" />
            ) : (
              <ShieldOff className="w-6 h-6 text-rose-400" />
            )}
            <div>
              <div className="text-sm font-bold text-white">
                BrowserHub Shields {shields.enabled ? 'UP' : 'DOWN'}
              </div>
              <div className="text-[11px] text-slate-400 truncate max-w-[230px]">
                {tab.url}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => onUpdateShields({ enabled: !shields.enabled })}
              className={`px-3 py-1 rounded-full text-xs font-bold transition cursor-pointer ${
                shields.enabled
                  ? 'bg-emerald-500 text-slate-950'
                  : 'bg-rose-500/20 text-rose-300 border border-rose-500/40'
              }`}
            >
              {shields.enabled ? 'ENABLED' : 'PAUSED'}
            </button>
            <button aria-label="Close" title="Close" onClick={onClose} className="p-1.5 rounded-xl hover:bg-[#121216] text-slate-400 cursor-pointer">
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Live Tab Stats */}
        <div className="grid grid-cols-3 gap-2 p-3 bg-[#060608] border-b border-slate-800 text-center">
          <div>
            <div className="text-lg font-extrabold text-emerald-400">{tab.adsBlocked}</div>
            <div className="text-[10px] text-slate-400">Ads Blocked (Tab)</div>
          </div>
          <div>
            <div className="text-lg font-extrabold text-cyan-300">{tab.trackersBlocked}</div>
            <div className="text-[10px] text-slate-400">Trackers Stopped</div>
          </div>
          <div>
            <div className="text-lg font-extrabold text-purple-300">{tab.bytesSavedKb} KB</div>
            <div className="text-[10px] text-slate-400">Payload Stripped</div>
          </div>
        </div>

        {/* Interactive Element Zapper */}
        <div className="p-3 border-b border-slate-800 flex items-center justify-between gap-2 bg-rose-950/20">
          <div className="text-xs">
            <div className="font-bold text-rose-200 flex items-center gap-1.5">
              <Crosshair className="w-3.5 h-3.5 text-rose-400" />
              uBlock Element Zapper
            </div>
            <div className="text-[11px] text-slate-400">
              Tap any annoying box or banner on the page to vaporize it.
            </div>
          </div>
          <div className="flex items-center gap-1.5 shrink-0">
            {tab.zappedSelectors.length > 0 && (
              <button
                onClick={onResetZapped}
                className="px-2 py-1.5 rounded-xl bg-[#121216] text-[11px] text-slate-300 hover:bg-[#1a1a1f] cursor-pointer"
              >
                Restore ({tab.zappedSelectors.length})
              </button>
            )}
            <button
              onClick={() => {
                onToggleZapper();
                onClose();
              }}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold cursor-pointer ${
                zapperMode
                  ? 'bg-rose-500 text-white'
                  : 'bg-rose-500/20 text-rose-300 border border-rose-500/40 hover:bg-rose-500/30'
              }`}
            >
              {zapperMode ? 'Exit Zapper' : 'Launch Zapper'}
            </button>
          </div>
        </div>

        {/* Granular Toggles */}
        <div className="max-h-72 overflow-y-auto p-3 space-y-2">
          {toggles.map((item) => {
            const active = Boolean(shields[item.key]);
            return (
              <div
                key={item.key}
                onClick={() => onUpdateShields({ [item.key]: !active })}
                className="flex items-center justify-between gap-3 p-2.5 rounded-2xl bg-[#08080b] hover:bg-black border border-slate-800/80 cursor-pointer"
              >
                <div className="flex items-start gap-2.5">
                  <div className="mt-0.5">{item.icon}</div>
                  <div>
                    <div className="text-xs font-semibold text-slate-100">{item.label}</div>
                    <div className="text-[10px] text-slate-400">{item.desc}</div>
                  </div>
                </div>
                <div
                  className={`w-9 h-5 rounded-full p-0.5 transition shrink-0 ${
                    active && shields.enabled ? 'bg-emerald-500' : 'bg-[#1a1a1f]'
                  }`}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-white transition-transform ${
                      active && shields.enabled ? 'translate-x-4' : 'translate-x-0'
                    }`}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
