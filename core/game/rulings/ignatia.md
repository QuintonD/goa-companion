# Rulings: Ignatia the Mad

Readings of Ignatia's cards (`heroes/IgnatiaHero.kt`, tests in `heroes/IgnatiaTest.kt`). Sources as
in [RULINGS.md](../RULINGS.md). Ignatia's catalog entry has no FAQ rulings. **[APP]** marks a reading
where text and FAQ are silent.

1. **Which text is "blue" and which "orange".** `{tiebreaker_blue}` is the coin's blue star side,
   the Blue team's symbol; `{tiebreaker_orange}` the orange gear side, the Red team's (rulebook p.5,
   "Tie Breaker coin" image, page image `p6L.png`; p.7 "the team whose symbol is currently revealed on
   the coin"). The engine's `state.tieBreaker == BLUE` applies the blue text. Chaos Bolt's reminder:
   "Apply the text matching the symbol on the Tie Breaker coin." The coin is read when the action is
   performed. Tests: every coin test sets `tieBreaker`.
2. **Loosely-Aimed Firebolts "May repeat once on a different hero"** belongs to the orange text: the
   card scan (`official/cards/ignatia-red-3a.webp`) prints it inside the orange box, under the
   separator; Imminent Eruption's "May repeat once on a minion" inside the blue box. "On a different
   hero": the repeat must target a hero not targeted before (the first target may be a minion).
   Repeats are optional (p.10) and offered only when such a target exists. Tests: `Loosely-Aimed
   Firebolts - …`, `Imminent Eruption - …`.
3. **Equilibrium** starts a This round effect; while it is in force, each Primary action or repeat of
   the hero who performed it asks which text to apply (once per action or repeat). [APP] A text whose
   first instruction can't be done is not offered (RULINGS 38); if only one can be done it applies
   without a question; if neither, the coin's applies and stops. Reason: "you may apply either" is a
   free choice; offering a text that can't start would only make the action fizzle. Tests:
   `Equilibrium - …` (three).
4. **The NebKher FAQ on Equilibrium is out of date.** goa2-faq (NebKher's Mind Grip): "If NebKher
   copies Ignatia's Silver (Equilibrium), he will re-set the coin to a side of his choice. (Ignatia
   only prevents ties from changing it.)" That describes another printing's Equilibrium (setting the
   coin); the 2nd-printing card prints the This round choice above, so card text wins (p.3). NebKher
   copying it gets the same choice for his own actions (it only matters for coin texts he performs).
5. **Chaos Bolt / Unreliable Jolt / the red cards**: "in range" = 1..Range spaces (p.9); "at maximum
   range" = exactly the card's Range (items included, p.16); "in a straight line" as p.19 (adjacent
   units are in a straight line). The cards are Ranged, so their attacks are ranged even on adjacent
   targets (RULINGS 19).
6. **Path of Ashes / Cinders / Flames, blue**: "Move up to N spaces in a straight line" (0 allowed;
   p.11 card-text movement). "Each empty space you moved through, or out of" = the starting space and
   every space of the path except the last one; a move of 0 places nothing. **Orange**: "Place up to
   N Magma tokens in radius": one at a time into empty spaces 1..Radius away, stopping at will.
   **[APP] Magma tokens have no rule of their own** (no text or rulebook entry beyond being tokens:
   obstacles, removed at the end of round, p.19) and **no known supply limit** (the box contents are
   not in the pinned sources; `TokenKind.supply = null`). Tests: `Path of …` (three).
7. **Abrupt Combustion / Spontaneous Immolation / Violent Conflagration**: "adjacent to a token or to a
   minion" and "adjacent to an enemy hero" are presence checks (p.19 "Target": they do not target),
   so an immune unit next to the hero counts; the hero who discards and the minion removed are
   targeted (immunity, heavy minion immunity apply). "Remove" gives no coins (p.19); "Defeat" does
   (p.13). Ignatia chooses the hero or minion. Tests: `Abrupt Combustion - …`, `Spontaneous
   Immolation - …`, `Violent Conflagration - …`.
8. **Unstable Portal / Chaos Gate**: swap with a unit in radius that can be swapped (Bulwark, Magnetic
   Dagger respected). Chaos Gate blue "You may move that unit 1 space" moves the swapped unit (Ignatia
   chooses where); orange "You may move 1 space" moves Ignatia. Tests: `Unstable Portal - …`,
   `Chaos Gate - …`.
9. **Searing Heat / Scorching Blaze**: the move is mandatory and exact (2, or 2 or 3, p.11); only heroes
   that can make it are offered; Ignatia chooses the hero and the space. Tests: `Searing Heat - …`,
   `Scorching Blaze - …`.
10. **Chaos Incarnate (Ultimate).** "The first time each turn after you perform a primary action": the
    first Primary action Ignatia completes in a turn (a repeat is part of the action it repeats, so it
    reacts after the whole action). [APP] That first time is used even if she declines to flip:
    "the first time" names one moment (unlike "once per turn", RULINGS 39). "You may flip the Tie
    Breaker coin; if you do, you may perform that action again": flipping alone is allowed; the second
    performance reads the flipped coin (so the other text applies unless Equilibrium lets her pick)
    and is not a repeat (its own "may repeat" works). **[APP] "Choosing different targets"**: every
    unit and token the first performance (and its repeats) targeted is excluded; spaces are not
    targets (p.19 "Target" speaks of units), so a Path can be performed again freely. The flip by
    Chaos Incarnate is independent of the tie flip of p.7 (both happen if both apply). It plays no part
    in deciding whether a Primary action is possible (RULINGS 4 probes the action's own text). Tests:
    `Chaos Incarnate - …` (three).

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`ignatia_effects.py`) | Engine |
|-------|--------------------------------------|--------|
| Chaos Incarnate flip | One optional step: flip and perform again together | Two choices: flip, then (only if flipped) perform again (card: "you may flip …; if you do, you may perform …") |
| Equilibrium sides | Both sides always offered | Only sides whose first instruction can be done (ruling 3) |
