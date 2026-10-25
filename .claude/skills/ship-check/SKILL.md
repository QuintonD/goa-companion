---
name: ship-check
description: Full verification gate for the Guards of Atlantis companion app. Use before declaring a feature or milestone done.
---

# Ship check

Run from the project root and report each result honestly (pass / fail / skipped + why).
On oldboi use `goa-gradle <tasks>` in place of `.\gradlew.bat` (memory cap, ADR-038).

1. `.\gradlew.bat assembleDebug`
2. `.\gradlew.bat testDebugUnitTest :core:model:test :core:domain:test :core:game:test :tools:catalog-import:test`
   — `testDebugUnitTest` covers only the Android modules; the pure-Kotlin ones (the rules engine
   included) run under `test`.
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
8. Check the network surface (ADR-036): the merged release manifest
   (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`)
   has `INTERNET`, no `backend:com.google.android.datatransport.cct.CctBackendFactory` meta-data (the
   code scanner's usage logger must have no upload backend), no `FOREGROUND_SERVICE`, and the
   HTTPS-only `networkSecurityConfig`; app code contacts no host but the group's sync server.

Never report "verified on device" unless step 5 actually ran on hardware.
