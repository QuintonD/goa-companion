# Xargatha (`xargatha`) — heuristic playbook

2nd printing. Dashboard: Attack 5–8, Defense 6, Initiative 3, Movement 6. Complexity 1. Catalog roles: tactician, pusher, disabler, durable, sniper. Advice in `heroes/xargatha.json`.

Units: `heuristic/DESIGN.md`. **[HERO]** needs the count of enemy units adjacent to her (the Attack and Range bonuses, and the enemy-melee −1 Defense that comes with them) and Stone Gaze's "next turn" flag.

She is a Zoner / Initiator [Cryomancy, BGG 3779890]. The fresher the wave, the more bodies she has to wear [TheTomTraveller, BGG 3459245]. She does not have a fast Gold by the roster's ceiling: init 11, Attack 4, and it repeats onto a hero. The damage is the crowd.

## 1. Identity

Fighter who manufactures her own Attack stat by standing in the enemy minions, then pays for it in Defense. The advice is the whole hero: "Surround yourself with enemy minions to boost your powers, but be careful not to overextend."

Proposed `HeroStyle`:

| Field | Value | Why |
|---|---|---|
| `role` | Fighter | Adjacent cleave, a pull, a movement-5 stalk. Alt Red makes her a sniper and is the exception. |
| `idealDistance` | 1 | Silver pulls them to adjacent. Primary Red wants them adjacent. The lunge is not hers; the pull is. |
| `aggression` | 0.6 | Round 1 she can kill a hero and keep a minion [BGG 3515164]. Later she zones. She still dies if four enemy melee are touching her and Red has been spent. |
| `laneWeight` | 0.55 | Cleave is a minion and a hero. Constrict is a minion she did not spend an attack on. She should not become a pure farmer or a pure diver. |
| `keepDefender` | true | Primary Red is Defense 6–7. Blue is 5–6. Gold is 2. Alt Red is Defense 4. On the swipe path, Red is both the weapon and the wall, so she blocks with Blue when Blue's 5–6 is enough. |
| `upgradeOrder` | Green first is the common instinct and not a rule; see §3 | Ruwakh asked if Green-first is mandatory [BGG 3459245]. It is not. It is right when tier-1 Charm (ranged minion only) is doing nothing. |

Minion modifiers, because every bonus below is paid in them [rulebook p.13, the numbers STRATEGY §1.9 uses]: an adjacent enemy melee is −1 Defense, an enemy ranged minion within 2 is −1, friendlies are the opposite. Her +1/+2/+3 Attack per other adjacent enemy unit is on top of that, and the target of the attack is not counted [CARD]. Two enemy melee beside her: Attack +2 or +4 or +6, Defense −2, before the ranged minion. That is the overextend.

## 2. Card-by-card

### `xargatha-gold` Cleave — Attack, init 11, Attack 4, Defense 2, Movement 1

Adjacent unit. After: she may repeat once on a different enemy hero, even if the first target was a minion. One minion only [TheTomTraveller, BGG 3459245].

- **For:** the minion and the hero, or the hero and the other hero. Also: hit the minion that was giving the hero +1, then the hero [same post]. Init 11 is "before their Red", not "before everyone".
- **When:** she is adjacent to a minion and to a hero whose remaining defense loses to Attack 4 plus modifiers, or the minion alone is the push. Round-1 openings in §3 are this card on turn 3 or 4 after Silver has placed the body.
- **Do not:** repeat onto a second minion. The repeat is a hero. Do not spend Gold as Defense 2.
- **In hand:** the threat that makes them careful where they stand to take a minion.

`xargatha-handicap` Desperate Cleave is init 10, Attack 3, Defense 1, hero only, and the repeat is still a different enemy hero.

### `xargatha-silver` Siren's Call — Skill, init 3, Defense 3, Range 3, no Movement

An enemy unit that is not adjacent, in range 3, moves up to 3 to a space adjacent to her, if it can. If it cannot end adjacent, it does not move at all [nichik, BGG 3459245, 2025-07-25; rulings 47]. AI.md names this card as one the old opponent Holds with no target. Do not.

- **For:** the setup, not the reaction. Pull a minion she will cleave, pull a hero into her ranged minion's −1 and her Gold, pull a hero off her team's minion [TheTomTraveller]. Init 3 means anyone with a faster card has already moved. Anyone still holding a Green of init 4–6 acts after her and can walk off. Play it when the target's movement is already spent, or their remaining card is slower than 3 and has no place-effect.
- **When:** the destination adjacent hex exists within 3 and is the hex that creates the Attack bonus or the −2 Tom's opening wants (beside her ranged minion, beside the enemy centre minion).
- **Do not:** target a unit that has no path of ≤3 onto an adjacent hex. Do not pull a fresh hero onto her when she is on alt Red (Defense 4) and their Gold is still live. Do not Hold it; if there is no pull, take the Defense 3 into the next turn or move with something else.
- **In hand:** Defense 3, and the threat of the pull, which good players walk away from if they still have a slow move [TheTomTraveller].

### `xargatha-red-1` Threatening Slash — Attack, init 7, Attack 5, Defense 6, Movement 5

Adjacent unit. +1 Attack for each other enemy unit adjacent to her.

### `xargatha-red-2a` Deadly Swipe — item: Initiative. Init 8, Attack 5, Defense 7, Movement 5

+2 per other enemy unit. `xargatha-red-3a` Lethal Spin (item: Radius, init 8, Attack 5, Defense 7): +3 per.

- **For:** the crowd button, and a Movement 5 stalk at init 7–8. "Look at this card more as a way to deny space for enemies walking up instead of getting all this free damage." Turn 3 she follows someone who has played Green, five spaces, and Golds them next turn [TheTomTraveller]. One enemy minion beside her is already Attack 7 / 9 / 11 before items, and Defense −1 from that melee if it is a melee minion.
- **When:** two or more other enemy units are adjacent and the total beats the target's block, or she is stalking a hero who has spent their move. Also the block: Defense 6–7.
- **Do not:** stack four enemy melee for a vanity total if the −4 Defense means the next attack kills her and the swing is not lethal. The advice is the cap.
- **In hand:** the zone. They do not walk up.

### `xargatha-red-2b` Long Thrust — item: Defense. Ranged attack, init 8, Attack 3, Defense 4, Movement 4, Range 1

+1 Range per enemy unit adjacent to her. `xargatha-red-3b` Rapid Thrusts (item: Movement, init 8, Attack 4, Defense 4, Range 1): the same, and she may repeat once on a different enemy hero.

- **For:** a shot that is melee until the crowd, then range 2, 3, 4. A Range item makes it 2 with nobody adjacent [TheTomTraveller]. He calls it his favourite change from the previous printing. It is how she snipes and still Constricts a minion at end of round.
- **When:** she is already in a knot of enemy minions and a hero is standing off, or she has the Range item and a line of 2. Rapid Thrusts: the first target should be the hero if she wants the repeat; the repeat is a hero, so a minion-then-hero works, hero-then-hero works, hero-then-minion does not.
- **Do not:** take this Red into Arien and Brogan's high attacks with Defense 4 and no Blue left [Ruwakh's first game, BGG 3459245]. She blocks with Blue (Defense 5–6) and plays scared.
- **In hand:** a bad block. Defense 4.

### `xargatha-green-1` Charm — Movement, init 5, Defense 3, Movement 2, Radius 2

Before or after the move, she may move an enemy ranged minion in radius up to 2. Offered before the move and, if declined, after [rulings 48].

### `xargatha-green-2a` Control — item: Attack. Init 4, Defense 3, Movement 2, Radius 2

An enemy ranged or melee minion. `xargatha-green-3a` Dominate (item: Attack, init 4, Defense 4, Movement 2, Radius 2): any enemy minion, up to 2, ignoring heavy immunity.

- **For:** the flexible one. Tom's list, all still on this text: block a path with the minion, hand it to a teammate, step away from a threat and leave the minion in kill range, drag it onto an enemy hero so Gold next turn is a minion and a hero. DaFink [BGG 3459245]: this is the support Green; Constrict is the efficient one. Ruwakh's first 2nd-printing game: primary Green "just gave so much freedom."
- **When:** the 2-space minion move changes an adjacency (her Attack bonus, an ally's farm, a block). The Movement 2 is a bonus, not the point; she has Movement 5 on Red.
- **Tier 1** moves only a ranged minion. If the only ranged minion is irrelevant, this card is a 2-step and an upgrade priority, not a dead card: the step still happens.
- **Do not:** spend it only to walk 2 when Red could have stalked 5, unless the minion move is the 15 points.

### `xargatha-green-2b` Constrict — item: Attack. Skill, init 4, Defense 3, Movement 2

End of round, before the minion battle: defeat an adjacent enemy melee minion. `xargatha-green-3b` Final Embrace (item: Initiative, init 4, Defense 4): melee or ranged. Never the heavy [FAQ; Tom].

- **For:** a minion without spending Red, so Red can move, threaten, or block [Cryomancy, BGG 3459245, 2025-03-20]. Silver pulls the victim in. She still has to be adjacent when the round ends.
- **The trap:** she sits on the minion instead of standing where next round starts. "A loss of tempo which can be the difference between a double push or not" [TheTomTraveller]. Enemies will try to walk her off it [DaFink].
- **When:** the minion flips the battle, she is not the one who must cross the map, and they cannot legally shove her off before end of round (or she can step back on with a later card — but this card is the effect, and a later move that leaves adjacency loses it).
- **Do not:** play it on turn 1 of a round where she needs to be elsewhere at the end, for a minion the battle would have removed anyway.

### `xargatha-blue-1` Stone Gaze — Skill, init 9, Defense 5, Movement 3, Radius 2

Next turn: enemy heroes in radius count as heroes and as terrain, and cannot take Movement actions. The radius moves with her. Fast Travel is a Movement action, so it stops [FAQ]. Place, swap, and card-text moves still work [rulings 46]. Not into the next round [FAQ].

### `xargatha-blue-2a` Petrifying Stare — item: Defense. Init 10, Defense 6, Movement 3, Radius 3

Same. `xargatha-blue-3a` Turn into Statues (item: Defense, init 10, Defense 6, Radius 4): same, radius 4.

- **For:** they took a minion and now they cannot walk to the next one. Easier as the radius grows [TheTomTraveller]. Also the lock after she has pulled them in: they attacked, or they didn't, and next turn they are terrain [BGG 3515164, the Cobra's Bite pattern]. Tier 1 radius 2 is hard; he says so, and Ruwakh's first game agreed.
- **When:** an enemy in radius 2 (then 3, then 4) has a reason to move next turn, and she will still be near them (the radius follows her, so she can step and keep them). Init 9–10 is before many Reds, so she gazes and then they act this turn under no gaze. The gaze is tomorrow's turn. Do not expect it to stop the attack they are playing now.
- **Do not:** play it when she needs Blue's Defense 5–6 for a hit that is already revealed. Do not expect it to stop Xargatha-like text moves, Whisper's place, or a swap.
- **In hand:** a real block, and a blank threat until the radius is 3 or 4.

### `xargatha-blue-2b` Fresh Converts — item: Attack. Init 10, Defense 6, Movement 3

If she is adjacent to an enemy minion, she may retrieve. `xargatha-blue-3b` Devoted Followers (item: Range, init 10, Defense 6): adjacent to an enemy unit, so a hero counts.

- **For:** the heal, standing in the crowd she wanted anyway. Silver or primary Green puts the minion there [TheTomTraveller]. The discard-heavy enemy team is the reason to take it. Tier 3, a hero is enough, so she can retrieve after the pull.
- **When:** she is already adjacent, and the card in the discard is the one the next turn needs. The primary still happens if she declines the retrieve; the condition is the adjacency.
- **Do not:** walk into a lethal crowd only to heal. The Attack item is the other reason (Cobra's Bite takes it for the item).

### `xargatha-purple-4` Metamorphosis

+1 Movement and +1 Initiative per enemy unit adjacent to her. She may move through obstacles on her own moves, not when someone else moves her, and not on pushes [rulings 45].

- The crowd that used to only add Attack now also makes her fast and long. A swipe at init 8 plus two adjacent enemies is init 10 and Movement 7, through bodies. She should be in the knot on purpose from level 7, and still leave before the Defense goes to zero.

## 3. Upgrade path

**Horde Slayer (Defense, the swipe)** [Cryomancy, BGG 3515164]:

1. `xargatha-green-2a` Control if Charm cannot see a melee minion she needs. The Attack item also lifts Gold and Red.
2. `xargatha-red-2a` Deadly Swipe. +2 per body, Defense 7, Initiative item.
3. `xargatha-blue-2b` Fresh Converts. The heal, and she is the target. (Petrifying Stare is the Defense item if she would rather lock than heal; the spotlight takes Converts here.)
4. Tier III: `xargatha-green-3a` Dominate first (the heavy, the Initiative item). Then `xargatha-red-3a` Lethal Spin (+3, Radius item — it does nothing for Red itself; it grows Gaze and Charm if those icons are in hand). Then `xargatha-blue-3a` Turn into Statues for the radius-4 lock.
5. The push variant: `xargatha-red-3b` Rapid Thrusts first (Movement item, the repeat, the shot), stay on Statues (Range item, so the thrust is range 2 with an empty neighbourhood), finish `xargatha-green-3b` Final Embrace so the third minion is end-of-round and can be a ranged minion.

**Cobra's Bite (Gold is the threat)** [BGG 3515164]:

1. `xargatha-green-2b` Constrict for the Attack item and a minion that is not her attack.
2. `xargatha-blue-2a` Petrifying Stare, the second Attack item, the lock.
3. `xargatha-red-2a` Deadly Swipe for the Defense item so the bluff is survivable. Pattern: Silver or Green puts her there, she threatens Gold, plays Blue instead. They hit her and cannot leave next turn; they leave and she walks back up.
4. Tier III: `xargatha-green-3a` Dominate (Initiative). Stay on `xargatha-blue-3a` (Range item). `xargatha-red-3b` Rapid Thrusts so the Range item becomes a shot, and with the repeat she can touch four targets in a round if the crowd, the pull and the repeat all line up. The spotlight's "3 attack and 7 damage" on Gold is Attack items: Constrict, Petrifying Stare, and Final Embrace are all Attack items, so Gold's 4 becomes 7 if all three are equipped. That requires Final Embrace in hand or as the item. His alternate takes Final Embrace first for that reason. Count icons, do not count a third Attack item that was tucked into a non-attack hand card.

**Tuck:** on the swipe path, Initiative from Deadly Swipe wants to be equipped so init 8 becomes 9 and she stalks ahead of more Greens. Attack items want to be equipped so Cleave's 4 crosses a defense. Defense from Long Thrust is the tuck when Swipe is the hand card — she already has Defense 7 on it. Radius (Lethal Spin) is a weak tuck until Gaze or Charm needs the extra hex; if she is on Statues, that card's item is Defense, and Lethal Spin's Radius in hand is the one that grows the gaze. Keep Lethal Spin's item by putting the other Red in the dashboard only when she wants the shot; otherwise the Radius sits on the swipe and Gaze does not grow. On the thrust path, the Range item (Statues, or Devoted Followers) wants to be equipped. Movement (Rapid Thrusts) wants to be equipped for the stalk.

**Phase:** round 1 she is one of the best openers in the box [BGG 3515164, and Cryomancy's reply]. Do not overcommit into a disrupt. Levels 1–3, learn the pull-then-cleave and do not live on Constrict's tempo loss. Levels 4–6, either the +3 swipe or the ranged repeat, and the gaze at radius 4 if heroes are the win. Green-first only when the ranged-only Charm is blank [Ruwakh's question; Tom's scaling].

**Openings** (Forgotten Island, from the Build & Go; the map is the one in the catalog):

- **Deadly Stare:** stand next to her ranged minion and the enemy centre minion. Silver pulls the enemy twin-taker next to the twins and into the ranged minion's radius (he hopes for a net −2). Gold takes the centre minion and that hero. They live by blocking with a big Blue, by going behind the twins, or by holding Gold to run on turn 4 and giving up the minion.
- **Fatal Attraction:** same shape on the jungle minion, ally strikes the twin-taker on turn 3, she pulls and Golds on turn 4.
- **Minions' Bane:** stand in the dead zone beside both twins and in the ranged minion's range, Gold one of them and the hero who came for the centre, Silver the other minion in for next round. If that hex is too hot, stand between the near twin and the centre and Silver the far minion in.
- **Double take:** open Green, save Blue as the block, Red moves her next to the enemy centre or the ranged minion, Silver pulls the other one to her ally. She does not ask about "Double Take 1"; this is the smoother one [BGG 3515164].

## 4. Synergies and counters

**With:** Hanu. His Red wants bodies beside her; his Silver can turn her primary Red's bonus on; his Green and Blue can swap a hero onto her or her onto them; his Blue plus her Stone Gaze is a lock; his Silver can also speed an enemy Green so that after her init-3 pull they are last, or they have to spend the move to leave [Hyc90, BGG 3487749, 2026-07-21]. Razzle, for the same "cares about friendly heroes" reason [AegisToast, same thread, 2025-03-30] — Razzle's extra bodies are heroes, and Metamorphosis and the swipe count enemy units, not friends. AegisToast's point is heroes who want friends nearby; Xargatha's numbers want enemies nearby. Use Razzle to deliver the enemy, not as a substitute for the crowd. Anyone who wants a discard or a body pulled off a spawn (Tom: "Anybody who likes empty spawn points"). Whisper wants the empty spawn she makes by pulling a minion off one.

**She fears:** Cutter and Sabina, anyone who punishes her for standing in enemy minions, anyone who moves the minions away, swaps [TheTomTraveller]. Garrus. On alt Red, high Attack in melee [Ruwakh]. Being shoved off a Constrict minion. A pull that cannot finish adjacent, which the old bot plays anyway [AI.md].

**Against her:** do not end a turn adjacent to her if your movement is spent and her Gold is live. Do stand on the far side of a minion so the pull's only adjacent hexes are bad for her, or body-block those hexes: if she cannot end you adjacent, you do not move [nichik]. Keep a slow move until after init 3 if you are the one she wants. Walk her off the minion before end of round when Constrict is resolved. Against the swipe, do not donate extra bodies; one minion is +1/+2/+3, three is how she one-shots. Against Stone Gaze, play the skill and the card-text move, not the boots. Against the thrust, do not stand off-angle while she is in a knot of your minions; stand where the range bonus does not reach, or kill her in melee where Defense 4 is the number.

A new group called her the only S-tier after a few games; Drinkdrawers notes that expert players do not [BGG 3459245, 2026-08-16]. The bot should play the openings and the tempo warning, not the beginner ranking.

## 5. Common mistakes

- Holding Siren's Call with no legal adjacent destination [nichik; AI.md].
- Pulling a unit "part way" when it cannot finish adjacent. It stays.
- Counting the attack's target in the +Attack [CARD].
- Stacking enemy melee until Defense is gone and the swing was not lethal [ADVICE].
- Constrict, then walking to a "better" hex and losing the minion. Or never walking, and losing the next round's position [TheTomTraveller].
- Expecting Stone Gaze to stop a place, a swap, or a "move 2" in card text [rulings 46; Tom's clarification is right about the boots and easy to over-read].
- Playing Gaze to stop an attack this turn. It is next turn.
- Two minions with Cleave.
- Charm at tier 1 aimed at a melee minion. It will not move.
- Green-first as dogma when Control is not the problem and Red's +2 is [Ruwakh's question, answered by the rest of the thread as "usually, not always"].
- Treating Metamorphosis as a push through enemies when someone else is moving her [rulings 45].

## 6. Heuristic rules

`evalTerms`:

- Each enemy unit adjacent to her, while primary Red or the thrust is in hand: +3, minus 2 per enemy melee beyond the second (the Defense bill). So one minion is +3, two is +4, four is +4 − 4 = 0 unless the attack this turn kills. [HERO]
- Stone Gaze in force on enemy heroes: +4 per enemy in her current radius who still has a Movement action they would have used. [HERO]
- Constrict / Final Embrace resolved, her still adjacent to a legal minion: +15 if that minion flips the projected battle, else +4. Zero if she has a later move that will leave. [HERO]

`threatAdjust`: while Gold is unplayed, every hex adjacent to her is Attack 4, init 11, and a second enemy hero adjacent to her is in the same action. While primary Red is unplayed, add the bonus for the crowd that would exist if they stepped in. While Silver is unplayed, a unit within 3 that has a path onto an adjacent hex can be pulled at init 3.

1. **H1** [PLAY_CARD] [`xargatha-silver`] **WHEN** an enemy unit in range 3 is not adjacent, a hex adjacent to her is reachable by that unit in ≤3, and that hex makes a Gold target, a Constrict target, or a −1/−2 modifier on a hero her ally or her Gold will hit **THEN** pull them onto that hex. Prefer a hero who has already spent their movement. **WEIGHT** +15 if the pulled minion becomes a kill, +8 if the hero's defense drops by 2 and an attack is coming. **SOURCE** [BGG 3515164 Deadly Stare; TheTomTraveller; nichik BGG 3459245] **[HERO]**

2. **H2** [PLAY_CARD] [`xargatha-silver`] **WHEN** no unit can end adjacent to her **THEN** do not play it. Move with Red or Green, or hold a different card. **WEIGHT** −6 for the Hold the old bot takes. **SOURCE** [nichik, BGG 3459245; rulings 47; AI.md] **[GENERAL]**

3. **H3** [PLAY_CARD] [`xargatha-gold`] **WHEN** she is adjacent to an enemy minion and an enemy hero, and Attack 4 plus modifiers beats the hero's remaining defense or the minion is the one that then lets the repeat beat it **THEN** hit the minion, repeat on the hero. **WHEN** only the minion is there and it is worth 15 **THEN** hit it and decline the repeat. **WEIGHT** 15 plus the kill or the strip. **SOURCE** [CARD; BGG 3459245; BGG 3515164] **[GENERAL]**

4. **H4** [CONFIRM] [Cleave's repeat, Rapid Thrusts' repeat] **WHEN** a different enemy hero is a legal target and Attack 4 (or the thrust's Attack) plus the crowd beats their remaining card, or they have no card **THEN** yes. **WHEN** the repeat only chips a hero who will block with a card they can spare **THEN** no. **SOURCE** [CARD] **[GENERAL]**

5. **H5** [PLAY_CARD] [`xargatha-red-1`, `xargatha-red-2a`, `xargatha-red-3a`] **WHEN** at least one other enemy unit is adjacent and the bonus flips the target's defense, and the enemy-melee count beside her is ≤ 2 or the swing is lethal **THEN** attack. **WHEN** she is not in the crowd and a hero within Movement 5 has spent their Green **THEN** walk the 5 and hit them if the arrival is safe, else walk to the hex beside them for Gold next turn. **WEIGHT** the kill, or +6 for the stalk that sets it. **SOURCE** [ADVICE; TheTomTraveller] **[HERO]**

6. **H6** [PLAY_CARD] [`xargatha-red-2b`, `xargatha-red-3b`] **WHEN** her Range (1 + adjacent enemies + a Range item) reaches a hero or a minion she is not standing on, and she is not in a hex their melee kills given Defense 4 **THEN** shoot. Rapid Thrusts: hero first if a second hero is also in the new range. **SOURCE** [CARD; TheTomTraveller] **[HERO]**

7. **H7** [PLAY_CARD] [`xargatha-green-1`, `xargatha-green-2a`, `xargatha-green-3a`] **WHEN** a legal minion in radius can be moved onto her (the bonus), onto an ally, off an enemy, or into a gap **THEN** move it, before her own step if that order is what creates the adjacency, else after. Dominate may take the heavy. **WEIGHT** +15 if someone then defeats it, +3 if it only adds to her bonus. **SOURCE** [TheTomTraveller; DaFink BGG 3459245; rulings 48] **[HERO]**

8. **H8** [PLAY_CARD] [`xargatha-green-2b`, `xargatha-green-3b`] **WHEN** she can end the round adjacent to a melee minion (or a ranged one, on Final Embrace), that minion flips the battle, and she is not required on the far side of the next zone **THEN** play it and do not spend a later move leaving that adjacency. **ELSE** do not. **WEIGHT** +15, or −8 if the tempo loss gives up the next push [TheTomTraveller]. **SOURCE** [CARD; FAQ; BGG 3459245] **[HERO]**

9. **H9** [PLAY_CARD] [`xargatha-blue-1`, `xargatha-blue-2a`, `xargatha-blue-3a`] **WHEN** an enemy in radius will want a Movement action next turn (they just took a minion, or they are pulled adjacent and their escape is boots) and Blue is not the only card that survives a revealed attack this turn **THEN** gaze. Prefer radius 3 or 4; at radius 2, only if two enemies are already inside. **WEIGHT** +4 per enemy whose next turn loses its move. **SOURCE** [TheTomTraveller; FAQ; BGG 3515164] **[HERO]**

10. **H10** [DEFEND] **WHEN** a hit is revealed this turn and Gaze would not stop it **THEN** block with Blue or Red, whichever is the cheaper card that wins, and do not play Gaze. **SOURCE** [REASONED; FAQ "next turn"] **[GENERAL]**

11. **H11** [PLAY_CARD] [`xargatha-blue-2b`, `xargatha-blue-3b`] **WHEN** she is already adjacent to an enemy minion (or any enemy unit, on Devoted Followers) and the top discarded card is worth more than the Blue's Defense in hand **THEN** retrieve it. **SOURCE** [TheTomTraveller] **[HERO]**

12. **H12** [PLAY_CARD] [the Cobra bluff] **WHEN** she has threatened Gold by standing adjacent with it in hand, Blue's gaze is legal, and the enemy's likely card is a Gold attack on her **THEN** play Blue, not Gold, if she survives their attack on Blue's Defense. If they leave instead, she still has Gold. **SOURCE** [BGG 3515164] **[HERO]**

13. **H13** [UPGRADE_CARD] **WHEN** Charm cannot touch the minions that matter (they are melee, and she is still tier 1) **THEN** Green first. **WHEN** she is already getting the pull-cleave and dying **THEN** the Defense item (Swipe, or Fresh Converts / Petrifying Stare) before a third Attack item. **SOURCE** [Ruwakh and DaFink, BGG 3459245; BGG 3515164] **[GENERAL]**

14. **H14** [KEEP_UPGRADE] [Red pair] **WHEN** the wave is full and melee is survivable **THEN** Swipe / Lethal Spin. **WHEN** she must not be the body in the knot, or Constrict is already her second minion **THEN** Long Thrust / Rapid Thrusts, and Blue stays the block. **SOURCE** [TheTomTraveller; Ruwakh] **[GENERAL]**

15. **H15** [KEEP_UPGRADE] [Green pair] **WHEN** allies need minions delivered **THEN** Control / Dominate. **WHEN** she needs a third minion and can sit still at end of round **THEN** Constrict / Final Embrace. **SOURCE** [DaFink, BGG 3459245] **[GENERAL]**

16. **H16** [CHOOSE_SPACE] [her own move] **WHEN** the destination is adjacent to 1 or 2 enemy units and not to 3+ enemy melee, unless Lethal Spin this turn kills from the larger crowd **THEN** that hex, on the side of her own minions if one is free. **WEIGHT** the evalTerms curve. **SOURCE** [ADVICE] **[HERO]**

17. **H17** threatAdjust [`xargatha`] **WHEN** Gold is unplayed **THEN** every adjacent hex is Attack 4 at init 11, and a second adjacent enemy hero is included. **WHEN** Silver is unplayed **THEN** any enemy unit within 3 that has a ≤3 path to an adjacent hex can be pulled at init 3; if no such path exists, add nothing. **WHEN** Gaze will be in force on their next turn **THEN** drop their Movement-action destinations. **SOURCE** [CARD; nichik; rulings 46] **[HERO]**

18. **H18** [PLAY_CARD] [against her] **WHEN** she has resolved Constrict and a move can take her off the minion **THEN** that move is worth the minion's 15. **WHEN** she is in a crowd on the swipe **THEN** do not add a friendly minion to her adjacency if you can take it instead. **SOURCE** [DaFink; ADVICE] **[HERO]**

19. **H19** [PLAY_CARD] [against her Silver] **WHEN** she has not played it and you still have a movement card of init ≤ 3 **THEN** step off the pull's landing hexes, or stand where no adjacent hex of hers is reachable in 3. **SOURCE** [nichik, BGG 3459245] **[GENERAL]**

20. **H20** [CONFIRM] [`xargatha-purple-4`] **WHEN** she moves **THEN** she may pass obstacles. Add +1 init and +1 movement per adjacent enemy unit to the card she is resolving, for ordering and for distance. Do not add them when another hero moves her. **SOURCE** [CARD; rulings 45] **[HERO]**

21. **H21** [RESPAWN] **WHEN** she dies **THEN** respawn toward the crowd, not toward an empty flank. Her tools do nothing in an empty zone. **SOURCE** [ADVICE; REASONED] **[GENERAL]**

22. **H22** [ACT_FIRST] **WHEN** she is tied with an ally and her pull or her gaze sets up their attack **THEN** she acts first. **WHEN** their attack sets up the body she pulls **THEN** they act first. **SOURCE** [BGG 3515164 Double Take; REASONED] **[GENERAL]**

## 7. Sources

- Catalog `heroes/xargatha.json`, handicap `xargatha-handicap`, FAQ (Stone Gaze, Constrict vs the heavy, next-turn does not cross the round).
- `heroes/XargathaHero.kt`, `core/game/rulings` 45–48.
- [BGG 3459245](https://boardgamegeek.com/thread/3459245) Xargatha spotlight (2nd printing), TheTomTraveller, 2025-02-11, community. DaFink, 2025-02-19, on the two Greens. Ruwakh, 2025-05-03, first game: primary Green, Gaze is hard at tier 1, alt Red felt fragile into Arien and Brogan. Designer `nichik`, 2025-07-25: if the pull cannot end adjacent, the target does not move. Drinkdrawers, 2026-08-16: beginners overrate her relative to experts.
- [BGG 3515164](https://boardgamegeek.com/thread/3515164) Build & Go, Cryomancy, 2025-05-21. Openings and the two builds. Attack-item totals in that post were checked against catalog icons.
- [BGG 3487749](https://boardgamegeek.com/thread/3487749) Hyc90, 2026-07-21, Hanu pairing; AegisToast, 2025-03-30, Razzle with heroes who want friends nearby.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Zoner / Initiator.
- 1st-printing spotlight [BGG 2897093](https://boardgamegeek.com/thread/2897093) (GoatRogan, 2022) was not used. Tom notes she was one of his least favourite heroes then and one of his favourites after the reprint [BGG 3459245].
- Tom's heading "Dangerous Slash" is not the 2nd-printing name; the card is `xargatha-red-1` Threatening Slash. The quoted bonus matches the current text. His "boots icon" line matches the Stone Gaze FAQ; rulings 46 are the operational reading (place, swap and card-text moves still work).
- No Xargatha strategy thread after the August 2026 comment on 3459245. The October 2026 forum pages have none.

## Implementation notes

`XargathaHeuristics.kt` (ADR-085). The general book scores an attack card from its printed reach (`HContext.strikeFrom`). Where this hero's text narrows the targets, the playbook re-scores the card's PLAY_CARD outlook on the units the engine would really offer (`BatchDSupport.strikeOn` + `strikeFix`): the card keeps its movement value and loses the hit it cannot make. That replaces the draft's flat −28 "fizzle" penalty, which also threw away the card's secondary Movement.

- **Implemented:** H1/H2 (Siren's Call only with an enemy unit 2–3 away; best with an attack in hand and a minion to pull), H3/H4 (Cleave: the adjacent hit plus the repeat on a different enemy hero; YES on the repeat), H5 (the Slash with its +1/+2/+3 Attack per other enemy unit beside her), H6 (Long Thrust's range is 1 plus the enemies beside her; Rapid Thrusts' repeat), H8 (Constrict: a minion beside her now, worth more later in the round), H9 (Stone Gaze with enemy heroes in radius), H11 (Fresh Converts only beside an enemy minion, or unit on tier III, with a card to retrieve), H14/H15 (KEEP: Deadly Swipe and Control), H17 (Silver's pull as a threat). `evalTerms`: Constrict resolved with a legal minion beside her +5, Stone Gaze pending +1.5.
- **Engine vs research:** the Slash bonus counts every other enemy *unit* beside her, heroes included, not only minions; Constrict is an end-of-round effect that checks adjacency then; Charm (tier I) moves only an enemy ranged minion. No disagreement.
- **Skipped:** H7 (Charm's minion move is a CHOOSE_UNIT/space sequence scored by the general minion placement), H10 (DEFEND: general), H12 (the Cobra bluff needs the enemy's intent), H13 (upgrade colour order), H16 (her own move: the general placement with exposure already prices the crowd's Defense bill through the minion modifier), H18/H19 (advice for the other side), H20 (Metamorphosis is engine-side), H21 (respawn), H22 (ACT_FIRST: general).
- **Ablation (strength 8):** 49–47 of 96 = 51.0% (Wilson 41.2–60.8%), with the playbook against without it, 2026-11-21 (`AI_H_ABLATION=xargatha AI_H_STRENGTH=8 AI_H_GAMES=96`: 48 line-ups, both sides).
