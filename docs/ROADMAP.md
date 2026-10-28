# Roadmap

Update checkboxes with a date and one line of evidence (e.g. "tests green, device-verified").
Run the `ship-check` skill before ticking a milestone's last box. **Emulator ≠ device:** boxes
that say "S25 Ultra" stay open until checked on the physical phone.

All milestones were built in parallel on 2026-10-17 (owner request), then reviewed by
independent rules/engine reviewers and fixed. Evidence: JVM gate (`assembleDebug
testDebugUnitTest verifyRoborazziDebug lintDebug detekt ktlintCheck`) green; 9 end-to-end
instrumented tests green on an API 36 emulator (`goa_api36`), twice.

**Validation round, 2026-10-17 (afternoon):** independent rules audit against the rulebook PDF,
card-by-card check of 282 cards against the scans, BGG research into what groups need, and
hands-on game nights on S25-sized emulators with the release build; findings fixed on eight
branches (ADR-016–024), then an adversarial review of all new rules text and two final hands-on
passes (including an in-place upgrade from the morning build with real data). Evidence on
`main`: 694 JVM tests, screenshot, lint, detekt and ktlint green; rulebook drift check green;
10 end-to-end instrumented tests green on the emulator. QA reports: `D:\.goa-qa\`.

## M0 — Scaffold
- [x] `git init`; Gradle 9.8.1 / AGP 9.4.1 project, version catalog, `build-logic` convention plugins (AGP 9 DSL) — 2026-10-17, ADR-010
- [x] Module skeleton per ARCHITECTURE.md; Hilt (KSP), Navigation 3, theme from DESIGN.md tokens — 2026-10-17
- [x] Bottom nav with 4 destinations; About screen with credits and OFL licences — 2026-10-17
- [x] Lint, ktlint, detekt, Roborazzi, unit-test setup all green — 2026-10-17
- [ ] Debug APK installs and launches on S25 Ultra (emulator only so far)

## M1 — Catalog
- [x] `tools/catalog-import`: fetch sources, normalise, write hero JSON + images + `SOURCES.md` — 2026-10-17, 48 importer tests
- [x] Catalog loader + validation tests (schema, deck composition, asset paths) — 2026-10-17
- [x] All heroes from all products imported: 32 heroes, 632 cards, 664 images, 32/32 verified, 0 unresolved (`REPORT.md`); printed "+" values and bold keywords kept (dataVersion 2) — 2026-10-17
- [x] Catalog grid, search, filters, hero detail with card viewer; sigil set v1 — 2026-10-17, E2E `CatalogFlowTest`

## M2 — Match setup & timer
- [x] `TimerEngine` (pure, fake-clock tested, drift < 250 ms / 30 min) + `MatchSetup` (counters by heroes in play) + DataStore state — 2026-10-17
- [x] Timer screen, preset editor, action timer, end-of-round checklist, cues, `keepScreenOn` — 2026-10-17, E2E `GameNightFlowTest`
- [x] Alarm scheduling, countdown notification + actions, Live Update on Android 16+ — 2026-10-17 (Robolectric-tested)
- [x] Whole-round timer view (owner request): every step of the round on one list, current step expanded, "Jump to" with undo, Settings choice + top-bar switch (ADR-026) — 2026-10-17, `TimerEngine.jumpTo` / VM / mapper / settings / backup tests, 11 round-view screenshots (portrait, Action seats, Upgrade, landscape, font scale 2, light/dark); hands-on round on the `goa_api36` emulator (debug build), its fixes included; not yet on the S25 Ultra
- [x] Round view hands-on with the 0.2.0 release build on S25-sized emulators (384 dp, `goa_s25a`) — 2026-10-18: switch from the header (overflow menu at this width) and from Settings, both kept across a force-stop; jump + undo, pause, auto-advance, Action hero chooser in both views, a full round into round 2, a running timer kept across four in-place upgrades. Fixed on `fix/0.2.0-handson`: "Round N" clipped to "ROU" by Blue's coin chip at 384 dp (the header now measures and moves the view switch, then Levels, into the menu); landscape at 832×384 dp cut off the round view's clock and hid the step view's Hurry up under the controls. Screenshots at 384 dp and 832×384 dp added. Emulator only
- [x] Owner request: each turn times every hero resolving (0:30 each) instead of one untimed Resolve step; naming the hero starts their full time; stored presets upgraded (preset revision 2, ADR-029) — 2026-10-18, `TimerEngineTest`, `TimerPresetMigrationTest`, `TimerPresetsDataTest`, VM/mapper tests, 11 screenshots re-recorded, E2E `GameNightFlowTest`/`RematchTimerFlowTest` updated; not yet on the S25 Ultra
- [x] Owner request: "Speed guards" preset, 0:30 to strategise and 0:10 per hero resolving; added once to saved preset lists (preset revision 3, ADR-030) — 2026-10-18, `TimerPresetsDataTest`, `TimerPresetMigrationTest`, `TimerPlanTest`, repository/VM tests, preset and setup screenshots re-recorded; not yet on the S25 Ultra
- [ ] Device test on S25 Ultra: locked screen, Doze, process death, reboot, clock change, status-bar chip (checklist in ARCHITECTURE.md)

## M3 — Players, matches, Elo
- [x] Room 3 schema: players, matches, participants, drafts; exported schema + migration test baseline — 2026-10-17
- [x] Match editor and log, seat-based `EloCalculator` + replay (double pilots, ADR-011), rating screens with charts — 2026-10-17, E2E `DoublePilotMatchTest`
- [x] Meta dashboard; versioned JSON export/import (lossless round-trip test); Auto Backup rules — 2026-10-17
- [ ] Backup export/import file pickers exercised on the S25 Ultra
- [x] Game-night flow (ADR-020): tab state kept across tabs, notification/cold start → Timer, running-timer bar, "Who won?" with rating changes, Rematch, pending-match resume, backup v3 (settings, presets, methods), group stats and compact Meta table — 2026-10-17, JVM tests + screenshots
- [x] ADR-020 flows checked on the emulator (tab switches mid-form, notification tap, running bar, rematch → setup) — 2026-10-17, final hands-on pass + E2E `RematchTimerFlowTest`; S25 Ultra still pending
- [x] Game-night fixes (ADR-018 addendum, ADR-024): background phase-over alert sounded by the app; Start timer always shows the running timer; rematches saved on Start timer, pending rows with heroes and Discard; Hurry up pinned; Previous phase off the bottom edge — 2026-10-17, emulator-5556 (Home, screen off, Doze, Sounds off)
- [ ] Phase-over chime before Android 17 reaches the S25 Ultra: background-audio hardening would silence it (ADR-018 addendum)
- [x] Share stats page for iPhone friends (ADR-027): Meta tab → one self-contained HTML file (leaderboard, best heroes, hero meta, group stats, newest 100 matches) via the share sheet, no INTERNET — 2026-10-17, `StatsPageRendererTest` (escaping, empty group, unranked, double pilot, Unknown hero, 500 matches = 125 KB) + `StatsPageViewModelTest`, sample parsed clean and rendered at 390 px, Meta screenshots re-recorded, JVM gate green
- [x] Stats page shared from the release build on an emulator — 2026-10-18: the file's leaderboard equals the app's (7 players, Elo, W–L, trend), Meta numbers match, no scripts or network references (the only `url(` is the logo's own gradient), 20 KB
- [ ] Share stats page from the S25 Ultra and open it on an iPhone (Messages/WhatsApp → Safari, light and dark)

## M4 — Draft
- [x] `DraftEngine` with House draft + all official methods + property tests (2–10 players, seat layouts) — 2026-10-17
- [x] Draft setup (teams, balance by Elo, Player Draft, complexity filter, pool), coin flip — 2026-10-17
- [x] Draft board with hidden picks, undo, pick assignment; draft → pending match → setup/timer → result — 2026-10-17, E2E `GameNightFlowTest`

## M5b — Rules reference (ADR-019)
- [x] Level & defeat ladder sheet (one tap from the timer and the Level Up step), 11 verbatim quick-reference topics, Rules screen with search — 2026-10-17, `RulesReferenceDataTest`, `RulesViewModelsTest`, screenshots
- [x] Rulebook pinned by SHA-256; `--rules-only` drift check green against the 2nd-printing PDF — 2026-10-17, `RulesCheckTest`
- [ ] Ladder sheet over the running timer checked on the S25 Ultra (TalkBack, large text)
- [x] Adversarial rules review fixed (ADR-022): 5-card starting hands from card text, Primary-action and upgrade excerpts, p.20 counter table, derived Handicap labels, setup/timer wording — 2026-10-17, `StartingCardsTest`, `RulesCheckTest`, drift check green

## M6 — Group sync (ADR-025)
- [x] Merge model in `core:domain` (`updatedAt` stamps, tombstones, player merges, LWW with a deterministic tie-break, group rating constants) — 2026-10-17, `SyncMergePropertiesTest` (commutative, associative, idempotent, any sync order converges, identical ratings; 300 seeded runs each), `SyncMergeTest`
- [x] Room v3 (`updated_at` backfill, `deletions`, `player_merges`), stamped repository writes, tombstones in the delete's transaction, read-time merge resolution, "Merge into…" with refusal; backup v4 with v1–v3 still importing — 2026-10-17, `MigrationTest`, `RoomPlayerRepositoryTest`, `BackupRoundTripTest`, `GroupSyncDataTest` (three Room phones syncing a game night, deletions, a duplicate friend)
- [x] Protocol and crypto: key proof bound to the link's auth token, AES-256-GCM sessions, chunking, timeouts, the sealed sync file — 2026-10-17, `SyncProtocolTest` (host + 3 phones, drops mid-upload and mid-download, wrong key, relay, invalid data, Nearby unavailable), `SyncCodecTest` (RFC 5869 vector, tamper, other group)
- [x] `:feature:sync`: Group screen, invite QR + Google code scanner, sync sheet with permission rationale, Who are you, sync-file share/import; Players tab sync action and "You"; Settings row — 2026-10-17, `SyncViewModelsTest`, 18 screenshots (light/dark, font scale 2)
- [x] Merged release manifest has no INTERNET permission (the scanner's `transport-backend-cct` declares it; removed by `tools:node="remove"`) — 2026-10-17, `aapt2 dump permissions` on the release APK (superseded by ADR-036: INTERNET for the sync server, the logger's CCT backend removed instead; see M13)
- [x] Adversarial review of merge and protocol, findings fixed (ADR-025 consequences) — 2026-10-18; gate green: `assembleDebug`, `testDebugUnitTest` (773 JVM tests, 0 failures), `lintDebug detekt ktlintCheck`, `verifyRoborazziDebug`, `:app:assembleRelease` (unsigned in the agent worktree: `keystore.properties` lives in the main checkout)
- [x] 0.2.0 hands-on on two S25-sized API 36 emulators (release build) — 2026-10-18: in-place upgrade 0.1.0 → 0.2.0 with real data (7 players, one archived, 2 drafts, 4 matches, one deleted, a custom preset, settings, a running timer) identical after upgrade (backup export diff: only the new v4 fields); **Nearby was broken on Android 12+** (manifest capped `ACCESS_WIFI_STATE` at 31, `BLUETOOTH_SCAN` lacked `neverForLocation`: errors 8032, 8034, 8036) and after the fix ran between the emulators over virtual Bluetooth: join by pasted invite, host + join, full syncs, a later rename winning, a deletion staying deleted, "Merge into…" with identical leaderboards and match logs on both phones; sync file both ways, another group's file and a tampered file refused with clear errors; permission refusal, permanent denial, settings round trip, no host (2-minute timeout), scanner unavailable all lead to the sync file. Fixed on `fix/0.2.0-handson` (ADR-025 consequences); JVM gate green
- [ ] **Two physical phones sync at the table** (emulators prove Nearby only over virtual Bluetooth): host + join, permissions on Android 12+ and 16, a phone walking away mid-sync, Wi-Fi Direct upgrade and range, the first-scan scanner download (unavailable on the emulators), a sync file over Quick Share
- [ ] Three or more phones in one session; a 300-match log over Bluetooth only (Wi-Fi off)

## M7 — Card trivia (ADR-028)
- [x] Trivia engine (facts from the catalog, card-number and initiative-duel questions, Leitner mastery with a spacing rule, retries) — 2026-10-18, `TriviaFactsTest` checks all 2,150 facts against the raw hero JSON (no null value, no Handicap or spellbook card), `TriviaEngineTest` incl. a 300-run seeded sweep over real heroes
- [x] Progress in `datastore/trivia_progress` (not synced, not in the backup file; Auto Backup covers `datastore/`) — 2026-10-18, `DataStoreTriviaProgressRepositoryTest` (merge, reset by hero via the catalog, corrupt data, write off the main thread)
- [x] `:feature:trivia`: home with progress, setup and a pinned Start bar; quiz with the official card scan after each answer; summary with "Practise the missed ones"; entry points on the Heroes tab top bar and the hero page — 2026-10-18, 30 VM tests, 32 screenshots (light/dark, font scale 2, 384 dp, landscape), adversarial review fixed, E2E `TriviaFlowTest` + the other 10 E2E tests green on `goa_s25a`
- [x] Hands-on with the release build on an S25-sized emulator — 2026-10-18: in-place upgrade over 0.2.0 with real data, owned-products scope (27 heroes, 1,793 numbers), answers checked against the JSON and the shown card scans, progress kept across a force-stop mid-round. Fixed from it: the card scan sat below the fold after an answer; Start was two screens down. Emulator only
- [ ] Trivia round on the S25 Ultra (TalkBack announcements, haptics, card scans)
- [x] Leaderboard hands-on with the release build on an S25-sized emulator (ADR-032) — 2026-10-19: in-place upgrade 0.3.0 → v0.4 (Room v3→v4) with real data, trivia progress and the "You" kept; Playing as (default You, picker, inline add starting the Challenge, Guest asked for a name), Challenges as Sam and Alex passed round, an unfinished Challenge (not recorded), practice as You (only You's progress moved), both boards and periods, Remove (tombstone in the v5 backup). Fixed on `fix/v0.4-handson`: a Challenge's Leaderboard / Play again / Done sat below up to 20 missed cards. Emulator only; trivia rounds over Nearby between two phones not yet run

## M8 — Board sandbox (ADR-033)
- [x] Hands-on with the release build on an S25-sized emulator (384 dp, font scale 1.3, landscape) — 2026-10-19: set-up from the latest match (2 taps from Heroes), from a finished draft and from scratch (Brogan + Arien vs Tigerclaw + Wasp: 5 taps); moves by tap, drag, long-press swap, undo/redo, push both ways with a blocked Spawn point and the tray, throne capture, Add hero/token, Measure, Pieces, boards rename/duplicate/delete + undo, force-stop autosave; overlays counted against the rulebook. Fixed on `fix/v0.4-handson`: New board's hero grid scrolled (15 of 22–32 heroes in view) and jumped under the finger at the first pick; a third pick hidden behind "Next pick"; "Try on the board" below the fold; the opening cut off a hero at each end; a band of empty water after deselecting; a push left the new Battle Zone off screen; overlapping TalkBack space nodes; rename not focused; undo/redo silent for TalkBack; backup texts silent on boards and trivia rounds. Emulator only
- [ ] Board on the S25 Ultra (pinch/drag smoothness, TalkBack)

## M10 — Trivia leaderboard (ADR-032)
- [x] Rounds played as a player (Playing as, Guest), the fixed 20-question Challenge (uniform draw, no repeats, answer time in tenths), `TriviaRound` in Room v4 (`trivia_rounds`, migration 3→4), tombstones, merged players resolved on read — 2026-10-19, `MigrationTest`, `RoomTriviaRoundRepository` tests, `TriviaChallenge`/`TriviaLeaderboard` tests (ties "=N", periods, archived/merged/unknown players)
- [x] Group sync document v2 with rounds (v1 and v2 refuse each other with "update the app", both over Nearby and in sync files; a merge always writes the current version), merge property tests with rounds and their tombstones; backup v5 (`triviaRounds`, `boards`; v1–v4 still import) — 2026-10-19, `SyncMergePropertiesTest`, `GroupSyncDataTest`, `BackupRoundTripTest`
- [x] Leaderboard screen (Challenge / Practice, All time / Last 30 days, a player's rounds with Remove), "as <name>" on every round and summary, the learning-progress rule (only "You" moves it), adversarial review fixed (14 findings) — 2026-10-19, VM tests, 45+ screenshots (light/dark, font scale 1.15/1.3/2, 384 dp, landscape), E2E `TriviaChallengeFlowTest`
- [x] Hands-on with the release build on a 384 dp emulator: in-place upgrade 0.3.0 → 0.4 with real data (players, matches incl. pending and deleted, draft, preset, settings, "You", trivia progress) intact; Challenges as two players passing the phone, an unfinished one not recorded, progress moved only for "You" — 2026-10-19, emulator only
- [ ] Trivia rounds synced between two physical phones; Challenge round on the S25 Ultra (TalkBack, haptics)

## M11 — Board sandbox (ADR-033)
- [x] Maps through the import pipeline from `goa2-backend` (pinned): Forgotten Island and Across the River (the base game's maps) verified space by space against official art / a mat photo and the rulebook (p.6, p.20); Narrow Passages and Magma and Ice shipped Unverified (spawn points corrected from a mat photo); the fan-made Vexing Cliffs left out — 2026-10-19, `MapImportTest`, `MapValidationTest`, overlays in `tools/catalog-import/cache/maps/`
- [x] Board rules in `core:domain/board` (distance, obstacles, movement with Normal / Through tokens / Ignore all and straight-line moves, Fast Travel, Zone adjacency, Range/Radius incl. the water ring, opening, Push the Lane with lane-bound minions and the tray, respawn, push) — 2026-10-19, independent rules audit against the rulebook (no rule wrong; gaps fixed), `HexGridTest`, `BoardRulesTest`, `BoardSetupTest`
- [x] `:feature:board`: Boards (Latest draft/match card, autosave, rename/duplicate/delete with Undo), New board (every owned hero on one screen, alternate picks, map picker with Unverified), Board (framed on the Lane, tap/drag/swap, undo/redo, card overlays, Measure, Push the lane, Pieces list, TalkBack nodes per space); entry points from Heroes, a finished draft and a match; code/UX review fixed (25 findings) — 2026-10-19, VM/data tests, 37 screenshots, E2E `BoardFlowTest`
- [x] Hands-on with the release build on a 384 dp emulator: Try on the board 1 tap, latest line-up 2 taps, a 2v2 from scratch 5 taps with no scrolling; overlays counted by hand against the rulebook and the card JSON; push, tray, throne capture; 10 findings fixed — 2026-10-19, emulator only
- [ ] Board pinch/pan/drag smoothness and TalkBack on the S25 Ultra (the emulator renders in software)

## M12 — Play against the computer (ADR-035)
- [x] Play UI on the engine's contract, driven by a scripted fake (`feature/play-ui`): the game table
  (every `DecisionKind`, Defend with previews, hand, hero status and Turn slots, HUD, initiative strip,
  log), New game (line-up, who you play, level per seat, Quick/Long, map, Coach, Tie Breaker flip),
  the game (paced computer turns Slow/Normal/Fast, reveal, wait, take back, Coach nudges), game over
  with turning points and Rematch, autosave and resume from Boards, Boards' Play and Puzzles entries
  — 2026-10-24, `GameViewModelTest`, `NewGameViewModelTest`, `DataStoreGameSaveRepositoryTest`,
  49 play screenshots; sandbox screenshots unchanged. JVM only
- [x] Puzzle screens (`feature/play-puzzles-ui`, PRODUCT §10): the list (Continue, N of M solved,
  difficulty/theme filters, progress, Reset), the puzzle on the game table (goal banner, intro, open
  hands, narrated replies, gentle miss with Try again / Restart / Show solution, nudges after a miss
  or 90 s idle or when asked, the last one outlining the move, seal and explanation, step-by-step
  replay), Settings → Puzzle nudges, local progress in `datastore/puzzle_progress`, Boards' Puzzles
  entry with "N of M solved"; `PuzzleBook` bound in `PlayEngineModule` on the play engine — 2026-10-24,
  `PuzzleViewModelTest` on the real `EnginePuzzleBook` (every listed puzzle solved with its stored
  line, every wrong try missed and narrated), `DataStorePuzzleProgressRepositoryTest`, 25 puzzle
  screenshots; play and sandbox screenshots unchanged. JVM only
- [ ] Puzzles hands-on on the S25 Ultra (TalkBack on the narrated replies and the nudge card)
- [ ] Bind the AI per level and the Coach in `core:data` `PlayEngineModule`
- [x] Hands-on with the debug build on an S25-sized emulator (`goa_s25a`, 1080×2340, font scale 1 and
  1.3, landscape) — 2026-10-24: Wasp + Arien vs Brogan + Xargatha (Easy, Coach on) three rounds with a
  defense, a defeat, respawn, a level up and Take back; Trinkets + Mortimer vs Widget + Razzle (Hard)
  and Mortimer + Gydion vs Razzle + Widget (Easy, Fast) with the Turret, Zombies, the spellbook and
  Razzle's figures; force-stop mid-card and resume. Fixed on `fix/play-handson`: hex coordinates in the
  log ("fast travels to (7,-9)"), the reveal in seat order, a unit moved by another hero's card read as
  moving itself, Block cards "Decided by its text" against a melee attack, a defeat or the end of round
  scrolled out of the panel unseen, Play / Upgrade / Defend buttons pushed out of the panel by the
  preview, five cards not fitting, upgrade choice without the next Tier's cards, duplicate chips
  ("Red Throne Zone" ×3, "Red Melee minion" ×3), the coach's minion nudge when only Hold was better,
  "that space" on an action choice, "Turr / et", Razzle's figures drawn as tokens, "SPELLSREMOVED",
  the title clipped at 384 dp. E2E `PlayFlowTest` (Boards → first reveal) green. Emulator only
- [x] Coach looks ahead (`fix/play-coach-ahead`, 2026-10-24): a tester saw no nudge in a whole game (the
  Hard-level analysis ran after the tap and could pass the 1.5 s timeout on a phone). The coach now
  analyses each question as soon as it is asked, on a fork in the background, and the answer is
  checked against it at once (at most 300 ms wait); same nudges for the same seed (`CoachAheadTest`).
  JVM only; the time on the S25 Ultra is not measured yet
- [x] See the whole board (`feature/board-full-view`, owner request "view the entire board by hiding
  the large window; a smaller top bar, especially in landscape") — 2026-10-25: one `HideablePanel` for
  the sandbox, the game and puzzles (Hide / drag down → a one-line peek bar with the question and
  Confirm for a tapped space or piece; a small card at the bottom end in landscape; hiding fits the
  whole board unless the person moved the view; the game's hero strip and the puzzle's goal hide with
  it; kept across rotation) and a compact `BoardScaffold` title bar (48 dp portrait, 40 dp landscape).
  `:feature:board` screenshots re-recorded (75 updated) plus 9 new (panel hidden portrait, landscape and
  font scale 2 on all three screens; the landscape bar at font scale 2). JVM only, not on the S25 Ultra
- [x] Card focus (`feature/card-focus`, owner request "automatic focus/zoom on selected cards on the
  first click, another click to confirm, a back swipe or upwards swipe to deselect, swipe across to see
  the other options") — 2026-10-25: one viewer (`ui/cards`: `CardFocusState`, `CardFocusHost`,
  DESIGN "Card focus") replaces the card strip + Confirm and the card sheet for every card question
  (play, discard / retrieve / swap, defend, upgrade, keep) in the game and puzzles, and reads the hand,
  a hero's cards, the "Asked by" card, a replay's card and every card of the Game state review (over its
  sheet; back returns to it); the sandbox gains "Read the cards" (a second
  tap measures with the card). Hand cards a question doesn't offer are shown, marked, unchoosable.
  Card text now draws icons inside reminders ("(Skill ✦ is an action type.)"). `CardFocusTest` (11:
  tap, second tap, Confirm, back, predictive back progress / cancel / commit, swipe up, scrim and ×,
  swipe to next, not an option, TalkBack actions, view only), `CardFocusDecisionTest` (4: discard,
  defend with back and swipe, play, Coach nudge after the second tap), `reviewCardFocusDark` (a review
  card opens view only, back returns); 10 new screenshots, the table / sandbox screenshots with card
  rows re-recorded, `tableCardSheetDark` and `PlaySheet.Card` removed. Emulator (`goa_s25b`, debug
  build, gesture navigation): first tap zooms, sideways swipe to the next card, the edge back gesture
  shrinks the card as it is dragged and springs back when cancelled, a finished back and an upward swipe
  close with nothing played, the second tap settles with the tick and plays the card. Haptics and the
  S25 Ultra itself not checked
- [ ] End-to-end test that plays a game to its end (needs the engine bound)
- [ ] Hands-on at the table and TalkBack on the S25 Ultra
- [x] Game state review (`feature/play-review`, ADR-037, owner: "let players review the game state, e.g.
  what cards have been played"): a top-bar Game state sheet on the game and puzzle screens — Now (Life,
  Waves, pushes, minions in the Battle Zone, coin, who acted / acts / is next; every hero by team with
  card in play, Turn slots, discards, item icons, markers, tokens, own hand) and Turns (each turn's
  cards at the reveal in acting order, its lines, the end of round), plus the read-only board at the
  start of any earlier turn. Engine: `GameSession.review` / `positionAt`, `GameView.actedThisTurn`,
  built from kept checkpoints through `Views` — 2026-10-25, `ReviewTest` (no card the viewer never saw,
  same review for games that differ only in hidden cards or tucked items, facedown stays facedown),
  `ReviewViewModelTest`, 12 review screenshots. JVM only
- [ ] Game state review hands-on on the S25 Ultra (pinch on the rewound board inside the sheet, TalkBack)

## M13 — Sync server client (ADR-036)
- [x] `core:domain`: `SyncServerToken` (fixed vector, computed independently in Python), `SyncServerAddress`,
  the invite's `s=` field (only when the group's server isn't the build default; unknown fields still
  ignored), `SyncFileException.version` — 2026-10-25, `SyncServerTest`, `SyncCodecTest`. JVM only
- [x] `core:data`: `OkHttpSyncServerRepository` (the phone algorithm in SYNC-SERVER.md through the sync-file
  merge path; upload only when the canonical document differs; 412 retried, three tries; every status in
  words; conditional GET → 304 when neither side changed), `group_sync` server override / switch / last
  outcome — 2026-10-25, `SyncServerRepositoryTest` (20 cases against a fake relay on MockWebServer),
  `SyncServerGroupStateTest`; token asserts SYNC-SERVER.md's vector. Plus a one-off JVM run (not
  committed) against the live relay on oldboi: two Room "phones" in throwaway group
  `ce6939c8-a052-4e9d-a909-306aeae14dcb` — first upload (rev 1), the second phone receiving 2 players +
  1 match without uploading, its new match uploaded (rev 2) and merged into the first, an idle sync,
  and another key for the same group id refused (401)
- [x] `feature:sync` + `app`: WorkManager scheduling (on screen and every 5 min while visible, 15 s after
  changes, hourly with a network, Sync with the server), join by invite syncs from the server and falls
  back to Nearby, Group screen Sync server card, Players tab dot, INTERNET with the CCT logger backend
  removed, HTTPS-only network config, `goa.syncServer` → BuildConfig — 2026-10-25,
  `SyncServerSchedulerTest`, `SyncServerSectionTest`, `ServerAttentionTest`, 12 new screenshots. Built,
  JVM-tested; **not run on an emulator or device yet**
- [x] Follow-up after merging feature/play (sync server + board full view): one Sync now (Group screen
  and Players icon sync through the server when it is on; Nearby is "Sync at the table (no internet)"),
  join-time sync as watched WorkManager work (expedited on API 31+), `Retry-After` honoured on 429/503,
  "The sync server is full" for `relay_full`, `s=` only while server sync is on — 2026-10-25,
  `SyncServerSchedulerTest` (11), `SyncServerSectionTest` (9), `SyncServerRepositoryTest` (21),
  `ServerAttentionTest` (2), 14 server screenshots. JVM only
- [ ] Against the real server on oldboi: two emulators syncing through it without meeting; a phone
  offline then online; wrong key after re-creating a group id; the release build's merged manifest
  (INTERNET present, `CctBackendFactory` absent, HTTPS-only config)
- [ ] Two physical phones (S25 Ultra + one more) syncing through the server; Doze and the hourly sync

## M14 — Styled on the official look (ADR-042)
- [x] Official graphics through the importer (`UiAssets.kt`): 32 deck symbols (each matched to its own
  card back, lowest margin 1.42), 6 item icons, 8 Spawn point symbols and both Tie Breaker faces
  from the pinned rulebook PDF by page and object; all in `official/SOURCES.md` — 2026-10-25,
  `UiAssetsTest`, `CatalogValidationTest` (symbol per hero, every file listed)
- [x] The look: steel-banner top bars, copper small-caps heads, Crimson Pro for printed text, card
  text on parchment; `ContrastTest` on every new pair — 2026-10-25
- [x] Show the cards: the hero page's Deck as card scans (by colour and tier), lean card viewer
  ("Read the card as text"), draft peek's starting cards as scans, deck symbols on hero page /
  catalog / peek, rules excerpts in the printed face, the coin's printed faces, official Spawn point
  symbols on the board — 2026-10-25, `OfficialDeckScreenshotTest`, gallery `officialArt*`, every
  affected screenshot re-recorded and looked at. JVM only
- [ ] On the S25 Ultra: card scans and symbols at real density, banner under the status bar, TalkBack on
  a deck card (reads values and full text)

## M15 — A stronger computer and a strength slider (ADR-039)
- [x] Contract: `Controller.Ai.strength` 1–10, old saves load as Easy 2 / Normal 6 / Hard 8 / Expert 10;
  `AiPlayers.forStrength`; the factory takes the strength — 2026-10-25, `ControllerStrengthTest`,
  `NewGameFormsTest`, NoPeeking and DecisionKinds over all ten strengths
- [x] The tuned evaluation (exposure × 0.3, opportunity × 2), fitted to self-play outcomes and measured
  59–64% over the old Normal / Hard / Expert; ten profiles, every adjacent step above 50% with its
  95% interval on held-out line-ups (AI.md "Measured strength"); two engine placement crashes found by
  the tournaments fixed — 2026-10-25, `LevelLadderTest`, `TimingTest` (10: 514 ms p95 on the desktop)
- [x] New game: the strength slider (per seat under Advanced), "Computer · Hard 8" wherever the level
  showed — 2026-10-25, slider screenshots portrait / landscape / light / W384 font scale 2. JVM only
- [ ] On the S25 Ultra: strength 10's think time per card play (budget 4 s; 2 s since v0.9, M20a), the slider with TalkBack
- [x] A new game starts at the strength of the last game started (`AppSettings.computerStrength`) —
  2026-10-25, `NewGameViewModelTest`, `SettingsDataSourceTest`. JVM only

## M16 — Gydion's spellbook at the game table (ADR-040)
- [x] Contract: `HeroView.extras` (places with ids, hidden ones null), built in `Views` so review and
  rewind show the same; the spellbook only to Gydion's own seat (Prepare Spells), cast spells and the
  box open — 2026-10-25, `ExtrasViewTest` (no-peeking, live and review)
- [x] Hero sheet / review / puzzles: a Spellbook section of the official landscape spell cards (in your
  spellbook / cast / not yet added), the cast question as landscape cards ("Cast Magic Missile"), card
  focus keeps each card's shape (`CardShape`, checked against every bundled image), the unlocked
  Ultimate as its card, the sandbox's card strip includes the spells — 2026-10-25,
  `SpellbookScreenshotTest` (14), `SpellbookSectionTest`, `CardShapeTest`. JVM only
- [ ] On the S25 Ultra: a Gydion game, casting from the book, the spellbook sheet in landscape
- [x] At any point (ADR-040, extended 2026-10-26, `feature/v08-spellbook-anytime`): a Spellbook control
  under Fit and Flip (Gydion's deck symbol, the spellbook's count) whenever a hero the person controls
  has one (choosing a card, any question, after the reveal, end of round, panel hidden, landscape,
  font scale 2), opening a Spellbook sheet (in your spellbook / cast / not yet added, card focus per
  group); an opponent's from his hero sheet ("Open the spellbook", facedown, a count); the review's
  rewound board shows the book as it was then. 2026-10-26, `SpellbookSectionTest` (11, incl. no-peeking
  for the control, the sheet and the marks), `SpellbookScreenshotTest` (33). JVM only
- [x] The cast question says why the card's other spells can't be cast (`DecisionContext.unavailableCards`:
  removed faceup, not added yet, its action can't start), in the panel (dimmed, one line each) and on the
  sheet ("Can be cast now" / the reason). 2026-10-26, `GydionTest`, `SpellbookSectionTest`
- [x] The action question says what the card will cast before Skill is chosen (hands-on pass:
  `DecisionContext.primaryPreview`): the spell as its card, its only target, "will do nothing" for
  Suggestion with nobody able (and any card's primary), the other spells with why, also when none can be
  cast; no new question (saves replay), nothing told after a look at another hand. 2026-10-26,
  `GydionCastPreviewTest` (11: every casting card × 4 layouts × 3 spellbooks against the cast,
  no-peeking), `CastPreviewScreenshotTest` (12). JVM only
- [x] Choosing Gydion: New game's "You play" ("Gydion's spellbook · 22 spells") and the line-up chip's
  menu (New game, New board) open the 22 spell cards by tier from the catalog; the draft's hero peek
  opens his hero page at the Spellbook (`HeroDetailRoute.section`). 2026-10-26,
  `SpellbookSetupScreenshotTest` (7), `DraftScreenshotTest.heroPeekGydionDark`,
  `OfficialDeckScreenshotTest.spellbookOpenedDark`. JVM only
- [ ] On the S25 Ultra: the Spellbook control in a Gydion game at every moment above, portrait and
  landscape, font scale 2; the draft peek's link; pass-and-play with Gydion on the other side
- [x] Re-check fixes (`fix/v08-handson-2`, ADR-040 extended): the sheet's Tiers share a row when they fit
  and the grid takes up to six columns (landscape no longer a third empty); "Cast with Elementary
  Evocation" wraps to two lines, stepping its size down, never cut off — 2026-10-27,
  `SpellbookSectionTest`, `SpellbookScreenshotTest` / `SpellbookSetupScreenshotTest` re-recorded and looked
  at. JVM only

## M17 — The table after the reveal: turn track and the card as the content (ADR-041)
- [x] Turn track: after the reveal the strip is the played cards in acting order with DONE / NOW / NEXT /
  PASS / OUT bands (never colour alone), ties, Emmitt's second card, facedown cards kept facedown;
  "NOW" over the acting hero on the map; moments for the reveal, turn over and round over; stays with
  the panel hidden and in landscape — 2026-10-25, `TurnTrackTest` (11), table screenshots
- [x] Decision panel: the asking card large beside compact action chips (only rules no card prints in
  words), Defend with the attacker's card and Holds / Falls under each hand card, no log lines
  repeated in the panel — 2026-10-25, Play / Puzzle screenshots re-recorded and looked at. JVM only
- [ ] On the S25 Ultra: a full round against the computer, panel shown and hidden, TalkBack on the track
- [x] Hands-on fixes (`fix/v07-handson`, ADR-041 §5–6, ADR-042): a printed action that isn't offered
  says why in one line from the engine (`DecisionContext.unavailable`: Gydion's empty spellbook, an
  Attack with no target, Movement or a Skill forbidden by an enemy card, a played Defense card); the
  card focus viewer keeps only what the card doesn't print, with "Read the card as text" collapsed and
  the card larger; the Tie Breaker coin is the official face in the HUD and New game's coin flip —
  2026-10-25, `UnavailableActionsTest` (7, incl. no-peeking), `PlayPanelTextTest`, spellbook / play /
  puzzle / review screenshots re-recorded and looked at. JVM only
- [x] Re-check fixes (`fix/v08-handson-2`, ADR-041 §7–9): the person's own question before the moment
  (the Minion Battle's Confirm was below the fold at font scale 2; game and puzzles), a new question opens
  the panel at its top; "Its Attack can't start" names the card's condition (Fireball: "no target in range
  that isn't adjacent to you or to a friendly unit", Choose-one cards each bullet's), no question added;
  Zone labels clear of the game table's buttons — 2026-10-27, `PanelOrderTest` (2), `NoTargetReasonTest`,
  `GydionCastPreviewTest` (+1), `EndOfRoundScreenshotTest` (4: portrait, landscape, W384 font scale 2,
  the whole board at font scale 2), play / puzzle / cast-preview screenshots re-recorded and looked at.
  JVM only
- [ ] On the S25 Ultra: the end of a round at font scale 2 (Minion Battle, then Keep from its top)

## M18 — Hero game state (ADR-044)
- [x] Engine: items for every hero are the public item icons (`HeroView.itemStats`), the tucked cards the
  owner's only; Min's Perfect Self no longer names the tucked card in the log — 2026-10-26,
  `HeroStateViewTest` (5: live, review, games differing only in the tucked card, no-peeking)
- [x] Hero sheet as a game state: this turn's card (facedown / faceup with NOW / NEXT / DONE and the
  initiative as it counts), the round's Turn slots, discard, item icons with "+1 Defense ×2" (your tucked
  cards too), where the hero stands or when it may respawn, markers and tokens; landscape side by side —
  2026-10-26, `HeroStateSheetTest` (7, incl. no-peeking on the sheet), `HeroStateScreenshotTest` (10:
  portrait, landscape, light, W384 font scale 2), hero / spellbook sheets re-recorded. JVM only
- [x] Reaching it: a long press on any hero's piece (even a target), TalkBack "Show <hero>'s game state"
  on the space, chip and track card, the board hint's line — 2026-10-26, `HeroStateSheetTest` (the real
  gesture and the action). JVM only
- [ ] On the S25 Ultra: long press on a hero that is a target (timing, haptic, no accidental answer), the
  sheet in landscape, TalkBack's custom action
- [x] Hands-on fixes (`fix/v08-table-polish`, ADR-044 §4–5, ADR-040 extended): a question's options and
  the hero asked come into view between the top overlays, the map buttons and the panel (framed again as
  the turn track and panel settle, never after the person's own pan; hexes down to ~30 dp to fit a Move
  3); the hand follows this turn's card on the hero sheet and the spellbook there is counts with "Open
  the spellbook"; the Spellbook sheet by Tier, filling the width (a rounding wrap left a third empty), each
  spell in the book marked "Cast with <card in hand>" for Gydion's own player — 2026-10-26,
  `MapViewportTest` (+3), `SpellbookSectionTest` (+5, incl. no-peeking), `GydionSheetsTest` (4, incl.
  no-peeking on both sheets), `SpellbookScreenshotTest` +9 (the sheet by Tier, the hero sheet with the
  hand, a Move 3 in view: portrait, landscape, light, W384 font scale 2 — its sheets now really at font
  scale 2: a sheet is its own window, so the shots set the configuration's scale too), play / puzzle /
  review / hero-state screenshots re-recorded (the map framing behind them) and looked at. JVM only
- [ ] On the S25 Ultra: an initiative tie and a Move 3 after the reveal (pieces in view, a pan not undone),
  Gydion's hero sheet and Spellbook sheet at font scale 1 and 2

## M19 — Upgrade preview (ADR-045)
- [x] Engine: `DecisionContext.upgrades` on the level up's first question, from the one selection the
  question, the Keep question and the AI read (`upgradeable` / `upgradesOf`) — 2026-10-26,
  `UpgradePreviewTest` (6: every playable hero 1 → 8, preview = Keep question; two levels in a round;
  two heroes levelling; a short pair; no peeking across a hidden upgrade history; no preview elsewhere)
- [x] Table: a row per card that may be upgraded with its two next-Tier cards and their item icons;
  card focus walks every card, upgrade cards captioned "Upgrade of …" with "Go to …" instead of
  Confirm — 2026-10-26, `CardFocusDecisionTest` (incl. a computer's level up shows none of its deck),
  level-up screenshots portrait / landscape / light / W384 font scale 2 re-recorded and looked at. JVM only
- [x] Hands-on polish (ADR-045 §6–9): the spells each Gydion card casts in the rows and the viewer; the
  viewer's card shrinks before a line is squeezed; the rows before the round's summary; official item
  icons with the stat in words — 2026-10-26, `NamedCardsTest`, `GydionLevelUpTest` (incl. the
  computer's level up shows none), `GydionLevelUpScreenshotTest` (portrait / landscape / light / short
  phone / W384 font scale 2) looked at. JVM only
- [x] Re-check (`fix/v08-handson-2`, ADR-045 extended): the viewer counts the cards and says a spell as its
  card's ("Card 2 of 9 · spell 2 of 2", TalkBack the same) — 2026-10-27, `GydionLevelUpTest` (+1),
  `GydionLevelUpScreenshotTest` re-recorded. JVM only
- [ ] On the S25 Ultra: a level up with three colours to choose from, the viewer swiped through, TalkBack
  on the rows and "Go to …"

## M20 — The resolution flow (ADR-046)
- [x] Engine: `CardsChanged.cards` / `discarded` (open cards only, p.4) and `Defended.attackerId`, additive,
  no event or question added — 2026-10-27, `ResolutionEventsTest` (5: a defense card and a forced discard,
  facedown discards and retrieves never named, a swap, old JSON decodes, random 3v3 games: named cards in
  their sentence, defenses after their attack, saves replay to the same events)
- [x] Panel: one entry per card resolved since the person's last choice (and the end of the round), the
  outcome in a few words, steps with the defending card as the card; the person's attack defended by an
  enemy opens by itself; game and puzzles — 2026-10-27, `ResolutionFlowTest` (8: attack + defense, a failed
  defense in a move-then-attack, a forced discard, a 3v3 turn of five cards, the end of round, nothing new,
  a facedown discard, no peeking over a random 3v3 game), `GameViewModelTest` (+1), `PuzzleViewModelTest`
  (a take back leaves nothing new),
  `ResolutionFlowScreenshotTest` (7: collapsed, expanded, the defense card in the viewer; portrait,
  landscape, light, W384 font scale 2) looked at; play / review screenshots re-recorded. JVM only
- [ ] On the S25 Ultra: a 3v3 game against strength 10 — the flow after the computer's turn, an attack of
  yours defended (open by itself), TalkBack on an entry (sentence, Expand / Collapse), font scale 2

## M20a — The computer at 3v3 and bigger (ADR-047 part A)
- [x] Harness for 3v3, 4v4 (single and double lane) and 5v5: mixed line-ups seating every hero, mirrored,
  defeats and win types per side, a deterministic work budget for the phone's cap; decision times per
  team size (AI.md "Budgets") — 2026-10-27, `AiSmokeTest` (3v3, 4v4, 5v5), `TimingTest`
- [x] Strength 10 within 2 s a card play (9: 1.5 s, 8: 1 s); samples on three threads in the app
  (`SampleRunner`, `AiSampleThreads`), quiet search copies (same answers, a quarter faster) —
  2026-10-27, `SampleRunnerTest`, `AiSampleThreadsTest`, `FingerprintTest`
- [x] 3v3: Push Potential 0.35 per attack card and the full exposure of a hero with no card left to
  defend with (strength 10 beat the v0.8 10 55.5%, 50.5–60.3%, 384 held-out games, fewer heroes lost,
  held against the kill-hunting exploiter); the four suspects measured, the 3v3 ladder, the candidates
  that did not pass (racing, focus fire, exposure and other lane weights; AI.md "Team sizes") —
  2026-10-28, `FingerprintTest`, `LevelLadderTest`
- [x] Engine: Stunt Doubles no longer crashes beyond Static Barrier on a token's Spawn point — 2026-10-27,
  `RazzleTest`
- [ ] On the S25 Ultra: strength 10's think time in a 3v3 and a 4v4 game (the 3–4× phone factor is
  still an assumption), and a game night against it at 3v3

## M20b — The learned evaluation (ADR-047 part B)
- [x] Part B, learned evaluation (`feature/v09-ai-learned`): strength 10 scores playout ends with a
  small net trained on self-play (features pooled per team, 1v1–5v5; weights in Kotlin source with the
  feature-schema hash) — 2026-10-27, strength 10 with it against strength 10 before it on held-out
  line-ups: 3v3 67.7% (60.8–73.9%), 2v2 67.2% (60.3–73.4%), 4v4 65.1% (58.1–71.5%) (192 games each); 10 over 9 75.0% (68.4–80.6%), 1–9 unchanged;
  `LearnedNetTest`, `:core:game:test` green. JVM only
- [ ] On the S25 Ultra: a 3v3 game at strength 10, decision times within ~2 s

## M20c — v0.9 merged: the resolution flow and the merged top strength (ADR-046, ADR-047 together)
- [x] `feature/v0.9` = E + A1 + A2 (`--no-ff`); one Stunt Doubles spawn rule covering both streams'
  crash tests (`RazzleTest`), `CONTRACT-CHANGES` — 2026-10-28, gate green: `:core:model/domain/game
  :tools:catalog-import:test` (core:game 1 733 tests), `testDebugUnitTest` (1 259), `verifyRoborazziDebug`,
  `lintDebug detekt ktlintCheck`; `:app:assembleRelease` builds (R8; unsigned in the worktree). JVM only
- [x] Composition measured (AI.md "The two parts together"): strength 10 = the net with v0.8's knobs
  beside it (part A's 3v3 knobs beside the net 45.8%, 38.9–52.9%); against v0.8's 10 on fresh
  line-ups 3v3 64.6% (57.6–71.0%), 2v2 69.3% (62.4–75.4%), 4v4 60.9% (53.9–67.6%); against the kill-hunter 66.1% (v0.8's 10: 51.6%), heroes lost 2.25
  against 2.10 a game; 3v3 ladder 10/9
  67.7%, 9/8 61.5%; card play p95 on the desktop with three threads 256 ms (3v3) / 417 ms (4v4) —
  2026-10-28, `LearnedNetTest` (the net's and 10's knobs), `LevelLadderTest`, `TimingTest`
- [x] `SyncServerRepositoryTest.changesToSyncedDataAreSignalled` flake (also on 1f7fa273): Room's
  table tracking starts after collection begins; the test warms up first — 2026-10-28, 6 of 6 runs
  green under load
- [ ] On the S25 Ultra: a 3v3 game night against strength 10 (heroes lost to a human who hunts
  kills; think time within ~2 s), the resolution flow after the computer's turns

## M18 — Web app: group, sync and players (ADR-043 W2)
- [x] Relay CORS for the Pages origin (`GOA_RELAY_CORS_ORIGINS`), deployed to oldboi — 2026-10-25,
  `CorsTest`, health and preflight checked from outside, existing groups untouched
- [x] Shared: the relay algorithm (`RelaySyncRepository`), group membership (`PrefsGroupRepository`),
  retry policy, and the Players/Group screens' builders and words (`core:group-presentation`); Android
  calls them — 2026-10-25, Android players/sync/data tests and screenshots unchanged (the invite dialog
  re-recorded on purpose)
- [x] The web's group data in IndexedDB, WebCrypto, sync with the relay, the Players tab, match
  editor, ratings, Group sync, invite QR link and camera scan — 2026-10-25, jsTest + Playwright WebKit
  and Chromium, `interop-live.mjs` (phone ↔ real relay ↔ browser, same ratings)
- [ ] On an iPhone: Safari and Home Screen, joining by the Camera from a phone's QR code, the camera
  scanner, sync after the app was in the background, the share sheet for sync files
- [ ] A real group on the production relay with one phone and one iPhone
- [x] Security review R2 (ADR-043 addendum): invite QR markup injection fixed, the invite key out of the
  address before the app loads, a Content-Security-Policy, the service worker installs and serves only
  this build's files, the Pages workflow pinned, a network guard so no test reaches the relay — 2026-10-26,
  `InviteQrTest`, `RouterTest`, `HostileSyncInputTest` (JVM + Node), Playwright `security.spec.ts`
- [ ] Owner: give the web app an origin of its own (a custom domain), as every Pages site of the account
  shares `quintond.github.io` and its storage (ADR-043 R2 item 4)

## M19 — Web app: board, play against the computer, puzzles (ADR-043 W4)
- [x] Shared: the board, table and puzzle screens' logic in `:core:play` (`BoardSandbox`, `TableUi`,
  `GameFacts`, `PuzzlePlay`, the New board/New game rules, the words); Android calls them — 2026-10-26,
  `feature:board` tests and screenshots unchanged
- [x] The engine, computer players, coach and puzzles in a Web Worker (`:web:engine`, `:web:worker`);
  the board as SVG; Boards, New board, the board sandbox, New game, the game table with the card
  focus viewer and Game state, the puzzles with nudges, misses and the solution replayed — 2026-10-26,
  `EngineHostTest`, `PuzzleControllerTest`, `GameControllerTest`, Playwright WebKit and Chromium
  (`play-board`, `play-game`, `play-puzzles`, the worker offline); AI think time per level measured
- [x] v0.7 merged in: the strength slider 1–10 (per seat, the last game's strength), the computer seated
  by the phone's rule (`AiPlayers.forApp`), missing actions say why, the lean card focus viewer, the
  printed Tie Breaker coin — 2026-10-26, `EngineHostTest` (strength 3 move by move against the phone's
  player), Playwright WebKit and Chromium; think time re-measured per strength (ADR-043 W4 item 4)
- [x] Hands-on game night on the web (R3): two iPhones (Home Screen, SE), a local relay and the phone's
  sync code; what a player hit fixed (ADR-043 Addendum R3) — 2026-10-26, `r3-coin`, `r3-layout`,
  `r3-game-night` (WebKit), `PuzzleControllerTest`
- [ ] On an iPhone: drag and pinch on the board, the card focus viewer's swipe, strength 10's think time,
  the worker after the app was in the background
- [x] v0.9 in the browser: the reviews R1–R3 and main (0.9.0) merged into `feature/web`; the shared
  code got 0.8/0.9's changes to what it took from the phone (the resolution flow moved to core/play);
  the learned evaluation's float arithmetic rounded per operation (`f32`), so `GameParityTest`'s
  strength 10 transcript is the same on the JVM and Node (re-pinned); the Android download rides on
  the web deploy at `android/` (ADR-048 addendum) — 2026-10-28. The web table doesn't show 0.8/0.9's
  new table UI yet (resolution flow panel, hero game-state sheet, upgrade preview row, Spellbook
  button): its engine and computer are 0.9's
- [ ] Wasm for the engine worker, if strength 10's capped search is felt in play
- [x] First load (R3, ADR-043 "R3 first load"): app.js 5.3 → 3.8 MB (gzipped 1.38 → 1.03 MB), WOFF2
  font subsets, QR libraries on first use, the precache after the first screen, resumable and checked
  by hash (WebKit on Fast 3G now finishes it), updates download only what changed, list thumbnails —
  2026-10-26, `r3-first-load.spec.ts`, `ThumbnailsTest`, `WebThumbnailsTest`; Fast 3G WebKit: usable
  16.2 → 12.5 s, offline never → 48 s (`scripts/first-load.mjs`). Not on an iPhone yet

## M5 — Polish & release readiness
- [x] Accessibility pass: ATF checks on 25 screens (no errors), contrast fixes, TalkBack labels, font scale 2.0 screenshots — 2026-10-17 (emulator/JVM)
- [ ] Tablet sanity check
- [x] Empty/error states, app icon (adaptive + monochrome) — 2026-10-17
- [ ] Onboarding (first-run walkthrough) — not built; empty states guide first use
- [x] Release build (R8, signing config outside repo), APK 41 MB, no INTERNET permission — 2026-10-17
- [x] Final data fixes (ADR-023): search scroll/kinds/snippets, Block label, timer-result handoff, game nights by gap, large-font layouts, plurals — 2026-10-17, JVM tests + release build on the emulator
- [ ] Hero grid scroll smoothness measured on the S25 Ultra (emulator host GPU crashes under flings; software GPU numbers only)
- [ ] Baseline profile
- [ ] Signed release APK sideloaded and smoke-tested on the S25 Ultra
