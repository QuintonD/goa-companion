---
name: ship-check
description: Full verification gate for the Guards of Atlantis companion app. Use before declaring a feature or milestone done.
---

# Ship check

Run from the project root and report each result honestly (pass / fail / skipped + why).

1. `.\gradlew.bat assembleDebug`
2. `.\gradlew.bat testDebugUnitTest`
3. `.\gradlew.bat lintDebug detekt ktlintCheck`
4. `.\gradlew.bat verifyRoborazziDebug` — if baselines changed intentionally, re-record with
   `recordRoborazziDebug` and say which screens changed.
5. If a device or emulator is attached: `.\gradlew.bat connectedDebugAndroidTest`, then install
   and smoke-test the touched feature. State whether it was the S25 Ultra or an emulator.
6. Check docs: ROADMAP checkbox updated with date and evidence; ADR added for any
   architectural choice; DOMAIN/DESIGN updated if behaviour or tokens changed.
7. Check data and assets: catalog validation green; `tools/catalog-import/REPORT.md` has no
   new unresolved disagreements; every file in `assets/official/` is in `official/SOURCES.md`;
   new fonts/sounds listed in `third_party/`.

Never report "verified on device" unless step 5 actually ran on hardware.
