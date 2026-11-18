# desktop-omnibox — Titanium feature block
#
# Make omnibox zero-suggest and autocomplete behave desktop-style rather than mobile-style.
#
# Sourced by ../patch.sh when ENABLE_DESKTOP_OMNIBOX=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's/is_desktop_android = !!BUILDFLAG(IS_DESKTOP_ANDROID);/is_desktop_android = false;/' components/omnibox/browser/zero_suggest_verbatim_match_provider.cc
sed -i 's/is_android_mobile = is_android_any \&\& !is_android_desktop;/is_android_mobile = is_android_any \&\& is_android_desktop;/' components/omnibox/browser/autocomplete_result.cc
# sed -i 's|OmniboxCapabilities.hasDesktopExperience(context)|true|g' chrome/browser/ui/android/omnibox/java/src/org/chromium/chrome/browser/omnibox/FuseboxSessionState.java
