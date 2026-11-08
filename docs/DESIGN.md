# Design

## Direction: the game's own look (ADR-053)
The app looks like the game. It builds on "Steel and bronze" below (ADR-042), and where the two differ this section wins.
Mock-ups: `design/mockups/original-look/`.

**The parts** (`core/designsystem/.../component/LookParts.kt`, `LookButtons.kt`; web: the end of `goa.css`, then a stylesheet per
area, `goa-look-*.css`). Every part falls back to a flat colour while its image loads:
- **Steel banner** (`GoaScreen`, `Modifier.steelBanner`): the rulebook's band redrawn (ADR-058), stretched, and its star medallion at the end at
  its own proportions, its disc toned into the band so it reads as ornament, not a control. Titles are `BevelText` (white metal
  with a dark outline). Compact bars (narrower than 380 dp, or text at 1.5x and more; `bannerCompact`) end in the frame's
  plain steel end instead of the medallion, so it never takes the title's or an action's room. At most two labelled glyphs
  on it (`BarActions`, see "Bottom nav" below), clear of the end (`BannerEndSpacer`, `bannerEndWidth`). Words as actions on it are `BannerTextButton`, light with a dark edge.
- **Slate plate** (`GoaPlate`, `Modifier.slatePlate`, `GoaCard`, `.goa-plate`, `.goa-card`): the dashboard's dark plate with a steel edge and
  small teal corner brackets. In the light theme the plate is the rulebook's white page: paper (`#FEFCF8` → `#F8F4EB`) with a steel edge
  (`#6F7C89` over a `#B4BEC8` rim), ink text, bronze accents, the same brackets. What sits on a plate is themed as the plate (`GoaPlateTheme`).
  Bottom nav, sheets (`GoaSheet`, never a bare `ModalBottomSheet`), search fields (`GoaTheme.colors.plate`) and the table's panels follow it.
  The board's sheets (the game's log and hero sheet, the sandbox's) dim what is behind them to 60 % black, and the
  game's open to 95 % of the height, their top at the top bar: the table's status panel is wholly behind them.
  **Dark in both themes by design** (`GoaDarkTheme`): the running timer, what floats over the painted map (`MapButton`, `SlateButton`),
  a card's own frame (`DesignedCard`), the hero dashboard. `PlateGround` is that slate's flat tone; a themed plate's is `GoaTheme.colors.plate`.
- **Plate styles** (`PlateStyle`): `Primary` (steel edge, brackets) for every grouped section; `Secondary` (flat, a quiet hairline, no
  brackets) only for one choice of several side by side (`SelectablePlate`); `Well`, a recess. A plate inside a plate draws as a well: a
  tone with no edge, so frames never nest.
- **Framing levels** (one rule, every screen, round 4): (1) the outer section plate, steel edge and teal brackets, on every grouped section:
  settings groups, More's groups, About's rows, a filter's groups, timer presets and setup choices, draft options. (2) Inside it no inner
  frames: rows are separated by spacing or hairlines (`PlateRowDivider`); a `SelectablePlate` or `GoaPlate(selected =)` inside a plate is a
  row of it: full width, no ground or edge; selected, the bronze wash and a 3 dp bronze bar at its leading edge with its radio
  (`selectionRow`); any other `GoaPlate` inside a plate is a toned recess without an edge. (3) A parchment title plate only for the
  screen's one heading (About's "Atlantis Companion", a hero's name in a sheet, "Phase timer"). (4) The gear-ended title plate only for a
  phase or an outcome heading (a timer phase, a pick, "Match over", "Draft complete", a decision). (5) A word tag inside
  a frame ("p.14", "Tonight", "Rulebook") is a `TintTag`: its word on a faint tint of it, never an outlined chip; a dial
  on a sheet ("LEVEL 2") is a well without an edge; a row of figures ("9 Rated matches · 1 Drafts with bans · …",
  `StatRow`) is one plate divided by hairlines, not a framed tile each; tiles of one choice side by side (the Play hub's
  scenes) keep the steel edge without the brackets. Exception: the hero dashboard's name plate ("Arien the Tidemaster")
  keeps the gear, as every parchment plate on the game table does; there consistency with the table wins over level (4)
  (round-5 gate). A team's rows on a plate (draft teams) are its faint tint without an edge, the column headed by its
  mark and name.
- **Title plate** (`TitlePlate`, `Modifier.titlePlate`, `.goa-title-plate`): the cards' parchment pill (redrawn, ADR-058; its ends from the art, the
  right one mirrored) growing with its text, and the cards' gear drawn as vectors at its own size (`TitlePlateSize`: 34 dp, 25 dp on a tile)
  over the pill's end, whatever the plate's height: steel teeth lit from the upper left, a dark face, a bronze hub. The words keep clear of
  the gear (only on a phase or outcome heading, see Framing levels; `gear = false` elsewhere). Content: a hero's name, what a question asks,
  a timer phase. A screen's one key title, not every heading. A plate holds a short title
  (a few words: "Minion Battle"); anything longer goes in the text box below it.
- **Text box** (`CardTextBox(PlateColor, cap)`, `.goa-text-box.<colour>`): the cards' parchment box in a frame colour with the word on its tab (dark on
  gold and silver, white on the rest): question text and the attack header in the asking card's colour, moments in gold, coach and nudges in green. The app's own words in a box are Inter (and its tab says "Note", never "Rule"); only text the game prints is Crimson.
- **Ribbons, counters, icons:** `ColourRibbon` on a card colour's section head (`SectionHeader(ribbon =)`); `LifeCounterIcon`, `WaveCounterIcon`,
  `CoinIcon`, `ActionIcon`, the official item icons as stats. **Team marks** are the official gear and star (`TeamMark`).
- **Buttons:** `GoaButton` (bronze metal with a sheen, the app's filled button), `MetalButton`, `GoaOutlinedButton` (the secondary button: a slate plate with a steel bevel; paper in the light theme), `SlateButton`
  over art. Disabled, a metal or outlined button is a filled, unlit slab with muted but legible words (`DisabledFace`, ≥ 4.5:1): clearly off,
  never a faded bronze and never an outline that passes for a text field. Say why under it (`DisabledReason`: an info glyph and a line).
  Slate buttons over art fade at 42 % (`Modifier.disabled`). Team-coloured draft buttons stay Material. A bronze glyph on a steel face is
  `SteelIconBronze` (6:1), never the theme's primary. **On the banner** a disabled glyph (undo with nothing to undo) is white at 50 % over a
  1 dp dark halo (`BarIconButton`), so it stays in sight.
- **Selection** (`SelectablePlate`, `GoaPlate(selected =)`, `Modifier.selectionFrame`, `SelectionMarkIcon`, `GoaFilterChip`,
  `selectableChipColors` / `selectableChipBorder` / `selectedCheck`, `selectableSegmentColors`): one look for every single and multi choice,
  chip, segment, plate or tile: the bronze `SelectedEdge` (1.5 dp) over a bronze wash (`selection`, `selectionFill` 20 %, 16 % light) and a
  leading check (a radio on a one-of-several plate). Unselected chips are small steel plates with a steel bevel (lit top, shaded foot).
- **Controls** (`Controls.kt`, round 3 of the hill climb): the game's own, never stock Material shapes. A **segmented bar**
  (`GoaSegmentedRow` / `GoaSegment`) is a recessed steel track with a bevelled edge, 48 dp high (each segment the whole
  height as its target); the selected segment is a bronze inset drawn inside it (wash, 1.5 dp edge, check). **Page tabs**
  (`GoaTabRow`: hero page, Players, trivia leaderboard) are one bar: tabs spread across the width, the selected one a
  bronze wash with a bold bronze word and a bronze rule, an engraved line under the bar, sideways scroll (faded) when
  large text no longer fits. A **switch** (`GoaSwitch`): off, a recess with a steel edge and a light thumb; on, a bronze track with a
  parchment thumb carrying a check. **Chips** (`GoaFilterChip`; a Material chip that can't be one takes `Modifier.chipSize()`, `ChipLabel`,
  `selectableChipBorder`): at least 48 dp tall, visibly (`ChipMinHeight`), with 8 dp above and below the words (`ChipLabelPadding`), so a
  chip grows with large text instead of squeezing it; the words wrap rather than cut; the leading glyph grows with the text
  (`ChipIconSize` × `scaledIconSize`). A group of chips that can outgrow the width is a `FlowRow` with 8 dp between lines. A **slider**
  (`GoaSlider`) and a **progress bar** (`GoaProgressBar`) are bronze for the chosen or done part on a neutral steel track
  (`ControlSteel.track`); teal never means progress. Steel edges and the on track are ≥ 3:1 on
  their grounds (`ContrastTest.controls`). Material's `SegmentedButton` stays only where the segment is a team colour (New board's Red | Blue).
- **Muted states** (`MutedStates.kt`, round 5): what is done, past, empty or off but still worth reading steps back by
  losing decoration, never by fading its words: no fill (or a lighter one), a thin edge in `outlineMuted` (≥ 3:1 on every
  ground), a tick for done (`DoneMark`, `DoneLabel`), and words in `textMuted` (≥ 4.5:1 everywhere, 5:1 on the plate and
  its well; `ContrastTest.mutedStates`). Never `alpha` on text and never a black scrim over words. A hero who has acted
  on the turn track has no well and no band: a 1 dp muted edge, the name, initiative and card in `textMuted`, a hairline
  and "✓ DONE"; a past draft step is unfilled with a muted edge, a tick and muted words (its team mark stays); an empty
  Turn slot is the slate with a dashed muted edge (`Modifier.emptySlotOutline`) and its number in `textMuted`.
- **Card family** (`CardFamilyLabel`): a card's family the same everywhere: the frame's ribbon (a dot for an Ultimate)
  and "Red · Tier II", "Gold · Basic" or "Ultimate" (`cardFamilyWords`), optionally after the hero's official deck
  symbol; the word always says it, never the colour alone.
- **Sideways rows** (`Modifier.horizontalEdgeFade(state)`, `fadingHorizontalScroll`, `EdgeFadeGutter`, `bleedHorizontally`): every row
  that scrolls sideways starts on the screen's gutter and runs to the screen's edge (`bleedHorizontally` lets it out of a padded column),
  and fades out at the edge that hides more, to a small steel chevron in a 14 dp strip at the very edge where nothing of the row shows (a
  mask and a cue, so it works on any ground; the chevron never sits on a chip or a card, and the row's 16 dp gutter is wider than the
  strip, so a row at rest loses nothing to it): the catalog's
  controls, the rules navigator's shortcuts, the board's card rail, the draft's step strip and role chips, the table's hero and
  turn strips. A finite group of about six or fewer wraps instead (`FlowRow`, `FilterChipRow` does it by itself): meta's
  sort chips, the deck's colour and tier filters.
  A page over a pinned bar fades into it while more is below (`bottomEdgeFade`: Game options over its coin bar).
- **Ceremony is for the game's states.** The title plate's gear (`titlePlate(gear =)`, `TitlePlate(gear =)`) marks a phase or an
  outcome; a catalog tile's name sits on the gearless plate, so an ornament never passes for a control. Grouped sections, More, Settings
  and About included, are the bracketed section plate (Framing levels).
- **Bronze on cream:** a symbol, star or check that carries meaning uses `bronzeInk` (the bronze in the dark theme, `#6A4A12` in the
  light one, ≥ 4.5:1 on paper and canvas), never the pale bronze of a fill.
- **Team words and team marks** (round 4): a word in a team's colour (a team's name, an actor heading) uses `teamTextColor(team)`
  (`teamRedText` / `teamBlueText`: `#F29488` / `#8DB9F5` dark, `#8E2A21` / `#1E4C8F` light; ≥ 6:1 on the plate, ≥ 4.5:1 on every
  ground), as legible as the body text beside it. `teamColor(team)` stays for marks, accent bars, rings and fills (≥ 3:1 on every
  ground, cream included). The operative instruction ("Choose an action") is body colour, with the team's mark beside it.
  `ContrastTest.teamWordsAndMarks` checks both.
- **Hero ratings:** a hero's four profile figures are titled **Hero ratings · 1–8** wherever they show (the hero page's plate, the draft's
  peek; `HERO_RATINGS_TITLE`) and end in one shared note, "Base → upgraded · 1 very poor, 8 exceptional" over "Comparative ratings, not card values" (`HeroRatingsNote`), so
  they never pass for numbers printed on a card.
- **Stat readout** (`HeroStatBlock`, `StatReadout`, `StatIcon`): one readout everywhere: the game's item icon, the value as base → max, the
  name. Tiles on the hero page, with the 1..8 bar in the draft's peek (`StatReadoutStyle.Bar`, the card's printed order), one line each on a
  sheet over the map (`Compact`, a card's printed values).
- **Heads:** `SectionHeader` is the one section head (Cinzel small caps in copper, a fading copper hairline, an optional action; a card colour's
  section may carry its ribbon). Inside a plate, `SubHeader` (Inter 13 semibold, muted, no rule).
- **Numbers:** ratings and scores are `GoaTextStyles.score` / `scoreMedium` / `scoreLarge` (Inter semibold, lining tabular figures) on every
  screen; Cinzel stays for headings.
- **Bronze and teal:** bronze is selected and primary; teal is next, correct, "you can act".
- **Card backs:** a facedown card shows its hero's own back (`official/heroes/<id>/back.webp`).

**Tokens changed:** dark `surface` `#1E2327`, `surfaceHigh` `#2A3136`, `onSurfaceVariant` `#B4BEC8`, team red `#E76C61`. `LookArtContrastTest` checks text
against the art itself: the slate at its lightest 1 %, the parchment at its darkest.

**The board** (`feature/board/map/MapLayers.kt` `PaintedMap`; web `play/map/`): replaces the zone fills, water and terrain described under "Board (sandbox)"
when a map has `art` (all four do); the drawn map stays as the fallback while loading.
- The painting sits under the hexes; its own edge is `art.sea`, one dark sea on all four maps (dimmed, it is the canvas), so it has no visible edge in any rotation. The Battle Zone is full colour, the rest dimmed (brightness about .58, saturation
  .5), with a thick white edge on a dark halo; rocks enclosed by it are lit and not outlined. Zone labels are bevelled words on a slate pill (80 %), set clear of every piece (a miniature stands a space tall above its own). A Battle Zone's label is never hidden: with no free place outside the Zone it takes the free spot nearest the Zone along its edges or the view's top and bottom edge (over a little empty map, never a piece).
- **Spaces a question offers take the light** (`MapLighting`): lit, white-edged, a move's step count as a white numeral in a plain dark disk (round; a list letter is a teal rounded square), the chosen one bronze; the Battle Zone keeps a thin line
  meanwhile. Occupied spaces you can't end on stay dark. Range and Radius stay patterns. Overlays use `ArtBoardPalette`, the same in both themes, each with a dark halo.
- **Board style** (Settings → Appearance; `LocalBoardStyle`): Miniatures, or Grid and discs (hex hairlines, discs with the type's glyph). Miniatures stand on a team-coloured base
  with a round type badge drawn above every figure, and on the badge's upper right the team's official gear or star on a dark badge nearly as large, as every hero carries it; the badge grows a little with large text (1.15× at most, so it never runs into a Zone label, which treats it as an obstacle) and never shows under 9 dp radius on screen however far out the map is (`MiniatureBadge`); a Heavy is larger. A disc (Grid and discs) carries the same gear or star. Team on a minion is never colour alone; TalkBack names it.
- **Hero tokens:** portrait in a team ring with the mark on a dark badge. Max zoom on art maps: 88 dp a hex.
- Spaces in `art.uncovered` have copied ground (ADR-053); only their Spawn symbols and Zone edges are drawn.

**Screens:**
- **Table:** HUD on a plate with official counters and bronze diamond pips (done: solid, dimmer; current: solid, bright, ringed; later: hollow), each group named over it (LIVES, WAVES, TURN, TIE BREAKER), numbers 20 sp; hero chips on plates; panel on rounded slate with the mark and name on its top strip; the hand in a gentle fan
  (3.2° a card, the outer ones lower), each still one target and one TalkBack node; the turn track one row of equal cells on the HUD's own plate under a hairline (the **score bar**, about 140 dp with the HUD), never over a piece (portrait and initiative, every hero's name and card, a thin state band); sideways a column beside the map's start edge. The panel ends just past the question, what follows it scrolls; the hand is a tab pinned at its foot ("Your hand · 5"). Minions to choose are full-width rows: team mark, type badge, what tells them apart first; minions that read alike are lettered A, B, C (nearest first) on the map and at the head of their rows.
- **Puzzle:** the goal and the puzzle's name on small lines at the panel's head, off the map; the title plate is the question's; "What you know" starts folded to two lines.
- **Hero sheet:** the dashboard (p.16) redrawn at the phone's density (ADR-058: the upscaled print, `official/ui/dashboard-frame`, was soft at this size; the web still uses it): a steel band with a bronze hairline and the plate's teal brackets, "Hero the Title" on the cards' title plate, a parchment **TURN n** tab over each of four slots, the played cards in them. Tigerclaw's art is the rulebook's; other heroes' portraits sit under the frame, cropped around the
  head and darkened toward the edges; either way the painting is a backdrop under a slate veil (60 %). An empty slot is the slate (55 %) with a dashed muted edge and its turn's number in Cinzel in `textMuted` (Muted states), 18 dp at the 411 dp reference width. The dashboard's dials are in the header (**LEVEL 2**, coin **COINS 2**) on wells without an edge (the sheet is the frame), never bars; the frame's name plate keeps the gear, as the table's other plates do. Items as icons with "+n", the Ultimate, the hand or card backs.
  A marker with a Turn slot (Snorri's runes, ADR-054) lies on a strip under its slot (only when a slot has one): .053 of the frame's width, the active one .063 with a bronze ring and "ACTIVE" beside it, never by colour alone. "Runes" under the frame says it in words. The active rune also rides on the hero chip (bottom left of the portrait) and over the hand.
- **Heroes grid:** slate tile, name on the restrained title plate (no gear: on a tile it passed for a control or a team mark; the gear and star stay for teams), portrait with the deck symbol at its top left, italic Crimson title, stars at 14 dp in bronze leading the text (a ranged hero has the game's Range icon beside them), then two roles in the muted tone, one a line at 15 sp (the title 15 sp too). The name is 15 sp and steps down to 14 sp at the least for a long one ("Silverarrow"), never further: the grid's columns are as wide as the longest name needs at 14 sp at the current font scale (`rememberTileNameWidth`), so three columns on a 411 dp phone at 100 %, two on a 384 dp one, fewer with larger text (above 130 % the floor is 19 dp, so 200 % keeps two columns). No product chip on a tile: it is on the hero page and in the filter. Every row keeps its height, so the tiles of a line are one height. A hero a search found through a card says so in one compact line under its portrait, never over the art: "Matches: Runic Dagger", the name in `CardTitle`'s Crimson (whole, wrapping to a second line; every tile of the results keeps the two lines; TalkBack says "Card: …"); the list row has the same line under its roles. Unfilled stars are `glyphMuted` (≥ 3:1); the list row names the complexity beside them. Grid or list is a two-segment switch at the end of the result count (both glyphs shown, the current one selected), not a banner glyph. The closed Filters chip counts what is applied ("Filters · 3"); each applied filter follows as a removable chip, and "Owned only" when the collection (Settings) hides heroes; the row fades at the edge that hides more. A search that finds cards and heroes says so in one summary ("6 cards · 4 heroes") and shows **All · Cards n · Heroes n** (a segmented bar; the tabs own the counts, the section heads under them are bare); All lists the first three cards, "All n cards", then the heroes, so both kinds are on the first screen. A card result's name is `CardTitle`.
- **Hero page:** art edge to edge fading into the canvas (dashboard art or the portrait cropped toward the top), symbol and name on the plate, stats as icons with base → max on a plate; the banner always names the hero. With large text the stars and roles run across the width under the plate.
  Under the header **Overview · Deck · FAQ** are true tabs (the app's one tab bar, `GoaTabRow`, as on Players and the trivia leaderboard; sticky; the tab you are on selected in bronze: wash, word and rule; each keeps its own scroll). The Overview leads with what the hero does: the art, the ratings, then **Starting cards**, the five the hero starts with (`StartingCards`), each a card row (ribbon, `CardTitle`, kind, its whole text, Initiative; a tap opens it); then your table (with no games yet one line, "No games logged with Arien yet", and an outlined "Log a match" beside it that opens a new match with the hero in its first seat), lore (a quotation: Crimson italic, 19 sp, behind a bronze rule) and one quiet line for the data ("Data: Stats of Atlantis · Verified"; each project and its address are in About → Data sources, the pinned versions in docs/DOMAIN.md). The Deck and FAQ open with a compact identity (symbol, name plate, stars and roles) instead of the art. On the Deck two filters ride under the tabs (sticky), each one choice with its "All" stated and selected when nothing is narrowed: **All colours** then the parts, wrapping onto a second line rather than off the edge, each with its colour's ribbon and count (Basic, Red, Green, Blue, Ultimate, Handicap, Spellbook; selected in the one selection look with a check), and under them **All tiers · Tier I · Tier II · Tier III** in the same chips; the count over the cards names what they leave ("Red · Tier II · 2 cards"). The scans are two across a phone (at least 160 dp each), each named under it (`CardTitle`, Crimson 15 sp); press-and-hold shows a card full screen (at most 1.6× its pixels; a tap or Back closes it; TalkBack: "Show full screen"), a tap opens the viewer. **Deck count:** one model everywhere: the deck's own cards, the Handicap card named apart: "18 cards + Handicap" over the deck, "3 of 18" in the card viewer, "Handicap" on the Handicap card, "Spellbook 2 of 12" on an extra. `HeroDetailRoute.DECK` / `FAQ` / `SPELLBOOK` open a tab.
- **Text that carries information** (roles, card captions, counts, notes, metadata pills) is at least 14 sp and wraps; it never shrinks to fit.
- **Rules:** the topic list is sentence-case Inter semibold (17 sp) for scanning, the topic page keeps the book's Cinzel heads. A long topic title gives the banner its head ("Turn sequence") and opens the page whole, in Cinzel. A topic with three or more stops has a sticky **section navigator** of chips: the rulebook's own heads verbatim, "Diagrams", and (in a topic with few heads) its headless boxed passages by their opening words; the passage you are reading is always the bronze chip (the first section above the first passage) and is kept scrolled into view, the row uses the shared edge fade (`horizontalEdgeFade`). The row starts with a labelled **Sections** button (list glyph and the word, 48 dp; TalkBack "Sections, 5 parts") that opens a sheet listing every section by its whole name, the current one selected; the chips after a hairline are shortcuts, so a later section is never found by scrolling sideways through cut labels. The banner keeps only Back. The whole title is set compact (Cinzel 20 sp), so the rules start on the first screen. The reader's page (`GoaTheme.colors.page`, `onPage`, `pageInk`, `pageEdge`) is the parchment in the light theme and a dark parchment `#221E19` with warm light Crimson ink `#EAE0CC` in the dark theme, so opening a passage never flashes a bright page at a dim table (`ContrastTest.readerPage`). Copper heads; passages in the box the rulebook prints them in: blue `#0F6F99`, green `#2B7A33` and orange `#A8560F` (the print's green and orange darkened to 5.3:1 and 5.2:1), a shade deeper on the dark page (`#0C5778`, `#225F28`, `#86440C`). A long topic with diagrams opens, before its first passage, with an anchor row: **Steps** (when a passage prints numbered steps) and each diagram as a thumbnail, each a tap from its place on the page (`topicAnchors`; pointers only, no words of ours). Pushing the Lane has the p.8 diagrams, each over its printed caption (`RulesDiagrams`; verbatim, in italics, checked against the PDF as any excerpt): multiplied into the light page; on the dark page a framed plate in a muted parchment. Their soft map is the PDF's own: the map art is embedded at about 100 ppi, so no higher render exists (only the vector labels sharpen). The topic list is one plate with dividers. A topic's foot is the steel band; sideways and with large text a plain line at the page's end instead. The ladder (Levels & defeats) is a bottom sheet: its drag handle, then its title with the pages and a Close button; a picked level key is solid bronze with a check (read at a glance), and that level's three numbers are the default view. The full table sits behind **Compare all levels**: the Level column pinned, the other six scrolling sideways with one-line 14 sp heads, 17 sp figures and the shared edge fade, the picked level's row in bronze; with large text it becomes one block per level, so no number is off-screen.
- **Timer:** the painted sea under a navy veil (`timer_sea` / `timer_sea_wide`, painted for the app at full resolution, a cut per orientation), the battlefield silhouette at the foot, phase on a title plate, steel double ring, digits in Inter on a near-black disc. Stays dark in both themes. Next (bronze) is wider than Pause (steel) because its label runs long.
- **Draft:** step on a title plate in a team-edged plate; pool tiles with portrait, symbol and parchment name; bans crossed out, dimmed and struck through. Words that name a team show its mark in line (`TeamText`). Once the coin has named the teams the draft speaks in colours only ("◆ Red picks 2", "Step 4 of 8 · 1 of 2 · for Ada", columns "Blue" and "Red"); "Team A"/"Team B" are the method editor's words, and the coin's team is marked by the coin's face ("Tie Breaker coin"). A finished draft is a plate per team with the seats divided; only the selected seat is framed. Teams balanced by Elo give each player a 48 dp padlock toggle (TalkBack: "Keep Ada on Red when re-balancing", Locked / Not locked); what it does is its tooltip on a long press ("Locked players stay on their team when re-balanced"), not a line on the screen, so the odd-count choice stays above the footer.

- **Group sync:** not in a group, one line on what it is for, then two plates, Scan an invite (the one bronze button) and Start a group (outlined), one sentence and one button each; a form (paste an invite, name the group) opens only in the plate chosen, and its button stays on and says what is missing rather than greying out. The transport, the server and the key fold under "How syncing works"; the foot is the app's beacons painting. In a group, the bronze action names its transport ("Sync with nearby phones"; with a server "Sync through the server", nearby outlined under it), then "Phone-to-phone sync", the normal way, in one line with the phones synced with; a server that is set up sits open under "Remote sync", otherwise it waits folded at the foot as "Optional remote sync". This phone's player is the shared `IdentitySelector` ("Playing as Ann", "Group · Friday nights", a chevron to change). The sync sheet has the drag handle and a close X beside its title. Hosting is an explicit state machine under its steps (Collect, Merge, Send back, Done: bronze now, teal ticks behind, hollow ahead), a count line ("2 phones included · 1 excluded"), each phone's state on its row (a phone left out says why only there), then one command: off while an included phone still sends, naming it with the live percent ("Waiting for Cid · 60%"), and on, bronze, once every included phone's data is in ("Merge data from 2 phones"). Starting by itself is a switch of its own under the command, "Start automatically when ready", off by default; armed, it says so under its label with an hourglass ("On · merges once Cid's data is in"). Then "Merging…". Cancel is a full-width outlined button. Each change is announced politely. A phone with an old invite offers "Show new invite" on its own row (the group's QR over the sheet). It ends on a summary plate: who is up to date, what changed on this phone, who to sync again.
- **Online:** My games is compact so New room stays in view: games waiting on you first, each a tight plate with a bronze edge, "Your turn" in teal with a hand, the map and Play; the rest one line each in one plate (state in words with an icon, then the map). Every game shows its heroes (a seat is its hero) as small overlapping round portraits in their team rings; TalkBack hears who plays whom. In a lobby a free seat is a dashed ring in its team's colour around "+", with "+ Tap to sit here" in teal; a taken seat without a hero yet, a solid ring around a silhouette; every taken seat ends in its state, a mark and a word ("Choose hero", "Not ready", teal "Ready"), and your own carries a trailing "Change hero" text button with its icon. Under the teams only your next step in bronze ("Choose your hero", "I'm ready"), then what the start waits for in one sentence in a well, each seat counted once ("Waiting for one more player to join, Cid to choose a hero and you to get ready."; a free seat adds Share invite), directly above "Start the game", which the host sees throughout, unlit until nothing holds it up, over "You start the game; the server flips the Tie Breaker coin."; taking ready back is outlined and "Leave the room" is an outlined button in the error colour. Join a room: the field's label stands above it for good ("Room code or invite link"); Join stays off until the field holds a plausible code or link, and the keyboard's Go with anything else says under the field what a code is. The online home opens on the shared `IdentitySelector` set compact, one 48 dp line ("Playing as Ann · Online", read only), so Create room stays in view; a game row puts the map on its own line under its state.
- **Trivia home:** a Practice / Challenge `GoaSegmentedRow` at the top, then who's playing (the row stays in both modes; its line follows the mode). Practice shows mastery as one header (the learned figure in Inter bold 28 sp, "of 2,150 numbers learned" in Cinzel, the bar, its legend and "A number is learned after three right answers in a row."), then **Quiz options** as one row that says what is set ("All heroes · all numbers · all cards · 4 choices · 10 questions") and opens the setup in place, then the per-hero rows (the bar, and under it both counts in words, "30 learned · 12 learning"; learning's segment and its legend mark are hatched, so the two differ by more than colour); Challenge shows its fixed round. The pinned bar holds one Start that names the mode ("Start practice", "Start challenge"; as Guest the Challenge asks who's playing first).
- **Trivia question:** upright, the question and the answers never change size or place when it is answered. The question plate leads with the card: its name in `CardTitle` (Crimson 26 sp) beside its colour's ribbon, its kind, whose it is, then the card's painting, cropped from its scan to the window that holds no printed number (x .25–.96, y .12–.50 of a deck card; checked on every scan; a card without a portrait scan shows its hero's art), in a band as tall as the screen leaves before the answer (96–320 dp) and kept after it; the painting is never enlarged past 1.6 screen pixels per scan pixel, so it is often smaller than its band, centred in it with its own rounded corners, then "What is its Defense?" on the title plate. Under it the answers (88 dp tiles, 64 dp for recall); Next is always in its slot ("Answer to go on", off, until answered). Answered, the verdict and the whole scan (up to 300 dp wide) are added under the answers and the list scrolls to them. The header (question N of M, score, who's playing) is pinned above.

**Rules the screens keep:**
- **Bronze is selected and primary; teal is next, correct, "you can act"** (a running clock, a right answer, a rating gained). Chips, switches and picked rows are bronze on every screen, all through the one selection look (`SelectablePlate`, `GoaPlate(selected =)`, chips and segments: the bronze 1.5 dp edge, a bronze wash and a leading check or radio; timer presets and setup, draft methods and options, game options); never a teal or team-coloured outline for a pick. Team colours mark teams only. Timer's teal progress arc is the running clock; a running Hurry up's arc is bronze.
- **One plate per section, dividers inside, brackets on the outer plate only:** a team's seats, a preset's phases, the timer's presets and setup choices, the rules topics, both leaderboards, every settings group (`SettingsGroup`). Plates never nest (an inner one is a well; an inner choice is a row). A list's action never floats over it: the match log docks "Log match" on a slate bar under the list (as trivia's Start bar; a pending match's "Open match setup" outlined and "Who won?" bronze share one row in equal halves), the leaderboard ends with "Add player". **Leaderboards** (Players, trivia, online) put their top three on a `Podium` at the head of their plate (a player's crest with the hero badge, a hero's portrait; name, figure; two columns' worth of name wraps, never cut; a name breaks only between words or after a hyphen, two lines at most, one size smaller before any ellipsis, and the figures sit in one fixed-width column), the rest as rows under it (tied places stand on equal steps; the group's places and rows open the player, so each carries a chevron and "View player" for TalkBack; from font scale 1.3 the rows drop the chevron, so the trend ("+84 last 5") is never cut), and end in the hall of banners (`ScreenFoot(FootArt.HALL)`); from font scale 1.3 the podium gives way to rows. A provisional rating is a small hourglass by the name (TalkBack: "Provisional rating"), and when every rating is provisional the scope line says it once instead; a match's result, with no such line, writes "Provisional" beside the hourglass.
- **A title on the banner** shrinks, then takes two lines, and only then is cut (`BannerTitle`, `.goa-topbar-title`); a long rules topic shows its head there and its whole title on the page. The timer's second line goes on a phone under 360 wide (TalkBack still hears it).
- **The search field** is the plate's slot: the slate in the dark theme, paper with a steel edge in the light one.
- **The light theme is designed, not inverted:** the warm paper canvas, paper plates with steel edges, ink text; the steel banner, the timer and the map stay dark, as on the page.

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
  coin**'s faces on the draft's coin, the game table's HUD and New game's coin flip (with the team's
  shape on a badge at the coin's foot: team never by colour alone).

**Where we deviate, and why:**
- **Dark canvas, not the white page.** The rulebook is white paper; at a dim table a white screen
  glares and drains the battery. Dark navy stays the default canvas; the light theme is the page: warm paper with paper plates on it.
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
- **Team is never colour alone.** The official gear and star are the team marks wherever a team is shown (ADR-053).

## Tokens (`core:designsystem`)
| Token | Dark | Light | Use |
|-------|------|-------|-----|
| background | `#0B1622` | `#EDE7DB` | app canvas |
| surface | `#1E2327` | `#FCFAF4` | cards, sheets (the light plate's paper) |
| onSurface | `#E8E2D6` | `#221D17` | body text (ink) |
| onSurfaceVariant | `#BCC6CF` | `#4C4438` | secondary text (≥ 7:1 on every ground, `ContrastTest.secondaryTextIsAaa`) |
| primary (bronze) | `#C9A35B` | `#7A5A1E` | key actions, headings accent |
| onPrimary | `#0B1622` | `#FFFFFF` | text/icons on filled primary |
| secondary (tide teal) | `#3FC1B0` | `#17695E` | progress, timers, next and correct |
| team red | `#E76C61` | `#B0362C` | red team |
| team blue | `#4F8FE0` | `#2C63B0` | blue team |
| teamRedText / teamBlueText | `#F29488` / `#8DB9F5` | `#8E2A21` / `#1E4C8F` | a team's words (`teamTextColor`) |
| textMuted / outlineMuted | `#A3ADB6` / `#7C8994` | `#665B4C` / `#857A68` | a done, past or empty state's words (≥ 4.5:1) and edge (≥ 3:1), never alpha (`ContrastTest.mutedStates`) |
| error | `#FF6B6B` | `#B3261E` | |
| surfaceHigh (surfaceVariant) | `#2A3136` | `#F1EBDF` | raised rows, portrait frame top |
| verified | `#3FC1B0` | `#17695E` | "Verified" badge (= secondary) |
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
| plate / plateWell | `#1D2124` / `#15191B` | `#FCFAF4` / `#F1ECE2` | a plate's flat tone (fields, fades) / a recess on it |
| selection / selectionFill | bronze / bronze 16 % | bronze / bronze 12 % | the picked choice's outline and tint |

**Player palette** (`PlayerColors`, 10 swatches; legacy). `Player.color` is still stored and synced (the web app draws it), but the phone no longer draws or asks for it: player crests are team-neutral (below). `Player.color` stores the dark tone;
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
team's mark, so a coral player on Blue is never read as Red.

All foreground colours above reach ≥ 4.5:1 against both background and surface in their
theme (checked 2026-10-17). Note: dark-theme filled primary buttons need dark text
(`onPrimary` = `#0B1622`); white on bronze is only 2.4:1. Re-check any new pair. Team
identity is never colour-only: pair it with a label or the team's official mark, gear for Red and star for Blue
(`TeamMark`, ADR-053). The shared words carry a glyph for the team, which `TeamText` draws as that mark.

Contrast checks added 2026-10-17 (WCAG ratio vs background / surface / surfaceHigh):
- Player palette: dark tones 6.9–11.2 / 6.0–9.8 / 5.2–8.5; light tones 5.0–6.1 / 5.8–7.0 /
  4.6–5.6. Every tone ≥ 4.5:1 on all three of its theme's surfaces.
- unverified `#E8B04A` 9.3 / 8.2 / 7.1 · `#8A5A00` 5.2 / 5.9 / 4.7.
- verified = secondary: light `#1B786B` is 4.25:1 on surfaceHigh — badges sit on surface or
  background only. Same caveat for light primary `#84662A` (4.29:1 on surfaceHigh): use it for
  icons/large text there, not body text.
- team red/blue (dark) on surfaceHigh are 4.1/4.2 — keep team-coloured *text* on background
  or surface; on surfaceHigh pair it with the shape mark.
- onSurfaceVariant on surfaceHigh: 7.6 (dark) / 8.1 (light) since round 4 (was 6.6 / 6.1).

Contrast checks added 2026-10-25 (ADR-042), enforced by `ContrastTest` (core:designsystem), which
fails the build when a token change breaks one:
- onBanner on bannerTop / bannerBottom: dark 8.6 / 13.2, light 6.6 / 10.3; onBannerVariant: dark
  6.4 / 9.8, light 4.9 / 7.7.
- heading (copper) on background / surface / surfaceHigh: dark 7.9 / 6.9 / 6.0, light 5.4 / 6.2 / 5.0.
- On parchment: onParchment 11.2 (dark) / 13.8 (light); onParchmentVariant 5.9 / 6.6; parchmentInk
  5.0 / 6.1.
- onSurface and onSurfaceVariant on background, surface and surfaceHigh, both themes (as above).

- **Type:** display = Cinzel (titles, headlines, `GoaTextStyles.bannerTitle` on the steel banner,
  `GoaTextStyles.sectionHead` copper small-caps heads, `overline` caps at 13 sp and ≤ 0.08 em tracking, the floor for
  Cinzel caps), body/UI = Inter, printed
  = Crimson Pro (`GoaTextStyles.printed` 18 sp, `printedSmall` 16 sp: card text, rulebook excerpts;
  the rules pages read it at 20 sp). Static instances in `core/designsystem/src/main/res/font/`, licences in
  `third_party/` (see `third_party/ASSETS.md`). Numbers use tabular figures:
  `GoaTextStyles.timerDigits` (96 sp), `timerDigitsSmall` (32 sp), `numeric` (14 sp), all
  `tnum, lnum`. **Floor:** secondary text (`bodySmall`: captions, subtitles, notes) is 14 sp, told apart from body text by
  `onSurfaceVariant`, not by size; 12 sp (`labelMedium`, `labelSmall`) only for tags and pills. A search field's glyph is
  `SearchFieldIcon` (24 dp, 32 dp at 2× text). `formatDuration`/`DurationText` give `m:ss` / `h:mm:ss` with spoken descriptions.
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
(the banner for a bar of one's own: the board screens) · `GoaPlate` / `GoaCard` (`PlateStyle`, `selected`) · `SelectablePlate` · `SectionHeader` (copper
small caps + fading rule) · `SubHeader` · `GoaButton` / `MetalButton` / `GoaOutlinedButton` / `SlateButton` ·
`StatBar` (1..8 base→max) · `HeroStatBlock` / `StatReadout` · `FilterChipRow` / `GoaFilterChip` · `DisabledReason` · `GoaSheetHandle` · `VerifiedBadge` /
`UnverifiedBadge` · `CardColorDot` · `ConfirmDialog` · `DurationText` · `EmptyState` ·
`HeroPortrait` (bronze double frame, inner gradient, sigil fallback; `framed = false` for the
grid tile, which draws its own fade, to keep scrolling cheap) · `HeroSigil` (monogram
fallback) · `PlayerAvatar` (the player's one identity on every screen: their crest, a filled heraldic shield, team-neutral; a steel shield with the initial as last fallback; with `showFace` and from 40 dp, on player lists, podiums and pages, a small round badge of their most-played hero at the crest's lower right, from `LocalPlayerFaces`, which the app derives from the match log at its root) · `AccountAvatar` (an online account: the crest its id picks, with the same badge when known) · `IdentitySelector` (who this phone plays as, one row on Online, Card trivia and Group sync: the mark, "Playing as Ann", its scope, a chevron when it can be changed here) · `Podium` (a leaderboard's top three on gold, steel and copper steps, 2 · 1 · 3; rows below at large text) · `ScreenFoot` (a painting faded into the canvas at a short screen's foot: `HALL` for leaderboards, `BEACONS` for group sync, `BATTLEFIELD` the rulebook's) · `TeamBadge` / `TeamMark`
· `ComplexityStars` (`showLabel` adds Challenging / Complex / Very Complex / Wizard). The
gallery screenshot test (`GalleryScreenshotTest`) shows them all, dark/light, font scale 1 and 2.
`cardtext/CardTextView` renders printed card text (inline `GameIcons`, italic reminders, bold
keywords) for the card viewer and the board.
**Official art** (`component/OfficialArt.kt`, ADR-042): `OfficialArt` (paths of the item icons,
Spawn point symbols and Tie Breaker faces) · `HeroSymbol` (a hero's deck symbol in its own
colours, the sigil while loading or without one) · `CardScan` (a card scan as it lies on the table,
with a designed fallback; one TalkBack node with the card's values and text) · `ParchmentPanel` /
`PrintedCardText` (printed text on the card's parchment, in Crimson Pro) · `CardTitle` (an official card's name, the one way the app sets it: Crimson semibold, `CardTitleSize` Caption 15 · Row 18 · Large 22 · Title 26 sp; `CardTitleSpan` inside running text) · `OfficialIcon` ·
`rememberOfficialBitmap` (for canvases). The gallery's `officialArt*` screenshots show them all.

## Key screens
Catalog grid (tiles carry the deck symbol at the portrait's corner) → Hero detail (the deck
symbol beside the name on its title plate; the **Deck** is the official card scans in a grid by colour, in tier order
(I, II A, II B, III A, III B), as many columns of at least 160 dp as fit (two on a phone held
upright, more held sideways; wider with large text), each named under it while it is too small to
read its printed name, plus what the card doesn't print (where Takahide's golds start, what a
Handicap card stands in for); tap = the card viewer, press-and-hold = the card full screen; Gydion's
spellbook the same with its landscape pages) · Card viewer (the scan large; under it only what the card
doesn't print — the Handicap basis, what its item icon gives, with the official icon — and "Read the
card as text", a full-width steel button right under the scan, which shows the values and the text on parchment; the scan is
never drawn larger than 1.6 times its own pixels, so its print stays sharp) · Timer (full-screen, giant ring countdown, phase chip with
"Previous phase" beside it, turn/round pips; bottom controls are Pause and Next only; a running
Hurry up takes the dial (its countdown is the big clock, "⚡ HURRY UP" above the digits and "LEFT", "LAST SECONDS!" or
"TIME!" under them, so urgency is in words as well as colour; the step's own clock moves to a slate pill under the dial;
the table-rule actions under the dial, "⚡ Start hurry-up · 0:30" and "Time-out +0:40", are 48 dp outlined pills with
a bronze edge and icon and a verb, never the plain slate of a status pill)
and a slim Hurry up bar with Restart and Stop is pinned above the controls (in landscape too, on the right; the round view,
which has no dial, keeps the bar with the countdown in it); the top bar holds only the coin chip and the overflow menu,
where every other tool is named (Levels & defeats, Tie Breaker coin…, Restart phase, Skip to end of round, the view
switch, Rules, Settings, End match… last); the coin chip is the Tie Breaker coin's face with "Coin Red" (TalkBack: "Tie
Breaker coin: Red"), the face alone only where it doesn't fit, and the bar's second
line says "Table rules · 14:32" ("Game 14:32" with large text); landscape runs a thin steel banner the full width (round
and subtitle on one line) and gives the dial the whole left column under it, step, table rules and controls on the right
(round view the same: list | step); paused = blinking
digits and a large "PAUSED" label; the **round view** alternative lists the whole round under
section headings — done rows ticked in bronze and stepped back by colour, never by alpha, so they
keep their contrast; the current row an expanded teal-bordered card whose border and digits turn
bronze in the final seconds and red in overtime; digits sized in dp so font scale 2 doesn't blow
them up; landscape = list left, clock and controls right) · Players list → Player profile (rating chart) · Match log/editor ·
Meta dashboard · Draft setup (the bar's back arrow leaves the draft: choices are saved as they are made; the in-flow
Back steps back; teams balanced by Elo say what the padlocks do in one line always in view, "🔒 Keeps a player on their
team when you re-balance", each padlock a 48 dp toggle "Keep Ada on Red" confirmed in a snackbar, and Re-balance is the
shared steel secondary button) → Draft board (a sticky step banner: team-edged plate, the step on its title plate with its clock
beside it, then whose turn inside the step on a line of its own ("Ada: pick 1 of 2", "Ada's pick"; "Step 3 of 5" for a
lone ban); the step chips, numbered steps with their counts by the team's mark ("4 · ◆ picks 2", `DraftStep`: one numbering
for the board, the method list and the banner), finished ones with a check and a lighter edge but words at full
contrast, the strip running to the screen's edges from the gutter with the edge fade, scrolled so the step before,
the step in play and the next stay in view; one team bar, per team its mark, name, a portrait or
empty ring per seat and its bans counted, which a tap opens into both rosters, equal-height columns with "Roles covered"
and ban chips, capped at 45 % of the height; then search with Hide taken and the role chips, pinned; the pool grid
follows, at least two rows in view at 411 dp. Large text or a phone under 640 dp tall pins only the banner. Taken
heroes shrink to chips; landscape moves the banner and teams to a side column so the pool keeps the full height; four tiles across on a phone (three at 360 dp), names up to 15 sp,
stepping down only for the longest; a pool tile's tap opens the hero peek sheet (one tap, one meaning: no inspect
button on the art; Open hero page sits under the hero, above the stats, so the footer never hides it), whose
commitment is pinned under its scrolling content: whom it is for, wrapping ("◆ Red · Ada: pick 1 of 2"),
beside the step's clock when the draft is timed (it keeps running while the hero is read), then the bronze primary with a short label that never truncates ("Pick Arien"), which commits; before the
first choice "Swap out of the pool instead" under it. Draft complete: a plate per team, rows divided by hairlines,
each row with a quiet Swap; after one tap swapping is a named mode: the bar says "Swap Arien with another ◆ Blue hero"
with Cancel, teammates offer a bronze Swap here and the other team steps back. One bronze Open match setup (with the Match setup flag) is pinned
with a ⋮ menu: Log result later, Try on the board, Handicap cards… and Deal again) · Draft methods (a radio plate per method: name, pool line, the numbered steps
by mark only under the layout chips' mark legend ("4 · ⚙ picks 2"), the method's prose folded under "Method details";
its name in the list-title style (Inter), the chosen one bronze, its sequence in team colours; "Continue with …"
pinned below; Sources, Duplicate, Edit and Delete in each plate's menu; New method at the list's end; the methods'
Team A and B are said as "coin team" and "other team" everywhere, with a mark legend under the layout chips).
Timer start reads as a start button: one plate with the active preset as a tappable row ("Preset · Table rules ·
Change ›", opens the presets), its phases as a labelled two-column grid (`PhaseGrid`: the phase's name over its length,
"0:30 ×4", "0:30 per hero"; never dot-joined prose), tonight's length in bronze in its cell with a "Tonight" tag (only the length in force: the preset's own length and
Reset are inside Adjust), one folded "Adjust for tonight" row ("Play a card length · 4 heroes") that opens to the per-turn
±15 s and Heroes at the table, and one bronze Start timer, the plate's only button. Under the plate a quiet row of two
text buttons, "Draft first" and "Match setup" (draft → setup → timer → log is the game-night flow), then pending
matches, each with two 48 dp text buttons with their icons, Discard and "Open match setup"; the presets are listed once, on Timer presets, each preset's phases in the same grid.
Who won? / match editor: Who won? asks the winner and the win condition first, then both teams as short rosters;
the editor shows the teams first, then the result. A seat is one line (portrait and hero, a tap changes the hero; the
pilot as a dropdown pill, "Ann ×2" for a double pilot; a "Handicap" tag), and its pencil opens the rest under it (the
Handicap card, Remove seat, "Ann pilots 2 heroes"). When and where, Details (duration, notes) and Who won?'s Match
status fold under labelled rows that say their current values. The winners are radio cards (an empty trophy outline, a bronze trophy and the bronze selection
frame once chosen) under "Which team won? · Required"; the win condition says "· Optional" and is a 2 × 2 grid of equal chips; the bronze Save ("Save
result", "Save match", "Save changes") is pinned under the form, off until the form can be saved, with the reason under it
(no Save in the banner). Each seat: the hero row, then the pilot as a dropdown field ("Pilot" on its outline, the name, ▾; it opens the pilot picker; no face, which would read as a second hero) with the Handicap card as a chip beside it ("Handicap" with a box, checked when on; TalkBack "Handicap card for Arien"), so a seat stays one row of controls, and a double pilot as a plain caption ("Ann pilots 2 heroes"), not a control.
Bottom nav: Heroes, Play, Timer, Players, More (original icons: shield with the brand mark's trident, a board space with a play mark,
hourglass, companions, a plate with three studs; solid variant when selected; labels share one size, capped by
the width, at large text). **More** lists, as labelled rows with icon and purpose: Draft (At the table), Rules
(Reference), Group sync and Backup and restore (Your group), Settings and About (App). The Draft keeps a back
stack of its own without an item (a draft in progress survives switching tabs; More is lit while it shows; back
on its root returns to where it was opened: More or the match log). **Top bars** show at most two glyphs, each
labelled (TalkBack and a long-press tooltip, `BarIconButton`); with more actions the first stays and the rest go
in a labelled overflow menu, "More options" (`BarActions`): Heroes shows the layout switch, then Rules and
Settings in the menu; Players shows Group sync as words with its glyph, "⟳ Group sync" (the glyph alone reads as refresh; at large
text the glyph, labelled; its attention dot also on the menu button), then your player
page, Share stats, Backup and Settings; Play keeps Settings (the board style is set there). The **Play tab** opens on the game in progress (Resume), then plates
with painted scenes (the app's own art in the official portraits' flat, bright brushwork, `third_party/ASSETS.md`), the
name engraved over the scene and the state in bronze: Play vs computer across the width, then Game night (Draft, then
Match setup, then the timer: it opens the Draft; Draft also stays in More), Puzzles (a teal tick and
"1 of 50 positions", "1 of 22 rounds": the same lines, `SolvedCount`, as over the puzzle list's two parts), Board sandbox, Online and Card
trivia two by two (one column at large text, three across on a wide screen); each plate says what it is in one short line
("Draft and time it"), and the scenes grow with the screen's height within limits, so all six entries are in view on a
411 × 891 phone. Settings groups are the restrained plate (`GoaPlate(style = Secondary)` via `SettingsGroup`: flat, a
quiet hairline, no brackets; More's groups too), one per section with hairlines between rows, never a plate inside a plate; the theme switch is a `GoaSegmentedRow` (each option's glyph, the picked one's check); About's prose sits on the page, only rows you tap on a group. An entry the engine can't serve says "Not available in this build", never "Coming soon".
Board sandbox is the saved-board list only. The bottom nav is hidden on the
running timer (no tab to mis-tap beside Next; back leaves to Heroes), on the draft board and on a board;
while a session runs, every other screen shows the **timer running bar** above the nav (timer
icon, phase, "Round · Turn" context, tabular clock in tide teal; dim when paused, error red in
overtime; tap returns to the timer). The Meta tab opens on the hero ranking, then the group summary (totals, last game night, how games end); the ranking
defaults to a compact sortable hero table
(48 dp rows, sort chips; Pick/Ban columns drop at large font) with a Table/Cards switch.

### Board screens' frame (sandbox, game, puzzle)
The three map screens share two pieces of chrome in `feature/board/ui/` so the map gets the screen.
**Compact top bar** (`BoardScaffold`): `GoaScreen`'s look (the steel banner, back arrow, Cinzel
title in light steel) at 48 dp with an 18 sp title in portrait and 40 dp with a 16 sp title in landscape; it
grows only when large text needs one line of the title. Glyphs as everywhere: at most two (the sandbox: Undo and
More options with Redo, Flip and Reset). A board's line-up too long for one line gives the bar a short title, "2 v 2 ·
Forgotten Island" or else "2 v 2 board", in the full 18 sp, with the line-up under it in Inter 12 sp where it fits whole,
else the heroes' portraits in their team rings after each team's mark, "vs" between (TalkBack hears the line-up either
way). A bar with a second line is 52 dp (more with large text) so the line clears the banner's bevelled edge. Any other title too long for one line takes two lines at 13 sp
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
whole board is in view), and the game hides the hero strip (back with the
panel), keeping the HUD pill and, after the reveal, the turn track (the turn's state is what a
player looks for with the board in view, ADR-041; sideways, both float at the top while the panel is hidden). Hidden or shown is kept per screen (`rememberSaveable`) and survives
rotation. The panel follows the theme (ADR-068): its **steel** answers (action chips, Read card, Setup, declines;
`PlateTone.STEEL`) are the dark slate in the dark theme and a light steel (#E8ECEE→#D5DCE0, ink text, a #8B979F
edge) on the light theme's paper, so no dark slab sits on paper; **gold** answers are the same metal in both. What
floats over the map (HUD, turn track) and the cards' own frames stay dark in both.

### Board (sandbox, ADR-033)
The drawn map (the fallback since ADR-053, see the top of this file): pointy-top hexes turned in 30° steps so the
Lane runs up the screen in portrait (the bottom team's Throne Zone at the bottom; "Flip" swaps) and
left to right in landscape. Zone fills are muted (`BoardPalette`, derived from the theme for dark
and light): lane Zones alternate two tones, side Zones a sea-green tone, Throne Zones a team tint,
the current Battle Zone a teal tint with a teal border; other Zone borders are bronze, spaces are
separated by a hairline of the canvas colour. Terrain is a dark hex with an original faceted rock.
One ring of water hexes (the terrain around the board, p.19) is drawn faintly around the map
(`water`/`waterLine`). Zone labels (Battle Zone, "Lane 1 · Battle Zone" on a double-lane map,
Red/Blue Throne) float at a fixed size outside the zoom, so they stay readable; they sit just
outside their Zone's edge, under every piece and overlay, and take the first of their places that
is in view and covers no piece, highlighted space, Measure line or map button (on the game table the
Fit / Flip / Spellbook column as laid out, under the hero strip and grown with the font scale); with
none free a Throne label hides, a Battle Zone's takes the nearest free spot (see the painted board above). **Spawn points** are the rulebook's printed symbols (p.6, `rememberSpawnArt`), drawn as markings on
the board, never as units: faded (50 %) and partly desaturated toward the Zone fill, 1.0 u across,
inside a piece's disc, so a minion or hero standing on one hides it completely (an occupied Spawn
point shows only its piece) and the pieces stay the most prominent thing on any space. A hero Spawn
point is Red's gear or Blue's star; a minion Spawn point carries at its lower right the type's
original glyph in a thin outline of the team's shape (a diamond for Red, a disc for Blue), no solid badge (the printed
symbols of one team are too alike at ~38 dp to tell the types apart; TalkBack names the Spawn point).
Until the symbols load, the original glyph on the team's shape outline. **Original glyphs** (masters `design/board/glyph_*.svg`, path data mirrored in
`ui/BoardGlyphs.kt`): crown = hero, sword = melee, arrow = ranged, shield = heavy. Pieces: heroes are the
official portrait in a team-coloured ring, minions a team disc with the glyph, tokens a stone square
with the label; heroes and minions carry the team's shape at the bottom right, so team is never
colour alone. **Overlays** each have a pattern as well as a colour, and the switch that turns one on
shows its swatch (the legend): "Movement", "Fast Travel", "Card range", "Radius". A chosen card shows **one overlay**
(its Movement, else its Range, else its Radius), so the marks never overlap; the switches add the others. While a layer
is on, the **map key** (`MapKey`, as the game's) is **docked to the map's top edge**: one full-width strip under the
bar, never a plate floating over pieces, its swatch and value each ("Movement 4: steps to each space", "Card range 2",
"Fast Travel", "Radius 1", "Where it goes", "Measure: 3 spaces"; short words by default, "Movement 4", "Card range 2",
its words capped at 1.3×), and the map is framed under it. Before anything is selected a dismissible tip over the
tools says "Tap a hero to see movement and range" (gone once a piece is selected). Markings: Movement = green fill and outline with the number of spaces (its
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
push brings the new Battle Zone into view. **Map controls** (`MapToolbar`, the sandbox's and the table's): one slim
steel pill docked at the map's end edge, under the key or the HUD: "Fit board" (it turns into "Lane view") and "Flip
view" as glyphs alone, 48 dp each, the words in a long-press tooltip and for TalkBack, so they read as map furniture
rather than a floating panel (round 5: the labelled column cost the map its room). A flip says "View flipped · Blue at the bottom" beside the column for a moment (TalkBack hears it). Every
framing (the opening, Fit, a reveal) moves aside, to the start or down, so **no piece stands under the column**
(`MapViewport.updateAvoid`), only where there is slack: never cutting a piece in view at an edge nor cropping what a
question is about (its options, the actor, a hint's target), zooming out a little rather than cropping. Each space's TalkBack node is a square inside its hex (1.46 map units, so neighbours never
overlap and Android keeps each node whole). The card strip starts one card before the chosen card
and fades at an edge with more cards beyond; from font scale 1.3 its chips wrap two to a row, each half the width, every name whole. Each chip heads with its family (`CardFamilyLabel`: its colour's ribbon and "Red · II", "Gold · Basic", held together), never the colour alone; from font scale 1.3 the family is a line of its own under the name. The chosen card takes the one selection look (bronze edge and tint, a tick), never its own colour. **New board** fits a 384 dp phone without scrolling:
one slot on top holds the latest draft's or match's line-up (a card with Set up) until the first
pick, then the teams (pick chips sharing the row, "Next pick" spelled out on an empty team, the
border and the switch otherwise); search with a segmented Red | Blue switch beside it (under it at
large text); a portrait grid with every hero on one screen, **names first**: the fewest columns whose every name
reads on one line at 13 sp or more and whose rows still fit with portraits as short as 0.74× their width (32 heroes at
411 × 891 dp: 5 columns, names 13–14 sp); only where that can't fit, as many columns as it takes (tiles 42–64 dp,
square at least). Each tile has the gap around it and the name under it, semibold, one size for the whole grid: the
size the longest name needs across its cell (never a smaller name on one tile); the height the columns leave makes the
portraits taller (up to 1.3× their width), so the grid fills the screen; one tap target of at
least 48 dp; a pick's badge is the team's mark and its number in labelLarge); and the Map menu beside Set up in the
bottom bar. A finished draft pins Open match setup, Log result later and Try on the board
under the teams on a phone held upright.

### Play against the computer (ADR-035, ADR-041)
The game table draws the sandbox's map (`MapBase`, Zone labels, `PieceToken`) and adds only what a
question needs. The official cards are the content: what a card prints is shown as the card, never
retyped; the app adds only what no card says (the turn's state, a rule, a verdict).
**On the board:** every space or piece that answers the question is the dashed teal target outline;
the tapped one gets the selection outline, and a move shows the engine's path as a bronze line with
dots at each step; a decision's other highlights (a card's range) use the violet Range outline; the
acting hero's space is outlined in bronze with a bronze **"NOW"** tag over its piece (dark letters; the turn's state is bronze on the map as on the track, the ring keeps the team's colour; growing with large text up to 1.5×,
outside the zoom; TalkBack hears "acting now" on the space); an immune minion is ringed with a dashed
teal circle (TalkBack: "immune"). Map taps select; a **Confirm** button of one line at 56 dp says what it does ("Move
here · 3", the engine's path's steps; "Respawn here", "Choose · Tigerclaw", "Remove · B") and the instruction
plate says which one it is in full ("Chosen: Lane Zone 3 · 3 spaces from Wasp"). The sandbox's key is one compact row
of short words ("Movement 4", "Card range 2") with an info mark; a tap opens the full words ("Card range 2: in reach
from here") and closes them again. Every option is a **48 dp target**: a question answered on the map frames its spaces
with hexes 48 dp across when they fit (the Battle Zone around them only when it fits at that size too). Spaces the
framing still leaves under about 40 dp (2 to 12 of them; once for a question it stays, as the list only makes the panel
taller) are **listed** at once. Any question answered on the map can be listed: a **List** chip (`MapListToggle`, 48 dp,
bronze with a tick while on) ends the hint row ("12 spaces · tap a lit one", the peek's too) or sits under a card's
question; on, every option is a 48 dp row in the panel led by its **letter** (A nearest the hero asked, then by
distance; spaces two to a line, "3 spaces from Wasp" over its Zone; heroes and tokens by name; minions with their
marks), and the map tags each option with the same letter in a **teal rounded square** (bronze once chosen; never a
round chip, so it can't be read as a step count), at least 20 dp tall on screen however far out the map is; the key
says "Letter = list row". Opened from the peek it shows the whole panel. A tap within 24 dp of a lone option's centre
takes it; a tap that could mean several options (closer together than a finger, or a figure standing over another
option) opens **Which one?** at the finger (`MapChooser`), a 48 dp row each with its letter while listed; the sandbox
asks the same between close pieces. While the person's question lights the map a **map key**
(`MapKey`, a small plate under the round at a glance and the turn track, inside what the map frames below, so it
covers no piece) names
each mark with its own swatch: "Lit: pick one" (a lit, white-edged hex), "Steps away" (the dark disk), "Letter =
list row" (the teal tag), "Card range" (the violet outline), "Route" (the bronze line); only what the map shows now. TalkBack: one line.
**Title:** "Round 2 · Turn 3" where the bar has the room (600 dp and wider); on a phone upright "Round 2", the turn
named over the HUD's pips ("TURN 3"), so the labelled State control is in the bar in both orientations. **Top-bar controls** (the game, a puzzle; `BarLabelButton`): each glyph with its word under it in
12 sp Inter ("Undo", "State", "Log", "More"; large text grows it no more than 1.15×), TalkBack and the long-press
tooltip saying it in full ("Take back", "Game state: cards played, turns, the board", "Game log", "More options");
one glyph per destination in both orientations (State is the dashboard glyph in the bar and in More).
**Framing a question** (extended after the v0.8 hands-on pass): when a question arrives the map brings
its options (with the unit they are about and the asking hero) into the part of the map nothing floats
over: under the HUD and the hero strip or turn track, clear of the map buttons' column (Fit, Flip, the
Spellbook; when the options fit beside it), above the panel. Hexes are 48 dp across for a question answered on the
map when its options fit, else at least about 38 dp, or down to about 30 dp when that is what it takes for every option
to be in view (a Move 3 under the turn
track); with still more, the middle of the options. It frames again as what floats over the map settles
(the turn track appears at the reveal, the panel grows) — but never once the person has dragged, pinched
or double-tapped since the question came: their view wins. The attacker and the acting hero are framed too, and
the whole Battle Zone with them when it fits the free area.
**Top, floating (upright; sideways the HUD heads the side panel and the turn's order is a column beside the map):**
the **score bar**, one plate: the HUD, each group named in small caps over it (LIVES: each team's Life counters as
heart + number by its shape; WAVES: Wave counters per Lane; TURN 3: four pips, done solid and dimmer, current solid
and ringed, later hollow, the round being in the title; the group names 11 sp; TIE BREAKER: the coin as printed, the showing face (Red's gear, Blue's star, `OfficialArt.tieBreaker`) with the
team's shape on a small surface badge at its foot; the same coin, 64 dp, heads New game's "Blue
shows" dialog), then, under a hairline, the **hero strip**, one form in every state (UI hill climb, round 4): the
turn track's cells (below), in the same places. When no turn resolves (before the reveal, the end of a round, a
puzzle solved) only each cell's second line differs: "Lv2 · coins · cards in hand" instead of the card played, and its
band says NOW on the acting hero or CARD DOWN once its card is down (p.7: which card stays hidden). A tap on a cell
opens the hero's state (TalkBack: "Open Arien's state"). In the side panel the
strip is portraits only, 48 dp discs (team ring and shape; TalkBack hears the full
chip; a tap opens the hero); the side panel takes 42 % of the width (at most 400 dp).
**Turn track** (after the reveal, `play/table/TurnTrack*.kt`): the strip becomes the turn's order, one row of equal
cells on the score bar's plate under the HUD and a hairline (the map is framed below it), one cell per hero in acting
order (the heroes done, in the order they acted, the acting hero, the next, the later ones, then passes), read at arm's
length. Each cell is stacked: the hero's portrait in its team ring (22 dp; the mark at its foot, the action chosen as
the game's icon at its head, crossed out while off the board) beside the initiative as it counts in 18 sp figures ("=11"
in teal while tied with another hero still to act); the name under them (13 sp bold, a step down to 78 % rather than
cut); the card played under that (12 sp medium in `onSurfaceVariant`, one size for the strip, on up to two lines broken at a space or
after a hyphen, a step smaller only so that no word is broken; never an ellipsis; "Facedown" for a card resolved
facedown). Across the
foot a thin band with the state in a word, 12 sp bold: **DONE** with a tick, no band (a muted hairline and the word in
`textMuted`; the cell loses its well for a 1 dp muted edge and its words go to `textMuted`, never faded: Muted states), **NOW**
(bronze band, bronze 2 dp border), **NEXT** (teal band and border), **PASS**, **OUT**; a hero still to act has no band.
Bands are dark steel on the dark theme and light steel with ink on the light theme's paper. **Who acts vs who
answers:** while another hero answers the card being resolved (defends against its attack, discards for it), the acting
hero's band says **RESOLVING** (steel, bronze words, a bronze edge) and the one asked **DEFENDS** or **ANSWERS** (bronze
band and border), and the panel's header names both ("Defend against Arien", "Answer Tigerclaw · Discard a card");
never colour alone. Four cells fit a 384 dp phone; past 76 dp a cell the strip scrolls at 88 dp cells, the
acting hero scrolled into view. **Sideways** with the panel shown the cells stand in a 148 dp column at the map's
start edge, beside the map (which is laid out to its right, never under it), each portrait beside the initiative, name
and card; the side panel then holds the round at a glance, the question and the hand's tab, and on a short screen
(under 480 dp tall) the actions go two to a row so all four and the tab are in view. A tap opens the card focus viewer
on the hero's card, swiping through the turn's cards, each captioned "Wasp · Done · Movement · Initiative 10"; a long
press (TalkBack: "Open Wasp's state") opens the hero. The track stays in view with the panel hidden (sideways, a
row of 88 dp cells under the HUD). The turn-over moment still shows the turn's cards themselves (48 dp, a card
resolved facedown a back).
**Moments:** a surfaceVariant card with a bronze border at the top of the panel (a polite live region)
states the transition the table is at: "ALL CARDS REVEALED · Wasp acts first with initiative 10."
(ties: "Arien and Brogan tie on initiative 11: their team chooses who acts first (p.7)." or the
coin's side and who flips it; passes) until the first hero chooses an action; "TURN 1 IS OVER" with
that turn's cards on their slots while the next turn's cards are played; "END OF ROUND 1" with the
p.8 steps (over a question it brings, the Minion Battle or a level up, it is one folded row with a flag above
the question, "End of round 1", its steps a tap away, so the options stay in view and the summary is never cut);
"ROUND 1 IS OVER · Red won the push · Arien reached level 2". With the panel hidden and
the computer acting, the peek bar says the reveal ("All cards revealed · Wasp acts first …").
**Panel** (bottom, max 48 % in portrait so the map stays readable; right side in landscape; hides to its peek bar, see
**Board screens' frame**): the strip's **header** says who decides and what, a two-line directive for a phone passed
round the table (UI hill climb, round 4): a 4 dp bar in the team's colour, the deciding hero's portrait (34 dp, team
ring, the team's mark on its badge; a team's question: the mark), then in near-white (ink on the light theme's
paper), never team-coloured text, the owner in 16 sp bold, "You choose · Arien", "Computer · Wasp acting" (online the
player's name, a puzzle's other side "The other side · …"), and the instruction in 16 sp, "Choose a space", "Defend
against Arien", "Answer Tigerclaw"; up to 1.4× with large text). What the question's parchment title plate says
("DISCARD A CARD") is said there only, never again in the header. One TalkBack node, a polite live region, so a new
owner is heard. A question sized to its end may take up to 56 % of the height, so its commit row is always whole.
While a **space** is chosen on the map the panel opens at its **peek** (portrait): the header, with the engine's
whole prompt as its instruction, one line with the count ("12 spaces · tap a lit one"), and only the commit (Confirm
naming the choice once one is tapped; answers not on the map such as "Don't respawn"); a tap on the strip, a drag up
or its ⌃ shows the whole panel (the asking card beside the question), a drag down or ⌄ goes down a detent (whole →
peek → hidden). Each new space question starts at the peek; a question about pieces (the Minion Battle's lettered
minions) shows its list at once. The map frames the question again whenever the panel changes size. Then the prompt in titleMedium (a live region). Choosing the action
for the card beside the chips has no prompt plate: the card and its chips are the question (TalkBack still hears the
prompt). A question's title already on the strip is not repeated: "Minion Battle: remove a minion" puts "Remove a
minion" on the plate. Pinned at the panel's foot, under what scrolls, only the **deciding hero's hand**, and only when
that hero is the person's, as a **tab** (`HandTab`): a drag handle, the card back, "Your hand (Arien) · 5" (whose,
always: the phone is passed round) and a chevron; a tap or a drag up shows the cards under it, a tap or a drag down folds them
(folded at first, kept for the screen); none while the computer or another seat decides, none when the question shows
the hand itself. The panel is sized to the question and the tab, so it never ends on a heading cut at the screen's edge. The decision comes first: the
instruction and the legal choices, the card a reminder beside them. A question asked by a card
shows **the card beside its answers**: 28 % of the panel wide (84–108 dp; a landscape spell goes above them) for
buttons, 72 dp beside a board question's tap hint and Confirm; tap it to read it. Sideways under 480 dp tall the
actions must all be in view: the card is 64 dp (its printed numbers read; tap it to read it large), beside it the
question and the action chips two to a row, each a label and its page, except Fast Travel's condition, kept in view in
fewer words ("Your/adjacent Zone · no enemies"; Fast Travel takes the width Hold doesn't need); TalkBack hears each rule
whole.
**Actions** are compact chips beside the card: the game's icon and the value the card prints
("Movement 4"; the primary filled bronze and named "Card action · Attack 6"), only the rules
no card prints in a few words (Fast Travel "To your Zone or an adjacent one, no enemy
hero or minion in either", Hold "Do nothing", Clear), each chip ending in its rulebook page as a small pill (`PagePill`:
"p.7" on the card action, "p.11" Fast Travel, "p.12" Hold, in the chip's ink). **Rulebook pages are never inline words
("(p.7)") in a button or caption:** a chip or a head carries its page as that pill, and a decision has at most one
**rule row** (`RuleRow`: an info icon or the verdict's mark, the rule or count in bodySmall semibold, the page pill at
its end); for an action question without the card action, "Only the card action applies the card's text." p.7. The
hero sheet's Items and Ultimate heads, the sandbox's Movement, Fast Travel, Range and Radius rows and game over's
reasons carry their pages the same way. A question asked while a card resolves but not by its action (Respawn, an initiative
tie, a minion's way back, the Minion Battle) has its own name on the text box's tab, a silver box and no action
symbol: never ATTACK over "Respawn in your Throne Zone?". Under the chips, one line (info
icon, bodySmall) for each action the card prints that isn't offered, with the engine's reason and
source (`DecisionContext.unavailable`, ADR-041): "Its Skill can't start: the spellbook is empty until
Prepare Spells is performed (designer, BGG 3491113).", "Its Attack can't start: no target in range
(p.10, FAQ).", "Its Movement is forbidden by Xargatha's Stone Gaze." (a tap opens that card), "Its
Defense is never performed from a played card, only from your hand when attacked (p.14)."; right under
the Primary chip, what it will do (`DecisionContext.primaryPreview`, ADR-040 extended): Gydion's spell
as its landscape card (40 dp portrait-equivalent, tap to read) with "Casts Fireball" (labelLarge) and
"Its only target: Brogan" (bodySmall), several spells as cards with "Casts one of these: you choose
which next", a "will do nothing" warning on errorContainer with a warning icon, and the card's other
spells one line each with the engine's reason (also after the action's own line when no spell can be
cast); the Note box holds only the actions the card can't offer. Card questions (play, discard, retrieve, upgrade,
keep) show "Asked by <card>" (28 dp thumbnail) when another card asks, and the cards as official
images: the hand as 84 dp cards shrinking to 60 dp to fit five, two or three cards larger (112 / 92
dp, so Keep's two next-Tier cards sit side by side with their item stats), a card the question
doesn't offer dimmed with a block icon. Under every card to choose (hand, discard, upgrade, keep; Defend is a list, below) its
**name** in Crimson 14 sp semibold (wraps, after a hyphen if need be, never cut). **Card numbers** (`CardNumbers`) follow
in a strip of their own under the name, so they hide none of the card's title or text: the Initiative, then the
primary action's value, then Range, then Movement, each with the game's own item icon and the figure in 16 sp bold
(a card under 100 dp wide shows the first two, on one line); from the catalog, growing with the text only to 1.3×. On cards to choose
(not a defence, which shows its total instead), under the card beside an action question (name and numbers) and under
the hero sheet's hand (name and numbers). The scan is for recognition, the strip for reading. The foot of a card question is one row: a steel **Read card** button (book icon; the chosen card, else
the first, in the card viewer with its words as text) and the bronze commit button. **A tap never both shows and commits:** a tap chooses a card (`TableUi.selected`; lifted 8 dp, a 3 dp
bronze edge and a bronze tick at its corner; TalkBack: "Chosen", its click "Choose", "Look at the card" a custom
action), a tap on the chosen card reads it in the **card focus** viewer (below), and only the bronze button
under the cards answers, naming the commitment ("Discard Magnetic Dagger", "Play Shock facedown", "Defend ·
Blink Strike", "Upgrade Liquid Leap"); before a card is chosen it is there, dimmed, saying what to do ("Choose a
card to discard"), so the panel doesn't jump. One extra tap at most: choose, then commit. **Level up** (which card to
upgrade, ADR-045) is a row per card that may be upgraded: the hand card framed 2 dp in its colour (the
choice, chosen and committed as any card), an arrow, then the two cards it would become with their item (the official icon and "+1
Initiative"), three cards across the panel (60–100 dp); under each card the spells it names (Gydion,
landscape, as wide as the card, tap to read); "The card on the left is the choice…" as the rule row (p.15) under the rows.
Keep's two cards have their spells under them the same way. Whenever the question is the person's
own (any kind, game and puzzles) the resolution flow and then the moment (reveal, turn over, the end of
round summary) come after it, and a new question opens the panel at its top. **Defend** (`DefensePanel.kt`, UI hill
climb round 5) is the attacking card's text box, "Arien attacks · melee" on its tab, the attacker's card (26 dp, tap to
read) and the Attack total; beside it, when no card saves the hero, that is said **once** ("No card saves Tigerclaw",
in bold), then the minion modifier ("Minion defense +1 each": counted in every total below); it carries "Discard a card to defend"
for TalkBack. Then the options as a **list** in one plate, a one-line `SelectablePlate` row per hand card (48 dp, its
hairline between): the card small (26 dp), its name as `CardTitle` (caption size), and at its end, in neutral text, its Defense total as
the engine counts it against the Attack and the outcome in a word in weight ("Def 2 vs 6 · **Defeated**"; p.14: "If you
cannot defend successfully, or choose not to defend, you are defeated"); only a saving card carries a mark (a teal tick,
"· **Defends**"), a failing one is never styled as an error. A Block settled up front says "Block fails · Defeated" or
"Blocks it"; a card whose text decides shows that text verbatim in italic Crimson under its name. A chevron (48 dp)
opens a card's own text in its row. TalkBack hears each row in words ("Defense 2 vs Attack 6"; chosen, how its total
is counted from the engine's numbers only, "Defense 2 = card 1 + minion 1 vs Attack 6"). Not defending is a
**full-width row of its own** under the list: "Don't defend · keep your cards" (p.13 at its end) and under it **what it
comes to**, played out by the engine on a fork of the game (`defeatPreview`: its own events, never the app's
reckoning): "Defeated", the Life counters the team spends with the counter's mark at 20 dp ("Blue −1 (1 left)", or the
last one and who wins), the coins each hero collects ("Arien +2"); TalkBack hears it in words with "Tigerclaw may
respawn before acting next turn". Where the engine can't be asked (online, a puzzle) the row says "Defeated", p.14.
**Nothing is chosen for the player**, except that when no card saves the hero, not defending is chosen from the start
and the button reads "Accept defeat · keep cards". Read card and the one-line button ("Defend · Blink Strike"; "Choose
a card, or Don't defend" until something is chosen) close it. A hand of four and not defending fit a 411 × 891 phone
with the commit in view. The list takes what the panel's room leaves (`LocalQuestionRoom`: the question's share of the
screen, 0.56, measured from where the question starts) and scrolls inside it; when cards are below, a bronze pill at
its foot, a line of the plate under the rows, says how many ("+1 more card", a tap scrolls to them), not a fade alone; with large text a row puts the name over its outcome. It never
gets less than two rows, so with large text the panel itself scrolls. A losing choice stays possible, never a
surprise. Declines and Pass are outlined. With large
text the asking card goes above the answers. The computer's move shows its portrait, band and strength
("COMPUTER · HARD 7") and prompt
and the card it resolves (56 dp, tap to read), a linear progress bar once it is "thinking…".
**Resolution flow** (ADR-046; `play/table/ResolutionFlow*.kt`), under the question: what happened since
the person last chose, **one entry per hero's card resolution** (plus one for the end of a round, and
one for the turn's start when something happened there), oldest first, at most 8 ("3 earlier cards in
the game log"). An entry is a surfaceVariant card (10 dp corners): collapsed, one 48 dp row with the
hero's portrait in its team ring (a flag for the end of round), the card played as a 30 dp official
thumbnail, the hero's name in the overline in its team colour ("BROGAN · YOU"), and the outcome in a few
words in bodyMedium — "Onslaught → Wasp defended with Lift Up (4 vs 4)", "Shock → Brogan defeated · Arien
discarded Liquid Leap", "Mad Dash → Wasp defeated: Lift Up fell short (5 vs 6)", "Cantrip → cast Magic
Missile · Blue melee minion defeated", "Liquid Leap → moved 2", "fast travelled to Lane Zone 1", "held";
"End of round 1 · Minion Battle: Red removes 1 · Arien reached level 2". A chevron opens it to its
steps in the engine's words, each with a 3 dp bar: the action chosen (with the card, 40 dp, tap to
read), moves, each attack with its total, **the defending card as the card** (48 dp, tap → the card
focus viewer captioned "Against Attack 4 · Defense 5 - 1 (adjacent enemy melee minion) = 4. Defended")
with its Defense sum and a verdict in words and shape ("✓ Defended" secondary, "! Defeated" error),
discards with the open card a discard names, pushes, places, coins, defeats, effects starting. **The
person's attack answered by an enemy** (an enemy hero defended against, or was defeated by, one of the
person's heroes) starts open and its line is semibold with a 1.5 dp border; an entry with a line a
player must not miss (a hero defeated, a Life counter, a Lane push, a level up, the Minion Battle) has
the bronze border and a semibold line, and those steps are semibold with a bronze bar. Nothing new
since the last answer: the last entry before, collapsed, marked "EARLIER". TalkBack: each entry is one
button read as a sentence ("Brogan, your hero, played Onslaught: Wasp defended with Lift Up (4 against
4)."), with "Expanded" / "Collapsed" and the action "Expand" / "Collapse"; a step's card is its own
button ("Wasp's defense card: Lift Up, …", "Read the card"). Built only from the events (p.4): a card
appears only where the log names it. The hidden panel's peek and the log sheet are unchanged (the log
keeps every line). The coach's nudge is a secondary-container card with "Choose anyway"
(outlined) and "Rethink" (filled). Game over leads with "You won" / "The computer won" in a card
bordered in the winner's colour, the rulebook reason (no page), then **How each hero did** as a grid under one
`SectionHeader`: a head row of icons over 13 sp words (Level, Hero KOs, Minion KOs, Deaths, Coins; "Hero / KOs" on
two lines; the game's crossed blades and coin) in fixed columns as wide as their longest word plus air, a row per hero (portrait in its team ring with the mark on
its foot, name, who played it, wrapping) with its figures in those columns (20 sp, a zero muted); with large text the
figures go under the name. Then the report ("Review your decisions", in view over the bar, with what it is under it:
"A replay of your decisions: the coach marks each choice you made against its best play. The result stands."), the coach's turning points
by round and turn, and an outlined New game; **Rematch** (bronze, with its icon) and **Done** (outlined) are pinned under
the page on the slate, as every flow's commit is (online: Done alone). New game reuses New board's picker; heroes not
yet playable are veiled and marked "Later". Options: the line-up plate, then
**You play** as "One hero | Whole team" (2 v 2 and up) and portrait chips for the heroes or the teams (the
chosen one bordered in its team's colour with a check), "The computer plays …" under them; strength, length
(each segment with the counters it starts with, as the engine sets them up: "Quick / 3 Waves · 4 Lives", "Long / 5
Waves · 6 Lives"; the note under them, "With 4 heroes; Lives are per team (p.5)"), map and a Coach switch row, all above the fixed
button "Start game · flip the coin" (it starts the game; the coin flip is how). The page fits a 411 × 891 phone whole:
the Coach row ends clear of the button. **Computer strength** (ADR-039) is a Material 3 slider, 1 to 10 with a tick per step
(default 5), the value and its band above it in the primary colour ("7 · Hard"; bands 1–3 Easy, 4–6
Normal, 7–8 Hard, 9–10 Expert), "Gentler" and "Stronger" under the ends, and the band's one-line note
under that (1 and 10 have their own); TalkBack reads "Computer strength, Strength 7 of 10, Hard".
Under "Advanced: a strength per seat" each computer hero gets the same slider with its name and value
on one line and no end labels. The computer's strength also shows wherever its level did ("Computer ·
Hard 7": the hero sheet, game over, the resume line on Boards).
**Feed** (ADR-065; `play/table/EventFeed.kt`): over the map, under the round at a glance and beside the
map buttons, each new log line but the headings, the reveal and the facedown plays pops in as a notice
(10 dp corners, surfaceContainerHigh at 94%, a 1 dp outline-variant edge, bodySmall, at most 2 lines and
300 dp wide), the newest on top, sliding down and fading in (250 ms). At most 3 at once; each older
place dims (100%, 72%, 45%), and a notice fades out after 6 s. A line not to miss (a hero defeated, a
Life counter, a push, a level up, the Minion Battle) is semibold with a 1.5 dp bronze edge and stays 9 s.
It takes no taps and TalkBack skips it (the announcement says what happened). A resumed game starts it
empty; a take back clears it. **Game log** sheet: every event, a **plate per turn**, the latest turn first and
its events in play order, so cause reads before effect (revising ADR-065's newest-first within a turn); the
title as the top bar says it ("ROUND 1 · TURN 4"); each line with the hero it is about (24 dp portrait in the
team ring, the mark on its badge; a minion's team mark) and an icon for what happened (the game's sword, shield
and runner; push, defeat, Life counter, level, coins, cards); lines not to miss semibold in a bronze-edged box;
the bookkeeping (actions chosen, the reveal, cards played facedown) folded under "Details · n" (TalkBack:
Expanded / Collapsed). A review's "See it in the log" opens that turn with the line marked.
**Hero sheet** (ADR-044; `play/table/HeroState.kt`, `HeroDetail`): opened by a tap on a hero's chip or
piece (when the tap is not an answer) and by a long press on any hero's piece (a haptic tick; the press is
then the long press's, no tap or pan) or track card, and by **State** in the top bar (both orientations); TalkBack:
"Open Wasp's state" on the space, cell and card. The hand is a grid as wide as the sheet (four to a row on a phone,
every card whole, round 4); smaller rows (discard, active effects) scroll with an edge fade and end room; the sheet
ends with 40 dp under its last line. The sheet's title names the hero, so the header is the 56 dp portrait in its team ring
(faded off the board) with "◆ Blue · Computer · Hard 8" and "On the board · Lane Zone 2" (off the board:
the respawn line in the error colour, p.7), then the number row (Level, Coins, In hand, Ultimate,
counters). **THIS TURN · ROUND 2, TURN 3**: the card at 112 dp (a card back when it is facedown to you;
Emmitt's second card at 76 dp beside it; a 40 dp outline when there is none), beside it the state as a
word on a tag like the track's band (NOW bronze, NEXT teal, else surfaceVariant: FACEDOWN, CHOOSING,
LATER, DONE, PASS, OUT), one line of what it means, the action chosen, the initiative as it counts in
titleMedium ("Initiative 14 · printed 13"), the place in order; one TalkBack node that opens the card.
**THIS ROUND · TURN SLOTS**: four 64 dp slots captioned Turn 1–4 as on the dashboard (an outline where
no card lies; "No card resolved yet this round." before the first). **DISCARD · n**, then **ITEMS ·
P.16**: the official item icons (40 × 30 dp) with "+1 Defense ×2" in titleMedium, side by side as they
fit; for your own hero "Tucked under the dashboard: only you see which cards (p.4)." and the tucked cards
captioned "+1 Defense". **YOUR HAND** comes right after this turn's card (the cards a player most needs
together; any hero's "In hand · open in this puzzle" in a puzzle). Then Ultimate, Active effects, Markers
and tokens, Spellbook. From 600 dp wide (landscape) this turn's column (card and hand) sits beside the
round's cards, discard and items. When a hero's
piece is an option the board hint adds "Long-press a hero for their game state."
**Spellbook** (Gydion, ADR-040; `play/table/SpellbookSection.kt`): the hero sheet sums it up (after the
v0.8 hands-on pass, when the Spellbook sheet is a tap away): a SPELLBOOK head with "Open the spellbook",
then one surfaceVariant card (tap = the sheet; one TalkBack node) with each group's name and count in
titleMedium ("In your spellbook 19 · Cast · removed faceup 3") and, for anyone else, "Facedown: only
Gydion can look at the cards in the spellbook." The review's Now tab (no sheet there) and the Spellbook
sheet show the groups in full, in the engine's order: "In your spellbook · 19" (the
spell cards, for Gydion's player only), "Cast · removed faceup · 3" (everyone), "Not yet added · 22"
(only before the first Prepare Spells, with "Prepare Spells adds every spell card to the spellbook.").
Anyone else sees "In the spellbook · 19" and "Facedown: only Gydion can look at the cards in the
spellbook." (a puzzle, whose hands are open, says "Open in this puzzle."). The cards are the official
landscape scans in a grid as wide as the sheet (columns of at least 104 dp, wider with large text up to
×1.6, at most 6; whole-pixel widths so a row never wraps its last card); the images are the content,
no card text retyped. On the Spellbook sheet each group lists its spells by Tier under a small
labelLarge head ("Gold (no tier) · 3", "Tier I · 2", …, "Ultimate · 1"), as the catalog and the pre-game
sheet do, a Tier sharing its row with the next when both fit (no row a third empty in landscape); the
viewer still swipes through the whole group. For Gydion's own player each spell in the
spellbook that a card in his hand names carries a line under it, "Cast with Lesser Evocation" ("Cast
with Cantrip or Lesser Evocation"; labelSmall, onSurface; the names are the catalog card text's own
quoted spells; two lines at most, the size stepping down rather than an ellipsis) — a cast question's
mark replaces it while asked. The groups' counts are said there only: the sheet's number row
leaves out the engine's "spellbook" and "spellsRemoved" counters (it keeps "Wish casts"). A tap opens
the card focus viewer among that group. Cantrip's
"Choose and cast one spell" offers the spells as landscape cards that share the row's height
(shrinking down to 44 dp-wide equivalents to fit three), "tap it again to cast it", "Cast Magic
Missile". An unlocked Ultimate shows as its card ("ULTIMATE · p.16"), as it lies faceup on the
dashboard. **At any point** (extended 2026-10-26, `play/table/SpellbookSheet.kt`): whenever a hero the
person controls (`TableUi.mine`) has a spellbook, a pill under Fit and Flip (the hero's official deck
symbol beside a small parchment landscape card holding the spellbook's count) opens the **Spellbook
sheet** (one TalkBack node: "Gydion's spellbook, 19 in the spellbook, 3 cast"). It sits in the map
buttons' column, under the round at a glance, the turn track and the hero strip, so it never covers
them, and stays with the panel hidden. The sheet: "Your spellbook · Gydion", the groups as above, card
focus per group. During the person's own cast question a bronze banner with the asking card says
"Cantrip asks: … 2 spells are offered; choose in the panel. Expeditious Retreat: Not in the spellbook:
removed faceup (Prepare Spells)."; offered spells get a bronze edge and "Can be cast now", the card's
others are dimmed with the engine's reason (words and an edge, never colour alone). The panel's cast
question shows those spells too, dimmed with the block mark, and one line each under the cards. An
opponent's Gydion: his hero sheet's SPELLBOOK head has "Open the spellbook" (the same sheet, the
spellbook a count, "Open information (p.4)"). The review's rewound board has the same pill, built from
that board's view. Before the game: "Gydion's spellbook · 22 spells" (deck symbol, bronze outline)
under his row in New game's "You play", and "Gydion's spellbook" in his line-up chip's menu (New game,
New board), open the 22 spell cards by tier (Gold (no tier), Tier I to III, Ultimate); the draft's hero
peek has a full-width "Gydion's spellbook · 22 spell cards" button that opens his hero page at its
Spellbook. **Card shape:** deck cards are portrait scans (729 × 1026), spell cards landscape (737 ×
510); `ui/cards/CardShape.kt` decides by the catalog id (`<hero>-spell-…`) and `CardShapeTest` checks
every bundled scan. A landscape card is never put in a portrait frame: in a row it keeps the row's
height and is about twice as wide (`TableCard`'s `width` is a portrait card's).

**Suggestions** (ADR-051; `play/game/CoachSheet.kt`): a full-width outlined Suggest button with the lightbulb, last in the
question's panel (under the Coach's "looking" line), never in the way of the answers; on the person's own question only, Coach on or off. The sheet is a
`BoardSheet` at 90% of the height: "Suggestions", "For Wasp · 12 guesses of the hidden cards", Done. "The coach is thinking…" with a small spinner until it has looked (a live region). One
12-14 dp surface card per option, the first one with a 2 dp bronze border unless the options are too close to rank (then a line above them says so). In a card: the official card at 64 dp when the option is a card, "1. Shock", "About 45% to
win", how firmly the guesses rank it, the points behind when there are any; **Why**, **Against it**, **In most playouts** as small bronze heads, each reason's sentence in body text and its rule and page under it in small text; a 48 dp
text button at the end: "Show on the board" (spaces, units, tokens: selects it, shuts the sheet, the table shows it and asks for Confirm) or "Read the card" (the card focus viewer over the sheet). Neither answers. Then the overlines WHAT CAN HIT YOU (a 32 dp portrait in its team ring beside each sentence),
THE ROUND (the cards at 56 dp in order, the sentence, its reasoning) and TIPS FOR WASP (the official tips, verbatim, bulleted, the source named). TalkBack: an option's head is one node ("1. Shock. About 45% to win. Best in most guesses"), each reason one node, heads are headings. Chances are words, never colour alone.

**Your decisions** (`play/game/GameReportSection.kt`): on the game-over screen, above Rematch, under COACH · YOUR DECISIONS: an outlined "Review your decisions" with its hint; then a line with the count and a determinate bar, and Cancel (a live region); then the accuracy as a title ("78% best or good, over 112 decisions"), the five marks with their counts in small text (words, not colours), "By hero" (with more than one hero) and "By kind of decision" one line per row, and "The biggest mistakes": a secondaryContainer card per mistake with ROUND 2 · TURN 3, the mark, the question, what was chosen and the points given up, the position in small text, "Show what the coach suggests" (the sheet's content in place) and "See it in the log" (the log sheet opens at that moment, its line marked).

### Puzzles (ADR-035, PRODUCT §10)
The puzzle screen is the game table (above). **The decision first:** while a question is asked the panel's head is
three short lines, the **goal** (flag icon, titleSmall) and "You play …" under it, with a steel **Setup** button
(book icon) at their end, then "title · Puzzle n of m" with the **difficulty mark** (three small hexes, filled up to the level, and the
word Hard / Harder / Hardest: shape and word, never colour alone); then the nudge if any, then the question, so every
choice and its Confirm are in view at 100 % on a 411 dp phone. **Setup** opens a sheet with what you know in full:
the goal and who you play, WHAT YOU KNOW with the theme, and the intro one sentence a line, word for word
(`PuzzleText.sentences`); nothing is ever cut with an ellipsis to make room. A **nudge** is the green parchment text box with "NUDGE 2 OF 3"
on its own tab, like ATTACK or COACH (×, Another nudge); the last nudge's move is a **dashed bronze ring**
around its space on the map (apart from the teal options and the white selection; TalkBack: "hint").
The other side's replies appear line by line under "<HERO> ANSWERS" with a progress bar; once they are
told, the resolution flow (as in the game) shows them grouped by card under the next question and
under a miss. A **miss** is
a quiet surfaceVariant card ("Not quite" in Cinzel, the reason, filled "Try again from before that
move", outlined Restart, text Show solution); never error red. **Solved** is a primary-container card
with a 2 dp bronze border and the original **seal** (a bronze hex with a tide-teal tick and six
points, drawn in code, opening out once with emphasised deceleration), then, before the explanation, **Next puzzle**
(bronze, with its arrow) beside "Replay solution" (steel; stacked with large text; the last puzzle: "Replay the
solution on the board" alone, bronze), then WHY IT WORKS as numbered lines (the explanation split sentence by sentence, a full round's by turn with a
"Turn 2" mark; never reworded), then RULES. The list has two parts, POSITIONS · 50 and FULL ROUNDS · 22, each with
"n of m solved"; the Continue card says "Up next · Puzzle 2 of 50", the count the puzzle's own screen uses. The
difficulty filter is one checked segmented bar, **"All" checked** by default, then Hard, Harder, Hardest, each with its
number of positions under the name ("All / 50", "Hard / 15", as "1 of 50 positions" under it counts), so it reads as a filter at
once (one difficulty at a time).
The **replay** panel has Back / Next arrows around "SOLUTION · STEP n OF m", the move in titleMedium
(its card beside it), WHAT HAPPENED, and the explanation below. The list's numbers are Cinzel in a
bronze ring (filled when solved); Continue is a secondary-container card (all solved: the seal).

### Card focus (selecting and reading cards; `feature/board/ui/cards`)
One viewer for every card the board screens show (the Game state review's too): the cards a question offers (play, discard,
retrieve, defend with, upgrade, keep), the "Asked by" card, your hand, a hero's played, resolved and
discarded cards, a puzzle replay's card, and the sandbox's "Read the cards".
- **Opening** (a tap on a card to read, or on the card a question has chosen, see the panel): the card grows out of it (scale and translate from the thumbnail's
  bounds, 340 ms emphasised deceleration) into a large card over a near-black scrim (93 %, the same in
  both themes; light text on it). Portrait: × with the question's title ("WASP · CARD", bronze overline)
  and "Card 2 of 4" with page dots on the scrim (the spells a card names are said as its pages: "Card 2
  of 9 · spell 1 of 2"), the card up to 60 % of the height (50 % above font
  scale 1.3; on a phone it is as wide as the screen allows; it gives way to the panel, down to 30 % /
  24 %, before any line of the panel is squeezed: the panel is measured for every card of the viewer,
  the tallest counts), then a surface panel (28 dp top corners) at the bottom, **as tall as its words**: only what the card doesn't print (ADR-041: the scan is the
  content, nothing it shows is retyped): the question's context ("Play a card facedown", "Arien
  attacks · melee · Attack 6"), a caption ("Turn 2 · Active"), the verdict ("! Falls · Defense 2
  against Attack 6", also as a pill on the card's corner; the Defense total says what items and
  minions change), why a card isn't an option, what choosing it means (Keep: "… goes to your hand",
  "… becomes an item: +1 Defense (p.16)"; Upgrade: the two cards of the next Tier by name, shown large
  as the next pages; an upgrade card itself: "Upgrade of Liquid Leap" and which card to choose instead, ADR-045; a
  card that names spells: "The spells it chooses from:" with each spell small, a tap goes to its page), a Handicap card's basis ("a weaker version of the Gold card (frame colour, p.20)"), its item
  stat with its item icon; then **"Read the card as text"** (collapsed, article icon and
  chevron; TalkBack hears Expanded / Collapsed): open, the name, kind, printed values and the text
  verbatim, for anyone the scan doesn't serve (the switch holds while swiping; in portrait the card steps back to 36 % of the height, 26 % with large text, and the words scroll into view). Landscape: the card on
  the left at full height; on the right a 48 dp header and the panel at the bottom, tighter spacing,
  the card text in bodyMedium and no "Swipe up…" line. The details
  scroll in both orientations, with a fade at the bottom while there is more. The scrim turns opaque
  behind the header (fading out just below it), so the screen's top bar never shows through.
- **Only the button confirms** (a tap on the large card is for reading, never a commitment): the panel
  ends with a filled button naming the choice for the card on show ("Discard Magnetic Dagger", "Play Shock
  facedown", "Defend with …", "Upgrade …", "Keep …", "Measure with …") and "Swipe up or go back to choose
  nothing."; pressed, the card presses (scale 0.93, 90 ms), settles with a teal tick seal, the phone gives
  its Confirm haptic, then the answer goes out and the card fades away. A card that isn't an
  option is veiled with a "Not an option" pill; its button reads "Not an option". A card shown with a question as what an answer leads to (an upgrade
  card, `FocusCard.answeredBy`) is not veiled: its caption says whose upgrade it is, a calm info line
  says which card to choose, and its button is an outlined "Go to Liquid Leap" (also a TalkBack
  action). While the game is busy the button reads "Wait…".
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
  name, kind, values and text (the card's data, as the scan shows it), verdict or reason and "card 2 of 4", and custom
  actions Next card, Previous card, Close; the Confirm button (the only way to answer) and × are ordinary buttons.
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
- Otherwise show them as-is (crop and resize only); don't redraw or recolour official art.
- Since ADR-053 the rulebook's and the cards' own parts may be the frames. Beyond crops, only what `official/SOURCES.md` records: toned miniatures, flattened photos of the mats, and the frames the print has too few pixels for (ADR-058): redrawn after it (banner, title plate, text boxes, plate brackets; `design/look/render_parts.py`) or, where illustrated, upscaled (dashboard frame) or recreated at full resolution with the old part as the reference (the battlefield silhouette, round 4; `third_party/ASSETS.md`). Redraw a frame when it's visibly soft at its size on a phone and no sharper copy is published; never a card, a portrait, a symbol or a board, and never anything that carries game data.

**Original app assets** — everything that is the app itself rather than the game.
- App icon: adaptive icon (trident-and-shield sigil on a deep-ocean gradient) plus themed
  monochrome layer.
- UI icons not in Material Symbols, empty-state illustrations.
- Hero **sigils**: small geometric emblems per hero (24-unit grid, 2-unit stroke), now only the
  fallback when official art (portrait, deck symbol) is missing or still loading.
- Player avatars: a player with rated games shows their most-played hero (games, then wins, then hero id; `RatingsResult.mostPlayedHeroes`) as the official portrait in a steel ring, and no two players share one (players claim in order of games played; a taken hero passes to their next most-played, none left is the crest); derived from the log, so nothing is stored or synced. Only on player-centred screens (leaderboards, the player page, this phone's player) and only from 40 dp: smaller, or among heroes (meta, draft, match seats), the crest. Otherwise one of 24 original crests as filled heraldic shields in the game's icon style (dark outline, metal rim, a field lit from the left, the charge in dark ink with a light emboss). Even crests are a bronze field in a steel rim, odd ones steel in bronze, so neighbours differ at a glance; red and blue are the teams' alone (gear and star), so a crest never reads as a side; charge and metal are the identity. The charges are line masters in `design/avatars/` (the web draws those, tinted); `python -P -E design/tools/crest_shields.py` sets them on the shields in `design/crests/`, which `svg2vd.py` converts.
- Pipeline (as built): hand-authored SVG in `design/` → `python -P -E design/tools/svg2vd.py`
  → `core/designsystem/src/main/res/drawable/` (and `app/.../drawable/ic_launcher_*`). The
  converter is deliberately small (path/line/poly/circle/ellipse/rect, groups untransformed or
  `translate() scale()`, user-space gradients) and refuses anything else, so output is predictable;
  `--check` reports stale drawables. Review renders in `GalleryScreenshotTest`; the SVG is the master.
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

### Destinations (`core:designsystem/icon/Destinations.kt`)
A destination keeps one glyph and one name on every screen that links to it (`Destination`): a flag is Match setup
(Timer start, a pending match in the log, a finished draft), the hourglass the Timer, the fanned cards the Draft, the open
book the Rules, the two arrows Group sync, the hexagon the board. A bar glyph that would not say where it leads is named:
Players' "Group sync", the draft setup's "Methods" and New board's and New game's "History" (the line-ups of past
drafts and matches, there only when there is one) are words on the bar. Match setup's odd or
large player count is one line, "Advanced player count", with "Read the rule" opening the rulebook's words.

