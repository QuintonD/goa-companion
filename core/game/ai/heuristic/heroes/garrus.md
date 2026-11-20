# Garrus (`garrus`) — heuristic playbook

2nd printing. Catalog: `app/src/main/assets/catalog/heroes/garrus.json`. Engine: `GarrusHero.kt`.
Rulings: `core/game/rulings/garrus.md`. Complexity 2. Stats: Attack 8/8, Defense 7/7, Initiative 3/3, Movement 5/6.
Catalog roles: disabler, durable, tactician. No tokens. One discard in his own pile is a resource, not a wound.

Weights are coin-equivalents added on top of the general playbook (one minion of lane margin at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5, a hero kill ≈ that hero's defeat coins + 1 Life counter). `CTX general` uses only board, hands, revealed cards, initiatives, coins, zones, distances, and his public discard size. `CTX hero` needs a fact only this playbook tracks.

1st-printing Garrus is a different deck (a gold that discards as part of the attack, a blue that shoves heroes out of a radius, an anti-range blue). Those numbers are not used. The 2022 designer notes on Howl versus movement 3+ and on Pilum versus a hero who wants to silver are kept, because both tools still exist. Card text wins where a 2022 sentence is wider than the 2nd-printing card.

## 1. Identity

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| role | Fighter | He holds the middle and hits. Not a pure tank: the hunt red and the pilum are how games end. [BGG 3779890] Zoner/Disruptor; the same thread's Garrus players call the hunt a finisher that is telegraphed, so the zone is the reliable job. |
| idealDistance | 1 | Gold, both reds, and the pilum's follow-up want him adjacent. With a discard and primary red kept, he threatens 2, then 3, then 4 (the step plus adjacent). Raise the treated reach, not this number. |
| aggression | 0.62 | He stands where they must come to him, and he steps in on turn 4. He does not trade his blue away to farm a safe minion. |
| laneWeight | 0.42 | Pressure that stops their take is the lane plan. A minion is what he hits when the hero will not stand next to Attack 7. |
| keepDefender | true | `garrus-blue-1` is Defense 6 with or without a condition. Alt red is Defense 7/8. Gold and silver are Defense 3. |
| upgradeOrder | see §3 | Alt red first when he is the front line. Primary red when an ally makes the first discard and the enemy is fragile. |

He wants the center of the Battle Zone, on the side the wave is about to leave, so a Howl covers the path they have to walk. One card in his discard turns on the gold's bonus and the primary red's step [Gamefound, Wolff Designa]. A second card in the discard still adds Attack, but it means he has spent the block. Enemies should not poke him once.

## 2. Card-by-card

Initiative band: Silver 13, Gold 11, Blue 9 then 10, Red 7 then 8, Green 5 then 4. Howl acts before almost everything. Green acts after almost everything, which is why the retrieve sees the hits. Items add +1 to every card with the icon. His hunt step and his pilum step are card text, not movement actions: his own Howl does not cap them, and neither does an enemy Howl.

"Discard size" is his discard pile at the moment the attack is made. The +1 on gold is card text; Attack items add to the printed 4, they do not multiply the +1 (`rulings/garrus.md` 1).

### `garrus-gold` Angry Strike — Attack 4, Defense 3, Movement 1, Initiative 11, +1 Attack per card in his discard

Adjacent. The bonus is counted when the attack is made, so a faster hit that he survives (he discards a different card) is already in the pile. Engine: the attack, no extra choice. Handicap `garrus-handicap` Angry Swipe, 5-player: Attack 3, Initiative 10, heroes only. Same bonus. Do not plan a minion take on it.

For: the second hit. [ADVICE] "Attacking second with your gold card can be an easy way to deal extra damage." [BGG 3526542] zero discards is a plain Attack 4; silver alone makes it 5; one wound on top makes it 6, before Attack items. [BGG 3779890, Exozik] turn-3 alt red and turn-4 gold is a real double kill. Movement 1 is not travel. Defense 3 will not save him.

When: he already has ≥ 1 discard and is adjacent to a hero the total meets, or to the minion that decides the lane. Or he has 0 and a faster revealed attack will hit him, he can survive it, and the +1 is what makes his swing connect. Initiative 11 loses to a 12 and to Howls, which is the usual way he swings second. An Initiative item can make him first and waste the bonus; do not take one just to win the gold race.

When not: turn 1 at 0 discards, into a hero who still blocks Attack 4, when a minion is there. As the only defender.

### `garrus-silver` Chilling Howl — Defense 3, Initiative 13, Radius 3, Skill

He may discard one resolved card (not this one; it is still resolving). This round, enemy heroes in radius cannot fast travel, and cannot move more than 2 with a movement action. Checked at the start of that action: a hero who starts outside and walks in is not capped; a hero who starts inside does not get the rest of a 4 back by leaving [FAQ] [Discord, Arty, 2025-02-28]. Card-text moves are not movement actions (Brogan's charge, Tigerclaw's Pick Pocket and Master Thief) [Discord, Arty, 2026-09-30] [rulings/garrus.md 2]. A hero immune to his skills ignores it, including a hero who becomes immune later (Misa's gold, nichik, [BGG 2886517]). No movement value.

Engine: optional `CHOOSE_CARD` among resolved cards, then the active effect. With `garrus-purple-4`, that discard asks `CONFIRM` to replay the discarded card's primary.

For: the cage and the first discard. [ADVICE] high movement values. [Gamefound] the cap and the fast-travel ban are why he is the answer to mobile heroes. [BGG 3526542] it also turns the gold into a 5 and unlocks the primary red's step without waiting to be hit. [BGG 3548024, Cryomancy's reply] the best Howl is the one between the enemy and the next wave, or the one that cuts a fleeing hero's blue from 4 to 2 so an ally stays on them. [BGG 2886354, nichik, designer, 2022] Tigerclaw's movement-action cards are 3+, so the cap matters; the 2026 ruling is what keeps his card-text dashes out of that sentence. dusteppe [BGG 3526542]: it is a wasted turn if he uses it only to discard and nobody in radius needed the cap. It is good when he is already next to them, because they cannot sprint out of the zone.

When: turn 1 or 2, at least one enemy in radius 3, and either they still have a movement action of 3+ or a fast travel, or he has 0 discards and gold or primary red is still in hand. The "this round" text is why turn 4 is late: only the rest of that turn remains.

When not: an empty radius, unless Battle Fury will replay a resolved attack that has a target right now. Defense 3, no boots: do not hold it as the last card hoping to "go first" on turn 4.

### `garrus-red-1` Trace — Attack 3, Defense 5, Movement 4, Initiative 7

Choose one. Bullet 1: if he has one or more cards in the discard, he may move 1, then he must target a hero adjacent to him. Bullet 2: a unit adjacent, no step. Without a discard there is no step; bullet 1 exists only when a hero is already adjacent. After a step, the hero target is mandatory: a step that ends next to nobody stops the action (`rulings/garrus.md` 3). Engine: `CHOOSE_OPTION` when both bullets can start, then the move (`CHOOSE_SPACE`, optional on this tier), then `CHOOSE_UNIT`.

For: the short hunt. Attack 3 is not a finisher before items. [BGG 3526542] the step is for a hero who is already wounded, usually on turn 4, usually after an ally's discard or his own silver. Bullet 2 is the minion.

When not: a step onto a hex that is not adjacent to the hero he means. Spending it as a walk when bullet 2's minion wins the lane and no wounded hero is one step away.

### `garrus-red-2a` Chase — Attack 4, Defense 5, Movement 4, Initiative 8, item Defense

Bullet 1 moves up to 2 (0 is legal) if he has a discard, then a hero adjacent. [BGG 3526542] this is the hunter card: low defense heroes and turn-4 kills. Without Attack items it does not crack a Defense 6. Keeping it tucks `garrus-red-2b`, whose item is Initiative — one Initiative item, not two. [BGG 3526542] two Initiative items at tier II leave the hunt with too little Attack and too little Defense.

### `garrus-red-3a` Hunt Down — Attack 4, Defense 6, Movement 4, Initiative 8, item Range

Bullet 1 moves up to 3. Defense 6, so he can block with it. The Range item widens the pilum (2) and both blues (2 or 3). Threat reach with a discard is 4 (step 3, then adjacent). Keeping it tucks `garrus-red-3b` (Radius), which widens Howl from 3 to 4 and Hold Ground from 4 to 5.

### `garrus-red-2b` Blunt Force — Attack 7, Defense 7, Movement 4, Initiative 8, item Initiative

Adjacent attack. After it connects, he may move 1 to a space adjacent to an enemy hero; if he does, he pushes that hero 3, ignoring obstacles. The move must end adjacent to an enemy hero or he cannot move [FAQ]. The hero he pushes can be a different one from the one he hit [FAQ] [Discord, Arty, 2025-08-28]. The 1st-printing "nearest enemy" restriction is not on this card. An immune hero is not a legal push target (p.10; Arty, 2025-12-18, "you cannot"). The push passes obstacles and stops on the farthest empty space within 3 (`rulings/garrus.md` 4). A full line of occupied hexes can leave the push at 0 [rulebook p.18]; he was still allowed to move. Engine: attack, then optional move of at least 1, then `CHOOSE_UNIT` if several heroes are adjacent.

For: the one-shot and the zone. [BGG 3526542] Attack 7 and Defense 7 are why he stands next to other brawlers. Most heroes will not stand there once they know he might play it; the threat is the play, the hit is the punish. Hit a minion that will not walk off, then step to the hero and throw them away from his ally or off the minion line. [BGG 3779890, Exozik] turn 3 this card, turn 4 gold. dusteppe [BGG 3526542]: at initiative 8 it rarely connects on a hero who played well, unless an Initiative item makes it less obvious; Cryomancy's reply: heroes who respect the gold and play a slow escape eat the red instead. Both are true. Do not require the hit in order to count the zone.

When not: a push that delivers them onto the next wave or into a charge line onto an ally. A move that ends next to only an immune hero. Defense 7 is a block he can spend; do not spend it on a minion the blue could have shoved, if a hero kill is not live and he has no other Defense 6.

Keeping it tucks Chase (Defense item). He does not get an Initiative item from this branch unless some other tuck gives him one.

### `garrus-red-3b` Send Flying — Attack 7, Defense 8, Movement 4, Initiative 8, item Radius

Same, but he may move up to 2, and the move must be at least 1 if he pushes ("if you do"). Staying does not push (`rulings/garrus.md` 4). Attack is still 7. The extra step is how he reaches a second hero after hitting a minion. Defense 8 is his largest block.

### `garrus-blue-1` Menace — Defense 6, Movement 3, Range 2, Initiative 9, Skill

Move an enemy unit in range 1 space to a space farther from him. Farther is shortest-path distance, obstacles included (`rulings/garrus.md` 7). Engine: `CHOOSE_UNIT`, then `CHOOSE_SPACE`. No legal unit: the primary is not offered.

For: one step that breaks a line. [BGG 3526542] initiative 9–10 moves them before their red: an ally's action becomes safe, or they land in an ally's reach. Use it on himself only when he must hold the hex; otherwise he can just walk. Shoving a minion is niche: one space off a spawn, or off a defense modifier, and only when no hero shove is worth more.

When not: as a panic button when a movement action would leave the radius of the thing he is trying to zone. Defense 6 is the block. Holding it is correct more often than playing it.

### `garrus-blue-2a` Threaten — same, may repeat once, Initiative 10, item Defense

Each repeat picks again, the same unit included. Two steps on one hero, or one step on two units. Keeping it tucks `garrus-blue-2b` (Attack item).

### `garrus-blue-3a` Terrify — same, may repeat up to two times, Defense 7, Initiative 10, item Defense

Three steps total, splittable. Defense 7. Keeping it tucks `garrus-blue-3b` (Attack item).

### `garrus-blue-2b` Form Up! — Defense 6, Movement 3, Range 3, Initiative 10, Skill, item Attack

Move a friendly unit in range (not himself) 1 space closer. May repeat once. Same geometry, closer instead of farther.

For: a minion pulled in so his defense modifier goes up and theirs goes down, or an ally pulled onto the hex their restricted attack needs [dusteppe, BGG 3526542]. After a push, a hero stuck on the wrong side can be dragged one step toward the new Battle Zone. [BGG 3526542] this is the harder blue: if no empty hex is closer, the unit is not a target. Obstacles do not get a special exception; they just remove hexes.

When not: pulling a friendly minion out of the Battle Zone, or off a hex that was holding a chokepoint. Keeping it tucks Threaten (Defense item), which is why the centurion build keeps it even though the shove-away card is easier to aim.

### `garrus-blue-3b` Testudo! — same, repeat up to two times, Defense 7, Range 3, item Attack

Three pulls. Defense 7.

### `garrus-green-1` Hold Ground — Defense 3, Movement 2, Initiative 5, Radius 3, Skill

If at least two enemy heroes are in radius, he may retrieve a discarded card. Fewer than two: the primary is not offered (`rulings/garrus.md` 5). Presence, not a target. Engine: `mayRetrieve`, which is a `CHOOSE_CARD` he can decline.

For: the dial on his discard, and the heal. Initiative 5 is after golds and most reds, so he plays it, they hit him, then he retrieves. [BGG 3526542] he may also decline and keep the discard, because the gold and the hunt want it. [ADVICE] enemies have to care about him once that discard exists; the retrieve is how he stops caring if the second hit is coming.

When not: one enemy in radius. Defense 3. Do not retrieve his only discard on the turn primary red or a 0-discard gold still needs it, unless the card coming back is the block that saves him now.

### `garrus-green-2a` Make a Stand — same, Radius 4, Initiative 4, item Initiative

Radius 4 is the one that covers a real fight. Keeping it tucks `garrus-green-2b` (Attack item). The hunter build wants that Attack item and does not want this card's Initiative item in addition to the one from tucking Blunt Force [BGG 3548024].

### `garrus-green-3a` Battle Ready — same, retrieve up to two, Radius 4, Initiative 4, item Initiative

Two retrieves. This is the "negate two hits" card [BGG 3526542]. Keeping it tucks `garrus-green-3b` (Movement item), the boot. Tucking this card (keeping the pilum) is the Initiative item the centurion uses to tie faster brawlers.

### `garrus-green-2b` Light Pilum — Defense 4, Movement 2, Range 2, Initiative 4, Skill, item Attack

An enemy hero in range discards a card, if able. Then he may move 1. No hero in range: the action is not offered, so he does not get the step (`rulings/garrus.md` 6; the other rules engine allows a skip, this one does not). A hero with an empty hand may be chosen; Light then does nothing to them. Engine: `CHOOSE_UNIT`, `discardIfAble`, optional move of 1.

For: a discard that is not an attack, plus a step. [BGG 3526542] turn 1: wound them and step in, turn 2 silver discards this resolved pilum (the discard counter turns on, Howl goes up), red and gold and blue are still in hand. In a melee it is the fake: he injures them and keeps the Attack 7. [BGG 2886354, nichik] throw it when they want to silver, to attack, or to take the coins. Initiative 4 means they have usually already acted, which is the point: the silver they just played is the one he punishes, not the one he races.

When not: an empty range. Into a hero with 0 cards (Light cannot kill them). Defense 4.

### `garrus-green-3b` Heavy Pilum — Defense 4, Movement 2, Range 2, Initiative 4, Skill, item Movement

Same, but "discards a card, or is defeated," and he may move up to 2, including 0. Empty hand: they die. That is the turn-4 kill on someone who already acted [dusteppe, BGG 3526542, the intent; the printed initiative is still 4, so it is a finisher on a hero who has already resolved, not a race against a red]. The step of 2 is card text.

### `garrus-purple-4` Battle Fury — Ultimate, no stats

Each time a resolved card of his is discarded, he may perform that card's primary. A discard from hand (a block, a "discard a card") does not count. A defense card has no primary. He must be on the board, and the primary must be legal (`rulings/garrus.md` 8). Engine: `CONFIRM`, then that card's decisions, inside the action that discarded it.

Silver is how he triggers it himself. The card he discards is already in the pile, so a replayed gold counts it for +1. [BGG 3526542] this is the "same card twice" line: play gold or red or pilum, later discard that resolved card with silver, take the primary again. Do not confirm a replay that has no target.

## 3. Upgrade path

[BGG 3548024] Centurion is the build he calls easier, and the Garrus player in that thread found it the more reliable of the two. It is the bot's start. Bloodhound is the switch when an ally can land the first discard and the enemy's defense is low. slyestfox [BGG 3779890] wants the hunt to be the finisher; Cryomancy's reply is that it is too telegraphed to be the default.

"Keep X" puts X in hand. The item is the other branch's catalog `item`.

Default (Centurion):

1. Tier II, order by the round he just played: `garrus-red-2b` if a hero is standing on him (Attack 7, Defense 7; tuck `garrus-red-2a` is Defense). `garrus-green-2a` for the Attack item gold needs (tuck `garrus-green-2b`). `garrus-blue-2b` so the tuck `garrus-blue-2a` is a second Defense and he can spend the red (Cryomancy's preference). Keep `garrus-blue-2a` instead if they are stacking Defense and he wants the tuck's Attack item more than the second Defense.
2. Tier III first: `garrus-green-3b`. Discard-or-die at range 2, and the tuck `garrus-green-3a` is Initiative, which is how Blunt Force ties a faster brawler. One Initiative item, not a stack.
3. Tier III second: `garrus-blue-3a` if he still wants another Attack item (tuck `garrus-blue-3b`). `garrus-blue-3b` if the Defense item from tucking Terrify is the hole.
4. Tier III third: `garrus-red-3b`. Attack stays 7, the step becomes 2, Defense 8. Tuck `garrus-red-3a` is Range, so Heavy Pilum and the blues reach one farther. He does not upgrade this for damage.

Bloodhound, when an ally is the one who discards them:

1. `garrus-red-2a` (step 2; tuck Blunt Force is the one Initiative item). Do not take a second Initiative at tier II.
2. `garrus-blue-2a` (tuck Form Up is Attack), so Chase is a threat before turn 4.
3. `garrus-green-2a` (tuck Light Pilum is the second Attack). Not the Initiative item.
4. Tier III: `garrus-green-3b` (another Initiative, from tucking Battle Ready; now he may have two, and only here), then `garrus-red-3a` (step 3; tuck Send Flying is Radius, Howl becomes 4), then `garrus-blue-3a` (tuck Testudo is Attack).

The stall variant of the hunt keeps `garrus-blue-3b` first (Defense item), then Hunt Down, then Battle Ready for the Movement item.

Items to tuck:

- One Defense item before he swings alt red for a minion. Blue is Defense 6; he can tuck a second Defense, not the only one.
- One Attack item before he expects gold (printed 4) or Chase (printed 4) to kill a hero who still holds a card.
- The first discard is not an item. Do not retrieve it just because green allows it.
- Radius (tuck Send Flying) only on the hunt path, where Howl is the cage. Range (tuck Hunt Down) only on the centurion path, where the pilum is the early wound.
- Initiative once, so alt red is not a free "he is on 8, I will play slow" tell [dusteppe]. Not twice before he has an Attack item [BGG 3526542].

Branch summary:

| Colour | Default | Why | Switch when |
|---|---|---|---|
| Red | b `garrus-red-2b`/`3b` | Attack 7, Defense 7/8, a 3-push. The zone works even when they refuse the hit. | An ally applies the first discard and they are fragile: `2a`/`3a`. |
| Blue | b `garrus-blue-2b`/`3b` | The tuck is Defense, so the red can be spent. The pull is the second job. | They stand at range 2 and need to be stepped off a line: `2a`/`3a`. |
| Green | a `garrus-green-2a`, then b `garrus-green-3b` | Tier II banks Attack and the retrieve. Tier III banks Initiative and the empty-hand kill. | He is already wounded every round and needs two retrieves: stay on `3a`. |

## 4. Synergies and counters

Teammates: anyone who can put the first card into an enemy's discard (he then hunts) or who chases on a green after his Howl has capped the escape at 2 [BGG 3548024]. A healer who is not him lets him keep a discard and still have a hand [BGG 3526542]; his own green is the worse version of that, because it costs the action. Silverarrow is the ally Panetierre names into Tigerclaw [BGG 2886354]: roots plus Howl, and a fast gold for the turn-4 kill. He is a sound first pick [Gamefound]: Defense 7, a cage, a push, no single hole.

Enemies he fears: a second hit on the same turn, after the first has already turned his gold on. Displacement that moves him off the center [BGG 3526542]. Heals that put the hunted card back before turn 4. Card-text movement (charges, blinks, pilfer steps), which Howl does not cap. A team that never hits him and never walks into radius 3: the discard engine and the cage both idle.

Playing against him:

- Do not poke him. Zero discards means Attack 4 and no hunt step. One discard means the gold grew and the step turned on. Two discards in one turn is the kill [Gamefound] [BGG 3526542].
- His predictable hunt, if he is on primary red: a setup card, silver that discards it, a reposition, red on turn 4. Break the step with a body, a token, or by keeping the wounded hero outside move 1/2/3 [BGG 3526542].
- Howl is usually turn 2 and lasts the round. Plan a card-text move, or start the movement action outside the radius. Do not fast travel through it.
- Alt red does not have to hit to matter. Do not end a turn adjacent to him while it is in hand. If he hits a minion, he can still step to you and push.
- His blue and his alt red are Defense 6–8. Gold and silver are 3. The turn those two are his only cards is the turn to hit him.
- Battle Fury does not replay a card he discarded from hand to block. It replays a resolved card. Killing him before silver is how that second primary never happens.

## 5. Common mistakes

- Howl on an empty radius, only to put a card in the discard. The turn was the cage [dusteppe, BGG 3526542].
- Howl on turn 4. "This round" has almost no round left, and Defense 3 is what he is holding.
- Primary red's step with an empty discard. The bullet does not move him.
- A hunt step that ends next to a minion and no hero. The hero target is mandatory and the action stops.
- Treating Howl as a cap on charges, blinks, and Pick Pocket. Those are not movement actions [Discord, Arty, 2026-09-30].
- Expecting a hero who walks into the radius to be slowed. The check is at the start.
- Retrieving his only discard, then playing Chase as if the step still exists.
- Two Initiative items at tier II on the hunter, and wondering why Attack 4 does not scare anyone [BGG 3526542].
- Confirming Battle Fury on a hand-discard, or on a resolved card that no longer has a target.
- Using the 1st-printing gold (the attack itself discards) or the 1st-printing anti-range blue. Those sentences are not on these cards.
- Standing where two enemies can both hit him, because the first hit "makes him stronger." The second hit is how he dies.

## 6. HEURISTIC RULES

1. `PLAY_CARD` [`garrus-silver`] WHEN it is turn 1 or 2, an enemy hero is in radius 3 (4 with a Radius item), and that hero still has a movement action of 3+ or a legal fast travel THEN prefer Howl. WEIGHT +12 if a minion they want is inside their full movement and outside a 2-step, +8 if the only loss is a fast travel out of the zone, +6 if he also has 0 discards and gold or primary red is still in hand. CTX general. SOURCE [ADVICE] [BGG 3526542] [BGG 3548024] [Gamefound].

2. `PLAY_CARD` [`garrus-silver`] WHEN no enemy is in radius, and Battle Fury has nothing to replay, THEN avoid it. WEIGHT −8. WHEN it is turn 4 and the cage would not change a move still unresolved this turn THEN avoid it. WEIGHT −6. A discard he could have bought with the card is not worth the turn by itself [dusteppe]. CTX general. SOURCE [BGG 3526542] [REASONED].

3. `CHOOSE_CARD` [`garrus-silver`] WHEN he may discard a resolved card THEN discard it if `garrus-purple-4` is in and that card's primary still has a target (rule 12), or if his discard size is 0 and gold or primary red is unplayed. WEIGHT +10 for a Fury replay that takes a minion or a hero card, +4 for the first discard that turns the step on. Decline when discard size is already ≥ 1, Fury would whiff, and the resolved card is not adding a gold bonus he still needs. CTX hero (ultimate flag) plus general discard size. SOURCE [BGG 3526542] [Gamefound] rulings/garrus.md 8.

4. `PLAY_CARD` [`garrus-gold`, `garrus-handicap`] WHEN he is adjacent and (discard size ≥ 1, or a faster revealed attack will hit him and he survives it) THEN prefer gold as the second swing. WEIGHT +4 per card already in the discard if that bonus is what meets their defense, the kill if they have 0 cards, +6 for a lane minion when no hero is in reach. WHEN discard size is 0 and nobody faster will hit him first THEN avoid gold on a hero who blocks Attack 4. WEIGHT −6. Do not take an Initiative item whose only effect is to make this gold act before their gold. CTX general. SOURCE [ADVICE] [BGG 3526542].

5. `CHOOSE_OPTION` [`garrus-red-1`, `garrus-red-2a`, `garrus-red-3a`] WHEN discard size ≥ 1 and a hero is adjacent to a hex he can reach in 1 / up to 2 / up to 3 THEN prefer bullet 1, and end the step adjacent to that hero. WEIGHT the kill if they have 0 cards, +12 if they have 1, +4 if they have 2+ and Attack 3/4/4 (plus items) does not meet them. A step of 0 is correct when he is already adjacent to the right hero. NEVER end the step on a hex with no enemy hero adjacent. WHEN discard size is 0 THEN bullet 2 only, if a unit is adjacent. CTX general. SOURCE [BGG 3526542] rulings/garrus.md 3.

6. `PLAY_CARD` [`garrus-red-2b`, `garrus-red-3b`] WHEN he is adjacent to a unit and Attack 7 (plus items and modifiers) meets a hero, or the unit is a minion and an enemy hero is 1 step away (`3b`: 2 steps) on a hex he can push them from THEN prefer this red. WEIGHT the kill, or +10 for the minion-plus-push. WHEN no hero will be adjacent after the hit and the push would have nowhere to go THEN it is still the zone: do not walk into two heroes just to "use" it. WEIGHT −6 for that walk. CTX general. SOURCE [BGG 3526542] [BGG 3548024].

7. `CHOOSE_SPACE` + `CHOOSE_UNIT` [`garrus-red-2b`, `garrus-red-3b`] WHEN the attack connected THEN move only if the end hex is adjacent to a hero he can target, and the 3-push (ignoring obstacles, farthest empty space within 3) puts that hero off an ally, off a minion, or out of their own attack's reach. WEIGHT +12. The pushed hero may be a different one from the target [FAQ]. Decline the move if every legal push helps them, or the only adjacent hero is immune. WEIGHT −8. `garrus-red-3b` must move at least 1 in order to push; `garrus-red-2b` moves exactly 1 or not at all. CTX general. SOURCE [BGG 3526542] [Discord Arty 2025-08-28] [BGG 3778446].

8. `CHOOSE_UNIT` / `CHOOSE_SPACE` [`garrus-blue-1`, `garrus-blue-2a`, `garrus-blue-3a`] WHEN an enemy in range 2 can be stepped farther THEN prefer the step that takes them off a straight line into an ally, out of their unresolved attack's reach, or into an ally's reach. WEIGHT +10 / +8 / +8. Repeat (`2a` once, `3a` twice) only when the next step still does one of those. A minion shove is +6 only when it strips a defense modifier or clears a spawn an ally needs. Do not play the card as the action when a walk of his own leaves the hex just as safe and keeps Defense 6 in hand. CTX general. SOURCE [BGG 3526542].

9. `CHOOSE_UNIT` [`garrus-blue-2b`, `garrus-blue-3b`] WHEN a friendly minion or hero in range 3 has an empty hex closer to him THEN pull a minion that will be adjacent to him before an unresolved attack, or pull an ally onto a hex their attack needs. WEIGHT +6 for the modifier, +8 for the ally's attack. Do not pull a unit out of the Battle Zone. Repeat only when the second pull still does one of those. CTX general. SOURCE [BGG 3526542] dusteppe in that thread.

10. `PLAY_CARD` [`garrus-green-2b`, `garrus-green-3b`] WHEN an enemy hero is in range 2 THEN prefer the pilum if they have 1 card (the discard empties them) or, on `garrus-green-3b` only, 0 cards (the kill). WEIGHT +12 or the kill. A hero with 0 cards is a wasted Light Pilum (WEIGHT −4). Then step toward the hero primary red will want, or out of a hex where two enemies are adjacent. No hero in range: do not play it; the step is not offered. CTX general. SOURCE [BGG 3526542] [BGG 2886354] rulings/garrus.md 6.

11. `PLAY_CARD` [`garrus-green-1`, `garrus-green-2a`, `garrus-green-3a`] WHEN two or more enemy heroes are in radius (3, then 4) AND a faster attack is about to hit him or already has THEN play it and retrieve after the hit. WEIGHT +8 if the card he gets back is a Defense 6+, +3 otherwise. Decline the retrieve when it would drop his discard size to 0 and gold or primary red is still unplayed, unless the card in the pile is the only block that saves him from a second hit this turn. WEIGHT −8 for emptying the pile in that case. One enemy in radius: do not play it. CTX general. SOURCE [BGG 3526542] [ADVICE].

12. `CONFIRM` [`garrus-purple-4`] WHEN the card just discarded from a resolved slot has a primary with a legal target THEN confirm. WEIGHT +15 if that primary takes a minion or pushes a hero, +12 if it is a second pilum on a hero with 1 card, the kill if it defeats them, +4 if it is only a shove. Decline a defense card (no primary) and decline a primary that would now fizzle. A discard from hand does not ask. CTX general. SOURCE [BGG 3526542] rulings/garrus.md 8.

13. `DEFEND` [`garrus-blue-1`, `garrus-blue-2a`, `garrus-blue-2b`, `garrus-blue-3a`, `garrus-blue-3b`, `garrus-red-2b`, `garrus-red-3b`] WHEN the printed Defense meets the hit THEN prefer these over gold or silver (Defense 3). WEIGHT +6. Spend alt red's Defense 7/8 before blue only when he does not still need the push this round. CTX general. SOURCE card text [REASONED].

14. `CHOOSE_SPACE` any movement of his WHEN several hexes are legal THEN prefer a Battle Zone hex from which his current Howl radius covers an enemy, or the hex an enemy must cross to reach the next wave. WEIGHT +6. This is the king-of-the-hill stand, not a chase across an empty zone. CTX general. SOURCE [BGG 3526542] [BGG 3548024].

15. `UPGRADE_CARD` / `KEEP_UPGRADE` any WHEN red is still tier I and an enemy hero ended the round adjacent to him THEN upgrade to `garrus-red-2b` and tuck the Defense item. WHEN the enemy team is fragile and an ally has a discard effect THEN `garrus-red-2a` instead, and do not tuck a second Initiative item at tier II. WEIGHT +5 for the first Attack item while gold is still printed 4, +4 for the first Defense item while he intends to spend the red. CTX general. SOURCE [BGG 3548024] [BGG 3526542].

16. `KEEP_UPGRADE` [`garrus-red-2a`, `garrus-red-3a`] WHEN kept THEN his threat reach toward heroes, while discard size ≥ 1, is 1 + the step (2, then 3), not 1. Score a hero inside that reach as if adjacent for the purpose of rule 5. idealDistance stays 1. CTX hero (branch flag, discard size). SOURCE [BGG 3526542] [REASONED].

17. `PLAY_CARD` [`garrus-red-2b`, `garrus-red-3b`] WHEN he is not adjacent to a hero but is adjacent to a minion, and a hero stands on a hex he can reach in the after-step (1, or up to 2) THEN hit the minion and take rule 7. WEIGHT +10. This is the "they will not stand next to me, so I hit what will not move" line. CTX general. SOURCE [BGG 3526542] Cryomancy's reply in that thread.

18. `PLAY_CARD` any WHEN two enemy heroes can both still attack him this turn and his only remaining Defense ≥ 6 is one card THEN do not spend that card as the action. WEIGHT −10. The first hit turns his gold on; the second is the kill [Gamefound]. CTX general. SOURCE [Gamefound] [BGG 3526542].

### evalTerms

Garrus has no tokens. Score the discard and the cage:

- Howl in force: +4 per enemy hero currently in radius who still has an unresolved movement action of 3+ or a legal fast travel. 0 per hero whose only remaining move is card text. 0 if the effect is not up.
- Discard size while gold or primary red is still in hand: +3 for the first card, +1 for each further card. The first unlocks the step and the bonus; the rest are only +1 Attack, and the missing hand card is already in the general hand term.
- Do not add a bonus for "he might Howl." That is a play, not a piece.

### threatAdjust

When Garrus is the enemy:

- While Howl is in force, a hex in radius 3 (4 with a Radius item) is a bad hex to start a movement action of 3+ or a fast travel from. Add +8. A hero who starts outside is not slowed for walking in. Card-text movement does not pay this [Discord, Arty, 2026-09-30].
- While `garrus-silver` is only in his possible pool, not yet played, the same hexes cost +3. He usually spends it on turn 2, not turn 4.
- While discard size ≥ 1 and `garrus-red-1`/`2a`/`3a` is in his possible pool, a hero within 1 / 2 / 3 steps of him is in the hunt, not only the adjacent hex. Add +6, +12 if that red is the known played card.
- While `garrus-red-2b`/`3b` is in his possible pool, a hex adjacent to him is a push hex even when the likely target of the attack is the minion in front of him. Add +8. The landing is 3 away, ignoring obstacles, on the far side of him.
- A single unresolved poke against him is not a good trade. Add nothing to "chip damage." A second attacker on the same turn is the threat that matters; that is the focus rule, not a hex bonus.
- Battle Fury does not make his hand discards dangerous. It makes a resolved card he then discards with silver dangerous. After silver, expect the primary of the card he just discarded, if it still has a target.

## 7. Sources

- Catalog `garrus.json` (2nd printing), including the `advice` field from yvo-niedrich/goa2-faq. Cited as [ADVICE].
- `GarrusHero.kt` and `core/game/rulings/garrus.md` for the decisions (hunt bullet only steps with a discard, Send Flying must move to push, Pilum is not offered with no hero, Fury is resolved-card discards only).
- [BGG 3526542] Cryomancy, 2025-06-12, "Garrus Hero Spotlight (2nd Printing)", community. dusteppe, 2025-06-13: alt red needs initiative or it does not fire; Howl is wasted if it only buys a discard; Testudo sets allies up. Cryomancy's reply the same day: the red is a zone even when it misses, and it punishes heroes who play slow to dodge the gold.
- [BGG 3548024] Cryomancy, 2025-07-25, "Garrus Build & Go Guide", community. Centurion and Bloodhound, and the Howl-on-the-jungler openings. slyestfox, 2025-07-25: Bloodhound is more fun and, for him, less effective; Centurion was the versatile one. Cryomancy's reply: time the Howl around the push and around an ally's chase.
- [BGG 3779890] Cryomancy, 2026-10-09, "Classes of Atlantis": Garrus as Zoner/Disruptor. slyestfox, 2026-10-10, calls him a finisher; Cryomancy, 2026-10-11, keeps the zone label because the hunt is telegraphed. Exozik, 2026-11-03: turn 3 alt red, turn 4 gold, both enemies in a 2v2.
- [BGG 3778446] Avtomatik, 2026-10-06; nichik, designer, the same day: Blunt Force's move must end adjacent to an enemy hero, and without the move there is no push. Cryomancy, 2026-10-07: a push of 0 spaces is still a legal push. Saroule14, 2026-10-15: a normal push is a straight line; this card's "ignoring obstacles" is what the engine already applies.
- [BGG 2886354] nichik, designer, 2022-10-18, inside the 1st-printing Tigerclaw spotlight: Howl matters because Tigerclaw's movement cards are 3+; Pilum when he wants to silver, attack, or take the coins; primary red if the goal is to threaten him. Kept for those two tools. The 2026 Pick Pocket ruling limits the Howl sentence to movement actions. Panetierre, playtester, 2022-10-21, in the same thread: Garrus with Silverarrow, three plans (minions, Attack items, discards). 1st-printing context, mechanisms still on the 2nd-printing cards.
- Gamefound, Wolff Designa, "Defiant Hero Pack overview" (https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/updates/7): official. Howl caps movement and bans fast travel; primary blue and alt red displace slow heroes; one card in the discard powers the gold and the primary red's step; opponents either leave him alone or commit enough hits to kill him. He holds the middle.
- Designer rulings at https://www.cockeyedandcurious.com/goa2/faq (Arty, Discord): Howl is checked at the start of the movement action (2025-02-28); he can hit one hero and push another (2025-08-28); he cannot push an immune hero (2025-12-18); Tigerclaw's Pick Pocket and Master Thief are not capped (2026-09-30). The FAQ's Light Pilum "no hero, can he still move?" records the question and no answer; the engine does not offer the action (`rulings/garrus.md` 6).
- [BGG 2884801] Eric_McClain, 2022-06-16, 1st-printing spotlight. Not used for numbers. The anti-range blue and the gold that discards on the attack are gone.
- BGG wiki "Garrus the Gladiator" (1st edition card table): not used.
- No Garrus-specific Reddit thread or transcribed video was found.
