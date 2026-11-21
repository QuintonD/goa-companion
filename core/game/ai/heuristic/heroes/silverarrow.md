# Silverarrow the Pathfinder (`silverarrow`) — heuristic playbook

2nd printing. Complexity **2**. Sheet: Attack 2–2, Defense **1–1**, Initiative 7–7, Movement 6–7. Sheet roles: sniper, damager, disabler, healer, farming. Defense 1 on the sheet is real: gold, silver and the greens are Defense 1–2. The reds are the only blocks (Defense 4–6).

**Rejected 1st-printing / campaign lines.** Wolff Designa's Devoted overview says most of her abilities stop working when enemies are close, and that gold cannot be used if any enemy is adjacent. That does not match these cards. Gold and the b-reds target a unit at **exact** maximum range (designer Arty, Discord 2025-05-01: range 3 means exactly 3, not "the farthest enemy"). An adjacent enemy does not turn the card off against a different unit at that distance. Snap Shot's second bullet exists **for** an adjacent target. The 1st-printing spotlight BGG 2891788 is not a source for when cards work.

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `Ranged` |
| `idealDistance` | `3` (2 while gold is the only attack; 4 once `silverarrow-red-3a` is the attack) |
| `aggression` | `0.35` |
| `laneWeight` | `0.40` |
| `keepDefender` | true — the red, and the a-red over the b-red because its Defense is higher |
| `upgradeOrder` | red a, blue a, green a, unless §3's branch switch is on |

She has to be close enough that allies get the shot and the heal, and far enough that Defense 1 is not in melee ([ADVICE]). Abandoning the fight to sit in the Throne loses the game.

## 2. Card-by-card

"At maximum range" is exactly the printed Range plus Range items (`rulings/silverarrow.md` 1).

| Id | Init | For | Play when | Do not | Hold |
|---|---|---|---|---|---|
| `silverarrow-gold` Shoot and Scoot | 12 | Attack 1, **exactly** range 2. After a connecting attack, she may Fast Travel to an **adjacent Zone** (not her own), both Zones empty of enemy units. This Fast Travel is not a Movement action: a movement ban does not stop it; a Fast Travel ban does. | A minion sits on the range-2 ring and at least one legal scoot space exists in an adjacent Zone, especially if this shot removes the last enemy unit in her Zone (the scoot is inside the action, the wave push is at the end of it — designer, Discord 2026-06-14). | A hero, unless Attack 1 + items meets every card they hold. Anyone closer than 2 or farther than 2. A scoot into a Zone that still has an enemy unit. | Defense 1. Almost never the block. |
| `silverarrow-handicap` Keep the Distance | 11 | Same shot, hero targets only, then the same scoot. | A hero on the ring. | Minions. | Defense 1. |
| `silverarrow-silver` Trailblazer | 13 | She may Fast Travel (own Zone or adjacent, normal rules). **This round,** she and friendly heroes in radius 4 ignore obstacles on **Movement actions**. Checked at the start of that move (designer, Discord 2025-02-23 and BGG 3505773). Not on card-text moves, not on Fast Travel. Zone borders are not obstacles (BGG 3592253, `nichik`-aligned replies, 2025-10-14 and 2026-06-14; BGG 3796272, 2026-11-10). | Turn 1, when a later Movement action (hers or an ally's) would be doubled by passing obstacles, and the Fast Travel itself lands her on a ring or beside the team. The round effect still happens if she declines the Fast Travel or takes it (community, BGG 3542316, cited on the cockeyed FAQ). | Trying to Fast Travel from one jungle to the other across the gap. The November 2026 opening that does this is illegal (BGG 3796272). Trying to Fast Travel through the stones by the enemy base. | Defense 1. The round effect is the reason to play it, not the Defense. |
| `silverarrow-red-1` / `2a` / `3a` Snap Shot / Opportunity Shot / Clear Shot | 9 / 9 / 10 | Choose: a unit in range 2 / 3 / 4 that is **not adjacent to any other unit** (she herself counts, so a unit next to her fails this bullet), or a unit adjacent to her. Both bullets are ranged. Attack 3 / 4 / 5. Defense 5 / 5 / 6. | The isolated bullet on a minion or a hero standing alone on the ring. The adjacent bullet only to finish something already touching her, then leave. | An isolated call on a unit standing next to a minion, an ally, or her. | The defender. |
| `silverarrow-red-2b` Long Shot | 9 | Attack 3, exactly range 3. Defense 4. | A unit on the ring. | Anything closer, including a hero who stepped in. | Defense 4. Weaker block than the a-red. |
| `silverarrow-red-3b` Rain of Arrows | 10 | Attack 3, exactly range 3. If the first target is a hero, she **must** repeat on a different hero on that ring; if that repeat happens, she may repeat on a minion. No second hero on the ring: the minion repeat is not offered (`rulings/silverarrow.md` 9). | Two heroes on the ring, or one hero and then a minion. | A single minion when an a-red would have hit it at Attack 5. Expecting closer targets (designer, Discord 2025-02-08). | Defense 5. |
| `silverarrow-green-1` Disorient | 3 | Move an adjacent enemy unit 1; if it moved, she may move 1. | She is adjacent and needs to peel. The primary does not start if nothing adjacent can move (`rulings/silverarrow.md` 5). | As a travel tool with nobody adjacent. Take the Movement action (2). | Defense 1. |
| `silverarrow-green-2a` / `3a` Divert Attention / Lead Astray | 2 / 2 | Move an adjacent enemy up to 2 / 3. If it moved, she moves that many spaces in a straight line (the path length, `rulings/silverarrow.md` 6). | The enemy's landing space is worse for them and her straight line ends at range, not adjacent. | A 0-space "move" (the "if you do" fails and she stays). A line that ends next to a second enemy. | Defense 2. |
| `silverarrow-green-2b` Fae Healing | 2 | A hero in radius 4 — friend or enemy, **never her** — may retrieve. If they do, they gain 1 coin. | An ally with a discarded card they will take. | An enemy. They decide, and they decline unless the card helps them. Naming them and having them decline spends the card (BGG 3491927; `rulings/silverarrow.md` 7). | Defense 2. |
| `silverarrow-green-3b` Nature's Blessing | 2 | Same, 2 coins. | An ally whose retrieve also completes a level (the 2 coins). | Herself. An enemy. | Defense 2. |
| `silverarrow-blue-1` / `2a` / `3a` Grasping Roots / Entangling Vines / Grappling Branches | 10 / 10 / 11 | **This turn:** enemy heroes in radius 2 / 3 / 4 cannot Fast Travel or move more than 1 with a **Movement action**. Checked at the start of the move (catalog FAQ). Not card-text moves, not Brogan's Mad Dash (it is an attack — designer, Discord 2025-02-09). | She resolves before the dasher (her init is higher than their revealed card) and they are in radius. Slower golds and greens are the ones still to act. | Heroes who already acted. A movement-capped hero whose card is not a Movement action. | Defense 3 / 3 / 4. A real spare block. |
| `silverarrow-blue-2b` Warning Shot | 10 | **End of turn:** an enemy hero in radius 3 discards, if able. She picks; immune heroes are not picked. Measured from where she is at the end. | A hero who has **already resolved** is still in radius 3, or a hero who acts after her cannot leave radius 3. | A hero who acts after her with a move that exits radius 3. They walk out and the end step whiffs if nobody else is left. | Defense 3. |
| `silverarrow-blue-3b` Treetop Sentinel | 11 | End of turn: discard **or be defeated**. Radius 3. | Same, and the hero has no card. That is a kill at the end of the turn. | Same whiff. | Defense 4. |
| `silverarrow-purple-4` Wild Hunt | — | Before **every** action, including each repeat and the Defense action, she may move exactly 2 in a straight line. Card-text move, not a Movement action: Roots does not cap it, and Trailblazer does not let it ignore obstacles (`rulings/silverarrow.md` 10). | Step onto the exact-range ring, or off an adjacent enemy, before the shot or before the block. On Rain of Arrows, once before the action and once before each repeat (designer, Discord 2025-06-03). | A 1-space nudge. It is 2 or nothing. A line into a space adjacent to a hero when Defense is still 1. | — |

## 3. Upgrade path

**Red.** Keep the a-line (`silverarrow-red-2a`, `silverarrow-red-3a`) unless a teammate is the bodyguard. It is Attack 4 then 5, range 3 then 4, Defense 5 then 6, and it can shoot an isolated unit **or** something adjacent. Tuck `silverarrow-red-2b` (Initiative item) and `silverarrow-red-3b` (Initiative item).

Keep the b-line only when the team wants two heroes shot at once (`silverarrow-red-3b`) and someone else can block for her. Tuck `silverarrow-red-2a` (Defense item) and `silverarrow-red-3a` (Radius item). Attack stays 3. Defense 4 then 5 is a worse life on a Defense-1 hero.

**Blue.** Default `silverarrow-blue-2a` / `silverarrow-blue-3a`: the lock is how she stops a dasher reaching Defense 1. Tuck `silverarrow-blue-2b` (Attack) and `silverarrow-blue-3b` (Range). The Range item moves every "exact range" ring out by 1, which is the sniper's item.

Keep `silverarrow-blue-2b` / `3b` when enemies end their turns inside radius 3 and cannot leave (slow heroes, cluttered zones). Tuck `silverarrow-blue-2a` (Defense) and `silverarrow-blue-3a` (Defense). Two Defense items are how b-red survives.

**Green.** Default `silverarrow-green-2a` / `3a` (the peel). Tuck `silverarrow-green-2b` (Attack) and `silverarrow-green-3b` (Attack). Keep the heal when an ally is a discard engine who needs the card and the coins (Takahide's partners, a stripped finisher). Tuck `silverarrow-green-2a` (Initiative) and `silverarrow-green-3a` (Movement).

**Phase.** Red first (it is the Defense and the damage). Then blue a if the enemy team is mobile, else the Range item's branch as above. Green last. She should be at tier-3 red before she is at tier-3 green.

## 4. Synergies and counters

**With:** an ally who stands in the fight so she does not have to. Trailblazer's radius 4 is for that ally's Movement action through rocks, not for their Fast Travel. A finisher who converts the end-of-turn discard (`silverarrow-blue-2b`) or the Roots lock. Rowenna's turn-1 strip into her shot is the pairing CCplusplus names on Rowenna's thread.

**Fears:** any hero who can end adjacent to her. Attack 2 on the sheet and Defense 1 means almost every red kills her if red is already spent. Ignore-obstacle charges and placement effects (they do not care about Roots). Being on the wrong side of a wave with no Fast Travel out.

**Against her:**

- Step off the exact-range ring. Gold and b-red cannot shoot you at any other distance. A-red cannot shoot you if you are adjacent to a minion, unless you are adjacent to her.
- Stand next to her only if you can kill her. She does not want you there, and Disorient / Lead Astray is how she leaves.
- Roots does not stop Mad Dash, Blink Strike, Wild Hunt, or a push. It stops Fast Travel and long Movement actions of heroes who have not yet acted.
- Do not Fast-Travel into her Zone if she is the enemy unit standing in it.
- End of turn, leave radius 3 if Warning Shot / Treetop Sentinel was her card and you act after init 10–11. If you already acted, you are the target.
- Trailblazer does not let anyone Fast Travel through walls or across non-adjacent zones.

## 5. Common mistakes

- Calling gold "the farthest unit in range." It is exact.
- The jungle-to-jungle Fast Travel opening (BGG 3796272). Illegal, and the thread's author conceded the zones are not adjacent.
- Shooting a-red's isolated bullet at a unit next to a minion.
- Playing green's primary with nobody adjacent.
- Offering Fae Healing to an enemy, or to herself.
- Holding red as if it were a bad Defense. It is the only Defense.
- Scooting into a Zone that still contains a minion.
- Playing Roots after the dasher has already moved.
- Using Wild Hunt as a 1-hex step, or expecting it to pass through obstacles because silver is active.

## 6. Heuristic rules

`evalTerms`: none of her tokens persist. +3 while Trailblazer is active this round and an ally in radius 4 has a Movement action still unplayed that gains at least 2 spaces by ignoring obstacles. +2 while an enemy hero who has not acted is inside her Roots radius and Roots is active this turn. 0 otherwise (do not price "she is far away" here; that is the exposure term).

`threatAdjust` (she is the enemy): for gold and b-reds, only the exact-range ring is threatened — spaces strictly inside that ring get **no** added threat from those cards, and the general "within range" smear should be reduced by 4 on those cards so the bot does not fear a shot that cannot be declared. A-red threatens the disk **except** units adjacent to another unit, which it threatens only if they are adjacent to her. With the Ultimate, add the two-hex straight lines that land on a threatened space (she may step before the shot). `silverarrow-blue-3b` threatens a kill at end of turn in radius 3, which is not an attack range: +the kill on enemy heroes in that radius who have one or zero cards, while that card may be the one she played.

1. `CTX general` [CHOOSE_UNIT] [`silverarrow-gold`, `silverarrow-handicap`, `silverarrow-red-2b`, `silverarrow-red-3b`] WHEN the unit is not at exact Range (items included) THEN it is not a target. Prefer a minion on the ring over a hero unless the engine total meets every card that hero can still hold. WEIGHT the minion, or the kill. Attack 1 on gold does not meet a Defense 3. SOURCE card text; designer Discord 2025-05-01.
2. `CTX general` [CONFIRM, CHOOSE_SPACE] [`silverarrow-gold`] WHEN the attack connected AND a space in an adjacent Zone is a legal Fast Travel THEN take it if that space is at ideal distance from the next minion or is the Zone the wave will not strand her behind. Decline if both Zones are not empty. WEIGHT 5. SOURCE hero FAQ; designer Discord 2026-06-14; `rulings/silverarrow.md` 2.
3. `CTX general` [PLAY_CARD, CHOOSE_SPACE] [`silverarrow-silver`] WHEN a later Movement action of hers or of an ally now in (or Fast-Travel-able into) radius 4 gains 2 or more spaces through obstacles THEN play it, and Fast Travel only to a legal space. Never choose a destination across a non-adjacent gap. WEIGHT 4. SOURCE BGG 3592253; BGG 3796272 (McGravityHammer, 2026-11-10, and the replies that the opening is illegal); BGG 3505773.
4. `CTX general` [CHOOSE_OPTION, CHOOSE_UNIT] [`silverarrow-red-1`, `silverarrow-red-2a`, `silverarrow-red-3a`] WHEN a unit in range is adjacent to no other unit (she counts) THEN the isolated bullet. WHEN the only kill is adjacent to her THEN the adjacent bullet, and the next action should leave. WEIGHT Attack 5 at tier 3 against a hero who cannot defend, else the minion. SOURCE card text; `rulings/silverarrow.md` 4.
5. `CTX general` [CHOOSE_UNIT, CONFIRM] [`silverarrow-red-3b`] WHEN the first target is a hero on the ring AND another hero is on the ring THEN the repeat is mandatory; take the minion repeat if a minion is also on the ring. If no second hero is on the ring, do not open on a hero expecting the minion. WEIGHT a second card (2.5) plus the minion. SOURCE `rulings/silverarrow.md` 9.
6. `CTX general` [PLAY_CARD] [`silverarrow-green-1`, `silverarrow-green-2a`, `silverarrow-green-3a`] WHEN an enemy unit is adjacent AND the straight line of the distance they are actually pushed ends at a space not adjacent to an enemy hero THEN primary. ELSE Movement. WEIGHT 4 for leaving melee. SOURCE `rulings/silverarrow.md` 5–6; [ADVICE].
7. `CTX general` [CHOOSE_UNIT] [`silverarrow-green-2b`, `silverarrow-green-3b`] WHEN the hero is an ally with a discard THEN offer it. Never herself. Never an enemy unless that enemy's retrieve is somehow the team's plan (it is not, by default). WEIGHT 2.5 plus 1 or 2 coins. SOURCE `rulings/silverarrow.md` 7; BGG 3491927.
8. `CTX general` [PLAY_CARD] [`silverarrow-blue-1`, `silverarrow-blue-2a`, `silverarrow-blue-3a`] WHEN an enemy hero in radius has a revealed initiative lower than this card AND their card has a Movement or Fast Travel action that would reach her or a minion THEN prefer Roots. WEIGHT 6. SOURCE card text; catalog FAQ (checked at the start of the move).
9. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`silverarrow-blue-2b`, `silverarrow-blue-3b`] WHEN an enemy hero will still be in radius 3 at end of turn (already resolved, or remaining movement cannot exit) THEN play it. On 3b, if that hero has no cards, this is the kill. WEIGHT 2.5 or the kill. SOURCE card text; `rulings/silverarrow.md` 8.
10. `CTX general` [DEFEND] [any] WHEN a red meets the attack THEN spend the lowest Defense card that still meets it, and spend red if it is the only one. Gold and silver at Defense 1 do not meet a real attack. WEIGHT the hero. SOURCE the printed Defense column; [ADVICE].
11. `CTX general` [CHOOSE_SPACE] [Wild Hunt and any move] WHEN the destination is adjacent to an enemy hero AND red is not the card about to block THEN avoid it. Prefer a space on an exact-range ring of the attack she still holds. WEIGHT 8 (Defense 1). SOURCE [ADVICE]; [REASONED].
12. `CTX general` [CHOOSE_SPACE] [`silverarrow-purple-4`] WHEN a 2-hex straight line lands on the exact ring, or leaves adjacency, before this action THEN take it. Before a Defense, take it if the landing space is out of the attacker's range (the modifier was already fixed — `rulings/silverarrow.md` 10 — so this is position for later, not a modifier trick). WEIGHT 4. SOURCE designer Discord 2025-06-03.
13. `CTX general` [CHOOSE_ACTION] [`silverarrow-gold`] WHEN no unit is at exact range THEN do not play gold for the primary. Fast Travel or Movement toward a ring, or Hold the card for the turn a minion will be on it. WEIGHT 3 (a wasted Attack 1). SOURCE `AI.md` (gold played with no target, then Held).
14. `CTX general` [PLAY_CARD] [any] WHEN she is more than one Zone from the Battle Zone AND no enemy hero is a legal exact-range target AND an ally is in the fight THEN move toward radius 4 of that ally. WEIGHT 4. SOURCE [ADVICE] (do not abandon them).
15. `CTX general` [CHOOSE_SPACE] [Fast Travel] WHEN the destination Zone or the current Zone contains an enemy unit THEN it is illegal; do not prefer a "through the rocks" space the silver effect does not create. WEIGHT 0 (illegal). SOURCE BGG 3796272; hero FAQ.

## 7. Sources

- Catalog `silverarrow.json` (cards, advice, FAQ).
- `core/game/rulings/silverarrow.md`, `heroes/SilverarrowHero.kt`.
- BGG 3796272, McGravityHammer, 2026-11-10, "Is This a legal opening for Silver Arrow?" — the opening is not legal. https://boardgamegeek.com/thread/3796272
- BGG 3592253 (2025-10-14, follow-up 2026-06-14): ignore-obstacles does not affect Fast Travel or zone adjacency. https://boardgamegeek.com/thread/3592253
- BGG 3505773 (2025-05-02): the silver buff is checked at the start of the Movement action. https://boardgamegeek.com/thread/3505773
- BGG 3491927 (2025-04-06): she is not her own heal target. https://boardgamegeek.com/thread/3491927
- Designer Arty, Discord dates cited above, via https://www.cockeyedandcurious.com/goa2/faq
- Community on Trailblazer's Fast Travel still granting the round effect: cockeyed FAQ cites BGG 3542316, amongdragons, 2025-07-15.
- Wolff Designa, Devoted overview, https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/6 — the "stays one step ahead" identity matches; the "abilities stop when enemies are adjacent" and "gold cannot be used if any enemy is adjacent" lines do not.
- BGG 2891788 is 1st printing and was not used for card timing.
- No 2nd-printing strategy spotlight, and no Reddit or YouTube guide, turned up. The rules threads above plus the card text are the source.

## 8. Implementation notes

`SilverarrowHeuristics.kt` (ADR-085). Checked against `SilverarrowHero.kt`:

- **H1/H13:** the general reach treats gold and the b-reds as a disk. The engine allows only a unit at exact range (printed range + Range items). At `PLAY_CARD` the note adds +1 when a unit is on the ring and −3 when units are only inside it.
- **H4:** the engine offers a "Choose one" bullet only when it can start (RULINGS 38), and `CHOOSE_OPTION` is opaque, so the one-ply look picks the bullet. The draft's fixed preference for the first bullet was removed.
- **H8 (Roots):** the draft read the enemy's *unresolved* card at `PLAY_CARD`, before the reveal, which is peeking. Removed. Roots has no note.
- **H11:** the draft gave −8 to *every* space question next to an enemy, including ones that move enemy units. Now it applies only to her own moves (`subjectId` is her) and only when she holds no red.
- **Not implemented:** H2, H3, H5, H6 (the step after the peel), H9, H12, H14, H15.
- **Ablation** (strength 8, `BatchCAblationTest`, the same line-ups and seeds as `HeroAblationTest`): 96 games 54.2% (52–44), Wilson 44.2–63.8%; 384 games 49.2% (189–195), Wilson 44.3–54.2%.
