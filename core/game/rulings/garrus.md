# Rulings: Garrus the Gladiator

Format as in [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** = the
card text and FAQ are silent and the engine takes the reading closest to the text. Code:
`heroes/GarrusHero.kt`; tests: `heroes/GarrusTest.kt`.

1. **Angry Strike / Angry Swipe.** "+1 Attack for every card in your discard" is card-text Attack (items
   never change it, p.16), counted when the attack is made.
2. **Chilling Howl.** The discard is optional and from Garrus's resolved cards (Turn slots); the silver
   card itself is still unresolved while it acts, so it cannot discard itself. The This round effect:
   enemy heroes in radius (from Garrus's current space, p.17) cannot Fast Travel and their Movement
   actions are capped at 2, checked at the start of the Movement action (FAQ "Does Garrus's Silver
   (Chilling Howl) slow an enemy moving into radius from outside?": no). Card-text moves are not
   Movement actions and are not capped (p.11). Heroes immune to his skill actions are not affected
   (p.18). Tests: "Chilling Howl", "Chilling Howl - FAQ".
3. **Trace / Chase / Hunt Down — the first bullet.** "If you have one or more cards in the discard, you
   may move 1 space (move up to N spaces). Target a hero adjacent to you." The bullet is offered when an
   enemy hero is adjacent now, or (with a discard) after a move that the text allows (RULINGS 38: a bullet
   that cannot start is not offered). Without a discard there is no move. After the move the hero target
   is mandatory: none, and the action stops (p.10). Chase / Hunt Down "move up to N" may be 0.
4. **Blunt Force / Send Flying.** After a connected attack (RULINGS 18): "You may move 1 space (up to 2
   spaces) to a space adjacent to an enemy hero" — any enemy hero, the attacked one or another; if no such
   end space exists there is no move at all (FAQ "Can Garrus's Alt Reds (Blunt Force) move away from the
   attacked hero to another?"). "Push that hero 3 spaces, ignoring obstacles": one enemy hero adjacent
   after the move, chosen by Garrus (FAQ: "you can still only choose one of them"), pushed straight away
   from him (p.18; it passes obstacles to the farthest empty space within 3). **[APP]** Send Flying's
   "up to 2 spaces … if you do" needs a move of at least 1 space: staying is not "moving". Tests:
   "Blunt Force - FAQ" (two tests), "Send Flying".
5. **Hold Ground / Make a Stand / Battle Ready.** "At least two enemy heroes in radius" is presence
   (p.18). Otherwise the first instruction cannot be done and the Primary action is not offered.
6. **[APP] Light Pilum / Heavy Pilum — no enemy hero in range.** "An enemy hero in range discards a card"
   is the first instruction and needs a valid target; without one the action cannot be performed at all
   (FAQ common "Can I play an action if only some of its instructions can be performed?": "If you cannot
   perform the first instruction (e.g., there is no valid target in range…), you may not perform the
   action at all"), even though Light Pilum's clause ends with "if able". p.10's own example of such a
   clause reads it so: "'An enemy hero in radius discards a card, if able'. You choose one enemy hero in
   radius to target, then that hero chooses a card to discard, if they have one" — the target is chosen,
   "if able" covers the discard. (A reviewer noted p.17 "If Able" could also be read as making the whole
   clause skippable, so that Light Pilum with no hero in range would still give its move; goa2-backend
   reads it that way. The same pattern decides whether Sabina's Close Support and Dodger's Death Trap
   are offered without a target.) A hero with an empty hand may be chosen: Light Pilum then does
   nothing to it, Heavy Pilum defeats it (RULINGS 22). The move comes after in both cases.
7. **Menace / Threaten / Terrify, Form Up! / Testudo!.** "Farther" / "closer" are measured as p.17 says
   (spaces on the shortest path, obstacles included) from Garrus's space; the end space must be empty.
   Each repeat (p.10) chooses again among all valid units, the same one included. Form Up! moves friendly
   units only (heroes or minions, never Garrus, p.17 "Friendly").
8. **Battle Fury (Ultimate).** "Each time after one of your resolved cards is discarded" = a card leaves a
   Turn slot for the discard pile (Chilling Howl, or any effect that discards a resolved card); a card
   discarded from the hand (defending, "discard a card") does not count. Garrus may then perform that
   card's primary action (asked only when it can be performed, RULINGS 4); a Defense card has none. It is
   a separate action performed inside the action that discarded the card, which then goes on.

## Disagreements with goa2-backend (cross-check only)

| Card | goa2-backend | Engine |
|------|--------------|--------|
| Light Pilum / Heavy Pilum | The enemy hero is chosen optionally (`is_mandatory=False`): the discard may be skipped and Garrus only moves, even with an enemy hero in range | The first instruction is mandatory (no "you may"); with no enemy hero in range the action is not offered (ruling 6, FAQ common) |
| Trace / Chase / Hunt Down | Both bullets always offered; the charge bullet then fails without a hero | A bullet that cannot start is not offered (RULINGS 38); same outcome otherwise |

Same as goa2-backend: Send Flying's move must end on another space (its destination filter excludes the
occupied start), Battle Fury fires for every resolved card discarded (`uses_per_turn=0`).
