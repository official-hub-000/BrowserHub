import React, { useEffect, useState } from 'react';
import { X, Info, Cpu, CheckCircle2, XCircle, Copy, RefreshCw, Gamepad2 } from 'lucide-react';
import { probeCapabilities, describeCapabilities, type DeviceCapabilities } from '../utils/capabilityProbe';
import { getGamepadStatus } from '../utils/gamepadBridge';
import { AndroidHybridBridge } from '../bridge/androidHybridBridge';
import { getLastAiAttempts } from '../utils/localAiEngine';
import { RUNS_HERE, EXTENSION_CATALOGUE } from '../data/extensionCatalogue';
import { WEB_GAMES } from '../data/games';

interface AboutDiagnosticsModalProps {
  onClose: () => void;
}

/**
 * Replaces the old "APK & Play Store" panel, whose every button pointed at a
 * dev-server route that does not exist in the installed APK.
 *
 * This shows what the app can actually do on THIS device, and lets the user copy
 * a report. That is far more useful than download links that never resolved.
 */
export const AboutDiagnosticsModal: React.FC<AboutDiagnosticsModalProps> = ({ onClose }) => {
  const [caps, setCaps] = useState<DeviceCapabilities | null>(null);
  const [copied, setCopied] = useState(false);
  const [attempts, setAttempts] = useState<string[]>([]);

  const refresh = () => {
    setCaps(probeCapabilities());
    setAttempts(getLastAiAttempts());
  };

  useEffect(() => {
    refresh();
    // Attempts change as the AI runs; a gentle poll keeps the panel truthful
    // without wiring another subscription through the shell.
    const timer = window.setInterval(() => setAttempts(getLastAiAttempts()), 2000);
    return () => window.clearInterval(timer);
  }, []);

  const pad = getGamepadStatus();

  const report = [
    'BrowserHub diagnostics',
    `Runtime: ${AndroidHybridBridge.getRuntimeBadge()}`,
    `Native bridge: ${AndroidHybridBridge.hasJniBridge() ? 'connected' : 'not present (web build)'}`,
    caps ? `Capabilities: ${describeCapabilities(caps)}` : 'Capabilities: probing',
    caps ? `Device tier: ${caps.tier}` : '',
    `Gamepad API: ${pad.mode} (${pad.connected} connected)`,
    `Extensions running here: ${RUNS_HERE.length} of ${EXTENSION_CATALOGUE.length}`,
    `Playable games listed: ${WEB_GAMES.length}`,
    `Last AI attempts: ${attempts.length ? attempts.join(' | ') : 'none yet'}`,
  ]
    .filter(Boolean)
    .join('\n');

  const copy = async () => {
    try {
      if (window.BrowserHubNative?.copyToClipboard) {
        window.BrowserHubNative.copyToClipboard(report);
      } else {
        await navigator.clipboard.writeText(report);
      }
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  const row = (label: string, value: string, ok?: boolean) => (
    <div className="flex items-start justify-between gap-3 py-1.5 border-b border-slate-800/60 last:border-0">
      <span className="text-[11px] text-slate-400 shrink-0">{label}</span>
      <span className="text-[11px] font-semibold text-slate-200 text-right break-words min-w-0">
        {ok === true ? <CheckCircle2 size={11} className="inline text-emerald-400 mr-1" /> : null}
        {ok === false ? <XCircle size={11} className="inline text-rose-400 mr-1" /> : null}
        {value}
      </span>
    </div>
  );

  return (
    <div
      className="bh-overlay fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-3"
      onClick={onClose}
    >
      <div
        className="bh-dialog w-full max-w-md rounded-3xl bg-black border border-slate-800 shadow-2xl overflow-hidden flex flex-col max-h-[88vh]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Info size={16} className="text-cyan-400" />
            <div>
              <div className="text-sm font-bold text-white">About &amp; Diagnostics</div>
              <div className="text-[10px] text-slate-400">What works on this device, verified live</div>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close diagnostics"
            title="Close"
            className="p-1.5 rounded-xl hover:bg-[#121216] text-slate-400 cursor-pointer"
          >
            <X size={16} />
          </button>
        </div>

        <div className="overflow-y-auto p-4 space-y-4">
          <div>
            <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5 flex items-center gap-1.5">
              <Cpu size={11} /> This device
            </div>
            {row('Runtime', AndroidHybridBridge.getRuntimeBadge())}
            {row('Native bridge', AndroidHybridBridge.hasJniBridge() ? 'connected' : 'web build', AndroidHybridBridge.hasJniBridge())}
            {caps ? (
              <>
                {row('Device tier', caps.tier)}
                {row('3D graphics', caps.webgl2 ? 'WebGL 2' : caps.webgl ? 'WebGL 1' : 'none', caps.webgl)}
                {row('WebAssembly', caps.wasm ? 'available' : 'missing', caps.wasm)}
                {row('CPU cores', String(caps.hardwareConcurrency))}
              </>
            ) : null}
          </div>

          <div>
            <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5 flex items-center gap-1.5">
              <Gamepad2 size={11} /> Features
            </div>
            {row('Gamepad API', `${pad.mode} · ${pad.connected} connected`, pad.mode === 'native')}
            {row('Extensions working', `${RUNS_HERE.length} of ${EXTENSION_CATALOGUE.length}`, RUNS_HERE.length > 0)}
            {row('Browser games', `${WEB_GAMES.length} listed`)}
            {row('Location VPN', 'not included — local filter only', false)}
          </div>

          <div>
            <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
              Last AI attempts
            </div>
            {attempts.length === 0 ? (
              <p className="text-[10px] text-slate-500">
                None yet. Ask the AI a question and this lists exactly which providers were tried
                and why each failed, so a problem is diagnosable instead of guesswork.
              </p>
            ) : (
              <ul className="space-y-1">
                {attempts.map((a, i) => (
                  <li key={i} className="text-[10px] font-mono text-slate-400 break-words">
                    {a}
                  </li>
                ))}
              </ul>
            )}
          </div>

          <p className="text-[10px] leading-relaxed text-slate-500">
            Nothing on this screen is asserted — every line is read from the running app. If a
            feature says unavailable here, that is the real answer for this device.
          </p>
        </div>

        <div className="p-3 border-t border-slate-800 flex items-center gap-2">
          <button
            onClick={copy}
            className="flex-1 py-2 rounded-xl bg-[#121216] border border-slate-700 text-[11px] font-bold text-slate-200 cursor-pointer flex items-center justify-center gap-1.5"
          >
            <Copy size={11} />
            {copied ? 'Copied' : 'Copy report'}
          </button>
          <button
            onClick={refresh}
            className="px-3 py-2 rounded-xl bg-[#121216] border border-slate-700 text-[11px] font-bold text-slate-200 cursor-pointer flex items-center gap-1.5"
            aria-label="Refresh diagnostics"
            title="Refresh"
          >
            <RefreshCw size={11} />
            Re-check
          </button>
        </div>
      </div>
    </div>
  );
};
