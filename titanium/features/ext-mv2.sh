# ext-mv2 — Titanium feature block
#
# Allow Manifest V2 extensions instead of hard-disabling them.
#
# Sourced by ../patch.sh when ENABLE_EXT_MV2=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|uncompiled_sources_ = \[|&\n  "browser_action.json",\n  "page_action.json",|' chrome/common/extensions/api/api_sources.gni
sed -i 's/api::webstore_private::MV2DeprecationStatus::kHardDisable)));/api::webstore_private::MV2DeprecationStatus::kNone)));/' extensions/browser/api/webstore_private/webstore_private_api.cc
sed -i 's/bool g_allow_mv2_for_testing = false;/bool g_allow_mv2_for_testing = true;/' extensions/browser/manifest_v2_handler.cc
