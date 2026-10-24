# Bain the Bounty Hunter — rulings

Sources as in [RULINGS.md](../RULINGS.md): card text (`catalog/heroes/bain.json`), the pinned rulebook
(printed pages), the hero's FAQ and goa2-faq `common.json`. **[APP]** marks a reading the text and FAQ
leave open. Code: `heroes/BainHero.kt`; tests: `heroes/BainTest.kt`.

1. **The Bounty marker** (`marker_bounty`) is one marker of Bain's: giving it moves it (p.18 "Markers").
   "A hero with the Bounty marker spends 1 additional Life counter when defeated", whoever defeats it,
   Bain included when he holds it (Close Call: "The marker's effect is applied to you"). It is returned
   when its holder is defeated and at the end of the round, not when Bain is defeated. *Source:* hero FAQ
   "What does a Bounty Marker … do?" ("If Bain's enemy is defeated that round, they spend an additional
   life counter (and return the Marker). At the end of the round, the Bounty Marker is returned. If Bain
   is defeated, the Marker is not returned."). *Engine:* the extra Life cost is read at the defeat, before
   the defeated hero's markers are returned (card text over the p.13 order, p.3; `Units.defeatHero`).
   Tests "Bounty marker - …".
2. **[APP] The hero already holding the marker cannot be given it again** (Dead or Alive, Side Quest leave
   it out of the options). *Reason:* it already has the one marker, so nothing is given; otherwise A
   Complicated Profession could make the same holder discard each time.
3. **Side Quest:** "If you are adjacent to an enemy unit" is a presence check (immune units count, p.18);
   "a hero in radius" is any hero, friend or enemy, never Bain (p.10). Not adjacent: nothing happens.
4. **Get over here!:** "no obstacles between you and the target" — the spaces strictly between are free of
   units, tokens, terrain and spaces that count as obstacles. An adjacent target is valid and stays where it
   is. The pull is card-text movement of exactly the distance − 1 along that line. **[APP]** A unit or token
   Bain may not move (Bulwark, "cannot be moved by enemy heroes") is not offered as a target, since the
   mandatory move could not be made.
5. **Light Crossbow / Heavy Crossbow / Arbalest:** "no units or terrain between you" — tokens between do
   not block (they are neither, p.19); adjacent units are always in a straight line (p.19).
6. **Close Call / Narrow Escape / Perfect Getaway** are Block cards (p.14): they block exactly when some
   hero holds the Bounty marker ("a hero in play"), else Bain is defeated. Close Call: the holder gives the
   marker to Bain (if Bain holds it, it stays); this is not Bain giving it, so A Complicated Profession
   does not apply. Narrow Escape: the marker goes back to Bain's supply.
7. **A Game of Chance / Dead Man's Hand / We're Not Done Yet!:** Bain picks an enemy hero in radius
   (targetable) with two or more cards in hand; that hero picks the card (p.10 "Making Choices"); Bain
   guesses a colour, the card is revealed; correct: that hero discards that card; wrong: the coins.
   "You can only guess colors that could be in that player's hand": the colours of that hero's cards this
   round that are in hand or facedown elsewhere (faceup played, resolved and discarded cards are known not
   to be in hand; facedown ones hide their colour, p.10). We're Not Done Yet!: after a wrong guess Bain
   chooses to repeat (the entire action, p.10) or to gain 2 coins; the repeated instance cannot repeat, so a
   wrong guess there gains the 2 coins.
8. **Hand Crossbow / Hunter-Seeker:** "Choose one" (RULINGS 38: a bullet with no target is not offered).
   Hunter-Seeker "Choose one, or both, on different targets": the choice is made first, the two bullets in
   either order (p.10 "you can choose the bullet points in any order"), one action; "both" is offered only
   when two different units can be targeted. Both are ranged attacks (RULINGS 19).
9. **Vantage Point / High Ground:** a Movement action ignoring obstacles (never ending on one), +1/+2
   Movement while any hero holds the Bounty marker; for this action only (it has no "This round").
10. **Drinking Buddies / Another One!:** Bain may pick a hero in radius (never himself, p.10), friend or
    enemy; once picked, that hero retrieves a card of its choice (the "may" is Bain's). **[APP]** Heroes with
    an empty discard are not offered (picking one would do nothing: "If they do" fails). Another One!'s
    "End of turn: May repeat once" is an End of turn effect started by the original instance: Bain may
    perform the whole action again; the repeat starts no new effect (p.10). Cancelled if Bain is defeated
    before the end of the turn (p.17).
11. **A Complicated Profession:** after Bain gives a hero the Bounty marker (Dead or Alive, Side Quest —
    a friendly hero too), that hero discards a card, if able. Not when a hero gives it to Bain.

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| A Complicated Profession | Fires on any marker placed on a hero while Bain acts (no check that it is the Bounty marker, `bain_effects.py`) | Only the Bounty marker (the card names it) |
| Hunter-Seeker order | The first bullet chosen is mandatory, the other then offered as optional | Both chosen up front with the order (p.10); same results except that the second bullet must then be performed |
