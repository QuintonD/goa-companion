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
