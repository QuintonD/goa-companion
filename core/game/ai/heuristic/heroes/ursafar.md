# Ursafar (`ursafar`) — heuristic playbook

2nd printing. Dashboard: Attack 6, Defense 6, Initiative 5, Movement 1–5. Complexity 2. Catalog roles: durable, pusher, farming, melee. Advice in `heroes/ursafar.json`.

Units: `heuristic/DESIGN.md`. **[HERO]** needs the public flag "enraged" (an active "This round: You are enraged", or `ursafar-purple-4` while he is on the board) and which of his cards are active.

**Order rule, used by every card below.** "This round: You are enraged" is printed last, so the card does not satisfy its own "If enraged" [rulings/ursafar.md 2]. The first enrage of the round is a 1-space Blue, a Gold or Red that does not need the bonus, or a calm Silver. Green never enrages [rulings 1]. Text applies only on the primary action: moving with the secondary boots does not enrage him [slyestfox, BGG 3513251, and the card structure].

## 1. Identity

Zoner / Finisher [Cryomancy, BGG 3779890]. He does not chase. He stands where the enemy must walk, gets angry on a minion, and then the hill is his [dusteppe, BGG 3513251; ADVICE].

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | Fighter | Three attacks once Silver copies one, Defense 6–8, adjacent only. |
| `idealDistance` | 1 | Every attack is adjacent. Gold's lunge adds 1, and only toward a hero, and only if already enraged. |
| `aggression` | 0.35 until enraged, then 0.8 | A calm bear who dives is the lost round. An enraged bear who will not swing is the other lost round. |
| `laneWeight` | 0.4 | Minions are how he enrages and how Rip/Tear prints coins. Kills are the point once the hill is set [BGG 3630546]. |
| `keepDefender` | true | Red is Defense 6–8. Blue is Defense 5–6 but Movement 1. Do not drop Red on a speculative hero if it is the only card that lives. |
| `upgradeOrder` | see §3 | Cocaine Bear (speed) or Growing Beast (coins). |

Designer sequence, the round in one line: "Use red to move 3 spaces to a minion, then gold to defeat it, then green to move, then silver to repeat gold and attack a second target (ideally position to threaten a hero)" [nichik, BGG 3513251, 2025-05-18]. "~300 recorded games and the win rate above 50%" [same, 2025-05-23].

## 2. Card-by-card

### `ursafar-gold` Claws That Catch — Attack, init 12, Attack 4, Defense 2, Movement 1

Before the attack: if enraged, he may move 1 to a space adjacent to an enemy hero. Then an adjacent unit. Then he is enraged.

- **For:** the enrage that cannot be sidestepped, and the turn-4 finisher. Cryomancy: once enraged, enemies can no longer use a faster Gold to step off it [BGG 3630546]. "Nearly unavoidable" at init 12, and with Initiative items it goes to 15 [BGG 3653998].
- **The lunge** needs a prior enrage, an enemy hero to land beside, and then the attack may be the minion he is now adjacent to instead of that hero [BGG 3630546]. Calm Gold does not lunge.
- **When:** turn 1 on a minion he is already touching, so the whole round is enraged. Turn 4 on a hero who has no defense left, lunging if needed.
- **Do not:** spend it as Defense 2. Do not lunge into a hex an enemy who acts after init 12 can kill, unless the lunge is the kill.
- **In hand, already enraged:** a 2-hex threat (stand, plus one step toward a hero). Silver copying it is the zone [BGG 3630546].

`ursafar-handicap` Jaws That Bite is the same at init 11, Attack 3, Defense 1, and the target must be a hero.

### `ursafar-silver` Angry Roar — Skill, init 7, Defense 4, no Movement

If enraged, perform the primary of one of his active cards that has an active effect. Then he is enraged.

- **Calm:** the "if" fails, the perform is skipped, and he becomes enraged with no target. Use that only when no attack can connect and he is already on the hill he wants, so Blue stays in hand as Defense [BGG 3630546].
- **Enraged:** copy Gold, Red, or Blue — the cards that say "This round". Never Green; Green has no active effect [nichik, BGG 3458850, 2025-02-12, designer; rulings 4]. The copied card stays where it is and its text runs in full, including a fresh "you are enraged".
- **If enraged and nothing can be performed,** the primary is not offered [rulings 4]. Do not play Silver into that.
- **In hand:** the third attack. Playing it early to enrage throws away the copy.

### `ursafar-red-1` Prey Drive — Attack, init 8, Attack 5, Defense 7, Movement 3, Radius 3

Adjacent attack. After: if enraged and the target was not removed, remove up to 1 enemy minion in radius. Then enrage. Removing is not defeating: no coins [FAQ]. The target still on the board (a hero who blocked, a minion Brogan saved) may itself be one of the removals [rulings 5].

### `ursafar-red-2a` Prey Abundance — item: Defense. Init 9, Attack 6, Defense 7, Movement 3, Radius 4

Same, radius 4. `ursafar-red-3a` Feeding Frenzy (item: Defense, init 9, Attack 6, Defense 8, Radius 4) removes up to 2.

- **For:** the weapon. Attack 6 at init 9, Defense 7–8 in hand, and a wave swing when the hero lives. Cryomancy: many heroes must leave a 10-initiative, 6-damage swing that can be copied [BGG 3630546; the "10" is this card plus the Initiative item from the Cocaine Bear green].
- **When:** already enraged, on a hero who will block, with enemy minions in radius — the removal is the tax. Or on a minion, to enrage, when Gold should be saved.
- **Do not:** count the removal as 2 coins. It is lane margin only (≈ 15 if it changes the battle, 0 coins).

### `ursafar-red-2b` Rip — item: Initiative. Init 9, Attack 5, Defense 6, Movement 3

After the attack: if enraged, gain 1 coin. `ursafar-red-3b` Tear (item: Initiative, init 9, Attack 5, Defense 7): if enraged, gain 2 coins; if he defeated a hero, that hero spends 1 more Life counter. Both clauses are under "If enraged" [rulings 6].

- **For:** the level lead. A minion plus the coin is a heavy's payout at tier 3 (2 from the minion + 2 from Tear). A hero kill spends an extra Life — that is the highest-value kill in this set besides a match-point push.
- **When:** enraged, on a minion he is certain to remove, or on a hero who cannot block. Silver copying Tear on a second minion is the greedy round; do not do it while standing in a kill [BGG 3630546].
- **Do not:** swing Tear calm. The coins and the extra Life do not happen, and he only becomes enraged afterwards.

### `ursafar-green-1` Sniff Out — Skill, init 4, Defense 2, Movement 2, Range 2

If enraged, an enemy hero in range discards, if able. Not offered when enraged and no enemy hero is in range [rulings 7]. Calm: the primary does nothing useful — do not play it to "try".

### `ursafar-green-2a` Eyes on the Prey — item: Attack. Init 3, Defense 3, Movement 2, Range 3

Same. `ursafar-green-3a` Apex Predator (item: Attack, init 3, Defense 3, Range 3): discard or defeated.

- **For:** the fourth wound. Four of five cards can force a discard on the primary-green path [BGG 3630546]. Range 3, init 3, so it answers a hero who has already committed.
- **When:** enraged, a hero in range, and an ally (or his own later Gold) can use the empty hand. Apex Predator on a hero with no card is a kill at range 3.
- **Do not:** discard a hero nobody will touch. Chase with Red instead [BGG 3630546].

### `ursafar-green-2b` Instinctive Reaction — item: Initiative. Init 3, Defense 3, Movement 2

If enraged, choose one: perform the primary of a discarded card, or retrieve a discarded card. `ursafar-green-3b` Evolutionary Response (item: Movement, init 3) is one or both.

- **For:** the heal that makes a 50/50 survivable, and a second use of the attack he just blocked with. A discarded attack performed this way becomes active, so Silver can copy it [BGG 3630546; rulings 8].
- **When:** enraged, he blocked with Red or Gold, and the attacker is still adjacent or the retrieved card is the one he needs next turn.
- **Do not:** play it calm. Do not retrieve a card he could have performed for the kill.

### `ursafar-blue-1` Prowling Brute — Movement, init 9, Defense 5, Movement 1

If enraged, after the move he may swap with an adjacent unit or token. Then he is enraged. The swap needs a prior enrage. The printed move is 1 either way.

### `ursafar-blue-2a` Rampaging Beast — item: Defense. Init 10, Defense 6, Movement 1

If enraged, swap, and if he swapped, move up to 1 more. `ursafar-blue-3a` Unstoppable Force (item: Range, init 10, Defense 6): the extra move is up to 2. The extra move is card text, not a Movement action [rulings 9].

- **For:** enrage plus a step, and later a swap through a body. Token-heavy games want this branch [BGG 3630546]. Silver copies it at init 7, which is the reactive version: step on Blue, swap on Silver after they have moved.
- **Do not:** swap off the hill into the open.

### `ursafar-blue-2b` Cold Ire — item: Attack. Init 10, Defense 6, Movement 1

If enraged, +1 Movement on this card. `ursafar-blue-3b` Eyes of Flame (item: Radius, init 10, Defense 6): +2 Movement. The bonus is this card's Movement action only [FAQ]. And it requires a prior enrage, so the first Blue of the round is still 1 space.

- **For:** crossing open ground once he is already angry, and the Silver copy that hunts [BGG 3630546]. Eyes of Flame plus Apex Predator is the pursuit pair (the Movement item is on Evolutionary Response, the Range item is on Eyes of Flame — Cryomancy's tier-III sentence pairs those items; check the icons before tucking).
- **Do not:** expect +2 on Gold or Red. They do not have the line.

### `ursafar-purple-4` Unbound Fury

Always enraged while he is on the board. Resolved cards that have an active-effect text count as active for Silver. Greens still do not [nichik, BGG 3458850]. From level 7, Gold lunges on the first attack of the round, and Silver can copy a resolved Gold/Red/Blue even if it was not "activated" in the narrow pre-ultimate sense.

## 3. Upgrade path

**Cocaine Bear (kill them before they leave)** [BGG 3653998]:

1. `ursafar-blue-2a` Rampaging Beast if he is walking into melee (Defense item, the swap). Cold Ire (`ursafar-blue-2b`, Attack item) if the attacks coming back are already big.
2. `ursafar-red-2a` Prey Abundance. Attack 6, Defense item, the removal.
3. `ursafar-green-2a` Eyes on the Prey. Initiative item, the range-3 discard.
4. Tier III: `ursafar-red-3a` Feeding Frenzy first (Gold goes to init 15 if both Initiative items exist — Abundance is Defense, Eyes on the Prey is Attack; the second Initiative item in Cryomancy's writeup is not on those two cards. Card icons: Initiative is Rip, Instinctive Reaction, and that is the other build. On this build the speed is Blue's init 10 plus Red's init 9, and items are Defense, Defense, Attack. Do not add a phantom Initiative item.)
5. Green: `ursafar-green-3b` Evolutionary Response when he is the one in the 50/50s (Movement item, perform and retrieve). `ursafar-green-3a` Apex Predator when he is being kited (Attack item, discard or die at range 3).
6. Blue last: Unstoppable Force where bodies and tokens block him; Eyes of Flame where the map is empty. Eyes of Flame's Radius item does nothing for Prey Abundance's radius unless he also has a radius icon — Abundance has radius, so the item does add 1 to that radius. Good tuck if he is on the removal Red.

**Growing Beast (coins, then the extra Life)** [BGG 3653998]:

1. `ursafar-blue-2b` Cold Ire (Attack item) for the Defense padding Cryomancy wants — the Defense item on this sentence is Cold Ire's partner `ursafar-blue-2a` if Defense is the goal; Cold Ire's icon is Attack. Take `ursafar-blue-2a` when the padding is the reason, Cold Ire when the Attack item and the open-ground move are the reason. Cryomancy's "defense item" on Cold Ire does not match the catalog. Card text wins.
2. `ursafar-red-2b` Rip, then Tear first at tier III. Two coins on an enraged minion, and +1 Life on a hero.
3. `ursafar-green-2a` for the Initiative item and a discard for his friends. Tier III green and blue as in the spotlight: Eyes of Flame's Range item lengthens Sniff/Apex; Apex's Movement item plus a copied Blue is the chase.

**Tuck:** on the kill build, tuck Initiative only if he took Rip's branch by mistake — he wants Attack and Defense in hand. On the coin build, Tuck the removal Red (Defense item) if Tear is the hand card, and keep Tear's Initiative item on the dashboard by tucking the other Red. Range is a weak tuck until Green has range. Radius helps the removal Red and nothing on Tear.

**Phase:** round 1, do not commit to a kill unless it is sure [BGG 3653998]. Get adjacent to a minion. Levels 1–3 are the awkward ones: Movement 1, Gold does not lunge until something else has enraged him. Levels 4–6, Tear or Feeding Frenzy, and stop being a pure farmer if the team has a discard. Never end the round on the far side of a push [ADVICE].

## 4. Synergies and counters

**With:** a ranged partner who does not need him to cross the map (Swift, Silverarrow — AegisToast, BGG 3487749, 2025-03-30). An ally who discards, so his enraged Gold is the finisher rather than the opener. He wants the fight in one place.

**He fears:** being moved. Push, place, swap, and "slower movement" are the counter [BGG 3630546]. Snipers who hit him at a range his lunge does not reach (Ignatia is the worked example: DaFink, BGG 3513251 — do not walk into maximum range; sit just inside it so her slow Red whiffs, or just outside it). Garrus, whose job into him is to stop the enrage and fall behind on levels doing it [Cryomancy, BGG 3513251]. Ending the round adjacent to nobody, so turn 1 is a 1-space Blue [BGG 3630546]. A death: the walk back is the whole punishment [slyestfox, BGG 3513251].

**Against him:** do not stand next to him at end of round if you are the minion he will open on. Kill his enrage turn — it is predictable, and it leaves him three productive cards, then two if you also move him [Naughty zoot, BGG 3513251]. His Gold, once enraged, is the card to respect; Red and the Silver copy are the ones to sidestep with a fast skill. Ranged heroes never step into the 2-hex lunge.

## 5. Common mistakes

- Chasing. "You excel when enemies come to you" [ADVICE]. The lost Ursafar games are a bear waddling one hex a round [slyestfox, BGG 3513251].
- Using the secondary Movement and expecting to be enraged.
- Lunging, swapping, coining, discarding, or adding Movement on the same card that enrages him.
- Copying a Green with Silver, or copying with the Ultimate as if Greens had become active [BGG 3458850].
- Playing Green while calm (Sniff Out does nothing; the perform-discarded Green does nothing).
- Treating a removal as coins [FAQ].
- Trading his own life for a kill. The respawn walk is worse for him than for almost anyone [BGG 3630546].
- Greedy Tear-plus-Silver on two minions while a hero is hitting him.
- Adding initiative in the writeup that the tucked card does not have.

## 6. Heuristic rules

`evalTerms`:

- Enraged, with an attack still in hand or an active Gold/Red Silver can copy: +6. Calm, on turn 1, adjacent to an enemy minion: +3 (the enrage is free). Calm, adjacent to nothing, more than 1 hex from a minion: −8. [HERO]
- No token of his own.

`threatAdjust`: while enraged and Gold is unplayed, every enemy hero within 2 is in the lunge. See H18.

1. **H1** [PLAY_CARD] [`ursafar-gold`, `ursafar-red-1`, `ursafar-red-2a`, `ursafar-red-2b`] **WHEN** he is calm and adjacent to an enemy minion **THEN** attack that minion and become enraged. Prefer Red if Gold should be the turn-4 lunge; prefer Gold if a hero could leave before init 12. **WEIGHT** +6 on top of the minion's 15. **SOURCE** [ADVICE; nichik BGG 3513251; BGG 3653998] **[HERO]**

2. **H2** [PLAY_CARD] [`ursafar-blue-1`, `ursafar-blue-2a`, `ursafar-blue-2b`, `ursafar-blue-3a`, `ursafar-blue-3b`] **WHEN** he is calm and not adjacent to a minion, and a 1-space step reaches a minion or the hill he must hold **THEN** step and enrage. Do not take the secondary Movement on Red or Gold to "get there and enrage". **WEIGHT** +6. **SOURCE** [ADVICE; rulings 2; BGG 3630546] **[HERO]**

3. **H3** [PLAY_CARD] [`ursafar-silver`] **WHEN** he is calm, no attack can reach a minion, and he is already on the hex he wants **THEN** Silver enrages him and Blue stays in hand. **WHEN** he is enraged and an active Gold, Red, or Blue primary can hit a new target **THEN** copy that card, not before the enemies who act after init 7 have moved, if the copy is a swing. **WEIGHT** the copied attack's value at init 7 instead of its printed init. **SOURCE** [BGG 3630546; nichik BGG 3458850] **[HERO]**

4. **H4** [PLAY_CARD] [`ursafar-gold`] **WHEN** he is enraged, an enemy hero is exactly 2 away, the step lands adjacent to that hero, and (Attack 4 plus modifiers beats their remaining defense, or a minion will be adjacent after the step and is the real target) **THEN** lunge. On turn 4 this outranks a minion that is not the match-point push. **WEIGHT** hero kill, or 15. **SOURCE** [CARD; BGG 3653998; BGG 3630546] **[HERO]**

5. **H5** [PLAY_CARD] [`ursafar-red-2a`, `ursafar-red-3a`, `ursafar-red-1`] **WHEN** he is enraged, the target is a hero who can block, and an enemy minion is in radius **THEN** attack the hero and remove the minion (up to 2 on Feeding Frenzy). The removal scores lane margin and 0 coins. **WEIGHT** +15 per removed minion if it changes the projected battle, else +2. **SOURCE** [CARD; FAQ; BGG 3630546] **[HERO]**

6. **H6** [PLAY_CARD] [`ursafar-red-2b`, `ursafar-red-3b`] **WHEN** he is enraged and the target will be removed (a minion, or a hero with no defense that beats Attack 5) **THEN** take the coin. On Tear, a hero who will be defeated outranks a minion by the extra Life counter, unless the minion is the push at match point. **WEIGHT** +1 or +2 coins, and the extra Life on top of a normal kill. **SOURCE** [CARD; rulings 6; BGG 3653998] **[HERO]**

7. **H7** [PLAY_CARD] [`ursafar-green-1`, `ursafar-green-2a`, `ursafar-green-3a`] **WHEN** he is enraged, an enemy hero is in range, and he or an ally still has an attack that becomes lethal after one discard **THEN** discard that hero. Apex Predator: if they have no card, the defeat is mandatory and is the play. **WHEN** calm **THEN** do not play these. **WEIGHT** +8, or the kill. **SOURCE** [BGG 3630546; rulings 7] **[HERO]**

8. **H8** [PLAY_CARD] [`ursafar-green-2b`, `ursafar-green-3b`] **WHEN** he is enraged and a discarded attack can hit the hero who just forced the block, or Evolutionary Response can both perform it and retrieve the card he needs **THEN** do that. **WEIGHT** the attack's value, plus 2.5 if a card returns to hand. **SOURCE** [BGG 3630546; rulings 8] **[HERO]**

9. **H9** [CHOOSE_SPACE / CONFIRM] [Blue, enraged] **WHEN** a swap puts a body between an enemy and their target, or pulls him through a wall toward his minion, and Unstoppable Force's extra steps do not leave the hill **THEN** swap, then take the extra steps toward the next target. **WHEN** the swap pulls him off the only minion he can hit **THEN** decline. **SOURCE** [BGG 3630546; rulings 9] **[HERO]**

10. **H10** [PLAY_CARD] [Blue movement bonus] **WHEN** he is not yet enraged **THEN** the move is 1, not 2 or 3. Plan the destination at distance 1. **SOURCE** [rulings 2; FAQ] **[HERO]**

11. **H11** [PLAY_CARD] [any] **WHEN** the projected push leaves him on the far side of the new battle zone and he still has a Movement action **THEN** spend it walking toward the side he must be on next round, even over a coin. **WEIGHT** −10 for "wrong side", which for him is a full round [ADVICE; BGG 3653998]. **SOURCE** [ADVICE] **[GENERAL]**

12. **H12** [DEFEND] [Red] **WHEN** Red's Defense (6–8) plus modifiers beats the attack and Blue does not, or Red is the larger Defense **THEN** keep Gold (Defense 2) and do not spend Red on a minion that same turn if this attack is already revealed. **WEIGHT** his life. **SOURCE** [CARD; REASONED from Defense 2 on Gold] **[GENERAL]**

13. **H13** [PLAY_CARD] [any attack] **WHEN** killing him would cost a Life and the walk back is longer than 3 hexes of his Movement 1 **THEN** decline the trade even if the enemy dies. **WEIGHT** −12 against a mutual kill. **SOURCE** [BGG 3630546; slyestfox BGG 3513251] **[GENERAL]**

14. **H14** [UPGRADE_CARD] **WHEN** the enemy's attacks are mostly ranged and he is being kited **THEN** `ursafar-green-3a` and `ursafar-blue-3b` before a third Defense item. **WHEN** he is landing the minion every round and levels are the win **THEN** Rip then Tear before more Attack. **SOURCE** [BGG 3653998; DaFink BGG 3513251] **[GENERAL]**

15. **H15** [KEEP_UPGRADE] **WHEN** choosing the Red pair **THEN** Abundance/Feeding Frenzy if the battle zone is full of minions he can tax; Rip/Tear if he can sit on one minion and the extra Life matters (enemy Life ≤ 2, or levels 4+). **SOURCE** [BGG 3630546; BGG 3653998] **[GENERAL]**

16. **H16** [CHOOSE_CARD] [Silver's copy, or Green's perform] **WHEN** several active cards qualify **THEN** copy the one whose primary can defeat a hero, else the one that defeats a minion, else the Blue swap. Never a card whose primary cannot start. **SOURCE** [rulings 4] **[HERO]**

17. **H17** [CONFIRM] [`ursafar-purple-4` world] **WHEN** he is on the board **THEN** treat every "If enraged" as true, including the first Gold of the round. Silver may target a resolved Gold, Red, or Blue that has an active-effect sentence, not a resolved Green. **SOURCE** [CARD; nichik BGG 3458850] **[HERO]**

18. **H18** threatAdjust [`ursafar`] **WHEN** he is enraged and Gold is unplayed **THEN** every hex adjacent to an enemy hero that is within 1 of him is a Gold landing (Attack 4, init 12). **WHEN** calm **THEN** only adjacent hexes, and only if he is already there; do not add the lunge. Silver unplayed and a Gold or Red already active: add that attack again at init 7. **SOURCE** [CARD; BGG 3630546] **[HERO]**

19. **H19** [PLAY_CARD] [against him] **WHEN** he is calm and adjacent to a minion **THEN** that minion is his enrage. Taking it, or moving him off it, is worth more than a random minion elsewhere (+6). **WHEN** he has just spent a whole turn to enrage **THEN** he has fewer productive cards left; a push or a move that wastes his next one is the window [Naughty zoot, BGG 3513251]. **SOURCE** [BGG 3513251] **[HERO]**

20. **H20** [RESPAWN] **WHEN** he dies **THEN** the spawn closest to the next battle zone, and the card played while dead is the one with Movement, aimed at that zone. A second death on the walk is the loss condition to avoid. **SOURCE** [ADVICE; BGG 3630546] **[GENERAL]**

21. **H21** [PLAY_CARD] [`ursafar-silver`] **WHEN** he is enraged, init 7 has not yet given him new information, and the copy is a hero swing that a faster enemy can step off **THEN** hold Silver until those faster cards have resolved, unless holding it means he never acts with it. **WEIGHT** +4 for striking at 7 instead of at 12 when the target's escape is faster than 12 and slower than 7. **SOURCE** [BGG 3630546] **[HERO]**

## 7. Sources

- Catalog `heroes/ursafar.json`, handicap `ursafar-handicap`. FAQ: removals are not coins; the Movement bonus is only the card that prints it.
- `heroes/UrsafarHero.kt`, `core/game/rulings/ursafar.md`.
- [BGG 3630546](https://boardgamegeek.com/thread/3630546) Ursafar spotlight (2nd printing), Cryomancy, 2025-12-20, community.
- [BGG 3653998](https://boardgamegeek.com/thread/3653998) Build & Go, Cryomancy, 2026-01-30, community. Item icons in that post were checked against the catalog and corrected where they disagreed.
- [BGG 3513251](https://boardgamegeek.com/thread/3513251) Toxic Heroes. Designer `nichik`, 2025-05-18 (the four-card sequence) and 2025-05-23 (~300 games, win rate above 50%). Community: dusteppe, Cryomancy (Garrus as the counter), slyestfox (punishing to misplay; better in 3v3 one-lane), DaFink and Naughty zoot (do not chase; Ignatia kites him; the enrage turn is a window).
- [BGG 3458850](https://boardgamegeek.com/thread/3458850) designer `nichik`, 2025-02-12: Silver copies an active card with an active effect, not Green; the Ultimate does not make Greens active.
- [BGG 3609893](https://boardgamegeek.com/thread/3609893) rules thread, 2025-11-12: community reads the enrage line as last. The engine ruling agrees; it is not a designer post.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Zoner / Finisher.
- [BGG 3487749](https://boardgamegeek.com/thread/3487749) AegisToast, 2025-03-30: pair him with a fast ranged hero.
- 1st-printing spotlight [BGG 2896403](https://boardgamegeek.com/thread/2896403) was not used. Slyestfox notes the 2nd-printing Gold lunge is tighter than the 1st printing [BGG 3513251].
- No Ursafar strategy thread newer than the January 2026 Build & Go. The October 2026 forum list has none.
