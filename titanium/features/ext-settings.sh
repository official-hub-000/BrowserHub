# ext-settings — Titanium feature block
#
# Expose extension settings on desktop_android.
#
# Sourced by ../patch.sh when ENABLE_EXT_SETTINGS=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|"platforms": \["win", "mac"\]|"platforms": ["win", "mac", "desktop_android"]|' chrome/common/extensions/api/_manifest_features.json
