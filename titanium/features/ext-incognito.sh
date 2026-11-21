# ext-incognito — Titanium feature block
#
# Allow extensions to run in incognito and open incognito as a window.
#
# Sourced by ../patch.sh when ENABLE_EXT_INCOGNITO=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|if (!context->IsOffTheRecord()) {|if (true) {|' extensions/browser/process_manager.cc
sed -i 's|public static boolean shouldOpenIncognitoAsWindow() {|public static boolean shouldOpenIncognitoAsWindow() { if (true) return true;|' chrome/browser/incognito/android/java/src/org/chromium/chrome/browser/incognito/IncognitoUtils.java
