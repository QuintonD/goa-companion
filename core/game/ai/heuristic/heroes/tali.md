# Tali the Spiritcaller (`tali`) — heuristic playbook

2nd printing. Complexity **3**. Sheet: Attack 4–6, Defense 4–4, Initiative 5–8, Movement 5–5. Sheet roles: damager, pusher, healer, durable, tokens. Card numbers only. Gold is Attack 3. The a-reds are Attack 2 / 3 / 4 **plus 3** when the card is not in the discard, so a normal play from hand is 5 / 6 / 7.

**The 1st-printing spotlight BGG 2893302 (2022-07-02) is obsolete.** Gold is not "Turn Into Ice." Blue is not a named-colour discard. The reds are not "discard a colour, they get it back." The ultimate does not punish retrieves. Ice tokens are not required to be adjacent to each other (that line is not on `tali-green-2a` or `tali-green-3a`; a 2024 designer note about "adjacent if able" belongs to the old text). Totems are not optional and not all immune. Do not encode any of that.

What still matches: she is a team hero; silver is the card that needs the ally; discarding on purpose leaves her open; a 1-point initiative swing decides races. The current silver is stronger and stranger than the old one, and the FAQ (2026-10-18) is the rule for it.

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `Support` |
| `idealDistance` | `1` on the a-red and on gold; `2` while `tali-red-2b` / `tali-red-3b` is the attack she will play from hand |
| `aggression` | `0.45` |
| `laneWeight` | `0.55`, and `0.70` if she kept the totem branch |
| `keepDefender` | true — `tali-red-1` / `2a` / `3a` (Defense 6 / 6 / 7). The b-reds are Defense 3 / 4. Gold is Defense 1. |
| `upgradeOrder` | red a first (it is the attack and the block), then ice or totem, then the blue that matches |

[ADVICE]: the basic attack likes Attack items more than most. Gold is 3, the b-reds are 3, and the a-red's printed Attack before the +3 is 2–4. Items are what push a splash or a shot over a Defense.

## 2. Card-by-card

Ice is a property of the token, not an Active effect. Every Ice token of hers gives each adjacent **enemy** hero −1 Initiative, and the penalties add up, including across Razzle's figures when order is checked (designer, Discord 2026-07-03: two on one figure and one on another is −3). Friendly heroes are not slowed. Initiative is read again before each hero acts. Supply is 3; placing beyond that removes an older Ice first (`rulings/tali.md` 7). Ice is an obstacle.

Totems keep the rules of the card that placed them. One totem in the supply. "Would be defeated" is not a Minion Battle removal, and it is not optional. No coins, and effects that follow a defeat do not happen (catalog FAQ; designer, Discord 2026-09-07).

| Id | Init | For | Play when | Do not | Hold |
|---|---|---|---|---|---|
| `tali-gold` Ice Blast | 11 | Attack 3, adjacent. If the target discards to defend — even a failed defense — another enemy hero in radius 4 discards a card of that **same colour**, from hand, if they have one. Played or resolved cards are not discarded (BGG 3521577, `nichik`, 2025-06-02). | Two enemies are in radius 4, the target must spend a card (the total beats their remaining Defense, or they will spend one anyway), and the second still holds that colour. A minion only if the coin matters; a minion's "discard" does not happen, so there is no splash. | Attack 3 into a hero who ignores it, with no second hero. That is a weak minion tool that spent the splash. | Defense 1. Not the block. |
| `tali-handicap` Ice Shard | 10 | Same splash, hero target only, radius 3, Attack 2. | Same, shorter radius. | A minion. | Defense 1. |
| `tali-silver` Commune with Spirits | 13 | A friendly hero **on the board** names a colour (gold, silver, red, green, blue). She may discard a card of that colour from hand. She **must** then perform a performable primary of that colour from the discard. The ally can be instructed out loud, including "say red if they played gold" (catalog FAQ). No secret codes. If every ally is dead or immune, the card does nothing. | The discard already holds the colour she wants, or her hand holds it and performing it **from the discard** is still good. Init 13 is first, so the replay happens before almost everyone. | A colour with no performable primary in the discard and none in hand. The mandatory perform would have no card. Naming red-a just to replay it: the +3 is off, because it is in the discard. | Defense 2. The reason to keep it is the replay, not the block. |
| `tali-red-1` / `2a` / `3a` Winter Dagger / Spear / Scepter | 8 / 9 / 9 | Adjacent, Attack 2 / 3 / 4, **+3 if this card is not in the discard**. From hand that is 5 / 6 / 7. From a silver replay it is the low number. | The hand attack. This is also the block (Defense 6 / 6 / 7). | Discarding it to silver for damage. The replay is the weak version. | The defender, until the turn she attacks with it. |
| `tali-red-2b` Spirit Wolf | 9 | Choose: if not in the discard, a unit in range 2; or a hero adjacent. Both are ranged (the card is). Attack 3, Defense 3. | From hand, the range-2 shot. From the discard (silver), only the adjacent hero, because the first bullet fails. | Expecting the range bullet after silver has put it in the discard. | Defense 3. Worse block than the a-red. |
| `tali-red-3b` Spirit Bear | 9 | One bullet, or **both** on different targets, in either order. Not the same bullet twice (designer, Discord 2026-06-02). | From hand: a unit at range 2 and a different adjacent hero. From the discard: the adjacent hero only, unless she has not discarded it. | Both bullets on one target. | Defense 4. |
| `tali-green-1` Glacial Barrier | 4 | Place 1 Ice in radius 3. | See the ice rules below. Init 4 is late on the turn she plays it, so turn-1 ice mostly shapes **later turns**. | On a friendly hero's square; it does not slow them, and it is an obstacle. | Defense 3. |
| `tali-green-2a` Wall of Frost | 3 | Up to 2 Ice, radius 3. | Same. | Sprinkling one each on heroes she does not need to delay. | Defense 3. |
| `tali-green-3a` Pack Ice | 3 | Up to 3 Ice, radius 3. | Stack on the hero whose initiative she must flip. | Filling the supply and deleting a token that was already on the important hero. | Defense 4. |
| `tali-green-2b` Ancestral Totem | 3 | Place a totem in range 2. A friendly **melee** minion adjacent to it, if it would be defeated, removes the totem instead. The totem can be Cleared. | Adjacent to the melee minion an enemy is about to defeat, on the side toward that enemy. | A ranged minion (it does not protect them). A minion that is only going to be removed in the Minion Battle. Replacing the totem every turn. | Defense 3. |
| `tali-green-3b` Venerated Totem | 3 | Any friendly minion, and the totem is immune to enemy actions, including Clear. | The minion that holds the lane, melee or ranged. | Same Minion Battle mistake. | Defense 4. |
| `tali-blue-1` / `2a` Cold Snap / Snowstorm | 9 / 10 | Choose a direction. Every enemy unit in radius 2 / 3 moves 1 that way, simultaneously, if the next space is empty (`rulings/tali.md` 4). Immune units stay. The direction is always chosen, so the primary always exists. | The push puts a hero off a minion, stacks two heroes inside gold's radius 4, or slides a minion toward her side of the lane. | A direction that pushes a hero into a better attack on her ally. | Defense 5. A solid spare. |
| `tali-blue-3a` Blizzard | 10 | Same, radius 3. If this card **is** in the discard, end of turn she may repeat once. | From hand, the push. From silver, the push **and** the end-of-turn repeat. That is the replay to aim at. | — | Defense 6. |
| `tali-blue-2b` Guardian Spirit | 10 | Up to one ally in radius 4 may retrieve. If this card is in the discard, she **must** retrieve it. | An ally has a discarded card. From silver: they retrieve and Guardian comes back to her hand in the same action. | Herself as the "ally." No ally with a discard: the "up to one" may be nobody, and if the card is not in the discard the action did little. | Defense 5. |
| `tali-blue-3b` Warrior Spirit | 10 | Up to one ally may retrieve. If this card is in the discard, **she** may retrieve a card. | Same, and from silver she can also take something else back. | — | Defense 6. |
| `tali-purple-4` Reign of Winter | — | After she defeats a minion with a **basic** attack (gold, including gold performed from the discard), an enemy in radius 4 discards a colour she names. Radius items apply. A minion defeated-but-not-removed (Brogan's Shield) still counts. A totem save does not. | Gold on a minion while an enemy in radius 4 holds a colour she wants gone. | Expecting it on a red. Reds are not basic. | — |

Silver's performer is the ally, via `CHOOSE_OPTION` "Name a color…", `sourceCardId` `tali-silver`. Hook the colour rule on that source card. The ally sees the revealed cards. Opponents hear the plan. There is no hidden "say blue."

## 3. Upgrade path

**Red.** Keep `tali-red-2a` / `tali-red-3a` by default. Attack from hand 6 then 7, Defense 6 then 7. Tuck `tali-red-2b` (Defense item) and `tali-red-3b` (Defense item). [ADVICE] on Attack items: she still wants them from other colours, because gold and the +3 threshold are where fights are decided. The a-red's own tuck is Defense, which a Defense-4 hero needs.

Keep `tali-red-2b` / `3b` when she must shoot a minion at range 2 and can do Spirit Bear's double hit. Tuck `tali-red-2a` (Initiative) and `tali-red-3a` (Radius). Defense 3 then 4 is a worse life. The Radius item widens gold's splash and the ultimate and Cold Snap. Take this only if someone else blocks.

**Green.** Keep ice (`tali-green-2a`, `tali-green-3a`) when the enemy has a hero whose initiative sits next to hers or her ally's (a gold at 11 against her gold at 11, a red at 9 against hers). Tuck `tali-green-2b` (Attack) and `tali-green-3b` (Attack). Those Attack items are the ones the advice is about.

Keep the totem when the lane is the win and a single minion is the margin. `tali-green-2b` is melee only and Clearable, so it is the weaker token; `tali-green-3b` is the one that holds a point. Tuck `tali-green-2a` (Initiative) and `tali-green-3a` (Initiative).

**Blue.** Keep `tali-blue-2a` / `3a` to stack heroes for the splash and to have a discard-replay that pushes twice (Blizzard). Tuck `tali-blue-2b` (Attack) and `tali-blue-3b` (Range).

Keep `tali-blue-2b` / `3b` when allies are being stripped and the retrieve is the team's health. Tuck `tali-blue-2a` (Defense) and `tali-blue-3a` (Movement). Guardian replayed from silver returns itself; that is the safe double-use.

**Phase.** Red a at the first level (block and damage). Green second: ice if fights are close on initiative, totem if the last two waves were lane losses. Blue third. She wants an Attack item before tier-3 vanity. Silver and gold do not upgrade. The Ultimate makes **gold on a minion** a colour strip in radius 4; that is a reason to farm with gold once it is on, not a reason to skip the a-red.

## 4. Synergies and counters

**With:** an ally who will name the colour, on a pre-agreed rule the table can hear: "the colour of the card already in the discard, unless I still hold Blizzard." Brogan or another melee who wants to act before a faster hero, which is what a stack of Ice is for. A pusher who profits when Cold Snap slides a minion. She should not be the only finisher: Attack 3 on gold does not close, and the a-red closes only from hand at 5–7.

**Fears:** a one-shot that lands before she chooses to discard (Defense 4, gold Defense 1). Clear on an Ancestral Totem. Enemies who do not stand together, so the splash and the push have one target. Razzle is a special case: Ice on several figures stacks into one initiative, which is either a lock or a waste if she did not mean to spend the whole supply on one hero.

**Against her:**

- Do not stand in a pair inside radius 4 if you share a colour and her gold can force the first discard. Split.
- Step off Ice before your key card if the −1 loses the race. The token is still an obstacle after you leave.
- Clear `tali-green-2b`. You cannot Clear `tali-green-3b`.
- A minion next to a totem is a trap: killing it spends the totem, pays no coins, and does not move Onslaught. Kill a different minion, or hit the minion only when the totem is already gone.
- Silver at 13 replays a discard. Do not hand her a gold or a Blizzard if silver is still in hand and an ally is alive to name it. Discarding her **a-red** is less scary: the replay loses the +3.
- Cold Snap moves everyone in the radius at once. Do not stand in a line that slides your hero onto her ally and your minion into her farm, if you can stand outside radius 2–3.
- The ally names the colour in the open. You hear the plan before the turn if they discussed it, and you see the name as the card resolves.

## 5. Common mistakes

- Replaying Winter Spear with silver for "a big attack." It is Attack 3, not 6.
- Placing one Ice next to three different heroes and flipping none of them.
- Placing a totem next to a ranged minion on the tier-2 card.
- Expecting the totem to save a minion in the Minion Battle.
- Gold on a lone hero at Attack 3.
- Naming a colour that is only on a resolved card. Discards come from hand (BGG 3521577).
- Offering Guardian Spirit to herself.
- Both Spirit Bear bullets on one target, or the same bullet twice.
- Treating ice as slowing her own team.
- Playing green at init 3 and expecting it to change heroes who already acted this turn. It changes the next turns.

## 6. Heuristic rules

`CTX hero` is her Ice tokens, her totem, and which of her cards are in the discard versus resolved versus hand.

`evalTerms`: +2 for each Ice adjacent to an enemy hero whose initiative, after the penalty, falls below an ally who still has not acted and who would otherwise act after them. +0.5 for an Ice that is only an obstacle. Cap the stack's value at +6 on one hero (a −3 is the whole trick; more tokens are supply). +the minion's lane value (up to 15 if it is the margin, else 5) for a totem adjacent to a friendly minion it can legally save, **once** (it eats one defeat). 0 if the minion is not in any enemy's reach. Do not give every Ice a flat "token = good" (the weakness `AI.md` names for set-up heroes).

`threatAdjust` (she is the enemy): a space adjacent to her Ice is slower for the hero who stands there: add +3 if the −1 flips a race against a teammate of hers who has not acted, else +1. Gold's splash is not printed reach: a second enemy in radius 4 is threatened for a card **if** the first target will discard, +2.5. Cold Snap / Blizzard threaten a 1-hex push of every enemy in radius, not damage: +2 on spaces in that radius when the push would move a hero off a minion or into her gold. Reign of Winter, once on, adds a colour discard in radius 4 after she takes a minion with gold.

1. `CTX hero` [CHOOSE_SPACE] [`tali-green-1`, `tali-green-2a`, `tali-green-3a`] WHEN an enemy hero's printed initiative is within 2 of hers or an ally's, and that hero has not yet acted on a later turn THEN place as many Ice as the card allows adjacent to that one hero, up to the supply of 3. A token that touches two such heroes is better than one that touches one. Do not place adjacent to a friendly hero's only exit. WEIGHT 4 per flipped race, else 0.5. SOURCE card text; the 1st-printing spotlight's "a 1-point swing matters" (the only part kept); designer Discord 2026-07-03 for Razzle stacking.
2. `CTX hero` [PLAY_CARD] [`tali-green-1`, `tali-green-2a`, `tali-green-3a`] WHEN turn ≥ 2 AND no enemy initiative is close AND the lane is the fight THEN do not spend another green on ice; she should have taken the totem, and if she did not, the card is a movement. WEIGHT 2. SOURCE [REASONED].
3. `CTX hero` [CHOOSE_SPACE] [`tali-green-2b`, `tali-green-3b`] WHEN a friendly minion the card can protect (melee only, for 2b) is in an enemy attack's range THEN place the totem adjacent to that minion, between them if the space exists. ELSE do not replace an existing totem that is already doing this. WEIGHT the saved minion (5, or 15 if it is the margin), once. SOURCE catalog FAQ; designer Discord 2026-09-07.
4. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`tali-gold`, `tali-handicap`] WHEN a second enemy hero is in radius (4, or 3 on the handicap) AND the target must discard to defend AND that second hero holds the colour the target is most likely to spend, in hand, not already played THEN gold on that target. Prefer the target whose defense colour the second hero also holds. WEIGHT 2.5 for the splash plus the attack. SOURCE card text; BGG 3521577.
5. `CTX general` [PLAY_CARD] [`tali-gold`] WHEN no second enemy is in radius OR Attack 3 + items meets nobody's Defense THEN do not spend gold on a hero. A minion is correct only if it is the margin or the Ultimate will strip a colour off an enemy in radius 4. WEIGHT 15 or 2.5, against 0 for a poke. SOURCE [ADVICE] (Attack items; know when you can afford to discard); [REASONED].
6. `CTX general` [CHOOSE_UNIT] [`tali-red-1`, `tali-red-2a`, `tali-red-3a`] WHEN the card is in hand (not the discard) THEN the attack is 5 / 6 / 7 plus items. Take a hero that loses to that, else a minion. WHEN the card is being performed from the discard THEN it is 2 / 3 / 4; take it only if that lower total still defeats the target or the minion is the margin. WEIGHT the kill or the minion. SOURCE card text; `rulings/tali.md` 6.
7. `CTX general` [CHOOSE_OPTION, CHOOSE_UNIT] [`tali-red-2b`, `tali-red-3b`] WHEN the card is not in the discard AND a unit is in range 2 THEN the ranged bullet; on Spirit Bear add the adjacent hero if they are a different unit and the attack matters. WHEN the card is in the discard THEN the adjacent-hero bullet only. WEIGHT as the attack. SOURCE designer Discord 2026-06-02; `rulings/tali.md` 8.
8. `CTX hero` [CHOOSE_OPTION] [`tali-silver`] WHEN the ally names a colour THEN name the colour of the performable discarded primary with the best rule-6, rule-7, rule-9 or rule-10 result. If the best card is in hand and not in the discard, name it only when performing it from the discard is still the best action (Blizzard's repeat, Guardian returning itself, ice placed at init 13, gold's splash). Do not name a-red for damage. If nothing of that colour can be performed, name a different colour. WEIGHT the replayed action. SOURCE catalog FAQ (2026-10-18: the discard is optional, the perform is not, and it can happen with an empty hand if the discard already has the card); `rulings/tali.md` 5.
9. `CTX hero` [CONFIRM, CHOOSE_OPTION] [`tali-blue-3a`] WHEN Blizzard is performed from the discard THEN take the end-of-turn repeat if a second push still moves an enemy. WEIGHT 4. SOURCE card text; `rulings/tali.md` 6.
10. `CTX general` [CHOOSE_OPTION] [`tali-blue-1`, `tali-blue-2a`, `tali-blue-3a`] WHEN a direction moves an enemy hero off a minion, or moves two enemy heroes into gold's radius, or moves an enemy minion toward her side, AND does not move an enemy hero onto her ally THEN that direction. Units move at once; do not plan an order. WEIGHT 4, or 15 if the minion is the margin. SOURCE `rulings/tali.md` 4 (they move at once). The cockeyed FAQ says the same ("it happens at the same time"); the scrape does not put a date on that sentence alone.
11. `CTX general` [CHOOSE_UNIT, CONFIRM] [`tali-blue-2b`, `tali-blue-3b`] WHEN an ally in radius has a discarded card they need THEN offer it. If the spirit card itself is in the discard, Guardian is retrieved automatically and Warrior should retrieve if she has a card she wants. WEIGHT 2.5, plus another 2.5 on Warrior. SOURCE `rulings/tali.md` 11.
12. `CTX general` [DEFEND] [`tali-red-1`, `tali-red-2a`, `tali-red-3a`] WHEN a blue meets the attack THEN spend the blue and keep the a-red for the hand attack. WHEN only the a-red meets it THEN spend it. Discarding it on purpose is legal and turns on a later silver replay at the **low** Attack; do that only when the replay's other text (not the damage) is the plan. WEIGHT 6 for keeping a 7-Attack card. SOURCE [ADVICE] "know when you can afford to discard"; [REASONED].
13. `CTX hero` [CHOOSE_UNIT, CHOOSE_OPTION] [`tali-purple-4`] WHEN gold defeats a minion AND an enemy in radius 4 holds a colour in hand THEN name that colour, preferring the defender. WEIGHT 4. SOURCE card text. Not on a red kill.
14. `CTX general` [CHOOSE_SPACE] [movement] WHEN gold is unplayed THEN end adjacent to a target who will be inside radius 4 of a second enemy. WHEN the a-red is the attack THEN end adjacent to the target and not in a −2 modifier she cannot block. Ideal distance 1, not the back of the zone. WEIGHT 3. SOURCE [ADVICE] (the team has to be on the same page; she is not a sniper unless the b-red is in hand).
15. `CTX hero` [PLAY_CARD] [`tali-silver`] WHEN the only performable replay is a weak a-red AND she could instead play that a-red from hand for +3 THEN play the a-red and keep silver. WEIGHT 3 (the +3 that is missing once the card is in the discard: 5/6/7 from hand, 2/3/4 from the discard). SOURCE card text; [REASONED].
16. `CTX general` [CHOOSE_TOKEN] [Clear, when she is the enemy] WHEN the token is an Ancestral Totem adjacent to a minion her team needs dead THEN Clear it before the attack. WHEN it is a Venerated Totem THEN it is not a legal Clear. WEIGHT the minion. SOURCE `rulings/tali.md` 3; designer Discord 2026-09-07. This is `threatAdjust`'s companion for the enemy's Clear choice.

## 7. Sources

- Catalog `tali.json` (cards, advice, three FAQ entries).
- `core/game/rulings/tali.md`, `heroes/TaliHero.kt`.
- BGG 3521577, `nichik`, 2025-06-02: the splash discards from hand only. https://boardgamegeek.com/thread/3521577
- Designer Arty, Discord 2026-06-02 (Spirit Bear), 2026-07-03 (Razzle ice stacks; also the Rowenna date, separate), 2026-07-16 (silver may perform a Clear), 2026-09-07 (totem is not a defeat), 2026-10-18 (discard on silver is optional; the perform does not need the card in hand). Cold Snap is simultaneous. https://www.cockeyedandcurious.com/goa2/faq
- Wolff Designa, Devoted overview. https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/6 — ally names the colour, communication limits, team hero. The "second-highest complexity" line is not the catalog (complexity 3).
- BGG 2893302 is 1st printing. Used only as a negative source. https://boardgamegeek.com/thread/2893302
- **No 2nd-printing strategy spotlight, Reddit thread, or YouTube guide found.**

## 8. Implementation notes

`TaliHeuristics.kt` (ADR-085). Checked against `TaliHero.kt`:

- **H8 (silver):** the colour is named by the *chosen friendly hero* (`chooser = namer`), so Tali's own book is asked only when she names it herself. The question is opaque, and the look-ahead scores the replay. The draft's colour scoring and its H15 "never silver while an a-red is in hand" were removed, because both fired without checking what the replay would be.
- **Spirit Wolf/Bear bullets:** the engine offers only the bullets that can start (out of the discard, only the adjacent-hero bullet), so the draft's target filter was removed.
- **H1/H3:** Ice and the Totem are placed through `CHOOSE_SPACE` from the green card. Ice goes next to enemy heroes, never into a friendly hero's last exits. The Totem goes next to a friendly minion it can save (melee only for Ancestral Totem) that an enemy can reach.
- **threatAdjust:** the draft counted Tali's own teammates as "a second enemy" (it used the deciding team's enemies). Fixed with the heroes of the other side from Tali.
- **Not implemented:** H2, H6, H7, H9–H11, H13–H16.
