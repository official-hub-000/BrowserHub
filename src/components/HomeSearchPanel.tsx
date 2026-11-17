import React, { useMemo, useRef, useState } from 'react';
import { Search, ArrowRight, X } from 'lucide-react';
import type { BrowserCustomization, SearchEngineId } from '../types/browser';
import { SEARCH_ENGINE_OPTIONS, getSearchEngineOption } from '../data/presets';
import { SearchEngineIcon } from './VectorIcons';

interface HomeSearchPanelProps {
  customization: BrowserCustomization;
  isIncognito?: boolean;
  onSelectSearchEngine: (id: SearchEngineId) => void;
  onNavigate: (url: string) => void;
}

const QUICK_CHIPS = [
  { label: 'Weather today', query: 'weather today' },
  { label: 'Latest tech news', query: 'latest technology news' },
  { label: 'Track my parcel', query: 'track my parcel' },
  { label: 'Word → English', query: 'translate' },
];

/**
 * Home search surface: engine switcher + wide query box + live suggestions.
 * Mirrors the address-bar behaviour so the two entry points never disagree.
 */
export const HomeSearchPanel: React.FC<HomeSearchPanelProps> = ({
  customization,
  isIncognito,
  onSelectSearchEngine,
  onNavigate,
}) => {
  const [text, setText] = useState('');
  const [focused, setFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const engine = getSearchEngineOption(customization.searchEngine);

  const looksLikeUrl = useMemo(() => {
    const t = text.trim();
    if (!t || t.includes(' ')) return false;
    return /^https?:\/\//i.test(t) || /\.[a-z]{2,}(\/|$)/i.test(t);
  }, [text]);

  const submit = (value: string, engineId?: SearchEngineId) => {
    const q = value.trim();
    if (!q) return;
    if (/^https?:\/\//i.test(q) || (/^\S+$/.test(q) && /\.[a-z]{2,}(\/|$)/i.test(q))) {
      onNavigate(/^https?:\/\//i.test(q) ? q : `https://${q}`);
    } else {
      onNavigate(getSearchEngineOption(engineId || customization.searchEngine).buildUrl(q));
    }
    setText('');
    inputRef.current?.blur();
  };

  const showSuggestions = focused && text.trim().length > 0;

  return (
    <div
      className="rounded-2xl border border-slate-800 p-3 space-y-2.5"
      style={{ backgroundColor: '#0e1526' }}
    >
      {/* Engine switcher */}
      <div className="space-y-1.5">
        <div className="flex items-center justify-between px-0.5">
          <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
            {isIncognito ? 'Private search with' : 'Search with'}
          </span>
          <span className="text-[10px] font-semibold text-cyan-400 truncate ml-2">{engine.name}</span>
        </div>
        <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
          {SEARCH_ENGINE_OPTIONS.map((e) => {
            const selected = customization.searchEngine === e.id;
            return (
              <button
                key={e.id}
                type="button"
                aria-label={`Use ${e.name}`}
                aria-pressed={selected}
                onClick={() => {
                  onSelectSearchEngine(e.id);
                  if (text.trim()) inputRef.current?.focus();
                }}
                className={`px-2.5 py-1.5 rounded-xl border text-[11px] font-semibold flex items-center gap-1.5 shrink-0 cursor-pointer ${
                  selected
                    ? 'bg-cyan-500/20 border-cyan-400 text-white'
                    : 'bg-slate-900 border-slate-800 text-slate-300'
                }`}
              >
                <SearchEngineIcon id={e.id} size={14} />
                <span>{e.shortLabel}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Query box */}
      <form
        onSubmit={(ev) => {
          ev.preventDefault();
          submit(text);
        }}
        className="flex items-center gap-2 rounded-xl border border-slate-700 focus-within:border-cyan-400 px-2.5 py-1.5"
        style={{ backgroundColor: '#070b14' }}
      >
        <Search size={16} className="text-slate-400 shrink-0" />
        <input
          ref={inputRef}
          value={text}
          onChange={(ev) => setText(ev.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => window.setTimeout(() => setFocused(false), 160)}
          onKeyDown={(ev) => {
            if (ev.key === 'Escape') inputRef.current?.blur();
          }}
          placeholder={engine.placeholder}
          enterKeyHint="go"
          inputMode="search"
          aria-label="Search the web or enter a website address"
          className="flex-1 min-w-0 bg-transparent text-[13px] text-white placeholder-slate-400 py-1 focus:outline-none"
        />
        {text && (
          <button
            type="button"
            aria-label="Clear search text"
            title="Clear"
            onClick={() => {
              setText('');
              inputRef.current?.focus();
            }}
            className="p-1 text-slate-400 hover:text-white cursor-pointer shrink-0"
          >
            <X size={14} />
          </button>
        )}
        <button
          type="submit"
          aria-label="Search"
          title="Search"
          className="px-2.5 py-1.5 rounded-lg bg-cyan-400 text-slate-950 font-bold shrink-0 cursor-pointer"
        >
          <ArrowRight size={15} />
        </button>
      </form>

      {/* Suggestions */}
      {showSuggestions ? (
        <div className="rounded-xl border border-slate-800 divide-y divide-slate-800 overflow-hidden">
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => submit(text)}
            className="w-full px-3 py-2 flex items-center gap-2 text-left hover:bg-slate-800 cursor-pointer"
          >
            <Search size={14} className="text-cyan-400 shrink-0" />
            <span className="text-[12px] text-slate-200 truncate">
              {looksLikeUrl ? 'Open' : 'Search'} <strong className="font-semibold text-white">{text}</strong>
              {looksLikeUrl ? '' : ` on ${engine.name}`}
            </span>
          </button>
          {SEARCH_ENGINE_OPTIONS.filter((e) => e.id !== customization.searchEngine)
            .slice(0, 2)
            .map((e) => (
              <button
                key={e.id}
                type="button"
                onMouseDown={(ev) => ev.preventDefault()}
                onClick={() => submit(text, e.id)}
                className="w-full px-3 py-1.5 flex items-center gap-2 text-left hover:bg-slate-800 cursor-pointer"
              >
                <SearchEngineIcon id={e.id} size={13} />
                <span className="text-[11px] text-slate-400 truncate">instead on {e.name}</span>
              </button>
            ))}
        </div>
      ) : (
        <div className="flex items-center gap-1.5 overflow-x-auto">
          {QUICK_CHIPS.map((chip) => (
            <button
              key={chip.label}
              type="button"
              onClick={() => submit(chip.query)}
              className="px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-800 text-[10px] text-slate-300 shrink-0 cursor-pointer hover:border-slate-600"
            >
              {chip.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
