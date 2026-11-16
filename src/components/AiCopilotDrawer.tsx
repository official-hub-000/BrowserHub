import React, { useEffect, useRef, useState } from 'react';
import {
  Sparkles,
  X,
  Send,
  ShieldCheck,
  Trash2,
  BookOpen,
  Copy,
  Check,
  FileText,
  PenLine,
  WifiOff,
  Lightbulb,
} from 'lucide-react';
import type { AiChatMessage, BrowserTab } from '../types/browser';
import { getPageContextForAi } from '../utils/pageTextExtractor';
import { runSmartHybridAi } from '../utils/localAiEngine';

interface AiCopilotDrawerProps {
  tab: BrowserTab;
  livePageText?: string;
  initialMode?: string;
  initialQuery?: string;
  onClose: () => void;
}

/** Renders **bold** and `code` inline without ever showing raw asterisks. */
function renderInline(line: string): React.ReactNode[] {
  const parts: React.ReactNode[] = [];
  const tokenRegex = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let k = 0;

  while ((match = tokenRegex.exec(line)) !== null) {
    if (match.index > lastIndex) parts.push(line.slice(lastIndex, match.index));
    const token = match[0];
    if (token.startsWith('**')) {
      parts.push(
        <strong key={`b${k++}`} className="font-semibold text-white">
          {token.slice(2, -2)}
        </strong>
      );
    } else {
      parts.push(
        <code
          key={`c${k++}`}
          className="px-1.5 py-0.5 rounded bg-slate-800 text-cyan-300 font-mono text-[11px]"
        >
          {token.slice(1, -1)}
        </code>
      );
    }
    lastIndex = match.index + token.length;
  }
  if (lastIndex < line.length) parts.push(line.slice(lastIndex));
  return parts;
}

function FormattedAiMessage({ content }: { content: string }) {
  const blocks = content.split(/(```[\s\S]*?```)/g);

  return (
    <div className="space-y-2 text-[13px] leading-relaxed text-slate-200">
      {blocks.map((block, bIdx) => {
        if (block.startsWith('```') && block.endsWith('```')) {
          const body = block.replace(/^```[a-zA-Z0-9_-]*\n?/, '').replace(/```$/, '');
          return (
            <pre
              key={bIdx}
              className="p-3 rounded-xl bg-slate-950 border border-slate-800 text-xs font-mono text-emerald-300 overflow-x-auto"
            >
              {body}
            </pre>
          );
        }

        return (
          <div key={bIdx} className="space-y-1.5">
            {block.split('\n').map((rawLine, lIdx) => {
              const line = rawLine.trim();
              if (!line) return null;

              const heading = /^(#{1,4})\s+(.*)$/.exec(line);
              if (heading) {
                return (
                  <div key={lIdx} className="text-[13px] font-bold text-cyan-300 pt-1.5">
                    {renderInline(heading[2].replace(/\*\*/g, ''))}
                  </div>
                );
              }

              if (/^([-•*])\s+/.test(line)) {
                return (
                  <div key={lIdx} className="flex items-start gap-2 pl-0.5">
                    <span className="text-cyan-400 font-bold leading-5">•</span>
                    <span className="flex-1">{renderInline(line.replace(/^([-•*])\s+/, ''))}</span>
                  </div>
                );
              }

              const num = /^(\d+)\.\s+(.*)$/.exec(line);
              if (num) {
                return (
                  <div key={lIdx} className="flex items-start gap-2 pl-0.5">
                    <span className="text-cyan-400 font-bold min-w-[15px] leading-5">{num[1]}.</span>
                    <span className="flex-1">{renderInline(num[2])}</span>
                  </div>
                );
              }

              return <p key={lIdx}>{renderInline(line)}</p>;
            })}
          </div>
        );
      })}
    </div>
  );
}

const QUICK_ACTIONS = [
  { mode: 'summarize', label: 'Summarize', icon: FileText, tone: 'text-cyan-300', prompt: 'Summarize this page' },
  { mode: 'eli5', label: 'Explain', icon: BookOpen, tone: 'text-emerald-300', prompt: 'Explain this page simply' },
  { mode: 'security_audit', label: 'Privacy', icon: ShieldCheck, tone: 'text-amber-300', prompt: 'Check page privacy & security' },
  { mode: 'draft', label: 'Draft', icon: PenLine, tone: 'text-purple-300', prompt: 'Write a short professional email about ' },
] as const;

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
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const startedRef = useRef(false);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages, loading]);

  const execute = async (mode: string, customQuery?: string, userLabel?: string) => {
    if (loading) return;
    setLoading(true);

    let nativeText = '';
    try {
      nativeText = window.BrowserHubNative?.getActivePageText?.() || '';
    } catch {
      nativeText = '';
    }

    const rawPageText = nativeText.trim().length > 40 ? nativeText : livePageText || '';
    const ctx = getPageContextForAi(tab.url, rawPageText);

    const visibleUserMsg = userLabel || customQuery;
    if (visibleUserMsg) {
      setMessages((prev) => [
        ...prev,
        {
          id: `u-${Date.now()}`,
          role: 'user',
          content: visibleUserMsg,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    }

    const res = await runSmartHybridAi({
      mode,
      title: tab.title && tab.title !== tab.url ? tab.title : ctx.title,
      url: tab.url,
      content: ctx.text,
      query: customQuery || '',
      history: messages.map((m) => ({ role: m.role, content: m.content })),
    });

    setMessages((prev) => [
      ...prev,
      {
        id: `a-${Date.now()}`,
        role: 'assistant',
        content: res.responseText,
        bullets: res.bullets,
        inferenceMs: res.inferenceMs,
        engineLabel: res.engineLabel,
        mode,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      },
    ]);
    setLoading(false);
  };

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;
    if (initialQuery && initialQuery.trim()) {
      execute(initialMode || 'chat', initialQuery.trim());
    } else if (initialMode && !['idle', 'chat', 'draft'].includes(initialMode)) {
      const labels: Record<string, string> = {
        summarize: 'Summarize this page',
        eli5: 'Explain this page simply',
        security_audit: 'Check page privacy & security',
      };
      execute(initialMode, undefined, labels[initialMode] || 'Summarize this page');
    }
  }, []);

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const q = input.trim();
    if (!q || loading) return;
    setInput('');
    execute('chat', q);
  };

  const copyMsg = (id: string, text: string) => {
    const plain = text.replace(/[*`#]/g, '');
    navigator.clipboard?.writeText(plain).catch(() => {});
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const ctx = getPageContextForAi(tab.url, livePageText);
  const pageStatus =
    ctx.kind === 'home'
      ? { text: 'No page open — ask anything', tone: 'text-slate-400' }
      : ctx.kind === 'search'
      ? { text: 'Search results page — open a result to summarize', tone: 'text-amber-300' }
      : ctx.text
      ? { text: 'Reading this page\'s real text', tone: 'text-emerald-300' }
      : { text: 'Waiting for page text', tone: 'text-amber-300' };

  return (
    <div className="fixed inset-0 z-50 flex justify-end" style={{ backgroundColor: 'rgba(2, 6, 23, 0.8)' }}>
      <div
        className="w-full max-w-md h-full border-l border-slate-800 flex flex-col shadow-2xl"
        style={{ backgroundColor: '#090e1a' }}
      >
        {/* Header */}
        <div
          className="px-4 py-3 border-b border-slate-800 flex items-center justify-between shrink-0"
          style={{ backgroundColor: '#0f172a' }}
        >
          <div className="flex items-center gap-2.5 min-w-0">
            <div
              className="w-9 h-9 rounded-xl flex items-center justify-center text-slate-950 shrink-0"
              style={{ background: 'linear-gradient(135deg, #22d3ee, #34d399)' }}
            >
              <Sparkles size={18} />
            </div>
            <div className="min-w-0">
              <div className="text-sm font-bold text-white flex items-center gap-1.5">
                <span>BrowserHub AI</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-emerald-950 border border-emerald-500/40 text-emerald-300 font-semibold">
                  No key
                </span>
              </div>
              <div className={`text-[11px] truncate ${pageStatus.tone}`}>{pageStatus.text}</div>
            </div>
          </div>
          <div className="flex items-center gap-1 shrink-0">
            {messages.length > 0 && (
              <button
                onClick={() => setMessages([])}
                title="Clear chat"
                aria-label="Clear chat"
                className="p-2 rounded-xl hover:bg-slate-800 text-slate-400 hover:text-white cursor-pointer"
              >
                <Trash2 size={16} />
              </button>
            )}
            <button
              onClick={onClose}
              title="Close assistant"
              aria-label="Close assistant"
              className="p-2 rounded-xl hover:bg-slate-800 text-slate-300 hover:text-white cursor-pointer"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Quick actions */}
        <div
          className="px-3 py-2 border-b border-slate-800 grid grid-cols-4 gap-1.5 shrink-0"
          style={{ backgroundColor: '#0b1220' }}
        >
          {QUICK_ACTIONS.map(({ mode, label, icon: Icon, tone, prompt }) => (
            <button
              key={mode}
              onClick={() => {
                if (mode === 'draft') {
                  setInput(prompt);
                  inputRef.current?.focus();
                } else {
                  execute(mode, undefined, prompt);
                }
              }}
              disabled={loading}
              className="px-1 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-[11px] font-semibold flex flex-col items-center gap-1 cursor-pointer disabled:opacity-50"
            >
              <Icon size={15} className={tone} />
              <span className="text-slate-200">{label}</span>
            </button>
          ))}
        </div>

        {/* Conversation */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto p-3.5 space-y-3">
          {messages.length === 0 && !loading && (
            <div className="space-y-3">
              <div className="rounded-2xl border border-slate-800 p-4 space-y-2" style={{ backgroundColor: '#111827' }}>
                <div className="text-sm font-bold text-white">Ask me anything</div>
                <p className="text-xs text-slate-300 leading-relaxed">
                  General questions, page summaries, math, and writing help — with no API key and no
                  account. I only read the page text you already have open.
                </p>
              </div>

              <div className="space-y-1.5">
                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-1 flex items-center gap-1">
                  <Lightbulb size={11} /> Try asking
                </div>
                {[
                  'Summarize this page',
                  'Explain how HTTPS keeps me safe',
                  'Calculate 18% of 2450',
                  'Draft a polite follow-up email',
                  'What is the capital of Japan?',
                ].map((sample) => (
                  <button
                    key={sample}
                    onClick={() => execute('chat', sample)}
                    className="w-full text-left px-3 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs text-slate-200 cursor-pointer"
                  >
                    {sample}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`rounded-2xl p-3.5 ${
                msg.role === 'user'
                  ? 'ml-8 border border-cyan-500/40 text-white'
                  : 'border border-slate-800 text-slate-200'
              }`}
              style={{ backgroundColor: msg.role === 'user' ? '#0e3a4c' : '#111827' }}
            >
              {msg.role === 'user' ? (
                <div className="text-[13px] font-medium leading-relaxed break-words">{msg.content}</div>
              ) : (
                <FormattedAiMessage content={msg.content} />
              )}

              {msg.role === 'assistant' && msg.bullets && msg.bullets.length > 0 && (
                <div className="mt-2 pt-2 border-t border-slate-800 space-y-1">
                  {msg.bullets.map((b, i) => (
                    <div key={i} className="text-[11px] text-emerald-300 flex items-start gap-1.5">
                      <WifiOff size={10} className="mt-0.5 shrink-0 opacity-70" />
                      <span>{b}</span>
                    </div>
                  ))}
                </div>
              )}

              {msg.role === 'assistant' && (
                <div className="mt-2 pt-2 border-t border-slate-800 flex items-center justify-between gap-2 text-[10px] text-slate-400">
                  <span className="truncate">
                    {msg.engineLabel || 'BrowserHub AI'}
                    {msg.inferenceMs ? ` • ${msg.inferenceMs}ms` : ''}
                  </span>
                  <button
                    onClick={() => copyMsg(msg.id, msg.content)}
                    className="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 cursor-pointer shrink-0"
                  >
                    {copiedId === msg.id ? <Check size={11} className="text-emerald-400" /> : <Copy size={11} />}
                    <span>{copiedId === msg.id ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>
              )}
            </div>
          ))}

          {loading && (
            <div
              className="rounded-2xl p-3.5 border border-slate-800 text-xs text-cyan-300 flex items-center gap-2"
              style={{ backgroundColor: '#111827' }}
            >
              <Sparkles size={14} className="animate-spin shrink-0" />
              <span>Thinking…</span>
            </div>
          )}
        </div>

        {/* Composer */}
        <form
          onSubmit={onSubmit}
          className="p-3 border-t border-slate-800 flex items-center gap-2 shrink-0"
          style={{ backgroundColor: '#0f172a' }}
        >
          <input
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask anything…"
            className="flex-1 min-w-0 rounded-xl bg-slate-900 border border-slate-700 focus:border-cyan-400 px-3.5 py-2.5 text-sm text-white placeholder-slate-400 focus:outline-none"
          />
          <button
            type="submit"
            disabled={loading || !input.trim()}
            aria-label="Send message"
            title="Send"
            className="px-3.5 py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 disabled:opacity-40 text-slate-950 font-bold cursor-pointer shrink-0"
          >
            <Send size={16} />
          </button>
        </form>
      </div>
    </div>
  );
};
