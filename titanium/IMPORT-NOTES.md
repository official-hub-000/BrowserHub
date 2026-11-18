# BrowserHub — Chromium build (from Titanium Browser)

Upstream: https://github.com/jqssun/android-titanium-browser
Imported commit: `bde250ae7c7d43a7a1827a44d07841132f6db7f4` (2026-11-12)
License: GPLv2 (`LICENSE`, intact) · Vanadium pinned at `a8b304ef63b51f0158775a9d4f90106e8fc9a721`

> **This is no longer a pristine import.** The 27 upstream files were imported
> byte-identically, then rebranded to BrowserHub. The changes are identity and
> build-robustness only — no capability was removed, nothing upstream does was
> disabled. Everything below records exactly what changed.

## What the build does

```
build.sh → clone Chromium at a pinned tag → ~200 Vanadium patches
        → patch.sh edits → gn gen → ninja (arm + arm64)
        → sign → out/release/<version>-{armeabi-v7a,arm64-v8a}.{apk,aab}
```

The browser — UI, tabs, omnibox, renderer — is Chromium, fetched at build time.
These files *modify* it. Nothing here is Chromium's source.

## Rebranding to BrowserHub

| Change | File | Why it was needed |
|---|---|---|
| `chrome_public_manifest_package` → `com.browserhub.app` | `args.gn` | package id; was `io.github.jqssun.helium` |
| Launcher name → **BrowserHub** | `patch.sh` | The visible app name comes from Chromium's `android_chrome_strings.grd`, **not** from `args.gn`. Upstream never changes it, so an upstream build installs as *"Chrome"*, next to the real Chrome. Four `sed` edits cover the Stable/Beta/Dev/Canary name variants. |
| `Google LLC` attribution → `BrowserHub, based on Titanium Browser and Chromium` | `patch.sh` | A modified Chromium must not claim Google made it, and GPLv2 requires naming the origin. |
| New app mark | `res/icon.svg` | Shipping another project's logo is a trademark problem. The new mark is drawn to the **same contract** `res/icon.sh` depends on: 512 viewBox, a `disc` clip at r=208, exactly one `scale(1)` token, and the four palette placeholders it recolours by sampling the themed launcher background. `icon.sh` is unmodified. |
| Store listing text | `fastlane/...` | Said "Titanium Browser"; now says BrowserHub and credits Titanium + Chromium as required. |

Verified by `./verify-identity.sh` — 21 checks covering the identity strings,
the icon pipeline contract, and that every script still parses.

## Build-robustness fixes

Three upstream behaviours made a first build fail in confusing ways. All three
are now explicit:

1. **Missing signing keys.** `set_keys` decoded two empty env vars into empty
   files and failed much later inside `apksigner`. It now detects the absence,
   sets `SKIP_SIGNING`, and `build.sh` publishes `unsigned-*.apk` — so a local
   build works without credentials.
2. **Optional extension bundle.** `bundle.py` called `urlretrieve(url)` on a
   hard-coded third-party release asset; a `None` URL raised and took the whole
   gclient hook — and the whole build — down. It now stages nothing and exits 0.
   (Verified: stages a `{}` index and returns success.)
3. **Attribution/metadata drift** between `args.gn`, `patch.sh` and `fastlane`,
   now consistent.

## Building it — measured blockers

**It cannot be built in this environment.** Verified, not assumed:

| Requirement | This sandbox |
|---|---|
| Chromium source (~100 GB+) | `chromium.googlesource.com` → **unreachable** |
| ~150 GB free disk | **20 GB** |
| `sudo` for `install-build-deps.sh` | uid 1001, no root |
| Wall time | hours on a large runner |

This is **source-verified, not build-verified**. To build for real
(Linux x86_64, ~200 GB free, root):

```bash
cd titanium
git submodule update --init --recursive      # Vanadium; reachable, ~1 command
export LOCAL_TEST_JKS=<base64 of local.properties>   # optional
export STORE_TEST_JKS=<base64 of your .jks>          # optional
./verify-identity.sh                          # 21 cheap checks first
./build.sh                                    # hours
```

Upstream's `.github/workflows/build.yml` needs **self-hosted runners** and those
two secrets; it is left intact for that reason.

## The `vanadium/` submodule is empty here

`build.sh` needs Vanadium's `patches/`. That repository is reachable from this
sandbox (`github.com/GrapheneOS/Vanadium` → 200) so it is one command locally.
It is deliberately **not vendored**: it is a separate GPLv2 project, and copying
it in would blur which code is Titanium's, which is Vanadium's, and which is
BrowserHub's.
