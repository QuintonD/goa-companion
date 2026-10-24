# Rulings: Hanu the Trickster

Format as [RULINGS.md](../RULINGS.md): card — question — reading — reason — source. **[APP]** marks a
reading the card text and FAQ leave open. Code: `heroes/HanuHero.kt`; tests: `heroes/HanuTest.kt`.

1. **Fight and Flight / Fight or Flight — "If the target is not defeated, After the attack: If able, move
   3 spaces in a straight line."** Only after an attack that connected on a target that survived; the
   move is exactly 3 spaces in one straight line and is made whenever possible ("If able" at the start
   makes the sentence non-mandatory, p.17, so with no such line nothing happens). `HanuTest` "Fight and
   Flight - …".
2. **Hurry up! — which hero.** Any hero in range (friend or enemy) with an unresolved card; never Hanu
   (Range counts 1..N). The hero is targeted (immunity applies). With two unresolved cards (Emmitt's
   Ultimate) Hanu picks the card. `HanuTest` "Hurry up - …".
3. **Hurry up! — how long.** The card's printed Initiative is 11 while that card stays unresolved in the
   same state ("until it is resolved, or otherwise changes state"); items and other modifiers still add
   (p.16). It is not an Active effect (no bold keyword, p.17), so it does not end if Hanu is defeated or
   his card changes state. Initiative order and tie breakers are read again after every resolution
   (p.7), so the change applies to the rest of the turn. `HanuHero.HurryRule`; `HanuTest` "Hurry up - a
   lower card can be raised …".
4. **The Ultimate Trick — "You choose the next action, and how it is performed".** **[APP]** "The next
   action" is the action the hero performs when it next resolves a card (p.7 "Perform One Action"), from
   the choice of action to the end of that action: Hanu answers every decision that hero would make in
   it (which action, targets, spaces, its own optional steps). Not included: respawning (before the
   action, p.7), Defense actions (a response to an attack, p.14), choices the text gives to other
   heroes (a defender still chooses its defense), and choices the hero makes while another hero's text
   is being applied (Reflect Projectiles making the attacker discard: the attacker picks the card). Only heroes targeted while the Ultimate is unlocked; the
   control ends when that action ends or the hero is defeated during it. The decision's `seat` is Hanu.
   `HanuTest` "The Ultimate Trick - …" (three tests).
5. **Helping Hand / Even the Odds / Outnumber — "adjacent to your friendly hero".** A friendly hero of
   Hanu (never Hanu himself, p.17 "Friendly") next to the target: a presence check, the friend is not
   targeted. Both bullets are ranged attacks (the card is Ranged, RULINGS 19). `HanuTest` "Helping Hand -
   …", "Outnumber - …".
6. **Trusted Sidekick / Pile On — "Choose one, or both, in any order".** One action (p.10) with up to two
   attacks: Hanu picks the first bullet, then may also perform the other one if it has a target. Trusted
   Sidekick's second bullet never targets a hero adjacent to Hanu. `HanuTest` "Trusted Sidekick - …",
   "Pile On - …".
7. **Monkey Trick / Monkey Twist / Monkey Business — "Swap two friendly units in radius".** Two different
   units of Hanu's team in radius, not Hanu; both are targeted; swap limits apply (RULINGS 17). Fewer
   than two: no Primary action. `HanuTest` "Monkey Trick - …".
8. **Unexpected Journey / There and Back Again / Safe Travels — immunity.** "That hero is immune": it
   cannot be targeted or affected by any hero's actions or their Active effects (p.18), except its own.
   The common FAQ: "a unit that is immune during “this turn” is still immune when end of turn
   resolves" — This turn effects end after the End of turn text is applied (p.7, p.17). Source: goa2-faq
   common. `HanuTest` "Safe Travels - … (FAQ - still immune then)".
9. **Unexpected Journey — "End of turn: Swap with that hero, regardless of radius and immunity."** The
   swap back happens wherever the two are, even though the hero is immune; not when either is off the
   board, and not when an effect forbids the swap (RULINGS 17). Both effects are Hanu's Active effects:
   if Hanu is defeated they are cancelled (p.13), so there is no swap back and the hero is no longer
   immune. Safe Travels' "You may move 1 space" comes after the swap back. `HanuTest` "Unexpected
   Journey - …".
10. **This Way! / That Way! — "move both of you that number of spaces in the same direction".** The friendly
    hero (in radius, targeted) chooses the distance; Hanu chooses the direction. "Moving in the same
    direction means moving in a straight line in one of those six directions" (p.17 "Direction"). Both
    move exactly that many spaces in that line, or neither moves (the reminder text). **[APP]** When one
    is in the other's way, the one in front moves first; only directions where both can make the full
    move are offered; if there is none, nobody moves. `HanuTest` "This Way - …".

## Cross-check with goa2-backend (`hanu_effects.py`)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| Hurry up! | Any hero but Hanu, base Initiative 11, modifiers stack | Same |
| The Ultimate Trick | Hanu's player chooses "the target's next action (this card, this round)" | Same in effect: the next action the hero performs when resolving a card |
| Journey family | Immune to everyone this turn; forced swap back ignoring range and immunity | Same; not when a "cannot be swapped" effect applies (RULINGS 17) |
| This Way! | Friend picks the distance, Hanu the direction; all-or-nothing | Same |
