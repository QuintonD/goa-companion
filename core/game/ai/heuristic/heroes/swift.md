# Swift the Sharpshooter (`swift`) — heuristic playbook

2nd printing. Complexity **3**. Sheet: Attack 5–5, Defense 2–2, Initiative 4–4, Movement 3–7. Sheet roles: sniper, farming, tactician. Card numbers only.

**1st-printing spotlight BGG 2889490 (bcsj, 2022-06-25, the playtest Swift player) is not a card source.** These parts do **not** match the printed cards and must not be encoded:

- Gold init 5, "the resolved card in your previous turn slot," Movement 2 on gold. Printed `swift-gold` is init **6**, "the primary action of your **rightmost resolved** card," or defeat an adjacent minion. Movement 1, Defense 1.
- Silver that swaps with a red or a gold and pushes. Printed `swift-silver` is a 2-hex straight move that ignores obstacles, and a repeat only if the card is **already resolved** when the action is performed (the gold replaying it — catalog FAQ).
- Shotgun "+3 if adjacent" and "up to one" adjacent hero. Printed `swift-red-2b` is Attack 4, Range 2, and **requires** an enemy hero adjacent to the target or the action stops before the attack (`rulings/swift.md` 3). `swift-red-3b` is Attack 5 and that hero discards **or is defeated**, with no "if able."
- Killshot range 6 and "7 with items" as a printed fact. Printed `swift-red-3a` is Range **5**. The campaign's "7" is not on the card.
- Killing Ground "not adjacent to you, not adjacent to terrain or a token." Printed text is "not adjacent to **terrain**" only. Tokens are not named.
- Alternate green as a card-swap crutch. Printed `swift-green-2b` / `3b` move enemy minions and pay coins **next turn** when enemy minions in radius are defeated.
- Alternate blue as a same-action off-axis dodge. Printed `swift-blue-2b` / `3b` place her at **end of turn**, off her straight lines; `swift-blue-3b` may then Fast Travel.
- The old Ultimate ("after an action on gold, do gold again"). Printed `swift-purple-4` fires after she **resolves a basic card**, and performs that card's primary, and "you cannot target the same enemy hero twice in the same turn this way."

What still matches, and is used below: red-then-gold is the double attack and it is predictable; vary it; a clever enemy steps in on the turn she wants the repeat (BGG 2889490). The rifle is the long straight tool and the shotgun is the hero tool ([ADVICE], and the same split in that spotlight). Sometimes gold should defeat the adjacent minion ([ADVICE]).

A Gamefound design note said a later Reload "removed" the minion so she would not gain coins. Printed `swift-gold` says **Defeat**. Coins are paid. Card text wins.

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `Ranged` |
| `idealDistance` | `4` on the rifle (`swift-red-1` / `2a` / `3a`); `2` on the shotgun (`swift-red-2b` / `3b`) |
| `aggression` | `0.55` |
| `laneWeight` | `0.45` |
| `keepDefender` | true — the red (Defense 4–6). Gold is Defense 1. Blue is 3–4. |
| `upgradeOrder` | red is the hero; gold only repeats what is already resolved to its left |

She is slow when she shoots. `swift-gold` at 6 and the reds at 8–9 lose to most golds (11–13) and blues (9–11). A hero who acts before her can leave the line or step adjacent. A minion cannot. [ADVICE]: threaten a hero and a minion together; the rifle pushes the lane, the shotgun fights heroes.

## 2. Card-by-card

| Id | Init | For | Play when | Do not | Hold |
|---|---|---|---|---|---|
| `swift-gold` Reload! | 6 | Perform the primary of the rightmost **faceup** resolved card, if that primary can start and is not a Defense. Or defeat an adjacent enemy minion (coins). | The card in the previous turn slot is the one she wants twice: a rifle shot that still has a target, a shotgun whose adjacent hero is still there, a jump, or Suppress. Or an enemy minion is adjacent and the repeat has no target. | When the rightmost resolved card is silver and she did not want a second 2-hex ignore-obstacle move. When the rightmost card's primary cannot start (no line, no adjacent hero for the shotgun). The bullet is then not offered (`rulings/swift.md` 2). | Defense 1. Not a block. |
| `swift-handicap` Backup | 6 | The same repeat, but an attack performed this way **cannot target minions**. No "defeat a minion" bullet. | A hero target for the repeated attack. | A minion. | Defense 1. |
| `swift-silver` Bounce | 12 | Move exactly 2 in a straight line, ignoring obstacles. If this action is performed while the card is already resolved, she may repeat once (another 2). | As the card **before** gold, when she wants a 4-hex ignore-obstacle dash at init 6 (play silver, then gold replays it). Or as a fast (init 12) 2-hex leave. | Expecting it to retrieve a discarded red. It does not. | Defense 2. A poor block and a real escape if she is about to be adjacent and a 2-hex line leaves. |
| `swift-red-1` Snipe | 8 | Attack 5, Range 4, **exact** range, straight line. | A minion on the ring and the line, or a hero whose revealed init is **lower** than 8 so they have not left yet. | A hero with a higher revealed init who can step off the line or into adjacency. A unit off the line. A unit at range 1–3. | Defense 4. |
| `swift-red-2a` Prepared Shot | 8 | Attack 5, Range 4, in range, straight line, **not adjacent**. | A unit on a line inside range 4 that is not next to her. | Anyone adjacent. They are not a target, and standing there is how a rifle Swift dies (the 1st-printing note about walking next to her still matches this restriction). | Defense 5. |
| `swift-red-3a` Killshot | 9 | Attack 6, Range 5, same restriction. | Same, longer. Init 9 still loses to a 12. | Same. | Defense 5. |
| `swift-red-2b` Shotgun | 8 | Attack 4, Range 2. Before the attack, an enemy hero adjacent to the target discards, if able. No such hero: the action does not start. | A minion in range 2 with an enemy hero adjacent to that minion, or a hero in range 2 with a **second** enemy hero adjacent to them. That is the fork [ADVICE] describes. | A lone minion. A lone hero. The primary will not be offered. | Defense 6. Best block on this branch. |
| `swift-red-3b` Super-Shotgun | 9 | Attack 5, Range 2. The adjacent enemy hero discards **or is defeated**. | Same geometry. If that hero's hand is empty, they die **before** the attack, and the target is still attacked. Two heroes adjacent to each other, both in range 2, can both die if neither can answer. | A target with no enemy hero next to it. | Defense 6. |
| `swift-green-1` / `2a` / `3a` Suppress / Pin Down / Killing Ground | 4 / 4 / 3 | An enemy hero in radius 3 / 4 / 4 who is **not adjacent to terrain** discards, or at tier 3 is defeated. Terrain includes water and heroes who count as terrain (Stone Gaze). Not "not adjacent to Swift." Not tokens, unless the engine has marked that token as terrain — do not assume a turret. | A hero who has already resolved, is in radius, and is off terrain. Init 3–4 means almost everyone has acted. | A hero standing next to a rock, a wall, or a water edge. No legal hero: the primary is not offered. | Defense 2–3. |
| `swift-green-2b` Mark for Death | 4 | Move an enemy minion in radius 4 up to 3, to a space in radius. **Next turn:** the first time an enemy minion in radius is defeated, +1 coin. The coin effect counts defeats by anyone, from her space at the moment of the defeat. A totem save is not a defeat. | An enemy minion is in radius (designer, Discord 2026-01-15: this tier needs a target; the higher tier is stronger and does not). Place it where an ally kills it next turn, or where it blocks a line. | No enemy minion in radius. Do not play it "for the coin" with nothing to move; the primary may not start and the effect may not stick. Follow the engine: if the primary is not offered, do not play the card. | Defense 2. |
| `swift-green-3b` Hunting Season | 3 | Up to two enemy minions, up to 3 spaces **each**. Next turn, the first **two** such defeats pay 1 coin each. Zero minions is legal ("up to"). | Same, and it is legal with no minion. Two coins if two minions will die in radius next turn. | Moving a minion out of the radius you need next turn. | Defense 3. |
| `swift-blue-1` / `2a` / `3a` Steam Jump / Assault Jump / Drop Trooper | 9 / 10 / 10 | Place on a straight line in radius 3 / 4 / 4. Then push an adjacent enemy up to 1 / 2 / 2. Tier 3: up to two enemies, one distance for both. | The line crosses the map or lands on a rifle line / shotgun range. The push knocks a hero off a minion. Reload it (gold) if one jump is short. | A destination off the line. This card does not do diagonals-that-aren't-a-hex-line. | Defense 3–4. |
| `swift-blue-2b` Delayed Jump | 10 | **End of turn:** place in radius 2, not on a straight line from her current space. Mandatory if a space exists. | She wants to leave the space the next turn's attacks were aimed at, and a non-line space in radius 2 is safer. | Expecting it to move her during her action. It does not. Enemies who act after init 10 still see her here. | Defense 4. Movement on the card is 1. |
| `swift-blue-3b` Mobile Scout | 10 | Same, radius 2, then she may Fast Travel if able. | The end-step leaves the Zone empty so the Fast Travel is legal, onto the next wave. | A crowded Zone (the Fast Travel will not be offered; the placement still happens). | Defense 4. |
| `swift-purple-4` Bullet Time | — | After she resolves a basic card, whatever action she took, she may perform that card's primary. The extra action may not target an enemy hero her actions already targeted this turn (`rulings/swift.md` 4–5). | Move with the red, then take the shot. Or resolve gold and perform Reload again, which the engine points at the card **before** gold, not at gold itself (`rulings/swift.md` 1). The second shot is a different enemy hero or a minion. | A second shot at the same enemy hero. Designer (Discord 2025-09-26) says the intention of the old wording was two shots on the same hero and the printed restriction is the RAW; designer (Discord 2026-09-21) says gold-performs-red-twice and move-then-shoot are both yes. This engine forbids the same enemy hero. Follow the engine. A minion is a legal second target. | — |

Rightmost resolved: turn slots run left to right. When gold resolves, the latest earlier slot that has resolved is the card it copies. The card she wants twice must be the one **immediately before** gold, not a red played on turn 1 if a green resolved on turn 2.

## 3. Upgrade path

**Rifle.** Keep `swift-red-2a`, `swift-red-3a`. Tuck `swift-red-2b` (Initiative) and `swift-red-3b` (Radius). Range 4 then 5, Attack 5 then 6, must stay off adjacency. She is a lane shooter. [ADVICE]: the rifle is the pushing weapon.

**Shotgun.** Keep `swift-red-2b`, `swift-red-3b`. Tuck `swift-red-2a` (Defense) and `swift-red-3a` (Defense). Attack 4 then 5, Range 2, and she only shoots when a hero is touching the target. [ADVICE]: this is the hero weapon. Defense 6 plus two Defense items is how she stands at range 2.

**Green.** Keep `swift-green-2a` / `3a` when the fight is away from rocks (open lanes). Tuck `swift-green-2b` (Attack) and `swift-green-3b` (Attack). Radius 4 and a tier-3 execute on a hero who cannot hug terrain.

Keep `swift-green-2b` / `3b` when the map is all edges and the coin for other people's minion kills is the value. Tuck `swift-green-2a` (Initiative) and `swift-green-3a` (Range). The Range item on a shotgun is what lets Range 2 become 3; on a rifle, Killshot is already 5 and the tuck would have been the shotgun's Defense instead, so this Range item is the shotgun's.

**Blue.** Keep `swift-blue-2a` / `3a` to cross the map on lines and push. Tuck `swift-blue-2b` (Defense) and `swift-blue-3b` (Movement). Keep `swift-blue-2b` / `3b` when she is dying at the end of the turn and wants to leave plus Fast Travel. Tuck `swift-blue-2a` (Attack) and `swift-blue-3a` (Attack). The 1st-printing note that alt blue wants the rifle's radius item does not map onto these icons one-for-one; read the tuck column above, not that guide's item list.

**Phase.** Red first, the weapon she will actually have a target for. Then blue if she cannot reach lines, else green. Gold is not an upgrade. The Ultimate last, and only after she is already creating a legal second target (a minion, or a different hero).

## 4. Synergies and counters

**With:** an ally who kills the minion she moved with Mark for Death, inside her radius, next turn (the coin). An ally who stands so the shotgun's "hero adjacent to the target" is their problem, not a hole in her action. Brogan and other pushers who put a hero on the line and leave them there. She should not be so far that the ally is alone (the 1st-printing spotlight's warning still matches [ADVICE] and the Defense-2 sheet).

**Fears:** anyone who acts before init 8 and can step one hex off a line, or step adjacent to a rifle Swift. Terrain, for the a-green: a hero who ends next to a rock is safe. NebKher copying a long rifle shot, if he can, is the 1st-printing hard counter and still a bad day if her red is the resolved card he copies — treat a copied Killshot as lethal at Defense 2. Fast melee that ends adjacent while her red requires "not adjacent."

**Against her:**

- The repeat is the previous slot once gold is down. Step off that card's geometry before init 6 if you act first; if you act after, the shot has already happened.
- Rifle: be adjacent to her, or off the line, or not at the far ring for Snipe. Prepared Shot and Killshot cannot target their own adjacency.
- Shotgun: do not stand next to the minion in her range 2. The minion is the target and you are the discard. On `swift-red-3b`, an empty hand there is a defeat, not a discard.
- A-green: end adjacent to terrain. That is the safe square (the opposite of Mrak). Tokens are not automatically safe.
- B-blue places her at end of turn, off her lines, and tier 3 may Fast Travel. A plan that aims at her current space for **next** turn is aimed at the wrong hex.
- Gold sometimes just kills the adjacent minion. Do not assume every gold is a second shot ([ADVICE]).

## 5. Common mistakes

- Playing red on turn 1 and green on turn 2 and gold on turn 3, then wondering why gold copied the green.
- Shotgun at a lone minion. The action does not start.
- Snipe at range 3 because it is "in range." Snipe is the ring only. Prepared Shot is the disk, excluding adjacency.
- Treating silver as a heal. It is a dash.
- Using gold to repeat a card that no longer has a target, instead of defeating the adjacent minion.
- Playing `swift-green-2b` with no enemy minion in radius.
- Standing at ideal distance 4 while holding the shotgun.
- Ultimate: shooting the same hero twice. Illegal in this engine. Shoot a minion second.
- Assuming ignore-obstacles on Bounce lets her ignore the "straight line" or stop early. It is exactly 2.

## 6. Heuristic rules

`evalTerms`: +4 while the previous turn's resolved card is a red or a blue whose primary can still be performed and gold is unplayed (the repeat is live). +1 next turn for each enemy-minion defeat she can still collect from `swift-green-2b` (one) or `swift-green-3b` (two), if she is in radius of a minion an ally can reach. 0 for a resolved silver unless gold can replay it into a useful 4-hex line (+3 in that case).

`threatAdjust` (she is the enemy): rifle cards threaten only straight lines, and Snipe only the exact ring — drop the generic disk. Shotgun threatens the **hero adjacent to her target**, not only the target: a hero standing next to a minion in her range 2 is in discard (or defeat) range even if she never targets the hero. Add +4 there while the shotgun may be in hand. A-green adds a discard threat in radius to heroes who are not adjacent to terrain, which is not an attack. With the Ultimate, a space a 2-hex straight Wild-Hunt-style step does **not** apply (that is Silverarrow); Swift's extra reach is Bounce (2, ignoring obstacles) and a second primary after she moves. Add the straight 2-hex ignore-obstacle exits as mobility, not as attack range.

1. `CTX general` [PLAY_CARD] [`swift-gold`] WHEN the rightmost resolved card's primary can start against a legal target THEN prefer the repeat over the minion, unless the minion is the lane margin and the repeat is only a hero who can defend. WEIGHT the second action (a minion, a card, or a kill). SOURCE card text; [ADVICE]; BGG 2889490 (the sequence, not the old wording).
2. `CTX general` [PLAY_CARD] [`swift-gold`] WHEN the repeat cannot start AND an enemy minion is adjacent THEN defeat the minion. WEIGHT 5, or 15 if it is the margin. Coins are paid (printed "Defeat"). SOURCE [ADVICE]; card text, against the Gamefound "remove" note.
3. `CTX general` [PLAY_CARD] [any] WHEN she will play gold later this round THEN the card played on the immediately previous turn should be the one rule 1 should copy. Do not play a setup card in that slot if a red or a jump is the payload. WEIGHT 5. SOURCE `rulings/swift.md` 1; [REASONED] from "rightmost."
4. `CTX general` [PLAY_CARD] [`swift-red-1`, `swift-red-2a`, `swift-red-3a`] WHEN a unit is on a straight line at a legal distance (exact ring for Snipe; any non-adjacent hex in range for the others) AND that unit is a minion, OR a hero whose revealed initiative is lower than this card THEN play it. WHEN the only hero target has a higher initiative and a movement that leaves the line or steps adjacent THEN do not. WEIGHT the minion or the kill. SOURCE card text; [REASONED] from init 8–9 versus golds at 11–13; BGG 2889490 (they step in on the repeat turn).
5. `CTX general` [CHOOSE_UNIT] [`swift-red-2b`, `swift-red-3b`] WHEN a unit in range 2 has an enemy hero adjacent to it THEN that unit is the target; prefer a minion the Attack defeats if the adjacent hero must discard, and prefer a hero target if a second hero adjacent to them will be defeated by `swift-red-3b` for lack of a card. WHEN no such pair exists THEN the primary is illegal; Movement or a different card. WEIGHT 2.5 for the discard, the kill for the empty hand, plus the minion. SOURCE `rulings/swift.md` 3; [ADVICE].
6. `CTX hero` [PLAY_CARD] [`swift-silver`] WHEN gold is in hand AND a 4-hex straight ignore-obstacle path (2 now, 2 when gold replays a resolved Bounce) reaches a rifle line or leaves a threat THEN play silver on the turn before gold. WHEN she only needs to leave now, at init 12, play it as the escape and do not count on a repeat. WEIGHT 4. SOURCE catalog FAQ; card text.
7. `CTX general` [CHOOSE_SPACE] [`swift-blue-1`, `swift-blue-2a`, `swift-blue-3a`] WHEN a straight-line space in radius is on the rifle's line or in shotgun range of a hero-minion pair THEN land there. Push an adjacent enemy off a minion if the push is offered. WEIGHT 4 plus a denied farm. SOURCE card text.
8. `CTX general` [CHOOSE_SPACE] [`swift-blue-2b`, `swift-blue-3b`] WHEN end of turn offers a non-line space outside enemy attack range THEN take the safest such space; on `swift-blue-3b` Fast Travel afterward if the Zone is empty and the destination is the next fight. WEIGHT 5. SOURCE card text; `rulings/swift.md` 10; STRATEGY.md §5 (place her out of next turn's attacks).
9. `CTX general` [PLAY_CARD, CHOOSE_UNIT] [`swift-green-1`, `swift-green-2a`, `swift-green-3a`] WHEN an enemy hero in radius is not adjacent to terrain AND has already resolved (or cannot move out) THEN the discard, or the kill on `swift-green-3a` if they have no card. WEIGHT 2.5 or the kill. SOURCE card text. Do not treat tokens as terrain.
10. `CTX general` [CHOOSE_UNIT, CHOOSE_SPACE] [`swift-green-2b`, `swift-green-3b`] WHEN moving minions THEN put them where they stay in radius and where an ally can defeat them next turn, up to the number of coin triggers (1 or 2). On 3b, playing with zero moves is legal and still arms the coins. WEIGHT 1 per coin that will actually be collected. SOURCE designer Discord 2026-01-15; `rulings/swift.md` 8; Tali FAQ (a totem save does not count).
11. `CTX general` [CONFIRM, CHOOSE_UNIT] [`swift-purple-4`] WHEN the extra primary has a target that is not an enemy hero already targeted this turn THEN take it. A minion is the usual second target. WHEN the only target is the same enemy hero THEN decline. WEIGHT the minion or the second hero. SOURCE `rulings/swift.md` 4–5; designer Discord 2026-09-21 for move-then-shoot, which the engine allows because any resolved action triggers it.
12. `CTX general` [PLAY_CARD] [any] WHEN the previous round's gold copied the red AND the enemy can reach her on that same turn number THEN do not repeat that order if another legal order (gold on the minion, or silver before gold, or red after a jump) takes a minion. WEIGHT 3. SOURCE BGG 2889490; `AI.md` (Swift's slot order was not planned, and predictable play is exploitable).
13. `CTX general` [DEFEND] [any] WHEN the shotgun red's Defense 6 meets the attack AND she has another way to attack (gold can still defeat a minion, or the rifle is the other branch and already resolved) THEN she may spend red. WHEN red is her only attack and gold would have nothing to copy THEN keep red and spend blue if blue meets the number. WEIGHT 6 for the only attack. SOURCE the Gamefound history of "losing the only attack ends the round," which the defeat-a-minion bullet softens but does not erase; [REASONED].
14. `CTX general` [CHOOSE_SPACE] [any] WHEN holding the rifle THEN do not end adjacent to an enemy hero. WHEN holding the shotgun THEN end at range 2 of a minion that a hero is adjacent to, not at range 4. WEIGHT 6. SOURCE card restrictions; [ADVICE].
15. `CTX general` [CHOOSE_ACTION] [`swift-gold`] WHEN both bullets are legal AND the repeat targets a hero who will defend AND the adjacent minion is the lane margin THEN the minion. WEIGHT 15 against 2.5. SOURCE [ADVICE] ("sometimes it's better to use your gold to defeat an adjacent minion").

## 7. Sources

- Catalog `swift.json`.
- `core/game/rulings/swift.md`, `heroes/SwiftHero.kt`.
- BGG 2889490, bcsj, 2022-06-25. Used only for the sequencing idea and the rifle/shotgun split. https://boardgamegeek.com/thread/2889490
- BGG 3555948, 2025-08-08: Bounce is already resolved when gold performs it. https://boardgamegeek.com/thread/3555948
- Designer Arty, Discord 2025-03-21 (each minion moves up to 3), 2025-09-05 (Shotgun and two Razzles), 2025-09-26 (Ultimate RAW vs intention), 2026-01-15 (tier-2 green needs a minion; tier 3 does not), 2026-07-05 (Reload may perform a Clear on the resolved card, because it is not a repeat), 2026-09-21 (move then shoot; gold performs the red twice — the engine still blocks the same hero). https://www.cockeyedandcurious.com/goa2/faq
- Wolff Designa, Renowned overview and the March update on Reload's history. https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/8 and `updates/21`. The "remove" wording loses to the printed "Defeat." The "previous turn slot" and "range 7" lines lose to the printed card.
- No 2nd-printing strategy spotlight, Reddit thread, or YouTube guide found.

## 8. Implementation notes

`SwiftHeuristics.kt` (ADR-085). Checked against `SwiftHero.kt`:

- **H1/H2:** the general book scores Reload! as a plain skill. The note prices gold as the better of the rightmost resolved card's primary (`strikeFrom` for an attack) and the best adjacent minion (`minionWorth`, the minion bullet only, not on the handicap card). The draft's flat +6 and its "do not cover the payload" −4 on every other card (H3) counted the same thing twice. H3 is left out.
- **H5:** the engine asks for the shotgun's target first, then stops the action if no enemy hero is next to it (`fizzle`, rulings/swift.md 3). A target without such a hero is −12. At `PLAY_CARD` a shotgun with no such pair in range 2 is −2.5.
- **H14:** applies only to her own moves (`subjectId`), not to pushes or minion moves.
- The rifle's straight-line and minimum-distance rules are the engine's target filter, so the draft's re-check of them was removed.
- **Not implemented:** H4 (the enemy's revealed initiative is not known when cards are chosen), H6–H12, H15.
