# Snorri the Runescribe (`snorri`) — heuristic playbook

2nd printing. Complexity **4**. Sheet: Attack 5–6, Defense 5–6, Initiative 2–2, Movement 4–5. Sheet roles: sniper, farming, durable, damager, pusher, healer. Card numbers are the combat numbers. Gold is Attack 2, plus 3 if the axe rune is active.

**No community strategy guide found** for 2nd-printing Snorri (STRATEGY.md §6 already listed this gap; a pass of the Strategy forum through 2026-11-10 still has no Snorri thread). The rules below are [REASONED] from the cards, the engine, the designer rulings, and the catalog advice. Wolff Designa's Arcane overview is the only designer play note: the gold does four different things by rune, reinscribing spends an action, and he gets more options per card as he levels, so late-game Snorri is the dangerous one.

## 1. Identity

| HeroStyle field | Value |
|---|---|
| `role` | `Fighter` |
| `idealDistance` | `1` on the melee red (`snorri-red-1` / `2a` / `3a`); `3` on the ranged red (`snorri-red-2b` / `3b`) |
| `aggression` | `0.55` |
| `laneWeight` | `0.45` |
| `keepDefender` | true — the green oath, **on a turn whose active rune matches the attack**, else the red (Defense 6–7) or the blue (Defense 5–6) |
| `upgradeOrder` | silver's plan first (he is blank until the runes exist), then the red that matches the distance, then green |

The oath cards have **no primary** (`SnorriHero.kt`: `hasPrimary = false`). They are a block plus a Movement action. Playing one spends the block. The "this turn you are immune" line happens only after a **successful** block; a failed block defeats him and cancels the effect (`rulings/snorri.md` 12). Designer (Discord 2025-11-11): you do not play the oath as a skill to become immune.

## 2. Card-by-card

Runes, and only the active one applies until the Ultimate. Active = the marker under the turn slot equal to the current turn. Nothing is active before the first `snorri-silver`. Markers survive the round and his defeat. Playing silver again moves them. He chooses slot 1, then 2, then 3; the last rune is the one left (`rulings/snorri.md` 1).

| Rune | Gold `snorri-gold` (and the handicap, where it has the line) | Melee red | Ranged red | Oath block | Trap | Passage / boon |
|---|---|---|---|---|---|---|
| axe | +3 Attack | `snorri-red-3a`: may repeat on an enemy minion | an enemy hero who **was** adjacent to the target discards or is defeated (mandatory, he picks) | blocks a **non-ranged** attack (`snorri-green-1` and up) | discards a **silver** (`snorri-green-2b` / `3b`) | ally may swap a resolved card with a hand card (`snorri-blue-2b` / `3b`) |
| bird | may target a minion in range 2 instead of an adjacent unit | — | `snorri-red-3b`: target **any** unit in range, not only the ring | blocks a **ranged** attack (from `snorri-green-2a`) | discards a **gold** (`snorri-green-3b` only) | ignore obstacles this turn (`snorri-blue-1` and the a-blues); ally may swap an item with the other card of that tier and colour (`snorri-blue-3b`) — designer, Discord 2025-02-09: that card can be anywhere, so it undoes a level-up |
| anvil | if the target is a hero, gain 3 coins (when targeted, win or lose). Handicap: 2 coins, no "if hero" | may retrieve (`snorri-red-1` and the a-reds) | — | blocks a **non-basic** attack (`snorri-green-3a` only) | discards a **blue** | immune to enemy actions this turn (`snorri-blue-2a` / `3a`); ally may retrieve **all** discards |
| horn | may repeat on a **different hero in range** (range 2, not only adjacent — designer, Discord 2025-09-28). The main target stays adjacent. | before the attack, may move 1 (`snorri-red-2a` / `3a`), so a unit 2 away becomes legal | after the attack, move up to 2 | blocks a **basic** attack | discards a **green** | `snorri-blue-3a`: +2 Movement |

`snorri-gold` Rune Sigils, init 11, Attack 2, Range 2, Defense 2, Movement 1. Ranged. Without a rune it is only "target a unit adjacent to you."

`snorri-handicap` Rune Shards, init 10, Attack 1, adjacent **hero**. Axe +3, anvil 2 coins, horn repeat. No bird line.

`snorri-silver` Inscribe the Runes, init **1**. Place the four markers. No movement. Defense 3. Almost always last that turn, so the slot-1 rune does nothing for heroes who already acted. The payoff is turns 2–4 of this round and every later round until he spends another action to move them. [ADVICE]: readjusting costs an action. Do it when the gold's slot is the wrong rune and gold is the attack this round, or when two slots are wrong. Not because a single off-rune green would have been nicer.

`snorri-red-1` Runic Dagger, init 7, Attack 5, Defense 6. Anvil: may retrieve after.

`snorri-red-2a` Runic Hammer, init 7, Attack 5, Defense 7. Horn: may move 1 before, so range is 2 only on a horn turn. Anvil: retrieve.

`snorri-red-3a` Runic Battleaxe, init 8, Attack 6, Defense 7. Horn step, axe repeat on a minion, anvil retrieve. This is the melee finisher (Attack 6, or 6 and a minion).

`snorri-red-2b` Runecaster, init 7, Attack 4, Range 3, **exact** maximum range. Horn: move up to 2 after. Axe: a hero who was adjacent to the target discards or dies.

`snorri-red-3b` Runeblaster, init 8, Attack 5, Range 3. Bird: any space in range; otherwise still exact max range. Same horn move and axe splash.

`snorri-green-1` Oath of Endurance, init 5, Movement 2, Block. Horn: basic. Axe: non-ranged. No bird, no anvil. Defense value is null — it does not race a number.

`snorri-green-2a` Oath of Fortitude, init 5. Adds bird: ranged. With two runes (Ultimate) the block succeeds if **any** active line matches.

`snorri-green-3a` Oath of Perseverance, init 4. Adds anvil: non-basic. "Choose one active rune" does **nothing** until two runes are active (designer `nichik`, BGG 3592811, 2025-10-15). Before the Ultimate he is tied to the slot.

`snorri-green-2b` Runetrap, init 5, radius 3, Defense 3. Horn green, axe silver, anvil blue. No bird. Needs a rune that has a line or the primary is not offered.

`snorri-green-3b` Runebomb, init 4, Defense 4. Adds bird: gold. Choose one rune only when two are active. Designer (Discord 2026-02-26): if the card does not say "choose," every active rune's line applies; Runetrap therefore makes the same hero discard both colours under the Ultimate. Runebomb chooses one.

`snorri-blue-1` Safe Passage, init 9, Movement 3, Defense 5. Bird: ignore obstacles on **his** moves, not when someone pushes him (designer, Discord 2025-08-28).

`snorri-blue-2a` / `3a` Hidden / Deep Passage, init 9 / 10, Defense 6. Bird: ignore obstacles. Anvil: immune this turn (this one does **not** require a block). Horn on 3a: +2 Movement.

`snorri-blue-2b` / `3b` Ancestral Boon / Grace, init 9 / 10, radius 4, Defense 6. The ally decides each "may." Not himself.

`snorri-purple-4` Rune Mastery. Once per action whose text reads a rune, he adds one inactive rune as a second active rune. Repeats are a new action, so he chooses again (`rulings/snorri.md` 3). Not on Hold or Fast Travel. Both lines fire unless the card says "choose one."

After-the-attack rune lines are skipped if the attack does not connect (`rulings/snorri.md` 11). Anvil coins on gold are **not** after-the-attack: they happen when the hero is targeted.

## 3. Upgrade path

**Red.** Keep the melee line (`snorri-red-2a`, `snorri-red-3a`) when he will stand in the Battle Zone. Tuck `snorri-red-2b` (Defense item) and `snorri-red-3b` (Movement item). Attack 5 then 6, and the horn step covers one extra hex on the turns he puts the horn there.

Keep the ranged line when the team already has a melee body and he should shoot the ring. Tuck `snorri-red-2a` (Initiative) and `snorri-red-3a` (Radius). Attack 4 then 5 is lower, and the axe splash is the hero-kill: a hero standing on the minion he shoots.

**Green.** Keep the oath (`snorri-green-2a`, `snorri-green-3a`) as the default. It is how a Defense-5 hero blocks a basic attack on a horn turn and a red on an anvil turn, with immunity after. Tuck `snorri-green-2b` (Attack) and `snorri-green-3b` (Attack).

Keep the trap when the enemy team shows the colours the trap names (a gold-dependent hero for bird at tier 3, a blue-defender for anvil). Tuck `snorri-green-2a` (Initiative) and `snorri-green-3a` (Initiative). The trap is a skill, Defense 3–4, and it is not the block.

**Blue.** Keep the passage (`snorri-blue-2a`, `snorri-blue-3a`) when the map's obstacles are the problem or he wants the anvil immunity without spending the oath. Tuck `snorri-blue-2b` (Defense) and `snorri-blue-3b` (Defense).

Keep the boon when an ally is the one who discards (Tali, Takahide's partner, a retrieved finisher). Anvil retrieves **all** their discards. Tuck `snorri-blue-2a` (Attack) and `snorri-blue-3a` (Range). The Range item pushes gold's range-2 minion shot and the horn repeat, and the ranged red's ring, out by 1.

**Phase.** First level: red, the distance he is actually standing at. Second: green oath if he has been hit, else blue. Do not re-spec the red at tier 3 unless the team's melee/ranged split changed. The Ultimate is the power spike the designer overview describes: two lines per action, and `snorri-green-3a` can finally pick.

**Default inscription** once he knows the round, [REASONED]. Slot of `snorri-silver` itself, the first time, on turn 1: the leftover rune (he acts at init 1, so that slot is spent). Thereafter:

- The turn he will play gold: **axe** if one target dies to Attack 2+3+items and not to 2; **horn** if a second hero is in range 2 and the first is adjacent; **anvil** if neither kill is live and 3 coins are the point; **bird** if the only good target is a minion he is not adjacent to.
- The turn he will play melee red: **horn** if the target is 2 away; **anvil** if a retrieve is worth more than the step; **axe** on `snorri-red-3a` if a minion is in repeat range and he is already adjacent.
- The turn he will play ranged red: **axe** if an enemy hero is adjacent to the unit on the ring; **bird** on `snorri-red-3b` if the target is in range but not on the ring; **horn** if he needs the two-step after the shot.
- The turn he expects to be attacked and still holds the oath: the rune that blocks that attack (horn basic, axe non-ranged, bird ranged, anvil non-basic). This overrides the attack placement if he would otherwise die.
- A trap turn: anvil to take a blue, axe to take a silver, horn to take a green, bird (tier 3) to take a gold. Prefer the colour they are still holding, not the one they already played.

## 4. Synergies and counters

**With:** an ally inside radius 4 on a boon turn who actually has a discard (anvil) or a resolved card they regret (axe). A melee ally who stands on the minion Snorri shoots, so the axe splash has a hero. He does not need to be adjacent to his team except for the boon.

**Fears:** a round with no runes, where gold is Attack 2 adjacent and the reds lose every rider. Being forced to re-inscribe the turn he needed to attack. An attack type his current oath cannot block (tier-1 oath does not stop a ranged attack at all). NebKher copying a rune card uses **Snorri's** rune for that turn number (`rulings/snorri.md` 16); the copy is the enemy's threat, not a reason to leave the rune off.

**Against him (`threatAdjust` and play):**

- The rune under this turn is public. Read it before you step.
- Axe on a gold turn: he is Attack 5 adjacent, not Attack 2. Horn: he can hit a second hero at range 2 after the adjacent hit. Bird: minions at range 2. Anvil: he wants a hero target for 3 coins even if the attack is small, so denying him a hero target denies the coins.
- Horn on a melee-red turn: he threatens from 2 hexes, not 1.
- Axe on a ranged-red turn: do not stand adjacent to the minion on his exact-range ring. That adjacency is a discard or a defeat, separate from the shot.
- His oath blocks only the matching type. Hit him with the other type. Tier-1 oath loses to every ranged attack. After a successful block he is immune to enemy actions for the rest of the turn — do not spend a second action on him that turn.
- If silver is still unplayed, nothing is active. He is a small melee Attack until he spends the turn.

## 5. Common mistakes

- Playing gold on a turn with no rune, or with bird when he needed the +3, and not having put axe on that slot.
- Re-inscribing every round. That is an action the advice says to spend only when the plan changed.
- Treating `snorri-green-3a`'s "choose" as a menu before the Ultimate. It is not (BGG 3592811).
- Playing the oath as the turn's card to "turn on immunity." There is no primary.
- Blocking with the oath on the wrong rune. The block fails and he is defeated.
- Shooting `snorri-red-2b` at a unit that is not on the ring. `snorri-red-3b` may, and only on a bird turn.
- Aiming the horn repeat at an adjacent-only hero. The repeat is range 2; the first hit is adjacent.
- Offering the boon to himself. Illegal.
- Forgetting the anvil coins happen even when the attack is defended. The target is still a hero he wants.

## 6. Heuristic rules

`CTX hero` means the four markers and which card he will play on which turn. The markers are public (`MarkerView.slot`).

`evalTerms`: +4 if a rune is on the turn he can still play gold and that rune is the one §3 would choose for the targets currently in reach. +2 for each other slot that matches the card still in hand for that turn. −4 if no runes are on the board and it is past turn 1 (he is playing a blank kit). A rune marker is not worth a flat token value; a wrong rune is 0, not a bonus. This is the term `AI.md` says the search never planned.

`threatAdjust` (he is the enemy): while axe is active, his gold threatens at Attack 5, not 2 — add 4 to adjacent hero spaces if 5 crosses their remaining Defense and 2 does not. While horn is active, add range-2 hero spaces as a second-hit threat (+4) even though the printed gold range is "adjacent" for the first target. While bird is active, minion spaces at range 2 are in his gold threat. While axe is active and the ranged red may be the card, a hero adjacent to a unit on his exact-range ring is threatened by the splash (+the discard, or the kill on `snorri-red-3b`'s "or is defeated" if they have no card), which is not his attack range on that hero.

1. `CTX hero` [PLAY_CARD] [`snorri-silver`] WHEN no markers are placed AND turn = 1 THEN play it. WEIGHT 8 (the rest of the round's riders). SOURCE [ADVICE]; card text (nothing is active before this); [REASONED].
2. `CTX hero` [CHOOSE_OPTION] [`snorri-silver`] WHEN placing slot N THEN use the §3 default for the card he will play on turn N. The slot of the turn he is playing silver, if he acts last, gets whichever rune is left. WEIGHT 4 per matching slot, against 0 for a random permutation. SOURCE [REASONED]; [ADVICE] ("plan ahead").
3. `CTX hero` [PLAY_CARD] [`snorri-silver`] WHEN markers exist AND the gold slot already matches §3 AND at most one other slot is wrong THEN do not play silver. WHEN the gold slot is wrong AND gold is still in hand AND a legal gold target exists this round THEN play silver only if no attack this turn takes a minion the lane needs. WEIGHT −4 for a needless re-inscribe; +6 for fixing the gold slot. SOURCE [ADVICE].
4. `CTX hero` [PLAY_CARD, CHOOSE_UNIT] [`snorri-gold`] WHEN axe is active AND Attack 2+3+items meets a hero's remaining Defense, or a minion is adjacent THEN this is the attack. Prefer the hero if both. WEIGHT the kill or the minion, plus 3 if anvil is the extra rune under the Ultimate and the target is a hero. SOURCE card text; [REASONED].
5. `CTX hero` [CONFIRM] [`snorri-gold`] WHEN horn is active AND a different hero is in range 2 THEN repeat. WEIGHT the second card or the second kill. Do not repeat onto a hero who easily defends if the first target was a minion you still need... the first target is already chosen; the repeat is a hero. SOURCE designer Discord 2025-09-28; `rulings/snorri.md` 5.
6. `CTX hero` [CHOOSE_UNIT] [`snorri-gold`] WHEN bird is active AND no adjacent unit is worth Attack 2 AND a minion is in range THEN the minion. WHEN anvil is active AND a hero is adjacent THEN the hero even if the attack will be defended (3 coins). WEIGHT 3 coins, or the minion. SOURCE card text; `rulings/snorri.md` 6–7.
7. `CTX hero` [CHOOSE_SPACE, CHOOSE_UNIT] [`snorri-red-2a`, `snorri-red-3a`] WHEN horn is active AND the target is 2 away AND the step lands adjacent THEN take the step and the attack. WHEN anvil is active AND the discard has a card AND he is already adjacent THEN skip a pointless step and attack for the retrieve. WEIGHT 2.5 for the retrieve, or the attack that was otherwise out of range. SOURCE `rulings/snorri.md` 8.
8. `CTX hero` [CONFIRM] [`snorri-red-3a`] WHEN axe is active AND an enemy minion is a legal repeat THEN repeat if it is the lane margin or a coin. WEIGHT 15 or 5. SOURCE card text.
9. `CTX hero` [CHOOSE_UNIT] [`snorri-red-2b`, `snorri-red-3b`] WHEN bird is not active THEN only a unit at exact Range. WHEN bird is active on `snorri-red-3b` THEN any unit in range, prefer a hero the engine total beats or a minion on the lane. WHEN axe is active AND an enemy hero is adjacent to a unit on the ring THEN prefer that unit over a lone minion. WEIGHT the splash (2.5, or the kill if they have no card) plus the shot. SOURCE `rulings/snorri.md` 10; card text.
10. `CTX hero` [DEFEND] [`snorri-green-1`, `snorri-green-2a`, `snorri-green-3a`] WHEN the active rune's line matches this attack (horn basic, axe not ranged, bird ranged, anvil not basic) THEN this block is the answer, whatever the Attack total. WHEN it does not match THEN do not spend it; a failed block is a defeat. WEIGHT the hero. SOURCE card text; `rulings/snorri.md` 12; designer Discord 2026-03-16.
11. `CTX hero` [PLAY_CARD] [`snorri-green-1`, `snorri-green-2a`, `snorri-green-3a`] WHEN choosing a card to play for Movement THEN prefer a different card if an attack he cannot block is still unresolved. Spending the oath to walk is how he dies to the other attack type. WEIGHT 8. SOURCE [REASONED] from `hasPrimary = false` and ruling 12.
12. `CTX hero` [CHOOSE_UNIT, CHOOSE_OPTION] [`snorri-green-2b`, `snorri-green-3b`] WHEN an enemy in radius still holds the colour the active rune names THEN play it and pick that hero. Prefer blue (anvil) over silver (axe) over green (horn), and gold (bird, tier 3) when they are a gold-dependent hero (Takahide, Swift's reload, anyone whose gold is the finisher). WEIGHT 4 for a blue or a gold, 2.5 for a silver or a green. SOURCE card text; [REASONED] on which colour hurts.
13. `CTX hero` [PLAY_CARD] [`snorri-blue-2a`, `snorri-blue-3a`] WHEN anvil is active AND an enemy action would defeat him after this card resolves AND the immunity covers the rest of the turn THEN the primary is a real defensive action, unlike the oath. Prefer it. Bird: play it when the path to the target is blocked and the ignore-obstacles move reaches. WEIGHT 6. SOURCE card text; `rulings/snorri.md` 13.
14. `CTX hero` [CHOOSE_UNIT, CONFIRM] [`snorri-blue-2b`, `snorri-blue-3b`] WHEN anvil is active AND an ally in radius 4 has two or more discarded cards THEN offer the full retrieve. WHEN axe is active AND an ally has a resolved card and a better card in hand THEN offer the swap. WHEN bird is active on `snorri-blue-3b` AND an ally's tucked item is the wrong branch THEN offer the item swap. ELSE do not spend the card. WEIGHT 2.5 per card retrieved, or 4 for a fixed level-up. SOURCE designer Discord 2025-02-09; `rulings/snorri.md` 15.
15. `CTX hero` [CHOOSE_OPTION] [`snorri-purple-4`] WHEN a second rune is asked THEN add the rune from §3 that the first rune does not already cover (axe if the attack is short, horn if a second hero is in range, anvil if the coins or the retrieve are the point). On a "choose one" card, pick the line with the higher weight from rules 10 or 12. WEIGHT 4. SOURCE designer Discord 2026-02-26; FAQ in the catalog.
16. `CTX general` [CHOOSE_SPACE] [movement] WHEN the next attack's rune needs him adjacent THEN end adjacent to that target. WHEN it needs the ring THEN end at exact Range of `snorri-red-2b` / `3b`. Do not end adjacent to an enemy on a turn the oath cannot block their likely attack. WEIGHT 4. SOURCE [REASONED].

## 7. Sources

- Catalog `snorri.json`. Advice: plan the runes; reinscribing spends an action; he wants levels.
- `core/game/rulings/snorri.md`, `heroes/SnorriHero.kt`.
- Designer `nichik`, BGG 3592811, 2025-10-15. https://boardgamegeek.com/thread/3592811
- Designer Arty, Discord 2025-02-09 (item swap), 2025-08-28 (ignore obstacles), 2025-09-28 (horn repeat at range), 2025-11-11 (oath is not a proactive skill), 2026-02-26 (both lines unless the card says choose), 2026-03-16 (only the active rune blocks). https://www.cockeyedandcurious.com/goa2/faq
- Wolff Designa, Arcane overview. https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/10
- **No community strategy thread, Reddit post, or YouTube guide found.** BGG 3592811 is a rules question, not a build.

## 8. Implementation notes

`SnorriHeuristics.kt` (ADR-085). Checked against `SnorriHero.kt`:

- Runes are markers from Snorri with a `slot` (`SnorriHero.placedRunes`). The rune under the current turn is active, and they are public, so both his notes and an enemy's threat read them.
- **H2:** inscription is four `CHOOSE_OPTION` questions "Which rune goes below turn slot N?" (labels Axe, Bird, Anvil, Horn). The question is opaque, so the look-ahead scores it too. The note leans axe, then horn, onto future slots, and the leftover onto the slot he is spending.
- **H10:** the engine works out the oath against the active rune and shows the result in `blockPreview`. If the preview is false, the oath does not block and he is defeated (−10).
- **H6:** the draft gave anvil's +3 coins to *minion* targets too (a null hero compared with a team). Fixed: heroes only, as the card says.
- **H5:** the horn repeat is the engine's optional repeat (a confirm question, already opaque). There is no note.
- **Not implemented:** H7, H8, H12–H16 (rune-specific choices the one-ply look already resolves through the engine).
