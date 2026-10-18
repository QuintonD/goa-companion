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
- [x] Merged release manifest has no INTERNET permission (the scanner's `transport-backend-cct` declares it; removed by `tools:node="remove"`) — 2026-10-17, `aapt2 dump permissions` on the release APK. Superseded by ADR-030: INTERNET for the hub only, the CCT backend registration removed instead (M8)
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

## M8 — The hub and the web app (ADR-030)
- [x] Contracts: invite links with hub and player (`GroupInvite.parseInvite`, `HubAddress`), `HubToken`; ADR-030/031; `:core:presentation` module — 2026-10-18, `HubInviteTest`
- [x] `:server:hub` core: storage, sign-in, sync API, page shell, catalog/static assets, Web Push base — 2026-10-19, 59 JVM tests (`SyncApiTest`, `SharedWritesTest`, `GroupStoreTest`, `SignInTest`, `ShellTest`, `WebPushTest` vs RFC 8291, `HubCliTest`), ktlint + detekt clean; `installDist` run locally: `goa-hub join` (also via stdin through `goa-hub.bat`), `serve`, sign-in through an invite link in headless Edge, pages in dark and light. Every tab is a "Coming soon" placeholder until its feature lands. **Not yet behind Funnel/Caddy or on an iPhone**
- [x] Android: hub address, auto-sync (WorkManager), invite links, `INTERNET`, assetlinks — 2026-10-19, `HubSyncRepositoryTest` (14, MockWebServer hub that opens, merges with `SyncMerge` and seals; 401/409/422/413/5xx/redirect/timeout/unreachable; status kept and cleared), `HubSyncSchedulerTest` (9, work-testing: constraints, 15 s debounce, backoff, cancel), `HubSectionTest` (6), `HubAttentionTest`, 11 Hub screenshots (light/dark, font scale 2, 384 dp) + Players badge; merged release manifest checked (INTERNET, no CCT backend, HTTPS-only config). JVM only: no emulator ↔ hub run yet, not on device
- [ ] Shared builders moved to `:core:presentation` (Android behaviour unchanged, tests moved)
- [ ] Web: Players (leaderboard, player page, merge, log, editor, "Who won?", meta, group stats)
- [ ] Web: Heroes (catalog, hero page, cards, FAQ) and Rules
- [ ] Web: Timer (presets, running timer, round view, Action seats, wake lock, chimes, push)
- [ ] Web: Draft (setup, methods, board, → match)
- [ ] Web: Card trivia, Group (join, who am I, invite QR, devices), Settings (constants, backup)
- [x] Deploy kit and runbook ([deploy/README.md](../deploy/README.md)): Tailscale Funnel first, Caddy + DuckDNS + router as the alternative, hardened systemd unit, update with rollback, daily backups, Windows variant — 2026-10-19, `shellcheck` clean, Caddyfile `caddy validate`/`fmt` (2.11.7) and its log filter checked, `systemd-analyze verify`/`security` (hub 1.5 OK); `install.sh` (both modes), `update.sh` (rollback on a broken build, APK publish) and the restore steps run in systemd containers (Debian 13, Ubuntu 24.04 with ufw, Fedora 44 with Java 25) against a stand-in hub; PowerShell scripts parse-checked, invite quoting through `cmd` tested. **Not yet tried on a real machine, a real tailnet or Windows end to end**
- [ ] Security review of sign-in, sync API and hosting; adversarial review of web writes vs the sync rules
- [ ] Hands-on: two emulators + a browser at 390 px converge through a local hub; game night on the web
- [ ] **Real iPhone over the internet** against the owner's hub (home screen, timer locked, push)

## M9 — Linking players (ADR-031)
- [x] Personal invites ("Invite <name>"), "You're <name>?" after the first sync, old "me" merge — 2026-10-18, `PlayerLinkingTest` (invite chooser, yes/not me/merge/refusal/missing player, add-me check), Roborazzi light/dark/font 2 at 384 dp. JVM + screenshots only
- [x] `PlayerMatchUp` + "Same people?" on the Players tab (on the fly, any sync) and "Find duplicates"; "Add me" checks similar names — 2026-10-18, `PlayerMatchUpTest` (rules + order/same-match properties), `SamePeopleTest`, `PlayerMatchUpStoreTest`. JVM + screenshots only
- [ ] Android linking flows on two phones (personal invite → "You're <name>?", look-alike merge after a sync)
- [ ] Web: the same three flows on the hub (ADR-031 §4)

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
