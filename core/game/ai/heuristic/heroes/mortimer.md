# Mortimer (`mortimer`) — heuristic playbook

2nd printing. Icons from `mortimer.json`. Both community guides are 2nd-printing and match the cards, with one paraphrase: the spotlight's silver line says adjacent spaces count as spawn points; the card says empty spaces adjacent to him **or** spawn points in radius. The card is what he may do. Zombie supply is 4. They are not removed at end of round, and not when he is defeated (rulings/mortimer.md 1–2; spotlight reply in the thread).

## 1. Identity

| Field | Value |
|---|---|
| Role | `Pusher` (he is also `Tank` once the defense items are on the silver) |
| Complexity | 3 |
| `idealDistance` | 2. He hits adjacent. Every zombie action is range or radius 4, so the herd, not his body, is what must sit on the fight. |
| Lane vs kill | `laneWeight` 0.65 until tier-3 red, then 0.50. He is a mid-to-late pusher [BGG 3453397]. Early, a wave is worth more than a zombie. |
| `aggression` | 0.40 in rounds where he has 0 zombies, 0.60 once three or more are within radius 4 of the battle zone |
| `keepDefender` | true. Silver is defense/skill, defense icon 3, and it places zombies even when the defense fails (catalog FAQ; rulings/mortimer.md 3). Reds print defense 7–8. He can afford to be hit by small attacks; he cannot afford to spend the red on a nothing turn. |
| `upgradeOrder` | two builds in §3, Vengeful Dead and Money Maker, both from [BGG 3556825] |

Profile bars (not added in play): Attack 8→8, Defense 7→7, Initiative 2→2, Movement 4→4. The bars match a slow, short body; the icons decide it. Gold initiative 11, red 7–8, green 4–5, blue 9–10, silver 6. Movement icons are 1–3. He acts late on the cards that move zombies and early-middle on gold.

Gold attack is 4 plus 1 per zombie removed adjacent to the target, and he may remove up to 3, so the gold is attack 4, 5, 6, or 7 [BGG 3453397]. That is a real kill only when the herd is already next to the target or one step away.

## 2. Card-by-card

`mortimer-gold` Knife of the Living Dead. Initiative 11, attack 4, defense 3, movement 1, radius 4. Adjacent unit. Before the attack, up to three times: move a zombie in radius 1, and/or remove a zombie adjacent to the target for +1 attack. The removals happen even if the attack then misses (rulings/mortimer.md 10). Play when at least one zombie is already adjacent to the target or one move away, and the target is a hero who dies at attack 5–7 or a minion. Do not spend three zombies to reach attack 7 if attack 5 already kills. Defense 3: not the card he holds against a real hit. The 3-choice limit becomes 5 with the ultimate.

`mortimer-silver` Awaken! Initiative 6, defense 3, radius 4, defense/skill. Place up to 4 zombies on empty spaces adjacent to him or on empty spawn points in radius. They persist. Play as soon as the wave allows, not instead of the first round's minions [BGG 3453397]. Also the card he discards to defend: the zombies are placed even when he is defeated. Four is the supply; a fifth placement asks which zombie to pick up first.

`mortimer-red-1` Crawling Dead. Initiative 7, attack 6, defense 7, movement 3, radius 4. Adjacent unit. After a connecting attack, choose one: move a zombie in radius 1, or another enemy hero in radius who is adjacent to a zombie discards, if able. "Another" excludes the attack's target. One choice, not "up to". Tier 1 has no "each hero only once" line because there is only one choice.

`mortimer-red-2a` Walking Dead. Attack 7, defense 7, item **defense**. After the attack, up to two times, and each enemy hero only once. The support red: discards, never a defeat from the zombie text.

`mortimer-red-3a` Racing Dead. Attack 7, defense 8, initiative 8, item **range**. Up to three times. The ultimate makes this up to five. The range item grows every "in range" card.

`mortimer-red-2b` Braains...! Attack 7, defense 7, item **initiative**. Before the attack, up to two times: move a zombie 1, or if an enemy hero in radius is adjacent to a zombie (immune heroes do not count), retrieve a discarded card. The retrieve is not optional. The bullet is only offered when he has a card in the discard and the adjacency holds (rulings/mortimer.md 9). The attack still needs a target. A disrupted attack leaves him with no coins and no retrieve [BGG 3453397].

`mortimer-red-3b` Braaaaaaaains...! Up to three times, item **movement**. The movement item is how the body catches the herd.

`mortimer-green-1` Stage Dive. Initiative 5, defense 3, movement 2, range 4. Choose one: move a zombie in range 1, or swap with a zombie in range. His walk. One step or one swap.

`mortimer-green-2a` Crowd Drift. Up to two times, the same two bullets, defense 4, item **attack**. Repeated swaps chain across zombies spaced 3–4 apart [BGG 3453397].

`mortimer-green-3a` Crowd Surf. Up to three times (five with the ultimate), initiative 4, item **attack**.

`mortimer-green-2b` Gathering Horde. Up to two times: move a zombie 1, or once per turn replace an enemy minion in range that is adjacent to two or more zombies with a zombie. Remove, not defeat: no coins, and it can push the lane (rulings/mortimer.md 11). Once per turn, not once per action.

`mortimer-green-3b` Army of Darkness. Up to three times (five with the ultimate), item **initiative**. The same replace.

`mortimer-blue-1` Corpse Slam. Initiative 9, defense 6, movement 3, range 4. Choose one: move a zombie in range up to 1 (0 allowed) and that zombie may push an adjacent unit or token 1 space directly away from itself, or move himself 1. He is not a legal push target (rulings/mortimer.md 7).

`mortimer-blue-2a` Morbid Mosh. Up to two times, defense 6, item **attack**.

`mortimer-blue-3a` Macabre Mayhem. Up to three times (five with the ultimate), defense 7, initiative 10, item **radius**.

`mortimer-blue-2b` Robbing Zombies. Up to two times: move a zombie in range up to 1 and gain 1 coin, or move himself 1. The coin is gained even when no zombie is in range, because "up to" does not stop the bullet (rulings/mortimer.md 6). Two coins, or one coin and a step.

`mortimer-blue-3b` Stalking Scalpers. Up to three times (five with the ultimate), defense 7, item **defense**. Three coins with no zombies, five once the ultimate is in.

`mortimer-purple-4` Master of Puppets. "Up to three times" becomes "up to five". "Choose one" and "up to two" do not change (rulings/mortimer.md 12). So tier-3 cards and the gold gain two extra choices. Tier-2 cards do not.

`mortimer-handicap` Shallow Graves. Initiative 10, attack 3, defense 1, movement 1, radius 4. A hero adjacent. Before the attack, up to two times, the gold's move-or-consume. Attack 3 plus up to 2. A smaller gold, heroes only.

## 3. Upgrade path

From [BGG 3556825] Mad_Mike Builds the Awakener (2025-08-10, community), which matches the spotlight.

**Vengeful Dead** (be hit, block with silver, zombies appear, gold them). Track enemy attack totals. Silver's defense icon is 3; each defense item adds 1. He wants silver to beat the attacks that will actually be swung at him.

- Tier 2: `mortimer-blue-2a` for the push when no hero is in front of him. `mortimer-red-2b` so a hit he did take can be retrieved. Green either way; Gathering Horde if the gold must stay large and the wave must move while he stands still. The attack item on Crowd Drift also grows the gold.
- Tier 3: `mortimer-blue-3a` first most of the time (defense 7 on the blue, radius, three pushes). `mortimer-green-3b` for the attack item and an easier two-zombie replace. Red last and by need: `mortimer-red-3b` (movement, retrieve, three choices) if he is swinging and healing; `mortimer-red-3a` (range, discards) if the wave is ending and heroes are running.

**Money Maker** (coins, then become a problem).

- Tier 2: `mortimer-blue-2b` is the build. `mortimer-red-2a` so the red still discards and defends at 7 while he farms. `mortimer-green-2a` is the travel. He can farm with blue and defend with red, or the reverse.
- Tier 3: when the coins will finish the levels he needs, switch the blue to `mortimer-blue-3a` and start pushing. If the ultimate is the actual plan, stay on `mortimer-blue-3b` and take five coins a play, and consider `mortimer-green-3b` over Crowd Surf for the attack item. `mortimer-red-3a` for two or more discards at range.

**Opening, same guide.** He cannot silver and still make a normal heavy race. Ask the team for one stalled turn, or give the wave up on purpose.

- Standard: green, red, blue, gold to get behind the side minions. Jungle is how he ends out of position. Center is the safe stand, and the red threatens a hero whose gold attack is 3 or less because his red defense is 7.
- Resurrection: center, and on the turn their twins-taker plays gold (often turn 3), play silver. One zombie on the space between the side minions, one next to the hero he will gold, one toward the wave's next zone, one on a spawn he wants presence on. They spend a blue to live and they do not walk to the second minion.

Always, before a wave moves: spend a choice to step zombies off spawn points the minions will spawn on. A spawn removes the zombie [BGG 3453397].

Default if no build is forced: Vengeful Dead. Money Maker when he is two or more coins behind the level the table is on, or when `mortimer-blue-3b` plus the ultimate is in reach before the last wave.

## 4. Synergies and counters

- Mrak [BGG 3487749, 2025-03-30, community]. Both durable, both token walls. Mortimer's zombies and Mrak's rocks do not share a supply. A zombie next to a rock is a space Mrak's seismic already likes, and a push from Corpse Slam can use either as the pushed token.
- Any ally who stalls round 1 for one turn. That is the difference between a herd and a melee attack 6 with no text.
- He does not need a fast partner late. He needs them early.

He fears: being pushed out of radius 4 of his own zombies [BGG 3453397, "Playing Against"]. A clear on the two zombies that were about to replace a minion. Spawn points eating the herd. His gold and his reds need a target; deny the minion and the text does not start.

Against him (this is `threatAdjust` and the enemy's choices):

- He is a melee attack 6–7 with extra text only inside radius 4 of a zombie. Push him, do not stand in that radius if you can fight elsewhere.
- Do not spend an attack to clear a zombie unless it is the one blocking the path you need, or clearing it costs him a silver turn to rebuild [BGG 3453397]. A zombie in the open is not worth a card.
- Stand in a pocket with few empty adjacent spaces if his blue push is the card. The zombie needs a straight step away from itself.
- His red discard and his red retrieve care about **him** being in radius 4 of you, not only the zombie. Leaving the zombie and staying in his radius still feeds Braains. Leave the radius.
- Silver on defense places the herd even when he dies. Killing him on the silver turn still leaves up to 4 zombies. Plan for them.
- Tokens persist through his death. They persist through the round. Only a spawn, a replace, his own consume, or a clear removes them.

## 5. Common mistakes

- Silver on turn 1 of round 1 while the enemy takes both side minions and the heavy. The guide is explicit.
- Packing all four zombies together every turn. Pack for the gold, for Gathering Horde, or for a wall. Otherwise one zombie toward the next zone, the cluster in the center [BGG 3453397].
- Leaving them on the spawn the wave is about to use.
- Consuming three zombies for attack 7 when attack 5 already defeats the target.
- Playing Braains with no discarded card and no hero next to a zombie, then whiffing the attack because the target walked. The retrieve never happens and the attack still needed a body.
- Expecting the tier-1 red to discard two heroes. It is choose one.
- Expecting the ultimate to add choices to a tier-2 "up to two" card. It does not.
- Chaining swaps through zombies that are 5 apart. Crowd Drift's range is 4, and each swap needs a zombie in range of where he then stands. Space them 3–4.
- Replacing a minion and counting the coins. Replace gives none.
- Blocking his own ally with a four-zombie wall on the path to their minion. Official advice.

## 6. HEURISTIC RULES

Zombie count on the board is hero-state, capped at 4. Distance, lane, hands, and initiatives are HContext.

1. `PLAY_CARD` [`mortimer-silver`] WHEN zombies on the board < 2 AND the lane is not about to be lost this round (enemy push potential cannot take the last minion before initiative 6, or an ally is taking it) AND it is not turn 1 of round 1 unless the Resurrection stall in §3 is on THEN play silver. Place: one on the path between the side minions if an enemy will walk it, one adjacent to the hero he wants to gold, one toward the next zone, one on a spawn that is not about to spawn, else adjacent to him. WEIGHT 8 for going from 0 to 4, 4 for topping up. Never place on a spawn the upcoming wave will use if another empty space exists. SOURCE [BGG 3453397]; [BGG 3556825]; [ADVICE].

2. `PLAY_CARD` [any zombie-mover] WHEN a zombie is on a spawn point a minion will spawn on at end of round AND he has a choice left on this action THEN move that zombie off it first. WEIGHT 6 per zombie saved. SOURCE [BGG 3453397].

3. `PLAY_CARD` [`mortimer-gold`] WHEN an enemy unit is adjacent AND at least one zombie is adjacent to it or one move away inside radius 4 AND attack 4 plus the zombies he can remove (max 3, or 5 with the ultimate) beats the defense they have left THEN play gold, and remove only as many as the kill needs. Move the rest to block the escape or toward the next zone, do not consume them. WEIGHT a hero kill, or 15 for the lane minion. If no zombie can reach the target, gold is attack 4: play it on a minion only when the lane needs it. SOURCE [BGG 3453397].

4. `PLAY_CARD` [`mortimer-red-2a`, `mortimer-red-3a`, `mortimer-red-1`] WHEN he is adjacent to a minion or a hero he should hit AND another enemy hero is in radius 4 and adjacent to a zombie AND that other hero has a card THEN attack the minion or the primary target and spend choices on the discard, one per hero. Move a zombie into adjacency first if the choice budget allows and the hero is one step away. WEIGHT 2.5 per discard, 8 if the discard happens before an ally's unresolved kill. Tier 1 is one choice total. SOURCE [BGG 3453397].

5. `PLAY_CARD` [`mortimer-red-2b`, `mortimer-red-3b`] WHEN he has a card in the discard AND an enemy hero in radius is adjacent to a zombie (not immune) AND the attack target will still be there at his initiative THEN play it, take the retrieve once, and spend the other choices moving zombies onto the next target. WEIGHT 2.5 for the card plus the attack. If either condition fails, do not play this red for the text; use it as defense 7–8 or as the attack only. SOURCE [BGG 3453397]; rulings/mortimer.md 9.

6. `PLAY_CARD` [`mortimer-green-2b`, `mortimer-green-3b`] WHEN an enemy minion in range 4 is adjacent to two or more zombies AND he has not replaced a minion yet this turn THEN replace it, and spend the other choices walking the pair toward the next minion. WEIGHT 12 (a lane minion with no coins is a push without the purse; 15 minus about 3 coins). If the pair is one step short, spend the first choice moving the second zombie in, then replace, once. SOURCE [BGG 3453397]; rulings/mortimer.md 11.

7. `CHOOSE_OPTION` [`mortimer-green-2a`, `mortimer-green-3a`, `mortimer-green-1`] WHEN he is more than 2 spaces from the battle zone or from the hero he is supposed to stand on AND a chain of zombies spaced at most 4 apart reaches that area THEN swap along the chain, and spend leftover choices moving a zombie into the next gap. WEIGHT 8 for arriving. A single swap that only gains one space is movement; do not spend the whole turn on it if a minion is adjacent. SOURCE [BGG 3453397].

8. `CHOOSE_OPTION` [`mortimer-blue-2a`, `mortimer-blue-3a`, `mortimer-blue-1`] WHEN a zombie is adjacent to an enemy hero or a minion that is on a space his team needs empty THEN move the zombie and push that unit 1 away from the zombie, off the minion or into a wall. Spend a leftover choice on his own 1-space move only to stay in radius 4 of the herd. WEIGHT 8 per push that changes a take, 3 for a cosmetic push. SOURCE [BGG 3453397].

9. `PLAY_CARD` [`mortimer-blue-2b`, `mortimer-blue-3b`] WHEN the coins finish a level this round, or he is on Money Maker and below the level the table is rushing, AND no rule-6 replace or rule-3 kill is available this turn THEN take the coin bullet up to the choice limit (2, 3, or 5). A zombie move on that bullet is optional. WEIGHT 1 per coin, 1.5 if it completes the level. Do not farm on the turn the lane minion is in range of a replace. SOURCE [BGG 3556825]; rulings/mortimer.md 6; [ADVICE] "farm blue".

10. `DEFEND` [`mortimer-silver`] WHEN the attack total beats every card except those he must keep for the kill AND silver's defense 3 plus defense items plus minion modifiers either beats the attack, or he has fewer than 2 zombies and the death is coming anyway THEN discard silver. The zombies are placed even on a failed defense. Place them where rule 1 places them, not in a pile on his corpse's adjacent spaces if those block his ally. WEIGHT 8 for the herd on top of the defense. WHEN red's defense 7–8 beats the attack and silver would not, keep silver and spend a low card or the red only if the red is not the upcoming kill. SOURCE catalog FAQ; [BGG 3556825].

11. `CHOOSE_SPACE` [his own "move 1" bullet] WHEN he is outside radius 4 of every zombie THEN step toward the herd, not toward a far minion. WEIGHT 4. Official: do not obstruct the ally. A step that enters the ally's only path is rejected. SOURCE [ADVICE]; [BGG 3453397] "within range of at least one zombie".

12. `PLAY_CARD` [red or gold] WHEN the only adjacent target can walk away before his initiative (their unresolved card is higher than his and is a move) THEN do not play the attack. Play a zombie move that puts two zombies on the minion they will not reach, or silver if the herd is down. WEIGHT −10 on a whiffed Braains. SOURCE [BGG 3453397] "have a guaranteed target".

13. `UPGRADE_CARD` WHEN his coin count is at least 2 behind the next level the enemy is buying AND the lane is stable THEN Money Maker, blue B first. ELSE Vengeful Dead, blue A then the retrieve red. WEIGHT 3. SOURCE [BGG 3556825].

14. `REMOVE_MINION` and clears he is offered on enemy tokens WHEN the token is not his, no rule. WHEN he is the enemy and the AI is clearing: see threatAdjust. His own choices never clear his zombies unless rule 3 is consuming them for a kill.

### evalTerms

- Each zombie not on a spawn that will spawn this wave: +2.
- A zombie on such a spawn: −3 (it is about to be deleted).
- A zombie adjacent to an enemy hero who is inside his radius 4: +3.
- Two or more zombies adjacent to the same enemy minion in his range 4, and he has not replaced yet this turn, and green B is in hand: +12.
- Three zombies adjacent to a hero he can reach with gold this turn: +6 (the gold's attack becomes 7).
- A zombie whose space is the only path an enemy needs, and which does not also block his ally: +4.
- Zombies more than 4 apart from each other and from him, with green A in hand: −2 each beyond the first. The chain is broken.

### threatAdjust

When the enemy is Mortimer:

- +6 on every enemy-hero space inside radius 4 of him while a zombie is adjacent to that space and a red is in hand. That is the discard and the retrieve, not a ranged attack. The attack itself is adjacent only: +8 on spaces adjacent to him while red or gold is in hand (attack 6–7, or 4–7).
- +4 on spaces one step from a zombie in his blue range while Mosh is in hand (the push).
- A zombie blocking a chokepoint the AI needs: threat on that token is 6, enough to spend a Clear when the path is the lane, not enough to spend an attack card [BGG 3453397].
- Do not add threat for a zombie sitting in the open with no hero adjacent.
- His body more than 4 from every zombie: treat him as attack 6–7 melee with no extra text. Worth kiting, not worth a detour to clear tokens.

## 7. Sources

- Catalog `mortimer.json` advice (2 sentences) and three FAQ entries (supply, defend-and-place, immune adjacency).
- `core/game/rulings/mortimer.md`. Cites [BGG 3556825] for the four-token opening and [BGG 3466150] for the supply count.
- [BGG 3453397] Mortimer spotlight, 2025-02-02, community (Mad_Mike). Full 2nd-printing guide. Placement, packing, spawn points, playing against him. Thread reply confirms tokens survive his defeat and "at his feet" means adjacent.
- [BGG 3556825] Mortimer Build & Go, 2025-08-10, same author. Vengeful Dead, Money Maker, Standard and Resurrection openings.
- [BGG 3487749] 2025-03-30, community: Mrak + Mortimer as a durable token pair.
- No 1st-printing conflict in the sources used. No Reddit thread found. The guides are one author's; branch order inside a tier is described as flexible, and the rules keep that.

## Implementation notes

`MortimerHeuristics.kt`, checked against `MortimerHero.kt` and `engine/Board.kt` (2026-11-21).

- Rule 1/2 checked: a token on a spawn point is removed when a unit spawns there (`spawnPiece`, p.19), so a zombie placed on a spawn is a short-lived one; the placement score avoids spawns.
- The "choose up to N times" bullets are `CHOOSE_OPTION`s; the lookahead plays them (rules 7, 8, 9's bullet).
- `evalTerms` does not add a flat value per zombie: `HEval` already counts every lasting token.
- Skipped: 4, 5, 10, 11, 13 beyond `upgradeOrder`.
