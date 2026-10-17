---
name: fix-hero
description: Correct a single Guards of Atlantis II hero, card, stat or FAQ entry in the catalog (wrong value, typo, missing image, title alias). Use for targeted data fixes; use import-catalog for bulk imports.
---

# Fix one hero

1. Find the evidence: the card image in `app/src/main/assets/official/cards/`, the pinned
   sources in `docs/DOMAIN.md#sources`, or a photo from the owner. Memory is not evidence.
2. Add the correction to `tools/catalog-import/overrides.json`, keyed by hero or card ID,
   with a `reason` and `evidence` field. Do not edit generated hero JSON directly.
3. Re-run `.\gradlew.bat :tools:catalog-import:run` and confirm the fix in the diff and in
   `tools/catalog-import/REPORT.md` (the disagreement should be gone).
4. Never change an existing hero or card ID — matches reference them. Renames go in
   `aliases` / display fields only.
5. Run `.\gradlew.bat :core:data:testDebugUnitTest`; update screenshot baselines if the
   hero's screens changed.
