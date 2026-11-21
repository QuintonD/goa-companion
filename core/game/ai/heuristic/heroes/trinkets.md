# Trinkets (`trinkets`) — heuristic playbook

2nd printing. Dashboard: Attack 3–5, Defense 2–4, Initiative 6, Movement 7–8. Complexity 3. Catalog roles: sniper, damager, pusher, tokens. Advice in `heroes/trinkets.json`.

Units: `heuristic/DESIGN.md`. One minion of even-lane margin ≈ 15, one coin = 1, a card in hand ≈ 2.5. A hero kill is coins plus Life counters (1 / 2 / 3 by lowest tier). **[GENERAL]** is distance, counts, initiative, hand, revealed cards. **[HERO]** needs the Turret (one obstacle, not a token, persists across rounds and across his defeat), Barrier tokens, and the Disruptor "this turn" flag.

The Turret is terrain and an obstacle. It is not a unit, minion, hero, or token. Clear does not remove it. Placing it while it is out moves the one Turret [FAQ; rulings/trinkets.md 1; Galatolol, BGG 3616297, 2026-04-15]. A minion that would spawn on it spawns adjacent [same post; rulebook p.8 via rulings]. Trinkets is the actor on every Turret card; the Turret only restricts targets [FAQ].

## 1. Identity

Disruptor / Pusher [Cryomancy, BGG 3779890, 2026-10-09]. He does not finish on Gold: Gold cannot hit a hero. He builds a gun, makes the enemy waste a card walking around it, and leaves at init 12 with Movement 4.

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | SetUp | The Turret is the piece. Without it the attack cards have no primary [rulings 2]. |
| `idealDistance` | 2 to the Turret, 3+ to enemy heroes | Overlap of the two ranges is the shot. He himself wants to be at the edge of the zone so Gold can leave [Galatolol, BGG 3616297]. |
| `aggression` | 0.35 | Threaten, discard, take minions. Hero kills are the explosion or an ally's follow-up. |
| `laneWeight` | 0.7 | Alt-red double-takes and Gold's "defeat a minion" are how he wins. Galatolol: you cannot out-farm him. |
| `keepDefender` | true | Red is Defense 5–6 and is also the gun. Do not spend it on a shot that does not pay, if it is the only block. |
| `upgradeOrder` | Red first on the chosen branch, then the Blue or Green that matches, see §3 | |

## 2. Card-by-card

### `trinkets-gold` Rapid Redeployment — Skill, init 12, Defense 1, Movement 4

Choose one: move up to 3 and place the Turret adjacent; or defeat an adjacent minion.

- **For:** deploy at speed, take a second minion, or run. Not a finisher [BGG 3611965].
- **When:** early when the Turret is off the board or on the wrong side of a coming push. Late when he must leave (Movement 4 at init 12 dodges most attacks) or when the minion bullet is the second farm.
- **Place:** the empty adjacent hex that (a) overlaps his own range on an enemy minion, (b) blocks a one-hex gap, (c) will still be in the battle zone after the likely push. The Turret is placed at the end of the move, so it does not clear itself as an obstacle for the path [BGG 3611965].
- **Do not:** spend Gold only to nudge the Turret one hex when Silver can place it after everyone has moved; path into a space the Turret currently occupies and forget it is an obstacle.
- **In hand:** the escape. Defense 1, so it is not the block.

`trinkets-handicap` Ramshackle Turret is only the move-and-place, init 12, and reminds that the Turret is not a token and stays between rounds. No minion bullet. In a handicap game the Turret must come from this card or from Silver.

### `trinkets-silver` Salvage Parts — Skill, init 5, Defense 2, no Movement

Choose one: place the Turret adjacent; remove it and move up to 3; remove it and retrieve a discarded card.

- **For:** the reactive deploy. Find the hex, let the fast cards resolve, then put the Turret on the adjacent space with the most targets [BGG 3611965, the line STRATEGY.md already quotes].
- **When:** the Turret should move and Gold is being saved to flee. Or he is about to be hit, Silver is the retrieve, and Gold is in hand to rebuild within two turns. Cryomancy: do not scrap the Turret without that Gold.
- **Do not:** retrieve by scrapping when a block with Red survives and the Turret is covering two minions; move-3 by scrapping when he is not in danger and the Turret is already on the twins.
- **In hand:** Defense 2 plus three tricks. While the Turret is up, holding Silver is what makes him hard to commit against [BGG 3611965, "Playing against"].

### `trinkets-red-1` Makeshift Minigun — Ranged attack, init 8, Attack 3, Defense 5, Movement 4, Range 2

"Target a unit in range of both you and the Turret." +2 Attack if the target is in a straight line from him and from the Turret. No Turret: no primary [rulings 2, 5].

### `trinkets-red-2a` Gatling Gun — item: Defense. Init 9, Attack 3, Defense 6, Movement 4, Range 3

Same shot, longer range. `trinkets-red-3a` Supercharged Cannon (item: Defense, init 9, Attack 4, Defense 6, Range 3) makes the line bonus +3.

- **For:** a wide overlapping lens. Closer to the Turret means more shared hexes [BGG 3611965]. The +2/+3 is a setup, not the plan: shoot without it when the shot takes a minion or strips a card.
- **Best geometry:** Turret on a gap the enemy must cross to take a minion; he stands on a second line through that gap.
- **Do not:** play it with no Turret; stand in melee to "get the line" when Red is his only Defense 6 and an enemy Gold still acts.
- **In hand:** the block (Defense 5, then 6).

### `trinkets-red-2b` Steam Discharge — item: Initiative. Init 9, Attack 4, Defense 6, Movement 4, Range 4

A unit in range and adjacent to the Turret. May repeat once on a different enemy unit. `trinkets-red-3b` Flame Belcher (item: Radius, init 9, Attack 5, Defense 6, Range 4) repeats up to two times. Each repeat needs a new enemy unit in range and adjacent to the Turret [rulings 6].

- **For:** the double-take. Turret between two enemy minions, ideally the twins, and leave it there for the wave that returns to the centre [BGG 3611965; Galatolol, BGG 3616297]. Second site: a choke, adjacent to one enemy minion, so a hero who steps through is the second target.
- **He** can stand up to range 4 away. Alt-green and primary-blue do not need him near the Turret at all [BGG 3611965].
- **Do not:** chase a triple-take so hard that Gold is gone and he is standing on the beach [Galatolol: "Don't be greedy"].
- **In hand:** Defense 6, and the Initiative item on the tier-2 card speeds every other card.

### `trinkets-green-1` Early Prototype — Skill, init 3, Defense 2, Movement 2, Radius 2

If he is in radius of the Turret, swap with a unit or token in the Turret's radius, then remove the Turret.

- **For:** one teleport, then the gun is gone. Only when the swap is a kill setup or an escape and Gold can rebuild next turn.
- **Do not:** swap a minion one hex and delete the Turret.

### `trinkets-green-2a` Updated Design — item: Attack. Init 3, Defense 2, Movement 2, Radius 2

Same swap, Turret stays. `trinkets-green-3a` Perfected Design (item: Attack, init 2, Defense 3, Movement 2, Radius 2): if he is in the Turret's radius, swap with a unit or token in that radius, or place himself into an empty space in that radius.

- **For:** swap an ally out of a hit, swap himself with an enemy minion to deny the attack, or (tier 3) blink to the Turret with no partner [BGG 3611965].
- **When:** he is in radius 2 of the Turret and the swap's end is safer or puts an ally on the target.
- **Do not:** swap an enemy hero onto a friend who did not ask for the fight; the Turret itself is not a swap target [rulings 7].

### `trinkets-green-2b` Self-Destruct — item: Defense. Init 3, Defense 2, Movement 2, Radius 3

Up to two enemy heroes in the Turret's radius discard, if able. Then the Turret is removed. `trinkets-green-3b` Emergency Protocol (item: Range, init 2, Defense 3, Radius 3): those heroes discard or are defeated, then the Turret goes.

- **For:** the kill card. Two discards, or at tier 3 two heroes with empty hands. He does not need to be near the Turret [BGG 3611965].
- **When:** turn 3–4, Turret radius already covers the injured hero's retreat, an ally cuts the other side. Early only when both discards will be followed up this round and Gold is in hand.
- **Do not:** detonate with no hero in radius (the Turret still leaves — rulings 9: the discards are "up to", so 0 is legal, and then the Turret is gone for nothing). Do not detonate on turn 1 of a round whose wave he wanted the alt-red for.

### `trinkets-blue-1` Disruptor Jolt — Skill, init 10, Defense 3, Movement 3, Radius 3

This turn: before any enemy hero in the Turret's radius performs a primary action, that hero discards if able; if they do, the effect ends. `trinkets-blue-2a` Disruptor Pulse (item: Attack, init 10, Defense 4, Radius 4) is the same. `trinkets-blue-3a` Disruptor Grid (item: Movement, init 11, Defense 4, Radius 4): discard or be defeated.

- **For:** a tax on the first primary inside the radius. Primary Defense counts, so a Defense-primary block discards twice [Cryomancy, BGG 3611965; rulings 8]. A secondary Defense does not. A repeat is another primary. No card in hand: Jolt/Pulse do nothing and the effect stays; Grid defeats them and the effect stays.
- **When:** two or more enemies are in radius, or one enemy is about to take a minion with a slow attack. Init 10–11 is before most Reds.
- **Do not:** play it when the only heroes in radius will Hold or take a secondary move. They are allowed to, and the trap whiffs [BGG 3611965].
- **In hand:** Defense 3–4. Weaker than Red.

### `trinkets-blue-2b` Deployable Barrier — item: Initiative. Init 10, Defense 4, Movement 3, Radius 3

Up to 2 Barrier tokens in radius, at least one adjacent to the Turret. He and friendly heroes have +1 Defense per Barrier they are adjacent to. `trinkets-blue-3b` Deployable Bastion (item: Initiative, init 11, Defense 4, Radius 3) places up to 3. No Turret: no placement [rulings 10]. Barriers are tokens: end of round, Clear, not his defeat. The +1 is a property, not removed by "ignore minion modifiers" [rulings 11]. Supply is not limited in this engine.

- **For:** a friendly bruiser's defense, and a wall. One Barrier in a one-hex gap is often worth more than the +1 [BGG 3611965].
- **When:** an ally must attack and is short 1 Defense, or a ranged enemy's only line crosses a hex he can fill.
- **Do not:** wall his own team off the minions; spend the turn placing Barriers when alt-red can take two minions and nothing is about to hit the ally [Galatolol: don't bother unless nothing better].

### `trinkets-purple-4` Unlimited Firepower

+1 Range and +1 Radius on cards that have those icons. "In range of the Turret" grows with it [rulings 12]. It does nothing for Gold, Silver, or a card with no Range/Radius icon.

## 3. Upgrade path

Two real heroes, and they do not mix well [BGG 3502775's warning is Wasp's, but Galatolol and Cryomancy say the same here: commit].

**Roadrunner (Gatling, run):** `trinkets-red-2a` first (Range 3, Defense item). Then `trinkets-green-2a` Updated Design (Attack item; the swap that does not delete the Turret) or `trinkets-green-2b` if the table clumps. Then `trinkets-blue-2a` Disruptor Pulse (Attack item, radius 4). Tier III: `trinkets-blue-3a` Disruptor Grid first when init 11 plus "or defeated" catches attacks; `trinkets-red-3a` for the +3 line; Green last, leaning `trinkets-green-3b` Emergency Protocol so the turretless kill exists. [BGG 3616297]

**Fort Knox (Steam Discharge, hold the box):** `trinkets-green-2b` Self-Destruct first if tier-1 Green was going to delete the Turret anyway; else `trinkets-red-2b` before the wave returns to the twins. `trinkets-blue-2b` Deployable Barrier for the second Attack item and the walls. Tier III: `trinkets-green-3b`, then `trinkets-red-3b` Flame Belcher (Defense 6, up to three shots, Radius item which also grows Barrier and Disruptor if he swaps). Stay on Bastion to force the kill box, or swap to Disruptor Grid if he needs init 11 more than walls. [BGG 3616297]

**Tuck:** the unused branch. Defense items want to sit on the Gatling path because Red is the block. Initiative items want to sit on Steam Discharge (`trinkets-red-2b`, both Barrier cards). Radius is the Flame Belcher tuck and is good on every Turret radius. Range is Emergency Protocol's tuck and helps only the line-attack and the swap's range-less cards not at all — tuck Range when he is not on a Range card.

**Phase:** levels 1–3, get one attack that works and do not be on the wrong beach [BGG 3616297: he suffers a wrong-side push more than most]. Levels 4–6, either the radius-4 trap or the twins gun, not both. Do not pick him into a ranged-heavy team that never has to walk into the Turret [Galatolol].

## 4. Synergies and counters

**With:** a finisher who converts his discard (Tigerclaw, Whisper, Ursafar). A slow ally who wants the Turret placed on their turn — Silver at init 5 can do that. He does not need to stand next to them.

**He fears:** losing Red to a discard (then he farms only with Gold and cannot run) [BGG 3611965]. Ranged enemies who shoot him without walking into the radius [Galatolol]. Anyone who threatens him on the turn Silver is his only remaining trick and the Turret is down. Body-blocking does not kill the Turret; ignoring it does.

**Against him:** threaten so he spends Gold leaving, or discard his Red. Do not end your turn on a straight line from both him and the Turret. Do not stand where Steam Discharge's Turret can be dropped beside you and your minion. If he is on alt-red and the Turret is on your twins, take one twin before he acts or accept the double. His Silver, while unplayed and the Turret is up, means a hit that does not kill him becomes a retrieve plus a run. The Turret cannot be Cleared, swapped, or pushed [Reddit r/boardgames, robin-m / Regniznevik, 2025-04-27; FAQ].

## 5. Common mistakes

- Playing a Turret attack with no Turret on the board (the primary is not offered; a naive bot then Holds).
- Scrapping the Turret to heal or to move when Gold is already spent [BGG 3611965].
- Re-placing the Turret every round instead of leaving it for the return wave.
- Using Gold's place-bullet when the minion-bullet takes the second minion and the Turret is already on the twins.
- Detonating at radius 0.
- Walling his own ally's minion with a Barrier.
- Treating the Turret as a token in eval (Clear, end of round). That is the old net's miss [AI.md set-up heroes].
- Standing in the centre of the beach to take one more minion [Galatolol].
- Copying Cryomancy's class label onto a farm-only turn: if the Turret is down and he is deep, Gold leaves, it does not dive.

## 6. Heuristic rules

`evalTerms`:

- Turret on the board: +6, plus +4 if it is adjacent to two enemy minions, plus +3 if it sits in a one-hex gap between terrain or a zone edge. 0 if it is more than two moves from the battle zone (wrong beach). The Turret is not removed at end of round, so this term persists. [HERO]
- Each Barrier adjacent to a friendly hero who can still be attacked this turn: +3. Barriers with no friend and no gap: +0.5 (they die at end of round). [HERO]
- Disruptor effect in force with at least one enemy in the Turret's radius who has not acted: +4. [HERO]

`threatAdjust`: the Turret is a second origin. See H16–H18.

1. **H1** [PLAY_CARD] [`trinkets-gold`] **WHEN** the Turret is off the board, or is outside the battle zone he will fight in after the projected push **THEN** move up to 3 and place it adjacent, on a hex in range of an enemy minion, preferring a hex that also blocks a gap. **WEIGHT** +10 (the gun comes back; without it Red and Self-Destruct do not exist). **SOURCE** [ADVICE; rulings 2; BGG 3616297] **[HERO]**

2. **H2** [PLAY_CARD] [`trinkets-silver`] **WHEN** the Turret is up, he has a legal adjacent hex that covers a new pair of targets or a gap, and Gold is still in hand **THEN** place with Silver after faster cards have resolved, and do not also move it with Gold this round. **WEIGHT** +6 vs spending Gold on the same place. **SOURCE** [BGG 3611965] **[HERO]**

3. **H3** [PLAY_CARD] [`trinkets-red-2b`, `trinkets-red-3b`, `trinkets-red-1` only if he has no alt-red] **WHEN** the Turret is adjacent to an enemy minion and he is in range of that minion **THEN** shoot the minion, and repeat on a second enemy unit adjacent to the Turret when one exists (a second minion before a hero, unless the hero's defense loses). **WEIGHT** 15 per minion, stop at two unless the third is free and he is not in a kill zone. **SOURCE** [BGG 3611965; Galatolol BGG 3616297] **[HERO]**

4. **H4** [CHOOSE_SPACE] [placing the Turret for alt-red] **WHEN** two enemy minions share a neighbour hex **THEN** that hex. Prefer the centre twins when the battle zone is the middle or is about to return there. **WEIGHT** +15 for the second minion the repeat will reach. **SOURCE** [BGG 3611965] **[HERO]**

5. **H5** [PLAY_CARD] [`trinkets-red-1`, `trinkets-red-2a`, `trinkets-red-3a`] **WHEN** a unit is in both ranges **THEN** prefer a target that is also in a straight line from him and from the Turret when the +2/+3 flips a defense or is free on a minion. Otherwise take the minion anyway. **WEIGHT** +6 when the bonus flips a block; else the minion's 15. **SOURCE** [CARD; rulings 5; BGG 3611965] **[HERO]**

6. **H6** [PLAY_CARD] [`trinkets-blue-1`, `trinkets-blue-2a`, `trinkets-blue-3a`] **WHEN** at least one enemy hero is in the Turret's radius, has a card, and their likely primary is an attack, a movement, or a setup skill, and he still has Red or Gold to follow **THEN** play Disruptor. Prefer the turn when two enemies are inside. **WEIGHT** +4 for the discard, +8 if the victim's hand will be empty on turn 4, +10 on Grid if they have no card (the defeat). **SOURCE** [BGG 3611965; rulings 8] **[HERO]**

7. **H7** [PLAY_CARD] [Disruptor] **WHEN** every enemy in radius can legally Hold or take a non-primary and has a reason to (no minion they want, no ally to save) **THEN** do not play it. **WEIGHT** −3. **SOURCE** [BGG 3611965] **[HERO]**

8. **H8** [PLAY_CARD] [`trinkets-green-2b`, `trinkets-green-3b`] **WHEN** one or two enemy heroes are in the Turret's radius, at least one has 0 cards or only a card the team can punish, and (Emergency Protocol or an ally acts after this discard) **THEN** detonate, including that hero. **WHEN** the radius is empty **THEN** do not. **WEIGHT** a strip ≈ 8 each; a defeat on Protocol ≈ the hero kill; the lost Turret ≈ −10 unless Gold is in hand (+6 back). **SOURCE** [BGG 3611965; rulings 9] **[HERO]**

9. **H9** [PLAY_CARD] [`trinkets-green-2a`, `trinkets-green-3a`] **WHEN** he is in the Turret's radius and a swap puts him out of an attack that would hit, or puts an ally onto a minion they cannot reach, or (Perfected Design) a place-bullet does that with no partner **THEN** swap or place. **WEIGHT** +8 for a denied hit, +15 if the ally then takes a minion they otherwise miss. **SOURCE** [BGG 3611965] **[HERO]**

10. **H10** [PLAY_CARD] [`trinkets-green-1`] **WHEN** the swap is worth at least 15 (a kill or an escape from a lethal hex) and Gold is in hand or the round is over after this turn **THEN** swap and accept the removal. **ELSE** never. **WEIGHT** −10 if Gold cannot rebuild. **SOURCE** [CARD; BGG 3611965] **[HERO]**

11. **H11** [PLAY_CARD] [`trinkets-blue-2b`, `trinkets-blue-3b`] **WHEN** a friendly hero is about to be attacked and one Barrier adjacent to them flips the defense, or a Barrier on a gap deletes an enemy's only path to a minion **THEN** place that token first (the one beside the Turret), then a second if it also matters. **WEIGHT** +8 for a saved ally card, +15 if the gap saves a minion. **SOURCE** [BGG 3611965; rulings 11] **[HERO]**

12. **H12** [PLAY_CARD] [`trinkets-gold`] **WHEN** an enemy attack that beats his remaining defense can reach his hex and he has Movement 4 still on Gold **THEN** move up to 3 toward safety. Place the Turret only if the adjacent hex at the end is a good gun site; the move does not require the place to be "useful", but the bullet is one action so the place happens. If the Turret is already perfect, take the minion bullet instead only when the minion is safe. **WEIGHT** his life over a 15-point minion. **SOURCE** [BGG 3611965; Galatolol] **[GENERAL]**

13. **H13** [CHOOSE_OPTION] [`trinkets-gold`] **WHEN** the Turret is already adjacent to the minions Red will shoot and an enemy minion is adjacent to him **THEN** defeat the minion. **WHEN** the Turret is absent **THEN** the place bullet. **SOURCE** [CARD] **[HERO]**

14. **H14** [CHOOSE_OPTION] [`trinkets-silver`] **WHEN** he is about to be defeated and a discarded card would have blocked the next hit and Gold is in hand **THEN** remove and retrieve. **WHEN** he must cross the map and the Turret is on the wrong beach **THEN** remove and move 3. **ELSE** place. **SOURCE** [BGG 3611965] **[HERO]**

15. **H15** [UPGRADE_CARD] [any] **WHEN** the battle zone is about to be the centre and alt-red is not yet taken **THEN** upgrade Red into `trinkets-red-2b` / `trinkets-red-3b` before the twins spawn. **WHEN** enemies are ranged and never walk in **THEN** do not start this hero's alt-red plan; Gatling plus Disruptor. **SOURCE** [BGG 3616297; Galatolol] **[GENERAL]**

16. **H16** threatAdjust [`trinkets`] **WHEN** the Turret is on the board **THEN** every hex in range of both Trinkets and the Turret is threatened by his Red at the printed Attack, plus 2 (or 3 on Supercharged Cannon) if the hex is in a straight line from both. Steam Discharge / Flame Belcher threaten only hexes adjacent to the Turret, out to his Range, and a second unit adjacent to the Turret is part of the same action. **SOURCE** [CARD; rulings 2] **[HERO]**

17. **H17** threatAdjust [`trinkets`] **WHEN** Disruptor is in force **THEN** an enemy primary action whose actor is in the Turret's radius costs that actor a card (or their life, on Grid, if they have none) before the action. Secondary actions do not. **WEIGHT** +4 to the cost of those primaries. **SOURCE** [rulings 8] **[HERO]**

18. **H18** [PLAY_CARD] [against him] **WHEN** his Red is still in hand and an attack can force it as the defense, and the Turret is what is winning him the wave **THEN** prefer that attack over a minion. **WHEN** Silver is unplayed, the Turret is up, and the attack does not kill him **THEN** the attack is worth less (he retrieves or runs). **SOURCE** [BGG 3611965] **[HERO]**

19. **H19** [CONFIRM] [repeats on Steam Discharge / Flame Belcher] **WHEN** another enemy unit is in range and adjacent to the Turret **THEN** yes, minion before hero unless the hero dies. **SOURCE** [rulings 6] **[HERO]**

20. **H20** [RESPAWN] [any] **WHEN** he is dead and the Turret still stands (it does) **THEN** respawn toward that Turret, not toward a random spawn, and spend the turn moving to range of it. **WEIGHT** +6 to paths that re-enter Red's range this round. **SOURCE** [FAQ: the Turret stays if he dies; REASONED] **[HERO]**

## 7. Sources

- Catalog `heroes/trinkets.json`: ids, numbers, text, advice, FAQ. Handicap `trinkets-handicap`.
- `heroes/TrinketsHero.kt`, `core/game/rulings/trinkets.md`. Designer on the one Turret: [BGG 3485218](https://boardgamegeek.com/thread/3485218), `nichik`, 2025-03-26.
- [BGG 3611965](https://boardgamegeek.com/thread/3611965) Trinkets spotlight (2nd printing), Cryomancy, 2025-11-15, community.
- [BGG 3616297](https://boardgamegeek.com/thread/3616297) Build & Go, Cryomancy, 2025-11-23; Galatolol, 2026-04-15, community (2v2, alt-red, do not Clear the Turret, spawn-adjacent).
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Disruptor / Pusher, Cryomancy, 2026-10-09.
- Reddit r/boardgames, 2025-04-27, "can Trinkets the scavenger turrets be targetted": https://www.reddit.com/r/boardgames/comments/1k95ljc/ — agrees with the FAQ (obstacle, not a token).
- 1st-printing spotlight [BGG 2910990](https://boardgamegeek.com/thread/2910990) (Drakonis1, 2022) was not used; the 2nd-printing cards differ.
- No strategy thread after 2026-10-24 is about Trinkets. Galatolol's April 2026 comment is the newest community note.

## Implementation notes

`TrinketsHeuristics.kt` (ADR-085). The general book scores an attack card from its printed reach (`HContext.strikeFrom`). Where this hero's text narrows the targets, the playbook re-scores the card's PLAY_CARD outlook on the units the engine would really offer (`BatchD.strikeOn` + `strikeFix`): the card keeps its movement value and loses the hit it cannot make. That replaces the draft's flat −28 "fizzle" penalty, which also threw away the card's secondary Movement.

- **Implemented:** H1/H13 (Gold rebuilds a missing or stranded Turret; with the Turret sited, Gold's other bullet defeats an adjacent minion outright), H2 (Silver rebuilds the Turret and keeps Gold), H3/H5 (Cannon: targets in range of him and of the Turret; Steam Discharge: in range and adjacent to the Turret, plus its repeats on other enemy units), H4 (Turret hex beside two enemy minions, in the battle zone), H6/H7 (Disruptor only with an enemy hero in the Turret's radius), H8 (Self-Destruct: the discards, or Protocol's kills, minus the lost Turret), H10 (Early Prototype only to escape with Gold in hand), H11 (Barrier: needs the Turret; tokens beside friendly heroes), H14 (Salvage's bullets), H16 (threat on the double line), H20 (respawn toward the Turret). `evalTerms`: Turret +4, +2 per adjacent enemy minion (two at most), +1.5 per Barrier beside a friend.
- **Engine detail the general book misses:** the Turret's and a Barrier's space questions have no subject, so the general book scores them as if Trinkets stood there. The playbook takes that placement score back (`BatchD.unplace`) and scores the site.
- **Engine vs research:** Disruptor resolves with no Turret and does nothing (no fizzle); Self-Destruct with nobody in radius still removes the Turret; the first Barrier must touch the Turret, so with no Turret Deployable Barrier places nothing; Early Prototype out of the Turret's radius does nothing. All are read as above.
- **Draft bug fixed:** the draft matched Salvage's "move" bullet with `contains("move")`, which also matched "Remove the Turret" in all three bullets.
- **Skipped:** H9 (Updated Design's swap target needs a forecast of the enemy attack; the swap is left to lookahead), H12 (an escape is the general book's exposure), H15 (the build is the KEEP default), H17/H18 (advice for the other side), H19 (the repeat CONFIRM goes through lookahead).
- **Ablation (strength 8):** 51–45 of 96 = 53.1% (Wilson 43.2–62.8%), with the playbook against without it, 2026-11-21 (`AI_H_ABLATION=trinkets AI_H_STRENGTH=8 AI_H_GAMES=96`: 48 line-ups, both sides).
