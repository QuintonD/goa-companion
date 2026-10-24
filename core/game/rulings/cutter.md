# Rulings: Cutter the Sky Pirate

Format as [RULINGS.md](../RULINGS.md): card — question — reading — reason — source. **[APP]** marks a
reading the card text and FAQ leave open. Code: `heroes/CutterHero.kt`; tests: `heroes/CutterTest.kt`.

1. **Walk the Plank / Few More Steps — "if that hero is pushed into another zone".** The Zone of the
   space the push ends in differs from the Zone it started in (a push of 0 never is). The hero must be
   one Cutter can push: an enemy hero under Bulwark is not offered (its first instruction can't be done).
   "Defeat a minion adjacent to you" defeats enemy minions only (p.17 "Defeat": "You can never defeat a
   friendly unit. No exceptions."), with coins. `CutterTest` "Walk the Plank - …".
2. **Grappling Bolt — what can be targeted (FAQ).** "Any obstacle (units, tokens, terrain, or other
   miniatures) … This includes immune units as well as the terrain spaces around the edge of the map
   (she can grapple onto the water)." The target is the first obstacle along each of the six lines within
   range ("with no obstacles between you"); the move stops next to it, so an adjacent obstacle may be
   targeted and Cutter does not move. "Ignore immunity" covers immunity only: effects that forbid
   targeting or interacting (Min's Smoke Bomb, NebKher's Crack in Reality) still apply. **[APP]** a space
   an effect makes "count as an obstacle" during her action is an obstacle she can grapple to.
   Source: Cutter FAQ (goa2-faq). `CutterTest` "Grappling Bolt - …".
3. **Daring Strike / Bold Thrust / Fearless Lunge — the move bullet.** **[APP]** The move ("Move 1 space",
   "1 or 2 spaces in a straight line") is not limited to moves that end in front of a hero: the text does
   not say so (unlike Brogan's charges). It is mandatory; then "Target a hero adjacent to you in the
   direction of the move" is the space one step further in the direction moved; with no enemy hero there
   the action stops (p.10) after the move ("Before the attack" text happens even if the attack fails,
   p.17). The bullet is offered when Cutter can move. `CutterTest` "Daring Strike - no hero in the
   direction of the move …".
4. **Evasive Shot / Tumble Shot — "the opposite direction".** The direction from the target to Cutter,
   taken when the target is chosen; the move is "up to" N spaces in a straight line that way (it may be
   0), also when the target was a minion that was defeated (After the attack: "after the target either
   defends, or is defeated and removed", p.13). Not when the attack did not connect (RULINGS 18).
   `CutterTest` "Evasive Shot - …", "Tumble Shot - …".
5. **Brace for Impact / Ramming Speed / Crashland — "to a space adjacent to an enemy hero; that hero
   discards".** The end space only needs an enemy hero next to it (presence, as Brogan's charge FAQ);
   then Cutter chooses one adjacent enemy hero she can target to discard. If the only one is immune,
   nobody discards. `CutterTest` "Brace for Impact - …".
6. **Bombardment / Barrage / Broadside — "adjacent to another enemy unit".** A presence check: any unit of
   Cutter's enemies next to that hero other than itself (immune units count, p.18). Broadside's repeat
   excludes every hero targeted before. `CutterTest` "Bombardment - …", "Broadside - …".
7. **X Marks the Spot / A Fistful of Coins — the enemy hero chooses.** That hero's controller picks the
   bullet; Cutter then picks the space ("You place that hero"). The placing bullet is offered only when
   it can be done (an empty space in Cutter's radius other than its own, and nothing forbids placing it,
   e.g. Bulwark) — RULINGS 38; otherwise Cutter gains the coins without a question. `CutterTest`
   "X Marks the Spot - …".
8. **A Fistful of Coins — "you alone win the game".** Checked right after the coins are gained (13 or more
   coins in total). **[APP]** The engine's result is per team (`GameView.winner: Team`), so a team game
   ends as a win of Cutter's team with `WinReason.CARD_TEXT` and the event text "Cutter … alone wins the
   game"; the app has no "single hero wins" result. `CutterTest` "A Fistful of Coins - …".
9. **Legend of the Skies (Ultimate) — "The first time each turn after you perform a primary action".**
   The offer comes after the first primary action of the turn only, whether or not Cutter takes it (the
   text names "the first time", not "once per turn" as Arien's Living Tsunami, RULINGS 39). A repeated
   instance is part of that action, so the offer waits until the action is over. "A card in the previous
   turn slot": the card on Turn slot (current turn − 1), faceup, with a Primary action Cutter may perform
   now; none on turn 1. The action performed through it is a primary action too, but no longer the first.
   Secondary actions never trigger it. `CutterTest` "Legend of the Skies - …".

## Cross-check with goa2-backend (`cutter_effects.py`)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| Walk the Plank / Daring Strike "Choose one —" | Both bullets always offered | Only bullets that can start (RULINGS 38) |
| Daring Strike after the move | Targeting the hero in front is optional | Mandatory step (p.10): the attack happens if there is a target |
| X Marks the Spot | Both options always offered to the enemy | The placing option only when a placement is possible |
| Legend of the Skies | `uses_per_turn = 1`, offered only when the previous slot holds a card | Same in effect; the first primary action of the turn uses the chance |
