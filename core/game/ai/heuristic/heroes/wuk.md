# Wuk (`wuk`) — heuristic playbook

2nd printing. Dashboard: Attack 5, Defense 8, Initiative 1, Movement 3–4. Complexity 3. Catalog roles: tokens, pusher, durable, sniper, tactician, healer. Advice in `heroes/wuk.json`.

Units: `heuristic/DESIGN.md`. **[HERO]** needs Tree tokens: supply 3, obstacles, not removed at end of round, Clear removes them, a minion spawn under one deletes it [rulings/wuk.md 1–2].

**The 1st-printing spotlight does not describe these cards.** [BGG 2894009, Panetierre, 2022-07-04, playtester] is about a different Gold (a push after the hit), a different Green (a 5-push), a different Blue heal, a different Red, and an Ultimate that turned trees into minions. None of that text is on the 2nd-printing cards. What still applies is named below and tagged. Everything else is from the current cards.

There is no 2nd-printing Wuk spotlight or Build & Go in the strategy forum (83 threads, listed 2026-11-20). Wolff Designa's Gamefound blurb (Renowned pack) calls him a brawler, a support and a pusher who denies ground with trees and is hard to pilot. That matches the dashboard and is not a card list.

## 1. Identity

Zoner / Pusher [Cryomancy, BGG 3779890, which matches the current kit]. Initiative 1 means he acts after the table. His attacks do not catch heroes who can still move. They make those heroes move, and then he takes the minion they left, or he hits the one who could not leave. Defense 8 is why he is allowed to stand there.

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | SetUp | The trees are the range, the walls and the teleports. Without one he is an adjacent hero-hitter with Movement 4 and init 7. |
| `idealDistance` | 2 | Gold's range is 2 after a tree is spent. The Red's tree bullet is range 2, then 3. He wants a tree between him and the thing he is not allowed to stand on. |
| `aggression` | 0.3 | Threat over damage. A hero kill is a gift, usually from Trample or from an ally who sat in the trees. |
| `laneWeight` | 0.7 | Claim / Assert Dominance and Trample's minions are push tools. The tree-Red's job is to stop them taking his minions. |
| `keepDefender` | true | Red is Defense 7–8. Blue is 5–6. Silver is 4. He blocks with Red or Blue, not with the tree planter, unless Red is the Trample and he is not adjacent to a fight. |
| `upgradeOrder` | see §3 | Tree-Red first if the trees are in the fight; Trample when he is done planting. |

His colour order is awkward on purpose. Gold 11 is his only fast card. Red 7–8 is the slow end of the Red band 7–12 [STRATEGY §1.6]. Green 5–6 is the **fast** end of the Green band 1–6, so Toss Away often acts before other greens. Silver 4 is "after they have moved". Blue 8–9 is a slow Blue. Adding the tree-Red's Initiative item (both `wuk-red-2a` and `wuk-red-3a` are Initiative) speeds every card, including the Silver he wanted to play last. That cost is real. Take the item anyway when range 3 is how he stops replanting every round; do not take a second Initiative item on top (Claim Dominance is the other one — tuck it if he is on the tree plan).

## 2. Card-by-card

Trees are ordinary obstacles. The 1st printing let heroes walk through them and forbade trees adjacent to trees. The 2nd printing does neither. A tree in a one-hex gap is a wall. Two trees may stand adjacent. Enemies Clear them by spending an attack.

### `wuk-gold` Tree Slam — Ranged attack, init 11, Attack 4, Defense 3, Movement 1, Range 2

Choose one: a minion adjacent to him; or remove an adjacent tree, then a unit in range. Both attacks are ranged [rulings 4]. If the tree bullet starts and no unit is in range, the tree is gone and the attack stops [rulings 3].

- **For:** the fast minion, or a range-2 shot paid for with a tree. Init 11 is the card that can hit a hero who has not yet moved. Attack 4 will not one-shot a careful hero. It takes a minion for certain, or it forces a block.
- **When:** the adjacent-minion bullet whenever a minion is touching him and the tree should stay. The tree bullet when a hero in range 2 will be defeated or will spend their best defense, and the tree is not the one holding a gap.
- **Do not:** delete the choke tree for a minion the other bullet could have taken. Do not start the tree bullet with no target in range.
- **In hand:** Defense 3, and the threat of range 2 from every adjacent tree. Weak block next to Red's 7.

`wuk-handicap` Makeshift Weapon is the same choice at init 10, Attack 3, Defense 2, and both bullets are heroes only.

### `wuk-silver` Mystic Saplings — Skill, init 4, Defense 4, Radius 3, no Movement

Place up to 3 trees in radius. They stay between rounds. With the supply empty he may pick one up and replant it, or stop [rulings 1; BGG 3466489, leizeQ; BGG 3504393, nichik pointing at p.18].

- **For:** the round he builds the map. Not every round [ADVICE: "Where will you need those trees next turn? And what about in the next round?"]. Panetierre's placement list still fits these tokens, with the walk-through sentence deleted:
  - adjacent to an enemy minion (the Red's second bullet, and a Clear risk),
  - on a hex an enemy wants, or in the one-hex gap beside terrain (now a hard wall),
  - adjacent to a hex an ally wants,
  - adjacent to him, so Gold's tree bullet and the Blue retrieve are on.
- **When:** init 4, after the fast heroes have committed, so the trees land on the paths they actually took. Or the turn the supply can be picked up and the stale tree moved one step (remove it, place it beside its old hex).
- **Do not:** plant on a minion spawn the push will fill. Do not plant three trees in a corner of the wrong zone. Do not play Silver when three trees are already in the fight; Hold or attack, and let the Ultimate replant if he has it.
- **In hand:** Defense 4. The block of last resort.

### `wuk-red-1` Nature's Protector — Ranged attack, init 7, Attack 5, Defense 7, Movement 4, Range 2

Choose one: a hero adjacent to him; or a unit in range adjacent to a tree.

### `wuk-red-2a` Nature's Guardian — item: Initiative. Init 7, Attack 5, Defense 8, Movement 4, Range 3

Same, range 3. `wuk-red-3a` Nature's Champion (item: Initiative, init 8, Attack 6, Defense 8, Range 3): one or both, on different targets. Bullet 1 is only a hero. Bullet 2 is any unit beside a tree. Both means a hero and a different unit, not two minions.

- **For:** the zone. Nobody stands still on a hex in range 3 of him that touches a tree, because Attack 5–6 at Defense 8 in his hand is a bad trade for them. When they leave, the bullet takes the minion beside the tree. When they cannot leave, it is a hero hit at init 7–8, which is late: only heroes who have already moved, or who have no movement left, get hit. That is the design, not a failure [REASONED from Initiative 1 and the Red band; the same shape as Panetierre's "you force them to move", even though his printed Red was different].
- **When:** a minion beside a tree is in range, and no hero is about to kill him. Champion: add the adjacent hero when they are a different unit and Attack 6 beats what they have left.
- **Do not:** take the adjacent-hero bullet when the tree bullet takes a minion and the hero can still block with a card he needed them to spend later. Do not stand out of range of his own trees.
- **In hand:** the wall. Defense 7–8. Spending it is the decision of the turn.

### `wuk-red-2b` Trample — item: Defense. Movement, init 7, Defense 7, Movement 4

If he moves in a straight line he may ignore obstacles. Each enemy hero moved through discards or is defeated. He defeats up to one enemy minion moved through. `wuk-red-3b` Angry Stampede (item: Radius, init 8, Defense 7, Movement 4): up to two minions. The straight-line choice is before the move [rulings 9]. Minions are chosen together, so a heavy that still has a friend in the zone is not taken [FAQ; rulings 10]. Each of Razzle's figures is a separate hero discard. Friendly units are never hit. The destination must be empty; a body on the last hex stops that line.

- **For:** the round he stops being a turret. Movement 4 through terrain, a discard on every hero in the path, and one or two minions. He gives up the tree-Red's range to do it [Panetierre's tradeoff still describes this branch].
- **When:** a straight line of 2–4 crosses a hero who has a card (the discard) or has none (the defeat), and ends on an empty hex that is still in the fight. Minions in the path are the farm. Init 7–8 is late, so they can step into the last hex if they have a slower card — the Movement item (`wuk-blue-3b`'s icon) makes the line longer than one body can plug.
- **Do not:** trample a line whose only prize is a heavy that is still immune. Do not trample off the hill when Dominance is in force and he is the reason those minions are adjacent. Do not take the normal (non-straight) move and expect the discards; they require the straight line.

### `wuk-green-1` Toss Away — Skill, init 6, Defense 4, Movement 2, Range 3

Place a token, or an enemy unit, that is adjacent to him into an empty space in range. Not a push: Magnetic Dagger and "cannot be placed" stop an enemy unit, and do not stop him placing his own tree unless a different effect says so.

### `wuk-green-2a` Mighty Throw — item: Attack. Init 5, Defense 4, Movement 2, Range 4

Same. `wuk-green-3a` Monstrous Throw (item: Attack, init 5, Defense 5, Range 4) may repeat once. The repeat may be the same object [rulings 6].

- **For:** relocate a tree to the next gap or the next minion, or put an enemy minion on an ally, or throw a hero off his minion. Range 3, then 4. Init 5–6 is before most other greens, so he often moves the piece before they take their slow step.
- **When:** the destination makes a Red bullet true, or takes a minion out of an enemy's adjacency, or puts it in an ally's. Repeat on Monstrous Throw when the same tree needs to go further (place, then place again) or a second unit also needs to move.
- **Do not:** throw the tree that is currently the only reason a hero is zoned, unless the new hex zones them harder. Do not throw an enemy who cannot be placed and then have no primary.

### `wuk-green-2b` Into the Canopy — item: Defense. Init 5, Defense 4, Movement 2, Radius 2

Choose one: swap with a tree in radius; or swap a friendly unit in radius with a tree in radius. `wuk-green-3b` Treetop Ride (item: Defense, init 5, Defense 5, Radius 2): up to two times, either bullet, and the radius is measured again after a swap [rulings 11].

- **For:** he becomes the tree, the tree becomes him. That is a teleport of radius 2, and the tree he left behind is now where he was — often still a wall. The ally bullet is how a slow friend appears on a tree he planted for them. Twice, he can swap to a tree and then swap an ally to a different tree, or swap twice and end two hops away.
- **When:** the tree is on the hex he needs (a minion, a safe side of the zone) and walking would take the Red he wanted to attack with. Or an ally is in radius and the tree is the hex they asked for.
- **Do not:** swap to a tree that is on a spawn about to fill, or into a revealed attack, or when the tree was the choke and moving it opens the gap.

### `wuk-blue-1` Gifts of Nature — Skill, init 8, Defense 5, Movement 2, Radius 3

Remove a tree in radius. He may retrieve a discarded card. No tree: no primary [rulings 7].

### `wuk-blue-2a` Tree of Plenty — item: Attack. Init 9, Defense 6, Movement 2, Radius 4

Remove a tree, then he may retrieve, or a friendly hero in radius may. `wuk-blue-3a` Abundance (item: Range, init 9, Defense 6, Radius 4): one or both. The friend may decline [rulings 7].

- **For:** a card back, paid with a tree. Himself after he blocked with Red and the round still has a turn. The ally when their retrieved card is a kill and his is not. Abundance when both are true and he can spare the one tree.
- **When:** a tree is in radius that is not the last gap, and the retrieved card is worth more than that tree (a kill card, or the only block). Radius 4 at tier 2 means the tree can be the one across the zone.
- **Do not:** eat the choke. Do not retrieve a card he was about to play anyway from hand.

### `wuk-blue-2b` Claim Dominance — item: Initiative. Init 9, Defense 6, Movement 2

This round: up to 1 enemy minion adjacent to him does not count in the minion battle, immune or not. `wuk-blue-3b` Assert Dominance (item: Movement, init 9, Defense 6): up to 2. He chooses at the battle, from his hex then, and he may choose none [rulings 8]. They are not removed, pay no coins, and still stand, so this is not a push by itself. A hero who counts as a heavy (Brogan's Ultimate) is not a minion and is not picked.

- **For:** the heavy, or the two bodies that flip an even lane, without spending an attack. The Movement item on Assert is what Trample wants.
- **When:** he will still be adjacent to those minions at end of round. Play it late enough that they cannot walk him off, or early if nothing can move him. Init 9 is before many Reds, so he often declares it and then has to survive on the spot.
- **Do not:** play it and then Trample to the other side of the zone. Do not count the minions as defeated. Next round they are still there [the 1st-printing warning about this shape is still true of this text].

### `wuk-purple-4` March of Nature — Radius 3

After each card he resolves, including Hold, he may place a tree in radius, items included [rulings 12]. Supply 3 still applies: the fourth place picks one up.

- This replaces "plant on Silver". A Hold on a quiet turn is a tree. A Trample that resolves is a tree at the destination. Do not spend Silver just to plant if the Ultimate is already doing it, unless he needs three at once.

## 3. Upgrade path

No 2nd-printing build list exists. The split is the one the cards still offer: the tree, or the stampede. Panetierre's two paths had that shape; the cards inside them have changed, so the order below is reasoned from the current icons.

**The grove (stay, shoot, wall):**

1. `wuk-red-2a` Nature's Guardian first when a tree is already near the minions. Range 3 means he replants less. The Initiative item is the cost in §1; accept it.
2. `wuk-green-2b` Into the Canopy when he or an ally needs the teleport (Defense item, which he feels less because Red is already 8). `wuk-green-2a` Mighty Throw when the trees are one hex off and range 4 fixes them (Attack item: Gold becomes 5, the tree-Red becomes 6).
3. Blue: `wuk-blue-2a` Tree of Plenty if anyone is discarding cards they need (Attack item, radius 4). `wuk-blue-2b` Claim Dominance if the heavy is how waves are being lost (Initiative item — a second one; only if the heavy is the game).
4. Tier III: `wuk-red-3a` Nature's Champion (the both-bullets, Attack 6). Then `wuk-green-3b` Treetop Ride if the double swap is the mobility, else `wuk-green-3a` if throwing twice is. `wuk-blue-3a` Abundance when both he and an ally need the card back. Tuck `wuk-blue-3b` unless Trample is coming; its Movement item does nothing for the tree-Red's attack.

**The stampede (leave the trees up as walls, stop shooting from them):**

1. Keep `wuk-red-1` until a line exists, then `wuk-red-2b` Trample. Do not take Guardian's Initiative item if the whole point is to switch — taking Guardian and then Trample at tier 3 means a round on the wrong Red. If the trees are already good, Guardian first is fine and Stampede waits.
2. `wuk-green-2a` so he can throw a body off the line before he runs it, or `wuk-green-2b` so he teleports onto the start of the line.
3. `wuk-blue-3b` Assert Dominance as soon as tier 3 starts if he is on Stampede: the Movement item is "harder to plug the last hex", and two minions drop out of the battle. Claim at tier 2 only if the heavy is already the swing.
4. `wuk-red-3b` Angry Stampede first at tier 3. Two minions in the path, init 8.

**Tuck:** Initiative is tucked by not taking Claim while on Guardian — he cannot tuck Guardian's item without putting Trample in hand. Attack items (Throw, Tree of Plenty) want the dashboard on the grove path so Attack 4 Gold and Attack 5 Red cross a defense. Defense items (Trample, Canopy) are redundant with Defense 7–8 and are the right tucks on the grove path. Range (Abundance) lengthens Gold and the throw. Radius (Stampede) lengthens Silver, the swaps, the retrieve, and the Ultimate. On the stampede path, Stampede's Radius item is in hand with the Red, which is what he wants for the Ultimate's plant-after-trample.

**Phase:** round 1, plant once, take a minion with Gold or Red, and walk before the push [ADVICE]. Levels 1–3 he is a wall with three tokens and one range-2 shot. Levels 4–6, range 3 or the trample, not a round of each. Level 7, Silver becomes optional. He still leaves before a push that would strand Movement 3–4 on the wrong beach [ADVICE].

## 4. Synergies and counters

**With:** a fast ally. Two slow brawlers miss the heroes who have already left [Panetierre, BGG 2894009, still true of Initiative 1]. Hanu's "set a card to a high initiative" and Bain's pull were named for the 1st printing and remain good into a hero who cannot chase; they are not required. A ranged ally who stands among the trees is protected by the zone, not by a rule that trees block shots — enemies simply do not want to stand there. Silverarrow and Swift were Panetierre's example. An ally who wants to be teleported: plant the tree, Treetop Ride them onto it.

**He fears:** Clear, and heroes who move his trees or his friends out of the zone (Xargatha's pull still does that). Stone Gaze and "movement 1" stop his Trample and his walks; they do not stop a swap with a tree. Long-range heroes who never enter the grove. A body on the last hex of a trample. His own Ultimate from the 1st printing is not a thing he has — do not play as if trees were minions.

**Against him:** do not end the turn adjacent to a tree that is in his range 2–3 if you have already spent your movement. Do spend an attack to Clear a tree that is both the gap and the range, when the minion is not worth more. Step into the last hex of a telegraphed trample if your card is slower than init 7–8. He is hard to kill (Defense 7–8 in hand, a retrieve if a tree can be spared). Discard him or move the trees. Do not try to win a damage race beside a tree.

## 5. Common mistakes

- Playing the 1st-printing kit: walking through his own trees, refusing to place trees adjacent, pushing 5, healing because a tree is nearby without removing it, counting trees as minions at level 7.
- Planting every round, including on spawns.
- Gold's tree bullet with no target, deleting the tree [rulings 3].
- Trample through a heavy that is still immune, and a second minion, and expecting both [FAQ].
- Dominance and then leaving the adjacency.
- Throwing the only choke tree at a hero who was already leaving.
- Blocking with Silver (Defense 4) while Red (Defense 7–8) is in hand.
- Chasing a sniper across open ground on Movement 3 [Panetierre's matchup note, still true].
- Holding Gold because no hero is in range, when an adjacent minion is 15 points. The first bullet does not need a tree.

## 6. Heuristic rules

`evalTerms`:

- Each tree that is adjacent to an enemy minion, or in a one-hex gap, or adjacent to him while the tree-Red or Gold is in hand: +4. Cap the sum at +12 (three trees). A tree on a minion spawn: +1 and −4 if a push can happen this round. A tree more than 4 hexes from the battle zone: +0. Trees persist across rounds, so this term does not zero at end of round. [HERO]
- Dominance in force, him adjacent to the excluded minions: +15 each if they flip the projected battle, else +3 (they remain). [HERO]

`threatAdjust`: a hex adjacent to a tree and within his Red range is a shot, at Attack 5–6, init 7–8, while that Red is unplayed. Gold adds range 2 from him to any hex, paid by an adjacent tree, at init 11, Attack 4.

1. **H1** [PLAY_CARD] [`wuk-silver`] **WHEN** fewer than 2 trees are in the battle zone he will fight in, or the existing trees are all on spawns or on the wrong side of a coming push **THEN** place up to 3: one in a gap or beside an enemy minion, one beside a hex he wants to stand in, and stop before planting a spawn. If the supply is empty, pick up the stale tree and replant it one hex toward the fight. **WEIGHT** +4 per well-placed tree. **SOURCE** [ADVICE; rulings 1–2; placement list adapted from Panetierre BGG 2894009] **[HERO]**

2. **H2** [PLAY_CARD] [`wuk-silver`] **WHEN** two or three trees are already in the fight **THEN** do not play Silver. Attack or Throw. **SOURCE** [ADVICE; REASONED] **[HERO]**

3. **H3** [PLAY_CARD] [`wuk-gold`] **WHEN** an enemy minion is adjacent **THEN** take it with bullet 1 unless a hero in range 2 dies to Attack 4 only if he spends a tree, and that tree is not the gap. **WEIGHT** 15, or the kill minus 4 for the tree. **SOURCE** [CARD; rulings 3] **[HERO]**

4. **H4** [PLAY_CARD] [`wuk-red-1`, `wuk-red-2a`, `wuk-red-3a`] **WHEN** a unit beside a tree is in range **THEN** that bullet, minion before hero, unless the hero's defense loses to this Attack and they have no movement left. Champion: add the adjacent-hero bullet when the target is a different unit and the second hit is worth at least 4. **WEIGHT** 15, or the card they spend. **SOURCE** [CARD; REASONED from init 7–8] **[HERO]**

5. **H5** [PLAY_CARD] [`wuk-red-2b`, `wuk-red-3b`] **WHEN** a straight line of 2–4 ends on an empty hex, crosses an enemy hero or an enemy minion, and the destination is still useful **THEN** trample, ignore obstacles, and defeat the minions the FAQ allows (not an immune heavy chosen together with its guard). Heroes in the path discard or die; he does not choose that. **WHEN** they can still move onto the last hex with a card slower than his init **THEN** do not telegraph it; Throw them off the line first if Green is faster, or wait. **WEIGHT** +4 per discard, +15 per minion, or the kill if a hero has no card. **SOURCE** [CARD; FAQ; rulings 9–10] **[HERO]**

6. **H6** [CONFIRM] [Trample's straight line] **WHEN** the prize is on the straight line **THEN** yes. **WHEN** he only needs a normal step onto a minion and the line's end is bad **THEN** move normally, and expect no discards. **SOURCE** [rulings 9] **[GENERAL]**

7. **H7** [PLAY_CARD] [`wuk-green-1`, `wuk-green-2a`, `wuk-green-3a`] **WHEN** a tree, or an enemy unit, is adjacent and a hex in range makes a Red bullet true, closes a gap, or hands a minion to an ally **THEN** place it there. Monstrous Throw: repeat if the same tree should move again or a second unit should. **WEIGHT** +4 for a better tree, +15 if the ally then takes the minion. **SOURCE** [CARD; REASONED] **[HERO]**

8. **H8** [PLAY_CARD] [`wuk-green-2b`, `wuk-green-3b`] **WHEN** a tree in radius 2 is the hex he needs and walking would spend the attack **THEN** swap with it. **WHEN** an ally in radius wants that tree's hex **THEN** swap them. Treetop Ride: a second swap if it plants the ally or moves him again onto a minion. **SOURCE** [CARD; rulings 11] **[HERO]**

9. **H9** [PLAY_CARD] [`wuk-blue-2b`, `wuk-blue-3b`] **WHEN** he is adjacent to the heavy or to enough enemy minions that excluding 1 (or 2) flips the projected battle, and he will not move off them afterwards **THEN** play Dominance. **WEIGHT** +15 per excluded minion that changes the battle. **SOURCE** [CARD; rulings 8] **[HERO]**

10. **H10** [PLAY_CARD] [`wuk-blue-1`, `wuk-blue-2a`, `wuk-blue-3a`] **WHEN** a tree in radius is not the last gap, and a discarded card (his, or a friend's on Plenty/Abundance) is a kill or the only block **THEN** remove that tree and retrieve. Abundance: both, when both retrieves are worth 2.5 or more. **WEIGHT** +2.5 per card, −4 for the tree. **SOURCE** [CARD; rulings 7] **[HERO]**

11. **H11** [CONFIRM] [`wuk-purple-4`] **WHEN** a card just resolved and a legal empty hex in radius is a gap, a minion-side, or his own side **THEN** place, picking up a stale tree if the supply is empty. **WHEN** the only hexes are spawns **THEN** decline. **SOURCE** [rulings 12] **[HERO]**

12. **H12** [DEFEND] **WHEN** Red's Defense 7–8 beats the attack **THEN** use Red only if Blue's 5–6 does not and the attack is real. Prefer to keep the tree-Red in hand and block with Blue. Never Silver if either beats it. **SOURCE** [CARD] **[GENERAL]**

13. **H13** [PLAY_CARD] [any Movement] **WHEN** a push this round would leave him on the far side **THEN** spend the move toward the next zone before planting a tree there is impossible. **WEIGHT** −8 for the wrong beach [ADVICE]. **SOURCE** [ADVICE] **[GENERAL]**

14. **H14** [UPGRADE_CARD] **WHEN** trees are already beside the minions **THEN** Guardian before anything else. **WHEN** he is spending every turn walking between trees **THEN** Trample or Canopy before a third tree-shot upgrade. **SOURCE** [REASONED; ADVICE] **[HERO]**

15. **H15** [KEEP_UPGRADE] [Red pair] **WHEN** the enemy must walk through a gap to take minions **THEN** keep the tree-Red. **WHEN** they refuse to enter the grove and the wave is across the map **THEN** keep Trample. **SOURCE** [REASONED from the two Reds] **[GENERAL]**

16. **H16** [KEEP_UPGRADE] [Blue pair] **WHEN** on Trample **THEN** Assert Dominance for the Movement item. **WHEN** on the tree-Red and already holding Guardian's Initiative item **THEN** Tree of Plenty, not Claim, unless the heavy is the swing. **SOURCE** [CARD icons; §1] **[HERO]**

17. **H17** threatAdjust [`wuk`] **WHEN** a tree is adjacent to a hex in his Red range and the tree-Red is unplayed **THEN** that hex is threatened at the Red's Attack and init. **WHEN** he is adjacent to a tree and Gold is unplayed **THEN** every hex in range 2 is threatened at Attack 4, init 11, at the cost of that tree. **WHEN** Trample is unplayed **THEN** straight lines of length ≤ his Movement through his hex are discard threats, not damage threats, unless the hero has no card. **SOURCE** [CARD] **[HERO]**

18. **H18** [PLAY_CARD] [against him] **WHEN** a tree is the only reason a minion is in his range and Clearing it costs one attack he can spare **THEN** Clear it. **WHEN** his Red is Defense 8 and still in hand **THEN** do not expect a damage race; discard or move the tree. **SOURCE** [REASONED; ADVICE "use trees to block"] **[HERO]**

19. **H19** [CHOOSE_SPACE] [Trample destination] **WHEN** several lines cross a prize **THEN** the one that also ends in the battle zone, not the longer one that strands him. **SOURCE** [ADVICE] **[GENERAL]**

20. **H20** [RESPAWN] **WHEN** he dies **THEN** the trees are still there. Respawn toward them, not toward a fresh empty zone, unless the push has already made those trees the wrong beach. **SOURCE** [rulings 2; REASONED] **[HERO]**

21. **H21** [CHOOSE_UNIT] [Dominance at the battle] **WHEN** excluding the heavy, or the minion that leaves them under the push threshold, is legal **THEN** that minion. He may pick a heavy. He should pick none if excluding one does not change the result and he would rather it die for someone's coins — it will not die in the battle if it does not count, and it will not pay coins either way if it is only excluded. Prefer the exclusion whenever the push flips. **SOURCE** [rulings 8] **[HERO]**

## 7. Sources

- Catalog `heroes/wuk.json` (2nd printing), handicap `wuk-handicap`, FAQ on the heavy during Stampede.
- `heroes/WukHero.kt`, `core/game/rulings/wuk.md`. Supply 3: [BGG 3466489](https://boardgamegeek.com/thread/3466489) and [BGG 3504393](https://boardgamegeek.com/thread/3504393), `nichik`, 2025-04-29, pointing at rulebook p.18 without correcting the count of 3.
- [BGG 2894009](https://boardgamegeek.com/thread/2894009) Wuk spotlight, Panetierre, 2022-07-04, playtester. **1st printing.** Used only for: plant beside minions and gaps; do not plant every round; attacks zone because he is slow; do not pair two slow heroes; do not chase snipers; Dominance-style effects leave the minion standing. Card texts, the walk-through rule, the adjacency ban, the 5-push, the heal-without-removing, and the "trees are minions" Ultimate are obsolete and were not turned into rules.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Zoner / Pusher, Cryomancy, 2026-10-09.
- Gamefound, Wolff Designa, Renowned pack update: https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/8 — flavour and role, not numbers.
- Fan FAQ mirror (designer on Discord, 2026-01, about the Ultimate's timing, not strategy): https://www.cockeyedandcurious.com/goa2/heroes/wuk — the engine already reads "after you resolve a card" as after the action finishes [rulings 12]. Not used as a playbook.
- No 2nd-printing community strategy guide found.

## Implementation notes

`WukHeuristics.kt` (ADR-085). The general book scores an attack card from its printed reach (`HContext.strikeFrom`). Where this hero's text narrows the targets, the playbook re-scores the card's PLAY_CARD outlook on the units the engine would really offer (`BatchD.strikeOn` + `strikeFix`): the card keeps its movement value and loses the hit it cannot make. That replaces the draft's flat −28 "fizzle" penalty, which also threw away the card's secondary Movement.

- **Implemented:** H1/H2 (Silver while fewer than two trees stand near the fight), H1/H11 (tree sites: beside an enemy minion, in the battle zone, never a minion spawn), H3 (Tree Slam: an adjacent minion, or a unit in range when a tree beside him can be spent; the handicap card heroes only), H4 (the tree-Red: an adjacent hero, or a unit in range beside a tree; Champion's second target), H5/H6/H19 (Trample: the best straight line through enemy heroes, who discard or die, and minions, who are defeated; the CONFIRM and the destination take that line), H9 (Dominance with enemy minions beside him), H10 (Gifts need a tree in radius), H17 (threat on hexes beside his trees), H20 (respawn toward the trees). `evalTerms`: +2 per tree beside an enemy minion (three at most); the general book already counts the tokens themselves.
- **Engine detail the general book misses:** tree placements (Saplings and March of Nature) are space questions with no subject, scored by the general book as Wuk standing there; the playbook takes that back.
- **Engine vs research:** Tree Slam's first bullet targets a *minion* adjacent to him (not any unit); the second spends a tree first and then must find a unit in range. Trample's discards and defeats need the straight-line choice ("Move normally" gives none). No disagreement.
- **Skipped:** H7/H8 (Toss and Canopy are space/unit sequences left to lookahead), H12 (DEFEND: general), H13 (lane side), H14–H16 (upgrade colour order), H18 (advice for the other side), H21 (Dominance's choice at the minion battle is the general minion worth).
- **Ablation (strength 8):** 61–35 of 96 = 63.5% (Wilson 53.6–72.5%), with the playbook against without it, 2026-11-21 (`AI_H_ABLATION=wuk AI_H_STRENGTH=8 AI_H_GAMES=96`: 48 line-ups, both sides).
