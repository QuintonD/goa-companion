# The computer opponents and the coach (`core.game.ai`)

ADR-035 point 3, ADR-039, PRODUCT §9. The strategy knowledge behind it is [STRATEGY.md](STRATEGY.md);
this file is how the code uses it, what each strength does, what it costs and how strong it is.

## Architecture

The app binds these in `core/data/.../game/PlayEngineModule.kt`:
`AiPlayers { strength -> forStrength(strength, seed) }` and `AiCoach(factory)`. A seat's
`Controller.Ai` carries its strength 1–10; a save from before the slider carries only the level and
plays as the level's strength (Easy 2, Normal 6, Hard 8, Expert 10, ADR-039).

| File | What |
|------|------|
| `AiPlayers.kt` | `AiPlayers.forStrength(strength, seed)` (production, wall-clock capped) and `deterministic(strength, seed)` (tests), `forLevel` for a level alone; the ten `LevelProfile`s and `TUNED`, the evaluation of the searching strengths; `SearchAi`, the one `AiPlayer`; `Fallback` (answers from the `Decision` alone) |
| `Search.kt` | `Searcher`: determinize → fork → answer an option → playout to a horizon → evaluate; option pruning, the playout policy, the one-ply "greedy" policy, the opponent model, public card memory; `Picker` (noise) |
| `Evaluator.kt` | The static evaluation in coin-equivalents (STRATEGY §2) as a sum of named `Term`s with scales (`EvalKnobs`), and `Ctx`, its per-state cache, which also scores "this hero standing on that space" without forking |
| `StaticPolicy.kt` | Scores every option of every `DecisionKind` from one state, no fork (STRATEGY §3): the playout policy, the opponent model and Easy |
| `CardProfile.kt` | Per-card numbers the heuristics need (attack, reach incl. "Before the attack: Move N", discard-or-defeat, blocks, a rating), read once from the catalog; nothing hero-specific |
| `AiCoach.kt` | `AiCoach(factory)`: `nudge` (Hard-level analysis from the human team's view) with `Explainer`, `prepare` (the same analysis before the answer); `turningPoints` with `TurningPoint` |
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
is never reached in practice. `AiPlayers.deterministic` removes it. `cancelled()` is checked before
every playout (a playout is 0.1–30 ms on the desktop).

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

Heroes with other shapes (Gydion's spellbook, extras, cards moved by text) fall back to swapping
identities within colour and Tier among hidden places. `HeroView.itemStats` is now sorted by stat,
because the deck order of the tucked cards told which colour each came from.

## Evaluation (STRATEGY §2)

`score = Lane + Life + Σ our heroes − Σ their heroes`, zero-sum, coin-equivalents; a finished game is ±1000.

- **Lane:** P(win the lane) from the Battle Zone offset and the Wave counters (the §1.1 recursion),
  with the next push's probability `σ(1.1 · projected margin)`; the projection subtracts each side's
  Push Potential (0.5 per attack card left for heroes within 6 spaces of the Battle Zone), and a
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
  `P(defeat) × (Life value + level coins + assists + 5 tempo) + P(discard) × card value`.
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
| 8 | Hard | mean − 0.3 sd for card plays | turn / turn | **6 / 2**, 8 | static; opaque one ply | opponent model | τ 0.25 within 0.8 | tuned | 1.5 s |
| 9 | Expert | mean | turn / turn | **16 / 6**, 10 | its own by **one-ply search** | + **memory** of cards shown since each level up | τ 0.15 within 0.6 (mixing) | tuned | 3 s |
| 10 | Expert | mean | turn / turn | 16 / 6, 10 | **every hero's** by one-ply search | + memory | τ 0.15 within 0.6 | tuned | 4 s |

"Tuned" is `TUNED`: exposure × 0.3, minion opportunity × 2 (Evaluation, above). "Opaque" choices are
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
  playout by one-ply search, and it mixes between near-equal plays.

The coach analyses at Hard (8) and so uses the tuned evaluation too.

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
  (49.0%, 192 games at Hard).
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

`TimingTest` with `AI_TIMING_GAMES=8`, desktop JVM (development machine, shared with other work,
2026-10-25), production players (`forStrength`, wall-clock cap on), eight mixed line-ups per
strength seating all 32 heroes once, both teams' decisions with more than one option (840–1 330 card plays
per strength). A phone is assumed 3–4× slower. Other decisions get a third of the card budget.

| Strength | Card play mean / p95 / max | Other decisions, means (max) | Slowest heroes, card play p95 / max | Device budget per card |
|----------|----------------------------|------------------------------|-------------------------------------|------------------------|
| 1 | 0.5 / 1.2 / 35 ms | 0.3–5.4 ms (111) | — (the first strength of the process pays the JIT warm-up) | 50 ms |
| 2 | 0.2 / 0.3 / 3.9 ms | 0.1–2.0 ms (30) | — | 50 ms |
| 3 | 0.1 / 0.2 / 2.8 ms | 0.1–0.9 ms (4.3) | — | 50 ms |
| 4 | 0.1 / 0.2 / 8.9 ms | 0.1–1.0 ms (7.4) | — | 50 ms |
| 5 | 6.8 / 22 / 44 ms | 0.3–10 ms (40) | Snorri 40 / 44, Razzle 39 / 39, Cutter 33 / 34 | 200 ms |
| 6 | 5.2 / 15 / 49 ms | 0.3–12 ms (169) | Snorri 38 / 49, Rowenna 35 / 40, Cutter 35 / 40 | 300 ms |
| 7 | 5.6 / 19 / 52 ms | 0.2–9.3 ms (154) | Rowenna 47 / 52, Cutter 38 / 48, Bain 30 / 43 | 500 ms |
| 8 | 39 / 117 / 577 ms | 0.7–25 ms (345) | Rowenna 402 / 577, Cutter 319 / 475, Takahide 200 / 200 | 1.5 s |
| 9 | 117 / 314 / 1 088 ms | 3–73 ms (781) | Bain 730 / 988, Emmitt 469 / 595, Snorri 442 / 685 | 3 s |
| 10 | 213 / 514 / 1 156 ms | 6–128 ms (824) | Bain 957 / 1 075, Min 823 / 831, Silverarrow 769 / 866 | 4 s |

At 4× the desktop time every strength stays inside its budget at p95 (10: ≈ 2.1 s; 9: ≈ 1.3 s; 8:
≈ 0.5 s). The worst card play at 10 (1.2 s desktop, ≈ 4.6 s at 4×) and the worst move choices at 9
and 10 (0.8 s, ≈ 3.2 s against caps of 1 s and 1.3 s) would be cut by the wall-clock cap on a slow
phone (fewer samples, still legal). Against the old Expert (176 / 408 / 832 ms), strength 10 (the same
samples and playouts, the tuned evaluation) costs about a quarter more at p95. In a browser (the web app's
engine worker, WebKit on the development machine, ADR-043 W4 item 4) the search runs about 8× the
desktop JVM's time: strength 10's card plays took 1.8 / 3.1 / 3.2 s (median / p90 / max), inside the
4 s budget; its other questions reach their 1.3 s cap. 1–8 stay far below their budgets there too. Strengths 5–7 cost
about twice the old Normal (3.6 / 10.5 / 29 ms) and stay far below their budgets. The coach's
`prepare` averaged 16 ms (max 157 ms) per decision over one Easy-vs-Hard game on mixed line-ups
(`CoachAheadTest`).

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
| 10 vs 9 | 214–170 | 55.7% | 50.7–60.6% | +40 | everyone's one-ply playouts |

10 vs 9 is over 384 games (line-ups 900–995 and 1300–1395: 54.7% and 56.8%). Every step's rate
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

`turningPoints` replays the save (`GameEngine` needed), evaluates the seat's winning chance
`σ(score / 15)` at every turn boundary and explains the 3–6 biggest swings, preferring turns where
something happened (a defeat, a push, a level, a forced defense, minions taken), oldest first, each
with the rule behind it.

## Known weaknesses

Read from game logs over all 32 heroes (`GameLogTest`, `UsageTest`, 2026-10-24).

- **Lane-first:** hero kills come from opportunities the playouts find, not from plans; there is no
  deliberate focus fire, card-kill setup over two turns, or "let the wave push itself". Since v0.7
  the searching strengths are much less cautious (exposure × 0.3), and more games end on Life
  counters: Hard with exposure × 0.3 against Hard, 47 of 192 games; with exposure × 1.5, 27 of 192.
- **Holding:** about a quarter of all actions are Holds (Hard self-play, 64 mixed games: 13%
  Mortimer to 35% Razzle). Most are chosen (moving would not improve the space), but Silver and Gold
  cards without a movement are often played on a turn their Primary has no target and are then held
  (Siren's Call, Death Trap, Crowd Control, Hurry up!, Inscribe the Runes about half the time they are
  played). The static round plan plays Silver early and the evaluation does not see that the card
  would have been worth more later; charging such plays measured no better (above), so the waste is
  mostly the spare card spent early.
- **Set-up heroes** are played through their pieces and counters only: an Illusion, Rock or Ice token
  is worth the same whatever it is for, so NebKher places Illusions where swapping enemy cards or
  setting up Mind Grip would be better and rarely attacks; Trinkets, Widget and Gydion gain least from
  deeper search (per-hero tables above). "Name a colour" (NebKher's Blue) and other choices whose
  effect comes next turn are looked at one ply deep, which sees nothing, so the first option wins.
- **Runes and other per-turn plans** (Snorri's rune under each turn slot, Swift's slot order, Cutter's
  Ultimate) are not planned over the round: the static round plan does not read them.
- **Fast Travel blocking**, spawn camping and post-push repositioning are not modelled explicitly.
- **Double lane:** the evaluation sums the lanes; it plays legally (smoke test) but has not been tuned.
- **Opponent model** is the static card policy: a human who plays unusual cards is guessed poorly
  (15% of each guess is uniform to stay robust).
- **The top end is flat:** above Expert (10) nothing measured better: 24 samples with 12 candidates,
  a longer horizon, a hotter or colder choice, a wider one-ply look, played-out upgrade choices, a
  sharper or looser opponent model (all in "What changed in v0.7"). The next gains need a better
  evaluation of set-up and round plans, not more search.
- **Wall-clock cap**: on a loaded phone the production player may search less than on the desktop;
  answers are then still legal but no longer reproducible for a seed.
- **Coach turning points** on quiet turns say only that the heroes "moved into position".

## Tests

| Test | What |
|------|------|
| `LevelLadderTest` | The named levels' strengths beat each other over a seeded, mirrored tournament (24 games on mixed line-ups from all heroes + 12 on the first-game heroes per pair): Easy (2) over 1 ≥ 60%, Normal (6) over Easy ≥ 75%, Hard (8) over Normal ≥ 65%, Expert (10) over Normal ≥ 75% |
| `NoPeekingTest` | Same public view, different hidden enemy cards → same answer at every strength 1–10; the determinizer keeps every public fact and samples only consistent upgrade histories |
| `DecisionKindsTest` | Every `DecisionKind` gets a legal answer at every strength: in AI games (every strength plays), a Clear action's token choice, and built questions of every kind |
| `AiSmokeTest` | Full AI games finish: every level, Long, 1v1, 2v1 with a Handicap card, the double-lane map |
| `AllHeroesAiTest` | 16 seeded AI-vs-AI games over shuffled line-ups that seat each of the 32 heroes twice (Easy, Normal, Hard, two at Expert): all finish, every answer accepted (2026-10-24: 5–15 rounds, all three win types) |
| `CoachTest` | Nudges on hand-built blunders (no defense, too weak a defense, walking into a revealed attack) and silence on the right choices; turning points of a finished game |
| `CoachAheadTest` | `prepare` + `nudge(analysis, …)` equals `nudge` on the blunder positions (every option) and on every answer of a game (every option of the first long lists); the session is unchanged; the answer-time cost |
| `CoachSurveyTest` | The coach speaks on fewer than 15% of a weak player's decisions, on mixed line-ups (`AI_COACH_GAMES` for more games) |
| `TimingTest` | Decision times per strength over mixed line-ups, the slowest heroes (`AI_TIMING_GAMES=8 AI_TIMING_STRENGTHS=…` prints the table above) |
| `ControllerStrengthTest` | A save with only a level loads as its strength; strengths round-trip; the bands |
| `TournamentTest`, `ExperimentTest`, `GameLogTest`, `UsageTest` | Opt-in (`AI_TOURNAMENT` / `AI_MIXED` with `AI_PAIRS=6:5,…`, `AI_EXPERIMENT=s10+samples=24:s10`, `AI_LOG`, `AI_USAGE` environment variables): long tournaments between strengths (per-hero rates; `AI_GAMES=<hero>` lists the games; a failing game is reported with its line-up and seed), profile variants (any knob or `x<TERM>=` scale), one game's log (a mixed or explicit line-up; `AI_LOG_DECISIONS`, `AI_LOG_VALUES` also for defenses, `AI_LOG_LEAVES=<hero>`), each card's actions, defeats and kills per hero |
| `FitTest`, `BlunderTest` | Opt-in (`AI_FIT=<games>:<strength>`, `AI_BLUNDERS=<strength>:<games>`): the evaluation's parts fitted to self-play outcomes (above); plays a decent player would call blunders, judged on the real game — a defense declined or too weak while one would hold, an action that defeats an enemy hero passed over, a move into a defeat another destination avoided |
