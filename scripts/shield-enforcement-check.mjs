// Every switch in the Shields panel must be enforced by real code somewhere.
// A toggle that flips a boolean nothing reads is exactly the "button does
// nothing" report we are fixing.
import fs from 'node:fs';

const types = fs.readFileSync('src/types/browser.ts', 'utf8');
const iface = types.slice(types.indexOf('export interface ShieldState'));
const body = iface.slice(0, iface.indexOf('\n}'));
const flags = [...body.matchAll(/^\s{2}(\w+)\??: boolean;/gm)].map((m) => m[1]);

const panel = fs.readFileSync('src/components/ShieldsPanel.tsx', 'utf8');
const exposed = [...panel.matchAll(/key: '(\w+)'/g)].map((m) => m[1]);

const nativeJs = fs.readFileSync('src/App.tsx', 'utf8');
const java = fs.readFileSync('android/standalone/src/com/browserhub/app/MainActivity.java', 'utf8');
const bridge = fs.readFileSync('src/bridge/androidHybridBridge.ts', 'utf8');
const jsSources = [
  fs.readFileSync('src/components/WebViewport.tsx', 'utf8'),
  nativeJs,
  bridge,
].join('\n');

const bad = [];
for (const flag of exposed) {
  const inJs = new RegExp(`shields\\.${flag}\\b`).test(jsSources);
  const inNative = java.includes(flag) || bridge.includes(flag);
  if (!inJs && !inNative) bad.push(flag);
}
for (const flag of flags) {
  if (!exposed.includes(flag) && !jsSources.includes(flag)) {
    // counters are read-only telemetry, not toggles
    if (!/^total/.test(flag)) bad.push(`${flag} (declared but unused)`);
  }
}
if (bad.length) {
  bad.forEach((f) => console.error(`  FAIL shields.${f} is toggled but enforced nowhere`));
  process.exit(1);
}
console.log(`  all ${exposed.length} shield toggles are enforced`);
