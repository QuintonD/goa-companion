# Review 3: an audit of the AI work through wave 3, and what stronger game AIs do

2026-11-24. The owner asked for a critical audit of the work done, the paths taken and their results, plus research
into how comparable games got strong AIs: "do not return with anything unvalidated or shallow". Wave 3 was stopped
first: C2's and E2's agents were cancelled, their work in progress committed (`t3/strength-10-search`,
`t3/train-leaf-net-backed-up-values`) and every branch pushed. One run was left to finish because it answers the main
question: W3 + NET6 against old s10 on line-ups 600–647 (below).

Six independent reports sit in `review-3/`. Three audit our own work, three are research:

| Report | Scope | Method |
|---|---|---|
| [V1-statistics](review-3/V1-statistics.md) | every headline number, the SPRT code, line-up hygiene, selection bias, cost | 1,058 raw summary lines and the per-game logs recomputed; Monte Carlo of the project's own stopping rule |
| [V2-code](review-3/V2-code.md) | strength 10 as it would ship (`t3/strength-10-search`) | code read at file:line; shadow-run logs recomputed |
| [V3-history](review-3/V3-history.md) | every AI era from v0.x to wave 3 | AI.md, ADRs, RESULTS, REVIEW-2, briefs |
| [R1-simultaneous](review-3/R1-simultaneous.md) | simultaneous-move games: SM-MCTS, Goofspiel, Pokémon, Diplomacy, RftG, RTS | papers and bot code read in full |
| [R2-cardgames](review-3/R2-cardgames.md) | imperfect-information card games: PIMC, Skat, Bridge, Hanabi, DouZero, poker | papers read in full (one erratum added) |
| [R3-methodology](review-3/R3-methodology.md) | how engines test, tune and train: fishtest, Texel/SPSA, NNUE, AlphaZero, ExIt, Gumbel | fishtest API data (4,000 runs), docs and papers |

The integrator re-checked the findings this review rests on (marked "checked" below) in the code and logs.

## 1. Where strength 10 stands

**The 65% target against old s10 is not yet shown, but the shipping candidate is now plausibly at it.** W3 + NET6
(`AiPlayers.SEARCH_10` on `t3/strength-10-search`) against old s10 on line-ups 600–647, 96 mirrored 2v2 games, finished
during this review: **67–29 = 69.8%** (Wilson 60.0–78.1; pairs 3 / 23 / 22, pair interval 61.3–78.3; LLR 4.3 at
SPRT(0.50, 0.55), 1.7 at SPRT(0.60, 0.65): undecided against 60%). On the same 48 line-ups and seeds W2T made 59.4%.
s10's 29 wins: last push 17, Life 10, Throne 2. The interval includes 65% and the range is on its third checkpoint
(below), so this says "probably 60–75%", not "≥ 65%". It is above what the chain of earlier results predicted (§1,
~62%). The deciding test is a run on unused line-ups (recommendation 2) and then the sealed 5000+ table.

| Version | Against old s10 | Games | Range | Note |
|---|---|---:|---|---|
| h10, the book alone | 24.0% (18.5–30.5) | 192 | 300–395 | the "traditional heuristics" player |
| W2 (search over the book, old net leaf) | 54.2% (47.1–61.1) | 192 | 300–395 | |
| W2T (+ tree after the reveal) | 56.8% (49.7–63.6) | 192 | 600–695 | |
| W2T 3v3 | 61.5% (51.5–70.6) | 96 | 300–347 | |
| W2 + NET6 | 55.5% (46.8–63.8) | 128 | 300–363 | |
| W2T on the same line-ups | 59.4% (49.4–68.7) | 96 | 600–647 | |
| W3 + NET6 | **69.8% (60.0–78.1)** | 96 | 600–647 | the shipping candidate, before the fixes in §2 |

What the numbers do and do not support (V1, recomputed):
- Every headline number is transcribed and computed correctly; the pentanomial SPRT is the standard GSPRT (fishtest's
  `LLR_alt2`); mirrored pairs are implemented right; results are deterministic and reproducible. s10 did not drift
  across waves 1–3. Nothing has touched the sealed range 5000+.
- **The 71.9% figures are not strength numbers.** W2T's 71.9% (64 games) was an SPRT at p1 0.6 stopped at its second
  look; under the held-out distribution that outcome has a ~1% chance, and the procedure reports ~66% on average when
  it stops there. W3's 71.9% (96 games, dev) was a single fixed-size run, unreplicated.
- **Gains measured against a sibling search carry over to s10 only partly.** All three earlier chains (W1→W2, W2→W2T,
  W2→NET6) over-predicted the next result against s10, by 28–75 Elo. Summing W3's in-family gains predicts ~72%;
  applying the observed carry-over gives ~62%. The W3 + NET6 checkpoint (69.8%) landed between the two, so this time
  the gains carried over better than before; one 96-game run does not settle which.
- **300–395 and 600–695 are not held out.** 300–395 decided the wave-1 flaw fixes before it was called held out and
  six tracks have looked at it since; 600–647 is on its third checkpoint. The same h10 code scored 70.3% on 300–395
  and 60.4% on 600–695 (p = 0.024): the ranges differ by more than their error bars.
- **Decisions were under-powered.** With the observed pair variance (~0.10), ±5 points needs ~300 games and +3 points
  ~1,700 to detect; SPRT(0.50, 0.55) needs ~420–670 games to finish. Runs were capped at 96–512, so many ended open and
  were pooled, and the reported rate at the first crossing is biased upward (a true 0.53–0.55 reads ~0.58). About 23
  variants were game-tested in wave 3 and 9 kept; 5 keeps rest on intervals that include 50% (W1, W2, F3, `knowLook`,
  and W2T before its pooled test).
- **Cost.** About 70 wall-hours (≈210 core-hours) of logged games in wave 3. A search game takes 100–180 s, so a run
  makes 60–90 games an hour. B2's 10 hours bought a keep in the HEval-leaf search, which does not ship (that leaf loses
  38.9% to the net leaf).

## 2. Defects in the strength-10 search (V2, checked)

None of these is an ADR-062 leak by the player: it reads only what strength 10 may read, and `NoPeekingTest` runs the
shipped configuration. The defects are in how it scores and models the enemy, and in shipping.

1. **Averaging is done on the logit scale, with ±1000 for a finished game.** The search takes the weighted mean of raw
   leaf values over its columns (`HybridAi.kt` ~298–315). The net leaf returns logit × ~37–39 coins (a clearly won
   position ≈ +40 to +120) but ±(1000 − round) for a finished game (`LearnedEval.kt` ~233, `Evaluator.kt:128`,
   checked). One column where a drawn enemy reply ends the game outweighs three normal columns: a card that wins ~66%
   by the net's own calibration can lose to one that wins ~5%. The project already has the fix (`WinChance`, used by
   the regret oracle for exactly this reason). This bites hardest in the last-push turns where s10 wins most of its
   games. Effect in games: not measured.
2. **The modelled enemy sees our face-down card.** In a playout the enemy's book answers on the fork without a
   determinization of its own. With `knowHands` it reads our `hand` (`HContext.defensesOf`, `killChance`), and our
   candidate card has already moved to `Zone.PLAYED` (`engine/Flow.kt` `moveCard(..., Zone.PLAYED)`, checked). So the
   enemy reply (column 0) and the drawn replies (columns 1–3) all react to the card we actually chose; real
   opponents redeal it. It affects Red-seat card plays only (seats play Red first). The search undervalues plays that
   are bad only if the enemy knows them, such as spending the last defense card. Effect in games: not measured.
3. **Red-seat card plays rest on four columns.** Known hands collapse the 24 samples to about one position, leaving
   one book reply and three random draws. Changing only the seed changed the card in 18 of 103 such plays (17.5%),
   against 1 of 36 with more columns (3v3 shadow run, recounted). Blue seats get no replies at all, so the same
   position is modelled differently by colour.
4. **The book's blind spots are the search's blind spots.** `hold` (hold the line) is off in every playout
   (`TurnPlan.kt:29`) and the push is only played out on turn 4; s10 still wins 70–74% of its wins against the
   search by the last push (31 of 44 and 23 of 39 on 600–695; 20 of 27 on dev).
5. **Shipping.**
   - Android and web create a new player for every decision (`EngineHost.aiFor` → `AiPlayers.forApp`, checked), so the
     memo is always empty while every playout step still hashes the state: the app is slower than every measured time.
   - No time bound: card plays reach 22 s in 2v2 and 23 s in 3v3 (14.7 s with `6.cap=12`) on oldboi; the web worker is
     single-threaded; old s10 was held to 2 s a card play on the phone (ADR-047).
   - `GameParityTest` runs the book and the old search only (checked), so nothing checks that phone and browser play
     the same new s10, and RESULTS.md's note that its strength-10 hash changes with the leaf switch is wrong.
   - The app's speed meter still measures the old search (`AiBenchmark.kt`, checked).
   - Search and playout exceptions fall back to the book silently, with no counter.
   - The branch head is work in progress ("uncompiled gates").
6. **The card choice is a pure best response with a fixed seed.** No mixing, no equilibrium; the same line gets the
   same reply every game, so a person can learn it. Its readability was never measured (V3: the old s10 was 88.9%
   predictable and an oracle exploiter beat it 60.9% over 256 games; no such run exists for the hybrid).

## 3. The paths taken (V3)

- **The pivot to traditional heuristics did not produce a strong player by itself.** The book alone is 24% against
  s10. Every point over s10 came from putting search back on top of the book with the old learned nets at the leaf;
  NET6 is the three v0.14 nets plus three fine-tuned from them. Two tracks (B, B2) re-learned what v0.9 had measured,
  that the learned leaf beats the hand evaluation (B2: 38.9% for the hand leaf over 144 games). What the pivot did buy:
  a fast, readable book for strengths 1–9 at about old s8's level, F1 (+15.6 points against s8), and a 4.8× faster
  search with the same answers.
- **One lever paid every time it was tried: a better playout policy and a longer look after the reveal** (one-ply
  playouts in v0.7, nets in the looks in v0.14, F1, the tree, W3's cheap F1 book).
- **Dead ends were repeated.** Longer horizons played by the same policy lost 7+ times across eras; the kill extension
  twice; joint team play three times; per-hero tuning was flat in three eras (the 32 playbooks measure 50.0% over 6,144
  games at h10).
- **Conclusions contradict each other across eras**: "fitted weights lose" (v0.7's fitted TUNED won), "regret matching
  measured nothing" (ADR-061 measured it even at 192 games, below the ~600 its own text says it needed), "the enemy-card
  guess buys nothing" (the oracle says knowing the card is worth 60.9%, and `rep=4` is built on the same weak guess).
- **The yardstick is self-referential.** The regret probe, the probe bench and the category swaps all use old s10 as
  the oracle, so they steer toward s10's choices, not past them. And no human game was ever logged, although the
  owner's complaint is about playing against it.
- **Unchecked keeps in the shipping configuration:** W1/W2's knobs (`K=24`, wider candidates, `rep=4`) on 128-game
  intervals, `6.cap=8` with no strength test, B2's `lastRace` never tried in W2T/W3, E2's training never run.

## 4. What stronger AIs for comparable games do (R1, R2, R3)

GoA, as strength 10 sees it, is a **simultaneous-move game with little hidden information**: hands are known, the
only unknown is the card each enemy puts face down, and after the reveal the turn is a perfect-information
sequential game. That places it next to Goofspiel, Pokémon battles and Diplomacy, not next to poker or Bridge.

1. **Best response to a model vs equilibrium.** The closest published analogue to our hybrid is Diplomacy's
   SearchBot: candidates from a base policy, rollouts by that policy, a value net, then a regret-matching
   equilibrium. Its best-response twin BRBot (our design) scores higher against the base policy (69.3% vs 52.7%) but
   loses head-to-head (11.1% vs 17.2%, par 14.3%; Gray et al., ICLR 2021, Table 3, checked in the paper). In
   Goofspiel (known hands, simultaneous cards, deterministic resolution) regret-matching search never lost
   significantly to the alternatives; in games with pure equilibria (Tron, Oshi-Zumo) UCT wins (Bošanský et al.,
   AIJ 2016). Which case GoA is can be measured cheaply: log the payoff matrices at real card plays and solve them.
   Old s10 is not an equilibrium player either (it mixes only among cards within 0.6 coins, temperature 0.15).
2. **Search over a fixed policy saturates; training the policy on the search's output breaks through.** Rollouts lift
   a base policy once (Tesauro & Galperin 1996, backgammon); the gain tracks the base policy's quality (Hanabi
   SPARTA); more search time stops helping (GIB; Scotland Yard 1M vs 10M simulations). The step that broke through in
   every case was expert iteration: train the policy and value on the searcher's choices and values, then search on
   the better policy (Hex ExIt, AlphaZero, DORA). Stockfish trains its NNUE purely on search scores (λ = 1.0). E2's
   pipeline is this idea; it never got to train.
3. **Deterministic expert rollouts can be worse than noisier ones** (Magic: the Gathering, Cowling et al. 2012), and
   letting the opponent choose among several continuation policies beats a single one (Brown, Sandholm & Amos 2018;
   Pluribus uses four). Our playouts are one deterministic book for both sides.
4. **Train the leaf on the positions the search asks about, with the search's values**, split by game (ReBeL, DeepStack,
   Student of Games; AlphaGo's value net overfitted until it took one position per game). NET6 is trained on turn
   boundaries of book games with game-result targets.
5. **Exploitability is a headline metric in strong systems** (SearchBot's human and RL exploiters, DORA's exploiters,
   AlphaStar's league). A pure deterministic responder that only beats a fixed deterministic bot is not evidence of
   strength against people.
6. **How engines spend games (fishtest, 4,000 recent runs):** a cheap short-time-control tier first, then a long one;
   bounds set for the effect sizes being tested; tests run to a verdict; about two thirds of all games go to
   rejected ideas. GoA has one tier only, at full cost (92 games an hour against s10), and a book-only screen does not
   predict search results (`lastRace` was even in h10 and +7.5 points inside x10). Position suites correlate with
   strength only across engines hundreds of Elo apart; our bench failed to predict games twice.

## 5. Recommendations, in order

These are what the evidence points to. None of them is measured yet; each needs its own test against old s10.

1. **Fix the four verified defects before anything else** (§2.1–2.3, 2.5): average win chances, not logits (the
   existing `WinChance`); answer enemy card plays in playouts on a copy where our face-down card is back in our hand;
   give Red and Blue seats the same reply model with more than three draws; cache the player per (game, seat) in the
   app, put a time bound on card plays, and add the hybrid to `GameParityTest`. These are corrections, not tuning:
   one SPRT against s10 for the scoring fixes together, decided to a verdict.
2. **Measure the owner's question directly.** W3 + NET6 (fixed) against old s10 on unused line-ups with
   SPRT(0.60, 0.65), about 380 games, instead of chaining in-family gains. Add two more numbers to every candidate:
   readability (the oracle exploiter, aimed at the hybrid) and the owner's own games (log them; they are the target).
3. **Solve the card choice as a game.** List the enemy's top-k cards as explicit columns, fill each cell with a
   playout + leaf averaged over enough samples, solve by regret matching, and play from the result (mixed). Judge it
   against s10 and against the exploiter. First run the cheap diagnostic: how often the logged card-play matrices
   have a pure saddle point.
4. **Expert iteration instead of hand knobs.** Train a card-choice policy and the leaf on the hybrid's own per-candidate
   values (E2's data, `~/.goa-qa/trackE2/data/sv-x.tsv`, is a start), then use the policy to propose candidates, as
   the enemy model and in the playouts. This is the route that broke through in Hex, Go, Hanabi and Diplomacy, and it
   also makes each decision cheaper.
5. **Change the development loop** (R3 §7): a calibrated reduced-budget tier to screen ideas, the full-budget SPRT run
   to a verdict for the survivors, a large progression check per release on 5000+; stop A/B-testing small knobs; spend
   games as training data (ExIt) and on SPSA for the few search knobs rather than one-at-a-time sweeps. More compute
   than oldboi (8 cores, 7 GB) is the owner's call.
6. **Stop repeating what the record already rejected**: longer horizons played by the same deterministic policy, kill
   extensions, per-hero weight tuning, the hand-written leaf for strength 10.
