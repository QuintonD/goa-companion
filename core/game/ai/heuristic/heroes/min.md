# Min (`min`) — heuristic playbook

2nd printing. Icons from `min.json`. The 1st-printing spotlight [BGG 2886926] (2022-06-21, community) is explicitly outdated (the thread says so). Current differences that matter: the smoke bomb is an obstacle, not a token units move through; green swaps only with the smoke bomb, not with mines or grenades; alt green doubles item bonuses with **no** empty-discard clause and does not retrieve; mines are 2 or 3 tokens with a real dud rate; the red repeat-on-another-hero is gone and the ultimate repeats any attack once instead. Advice that still matches is cited. A BGG video comment on Perfect Self (2020s, community, on the Optimal Play Min review) matches the current double-item text and is used. [BGG 3487749] (2025) is the current Min + Ignatia combo.

## 1. Identity

| Field | Value |
|---|---|
| Role | `Fighter` |
| Complexity | 2 |
| `idealDistance` | 1. Every red is melee. Gold is range 1 and ranged, so it is still the adjacent space, and it counts as a ranged attack for immunity and Block. |
| Lane vs kill | `laneWeight` 0.45. He takes minions in melee and sets kills with a push, a grenade, or a mine on the path. |
| `aggression` | 0.65. He wants to hit and then not be there. |
| `keepDefender` | true for the green while the smoke bomb is on the board. `min-green-*` a-branch is a Block: the swap must succeed or the block fails (rulings/min.md 5). Without the bomb in range, that card does not save him. |
| `upgradeOrder` | §3 |

Profile bars (not added in play): Attack 4→8, Defense 3→8, Initiative 8→8, Movement 6→6. The initiative bar matches the cards (gold 13, silver 12, blue 10–11, red 9–10). Enemies with a faster card walk away after he arrives and before he attacks, unless he attacks on the red, which is slower than most blues and golds. That is the whole melee problem, and it survived the reprint [BGG 2886926 comments].

Supplies (rulings/min.md 9): one Smoke bomb, one Grenade, two blast mines, two dud mines. Placing the smoke bomb again moves it. Mines are passable and facedown; the other team does not know blast from dud (rulings/min.md 6–7). His own decisions do.

## 2. Card-by-card

`min-gold` Fast as Lightning. Initiative 13, attack 2, defense 1, movement 1, range 1, ranged. After a connecting attack, apply the "After the attack" text of his resolved or discarded red, using that red's radius if the text has one. The red must be faceup (resolved or discarded). If it is still in hand, gold is attack 2 and nothing else. Official advice: the gold works even when the red was used as movement or as a defense. Play late, on a minion or a hero, once the red is faceup. Do not play it before the red in a round where he wants the push or the smoke swap. Defense 1.

`min-silver` Smoke Bomb. Initiative 12, defense 1, radius 3, skill. Place the Smoke bomb. Enemy heroes cannot target him or another unit when the bomb is on a straight line strictly between them and the target (rulings/min.md 4). It does not stop presence checks or a move that then attacks from the new space (Brogan may charge past it). It is one token. Play so that the bomb sits on the line from an enemy hero to Min, to an ally, or to a friendly minion, and so that he can later swap to it (green range 3–4, red alt radius 4). Official advice: end movement on a straight line from an enemy to the bomb. It is not a defender by the number (defense 1). It is the defender's enabler.

`min-red-1` Crane Stance. Initiative 9, attack 3, defense 5, movement 5. Adjacent unit. After a connecting attack, push an adjacent enemy unit up to 3. The push is optional and need not be the target (rulings/min.md 3). The early stick and a real defense (5).

`min-red-2a` Tiger Stance. Attack 4, defense 5, movement 5, initiative 9, item **initiative**. After the attack he may move 1 to a space still adjacent to the target, then the same push. If the target was defeated and removed, the step-around does not happen; the push does (rulings/min.md 2).

`min-red-3a` Dragon Stance. Attack 4, defense 6, movement 5, initiative 10, item **movement**. The step-around is 1 or 2 spaces, still ending adjacent to the target, then the push.

`min-red-2b` Viper Stance. Attack 5, defense 5, movement 5, initiative 9, radius 4, item **defense**. After the attack he may swap with the Smoke bomb in radius 4.

`min-red-3b` Cobra Stance. Attack 5, defense 6, movement 5, initiative 10, radius 4, item **defense**. Swap with the smoke bomb, and if he does, he may place it on a space in radius 4. The leave-and-replant.

`min-green-1` Poof! Initiative 2, movement 3, range 3, defense primary, Block. Swap with the Smoke bomb in range; if he does, block the attack. No bomb in range, or he cannot be swapped: the block fails (rulings/min.md 5).

`min-green-2a` Vanish. Same, range 4, item **defense**.

`min-green-3a` Ruse. Same, range 4, initiative 1, item **radius**, and after the swap he may place the bomb on a space in range. The place does not happen when the swap fails.

`min-green-2b` Inner Strength. Initiative 2, defense 2, movement 3, item **attack**. This round, double his item bonuses. No other text. A discard does not turn it off.

`min-green-3b` Perfect Self. Initiative 1, defense 3, movement 3, item **range**. Choose one or both: double item bonuses this round, and/or take a tier II card still in the deck and add it as a permanent item of its icon. The permanent item is how a color he is not holding becomes a stat. Do not pull the tier II of a color he is about to upgrade into hand if that is the last copy of the branch he wants.

`min-blue-1` Trip Mine. Initiative 10, defense 4, movement 2, radius 3. Place 2 mines, 1 blast and 1 dud, facedown, in radius. All of them: fewer empty spaces than tokens and the action cannot start. An enemy hero who moves through a blast discards, if able. Friendly heroes do not. An immune hero removes the mine and does not discard (rulings/min.md 6–7).

`min-blue-2a` Cluster Mine. 3 mines, 1 blast and 2 duds, initiative 11, item **attack**. More ground, worse odds. Official advice: mines cover more ground and are less reliable.

`min-blue-3a` Minefield. 3 mines, 2 blasts and 1 dud, defense 5, item **attack**. The reliable field.

`min-blue-2b` Death Grenade. Initiative 11, defense 4, movement 2, radius 2, item **initiative**. Place a Grenade in radius. At end of turn, up to 1 enemy hero then adjacent to it discards or is defeated. Then remove it. A hero who is immune "this turn" is still immune at end of turn (catalog FAQ). If Min is defeated first, the effect is cancelled and the token stays until end of round (rulings/min.md 8).

`min-blue-3b` Holy Death Grenade. The same, up to 2 enemy heroes, defense 5, item **initiative**.

`min-purple-4` Flurry of Blows. After each attack action (not after a repeat), he may repeat it once on a different target. The push's target is already "targeted", so the repeat cannot pick the unit he pushed (rulings/min.md 11).

`min-handicap` Chop Down. Initiative 12, attack 1, defense 1, movement 1, range 1, ranged. A hero adjacent, not a minion; the same red-text copy. Attack 1. Use it to apply a push or a smoke swap, not to kill.

## 3. Upgrade path

Official: mines versus grenades is the blue choice; gold copies the red even if the red was spent; end moves on the smoke line.

1. **Red, the stance.** `min-red-2a` / `3a` (push, step-around, initiative item then movement item) when he is the body's controller: the push of 3 is how a melee hero moves someone who tried to stand on a minion. `min-red-2b` / `3b` (attack 5, defense item, smoke swap) when he must hit and leave, or when defense items are how he lives. Default 2a. The gold copies whichever he took, so this choice is also the gold's second text.
2. **Green.** `min-green-2a` / `3a` while he plays the smoke bomb. The block is his real defense and the range-4 swap is the escape. Tuck defense. `min-green-2b` / `3b` when he already has two items and knows the numbers: doubling is the video-comment build, and it is strongest with a range item and a defense or attack item, not with an initiative item he does not need (he is already initiative 8). Perfect Self's permanent tier II item is the grab. Default the block (2a) until he has two items, then 3b is allowed as the tier-3 switch even if tier 2 was the block.
3. **Blue.** Grenade (`2b`/`3b`) with Ignatia or any ally who hits the hero he tagged, and whenever turn-4 kills are the win. The initiative item is often a blank on a hero this fast; take it only to win a specific tie at 11 or 12. Mines (`2a`/`3a`) when the map has a corridor the enemy must walk and he wants the attack items. Default grenade. Official: grenades need timing, mines are the less reliable net.
4. **Ultimate** makes every attack a second attack. After it, prefer the push red: two attacks and two pushes. The smoke-swap red still leaves once.

Item priority: defense (he starts at defense icons 1 on gold and silver), range (gold is range 1; a range item makes the copy attack reach), radius (smoke and mines), movement, attack, initiative last.

## 4. Synergies and counters

- Ignatia on her discard blue [BGG 3487749]. Grenade adjacent to a hero, her blue discards that hero because they are adjacent to a token, and on turn 4 her tier-3 blue defeats them. His holy grenade then catches a second body at end of turn. If the combo is not there he still pushes and she still farms.
- A melee ally who acts after his push. Crane's push of 3 delivers a body.
- He is his own setup. The smoke line protects an ally who stands so the bomb is between them and the shooter.

He fears: a faster gold than his red. He arrives on green or on movement and they walk. Arien and anyone who attacks from a space the smoke does not block, including a charge that ends beside him. Heavy minions, because he is melee and attack 3–5. Losing the smoke bomb off the board (it was the only one, and green cannot block).

Against him:

- Do not end a move on a straight line that passes through the smoke bomb if he or the minion is the target. Step one space off the line and the shot is legal.
- The bomb is an obstacle. He also cannot walk through it. Neither can you. A corridor with the bomb in it is closed.
- Grenade: leave the token's adjacent spaces before end of turn, or be immune this turn, or have a card you can lose. On turn 4 with `min-blue-3b`, two heroes adjacent to it are the kill.
- Mines are passable and secret. Do not walk a path of two or three fresh mines when a one-space detour exists, unless the detour is a real hit. He knows which are blasts; you do not.
- His gold does nothing extra while the red is still in hand. Pressure him before he has played the red if you do not want the push.
- Alt green doubles items for the round, not only while his discard is empty. The 1st-printing "poke him and the buff dies" is false. Hit him because defense icons on gold and silver are 1, not because the buff is fragile.

## 5. Common mistakes

- Playing gold before the red is faceup. Attack 2, no push, no swap.
- Expecting the 3-move leave on a kill. Tiger and Dragon do not step around a removed target.
- Playing Poof / Vanish / Ruse with the bomb out of that card's range. The block fails and he dies.
- Treating the smoke bomb as passable. It is not, on this printing.
- Swapping to the bomb with the green and then having no bomb on the line that mattered.
- Cluster Mine as if it were two blasts. It is one blast and two duds. Minefield is the two-blast card.
- Throwing the grenade adjacent to himself on a turn an enemy wants to hit him, or onto a space the target can leave before end of turn.
- Stacking initiative items. His red is already the card that must go later than their escape; making it faster helps them walk after he has moved in.
- Perfect Self pulling the tier II branch he still wanted in hand.

## 6. HEURISTIC RULES

Own mines: he knows blast from dud. Enemy-facing threat uses the printed blast count, not the hidden layout (no peeking). "Red faceup" means his red is resolved this round or in the discard.

1. `PLAY_CARD` [`min-silver`] WHEN the smoke bomb is not on a straight line between an enemy hero and Min, or between an enemy hero and an ally or a friendly minion he is protecting AND a space in radius 3 completes such a line AND that space is one he can swap to later (within range 4 of where he wants to be) THEN play silver and place it there. WEIGHT 8. Do not place it on the only path his ally needs. HContext. SOURCE [ADVICE]; rulings/min.md 4.

2. `CHOOSE_SPACE` [any move, and the step-around on `min-red-2a` / `min-red-3a`] WHEN the smoke bomb is on the board THEN end on a space that puts the bomb strictly between him and the nearest enemy hero. WEIGHT 6. SOURCE [ADVICE].

3. `PLAY_CARD` [`min-red-1`, `min-red-2a`, `min-red-3a`, `min-red-2b`, `min-red-3b`] WHEN an enemy hero or the lane minion is adjacent AND his attack total beats what they can defend, or the push or the swap is the point even if they defend THEN play the red. WEIGHT a kill if it kills, 15 for the lane minion, 6 for a push that moves a hero off a minion. SOURCE [BGG 2886926] comment that still matches: the slow card is how a fast melee threatens.

4. `CHOOSE_SPACE` [the push on Crane / Tiger / Dragon] WHEN an enemy unit is adjacent after the attack THEN push a hero who is not the kill target up to 3, off a minion or into a wall, rather than the unit he just hit, unless the hit target is the one who must move. Distance 3 if the path is open, else the farthest that changes their next attack. WEIGHT 8. The repeat from the ultimate cannot target that same unit. SOURCE rulings/min.md 3 and 11.

5. `CHOOSE_TOKEN` [`min-red-2b`, `min-red-3b`] WHEN the attack connected AND the smoke bomb is in radius 4 AND an enemy can still attack his current space THEN swap. On Cobra, place the bomb back onto a line between the enemy and the space he left or an ally. WEIGHT 8. If no enemy acts after him, do not spend the bomb's position. SOURCE card text.

6. `PLAY_CARD` [`min-gold`] WHEN the red is faceup AND a unit is in range (1 + range items) AND (the copied text does something now OR the unit is the lane minion) THEN play gold. Copied push: same as rule 4. Copied swap: same as rule 5. If the red is in hand, do not play gold unless attack 2 takes a minion he would otherwise lose and no enemy acts after initiative 13. WEIGHT 10 when the copy fires, 4 for a bare minion. SOURCE [ADVICE]; rulings/min.md 1.

7. `DEFEND` [`min-green-1`, `min-green-2a`, `min-green-3a`] WHEN the smoke bomb is in that card's range AND the swap is legal AND the attack would hit THEN block with the green. WEIGHT the hero kill it prevents. WHEN the bomb is out of range THEN the green is not a defense; use red (defense 5–6) if it beats the total. On Ruse, after a defensive swap, place the bomb onto the line the attacker would need next turn. SOURCE rulings/min.md 5.

8. `PLAY_CARD` [`min-blue-2b`, `min-blue-3b`] WHEN it is turn 1, 2, or 3 AND a space in radius 2 is adjacent to an enemy hero who cannot leave that adjacency before end of turn without giving up their attack, or adjacent to the space a revealed slow card will stand on THEN place the grenade there. WEIGHT 8 for one hero, 14 for two heroes on the holy grenade, and 15 on turn 4 if at least one of them has 0 cards (the defeat). Do not place it adjacent to himself if an enemy attack reaches that space before end of turn. SOURCE [ADVICE] "precise timing"; [BGG 3487749]; catalog FAQ on immunity.

9. `CHOOSE_SPACE` [`min-blue-1`, `min-blue-2a`, `min-blue-3a`] WHEN placing mines THEN put the blasts on spaces an enemy hero must cross to reach the lane minion or to reach him, and the duds on the tempting side path. He places every token or the action does not start, so do not play the card without that many empty spaces in radius. WEIGHT 4 per blast on a forced path, 1 per dud. Against him, the enemy weights a mine at `(blast count / mine count)` * 2.5, about 1.2 on Trip and Cluster and 1.7 on Minefield, and walks around when a detour costs less than that. SOURCE rulings/min.md 7; [ADVICE].

10. `PLAY_CARD` [`min-green-2b`, `min-green-3b`] WHEN he has 2 or more items AND it is turn 1 or the round's first action AND a fight is happening this round THEN double the items. WEIGHT 2 per item he will use on an attack or a defense this round (an attack item on a connecting attack, a defense item if he expects to be hit). With 0 or 1 item, do not spend the green on this; play the bomb line or the mines. On Perfect Self, also take the permanent tier II item whose icon is defense, range, or radius, and not the icon of a branch he still needs in hand. WEIGHT 3 for that item. SOURCE card text; BGG video comment on Perfect Self (community, matches current text); the 1st-printing empty-discard clause is rejected.

11. `CHOOSE_UNIT` [`min-purple-4`] WHEN the ultimate is unlocked AND a second legal target exists that is not a unit this action already targeted (including the pushed unit) THEN repeat on the lane minion if it is still there, else on a hero the first attack left in range. WEIGHT the second hit. SOURCE rulings/min.md 11.

12. `PLAY_CARD` [movement] WHEN he is not adjacent to a target and his red is still in hand THEN move with green or with the red's movement only if the destination is adjacent to a target whose escape card is slower than initiative 9. If their gold is 11–13 and still in hand, do not walk up on a turn they have not moved; threaten with the red's movement 5 from outside their attack, or throw the grenade. WEIGHT −6 for a walk-up that arrives before they act. SOURCE [BGG 2886926] initiative comment, which matches these initiatives.

13. `DEFEND` [non-block] WHEN green cannot block THEN the cheapest red or blue whose defense icon plus items plus minion modifiers beats the attack. Gold and silver at defense 1 are the last resort. SOURCE defense icons.

14. `UPGRADE_CARD` WHEN Ignatia is an ally THEN blue `b` before blue `a`. WHEN he has fewer than two items THEN green `a` before green `b`. Red `a` by default. WEIGHT 3. SOURCE §3; [BGG 3487749].

### evalTerms

- Smoke bomb on a straight line between an enemy hero and Min, or between an enemy hero and a friendly hero or a friendly minion: +8. The same bomb also inside green range of him: +2 more (the block exists).
- Smoke bomb in a space that blocks his ally's only approach and does not sit on a shot line: −4.
- Grenade on the board, for each enemy hero currently adjacent to it who is not immune this turn: +6, or +15 if it is turn 4 and that hero has 0 cards.
- Each of his blast mines on a space, scored only for his own team (he knows): +2.5 if an enemy move this turn would cross it, else +0.5. Duds: +0. A mine is not an obstacle.
- Doubled item bonuses active this round: +2 per item he has. The permanent tier II item is already an item; do not add it twice.

### threatAdjust

When the enemy is Min:

- +6 on spaces adjacent to him while a red is in hand or faceup this round (the push of 3 starts from adjacent). The push threat is the three spaces in a straight line away from him, not a ranged kill.
- +8 on the straight line through the smoke bomb: targeting across that line is illegal. Threat through the bomb is 0. Threat from a space one step off the line is normal.
- +6 on each space adjacent to his grenade until end of turn. 0 after it is removed.
- Mines: from the enemy seat, a facedown mine is `(blasts on that card / mines on that card)` * 2.5 of discard-threat on that space, and 0 as a wall. Do not path through two of them when a clear path exists.
- Gold in hand while the red is still in hand is attack 2 at range 1. Do not add the push threat until the red is faceup.

## 7. Sources

- Catalog `min.json` advice (3 sentences) and the end-of-turn immunity FAQ.
- `core/game/rulings/min.md`. Designer BGG 3533578 is quoted there for the failed mandatory step on Ruse.
- [BGG 3487749] 2025-04-05, community: Min grenade + Ignatia discard blue, strongest on turn 4.
- [BGG 2886926] Min spotlight, 2022-06-21, community, marked outdated in the thread. Kept: fast melee gets kited, so the slow red is the threat; gold copies the red; smoke lines; mines rarely "trick" anyone and work as denial; grenade timing. Rejected: passable smoke, green swaps with mines and grenades and retrieves silver, alt green dies to a discard, alt red repeats on a hero, ultimate as pure movement.
- BGG video 563611, Optimal Play, "Min the Dragonmonk", community comment: Perfect Self doubling items is strong when the numbers are known, and weak if he cannot reach after spending green. Matches `min-green-2b` / `min-green-3b`. The video itself was not transcribed.
- Gamefound Renowned update 8: initiator, mines, smoke, escape, reactive. No numbers taken. "High initiative" matches the dashboard.
- No 2nd-printing spotlight. No Reddit thread found.

## Implementation notes

`MinHeuristics.kt`, checked against `MinHero.kt` (2026-11-21).

- Rule 7: the engine's defense preview (`blockPreview`) already says whether the green's swap with the bomb can happen. The playbook only refuses a green the preview says cannot block.
- Rule 8: the grenade's space is a `CHOOSE_SPACE` with no subject, so the general book would score it as if Min stood there. The playbook scores the space by the enemy heroes beside it: one who has already acted stays until end of turn; an empty hand there is a defeat.
- Rule 6: gold reads only whether a red is faceup (resolved or discarded) and a unit is adjacent (range 1).
- `threatAdjust` adds the grenade's end-of-turn discard only. The smoke bomb's blocked lines would need a negative threat, which the exposure sum does not take; left out.
- Skipped: 2, 4, 5, 9, 11 (spaces and pushes the lookahead plays), 12 (initiative walk-ups: general escape logic), 14 beyond `upgradeOrder`.
