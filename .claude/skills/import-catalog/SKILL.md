---
name: import-catalog
description: Build or refresh the Guards of Atlantis II hero catalog (JSON, card images, artwork, FAQ rulings) from the pinned online sources. Use for the initial import, new heroes, a new printing, or a source update.
---

# Import the catalog

Sources, roster and the `verified` rule are in `docs/DOMAIN.md#sources`. The importer is
the Kotlin JVM module `tools/catalog-import` and serialises `core:model` types, so its output
always matches the app's schema. Code map: `Sources.kt` (pinning, cache), `SourceModels.kt`
(parsers), `CardMapping.kt` (IDs, values), `TextMarkup.kt` (card text), `CrossCheck.kt`,
`ImageMatcher.kt`, `Overrides.kt`, `Importer.kt`, `Output.kt` (JSON, SOURCES.md, REPORT.md).

1. **Pin sources.** `tools/catalog-import/sources.lock.json` holds one commit SHA per repo.
   Change a SHA only when deliberately refreshing: look up the branch head once with
   `https://api.github.com/repos/<owner>/<repo>/commits/<branch>`. Files are fetched from
   `raw.githubusercontent.com/<repo>/<sha>/…`; the only API call is one recursive tree listing
   per pinned SHA (needed for image file names), cached as `.tree.json`. Everything lands in
   `tools/catalog-import/cache/<owner>__<repo>/<sha>/` (gitignored, ~170 MB on a cold run).
2. **Run** from the repo root (Windows: `.\gradlew.bat`, set `GRADLE_USER_HOME` per ADR-010):
   ```
   ./gradlew :tools:catalog-import:run                       # full import
   ./gradlew :tools:catalog-import:run --args="--offline"    # cache only, no network
   ./gradlew :tools:catalog-import:run --args="--no-images"  # data only (fast)
   ```
   It:
   - maps source 1 (`new_heroes.json`, `spells.json`, `states.ts`) to
     `app/src/main/assets/catalog/heroes/<id>.json` + `catalog/index.json`;
   - card IDs: `<hero>-gold`, `<hero>-silver`, `<hero>-<colour>-1`,
     `<hero>-<colour>-<2|3><a|b>` (source `variant.first` 1 → a, 2 → b), `<hero>-purple-4`,
     `<hero>-handicap`, extra deck cards `<hero>-gold-extra-<n>` (Takahide), spellbook pages
     `<hero>-spell-<name-slug>` in `extras` (Gydion). **IDs are permanent.**
   - converts text to the `CardText` markup: `::attack_red::` → `{attack}` (colour suffix
     dropped; hero tokens/markers keep their names, see `CardText.ICONS`), `~(…)` reminder,
     `>>`/`>` options → `• ` paragraphs, `**bold**` kept (source 1), printed line wraps joined. Where
     goa2-faq has the same words, its paragraphing is used (with source-1 bold); its mid-sentence
     line breaks are joined too (`TextMarkup.joinWraps`: a break stays only after `. : ) ! ? —`
     or before a bullet, reminder, icon label or bold keyword), and its `- ::rune…:::` list
     items lose the bullet (the card prints the icon label). Colours are the printed frame:
     Handicap cards gold (Takahide's silver), spellbook pages gold/red/green/blue/purple. Unknown icon
     tokens abort the run. Printed `+` values (`primaryValueSign`/`modifierValueSign` `PLUS`)
     → `values.plus`; an unknown sign aborts the run.
   - cross-checks numbers (incl. `+` signs, field `values.plus`) against colefin8 and text, tier, variant, colour, stats and roles
     against goa2-faq; attaches goa2-faq lore and FAQ rulings (per-hero + common rulings that
     reference the hero's cards);
   - identifies each card scan (source 2) by comparing its illustration with source-1 card
     artwork; a Handicap card that reuses its basic card's art is told apart by the top-right
     badge. Spellbook pages have no source-1 art: they are mapped in `overrides.json` `images`.
     Scans → WebP q80 ≤ 768 px (`official/cards/<card-id>.webp`); goa2-faq `public/hero/<id>.png`
     → ≤ 512 px portraits (`official/heroes/<id>/portrait.webp`). Unchanged images are not
     re-encoded (`cache/assets-manifest.tsv`); stale ones are deleted;
   - applies `tools/catalog-import/overrides.json` last;
   - writes `official/SOURCES.md` (file → raw URL @ SHA), bumps `dataVersion` in
     `catalog/index.json` only when generated JSON changed, and writes
     `tools/catalog-import/REPORT.md` (counts per product, every disagreement, resolved ones
     with evidence, cross-check gaps, missing fields/images, low-confidence image matches,
     printed `+` values and bold counts, values the schema can't express, verified list).
3. **Check `REPORT.md`.** Expect 32 heroes (more after the 3rd printing): 18 deck cards +
   Handicap each; Takahide 20 + Handicap; Gydion 18 + Handicap + 22 spells. Resolve every
   unresolved disagreement **by looking at the card image** (crop the text box or value
   banner; the hero card `<hero>-0.jpeg` has stat bars and role icons), then record it in
   `overrides.json`:
   - source 1 wrong → patch the field (`"stats": {"movement": [7, 8]}`, `"text": "…"`);
   - source 1 right → `"confirm": ["item", "values.defense", "text", …]` (field names as in
     the report);
   - always with `reason` and `evidence` (which image, read when). Never change `id`.
   - A hero missing from colefin8 (or with bad colefin8 data) is verified only after every
     card's numbers were compared with the images: add it to `imageChecks`.
   Review every new image assignment visually (a contact sheet with card ID + name under each
   output WebP); the art matcher has been right on all 632 cards but read the low-confidence list.
4. Run `./gradlew :tools:catalog-import:test :core:data:testDebugUnitTest` (importer mapping +
   catalog validation) and `verifyRoborazziDebug`; re-record catalog baselines if they changed
   on purpose.
5. Spot-check 3 random heroes in the app against their card images; say which ones.

Never hand-edit generated JSON or images. Never invent a value: unresolved → `null` +
listed in `REPORT.md`.
