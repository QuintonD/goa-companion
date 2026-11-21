# Wasp (`wasp`) — heuristic playbook

2nd printing. Dashboard: Attack 5, Defense 4–5, Initiative 6, Movement 5. Complexity 1. Catalog roles: disabler, tactician, sniper. Advice in `heroes/wasp.json`.

Units: `heuristic/DESIGN.md`. **[HERO]** needs Static Barrier's "this turn" obstacle rewrite and Magnetic Dagger's "this turn" no-swap/no-place flag. Both are public once the card resolves.

She is easy to build into a hero who does a little of everything and wins nothing [Cryomancy, BGG 3502775, 2025-04-26]. The Red branch is the choice: brawler with a splash discard, or a curved sniper. Do not take one card from each.

## 1. Identity

Support / Finisher [Cryomancy, BGG 3779890]. The support is a cage and a shove. The finisher is either the splash on a melee hit or the boomerang from off the line.

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | Support on primary Red, Ranged on alt Red | The branch changes the hero more than any other core-box Red [BGG 3502775]. |
| `idealDistance` | 1 on Electrocute, 3 on the boomerang | Boomerang cannot target a straight line, and adjacent is a straight line [CARD]. |
| `aggression` | 0.45 | She cannot eat repeated hits [ADVICE]. One good attack, then leave. |
| `laneWeight` | 0.55 | Shoves and pulls exist to move minions as much as heroes [ADVICE]. |
| `keepDefender` | true | Primary Green is a ranged Block with no Defense number. Primary Red is Defense 6–7. Alt Red is Defense 3–4, and then she cannot stand in melee. |
| `upgradeOrder` | the Red she committed to, then the Blue that keeps her alive on that plan, see §3 | |

## 2. Card-by-card

### `wasp-gold` Magnetic Dagger — Attack, init 12, Attack 3, Defense 2, Movement 1, Radius 3

Adjacent attack. After: this turn, enemy units in radius 3 cannot be swapped or placed by themselves or by enemy heroes. Normal movement and pushes still work [FAQ]. Units outside may place themselves into the radius [FAQ; RULINGS 43]. Fast Travel is a placement, so it stops too [RULINGS 12].

- **For:** a fast Attack 3, and a lock on Misa, Arien, Hanu, Whisper and anyone whose escape is a place or a swap. Hard to line up: she needs a melee target and a reason someone will place this turn [BGG 3466028].
- **When:** the target is a minion or a hero who dies to Attack 3, and an enemy in radius 3 still holds a place/swap. Or turn 4 when Attack 3 is enough.
- **Do not:** play it only for the lock with no attack target (the attack is first). Do not expect it to stop a push or a walk.
- **In hand:** Defense 2. Poor block, real threat.

`wasp-handicap` Crackling Dagger is the same lock at init 11, Attack 2, and the target must be a hero.

### `wasp-silver` Static Barrier — Skill, init 13, Defense 2, Radius 2, no Movement

This turn: while an enemy outside the radius acts, spaces inside are obstacles; while an enemy inside acts, spaces outside are obstacles. Read from their current hex each step [RULINGS 42]. Swaps and attacks across the barrier are allowed [FAQ]. Pushes are not [RULINGS 16, 42].

- **For:** the cage. Init 13 is before almost everyone. Radius 2 is a large dome. Outsiders lose paths in; insiders are stuck with what is already in the dome and will often have to hit her or a minion [BGG 3466028].
- **When:** an enemy jungler is outside and the beach minion is inside (Lifeguard Cage), or a frail hero has stepped behind the twins and the dome catches them with her Red still in hand (Trepidatious Cage) [BGG 3502775]. Also to buy a slow ally one turn.
- **Not:** a wall against shots. They can still target through it [BGG 3466028]. Useless as a cage against a hero who was going to play a skill, not a move [BGG 3466028, "Playing against"].
- **In hand:** Defense 2, and the threat of the dome. Radius items change the dome: radius 3 cages more outsiders and gives insiders more room [BGG 3502775].

### `wasp-red-1` Shock — Attack, init 8, Attack 5, Defense 6, Movement 4, Radius 2

Adjacent attack. After: an enemy hero in radius and not adjacent discards, if able.

### `wasp-red-2a` Electrocute — item: Initiative. Init 9, Attack 5, Defense 6, Movement 4, Radius 3

Same, radius 3. `wasp-red-3a` Electroblast (item: Initiative, init 9, Attack 6, Defense 7, Radius 3): that hero discards or is defeated.

- **For:** the brawler. Attack 5–6, Defense 6–7, and a second hero in the ring loses a card for free [BGG 3466028]. The adjacent target is not the discard target.
- **When:** a minion or a hero is adjacent and a different enemy hero is in radius (2, then 3). Early, so the team can chase the discarded hero. Tier 3, the splash kills a hero with an empty hand if any melee target exists.
- **Do not:** stand where the only other hero is adjacent (they are excluded). Do not spend this Red as the block if the splash would have taken a card and Blue or Green can block instead — unless the incoming attack kills her.

### `wasp-red-2b` Charged Boomerang — item: Defense. Init 9, Attack 3, Defense 3, Movement 4, Range 3, ranged

"Target a unit in range and not in a straight line." Adjacent is a straight line, so she cannot shoot a neighbour. `wasp-red-3b` Thunder Boomerang (item: Movement, init 9, Attack 4, Defense 4, Range 3): if the first target was a hero, she may repeat once on a different target.

- **For:** the sniper. Fork a hero and a minion from an odd angle, far enough that the reply does not reach [BGG 3466028].
- **When:** two targets sit off her lines. Tier 3, hit the hero first so the repeat exists, then the minion.
- **Do not:** walk into the centre and expect a shot. Do not take this Red and then stand in melee: Defense 3–4 will not hold [BGG 3502775].
- **In hand:** a weak block. On this path, Blue and the ranged-Block Green are the life.

### `wasp-green-1` Stop Projectiles — Defense, init 3, Movement 2, Block, no Defense

"Block a ranged attack." A failed block (it was melee) still discards the card and she is defeated; she may attempt it [FAQ; same reading as Tigerclaw].

### `wasp-green-2a` Deflect Projectiles — item: Initiative. Init 3, Movement 2, Range 3

Block a ranged attack; if she does, an enemy hero in range, other than the attacker, discards if able. `wasp-green-3a` Reflect Projectiles (item: Radius, init 2, Movement 2, Range 3): an enemy hero in range discards — the "other than the attacker" limit is gone, so the attacker can be the one.

- **For:** a ranged-immunity that lives in hand [BGG 3466028]. The tier-2 splash hits a third party; tier 3 can punish the shooter.
- **When to keep:** the biggest threat is ranged. When to play as a primary: almost never; it is the block. Movement 2 exists if she must leave and the block is not needed.
- **Do not:** upgrade into it against a melee table and feel safe [BGG 3466028: "think about who is your biggest threat"].

### `wasp-green-2b` Telekinesis — item: Attack. Init 3, Defense 3, Movement 2, Range 3

Place a unit or token in range, not in a straight line, into a space adjacent to her. `wasp-green-3b` Mass Telekinesis (item: Attack, init 2, Defense 4, Range 3) may repeat once. Defeated minions are not in range [nichik, BGG 3466028, 2025-05-18].

- **For:** pull a hero onto her Gold, pull an ally out, pull a slow ally up, pull a minion to a friend. The target must sit off her lines [BGG 3466028].
- **When:** init 3, after the fast cards have moved people onto those crooked hexes. Mass: pull the minion for the ally and the hero for herself, or the reverse.
- **Do not:** try to pull someone already adjacent or on a line. Set the angle with Blue first if the board is a grid of lines.

### `wasp-blue-1` Lift Up — Skill, init 10, Defense 5, Movement 3, Radius 2

Move a unit or token in radius 1 space, not farther from her and not closer. May repeat once on the same target (so up to 2 steps along the ring).

### `wasp-blue-2a` Control Gravity — item: Defense. Init 10, Defense 5, Movement 3, Radius 3

Same. `wasp-blue-3a` Center of Mass (item: Defense, init 11, Defense 6, Radius 3) repeats up to two times (up to 3 steps on the ring).

- **For:** orbit a minion onto a friend's attack, or off an enemy's, or turn a target off her straight line so Green or the boomerang can see it [BGG 3466028]. Tokens too.
- **When:** the one-step ring move changes who is adjacent to the minion. Repeating spends the same action; take the second step only when the first did not finish the job.
- **In hand:** Defense 5–6. Often the block on the sniper path.

### `wasp-blue-2b` Kinetic Repulse — item: Attack. Init 10, Defense 5, Movement 3

Push up to 2 adjacent enemy units 3 spaces. A pushed hero stopped by an obstacle discards if able. `wasp-blue-3b` Kinetic Blast (item: Range, init 11, Defense 6): the distance is 3 or 4, chosen once for both [RULINGS 16]. A push of 0 because the first hex is blocked counts as stopped [RULINGS 16]. A push an effect forbids is not "stopped by an obstacle".

- **For:** the melee answer. Slam two heroes into walls, or slide a minion to an ally who cannot reach it, or clear the enemy melee minion off her Defense [BGG 3466028].
- **When:** the 3rd (or 4th) hex, or any earlier hex, is an obstacle for at least one hero, and that hero has a card. Minions do not discard; pushing them is for the wave and for modifiers.
- **Do not:** push a hero 3 into empty ground and call it a discard. Do not push her own team's minion (enemy units only).

### `wasp-purple-4` High Voltage — Radius 3

After she performs a basic skill, she may defeat an enemy minion in radius 3; an enemy hero who was adjacent to that minion discards if able [RULINGS 44]. The basic skill is Static Barrier's primary. The hero who discards is one who was adjacent before the defeat.

- Play Silver, then take the minion the dome trapped, and the hero standing on it loses a card. Do not count Gold or Red as the trigger.

## 3. Upgrade path

**Shock Support (stay in melee)** [BGG 3502775]:

1. `wasp-red-2a` Electrocute first (radius 3, Initiative item).
2. `wasp-green-2b` Telekinesis (Initiative item, the pull) unless the enemy's only real attacks are ranged, in which case `wasp-green-2a` Deflect Projectiles and she is much safer in hand.
3. `wasp-blue-2b` Kinetic Repulse (Defense item) so Red can be swung. Control Gravity if the job is feeding minions to an ally and she is not the one being hit.
4. Tier III: `wasp-blue-3b` Kinetic Blast (Defense 6, the 4-push), then `wasp-green-3b` Mass Telekinesis (the Radius item, which enlarges Silver), then `wasp-red-3a` Electroblast (Attack 6, Defense 7, splash can kill). The "bug zapper" swap to Center of Mass is the ally-execute variant and is the one Cryomancy calls untested; take it only when an ally is the finisher and she is the one orbiting victims back into range.

**Supercharged Sting (the curve)** [BGG 3502775]:

1. A Defense item before she reveals the low-Defense Red: `wasp-blue-2b` or, if she wants the Attack item, `wasp-blue-2a`. Then `wasp-red-2b` as the surprise.
2. `wasp-green-2a` for the Attack item while Red is still the primary, if the table is ranged. Skip it if the table is melee; the block will never fire.
3. Tier III: `wasp-red-3b` Thunder Boomerang first (the repeat). Then `wasp-blue-3a` Center of Mass for the Range item (Deflect reaches farther, and the ring sets up the curve). Then `wasp-green-3a` Reflect for another Attack item. Gold at Attack 3 plus two Attack items is a real melee hit; do not forget it exists [BGG 3502775].
4. The alternate tier III (back into Electroblast) is the "we already stripped them" build. Only after the team is actually removing Reds and Blues.

**Tuck:** on the melee path, Initiative from both Red and Green, Defense from the push. On the sniper path, Defense from the boomerang's tier-2 card is small comfort (Defense 3); the Blue's Defense item matters more, so tuck the boomerang's partner only when Center of Mass is the hand card. Range wants to be in hand on the sniper (Kinetic Blast's item, or tuck it if she did not take that Blue). Radius wants to be in hand whenever Silver is the plan (Reflect's item).

**Phase:** round 1 she can take the centre minion or cage the beach [BGG 3502775]. She should not be the one who walks into Silverarrow or Sabina and tries the cage; move with Red on turn 2 instead [same, Lifeguard Cage]. Levels 4–6, commit: splash radius or the repeatable curve, not a third unrelated trick.

## 4. Synergies and counters

**With:** a slow ally who needs one turn of cover (Silver at 13). A melee finisher who wants a hero pulled adjacent or a minion orbited onto them (the bug-zapper note). A hero who cannot reach the twins: she pushes the minion to them. She is the one who stops placement escapes, so she wants those enemies on the other team, not as partners who need to blink.

**She fears:** a single large attack while she is on the boomerang (Defense 3–4, no heal) [ADVICE; BGG 3502775]. Standing in a straight line when that is the Red she chose. Ranged heroes, if she tucked the Block and kept Telekinesis. Anyone who can hit through the dome; the dome is not cover.

**Against her:** know the Red. Primary Red's splash dies if the adjacent target leaves. Alt Red and Telekinesis die if she is kept on a straight line to the target [BGG 3466028]. Silver is hard to "dodge" but a skill instead of a move ignores the cage. Gold's lock matters only when she has a melee target and the team was about to place or swap. Do not waste a turn respecting the dagger otherwise.

## 5. Common mistakes

- One card from each branch. Jack of all trades [BGG 3502775].
- Boomerang from a hex with nothing off-line, then Hold.
- Counting an adjacent hero as Shock's discard target.
- Expecting Magnetic Dagger to stop movement [FAQ].
- Using the ranged Block on a melee attack and dying, having believed the rest of the text would happen.
- Caging a hero who was not going to move.
- Pushing into open hexes and expecting a discard.
- Pulling a minion from the supply. It is not in range [nichik, BGG 3466028].
- Forgetting Gold can kill once Attack items exist, and playing only the boomerang from too far away to help [BGG 3502775].

## 6. Heuristic rules

`evalTerms`:

- Static Barrier in force: +4 if at least one enemy is inside and their remaining action is a move, or one enemy is outside and the minion they want is inside. +0 if everyone left was going to play a non-move. [HERO]
- Magnetic Dagger lock in force: +3 per enemy in radius 3 who still has a place or swap they would have used. +0 against a team with none. [HERO]

`threatAdjust`: the dome rewrites enemy movement for the rest of the turn. The dagger deletes enemy place/swap/Fast Travel for units inside it.

1. **H1** [PLAY_CARD] [`wasp-silver`] **WHEN** an enemy outside radius 2 has a minion they want inside it, or an enemy inside it has their only safe exit outside it, and she still holds a Defense 5+ card **THEN** play Barrier and stand so the dome cuts that path. **WEIGHT** +15 if the caged hero loses a minion, +8 if they only lose a position. **SOURCE** [BGG 3502775 Lifeguard / Trepidatious Cage; FAQ] **[HERO]**

2. **H2** [PLAY_CARD] [`wasp-silver`] **WHEN** the enemies who have not acted will play skills, not moves, or a ranged attacker shoots through the dome onto her and she does not hold Defense 6 **THEN** do not cage. **WEIGHT** −4. **SOURCE** [BGG 3466028] **[GENERAL]**

3. **H3** [PLAY_CARD] [`wasp-red-1`, `wasp-red-2a`, `wasp-red-3a`] **WHEN** an enemy unit is adjacent and a different enemy hero is in radius **THEN** hit the adjacent unit (minion first unless the hero dies) and discard the other. Electroblast: if the other hero has no card, they are defeated — that target outranks a minion. **WEIGHT** 15 plus 4 for the discard, or the kill. **SOURCE** [CARD; BGG 3466028] **[GENERAL]**

4. **H4** [PLAY_CARD] [`wasp-red-2b`, `wasp-red-3b`] **WHEN** a unit is in range 3 and not on a straight line from her, and she is not in a hex an unspent enemy attack can kill given Defense 3–4 **THEN** shoot. Thunder Boomerang: hero first, then repeat on a minion or a second hero. **WEIGHT** 15, or the card stripped. **SOURCE** [CARD; BGG 3466028] **[GENERAL]**

5. **H5** [PLAY_CARD] [`wasp-gold`] **WHEN** an adjacent unit can be hit and an enemy in radius 3 still holds a swap, place, or Fast Travel **THEN** attack and lock. **WHEN** the lock has nobody to stop **THEN** play Gold only as the Attack 3 finisher or the minion, not as a "disable". **SOURCE** [FAQ; BGG 3466028] **[HERO]**

6. **H6** [PLAY_CARD] [`wasp-blue-2b`, `wasp-blue-3b`] **WHEN** an adjacent enemy hero's push line hits an obstacle within the chosen distance **THEN** push them, the longer distance if it still hits an obstacle, and a second enemy unit if its line does not cross the first [RULINGS 16]. **WEIGHT** +4 per discard, +15 if a pushed minion is the one an ally can now reach or the one that was giving −1 Defense. **SOURCE** [CARD; BGG 3466028] **[GENERAL]**

7. **H7** [PLAY_CARD] [`wasp-blue-1`, `wasp-blue-2a`, `wasp-blue-3a`] **WHEN** a 1-step orbit (or 2, or 3) makes a minion adjacent to an ally who had no minion, or takes it off an enemy's attack, or moves a target off her straight line before the boomerang or Telekinesis **THEN** take those steps and stop when the condition is met. **SOURCE** [BGG 3466028; BGG 3502775] **[GENERAL]**

8. **H8** [PLAY_CARD] [`wasp-green-2b`, `wasp-green-3b`] **WHEN** a unit or token is in range, not on a straight line, and an empty hex adjacent to her is the hex her Gold, her ally's attack, or safety wants **THEN** place it there. Mass: repeat if a second pull is the same kind of gain. **WEIGHT** +15 if it delivers a minion, +8 if it sets a kill. **SOURCE** [CARD; BGG 3466028] **[GENERAL]**

9. **H9** [DEFEND] [`wasp-green-1`, `wasp-green-2a`, `wasp-green-3a`] **WHEN** the attack is ranged **THEN** block. Deflect: discard a different enemy in range 3 if one exists. Reflect: discard the attacker if they are in range, else another. **WHEN** the attack is not ranged **THEN** do not spend this card. **WEIGHT** her life, plus 4 for the extra discard. **SOURCE** [CARD; FAQ; BGG 3466028] **[GENERAL]**

10. **H10** [PLAY_CARD] [primary Green] **WHEN** she is considering playing it for Movement 2 **THEN** only if no ranged attack remains that she cannot otherwise survive. The card in hand is the immunity. **WEIGHT** +6 for keeping it against a known ranged attacker. **SOURCE** [BGG 3466028] **[GENERAL]**

11. **H11** [KEEP_UPGRADE] [Red pair] **WHEN** the team needs a body in melee and she has Defense items **THEN** Electrocute / Electroblast. **WHEN** she is the one who must not be hit **THEN** Charged / Thunder Boomerang, and the rest of the build is not allowed to tuck the Block or the Defense item. **SOURCE** [BGG 3502775; BGG 3466028] **[GENERAL]**

12. **H12** [UPGRADE_CARD] **WHEN** she is on the boomerang and has no Defense item yet **THEN** the Blue that gives Defense before the Red upgrade. **WHEN** she is on Electrocute **THEN** Red first for the radius. **SOURCE** [BGG 3502775] **[GENERAL]**

13. **H13** [CHOOSE_NUMBER] [`wasp-blue-3b`] **WHEN** both distances are legal **THEN** the one that stops the hero on an obstacle. If neither does, the shorter, unless the longer push puts a minion on the ally. **SOURCE** [CARD; RULINGS 16] **[GENERAL]**

14. **H14** [CONFIRM] [`wasp-purple-4`] **WHEN** she has just resolved Static Barrier and an enemy minion is in radius 3 **THEN** defeat it, preferring one that had an enemy hero adjacent. **WEIGHT** 15 plus 4 if the hero discards. **SOURCE** [RULINGS 44] **[HERO]**

15. **H15** threatAdjust [`wasp`] **WHEN** Static Barrier is in force **THEN** enemy movement, push and placement that would cross the radius boundary is illegal for the rest of the turn. Attacks and swaps are not. Recompute reach; do not add a damage threat. **SOURCE** [FAQ; RULINGS 42] **[HERO]**

16. **H16** threatAdjust [`wasp`] **WHEN** Magnetic Dagger has resolved **THEN** enemy units in radius 3 cannot Fast Travel, swap or place themselves or be placed by enemy heroes. Movement remains. **SOURCE** [FAQ; RULINGS 43] **[HERO]**

17. **H17** threatAdjust [`wasp`] **WHEN** primary Green is still in hand **THEN** ranged attacks against her fail while she keeps it. Melee attacks use her numeric Defense only. **SOURCE** [BGG 3466028] **[HERO]**

18. **H18** [PLAY_CARD] [against her] **WHEN** she is on the boomerang **THEN** end the turn on a straight line from her, or adjacent. **WHEN** she is on Electrocute **THEN** do not stand in her radius unless you are the one adjacent target she would have to hit. **SOURCE** [BGG 3466028] **[HERO]**

19. **H19** [CHOOSE_ACTION] **WHEN** the primary has no legal target (no off-line unit, no one to push, dome not useful) **THEN** move or Fast Travel to an angle, do not Hold a Gold that could have taken a minion. **SOURCE** [AI.md holding; REASONED] **[GENERAL]**

20. **H20** [DEFEND] [Red vs Blue] **WHEN** she is on Electrocute and Red is Defense 6–7 **THEN** Red may block a melee hit that Blue's 5–6 cannot, but only after the splash has been scored as more valuable in hand. If the splash's discard is worth more than surviving on Blue, block with Blue. **SOURCE** [REASONED from the two Defense numbers and BGG 3466028] **[GENERAL]**

## 7. Sources

- Catalog `heroes/wasp.json`, handicap `wasp-handicap`, FAQ (barrier, the ring-move, the push discard, failed blocks, dagger vs movement, obstacles).
- `heroes/WaspHero.kt`, `core/game/rulings` 42–44 and the push rule 16.
- [BGG 3466028](https://boardgamegeek.com/thread/3466028) Wasp spotlight (2nd printing), Cryomancy, 2025-02-22. Designer `nichik`, 2025-05-18: Telekinesis does not pull a minion that is not in range.
- [BGG 3502775](https://boardgamegeek.com/thread/3502775) Build & Go, Cryomancy, 2025-04-26. VAlchemyst, 2025-04-30, community: the centre-minion variant of the cage.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Support / Finisher.
- [BGG 3502652](https://boardgamegeek.com/thread/3502652) is a 1st-edition play report (Wasp + Xargatha). Not used for card plans.
- 1st-printing spotlight [BGG 2900487](https://boardgamegeek.com/thread/2900487) was not used.
- No Wasp thread after 2026-10-24.

## Implementation notes

`WaspHeuristics.kt` (ADR-085). The general book scores an attack card from its printed reach (`HContext.strikeFrom`). Where this hero's text narrows the targets, the playbook re-scores the card's PLAY_CARD outlook on the units the engine would really offer (`BatchDSupport.strikeOn` + `strikeFix`): the card keeps its movement value and loses the hit it cannot make. That replaces the draft's flat −28 "fizzle" penalty, which also threw away the card's secondary Movement.

- **Implemented:** H1/H2 (the dome only with one enemy inside its radius and another outside), H3 (Shock: the adjacent hit plus the discard, or Electroblast's kill, of a hero in radius who is not adjacent), H4 (the boomerang only at units 2+ away and off her six lines; Thunder Boomerang's repeat), H5 (Magnetic Dagger is an adjacent attack), H6/H13 (Kinetic Repulse: an adjacent hero whose push line meets an obstacle discards; Thunderous Repulse picks the distance that stops them), H8 (Telekinesis only with an enemy minion off her lines and an attack in hand), H18 (threat of the boomerang off her lines). `evalTerms`: dome in force +2, dagger lock +1.
- **Engine vs research:** Telekinesis needs an empty space adjacent to her and a target not on her lines (any unit or token, friendly included); Repulse picks the units first, then one distance for all of them. No disagreement with the research.
- **Skipped:** H7 (Lift's orbit is a CHOOSE_UNIT/space sequence left to lookahead), H9/H10/H17/H20 (Defend and holding the ranged Block: the general book reads the engine's block preview), H11/H12 (branch choice: the default upgrade order), H14 (High Voltage's CHOOSE_UNIT is the general minion worth), H15/H16 (movement rewriting by the dome is engine-side; the threat map does not model it), H19 (CHOOSE_ACTION lookahead).
- **Ablation (strength 8):** 52–44 of 96 = 54.2% (Wilson 44.2–63.8%), with the playbook against without it, 2026-11-21 (`AI_H_ABLATION=wasp AI_H_STRENGTH=8 AI_H_GAMES=96`: 48 line-ups, both sides).
