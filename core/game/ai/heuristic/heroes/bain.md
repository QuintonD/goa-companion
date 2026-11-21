# Bain (`bain`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/bain.json`. Engine: `BainHero.kt`.
Rulings: `core/game/rulings/bain.md`. Complexity 2. Stats: Attack 5/5, Defense 4/6, Initiative 4/4, Movement 5/6.
Catalog roles: tactician, sniper, healer, farming, durable. One marker: `marker_bounty`.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). The Bounty marker adds one more Life counter on top of that kill; where a rule needs a number for "the marker is live this round", use +8. `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances. `CTX hero` needs a fact only this playbook tracks (who holds the marker, which branch is in hand).

The item you gain is the icon on the card you tuck, not the card you keep (`Flow.upgradeOnce`). Every "tuck X" below means that.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Support | He paints a target and pulls units. Solo kills lose to his action economy [BGG 3500265]. Catalog also calls him a sniper; the shot is how the mark and the lane happen. |
| idealDistance | 2 | `bain-red-1` is range 2 on a straight line. Raise to 3 after `bain-red-2a` or `bain-red-2b` is kept (both are range 3). Gold itself is adjacent. |
| aggression | 0.45 | Mark and farm. Dive only when an ally can take the marked hero, or when `bain-red-3b` can shoot the mark and a minion together. |
| laneWeight | 0.60 | Primary red is a minion tool that also denies the line. Kills are the team's, after the marker is out. |
| keepDefender | true | Defense stat 4. The real blocks are red (Defense 5–7) and blue (Defense 4–5). The green block has no printed Defense and fails unless someone else holds the marker. |
| upgradeOrder | see §3 | Red first for the line and, by tucking `bain-red-2b`, the Initiative item his gold needs. |

He wants a clear straight line onto a minion that an enemy hero does not want to stand on, and an enemy hero inside radius 4 after that shot so the marker can move. Lane and kill are the same plan only after the marker is on the hero the team is hitting.

Class label [BGG 3779890, Cryomancy, 2026-10-09]: Support / Initiator.

## 2. Card-by-card

Initiative band: Silver 13, Gold 11, Blue 9 then 10, Red 8 then 9, Green 4 then 3. His own Initiative stat is 4. Items add +1 to every card that has the icon. Silver has no Movement, so the primary does not relocate him.

### `bain-gold` Dead or Alive — Attack 4, Defense 2, Movement 1, Initiative 11, Radius 4

Adjacent attack. After it connects, he may give an enemy hero in radius 4 the Bounty marker. The holder spends 1 additional Life counter when defeated. Engine: attack, then optional `CHOOSE_UNIT`. The hero who already holds the marker is not offered (rulings §2). Giving it moves the one marker.

For: put the marker on the hero the team will hit. It also turns on the green block, the Vantage bonus, and the alt-red bounty shot. [BGG 3500265] unlike most golds, do not save it to finish on turn 4: a hero defeated by this attack cannot be marked, so the extra Life is lost. [ADVICE] the marker's threat is part of its value even before the kill. [Gamefound Defiant, 2023-05-07, publisher] an early death of the marked hero is expensive.

When: turn 1–2, once he is adjacent to a minion (or a hero he does not need to be the one to kill), and an enemy hero will still be in radius 4 after the attack. Initiative 11 beats typical reds, so the mark lands before most escapes. With `bain-purple-4` in play, the same give forces a discard.

When not: as the killing blow on the only enemy inside radius 4, if the marker is not already on someone. As travel (Movement 1). Defense 2 is a bad card to be caught holding as the only defender.

Handicap `bain-handicap` Side Quest replaces this in a 5-player game: Skill, Defense 1, Movement 1, Initiative 10, Radius 4. If he is adjacent to an enemy unit (presence, immune units count), he gives a hero in radius the marker — friend or enemy, never himself. No attack, and the give is not optional once the adjacency check passes. Do not plan a minion take on it. Do not play it when the only hero in radius is an ally, unless the block branch is dead without a marker and the extra Life on that ally is acceptable. With the ultimate in play, that ally discards.

### `bain-silver` Get over here! — Defense 2, Initiative 13, Range 4, Skill

Target a unit or a token in range 4 on a straight line with no obstacles between, and move it toward him until adjacent. An adjacent target is legal and stays put (rulings §4). Card-text movement of exactly distance − 1. A unit he may not move (Bulwark) is not offered. Engine: `CHOOSE_OBJECT` (unit or token). No Movement value, so the primary does not move Bain.

For: the card that separates good Bain players [BGG 3500265]. Pull an enemy hero onto a hex an ally's unresolved attack reaches. Pull an enemy hero off a minion. Pull a friendly minion out of an attack, or adjacent to Bain for his own defense modifier [Gamefound]. Pull an enemy minion to an ally who can take it. Pull a token off a Brynn obstacle count, or off his own crossbow line (tokens do not block the crossbow; units and terrain do). [ADVICE] save allies, disrupt, clear the wave faster.

When: the line is already there at the start of the turn. Initiative 13 acts before almost everyone, so this cannot catch a hero who moves later this turn. [REASONED]

When not: no useful target on a clear line. Pulling an enemy minion onto himself when nobody else will take it is the usual trap [BGG 3500265]. Defense 2 and no boots: a bad last card. Do not treat a 2023 ruling that Vantage Point lets this pull through an occupied neighbour [BGG 3123266, designer, 1st-printing question] as 2nd-printing text. Vantage is a movement action on its own card; this script still requires an empty line (`GetOverHere`).

### `bain-red-1` Light Crossbow — Attack 5, Defense 5, Movement 4, Initiative 8, Range 2, Ranged

A unit in range on a straight line with no units or terrain between. Tokens between do not block (rulings §5). Adjacent units are always in line. Engine: ranged attack with `clearShot`.

For: the farming shot and the area denial. [BGG 3500265] stand where a hero who stays on the line is hit, and a minion is the shot if they leave. The post that follows calls it a push card more than a kill card. Defense 5 is his best early block.

When: a legal target is in range. Initiative 8 loses to golds and most blues, so a hero with a faster card steps off. The value is often the hex they refuse.

When not: into a hero who still holds a Defense ≥ his Attack total, when a minion sits on another clear line. No shot through a unit or a terrain hex.

### `bain-red-2a` Heavy Crossbow — Attack 5, Defense 5, Movement 4, Initiative 8, Range 3, Ranged, item Defense

Same text, range 3. Tuck `bain-red-2b` and the item is Initiative, which is what his gold (11) needs against other golds. Keeping this card is the default hand.

### `bain-red-3a` Arbalest — Attack 6, Defense 6, Movement 4, Initiative 9, Range 4, Ranged, item Defense

Same text, range 4, Attack 6. Tuck `bain-red-3b` for a Movement item. This is the first primary red that threatens a defended hero without an Attack item.

### `bain-red-2b` Hand Crossbow — Attack 4, Defense 6, Movement 4, Initiative 8, Range 3, Ranged, item Initiative

Choose one: a hero in range who has the Bounty marker (no straight-line clause), or a unit adjacent to him. Engine: `CHOOSE_OPTION` then the attack. Minions are only the adjacent bullet. [BGG 3500265] the hunter card; the marker replaces the line restriction.

When not: as a minion plan from 2–3 hexes away. Attack 4 before items. Keeping this tucks `bain-red-2a` and grants Defense, which he wants if he is walking into melee for the adjacent bullet.

### `bain-red-3b` Hunter-Seeker — Attack 5, Defense 7, Movement 4, Initiative 9, Range 3, Ranged, item Movement

Choose one or both, on different targets, same two bullets. "Both" is offered only when two different units can be hit (rulings §8). Do both whenever a marked hero is in range and a different enemy unit is adjacent: the minion is a lane step (~15) and the hero is a real hit at Attack 5.

### `bain-green-1` Close Call — Movement 2, Initiative 4, Block

No printed Defense. Blocks only if a hero other than Bain holds the marker, and that hero then gives the marker to Bain. Bain holding it does not count [BGG 3676852, designer `nichik`, 2026-03-13; rulings §6]. The give is not Bain giving it, so `bain-purple-4` does not discard. While he wears it he is +1 Life if defeated, Vantage's bonus is off, the alt-red bounty shot is off, and a second block this round fails.

For: surviving one attack that beats every numeric defense. [BGG 3500265] upgrade off this card early if you stayed on the block branch, because wearing the marker is the cost.

When not: any attack his red (Defense 5) or blue (Defense 4) already stops. Any time he himself holds the marker, or nobody does — the block fails and he is defeated. As the action, when the marker is out and he may still be attacked: playing it spends the block. Movement 2 is the travel only when the block cannot fire.

### `bain-green-2a` Narrow Escape — Movement 2, Initiative 4, Block, item Initiative

Blocks on the same condition, then retrieves the marker (back to his supply, the kill bonus ends). He does not wear it. Keeping this card tucks `bain-green-2b` and grants Attack. Tuck this card (keep Vantage) to gain the Initiative item.

### `bain-green-3a` Perfect Getaway — Movement 2, Initiative 3, Block, item Initiative

Blocks on the same condition and leaves the marker where it is. This is the block that keeps the extra Life on the enemy. No primary beyond the defense text (`hasPrimary` false); the secondary is still Movement, so playing it as a move spends the block. Tuck `bain-green-3b` and the item is Radius (gold's 4, the blues' 3).

### `bain-green-2b` Vantage Point — Defense 3, Movement 2, Initiative 4, Skill, item Attack, `plus` Movement

Movement action that ignores obstacles (never ending on one). +1 Movement on this action only if a hero other than Bain holds the marker. The ignore is unconditional [BGG 3500265]. Engine: `movementAction`. Printed Movement 2, so 3 with the bonus, before a Movement item.

For: the default green. Line up a crossbow or a silver line on turn 3, or cross the jungle when tokens and minions block the walk. [BGG 3500265] the author takes this whenever tokens are thick, or when Bain is the initiator who marks and leaves the kill to the team. Keeping it tucks `bain-green-2a` and grants Initiative.

When not: as a substitute for the block on a turn he is the one being focused and the marker is already on an enemy. Defense 3.

### `bain-green-3b` High Ground — Defense 4, Movement 2, Initiative 3, Skill, item Radius, `plus` Movement

Same, +2 Movement while someone else holds the marker (printed 2, so 4 with the bonus). Keeping it tucks `bain-green-3a` and grants Initiative.

### `bain-blue-1` A Game of Chance — Defense 4, Movement 3, Initiative 9, Radius 2, Skill

An enemy hero in radius with 2 or more cards in hand chooses one of those cards. Bain guesses a colour he could still have in hand (hand, and facedown cards; face-up played, resolved and discarded colours are gone). Correct: that hero discards it. Wrong: Bain gains 1 coin. Engine: `CHOOSE_UNIT`, then the enemy's `CHOOSE_CARD`, then Bain's `CHOOSE_OPTION`. Fizzles with no such hero — then it is only a Movement 3 / Defense 4 card.

For: a discard or a coin, never neither, once a victim exists [BGG 3500265]. The enemy picks the card, so a hand of two colours is not a sure discard. A hand of one possible colour is.

When not: over a clear minion shot. Radius 2 is short. Do not guess a colour that is not in `guessable`.

### `bain-blue-2a` Dead Man's Hand — Defense 4, Movement 3, Initiative 10, Radius 3, item Defense

Same, wrong guess gains 2 coins. Keeping it tucks `bain-blue-2b` and grants Attack, which is what gold's 4 and alt red's 4 need. Initiative 10 beats typical reds.

### `bain-blue-3a` We're Not Done Yet! — Defense 5, Movement 3, Initiative 10, Radius 3, item Range

Same, and on a wrong guess he may repeat the whole action once or gain 2 coins. The repeat cannot repeat (rulings §7). Keeping it tucks `bain-blue-3b` and grants Attack. The Range item extends the crossbows and the silver.

### `bain-blue-2b` Drinking Buddies — Defense 4, Movement 3, Initiative 10, Radius 3, item Attack

He may have a hero in radius (never himself) retrieve a discarded card; if they do, he may retrieve one too. Empty discards are not offered. Engine: optional `CHOOSE_UNIT`, then that hero's `CHOOSE_CARD`, then his own retrieve. [BGG 3500265] a support heal that cannot start on himself. An enemy is legal only when no ally has a discard and Bain needs a card back. Keeping it tucks `bain-blue-2a` and grants Defense.

When not: with no discarded attack worth retrieving. Defense 4 is a fine spare block; do not spend it as a blank skill.

### `bain-blue-3b` Another One! — Defense 5, Movement 3, Initiative 10, Radius 3, item Attack

Same, plus end of turn: may repeat once (`CONFIRM`). The repeat sees cards discarded after this resolved, which is why it lines up with slower reds [BGG 3500265]. Cancelled if he is defeated first. Keeping it tucks `bain-blue-3a` and grants Range.

### `bain-purple-4` A Complicated Profession — Ultimate

After Bain gives a hero the marker (gold or Side Quest, including a friend on Side Quest), that hero discards a card if able. Not a defeat. Not when Close Call makes someone give the marker to him. Not if the same hero already holds it. One marker, returned at end of round and when its holder is defeated, not when Bain is defeated (FAQ).

## 3. Upgrade path

Red is the weapon. Green is either the chase (default) or the "block anything" plan. Blue is discard-or-coins, or the heal. [BGG 3500265] he is a support who struggles to solo-kill; [BGG 3500265 post 3, TheTomTraveller] alt green when he initiates, block green only with a real Initiative item, and he would not take the block without that item. A later player in the same thread was the slowest hero at the table and died to double golds before the marker and the block could come out: if he is the lowest Initiative stat in the game, take the first Initiative item before anything else.

Default (keep / tuck → item):

1. Tier II first: keep `bain-red-2a`, tuck `bain-red-2b` → Initiative. Range 3 line, and gold goes from 11 to 12. If he is already the slowest hero, do step 2 first instead.
2. Tier II second: keep `bain-green-2b`, tuck `bain-green-2a` → Initiative. Second Initiative item, ignore-obstacle chase. Switch: keep `bain-green-2a`, tuck `bain-green-2b` → Attack, only when an Initiative item is already tucked and enemies are focusing him.
3. Tier II third: keep `bain-blue-2a`, tuck `bain-blue-2b` → Attack. Gold's 4 and the alt red's 4 start to threaten. Switch: keep `bain-blue-2b`, tuck `bain-blue-2a` → Defense, when an ally is being stripped of attack cards.
4. Tier III first: keep `bain-red-3a`, tuck `bain-red-3b` → Movement, if he stayed on the line. If the team is killing the marked hero and someone else farms, keep `bain-red-3b` (both bullets), tuck `bain-red-3a` → Defense.
5. Tier III second: on the default path keep `bain-green-3b`, tuck `bain-green-3a` → Initiative (+2 Movement while the marker is out). On the block path keep `bain-green-3a` so the marker stays on the enemy; tuck `bain-green-3b` → Radius.
6. Tier III last: keep `bain-blue-3a`, tuck `bain-blue-3b` → Attack, unless he is on the heal, in which case keep `bain-blue-3b` and tuck `bain-blue-3a` → Range for the crossbow and the silver.

Alt red from tier II: keep `bain-red-2b`, tuck `bain-red-2a` → Defense (he will be adjacent for minions). Pair it with Vantage so the tucked green is his Initiative. Do not also expect the bounty shot before a marker is out.

Branch summary:

| Colour | Default keep | Tuck → item | Switch keep when |
|---|---|---|---|
| Red | `bain-red-2a` / `3a` | Initiative, then Movement | The team farms and he hunts the mark: `2b` / `3b` (Defense item). |
| Green | `bain-green-2b` / `3b` | Initiative, then Initiative | He is the focused finisher and already has Initiative: `2a` / `3a`. |
| Blue | `bain-blue-2a` / `3a` | Attack, then Attack | An ally needs attack cards back: `2b` / `3b` (Defense, then Range). |

Do not tuck the only Defense 5+ red. After `bain-red-2a` is in hand, blue's Defense 4 can be the spare.

## 4. Synergies and counters

Teammates: anyone who can hit the marked hero after his gold. He is the setup, they are the kill [BGG 3500265; Gamefound]. A pull onto an ally's unresolved red is the silver's best target. Token heroes clog his crossbow lines (units and terrain block; tokens do not) and are why he takes Vantage.

Enemies he fears: a faster gold pair that kills him before Initiative 11, which is how a first game on him fell apart [BGG 3500265 post 2]. Heroes who never stand on a straight line. Anyone who can kill him on the turn Close Call has just put the marker on him.

Playing against him (`threatAdjust` and the enemy plan):

- A clear straight line of length 2, then 3, then 4, with no unit or terrain between, is a shot even when he is not adjacent. Tokens do not break it.
- While `bain-red-2b` / `3b` is in his pool, the marked hero is in danger at range 3 with no line required, and at tier III an adjacent minion is a second attack.
- While `bain-silver` is in his pool, a unit or token on a clear line within 4 can be pulled adjacent to him. Do not finish your turn on that line if an ally of his has not acted.
- His green block does nothing while he wears the marker or nobody does [BGG 3676852]. Double-tap him the turn Close Call has just moved it onto him.
- The marker returns at end of round. A kill in a later round does not pay the extra Life unless he marked again.
- When he asks you to choose a card for the guess, show a colour he cannot be sure of, and not your only Defense 6, if you have a choice. That decision is yours, not his.

## 5. Common mistakes

- Saving gold for a turn-4 kill and defeating the only hero who could have worn the marker.
- Defending with Close Call / Narrow Escape / Perfect Getaway when he himself holds the marker, or when nobody does.
- Using Close Call against a hit his red already stops, then walking around worth +1 Life with the bounty shot and the Vantage bonus turned off.
- Playing Get over here! because it is Initiative 13, with no pull that helps, or yo-yoing an enemy minion onto himself.
- Shooting the crossbow through a unit or a terrain hex, or expecting the alt red's bounty bullet to need a clear line (it does not).
- Taking the block branch and never tucking an Initiative item, then losing the race to put the marker out.
- Guessing a colour that is already face up, or spending Chance over a minion when the victim still has three colours in the possible pool.
- Healing an enemy with Drinking Buddies so Bain can retrieve, when an ally had a discarded attack.
- Treating Vantage Point as a permanent "ignore obstacles" for the silver. It is this movement action only.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`bain-gold`] WHEN he is adjacent to an enemy unit he can attack and, after that attack connects, an enemy hero who does not already hold the marker would be in radius 4 THEN prefer gold over holding it for turn 4. WEIGHT +8 for the marker (it turns on block, Vantage's bonus, and alt red), +8 more if an ally who has not acted can reach that hero this round, +6 more if `bain-purple-4` is in play and that hero has at least 1 card. WHEN the attack would defeat the only enemy inside radius 4 and the marker is not already on someone THEN avoid gold for that target. WEIGHT −10. CTX hero (marker holder, ultimate in play). SOURCE [BGG 3500265] [ADVICE] [REASONED].

2. `CHOOSE_UNIT` [`bain-gold`, `bain-handicap`] WHEN giving the marker THEN prefer the enemy hero an ally can still attack this round, then the enemy with the fewest Life counters left. Never an ally on Side Quest if any enemy is in radius. With the ultimate in play, do not give it to an ally at all. WEIGHT +8 on the focus target, −10 on an ally while the ultimate is in play. CTX hero. SOURCE [BGG 3500265] [REASONED].

3. `DEFEND` [`bain-green-1`, `bain-green-2a`, `bain-green-3a`] WHEN a hero other than Bain holds the marker AND the attack total beats every numeric defense still in his hand THEN block with this card. WEIGHT +14 (he lives). On `bain-green-1` only, WEIGHT −8 on top because the marker moves onto him. WHEN he holds the marker, or nobody does THEN never defend with these. WEIGHT −20 (the block fails and he is defeated). CTX hero. SOURCE [BGG 3676852] rulings §6 [BGG 3500265].

4. `PLAY_CARD` [`bain-green-1`, `bain-green-2a`, `bain-green-3a`] WHEN someone else holds the marker and an enemy who has not acted can still attack Bain THEN do not spend the card as Movement. WEIGHT −8. WHEN nobody else holds the marker THEN it is only Movement 2, and a crossbow line is a better action if one is legal. WEIGHT −4 versus the shot. CTX hero. SOURCE [BGG 3500265] [REASONED].

5. `PLAY_CARD` [`bain-silver`] WHEN a unit or token within range 4 lies on a straight line with no obstacle strictly between THEN prefer the pull in the `CHOOSE_OBJECT` order of rule 6, and prefer this card over a Movement action that does not reach a crossbow line. WEIGHT the best pull in rule 6. WHEN no such target changes a hex an ally threatens, a minion's owner, or an obstacle count THEN avoid. WEIGHT −8. Initiative 13: score the board as it is now, not after later movers. CTX general. SOURCE [BGG 3500265] [ADVICE] [Gamefound] [REASONED].

6. `CHOOSE_OBJECT` [`bain-silver`] WHEN pulling THEN prefer, in order: an enemy hero whose landing hex is in an ally's unresolved attack; a friendly minion an enemy's unresolved attack still reaches; an enemy minion whose landing hex an ally can melee and Bain cannot; a token whose removal drops a hero below 3 obstacles or opens his crossbow line; a friendly minion moved adjacent to him if he is about to be attacked. WEIGHT +12, +10, +10, +6, +4. An enemy minion that lands adjacent to him and to nobody else: WEIGHT −6. Do not pull a unit onto a charge line of length 2–4 that ends next to a Brogan who still holds a charge. CTX general. SOURCE [BGG 3500265] [Gamefound] [REASONED].

7. `PLAY_CARD` [`bain-red-1`, `bain-red-2a`, `bain-red-3a`] WHEN a unit is in range on a straight line with no unit or terrain between THEN prefer that shot. WEIGHT +15 if the target is a minion in the Battle Zone, +10 if an enemy hero is on the same line and will have to leave it (they still hold a card that can move), the kill if his Attack total meets their Defense and they have 0 cards. A token between does not cancel the line. No line: WEIGHT −8, do not play it. CTX general. SOURCE [BGG 3500265] rulings §5 [REASONED].

8. `CHOOSE_OPTION` [`bain-red-2b`, `bain-red-3b`] WHEN a hero in range holds the marker THEN take the bounty bullet, not the adjacent bullet, unless the adjacent unit is a Battle Zone minion and the marked hero still has a card that stops Attack 4 (or 5 at tier III). On `bain-red-3b`, when both a marked hero in range and a different adjacent enemy unit exist THEN take both. WEIGHT +15 for the minion plus the hero hit (a discard if they have a card, the kill if they have none and the Attack total connects). CTX hero (marker). SOURCE [BGG 3500265] [REASONED].

9. `PLAY_CARD` [`bain-blue-1`, `bain-blue-2a`, `bain-blue-3a`] WHEN an enemy in radius has 2 or more cards and only one colour remains in the guessable pool THEN prefer the guess over a minion that is not the lane-deciding one. WEIGHT +10 (the discard is certain). WHEN two or more colours remain THEN the floor is the coins (1, then 2); do not play it ahead of a clear Battle Zone minion shot. WEIGHT −6 against that shot. No victim: do not play it for the skill. CTX general. SOURCE [BGG 3500265] rulings §7 [REASONED].

10. `CHOOSE_OPTION` [`bain-blue-1`, `bain-blue-2a`, `bain-blue-3a`] WHEN guessing THEN if hands are known, name the shown card's colour. Otherwise name the colour with the most cards still in the guessable pool; ties go red, then blue, then gold, then green, then silver. Never a colour outside that pool. CTX general (the pool); know-hands is the strength-10 case. SOURCE rulings §7 [REASONED].

11. `CHOOSE_OPTION` [`bain-blue-3a`] WHEN the guess was wrong THEN repeat if that hero still has 2 or more cards and the guessable pool is now a single colour, or they would be on 1 card after a hit. Otherwise take the 2 coins. WEIGHT the coins are 2; the repeat is rule 9's discard. CTX general. SOURCE [BGG 3500265] [REASONED].

12. `CHOOSE_UNIT` [`bain-blue-2b`, `bain-blue-3b`] WHEN an ally in radius has an attack card in the discard THEN choose that ally, then retrieve Bain's own best attack if he has one. WHEN only an enemy has a discard THEN decline, unless Bain's discard holds a red or blue and he has no numeric defender in hand. WEIGHT +6 for an ally's red back, +4 for Bain's own red back, −6 for retrieving an enemy. CTX general. SOURCE [BGG 3500265].

13. `CONFIRM` [`bain-blue-3b`] WHEN, since this card resolved, a friendly hero discarded an attack card that is still in a discard THEN repeat. WEIGHT +6. Otherwise decline. CTX general. SOURCE [BGG 3500265].

14. `CHOOSE_SPACE` [`bain-green-2b`, `bain-green-3b`] WHEN moving THEN prefer a hex that puts a Battle Zone minion on a clear crossbow line, else a hex that puts an enemy on a clear silver line, else the hex that most reduces distance to the Battle Zone. Ignore obstacles on the way; do not end on one. The +1 / +2 Movement exists only while someone else holds the marker — do not plan the longer walk without it. CTX hero for the bonus, general for the line. SOURCE [BGG 3500265] [REASONED].

15. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN red and green are still tier I and he has no Initiative item THEN keep `bain-red-2a` and tuck `bain-red-2b` (Initiative), unless his Initiative stat is the lowest in the game, in which case keep `bain-green-2b` and tuck `bain-green-2a` first. WEIGHT +4 for each Initiative item on the later race between his gold and an enemy gold. Do not keep `bain-green-2a` until an Initiative item is already tucked. CTX general. SOURCE [BGG 3500265] [REASONED].

16. `KEEP_UPGRADE` [`bain-red-2b`, `bain-red-3b`] WHEN kept THEN set idealDistance to 1 for minions and 3 for the marked hero, and do not score a crossbow line. The bounty bullet does not need a clear line. CTX hero (branch flag). SOURCE [BGG 3500265].

17. `PLAY_CARD` [`bain-gold`] WHEN it is turn 4, the marker is already on an enemy, and an adjacent enemy has 0 cards and his Attack total meets their Defense THEN gold is the finisher and he should not spend the "may" on a different hero unless the ultimate would discard that other hero. WEIGHT the kill, plus the extra Life if the target is the holder. WHEN turn 1 and he is not adjacent to a minion a Movement 4 card can reach THEN do not open on gold. WEIGHT −6. CTX hero. SOURCE [BGG 3500265] [BGG 3092335] [REASONED].

18. `PLAY_CARD` [`bain-handicap`] WHEN he is adjacent to an enemy unit and an enemy hero in radius does not hold the marker THEN play it to mark that hero. WEIGHT +8, +6 more with the ultimate and a card in their hand. WHEN the only legal recipient is an ally THEN avoid. WEIGHT −8, and −10 more with the ultimate. CTX hero. SOURCE card text [REASONED].

### evalTerms

- Enemy holds the marker: +8 while any friendly hero who has not yet acted this turn can still attack them, because the next defeat costs one extra Life counter and the marker returns at end of round. +3 if nobody can attack them, for the cards it still turns on (block, Vantage bonus, alt-red shot). 0 if the round cannot reach a defeat and he is not on those branches.
- Bain holds the marker: −8. He is the extra Life, and `bountyInPlay` is false, so the block, the Vantage bonus and the bounty shot are off.
- No marker: 0. Do not score "he might mark later"; that is a play, not a piece.
- `bain-purple-4` in play does not add a standing bonus. The discard is scored when he gives the marker (rule 1).

### threatAdjust

When Bain is the enemy:

- A hex on a straight line from him of length ≤ his current primary-red range (2, then 3, then 4), with no unit or terrain strictly between, is a shot. Tokens do not break it. Add +6 while `bain-red-1` / `2a` / `3a` is in his possible pool, +10 if that card is the known played card.
- While a hero holds the marker and `bain-red-2b` / `3b` is in his possible pool, every hex within range 3 of him that this hero stands on is threatened with no line. Add +8. At `bain-red-3b`, also treat a hex adjacent to him as a second attack when the marked hero is in range.
- While `bain-silver` is in his possible pool, a hex within 4 on a clear line is a pull-to-adjacent. Add +5 for an enemy hero standing there before he acts (he is Initiative 13 when this card is the one in the pool's fast slot).
- A hex of his that is adjacent to an enemy, with another enemy hero in radius 4, is where `bain-gold` wants to stand. Add +4 while gold is in the pool and the marker is not yet on an enemy.
- Do not add threat for the green block. It is a defense, and it is off while he wears the marker.

## 7. Sources

- Catalog `bain.json` (2nd printing), including the `advice` field from yvo-niedrich/goa2-faq. Cited as [ADVICE]. FAQ in the same file: the marker adds a Life counter if that enemy is defeated this round, returns at end of round, and does not return just because Bain is defeated.
- `BainHero.kt` and `core/game/rulings/bain.md` for which decisions exist and for the "a hero in play" reading (not Bain himself).
- [BGG 3500265] TheTomTraveller, 2025-04-21, "Bain Hero Spotlight 2nd Printing", community. Post 2 (2025-05-10) on being the slowest hero and failing to set the lines; post 3 TheTomTraveller, same day: alt green as initiator, block green only with heavy Initiative, straight-line red as a push card, solo kills are hard.
- [BGG 3676852] `nichik`, 2026-03-13, designer: Close Call / Narrow Escape do not block while Bain himself holds the marker.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Bain as Support / Initiator. Community.
- [BGG 3482463] 2025-03-21, community: markers return at end of round. Matches the FAQ; not a strategy source.
- [BGG 3123266] `nichik`, 2023-07-24, designer, on Vantage Point and Get over here!. 1st-printing question. Not used for 2nd-printing targeting; the engine's `GetOverHere` still requires an empty line.
- [BGG 2904317] GoatRogan, 2022-07-22, "Bain Hero Spotlight (1st printing)". Not used for numbers. The 2nd-printing spotlight replaces it.
- [BGG 3008835] 2023-01-15, the bounty FAQ question. The catalog FAQ is the text used.
- Gamefound, Wolff Designa, "Defiant" hero pack overview, 2023-05-07: https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/7 — publisher. Bounty raises the stakes; Get over here! pulls enemies, friendly minions, and a friendly minion for a defense buff; he wants a team that hunts heroes. Matches 2nd-printing text. The "healing" line matches Drinking Buddies, not a potion marker.
- [BGG 3092335] Panetierre, colour roles. General. Gold finishes or dodges; used only to say his gold is the exception when the marker is not out yet.
- Optimal Play, "Bain the Bounty Hunter" (YouTube, 2025-06-12): description only ("never underestimate the psychological effect of the bounty token"), which restates [ADVICE]. No transcript retrieved, so no rule is taken from it. A 2024 playthrough (Bain & Min vs Whisper & Arien) has no strategy text in the description.

No Bain-specific Reddit strategy thread was found. A 2022 4v4 session report (r/boardgames `v5yeu2`) names him in a lane and is not used.

## Implementation notes

What `heroes/BainHeuristics.kt` encodes, checked against the engine (2026-11-21).

- Implemented: H1/H18 (gold: the marker on an enemy in radius 4 who does not wear it, more when an ally can reach them, plus the ultimate's discard; Side Quest the same), H2 (marker recipient, told apart from the attack target by its prompt; never an ally), H3 (the green blocks are a last resort: Close Call hands Bain the marker, Narrow Escape takes it back; whether the block holds is the engine's `blockPreview`), H4 (keep the green block while someone else wears the marker and an enemy can still attack), H5 (Get over here! only for an enemy hero on a clear line, best with an ally close), H7 (the crossbow replaces the general strike with the clear-line targets only: no unit and no terrain between, tokens allowed), H8 (Hand Crossbow / Hunter-Seeker: the marked hero in range or an adjacent unit, both on 3b), H9/H10 (A Game of Chance by 1/colours; guess the colour the visible hand holds most, ties red, blue, gold, green, silver), H12 (Drinking Buddies helps an ally, not an enemy), H15 (keep `bain-red-2a`, tucking `bain-red-2b` for Initiative). evalTerms: the marker on an enemy +4, on Bain or an ally −4.
- Engine: Dead or Alive offers only enemy heroes in radius (`heroesInRadius(enemyOnly = true)`); only Side Quest can mark an ally.
- Engine: the guess options are already only the colours that could be in the victim's hand (`Chance.guessable`), and the victim picks the card, so a strength-10 hand count is a likelihood, not a certainty.
- Skipped: H6 (pull order), H11, H13, H14, H16, H17 (gold as turn-4 finisher is the general book's), threatAdjust (the general threat map already counts every hex in his range; a clear-line test would only lower it).
- Ablation (strength 8, 96 games, 2026-11-21): 58.3%, Wilson 48.3–67.7%.
