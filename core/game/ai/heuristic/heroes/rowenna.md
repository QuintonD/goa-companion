# Rowenna the Vanguard (`rowenna`) — heuristic playbook

2nd printing. Complexity **2**. Sheet: Attack 8–8, Defense 7–7, Initiative 4–4, Movement 3–5. Sheet roles: melee, durable, healer, farming, pusher, tactician. The sheet Attack 8 is **not** added to cards. Highest printed Attack is 7 (`rowenna-red-2b`, `rowenna-red-3b`). Gold is 4. Use the engine total.

She is a front-line support who can stand in the fight, not a closer. Gold offers the target a card **before** the attack (`rulings/rowenna.md` 1; catalog FAQ). The execute is the alt blue, and only on an empty hand.

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `Tank` |
| `idealDistance` | `1` (she has no printed range until `rowenna-purple-4`) |
| `aggression` | `0.60` |
| `laneWeight` | `0.45` (harass and keep allies alive; the push is a side effect) |
| `keepDefender` | true — a blue (Defense 6–7) or a red (Defense 6–8). Gold is Defense 2. |
| `upgradeOrder` | red by job (§3), then the blue that matches the team's finisher, then green |

CCplusplus (BGG 3447239, 2025-01-24, playtester-reviewed): she struggles to push because the attacks are melee and Movement on the non-red cards is 2. She jumps with silver and swaps with blue. Lane weight stays under a dedicated pusher.

## 2. Card-by-card

| Id | Init | For | Play when | Do not | Hold |
|---|---|---|---|---|---|
| `rowenna-gold` Code of Chivalry | 11 | Attack 4, adjacent. If the target is a hero, **both** may retrieve first. She chooses, then the target, and the target may defend with the card it just got. | Opening a fight: their best Defense is already the one in hand, and the discarded card's Defense loses to the engine total. Or a self-heal: you need a card back and you are happy to swing at a minion (no retrieve) or at a hero whose retrieve does not save them. Also after you blocked with red and want it back. | A hero whose discarded card's Defense, plus items and minion modifiers, meets Attack 4 + items. That is a heal for them and a wasted finisher. Turn 4 against an empty hand — use alt blue if you have it. | Defense 2. Bad defender. |
| `rowenna-handicap` Earn the Spurs | 10 | Same retrieve, but the target must be a hero. Attack 4, Defense 1, Movement 1. | Same as gold, hero targets only. | Minions (illegal). As a block. | None. |
| `rowenna-silver` Throw the Gauntlet | 5 | Place adjacent to an enemy hero in range 4. That hero may step 1; if it does, you gain 2 coins. The enemy moves itself (`rulings/rowenna.md` 2). | The hero is committed: already adjacent to a minion or an ally, or its remaining movement cannot leave range 4 before you act. Best when the landing space is also adjacent to other enemy units ([ADVICE]). | When they can walk out of range 4 and the primary fizzles, leaving you unmoved. When their step of 1 would land on your ally and that kill is worth more than 2 coins. | Defense 3. A block, not the tool. |
| `rowenna-red-1` Token of Gratitude | 8 | Attack 5. After, a friendly hero in radius 1 gains 1 coin. Never herself. | Adjacent to a target **and** an ally is in radius 1. The coin is mandatory; with no ally the action stops after the attack (the attack itself still happens). | Fishing for the coin with no ally nearby when a different card takes the minion safer. | Defense 6. A real block. |
| `rowenna-red-2a` Fair Share | 8 | Attack 6, ally in radius 3 gains 1 coin. | Same, easier radius. | — | Defense 6. |
| `rowenna-red-3a` Paragon of Grace | 9 | Attack 6, coin, may repeat on a different target. The coin is mandatory and comes **before** the repeat. No ally in radius: the repeat does not happen (designer Arty, Discord 2026-07-03; `rulings/rowenna.md` 3). The engine also treats the ally who got the first coin as a target, so the second coin needs a **different** ally. | Two allies in radius 3, two legal targets. That is two coins and two attacks. | Expecting a second coin with only one ally. | Defense 7. |
| `rowenna-red-2b` Feat of Bravery | 8 | Attack 7, Defense 7. An ally in radius 4 may retrieve. | The ally has a discarded card you want back, and you are adjacent to something you can hit. Higher Attack is why this branch exists (one more point through a red Defense). | An ally with an empty discard (they are not offered). | Defense 7. |
| `rowenna-red-3b` Paragon of Valor | 9 | Attack 7. Ally may retrieve; **if they do**, you may repeat on a different target. | The ally will take the retrieve (they have a discard and a reason). Then a second target exists. | No ally who will retrieve: you get one attack and no second swing. | Defense 8. Best block in the deck. |
| `rowenna-green-1` / `2a` / `3a` Close Quarters / Melee / Grand Melee | 4 / 4 / 3 | Move 2. If adjacent to an enemy hero after the move, place a friendly minion in radius next to that hero, and/or place an enemy minion in radius next to you. Tier 3: up to two times, same bullet allowed. | You can end adjacent to an enemy hero. Friendly minion next to them swings their Defense down by 1 and hands your ally a target; enemy minion next to you is a farm and a −1 on you that your Defense can pay. | The primary when you cannot end adjacent to a hero: it does not start (`rulings/rowenna.md` 5). Use the Movement action instead. | Defense 3–4. |
| `rowenna-green-2b` / `3b` Opening Shots / Opening Volley | 4 / 3 | An enemy hero in radius 3 discards, if **both** you and that hero have an empty discard. Tier 3 may repeat; the hero who just discarded no longer qualifies. | Turn 1, before either discard has a card, and a finisher on your team acts later. Radius 3, so you do not have to be adjacent. | Any time **you** have a card in the discard. Any time the enemy already discarded. It is not a closer. | Defense 4. |
| `rowenna-blue-1` / `2a` / `3a` Stand Guard / Protect the Weak / Defend the Innocent | 9 / 10 / 10 | Swap with a friendly unit in range 3 / 4 / 4 that is adjacent to an enemy hero, **or** a hero who has a card in the discard. Tier 3 then you may retrieve. | An ally is about to be hit and you can wear it, or you can land on a minion an enemy red was about to take. Your Defense is the point of the swap. | Swapping onto a hero-kill you cannot block, when the ally could have blocked it themselves. | Defense 6–7. Often the card you keep. |
| `rowenna-blue-2b` Accept Surrender | 10 | Defeat an enemy hero adjacent to you with **no cards in hand**. | They have revealed their last card or already played it, you are adjacent, and your init beats whatever they have left (or they have already resolved). | They still hold a card. The primary is not a threat until the hand is empty; do not spend the card as a walk. | Defense 6. Keep it for the execute **and** as the block until turn 4. |
| `rowenna-blue-3b` Glorious Triumph | 10 | Same execute. Friendly heroes gain **triple** assist coins (the normal assist, then twice more — `rulings/rowenna.md` 9). | Same window. The coins are the extra reason to take the kill even when the lane is also available. | Same. | Defense 7. |
| `rowenna-purple-4` Fabled Lance | — | Every attack becomes ranged, Range 2 (items raise it). The handicap still targets a hero only. | Once this is on, gold and red threaten at 2. Silver is less mandatory for closing ground. Still no execute on gold. | — | — |

## 3. Upgrade path

**Red is the job.** CCplusplus: everyone likes primary red (the coin); alt red is harder to fire and stronger.

- Keep `rowenna-red-2a` then `rowenna-red-3a` when the team is coining for levels and you can stand in radius 3 of an ally (a pusher, Sabina, a farmer). Tuck `rowenna-red-2b` (Defense item) and `rowenna-red-3b` (Defense item). Two Defense items on a hero who blocks with blue.
- Keep `rowenna-red-2b` then `rowenna-red-3b` when an ally actually discards and you need Attack 7. Tuck `rowenna-red-2a` (Initiative item) and `rowenna-red-3a` (Range item). The Range item matters once the Ultimate is on; before that her attacks have no range icon for it to raise, so this tuck is for later and for the Initiative from 2a.

**Blue.** Keep `rowenna-blue-2b` / `rowenna-blue-3b` when nobody else on the team finishes empty hands. Tuck `rowenna-blue-2a` (Attack item) and `rowenna-blue-3a` (Radius item). Keep `rowenna-blue-2a` / `rowenna-blue-3a` when a finisher is present and allies will overextend. Tuck `rowenna-blue-2b` (Defense item) and `rowenna-blue-3b` (Movement item). She is slow; the Movement item is the one that fixes silver-denied rounds.

**Green.** Keep `rowenna-green-2a` / `rowenna-green-3a` as the default (minion placement is the push tool she does have). Tuck `rowenna-green-2b` (Initiative) and `rowenna-green-3b` (Initiative). Keep Opening Shots / Volley only with a finisher who wants a turn-1 strip (Silverarrow, Swift, a discard partner). Tuck `rowenna-green-2a` (Attack) and `rowenna-green-3a` (Attack).

**Phase.** Level 2: red, the branch the team needs. Level 3: blue if you are the finisher or the bodyguard, else green. Level 4–6: the same branches to tier 3, red first if the tier-3 repeat is the payoff (`rowenna-red-3a` two coins, `rowenna-red-3b` the second swing). Do not take Opening Volley unless the empty-discard window has been real in this game.

## 4. Synergies and counters

**With:** a finisher who acts after a turn-1 `rowenna-green-2b` / `3b` strip or after she has forced a Defense with gold. Silverarrow is the example CCplusplus gives. An ally who stands in her red radius so the coin or the retrieve fires. She gives coins; she also needs assist coins and donated coins to reach the Ultimate.

**Fears:** being kited. If the table steps out of range 4 on the turn she plays silver, she Holds and her Movement 2 does not bring her back (CCplusplus). High-Defense heroes who laugh at Attack 4 after she has healed them. Discard effects that take the blue she was saving for the execute.

**Against her:**

- Track silver. It is init 5 and range 4, and it is most of her mobility. Leave range 4 and she does not move.
- Taking the 2 coins to step off her is correct when the step takes a minion, lands a kill, or leaves a death. Two coins is cheap.
- Do not end a round adjacent to her with an empty hand if alt blue may be in the deck. Leave, or keep one card.
- Her gold is an initiator. A low-Defense card in the discard is how she **can** kill you; a high-Defense card in the discard is how she cannot. Retrieve order is open, so this is knowable.
- Bodyguard blue: do not assume the ally you are about to hit is the one who will be there. Range 3–4, init 9–10.

## 5. Common mistakes

- Gold as a turn-4 finisher on a hero who still has a defendable card in the discard.
- `rowenna-red-3a` with one ally, expecting two coins. The repeat dies on the mandatory coin (designer, 2026-07-03).
- Playing Opening Shots after she already has a discard.
- Chasing with Movement 2 because silver was dodged.
- Standing where the red coin cannot see an ally, every attack.
- Swapping in with blue onto a hit her remaining hand cannot block.
- Treating her as the lane carry. She farms what is adjacent; she does not cross the map for a wave.

## 6. Heuristic rules

Weights as in the other playbooks: coin = 1, card ≈ 2.5, one minion of an even lane ≈ 15, a kill ≈ level coins + assists + one Life.

`evalTerms`: +1 while an ally is inside the radius of the red she still holds (the coin or retrieve is live). +2 while an adjacent enemy hero has no cards in hand and she still holds `rowenna-blue-2b` or `rowenna-blue-3b`. Nothing else of hers persists on the board.

`threatAdjust` (she is the enemy): any space adjacent to an enemy hero within 4 of her gets +5 while `rowenna-silver` may be unplayed (she can be there at init 5, which is beyond Movement 2). A friendly unit of hers that is adjacent to you, and within 4 of her, is a swap threat: +4 on your current space while `rowenna-blue-1` / `2a` / `3a` may be unplayed. An empty-handed hero adjacent to her is in execute range even though she has no printed "attack reach" for it: +the kill on that adjacency while alt blue may be unplayed.

1. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`rowenna-gold`, `rowenna-handicap`] WHEN the target is a hero AND a card in that hero's discard has Defense (plus items and the current minion modifier) ≥ the engine Attack total THEN avoid that target. Prefer a minion, or a hero whose discard cannot answer. WEIGHT the kill you would hand them (their card back is 2.5, and you lose the swing). SOURCE catalog FAQ; CCplusplus, BGG 3447239; `rulings/rowenna.md` 1.
2. `CTX general` [PLAY_CARD] [`rowenna-gold`] WHEN your own discard holds the blue or red you need AND the target is a minion, or a hero who fails rule 1 THEN prefer gold. WEIGHT 2.5 for the card back, plus the minion. SOURCE CCplusplus (self-heal, especially if she acts after them).
3. `CTX general` [PLAY_CARD, CHOOSE_SPACE] [`rowenna-silver`] WHEN an enemy hero is in range 4 AND that hero's revealed card cannot move out of range 4 before this resolves AND at least one empty space in range is adjacent to that hero THEN prefer silver. Prefer the space that is also adjacent to the most other enemy units. WEIGHT 4 for the position, plus 2 if they step. SOURCE [ADVICE]; CCplusplus, BGG 3447239.
4. `CTX general` [PLAY_CARD] [`rowenna-silver`] WHEN every enemy hero in range 4 has a revealed card that can leave range 4 before init 5 THEN avoid silver. WEIGHT 4 (a Hold and a lost round). SOURCE CCplusplus.
5. `CTX general` [CHOOSE_UNIT, CHOOSE_SPACE] [`rowenna-red-1`, `rowenna-red-2a`, `rowenna-red-3a`] WHEN an ally is in the card's radius THEN take the attack; on `rowenna-red-3a` repeat only when a second ally is in radius and a second target exists. WHEN no ally is in radius THEN the attack is still legal, but it is worth only the target, not the coin. WEIGHT +1 per coin that will actually be gained. SOURCE card text; designer Discord 2026-07-03; `rulings/rowenna.md` 3.
6. `CTX general` [CHOOSE_UNIT] [`rowenna-red-2b`, `rowenna-red-3b`] WHEN an ally in radius has a discard THEN prefer a target that lets you offer them the retrieve; repeat on 3b only if they take it. WEIGHT 2.5 for their card, plus a second attack if the repeat is live. SOURCE `rulings/rowenna.md` 4.
7. `CTX general` [PLAY_CARD] [`rowenna-green-2b`, `rowenna-green-3b`] WHEN turn = 1 AND her discard is empty AND an enemy hero in radius 3 has an empty discard THEN prefer this card. On 3b, repeat only on a hero who still has an empty discard. WEIGHT 2.5 per card stripped, more if a teammate's later attack beats the hand that remains. SOURCE card text; CCplusplus (needs a finisher).
8. `CTX general` [PLAY_CARD] [`rowenna-green-2b`, `rowenna-green-3b`] WHEN her discard is not empty OR no enemy in radius has an empty discard THEN do not choose the primary (it will not be offered). Do not play the card for the skill. SOURCE `rulings/rowenna.md` 8.
9. `CTX general` [CHOOSE_OPTION, CHOOSE_SPACE] [`rowenna-green-1`, `rowenna-green-2a`, `rowenna-green-3a`] WHEN adjacent to an enemy hero after the move THEN place a friendly minion adjacent to that hero if an ally can attack them this turn or the next; else place an enemy minion adjacent to her if she wants the farm and her Defense still beats the attacks that resolve after this init. WEIGHT 4 for the modifier, or the minion. SOURCE CCplusplus; [REASONED].
10. `CTX general` [CHOOSE_UNIT] [`rowenna-blue-1`, `rowenna-blue-2a`, `rowenna-blue-3a`] WHEN a friendly unit in range is the target of an unresolved enemy attack that her remaining Defense meets and the ally's does not THEN swap with that unit. WHEN the unit is a minion an enemy is about to defeat and she can survive standing there THEN swap. WEIGHT the ally's life, or the denied minion. SOURCE CCplusplus, BGG 3447239.
11. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`rowenna-blue-2b`, `rowenna-blue-3b`] WHEN she is adjacent to an enemy hero with no cards in hand THEN override the primary on that hero. WEIGHT the kill, and on `rowenna-blue-3b` add 2 × the normal assist coins (triple = the usual assist plus two more). SOURCE card text; CCplusplus; `rulings/rowenna.md` 9.
12. `CTX general` [PLAY_CARD] [`rowenna-blue-2b`, `rowenna-blue-3b`] WHEN no adjacent enemy has an empty hand AND it is before turn 4 THEN do not spend it as movement. Hold it. WEIGHT 6 (the execute plus a Defense 6–7 block). SOURCE [REASONED] from the card and from "blue is the spare" in STRATEGY.md §1.5, which fits this card.
13. `CTX general` [DEFEND] [any] WHEN the attack is met by a red or a blue AND gold is still in hand for a later retrieve of that red THEN prefer to keep gold and spend the cheaper card that still holds. WHEN only gold meets it THEN gold. WEIGHT 2.5. SOURCE CCplusplus (she often does not mind blocking with red if gold retrieves it).
14. `CTX general` [CHOOSE_SPACE] [any movement] WHEN silver is already spent this round THEN prefer spaces that stay next to allies (red radius) and next to a minion she can hit. Do not spend the whole move chasing a hero who is still faster. WEIGHT 3. SOURCE CCplusplus (low movement); [ADVICE] (talk to teammates about the reds).
15. `CTX general` [PLAY_CARD] [`rowenna-gold`] WHEN the Ultimate is on AND a hero at range 2 fails rule 1 THEN gold may target them at range 2. The retrieve problem is unchanged. WEIGHT as rule 1. SOURCE `rowenna-purple-4`; `rulings/rowenna.md` 6.

## 7. Sources

- Catalog `rowenna.json` (cards, advice, FAQ).
- `core/game/rulings/rowenna.md`, `heroes/RowennaHero.kt`.
- CCplusplus, Rowenna spotlight, BGG 3447239, 2025-01-24 (2nd printing, playtester-reviewed). https://boardgamegeek.com/thread/3447239
- Designer Arty, Discord 2026-07-03, on Paragon of Grace (cockeyed FAQ). https://www.cockeyedandcurious.com/goa2/faq
- Wolff Designa, Arcane pack overview (front-line support, self-heal). The line "she will not attack an unarmed opponent" matches gold and **contradicts** `rowenna-blue-2b` / `rowenna-blue-3b`. Card text wins: empty hands are the execute. https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/10
- No 2nd-printing Reddit or YouTube guide found. The 1st-printing spotlights were not used; this hero's 2nd-printing thread is the strategy source.
