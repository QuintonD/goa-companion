# Roadmap

Update checkboxes with a date and one line of evidence (e.g. "tests green, device-verified").
Run the `ship-check` skill before ticking a milestone's last box.

## M0 — Scaffold
- [ ] `git init`; Gradle 9.6 / AGP 9.4 project, version catalog, `build-logic` convention plugins (AGP 9 DSL)
- [ ] Module skeleton per ARCHITECTURE.md; Hilt (KSP), Navigation 3, theme from DESIGN.md tokens
- [ ] Bottom nav with 4 empty destinations; About screen with credits and OFL licences
- [ ] Lint, ktlint, detekt, Roborazzi, unit-test setup all green on an empty app
- [ ] Debug APK installs and launches on S25 Ultra

## M1 — Catalog
- [ ] `tools/catalog-import`: fetch sources, normalise, write hero JSON + images + `SOURCES.md` (see `import-catalog` skill)
- [ ] Catalog loader + validation tests (schema, deck composition, asset paths)
- [ ] All heroes from all products imported; unverified ones listed in `tools/catalog-import/REPORT.md`
- [ ] Catalog grid, search, filters, hero detail with card viewer; sigil set v1 (fallback)

## M2 — Match setup & timer
- [ ] `TimerEngine` (pure, fake-clock tested) + `MatchSetup` (wave/life counters) + DataStore state
- [ ] Timer screen, preset editor, action timer, end-of-round checklist, cues, `keepScreenOn`
- [ ] Alarm scheduling, countdown notification + actions, Live Update on Android 16+
- [ ] Device test: locked screen, Doze, process death, reboot

## M3 — Players, matches, Elo
- [ ] Room 3 schema: players, matches, participants; exported schema + migration test baseline
- [ ] Match editor and log, `EloCalculator` + replay, player/hero rating screens with charts
- [ ] Meta dashboard; versioned JSON export/import (lossless round-trip test); Auto Backup rules

## M4 — Draft
- [ ] `DraftEngine` with all official methods + property tests (2–10 players)
- [ ] Draft setup (teams, balance by Elo, Player Draft, complexity filter, pool), coin flip
- [ ] Draft board with hidden picks, undo, pick assignment; draft → pending match → timer

## M5 — Polish & release readiness
- [ ] Accessibility pass (TalkBack, font scale 2.0, contrast), tablet sanity check
- [ ] Empty/error states, onboarding, app icon (adaptive + monochrome)
- [ ] Release build (R8, signing config outside repo), baseline profile, APK size < ~100 MB
- [ ] Signed release APK sideloaded and smoke-tested on the S25 Ultra
