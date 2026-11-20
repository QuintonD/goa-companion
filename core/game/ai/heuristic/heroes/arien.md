# Arien (`arien`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/arien.json`. Engine: `ArienHero.kt`.
No `core/game/rulings/arien.md`. Complexity 1. Stats: Attack 8/8, Defense 5/6, Initiative 4/4, Movement 5/6.
Catalog roles: tactician, disabler, durable, pusher. No tokens.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances. `CTX hero` needs a fact only this playbook tracks.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Fighter | Melee brawler who also locks skills and repositions. Not a pure tank (Defense 5/6) and not a pure assassin. |
| idealDistance | 1 | Primary red and gold are adjacent attacks. Alt red (`arien-red-2b`/`3b`) wants 2; raise ideal distance to 2 only after that branch is kept. |
| aggression | 0.60 | Dives the wave when a Duelist card is in hand; otherwise he is a normal melee body. |
| laneWeight | 0.55 | Minions are how the red line connects, and both greens move him onto the wave. Kills are the bonus on that line, not a separate plan. |
| keepDefender | true | `arien-blue-1` Defense 5 (ignores minion modifiers) or `arien-red-1` Defense 6 is the card that lets him stand in the wave. Silver is Defense 3 and has no movement. |
| upgradeOrder | see §3 | Red and blue define the role. |

He wants to stand where a minion and an enemy hero share a straight line, or just outside a skill he is about to cancel. Lane and kill are the same action when the line is there: take the minion, the hero behind it discards.

## 2. Card-by-card

Initiative band used below: Gold ~11, Blue ~9–11, Red ~8–9, Green ~3–4, Silver 13 with no movement. Arien's own Initiative stat is 4, so his colours sit in the normal band. Items add +1 to every card.

### `arien-gold` Noble Blade — Attack 4, Defense 2, Movement 1, Initiative 11

Adjacent attack. Before it, he may move one other unit adjacent to the target exactly 1 space (never himself). Engine: `CONFIRM` then `CHOOSE_UNIT` then the attack.

For: strip a minion off the target so the Defense total drops; slide a friendly minion onto a space he or an ally can take next; pull an ally one space out of an enemy's reach; move a second enemy off a follow-up. [ADVICE] "Use your gold card to move allies out of harm's way, or to set up future attacks." [BGG 3487765] same uses, plus moving a minion that was supplying a defense modifier.

When: turn 2–4, after he is already adjacent. Initiative 11 beats typical reds, so it connects before most escapes. Best target is a hero whose defense total falls under 4 once one adjacent minion steps away, or a minion when no hero is in reach.

When not: as a travel tool (Movement 1). Do not spend the "may" when the moved unit's new space is worse (opening a line for an enemy charge, or pulling a friendly minion out of the Battle Zone — it walks back after the action). Defense 2: a terrible card to be caught holding as the only defender against a red.

Handicap `arien-handicap` Ceremonial Blade replaces this in a 5-player game: Attack 3, Defense 1, Initiative 10, and the before-step moves a minion only, and the target must be a hero. Same jobs, weaker numbers. Do not plan a minion take on the handicap gold.

### `arien-silver` Spell Break — Defense 3, Initiative 13, Radius 3, Skill

This turn, enemy heroes in radius 3 cannot take Skill actions except on gold cards. Attacks, movement, and defense still work. No movement value, so the primary does not relocate him. Engine: no choice; the effect is an active flag.

For: blanking a non-gold skill that would otherwise win the turn (heals, summons, movement skills, Wuk's tree silver, Ignatia's Equilibrium). [ADVICE] it "can force opponents to play around it while it's in your hand." [BGG 3487765] Cryomancy: the threat of holding it is often as useful as playing it; [BGG 3487765 post 2] Dark_lord7: cancelling Ignatia's grey skill and Wuk's tree silver is devastating, and the card is weak in the beginner box because those players do not use skills.

When: he is already inside radius 3 of the skill user, and that hero's unresolved card is a non-gold skill (after the reveal) or, before the reveal, that hero's only remaining high-value card is such a skill. Initiative 13 means he acts before almost every gold.

When not: most rounds. Do not play it with no skill-user in radius 3, and do not walk into the radius on this card (no movement). Do not spend it to "do something" on turn 1. Defense 3 and no boots: a bad last card. Holding it is the default.

### `arien-red-1` Dangerous Current — Attack 6, Defense 6, Movement 4, Initiative 8

Adjacent attack. Before it, up to one enemy hero in the 2 spaces in a straight line directly behind the target discards a card or is defeated. No hero behind is required (the attack still happens). Engine: `CHOOSE_UNIT` optional for the hero behind, then the attack.

For: the melee brawler attack and the area denial. [BGG 2432847, designer `nichik`, 2020, 1st printing, same behind-the-target mechanism] "it is secretly an area denial which you trigger by attacking a minion and forcing the opponent to move away to avoid discard." Best used on a minion that will not move, with the hero standing on one of the two spaces behind it. Attack 6 is a real hit; Defense 6 is his best early block.

When: he is adjacent to a legal target and either (a) an enemy hero occupies one of those 2 spaces, or (b) the minion is the one that keeps the lane. Initiative 8 loses to golds and most blues, so a hero who can step off the line with a faster card will do so — the value is often the space they refuse to stand on.

When not: into a hero who still holds a Defense 6+ card and is not on a kill clock, if a minion take is available on the same card via the line. Do not treat Movement 4 as part of the attack; the attack does not move him.

### `arien-red-2a` Raging Stream — Attack 7, Defense 7, Movement 4, Initiative 8, item Initiative

Same as `arien-red-1` with 3 spaces behind the target, Attack 7, Defense 7. [BGG 3487765] "an attack as strong as Brogan's charge" at an initiative people cannot ignore, and the line is easiest through a minion. The Initiative item makes every other card 1 faster, which is what lets this slow red catch heroes who did not take Initiative themselves.

### `arien-red-3a` Violent Torrent — Attack 7, Defense 7, Movement 4, Initiative 9, item Initiative

Same, 5 spaces behind, and may repeat once on a different unit (`CONFIRM`). [BGG 3487765] one of the easier repeats: two melee targets means two attacks. Second target must be a different unit. Use the repeat whenever a second enemy unit is adjacent; a second minion is a full extra lane step (~15) and a second hero is a second discard.

### `arien-red-2b` Rogue Wave — Attack 4, Defense 4, Movement 4, Range 2, Initiative 8, Ranged, item Defense

Ranged attack with no line restriction. After a connected attack, he may push an enemy unit adjacent to him up to 2 (`CHOOSE_UNIT` optional, `CHOOSE_NUMBER` 0..2). [BGG 3487765] the reliable minion tool; the push is for bullying, not a combo to force. Push an enemy hero out of their own red's reach, or push an enemy minion to an ally. The Defense item patches his 5/6 stat.

When not: as a finisher (Attack 4 before items). Do not push a unit into a space that opens a charge line onto him or an ally. Defense 4 is a weak block against reds — this branch gives up `arien-red-1`'s Defense 6.

### `arien-red-3b` Tidal Blast — Attack 4, Defense 4, Movement 4, Range 2, Initiative 9, Ranged, item Movement

Same, push up to 3. Still Attack 4 printed. The Movement item helps the greens and the silver's lack of boots only on movement actions, not on this attack.

### `arien-green-1` Liquid Leap — Defense 3, Movement 2, Range 2, Initiative 4, Skill

Place himself on an empty space in range 2 that has no spawn point and is not adjacent to an empty spawn point. Engine: `CHOOSE_SPACE`. Not movement, so Slippery-style caps do not apply, and he can cross a wall.

For: a slow relocate onto the next turn's attack. [BGG 3487765] at tiers 1–2 the spawn restrictions make it hard inside the wave. Use it at the end of the turn (Initiative 4, often last) so the destination is still there.

When not: any space adjacent to an empty spawn, any spawn point, any space that leaves him adjacent to two enemy heroes with his red already played. Defense 3.

### `arien-green-2a` Magical Current — same text, Range 3, item Attack

The Attack item is the one that makes gold and the alt red actually kill. Range 3 is the first leap that changes zones reliably.

### `arien-green-3a` Stranger Tide — Defense 4, Movement 2, Range 3, Initiative 3, item Radius

Drops the "not adjacent to an empty spawn point" clause. Only "without a spawn point" remains. [BGG 3487765] this is the chase and escape tool: over a barricade, onto a wounded hero. The Radius item widens `arien-silver` (3) and `arien-blue-3b` (printed radius 1). Initiative 3 is last. [BGG 3084849] is a rules thread on this card; no strategy beyond the text.

### `arien-green-2b` Arcane Whirlpool — Defense 3, Movement 2, Range 4, Initiative 4, Skill, item Defense

Swap with an enemy minion in range 4. Not movement. Engine: `CHOOSE_UNIT`. [BGG 3487765] the long jump that also hands the minion toward his side. Best swap is a twin (a second minion is standing there to take) or a minion an ally can reach and he cannot.

When not: the swap that lands him beside two enemies with no Duelist card in hand, or that delivers a minion to the enemy. Range 4 will reach minions he should not be standing on.

### `arien-green-3b` Ebb and Flow — same, and if the swapped minion was adjacent, he may repeat once (`CONFIRM`). Item Defense.

Repeat only when the second swap also improves his space or passes a second minion. A repeat that was adjacent puts him back near the first minion's new space.

### `arien-blue-1` Aspiring Duelist — Defense 5, Movement 3, Initiative 9, Primary is the defense text

Ignore all minion defense modifiers, friendly and enemy. No attack primary (the card is a defense card). Played as a primary it does nothing but the movement/fast-travel; the text applies when it is discarded to defend.

For: standing next to enemy minions. The same text makes friendly minions stop helping him. Defense 5 plus no minion penalty is how he lives in the wave.

When not: do not defend with it against an Attack 6+ if `arien-red-1` (Defense 6) is in hand and he is not standing in enemy minions. Do not play it as the action on a turn he needed the red.

### `arien-blue-2a` Expert Duelist — Defense 6, Movement 3, Initiative 10, item Initiative

Same ignore-modifiers, plus this turn he is immune to attack actions of every enemy hero except the attacker he just blocked. Skills still discard or defeat him. [BGG 3487765] discard this first against a double tap, then leave. Initiative 10 is fast enough to matter as a movement action too, but the text is the defense.

### `arien-blue-3a` Master Duelist — Defense 6, Movement 3, Initiative 10, item Range

The immunity lasts the whole round, still except the hero who attacked him when he discarded it. [BGG 3487765] he can chase one target and ignore the others' attacks, or take one hit and turn on someone who can no longer attack him. Enemy plan: do not double-tap him; send one hero, or use skills. The Range item extends `arien-red-2b`/`3b` and the leaps.

### `arien-blue-2b` Slippery Ground — Defense 6, Movement 3, Initiative 10, Primary Movement, item Attack

This turn, enemy heroes adjacent to him cannot fast travel and cannot move more than 1 with a movement action. Card-text moves are not capped. Fixed adjacency: a Radius item does not widen it (`arien-blue-3b` is the one with a radius). [BGG 3487765] play at Initiative 10 to catch heroes who were about to leave on a blue, or to ruin a red/blue movement onto a minion. Then an ally's green walks up.

When not: if the only threat is a card-text dash (Brogan's charge, Tigerclaw's blink). Those ignore this cap. Defense 6 is excellent; the Attack item is the reason to take the branch when he is on alt red.

### `arien-blue-3b` Deluge — Defense 6, Movement 3, Initiative 10, Radius 1, item Attack

Same cap, but "in radius" instead of adjacent. Printed radius 1, so it matches Slippery Ground until a Radius item (`arien-green-3a`) makes it 2. [BGG 3523913] the whole difference from Slippery Ground is that radius. Do not expect radius 2 without that item.

### `arien-purple-4` Living Tsunami — Ultimate, no stats

Once per turn, before he performs an Attack action, he may move 1. Declining does not spend it. Engine: `CONFIRM` + `CHOOSE_SPACE` on `BeforeAction` of an attack, not of a skill. It does not fire on Spell Break, the leaps, or the whirlpool.

For: step onto the line, or step off a bad adjacent hex, and still attack. It does not stack a second step. Count it as +1 melee reach on gold and primary red only.

## 3. Upgrade path

Red and blue define the role [ADVICE]. Green is mobility on either branch [ADVICE].

Default path (first games, and the bot's start): primary red, primary blue, primary green.

1. Tier II first: `arien-blue-2a` if he is already fighting in the wave (Initiative item + the double-tap immunity). Else `arien-red-2a` (Initiative item + 3-space line). Cryomancy's even build in [BGG 3493559] opens `arien-red-2b` for the Initiative item when he wants to be an all-rounder; that is the alt path, not the default.
2. Tier II second: the other of red-2a / blue-2a. If the team needs a reliable ranged minion take, `arien-red-2b` instead of red-2a, and then he must respect Defense 4.
3. Tier II third: `arien-green-2a` (Attack item, range 3 leap). Take `arien-green-2b` only when an ally is the one who should receive minions and Arien is the diver.
4. Tier III first: `arien-red-3a` if he stayed primary (the repeat). If he is on alt red, `arien-blue-3b` first for the Range item on the wave [BGG 3493559], then `arien-red-3b`.
5. Tier III second: `arien-blue-3a` on the primary path (round-long immunity). On the assassin path, `arien-blue-3b` plus `arien-green-3a`'s Radius item so Deluge is radius 2 [BGG 3493559].
6. Tier III last: `arien-green-3a` on the primary path. The leap loses its spawn-adjacency restriction and the Radius item feeds Spell Break.

Items to tuck (the card not kept in hand):

- Keep the Initiative item from `arien-red-2a` or `arien-blue-2a` in the item row. Arien's stat is 4; without an Initiative item his red (8) loses to enemy blues.
- Keep one Attack item (`arien-green-2a` or `arien-blue-2b`) so gold's 4 and alt red's 4 can threaten a defended hero.
- Keep `arien-green-3a`'s Radius item if Spell Break or Deluge is part of the plan; otherwise `arien-blue-3a`'s Range item if he is on alt red.
- Do not tuck the only Defense 6. Primary red and primary blue are both Defense 6+; he can spare one as the item only after the other is in hand.

Branch summary:

| Colour | Default | Why | Switch when |
|---|---|---|---|
| Red | a `arien-red-2a`/`3a` | Line discard through minions, Defense 7, repeat at III. | The team has no other ranged minion-taker, or he is dying in melee: `2b`/`3b`. |
| Blue | a `arien-blue-2a`/`3a` | Ignore minion modifiers and stop focus fire. | He is the one chasing and an ally already locks the target: `2b`/`3b`, and only with a Radius item at III. |
| Green | a `arien-green-2a`/`3a` | Leap that eventually ignores empty-spawn adjacency; Attack then Radius. | Allies need minions handed to them: `2b`/`3b`. |

## 4. Synergies and counters

Teammates: anyone who hits the hero Arien's gold slides, or who takes the minion his whirlpool delivers. A slow ally red becomes safe if Arien's Slippery Ground / Deluge has already capped the enemy's movement. He pairs with skill-light teammates; he is the one who answers skill-heavy enemies.

Enemies he fears: heroes whose threat is an attack, not a skill (Brogan's charge, Garrus's alt red, Emmitt's temporal red). Spell Break does not touch those. Heroes who can stand off his line and still hit him at range. Focus fire after he has spent `arien-blue-3a`'s exception on the wrong attacker.

Playing against him (this is `threatAdjust` and the enemy plan):

- While he could hold `arien-silver`, any enemy in radius 3 should not commit their only non-gold skill. The card is a threat in hand [ADVICE].
- His primary red threatens not only adjacent hexes but the 2, then 3, then 5 hexes straight behind whatever he can melee. A minion is a legal target. Stepping off that line is worth a card.
- Do not double-tap him on the turn he discards `arien-blue-2a`, or for the rest of the round after `arien-blue-3a`. One attacker, or a skill.
- `arien-green-1`/`2a` cannot land on a spawn point or next to an empty one. Those hexes are the escape from his leap until `arien-green-3a`.
- If he is on alt red, Attack 4 is blockable; the danger is the push afterwards and the Range.

## 5. Common mistakes

- Playing Spell Break on an empty radius because it is Initiative 13 and "should go first".
- Treating primary red as only an adjacent attack and standing where the line behind the minion is empty.
- Diving enemy minions without a Duelist card in hand, or expecting friendly minions to add Defense while Duelist is the block.
- Using gold's 1-space nudge on nothing, or using it to move himself (the text forbids it).
- Taking alt red and still walking into melee as if Defense were 7.
- Expecting Deluge to be wider than adjacent before a Radius item exists.
- Firing Living Tsunami on a skill, or spending the turn's step and then declining the attack.
- Upgrading green before red or blue. The leap does not win the fight; the line and the immunity do.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`arien-red-1`, `arien-red-2a`, `arien-red-3a`] WHEN he is adjacent to a unit and an enemy hero occupies one of the N hexes (N = 2/3/5) in the straight line from him through that unit THEN prefer that red's primary over a plain minion attack elsewhere. WEIGHT +10 if the hero behind has 1 card in hand (the discard sticks), +18 if they have 0 cards (the behind-step defeats them), +4 if they have 2+ (they will often just step off, but the hex is denied). CTX general. SOURCE [BGG 2432847] [BGG 3487765] [REASONED].

2. `PLAY_CARD` [`arien-silver`] WHEN after the reveal an enemy in radius 3 has played a non-gold skill, or before the reveal an enemy in radius 3 has no attack left in the possible pool and still has a skill THEN prefer Spell Break. WEIGHT +12 if that skill is a heal, a summon, or a movement skill that would escape a kill; +4 if it is a minor coin skill. CTX general (possible card pool; strength 10 may use the real hand). SOURCE [ADVICE] [BGG 3487765].

3. `PLAY_CARD` [`arien-silver`] WHEN no enemy hero is in radius 3, or every enemy in radius 3 has only attack/movement cards left THEN avoid playing it. WEIGHT −8. CTX general. SOURCE [BGG 3487765] [REASONED].

4. `PLAY_CARD` [`arien-blue-2a`, `arien-blue-3a`] WHEN two or more enemy heroes can attack him this turn and he is about to be the first of those hits THEN defend with this card, not with the red. WEIGHT +14 (the second attack misses). For `arien-blue-3a` the immunity is the rest of the round: WEIGHT +8 more if a third enemy attack is still unresolved. CTX general. SOURCE [BGG 3487765] [REASONED].

5. `DEFEND` [`arien-blue-1`, `arien-blue-2a`, `arien-blue-3a`] WHEN the attack's minion modifier is negative for him (more enemy minions adjacent than friendly) THEN prefer these over a card whose printed Defense is only 1 higher. WEIGHT +3 per enemy minion modifier he would otherwise eat, and −3 per friendly minion modifier he gives up. CTX general. SOURCE card text [REASONED].

6. `PLAY_CARD` [`arien-blue-2b`, `arien-blue-3b`] WHEN an enemy hero is in the cap (adjacent, or in radius for Deluge) and their unresolved card has Movement ≥ 2 or is their fast-travel card, and an ally can reach that hero's current hex with a later card THEN prefer the cap. WEIGHT +8. WHEN the enemy's only escape is card-text movement (a charge, a blink, a place) THEN avoid. WEIGHT −6. CTX general. SOURCE [BGG 3487765] [REASONED].

7. `CHOOSE_SPACE` [`arien-green-1`, `arien-green-2a`, `arien-green-3a`] WHEN placing THEN prefer a hex adjacent to a minion that has an enemy hero on the red line behind it, or a hex in radius 3 of a skill-user if Spell Break is still in hand. WEIGHT +8 for the line, +6 for the Spell Break radius. Never a spawn point; never, on `green-1`/`2a`, a hex adjacent to an empty spawn. CTX general. SOURCE [BGG 3487765] [REASONED].

8. `CHOOSE_UNIT` [`arien-green-2b`, `arien-green-3b`] WHEN swapping THEN prefer the enemy minion whose space is in the Battle Zone and whose departure hex is closer to an ally than to an enemy hero. WEIGHT +10 if an ally can then melee that minion, −8 if the landing hex is adjacent to two enemy heroes and no Duelist card remains in hand. CTX general. SOURCE [BGG 3487765].

9. `CONFIRM` + `CHOOSE_UNIT` [`arien-gold`] WHEN a unit adjacent to the target is a minion granting that target a defense modifier that puts the Defense total above his Attack total THEN move that minion. WEIGHT +the attack connecting (a kill if the target has no cards, else +6 for the discard). WHEN the unit is an ally in an enemy's attack range THEN move the ally out. WEIGHT +8. WHEN neither is true THEN decline the may. CTX general. SOURCE [ADVICE] [BGG 3487765].

10. `CHOOSE_NUMBER` [`arien-red-2b`, `arien-red-3b`] WHEN the push can put an enemy hero off a hex from which their unresolved attack still reaches an ally, or can put an enemy minion on a hex an ally threatens THEN push that far, not 0. WEIGHT +8. Do not push onto a straight line of length 2–4 that ends adjacent to a Brogan who still holds a charge. CTX general. SOURCE [BGG 3487765] [REASONED].

11. `CONFIRM` [`arien-red-3a`] WHEN a second enemy unit is adjacent after the first attack THEN repeat. WEIGHT +15 if it is a minion in the Battle Zone, +8 if it is a hero with a card in hand, +18 if that hero has no cards. CTX general. SOURCE [BGG 3487765].

12. `PLAY_CARD` [`arien-red-1`, `arien-red-2a`, `arien-red-3a`] WHEN he is not adjacent to any unit but a Movement action or Living Tsunami would make him so, and a hero sits on the line THEN still prefer this red over green if the move is legal on the attack (Living Tsunami only). Otherwise play green to the line and hold the red. WEIGHT the line from rule 1, delayed one turn, so ×0.6 if it has to wait. CTX general. SOURCE [BGG 2432847] [REASONED].

13. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN the lowest tier colour is red or blue and both are still tier 1 THEN upgrade blue first if two enemy heroes are regularly adjacent to him, else red. Keep the Initiative branch in hand if he has no Initiative item yet; tuck the other. WEIGHT +4 for the Initiative item on every later race, which is the difference between his red at 8 and an enemy blue at 10. CTX general. SOURCE [BGG 3493559] [ADVICE].

14. `KEEP_UPGRADE` [`arien-red-2b`, `arien-red-3b`] WHEN kept THEN set idealDistance to 2 and stop scoring "stand in the enemy minion cluster" unless a Duelist card is in hand. The Defense item is the tuck, not a reason to dive. CTX hero (branch flag). SOURCE [BGG 3487765].

15. `PLAY_CARD` [`arien-gold`] WHEN it is turn 4 and an adjacent enemy has no cards and his Attack total (4 + items + modifiers) meets their Defense THEN this is the finisher, and the before-step should remove a defending minion first. WEIGHT the kill. WHEN turn 1 and a minion is available to a Movement 4 card THEN do not open on gold. WEIGHT −6. CTX general. SOURCE [REASONED] from the colour-role essay [BGG 3092335]: gold finishes or dodges, red farms.

### evalTerms

Arien has no tokens. Score active effects as set-ups:

- Spell Break in force: +4 per enemy hero currently in radius 3 who still has an unresolved non-gold skill. 0 otherwise.
- Slippery Ground / Deluge in force: +3 per enemy hero inside the cap who still has an unresolved movement card of Movement ≥ 2.
- Expert Duelist (this turn) or Master Duelist (this round) immunity: +6 while two or more enemy heroes other than the excepted attacker can still attack him. The excepted attacker is worth 0.
- Do not add a bonus for "he might hold Spell Break". That is a play choice, not a board piece.

### threatAdjust

When Arien is the enemy:

- A hex occupied by an enemy hero that lies on the 2/3/5-behind line of a minion Arien can reach with his current primary red (adjacent, or adjacent after Living Tsunami's 1 step if the ultimate is in play) is threatened as a discard even though his printed attack range does not include that hero. Add +6 to that hex while `arien-red-1`/`2a`/`3a` is in his possible pool, +10 if it is the known played card.
- Any hex in radius 3 of him, while `arien-silver` is in his possible pool, is a bad hex to resolve a non-gold skill from. Add +5 to the cost of playing a skill there. Gold skills are exempt.
- After he has discarded `arien-blue-2a` this turn, or `arien-blue-3a` this round, attack actions from heroes other than the recorded attacker do not threaten him. Do not focus-fire.
- His leap cannot enter a spawn point. Those hexes are safe from `arien-green-*a`.

## 7. Sources

- Catalog `arien.json` (2nd printing), including the `advice` field from yvo-niedrich/goa2-faq. Cited as [ADVICE].
- `ArienHero.kt` for which decisions exist (the gold may-move, the behind-target choice, the wave push, the leap filter, the repeat confirms, Living Tsunami only before an Attack action).
- [BGG 3487765] Cryomancy, 2025-03-30, "Arien Hero Spotlight (2nd Printing)", community. Post 2 Dark_lord7, 2025-04-02, community, on Spell Break versus Ignatia and Wuk.
- [BGG 3493559] Cryomancy, 2025-04-09, "Arien Build & Go Guide", community. Includes the Gydion-in-the-jungle silver opening and the tier-III item order. (STRATEGY.md called this thread a Gydion note; the thread title and body are Arien.)
- [BGG 2432847] `nichik`, 2020-05-21, "Going melee", designer. 1st printing, kept because the behind-the-target discard is the same mechanism. The numeric "strength 7 or 8" in a later community post is 1st-printing and is not used.
- [BGG 2900722] 1st-printing spotlight, designer post 2022-07-16. Not used for numbers. His "range 2 red damage is 5" does not match `arien-red-2b` Attack 4. Card text wins.
- [BGG 3523913] 2025-06-07, community: Deluge's radius is what a Radius item widens; Slippery Ground is adjacency. Matches `arien-blue-3b` radius 1 and `arien-blue-2b` radius null.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Arien as Initiator/Disruptor.
- [BGG 3481942] 2nd-printing tier-list thread: Arien as a counter-pick into skill heroes; one group rated the silver low in beginner games. Used only as context.
- [BGG 3092335] Panetierre, colour roles. General, not Arien-specific.
- Reddit r/boardgames `1akgd6v` (GalahadB, 2024-02-06): turn-4 empty-hand kills and Gold > Blue > Red > Green. General.
- Optimal Play, "Arien the Tidemaster" (YouTube, Oct 2023): description only ("mobility and strong but particular attacks"). No transcript retrieved, so no rule is taken from it.
- Gamefound / 1st-printing promo text: not used.

No Arien-specific Reddit thread or transcribed video was found.
