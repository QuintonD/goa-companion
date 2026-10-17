# Product

## Goal

The app a GoA2 group leaves open on the table all game: it keeps the pace, settles "who is
best with Tigerclaw", runs the draft, and answers questions about a hero without digging
through cards. Private build for one play group (ADR-006).

**Users:** a regular play group (2–10 players, usually 4–6), one phone passed around or propped
up; friends may have the app on their own phones too, kept in step by group sync. **Quality bar:** feels like a first-party premium app — fast, polished,
accessible, no placeholder UI shipped. Terminology follows the rulebook (DOMAIN.md).

**Prior art:** BGG thread 3504878 "Guards of Atlantis Timer" (fan app with draft modes,
timers, stats). Study it for ideas and pitfalls; don't copy code or assets from it.

## Features and acceptance criteria

### 1. Hero catalog
- All 32 heroes from **every product** (Core, Devoted, Defiant, Renowned, Arcane, Wayward),
  2nd printing; ready for the 3rd-printing heroes when they appear.
- Grid/list with official artwork (sigil fallback), name, title, complexity ★–★★★★, roles,
  product. The grid shows three heroes across a phone up to font scale 1.3 and two above it,
  so names never break inside a word; the list is compact (six or more rows per screen); the
  grid/list choice is saved.
- Filter by complexity, role, product, attack style (Ranged / Mixed / Non-ranged attack cards, see
  DOMAIN.md); full-text search over names, aliases, card names and card text. Search lists
  every matching **card** (whole phrase > all words), ranked above heroes matched only
  partially; words may span a card and its hero, and a card's kind narrows a multi-word query
  ("brogan ultimate", "tigerclaw red tier 2"). A result shows the line that matched (a "Choose
  one —" lead-in comes with its options); tapping a card opens it in the card viewer. Results
  start at the top whenever the query, filters or sort change. Back with a search typed clears
  it. When the owned-products collection hides every match, the empty state says so and offers
  "Show all products".
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
- **Two views of the running timer** (device preference, Settings → Running timer, and a switch
  in the timer's top bar that applies and saves at once; default Step by step):
  - *Step by step*: one step per screen around the big dial; Next and Back walk the round.
  - *Whole round*: every step of the round on one scrolling list under Round start / Turn 1…N /
    End of round headings, each with its length ("0:30", "Untimed") and context (which Action
    hero, which checklist step carries the Upgrade timer). Done steps are ticked, the current one
    is expanded with its live clock, progress bar, warning/overtime colours, rules hint, Level Up
    rule and ladder, and the Action seat chooser; tap its clock to pause. The list keeps the
    current step in view when the step changes, but never while someone is scrolling it. Tapping
    another step asks "Jump to …?"; the jump starts that step fresh and Back right after it
    returns, like an accidental Next. Header, Hurry up, time-outs and the controls stay pinned;
    landscape puts the list beside the current step's clock and controls.
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
  seats / swap heroes / swap sides → Match setup, saved as a pending match when its timer
  starts; draft again → the draft). Pending matches can be resumed into setup and the timer from
  the log; on the Timer tab they show each team's heroes and when they were set up, with
  Discard. The pilot picker can add a
  player inline.
- Elo per player and per hero (seat-based, double-pilot aware, tunable — DOMAIN.md / ADR-011),
  provisional flag for <10 games, rating history chart.
- Meta dashboard: last game night (matches, biggest movers, streaks), how games end (win
  conditions, sides, average length by mode), hero win/pick/ban rates as a compact sortable
  table or cards, best player per hero, rivalries and teammate pairs (Wilson-ranked, minimum
  sample), strongest hero pairs. Profiles add the player's win-condition mix, head-to-head and
  teammate records, and dates under the rating chart.
- Edit/delete any match; all ratings recompute.
- **Share stats page** (ADR-027), for friends who can't run the app (iPhone players whose matches
  an Android phone logs): the share icon on the Meta tab's top bar writes one self-contained HTML
  file and opens the share sheet. It holds the leaderboard (Elo, games, W–L, recent trend,
  provisional), each player's best heroes as their profile lists them, the hero meta (same
  "Not enough games yet" rule), the group stats the Meta tab shows, and the newest 100 matches
  (date, mode, teams with pilots and heroes, winner, win condition, notes), with "Generated …,
  ratings as of the last logged match" in the footer. All numbers come from the app's own
  calculations; nothing new is computed. Text only (no card art), light/dark, printable, opens
  offline in any browser; no scripts or network references.
- Export/import full data as a versioned JSON file (backup, moving phones): players, matches,
  drafts, tombstones and player merges, rating constants, app settings, timer presets and custom
  draft methods. Import "Merge" follows the group-sync rules (the later edit of a record wins).
  Android Auto Backup enabled for the database and settings.
- Duplicate players (one friend added on two phones): "Merge into…" on the player page, with a
  confirmation naming both and their match counts; refused, with the matches listed, when the two
  played in the same match.
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

### 6. Group sync (ADR-025)
Each friend has the app on their own phone (APK shared privately, ADR-006 addendum); the group's
phones keep the same matches, players, drafts, deletions and rating constants by syncing **face to
face at the table**, phone to phone over Bluetooth and Wi-Fi Direct (Google Nearby Connections).
No server, no accounts, no internet; the app still has no INTERNET permission.
- **Group:** "Start a group" (name) creates the group and its key; "Invite a friend" shows a QR
  code (and "Share as text"); a friend's phone joins by scanning it (Google's code scanner, no
  camera permission) or pasting the invite. Leave group keeps all data.
- **Join flow:** install → Group sync → scan → the first sync starts right away → "Which player
  are you?" (pick yourself, or add yourself: this is how a friend adds themself as a player).
  Your player shows "You" in the Players list, and the Players tab's top bar opens your page.
- **Sync session** (a sheet, from Group sync → Sync now or the Players tab's sync icon): one phone
  taps Host, the others Join (a joiner finds the host on its own; with two hosts in range it asks
  which). The host lists each phone with its progress and taps Sync when everyone is in; result
  summary per phone ("+5 matches, +1 player, 2 updated"). Permissions are asked when a sync starts,
  with an explanation; after a refusal the sheet offers system settings and the file fallback.
  Every stage times out; Cancel always works; the sheet can't be swiped away while merged data is
  being written.
- **Merge rules:** the later edit of a match, player or draft wins; a deleted match stays deleted
  everywhere unless someone edits it later; duplicates merged on one phone are merged on all;
  rating constants are the group's (the latest change wins). After a sync, two players with the
  same name get a "Merge into…" hint.
- **Fallback:** "Share sync file" (encrypted with the group key) through Quick Share, a message or
  Drive; "Import sync file" or opening the file merges it the same way, after showing what would
  change. A file from another group, or one this phone's key can't open, gets a clear error.
- **Done when:** merge properties (commutative, associative, idempotent, convergence, identical
  ratings) and the protocol over a fake transport (host + 3 phones, a dropped phone, a wrong key)
  pass on the JVM; the merged release manifest has no INTERNET permission; **two physical phones
  sync at the table** (Nearby can't be proven on emulators).

### Settings
Theme (dark default / light / system), owned products (default: all), timer defaults, running
timer view (step by step / whole round), cue sounds and haptics, Elo constants (advanced; shared
with the sync group), Group sync, export/import, About/credits.

## Non-goals (v1)
- Online play, accounts, cloud sync, ads, analytics SDKs, any network access from the app. (Group
  sync is phone to phone at the table, ADR-025; nothing goes through the internet.)
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
