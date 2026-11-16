#!/usr/bin/env bash
set -euo pipefail

echo "⚡ 1/3 Building BrowserHub Hybrid Production Web Bundle..."
npm run build

echo "📦 2/3 Syncing Web Assets into Android WebViewAssetLoader Directory..."
mkdir -p android/app/src/main/assets/public
rm -rf android/app/src/main/assets/public/*
cp -r dist/* android/app/src/main/assets/public/

echo "🤖 3/3 Building Google Play Store .AAB + Universal .APK..."
cd android
gradle bundlePlaystoreRelease assembleUniversalRelease

echo ""
echo "✅ BUILD COMPLETE!"
echo "👉 Google Play Console Upload File (.aab): android/app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab"
echo "👉 Direct Install Android File (.apk):     android/app/build/outputs/apk/universalRelease/app-universal-release.apk"
