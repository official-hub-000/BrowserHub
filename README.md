# BrowserHub

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
