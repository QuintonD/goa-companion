---
name: import-catalog
description: Build or refresh the Guards of Atlantis II hero catalog (JSON, card images, artwork, FAQ rulings) from the pinned online sources. Use for the initial import, new heroes, a new printing, or a source update.
---

# Import the catalog

Sources, roster and the `verified` rule are in `docs/DOMAIN.md#sources`. The importer is
the Kotlin JVM module `tools/catalog-import` and reuses `core:model` types, so its output
always matches the app's schema.

1. **Pin sources.** Update commit SHAs in `tools/catalog-import/sources.lock.json` only when
   deliberately refreshing. Fetch via `raw.githubusercontent.com`, never the GitHub API.
   Raw downloads are cached in `tools/catalog-import/cache/` (gitignored).
2. **Run** `.\gradlew.bat :tools:catalog-import:run`. It must:
   - map source 1 (`new_heroes.json`, `spells.json`, `states.ts`) to hero JSON in
     `app/src/main/assets/catalog/heroes/` — slug IDs, card IDs `<hero>-<color>-<tier><a|b>`,
     tokens like `::icon::` converted to the app's inline-icon markup, `~(…)` kept as
     reminder text;
   - attach FAQ rulings and lore from source 3 (fix `â€™`-style mojibake);
   - cross-check numbers against source 4 and text against source 3;
   - convert card photos (source 2) and artwork (source 1) to WebP q80 (cards ≤ 768 px,
     portraits ≤ 512 px) under `app/src/main/assets/official/`;
   - apply `tools/catalog-import/overrides.json` last;
   - write `official/SOURCES.md` (file → source URL @ SHA) and bump `dataVersion` in
     `catalog/index.json` when output changed;
   - write `tools/catalog-import/REPORT.md`: counts per product, every disagreement, every
     missing field or image, and which heroes are `verified`.
3. **Check** `REPORT.md`: 32 heroes (more after the 3rd printing), the expected card counts
   (19 per hero; Takahide 21; Gydion + 22 spells). Resolve disagreements by looking at the card
   image, then record the fix in `overrides.json` with a comment.
4. Run `.\gradlew.bat :core:data:testDebugUnitTest` (catalog validation) and
   `verifyRoborazziDebug`; re-record catalog baselines if they changed on purpose.
5. Spot-check 3 random heroes in the app against their card images; say which ones.

Never hand-edit generated JSON or images. Never invent a value: unresolved → `null` +
listed in `REPORT.md`.
