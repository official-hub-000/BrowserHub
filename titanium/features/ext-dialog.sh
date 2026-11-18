# ext-dialog — Titanium feature block
#
# Do not filter touch events for security in the extension install dialog.
#
# Sourced by ../patch.sh when ENABLE_EXT_DIALOG=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|.with(ModalDialogProperties.FILTER_TOUCH_FOR_SECURITY, true)|.with(ModalDialogProperties.FILTER_TOUCH_FOR_SECURITY, false)|' chrome/browser/ui/android/extensions/java/src/org/chromium/chrome/browser/ui/extensions/ExtensionInstallDialogBridge.java
