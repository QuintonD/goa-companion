# Rulings: Mrak the Rockshaper

Sources in order (RULINGS.md): card text (`catalog/heroes/mrak.json`), the 2nd-printing rulebook
(printed pages), the FAQ (Mrak has no hero FAQ; goa2-faq `common.json`). **[APP]** = text and FAQ
are silent; the engine takes the reading closest to the text. Code: `heroes/MrakHero.kt`; tests:
`heroes/MrakTest.kt`.

1. **[APP] Rock token supply.** Question: how many Rock tokens are there (p.18 "Tokens are
   intentionally limited")? Reading: not limited (`TokenKind.supply = null`). Reason: the box's count
   is in none of the pinned sources. Needs the real count to be exact. Rocks are removed at the end
   of round (p.19, the text says nothing else).
2. **"Adjacent to terrain, or to a Rock token".** Cards: Seismic Slam/Assault, Epicenter, Treacherous
   Ground, Rockslide, Avalanche, Stomping Step, Ground Shaker. Reading: a neighbouring hex that is
   terrain (p.19: printed terrain and the water around the board; units that count as terrain, Stone
   Gaze) or holds a Rock token (presence). The enemy hero of the Seismic cards is targeted (immune
   heroes can't be chosen).
3. **[APP] Fissure "in each of the first three empty spaces in the straight line from you in the
   direction of the attack".** Reading: the direction is from Mrak to the target's space when the
   attack is made; walking that line from Mrak, occupied spaces and terrain are skipped and the first
   three empty spaces get a rock (a defeated target's space is empty again, so it is the first); the
   line ends at the board's edge (RULINGS 14 extent), so fewer than three may be placed. Only after
   an attack that connected (RULINGS 18). Tests: "Fissure - …", "Lesser Fissure - …".
4. **[APP] Stone Grip "Place exactly 3 Rock tokens into empty spaces adjacent to an enemy hero in
   range, and as far away from you as possible".** Reading: Mrak picks one enemy hero in range
   (targeted, p.10 "A hero in range"); the three rocks go into empty spaces next to that hero, the
   farthest from Mrak first; Mrak picks only among spaces tied at the cut. "Exactly 3": a hero with
   fewer than three empty spaces around can't be picked (no partial placement); with none such the
   Primary action is not offered. Tests: "Stone Grip - …".
5. **[APP] Treacherous Ground / Rockslide / Avalanche.** Reading: any unit in range other than Mrak
   (friend or enemy, targeted; displacement limits apply), moved exactly 1 space to a space next to
   terrain or a rock; "You may" covers the whole step. Avalanche's repeat is not offered after the
   move was declined (it would offer the same choice again).
6. **Boulder Rush / Blitz / Boulderdozer.** Reading: any token or an enemy unit adjacent (targeted,
   not one that can't be pushed by Mrak, e.g. Brogan's Bulwark); Mrak picks the distance 1..N; the
   push ignores obstacles (p.18: through obstacles to the farthest empty space within the distance,
   possibly 0). Then Mrak may move 0..N spaces in the same direction (away from where the target
   stood), ignoring obstacles, ending on an empty space; card-text movement, not a Movement action.
7. **[APP] Rolling Stone / Strolling Stone "Move any number of spaces".** Reading: at least 1 space
   (a move of 0 is no move), straight, ignoring obstacles; the spaces between the start and the
   destination may hold at most 1 (2) empty spaces; terrain and water are not empty spaces. With no
   such destination the mandatory move fails, so the Primary action is not offered.
8. **Stomping Step / Ground Shaker.** Reading: the unit (friend or enemy, targeted) must be next to
   terrain or a rock where it stands; it moves exactly 1 space (mandatory), then a rock goes into the
   space it left. Ground Shaker's repeat excludes the first unit.
9. **Stone Carapace / Rock Solid.** Reading: the effect is on the played card (on its Turn slot);
   whenever Mrak would discard a card from his hand (forced or chosen, any filter) he is asked
   whether to discard Stone Carapace instead; he may also defend with it as if it were in his hand
   (its Defense value, no text). Either discard changes the card's state, so the effect ends (p.17).
   Rock Solid's "You may retrieve" comes before the movement (p.11). **[APP]** With no card in hand
   there is nothing Mrak "would discard", so "discards a card, or is defeated" still defeats him.
   Defense cards are always discarded from hand (p.10) and are not replaced. Tests: "Stone Carapace -
   …", "Rock Solid - …".
10. **Rock and a Hard Place (Ultimate).** Reading: after each placement by one of Mrak's card texts
    (Fissure's three rocks, Stone Grip's three, one Stomping Step rock: each is one placement), every
    enemy hero next to at least one of the rocks just placed discards a card, if able; once per turn
    (only a placement next to at least one such hero uses it). Immune heroes neither trigger it nor
    discard (goa2-faq common: "Would an immune hero trigger effects, that require adjacency? No.").
    Tests: "Rock and a Hard Place - …".

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`scripts/mrak_effects.py`, `steps/markers.py`) | Engine |
|-------|------------------------------------------------------------|--------|
| Rock and a Hard Place | Optional: Mrak is asked whether to use it on a placement ("Optional and once per turn — the actor is prompted") | Not optional: the text has no "may", so the first placement next to an enemy hero uses it (ruling 10) |

Fissure (skip obstacles, stop at the edge), Stone Grip ("3 or none", farthest first) and Rolling Stone
(only empty interior spaces count) are read the same way there.
