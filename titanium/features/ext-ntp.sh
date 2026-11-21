# ext-ntp — Titanium feature block
#
# Respect NTP URL overrides when deciding whether a tab is the new tab page.
#
# Sourced by ../patch.sh when ENABLE_EXT_NTP=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|if (isTabNtp \&\& !currentTab.isNativePage() \&\& !isTabWebUiNtp) {|if (isTabNtp \&\& !currentTab.isNativePage() \&\& !isTabWebUiNtp \&\& !UrlOverrideUtils.isNtpOverrideEnabled()) {|' chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java
