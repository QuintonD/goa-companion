# Tigerclaw (`tigerclaw`) — heuristic playbook

2nd printing. Dashboard: Attack 4–8, Defense 1–4, Initiative 8, Movement 7–8. Complexity 1. Catalog roles: melee, disabler, farming. Official advice is the four lines in `heroes/tigerclaw.json` (`advice`).

Coin units follow `heuristic/DESIGN.md`: one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5. A hero kill is the coins paid (the hero's level to the killer, its lowest tier to each ally) plus the Life counters spent (1 at levels 1–3, 2 at 4–6, 3 at 7–8). Rules tagged **[GENERAL]** use only distances, counts, initiatives, lane, coins, hand, revealed cards and public pieces. **[HERO]** needs a named flag (poison holder and strength, which Blue branch is in hand, "immune to enemy attack actions next turn").

## 1. Identity

Assassin who farms while he waits, then finishes. Cryomancy's class pair is Finisher / Initiator [BGG 3469903, 3779890]. He does not stand in a fight. He dashes through a target, takes the coins or the kill, and is gone before the answer.

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | Assassin | Kill and coin pressure, not a front line [BGG 3469903; Yzrac, BGG 3688691, 2026-07-29, community] |
| `idealDistance` | 2 | `tigerclaw-gold` Blink Strike needs a straight line of 2 through an enemy unit onto an empty space. Adjacent is the attack; 2 is the approach. |
| `aggression` | 0.65 | Happy to open on a hero when the dash lands and a card remains to leave. Otherwise take the minion. |
| `laneWeight` | 0.45 | Minions fund the steal and the levels; the round is won by the turn-4 dash or the coin denial. |
| `keepDefender` | true | Blues have **no Defense number**. The card that matches the incoming attack type must stay in hand until it blocks, or a Red (Defense 3–6) must. |
| `upgradeOrder` | see §3 | Branch is the matchup, not a fixed colour order. |

He is the fastest stat in the roster (Initiative 8). Printed Gold 13 ties or beats the Gold band 6–13 [STRATEGY.md §1.6 DATA]. Designer, 2026: "Your gold dodges or ties with any card. Your blues either block or dodge any attack. And you have both green and silver to move after your opponents" [nichik, BGG 3688691, 2026-04-06]. That is the whole plan: act first to hit or leave, act last to steal or hide.

## 2. Card-by-card

Defense on a secondary action does **not** apply the card text. Only Blues apply text while defending [nichik, BGG 3688691]. A failed Block (wrong attack type) defeats him and skips the rest of the Blue text [rulings/tigerclaw.md 8].

### `tigerclaw-gold` Blink Strike — Attack, init 13, Attack 3, Defense 1, Movement 1

"Before the attack: Move 2 spaces in a straight line through an enemy unit; target that unit." The landing space must be empty. No landing, no attack [FAQ; rulings 1].

- **For:** the unavoidable opener and the turn-4 finish. Farming a minion nobody can answer, or killing a hero whose remaining defense loses to Attack 3 plus modifiers.
- **When:** turn 1–2 on a minion whose far side is empty and no enemy still to act can reach the landing space; turn 4 on a hero already stripped, or any time the dash both hits and leaves the threat zone.
- **Best spaces:** the empty hex directly beyond the target, away from the enemy who acts next. A friendly unit on the far side of the target is a denied attack [ADVICE].
- **Do not:** dash into a cluster; dash when the only landing is the space an enemy still holding a melee attack wants; spend it as a Defense 1 block.
- **In hand:** the threat. Enemies must not stand with an empty hex behind them. Defense 1, so it is a terrible block.

`tigerclaw-handicap` Nimble Strike is the same dash at init 12, Attack 2, and the unit must be an enemy **hero**.

### `tigerclaw-silver` Blend Into Shadows — Skill, init 6, Defense 2, Radius 2

"If you are adjacent to terrain, place yourself into a space in radius; if you do, Next turn: You are immune to enemy attack actions." Terrain includes the water ring and units that count as terrain [rulings 3]. Not offered if he is not adjacent to terrain. Immunity is attack actions only, next turn of this round only, and it is public the moment the card resolves.

- **For:** the reposition that makes the following turn free. Use that free turn to take a minion or to stand next to the hero Gold will finish [BGG 3508807, Cryomancy].
- **When:** most rounds, from a space beside terrain (walls and the wave — Cryomancy). Init 6 is after Golds, Blues and most Reds, so the place sees where they already moved.
- **Best space:** radius 2, beside terrain again (so Silver can be retrieved and replayed), on a minion, not in a revealed attack that is not an attack action (discards still work).
- **Do not:** play it with no follow-up that wants immunity; expect anyone to attack him next turn (they will not — Yevaud's objection in BGG 3688691 is right, and the value is the uncontested action, not a bait); treat it as immunity to discard.
- **In hand:** Defense 2 and a teleport. Holding it denies the immunity. Prefer to play it and keep Blue.

### `tigerclaw-red-1` Hit and Run — Attack, init 9, Attack 3, Defense 3, Movement 4

Adjacent attack, then may move 1. The safe hit: strike, step off [Yzrac, BGG 3688691].

- **When:** a minion or a hero he can leave. Turn 3 stalk into turn-4 Gold is the Cryomancy pattern (attack, move, move, attack).
- **Do not:** hit and stay adjacent to a hero who still acts this turn.
- **In hand:** the real numeric defense (3) when Blue is the wrong type.

### `tigerclaw-red-2a` Combat Reflexes — item: Initiative. Init 9, Attack 4, Defense 3, Movement 4

May move 1 before the attack; the after-move exists only if he did not take the before-move [rulings 4]. Tier 3 `tigerclaw-red-3a` Leaping Strike (item: Radius, init 10, Attack 4, Defense 4) allows both moves.

- **For:** a one-space pseudo-range. Step in if nothing threatens the arrival; hit and step out if something does [BGG 3469903].
- **Item:** Initiative on every card. This is the item that keeps Gold at the ceiling, not Backstab (Cryomancy's Build & Go calls Backstab an initiative item; the catalog icon is Defense. Card text wins.)

### `tigerclaw-red-2b` Backstab — item: Defense. Init 9, Attack 5, Defense 5, Movement 5

"+2 Attack if a friendly unit is adjacent to the target." The friendly unit is never himself [rulings 5]. `tigerclaw-red-3b` Backstab with a Ballista (item: Defense, init 10, Attack 5, Defense 6, Movement 5, Range 1, **ranged**) adds that +2 and "the target cannot perform a primary action to defend." Range 1 is adjacent until a Range item. Because the card is ranged, "Block a ranged attack" stops it even on an adjacent target [RULINGS 19].

- **For:** the melee threat. Attack 5, or 7 with the friendly unit, at init 9, Defense 5 in hand. Stand where a friendly minion touches the target.
- **Ballista:** the answer to Defense-primary and Defense/Skill blocks (Arien's Blue). A secondary Defense still works [rulings 5]. Take it when those cards are what is keeping targets alive.
- **Do not:** count his own figure as the friendly unit; fire Ballista into a known ranged-Block.

### `tigerclaw-green-1` Light-Fingered — Skill, init 2, Defense 1, Movement 3

May move 1, take 1 coin from an adjacent enemy hero, and if a coin was taken may move 1 again. No adjacent enemy after the first move: the action stops, no second move [FAQ; rulings 6]. A hero with 0 coins can be chosen and then nothing is taken, so the second move does not happen.

### `tigerclaw-green-2a` Pick Pocket — item: Attack. Init 2, Defense 1, Movement 3

Move up to 2, take 1 coin, then may move 1. `tigerclaw-green-3a` Master Thief (item: Movement, init 1, Defense 2, Movement 3) takes 1 or 2 coins and the exit is up to 2. The number is asked only when the victim has 2 or more [rulings 6].

- **For:** the best farm in the kit. Coins for him, a denied level for them, and the move he wanted anyway [BGG 3469903; anwei, BGG 2886354: the drawback is that it spends Green].
- **When:** after victims have stepped into the 2-space reach (init 2, then 1, is after almost every attack). Turn 4 if they might step out on turn 3 [BGG 3508807, "The Thief"]. Double-level openings are worth the wave only when the stolen coin completes his level (~1.5) and the wave is not match point.
- **Do not:** open the round with it when a minion must be answered first; path through a space that leaves him adjacent to a hero who still acts; steal 2 when 1 already levels him and the extra coin is the victim's last defense resource — take 2 anyway if it denies their level.
- **In hand:** Defense 1. Not a block. It is the slow escape (Movement 3, Fast Travel) once the attacks have resolved.

### `tigerclaw-green-2b` Poisoned Dagger — item: Initiative. Init 2, Defense 1, Movement 3, Range 3

Poison marker: −1 Initiative, Attack and Defense until end of round (or the holder's defeat). `tigerclaw-green-3b` Poisoned Dart (item: Initiative, Range 3) is −2. Values may be 0 or negative [FAQ]. One marker; giving it again moves it. The text says "a hero", so a friend is legal [rulings 7] and is almost never correct.

- **For:** marking the hero the team will hit, or dragging a rival fast hero below him [BGG 3469903]. Items still apply; poison is another modifier [anwei, BGG 3469903, 2025-03-03, playtester].
- **When:** the marked hero will be attacked this round by someone whose total now beats a defense it did not beat before, or their Gold would otherwise tie his.
- **Do not:** mark a hero nobody can reach; forget the penalty on his own later attack (Cryomancy's own miss); mark a friend to "save" them.
- **In hand:** still Defense 1. The marker is the value, and it is public.

### `tigerclaw-blue-1` Dodge — Defense, init 10, Movement 3, Block, no Defense

"Block a ranged attack." Nothing else.

### `tigerclaw-blue-2a` Sidestep — item: Attack. Init 11, Movement 3

Block a ranged attack, then may move 1. `tigerclaw-blue-3a` Evade (item: Attack, init 11) also retrieves the resolved or discarded basic skill (`tigerclaw-silver`) [rulings 9]. Retrieving it from a turn slot cancels the immunity.

### `tigerclaw-blue-2b` Parry — item: Defense. Init 11, Movement 3

"Block a non-ranged attack. The attacker discards a card, if able." `tigerclaw-blue-3b` Riposte (item: Range, init 11): the attacker discards or is defeated. The attacker chooses the card; with none, Riposte defeats them and the coins go to Tigerclaw [rulings 10].

- **For:** the only text that saves him. Ranged-block is the default, because melee is dodged by moving and by init [BGG 3469903; nichik, BGG 3688691]. Melee-block when the scary attacks are melee (Razzle, Brogan's charge, a table that has both upgraded into melee).
- **When to play as a primary:** only to dodge with the Movement 3 / Fast Travel, or to step 1 after a Sidestep/Evade block. Never as a speculative skill.
- **In hand:** this is the defender. A known ranged-block means melee attacks ignore it completely, and the reverse [STRATEGY.md §5]. Holding the wrong branch is how he dies to Attack 3 [Yevaud's game, BGG 3688691].
- **Evade:** play Silver early, block later, take Silver back, stand beside terrain, do it again [BGG 3508807].
- **Riposte:** let them hit, then punish. Do not spend it on a minion.

### `tigerclaw-purple-4` Cloak and Daggers

"After you perform a basic action, you may repeat it once; if you repeat an attack action, you cannot target the same unit." Every basic action, including secondary Movement, Fast Travel, Clear and Attack on Gold and Silver. Not Defense, not Hold, not a repeated instance [rulings 11]. Ask only when the repeat can do something.

- Repeat a minion dash onto a second minion or the hero behind it. Repeat a steal only when a second adjacent victim exists. Do not repeat into the same landing problem.

## 3. Upgrade path

Match the Blue to the attack type that can actually touch him. Everything else serves coins and the Gold's initiative.

**Default (Mischievous Scamp, items corrected from the catalog):**

1. `tigerclaw-green-2a` Pick Pocket — the Attack item and the coin engine. First, unless a faster enemy is already tying Gold 13, in which case `tigerclaw-green-2b` Poisoned Dagger (Initiative item) or `tigerclaw-red-2a` Combat Reflexes (Initiative item) comes first.
2. `tigerclaw-red-2b` Backstab — Defense item, Attack 5 / 7 beside a friend. Take `tigerclaw-red-2a` instead when he must step in and out and the Initiative item is the point (Devious Assassin) [BGG 3508807].
3. `tigerclaw-blue-2a` Sidestep if any enemy's best attack is ranged; else `tigerclaw-blue-2b` Parry. Sidestep's Attack item feeds Backstab; Parry's Defense item feeds the brawl.
4. Tier III: `tigerclaw-blue-3a` Evade when Silver should be replayed every round (Attack item). `tigerclaw-red-3b` Ballista when targets live on primary-Defense blocks, and only after a Range item exists or a Range-1 shot is enough [BGG 3469903: "doesn't do much without a ranged item"]. `tigerclaw-green-3a` Master Thief for the second coin and the Movement item. `tigerclaw-green-3b` Poisoned Dart when the −2 and the Initiative item beat another coin. `tigerclaw-blue-3b` Riposte when the table has committed to melee and their Defense is too high to punch — the discard-or-die is the kill.
5. `tigerclaw-red-3a` Leaping Strike last on the mobile path: the Radius item does nothing until Silver or a later radius card exists. Tuck it when Ballista is the hand card.

**Tuck as items:** the branch not chosen. Prefer Initiative and Attack in hand early; Defense items when he is diving; never tuck the Blue that matches the live threat. Range is tucked until Ballista or Poison wants it. Radius is the weakest tuck until Silver's place is short.

**Phase:** levels 1–3 steal and stay alive (Green, then the matching Blue, Red as the weapon). Levels 4–6 turn Gold into a one-shot (Attack items, Ballista, Riposte) or into a second Silver each round (Evade). Level 7 Ultimate: every basic action is two, so a dash plus a different target, or a steal plus a step, is the round.

## 4. Synergies and counters

**With:** a teammate who injures on turns 1–2 so his init-13 Gold finishes on turn 4. A friendly minion or hero adjacent to the victim turns Backstab on. Heroes who hold a target still (pulls, roots) turn the dash from a guess into a kill. He does not need them adjacent to himself.

**He fears:** ranged attacks while he holds Parry, melee attacks while he holds Dodge, and any discard [ADVICE; nichik, BGG 2886354, 2022-10-18, designer: "With no options to heal and his tricky escape cards, Tigerclaw really hates discard"]. Movement caps (Garrus Silver): "All Tigerclaw's movement cards are movement 3+ up. Hence Garrus's Silver has strong effect on him" [same post; still true of Red 4–5, Green 3, Blue 3]. Being pulled off terrain deletes Silver [BGG 3469903]. A body on the far side of his target deletes Gold [ADVICE]. Dusteppe, same thread: two ranged heroes and he should not rob and leave.

**Playing against him (`threatAdjust`):** read the Blue. Attack with the other type. Stand with an obstacle or a body in the space two hexes through you. Deny terrain. Discard him when he wants to Silver, to attack, or to steal [nichik, BGG 2886354]. Do not try to out-initiative his Gold; out-position the landing. His Silver is not immunity to skills. After he dashes, he is on a known hex with Defense 1–5 still in hand — hit the landing if your card is slower than 13 and the landing is in range.

## 5. Common mistakes

- Playing him as a bruiser who stays adjacent [Yzrac, BGG 3688691].
- Treating Blue text as a reaction on the card he played this turn. The block comes from hand [Noobozor, BGG 3688691].
- Blocking with Gold or Green (Defense 1) while Red or the matching Blue is in hand.
- Dashing when the landing is not empty, then having no attack.
- Spending Green on turn 1 and having no slow move left to steal or leave.
- Poisoning a hero and then calculating the attack without the −1/−2.
- Using Silver as bait. It announces the immunity.
- Calling Backstab an Initiative item, or Master Thief an Initiative item. They are Defense and Movement.
- Repeating Cloak and Daggers onto the same unit, or repeating a dash with no second landing.

## 6. Heuristic rules

`evalTerms` (his pieces, from the seating team's view):

- Poison on an enemy hero: +4 at −1, +8 at −2, while it will be attacked or will act after him this round; +2 if it only taxes a card they will not play. Poison on a friend: −6. [HERO]
- "Immune to enemy attack actions" already in force on him: +6 if he has not acted this turn (the free action), +1 if he has (only the remaining enemy attacks whiff). [HERO]
- No persistent token. Do not invent one.

`threatAdjust` when he is the enemy: see H18–H20.

1. **H1** [PLAY_CARD] [`tigerclaw-gold`, `tigerclaw-handicap`] **WHEN** an enemy unit has an empty hex 2 spaces through it in a straight line, that landing is not in the reach of an enemy who acts after init 13, and (the target is a minion, or his Attack 3 plus modifiers beats every defense card that target can still hold, or the target has 0 cards) **THEN** play Blink Strike and land on the far side, preferring the landing farther from the next enemy actor. **WEIGHT** minion ≈ 15; hero kill ≈ coins + life. **SOURCE** [CARD; FAQ; BGG 3469903] **[GENERAL]**

2. **H2** [PLAY_CARD] [`tigerclaw-gold`] **WHEN** every straight-line landing through a legal target is occupied or is a kill for an enemy still to act **THEN** do not play Gold. **WEIGHT** −20 (a fizzled primary). **SOURCE** [ADVICE; FAQ] **[GENERAL]**

3. **H3** [DEFEND] [`tigerclaw-blue-1`, `tigerclaw-blue-2a`, `tigerclaw-blue-3a`] **WHEN** the attack is ranged **THEN** block with the Blue. On Sidestep/Evade, step 1 toward terrain if he is not already adjacent to it. On Evade, retrieve `tigerclaw-silver` if it is resolved or discarded and he can be adjacent to terrain next turn. **WEIGHT** 25 (the life he does not spend) plus 4 if Silver returns to hand. **SOURCE** [CARD; rulings 8–9; BGG 3508807] **[GENERAL]**

4. **H4** [DEFEND] [`tigerclaw-blue-2b`, `tigerclaw-blue-3b`] **WHEN** the attack is not ranged **THEN** block. Riposte: if the attacker has no other card, the defeat is the point (do not decline). **WEIGHT** 25, or a hero kill if Riposte finds an empty hand. **SOURCE** [CARD; rulings 10] **[GENERAL]**

5. **H5** [DEFEND] [any Blue] **WHEN** the attack is the type this Blue does not block **THEN** do not discard that Blue. Defend with Red if Red's Defense plus modifiers beats the attack; else the smallest Defense that beats it; else decline only when no card beats it. **WEIGHT** −15 for a failed Block (he dies and the Blue text does not happen). **SOURCE** [rulings 8; BGG 3688691] **[HERO]** (which branch is in hand)

6. **H6** [PLAY_CARD] [`tigerclaw-silver`] **WHEN** he is adjacent to terrain, a radius-2 space is empty, and he still holds a card that can take a minion or a hero on the next turn **THEN** place beside terrain, on or next to that future target, outside current attack reach. **WEIGHT** +8 for a safe minion next turn, +12 if the next card kills a hero from that space. **SOURCE** [ADVICE; BGG 3469903; BGG 3508807] **[GENERAL]**

7. **H7** [PLAY_CARD] [`tigerclaw-silver`] **WHEN** no enemy has a card in the discard and no attack is aimed at him and the next turn has nothing to do safely **THEN** hold Silver. **WEIGHT** −4 (telegraphed immunity with no payoff). **SOURCE** [REASONED from Yevaud, BGG 3688691, against Cryomancy's "most rounds": play it when the following action is worth a card] **[GENERAL]**

8. **H8** [PLAY_CARD] [`tigerclaw-green-1`, `tigerclaw-green-2a`, `tigerclaw-green-3a`] **WHEN** an enemy hero with at least 1 coin is within the card's move (1, or 2) and the exit step can leave the threat zone of enemies still to act, and he is not the only hero who can stop a match-point minion this turn **THEN** steal. Take 2 on Master Thief when the victim has 2 or more. **WEIGHT** +1 per coin, +1.5 if the coin levels him this round, +3 if it denies their level. **SOURCE** [CARD; BGG 3469903; BGG 3508807] **[GENERAL]**

9. **H9** [CHOOSE_SPACE] [Green steal] **WHEN** the first move can end adjacent to the victim **THEN** that end is mandatory in the search; a path that does not is a fizzle and scores −4. The second move leaves toward his next target or toward terrain. **SOURCE** [FAQ; rulings 6] **[GENERAL]**

10. **H10** [PLAY_CARD] [`tigerclaw-red-1`, `tigerclaw-red-2a`, `tigerclaw-red-3a`] **WHEN** an adjacent enemy (or one space away, if the before-move is legal) can be hit and the after-move reaches a hex no remaining enemy attack covers **THEN** hit and take the after-move. If the before-move is what brings the target into reach and the arrival is safe, take the before-move and skip the after-move. **WEIGHT** minion 15, or the card stripped from a hero (~4–8). **SOURCE** [BGG 3469903] **[GENERAL]**

11. **H11** [PLAY_CARD] [`tigerclaw-red-2b`, `tigerclaw-red-3b`] **WHEN** a friendly unit is adjacent to the target **THEN** prefer that target over an equal target without the +2. Ballista: prefer a hero whose remaining defense is a primary Defense action, and do not shoot it if they hold a ranged Block. **WEIGHT** +6 for the +2 when it flips a defense; +8 extra when primary-Defense is deleted. **SOURCE** [CARD; rulings 5; BGG 3508807] **[GENERAL]**

12. **H12** [PLAY_CARD] [`tigerclaw-green-2b`, `tigerclaw-green-3b`] **WHEN** an enemy hero in range 3 will be attacked later this round by him or an ally, and the −1 or −2 flips that attack from "they can block" to "they cannot", or drops their remaining initiative below his **THEN** poison that hero. Never a friend. If the marker is already on a better target, do not move it. **WEIGHT** +8 when it creates a kill, +4 when it only wins an initiative tie he would have lost. **SOURCE** [CARD; BGG 3469903; anwei BGG 3469903] **[HERO]**

13. **H13** [PLAY_CARD] [any] **WHEN** it is turn 4, an enemy hero in dash range has 0 cards or only cards his Attack beats, and Gold's landing is empty **THEN** Gold over a steal and over a minion, unless the minion is the one that pushes at match point (then the minion, and only if Gold cannot do both by dashing through the hero onto a plan that still reaches the minion — it cannot; pick the push). **WEIGHT** hero kill vs 15. **SOURCE** [BGG 3469903; nichik BGG 3688691] **[GENERAL]**

14. **H14** [CHOOSE_ACTION] [any] **WHEN** the primary has no legal target and the card has Movement **THEN** move or Fast Travel toward the next round's minion, ending beside terrain if Silver is still in hand or in the discard and Evade can retrieve it. Do not Hold a Gold or Green that could have moved. **WEIGHT** +3 vs a wasted Hold. **SOURCE** [ADVICE; AI.md holding weakness] **[GENERAL]**

15. **H15** [KEEP_UPGRADE] [Blue pair] **WHEN** the scariest enemy attack still in their possible pool is ranged **THEN** keep Sidestep/Evade; **WHEN** it is melee and ranged threats are absent **THEN** keep Parry/Riposte. **WEIGHT** 10 (the branch is the difference between a block and a death). **SOURCE** [BGG 3469903; BGG 3508807] **[HERO]**

16. **H16** [UPGRADE_CARD] [any] **WHEN** level 2–4 and Gold is not strictly faster than every enemy Gold **THEN** upgrade the colour whose item is Initiative (`tigerclaw-red-2a`, `tigerclaw-green-2b`, later `tigerclaw-green-3b`) before a third Attack item. **WHEN** he already ties for fastest Gold **THEN** Pick Pocket first. **SOURCE** [CARD icons; BGG 2908021 via STRATEGY §1.11; BGG 3508807 corrected] **[GENERAL]**

17. **H17** [CONFIRM] [`tigerclaw-purple-4`] **WHEN** the repeat of an attack has a different legal unit, or the repeat of a move/Fast Travel/steal changes his hex or takes a coin **THEN** yes. **WHEN** the repeat's only target is the same unit, or the second dash has no landing **THEN** no. **WEIGHT** a second minion ≈ 15, a second coin = 1. **SOURCE** [rulings 11] **[GENERAL]**

18. **H18** threatAdjust [`tigerclaw`] **WHEN** scoring an enemy hex **THEN** add the Blink Strike threat to every enemy unit that has an empty hex two steps straight through it, at Attack 3, init 13, until Gold is known spent. The landing hex is as dangerous as the target. **WEIGHT** same as a revealed init-13 Attack 3. **SOURCE** [CARD; ADVICE] **[GENERAL]**

19. **H19** threatAdjust [`tigerclaw`] **WHEN** his Blue has been seen this game **THEN** attacks of the type it does not block ignore his Block and use only his numeric Defense (Red 3–6, Silver 2, Green 1–2, Gold 1). Attacks of the matching type treat him as unhittable while that Blue is still in hand. **SOURCE** [CARD; BGG 3688691] **[HERO]**

20. **H20** [PLAY_CARD] [against him] **WHEN** he is adjacent to terrain and Silver is unplayed **THEN** prefer a discard over an attack. **WHEN** he has just resolved Silver **THEN** do not attack him next turn; take the minion or a different hero. **WEIGHT** the attack he will not land ≈ 0, the discard ≈ 8 (he has no heal). **SOURCE** [nichik, BGG 2886354; ADVICE] **[GENERAL]**

21. **H21** [RESPAWN] [any] **WHEN** he is off the board **THEN** respawn on the throne spawn closest to the battle zone that no enemy still to act can attack, beside terrain if one exists. Play the lowest-value card (Green if the steal cannot reach). **SOURCE** [STRATEGY §1.10; ADVICE mobility] **[GENERAL]**

22. **H22** [PLAY_CARD] [any] **WHEN** a movement-limit effect caps him at 1 (Garrus Silver and the like) and the card's only value was a Movement 3+ leave **THEN** do not spend Gold or Red on a plan that needs the printed movement; hit what is already adjacent or hold the defender. **WEIGHT** −6 on a move that the cap deletes. **SOURCE** [nichik, BGG 2886354] **[GENERAL]**

## 7. Sources

- Catalog `app/src/main/assets/catalog/heroes/tigerclaw.json` (2nd printing): card ids, numbers, text, advice, FAQ. Handicap `tigerclaw-handicap`.
- Engine `heroes/TigerclawHero.kt`, `core/game/rulings/tigerclaw.md`.
- [BGG 3469903](https://boardgamegeek.com/thread/3469903) Tigerclaw Hero Spotlight (2nd printing), Cryomancy, 2025-02-28, community. Item-icon slips corrected against the catalog.
- [BGG 3508807](https://boardgamegeek.com/thread/3508807) Tigerclaw Build & Go, Cryomancy, 2025-05-08, community.
- [BGG 3688691](https://boardgamegeek.com/thread/3688691) 2026-04-05, designer `nichik` (2026-04-05, 2026-04-06), community Yevaud, Frantic Ferret, Blair46, Noobozor, Yzrac (2026-07-29).
- [BGG 2886354](https://boardgamegeek.com/thread/2886354) 1st-printing spotlight thread; designer `nichik` 2022-10-18 and playtester anwei 2022-10-18, kept where the 2nd-printing numbers still match (movement 3+ on Red/Green/Blue, no heal, hates discard). Eric_McClain's 1st-printing card text was not used.
- [BGG 3469903] anwei, 2025-03-03, playtester: poison is a modifier on top of items.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Classes of Atlantis, Cryomancy, 2026-10-09: Finisher / Initiator.
- STRATEGY.md §1.6 initiative bands, §5 Tigerclaw. No 2nd-printing thread after 2026-10-24 changes the plan; 3688691 is the 2026 designer thread and was already cited there.

## Implementation notes

`TigerclawHeuristics.kt` (ADR-085). The general book scores an attack card from its printed reach (`HContext.strikeFrom`). Where this hero's text narrows the targets, the playbook re-scores the card's PLAY_CARD outlook on the units the engine would really offer (`BatchDSupport.strikeOn` + `strikeFix`): the card keeps its movement value and loses the hit it cannot make. That replaces the draft's flat −28 "fizzle" penalty, which also threw away the card's secondary Movement.

- **Implemented:** H1/H2 (Blink Strike is scored only on an adjacent enemy unit with an empty hex straight behind it; the handicap card on heroes only), H1 landing (the CHOOSE_SPACE of the dash picks the target: the landing is scored by the unit passed through), H6/H7 (Blend Into Shadows needs terrain beside him and a follow-up card; worth more while he is exposed), H8 (steal: best victim with a coin within the move, +1.5 when it completes his level, +3 when it costs theirs), H11 (Backstab target with a friendly unit beside it), H12 (Poison: an enemy someone will attack; never a friend, −12 on the target choice), H15/H16 (KEEP_UPGRADE: the Blue that blocks the type their possible pool mostly holds; an Initiative item when an enemy Gold reaches 13), H18 (threat of the dash), H21 (respawn beside terrain). `evalTerms`: Poison 3 per point on an enemy, −6 on a friend; Blend's immunity pending or in force +3.
- **Engine already does it:** H3–H5. The Defend question carries the engine's block preview, and a Blue of the wrong type previews as a failed Block, so the general book already scores it −18 and the right Blue +24. A second note would double count.
- **Skipped:** H9/H10 (the move after a steal or a hit is a CHOOSE_SPACE the general placement already scores with exposure), H13 (a turn-4 kill is `heroStrikeWorth`'s sure-kill case), H14/H17 (CHOOSE_ACTION and the Cloak-and-Daggers CONFIRM go through one-ply lookahead at strength 5+), H19/H20 (advice for the heroes playing against him, and the Blue seen this game needs the card log, which `HContext` keeps only as `seen` for card counting), H22 (movement caps are engine effects `HContext` does not expose).
- **Engine vs research:** no disagreement found. The engine confirms: the dash ignores obstacles but must end on an empty hex, the target is the unit passed; Light-Fingered on a 0-coin hero takes nothing and gives no second move; Poison may target a friend; Riposte defeats an attacker with no card.
- **Ablation (strength 8):** 56–40 of 96 = 58.3% (Wilson 48.3–67.7%), with the playbook against without it, 2026-11-21 (`AI_H_ABLATION=tigerclaw AI_H_STRENGTH=8 AI_H_GAMES=96`: 48 line-ups, both sides).
