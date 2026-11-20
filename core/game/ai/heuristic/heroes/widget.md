# Widget (`widget`) — heuristic playbook

2nd printing. Dashboard: Attack 5, Defense 4, Initiative 4, Movement 5–6. Complexity 3. Catalog roles: tactician, pusher, damager. Advice in `heroes/widget.json`. No hero FAQ.

Units: `heuristic/DESIGN.md`. **[HERO]** needs Pyro: one token, supply 1, not removed at end of round, removed by Clear, removed when a minion spawns on its hex, not removed when Widget is defeated [rulings/widget.md 1; GoatRogan, BGG 3454879]. While Pyro is off the board, Breath, Nibble/Gnaw, the swaps and Drag Off have no primary.

Gold copies a **resolved** skill, not a discarded one, on a different unit [CARD; nichik, BGG 3454879, 2025-02-17]. Play the skill first.

## 1. Identity

Pusher / Support [Cryomancy, BGG 3779890; he says Widget is the one class he is least sure of]. Two bodies. She is slow (Initiative 4) and her Gold is init 11, Attack 4, so the dragon does the tricky part and the Gold does it again, faster, after something has already resolved.

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | SetUp | Pyro is the origin of the breath and the swap. |
| `idealDistance` | 2 | She wants to be adjacent for Gold and Red. Pyro wants to be on the line or beside the minion, often 2–3 from her so a swap is a teleport. |
| `aggression` | 0.4 | Kills come from the copied skill and from an ally she swaps in. She herself should not eat a focused attack: no heal [GoatRogan]. |
| `laneWeight` | 0.65 | Nibble/Gnaw plus a Red minion is the double-take. Breath is the hero tool. |
| `keepDefender` | true | Silver is Defense 6 and has no Movement icon, so playing it spends the wall and cannot Fast Travel. Red-1 is Defense 6, Movement 4. One of those stays until the hit is real. |
| `upgradeOrder` | do not rush Red; Blue before the enemy learns to walk away from Pyro; see §3 | |

## 2. Card-by-card

### `widget-gold` Fight as One — Attack, init 11, Attack 4, Defense 2, Movement 1

Adjacent unit. After the attack: she may perform the primary of one resolved skill, targeting a different unit.

- **For:** the second action at init 11. A skill she already played at init 3–10 happens again, after the hit, and not on the same unit [nichik: the line exists to stop a double discard on one action]. If the skill does not target a unit (place Pyro, move Pyro), it is still legal; the "different unit" binds only when that skill targets a unit [nichik, BGG 3454879; rulings 6].
- **When:** a skill is already face-up in a turn slot. Turn 4 is the best Gold: up to three resolved skills to copy [GoatRogan]. Turn 1 Gold is a plain Attack 4.
- **Copy priority:** Breath on a hero who is not the one she just hit; Nibble/Gnaw on a second minion; the Blue swap to leave or to bring an ally in; Silver's move-both to step off the hex she just attacked from.
- **Do not:** copy a card she discarded to block. Do not copy onto the unit she just attacked. Do not play Gold before any skill and expect the text to matter [ADVICE; GoatRogan].
- **In hand:** Defense 2. The threat is the copy, which the enemy can see coming once the skill is resolved.

`widget-handicap` Follow Along is the same at init 10, Attack 3, Movement 2, and the target must be a hero.

### `widget-silver` Dragon Bond — Skill, init 6, Defense 6, Radius 2, no Movement

Choose one: place Pyro in an empty space in radius 2 (reposition if Pyro is already out [rulings 2; M1NGShENG, BGG 3454879, citing Place]); or, if Pyro is out, move both herself and Pyro up to 2 each, in either order.

- **For:** getting the dragon onto the board, or a slow double-move after the fast cards have resolved. Init 6 is the reactive window [GoatRogan].
- **Place:** not on a spawn the next push will fill [GoatRogan, the day he wrote the guide; ADVICE]. On a line through a hero for Breath, or adjacent to the minion Gnaw wants, or on a gap.
- **Move-both:** 2 each, not 2 shared [GoatRogan to Ruwakh, 2025-02-05]. Use it to step off a hit after Gold copies it, or to walk both toward the wave.
- **Do not:** play Silver just to relocate Pyro one hex when Red will move Pyro up to 3 or 4 after a minion. Do not spend Defense 6 when a revealed attack needs that 6 and Blue can do the swap.
- **In hand:** the best block. No Movement icon: she cannot Fast Travel on Silver.

### `widget-red-1` Diversionary Strike — Attack, init 8, Attack 5, Defense 6, Movement 4

Adjacent attack. After: move Pyro up to 2. The attack does not need Pyro. If the attack does not connect, the move does not happen [rulings 10]. "Up to" includes 0 [Ruwakh, BGG 3454879, 2025-03-29, from p.10].

### `widget-red-2a` Diversionary Attack — item: Initiative. Init 8, Attack 5, Defense 6, Movement 4

Pyro moves up to 3. `widget-red-3a` Diversionary Assault (item: Initiative, init 9, Attack 6, Defense 7): up to 4.

- **For:** the safe gun and the setup. Bonk a minion, walk Pyro onto the next one for Gnaw, or onto a line for Breath, or into a gap [GoatRogan].
- **When:** she is adjacent to a minion and Pyro is out of place. Also the block (Defense 6, then 7).
- **Do not:** rush the upgrade off tier 1. Tier-1 Red is already Attack 5, Defense 6 [GoatRogan].

### `widget-red-2b` Airborne Attack — item: Defense. Init 8, Attack 3, Defense 4, Movement 4, Radius 3

Before the attack: she may swap with Pyro in radius. Then an adjacent unit. `widget-red-3b` Airborne Assault (item: Range, init 9, Attack 4, Defense 5, Radius 3): she may also swap after the attack.

- **For:** a blink onto Pyro, a hit, and (tier 3) a blink back. The second way, with Silver-copied-by-Gold, to move and attack [GoatRogan]. Defense drops to 4–5. She plays safer.
- **When:** Pyro is adjacent to the target and she is not, and the arrival is not a kill zone. After the hit, swap back if an enemy still to act can reach the new hex.
- **The swap is offered first,** and the primary is available whenever the swap is, even if she is not yet adjacent to a target [rulings 5].

### `widget-green-1` Fiery Breath — Skill, init 4, Defense 1, Movement 2, Range 2

An enemy hero in range of Pyro and in a straight line from Pyro discards, if able. No Pyro: no primary [rulings 3].

### `widget-green-2a` Flaming Breath — item: Attack. Init 4, Defense 2, Movement 2, Range 3

Same. `widget-green-3a` Scorching Breath (item: Attack, init 3, Defense 2, Range 3): discard or defeated.

- **For:** the line. Play when a hero might stand still, or threaten the hex they want; if they leave, take the Movement 2 instead [GoatRogan]. Copied by Gold, it is a second hit at init 11, and it cannot be the hero she just struck.
- **When:** Pyro has a straight line of length ≤ range to a hero. Scorching Breath on a hero with no card is a kill from Pyro's hex, not from hers.
- **Do not:** stand Pyro on the spawn. Do not fire with no line and no Pyro.

### `widget-green-2b` Nibble — item: Initiative. Init 4, Defense 2, Movement 2, Range 3

Remove an enemy minion in range that is adjacent to Pyro. Remove Pyro. No coins [GoatRogan; the card says remove]. `widget-green-3b` Gnaw (item: Movement, init 3, Defense 2, Range 3): defeat that minion (coins, and only an enemy — she cannot defeat a friend [rulings 4]), then remove Pyro.

- **For:** the second minion, paid for with the dragon. Telegraphed: Pyro must already be adjacent, and she must be in range 3 and willing to stand still [GoatRogan]. Gold plus this is two minions at init 11 if Pyro was walked up by Red earlier. Tier 2 pays no coins; tier 3 does. A surprise level into Nibble after winning the middle is how a beach gets cleaned [GoatRogan].
- **When:** the minion changes the battle by 15, or it is a heavy she could not otherwise reach, and she can replay Silver next turn to bring Pyro back.
- **Do not:** eat Pyro for a minion that was already going to die in the battle, or when Breath would have discarded the hero who is winning the round.

### `widget-blue-1` All Aboard — Skill, init 9, Defense 3, Movement 3, Range 2

Swap Pyro in range with herself or with a friendly hero in range.

### `widget-blue-2a` Take-Off — item: Defense. Init 10, Defense 3, Movement 3, Range 3

Same. `widget-blue-3a` Safe Landing (item: Defense, init 10, Defense 4, Range 3): she may move Pyro 1 first, then the swap. The swap needs Pyro in range again; moving Pyro out of range stops the action [rulings 9].

- **For:** the ally teleport. Pyro beside their target, Blue at init 9–10, they are there for their attack. Or she tags out. Both Pyro and the friend must be in her range [GoatRogan]. Safe Landing's 1-step is to bring Pyro into range or onto the right side of the friend, not to walk it away.
- **Gold copy:** a second swap after she has hit someone else. Not on the unit she hit.
- **In hand:** Defense 3–4. Worse than Silver and primary Red.

### `widget-blue-2b` Drag Off — item: Attack. Init 10, Defense 3, Movement 3

Move Pyro and an enemy unit adjacent to Pyro, each 2 or 3 spaces, the same direction. `widget-blue-3b` Carry Away (item: Radius, init 10, Defense 4): the same, ignoring obstacles. Both moves are mandatory. The engine asks the two distances separately ("each"); a community note that they must be the same number is the other reading, and the card's "each" plus the engine win [rulings 7]. If both moves cannot be completed, there is no primary.

- **For:** drag a hero off a minion or onto an ally, or serve a minion to a friend. Not an escape that drags the hero she just hit with Gold: different unit [GoatRogan; rulings 6].
- **When:** Pyro is adjacent to the unit, and 2 or 3 empty hexes exist in one direction for both (Carry Away may pass obstacles and must still end empty).
- **Do not:** aim through a wall on Drag Off. Do not pick 3 when only 2 exists.

### `widget-purple-4` Dragon Knight

After she performs a Movement action, she may perform the primary of one face-up skill. Face-up includes the revealed card, resolved cards, and discarded cards that are not face-down. Not hand, not items [rulings 8]. Fast Travel is not a Movement action. So a walk on Red or Blue or Green can fire Breath, or Silver's move, again. Gold's copy is a separate, earlier trick.

## 3. Upgrade path

GoatRogan refuses a single build. The levers:

1. Hold Red at tier 1 until the game shows whether Pyro is late (stay `widget-red-2a` / `widget-red-3a`, Initiative item, Defense 6–7, Pyro walks 3 then 4) or she needs the blink (`widget-red-2b` / `widget-red-3b`, Defense item then Range item, Defense 4–5).
2. Primary Blue (`widget-blue-2a`, `widget-blue-3a`) when friends will stand on the swap. Alt Blue when enemies are willing to stand next to Pyro. Primary is the safer default [GoatRogan].
3. A quick `widget-green-2b` Nibble after a won middle wave, knowing it pays no coins until `widget-green-3b`. Otherwise Breath, and the Attack item, when heroes stand on lines.
4. Initiative items (`widget-red-2a`, `widget-red-3a`, `widget-green-2b`) make Gold at 11 into something that acts before the escapes. They also speed Silver off init 6; that is usually acceptable [GoatRogan].
5. If tier 3 is unclear, stay on primary Blue: the Defense item, and Safe Landing's lack of a Radius icon means the Radius item is elsewhere — Carry Away is the Radius item, Creeping is not hers. Silver's place is radius 2 with no upgrade on that icon unless an item says Radius. `widget-blue-3b` is the Radius item. Take it when Silver's place is the bottleneck, not "because primary Blue has radius". GoatRogan's "primary Blue comes with a Radius item" does not match the catalog (Safe Landing's item is Defense). Card text wins. The Radius item is Carry Away.

**Tuck:** Defense from Take-Off / Safe Landing wants to be equipped while Silver is being played for the skill, because Silver's 6 leaves the hand. Attack from Breath wants to be equipped so Gold's 4 becomes 5 or 6. Initiative from the primary Red wants to be equipped so the copy happens before they walk. Range (Airborne Assault) lengthens Breath, the swap, Nibble. Radius (Carry Away) lengthens only cards with Radius: Silver is the one. Tuck Radius when she is not placing Pyro at the edge of 2.

**Phase:** turns 1–2 of the game, place Pyro off a spawn, on a line or a gap. Do not Gold first. Levels 1–3, Red stays a respectable minion tool and Silver is the dragon. Levels 4–6, either the double-take (Nibble then Gnaw, primary Red) or the blink-strike (Airborne, Breath). Level 7, every walk is a skill; then Red's Movement 4 plus a copied Breath is the round, and she should walk more and Hold less.

## 4. Synergies and counters

**With:** an ally who will attack the hex she names. She should say which Blue she is on [GoatRogan]. A finisher who is too slow to reach the target: Pyro is already there, Blue brings them in at init 9–10, they hit. She is a bad partner for a hero who needs her to stand in melee and soak.

**She fears:** Clear. One attack spent on Pyro deletes Breath, Gnaw, the swap and the drag until she spends Silver [ADVICE; GoatRogan: "punch Pyro in the face" when that attack also denies her a minion]. Brogan's kicks and anyone else who moves tokens. A spawn point under Pyro at the moment the lane pushes [GoatRogan]. Pressure on the turn she must stand still to replay Silver: Defense 6 is in that card, so the turn she plays it she is on Red's defense only if Red is still in hand. No heal.

**Against her:** if Pyro is adjacent to the minion she wants and also adjacent to you, Clearing Pyro can be worth more than the minion. If Pyro is not out, hit her or discard her on the Silver turn. Do not stand on a straight line from Pyro at Breath's range. Do not stand on the hex her ally will be swapped onto. Gold's copy cannot touch the unit she just hit, so the second body is the one to defend.

## 5. Common mistakes

- Gold on turn 1, nothing resolved, then a plain Attack 4 and a wasted text [GoatRogan].
- Pyro on a spawn [ADVICE; GoatRogan].
- Copying the skill onto the same unit [nichik].
- Copying a discarded block. Gold cannot. The Ultimate can, if it is face-up.
- Nibble for a minion that was not worth losing Pyro for a full round.
- Safe Landing: stepping Pyro out of range and fizzling the swap [rulings 9].
- Treating "2 spaces" on Silver as a shared pool. It is 2 each [GoatRogan].
- Treating Drag Off's two distances as one number. The engine asks each [rulings 7]. GoatRogan's "same amount" is the contradicted reading.
- Holding Silver forever because Defense 6 feels good, so Pyro never arrives. Or playing it every turn and never having a block.
- Fast Travel to "trigger" Dragon Knight. It does not [rulings 8].

## 6. Heuristic rules

`evalTerms`:

- Pyro on the board, not on a spawn, within 3 of an enemy hero or an enemy minion: +8. Pyro on a spawn: +2 (it dies on the push) and a penalty of −4 if a push is possible this round. Pyro off the board: 0, and every Pyro card's primary is worth 0. [HERO]
- A resolved skill in a turn slot while Gold is in hand: +4, because the copy exists. A discarded skill does not add this for Gold; it does add +2 once Dragon Knight is in force and she still has a Movement action. [HERO]

`threatAdjust`: Breath's origin is Pyro's hex, straight lines, the card's Range. Not Widget's hex.

1. **H1** [PLAY_CARD] [`widget-silver`] **WHEN** Pyro is off the board, or is on a spawn, or is more than range 3 from the fight **THEN** place Pyro in radius 2 on an empty hex that is not a spawn, preferring a straight line through an enemy hero or a hex adjacent to an enemy minion, and a gap if neither exists. **WEIGHT** +8. **SOURCE** [ADVICE; GoatRogan; rulings 1] **[HERO]**

2. **H2** [PLAY_CARD] [`widget-gold`] **WHEN** no skill is resolved **THEN** play Gold only to take a minion that would otherwise be lost or to kill a hero Attack 4 actually defeats. Do not play it "to start the combo". **WHEN** a skill is resolved and the copy can hit a different unit **THEN** Gold is the turn-4 card, earlier if the copy would miss its window. **WEIGHT** the attack plus the copied skill. **SOURCE** [ADVICE; GoatRogan; nichik BGG 3454879] **[HERO]**

3. **H3** [CHOOSE_CARD] [Gold's copy, Dragon Knight] **WHEN** several skills can start **THEN** Scorching Breath on a hero with no card, else Breath on a hero who will lose a card the team can use, else Gnaw/Nibble on a minion worth 15, else the Blue that saves her or delivers an ally, else Silver's move off the hex. Never the unit she just attacked. **SOURCE** [nichik; rulings 6] **[HERO]**

4. **H4** [PLAY_CARD] [`widget-green-1`, `widget-green-2a`, `widget-green-3a`] **WHEN** Pyro has a straight line of length ≤ the card's Range to an enemy hero **THEN** fire. Scorching Breath: a hero with no legal block is the kill. **WHEN** they can step off the line and she has Movement 2 that reaches a better hex **THEN** move instead of a whiff. **SOURCE** [GoatRogan; rulings 3] **[HERO]**

5. **H5** [PLAY_CARD] [`widget-green-2b`, `widget-green-3b`] **WHEN** an enemy minion is adjacent to Pyro and in her range, the minion is worth 15 (or is the heavy, or Gnaw's coins matter), and Silver is in hand or the round will end so Pyro can return next round **THEN** eat it and remove Pyro. **ELSE** do not. **WEIGHT** 15, plus 2 coins on Gnaw only, minus 8 if Pyro cannot return before her next attack. **SOURCE** [GoatRogan; CARD] **[HERO]**

6. **H6** [PLAY_CARD] [`widget-red-1`, `widget-red-2a`, `widget-red-3a`] **WHEN** she is adjacent to a minion or a hero she should hit **THEN** hit, then move Pyro up to 2/3/4 onto the next Breath line, the next Gnaw adjacency, or a gap. If Pyro is already perfect, move 0. **SOURCE** [GoatRogan; Ruwakh BGG 3454879] **[HERO]**

7. **H7** [CONFIRM] [`widget-red-2b`, `widget-red-3b`] **WHEN** swapping with Pyro puts her adjacent to a target she was not adjacent to, and the arrival is not in a revealed kill **THEN** swap, attack, and on Assault swap back if an enemy still to act threatens the arrival. **SOURCE** [rulings 5; GoatRogan] **[HERO]**

8. **H8** [PLAY_CARD] [`widget-blue-1`, `widget-blue-2a`, `widget-blue-3a`] **WHEN** Pyro is in range and adjacent to an enemy an ally wants, and that ally is in range **THEN** swap the ally with Pyro. **WHEN** an attack she cannot block will hit her and Pyro is in range on a safe hex **THEN** swap herself. Safe Landing: the 1-step only if it keeps Pyro in range and improves the swap. **WEIGHT** +15 if the ally then takes the minion, or her life. **SOURCE** [GoatRogan; rulings 9] **[HERO]**

9. **H9** [CHOOSE_NUMBER / CHOOSE_SPACE] [`widget-blue-2b`, `widget-blue-3b`] **WHEN** one direction moves the enemy unit onto an ally's attack or off the minion, and both pieces have a legal end at 2 or at 3 **THEN** that direction. Pick the two distances independently so both moves exist. Carry Away may cross obstacles. **SOURCE** [rulings 7; GoatRogan, distances corrected] **[HERO]**

10. **H10** [CHOOSE_OPTION] [`widget-silver`] **WHEN** Pyro is out and both she and Pyro are on the wrong side of a coming push **THEN** move both up to 2 toward the next zone. **WHEN** Pyro is badly placed but she is safe **THEN** the place bullet, onto a non-spawn. **SOURCE** [GoatRogan] **[HERO]**

11. **H11** [DEFEND] **WHEN** Silver is in hand and its Defense 6 is the card that beats the attack, and Pyro is already well placed **THEN** block with Red if Red also beats it, and keep Silver. **WHEN** only Silver beats it **THEN** spend Silver. **SOURCE** [CARD; GoatRogan on the swapped defense] **[GENERAL]**

12. **H12** [PLAY_CARD] [any] **WHEN** Pyro is adjacent to an enemy who has an attack and a reason to Clear, and she can swap or move Pyro off that adjacency before they act **THEN** do that before Breath. **WEIGHT** +8 to not lose the token. **SOURCE** [ADVICE; GoatRogan] **[HERO]**

13. **H13** [UPGRADE_CARD] **WHEN** Pyro is chronically out of position **THEN** primary Red before alt Red. **WHEN** she is blinking onto kills and Defense items exist **THEN** Airborne. **WHEN** a push was just won and the enemy beach is full **THEN** Nibble now, accept no coins until Gnaw. **SOURCE** [GoatRogan] **[HERO]**

14. **H14** [KEEP_UPGRADE] **WHEN** friends are reliable **THEN** Take-Off / Safe Landing. **WHEN** enemies camp Pyro **THEN** Drag Off / Carry Away. **SOURCE** [GoatRogan] **[GENERAL]**

15. **H15** [CONFIRM] [`widget-purple-4`] **WHEN** she just took a Movement action (not Fast Travel) and a face-up skill can start **THEN** yes, same priority as H3. A discarded face-up Breath counts here and does not count for Gold. **SOURCE** [rulings 8] **[HERO]**

16. **H16** threatAdjust [`widget`] **WHEN** Pyro is out and Breath is unplayed **THEN** straight lines from Pyro out to that card's Range are discard threats (or kills, on Scorching Breath, against an empty hand), at Breath's initiative, and again at init 11 if Gold is unplayed and Breath is already resolved. **SOURCE** [CARD; rulings 3] **[HERO]**

17. **H17** [PLAY_CARD] [against her] **WHEN** an attack can Clear Pyro and the alternative is a minion she would have taken with Pyro **THEN** Clear Pyro. **WHEN** Pyro is off the board and Silver is her likely card **THEN** attack her this turn if the attack beats Red's Defense and she may have spent Silver. **SOURCE** [GoatRogan; ADVICE] **[HERO]**

18. **H18** [CHOOSE_SPACE] [placing or moving Pyro] **WHEN** the hex is a minion spawn **THEN** avoid it unless no other hex sets up Breath or Gnaw. **WEIGHT** −6 on a spawn. **SOURCE** [GoatRogan; rulebook spawn vs token] **[HERO]**

19. **H19** [CHOOSE_ACTION] **WHEN** the primary needs Pyro and Pyro is absent **THEN** do not play that card. Play Silver, or a Red attack that does not need Pyro, or move. **SOURCE** [rulings 3; AI.md holding] **[HERO]**

20. **H20** [PLAY_CARD] [`widget-silver`] **WHEN** Gold is in hand, Pyro is out, and she is about to attack **THEN** do not spend Silver on a place that Gold could copy after the hit. Save Silver's move-both as the copy, and attack with Red or Gold. **SOURCE** [GoatRogan: the enticing Gold copy is the move] **[HERO]**

## 7. Sources

- Catalog `heroes/widget.json`, handicap `widget-handicap`. Advice is the four dashboard lines. FAQ count 0.
- `heroes/WidgetHero.kt`, `core/game/rulings/widget.md`.
- [BGG 3454879](https://boardgamegeek.com/thread/3454879) Widget & Pyro spotlight (2nd printing), GoatRogan, 2025-02-05, playtester (STRATEGY.md treats GoatRogan as a playtester on BGG 2908021). Designer `nichik`, 2025-02-17: "targeting a different unit" stops a double discard; it does not require the copied skill to target a unit.
- Ruwakh, 2025-02-05 and 2025-03-29, community: Silver is 2 spaces each; Diversionary's Pyro move is not a condition of the attack.
- M1NGShENG, 2025-06-05: Place can reposition Pyro. Matches rulings 2.
- Smitty_80, 2025-02-22, community: argues the copied skill's target must be a unit. Nichik's post is the designer reading the engine follows.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Pusher / Support, with Cryomancy unsure.
- GoatRogan's "same distance" on Drag Off is not used; the card says "each" and rulings 7 agrees. His "primary Blue comes with a Radius item" does not match Safe Landing's Defense item.
- No Widget Build & Go thread exists in the strategy forum. No Widget thread after 2025-06. Reddit did not turn up a Widget guide.
