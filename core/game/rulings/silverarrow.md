# Silverarrow the Pathfinder — rulings

Sources as in [RULINGS.md](../RULINGS.md): card text (`catalog/heroes/silverarrow.json`), the pinned
rulebook (printed pages), the hero's FAQ and goa2-faq `common.json`. **[APP]** marks a reading the text
and FAQ leave open. Code: `heroes/SilverarrowHero.kt`; tests: `heroes/SilverarrowTest.kt`.

1. **"At maximum range"** (Shoot and Scoot, Keep the Distance, Long Shot, Rain of Arrows).
   *Reading:* exactly as many spaces away as the card's Range value, Range items included; nearer units
   are not targets. *Reason:* p.9 "The value on the Range/Radius icon determines the maximum Range" and
   p.16 items change that value. *Source:* p.9, p.16. Tests "Shoot and Scoot - Target a unit at maximum
   range", "a Range item moves the maximum range".
2. **Shoot and Scoot's fast travel is not a Movement action.** *Reading:* it follows the Fast Travel
   restrictions (no enemy unit in the Zone left or entered, empty destination), "to an adjacent zone" means
   a space of a Zone adjacent to hers (not her own Zone), it is optional, and it is offered only when the
   attack connected (RULINGS 18). Effects that forbid fast travel ("cannot fast travel": Deluge, Grasping
   Roots) or placing her stop it; effects that forbid movement actions (Stone Gaze) do not. *Source:* hero
   FAQ "When can Silverarrow's Gold Fast Travel?" ("a rare instance of a Fast Travel which is not
   replacing a movement action. Silverarrow could Fast Travel (if valid), even if she cannot move due to
   another effect"); p.11 "If a card text allows you to Fast Travel as part of another action, this Fast
   Travel is subject to the same restrictions". Tests "Shoot and Scoot - After the attack…", "FAQ - she
   may fast travel even when she cannot perform movement actions", "an effect that forbids fast travel
   stops it".
3. **Trailblazer's "You may fast travel, if able"** is the same text fast travel (her own Zone or an
   adjacent one). Its This round effect lets the covered heroes ignore obstacles on **Movement actions**
   only (not card-text moves, not Fast Travel: hero FAQ "ignoring obstacles has no effect on zone
   adjacency"); whether a friendly hero is in radius is read when its movement starts (p.11, like the
   Grasping Roots FAQ). Never ending on an obstacle (common FAQ "What are Obstacles?"). Tests "Trailblazer - …".
4. **Snap Shot "not adjacent to any other unit"** is a presence check: any unit counts, Silverarrow
   herself and immune units included, so a unit adjacent to her is only a target of the second bullet.
   Both bullets are ranged attacks (RULINGS 19). *Source:* p.18 presence checks; p.10 "Choose one".
5. **[APP] Disorient / Divert Attention / Lead Astray need a movable target.** The unit to move is
   mandatory ("Move an enemy unit adjacent to you…"): with no adjacent enemy unit that can be targeted and
   moved at least 1 space, the action cannot start (FAQ "Can I play an action if only some of its
   instructions can be performed?"). "Up to N" lets her move it 0 spaces; then "if you do" fails and she
   does not move.
6. **"Move up to that number of spaces"** (Divert Attention, Lead Astray) is the number of spaces the
   enemy unit was moved (its path), and her move is a card-text move in a straight line.
7. **Fae Healing / Nature's Blessing:** Silverarrow picks any hero in radius, friend or enemy, never
   herself (common FAQ "Can my hero target themself"); that hero decides whether to retrieve and which
   card (p.10 "Making Choices"); coins only if they did.
8. **Warning Shot / Treetop Sentinel** resolve at the end of the turn from her space then; Silverarrow
   picks the hero (p.10 example "An enemy hero in radius discards a card, if able"), immune heroes cannot
   be picked (common FAQ on end-of-turn immunity). With no enemy hero in radius nothing happens.
9. **Rain of Arrows:** "If you target a hero, repeat once on a different hero" is mandatory (no "may");
   it happens when the first target was a hero. With no different hero at maximum range the repeat has no
   target, and "if you do, may repeat once on a minion" is then not offered. The repeats are actions of
   their own (p.10), so Wild Hunt may move her before each.
10. **Wild Hunt "Each time before you perform an action"** applies before every action she performs:
    Primary, Movement, Fast Travel, Clear, Hold, each repeat, and the **Defense action** (p.14: "you may
    reveal exactly one card from your hand and discard it, to perform its Defense action"; the move comes
    after the card is discarded, before its text and the total; minion modifiers were fixed at step 2).
    The move is optional, exactly 2 spaces in a straight line (no "up to"), and a card-text move (not a
    Movement action: movement limits do not apply). Like Arien's Ultimate (RULINGS 4), the move counts for
    whether a Primary action is possible.

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| Shoot and Scoot fast travel under a movement-action ban | A movement prevention effect blocks it (`tests/engine/test_silverarrow_pr2.py` "test_movement_prevention_blocks_card_text_fast_travel") | Not blocked: hero FAQ "even if she cannot move due to another effect" (ruling 2) |
| Divert Attention / Lead Astray: "that number of spaces" | The distance between the enemy unit's start and end spaces | The spaces it was moved (ruling 6); equal unless the path bends |
| Warning Shot / Treetop Sentinel: choosing the hero | Optional | Mandatory when an enemy hero is in radius (p.10 example; "if able" is about the discard) |
| Wild Hunt before a Defense action | Fires on the defender's BEFORE_ACTION passives | Same (ruling 10) |
