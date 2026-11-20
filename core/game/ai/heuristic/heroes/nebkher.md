# Nebkher (`nebkher`) — heuristic playbook

2nd printing. Icons from `nebkher.json`. The catalog spells the name NebKher; the id is `nebkher`. The 1st-printing spotlight [BGG 2896563] (2022-07-08, community) is a different kit: silver initiative 11 with a suicide and a +2 initiative, green that teleports him when he is discarded, blue that swaps a card in hand, red that walks a friendly minion, ultimate that triggers on his own death. None of that is on the current cards. One ruling survived and is confirmed by the current FAQ and by the designer in that thread: when he performs someone else's action, he performs it from his own space. The designer's 2025 line is the real brief: to play him well he has to know everyone else's cards [BGG 3459990, nichik, 2025-02-12].

The BGG wiki page for him is the 1st-printing card list. Do not use its numbers.

## 1. Identity

| Field | Value |
|---|---|
| Role | `SetUp` |
| Complexity | 4 |
| `idealDistance` | 4. Gold is range 5. He wants as many enemies as possible inside that range [ADVICE], and he does not want to be adjacent to their melee. |
| Lane vs kill | `laneWeight` 0.45. Gold's second option defeats an adjacent minion. His reds are attack 2–4. The lane is a copy of someone else's take, or that gold bullet, not a brawl. |
| `aggression` | 0.35. He ruins a plan. He does not open the fight. |
| `keepDefender` | true. Reds print defense 6–7, but gold is defense 2 and he has no heal. Silver is defense 3 at initiative 3: playing it is a late, exposed turn. The profile defense bar (5→6) is not added. |
| `upgradeOrder` | §3 |

Profile bars (not added in play): Attack 2→3, Defense 5→6, Initiative 1→1, Movement 2→5. Order is the icons. Gold initiative 9 is a slow gold, so he acts after most golds and before most reds. Silver initiative 3 is a green's timing: he repositions last. Green is 5–6, also late. Blue and the tier-1 red are 7–11, so the split and the named colour go before the attacks he wants to blank.

Illusion tokens have no supply limit in the engine (rulings/nebkher.md 14). They are ordinary tokens except where a card says otherwise. Green B makes them count as friendly melee minions **while he is performing actions**, which is a defense modifier on his attacks (−1 for an enemy standing next to one) and a guard for heavy-minion immunity. They are not units. They do not do this while someone else is attacking him (rulings/nebkher.md 15).

## 2. Card-by-card

`nebkher-gold` Mind Grip. Initiative 9, defense 2, movement 1, range 5, skill. Choose one: perform an action on the card in the previous turn slot of an enemy hero in range (turn 1 has no previous slot), or defeat an adjacent enemy minion. Tokens he would place become Illusions and he skips markers. The action is any the card offers him now: primary, movement, fast travel, attack, clear. Not a defense, and not Hold (rulings/nebkher.md 2). He uses the copied card's values. His own items apply to those icons [APP, rulings/nebkher.md 2]. Copied Active effects are his, and they do not end when the original card changes or that hero dies (catalog FAQ). An immune hero's card cannot be copied. Play when a previous-turn card in range 5 is worth more than the minion, which is most strong primaries. Official: keep as many opponents as possible in that range. Defense 2.

`nebkher-silver` Diabolical Laughter. Initiative 3, defense 3, radius 4, skill. He laughs (the primary can always start), then up to three times: swap with an Illusion in radius, place an Illusion adjacent, or swap two resolved cards of an enemy hero in radius without cancelling their Active effects. The same bullet may be taken twice. Official: the rearrange is for the gold, and the green plus this silver are his movement, so play the movers early. With the ultimate, the laugh itself makes every targetable enemy hero in radius 5 discard or be defeated, before the choices (rulings/nebkher.md 10). That radius is the ultimate's, plus radius items.

`nebkher-red-1` Phantasmal Sentry. Initiative 7, attack 2, defense 6, movement 3, range 4, ranged. A hero in range who is adjacent to an Illusion that is also in range, or a unit adjacent to him. Attack 2 does not kill. The adjacent option is the minion take. The hero option is a tag that exists only while an Illusion is next to them.

`nebkher-red-2a` Phantasmal Warrior. Attack 3, defense 7, range 5, initiative 7, item **initiative**. Before the attack he may move an Illusion in range up to 1, onto a space adjacent to an enemy hero in range, then he must target that hero. Or he targets a unit adjacent to him. The Illusion move is normal movement: obstacles block it (rulings/nebkher.md 12).

`nebkher-red-3a` Phantasmal Champion. The same, the Illusion moves up to 2, attack 3, defense 7, range 5, initiative 8, item **radius**.

`nebkher-red-2b` Twist Fate. Initiative 7, attack 4, defense 6, movement 3, range 2, ranged, item **defense**. A unit in range. After a connecting attack, he may swap an enemy unit in range with an Illusion adjacent to him. Attack 4 is his reliable hit. Range 2 is short. The defense item is why this branch is the safe one.

`nebkher-red-3b` Devious Scheme. Attack 4, defense 6, range 2, initiative 8, item **defense**. The swap partner is an Illusion **in range**, not only one adjacent to him.

`nebkher-green-1` Fleeting Image. Initiative 6, defense 3, movement 2, radius 2. Place an Illusion in radius (mandatory), then he may swap with any Illusion on the board. His first teleport. Radius 2 is small. Play early [ADVICE].

`nebkher-green-2a` Multiple Projections. Place up to 2, radius 3, initiative 5, item **initiative**, then the same swap.

`nebkher-green-3a` Master of Illusions. Place up to 3, radius 3, defense 4, item **initiative**, then the swap.

`nebkher-green-2b` Illusionary Force. Place up to 2, radius 4, item **attack**. This round, while he performs actions, every Illusion is a token and a friendly melee minion. The attack item matters because his printed attacks are 2–4 and the minion modifier is the other +1 in disguise (it is −1 on them).

`nebkher-green-3b` Illusionary Army. Place up to 3, radius 4, defense 4, item **attack**. The same this-round rule.

`nebkher-blue-1` Imbue Doubt. Initiative 10, defense 4, movement 2, radius 3. Name a colour. Next turn, after cards are played, an enemy hero in radius discards a card of that colour, if able. The radius is measured from where he is then. A turn-4 name does not carry into the next round (catalog FAQ).

`nebkher-blue-2a` Time to Reconsider. Radius 4, defense 5, initiative 11, item **attack**.

`nebkher-blue-3a` An Illusion of Choice. Up to two enemy heroes, radius 4, defense 5, item **movement**. The movement item adds +1 to movement icons (gold prints 1, most skills print 2).

`nebkher-blue-2b` Crack in Reality. Initiative 11, defense 5, movement 2, item **defense**. He picks one of the three straight lines through his space. This turn, a unit off the line cannot target, move, or place across it. Units on the line can affect both sides (catalog FAQ). The line does not move with him.

`nebkher-blue-3b` Shift Reality. The same split, and units off the line also cannot interact with him, wherever he is now. Item **range**. Range grows gold and the reds. This is the self-save: only a unit standing on the line can touch him.

`nebkher-purple-4` What the Hell Are You? Radius 5. After he laughs as part of an action, every targetable enemy hero in that radius discards or is defeated. The laugh is on silver. Every silver play with the ultimate unlocked is this, and then the three choices.

`nebkher-handicap` Mind Probe. Initiative 8, defense 1, movement 1, range 3. Perform an action on the previous-turn card. No minion bullet. "Do not use markers" instead of "skip giving markers"; same result (rulings/nebkher.md 3). Range 3, not 5. A weaker gold with no farm option.

## 3. Upgrade path

Official: gold wants bodies in range; silver rearranges for gold; green and silver are the walk, and they are played early.

1. **Green first or second, and early in the round once he has it.** `nebkher-green-2a` / `3a` is the teleport: place, then swap to an Illusion that is already where the fight will be. Two initiative items on a stat of 1. Take one so silver and green are not the slowest cards on the table; do not take both unless he is losing the race to place before their green. `nebkher-green-2b` / `3b` when he will attack (red A needs the Illusion next to the hero, and the melee-minion count makes attack 3 real) or when a heavy is being protected by nothing. Default 2a. He cannot walk on movement 2.
2. **Red.** `nebkher-red-2b` / `3b` is the farm and the defend: attack 4, range 2, a defense item, and a swap that moves an enemy onto an Illusion. `nebkher-red-2a` / `3a` is the hero shot: attack 3 at range 5, but only at an Illusion he can walk next to them. Default 2b until Illusions are reliably next to heroes, then 3a if the team needs the long tag. The 1st-printing "you need a friendly minion" is false. He needs an Illusion.
3. **Blue.** `nebkher-blue-2a` / `3a` names a colour and strips it next turn, after they have played. Name the colour they will have **left**, not the colour they will play. At tier 3, two heroes, and the movement item. `nebkher-blue-2b` / `3b` is the wall and, at tier 3, the personal immunity to anyone off the line. Take B when the enemy is ranged (the 1st-printing spotlight's matchup note still fits this card: a line between Silverarrow, Dodger, or the current Ignatia and her target blanks the shot). Default A. Shift Reality's range item is the reason to take 3b in a game he is being focused, even if 2b was not the plan.
4. **Ultimate** makes silver the round's kill card whenever two or more enemies are in radius 5. Do not spend silver at initiative 3 with the ultimate up and nobody in that radius. Do spend it when they are, even if the three choices are small: the laugh is the action.

## 4. Synergies and counters

He does not have a combo partner so much as a table he can read. Copying is better in 3v3 than in 2v2 because more previous-turn cards sit inside range 5. That part of [BGG 2896563] still matches the current gold.

- A lane ally who takes the minion he is not copying. His attack 2–4 is a bad sole farmer. Gold's minion bullet covers one.
- Anyone whose previous turn was a strong primary he wants twice: a push, a pull, a big attack, a place. He should stand in range 5 of that hero at the start of the next turn, before initiative 9.
- Gydion. Mind Grip on a Gydion card casts from Gydion's spellbook, and Nebkher is the caster (rulings/gydion.md 9). He should copy a live spell, not a card whose book is empty. He does not get to look at the book before he chooses; the engine has no hidden information there, but the heuristic should still only copy a Gydion card when the deduced book can start that spell.
- Ignatia's current Equilibrium, if copied, gives **him** the this-round choice on coin texts he then performs. It does not set the coin. The FAQ line that says it does is the old card (rulings/ignatia.md 4).

He fears: being caught out of position on movement 2 with silver and green already played. Empty range: gold becomes "defeat an adjacent minion" or a walk. A table of heroes whose previous turn was movement, so the copy is a walk. Discard into his gold, because gold's defense icon is 2 and it is the card he most wants in hand on the turn he copies.

Against him:

- Do not play your best primary on a turn he is in range 5 and will live to act next turn. Play it late in the round, or on turn 4, when there is no next turn inside the round. A "next turn" effect played on turn 4 dies at end of round anyway; his copy of it next round is a new action and the old Active effect is gone.
- Previous turn slot is the faceup card on the slot numbered one less. A facedown card has no action he can take. If you can keep the dangerous card facedown, he cannot copy it (rulings/nebkher.md 1).
- After he laughs with the ultimate, every hero in radius 5 is a discard or a death. Leave the radius before initiative 3, or have a card you can lose and a defender for the second hit.
- Crack: the line is three fixed directions through the space he occupies when he plays it. Stand on the line if you must touch the other side. Against Shift Reality, a unit off the line cannot touch him at all. The counter is to already be on the line, or to have acted before initiative 11.
- Named colour: he calls it this turn and it fires next turn after you play. Play the named colour. Keep a different colour. Do not hold two of the named colour unless you will play one and defend with the other on purpose.
- His Illusions are not minions while you are attacking him. Do not fear a −1 that is not there on your defense. Do fear it on his attack, the turn he is acting, if an Illusion is next to you and green B is active.
- Killing the Illusions is available to anything that removes tokens. He places more. Clear the one that is next to you if his red A is in hand; ignore the ones in the open.

## 5. Common mistakes

- Copying a previous-turn movement, or a card whose primary cannot start from his space, when an adjacent minion was the lane. The minion bullet is the honest play.
- Copying on turn 1. There is no previous slot.
- Playing green late. Official: play it early. A teleport after the fight has moved is a walk to an empty Illusion.
- Silver as a defender because it is "the trick". Defense 3, initiative 3. He stands there all turn first.
- Naming the colour they are about to play. The discard happens after next turn's play. Name the spare.
- Naming on turn 4. It does not carry.
- Red A with no Illusion that can reach a space adjacent to a hero. The bullet is not offered. He is then a melee attack 3, which is a minion card.
- Treating green B's Illusions as a wall on the enemy's turn. They count as minions only while he acts.
- Expecting units on the Crack line to be split. They can touch both sides. Shift Reality still saves him from units off the line, not from a unit on it.
- Using the 1st-printing silver (suicide, +2 initiative, swap with a minion) or the 1st-printing ultimate (it does not trigger on his death).
- Rearranging resolved cards and forgetting Active effects stay. He moves which slot Mind Grip will see. He does not turn their effect off.

## 6. HEURISTIC RULES

"Previous card" means the faceup card on that enemy's previous turn slot. Empty on turn 1, and empty if the card is facedown. Colour names are gold, silver, red, green, blue.

1. `PLAY_CARD` [`nebkher-gold`] WHEN it is turn 2 or later AND an enemy hero in range 5 has a previous card whose primary, from Nebkher's space, defeats a hero, takes the lane minion, discards, or places him in the fight AND that action can start THEN play gold and copy it. WEIGHT the copied effect (a lane minion 15, a discard 2.5, a copied attack at its kill chance). WHEN the best copy is worth less than the adjacent lane minion THEN defeat the minion. WEIGHT 15. Do not copy Hold, and do not copy a card that cannot start. HContext plus the known card pool (he is expected to know the catalog: [BGG 3459990]). SOURCE [ADVICE]; rulings/nebkher.md 1–2.

2. `CHOOSE_ACTION` [the copied card] WHEN several actions are offered THEN the one with the highest weight under rule 1, performed from his space, not from the original hero's space. Tokens become Illusions. Do not give markers. A copied Gydion cast only if the deduced spellbook can start it. SOURCE rulings/nebkher.md 3; rulings/gydion.md 9; designer reply in [BGG 2896563] ("from Nebkher's position"), which matches the current card.

3. `PLAY_CARD` [`nebkher-green-1`, `nebkher-green-2a`, `nebkher-green-3a`] WHEN it is turn 1 or he is more than 3 spaces from range-5 of the heroes he wants to copy THEN place Illusions on empty spaces in the battle zone and on a space he wants to occupy, then swap to the one that puts the most enemies in gold range without standing adjacent to two of them. WEIGHT 8 for entering that range, 3 per Illusion that will be next to an enemy for red A. Play this before silver if both are in hand and he is out of position. SOURCE [ADVICE].

4. `PLAY_CARD` [`nebkher-green-2b`, `nebkher-green-3b`] WHEN he will attack this round AND an enemy he can reach will be adjacent to an Illusion he places THEN place them adjacent to that enemy, not as a teleport net. The modifier applies only while he acts. WEIGHT 4 per such Illusion (the −1 defense is about one point of attack, priced at 4 when it changes a kill into a hit or a hit into a kill). Also play this when a friendly heavy in the battle zone has no other non-heavy guard and an Illusion in that zone will supply it. WEIGHT 6. SOURCE rulings/nebkher.md 15.

5. `CHOOSE_OPTION` [`nebkher-silver`] WHEN an Illusion in radius 4 is in a better zone than his space THEN swap to it, and spend another choice placing an Illusion where he may need to be next turn. WHEN an enemy in radius has two resolved cards and swapping two slots puts their strongest primary onto the slot that will be "previous" on the turn he wants to copy it THEN do that swap, once. Do not swap slots if it turns off a copy he wanted this round. WEIGHT 10 for the teleport, 8 for a rearranged gold setup. Initiative 3: this is the last action. Do not play silver for a swap he could have done with green at initiative 5–6 unless the rearrange or the ultimate laugh is the point. SOURCE [ADVICE].

6. `PLAY_CARD` [`nebkher-silver`] WHEN the ultimate is unlocked AND two or more enemy heroes are in radius 5 and targetable THEN play silver even if the three choices are weak. The laugh defeats those with 0 cards and discards the rest. WEIGHT a hero kill per empty-handed hero, plus 2.5 per hero with a card. WHEN nobody is in that radius THEN do not play silver for the laugh; use green. SOURCE rulings/nebkher.md 10.

7. `CHOOSE_OPTION` [`nebkher-red-2a`, `nebkher-red-3a`] WHEN an Illusion in range can move up to 1 (or 2) onto a space adjacent to an enemy hero in range AND attack 3 plus items plus the green-B modifier (if active and the Illusion will be adjacent) beats what they have, or they have 0 cards THEN walk the Illusion and hit that hero. ELSE if a minion is adjacent and it is the lane THEN the melee bullet. Attack 3 on a healthy hero who can defend is a wasted red: do not walk the Illusion in for it. WEIGHT the kill, else 4 for a tag, else 15 for the minion. SOURCE card text.

8. `CHOOSE_UNIT` [`nebkher-red-2b`, `nebkher-red-3b`] WHEN a unit in range 2 is the lane minion or a hero attack 4 kills THEN attack it. After a connecting hit, swap an enemy unit in range with an Illusion if the swap pulls a hero off a minion or puts the hero next to his ally. On Twist Fate the Illusion must be adjacent to him; on Devious Scheme it must be in range. WEIGHT 15 for the minion, 8 for a swap that changes a take. SOURCE card text.

9. `CHOOSE_OPTION` [`nebkher-blue-1`, `nebkher-blue-2a`, `nebkher-blue-3a`] WHEN it is turn 1, 2, or 3 AND at least one enemy who will be in radius next turn (3, then 4) can be predicted to hold a colour after they play THEN name that spare colour. Prefer a colour that is a defense icon of 5 or better, so the card they lose is the shield, not a spent trick. On the tier-3 card, both heroes if both can hold it; else the one colour shared by the two heroes most likely to still be in radius. WEIGHT 2.5 per card it will actually take, 0 if he names a colour they will play. Do not play this on turn 4. SOURCE card text; catalog FAQ; the deduction is [BGG 3459990].

10. `CHOOSE_SPACE` [`nebkher-blue-2b`, `nebkher-blue-3b`] WHEN a straight line through his space puts every enemy who has not acted on the other side of an ally they could hit, or on the other side of the lane minion, THEN that line. On Shift Reality, prefer the line that also leaves no enemy **on** it, so nobody can target him. Units already on the line can still act across it; do not count them as blocked. WEIGHT 12 for blanking a revealed attack, 8 for saving himself on 3b. Initiative 11: play it before their reds (typically 7–9) and not after their golds have already fired. SOURCE catalog FAQ; rulings/nebkher.md 17.

11. `PLAY_CARD` [movement or green] WHEN fewer than two enemies are in gold range 5 AND he has not yet placed an Illusion he can swap to THEN the green, not a red and not the blue name. WEIGHT 6. A turn spent attacking from outside range 5 is a turn the gold does nothing next time either, because he will not be in range to copy. SOURCE [ADVICE].

12. `DEFEND` [any] WHEN the attack total is beaten by a red (defense 6–7) or a blue (defense 4–5) plus items and minion modifiers THEN spend the cheapest of those, and do not spend gold (defense 2) unless it is the only success. Green B's Illusion does not add a minion modifier on **this** defense; he is not the one acting. SOURCE rulings/nebkher.md 15; defense icons.

13. `CHOOSE_CARD` [the silver rearrange] WHEN swapping two resolved cards would move an Active effect he needs to keep onto a slot he will not copy, that is fine: effects do not cancel. Score only which card Mind Grip will see next turn, and which card the enemy will wish were still on the earlier slot. WEIGHT 8 when the next gold copy changes, else 0. Do not rearrange a facedown card hoping to learn it; the log will not name it, but do not spend a choice for information the seat does not get. SOURCE rulings/nebkher.md 9; catalog FAQ.

14. `UPGRADE_CARD` WHEN he has been out of the battle zone at the end of a round THEN green A first. WHEN he is the one being attacked THEN red B for the defense item, then blue B at tier 3 if the focus continues (Shift Reality). WHEN Illusions are already next to heroes THEN red A. Blue A when hands are full and predictable. One initiative item, not two. The movement item on `nebkher-blue-3a` outranks a second attack item. WEIGHT 3. SOURCE [ADVICE]; [REASONED].

15. `RESPAWN` [`nebkher`] WHEN respawning THEN a space that will be in range 5 of at least two enemies after one green swap, not the deepest corner of his zone. WEIGHT 6. SOURCE movement 2 and gold range 5.

### evalTerms

- Each Illusion adjacent to an enemy hero and inside his current red range: +5 while red A is in hand or is his current card. +2 if only green B is active and he has an attack (the modifier), which stacks with the +5 when both are true: the adjacency is the targeting condition and the modifier.
- An Illusion on a space he can swap to that is inside gold range of more enemies than his current space: +4 per extra enemy that swap would add, cap +12.
- Green B active this round: the modifier is already in the combat preview while he acts. Do not add a flat bonus on top of the per-Illusion term. +0 extra.
- A named colour pending, and at least one enemy currently in the radius he will have next turn who could hold that colour after playing: +3 per such hero. On turn 4 this term is 0.
- Crack or Shift line this turn, if it separates an enemy who has not acted from a target they could otherwise hit: +10. Shift, and no enemy on the line: +8 more for his own safety.
- Gold in hand on turn 2–4 with two or more enemies in range 5: +4. This is the option value, not a token.

### threatAdjust

When the enemy is Nebkher:

- +6 on his gold range (5) at the **start of a turn after** a strong primary was played, for the hero who played it. The danger is next turn, from his space, not a halo of attack 5. Do not paint range 5 as an attack.
- +4 on spaces adjacent to one of his Illusions while red A may be in hand, if that space is also in his red range. Attack 2 or 3. The lethal version is green B active and the defender already wounded: +8.
- +3 on range 2 while red B may be in hand (attack 4), and +4 on an enemy unit that could be swapped with an Illusion after that hit.
- +8 on every enemy hero space inside radius 5 while the ultimate is unlocked and silver may still be played. That is a discard, or a kill if the hand is empty.
- The Crack line, once played, is a hard cut: threat and movement across it are 0 for units off the line. Units on the line are not cut. Shift Reality: units off the line also have 0 threat into him.
- A named colour is not a board threat this turn. It is a hand constraint next turn. The enemy playbook drops that colour's value as a keeper and raises it as the card to play.
- Safe plan: play the strong primary on turn 4, or from outside range 5, or into a facedown slot. Do not stand next to his Illusion at range.

## 7. Sources

- Catalog `nebkher.json` advice (3 sentences) and the FAQ (Mind Grip interactions, the laugh is not literal, units on the Crack line, turn-4 "next turn" dies).
- `core/game/rulings/nebkher.md` and the Gydion and Ignatia rulings it points at.
- [BGG 3459990] article 45647762, 2025-02-12, author user 202424 (nichik / Arty, designer): Nebkher is a 4-star, hard for the whole table, and "to play Nebkher competently you need to know everyone else's cards pretty well."
- [BGG 2896563] Nebkher spotlight, 2022-07-08, community. 1st printing. Kept: he is slow and disruptive, better with more enemies in gold range, and a copied action resolves from his space (designer reply in the thread). Rejected: the printed silver, both greens, both blues, the minion-walking red, the death ultimate, and every number that is not on the current card.
- The BGG wiki `guards-atlantis-ii-tabletop-moba-nebkher` is the same old list. Not used for any number.
- No 2nd-printing spotlight and no build guide in the strategy forum (83 threads, listed 2026-11-20). No Reddit thread found. Branch order is [REASONED] from the current text plus the three official advice sentences.
