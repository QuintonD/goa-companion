# Encoding puzzles

How to turn a concept of [CONCEPTS.md](CONCEPTS.md) into a bundled, solver-proven puzzle (ADR-035,
PRODUCT.md §10). One file per puzzle, one line in the registry, one generated answer key; the tests
do the proving. Status of every concept: [STATUS.md](STATUS.md).

Code: `core/game/src/main/kotlin/dev/quinton/goa/core/game/puzzles/`

| File | What |
|------|------|
| `Puzzle.kt` | `Puzzle` (what a puzzle file defines), `Move`/`Moves` (stored lines), `WrongTry` |
| `book/Pnn<Name>.kt` | One puzzle each; `book/Puzzles.kt` lists them |
| `Goals.kt` | `GoalTracker`: reads the goal from events; `haltAt`: the deadline |
| `PuzzleSolver.kt` | AND-OR proof search |
| `AnswerKey.kt` | The precomputed answers on every winning line (`src/main/resources/.../puzzles/keys/pNN.key`) |
| `PuzzleRuntime.kt` | `EnginePuzzleBook` (`PuzzleBook`), `PreparedPuzzle`, `EnginePuzzleSession` (`PuzzleSession`) |
| `StateHash.kt` | 128-bit position fingerprint for the transposition table |

## The workflow

1. **Pick** a concept in [STATUS.md](STATUS.md) whose heroes are all playable
   (`GameEngine.playableHeroes`; a hero is playable once its module says `complete = true`).
2. **Write** `book/Pnn<Name>.kt`, `nn` = the concept number (C23 → `P23LiftUpIntoTheLine`, id
   `p23`). Copy the closest existing puzzle (see "The puzzles so far" below).
3. **Register** it: one line in `book/Puzzles.kt`.
4. **Run** `./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process :core:game:test --tests '*Puzzle*'`
   (with `GRADLE_USER_HOME`, `TMP`, `TEMP` on D:, see AGENTS.md). The first run writes the answer
   key and fails "the answer key was missing … run the tests again"; run again. Read the output
   (`-i`): the proof test prints the main line and, at every player decision, **every** winning
   option. More than one winning option where the concept expects one = an unintended alternative.
5. **Fix or cut** (below), then update [STATUS.md](STATUS.md) and, when the concept changed, its
   entry in [CONCEPTS.md](CONCEPTS.md) (an "Encoded as" line: what changed and why).
6. Commit the puzzle file, the registry line and the `.key` file together.

## A puzzle file

```kotlin
/** CONCEPTS.md C14 "Long Thrust" (Brogan stands in for Tigerclaw as the hero with no cards). */
object P14LongThrust : Puzzle() {
    override val concept = "C14"
    override val info = PuzzleInfo(
        id = "p14", number = 14, title = "Long Thrust", difficulty = 1,
        theme = "Range set by card text",
        heroes = listOf("xargatha", "wasp", "arien", "brogan"),   // every seated hero, player first
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        nudges = listOf("gentle…", "stronger…", "names the first move"),
        explanation = "Why it works, with page refs (p.9) …",
        rules = listOf("p.9 Range", "p.13 Attacking a hero", "p.19 Unit"),
    )
    override val playerSeats = setOf("xargatha")
    override fun PositionBuilder.setUp() { … }                       // EFFECTS.md "Positions"
    override val solution = listOf(action(ActionType.PRIMARY), hero("brogan"))
    override val nudgedMoves = solution                              // what the last nudge names
    override val wrongTries = listOf(
        WrongTry("Thrust at Arien: Aspiring Duelist defends 5 against 3",
            listOf(action(ActionType.PRIMARY), hero("arien"))),
    )
}
```

- **`info`**: `id` = `p` + concept number (permanent: progress is stored by it; parallel encoders
  never collide). `number` is set by the book (its place in the list: difficulty, then id), so put
  the concept number there. `heroes`: every seated hero, a player hero first; the book lists a
  puzzle only when all of them are playable. `nudges`: at least three, gentle to strong; the last
  names the first move(s), which are `nudgedMoves` (default: the first solution move).
  `explanation`: shown solved or not; say why the tempting tries fail too; cite printed pages
  (`p.N` = PDF page − 1). `rules`: the pages it leans on.
- **`playerSeats`**: the heroes the player controls, all on one team.
- **`deadline`**: from the goal (`WinGame` has none: this turn). Override only with a reason.
- **`nodeBudget`**: solver expansions per solve in the tests (default 200 000). Raise it only if the
  key test reports "out of budget", and say why in a comment.

### Stored lines (`Moves`)

Only the player's answers, in order; every other decision is answered by the runtime. A decision
with one option is answered by the engine and never asked (except acts of the player: play a card,
choose an action, defend, respawn, confirm and optional steps — CONTRACT-CHANGES.md), so list only
what is asked. `action(ActionType.X)`, `unitAt(q, r)` (the unit standing there when the move is
played), `hero("id")`, `tokenAt(q, r)`, `space(q, r)`, `card("id")`, `option(i)` (a "Choose one"
bullet), `number(n)`, `yes()`, `decline()`, `pass()`. A move that is not offered fails the test
with the pending decision and its options, which is the quickest way to learn what the engine asks.

**Wrong tries** are the concept's tempting lines, cut at the move that loses: the status must turn
MISSED exactly at the last move (earlier = the try is too long; never = it isn't wrong). The test
then plays on off the key, undoes back to the miss and once more, and expects PLAYING.

### Positions

Use the PositionBuilder (EFFECTS.md "Positions"). Conventions that keep puzzles honest:

- `clearMinions()` and list every minion. State Life counters, Wave counters and the Battle Zone
  when they differ from the opening. Keep displaced minions inside the Battle Zone unless the puzzle
  is about them going back (p.18).
- Account for all five cards of every hero: `played`, `resolved(slot, …)` (an already resolved hero
  this turn has a card in slot `turn`), `hand(…)` (exact: the rest of this round's cards are in the
  discard), `discard`, `items` (level L has L − 1 items: the other card of each upgraded colour).
- `phase = PositionPhase.Resolving(next = "hero")` for "X acts next"; `PlayCards(fromSeat)` for a
  puzzle that starts with choosing a card. Seats are in call order.
- **Hidden information.** Opponent hands are open in a puzzle (CONCEPTS.md conventions), so say in
  the nudges or the explanation what the player can't see on the table. The solver treats the
  opponent as seeing everything, which is sound for the player's proof, with one exception the key
  generator rejects: **the player choosing a card after an opponent chose one facedown** (the solver
  would read it). Seat the player's heroes before opponents with a real choice, or leave the
  opponent one card (P17 seats Arien first with his card already played).
- Rotating a position to the other side of Forgotten Island: `(q, r) → (3 − q, −7 − r)` (the map is
  symmetric under this half turn; Red's and Blue's spaces swap). P34 uses it.

### Making it difficult, not just correct

`PuzzleBookTest` plays the **first option at every decision** and expects no solve. Option order is
the engine's canonical order: spaces by row then column (`r`, then `q`), units by seat then creation,
hand cards in catalog order (gold, silver, red, green, blue), actions Primary first. A puzzle whose
answer is "Primary, the first target, …" is a click-through; give it a real choice (P17 now starts
at the card choice, where the tempting Shock comes first) rather than reordering ids to dodge the
test. Read the printed winning options too: a second winning target or space usually means the
position is easier than the concept thought.

### When the concept is wrong under the engine

- An illegal position (two obstacles on a space, a space off the map): the builder throws.
- An unintended easier solution, the solver finds a refutation, or a click-through: change the
  position so it still teaches the same idea and stays difficult, and record the change in
  CONCEPTS.md ("Encoded as pNN (changed): …"). If that can't be done, mark the concept CUT in
  STATUS.md with the reason.
- If the engine looks wrong (a rule misread, a card misimplemented), don't encode around it: write
  the case up (position, decision, expected vs actual, rule and page) and mark the puzzle blocked.

## What the tests check (`PuzzleBookTest`, once per puzzle)

| Test | Checks |
|------|--------|
| the info is complete | id/concept match, difficulty 1–3, ≥ 3 nudges, explanation, rules, seated heroes = `info.heroes` |
| the answer key is up to date | regenerates the key from the solver; rewrites the file and fails when it differs |
| the solver proves the goal is forced | root is a WIN within `nodeBudget`; prints the main line and every winning option |
| the stored solution wins | status PLAYING after each move, SOLVED after the last; every answer < 200 ms |
| the last nudge names a winning first move | `nudgedMoves` keep the win |
| every wrong try is refuted | MISSED exactly at its last move; no best choice; plays on off the key; undo |
| the first option does not solve it | see above |
| show me always solves it | `bestChoice()` at every decision solves it |
| the solver alone agrees with the key | the same statuses with and without the key |
| restart | back to the start |

`PuzzleSolverTest` covers the solver, goals, deadline and runtime on small test positions.

## How it works

### Solver (`PuzzleSolver`)

Exact AND-OR search over the engine's own decisions (`EngineSession.forkForAnswer` + `answer`):

- **Sides.** A decision of a player seat is an OR node (the player picks), of another hero of the
  player's team an OR node answered for them (`FRIEND`), of anyone else an AND node (`OPPONENT`).
  Team decisions name the team's first hero (`Decision.team` decides).
- **Goal and deadline.** `GoalTracker` reads the events after each answer: `DefeatHero` = a
  `Defeated` event of that hero; `WinGame` = `GameOver` for the player's team; `PushLane` = a
  `LanePushed` won by the player's team (or a throne / last-push win); `Survive` = no player seat
  `Defeated`, no lane pushed against the team, until the deadline; `GainCoins` = the sum of
  `CoinsGained` of the player seats. The game is **halted** at the deadline
  (`EngineSession.haltWhen`): end of turn = after the turn's End of turn step (in turn 4, before
  the end-of-round steps); end of round = when the next round would start, so the Minion Battle,
  pushes, level-up choices (asked of the player's seats too) and pity coins (they count for
  `GainCoins`) are inside it. A position is a WIN when the goal is met against every reply before
  the halt.
- **Transpositions.** Positions are keyed by a 128-bit hash of the engine checkpoint (the decision
  counter removed, card stamps ranked), the answers inside the current step, and the coins so far.
  Only proven values are stored; the table is cleared past 400 000 entries.
- **Order and pruning.** Options in canonical order, an OR node stops at its first win, an AND node
  at its first refutation. `Solved.best` is the first winning option (player, friend) or the first
  refutation (opponent); when every reply loses, the opponent plays the one that holds out longest
  (most decisions), so "show me" lines don't let the opponent collapse at once. Deterministic.
- **Budget.** Expansions per `solve` call; past it the value is UNKNOWN (not stored). `cancelled`
  is polled at every expansion.

### Answer keys (`AnswerKey`) and why

A wrong move is cheap for a person and expensive for the solver: proving that the goal can no
longer be forced means refuting every continuation (p17's Shock: ~6 400 positions, 0.5–1.1 s on the
development desktop, 2–4 s on a phone). The runtime must answer in well under a second on a phone,
so the work is done at build time: the key lists, for every player decision on every winning line,
all winning options, and for every other decision there the reply the runtime plays. Generated by
the solver, stored as a resource, regenerated and compared by the tests (a stale or missing key is
rewritten and the test fails, so the stored key is always the solver's own). It is ignored at
runtime when `GameEngine.ENGINE_VERSION` or the starting position's fingerprint differs.

Format (`pNN.key`): `P <path> <winning indices>` for player decisions, `R <path> <reply index>`
for the others; `<path>` = the option indices answered since the start, dot-separated (`-` = the
first decision). A key over 20 000 decisions fails: the puzzle has too many alternative lines.

### Runtime (`EnginePuzzleSession`)

- Every non-player decision is answered at once (key reply, else the solver's `best`).
- After a player answer: SOLVED when the goal is met; MISSED when it failed, or the answer is not
  among the key's winning options (exact), or — off the key — the solver proves a LOSS. MISSED
  stays until `undo`.
- Off the key (after a miss) replies come from a budgeted search (`RUNTIME_BUDGET` = 1 500
  expansions); with no key at all the full budget is used.
- `undo` replays the line from the start without the player's last answer (and what followed);
  `restart` is a new session; `bestChoice` is the key's first winning option (null after a miss).
- Answer through the `PuzzleSession`, not through `game` (the `GameSession` is for views and
  events; `game.view(seat)` of an opponent shows that opponent's hand, which puzzles treat as open).

## The puzzles so far (templates)

| Puzzle | Starts | Shows how to |
|--------|--------|--------------|
| `P01ThroughTheLine` | Resolving, the player acts next | a `WinGame` by Life counters; target by `unitAt`; an "up to 1" choice by `hero` |
| `P14LongThrust` | Resolving | a hero standing in for an unplayable one; all moves named by the last nudge |
| `P17IntoTheWall` | Play a card, two player seats | seating an opponent first with his card already played; card choice as the first move |
| `P34RespawnWhereItCounts` | Resolving, player hero off the board | `PushLane`; respawn by `space`; `decline()`; a position rotated to the other side |

### Measured (development desktop, JVM, 2026-10-24)

| | p01 | p14 | p17 | p34 |
|--|-----|-----|-----|-----|
| Root proof (no key) | 12 nodes, < 10 ms | 6 nodes, < 5 ms | 6 432 nodes, 0.5–1.2 s | 8 nodes, < 20 ms |
| Key generation | 164 nodes, 30–50 ms | 147 nodes, 30–85 ms | 10 167 nodes, 0.5–0.9 s | 9 nodes, 15 ms |
| Player answer on the key | ≤ 20 ms | ≤ 11 ms | ≤ 18 ms | ≤ 1 ms |
| Answer off the key (slowest) | 9 ms | 0 ms | 35 ms | 0 ms |

A solver expansion costs 50–150 µs (one engine step re-run, plus a 12 µs hash when a step
commits). With the key, a phone (3–4× slower) answers in well under 100 ms. The test asserts every
answer under 200 ms on the desktop.
