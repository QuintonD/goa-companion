# Hanu (`hanu`) — heuristic playbook

2nd printing. Numbers are catalog icons (`hanu.json`) plus items. The 1st-printing spotlight [BGG 2894481] (2022-07-04, community) describes a different kit: gold and silver turned off by a non-empty discard, a green that blocked and sent him to his throne, a blue that made **both** heroes immune, and an alt red that attacked any unit in range. None of that is on the current cards. Those lines are rejected. What still matches is noted in the rules. The 2025–2026 combo thread [BGG 3487749] is 2nd-printing and is used.

## 1. Identity

| Field | Value |
|---|---|
| Role | `Support` (he also `Pusher` when an ally stands next to the target) |
| Complexity | 3 |
| `idealDistance` | 2. Gold wants adjacent, then a straight 3-space leave. Red wants range 3–4 onto a hero who is adjacent to an ally and, at tier 3 primary, not adjacent to Hanu. |
| Lane vs kill | `laneWeight` 0.50. Gold attack is 2, so minions are his reliable gold kills; heroes are a team shot. |
| `aggression` | 0.45. He starts fights only when the 3-space line or an ally's follow-up exists. |
| `keepDefender` | true. Gold, silver and both greens print defense 1–2. Red prints 4–6 and blue prints 4–5. A red or a blue stays in hand if an attack can reach him. The profile defense bar is 1→1 and is not added. |
| `upgradeOrder` | §3 |

Profile bars (not added in play): Attack 4→4, Defense 1→1, Initiative 8→8, Movement 7→8. The cards are why the bars are high: gold prints initiative 13 and movement 2, reds print movement 5, blues print initiative 10–11, greens print initiative 1–2. He repositions by those icons. He does not tank.

## 2. Card-by-card

`hanu-gold` Fight and Flight. Initiative 13, attack 2, defense 1, movement 2. Adjacent unit. If the target is **not** defeated, after the attack he moves 3 spaces in a straight line, if a line exists (rulings/hanu.md 1). The 3-move does not happen when the target dies. Play as the fast minion take only when he is happy to stay, or as a hero tap when a 3-line escape exists and the hero will survive (attack 2 usually does not kill). Do not play it into a space with no 3-line when the enemy acts after him. It is a bad card to defend with.

`hanu-silver` Hurry up! Initiative 12, defense 1, range 4, skill. Set the printed initiative of an unresolved card of a hero in range (never himself) to 11 until that card resolves. Items and other modifiers still add (rulings/hanu.md 3). Friend or enemy. Play after the reveal, when changing one card's place in the order wins a hit or saves a body. Do not play it when nobody in range has an unresolved card. Defense 1: it is not the card he holds to live. With the ultimate unlocked, this card also hands him the target's action (§6 rule 4).

`hanu-red-1` Helping Hand. Initiative 9, attack 3, defense 4, movement 5, range 3, ranged. Choose one: a unit adjacent to him, or a hero in range who is adjacent to his friendly hero (presence, not a target). The early team attack and his first real defender. The ranged option is dead with no ally next to the enemy.

`hanu-red-2a` Even the Odds. Same choice, attack 4, defense 4, movement 5, range 4, initiative 9, item **initiative**. The support-damage branch. Tuck when the team does not need him to act earlier.

`hanu-red-2b` Outnumber. Choose one: a unit adjacent to him, or a **minion** in range adjacent to his friendly hero. Attack 5, defense 5, movement 5, range 4, initiative 9, item **defense**. The lane branch. Higher attack, and the ranged shot is only a minion. Tuck the defense item when he is the one being hit.

`hanu-red-3a` Trusted Sidekick. Initiative 10, attack 4, defense 5, movement 5, range 4, item **movement**. Choose one or both, in any order: a unit adjacent to him, and a hero in range who is adjacent to his friendly hero **and not adjacent to him**. Two attacks, and the second cannot be the hero standing on him. Not a double hit on one adjacent hero (the 1st-printing "solo turn-4 kill" does not exist). The movement item adds +1 to those movement-5 icons.

`hanu-red-3b` Pile On. Initiative 10, attack 5, defense 6, movement 5, range 4, item **radius**. One or both: adjacent unit, and a minion in range next to his ally. Best printed defense in the deck (6). Radius item grows the greens and blues.

`hanu-green-1` Monkey Trick. Initiative 2, defense 1, movement 2, radius 1, skill. Swap two friendly units in radius, not himself (rulings/hanu.md 7). Radius 1 means both are adjacent to him. Needs two other friendlies. Play last, to put an ally onto a target or a minion off a bad space. With fewer than two legal units the primary does not start: do not play the card for the text.

`hanu-green-2a` Monkey Twist. Same swap, radius 2, defense 2, item **defense**. The ally-positioning branch.

`hanu-green-2b` Hear Nothing. Initiative 2, defense 2, movement 2, radius 3, item **attack**. Swap with an enemy hero in radius. A late pull. No immunity and no swap-back. Tuck the attack item; his attacks are small and the item is the weak half of this card.

`hanu-green-3a` Monkey Business. The tier-2a swap, plus he may move 1. Radius 2, defense 2, initiative 1, item **defense**.

`hanu-green-3b` See Nothing. Swap with an enemy hero in radius 3, then he may move 1. Initiative 1, defense 2, item **range**. The range item grows red and silver. The move after the swap is how he does not stay on the enemy.

`hanu-blue-1` Unexpected Journey. Initiative 10, defense 4, movement 3, radius 2. Swap with an enemy hero in radius. That hero is immune this turn. At end of turn they swap back, ignoring radius and immunity, unless one is off the board or a swap is forbidden (rulings/hanu.md 8–9). Hanu himself is not immune. Play when the enemy has not yet acted and their card needs the space they are standing on. Do not play it if an ally still to act was going to attack that hero: the immunity blanks the ally.

`hanu-blue-2a` There and Back Again. Same, radius 3, defense 4, initiative 11, item **attack**.

`hanu-blue-2b` This Way! Initiative 11, defense 4, movement 3, radius 3, item **initiative**. A friendly hero in radius chooses 1 or 2. Hanu chooses the direction. Both move that many spaces in that straight line, or neither moves (rulings/hanu.md 10). The distance decision is the ally's seat, not Hanu's, unless the ultimate is controlling that ally. Offense: push a position-dependent ally (Brogan, Ignatia, Swift) onto their line. Defense: leave together.

`hanu-blue-3a` Safe Travels. The journey text, radius 3, defense 5, initiative 11, item **attack**, and after the end-of-turn swap-back he may move 1. The 1-move is after he has returned to his own start, so it is a small reposition, not an escape from their space.

`hanu-blue-3b` That Way! Same as This Way, distance 1, 2 or 3, defense 5, item **initiative**.

`hanu-purple-4` The Ultimate Trick. He chooses the next action, and how it is performed, of a hero he targets with Hurry up!. The controlled window is that hero's next resolved action only: not the respawn, not a defense, not choices the text gives to someone else (rulings/hanu.md 4).

`hanu-handicap` Fight or Flight. Initiative 12, attack 1, defense 1, movement 1. A hero adjacent, not a minion; the same survive-then-move-3. Attack 1 will not kill. Use it only for the forced 3-move after a tap.

## 3. Upgrade path

Official advice: gold keeps him alive when a 3-line exists; silver speeds an ally or drags an enemy forward; tell the team, the setup is the point.

1. **First upgrade.** Keep `hanu-red-2a` if an ally is a melee body (the ranged shot is a hero). Keep `hanu-red-2b` if the job is the lane (attack 5, defense item, the ranged shot is a minion). Tuck the other. The defense item from 2b matters because nothing else raises the defense icons on gold, silver and green.
2. **Second, the escape and the pull.** Against ranged poke or a team that will stand on his ally, keep `hanu-green-2b` (swap the enemy, radius 3) and tuck attack. Against a team he must reposition, keep `hanu-green-2a` and tuck defense. Default: 2b, because radius 1 on the tier-1 green barely functions and radius 3 on a late swap does.
3. **Blue.** Keep `hanu-blue-2b` / `hanu-blue-3b` when a teammate's cards need a specific space (Brogan's line, Ignatia's line, Swift). The initiative items stack on an already high stat; take them only if a key tie is on 11 or 12. Otherwise keep `hanu-blue-2a` for the larger radius on the swap, and do not expect the attack item to make gold kill heroes.
4. **Tier 3 red.** `hanu-red-3a` when hero kills are the plan and an ally will be adjacent to the victim: the movement item and the second attack. `hanu-red-3b` when he is dying or the lane is the win: defense 6 and a second minion shot. The radius item on 3b is the one that makes green and blue reach.
5. Do not build three attack items. His gold stays attack 2. Defense and radius and range (`hanu-green-3b`) change more turns.

1st-printing "empty discard or three of your cards shut off" is false. He may play gold and silver after a discard. He still should not spend his only defense 4–6 card on a minion when an enemy attack is unresolved and reaches him.

## 4. Synergies and counters

Teammates, from [BGG 3487749] (2026-07-21, community) and the cards:

- Xargatha. Her pull puts a hero next to her, which is exactly `hanu-red-*`'s ranged condition. His silver raises her primary red. His green and blue swap the enemy onto her or her onto them. His blue 2b/3b plus her primary blue is the lockdown the thread names.
- Razzle, Takahide, Rowenna, any hero "who cares about a friendly hero adjacent". His red does not function at range without that body. [BGG 3487749]
- A slow melee ace (Brogan, Ursafar). Silver sets their red's printed initiative to 11, so they act before the enemy walks. This is the part of [BGG 2894481] that still matches the current silver, minus the old "full distance in a straight line" clause, which is gone.
- He is a bad partner for a sniper who will not stand next to the enemy. The ranged red then never turns on. Take `hanu-red-2b` and shoot minions, or do not draft him into that pair.

He fears: his own defense icons. A single red attack of 5 or more kills him if he spent the red and the blue. Heroes who ignore his swap (Brogan's Bulwark, anything that cannot be swapped) turn the green and the blue into walks. He does not fear a non-empty discard; that was the old printing.

Against him:

- Do not stand adjacent to his ally inside his red range (3, then 4) if you cannot defend attack 3–5 or step out before initiative 9–10.
- If he has not acted and Hurry up! is in hand, a slow green (initiative 2–6) can be pulled to 11. Do not rely on moving after his ally.
- Journey immunity: if he swaps with you, you cannot be targeted for the rest of the turn, and you return at end of turn. Use the turn to act from his old space if your card still does something there. Do not expect to be hit, and do not expect to stay.
- Killing his ally, or swapping the ally off the target, turns off the ranged half of primary red.
- He is a support. A wave he is forced to answer alone, with attack 2, is a wave his team is not playing.

## 5. Common mistakes

- Playing gold, killing the minion, and expecting the 3-space leave. The leave requires a survivor.
- Playing Monkey Trick at radius 1 with one friendly in radius. The primary does not start.
- Journey on the enemy a teammate was about to attack. That teammate loses the target for the turn.
- Journey after the enemy has already acted, then standing in their space with defense 1 until end of turn. He is not immune.
- Hurry up! on an ally while the ultimate is unlocked, which steals the ally's action, unless Hanu is going to spend it better on purpose.
- Treating This Way as "Hanu picks the distance". The ally's seat picks the number. Hanu only picks the direction, and only full-distance lines exist.
- Holding silver "for later" on a turn where the revealed order is already won by one bump to 11, or playing it when every unresolved card is already 11–13 and the bump does not change who acts first.
- Upgrading attack items and then swinging gold at heroes.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`hanu-gold`] WHEN an enemy unit is adjacent AND a straight 3-space line from his space ends on an empty space not adjacent to an enemy hero AND the attack will not defeat the target (attack total ≤ the defense they can show, or the target is a hero with a card in hand) THEN play gold and take the line that ends farthest from enemy heroes. WEIGHT 8 for the leave plus whatever the attack is worth. If the attack will defeat the target, play gold only for the defeat, and do not count the leave. HContext. SOURCE rulings/hanu.md 1; [ADVICE] "as long as you have an escape route".

2. `PLAY_CARD` [`hanu-gold`] WHEN the adjacent unit is an enemy minion, the lane margin is even or he is behind, AND he is fine staying (no enemy hero can attack his space after initiative 13) THEN play gold for the minion. WEIGHT 15 if that minion is the lane, else 4. SOURCE [REASONED].

3. `CHOOSE_CARD` [`hanu-silver`] WHEN a friendly hero in range 4 has an unresolved attack whose printed initiative is ≤ 10 AND an enemy who would walk out of that attack has an unresolved card at initiative ≥ 11 THEN set the ally's attack to 11. WEIGHT 12 if the attack then reaches a hero, 15 if it then reaches the lane minion. Items on that ally still add, so compare 11 + their initiative items with the enemy's card. HContext. SOURCE [ADVICE]; [BGG 3487749]; the mechanism that remains from [BGG 2894481].

4. `CHOOSE_CARD` [`hanu-silver`] WHEN the ultimate is unlocked AND an enemy in range 4 has an unresolved card THEN target that enemy, not an ally, and on their action spend it: movement or fast travel away from his team, or an attack on a minion that is not the lane-decider, never an attack on his ally. WEIGHT 20. Do this over rule 3 unless the ally's attack is itself a hero kill. hero-state (ultimate unlocked is a level flag, HContext once levels are public). SOURCE card `hanu-purple-4`; rulings/hanu.md 4; [BGG 2894481] on the ultimate, which matches.

5. `CHOOSE_CARD` [`hanu-silver`] WHEN an enemy's unresolved card has printed initiative ≥ 12 AND an ally has an unresolved card at 12 or higher that should act first THEN set the enemy to 11. WEIGHT 8. WHEN an enemy green or slow skill (printed ≤ 6) would act after his ally's attack and walk out or save the target THEN set that enemy card to 11 so they act first. WEIGHT 10. HContext. SOURCE [BGG 3487749] last paragraph; [REASONED].

6. `PLAY_CARD` [`hanu-red-1`, `hanu-red-2a`, `hanu-red-3a`] WHEN a hero is in red range, adjacent to his ally, and (on `hanu-red-3a`) not adjacent to Hanu, AND his attack total beats a defense they have left or they have 0 cards THEN the ranged option, and on 3a also the adjacent option if a second unit is adjacent. WEIGHT a hero kill if it kills, else 6 for the hit. If the only legal ranged target is a full-hand tank and he has no ally there, do not play the card for the primary; use movement 5. HContext. SOURCE card text; [BGG 3487749].

7. `CHOOSE_OPTION` [`hanu-red-2b`, `hanu-red-3b`] WHEN a minion in range is adjacent to his ally AND that minion is the one that swings the lane THEN the minion option. On 3b, also take an adjacent enemy unit if attack 5 kills it or it is a second minion. WEIGHT 15 for the lane minion. Prefer this red over gold when both can take the minion and gold's attack 2 is not needed for the timing. SOURCE card text.

8. `PLAY_CARD` [`hanu-blue-1`, `hanu-blue-2a`, `hanu-blue-3a`] WHEN an enemy hero in radius has not resolved this turn AND their card is an attack or a minion-take AND the space Hanu would land on is not adjacent to a second enemy who acts after him THEN swap. WEIGHT 12 if it ruins the attack or the minion, else 0. Do not swap when an ally still to act can attack that hero (immunity). After the swap, do not target them. HContext. SOURCE rulings/hanu.md 8–9; the surviving half of [BGG 2894481] "swap them off the minion".

9. `CHOOSE_SPACE` [`hanu-blue-2b`, `hanu-blue-3b`] WHEN Hanu is asked for the direction THEN the line where both complete the move AND the ally ends adjacent to their revealed attack's target, or both end with no enemy adjacent. If no such line, decline in the sense of taking no direction only when the engine offers a decline; otherwise the least-exposed legal line. WEIGHT 10. The distance is not his decision. HContext. SOURCE rulings/hanu.md 10; [ADVICE] on setting allies up.

10. `CHOOSE_UNIT` [`hanu-green-2b`, `hanu-green-3b`] WHEN it is his action at initiative 1–2 AND an enemy hero in radius is standing on a minion or adjacent to his ally who has already acted and hit THEN swap with that hero. On 3b, the extra 1-space move goes off the enemy, not back onto them. WEIGHT 12. Do not swap an enemy into a space his own minion wave needs. SOURCE card text; [BGG 3487749].

11. `CHOOSE_UNIT` [`hanu-green-1`, `hanu-green-2a`, `hanu-green-3a`] WHEN two friendly units (not him) are in radius THEN swap the ally hero with the friendly minion or the other body that puts the ally adjacent to their target, or that pulls the ally out of an enemy's range. On 3a, the extra 1-space is part of the same intent. WEIGHT 8. If fewer than two friendlies are in radius, the card is movement only: do not play it for the swap. SOURCE rulings/hanu.md 7.

12. `DEFEND` [any] WHEN the attack total is greater than every remaining card's defense except one red or blue THEN spend the card that still beats it, and if none beats it, decline only when the death is cheaper than spending the last team-setup card and a teammate can still win the lane. Default: spend the cheapest card that succeeds. Never defend a basic attack of 2 or less with `hanu-red-3b` if a defense-1 card plus a friendly minion modifier succeeds. WEIGHT of a failed defense is the hero kill. HContext. SOURCE the defense icons; [REASONED].

13. `PLAY_CARD` [any] WHEN he has 1 card in hand and an enemy attack can reach him after he would act THEN do not play the last defense-4-or-better card as a movement. Hold is not available if he must play; then play the low-defense card (gold, silver, green) and keep the red. WEIGHT 6. SOURCE [REASONED] from defense icons, not from the 1st-printing discard lock.

14. `RESPAWN` [`hanu`] WHEN respawning THEN a space in the battle zone within red range of an ally, not adjacent to two enemies. WEIGHT 6 versus a spawn on the far side. HContext. SOURCE role.

15. `UPGRADE_CARD` / `KEEP_UPGRADE` WHEN the ally is melee THEN keep the `a` red and tuck `b` if its item is defense and he has no defense item yet; see §3 for the rest. WEIGHT 3. SOURCE [BGG 3487749]; [REASONED].

### evalTerms

Hanu has no persistent token. Terms are small and about the turn:

- An enemy hero currently immune from his journey effect, while an ally still has an unresolved attack that could have hit them: −8 (he turned off his own team).
- The same immunity while that enemy's unresolved attack can no longer reach his ally from the swapped space: +10.
- No token, counter, or piece of his own to price. Return 0 the rest of the time.

### threatAdjust

When the enemy is Hanu:

- +6 on every enemy-hero space that is adjacent to a Hanu ally and inside his current red range (3 or 4). That is the real ranged threat. Spaces in range with no ally adjacent get +0 from this hero beyond a normal adjacent gold.
- +4 on his adjacent spaces when gold is still in hand and a straight 3-line leaves the space, but only as "he can tap and leave", not as a kill. Attack 2 does not justify a kill-threat.
- +8 on an ally's current space when Hurry up! is in his hand and that ally's likely attack, bumped to 11, would then reach the space. This is the threat of the ally, tagged because Hanu creates it.
- Journey does not make his destination safe. He is not immune. Do not reduce threat on the space he swaps into.

## 7. Sources

- Catalog `hanu.json` advice (3 sentences). Official.
- `core/game/rulings/hanu.md`.
- [BGG 3487749] Interesting hero combos, posts 2025-03-30 and 2026-07-21, community. Hanu with Razzle, Takahide, Rowenna, Xargatha. 2nd-printing era.
- [BGG 2894481] Hanu spotlight, 2022-07-04, community. 1st printing. Used only for: silver speeds an ally's red or an enemy's slow card; the ultimate steals the action; he is a support who should not front-line. Rejected: empty-discard lock, gold cannot kill heroes or heavies, Killing Joke green, both-heroes immunity, alt-red "any unit in range", "full distance" clause on silver.
- [BGG 2431430] Kickstarter spotlight index, 2020, designer campaign text. Roles only (controller, support). The card list there is pre-revision.
- Gamefound Renowned update 8 (https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/8): "strongest supports and initiators", high initiative, high mobility, usefulness drops when pressured. The sentence "some abilities stop working if he has any cards in the discard" matches the 1st printing and is rejected for 2nd.
- No 2nd-printing Hanu spotlight. No Reddit thread found.

## Implementation notes

`HanuHeuristics.kt`, checked against `HanuHero.kt` (2026-11-21).

- Rule 3/4/5: Hurry up! is a `CHOOSE_UNIT` on the hero first ("Set the Initiative of a hero's unresolved card to 11"), then a `CHOOSE_CARD` only when that hero has two unresolved cards. The rules are scored on the unit choice, reading that hero's faceup unresolved card.
- Rule 6/7: the red's far bullet is legal only on a hero (red 1/2a/3a) or minion (red 2b/3b) beside a friendly hero, and red 3a's also not adjacent to Hanu. The general book reads printed range, so when neither bullet has a target the playbook cancels that strike estimate instead of a flat −14 (the red is still movement 5 and defense 4–6).
- Rule 1: gold's leave needs three empty spaces in a straight line (`MoveSpec(straightLine = true)`), and only fires when the target survives, so it is scored on a hero with a card in hand.
- Rules 9–11 (This Way direction, green swaps) are opaque choices for the general book; the one-ply lookahead plays them. Rule 12, 13 and 15 are left to the general defense and upgrade scoring.
- `evalTerms`: none. Journey's immunity cuts both ways and the lookahead already sees it.
- Measured (ablation, strength 8, 96 games): `laneWeight` 0.50 from §1 scored 45.8%; 0.85 scored 46.9%, and 49.0% once the dead-strike correction kept the red's movement value. `laneWeight` scales the general book's pull into the Battle Zone, so the research value halved it.
