# desktop-menu — Titanium feature block
#
# Desktop-style app menu: multi-window management and incognito-window toggling.
#
# Sourced by ../patch.sh when ENABLE_DESKTOP_MENU=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|if (!IncognitoUtils.shouldOpenIncognitoAsWindow() \|\| is|if (!shouldShowNewIncognitoWindow() \|\| is|' chrome/android/java/src/org/chromium/chrome/browser/tabbed_mode/TabbedAppMenuPropertiesDelegate.java
sed -i 's|if (!separateIncognitoWindow \|\| is|if (!shouldShowNewIncognitoWindow() \|\| is|' chrome/android/java/src/org/chromium/chrome/browser/tabbed_mode/TabbedAppMenuPropertiesDelegate.java
sed -i 's|<item type="id" name="manage_all_windows_menu_id" />|&<item type="id" name="incognito_toggle_menu_id" />|' chrome/android/java/res/values/ids.xml # regular
sed -i 's|modelList.add(buildManageWindowsItem());$|&if (isIncognito \&\& MultiWindowUtils.isMultiInstanceApi31Enabled()) modelList.add(new ListItem(AppMenuHandler.AppMenuItemType.STANDARD, AppMenuItemUtils.buildModelForStandardMenuItem(mContext, getAppMenuItemTheme(), R.id.incognito_toggle_menu_id, R.string.accessibility_tabstrip_btn_incognito_toggle_incognito, R.drawable.ic_open_in_browser, false)));|' chrome/android/java/src/org/chromium/chrome/browser/tabbed_mode/TabbedAppMenuPropertiesDelegate.java # regular
sed -i 's|if (id == R.id.manage_all_windows_menu_id) {|if (id == R.id.incognito_toggle_menu_id) { int w = MultiWindowUtils.getLastAccessedWindowIdExcludingSelf(mInstanceId, PersistedInstanceType.REGULAR); if (w != INVALID_WINDOW_ID) openWindow(w, NewWindowAppSource.WINDOW_MANAGER); else openNewWindow(false); return true; } &|' chrome/android/java/src/org/chromium/chrome/browser/multiwindow/MultiInstanceManagerApi31.java # regular

# crbug.com/406136787: load unpacked
sed -i 's|assert treeId.equals(documentId);|&\n if ("com.android.externalstorage.documents".equals(mAuthority)) { String fastId = mRelativePath.isEmpty() ? treeId : (treeId.endsWith(":") ? treeId + mRelativePath : treeId + "/" + mRelativePath); Uri fast = DocumentsContract.buildDocumentUriUsingTree(tree, fastId); return contentUriExists(fast) ? fast : null; }|' base/android/java/src/org/chromium/base/VirtualDocumentPath.java

# crbug.com/445475304: incognito back
sed -i 's|private void onTabChanged(@Nullable Tab tab) {|private void onTabChanged(@Nullable Tab tab) { if (tab != null \&\& tab.isIncognitoBranded()) { mSystemBackPressSupplier.set(true); return; }|' chrome/browser/back_press/android/java/src/org/chromium/chrome/browser/back_press/MinimizeAppAndCloseTabBackPressHandler.java

# crbug.com/431004500: incognito uaf
sed -i '/for (int i = 0; i < tab_list->GetTabCount(); ++i) {/i if (!tab_list) { continue; }' chrome/browser/extensions/api/tabs/tabs_api.cc

# crbug.com/40274462: incognito uaf
sed -i '/CONTENT_EXPORT static WebContents\* FromRenderFrameHost(RenderFrameHost\* rfh);/a\CONTENT_EXPORT static bool HasLiveWebContentsForBrowserContext(BrowserContext* browser_context);' content/public/browser/web_contents.h
sed -i '/^WebContentsImpl::WebContentsImpl(BrowserContext\* browser_context)/i\ bool WebContents::HasLiveWebContentsForBrowserContext(BrowserContext* browser_context) { for (WebContentsImpl* web_contents : WebContentsImpl::GetAllWebContents()) { if (web_contents->GetBrowserContext() == browser_context) { return true; } } return false; }' content/browser/web_contents/web_contents_impl.cc
sed -i '/#include "content\/public\/browser\/render_process_host.h"/a#include "content/public/browser/web_contents.h"' chrome/browser/profiles/profile_destroyer.cc
sed -i '/^void ProfileDestroyer::DestroyOTRProfileWhenAppropriateWithTimeout($/,/MaybeSendDestroyedNotification/{/  profile->MaybeSendDestroyedNotification();/i\
if (content::WebContents::HasLiveWebContentsForBrowserContext(profile)) { return; }
}' chrome/browser/profiles/profile_destroyer.cc

# crbug.com/444024982: api 31
sed -i 's/|| mSupportedProfileType == SupportedProfileType.REGULAR) {/|| mSupportedProfileType == SupportedProfileType.REGULAR || mSupportedProfileType == SupportedProfileType.MIXED) {/' chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java
sed -i 's/|| mSupportedProfileType == SupportedProfileType.OFF_THE_RECORD) {/|| mSupportedProfileType == SupportedProfileType.OFF_THE_RECORD || mSupportedProfileType == SupportedProfileType.MIXED) {/' chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java

export PATCHED=1
