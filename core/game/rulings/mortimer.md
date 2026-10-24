# Rulings: Mortimer the Awakener

Sources in order (RULINGS.md): card text (`catalog/heroes/mortimer.json`), the 2nd-printing
rulebook (printed pages), the FAQ (Mortimer's `faq`, goa2-faq `common.json`). **[APP]** = text and
FAQ are silent; the engine takes the reading closest to the text. Code: `heroes/MortimerHero.kt`;
tests: `heroes/MortimerTest.kt`.

1. **Zombie token supply: 4.** FAQ (Mortimer): "Can I take Zombie tokens from the board if all
   Zombie tokens are already in play? Yes … first remove up to that number of tokens of the same
   type from the board" (p.18 "Place"). Count: at least 4 (Awaken!: "Place up to 4 Zombie tokens"; the
   BGG "Mad_Mike Builds the Awakener" opening, thread 3556825, places four in one action) and at most 4
   (BGG thread 3466150, a token-tray maker who had opened every pack but Renowned — Mortimer is Wayward:
   "no other hero had more than 4 tokens"). Community sources; the dashboard back is not in ours.
   Engine: `supply = 4`. With none left, Awaken! asks which zombie to take from the board first, or to
   stop ("up to 4"), and only offers zombies whose removal leaves a space; Gathering Horde / Army of
   Darkness take one back (Mortimer's choice) before the replacing zombie is placed. Tests: "Awaken -
   FAQ - all four zombies in play …", "Gathering Horde - replace …".
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
6. **"Move a Zombie token in range up to 1 space" (Corpse Slam family, Robbing Zombies family).**
   p.10: "Steps which include 'you may', 'up to' and 'if able' are non-mandatory"; nichik (who posts for
   Wolff Designa, BGG thread 3533578, article 46263552): "'non-mandatory' in this context means your
   action doesn't fizzle if you cannot complete that step". Reading: the distance is 0 or 1 and, with
   no Zombie token in range, the step is skipped and the rest of the bullet still applies. So Robbing
   Zombies / Stalking Scalpers' "Gain 1 coin" (a mandatory step after it) is gained with no zombie in
   range too. Corpse Slam's bullet has nothing left to do without a zombie ("that Zombie token may
   push …"), so it is not offered then (RULINGS 38; choosing it would change nothing).
   **Changed (audit C, 2026-10-24).** Before: a zombie in range was required, by analogy with Brogan's
   "Push an enemy unit or a token adjacent to you up to 1 space" (BroganHero fizzles with no target).
   That analogy does not hold: Brutal Jab's push is its last step, so skipping and fizzling give the
   same result there, while Robbing Zombies has a step after it. Tests: "Robbing Zombies - p10 up to is
   non-mandatory, no zombie in range still gains the coin".
7. **[APP] "that Zombie token may push a unit or a token adjacent to it 1 space".** Reading: any unit
   or token next to the zombie except the zombie itself and Mortimer (p.10: "If your action is
   affecting you … it will say so"), targeted, not one that can't be pushed by Mortimer; pushed 1
   space directly away from the zombie (p.18), possibly 0. Test: "Corpse Slam - …".
8. **Crawling / Walking / Racing Dead: "Another enemy hero in radius, adjacent to a Zombie token,
   discards a card, if able".** Reading: an enemy hero other than the attack's target, targeted
   (immune heroes can't be chosen), next to a zombie; "each enemy hero can only be targeted once" =
   by this bullet within the action. Only after an attack that connected (RULINGS 18). **Razzle FAQ**:
   "Cards such as Mortimer's Crawling Dead (which let you affect 'another enemy hero in radius') … can
   target Razzle twice by targeting two Razzle figures": "another" and "only once" are per figure, so a
   second figure of hers may be chosen (and one other than the attack's target). Test: "Walking Dead -
   Razzle FAQ - …".
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
