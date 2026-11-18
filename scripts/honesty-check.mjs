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

/*
 * "Totally ad free" is the easiest claim in this file to make and the easiest to
 * quietly break: one analytics SDK, one mediation library, one upsell webview and
 * it stops being true. So it is enforced as a code property, not a sentence.
 *
 * The only permitted occurrences of these names are inside the blocking lists
 * that are used to REFUSE ad requests — those are checked separately below.
 */
const AD_SDK = [
  { re: /com\.google\.android\.gms\.ads/, why: 'Google Mobile Ads SDK' },
  { re: /com\.facebook\.ads/, why: 'Meta Audience Network' },
  { re: /com\.applovin/, why: 'AppLovin SDK' },
  { re: /com\.unity3d\.ads|com\.unity3d\.services\.ads/, why: 'Unity Ads' },
  { re: /com\.ironsource|com\.supersonic/, why: 'ironSource / LevelPlay' },
  { re: /com\.adcolony/, why: 'AdColony' },
  { re: /com\.chartboost/, why: 'Chartboost' },
  { re: /com\.mopub|com\.vungle|com\.inmobi|com\.startapp/, why: 'another ad network' },
  { re: /admob|adsense/i, why: 'an ad network reference outside a block list' },
];

const adFiles = [];
const walkAd = (dir) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = `${dir}/${entry.name}`;
    if (entry.isDirectory()) {
      if (['node_modules', '.git', 'dist'].includes(entry.name)) continue;
      walkAd(full);
    } else if (/\.(java|kt|tsx?|jsx?|json|gradle|xml)$/.test(entry.name)) {
      adFiles.push(full);
    }
  }
};
walkAd('android');
walkAd('src');

const adHits = [];
for (const file of adFiles) {
  // package.json / build files must not depend on an ad library either.
  const src = fs.readFileSync(file, 'utf8');
  const inBlockList = /AD_DOMAINS|blocked|BLOCK|block/i.test(src);
  for (const rule of AD_SDK) {
    if (!rule.re.test(src)) continue;
    // A blocking list naming an ad host is the opposite of shipping that SDK.
    if (inBlockList && !/com\.[a-z]+\.(ads|applovin|unity3d)/.test(src)) continue;
    adHits.push(`${file}: ${rule.why}`);
  }
}
const pkg = fs.readFileSync('package.json', 'utf8');
for (const bad of ['admob', 'applovin', 'ironsource', 'unity-ads', 'react-native-google-mobile-ads']) {
  if (pkg.toLowerCase().includes(bad)) adHits.push(`package.json depends on ${bad}`);
}
if (adHits.length) {
  adHits.forEach((h) => console.error(`  FAIL ad SDK present: ${h}`));
  process.exit(1);
}

console.log('  no fake engine / VPN / runtime claims in the UI');
console.log('  no ad SDK anywhere: the "ad-free" claim is a code property, not a promise');
