# Whisper (`whisper`) — heuristic playbook

2nd printing. Dashboard: Attack 7, Defense 4, Initiative 7, Movement 3–5. Complexity 2. Catalog roles: damager, durable, tactician, pusher. Advice in `heroes/whisper.json`.

Units: `heuristic/DESIGN.md`. **[HERO]** needs empty minion spawn points in the battle zone (a spawn with no obstacle; his own hex and occupied points do not count [rulings/whisper.md 6]) and whether an enemy hero in radius has a non-empty discard. Both are public.

He is an initiator or a finisher, rarely both in one round [Cryomancy, BGG 3589436, 2025-10-09, the line STRATEGY.md quotes]. Mobility is the hole, except Green [ADVICE].

## 1. Identity

Initiator / Finisher [BGG 3779890]. Attack 7 on the dashboard is the Blood Fury / Frenzy number, not the Gold. Gold is Attack 2 at range 3. He scares people with forced choices, then either steps in or lets an ally take the body.

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | Assassin | The kit is discards, a snipe on a full-health hero, and a melee Attack 6–7 that repeats once someone is already hurt. |
| `idealDistance` | 2 | Gold's range is 3. The primary Red wants empty spawns in radius 2 so he can step. Alt Red wants to be adjacent. |
| `aggression` | 0.6 | Pressure every round, but farm early or he never reaches the tier where the stats move [BGG 3589436; Ruwakh, same thread, 2025-10-18]. |
| `laneWeight` | 0.4 | Lesser Evil / Greater Good is a wave tool that becomes a kill tool. He still has to take minions or he falls behind: most tier-2 upgrades do not change base stats [BGG 3599730]. |
| `keepDefender` | false | Nothing in hand is a real wall. Silver (Defense 2, init 13) is the save, and only when an enemy in radius 3 already has a discard. Red's Defense 4–7 is what he actually blocks with, and he often needs Red to move. |
| `upgradeOrder` | an Attack item first, then the Red that matches the job, see §3 | |

## 2. Card-by-card

Both bullets of Gold are ranged, including the adjacent one [rulings 3]. A "Block a ranged attack" stops either.

### `whisper-gold` Swift Justice — Ranged attack, init 12, Attack 2, Defense 2, Movement 1, Range 3

Choose one: a hero in range with an empty discard, and after the attack, if they defended, they perform a Movement action of the card they defended with, full distance, straight; or an adjacent unit.

- **For:** the opener on a healthy hero, the displacement, or a fast melee finish on someone already hurt. Three limits on the snipe: empty discard, they may run, Attack 2 is weak without items [BGG 3589436].
- **The run:** only if they defended and were not defeated. Distance is that card's Movement, items included. They choose the direction. No Movement icon, or no straight line of exactly that length: they stay [FAQ; rulings 1–2]. A limit below the full distance makes the move impossible. Silver, with no Movement icon, is the block that does not move them — Cryomancy's warning.
- **When:** turn 1–2 to hand an ally a wounded hero. Turn 3–4 when the remaining block is known, so the run is a direction he wants (or a kill, if Attack 2 plus items beats every card they have left). Melee bullet: the turn-4 finish, init 12, on a hero who is already adjacent.
- **Do not:** snipe a hero whose Green is Movement 4–5 into his ally. Do not snipe someone who already has a discard; the bullet is not offered. Time it badly and they escape [ADVICE].
- **In hand:** the threat on every full-health hero in range 3. Defense 2.

`whisper-handicap` Vile Accusation is the same choice at init 11, Attack 1, Range 2, and the melee bullet is a hero only.

### `whisper-silver` Death Seeker — Skill, init 13, Defense 2, Radius 3, no Movement

If an enemy hero in radius has a card in the discard, choose one: this turn, immune to enemy actions; or retrieve a discarded card. An immune enemy does not count [rulings 5]. Immunity is enemy actions and their active effects, not his own team, not the minion battle.

- **For:** not dying, or taking back the card he traded. It asks the team to have already drawn blood [ADVICE; BGG 3589436].
- **When:** a lethal attack is coming and the condition is true — immunity. Or he discarded a card he needs and nobody is killing him — retrieve.
- **The 50/50:** if the enemy knows the immunity is his only save, they can play something else and hit him after [BGG 3589436]. If he has another way off the hex, take it and save Silver.
- **Do not:** play it with no wounded enemy in radius (the primary does not start). Do not treat it as Defense 2 against a hit this turn; init 13 means it happens before most attacks only if he plays it, and then he is immune this turn, not holding a number.
- **In hand:** the "I might be immortal" that enemies must respect once anyone is hurt [BGG 3589436, playing against].

### `whisper-red-1` Crimson Trail — Attack, init 9, Attack 3, Defense 4, Movement 4, Radius 2

Before the attack: he may move up to 1 if an empty minion spawn is in radius in the battle zone. Then an adjacent unit.

### `whisper-red-2a` Sanguine Path — item: Defense. Init 9, Attack 3, Defense 4, Movement 4, Radius 2

Up to 1 step per empty spawn in radius, cap 2. `whisper-red-3a` Blood Pilgrimage (item: Movement, init 10, Attack 4, Defense 5, Radius 2): cap 3. The count is taken once, before the move [rulings 6].

- **For:** the finisher that grows as the wave dies. Steps 1 / 2 / 3 at high initiative, low damage, weak Defense [BGG 3589436]. Best on his own beach and when the wave has come back to the centre full of empty spawns.
- **When:** a hero or a minion is within the cap, and a spawn in radius 2 is empty. Also turns 1–2 if Gold is being saved.
- **Do not:** count spawns outside the battle zone, hero spawns, or a spawn he is standing on. Do not expect the steps on a fresh wave.

### `whisper-red-2b` Blood Fury — item: Initiative. Init 9, Attack 6, Defense 6, Movement 4, Radius 5

Adjacent attack. After: if an enemy hero in radius has a discard, he may repeat once on a different target. `whisper-red-3b` Blood Frenzy (item: Range, init 10, Attack 7, Defense 7, Radius 5): repeat up to five times on different targets. The condition is checked once, after the first attack, not before each repeat [rulings 10; the engine disagrees with a design note that said "each time" — the engine wins].

- **For:** the brawl. Attack 6–7, and if it connects on a hero the repeat is live (they just discarded, and they are in radius). Floor: they dodge, he still takes a minion only if he aimed at the minion first. If he hits the hero and they live, the repeat takes the minion [BGG 3589436].
- **When:** adjacent to a hero and a minion, and he can afford to be in melee (Defense 6–7). Frenzy: keep repeating on minions while each one is worth 15 and he is not walking into the next attacker.
- **Do not:** assume five kills. Each repeat needs a new adjacent target at the moment it is taken, and the condition was the first hit's aftermath. Do not stand in melee on this Red if he built no Defense and the enemy still has a big Gold.

### `whisper-green-1` Shadow Step — Skill, init 3, Defense 3, Movement 2, Range 3

Place himself on an empty minion spawn in range in the battle zone. Heavy spawns count. Hero spawns do not [rulings 7].

### `whisper-green-2a` Shadow Walk — item: Attack. Init 2, Defense 3, Movement 2, Range 4

Same, range 4. `whisper-green-3a` Creeping Shadow (item: Radius, init 2, Defense 4, Range 4): or a space in range adjacent to such a spawn. The neighbour must be in range and empty; the spawn itself need not be in range [rulings 7].

- **For:** the teleport. Arrive on the spawn a hero just cleared, or pick a side of the fight after the fast cards have moved [BGG 3589436].
- **When:** init 2–3, a spawn in range is empty or is about to be emptied by someone who acts before him. Creeping Shadow: the adjacent hex when the spawn itself is a bad place to stand (ranged minion, enemy Gold).
- **Do not:** expect a hex that is not a spawn or next to one. He cannot choose "exactly beside them" unless that hex qualifies.

### `whisper-green-2b` Cruel Twist — item: Defense. Init 2, Defense 3, Movement 2, Range 4

Swap with an adjacent unit, or with an enemy hero in range who has a card in the discard. Then move up to 2. `whisper-green-3b` Sealed Fate (item: Defense, init 2, Defense 4, Range 4): the move is up to 3. The swap is mandatory [rulings 11].

- **For:** the hurt hero comes to him, at init 2, and his allies who are too fast to chase simply stand next to him [BGG 3589436]. Also a small escape: swap with an adjacent minion and walk.
- **When:** a hero in range 4 has a discard and the swap puts them where an ally, or his own Red, can hit. Or he is surrounded and an adjacent unit is the door.
- **Do not:** swap a full-health hero (the long swap is not offered). Do not swap onto a hex he cannot leave if the following move is what keeps him alive.

### `whisper-blue-1` Seeds of Fear — Skill, init 10, Defense 5, Movement 2, Radius 2

An enemy unit in radius on a spawn point (minion or hero spawn [rulings 8]) moves exactly 1. A unit that cannot move 1 is not a target.

### `whisper-blue-2a` Sprouts of Panic — item: Initiative. Init 10, Defense 5, Movement 2, Radius 3

Move that unit up to 2. `whisper-blue-3a` Blooming Nightmare (item: Initiative, init 11, Defense 6, Radius 3): up to 3. The "up to" may be 0.

- **For:** send a minion to an ally, or off its spawn so his primary Red gains a step, or so alt-Red has two minions that are not glued to their points [BGG 3589436]. Heroes on spawns are a bonus, not the plan.
- **When:** the minion's new hex is adjacent to an ally or to him, and the spawn it left is the one his Red will count.
- **In hand:** Defense 5–6, his best numeric block.

### `whisper-blue-2b` Lesser Evil — item: Attack. Init 10, Defense 5, Movement 2, Range 3

An enemy hero in range chooses: discard a card if able, or Whisper may defeat an adjacent minion. Either option may be chosen even if it does nothing [CARD; FAQ; rulings 9]. No enemy hero in range: the card cannot be played.

### `whisper-blue-3b` Greater Good — item: Attack. Init 11, Defense 6, Movement 2, Range 3

Discard or be defeated, or Whisper defeats up to 3 adjacent minions. The up-to-3 are chosen together, so a heavy cannot be taken together with the last non-heavy that keeps it immune [FAQ; rulings 9].

- **For:** the lose-lose. Use it when he is ahead on the wave, or when their giving him the minion is what puts him ahead, or when the hero is already facing another discard [BGG 3589436; BGG 3599730]. On the enemy beach the throne makes them panic.
- **When:** init 10–11, a hero in range 3, and at least one of the two options hurts. If he has no adjacent minion, a clever enemy takes that option and nothing happens — do not play it then unless the discard is the point and they will not throw the minion option away for free. They might. Assume they pick the option with the smaller cost, including "no effect".
- **Do not:** play it with no plan, from behind [BGG 3589436].

### `whisper-purple-4` Grim Reaper

On basic actions he may choose one or both, up front, either order [rulings 4]. Gold's two bullets may hit the same unit if it qualifies for both. Silver's two bullets (immunity and retrieve) can both be taken. The handicap's bullets too.

- Turn 4 with both bullets: snipe the healthy hero and cut the adjacent minion, or immune and retrieve, when the condition for each is true.

## 3. Upgrade path

Tier 2 changes items and text, not the printed Attack/Defense/Initiative, except alt Red (Attack 6, Defense 6, Initiative item) [BGG 3599730]. The first upgrades are the items.

**The Executioner (Gold kills)** [BGG 3599730]:

1. An Attack item: `whisper-green-2b` Cruel Twist if he will use the swap, else `whisper-blue-2a` Sprouts of Panic.
2. `whisper-red-2a` Sanguine Path (Defense item, the extra step) or the second Attack item, depending on their Silver's Defense. By level 4, Gold is Attack 4 at range 3.
3. Tier III: `whisper-blue-3a` Blooming Nightmare first (another Attack item, Initiative, the 3-step shove). Then `whisper-green-3a` Creeping Shadow if he needs Defense 4 on Green, else `whisper-red-3a` Blood Pilgrimage (Attack 4, init 10, Movement item, cap 3, and the Range item which lengthens Gold and the long swap). This build dodges; it does not block [BGG 3599730].

**Devil's Bargain (the choice)** [BGG 3599730]:

1. `whisper-blue-2a` (Attack item, minions come to him), `whisper-red-2a` (Initiative, the steps), `whisper-green-2a` Shadow Walk (Defense item, the teleport). Order is whichever stat is failing.
2. Tier III: `whisper-blue-3b` Greater Good first or second. It is the build. Then `whisper-red-3b` Blood Frenzy so a hero who discards to the Blue still loses minions, and the Defense/Attack jump to 7. Then Creeping Shadow. Allies who move minions onto him, and allies who discard early, are the team this build wants [BGG 3599730]. Dodger is the natural partner [same post's opening].

**Tuck:** Attack items stay in the dashboard on the Executioner until Gold is Attack 4 or 5. Defense is the tuck he feels, because nothing else is tanky — tuck a Defense item only when the hand card is the one he will block with (Blue 5–6, or Frenzy's 7). Initiative on Sprouts / Blooming wants to be equipped so Greater Good and Gold stay ahead of the escapes. Range is Frenzy's item and lengthens nothing on Frenzy itself (no Range icon); it lengthens Gold, the shadow place, and the swap. Tuck Range when those are the weapons. Radius is Creeping Shadow's item and lengthens the Red's spawn count and Silver's "wounded enemy" check. Good on the primary-Red path.

**Phase:** round 1, farm, and only snipe if an ally can finish [BGG 3599730]. Levels 1–3 he is a soft Attack 2–3 with a teleport. Levels 4–6 the Gold becomes a real number or Greater Good comes online. Do not skip the minions to "be the assassin" and then sit at tier 1 while the table is at tier 3 [BGG 3589436].

## 4. Synergies and counters

**With:** Dodger (both live on corpses and empty spawns). Anyone who discards on turn 1 so his Silver, his swap, and his Frenzy are all on. A tactician who slides minions onto him for Greater Good and for the double-take. He is a bad solo killer of a careful team; he is the pressure that makes their choice wrong [BGG 3589436].

**He fears:** heal teams (the empty discard never arrives, so Gold's snipe, the swap, and Silver all shrink) [BGG 3589436]. Being moved: his Movement 3–5 is on cards he wanted to attack with, and Green is the only real reposition. Displacement and "you cannot move" (Stone Gaze does not stop his place, which is not a Movement action — card text still places him; a movement cap does stop the Gold victim's forced run and his own Red's before-move if that move is card text... Red's before-move is card text, not a Movement action, so Stone Gaze does not stop it. His Green's place is not a Movement action either. What Stone Gaze stops is Seeds of Fear's "move that unit" only if that is a movement action — the card says "Move that unit", which the engine treats as a move. Do not overclaim.) A durable hero standing on him so the squishy one never has to.

**Against him:** once anyone on your team has discarded, his Silver is live in radius 3. Do not leave the frail hero in Gold's range 3 with an empty discard and a Green that walks them into his team. If you must block the snipe, a card with no Movement icon, or a Movement so long that no straight line exists, keeps you in place [BGG 3589436]. Put a body on the spawn he wants. When he offers Lesser Evil / Greater Good, take the option whose cost is smaller, and take the no-effect option when the other one is a minion swing or a death. Double-heal and "just retrieve" are the hard answers.

## 5. Common mistakes

- Trying to snipe and finish in the same round [BGG 3589436].
- Gold into a hero who blocks with a big Movement and lands on his ally.
- Playing Silver with nobody hurt in radius.
- Counting occupied or out-of-zone spawns toward the Red steps.
- Repeating Frenzy before the first attack has connected, or onto the same unit.
- Playing Greater Good with no adjacent minion and no confidence they will discard.
- Letting the enemy pick for him in the heuristic: the enemy picks, and they may pick nothing [FAQ].
- Ignoring farm because Attack 7 is on the dashboard. That 7 is the tier-3 alt Red [BGG 3599730].
- Using Gold's melee bullet on a minion while a stripped hero is in range 3 and the snipe kills them. Or the reverse: sniping for 2 damage a hero who will block and walk away, when the adjacent minion was the push.

## 6. Heuristic rules

`evalTerms`:

- Each empty minion spawn in his radius 2 inside the battle zone, while primary Red is in hand: +2 (a step he can spend). Cap the bonus at the card's cap (1 / 2 / 3). [HERO]
- An enemy hero in radius 3 with a non-empty discard, while Silver is in hand: +5 (the immunity is available). Same fact while alt Green or alt Red is in hand: +3. [HERO]

`threatAdjust`: Gold threatens every enemy hero in range 3 whose discard is empty, at Attack 2, and the hex they would be forced to run toward is part of the threat if their likely block has a Movement icon.

1. **H1** [PLAY_CARD] [`whisper-gold`] **WHEN** it is turn 1 or 2, an enemy hero in range 3 has an empty discard, and an ally can reach that hero after a straight-line run of the Movement on the card they are most likely to block with **THEN** snipe, unless that run lands them on an ally who would die for it. **WEIGHT** +8 (a strip plus a shove) when the ally can convert, else +2. **SOURCE** [ADVICE; BGG 3589436] **[GENERAL]**

2. **H2** [PLAY_CARD] [`whisper-gold`] **WHEN** it is turn 4, or the hero has one card left, and Attack 2 plus items and modifiers beats that card, and their discard is empty **THEN** snipe. If they are adjacent and the melee bullet kills them even with a discard full of blocks, the melee bullet instead. **WEIGHT** the kill. **SOURCE** [BGG 3589436; BGG 3599730] **[GENERAL]**

3. **H3** [PLAY_CARD] [`whisper-gold`] **WHEN** every hero in range already has a discard, or the only blocks left are Movement 4+ into his team **THEN** do not snipe. Take an adjacent minion with the other bullet if one is there. **SOURCE** [ADVICE; BGG 3589436] **[GENERAL]**

4. **H4** [PLAY_CARD] [`whisper-silver`] **WHEN** an enemy in radius 3 has a discard and a revealed attack would defeat him **THEN** immunity. **WHEN** he is not under that attack and a discarded card is the one he needs next **THEN** retrieve. **WHEN** he has another card that leaves the hex and the enemy can wait out the immunity **THEN** leave and hold Silver. **WEIGHT** his life, or +2.5 for the retrieved card. **SOURCE** [ADVICE; BGG 3589436; rulings 5] **[HERO]**

5. **H5** [PLAY_CARD] [`whisper-red-2a`, `whisper-red-3a`, `whisper-red-1`] **WHEN** the empty-spawn count in radius 2 gives him the step onto a hero whose remaining defense loses to this Attack, or onto a minion **THEN** take the steps (up to the cap) and attack. Prefer the hero on turn 4, the minion otherwise. **SOURCE** [BGG 3589436; rulings 6] **[HERO]**

6. **H6** [PLAY_CARD] [`whisper-red-2b`, `whisper-red-3b`] **WHEN** he is adjacent to an enemy hero and an enemy minion, and this Red's Defense is what he will not need against a still-unresolved attack **THEN** hit the hero. If they are not removed, repeat on the minion (the condition is then true). If they will dodge, hit the minion first; the repeat is then not available unless someone else in radius 5 already has a discard. **WEIGHT** 15 plus the strip, or 15. **SOURCE** [CARD; rulings 10; BGG 3589436] **[HERO]**

7. **H7** [CONFIRM] [Frenzy repeats] **WHEN** another legal target is adjacent and is a minion worth 15 or a hero who dies **THEN** yes, up to five, and stop when the next target is only a chip. **SOURCE** [rulings 10] **[HERO]**

8. **H8** [PLAY_CARD] [`whisper-green-1`, `whisper-green-2a`, `whisper-green-3a`] **WHEN** an empty battle-zone minion spawn in range is adjacent to the hero or minion he wants, or Creeping Shadow's neighbour hex is **THEN** place there, after anyone who acts earlier has had the chance to clear that spawn. **WEIGHT** +6 if it puts him on a target, +15 if that target is a minion he then cannot take this turn — so only place when he still has an attack, or the place is the setup for next turn and this turn had no attack. **SOURCE** [BGG 3589436; rulings 7] **[HERO]**

9. **H9** [PLAY_CARD] [`whisper-green-2b`, `whisper-green-3b`] **WHEN** an enemy hero in range has a discard and swapping with them puts them adjacent to an ally who acts later or to a hex his still-held Red reaches after the bonus move **THEN** swap, then move up to 2 (or 3) onto the follow-up. **ELSE WHEN** he is adjacent to an enemy and the swap-plus-move is the only way off a lethal hex **THEN** swap with a minion and leave. **SOURCE** [BGG 3589436] **[HERO]**

10. **H10** [PLAY_CARD] [`whisper-blue-2b`, `whisper-blue-3b`] **WHEN** an enemy hero is in range 3 and the worse option for them is at least 8 (a discard they cannot afford, or minions whose margin is 15 or more, or a defeat on Greater Good) **THEN** play it. Score the enemy as choosing the cheaper option, and score "no effect" as 0 so he does not play it when that is their best choice. **WEIGHT** the cheaper option's value. **SOURCE** [CARD; FAQ; BGG 3589436; STRATEGY §5] **[GENERAL]**

11. **H11** [CHOOSE_OPTION] [as the enemy facing Lesser Evil / Greater Good] **WHEN** both bullets are offered **THEN** pick the one with the lower cost, including a bullet that does nothing. Greater Good's defeat costs a life; three minions cost 45 of margin if they were the push, else 15 each. A discard of his last card costs the upcoming kill. **SOURCE** [FAQ; rulings 9] **[GENERAL]**

12. **H12** [PLAY_CARD] [`whisper-blue-1`, `whisper-blue-2a`, `whisper-blue-3a`] **WHEN** an enemy minion on a spawn can be moved adjacent to him or an ally **THEN** move it, and leave the spawn empty for his Red if that Red is still in hand. A hero on a spawn is a target only when the move takes them off an ally or onto his Red. **SOURCE** [BGG 3589436] **[HERO]**

13. **H13** [PLAY_CARD] [any] **WHEN** it is turns 1–2, nobody is stripped, and a minion is available **THEN** take the minion (Red, or Gold's melee bullet) rather than a speculative snipe that an ally cannot convert. **WEIGHT** 15 over a +2 poke. **SOURCE** [BGG 3599730; Ruwakh BGG 3589436] **[GENERAL]**

14. **H14** [UPGRADE_CARD] **WHEN** their Silvers are low Defense **THEN** Attack items before a third Defense item, so Gold's 2 becomes 4. **WHEN** he is dying to skirmishers **THEN** the Defense item on Sanguine Path or Creeping Shadow before the next Attack item. **SOURCE** [BGG 3599730] **[GENERAL]**

15. **H15** [KEEP_UPGRADE] **WHEN** the job is finishing wounded heroes from 2–3 away **THEN** Sanguine Path / Blood Pilgrimage. **WHEN** the job is standing in the wave and taking two bodies **THEN** Blood Fury / Frenzy. **WHEN** allies already discard **THEN** Greater Good over Blooming Nightmare. **SOURCE** [BGG 3589436; BGG 3599730] **[GENERAL]**

16. **H16** [CHOOSE_OPTION] [`whisper-purple-4`] **WHEN** both basic bullets can start and the second is worth at least 4 **THEN** take both. Gold: snipe plus the adjacent minion when both exist. Silver: immunity and retrieve when both the wounded enemy and the discarded card exist. **SOURCE** [rulings 4] **[GENERAL]**

17. **H17** threatAdjust [`whisper`] **WHEN** his Gold is unplayed **THEN** every enemy hero in range 3 with an empty discard is under an Attack 2, init 12, and if they block with a Movement card they will be shoved the full printed distance. Add the danger of that shove's landing to his allies' plans. **WHEN** any enemy in radius 3 has a discard and Silver is unplayed **THEN** attacks on him this turn can be blanked at init 13. **SOURCE** [CARD; BGG 3589436] **[HERO]**

18. **H18** [DEFEND] [against his Gold snipe] **WHEN** a card in hand has no Movement icon, or no straight line of that exact length exists **THEN** prefer that card if it also beats Attack 2, so he does not place the defender. **SOURCE** [FAQ; BGG 3589436] **[GENERAL]**

19. **H19** [PLAY_CARD] [any] **WHEN** he is more than one Green-range from the battle zone and a push is about to move the zone farther **THEN** Shadow Step / Walk toward it this turn, over a speculative hero poke. **SOURCE** [ADVICE] **[HERO]**

20. **H20** [RESPAWN] **WHEN** he is dead **THEN** respawn and hold Green if a spawn in the new zone will be empty by his turn; otherwise the card with Movement. **SOURCE** [ADVICE; REASONED] **[HERO]**

## 7. Sources

- Catalog `heroes/whisper.json`, handicap `whisper-handicap`, FAQ (the forced run; Lesser Evil may do nothing).
- `heroes/WhisperHero.kt`, `core/game/rulings/whisper.md`. Designer confirmation of the "even if it would have no effect" reminder is BGG 2888636, cited from RULINGS 38 (1st-printing thread, the reminder is still on the 2nd-printing card).
- [BGG 3589436](https://boardgamegeek.com/thread/3589436) Whisper spotlight (2nd printing), Cryomancy, 2025-10-09. Ruwakh, 2025-10-18, community: the guide's "farm early" won him a double push.
- [BGG 3599730](https://boardgamegeek.com/thread/3599730) Build & Go, Cryomancy, 2025-10-27.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Initiator / Finisher.
- [BGG 3513251](https://boardgamegeek.com/thread/3513251) dusteppe names him with Ursafar and Tigerclaw as a most-picked "toxic" hero; the card plan above is from the spotlight, not from that complaint.
- 1st-printing spotlight [BGG 2888636](https://boardgamegeek.com/thread/2888636) (Saroule14, 2022) was not used for card text.
- No Whisper strategy thread after 2026-10-24. The spotlight itself is 2025-10-09, before the previous research pass; the Build & Go (2025-10-27) is the one that pass had not folded into a playbook.

## Implementation notes

`WhisperHeuristics.kt` (ADR-085). The general book scores an attack card from its printed reach (`HContext.strikeFrom`). Where this hero's text narrows the targets, the playbook re-scores the card's PLAY_CARD outlook on the units the engine would really offer (`BatchDSupport.strikeOn` + `strikeFix`): the card keeps its movement value and loses the hit it cannot make. That replaces the draft's flat −28 "fizzle" penalty, which also threw away the card's secondary Movement.

- **Implemented:** H1–H3 (Swift Justice: a hero in range with an empty discard, or an adjacent unit; the handicap's melee bullet heroes only), H4 (Death Seeker only with a hurt enemy in radius; immunity scaled by exposure, or a retrieved card), H5 (the Reds step one space per empty minion spawn in radius in the battle zone, up to the cap), H6 (Blood Fury repeats once the hit hero has discarded), H8 (Shadow Step needs an empty minion spawn in range; worth it beside a target with an attack in hand), H10 (Lesser/Greater Evil: the enemy takes the cheaper bullet, and a bullet with no effect costs them nothing), H15 (KEEP: Blood Fury, Lesser Evil, Shadow Walk), H17 (snipe threat on a hero with an empty discard). `evalTerms`: +1 per hurt enemy within 3 (two at most).
- **Engine vs research:** the Red's step count is read before moving and counts empty minion spawn points in radius inside the battle zone; Blood Fury's condition is checked after the first hit, so hitting a hero who defends with a discard switches the repeat on. Lesser Evil fizzles with no enemy hero in range. No disagreement.
- **Skipped:** H7/H16 (CONFIRM and CHOOSE_OPTION go through lookahead), H9 (Cruel Twist's swap needs a forecast of where the enemy goes), H11/H18 (advice for the other side), H12 (Seeds of Fear's move is a space question for the general minion score), H13 (general farming), H14 (upgrade colour order), H19/H20 (lane timing and respawn are the general book's).
- **Ablation (strength 8):** 49–47 of 96 = 51.0% (Wilson 41.2–60.8%), with the playbook against without it, 2026-11-21 (`AI_H_ABLATION=whisper AI_H_STRENGTH=8 AI_H_GAMES=96`: 48 line-ups, both sides).
