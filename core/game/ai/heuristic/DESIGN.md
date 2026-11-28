# The heuristic computer opponent (ADR-085): design contract

Owner, 2026-11-20: "Improve the AI. Abandon the current approach in favour of more traditional approaches.
Start with a robust series of heuristics: first a general set, then character-specific sets. Reason it out,
and research the community and guides."

The current opponent (AI.md: determinized Monte Carlo search with playouts, a tuned evaluation and a
learned net) is replaced as **the computer player** by a **rule-based, explainable heuristic player**: a
general playbook every hero uses, plus one playbook per hero. This file is the contract every agent builds
against. Research lives next to it: [GENERAL.md](GENERAL.md) (general heuristics, sourced) and
[heroes/](heroes/) (one `<heroId>.md` per hero, sourced). Code lives in
`core/game/src/main/kotlin/dev/quinton/goa/core/game/ai/heuristic/`.

## What stays, what goes

- **Goes, for the computer player:** `SearchAi` (sampling, playouts, racing, regret matching) and the
  learned net at every strength. `AiPlayers.forApp`, `forStrength`, `deterministic` and `forLevel` return
  the heuristic player (`HeuristicAi`). Their signatures stay: the app, the web worker and the online
  server's `Takeover` call them.
- **Stays, untouched:** the coach (`AiCoach` and friends) and the puzzle solver keep their own search for
  now, and the old player stays reachable as `AiPlayers.searchDeterministic(strength, seed)` (tests only)
  so tournaments can measure the new player against the old one. Do not delete old code in this round.
- **Stays, reused:** the engine, `GameState` read helpers (`reach`, `fastTravelDestinations`, `cardValue`,
  `heavyImmune`, `upgradesOf` …), `CardProfile` (printed numbers per card), `Determinizer`. Reuse low-level
  helpers freely; do **not** call `Searcher`, `Evaluator`, `StaticPolicy`, `LearnedNet` or anything that
  plays out games. The new player has its own hand-written evaluation (`HEval`). You may *read*
  `StaticPolicy`/`Evaluator`/STRATEGY.md for ideas and port logic you agree with into the new files.

## Non-negotiables (from AGENTS.md / AI.md)

1. **No hidden information.** Every answer reads only a determinized copy from the deciding team's view:
   `EngineSession.determinizeWith(team, seed, Determinizer.Options(...))` (see how `Searcher.determinize`
   builds the options: human teammates' facedown cards hidden, `hideAllOf` when asked). Strength 10 alone
   gets `knowHands = true` (ADR-062, owner's request). Threat and initiative reasoning about an enemy must
   use the cards that enemy **could** hold by public knowledge (its whole possible pool, optionally
   weighted), not the one sampled hand. `NoPeekingTest`'s guarantees must hold for the new player.
   Wave 3 (F1): after the reveal, strengths 9–10 play the rest of the turn out inside a look. At 10 an enemy's
   defense or discard in it is answered by its book from the known hand (ADR-062); at 9 the look prices it from the
   pool, as every look did before. Strategy round, Track W (`wave.even`): at 9 the turn then goes on, the defender's
   book answering on the team's determinized copy, and only the change from that answer to the turn's end is added
   to the pooled price, so a hit on a hero is judged at the same horizon as a minion; the strike itself stays pooled.
2. **Determinism.** An answer depends only on (seed, decision id, seat) and the session: no state carried
   between `choose` calls except caches whose key fully determines their value. Undo/restore must give the
   same answers. All randomness from `Random(mix(seed, decision.id, seat))`.
3. **Pure Kotlin, common code** (JVM + JS, ADR-043): no `java.*` in `src/main`, no threads.
4. **Never invent game data.** Card numbers come from the catalog (`CardProfile`, `EngineData`); card
   effects are resolved by the engine. Hero playbooks reference cards by catalog id and may encode
   *strategy* (when/where/why), never re-implement or restate card *rules* that the engine resolves.
5. **Generic first.** A hero with no playbook (or a future hero) must play sensibly from the general set.
6. **Fast.** Typical answer < 20 ms on the desktop JVM, worst case < 250 ms (the phone is 3–4× slower);
   no playouts. One-ply lookahead (fork, answer, finish the current action with the general policy,
   evaluate with `HEval`) is allowed for choices whose effect only the engine knows. Lifted for 9–10 by the
   owner's goal of 2026-11-21 (strength over cost, a card play under ~10 s): after the reveal a look plays the
   turn to its end, every hero by its own static book, and on turn 4 through the round's end (`CardSim.RestLook`,
   F1); about 15 ms an answer on the desktop JVM.
7. **Legal and total.** Any exception → `Fallback.choose(decision)`; every `DecisionKind` answered.

## Shape of the code

```
ai/heuristic/
  HeuristicAi.kt        AiPlayer: determinize once, build HContext, ask playbooks, pick (noise by strength)
  HeuristicProfile.kt   strength 1..10 → which layers are on, noise, slips (below)
  HContext.kt           per-decision facts: det session/state, seat, team, lane, threat map, reach,
                        enemy possible cards, card profiles, caches; helpers used by every playbook
  HEval.kt              hand-written position evaluation in coin-equivalents (lane, life, coins, levels,
                        hand, placement, exposure) + Σ hero playbooks' evalTerms
  Lookahead.kt          one-ply look: fork the det session, answer, finish the action with the general
                        playbook, HEval; used for opaque choices and to compare candidate actions. Every look
                        (this one, CardSim's, a teammate's prelude) stops at an enemy's answer that reads its
                        hidden cards (a defense, a forced discard): that is priced from the pool, never played
  TurnPlan.kt           at PLAY_CARD the hero forms an intent (kill X, clear minion, push, defend lane,
                        retreat, farm, fast travel, set up); later decisions of the same turn re-derive the
                        same intent from the state (deterministic, no memory) and follow it
  CardSim.kt            the card play's look (strength 5+): each card's own action resolved alone through the
                        engine, once per action it offers (and, from 7, four tries at its next choice), scored by
                        HEval with the enemies' threat split by who may act first; no enemy card is played (from
                        7 a computer teammate's known card that acts first resolves before ours, G14, unless
                        it makes an enemy defend or discard), and an attack on or a discard from a hidden hand
                        is priced from the pool when the defender would answer.
                        Track T (`team.order`): every computer teammate's known card is resolved in initiative
                        order, before ours or after it (from the best leaf of each action), and a teammate's hit
                        that reaches a hidden defense is priced there and its action ended, the next hit on that
                        hero meeting the thinner hand
  TeamPlay.kt           team play (strategy round, Track T): known teammate cards, the follow-up of a strip
                        (G113), the joint card choice (G115), the turn-4 finisher reserve (G116, G117)
                        The same look (TurnLook) scores actions, targets, spaces and defenses after the reveal
  General.kt            the general playbook: dispatches each DecisionKind to its rules (GENERAL.md):
  PlayRules.kt            PLAY_CARD (G1–G22)        ActionRules.kt  CHOOSE_ACTION (G32–G42)
  SpaceRules.kt           spaces, respawn, minion return  UnitRules.kt  CHOOSE_UNIT (G57–G68)
  DefendRules.kt          DEFEND (G77–G84)          CardRules.kt    discards, retrieves, level-ups
  MiscRules.kt            act first, tokens, options, confirms, numbers, minion removal
  TeamStrategy.kt       the team plan (GENERAL §4a): composition from the kits, KILL / PUSH / SCALE, each hero's
                        job; acts through one hook each in HContext.style, HContext.killWorth and HEval, and in
                        CardRules' upgrades (knobs `plan`, `plan.style`, `plan.race`, `plan.upgrade`, `plan.econ`)
  HFacts.kt             facts the general book derives (defenselessness, who still acts, lane deltas, the
                        lane-priced minion threat, push direction)
  HeroHeuristics.kt     the per-hero interface + registry (below)
  heroes/<Name>Heuristics.kt   one object per hero, registered in HeroPlaybooks
```

Scoring: every option of a decision gets `general score + hero adjust`, both in coin-equivalents (one
minion of lane advantage at an even lane ≈ 15, one coin = 1, a card in hand ≈ 2.5 — keep the units of
STRATEGY.md §2.2 so numbers mean the same everywhere). The player picks the max, or with the profile's noise.

Card numbers a playbook reads are `HCard` (attack, defense, initiative, reach, and the rest of
`CardProfile`). `CardProfile` stays module-internal. An enemy hand is not the one sampled hand:
`HContext.possibleCards` is the public pool, and `HContext.hand` is null until strength 10
(`knowHands`). `HeuristicProfile.lookahead` is how far the one ply goes: off, opaque choices only,
then actions and targets, then a close movement.

### The per-hero interface (`HeroHeuristics.kt`)

```kotlin
interface HeroHeuristics {
    val heroId: String                                   // catalog id, e.g. "arien"
    val style: HeroStyle get() = HeroStyle.DEFAULT       // role, preferred distance, aggression, upgrade order
    /** A hard rule: return one of d.options to play it outright, or null to let scoring decide. */
    fun override(ctx: HContext, d: Decision): Choice? = null
    /** Added to the general score of [choice] (coin-equivalents). Dispatch on d.kind / d.sourceCardId. */
    fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = 0.0
    /** This hero's own terms of the position evaluation (its tokens, pieces, counters, set-ups), from [team]'s view. */
    fun evalTerms(state: GameState, team: Team, heroId: String): Double = 0.0
    /** How dangerous this hero (as an enemy) is to a space/unit beyond printed reach (e.g. Brogan's straight lines). */
    fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double = 0.0
}
```

`HeroStyle` carries data the general playbook reads: `role` (Tank / Fighter / Ranged / Support / Assassin /
Pusher / SetUp), `idealDistance` to enemy heroes, `aggression` (0..1), `laneWeight`, `upgradeOrder`
(card ids or colours by priority, with item preference), `keepDefender` (hold a defence card). Hero
playbooks are registered in `HeroPlaybooks.forHero(id)`; unknown ids get `DefaultHeroHeuristics`.

Each hero playbook has a unit test file `heroes/<Name>HeuristicsTest.kt` with at least: (a) the hero plays
whole games without errors in 2v2 and 3v3 (`AiHarness`), (b) one or more hand-built positions where its
signature heuristic must fire (use `position/PositionBuilder` or a seeded game advanced to a decision).

## Strength 1–10 (`HeuristicProfile`)

| Strength | Layers on | Noise |
|---|---|---|
| 1–2 | general playbook only, no threat map, no lookahead; forgets minion modifiers sometimes | high temperature, slips 15–25% |
| 3–4 | + threat map (exposure), lookahead for opaque choices | medium, slips 8–12% |
| 5–6 | + hero playbooks, turn plans; the team plan's upgrades and economy (`plan.upgrade`, `plan.econ`, GENERAL §4a) | low, slips 3–5% |
| 7–8 | + initiative reading (enemy possible cards), focus fire / team coordination, card counting; the team plan's jobs and Life race (`plan.style`, `plan.race`) | very low |
| 9 | + two-turn plans (strip-then-kill, defender held back for a teammate), lane timing; the wave layer (Track W: one look horizon, farm first, the next push priced by presence, reposition and hold by the projected wave, `wave`); team play (Track T, G113–G118): a computer teammate's facedown card is known and played in initiative order inside the card look, before and after ours, its hits priced and carried to the next striker (`team.known`, `team.order`, `team.follow`); the teammate asked first plays its half of the best pair of cards (`team.joint`); a turn-4 finisher kept by the team (`team.reserve`); a same-team tie played out both ways (`team.tie`); after the reveal every look plays the rest of the turn (F1, `turnRest`) and every own square is looked at; the last card's escape on turn 4 (F3) | none on critical choices |
| 10 | 9 + `knowHands` (ADR-062): inside those turns, and in the card play's own-action look (`knowLook`), enemy defenses are answered from the known hand; two of its own choices inside every look are branched (`lookBranches = 2`, 9 and below 1). Measured h10 : h9 56.6% on 256 development games (RESULTS.md, wave 3) | argmax |

Defences and lane-deciding moves never slip (they feel broken, not human). Saves from before the slider
map levels to strengths as now (Easy 2, Normal 6, Hard 8, Expert 10).

## Measuring (tests, opt-in, `core/game/src/test/.../ai/heuristic/`)

- `HeuristicTournamentTest` (opt-in, env `AI_H_TOURNAMENT`): mirrored mixed line-ups (`AiHarness.mixedLineups`),
  heuristic strength vs heuristic strength, and heuristic vs old search (`Tournament.Side`). Report win
  rate, Wilson interval, per-hero rates, how games ended, mean answer time.
  `AI_H_FIRST` is the first line-up (a held-out set for a final table); a side may carry measurement knobs,
  `h10+sim=0+laneTiming=0` (`HeuristicProfile.tune`: a named weight, or a layer switched off).
- `HeuristicDiagTest` (opt-in, `AI_H_DIAG=h10:s8`): how side a loses heroes and games, turn by turn, with
  minion and hero kills per side and, for the heuristic side, each looked-at option's value.
- `HeroAblationTest` (opt-in, `AI_H_ABLATION=<heroId>`, a comma list or `all`): the same strength with and
  without one hero's playbook, the hero seated on both sides equally; a playbook must not lose (aim: > 55%).
  `AI_H_ABLATION_MODE=full,rules,style` splits notes from style, `AI_H_SKIP` takes fresh line-ups,
  `AI_THREADS` runs games at once, `AI_H_TUNE` gives both sides knobs.
- Knobs a side may carry (`h10+...`): every `profile.knob` name, plus `heroes=0` (no playbook at all),
  `patchLook=1` (static corrections inside a look), `placeProbe=0`, `allyFirst=0`, `laneFloor=0.85`.
  The integration round's log is [RESULTS.md](RESULTS.md).
- Always-on (cheap): all 32 heroes play 2v2 and 3v3 to the end at strengths 1, 5, 10; every `DecisionKind`
  answered; determinism (same seed → same game); no-peeking pairs as in `NoPeekingTest`.
  `HeuristicLegalityTest`: the answer comes from the real question's options even when the sampled copy
  offers one the real question does not.

## Building on oldboi (7 GB RAM, shared)

Never run `./gradlew` or `goa-gradle` directly. From the root of your checkout run the shared gate
`/tmp/claude-1000/-home-quinton-goa-companion/bb386302-b635-4d0f-906f-1e109ea75466/scratchpad/goa-build <tasks>`,
which waits for any other agent's build and then runs memory-capped. Prefer narrow tasks:
`:core:game:jvmTest --tests '*heuristic*'`; long tournaments with `AI_PROGRESS=1`. Do not start emulators.
Do not run the whole `:core:game:allTests` more than once at the end.
