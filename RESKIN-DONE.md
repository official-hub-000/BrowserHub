# Re-skin done checklist

Proof that this was a re-skin and nothing was removed. Audited against
**`0f5c17d`** — the last commit before Step 0.

> The branch note names `f97dedb3` as the base, but that commit contains only a
> `README.md` stub. `0f5c17d` is the real pre-re-skin tree. Every number below
> is measured against it.

---

## 1. Nothing was deleted

```
$ git diff --diff-filter=D --name-only 0f5c17d HEAD
(empty)

$ git diff --diff-filter=D --name-only 0f5c17d HEAD -- '*.kt'
(empty)
```

**0 files deleted. 0 Kotlin files deleted.**

| | before | now |
|---|---|---|
| tracked `.kt` files | 49 | 58 |

Only additions: the ported design system.

## 2. AndroidManifest is untouched

| component | before | now |
|---|---|---|
| `<uses-permission>` | 5 | 5 |
| `<service>` | 1 | 1 |
| `<receiver>` | 0 | 0 |
| `<activity>` | 1 | 1 |
| `<provider>` | 0 | 0 |

## 3. Every screen surface still exists

All 21 root surfaces from `INVENTORY.md` are present in the tree:
`AboutDiagnostics`, `AddShortcut`, `AskAi`, `Bookmarks`, `ControlMenu`,
`CustomizationStudio`, `ExtensionPageDialog`, `ExtensionsOverlay`, `FindInPage`,
`GamesHub`, `History`, `NativeVideoPlayer`, `PageSuperpowers`, `PageTranslator`,
`QuickTabStrip`, `SearchEnginePicker`, `ShieldsUp`, `SpeedBenchmark`,
`TabVault`, `VideoGrabber`, `VpnOverlay`.

No surface was deleted and none was left unthemed — they all render through
`ModalSheetScrim`, which is now the GamersHub `SheetFrame`.

## 4. The one function that changed identity

A function-level diff found exactly one base function absent at `HEAD`:

```
app/…/ui/components/BottomControlBar.kt::BottomBarItem
```

This is **intentional and behaviour-preserving**:

- it was `private`, with no callers outside its own file — verified by grep;
- the GamersHub five-slot floating bar needs an expanding selected slot with a
  mono label, so the private helper was replaced by `private fun FloatingSlot`
  (`icon, label, selected, onClick, testTag, badge`);
- **all four original test tags survived** — `nav_home_button`,
  `nav_shields_button`, `nav_tabs_button`, `nav_menu_button` — and two were
  added (`nav_ask_ai_button`, `rail_tabs_button`).

It is a private layout helper, not a screen, service, receiver, or permission.
No user-facing surface was lost.

## 5. Feature set, data flow, business logic — untouched

| area | files | changed? |
|---|---|---|
| ViewModels | 2,617 lines | no |
| repositories | — | no |
| services / receivers | 1 service | no |
| permissions | 5 | no |
| entitlement logic | — | no |
| ads / cast SDKs | — | no |
| navigation targets | — | no |

The re-skin touches only: `ui/theme/`, the ported `theme/`, `ui/kit`,
`ui/icons`, `ui/sheets`, `ui/components/BottomControlBar.kt`, the motion specs
inside `ui/BrowserHubApp.kt`, the `ModalSheetScrim` shell, `themes.xml`,
`colors.xml`, and `res/font/oxanium.ttf`.

`StudioTheme` keeps all **7** of its persisted values — none deleted, since it
is a stored user preference. They are mapped onto GC accents in
`theme/GCBridge.kt` (see `THEME.md` §8).

## 6. Static verification

No Android SDK, `aapt2`, or `d8` exists in this sandbox, so a real build cannot
run here. What was checked instead:

- **brace balance** — 54 files, 0 unbalanced (`/tmp/kc.py` state machine);
- **symbol existence** — every referenced icon grepped out of `GhIcons.kt`
  (77 glyphs); `Spark`/`Layers`/`Grid` guessed wrongly earlier and were
  replaced with real ones (`AutoAwesome`/`Widgets`/`Tune`);
- **constructor arity** — `PanelCard`, `BigButton`, `StatusPill`, `AIcon`,
  `IconChip`, `GaugeRing` call sites match their signatures;
- **import resolution** — every added import checked against the file's needs;
- **token provenance** — type scale and shape values diffed against
  `theme/RomKit.kt` rather than the brief, and corrected where they differed
  (see §7).

Compiling on a machine with the SDK is the remaining step, and it needs
`google-services.json`, `debug.keystore`, and the Gradle wrapper — none of which
are in the repo (unchanged from before this work).

## 7. Where the sources beat the brief

The brief said to prefer the GamersHub sources on any conflict. Three places
mattered:

1. **`labelSmall` uppercase.** The brief describes labels as uppercase. Compose
   `TextStyle` has no text-transform property, so `textTransform = …` would not
   compile. GamersHub applies `.uppercase()` at the call site in `SectionLabel`;
   that is what is done here.
2. **Type weights.** The brief gave sizes; `RomKit.kt` gives sizes *and*
   weights/tracking. `MonoValue` is 12 sp **Medium with no tracking** and
   `LabelSm` is **9.5 sp, +0.4**, not the values first written. Corrected to
   match source.
3. **Icon names.** `Spark`, `Layers`, and `Grid` do not exist in `GhIcons.kt`.
   Real glyphs were used instead.

## 8. Screenshots

Static renders built directly from the ported token values (`GC`/`RomKit` are
parsed at render time, so the colours cannot drift from the app):

- `docs/reskin-compare-shell.png` — bottom bar + Lobby tab, before/after
- `docs/reskin-compare-sheet.png` — a `SheetFrame` pop-up with primary and ghost
  CTA, plus the rest/pressed CTA states

They are illustrations of the design layer, not device captures — the sandbox
has no emulator. The token values in them are read from source, not retyped.

## 9. The APK

The build now actually runs, on GitHub's runners (this sandbox has no JDK, no
Android SDK, and cannot reach `dl.google.com` or `maven.google.com`).

- **Release:** https://github.com/official-hub-000/BrowserHub/releases/tag/build-65
- **APK:** https://github.com/official-hub-000/BrowserHub/releases/download/build-65/BrowserHub.apk
- **Size:** 23,812,734 bytes · **Commit:** `72f0189` · **Run:** 44867710807
- Signed with a generated debug key — installs directly, not Play-ready.

To build it, CI supplies the three things the tree is missing: a
`google-services.json` (a real one from `secrets.FIREBASE_GOOGLE_SERVICES_JSON`
if present, otherwise a placeholder), a `debug.keystore` generated on the
runner, and Gradle itself (9.3.1, because AGP 9.1.1 requires it).

### What the real compiler caught that static checks could not

The earlier claim in §6 that brace/symbol checks were sufficient was too
confident. Six real defects only surfaced once Kotlin ran:

1. `GCBridge.kt` — `StudioTheme` used as a receiver without being imported.
2. `BrowserHubApp.kt` — `BrowserHubGameCornerTheme` and `BrowserHubSemanticLayer`
   called without imports.
3. `BrowserHubApp.kt` — `targetState == None` had `fadeOut` on the *enter* side
   of `togetherWith`, which is an `ExitTransition`; no overload applied.
4. `BottomControlBar.kt` — the 23 restored imports were appended *after* the
   code; Kotlin requires them at the top.
5. `ShieldsAndTabsSheets.kt` — replacing `ModalSheetScrim` sliced to the first
   `@Composable`, which was the parameter line, leaving 63 orphaned lines.
6. `SheetFrame.kt` — the port dropped the source's `@Composable` annotation.

All six are fixed. The lesson is recorded here rather than quietly deleted:
compile before claiming a build is green.
