# Misa (`misa`) — heuristic playbook

2nd printing. Icons from `misa.json`. The 1st-printing spotlight [BGG 2890793] (2022-06-28, community, author Zith; designer nichik replies on a rules point) describes a different hero: gold did not defeat minions and granted melee immunity, silver was a taunt, primary green repeated the next action, alt green was a move-through discard, and the ultimate turned "next turn" into "this round". None of that is on the current cards. Gamefound's Devoted overview repeats the old green split and says gold cannot defeat minions. The catalog disagrees: gold's second option defeats an adjacent minion. The card wins. What remains true, and what official advice still says: she has one attack card, and it has to count; silver is mobility and a retrieve; blue and green pick the job; Slash and Dash is the hero-hunt and is optional if the team is on the wave; alt green is strong and awkward.

## 1. Identity

| Field | Value |
|---|---|
| Role | `Fighter` on red A (melee), `Ranged` on red B. The style field is one value: `Fighter`, and red B lowers `idealDistance` rules rather than the role. |
| Complexity | 2 |
| `idealDistance` | 4 for gold's placement (exact maximum range), then 1 if she kept the sword or 2 if she kept the bow. |
| Lane vs kill | `laneWeight` 0.55 on blue A and red A (placement and the sword take minions). 0.25 on blue B (Dash and Slash / Death from Above discard a hero she moves through). |
| `aggression` | 0.55. One attack a round. She spends it when it kills or takes the lane, and places herself the other turns. |
| `keepDefender` | true. The sword's defense icons are 6–7 and the bow's are 4. Blue prints defense 5–6. Gold is 2 and silver is 3. After she connects with the sword she gains +2 / +3 / +5 defense **this turn**, so the dive is safer against anyone who acts later (rulings/misa.md 4). |
| `upgradeOrder` | §3 |

Profile bars (not added in play): Attack 6→6, Defense 5→5, Initiative 7→7, Movement 1→8. The movement bar is a rating, not a distance. Printed movement icons are 1, 2 or 3. `misa-red-3a` is her movement item and adds +1 to those icons when she takes the movement action. It does not lengthen a dash or the green step; those distances are card text. She crosses the board by placing herself. Range items do lengthen gold and the bow, because those are range icons.

## 2. Card-by-card

`misa-gold` Watch How I Soar. Initiative 12, defense 2, movement 1, range 4, skill. Choose one: place herself on an empty space at **exact** range 4, or defeat an adjacent enemy minion (coins, heavy immunity applies). Not an attack. Play the place to enter the fight or to leave it, the defeat when she is already adjacent to the minion that matters. With the ultimate she may do both, in either order, if the first can start. Defense 2: a poor shield.

`misa-silver` Swoop In. Initiative 6, defense 3, radius 3, skill. Place herself on an empty space in radius adjacent to **two or more** enemy units (presence, immune units count). If she does, she may retrieve one of her discarded cards. Official: mobility and self-healing. Play when a space in radius 3 touches two enemies, a hero and a minion included, and she wants to be there or she has a card to retrieve. Initiative 6 is late. It is not a taunt. Do not play it with no such space; the primary does not start.

`misa-red-1` Challenge Accepted. Initiative 9, attack 5, defense 6, movement 3. Adjacent unit. After a connecting attack, +2 defense this turn. The one attack. Play when adjacent to a hero it can kill or to the lane minion, and prefer a turn where someone acts after her so the +2 matters.

`misa-red-2a` Matter of Honor. Attack 6, defense 7, movement 3, initiative 9, item **initiative**. +3 defense after a hit.

`misa-red-3a` Worthy Opponent. Attack 6, defense 7, movement 3, initiative 10, item **movement**. +5 defense after a hit. The movement item is the difference between walking and not.

`misa-red-2b` Power Shot. Initiative 9, attack 3, defense 4, movement 3, range 2, ranged, item **defense**. A unit in range. After a connecting attack, if the target **was** at maximum range, she may move it 1 space farther from her.

`misa-red-3b` Thunder Shot. Attack 3, defense 4, range 2, initiative 10, item **defense**. After the attack, if the target is not adjacent, she may move it 1 farther. Attack 3 does not kill a healthy hero. This is the wave and the shove, not the sword.

`misa-green-1` Focus. Initiative 3, defense 3, movement 2, skill. Next turn, before a primary action, she may move 1 (exactly 1, or none). The setup. Play the turn before the sword, so the sword starts with a step.

`misa-green-2a` Discipline. Next turn, before a primary, move up to 2. Initiative 2, item **initiative**.

`misa-green-3a` Mastery. Next turn, before a primary, move up to 3. Defense 4, item **initiative**. "Next turn" does not cross the round (catalog pattern; rulings/misa.md 6). Do not play it on turn 4.

`misa-green-2b` Living Tornado. Initiative 2, defense 3, movement 2, radius 3, item **attack**. Swap two units at **exact** maximum radius. Not herself. Both must be at distance 3 (plus radius items) and swappable. Official: strong and tricky.

`misa-green-3b` Storm Spirit. Swap two units in radius that are at **equal** distance from her. Defense 4, item **attack**. A hero and a minion on the same ring trade places.

`misa-blue-1` Sudden Breeze. Initiative 10, defense 5, movement 1. Move up to 3 in a straight line, ignoring obstacles. She may place one enemy unit she moved through onto a space adjacent to her.

`misa-blue-2a` Gust of Wind. Up to 4, defense 6, item **attack**.

`misa-blue-3a` Crushing Squall. Up to 5, defense 6, initiative 11, item **radius**. The radius item grows silver and the tornado.

`misa-blue-2b` Dash and Slash. Up to 4, ignoring obstacles. An enemy hero she moved through discards, if able. Defense 6, item **defense**. Official: skip this branch if the team is on the wave rather than on heroes.

`misa-blue-3b` Death from Above. Up to 5. The same discard. Item **range**. The range item makes gold's placement and the bow reach farther. The name is the old green's kill; the current card only discards.

`misa-purple-4` Power Overwhelming. Whenever she chooses one, she may choose two different options, in any order. After she places herself (her own placement texts, and Fast Travel; not a swap and not a respawn), an adjacent enemy hero discards, if able (rulings/misa.md 9–10). Gold can then place at range 4 **and** defeat the adjacent minion, if she orders them so the first can start.

`misa-handicap` Leaf on the Wind. Initiative 11, defense 1, range 4. Place herself at exact maximum range. No minion option. A pure reposition, and a bad shield.

## 3. Upgrade path

1. **Say what the team is doing before the first upgrade.** Wave: red B is allowed and blue B is not. Heroes: red A and blue B. Official advice is this split.
2. **Green A early if the sword needs a step.** Focus into Discipline into Mastery is how movement 1 still reaches. Play it the turn before the attack, never on turn 4. Two initiative items on a hero at initiative 7 will make the sword go before their escape; take one, not three. Tuck the extra initiative only when a tie at 9 or 10 is the kill.
3. **Green B when the board is ringed.** Living Tornado swaps the two units at exact radius 3. Storm Spirit swaps any equal pair inside radius 3. Take it when minions and heroes share a ring (a battle zone full of bodies). Skip it on an empty wing. The attack item helps the sword more than the bow.
4. **Blue A** (place a unit she flew through beside her) is the wave and the setup. Blue B is the discard. Blue A's defense icons are the same 5–6. Tier 3 A gives radius; tier 3 B gives range. If she kept the bow, the range item is the one that matters. If she kept the sword, radius grows the swoop and the tornado.
5. **Red A** is the kill: attack 5 then 6, defense 6 then 7, and at tier 3 the movement item plus +5 defense after she hits. **Red B** attack 3 at range 2 shoves the target 1 farther when it was at max range. Take B only for the wave, or when she must not stand adjacent. Default A.
6. Ultimate: once she has it, gold is two options and every placement is a discard. That is when blue B becomes less necessary, because the placement itself strips a card.

## 4. Synergies and counters

- A lane partner who does not need her one attack. She defeats one adjacent minion with gold and can place a second body with blue A. She should not be the only hero who can take a heavy.
- An ally who kills what she has stripped. Blue B's discard, and the ultimate's discard on placement, are the setup. She arrives, they act after initiative 10.
- Hanu's ranged red wants an ally adjacent to the victim. She is often that ally, because she places herself there. His This Way also solves her movement 1.

She fears: spending the sword on a minion and having nothing for the hero. Being kited, because movement on the card is 1–3 and the green step only happens next turn, before the primary, and only if she played green. Reposition effects on her or on her target (the 1st-printing comment about melee still matches the sword). A team that never stands two units together, so silver never places.

Against her:

- Do not stand at exact range 4 from her on a turn gold is in hand if that space is where you cannot afford her to be. She will be there at initiative 12, before most reds.
- The sword is one card. If she has played it, she does not have another attack. Take the minion she was saving it for. If she has not played it, do not stand adjacent at initiative 9 or lower unless you can defend attack 5–6 or you act first and leave.
- After she hits with the sword she is harder to crack **this turn** (+2 / +3 / +5). Hit her before she attacks, or next turn.
- Silver needs two enemy units. Do not cluster a hero and a minion inside her radius 3 if you cannot answer her arrival.
- Blue B and the ultimate discard a hero she moves through or lands next to. A straight line through you into her team is the danger, not her attack stat.
- Alt green swaps two units at a fixed distance. Stand off her radius-3 ring if Tornado is the card, and do not share a distance with your own minion if Storm Spirit is the card.

## 5. Common mistakes

- Playing the sword on a minion when a hero in reach would die to attack 5 or 6, or the reverse on a lane turn: playing it on a hero and giving up the last minion.
- Playing Focus / Discipline / Mastery on turn 4. There is no next turn.
- Expecting the green step in the middle of the action. It happens before the primary, next turn, once.
- Swoop In next to one enemy. The space must touch two.
- Gold "anywhere up to range 4". It is exact.
- Tornado on units at range 2. They are not at maximum radius.
- Treating Dash and Slash as a defeat. It discards, if able. A hero with no card is not defeated by it.
- Defending with the sword after she has already attacked and gained the defense bonus. The bonus applies to whatever she defends with; she can spend a blue and keep the sword's attack for nothing, but if the sword is the only card that beats the total she has to spend it. Do the arithmetic.
- Using the 1st-printing taunt, the repeat-action green, or "gold cannot take a minion".

## 6. HEURISTIC RULES

"Sword" means `misa-red-1`, `misa-red-2a`, `misa-red-3a`. "Bow" means `misa-red-2b`, `misa-red-3b`. "Pending step" means Focus, Discipline or Mastery was played last turn and this is that next turn.

1. `PLAY_CARD` [sword] WHEN she is adjacent to an enemy hero whose remaining defense cannot beat attack 5 or 6, or who has 0 cards, AND either the lane is stable or the hero kill is worth more than the minion THEN play the sword on that hero. WEIGHT a hero kill. After it connects, she is not a priority target for enemies who act later this turn. HContext. SOURCE [ADVICE] "one attack card"; rulings/misa.md 4.

2. `PLAY_CARD` [sword or bow] WHEN the lane minion is the one that swings an even lane AND she can reach it (adjacent, or range 2 on the bow, or the pending step reaches it) AND no rule-1 hero exists THEN the minion. WEIGHT 15. SOURCE [REASONED].

3. `PLAY_CARD` [`misa-green-1`, `misa-green-2a`, `misa-green-3a`] WHEN it is turn 1, 2, or 3 AND she is 1, 2, or 3 spaces short of the space the sword or the blue wants next turn AND she will still have that attack or that blue THEN play the green. WEIGHT 5 for a step that makes a kill or a lane minion legal next turn, 0 on turn 4. Do not play it and the sword on the same turn and expect the step. SOURCE card text; rulings/misa.md 6.

4. `CHOOSE_SPACE` [the pending step] WHEN this turn's primary is the sword or a blue dash THEN move the 1 / up to 2 / up to 3 onto the space the primary needs, before the primary is chosen. A primary that was illegal and becomes legal after the step is a legal play. WEIGHT 8. SOURCE rulings/misa.md 6.

5. `PLAY_CARD` [`misa-gold`] WHEN a space at exact range 4 (plus range items) is adjacent to the hero rule 1 wants, or inside the battle zone when she is outside it, AND she does not already hold the minion she must defeat this turn THEN place. WEIGHT 8. WHEN an enemy minion is adjacent AND it is the lane minion AND the place does not set up a kill THEN defeat the minion. WEIGHT 15. With the ultimate, do the place first if she is not yet adjacent, then the defeat if the new space is adjacent to a minion; or the defeat first if she is already adjacent and the place still has a space at range 4 from the start. The engine orders them as chosen. SOURCE card text; rulings/misa.md 1–2 and 9.

6. `PLAY_CARD` [`misa-silver`] WHEN a space in radius 3 is adjacent to two or more enemy units AND (she has a card in the discard OR that space is the sword's space) THEN place there and retrieve the highest-defense discarded card if one exists. WEIGHT 6 plus 2.5 for the retrieve. Initiative 6: this is a late arrival, so it is the right card when the sword would have been kited at initiative 9. SOURCE [ADVICE].

7. `CHOOSE_SPACE` [`misa-blue-1`, `misa-blue-2a`, `misa-blue-3a`] WHEN a straight line of up to 3 / 4 / 5 passes through an enemy hero or a minion and ends adjacent to where she wants to be THEN take it and place the passed unit adjacent to her, preferring a hero her sword can reach or a minion her ally can reach. WEIGHT 10 for delivering a hero, 15 if the placed minion is then defeated by someone who acts later. SOURCE card text.

8. `PLAY_CARD` [`misa-blue-2b`, `misa-blue-3b`] WHEN a straight line passes through an enemy hero who has a card AND the destination is safe or is the sword's space for a later turn THEN play it. WEIGHT 2.5, or 8 if a teammate acts after initiative 10 and can kill the emptied hero. Do not play it for the wave. If the team is behind on the lane and she has gold or the sword for the minion, play those instead. SOURCE [ADVICE].

9. `CHOOSE_UNIT` [`misa-green-2b`] WHEN two units sit at exact maximum radius AND swapping them puts an enemy hero adjacent to her or to her ally, or puts the lane minion adjacent to the hero who can take it, or swaps an enemy hero off that minion THEN swap that pair. WEIGHT 12. If no pair is at maximum radius, do not play the card for the primary. SOURCE [ADVICE] "tricky to land".

10. `CHOOSE_UNIT` [`misa-green-3b`] WHEN two units in radius share a distance AND the swap does the same job as rule 9 THEN swap them. Prefer a hero and a minion on the same ring. WEIGHT 12. SOURCE card text.

11. `CHOOSE_SPACE` [bow after-attack move] WHEN the target is a hero who survived AND moving them 1 farther takes them out of their ally's fight or off a minion THEN move them. On Power Shot only if they were at maximum range when hit. On Thunder Shot if they are not adjacent. WEIGHT 4. Do not shove a hero into your own team's threat by accident; "farther from her" can still be into an ally, and that is legal and often the point. WEIGHT 8 if the farther space is adjacent to her ally who has not acted. SOURCE rulings/misa.md 5.

12. `CHOOSE_UNIT` [`misa-purple-4` placement discard] WHEN she has just placed herself AND an enemy hero is adjacent THEN that hero discards. If two are adjacent, the one with fewer cards, so the next hit kills. WEIGHT 2.5, or a kill's setup at 8. This includes Fast Travel. It does not include a swap. SOURCE rulings/misa.md 10.

13. `DEFEND` [any] WHEN she has connected with the sword this turn THEN the defense total includes +2 / +3 / +5, so a blue or a green may now beat an attack that would have killed her, and the sword can stay in hand only if it has not already been played. If the sword is still in hand and is the attack she needs next, defend with a blue (defense 5–6 plus the bonus if the bonus already started — it has not, if she has not hit). Spend the lowest card that beats the total. WEIGHT of spending the unplayed sword on a non-lethal hit: −10. SOURCE rulings/misa.md 4.

14. `RESPAWN` [`misa`] WHEN respawning THEN a space at range 4 from an enemy minion in the battle zone, so the next gold places her onto it, not a space already in the zone but out of every range. WEIGHT 6. SOURCE movement 1.

15. `UPGRADE_CARD` WHEN the team is behind on the lane and ahead on lives THEN keep red B and blue A, tuck defense and radius. WHEN a hero on the enemy team is worth more than the lane (one life left, or a Wish, or a farmer) THEN keep red A and blue B. Green A if she is not adjacent to the fight at the end of rounds; green B if three or more units tend to share her rings. WEIGHT 3. SOURCE [ADVICE].

### evalTerms

- Focus / Discipline / Mastery pending for the coming turn: +3 / +5 / +7 if a primary in hand becomes legal after that move, else +1.
- No token of her own.
- The this-turn defense bonus is not a position term. The combat preview already sees it. Do not add it again.

### threatAdjust

When the enemy is Misa:

- +6 on every empty space at exact gold range (4 + range items) while gold is in hand. She can be there at initiative 12. Not +6 on the spaces in between.
- +10 on spaces adjacent to her while the sword is in hand. Attack 5 or 6. 0 from the sword at range 2 or more.
- +3 on spaces at range 1–2 while the bow is in hand. Attack 3, plus a one-space shove if they are not adjacent after the hit.
- +4 on a straight line of 3, 4, or 5 through a hero while the matching blue is in hand. Blue B is a discard on the hero she passes; blue A is a place beside her.
- While a green A is already faceup from last turn, extend the adjacent threat by 1, 2, or 3 along her movement. She steps before the sword.
- Silver is not a taunt. Do not force attacks onto her. The danger is the space next to two of your units, inside radius 3.

## 7. Sources

- Catalog `misa.json` advice (5 sentences). goa2-faq has no `misa.json` FAQ (rulings/misa.md).
- `core/game/rulings/misa.md`.
- [BGG 2890793] Misa spotlight, 2022-06-28, community (Zith), with nichik (designer) on declining a defense. 1st printing. Kept: one attack must count; she telegraphs; melee is kited by initiative; reposition answers her. Rejected: the printed texts of gold, silver, both greens, alt blue's old identity, and the ultimate. Designer reply is about the rules of declining a defense, not about her current red.
- Gamefound Devoted update 6 (https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/6): one attack, gold as mobility. The claims that gold cannot defeat a minion and that one green repeats the action are the old kit and are rejected.
- [BGG 2431430] campaign index: "mobility disabler", one attack. Roles only.
- No 2nd-printing spotlight. No Reddit thread found.
