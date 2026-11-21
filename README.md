# BrowserHub

A fast, privacy-minded Android mobile web browser, cross-browser extension hub, and built-in Privacy VPN built with Kotlin, Jetpack Compose, Material 3, Room, and Android `VpnService`.

## Features
- **WebView & Smart Address Bar**: Integrated `WebView` inside `MainActivity` with a top omnibox URL/search bar, HTTPS/HTTP security indicators, VPN shield toggle, loading progress bar, Find in Page, Desktop Site toggle, and Share/External Browser actions.
- **Cross-Browser WebExtension Engine**:
  - Parses and extracts **Chrome (`.crx`)**, **Firefox (`.xpi`)**, **Opera (`.nex`/`.oex`)**, **Edge**, **UserScript (`.user.js`)**, and **`.zip`** Manifest V2 & V3 extension packages.
  - Injects matching `content_scripts` (JS & CSS) into live `WebView` pages (`document_start`, `document_end`, `document_idle`) using WebExtension URL `MatchPattern` rules.
  - Provides `chrome.*` & `browser.*` JavaScript APIs (`storage.local`, `runtime.getManifest`, `tabs.query`, `tabs.create`, `management`, `i18n`) and runs extension `default_popup` & `options_ui` HTML pages in an isolated virtual origin `WebView`.
  - Persists installed extensions, enabled states, and extension storage in Room (`BrowserDatabase`).
- **Official Extension Stores with Live Install Status**:
  - Direct links to **Chrome Web Store**, **Firefox Add-ons (AMO)**, **Microsoft Edge Add-ons**, **Opera Add-ons**, and **Greasy Fork** in PC Store Mode.
  - Updates native store buttons (`Add to Chrome`, `Add to Firefox`, `Add to Opera`, `Get`) to **Added to BrowserHub ✓** alongside a green **Installed in BrowserHub ✓** store banner without wiping store state, plus `chrome.management` & `navigator.mozAddonManager` polyfills.
- **Built-in Unlimited Privacy VPN (No Login, No Subscription)**:
  - Uses Android `VpnService` (`BrowserHubVpnService`) to create an encrypted DNS-over-HTTPS (RFC 8484) tunnel supporting **Cloudflare 1.1.1.1**, **AdGuard Privacy DNS**, and **Quad9 9.9.9.9** with zero bandwidth caps or accounts.
  - Filters known tracker & ad hosts at both DNS (`NXDOMAIN`) and `WebView` request layers, blocks cross-site third-party cookies, and enables WebRTC IP leak protection.
- **Multi-Tab, Bookmarks & History**: Persistent tabs, Private (Incognito) mode, customizable speed-dial shortcuts, searchable bookmarks, and browsing history.
