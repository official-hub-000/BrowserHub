# dev-incognito — Titanium feature block
#
# Let DevTools attach to incognito tabs.
#
# Sourced by ../patch.sh when ENABLE_DEV_INCOGNITO=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|intent.putExtra(DevToolsActivity.WEB_CONTENTS_KEY, webContents);|&intent.putExtra("org.chromium.chrome.browser.devtools.DevTools.Incognito", webContents.isIncognito());|' chrome/android/java/src/org/chromium/chrome/browser/devtools/DevToolsActivity.java
sed -i 's|TabModel tabModel = getTabModelSelector().getModel(false);|TabModel tabModel = getTabModelSelector().getModel(getIntentDataProvider().isOffTheRecord());|' chrome/android/java/src/org/chromium/chrome/browser/devtools/DevToolsActivity.java
sed -i 's|import org.chromium.chrome.browser.flags.ActivityType;|&\nimport org.chromium.chrome.browser.flags.CustomTabProfileType;|' chrome/android/java/src/org/chromium/chrome/browser/devtools/DevToolsIntentDataProvider.java
sed -i '/^public class DevToolsIntentDataProvider extends BrowserServicesIntentDataProvider {$/a\ @Override public @CustomTabProfileType int getCustomTabMode() { return mIntent.getBooleanExtra("org.chromium.chrome.browser.devtools.DevTools.Incognito", false) ? CustomTabProfileType.INCOGNITO : CustomTabProfileType.REGULAR; }' chrome/android/java/src/org/chromium/chrome/browser/devtools/DevToolsIntentDataProvider.java
sed -i 's|case CustomTabProfileType.INCOGNITO:|& if (getIntentDataProvider().getActivityType() == ActivityType.DEV_TOOLS) return null;|' chrome/android/java/src/org/chromium/chrome/browser/customtabs/BaseCustomTabActivity.java
