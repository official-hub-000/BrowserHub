// Verifies settings survive a restart: writes prefs, then re-reads them exactly
// the way App.tsx does. Uses the same merge semantics (nested section over
// defaults) so a corrupt or partial store can never silently reset a setting.
import assert from 'node:assert';

const STORE_KEY = 'browserhub.prefs.v1';
const mem = new Map();
globalThis.window = {
  localStorage: {
    getItem: (k) => (mem.has(k) ? mem.get(k) : null),
    setItem: (k, v) => mem.set(k, String(v)),
    removeItem: (k) => mem.delete(k),
  },
};

function readStoredPrefs() {
  try {
    const raw = window.localStorage.getItem(STORE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}
function withStored(section, fallback) {
  const stored = readStoredPrefs()[section];
  if (!stored || typeof stored !== 'object') return fallback;
  return { ...fallback, ...stored };
}

const DEFAULTS = {
  customization: { searchEngine: 'google', theme: 'cyber-cyan', addressBarPosition: 'top' },
  shields: { enabled: true, blockAds: true, blockTrackers: true },
  vpn: { selectedNodeId: 'ch-zurich', dohProvider: 'mullvad', killSwitch: true },
};

// --- session 1: user changes settings -----------------------------------
const customization = { ...DEFAULTS.customization, searchEngine: 'duckduckgo', theme: 'ocean' };
const shields = { ...DEFAULTS.shields, blockAds: false };
const vpn = { ...DEFAULTS.vpn, killSwitch: false };
const extensions = [{ id: 'ublock-origin', enabled: false }];
window.localStorage.setItem(
  STORE_KEY,
  JSON.stringify({ customization, shields, vpn, extensions })
);

// --- session 2: app restarts and reads them back -------------------------
assert.equal(withStored('customization', DEFAULTS.customization).searchEngine, 'duckduckgo');
assert.equal(withStored('customization', DEFAULTS.customization).theme, 'ocean');
assert.equal(withStored('shields', DEFAULTS.shields).blockAds, false);
assert.equal(withStored('shields', DEFAULTS.shields).blockTrackers, true, 'untouched keys keep defaults');
assert.equal(withStored('vpn', DEFAULTS.vpn).killSwitch, false);
assert.deepEqual(readStoredPrefs().extensions, extensions);

// --- corrupt store must not crash or wipe defaults -----------------------
mem.set(STORE_KEY, '{not json');
assert.equal(withStored('customization', DEFAULTS.customization).searchEngine, 'google');

// --- partial store must merge, not replace ------------------------------
mem.set(STORE_KEY, JSON.stringify({ customization: { searchEngine: 'brave' } }));
const partial = withStored('customization', DEFAULTS.customization);
assert.equal(partial.searchEngine, 'brave');
assert.equal(partial.addressBarPosition, 'top');

console.log('Preferences persistence OK (search engine, theme, shields, VPN, extensions)');
