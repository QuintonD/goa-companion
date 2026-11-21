# Gydion (`gydion`) — heuristic playbook

2nd printing. Every number is the catalog icon on that card (`app/src/main/assets/catalog/heroes/gydion.json`). Attack and defense totals are that icon plus items and modifiers (`cardValue`); the dashboard stat pair is not added on top. The spellbook starts empty. No spell can be cast until `gydion-silver` has been performed (rulings/gydion.md 1; designer, BGG 3491113). Community strategy for Gydion is almost absent; where a 1st-printing or campaign sentence disagrees with the card, the card wins and that is marked.

## 1. Identity

| Field | Value |
|---|---|
| Role | `SetUp` (secondary `Ranged`) |
| Complexity | 4 (catalog) |
| `idealDistance` | 3 (most spells are range or radius 3; he is not a brawler) |
| Lane vs kill | `laneWeight` 0.40. He takes minions when a spell or the attack spell reaches one, and otherwise spends the turn on a spell that removes a card, a life, or a body. |
| `aggression` | 0.40 until the spellbook has been filled once, then 0.55 |
| `keepDefender` | true. Printed defense icons are 1–4 on gold, silver and blue, 2–3 on green, 6–7 on red. Hold a red if a real attack is coming. |
| `upgradeOrder` | see §3. Default first tuck is a movement or range item, not a second attack item. |

Profile bars (rulebook p.21, descriptive, not added to any icon): Attack 5→6, Defense 3→5, Initiative 3→3, Movement 1→4. What he can actually do is the icons. Movement icons on his deck cards are 2 or 3, so without `gydion-spell-expeditious-retreat`, `gydion-spell-dimension-door`, or a movement item on those icons he does not cross a zone in one action. Order is the card's initiative: greens print 4–5, blues print 9–10, gold and silver print 11 and 13.

Public counters on his hero view: `spellbook`, `spellsRemoved`, `wishCasts`. Removed spells are faceup. The spellbook's identities follow from "every spell minus the faceup removed ones" (rulings/gydion.md 3). Treat that as known. Only his own seat is shown the names; the heuristic may use the deduction.

## 2. Card-by-card

Casting removes the spell faceup first, then performs the spell's own action. Gold spells are basic. Coloured spells are not. Hold, Clear and Fast Travel are not offered on the spell (rulings/gydion.md 6). A spell whose first instruction cannot start is not offered (rulings/gydion.md 5). Items on Gydion apply to the spell's icons (rulings/gydion.md 4).

### Deck

`gydion-gold` Cantrip. Initiative 13, defense 1, skill. Choose one spell in the book: `gydion-spell-shocking-grasp`, `gydion-spell-magic-missile`, `gydion-spell-expeditious-retreat`. Play when the book holds one of those and the cast changes the turn: a basic attack into a minion, a 1-space shove, or a straight 5-move. Do not play it as a reload, and do not play it when the book is empty (the primary cannot start). It is a poor defender (defense 1). Keeping it is the fast basic attack, not a shield.

`gydion-silver` Prepare Spells. Initiative 13, defense 2, skill. Adds **all** spell cards, including tiers he cannot cast yet and `gydion-spell-wish`, and returns removed spells (BGG 3491113). Only he may look; everyone else can still deduce. Play the first time the book is empty, as early as a wave allows, almost always round 1. Play again when the spells he can cast are gone or the one he needs is removed. Do not play it in a round where Shield or Invulnerability must keep working: returning the spell cancels that Active effect (rulings/gydion.md 8). Defense 2 is a weak answer to a real attack. The card has no movement.

`gydion-red-1` Elementary Evocation. Initiative 7, defense 6, movement 3. Casts only `gydion-spell-burning-hands`. The early fight card and his best printed defender. Play when an enemy hero is adjacent to a unit he can attack, so the discard lands, or when he must walk 3 and still threaten. Do not play it with an empty book.

`gydion-red-2a` Lesser Evocation. Initiative 8, defense 6, movement 3, item **defense**. Casts Burning Hands or `gydion-spell-fireball`. Same job as tier 1, plus a shot that is illegal next to a friendly unit. Tuck the other branch when he is being focused (the defense item raises every defense icon).

`gydion-red-2b` Lesser Necromancy. Initiative 8, defense 6, movement 3, item **initiative**. Casts `gydion-spell-vampiric-touch` or `gydion-spell-create-undead`. The sustain and lane-repair branch. Tuck when the team is ahead on lives and he needs to act earlier.

`gydion-red-3a` Greater Evocation. Initiative 8, defense 7, movement 3, item **defense**. Adds `gydion-spell-sunburst`. The finisher branch once at least one other spell is removed or he has a range item; at range 0 Sunburst has no legal target (rulings/gydion.md 14).

`gydion-red-3b` Greater Necromancy. Initiative 8, defense 7, movement 3, item **radius**. Adds `gydion-spell-energy-drain`. Take when a Life counter is already spent, or when radius items will grow Banishment, Cloud Kill, Suggestion, Dominate, Power Word Kill, Polymorph and Dimension Door.

`gydion-green-1` Elementary Abjuration. Initiative 5, defense 2, movement 2. Casts only `gydion-spell-shield`. Play early in the round, before enemy basic attacks (golds, silvers, and his own gold spells if copied). Useless against reds. Do not play it the same round you will Prepare.

`gydion-green-2a` Lesser Abjuration. Initiative 4, defense 3, movement 2, item **initiative**. Shield or `gydion-spell-banishment`. Banishment is the displacement tool (place an adjacent unit or token anywhere in radius 3).

`gydion-green-2b` Lesser Conjuration. Initiative 4, defense 3, movement 2, item **attack**. `gydion-spell-find-familiar` or `gydion-spell-dimension-door`. The mobility and scaling branch. The attack item only helps spells that have an attack icon.

`gydion-green-3a` Greater Abjuration. Initiative 4, defense 3, movement 2, item **initiative**. Adds `gydion-spell-invulnerability` (immune to non-basic attacks this round). Pair with Shield only if he can cast both before he is hit; usually pick the immunity that matches the attacks still in enemy hands.

`gydion-green-3b` Greater Conjuration. Initiative 4, defense 3, movement 2, item **movement**. Adds `gydion-spell-cloud-kill` (an enemy hero in radius 3 discards a basic card, if able). The movement item adds +1 to every movement icon (the conjuration cards print 2). It is his only movement item.

`gydion-blue-1` Elementary Enchantment. Initiative 9, defense 3, movement 2. Casts only `gydion-spell-suggestion`. A forced 3-space straight move. Play when an enemy in radius can be moved off a minion, off him, or into a teammate. If nobody can move 3 in a straight line the spell is still cast and removed (rulings/gydion.md 24): do not cast it then.

`gydion-blue-2a` Lesser Enchantment. Initiative 10, defense 4, movement 2, item **defense**. Suggestion or `gydion-spell-dominate-person`. Dominate needs an enemy hero in radius 3 who has an enemy minion adjacent to them, in radius 3 of Gydion. That defeat gives coins and can push.

`gydion-blue-2b` Lesser Transmutation. Initiative 10, defense 4, movement 2, item **attack**. `gydion-spell-midas-touch` or `gydion-spell-disintegrate`. Midas pays 1 coin per two **other** removed spells, rounded down, so it pays 0 until two spells are already removed. Disintegrate removes an adjacent token or an adjacent enemy non-heavy minion.

`gydion-blue-3a` Greater Enchantment. Initiative 10, defense 4, movement 2, item **range**. Adds `gydion-spell-power-word-kill`. The range item also grows Magic Missile, Fireball and Sunburst. Power Word Kill is the reason to take this branch.

`gydion-blue-3b` Greater Transmutation. Initiative 10, defense 4, movement 2, item **attack**. Adds `gydion-spell-polymorph` (swap an enemy hero in radius 3 with a token or an enemy minion in radius 3). A second or third attack item. Take when the table is full of tokens (Mortimer, Mrak, Min, Tali, Wuk) or when he is farming coins and clearing tokens.

`gydion-purple-4` The Archwizard. Whenever he would add cards to the spellbook, he may cast `gydion-spell-wish` instead, if Wish is in the book and can be cast. Only Prepare adds cards. Nebkher performing Prepare is not "you" (rulings/gydion.md 11).

`gydion-handicap` Cheap Trick. Initiative 10, defense 1. Casts only Expeditious Retreat. In a handicap game it is a straight 5-move and nothing else. Do not treat it as Cantrip.

### Spells

`gydion-spell-shocking-grasp` (gold, basic attack 3). Adjacent unit; after a connecting attack, move the target up to 1. Use on a minion he should take, or on a hero who must be nudged off a spot. The shove does not happen if the attack misses.

`gydion-spell-magic-missile` (gold, basic attack 1, range 3). A unit in range and not adjacent. Attack 1 does not kill a hero. Use on a minion he is not standing next to, or to tag a hero who is out of melee. Do not cast it when every target is adjacent.

`gydion-spell-expeditious-retreat` (gold, basic movement 5, straight line only). His only early cross-zone move. Use to enter or leave the battle zone. It does not attack.

`gydion-spell-burning-hands` (red, attack 5). Adjacent unit; before the attack, up to one enemy hero adjacent to that target discards, if able. He may decline the discard. The discard happens even if the attack then misses (rulings/gydion.md 16). Best target is a minion or hero who has another enemy hero beside them. Attack 5 is his reliable melee number.

`gydion-spell-fireball` (red, attack 5, range 3). A unit in range that is not adjacent to him and not adjacent to a friendly unit (immune friendlies still count). Wait until allies have stepped off the target. Do not throw it into a clump of his own minions.

`gydion-spell-sunburst` (red, attack 0, range 0, +1 attack and +1 range per **other** removed spell, plus range items). Target a unit at **exactly** that distance (designer, BGG 3589662). With zero removed spells and no range item it cannot hit. Fuel it with casts and with Find Familiar before relying on it.

`gydion-spell-vampiric-touch` (red, attack 5). Adjacent unit; after a connecting attack he may retrieve one of his own discarded cards. The heal of a card (~2.5) on top of a minion or a hero hit. Requires a card in his discard and a hit.

`gydion-spell-create-undead` (red, skill, range 3). Respawn a friendly minion on an empty friendly spawn point in range in the battle zone, any type the spawn rules allow. Use when a spawn is empty inside the battle zone and the lane is about to be counted. Does nothing with no legal spawn.

`gydion-spell-energy-drain` (red, skill, range 3). An enemy hero in range discards a non-basic card, if able, and his team regains 1 spent Life counter. The hero must be targetable or the life is not regained (catalog FAQ; rulings/gydion.md 19). Cannot go above the team's starting life. Skip when no life is spent, unless the non-basic discard itself wins the turn.

`gydion-spell-shield` (green, skill). This round he is immune to basic attacks, until the spell returns to the book or he is defeated. Cast before golds and silvers resolve against him. It does not stop reds, blues, or coloured spells.

`gydion-spell-banishment` (green, skill, radius 3). Place an adjacent unit or token into an empty space in radius. Rescue an ally, pull a minion off a spawn, or park an enemy hero out of the fight. Not a defeat and not coins.

`gydion-spell-invulnerability` (green, skill). This round he is immune to non-basic attacks. The answer to reds. Same cancel rule as Shield.

`gydion-spell-dimension-door` (green, skill, radius 0, +1 radius per other removed spell, plus radius items). Place himself on an empty space at **exactly** that distance. Dead at radius 0 because his own space is not empty. This is how a movement-1 hero crosses the board once the book is partly spent.

`gydion-spell-find-familiar` (green, skill, radius 3). Place a Familiar token, then he may remove up to three other spells faceup, one at a time, and may stop. Those removals fuel Sunburst, Dimension Door and Midas. The Familiar is an ordinary obstacle, removed at end of round (rulings/gydion.md 22). Do not remove the spell he still needs this round. Do not remove Wish if `wishCasts` is 2 and he can win.

`gydion-spell-cloud-kill` (green, skill, radius 3). An enemy hero in radius discards a basic card, if able. Use to strip a gold or silver that is still in hand (deduced), especially a defender. If they have no basic card it does nothing but still spends the spell: do not cast it then.

`gydion-spell-suggestion` (blue, skill, radius 3). If able, an enemy hero in radius moves 3 spaces in a straight line. He chooses who and which line, among heroes who can actually move 3. Use to shove someone off a minion or into a kill. Casting with nobody able only burns the spell.

`gydion-spell-dominate-person` (blue, skill, radius 3). Target an enemy hero in radius, then defeat an enemy minion in radius adjacent to that hero. The minion step gives coins and can take the last minion. If the hero has no such minion the spell stops after the target step and the minion is not taken (rulings/gydion.md 23). Do not cast it for the lane unless the minion is there.

`gydion-spell-power-word-kill` (blue, skill, radius 3). Defeat an enemy hero in radius with no cards in hand. The check targets, so an immune hero cannot be chosen. This is a full hero kill with no attack total. Empty hand in radius 3 is the condition, not "low health".

`gydion-spell-midas-touch` (blue, skill). Gain 1 coin per two other removed spells, rounded down. A farm spell, not a tempo spell. Cast when the payout is at least 2 (four removed spells) or when those coins finish a level this round (each such coin ≈ 1.5).

`gydion-spell-disintegrate` (blue, skill). Remove an adjacent token or an adjacent enemy non-heavy minion. No coins (remove, not defeat). Use on a zombie, rock, smoke bomb, or a minion that must leave the lane when he cannot defeat it. Heavy minions are illegal.

`gydion-spell-polymorph` (blue, skill, radius 3). Swap an enemy hero in radius with a token or an enemy minion in radius. One instruction: only heroes who have such a partner are offered. Use to swap a dangerous hero with a minion he wants removed from the lane, or with a token that was zoning his team.

`gydion-spell-wish` (purple, skill). Cast any spell still in the book that can start. After the third time he has cast Wish, his team wins, once the inner spell has been performed (rulings/gydion.md 12). The inner spell is still removed. Wish itself is removed, so the next Prepare puts it back.

## 3. Upgrade path

Round 1, before any upgrade: play `gydion-silver` on turn 1 or 2 unless an enemy is already on him in his own zone (BGG 3493559: he is caught there when the skill matters). A lost minion in round 1 is cheaper than a whole round of dead cards. After the book is full, spend the other three turns on minions and on one spell that does not empty the book.

Phase by what the table is doing:

1. **First upgrade, he cannot reach the battle zone.** Keep `gydion-green-2b` (attack item is incidental) or, if he can already walk, `gydion-blue-2a` (defense item). Tuck the other as the item only when its icon is movement, range, radius or defense.
2. **Enemies kill with golds and silvers.** Branch green A toward Shield, then `gydion-green-3a` for Invulnerability if reds appear. Tuck initiative.
3. **His team is down a Life counter.** Branch red B to Energy Drain. Tuck radius (`gydion-red-3b`).
4. **Hands are being emptied (discards on the enemy team, or turn 4).** Branch blue A to Power Word Kill. Tuck range (`gydion-blue-3a`).
5. **He is safe and the book is being spent.** Branch green B. Find Familiar, then Door and Sunburst. Tuck movement (`gydion-green-3b`).
6. **Token heroes or a stuck lane.** Branch blue B for Disintegrate and Polymorph. Tuck attack only after a defense item exists.
7. **Default damage, allies can step off his targets.** Branch red A. Fireball at attack 5, range 3, then Sunburst once `spellsRemoved ≥ 1` or he has a range item. Tuck defense.

Item priority, highest first: movement, range, radius, defense, initiative, attack. Attack items do nothing for Shield, Door, Midas, the discards, or Power Word Kill.

Ultimate: once `wishCasts` is 2, the next Prepare is a win if Wish and one other castable spell are in the book. Before that, refill. Wish instead of a refill only when the inner spell wins a hero or a lane **and** a refill is not required to survive the round.

## 4. Synergies and counters

Teammates: a melee ally who leaves the space next to the target makes Fireball legal. A lane ally covers round 1 while he Prepares (BGG 3493559, the jungle-catch note is the enemy's plan; his team should not leave him alone there). Brogan and other bodies who stand in front buy the round Shield needs. He does not need a specific partner; he needs one turn of safety.

He fears: anyone who reaches his side zone on turn 1 before silver (Arien's long move, Misa's placement, Hanu). Discard-heavy heroes, because he has no built-in retrieve until Vampiric Touch and his defense icons are low outside red. Nebkher's Mind Grip casts from **his** spellbook and Nebkher is the caster (rulings/gydion.md 9; designer, BGG 3487018). Standing in Nebkher's range 5 with a live spell on the previous turn hands Nebkher that spell.

Against him (`threatAdjust` and the enemy playbook):

- Deduce the book. A faceup removed spell is gone until the next Prepare.
- If Shield is removed and not returned, basic attacks into him are worth nothing this round. If Invulnerability is out, non-basic attacks are worth nothing. Switch attack class. Do not spend the attack that matches the immunity.
- Do not be the last card in hand inside radius 3 while Power Word Kill is still in the book and he holds the enchantment card.
- Do not stand at Sunburst's exact range (removed count + his range items). Adjacent is safe from Sunburst; one off the number is safe.
- Do not stand adjacent to a friendly of his if that is the only thing making Fireball illegal, when he is about to cast it — step off if you are the bait, or stay glued to the real target if you are protecting it.
- Race `wishCasts`. At 2, he must not be allowed to play silver. Killing him, or a discard that strips silver, is worth more than a minion.
- Arien's silver (skill lock) stops his coloured casts; it does not stop gold spells. Gold cards are the exception on that lock.

## 5. Common mistakes

- Playing any coloured card or Cantrip before the first Prepare. The primary cannot start. The card becomes a walk or a bad defense.
- Casting Find Familiar and removing Wish, Sunburst, or the only attack spell, then having nothing to cast.
- Preparing the same round Shield or Invulnerability must last. The return cancels them.
- Fireball into a friendly minion. The target is illegal and the spell is not offered; if it was the only option the action does not start.
- Sunburst or Dimension Door at 0 radius or range, expecting "anywhere up to". Both are exact.
- Energy Drain with no spent Life, or with no targetable hero. The life does not happen.
- Suggestion when nobody can move 3. The spell still leaves the book.
- Treating Midas as income in round 1. Two other spells must already be removed.
- Holding silver all game "to be safe" after the book is full, then having no casts left on turn 4.
- Ignoring `wishCasts`. The third Wish is not a combat trick. It ends the game.

## 6. HEURISTIC RULES

Weights are coin-equivalents. A listed weight is added by `adjust`, or is the value of an `evalTerms` term. "Book empty" means the public `spellbook` counter is 0. "Can cast X" means X is not among the faceup removed spells and the deck card that lists it is the one being resolved. General facts (distance, hand size, revealed cards, coins, lane, initiatives) are marked HContext. Spell identity, `wishCasts`, and which immunity is active are marked hero-state; they are deducible from public counters and faceup spells, so strength 7+ may use them and strength 5–6 may use the counters even when names are hidden from teammates.

1. `PLAY_CARD` [`gydion-silver`] WHEN book empty AND it is turn 1 or 2 AND no enemy hero is within his movement-2 reach of a kill on him (no revealed attack that reaches his space before initiative 13) THEN play `gydion-silver`. WEIGHT 25. HContext + hero-state (`spellbook`). SOURCE [REASONED]; designer BGG 3491113; [BGG 3493559].

2. `PLAY_CARD` [`gydion-silver`] WHEN `wishCasts` ≥ 2 AND Wish is in the book AND at least one other spell can start AND silver is in hand THEN play silver, and on the confirm choose Wish. WEIGHT 1000 (alternate win). hero-state. SOURCE card `gydion-spell-wish`; rulings/gydion.md 12.

3. `CONFIRM` / `CHOOSE_OPTION` [`gydion-purple-4`, `gydion-silver`] WHEN the prompt is cast Wish instead of adding AND `wishCasts` < 2 AND the number of removed spells he still intends to cast this round is ≥ 1 THEN choose "Add the spells", not Wish. WEIGHT 15. hero-state. SOURCE [REASONED]; rulings/gydion.md 11.

4. `CONFIRM` [`gydion-purple-4`] WHEN `wishCasts` = 2 AND an inner spell can start THEN choose Wish. WEIGHT 1000. hero-state. SOURCE card text.

5. `PLAY_CARD` [any cast card] WHEN the book is empty THEN do not play a card whose only primary is a cast. Prefer a card with movement 3 (`gydion-red-*`) if he must enter the battle zone, else hold the cast cards. WEIGHT −20 on the dead cast. HContext + hero-state. SOURCE rulings/gydion.md 5.

6. `CHOOSE_OPTION` [the evocation and cantrip menus] WHEN choosing a spell THEN score the spell by the rule below and do not pick one that cannot start. WEIGHT as that spell. HContext. SOURCE rulings/gydion.md 5.

7. `CHOOSE_UNIT` [`gydion-spell-power-word-kill`] WHEN an enemy hero in radius 3 has 0 cards in hand and is targetable THEN that hero. WEIGHT a hero kill (coins he would gain + one Life, use 15 when lives are even, 30 when the enemy team is one life from losing). hero-state for the spell being available; hand size is HContext. SOURCE card text.

8. `PLAY_CARD` [`gydion-blue-3a` or the card that can cast it] WHEN such a hero exists this turn after earlier actors AND Power Word Kill is in the book THEN play that card over a minion. WEIGHT same as rule 7. SOURCE [REASONED].

9. `CHOOSE_OPTION` [`gydion-spell-shield`] WHEN an enemy has a revealed basic card (gold or silver) that acts after him, or an unrevealed hand that still contains a gold or silver, AND Shield is in the book AND he will not Prepare this round THEN cast Shield. WEIGHT 12 if a basic attack can reach him, else 4. HContext + hero-state. SOURCE card text.

10. `CHOOSE_OPTION` [`gydion-spell-invulnerability`] WHEN the attacks that can still reach him this round are non-basic (red, or a coloured spell) AND Invulnerability is in the book THEN cast it instead of Shield. WEIGHT 12. If both classes can reach him and he can cast only one, cast the one matching the higher attack total. SOURCE card text; rulings/gydion.md 20.

11. `CHOOSE_OPTION` [`gydion-spell-energy-drain`] WHEN his team has a spent Life counter AND an enemy hero is targetable in range 3 THEN cast it. WEIGHT 5 at full lives remaining above the death cost, up to 15 when his team is one life from losing, plus 2.5 if the target's deduced hand contains a non-basic card. SOURCE catalog FAQ; rulings/gydion.md 19.

12. `CHOOSE_OPTION` [`gydion-spell-fireball`] WHEN the target unit is in range 3, not adjacent to him, and not adjacent to a friendly unit THEN prefer it over Burning Hands if the target is a hero he cannot reach or a minion the lane needs. WEIGHT the minion (15 if it is the one that swings an even lane) or the attack's kill chance. Do not select a unit adjacent to a friendly. SOURCE card text; flavor line on `gydion-red-2a`.

13. `CHOOSE_UNIT` [`gydion-spell-burning-hands`] WHEN an enemy hero is adjacent to the target and that hero has a card in hand THEN choose the discard on that hero before the attack, unless the discard would be his teammate's setup. WEIGHT 2.5 for the card, plus 6 if that hero acts later this turn and the discard leaves them unable to defend. SOURCE rulings/gydion.md 16.

14. `CHOOSE_OPTION` [`gydion-spell-sunburst`] WHEN `spellsRemoved + range items` equals the distance to a unit he wants AND that number is ≥ 1 THEN cast it. Attack equals that same number plus attack items. WEIGHT as an attack of that size. Skip when the distance does not match any legal unit. SOURCE card text; BGG 3589662 designer.

15. `CHOOSE_OPTION` [`gydion-spell-find-familiar`] WHEN Sunburst or Dimension Door is in the book AND `spellsRemoved` < the distance he needs AND the book contains spells he cannot cast with his current hand THEN remove those unusable spells, up to 3, and stop before removing a spell his remaining hand can cast or Wish at `wishCasts` ≥ 1. WEIGHT 4 per removed spell that increases a distance he will use this game, minus 8 if he removes his last attack spell. SOURCE card text; designer BGG 3475239 (any spell in the book is legal to remove).

16. `CHOOSE_SPACE` [`gydion-spell-dimension-door`, `gydion-spell-expeditious-retreat`] WHEN he is outside the battle zone or more than 4 spaces from the nearest enemy minion he should take THEN place or move into the battle zone, on a space that is not adjacent to two enemy heroes. Door only at the exact radius. WEIGHT 10 for entering the zone, 4 for a better lane offset. HContext. SOURCE [REASONED]; movement stat 1.

17. `CHOOSE_SPACE` [`gydion-spell-suggestion`] WHEN an enemy in radius 3 can move exactly 3 in a straight line THEN choose the line that takes them off an enemy minion they were about to score, or onto a space adjacent to his ally who has a revealed melee attack still to act, or away from him if he is the target. WEIGHT 15 for a denied minion, 8 for delivering a body to an ally. SOURCE card text.

18. `CHOOSE_OPTION` [`gydion-spell-dominate-person`] WHEN an enemy hero in radius 3 is adjacent to an enemy minion that is also in radius 3 THEN cast it if that minion is the lane-deciding one or the hero is the kill target. WEIGHT 15 for the minion plus the coins, 0 if the minion is not the one the lane needs and the hero is not dying. SOURCE rulings/gydion.md 23.

19. `CHOOSE_OPTION` [`gydion-spell-midas-touch`] WHEN `floor(spellsRemoved / 2)` ≥ 2 OR those coins complete a level at end of round THEN cast it on a turn he is not in range of a kill. WEIGHT the coins (1 each, 1.5 if they finish the level). SOURCE card text; rulings/gydion.md 25.

20. `CHOOSE_UNIT` [`gydion-spell-disintegrate`, `gydion-spell-polymorph`] WHEN an enemy token (zombie, rock, smoke, tree, ice) is adjacent, or an enemy non-heavy minion is adjacent, AND that piece is what makes the enemy's attack or the lane work THEN remove or swap it. Polymorph: swap the enemy hero with the minion that is about to score, or with the token that was blocking his ally. WEIGHT 8 for a zoning token, 15 for a lane minion (no coins on Disintegrate). SOURCE card text.

21. `DEFEND` [any] WHEN Shield is active AND the incoming attack is basic THEN do not discard; the attack does not hit. WHEN Invulnerability is active AND the attack is non-basic THEN the same. WHEN neither immunity matches, defend with the lowest defense icon that still beats the attack total, and never discard silver if the book is empty or the last copy of a spell he needs is only castable from the card he would discard. WEIGHT of a wrong discard: −8. SOURCE rulings/gydion.md 20; [REASONED].

22. `UPGRADE_CARD` / `KEEP_UPGRADE` [any] WHEN choosing the kept card THEN follow §3, and tuck the branch whose `item` is the highest missing priority (movement, range, radius, defense, initiative, attack). WEIGHT 3 for the right item, the level itself is the general playbook's. SOURCE [REASONED].

### evalTerms

From his team's view, for the Gydion on that team. The enemy's Gydion is scored with the sign flipped by the caller (the interface is already "from `team`'s view").

- `spellbook` counter = 0 while he has a cast card in hand or unresolved: −8. His hand does not cast.
- `spellbook` counter > 0: +3, plus +1 for each removed spell while Sunburst or Dimension Door is still in the book and he has the card that casts it, capped at +6.
- Shield active (Burning-hands school spell removed this round and not returned) against a side that still holds a basic attack: +8.
- Invulnerability active against a side that still holds a non-basic attack: +8.
- Both immunities up: +12, not +16. One attack class will usually be the one they keep.
- `wishCasts` * 10. At 2, and silver plus Wish plus one castable spell available: +40 on top. At 3 the game is already over.
- Familiar token: +0.5 as an obstacle. Do not price it as a minion.

### threatAdjust

When `enemyHeroId` is `gydion`, extra danger beyond printed reach:

- +12 on each enemy-hero space at exact distance `spellsRemoved + his range items` while Sunburst is in the book and he holds an evocation card. 0 at every other distance.
- +15 on enemy heroes with 0 cards in hand, within radius 3 + radius items, while Power Word Kill is in the book and he holds the enchantment card.
- +6 on spaces adjacent to him while Burning Hands or Shocking Grasp is in the book.
- +4 on spaces at range 2–3 while Fireball is in the book, and 0 on spaces adjacent to one of his friendly units.
- While Shield is active, threat of his **basic** attacks is 0 (the adjust returns 0 and the caller should zero basic-attack threat into him; this function is about his danger outward, so the immunity belongs in the defender's eval, rule 21). Outward: a Gydion who has cast nothing is not a threat at range 3. Do not paint his whole stat block as attack 5.

## 7. Sources

- Catalog `gydion.json`, including advice and the Energy Drain FAQ. Advice is the two spellbook sentences only; it does not pick branches. Count: 1 official advice text.
- Engine rulings `core/game/rulings/gydion.md` (designer answers quoted there): BGG 3491113 (Arty, designer, spells do not start in the book; silver adds all), BGG 3487018 (designer, Nebkher copies from Gydion's book), BGG 3589662 (designer, Sunburst is exact range, items apply), BGG 3475239 (designer, Find Familiar removes any spell in the book), BGG 3456872 (designer, Discord via BGG, removal only on cast or a spell effect).
- [BGG 3493559] (2025-04-09, Arien Build & Go, community) one paragraph: catch a round-1 skill hero such as Gydion in his own side zone. Not a Gydion guide.
- [BGG 3459990] is the designer's complexity remark about Nebkher, not Gydion. No Gydion spotlight exists in the strategy forum (83 threads listed 2026-11-20).
- Gamefound Arcane pack update (https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/10), designer overview: separate spellbook, cast spells leave it, branches are schools of three. The "21 spells plus Wish" count matches the 22 extras. No per-spell numbers were taken from it.
- No Reddit thread, no 2nd-printing spotlight, no build guide. Gaps are the whole opening book and which school wins; those rules are [REASONED] from the cards.

## Implementation notes

`GydionHeuristics.kt`, checked against `GydionHero.kt`, `gydion/Spellbook.kt` and `gydion/GydionSpells.kt` (2026-11-21).

- **Rule 3 is reversed in code.** The engine casts Wish through the Archwizard confirm on Prepare Spells, and every cast counts toward the three. Choosing "Add the spells" whenever `wishCasts` < 2 would never reach 2, so the alternate win could not happen. The playbook casts Wish while the book still holds 4 or more spells and refills a thinner book. Rules 2 and 4 (the third Wish) are an `override`.
- Rule 21 is not encoded. Shield and Invulnerability make him immune (`AttackImmunity`), so the engine never lets that attack target him and no Defense decision is asked.
- Rule 6: the engine asks the spell as `CHOOSE_CARD` ("Choose and cast one spell in the spellbook"), not `CHOOSE_OPTION`, and offers only spells that can start. Plain attack spells (Magic Missile, Shocking Grasp, Vampiric Touch) are scored by the general strike estimate of the spell card.
- `threatAdjust` counts Sunburst only while `gydion-red-3a` may be in his hand and Power Word Kill only while `gydion-blue-3a` may be: those are the only cards that list them. Sunburst's attack equals its exact range, so its threat grows with that number.
- The book's contents are public after Prepare (every spell not removed faceup), so `inBook` for an enemy Gydion reads only counters and faceup spells.
- Prepare is discouraged while Shield or Invulnerability is in force: `Spellbook.setTag` cancels a spell's effect when it returns to the book.
- Skipped (lookahead or general book already answers them, or the card choice is engine-opaque): 13 (Burning Hands discard pick), 15 (Find Familiar removals), 17 (Suggestion line), 18, 20, 22 beyond `upgradeOrder`.
