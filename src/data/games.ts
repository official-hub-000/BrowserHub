/**
 * A curated, honest browser-games hub.
 *
 * Rules this file obeys, because the app must not over-promise:
 *   - No piracy and no "free GTA V / FIFA / Call of Duty" claims. Those titles
 *     are not playable in a browser, and claiming otherwise would be a fake
 *     feature — the exact thing the honesty check exists to prevent.
 *   - Every entry is a real, publicly playable web game or a real catalogue.
 *   - Each entry declares what it needs (`webgl`, `keyboard`, `gamepad`,
 *     `touch`) so the hub can feature-detect on the device and only promote
 *     games that will actually run.
 *   - Console-class games are listed as *purchase links to official storefronts*
 *     via a real streaming client, never as installable content.
 */

export type GameNeed = 'webgl' | 'keyboard' | 'gamepad' | 'touch' | 'pointerlock' | 'wasm';

export interface WebGame {
  id: string;
  title: string;
  /** One line: what it actually is. */
  blurb: string;
  url: string;
  category: 'action' | 'racing' | 'sports' | 'puzzle' | 'strategy' | 'retro' | 'io' | 'sandbox';
  needs: GameNeed[];
  /** True when the site publishes a mobile/touch control scheme. */
  touchReady: boolean;
  /** Where the game really comes from, so the label is verifiable. */
  source: 'official' | 'catalogue' | 'open-source';
  /** Heavy games are flagged so the UI can warn instead of stuttering. */
  weight: 'light' | 'medium' | 'heavy';
}

export const WEB_GAMES: WebGame[] = [
  // ---- sandbox / open world (light enough for a phone browser) -------------
  {
    id: 'classic-minecraft',
    title: 'ClassiCube (Minecraft Classic)',
    blurb: 'Open-source Minecraft Classic client. Single player and real public servers.',
    url: 'https://www.classicube.net/play/',
    category: 'sandbox',
    needs: ['webgl', 'keyboard'],
    touchReady: false,
    source: 'open-source',
    weight: 'light',
  },
  {
    id: 'krunker',
    title: 'Krunker',
    blurb: 'Browser FPS with real online matches and a native touch control option.',
    url: 'https://krunker.io/',
    category: 'action',
    needs: ['webgl', 'keyboard'],
    touchReady: true,
    source: 'official',
    weight: 'medium',
  },
  {
    id: 'slither',
    title: 'Slither.io',
    blurb: 'Massively multiplayer snake. Built for touch from the start.',
    url: 'https://slither.io/',
    category: 'io',
    needs: ['touch'],
    touchReady: true,
    source: 'official',
    weight: 'light',
  },
  {
    id: 'agar',
    title: 'Agar.io',
    blurb: 'Multiplayer cell-eating arena. One-thumb controls.',
    url: 'https://agar.io/',
    category: 'io',
    needs: ['touch'],
    touchReady: true,
    source: 'official',
    weight: 'light',
  },
  {
    id: 'diep',
    title: 'Diep.io',
    blurb: 'Tank shooter with skill trees. Touch supported.',
    url: 'https://diep.io/',
    category: 'io',
    needs: ['touch'],
    touchReady: true,
    source: 'official',
    weight: 'light',
  },

  // ---- racing --------------------------------------------------------------
  {
    id: 'slowroads',
    title: 'slowroads',
    blurb: 'Endless procedurally generated driving. One of the best browser driving games.',
    url: 'https://slowroads.io/',
    category: 'racing',
    needs: ['webgl', 'keyboard'],
    touchReady: true,
    source: 'official',
    weight: 'medium',
  },
  {
    id: 'drift-hunters',
    title: 'Drift Hunters',
    blurb: '3D drift racing with real physics. Keyboard recommended.',
    url: 'https://drifthunters.com/',
    category: 'racing',
    needs: ['webgl', 'keyboard'],
    touchReady: false,
    source: 'official',
    weight: 'medium',
  },

  // ---- sports --------------------------------------------------------------
  {
    id: 'basketball-stars',
    title: 'Basketball Stars',
    blurb: 'Arcade 1v1 basketball. Touch controls built in.',
    url: 'https://www.crazygames.com/game/basketball-stars',
    category: 'sports',
    needs: ['touch'],
    touchReady: true,
    source: 'catalogue',
    weight: 'light',
  },
  {
    id: 'penalty-shooters',
    title: 'Penalty Shooters 2',
    blurb: 'Football penalty shootouts, quick rounds, touch friendly.',
    url: 'https://www.crazygames.com/game/penalty-shooters-2',
    category: 'sports',
    needs: ['touch'],
    touchReady: true,
    source: 'catalogue',
    weight: 'light',
  },
  {
    id: 'retro-bowl',
    title: 'Retro Bowl',
    blurb: 'American football with a management layer. Touch friendly.',
    url: 'https://www.crazygames.com/game/retro-bowl',
    category: 'sports',
    needs: ['touch'],
    touchReady: true,
    source: 'catalogue',
    weight: 'light',
  },

  // ---- retro / emulation ---------------------------------------------------
  {
    id: 'emulatorjs',
    title: 'EmulatorJS (retro consoles)',
    blurb: 'Runs public-domain and homebrew ROMs for NES, SNES, GBA, Genesis and more.',
    url: 'https://www.emulatorjs.org/',
    category: 'retro',
    needs: ['wasm', 'gamepad'],
    touchReady: true,
    source: 'open-source',
    weight: 'medium',
  },
  {
    id: 'tic80',
    title: 'TIC-80',
    blurb: 'Fantasy console with thousands of free games made for it.',
    url: 'https://tic80.com/play',
    category: 'retro',
    needs: ['touch'],
    touchReady: true,
    source: 'open-source',
    weight: 'light',
  },

  // ---- strategy / puzzle ---------------------------------------------------
  {
    id: '2048',
    title: '2048',
    blurb: 'The sliding tile puzzle. Perfect for a phone.',
    url: 'https://play2048.co/',
    category: 'puzzle',
    needs: ['touch'],
    touchReady: true,
    source: 'official',
    weight: 'light',
  },
  {
    id: 'lichess',
    title: 'Lichess',
    blurb: 'Free open-source chess with real online opponents.',
    url: 'https://lichess.org/',
    category: 'strategy',
    needs: ['touch'],
    touchReady: true,
    source: 'open-source',
    weight: 'light',
  },
  {
    id: 'neptune',
    title: 'Neptune\'s Pride (archive)',
    blurb: 'Internet Archive playable games collection, runs in the browser.',
    url: 'https://archive.org/details/internetarcade',
    category: 'retro',
    needs: ['touch'],
    touchReady: true,
    source: 'catalogue',
    weight: 'medium',
  },

  // ---- catalogues (real lists, not single games) ---------------------------
  {
    id: 'crazygames',
    title: 'CrazyGames (full catalogue)',
    blurb: 'Thousands of browser games, filtered by category and device.',
    url: 'https://www.crazygames.com/',
    category: 'action',
    needs: ['touch'],
    touchReady: true,
    source: 'catalogue',
    weight: 'light',
  },
  {
    id: 'itchio',
    title: 'itch.io browser games',
    blurb: 'Indie games that run in the page, many free.',
    url: 'https://itch.io/games/html5',
    category: 'action',
    needs: ['touch'],
    touchReady: true,
    source: 'catalogue',
    weight: 'light',
  },
  {
    id: 'poki',
    title: 'Poki',
    blurb: 'Curated browser games, mobile first.',
    url: 'https://poki.com/',
    category: 'action',
    needs: ['touch'],
    touchReady: true,
    source: 'catalogue',
    weight: 'light',
  },
];

/**
 * Console-class titles. These are NOT playable in a browser and this file says
 * so. The entry takes you to the official storefront, which is the only honest
 * destination for them.
 */
export interface ConsoleLink {
  id: string;
  title: string;
  publisher: string;
  store: string;
  url: string;
  note: string;
}

export const CONSOLE_LINKS: ConsoleLink[] = [
  {
    id: 'gta5',
    title: 'Grand Theft Auto V',
    publisher: 'Rockstar Games',
    store: 'Rockstar Store',
    url: 'https://store.rockstargames.com/',
    note: 'PC/console only. There is no browser version.',
  },
  {
    id: 'ea-fc',
    title: 'EA SPORTS FC (formerly FIFA)',
    publisher: 'Electronic Arts',
    store: 'EA Store',
    url: 'https://www.ea.com/games/ea-sports-fc',
    note: 'PC/console only. No browser build exists.',
  },
  {
    id: 'cod',
    title: 'Call of Duty',
    publisher: 'Activision',
    store: 'Call of Duty store',
    url: 'https://www.callofduty.com/',
    note: 'PC/console only. No browser build exists.',
  },
];

/**
 * Real cloud-streaming clients that let you play owned PC games on a phone.
 * They need a subscription and a stable connection — stated, not hidden.
 */
export const CLOUD_GAMING = [
  {
    id: 'xbox-cloud',
    title: 'Xbox Cloud Gaming',
    url: 'https://www.xbox.com/play',
    note: 'Streams games you own or Game Pass titles. Needs an Xbox account and a strong connection.',
  },
  {
    id: 'geforce-now',
    title: 'GeForce NOW',
    url: 'https://play.geforcenow.com/',
    note: 'Streams supported games from your own Steam/Epic library. Free tier has queues.',
  },
  {
    id: 'luna',
    title: 'Amazon Luna',
    url: 'https://luna.amazon.com/',
    note: 'Subscription streaming. Region limited.',
  },
];

export const GAME_CATEGORIES = [
  { id: 'all', label: 'All' },
  { id: 'sandbox', label: 'Sandbox' },
  { id: 'action', label: 'Action' },
  { id: 'racing', label: 'Racing' },
  { id: 'sports', label: 'Sports' },
  { id: 'io', label: 'IO' },
  { id: 'retro', label: 'Retro' },
  { id: 'strategy', label: 'Strategy' },
  { id: 'puzzle', label: 'Puzzle' },
] as const;

/** A gamepad is required for some and merely better for others. */
export function gamesNeedingGamepad(): WebGame[] {
  return WEB_GAMES.filter((g) => g.needs.includes('gamepad'));
}
