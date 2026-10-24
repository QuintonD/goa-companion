# Tali the Spiritcaller — rulings

Readings of Tali's cards (`catalog/heroes/tali.json`) beyond the plain text, in the format of
[RULINGS.md](../RULINGS.md). Code: `heroes/TaliHero.kt`; tests: `heroes/TaliTest.kt`.

1. **Ice tokens: "Enemy heroes have -1 Initiative for every Ice token they are adjacent to"** is a
   property of the tokens, not an Active effect (no Active-effect keyword; p.19 "Once a token is placed,
   it retains all its properties until that token is removed (this does not include Active effects)").
   So it works whichever card placed them and after that card changes state, for every Ice token of
   Tali on the board, and initiative is read again before each hero acts (p.7). Enemies = Tali's
   enemies; friendly heroes are unaffected. Tests "Glacial Barrier - …", "Wall of Frost - …".
2. **Totems keep the rules of the card that placed them** (p.19, same sentence): an Ancestral Totem
   protects friendly *melee* minions; a Venerated Totem protects any friendly minion and is immune to
   enemy actions. "Would be defeated" is any defeat (attack, card text), never a Minion Battle removal;
   it is automatic. FAQ: no minion is defeated, so no coins and nothing that follows a defeat (Brogan's
   Onslaught does not move in). Tests "Ancestral Totem - …", "Venerated Totem - …".
3. **Venerated Totem, "immune to enemy actions"** includes the Clear action (p.18 "Immune": "not
   affected by your actions"); the engine's Clear now offers only tokens the hero may target (shared
   change, EFFECTS.md "Tali").
4. **[APP] Cold Snap family, "Move each enemy unit in radius 1 space in that direction, if able".** All
   units move "at once": a unit blocked only by another enemy unit that moves too still moves (the
   engine moves units until no more can). Each moves only if the next space is empty (p.11); immune
   units (e.g. guarded heavy minions) and units that may not be moved by Tali stay. The direction is
   always chosen, so the action is always possible. Test "Cold Snap - …".
5. **Commune with Spirits.** "A friendly hero in play" is a friendly hero on the board (FAQ: "her
   whole team is dead or immune … No friendly hero can be chosen and the card does nothing"); Tali
   chooses the friend, the friend names the colour (it answers the decision; open table talk, FAQ, is
   outside the engine). [APP] The colours offered are the five Action card colours (gold, silver, red,
   green, blue); the Ultimate is never in hand or discard. The discard is optional; performing a card
   of that colour from the discard is mandatory, and only cards whose Primary action can be performed
   are offered (faceup, not a Defense card, first instruction possible). Tests "Commune with Spirits - …".
6. **"If this card is (not) in the discard"** (Winter cards, Spirit Wolf/Bear, Blizzard, Guardian and
   Warrior Spirit) is read when the clause is applied: a card performed from the discard through
   Commune with Spirits is in the discard. Blizzard's End of turn repeat is a repeated instance (p.10:
   it cannot repeat again).
7. **[APP] Token supplies.** No source we have counts Tali's tokens; the engine uses the most any card
   places at once: 3 Ice tokens (Pack Ice), 1 Totem ("a Totem token"). With the supply used up, p.18
   applies: one of Tali's tokens of that kind is removed from the board first (never one placed by the
   same action). goa2-backend uses the same numbers.
8. **Spirit Bear, "Choose one, or both, on different targets".** Tali chooses up front: either bullet
   alone, or both in either order (p.10: "you can choose the bullet points in any order"); the second
   attack may not target the first one's target. Both attacks are ranged (the card is, RULINGS 19).
9. **Ice Blast / Ice Shard:** "if the target discards a card to defend" holds even when that defense
   fails. The other enemy hero is chosen by Tali among enemy heroes in radius other than the target and
   discards a card of that colour if it has one (p.10 "if able"). With no such hero nothing happens.
10. **Reign of Winter, "with your basic attack":** an Attack action on a basic card (p.9), the gold
    card performed from the discard through Commune with Spirits included. Tali chooses the hero, then
    the colour. The radius is the Ultimate's (4, Radius items included as for High Voltage).
11. **Guardian Spirit / Warrior Spirit:** "Up to 1 friendly hero" may be no one; only friends with a
    discarded card are offered. Guardian Spirit's "retrieve it" is mandatory when it is in the discard.
