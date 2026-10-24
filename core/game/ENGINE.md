# The rules engine (`:core:game`)

ADR-035. Pure Kotlin/JVM; depends on `core:model` (catalog, maps) and `core:domain` (board
geometry and rules, starting cards, counter table). The public contract is `GameApi.kt`; this file
is how the engine behind it works. Card authors read [EFFECTS.md](EFFECTS.md); rule readings are
in [RULINGS.md](RULINGS.md); contract changes in [CONTRACT-CHANGES.md](CONTRACT-CHANGES.md).

## Packages

| Package | What |
|---------|------|
| `core.game` | `GameApi.kt`, the contract |
| `core.game.engine` | State, the session, the flow and every rule: `State.kt`, `Game.kt` (decisions, lookups, behaviour sources), `Board.kt`, `Cards.kt`, `Combat.kt`, `Units.kt`, `Effects.kt`, `Actions.kt`, `Flow.kt`, `Setup.kt`, `Views.kt`, `Session.kt`, `Determinizer.kt`, `GameEngine.kt` |
| `core.game.effects` | The authoring framework: `Behavior` (hooks), `CardScript`/`HeroModule`/`Registry`, `Helpers.kt` |
| `core.game.heroes` | One `HeroModule` per hero (`<Hero>Hero.kt`) and `Heroes.all` |
| `core.game.position` | `PositionBuilder` / `position { }` |
| `core.game.ai` | The computer opponents (`AiPlayers`) and the coach (`AiCoach`): [ai/AI.md](ai/AI.md) |
| `core.game.puzzles` | Puzzle solver, answer keys, runtime (`PuzzleBook`, `PuzzleSession`) and `book/` (one file per puzzle): [puzzles/ENCODING.md](puzzles/ENCODING.md) |

## State

`GameState` (`State.kt`) is plain, mutable, `@Serializable` data: heroes with every card and its
state (`CardInst`: zone, Turn slot, facedown, played), markers, minions bound to Lanes, pieces
(tokens and other objects; a piece may be a *body* of a hero), Battle Zones, Wave and Life
counters, the coin, Active effects (`ActiveEffect`: card, owner, timing, round/turn made, action
type, data), the program counter (`step`, `stepSeat`), the turn record (start positions, who played
an attack card, cards discarded, who resolved, once-per-turn notes) and a decision counter. It holds
ids only; what a card or token *does* is code in the registry. `copy()` is a hand-written deep copy
(a few hundred small objects, microseconds), because the session copies the state on every run.

## Re-execution

The flow is a list of steps (`Step`): turn start, one `PLAY` step per seat, reveal, one `RESOLVE`
step per hero's card, end of turn, then the end-of-round steps (`EOR_EFFECTS`, `MINION_BATTLE`,
`CLEANUP`, `PUSH`, `RETRIEVE`, one `LEVEL_UP` per seat, `PITY`). `Flow.runStep` performs the
current step and moves the counter.

The session (`EngineSession`) keeps a **checkpoint** (the state at the start of the current step)
and the **answers** given in this step. To advance it copies the checkpoint and runs the step with a
`Game` bound to those answers. Rules and card scripts are ordinary imperative Kotlin; whenever they
need a choice they call `Game.ask(...)`:

- the k-th `ask` of the run returns the k-th answer if there is one;
- otherwise it throws `Suspend(decision)`: the run stops, the partly applied copy becomes the
  displayed state (`view`), and the decision is pending (`Decision.id = committed decisions + k`).

An answer is validated against the pending options, appended, and the step runs again from the
checkpoint. When a step finishes, its state becomes the new checkpoint and its events are committed.
A win throws `GameEnded` from anywhere (p.8: "the game ends immediately").

Why this design: card effects stay easy to write (no callbacks, no state machines per card), the
whole game is serializable at *any* decision, mid-card and mid-defense included (the save is the
start state plus the answers), forks are a pointer copy, and the same code runs for humans, the AI
and the puzzle solver. The cost is re-running the step for every answer; steps are short (one
card resolution) and the costly option lists (the Primary-action probe, defense previews) are
built lazily, only when a question is really pending (`Game.askAlways`).

Rules for code that runs inside a step (scripts included):

- **Deterministic**: no randomness, no clock, no iteration over unordered hash maps. Option lists are
  sorted canonically (`HEX_ORDER` for spaces, seat/creation order for units).
- **All state changes go through `Game` primitives** (`setPos`, `place`, `move`, `push`, `swap`,
  `moveCard`, `discard`, `addEffect`, …): they keep the occupancy and behaviour caches right
  (`Game.touch()`), emit events and fire triggers.
- **Single-option questions are not asked** (`ask(auto = true)`, the default) unless the question is
  an act of the player (play, action, defend, respawn, confirm, optional choices with Decline).
- **Control flow signals** (`ControlSignal`, no stack traces): `Suspend`, `GameEnded`, `Fizzle`
  (a mandatory step can't be done, p.10: caught at the action, which then ends), `ActorDefeated`
  (the acting hero was defeated: its action ends), `ProbeHit` (probes). Never catch them broadly.

## Save, restore, fork, undo, determinize

- `save()`: `{ initial state, every answer with its decision id and seat }` as JSON.
  `restore()` replays. `ENGINE_VERSION` marks saves made by older rules.
- `fork()`: shares the immutable history and event chains (`Chain`, a persistent list) and the
  checkpoint (never mutated in place); only the current step's answers are copied.
- `undoToLastHumanDecision()`: replays the history up to the last answer of a `Controller.Human`
  seat; that decision is pending again.
- `determinize(team, seed)` (`Determinizer`): from the checkpoint, for every hero of the other team,
  an *upgrade history* consistent with its level, item icons and faceup cards is drawn (item card
  ids are private), its kept cards and the hidden basics are dealt to the hidden places (hand,
  facedown cards, the unrevealed played card), then hand and played card are dealt again from the
  hand pool; heroes outside the one-card-per-colour shape swap hidden identities within colour and
  Tier instead; the other team's facedown tokens shuffle kinds. Places are canonicalised first and
  hand stamps cleared, so the sample depends only on public information and the seed
  (`SessionTest`, `PropertyTest`, `ai/NoPeekingTest`). `determinizeWith(team, seed, options)` adds the
  AI's opponent model, card memory and hidden teammate plays (`ai/AI.md`). The step is then re-run
  with the given answers in *lenient* mode: an answer that no longer fits the sampled state is
  replaced by a seeded option.

## Views and events

`Views.build` follows p.4: a seat sees its own hand and played card; other hands only as counts;
played cards facedown until the reveal; facedown resolved/discarded cards and the other team's
facedown tokens hidden; everything else open. Events are full English sentences (`GameEvent.text`)
for the log and TalkBack; they never name a hidden card: a facedown card, or a card that is hidden
both before and after a swap, is "a facedown card" / "two hidden cards" (`Cards.isPublic`), and a
facedown token is "A facedown token" (RULINGS 54). Item cards are not named in `HeroView` (only their
icons, `itemStats`); the level-up log line names the item card, which the item icon already tells
(every Tier II/III pair of a colour has two different item icons in the catalog).

**Review (ADR-037).** The session keeps the committed checkpoints at turn boundaries (`Mark` in
`Review.kt`: turn start, just after the reveal, round end; a built position's start state) — a reference
each, since checkpoints are never mutated. `review(viewers)` turns them into `TurnReview`s: the cards
come from `Views.merged` on the post-reveal state (the same hiding as the live table), the order from the
turn record at the turn's end, the lines from the event indices kept with each mark; `positionAt` is
`Views.merged` on a turn's start state. Forks share the marks; replay rebuilds them.

## Behaviours in force

`Game.sources()` lists every behaviour in force with its source: hero rules, unlocked Ultimates of
heroes on the board, Active effects in force, markers, pieces on the board — in the p.17 order
(the coin's team first, then seat order). Queries (`statDelta`, `forbidsAction`, `countsAsObstacle`…)
combine all sources; triggers and replacements run in that order and may ask decisions. See
EFFECTS.md for the hook list.

## Performance

`BenchmarkTest` plays random-legal 2v2 Quick games on the desktop JVM: **about 28–30 games per
second** (≈ 850 decisions per game, about 25 000 decisions/s) on the development machine,
2026-10-24 (target ≥ 20). Random games are long (10–25 rounds) because random heroes rarely meet;
real games have far fewer decisions. `PropertyTest` plays 1 200 games with invariant checks at every
decision in about a minute.

Hot spots, if the AI needs more: the Primary-action probe copies the state once per Primary option
asked; BFS movement asks `sources()` per space; re-running a long step (a repeated attack) is
quadratic in its decisions.

## Style

`core/game/detekt.yml` widens detekt's complexity limits for this module only (cyclomatic 32,
conditions 6, nesting 5, guard-clause returns): rules code follows the rulebook's own branching, and
hook code guards every clause of a card with an early return. Everything else is the app's style.

## Tests

| Test | What |
|------|------|
| `rules/SetupTest` | p.5–6, p.20: opening, hands, Handicap, counters (and the built-in table = `match-setup.json`) |
| `rules/TurnTest` | p.7: hidden play, pass, initiative, ties and the coin, recomputed initiative, respawn, actions offered, Turn slots, This turn |
| `rules/MovementTest` | p.11, p.17–18: movement, Fast Travel, card-text moves, limits at the start; engine = `BoardRules` on 200 random positions; push = `BoardRules.push` on 300 |
| `rules/CombatTest` | pp.12–14: targets, minions and coins, heavy immunity, the p.13/p.14 Brogan, Wasp and Dodger examples, primary vs secondary defense, Block, defeat consequences, last Life counter, Bolster |
| `rules/MinionsTest` | p.8, p.18–20: the p.8 Minion Battle example, heavy last, remove all, mid-turn push, no push by moving, last push, throne, spawn points blocked, minions returning, double lane |
| `rules/EndOfRoundTest` | p.8, pp.15–16: tokens, markers, retrieve, End of round effects first, level up, Tier II before III, Ultimate, Pity coin |
| `rules/CardRulesTest` | p.10, p.17–19 with test-only scripts: mandatory and optional steps, Primary availability, before the attack, repeat, Active effect timings and cancelling, card states, token supply |
| `heroes/*Test` | Every card of Arien, Brogan, Wasp and Xargatha, clause by clause, with their FAQ |
| `SessionTest` | Decision ids, rejected answers, save/restore mid-card and mid-defense, fork, undo, determinize |
| `ReviewTest` | The game-state review: turns partition the log, cards as the viewer saw them at the reveal, no card the viewer never saw (review and kept boards), facedown played cards / tucked items / facedown resolved cards hidden, puzzle viewers, mid-turn positions, restore/fork/undo, `actedThisTurn` |
| `PropertyTest` | 1 200 random games (1v1, 2v1 Handicap, 2v2 Quick/Long, double lane): invariants at every decision; determinism; save/restore and fork equivalence; determinize |
| `BenchmarkTest` | Games per second |
| `ai/*Test` | The computer opponents and the coach: level ladder, no peeking, every decision kind, timings ([ai/AI.md](ai/AI.md)) |
| `puzzles/PuzzleBookTest` | Every bundled puzzle: proof, answer key up to date, solution, nudges, wrong tries, first-option and "show me" lines, answer times |
| `puzzles/PuzzleSolverTest` | Solver, goals, deadline halt, runtime and answer-key rules on small positions |
