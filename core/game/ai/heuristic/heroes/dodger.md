# Dodger (`dodger`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/dodger.json`. Engine: `DodgerHero.kt`.
Rulings: `core/game/rulings/dodger.md`. Complexity 1. Stats: Attack 3/3, Defense 2/5, Initiative 7/7, Movement 5/5.
Catalog roles: damager, sniper, pusher, farming, disabler. No tokens. Ultimate changes how her own texts count spaces.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances. `CTX hero` needs a fact only this playbook tracks.

1st-printing Dodger is a different deck (gold Attack 1 with +range, silver/green/blue that may remove a friendly minion, red that gains Attack when the target has a discard or stands next to an empty spawn). None of those numbers are used below. Card text wins.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Ranged | Finisher who shoots from beside an empty spawn. Not a tank (Defense 2/5) and not a melee assassin. [BGG 3779890] calls her Finisher/Pusher; the bot's distance is the ranged one. |
| idealDistance | 2 | `dodger-gold` and `dodger-red-1`/`2b`/`3b` are Range 2. Primary red at tier II+ snipes discarded heroes at Range 3; raise ideal distance to 3 only while `dodger-red-2a` or `dodger-red-3a` is the kept red and an enemy already has a card in the discard. |
| aggression | 0.55 | She wants an early discard, then a turn-4 shot. She does not dive a fresh wave. |
| laneWeight | 0.50 | A minion take opens the spawn her kit reads. The kill is the payoff of that spawn, not a separate plan. She also stalls when the push would strand her outside the Battle Zone. |
| keepDefender | true | `dodger-blue-1`/`2a`/`3a` is the block (Defense 3/4/4, +2/+2/+4 with the spawn condition). Red is Defense 5–6 but it is the finisher. Silver is Defense 1. |
| upgradeOrder | see §3 | Green first for the coin when she is not the focus target; blue first when she is. Red is the kill card either way. |

She wants to stand in the Battle Zone, next to empty minion spawn points, outside the cluster of enemy minions. Lane and kill meet when she takes a minion: the spawn goes empty, Death Trap and the shields turn on, and a hero who already discarded is in range of the red.

Every attack card of hers is ranged, including the adjacent bullet (`dodger-gold`, `dodger-red-1`/`2a`/`3a`). A Block that stops only non-ranged attacks does not stop them [BGG 3501956].

## 2. Card-by-card

Initiative band used below: her Gold 12, Blue 10 then 11, Red 9 then 10, Silver 7, Green 3 then 2. She is fast for every colour except Green. Items add +1 to every card that has the icon. "Empty spawn" below means a printed minion spawn point, in a current Battle Zone, with nothing on it. Both teams' spawns count. "In radius" is distance 1..Radius from her, not her own hex (`rulings/dodger.md` 1).

### `dodger-gold` Dread Razor — Attack 2, Defense 1, Movement 1, Range 2, Initiative 12, Ranged

Choose one: a unit adjacent to her, or, only if she is adjacent to an empty spawn in the Battle Zone, a unit in range. Engine: `CHOOSE_OPTION` when both bullets have a target, else the only legal bullet, then the attack. Handicap `dodger-handicap` Rusty Razor replaces this in a 5-player game: Attack 1, Initiative 11, and both bullets are heroes only. Do not plan a minion take on the handicap gold.

For: the turn-4 finisher and the fast minion shot. A hero with no cards left dies to Attack 2. [BGG 3474317] the range is how she finishes weakened heroes; [BGG 3499269] turn 4 gold after a turn-3 Death Trap, on a hero with no cards left. [ADVICE] she is stronger when spawn points are empty. Movement 1 is not a travel tool.

When: she is already adjacent to an empty Battle Zone spawn and either (a) an enemy hero in range 2 has no cards in hand, or (b) a minion in range 2 is the one that swings the lane and no such hero exists. Initiative 12 acts before typical golds (11) and before almost every red, so the target is still where it was revealed.

When not: turn 1, as a walk. The adjacent bullet into a hero who still holds a Defense 5+ card (Attack 2 does not stick). Any ranged bullet when she is not adjacent to an empty spawn — the engine will not offer it. Defense 1: the worst card to be caught holding as the only defender.

With `dodger-purple-4` in the Ultimate zone, every empty space counts as an empty friendly spawn during this action, so the ranged bullet is legal whenever she has an empty neighbour.

### `dodger-silver` Death Trap — Defense 1, Initiative 7, Radius 4, Skill

An enemy hero in radius who is adjacent to an empty spawn in the Battle Zone discards a card, if able. No movement value. Engine: `CHOOSE_UNIT` among heroes who meet both conditions; if none, the primary is not offered (it fizzles). She chooses the hero [Discord, Arty, 2025-02-16, p.10]. A hero with an empty hand may be chosen; "if able" then does nothing.

For: the early discard that makes the red legal and the gold lethal. [ADVICE] "Play your silver card on turns when you expect enemy heroes to attack minions." [BGG 3474317] earlier in the round, so both attacks are still in hand to follow up. [BGG 3499269] the "Opener of the Way": silver on turn 3 into a hero who took the twin and is standing on the emptied spawn, gold on turn 4.

When: at her resolution an enemy in radius 4 is adjacent to an empty Battle Zone spawn, and that hero still has a card. Initiative 7 is after Gold, after Blue, and after a Red at 8–9, so a faster hero who just killed a minion is often still standing next to the hole. It is before Green, so it does not punish a Green take unless a spawn was already empty.

When not: no legal hero. Do not play it to "go first"; it does not relocate her, and Defense 1 will not save her. Do not spend it on a hero with 3+ cards if a Range-2 minion take on gold is also available and the lane is the game. Holding it is only correct when a spawn is about to empty and she can fire later this round.

### `dodger-red-1` Littlefinger of Death — Attack 4, Defense 5, Movement 4, Range 2, Initiative 9, Ranged

Choose one: a unit adjacent to her, or a hero in range who already has one or more cards in the discard. The discard is checked at targeting and again as the attack is made (`rulings/dodger.md` 2). Minions are only on the adjacent bullet. Engine: `CHOOSE_OPTION` then `CHOOSE_UNIT`.

For: the kill card and, until tier III, her only adjacent minion attack on red. [BGG 3474317] gold and silver put a card in the discard; this is the attack that threatens the kill. [BGG 3474317] the adjacent-only minion take "forces you into positions where your defenses may be even lower." Defense 5 is her best early block if the blue condition is down.

When: a hero in range 2 has a card in the discard and Attack 4 (plus items and minion modifiers) meets what they have left, or they have 0–1 cards in hand. Or she is adjacent to the minion that decides the lane and no such hero exists.

When not: the ranged bullet at a hero with an empty discard pile. Into a hero who still holds a Defense 6+ and is not on a kill clock, if an adjacent minion would open a spawn she needs. Do not Fast Travel on it [general: red is the attack].

### `dodger-red-2a` Finger of Death — Attack 4, Defense 6, Movement 4, Range 3, Initiative 9, Ranged, item Initiative

Same text as `dodger-red-1` at Range 3 and Defense 6. [BGG 3474317] the tier-II snipe: follow up on turn 3 or 4 against a hero who already discarded. The Initiative item is what the other branch gives her if she keeps Burning Skull instead.

### `dodger-red-3a` Middlefinger of Death — Attack 5, Defense 6, Movement 4, Range 3, Initiative 10, Ranged, item Movement

Choose one or both, on different targets, in either order. Engine offers both orders only when the two bullets have two different legal targets (`rulings/dodger.md` 3). A hero who discards to survive the first attack is the first target and cannot be the second. The second bullet stops the action if it has no target when reached.

For: one action that takes the minion and shoots the hero, or two hero-facing shots when one hero is adjacent and another already discarded. [BGG 3499269] "extra initiative and double attack" — the printed initiative is 10, one higher than Finger, and both bullets may fire. Use both whenever two different legal targets exist. Order: the adjacent minion first when it is granting the ranged hero a defense modifier; otherwise the hero first.

### `dodger-red-2b` Burning Skull — Attack 3, Defense 5, Movement 4, Range 2, Initiative 9, Ranged, item Defense

Target a unit in range. After a connected attack, she may move up to 1 minion adjacent to her 1 space, to a space not adjacent to her. Any minion she can target, friendly or enemy; heavies follow Heavy Minion Immunity. Engine: the attack, then optional `CHOOSE_UNIT` (Decline skips). [Discord, Arty, 2025-03-24] "up to" is 0, 1, or 2 on the tier-III card; the same reading is 0 or 1 here.

For: the reliable ranged minion take. No discard and no empty-spawn test. [BGG 3474317] the after-step clears a spawn, passes a minion to an ally, or pulls an enemy minion off her so a slower counterattack loses the defense modifier. Initiative 9 is fast for a red, which is why the modifier strip lands before most reds.

When not: as the finisher (Attack 3 before items). Do not move a friendly minion off a hex that was about to give her or an ally a defense modifier against an attack still unresolved. Do not move a minion out of the Battle Zone expecting it to stay; it walks back after the action (p.18). The 2024 BGG note that Burning Skull pushes units away from the defeated target [BGG 3417802] quotes 1st-printing text. This card does not.

### `dodger-red-3b` Blazing Skull — Attack 4, Defense 5, Movement 4, Range 2, Initiative 10, Ranged, item Radius

Same, up to 2 minions, one at a time, each to a space not adjacent to her. The Radius item widens Death Trap (4), the rituals (3), the shields (2), Weakness/Enfeeblement (4), and Necromastery (1). It does not add Range.

### `dodger-green-1` Dark Ritual — Defense 2, Movement 2, Initiative 3, Radius 3, Skill

If 2 or more empty spawns are in radius in the Battle Zone, gain 1 coin. Fewer than 2 and the primary is not offered (`rulings/dodger.md` 5). Engine: no choice; coins are gained.

For: a coin at the end of the turn, after earlier actors have emptied spawns. [BGG 3474317] "if you find yourself at the end of a round and already in a good position, spam this card to accelerate." Initiative 3 is last, which is when the count is honest.

When not: as a defender (Defense 2). When the lane is one minion from a push she could take with red. One coin does not beat a minion of margin.

### `dodger-green-2a` Darker Ritual — Defense 2, Movement 2, Initiative 3, Radius 3, Skill, item Defense

Same, gain 2 coins. [BGG 3499269] both of his builds keep this first "because Dodger likes money." Keeping it tucks `dodger-green-2b`, whose item is Attack — the item gold (2) and the skull (3) need.

### `dodger-green-3a` Darkest Ritual — Defense 3, Movement 2, Initiative 2, Radius 3, Skill, item Defense

Same 2 coins, then, only if she has the Ultimate, gain an Attack item (a spare from the box; the engine adds a permanent Attack icon, and does not count the physical spares). The item sentence is not reached if the 2-spawn test failed (`rulings/dodger.md` 5). With the Ultimate that test is almost always true. Each play adds another Attack icon. Initiative 2 is last.

### `dodger-green-2b` Necromancy — Defense 2, Movement 2, Initiative 3, Skill, item Attack

Respawn a friendly minion on an empty friendly spawn point adjacent to her in the Battle Zone. FAQ: any friendly type that was in this wave and has been defeated or removed, onto the spawn itself, not beside it. Not an enemy spawn. The designer's 2022 "any spawn point" [BGG 2948110] is 1st printing; `rulings/dodger.md` 6 keeps the 2nd-printing "friendly" word. Engine: `CHOOSE_OPTION` when several lane/type pairs are missing, then `CHOOSE_SPACE`.

For: a minion that swings the battle count, or a body on a hex an ally needs. [BGG 3474317] it stalls the wave (the enemy can farm the new body) and is the only "bring them back" in the roster. The spawn must be friendly.

When not: when that empty friendly spawn is one of the two she needs this turn for a shield, a ritual, or gold's ranged bullet. Respawning fills it. When the wave has no missing friendly minion (primary not offered).

### `dodger-green-3b` Necromastery — Defense 3, Movement 2, Initiative 2, Radius 1, Skill, item Range

Same respawn, but the friendly spawn may be in radius 1 (adjacent only, until a Radius item makes it 2). [BGG 3519543] the printed radius is why this card exists: adjacent text cannot be widened; radius 1 can. Initiative 2 also resolves after scary turn-enders. The Range item widens gold and every red.

### `dodger-blue-1` Shield of Decay — Defense 3, Movement 3, Initiative 10, Radius 2, Primary is the defense text

+2 Defense if 2 or more empty spawns are in radius in the Battle Zone. No attack primary. Played as an action it is only movement or fast travel; the bonus applies when the card is discarded to defend (p.14). Rulebook example: Defense 4 + 2 = 6 against a 6.

For: standing in the Battle Zone near empty spawns. [ADVICE] low defense is the weakness; this is how she lives there. [BGG 3474317] stay near the bulk of spawn points, and be next to an enemy minion she can remove to open the second spawn before the hit arrives. Without the condition the block is Defense 3.

When not: do not defend with it against an Attack 6+ if `dodger-red-1` (Defense 5, no condition) meets the hit and the spawn bonus is off. Do not spend it as the action on a turn the red would have killed.

### `dodger-blue-2a` Vampiric Shield — Defense 4, Movement 3, Initiative 10, Radius 2, item Initiative

Same +2, so 6 with the condition, 4 without. The Initiative item is what she gets if she keeps Weakness instead.

### `dodger-blue-3a` Aegis of Doom — Defense 4, Movement 3, Initiative 11, Radius 2, item Initiative

+4 with the condition, so 8, else 4. [BGG 3499269] the tier-III block he keeps on the beginner path. She may be outside the Battle Zone and still gain the bonus: the spawns must be in radius and in the Battle Zone, she need not be. (The public FAQ states the question and does not record Arty's answer; the card text is the rule.)

### `dodger-blue-2b` Weakness — Defense 4, Movement 3, Initiative 10, Radius 4, Skill, item Attack

This turn, enemy heroes in radius have −4 Attack. They can still attack at 0 or below, and a hero still discards to survive a 0 or negative attack [Discord, Arty, 2025-10-12, p.14]. The radius is read from her current space each time a value is read. A hero who movement-actions out before the attack strikes at full Attack [Discord, Arty, 2026-10-18]. A dash that starts outside and ends inside is reduced only if she is still in radius at the "target" step [Discord, Arty, 2025-03-23]. Skill-immune heroes ignore it.

For: saving an ally who must stand, or blunting two attacks she cannot dodge. [BGG 3474317] dodge instead when she acts first and can leave; the card is support. Radius 4 does not require her to stand in the melee. Keeping this card tucks Vampiric Shield, so the item is Initiative and she gives up the conditional +2 block.

When not: as her only plan to stop a minion take. −4 can drop a small attack under a guarded minion's defense, but a real red still connects. Defense 4 with no spawn bonus.

### `dodger-blue-3b` Enfeeblement — Defense 4, Movement 3, Initiative 11, Radius 4, Skill, item Attack

Same, −6 Attack, and those heroes cannot repeat actions (Arien's Violent Torrent, Tigerclaw's repeats, her own nothing). Same walk-out rule. Keeping it tucks Aegis, item Initiative.

### `dodger-purple-4` Tide of Darkness — Ultimate, no stats

While she is performing an action, all spaces count as if they were in the Battle Zone and had a friendly minion spawn point. Read only by her own texts, defense included (`rulings/dodger.md` 9). It does not change which minions exist to respawn, and other heroes' cards do not see it. A minion respawned outside the real Battle Zone walks back after the action.

For: turning the conditions on. Gold's ranged bullet needs any empty neighbour. Death Trap hits any enemy in radius next to any empty space. Rituals and shields see every empty space in radius. Necromancy may target any empty space in reach, then the minion walks back if that space was not really in the Battle Zone — so still prefer a real friendly spawn in the real Battle Zone.

No decision of its own. Once it is in the Ultimate zone, stop treating "not enough empty spawns" as a reason to skip gold, silver, or the ritual.

## 3. Upgrade path

Both published builds keep the coin green first, then the red that matches the plan, then blue [BGG 3499269]. [ADVICE] says the weakness is defense, so blue moves ahead of green when she is the one being hit. "Keep X" means X goes to hand; the other branch of that upgrade is the item, and its catalog `item` icon is the bonus.

Default (first games, and the bot's start): primary red, primary blue, primary green. This is the kill line plus the only conditional block.

1. Tier II first: `dodger-green-2a` if fewer than two enemies are regularly attacking her. The tuck is `dodger-green-2b` (Attack), which is what Attack 2 and Attack 3 need. If she is the focus target, `dodger-blue-2a` first instead (Defense 4, +2; tuck `dodger-blue-2b` is Attack as well).
2. Tier II second: `dodger-red-2a` (Range 3 snipe, Defense 6; tuck `dodger-red-2b` is Defense — the item [ADVICE] is asking for). Take `dodger-red-2b` instead when nobody else on the team can shoot a minion, and accept Attack 3 and the loss of the discard bullet.
3. Tier II third: the blue not taken. If she kept primary red, keep `dodger-blue-2a` unless an ally is the one dying in radius 4, in which case `dodger-blue-2b` and its tucked Initiative item.
4. Tier III first: `dodger-blue-3a` on the default path (the +4 block; tuck `dodger-blue-3b` is another Attack). On the alt-red path, `dodger-red-3b` can wait.
5. Tier III second: `dodger-red-3a` if she stayed primary (both bullets, Initiative 10, Range 3; tuck `dodger-red-3b` is Radius, which widens Death Trap, the shields, and the rituals). If she is on alt red, `dodger-red-3b`.
6. Tier III last: `dodger-green-3a` on the default path (2 coins, and with the Ultimate a free Attack icon; tuck `dodger-green-3b` is Range). Take `dodger-green-3b` when the lane is the win and a radius-1 respawn, widened by the Radius item from step 5, swings it.

The alt kill path [BGG 3499269, "Touch of Death"] keeps `dodger-green-2a`, then `dodger-red-2a`, then `dodger-blue-2b` (tucked Initiative from Vampiric Shield), then `dodger-red-3a`, `dodger-green-3b`, `dodger-blue-3b` (tucked Initiative from Aegis). Two Initiative items put Middlefinger at 12 and gold at 14. She is then a sniper with Defense 4 blues and no +4 shield. Use it when an ally is already the front line.

Items to tuck (the card not kept):

- One Defense item, from tucking `dodger-red-2b` while Finger is kept, or from tucking a shield only after another Defense 5+ card is in hand. Do not tuck the only conditional shield.
- One Attack item, from tucking `dodger-green-2b` or `dodger-blue-2b`, before she tries to kill with gold's 2 or the skull's 3.
- The Radius item from tucking `dodger-red-3b` if Death Trap and Aegis are the plan. The Range item from tucking `dodger-green-3b` if the snipe is the plan. She rarely wants both tucks in the same game; the kept card is the one she will play.
- Initiative is a luxury. Her red is already 9 and her gold is already 12. Take a tucked Initiative item (`dodger-red-2a` or `dodger-blue-2a`) when an enemy gold is also 12 and she must act first.

Branch summary:

| Colour | Default | Why | Switch when |
|---|---|---|---|
| Red | a `dodger-red-2a`/`3a` | Discard-snipe at Range 3, Defense 6, both bullets at III. | The team cannot shoot minions, or she cannot survive adjacent: `2b`/`3b`. |
| Blue | a `dodger-blue-2a`/`3a` | The conditional block is the answer to Defense 2. | An ally is the one being focused and radius 4 covers them: `2b`/`3b`. |
| Green | a `dodger-green-2a`/`3a` | Coins, then an Attack icon once the Ultimate is in. | The lane is the win condition: `2b`/`3b`. |

## 4. Synergies and counters

Teammates: anyone who discards a hero early (Whisper is the example in [BGG 3474317]) or who slides a minion off a spawn and leaves it empty. A stall ally keeps the wave, and her, inside a Battle Zone full of holes. She sets them up by opening a spawn on a fast red, then they walk in.

Enemies she fears: heals at high initiative (Bain, Brogan) that put the discarded card back before her red [BGG 3474317]. Token heroes who stand a body on the spawn (Mortimer). Anyone who kills her before the wave thins out, while radius 2 holds fewer than 2 empty spawns. Focus fire after she has spent the shield.

Playing against her (this is `threatAdjust` and the enemy plan):

- Keep minions on spawn points. Every empty Battle Zone spawn next to her turns on gold's range; every one next to you, inside radius 4, is Death Trap [ADVICE] [STRATEGY §5].
- Do not end a turn adjacent to an empty spawn while `dodger-silver` can still resolve and you still have a card.
- Once you have a card in the discard, Range 3 on `dodger-red-2a`/`3a` is a turn-4 kill. Leave, or retrieve the card. [BGG 3474317]
- Her blue block is 3 or 4, not 5 or 8, while radius 2 holds fewer than 2 empty Battle Zone spawns. That is when to hit her.
- A Block that only stops melee does not stop Littlefinger, even adjacent [BGG 3501956].
- Replacing the holes with tokens denies the count. She does not remove tokens with these cards.

## 5. Common mistakes

- Playing Death Trap because Initiative 7 "should do something" when no enemy is adjacent to an empty spawn. The primary is not offered.
- Treating primary red as a ranged minion attack. Minions are the adjacent bullet only. The ranged bullet requires a card already in that hero's discard.
- Standing in enemy minions with Shield of Decay while radius 2 has one empty spawn, and expecting Defense 5. The printed number is 3.
- Taking Burning Skull and still planning the discard snipe.
- Filling the last empty friendly spawn with Necromancy on the turn gold or the shield needed it.
- Respawning onto an enemy spawn. 2nd-printing text says friendly.
- Expecting the 1st-printing package: sacrifice her own minion, +Attack for a discard, gold Attack 1. Those sentences are not on these cards.
- Firing Middlefinger's second bullet at the hero who just discarded to the first. That hero is excluded.
- Walking out of Enfeeblement and forgetting the enemy can do it to her: a movement action that starts in radius and ends outside strikes at full Attack.
- Upgrading a third coin before she has a Defense 5+ card in hand, then dying on turn 2.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`dodger-silver`] WHEN an enemy hero is in radius 4 and adjacent to an empty Battle Zone spawn THEN prefer Death Trap over a minion walk. WEIGHT +14 if that hero has 1 card in hand (the discard empties them and the next attack kills), +8 if they have 2, +3 if they have 3+ (a spare discard, usually silver). CTX general. SOURCE [ADVICE] [BGG 3474317] [BGG 3499269].

2. `PLAY_CARD` [`dodger-silver`] WHEN no enemy in radius is adjacent to an empty Battle Zone spawn THEN avoid it. WEIGHT −8. A faster enemy (revealed initiative greater than her silver, or before the reveal a possible card of initiative ≥ 8) who is adjacent to a minion on a spawn and inside radius 4 is not this case: they empty the spawn before she acts, so rule 1 applies after the reveal and is only +4 before it. CTX general. SOURCE [BGG 3474317] [REASONED].

3. `PLAY_CARD` [`dodger-gold`, `dodger-handicap`] WHEN it is turn 4, she is adjacent to an empty Battle Zone spawn (or the Ultimate is in and she has an empty neighbour), and an enemy hero in range has 0 cards in hand THEN this is the finisher. WEIGHT the kill. WHEN turn 1 THEN avoid gold. WEIGHT −6. CTX general. SOURCE [BGG 3499269] [BGG 3474317].

4. `CHOOSE_OPTION` [`dodger-gold`, `dodger-handicap`] WHEN both bullets are offered THEN prefer the ranged bullet if it reaches a hero with 0 cards or a minion the adjacent bullet does not reach. WEIGHT the kill, or +15 for that minion. Prefer the adjacent bullet when the only ranged target is a hero who still blocks Attack 2. CTX general. SOURCE [REASONED].

5. `PLAY_CARD` / `CHOOSE_OPTION` [`dodger-red-1`, `dodger-red-2a`, `dodger-red-3a`] WHEN a hero in range already has a card in the discard THEN prefer the ranged bullet over an adjacent minion that does not decide the lane. WEIGHT +12 if they have 1 card in hand, the kill if they have 0, +4 if they have 2+ and Attack 4 (5 at III, plus items) does not meet their remaining defense. Range is 2 / 3 / 3. CTX general. SOURCE [BGG 3474317] [BGG 3474317 post on turn 3–4].

6. `CHOOSE_OPTION` [`dodger-red-3a`] WHEN both bullets have different legal targets THEN choose both. Order the adjacent minion first if it is adjacent to the ranged hero (its death drops that hero's minion modifier before the second attack); otherwise the hero first. WEIGHT +15 for the extra minion, plus rule 5's discard weight. Do not aim the second bullet at the first target. CTX general. SOURCE card text [BGG 3499269] [REASONED].

7. `DEFEND` [`dodger-blue-1`, `dodger-blue-2a`, `dodger-blue-3a`] WHEN empty Battle Zone spawns in radius (2, or 3 with a Radius item, or any empty spaces in radius while the Ultimate is in) number 2 or more AND the bonus is what makes the total meet the attack THEN defend with this card rather than a red whose printed Defense is lower than the boosted total. WEIGHT +6. WHEN the count is under 2 THEN the printed Defense is 3 or 4: prefer `dodger-red-1`/`2a`/`3a` (Defense 5/6/6) if it meets the hit. WEIGHT +4 for the red in that case. CTX general. SOURCE card text [ADVICE] [REASONED].

8. `PLAY_CARD` [`dodger-blue-2b`, `dodger-blue-3b`] WHEN two or more enemy heroes are in radius 4, their unresolved attacks would connect, and they have no movement action still to resolve that can end outside the radius THEN prefer the debuff. WEIGHT +8 per attack whose total falls under the defender's defense because of the −4 or −6. WHEN every threatened hero can still movement-action out THEN avoid. WEIGHT −6. A repeat still queued on an enemy (`dodger-blue-3b` only) adds +6 if stopping it saves a second hit. CTX general. SOURCE [BGG 3474317] [Discord Arty 2026-10-18].

9. `PLAY_CARD` [`dodger-green-1`, `dodger-green-2a`, `dodger-green-3a`] WHEN empty spawns in radius in the Battle Zone are 2 or more (always, practically, once the Ultimate is in and two empty spaces are in radius) AND no enemy hero in her attack range has 0 cards THEN take the coins. WEIGHT +1 or +2 as printed, +6 more on `dodger-green-3a` the first time the Ultimate adds an Attack icon (later copies +2; they stack but the first is the one that flips Attack 2 into a threat). WHEN the count is under 2 THEN do not play it as a primary; move on it. CTX general. SOURCE [BGG 3474317] [BGG 3499269].

10. `CHOOSE_SPACE` [`dodger-green-2b`, `dodger-green-3b`] WHEN respawning THEN prefer a real friendly spawn in the real Battle Zone whose new minion swings the lane margin or stands adjacent to an ally who is about to be attacked. WEIGHT +15 if the margin flips, +4 if it only adds a defense modifier. Avoid the spawn if it is one of only two empty spawns in her shield radius and an enemy attack is still unresolved this turn. WEIGHT −8. Never an enemy spawn. With the Ultimate, do not pick a space outside the real Battle Zone (the minion walks back). CTX general. SOURCE [BGG 3474317] rulings/dodger.md 6 and 9.

11. `CHOOSE_UNIT` [`dodger-red-2b`, `dodger-red-3b`] WHEN the attack connected and a minion adjacent to her can step onto a space not adjacent to her THEN move it if that space empties a spawn she will want next turn, hands the minion to an ally's melee, or strips a defense modifier from a hero an unresolved slower attack will hit. WEIGHT +8 / +8 / +6. Decline when the minion is her own defense modifier against an attack still to come. WEIGHT −6 for that move. Stop after one minion if the second (`dodger-red-3b`) has no such space. CTX general. SOURCE [BGG 3474317] [Discord Arty 2025-03-24].

12. `PLAY_CARD` [`dodger-red-2b`, `dodger-red-3b`] WHEN a minion is in range 2 and no hero with a discard is in range of her primary red (or she did not keep primary red) THEN this is the minion card, ahead of gold, unless rule 3's finisher is live. WEIGHT +6 over walking up to melee the same minion, because Range 2 keeps her off the enemy minion that would cut her defense. CTX general. SOURCE [BGG 3474317].

13. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN the lowest tier colour is still tier I THEN upgrade green if she was not attacked twice last round, else blue. Keep the branch in §3. Tuck the Defense item (`dodger-red-2b`) before tucking the only shield. WEIGHT +4 for the first Attack item while gold is still Attack 2, +4 for the first Defense item while her block without the spawn bonus is 3 or 4. CTX general. SOURCE [ADVICE] [BGG 3499269].

14. `KEEP_UPGRADE` [`dodger-red-2a`, `dodger-red-3a`] WHEN kept THEN set idealDistance to 3 on any turn an enemy has a public card in the discard; otherwise 2. CTX hero (branch flag). SOURCE [BGG 3474317] [REASONED].

15. `CHOOSE_ACTION` [`dodger-blue-1`, `dodger-blue-2a`, `dodger-blue-3a`] WHEN the card is the action THEN movement or fast travel only (there is no primary). Prefer a destination in the Battle Zone from which radius 2 already holds 2 empty spawns, or from which one more minion kill would make it so. WEIGHT +5. Do not fast travel onto a hex adjacent to two enemy heroes while this blue is the card being spent. CTX general. SOURCE card text [BGG 3474317].

16. `PLAY_CARD` any WHEN the Ultimate is in the Ultimate zone THEN do not require a printed spawn for gold's ranged bullet, Death Trap, the rituals, or the shield bonus; require an empty space in the same geometric place. Still require a real friendly in-zone spawn for a respawn she wants to keep. CTX hero (ultimate flag). SOURCE rulings/dodger.md 9 [REASONED].

17. `PLAY_CARD` [`dodger-gold`] WHEN she is not yet adjacent to an empty spawn, the Ultimate is not in, and a Movement 4 card would both take a minion and leave her adjacent to the hole THEN play the red (or move on green) and hold gold for the turn the hole exists. WEIGHT rule 3's finisher ×0.6, one turn later. CTX general. SOURCE [BGG 3474317] [REASONED].

### evalTerms

Dodger has no tokens. Score the condition she is actually standing in:

- While `dodger-purple-4` is in the Ultimate zone: +4. Her tests are on for the rest of the match; this is a fact, not a hope.
- While a shield (`dodger-blue-1`/`2a`/`3a`) is in hand and 2 or more empty Battle Zone spawns sit in its radius: +3. She can stand in the wave. 0 if the count is under 2.
- Do not add a point per empty spawn on top of that. The lane evaluation already has the missing minions, and rule 1 scores the discard those holes threaten.

### threatAdjust

When Dodger is the enemy:

- A hex adjacent to an empty Battle Zone spawn, inside radius 4 of her, is a discard hex while `dodger-silver` is in her possible pool. Add +8. While the Ultimate is in, "empty Battle Zone spawn" becomes "any empty space" for this test. Add +8 the same way.
- A hero who already has a card in the discard is inside her attack range at 2 (`dodger-red-1`) or 3 (`dodger-red-2a`/`3a`) even when she is not adjacent. Add +6 while that red is in her possible pool, +12 if it is the known played card.
- Her adjacent bullet is still a ranged attack. Do not step onto her with a Block that only stops melee and expect to be safe [BGG 3501956].
- A hex of hers whose radius 2 holds fewer than 2 empty Battle Zone spawns is where her shield is 3 or 4. Prefer to hit her there. No extra threat on her; this is the attacker's target choice, already in rule 7's mirror: −0 on the spawn-rich hexes, and do not spend a second attacker after the shield has answered the first unless the bonus was off.

## 7. Sources

- Catalog `dodger.json` (2nd printing), including the `advice` field from yvo-niedrich/goa2-faq. Cited as [ADVICE].
- `DodgerHero.kt` and `core/game/rulings/dodger.md` for which decisions exist and the 2nd-printing readings (friendly spawns only, both bullets of Middlefinger, Tide of Darkness only on her own actions, shield bonus on the defense).
- [BGG 3474317] Cryomancy, 2025-03-08, "Dodger Hero Spotlight (2nd Printing)", community. Post 2 Ruwakh asks for match-ups; Cryomancy answers Whisper as a discard ally, Bain and Brogan heals and Mortimer's tokens as counters.
- [BGG 3499269] Cryomancy, 2025-04-19, "Dodger Build & Go Guide", community. Clear the Area and Touch of Death, and the turn-3 silver / turn-4 gold opening. Item icons in this file are the tucked branch's catalog `item`, which is how that guide's "take X for the item" arithmetic matches the cards.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Dodger as Finisher/Pusher.
- [BGG 3501956] DrReyrey, 2025-04-25; AegisToast and the quoted reply: the death-finger cards are ranged attacks even on the adjacent bullet. Community, rules.
- [BGG 3519543] Lonestar_, 2025-05-29, community: Necromastery's radius 1 is what a Radius item widens; Necromancy's "adjacent" is not.
- [BGG 3481942] 2025-03-20, "Second print tier list", community: Dodger called fragile and awkward until the spawns empty, with strong card text. Context only.
- Designer rulings recorded at https://www.cockeyedandcurious.com/goa2/faq (Arty, Discord): Death Trap's target is her choice (2025-02-16); "up to 2" minions means 0, 1, or 2 (2025-03-24); a dash is reduced by Weakness only at the moment it targets (2025-03-23); a 0 or negative attack still requires a discard to live (2025-10-12); Enfeeblement is rechecked after a move, so leaving the radius restores the Attack (2026-10-18).
- [BGG 2901324] Panetierre, 2022-07-17, playtester, 1st-printing spotlight. Not used for numbers. The "may remove a friendly minion" sentences and the +Attack riders are absent from the 2nd printing.
- [BGG 3417802] nichik, 2024-12-06, on a Burning Skull text that pushes units away from the defeated target. That sentence is not on `dodger-red-2b`. Not used.
- [BGG 2948110] nichik, 2022-10-08, Necromancy onto any spawn. 1st printing; the 2nd-printing card says friendly. Not used.
- [BGG 2433317] 2020-05-22, Blazing Skull +4 Attack from a spawn. 1st printing. Not used.
- Optimal Play, "Dodger the Warlock" (YouTube, the boardgamevideo mirror of `3HRuTZZcopM`): no transcript retrieved, so no rule is taken from it.
- No Dodger-specific Reddit thread was found.

## Implementation notes

What `heroes/DodgerHeuristics.kt` encodes, checked against the engine (2026-11-21).

- Implemented: H1/H2 (Death Trap: an enemy hero in radius 4 beside an empty Battle Zone spawn discards; nothing on an empty hand), H3/H4 (Dread Razor: an adjacent unit, or any unit in range while she herself is beside an empty spawn; replaces the general strike estimate; its `CHOOSE_OPTION` scores each bullet), H5 (Finger of Death: an adjacent unit, or a hero in range with a card in the discard; Middlefinger's "Both" options score the sum), H8 (the ritual pays only with 2 empty spawns in radius 3). Tide of Darkness makes every empty space a spawn point for her actions.
- Engine: Death Trap is "discards a card, if able", not "or is defeated"; the draft valued an empty hand.
- Engine: H7 needs no rule. The shield's +2/+4 is already in `DEFEND`'s `defensePreview`.
- Skipped: Burning/Blazing Skull minion moves, Necromancy, Weakness/Enfeeblement, evalTerms (the draft's constant for the ultimate is the same on both sides of every comparison), threatAdjust (the general reach already counts radius 4 for Death Trap).
