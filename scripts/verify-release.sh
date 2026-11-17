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
