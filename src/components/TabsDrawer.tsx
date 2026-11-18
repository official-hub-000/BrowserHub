import React, { useState } from 'react';
import {
  Plus,
  X,
  EyeOff,
  Pin,
  Columns2,
  Flame,
  Moon,
  Fingerprint,
  Layers,
} from 'lucide-react';
import type { BrowserTab } from '../types/browser';
import { useExitTransition } from '../utils/useExitTransition';

interface TabsDrawerProps {
  tabs: BrowserTab[];
  activeTabId: string;
  splitTabId: string | null;
  biometricLockIncognito: boolean;
  onSelectTab: (id: string) => void;
  onNewTab: (incognito: boolean, groupId?: BrowserTab['groupId']) => void;
  onCloseTab: (id: string) => void;
  onTogglePin: (id: string) => void;
  onToggleHibernate: (id: string) => void;
  onToggleSplitTab: (id: string) => void;
  onFireBurnAll: () => void;
  onClose: () => void;
}

export const TabsDrawer: React.FC<TabsDrawerProps> = ({
  tabs,
  activeTabId,
  splitTabId,
  biometricLockIncognito,
  onSelectTab,
  onNewTab,
  onCloseTab,
  onTogglePin,
  onToggleHibernate,
  onToggleSplitTab,
  onFireBurnAll,
  onClose,
}) => {
  const [mode, setMode] = useState<'normal' | 'incognito'>('normal');
  const [vaultUnlocked, setVaultUnlocked] = useState(!biometricLockIncognito);
  const [groupFilter, setGroupFilter] = useState<'all' | BrowserTab['groupId']>('all');

  const filteredTabs = tabs.filter((t) => {
    if (mode === 'incognito' && !t.isIncognito) return false;
    if (mode === 'normal' && t.isIncognito) return false;
    if (groupFilter !== 'all' && t.groupId !== groupFilter) return false;
    return true;
  });

  const savedRamMb = tabs
    .filter((t) => t.isHibernated)
    .reduce((acc, t) => acc + t.memoryMb, 0);

  const exit = useExitTransition(onClose, 'bh-dialog', 'bh-dialog-out');

  return (
    <div className={`bh-overlay fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-3${exit.isClosing ? ' bh-overlay-out' : ''}`} onClick={exit.requestClose}>
      <div className={`${exit.className} w-full max-w-2xl rounded-3xl bg-black border border-slate-800 shadow-2xl flex flex-col max-h-[88vh] overflow-hidden`} onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2 bg-[#08080b]">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setMode('normal')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold cursor-pointer ${
                mode === 'normal'
                  ? 'bg-cyan-500 text-slate-950'
                  : 'bg-[#121216] text-slate-300'
              }`}
            >
              Tabs ({tabs.filter((t) => !t.isIncognito).length})
            </button>
            <button
              onClick={() => setMode('incognito')}
              className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold cursor-pointer ${
                mode === 'incognito'
                  ? 'bg-purple-500 text-white'
                  : 'bg-[#121216] text-purple-300'
              }`}
            >
              <EyeOff className="w-3.5 h-3.5" />
              Incognito Vault ({tabs.filter((t) => t.isIncognito).length})
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                onFireBurnAll();
                onClose();
              }}
              title="Fire Button: Incinerate Session Data & Reset"
              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-gradient-to-r from-orange-500 to-rose-600 text-white text-xs font-bold hover:opacity-95 cursor-pointer"
            >
              <Flame className="w-3.5 h-3.5" />
              Fire Wipe
            </button>
            <button aria-label="Close" title="Close" onClick={onClose} className="p-1.5 rounded-xl hover:bg-[#121216] text-slate-400 cursor-pointer">
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Workspace / Tab Group Filter Strip */}
        <div className="px-4 py-2 bg-[#040405] border-b border-slate-800 flex flex-wrap items-center justify-between gap-2 text-[11px]">
          <div className="flex items-center gap-1.5">
            <Layers className="w-3.5 h-3.5 text-slate-400" />
            {(['all', 'main', 'work', 'research', 'media'] as const).map((g) => (
              <button
                key={g}
                onClick={() => setGroupFilter(g)}
                className={`px-2.5 py-0.5 rounded-full capitalize cursor-pointer ${
                  groupFilter === g
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 font-semibold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {g}
              </button>
            ))}
          </div>
          {savedRamMb > 0 && (
            <span className="text-emerald-400 font-medium">
              <Moon size={11} className="inline-block shrink-0" /> {savedRamMb} MB RAM freed by Tab
              Hibernation
            </span>
          )}
        </div>

        {/* Biometric Vault Gate for Incognito */}
        {mode === 'incognito' && biometricLockIncognito && !vaultUnlocked ? (
          <div className="p-10 text-center space-y-3">
            <Fingerprint className="w-12 h-12 text-purple-400 mx-auto animate-pulse" />
            <div className="text-sm font-bold text-white">Incognito Vault Biometric Lock</div>
            <p className="text-xs text-slate-400 max-w-xs mx-auto">
              Private tabs are hardware-encrypted and hidden from recent-apps switcher.
            </p>
            <button
              onClick={() => setVaultUnlocked(true)}
              className="px-5 py-2 rounded-xl bg-purple-500 text-white text-xs font-bold hover:bg-purple-400 cursor-pointer"
            >
              Authenticate Android Biometric
            </button>
          </div>
        ) : (
          <div className="p-4 overflow-y-auto grid sm:grid-cols-2 gap-3 flex-1">
            {filteredTabs.map((t) => {
              const isActive = t.id === activeTabId;
              const isSplit = t.id === splitTabId;
              return (
                <div
                  key={t.id}
                  onClick={() => {
                    onSelectTab(t.id);
                    onClose();
                  }}
                  className={`rounded-2xl p-3.5 border transition cursor-pointer flex flex-col justify-between gap-3 ${
                    isActive
                      ? 'bg-cyan-500/10 border-cyan-400'
                      : isSplit
                        ? 'bg-purple-500/10 border-purple-400'
                        : 'bg-[#08080b] border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-white truncate flex items-center gap-1.5">
                        {t.isPinned && <Pin className="w-3 h-3 text-amber-400 shrink-0" />}
                        {t.isIncognito && <EyeOff className="w-3 h-3 text-purple-400 shrink-0" />}
                        <span className="truncate">{t.title}</span>
                      </div>
                      <div className="text-[10px] text-slate-400 truncate mt-0.5">{t.url}</div>
                    </div>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onCloseTab(t.id);
                      }}
                      aria-label={`Close ${t.title}`}
                      title="Close tab"
                      className="p-1 rounded-lg hover:bg-[#121216] text-slate-400 shrink-0 cursor-pointer"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <div
                    onClick={(e) => e.stopPropagation()}
                    className="flex items-center justify-between pt-2 border-t border-slate-800/80 text-[10px]"
                  >
                    <span className="text-slate-400">
                      {t.isHibernated ? (
                        <span className="inline-flex items-center gap-1">
                          <Moon size={10} className="shrink-0" />
                          <span>Sleeping (4 MB)</span>
                        </span>
                      ) : (
                        `${t.memoryMb} MB RAM`
                      )}
                    </span>
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => onTogglePin(t.id)}
                        title="Pin Tab"
                        aria-label="Pin tab"
                        className={`p-1 rounded hover:bg-[#121216] cursor-pointer ${
                          t.isPinned ? 'text-amber-400' : 'text-slate-400'
                        }`}
                      >
                        <Pin className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => onToggleHibernate(t.id)}
                        title="Hibernate Tab to Free RAM"
                        aria-label="Hibernate tab to free RAM"
                        className={`p-1 rounded hover:bg-[#121216] cursor-pointer ${
                          t.isHibernated ? 'text-emerald-400' : 'text-slate-400'
                        }`}
                      >
                        <Moon className="w-3.5 h-3.5" />
                      </button>
                      {t.id !== activeTabId && (
                        <button
                          onClick={() => {
                            onToggleSplitTab(t.id);
                            onClose();
                          }}
                          title="Open Side-by-Side Split View"
                          aria-label="Open side-by-side split view"
                          className={`inline-flex items-center gap-1 px-2 py-0.5 rounded font-semibold cursor-pointer ${
                            isSplit
                              ? 'bg-purple-500 text-white'
                              : 'bg-[#121216] text-cyan-300 hover:bg-[#1a1a1f]'
                          }`}
                        >
                          <Columns2 className="w-3 h-3" />
                          {isSplit ? 'Unsplit' : 'Split'}
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Footer New Tab Actions */}
        <div className="p-3 border-t border-slate-800 bg-[#060608] flex items-center justify-between gap-2">
          <button
            onClick={() => {
              onNewTab(mode === 'incognito', groupFilter === 'all' ? 'main' : groupFilter);
              onClose();
            }}
            className="flex-1 py-2.5 rounded-2xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs inline-flex items-center justify-center gap-1.5 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            New {mode === 'incognito' ? 'Incognito Vault' : 'Speed'} Tab
          </button>
        </div>
      </div>
    </div>
  );
};
