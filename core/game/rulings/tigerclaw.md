# Rulings: Tigerclaw the Cutpurse

Format as in [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** = the
card text and FAQ are silent and the engine takes the reading closest to the text. Code:
`heroes/TigerclawHero.kt`; tests: `heroes/TigerclawTest.kt`.

1. **Blink Strike / Nimble Strike — the move.** "Move 2 spaces in a straight line through an enemy
   unit": the first space holds an enemy unit (a hero for Nimble Strike), the second is empty; that
   unit is the only obstacle passed (text movement, p.11, not a Movement action). The move is
   mandatory: without it there is no attack and no Primary action (FAQ "Does Tigerclaw's Blink Strike
   attack require movement?"). Test: "Blink Strike - FAQ".
2. **[APP] Blink Strike through an immune unit.** Moving through is presence, not targeting (p.18:
   units that ignore obstacles may move through an immune unit), so the move happens; "target that
   unit" then has no valid target and the action stops (p.10). Same reading as Brogan's Mad Dash FAQ
   ("the move may end next to an immune unit, and then the attack fizzles"). Test: "Blink Strike - APP".
3. **[APP] Blend Into Shadows — not adjacent to terrain.** The first instruction cannot be done, so the
   Primary action is not offered (FAQ "Can I play an action if only some of its instructions can be
   performed?"). Terrain includes the water around the board and units counting as terrain (p.19).
   The placement is mandatory once adjacent ("place yourself …; if you do"). Its immunity covers
   attack actions of enemy heroes and their Active effects (p.18). Next turn only (FAQ common: never
   into the next round).
4. **Combat Reflexes.** "If you did not move before the attack" = the optional move before the attack
   was declined or impossible.
5. **Backstab / Backstab with a Ballista.** "A friendly unit adjacent to the target" is presence (p.18),
   never Tigerclaw himself (p.17 "Friendly"). Ballista: "the target cannot perform a primary action to
   defend" removes the cards whose Defense action is primary (Defense and Defense/Skill cards, p.14)
   from the defender's options; cards with a secondary Defense action may still defend. Test:
   "Backstab with a Ballista". (Engine change: `Combat.defend` filters those cards when
   `noPrimaryDefense`.)
6. **Light-Fingered / Pick Pocket / Master Thief.** The steal is mandatory: with no enemy hero
   adjacent after the first move, the action stops and the last move is not made (FAQ "Does
   Tigerclaw's green thievery skill require an enemy hero?"). **[APP]** An adjacent enemy hero with no
   coins may be chosen; nothing is taken, so "if you do" fails and there is no second move. Master
   Thief: the number (1 or 2) is asked only when the hero has 2 or more coins.
7. **[APP] Poisoned Dagger / Dart — "a hero in range".** The text does not say enemy, so any hero in
   range may get the marker, friendly ones included; never Tigerclaw (p.10: an action affects you only
   when it says so). There is one Poison marker: giving it again moves it. Its strength is the card
   that gave it (-1 Dagger, -2 Dart); it applies to the Initiative, Attack and Defense values of every
   card of the holder until it is returned (end of round, or the holder's defeat, p.13). FAQ "What
   happens if Tigerclaw's poison reduces a value below 1?": values may be 0 or negative. Agrees with
   goa2-backend.
8. **[APP] Blocks (Dodge, Sidestep, Evade, Parry, Riposte).** "Block a (non-)ranged attack" is the
   first, mandatory instruction of the Defense text: when the attack is of the other kind the Block
   fails (the defender is defeated, p.14) and the rest of the text is skipped (p.10) — no move, no
   retrieve, the attacker discards nothing. Agrees with goa2-backend (`build_on_block_steps`) and with
   Wasp's FAQ reading of "Block a ranged attack" against a non-ranged attack. Tests: "Sidestep - APP",
   "Parry - APP".
9. **Evade — "your resolved or discarded basic skill card".** The silver card (Blend Into Shadows) when
   it is resolved or discarded and faceup; retrieving it from a Turn slot cancels its Active effect (a
   state change, p.17).
10. **Parry / Riposte.** The attacker chooses the card it discards; with no card, Riposte defeats it
    (coins to Tigerclaw) and the attacker's action ends.
11. **Cloak and Daggers (Ultimate).** "A basic action" is any action on a basic card (p.9: "All
    actions on a basic card are basic actions"): the Primary action and the secondary Movement, Fast
    Travel, Clear and Attack actions of the gold and silver cards (and the Handicap card). Defense is not
    repeated (it is performed only in answer to an attack, p.14) and Hold does nothing. The repeat is
    asked each time Tigerclaw performs such an action (no "once per turn" in the text), never for a
    repeated instance (p.10), not while an effect forbids repeating (Dodger's Enfeeblement). "Cannot
    target the same unit": every unit the first instance targeted is excluded. **[APP]** The question
    is skipped when the repeat could do nothing at all (no target for a secondary attack, no Fast
    Travel destination, no adjacent token to Clear).

## Disagreements with goa2-backend (cross-check only)

| Card | goa2-backend | Engine |
|------|--------------|--------|
| Blink Strike | "Target a unit adjacent to you in a straight line; move to the space directly behind it" — the hero is *placed* behind the unit | Card text: "Move 2 spaces … through an enemy unit" — a text move, not a placement (Magnetic Dagger does not stop it, a Movement-limit effect does not apply); ruling 1 |
| Cloak and Daggers | `uses_per_turn=1` | No such limit in the text; each basic action may be repeated once (ruling 11) |
