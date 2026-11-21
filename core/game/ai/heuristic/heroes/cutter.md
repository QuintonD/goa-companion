# Cutter (`cutter`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/cutter.json`. Engine: `CutterHero.kt`.
Rulings: `core/game/rulings/cutter.md`. Complexity 3. Stats: Attack 4/8, Defense 4/4, Initiative 6/6, Movement 4/8.
Catalog roles: tactician, damager, farming. No tokens.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). A win by `cutter-blue-3b` is the game, not a pile of coins. `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances. `CTX hero` needs the branches, her coin total against 13, and the card in the previous turn slot when `cutter-purple-4` is unlocked.

The item you gain is the icon on the card you tuck, not the card you keep. The "+2 Attack" on the lunge is card text: Attack items add to the printed Attack icon (4, then 5, then 5) and do not add to the +2.

1st-printing Cutter is a different deck. Gold pushed 3, not 4. Silver was Initiative 2 and its defense action moved her. The lunge was optional and +3, and it could target a unit. Alt green was a block (Mediocre / Pathetic). Alt blue placed coins on hexes. The BGG wiki page and [BGG 2893527] describe that deck. Only the ideas that still match these cards are used, and they are marked.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Pusher | She opens the fight and moves the wave. Class label [BGG 3779890; BGG 3790096]: Initiator / Pusher. The kill is usually an ally's. |
| idealDistance | 2 | Gold wants 1. The lunge wants a clear line of 1–3 with the hero in the next hex. The dash wants 3 or more. Standing on them wastes the line cards. |
| aggression | 0.65 | [ADVICE] the silver is a long move at low initiative so she can start the fight. She still holds red or blue to live: Defense 4. |
| laneWeight | 0.60 | [BGG 3790096] start ahead on the wave. Kill pressure is how she gets that wave, not a separate plan. Alt blue wants the lead before it asks for coins. |
| keepDefender | true | Red on the primary branch is Defense 6–7. Blue is Defense 5–6. Silver and gold are Defense 2. |
| upgradeOrder | see §3 | Barrage and the lunge first. The dash if she is hitting it; the swap if the team is behind. |

She wants two lines, not a hex. The line she will travel, and the line an enemy will be shoved or lunged along after she arrives. A single crowded hex is how she gets turned off [BGG 3790096, "Playing against"].

## 2. Card-by-card

Initiative band: Gold 12, Blue 10 then 11, Red 8 then 9, Silver 5, Green 3 then 2. Her Initiative stat is 6. Silver is slow enough to relocate after the reds, and still faster than her own green. Green is the card she saves until she has seen where everyone stopped. Gold is how she acts before their answers, once she is already adjacent.

### `cutter-gold` Walk the Plank — Defense 2, Movement 1, Initiative 12, Skill

Choose one: push an adjacent enemy hero up to 4, and if the hex they end on is in a different zone from the hex they started on, they discard or are defeated; or defeat an adjacent enemy minion. A push of 0 does not change zone. A hero she cannot push (Bulwark) is not offered. Engine: `CHOOSE_OPTION`, then `CHOOSE_UNIT`, then `CHOOSE_NUMBER` 0..4. The zone test is on the hex the push actually reached.

For: the shove, not the kill. [BGG 3790096] it is worse than a 1-Attack gold at finishing a turn-4 empty hand, because the defeat needs a zone border on the ray. Used early, the discard lands before they attack, and the landing hex is where an ally is waiting. Also the way she peels someone off a minion, including the heavy. [ADVICE] gold and red are both awkward; the other cards exist to put her on the hex this one needs. [Gamefound Defiant, 2023-05-07] the card only works from the exact spot.

When: she is adjacent, and some distance 1–4 along the away-ray either crosses a zone or puts them in an ally's unresolved attack. Initiative 12 beats typical reds, so they do not step off first.

When not: a push that changes nothing and a minion is sitting there (take the minion). A push into a line where their next card still works. As travel (Movement 1). Defense 2.

Handicap `cutter-handicap` Few More Steps replaces this in a 5-player game: push up to 3, no minion bullet, Defense 1, Initiative 12. Same zone rule. Do not plan a minion take on it.

### `cutter-silver` Grappling Bolt — Defense 2, Initiative 5, Range 5, Skill

Target the first obstacle on a straight line within range 5, ignore immunity, and move in that line until she is adjacent to it. Obstacles include units, tokens, terrain, other miniatures, immune units, and the water / edge around the board (FAQ). Already adjacent: the target is legal and she does not move. Effects that forbid targeting still apply (Smoke Bomb, Crack in Reality). Engine: `CHOOSE_SPACE` on those obstacle hexes, one per direction. No Movement icon, so a Movement item does not lengthen it. A Range item does.

For: the relocate. [ADVICE] long moves at low initiative, which is how she plays forward. [BGG 3790096] draw the six lines at the start of the round and pick the anchor whose adjacent hex skirts the fight instead of hugging an enemy. The cradle behind the twin minions is the landmark they name: from there the jungle, the central terrain and the beach minion are all hooks. Play it on turn 1 or 2 so the Defense 2 card is gone and green is still available once people have moved.

When not: the only anchor leaves her adjacent to two enemies and she has already played red and blue. An enemy standing on the line becomes the first obstacle and she stops next to them; pick another line if one exists. Defending with this card does not move her. The 1st-printing defense-move [BGG 2895440] is not this card.

### `cutter-red-1` Daring Strike — Attack 4, Defense 6, Movement 3, Initiative 8

Choose one. Before the attack, move exactly 1 in a straight line, then target the hero in the next hex in that direction, +2 Attack. Or target a unit adjacent to her, no bonus. The move does not say "to a space in front of a hero": it is mandatory, and if the hex in front is empty the attack stops after the move (rulings §3). The +2 is card text. Engine: `CHOOSE_OPTION`, then `moveSelf`, then the attack with bonus 2.

For: the hit that matches a Brogan charge's Attack 6, with the restriction that the bonus target is a hero on the line [BGG 3790096]. The adjacent bullet is how she takes a minion. She ends the lunge next to them, so an enemy minion on that hex is a defense penalty she has accepted. A tank who can block Attack 6 with a blue, and still hold their red, punishes the dive.

When: a 1-hex step ends with an enemy hero in the next hex, and her Attack total (4 + items + 2) meets their Defense or they are standing on a minion she is willing to give up the shot for. Initiative 8 loses to golds and to blues at 10, so the line has to survive their move. [BGG 2893527, the part that survives] forcing them to spend the turn stepping off the line is a good result; forcing them to spend their red to do it is a better one. The card no longer gives +3, and the bonus no longer works on a minion.

When not: a lunge into a hex with nobody in front. The adjacent bullet into a hero who still holds Defense ≥ her Attack, when a minion is also adjacent. Defense 6 is the card she keeps if Attack 6 is coming back.

### `cutter-red-2a` Bold Thrust — Attack 5, Defense 6, Movement 3, Initiative 9, item Initiative

Move 1 or 2, then the hero in front, +2 (so 7 plus items on the 5). Keeping it tucks `cutter-red-2b` and grants Defense.

### `cutter-red-3a` Fearless Lunge — Attack 5, Defense 7, Movement 3, Initiative 9, item Movement

Move 1, 2 or 3, then the hero in front, +2. Keeping it tucks `cutter-red-3b` and grants Radius (the blues and the alt green).

### `cutter-red-2b` Evasive Shot — Attack 4, Defense 4, Movement 3, Initiative 9, Range 2, Ranged, item Defense

A unit in range 2 on a straight line. No "empty line" clause: a unit between her and the target does not, by this card's text, stop the shot. The engine checks `inStraightLine` and range only. After the attack connects, she may move up to 2 in the opposite direction. The direction is fixed when she picks the target, and the step still happens if the target was a minion she defeated (rulings §4). The 1st-printing complaint that a dead minion cancelled the step does not apply.

For: the safe minion take, including a heavy, then a step out of the Battle Zone (fast travel next) or deeper in [BGG 3790096]. Obstacles that would stop the lunge do not stop this shot.

When not: diving in. Defense 4 is her stat and no better. If both backstep hexes are denied, she stays in the fight she was trying to leave. Keeping it tucks `cutter-red-2a` and grants Initiative.

### `cutter-red-3b` Tumble Shot — Attack 4, Defense 5, Movement 3, Initiative 9, Range 2, item Radius

Same, backstep up to 3. Keeping it tucks `cutter-red-3a` and grants Movement.

### `cutter-green-1` Brace for Impact — Defense 3, Movement 2, Initiative 3, Skill

Move exactly 3 in a straight line, ignoring obstacles, to a space adjacent to an enemy hero (presence: an immune hero still makes the hex legal). Then one adjacent enemy hero she can target discards, if able. If the only one is immune, she has moved and nobody discards. Engine: `moveSelf` with that end filter, then `CHOOSE_UNIT`.

For: the engage. Minimum 3, so a hero already adjacent is not a target unless some other 3-hex line ends next to them. [BGG 3790096] the number of hexes at tier III is the threat; the landing is "adjacent", not "on". Because she is slow, this resolves after most movement, and the next turn's red or gold is the second hit. [BGG 2893527] a turn-3 dash that leaves her adjacent is the setup for a turn-4 plank. That sequence still fits these cards. The same guide's block-green branch does not exist anymore.

When: a legal landing exists next to a hero with 1 or 0 cards, or next to a hero she wants adjacent for the plank. Also the travel tool when she is off the wave and a hero is the only way the move is legal: she still has to end next to someone.

When not: no enemy hero is adjacent to any hex 3 steps away. Defense 3.

### `cutter-green-2a` Ramming Speed — Defense 3, Movement 2, Initiative 3, item Attack

Move 3 or 4. Keeping it tucks `cutter-green-2b` and grants Defense.

### `cutter-green-3a` Crashland — Defense 4, Movement 2, Initiative 2, item Range

Move 3, 4 or 5. Keeping it tucks `cutter-green-3b` and grants Defense. The Range item is on this card, so she gains it only by tucking it.

### `cutter-green-2b` Outmaneuver — Defense 3, Movement 2, Initiative 3, Radius 3, item Defense

Swap with an enemy minion in radius 3, then she may move that minion up to 2. Not blocked by terrain (a swap ignores obstacles). [BGG 3790096] the reliable way in when the lines are a mess, and the way she hands a minion to an ally: the swap plus the 2-hex nudge is the "five or six hexes toward your team" they describe. She can also nudge it back toward herself and take it with another card. [ADVICE] the branch for mobility and for setting up minion takes. Keeping it tucks `cutter-green-2a` and grants Attack (the lunge's printed 4 or 5).

When not: the swap that lands her next to two enemies with red and blue already spent, or that delivers the minion to the enemy.

### `cutter-green-3b` Outsmart — Defense 4, Movement 2, Initiative 2, Radius 3, item Defense

Same, nudge up to 3. Keeping it tucks `cutter-green-3a` and grants Range (silver to 6, the shot to 3).

### `cutter-blue-1` Bombardment — Defense 5, Movement 3, Initiative 10, Radius 3, Skill

An enemy hero in radius, adjacent to another enemy unit, and not adjacent to her, discards if able. The "another unit" check is presence: their minion, or their teammate, including an immune one. She herself must not be adjacent. Engine: `CHOOSE_UNIT`.

For: the initiator. [BGG 3790096] heroes standing next to their own minion, playing a slow card because they feel safe, are the target. It spends no attack. Play it early, keep the red to block, keep the gold to shove or to take the minion. Initiative 10 beats typical reds.

When not: the only enemy in radius is adjacent to her, or is standing alone. Then it fizzles, and it was a Defense 5 card.

### `cutter-blue-2a` Barrage — Defense 5, Movement 3, Initiative 10, Radius 4, item Initiative

Same, radius 4. Keeping it tucks `cutter-blue-2b` and grants Attack.

### `cutter-blue-3a` Broadside — Defense 6, Movement 3, Initiative 11, Radius 4, item Initiative

Same, and she may repeat once on a different target (`CONFIRM`). Keeping it tucks `cutter-blue-3b` and grants Attack. Tuck this card to gain the Initiative item.

### `cutter-blue-2b` X Marks the Spot — Defense 5, Movement 3, Initiative 10, Radius 3, item Attack

An enemy hero in radius chooses: she places them on an empty hex in her radius, or she gains 2 coins. The place option is not offered when no empty hex exists or they cannot be placed (Bulwark); she then gains the coins with no question (rulings §7). Engine: her `CHOOSE_UNIT`, then the enemy's `CHOOSE_OPTION`, then her `CHOOSE_SPACE` if they allowed it.

For: a bad deal unless both answers hurt them. [BGG 3790096] they will not hand her the answer she wants. Placement is forced when giving her the coins is worse: she is ahead on the wave, or an ally's unresolved attack covers the hexes she can reach, or (at tier III) the coins win the game. They allow a soft placement when she is already ahead on levels and the hex does not obviously end their turn. [BGG 3790096 post 3, 2026-11-02] a slow hero about to swap with a piece (Mortimer is the example) would rather be moved than pay, if the move takes them off that piece. Keeping this tucks `cutter-blue-2a` and grants Initiative, and gives up Barrage.

### `cutter-blue-3b` A Fistful of Coins — Defense 6, Movement 3, Initiative 11, Radius 3, item Attack

Same, 3 coins, and if she has 13 or more coins after the gain she alone wins. The engine ends the game as her team's win (`WinReason.CARD_TEXT`). Coins accumulate inside the round; levels are bought at the end. Past the last level she still gains coins (rulebook p.16). 13 is the whole plan. [ADVICE] alt blue is the extra coin when she has an action to spare, which matches the spotlight: it is at its best when she is already ahead and can afford a card that might "only" pay 3.

The enemy's correct answer, when this choice is put to them: if her coins + 3 ≥ 13, always be placed. Otherwise be placed when every legal hex is outside unresolved attacks and still in the fight they wanted; pay the 3 when the best hex is in an ally's attack or off the map's useful side. That decision is theirs. Her playbook's job is to pick a target for whom both answers are bad, and to play the card with no placement possible when the coins would win outright.

### `cutter-purple-4` Legend of the Skies — Ultimate

The first time each turn, after she performs a primary action, she may perform the primary of the card in the previous turn slot. Declining spends it: the engine marks the turn used before she answers, and a repeat does not ask again (rulings §9). Nothing on turn 1. The copied action is itself a primary, but it is not "the first", so it does not chain. Secondaries do not trigger it. A facedown previous card has no primary she can perform.

[BGG 2893527, the part that still matches the sentence] one extra primary on a turn she can use it is the value; a seven-action round is a fantasy. The practical pairs are a dash or a lunge copied after the other one, so turn N's card is the button on turn N+1. Silver is a reliable primary to hang the trigger on, because the grapple usually has an anchor. This is the plan the current search AI does not keep across the round (AI.md, "Runes and other per-turn plans").

## 3. Upgrade path

[ADVICE] gold and red need the other cards to line them up. Primary blue and green are timing cards. Alt green is the reliable move and the minion pass, used when the team is behind [BGG 3790096]. Alt blue is the coin card when she can spare the action, and the win card when 13 is in reach. [BGG 2893527] told her to pick green first because one of the two greens was a block. That branch is gone. Green is no longer the automatic first upgrade.

Default, the initiator (keep / tuck → item). She is trying to discard someone on turn 2 or 3 and still have a Defense 6.

1. Tier II first: keep `cutter-blue-2a`, tuck `cutter-blue-2b` → Attack. Barrage at radius 4. The Attack item adds to the lunge's printed Attack, not to the +2.
2. Tier II second: keep `cutter-red-2a`, tuck `cutter-red-2b` → Defense. Lunge of 1–2, Defense 6 in hand, and a Defense item on a track that stays at 4.
3. Tier II third: keep `cutter-green-2a`, tuck `cutter-green-2b` → Defense, if the 3–4 dash is finding landings. Keep `cutter-green-2b`, tuck `cutter-green-2a` → Attack, when her team is behind on the wave or allies cannot reach the minions.
4. Tier III first: keep `cutter-red-3a`, tuck `cutter-red-3b` → Radius. The lunge reaches 3. Radius widens Barrage and, if she ever swaps branch, the coin card and the minion swap.
5. Tier III second: keep `cutter-green-3a`, tuck `cutter-green-3b` → Defense, dash of 5. She does not get the Range item on this path; it is printed on the card she kept. Take the Range only by switching to `cutter-green-3b`.
6. Tier III last: keep `cutter-blue-3a`, tuck `cutter-blue-3b` → Attack. The repeat. Switch: keep `cutter-blue-3b` when her coins + 3 can reach 13 this game, or she is already ahead and the coin is worth more than a second discard. That tuck is Initiative (`cutter-blue-3a`'s icon).

Alt red, when the lanes are full of bodies and the lunge keeps fizzling [BGG 3790096]: keep `cutter-red-2b`, tuck `cutter-red-2a` → Initiative, and set idealDistance to 2 with a backstep. Defense in hand on that card is 4, so the Defense item from a tucked green, or keeping the blue, has to do the work. At tier III keep `cutter-red-3b` (backstep 3); the tuck is Movement.

Do not import "Giant Killer" or "Tiny Dancer" [BGG 2893527]. Both assume the deleted block green and a +3 lunge.

Branch summary:

| Colour | Default keep | Tuck → item | Switch keep when |
|---|---|---|---|
| Blue | `cutter-blue-2a` / `3a` | Attack, then Attack | Ahead on the wave, or coins can hit 13: `2b` / `3b` (Initiative). |
| Red | `cutter-red-2a` / `3a` | Defense, then Radius | Lines are cluttered: `2b` / `3b` (Initiative, then Movement). |
| Green | `cutter-green-2a` / `3a` | Defense, then Defense | Team is behind, or allies need the minion handed to them: `2b` / `3b` (Attack, then Range). |

Do not tuck the only Defense 6. Silver is not an upgrade.

## 4. Synergies and counters

Teammates: anyone who can hit a hero she has shoved, discarded, or placed. She is the first hit [BGG 3790096; Gamefound]. A partner who already threatens a hex in her radius makes X Marks / Fistful a real placement. She wants to be on the team that can punish a hero with one card left, because her gold often cannot take the last card herself unless a border is on the ray.

Enemies she fears: anyone who stands on the line. A body between her and the lunge hex stops the move (the lunge does not ignore obstacles; the dash does). Bulwark turns the plank off and can turn the placement off. A sandwich on the alt red leaves the backstep with nowhere to go. High defense that blocks Attack 6 without spending a red, because the lunge has to arrive.

Playing against her [BGG 3790096]:

- Stand well inside a zone, not on a ray that reaches a border within 4. The plank does not defeat or discard without that border. It also does nothing to a hero she cannot push.
- Step onto the grapple line she wants. She stops at the first obstacle. Be that obstacle only if being adjacent to her is acceptable; otherwise she still has five other directions.
- The lunge is a straight move of 1–3, then the hero in the next hex. A unit or a terrain hex on the path stops it. Being adjacent already is safe from the +2; she would have to use the no-bonus bullet.
- The dash ignores obstacles and cannot be shorter than 3. Adjacent to her is the safe pocket from that card.
- Alt red steps backwards after the shot. Take away both directions if you want her to stay.
- Clutter beats her. Every extra body deletes a line.
- When she offers placement or coins, take the placement if her coins + 2, or + 3, would reach 13. Take the placement if you are about to use a piece she is pulling you off. Pay the coins when every hex she can reach is in an unresolved attack.
- One Man Army is not hers. Do not stand on a Brogan charge line she has just pushed you onto.

## 5. Common mistakes

- Saving the plank for a turn-4 kill. Unless the target has no cards and a border is on the ray, it is a shove.
- Lunging with nobody in the next hex. She moves, and the attack does not happen.
- Expecting the +2 on a minion, or on a hero she is already adjacent to.
- Playing the dash when the only enemy hero is already adjacent and no 3-hex line ends next to anyone.
- Playing Barrage into a hero who is adjacent to her, or into a hero standing alone.
- Treating X Marks as "I choose". They choose. If both answers are fine for them, the card was a Movement 3.
- Handing her the 3 coins when she is on 10 or more.
- Grappling the nearest enemy because the line was clear, and arriving between two of them with Defense 2 already committed.
- Defending with the silver in order to move. This printing does not do that.
- Copying a previous-turn primary that now fizzles, or spending the ultimate's one ask on a second move that does not change the board.
- Building the 1st-printing block green, or counting the lunge as +3.
- Playing green on turn 1 and silver on turn 4. Silver is the early relocate; green is the one she still wants after the board has moved [BGG 3790096].

## 6. HEURISTIC RULES

1. `CHOOSE_OPTION` [`cutter-gold`, `cutter-handicap`] WHEN a push of some distance 1..N (N = 4, or 3 on the handicap) ends in a different zone THEN take the push. WEIGHT the kill if that hero has 0 cards, +10 if they have 1 (the discard empties them), +6 if they have 2 or more and the landing hex is in an ally's unresolved attack or off a Battle Zone minion. WHEN no such push exists and an enemy minion is adjacent THEN defeat the minion. WEIGHT +15. A push of 0 is never the answer. CTX general. SOURCE [BGG 3790096] [ADVICE] [REASONED].

2. `PLAY_CARD` [`cutter-gold`] WHEN she is not adjacent to a hero she can push across a border, and a dash or a grapple this turn would make her so, and the plank is still in hand for a later turn THEN do not spend the plank as Movement 1. WEIGHT −6. WHEN it is turn 4, she is adjacent, and rule 1's push kills THEN it is the finisher. CTX general. SOURCE [BGG 3790096] [BGG 2893527] [REASONED].

3. `CHOOSE_SPACE` [`cutter-silver`] WHEN several first-obstacles are in range THEN prefer the landing hex (the hex adjacent to that obstacle, toward her) that is not adjacent to two enemy heroes, and that sits on a later lunge line, dash landing, or plank adjacency. WEIGHT +8 for a landing that makes rule 1 or rule 6 legal next turn, −8 for a landing adjacent to two enemies when red and blue are already played. An enemy who has stepped onto a line to catch her is a legal anchor and a bad one if any other line exists. Water and the board edge count. CTX general. SOURCE [BGG 3790096] FAQ [REASONED].

4. `PLAY_CARD` [`cutter-silver`] WHEN it is turn 1 or 2 and a landing from rule 3 exists THEN prefer it over the dash. The dash is the card she wants after other people have moved; this one is how she gets there. WEIGHT +4 for clearing Defense 2 out of the final hand, on top of the landing. WHEN it is turn 4 and she is already on a plank or a minion THEN avoid. WEIGHT −6. Defending with it never moves her. CTX general. SOURCE [BGG 3790096] [ADVICE] [REASONED].

5. `CHOOSE_OPTION` [`cutter-red-1`, `cutter-red-2a`, `cutter-red-3a`] WHEN a straight move of 1..N (N = 1, 2 or 3) ends with an enemy hero in the next hex and (printed Attack + items + 2) meets their Defense, or they have 0 or 1 card THEN lunge. WEIGHT the kill, or +8 for the hit that forces a red. WHEN the lunge line is empty of heroes and a Battle Zone minion is adjacent THEN take the adjacent bullet. WEIGHT +15. Do not lunge if the hex in front of every legal step is empty. WEIGHT −10 for a move that fizzles the attack. CTX general. SOURCE [BGG 3790096] rulings §3 [REASONED].

6. `PLAY_CARD` [`cutter-green-1`, `cutter-green-2a`, `cutter-green-3a`] WHEN a straight line of length L (L in 3, or 3..4, or 3..5), ignoring obstacles, ends adjacent to an enemy hero THEN prefer the dash on turn 2 or 3 if the plank or the lunge is still in hand for the next turn. WEIGHT +8 for the discard if they have a card, +6 more if the landing makes rule 1 or rule 5 legal next turn, ×0.6 on that follow-up because it is next turn. A hero already adjacent does not, by himself, make a shorter dash legal. CTX general. SOURCE [BGG 3790096] [BGG 2893527] [REASONED].

7. `CHOOSE_UNIT` [`cutter-green-2b`, `cutter-green-3b`] WHEN swapping THEN prefer the enemy minion whose hex is in the Battle Zone and whose 2- or 3-step nudge can reach an ally, while her own landing is not adjacent to two enemies. WEIGHT +10 for the ally's minion, +6 if she lands next to a second minion she can take. Move the minion toward the ally, not back to the enemy. WHEN the team is ahead and a dash landing from rule 6 exists THEN do not spend this card. CTX general. SOURCE [BGG 3790096] [ADVICE].

8. `PLAY_CARD` [`cutter-blue-1`, `cutter-blue-2a`, `cutter-blue-3a`] WHEN an enemy hero is in radius, not adjacent to her, and adjacent to another enemy unit, and that hero has at least 1 card THEN prefer this over the lunge if the lunge would spend the Defense 6. WEIGHT +8 for the discard, +8 more on Broadside if a second such hero exists (rule 9). No such hero: do not play it. WEIGHT −8. CTX general. SOURCE [BGG 3790096] [ADVICE].

9. `CONFIRM` [`cutter-blue-3a`] WHEN a second legal hero still has a card THEN repeat. WEIGHT +8. A hero with no card discards nothing: Broadside does not say "or is defeated". Decline that repeat. CTX general. SOURCE card text [REASONED].

10. `CHOOSE_UNIT` [`cutter-blue-2b`, `cutter-blue-3b`] WHEN choosing who is offered the choice THEN prefer the enemy for whom every empty hex in her radius is in an ally's unresolved attack, else the enemy whose coins-or-place decision is worst, else — on `cutter-blue-3b` only — anyone, if her coins + 3 ≥ 13. WEIGHT +10 for a placement into an attack, +3 for the coins if they will pay, and the game if placement is impossible and the coins reach 13. If they can place and the coins would win, assume they place, and score the placement only. CTX hero (coins, 13). SOURCE [BGG 3790096] [STRATEGY.md §5] rulings §7–8 [REASONED].

11. `CHOOSE_SPACE` [`cutter-blue-2b`, `cutter-blue-3b`] WHEN she is allowed to place THEN put them in an ally's unresolved attack, else off a Battle Zone minion, else onto a plank ray that crosses a zone, else the hex farthest from the fight they wanted. Never a hex that opens a charge line onto her. CTX general. SOURCE [BGG 3790096] [REASONED].

12. `CHOOSE_OPTION` [`cutter-red-2b`, `cutter-red-3b`] is not a choose-one. The shot is the action. `CHOOSE_SPACE` for the backstep: prefer a hex outside the Battle Zone when she wants fast travel next, else a hex that is a dash or plank setup, else the farthest of the legal backstep hexes from the enemies who have not acted. WEIGHT +6 for leaving the zone, +4 for the setup. If every backstep hex is worse than standing still, move 0. Do not stay adjacent to two enemies when this card is Defense 4 or 5 and blue is spent. CTX general. SOURCE [BGG 3790096] rulings §4.

13. `CONFIRM` [`cutter-purple-4`] WHEN the previous turn slot has a primary that is still legal and its outcome is a minion, a discard, a zone-crossing push, or a lunge that connects THEN take it. WEIGHT that action's value. WHEN the copy would fizzle or would only spend the turn THEN decline. The decline still uses the one ask. On `PLAY_CARD`, while the ultimate is unlocked and it is not turn 4, add +4 for playing the card she wants to copy next turn (the dash before a lunge, or the lunge before a plank), and do not add it for silver unless the grapple is the only primary that will be legal. CTX hero (previous slot, ultimate). SOURCE [BGG 2893527] rulings §9 AI.md [REASONED].

14. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN blue is still tier I THEN keep `cutter-blue-2a` and tuck `cutter-blue-2b` (Attack), unless her coins are already 8 or more or her team is ahead by 2 minions of margin, in which case keep `cutter-blue-2b` and tuck `cutter-blue-2a` (Initiative). Red before green. Green follows rule 7's branch: dash if the lines exist, swap if the team is behind. WEIGHT +4 for the Defense item from tucking the alt red, on a Defense track of 4. CTX general. SOURCE [ADVICE] [BGG 3790096] [REASONED].

15. `KEEP_UPGRADE` [`cutter-red-2b`, `cutter-red-3b`] WHEN kept THEN set idealDistance to 2 and score the backstep. Stop requiring a clear lunge line. CTX hero (branch). SOURCE [BGG 3790096].

16. `PLAY_CARD` [`cutter-blue-3b`] WHEN her coins + 3 ≥ 13 and an enemy is in radius and placement is impossible THEN play it. WEIGHT the game. WHEN placement is possible, play it only if rule 10's placement is worth a card; do not play it just to hope they pay the coins. CTX hero. SOURCE card text [STRATEGY.md §5] [REASONED].

17. `PLAY_CARD` any WHEN it is round 1 and she is not yet on a line toward the Battle Zone THEN prefer silver onto the rule-3 landing over gold and over a lunge that fizzles. WEIGHT +6. CTX general. SOURCE [BGG 3790096] [ADVICE].

### evalTerms

- No token and no marker. Do not invent a plank "mark".
- Coins toward 13, and `cutter-blue-3b` is in hand or is the kept upgrade: +4 per coin above 8, and the game's value if she is already at 13 or can be handed 3 with placement impossible. Below 8, or without that card, her coins are the general eval's coins and this term is 0.
- Legend of the Skies unlocked, and the previous turn slot is a primary she has not yet copied this turn: +4 if that primary is currently legal and its target still exists (a hero on a dash landing, a hero on a lunge line, a minion adjacent for the plank's second bullet). 0 on turn 1, and 0 after she has already been asked this turn.
- Do not score "she might be holding the plank". That is a play.

### threatAdjust

When Cutter is the enemy:

- A hex adjacent to her, from which the away-ray crosses a zone border within 4 (3 on the handicap), is a discard or a defeat while `cutter-gold` or `cutter-handicap` is in her pool. Add +8, and the kill's weight if the hero who would stand there has 0 cards. A hex in the middle of a zone, with no border on that ray inside the push distance, does not get this. Add +10 if the plank is the known played card.
- While `cutter-red-1` / `2a` / `3a` is in her pool, a hero hex is threatened when some straight step of length 1..N from her leaves that hero in the next hex. Obstacles on the step block it. Add +8. A hex already adjacent to her is not on that bonus; it is only the no-bonus Attack 4/5/5.
- While `cutter-red-2b` / `3b` is in her pool, every hex in range 2 on a straight line is a shot, units between or not, and the hex she would step back into is where she will be afterwards. Add +5 for the shot. Do not assume a clear lane.
- While `cutter-green-1` / `2a` / `3a` is in her pool, a hex exactly 3, 3–4, or 3–5 away in a straight line, next to where an enemy hero could be, is a landing. Obstacles do not block it. A hex adjacent to her is not a landing. Add +6 for a hero standing where that landing can be chosen beside them.
- While `cutter-silver` is in her pool, the first obstacle on each line within 5 is where she can appear (on the near side of it). Add +4 to an enemy hero who is that first obstacle, because she arrives adjacent. Filling her preferred line on purpose is the counter, not an accident.
- While `cutter-blue-3b` is in her pool and her coins + 3 ≥ 13, every hex in radius 3 of her is a hex you do not want to be offered the choice from. Add +6. Paying is the loss.
- Bulwark and "cannot be pushed / placed" delete the plank and the placement. Do not add those threats against a hero covered by them.

## 7. Sources

- Catalog `cutter.json` (2nd printing), including the `advice` field. Cited as [ADVICE]. FAQ in the same file: Grappling Bolt targets any obstacle, immune units, and the edge water.
- `CutterHero.kt` and `core/game/rulings/cutter.md`: the plank's zone test, the lunge that moves before it knows if a hero is in front, the backstep after a defeated minion, the dash as presence, the enemy's choice on the coin cards, Legend of the Skies spending its ask on the decline.
- [BGG 3790096] Cryomancy, 2026-10-29, "Cutter Hero Spotlight (2nd Printing)", community. The plank as a shove, the grapple anchors including the twins' cradle, Barrage as the initiator, the dash into a follow-up, Outmaneuver handing minions across, X Marks as an enemy-choice card, and the "playing against" section. Post 3, 2026-11-02, community: X Marks into a slow hero who is about to swap (Mortimer). Post 4, Cryomancy, 2026-11-03, agrees. This thread is newer than the 2026-10-24 pass in STRATEGY.md.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Cutter as Initiator / Pusher. The same label is the header of 3790096.
- [BGG 2893527] ShowaKun, 2022-07-03, "Cutter Hero Spotlight", 1st printing. Used only where the mechanism is still on these cards: a turn-3 dash that leaves her adjacent sets up a turn-4 plank; forcing a step off the lunge line is value; the ultimate is worth one extra primary, not a seven-action round. Not used: push 3, +3 Attack, optional lunge onto a unit, silver's defense-move, Mediocre / Pathetic, coin-space blue, Giant Killer, Tiny Dancer.
- [BGG 2895440] 2022-07-06, community and a playtester reply: grappling as a defense moves her, then the attack still lands. 1st-printing card. Not used. This silver's defense is Defense 2 and does not move.
- Gamefound, Wolff Designa, "Defiant" hero pack overview, 2023-05-07: https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/7 — publisher. She has the most discard effects, Grappling Bolt is the mobility, the plank needs an exact spot, she is an initiator. Matches this printing. The "up to 4 of 5 cards" line matches gold, red, primary blue and primary green all being able to discard or defeat.
- https://www.cockeyedandcurious.com/goa2/heroes/cutter — records two designer Discord rulings already in the FAQ and the engine: water is a grapple target (Arty, 2025-02-16); the dash's "adjacent to an enemy hero" is presence, so an immune hero is a legal landing and then need not be the one who discards (Arty, 2025-03-28). No extra tactic is taken from that page.
- The BGG wiki page `guards-atlantis-ii-tabletop-moba-cutter` is 1st-printing text (push 3, Pathetic, coin spaces). Not used for numbers.
- Optimal Play's public playlist has a Cutter game (Cutter & Xargatha vs Sabina & Silverarrow) and no Cutter strategy video with a retrieved transcript. No rule is taken from it.
- AI.md "Known weaknesses": her ultimate is one of the per-turn plans the search player does not keep. Rule 13 is the correction.

No Cutter-specific Reddit strategy thread was found. The 2022 session report (r/boardgames `v5yeu2`) only places her in a lane.

## Implementation notes

What `heroes/CutterHeuristics.kt` encodes, checked against the engine (2026-11-21).

- Implemented: H1/H2 (plank: the best push across a zone border, a discard-or-defeat, or the adjacent minion it defeats; held when it has neither; its `CHOOSE_OPTION` scores each bullet and the push distance reaches the border), H4 (grapple early, not on turn 4), H5 (lunge: a straight step of 1..N then the hero in front at +2, else the plain adjacent hit; replaces the general strike estimate), H6 (dash: a straight 3..N ignoring obstacles to beside a hero, who discards), H8/H9 (Bombardment family: a hero in radius, beside another enemy unit and not beside her; Broadside's repeat). evalTerms: coins above 9 while A Fistful of Coins is in her round (which tier-III blue she kept is public; no hand is read).
- Engine: the lunge's adjacent bullet is always offered when a unit is adjacent; the draft's penalty for "no lunge line" punished a legal minion hit and was dropped.
- Engine: the push stops at the first obstacle (`pushAway`) and the discard needs `zoneOf` to change, so a border behind a unit does not count. The handicap pushes up to 3 and has no minion bullet.
- Skipped: H3 (grapple landing), H7 (swap), H10/H11/H16 (X Marks the Spot placement), H12 (backstep), H13 (Legend of the Skies), H14/H15 (branch choice), threatAdjust.
- Style: laneWeight is 1.0, not the proposed 0.60: 46.9% with 0.60, 54.2% (Wilson 44.2–63.8%) with 1.0 (strength 8, 96 games, 2026-11-21). Notes alone 51.0%, style alone 49.0%.
