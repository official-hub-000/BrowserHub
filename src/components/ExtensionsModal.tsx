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
  Search,
  AlertTriangle,
  ExternalLink,
} from 'lucide-react';
import type { BrowserExtension } from '../types/browser';
import {
  EXTENSION_CATALOGUE as CATALOGUE,
  CATALOGUE_GROUPS,
  SUPPORT_BADGE,
  STORE_FRONTS,
} from '../data/extensionCatalogue';

import { parseGlobalStoreUrl, unpackExtensionFile } from '../utils/extensionUnpacker';
import { SiteIcon } from './VectorIcons';
import { isExtensionEnforced } from '../utils/extensionEffects';

interface ExtensionsModalProps {
  extensions: BrowserExtension[];
  onNavigate: (url: string) => void;
  onToggleExtension: (id: string) => void;
  onToggleIncognitoExt: (id: string) => void;
  onInstallExtension: (ext: BrowserExtension) => void;
  onRemoveExtension: (id: string) => void;
  onClose: () => void;
}

export const ExtensionsModal: React.FC<ExtensionsModalProps> = ({
  extensions,
  onNavigate,
  onToggleExtension,
  onToggleIncognitoExt,
  onInstallExtension,
  onRemoveExtension,
  onClose,
}) => {
  const [activeTab, setActiveTab] = useState<
    'installed' | 'store' | 'browse' | 'import' | 'studio'
  >('installed');
  const [storeQuery, setStoreQuery] = useState('');
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
    const input = storeUrlInput.trim();
    if (!input) return;

    // Opening a store page and installing an archive are different actions — be honest
    // about which one happens instead of dropping the user into a fake "installed" state.
    const isStorePage =
      /^https?:\/\//i.test(input) &&
      /(chromewebstore\.google\.com|addons\.mozilla\.org|greasyfork\.org|github\.com|openuserjs\.org)/i.test(input);

    if (isStorePage) {
      onClose();
      onNavigate(input);
      return;
    }

    const ext = parseGlobalStoreUrl(input);

    /*
     * A .crx/.xpi record cannot execute in Android WebView. Installing it put a
     * card in the list that changed nothing — exactly "I can't add any of them /
     * they don't work". A userscript is different: its code genuinely runs, so
     * only that case is added outright.
     */
    if (ext.effectKey === 'custom-script') {
      onInstallExtension(ext);
      setStoreUrlInput('');
      flashNotice(`Added "${ext.name}" — edit its code in the Studio tab`);
      setActiveTab('installed');
      return;
    }

    setStoreUrlInput('');
    flashNotice(
      `${ext.name} was read as a ${ext.format} package. Android WebView cannot run its code, so it was not added. ` +
        `See the Working tab for native equivalents, or paste a userscript.`
    );
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
      icon: studioType === 'css' ? 'css' : 'userscript',
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
    <div className="bh-overlay fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-3" onClick={onClose}>
      <div className="bh-dialog w-full max-w-2xl rounded-3xl bg-black border border-slate-800 shadow-2xl flex flex-col max-h-[90vh] overflow-hidden" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-gradient-to-r from-[#0b0713] to-black">
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
          <button aria-label="Close" title="Close" onClick={onClose} className="p-1.5 rounded-xl hover:bg-[#121216] text-slate-400 cursor-pointer">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="grid grid-cols-5 gap-1 p-2 bg-[#060608] border-b border-slate-800 text-[10px]">
          {[
            { id: 'installed', label: `Installed (${extensions.length})` },
            { id: 'store', label: 'Working' },
            { id: 'browse', label: 'Stores' },
            { id: 'import', label: 'Import' },
            { id: 'studio', label: 'Studio' },
          ].map((t) => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id as typeof activeTab)}
              className={`py-2 px-2 rounded-xl font-semibold transition cursor-pointer ${
                activeTab === t.id
                  ? 'bg-purple-500 text-white'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-[#121216]'
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
                className="rounded-2xl bg-[#08080b] border border-slate-800 p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="flex items-start gap-3 min-w-0">
                  <div className="w-8 h-8 rounded-lg bg-[#121216] border border-slate-700 flex items-center justify-center shrink-0 mt-0.5"><SiteIcon id={ext.icon} size={18} /></div>
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-xs font-bold text-white">{ext.name}</span>
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-[#121216] text-cyan-300 font-mono">
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
                      : 'bg-[#121216] text-slate-400'
                  }`}
                >
                  {ext.enabled ? 'ON' : 'OFF'}
                </button>
              </div>
            ))}

          {activeTab === 'store' && (
            <div className="space-y-3">
              <div className="rounded-xl border border-slate-800 bg-[#060608] px-3 py-2 text-[10px] leading-relaxed text-slate-400">
                Every card states whether the extension <strong className="text-emerald-300">actually works</strong> in
                this build. Android WebView has no extension engine — no Chrome, Edge or Firefox add-on can execute its
                code inside it. So BrowserHub ships native equivalents for the ones it can honour, and labels the rest
                honestly instead of installing something that does nothing.
              </div>

              <div className="flex items-center gap-2 rounded-xl border border-slate-800 bg-[#08080b] px-3 py-2">
                <Search size={14} className="text-slate-500 shrink-0" />
                <input
                  value={storeQuery}
                  onChange={(e) => setStoreQuery(e.target.value)}
                  placeholder="Search the catalogue"
                  className="flex-1 bg-transparent outline-none text-[12px] text-white placeholder:text-slate-500"
                />
                {storeQuery ? (
                  <button type="button" onClick={() => setStoreQuery('')} className="text-[10px] text-slate-400 cursor-pointer">
                    Clear
                  </button>
                ) : null}
              </div>

              {CATALOGUE_GROUPS.map((group) => {
                const items = CATALOGUE.filter(
                  (e) =>
                    e.support === group.id &&
                    (!storeQuery.trim() ||
                      e.name.toLowerCase().includes(storeQuery.trim().toLowerCase()) ||
                      e.description.toLowerCase().includes(storeQuery.trim().toLowerCase()))
                );
                if (items.length === 0) return null;
                const badge = SUPPORT_BADGE[group.id];
                return (
                  <div key={group.id} className="space-y-2">
                    <div className="flex items-center gap-2 pt-1">
                      <span
                        className={`text-[9px] px-1.5 py-0.5 rounded font-bold ${
                          badge.tone === 'green'
                            ? 'bg-emerald-500/20 text-emerald-300'
                            : badge.tone === 'amber'
                              ? 'bg-amber-500/20 text-amber-300'
                              : 'bg-[#121216] text-slate-400'
                        }`}
                      >
                        {badge.label}
                      </span>
                      <span className="text-[10px] text-slate-500">{group.hint}</span>
                    </div>

                    {items.map((item) => {
                      const alreadyInstalled = extensions.some((e) => e.id === item.id);
                      const works = item.support === 'runs-here';
                      return (
                        <div
                          key={item.id}
                          className="rounded-2xl bg-[#08080b] border border-slate-800 p-3 space-y-2"
                        >
                          <div className="flex items-start gap-2.5">
                            <div className="w-9 h-9 rounded-xl bg-[#121216] border border-slate-700 flex items-center justify-center shrink-0">
                              <SiteIcon id={item.icon} size={18} />
                            </div>
                            <div className="min-w-0 flex-1">
                              <div className="text-[12px] font-bold text-white flex items-center gap-1.5 flex-wrap">
                                <span>{item.name}</span>
                                <span className="text-[9px] px-1.5 py-0.5 rounded bg-[#121216] text-purple-300">
                                  {item.format}
                                </span>
                              </div>
                              <p className="text-[10px] text-slate-400 mt-0.5 leading-relaxed">{item.description}</p>
                              <div className="text-[9px] text-amber-300 mt-0.5">
                                {item.rating} · {item.usersCount} users
                              </div>
                            </div>
                          </div>

                          <div
                            className={`flex items-start gap-1.5 text-[10px] leading-relaxed rounded-lg px-2 py-1.5 ${
                              works ? 'bg-emerald-500/10 text-emerald-200' : 'bg-[#0d0a05] text-amber-200'
                            }`}
                          >
                            {works ? (
                              <CheckCircle2 size={10} className="mt-0.5 shrink-0 text-emerald-400" />
                            ) : (
                              <AlertTriangle size={10} className="mt-0.5 shrink-0 text-amber-400" />
                            )}
                            <span>
                              {works ? 'Working: ' : 'Not working here: '}
                              {item.works}
                            </span>
                          </div>

                          <div className="flex items-center gap-2">
                            {works ? (
                              <button
                                type="button"
                                disabled={alreadyInstalled}
                                title={alreadyInstalled ? 'Already installed' : `Add ${item.name}`}
                                aria-label={alreadyInstalled ? 'Already installed' : `Add ${item.name}`}
                                onClick={() => {
                                  onInstallExtension({ ...item, enabled: true, allowIncognito: true });
                                  flashNotice(`Added "${item.name}"`);
                                }}
                                className={`px-3 py-1.5 rounded-xl text-[11px] font-bold ${
                                  alreadyInstalled
                                    ? 'bg-[#121216] text-slate-500 cursor-default'
                                    : 'bg-purple-500 hover:bg-purple-400 text-white cursor-pointer'
                                }`}
                              >
                                {alreadyInstalled ? 'Installed' : '+ Install'}
                              </button>
                            ) : (
                              <button
                                type="button"
                                disabled={!item.storeUrl}
                                title={
                                  item.storeUrl
                                    ? `Open the official listing for ${item.name} (cannot be installed into WebView)`
                                    : 'No official listing'
                                }
                                aria-label={`Open the official listing for ${item.name}`}
                                onClick={() => item.storeUrl && onNavigate(item.storeUrl)}
                                className={`px-3 py-1.5 rounded-xl text-[11px] font-bold border flex items-center gap-1.5 ${
                                  item.storeUrl
                                    ? 'bg-black border-slate-700 text-slate-200 cursor-pointer'
                                    : 'bg-[#121216] border-slate-800 text-slate-600 cursor-not-allowed'
                                }`}
                              >
                                <ExternalLink size={10} />
                                <span>Official listing</span>
                              </button>
                            )}
                            {!works && item.support === 'importable' ? (
                              <button
                                type="button"
                                onClick={() => {
                                  onInstallExtension({ ...item, enabled: true, allowIncognito: true });
                                  flashNotice(`Imported "${item.name}" (inactive — cannot execute here)`);
                                }}
                                disabled={alreadyInstalled}
                                title="Import the package record for inspection. Its scripts will not run."
                                aria-label={`Import ${item.name} for inspection`}
                                className={`px-2.5 py-1.5 rounded-xl text-[11px] font-semibold border ${
                                  alreadyInstalled
                                    ? 'bg-[#121216] border-slate-800 text-slate-600 cursor-default'
                                    : 'bg-black border-slate-700 text-slate-300 cursor-pointer'
                                }`}
                              >
                                {alreadyInstalled ? 'Imported' : 'Import'}
                              </button>
                            ) : null}
                            <span className="text-[9px] text-slate-600 ml-auto uppercase">{item.source}</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                );
              })}

              <div className="rounded-xl border border-slate-800 bg-[#060608] p-3 space-y-1.5">
                <div className="text-[11px] font-bold text-slate-200">Have a store link?</div>
                <p className="text-[10px] leading-relaxed text-slate-400">
                  Paste any Chrome Web Store, Edge Add-ons, Firefox Add-ons, Greasy Fork or
                  OpenUserJS link in the <strong className="text-slate-200">Import</strong> tab. It opens the real
                  listing, and any CSS or userscript part can be applied — the extension sandbox itself cannot run in
                  WebView.
                </p>
              </div>
            </div>
          )}

          {activeTab === 'browse' && (
            <div className="space-y-3">
              <div className="rounded-xl border border-slate-800 bg-[#060608] px-3 py-2 text-[10px] leading-relaxed text-slate-400">
                These open the <strong className="text-slate-200">real</strong> extension stores in a
                tab, so you can search them like on a desktop. You can read listings, reviews and
                documentation and download files. What you cannot do is make Chrome, Edge or Firefox
                extension code run inside Android WebView — that engine does not exist here. Tap a
                card to browse; tap the download in the Import tab to keep the file.
              </div>

              {STORE_FRONTS.map((store) => (
                <div
                  key={store.id}
                  className="rounded-2xl border border-slate-800 bg-[#08080b] p-3 space-y-2"
                >
                  <div className="flex items-start gap-2.5">
                    <div className="w-9 h-9 rounded-xl bg-[#121216] border border-slate-700 flex items-center justify-center shrink-0">
                      <SiteIcon id={store.icon} size={18} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-[12px] font-bold text-white flex items-center gap-1.5 flex-wrap">
                        <span>{store.name}</span>
                        <span className="text-[9px] px-1.5 py-0.5 rounded bg-[#121216] text-slate-400">
                          {store.count}
                        </span>
                      </div>
                      <p className="text-[10px] text-slate-400 mt-0.5 leading-relaxed">
                        {store.blurb}
                      </p>
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-1.5">
                    <button
                      type="button"
                      onClick={() => onNavigate(store.url)}
                      title={`Browse ${store.name}`}
                      aria-label={`Browse ${store.name}`}
                      className="px-2.5 py-1.5 rounded-xl text-[10px] font-bold bg-purple-500/20 border border-purple-400/60 text-white cursor-pointer flex items-center gap-1"
                    >
                      <ExternalLink size={9} />
                      <span>Browse</span>
                    </button>
                    {store.categories.slice(0, 3).map((cat) => (
                      <button
                        key={cat.label}
                        type="button"
                        onClick={() => onNavigate(cat.url)}
                        title={`${store.name}: ${cat.label}`}
                        aria-label={`${store.name} category ${cat.label}`}
                        className="px-2 py-1.5 rounded-xl text-[10px] font-semibold bg-black border border-slate-700 text-slate-300 cursor-pointer"
                      >
                        {cat.label}
                      </button>
                    ))}
                  </div>
                </div>
              ))}

              <div className="rounded-xl border border-amber-500/30 bg-[#0d0a05] p-3 space-y-1">
                <div className="text-[11px] font-bold text-amber-200 flex items-center gap-1.5">
                  <AlertTriangle size={11} className="text-amber-400" />
                  <span>What &quot;browse&quot; means here</span>
                </div>
                <p className="text-[10px] leading-relaxed text-amber-200/90">
                  It opens the genuine storefront in a tab. Installing an add-on into BrowserHub is
                  only possible for the nine native equivalents in the <strong>Working</strong> tab.
                  Every other add-on will browse fine and do nothing if installed, which is exactly
                  what the status labels prevent.
                </p>
              </div>
            </div>
          )}

          {activeTab === 'import' && (
            <div className="space-y-4">
              {/* Store URL Importer */}
              <form
                onSubmit={handleImportStoreUrl}
                className="rounded-2xl bg-black border border-slate-800 p-4 space-y-3"
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
                    className="flex-1 rounded-xl bg-black border border-slate-700 px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-400"
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
                <div className="flex gap-1 bg-black p-1 rounded-xl border border-slate-800 text-[11px]">
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
                className="w-full rounded-xl bg-black border border-slate-800 px-3 py-2 text-xs text-white"
              />

              <textarea
                rows={6}
                value={studioCode}
                onChange={(e) => setStudioCode(e.target.value)}
                className="w-full rounded-xl bg-black border border-slate-800 p-3 text-xs font-mono text-emerald-300 focus:outline-none focus:border-emerald-500"
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
