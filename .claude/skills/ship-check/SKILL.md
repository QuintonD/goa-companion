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
8. Check privacy rules: the merged release manifest
   (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`)
   lists only the intended permissions — `INTERNET` is allowed for the group's hub (ADR-030), the
   datatransport `CctBackendFactory` registration is absent (Google's logger can't upload), and
   `networkSecurityConfig` is the HTTPS-only one; the only host the app contacts is the group's hub
   (no other URLs in app code); `git remote -v` shows no public remote.

Never report "verified on device" unless step 5 actually ran on hardware.
