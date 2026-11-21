# Brogan (`brogan`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/brogan.json`. Engine: `BroganHero.kt`.
No `core/game/rulings/brogan.md`; the Brogan entries are in `core/game/RULINGS.md` (19, 31, 36, 49, 50).
Complexity 1. Stats: Attack 7/7, Defense 8/8, Initiative 1/1, Movement 2/4.
Catalog roles: durable, disabler, pusher, farming. No tokens.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances. `CTX hero` needs a fact only this playbook tracks (the charge branch, whether One Man Army is in play).

The item you gain is the icon on the card you tuck, not the card you keep (`Flow.upgradeOnce`). "Keep X, tuck Y → item" means that.

1st-printing Brogan is a different kit. Silver was choose-one and lasted the round; primary green denied the enemy's coins; alt green retrieved a card; alt red was a range bonus. None of that is used below. Card text wins. Where a 2nd-printing guide names an item, it has been checked against the tucked card's `item` field.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Tank | Defense 8 and Bulwark. He is also the Pusher: the charge and the minion are the same job. Class label [BGG 3779890]: Zoner / Pusher. |
| idealDistance | 2 | `brogan-red-1` cannot attack a unit he started adjacent to. He must move 2 or more in a straight line. Gold wants 1, after the charge has already been spent or blocked. |
| aggression | 0.75 | [ADVICE] if they focus him, they are not hitting his allies. He can stand in the wave. Lower only when red and blue are both already played. |
| laneWeight | 0.70 | The center minion is called the Brogan minion because this hero takes it [BGG 3503843]. Kills are the fork on top of that take. |
| keepDefender | true | Hold red (Defense 7–8) or blue (Defense 5–6). Silver is Defense 4 and is the heal, not the wall. |
| upgradeOrder | see §3 | Primary red, with blue's push first so the charge has a target. |

He wants to end the turn on a hex from which two straight lines of length at least 2 each reach an enemy unit, or adjacent to an enemy hero and a minion so the next card forks both. A charge that can only go one way is a card the enemy deletes by stepping toward him.

## 2. Card-by-card

Initiative band: Silver 12, Gold 11, Blue 8 then 9, Red 7 then 8, Green 6 then 5. His own Initiative stat is 1, so every race is lost until an Initiative item is tucked. Items add +1 to every card that has the icon. [BGG 2432019, designer] three Initiative items and he outruns a Tigerclaw who took none. One is the practical ask; three makes him act before the people who were supposed to body-block him.

### `brogan-gold` Onslaught — Attack 4, Defense 3, Movement 1, Initiative 11

Adjacent attack. After it connects, he moves into the space the target occupied, if that space is empty and the move is legal. The step is mandatory when it is possible [FAQ; BGG 3533578, 2025-06-27: "if able" means the action does not fizzle when he cannot step, not that he may decline]. It does not happen when the attack did not connect (RULINGS 50), so a hero who blocks stays put and he stays put.

For: the minion take and the one-hex advance. [BGG 3479796] plan the step: onto a line for the red next turn, closer to the next target, or off a hex an enemy was about to charge. [BGG 3503843] a charge that was body-blocked on turn 3 is followed by this on turn 4, taking the minion and standing on its space. The rulebook's own example (p.12) is Brogan playing this at 11 to act before Dodger's red.

When: he is already adjacent, and the space he would inherit is one he is willing to stand on. Initiative 11 beats typical reds even with his stat of 1.

When not: as travel (Movement 1) when a charge can take the same minion and move 2+. Defense 3 is his worst block. Do not attack a unit whose empty space is surrounded by enemy heroes if he has no Defense 6+ left, unless that kill is the game: he cannot refuse the step.

Handicap `brogan-handicap` Leap Forward replaces this in a 5-player game: Attack 3, Defense 3, Initiative 10, Movement 1, and the target must be a hero. Same step. Do not plan a minion take on it.

### `brogan-silver` Bulwark — Defense 4, Initiative 12, Radius 4, Skill

He may retrieve a discarded card. This turn, he and friendly units in radius 4 cannot be moved, pushed, swapped or placed by enemy heroes. No Movement value. Engine: optional retrieve, then an active flag. The immunity is this turn, not this round (1st printing lasted the round; do not hold the effect across turns).

For: the heal, and the anchor. [BGG 3479796] after he has been hit, this is how he stands back up. It is also how an ally or a friendly minion stays on the hex a push was about to steal. [ADVICE] an attack card in hand is a threat; this is the card that lets him discard one and get it back.

When: he has a card in the discard he wants (a red or a blue), or an enemy who has not acted can displace him or a friendly unit in radius 4. Initiative 12 acts before almost every push.

When not: turn 1, with an empty discard and no displacement on the table. Playing it because it is fast spends Defense 4 and the heal. Enemies who see it know he will not move this turn; they walk to the hex the charge needed [BGG 3479796, "Playing against"]. Holding it is the default.

### `brogan-red-1` Mad Dash — Attack 6, Defense 7, Movement 3, Initiative 7

Before the attack, move exactly 2 spaces in a straight line to a space adjacent to an enemy unit, then target an enemy unit adjacent to him. If he cannot make that move, there is no attack (the card's reminder; FAQ). The landing may be next to an immune unit, and the attack then fizzles. He may charge through a smoke token, because the target is chosen after the move (FAQ). Engine: `moveSelf(2, 2, straightLine, endFilter = adjacent to an enemy)` then `adjacentAttackTarget`.

For: the attack and the mobility he does not otherwise have. Attack 6 at tier I is above a typical blue's Defense (the colour-role means in STRATEGY.md are about 4.3–5.3) and is why [BGG 3503843] treats a tier-I blue as not a reliable block. [BGG 3479796] he cannot hit a unit he started adjacent to. Enemies delete the card by stepping toward him so no 2-space line remains. The answer is a fork: two directions, so closing one leaves the other.

When: a 2-space straight line ends adjacent to an enemy unit, and at least one such landing is not a dead end. Initiative 7 loses to golds and blues, so the fork has to survive their movement.

When not: he is already adjacent to the only enemy and no 2-space line reaches another. Into an immune unit. Defense 7 is the card he holds when Attack 6+ is coming back at him.

### `brogan-red-2a` Bullrush — Attack 6, Defense 8, Movement 3, Initiative 7, item Initiative

Move 2 or 3. Keeping this tucks `brogan-red-2b` and grants Defense. The extra hex is what restores a fork after one line is blocked.

### `brogan-red-3a` Furious Charge — Attack 7, Defense 8, Movement 3, Initiative 8, item Movement

Move 2, 3 or 4. Attack 7. The printed Initiative rises from 7 to 8; that is the card, not an item. Keeping it tucks `brogan-red-3b` and grants Radius (Bulwark's 4, Bolster's 2). This is the charge that crosses a zone line.

### `brogan-red-2b` Throwing Axe — Attack 3, Defense 7, Movement 4, Initiative 7, Range 2, Ranged, item Defense

Choose one: a unit adjacent to him, or he may discard a card and, if he does, a unit in range 2. The card is Ranged, so the adjacent bullet is a ranged attack too (RULINGS 19): a "block a ranged attack" stops it. Engine: `CHOOSE_OPTION`, and the range bullet is `CHOOSE_CARD` (optional discard) then the shot. Attack 3 does not kill a hero who still holds a blue. [BGG 3479796] the consistent minion tool, and the discard is safe when Bulwark can retrieve it later. Keeping this tucks `brogan-red-2a` and grants Initiative, which is the reason to take the branch when every charge is being walked into.

When not: discarding his last Defense 6+ to enable a range-2 Attack 3 that is not a kill.

### `brogan-red-3b` Throwing Spear — Attack 4, Defense 7, Movement 4, Initiative 8, Range 2, Ranged, item Radius

Choose one: adjacent, or he may discard a card and then, if the discard pile is not empty, a unit in range. He can shoot without discarding on this action when a card is already in the discard. That is the difference from the axe. Keeping it tucks `brogan-red-3a` and grants Movement.

### `brogan-green-1` Shield — Defense 4, Movement 2, Initiative 6, Radius 1, Skill

This round, when a friendly minion in radius is defeated, he may discard a silver card. If he does, the minion is not removed. The enemy still gains the coins (the parenthetical is a rules reminder, and it still applies when the upgrade drops the sentence [BGG 3479796, Cryomancy, 2026-08-05]). Not during the Minion Battle: those minions are removed, not defeated (FAQ; RULINGS 49). Engine: the primary only starts the flag; the ask is `CHOOSE_CARD` at the moment of the defeat, with Decline.

For: flipping who wins the battle, or saving the minion that stops a last-wave push. Not for denying farm. The enemy is paid.

When: on turn 1 or 2 of the round, so the flag exists before the attacks. Initiative 6 loses to reds and golds played on the same turn, so the same-turn save does not catch a faster killer. Radius 1 means he has to be standing on the minion.

When not: to "stop their coins". To save a minion when his team already wins the battle without it. When discarding the silver spends the only heal and the battle was not the game. [BGG 3479796] the author uses it when it changes the winner, when the team wants to ignore the wave and hunt, or on the last wave.

### `brogan-green-2a` Bolster — Defense 5, Movement 2, Initiative 5, Radius 2, item Attack

Same, radius 2. Keeping it tucks `brogan-green-2b` and grants Defense. Tuck it (keep the drummer) to gain the Attack item.

### `brogan-green-3a` Fortify — Defense 5, Movement 2, Initiative 5, Radius 2, item Attack

Same radius, and the discard may be a basic card. Basic means Gold or Silver only (`isBasic`: tier null and that colour), not a red, blue or green. So he can spend gold and keep Bulwark, or spend Bulwark and keep gold. Keeping this tucks `brogan-green-3b` and grants Defense.

### `brogan-green-2b` War Drummer — Defense 5, Movement 2, Initiative 5, Range 4, item Defense

A friendly hero in range gains 1 coin, or 3 if any hero was defeated this round. Engine: `CHOOSE_UNIT` among friendly heroes in range. He is not in his own range (`unitsWithin`), so this does not pay him. Fizzles with no ally in range. [BGG 3479796] pay the ally who wants tier III, or the one who is behind. The bonus counts a dead ally too; do not die to turn it on. Keeping this tucks `brogan-green-2a` and grants Attack, which is what makes gold's 4 and the charge's 6 into kills. 1st printing's "overheal" comment [BGG 2886858] was about a different alt green. This card is coins.

### `brogan-green-3b` Master Skald — Defense 5, Movement 2, Initiative 5, Range 4, item Defense

2 coins, or 4 if any hero was defeated this round. Keeping it tucks `brogan-green-3a` and grants Attack.

### `brogan-blue-1` Brutal Jab — Defense 5, Movement 2, Initiative 8, Skill

He may move 1. Then he pushes an enemy unit or a token adjacent to him up to 1. The push needs a target or the action fizzles. Engine: optional `moveSelf(1)`, then `CHOOSE_OBJECT`, then `CHOOSE_NUMBER` 0..1.

For: the setup. [BGG 3479796] shove a hero who acts before him onto next turn's charge line, or shove a minion to an ally, or shove a hero off a minion and into an ally's red. Initiative 8 beats his own red, so on a turn he does not play red this is the card that goes before the slow attacks.

When not: with nothing adjacent to push. A push of 0 is legal and useless. Do not push a unit onto a straight line an enemy charge still holds.

### `brogan-blue-2a` Mighty Punch — Defense 6, Movement 2, Initiative 9, item Initiative

May move 1, push up to 2. Keeping it tucks `brogan-blue-2b` and grants Attack. Tuck it (keep Shield Bash) to gain the Initiative item.

### `brogan-blue-3a` Savage Kick — Defense 6, Movement 2, Initiative 9, item Initiative

Move up to 2 (0 is legal), then push up to 2. The move is not optional in the "may" sense; "up to" includes staying. Keeping it tucks `brogan-blue-3b` and grants Range, which only the axe and the spear use.

### `brogan-blue-2b` Shield Bash — Defense 6, Movement 2, Initiative 9, item Attack

An enemy hero adjacent to him who has played an attack card this turn discards a card, if able. "Played" is recorded at the reveal and is not forgotten if that card is later discarded or swapped (FAQ; RULINGS 36). It does not require that they have resolved the attack. Engine: `CHOOSE_UNIT` among adjacent enemies in `turnRecord.playedAttack`. Fizzles if none. Initiative 9 beats a typical red, so he can answer a red that was just revealed, if he himself played this card.

For: the third melee that does not spend gold or red. [BGG 3479796] the dream is to take their gold, bash, and gold them later when they cannot leave. Best when they are standing on a minion they came to take.

When not: as a guess, when no adjacent enemy still has an attack in the possible pool. After the reveal, if nobody qualifies, the card has already been spent. Keeping this tucks `brogan-blue-2a` and grants Initiative.

### `brogan-blue-3b` Counterattack — Defense 6, Movement 2, Initiative 9, item Range

Same, and a hero with no card is defeated. That is a kill, not a discard. Keeping it tucks `brogan-blue-3a` and grants Initiative.

### `brogan-purple-4` One Man Army — Ultimate

During the Minion Battle, while he is standing in that lane's Battle Zone, he counts as a heavy minion of his team (RULINGS 31; [BGG 3647189], 2026-01-18: outside the zone he adds nothing, and a defeat that keeps him out of the zone removes the bonus). He is removed last among that team's bodies. If the battle would remove him, he is not removed and his team loses the push: `pushDue` is set to the opponent, so the lane still moves, and he stays on the board (`OneManArmy.replace`). A margin that removes every real minion but does not reach him does not push, because he is still a minion in the count.

So the ultimate swings a battle he would have lost by the one body he adds. It does not cancel a push whose margin would remove that body too. It does not count from the jungle.

## 3. Upgrade path

[BGG 3503843] two builds, read with the tuck rule. [BGG 3479796] primary red is the iconic card and the one enemies must respect; alt red is what you take when they have learned to walk into the charge. Primary green is the card he does not need early; the drummer's tucked partner is the Attack item.

Default, Berserker (charge, and be the body they have to answer):

1. Tier II first: keep `brogan-blue-2a`, tuck `brogan-blue-2b` → Attack. The push grows to 2 and gold's 4 becomes 5.
2. Tier II second: keep `brogan-red-2a`, tuck `brogan-red-2b` → Defense. Charge of 2 or 3, Defense 8 in hand.
3. Tier II third: keep `brogan-green-2b`, tuck `brogan-green-2a` → Attack. Second Attack item (gold 6, charge 8 before the tier-III card). Switch: keep `brogan-green-2a` when he is the one standing on the wave and a saved minion changes the battle; that tucks Defense instead of Attack.
4. Tier III first: keep `brogan-red-3a`, tuck `brogan-red-3b` → Radius. Attack 7, distance 2–4, printed Initiative 8.
5. Tier III second: keep `brogan-green-3b`, tuck `brogan-green-3a` → Attack, if the coins are still the plan. Keep `brogan-green-3a` when the last wave needs a gold-or-silver save.
6. Tier III last: keep `brogan-blue-3b`, tuck `brogan-blue-3a` → Initiative. This is his first Initiative item on this path, and Counterattack kills an empty hand. Keep `brogan-blue-3a` only when he has already tucked an Initiative item another way and does not need the bash; the tucked item is then Range, which this path's red does not use.

Captain America, when charges are being body-blocked or the team needs him to threaten without standing on the line [BGG 3503843]:

1. Keep `brogan-blue-2b`, tuck `brogan-blue-2a` → Initiative.
2. Keep `brogan-green-2b`, tuck `brogan-green-2a` → Attack.
3. Keep `brogan-red-2b`, tuck `brogan-red-2a` → Initiative. Second Initiative item, and a range-2 shot.
4. Keep `brogan-red-3b`, tuck `brogan-red-3a` → Movement. He can shoot once a card is already discarded, and the Movement item is the mobility the charge used to provide.
5. Keep `brogan-green-3a` if the wave needs the save (tuck → Defense), else `brogan-green-3b` (tuck → Attack).
6. Keep `brogan-blue-3a`, tuck `brogan-blue-3b` → Range, so the spear is range 3. The guide's "Savage Kick acquires a ranged item" is this tuck.

Openings, round 1, from the same guide. They are plans for the first four cards, not a script:

- Standard: green (or blue, same Movement 2) onto a hex from which red's 2-space line hits the center Battle Zone minion. Then blue and gold to take a second step or a second minion.
- If the charge on turn 3 is blocked, gold on turn 4 takes the minion and he inherits the space [BGG 3503843].
- The twins pocket behind his own pair is a hex most heroes cannot reach on turn 2. Green there on turn 2, with silver still in hand, threatens to keep a twin from being removed. The killer still gets the coins.

Branch summary:

| Colour | Default keep | Tuck → item | Switch keep when |
|---|---|---|---|
| Red | `brogan-red-2a` / `3a` | Defense, then Radius | Every charge line is being stepped on: `2b` / `3b` (Initiative, then Movement). |
| Blue | `brogan-blue-2a`, then `3b` | Attack, then Initiative | He needs Initiative before tier III: keep `2b` first (tuck → Initiative) and give up the push. |
| Green | `brogan-green-2b` / `3b` | Attack, then Attack | He is the wave anchor: `2a` / `3a` (Defense). |

Do not tuck the only Defense 7. After Bullrush is in hand, blue's Defense 6 can be the spare. Silver stays in hand until it has a job; it is not an item.

## 4. Synergies and counters

Teammates: anyone who takes the minion his punch slides, or who hits the hero he shoved onto a line. He wants a partner in the jungle on round 1 so the center minion and the twins are both threatened [BGG 3503843]. The drummer prefers an ally whose tier-III card is the team's win condition.

Enemies he fears: heroes who can be adjacent to him before Initiative 7 and fill the 2-space line. Displacement that happens before Bulwark (he is slow unless he spends the silver early). A double tap that catches him with red and blue both already played: Defense 4 on silver and Defense 3 on gold do not stop Attack 6.

Playing against him:

- If he is forking you and another unit and one move blocks both lines, make that move. A wasted red is a turn his team does not get [BGG 3479796].
- Step toward him, not away. The charge's minimum is 2. Adjacent is safe from that card.
- When he plays Bulwark he is not moving. That is the turn to walk onto the hex his next charge wanted, or to put a second attacker on him before the heal comes back.
- Shield and Bolster do not stop your coins, and they do not work in the Minion Battle. Killing the minion still pays. What it stops is the body leaving the zone.
- One Man Army adds a heavy only while he is in that Battle Zone. A defeat, or a push that leaves him outside it, deletes the +1 [BGG 3647189]. A margin that would remove him as well still pushes; he just stays on the board.
- Alt red's adjacent bullet is ranged. A block-ranged card stops it (RULINGS 19).

## 5. Common mistakes

- Charging when the only enemy is already adjacent, and fizzling the attack.
- One charge line. The enemy steps onto it and the red is gone.
- Playing Bulwark on turn 1 to "do something" at Initiative 12.
- Treating Shield as a farm denial. The killer is paid, and the flag was not even up if he played it on the same turn as a faster attack.
- Discarding silver to save a minion his team did not need, then having no heal.
- Paying the drummer to himself, or playing it with no ally in range 4.
- Playing Shield Bash before anyone adjacent is going to attack, then fizzling after the reveal.
- Refusing Onslaught's step. He cannot. The choice is whether to swing at a unit whose space is a bad place to stand.
- Standing outside the Battle Zone with the ultimate up and expecting the +1.
- Importing 1st-printing silver (a round-long choice) or 1st-printing green (the enemy does not get coins). Both are false on these cards.
- Expecting Counterattack's item to be Initiative if he keeps Counterattack. The Initiative item is on Savage Kick, which he must tuck.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`brogan-red-1`, `brogan-red-2a`, `brogan-red-3a`] WHEN at least one straight line of length N (N = 2, or 2..3, or 2..4) ends on an empty hex adjacent to an enemy unit THEN prefer this red over gold. WEIGHT +15 if that unit is a Battle Zone minion, the kill if it is a hero whose Defense total loses to his Attack and who has 0 cards, +8 if it is a hero with 1 card. WHEN two such lines exist in different directions THEN add +6 (the fork; one body-block does not blank it). WHEN the only enemy is already adjacent and no such line exists THEN do not play it. WEIGHT −12. CTX general. SOURCE [BGG 3479796] [BGG 3503843] FAQ [REASONED].

2. `CHOOSE_SPACE` [`brogan-red-1`, `brogan-red-2a`, `brogan-red-3a`] WHEN the charge has several legal landings THEN prefer the landing adjacent to a Battle Zone minion, else the landing adjacent to a hero with 1 or 0 cards, else the landing that leaves a second enemy on a different line for a later card. Never a landing whose only adjacent enemy is immune. CTX general. SOURCE [BGG 3479796] FAQ [REASONED].

3. `PLAY_CARD` [`brogan-blue-1`, `brogan-blue-2a`, `brogan-blue-3a`] WHEN an enemy hero or a minion is adjacent, or one step away, and a push of the card's maximum would put that hero on a hex his red can charge next turn, or put a minion on a hex an ally threatens THEN prefer the punch over a charge that has only one line. WEIGHT +8 for the setup, +10 if the pushed minion is the one an ally could not reach. A push of 0 is WEIGHT −4 unless the move itself reached the next turn's line. CTX general. SOURCE [BGG 3479796] [BGG 3503843].

4. `CHOOSE_NUMBER` [`brogan-blue-1`, `brogan-blue-2a`, `brogan-blue-3a`] WHEN pushing THEN pick the distance that lands the unit on the charge line or on the ally's hex, not 0. Do not push an enemy hero onto a straight line of length 2–4 that ends adjacent to a different enemy who still holds a charge of their own. CTX general. SOURCE [BGG 3479796] [REASONED].

5. `PLAY_CARD` [`brogan-blue-2b`, `brogan-blue-3b`] WHEN an enemy hero is adjacent and that hero's possible pool still contains an attack, and they are adjacent to a minion or to Brogan THEN prefer this over spending red. WEIGHT +8 if they have 2 or more cards (the discard sticks and red is saved), the kill on `brogan-blue-3b` if they have 1 card and the reveal can show the attack. WHEN no adjacent enemy could legally have played an attack THEN do not play it. WEIGHT −10 (it fizzles). After the reveal, the condition is known: only heroes in `playedAttack`. CTX general. SOURCE [BGG 3479796] FAQ [REASONED].

6. `PLAY_CARD` [`brogan-gold`] WHEN he is adjacent to a Battle Zone minion and no charge line of the minimum length exists THEN prefer gold, and accept the step onto that minion's space. WEIGHT +15, plus +4 if that space is itself a charge landing for next turn. WHEN the inherited space is adjacent to two enemy heroes and he has no Defense 6+ left in hand THEN avoid that target. WEIGHT −8. CTX general. SOURCE [BGG 3503843] [BGG 3479796] [BGG 3533578].

7. `PLAY_CARD` [`brogan-silver`] WHEN he has a red or a blue in the discard, or an enemy who has not acted can push, pull, swap or place him or a friendly unit in radius 4 THEN prefer Bulwark. WEIGHT +8 for the retrieve of a red, +6 for the immunity on a unit that was about to be displaced off a minion. WHEN the discard is empty and no such displacement exists THEN avoid. WEIGHT −8. CTX general. SOURCE [BGG 3479796] [ADVICE] [REASONED].

8. `PLAY_CARD` [`brogan-green-1`, `brogan-green-2a`, `brogan-green-3a`] WHEN it is turn 1 or 2, he is in radius of a friendly Battle Zone minion, and removing that minion this round would lose the battle or the last wave THEN play it now, before the attacks. WEIGHT +15 minus 2 (the enemy's coins) minus 2.5 if he will have to spend silver, and on Fortify he may spend gold instead so the heal survives: subtract 2.5 for whichever basic he can spare. WHEN his team already wins the battle without that minion THEN do not play it. WEIGHT −6. Same-turn play does not catch an enemy who acts first. CTX general. SOURCE [BGG 3479796] FAQ [REASONED].

9. `CHOOSE_CARD` [`brogan-green-1`, `brogan-green-2a`, `brogan-green-3a`] WHEN a friendly minion in radius is defeated THEN discard the silver, or on Fortify the gold, only if that minion is the one that changes the battle result or stops a push. Otherwise decline. On Fortify, if both gold and silver are in hand and he has already been hit this round, discard gold and keep silver. WEIGHT the minion is +15 at an even lane; the card spent is −2.5; the enemy's coins are already theirs either way. CTX general. SOURCE [BGG 3479796] [REASONED].

10. `CHOOSE_UNIT` [`brogan-green-2b`, `brogan-green-3b`] WHEN an ally is in range THEN pay the ally who is furthest from their next level, else the lowest-level ally. WEIGHT the coins (1 or 2, or 3 or 4 if `heroDefeatedThisRound`). Do not play the card with no ally in range. CTX general. SOURCE [BGG 3479796].

11. `CHOOSE_OPTION` [`brogan-red-2b`, `brogan-red-3b`] WHEN an adjacent Battle Zone minion exists and no hero in range is on 0 cards THEN take the adjacent bullet and do not discard. WHEN the only minion is at range 2 and a card already in the discard is not his last Defense 6+ THEN take the range bullet. On the spear, if the discard pile is already non-empty, do not discard a defender to shoot. WEIGHT +15 for the minion, −6 for discarding the last Defense 7. CTX general. SOURCE [BGG 3479796] [REASONED].

12. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN blue is still tier I THEN keep `brogan-blue-2a` and tuck `brogan-blue-2b` (Attack) before upgrading red, unless every enemy who can reach him has a way to step onto his only charge line, in which case keep `brogan-blue-2b` and tuck `brogan-blue-2a` (Initiative) and plan to keep the axe. WEIGHT +4 for the Attack item on gold and the charge. CTX general. SOURCE [BGG 3503843] [REASONED].

13. `KEEP_UPGRADE` [`brogan-red-2b`, `brogan-red-3b`] WHEN kept THEN set idealDistance to 1 and stop scoring charge lines. Movement 4 on the card is the walk; the shot is range 2. CTX hero (branch flag). SOURCE [BGG 3479796].

14. `PLAY_CARD` any WHEN it is round 1 and he has not yet taken a minion THEN prefer a Movement 2 card onto a hex from which rule 1's line hits the center Battle Zone minion, over Bulwark and over gold. WEIGHT +6 for being on that hex at the end of the turn. CTX general. SOURCE [BGG 3503843] [ADVICE].

15. `DEFEND` [`brogan-red-1`, `brogan-red-2a`, `brogan-red-3a`, `brogan-blue-2a`, `brogan-blue-3a`] WHEN the attack is Attack 6 or more and silver is the other card in hand THEN defend with the red or the blue, not with silver, if he still has a way to be healed later or the hit would defeat him. Silver is the retrieve. WEIGHT +4 for keeping the heal. CTX general. SOURCE [BGG 3479796] [REASONED].

16. `REMOVE_MINION` [`brogan-purple-4`] WHEN he is in the Battle Zone and the battle would remove him THEN the engine already loses the push instead of removing him. Do not spend a card earlier in the round to "save" a push this margin cannot save. Do spend a move to be inside the zone when his one body ties or wins the count. WEIGHT +15 for standing in the zone when the count without him loses by 1, +0 when the count without him loses by more than 1 (the push happens anyway; he merely stays). CTX hero (ultimate in play, and only that lane's zone). SOURCE RULINGS 31 [BGG 3647189] [REASONED].

### evalTerms

- Bulwark in force this turn: +4 if an enemy who has not acted could otherwise push, pull, swap or place him or a friendly minion in radius 4. 0 if nothing on the table displaces.
- Shield, Bolster or Fortify in force this round: +15 if a friendly minion currently in radius is the body that swings the battle, and he still holds a legal discard (silver, or gold/silver on Fortify), minus 2.5 for that card. 0 if the team already wins without it, or he has nothing legal to discard. Do not subtract the enemy's coins here; they were never denied.
- One Man Army, and he is in that lane's Battle Zone: +15 when adding one heavy ties or wins a battle his team would otherwise lose. +0 when the enemy's lead is large enough to remove him as well (the push is lost to the enemy, and he stays — that is a placement, not a lane point). 0 outside the zone.
- Do not add a bonus for "he might be holding the charge". That is a play, not a piece. His Defense stat is already why the general exposure weight should sit low; the style's aggression is the lever.

### threatAdjust

When Brogan is the enemy:

- A hex adjacent to an enemy unit is threatened by his primary red when a straight line of length N (2, then 2–3, then 2–4) runs from his current hex to that landing, through empty hexes. The unit itself is not on the landing. Add +8 while `brogan-red-1` / `2a` / `3a` is in his possible pool, +12 if that card is the known played card. A hex adjacent to him is not threatened by that red.
- Two landings in different directions: both hexes get the bonus. Blocking one is not safety.
- While `brogan-red-2b` / `3b` is in his pool, range 2 in addition, and only if he can discard or (spear) already has a discard. Add +5. The adjacent hex is a ranged attack, not a charge.
- While `brogan-silver` is the known played card, do not plan a push, pull, swap or place on him or on a friendly unit in radius 4 for the rest of the turn.
- While One Man Army is in play, a hex of his outside the Battle Zone is the hex you want him on. Add +6 to the value of a displace or a defeat that leaves him outside the zone before the battle, when the +1 would have tied the count.

## 7. Sources

- Catalog `brogan.json` (2nd printing), including the `advice` field. Cited as [ADVICE]. The five FAQ entries in the same file: the Onslaught step is mandatory when the space is empty; the charge fizzles if the move cannot be made, including next to an immune unit, and it can pass a smoke token; Shield does not apply in the Minion Battle; Shield Bash checks "played", not "resolved".
- `BroganHero.kt`, RULINGS 19 (the axe's adjacent bullet is ranged), 31 (One Man Army), 36 (played an attack), 49 (Shield asks on each defeat, never the battle), 50 (no step if the attack did not connect). `isBasic` is Gold and Silver only, which is Fortify's discard.
- [BGG 3479796] Cryomancy, 2025-03-17, "Brogan Hero Spotlight (2nd Printing)", community. The fork, walking into the charge, Bulwark as the heal, Shield only when it changes the wave, the drummer, Shield Bash's dream sequence. Cryomancy, 2026-08-05, in the same thread: the enemy still gains the coins after the upgrade; the parenthetical is a reminder, not a clause that fell off. `nichik`, 2025-03-17, in the same thread: ultimates show up in a few percent of games. The ultimate is still specified here because the engine plays it.
- [BGG 3503843] Cryomancy, 2025-04-28, "Brogan Build & Go Guide", community. Berserker and Captain America, and the four round-1 openings. Item names in that guide match the tucked card, not the kept card; this file uses the catalog icons.
- [BGG 3533578] 2025-06-27, community, confirmed in the thread: Onslaught's "if able" does not make the step optional. The designer's general "if able" note in the same thread (the step is non-mandatory only in the sense that a failure does not fizzle the rest) agrees.
- [BGG 3647189] priggy and replies, 2026-01-18, community: One Man Army counts only in that Battle Zone.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Brogan as Zoner / Pusher.
- [BGG 2432019] `nichik`, 2020, designer: three Initiative items and Brogan outruns a Tigerclaw with none. General, not a build.
- [BGG 2973407] 2022-11-18, community: a charge's move happens before the attack, so a block that swaps his card still leaves him on the landing. Used only as timing, not as a tactic to seek.
- [BGG 2886858] Eric_McClain, 2022-06-20, 1st-printing spotlight. Not used. Silver, green, blue and the ultimate do not match these cards. The "overheal" reply in that thread is about the 1st-printing alt green.
- Optimal Play, "Brogan the Barbarian" (YouTube, 2023-09-01): description only ("charging and smashing, or hanging back to toss spears and perform songs"). No transcript retrieved. The date sits on the 1st printing, so no rule is taken from it.

No 2nd-printing Brogan thread newer than the 2026-08-05 coin clarification was found. The forum scan on 2026-11-20 did not turn up a later spotlight.

## Implementation notes

What `heroes/BroganHeuristics.kt` encodes, checked against the engine (2026-11-21).

- Implemented: H1 (the charge replaces the general strike estimate with the best unit beside a real landing: a straight move of exactly 2 / 2–3 / 2–4 through empty spaces, `MoveSpec(straightLine, endFilter = beside an enemy unit)` as the engine moves him; +1.5 when landings fork in two directions), H2 (the landing beside the best target), H5 (Shield Bash / Counterattack by the chance an adjacent enemy plays an attack; `playedAttack` once known), H7 (Bulwark only with a red or blue to retrieve), H12 (keep the push blue), H15 (block with red or blue, not Bulwark).
- Engine: `CardProfile` reads "Before the attack: Move 2 spaces …" as a 2-space pre-move, so the general book gives the charge reach 3 (4, 5) and counts units he already stands next to. The charge cannot hit those (FAQ, `BroganHero`), which is why the correction often makes the card worse, not better.
- Skipped: H3/H4 (push setups), H6 (gold step), H8/H9 (Shield/Bolster/Fortify), H10 (War Drummer), H11 (axe bullets), H13, H14, H16 (One Man Army), evalTerms, threatAdjust (the general reach already includes the pre-move).
