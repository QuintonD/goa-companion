# Rulings: NebKher the Harbinger

Readings of NebKher's cards (`heroes/NebkherHero.kt`, tests in `heroes/NebkherTest.kt`). Sources as
in [RULINGS.md](../RULINGS.md); NebKher's FAQ is in `nebkher.json` (`faq`). **[APP]** marks a reading
where text and FAQ are silent.

## Mind Grip (gold) and Mind Probe (Handicap)

1. **"The card in the previous turn slot of an enemy hero in range"** = that hero's faceup card on
   the Turn slot numbered one less than the current turn (nothing on turn 1). The hero is targeted:
   an immune hero's card can't be used (FAQ "NebKher cannot copy the card of an immune hero").
   A facedown card has no actions (p.10). Tests: `Mind Grip - perform an action …`, `- only the
   previous turn slot …`, `- FAQ, NebKher cannot copy …`.
2. **"Perform an action on the card"**: any action the card offers NebKher now (p.7: Primary,
   Movement, Fast Travel, Attack, Clear), computed as for his own card (the Primary only when its first
   instruction can be done, RULINGS 4); never a Defense (FAQ: "You could not use Mind Grip's copy
   ability to copy a card while defending"). **[APP] Hold is not offered**: holding on another hero's
   card does nothing, and offering it would let the bullet be chosen with no action to take; a card
   offering only Hold makes that hero not a valid choice. The action is NebKher's: the copied card's
   type, range and values (FAQ: a copied non-ranged attack is a non-ranged attack; it is performed with
   the copied card's Attack value). [APP] NebKher's own items count for the copied card's icons (p.16:
   items modify "the value … on a card" the hero uses). Tests: `Mind Grip - FAQ, a copied non-ranged
   attack …`.
3. **"If you would place any tokens this way, place Illusion tokens instead; skip giving markers"**
   (Mind Probe: "do not use markers") is a hero rule of NebKher: every token NebKher places becomes an
   Illusion token (`Behavior.tokenKind`) and no marker he would give is given (new hook
   `Behavior.forbidsMarker`, EFFECTS.md "NebKher"). Reason: NebKher's own cards place only Illusion
   tokens and give no markers, so the rule changes only what he does "this way". FAQ: the Illusions
   "will not take on the properties of the copied tokens". Tests: `Mind Grip - if you would place …`,
   `- skip giving markers`, `Mind Probe - …`.
4. **Copied Active effects are NebKher's** (owner NebKher, the copied card's behaviour): they last
   their timing and are cancelled when NebKher is defeated or at Retrieve; the copied card changing
   state or its hero being defeated does not end them (FAQ). Known limit: the effect is not cancelled
   when NebKher's own Gold changes state mid-round (it is resolved, then retrieved at the end of round,
   so only a card-swapping effect could do that). Test: `Mind Grip - FAQ, copied Active effects …`.
5. **"Defeat a minion adjacent to you"**: an enemy minion (p.13 "You can never defeat a friendly
   unit"); NebKher gains its coins. Test: `Mind Grip - defeat a minion …`.
6. **"Choose one —"** offers only the bullets that can be done (RULINGS 38). Test: `Mind Grip -
   choose one …`.
7. **FAQ notes about other heroes' cards** ("this card" means the printed card; Min's mines, Wuk's
   trees, Trinkets' Turret are used as those heroes would) depend on those heroes' scripts using the
   performing hero (`a.heroId`) for "you" (EFFECTS.md "Known engine limits"). "The Turret" of a
   Trinkets card is Trinkets' one Turret whoever performs it, so NebKher places, removes and attacks
   with hers and never builds a second one. Gydion's cards cast from Gydion's spellbook
   (rulings/gydion.md 9). Tests: `Mind Grip - FAQ, NebKher copies Trinkets' cards …`, `- FAQ, a copied
   Trinkets attack …`, `- a copied Gydion card casts from Gydion's spellbook …`. Snorri's rune cards read
   Snorri's runes when NebKher performs them (rulings/snorri.md 16). The FAQ line on Ignatia's
   Equilibrium describes another printing's card (rulings/ignatia.md 4).

## Diabolical Laughter (silver) and What the Hell Are You? (Ultimate)

8. **"Laugh diabolically; if you do, choose up to three times —"**: the laugh always happens (FAQ: no
   need to really laugh), so the Primary action is always possible. Then up to three choices, one at
   a time, the same bullet allowed again, only possible bullets offered, Stop at any time.
   Tests: `Diabolical Laughter - …` (three).
9. **"Swap two resolved cards of an enemy hero in radius, without canceling active effects"**: NebKher
   picks the hero and two of its Turn slots (chosen by slot, so a facedown card stays hidden); the two
   cards swap slots and keep their Active effects, and each keeps its own face: a facedown card stays
   facedown and the log does not name it (RULINGS 51; before the audit the facedown state stayed with
   the slot, which revealed the card). Test: `Diabolical Laughter - swap two resolved
   cards …`.
10. **Ultimate**: right after the laugh (before the choices), every enemy hero in radius 5 (the
    Ultimate's Radius, items included) that can be targeted discards a card, or is defeated (RULINGS
    22), in seat order; coins for a defeat go to NebKher. Test: `What the Hell Are You - …`.

## Attacks

11. **Phantasmal Sentry**: "a hero in range adjacent to an Illusion token in range" — the token's
    adjacency is a presence check. Test: `Phantasmal Sentry - …`.
12. **Phantasmal Warrior / Champion**: "Before the attack: Move an Illusion token in range up to N
    spaces to a space adjacent to an enemy hero in range. Target that hero." NebKher picks the token and
    the end space (0 spaces when it already is next to such a hero); the move is normal movement of the
    token (obstacles block). Then the target is an enemy hero in range adjacent to the token's space.
    The bullet is offered only when some token can end next to an enemy hero that can be attacked.
    Tests: `Phantasmal Warrior - …`, `Phantasmal Champion - …`.
13. **Twist Fate / Devious Scheme**: after a connected attack (RULINGS 18), NebKher may swap an enemy
    unit in range (not only the target) with an Illusion token adjacent to him / in range; both are
    targeted; swap limits apply (Bulwark, Magnetic Dagger). Tests: `Twist Fate - …`, `Devious Scheme - …`.

## Illusions

14. **Placing**: "in radius" = empty spaces 1..Radius from NebKher. Fleeting Image's single token is
    mandatory; "up to N" may stop at any time. "You may swap with an Illusion token in play": any
    Illusion token on the board. **[APP] Illusion tokens have no known supply limit** (the box
    contents are not in the pinned sources; `TokenKind.supply = null`). Tests: `Fleeting Image - …`,
    `Multiple Projections and Master of Illusions - …`.
15. **Illusionary Force / Army** ("This round: While you are performing actions, all Illusion tokens
    count as both tokens and friendly melee minions"): while NebKher is the acting hero, Illusions are
    friendly melee minions of his team for minion defense modifiers (p.14: so an enemy hero next to one
    gets −1 against his attack) and for heavy minion immunity (p.13). They are never units, so they
    can't be attacked or moved as minions, and checks other heroes' scripts make with their own minion
    test do not see them (engine limit). Not while an enemy hero acts, including when it attacks
    NebKher (the modifiers are computed for the attacker, p.14 step 2). Tests: `Illusionary Force -
    …`, `Illusionary Army - …`.

## Blue cards

16. **Imbue Doubt / Time to Reconsider / An Illusion of Choice**: "Name a color" — one of gold, silver,
    red, green, blue (the colours a card in hand can have). "Next turn, after playing cards": after the
    reveal of the next turn (p.18), from NebKher's space then; NebKher chooses the enemy hero(es) in
    radius (immune ones can't be chosen); each discards a card of that colour, if able. An Illusion of
    Choice: up to two different heroes. FAQ: an effect made on turn 4 does not carry over. Tests:
    `Imbue Doubt - …` (two), `Time to Reconsider - …`, `An Illusion of Choice - …`.
17. **Crack in Reality / Shift Reality**: NebKher picks one of the three straight lines through his
    space; the line is fixed for the turn. While a unit off the line performs an action, spaces and
    objects on the other side are as if they did not exist for it: they are obstacles to its movement
    and placement and their units can't be targeted (`Behavior.forbidsInteraction`). FAQ: "Units on the
    line can affect and be affected by units on both sides." Shift Reality adds: units off the line
    cannot interact with NebKher, wherever he is now. Known limit: radius-based Active effects already in
    force (e.g. a Static Barrier) and presence checks are not cut by the line; only targeting, movement,
    placement and obstacles are. Tests: `Crack in Reality - …` (two), `Shift Reality - …`.

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`nebkher_effects.py`, `docs/heroes/nebkher.md`) | Engine |
|-------|-----------------------------------------------------------------|--------|
| Crack in Reality scope | Distance between the sides is infinite for every query (radius effects too) | Only targeting, movement, placement and obstacles (ruling 17, engine hook scope) |
