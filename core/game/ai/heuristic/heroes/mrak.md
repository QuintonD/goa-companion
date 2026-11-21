# Mrak (`mrak`) — heuristic playbook

2nd printing. Icons from `mrak.json`. The spotlight [BGG 3449043] (2025-01-26, community, a few testers read it) matches the current cards, including the rock supply of 3 and the note that a later gold picks those rocks back up. Rulings use that supply. Rocks are removed at end of round. They are obstacles. "Adjacent to terrain" includes printed terrain and the water around the board (rulings/mrak.md 2).

## 1. Identity

| Field | Value |
|---|---|
| Role | `Tank` (the job is `SetUp`: he makes the ground worse and then hits) |
| Complexity | 3 |
| `idealDistance` | 1 for the gold and the push, but he wants the enemy adjacent to terrain or a rock more than he wants them adjacent to him. Seismic is radius 3, then 4. |
| Lane vs kill | `laneWeight` 0.40. He is a poor pure farmer. His wave presence is denying the enemy's take [BGG 3449043]. |
| `aggression` | 0.35. The action should come to the middle zone. He dives only on the carapace branch. |
| `keepDefender` | true on primary red (defense icons 7–8). On alt red the played card **is** a defense: once `mrak-red-2b` or `mrak-red-3b` is resolved and still on its turn slot, he may discard it as if it were in hand, and he may discard it instead of a hand card. That is not "keep it in hand". It is "play it, then it covers him". |
| `upgradeOrder` | §3 |

Profile bars (not added in play): Attack 8→8, Defense 8→8, Initiative 1→1, Movement 2→4. They describe him; they are not added to icons. His cards print attack 6–7 and defense 6–8. Gold is attack 4, initiative 11. Red primary is initiative 7–8. Green is 5–6. Blue is 8–9. Movement icons are 1–4. He acts late on the red and the green. That is a feature on the carapace branch: gold at 11 hunts someone who already spent their fast card [BGG 3449043].

## 2. Card-by-card

`mrak-gold` Fissure. Initiative 11, attack 4, defense 3, movement 1. Adjacent unit. After a connecting attack, a rock on each of the first three empty spaces in the straight line from him through the target. Occupied spaces and terrain are skipped. A defeated target's space is empty and takes the first rock. The line stops at the board edge, so he may place fewer than three (rulings/mrak.md 3). Attack 4 kills most silvers' defense and is a turn-4 finisher if he is in position [BGG 3449043]. The rocks block his team too. Check the line before he swings. Later in the round this card picks rocks back out of the supply of 3, which removes an earlier wall.

`mrak-silver` Stone Grip. Initiative 12, defense 4, range 3, skill. Exactly 3 rocks on empty spaces adjacent to one enemy hero in range, farthest from him first. He picks the hero; the distance rule is on the tokens (thread reply, 2025-01-28). A hero with fewer than 3 empty adjacent spaces cannot be chosen. If none can, the primary does not start. Play to close an escape or to seal someone who already has 3 obstacles around them (the other three spaces). It does not move him. With movement 2, standing still is how he falls off the wave [BGG 3449043]. Defense 4.

`mrak-red-1` Seismic Slam. Initiative 7, attack 6, defense 7, movement 3, radius 3, skill, with a secondary non-ranged attack. An enemy hero in radius who is adjacent to terrain or a rock discards a card, or is defeated. The attack action is the stick. Two different primaries: the skill does not need him adjacent, and it defeats a hero with no cards; the attack is how a body at attack 6 dies.

`mrak-red-2a` Seismic Assault. The same skill, attack 7, defense 8, radius 4, item **initiative**. No repeat yet.

`mrak-red-3a` Epicenter. The same, attack 7, defense 8, radius 4, initiative 8, item **range**, and he may repeat once on a different target. The range item grows silver and the greens.

`mrak-red-2b` Stone Carapace. Initiative 7, attack 7, defense 6, movement 4, primary **movement**, item **defense**. This round, while this card sits on the turn slot: if he would discard a card from hand, he may discard this card instead; and he may discard this card to defend, as if it were in hand (defense 6, no text). Either discard ends the effect. Play it early to walk 4 and to arm the extra defense. It is not a skill.

`mrak-red-3b` Rock Solid. The same, plus he may retrieve a discarded card before the move. Initiative 8, item **defense**. The retrieve is a card (~2.5) on the way in.

`mrak-green-1` Treacherous Ground. Initiative 6, defense 4, movement 2, range 3. He may move a unit in range (not himself) 1 space, to a space adjacent to terrain or a rock. Friend or enemy. A deny, a rescue, or the setup for seismic.

`mrak-green-2a` Rockslide. Range 4, item **attack**.

`mrak-green-3a` Avalanche. Range 4, defense 5, item **radius**, and he may repeat once. The repeat is not offered if he declined the first move (rulings/mrak.md 5).

`mrak-green-2b` Rolling Stone. Initiative 5, defense 4, movement 2, item **initiative**. Move any number of spaces in a straight line, ignoring obstacles, with at most one empty space between start and destination. Terrain, units, tokens and rocks are not empty. The start and the end do not count. At least 1 space. This is how he rides a fissure: three rocks in a line are three non-empty spaces, so the slide along them is long [BGG 3449043].

`mrak-green-3b` Strolling Stone. At most **two** empty spaces in between. Defense 5, item **initiative**. The mobility branch official advice names.

`mrak-blue-1` Boulder Rush. Initiative 8, defense 6, movement 2. Push an adjacent token or enemy unit 1 or 2, ignoring obstacles, then he may move up to 2 in that same direction, ignoring obstacles.

`mrak-blue-2a` Boulder Blitz. 1, 2 or 3, and he may follow up to 3. Defense 7, item **defense**.

`mrak-blue-3a` Boulderdozer. 1 to 4, follow up to 4, item **movement**. The movement item adds +1 to the movement icon (printed 2) when he takes the movement action. The push distance and the follow-up are card text, so the item does not lengthen them.

`mrak-blue-2b` Stomping Step. Initiative 9, defense 7, movement 2, radius 4, item **attack**. Move a unit in radius that is already adjacent to terrain or a rock, exactly 1, then a rock in the space it left. He can pull a minion off a take or put an ally onto one [BGG 3449043].

`mrak-blue-3b` Ground Shaker. The same, and repeat once on a different unit. Item **attack**.

`mrak-purple-4` Rock and a Hard Place. Once per turn, after he places one or more rocks adjacent to one or more enemy heroes, each of those heroes discards, if able. Not optional (rulings/mrak.md 10; the community engine asks, this one does not). Fissure's three rocks are one placement. Stone Grip's three are one placement. One Stomping rock is one placement. Immune heroes do not count. The first placement each turn that touches an enemy hero spends it.

`mrak-handicap` Lesser Fissure. Initiative 10, attack 3, defense 2, movement 1. A hero adjacent, not a minion; the same three-rock line. Attack 3. Use it for the wall and a turn-4 tag, not for a minion.

## 3. Upgrade path

Official advice: area denial; alt red is the one that makes him hard to kill; alt green is the one that moves.

1. **Where is the fight.** If it will stay in the middle, among printed rocks and corridors, keep primary green and primary red and stand there [BGG 3449043]. If the wave will go to a beach or a throne, he cannot walk there on movement 2. Keep `mrak-green-2b` / `3b` first.
2. **Red.** `mrak-red-2a` / `3a` when discards are how his team kills and he is not the one being focused. Defense icons 8, radius 4, a second target at tier 3. `mrak-red-2b` / `3b` when he is the body they will hit, or when he must hunt: movement 4, an extra defense from the slot, a defense item, and at tier 3 a retrieve. Default 2b if the enemy team is the one diving, 2a if he is the one zoning a wounded hero. Tuck defense from 2b whenever he kept 2a and has no defense item; the item still raises his other defense icons.
3. **Blue.** `mrak-blue-2a` / `3a` to shove and follow, the way he crosses a screen of his own rocks. The defense item on 2a is the durable one. `mrak-blue-2b` / `3b` when a unit is already on terrain and he wants them off it, or he wants a rock where they stood. Attack items help gold's 4 and the secondary attack. Default 2a. The spotlight's warning: primary blue gives up a clean movement card and he stands more.
4. **Initiative items** (on green B and red A) make his already-slow cards faster than enemy greens, so they reposition after him. Take one only to win a tie on the seismic. Taking none is the stalk [BGG 3449043].
5. **Ultimate** turns the first rock placement each turn that touches a hero into a discard. Stone Grip then discards the hero it walls, if they had a card. That is the reason to play silver once he has the ultimate, even though silver does not move him.

## 4. Synergies and counters

- Mortimer [BGG 3487749]. Zombies and rocks stack as obstacles. A hero walled by both cannot leave. Mortimer's push can shove a unit onto Mrak's rock. Do not build both walls on the ally's path.
- Brynn, or anyone who wants three obstacles around a hero. Stone Grip is three obstacles in one action if three spaces were empty; if they already had three, it seals the last three and they cannot move [BGG 3449043].
- A lane partner. He denies takes with Treacherous Ground. He does not replace a farmer.

He fears: heroes who ignore obstacles, place themselves at range, or swap (Misa's dash, Arien, Hanu, Whisper) [BGG 3449043]. They do not care about the wall. Forced movement on him, because movement 2 does not walk back from a bad zone. Discards while he is on primary red, because that branch has no retrieve. Carapace is the self-answer, and it ends when he uses it.

Against him (enemy playbook):

- Do not stand next to terrain or a rock inside radius 4 while seismic may be in hand, and never do it with 0 cards. That is a defeat, not a discard. Water at the board edge counts.
- Do not end a turn with only three or fewer empty adjacent spaces if Stone Grip is in hand and he is in range 3. He seals the rest, and with the ultimate he also discards you.
- His rocks are the supply of 3. A second placement picks the first wall up. You can bait the gold to delete his own screen [BGG 3449043].
- He is attack 6–7 in melee and defense 7–8 on the red. Do not brawl him with a smaller stick. Move him. A swap, a pull, a place, a push across a zone border costs him a whole action to return.
- Ignore-obstacle movement and placement do not care that the rock is there. Walk through or place past it.
- Carapace on the slot means the card you thought was a second hit is a defense 6 he can spend from the table, once. The third hit is the real one. After he has discarded it, the effect is gone.
- Standing next to terrain is safe against Swift and dangerous against Mrak. The spotlight says to remember the water.

## 5. Common mistakes

- Fissure through his own ally's path, or through the space he needed to slide along later.
- Stone Grip on a hero with two empty adjacent spaces. Nothing is placed. Count the spaces, and count rocks already there: the supply will be picked up first, and those spaces become empty (rulings/mrak.md 1).
- Playing silver in a side zone because someone is in range 3, then being out of the wave for two turns.
- Seismic on a hero who is not adjacent to terrain or a rock. The primary does not start. Use the attack, or move them there with green first.
- Rolling Stone through two empty spaces at tier 2. The limit is one. Units and rocks are not empty, so the line of rocks is the road.
- Declining Avalanche's first move and expecting the repeat.
- Treating Carapace as a card he holds in hand for the extra defense. The effect is on the resolved card. Unplayed, it is only movement 4 and defense 6 in hand, one card, not two.
- Using the carapace discard on the first small hit and then dying to the second with the effect already gone.
- Initiative items "because he is slow". They make enemy greens go after him. The stalk wants the opposite.
- Forgetting the board edge and the water. Both are terrain.

## 6. HEURISTIC RULES

Rock count is hero-state, supply 3. Terrain is map data, HContext once the map is known. "Seismic" means `mrak-red-1`, `mrak-red-2a`, `mrak-red-3a`.

1. `CHOOSE_SPACE` [respawn, and any move] WHEN the battle zone is the middle, with printed rocks, THEN stand there, adjacent to terrain if an enemy hero is in radius 4. WHEN the zone has moved to a beach or a throne AND he does not have Rolling Stone or a blue follow-up in hand THEN do not chase it with movement 2; deny from the nearest terrain. WEIGHT 6 for the middle, −8 for a deep beach with no slide. SOURCE [BGG 3449043].

2. `PLAY_CARD` [seismic skill] WHEN an enemy hero in radius is adjacent to terrain or a rock AND that hero has 0 cards THEN the skill, not the attack. It defeats them. WEIGHT a hero kill. On Epicenter, repeat on a second hero in the same state, else on a hero who still has a card if the discard matters. SOURCE card text; [BGG 3449043].

3. `CHOOSE_ACTION` [seismic] WHEN the hero is adjacent to him AND attack 6 or 7 beats the defense they have left AND they have a card (so the skill would only discard) THEN the secondary attack, not the skill, unless an ally who acts later needs the discard more than the damage. WEIGHT the kill over 2.5. WHEN he is not adjacent and the skill's condition holds THEN the skill. WEIGHT 2.5, or the kill if they have 0 cards. SOURCE [BGG 3449043] "the attack is the secondary".

4. `PLAY_CARD` [`mrak-green-1`, `mrak-green-2a`, `mrak-green-3a`] WHEN an enemy hero or a minion in range can move 1 onto terrain or a rock AND that stops a take or turns seismic on THEN move them. WHEN an ally in range can move 1 onto their target or off an enemy's range THEN move the ally. On Avalanche, repeat on a second unit. Decline only when every such move helps the enemy. WEIGHT 15 for a denied lane minion, 8 for making seismic lethal. SOURCE [BGG 3449043].

5. `PLAY_CARD` [`mrak-gold`] WHEN an adjacent unit should be hit (lane minion, or a hero who cannot defend attack 4) AND the three-rock line does not cross an ally's only path AND the line creates adjacency for a later seismic or a slide THEN attack along that line. WEIGHT 15 for the minion plus 4 if the rocks touch an enemy hero (the ultimate then discards them, once this turn). If the only line seals his ally, choose another facing or do not play gold. SOURCE [BGG 3449043]; rulings/mrak.md 3.

6. `PLAY_CARD` [`mrak-silver`] WHEN an enemy hero in range 3 has at least 3 empty adjacent spaces, after rocks are picked back up, AND closing the far side traps them against terrain or denies the escape from a kill AND he is not leaving the zone by standing still THEN Stone Grip. WEIGHT 10 if the hero ends with 0 empty adjacent spaces, 6 if it only blocks one exit. With the ultimate, add 2.5 because the placement discards them. Do not play it when the primary cannot place 3. SOURCE [BGG 3449043]; rulings/mrak.md 4.

7. `PLAY_CARD` [`mrak-red-2b`, `mrak-red-3b`] WHEN it is turn 1 or 2 AND he needs to be 3 or 4 spaces closer to the fight AND an attack is likely later this round THEN play carapace, walk the 4, and on Rock Solid retrieve a discarded defense first. The slot now covers one discard or one defense (defense 6 plus items). WEIGHT 8. Do not play it on turn 4 if gold is the finisher and nobody will attack him; the move is the value. SOURCE [BGG 3449043].

8. `DEFEND` [`mrak-red-2b`, `mrak-red-3b`] WHEN that card is on the turn slot AND the attack would otherwise discard a hand card or defeat him AND defense 6 plus items plus minion modifiers beats the attack, or the hand card it would replace is the one he needs THEN discard the carapace. WEIGHT 6 for keeping the hand card. Do not spend it on an attack a defense-4 card already beats. After it is spent, the effect is gone: the next attack is a normal defense. SOURCE rulings/mrak.md 9.

9. `CHOOSE_SPACE` [`mrak-green-2b`, `mrak-green-3b`] WHEN a straight line to the battle zone or along his own fissure has at most one empty interior space (two on Strolling Stone) THEN take the longest such line that ends in the zone, not the shortest step. Rocks, heroes, minions and terrain are the road. WEIGHT 8 for entering the zone, 4 for a longer slide that still ends safe. If no legal line exists, the card is not the primary. SOURCE [BGG 3449043]; rulings/mrak.md 7.

10. `CHOOSE_NUMBER` [`mrak-blue-1`, `mrak-blue-2a`, `mrak-blue-3a`] WHEN an enemy unit or a token is adjacent THEN push it the distance that takes it off a minion or into a wall, ignoring obstacles, and follow only if the landing space is closer to the next target and not adjacent to two enemies. WEIGHT 10 for the deny plus 4 for the follow. A push of his own rock is legal and is how he reuses the supply; do it when the rock is in his way and the enemy is not. SOURCE card text; [BGG 3449043].

11. `CHOOSE_UNIT` [`mrak-blue-2b`, `mrak-blue-3b`] WHEN a unit in radius is adjacent to terrain or a rock AND moving it 1 pulls a minion off a take, puts an ally onto a target, or pulls an enemy off a space they needed THEN do it, and the rock lands where they stood. On Ground Shaker, repeat on a different unit if a second such move exists. WEIGHT 15 for the denied minion, 6 for the setup. SOURCE [BGG 3449043].

12. `CONFIRM` [ultimate, which is not a confirm: it happens] WHEN he places rocks and an enemy hero is adjacent to a rock just placed AND it is the first such placement this turn THEN expect the discard and do not also spend a hand card on that same hero unless they have two cards. Planning note, not a choice. WEIGHT already inside rules 5 and 6. SOURCE rulings/mrak.md 10.

13. `UPGRADE_CARD` WHEN his position at the end of the last round was outside the battle zone THEN green B before anything else. WHEN he was defeated or forced to discard last round THEN red B. ELSE red A, then blue A, and no initiative item unless a seismic tie is the kill. WEIGHT 3. SOURCE [ADVICE]; [BGG 3449043].

14. `PLAY_CARD` [`mrak-gold`] WHEN rocks on the board are already walling an enemy AND this attack's line would pick them up (supply 3) AND the wall is worth more than the hit THEN do not play gold. WEIGHT −8 against destroying his own seal. SOURCE [BGG 3449043]; rulings/mrak.md 1.

### evalTerms

- Each enemy hero adjacent to one of his rocks: +4, or +8 if that hero has 0 cards and seismic is in hand (the defeat is available, not yet taken).
- An enemy hero with no empty adjacent space because of his rocks: +10.
- A rock line that crosses the only path to the lane minion and does not also block his ally: +6.
- The same line blocking his ally and not the enemy: −6.
- Carapace or Rock Solid resolved on a turn slot and not yet discarded: +8.
- Rocks sitting in the open, adjacent to nobody and not on a path: +0.5. They are the supply, and gold may need them back.

### threatAdjust

When the enemy is Mrak:

- +10 on any space adjacent to terrain or to one of his rocks, inside his current seismic radius, while a primary red may be in hand. +15 if the hero who would stand there has 0 cards.
- +6 on spaces that would be the far side of a Stone Grip (the empty spaces farthest from him around a hero in his range 3) while silver is in hand and that hero has 3 or more empty adjacent spaces.
- +4 on the three empty spaces beyond an adjacent unit, on the line of a Fissure, while gold is in hand. That is a wall, not only an attack-4 hit.
- His melee threat is real: +8 adjacent, attack 6–7 on the red, attack 4 on gold at initiative 11. Do not stand and trade if your attack is smaller.
- A space that ignores obstacles to leave (a placement, a swap, an ignore-obstacle move) is not made safe by his rocks. Do not add wall-threat against a hero who has that card in hand; add the seismic threat only.
- Safe pockets are spaces with no terrain neighbour and no rock neighbour, outside the straight line of his gold, and more than his radius from him. The spotlight's "middle of the zone, next to the rocks" is where **he** wants to be, and where his enemies should not.

## 7. Sources

- Catalog `mrak.json` advice (3 sentences). No hero FAQ.
- `core/game/rulings/mrak.md`, which cites [BGG 3449043] for the supply of 3.
- [BGG 3449043] Mrak spotlight, 2025-01-26, community. Full 2nd-printing text. Thread replies: the "as far as possible" rule is on the tokens, not on which hero; units are obstacles for Rolling Stone.
- [BGG 3487749] 2025-03-30, community: Mrak and Mortimer as a token pair.
- Gamefound Arcane update 10: control, rocks, low mobility, no self-healing, alt red against discards, alt green for mobility. The "rocks crumble at end of next turn" line does **not** match the card or the engine (rocks leave at end of round, p.19). Rejected. No numbers taken from the update.
- No Reddit thread found.

## Implementation notes

`MrakHeuristics.kt`, checked against `MrakHero.kt` (2026-11-21).

- Rule 2/3: seismic's skill cannot start without an enemy hero in radius next to terrain or any Rock token (`nextToTerrainOrRock`). The card profile reads its radius as a reach, so the general book over-counts it; with no such hero the playbook cancels that estimate (the card remains movement 3, defense 7–8 and an adjacent secondary attack). An empty hand on that ground is a defeat.
- Rule 3 at `CHOOSE_ACTION`: the skill when it defeats; the attack only as a small nudge when an adjacent hero has cards (the lookahead weighs both).
- Weights were cut from the draft (−16 / −10 / +8) to small corrections: they had made his reds and silver nearly unplayable.
- Skipped: 1, 4, 9–11 (movement, pushes and swaps the lookahead plays), 12, 13 beyond `upgradeOrder`.
