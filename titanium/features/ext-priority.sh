# ext-priority — Titanium feature block
#
# Raise the process priority of extension renderers so they are not killed.
#
# Sourced by ../patch.sh when ENABLE_EXT_PRIORITY=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|private @ChildProcessImportance int mEffectiveImportance;$|&private final boolean mIsExtensionProcess;|' content/public/android/java/src/org/chromium/content/browser/ChildProcessLauncherHelperImpl.java
sed -i 's|^ *if (!ApplicationStatus.isInitialized()) return;$|mIsExtensionProcess = Arrays.asList(commandLine).contains("--extension-process"); if (mIsExtensionProcess) mEffectiveImportance = ChildProcessImportance.IMPORTANT;&|' content/public/android/java/src/org/chromium/content/browser/ChildProcessLauncherHelperImpl.java
sed -i 's|@ChildProcessImportance int importance) {$|&if (mIsExtensionProcess) importance = ChildProcessImportance.IMPORTANT;|' content/public/android/java/src/org/chromium/content/browser/ChildProcessLauncherHelperImpl.java
