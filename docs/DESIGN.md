# Design

## Direction: "Steel and bronze" — the game's own look (ADR-042)
The app is styled on the official graphics and leans on them wherever they serve the player: the
cards are shown as the cards, heroes as their art and deck symbols, the board as its printed
symbols. Dark theme is the default (table lighting); light theme fully supported. Replaces "Sunken
bronze" (deep-ocean dark with bronze and teal), which stays as the canvas and the accents.

**Taken from the official look** (rulebook pages, card scans, card backs; ADR-042):
- **Steel banner** — the rulebook's page banner (steel-blue band with metallic edging) is every top
  bar (`GoaScreen`, the board screens' compact bar): a steel-blue gradient, a bright steel edge on
  top, a dark edge and a bronze hairline below, the title in engraved Cinzel caps in light steel.
  It stays dark steel in the light theme too, as on the page.
- **Copper small-caps heads** — the rulebook sets its section heads ("Place the Board", "Minion
  Battle") in small caps in a copper red. `SectionHeader`, the catalog's `SectionTitle` and the
  rules excerpts' printed headings do the same: Cinzel (whose lower case are small caps) in
  `heading` copper, with a fading copper rule.
- **The cards as printed** — card scans are the deck display, the viewer and the answer
  (`CardScan`: printed-card corners, a dark edge, a soft shadow); text the game prints (card text,
  rulebook excerpts) is set in a Minion look-alike (Crimson Pro) and card text sits on the cards'
  **parchment** text box (`ParchmentPanel`, `PrintedCardText`).
- **Deck symbols** — each hero's emblem from their card backs (`HeroSymbol`) beside the hero's
  name on the hero page, on catalog tiles and rows, and in the draft's hero peek.
- **Item icons** (the sketched icons at the foot of every card) where an item's stat is explained,
  the **Spawn point symbols** (hero gear/star, minion symbols) on the map, and the **Tie Breaker
  coin**'s faces on the draft's coin.

**Where we deviate, and why:**
- **Dark canvas, not the white page.** The rulebook is white paper; at a dim table a white screen
  glares and drains the battery. Dark navy stays the default canvas; light theme is a warm off-white.
- **Contrast.** Every pair is checked to WCAG (≥ 4.5:1 text), which the print doesn't need: the
  copper is lighter in the dark theme (`#E39B5F`) than the print's, the parchment dimmer (`#D9D2C1`).
- **Legibility at phone size.** The official item icons are grey sketches and the minion Spawn
  symbols of one team differ too little to tell apart at board size, so card text keeps the app's
  inline vector icons (`GameIcons`), minion pieces keep their original type glyphs, and a minion
  Spawn point gets a small outlined type mark (team shape + type glyph) on the printed symbol.
- **Spawn points stay markings.** On the table they are printed under the pieces; on a phone, a
  full-colour symbol reads as a unit, so they are faded, partly desaturated and smaller than a piece. Card names
  are repeated under a scan only while the scan is too small to read them (under 140 dp wide).
- **No commercial fonts.** Modesto and Minion are licensed fonts; Cinzel (Copperplate/Trajan
  caps) and Crimson Pro (a Minion look-alike) are OFL and stand in for them. Inter stays for the
  app's own words, so verbatim game text (Crimson) and our text (Inter) look different.
- **Team is never colour alone.** The official red/blue (copper gear / ice star) is kept, and the
  ◆/● shape marks stay wherever a team is shown.

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
| bannerTop → bannerBottom | `#26496A` → `#152C42` | `#2E5C7E` → `#1D3E57` | steel banner (top bars) |
| onBanner / onBannerVariant | `#F3F6F9` / `#C9D8E4` | same | banner title / banner icons |
| bannerHighlight / bannerShadow | `#A9CBE0` / `#0A1622` | same | banner edges (decorative) |
| heading | `#E39B5F` | `#9A4A1F` | copper section heads |
| parchment | `#D9D2C1` | `#EEE8DA` | card-text box |
| onParchment / onParchmentVariant | `#231C14` / `#54483A` | `#231C14` / `#5A4E3E` | printed text / reminders on parchment |
| parchmentInk | `#8A3F18` | same | inline game icons on parchment |
| parchmentEdge | `#8C826E` | `#B9AE98` | parchment border (decorative) |

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

Contrast checks added 2026-10-25 (ADR-042), enforced by `ContrastTest` (core:designsystem), which
fails the build when a token change breaks one:
- onBanner on bannerTop / bannerBottom: dark 8.6 / 13.2, light 6.6 / 10.3; onBannerVariant: dark
  6.4 / 9.8, light 4.9 / 7.7.
- heading (copper) on background / surface / surfaceHigh: dark 7.9 / 6.9 / 6.0, light 5.4 / 6.2 / 5.0.
- On parchment: onParchment 11.2 (dark) / 13.8 (light); onParchmentVariant 5.9 / 6.6; parchmentInk
  5.0 / 6.1.
- onSurface and onSurfaceVariant on background, surface and surfaceHigh, both themes (as above).

- **Type:** display = Cinzel (titles, headlines, `GoaTextStyles.bannerTitle` on the steel banner,
  `GoaTextStyles.sectionHead` copper small-caps heads, `overline` caps), body/UI = Inter, printed
  = Crimson Pro (`GoaTextStyles.printed` 18 sp, `printedSmall` 16 sp: card text, rulebook excerpts;
  the rules pages read it at 20 sp). Static instances in `core/designsystem/src/main/res/font/`, licences in
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
`GoaScreen` (steel-banner title bar, optional snackbar host) · `SteelBanner` / `Modifier.steelBanner`
(the banner for a bar of one's own: the board screens) · `GoaCard` · `SectionHeader` (copper
small caps + fading rule) ·
`StatBar` (1..8 base→max) · `FilterChipRow` / `GoaFilterChip` · `VerifiedBadge` /
`UnverifiedBadge` · `CardColorDot` · `ConfirmDialog` · `DurationText` · `EmptyState` ·
`HeroPortrait` (bronze double frame, inner gradient, sigil fallback; `framed = false` for the
grid tile, which draws its own fade, to keep scrolling cheap) · `HeroSigil` (monogram
fallback) · `PlayerAvatar` (crest in player colour, initial fallback) · `TeamBadge` / `TeamMark`
· `ComplexityStars` (`showLabel` adds Challenging / Complex / Very Complex / Wizard). The
gallery screenshot test (`GalleryScreenshotTest`) shows them all, dark/light, font scale 1 and 2.
`cardtext/CardTextView` renders printed card text (inline `GameIcons`, italic reminders, bold
keywords) for the card viewer and the board.
**Official art** (`component/OfficialArt.kt`, ADR-042): `OfficialArt` (paths of the item icons,
Spawn point symbols and Tie Breaker faces) · `HeroSymbol` (a hero's deck symbol in its own
colours, the sigil while loading or without one) · `CardScan` (a card scan as it lies on the table,
with a designed fallback; one TalkBack node with the card's values and text) · `ParchmentPanel` /
`PrintedCardText` (printed text on the card's parchment, in Crimson Pro) · `OfficialIcon` ·
`rememberOfficialBitmap` (for canvases). The gallery's `officialArt*` screenshots show them all.

## Key screens
Catalog grid (tiles carry the deck symbol at the portrait's corner) → Hero detail (the deck
symbol beside the name; the **Deck** is the official card scans in a grid by colour, in tier order
(I, II A, II B, III A, III B), as many columns of at least 100 dp as fit (three on a phone held
upright, a colour's five in a row held sideways; wider with large text), each named under it only
while it is too small to read its printed name, plus what the card doesn't print (where Takahide's
golds start, what a Handicap card stands in for); tap = the card viewer; Gydion's spellbook the same
with its landscape pages two to a row) · Card viewer (the scan large; under it only what the card
doesn't print — the Handicap basis, what its item icon gives, with the official icon — and "Read the
card as text", which shows the values and the text on parchment) · Timer (full-screen, giant ring countdown, phase chip with
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

### Board screens' frame (sandbox, game, puzzle)
The three map screens share two pieces of chrome in `feature/board/ui/` so the map gets the screen.
**Compact top bar** (`BoardScaffold`): `GoaScreen`'s look (the steel banner, back arrow, Cinzel
title in light steel) at 48 dp with an 18 sp title in portrait and 40 dp with a 16 sp title in landscape; it
grows only when large text needs one line of the title. A title too long for one line (a board named
after its line-up, "Arien, Brogan, Xargatha vs Wasp, Tigerclaw, Sabina") takes two lines at 13 sp
within the same height, portrait and landscape; with large text, where two lines would not fit, it is
cut with an ellipsis (TalkBack reads it whole). Icons keep 48 dp touch targets, overlapping
the bar's visible height. The content keeps clear of a display cutout the app shell left (landscape).
Other screens keep `GoaScreen`. **Hideable panel** (`HideablePanel`): shown, the panel (bottom in
portrait, end side in landscape) opens under a 40 dp strip with a drag handle and Hide (chevron down;
chevron toward the edge in landscape; "Hide panel"); drag the strip down (sideways in landscape) to
hide. Hidden, it leaves a **peek bar**: one line across the bottom in portrait (56 dp; in landscape a
248 dp card at the bottom end, text on up to three lines over the buttons, so the map, limited by its
height there, keeps its size) with what the panel holds ("✋ ● Wasp · Lane Zone 3 · 3 spaces from
Wasp"), a filled Confirm when a space or piece tapped on the map answers the question, and Show
(chevron up; "Show panel"); tap it or drag it up to show the panel. A question waiting for the person
gives the peek a teal border and a teal tap mark (TalkBack: "Your move"), and the peek is a polite
live region, so a new question is heard while the panel is hidden. Hiding fits the whole board into
the larger view once, unless the person has pinched or panned since the view was last framed; the
Fit button then reads "Zoom to the lane". While hidden the map no longer zooms to each question (the
whole board is in view), and the game hides the hero strip and the puzzle's goal banner (back with the
panel), keeping the HUD pill and, after the reveal, the turn track (compact: the turn's state is what a
player looks for with the board in view, ADR-041). Hidden or shown is kept per screen (`rememberSaveable`) and survives
rotation.

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
hide. **Spawn points** are the rulebook's printed symbols (p.6, `rememberSpawnArt`), drawn as markings on
the board, never as units: faded (50 %) and partly desaturated toward the Zone fill, 1.0 u across,
inside a piece's disc, so a minion or hero standing on one hides it completely (an occupied Spawn
point shows only its piece) and the pieces stay the most prominent thing on any space. A hero Spawn
point is Red's gear or Blue's star; a minion Spawn point carries at its lower right the type's
original glyph in a thin outline of the team's shape (◆ Red / ● Blue), no solid badge (the printed
symbols of one team are too alike at ~38 dp to tell the types apart; TalkBack names the Spawn point).
Until the symbols load, the original glyph on the team's shape outline. **Original glyphs** (masters `design/board/glyph_*.svg`, path data mirrored in
`ui/BoardGlyphs.kt`): crown = hero, sword = melee, arrow = ranged, shield = heavy. Pieces: heroes are the
official portrait in a team-coloured ring, minions a team disc with the glyph, tokens a stone square
with the label; heroes and minions carry the team's shape at the bottom right, so team is never
colour alone. **Overlays** each have a pattern as well as a colour, and the switch that turns one on
shows its swatch (the legend): Movement = green fill and outline with the number of spaces (its
own colour, apart from the Battle Zone's teal: dark `#7BD88F`, light `#2A7037`, the player palette's
Kelp; on the Zone fills 6.7–7.9:1 dark, 3.9–4.5:1 light, above the 3:1 a graphic needs; the numbers
are onSurface), Fast Travel = bronze hatching,
Range = violet inner outline, Radius = three dots; where a piece should go (a waiting minion's
targets, a minion's way back to its Battle Zone) = dashed teal outline; the selected piece = bright
outline on a dark halo; Measure = bronze line (dashed when not in a straight line). The selection
panel sits at the bottom in portrait (max half the height) and on the right in landscape, and hides
like the game's panel (see **Board screens' frame** below); hidden, its peek bar shows the piece
(token, name, Zone) with Deselect. The board opens on the Lane between the hero spawn areas with both teams' heroes in
view, hexes about 38 dp across (down to 32 dp to fit the Lane's length; the map's sides are what
gets cropped; a double-lane map on its whole region); the view zooms and pans so the selected piece
and what its overlays reach stay in sight and tappable, clear of the panel, the tools and the
Unverified badge; when the panel closes the map fills its place again (no band of empty water); a
push brings the new Battle Zone into view. "Fit" shows the whole map and turns into "Zoom to the
lane". Each space's TalkBack node is a square inside its hex (1.46 map units, so neighbours never
overlap and Android keeps each node whole). The card strip starts one card before the chosen card
and fades at an edge with more cards beyond. **New board** fits a 384 dp phone without scrolling:
one slot on top holds the latest draft's or match's line-up (a card with Set up) until the first
pick, then the teams (pick chips sharing the row, "Next pick" spelled out on an empty team, the
border and the switch otherwise); search with a segmented Red | Blue switch beside it (under it at
large text); a portrait grid with as many columns as it takes to fit every hero (tiles 42–64 dp,
each with the gap around it and the name under it, which shrinks to fit, one tap target of at
least 48 dp); and the Map menu beside Set up in the bottom bar. A finished draft pins Set up match, Log result later and Try on the board
under the teams on a phone held upright.

### Play against the computer (ADR-035, ADR-041)
The game table draws the sandbox's map (`MapBase`, Zone labels, `PieceToken`) and adds only what a
question needs. The official cards are the content: what a card prints is shown as the card, never
retyped; the app adds only what no card says (the turn's state, a rule, a verdict).
**On the board:** every space or piece that answers the question is the dashed teal target outline;
the tapped one gets the selection outline, and a move shows the engine's path as a bronze line with
dots at each step; a decision's other highlights (a card's range) use the violet Range outline; the
acting hero's space is outlined in bronze with a bronze **"NOW"** tag over its piece (fixed size,
outside the zoom; TalkBack hears "acting now" on the space); an immune minion is ringed with a dashed
teal circle (TalkBack: "immune"). Map taps select, a **Confirm** button with the choice spelled out
("Confirm · Lane Zone 3 · 3 spaces from Wasp") answers.
**Top, floating:** one HUD pill (each team's Life counters as heart + number by its shape, Wave
counters per Lane, "R2" and four turn pips: done filled, current ringed, later hollow; the Tie
Breaker coin as the showing team's shape on a bronze ring), then, before the reveal, the hero strip:
one chip per hero (portrait ringed in team colour, shape, name, Lv, coins, cards in hand, and a small
card back once its card is down, p.7: which card stays hidden). In landscape with the panel shown the
strip is portraits only, 48 dp discs on the HUD's row (team ring and shape; TalkBack hears the full
chip; a tap opens the hero); the side panel takes 42 % of the width (at most 400 dp).
**Turn track** (after the reveal, `play/table/TurnTrack*.kt`): the strip becomes the played cards
themselves, as they lie faceup in front of each player until resolved (p.7): one slot per hero in
acting order: the heroes done (in the order they acted), the acting hero, the next, the later ones,
then passes. Each slot is the official card (48 dp; Emmitt's second card peeking behind the first)
with the initiative as it counts in a badge over the printed one (bronze ring; "=11" in a teal ring
while tied with another hero still to act), the action a hero chose as the game's icon at the top
right, the hero's portrait in its team ring over the art (crossed out while off the board), its name
with the team's shape under the card, and the state in a word on a band across the card's foot:
**DONE** with a tick (the card stepped back under a 50 % canvas veil), **NOW** (bronze band, bronze
2 dp border), **NEXT** (teal band and border), **PASS** (no card: an empty outline), **OUT** (a card
that left the table unresolved); never colour alone. A card resolved facedown stays a card back. A
tap opens the card focus viewer, swiping through the turn's cards, each captioned "Wasp · Done ·
Movement · Initiative 10"; a long press (TalkBack: "Read about Wasp") opens the hero. The track stays
in view with the panel hidden and in landscape, compact there (32 dp cards, no names, DONE as a tick).
**Moments:** a surfaceVariant card with a bronze border at the top of the panel (a polite live region)
states the transition the table is at: "ALL CARDS REVEALED · Wasp acts first with initiative 10."
(ties: "Arien and Brogan tie on initiative 11: their team chooses who acts first (p.7)." or the
coin's side and who flips it; passes) until the first hero chooses an action; "TURN 1 IS OVER" with
that turn's cards on their slots while the next turn's cards are played; "END OF ROUND 1" with the
p.8 steps; "ROUND 1 IS OVER · Red won the push · Arien reached level 2". With the panel hidden and
the computer acting, the peek bar says the reveal ("All cards revealed · Wasp acts first …").
**Panel** (bottom, max 56 % in portrait; right side in landscape; hides to its peek bar, see
**Board screens' frame**): the header names the deciding hero and the kind in the team's colour
("◆ ARIEN · ACTION"), then the prompt in titleMedium (a live region). A question asked by a card
shows **the card beside its answers**: a third of the panel wide (96–150 dp; a landscape spell goes
above them) for buttons, 72 dp beside a board question's tap hint and Confirm; tap it to read it.
**Actions** are compact chips beside the card: the game's icon and the value the card prints
("Attack 6", "Movement 4"; the primary filled primaryContainer with "PRIMARY" above), only the rules
no card prints in a few words with the page (Fast Travel "To your Zone or an adjacent one, no enemy
hero or minion in either (p.11)", Hold "Do nothing (p.12)", Clear), and one line under them: "Only
the primary action applies the card's text (p.7)." Card questions (play, discard, retrieve, upgrade,
keep) show "Asked by <card>" (28 dp thumbnail) when another card asks, and the cards as official
images: the hand as 84 dp cards shrinking to 60 dp to fit five, two or three cards larger (140 / 110
dp, so Keep's two next-Tier cards sit side by side with their item stats), a card the question
doesn't offer dimmed with a block icon, and a teal "Tap a card to see it large; tap it again to …"
line; a tap opens the **card focus** viewer (below), which answers. **Defend** is an error-container
header with the attacker's card (56 dp, tap to read), "Arien attacks · melee", the Attack total, the
minion modifier counted and "Discard a card to defend" (it carries the prompt for TalkBack), then the
hand's cards themselves with the verdict under each in words with an icon ("✓ Holds" teal, "! Falls"
red, "Decided by its text" grey) and the Defense total with a shield when items or minions change
the printed value; "Don't defend" is chosen then confirmed. Declines and Pass are outlined. With large
text the asking card goes above the answers. The computer's move shows its portrait, level and prompt
and the card it resolves (56 dp, tap to read), a linear progress bar once it is "thinking…". Under
the question, the lines of what happened that the table doesn't show (an attack, a defense, a defeat,
coins, a push…), not the reveal, the action chosen or a move (the track and the board show those);
the log sheet keeps every line. The coach's nudge is a secondary-container card with "Choose anyway"
(outlined) and "Rethink" (filled). Game over leads with "You won" / "The computer won" in a card
bordered in the winner's colour, the rulebook reason, each hero's results, then the coach's turning
points by round and turn. New game reuses New board's picker; heroes not yet playable are veiled and
marked "Later".
**Spellbook** (Gydion, ADR-040; `play/table/SpellbookSection.kt`): the hero sheet and the review's Now
tab show a SPELLBOOK section after the discard, in the engine's order: "In your spellbook · 19" (the
spell cards, for Gydion's player only), "Cast · removed faceup · 3" (everyone), "Not yet added · 22"
(only before the first Prepare Spells, with "Prepare Spells adds every spell card to the spellbook.").
Anyone else sees "In the spellbook · 19" and "Facedown: only Gydion can look at the cards in the
spellbook." (a puzzle, whose hands are open, says "Open in this puzzle."). The cards are the official
landscape scans in a grid as wide as the sheet (columns of at least 104 dp, at most 4); the images are
the content, no card text retyped. The groups' counts are said there only: the sheet's number row
leaves out the engine's "spellbook" and "spellsRemoved" counters (it keeps "Wish casts"). A tap opens
the card focus viewer among that group. Cantrip's
"Choose and cast one spell" offers the spells as landscape cards that share the row's height
(shrinking down to 44 dp-wide equivalents to fit three), "tap it again to cast it", "Cast Magic
Missile". An unlocked Ultimate shows as its card ("ULTIMATE · p.16"), as it lies faceup on the
dashboard. **Card shape:** deck cards are portrait scans (729 × 1026), spell cards landscape (737 ×
510); `ui/cards/CardShape.kt` decides by the catalog id (`<hero>-spell-…`) and `CardShapeTest` checks
every bundled scan. A landscape card is never put in a portrait frame: in a row it keeps the row's
height and is about twice as wide (`TableCard`'s `width` is a portrait card's).

### Puzzles (ADR-035, PRODUCT §10)
The puzzle screen is the game table (above) plus: the **goal banner** floating under the hero strip
(surface at 94 %, bronze border, flag icon, goal in titleSmall, "You play …" under it; in landscape one
line, "goal · You play …", with a chevron to open it in full when it is cut); the panel opens
with "PUZZLE n OF m", the **difficulty mark** (three small hexes, filled up to the level, and the
word Hard / Harder / Hardest: shape and word, never colour alone), the title in Cinzel and the theme,
then "What you know" (surfaceVariant, folds to one line). A **nudge** is a secondary-container card
like the Coach's ("NUDGE 2 OF 3", ×, Another nudge); the last nudge's move is a **dashed bronze ring**
around its space on the map (apart from the teal options and the white selection; TalkBack: "hint").
The other side's replies appear line by line under "<HERO> ANSWERS" with a progress bar. A **miss** is
a quiet surfaceVariant card ("Not quite" in Cinzel, the reason, filled "Try again from before that
move", outlined Restart, text Show solution); never error red. **Solved** is a primary-container card
with a 2 dp bronze border and the original **seal** (a bronze hex with a tide-teal tick and six
points, drawn in code, opening out once with emphasised deceleration), then WHY IT WORKS and RULES.
The **replay** panel has Back / Next arrows around "SOLUTION · STEP n OF m", the move in titleMedium
(its card beside it), WHAT HAPPENED, and the explanation below. The list's numbers are Cinzel in a
bronze ring (filled when solved); Continue is a secondary-container card (all solved: the seal).

### Card focus (selecting and reading cards; `feature/board/ui/cards`)
One viewer for every card the board screens show (the Game state review's too): the cards a question offers (play, discard,
retrieve, defend with, upgrade, keep), the "Asked by" card, your hand, a hero's played, resolved and
discarded cards, a puzzle replay's card, and the sandbox's "Read the cards".
- **First tap** on a thumbnail: the card grows out of it (scale and translate from the thumbnail's
  bounds, 340 ms emphasised deceleration) into a large card over a near-black scrim (93 %, the same in
  both themes; light text on it). Portrait: × with the question's title ("WASP · CARD", bronze overline)
  and "Card 2 of 4" with page dots on the scrim, the card about half the height (38 % above font scale
  1.3), then a surface panel (28 dp top corners) with the question's context ("Discard a card", "Arien
  attacks · melee · Attack 6"), the name in Cinzel, tier and colour, the verdict ("! Falls · Defense 2
  against Attack 6", also as a pill on the card's corner), why a card isn't an option, what choosing it
  means (Keep: "… goes to your hand", "… becomes an item: +1 Defense (p.16)"; Upgrade: the two cards of
  the next Tier with their item stats), the printed values, the text verbatim, its item stat.
  Landscape: the card on the left at full height; on the right a 48 dp header and the panel down to
  the bottom edge, with the tier beside the name, tighter spacing, the card text in bodyMedium and no
  "Swipe up…" line, so on a phone held sideways the card's text shows without scrolling. The details
  scroll in both orientations, with a fade at the bottom while there is more. The scrim turns opaque
  behind the header (fading out just below it), so the screen's top bar never shows through.
- **Second tap** on the focused card confirms: a press (scale 0.93, 90 ms), a bouncy settle with a teal
  tick seal, the phone's Confirm haptic, then the answer goes out and the card fades away. The panel
  ends with "Tap the card again to discard Magnetic Dagger" (teal, touch icon) over a filled button
  saying the same ("Discard Magnetic Dagger", "Play Shock facedown", "Defend with …", "Upgrade …",
  "Keep …", "Measure with …"), and "Swipe up or go back to choose nothing." A card that isn't an
  option is veiled with a "Not an option" pill; a tap on it shakes it with the Reject haptic; its
  button reads "Not an option". While the game is busy the button reads "Wait…".
- **Swipe sideways** on the card for the others (a pager, neighbours peeking at 93 % scale and 55 %
  alpha, snapping); tap a neighbour to go to it.
- **Landscape cards** (Gydion's spells) keep their aspect: in portrait the card is the screen's width
  (minus the peeking neighbours) and short, leaving the panel more room; in landscape it may take 55 %
  of the width instead of 40 %. A mixed set (a deck card and a spell) shares one stage as wide as the
  widest card and as tall as the tallest, each card centred at its own largest size.
- **Close with nothing chosen:** the system back gesture (predictive: the card shrinks up to 14 % and
  drifts away from the edge as you drag, springs back if you cancel), an upward swipe or fling on the
  card (it flies up), a tap on the scrim, or ×. A downward drag only gives a little and springs back.
- **View only** (no question): the same viewer without the button; "Swipe sideways for the other cards;
  swipe up or go back to close."
- **TalkBack:** each thumbnail is one node ("Look at the card"); the focused card is one node with its
  values, verdict or reason and "card 2 of 4", its click action named after the choice, and custom
  actions Select, Next card, Previous card, Close; the Confirm button and × are ordinary buttons.
- It is its own full-screen window (a dialog without the platform dim or window animation), so it shows
  above the panel, a hidden panel's peek bar and sheets alike. One `CardFocusState` per screen
  (`rememberCardFocusState()`, `CardFocusHost(state)`); `open(cards, index, selection)` or `show(cards)`.

## Asset policy
Two kinds of asset (ADR-006, ADR-034):

**Official game assets** — allowed for game content.
- Card images (photographed full cards), card artwork used as hero portraits, card text and
  game iconography, imported from the sources in DOMAIN.md: hero deck symbols (source 1's logos,
  each checked against its card back), the item icons (source 1), and the hero/minion Spawn point
  symbols and Tie Breaker coin faces extracted from the pinned rulebook PDF (ADR-042). Stored under
  `app/src/main/assets/official/`, every file listed with its source in `official/SOURCES.md`.
- Card text contains inline icon tokens; render them with matching vector icons (redrawn
  originals in the game's icon style if no clean official vector exists).
- Show them as-is (crop and resize only); don't redraw or recolour official art.
- Present official art inside our own frames so it fits the app's look.

**Original app assets** — everything that is the app itself rather than the game.
- App icon: adaptive icon (trident-and-shield sigil on a deep-ocean gradient) plus themed
  monochrome layer.
- UI icons not in Material Symbols, frames, backgrounds, empty-state illustrations.
- Hero **sigils**: small geometric emblems per hero (24-unit grid, 2-unit stroke), now only the
  fallback when official art (portrait, deck symbol) is missing or still loading.
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

In-app "About": credit Wolff Designa for game content and art.

### Container colours (added 2026-10-17)
Material containers are set explicitly so components never fall back to Material's default
lavender. Contrast (on-colour vs container): dark primaryContainer `#3D3018`/`#F0DDB0` 9.6:1,
secondaryContainer `#123F3B`/`#BDEFE7` 9.3:1, errorContainer `#4D1C1C`/`#FFD9D6` 10.8:1; light
primaryContainer `#F1E2C0`/`#3A2A08` 10.8:1, secondaryContainer `#CDEDE7`/`#0B3B34` 10.0:1,
errorContainer `#F9DEDC`/`#410E0B` 12.8:1. Tertiary mirrors secondary.
