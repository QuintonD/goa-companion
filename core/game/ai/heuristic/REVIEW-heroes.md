# Review of the hero playbooks (gpt-6.1-sol, 2026-11-21): ledger

Two independent reviews of `ai/heuristic/heroes/` (23 findings; H = first pass, H2- = second). Each one was
checked against the code, the card text (`app/src/main/assets/catalog/heroes/<id>.json`) and the engine
(`core/game/src/main/kotlin/dev/quinton/goa/core/game/heroes/`). Severity: blocker / should / nit.

| Id | Reviewer | Ours | Outcome | Reason | Commit |
|----|----------|------|---------|--------|--------|
| H1 | blocker | should | fixed | Which tier-III blue Cutter kept is hidden (the determinizer samples the history, `Determinizer.kt:10-15`); the comment calling it public was wrong. Her own team reads her hand; the other team counts Fistful only once it is faceup. | 8f69ab74 |
| H2 | should | nit | fixed | No live bug: every effect is cleared at Retrieve (`Flow.kt:522`) and Equilibrium / Shield / Invulnerability are THIS_ROUND, so an unspent record is always in force. `BatchBSupport.effect` now uses `BatchASupport.live` anyway (no behaviour change, safe for future timings). | 4a9902d4 |
| H3 | should | — | disagree | Attacks can never target a friendly unit: `attackTarget` keeps `g.isEnemy(heroId, it)` only (`engine/Combat.kt:39-48`, p.13 "You can never attack or defeat friendly heroes and minions"). Current's victims are enemy heroes (`ArienHero.kt:154`). Both restrictions are the rules. | — |
| H4 | blocker | should | fixed | Real: a lone marked hero beside Bain (or a lone adjacent hero with a discard beside Dodger) was valued as two hits. New `BatchASupport.bestDistinctPair` takes one target per bullet, different ids, else the best single bullet; Dodger's "Both" option uses it too. | a46f073e |
| H5 | should | should | fixed | Real: the hero bullet reached every hero within 1 + step. It now uses the engine's legal landings (`reach(MoveSpec(me, 0, step))` plus staying), exactly the card's own start check (`GarrusHero.kt:139-140`). | 4a9902d4 |
| H6 | blocker | should | fixed | Real: Death Grenade takes up to 1 hero, Holy up to 2 (`MinHero.kt:118-119`). The space note keeps the best 1 or 2 of the adjacent heroes by the asking card; `evalTerms` reads the grenade's public effect card. | a46f073e |
| H7 | should | nit | fixed | Real but small: the number question does not name the hero, and CHOOSE_NUMBER is already looked at through the engine from strength 3. The note now reads the pushed hero from the engine's prompt ("Push <hero> how many spaces?", `CutterHero.kt:143`); with no single match it gives no border bonus (only "0 is never the answer" stays). | 8f69ab74 |
| H8 | should | should | fixed | Real: orange needs only the minion in radius; the adjacent enemy hero may stand anywhere (`IgnatiaHero.kt:280-283`). | 4a9902d4 |
| H9 | should | nit | fixed (partly disagree) | Terrain was already excluded: `geo.isSpace` is false on terrain (`BoardGeometry.kt:58-60`, `spaces` and `terrain` are separate sets). The custom occupant lookup is replaced by the engine's `isEmpty` anyway, the predicate Stone Grip itself uses (`MrakHero.kt:198`), so obstacle effects count too. | 4a9902d4 |
| H10 | should | should | fixed | Real: Outnumber / Pile On shoot only minions far away. The threat counts only Helping Hand / Even the Odds / Trusted Sidekick, each at its own `attackReach`. | 4a9902d4 |
| H11 | should | should | fixed | Real at strength 10: the guess summed every nearby enemy's known hand. The question does not name the victim (`BainHero.kt:258-266`), so a hand is read only when exactly one enemy in the card's radius has two or more cards; otherwise the tie order decides. | a46f073e |
| H12 | should | should | fixed | Real: a cross-team tie goes to the team on the Tie Breaker coin, Reverse Time included (`Flow.kt:233-237`, card: "Resolve ties as normal"). An equal Initiative now counts when the enemy's team holds the coin. | 4a9902d4 |
| H13 | should | should | fixed | Real: a defense that ignores minion modifiers (Arien's Duelists, `HCard.ignoresMinionModifiers`) does not get the modifier added to its Initiative. | 4a9902d4 |
| H2-01 | blocker | should | fixed | Real: a facedown resolved/discarded slot is sampled below 10. For the other team gold is spent only when it is faceup (`BatchC.spent`); her own team reads the zone. | 8f69ab74 |
| H2-02 | should | should | fixed | Real: `BatchDSupport.mayHold` dropped the revealed card still to resolve, so eight threat notes went silent on the one card known to be coming (all callers are `threatAdjust`). It now keeps it. | 4a9902d4 |
| H2-03 | blocker | — | disagree | The oaths have no primary: catalog `"primary": "defense"`, engine `hasPrimary = false` and the immunity starts only inside `defense()` (`SnorriHero.kt:367-383`, rulings/snorri.md 12). Played on the turn it is Movement 2 only, so the penalty stands. | — |
| H2-04 | should | should | fixed | Real: the horn repeat follows a first hit on an adjacent unit. Play needs an adjacent target and a different enemy hero in the card's reach (handicap: heroes only); the threat needs a unit hostile to Snorri beside him. | 4a9902d4 |
| H2-05 | blocker | should | fixed | Real: the repeat needs an enemy hero in radius with a discard after the attack (one already has, or the first target is a hero with a card to lose); Blood Frenzy repeats up to five times on different targets. | 4a9902d4 |
| H2-06 | should | should | fixed | Real: Thunder Boomerang repeats only after targeting a hero. The repeat is a hero first, then a different target (`bestDistinctPair`). | a46f073e |
| H2-07 | should | should | fixed | Real: "both" takes one adjacent hero and a different tree-adjacent unit in range; tried in both orders, second at 0.6 as before. | a46f073e |
| H2-08 | should | should | fixed | Real: the engine measures the Turret with the action's Range, items included (`TrinketsHero.kt:228`). Steam Discharge / Flame Belcher keep adjacency. | 4a9902d4 |
| H2-09 | should | nit | fixed (partly disagree) | Self is not a legal recipient: "in radius" never includes you (p.10), and the engine drops her (`SilverarrowHero.kt:273-275`). The radius now includes Radius items. | 4a9902d4 |
| H2-10 | should | should | fixed | Real: the splash follows a defense discard. Play needs an adjacent hero with a card in hand (public count) and a different enemy hero in the card's radius (gold 4, handicap 3); the threat needs the hero beside Tali to hold a card. | 4a9902d4 |

Tests added: `CutterHeuristicsTest` (Fistful counts for her own team only), `BainHeuristicsTest`
(`bestDistinctPair` never counts one target twice), `MinHeuristicsTest` (a grenade counts 1 or 2 heroes by
its card), `TaliHeuristicsTest` (the splash needs a target with a card).

Strength (192 games on development line-ups, `AI_H_TOURNAMENT=h10:s8 AI_H_GAMES=96 AI_H_FIRST=1000`):
before (88280d5c) 103-89 = 53.6% [Wilson 46.6-60.6%], after (4a9902d4) 99-93 = 51.6% [44.5-58.5%]. Four games
of 192, inside the noise of one measurement (the intervals overlap almost entirely): no measurable drop, and no
measurable gain either. The fixes are for correctness against the card text and for hidden information, not tuning.
Answer time unchanged (h10 mean 3.4 ms, p95 11.4 ms).

`GameParityTest.aSeededAiGameTakesTheSameCourse` re-pinned on the JVM (the seeded game's choices changed);
`:core:game:jsNodeTest --tests '*Parity*'` agrees.
