# Design

## Direction: "Sunken bronze"
Deep-ocean dark UI with weathered bronze accents and luminous teal highlights — ancient,
tactile, premium. Dark theme is the default (table lighting); light theme fully supported.

## Tokens (`core:designsystem`)
| Token | Dark | Light | Use |
|-------|------|-------|-----|
| background | `#0B1622` | `#F4EFE6` | app canvas |
| surface | `#122334` | `#FFFFFF` | cards, sheets |
| onSurface | `#E8E2D6` | `#1A1F26` | body text |
| onSurfaceVariant | `#A9B4C2` | `#4A5563` | secondary text |
| primary (bronze) | `#C9A35B` | `#84662A` | key actions, headings accent |
| onPrimary | `#0B1622` | `#FFFFFF` | text/icons on filled primary |
| secondary (tide teal) | `#3FC1B0` | `#1B786B` | progress, timers, selection |
| team red | `#E26459` | `#B83A30` | red team |
| team blue | `#4F8FE0` | `#2C63B0` | blue team |
| error | `#FF6B6B` | `#B3261E` | |

All foreground colours above reach ≥ 4.5:1 against both background and surface in their
theme (checked 2026-10-17). Note: dark-theme filled primary buttons need dark text
(`onPrimary` = `#0B1622`); white on bronze is only 2.4:1. Re-check any new pair. Team
identity is never colour-only — always pair with a label or shape (◆ red / ● blue).

- **Type:** display = Cinzel (OFL), body/UI = Inter (OFL); bundle via `res/font`, include
  licences in `third_party/`. Timer digits use tabular figures.
- **Shape:** 12 dp cards, 28 dp sheets; subtle engraved border (1 dp bronze at 30%).
- **Motion:** 200–300 ms emphasised easing; respect "remove animations" setting.
- **Spacing:** 4 dp grid; screen padding 16 dp.

## Key screens
Catalog grid → Hero detail · Timer (full-screen, giant ring countdown, phase chip,
turn/round pips) · Players list → Player profile (rating chart) · Match log/editor ·
Meta dashboard · Draft setup → Draft board (two team columns, pool grid, step banner).
Bottom nav: Catalog, Timer, Draft, Players. Settings from top bar.

## Asset policy
Private, non-commercial build (ADR-006), so two kinds of asset:

**Official game assets** — allowed for game content.
- Card images (photographed full cards), card artwork used as hero portraits, card text and
  game iconography (action/stat icons), imported from the sources in DOMAIN.md. Stored under
  `app/src/main/assets/official/`, every file listed with its source in `official/SOURCES.md`.
- Card text contains inline icon tokens; render them with matching vector icons (redrawn
  originals in the game's icon style if no clean official vector exists).
- Show them as-is (crop and resize only); don't redraw or recolour official art.
- Present official art inside our own frames so it fits the app's look.

**Original app assets** — everything that is the app itself rather than the game.
- App icon: adaptive icon (trident-and-shield sigil on a deep-ocean gradient) plus themed
  monochrome layer.
- UI icons not in Material Symbols, frames, backgrounds, empty-state illustrations.
- Hero **sigils**: small geometric emblems per hero (24-unit grid, 2-unit stroke) used for
  compact chips, draft tiles and as the fallback when official art is missing.
- Player avatars: 24 original abstract crests, colour-tinted by player colour.
- Pipeline: hand-authored SVG in `design/` → VectorDrawable via Android Studio's Vector
  Asset Studio, or in batch with `vd-tool` (Android Studio's converter) →
  `core/designsystem/src/main/res/drawable/`. Review the output; the SVG is the master.
- Sounds: original, CC0, or official if available; record the source in `third_party/ASSETS.md`.

In-app "About": credit Wolff Designa for game content and art; note this is a private build.
