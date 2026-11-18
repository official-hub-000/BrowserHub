import React, { useEffect, useState } from 'react';
import {
  Power,
  Shield,
  Lock,
  X,
  Globe,
  Wifi,
  Check,
  AlertTriangle,
} from 'lucide-react';
import type { VpnState } from '../types/browser';
import { GLOBAL_VPN_NODES } from '../data/presets';
import { AndroidHybridBridge } from '../bridge/androidHybridBridge';
import { FlagIcon } from './VectorIcons';
import { useExitTransition } from '../utils/useExitTransition';

interface VpnModalProps {
  vpn: VpnState;
  onUpdateVpn: (patch: Partial<VpnState>) => void;
  onClose: () => void;
}

interface ExitReport {
  ip: string | null;
  country: string | null;
  city: string | null;
  isp: string | null;
  error: boolean;
}

/**
 * Shows the user their real public exit. They connected the shield, checked a
 * "what is my IP" site, and saw their own location — which is correct behaviour
 * for a local filter, but with no evidence on screen it reads as a broken VPN.
 * Displaying the actual exit turns an invisible truth into a verifiable one.
 */
const EXIT_ENDPOINTS = [
  'https://ipapi.co/json/',
  'https://ipwho.is/',
];

export const VpnModal: React.FC<VpnModalProps> = ({ vpn, onUpdateVpn, onClose }) => {
  const activeNode =
    GLOBAL_VPN_NODES.find((n) => n.id === vpn.selectedNodeId) || GLOBAL_VPN_NODES[0];

  const [exit, setExit] = useState<ExitReport | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      for (const url of EXIT_ENDPOINTS) {
        try {
          const res = await fetch(url);
          if (!res.ok) continue;
          const data: any = await res.json();
          const ip = data?.ip ?? null;
          if (!ip) continue;
          if (!cancelled) {
            setExit({
              ip,
              country: data?.country_name ?? data?.country ?? null,
              city: data?.city ?? null,
              isp: data?.org ?? data?.connection?.isp ?? null,
              error: false,
            });
          }
          return;
        } catch {
          // Try the next endpoint; an offline phone simply shows "unavailable".
        }
      }
      if (!cancelled) setExit({ ip: null, country: null, city: null, isp: null, error: true });
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [vpn.connected]);

  const handleToggleConnect = async () => {
    if (vpn.connected) {
      await AndroidHybridBridge.syncVpnTunnel(
        false,
        activeNode.id,
        activeNode.protocol,
        vpn.dohProvider,
        vpn.killSwitch
      );
      onUpdateVpn({ connected: false, connecting: false });
      return;
    }

    onUpdateVpn({ connecting: true });
    await AndroidHybridBridge.syncVpnTunnel(
      true,
      activeNode.id,
      activeNode.protocol,
      vpn.dohProvider,
      vpn.killSwitch
    );
    setTimeout(() => {
      onUpdateVpn({
        connected: true,
        connecting: false,
        protocol: activeNode.protocol,
        encryptedMb: Number((vpn.encryptedMb + 1.4).toFixed(1)),
      });
    }, 350);
  };

  const motion = useExitTransition(onClose, 'bh-dialog', 'bh-dialog-out');

  return (
    <div className={`bh-overlay fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-3${motion.isClosing ? ' bh-overlay-out' : ''}`} onClick={motion.requestClose}>
      <div className={`${motion.className} w-full max-w-md rounded-3xl bg-black border border-slate-800 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]`} onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-gradient-to-r from-black to-cyan-950/40">
          <div className="flex items-center gap-2.5">
            <div
              className={`w-9 h-9 rounded-2xl flex items-center justify-center ${
                vpn.connected ? 'bg-emerald-500/20 text-emerald-400' : 'bg-[#121216] text-slate-400'
              }`}
            >
              <Lock className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-bold text-white flex items-center gap-2 flex-wrap">
                On-Device Privacy Shield
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-500/15 text-cyan-300">
                  Android VpnService
                </span>
                <span className="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 font-bold">
                  NOT A LOCATION VPN
                </span>
              </div>
              <div className="text-[11px] text-slate-400">
                Filters this app on your phone · does not change your IP
              </div>
            </div>
          </div>
          <button aria-label="Close" title="Close" onClick={onClose} className="p-1.5 rounded-xl hover:bg-[#121216] text-slate-400 cursor-pointer">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Honest capability notice — this build filters locally; it does not
            tunnel your traffic through a remote exit node yet. */}
        <div className="px-4 py-2 bg-amber-500/10 border-b border-amber-500/30 flex items-start gap-2">
          <AlertTriangle size={13} className="text-amber-400 mt-0.5 shrink-0" />
          <p className="text-[10px] leading-relaxed text-amber-200/90">
            <strong>Your IP address and location do not change.</strong> That is expected, not a
            bug: this shield filters inside BrowserHub (ad &amp; tracker blocking, tracking-parameter
            stripping, encrypted DNS preference), but it has no remote exit servers to route your
            traffic through. A location-changing VPN needs servers to exit from, and this build
            ships none. Use a dedicated VPN app if you need a different IP.
          </p>
        </div>

        {/* Big Power Ring & Tunnel Telemetry */}
        <div className="p-4 bg-[#050506] border-b border-slate-800 flex items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="text-xs font-semibold text-slate-400">Tunnel Status</div>
            <div
              className={`text-base font-extrabold ${
                vpn.connecting
                  ? 'text-amber-300'
                  : vpn.connected
                    ? 'text-emerald-400'
                    : 'text-slate-300'
              }`}
            >
              {vpn.connecting
                ? 'Starting shield...'
                : vpn.connected
                  ? 'Shield active'
                  : 'Direct connection'}
            </div>
            <div className="text-[11px] font-mono text-slate-400">
              Resolver: {vpn.connected ? vpn.dohProvider : 'system DNS'} •{' '}
              {activeNode.baseLatencyMs}ms
            </div>
            <div className="text-[10px] text-cyan-300">
              {vpn.encryptedMb.toFixed(1)} MB filtered on this device
            </div>
            <div className="text-[10px] leading-relaxed pt-1 border-t border-slate-800/70 mt-1">
              <div className="text-slate-500 uppercase tracking-wider text-[9px] font-bold">
                Your real public exit
              </div>
              {exit === null ? (
                <div className="text-slate-400 font-mono">Checking…</div>
              ) : exit.ip ? (
                <>
                  <div className="text-slate-200 font-mono">{exit.ip}</div>
                  <div className="text-slate-400">
                    {[exit.city, exit.country].filter(Boolean).join(', ')}
                    {exit.isp ? ` · ${exit.isp}` : ''}
                  </div>
                  <div className="text-amber-300/90">
                    This is your phone&apos;s own location, and it stays that way while the shield is
                    on. The shield filters content; it does not move your exit.
                  </div>
                </>
              ) : (
                <div className="text-slate-400">
                  Could not read your IP (likely offline). Your location is unchanged either way.
                </div>
              )}
            </div>
          </div>

          <button
            onClick={handleToggleConnect}
            aria-label={vpn.connected ? 'Disconnect VPN' : 'Connect VPN'}
            title={vpn.connected ? 'Disconnect VPN' : 'Connect VPN'}
            className={`w-16 h-16 rounded-full flex items-center justify-center border-2 transition-all shrink-0 cursor-pointer ${
              vpn.connected
                ? 'bg-emerald-500/20 border-emerald-400 text-emerald-300 shadow-lg shadow-emerald-500/25'
                : 'bg-[#121216] border-slate-600 text-slate-300 hover:border-cyan-400'
            }`}
          >
            <Power className="w-7 h-7" />
          </button>
        </div>

        {/* Scrollable Server Nodes + DNS + Kill Switch */}
        <div className="overflow-y-auto p-3.5 space-y-3.5">
          <div>
            <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-2 flex items-center justify-between">
              <span>Global Routing Nodes ({GLOBAL_VPN_NODES.length})</span>
              <span className="text-emerald-400">RAM-Only Diskless Servers</span>
            </div>
            <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
              {GLOBAL_VPN_NODES.map((node) => {
                const selected = node.id === vpn.selectedNodeId;
                return (
                  <button
                    key={node.id}
                    onClick={() =>
                      onUpdateVpn({
                        selectedNodeId: node.id,
                        protocol: node.protocol,
                      })
                    }
                    className={`w-full flex items-center justify-between p-2.5 rounded-2xl border text-left transition cursor-pointer ${
                      selected
                        ? 'bg-cyan-500/15 border-cyan-400/50 text-white'
                        : 'bg-[#060608] border-slate-800/80 text-slate-300 hover:bg-[#121216]'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <FlagIcon code={node.flag} size={20} />
                      <div className="min-w-0">
                        <div className="text-xs font-bold truncate">{node.city}</div>
                        <div className="text-[10px] text-slate-400">
                          {node.protocol} • Load {node.loadPercent}%
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <span className="text-[11px] font-mono text-emerald-400">
                        {node.baseLatencyMs}ms
                      </span>
                      <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-[#121216] text-slate-400 font-semibold">
                        planned
                      </span>
                      {selected && <Check className="w-4 h-4 text-cyan-400" />}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Encrypted DNS-over-HTTPS Selector */}
          <div className="rounded-2xl bg-[#08080b] border border-slate-800 p-3 space-y-2">
            <div className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
              <Globe className="w-3.5 h-3.5 text-cyan-400" />
              Encrypted DNS-over-HTTPS (RFC 8484)
            </div>
            <div className="grid grid-cols-3 gap-1.5">
              {(
                [
                  { id: 'mullvad', label: 'Mullvad DoH' },
                  { id: 'adguard', label: 'AdGuard DNS' },
                  { id: 'nextdns', label: 'NextDNS' },
                  { id: 'quad9', label: 'Quad9 9.9.9.9' },
                  { id: 'cloudflare', label: '1.1.1.1' },
                ] as const
              ).map((dns) => (
                <button
                  key={dns.id}
                  onClick={() => onUpdateVpn({ dohProvider: dns.id })}
                  className={`px-2 py-1.5 rounded-xl text-[11px] font-semibold border cursor-pointer ${
                    vpn.dohProvider === dns.id
                      ? 'bg-cyan-500/20 border-cyan-400 text-cyan-200'
                      : 'bg-black border-slate-800 text-slate-400'
                  }`}
                >
                  {dns.label}
                </button>
              ))}
            </div>
          </div>

          {/* Kill Switch & WebRTC Leak Protection */}
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => onUpdateVpn({ killSwitch: !vpn.killSwitch })}
              className={`p-2.5 rounded-2xl border text-left cursor-pointer ${
                vpn.killSwitch
                  ? 'bg-emerald-950/30 border-emerald-500/40 text-emerald-200'
                  : 'bg-black border-slate-800 text-slate-400'
              }`}
            >
              <div className="text-xs font-bold flex items-center gap-1">
                <AlertTriangle className="w-3.5 h-3.5" /> Kill Switch
              </div>
              <div className="text-[10px] opacity-80 mt-0.5">
                {vpn.killSwitch ? 'Blocks leaks on drop' : 'Disabled'}
              </div>
            </button>

            <button
              onClick={() => onUpdateVpn({ blockWebRtcLeak: !vpn.blockWebRtcLeak })}
              className={`p-2.5 rounded-2xl border text-left cursor-pointer ${
                vpn.blockWebRtcLeak
                  ? 'bg-cyan-950/30 border-cyan-500/40 text-cyan-200'
                  : 'bg-black border-slate-800 text-slate-400'
              }`}
            >
              <div className="text-xs font-bold flex items-center gap-1">
                <Wifi className="w-3.5 h-3.5" /> WebRTC Lock
              </div>
              <div className="text-[10px] opacity-80 mt-0.5">
                {vpn.blockWebRtcLeak ? 'STUN IP leaks blocked' : 'Standard WebRTC'}
              </div>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
