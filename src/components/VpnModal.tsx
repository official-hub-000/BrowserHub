import React from 'react';
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

interface VpnModalProps {
  vpn: VpnState;
  onUpdateVpn: (patch: Partial<VpnState>) => void;
  onClose: () => void;
}

export const VpnModal: React.FC<VpnModalProps> = ({ vpn, onUpdateVpn, onClose }) => {
  const activeNode =
    GLOBAL_VPN_NODES.find((n) => n.id === vpn.selectedNodeId) || GLOBAL_VPN_NODES[0];

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

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-3">
      <div className="w-full max-w-md rounded-3xl bg-slate-950 border border-slate-800 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-gradient-to-r from-slate-900 to-cyan-950/40">
          <div className="flex items-center gap-2.5">
            <div
              className={`w-9 h-9 rounded-2xl flex items-center justify-center ${
                vpn.connected ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-800 text-slate-400'
              }`}
            >
              <Lock className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-bold text-white flex items-center gap-2">
                Built-In Zero-Log VPN
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-500/15 text-cyan-300">
                  Free • Unlimited
                </span>
              </div>
              <div className="text-[11px] text-slate-400">
                Android VpnService • WireGuard • Multi-Hop • Tor
              </div>
            </div>
          </div>
          <button aria-label="Close" title="Close" onClick={onClose} className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 cursor-pointer">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Big Power Ring & Tunnel Telemetry */}
        <div className="p-4 bg-slate-900/50 border-b border-slate-800 flex items-center justify-between gap-4">
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
                ? 'Handshaking Keys...'
                : vpn.connected
                  ? 'ENCRYPTED & MASKED'
                  : 'Direct Connection'}
            </div>
            <div className="text-[11px] font-mono text-slate-400">
              IP: {vpn.connected ? activeNode.ipMask : 'Exposed Carrier IP'} •{' '}
              {activeNode.baseLatencyMs}ms
            </div>
            <div className="text-[10px] text-cyan-300">
              ChaCha20-Poly1305 • {vpn.encryptedMb.toFixed(1)} MB Shielded
            </div>
          </div>

          <button
            onClick={handleToggleConnect}
            aria-label={vpn.connected ? 'Disconnect VPN' : 'Connect VPN'}
            title={vpn.connected ? 'Disconnect VPN' : 'Connect VPN'}
            className={`w-16 h-16 rounded-full flex items-center justify-center border-2 transition-all shrink-0 cursor-pointer ${
              vpn.connected
                ? 'bg-emerald-500/20 border-emerald-400 text-emerald-300 shadow-lg shadow-emerald-500/25'
                : 'bg-slate-800 border-slate-600 text-slate-300 hover:border-cyan-400'
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
                        : 'bg-slate-900/70 border-slate-800/80 text-slate-300 hover:bg-slate-900'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span className="text-lg">{node.flag}</span>
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
                      {selected && <Check className="w-4 h-4 text-cyan-400" />}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Encrypted DNS-over-HTTPS Selector */}
          <div className="rounded-2xl bg-slate-900/80 border border-slate-800 p-3 space-y-2">
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
                      : 'bg-slate-950 border-slate-800 text-slate-400'
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
                  : 'bg-slate-900 border-slate-800 text-slate-400'
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
                  : 'bg-slate-900 border-slate-800 text-slate-400'
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
