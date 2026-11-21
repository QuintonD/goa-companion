# Razzle the Ringmaster (`razzle`) — heuristic playbook

2nd printing. Complexity **4** (catalog). Sheet: Attack 3–3, Defense 1–4, Initiative 8–8, Movement 5–6. Roles on the sheet: tactician, melee. Combat totals are the **card's** printed values plus items, minion modifiers and card text. Do not add the sheet stats on top. The engine's attack total and `defensePreview` are the numbers to compare.

Figures: up to **four** (`rulings/razzle.md` 5). Defeating any one defeats Razzle once and removes all of them. They share one hand, deck, discard and marker set. During an action, every figure other than the acting one is another hero (catalog FAQ; designer Arty, Discord 2025-02-05 through 2026-06-25). Between actions they are the same hero, so initiative penalties **add across figures** (designer, Discord 2026-07-03: two Ice on one figure and one Ice on another is −3 when order is checked).

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `SetUp` (melee disruptor / support; not a solo killer) |
| `idealDistance` | `1` for the **acting** figure to its target; other figures should not all share that space's danger |
| `aggression` | `0.40` |
| `laneWeight` | `0.55` |
| `keepDefender` | true — `razzle-silver` while another figure is in its radius; otherwise the highest Defense card still in hand |
| `upgradeOrder` | see §3 |

She wins by being in several places, wasting enemy attacks, swapping allies into the swing, and leaving. Printed attacks are 2 (`razzle-gold`) or 4–5 (reds). She does not out-trade a brawler on raw Attack. Lane value comes from bodies denying Fast Travel (any enemy unit in a Zone blocks it, rulebook p.11) and from threatening more than one minion.

## 2. Card-by-card

Decisions the engine actually asks (see `RazzleHero.kt`, `rulings/razzle.md`): `CHOOSE_UNIT` "which figure performs this action" before the action; spawn spaces one at a time (`CHOOSE_SPACE`, optional, stop allowed); removes (`CHOOSE_UNIT`, optional); swap / push / move-another-figure targets.

| Id | Init | What it is for | Play when | Do not play | Hold value |
|---|---|---|---|---|---|
| `razzle-gold` Stunt Doubles | 13 | Attack 2, adjacent. After the attack, spawn up to 3 **from the supply** in radius **1** of the **acting** figure (radius items widen it). Not a copy already on the board. | The acting figure is adjacent to a minion or a hero you are willing to poke, and at least one empty spawn space is not in a multi-discard cluster. Turn 2–3, after you have seen where the round is, is the normal window. | Turn 1 into a space every enemy can hit; when every supply figure would land next to a multi-target discard (Mortimer, Xargatha, Wasp red, Swift shotgun, Wuk Trample, Min). Attack 2 is not a finisher. | Low Defense (1). Not the defender. |
| `razzle-silver` Crowd Control | 13 | **Defense:** +2 Defense per **other** figure in radius 3 of the **attacked** figure (1, 3, 5 or 7 before items). **Skill:** remove every other figure. | Skill: collapse onto the safest figure when two or more are threatened and you cannot block them all. Defense: an attack that 1 + 2×(others in radius) + items meets, and you still have a plan after the card leaves the hand. | Skill on the first action of the round if you have not spawned yet (nothing to remove; primary is not offered). Defense when no other figure is in radius (Defense 1 — almost any attack defeats her) unless it is the last card. | Highest defensive ceiling in the deck once clones exist. Keep it until an attack forces it or the skill is the vanish. |
| `razzle-handicap` Stunted Doubles | 12 | Attack 1, adjacent hero only, then the same spawn. | Only in a handicap game, and only on the same spawn rules as gold. | As a defender (Defense 1). | None. |
| `razzle-red-1` Phantom Strike | 9 | Attack 4, adjacent. May remove one figure after. | A minion, or a hero whose remaining Defense loses to 4, when you also want one figure gone. | When the only figure is the acting one and you needed the body. | Defense 3. Emergency block, not the plan. |
| `razzle-red-2a` Hit and Gone | 9 | Attack 4. May remove any number except the last figure. | Smack, then delete the figures that are in danger. High init lets this happen before slower attacks. | Removing so many that `razzle-silver` drops to Defense 1 while a big attack is still unresolved. | Defense 4. |
| `razzle-red-3a` Into Thin Air | 10 | Attack 5. May remove any number, **including all**. Removing all is not a defeat: no coins, no Life, respawn next card (`rulings/razzle.md` 6). | The escape when every figure is caught, or a clean minion take plus a full vanish. | Vanishing when a figure is holding a lane space the team still needs and nobody is threatening a kill. | Defense 4. |
| `razzle-red-2b` Rummage | 9 | Attack 4. If another figure is in radius 3, may retrieve one card. | Clustered, after you have spent a card, against discard-heavy teams. | Spread out (the retrieve does not fire). As a finisher that needs the card you would retrieve for something else this turn. | Defense 4. |
| `razzle-red-3b` Ransack | 10 | Attack 5. One optional retrieve **per** other figure in radius 3. | Three other figures in radius and two or more cards in the discard. This is the brawl heal. | Same as 2b, and never as the vanish — it does not remove figures. | Defense 4. |
| `razzle-green-1` Tightrope | 2 | Movement 2, Defense 1. After the move, may move another figure exactly 1. | Late in the turn (init 2). Split one figure toward the next wave and one toward a target, or pull a figure back into silver radius. | As a primary when you have no second figure and the move itself is useless. | Poor defender. |
| `razzle-green-2a` / `razzle-green-3a` High Wire / Wire Dancers | 2 / 1 | After you move, move another figure up to 2 / up to 3. | The reactive reposition. Keep figures inside silver radius **or** park one figure safe, toward the next Battle Zone. | Spending it early in the round when you still need a later reaction (it is the only low-init move). | Defense 2. |
| `razzle-green-2b` Theatrics | 2 | Swap with a minion in range 2 (friend or foe). | After faster heroes have moved. Steal a minion an enemy stepped toward, or swap an enemy minion next to an ally / another figure. | When an enemy can simply walk up and stand on the swap you telegraphed, and no figure is walled off. Range 2 is short. | Defense 2. |
| `razzle-green-3b` Spectacle | 1 | Same swap, then another figure may swap a **different** minion. | Two figures each have a minion in range 2 and at least one swap changes a modifier or denies a farm. | A second swap that leaves both figures adjacent to a multi-discard hero. | Defense 2. |
| `razzle-blue-1` / `2a` / `3a` Alleyoop / Group Performance / Team Spirit | 10 / 11 / 11 | Swap with a friendly hero in range 3 / 4 / 4 (another figure counts while the action resolves). Then move another figure up to 1 / 2 / 3. | An ally's best attack is close to this init, and the swap puts that ally on a target who has already revealed a losing card. Also to pull an ally out of a kill. | Swapping yourself onto a space every enemy can hit when silver is already spent. | Defense 2 / 3 / 3. Usable block, worse than a clustered silver. |
| `razzle-blue-2b` Magic Trick / `razzle-blue-3b` Aaaand it's gone! | 11 | Push an adjacent unit up to 2 / 3; this figure moves the **same number** of spaces the other way. One step. | The push's landing space is the one an ally wants, **and** the opposite path of that length is empty. Pushing a minion to an ally, an ally into a target, or bouncing off an enemy to safety. | Back to a wall or any blocked opposite path. Designer (Discord 2026-08-16) and `rulings/razzle.md` 12: the step fizzles, so a walled figure cannot push at all. An older Discord note (2025-03-05) said the push still happens; the 2026 ruling and this engine agree on the fizzle. Choose a shorter distance both sides can finish. | Defense 3. |
| `razzle-purple-4` Twin Strike | — | After a **basic** attack (gold or the handicap), another figure may repeat it on a different unit. The repeat may spawn again if supply remains. | When choosing the gold target, leave a second legal target for another figure. | Repeating onto a figure's neighbor who will then multi-discard both. | — |

Spawn spaces are empty, or (this engine only) a Spawn point whose token is removed first (`rulings/razzle.md` 5). Designer Arty (Discord 2026-08-14) says token spaces are **not** legal. Prefer empty spaces. Do not build a plan that only works by clearing a token.

"Up to 3" is 0, 1, 2 or 3 (designer, Discord 2026-07-12). A removed or defeated figure returns to the supply and can be spawned later (designer `nichik`, BGG 3471824, 2025-03-03). The catalog advice "you cannot spawn it again" means you cannot reposition a figure that is **already on the board** by spawning it. It does not mean a removed figure is gone for the game.

## 3. Upgrade path

Items: you **tuck** the card you do not keep; the tucked card's `item` is the bonus.

Two red plans. Do not mix them mid-stream.

**A — vanish (primary red).** Keep `razzle-red-2a` then `razzle-red-3a`. Tuck `razzle-red-2b` (Initiative item) and `razzle-red-3b` (Movement item). Cycle red into a later gold: delete the bodies that are caught, spawn them somewhere else next. Community (Rhas_al_Ghul, BGG 3514028, 2025-05-19) plays this as "never get hit, or get hit only at max silver." A Radius item helps the re-spawn spread; that icon is on `razzle-blue-3a`, so it is gained by **tucking** `razzle-blue-3a` (keeping `razzle-blue-3b`). Take that only when the push-blue is the blue you want.

**B — brawl heal (alternate red).** Keep `razzle-red-2b` then `razzle-red-3b`. Tuck `razzle-red-2a` (Defense item) and `razzle-red-3a` (Range item). Stay clustered. Cryomancy (BGG 3613420, 2025-11-18) names this into teams with lots of discards: you still die if they commit, but they spend more than the kill is worth. Better against high-initiative, low-Attack heroes (Cryomancy, BGG 3659764).

**Green.** Default keep `razzle-green-2a` / `razzle-green-3a` (two-body move). Tuck `razzle-green-2b` (Attack item) and `razzle-green-3b` (Attack item). Switch to Theatrics / Spectacle only when the minion swaps are real every round (tight waves, walled figures). Green is the reaction card; take it before blue if you are dying to bad positions, after red if the red plan is the whole hero.

**Blue.** Default keep `razzle-blue-2a` / `razzle-blue-3a` when a melee ally (Brogan, Rowenna, Ursafar) is in the team: the swap is the kill setup. Keep `razzle-blue-2b` / `razzle-blue-3b` when you need a self-peel and a Radius item (tuck 3a). Blue's Defense (3) is the block when silver is spent; a Defense item from tucking `razzle-blue-2b` (keep 2a) or from the red-A tuck helps that.

**Order by phase.** Levels 1–3: red first (it is both the Attack and the survival trick), then green, then blue. Levels 4–6: finish the same branches; do not "correct" a red branch you already committed. Level 7–8: the other colours, then the Ultimate. Early game her Defense sheet starts at 1; Initiative and Defense items are how she stops being one-shot (Wolff Designa, Arcane pack overview — the overview's "no range or radius item" and "each hit doubles the copies" do **not** match these cards).

## 4. Synergies and counters

**With:** a melee ally whose Attack init sits next to blue 10–11, so the swap is not answered. She wants to stand near allies for the swap and away from them only to deny a second Zone's Fast Travel.

**Fears:** any repeat-on-another-hero or multi-discard while two figures are near it. Named in play: Wasp's repeating red and Swift's shotgun (charliebevis, BGG 3613420, 2025-11-17); Cutter's Bombardment timed on the gold turn; Mortimer Crawling Dead; Xargatha Cleave; Wuk Trample through several figures (designer, Discord 2026-06-25: one discard per figure). Ranged heroes punish the single figure at the start of the round. Melee brawlers struggle to pin every clone and are the matchup she wants (Cryomancy, BGG 3659764).

**Against her (`threatAdjust`):**

- One figure on the board: treat her like Defense 1. A ranged attack she cannot step out of, on turn 1, takes silver or the whole hero.
- Gold is init 13. Threaten the empty spaces in radius 1 of the figure that can reach a melee target; she spawns there or she does not spawn.
- After the spawn, cover **every** figure. Silver's skill saves only one. If one figure is unthreatened she vanishes to it.
- Her red plan is readable once you have seen it. A-path: she wants red then gold, or silver then gold. B-path: she blocks with silver and retrieves. Attack in the window after silver is gone and before the red connects (Cryomancy, same thread).
- Do not stand where primary blue can swap an ally onto you, and do not stand on a line her alt blue can push you along.
- Killing any figure kills all of them. Isolated does not mean "ignore"; it means "the one you can reach."

## 5. Common mistakes

- Spawning all three on turn 1 in one cluster next to a multi-discard hero.
- Treating silver Defense as 7 when the **attacked** figure does not have the others in radius 3. Modifiers are per figure (BGG 3689258, 2026-04-06; designer, Discord 2025-02-05).
- Using the skill to delete clones when nothing is threatening them, then having Attack 2 and Defense 1.
- Playing gold, red and silver in the same order every round. Cryomancy: a consistent pattern is a death sentence on her more than on other heroes.
- Pushing with alt blue when the opposite path is short.
- Chasing a hero kill with Attack 2–5 while a minion is free and a figure can sit in the next Zone.
- Holding for lack of a target so often that the round is four Holds (the old search player held on about 35% of her actions, `AI.md`).

## 6. Heuristic rules

Weights are coin-equivalents: a coin = 1, a card in hand ≈ 2.5 (more if it is their last card or their only attack), one minion of lane margin at an even lane ≈ 15, a hero kill ≈ that hero's level coins + assist coins + one Life (about 5 early, about 15 when that Life can end the game). `CTX general` uses distances, counts, initiatives, zones, coins, hand, revealed cards, tokens. `CTX hero` needs her figures.

`evalTerms` (her team): +4 for each figure beyond the first that shares a silver radius with another figure and is not adjacent to an enemy hero who still has a multi-target discard in the possible pool. −10 for a figure adjacent to such a hero while a second figure is also adjacent to that hero. +3 for a figure in a Zone that contains no other enemy unit and that an enemy would otherwise Fast Travel through. 0 for a figure on top of an ally's only exit.

`threatAdjust` (she is the enemy): a space adjacent to two of her figures gets +6 beyond printed reach (one attack threatens the shared hand twice). A space in radius 1 of her only figure, while `razzle-gold` may still be in hand, gets +4 (the spawn lands there at init 13). While she has a single figure, every space in an enemy's attack range of that figure gets +5 (Defense 1 if silver is the block, and silver is not a 7).

1. `CTX hero` [PLAY_CARD] [`razzle-gold`, `razzle-handicap`] WHEN turn = 1 AND figures = 1 AND any enemy possible attack can reach every empty space in radius 1 of the only figure THEN avoid gold. WEIGHT 8. SOURCE Cryomancy, BGG 3659764, 2026-02-09 ("most vulnerable early in a round"); [REASONED].
2. `CTX hero` [CHOOSE_SPACE] [`razzle-gold`] WHEN spawning THEN prefer an empty space that is in radius of at least one existing figure (silver) and not adjacent to an enemy who could target two figures with one action; stop before placing a figure that fails both. WEIGHT 6 per bad figure not placed, versus 4 per good figure placed. SOURCE Cryomancy, BGG 3659764; catalog advice (clones inside silver radius); designer Discord 2026-07-12 (may spawn fewer than 3).
3. `CTX hero` [PLAY_CARD] [`razzle-silver`] WHEN two or more figures are each in range of a different enemy attack this turn AND silver is the skill that puts the surviving figure outside all of those ranges THEN override the skill. WEIGHT 12 (the hero, not a card). SOURCE Cryomancy, BGG 3659764 (vanish rather than block every hit).
4. `CTX hero` [DEFEND] [`razzle-silver`] WHEN the attacked figure has N other figures in radius 3 (radius items included) AND 1 + 2N + Defense items ≥ the attack AND no other card in hand meets it more cheaply THEN prefer silver. WHEN N = 0 AND any other card meets the attack THEN avoid silver. WEIGHT 2.5 for the card saved, or the kill if she falls. SOURCE card text; `rulings/razzle.md` 7.
5. `CTX hero` [CHOOSE_UNIT] [`razzle-red-2a`, `razzle-red-3a`, `razzle-red-1`] WHEN removing figures after the attack THEN remove each figure that is in an enemy attack range and keep a set that still lies inside one silver radius, unless `razzle-red-3a` and every remaining figure is threatened, in which case remove all. WEIGHT 10 for a vanished kill, 4 for each threatened figure removed. SOURCE Cryomancy, BGG 3659764; card text of `razzle-red-3a` (not a defeat).
6. `CTX hero` [PLAY_CARD] [`razzle-red-2b`, `razzle-red-3b`] WHEN another figure is in radius 3 of the acting figure AND the discard holds a card AND an enemy in the matchup has a repeat discard THEN prefer this red over the vanish red if both are somehow available; while it is the kept red, prefer playing it after a card has been spent, not before any discard exists. WEIGHT 2.5 per card retrieved (× figures in radius for 3b, capped by the discard size). SOURCE Cryomancy, BGG 3613420; [REASONED].
7. `CTX general` [PLAY_CARD] [`razzle-blue-1`, `razzle-blue-2a`, `razzle-blue-3a`] WHEN a friendly hero in range has an attack whose printed Attack, from the swapped space, meets an enemy who has already revealed a card that cannot defend it AND this card's initiative is within 2 of that ally's attack initiative THEN prefer the swap onto that space. WEIGHT the kill, else 6 for pulling an ally out of a space where an unresolved enemy attack reaches them. SOURCE Cryomancy, BGG 3659764.
8. `CTX hero` [CHOOSE_NUMBER, CHOOSE_SPACE] [`razzle-blue-2b`, `razzle-blue-3b`] WHEN the opposite path of length N is blocked THEN do not choose N; choose the longest N both the push and the retreat can complete, or decline the primary. WEIGHT 4 (a fizzled card). SOURCE designer Discord 2026-08-16; `rulings/razzle.md` 12.
9. `CTX hero` [PLAY_CARD] [`razzle-green-2a`, `razzle-green-3a`, `razzle-green-1`] WHEN it is the last unresolved card of the turn (init 1–2) AND two figures can end the turn either both inside silver radius or one of them in the Zone the wave pushes toward next THEN prefer green over holding a later reaction you will not get. WEIGHT 4. SOURCE Cryomancy, BGG 3659764 ("only reactive card"); [ADVICE] "the more of you in play, the stronger you are."
10. `CTX hero` [CHOOSE_UNIT] [`razzle-green-2b`, `razzle-green-3b`] WHEN a minion in range 2 is the one an enemy hero is adjacent to and about to farm, or swapping it flips a minion modifier on an ally by 2 THEN prefer that minion. WEIGHT 5 for a denied farm, 4 for a modifier swing. SOURCE Cryomancy, BGG 3659764; [REASONED] on the modifier (enemy melee −1, friendly melee +1).
11. `CTX hero` [CHOOSE_UNIT] [any] WHEN choosing the acting figure THEN prefer the figure from which the primary can start and which, after the action, is not the only figure left in an enemy's range. WEIGHT 8. SOURCE `rulings/razzle.md` 2; [REASONED].
12. `CTX general` [PLAY_CARD] [any] WHEN the round's earlier cards were gold-then-silver or red-then-gold in the same order as the previous round AND no rule above overrides THEN penalise that same lead card. WEIGHT 3. SOURCE Cryomancy, BGG 3659764 (unpredictable); `AI.md` predictable-card weakness. This is a penalty, not a script.
13. `CTX general` [CHOOSE_ACTION] [`razzle-gold`] WHEN the primary cannot defeat the adjacent unit and the only spawn spaces fail rule 2 THEN prefer Movement or Hold over a spawn into the bad spaces. WEIGHT 6. SOURCE Cryomancy, BGG 3659764 ("don't drop clones carelessly").
14. `CTX hero` [CONFIRM, CHOOSE_UNIT] [`razzle-purple-4`] WHEN another figure can repeat the basic attack on a different enemy minion or on a hero who cannot defend THEN yes. WHEN the second target is a hero who will then have two figures in their multi-discard radius THEN no. WEIGHT a minion (5, or 15 if it is the lane margin) or the second card (2.5). SOURCE card text; catalog FAQ.
15. `CTX general` [RESPAWN] [`razzle-gold`] WHEN placing the single respawn figure THEN prefer a space whose radius-1 neighborhood contains two empty spaces that are not adjacent to an enemy hero, on the side of the next Battle Zone. WEIGHT 4. SOURCE [ADVICE] (only one figure at start and on respawn); [REASONED].
16. `CTX hero` [PLAY_CARD] [any] WHEN figures ≥ 2 AND an enemy hero is adjacent to two of them AND that hero's possible pool contains a repeat-on-another-hero or a multi-discard THEN the next card should be a remove (`razzle-red-2a` / `razzle-red-3a` / silver skill) or a move that breaks the adjacency, not an attack. WEIGHT 12. SOURCE catalog FAQ; BGG 3544784 (Regniznevik, 2025-07-20, confirmed); BGG 3613420.

## 7. Sources

- Catalog `app/src/main/assets/catalog/heroes/razzle.json` (cards, advice, FAQ). Advice is goa2-faq. The "cannot spawn it again" sentence is read as the designer reads it, not as "gone forever."
- `core/game/rulings/razzle.md`, `heroes/RazzleHero.kt`.
- Cryomancy, "A Mad_Mike Guide to the Razzle Dazzler," BGG 3659764, 2026-02-09 (2nd printing). https://boardgamegeek.com/thread/3659764
- charliebevis, BGG 3613420, 2025-11-17, and the 2025-11-18 reply. https://boardgamegeek.com/thread/3613420
- Rhas_al_Ghul, BGG 3514028, 2025-05-19. https://boardgamegeek.com/thread/3514028
- BGG 3689258 (McGravityHammer, 2026-04-06) minion modifiers; BGG 3544784 (2025-07-19) another-hero.
- Designer Arty (BGG `nichik`), Discord rulings collected at https://www.cockeyedandcurious.com/goa2/faq (dates in the rules above). Spawn-on-token disagrees with this engine; empty spaces are the plan.
- Designer `nichik`, BGG 3471824, 2025-03-03 (defeated copies are not gone).
- Wolff Designa, Arcane pack overview, https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/10 — identity only. Its spawn-doubling and "no range or radius item" do not match these cards.
- No Reddit or YouTube strategy found for 2nd-printing Razzle.

## 8. Implementation notes

`RazzleHeuristics.kt` (ADR-085). Checked against `RazzleHero.kt`:

- **Which figure acts** is the engine's `CHOOSE_UNIT` "Which one of you performs the action?", asked before every action when she has two or more figures. It is an opaque choice, so from strength 3 the one-ply look already plays the action out from each figure. H11 only adds a small lean (+1.5) toward a figure that stands next to something worth hitting when the card is an attack.
- **Spawning** asks `CHOOSE_SPACE` "Spawn another one of you in radius (up to N more)", which you may decline, and only offers empty spaces (`canSpawnInto`). Every offered space is in radius 1 of the acting figure, so "in silver's radius of an existing figure" (H2) is always true. The note scores only how close the space is to the enemy heroes.
- **Silver's defense** (H4): the engine measures the +2 per figure from the figure that was attacked and shows the total in `defensePreview`, so the note reads the preview rather than counting figures itself.
- **Not implemented:** H16 and the "multi-target discard" terms in `evalTerms`/`threatAdjust`. `HCard`/`CardProfile` has no multi-target flag. The draft listed other heroes' card ids from memory, and that list was removed (game data comes from the catalog only). With a single-target attack, two figures next to one enemy are no worse than one. Also left out: H3, H6–H10, H12–H14 (a predictable order, swap and push choices). The look-ahead covers the opaque ones.
- **Ablation** (strength 8, `BatchCAblationTest`, the same line-ups and seeds as `HeroAblationTest`): 96 games 53.1% (51–45), Wilson 43.2–62.8%; 384 games 52.1% (200–184), Wilson 47.1–57.0%.
