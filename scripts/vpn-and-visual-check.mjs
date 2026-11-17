#!/usr/bin/env node
/**
 * Guards two reports from real-device testing:
 *
 *   1. "VPN is connected but my location is still showing the real one"
 *      — correct behaviour, because this build has no remote exit servers, but
 *        the UI called it a VPN and marked nodes as selectable locations. That
 *        is the lie. A local filter must say it is a local filter, show the
 *        user's real exit IP, and never imply a location change.
 *
 *   2. "The UI is too much simple, it's feeling bored"
 *      — the start screen was flat rectangles on black. The AMOLED contract is
 *        still mandatory, so depth has to come from light and motion, not from
 *        lighter panels. This check requires those layers to exist.
 *
 * Run: node scripts/vpn-and-visual-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const problems = [];

const vpnModal = read('src/components/VpnModal.tsx');
const presets = read('src/data/presets.ts');
const css = read('src/index.css');
const viewport = read('src/components/WebViewport.tsx');
const vpnService = read('android/standalone/src/com/browserhub/app/vpn/HubVpnService.java');

/* ------------------------------- VPN truth -------------------------------- */

// The panel must name itself honestly.
if (!/NOT A LOCATION VPN/i.test(vpnModal)) {
  problems.push('VpnModal.tsx: does not state that this is not a location-changing VPN');
}
if (!/does not change your IP|stays that way|location do not change/i.test(vpnModal)) {
  problems.push('VpnModal.tsx: does not tell the user their IP/location is unchanged');
}

// The user must be able to verify it themselves, on screen.
if (!/Your real public exit/i.test(vpnModal)) {
  problems.push('VpnModal.tsx: no on-screen readout of the real exit IP, so "it did not change" is unverifiable');
}
if (!/ipapi\.co|ipwho\.is|ipify/.test(vpnModal)) {
  problems.push('VpnModal.tsx: the exit readout has no working source endpoint');
}

// Node entries are placeholders, and the data file must say so.
if (!/NOT servers this build connects to|no remote exit infrastructure/i.test(presets)) {
  problems.push('presets.ts: the node list no longer documents that it is a preview list');
}

// The native service must not claim to route traffic it does not route.
if (!/addRoute/.test(vpnService)) {
  problems.push('HubVpnService.java: tunnel builder lost its route configuration');
}
// A route limited to the tunnel's own subnet carries no user traffic — that is
// exactly why the IP does not change, and it must stay documented.
if (!/does not carry|local (filter|blocking)|no user traffic|0\.0\.0\.0\/0/i.test(vpnService)) {
  problems.push(
    'HubVpnService.java: must document that this tunnel filters locally and does not carry user traffic'
  );
}

/* ------------------------------- visual depth ----------------------------- */

for (const cls of ['bh-aurora', 'bh-card', 'bh-gradient-text', 'bh-rise', 'bh-grid-veil']) {
  if (!css.includes(`.${cls}`)) problems.push(`index.css: missing .${cls} (the depth layer that fixes "boring")`);
}
for (const cls of ['bh-aurora', 'bh-card']) {
  if (!viewport.includes(cls)) problems.push(`WebViewport.tsx: start page does not use .${cls}`);
}

// Reduced-motion must still be honoured — animation is decoration, not function.
if (!/prefers-reduced-motion: reduce[\s\S]{0,400}bh-aurora/.test(css)) {
  problems.push('index.css: the new motion is not disabled under prefers-reduced-motion');
}

// The AMOLED rule still wins: no light panel may be introduced for depth.
if (/bg-white|#ffffff|#f8fafc/i.test(viewport)) {
  problems.push('WebViewport.tsx: a light surface was added — depth must come from light, not a white panel');
}

/* ------------------------- no fake feature card --------------------------- */

// Strip comments first: a comment explaining the old bug is not the bug.
const viewportCode = viewport
  .split('\n')
  .filter((l) => !l.trim().startsWith('{/*') && !l.trim().startsWith('*') && !l.trim().startsWith('//'))
  .join('\n');
if (/example\.com/.test(viewportCode)) {
  problems.push('WebViewport.tsx: a card still points at example.com (fake download)');
}

if (problems.length) {
  console.error(`❌ ${problems.length} VPN/visual violation(s):\n`);
  for (const p of problems) console.error(`  ${p}`);
  process.exit(1);
}

console.log('shield is labelled honestly with a live exit readout, and the UI has real visual depth');
