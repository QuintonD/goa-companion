# Product

## Goal

The app a GoA2 group leaves open on the table all game: it keeps the pace, settles "who is
best with Tigerclaw", runs the draft, and answers questions about a hero without digging
through cards. Private build for one play group (ADR-006).

**Users:** a regular play group (2–10 players, usually 4–6), one phone passed around or
propped up. **Quality bar:** feels like a first-party premium app — fast, polished,
accessible, no placeholder UI shipped. Terminology follows the rulebook (DOMAIN.md).

**Prior art:** BGG thread 3504878 "Guards of Atlantis Timer" (fan app with draft modes,
timers, stats). Study it for ideas and pitfalls; don't copy code or assets from it.

## Features and acceptance criteria

### 1. Hero catalog
- All 32 heroes from **every product** (Core, Devoted, Defiant, Renowned, Arcane, Wayward),
  2nd printing; ready for the 3rd-printing heroes when they appear.
- Grid/list with official artwork (sigil fallback), name, title, complexity ★–★★★★, roles,
  product. The grid shows three heroes across a phone at any font scale; the list is compact
  (six or more rows per screen); the grid/list choice is saved.
- Filter by complexity, role, product, attack style (Ranged / Mixed / Non-ranged attack cards, see
  DOMAIN.md); full-text search over names, aliases, card names and card text. Search lists
  every matching **card** (whole phrase > all words), ranked above heroes matched only
  partially; tapping a card opens it in the card viewer. When the owned-products collection
  hides every match, the empty state says so and offers "Show all products".
- Hero detail: stat bars (Attack/Defense/Initiative/Movement, base → max), roles, full deck
  laid out by colour and tier (basic → I → II → III → Ultimate) with official card images
  (tap to zoom, swipe between cards), card text and values, item each card grants, Handicap
  card (which basic card it replaces), extras (Gydion's spellbook grouped by tier), Ultimate
  tiles with their text, **FAQ rulings**, and personal stats (games, win
  rate, Elo) from the match log.
- Unverified data is visibly badged. Works fully offline.
- **Done when:** every hero passes catalog validation, search returns in <100 ms, screenshot
  tests cover grid, filters and detail in light/dark at font scale 1.0 and 2.0.

### 2. Match setup and timer
- **Setup:** pick mode (Quick / Long / Double lane) and players → app shows Wave and Life
  counters to lay out (DOMAIN.md table), handicap reminders for odd counts, a "House rule" label
  wherever the double-pilot count gives different numbers than the rulebook's per-player rules,
  the Double lane rules (p.20) when Double lane is chosen, and which team's symbol shows on the
  Tie Breaker coin. After a draft the coin is the draft's (leave it as it is; "Correct the coin"
  fixes a mistake); without a draft the app flips it. The coin is saved to the match and the
  timer starts with it.
- **Rule timers per phase** (owner's table rules): Pre-round strategy 1:30 → 4 × Turn 0:30 →
  Upgrade 2:00 during the end-of-round Level Up step; optional per-hero Action timer. Every
  phase is configurable (on/off, duration, cues, auto-advance) and presets are saved/editable.
  Defaults in DOMAIN.md.
- Big, glanceable countdown; tap to pause; next/back; round and turn pips; overtime counts up.
  Each turn is "Play a card" (timed) then an untimed "Resolve" step after the reveal, so
  resolution never shows as overtime. Back right after an accidental Next restores the phase
  with its time; double taps don't skip phases. Side-by-side layout in landscape.
- Table rules (house rules, per preset): on-demand "Hurry up" countdown, optional team
  time-outs, opt-in volume-key control; "turn length tonight" adjustable from the start card.
- Action timer: tap "next" as each player resolves; shows whose turn it is if heroes known.
- **End of round:** tap-through checklist of the six official steps (each with a short
  rulebook summary, p.8 and p.15), then next round.
- Haptic + audio cues at configurable thresholds (e.g. 10 s, 5 s, 0).
- Correct when backgrounded, locked or after process death: countdown notification with
  controls (promoted Live Update on Android 16+), alarm at phase end. Screen stays awake on
  the timer screen.
- **Done when:** drift <250 ms over 30 min, phase-end alert fires with screen locked and in
  Doze, state survives process death and reboot, all controls work with TalkBack.

### 3. Players, matches and meta
- Player profiles (name, colour, avatar from original avatar set). No accounts.
- Log a match: date, mode, teams (Red/Blue), hero seats with their pilot (a player may
  **double-pilot** two heroes), handicap flags, winner, win
  condition (Throne / Last Push / Life counters / Other), duration, notes. Draft → pending
  match automatically; timer session can fill in duration.
- **"Who won?"** when a game ends (timer summary, or a pending match in the log): winner and
  win condition first, everything else optional below (each winner button names that team's
  pilots, so the table confirms who it credits at a glance); dated from the session start. Saving
  shows each player's rating change, their rank and who is on top, plus **Rematch** (same
  seats / swap heroes / swap sides / draft again → pending match → Match setup). Pending
  matches can be resumed into setup and the timer from the log. The pilot picker can add a
  player inline.
- Elo per player and per hero (seat-based, double-pilot aware, tunable — DOMAIN.md / ADR-011),
  provisional flag for <10 games, rating history chart.
- Meta dashboard: last game night (matches, biggest movers, streaks), how games end (win
  conditions, sides, average length by mode), hero win/pick/ban rates as a compact sortable
  table or cards, best player per hero, rivalries and teammate pairs (Wilson-ranked, minimum
  sample), strongest hero pairs. Profiles add the player's win-condition mix, head-to-head and
  teammate records, and dates under the rating chart.
- Edit/delete any match; all ratings recompute.
- Export/import full data as a versioned JSON file (backup, moving phones): players, matches,
  drafts, rating constants, app settings, timer presets and custom draft methods. Android Auto
  Backup enabled for the database and settings.
- **Done when:** recompute of 1,000 matches <1 s on device; import→export is lossless.

### 4. Draft
- Choose players and teams (manual, balanced by Elo, or official Player Draft), complexity
  filter, hero pool (all, owned products, or custom).
- Methods: the group's **House draft** (default: pool of 15, A ban+pick, B ban+2 picks,
  alternate pairs, single last pick) plus the official All Random, All Pick, Single Draft,
  Random Draft and Pick and Ban (exact sequences in DOMAIN.md); custom methods can be saved. Animated Tie Breaker coin flip decides Team A.
- Pass-and-play UI with clear "Team Red — Ban" prompts, hidden picks where the method needs
  them, optional per-step timer, undo at every step (with "Undid …" feedback and Redo), then
  assign picks to players. The step banner stays on screen while the pool scrolls; the pool is
  dense (about five tiles across) with name search, role filters and "Hide taken", and taken
  heroes shrink to chips. Long-press a hero for a peek (★, roles, stats, starting cards, link to
  the hero page); each team shows the roles its revealed picks cover. Single Draft shows the
  current player's own heroes first.
- The setup form (players, teams, method, pool, coin) survives leaving the tab, Back from the
  board and process death; after a draft starts, the next game defaults to the same players,
  teams and method (one "Clear" forgets them).
- Finished draft → "Start match" creates a pending match and opens setup/timer.
- **Done when:** every method completes legally for 2–10 players (property tests), undo works
  at every step, and the result flows into match log and timer.

### 5. Rules reference
Fast rule lookup at the table, starting with the arithmetic players get wrong every game (BGG
research, 2026-10-17). A reference, not a card cheat sheet: hidden information stays hidden (p.4).
- **Levels & defeats** (bottom sheet): pick the defeated hero's level → Life counters their
  team spends, coins for the defeat, assist coins; the full table for levels 1–8 (lowest card
  Tier, Coins (Defeat), Assist Coins, Life counters, next level's cost, max card Tier) from the
  rulebook's p.13 / p.15 / p.23 tables; the defeat and level-up rules quoted verbatim (level up
  is mandatory and simultaneous; "If you are defeated, use your correct Tier and Level"; minion
  coins; Pity coin); and the cards of the match's heroes whose text changes Life counters or
  assist coins (e.g. Bain "Dead or Alive", Ursafar "Tear", Gydion "Energy Drain", Rowenna
  "Glorious Triumph"), quoted from the catalog, never computed.
- One tap from the running timer (top bar; in the menu at large font sizes) and from the Level
  Up end-of-round step; also from the Rules screen.
- **Quick reference:** 11 searchable topics (turn sequence & tied initiative, hero respawn,
  attack/defense/defeats, minions & Heavy minion, the 6 end-of-round steps, levelling up & items,
  Active effects, pushing the Lane, table talk & hidden information, ways to win, double-lane
  map), each made of verbatim rulebook excerpts with printed page numbers. Anything in our own
  words is labelled "Summary".
- Reached from the Heroes tab top bar (Rules) and the timer's menu.
- **Done when:** every excerpt passes the importer's drift check against the pinned rulebook
  (`--rules-only`), the ladder's numbers pass their table and rule tests, and the ladder and a
  topic page have light/dark (and font scale 2) screenshots.

### Settings
Theme (dark default / light / system), owned products (default: all), timer defaults, cue
sounds and haptics, Elo constants (advanced), export/import, About/credits.

## Non-goals (v1)
- Online play, accounts, cloud sync, ads, analytics SDKs, any network access from the app.
- Play Store or any public distribution — sideloaded only.
- Full rules engine or board-state simulation (we don't track minions, coins or positions).
- iOS; tablet-first layouts (phone-first; tablets must not break).

## Open questions (owner to decide)
- Which printing the group plays (data targets the 2nd printing / upgrade kit; a 3rd printing
  is crowdfunding for 2027 — the model supports multiple printings).
- Later: optional Life/Wave counter tracker during a match? Glicko-2 alongside Elo? (Research
  2026-10-17: a standalone tracker duplicates the physical counters and card effects change the
  counts; at most as part of an optional defeat/push event log.)
- Later: general goa2-faq rulings in the rules reference (labelled as community rulings).
