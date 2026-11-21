# Emmitt (`emmitt`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/emmitt.json`. Engine: `EmmittHero.kt`.
Rulings: `core/game/rulings/emmitt.md`. Complexity 4. Stats: Attack 5/8, Defense 6/6, Initiative 1/1, Movement 2/4.
Catalog roles: melee, tactician, durable, disabler, tokens. Glitch tokens, removed at end of turn. No FAQ on the catalog card.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances, and whether Reverse Time is already the active order. `CTX hero` needs a fact only this playbook tracks.

"Forward" means Reverse Time is not in force this turn. "Reverse" means `emmitt-gold` or `emmitt-handicap` connected last turn, so lower initiative acts first (`Behavior.reversesInitiative`). The effect is this round only (`rulings/emmitt.md` 1). Ties still use the coin.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Support | He sets the order and moves other heroes. The punch is the threat that makes them stand still; the skills cash it. [BGG 3779890] Disruptor/Support. [BGG 3459593] he supports allies' kills more than he pushes the wave. |
| idealDistance | 1 | Gold and both reds are adjacent. Skills reach 2–4, but the card that defines the turn is the melee hit that starts Reverse Time. |
| aggression | 0.40 | He walks into melee to punch a minion or a hero who cannot block. He does not dive two heroes to farm. |
| laneWeight | 0.40 | A shove off a minion is the lane play. Taking the minion himself is how he turns Reverse Time on, not the win condition. |
| keepDefender | true | `emmitt-silver` is Defense 6 and the teleport. `emmitt-red-1` is Defense 7 and the threat. Spend green and blue before either. |
| upgradeOrder | see §3 | Primary red is the default threat. The skill build replaces it when an ally is the brawler. |

He wants one ally with a slow, heavy red. Forward, he threatens Temporal Punch so enemies hold a fast card. Reverse, that ally's red goes before their escape. His own Movement 2 and Initiative 1 mean he does not chase. He arrives by silver, by Time Loop, or because they walked to him.

## 2. Card-by-card

Initiative band, forward: Gold 11, Blue 8 then 9, Red 7 then 8, Green 6 then 5, Silver 1. Items add +1 to every card with the icon. In reverse, that list is the order from last to first: Silver acts first, Gold last. A card he plays on turn 4 cannot start a Reverse Time that matters; there is no next turn this round.

"Remained" (`rulings/emmitt.md` 7, engine): on the hex it held at the start of this turn, and not moved, pushed, placed, or swapped since, including a trip that ends on the start hex. Designer shorthand "has not moved this turn" [Discord, Arty, 2025-02-15] matches the intent. The engine's note is the one to test.

"Already resolved" (`rulings/emmitt.md` 8): their card is on a Turn slot this turn. A hero still to act does not qualify.

### `emmitt-gold` Reverse Time — Attack 4, Defense 2, Movement 1, Initiative 11

Adjacent attack. After it connects, next turn the order flips, ignoring immunity. The immunity clause is only the order: it does not pierce Future Proof or anything else [BGG 3687772, nichik, 2026-04-03]. A blocked hit does not connect and does not flip the order (RULINGS 18). Engine: the attack, then the active effect. No confirm.

Handicap `emmitt-handicap` Tear in Time, 5-player: Attack 3, Initiative 10, hero targets only, same next-turn text. Do not plan a minion hit on it. It is one slower than gold, so more reds act before it.

For: the turn he wants, not the damage. Attack 4 reliably defeats a minion and that is enough to connect. [ADVICE] it hits everyone, allies included. [BGG 3459593] announce it; think every card in both orders. [Gamefound, Wolff Designa, Wayward overview] a slow save or a kill on a hero with no slow card left; the same swing strands an ally who needed to go last. [BGG 3459593] turn 2 or 3, not the opening of the round. Turn 4 wastes the "next turn" text.

When: turn 2 or 3, an adjacent unit he can actually beat, and the flipped next turn helps his team (an ally still holds a card of initiative ≤ 7 that will reach an enemy, and no ally is left holding only a slow card while an enemy slow red will now act first on them). A minion is the clean target when the hero might still block Attack 4. The hero is the target when they have 0 cards, or their best remaining total against Attack 4 fails.

When not: turn 1 (hands are full; the flip is a guess). Turn 4, except as a plain Attack 4 finisher on an empty hand — the order will not come. Any turn an ally's only remaining escape is a Green and an enemy still holds a slow attack that reaches that ally. Defense 2: do not be caught with only this as a block. Movement 1 is not travel.

### `emmitt-silver` Unstable Timeline — Defense 6, Initiative 1, Radius 4, defense and skill

Place 2 Glitch tokens in radius, at least two spaces between each (engine: every pair at distance ≥ 3). As a defense, place 3. An enemy hero he chooses, any enemy on the board, picks one token; he swaps with it. End of turn, remove every Glitch. No movement value. Choosing the hero is not targeting, so immunity does not stop it (`rulings/emmitt.md` 5). A dead hero still off the board cannot be chosen [BGG 3459593]. If no enemy is on the board the swap text never starts and the tokens stay until end of round. If the placement cannot be finished, nothing is placed; as a defense the Defense 6 still counts (`rulings/emmitt.md` 3). Supply is 3 (`rulings/emmitt.md` 4); extra tokens are taken off the board first (`CHOOSE_TOKEN`).

Engine, primary or defense: optional recycle `CHOOSE_TOKEN`, then `CHOOSE_SPACE` per token, then `CHOOSE_UNIT` for the enemy, then that enemy's `CHOOSE_TOKEN`.

For: the relocate and the Defense 6. [ADVICE] mobility, and it leaves him vulnerable because the enemy picks the token. [BGG 3459593] place so every token is a position he can live with; the enemy takes the worst of them. Forward (he acts last): an escape that is hard to chase. Reverse (he acts first): a dodge before the swing, landing where the punch or an ally's red reaches. Tokens are obstacles until end of turn even after the swap.

When: he must leave, or reverse has just started and initiative 1 lets him move before anyone. As a defense, when he must keep the red, or Defense 6 is what meets the hit and a landing among the tokens is acceptable. Three tokens on defense, still their choice, so the landings have to be even safer.

When not: as a casual walk. One good hex and one hex adjacent to two enemies is a donation. Do not spend it on turn 1 of a round he still needs a Defense 6 later. After the swap he is often out of the fight; that is the vulnerability [ADVICE].

### `emmitt-red-1` Temporal Punch — Attack 9, Defense 7, Movement 3, Initiative 7

Adjacent attack. The defender's total uses the Initiative of their card and their Initiative items, not the Defense of the card or Defense items. Minion modifiers still apply (the reminder on `emmitt-red-1`; the upgrade cards omit the reminder and do not change the rule). The engine also keeps "+N Defense" printed on the defense card's own text, and a Block still blocks or fails on its condition (`rulings/emmitt.md` 6). Designer: the card "replaces def value of card and items with ini values. Nothing else" [Discord, Arty, 2025-07-09]. Follow the engine in the bot: printed Defense and Defense items are out; Initiative, Initiative items, minion modifiers, and "+N Defense" text are in. So `dodger-blue-1` against this, with the spawn bonus, is 10 + 2 before minions, not 10 and not its printed Defense 3.

For: the threat. Attack 9 beats a Green (about 2–6), most Silvers that are not Garrus's 13, and a Red at 7–8. A Gold at 11–12 survives unless modifiers go the other way. [BGG 3459593] it pulls fast cards out of their hand, which is a lose-lose if an ally has a normal high-Attack red. It does not pull a fast Silver. Forward: a slow melee threat. Reverse: they will have moved on the cards that are now fast, so do not count on them still being adjacent [BGG 3459593].

When: adjacent to a hero whose best remaining block is initiative ≤ 8 (9 does not meet 9 if the rule is total ≥ attack — a 9 meets Attack 9; the punch sticks when their total is under 9), or adjacent to a minion and no such hero exists. Defense 7 is his best normal block. Prefer to keep it and block with silver if both would save him and he still wants the threat.

When not: into a hero holding a Gold or a Silver at 12+ who is not on a kill clock. As the reverse-time attack: they are usually gone. Movement 3 is his longest printed walk; spending the punch to walk is the mistake unless the walk is the only way onto next turn's target.

### `emmitt-red-2a` Temporal Slam — Attack 11, Defense 7, Movement 3, Initiative 7, item Initiative

Same rule, Attack 11. A Gold at 11 fails without an Initiative item or a minion. [BGG 3606834] this is the card that "sunders enemy gold cards" if they have no fast Silver. Keeping it tucks `emmitt-red-2b` (Defense item).

### `emmitt-red-3a` Temporal Judgment — Attack 12, Defense 8, Movement 3, Initiative 8, item Range

Attack 12. Only a very fast card, plus modifiers, lives. Defense 8 is the block. The Range item widens Time Walk, Time Trap, and Time Loop. Keeping it tucks `emmitt-red-3b` (Defense item).

### `emmitt-red-2b` Flashback — Attack 5, Defense 6, Movement 3, Initiative 7, Radius 3, item Defense

Adjacent attack. After it connects, he may place 3 Glitch tokens in radius, pairwise at distance ≥ 3; if he does, up to 1 enemy hero in radius swaps with a token of their choice. End of turn, remove them. The swapped hero need not be the one he hit [BGG 3459593]. He chooses the hero; they choose the token, at any distance [Discord, Arty, 2026-07-19]. Engine: attack, `CONFIRM` to place, `CHOOSE_SPACE` × 3, optional `CHOOSE_UNIT`, their `CHOOSE_TOKEN`.

For: the yank. Attack 5 is not the kill. Because they choose, every token has to be a bad hex for them: off the minion, out of their red's reach, not a free walk to the next wave. [BGG 3459593] forward, this sets up an ally who acts after him; reverse, he goes before their Gold and the ally's now-late high initiative hits the new hex.

When not: three tokens of which one is a gift. Decline the may when no pair of bad hexes exists (he should decline a set whose worst token, from his point of view, helps them). Keeping this card tucks Temporal Slam, so the item is Initiative and he gives up Attack 11.

### `emmitt-red-3b` Déjà Vu — Attack 6, Defense 7, Movement 3, Initiative 8, Radius 3, item Defense

Same, but 2 tokens. Fewer choices for them is the upgrade [BGG 3459593]. Attack 6 still loses to a real block. Keeping it tucks Temporal Judgment (Range item).

### `emmitt-green-1` Time Walk — Defense 2, Movement 2, Range 3, Initiative 6, Skill

Move an enemy hero in range who remained, 2 spaces in a straight line. Engine: `CHOOSE_UNIT`, then the move (a straight 2, so a `CHOOSE_SPACE` among the legal ends). No one in range who remained: the primary is not offered.

For: a hero who stood still. [BGG 3459593] forward, they held their hex to use a card; shove them off a minion, into an ally, or out of their own reach. Reverse, initiative 5–6 is early, and at that moment almost everyone has remained, because nobody has moved yet. That is when the shove stops a take or saves an ally before the high-initiative cards.

When not: a hero who already left their start hex. Defense 2. Do not shove an enemy next to an ally who cannot survive the attack that then reaches them.

### `emmitt-green-2a` Fast Forward — same text, Range 4, Initiative 5, item Attack

Range 4 is the version that crosses a zone. Keeping it tucks `emmitt-green-2b` (Initiative item).

### `emmitt-green-3a` Back to the Future — Defense 3, Movement 2, Range 4, Initiative 5, item Attack

Choose one: place a unit in range onto the empty hex it occupied at the start of this turn (not himself, and it must actually have left), or the same 2-space shove. Engine: `CHOOSE_OPTION`, then `CHOOSE_UNIT`. A bullet with no target is not offered.

For: the rewind. [BGG 3459593] forward, pull back a hero who spent a long move to leave or to reach a minion. Reverse, it answers a Green that already fired and ran. The shove remains the tool when they have not moved.

When: the place bullet if the start hex is worse for them than where they stand (they escaped, or they reached the minion). The shove if they remained and a 2-step line is the setup.

### `emmitt-green-2b` Time Capsule — Defense 2, Movement 2, Initiative 5, Radius 4, Skill, item Initiative

He, and friendly heroes in radius, may each retrieve all cards they discarded this turn. Each decides (`CONFIRM`, that hero chooses). All or nothing per hero. Only cards still in the discard that were discarded this turn, and only those discarded before this action [Discord, Arty, 2026-06-05]. A swap into the discard is not a discard [Discord, Arty, 2026-01-18]. Retrieving a discarded defense ends its active effect (`rulings/emmitt.md` 10). Not offered if nobody has such a card.

For: the undo, forward. Initiative 5 is after Gold, Blue, and most Reds, so the discards have happened. [BGG 3459593] the more cards hit the discard this turn, the better. Reverse, the Golds have not acted yet, so this often undoes only an enemy Green. Weak until tier III.

When not: to "heal in advance." It does not see the future of the turn. Defense 2.

### `emmitt-green-3b` Future Proof — Defense 3, Movement 2, Initiative 5, Radius 4, Skill, item Initiative

Choose one: the same retrieve, or this turn friendly heroes in radius (never himself) are immune to enemy actions and to enemy active effects. Immunity is rechecked from his current space (`rulings/emmitt.md` 11). It does not interact with Reverse Time's immunity clause [BGG 3687772]. Engine: `CHOOSE_OPTION`; the immunity bullet is always there; the retrieve only if someone has a qualifying card.

For: the reverse-time shield. He acts early, allies in radius cannot be hit for the rest of the turn, including by the Golds that now go last. He himself is hittable. [BGG 3459593] "them never being attacked at all," and it does not save him. Forward, the retrieve is usually the bullet, because the attacks have already landed and immunity would only cover whoever has not acted (Greens).

Keeping it tucks `emmitt-green-3a` (Attack item).

### `emmitt-blue-1` Time Snare — Defense 3, Movement 2, Range 2, Initiative 8, Skill

An enemy hero in range who has already resolved a card this turn discards a card, if able. Empty hand: nothing. Engine: `CHOOSE_UNIT`; no such hero and the primary is not offered.

For: a discard on someone who went fast. [BGG 3459593] forward, that is a Gold or a faster Blue (initiative above 8). A Red at 7 and every Green have not acted yet. Reverse, Greens and slow Reds have already acted, and this becomes the reliable discard. Range 2 means he is close.

When not: fishing. If nobody in range has resolved a card, do not play it. Defense 3 is a poor block next to the red's 7.

### `emmitt-blue-2a` Time Trap — same text, Range 3, Initiative 9, item Defense

Initiative 9 still loses to a typical Gold, so forward it catches Golds and Blues at 10+. Range 3 is the first trap that does not require him to stand on them. Keeping it tucks `emmitt-blue-2b` (Attack item). [BGG 3606834] the extra Attack is why the punch build takes this second.

### `emmitt-blue-3a` Time Bomb — Defense 4, Movement 2, Range 3, Initiative 9, item Movement

Same target, but "discards a card, or is defeated." A hero with no cards left dies. A hero with cards discards. This is the turn-4 finisher on someone who already acted and is empty [BGG 3606834]. "If able" on the lower traps does not kill that hero. The Movement item is the boot his stat 2 is missing; it is the tuck when Time Warp is kept instead.

### `emmitt-blue-2b` Time Loop — Defense 4, Movement 2, Range 4, Initiative 9, Skill, item Attack

Swap with an enemy hero in range who has already resolved a card this turn. Engine: `CHOOSE_UNIT`.

For: stealing a position. [BGG 3459593] they walked to the next wave, he takes the hex, they inherit his. An ally standing next to him receives a wounded hero. Range 4 is his real mobility. Same timing as the trap: forward it catches fast cards, reverse it catches slow ones.

When not: the swap that puts him adjacent to two enemies and puts them next to the minion he was holding. Defense 4.

### `emmitt-blue-3b` Time Warp — Defense 4, Movement 2, Range 4, Initiative 9, Skill, item Radius

Choose one: the swap, or an enemy in range swaps their unresolved card with a resolved card of their choice. They must have both. Both cards' active effects end. The card that comes in is now this turn's card and resolves on its own initiative (`rulings/emmitt.md` 12). Engine: `CHOOSE_OPTION`, `CHOOSE_UNIT`, their `CHOOSE_CARD`.

For: blanking the card they have not played. They pick the replacement, so this is good when every resolved card is a weak one to replay and the unresolved card is the kill. It is bad when they have a resolved attack they are happy to play again. [BGG 3459593] the more they have resolved, the more rope he gives them. Forward: their slow Red or Green is still unresolved and this stops it. Reverse: their Gold is still unresolved (it goes late) and this is how he saves an ally from the finisher.

Keeping it tucks `emmitt-blue-3a` (Movement item). [BGG 3606834] that boot is why a punch build grabs this early at tier III. The Radius item widens the Glitch cards and Future Proof.

### `emmitt-purple-4` Alternative Timelines — Ultimate, no stats

He may play two cards each turn. If he does, after the reveal he retrieves one of the two (`CHOOSE_CARD`). Both were revealed, so "played an attack card this turn" counts both (`rulings/emmitt.md` 13). Off the board the Ultimate is not in force and he plays one. The kept card is the one that resolves; he chooses after he has seen every revealed card.

For: play the two cards that answer the two possible boards (a trap and a shove, or the punch and the silver), look, take back the whiff or the card he still needs as a block. Do not play two when one of them is the only Defense 6 and he will not be retrieving it.

## 3. Upgrade path

[BGG 3606834] gives two builds. The bot starts on Temporal Warrior: the punch is a threat the general playbook can respect, and it depends less on a perfect flipped turn. Captured In Time is the switch when an ally is the brawler and Emmitt should move people.

"Keep X" puts X in hand. The item is the other branch's catalog `item`.

Default:

1. Tier II first: `emmitt-red-2a`. Attack 11 is what makes a Gold a bad block. Tuck `emmitt-red-2b` (Defense).
2. Tier II second: `emmitt-blue-2a`. Range 3 trap, and the tuck `emmitt-blue-2b` is Attack, which Slam wants. [BGG 3606834]
3. Tier II third: `emmitt-green-2b`. The retrieve, and the tuck `emmitt-green-2a` is a second Attack. He is now Attack 4+2 on gold and 11+2 on the punch.
4. Tier III first: `emmitt-green-3b` if he will see Reverse Time most rounds (the immunity; tuck `emmitt-green-3a` is a third Attack). If the game is forward-time punches, `emmitt-blue-3b` first instead, for the Movement item on a hero whose stat is 2.
5. Tier III second: the other of those two. Time Bomb (`emmitt-blue-3a`) replaces Time Warp when enemies are ending turns with empty hands and he already has a Movement item from somewhere else.
6. Tier III third: `emmitt-red-3a`. Attack 12, Defense 8, Initiative 8. Tuck `emmitt-red-3b` (Defense). The Range item is on this card; he only gets it if he keeps Déjà Vu instead, which is the other build.

Captured In Time [BGG 3606834], when an ally holds the heavy red:

1. `emmitt-blue-2a` (tuck Attack).
2. `emmitt-red-2b` (tuck Initiative from Temporal Slam). The punch is gone; the yank is the red.
3. `emmitt-green-2a` (tuck Initiative from Time Capsule). Two Initiative items make his blue later in reverse, so more heroes have already resolved when the trap fires.
4. Tier III: `emmitt-red-3b` first (2 tokens, tuck Range from Judgment), then `emmitt-blue-3a` (the empty-hand kill, tuck Radius from Time Warp), then green by match-up: `emmitt-green-3b` if allies are dying in the flipped turn (tuck Attack), else `emmitt-green-3a` (tuck Initiative).

Items to tuck:

- On the default path, two Attack items before tier III, from the blue and the green tucks. The punch is the card they serve.
- One Defense item from tucking Flashback. Do not also tuck the red he blocks with.
- A Movement item by tier III if he is still on Movement 2. That is the tuck of `emmitt-blue-3a`, so it means keeping Time Warp. Take it before a third Attack item if he cannot reach the melee.
- Initiative items are the skill-build's tool, not the default. They make him faster forward and later in reverse. Later in reverse is what the trap wants; faster forward is what gets the gold hit in. One is enough on the punch build (he does not tuck an Initiative card there unless he keeps Flashback).

Branch summary:

| Colour | Default | Why | Switch when |
|---|---|---|---|
| Red | a `emmitt-red-2a`/`3a` | Attack 11 then 12 on the initiative rule. Defense 7 then 8. | An ally is the brawler and enemies will not stand still: `2b`/`3b`. |
| Blue | a `emmitt-blue-2a`, then `3b` if he still has no Movement item, else `3a` | The trap is Range 3 and banks an Attack item. Time Bomb kills the empty hand; Time Warp banks the boot. | He is the one who must steal a hex at Range 4: stay on `2b`/`3b`. |
| Green | b `emmitt-green-2b`/`3b` | Retrieve forward, immunity in reverse. | Nobody is discarding and enemies are running: `2a`/`3a`. |

## 4. Synergies and counters

Teammates: a slow brawler. Brogan's charge, Garrus's alt red, anyone whose red is initiative 7–8 and Attack 6+. Reverse Time turns that red into the first hit. He is bad beside a team that only has fast cards: the flip gives the enemy's Greens the first move [ADVICE] [BGG 3459593].

Enemies he fears: a fast Silver they can throw at the punch (Garrus's Howl at 13, Arien's Spell Break does not stop an attack). Displacement that puts him off the adjacent hex before initiative 7. Killing the silver out of his hand, because the enemy then chooses a bad hex and he has lost the Defense 6 [BGG 3459593]. Denying him an adjacent unit on the gold turn, so the order never flips.

Playing against him:

- Keep a slow card. When the order flips, the Green or the slow Red is the escape and the counter [BGG 3459593].
- Do not spend that slow card on the turn before his gold if he is adjacent to a minion. He will connect, and the next turn the card is gone.
- Block the punch with initiative, not with a fat Red. Temporal Slam (11) laughs at a Defense 8 card printed at initiative 7. Minion modifiers still count. A Block still blocks.
- If he is forced to defend with silver, pick the Glitch that strands him. He had to offer you only hexes he could accept; take the one furthest from the Battle Zone.
- Future Proof does not cover him. He is the legal target on that turn.
- A hero who has not acted is not a Time Snare target. A hero who has moved is not a Time Walk target.

## 5. Common mistakes

- Gold on turn 1, or gold on turn 4 "to go reverse." Turn 4 has no next turn. Turn 1 flips a board he has not read.
- Gold while an ally's only card is a Green and an enemy slow red reaches that ally. The flip is the ally's death [ADVICE] [BGG 3606834].
- Expecting the punch to land in reverse. Their now-fast Green walks them off the hex before initiative 7.
- One Glitch on a good hex and one on a kill hex. They take the kill hex.
- Time Walk on a hero who stepped off and stepped back. The engine does not call that "remained."
- Time Snare or Time Loop on a hero who has not resolved a card yet. The primary is not offered.
- Time Capsule in reverse, expecting to pick up discards from Golds that have not acted.
- Future Proof to save himself. The immunity is other friendly heroes.
- Time Warp when they have a resolved Attack they want to play again. He spent the card to replay their best hit.
- Defending against Temporal Punch with the high-Defense red. The number that matters is that card's initiative.
- Playing two Ultimate cards and retrieving the one that had a target.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`emmitt-gold`, `emmitt-handicap`] WHEN the turn is 2 or 3, an adjacent unit will not block Attack 4 (a minion, or a hero with 0 cards, or a hero whose best remaining defense total is under 4), AND the next turn's flip helps (see rule 2) THEN prefer gold over the punch and over a skill. WEIGHT +14, plus the kill if the target hero has 0 cards. A minion target is +6 when the only hero might still block; the reverse is the point, not Attack 4. CTX general. SOURCE [BGG 3459593] [BGG 3606834] [ADVICE].

2. `PLAY_CARD` [`emmitt-gold`, `emmitt-handicap`] WHEN an ally's remaining cards are all initiative ≤ 6 and an enemy still holds a card of initiative ≤ 7 that reaches that ally THEN avoid gold. WEIGHT −20. The flip lets that enemy act before the ally's escape. Also avoid on turn 1 (WEIGHT −8) and on turn 4 unless the target has 0 cards (then it is only the attack; the order is worth 0). CTX general. SOURCE [ADVICE] [BGG 3606834] [REASONED].

3. `PLAY_CARD` [`emmitt-red-1`, `emmitt-red-2a`, `emmitt-red-3a`] WHEN he is adjacent to a hero and it is forward time THEN prefer the punch if their best block total, scored as initiative + initiative items + minion modifiers + any "+N Defense" on that card, is under his Attack (9/11/12 plus Attack items). WEIGHT the kill if they have 0 cards, +12 if they have 1 and the total fails, +4 if they will throw a Gold and survive (the Gold is now gone). WHEN it is reverse time THEN avoid the punch unless they are still adjacent and have no unresolved movement card. WEIGHT −8 on the hopeful version. CTX general. SOURCE [BGG 3459593] [BGG 3606834] rulings/emmitt.md 6.

4. `DEFEND` any WHEN the attacker is Emmitt's primary red (he is the defender in this rule only as a mirror; when he himself blocks a normal attack, ignore this) — see threatAdjust. For his own blocks: WHEN Defense 6 or 7 meets the incoming hit THEN prefer `emmitt-red-1`/`2a`/`3a` if he does not still need the punch this round, else `emmitt-silver` if every Glitch he can place is a hex he accepts. WEIGHT +6 for keeping the punch when silver also lives. CTX general. SOURCE [BGG 3459593] [ADVICE].

5. `CHOOSE_SPACE` [`emmitt-silver`] WHEN placing Glitches THEN every token must be a hex he would accept if it is the one they pick: not adjacent to two enemy heroes, and in or beside the Battle Zone if any such hex exists in radius. Pairs at distance ≥ 3. WEIGHT −25 for a set whose worst hex fails that test (do not start it). WEIGHT +8 if the worst hex still threatens a minion or an enemy with the punch. As a defense he places 3 and the same test is stricter. CTX general. SOURCE [BGG 3459593] [ADVICE].

6. `CHOOSE_UNIT` [`emmitt-silver`] WHEN he chooses who picks the token THEN prefer the enemy hero who is least able to punish every landing hex (already acted, no attack left, or far from all of them). WEIGHT +5. Do not choose a hero who is off the board. CTX general. SOURCE [BGG 3459593].

7. `CONFIRM` + `CHOOSE_SPACE` [`emmitt-red-2b`, `emmitt-red-3b`] WHEN the attack connected THEN place only if every token is a bad hex for the hero he will swap (off their minion, out of their unresolved attack's reach, not toward the next wave). 3 tokens at tier II, 2 at tier III, radius 3, pairwise distance ≥ 3. WEIGHT +10 if the best token they can pick still does that. Decline otherwise. WEIGHT −6. The swapped hero need not be the attack target; prefer a hero who remained on a minion over the one he just failed to kill. CTX general. SOURCE [BGG 3459593] [Discord Arty 2026-07-19].

8. `PLAY_CARD` [`emmitt-green-1`, `emmitt-green-2a`, `emmitt-green-3a`] WHEN it is forward and an enemy in range remained (still on their start hex, no displacement) THEN shove if a straight 2 puts them off a minion, into an ally's attack, or out of their own attack. WEIGHT +10 / +12 / +8. WHEN it is reverse THEN the same shove is earlier: WEIGHT +12 if it takes them off a minion they have not yet struck, or off an ally. Do not shove them into an ally who has no block left. WEIGHT −15 for that line. CTX general. SOURCE [BGG 3459593] [REASONED].

9. `CHOOSE_OPTION` [`emmitt-green-3a`] WHEN a unit in range has left its start hex and that hex is empty THEN prefer the place bullet if the start hex is worse for them than the hex they occupy (they reached the minion, or they escaped an ally). WEIGHT +12. Else the shove, under rule 8. Never himself. CTX general. SOURCE [BGG 3459593].

10. `PLAY_CARD` [`emmitt-blue-1`, `emmitt-blue-2a`, `emmitt-blue-3a`] WHEN an enemy in range has already resolved a card this turn THEN prefer the trap. WEIGHT +10 if they have 2+ cards (a real discard), the kill on `emmitt-blue-3a` if they have 0, +4 on `emmitt-blue-1`/`2a` if they have 0 (the "if able" does nothing; do not spend the card on an empty hand). Forward, this is mostly Golds and faster Blues. Reverse, it is Greens and slow Reds. WEIGHT +4 extra in reverse when their resolved card was a movement card and an ally can now reach the hex. CTX general. SOURCE [BGG 3459593] [BGG 3606834].

11. `CHOOSE_UNIT` [`emmitt-blue-2b`, `emmitt-blue-3b`] WHEN swapping places THEN prefer the enemy who already resolved a card and whose hex is the one he wants (closer to the Battle Zone, or adjacent to an ally who can hit them) while his own hex is worse for them. WEIGHT +12. Avoid the swap that leaves him adjacent to two enemies. WEIGHT −10. Range 4. CTX general. SOURCE [BGG 3459593].

12. `CHOOSE_OPTION` [`emmitt-blue-3b`] WHEN their unresolved card is an attack or a movement skill and every resolved card they could swap in is not (a spent skill, a defense already used, a coin card) THEN prefer the card swap over the place swap. WEIGHT +14 if the unresolved card would have defeated an ally, +8 if it would have taken the minion. WHEN they have a resolved attack they can legally replay THEN avoid the card swap. WEIGHT −10. They choose the resolved card. CTX general. SOURCE [BGG 3459593] rulings/emmitt.md 12.

13. `CONFIRM` [`emmitt-green-2b`, `emmitt-green-3b`] WHEN he or an ally in radius discarded a card this turn that is still in the discard THEN that hero retrieves. WEIGHT +8 per card retrieved that is an attack or a Defense 5+, +3 per other card. Do not retrieve a discarded defense whose active effect is still the reason an ally is alive (retrieving ends it). WEIGHT −10 for that one. Cards discarded later this turn are invisible to this action. CTX general. SOURCE [BGG 3459593] [Discord Arty 2026-06-05] rulings/emmitt.md 10.

14. `CHOOSE_OPTION` [`emmitt-green-3b`] WHEN it is reverse and an ally in radius 4 is inside an enemy attack that has not resolved THEN the immunity bullet, not the retrieve. WEIGHT +14 per such ally. He himself gains nothing. WHEN it is forward and the discards have already happened THEN the retrieve (rule 13) unless a still-unresolved enemy attack reaches an ally in radius, in which case immunity. CTX general. SOURCE [BGG 3459593] [BGG 3687772].

15. `CHOOSE_CARD` [`emmitt-purple-4`] WHEN both played cards are revealed THEN retrieve the one whose primary has no legal target. If both work, retrieve the one he still needs as a block (`emmitt-silver`, or the red if the silver is the card that should resolve). WEIGHT +10 for binning a whiff, +6 for putting Defense 6 back in hand when an attack is still unresolved. Do not play a second card at all if the only Defense 6 would have to resolve and he is in range of two enemies. CTX general. SOURCE rulings/emmitt.md 13 [REASONED].

16. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN both red and blue are still tier I THEN upgrade red first if he is the team's only melee threat, else blue. Keep §3's branch. WEIGHT +6 for the first Attack item on the punch path (Attack 9 becomes 10, which is the difference against a Gold at 11 with one minion). WEIGHT +4 for a Movement item while his movement actions are still 2. CTX general. SOURCE [BGG 3606834] [REASONED].

17. `KEEP_UPGRADE` [`emmitt-red-2b`, `emmitt-red-3b`] WHEN kept THEN stop scoring "stand adjacent and punch" as the plan. idealDistance stays 1 only on the turn a Glitch swap or Time Loop can make him adjacent; otherwise 2. CTX hero (branch flag). SOURCE [BGG 3459593].

18. `PLAY_CARD` [`emmitt-silver`] WHEN it is reverse and initiative 1 (plus items) will act before every enemy attack that reaches his current hex THEN prefer silver if rule 5 has a legal set of landings. WEIGHT +10. WHEN it is forward and he is not in danger THEN avoid. WEIGHT −6. Holding it is the default [ADVICE]. CTX general. SOURCE [BGG 3459593] [ADVICE].

19. `PLAY_CARD` [`emmitt-green-2b`] WHEN it is reverse and the only discards so far are from enemy Greens, and Future Proof's immunity is not the card he holds THEN avoid capsule. WEIGHT −5. The Golds have not hit yet. CTX general. SOURCE [BGG 3459593].

### evalTerms

- Reverse Time in force, and at least one of his allies has an unresolved card of initiative ≤ 7 that reaches an enemy who has no unresolved card of initiative ≤ 7: +8. The slow hit is now first and they have no slow escape.
- Reverse Time in force, and an enemy has an unresolved card of initiative ≤ 7 that reaches one of his allies who has no unresolved card of initiative ≤ 6: −8. The flip is hurting his team. Both can be true for different heroes; sum them.
- Future Proof's immunity in force: +8 per other friendly hero currently in radius who is also in an unresolved enemy attack's reach. Emmitt himself is 0.
- Glitch tokens on the board: +2 per token that is a bad hex for an enemy who may still be forced to swap this turn, until end of turn. 0 if the only swap is his own silver and he is the one who will land there (that was a play, not a piece he owns).

### threatAdjust

When Emmitt is the enemy:

- While `emmitt-red-1`/`2a`/`3a` is in his possible pool, an adjacent hex is not a normal Attack 9/11/12. The defender's total is initiative-based, as in rule 3. Add +10 to the cost of standing there with only a slow defensive card left. A Block card is not this penalty.
- While Reverse Time is in force, low-initiative cards are the fast ones. A hex he can reach with a Green shove (range 3, then 4, straight 2) is threatened even though he has no attack range there. Add +6 while `emmitt-green-1`/`2a`/`3a` is in his possible pool and the hero on that hex has remained.
- While `emmitt-blue-2b`/`3b` is in his possible pool, his hex is contagious out to range 4 for any hero who has already resolved a card: standing in a good hex after acting can mean he takes it. Add +5.
- `emmitt-green-3b` immunity does not include him. Focus him, not the ally in the radius, on that turn.
- His gold does not threaten next round. A turn-4 Reverse Time adds no future-order threat.

## 7. Sources

- Catalog `emmitt.json` (2nd printing), including the `advice` field from yvo-niedrich/goa2-faq. Cited as [ADVICE].
- `EmmittHero.kt` and `core/game/rulings/emmitt.md` for the decisions and the engine readings (remained, already resolved, Glitch spacing and supply, "+N Defense" still counts, Ultimate retrieve after the reveal).
- [BGG 3459593] Cryomancy, 2025-02-11, "Emmitt Hero Spotlight (2nd Printing)", community. Both orders for every card; he chooses which enemy picks the Glitch.
- [BGG 3606834] Cryomancy, 2025-11-07, "Emmitt Build & Go Guide", community. Captured In Time and Temporal Warrior. One post, no replies.
- [BGG 3687772] nichik, 2026-04-03, designer: Reverse Time's "ignores immunity" is only the initiative order. It does not cancel Future Proof.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Emmitt as Disruptor/Support.
- Gamefound, Wolff Designa, "Wayward Hero Pack overview" (https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/9): official. Reverse Time lets a slow card go first, to save an ally or to catch a hero with no slow card; it also endangers allies. The device is an unreliable teleport, worse when he is hit.
- Designer rulings at https://www.cockeyedandcurious.com/goa2/faq (Arty, Discord): the punch replaces the Defense of the card and of Defense items and nothing else (2025-07-09); "remained" means they stayed this turn (2025-02-15); Time Capsule sees only discards already made (2026-06-05); a swap into the discard is not a discard (2026-01-18); on Flashback he picks the hero and they pick the token at any range (2026-07-19).
- [BGG 3481942] tier-list thread, 2025-03-20: Emmitt appears in personal lists only. No rule taken from it.
- No Emmitt-specific Reddit thread or transcribed video was found. 1st-printing card text was not found in the spotlights; the 2022 wiki pages are other heroes. Nothing older was needed, and nothing older was used for numbers.

## Implementation notes

What `heroes/EmmittHeuristics.kt` encodes, checked against the engine (2026-11-21).

- Implemented: H2 (no Reverse Time on turn 1), H3 (the punch: the defender answers with the Initiative of a defense card plus Initiative items and minion modifiers, so it is scored by the chance their hand holds a card that fast; replaces the general strike estimate and the target's value), H6 (the Glitch is picked by an enemy who has acted), H10 (the traps by the chance an enemy in range resolves before him: a faster card forward, a slower one in Reverse Time; or-defeated on Time Bomb).
- Engine: the punch calls `attack(defenseFromInitiative = true)`; a Block card still blocks. Reverse Time is an active effect of `Timing.NEXT_TURN`.
- Draft fix: the draft read `turnRecord.resolved` at card play, which is always empty then, so the traps were always discouraged.
- Skipped: H1 (the flip's worth), H4, H5 (Glitch placement), H7–H9 (glitch swaps, shoves), H11–H19, evalTerms, threatAdjust (the general threat map already scores the punch's printed Attack).
