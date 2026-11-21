# INVENTORY — BrowserHub, before the GamersHub re-skin

Written **before** any change, from source rather than from the README. This is
the contract: nothing listed here may disappear in the re-skin.

Captured at commit `0f5c17d` (branch `arena/20b7da89-browserhub`).

---

## Step 0 gate: toolkit

| Question | Answer |
|---|---|
| Is this app Jetpack Compose? | **Yes.** `androidx.compose` + Compose compiler plugin (`alias(libs.plugins.kotlin.compose)`), Material 3, `compose-bom`, `activity-compose`, `lifecycle-runtime-compose` |
| Can the GamersHub Compose design system be ported 1:1? | **Yes.** Same toolkit, same Compose BOM family, same minSdk (24). No hand-porting of shapes required. |

Gate **passes**. No toolkit migration decision is needed from the owner.

---

## Permissions (5) — frozen

| Permission | Declared |
|---|---|
| `INTERNET` | yes |
| `ACCESS_NETWORK_STATE` | yes |
| `FOREGROUND_SERVICE` | yes |
| `FOREGROUND_SERVICE_SPECIAL_USE` | yes |
| `POST_NOTIFICATIONS` | yes |

---

## Components declared in the manifest

| Kind | Name | Notes |
|---|---|---|
| Activity | `.MainActivity` | LAUNCHER + `VIEW` intent filters |
| Service | `.vpn.BrowserHubVpnService` | `BIND_VPN_SERVICE`, `android.net.VpnService` filter, `specialUse` FGS subtype `vpn_privacy_dns_tunnel` |

No receivers, no providers. Both entries are frozen.

---

## User-visible surfaces — 21 total

Every one of these is reachable from the app shell and must survive.

| # | Surface | Source | Tab / surface after re-skin |
|---|---|---|---|
| 1 | Hub Home (search engines, quick sites, tools) | `HubHomeScreen.kt` | **Home** tab |
| 2 | Omnibox header | `components/OmniboxHeader.kt` | **Home** tab (top chrome) |
| 3 | WebView pane | `components/WebViewPane.kt` | **Home** tab (content) |
| 4 | Quick tab strip | `components/QuickTabStrip.kt` | **Home** tab |
| 5 | Find in page bar | `components/FindInPageBar.kt` | **Home** tab |
| 6 | Shields up sheet | `ShieldsAndTabsSheets.kt` | **Shields** tab |
| 7 | Tab vault sheet | `ShieldsAndTabsSheets.kt` | **Tabs** slot |
| 8 | Search engine picker sheet | `ShieldsAndTabsSheets.kt` | **Home** (picker) |
| 9 | Modal sheet scrim (shared shell) | `ShieldsAndTabsSheets.kt` | replaced by `SheetFrame` |
| 10 | Ask AI sheet (+ chat/image/audio/video studios, quick tiles, bubbles) | `AiAndTranslatorSheets.kt` | **Ask AI** tab |
| 11 | Page translator sheet | `AiAndTranslatorSheets.kt` | **Ask AI** tab (sibling) |
| 12 | Control menu sheet | `ControlAndStudioSheets.kt` | **Menu** slot |
| 13 | Customization studio sheet | `ControlAndStudioSheets.kt` | **Menu** slot |
| 14 | Extensions overlay screen (+ page dialog, store rows) | `ExtensionsOverlayScreen.kt` | **Menu** → Tools |
| 15 | Bookmarks overlay | `LibraryOverlays.kt` | **Menu** → Library |
| 16 | History overlay | `LibraryOverlays.kt` | **Menu** → Library |
| 17 | Video grabber (media download) sheet | `MediaGamesDiagnosticsSheets.kt` | **Menu** → Tools |
| 18 | Games hub sheet | `MediaGamesDiagnosticsSheets.kt` | **Menu** → Tools |
| 19 | Speed benchmark sheet | `MediaGamesDiagnosticsSheets.kt` | **Menu** → Tools |
| 20 | About / diagnostics sheet | `MediaGamesDiagnosticsSheets.kt` | **Menu** → Tools |
| 21 | Web devtools pane | `MediaGamesDiagnosticsSheets.kt` | **Menu** → Tools |
| 22 | Page superpowers sheet | `PageSuperpowersSheet.kt` | **Menu** → Tools |
| 23 | VPN overlay screen | `VpnOverlayScreen.kt` | **Shields** tab (sibling) |
| 24 | Add shortcut dialog | `HubHomeScreen.kt` | **Home** |
| 25 | Native video player overlay | `components/NativeVideoPlayerOverlay.kt` | **Home** (media) |
| 26 | Bottom control bar (5 slots) | `components/BottomControlBar.kt` | **re-skinned as the floating bar** |

*(26 rows; the 21 "root surfaces" plus 5 shared/shell pieces counted separately —
all must remain reachable.)*

### Existing 5-slot bottom bar — the tab mapping

The app already has exactly five slots, which maps 1:1 onto the GamersHub shell
with no feature displaced:

| Existing slot | After re-skin | Contents |
|---|---|---|
| Home | **Lobby** | Hub home, omnibox, webview, quick tabs, find-in-page |
| Shields | **Shields** | ShieldsUp, VPN overlay |
| Ask AI | **AI** | AskAi, translator, studios |
| Tabs | **Tabs** | TabVault, quick strip |
| Menu | **Tools** | Control menu, customization, extensions, library, video grabber, games, benchmark, diagnostics, superpowers |

Nothing is deleted and nothing needs an invented empty state: every surface
already had a home.

---

## Feature engines (frozen — no logic changes)

| Engine | File | Owner concern |
|---|---|---|
| AI (keyless, Firebase App Check) | `ai/BrowserAiEngine.kt`, `ai/BrowserAiMediaEngine.kt`, `ai/AppCheckInit.kt` | frozen |
| WebExtension engine (crx/xpi/MV2/MV3) | `extensions/*.kt` | frozen |
| Shields / adblock | `privacy/ShieldsEngine.kt`, `privacy/TrackerBlocklist.kt` | frozen |
| Shortlink bypass | `privacy/ShortlinkBypassEngine.kt` | frozen |
| YouTube resolver | `privacy/YouTubeStreamResolver.kt` | frozen |
| Downloads / media | `downloads/DownloadAndMediaEngine.kt` | frozen |
| VPN (DoH tunnel, DNS codec) | `vpn/*.kt` | frozen |
| Page tools / superpowers | `browser/PageToolsEngine.kt`, `browser/PageSuperpowersEngine.kt` | frozen |
| Diagnostics | `diagnostics/DiagnosticsEngine.kt` | frozen |
| Games catalog | `games/GamesCatalog.kt` | frozen |
| Persistence | `data/**` (Room) | frozen |
| ViewModel | `ui/viewmodel/BrowserViewModel.kt` (2,617 lines) | frozen |

**Out of scope and untouched:** ViewModels, repositories, services, receivers,
permissions, entitlement logic, Firebase/App Check wiring, feature set.

---

## Baseline snapshot

To be quoted verbatim after the work in `RESKIN-DONE.md`:

```
git diff --stat 0f5c17d..HEAD
deleted files must contain: 0 screens, 0 services, 0 receivers
declared permissions must remain: 5
composable surfaces must remain: 21 root surfaces
```

## Colours currently in use (to be replaced, per the brief)

`ui/theme/Color.kt` holds BrowserHub's own OLED palette — `FlagshipCyan
#00D4FF`, `FlagshipEmerald`, `FlagshipViolet #A855F7`, etc. These are the values
the re-skin replaces with the GamersHub tokens. The app's **accent-switching
capability** is worth noting: GamersHub already has a 6-tone accent engine plus a
custom slider, so that existing capability maps onto the new system rather than
being lost.
