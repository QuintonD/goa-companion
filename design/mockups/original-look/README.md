# Closer to the game's own look: mock-ups for review

The mock-ups the look was built from (ADR-053): the `NN-*.png` files, 411 × 891 dp at 2× (`09-pieces.png` is a wider
comparison). Kept as the record of the target, not maintained.

| Screen | What it shows |
|--------|---------------|
| `01-table-painted` | **Recommended board.** The real painted map, Battle Zone lit as on rulebook p.8, rulebook miniatures with a type badge, official Life and Wave counters, the card hand |
| `02-table-grid` | Alternative: the same painting with a hex grid and today's disc pieces |
| `03-table-move` | A Movement question: the reachable spaces lit (computed on the real map), turn track, parchment text box |
| `04-hero-page` | Hero page headed by the hero's dashboard art, with item icons as the stats |
| `05-hero-sheet` | Hero sheet built on the real dashboard: cards lie in Turn 1–4 |
| `06-heroes` | Catalog tiles in the cards' title plate and dark plate |
| `07-timer` | Timer on the painted sea with the rulebook's battlefield silhouette |
| `08-rules` | Rules page set like the rulebook (verbatim text from `rules-reference.json`) |
| `09-pieces` | Miniatures, miniatures + type badge, and discs, at true size |

## Where the art comes from
- **Forgotten Island painting:** CasperCB/Guards_Replay `assets/maps/forbidden_island.webp` (1400 px), the art the
  map import checked space by space (`tools/catalog-import/REPORT.md`). Pinned by commit in `tools/catalog-import/overrides.json`.
- **Banner, title plate, text boxes, ribbons, slate plate, dashboard frame, Tigerclaw's dashboard art, Life and Wave
  counters, coin, silhouette, p.8 diagram:** extracted from the pinned rulebook PDF.
- **Miniatures:** the rulebook's greyscale renders (printed p.6), cut out and toned to match the print.
- **Cards, portraits, deck symbols, item icons, Spawn symbols, Tie Breaker:** the app's bundled `assets/official/`.
  The card back is Tigerclaw's scan from source 2.

All of it belongs to Wolff Designa. What the app ships comes through the importer and is listed in `official/SOURCES.md`.

## The game data in the mock-ups
Rulebook first-game line-up (p.5), Quick game for 4 (4 Life, 3 Wave), starting hand and minions from the catalog and
map JSON. Initiative order on `03` is highest first (p.7). The level-8 Tigerclaw on `05` is an invented but legal state.
Pieces use `HexLayout`'s rotation and a similarity fit of the importer's anchors (worst anchor 13.9 px of a 72 px hex).

