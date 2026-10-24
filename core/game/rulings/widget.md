# Rulings: Widget the Dragonbonded (with Pyro)

Format as [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** where the
card text, the hero's FAQ (none for Widget) and the rulebook are silent. Code: `heroes/WidgetHero.kt`;
tests: `heroes/WidgetTest.kt`.

1. **Dragon Bond — what Pyro is.** Pyro is one piece of kind `pyro` (supply 1): "Pyro counts as a
   token, but is not removed at the end of round" (card), so it is an obstacle, a Clear action
   (anyone's) removes it, and it is not removed when Widget is defeated (p.19 "Tokens are not removed
   when the hero who placed them is defeated"). It belongs to no team (it is not a unit).
   Tests "Dragon Bond - Pyro stays at the end of round", "… Clear action removes it".
2. **Dragon Bond — placing Pyro while it is in play.** The place bullet takes Pyro from the board
   into a different empty space in radius (p.18 "Place": "from the board or from the supply … into a
   different space"). The "If Pyro is in play" bullet is not offered when Pyro is not on the board,
   and a single possible bullet is not asked (RULINGS 38). Widget chooses who moves first; each move
   is "up to 2" through empty spaces (p.11 movement in the card text).
3. **Breath cards — "in range of Pyro".** The card's Range (items included, p.16) counted from Pyro's
   space; "in a straight line from Pyro" per p.19. No Pyro in play: the first instruction can't be
   done, so the Primary action is not offered (FAQ "first instruction").
4. **Nibble / Gnaw — "in range".** Without "of Pyro", range is counted from Widget (p.9); the minion
   must also be adjacent to Pyro. Gnaw says "a minion", but "You can never defeat a friendly unit. No
   exceptions" (p.17 "Defeat"), so only enemy minions can be chosen. "Remove Pyro" follows the minion
   step. goa2-backend reads both the same way.
5. **Airborne Attack / Assault — order.** The card prints "Before the attack: You may swap with Pyro
   in radius" before "Target a unit adjacent to you", so the optional swap is offered first and the
   target is chosen from the new space. The Primary action is offered when that swap can be offered
   (RULINGS 4). The swap is with Widget only ("You may swap").
6. **[APP] Fight as One / Follow Along — "targeting a different unit".** The skill action performed
   may not target the attacked unit (same exclusion as a repeat "on a different target", RULINGS 37).
   Offered cards: Widget's faceup resolved (Turn slot) skill cards whose action can start without
   that unit (the FAQ "first instruction" probe); it is a Skill action on that card, so Spell Break
   can forbid it. Reason: the closest reading of "different"; a card that could only affect the
   attacked unit would do nothing.
7. **[APP] Drag Off / Carry Away — "either 2 or 3 spaces each, in the same direction".** Pyro and the
   enemy unit each move 2 or 3 spaces (chosen separately: "each"), both in a straight line in the same
   one of the six directions (p.17 "Direction"). Both moves are mandatory; Widget chooses Pyro's end,
   then the unit's; the order is the one that lets both moves happen (the unit first when it stands in
   Pyro's way). No range limit. Carry Away's "ignoring obstacles" applies to both moves (p.18: they may
   pass obstacles but end on an empty space). **Disagreement:** goa2-backend moves both by the same
   offset (one distance for both).
8. **[APP] Dragon Knight — "faceup skill cards".** The revealed played card, and resolved and
   discarded cards that are not facedown (p.10: only resolved or discarded cards can be facedown;
   discarded cards are open information). Not cards in hand (hidden) and not items (p.16: only the item
   section is visible; an item is not an Action card in play). Only cards whose action can start are
   offered. **Disagreement:** goa2-backend offers resolved Turn-slot cards only. "After you perform a
   movement action" means a Movement action (primary or secondary); Fast Travel is not one (goa2-faq
   "Is Fast Travel the same thing as movement? No").
9. **Safe Landing.** The optional 1-space move needs Pyro in range; the swap then needs Pyro in range
   again — moving it out of range makes the mandatory swap impossible and the action stops (p.10).
10. **Diversionary cards.** "Move Pyro up to N spaces" after the attack does nothing when Pyro is not
    in play, and is not applied when the attack failed to connect (RULINGS 18).
