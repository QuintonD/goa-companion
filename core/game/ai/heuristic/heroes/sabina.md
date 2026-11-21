# Sabina the Commander (`sabina`) — heuristic playbook

2nd printing. Complexity **1**. Sheet: Attack 1–7, Defense 3–3, Initiative 5–5, Movement 5–6. Sheet roles: tactician, pusher. Card values are the combat numbers. Gold Attack is **2**, not the sheet's 7. Dead Shot's conditional total is 3 + 4 = 7 before items; the 9 in §3 is that 7 plus two Attack items.

She is a lane hero who stands with friendly minions. Without them, every skill is a walk (Cryomancy, BGG 3461122, 2025-02-14). Designer, in the 1st-printing spotlight BGG 2889623: top three lane pushers, and the best at winning the **second** push. The 2nd-printing kit still moves, swaps and deletes minions, so that job still fits. A 2nd-printing aside that alt red "removes two minions at tier II" (BGG 3481942, as quoted in STRATEGY.md) does **not** match the card: `sabina-red-2b` removes up to **one**, `sabina-red-3b` up to **two**. Counting the defeated target plus the one removal is the only way tier II removes two bodies, and the removal pays no coins.

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `Pusher` |
| `idealDistance` | `2` (red range). Gold is range 1 and pushes only if the target is adjacent. |
| `aggression` | `0.50` |
| `laneWeight` | `0.75` |
| `keepDefender` | true — the red (Defense 6 at tier 1–2, 7 at tier 3). Every other card is Defense 2–4. |
| `upgradeOrder` | red first, and the red **is** the win condition (§3) |

## 2. Card-by-card

| Id | Init | For | Play when | Do not | Hold |
|---|---|---|---|---|---|
| `sabina-gold` Point Blank Shot | 12 | Ranged, Range 1, Attack 2. If the target is still adjacent after the attack, push it 1. A defeated target is not pushed. | A melee hero has stepped next to her or next to a minion, and init 12 means she pushes them off before their slower attack. A minion that is adjacent. With a Range item the shot reaches 2, but the push still requires adjacency. | A hero kill on Attack 2 unless items and a modifier already beat every card they hold. A ranged attack she hoped was a push at range 2. | Defense 2. The threat of the push is worth more in hand than as a block ([ADVICE]: a counter to non-ranged attacks). |
| `sabina-handicap` Bang | 11 | Adjacent hero, Attack 1, then push 1 if they are still there. | A push, not a kill. | As a block (Defense 1). | None. |
| `sabina-silver` Back to Back | 8 | Swap with a friendly minion in radius 2. Not a hero. Not an immune heavy (`rulings/sabina.md` 7; designer, Discord 2026-04-05). | A friendly minion in radius 2 is on the wrong side of an enemy who has just committed a slow move, and the swap puts that minion out of their remaining movement, or puts her on a minion she wants. Also a plain reposition when the landing space is safer than walking. | Face-blocking a slow red by swapping onto the minion they were going to hit. Defense 2 dies, and the enemy gets coins instead of a minion (BGG 3461122 comments, 2025-07-05). | Defense 2. Niche. Not every round. |
| `sabina-red-1` Quickdraw | 8 | Ranged, Range 2, Attack 2, **+3** if the target **played an attack card this turn**. Minions never did. The bonus remembers a card later discarded or swapped (catalog FAQ). A card returned to hand is no longer "played" (designer, Discord 2025-08-15). Gydion's spells are cast, not played. | A hero in range 2 has revealed an attack card. Attack 5 before items. | A hero who revealed a skill, or a minion you needed the bonus for. Calling it on a guess when they can still be holding the attack is the point of the **threat**, but the card should be played when the reveal already shows the attack or when their possible pool is almost all attacks. | Defense 6. This is the block. |
| `sabina-red-2a` Gunslinger | 9 | Attack 3, +3 on the same condition. Range 2. | Same. Attack 6 before items. Init 9, not 11. | — | Defense 6. |
| `sabina-red-3a` Dead Shot | 9 | Attack 3, **+4** on the same condition. Range 2. | Same. Attack 7 before items. | — | Defense 7. |
| `sabina-red-2b` Shootout | 9 | Attack 3, Range 2. If the target **was adjacent**, remove up to one enemy minion adjacent to her. No coins for the remove. Heavy immunity applies. The attack must connect. | She is adjacent to the target and to an enemy minion, and deleting that minion swings the lane or clears the −1 modifier before a later attack. Init is **9**. A guide line that says 11 is wrong. | A non-adjacent target when the whole point was the remove (the remove does not happen). Standing in a ring of enemy melee minions and shooting before she can clear them, into an attack that resolves first. | Defense 6. |
| `sabina-red-3b` Bullet Hell | 9 | Same, remove up to **two**. One at a time, so a siege's last minions can fall in sequence (designer, Discord 2025-06-10: sequential "up to" can, simultaneous cannot). | Same, when two enemy minions are adjacent. The third body is the defeated target, which does pay coins if it is a minion. Cryomancy: do not expect the third body most rounds. | — | Defense 7. |
| `sabina-green-1` / `2a` / `3a` Troop Movement / Marching Orders / Path to Victory | 4 / 3 / 3 | Move a friendly minion in radius 1 space, to a space still in radius. Repeat once / once / up to two. Same minion allowed. Radius 2 / 3 / 3. | Late in the turn, after enemies have stepped. Pull a friendly minion out of their reach, or park one next to an enemy to flip a modifier, or set two friendlies in radius for the alt blue. | Early in the turn, before they move, unless the minion is already somewhere fatal. | Defense 2–3. |
| `sabina-green-2b` Close Support | 3 | An enemy hero in radius 3 who is adjacent to a friendly minion discards. | That hero has revealed a card that leaves them there, or they have already resolved beside the minion. | No such hero: the primary is not offered. It does not save the minion by itself. | Defense 3. |
| `sabina-green-3b` Covering Fire | 3 | Same, discard **or be defeated**. | A hero beside a friendly minion, in radius 3, with no card that can be discarded, or whose discard you want more than the minion. | A hero who can discard a spare and walk; you spent a card for their silver. | Defense 3. |
| `sabina-blue-1` / `2a` Listen Up / Roger Roger | 9 / 10 | Swap two minions in radius 1 / 2. Friend or foe. Heavy immunity applies. | Swap a friendly minion with an enemy minion: their minion becomes your farm, yours leaves their attack, and a modifier of +1 becomes −1. | A swap that only shuffles two friendlies when the lane did not need it. Immune heavies. | Defense 3 / 4. |
| `sabina-blue-3a` Ready and Waiting | 10 | Same swap, **ignoring heavy immunity**. | The heavy is the piece to move. Radius 2 is still short; a Radius item (from tucking `sabina-red-3b`) is what makes this the heavy tool. | — | Defense 4, and the tucked partner is an Attack item if you kept this and tucked `sabina-blue-3b`. |
| `sabina-blue-2b` Steady Advance | 10 | If two or more friendly minions are in radius 2 (presence, immune heavies count — designer, Discord 2025-08-14), you may retrieve; if you do, you may move 1. | Red is in the discard and two friendlies are in radius. The step is the exit. | One friendly minion. Retrieving when you did not need the card and the step lands you in a worse space. | Defense 4. This branch exists so a lost red comes back. |
| `sabina-blue-3b` Unwavering Resolve | 10 | Same, then move up to 2 after the retrieve. Radius 2. | Same, when the two spaces matter. | — | Defense 4. |
| `sabina-purple-4` Big Sodding Gun | — | Basic attack (gold, or Bang) gets +2 Range and +2 Attack. Bang has no Range icon, so only the Attack applies to it. Whenever she pushes an enemy hero, that hero discards or is defeated, even on a push of 0 (`rulings/sabina.md` 9). | Gold becomes a real finisher (Attack 4 before items, Range 3 before items) and every push is a strip. | — | — |

## 3. Upgrade path

Cryomancy's two builds (BGG 3497427, 2025-04-16) match the printed items. "Primary" in that guide means the a-branch.

**Dead Shot (kill threat).** Keep `sabina-red-2a`, `sabina-blue-2b`, `sabina-green-2a`. Tuck `sabina-red-2b` (Defense), `sabina-blue-2a` (Attack), `sabina-green-2b` (Initiative). Then keep `sabina-green-3b` first: tucking `sabina-green-3a` gives the **Range** item, so red reaches 3 and gold reaches 2. Then `sabina-blue-3b` (tuck `sabina-blue-3a`, Attack item) and `sabina-red-3a` (tuck `sabina-red-3b`, Radius item). Dead Shot's conditional total is 3 + 4 + two Attack items = **9** before minion modifiers. That 9 is the guide's number and it is this arithmetic, not a printed 9.

If the lane is the only win left, the guide's own alternate is to switch the red to `sabina-red-3b` instead of Dead Shot.

**Bullet Hell (deny the wave).** Keep `sabina-green-2a`, `sabina-blue-2a`, `sabina-red-2b`. Tuck Close Support (Initiative), Steady Advance (Defense), Gunslinger (Initiative). Then `sabina-red-3b` (tuck Dead Shot, Movement item) and `sabina-green-3a` (tuck Covering Fire, Initiative). Blue tier 3 is a choice: `sabina-blue-3b` for the Attack item and the heal-step, or `sabina-blue-3a` if you swapped onto the a-red and want the heavy swap. Cryomancy prefers the heal-step.

**Which red.** [ADVICE]: the red decides the win condition. A-branch threatens heroes out of their attack cards and punishes them when they play one. B-branch deletes minions and needs green-a and blue swaps to be standing in the right ring. Pick from the team: if an ally already pushes, take Dead Shot; if the team is short a pusher, take Bullet Hell. Do not change at tier 3 unless the win condition changed (the guide's "alternate tier III").

**Phase.** First upgrade is the red of that plan, or Marching Orders if the player cannot yet use minions and still wants the lane build's Initiative item. Then the blue that retrieves (kill plan) or the blue that swaps (lane plan). Green tier 3 early in the kill plan because it is the Range item.

## 4. Synergies and counters

**With:** friendly minions, always. An ally who finishes a hero she has stripped with Covering Fire or scared off a minion. She should not take the heavy herself in the opening unless her gold is the slowest gold on the team (Cryomancy, BGG 3497427). She is bad at twins: low Defense on everything but red, so enemy melee next to both twins is a −2 she does not want to sit in.

**Fears:** the end of a wave, when no friendly minion is left and every skill is blank (Cryomancy, BGG 3461122). Being pulled off the minion line. A melee hero who stands next to her with an obstacle behind them, so the gold push has nowhere to go, and then attacks. Losing the red: Defense falls to 2–4.

**Against her:**

- Fight her between waves, or drag her away from friendly minions.
- Her red is the card worth forcing. It is the attack, the block, and (on the a-branch) the only big number.
- Gold in hand means a melee step onto her is a push at init 12. Do it with an obstacle at your back, or with a faster attack that does not need to stay adjacent.
- A-red only jumps when you **play an attack card**. A skill, a movement, or a held attack does not give her the +3 / +4. After the reveal she knows. Gydion casting a spell does not count.
- B-red's extra minions are removes. You do not pay her coins for them, but you can lose the lane. Do not stand in a cluster of your own minions adjacent to her if `sabina-red-3b` is in the deck.
- Heavy immunity blocks `sabina-blue-2a`. It does not block `sabina-blue-3a`, and it does not stop Steady Advance from counting the heavy as a body.

## 5. Common mistakes

- Shooting Quickdraw at a minion and expecting +3.
- Treating Shootout's initiative as 11. It is 9. Gold is the init-12 push.
- Swapping onto a minion to body-block a red.
- Taking twins in the open with Defense 2 cards.
- Playing green before the enemies move, so they walk to the minion's new space.
- Using gold on a hero at range 2 (Range item) and expecting the push.
- Removing minions with b-red and counting the coins. The reminder text says there are none.
- Upgrading both reds' jobs at once. The branch is the plan.

## 6. Heuristic rules

`evalTerms`: +2 for each friendly minion in radius 2 while she is on the board (her skills are on), capped at +6. +4 extra while two or more friendlies are in radius and she holds `sabina-blue-2b` or `sabina-blue-3b` with a card in the discard. 0 for enemy minions except through the lane term. A friendly minion she can reach with green is not worth more than the lane already says unless an enemy attack is about to take it this turn, in which case +5 if green is still unplayed.

`threatAdjust` (she is the enemy): a hero standing on a friendly minion of hers, inside her green radius (2 at tier 1, 3 later), is in discard range even though she is not in attack range: +3 while `sabina-green-2b` may be unplayed, +the kill while `sabina-green-3b` may be unplayed and that hero's hand is empty. Her attack threat is range 2 (range 3 with a Range item), not her Movement. Gold adds a push only on the adjacent hex, and only if the push's destination exists; a space with no push-exit is **not** safer to stand in if she still holds red.

1. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`sabina-red-1`, `sabina-red-2a`, `sabina-red-3a`] WHEN the target is a hero who has played an attack card this turn (revealed, resolved, or later discarded or swapped — not returned to hand) AND the engine total with the +3 or +4 meets a card they can still be holding, or meets all of them THEN prefer that target over a minion. WEIGHT the card they spend (2.5, or 6 if it is their defender) or the kill. SOURCE catalog FAQ; Cryomancy, BGG 3461122.
2. `CTX general` [PLAY_CARD] [`sabina-red-1`, `sabina-red-2a`, `sabina-red-3a`] WHEN no hero in range has played an attack card AND a minion is in range AND the lane is within one minion THEN prefer the minion only if no rule-1 target exists. The printed Attack without the bonus is 2 or 3. WEIGHT the minion (5, or 15 if it is the margin). Do not spend red on a minion when a revealed attack-card hero is in range. SOURCE [REASONED] from the bonus text.
3. `CTX general` [CHOOSE_UNIT, CHOOSE_UNIT] [`sabina-red-2b`, `sabina-red-3b`] WHEN she is adjacent to the target AND at least one enemy minion is adjacent to her THEN take the shot and remove the minion that most changes the lane count; on 3b remove a second if it exists. Prefer a target that is itself an enemy minion when the coins plus the removes beat a hero-only shot. WEIGHT 15 per removed minion when the lane is even, else 3 (no coins). SOURCE card text; Cryomancy, BGG 3461122 and BGG 3497427.
4. `CTX general` [PLAY_CARD] [`sabina-gold`] WHEN an enemy unit is adjacent AND either it is a minion, or it is a hero whose next action is a non-ranged attack that the push moves out of range THEN prefer gold. WEIGHT 6 for the denied attack, or the minion. SOURCE [ADVICE]; Cryomancy, BGG 3461122.
5. `CTX general` [CHOOSE_SPACE] [`sabina-gold`] WHEN pushing THEN prefer the space that leaves them out of their attack range or onto a worse modifier. If every direction is blocked, the push of 0 still counts once the Ultimate is on (they discard or die) but does not move them. WEIGHT 4, or the strip. SOURCE `rulings/sabina.md` 9.
6. `CTX general` [CHOOSE_UNIT, CHOOSE_SPACE] [`sabina-green-1`, `sabina-green-2a`, `sabina-green-3a`] WHEN an enemy hero has already resolved a move toward a friendly minion in radius AND one step puts that minion outside their remaining reach THEN move it. ELSE if an ally is about to attack a hero, move a friendly minion adjacent to that hero. WEIGHT 5 for the denied farm, 4 for the modifier. SOURCE Cryomancy, BGG 3461122 (green is the late card, init 3–4).
7. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`sabina-green-2b`, `sabina-green-3b`] WHEN an enemy hero is in radius, adjacent to a friendly minion, AND (their revealed card keeps them there OR they have already resolved) THEN prefer the primary. On 3b, if their hand is empty, this is a kill. WEIGHT 2.5 or the kill. SOURCE card text; Cryomancy.
8. `CTX general` [CHOOSE_UNIT] [`sabina-blue-1`, `sabina-blue-2a`, `sabina-blue-3a`] WHEN a friendly minion and an enemy minion are both in radius THEN swap those two if the enemy minion becomes reachable by an ally and the friendly one leaves an enemy's range, or if the swap flips a modifier on a hero fight by 2. On 3a the heavy is a legal enemy minion. WEIGHT 5 plus 4 for a modifier. SOURCE Cryomancy, BGG 3461122.
9. `CTX general` [PLAY_CARD, CONFIRM] [`sabina-blue-2b`, `sabina-blue-3b`] WHEN two or more friendly minions are in radius (presence) AND a card in the discard is her red or another card she will play this round AND the optional step moves her out of an unresolved attack or toward a target THEN retrieve and take the step. WEIGHT 2.5 plus 4 for the step. SOURCE card text; Cryomancy, BGG 3497427 (the heal is why the kill-build takes this blue).
10. `CTX general` [CHOOSE_UNIT] [`sabina-silver`] WHEN a friendly minion in radius 2 is outside an enemy's remaining movement and she wants that minion's space, or the minion is inside an enemy's remaining movement and the swap puts it out THEN swap. WHEN the swap lands her on the space of a slow red she cannot block THEN avoid. WEIGHT 4, or −8 for the bad face-block. SOURCE Cryomancy, BGG 3461122, and the 2025-07-05 comments on that thread.
11. `CTX general` [CHOOSE_SPACE] [any movement or Fast Travel] WHEN a wave push is about to change the Battle Zone AND she would be left outside it THEN prefer the space on the side the wave is going, next to a friendly minion. WEIGHT 6. SOURCE Cryomancy, BGG 3461122 ("don't get caught out of position when it pushes"); [ADVICE].
12. `CTX general` [DEFEND] [any] WHEN red's Defense meets the attack AND no cheaper card does THEN spend red only if gold or alt blue can get it back, or the attack kills her otherwise. A Defense 2 card that meets a small attack is the one to spend. WEIGHT 6 for keeping red. SOURCE Cryomancy, BGG 3461122 (red is the versatile card; without it she is careful).
13. `CTX general` [PLAY_CARD] [`sabina-red-2b`, `sabina-red-3b`] WHEN she is adjacent to two or more enemy melee minions AND an enemy attack that beats Defense 6 − (those minions) resolves after her THEN she should still shoot if the removes happen before that attack and clear the modifier; else do not walk into that ring. WEIGHT 4. SOURCE Cryomancy, BGG 3461122.
14. `CTX general` [UPGRADE_CARD, KEEP_UPGRADE] [red] WHEN the team is behind on the lane and ahead on lives THEN prefer the b-red (`sabina-red-2b` / `3b`). WHEN the team is ahead on the lane or an ally is the pusher THEN prefer the a-red. WEIGHT the plan, not a coin. SOURCE [ADVICE]; Cryomancy, BGG 3497427.
15. `CTX general` [PLAY_CARD] [any] WHEN no friendly minion is in radius 2 AND the card's primary needs one (`sabina-silver`, the greens, the blues) THEN do not play it for the primary. Play red or gold, or move toward a friendly minion. WEIGHT 4. SOURCE Cryomancy, BGG 3461122; [ADVICE].

Opening sketch from BGG 3497427, as preferences rather than a script: turn-1 silver onto a minion, then a second minion, and leave the heavy to the slower gold. A red threat at the twins is for a hero whose blue Defense is 3. Do not open by taking both twins unless she has snuck behind them.

## 7. Sources

- Catalog `sabina.json`.
- `core/game/rulings/sabina.md`, `heroes/SabinaHero.kt`.
- Cryomancy, Sabina spotlight, BGG 3461122, 2025-02-14, and the July 2025 comments. https://boardgamegeek.com/thread/3461122
- Cryomancy, "Mad_Mike Builds the Gunslinger," BGG 3497427, 2025-04-16. https://boardgamegeek.com/thread/3497427
- Designer, BGG 2889623 (1st printing) on lane priority — the job still matches; the old silver (Defense from minions, a block) does **not**. Do not play for that silver.
- Designer Arty, Discord 2025-04-19, 2025-06-10, 2025-08-14, 2025-08-15, 2026-04-05, via https://www.cockeyedandcurious.com/goa2/faq
- BGG 3481942 is not re-quoted beyond STRATEGY.md's line, which loses to the printed remove counts.
- No Reddit or YouTube guide found.

## 8. Implementation notes

`SabinaHeuristics.kt` (ADR-085). Checked against `SabinaHero.kt`:

- **H1:** the engine gives the +3/+4 when the target's body is in `turnRecord.playedAttack`, which is filled only after the reveal and so is public. The note adds the engine-priced difference `heroStrikeWorth(total + bonus) − heroStrikeWorth(total)`, so the bonus counts only where it changes a kill or a strip.
- **H15:** blue-1/2a/3a "Swap two minions" does not need a *friendly* minion, so only silver and the greens get the "no friendly minion near" penalty.
- **threatAdjust:** the draft fired only when a hero was already standing on the space. Exposure is asked for spaces a hero might move to, so it now fires for any space next to her minion, within radius 3, while a Close Support or Covering Fire card is still in her possible pool.
- **Not implemented:** H2, H3, H5, H6, H8–H11, H13 (minion moves, swaps, removals). These are opaque or per-target choices the look-ahead answers.
- **Ablation** (strength 8, `BatchCAblationTest`, the same line-ups and seeds as `HeroAblationTest`): 96 games 53.1% (51–45), Wilson 43.2–62.8%; 384 games 49.5% (190–194), Wilson 44.5–54.5%.
