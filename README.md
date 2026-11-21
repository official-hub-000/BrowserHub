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

---

## Third-party code

### `titanium/` — Titanium Browser for Android, with switchable features

An exact clone of [Titanium Browser for Android](https://github.com/jqssun/android-titanium-browser)
(commit `bde250ae7c7d43a7a1827a44d07841132f6db7f4`, 2026-11-12), plus a layer
that turns Titanium's own feature blocks on and off. Package id, icon and store
listing are upstream's, unmodified. It contains no BrowserHub features.

**License: GNU General Public License v2.0** — see `titanium/LICENSE`. The full,
unmodified license text is included as GPLv2 requires, and upstream's
`README.md` — which carries the attribution to
[Vanadium](https://github.com/GrapheneOS/Vanadium) by GrapheneOS and to
Chromium — is preserved unedited.

Of the 28 upstream files, 27 are byte-identical. Only `patch.sh` differs: it
was split into `features/*.sh` so each feature can be toggled, and the split was
verified to reproduce all 120 upstream statements exactly.

```bash
cd titanium
./feature-picker.sh --list    # 22 features, all on by default (stock Titanium)
./build.sh                    # needs ~200 GB, root, and a Chromium checkout
```

See `titanium/IMPORT-NOTES.md` for what the project is, the full feature list,
and how to build it.

### `release/` — BrowserHub v1.0.0 APK

A previously built, signed WebView Android app (`com.browserhub.app`), unrelated
to `titanium/`. Kept as a downloadable artifact; its source is not in this
branch.

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
