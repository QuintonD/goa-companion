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
| surfaceHigh (surfaceVariant) | `#1A2E44` | `#EDE5D6` | raised rows, portrait frame top |
| verified | `#3FC1B0` | `#1B786B` | "Verified" badge (= secondary) |
| unverified | `#E8B04A` | `#8A5A00` | "Unverified" badge |
| engravedBorder | bronze 30 % | bronze 30 % | card/chip borders (decorative) |
| bronzeGlow | bronze 18 % | bronze 14 % | top sheen of frames/cards (decorative) |
| medallion | `#07101A` | `#E6DCC8` | bottom shade behind art/sigils (decorative) |

**Player palette** (`PlayerColors`, 10 swatches). `Player.color` stores the dark tone;
`playerColor(argb)` draws the theme tone, so names and crests read in both themes:

| Swatch | Dark tone | Light tone |
|--------|-----------|------------|
| Tide teal | `#3FC1B0` | `#186E62` |
| Bronze | `#D9B44A` | `#7A5E24` |
| Coral | `#EE7F6F` | `#B23F2F` |
| Sky | `#62A8F2` | `#2A62AE` |
| Amethyst | `#B892E6` | `#6B45A0` |
| Kelp | `#6CC27A` | `#2A7037` |
| Rose | `#EE85B8` | `#A83A70` |
| Ember | `#EE9A52` | `#9A4E0E` |
| Pearl | `#C3CCD6` | `#56616C` |
| Lime | `#AECF55` | `#55691A` |

Coral and Sky sit close to team red and team blue. Draft quick add skips them, and wherever a
player appears in a team context (draft board seats, completed draft) their name carries the
team's shape mark, so a coral player on Blue is never read as Red.

All foreground colours above reach ≥ 4.5:1 against both background and surface in their
theme (checked 2026-10-17). Note: dark-theme filled primary buttons need dark text
(`onPrimary` = `#0B1622`); white on bronze is only 2.4:1. Re-check any new pair. Team
identity is never colour-only — always pair with a label or shape (◆ red / ● blue).

Contrast checks added 2026-10-17 (WCAG ratio vs background / surface / surfaceHigh):
- Player palette: dark tones 6.9–11.2 / 6.0–9.8 / 5.2–8.5; light tones 5.0–6.1 / 5.8–7.0 /
  4.6–5.6. Every tone ≥ 4.5:1 on all three of its theme's surfaces.
- unverified `#E8B04A` 9.3 / 8.2 / 7.1 · `#8A5A00` 5.2 / 5.9 / 4.7.
- verified = secondary: light `#1B786B` is 4.25:1 on surfaceHigh — badges sit on surface or
  background only. Same caveat for light primary `#84662A` (4.29:1 on surfaceHigh): use it for
  icons/large text there, not body text.
- team red/blue (dark) on surfaceHigh are 4.1/4.2 — keep team-coloured *text* on background
  or surface; on surfaceHigh pair it with the shape mark.
- onSurfaceVariant on surfaceHigh: 6.6 (dark) / 6.1 (light).

- **Type:** display = Cinzel (titles, headlines, `GoaTextStyles.overline` section caps),
  body/UI = Inter. Static instances in `core/designsystem/src/main/res/font/`, licences in
  `third_party/` (see `third_party/ASSETS.md`). Numbers use tabular figures:
  `GoaTextStyles.timerDigits` (96 sp), `timerDigitsSmall` (32 sp), `numeric` (14 sp), all
  `tnum, lnum`. `formatDuration`/`DurationText` give `m:ss` / `h:mm:ss` with spoken descriptions.
- **Shape:** 12 dp cards, 28 dp sheets/dialogs, 10 dp chips; engraved border (1 dp bronze at 30%).
  `EngravedRule` (fading 1 dp bronze line) separates the top/bottom bars from content.
- **Motion:** `Motion` tokens — 200/250/300 ms, emphasised easing curves. Screen pushes fade
  and drift 1/10 width; predictive back uses the pop transition.
- **Spacing:** 4 dp grid (`Spacing`); screen padding 16 dp.
- **Large text:** above font scale 1.3 (`LARGE_FONT_SCALE`) compact rows stack (StatBar label
  over bars, theme selector becomes a radio list); icons beside text grow with
  `scaledIconSize` (capped 1.8×). Fixed label and table columns (StatBar labels, Meta table
  cells) grow with the font scale; the hero grid drops to two columns and a printed rules
  table becomes one block per row when a word would otherwise break mid-word.

## Components (`core:designsystem/component`)
`GoaScreen` (title bar + engraved rule, optional snackbar host) · `GoaCard` · `SectionHeader` ·
`StatBar` (1..8 base→max) · `FilterChipRow` / `GoaFilterChip` · `VerifiedBadge` /
`UnverifiedBadge` · `CardColorDot` · `ConfirmDialog` · `DurationText` · `EmptyState` ·
`HeroPortrait` (bronze double frame, inner gradient, sigil fallback; `framed = false` for the
grid tile, which draws its own fade, to keep scrolling cheap) · `HeroSigil` (monogram
fallback) · `PlayerAvatar` (crest in player colour, initial fallback) · `TeamBadge` / `TeamMark`
· `ComplexityStars` (`showLabel` adds Challenging / Complex / Very Complex / Wizard). The
gallery screenshot test (`GalleryScreenshotTest`) shows them all, dark/light, font scale 1 and 2.
`cardtext/CardTextView` renders printed card text (inline `GameIcons`, italic reminders, bold
keywords) for the card viewer and the board.

## Key screens
Catalog grid → Hero detail · Timer (full-screen, giant ring countdown, phase chip with
"Previous phase" beside it, turn/round pips; bottom controls are Pause and Next only; a running
Hurry up is pinned above them (under the dial in landscape); landscape puts the dial on the left half; paused = blinking
digits and a large "PAUSED" label; the **round view** alternative lists the whole round under
section headings — done rows ticked in bronze and stepped back by colour, never by alpha, so they
keep their contrast; the current row an expanded teal-bordered card whose border and digits turn
bronze in the final seconds and red in overtime; digits sized in dp so font scale 2 doesn't blow
them up; landscape = list left, clock and controls right) · Players list → Player profile (rating chart) · Match log/editor ·
Meta dashboard · Draft setup → Draft board (sticky step banner over a scrolling list of sequence
chips, two team columns with role coverage and ban chips, then a dense pool grid with search,
role chips and "Hide taken"; taken heroes shrink to chips; landscape moves the banner and teams
to a side column so the pool keeps the full height; long-press opens the hero peek sheet).
Bottom nav: Heroes, Timer, Draft, Players (original icons: shield, hourglass, fanned cards,
companions; solid variant when selected). Settings from top bar. The Heroes top bar carries
layout, Rules, Board, Card trivia and Settings; when the five icons and the title don't fit (narrow
screen or large text) Board and Card trivia move into "More options". The bottom nav is hidden on the
running timer (no tab to mis-tap beside Next; back leaves to Heroes), on the draft board and on a board;
while a session runs, every other screen shows the **timer running bar** above the nav (timer
icon, phase, "Round · Turn" context, tabular clock in tide teal; dim when paused, error red in
overtime; tap returns to the timer). The Meta tab defaults to a compact sortable hero table
(48 dp rows, sort chips; Pick/Ban columns drop at large font) with a Table/Cards switch.

### Board (sandbox, ADR-033)
The map is drawn by the app, never from the map art: pointy-top hexes turned in 30° steps so the
Lane runs up the screen in portrait (the bottom team's Throne Zone at the bottom; "Flip" swaps) and
left to right in landscape. Zone fills are muted (`BoardPalette`, derived from the theme for dark
and light): lane Zones alternate two tones, side Zones a sea-green tone, Throne Zones a team tint,
the current Battle Zone a teal tint with a teal border; other Zone borders are bronze, spaces are
separated by a hairline of the canvas colour. Terrain is a dark hex with an original faceted rock.
One ring of water hexes (the terrain around the board, p.19) is drawn faintly around the map
(`water`/`waterLine`). Zone labels (Battle Zone, "Lane 1 · Battle Zone" on a double-lane map,
Red/Blue Throne) float at a fixed size outside the zoom, so they stay readable; they sit just
outside their Zone's edge, under every piece and overlay, and take the first of their places that
is in view and covers no piece, highlighted space, Measure line or map button; with none free they
hide. **Original glyphs** (masters `design/board/glyph_*.svg`, path data mirrored in
`ui/BoardGlyphs.kt`): crown = hero Spawn point, sword = melee, arrow = ranged, shield = heavy.
Spawn points draw the glyph on the team's shape outline (◆ Red / ● Blue). Pieces: heroes are the
official portrait in a team-coloured ring, minions a team disc with the glyph, tokens a stone square
with the label; heroes and minions carry the team's shape at the bottom right, so team is never
colour alone. **Overlays** each have a pattern as well as a colour, and the switch that turns one on
shows its swatch (the legend): Movement = green fill and outline with the number of spaces (its
own colour, apart from the Battle Zone's teal: dark `#7BD88F`, light `#2A7037`, the player palette's
Kelp; green on the lane fills ≥ 4.5:1, the numbers are onSurface), Fast Travel = bronze hatching,
Range = violet inner outline, Radius = three dots; where a piece should go (a waiting minion's
targets, a minion's way back to its Battle Zone) = dashed teal outline; the selected piece = bright
outline on a dark halo; Measure = bronze line (dashed when not in a straight line). The selection
panel sits at the bottom in portrait (max half the height, collapsible) and on the right in
landscape. The board opens on the Lane between the hero spawn areas, hexes at least 38 dp across
(a double-lane map on its whole region); the view zooms and pans so the selected piece and what its
overlays reach stay in sight and tappable, clear of the panel, the tools and the Unverified badge;
"Fit" shows the whole map and turns into "Zoom to the lane". The card strip starts one card before
the chosen card and fades at an edge with more cards beyond. **New board** fits a phone without
scrolling: teams (pick chips, "Next pick" on the team that picks next), search with a segmented
Red | Blue switch beside it (under it at large text), a 58 dp portrait grid, and the Map menu beside
Set up in the bottom bar; the latest draft's or match's line-up sits on top as a card with Set up.

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
- Pipeline (as built): hand-authored SVG in `design/` → `python -P -E design/tools/svg2vd.py`
  → `core/designsystem/src/main/res/drawable/` (and `app/.../drawable/ic_launcher_*`). The
  converter is deliberately small (path/line/poly/circle/ellipse/rect, untransformed groups,
  user-space gradients) and refuses anything else, so output is predictable; `--check` reports
  stale drawables. Review renders in `GalleryScreenshotTest`; the SVG is the master.
- App icon: bronze-gradient heater shield bearing a navy trident, on a deep-ocean gradient
  with a teal glow; themed icon = shield outline + trident. The splash shows the foreground
  on the canvas colour and is held until settings (theme) have loaded.
- Sounds: original, CC0, or official if available; record the source in `third_party/ASSETS.md`.

In-app "About": credit Wolff Designa for game content and art; note this is a private build.

### Container colours (added 2026-10-17)
Material containers are set explicitly so components never fall back to Material's default
lavender. Contrast (on-colour vs container): dark primaryContainer `#3D3018`/`#F0DDB0` 9.6:1,
secondaryContainer `#123F3B`/`#BDEFE7` 9.3:1, errorContainer `#4D1C1C`/`#FFD9D6` 10.8:1; light
primaryContainer `#F1E2C0`/`#3A2A08` 10.8:1, secondaryContainer `#CDEDE7`/`#0B3B34` 10.0:1,
errorContainer `#F9DEDC`/`#410E0B` 12.8:1. Tertiary mirrors secondary.
