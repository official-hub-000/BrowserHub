#!/usr/bin/env bash
# Restores the Gradle-free APK toolchain into ./build-tools.
#
# The jars and the aapt2 binary are ~60 MB of third-party binaries, so they are
# gitignored; a fresh checkout (or a recycled sandbox) restores them with this
# one command. Every source below is a public mirror that needs no credentials.
#
# Usage: scripts/fetch-apk-tools.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/build-tools"
TMP="${TMPDIR:-/tmp}/bh-aptk"
mkdir -p "$TOOLS" "$TMP"

# GitHub serves repo blobs over the API even when the CDN is unreachable;
# "Accept: application/vnd.github.raw" returns the file bytes directly.
gh_raw() { # repo, path, out
  curl -fsSL --retry 3 -H "Accept: application/vnd.github.raw" \
    "https://api.github.com/repos/$1/contents/$2" -o "$3"
}

echo "== Android platform stubs (compile-time only) =="
if [ ! -s "$TOOLS/android.jar" ]; then
  echo "  ↓ android.jar (API 34)"
  curl -fsSL --retry 3 -o "$TMP/platforms.tgz" \
    "https://codeload.github.com/Sable/android-platforms/tar.gz/refs/heads/master"
  tar xzf "$TMP/platforms.tgz" -C "$TMP" android-platforms-master/android-34/android.jar
  cp "$TMP/android-platforms-master/android-34/android.jar" "$TOOLS/android.jar"
fi

echo "== Compiler / dexer / signer =="
# ecj = Eclipse batch compiler (javac stand-in); d8/apksigner/android.jar are the
# exact copies the minapk toolchain ships, which this build already targets.
if [ ! -s "$TOOLS/ecj-3.45.0.jar" ]; then
  echo "  ↓ ecj-3.45.0.jar"
  gh_raw NorbertXYZ/eclipse-maven org/eclipse/jdt/ecj/3.45.0/ecj-3.45.0.jar "$TOOLS/ecj-3.45.0.jar"
fi
if [ ! -s "$TOOLS/d8.jar" ] || ! unzip -l "$TOOLS/d8.jar" 2>/dev/null | grep -q 'com/android/tools/r8/D8.class'; then
  echo "  ↓ d8.jar (R8 dexer)"
  curl -fsSL --retry 3 -o "$TMP/minapk.tgz" \
    "https://registry.npmjs.org/@drxiaozhi/minapk/-/minapk-0.5.1.tgz"
  tar xzf "$TMP/minapk.tgz" -C "$TMP" package/tools/d8.jar
  cp "$TMP/package/tools/d8.jar" "$TOOLS/d8.jar"
fi
if [ ! -s "$TOOLS/apksigner.jar" ]; then
  echo "  ↓ apksigner.jar"
  curl -fsSL --retry 3 -o "$TMP/minapk.tgz" \
    "https://registry.npmjs.org/@drxiaozhi/minapk/-/minapk-0.5.1.tgz"
  tar xzf "$TMP/minapk.tgz" -C "$TMP" package/tools/apksigner.jar
  cp "$TMP/package/tools/apksigner.jar" "$TOOLS/apksigner.jar"
fi
if [ ! -s "$TOOLS/aapt2" ]; then
  echo "  ↓ aapt2"
  curl -fsSL --retry 3 -o "$TMP/aaptjs.tgz" \
    "https://registry.npmjs.org/aaptjs3/-/aaptjs3-2.0.2.tgz"
  tar xzf "$TMP/aaptjs.tgz" -C "$TMP" package/bin/x64/linux/aapt2
  cp "$TMP/package/bin/x64/linux/aapt2" "$TOOLS/aapt2"
  chmod +x "$TOOLS/aapt2"
fi

chmod 644 "$TOOLS"/*.jar 2>/dev/null || true
chmod +x "$TOOLS/aapt2" "$TOOLS/zipalign" 2>/dev/null || true

echo ""
echo "✅ Toolchain ready:"
ls -la "$TOOLS" | sed 's/^/  /'
