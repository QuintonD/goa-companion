# The computer opponents and the coach (`core.game.ai`)

ADR-035 point 3, PRODUCT §9. The strategy knowledge behind it is [STRATEGY.md](STRATEGY.md); this
file is how the code uses it, what each level does, what it costs and how strong it is.

## Architecture

The app binds these in `core/data/.../game/PlayEngineModule.kt`: `AiPlayers { level -> forLevel(level, seed) }`
and `AiCoach(factory)`.

| File | What |
|------|------|
| `AiPlayers.kt` | `AiPlayers.forLevel(level, seed)` (production, wall-clock capped) and `deterministic(level, seed)` (tests); the four `LevelProfile`s; `SearchAi`, the one `AiPlayer`; `Fallback` (answers from the `Decision` alone) |
| `Search.kt` | `Searcher`: determinize → fork → answer an option → playout to a horizon → evaluate; option pruning, the playout policy, the one-ply "greedy" policy, the opponent model, public card memory; `Picker` (noise) |
| `Evaluator.kt` | The static evaluation in coin-equivalents (STRATEGY §2) and `Ctx`, its per-state cache, which also scores "this hero standing on that space" without forking |
| `StaticPolicy.kt` | Scores every option of every `DecisionKind` from one state, no fork (STRATEGY §3): the playout policy, the opponent model and Easy |
| `CardProfile.kt` | Per-card numbers the heuristics need (attack, reach incl. "Before the attack: Move N", discard-or-defeat, blocks, a rating), read once from the catalog; nothing hero-specific |
| `AiCoach.kt` | `AiCoach(factory)`: `nudge` (Hard-level analysis from the human team's view) with `Explainer`; `turningPoints` with `TurningPoint` |
| `engine/Determinizer.kt` | Samples hidden information (below) |

One answer, every level but Easy: draw *N* determinized copies of the game from the deciding team's
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

## Levels

| | Easy | Normal | Hard | Expert |
|-|------|--------|------|--------|
| Search | none: static scores on one sample | each option played out once | 6 samples for card plays, 2 for other decisions | 16 / 6 samples |
| Horizon (cards / other) | — | end of turn / end of the action | end of turn / end of turn | end of turn / end of turn |
| Enemy cards | uniform guess | opponent model | opponent model | + memory of cards shown since each level up |
| Later choices inside a playout | — | static; opaque ones one ply deep | static; opaque ones one ply deep | one-ply search for every hero (its own, teammates', enemies') |
| Opaque choices at the root (bullets, tokens, swaps…) | one ply deep | searched | searched | searched |
| Threats seen | nearest enemy only, next-turn weight 0.12 | all, 0.22 | all, 0.22 | all, 0.22 |
| Minion modifiers when moving | ignored 40% of the time | always | always | always |
| Card plays | — | mean | mean − 0.3 sd | mean |
| Noise | softmax τ 1.2 within 4, 8% "beginner slip" among the top 3 | τ 0.6 within 1.5 | τ 0.25 within 0.8 | τ 0.15 within 0.6 (mixing) |
| Device budget per card play | 50 ms | 300 ms | 1.5 s | 4 s |

- **Easy** plays the round plan (Gold, Green, Silver first; Red late; Blue as the spare), takes what
  is in reach, and makes the beginner's mistakes STRATEGY §4.2 lists: it looks only at the nearest
  enemy, forgets minion modifiers when it moves, and sometimes picks the second or third best option.
  It never slips on a defense it can make or on an option worth a push or a kill.
- **Normal** is the honest greedy player: no deliberate mistakes, one playout per option against the
  most likely enemy cards.
- **Hard** plans the turn against what you might have played: six guesses of your hidden cards from
  the opponent model, each own card played through the whole turn, risk-averse.
- **Expert** samples more, remembers every card you have shown since your last level up, plays out
  everyone's actions by one-ply search instead of rules of thumb, and mixes between near-equal plays.

### Departures from STRATEGY.md §4.2, and why

- **Round planning** (Expert beam search over the card order to turn 4, regret matching): not built.
  Playouts to the end of the *next* turn measured worse than to the end of this one (39–44% against
  Hard, 36 games each) because the later turns are played by rules of thumb; Expert spends its time
  on more samples and smarter playouts instead. The round plan lives in the static card policy and the
  hand terms (spare, ratings).
- **Sample counts** are far below §4.2's 24–64: 6 (Hard) / 16 (Expert). Measured: Hard with 8 samples
  and one-ply self-play beat Normal 92–96%, too wide a gap with Expert on top; the lighter Hard keeps
  the ladder evenly spaced. Expert with 16 vs 8 samples gained only a few points.
- **Sampling enemy cards inside the playout** (enemies who play after you) from the opponent model
  measured worse (42%, 36 games) than letting them play their best static card; not used.
- **Normal's τ = 2** would make it visibly random on our score scale; τ 0.6 within 1.5 instead.
- **Easy's "highest-initiative card 50%"** rule: replaced by the static round plan plus noise, which
  reads more like a beginner than a fixed rule.
- **Upgrades**: static rating at every level (card stats + item worth by matching icons + a small Red
  bias); Expert's playout refinement is not built.
- **Team play**: AI teammates share the evaluation (so they rarely block each other or chase the same
  minion), but there is no explicit focus-fire plan and no "I'll take the minions" log line.
- **Tried over all 32 heroes and dropped** (Hard vs Hard, 192 mirrored games each, 2026-10-24): a
  charge of 1 or 2 for a card played only to hold (Silver and Gold cards whose Primary has no target
  are held about half the time they are played): 49% and 49%; a forced discard without a spare card
  costing 4.5 instead of 2.5: 50% (Normal 52%); Expert with mean − 0.3 sd: 63% against Hard where
  plain Expert made 68% on the same 128 games.

## Budgets and measured time

`TimingTest` with `AI_TIMING_GAMES=8`, desktop JVM (development machine shared with other builds,
2026-10-24), production players (`forLevel`), eight mixed line-ups per level seating all 32 heroes
once, both teams' decisions with more than one option (800–1 200 card plays per level). A phone is
assumed 3–4× slower.

| Level | Card play mean / p95 / max | Other decisions mean (max) | Slowest heroes, card play p95 / max | Device budget per card |
|-------|---------------------------|----------------------------|-------------------------------------|------------------------|
| Easy | 0.1 / 0.1 / 0.8 ms | 0.1–0.3 ms (1.8) | — (all under 1 ms) | 50 ms |
| Normal | 3.6 / 10.5 / 28.7 ms | 0.8–2.4 ms (33) | Cutter 29 / 29, Snorri 28 / 28, Rowenna 27 / 27 | 300 ms |
| Hard | 29 / 117 / 241 ms | 5–16 ms (111) | Snorri 191 / 241, Rowenna 187 / 231, Cutter 184 / 233 | 1.5 s |
| Expert | 176 / 408 / 832 ms | 32–105 ms (746) | Bain 617 / 728, Emmitt 545 / 581, Tali 500 / 509 | 4 s |

At 4× the desktop time every level stays inside its budget at p95 and at the worst card play
(Expert ≈ 1.6 s at p95, 3.3 s at worst; Hard ≈ 1.0 s worst; Normal ≈ 0.12 s). The figures are for a
warm JVM: the very first decision of a process paid up to 25 ms of JIT warm-up at Easy. Against the
earlier four-hero measurement the mean card play grew 2× (Normal), 3× (Hard) and 1.5× (Expert):
the opaque choices looked at one ply deep and the longer texts of the mixed roster (Snorri, Rowenna,
Cutter, Bain: long option lists and multi-step cards). Other decisions get a third of the card budget; Expert's worst move choice
(746 ms) would be cut by that cap on a slow phone (fewer samples, still legal). The coach's `nudge`
averages 47 ms (max 757 ms) per decision on the desktop over 12 mixed games.

## Measured strength

Mirrored tournaments (STRATEGY §4.5) over **mixed line-ups from all 32 heroes**
(`AiHarness.mixedLineups`: consecutive seeded shuffles of the roster cut into fours, so every hero is
seated equally often), the coin alternating, every game played twice with the levels swapping teams
(same seed and coin), Quick, Forgotten Island, `deterministic` players
(`AI_MIXED=96 AI_FIRST_SEED=96`). Final run on line-ups 96–191 (not used for tuning), 192 games per
pair, 2026-10-24; 95% Wilson intervals:

| Pair | Wins | Rate | 95% interval | ≈ Elo gap |
|------|------|------|--------------|-----------|
| Normal vs Easy | 178–14 | 93% | 88–96% | +450 |
| Hard vs Normal | 146–46 | 76% | 70–82% | +200 |
| Expert vs Hard | 115–77 | 60% | 53–67% | +70 |
| Hard vs Easy | 191–1 | 99% | 97–100% | — |
| Expert vs Normal | 161–31 | 84% | 78–88% | — |

On the tuning line-ups (0–63) Expert beat the same Hard 87–41 (68%, 59–75%); over both sets
202–118 (63%, 58–68%). Before this round of work, with the AI tuned on four heroes, the same harness
gave Normal–Easy 88% (79–93%), Hard–Normal 81% (72–88%), Expert–Hard 61% (51–71%) over 96 games on
line-ups 0–47; the new Hard beat the old Hard 57% (50–64%) and 59% (51–67%) on two sets. Games last
7.4–7.7 rounds; endings over the final Expert–Hard run: last push 60%, Life counters 23%, Throne 17%.
Expert's margin did not grow with more search: 24 samples or 14 candidates per decision made 59%
against Hard on the 128 games where 16 samples and 10 candidates made 68%.

The first-game heroes alone (the earlier measurement, seeds 100–107, 96 games per pair, before
this round): 94% / 80% / 65%. `LevelLadderTest` replays 24 mixed games (line-ups 200–211) plus 12
first-game games per pair on every test run (97% / 86% / 58%).

The gaps are uneven: Easy is a beginner and is meant to lose to anyone careful; Expert's margin over
Hard is the smallest because more search buys less once the playout policy and the evaluation are
the limit. Per hero (`TournamentTest` prints each level's win rate with every hero; 12 games each,
so ±25 points): the stronger level gains least with Gydion, NebKher, Tali, Trinkets and Widget,
the heroes whose cards work through set-up (spells, Illusions, Ice, the Turret, Pyro) that the
evaluation sees only as pieces and counters.

## The coach

`nudge` runs Hard-level playouts (6 / 4 samples, end-of-turn horizon) on copies determinized from
the human team's view, always including the human's choice. It speaks only when the choice is at
least 5 coin-equivalents below the best option on average **and** at least 1 below it in 75% of the
samples **and** a concrete reason is found; otherwise it says nothing. Reasons, in order: a defense
choice (a card that would stop the attack, a card too weak, a cheaper card that also works — with the
totals, p.14); a defeat the choice walks into (the attacker, its card when it is face up, the minion
modifier at that space, p.14); an empty hand (p.13); a lane push gained or given (p.8); a hero kill
missed (p.13); Fast Travel with the Red or Gold card (p.11); a minion left in reach (p.13, p.8).
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

`turningPoints` replays the save (`GameEngine` needed), evaluates the seat's winning chance
`σ(score / 15)` at every turn boundary and explains the 3–6 biggest swings, preferring turns where
something happened (a defeat, a push, a level, a forced defense, minions taken), oldest first, each
with the rule behind it.

## Known weaknesses

Read from game logs over all 32 heroes (`GameLogTest`, `UsageTest`, 2026-10-24).

- **Lane-first and cautious:** hero kills come from opportunities the playouts find, not from plans;
  there is no deliberate focus fire, card-kill setup over two turns, or "let the wave push itself".
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
- **Expert's margin** over Hard is 60–68% depending on the line-ups; more samples or candidates did
  not widen it. The next gains need a better evaluation of set-up and round plans, not more search.
- **Wall-clock cap**: on a loaded phone the production player may search less than on the desktop;
  answers are then still legal but no longer reproducible for a seed.
- **Coach turning points** on quiet turns say only that the heroes "moved into position".

## Tests

| Test | What |
|------|------|
| `LevelLadderTest` | Each level beats the one below over a seeded, mirrored tournament (24 games on mixed line-ups from all heroes + 12 on the first-game heroes per pair, thresholds 75% / 70% / 55%) |
| `NoPeekingTest` | Same public view, different hidden enemy cards → same answer at every level; the determinizer keeps every public fact and samples only consistent upgrade histories |
| `DecisionKindsTest` | Every `DecisionKind` gets a legal answer at every level: in AI games, a Clear action's token choice, and built questions of every kind |
| `AiSmokeTest` | Full AI games finish: every level, Long, 1v1, 2v1 with a Handicap card, the double-lane map |
| `AllHeroesAiTest` | 16 seeded AI-vs-AI games over shuffled line-ups that seat each of the 32 heroes twice (Easy, Normal, Hard, two at Expert): all finish, every answer accepted (2026-10-24: 5–15 rounds, all three win types) |
| `CoachTest` | Nudges on hand-built blunders (no defense, too weak a defense, walking into a revealed attack) and silence on the right choices; turning points of a finished game |
| `CoachSurveyTest` | The coach speaks on fewer than 15% of a weak player's decisions, on mixed line-ups (`AI_COACH_GAMES` for more games) |
| `TimingTest` | Decision times per level over mixed line-ups, the slowest heroes (`AI_TIMING_GAMES=8` prints the table above) |
| `TournamentTest`, `ExperimentTest`, `GameLogTest`, `UsageTest` | Opt-in (`AI_TOURNAMENT` / `AI_MIXED`, `AI_EXPERIMENT`, `AI_LOG`, `AI_USAGE` environment variables): long tournaments (per-hero rates; `AI_GAMES=<hero>` lists the games), profile experiments, one game's log (a mixed or explicit line-up; `AI_LOG_DECISIONS`, `AI_LOG_VALUES`, `AI_LOG_LEAVES=<hero>`), each card's actions, defeats and kills per hero |
