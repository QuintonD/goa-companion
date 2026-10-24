# Rulings: Brynn the Seeker

Format as [RULINGS.md](../RULINGS.md): card — question — reading — reason — source. **[APP]** marks a
reading the card text and FAQ leave open. Code: `heroes/BrynnHero.kt`; tests: `heroes/BrynnTest.kt`.

1. **All cards — what is "adjacent to 3 or more obstacles"?** The hero's six neighbouring spaces are
   counted when they hold an obstacle: any unit of either team (Brynn included), a token, terrain
   (rocks printed on the board and the water around it), or another miniature such as Trinkets' Turret.
   Reason: the card's reminder "(You, other heroes, minions, tokens, and terrain are obstacles.)"; p.18
   "Obstacle"; p.19 "Terrain … including the water spaces surrounding the board". **[APP]** Spaces an
   effect makes "count as obstacles" during Brynn's action (Wasp's Static Barrier) count too: they are
   obstacles for that action. `BrynnHero.obstaclesAround`; `BrynnTest` "High Ground - plus 2 attack …
   (Brynn counts)".
2. **All cards — an immune hero (FAQ).** "Whenever you are required to check the status of a unit, it
   counts as targeting, which you cannot do with an immune unit." A hero the action cannot target never
   counts as adjacent to 3 or more obstacles, also in the conditions of True Grit, Die Hard, Mountain
   Guide and Expedition Leader, and also under Over the Top. Source: Brynn FAQ (goa2-faq common).
   `BrynnHero.obstacleBound`; `BrynnTest` "True Grit - FAQ - an immune hero never counts …".
3. **High Ground / Elevated Ambush / Peak Precision / Split Attack / Split Throw — when is the target's
   obstacle count read?** **[APP]** When the target is chosen ("If you target a hero …"), before the
   attack total is announced; nothing moves between the two. `BrynnTest` "High Ground - …".
4. **Familiar Ground / Home Turf — the adjacent target.** The card is Ranged, so both bullets are ranged
   attacks (RULINGS 19). "Choose one —" offers only bullets with a target (RULINGS 38). `BrynnTest`
   "Familiar Ground - …".
5. **Decoy — "Choose up to two times, on different targets".** One action (p.10 "Modal Actions": "Treat
   this as a single action … you can choose the bullet points in any order"); a bullet may be chosen
   twice; the second target must differ from the first; "up to" allows choosing none (p.10: steps with
   "up to" are non-mandatory). Each move is exactly 1 space (p.11 card-text movement). The Primary is
   not offered when nothing can be moved. Other heroes' Ultimates that widen choices are asked
   (`choiceLimit`). `BrynnTest` "Decoy - …".
6. **Peak Precision — "After the attack: You may retrieve this card."** Only after an attack that
   connected (RULINGS 18) on a qualifying hero. The card goes from its played state back to the hand, so
   there is no card to place on the Turn slot this turn (p.7) — **[APP]**, the rulebook does not describe
   a card retrieved while it resolves. `BrynnTest` "Peak Precision - …".
7. **Split Attack — "may repeat once on a different unit adjacent to you".** The repeat is the whole
   action again (p.10) — a ranged attack in range — restricted to units adjacent to Brynn and other
   than the first target. Split Throw's repeat may target any other unit in range. `BrynnTest`
   "Split Attack - …", "Split Throw - …".
8. **Bear Trap / Log Trap / Deadfall Trap.** "Discards a card, if able": a hero with no card discards
   nothing and the action is still performed. "Or is defeated": RULINGS 22. `BrynnTest` "Log Trap - …",
   "Deadfall Trap - …".
9. **Tread Lightly / Cover Tracks / Hide Traces — "a unit adjacent to you".** Any unit of either team
   (heroes and minions), as targeting allows (immunity, heavy minion immunity) and as swap limits allow
   (Bulwark, Magnetic Dagger). Cover Tracks' "You may move 1 space" and Hide Traces' "Move up to 2
   spaces" are card-text movement after the swap. `BrynnTest` "Tread Lightly - …".
10. **True Grit / Die Hard — "a discarded attack card".** The card's type now: a facedown discarded card
    has no type (p.10). **[APP]** The Primary is always available (its first step is optional), as for
    Xargatha's Fresh Converts. `BrynnTest` "True Grit - …".
11. **Mountain Guide / Expedition Leader — "a different friendly unit".** Different from the unit moved by
    the first sentence (any friendly unit in radius when none was moved). Both moves are "up to" N, so
    the unit may stay. `BrynnTest` "Mountain Guide - …".
12. **Over the Top (Ultimate) — "While you are performing actions".** Brynn's own actions: the only texts
    that read the condition are her cards, so the check is in her card scripts (another hero performing
    one of her cards does not get it). "In play" = on the board. Immune enemy heroes still do not count
    (ruling 2). `BrynnTest` "Over the Top - …".

## Cross-check with goa2-backend (`brynn_effects.py`)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| "Choose one —" | Both bullets always offered (a bullet with no target then fails) | Only bullets with a target (RULINGS 38) |
| Peak Precision retrieve | Offered whenever the target qualified | Only when the attack connected (RULINGS 18) |
| Mountain Guide second move | Optional selection | The unit is chosen, its move is "up to" (same outcome) |
