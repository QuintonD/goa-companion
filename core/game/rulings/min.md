# Min the Dragonmonk — rulings

Readings of Min's cards (`catalog/heroes/min.json`) beyond the plain text, in the format of
[RULINGS.md](../RULINGS.md). Code: `heroes/MinHero.kt`; tests: `heroes/MinTest.kt`.

1. **Fast as Lightning / Chop Down, "Apply the 'After the attack' text of your resolved or discarded
   red card".** The red card must be faceup (p.10: a facedown card has no colour) and resolved or in the
   discard; with none, nothing follows. Its text is applied as part of the gold action: "the target"
   is the gold card's target, and "(If it has radius, use that card's value)" is the red card's Radius
   icon with Radius items (p.16). It is "After the attack" text, so only when the attack connected
   (RULINGS 18). Tests "Fast as Lightning - …", "Chop Down - …".
2. **[APP] Tiger / Dragon Stance, "move … to a space adjacent to the target".** A target that was
   defeated and removed is no longer on the board, so there is no space adjacent to it and no move
   (the move is optional anyway); the push still follows. goa2-backend reads it the same way (its
   adjacency filter needs the target). "1 or 2 spaces" is an exact distance of 1 or 2 (p.11).
3. **"Push an enemy unit adjacent to you up to 3 spaces"** is optional ("up to", p.10): Min picks an
   enemy unit adjacent if there is one, then a distance 0–3 (as Brogan's Jab).
4. **Smoke bomb: "enemy heroes cannot target you or another unit if the Smoke bomb token is on a
   straight line between that enemy hero and their target"** is a property of the token while it is on
   the board (no Active-effect keyword; p.19 tokens keep their properties). It applies only when the
   hero and the target are in a straight line (p.19) and the token is on a space strictly between
   them; "another unit" is any unit, friend or foe of Min. It forbids targeting without being
   immunity (`forbidsTarget`), so presence checks and moves are unaffected (Brogan FAQ: he may charge
   past it and then target). There is one Smoke bomb ("the Smoke bomb token"): placing it again moves
   it (p.18). Tests "Smoke Bomb - …".
5. **Poof! / Vanish / Ruse.** The swap is mandatory when Min's Smoke bomb is in range (the card's
   Range with items) and he may be swapped; then the Block succeeds, otherwise it fails (p.14). Ruse's
   "You may place the Smoke bomb into a space in range" is a sentence of its own, so it may be done even
   without the swap (from Min's space then). **Disagreement:** goa2-backend allows the placement only
   after a swap.
6. **Mines.** "units may move through them" (passable tokens; the mover chooses the path space by
   space, RULINGS 15); "remove mine tokens after an enemy hero moves through them": any move through
   the token's space, by any means — a Movement action, card text, ignoring obstacles (p.18: "any
   special effects of tokens are still applied") or a push (p.18: a pushed unit is "moved through"
   obstacles that can be moved through; shared change, EFFECTS.md "Min"). Friendly heroes and minions
   pass without effect. Each blast removed makes that hero discard a card if able (it chooses).
   [APP] A hero immune to Min's skill actions still removes the mines but does not discard (p.18:
   immune units are "not affected"; common FAQ: they "can choose to move through and ignore tokens").
7. **Placing mines (Trip Mine family).** All tokens are one mandatory step: with fewer empty spaces in
   radius than tokens, the action cannot be performed. Min picks the spaces, then which of them hold the
   blasts (or the duds, whichever is fewer); the tokens go down in board order and are labelled "Mine",
   so neither the log nor the other team's view tells them apart (p.4 hidden information; the
   determinizer shuffles their kinds). A removed mine is revealed in the log.
8. **Death Grenade / Holy Death Grenade.** The token is placed now; at the end of turn Min chooses up
   to 1 (2) enemy heroes adjacent to the token where it is then (it may have been moved), targetable
   (common FAQ: a hero immune "this turn" is still immune at the end of turn), each discards a card or
   is defeated; then the token is removed. If the token was removed meanwhile nothing happens. If Min
   is defeated first the effect is cancelled (p.17) and the token stays until the end of the round.
9. **[APP] Token supplies.** No source we have counts Min's tokens: one Smoke bomb, one Grenade, two
   blast and two dud mines (the most any card places: Minefield 2 blasts, Cluster Mine 2 duds).
   goa2-backend uses the same numbers.
10. **Inner Strength / Perfect Self, "Double your item bonuses"**: every item counts twice
    (`itemMultiplier`); two such effects do not multiply further. Perfect Self's "Take a Tier II card
    from your deck and add it to your dashboard as a permanent item": a Tier II card still in the deck
    (any colour; not one already an item) becomes an item of its icon; that bullet is not offered when
    there is none. "Choose one, or both" is asked as three options.
11. **Flurry of Blows, "Each time after you perform an attack action, you may repeat it once on a
    different target".** Any attack action (primary or secondary, basic or not), never a repeated
    instance (p.10); "a different target" excludes every unit the action targeted (RULINGS 37), so a
    unit Min pushed with Crane Stance's text is excluded too. Offered only when the repeat has a target.
