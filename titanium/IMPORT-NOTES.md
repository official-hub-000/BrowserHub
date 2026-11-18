# Titanium Browser (imported)

Upstream: https://github.com/jqssun/android-titanium-browser
Imported commit: `bde250ae7c7d43a7a1827a44d07841132f6db7f4`
Commit date: 2026-11-12
License: GPLv2 (`titanium/LICENSE`)
Vanadium submodule pinned at: `a8b304ef63b51f0158775a9d4f90106e8fc9a721`

All 27 files are byte-for-byte identical to upstream (verified by SHA-256 on import).

## What this repository actually is

It is **not an application's source code**. It is a *patchset and build
configuration* for Chromium for Android.

```
build.sh ──► fetch Chromium (a release tag) ──► apply Vanadium's ~200 patches
         ──► apply Titanium's own patch.sh edits ──► gn gen ──► ninja
         ──► sign ──► out/release/<version>-{armeabi-v7a,arm64-v8a}.{apk,aab}
```

The browser itself — every line of the UI, tabs, omnibox, renderer — is
Chromium, cloned at build time from `chromium.googlesource.com`. Nothing in
this repo is that code. `build.sh` fetches it; these 27 files change it.

| Path | Role |
|---|---|
| `build.sh` | The whole pipeline: clone Chromium at a pinned version, apply patches, build both ABIs, sign APKs and an AAB |
| `patch.sh` | 185 lines of surgical `sed` edits to Chromium source — the actual Titanium feature set |
| `common.sh` | Shared shell helpers; `set_keys` decodes signing keys from `$LOCAL_TEST_JKS` / `$STORE_TEST_JKS` env vars |
| `args.gn` | GN build arguments (`target_os = "android"`, `target_cpu = "arm"`) |
| `.gclient` | Chromium `gclient` checkout config + the filter-list download hook |
| `.gitmodules` | The `vanadium/` submodule (GrapheneOS/Vanadium, the GPLv2 Chromium patchset this builds on) |
| `extensions/` | GN rules, `bundle.py`, and `stage_bundled_extensions.inc` for bundling extensions into the APK |
| `res/` | App icon SVG + tint script + `themed_app_icon.xml` |
| `fastlane/` | Play Store metadata: descriptions and six phone screenshots |
| `.github/workflows/build.yml` | The CI that does the real build |

## What `patch.sh` changes (the feature set)

Reading the 185 lines rather than guessing:

- **Signature checks relaxed for bundled extensions** — `verifyPackage` forced to return `false` so the Titanium Extension loads.
- **Scheme guard on external intents** — three edits in `LaunchIntentDispatcherHooks` requiring `URLUtil.isNetworkUrl`, so non-web `intent://` links cannot reach the browser.
- **Bundled extensions staged at startup** via `external_pref_loader.cc`, so they are present on first run rather than after a sync.
- **Feature overrides forced on** (9 `EnableFeature` calls, including submenus in the app menu, Android DevTools frontend and media picker), plus **11 flags pushed past expiry** by rewriting `expiry_milestone` to `-1` in `flag-metadata.json` — `align-wakeups`, `android-bottom-bar`, `cct-open-in-browser-button-if-allowed-by-embedder`, `darken-websites-checkbox-in-themes-setting`, `enable-accessibility-sequential-focus`, `enforce-incognito-isolation`, `inline-pdf-v2`, `jump-start-omnibox`, `lock-controls-on-tablets-v2`, `offline-auto-fetch`, `use-fullscreen-insets-api`.
- **Omnibox site search enabled in prod** (ships disabled upstream).
- **Debloating**: removes Google AR Core requirement, privacy-sandbox attestation assets, and the language-settings / JavaScript-optimizer pref rows.
- **UI overrides**: Magic Stack educational-tip default flipped, `extractNativeLibs="false"`, PDF `application/pdf` intent filter added.
- **Build tweak**: `_is_base_module && !_omit_dex` in `build/config/android/rules.gni`.

## Building it — the honest status

**It cannot be built in this environment, and no amount of setup changes that.**
Three independent hard blockers, each verified:

| Requirement | This sandbox |
|---|---|
| Chromium source (~100 GB+) | `chromium.googlesource.com` → **unreachable** (only GitHub/PyPI/npm are routed) |
| Disk (~150 GB for a full arm64 + arm build) | **21 GB** total, 20 GB free |
| `sudo` for `install-build-deps.sh` | running as **uid 1001**, no passwordless sudo |
| Build time | several hours; the repo's own CI uses a dedicated large runner |

So this import is **source-faithful, not build-verified**. Anyone claiming a
green build from this directory would be guessing.

To build it for real, on a Linux x86_64 machine with ~200 GB free and root:

```bash
cd titanium
git submodule update --init --recursive     # fetches Vanadium
export LOCAL_TEST_JKS=<base64 of local.properties>
export STORE_TEST_JKS=<base64 of your .jks>
./build.sh                                  # hours; output in chromium/src/out/release/
```

Or simply use the upstream CI, which is already configured for this.

### The `vanadium/` submodule is empty here

`build.sh` needs Vanadium's `patches/` directory, which is populated by
`git submodule update --init`. That URL **is** reachable from this sandbox
(`github.com/GrapheneOS/Vanadium` → 200), so it is a one-command step locally:

```bash
git -C titanium submodule update --init --recursive
```

It was deliberately not vendored into this repository: it is a separate GPLv2
project with its own history, and copying it in would hide which code is
Titanium's and which is Vanadium's.
