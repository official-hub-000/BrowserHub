// Fails if the UI advertises a capability the build does not have. Real-device
// testers treat a fake-success control as a bug, so these strings must stay out.
import fs from 'node:fs';
import path from 'node:path';

const files = [];
const walk = (dir) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p);
    else if (/\.tsx?$/.test(entry.name)) files.push(p);
  }
};
walk('src');
// The README is user-facing documentation and must stay honest too.
const readme = fs.readFileSync('README.md', 'utf8');

// claim -> why it is not true in this build
const FORBIDDEN = [
  { re: /GeckoView \d+/, why: 'this build has exactly one Chromium WebView engine' },
  { re: /Blink \d+ \+|Chrome \d+ \+ Gecko/, why: 'no second engine exists' },
  { re: /WireGuard • Multi-Hop • Tor/, why: 'remote tunnelling is not wired' },
  { re: /ENCRYPTED & MASKED/, why: 'traffic is not routed through an exit node' },
  { re: /Exposed Carrier IP/, why: 'the app does not read or mask your public IP' },
  { re: /Node\.js \d+ \(Server\)/, why: 'no Node runtime ships in the APK' },
];

const hits = [];
for (const file of files) {
  const src = fs.readFileSync(file, 'utf8');
  for (const rule of FORBIDDEN) {
    if (rule.re.test(src)) hits.push(`${file}: ${rule.re} — ${rule.why}`);
  }
}
for (const rule of FORBIDDEN) {
  if (rule.re.test(readme)) hits.push(`README.md: ${rule.re} — ${rule.why}`);
}
if (hits.length) {
  hits.forEach((h) => console.error(`  FAIL ${h}`));
  process.exit(1);
}
console.log('  no fake engine / VPN / runtime claims in the UI');
