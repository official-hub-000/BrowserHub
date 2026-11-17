#!/usr/bin/env bash
# One-shot release gate: CSS compatibility, AI behaviour, APK signature and contents.
# Usage: scripts/verify-release.sh [jdk-bin-dir]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JAVA_BIN="${1:-}"
JAVA="$JAVA_BIN/java"; [ -n "$JAVA_BIN" ] || JAVA="java"

echo "== CSS compatibility =="
node scripts/css-compat-check.mjs

echo "== AI behaviour =="
npx tsx scripts/ai-guard-test.mts

echo "== AI answers as the user sees them =="
npx tsx scripts/ai-live-smoke.mts | tail -1

echo "== Surfaces render, every button labelled =="
npx tsx scripts/render-preview.tsx >/dev/null
node -e '
const fs = require("fs");
let bad = 0;
for (const name of ["home", "menu", "translate", "ai"]) {
  const html = fs.readFileSync(`/tmp/preview-${name}.html`, "utf8");
  const buttons = [...html.matchAll(/<button\b[^>]*>[\s\S]*?<\/button>/g)];
  for (const m of buttons) {
    const tag = m[0].slice(0, m[0].indexOf(">"));
    const text = m[0].replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
    if (!text && !/aria-label|title=/.test(tag)) bad++;
  }
  console.log(`  ${name}: ${buttons.length} buttons checked`);
}
if (bad > 0) { console.error(`  ${bad} unlabelled button(s)`); process.exit(1); }
console.log("  all buttons labelled");
'

echo "== Device input regression guards =="
node -e '
const fs = require("fs");
const app = fs.readFileSync("src/App.tsx", "utf8");
const java = fs.readFileSync("android/standalone/src/com/browserhub/app/MainActivity.java", "utf8");
const kt = fs.readFileSync("android/app/src/main/java/com/browserhub/app/MainActivity.kt", "utf8");
let bad = [];
// 1. The native browser view is stacked ABOVE the React shell, so the shell
//    must re-sync its rect continuously and yield while shell UI floats on top.
if (!/setInterval\(schedule, \d+\)/.test(app)) bad.push("App.tsx: no continuous native viewport re-sync");
if (!/pipOpen:\s*pipTitle !== null/.test(app)) bad.push("App.tsx: mini player does not hide the native page");
if (!/fireSweep:\s*fireSweepAnim/.test(app)) bad.push("App.tsx: shutdown sweep does not hide the native page");
if (/className="absolute bottom-3 right-3 z-30/.test(app)) bad.push("App.tsx: PiP card still inside the native viewport");
// 2. A GONE WebView can keep a cached touch target on OEM skins.
if (!/setFocusableInTouchMode\(false\)/.test(java)) bad.push("MainActivity.java: hidden browser view stays focusable");
if (!/setTranslationY\(browserWebView\.getHeight\(\)/.test(java)) bad.push("MainActivity.java: hidden browser view not moved off-screen");
if (!/bringToFront\(\)/.test(java)) bad.push("MainActivity.java: browser view not raised when shown");
if (!/onFindResultReceived/.test(java)) bad.push("MainActivity.java: find-in-page never reports match counts");
if (!/__onNativeFindResult/.test(app)) bad.push("App.tsx: find bar cannot show real match counts");
if (!/setFocusableInTouchMode\(false\)/.test(java)) bad.push("MainActivity.java: hidden browser view stays focusable");
if (!/isFocusableInTouchMode = false/.test(kt)) bad.push("MainActivity.kt (Play Store shell): hidden browser view stays focusable");
if (!/bringToFront\(\)/.test(kt)) bad.push("MainActivity.kt (Play Store shell): browser view not raised when shown");
if (!/applyPageTranslation/.test(java) || !/applyPageTranslation/.test(kt)) bad.push("page translation missing from a native shell");
if (!/setPageTranslation/.test(app)) bad.push("App.tsx never sends the translation lexicon to the page");
if (bad.length) { bad.forEach((b) => console.error("  FAIL " + b)); process.exit(1); }
console.log("  native viewport sync + touch handoff guards OK");
'

echo "== AI reliability: model rotation in the engine + both native shells =="
node scripts/ai-reliability-check.mjs | tail -1

echo "== Exact arithmetic + no prompt contamination =="
npx tsx scripts/math-and-prompt-check.mts | tail -1

echo "== AI survives provider throttling (answered-then-apologised bug) =="
npx tsx scripts/ai-throttle-simulation.mts | tail -1

echo "== Live chat recovers (success must not self-block, providers independent) =="
npx tsx scripts/live-chat-recovery-test.mts | tail -1

echo "== Privacy shield labelled honestly + UI has visual depth =="
node scripts/vpn-and-visual-check.mjs | tail -1

echo "== URL safety (intent:// can never reach the WebView) =="
npx tsx scripts/url-safety-test.mts | tail -1

echo "== Games hub honesty (no fake playability) =="
node scripts/games-honesty-check.mjs | tail -1

echo "== Screen fits the device + Back always works =="
node scripts/viewport-and-back-check.mjs | tail -1

echo "== Extension store: every entry says whether it works =="
node scripts/extensions-honesty-check.mjs | tail -1

echo "== Pure AMOLED black + motion =="
node scripts/amoled-motion-check.mjs | tail -1

echo "== Every control is wired (no dead buttons) =="
node scripts/button-wiring-check.mjs | tail -1

echo "== Overlays can always be dismissed =="
node scripts/modal-escape-check.mjs | tail -1

echo "== 360px layout safety =="
node scripts/narrow-layout-check.mjs | tail -1

echo "== Extensions: enforced or labelled preview =="
node scripts/extension-enforcement-check.mjs | tail -1

echo "== Real search URLs, no mock pages =="
node scripts/search-url-check.mjs | tail -1

echo "== Every shield toggle is enforced =="
node scripts/shield-enforcement-check.mjs | tail -1

echo "== No fake feature claims =="
node scripts/honesty-check.mjs | tail -2

echo "== Every visible setting has an effect =="
node scripts/settings-effect-check.mjs | tail -2

echo "== Settings persistence =="
node scripts/prefs-persistence-test.mjs | tail -1
node scripts/prefs-wiring-check.mjs

echo "== Offline page translator injection =="
node scripts/translate-injection-test.mjs | tail -1

echo "== Vector icons (no emoji in UI) =="
node -e '
const fs = require("fs");
const path = require("path");
const files = ["src/App.tsx"].concat(
  fs.readdirSync("src/components").map((f) => "src/components/" + f)
);
const pat = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u;
let bad = [];
for (const f of files) {
  if (f.endsWith("VectorIcons.tsx")) continue;
  const src = fs.readFileSync(f, "utf8");
  src.split("\n").forEach((line, i) => {
    if (pat.test(line) && !/^\s*\/\//.test(line)) bad.push(f + ":" + (i + 1) + " " + line.trim().slice(0, 70));
  });
}
if (bad.length) { bad.slice(0, 10).forEach((b) => console.error("  FAIL emoji icon " + b)); process.exit(1); }
console.log("  no emoji icons left in any UI surface");
'

echo "== APK signature =="
"$JAVA" -jar build-tools/apksigner.jar verify -v release/BrowserHub-v1.0.0-release.apk 2>/dev/null \
  | grep -E "^Verifies|v2 scheme|v3 scheme|Number of signers"

echo "== APK contents =="
node -e '
const { execSync } = require("child_process");
const listing = execSync("unzip -l release/BrowserHub-v1.0.0-release.apk").toString();
for (const need of ["assets/public/index.html", "classes.dex", "AndroidManifest.xml"]) {
  if (!listing.includes(need)) { console.error("missing " + need); process.exit(1); }
  console.log("  ok " + need);
}
const js = listing.match(/assets\/public\/assets\/index-[A-Za-z0-9_-]+\.js/)[0];
const css = listing.match(/assets\/public\/assets\/index-[A-Za-z0-9_-]+\.css/)[0];
const cssBody = execSync(`unzip -p release/BrowserHub-v1.0.0-release.apk ${css}`).toString();
for (const token of ["@layer", "oklch", "oklab", "color-mix"]) {
  if (cssBody.includes(token)) { console.error(`packaged CSS still contains ${token}`); process.exit(1); }
}
console.log("  packaged CSS is Android-WebView safe");
const jsBody = execSync(`unzip -p release/BrowserHub-v1.0.0-release.apk ${js}`).toString();
for (const marker of ["startAiRequest", "Search the web or enter a website"]) {
  if (!jsBody.includes(marker)) { console.error("bundle missing " + marker); process.exit(1); }
  console.log("  ok bundle contains " + marker);
}
'
echo ""
echo "✅ Release verified."
