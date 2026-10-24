# Rulings: Wuk the Grovekeeper

Sources in order (RULINGS.md): card text (`catalog/heroes/wuk.json`), the 2nd-printing rulebook
(printed pages), the FAQ (Wuk's `faq`, goa2-faq `common.json`). **[APP]** = text and FAQ are
silent; the engine takes the reading closest to the text. Code: `heroes/WukHero.kt`; tests:
`heroes/WukTest.kt`.

1. **[APP] Tree token supply.** Card: Mystic Saplings, March of Nature. Question: how many Tree
   tokens are there (p.18 "Tokens are intentionally limited")? Reading: not limited
   (`TokenKind.supply = null`). Reason: the box's token count is in none of the pinned sources;
   inventing one is worse than not limiting. Needs the real count (box contents) to be exact.
2. **Tree tokens stay between rounds.** Card: Mystic Saplings ("Tree tokens are not removed at the
   end of round"). Reading: a property of every Tree token, including those March of Nature places
   (p.19 "Unless the card text says otherwise, tokens are removed at the end of round"). Test:
   "Mystic Saplings - up to, so Wuk may stop, and Tree tokens are not removed at the end of round".
3. **Choose one with a bullet that can't start.** Cards: Tree Slam, Makeshift Weapon, Nature's
   Protector/Guardian, Into the Canopy, Tree of Plenty. Reading: RULINGS 38 (not offered; one
   possible bullet is taken without a question; none = the mandatory step fails, so the Primary
   action is not offered). Tree Slam's second bullet "can start" when a Tree token is adjacent,
   even with no unit in range: the tree is then removed and the action stops (p.10). Tests: "Tree
   Slam - no adjacent minion and no adjacent tree", "Makeshift Weapon - with no tree adjacent".
4. **Tree Slam / Makeshift Weapon / Nature's cards are ranged on adjacent targets too.** Reading:
   RULINGS 19 (the card is Ranged, so every attack it makes is ranged).
5. **[APP] Nature's Champion "Choose one, or both, on different targets".** Reading: one bullet,
   its attack, then the other bullet if Wuk wants it and it has a target other than the first;
   either order (p.10 "you can choose the bullet points in any order"); both attacks are one action.
   Test: "Nature's Champion - Choose one, or both, on different targets".
6. **Toss Away / Mighty Throw / Monstrous Throw: "a token, or an enemy unit, adjacent to you".**
   Reading: any token (anyone's) or an enemy unit, targeted (immune units, e.g. a heavy minion
   with a friendly non-heavy minion in its Battle Zone, can't be chosen) and placeable by Wuk
   (Magnetic Dagger); into an empty space 1..Range from Wuk other than its own (p.18 "Place": "into
   a different space"). Monstrous Throw's repeat may pick the same object again (no restriction).
7. **Gifts of Nature / Tree of Plenty / Abundance: "Remove a Tree token in radius" is mandatory
   and first**, so without one the Primary action is not offered (FAQ "first instruction").
   **[APP]** "A friendly hero in radius may retrieve": Wuk picks the hero, that hero picks the card
   and may decline (p.10 "Making Choices"); only friendly heroes with a discarded card are offered,
   and a bullet with nothing to retrieve is not offered (both bullets are "may").
8. **[APP] Claim / Assert Dominance.** Question: when is "adjacent to you" checked, and who picks?
   Reading: at the Minion Battle, from Wuk's space then (an Active effect is measured from the
   owner's current space, p.17); Wuk picks up to 1 (2) of the enemy minions adjacent to him in that
   Battle Zone, and may pick none ("up to"). "Regardless of immunity": a heavy minion may be picked
   and no immunity is checked. A minion that does not count can't be removed in that battle and
   still stands in the Battle Zone, so its team has not "lost its last minion" (p.8: no push unless
   none remain). Only minion miniatures are picked: a hero that counts as a heavy minion (Brogan's
   One Man Army) is not, as the card says "enemy minion" and the engine would otherwise depend on
   the order the two effects are asked. Tests: "Claim Dominance - …", "Assert Dominance - …".
9. **[APP] Trample / Angry Stampede "If you move in a straight line: You may ignore obstacles".**
   Reading: Wuk decides before moving whether he moves in a straight line ignoring obstacles (a
   Movement action along one of the six lines, through any obstacles, ending on an empty space) or
   moves normally (a Movement action with no text effect: a straight move that does not ignore
   obstacles can't pass units). "Moved through" = units in the spaces between the start and the end.
   Every enemy hero moved through (not immune to Wuk's Movement action) discards a card or is
   defeated (RULINGS 22), in path order; then Wuk picks up to 1 (2) enemy minions moved through and
   they are defeated (coins to Wuk). Friendly units are never affected. Tests: "Trample - …".
10. **FAQ (Wuk): "When moving through a heavy minion and another one, does the order matter?"** —
    "'Up to two minions' resolves at the same time, so the heavy minion cannot be targeted and will
    not be defeated." The minions are chosen together while the heavy one is still immune. Test:
    "Angry Stampede - FAQ - moving through a heavy and another minion, the heavy stays immune".
11. **Into the Canopy / Treetop Ride.** Reading: "Choose up to two times" allows the same bullet
    twice (the text limits bullets elsewhere with "or both" / "once per turn"); radius is measured
    from Wuk's space at each choice, so after swapping with a tree the radius has moved. Swaps check
    "cannot be swapped" effects (RULINGS 17). Tests: "Treetop Ride - …".
12. **March of Nature "Each time after you resolve a card".** Reading: after each of Wuk's cards is
    placed on its Turn slot (`Trigger.CardResolved`), Hold included, while Wuk is on the board;
    into an empty space 1..Radius (the Ultimate's Radius icon, items included as for High Voltage).

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`scripts/wuk_effects.py`) | Engine |
|-------|------------------------------------------|--------|
| Trample's minions | Picked one at a time, immunity re-checked after each defeat ("defeating a support minion can strip an adjacent heavy's immunity") | Wuk FAQ: chosen at the same time; the heavy stays immune (ruling 10) |
| Claim Dominance | Subtracts min(N, adjacent enemy minions) from that team's count; no choice, and the minions removed afterwards may include the ones that "do not count" | Wuk picks up to N minions; those are left out of the battle, so they can't be removed in it (ruling 8) |
