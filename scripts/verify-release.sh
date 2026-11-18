#!/usr/bin/env bash
# One-shot release gate: CSS compatibility, AI behaviour, APK signature and contents.
# Usage: scripts/verify-release.sh [jdk-bin-dir]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JAVA_BIN="${1:-${JAVA_BIN:-}}"
JAVA="$JAVA_BIN/java"; [ -n "$JAVA_BIN" ] || JAVA="java"
# Child `node -e` gates read this, so it must be visible in their environment.
export JAVA

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

echo "== Screenshot failures (page text, offline claim, summary) =="
npx tsx scripts/screenshot-failures-test.mts | tail -1

echo "== WebExtension runtime (packages parse, match and execute) =="
npx tsx scripts/extension-runtime-test.mts | tail -1

echo "== Viewport sync coalesced (no JNI call per frame or per render) =="
npx tsx scripts/viewport-sync-test.mts | tail -1

echo "== Video grabber (finds real media, refuses the impossible) =="
npx tsx scripts/video-capture-test.mts | tail -1

echo "== Stream engine: playlists parse, segments join, AES-128 decrypts =="
ENGINE_DIR="$(mktemp -d)"
"$JAVA" -jar build-tools/ecj-3.45.0.jar -8 -nowarn -cp build-tools/android.jar -d "$ENGINE_DIR" \
  $(find android/standalone/src/com/browserhub/app/media -name '*.java') \
  android/standalone/test/MediaEngineTest.java android/standalone/test/MediaMergeTest.java \
  android/standalone/test/DashMergeTest.java || {
    echo "  FAIL the media engine does not compile"; exit 1; }
"$JAVA" -cp "$ENGINE_DIR" MediaEngineTest | tail -1
"$JAVA" -cp "$ENGINE_DIR" MediaMergeTest | tail -1
"$JAVA" -cp "$ENGINE_DIR" DashMergeTest | tail -1
rm -rf "$ENGINE_DIR"

echo "== Download queue: progress shown is progress that happened =="
npx tsx scripts/download-queue-test.mts | tail -1

echo "== Video grabber: download path survives scoped storage =="
node scripts/media-download-check.mjs | tail -1

echo "== Video grabber: no ad SDK anywhere (the ad-free guarantee) =="
node scripts/honesty-check.mjs | tail -1

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
node -e '
const { execSync } = require("child_process");
let out = "";
try {
  out = execSync(`"${process.env.JAVA}" -jar build-tools/apksigner.jar verify -v release/BrowserHub-v1.0.0-release.apk`,
    { stdio: ["ignore", "pipe", "ignore"] }).toString();
} catch (e) {
  console.error("  FAIL apksigner could not verify the APK (" + e.message.split("\n")[0] + ")");
  process.exit(1);
}
const need = [
  [/^Verifies$/m, "APK verifies"],
  [/v2 scheme \(APK Signature Scheme v2\): true/, "v2 signature present"],
  [/v3 scheme \(APK Signature Scheme v3\): true/, "v3 signature present"],
];
let bad = 0;
for (const [re, label] of need) {
  if (re.test(out)) console.log("  ok " + label);
  else { console.error("  FAIL " + label); bad = 1; }
}
const signers = /Number of signers: (\d+)/.exec(out);
if (signers) console.log("  signers: " + signers[1]);
process.exit(bad);
'

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
