# dev — Titanium feature block
#
# Enable the Task Manager and DevTools entry points, including the version-gated branches.
#
# Sourced by ../patch.sh when ENABLE_DEV=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

# sed -i '0,/modelList.add(buildSettingsItem());/s//modelList.add(mMoreToolsItemBuilder.buildTaskManagerItem());modelList.add(mMoreToolsItemBuilder.buildDevToolsItem());&/' chrome/android/java/src/org/chromium/chrome/browser/tabbed_mode/TabbedAppMenuPropertiesDelegate.java
sed -i '/BASE_FEATURE(kTaskManagerClank,/,/);/ s/base::FEATURE_DISABLED_BY_DEFAULT/base::FEATURE_ENABLED_BY_DEFAULT/' chrome/browser/task_manager/common/task_manager_features.cc
if version_lt "$VERSION" "156.0.8060.0"; then
sed -i 's|!DeviceFormFactor.isNonMultiDisplayContextOnTablet(mContext)|(false \&\& &)|' chrome/android/java/src/org/chromium/chrome/browser/tabbed_mode/MoreToolsItemBuilder.java
sed -i 's|boolean shouldShowDeveloperMenu() {|boolean shouldShowDeveloperMenu() { if (true) return DevToolsWindowAndroid.isDevToolsAllowedFor(getProfile(), mItemDelegate.getWebContents());|' chrome/android/java/src/org/chromium/chrome/browser/contextmenu/ChromeContextMenuPopulator.java
else
sed -i 's|DeviceFormFactor.isNonMultiDisplayContextOnTablet(context)|(true \|\| &)|' chrome/browser/devtools/android/java/src/org/chromium/chrome/browser/devtools/DevToolsWindowAndroid.java
fi
sed -i 's|TabUtils.isUsingDesktopUserAgent(mItemDelegate.getWebContents())|(true \|\| TabUtils.isUsingDesktopUserAgent(mItemDelegate.getWebContents()))|' chrome/android/java/src/org/chromium/chrome/browser/contextmenu/ChromeContextMenuPopulator.java
