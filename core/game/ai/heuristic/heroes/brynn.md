# Brynn (`brynn`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/brynn.json`. Engine: `BrynnHero.kt`.
Rulings: `core/game/rulings/brynn.md`. Complexity 3. Stats: Attack 4/8, Defense 4/4, Initiative 7/7, Movement 5/6.
Catalog roles: tactician, damager, durable. No tokens of her own. The attack track has room (4 to 8); the defense track does not (4 to 4), so Defense lives on red and blue cards, and Attack items are worth taking.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances, and obstacle counts. `CTX hero` needs the red branch and whether `brynn-purple-4` is unlocked.

The item you gain is the icon on the card you tuck, not the card you keep. Attack items add to a card's Attack icon. They do not add to the "+2 attack" in the card text (`cardValue`: items never affect values in the card text).

The Gamefound Wayward overview (2023-05-18) describes a different Brynn: climbing, a damage-triggered move, potion markers. That text is not this printing. It is not used.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Fighter | Primary red is a melee brawl that becomes Attack 6–7 when the target is crowded. She also farms and she also sets the crowd up. Class label [BGG 3779890]: Finisher / Support. |
| idealDistance | 1 | `brynn-red-1` is adjacent, and she herself is one of the obstacles. Raise to 2 only while `brynn-red-2b` / `3b` is the red in hand. |
| aggression | 0.55 | She walks into the crowd when red or blue is still in hand. She does not walk in once those two are spent: Defense 4 and a silver of Defense 2. |
| laneWeight | 0.50 | A minion is both a coin and an obstacle. Taking the wrong one turns her own bonus off. |
| keepDefender | true | Red is Defense 6–7 on the primary branch, blue is Defense 5–6. Alt red drops that to 3 then 4. |
| upgradeOrder | see §3 | Primary red first. Alt red, if ever, is the last tier-II card. |

She wants an enemy hero with 3 or more obstacles in the six neighbouring hexes, and she wants to be the one who acts while that is still true. Obstacles are heroes (Brynn included), minions, tokens, terrain, and the water around the board. A space that counts as an obstacle for enemy heroes counts for her too [BGG 3482037, designer]. The fast count is "3 or fewer empty hexes adjacent" [BGG 3456595]. An immune hero never counts as obstacle-bound, including under the ultimate (FAQ; rulings §2).

On a melee attack she is one of the six. The +2 needs two other obstacles, not three [BGG 3456595].

## 2. Card-by-card

Initiative band: Gold 12, Blue 10 then 11, Red 9 then 10, Green 3 then 2, Silver 1. Her own Initiative stat is 7, so gold, blue and red are all fast. Green and silver act after almost everyone. A crowd she is counting on has to survive the golds that go before her red. A crowd she builds with silver (Initiative 1) is for the next turn's gold (Initiative 12), which then goes before those same people can leave.

### `brynn-gold` Familiar Ground — Attack 2, Defense 2, Movement 1, Initiative 12, Range 3, Ranged

Choose one: a unit adjacent to her, or a hero in range who is obstacle-bound. Both bullets are ranged attacks (rulings §4). A bullet with no target is not offered. Engine: `CHOOSE_OPTION`, then the attack.

For: the zoning card and the last hit, not the damage. [BGG 3456595] Attack 2 does not scare anyone until Attack items exist; the threat is the hex. Stand in range of a crowded hex the enemy wants, on turn 3 or 4. Initiative 12 acts before most movement, so a crowd that is already there gets hit before they walk.

When: a bound hero is in range 3, or an adjacent minion is the lane step and no bound hero is in range. Prefer the bound hero when they have 0 cards and 2 plus items meets their Defense, or when the shot is what keeps them off the hex.

When not: as travel (Movement 1). As a finisher into a hero who still holds Defense 3 or more, unless the only job is to make them spend a card. Defense 2 is a bad last card.

Handicap `brynn-handicap` Home Turf replaces this in a 5-player game: Attack 2, Defense 1, Initiative 11, Range 2, and both bullets target heroes only. No minion take.

### `brynn-silver` Decoy — Defense 2, Initiative 1, Radius 4, Skill

Choose up to two times, on different targets: move an enemy minion in radius exactly 1, or move an enemy hero in radius who is obstacle-bound exactly 1. The same bullet may be chosen twice. None is legal. A unit she may not move is not offered. Engine: up to two cycles of optional `CHOOSE_OPTION` then `CHOOSE_UNIT` then a 1-space move.

For: the setup card. [BGG 3456595] move a minion so a hero reaches 3 obstacles, then either move that hero 1 in the same play, or leave them crowded for the next turn. Also slide a minion to an ally who can take it, slide a hero off a minion so they spend a card to come back, or make a one-hex wall in front of a low-Movement card. [ADVICE] use it to help the team get minions.

When: late, because Initiative 1 is last. The board she moves is the board after everyone else. That is why the minion-then-hero combo is reliable on this card and not on her red.

When not: as a last card on turn 4 if she is the one who might be attacked. Defense 2. Do not move the hero off a 3-obstacle hex when the next turn's gold or trap wanted them to stay, unless the 1-space move puts them somewhere worse (off the minion, into a wall, out of their own charge line).

### `brynn-red-1` High Ground — Attack 4, Defense 6, Movement 4, Initiative 9

Adjacent attack. If the target is a hero and obstacle-bound, +2 Attack (card text, not an item). She counts as one obstacle, so the other two have to be real. Engine: the bonus is read when the target is chosen (rulings §3).

For: the brawl. Attack 4 is an ordinary hit; Attack 6 is the one that forces a red. Defense 6 is her best early block. [BGG 3456595] the bonus is uncommon and is the point of the hero. [ADVICE] the card still does its job when the condition fails; the unconditional part is the one to plan on.

When: adjacent to a minion, or adjacent to a hero who already has two other obstacles. Initiative 9 loses to golds and to blues at 10+, so do not count a crowd that only exists if nobody faster moves.

When not: walking past a Battle Zone minion to reach a hero who is not bound, hoping the +2 appears. Defense 6 is worth more in hand against Attack 6 than a dry Attack 4 is worth spending, if another card takes the minion.

### `brynn-red-2a` Elevated Ambush — Attack 5, Defense 6, Movement 4, Initiative 9, item Initiative

Same, Attack 5, so 7 when bound. Keeping it tucks `brynn-red-2b` and grants Defense, which a Defense-4 hero wants. This is the default red.

### `brynn-red-3a` Peak Precision — Attack 5, Defense 7, Movement 4, Initiative 10, item Radius

Same, and after the attack connects on a bound hero she may retrieve this card (`CONFIRM`). Not if the condition failed, and not if the attack did not connect [BGG 3686648, designer; rulings §6]. The card returns to hand, so it does not sit on this turn's slot. It can be the block or the move afterwards. Keeping it tucks `brynn-red-3b` and grants Defense. The Radius item is on this card, so she only gains it by tucking it (keeping the throw).

### `brynn-red-2b` Split Attack — Attack 3, Defense 3, Movement 4, Initiative 9, Range 2, Ranged, item Defense

A unit in range 2. If that target is a bound hero, she may repeat once on a different unit that is adjacent to her. The repeat is not any unit in range (that is the tier-III card). [BGG 3456595] the repeat almost never happens at tier II, because the second body has to be adjacent and the first has to be a crowded hero. Defense 3. Take this last among tier-II cards so the hole is short, and keep a blue of Defense 5 in hand. Keeping it tucks `brynn-red-2a` and grants Initiative.

The first target is the one that unlocks the repeat. Shooting the minion first means there is no second hit.

### `brynn-red-3b` Split Throw — Attack 3, Defense 4, Movement 4, Initiative 10, Range 2, Ranged, item Defense

Same, and the repeat may be any other unit in range, not only an adjacent one. This is the tier at which the branch starts to hit two things [BGG 3456595]. Defense 4 is still her whole defense stat and nothing more. [ADVICE] the ranged red makes her a better pusher and leaves her more open.

### `brynn-green-1` Bear Trap — Defense 3, Movement 2, Initiative 3, Radius 3, Skill

Choose one: an enemy hero adjacent to her discards, if able, or an enemy hero in radius who is obstacle-bound discards, if able. A hero with no card discards nothing and the action still happens (rulings §8). Engine: `CHOOSE_OPTION`, then `CHOOSE_UNIT`.

For: "do not stand next to me", and the ranged discard when the crowd is real. [BGG 3456595] allies who see it can walk up and become the third obstacle. A ranged hit is usually followed by gold next turn.

When: someone is already adjacent, or someone in radius is already bound. Initiative 3 is after the golds and the reds, so the crowd is the one they finished the turn on. That is the reliable window.

When not: as a blank Movement 2 when neither bullet has a target. Defense 3.

### `brynn-green-2a` Log Trap — Defense 3, Movement 2, Initiative 2, Radius 4, item Initiative

Same, radius 4. Keeping it tucks `brynn-green-2b` and grants Attack.

### `brynn-green-3a` Deadfall Trap — Defense 4, Movement 2, Initiative 2, Radius 4, item Initiative

Same choice, and the hero discards or is defeated. Adjacent, the kill does not need a crowd. In radius, it does. This is the finisher. Keeping it tucks `brynn-green-3b` and grants Movement.

### `brynn-green-2b` True Grit — Defense 3, Movement 2, Initiative 2, Radius 4, item Attack

She may retrieve a discarded attack card. The retrieve does not need a crowd. If an enemy hero in radius is bound, she then moves up to 3, which is more than this card's Movement 2. A facedown discard has no type, so it is not an attack (rulings §10). [BGG 3456595] new players miss the heal and only look for the move. Keeping it tucks `brynn-green-2a` and grants Initiative.

When: she, or an ally she cannot heal (this retrieves her own attack only), has a red or a gold in the discard. The move is the bonus when the crowd is up. Use the move to leave, or to land adjacent for the red, not to wander.

### `brynn-green-3b` Die Hard — Defense 4, Movement 2, Initiative 2, Radius 4, item Movement

Same, move up to 4. Keeping it tucks `brynn-green-3a` and grants Initiative, and gives up Deadfall's kill.

### `brynn-blue-1` Tread Lightly — Defense 5, Movement 3, Initiative 10, Radius 2, Skill

Swap with a unit adjacent to her (friend or enemy, hero or minion), or with a bound enemy hero in radius. Engine: `CHOOSE_UNIT`.

For: the defense card and the position card. [BGG 3456595] swap an ally out of an enemy red, or off a minion she wants for the wave. Swap with a bound enemy so an ally who was adjacent to her is now adjacent to them. With the bonus, cross ground as the wave moves. Harder to spend once she is on alt red, because this becomes the only good block.

When not: swapping a friendly minion into the enemy's reach, or swapping herself adjacent to two enemies with red already played.

### `brynn-blue-2a` Cover Tracks — Defense 5, Movement 3, Initiative 10, Radius 3, item Defense

Same swap, then she may move 1. Keeping it tucks `brynn-blue-2b` and grants Attack.

### `brynn-blue-3a` Hide Traces — Defense 6, Movement 3, Initiative 11, Radius 3, item Range

Same swap, then move up to 2 (the move is "up to", so 0 is legal, and it is not a "may" — she is asked for a destination). Keeping it tucks `brynn-blue-3b` and grants Attack. Initiative 11 is gold-speed.

### `brynn-blue-2b` Mountain Guide — Defense 5, Movement 3, Initiative 10, Radius 4, item Attack

She may move a friendly unit adjacent to her up to 2 (hero or minion). If a bound enemy is in radius, she then moves a different friendly unit in radius up to 2. The second move is not optional when the condition holds and a unit exists: with no second unit the action fizzles (`Guide`). Declining the first move lets the one friendly unit be the second. [BGG 3456595] the flashy use is moving an ally into an attack they could not reach, or sliding a minion off an enemy red. If the first move did not happen, the second may still happen. Keeping it tucks `brynn-blue-2a` and grants Defense.

When not: the condition is up, she has exactly one friendly unit she can move, and she takes the first move anyway. That fizzles the required second step.

### `brynn-blue-3b` Expedition Leader — Defense 6, Movement 3, Initiative 11, Radius 4, item Attack

Same, up to 3. Keeping it tucks `brynn-blue-3a` and grants Range (gold and the split reds).

### `brynn-purple-4` Over the Top — Ultimate

While she is performing actions, every enemy hero in play counts as obstacle-bound. Immune heroes still do not (rulings §12). It does not help another hero who is somehow resolving her card. It is not a standing aura between her actions: the check is inside her scripts.

Once it is unlocked, gold's range bullet, both trap bullets, the split's repeat, the long swap, the long move and the guide's second move are all on, against every targetable enemy hero, with no need to build a crowd.

## 3. Upgrade path

[BGG 3456595] the red she keeps decides the hero. First games stay on primary red, a brawler with Defense 6. Alt red is a pusher and a defense problem; if she takes it, it is the last tier-II upgrade, and she keeps primary blue so a Defense 5 card is in hand. Alt green is how she gets a discarded red back, not only a movement trick. [ADVICE] coordinate: allies who make tokens or move bodies are how the bonus happens. Named partners in that spotlight: Mrak, Tali, Mortimer, Trinkets, Wuk, Min.

Default (keep / tuck → item):

1. Tier II first: keep `brynn-red-2a`, tuck `brynn-red-2b` → Defense. Attack 5, or 7 when bound, and a Defense item on a hero whose track does not grow.
2. Tier II second: keep `brynn-blue-2a`, tuck `brynn-blue-2b` → Attack. The swap stays in hand at Defense 5, and the Attack item adds to the red's icon (not to the +2).
3. Tier II third: keep `brynn-green-2a`, tuck `brynn-green-2b` → Attack. Second Attack item, radius-4 discard. Switch: keep `brynn-green-2b`, tuck `brynn-green-2a` → Initiative, when she is the one being forced to discard attacks.
4. Tier III first: keep `brynn-red-3a`, tuck `brynn-red-3b` → Defense. The retrieve is the card that lets her hit and still block. She does not gain the Radius item on this path; that icon is on the card she is holding.
5. Tier III second: keep `brynn-green-3a`, tuck `brynn-green-3b` → Movement. Adjacent empty-hand heroes die with no crowd required.
6. Tier III last: keep `brynn-blue-3a`, tuck `brynn-blue-3b` → Attack. Defense 6 and Initiative 11.

Alt red, only when she cannot stand next to enemy minions or the fight is on top of an ally [BGG 3456595]:

1. Take it last at tier II. Keep `brynn-red-2b`, tuck `brynn-red-2a` → Initiative. Defense in hand on that card is 3.
2. Keep `brynn-blue-2a` (Defense 5 in hand). Do not also give up the swap.
3. At tier III, keep `brynn-red-3b` so the repeat is any second unit in range. That tucks Peak Precision and grants Radius, which widens the trap, the swap and the silver, not the throw (the throw is Range). If she would rather retrieve the melee red, she is not on this branch.

Branch summary:

| Colour | Default keep | Tuck → item | Switch keep when |
|---|---|---|---|
| Red | `brynn-red-2a` / `3a` | Defense, then Defense | She must fight at range: `2b` last at tier II, then `3b`. Ideal distance becomes 2. |
| Blue | `brynn-blue-2a` / `3a` | Attack, then Attack | An ally is the one who should be moved onto the target: `2b` / `3b` (Defense, then Range). |
| Green | `brynn-green-2a` / `3a` | Attack, then Movement | Her red is being discarded out of her hand: `2b` / `3b` (Initiative). |

Do not tuck the only Defense 6. On alt red, do not tuck the blue.

## 4. Synergies and counters

Teammates: token-makers and anyone who moves a body onto the target. Mrak, Tali, Mortimer, Trinkets, Wuk and Min are the list from the spotlight. An ally who walks up next to a hero is the third obstacle for a trap she has already shown. She returns the favour by sliding a minion into their reach with silver or with Mountain Guide.

Enemies she fears: anyone who kills the friendly minion that was completing the three, before her Initiative. A faster gold that simply leaves the hex. Focus once her red and blue are both on the table. Brogan's Bulwark, because Decoy cannot move what it cannot move.

Playing against her:

- Three obstacles is the whole kit. Step off the crowd before she acts. Her red is Initiative 9 and her gold is 12, so a card slower than that does not leave in time.
- She is one obstacle while adjacent. Two others, and the melee red is +2.
- Her silver goes last. Do not finish the turn one minion short of three if she has not acted and Decoy is in her pool: she can place that minion and then move you.
- Killing her friendly minion deletes a setup [BGG 3456595].
- Immune heroes are not a crowd for her, and the ultimate does not change that.
- Spaces that already count as obstacles for enemy heroes (Takahide's Spinning Blade, and the same kind of effect) count for her cards [BGG 3482037]. Standing in your own obstacle-aura is standing in her bonus.
- Alt red is Attack 3 and Defense 3 or 4. Block it with a blue. Do not stand adjacent as well, or the tier-III repeat reaches a second body with no further crowd.
- Once Over the Top is unlocked, every targetable enemy hero is bound for the duration of her action. Range 3 on gold and radius 4 on the trap no longer need a crowd.

## 5. Common mistakes

- Counting her as a bystander on her own melee attack. She is one obstacle. The bonus needs two more.
- Treating a missing crowd as a blank card. The adjacent attack, the adjacent discard, the retrieve and the swap all work without it [ADVICE].
- Shooting a minion first on Split Attack, then having no repeat.
- Holding silver until turn 4. Defense 2, and the setup was for a gold that should already have been played.
- Expecting a crowd built before the golds to still be there at Initiative 9.
- Upgrading to alt red early and walking into melee with Defense 3.
- Retrieving Peak Precision when the target was not bound, or when the attack did not connect.
- Taking Mountain Guide's first move when it is the only friendly unit and a bound hero is in radius. The second move then has nobody and the action fizzles.
- Using the 2023 Gamefound potion-and-climb text. Those cards are not in this deck.
- Playing for the +2 and never taking the minion that wins the lane.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`brynn-red-1`, `brynn-red-2a`, `brynn-red-3a`] WHEN she is adjacent to a hero who has at least 2 obstacles besides her THEN prefer this red. WEIGHT +8 on top of the attack, because Attack 4/5/5 becomes 6/7/7 and that is what forces a red block. WHEN she is adjacent to a Battle Zone minion and the hero is not bound THEN still take the minion with this card if no trap or gold has a bound target; the unconditional hit is the plan. WEIGHT +15 for the minion, and do not add the +8. CTX general. SOURCE [BGG 3456595] [ADVICE] [REASONED].

2. `PLAY_CARD` [`brynn-gold`, `brynn-handicap`] WHEN a bound enemy hero is in range and it is turn 3 or 4 THEN prefer the range bullet over an adjacent minion, if her Attack total (printed + items, not counting a text bonus this card does not have) meets their remaining Defense or they have 0 cards. WEIGHT the kill, or +6 if it only spends their last spare. WHEN the bound hero is out of range and a minion is adjacent THEN take the minion. Handicap has no minion bullet. CTX general. SOURCE [BGG 3456595] [REASONED].

3. `CHOOSE_OPTION` + `CHOOSE_UNIT` [`brynn-silver`] WHEN an enemy hero in radius has exactly 2 obstacles THEN move an enemy minion 1 onto one of their empty neighbours first, then stop if her gold or her trap is still in hand for next turn. WEIGHT +8 for leaving the crowd in place. WHEN no such card remains this round THEN take the second choice and move that hero 1 off a minion or onto a wall. WEIGHT +6. WHEN an ally could melee a minion she cannot THEN that minion is the move, even if no hero becomes bound. WEIGHT +10. Do not play silver as the last card if an enemy who has not acted can attack her and she holds no other defender. CTX general. SOURCE [BGG 3456595] [ADVICE].

4. `CHOOSE_OPTION` [`brynn-green-1`, `brynn-green-2a`, `brynn-green-3a`] WHEN an enemy hero is adjacent and has 0 cards THEN take the adjacent bullet. On Deadfall that is a defeat with no crowd. WEIGHT the kill. WHEN nobody adjacent has 0 cards and a bound hero in radius has 0 or 1 card THEN take the radius bullet. WEIGHT the kill, or +8 for stripping the last card. WHEN the only adjacent hero has 2 or more cards and a bound hero is in radius THEN take the radius bullet anyway: the adjacent one can spend a spare, the crowded one is the setup for gold next turn. WEIGHT +6. No target of either kind: do not play it. CTX general. SOURCE [BGG 3456595] [REASONED].

5. `PLAY_CARD` [`brynn-green-2b`, `brynn-green-3b`] WHEN an attack card of hers is in the discard THEN prefer the retrieve over a trap that would only hit a hero with 2 or more cards. WEIGHT +6 for a red back, +4 for a gold back. The move up to 3 or 4 is available only while a bound enemy is in radius; use it to become adjacent to a minion her red can take, or to leave two enemies. WEIGHT +4 for that landing. The retrieve still happens when the move does not. CTX general. SOURCE [BGG 3456595].

6. `CHOOSE_UNIT` [`brynn-blue-1`, `brynn-blue-2a`, `brynn-blue-3a`] WHEN swapping THEN prefer a bound enemy hero in radius whose hex is adjacent to an ally who has not acted and still holds an attack. WEIGHT +10. Else an ally who is the target of an unresolved enemy attack. WEIGHT +8. Else an enemy minion whose hex is in the Battle Zone and closer to an ally than her current hex. WEIGHT +6. Avoid a landing adjacent to two enemy heroes when red is already played. WEIGHT −8. CTX general. SOURCE [BGG 3456595].

7. `CHOOSE_UNIT` [`brynn-blue-2b`, `brynn-blue-3b`] WHEN a bound enemy is in radius THEN do not take the first move unless a different friendly unit can still be moved. Decline the first and use the second for the one unit, if there is only one. Prefer a friendly hero who would then be adjacent to an enemy they can attack, else a friendly minion slid off an enemy's attack or onto a hex an ally threatens. WEIGHT +10 for the ally's attack, +8 for the minion. WHEN no enemy is bound THEN the first move is the whole card; decline it if the unit is already where it should be. CTX general. SOURCE [BGG 3456595] `Guide` [REASONED].

8. `CONFIRM` [`brynn-red-3a`] WHEN the attack connected and the target was bound THEN retrieve. WEIGHT +6 (Defense 7 back in hand, or a second action later). Do not retrieve otherwise; the engine does not offer it. CTX general. SOURCE [BGG 3686648] [BGG 3456595].

9. `PLAY_CARD` [`brynn-red-2b`, `brynn-red-3b`] WHEN a bound hero is in range and a different enemy unit is a legal repeat (adjacent on Split Attack, in range on Split Throw) THEN target the hero first. WEIGHT +8 for the hero hit plus +15 if the repeat is a Battle Zone minion. WHEN no bound hero is in range THEN this is a range-2 Attack 3, and a primary-red melee minion is better if she is on that branch. On this branch it is the minion tool: WEIGHT +15, and do not add a repeat that cannot happen. Defense 3/4: do not be adjacent to two enemies after the shot if blue is already spent. CTX hero (which red is in hand). SOURCE [BGG 3456595] [ADVICE].

10. `CONFIRM` [`brynn-red-2b`, `brynn-red-3b`] WHEN the repeat is legal THEN take it if the second unit is a Battle Zone minion or a hero with 0 or 1 card. WEIGHT +15 or the kill. Decline a second hero who still holds Defense ≥ her Attack and is not the focus. CTX general. SOURCE [BGG 3456595].

11. `PLAY_CARD` [`brynn-red-1`, `brynn-red-2a`, `brynn-red-3a`, `brynn-blue-1`, `brynn-blue-2a`, `brynn-blue-3a`] WHEN both are still in hand and an enemy who has not acted can attack her for more than Defense 4 THEN do not spend both in the same turn. Keep one. WEIGHT +6 for the card that remains. Silver is not that card. CTX general. SOURCE [BGG 3456595] [REASONED].

12. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN red is still tier I THEN keep `brynn-red-2a` and tuck `brynn-red-2b` (Defense). Do not keep `brynn-red-2b` until blue and green are already tier II. WEIGHT +4 for the Defense item on a track that stays at 4. If alt red is kept, set idealDistance to 2 and require a Defense 5+ blue in hand before she ends a turn adjacent to an enemy hero. CTX hero (branch). SOURCE [BGG 3456595] [ADVICE].

13. `KEEP_UPGRADE` [`brynn-green-2b`, `brynn-green-3b`] WHEN she has discarded an attack card in each of the last two rounds, or two enemies are forcing discards THEN keep the heal branch instead of the trap. Tuck → Initiative. CTX general. SOURCE [BGG 3456595].

14. `PLAY_CARD` [`brynn-silver`] WHEN it is turn 4 and she holds no other card THEN avoid, if any move or gold can still take a minion. A setup with no later card is a Defense 2 spent on a 1-space shove. WEIGHT −6. WHEN it is turn 3 and gold is still in hand THEN prefer silver if a hero can be brought to 3 obstacles. WEIGHT +8, scored on next turn's gold at ×0.6 because a later enemy gold (Initiative above 1, which is all of them) can still walk before her gold only if they play gold next turn too. If their gold is already spent, do not discount. CTX general. SOURCE [BGG 3456595] [REASONED].

15. `CHOOSE_SPACE` any WHEN she is placing or moving herself and `brynn-red-1` / `2a` / `3a` is still in hand THEN prefer a hex adjacent to an enemy hero who would then have 2 or more obstacles besides her, else a hex adjacent to a Battle Zone minion. On the alt red, prefer range 2 from that hero and not adjacent to two enemies. CTX hero (branch). SOURCE [BGG 3456595] [REASONED].

### evalTerms

Count obstacles the way `BrynnHero.obstaclesAround` does, including spaces that count as obstacles for her. Do not count an immune hero as bound.

- A bound enemy hero in range of a card she still holds: +4 if that card is gold or a trap, +6 if it is primary red (the +2 is live). 0 if the only such card is already played this turn.
- Over the Top unlocked: while it is her action, every targetable enemy hero is bound, so the bonuses above apply without a crowd. Between her actions the ultimate adds 0; it does not pin them.
- Do not score a crowd that includes a friendly minion an enemy who acts before her can take, unless that enemy has already acted. The bonus is worth half (+2 / +3) until their card is resolved.
- No token of her own to score.

### threatAdjust

When Brynn is the enemy:

- A hex with 3 or more obstacles, inside range 3 of her while `brynn-gold` is in her pool, is a shot at Initiative 12. Add +6. She does not need to be adjacent.
- A hex adjacent to her, where the hero standing there already has 2 other obstacles, is her primary red at +2. Add +8 while `brynn-red-1` / `2a` / `3a` is in her pool. The hex adjacent to her with fewer than 2 other obstacles is only Attack 4/5/5.
- While `brynn-red-2b` is in her pool, range 2 is Attack 3, and a repeat exists only back onto a hex adjacent to her. While `brynn-red-3b` is in her pool, the repeat is any other hex in range 2 once the first target is a bound hero. Add +5 for the second hex.
- While `brynn-silver` has not acted (it is Initiative 1), a hero on 2 obstacles inside radius 4 should treat every empty neighbour a minion can step into as already filled. Add +4 to staying there.
- While `brynn-green-3a` is in her pool, a hex adjacent to her is a defeat for a hero with 0 cards, crowd or not. Add the kill's weight to that hex.
- Over the Top unlocked: every hex in gold's range 3 and in the trap's radius is the bound version. Add the gold and trap figures above with no obstacle test. Immune heroes stay exempt.
- A space that counts as an obstacle for enemy heroes is an obstacle in this count [BGG 3482037].

## 7. Sources

- Catalog `brynn.json` (2nd printing), including the `advice` field. Cited as [ADVICE]. FAQ in the same file: an immune hero is not a legal status check, so the crowd condition never sees them.
- `BrynnHero.kt` and `core/game/rulings/brynn.md`: when the count is read, Peak Precision's retrieve, the split's two different repeats, Decoy's "up to two", Mountain Guide's second unit, Over the Top's scope.
- [BGG 3456595] TheTomTraveller, 2025-02-07, "Brynn Hero Spotlight 2nd Printing", community, with GoatRogan named as a reader. The three-empty-hex count, the melee "she is one obstacle", silver's minion-then-hero line, primary red for the first games, alt red last and only with a defense plan, alt green as the heal, and the teammate list. Post 2 is thanks, no extra rules.
- [BGG 3482037] `nichik`, 2025-03-21, designer: spaces that count as obstacles for enemy heroes count for Brynn's cards. Takahide's Spinning Blade is the example; a later reply names Wasp's silver as the same kind of case.
- [BGG 3686648] `nichik`, 2026-04-01, designer: Peak Precision's retrieve happens only when the condition is met.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Brynn as Finisher / Support.
- Gamefound, Wolff Designa, "Wayward" hero pack overview, 2023-05-18: https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/9 — not used. The publisher marks the cards as unfinished, and the abilities described (climb, potions) are not on these cards.

No 2nd-printing Brynn build thread beyond the spotlight was found, and no transcribed video. The forum scan on 2026-11-20 found no later Brynn strategy thread.

## Implementation notes

What `heroes/BrynnHeuristics.kt` encodes, checked against the engine (2026-11-21).

- Implemented: H1 (primary red: +2 Attack on a bound adjacent hero, replacing the general strike estimate), H2 (gold: an adjacent unit, or a bound hero in range; its `CHOOSE_OPTION` scores each bullet), H4 (traps: an adjacent enemy hero, or a bound one in radius; or-defeated on Deadfall; the same bullet scoring), H8 (Peak Precision takes itself back), H12 (keep primary red), H14 (no turn-4 Decoy while other cards remain). threatAdjust: next to her with three obstacles around is her red at +2.
- Engine: "bound" is `BrynnHero.obstaclesAround(hero, forHero = brynn) >= 3`, Brynn and spaces that count as obstacles for her included; with Over the Top every enemy hero counts as bound while she performs actions, so her own decisions treat all enemies as bound once it is unlocked.
- Engine: "Choose one —" asks only when both bullets have a target (RULINGS 38); index 0 is the adjacent bullet, 1 the bound one.
- Skipped: H3 (Decoy), H5 (True Grit), H6/H7 (swaps, Mountain Guide), H9/H10 (Split), H11, H13, H15, evalTerms (the crowd changes with every move; scored at the decision instead).
