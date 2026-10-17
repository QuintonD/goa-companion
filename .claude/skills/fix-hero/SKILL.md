---
name: fix-hero
description: Correct a single Guards of Atlantis II hero, card, stat or FAQ entry in the catalog (wrong value, typo, missing image, title alias). Use for targeted data fixes; use import-catalog for bulk imports.
---

# Fix one hero

1. Find the evidence: the card image in `app/src/main/assets/official/cards/` (full-size scan
   in `tools/catalog-import/cache/dmrediger__goa2/<sha>/public/hero-cards/…`), the hero card
   `<hero>-0.jpeg` for stats/roles, the pinned sources in `docs/DOMAIN.md#sources`, or a photo
   from the owner. Memory is not evidence.
2. Add the correction to `tools/catalog-import/overrides.json`. Do not edit generated hero
   JSON directly. Every entry needs `reason` and `evidence`:
   ```json
   "heroes": { "hanu": { "stats": { "movement": [7, 8] }, "reason": "…", "evidence": "…" } },
   "cards":  { "gydion-spell-fireball": { "text": "…", "reason": "…", "evidence": "…" },
               "wasp-green-1": { "confirm": ["block", "values.defense"], "reason": "…", "evidence": "…" } },
   "images": { "<card-id>": { "path": "public/hero-cards/…jpeg", "reason": "…", "evidence": "…" } },
   "imageChecks": { "<hero-id>": { "evidence": "…", "checked": "YYYY-MM-DD" } }
   ```
   Keys replace fields (`values` and `stats` merge one level deep); `confirm` marks a
   cross-check disagreement as checked with source 1 right; `images` assigns a card scan;
   `imageChecks` records that a hero's numbers were checked on the images (needed for
   `verified` when colefin8 lacks the hero). Title spellings go in `aliases` / `title`.
3. Re-run `./gradlew :tools:catalog-import:run --args="--offline"` and confirm the fix in the
   diff and in `tools/catalog-import/REPORT.md` (the disagreement moves to "resolved").
4. Never change an existing hero or card ID — matches reference them. Renames go in
   `aliases` / display fields only.
5. Run `./gradlew :core:data:testDebugUnitTest`; update screenshot baselines if the hero's
   screens changed.
