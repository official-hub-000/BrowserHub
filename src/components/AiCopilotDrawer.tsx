import React, { useEffect, useState } from 'react';
import {
  Sparkles,
  X,
  Send,
  ShieldCheck,
  Cpu,
  Trash2,
  BookOpen,
  Search,
  Lock,
} from 'lucide-react';
import type { AiChatMessage, BrowserTab } from '../types/browser';
import { getStaticPageContentForAi } from '../utils/pageTextExtractor';
import { runLocalAiClient } from '../utils/localAiEngine';

interface AiCopilotDrawerProps {
  tab: BrowserTab;
  livePageText?: string;
  initialMode?: string;
  initialQuery?: string;
  onClose: () => void;
}

export const AiCopilotDrawer: React.FC<AiCopilotDrawerProps> = ({
  tab,
  livePageText,
  initialMode,
  initialQuery,
  onClose,
}) => {
  const [messages, setMessages] = useState<AiChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  const runLocalAiAction = async (mode: string, customQuery?: string) => {
    setLoading(true);

    // Pull real live DOM text from Android Native WebView when running on a physical phone!
    let nativeExtracted = '';
    try {
      if (typeof window !== 'undefined' && window.BrowserHubNative?.getActivePageText) {
        nativeExtracted = window.BrowserHubNative.getActivePageText() || '';
      }
    } catch {
      nativeExtracted = '';
    }

    const effectiveText =
      nativeExtracted.trim().length > 40 ? nativeExtracted : livePageText;
    const pageInfo = getStaticPageContentForAi(tab.url, effectiveText);

    if (customQuery) {
      setMessages((prev) => [
        ...prev,
        {
          id: `u-${Date.now()}`,
          role: 'user',
          content: customQuery,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    }

    const localRes = runLocalAiClient({
      mode,
      title: tab.title || pageInfo.title,
      url: tab.url,
      content: pageInfo.text,
      query: customQuery || '',
    });

    setMessages((prev) => [
      ...prev,
      {
        id: `a-${Date.now()}`,
        role: 'assistant',
        content: localRes.responseText,
        bullets: localRes.bullets,
        inferenceMs: localRes.inferenceMs,
        mode,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      },
    ]);
    setLoading(false);
  };

  useEffect(() => {
    const startMode = initialMode || 'summarize';
    runLocalAiAction(startMode, initialQuery);
  }, []);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || loading) return;
    const q = input.trim();
    setInput('');
    runLocalAiAction('chat', q);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex justify-end">
      <div className="w-full max-w-md h-full bg-slate-950 border-l border-slate-800 flex flex-col shadow-2xl">
        {/* Header */}
        <div className="p-4 border-b border-slate-800 bg-gradient-to-r from-emerald-950/60 via-cyan-950/40 to-slate-950 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-2xl bg-gradient-to-br from-cyan-500 to-emerald-500 text-slate-950 flex items-center justify-center">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-bold text-white flex items-center gap-1.5">
                BrowserHub Neural Core
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-semibold">
                  No API Key
                </span>
              </div>
              <div className="text-[10px] text-slate-400 flex items-center gap-1">
                <Lock className="w-3 h-3 text-emerald-400" />
                100% Free • Offline Ready • On-Device Privacy
              </div>
            </div>
          </div>

          <div className="flex items-center gap-1">
            <button
              onClick={() => setMessages([])}
              title="Wipe AI RAM Context"
              className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 cursor-pointer"
            >
              <Trash2 className="w-4 h-4" />
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Quick 1-Tap Action Strip */}
        <div className="px-3 py-2.5 bg-slate-900/70 border-b border-slate-800 flex items-center gap-1.5 overflow-x-auto text-[11px]">
          <button
            onClick={() => runLocalAiAction('summarize')}
            className="shrink-0 inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-cyan-500/15 text-cyan-300 border border-cyan-500/30 font-semibold cursor-pointer"
          >
            <BookOpen className="w-3 h-3" /> TL;DR Page
          </button>
          <button
            onClick={() => runLocalAiAction('eli5')}
            className="shrink-0 inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-purple-500/15 text-purple-300 border border-purple-500/30 font-semibold cursor-pointer"
          >
            <Cpu className="w-3 h-3" /> Explain Simply
          </button>
          <button
            onClick={() => runLocalAiAction('audit')}
            className="shrink-0 inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 font-semibold cursor-pointer"
          >
            <ShieldCheck className="w-3 h-3" /> Privacy Audit
          </button>
          <button
            onClick={() => runLocalAiAction('browse_for_me', tab.title)}
            className="shrink-0 inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-amber-500/15 text-amber-300 border border-amber-500/30 font-semibold cursor-pointer"
          >
            <Search className="w-3 h-3" /> AI Brief
          </button>
        </div>

        {/* Messages Stream */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`rounded-2xl p-3.5 text-xs leading-relaxed ${
                msg.role === 'user'
                  ? 'bg-cyan-500/20 border border-cyan-500/30 text-cyan-100 ml-8'
                  : 'bg-slate-900 border border-slate-800 text-slate-200 mr-3'
              }`}
            >
              <div className="whitespace-pre-wrap">{msg.content}</div>
              {msg.bullets && msg.bullets.length > 0 && (
                <ul className="mt-2.5 pt-2.5 border-t border-slate-800 space-y-1.5 text-[11px] text-slate-300">
                  {msg.bullets.map((b, i) => (
                    <li key={i} className="flex items-start gap-1.5">
                      <span className="text-emerald-400 mt-0.5">•</span>
                      <span>{b}</span>
                    </li>
                  ))}
                </ul>
              )}
              {msg.inferenceMs !== undefined && (
                <div className="mt-2 text-[10px] text-slate-500 flex items-center justify-between">
                  <span>On-Device Engine • {msg.inferenceMs}ms</span>
                  <span className="text-emerald-400">0 bytes sent externally</span>
                </div>
              )}
            </div>
          ))}
          {loading && (
            <div className="text-xs text-cyan-300 animate-pulse px-2">
              Synthesizing locally on-device...
            </div>
          )}
        </div>

        {/* Chat Input */}
        <form onSubmit={handleSend} className="p-3 border-t border-slate-800 bg-slate-900/60 flex gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask anything about this page (100% private)..."
            className="flex-1 rounded-xl bg-slate-950 border border-slate-700 px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-400"
          />
          <button
            type="submit"
            className="px-3.5 py-2 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-500 text-slate-950 font-bold text-xs cursor-pointer"
          >
            <Send className="w-4 h-4" />
          </button>
        </form>
      </div>
    </div>
  );
};
