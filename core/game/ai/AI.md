# The computer opponents and the coach (`core.game.ai`)

ADR-035 point 3, ADR-039, PRODUCT §9. The strategy knowledge behind it is [STRATEGY.md](STRATEGY.md);
this file is how the code uses it, what each strength does, what it costs and how strong it is.

## Architecture

The app binds these in `core/data/.../game/PlayEngineModule.kt`:
`AiPlayers { strength -> forStrength(strength, seed, AiSampleThreads) }` (each answer's samples on three
background threads, "Budgets") and `AiCoach(factory)`. A seat's
`Controller.Ai` carries its strength 1–10; a save from before the slider carries only the level and
plays as the level's strength (Easy 2, Normal 6, Hard 8, Expert 10, ADR-039).

| File | What |
|------|------|
| `AiPlayers.kt` | `AiPlayers.forStrength(strength, seed)` (production, wall-clock capped) and `deterministic(strength, seed)` (tests), `forLevel` for a level alone; the ten `LevelProfile`s and `TUNED`, the evaluation of the searching strengths; `SearchAi`, the one `AiPlayer`; `Fallback` (answers from the `Decision` alone) |
| `Search.kt` | `Searcher`: determinize → fork → answer an option → playout to a horizon → evaluate; option pruning, racing, the playout policy, the one-ply "greedy" policy, the opponent model, public card memory; `SampleRunner` (the samples of one answer, sequential unless the app passes threads); `Hunt` (the exploiter's taste, tests); `Picker` (noise) |
| `Evaluator.kt` | The static evaluation in coin-equivalents (STRATEGY §2) as a sum of named `Term`s with scales (`EvalKnobs`), and `Ctx`, its per-state cache, which also scores "this hero standing on that space" without forking |
| `StaticPolicy.kt` | Scores every option of every `DecisionKind` from one state, no fork (STRATEGY §3): the playout policy, the opponent model and Easy |
| `CardProfile.kt` | Per-card numbers the heuristics need (attack, reach incl. "Before the attack: Move N", discard-or-defeat, blocks, a rating), read once from the catalog; nothing hero-specific |
| `Features.kt`, `LearnedEval.kt`, `LearnedWeights.kt` | The learned evaluation (below): team-size invariant features of a state, the net with its per-hero and per-card parts (`LearnedNet`, `NetEvaluator`, `PositionEvaluator`, `EvalSource`) and its generated weights (`LearnedCodec`: Base64 floats) |
| `AiCoach.kt` | `AiCoach(factory)`: `nudge` (Hard-level analysis from the human team's view) with `Explainer`, `prepare` (the same analysis before the answer); `turningPoints` with `TurningPoint`; `suggest`, `review` |
| `CoachFacts.kt`, `CoachSuggest.kt`, `CoachReasons.kt`, `LineBuilder.kt`, `ThreatFinder.kt`, `RoundPlanner.kt`, `CoachReviewer.kt`, `WinChance.kt` | Suggestions and the post-game review (ADR-051, "The coach") |
| `engine/Determinizer.kt` | Samples hidden information (below) |

One answer, every strength from 5 up: draw *N* determinized copies of the game from the deciding team's
view; on each, fork once per option (big option lists — move destinations — are first cut to the
best 6–10 by static score), answer it, play on to the horizon with the playout policy and evaluate;
average per option (Hard: mean − 0.3 sd for card plays, against the spread of guesses) and pick with
a little softmax noise among the near-best. All options of a sample share the same determinization
(common random numbers), so differences between options are not hidden-card noise.

**Never reads hidden information.** Every answer starts with
`EngineSession.determinizeWith(team, seed, options)`; the evaluation, the policies and the coach read
only those sampled copies (and the decision itself). The real session's `state` is never read.
`NoPeekingTest` builds pairs of sessions that look identical to the deciding seat but differ in the
other team's hidden cards (a facedown play; which colour an enemy upgraded under the same item icon;
hidden hands after the reveal), asserts the seat's views are equal, and checks that every level gives
the same answer for the same seed.

**Generic.** Nothing names a hero. Card effects are resolved only by the engine (forks); the
heuristics read printed values and a few text patterns (`CardProfile`). A hero added later is played
through the same `Decision` options; unusual card shapes degrade to "plain card" heuristics and the
determinizer's fallback. Two generic devices carry the hero mechanics the heuristics cannot read
(2026-10-24, measured over all 32 heroes):

- **Opaque choices are looked at one ply deep** (`Searcher.opaque`, `LevelProfile.shallow`): a
  "Choose one" bullet, a confirm or number, a token, a space that is not a hero's or a minion's
  move (placing a Zombie, the Turret, Pyro, an Illusion; moving a token), a unit choice that is not
  an attack target ("Target …": swaps, pushes, pulls, Mind Grip's hero, which of Razzle's bodies
  acts) and another hero's card (Hurry up!). Inside playouts, and for Easy at the root, each option
  is answered on a fork, the action finished statically and the result evaluated. Before this every
  "Choose one" took its first bullet and token placements were scored as if the hero moved there.
- **What the evaluation sees of a hero's set-up** (below): its own pieces on the board, its public
  dashboard counters and several bodies. The only names it knows are the counter keys.

**Determinism.** An answer depends on the seed, the decision id and the seat (not on the order of
calls), so undo and restore give the same answers. The production player stops sampling at the
device budget (`budgetMs` for card plays, a third of it for other decisions); on the desktop that cap
is rarely reached. `AiPlayers.deterministic` removes it. `cancelled()` is checked before
every playout (a playout is 0.1–30 ms on the desktop).

**Samples on several threads (v0.9).** `Searcher` runs the samples of one answer through a
`SampleRunner`: the engine has no threads (it is shared with the web build, ADR-043), so the default
runs them one after the other; the app passes `AiSampleThreads` (`core/data` `PlayEngineModule`, up to
three daemon threads). The first sample fixes the candidates; the rest run in batches of the runner's
width, each on its own column of the value table and its own determinized copy, combined in sample
order, so an answer without the wall-clock cap is the same with any runner (`SampleRunnerTest`). On
the desktop three threads answered 2.15× faster (3v3 strength 10, card play mean 296 → 137 ms, p95
655 → 286 ms). The deadline is checked between batches.

**Search copies are quiet (v0.9).** The determinized copies and their forks build no explanations
for a player at their questions (`EngineSession.quiet`: no "why not" reasons or Primary previews at
`CHOOSE_ACTION`; the options are the same checks), and forks answered at once share the shown state
instead of copying it (`forkForAnswer`). Same answers for every seed (`FingerprintTest` hashes equal
before and after); a 3v3 strength-10 game about a quarter faster. What is left of the cost: one-ply
looks in playouts (87% of the time), and in them the engine's probe of whether a Primary can start
(a state copy per `CHOOSE_ACTION`, 26%); the evaluation is 12%.

**Racing (v0.9, built, off).** After four samples an option whose paired gap to the best (common
random numbers: the same determinizations), less two standard errors, exceeds a margin is not played
out any more (`LevelProfile.race`); at least two stay. With a margin of 3 coin-equivalents at strength
10 it measured 51.0% (41–61%, 96 games in 3v3) for 22% fewer playout steps (card play mean 296 →
227 ms, p95 655 → 456 ms on the same games), but the packages it was part of measured below even
("Team sizes"), so no strength uses it.

### The determinizer (`engine/Determinizer.kt`)

Item cards are private (only their icons are public, BGG 3590819), so which card each enemy holds
after a level up is hidden. For each enemy hero with the standard one-card-per-colour shape:

1. enumerate every **upgrade history** consistent with the public facts: the level (Tier II before
   Tier III, p.15), the multiset of item icons, the faceup cards (resolved, discarded, revealed) and,
   with memory (Expert), the cards seen faceup since that hero's last level up (`Options.seen`, from
   the log: reveals, actions, defenses);
2. draw one uniformly; deal its kept cards and the hidden basic cards to the hidden places (hand,
   facedown cards, the unrevealed played card) in a canonical order; its other cards become items,
   the rest the deck; hand stamps are cleared (they would tell the order cards were moved);
3. before the reveal, deal the played card again from the hand pool, weighted by the opponent model
   (`Options.playWeight`: softmax of the hero's own static card scores, τ = 1.5, mixed with 15%
   uniform; Normal and up — Easy guesses uniformly);
4. human teammates' unrevealed played cards are dealt again too (`Options.hidePlayedOf`): a facedown
   card is hidden even to a teammate (p.4). AI teammates count as having announced their plan (legal
   table talk before the reveal).
5. the coach speaks to one player, who may not see a teammate's cards (p.4): for it every teammate but the
   seat asking is resampled like an enemy, hand, items and facedown cards and tokens (`Options.hideAllOf`,
   `LevelProfile.hideTeammates`; `CoachTruthTest`). A computer team's own search keeps the rule above.

Heroes with other shapes (Gydion's spellbook, extras, cards moved by text) fall back to swapping
identities within colour and Tier among hidden places. `HeroView.itemStats` is now sorted by stat,
because the deck order of the tucked cards told which colour each came from.

## Evaluation (STRATEGY §2)

`score = Lane + Life + Σ our heroes − Σ their heroes`, zero-sum, coin-equivalents; a finished game is ±1000.

- **Lane:** P(win the lane) from the Battle Zone offset and the Wave counters (the §1.1 recursion),
  with the next push's probability `σ(1.1 · projected margin)`; the projection subtracts each side's
  Push Potential (0.5 per attack card left for heroes within 6 spaces of the Battle Zone; 0.35 at
  three heroes a side since v0.9, "Team sizes"), and a
  projected Minion Battle that empties a side counts as ~0.95. 60 per 1.0 of P(win), plus 1.5 per
  minion of margin. One minion of advantage at an even lane is worth ~15: the AI is lane-first, as the
  designer's "Ruling the Waves" says it should be.
- **Life:** 20 / 12 / 8 / 5… for the last, second-last… counter.
- **Per hero:** coins + level costs + 3 per level (+1 when a level is due), Ultimate +8; cards in
  hand against the plays left this round (2.5 per needed card, the spare 3 / 2 / 1.5 / 0.8 by turn,
  −3 per missing card) plus 0.12 × card ratings; **placement**: −0.55 per space outside the Battle
  Zone (+1 inside; a path of 3 spaces ≈ an action, BGG 2898177), **minion opportunity** (an attack card
  that reaches an enemy minion: 3 this turn, 1.5 later), and **exposure** (H4): for each enemy card
  that can reach the space this turn (revealed card: weight 0.6) or next turn (0.22), whether its
  Attack total beats the hero's best Defense there (items, minion modifiers, Block answers; a
  discard-or-defeat text against an empty hand), giving
  `P(defeat) × (Life value + level coins + assists + 5 tempo) + P(discard) × card value`. In 3v3
  (v0.9) a hero with no card left to defend with, now or after the card it still has to play, counts
  this in full even where the term is scaled down ("Team sizes").
  Off the board: −4 plus the Throne's distance to the Battle Zone. A hero with several bodies
  (Razzle's Stunt Doubles: any of them may act, a defeat of any defeats the hero) counts the best
  presence and reach and the worst exposure over its bodies.
- **Counters** on a hero's dashboard (`HeroState.counters`, public): spells ready in a spellbook
  1.5 each up to 5 (every Gydion card but the refill casts from it; the book starts empty), spells
  already cast 0.1, Wish casts 10.
- **Pieces:** each hero's own pieces on the board (tokens, the Turret, Pyro; not its bodies), the best
  4 per hero: 1.2 if it lasts beyond the round, else 0.8 × the turns left / 4; × (1 − 0.12 per space
  from the Battle Zone, at least 0.25); +0.4 within 2 spaces of an enemy hero. Zero-sum like the rest:
  enemy pieces count against us, so Clear actions on them have a reason. A scale of 0.4 or 0 for
  round-only pieces (Illusions, Rocks, Ice…) measured no better (46%, 49% over 192 games).

**Terms and their scales (v0.7, ADR-039).** The score is the sum of twelve named parts (`Term`: lane,
minion margin, pieces, Life, coins, levels, counters, hand, hand rating, presence, minion
opportunity, exposure), each Red minus Blue, each multiplied by `EvalKnobs.scales` (default 1). The
weights inside them are knobs too (`presencePerSpace`, `nowWeight`, `laneWeight`, `killTempo`,
`handRating`…), so a tournament can try any of them without a build (`ExperimentTest`).
`FitTest` plays self-play games, records the parts at the start of every turn and who won, and fits
P(Red wins) = σ(Σ mᵢ · partᵢ) by logistic regression; equal mᵢ would mean the parts are weighed
right against each other. Over 12 096 positions from 400 Hard self-play games it fitted exposure at
**−0.04** of the lane's weight, the largest part by spread (rms 28 coin-equivalents) and the one that
says least about who wins, and minion opportunity at **2.35×**; the held-out log-loss fell from
0.620 (one slope on today's score) to 0.590. The searching strengths (5–10) therefore use `TUNED`:
exposure × 0.3, opportunity × 2 (the static strengths 1–4 keep the hand-made weights, which their
designed mistakes are built on). A refit at Expert on the tuned evaluation (4 373 positions) still
put exposure near 0 and opportunity near 3.7×, but neither 0.15 nor 3.5 measured better. What the
fit and the tournaments say together: a hero's danger matters through what the playouts find
(an attack that lands this turn), not through a static guess of next turn's threats.

## Learned evaluation (ADR-047 part B, ADR-049)

The net described here first is v0.9's; v0.10 extended it ("v0.10: a net that knows heroes, cards and
pieces", below), and that is the checked-in one. v0.9's net and features are kept in test sources as
the baseline of every later measurement (`net=v09`).

Strength 10 (Expert) scores the end of every playout with a small learned network instead of the
hand-weighted sum above (`LearnedEval.kt`, `Features.kt`, `LearnedWeights.kt`;
`AiPlayers.LEARNED_FROM`); the one-ply looks inside playouts, the static policy, the opponent model,
strengths 1–9 and the coach keep the hand evaluation. The search is unchanged: the same samples,
candidates, horizons and noise. `LevelProfile.learned` (`EvalSource`) switches it per profile, so a
tournament can try it at any strength. The net was trained beside v0.8's `TUNED`, and both its
inputs and strength 10's hand parts keep that knob set (`Features.KNOBS` / `SCALES`,
`AiPlayers.TUNED_WITH_NET`), not part A's 3v3 knobs: measured, "The two parts together".

**Features** (`Features.kt`, pure Kotlin, commonMain-safe). One state, one team's point of view,
team-size invariant so one net plays 1v1 to 5v5: every hero gets the same 52 numbers (on the board,
presence and distance to the Battle Zone, progress along the lane; level, coins, a level due,
Ultimate, lowest Tier; hand size against the plays left, spare and missing cards; the printed
numbers of the cards in hand — attack cards, best Attack and reach with items, best Defense,
defenders, Block, highest and lowest Initiative, best Movement, the rating; the played card's
Initiative, Attack, reach, Movement; the five item bonuses; the hand evaluation's exposure,
opportunity, defeat and discard costs and the minion modifier at its space; enemies within 2 and 4,
allies within 2, enemy and own minions within 2, the nearest enemy; enemy cards that can reach it
and beat its defense this turn and next; its dashboard counters, its pieces, whether it has acted,
enemies acting after it, extra bodies). They are pooled per team as sum and max (own team, enemy
team: 4 × 52), then 40 global numbers: the hand evaluation's twelve terms and its tuned score (the
net can learn to trust them), pushes to win and to lose, Wave counters, match points, minions by
type per side, Push Potential per side, Life counters and their worth, "one defeat loses", round,
turn, revealed, end of round, team sizes, the coin. 248 inputs. Like `Evaluator` it reads only the
state it is given — a determinized copy in a search — and names no hero; `NoPeekingTest` covers it
through the strengths.

**Net.** N(x) = MLP(x) + w·x: 248 → 64 → 32 → 1 with ReLU plus a linear skip ("wide and deep"),
applied to both teams' features; P(Red wins) = σ(N(Red) − N(Blue)), so swapping the teams negates
the score exactly. The score is the logit × the coin scale (the hand evaluation's fitted slope,
1/k ≈ 39 coin-equivalents per unit), so the search's noise, risk and the ±1000 of a finished game
keep their meaning. Inference reads raw features (the training standardization is folded into the
first layer) and skips zero inputs.

**Data** (`LearnedSelfPlayTest`). Self-play on training line-ups only (`Teams.TRAIN`, a seed family
of its own): 2v2 ×1, 3v3 ×2, 4v4 ×1.2, 5v5 ×0.4, every hero seated equally often, ≤ 4 threads. Each
team plays a variant drawn per game: strength 7 ×4, a noisy 7 (τ 1.5 within 4) ×2, an aggressive 7
that ignores its own exposure and values Life counters twice (so walking into danger gets punished)
×2, strength 6 ×1, strength 8 ×1. Generation 1 replaced the plain and noisy 7s and the 8 with the
generation-0 net. A game is stored as its setup and answers and replayed to record positions: every
turn boundary (what a playout to the end of the turn ends on) and a third of the moments after a
card resolved (what a one-ply look sees).

**Targets and training** (`LearnedTrainTest`, `Trainer.kt`, pure Kotlin, seeded). λ-returns over a
game's positions, bootstrapped from a teacher: G = (1 − λ)·P_teacher(next) + λ·G(next), the result
at the end. The first teacher is the hand evaluation (σ(k · tuned score)), then the previous net
(fitted value iteration). Cross-entropy, AdamW (lr 3e-4, weight decay 1e-3 on the weights), batches
of 512, early stopping on a validation split. The split is by line-up (line-up % 10: 8 validation,
9 test, the rest training).

**What measured.** Every candidate net was screened at strength 7 (cheap: 192 games in 2–3 minutes)
in 3v3, mirrored, on 96 line-ups of the tuning family (`Teams.TUNE`); the chosen one was then measured
at strength 10 on the test family (`Teams.TEST`), which no training or choice used. 95% Wilson
intervals.

| Candidate (strength 7 with it, 3v3) | Against | Result | Kept |
|-------------------------------------|---------|--------|------|
| Trained on the game result alone (λ 1) | strength 7 | 37.5% (31.0–44.5%) | no |
| λ 0.95 / 0.85 from the hand evaluation | strength 7 | 51.6% / 58.9% | no |
| λ 0.7 / 0.5 / 0.3 / 0 | strength 7 | 68.2% / 65.6% / 57.3% / 63.5% | λ 0.7 |
| λ 0.7 from the λ-0.7 net instead of the hand evaluation (`g0t07`, generation 0) | strength 7 | 72.4% (65.7–78.2%) | yes |
| The same iterated once / twice more on the same games | strength 7 | 59.9% / 65.1% | no |
| Generation 1 (gen-0 + gen-1 games, λ 0.7 from the hand evaluation; `g1a`) | generation 0 | 58.3% (51.3–65.1%) | **yes** |
| Generation 1 with seed 2 / λ 0.6 / λ 0.8 / the teacher iterated | generation 0 | 61.5% / 55.7% / 55.7% / 51.6% | no |
| Generation 2 (all games / gen-1 + gen-2 games) | generation 1 | 52.6% / 47.9% | no |
| The net also in the one-ply looks inside playouts (`EVERYWHERE`) | the net at playout ends | 45.3% (38.4–52.4%) | no |

What this says: the result alone is the target that predicts results best (held-out log-loss 0.552
against the hand evaluation's 0.581 on generation 0) and plays worst. A search picks the option whose
leaf the evaluation overrates, so a sharp, noisy evaluation is exploited by its own search. Targets
bootstrapped from the hand evaluation a few positions on (λ 0.7 per recorded position: about a turn) give a smooth
evaluation that sees further than the hand one, and that is what plays better. A third generation
of self-play added nothing measurable; generation 1 is checked in.

**Strength 10 with the net against strength 10 with the hand evaluation** (`TeamTournamentTest`,
96 test line-ups mirrored = 192 games each, every hero seated equally often):

| Team size | Wins | Rate | 95% interval | ≈ Elo |
|-----------|------|------|--------------|-------|
| 3v3 | 130–62 | 67.7% | 60.8–73.9% | +130 |
| 2v2 | 129–63 | 67.2% | 60.3–73.4% | +125 |
| 4v4 | 125–67 | 65.1% | 58.1–71.5% | +110 |

Earlier, generation 0 at strength 10 on tuning line-ups: 58.3% (48.3–67.7%) over 96 games.

**Why only strength 10.** The net helps every searching strength (7: 68–72%), but with it at 5–10
the steps between neighbours shrank on the ladder's line-ups 900–995 (2v2, 192 games each): 6 over 5
55.7% (48.7–62.6%), 7 over 6 67.2%, 8 over 7 56.8% (49.7–63.6%), 9 over 8 62.5%, 10 over 9 51.6%
(44.5–58.5%). Giving it to Expert alone keeps the measured ladder 1–9 as it was and makes the top
step the biggest: 10 over 9 75.0% (68.4–80.6%), 192 games.

**Held-out log-loss** against the real results (test split of generations 0 + 1: 41 756 positions
from 640 games of line-ups never trained on): the tuned hand evaluation (one fitted slope) 0.597, a
logistic fit of its twelve terms (as `FitTest`) 0.591, the net 0.580; in 3v3 0.583 / 0.572 / 0.564.

**Cost.** One evaluation of a 3v3 position on the desktop: the features about 40 µs (they include the
hand evaluation's terms, about 21 µs alone), the net a few µs more — well under a playout (0.1–30
ms). Decision times at 3v3 (`LearnedTimingTest`, 4 games each on test line-ups, one at a time, no
wall-clock cap, desktop shared with other work): card plays 395 ms mean / 833 ms p95 / 1.65 s max
with the net, 415 / 822 / 1.90 s with the hand evaluation; moves 232 / 584 ms against 203 / 568 ms.
The net costs nothing measurable per decision (one evaluation per playout, against thousands of
engine steps); 3v3 itself costs about twice 2v2 (Budgets), which the device budget, not the
evaluation, has to answer (ADR-047 part A).

**How to retrain.** One command trains on the stored self-play games and writes the checked-in
weights (seeded: the same data, seed and thread count give the same net, bit for bit; about 2 minutes
of training on six threads for 10 400 games, once their features are cached). The checked-in net, with
`<dir>` = `D:\.goa-qa\v09\a2\data` and `<dir2>` = `D:\.goa-qa\v10\value\data`:

```
AI_HEAP=8g AI_THREADS=6 AI_SEED=3 AI_CACHE=<a directory> AI_TRAIN_WRITE=1 \
  AI_TRAIN=<dir>/gen0/games-0.jsonl,<dir>/gen1/games-1.jsonl,<dir2>/gen2/games-2.jsonl \
  ./gradlew :core:game:jvmTest --tests '*LearnedTrainTest' --rerun
```

(defaults: λ 0.7, teacher `hand`, 64 → 32, lr 3e-4, weight decay 1e-3, table decay 1, seed 1, 4 threads;
`AI_NETS` is where named nets are saved, `AI_TRAIN_NAME` their name; `AI_TABLES=0` and `AI_BOARD=0`
leave out the v0.10 parts; `AI_WRITE_NET=<name>` writes a saved net as the weights without training).
The games themselves are reproducible too
(deterministic players, seeded per game; generation 0 was played before v0.9's two engine
fixes, which only changed choices met inside search forks, and its games still replay exactly). Generation 2 was
played with the net trained on generations 0 and 1 (`AI_TRAIN=<gen0>,<gen1> AI_TRAIN_NAME=a1`):

```
AI_SELFPLAY=4000:<dir2>/gen2/games-2.jsonl AI_FIRST=6400 AI_THREADS=6 AI_SIZES='2*1,3*2,4*1.2,5*0.4' AI_EPS_UPGRADE=0.3 \
  AI_HERO_WEIGHTS='gydion*3,nebkher*3,trinkets*2,brogan*2,min*2,dodger*2,xargatha*2,bain*2,cutter*1.5,emmitt*1.5' \
  AI_VARIANTS='s7+net=a1*4,s7+net=a1+temp=1.5+gap=4*2,s7+xEXPOSURE=0+xLIFE=2+tempo=10*2,s7+hunt=1/2/1.3+focus=true*2,s8+net=a1*1,s7*1' \
  ./gradlew :core:game:jvmTest --tests '*LearnedSelfPlayTest' --rerun
```

Generations 0 and 1:

```
AI_SELFPLAY=3200:<dir>/gen0/games-0.jsonl AI_SIZES='2*1,3*2,4*1.2,5*0.4' \
  ./gradlew :core:game:test --tests '*LearnedSelfPlayTest' --rerun
AI_SELFPLAY=3200:<dir>/gen1/games-1.jsonl AI_FIRST=3200 AI_SIZES='2*1,3*2,4*1.2,5*0.4' \
  AI_VARIANTS='s7+net=g0t07*4,s7+net=g0t07+temp=1.5+gap=4*2,s7+xEXPOSURE=0+xLIFE=2+tempo=10*2,s7*1,s8+net=g0t07*1' \
  ./gradlew :core:game:test --tests '*LearnedSelfPlayTest' --rerun
```

where `g0t07` is the generation-0 net: `AI_TRAIN=<gen0> AI_TRAIN_NAME=g0l0.7` then
`AI_TRAIN=<gen0> AI_TEACHER=g0l0.7 AI_TRAIN_NAME=g0t07` (each `--tests '*LearnedTrainTest'`). A change
to `Features` changes `Features.SCHEMA_HASH` and `LearnedNetTest` fails until the net is retrained;
after retraining, measure it against the previous one (`AI_TEAMS=s7+net=<new>:s7+net=<old>`,
`TeamTournamentTest`) and keep it only if it wins. New weights also change strength 10's transcript in
`GameParityTest`: re-pin it from the JVM and check that Node agrees (`:core:game:jsNodeTest`).

### v0.10: a net that knows heroes, cards and pieces (ADR-049)

The goal: strength 10 plays better with every hero and stops trading heroes for the lane. v0.9's 52
numbers per hero are pooled per team, so the net cannot tell what a hero or a token is for.

**Diagnosis first.** Strength 10 over 8 won 75.3% (70.2–79.9%, 300 tune games). Least uplift: Gydion
+35%, NebKher +37%, Brogan +39%, Min, Dodger and Xargatha +41%, each ±24 points (28 games a side), so
this names suspects, no more. In three logged games NebKher held on about 31% of its actions and Gydion
on 21% (few games). `BlindSpotTest` counted the questions the search cannot tell apart at all (the
options within 0.01 of each other, 18 games, 3v3): about 7% overall, but 71% of "who acts first" on tied
Initiative, and nearly all of Snorri's rune under a slot, Bain's Bounty and Tali's "names a color". Those
answers are coin tosses, because the effect comes after the playout ends.

**The net.** N(team) = MLP(pooled features) + linear skip + Σ over the team's heroes of a linear
correction on that hero's 52 numbers and a constant (a shared net plus small per-hero residuals) + a
value per card id and place (in hand, played, resolved face up, an item, in an extra place such as a
spellbook). The tables start at zero and decay towards it (AdamW, table decay 1). Still
P(Red) = σ(N(Red) − N(Blue)), exactly antisymmetric, team-size invariant, the coin scale, one evaluation
at each playout's end. A hero or card the tables do not know adds nothing.

**New inputs** (374 instead of 248): per team, each piece kind's count, how near the Battle Zone and how
near the enemy heroes it stands (within 6 spaces, the nearer the more; 18 kinds with "other"), the
markers its heroes carry per kind, and its dashboard counters per key. They read `PieceState.kind` and
friends, keys as `Evaluator.counterValue` knows them; no logic depends on a hero. A facedown token's
kind in a search copy is the determinizer's guess (enemy kinds are shuffled among themselves), so
`NoPeekingTest` still holds. Those inputs get a floor under their training scale, so a rare piece does
not shout.

**Weights** are now Base64 text of the 32-bit floats (`LearnedCodec`), so a value keeps its exact bits
in 5.3 characters: 33 900 floats in 209 KB of source (v0.9: 18 300 floats in 243 KB).

**Cost.** One evaluation of a 3v3 position, measured side by side on the shared desktop
(`LearnedNetTest`): 107.5 µs against v0.9's 80.2 µs, 1.34× (limit 1.5×); the hand evaluation 20.9 µs.
That is one evaluation per playout against thousands of engine steps, so tournaments hold both sides to
the same work; `TimingTest` was not rerun on the new net.

**Data.** Generation 2 (4 000 games, "How to retrain"): the net in self-play, the heroes with the lowest
uplift seated more often, the kill-punishing variants (the aggressive 7, `Hunt`) in 40% of the mix,
ε-exploration at the level-up questions. In it the net at strength 7 won 63.4% of its seats, the
aggressive 7 26.6%, `Hunt` 40.6%. 39% of those games ended on Life counters. Targets stay λ 0.7 from the
hand evaluation: result-only targets lost before (37.5%, above).

**What measured.** Screened at strength 7, 3v3, tune line-ups, against v0.9's net:

| Net (seed 1 unless noted) | Against v0.9 | Heroes lost a game (v0.9's) | Kept |
|---------------------------|--------------|-----------------------------|------|
| Generations 0 + 1, tables and board (`a1`) | 56.8% over 192, 55.2% (50.7–59.6%) over 480 | not recorded | generated the data |
| The same, board only / tables only | 51.0% and 54.4% / 59.9% and 54.2% (192 + 480 each) | not recorded | no |
| The same, table decay 0.3 / 3 | 51.6% / 51.6% (192) | not recorded | no |
| Generations 0–2, **neither part** (`p0`) | 48.4% (43.5–53.4%, 384) | 1.57 (1.67) | no |
| Generations 0–2, board only | 57.8% (52.8–62.7%, 384) | 1.53 (1.74) | no |
| Generations 0–2, tables only | 57.8% (52.8–62.7%, 384) | 1.41 (1.73) | no |
| Generations 0–2, tables and board, seed 1 (`b1`) | 56.0% (51.0–60.9%, 384) | 1.33 (1.70) | no |
| The same, seed 2 | 56.8% (51.8–61.6%, 384) | 1.41 (1.85) | no |
| The same, **seed 3** (`b1s3`) | 61.5% (56.5–66.2%, 384) | 1.41 (1.84) | **yes** |
| The same, λ 0.6 / λ 0.8 | 55.5% / 53.4% (384) | 1.54 (1.77) / 1.43 (1.61) | no |
| Generations 1 + 2 alone | 55.2% (50.2–60.1%, 384) | 1.45 (1.77) | no |
| `b1` against `a1` | 46.9% (41.9–51.9%, 384) | 1.51 (1.66) | — |

What this says: a retrain without the new parts does not beat v0.9, and each part alone does. Together
they are not clearly better than either alone at strength 7, but they lost the fewest heroes in `b1`.
Generation 2's games did not make a stronger net than generations 0 + 1 head to head (`b1` against `a1`
above), though it lost fewer heroes, which is why it stayed. The seeds' spread is about the noise of 384
games, so `b1s3`'s lead over `b1` is unproven at strength 7; both went to strength 10.

**At the phone's work** (`s10+work=15500` both sides, tune line-ups, `AI_SPRT=0.55`): `b1` 55.8%
(50.9–60.5%, 400 games, undecided), 1.66 heroes lost a game against 2.05; `b1s3` 60.4% (53.4–67.1%, the
test decided H1 after 192 games), 1.56 against 2.09. `b1s3` is the checked-in net.

**Confirmed once on the test family**, new strength 10 against v0.9's, the same work on both sides,
95% Wilson intervals:

| Team size (work) | Wins | Rate | 95% interval | Heroes lost a game, new / v0.9 | With no card left to defend | How the new 10 won (last push / Throne / Life) | How v0.9's won |
|------------------|------|------|--------------|--------------------------------|-----------------------------|------------------------------------------------|----------------|
| 3v3 (15 500) | 242–158 | 60.5% | 55.6–65.2% | 1.71 / 1.96 | 205 / 221 | 113 / 49 / 80 | 78 / 22 / 58 |
| 2v2 (15 500) | 114–78 | 59.4% | 52.3–66.1% | 0.91 / 1.16 | 47 / 61 | 61 / 24 / 29 | 40 / 15 / 23 |
| 4v4, Forgotten Island (13 200) | 109–83 | 56.8% | 49.7–63.6% | 2.29 / 2.49 | 170 / 169 | 38 / 18 / 53 | 23 / 15 / 45 |

The aim of 60% in 3v3 with no more heroes lost is met; 4v4's interval includes even. Against v0.8's
strength 10 the v0.9 net lost 1.98 heroes a game in 3v3 where v0.8's lost 1.29, so 1.71 is better, not
yet back there (a different opponent, so not a direct comparison).

**Against the kill-hunter** (`s10+learned=HAND+hunt=1/2/1.3+focus=true`, uncapped, against the capped
side, 3v3, 192 games on the same test line-ups):

| Against the kill-hunter | Wins | Rate | 95% interval | Heroes lost a game (the hunter's) | With no card left to defend (the hunter's) | Games lost on Life counters |
|-------------------------|------|------|--------------|-----------------------------------|---------------------------------------------|------------------------------|
| **New strength 10** | 142–50 | 74.0% | 67.3–79.7% | 2.10 (1.70) | 116 (124) | 42 of 50 |
| v0.9's strength 10 | 119–73 | 62.0% | 54.9–68.5% | 2.27 (1.49) | 131 (112) | 51 of 73 |

The new net wins more of these games and trades better: it loses fewer heroes to the hunter and takes
more of the hunter's. The hunter still takes more heroes than it loses.

**The scorecard** (new over v0.9, 400 test games, about 38 games a hero a side, so ±22 points each): no
hero's uplift is negative. Worst: Garrus +0.0%, Silverarrow +10.3%, Dodger +10.5%, Xargatha +10.8%,
Snorri +11.1%. Best: Gydion +41.0% (+20.9, +61.2), Cutter +36.1%, Mortimer +34.2%, Takahide +31.6%, Swift
+30.6%. Of the heroes strength lifted least: Gydion +41.0%, Trinkets +28.2%, Min +27.0%, NebKher +20.0%,
Brogan +18.9%, Xargatha +10.8%, Dodger +10.5%. Fourteen intervals lie wholly above zero. The set-up heroes
gained the most, what the tables and token inputs were for; strength 10 over 8 was not measured again,
so whether the gaps between heroes narrowed at that step is not known.

**The ladder:** 10 over 9 in 3v3, 84.4% (75.8–90.3%, 96 test games, uncapped). Strengths 1–9 give the same
answers (`FingerprintTest`, strengths 7 and 9, one 3v3 game each, hashes equal before and after), v0.9's
net through `net=v09` too (hash −1133468300).

**Upgrade tables.** Monte Carlo control at the level-up questions: ε 0.3 in generation 2, the value of a
choice = the game's result minus the net's chance just before it (`UpgradeTableTest`), 138 946 answers in
672 (question, card) cells. "Which card to upgrade" showed nothing beyond chance (mean |z| 0.76, against
0.80 for pure noise); "which card to keep" somewhat (0.92, 36 of 384 cells over z 2). Ten cells with
z ≥ 2.5 and at least 100 answers, played as a table at strength 7: 50.6% (47.5–53.7%, 1 000 games), 1.61
heroes lost against 1.64. Even, as earlier evidence said (49%), so no table ships and no further
generations were played. Strength 10's upgrades stay with the search, whose net now has the card table.

**Tried and dropped in v0.10:**

| Candidate | Result | Why dropped |
|-----------|--------|-------------|
| Searching a tied answer again to the end of the next turn (the generic fix for "the effect comes later"), strength 7 | 52.1% (45.0–59.0%, 192), 48.3% (45.2–51.4%, 1 000) | Even. Not tried at 10: the later turns are played by rules of thumb, as in every longer horizon so far |
| The upgrade table above | 50.6% (47.5–53.7%, 1 000) | Even |
| Table decay 0.3 / 3 | 51.6% / 51.6% (192), against `a1`'s 56.8% on the same games | Not better; log-loss the same |
| λ 0.6 / 0.8 on the new data | 55.5% / 53.4% (384) | Not better than λ 0.7 |
| Generations 1 + 2 without generation 0 | 55.2% (384) | Not better |
| A third new generation | Not played | `b1` did not beat `a1` head to head; the time went to the gates |

Held-out log-loss against the real results: on generations 0 + 1's test split `a1` 0.5768 (3v3 0.5613)
where v0.9's net made 0.580 (0.564); `b1s3` on generations 0–2's test split 0.5807 (3v3 0.5714), the hand
evaluation 0.6039 (0.5964) and a fit of its twelve terms 0.5969 (0.5862). As in v0.9, log-loss hardly
separates what play separates: on that split `p0` made 0.5842 and `b1` 0.5808, against 48.4% and 56.0% in play.

## Strengths

The player sets the computer's **strength from 1 to 10** (ADR-039; one for all, or per seat). The
four levels are its bands, for the words: **1–3 Easy, 4–6 Normal, 7–8 Hard, 9–10 Expert**. A save
from before the slider carries only the level and plays as Easy 2, Normal 6, Hard 8, Expert 10, the
strengths whose profiles the levels had (Normal, Hard and Expert with the v0.7 evaluation). Each step
adds one lever that measured (below); the knobs are `LevelProfile`s in `AiPlayers.profile(strength)`.

| | Band | How it chooses | Horizon (cards / other) | Samples (cards / other), candidates | Later choices in a playout | Enemy cards | Noise | Evaluation | Device budget per card play |
|-|------|----------------|-------------------------|--------------------------------------|----------------------------|-------------|-------|------------|-----------------------------|
| 1 | Easy | static scores on one sample | — | — | — | uniform guess | τ 2.0 within 6, 16% slips; forgets minion modifiers 70% of its moves | nearest enemy only, next turn 0.08 | 50 ms |
| 2 | Easy | static | — | — | — | uniform | τ 1.2 within 4, 8% slips, forgets modifiers 40% | nearest only, 0.12 | 50 ms |
| 3 | Easy | static | — | — | — | uniform | τ 0.9 within 3, 5% slips, forgets 25% | nearest only, 0.15 | 50 ms |
| 4 | Normal | static | — | — | — | opponent model | τ 0.6 within 2, 3% slips, forgets 10% | all enemies, 0.18 | 50 ms |
| 5 | Normal | each option played out once | end of turn / end of the action | 1 / 1, the best 3 | static; opaque ones one ply deep | uniform | τ 1.0 within 3 | tuned | 200 ms |
| 6 | Normal | played out once | turn / action | 1 / 1, 6 | static; opaque one ply | opponent model | τ 0.6 within 1.5 | tuned | 300 ms |
| 7 | Hard | played out once | turn / **turn** | 1 / 1, 6 | static; opaque one ply | opponent model | τ 0.5 within 1.3 | tuned | 500 ms |
| 8 | Hard | mean − 0.3 sd for card plays | turn / turn | **6 / 2**, 8 | static; opaque one ply | opponent model | τ 0.25 within 0.8 | tuned | 1 s |
| 9 | Expert | mean | turn / turn | **16 / 6**, 10 | its own by **one-ply search** | + **memory** of cards shown since each level up | τ 0.15 within 0.6 (mixing) | tuned | 1.5 s |
| 10 | Expert | mean | turn / turn | 16 / 6, 10 | **every hero's** by one-ply search | + memory | τ 0.15 within 0.6 | **learned** at playout ends (v0.8's tuned inside) | 2 s |

"Tuned" is `TUNED`: exposure × 0.3, minion opportunity × 2 (Evaluation, above), and since v0.9 in 3v3
a Push Potential of 0.35 per attack card and the full exposure of a hero with no card left to defend
with ("Team sizes"). Since v0.9 (ADR-047 part B) strength 10 scores the end of every playout with the
learned evaluation instead (`AiPlayers.LEARNED_FROM`); its one-ply looks inside playouts, static
policy and opponent model keep v0.8's tuned knobs, without the 3v3 ones (`TUNED_WITH_NET`, "The two
parts together"). Device budgets since v0.9 ("Budgets"); the app runs the samples on three
threads, so the answers do not depend on the device wherever the cap is not reached. "Opaque" choices are
a bullet, a token, a confirm or number, a space that is not a move, a unit that is not an attack
target, another hero's card (Architecture).

- **1** is a true first game: it looks only at the nearest enemy, forgets the minion modifiers most
  of the time it moves, and often takes the second or third best option. **2** is Easy as before the
  slider; **3** slips less; **4** sees every enemy and rarely slips. None of them ever slips on a
  defense it can make or on an option worth a push or a kill (STRATEGY §4.2).
- **5** is the first that looks ahead: its three likeliest options, each played out once against
  uniform guesses of the enemies' cards, chosen loosely. **6** is Normal: every option played out once
  against the most likely enemy cards.
- **7** plays every choice, not only card plays, to the end of the turn: the biggest single step of
  the searching strengths (67% over 6).
- **8** is Hard: six guesses of your hidden cards from the opponent model, each own card played
  through the whole turn, risk-averse.
- **9** samples more, remembers every card you have shown since your last level up, and plays its own
  later choices in a playout by one-ply search; **10** is Expert: everyone's later choices in a
  playout by one-ply search, it mixes between near-equal plays, and since v0.9 it judges the end of
  every playout with the learned evaluation (Learned evaluation) and thinks at most 2 s a card play on
  the phone, on three threads.

The coach analyses at Hard (8) but keeps the tuned hand evaluation (its thresholds and reasons were set
on it; `AiCoach` sets `learned = HAND`).

### Departures from STRATEGY.md §4.2, and why

- **Round planning** (Expert beam search over the card order to turn 4, regret matching): not built.
  Playouts to the end of the *next* turn measured worse than to the end of this one, before v0.7
  (39–44% against Hard, 36 games each) and again with the tuned evaluation (37.5%, 128 games against
  Expert), because the later turns are played by rules of thumb. The round plan lives in the static
  card policy and the hand terms (spare, ratings).
- **Sample counts** are below §4.2's 24–64: 6 (Hard) / 16 (Expert). 24 samples with 12 candidates
  measured even with 16 and 10 over three runs (233 of 448 games, 52.0%), so it is not used.
- **Sampling enemy cards inside the playout** (enemies who play after you) from the opponent model
  measured worse (42%, 36 games) than letting them play their best static card; not used.
- **Normal's τ = 2** would make it visibly random on our score scale; τ 0.6 within 1.5 instead.
- **Easy's "highest-initiative card 50%"** rule: replaced by the static round plan plus noise, which
  reads more like a beginner than a fixed rule.
- **Upgrades**: static rating at every strength (card stats + item worth by matching icons + a small
  Red bias). Playing the upgrade choice out through the new round's first turn measured no better
  (49.0%, 192 games at Hard), nor did a learned per-hero table (50.6%, 1 000 games, ADR-049).
- **Team play**: AI teammates share the evaluation (so they rarely block each other or chase the same
  minion), but there is no explicit focus-fire plan and no "I'll take the minions" log line.
- **Tried over all 32 heroes and dropped** (before v0.7, Hard vs Hard, 192 mirrored games each): a
  charge of 1 or 2 for a card played only to hold: 49% and 49%; a forced discard without a spare card
  costing 4.5 instead of 2.5: 50% (Normal 52%); Expert with mean − 0.3 sd: 63% against Hard where
  plain Expert made 68% on the same 128 games.

### What changed in v0.7 (ADR-039), and what was tried and dropped

Every candidate was played against the profile it changes, mirrored, on mixed line-ups from all 32
heroes, with deterministic players; the ladder below is on line-ups 900–995, which no kept change
was tuned on. 192 games unless noted; 95% Wilson intervals.

| Change (against the same profile without it) | Result | Kept |
|-----------------------------------------------|--------|------|
| Exposure × 0.5, Hard | 56.3% (49–63), then 55.7% (49–63) on other line-ups | → 0.3 |
| Exposure × 0.3, Hard | 62.5% (55–69) | yes |
| Exposure × 0.2 / 0.1 / 0, Hard | 55.2% / 59.4% / 50.0% | no (0.3) |
| Exposure × 1.5, Hard | 38.0% (32–45): the direction is clear | — |
| Exposure × 0.3 and opportunity × 2, Hard | 64.1% (57–71) | **yes** (`TUNED`) |
| The same, Normal / Expert (128 games) | 60.9% (52–69) / 59.4% (51–68) | yes |
| Expert with `TUNED` against the Expert before v0.7 | 59.9% (52.8–66.6%), and 59.4% (51–68%) over 128 games on other line-ups | — |
| Opportunity × 2 alone; hand rating × 3; both with exposure × 0.5 | 53.6% / 53.1% / 61.5% and 55.7% | via `TUNED` |
| Opportunity × 3.5, exposure × 0.15, levels × 1.5, pieces × 1.5 (on `TUNED`, Hard) | 50.0% / 54.7% / 48.4% / 52.6% | no |
| An enemy plays one card a turn: its cards' threats capped at one card's chance (`oneCardPerTurn`), Hard | 51.6%; with exposure × 0.3, 56.8% | no |
| Opponent model τ 0.8 / 3 (instead of 1.5), Hard (128) | 51.6% / 50.0% | no |
| Upgrade choice played through the new round's first turn, Hard | 49.0%; with 4 samples for other decisions 49.5% | no |
| Expert: 24 samples, 12 candidates | 233 of 448 games, 52.0% | no |
| Expert: end of the next turn | 37.5% (30–46), 128 games | no |
| Expert without one-ply playouts for teammates and enemies (128 + 192) | Expert wins 54.7% of 320 | that is strength 9 |
| Expert: τ 0 (no mixing) / τ 0 with 24 samples / τ 0.3 within 0.9 | 43.8% / 41.7% / 46.9% | no |
| Expert: one-ply look at 12 options instead of 8 (128) | 44.5% | no |
| Expert: mean − 0.15 sd; no card memory (128) | 52.3% / 53.1% | no |
| Hard: τ 0.5 within 1.2 / τ 0.1 within 0.4 | 47.4% / 46.4% | no |

Also fixed, found by these tournaments: two engine crashes ("Place into a non-empty space") when
Takahide's Spinning Blade made a space an obstacle for enemy units and Razzle's Stunt Doubles or
Wuk's Toss Away offered it (`RazzleTest`, `WukTest`).

**Blunders** (`BlunderTest`, Expert self-play with the evaluation before v0.7, 16 games, 5 751
decisions): no
move into a defeat that another destination avoided; 0.19 defenses a game declined while a card
would have held, and 0.38 actions a game passed over one that defeats an enemy hero. Read in the game
logs, those were not mistakes: the declined defense came when the round's push won the game either
way, and the "missed defeat" was a teammate's attack that made it on the same turn.

## Budgets and measured time

**v0.9 (ADR-047): by team size.** The owner's ceiling is about 2 s per card play at the top strength
on the S25 Ultra, in 3v3 or bigger. `TimingTest` (`AI_TIMING_FORMAT=2v2|3v3|4v4|4v4river`,
`AI_TIMING_GAMES`, production players with the v0.7 budgets, one thread, both teams' decisions with
more than one option, desktop JVM shared with three other builds, 2026-10-27, one afternoon) also
prints the work of a card play in engine answers (the playouts' steps) and the time per 1 000 of them:

| Format | Strength | Card play mean / p95 / max | Moves (`CHOOSE_SPACE`) mean / p95 | Work per card play mean / p95 (answers) | ms per 1 000 answers |
|--------|----------|----------------------------|-----------------------------------|------------------------------------------|----------------------|
| 2v2 | 8 | 28 / 81 / 445 ms | — | 475 / 1 372 | 58 |
| 2v2 | 9 | 106 / 249 / 843 ms | — | 1 812 / 3 962 | 58 |
| 2v2 | 10 | 204 / 479 / 1 464 ms | — | 3 468 / 6 460 | 59 |
| 3v3 | 8 | 50 / 136 / 381 ms | 22 / 84 ms | 769 / 2 153 | 64 |
| 3v3 | 9 | 184 / 451 / 1 253 ms | 100 / 358 ms | 2 774 / 6 622 | 66 |
| 3v3 | 10 | 377 / 799 / 1 648 ms | 204 / 531 ms | 5 418 / 9 435 | 70 |
| 4v4 (Forgotten Island) | 8 | 69 / 198 / 369 ms | 32 / 107 ms | 949 / 2 604 | 73 |
| 4v4 (Forgotten Island) | 9 | 234 / 585 / 1 460 ms | 132 / 497 ms | 3 049 / 7 312 | 77 |
| 4v4 (Forgotten Island) | 10 | 617 / 1 365 / 2 271 ms | 298 / 783 ms | 7 580 / 14 416 | 81 |
| 4v4 (Across the River) | 10 | 820 / 1 637 / 3 276 ms | 379 / 980 ms | 7 491 / 13 634 | 109 |

Strength 10 costs 1.8× its 2v2 time in 3v3 and 3× in 4v4 (4× on the double lane): each playout has
more heroes acting (1.5–2.2× the steps) and each step is dearer (the state is bigger; two lanes double
the lane terms). The time per answer is the same for every strength of a format, so the work is a
deterministic stand-in for time: `LevelProfile.workBudget` (tests only, `AI_EXPERIMENT=s10+work=6000`)
stops sampling after that many answers, as the wall clock does on a phone, but reproducibly.

**The phone factor** stays 3–4× this desktop (no device measurement this round; ADB is not available
to the AI streams). It is a planning number on the slow side: the S25 Ultra's Snapdragon 8 Elite
scores well above this Ryzen 7 3800X single-threaded, but ART runs this allocation-heavy code slower
than HotSpot's C2 and a phone throttles over a long game. With 4× and today's sequential search,
strength 10's p95 card play is ≈ 3.2 s in 3v3 and ≈ 5.5 s (≈ 6.5 s on the double lane) in 4v4, so a
2 s cap would cut a slow 3v3 decision to about 60% of its samples and a slow 4v4 one to about a third.

**Budgets since v0.9:** 10: **2 s** per card play (was 4 s), 9: 1.5 s (3 s), 8: 1 s (1.5 s); 5–7
unchanged (200 / 300 / 500 ms), 1–4 50 ms. Other decisions get a third. The app runs the samples on
three threads (2.15× on the desktop), so at 4× the p95 3v3 card play of 10 needs ≈ 1.5 s (inside the
cap), the 4v4 one ≈ 2.5 s (≈ 3 s on the double lane: cut to 65–80% of its samples), and today's 4 s
of one thread buys about the samples 2 s of three threads do. What samples are worth under the cap,
measured with work budgets (2 s at 4× = 500 ms of desktop time ≈ 7 200 answers in 3v3, 6 100 in
4v4, × 2.15 with three threads): see "Team sizes".

The merged strength 10 (the net, three threads, the 2 s cap) is timed in "The two parts together".

**v0.7, 2v2** (`AI_TIMING_GAMES=8`, 2026-10-25, the budgets of then), for strengths 1–7 still current:

| Strength | Card play mean / p95 / max | Other decisions, means (max) | Slowest heroes, card play p95 / max | Device budget per card |
|----------|----------------------------|------------------------------|-------------------------------------|------------------------|
| 1 | 0.5 / 1.2 / 35 ms | 0.3–5.4 ms (111) | — (the first strength of the process pays the JIT warm-up) | 50 ms |
| 2 | 0.2 / 0.3 / 3.9 ms | 0.1–2.0 ms (30) | — | 50 ms |
| 3 | 0.1 / 0.2 / 2.8 ms | 0.1–0.9 ms (4.3) | — | 50 ms |
| 4 | 0.1 / 0.2 / 8.9 ms | 0.1–1.0 ms (7.4) | — | 50 ms |
| 5 | 6.8 / 22 / 44 ms | 0.3–10 ms (40) | Snorri 40 / 44, Razzle 39 / 39, Cutter 33 / 34 | 200 ms |
| 6 | 5.2 / 15 / 49 ms | 0.3–12 ms (169) | Snorri 38 / 49, Rowenna 35 / 40, Cutter 35 / 40 | 300 ms |
| 7 | 5.6 / 19 / 52 ms | 0.2–9.3 ms (154) | Rowenna 47 / 52, Cutter 38 / 48, Bain 30 / 43 | 500 ms |
| 8 | 39 / 117 / 577 ms | 0.7–25 ms (345) | Rowenna 402 / 577, Cutter 319 / 475, Takahide 200 / 200 | 1.5 s (now 1 s) |
| 9 | 117 / 314 / 1 088 ms | 3–73 ms (781) | Bain 730 / 988, Emmitt 469 / 595, Snorri 442 / 685 | 3 s (now 1.5 s) |
| 10 | 213 / 514 / 1 156 ms | 6–128 ms (824) | Bain 957 / 1 075, Min 823 / 831, Silverarrow 769 / 866 | 4 s (now 2 s) |

The coach's `prepare` averaged 16 ms (max 157 ms) per decision over one Easy-vs-Hard game on mixed
line-ups (`CoachAheadTest`).

In a browser (the web app's engine worker, WebKit on the development machine, ADR-043 W4 item 4) the
search ran about 8× the desktop JVM's time with v0.7's strengths: strength 10's card plays took 1.8 /
3.1 / 3.2 s (median / p90 / max) against its 4 s budget then. Since v0.9 the cap is 2 s and the browser
has no sample threads, so strength 10 in a browser reaches its cap more often and searches fewer samples
than on a phone (still legal, still the same player per sample: ADR-043, "v0.9 in the browser").

## Measured strength

Mirrored tournaments (STRATEGY §4.5) over **mixed line-ups from all 32 heroes**
(`AiHarness.mixedLineups`: consecutive seeded shuffles of the roster cut into fours, so every hero is
seated equally often), the coin alternating, every game played twice with the strengths swapping
teams (same seed and coin), Quick, Forgotten Island, `deterministic` players. Final run on line-ups
900–995 (not used for tuning), 192 games per pair, 2026-10-25 (`AI_MIXED=96 AI_FIRST_SEED=900`); 95%
Wilson intervals:

| Pair | Wins | Rate | 95% interval | ≈ Elo gap | What the step adds |
|------|------|------|--------------|-----------|--------------------|
| 2 vs 1 | 140–52 | 72.9% | 66.2–78.7% | +170 | fewer slips, remembers the modifiers more often |
| 3 vs 2 | 127–65 | 66.1% | 59.2–72.5% | +115 | fewer slips again |
| 4 vs 3 | 123–69 | 64.1% | 57.1–70.5% | +100 | every enemy seen, the opponent model, rare slips |
| 5 vs 4 | 148–44 | 77.1% | 70.6–82.5% | +210 | looks ahead: options played out |
| 6 vs 5 | 125–67 | 65.1% | 58.1–71.5% | +110 | every option, the opponent model, less noise |
| 7 vs 6 | 128–64 | 66.7% | 59.7–73.0% | +120 | every choice played to the end of the turn |
| 8 vs 7 | 113–79 | 58.9% | 51.8–65.6% | +60 | six guesses, risk-averse |
| 9 vs 8 | 115–77 | 59.9% | 52.8–66.6% | +70 | sixteen guesses, memory, its own one-ply playouts |
| 10 vs 9 | 214–170 | 55.7% | 50.7–60.6% | +40 | everyone's one-ply playouts (v0.7) |
| 10 vs 9 (v0.9) | 144–48 | 75.0% | 68.4–80.6% | +190 | + the learned evaluation at playout ends |

10 vs 9 (v0.7) is over 384 games (line-ups 900–995 and 1300–1395: 54.7% and 56.8%); since v0.9
strength 10 adds the learned evaluation (Learned evaluation) and the step is 75.0% (line-ups 900–995,
2026-10-27). Every step's rate
and every interval's lower end is above 50%; the highest steps come closest to it (the top end is
flat, Known weaknesses). From 1 to 10 the steps add up to about 1 000 Elo.

Against the AI before v0.7 (the same harness): the tuned evaluation alone won 60.9% at Normal
(128 games), 62.5% and 64.1% at Hard (two runs of 192) and 59.4% (128) and 59.9% (192) at Expert, so a save from before the slider plays stronger
than it did at its level; Easy (2) is unchanged and 1 is gentler (2 beats 1 72.9%).

Before v0.7 the four levels measured (line-ups 96–191): Normal–Easy 93%, Hard–Normal 76%,
Expert–Hard 60%; per hero (`TournamentTest` prints each side's win rate with every hero; 12 games
each, so ±25 points) the stronger level gained least with the set-up heroes (Gydion, NebKher, Tali,
Trinkets, Widget), whose cards work through pieces and counters the evaluation sees only as such.

`LevelLadderTest` replays 24 mixed games (line-ups 200–211) plus 12 first-game games for the named
levels' strengths (2 over 1, 6 over 2, 8 over 6, 10 over 6) on every test run: 36 games cannot
guard a gap of about 63% (10 over 8 made 16 of 36 there, against 59.9% and 55.7% for its two steps
above).

## Team sizes: 3v3 and bigger (v0.9, ADR-047 part A)

The owner beat strength 10 "easily", by hero kills and pushing the lane, in 3v3 or bigger, and
everything above had been measured on 2v2. The harness now plays any size (`AiHarness.Format`,
`AI_FORMAT=3v3`, `4v4`, `4v4river`, `5v5river`): mixed line-ups from consecutive seeded shuffles of
the roster cut into groups of 2 × size (every hero seated about equally often; the 2v2 list is the
old one), mirrored, seeded, deterministic players, four game threads. 3v3 is played on Forgotten
Island Quick (5 Life counters for 6 heroes), the map the app starts on; 4v4 on Forgotten Island (the
app allows it; Life by the nearest listed row, 5) and on Across the River, the double lane the
rulebook requires from 8 players (p.20; 6 Life, 7 Waves a lane). A 3v3 strength-10 game takes 1–1.5
minutes of wall time with four games at once, a 4v4 one about two. Tournaments now report defeats per
game per side, how many of them were heroes with no card left to defend with, and how each side won
(`Score.details`); a crashed game is dumped and reproduced (`AI_DUMP_DIR`, `CrashReproTest`). The
first 3v3 tournament found an engine crash (Razzle's Stunt Doubles offered a Spawn point under a token
beyond Wasp's Static Barrier, CONTRACT-CHANGES), fixed with `RazzleTest`.

What 3v3 looks like: kills decide it. Strength 10 against itself loses 1.6–1.9 heroes a game (about
1 in 2v2), 30–40% of games end on Life counters, and about half of all defeats are of a hero with no
card left to defend with: one enemy's "discards a card" strips the hand on one turn, another's attack
lands on the next (game logs: Swift's Suppress and Reload!, then Brynn's attacks, took three of Red's
four heroes in one game; strength 8 loses 0.75 heroes a game that way).

**The four suspects, measured** (3v3 unless noted; 95% Wilson intervals):

1. **Budget starvation: not at today's 4 s; real in 4v4 at the owner's 2 s.** Strength 10 costs 1.8×
   its 2v2 time in 3v3 and 3× in 4v4 (Budgets). At a 4× phone today's 4 s cap is reached only by the
   slowest 3v3 decisions; a 2 s cap on one thread would cut a slow 3v3 card play to ~60% of its
   samples and a 4v4 one to about a third. What samples are worth: 16 against 8, 54.2% (44–64%, 96
   games), the side with fewer samples losing more heroes (1.83 against 1.46 a game); in 4v4 at 2 s,
   three threads' worth of work (15 000 answers) against one thread's (6 000): 58.3% (48–68%, 96).
   Hence 2 s on three threads: about today's samples in half the time.
2. **Caution tuned away: confirmed as self-play's choice, and the gap is narrow.** Doubling the
   exposure scale (0.3 → 0.6) loses 41.1% (34–48%, 192 games at strength 8) although it loses fewer
   heroes (1.20 against 1.68). The exposure term did not compound several attackers; a focus-fire
   model (`EvalKnobs.focusFire`: each enemy's one card, the hero answering the strongest attack first
   with the cheapest card that holds) measured 48.4% (42–56%, 192). What the term misses is a hero
   with **no card left to defend with**, certain to fall to any attack that reaches it one turn after
   the playouts end. Counting that hero's exposure in full (`EvalKnobs.defenseless`, from 3 heroes a
   side) measured 53.6% at strength 8 (47–61%, 192; 2.0: 46.4%, 0.6: 49.0%; in 2v2 46.9%) and 51.6%
   at 10 (44.5–58.5%, 192 held-out games) with 35% fewer heroes lost that way (95 against 147). Alone
   it trades kills for pushes (in the dropped package below the win rate stayed even); with the 3v3
   Push Potential (3.), which wins pushes, it is **kept for 3v3**: the pair beat today's 10 and keeps
   the heroes the Push Potential alone exposes (the exploiter, below).
3. **Lane arithmetic: confirmed, in one number.** The projection of the round's Minion Battle sums
   every hero's Push Potential (0.5 of a minion per attack card left), but three heroes a side hunt
   the same minions two did. Push Potential 0.35 per attack card measured 57.3% (50–64%, 192 games at
   strength 8) and, at strength 10 on held-out line-ups 900–995, **57.8% (50.7–64.6%, 192 games)**
   against today's 10, winning more by pushes (59 last pushes and 20 Throne pushes against 41 and 8)
   with the same heroes lost (1.76 against 1.80 a game). It is kept for three heroes a side only: in
   2v2 0.35 measured 53.1% (46–60%), in 4v4 49.0% (39–59%, 96) and 0.25 (the same minions shared by
   four) 42.7% (33–53%, 96). The other lane weights hold: 0.75 per card 45.8% (39–53%), lane weight 80
   instead of 60 50.0% (43–57%), minion opportunity × 1 instead of × 2 44.3% (37–51%), Life × 1.5
   47.4%, kill tempo 10 instead of 5 47.4% (192 games at strength 8 each).
4. **No focus fire / team plan: within a turn it is there, across turns it is not.** At 10 teammates'
   later actions in a playout are one-ply searched and attack a stripped hero when the evaluation sees
   the kill, so two-hero kills inside a turn are found. A focus-fire term measured nothing (2., and
   46.9% on top of the defenseless floor). Kills set up over two turns are not made; the defenseless
   floor (2.) makes them feared in 3v3, and planning them is part B's ground.

**A stand-in for the owner.** `Hunt` (`LevelProfile.hunt`, `AI_EXPERIMENT=s10+hunt=a/b/c`) is
strength 10 with a one-sided taste: its own heroes' exposure × a, the enemy's × b, the enemy's Life
counters × c. Reckless (0 / 2 / 1.5) lost 22.9% (13–37%, 48 games) and 3.25 heroes a game against
1.67; hunting with caution and focus fire (1 / 2 / 1.3, `focus=true`) 43.8% (31–58%, 48), losing 2.06
heroes a game against 2.23. No style of our own beat strength 10, so the exploiter measures
robustness, not a ceiling. On line-ups 900–947 (96 games each) today's 10 won 55.2% (45–65%) against
it, losing 2.14 heroes a game (62 with an empty hand) and 28 games on Life counters; with the
defenseless floor 51.0% (41–61%), 1.94 heroes a game (48 with an empty hand) and 20 games on Life
counters: fewer heroes lost, the win rate the same within the noise. The 3v3 Push Potential alone
made strength 10 *more* exposed to it (43.8%, 34–54%; 2.31 heroes a game, 73 with an empty hand; 35
games lost on Life counters); with the defenseless floor as well 54.2% (44–64%), 2.06 heroes a game
(61 with an empty hand) and 24 games lost on Life counters, while the exploiter lost 2.15 (91). That
is why the two ship together.

**What part A shipped** (on its own branch) was today's 10 with, in 3v3, the Push Potential of 0.35
and the defenseless floor (it answers exactly as the variant measured below; in 2v2 and 4v4 exactly
as before, `FingerprintTest`), a 2 s budget, and its samples on three threads in the app: on the
phone it searches about as much as today's 10 did in 4 s, and against a one-thread 10 held to the
owner's 2 s it gets 2.15× the samples (3v3: 16 against 8 samples 54.2%; 4v4: 58.3%, above).
Strengths 5–9 share `TUNED` and play 3v3 that way; 1–4 are unchanged. Since the merge strength 10
judges playout ends with the learned evaluation and keeps v0.8's knobs beside it; the budget and the
threads stay ("The two parts together").

| 3v3, no cap, against the v0.8 strength 10 | Wins | Rate | 95% interval | Heroes lost a game (with an empty hand, in all) |
|-------------------------------------------|------|------|--------------|--------------------------------------------------|
| Push Potential 0.35 alone, line-ups 900–995 | 111–81 | 57.8% | 50.7–64.6% | 1.76 against 1.80 (120 / 119) |
| **Shipped: Push Potential and defenseless floor**, line-ups 900–995 | 111–81 | 57.8% | 50.7–64.6% | 1.70 against 1.92 (88 / 133) |
| **Shipped**, line-ups 1700–1795 | 102–90 | 53.1% | 46.1–60.1% | 1.88 against 1.84 (96 / 143) |
| **Shipped**, both (384 games) | 213–171 | 55.5% | 50.5–60.3% | 1.79 against 1.88 (184 / 276) |

The earlier candidate package (racing and the defenseless floor, before the Push Potential was found)
did not beat today's 10 and was dropped:

| Where (package: race 3, defenseless 1.0, 2 s on three threads) | Package against today's 10 | Games | Rate | 95% interval |
|-----------------------------------------------------------------|----------------------------|-------|------|--------------|
| 3v3, the phone at 2 s (4×; package 15 500 answers; old: one thread, 7 200); heroes lost a game 1.59 against 1.93, with an empty hand 89 against 146 | 104–88 | 192 | 54.2% | 47.1–61.1% |
| 3v3, no cap (line-ups 1300–1395); heroes lost 1.75 against 1.94, with an empty hand 94 against 156; more wins on Life counters (45 against 33), fewer by pushes (47 against 67) | 92–100 | 192 | 47.9% | 41.0–55.0% |
| 4v4 Forgotten Island, the phone at 2 s (13 200 against 6 100); heroes lost 2.27 against 2.39 | 44–52 | 96 | 45.8% | 36.2–55.8% |

**Ladder in 3v3** (the v0.9 profiles with the 3v3 Push Potential, measured before the defenseless
floor joined `TUNED` — both sides of each searching pair carry both; line-ups 900–947, 96 games a
pair, 95% intervals): 2 over 1
81.3% (72–88%), 3 over 2 66.7% (57–75%), 4 over 3 61.5% (52–71%), 5 over 4 82.3% (74–89%), 6 over 5
58.3% (48–68%), 7 over 6 74.0% (64–82%), 8 over 7 54.2% (44–64%), 9 over 8 57.3% (47–67%), 10 over 9
61.5% (51.5–70.6%). Every step wins; 96 games cannot separate 6/5, 8/7 and 9/8 from even (their 2v2
steps over 192 games could, "Measured strength"). `LevelLadderTest` (2v2) is unchanged. With the
merged strength 10 (the net) and 9 and 8 as shipped, on line-ups 2000–2047: 10 over 9 67.7%
(57.8–76.2%), 9 over 8 61.5% (51.5–70.6%) ("The two parts together").

**Tried and dropped in v0.9** (3v3, 192 games at strength 8 unless noted): exposure × 0.6 41.1%;
focus fire 48.4%; Push Potential 0.75 45.8%, 0.35 in 2v2 53.1% and in 4v4 49.0%, 0.25 in 4v4 42.7%;
lane weight 80 50.0%; minion opportunity × 1 44.3%;
Life × 1.5 47.4%; kill tempo 10 47.4%; defenseless 1.0 53.6% (10: 51.6%), 2.0 46.4%, 0.6 49.0%, in 2v2
46.9%; defenseless with focus fire against defenseless alone 46.9%; racing 51.0% (96 games at 10);
the package above; the Push Potential alone (it made the AI more exposed to the exploiter). Kept: the
3v3 Push Potential with the 3v3 defenseless floor, the budgets, the threads, the quiet search copies,
the harness, the engine fix.

## The two parts together (v0.9 integration, ADR-047)

Part A tuned the hand evaluation for 3v3 (Push Potential 0.35, the defenseless floor) with the hand
evaluation at the playout leaves; part B trained the net beside v0.8's `TUNED` and put it at strength
10's leaves. The question at the merge was whether they compose. What the code does: the net's inputs
include the hand evaluation's terms, but `NetEvaluator` builds its own hand evaluation with the knobs
the net was trained with (now frozen as `Features.KNOBS` / `SCALES`), so part A's knobs never reach
the net. They would reach strength 10 only through its hand parts — the one-ply looks inside
playouts, the static policy and the opponent model. Measured, 3v3, mirrored, deterministic players,
fresh mixed line-ups 2000–2095 (`AiHarness.mixedLineups`; no stream tuned or trained on them; A2's
families are a different generator), 95% Wilson intervals:

| 3v3 | Wins | Rate | 95% interval | Heroes lost a game (with an empty hand, in all) |
|-----|------|------|--------------|--------------------------------------------------|
| (a) net + part A's 3v3 knobs in the hand parts, against (b) net + v0.8's knobs there | 88–104 | 45.8% | 38.9–52.9% | 1.71 against 1.61 (92 / 90) |

(b) wins or ties, so strength 10 ships as (b): `AiPlayers.TUNED_WITH_NET` (v0.8's `TUNED`) beside the
net, `TUNED` with the 3v3 knobs for 5–9 (`LearnedNetTest` asserts both, and that the leaf scores do
not move with the knobs). Part A's 3v3 gain was measured with the hand evaluation judging the leaves;
with the net judging them the same knobs in the playout policy bought nothing (the interval includes
even, the point estimate is below it).

**The merged strength 10 against v0.8's strength 10.** "v0.8's 10" is
`s10+learned=HAND` (`ExperimentTest`), deterministic, the hand evaluation with v0.8's `TUNED`; it
gives the very answers of 1f7fa273's `AiPlayers.deterministic(10)` (three games, 1 139 answers,
hashes compared across the two builds). It is the uncapped equivalent of v0.8's 10 (4 s, one thread),
which on the desktop rarely reached its cap. Same harness, line-ups 2000–2095 (192 games each):

| Team size | Wins | Rate | 95% interval | ≈ Elo | Heroes lost a game, merged / v0.8 | How the merged 10 won (last push / Throne / Life) |
|-----------|------|------|--------------|-------|-----------------------------------|---------------------------------------------------|
| 3v3 | 124–68 | 64.6% | 57.6–71.0% | +104 | 1.98 / 1.29 | 66 / 39 / 19 |
| 2v2 | 133–59 | 69.3% | 62.4–75.4% | +141 | 1.13 / 0.77 | 74 / 46 / 13 |
| 4v4 (Forgotten Island) | 117–75 | 60.9% | 53.9–67.6% | +77 | 2.45 / 2.13 | 51 / 29 / 37 |

It wins mostly by pushing and loses more heroes than v0.8's 10 does: the net trades heroes for the
lane, and against v0.8's 10 the trade pays.

**Against the kill-hunter** (`Hunt` 1 / 2 / 1.3 with focus fire on v0.8's 10, part A's stand-in for
the owner: `s10+learned=HAND+hunt=1/2/1.3+focus=true`), 3v3, the same line-ups for both:

| 3v3 against the kill-hunter, line-ups 2000–2095 | Wins | Rate | 95% interval | Heroes lost a game (exploiter's) | With an empty hand (exploiter's) | Games lost on Life counters |
|--------------------------------------------------|------|------|--------------|----------------------------------|----------------------------------|-----------------------------|
| **Merged strength 10** | 127–65 | 66.1% | 59.2–72.5% | 2.25 (1.55) | 122 (117) | 43 |
| v0.8's strength 10 | 99–93 | 51.6% | 44.5–58.5% | 2.10 (1.89) | 149 (131) | 47 |

(Each half of 96 games: merged 70.8% and 61.5%, losing 2.06 and 2.43 heroes a game; v0.8's 50.0%
and 53.1%, losing 2.19 and 2.01.) The merged 10 beats the exploiter far more often, but it loses
about as many heroes to it as v0.8's 10 did — slightly more (2.25 against 2.10 a game), fewer of them
with an empty hand — and two thirds of the games it loses it loses on Life counters (43 of 65;
v0.8's 10: 47 of 93). The net does not make strength 10 safer against a kill-hunter; it makes it win
the lane faster than the hunter can cash its kills.

**Ladder in 3v3** (line-ups 2000–2047, 96 games a pair): 10 over 9 67.7% (57.8–76.2%), 9 over 8
61.5% (51.5–70.6%); `LevelLadderTest` (2v2) green.

**Time** (`TimingTest`, production players: the 2 s cap on, three sample threads as in the app,
both teams' decisions with more than one option, desktop JVM with nothing else running, 2026-10-28):

| Strength 10 | Card play mean / p95 / max | Moves (`CHOOSE_SPACE`) mean / p95 / max | Work per card play mean / p95 (answers) | At a 3–4× phone: card play mean / p95 / max |
|-------------|----------------------------|-----------------------------------------|------------------------------------------|---------------------------------------------|
| 3v3, the net (6 games) | 116 / 256 / 898 ms | 81 / 199 / 407 ms | 5 365 / 9 578 | 0.35–0.46 / 0.77–1.02 / 2 s (capped) |
| 3v3, the hand evaluation (v0.8's 10), same line-ups | 120 / 262 / 923 ms | 80 / 211 / 343 ms | 5 417 / 9 435 | — |
| 4v4 Forgotten Island, the net (4 games) | 190 / 417 / 1 025 ms | 116 / 292 / 626 ms | 7 364 / 14 038 | 0.57–0.76 / 1.25–1.67 / 2 s (capped) |

The net costs nothing measurable per decision (one evaluation per playout). At the assumed phone
factor the p95 card play stays inside the owner's ~2 s in 3v3 and 4v4; only the slowest few (the
max) reach the cap and are cut short. The factor is still an assumption, not a device measurement.

## The coach

`nudge` runs Hard-level playouts (6 / 4 samples, end-of-turn horizon) on copies determinized from
the human team's view, always including the human's choice. It speaks only when the choice is at
least 5 coin-equivalents below the best option on average **and** at least 1 below it in 75% of the
samples **and** a concrete reason is found; otherwise it says nothing. Reasons, in order: a defense
choice (a card that would stop the attack, a card too weak, a cheaper card that also works — with the
totals, p.14); a defeat the choice walks into (the attacker, its card when it is face up, the minion
modifier at that space, p.14); an empty hand (p.13); a lane push gained or given (p.8); a hero kill
missed (p.13); Fast Travel with the Red or Gold card (p.11); a minion left in reach (p.13, p.8).
The app runs the analysis ahead: `prepare` searches as soon as the question is asked (the pruned
candidates, without the choice, on a fork), and `nudge(analysis, …)` at the answer plays out only the
person's choice if the pruning left it out (same seeds and samples), then judges as above — the same
note as `nudge` (`CoachAheadTest`, every answer of a game compared). Desktop, one Easy-vs-Hard game
on mixed line-ups, two runs: `prepare` mean 9–11 ms, max 44–86 ms; the answer-time `nudge` mean 0.1 ms, max 7 ms.
Real outputs (`CoachTest`, `CoachSurveyTest`):

- "Your Lift Up can still stop this attack (Defense 5 against Attack 4)." — *A Defense total equal to
  or higher than the Attack total defends successfully; without it Wasp is defeated (p.14).*
- "Magnetic Dagger isn't enough against Attack 4, but Lift Up is." — *Defense 2 is below Brogan's
  Attack 4, so the card is discarded and Wasp is still defeated (p.14).*
- "Brogan's Onslaught can still reach that space." — *An attack whose total is higher than your best
  Defense card defeats you; minion modifiers count (p.14).*
- "Brogan's Bullrush can still reach that space." — *The enemy ranged minion gives you −1 Defense
  within 2 spaces of it (p.14).*

Over 12 games on mixed line-ups from all 32 heroes (an Easy or Normal player in the human seat
against Hard, `AI_COACH_GAMES=12`) it spoke on 47 of 1 915 decisions (2.5%): minions left in reach
and missed minion kills 26, hero kills missed 9, lane pushes given or missed 5, defeats walked into 6,
Fast Travel with a Red card 1. A kill or a minion is credited to the choice only when the deciding
hero itself makes it in the playouts; before that it once told Dodger that Dark Ritual (a coin skill)
"can take a minion" because a teammate took one later in the turn. It never commented on a hero
mechanic (tokens, spells, bodies, swaps): its reasons are all read from engine events.

With the v0.7 evaluation (ADR-039; the "human" seat played at Easy 2 or Normal 6 against Hard 8,
`AI_COACH_GAMES=12`) it spoke on 39 of 1 660 decisions (2.3%), the analysis taking 27 ms on average
(max 487 ms) on the desktop.

`turningPoints` replays the save (`GameEngine` needed), evaluates the seat's fitted winning chance
(`WinChance`, below; `σ(score / 15)` before v0.10) at every turn boundary and explains the 3–6 biggest
swings, preferring turns where something happened (a defeat, a push, a level, a forced defense, minions
taken), oldest first, each with the rule behind it.

### Suggestions and the post-game review (v0.10, ADR-051)

The app's **Suggest** button asks `Coach.suggest`: up to three options, each with a winning chance, how
firmly the samples rank it, the line its playouts repeat, its reasons; what can hit the hero; the round plan;
the hero's own official tips. `prepare` is unchanged and pays for none of it: `suggest` takes its
finished analysis, plays more samples on the same seeds (`Searcher.deepen`, 12 for card plays, 8 otherwise)
and only then builds the words (`SuggestionBuilder`, `LineBuilder`, `ReasonFinder`, `ThreatFinder`, `RoundPlanner`).
The nudges are as they were, with one fix found by reading the survey ("Your Charm can still stop this
attack (Defense 3 against Attack 5)" no longer happens); the coach never warns against the option it just put first.

**The win chance** (`WinChance`, `CalibrationTest`) is Platt scaling, P(team wins) = σ(k · score), of the hand
evaluation with the coach's knobs at every turn boundary of the stored games (generations 0 and 1: 149 702
positions of 6 399 games; line-ups % 10 < 8 fit, the rest measure). The old σ(score / 15) was far too
sure. Each sample's score goes through the fit and the chances are averaged, so a finished game in one
sample does not swamp the mean. Held-out log-loss (coin flip 0.693):

| Team size | k | 1/k | Fit | Old σ(score / 15) | One slope for all sizes |
|-----------|---|-----|-----|-------------------|-------------------------|
| 2v2 | 0.0294 | 34.0 | 0.593 | 0.659 | 0.596 |
| 3v3 | 0.0258 | 38.8 | 0.600 | 0.699 | 0.600 |
| 4v4 | 0.0214 | 46.8 | 0.611 | 0.755 | 0.612 |
| 5v5 | 0.0204 | 48.9 | 0.605 | 0.775 | 0.607 |

Reliability in 3v3, of the side favoured (said → won): 54.4 → 55.4%, 65.0 → 68.0%, 74.4 → 75.7%, 84.3 → 88.8%,
94.8 → 88.8%; the old formula said 96.1 where 81.4% won and 85.4 where 69.4% won. A slope per round of the game gained
nothing held out, so one slope per team size is used. The fit is within about 5 points except at the very top, so the
sheet shows the nearest 5, from 5 to 95 (0 or 100 only when every sample finished the game that way). It is self-play's
chance: a person who plays differently may beat it.

**Confidence** comes from the paired differences of the samples (common random numbers): best in most guesses (a gap of
at least 3 points, two standard errors clear, ahead in 75% of the samples), probably best (1 point, one standard error),
otherwise too close to rank, and the sheet says so instead of ranking noise. 72% of decisions over the survey are too
close to rank: most options of a decision after the reveal end the turn much alike, which is true and worth saying.

**The line** is the steps that recur together in a majority of an option's playouts (at least three samples). Another
hero's card is named only when it is open now, or, with "could", when every playout that has the step guessed the
same card; an Attack total only for an open card. **Threats** read `Ctx.HeroInfo.threats`, the evaluation's own list, on
eight determinized copies: the card when public, otherwise "a card of Attack N could", the Defense it takes, the
hero's best Defense with items and the minion modifier at its space. **The round plan** plays candidate orders of
the hero's cards (the playouts' own, and a rival of each) to the end of the round on six guesses; an order is told only when the same one
wins, alone, in at least two thirds of them and agrees with a clear first suggestion; otherwise the card to keep if that is as stable; otherwise nothing.
It told something at 263 of 641 card plays (165 with an order). **Advice** is the hero's `advice`, verbatim from source 3 through the importer.

**Reasons** went from 7 kinds to 32 (`CoachReasonKind`), each read from engine events or printed values with its page. Why
A rather than B: the gap between their paired playouts is split across the evaluation's twelve `Term`s, and that picks which
reason is named first. Silence stays allowed: reasons appear on 63% of decisions. By kind over the survey:
upgrades 1 022, Fast Travel 557, a minion 445, defense holds 249 / falls 120 / cheaper 120 / wasted 41, discards 265, tokens 187, swaps 168, minion modifiers 200, kills 141, forced moves 140, respawns 126, levels 117, Life cost 107, danger 116, Minion Battle 88, reach 63, coins 59, markers 49, pushes won 42 / given 18, position 35, a defender kept 33, spell counters 24, initiative 13, an empty hand 7, bodies 6, a target with no card 5, Block 3, focus fire 2. Every one of the 32 heroes gets reasons about what it does (tokens: Illusion, Turret, Pyro, Tree, Rock; bodies; markers: runes,
poison, bounty; spellbook counters; swaps; discards; coins). What each sentence means, and its page, is in `CoachReasons.kt`.

**The review** (`Coach.review`, `CoachReviewer`) replays the save and looks at every decision of the person's with more than one
option from what their team could know then, always including the choice made. A decision's loss is the best option's chance less the chance of the choice. The
edges were set on a survey of one seat's decisions over 16 games per strength on test line-ups, against a noise floor
measured by analysing the same decisions with two seeds (mean 0.000, p95 0.000):

| Player | Decisions | Mean loss | p90 | p95 | p99 | Best / good / inaccuracy / mistake / blunder |
|--------|-----------|-----------|-----|-----|-----|-----------------------------------------------|
| Strength 4 | 1 020 | 0.8 | 2.3 | 3.9 | 9.4 | 465 / 440 / 97 / 14 / 4 (89% accurate) |
| Strength 6 | 1 258 | 0.5 | 0.7 | 2.0 | 11.4 | 753 / 442 / 55 / 3 / 5 (95%) |
| Strength 8 (the coach's own search) | 1 342 | 0.0 | 0.0 | 0.1 | 0.3 | 1 013 / 329 / 0 / 0 / 0 (100%) |
| Strength 10 | 1 277 | 0.9 | 1.6 | 4.2 | 18.6 | 770 / 396 / 88 / 12 / 11 (91%) |

Good is below 2 points (well above the noise, about a decent player's p95), an inaccuracy below 5 (a weak player's p95), a mistake below 10 (their p99), a blunder from 10
up. Strength 8 is the coach's own profile, so it agrees with itself; strength 10 plays by the learned evaluation and the coach by the hand one, and it gets more of a strong mark (0.7 blunders a game against 0.25 for strength 4) although it is far stronger. A mistake or blunder must now also name a reason, or it stays an inaccuracy (that alone took strength 10 from 29 blunders to 11). The marks measure against a strength-8 look, not perfect play. Accuracy is
the share of best and good. Nothing is stored; a save from before this round reviews the same.

**Does it help?** A strength-4 player that takes the top suggestion at every decision, 3v3, mirrored, on 32 test line-ups (64 games each):

| Against | Wins | Rate | 95% interval | How the games ended |
|---------|------|------|--------------|---------------------|
| Plain strength 4 | 64–0 | 100% | 94.3–100% | 41 Throne, 18 last push, 5 Life counters |
| Strength 6 | 51–13 | 79.7% | 68.3–87.7% | 16 Throne, 27 last push, 21 Life counters |

**Agreement with a far deeper analysis** (64 samples on the same search, 300 sampled decisions of 3v3 test games): the look ahead's top option is the same in 98.7%, the sheet's
(12 / 8 samples) in 98.3%, and always within 3 points of the deep best. By confidence at 12 / 8: best in most guesses 100% (42), probably 98.2% (56), too close 98.0% (202). Sampling more decides
little here, because the same playout policy runs on guesses that mostly differ in cards nobody plays that turn: **more samples are not stronger advice**, and the deeper the same search goes the more it only confirms itself.

**Truth** (`CoachSuggestTest`): over 14 mixed 3v3 games from all 32 heroes (3 099 decisions of every `DecisionKind`), every sentence checked: its page, its printed numbers against the decision's context and the card data, what it
claims of the playouts recounted from their events, and no card of another hero named unless open or with "could": none failed. `NoPeekingTest` now also requires the same suggestions, word for word, for two sessions with the same public view. Read as a player
(about six hundred sentences), which found and fixed: the false "can still stop"; "5 points behind" beside two equal chances; costs told under "Why"; the same card named twice in one reason; a reason about the best option told under another without saying so; other heroes' level ups in the line;
a plan that "won" with no rival order.

**Time.** Desktop, 3v3, budget cut to 375 ms for a 4× phone on one thread (`AI_COACH_TIMING`, machine shared with three other streams): the look ahead mean 39 ms, p95 142 ms; the tap to the sheet mean 101 ms, p95 382 ms, max 405 ms (1.5 s at 4×), 9.1 samples on average, a round plan at 91 of 439 card plays (the rest ran out of budget or were not stable); unhurried the same survey took 124 ms mean, 616 ms p95, 2.5 s at most. The app runs the samples on three threads. The look ahead is unchanged (`CoachAheadTest`: prepare mean 21 ms, max 149 ms; the answer-time nudge mean 0.3 ms, 230 answers equal to a search from nothing). A whole review of one seat in a 3v3 game: mean 3.6 to 4.8 s on the desktop, max 12.4 s on one thread, so about 15 to 50 s at 4×, in the background with its progress and Cancel. No device figure: the phone factor is still an assumption.

**Tried and dropped.**

| What | Result | Kept |
|------|--------|------|
| A slope per round of the game | Held-out log-loss within 0.003 of one slope per size | no |
| One slope for all sizes | 3v3 even; 2v2 0.596 against 0.593, 5v5 0.607 against 0.605 | no: per size costs nothing |
| The net for the coach's scores | Not measured: the brief keeps the hand evaluation unless it measures better, and the fit above is on it | not built |
| More samples for the sheet than 12 / 8 | 98.3% against 98.7% agreement with 64: no more worth paying | no |

## Known weaknesses

Read from game logs over all 32 heroes (`GameLogTest`, `UsageTest`, 2026-10-24).

- **Lane-first:** hero kills come from opportunities the playouts find, not from plans; there is no
  deliberate focus fire, card-kill setup over two turns, or "let the wave push itself". Since v0.7
  the searching strengths are much less cautious (exposure × 0.3), and more games end on Life
  counters: Hard with exposure × 0.3 against Hard, 47 of 192 games; with exposure × 1.5, 27 of 192.
- **3v3 and bigger (v0.9):** about half the heroes lost are lost with an empty hand to a two-turn
  card-kill (strip, then hit, after the playouts' horizon). In 3v3 the defenseless floor cuts those
  losses by about a third; in 4v4 it is not measured and off. The AI does not set such kills up
  itself, keep a defender back for a teammate, or count which enemy acts after a discard. No
  style the engine can play (the `Hunt` exploiters) beats strength 10, so how a human beats it is
  still read only from the owner's report; the numbers here are AI against AI.
- **The net trades heroes for the lane (v0.9, less since v0.10):** v0.9's net won mostly by pushing and
  lost more heroes than v0.8's 10 did (3v3 1.98 against 1.29 a game, "The two parts together"). v0.10's
  loses fewer than v0.9's (3v3 1.71 against 1.96 a game, 2v2 0.91 against 1.16, 4v4 2.29 against 2.49),
  and beats the kill-hunting exploiter 74.0% where v0.9's won 62.0%, still losing more heroes to it than it
  takes (2.10 against 1.70 a game; v0.9's 2.27 against 1.49). Whether a human who hunts kills punishes that is the device game night's question.
- **4v4 on the phone:** part A estimated from one-thread times that a slow 4v4 card play (p95) would
  pass the 2 s cap at a 4× phone; timed with three threads (the merged 10, "The two parts together")
  the p95 is ≈ 1.7 s at 4× and only the slowest card plays (up to ≈ 4 s needed) are cut. The double
  lane (≈ 1.3× the cost per step) was not timed with the net.
- **Holding:** about a quarter of all actions are Holds (Hard self-play, 64 mixed games: 13%
  Mortimer to 35% Razzle). Most are chosen (moving would not improve the space), but Silver and Gold
  cards without a movement are often played on a turn their Primary has no target and are then held
  (Siren's Call, Death Trap, Crowd Control, Hurry up!, Inscribe the Runes about half the time they are
  played). The static round plan plays Silver early and the evaluation does not see that the card
  would have been worth more later; charging such plays measured no better (above), so the waste is
  mostly the spare card spent early.
- **Set-up heroes:** in the hand evaluation an Illusion, Rock or Ice token is worth the same whatever it
  is for, and it still answers every one-ply look inside playouts, so where a token goes in a playout is
  chosen without the net's knowledge. Since v0.10 the net at playout ends weighs pieces per kind and per
  hero, and those heroes gained most (Gydion +41%, Trinkets +28%, NebKher +20% over v0.9); they still win
  no more than average with it (Trinkets and NebKher about 48%). "Name a colour" (NebKher's Blue) and
  other choices whose effect comes next turn are looked at one ply deep, which sees nothing, so inside a
  playout the first option wins and at the root it is a coin toss; searching them further measured even.
- **Runes and other per-turn plans** (Snorri's rune under each turn slot, Swift's slot order, Cutter's
  Ultimate) are not planned over the round: the static round plan does not read them.
- **Fast Travel blocking**, spawn camping and post-push repositioning are not modelled explicitly.
- **Double lane:** the evaluation sums the lanes; it plays legally (smoke tests, 4v4 and 5v5 too) but
  has not been tuned, and each engine step costs a third more than on one lane.
- **Opponent model** is the static card policy: a human who plays unusual cards is guessed poorly
  (15% of each guess is uniform to stay robust).
- **The top end was flat:** above Expert (10) no search setting measured better (24 samples with 12
  candidates, a longer horizon, a hotter or colder choice, a wider one-ply look, played-out upgrade
  choices, a sharper or looser opponent model; "What changed in v0.7"). The learned evaluation
  (v0.9) was the next gain, and the per-hero and per-card parts (v0.10) the one after.
- **Wall-clock cap**: on a loaded phone the production player may search less than on the desktop;
  answers are then still legal but no longer reproducible for a seed. The phone factor (3–4×) is an
  assumption, not a device measurement ("Budgets").
- **Coach turning points** on quiet turns say only that the heroes "moved into position".
- **The coach is a strength-8 look:** its marks and suggestions measure against its own search, so strength 10 with the net shows
  "mistakes" there too, and a card worth more next turn than this one is not seen. Suggestions make a weak player strong, not a strong one stronger.

## Tests

| Test | What |
|------|------|
| `LevelLadderTest` | The named levels' strengths beat each other over a seeded, mirrored tournament (24 games on mixed line-ups from all heroes + 12 on the first-game heroes per pair): Easy (2) over 1 ≥ 60%, Normal (6) over Easy ≥ 75%, Hard (8) over Normal ≥ 65%, Expert (10) over Normal ≥ 75% |
| `NoPeekingTest` | Same public view, different hidden enemy cards → same answer at every strength 1–10; the determinizer keeps every public fact and samples only consistent upgrade histories |
| `DecisionKindsTest` | Every `DecisionKind` gets a legal answer at every strength: in AI games (every strength plays), a Clear action's token choice, and built questions of every kind |
| `AiSmokeTest` | Full AI games finish: every level, Long, 1v1, 2v1 with a Handicap card, the double-lane map, 3v3 (Forgotten Island), 4v4 and 5v5 (Across the River) at Normal and 4v4 on Forgotten Island at Hard |
| `SampleRunnerTest` | A threaded `SampleRunner` gives the same answers as the sequential one (40 decisions of a 3v3 game at strength 8) |
| `AllHeroesAiTest` | 16 seeded AI-vs-AI games over shuffled line-ups that seat each of the 32 heroes twice (Easy, Normal, Hard, two at Expert): all finish, every answer accepted (2026-10-24: 5–15 rounds, all three win types) |
| `CoachTest` | Nudges on hand-built blunders (no defense, too weak a defense, walking into a revealed attack) and silence on the right choices; turning points of a finished game |
| `CoachAheadTest` | `prepare` + `nudge(analysis, …)` equals `nudge` on the blunder positions (every option) and on every answer of a game (every option of the first long lists); the session is unchanged; the answer-time cost |
| `CoachSurveyTest` | The coach speaks on fewer than 15% of a weak player's decisions, on mixed line-ups (`AI_COACH_GAMES` for more games) |
| `CoachSuggestTest` | The survey of suggestions: every sentence against the events and printed values it came from, no hidden card named (`AI_COACH_SURVEY=<games>`, `AI_COACH_SURVEY_OUT=<file>`); the defense at a hand-built attack; suggesting after a look ahead is the same as from nothing |
| `CoachHelpTest`, `CalibrationTest` | Opt-in (`AI_COACH_HELP=4:4,4:6`, `AI_COACH_DEEP=<n>`, `AI_COACH_MARKS=<games>`, `AI_COACH_TIMING=<games>`, `AI_CALIBRATE=<games.jsonl>,…`): "The coach" |
| `TimingTest` | Decision times per strength over mixed line-ups, the slowest heroes and the work per card play (`AI_TIMING_GAMES=8 AI_TIMING_STRENGTHS=… AI_TIMING_FORMAT=3v3` prints the tables above; `AI_TIMING_THREADS=3` with a threaded runner, `AI_TIMING_VARIANT=s10+race=3` a profile variant) |
| `ControllerStrengthTest` | A save with only a level loads as its strength; strengths round-trip; the bands |
| `TournamentTest`, `ExperimentTest`, `GameLogTest`, `UsageTest` | Opt-in (`AI_TOURNAMENT` / `AI_MIXED` with `AI_PAIRS=6:5,…`, `AI_EXPERIMENT=s10+samples=24:s10`, `AI_LOG`, `AI_USAGE` environment variables; `AI_FORMAT=3v3` for bigger teams, `AI_KEEP_GOING` and `AI_DUMP_DIR` for long runs, `AI_PROGRESS` a line per game; variants also `work=`, `race=`, `defenseless=`, `focus=`, `hunt=a/b/c`, `pp=`, `learned=HAND` — `s10+learned=HAND` is v0.8's 10): long tournaments between strengths (per-hero rates; `AI_GAMES=<hero>` lists the games; a failing game is reported with its line-up and seed), profile variants (any knob or `x<TERM>=` scale), one game's log (a mixed or explicit line-up; `AI_LOG_DECISIONS`, `AI_LOG_VALUES` also for defenses, `AI_LOG_LEAVES=<hero>`), each card's actions, defeats and kills per hero |
| `FingerprintTest`, `CrashReproTest` | Opt-in (`AI_FINGERPRINT=<variant>:<games>`, `AI_REPRO=<dump>\|<variant>\|<seed>`): a hash of every answer of deterministic games (a change meant only to be faster must keep them); a crashed game's decision asked again from its dump |
| `LearnedNetTest` | The checked-in weights load and were trained on these features (`Features.SCHEMA_HASH`, input count); the board inputs name real piece kinds and the tables real heroes and cards; a piece reaches the net per kind, seen from both sides; the net's inputs and strength 10's hand parts keep the knobs it was trained with (`Features.KNOBS` / `SCALES`, `TUNED_WITH_NET`; leaf scores do not move with part A's 3v3 knobs, which do move the hand evaluation); swapping the teams negates the score; mirrored openings give mirrored features except the board-dependent ones; an evaluation costs far less than a playout (prints µs per position) |
| `LearnedSelfPlayTest`, `LearnedTrainTest`, `TeamTournamentTest`, `LearnedTimingTest` | Opt-in (`AI_SELFPLAY`, `AI_TRAIN`, `AI_TEAMS`, `AI_LTIMING`): self-play data at any team size, training (Learned evaluation, "How to retrain"), mirrored tournaments of any variant (`learned=`, `net=<name>` or `net=v09` on top of `ExperimentTest`'s keys) at any team size on the train / tune / test line-up families, decision times at any team size |
| `BlindSpotTest`, `UpgradeTableTest` | Opt-in (`AI_BLIND=<variant>:<games>`, `AI_UPGRADES=<games>`): which questions the search cannot tell apart, per hero and question; the value of each level-up choice from games played with `AI_EPS_UPGRADE` (ADR-049) |
| `FitTest`, `BlunderTest` | Opt-in (`AI_FIT=<games>:<strength>`, `AI_BLUNDERS=<strength>:<games>`, `AI_FORMAT`): the evaluation's parts fitted to self-play outcomes (above); plays a decent player would call blunders, judged on the real game — a defense declined or too weak while one would hold, an action that defeats an enemy hero passed over, a move into a defeat another destination avoided |
