#!/bin/bash
source common.sh
set_keys
export VERSION=$(grep -m1 -o '[0-9]\+\(\.[0-9]\+\)\{3\}' vanadium/args.gn)
export CHROMIUM_SOURCE=https://chromium.googlesource.com/chromium/src.git # https://github.com/chromium/chromium.git
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update
sudo apt-get install -y sudo lsb-release file nano git curl python3 python3-pillow imagemagick librsvg2-bin
sudo dpkg --add-architecture i386; sudo apt-get update; sudo apt-get install -y libgcc-s1:i386

git clone --depth 1 https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH="$PWD/depot_tools:$PATH"
mkdir -p chromium/src/out/Default; cd chromium/src
git init
git remote add origin $CHROMIUM_SOURCE
git fetch --depth 1 $CHROMIUM_SOURCE +refs/tags/$VERSION:chromium_$VERSION
git checkout $VERSION
cp $SCRIPT_DIR/.gclient ../.gclient

# https://grapheneos.org/build#browser-and-webview
rm -rf $SCRIPT_DIR/vanadium/patches/*trichrome-{apk-build-targets,browser-apk-targets}.patch
rm -rf $SCRIPT_DIR/vanadium/patches/*{detailed,supported}-language*.patch
rm -rf $SCRIPT_DIR/vanadium/patches/*javascript-optimizer-{site-setting,settings-UI}.patch
rm -rf $SCRIPT_DIR/vanadium/patches/*component-updates.patch
rm -rf $SCRIPT_DIR/vanadium/patches/*{pdf,PDF,for-content-public,toolbar-button,configs-from-config-app,config-app-parsing,new-tab-card,predictive-back}*.patch
# rm -rf $SCRIPT_DIR/vanadium/patches/*crashpad*.patch
replace "$SCRIPT_DIR/vanadium/patches" "VANADIUM" "TITANIUM"
replace "$SCRIPT_DIR/vanadium/patches" "Vanadium" "Titanium"
replace "$SCRIPT_DIR/vanadium/patches" "vanadium" "titanium"
git am --whitespace=nowarn --keep-non-patch $SCRIPT_DIR/vanadium/patches/*.patch

gclient sync -D --no-history --nohooks
gclient runhooks
./build/install-build-deps.sh --no-prompt

source $SCRIPT_DIR/patch.sh
cp $SCRIPT_DIR/args.gn out/Default/args.gn
gn gen out/Default # gn args out/Default; echo 'treat_warnings_as_errors = false' >> out/Default/args.gn
mkdir -p out/tmp out/release

# Android only, and only the ABIs you ask for.
#
#   BROWSERHUB_ABI=both    two APKs, 32-bit + 64-bit (default; covers old phones)
#   BROWSERHUB_ABI=arm64   one APK, 64-bit only  -- every phone since ~2015
#   BROWSERHUB_ABI=arm     one APK, 32-bit only
#
# arm64 alone halves the build: the 32-bit pass is a separate full compile that
# no modern device ever loads.
ABI="${BROWSERHUB_ABI:-both}"
case "$ABI" in
    both|arm64|arm) ;;
    *) echo "build.sh: BROWSERHUB_ABI must be both, arm64 or arm (got '$ABI')" >&2; exit 1 ;;
esac

if [ "$ABI" = "both" ] || [ "$ABI" = "arm" ]; then
    autoninja -C out/Default chrome_public_apk
    mv $(find out/Default/apks -name 'Chrome*.apk') out/tmp/$VERSION-armeabi-v7a.apk
fi

if [ "$ABI" = "both" ] || [ "$ABI" = "arm64" ]; then
    sed -i 's/target_cpu = "arm"/target_cpu = "arm64"/' out/Default/args.gn
    autoninja -C out/Default chrome_public_apk
    mv $(find out/Default/apks -name 'Chrome*.apk') out/tmp/$VERSION-arm64-v8a.apk
fi

# The AAB is a Play Store *upload* format. Android cannot install one, so it is
# off by default; enable it only when publishing to Play.
if [ -n "${BROWSERHUB_AAB:-}" ]; then
    autoninja -C out/Default chrome_public_bundle
    mv $(find out/Default/apks -name 'Chrome*.aab') out/tmp/$VERSION-arm64-v8a.aab
fi

export PATH=$PWD/third_party/jdk/current/bin/:$PATH
export ANDROID_HOME=$PWD/third_party/android_sdk/public
if [ -n "${SKIP_SIGNING:-}" ]; then
    # No keys: publish the unsigned artifacts so a local build is still usable
    # for testing, and name them so nobody mistakes them for release builds.
    for a in out/tmp/*.apk out/tmp/*.aab; do
        [ -e "$a" ] || continue
        base=$(basename "$a"); mv "$a" "out/release/unsigned-$base"
    done
    echo "build.sh: unsigned artifacts in out/release/"
else
    # Sign only what this ABI selection actually produced.
    for a in out/tmp/*.apk; do
        [ -e "$a" ] || continue
        sign_apk "$a" "out/release/$(basename "$a")"
    done
    for a in out/tmp/*.aab; do
        [ -e "$a" ] || continue
        sign_aab "$a" "out/release/$(basename "$a")"
    done
fi
rm -rf $SCRIPT_DIR/keys
