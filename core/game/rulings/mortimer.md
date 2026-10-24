# Rulings: Mortimer the Awakener

Sources in order (RULINGS.md): card text (`catalog/heroes/mortimer.json`), the 2nd-printing
rulebook (printed pages), the FAQ (Mortimer's `faq`, goa2-faq `common.json`). **[APP]** = text and
FAQ are silent; the engine takes the reading closest to the text. Code: `heroes/MortimerHero.kt`;
tests: `heroes/MortimerTest.kt`.

1. **[APP] Zombie token supply.** FAQ (Mortimer): "Can I take Zombie tokens from the board if all
   Zombie tokens are already in play? Yes … first remove up to that number of tokens of the same
   type from the board" (p.18 "Place"). The box's Zombie count is in none of the pinned sources, so
   the supply is not limited (`TokenKind.supply = null`) and nothing is ever taken from the board.
   Gap: with the real count, the placements must first ask which Zombie tokens on the board to take
   (`placeToken(takeFrom)`, p.18 "first remove up to that number of tokens"); not written yet. Test: "Awaken - FAQ - more zombies than the supply (APP - …)".
2. **Zombie tokens stay between rounds.** Card: Awaken! ("Zombie tokens are not removed at the end of
   round"). Reading: a property of every Zombie token, including those placed by Gathering Horde.
   They are not removed when Mortimer is defeated (p.19). Tests: "Awaken - Zombie tokens are not
   removed at the end of round", "Awaken - FAQ - discarded to defend …".
3. **FAQ (Mortimer): "If I cannot defend against an attack, can I still discard this to place the
   Zombie tokens?" — "Yes."** Awaken! is a Defense/Skill card: its text applies when it is discarded
   to defend (p.14), whatever its Defense value. Test: "Awaken - FAQ - discarded to defend even when
   its Defense can't win, the zombies are placed".
4. **Awaken! "into spaces adjacent to you, or into spawn points in radius".** Reading: empty spaces
   next to Mortimer, or empty spawn points (any: hero or minion, either team) 1..Radius away; one at a
   time, up to 4.
5. **[APP] "Choose up to N times —".** Reading: N choices at most, the same bullet again included
   (the cards limit repeats explicitly where they mean to: "once per turn", "each enemy hero can only
   be targeted once"); each time only bullets that can be done are offered; Mortimer may stop at any
   time ("up to"), and the choices stop when no bullet can be done. "Choose one —" is mandatory:
   with no possible bullet the step fails (RULINGS 38). One action (p.10 "Treat this as a single
   action").
6. **[APP] "Move a Zombie token in range up to 1 space" (Corpse Slam family, Robbing Zombies
   family).** Reading: a Zombie token in range must be chosen (the bullet is not offered without one);
   "up to" makes the distance 0 or 1. Reason: same reading as Brogan's "Push an enemy unit or a token
   adjacent to you up to 1 space", which needs a target (BroganHero). So Robbing Zombies' coin needs a
   zombie in range, but not a move. p.10 calls "up to" steps non-mandatory; the step being optional
   would let the coin be gained with no zombie at all, which the engine does not allow. Tests:
   "Robbing Zombies - …".
7. **[APP] "that Zombie token may push a unit or a token adjacent to it 1 space".** Reading: any unit
   or token next to the zombie except the zombie itself and Mortimer (p.10: "If your action is
   affecting you … it will say so"), targeted, not one that can't be pushed by Mortimer; pushed 1
   space directly away from the zombie (p.18), possibly 0. Test: "Corpse Slam - …".
8. **Crawling / Walking / Racing Dead: "Another enemy hero in radius, adjacent to a Zombie token,
   discards a card, if able".** Reading: an enemy hero other than the attack's target, targeted
   (immune heroes can't be chosen), next to a zombie; "each enemy hero can only be targeted once" =
   by this bullet within the action. Only after an attack that connected (RULINGS 18).
9. **Braains…! / Braaaaaaaains…! "If an enemy hero in radius is adjacent to a Zombie token, retrieve a
   discarded card".** Reading: the retrieve is not optional (no "may"), so the bullet is offered only
   when Mortimer has a discarded card and the condition holds. **FAQ (common): "Would an immune hero
   trigger effects, that require adjacency? No."** — an immune enemy hero next to a zombie does not
   count. Tests: "Braains - …", "Braains - FAQ - an immune hero next to a zombie does not count".
10. **Knife of the Living Dead / Shallow Graves.** Reading: the target is chosen first, then the
    choices ("Before the attack"), then the attack with +1 per Zombie token removed next to the
    target. "Before the attack" text happens even when the attack then fails to connect (p.17).
11. **[APP] Gathering Horde / Army of Darkness "Replace an enemy minion … with a Zombie token".**
    Reading: p.19 "Replace" — the minion leaves the board (removed: no coins; a Lane push if it was its
    team's last minion in the Battle Zone, p.8 "If you defeat, or remove") and a Zombie token is placed
    in its space. The minion is targeted (an immune heavy minion can't be replaced); "two or more
    Zombie tokens" adjacent is presence. "Once per turn" is per Mortimer per turn. Tests: "Gathering
    Horde - …", "Army of Darkness - …".
12. **Master of Puppets (Ultimate).** Reading: Mortimer's "Choose up to three times" cards (Knife of
    the Living Dead, Racing Dead, Braaaaaaaains…!, Crowd Surf, Army of Darkness, Macabre Mayhem,
    Stalking Scalpers) allow up to five choices; "Choose one" and "up to two times" are unchanged.
    Tests: "Master of Puppets - …".

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`scripts/mortimer_effects.py`) | Engine |
|-------|-----------------------------------------------|--------|
| The zombie's push | Any unit or token adjacent to the zombie, Mortimer included (`skip_self_filter=True`) | Never Mortimer (p.10, ruling 7) |
| "Move 1 space" bullet | The move may be skipped | Exactly 1 space, mandatory once chosen (p.11 "Movement in the Card Text"); not offered when Mortimer can't move |
| Gathering Horde "Once per turn" | A flag for the current action | Per turn (a second action in the same turn can't replace again) |
