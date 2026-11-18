# core — Titanium feature block
#
# Base Titanium build: icon reskin, extractNativeLibs, PDF intent, debloat, bundled-extension staging, flag unexpiring, omnibox site search, feature overrides, task manager, accessibility, devtools menu.
#
# Sourced by ../patch.sh when ENABLE_CORE=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

mkdir -p chrome/android/java/res_titanium_base
cp $SCRIPT_DIR/res/drawable/themed_app_icon.xml chrome/android/java/res_titanium_base/drawable/themed_app_icon.xml
for icon in $(find chrome/android/java/res_titanium_base -type f -name '*.png'); do convert $icon -fill navy -tint 36 $icon && $SCRIPT_DIR/res/icon.sh $icon; done
sed -i 's|<application |<application android:extractNativeLibs="false" |' chrome/android/java/AndroidManifest.xml
sed -i 's|<data android:mimeType="message/rfc822"/>|<data android:mimeType="message/rfc822"/><data android:mimeType="application/pdf"/>|' chrome/android/java/AndroidManifest.xml
sed -i '/com.google.ar.core.min_apk_version/d' third_party/arcore-android-sdk-client/AndroidManifest_basesplit.xml
# sed -i 's|Google LLC|jqssun, Google LLC|' chrome/browser/ui/android/strings/android_chrome_strings.grd

cp -r $SCRIPT_DIR/extensions/dist titanium/
cp $SCRIPT_DIR/extensions/stage_bundled_extensions.inc titanium/dist/
sed -i 's|"//components/privacy_sandbox/privacy_sandbox_attestations/preload:privacy_sandbox_attestations_assets",|&"//titanium/dist:extension_assets",|' chrome/android/BUILD.gn
sed -i 's|if (!base::PathService::Get(base::DIR_MODULE, \&cur)) {|if (!base::PathService::Get(chrome::DIR_USER_DATA, \&cur)) {|' chrome/common/chrome_paths.cc
sed -i 's|#include "extensions/buildflags/buildflags.h"|&\n#include "titanium/dist/stage_bundled_extensions.inc"|' chrome/browser/extensions/external_pref_loader.cc
sed -i 's|ReadStandaloneExtensionPrefFiles(prefs);|&StageBundledExtensions(base_path_id_, base_path_, prefs);|' chrome/browser/extensions/external_pref_loader.cc
sed -i 's|if (extension.location() == mojom::ManifestLocation::kCommandLine) {|if (extension.location() == mojom::ManifestLocation::kExternalPref) return false;\n&|' chrome/browser/extensions/extension_safety_check_utils.cc

sed -i '/android_apk_or_module_srcjar_deps_common/{n;s|if (!_omit_dex) {|if (_is_base_module \&\& !_omit_dex) {|}' build/config/android/rules.gni
sed -i '/safelyRemovePreference(prefFragment/d' titanium/chromium_src/chrome/browser/language/android/java/src/org/chromium/chrome/browser/language/settings/LanguageSettingsExt.java # language
sed -i '/safelyRemovePreference($/{N;/PREF_JAVASCRIPT_OPTIMIZER/d}' titanium/chromium_src/chrome/android/java/src/org/chromium/chrome/browser/privacy/settings/PrivacySettingsExt.java # optimizer
sed -i 's|if (!Intent\.ACTION_VIEW\.equals(intent\.getAction())) {|if (!Intent.ACTION_VIEW.equals(intent.getAction()) \|\| !android.webkit.URLUtil.isNetworkUrl(IntentHandler.getUrlFromIntent(intent))) {|' titanium/chromium_src/chrome/android/java/src/org/chromium/chrome/browser/LaunchIntentDispatcherHooks.java # scheme guard
sed -i 's|if (urlFromIntent == null) {|if (!android.webkit.URLUtil.isNetworkUrl(urlFromIntent)) {|' titanium/chromium_src/chrome/android/java/src/org/chromium/chrome/browser/LaunchIntentDispatcherHooks.java # scheme guard
sed -i 's|static void maybeModifyCustomTabIntents(Intent intent) {|& if (!android.webkit.URLUtil.isNetworkUrl(IntentHandler.getUrlFromIntent(intent))) { return; }|' titanium/chromium_src/chrome/android/java/src/org/chromium/chrome/browser/LaunchIntentDispatcherHooks.java # scheme guard
sed -i 's|readBoolean(getSettingsPreferenceKey(moduleType), true)|readBoolean(getSettingsPreferenceKey(moduleType), !HomeModulesUtils.belongsToEducationalTipModule(moduleType))|' chrome/browser/magic_stack/android/java/src/org/chromium/chrome/browser/magic_stack/HomeModulesConfigManager.java # ntp
sed -i 's|private static boolean verifyPackage(Context ctx, String pkgName, byte\[\] sha256CertDigestBytes) {|& if (true) return false; |' titanium/android_config/parser/java/src/app/titanium/config/TitaniumConfParser.java # config parser
sed -i 's|static boolean isSupported() {|& if (android.os.Build.VERSION.SDK_INT < 31) return false;|;s|LocationManager locationManager, LocationListener listener) {|& if (android.os.Build.VERSION.SDK_INT < 31) return false;|' titanium/chromium_src/services/downstream/device/geolocation/android/java/src/org/chromium/device/geolocation/CoarseLocationRequests.java # api

# sed -i 's|int ExpirationMilestoneForFlag(const char\* flag) {|int ExpirationMilestoneForFlag(const char* flag) { if ((true)) return -1;|' chrome/browser/unexpire_flags.cc
for flag in "align-wakeups" "android-bottom-bar" "cct-open-in-browser-button-if-allowed-by-embedder" "darken-websites-checkbox-in-themes-setting" "enable-accessibility-sequential-focus" "enforce-incognito-isolation" "inline-pdf-v2" "jump-start-omnibox" "lock-controls-on-tablets-v2" "offline-auto-fetch" "use-fullscreen-insets-api"; do
    sed -i "/\"name\": \"$flag\"/,/}/ s/\"expiry_milestone\": [0-9]\+/\"expiry_milestone\": -1/" chrome/browser/flag-metadata.json
done
sed -i 's|newFlag(OmniboxFeatureList.OMNIBOX_SITE_SEARCH, FeatureState.ENABLED_IN_TEST);|newFlag(OmniboxFeatureList.OMNIBOX_SITE_SEARCH, FeatureState.ENABLED_IN_PROD);|' components/omnibox/common/android/java/src/org/chromium/components/omnibox/OmniboxFeatures.java # search
sed -i 's|BASE_FEATURE(kOmniboxSiteSearch, DISABLED);|BASE_FEATURE(kOmniboxSiteSearch, ENABLED);|' components/omnibox/common/omnibox_features.cc # search

sed -i '/#if BUILDFLAG(IS_DESKTOP_ANDROID)/{
a\
feature_overrides.EnableFeature(chrome::android::kSubmenusInAppMenu);\
feature_overrides.EnableFeature(features::kAndroidDevToolsFrontend);\
feature_overrides.EnableFeature(kAndroidMediaPicker);\
feature_overrides.EnableFeature(features::kUserMediaScreenCapturing);\
feature_overrides.EnableFeature(media::kAndroidEnableBackgroundMediaCapturing);\
feature_overrides.EnableFeature(media::kAutoPictureInPictureAndroid);\
feature_overrides.EnableFeature(chrome::android::kLoadAllTabsAtStartup);\
feature_overrides.EnableFeature(chrome::android::kChromeNativeUrlOverriding);\
feature_overrides.EnableFeature(download::features::kEnableDownloadSaveAsContextMenu);\
#if 0
d}' chrome/browser/chrome_browser_field_trials.cc
