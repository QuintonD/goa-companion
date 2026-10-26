# Contract changes (`GameApi.kt`)

Every change to the public contract since ADR-035's first version (commit `63a8f26`), newest
last. All are additions with defaults; nothing was renamed or removed. Behaviour notes that
other branches should know are listed too.

## Additions

| Where | Change | Why |
|-------|--------|-----|
| `CardView.slot: Int? = null` | The Turn slot 1..4 of a resolved card | p.7 "Place your resolved card on the current Turn slot": the UI draws the dashboard slots; the order of `HeroView.resolved` alone loses which turns were skipped |
| `WinReason.CARD_TEXT` (`"card_text"`) | A win a card's text declares | Cutter's A Fistful of Coins ("you alone win the game"), Gydion's Wish ("your team wins the game") |
| `HeroView.alsoPlayed: List<CardView> = emptyList()` | Further played cards this turn, hidden like `played` | Emmitt's Ultimate lets him play two cards |
| `PuzzleInfo.intro: String = ""` | What the player knows that the board doesn't show (cards played facedown, opponents' hands, who acts next) | Puzzles treat hands as open information; the play view hides them |
| `Controller.Ai.strength: Int = level.strength`, `Controller.Ai(strength)`; `AiLevel.strength` (2 / 6 / 8 / 10), `AiLevel.of(strength)`; `object AiStrength` (`MIN` 1, `MAX` 10, `DEFAULT` 5, `coerce`) | A computer seat plays at a strength 1–10; `level` stays as its band (1–3 Easy, 4–6 Normal, 7–8 Hard, 9–10 Expert) for the words. A save that carries only `level` loads as that level's strength | ADR-039: the difficulty slider. Build seats with `Controller.Ai(strength)`; read `strength` to play and `AiLevel.of(strength)` for the band's name |

## Behaviour of the existing contract (no type change)

- **Single-option decisions are answered by the engine** and never appear in `pending` — a unit
  to move when only one can be moved, a destination when only one exists, a target when only one
  is valid. Decisions that are acts of the player are always asked, even with one option:
  `PLAY_CARD` (also `[Pass]` alone), `CHOOSE_ACTION` (also `[Hold]` alone), `DEFEND` (cards +
  Decline), `RESPAWN` (spaces + Decline), `CONFIRM`, and optional choices (they always carry a
  `Choice.Decline`). So `Decision.id` still increases by one per decision asked.
- **`Decision.seat`** is the hero whose controller answers. For team decisions (`ACT_FIRST`,
  `REMOVE_MINION`, `MINION_RETURN`) it is the team's first hero in seat order. An effect may hand
  a hero's choices to another hero (Hanu's Ultimate); `seat` then names the deciding hero.
- **`DecisionContext.defensePreview`** is filled by probing each card's Defense text; it is null
  for Block cards and for texts that ask a question while resolving. `paths` keys are `"q,r"`.
- **`GameSession.events`** is the full log; `AnswerResult.Accepted.events` are only the new ones.
  Event text never names a facedown card.
- **`save()` / `restore()`** replay the answers from the start (or from the built position);
  `SavedGame.engineVersion` is `GameEngine.ENGINE_VERSION` (1). A save whose answers no longer fit
  after a rules change throws on `restore` (`IllegalStateException`): the app should offer to drop it.
- **`determinize(team, seed)`** returns a session whose pending decision is the same kind as the
  original's; its answers can't be used on the original (different hidden state).
- **`undoToLastHumanDecision()`** replays to just before that decision; AI seats then answer again.
- **`GameFactory.newGame`** throws `IllegalArgumentException` for an unknown map, an unknown or not
  yet playable hero, a hero seated twice, or a missing team.

## Engine-side entry points (not in `GameApi.kt`)

- `GameEngine(catalog, maps, matchRules = null, registry = Heroes.registry(), allowIncomplete = false)`;
  `matchRules` is `catalog/match-setup.json` when loaded (the built-in table is equal, `SetupTest`).
- `GameEngine.fromState(setup, state)` and the `position(engine, mapId) { … }` DSL
  (`dev.quinton.goa.core.game.position`) for positions (puzzles, tests, "play from here").
- `EngineSession.state` (read-only view of the engine state, for the AI's evaluation and puzzles)
  and `EngineSession.forkSession()` (a fork typed as `EngineSession`).
- **Puzzles** (`dev.quinton.goa.core.game.puzzles`, core/game/puzzles/ENCODING.md): the app builds
  `EnginePuzzleBook(engine)` (a `PuzzleBook`); `start(id)` returns an `EnginePuzzleSession`
  (a `PuzzleSession`). A puzzle is listed once every hero it seats is playable.
- Internal helpers on `EngineSession` for the puzzle solver (additive, `internal`, 2026-10-24):
  `checkpointState` and `stepAnswers` (the position key), `haltWhen` (stop the game before a step
  whose start state matches: `pending` becomes null, as at the end of a game; copied by forks), and
  `forkForAnswer()` (a fork that shares the displayed state because it is answered at once).

## Puzzle behaviour (no type change)

- **`PuzzleInfo.id`** is `p` + the concept number of core/game/puzzles/CONCEPTS.md (`p01`…`p60`),
  so encoders working in parallel never collide; 50 are shipped. **`PuzzleInfo.number`** is set by
  the book: the place in the `Puzzles.all` list (the learning order) among the playable puzzles.
- **A puzzle game stops at its deadline**: `PuzzleSession.game.pending` is null once the turn (or
  round) of the goal is over, whether the goal was met or not; `status` says which.
- **Answer through `PuzzleSession.answer`**, never `game.answer`: the runtime answers every other
  seat at once. `game.view(seat)` of an opponent shows that opponent's hand: puzzles treat hands as
  open information (CONCEPTS.md conventions).
- `PuzzleSession.bestChoice()` is null after a miss (undo first) and on decisions that are not the
  player's.


## 2026-10-24 (AI, `feature/play-ai`)

- **New public entry points** (`dev.quinton.goa.core.game.ai`): `AiPlayers.forLevel(level, seed)` (the
  `AiPlayer` per `AiLevel`, capped at the phone budget), `AiPlayers.deterministic(level, seed)` (no
  wall-clock cap, for tests), `AiCoach(factory, seed)` (the `Coach`; `turningPoints` needs the
  `GameEngine` as factory). See `core/game/ai/AI.md`.
- `EngineSession.determinizeWith(viewer, seed, Determinizer.Options)` (additive): `determinize` with the
  AI's refinements — public card memory (`seen`), an opponent model for unrevealed played cards
  (`playWeight`) and human teammates whose facedown card is hidden (`hidePlayedOf`). Same guarantee:
  the sample depends only on public information and the seed. `determinize(viewer, seed)` is
  `determinizeWith(viewer, seed, Options())`.
- **`determinize` behaviour:** since item card ids are private, it now samples each enemy's *upgrade
  history* (which colours were upgraded, which card of each pair was kept) consistent with the level,
  the item icons and the faceup cards, instead of keeping the real items. Hand stamps are cleared;
  facedown discards keep their pile order. Heroes outside the one-card-per-colour shape (extras,
  spellbook, cards moved by text) use the previous swap within colour and Tier.
- **`HeroView.itemStats` is sorted by stat** (it followed deck order, which told which colour each
  item came from).
- `EngineSession.engineData` is `internal` (the AI reads cards and maps from it); not public API.
- **Leak to fix in the flow (not changed here):** the level-up log line
  `"<hero> gains <card name> as an item (+1 …)"` (`Flow.kt`, `upgradeOnce`) names the tucked card,
  which is hidden information (designer, BGG 3590819); suggest `"<hero> gains an item (+1 …)"`. The AI
  does not read that event.

## 2026-10-24 (hands-on play round, `fix/play-handson`)

All additive (defaults keep old callers and saves working; events are rebuilt on replay).

- `DecisionContext.blockPreview: Map<String, Boolean>` (DEFEND): for Block cards whose condition the
  engine settles before any choice, whether the Block holds against this attack (Stop Projectiles
  against a melee attack: `false`). `defensePreview` stays `null` for Block cards.
- `GameEvent.CardsRevealed.played` is ordered as the cards will act (initiative, ties by the coin,
  p.7; passes last) and its text follows that order; new `initiatives` (as they count: items and
  effects included).
- `HeroView.initiative`: after the reveal, while the card is unresolved, its initiative as it counts.
- Event text names Zones instead of hex coordinates: "Brogan fast travels to Lane Zone 2.",
  "Wasp respawns in the Red Throne Zone.", "Arien is placed 3 spaces away, in Lane Zone 1."
  (`Game.spaceName`, on `core:domain`'s `BoardMap.zoneName`, which `BoardText.zone` now uses).

## 2026-10-24 (coach looks ahead, `fix/play-coach-ahead`)

Additive; a `Coach` that implements neither new method behaves as before.

- `CoachAnalysis` (`decisionId`): the coach's look at one decision, opaque to the app.
- `Coach.prepare(session, decision, cancelled): CoachAnalysis?` (default `null`): looks at a decision
  before the person answers. Called on a `fork()` the caller doesn't touch meanwhile, possibly off the
  engine thread; never changes the session; reads only what the deciding team may know (the same
  determinized copies as `nudge`). One call at a time per coach.
- `Coach.nudge(analysis, session, decision, choice, cancelled): CoachNote?` (default: the old
  `nudge(session, decision, choice, cancelled)`): the nudge from a prepared look. `AiCoach` gives the
  same note as the old `nudge` for the same seed (`CoachAheadTest`); it falls back to the old `nudge`
  when the analysis was made for another decision.
- `AiCoach` behaviour: `prepare` runs the analysis `nudge` ran, without the choice; `nudge(analysis, …)`
  plays out only the person's choice when the pruning had left it out (same seeds and samples,
  appended last as before), then judges and explains as before.

## 2026-10-25 (game-state review, `feature/play-review`, ADR-037)

Additive; defaults keep other `GameSession` implementations (test fakes) compiling.

- `GameSession.review(viewers: Set<String>): List<TurnReview>` (default empty): the game so far turn by
  turn, the turn being played last. `TurnReview` (`round`, `turn`, `plays`, `opening`, `events`,
  `endOfRound`, `finished`, `hasStart`, `fromPosition`) and `TurnPlay` (`heroId`, `team`, `card`,
  `alsoPlayed`, `initiative`, `resolved`). `plays` are each hero's card as the viewers saw it just after
  the reveal, in acting order; empty before the reveal.
- `GameSession.positionAt(round, turn, viewers): GameView?` (default null): the board at the start of
  that turn (or a built position's start inside it) as the viewers saw it then.
- `viewers` merge like the app's views: each viewer as it sees itself, everyone else as the first viewer
  in seat order sees them; empty = spectator. The play screen passes the person's heroes, a puzzle every
  seat (its hands are open).
- `GameView.actedThisTurn: List<String> = emptyList()`: after the reveal, the heroes who have resolved
  their card this turn, in order (`TurnRecord.resolved`).
- Behaviour: `EngineSession` keeps references to committed checkpoints at turn boundaries (turn start,
  just after the reveal, round end; a built position's start state) and builds every card of the review
  through `Views` on them, so it can never name more than the live view did. Forks, `restore` and
  `undoToLastHumanDecision` carry or rebuild them; `determinize` copies start empty. Nothing is saved:
  the history is rebuilt by the replay.

## 2026-10-25 (Gydion's spellbook in play, `feature/v07-spellbook`, ADR-040)

Additive; a hero without extras gets `extras = emptyList()`, so other `GameSession` implementations and
saves are unaffected (views are rebuilt, never saved).

- `HeroView.extras: List<ExtraPlaceView> = emptyList()`: the hero's extra cards (hero JSON `extras`) by
  place. `ExtraPlaceView(place, count, cardIds, open)`: `cardIds` in the hero JSON's `extras` order, or
  null when the place is hidden from this viewer; `count` is always public. Gydion's places, in this
  order: `spellbook` (not open: named to Gydion's own seat only, not to teammates nor to a spectator —
  Prepare Spells "Only Gydion can look at the cards in the spellbook"), `removed` (open: cast, or removed
  by Find Familiar, "remove it from the spellbook faceup"), `box` (open: not added yet; the game starts
  with an empty spellbook, designer on BGG 3491113). Every declared place is listed, also when empty.
- Engine side: `ExtraPlace(tag, open)` and `Registry.extraPlaces(heroId, vararg places)` (a hero module
  declares its places; a tag used without being declared is listed as hidden). Built in `Views`, so the
  live table, `merged` views, `review`/`positionAt` (ADR-037) and puzzles show the same visibility.
  `ExtrasViewTest` checks it, with a no-peeking test (two games differing only in the spellbook's spells
  look the same to every other seat, live and in the review).

## 2026-10-25 (why an action is missing, `fix/v07-handson`, ADR-041 §5)

Additive; defaults keep other `GameSession` implementations, test fakes and saves unaffected (decisions
are rebuilt by the replay, never saved).

- `DecisionContext.unavailable: List<UnavailableAction> = emptyList()`: on `CHOOSE_ACTION`, each action the
  asking card prints (its primary; a printed secondary Movement or Attack) that is not among the options,
  with why. `UnavailableAction(action: ActionType, text: String, source: String? = null, byCardId: String? = null)`:
  `text` is one short line ("Its Skill can't start: the spellbook is empty until Prepare Spells is
  performed", "Its Movement is forbidden by Xargatha's Stone Gaze"), `source` the rule ("p.10, FAQ",
  "p.14", "designer, BGG 3491113"), null when `byCardId` (a faceup card whose effect forbids it) is the
  reason. Never names a hidden card.
- Engine side: `Game.actionAvailability(heroId, cardId, explain)` builds the options and the reasons in
  one pass (`actionOptions` = its options); `actionForbiddenBy` (the forbidding `Src`) under
  `actionAllowed`; `primaryBlocker` (the probe's `Fizzle` reason and source) under `primaryPossible`;
  `Fizzle(reason, source = null)` and `fizzle(reason, source)`. `attackTarget(…, none = "no target")`:
  `adjacentAttackTarget` fizzles "no target adjacent to you", `rangedAttackTarget` "no target in range"
  (the log's "… stops:" lines changed with them). Gydion's `castOneOf` says why no spell can be cast.
