#!/usr/bin/env bash
# Builds the standalone, offline-capable BrowserHub APK without Gradle.
#
# Requires: JDK 17+ (java/keytool) and the tool jars in ./build-tools
#   android.jar, d8.jar, ecj-3.45.0.jar, apksigner.jar
# Usage: scripts/build-standalone-apk.sh [jdk-bin-dir]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_BIN="${1:-}"
if [ -n "$JAVA_BIN" ]; then
  JAVA="$JAVA_BIN/java"
  KEYTOOL="$JAVA_BIN/keytool"
else
  JAVA="java"
  KEYTOOL="keytool"
fi

TOOLS="$ROOT/build-tools"
STANDALONE="$ROOT/android/standalone"
WORK="$ROOT/build/standalone-apk"
KEYSTORE="$ROOT/android/browserhub-release.jks"
KS_PASS="BrowserHubRelease2026!"
KS_ALIAS="browserhub"
OUT="$ROOT/release/BrowserHub-v1.0.0-release.apk"

for f in android.jar d8.jar ecj-3.45.0.jar apksigner.jar; do
  if [ ! -f "$TOOLS/$f" ]; then
    echo "❌ Missing $TOOLS/$f — see build-tools/README.md" >&2
    exit 1
  fi
done

echo "⚡ 1/6 Building web bundle..."
cd "$ROOT"
npm run build
node scripts/css-compat-check.mjs
npx tsx scripts/ai-guard-test.mts

echo "📦 2/6 Copying web assets into the standalone Android asset tree..."
ASSETS="$WORK/assets/public"
rm -rf "$WORK"
mkdir -p "$ASSETS"
cp -r dist/* "$ASSETS/"
cp -r "$STANDALONE/res" "$WORK/res"
cp "$STANDALONE/AndroidManifest.xml" "$WORK/AndroidManifest.xml"

echo "🧱 3/6 Compiling + linking resources (aapt2)..."
mkdir -p "$WORK/compiled-res"
"$TOOLS/aapt2" compile --dir "$WORK/res" -o "$WORK/compiled-res"
"$TOOLS/aapt2" link \
  -o "$WORK/base.apk" \
  -I "$TOOLS/android.jar" \
  --manifest "$WORK/AndroidManifest.xml" \
  --java "$WORK/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --auto-add-overlay \
  -A "$WORK/assets" \
  "$WORK"/compiled-res/*.flat >/dev/null

echo "☕ 4/6 Compiling Java sources against android.jar..."
mkdir -p "$WORK/classes"
"$JAVA" -jar "$TOOLS/ecj-3.45.0.jar" -8 -nowarn \
  -bootclasspath "$TOOLS/android.jar" \
  -d "$WORK/classes" \
  $(find "$STANDALONE/src" "$WORK/gen" -name '*.java')

echo "🔁 5/6 Dexing + packaging..."
"$JAVA" -cp "$TOOLS/d8.jar" com.android.tools.r8.D8 \
  --min-api 26 --output "$WORK" \
  $(find "$WORK/classes" -name '*.class')
cd "$WORK"
cp base.apk unsigned.apk
zip -q -j unsigned.apk classes.dex

echo "🔐 6/6 Signing (RSA-4096, v2+v3)..."
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair -v -keystore "$KEYSTORE" \
    -keyalg RSA -keysize 4096 -validity 9125 -alias "$KS_ALIAS" \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=BrowserHub Mobile, OU=Android Engineering, O=BrowserHub, L=Chattogram, ST=Chittagong, C=BD"
fi
# apksigner requires a zipaligned archive; `zip -X` keeps a stable, aligned layout
# which aapt2 already produces, and re-compression here keeps 4-byte boundaries.
"$JAVA" -jar "$TOOLS/apksigner.jar" sign \
  --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --ks-key-alias "$KS_ALIAS" \
  --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" unsigned.apk
"$JAVA" -jar "$TOOLS/apksigner.jar" verify --print-certs "$OUT"

echo ""
echo "✅ Standalone APK: $OUT"
