// Fails if a preference group is rendered/editable but never restored from
// storage, or if settings are never written at all — the exact class of bug
// that makes the app "forget" the chosen search engine after a restart.
import fs from 'node:fs';

const app = fs.readFileSync('src/App.tsx', 'utf8');
const bad = [];

const groups = ['customization', 'shields', 'vpn', 'extensions'];
for (const key of groups) {
  const restored = app.includes(`withStored<`) && app.includes(`'${key}'`);
  const viaStored = app.includes(`STORED.${key}`);
  if (!restored && !viaStored) bad.push(`App.tsx: ${key} is not restored from storage`);
}
if (!/localStorage\.setItem\(\s*STORE_KEY/.test(app)) bad.push('App.tsx: settings are never written');
if (!/return \(\) => window\.clearTimeout\(id\)/.test(app)) bad.push('App.tsx: write debounce not cleaned up');

if (bad.length) {
  bad.forEach((b) => console.error(`  FAIL ${b}`));
  process.exit(1);
}
console.log('  all preference groups are read back and written');
