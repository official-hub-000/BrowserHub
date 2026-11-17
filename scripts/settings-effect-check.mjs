// Catches "decorative settings": a preference that is rendered in the settings
// sheet and persisted, but never read by any UI outside that sheet. Those are
// the controls users report as "tap does nothing".
import fs from 'node:fs';
import path from 'node:path';

const types = fs.readFileSync('src/types/browser.ts', 'utf8');
const iface = types.slice(types.indexOf('export interface BrowserCustomization'));
const body = iface.slice(0, iface.indexOf('}'));
const fields = [...body.matchAll(/^\s{2}(\w+)\??:/gm)].map((m) => m[1]);

const consumers = [];
const walk = (dir) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p);
    else if (/\.(tsx?|ts)$/.test(entry.name)) consumers.push(p);
  }
};
walk('src');

const bad = [];
for (const field of fields) {
  const used = consumers.some((file) => {
    if (file.endsWith('CustomizeDrawer.tsx') || file.endsWith('types/browser.ts') || file.endsWith('App.tsx')) {
      // App.tsx implements persistence for every field, so ignore the writer;
      // look for real consumers only in other components.
      return false;
    }
    const src = fs.readFileSync(file, 'utf8');
    return new RegExp(`customization\\.${field}\\b`).test(src);
  });
  // Allow fields that App.tsx itself renders/applies, either directly or via an
  // inline CSS custom property map (customAccentHex -> --bh-accent).
  const app = fs.readFileSync('src/App.tsx', 'utf8');
  const RENDER_MAP = { customAccentHex: '--bh-accent' };
  const appliedInApp = RENDER_MAP[field]
    ? app.includes(RENDER_MAP[field])
    : new RegExp(`customization\\.${field}\\b`).test(
        app.replace(/const STORE_KEY[\s\S]*?const STORED = readStoredPrefs\(\);/, '')
      );
  if (!used && !appliedInApp) bad.push(field);
}

if (bad.length) {
  bad.forEach((f) => console.error(`  FAIL customization.${f} is editable but changes nothing`));
  process.exit(1);
}
console.log(`  all ${fields.length} customization settings affect the UI`);
