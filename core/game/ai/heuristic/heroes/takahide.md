# Takahide the Warlord (`takahide`) — heuristic playbook

2nd printing. Complexity **4**. Sheet: Attack 5–7, Defense 4–7, Initiative 3–5, Movement 1–4. Sheet roles: durable, sniper, tactician, damager, farming. Those ranges describe the three golds, not a single card. He has **no FAQ** in the catalog.

He starts with `takahide-gold` in hand and the other two golds in the deck. Only one gold is in hand until `takahide-purple-4`. Most skills need an **ally** with a discard, or an ally willing to make one. His own discard does not trigger them (CCplusplus, BGG 3451226, 2025-01-30).

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `Support` |
| `idealDistance` | `1` while `takahide-gold-extra-2` is the gold he will attack with; `3` while `takahide-gold-extra-1` is that gold; otherwise he wants allies at 3–4 and enemies not adjacent on turn 4 |
| `aggression` | `0.45` |
| `laneWeight` | `0.35` (no minion-clear besides the two attack golds; CCplusplus: wave control is the weak part) |
| `keepDefender` | true — `takahide-gold` is Defense **8**. `takahide-silver` is Defense 6. The reds are Defense 1–2. Never `takahide-handicap`. |
| `upgradeOrder` | the red that matches the team, then green for mobility or coins, then blue |

The spotlight's "one-shot him by beating the silver" is incomplete. Float is Defense 8, silver is 6, Strike is 3, Sting is 1, reds are 1–2. Card text wins. He dies to card-kills and empty hands, not to a single big swing, because he is slow (Movement 1–4 outside Float and the green) and cannot leave on turn 4 (CCplusplus).

## 2. Card-by-card

Swapping a gold exchanges all states. The deck gold takes the slot (faceup once revealed) and the old gold goes to the deck (`rulings/takahide.md` 2). Using a gold as Movement, Fast Travel or Defense does **not** swap. That is how he gets stuck on the wrong gold (CCplusplus).

| Id | Init | For | Play when | Do not | Hold |
|---|---|---|---|---|---|
| `takahide-gold` Float like a Butterfly | 8 | **Primary:** swap with a different deck gold (he chooses), **then** move 5. The move is still this action (`rulings/takahide.md` 3). Defense 8. Movement action without the primary: move 5, **no swap**. | He needs to cross the map and he wants Sting or Strike in hand **next round** (the swapped-in gold is the card that comes back). Or he is happy to give up Float for the round. | As the block, if he still needs the swap later — defending does not swap, but it does spend the Defense-8 card. When no other gold is in the deck (after the Ultimate): the primary is not offered. Fast Travel and Defense remain. | The defender. Defense 8. [ADVICE] and STRATEGY.md §5: this, not the red, is the card to keep. |
| `takahide-gold-extra-1` Sting like a Bee | 7 | Attack 5, **exact** range 3. Then swap. Defense 1, Movement 1. | A unit on the ring, and the gold he swaps to is the one he wants next (usually Float, so the defender is back). | A unit at range 1 or 2. As a block (Defense 1) unless it is that or death. As a walk: he keeps Sting and does not get Float back. | Terrible defender. Get it out via the primary or hold it for the ring. |
| `takahide-gold-extra-2` Strike like a Tiger | 9 | Attack 7, adjacent. Then swap. Defense 3, Movement 3. | An adjacent hero the 7 meets, or an adjacent minion, then swap back to Float or over to Sting. | A non-adjacent target. Defending with it (Defense 3) if Float is available instead. | Defense 3. A middling block and his biggest Attack. |
| `takahide-silver` Bushido | 5 | Swap the gold that is out of the deck (hand, resolved, or discard) with a different deck gold. If the gold was resolved or discarded, the new one arrives **facedown**. A facedown gold is not a gold until it is in hand again, so a second Bushido can fail (`rulings/takahide.md` 7). A swap of the gold in hand names neither card. | He is holding the wrong gold and can spend Defense 6 to fix the next turn. The bluff is which of the **other two** arrived. He cannot swap a card with itself (designer, Discord 2026-06-16): to keep the same gold he Holds. | When the gold he wants is already in hand. When the only out-of-deck gold is facedown. As a casual block: he loses the tool that fixes the cycle. | Defense 6. Real, and expensive. |
| `takahide-handicap` Shameful Display | — | A Block with no condition. The parenthetical "you are defeated" is what happens. It does not cancel the kill rewards (designer, Discord 2025-03-06). | Never. | Any time it is offered, including as the only card. Decline and be defeated by the attack rather than "defending" into the same defeat if declining is identical — it is the same result. Never choose it when another card exists. | None. If the Ultimate fires, this is the silver that returns to the deck (`rulings/takahide.md` 10). |
| `takahide-red-1` / `2a` Set an Example / Lead from the Front | 11 / 11 | Attack 2 / 3, adjacent. After the attack connects, an ally in radius 3 / 4 may swap their **unresolved** card with a card in hand. The ally decides. The new card acts at its own init; if it is higher, that ally goes next, no full re-sort (designer, Discord 2026-07-10). A swapped-in attack counts as a played attack (Sabina). | He is adjacent to a minion (the attack has to connect) and an ally who has not acted is in radius, with a better card in hand than the one they revealed. Init 11 means the reveal has happened. | No ally with an unresolved card. Using it as the defender (Defense 1–2). | Defense 1 / 2. Not the spare. |
| `takahide-red-3a` Hold my Saké | 12 | Same, radius 4, Attack 3, and the ally may swap with hand **or discard**. | Same, plus an ally whose discarded card is the one the reveal now wants. | — | Defense 2. |
| `takahide-red-2b` Spinning Blade | 11 | Attack 3, adjacent. This turn, empty spaces adjacent to him are obstacles for enemy units. They can be passed by ignore-obstacles and still cannot be **ended** on (designer, Discord 2026-10-20). Not for tokens. He counts as adjacent to 3+ obstacles for Brynn (designer, Discord 2026-06-16). | Init 11, so slower heroes have not moved. He is in a gap they need, or a hero would otherwise step adjacent for a turn-4 kill. | When Brynn is the enemy and the obstacle ring is a gift to her. When the attack cannot connect (no rider). | Defense 2. |
| `takahide-red-3b` Blade Helix | 12 | Same, and the ring is radius 1 (Radius items widen it). | Same, one hex thicker. | — | Defense 2. |
| `takahide-green-1` / `2a` / `3a` Come to Aid / Bring the Relief / Commit Reserves | 4 / 3 / 3 | An ally in range 3 / 4 / 4 may discard. If that ally **has a card in the discard** (already, or because they just did), he may move up to 3 / 4 / 4, ignoring obstacles at tier 3. The ally declining does **not** cancel the move when a discard was already there (`rulings/takahide.md` 16). The spotlight's "if they say no, you Hold" is wrong in that case: the card is already played, and the move still happens. If their discard is empty and they decline, there is no move. | An ally in range already has a discard, or has a spare they can spend, and the move reaches a Sting ring or a Strike adjacency. | No ally in range: the primary cannot start. An ally with an empty discard who will not spend. | Defense 3–4. |
| `takahide-green-2b` / `3b` Pledge of Allegiance / Loyal Retainer | 3 / 3 | Ally in range 4 may discard. If they have a discard, **both** gain 1 / 2 coins and he may retrieve. | The ally can afford it (already discarded, or a heal is coming) and the coins complete a level. He is not giving up a chase he needed. | An ally whose discard gets them killed. Two coins do not pay a hero. | Defense 4. |
| `takahide-blue-1` / `2a` Proven Warrior / Chosen Champion | 9 / 10 | Choose a **faceup** card in an ally's discard in radius 3 / 4 (not his own). An enemy in radius discards that colour, if they have it in hand. | The ally's discard shows a colour the enemy still holds in hand (not already played or resolved). | No faceup ally discard, or no enemy in radius: it does not start. Naming a colour the enemy has already played (Tali's Ice Blast ruling, BGG 3521577, is the same discard-from-hand rule). | Defense 4. |
| `takahide-blue-3a` The Right Hand | 10 | Same, up to **two** different enemy heroes. One hero once, even if they are Razzle (`rulings/takahide.md` 19). | Two enemies in radius both hold the colour. | — | Defense 5. |
| `takahide-blue-2b` / `3b` Calculated Risk / Tactical Gambit | 10 | An ally in radius 4 may discard an **attack** card. If they have a discard, they may move up to 2, ignoring obstacles at tier 3. | The move puts that ally's remaining attack on a target they could not reach, and the discarded attack was not the only one they needed. CCplusplus's picture is Brogan getting two extra spaces before a charge. | An ally with one attack card and no kill at the end of the move. | Defense 4 / 5. |
| `takahide-purple-4` Ready for War | — | Silver returns to the deck; both deck golds come to hand. Hand size 6. No gold left to swap with. Sting and Strike's after-attack swap fizzles harmlessly. Float's primary is not offered. | He now holds the gold he wants and keeps Float as the Defense 8. Play Sting or Strike for the attack and do not expect a cycle. | Trying to Bushido. Silver is in the deck. | — |

Ally choices (the swap, the discard, the move) are that ally's decision, asked with Takahide's `sourceCardId`. The rules below say what Takahide should set up **and** what the ally should answer when the decision is theirs. Hook those on `sourceCardId`, not only on the ally's hero id.

## 3. Upgrade path

**Red.** Keep `takahide-red-2a` / `takahide-red-3a` when an ally has real cards to swap in (a hero with a defensive card and an attack, who guesses wrong). Tuck `takahide-red-2b` (Defense — useful, his reds are tissue) and `takahide-red-3b` (Range). CCplusplus: this is the card that sees the reveal and changes the ally's mind. He must be adjacent to his own target, so he stands in the wave to stay in radius.

Keep `takahide-red-2b` / `3b` when the enemy is melee and turn-4 approaches are the way he dies. Tuck `takahide-red-2a` (Initiative) and `takahide-red-3a` (Movement). The ring is the peel his Movement stat cannot do.

**Green.** Keep `takahide-green-2a` / `3a` when he is stuck out of range (the usual problem, Movement 1–4). Tuck `takahide-green-2b` (Defense) and `takahide-green-3b` (Defense). A 4-space ignore-obstacle move is his chase.

Keep `takahide-green-2b` / `3b` when the team is winning the wave and wants levels, and an ally can eat the discard. Tuck `takahide-green-2a` (Attack) and `takahide-green-3a` (Radius). He stands still for this. Do not take it from behind.

**Blue.** Keep `takahide-blue-2a` / `3a` when allies are already being hit, so a faceup discard exists to name. Tuck `takahide-blue-2b` (Initiative) and `takahide-blue-3b` (Initiative). Spread the same colour onto one enemy, then two.

Keep `takahide-blue-2b` / `3b` only with an ally whose attack is the game (Brogan, Rowenna, a shotgun Swift) and who can spare a different attack card. Tuck `takahide-blue-2a` (Attack) and `takahide-blue-3a` (Attack). Attack items sit on Sting (5) and Strike (7).

**Phase.** Red first if an ally is in radius every fight; green first if he cannot reach the fight. Blue third. Do not upgrade for a gold; the cycle is Bushido and the primary swap, not a level.

**Cycle, [REASONED] from the spotlight's "have the right gold" advice.** Round he must move: Float primary, swap to the attack he wants next round, walk 5. Round he must hit a ring: Sting primary, swap to Float so the defender returns. Round he is adjacent: Strike primary, swap to Float. If the gold in hand is already the one this round needs, do **not** play its primary until the attack (Sting, Strike) or the move (Float); use other cards, and Bushido only to fix a mismatch. Turn 4: do not be adjacent unless Strike's 7 kills them or Float is still in hand to block. He will not get a second move.

## 4. Synergies and counters

**With:** allies who stay in range 3–4 and who can afford a discard. Healers (Rowenna's retrieve, Silverarrow's Fae Healing, Tali's spirit) make Pledge and Come to Aid safe. An ally with a flexible hand makes Set an Example the best card in the deck. He tells them the radius; they have to stand in it (CCplusplus).

**Fears:** discards aimed at **him** (his skills do not turn on from his own discard). A turn 4 adjacency while Float is in the deck. Getting stuck on Sting (Defense 1, Movement 1) because he walked with it. Teams that never discard, so every green and blue is blank. Brynn, if he is on the blade ring.

**Against him:**

- Check whether Float or Bushido is still in hand. Defense 8 and Defense 6 are the cards that stop a one-shot. Sting does not. Reds do not.
- Discard him rather than his ally if you want his skills off. Hitting the ally **turns the skills on**.
- He cannot swap to the same gold to bluff. After Bushido, the new gold is one of the other two. A facedown gold (from a resolved or discarded swap) is hidden; a hand swap is also hidden; a primary swap is logged (both names).
- Spinning Blade / Blade Helix: do not try to end adjacent. Ignore-obstacles walks through and still cannot stop on the ring. The effect is this turn only, from his current space.
- His attacks are Sting's ring and Strike's adjacency. Float does not threaten damage. A round where you saw him move 5 on Float, he is not attacking with a gold until the card he swapped in comes back.
- Turn 4, he is slow. Be adjacent only if you can finish the card-kill. If he still holds a red at init 11–12, your approach may be the one the ring stops.

## 5. Common mistakes

- Keeping the red as the defender because "reds are the Defense cards." His are not.
- Defending with Shameful Display.
- Walking or blocking with Sting or Strike and then having the same gold next round, with Bushido already spent.
- Playing Come to Aid at an ally with an empty discard who will refuse. The card is spent and he does not move. (If they already have a discard, their refusal does not matter.)
- Playing Proven Warrior on a colour the enemy has already revealed.
- Set an Example with no ally in radius, or without an adjacent target so the rider never starts.
- Standing next to Brynn on the blade turn.
- After the Ultimate, trying to primary Float. It will not be offered.
- Treating Bushido as able to "refresh" the same gold.

## 6. Heuristic rules

`CTX hero` is which gold is in hand, in the deck, resolved, discarded, or facedown.

`evalTerms`: +5 while Float is in hand (Defense 8 still available). +3 while the in-hand gold matches the target that exists now (Sting and a unit at exact range 3, or Strike and an adjacent legal target). −4 while Sting is the only gold in hand and no unit is at exact range 3 (Defense 1 and no shot). +2 while an ally is inside his current red radius and that ally has an unresolved card. A facedown gold is not given a fake identity; at strengths below 10 it is one of the two golds not otherwise accounted for, weighted by the public cycle, never by peeking.

`threatAdjust` (he is the enemy): Sting threatens only the exact range-3 ring, not the disk (same correction as Silverarrow's gold: −4 inside the ring so a "within 3" smear is not a shot). Strike is adjacent Attack 7, printed. Spinning Blade / Blade Helix add a movement ban on the empty adjacent (or radius-1) spaces for the rest of the turn: those spaces are not attack range, they are a wall, worth +6 to avoid stepping onto if he has played that red and you have not moved. Set an Example does not add damage; it adds the ally's threat at a new initiative. While his red may be unplayed and an ally of his has not acted, add +3 to spaces that ally could threaten if they swapped in their other card.

1. `CTX hero` [DEFEND] [`takahide-handicap`] WHEN it is an option THEN never choose it. WEIGHT the kill (choosing it is the defeat). SOURCE designer Discord 2025-03-06; card text.
2. `CTX hero` [DEFEND] [`takahide-gold`] WHEN Defense 8 meets the attack THEN prefer Float over silver, red, Sting and Strike. Spend a red (Defense 1–2) only if it meets the number, which it rarely will. WEIGHT 6 for keeping Float when a cheaper card meets it. SOURCE printed Defense; STRATEGY.md §5; against CCplusplus's "beat the silver," which understates Float.
3. `CTX hero` [PLAY_CARD, CHOOSE_CARD] [`takahide-gold`] WHEN he needs a space 3–5 hexes away AND the gold he wants next round is in the deck THEN primary, choose that gold, take the 5-move. WHEN Float is the gold he needs **next** as well THEN do not primary; Movement action keeps it, or another card moves him. WEIGHT 4. SOURCE `rulings/takahide.md` 3; CCplusplus (stuck when the secondary spends the card).
4. `CTX hero` [CHOOSE_UNIT, CHOOSE_CARD] [`takahide-gold-extra-1`] WHEN a unit is at exact range 3 AND Attack 5 + items meets the target or the target is a minion THEN shoot, and swap to Float unless Strike is the next round's geometry. WEIGHT the shot, plus 5 for arriving with Float next round. SOURCE card text; [ADVICE].
5. `CTX hero` [CHOOSE_UNIT, CHOOSE_CARD] [`takahide-gold-extra-2`] WHEN an adjacent hero loses to Attack 7 + items, or an adjacent minion is the lane THEN strike, and swap to Float. WEIGHT the kill or the minion. SOURCE card text.
6. `CTX hero` [PLAY_CARD, CHOOSE_CARD] [`takahide-silver`] WHEN the in-hand gold is not the one rules 3–5 want AND a different gold is in the deck AND he can survive without Defense 6 this round THEN Bushido. Do not choose the gold he already holds. A swap of a resolved or discarded gold comes back facedown; that is fine, the owner knows it. WEIGHT 5. SOURCE [ADVICE]; designer Discord 2026-06-16; `rulings/takahide.md` 7–8.
7. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`takahide-red-1`, `takahide-red-2a`, `takahide-red-3a`] WHEN he is adjacent to a legal target AND an ally in radius has an unresolved card AND that ally's hand (or, on 3a, discard) contains a card that answers the revealed round better than the one they played THEN play the red. The ally should accept the swap when the new card's action is legal and the old one is not, or when the new card's initiative lets them act before the enemy attack. WEIGHT 6 for a corrected kill, 2.5 for a corrected block. SOURCE CCplusplus, BGG 3451226; designer Discord 2026-07-10.
8. `CTX general` [PLAY_CARD] [`takahide-red-2b`, `takahide-red-3b`] WHEN an enemy who has not acted could step adjacent this turn AND he has an adjacent target so the attack connects AND Brynn is not the one who wants the obstacles THEN play it. WEIGHT 6. SOURCE CCplusplus (lock out a turn-4 kill); designer Discord 2026-10-20 and 2026-06-16.
9. `CTX general` [PLAY_CARD, CHOOSE_SPACE] [`takahide-green-1`, `takahide-green-2a`, `takahide-green-3a`] WHEN an ally in range has a card in the discard, OR has a spare they should spend, AND the move reaches an attack space THEN play it and take the move. The ally should discard only if their discard was empty and the move is worth a card. WEIGHT 4. SOURCE `rulings/takahide.md` 15–16, which corrects the spotlight's "they say no and you Hold."
10. `CTX general` [PLAY_CARD] [`takahide-green-2b`, `takahide-green-3b`] WHEN the coins complete a level for him or the ally AND the ally already has a discard or can spend a spare without dying THEN play it and retrieve if his own discard has a card he will use. WEIGHT the level (coins at 1.5 if they complete it) plus 2.5. SOURCE CCplusplus (do not collect coins by getting the ally killed).
11. `CTX general` [CHOOSE_CARD, CHOOSE_UNIT] [`takahide-blue-1`, `takahide-blue-2a`, `takahide-blue-3a`] WHEN an ally's faceup discard has a colour an enemy in radius still holds in hand THEN name that card, and pick the enemy who holds a defender or an attack of that colour. On 3a, the second enemy only if they also hold it. WEIGHT 4 for a defender, 2.5 for a spare. SOURCE card text; `rulings/takahide.md` 18–19.
12. `CTX general` [PLAY_CARD] [`takahide-blue-2b`, `takahide-blue-3b`] WHEN the ally's extra 2 spaces (ignore obstacles at tier 3) make an attack of theirs legal AND they still hold another attack card after the discard THEN the ally should discard and move. ELSE do not play it. WEIGHT the kill that move creates. SOURCE CCplusplus.
13. `CTX general` [CHOOSE_SPACE] [any] WHEN turn = 4 AND Float is not in hand AND no attack of his kills the adjacent hero THEN do not end adjacent to an enemy hero. Prefer radius 3–4 of an ally. WEIGHT 8. SOURCE CCplusplus (card-kills, low movement).
14. `CTX hero` [CHOOSE_ACTION] [`takahide-gold`, `takahide-gold-extra-1`, `takahide-gold-extra-2`] WHEN the Ultimate is on AND the action is Float's primary THEN it is not offered; do not plan on it. Sting and Strike still attack, and the swap after them does nothing. Hold Float to block. WEIGHT 5 for keeping Defense 8. SOURCE `rulings/takahide.md` 4 and 10.
15. `CTX general` [PLAY_CARD] [greens and blues] WHEN no ally is in the printed range or radius THEN do not play that card. Move toward an ally, or attack with the gold that has a target. WEIGHT 4. SOURCE card text; [ADVICE]; CCplusplus ("typically 3 or 4 spaces").
16. `CTX general` [CHOOSE_UNIT] [attack golds and reds] WHEN the target is a minion AND a hero is an equally legal target the engine total beats THEN the hero if the team is hunting a card-kill, else the minion if it is the lane margin. He has few minion tools, so a margin minion outranks a hero who will just defend. WEIGHT 15 against 2.5. SOURCE CCplusplus (each attack has to matter; wave pressure is weak).

## 7. Sources

- Catalog `takahide.json`. No FAQ entries.
- `core/game/rulings/takahide.md`, `heroes/TakahideHero.kt`.
- CCplusplus, Takahide spotlight, BGG 3451226, 2025-01-30 (playtester-reviewed). The "silver is the one-shot check" and "ally says no, you Hold" lines lose to the printed Defense on Float and to ruling 16. https://boardgamegeek.com/thread/3451226
- Designer Arty, Discord 2025-03-06 (Shameful Display), 2025-09-04 (he does not choose the starting gold), 2025-10-04 (facedown stays hidden), 2026-04-14, 2026-06-16 (cannot swap with itself; Brynn counts the ring), 2026-07-10 (swap-in acts next), 2026-10-20 (cannot end on the ring). https://www.cockeyedandcurious.com/goa2/faq
- Wolff Designa, Wayward overview. https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/9 — three golds, swap after use, secret swap, allies must discard. Matches.
- No Reddit or YouTube guide found.
