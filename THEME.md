# BrowserHub — GamersHub design system

BrowserHub now wears the GamersHub (Game Corner) visual language: warm near-black
surfaces, hairline depth, Oxanium display type, mono tracked labels, one accent
that drives everything, and a floating five-slot bar. **No feature, screen,
service, receiver, permission, ViewModel, repository, or business rule changed.**
This document describes the design layer only.

Provenance: the token values are ported verbatim from GamersHub
(`theme/GameCorner.kt`, `theme/RomKit.kt`, `ui/components/Parts.kt`,
`ui/icons/GhIcons.kt`, `ui/sheets/Sheets.kt`). See `THIRD-PARTY.md` and
`OFL-oxanium.txt`. All artwork is original geometry; nothing was extracted from
`frb.axeron.redcorner`.

---

## 1. Colour

### Base surfaces

| Token | Value | Used for |
|---|---|---|
| `GC.Bg` | `#05060A` | app background, window background |
| `GC.Surface` / `RomKit.Panel` | `#0D1017` | cards, sheets, the nav bar |
| `GC.SurfaceHi` / `RomKit.Panel2` | `#141926` | pressed/selected slot, chips |
| `GC.TagBg` | `#1A2030` | tag pills, dividers |
| `GC.Stroke` / `RomKit.Stroke` | `#232B3D` | **every** hairline |

### Type

| Token | Value |
|---|---|
| `GC.Text` | `#F2F5FA` |
| `GC.TextSoft` | `#B6BFD0` |
| `GC.TextDim` | `#8A94A8` |

### Status

`RomKit.Cyan` `#35E1F2` · `RomKit.Good` `#38E08B` · `RomKit.Warn` `#FFC857` ·
`RomKit.Bad` `#FF5C6C`

### Accent

Six fixed tones plus a custom slot. Index `0` is the default.

| # | Name | Value |
|---|---|---|
| 0 | Light red | `#FF6900` |
| 1 | Rose | `#FF6B6B` |
| 2 | True | `#FF4D4D` |
| 3 | Signal | `#FF2D2D` |
| 4 | Deep | `#E53935` |
| 5 | Ember | `#FF7A18` |
| 6 | custom | user-picked; bed = `Surface` mixed 22% toward accent |

Pair `#FF8A3D` · deep `#D95300` · `GC.OnAccent` (ink on a filled accent)
`#0A0A0A`.

Two helpers in `GameCorner.kt` do the contrast work for you:

- `readable(c)` — if relative luminance `< 0.34`, lerp 34% toward white.
- `inkOn(c)` — if luminance `> 0.42`, ink is `#17100A`, else `#FDF7F3`.

### Depth — one model

A single **1 dp hairline** (`GC.Stroke` / `RomKit.Stroke`). There is no
elevation, no shadow, no gradient anywhere in the app. The only exceptions are
the ember CTA and accent rims, both of which are accents rather than depth.

### Game Space

The `Void`/`InkPanel` variant in `GameCorner.kt` stays available and is used
only for landscape console surfaces, as in the source.

---

## 2. Typography

**Oxanium** (SIL OFL 1.1, variable `wght` 200–800) at `res/font/oxanium.ttf`,
43,536 bytes. Weights are selected with `FontVariation.weight` so real instances
are used rather than synthetic emboldening.

| Role | Size / spec |
|---|---|
| Display Black | 30 sp, tracking −0.5 |
| Bold | 20 sp, tracking −0.2 |
| SemiBold | 15 sp |
| Body | 14 sp / 20 sp line |
| BodySmall | 12 sp / 17 sp line |
| Caption | 11 sp |
| Label (mono) | 10 sp, tracking +1.0, **uppercase** |
| LabelSm (mono) | 9.5 sp, tracking +0.4 |
| Counter | 28 sp, tracking −0.5 |
| Log | 10 sp / 14 sp line |
| MonoValue | 12 sp |

Numbers, units, statuses and timestamps are **always** monospace. Labels are
**always** uppercase and tracked.

---

## 3. Shape

| Token | Radius |
|---|---|
| `RomKit.R18` | panels, sheets, nav bar |
| `RomKit.R16` | primary buttons |
| `RomKit.R12` | ghost buttons |
| `RomKit.R10` | wells, nav slots |
| `RomKit.RPill` | pills, grabber |

Spacing runs in `18 / 16 / 10`. The nav bar is 16 dp from the edges, 3 dp
inner padding, 1 dp border, 5 dp gap between slots.

---

## 4. Motion

One curve for the whole app:

```kotlin
val RomKitEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
```

| Duration | Value | Used for |
|---|---|---|
| `RomKit.Micro` | 180 ms | nav padding, small state |
| `RomKit.Standard` | 300 ms | outgoing content |
| `RomKit.Emphasized` | 420 ms | incoming content |

- **Tabs** — `AnimatedContent`: incoming slides in from `+it/8` over Emphasized
  while fading in; outgoing slides to `−it/10` over Standard while fading out.
- **Nav** — background tweens to `Panel2`, tint to accent, padding 15→18 dp over
  Micro; pressed alpha 0.80; icons 23 dp; mono 11 sp uppercase accent label.
- **Buttons** — press scale 0.86; ghost border at 40%.
- **Pop-ups** — all of them are `SheetFrame`: scrim `#99000000`, click to
  dismiss, bottom-anchored, `fillMaxWidth`, max 760 dp, R18, 18 dp gutter,
  navigation-bar inset plus 18 dp, and a 44×5 pill grabber.
- **Cards** — flip between states with no bespoke animation.

Springs have been removed from the app shell entirely (`grep spring(` in
`ui/BrowserHubApp.kt` returns 0).

---

## 5. Component kit

Ported to `ui/kit/` and `ui/sheets/SheetFrame.kt`; signatures unchanged from
GamersHub.

`PanelCard(modifier, accent, content)` · `SectionLabel(text, modifier, tint)` ·
`KeyValueRow` · `RkPill` · `RkDot` · `PrimaryButton` · `GhostButton` ·
`TruthLine` · `RkStepRow` · `RkLogLine` · `RkToggleCard` · `GhostRow(vararg)` ·
`AIcon(imageVector, contentDescription, modifier, tint, animated)` · `Toggle` ·
`TagPill` · `IconChip(size = 52)` · `BigButton(primary)` ·
`StatusPill(orange)` · `CircleIconButton` · `GaugeRing(diameter = 150)` ·
`SegmentedBar` · `SolidBar` · `Grabber` · `HeroMark` · `Card` · `Confirm` ·
`EngineSwitch`.

---

## 6. Icons

77 glyphs in `ui/icons/GhIcons.kt`, drawn on a 24u grid: no curves, miter joins,
butt caps, 45° chamfers, V chevrons, diamond nodes, a single 1.75u stroke.

Rendered through `AIcon`, which adds the state animation. If a glyph is missing
it is generated with `tools/icons_emit.py`, never substituted with an ad-hoc
Material icon.

---

## 7. Shell

Five-slot floating bar at the bottom: R18 `Panel` fill, 1 dp hairline, R10 slots,
23 dp icons, and the selected slot expands to show a mono uppercase accent
label. Content is full-bleed on near-black, laid out in `PanelCard` columns
under `SectionLabel` headers.

Tabs are **Lobby · Shields · AI · Tabs · Tools**, mapping to the app's existing
surfaces (see `INVENTORY.md`). Nothing was removed: surfaces without a natural
tab render as empty states inside their host tab.

---

## 8. Changing the accent

The accent is a single index plus an optional custom colour. `GC.custom` carries
the override and `LocalAccent` / `LocalAccentIdx` publish it to the tree.

```kotlin
BrowserHubGameCornerTheme(
    theme = StudioTheme.CYBER_CYAN,     // existing persisted user choice
    content = { /* app */ }
)
```

`StudioTheme` keeps all seven of its persisted values — none were deleted, since
it is a stored user preference (`data/model/BrowserModels.kt:76`). They are
mapped onto GC accents in `theme/GCBridge.kt`:

| `StudioTheme` | Accent |
|---|---|
| `AMOLED_BLACK` | 0 |
| `CYBER_CYAN` | 0 |
| `MULLVAD_EMERALD` | custom `RomKit.Good` |
| `ARC_VIOLET` | custom `#A855F7` |
| `NORDIC_FROST` | custom `#93C5FD` |
| `VIVALDI_CRIMSON` | 2 |
| `WARM_DUSK` | 5 |

To expose the six GC tones in the picker, add entries to that map — the theme
layer already accepts any index or custom colour.

---

## 9. What did not change

ViewModels, repositories, services, receivers, permissions, entitlement logic,
ad/cast SDKs, navigation targets, data flow, and the feature set are untouched.
This layer only replaces how things look and move.

---

## 10. Default accent is cyan

The default accent is **`#35E1F2`** (cyan), not the original light red.

**Why cyan and not a new colour:** it was already in the palette as
`RomKit.Cyan`, used as the secondary in both `darkColorScheme` calls. Promoting
an existing token keeps the design system internally consistent — nothing was
invented.

### How it is wired

Cyan is now **tone 0** in `GC.TONES`, which is what index `0` resolves to. That
matters because the default path is `StudioTheme.AMOLED_BLACK → index 0`, and
`LocalAccent` falls back to `GC.LightRed`, which is now `#35E1F2`. One change,
so every consumer — `primary`, `LocalAccent`, `accentNow()`, tag beds, icon
chips, gradient rims — follows automatically.

| # | Tone | Value |
|---|---|---|
| **0** | **Cyan (default)** | **`#35E1F2`** |
| 1 | Light red | `#FF6900` |
| 2 | Rose red | `#FF6B6B` |
| 3 | True red | `#FF4D4D` |
| 4 | Signal red | `#FF2D2D` |
| 5 | Deep red | `#E53935` |
| 6 | Ember | `#FF7A18` |
| 7 | custom | user pick |

**Nothing was removed.** The six ember tones all still exist and are still
selectable — they moved down one slot. `StudioTheme` values were remapped to
keep pointing at the same hues: `VIVALDI_CRIMSON` 2→3, `WARM_DUSK` 5→6.
`CYBER_CYAN` already meant cyan and still does.

Cyan's bed (`#0B2A31`) and pair (`#7BF0FA`) were derived to sit inside the same
warm near-black family, so `readable()` and `inkOn()` behave as they do for
every other tone.

Incognito still reads as a distinct mode: it keeps its own surface, background
and container values, and now shares the cyan accent rather than fighting it.

## 11. App icon

The old mark was a **512 px raster** (a cyan globe plus a purple cursor) sitting
inside an adaptive layer — it carried its own background, fought the background
layer, and went soft on launchers.

The new mark is drawn in the design system's own icon dialect: straight
segments only, **no curves**, miter joins, butt caps, 45° chamfers, flat-top
hexagonal geometry. It is pure vector at every density.

| Layer | File | Notes |
|---|---|---|
| background | `ic_launcher_background.xml` | `#0B2D36 → #071620 → #05060A`, same surface family as `GC.Bg` |
| foreground | `ic_launcher_foreground.xml` | cyan `#35E1F2` and `#7BF0FA`, mark within the r=33 safe zone |
| monochrome | `ic_launcher_monochrome.xml` | alpha-only white, identical geometry, for Android 13 themed icons |
| rasters | `mipmap-*/ic_launcher[_round].png` | 48/72/96/144/192, regenerated from the same geometry |

The launcher name and package are unchanged — this is a new mark for the same
app, not a rebrand.
