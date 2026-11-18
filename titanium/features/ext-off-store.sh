# ext-off-store — Titanium feature block
#
# Allow installing extensions from non-Web-Store sources (Opera, Edge, and direct CRX).
#
# Sourced by ../patch.sh when ENABLE_EXT_OFF_STORE=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i '/^bool OffStoreInstallAllowedByPrefs(/a\if (const auto\& o = item.GetRequestInitiator(); o \&\& o->scheme() == "chrome-extension") return true; for (const char* d : {"addons.opera.com", "operacdn.com", "microsoftedge.microsoft.com", "edge.microsoft.com", "delivery.mp.microsoft.com"}) if (item.GetURL().DomainIs(d) || item.GetReferrerUrl().DomainIs(d)) return true;' chrome/browser/download/download_crx_util.cc
# sed -i 's/bool g_allow_offstore_install_for_testing = false;/bool g_allow_offstore_install_for_testing = true;/' chrome/browser/download/download_crx_util.cc
sed -i 's|public static void maybeDefaultEnableWindowSetting(Activity activity, Profile profile) {$|&WebsitePreferenceBridge.setContentSettingCustomScope(profile, ContentSettingsType.REQUEST_DESKTOP_SITE, "[*.]chromewebstore.google.com", SITE_WILDCARD, ContentSetting.ALLOW);|' chrome/browser/ui/android/desktop_site/java/src/org/chromium/chrome/browser/desktop_site/DesktopSiteUtils.java # desktop
