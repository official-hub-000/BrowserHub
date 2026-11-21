# Titanium Browser, with switchable features

An exact clone of [Titanium Browser for Android](https://github.com/jqssun/android-titanium-browser)
plus a control layer for turning Titanium's own feature blocks on and off.

There are **no BrowserHub features here** and no BrowserHub identity. Upstream's
package id, app icon and store listing are untouched.

## What it is

Titanium is not an app's source code. It is a **patchset and build configuration
for Chromium**:

```
build.sh → clone Chromium at a pinned tag → ~200 Vanadium patches
        → patch.sh edits → gn gen → ninja (arm + arm64) → sign → .apk/.aab
```

Every part of the browser UI — tabs, omnibox, renderer, settings — is Chromium,
fetched at build time from `chromium.googlesource.com`. Nothing in this
repository is that code.

## Turning features on and off

Upstream applied all its edits in one 185-line `patch.sh`. The same edits now
live in `features/*.sh`, one file per feature area, and `patch.sh` sources the
ones you have enabled.

**Stock Titanium is the default** — every feature on, no config file needed:

```bash
./feature-picker.sh --list     # see all 22 features and their state
./feature-picker.sh            # interactive toggle
./feature-picker.sh ext-mv2=0  # specific changes
./feature-picker.sh --reset    # back to stock (all on)
```

Choices are written to `features/enabled.env`, which `build.sh` loads
automatically. You can also skip the picker entirely:

```bash
ENABLE_EXT_MV2=0 ./build.sh
```

A feature that is off contributes **nothing** — its file is not sourced.

### The features

| Key | What it does |
|---|---|
| `core` | The base build: icon reskin, `extractNativeLibs`, PDF intent filter, AR Core debloat, bundled-extension staging and signature bypass, flag unexpiring, omnibox site search, feature overrides, task manager |
| `accessibility` | Route accessibility through complex-user-interaction and touch-exploration state |
| `dev` | Task Manager and DevTools entry points (version-gated) |
| `dev-incognito` | DevTools on incognito tabs |
| `dev-viewport` | DevTools usable on phone screens |
| `playback` | Android media-playback guard neutralised |
| `ext-viewport` | `chrome://extensions` usable on phone screens |
| `ext-mv2` | Manifest V2 extensions allowed instead of hard-disabled |
| `ext-off-store` | Install extensions from outside the Web Store |
| `ext-toolbar` | Extensions toolbar button on the phone toolbar |
| `ext-popup` | Extension popups anchored correctly |
| `ext-fullscreen` | Extension popups sized to the viewport |
| `ext-pin` | Menu button hidden unless pinned |
| `ext-incognito` | Extensions in incognito; incognito as a window |
| `ext-priority` | Extension renderers kept at IMPORTANT priority |
| `ext-settings` | Extension settings on `desktop_android` |
| `ext-dialog` | Touch events not filtered in the install dialog |
| `ext-locale` | Locale handling for unpacked/content-URI extensions |
| `ext-ntp` | NTP URL overrides respected |
| `ext-app` | Legacy app API on `desktop_android` |
| `desktop-omnibox` | Desktop-style omnibox zero-suggest behaviour |
| `desktop-menu` | Desktop-style multi-window and incognito-window menu |

## Verifying it is really stock Titanium

```bash
./verify-identity.sh
```

20 checks. They assert the package id is Titanium's, no BrowserHub identity
survives anywhere, all 22 fragments exist and are non-empty, the fragment
statements still total the upstream count, and every script parses.

The split itself was verified by reconstruction: the fragments replayed in
order produce **120 of 120 upstream statements, identical**. If you disable
nothing, you get Titanium.

## Building

Unchanged from upstream, and **not buildable in this workspace**:

| Requirement | Here |
|---|---|
| Chromium source (~100 GB+) | `chromium.googlesource.com` unreachable |
| ~150 GB free disk | 20 GB |
| `sudo` for `install-build-deps.sh` | uid 1001, no root |
| Wall time | hours on a large runner |

The workflow in `.github/workflows/build.yml` is upstream's and needs
**self-hosted runners** plus `LOCAL_TEST_JKS` / `STORE_TEST_JKS`.

To build for real, on Linux x86_64 with ~200 GB free and root:

```bash
cd titanium
git submodule update --init --recursive   # Vanadium; reachable, one command
export LOCAL_TEST_JKS=<base64 of local.properties>
export STORE_TEST_JKS=<base64 of your .jks>
./feature-picker.sh --list                # confirm the feature set
./build.sh                                # hours
```

## Why no prebuilt APK could be delivered

The goal is a Titanium clone. Upstream publishes signed APKs, and delivering one
was attempted through every route available — all measured, none viable:

| Route | Result |
|---|---|
| GitHub release asset (`releases/download/...`) | 302 → `objects.githubusercontent.com` → **blocked** (`000`) |
| GitHub API asset endpoint with auth | 302 → same blocked storage host |
| `codeload.github.com` repo tarball | **works**, but nothing over git's 100 MB blob cap can be pushed through it |
| `api.github.com` binary blobs | capped at **100 MB**; the smallest of 61 releases' APKs is **179.6 MB**, the current arm64 is **320.1 MB** |
| Google Drive (connected app) | `googleapis`/`drive.google.com` unreachable (`000`) — the connector cannot move bytes the sandbox cannot reach |
| `gh release upload` back to GitHub | fails: `uploads.github.com` unreachable |

So a 179–320 MB prebuilt APK cannot cross into this workspace, and a Chromium
build cannot run in it. `build-elsewhere.md` covers the four ways to get one
built, including a Dockerfile.

## The `vanadium/` submodule

`build.sh` needs two things from it: the Chromium version on line 4
(`grep ... vanadium/args.gn`) and the patch stack it applies. Both are absent
until the submodule is checked out, so this is the first command after cloning:

```bash
git submodule update --init
```

It is pinned to `a8b304ef63b51f0158775a9d4f90106e8fc9a721`, the same commit as
upstream, and was **verified working** from a fresh clone: it checks out that
exact commit and delivers 322 files, 304 of them patches, after which line 4 of
`build.sh` resolves `VERSION=157.0.8103.78`.

Note the mapping lives in a `.gitmodules` at the **root of this repository**,
with `path = titanium/vanadium`. Upstream's `.gitmodules` says `path = vanadium`
because `titanium/` is upstream's repo root; here it is a subdirectory, and git
ignores a `.gitmodules` that sits in a subdirectory when resolving submodule
paths.

The submodule is deliberately not vendored: it is a separate GPLv2 project, and
copying it in would blur which code is Titanium's, which is Vanadium's, and
which is the build output.

## Licence

Titanium is GPLv2; `LICENSE` is the unmodified upstream text and upstream's
`README.md` (with its attribution to Vanadium/GrapheneOS and Chromium) is
preserved. Chromium is BSD-3-Clause, Vanadium GPLv2.
