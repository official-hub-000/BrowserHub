import React, { useRef, useState } from 'react';
import {
  Puzzle,
  X,
  Upload,
  Globe,
  Code2,
  Trash2,
  Plus,
  CheckCircle2,
  EyeOff,
} from 'lucide-react';
import type { BrowserExtension } from '../types/browser';
import { DISCOVER_WORLD_EXTENSIONS } from '../data/presets';
import { parseGlobalStoreUrl, unpackExtensionFile } from '../utils/extensionUnpacker';

interface ExtensionsModalProps {
  extensions: BrowserExtension[];
  onToggleExtension: (id: string) => void;
  onToggleIncognitoExt: (id: string) => void;
  onInstallExtension: (ext: BrowserExtension) => void;
  onRemoveExtension: (id: string) => void;
  onClose: () => void;
}

export const ExtensionsModal: React.FC<ExtensionsModalProps> = ({
  extensions,
  onToggleExtension,
  onToggleIncognitoExt,
  onInstallExtension,
  onRemoveExtension,
  onClose,
}) => {
  const [activeTab, setActiveTab] = useState<'installed' | 'store' | 'import' | 'studio'>('installed');
  const [storeUrlInput, setStoreUrlInput] = useState('');
  const [statusToast, setStatusToast] = useState<string | null>(null);
  const [studioName, setStudioName] = useState('Custom Neon Link Highlighter');
  const [studioType, setStudioType] = useState<'css' | 'js'>('css');
  const [studioCode, setStudioCode] = useState(
    `/* Live UserCSS injected into active tabs */\na:hover {\n  text-shadow: 0 0 8px #22d3ee !important;\n}`
  );
  const fileInputRef = useRef<HTMLInputElement>(null);

  const flashNotice = (msg: string) => {
    setStatusToast(msg);
    setTimeout(() => setStatusToast(null), 3200);
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const unpacked = await unpackExtensionFile(file);
    onInstallExtension(unpacked);
    flashNotice(`Installed "${unpacked.name}" (${unpacked.format})!`);
    setActiveTab('installed');
    e.target.value = '';
  };

  const handleImportStoreUrl = (e: React.FormEvent) => {
    e.preventDefault();
    if (!storeUrlInput.trim()) return;
    const ext = parseGlobalStoreUrl(storeUrlInput);
    onInstallExtension(ext);
    setStoreUrlInput('');
    flashNotice(`Imported "${ext.name}" (${ext.format})!`);
    setActiveTab('installed');
  };

  const handleCreateStudioExt = (e: React.FormEvent) => {
    e.preventDefault();
    if (!studioName.trim() || !studioCode.trim()) return;
    const newExt: BrowserExtension = {
      id: `ext-studio-${Date.now()}`,
      name: studioName.trim(),
      version: '1.0.0',
      author: 'Local Studio',
      source: 'userscript',
      format: studioType === 'css' ? 'NATIVE-MOD' : 'USER.JS',
      description:
        studioType === 'css'
          ? 'Live UserCSS stylesheet injected across tabs.'
          : 'Custom Userscript running in BrowserHub GM_* sandbox.',
      icon: studioType === 'css' ? '🎨' : '⚡',
      enabled: true,
      allowIncognito: true,
      permissions: ['<all_urls>', 'scripting'],
      effectKey: studioType === 'css' ? 'custom-css' : 'custom-script',
      customCss: studioType === 'css' ? studioCode : undefined,
      customJs: studioType === 'js' ? studioCode : undefined,
    };
    onInstallExtension(newExt);
    flashNotice(`Injected "${newExt.name}" live!`);
    setActiveTab('installed');
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-3">
      <div className="w-full max-w-2xl rounded-3xl bg-slate-950 border border-slate-800 shadow-2xl flex flex-col max-h-[90vh] overflow-hidden">
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-gradient-to-r from-purple-950/50 to-slate-900">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-2xl bg-purple-500/20 text-purple-300 flex items-center justify-center">
              <Puzzle className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-bold text-white">
                Universal World Extension Engine
              </div>
              <div className="text-[11px] text-slate-400">
                Chrome Web Store (.CRX3) • Firefox AMO (.XPI) • GreasyFork (.USER.JS)
              </div>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 cursor-pointer">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="grid grid-cols-4 gap-1 p-2 bg-slate-900/70 border-b border-slate-800 text-xs">
          {[
            { id: 'installed', label: `Installed (${extensions.length})` },
            { id: 'store', label: '1-Tap Hub' },
            { id: 'import', label: 'CRX / XPI / URL' },
            { id: 'studio', label: 'Userscript IDE' },
          ].map((t) => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id as typeof activeTab)}
              className={`py-2 px-2 rounded-xl font-semibold transition cursor-pointer ${
                activeTab === t.id
                  ? 'bg-purple-500 text-white'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {statusToast && (
          <div className="mx-4 mt-3 px-3 py-2 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-200 text-xs flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            {statusToast}
          </div>
        )}

        {/* Content Body */}
        <div className="overflow-y-auto p-4 space-y-3 flex-1">
          {activeTab === 'installed' &&
            extensions.map((ext) => (
              <div
                key={ext.id}
                className="rounded-2xl bg-slate-900/80 border border-slate-800 p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="flex items-start gap-3 min-w-0">
                  <div className="text-2xl shrink-0 mt-0.5">{ext.icon}</div>
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-xs font-bold text-white">{ext.name}</span>
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-cyan-300 font-mono">
                        {ext.format}
                      </span>
                      <span className="text-[10px] text-slate-500">v{ext.version}</span>
                    </div>
                    <p className="text-[11px] text-slate-400 leading-relaxed">{ext.description}</p>
                    <div className="flex flex-wrap items-center gap-2 pt-0.5">
                      <button
                        onClick={() => onToggleIncognitoExt(ext.id)}
                        className={`inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full border cursor-pointer ${
                          ext.allowIncognito
                            ? 'border-purple-500/40 bg-purple-500/15 text-purple-200'
                            : 'border-slate-700 text-slate-500'
                        }`}
                      >
                        <EyeOff className="w-2.5 h-2.5" />
                        {ext.allowIncognito ? 'Allowed in Incognito' : 'Incognito Off'}
                      </button>
                      <button
                        onClick={() => onRemoveExtension(ext.id)}
                        className="inline-flex items-center gap-1 text-[10px] text-rose-400 hover:underline cursor-pointer"
                      >
                        <Trash2 className="w-2.5 h-2.5" /> Remove
                      </button>
                    </div>
                  </div>
                </div>

                <button
                  onClick={() => onToggleExtension(ext.id)}
                  className={`px-3.5 py-1.5 rounded-full text-xs font-bold shrink-0 cursor-pointer ${
                    ext.enabled
                      ? 'bg-emerald-500 text-slate-950'
                      : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {ext.enabled ? 'ON' : 'OFF'}
                </button>
              </div>
            ))}

          {activeTab === 'store' && (
            <div className="space-y-2.5">
              <div className="text-xs text-slate-400">
                Verified desktop extensions pre-configured for 1-tap installation on Android:
              </div>
              {DISCOVER_WORLD_EXTENSIONS.map((item) => {
                const alreadyInstalled = extensions.some((e) => e.id === item.id);
                return (
                  <div
                    key={item.id}
                    className="rounded-2xl bg-slate-900/80 border border-slate-800 p-3.5 flex items-center justify-between gap-3"
                  >
                    <div className="flex items-start gap-3">
                      <div className="text-2xl">{item.icon}</div>
                      <div>
                        <div className="text-xs font-bold text-white flex items-center gap-2">
                          {item.name}
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-purple-300">
                            {item.format}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-400 mt-0.5">{item.description}</p>
                        <div className="text-[10px] text-amber-300 mt-1">
                          {item.rating} • {item.usersCount} users
                        </div>
                      </div>
                    </div>
                    <button
                      disabled={alreadyInstalled}
                      onClick={() => {
                        onInstallExtension({
                          ...item,
                          enabled: true,
                          allowIncognito: true,
                        });
                        flashNotice(`Added "${item.name}"!`);
                      }}
                      className={`px-3 py-1.5 rounded-xl text-xs font-bold shrink-0 cursor-pointer ${
                        alreadyInstalled
                          ? 'bg-slate-800 text-slate-500 cursor-default'
                          : 'bg-purple-500 hover:bg-purple-400 text-white'
                      }`}
                    >
                      {alreadyInstalled ? 'Installed' : '+ Add'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {activeTab === 'import' && (
            <div className="space-y-4">
              {/* Store URL Importer */}
              <form
                onSubmit={handleImportStoreUrl}
                className="rounded-2xl bg-slate-900 border border-slate-800 p-4 space-y-3"
              >
                <div className="text-xs font-bold text-white flex items-center gap-1.5">
                  <Globe className="w-4 h-4 text-cyan-400" />
                  Import From Any Global Extension Store URL or Name
                </div>
                <p className="text-[11px] text-slate-400">
                  Paste any link from <strong>Chrome Web Store</strong>, <strong>Firefox Add-ons (AMO)</strong>, <strong>GreasyFork</strong>, <strong>Microsoft Edge Add-ons</strong>, or type any extension name in the world:
                </p>
                <div className="flex gap-2">
                  <input
                    value={storeUrlInput}
                    onChange={(e) => setStoreUrlInput(e.target.value)}
                    placeholder="https://chromewebstore.google.com/detail/refined-github/..."
                    className="flex-1 rounded-xl bg-slate-950 border border-slate-700 px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-400"
                  />
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-xl bg-cyan-500 text-slate-950 font-bold text-xs hover:bg-cyan-400 shrink-0 cursor-pointer"
                  >
                    Import
                  </button>
                </div>
              </form>

              {/* Binary File Unpacker (.CRX / .XPI / .ZIP / .USER.JS) */}
              <div className="rounded-2xl border-2 border-dashed border-purple-500/40 bg-purple-950/15 p-5 text-center space-y-2.5">
                <Upload className="w-7 h-7 text-purple-400 mx-auto" />
                <div className="text-xs font-bold text-white">
                  Sideload Local .CRX, .XPI, .ZIP, or .USER.JS File
                </div>
                <p className="text-[11px] text-slate-400 max-w-md mx-auto">
                  Real binary CRX2/CRX3 header stripper + JSZip manifest.json verifier. Drop any desktop package from anywhere in the world!
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".crx,.xpi,.zip,.js"
                  onChange={handleFileUpload}
                  className="hidden"
                />
                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-purple-500 hover:bg-purple-400 text-white text-xs font-bold cursor-pointer"
                >
                  <Plus className="w-4 h-4" />
                  Select Extension Package
                </button>
              </div>
            </div>
          )}

          {activeTab === 'studio' && (
            <form onSubmit={handleCreateStudioExt} className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-white flex items-center gap-1.5">
                  <Code2 className="w-4 h-4 text-emerald-400" />
                  Live Userscript &amp; Custom CSS Studio
                </span>
                <div className="flex gap-1 bg-slate-900 p-1 rounded-xl border border-slate-800 text-[11px]">
                  <button
                    type="button"
                    onClick={() => setStudioType('css')}
                    className={`px-2.5 py-1 rounded-lg font-semibold cursor-pointer ${
                      studioType === 'css' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'
                    }`}
                  >
                    Custom CSS
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setStudioType('js');
                      setStudioCode(
                        `// ==UserScript==\n// @name Auto-Clean Tracking Links\n// @match *://*/*\n// ==/UserScript==\nconsole.log('BrowserHub Userscript active!');`
                      );
                    }}
                    className={`px-2.5 py-1 rounded-lg font-semibold cursor-pointer ${
                      studioType === 'js' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'
                    }`}
                  >
                    Userscript (.js)
                  </button>
                </div>
              </div>

              <input
                value={studioName}
                onChange={(e) => setStudioName(e.target.value)}
                placeholder="Extension / Script Name"
                className="w-full rounded-xl bg-slate-900 border border-slate-800 px-3 py-2 text-xs text-white"
              />

              <textarea
                rows={6}
                value={studioCode}
                onChange={(e) => setStudioCode(e.target.value)}
                className="w-full rounded-xl bg-slate-950 border border-slate-800 p-3 text-xs font-mono text-emerald-300 focus:outline-none focus:border-emerald-500"
              />

              <button
                type="submit"
                className="w-full py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
              >
                Compile &amp; Inject Into Browser
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
