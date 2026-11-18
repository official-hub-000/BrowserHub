# dev-viewport — Titanium feature block
#
# Make DevTools render usably on phone-sized screens.
#
# Sourced by ../patch.sh when ENABLE_DEV_VIEWPORT=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|<meta charset="utf-8">|&\n<meta name="viewport" content="width=device-width">|' third_party/devtools-frontend/src/front_end/entrypoint_template.html
sed -i '$a@media (max-width: 600px) { .settings-window-title, .tabbed-pane-header-tab-title { display: none; } .tabbed-pane-shadow.vertical-tab-layout .tabbed-pane-header-tabs { width: auto; } }' third_party/devtools-frontend/src/front_end/panels/settings/settingsScreen.css
sed -i 's|document_->GetSettings() ? |document_->GetSettings() \&\& !document_->IsViewSource() ? |' third_party/blink/renderer/core/css/resolver/viewport_style_resolver.cc
sed -i 's|kViewSourceLineWrappingEnabled, false|kViewSourceLineWrappingEnabled, true|' chrome/browser/prefs/browser_prefs.cc
