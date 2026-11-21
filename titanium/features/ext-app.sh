# ext-app — Titanium feature block
#
# Expose the legacy app API on desktop_android.
#
# Sourced by ../patch.sh when ENABLE_EXT_APP=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|^  "app": {$|&\n    "platforms": ["chromeos", "linux", "mac", "win"],|' chrome/common/extensions/api/_api_features.json
