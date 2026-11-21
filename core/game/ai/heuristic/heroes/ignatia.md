# Ignatia (`ignatia`) — heuristic playbook

2nd printing. Icons from `ignatia.json`. `{tiebreaker_blue}` is the blue-star face (`state.tieBreaker == BLUE`). `{tiebreaker_orange}` is the orange-gear face, the red team's symbol (rulings/ignatia.md 1). She reads the coin when the action is performed.

The 1st-printing spotlight [BGG 2887471] (2022-06-22, community, author null) is right about **how ties flip the coin** and wrong about most card texts. Current silver does not set the coin. Current gold is attack 2, not "the highest damage gold". Current blue-orange text removes or defeats a minion; it does not discard the adjacent hero. Current alt blue moves a hero a fixed 2 or 3 in a straight line. Current ultimate flips the coin once per turn and may perform the action again; it is not eight actions. Those 1st-printing lines are rejected. The tie procedure below is the rulebook, which that spotlight also describes, and it still holds.

Coin procedure (rulebook p.7, as in STRATEGY.md §1.6): highest initiative acts first. On a cross-team tie the team showing acts first, then that player flips the coin. Same-team ties do not flip.

- If she herself is in a cross-team tie, both winning it and losing it leave **her team's face** showing while she acts. Her team's text is the reliable one on her own ties.
- If a teammate wins a cross-team tie before she acts, that teammate flips the coin to the enemy face before her action.
- If an enemy wins a cross-team tie before she acts, they flip it onto her team's face.
- If no cross-team tie resolves before her action, she reads the coin as it stands.

Official advice: have a target on both faces; her team's face is the one an untimely flip is less likely to take away; silver takes control of the text and spends her safety.

## 1. Identity

| Field | Value |
|---|---|
| Role | `Ranged` |
| Complexity | 4 |
| `idealDistance` | 3. Gold's hero face is range 3. Primary red is range 2 then 3, and wants both a straight-line target and a non-line target. Alt red is range 5 and wants either adjacent or exactly maximum range. |
| Lane vs kill | `laneWeight` 0.45. Blue-orange removes or defeats a minion next to a hero. Gold-blue hits an adjacent minion. She is a real pusher on tier-3 red [BGG 3487749] and a hero hitter when the coin matches. |
| `aggression` | 0.60 when both faces have a target, 0.30 when only one does |
| `keepDefender` | true while silver is the plan. Silver prints defense 7 and initiative 1. Spending it for Equilibrium takes the best shield out of hand. Reds print defense 4–6. |
| `upgradeOrder` | §3 |

Profile bars (not added in play): Attack 5→5, Defense 6→6, Initiative 2→2, Movement 4→5. Order is the icons: green 4–5 and silver 1 act late, gold 11 and blue 9–10 act early-middle, red 7–8 sits with the pack. Late is good: more ties have already flipped, and bodies have stopped moving. It is bad against a hero who walks out at initiative 9 or higher.

Magma tokens are ordinary obstacles, removed at end of round, no supply limit in the engine (rulings/ignatia.md 6). They matter because blue-blue discards a hero adjacent to a token or a minion.

## 2. Card-by-card

`ignatia-gold` Chaos Bolt. Initiative 11, attack 2, defense 2, movement 1, range 3, ranged. Blue face: a minion adjacent. Orange face: a hero in range. Attack 2 does not kill a hero who has a card. Play when **both** a minion is adjacent and a hero is in range 3, or when the face she will actually read has its target. Do not play it as a finisher. Defense 2 is a bad shield. The 1st-printing "non-adjacent only" and "highest damage gold" are false.

`ignatia-silver` Equilibrium. Initiative 1, defense 7, skill. This round, each primary action or repeat, she may apply either face. A face whose first instruction cannot start is not offered (rulings/ignatia.md 3). Play on turn 1 of a round where two or three of her other cards would otherwise be coin-locked, and only when she can survive without defense 7 in hand. Do not play it for a single action she could already do on the coin she can predict. It does not lock the coin for her team. The old FAQ line about Nebkher "re-setting the coin" by copying it is a different printing (rulings/ignatia.md 4).

`ignatia-red-1` Playing with Fire. Initiative 7, attack 3, defense 4, movement 3, range 2, ranged. Blue: a unit in range not in a straight line. Orange: a unit in range in a straight line. Adjacent is a straight line (rulings/ignatia.md 5). Stand so one enemy is on a line and another is not, or so the one target can be stepped onto either geometry before she acts.

`ignatia-red-2a` Erratic Fireblast. Same geometry, attack 3, defense 5, range 3, item **defense**. The safe red. Range 3 makes "both geometries" much easier.

`ignatia-red-2b` Crack of Doom. Initiative 7, attack 5, defense 6, movement 3, range 5, item **initiative**. Blue: adjacent unit. Orange: a unit at **maximum** range (exactly 5, plus range items). The hero-hitting red. She wants to be exactly range 5 from one target and adjacent to another, or to know the face.

`ignatia-red-3a` Loosely-Aimed Firebolts. The 2a geometry, attack 4, defense 5, range 3, initiative 8, item **radius**. The repeat-once-on-a-different-hero is on the **orange** (straight line) face only (rulings/ignatia.md 2). The first target may be a minion; the repeat must be a hero she has not targeted.

`ignatia-red-3b` Imminent Eruption. The 2b geometry, attack 6, defense 6, range 5, initiative 8, item **initiative**. The repeat-once-on-a-minion is on the **blue** (adjacent) face only. Attack 6 is the number that kills. This is the "very good pusher on T3 red" [BGG 3487749].

`ignatia-green-1` Path of Ashes. Initiative 5, defense 2, movement 2, radius 3. Blue: move up to 2 in a straight line; place a Magma token on each empty space she moved through or out of (the start and the path, not the destination). A move of 0 places nothing. Orange: place up to 2 Magma tokens in radius. Tokens enable her blue discard and block paths. They also block her team.

`ignatia-green-2a` Path of Cinders. Move up to 3, or place up to 3, radius 4, item **defense**.

`ignatia-green-3a` Path of Flames. Move up to 4, or place up to 4, radius 4, defense 3, initiative 4, item **defense**.

`ignatia-green-2b` Unstable Portal. Initiative 5, defense 2, movement 2, radius 4, item **attack**. Blue: swap with a friendly unit in radius. Orange: swap with an enemy unit in radius.

`ignatia-green-3b` Chaos Gate. The same swaps, plus blue may move the swapped unit 1 and orange may move herself 1. Defense 3, item **attack**. Not a defense-primary. The 1st-printing "surprise swap when attacked" is false.

`ignatia-blue-1` Abrupt Combustion. Initiative 9, defense 3, movement 3, radius 3. Blue: an enemy hero in radius who is adjacent to a token or a minion discards a card, if able. Orange: remove an enemy minion in radius that is adjacent to an enemy hero. Remove gives no coins. Presence checks count immune units; the hero or minion she picks is targeted (rulings/ignatia.md 7).

`ignatia-blue-2a` Spontaneous Immolation. Same, radius 4, defense 4, item **attack**.

`ignatia-blue-3a` Violent Conflagration. Blue: that hero discards **or is defeated**. Orange: **defeat** the minion (coins). Radius 4, defense 4, initiative 10, item **range**. This is the kill card in the Min combo [BGG 3487749].

`ignatia-blue-2b` Searing Heat. Initiative 9, defense 4, movement 3, radius 3, item **initiative**. Blue: move a friendly hero in radius 2 spaces in a straight line. Orange: the same for an enemy hero. The move is exact. A hero who cannot make it is not offered. With no legal hero the primary does not start (rulings/ignatia.md 9).

`ignatia-blue-3b` Scorching Blaze. The same, 2 **or** 3 spaces, defense 4, item **movement**.

`ignatia-purple-4` Chaos Incarnate. The first time each turn she finishes a primary action, she may flip the coin; if she does, she may perform that action again on different targets. The second performance reads the new face, so it is the other text, unless Equilibrium lets her pick (rulings/ignatia.md 10). Flipping is optional and the repeat is optional. Spaces are not targets, so a Path may be walked again. The flip also changes later ties this turn.

`ignatia-handicap` Unreliable Jolt. Initiative 10, attack 1, defense 1, movement 1, range 2, ranged. Blue: a hero adjacent. Orange: a hero in range and not adjacent. Attack 1. Play only when both geometries have a hero, for the tag, never as a kill.

## 3. Upgrade path

1. **Red first if she is the team's damage.** `ignatia-red-2a` when she must function without predicting the coin: range 3 and a defense item, and both geometries are common. `ignatia-red-2b` when a teammate can fix her position (Hanu's This Way, a swap) or she will play silver early: attack 5 and range 5, at the cost of needing adjacent **or** exactly max range. Default 2a. Tuck initiative from 2b if she is losing ties she wanted, else tuck defense.
2. **Blue second.** `ignatia-blue-2a` / `3a` with Min, or into any token hero, or when the lane is won by removing a minion that sits on a hero. The tier-3 blue face is a defeat, not a discard. `ignatia-blue-2b` / `3b` when the team needs forced movement (Brogan's charge ruined, an ally shoved onto a line). The movement item on 3b adds +1 to her movement icons (printed 1–3). Default 2a into a token team, 2b otherwise.
3. **Green.** `ignatia-green-2a` / `3a` when she is on blue A: the magma is the token that turns the discard on. `ignatia-green-2b` / `3b` when she needs a swap more than a wall. Two defense items from green A plus red A make silver less mandatory. Attack items from green B do little for gold's attack 2; they help the red.
4. **Tier 3 red** follows the tier 2 choice. `ignatia-red-3b` is the card the combo thread calls a very good pusher. Take it when attack 6 will be the round's kill. Take `ignatia-red-3a` when the straight-line repeat on a second hero is the plan and radius items will grow the blues and greens.
5. Silver is not an upgrade. The decision is when to spend it, not whether to own it.

## 4. Synergies and counters

- Min, both on the discard blue [BGG 3487749, 2025-04-05, community]. His grenade is a token. Her blue-blue discards a hero adjacent to a token. On turn 4, `ignatia-blue-3a` defeats that hero. His grenade at end of turn does it again if they lived. She is also the pusher if the combo misses.
- Hanu's `hanu-blue-2b` / `3b` puts her on the line the orange red needs, or adjacent for the blue red.
- Any token ally (Mortimer's zombies, Mrak's rocks, Tali, Wuk) turns blue-blue on without her spending green.
- She does not need a lane partner, but gold attack 2 and "remove" (no coins) on the low blues mean a partner who defeats minions covers the coin gap.

She fears: a table with no ties and a coin stuck on the wrong face, when she did not stand on both geometries. Fast heroes who leave the line before initiative 7. Skill locks (Arien's silver) on the turn she needed the blue. Low defense outside red and silver: gold is 2, green is 2–3, blue is 3–4.

Against her:

- Step off the straight line **and** out of adjacency before she acts, or step onto the line if you know the face is the non-line one. One target that satisfies only one face is how she loses the turn.
- Do not stand adjacent to a token or a minion inside her blue radius if your hand is the card you need, and never do it on turn 4 against `ignatia-blue-3a`.
- Her team's face is what you should expect when she is the one in the tie. Flip the coin with your own tie **before** she acts if you want her on the bad face: a teammate of hers winning a tie flips it away from her; you winning a tie flips it onto her.
- Silver at initiative 1 means she has already spent the defense-7 card by the time anyone attacks. That is the turn to hit her. Official advice says the same thing.
- Equilibrium does not stop you from tying later. It only lets her ignore the coin on her own actions.

## 5. Common mistakes

- Playing a card that has a target on only one face, without a predicted coin.
- Playing silver every round. Defense 7 leaves the hand, and she acts at initiative 1 that turn.
- Assuming gold kills. Attack 2 is a minion card on the blue face and a poke on the orange face.
- Repeating `ignatia-red-3a` on the blue (non-line) face. The repeat is orange only. Repeating `ignatia-red-3b` on the orange face. The minion repeat is blue only.
- Placing magma on the path her own ally needs. The token is an obstacle for everyone.
- Orange green "place up to N" on top of her own minions' spawn points.
- Chaos Incarnate flip when the other face has no target and Equilibrium is down. She spends the flip and the second action does not start. Flip when the other face has a target, or when a later ally needs the other team to win the next tie.
- Using 1st-printing blue text (discard the adjacent hero, move someone 3). The current orange blue is a minion, and the current alt blue is a fixed straight move.

## 6. HEURISTIC RULES

"Her face" means the text of her team's symbol. "The other face" means the opposite. Predict the coin at the moment of her action from the revealed initiative order, as in the procedure at the top. That prediction is HContext once cards are revealed. Before the reveal, score a card as the face she can already guarantee, and if she cannot guarantee one, require both faces to have a target.

1. `PLAY_CARD` [any coin card] WHEN both faces have a legal target from a space she can reach with that card's movement before the action THEN the card is legal to play. WHEN only one face has a target AND the predicted coin is the other face AND Equilibrium is not active THEN do not play it. WEIGHT −15 on the dead face. HContext. SOURCE [ADVICE]; [BGG 2887471] prediction section, which matches p.7.

2. `PLAY_CARD` [any] WHEN she is in a cross-team tie on the card she is about to play THEN treat her face as certain. WEIGHT +6 to score the her-face target, +0 to the other face. SOURCE p.7 as applied in §1; [ADVICE] "team's color".

3. `PLAY_CARD` [`ignatia-silver`] WHEN two or more of her remaining cards each have a target on only one face AND those faces differ AND no enemy attack of 6 or more can reach her after initiative 1 this turn THEN play silver on turn 1. WEIGHT 12. If an attack can reach her and silver is her only defense ≥ 6, do not play it. SOURCE [ADVICE].

4. `CHOOSE_OPTION` [Equilibrium, any primary] WHEN she is asked which face THEN the face whose target is a hero with 0 cards, else a lane-deciding minion, else a discard, else any target. Never a face with no target. WEIGHT the underlying effect (kill 15+, lane minion 15, discard 2.5). hero-state (Equilibrium active). SOURCE rulings/ignatia.md 3.

5. `CHOOSE_UNIT` [`ignatia-red-3b`] WHEN the predicted face is blue (adjacent) AND an enemy hero is adjacent AND an enemy minion is also adjacent THEN attack the hero (attack 6) and repeat on the minion if the repeat is offered. WEIGHT the kill plus 15 if the minion is the lane. WHEN the face is orange THEN the unit at exact maximum range, preferring a hero with no cards. SOURCE rulings/ignatia.md 2; [BGG 3487749].

6. `CHOOSE_UNIT` [`ignatia-red-3a`] WHEN the face is orange AND two heroes are in range on straight lines THEN hit the minion or the first hero, then repeat on a different hero. WEIGHT 8 for the second hero if it is a discard-or-kill setup, else 3. Do not repeat on the blue face. SOURCE rulings/ignatia.md 2.

7. `PLAY_CARD` [`ignatia-blue-3a`] WHEN an enemy hero in radius 4 is adjacent to a token or a minion AND that hero has 1 card or 0 THEN play it if the blue face will apply (predicted, her tie, or Equilibrium). On 0 cards the blue face defeats them. WEIGHT a hero kill. On 1 card, WEIGHT 8 (the card, plus they are empty for a later hit). The orange face defeats the minion instead: take that when the minion is the lane and the hero is not dying. SOURCE card text; [BGG 3487749].

8. `PLAY_CARD` [`ignatia-blue-1`, `ignatia-blue-2a`] WHEN the blue face will apply AND a hero in radius is adjacent to magma, a zombie, a rock, a minion, or Min's grenade AND that hero has a card THEN the discard. WEIGHT 2.5, or 8 on turn 3 if a teammate acts later with a kill. The orange face: remove the minion only when it swings the lane or it is the token-adjacency that protects the hero. Removing a non-key minion for no coins is WEIGHT 2. SOURCE card text; rulings/ignatia.md 7.

9. `CHOOSE_SPACE` [`ignatia-green-1`, `ignatia-green-2a`, `ignatia-green-3a`] WHEN the blue face will apply THEN move in a straight line that places magma on empty spaces adjacent to an enemy hero she wants to discard, without sealing her ally's only path to the lane. WHEN the orange face will apply THEN place the tokens on empty spaces adjacent to that hero, not on spawn points the wave is about to use. WEIGHT 4 per token that newly satisfies "adjacent to a token" for a hero in her blue radius, minus 6 if the token blocks an ally. SOURCE card text.

10. `CHOOSE_UNIT` [`ignatia-green-2b`, `ignatia-green-3b`] WHEN the orange face will apply THEN swap with the enemy unit who is about to take the lane minion, or who is standing out of her red's geometry, preferring a swap that lands her in range. On 3b, the self-move of 1 completes the geometry. WHEN the blue face will apply THEN swap with the ally who needs to be on a line, and on 3b move that ally 1 onto the line. WEIGHT 10. SOURCE card text.

11. `CHOOSE_SPACE` [`ignatia-blue-2b`, `ignatia-blue-3b`] WHEN the orange face will apply THEN move the enemy hero the exact 2 (or 2 or 3) onto a space off their minion, or into her ally's melee, or off a straight line they needed. WHEN the blue face will apply THEN move the ally onto their attack's space. If the only legal move throws an ally out of the fight, do not play the card. WEIGHT 10 for a denied minion, 8 for a delivered ally. SOURCE rulings/ignatia.md 9.

12. `PLAY_CARD` [`ignatia-gold`] WHEN her face is blue AND a minion is adjacent AND that minion is the lane THEN play it. WEIGHT 15. WHEN her face is orange AND a hero is in range 3 AND a teammate's attack acts after her and needs the hero damaged or the hero has no defense that beats attack 2 THEN play it as the poke. WEIGHT 3. If neither target exists for the predicted face, do not play gold. SOURCE card text. Attack is 2; do not price it as a kill.

13. `CONFIRM` [`ignatia-purple-4`] WHEN the other face has a legal target that the first performance did not already hit THEN flip and perform again. WEIGHT the second effect, at half if Equilibrium is down and the flip is the only way to get that face (it is deterministic: the other face). WHEN a later ally needs the coin on the other team to win their tie AND the second action is worthless THEN flip and decline the second action. WEIGHT 4. ELSE decline the flip. SOURCE rulings/ignatia.md 10.

14. `CHOOSE_SPACE` [movement on red or green, and respawn] WHEN ending the move THEN a space that is in straight-line range of one enemy unit and not-in-line range of another, or (on alt red) adjacent to one unit and at exact maximum range of another. WEIGHT 6 versus a space with only one geometry. HContext. SOURCE [ADVICE]; [BGG 2887471] "stand where both texts have a target", the geometry of which changed but the principle matches `ignatia-red-*`.

15. `DEFEND` [any] WHEN silver is in hand AND the attack total is ≤ 7 (her printed 7, plus defense items, plus minion modifiers) AND she does not already have Equilibrium active THEN defend with silver only if the attack would kill her. A non-lethal hit should be answered with red (defense 4–6) so silver remains. WEIGHT of spending silver on a small hit: −8. SOURCE [ADVICE].

16. `UPGRADE_CARD` WHEN Min is an ally or two or more tokens are already on the board THEN upgrade blue before green, and keep the `a` branch. ELSE red, then blue `b` if no tokens, green `a` if she kept blue `a`. WEIGHT 3. SOURCE [BGG 3487749]; [REASONED].

### evalTerms

- Each magma token adjacent to an enemy hero who is inside her current blue radius: +3. A token that only blocks her ally's path to a minion: −4.
- Equilibrium active and she still has at least one coin card in hand: +8.
- Equilibrium active and her hand is empty: +0. The defense card is already gone.
- No other persistent piece. Magma leave at end of round.

### threatAdjust

When the enemy is Ignatia:

- +8 on a hero space that is adjacent to a token or a minion and inside her blue radius (3, then 4), while `ignatia-blue-*` may be in hand. At tier 3 that space is a kill space on the blue face if the hero has 0 or 1 card: +15 instead.
- +5 on spaces at exact range 5 from her while `ignatia-red-2b` or `ignatia-red-3b` may be in hand, and +5 on spaces adjacent to her for the same cards. The gap (range 2–4) is the safe belt against alt red, not against primary red.
- +4 on spaces in her red range that lie on a straight line from her, and +4 on spaces in range that do not, while primary red may be in hand. A space that is both (it cannot be) does not exist; adjacent is always a line.
- Her gold is not a kill threat. +0 beyond a normal attack-2 adjacent or range-3 poke.
- While Equilibrium is active, both of the above apply at once. The safe space is out of radius and out of both geometries.

## 7. Sources

- Catalog `ignatia.json` advice (3 sentences).
- `core/game/rulings/ignatia.md`. Rulebook p.7 tie procedure, via STRATEGY.md §1.6.
- [BGG 2887471] Ignatia spotlight, 2022-06-22, community (null). 1st printing. Kept: predict ties; stand where both texts work; her own tie resolves onto her team's face; silver is the control card and is not for every turn. Rejected: silver sets the coin for two turns; gold attack and targeting; blue-orange discards the adjacent hero; alt blue's "up to 3 / up to 1"; ultimate as eight actions; green T3 as a defense/skill.
- [BGG 3487749] 2025-04-05, community: Min's grenade plus her discard blue, near-certain on turn 4; she is a strong pusher on tier-3 red.
- Gamefound Renowned update 8: polarizing, 4-star, two texts on the coin, "either side works". No numbers taken from it. The campaign silver description is the old card.
- No 2nd-printing spotlight. No Reddit thread found.

## Implementation notes

`IgnatiaHeuristics.kt`, checked against `IgnatiaHero.kt` (2026-11-21).

- **The coin is the prediction.** `IgnatiaHero.coinSide`: the Blue team's side shows the blue text, the Red team's side the orange text. The coin is public, so the face it shows when she plays is the expected face. Rule 1's "require both faces before the reveal" made almost every coin card dead in the draft; the code instead cancels the general strike estimate only when the face the coin shows has no target, and adds a little when both faces do (a tie can still flip it).
- Rule 4: Equilibrium's face choice is a `CHOOSE_OPTION` the engine offers only when both faces can start; it is left to the lookahead. Rule 13 (Chaos Incarnate confirm) likewise.
- Gold's faces: blue is an adjacent minion, orange a hero in range. Primary red: blue is a unit not in a straight line, orange a unit in a straight line. Alt red: blue adjacent, orange exactly at maximum range. Blue A: blue is a hero beside a token or any minion (discard), orange removes an enemy minion beside an enemy hero.
- `evalTerms` counts only magma beside enemy heroes; `HEval` already prices each token.
- Skipped: rules 5, 6, 9–11, 14 (target and space choices the lookahead plays), 16 beyond `upgradeOrder`.
- Measured (ablation, strength 8): 61.5% of 96 with the first dead-face correction; 55.7% of 192 (Wilson 48.7–62.6%) after it kept the card's movement value.
