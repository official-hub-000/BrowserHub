import React from 'react';
import { Languages, X, Check } from 'lucide-react';
import { SUPPORTED_LANGUAGES } from '../utils/translator';
import { LangBadge } from './VectorIcons';

interface TranslateModalProps {
  activeLang: string | null;
  bilingual: boolean;
  onSelectLang: (langCode: string | null) => void;
  onToggleBilingual: (val: boolean) => void;
  onClose: () => void;
}

export const TranslateModal: React.FC<TranslateModalProps> = ({
  activeLang,
  bilingual,
  onSelectLang,
  onToggleBilingual,
  onClose,
}) => {
  return (
    <div
      className="bh-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
      style={{ backgroundColor: 'rgba(0, 0, 0, 0.88)' }}
      onClick={onClose}
    >
      <div
        className="bh-sheet w-full max-w-md rounded-t-3xl sm:rounded-3xl border border-slate-800 shadow-2xl overflow-hidden flex flex-col max-h-[82vh]"
        style={{ backgroundColor: '#000000' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-5 py-4 border-b border-slate-800 flex items-center justify-between"
          style={{ backgroundColor: '#000000' }}
        >
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-indigo-500/20 border border-indigo-500/40 text-indigo-300 flex items-center justify-center">
              <Languages size={18} />
            </div>
            <div>
              <div className="text-sm font-bold text-white">Page Translator</div>
              <div className="text-[11px] text-slate-400">14 Languages • Instant On-Device</div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {activeLang && (
              <button
                onClick={() => {
                  onSelectLang(null);
                  onClose();
                }}
                className="px-3 py-1.5 rounded-xl bg-rose-500/20 border border-rose-500/40 text-rose-300 text-xs font-semibold cursor-pointer"
              >
                Show Original
              </button>
            )}
            <button aria-label="Close" title="Close"
              onClick={onClose}
              className="p-2 rounded-xl hover:bg-[#121216] text-slate-300 cursor-pointer"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="p-4 space-y-3 overflow-y-auto">
          <label className="flex items-center justify-between px-3.5 py-2.5 rounded-xl bg-black border border-slate-800 text-xs text-slate-200 cursor-pointer">
            <div>
              <div className="font-semibold text-white">Bilingual Side-by-Side Mode</div>
              <div className="text-[11px] text-slate-400">Keep original text visible alongside translation</div>
            </div>
            <input
              type="checkbox"
              checked={bilingual}
              onChange={(e) => onToggleBilingual(e.target.checked)}
              className="w-4 h-4 accent-indigo-500"
            />
          </label>

          <div className="grid grid-cols-2 gap-2">
            {SUPPORTED_LANGUAGES.map((lang) => {
              const selected = activeLang === lang.code;
              return (
                <button
                  key={lang.code}
                  type="button"
                  onClick={() => {
                    onSelectLang(lang.code);
                    onClose();
                  }}
                  className={`px-3 py-2.5 rounded-xl border text-left text-xs flex items-center justify-between cursor-pointer transition ${
                    selected
                      ? 'bg-indigo-600 border-indigo-400 text-white font-bold'
                      : 'bg-black hover:bg-[#121216] border-slate-800 text-slate-200'
                  }`}
                >
                  <span className="flex items-center gap-2 min-w-0">
                    <LangBadge code={lang.code} size={22} />
                    <span className="truncate">{lang.nativeName}</span>
                  </span>
                  {selected && <Check size={14} className="shrink-0" />}
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};
