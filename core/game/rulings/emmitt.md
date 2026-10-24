# Rulings: Emmitt the Traveler

Format as [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** where the
card text, the hero's FAQ (none for Emmitt) and the rulebook are silent. Code: `heroes/EmmittHero.kt`;
tests: `heroes/EmmittTest.kt`.

1. **Reverse Time / Tear in Time.** A "Next turn" effect (p.18): in force for the whole next turn of
   this round only (goa2-faq: never into the next round), started only when the attack connected
   (RULINGS 18). "Ignores immunity": it orders every hero, immune or not. Ties are broken as usual
   (p.7, the coin). Engine: `Behavior.reversesInitiative`.
2. **[APP] Glitch tokens — "with at least two spaces between each token".** Every two tokens placed
   are 3 or more spaces apart (two spaces lie between them). goa2-backend reads it the same way.
3. **[APP] Glitch tokens — all or nothing.** "Place 2 (3) Glitch tokens" is one step: a space is only
   offered when the rest of the tokens can still be placed, and with no room for all of them nothing
   is placed and the step can't be done — Unstable Timeline's Primary action is not offered (FAQ
   "first instruction"), its Defense text stops (the Defense value still counts), and Flashback/Déjà
   Vu's "You may place" is not offered. Reason: p.10 mandatory steps; placing part of a set is not
   what the text says.
4. **[APP] Glitch supply 3.** The most any card places; our sources do not list the box contents.
   Audit C (2026-10-24) searched the shop, retailer listings, Kickstarter/Gamefound pages and the BGG
   forums: no Glitch count is given anywhere; the only bound is a token-tray maker's "no other hero had
   more than 4 tokens" (BGG thread 3466150; Emmitt is Wayward), so the box holds 3 or 4.
   With too few in the supply Emmitt first takes tokens of the same type from the board, choosing
   which (p.18 "Place").
5. **Unstable Timeline — who chooses.** "An enemy hero in play" is any enemy hero on the board; Emmitt
   chooses which (p.10: you make the choices unless the text gives one to another hero), and that hero
   chooses the Glitch token (any on the board). Choosing is not targeting, so immunity does not stop it
   (goa2-backend agrees). Emmitt then swaps with it (p.19; "cannot be swapped" effects apply). With no
   enemy hero in play the text stops there and its "End of turn" text is never reached (p.10), so the
   tokens stay until the end of round. As a Defense the swap happens during the defense (step 4,
   p.14): the Defense total is compared as usual and minion modifiers were already counted at step 2.
6. **[APP] Temporal Punch / Slam / Judgment.** The defender's total is the printed Initiative of the
   discarded card + Initiative items + modifiers in force, + minion modifiers (the card's reminder);
   "+N Defense" from the Defense card's own text still applies (it is neither "the Defense value of
   their card" nor of their items). A Block card still blocks or fails on its condition (p.14).
7. **[APP] "who remained in the same space since the last turn".** The hero is on the space it held
   when this turn began and was not moved, pushed, placed or swapped during this turn: a hero moved
   away and back did not remain; a hero who was off the board at the start of the turn did not remain.
   Reason: "remained" says it never left. **Disagreement:** goa2-backend compares the two positions
   only (leaving and returning counts as remaining). Engine: Emmitt's hero rule records displacements
   in `TurnRecord.notes`. Known limit: with Razzle, the per-id record does not follow her acting-figure
   bookkeeping (rulings/razzle.md 13).
8. **"who has already resolved a card this turn"** — the hero's card was put on its Turn slot this
   turn (p.7 "Resolve Your Card"), so a hero still to act does not qualify.
9. **Back to the Future — place bullet.** A unit in range (never Emmitt himself: goa2-faq "Can my hero
   target themself? No"), on the board at the start of this turn, not on that space now, and that
   space empty (p.18 "Place"). RULINGS 38: a bullet that can't start is not offered.
10. **[APP] Time Capsule / Future Proof — retrieving.** Emmitt and each friendly hero in radius decides
    for itself ("may" is theirs), all or nothing ("all cards discarded this turn"), only cards still in
    the discard pile. Not offered when nobody has such a card (first instruction). goa2-backend agrees.
    Retrieving a discarded Defense card cancels its Active effect (p.17, card state change).
11. **Future Proof — immunity.** Friendly heroes (never Emmitt, p.17) within Emmitt's radius measured
    from his current space at each check (p.17) are immune to actions of enemy heroes and to their
    Active effects (p.18) this turn.
12. **Time Warp — card bullet.** Emmitt chooses the enemy hero in range (it must have an unresolved card
    and a resolved card); that hero chooses which resolved card. "All their card states are swapped"
    (p.10), so both cards' Active effects end. **[APP]** The swapped-in card is now this turn's played
    card, so an attack card swapped in counts for "played an attack card this turn" (RULINGS 36). The
    hero then resolves it by its own initiative.
13. **Alternative Timelines (Ultimate).** The second card is optional ("Play only one card"); both are
    revealed, so "played an attack card this turn" counts both (RULINGS 36); after the reveal (after
    "Next turn, after playing cards" effects) Emmitt must retrieve one of the two, his choice. The
    Ultimate is in force only while Emmitt is on the board (p.16), so an off-board Emmitt plays one
    card. `HeroView.alsoPlayed` shows the second card; `PropertyTest` allows two played cards for him.
