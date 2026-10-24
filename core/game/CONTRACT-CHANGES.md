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
