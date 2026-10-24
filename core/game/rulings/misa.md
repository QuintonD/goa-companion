# Rulings: Misa the Samurai

Format as in [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** = the
card text and FAQ are silent and the engine takes the reading closest to the text. Misa has no FAQ
entries (goa2-faq has no `misa.json`). Code: `heroes/MisaHero.kt`; tests: `heroes/MisaTest.kt`.

1. **"At maximum range" / "at maximum radius".** Exactly the card's Range (Radius) value, items
   included (p.9 "The value on the Range/Radius icon determines the maximum Range/Radius"; p.16 items).
   Watch How I Soar and Leaf on the Wind place Misa into an empty space exactly that far (p.18 Place).
2. **Watch How I Soar — "Defeat a minion adjacent to you".** An enemy minion only ("You can never defeat
   a friendly unit. No exceptions.", p.17) that can be targeted (heavy minion immunity); Misa gains its
   coins (p.13). A bullet that cannot start is not offered (RULINGS 38).
3. **Swoop In.** "Adjacent to two or more enemy units" is presence (p.18: immune units count); the
   retrieve is optional and only after the placement ("if you do").
4. **Challenge Accepted / Matter of Honor / Worthy Opponent.** The "This turn: Gain +N Defense" effect
   starts only after an attack that connected (RULINGS 18) and adds to the Defense value of every card
   Misa defends with this turn.
5. **Power Shot / Thunder Shot.** Power Shot: "was at maximum range" is the distance when the target
   was attacked. Thunder Shot: "is not adjacent" is read after the attack. The move is optional ("you
   may"), 1 space to a space farther from Misa (p.17 "Farther"), only for a target still on the board.
6. **[APP] Focus / Discipline / Mastery — "Before you perform a primary action".** Every primary action
   Misa performs next turn (a repeated instance is a separate action, p.10), including a primary Defense
   action (p.14 calls Defense the Primary action of a Defense card; none of Misa's own cards has one, so
   this matters only if another effect gives her one). The hook runs before the action's text, so a
   Primary action whose first instruction becomes possible only after the move is offered (RULINGS 4,
   like Arien's Living Tsunami). Focus: "you may move 1 space" (exactly 1 or none); Discipline/Mastery:
   "move up to 2 (3)" (0 allowed). Next turn of the same round only (FAQ common).
7. **Living Tornado / Storm Spirit.** Any two units (heroes or minions, either team, never Misa) that can
   be targeted and swapped (RULINGS 17). Storm Spirit: both in radius and the same distance from Misa;
   only units that have a partner at their distance are offered first.
8. **Sudden Breeze / Gust of Wind / Crushing Squall / Dash and Slash / Death from Above.** "Move up to N
   spaces in a straight line, ignoring obstacles" is a text move (p.11) that may pass any obstacle and
   end on an empty space (p.18 "Ignoring Obstacles"). "An enemy unit (hero) you moved through" = a unit
   in a space of the path other than the end, targetable. The placement is optional; the discard clause
   ends with "if able", so with no hero passed nothing happens.
9. **Power Overwhelming (Ultimate) — choosing two.** "Whenever you choose one, you may choose two
   different options instead, in any order": implemented as the `choiceLimit` hook (2 for Misa's printed
   "Choose one"). Watch How I Soar then offers each bullet alone and both bullets in either order; a
   combination is offered when its first bullet can start, and a second bullet that cannot be done stops
   the action (p.10).
10. **[APP] Power Overwhelming — "Each time after you place yourself".** Any placement of Misa by Misa:
    her card texts and Fast Travel ("Fast Travel is a placement, not movement", goa2-faq common, as
    RULINGS 12). Not a respawn (p.7 is a respawn, not a placement), a swap (p.19 "This does not count as
    movement or placement"), or a placement by another hero. Misa chooses an adjacent enemy hero that can
    be targeted; it chooses the card. Test: "Power Overwhelming - APP - Fast Travel is placing yourself".

## Disagreements with goa2-backend (cross-check only)

None found on rulings 1–8 (goa2-backend's Focus family also fires before a primary Defense action).
Power Overwhelming has no script there (`build_steps` returns nothing), so rulings 9–10 could not be
compared.
