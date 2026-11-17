import React, { useEffect, useMemo, useState } from 'react';
import {
  Gamepad2,
  Play,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Info,
  Search,
  Cpu,
  ExternalLink,
  Smartphone,
} from 'lucide-react';
import {
  WEB_GAMES,
  CONSOLE_LINKS,
  CLOUD_GAMING,
  GAME_CATEGORIES,
  type WebGame,
} from '../data/games';
import {
  probeCapabilities,
  checkGameFit,
  describeCapabilities,
  type DeviceCapabilities,
} from '../utils/capabilityProbe';
import { getGamepadStatus, emulateKeysFromGamepad, type GamepadStatus } from '../utils/gamepadBridge';
import { SiteIcon } from './VectorIcons';

interface GamesHubProps {
  onNavigate: (url: string) => void;
}

type Section = 'play' | 'pad' | 'console';

export const GamesHub: React.FC<GamesHubProps> = ({ onNavigate }) => {
  const [section, setSection] = useState<Section>('play');
  const [category, setCategory] = useState<string>('all');
  const [query, setQuery] = useState('');
  const [caps, setCaps] = useState<DeviceCapabilities | null>(null);
  const [pad, setPad] = useState<GamepadStatus | null>(null);
  const [emulating, setEmulating] = useState(false);

  // Probe after mount: creating a GL context during SSR/prerender is pointless
  // and the result is device-specific anyway.
  useEffect(() => {
    setCaps(probeCapabilities());
    setPad(getGamepadStatus());
    const onConnect = () => setPad(getGamepadStatus());
    window.addEventListener('gamepadconnected', onConnect);
    window.addEventListener('gamepaddisconnected', onConnect);
    // Poll briefly: Android sometimes only reveals a pad after the first press.
    const poll = window.setInterval(() => setPad(getGamepadStatus()), 2500);
    return () => {
      window.removeEventListener('gamepadconnected', onConnect);
      window.removeEventListener('gamepaddisconnected', onConnect);
      window.clearInterval(poll);
    };
  }, []);

  // Key emulation must be torn down when leaving.
  useEffect(() => {
    if (!emulating) return undefined;
    const stop = emulateKeysFromGamepad();
    return () => stop();
  }, [emulating]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return WEB_GAMES.filter((g) => {
      if (category !== 'all' && g.category !== category) return false;
      if (!q) return true;
      return g.title.toLowerCase().includes(q) || g.blurb.toLowerCase().includes(q);
    });
  }, [category, query]);

  const fit = (game: WebGame) => checkGameFit(game.needs, game.weight);

  const chip = (active: boolean) =>
    `px-2.5 py-1 rounded-lg border text-[11px] font-semibold cursor-pointer transition-colors ${
      active
        ? 'bg-cyan-500/20 border-cyan-400 text-white'
        : 'bg-black border-slate-700 text-slate-300 hover:border-slate-500'
    }`;

  return (
    <div className="h-full w-full overflow-y-auto" style={{ backgroundColor: '#000000' }}>
      <div className="max-w-3xl mx-auto p-4 space-y-4 pb-8">
        {/* Header */}
        <div className="rounded-2xl border border-slate-800 p-4 space-y-2" style={{ backgroundColor: '#050506' }}>
          <div className="flex items-center gap-2">
            <Gamepad2 size={18} className="text-cyan-300 shrink-0" />
            <h1 className="text-base font-extrabold text-white">Games</h1>
          </div>
          <p className="text-[11px] leading-relaxed text-slate-400">
            Browser games that really run in a page. Each entry is checked against this
            device&apos;s graphics support before it is offered, and console titles are linked
            to their official storefronts — there is no browser version of them, and this
            page will not pretend otherwise.
          </p>
          {caps ? (
            <div className="flex items-start gap-1.5 text-[10px] text-slate-500 pt-1">
              <Cpu size={10} className="mt-0.5 shrink-0" />
              <span>
                This device: {describeCapabilities(caps)} · tier <strong className="text-slate-300">{caps.tier}</strong>
              </span>
            </div>
          ) : null}
        </div>

        {/* Sections */}
        <div className="flex gap-1.5 flex-wrap">
          <button type="button" onClick={() => setSection('play')} className={chip(section === 'play')}>
            Play now
          </button>
          <button type="button" onClick={() => setSection('pad')} className={chip(section === 'pad')}>
            Gamepad
          </button>
          <button type="button" onClick={() => setSection('console')} className={chip(section === 'console')}>
            Console & cloud
          </button>
        </div>

        {section === 'play' && (
          <>
            <div className="flex items-center gap-2 rounded-xl border border-slate-800 px-3 py-2" style={{ backgroundColor: '#08080b' }}>
              <Search size={14} className="text-slate-500 shrink-0" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search games"
                className="flex-1 bg-transparent outline-none text-[12px] text-white placeholder:text-slate-500"
              />
              {query ? (
                <button type="button" onClick={() => setQuery('')} className="text-[11px] text-slate-400 cursor-pointer">
                  Clear
                </button>
              ) : null}
            </div>

            <div className="flex gap-1.5 flex-wrap">
              {GAME_CATEGORIES.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => setCategory(c.id)}
                  className={chip(category === c.id)}
                >
                  {c.label}
                </button>
              ))}
            </div>

            <div className="space-y-2">
              {visible.map((game) => {
                const f = fit(game);
                return (
                  <div
                    key={game.id}
                    className="rounded-2xl border border-slate-800 p-3 space-y-2"
                    style={{ backgroundColor: '#08080b' }}
                  >
                    <div className="flex items-start gap-2.5">
                      <div className="w-9 h-9 rounded-xl border border-slate-700 flex items-center justify-center shrink-0" style={{ backgroundColor: '#121216' }}>
                        <SiteIcon id={game.id} size={18} />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="text-[12px] font-bold text-white flex items-center gap-1.5 flex-wrap">
                          <span>{game.title}</span>
                          <span className="text-[9px] px-1.5 py-0.5 rounded bg-[#121216] text-slate-400 uppercase">
                            {game.category}
                          </span>
                          {game.touchReady ? (
                            <span className="text-[9px] px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-300 flex items-center gap-0.5">
                              <Smartphone size={8} /> touch
                            </span>
                          ) : null}
                        </div>
                        <p className="text-[11px] text-slate-400 leading-relaxed mt-0.5">{game.blurb}</p>
                      </div>
                    </div>

                    {/* Honest compatibility line */}
                    <div className="flex items-start gap-1.5 text-[10px] leading-relaxed">
                      {f.ok && !f.warning ? (
                        <>
                          <CheckCircle2 size={10} className="mt-0.5 text-emerald-400 shrink-0" />
                          <span className="text-emerald-300">Ready on this device</span>
                        </>
                      ) : f.ok && f.warning ? (
                        <>
                          <AlertTriangle size={10} className="mt-0.5 text-amber-400 shrink-0" />
                          <span className="text-amber-300">{f.warning}</span>
                        </>
                      ) : (
                        <>
                          <XCircle size={10} className="mt-0.5 text-rose-400 shrink-0" />
                          <span className="text-rose-300">
                            Needs {f.missing.join(', ')} — this device reports it as unavailable
                          </span>
                        </>
                      )}
                    </div>

                    {!game.touchReady ? (
                      <div className="flex items-start gap-1.5 text-[10px] text-slate-500 leading-relaxed">
                        <Info size={10} className="mt-0.5 shrink-0" />
                        <span>Built for keyboard — you can still open it, but it will be awkward without a pad.</span>
                      </div>
                    ) : null}

                    <div className="flex items-center gap-2 pt-0.5">
                      <button
                        type="button"
                        onClick={() => onNavigate(game.url)}
                        disabled={!f.ok}
                        title={f.ok ? `Open ${game.title}` : `Unavailable: needs ${f.missing.join(', ')}`}
                        aria-label={f.ok ? `Play ${game.title}` : `${game.title} unavailable on this device`}
                        className={`px-3 py-1.5 rounded-xl text-[11px] font-bold flex items-center gap-1.5 ${
                          f.ok
                            ? 'bg-cyan-500/20 border border-cyan-400 text-white cursor-pointer'
                            : 'bg-[#121216] border border-slate-700 text-slate-500 cursor-not-allowed'
                        }`}
                      >
                        <Play size={11} />
                        <span>Play</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => onNavigate(game.url)}
                        className="px-2.5 py-1.5 rounded-xl text-[11px] font-semibold text-slate-400 border border-slate-700 bg-black cursor-pointer flex items-center gap-1"
                      >
                        <ExternalLink size={10} />
                        <span>Details</span>
                      </button>
                      <span className="text-[9px] text-slate-600 ml-auto uppercase">{game.source}</span>
                    </div>
                  </div>
                );
              })}
              {visible.length === 0 ? (
                <div className="text-center text-[11px] text-slate-500 py-8">
                  No games match that filter.
                </div>
              ) : null}
            </div>
          </>
        )}

        {section === 'pad' && (
          <div className="space-y-3">
            <div className="rounded-2xl border border-slate-800 p-4 space-y-2" style={{ backgroundColor: '#050506' }}>
              <div className="text-[12px] font-bold text-white flex items-center gap-1.5">
                <Gamepad2 size={14} className="text-cyan-300" />
                <span>Controller status</span>
              </div>
              {pad ? (
                <>
                  <div className="flex items-center gap-2 text-[11px]">
                    {pad.mode === 'native' ? (
                      <CheckCircle2 size={12} className="text-emerald-400 shrink-0" />
                    ) : pad.mode === 'emulated' ? (
                      <AlertTriangle size={12} className="text-amber-400 shrink-0" />
                    ) : (
                      <XCircle size={12} className="text-rose-400 shrink-0" />
                    )}
                    <span className="text-slate-200 font-semibold">
                      {pad.mode === 'native'
                        ? `${pad.connected} controller${pad.connected === 1 ? '' : 's'} connected`
                        : pad.mode === 'emulated'
                          ? 'No controller visible to the page'
                          : 'Gamepad API unavailable'}
                    </span>
                  </div>
                  {pad.names.length > 0 && (
                    <ul className="text-[10px] text-slate-400 space-y-0.5 pl-5 list-disc">
                      {pad.names.map((n, i) => (
                        <li key={i}>{n}</li>
                      ))}
                    </ul>
                  )}
                  <p className="text-[10px] leading-relaxed text-slate-500">{pad.reason}</p>
                </>
              ) : (
                <div className="text-[11px] text-slate-500">Checking…</div>
              )}
            </div>

            <div className="rounded-2xl border border-slate-800 p-4 space-y-2" style={{ backgroundColor: '#050506' }}>
              <div className="text-[12px] font-bold text-white">Keyboard translation</div>
              <p className="text-[10px] leading-relaxed text-slate-400">
                For games that only read the keyboard, BrowserHub can translate pad buttons
                into key presses: A → Space, B → Esc, X → E, Y → F, D-pad → arrow keys,
                Start → Enter.
              </p>
              <button
                type="button"
                onClick={() => setEmulating((v) => !v)}
                disabled={!pad || pad.mode === 'unavailable'}
                title={
                  !pad || pad.mode === 'unavailable'
                    ? 'This browser engine has no Gamepad API'
                    : emulating
                      ? 'Stop translating pad input'
                      : 'Start translating pad input into key presses'
                }
                className={`w-full py-2 rounded-xl text-[11px] font-bold border ${
                  emulating
                    ? 'bg-emerald-500/20 border-emerald-400 text-white cursor-pointer'
                    : 'bg-[#121216] border-slate-700 text-slate-200 cursor-pointer'
                } ${!pad || pad.mode === 'unavailable' ? 'opacity-50 cursor-not-allowed' : ''}`}
              >
                {emulating ? 'Translation is ON — tap to stop' : 'Turn on pad → keyboard translation'}
              </button>
              <p className="text-[10px] leading-relaxed text-slate-500">
                Translation only works while a controller is actually connected. It runs on
                this device and sends nothing anywhere.
              </p>
            </div>

            <div className="rounded-2xl border border-slate-800 p-4 space-y-1.5" style={{ backgroundColor: '#050506' }}>
              <div className="text-[12px] font-bold text-white">Why this can be limited</div>
              <p className="text-[10px] leading-relaxed text-slate-400">
                Android WebView often does not hand raw gamepad devices to web pages. That is
                a platform limit, not a setting — no web page can override it. Games that
                support touch or on-screen controls work regardless, which is why the games
                above are labelled with what they need.
              </p>
            </div>
          </div>
        )}

        {section === 'console' && (
          <div className="space-y-3">
            <div className="rounded-2xl border border-amber-500/30 p-3 flex items-start gap-2" style={{ backgroundColor: '#0d0a05' }}>
              <Info size={12} className="text-amber-400 mt-0.5 shrink-0" />
              <p className="text-[10px] leading-relaxed text-amber-200">
                GTA V, EA SPORTS FC and Call of Duty are <strong>not playable in a browser</strong>.
                There is no web build of them, and no browser can run them locally. The links
                below go to the official stores, and to real cloud-streaming services that can
                run games you own on a phone.
              </p>
            </div>

            <div className="text-[11px] font-bold text-slate-300 pt-1">Official stores</div>
            <div className="space-y-2">
              {CONSOLE_LINKS.map((item) => (
                <div key={item.id} className="rounded-2xl border border-slate-800 p-3" style={{ backgroundColor: '#08080b' }}>
                  <div className="text-[12px] font-bold text-white">{item.title}</div>
                  <div className="text-[10px] text-slate-400 mt-0.5">
                    {item.publisher} · {item.store}
                  </div>
                  <div className="text-[10px] text-amber-300 mt-1">{item.note}</div>
                  <button
                    type="button"
                    onClick={() => onNavigate(item.url)}
                    className="mt-2 px-3 py-1.5 rounded-xl text-[11px] font-semibold border border-slate-700 bg-black text-slate-200 cursor-pointer flex items-center gap-1.5"
                  >
                    <ExternalLink size={10} />
                    <span>Open {item.store}</span>
                  </button>
                </div>
              ))}
            </div>

            <div className="text-[11px] font-bold text-slate-300 pt-2">Cloud gaming (play owned games)</div>
            <div className="space-y-2">
              {CLOUD_GAMING.map((item) => (
                <div key={item.id} className="rounded-2xl border border-slate-800 p-3" style={{ backgroundColor: '#08080b' }}>
                  <div className="text-[12px] font-bold text-white">{item.title}</div>
                  <div className="text-[10px] text-slate-400 mt-1 leading-relaxed">{item.note}</div>
                  <button
                    type="button"
                    onClick={() => onNavigate(item.url)}
                    className="mt-2 px-3 py-1.5 rounded-xl text-[11px] font-semibold border border-cyan-500/40 bg-cyan-500/10 text-cyan-200 cursor-pointer flex items-center gap-1.5"
                  >
                    <ExternalLink size={10} />
                    <span>Open {item.title}</span>
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
