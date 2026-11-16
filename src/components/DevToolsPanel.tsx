import React, { useState } from 'react';
import { Terminal, X, Play, ShieldAlert, CheckCircle2 } from 'lucide-react';
import type { BrowserTab, NetworkLogEntry } from '../types/browser';

interface DevToolsPanelProps {
  tab: BrowserTab;
  networkLogs: NetworkLogEntry[];
  onClose: () => void;
}

export const DevToolsPanel: React.FC<DevToolsPanelProps> = ({
  tab,
  networkLogs,
  onClose,
}) => {
  const [subTab, setSubTab] = useState<'network' | 'console' | 'elements'>('network');
  const [jsInput, setJsInput] = useState('navigator.userAgent');
  const [consoleHistory, setConsoleHistory] = useState<Array<{ cmd: string; out: string }>>([
    {
      cmd: 'window.__BROWSERHUB_ENGINE__',
      out: '"Hybrid Android v1.0 (GeckoView 136 + Blink 134 • Zero Telemetry)"',
    },
  ]);

  const executeJs = (e: React.FormEvent) => {
    e.preventDefault();
    if (!jsInput.trim()) return;
    let output = '';
    try {
      const contextVars = {
        tabUrl: tab.url,
        tabTitle: tab.title,
        isDesktopMode: tab.isDesktopMode,
        adsBlocked: tab.adsBlocked,
      };
      if (jsInput.trim() === 'navigator.userAgent') {
        output = tab.isDesktopMode
          ? '"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) BrowserHub/134.0.0.0 Safari/537.36"'
          : '"Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) Gecko/136.0 Firefox/136.0 BrowserHub/1.0"';
      } else {
        const fn = new Function('tab', `return (${jsInput});`);
        output = JSON.stringify(fn(contextVars), null, 2) ?? 'undefined';
      }
    } catch (err) {
      output = `Error: ${err instanceof Error ? err.message : String(err)}`;
    }
    setConsoleHistory((prev) => [...prev, { cmd: jsInput, out: output }]);
    setJsInput('');
  };

  return (
    <div className="h-64 bg-slate-950 border-t border-cyan-500/40 flex flex-col shrink-0 z-30">
      {/* Header */}
      <div className="px-3 py-1.5 bg-slate-900 border-b border-slate-800 flex items-center justify-between text-xs">
        <div className="flex items-center gap-2">
          <span className="font-bold text-cyan-300 inline-flex items-center gap-1">
            <Terminal className="w-3.5 h-3.5" /> DevTools
          </span>
          {(['network', 'console', 'elements'] as const).map((st) => (
            <button
              key={st}
              onClick={() => setSubTab(st)}
              className={`px-2.5 py-0.5 rounded-lg capitalize font-semibold cursor-pointer ${
                subTab === st ? 'bg-cyan-500/20 text-cyan-200' : 'text-slate-400 hover:text-white'
              }`}
            >
              {st}
            </button>
          ))}
        </div>
        <button onClick={onClose} className="p-1 rounded hover:bg-slate-800 text-slate-400 cursor-pointer">
          <X className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto p-2.5 font-mono text-[11px]">
        {subTab === 'network' && (
          <table className="w-full text-left">
            <thead className="text-slate-500 border-b border-slate-800">
              <tr>
                <th className="py-1">Status</th>
                <th className="py-1">Domain / Resource</th>
                <th className="py-1">Type</th>
                <th className="py-1">Rule / Latency</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-900">
              {networkLogs.map((log) => (
                <tr key={log.id}>
                  <td className="py-1 pr-2">
                    {log.status === 'blocked' ? (
                      <span className="text-rose-400 inline-flex items-center gap-1">
                        <ShieldAlert className="w-3 h-3" /> BLOCKED
                      </span>
                    ) : (
                      <span className="text-emerald-400 inline-flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> 200 OK
                      </span>
                    )}
                  </td>
                  <td className="py-1 pr-2 text-slate-200 truncate max-w-[180px]">{log.domain}</td>
                  <td className="py-1 pr-2 text-slate-400">{log.type}</td>
                  <td className="py-1 text-cyan-300">
                    {log.ruleMatched ? log.ruleMatched : `${log.latencyMs}ms (${log.sizeKb}KB)`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {subTab === 'console' && (
          <div className="flex flex-col h-full justify-between gap-2">
            <div className="space-y-1.5 overflow-y-auto">
              {consoleHistory.map((item, idx) => (
                <div key={idx} className="border-b border-slate-900 pb-1">
                  <div className="text-cyan-400">&gt; {item.cmd}</div>
                  <div className="text-emerald-300 whitespace-pre-wrap">{item.out}</div>
                </div>
              ))}
            </div>
            <form onSubmit={executeJs} className="flex items-center gap-1.5 pt-1 border-t border-slate-800">
              <span className="text-cyan-400">&gt;</span>
              <input
                value={jsInput}
                onChange={(e) => setJsInput(e.target.value)}
                placeholder="Evaluate JavaScript expression (e.g. 120 * 8, tab.url)..."
                className="flex-1 bg-transparent text-white focus:outline-none"
              />
              <button
                type="submit"
                className="px-2 py-0.5 rounded bg-cyan-500 text-slate-950 font-bold inline-flex items-center gap-1 cursor-pointer"
              >
                <Play className="w-3 h-3" /> Run
              </button>
            </form>
          </div>
        )}

        {subTab === 'elements' && (
          <pre className="text-slate-300 leading-relaxed whitespace-pre-wrap">
{`<!DOCTYPE html>
<html lang="${tab.translatedLang || 'en'}" data-engine="browserhub-hybrid" data-viewport="${tab.isDesktopMode ? 'desktop-1280' : 'mobile-fluid'}">
  <head>
    <meta http-equiv="Content-Security-Policy" content="upgrade-insecure-requests" />
    <title>${tab.title}</title>
    <!-- Shields Ultra: ${tab.adsBlocked} ad nodes & ${tab.trackersBlocked} tracker scripts stripped pre-DOM -->
  </head>
  <body data-incognito="${tab.isIncognito}" data-zapped-nodes="${tab.zappedSelectors.length}">
    <main id="app-viewport" zoom="${tab.zoomLevel}%">...</main>
  </body>
</html>`}
          </pre>
        )}
      </div>
    </div>
  );
};
