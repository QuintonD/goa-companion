# Rulings of the rules engine

Sources, in order: card text (p.3: "If a card's text directly contradicts the rules, the card's
text takes precedence"), the pinned 2nd-printing rulebook (printed pages, `PDF PAGE n (printed
n-1)` in `D:\.goa-qa\rulebook-text\rulebook.txt`, page images in `D:\.goa-qa\rules\img`), the FAQ
rulings (each hero's `faq`, and goa2-faq `common.json`). Where they are silent the engine takes
the reading closest to the text, marked **[APP]** with the reason. The community engine
`goa2-backend` is a cross-check only; disagreements are listed at the end.

Each ruling names the code and the test that pins it.

## Turn and initiative

1. **Coin flip timing (p.7).** After a cross-team tie the coin's team acts first and that hero
   flips the coin *after resolving the card* ("After that player has resolved their card, that
   player (and only that player!) must flip"). A card read during that action sees the unflipped
   coin. `Flow.resolveNext`; `TurnTest` "tied initiative across teams".
2. **Several heroes of the coin's team tied (p.7).** "One of the tied players on the team whose
   symbol is currently revealed on the coin acts first": that team picks (`ACT_FIRST`) and the
   picked hero flips. Remaining ties are checked again after each resolution.
3. **[APP] `CHOOSE_ACTION` is always asked**, even when Hold is the only action: the player sees
   that the card does nothing rather than having the engine skip it. `PLAY_CARD` with an empty
   hand asks `[Pass]` (p.7 "you must announce that you are passing").
4. **Primary action availability (FAQ "Can I play an action if only some of its instructions can
   be performed?").** The Primary action is offered only when its first instruction can be done.
   The engine probes it on a copy: possible when the text reaches a real choice or changes the
   board before stopping; not possible when it stops before doing anything. Hooks that run
   *before* the action (Arien's Ultimate move) count, so a hero 2 spaces from every target may
   still choose an attack with Living Tsunami. A text that starts and then stops at a mandatory
   step (p.10) still counts as performed: "after the action" triggers fire. `Actions.primaryPossible`.
5. **A played Defense card** offers its secondary actions and Hold only (p.14 "You cannot perform
   a Defense action on an already played card"); a played Defense/Skill card is a Skill (p.14).
6. **Clear** is offered on a card with an Attack action (primary or secondary) when at least one
   token is adjacent; "remove any number" is asked token by token with a Stop option (p.13).
7. **Off-board heroes** are asked `RESPAWN` (empty hero spawn points; if none, the nearest empty
   spaces adjacent to one, p.7; [APP] if none of those, the empty spaces nearest a spawn point,
   as `BoardRules.heroRespawn`). A token on a hero spawn point makes it not empty (p.7 "empty Spawn
   point"); p.19's "remove the token first" is applied to minions respawning on a push (p.8).
8. **A defeated hero's unresolved card** goes to the current Turn slot ("it is resolved, without
   performing an action", p.13); that hero does not act this turn.
9. **Order of simultaneous effects (p.17):** "use the same method as when resolving tied
   initiative (See page 7), but do not flip the Tie Breaker coin". Timed effects that apply at the
   same moment (End of turn, End of round, Next turn after playing cards): the coin's team first;
   within a team the team decides (p.7 "they have to decide which one of them will act first"):
   with two or more of one team's effects due, its first seat is asked (`CHOOSE_OPTION`; the owner
   when they are all one hero's). Each effect is looked up again before it applies, so one cancelled
   by an earlier one (its owner defeated, p.13; its card changed state, p.17) does not apply.
   `Flow.applyDue`; `EngineAuditTest`. **[APP]** Hooks reacting to one event (triggers,
   replacements, queries) still run in seat order within a team: which of them would do something is
   only known by running them, so asking would mean a question at every event.

## Actions, movement, placement

10. **Movement limits are read at the start of a Movement action (p.11)**; a hero may move into
    and through a limiting radius (Arien FAQ). `Actions.movementMoves`; `MovementTest`.
11. **[APP] Exact distances in card text** ("Move 2 spaces", p.11 "you must move the exact number
    of spaces") use paths that never enter the same space twice. "Up to N" uses any path.
12. **Fast Travel is a placement** (goa2-faq: "Fast Travel is a placement, not movement"; p.18
    "Place": only into an empty space), so an effect that stops a hero being placed by itself
    stops its Fast Travel too (Wasp's Magnetic Dagger). It still needs a Movement action (Stone
    Gaze FAQ). `Actions.canFastTravel`.
13. **[APP] Arien's Slippery Ground / Deluge and Fast Travel.** The FAQ says this card "will not
    affect pushing, swapping, or placement effects (including fast travel!)", but the card says
    "Enemy heroes adjacent to you cannot fast travel". Card text wins (p.3): affected heroes
    cannot Fast Travel. (Read as: it does not stop other placements.) `ArienTest` "Slippery Ground".
14. **[APP] Water and "ignoring obstacles".** A unit ignoring obstacles may pass the water ring
    around the map one hex deep (`BoardGeometry.edge`, the same extent Range/Radius use); a push
    ignoring obstacles stops at that ring. (`BoardRules` in the sandbox lets such a move pass any
    hex; on the real map no shortest path needs more.) `MovementTest` push consistency.
15. **Tokens units may move through (Min's mines).** When such a token is in reach, movement is
    asked space by space so the mover chooses the path (p.18: "any special effects of tokens are
    still applied"). Otherwise a destination is asked and the engine's path is shown in
    `DecisionContext.paths`.
16. **Push (p.18).** No straight line between pusher and target = no push. "Stopped by an
    obstacle" (Wasp) includes a push of 0 because the first space is an obstacle; a push an effect
    forbids (Bulwark) is not "stopped by an obstacle". Several targets "up to X spaces": the
    distance is chosen once and all move it; from one pusher their lines never cross.
17. **Swap (p.19)** ignores obstacles, movement and placement limits, but not "cannot be swapped"
    effects (Bulwark, Magnetic Dagger).

## Attacks and defense (pp.13–14)

18. **[APP] An attack that "fails to connect" (p.17 "Before the Attack").** After the "Before the
    attack" text the target is checked again (still an enemy on the board, not immune, adjacent for
    a non-ranged attack, matching the card's targeting); if not, there is no attack, no defense,
    and "After the attack" text is not applied (it applies "after the target either defends, or
    is defeated and removed", p.13, and p.17 "After the Attack": "Apply this text after the target of
    your attack defends or is removed" — neither happens). Rules audit (2026-10-24) re-read: the
    other reading (After text always applies once Before text did) has no source; Brogan's Mad Dash
    reminder "If you cannot make this move, you cannot attack" and the FAQ's "fizzle" both treat an
    attack that never reaches its target as not happening. Kept. `CardRulesTest`.
19. **A ranged card's attack is ranged even on an adjacent target** (p.9: "Ranged primary actions
    have the word Ranged next to their type"): Brogan's Throwing Axe bullet 1 can be blocked by
    "Block a ranged attack". `BroganTest`.
20. **Block cards** succeed only when their text says so for this attack; otherwise "you are
    defeated" (p.14). Takahide's Shameful Display has no condition, so it always fails.
21. **Minion defense modifiers** count by presence (p.18: presence checks count immune units), so
    an immune heavy minion still gives +1/−1. They are computed at step 2 (p.14) from the board as
    it is then, so a "Before the attack" move changes them (Arien's Noble Blade). `ArienTest`.
22. **[APP] "X discards a card, or is defeated"**: a hero with a card in hand must discard one (it
    chooses which); only a hero with no matching card is defeated, credited to the card's owner
    for coins. Same reading as "discards a card, if able" (p.10) with defeat as the penalty.
23. **Defense previews** (`DecisionContext.defensePreview`) run the Defense text on a copy; null
    when the text asks something or the card is a Block.
24. **Hero defeat order (p.13):** removed; unresolved card resolved; Active effects cancelled;
    markers returned (except persistent ones the card names, e.g. Snorri's runes); coins = level to
    the defeater, assist coins = lowest Tier to every other hero of the defeater's team (on the board
    or not); Life counters = lowest Tier (+ extra costs such as Bounty); 0 left = immediate loss.
    "Lowest card Tier" is read from the hero's current coloured cards. A defeat with no defeating
    hero (none so far) gives no coins.

## Minions and the Lane (pp.8, 18–20)

25. **Heavy Minion Immunity (p.13)** is checked in the heavy minion's own Lane's Battle Zone and
    counts pieces that count as friendly non-heavy minions there (p.17 "Counts as").
26. **[APP] Mid-turn pushes count minions displaced out of the Battle Zone** during the action: the
    last enemy minion "in the Battle Zone" is the last one of that Lane on the board, because
    displacement "has no impact" (p.18) and a push "can never be triggered by moving, pushing, or
    swapping a minion" (p.8). For the same reason step 3's "Remove all minions in the previous Battle
    Zone" removes a Lane minion displaced out of it during the action too (it would otherwise walk
    into the new Battle Zone as a minion more than its Spawn points). `MinionsTest`, `EngineAuditTest`.
27. **Minions outside the Battle Zone go back after every action** (p.18 "after you perform an
    action"): after each hero action (Hold included), after End of turn and End of round effects.
    Ties are the minion's team's (`MINION_RETURN`), the coin's team first.
28. **End of round order is the printed order (p.8):** End of round effects, Minion Battle,
    remove tokens and return markers, then Push the Lane, retrieve, level up, Pity coin. A battle
    that empties a team's Battle Zone pushes at step 3, after tokens are gone.
29. **"This cancels all your Active effects, including 'Next Turn' effects" belongs to Retrieve
    Cards, not to the push.** The page layout jumbles it in the extracted text; the page image
    (`p9R.png`, printed p.8) shows it as the green note under Retrieve Cards. A mid-turn push cancels
    no one's effects; retrieving at the end of the round cancels everyone's. `EndOfRoundTest`.
30. **Respawn on a push (p.8 step 3):** a token on a Spawn point is removed first; a non-token
    obstacle sends the minion to "the nearest empty space of their choice in the Battle Zone",
    the coin's team first. [APP] "Nearest" is measured from that Spawn point (as `BoardSetup`).
31. **Brogan's One Man Army (Ultimate).** [APP] "During minion battle you count as a heavy minion"
    only while Brogan stands in that Lane's Battle Zone. He is removed last like a heavy minion; when
    he would be removed, the removals stop and his team loses the push. A team whose real minions are
    all removed but whose Brogan survives the battle still has a "minion" and is not pushed (the
    card makes him one during the battle, and its second clause says when the push is lost).
32. **Double lane (p.20).** Each Lane pushes on its own; end-of-round pushes of both Lanes are
    resolved together ("Minion Battles in each of the two Battle Zones are resolved separately, but
    happen simultaneously"), and if both teams would win at once the pushes are undone ([APP]
    minions, Battle Zones and Wave counters as before the push). After the last Wave counter on
    either Lane flips, the team with more Zones between its Throne and both Battle Zones wins; at the
    end of the round the count is made once, after both Lanes' pushes. On a tie no one wins and play
    continues; the count is made again after every later push. Designer (Arty, BGG thread 3499394,
    2025-04-21): "So if there is a tie after the last wave counter is flipped, no one wins and the
    game continues until a single push in one lane happens to break it." — "…or a life-counter
    victory happens first." `Units.pushLane`, `Units.countZonesAfterPushes`; `EngineAuditTest`.
33. **[APP] Counters outside the rulebook table:** a double-lane map with fewer than 6 heroes uses
    the 6–8 player row (6 Life counters); a single-lane game with 7+ heroes uses the 6-player row.

## Effects and card states (pp.10, 17–19)

34. **Active effects** are in force: This turn — the turn they were made; This round — until
    Retrieve; Next turn — the next turn of the same round (FAQ: never into the next round); End of
    turn / End of round / Next turn after playing cards — applied once. They end when the card
    changes state (except when a played card is placed on its Turn slot, p.7), when the owner is
    defeated, and at Retrieve. A Defense card's effect lives on the discarded card.
35. **"Only one instance of an active effect per card"** (p.10): starting a card's effect again
    replaces the old one.
36. **"Played an attack card this turn"** (Brogan, Sabina FAQ) is recorded at the reveal and when a
    card enters play after it, and is never forgotten that turn.
37. **Repeats (p.10):** a repeated instance cannot repeat; "on a different target" excludes every
    unit the action targeted so far; "on the same target" allows only the first target.
38. **[APP] "Choose one —" with a bullet that cannot start** is not offered; with one possible
    bullet there is no question.
51. **"X discards a card, if able" needs an X (p.17 "If Able", p.10).** The sentence's subject is a
    target to find; "if able" makes only the discard optional. goa2-faq (Gydion, Energy Drain): "you
    need to be able to target a hero first. 'If able' after a comma makes the preceding step
    non-mandatory (p. 17), so it only applies to the 'discard' step"; p.10: "You choose one enemy hero
    in radius to target, then that hero chooses a card to discard, if they have one." No such hero:
    the step can't be done (as a first instruction the Primary action is not offered, ruling 4); a
    hero with no card: nothing happens and the text goes on. "If able, …" at the start of a sentence
    makes the whole sentence optional. EFFECTS.md "Mandatory and optional steps".
52. **The log never names a hidden card (p.4).** Discarding, retrieving or swapping a facedown card
    (or a card that is hidden before and after a swap, e.g. hand and a facedown slot) says "a facedown
    card" / "two hidden cards"; a facedown token is "A facedown token". `Cards.isPublic`;
    `EngineAuditTest`.

## The four first-game heroes

39. **[APP] Arien's Living Tsunami "Once per turn":** declining the move does not use it up; it
    can be used before a later Attack action that turn. Violent Torrent's repeat is a second Attack
    action, so the move may be used there if it was not used before.
40. **Arien's Dangerous Current family:** the hero behind must be targetable by Arien's attack
    (immune heroes can't be chosen); "Up to 1" may be nobody (FAQ).
41. **Arien's Liquid Leap:** "empty spawn point" uses p.17 ("a Spawn point in an empty space"),
    so a spawn point with a unit on it does not forbid the spaces next to it.
42. **[APP] Wasp's Static Barrier:** whether the acting enemy hero is "outside/in radius" is read
    from its current space each time a space is checked (its position at the start of each move
    step), and the effect applies to every move, push and placement during that hero's actions
    (FAQ: swaps and attacks across the barrier are allowed).
43. **Wasp's Magnetic Dagger** applies to enemy units in radius being swapped or placed by
    themselves or by enemy heroes (FAQ: placement abilities of enemy heroes; units outside may
    place themselves into the radius).
44. **Wasp's High Voltage:** "after you perform a basic skill" = after a Primary Skill action on a
    basic card (Static Barrier); the hero who discards is one who was adjacent to the minion before
    it was defeated.
45. **[APP] Xargatha's Metamorphosis "You may move through obstacles"** applies to her own moves
    (she is both mover and actor), movement actions and card text alike; not when another hero
    moves her, and not to pushes.
46. **Xargatha's Stone Gaze:** petrified heroes count as terrain (an obstacle that also satisfies
    "adjacent to terrain") and cannot take Movement actions or Fast Travel through one (FAQ);
    place, swap and card-text moves still work.
47. **Xargatha's Siren's Call "if able, move the target":** mandatory whenever a space next to her
    can be reached within 3; otherwise the target stays and the card resolves.
48. **Xargatha's Charm family:** the minion move is offered before her movement and, if declined,
    after it; once.
49. **Brogan's Shield family:** works whenever a friendly minion in radius is *defeated* (any
    source), never in the Minion Battle (removed, FAQ). Brogan is asked each time.
50. **Brogan's Onslaught:** the move into the space is mandatory when it is empty (FAQ), and does
    not happen when the attack did not connect.

## Disagreements with goa2-backend (source 7, cross-check only)

| Topic | goa2-backend (`deterministic_rules.md`, `scripts/`) | Engine (from the rulebook) |
|-------|------------------------------------------------------|----------------------------|
| End of round order | Retrieve cards first, then Minion Battle, tokens, level up, pity; no "return markers" step | p.8 order: Minion Battle, remove tokens and return markers, push, retrieve, level up, Pity coin |
| Tie Breaker flip | Flipped "immediately after one player from the Favored Team is chosen" | p.7: flipped after that player has resolved their card |
| Heavy immunity | "until no more friendly minions are present" | p.13: friendly **non-heavy** minions in the Battle Zone |
| Minions out of the Battle Zone | Moved back "immediately" | p.18: "after you perform an action" |
| One Man Army | Brogan is +1 to his team's count; the push happens when his team has no real minions left | Ruling 31: he is a heavy minion during the battle; the push is lost only when he would be removed |
| "Discards a card, or is defeated" | Must discard if the hero has cards; defeat otherwise | Same (ruling 22) |
| Living Tsunami | Optional move, once per turn | Same; declining does not use it up (ruling 39) |
