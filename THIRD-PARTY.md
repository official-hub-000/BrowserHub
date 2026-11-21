# Third-party code and assets

## Oxanium (font)

`app/src/main/res/font/oxanium.ttf` — variable weight, used for every display,
headline, title and body style in the GamersHub design system.

**Oxanium** is by Severin Meyer, licensed under the **SIL Open Font License 1.1**
(OFL-1.1). The OFL requires the licence text to travel with the font, so
`OFL-oxanium.txt` sits beside this file (attribution extracted from the font binary; the full OFL body is referenced, not reproduced).

OFL summary of the obligations that apply here:

- the font may be used, studied, modified and redistributed freely, including
  bundled inside an application;
- it may **not** be sold on its own;
- any derivative of the font must not use the Reserved Font Name(s);
- the copyright notice and this licence must be included with any redistribution.

> GamersHub itself ships `oxanium.ttf` without an OFL notice. The licence still
> applies, so the attribution is added here rather than inherited.

## GamersHub design system (ported source)

The following files are ported from
[GamersHub](https://github.com/official-hub-000/GamersHub) — first-party code of
this project, adapted by changing package names and import sites only:

| Origin | Ported to |
|---|---|
| `theme/GameCorner.kt` | `app/src/main/java/com/browserhub/app/theme/GameCorner.kt` |
| `theme/RomKit.kt` | `app/src/main/java/com/browserhub/app/theme/RomKit.kt` |
| `ui/components/Parts.kt` | `.../ui/kit/Parts.kt` |
| `ui/components/EngineSwitch.kt` | `.../ui/kit/EngineSwitch.kt` |
| `ui/components/Confirm.kt` | `.../ui/kit/Confirm.kt` |
| `ui/icons/GhIcons.kt` | `.../ui/icons/GhIcons.kt` (regenerated from `tools/icons_src.py`) |
| `ui/sheets/Sheets.kt` (`SheetFrame` only) | `.../ui/sheets/SheetFrame.kt` |
| `ui/icons` generator | `tools/icons_src.py`, `tools/icons_emit.py` |

Token values, geometry and motion constants are unchanged.

## Deliberately NOT ported

Per the mission's provenance guardrails, none of the artwork extracted from the
original Axeron game-bar package (`frb.axeron.redcorner`) is included:

- the intro films;
- the AXERON / GAME CORNER wordmark art;
- the crosshair vectors;
- any intro or logo SVG carrying extraction comments.

`GhIcons.kt` is generated vector geometry (`tools/icons_src.py`), not extracted
artwork, so it is safe to carry over. GamersHub's own `HeroMark` brand glyph was
**not** ported, because the brand stays BrowserHub's.
