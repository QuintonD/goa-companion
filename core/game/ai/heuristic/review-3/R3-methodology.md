# R3 — How strong engines turn compute into strength: testing, tuning, training (and what GoA should copy)

> Part of the wave-3 audit (2026-11-24); the synthesis is `../REVIEW-3-audit.md`. Paths under `/tmp/claude-1000/` were the
> agents' scratch folders at the time and may be gone.

Auditor R3, 2026-11-24. Read-only. Evidence tags on every claim:
- **[P]** primary source I opened (URL given; downloaded copies and extracted text are in
  `/tmp/claude-1000/-home-quinton-goa-companion/bb386302-b635-4d0f-906f-1e109ea75466/scratchpad/r3dl/`).
- **[D]** a number I recomputed from raw data (fishtest's public API, GoA run logs); the script is named and sits in
  `/tmp/claude-1000/-home-quinton-goa-companion/bb386302-b635-4d0f-906f-1e109ea75466/scratchpad/r3scripts/`.
- **[C]** code or a doc in this repo I read (path:line, branch).
- **UNVERIFIED** — anything I could not check. None of it is used in the conclusions.

---

## 0. The answer to the owner's worry, in one page

"We spend a lot of time playing games instead of improving the solution." Here is what the evidence says:

1. **Strong engines don't avoid games. They make each game cheaper and make it count for more.** Stockfish's
   fishtest played **359 million games in 6.5 months** (May 6 to Nov 23 2026, 4,000 finished runs) [D]. About 2/3 of
   the games in standard tests went to patches that were then rejected [D]. Short-time-control screening, SPRT
   stopping and paired openings exist to cut the cost per decision. None of them removes games from acceptance.
   The practitioners' sources agree that position suites and fitted proxies do not replace games (§4) [P].
2. **GoA's real cost driver is the price of one full-budget game.** x10 against s10 runs at a **median 92 games/h** on
   the 8-core box. The book against the book runs at **11,520 games/h**, and the book against s8 at **3,323 games/h** [D].
   That is a 35–125× gap. Fishtest's two tiers are only 6× apart (10+0.1 vs 60+0.6) [P]. The wave-3 workflow tests
   every candidate at the expensive tier, so GoA has no "STC" for search changes. It used to have one: nets were
   screened at strength 7, "192 games in 2–3 minutes" (AI.md:248) [C].
3. **The decision rule is too weak for the effects it decides.** With mirrored pairs (measured variance ratio 0.75), a
   96-game run has a 95% half-width of **±8.7 points** and resolves only effects of **≥12.4 points** at 80% power.
   192 games give ±6.1 and 8.8 [D]. Keeping a change when it scores ≥55% on 96 games keeps a useless change **13%** of
   the time and drops a real +5-point change **50%** of the time [D]. The SPRT(0.50, 0.55) in the code is sound when it
   runs to the end. At the usual cap of 192 games, though, it reaches H1 for a true +5-point change only **21%** of the
   time and is undecided 77% of the time (Monte Carlo of the code's own rule) [D].
4. **"Weights fitted to outcomes lose in play"** is a known phenomenon in the literature. GoA has real evidence of it
   for nets: λ=1 (result alone) scored 37.5% against 68.2% for bootstrapped targets, 192 games each, AI.md:255 [C].
   The HEval version of the claim is not established: three 64-game runs with 95% intervals of ±11 points that overlap
   the hand weights [C/D]. Practitioners use the engine's own positions, take only a few per game, score them with
   **search** rather than outcomes (Stockfish NNUE now trains at λ = 1.0, meaning "purely based on search scores"
   [P]), and still accept the result only after games.
5. **What would actually save time:**
   - (a) A validated cheap tier. Reduced-budget x10, *not* the book alone: B2's `lastRace` measured even inside h10
     but +7.5 points inside x10 [C].
   - (b) SPRT bounds that match the effect sizes, run to the end.
   - (c) Spending the game budget on methods where every game is data, not a verdict: SPSA for the ~10 search knobs,
     and Expert Iteration (x10 as the expert, a net trained on its choices with search-scored targets) for the
     policy and leaf.
   - (d) A cheaper search per decision (Gumbel/Sequential Halving at the root), which speeds up play and testing.

   §7 gives the loop with numbers. In my model the tiered loop's cost per *true* kept +5-point improvement is
   **~5–6 box-hours**, against **4–8 h** for today's habit. The time is similar. The difference is that false keeps
   fall from **8–19% to ≈0%** and 40% more real gains survive [D, model assumptions stated]. The time saving grows
   as effects shrink (§7.4).

---

## 1. Testing: Stockfish fishtest

### 1.1 What fishtest runs [P]
- **Bounds** (normalized Elo, α = β = 0.05). Standard STC `[0.0, 2.0]`, standard LTC `[0.5, 2.5]`, non-regression
  (simplifications) `[-1.75, 0.25]` for both. Source: `server/fishtest/templates/tests_run.html.j2` lines 375–378, and
  `elo_model = "normalized"` at line 6. α and β are set in `server/fishtest/views_run.py` (≈559–560, `alpha=0.05,
  beta=0.05`) (github.com/official-stockfish/fishtest, read via `gh api` 2026-11-24).
- **Time controls.** STC is 10+0.1 s and LTC is 60+0.6 s, 1 thread. "Scalers" run at 180+1.8 or with 8 threads
  (fishtest wiki *Creating my first test*, raw
  `https://raw.githubusercontent.com/wiki/official-stockfish/fishtest/Creating-my-first-test.md`).
- **Procedure.** "Run your test with … STC. If your STC test passes, create a new test with … LTC". "You should never
  stop a Standard test … SPRT … will automatically run as many games as needed" (same page).
- **Throughput.** A code comment in views_run.py says the batch "results in 32 games per batch for single threaded
  STC games … completed in roughly 2 minutes on a 8 core worker", so about 960 STC games/h per 8-core box.
- **Opening book.** `UHO_Lichess_4852_v1.epd` is used in 3,985 of the last 4,000 finished runs [D, fishtest API
  `/api/finished_runs`]. Positions are deliberately unbalanced (see §5).
- **Machine-speed independence.** "Nodestime allows SF to use a budgeted node count … making testing results
  independent of engine speed … Nodestime tests do not substitute tests under real TC" (wiki).

### 1.2 Normalized Elo and test length [P]
Van den Bergh, *Comments on normalized Elo* (https://www.cantate.be/Fishtest/normalized_elo_practical.pdf):
- t_n = (µ − ½)/σ_pg. In the pentanomial case σ_pg is "the standard deviation of the outcome distribution multiplied
  by √2, where we score the outcome of a game pair as 0, ¼, …, 1". Normalized Elo is e_n = 347.43·t_n.
- For α = β = 0.05, the worst-case expected length of an SPRT is **T = 1,046,535 / (e_n1 − e_n0)² games** (eq. 3.4).
  A width of 2 (fishtest STC or LTC) gives **261,634 games** worst case. The general expected length is T(h)/w² with
  T(h) = (2b/h)(1−e^{−hb})/(1+e^{−hb}) and b = ln 19.

### 1.3 What fishtest actually spends: 4,000 finished runs, 2026-05-06 → 2026-11-23 [D]
Script `fishtest_stats.py` on 80 pages of `https://tests.stockfishchess.org/api/finished_runs?page=N`.

| Test | Runs | Accepted | Rejected | Stopped/no verdict | Median games | p10–p90 games |
|---|---:|---:|---:|---:|---:|---|
| STC [0,2] 10+0.1 | 2,783 | 288 (10.3%) | 1,560 | 935 | 58,656 | 896–216,416 |
| LTC [0.5,2.5] 60+0.6 | 245 | 38 (15.5%) | 149 | 58 | 76,032 | 19,361–206,364 |
| STC non-regression | 393 | 139 (35.4%) | — | — | 68,896 | 307–253,229 |
| LTC non-regression | 68 | 40 (58.8%) | — | — | 139,527 | 8,948–375,531 |
| SPSA tunes (all TCs) | 111 | — | — | — | 40,694 | — |

- Of the standard STC submissions, ~1.4% end as LTC passes (38 / 2,783, same window; the populations overlap only
  roughly).
- 68% of STC games and 65% of LTC games went to tests that ended rejected (`share of games spent on rejected`).
  Rejected tests are not cheap: their median at STC is 75,520 games, because most patches are close to zero.
- 359 M games in the window (300 M of them at STC). My rough CPU estimate is ≈800 core-years, assuming a game uses
  base + 60·increment per side (**rough**).
- Strength gained over the window: master vs SF18 went from +13.08 Elo (2026-05-04) to +44.69 (2026-08-19), then
  master vs SF19 was +20.92 (2026-11-09). That is ≈52 Elo at 60+0.6 1 thread on the UHO book [P, Stockfish wiki
  *Regression Tests*, raw `…/wiki/official-stockfish/Stockfish/Regression-Tests.md`]. So fishtest buys roughly
  **0.15 Elo per million games**, or ~4–5 M games per accepted LTC patch. Stockfish is a mature engine, so its effects
  are tiny.

### 1.4 The pentanomial model and game pairs [P][D]
Fishtest scores openings as pairs (each side once) and models the five pair outcomes; the wiki "Fishtest
mathematics" page links the theory. I measured how much pairing reduces variance in each setting:
- **Fishtest** (pooled pentanomials of the window). Pair variance / (independent-game variance / 2) =
  **0.48** at STC and **0.42** at LTC, so pairing roughly halves the games needed [D].
- **GoA** (389 tournaments in agents' logs, `goa_pairs.py`). Median ratio **0.78** (IQR 0.65–0.89); pooled
  pentanomial 6,402 / 0 / 23,926 / 0 / 7,398 pairs, so **63% of mirrored pairs split 1–1** [D]. Pairing saves GoA
  about 25%, not 50%. That is real but modest. GoA's `Tournament.Sprt` implements the same normal approximation to
  the pentanomial GSPRT (`core/game/src/test/kotlin/dev/quinton/goa/core/game/ai/Tournament.kt:231` p0 0.5,
  α = β = 0.05; `:249` the LLR; `:263` MIN_PAIRS = 8) [C].

### 1.5 Why very fast time controls, and the known failure [P]
- **Throughput.** Everything is screened at 10+0.1. Only STC winners spend LTC games.
- **Scaling is checked, not assumed.** Speedup Elo at STC is 2.10·x% and at LTC 1.43·x% (Stockfish wiki *Useful
  data*, "Elo from speedups"), so effects shrink or change with time control. That is why LTC exists. Some search
  changes reverse sign: Stockfish PR #3937 (2022-02-17, *Search tuning at very long time control*) **failed STC**
  (LLR −2.94 after 4,968 games, bounds 0–2.5), measured +3.36 ± 1.8 Elo at LTC, then passed VLTC
  [P, `gh api repos/official-stockfish/Stockfish/issues/3937`]. A cheap tier can reject a good idea that scales.
- **GoA has the same failure in its own data.** B2's `lastRace` measured even as the book's own evaluation (51.2%, 256
  games), but +7.5 points inside x10 (57.5%, 240 games, SPRT H1) [C, HEVAL.md / RESULTS.md Track B2 on
  `t3/implement-shared-brief-task`]. So a GoA cheap tier must be **x10 at a reduced budget, not h10**.

### 1.6 Minimum resolvable effect and cost, against GoA [D]
Fishtest resolves 0–2 normalized Elo. With a 50% draw rate and a balanced book that is about 0–1.4 logistic Elo
(conversion table in the normalized-Elo note) [P]. It costs 60k–260k games, roughly 60–270 box-hours per test on
an 8-core worker at STC (§1.1 throughput).

GoA's paired per-game SD is 0.433 [D], so 1 point of win rate ≈ 8.0 nElo. GoA's H1 of 55% is **40 nElo**, 20× wider
than fishtest's bounds, so it needs ~400× fewer games. Script `power.py`:

| Games (mirrored) | SE | 95% half-width | Effect resolvable at 80% power |
|---:|---:|---:|---:|
| 64 | 5.4 pts | ±10.6 | 15.2 pts |
| 96 | 4.4 | ±8.7 | 12.4 |
| 192 | 3.1 | ±6.1 | 8.8 |
| 320 | 2.4 | ±4.7 | 6.8 |
| 512 | 1.9 | ±3.8 | 5.4 |
| 1000 | 1.4 | ±2.7 | 3.8 |

**SPRT(0.50, 0.55), untruncated:** 397 expected games when the truth is 50% or 55%, 650 at 52.5%, 147 at 60%. A
fixed-n test with the same α and β needs 812. **SPRT(0.50, 0.53):** 1,104 / 1,806 / 409. **SPRT(0.50, 0.52):**
2,484 / 4,064 / 920 [D, Van den Bergh's T(h)/w²].

At 92 games/h, one undecided-case SPRT(0.50, 0.55) at full budget costs 4–7 h. One SPRT(0.50, 0.52) costs 27–44 h.

**GoA's truncation** (`trunc_sprt.py`). I simulated the code's own rule: pairs, MIN_PAIRS 8, a check every 16
line-ups, stop at `AI_H_GAMES` (HeuristicTournamentTest:33–36 on `t3/strength-10-search`: "the pentanomial SPRT … on
batches of AI_SPRT_BATCH line-ups, at most AI_H_GAMES") [C].

| Cap | True 50%: H1 / H0 / undecided | True 55% | True 60% |
|---:|---|---|---|
| 96 | 0.4% / 5% / 94% | 5% / 1% / 94% | 32% / 0% / 68% |
| 192 | 1% / 20% / 78% | 21% / 1% / 77% | 83% / 0% / 17% |
| 320 | 2% / 44% / 54% | 45% / 2% / 53% | 98% / 0% / 2% |
| ∞ | 4% / 96% / 0% (446 games) | 96% / 4% / 0% (439 games) | ≈100% (151 games) |

The implementation reaches its nominal error rates when it runs to the end. Two consequences follow. A recorded "SPRT
H1" at 160–320 games (W3 vs W2T 59.4% at 160; lastRace 57.5% at 240) is consistent with a true effect of roughly
+8–10 points. And most real +5-point changes end "undecided" and get judged on the point estimate. Judged that way,
the rule "keep if ≥ 55%" has P(keep | true 50%) = 13% at 96 games, 5% at 192 and 18% at 64, and P(drop | true 55%) =
50% at any n [D].

Results selected on development line-ups also show winner's-curse shrinkage. W2T against s10 scored 71.9% on 64 dev
games and then 56.8% on 192 held-out games (CONTEXT.md headline claims). The gap is 2.4 combined SEs. Selecting
the best of several noisy 64-game runs produces exactly this, which is why fishtest re-tests at LTC instead of
trusting the STC estimate.

---

## 2. Tuning: Texel, SPSA, CLOP

### 2.1 Texel tuning (logistic regression on outcomes) [P]
CPW *Texel's Tuning Method*, a reprint of Peter Österlund's 2014 CCC posts
(https://www.chessprogramming.org/Texel%27s_Tuning_Method):
- **Data.** "64000 games played at a fast time control (such as 1s+0.08s/move) between the current and/or previous
  versions of the engine", about 8.8 M positions. The score is the **quiescence-search** score, not the static eval,
  with ~400 parameters. A local minimum takes "within 6 hours" on 16 cores.
- **Result.** "24.6 + 4.0 + 5.8 + 2.8 + 12.8 + 39.4 + 10.2 = 99.6 Elo", each step measured with 32,000 games. The
  +39.4 came from *including* positions whose q-search score differed from the game's search score. He had removed
  them as noise, but "the q-search function has to deal with them all the time in real games".
- **His concerns** are the GoA situation exactly: "Correlation does not imply causation … the chess engine would learn
  to appreciate positional features that are correlated to increased winning chances, even though actively striving
  to reach such positions does not increase winning chances". And "the chess engine will not be able to learn things
  that it does not understand at all": if the engine cannot win KBNK, the fit learns to score it as a draw.

### 2.2 The modern practice (Ethereal) [P]
Andrew Grant, *Evaluation & Tuning in Chess Engines* (https://raw.githubusercontent.com/AndyGrant/Ethereal/master/Tuning.pdf):
- "using a large number of positions from the same game has been shown to produce a lower quality dataset".
- The current dataset comes from "one million self-play games at various hyper-bullet time controls". Each sampled
  position is searched deeply and replaced by the end of its principal variation, a quiet leaf. "the increased
  diversity and the conversion of position to a quiet state, appears more important than the WDL result precision."

### 2.3 Games-as-gradient: SPSA and CLOP [P]
- **SPSA in fishtest.** "play a mini match using 2 different values … parameter_value ± ck … update … (ck·rk)·(wins −
  losses)". The guidance: "tuning many values at once (like a PSQT table) generates random change"; "If after a few
  thousand games the values are barely changing … the tuning run is useless". A tune's game count is fixed at
  creation (fishtest wiki). The 111 SPSA runs in my window had a median of 40,694 games [D]. Tuned values land as ordinary
  master commits. Example: "VVLTC parameters tweak" in Regression Tests, 2026-07-16 [P]. I did not open that
  commit's own SPRT links.
- **CLOP** (Coulom, https://www.remi-coulom.fr/CLOP/CLOP.pdf): "Tuning parameters without measuring win rates may
  sometimes work, but it is dangerous … optimizing a criterion such as temporal difference does not necessarily find
  parameters that maximize the probability of winning." The objective is simple regret: "losing games does not
  matter". SPSA is "very sensitive to a good choice of meta-parameters". CLOP is for a few smooth parameters.

### 2.4 "Weights fitted to outcomes lose in play": known? And GoA's evidence
- **Known, in this form:** a criterion that predicts outcomes is not the criterion that maximizes wins. Sources: Texel
  (causation concern, §2.1), CLOP's warning (§2.3), and ExIt below, where two nets have the same prediction error but
  play 50 ± 13 Elo apart (§4). Modern engines answer by changing the **target**, not by fitting harder:
  - Texel used q-search scores.
  - Ethereal uses deep-search leaves.
  - Stockfish NNUE now trains at **λ = 1.0 = "purely based on search scores"**. The nnue-pytorch `LambdaController`
    docstring says "lambda_ = 0.0 - purely based on game results … 1.0 - purely based on search scores". The current
    nettest recipe `threats.yaml` sets `start-lambda: 1.0`, `end-lambda: 1.0`, with a cosine dip
    `lambda-cycle-delta: -0.3` [P,
    https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/master/model/lambda_utils.py,
    https://raw.githubusercontent.com/vondele/nettest/master/threats.yaml].
- **GoA's own strong evidence (nets).** AI.md:255–268 [C]: "Trained on the game result alone (λ 1) … 37.5%", against
  λ 0.7 at 68.2%, 192 games each. That is ~30 points, many SEs. AI.md's explanation: "the result alone is the target
  that predicts results best (held-out log-loss 0.552 …) and plays worst. A search picks the option whose leaf the
  evaluation overrates." This is the literature's lesson, reproduced. Note that GoA's λ is inverted relative to
  nnue-pytorch: GoA λ 1 = result.
- **GoA's weak evidence (HEval).** RESULTS.md Track B on `t3/implement-shared-brief-task` [C]:
  - Set 1 fitted to outcomes as x10's leaf: 43.8% against s10, **64 games, Wilson 32.3–55.9**.
  - Set 3: 35.9% (25.3–48.2).
  - Distilled: 40.6% (29.5–52.9).
  - The hand leaf, measured in another run on the same line-ups: 43.8%.
  - As h10's evaluation, set 1 scored 49.2% (128 games, 40.7–57.8).

  These intervals overlap each other and the hand weights. The data show "**not shown better**", not "loses". The fit
  also departs from the practice in three ways:
  1. ~52 correlated positions per game: 62,782 positions from 1,200 games, ~44 games per fitted term. Texel used
     64,000 games for 400 terms, ~160 per term.
  2. Outcomes come from h10 and s8 play, while the weights are used under x10 play.
  3. Labels are results, not search scores.

  The project's later within-sample leaf-ranking fit against the net (HEVAL.md "Calibration pipeline" step 3) is the right shape: search-backed
  targets on the positions the search actually evaluates.

---

## 3. Training: NNUE, AlphaZero, Leela, KataGo, ExIt, Gumbel

| System | Game | Compute (as stated) | What made it efficient | Source |
|---|---|---|---|---|
| AlphaZero | chess, shogi, Go | 5,000 1st-gen TPUs for self-play + 64 2nd-gen TPUs for training; 700k steps; chess 9 h / 44 M games, shogi 12 h / 24 M, Go 34 h / 21 M; 800 sims | General method; **dropped AlphaGo Zero's 55% gating match**: "omitting the evaluation step and the selection of best player" | arXiv 1712.01815 [P] |
| KataGo | 19×19 Go | Surpassed ELF after 19 days on ≤ 28 V100s (≈1.4 GPU-years) vs ELF ≈74 GPU-years: "a factor of 50 reduction"; "at least an order of magnitude more efficient than … Leela Zero"; "strong or top human amateur strength … on even single GPUs in mere days" | Playout-cap randomization: full search (600→1000 nodes) on p = 0.25 of moves, only those recorded; fast 100→200 otherwise. Ablation factors: playout cap 1.37×, forced playouts + policy-target pruning 1.25×, global pooling 1.60×, auxiliary policy 1.30×, ownership/score targets 1.65× | arXiv 1902.10565 [P] |
| Expert Iteration | 9×9 Hex | MCTS 10,000 iterations/move; ~7.4 M expert move choices in the final value+policy phase; per-move timing on 1 Xeon E5-1620 + Titan X (total training hardware not stated: UNVERIFIED) | Separate planner (search) from generaliser (net); **tree-policy targets** (cost-sensitive); online dataset growth; beat MoHex 1.0 75.3% at equal iterations | arXiv 1705.08439 [P] |
| Gumbel AlphaZero/MuZero | 9×9 Go, chess, Atari | — | Sample root actions without replacement + Sequential Halving (simple regret); policy target from completed Q-values; "Gumbel Mu Zero learns reliably even with 2 simulations" on 9×9 Go, where "Mu Zero fails to learn from 16 or less simulations" | ICLR 2022 full text (mirror) [P] |
| MiniZero | 9×9 Go, 8×8 Othello | **One machine, 2 Xeons + 4 GTX 1080Ti**; 600k self-play games per model; 3 residual blocks | Gumbel at n = 2/16 learns; "using more simulations generally results in higher performance" | arXiv 2310.11305 [P] |
| AlphaZeroGeneral sweep | 6×6 Othello | One GTX 980 Ti per run; default run 44.0 h | "arenacompare … is a key factor of time cost … too large value is time-sensitive and not necessary at all" | arXiv 1903.08129 [P] |
| Polygames | Hex 19×19, Havannah | (compute not extracted: UNVERIFIED) | Size-invariant nets, train at 13×13, play 19×19; beat strong humans | arXiv 2001.09832 [P] |
| Stockfish NNUE | chess | "On a high-end GPU, training a big net takes approximately 4-5 days"; ~700–800 GB of data | λ = 1.0 search-score targets on relabelled positions. File names such as `…relabel-BT4…binpack` suggest Leela-BT4 relabelling (inferred from names: UNVERIFIED) | nettest README, threats.yaml, nnue-pytorch docs [P] |

Lessons that carry over to GoA:
1. **Train on search-improved targets, not raw outcomes.** ExIt's tree-policy targets, KataGo and AlphaZero visit
   distributions, Gumbel's completed Q-values, and NNUE at λ = 1. GoA's λ-0.7 bootstrapped nets and its newer
   search-value data (Track E2) are on this path [C, `~/.goa-qa/trackE2/train2.sh` comments].
2. **Spend expensive search only where it is recorded** (KataGo's playout-cap randomization, measured 1.37×). In GoA:
   run full x10 on a sampled fraction of decisions, and let the book or a cheap search answer the rest of the game.
3. **Denser targets than win/loss are worth about 1.65×** (KataGo ownership/score). In GoA the natural auxiliaries
   are Life counters lost, pushes, wave state and final margin. This is an analogy, not measured for GoA.
4. **Gating matches can be dropped in a learning loop** (AlphaZero; the AZG sweep). Monitor with a periodic
   fixed-opponent match instead of gating every generation.
5. **Smallest-scale successes run on one machine with GPUs**: MiniZero (4× 1080Ti), AZG Othello (1 GPU, ~44 h per
   run), KataGo's single-GPU claim. oldboi has 8 CPU cores and 7 GB and no GPU. GoA's net is small (248 inputs, pure
   Kotlin trainer), so training is not the bottleneck. **Expert labels are**, at 92 x10 games/h. ExIt-scale data
   (millions of decisions) needs cheaper experts, which means Gumbel-style few-sample policy improvement and
   playout-cap randomization.

---

## 4. Position suites and regret against games

- **Practitioners.** CPW *Test-Positions*: "In recent years testing positions has fallen out of favor among top engine
  developers. SPRT tests are now generally regarded as a superior method for engine testing" [P]. Stockfish accepts
  patches only through fishtest SPRT (§1.1).
- **Suites against Elo, across engines.** STS total score correlated r = 0.797 / 0.773 / 0.785 with WBEC, CEGT and
  CCRL (25, 38 and 31 engines); individual STS suites gave 0.32–0.69 (Jouni, CCC, 2010-03-29/30,
  https://talkchess.com/viewtopic.php?p=339955) [P, forum]. That is r ≈ 0.8 across engines hundreds of Elo apart.
  It is far too coarse to decide a 5-point change.
- **Prediction metrics against play.** ExIt: "despite the very similar prediction errors, the TPT network is 50 ± 13
  Elo stronger than the CAT network" [P]. Ruoss et al. 2024 (arXiv 2402.04494) report a Lichess Elo that differs
  between play against humans and play against bots, and give three hypotheses [P]. Relevant because the 65% bar
  stands in for "feels strong to a human".
- **GoA's own data agree** (REVIEW-2-losses.md §2 and §5 on `feature/heuristic-ai` [C]):
  - "Summed regret does not separate won from lost games" (won 87 pp, lost 77 pp).
  - The oracle's largest disagreement (actions) was worth **−0.8 points** in games when swapped to s10.
  - Own moves, ranked 2nd and 6th by regret, were worth **+11.7**.
  - HEval's fitted sets improved log-loss (0.593 → 0.587) with no measurable play gain (§2.4).

  So: use the position bench and regret probe to **find and diagnose** problems and as a fast regression alarm. Never
  use them as the acceptance test.

---

## 5. Variance reduction for game-based evaluation

| Technique | Measured saving | Where | GoA status |
|---|---|---|---|
| Paired / mirrored games, pentanomial | Variance ×0.42–0.48 (≈2× fewer games) in chess | [D] fishtest window | **×0.75–0.78 (≈1.3×)** measured [D]; already used |
| Unbalanced opening book (UHO vs a balanced book) | Same engine pairs on UHO vs noob_3moves: **3.1× fewer games at STC equal time, 6.8–7.7× at LTC and with time odds** (one time-odds point ≈1.0) | [D] from raw pentanomials in Stockfish wiki *Useful data*, "Equivalent time odds" | Analog: choose line-ups where strength, not the line-up, decides the pair. 63% of GoA pairs split [D]. Untested, and must keep a uniform held-out set |
| Common random numbers (same line-ups and seeds for both variants against the same opponent) | GoA swap log (128 games each vs s8): `h10+earlyRed=0` vs h10 outcome correlation **r = 0.66**, so Var(difference) ×0.34. Category swaps answered by s10: r = 0.07–0.36 | [D] `crn.py` on the agent's `swap.log` | Pays only when the baseline's games are **cached and reused** for many candidates and the change is small and deterministic: ≈1.5× better than head-to-head then. Little gain for stochastic search changes |
| SPRT (sequential stopping) | ~51% fewer games than fixed-n at H0/H1, ~20% at the midpoint (8.67 vs 10.82 /w²) | [D] from Van den Bergh's formula | Implemented. Loses its value when truncated (§1.6) |
| Control variates with a value estimate (AIVAT) | HUNL poker: SD ÷3, "more than a factor of 10" fewer hands | arXiv 1612.06915 [P] | Possible later: GoA has a value net and knows its own strategy. Needs research. Unbiasedness depends on correct use |
| Reduced budget (STC, nodestime) | Fishtest STC/LTC = 6× cheaper; 10% of STC submissions pass | [P][D] | GoA: x10 vs s10 at 92 g/h against 3,323 g/h for book vs s8. No validated reduced-budget x10 tier yet |
| Start from mid-game positions | Book exits at fixed depth (fishtest); Ethereal suggests restarting games from resolved positions [P] | | GoA games average 6.7 rounds (608 tournament lines) [D]. Starting at round 3 would cut ~30%. Unmeasured; risks skipping early mistakes |

---

## 6. What is weak or wasted in the current GoA loop (from the above)

1. **No cheap tier for search changes.** Every wave-3 candidate is measured at x10 full budget against s10, 46–109 g/h
   [D]. The old net pipeline had a cheap screen (AI.md:248, 192 games in 2–3 min) [C].
2. **Runs are sized below what the effects need.** Median table run is 192 games, IQR 144–256 (rough parse of
   RESULTS.md on `t3/strength-10-search`) [D]. That resolves ~9 points, while the decisions are about 5 points.
3. **SPRT truncated at the run cap.** As simulated in §1.6, most real +5-point changes end undecided, then get judged
   on the point estimate.
4. **Development-line-up winners are not re-tested at the same size before stacking.** W2T fell from 71.9% (64 dev)
   to 56.8% (192 held-out).
5. **Proxies were used as verdicts in places.** The fitted-HEval conclusion rests on 64-game runs. Literature and GoA's
   own regret study both say proxies mis-rank.
6. **Games are used only as verdicts.** No SPSA for the ~10 search knobs (K, topS, topU, rep, td, tw, cap, …), which
   were set by one-at-a-time A/B runs. One-at-a-time runs cannot see interactions and spend ~200 games per bit of
   information.

---

## 7. Proposed development loop for GoA

### 7.1 Measurement tiers (each with its evidence)
- **Tier 0 — no games, minutes.** Rules and no-peek tests, the position bench and regret probe as a *regression
  alarm*: flag a change that alters many answers or regresses on puzzles. Not an acceptance test (§4).
- **Tier 1 — "STC", reduced-budget x10 against s10** (or against the current champion), with full-budget s10 or a
  proportionally reduced s10. The exact budget must be calibrated (below).
  - Test: SPRT(0.50, 0.54), α = β = 0.05, mirrored pairs, **run to a verdict**.
  - Expected length: 621 games at the hypotheses, ~1,016 worst case [D].
  - Throughput: ~900 g/h if the reduced budget is ~10× cheaper. **That is an assumption to measure**; for comparison
    book vs s8 runs at 3,323 g/h.
  - Cost: ~0.7–1.1 h per idea.
  - Evidence for the tier: fishtest STC → LTC (§1.1, §1.3).
  - Evidence against the cheap variant: h10-only screening misses search gains (`lastRace`, §1.5).
- **Tier 2 — "LTC", full-budget x10 against s10 on fresh line-ups.**
  - Test: SPRT(0.50, 0.55) run to a verdict. 397 games at the hypotheses (~4.3 h at 92 g/h), ~650 worst case (~7 h)
    [D]. Only Tier-1 passes go here.
  - Evidence: fishtest's LTC confirmation and the measured winner's-curse shrinkage (§1.6).
- **Tier 3 — progression check**, weekly or per release. 1,000 held-out games of the current champion against s10
  (≈11 h, ±2.7 points) and against the previous champion. This is Stockfish's regression-test practice (60,000 games
  per point, §1.3) scaled to GoA's effect sizes.
- **One-time calibration before trusting Tier 1** (~1–2 box-days). Re-run 6–8 already-decided search changes at the
  reduced budget: W3 vs W2T, NET6 vs net leaf, lastRace, cap=12, td=3, round horizon. Require the sign to agree in
  ≥ 5 of 6 and the reduced-budget effect to be ≥ 50% of the full-budget one. Fishtest's #3937 shows why this check
  matters.

### 7.2 Making the games count more
- Keep mirrored pairs. Add **CRN with a cached baseline** for small deterministic book changes (r = 0.66 measured, so
  ≈1.5× fewer games than head-to-head).
- **Line-up selection** (UHO analog). From existing logs, estimate per line-up how often pairs split. Pilot a
  "decisive line-up" set for Tier 1 only, and keep Tier 2 and 3 on uniform held-out line-ups. In chess this gave 3–8×
  [D], but GoA's gain is unknown.
- **Mid-game starts** (round 3 from stored positions) for Tier 1 only: ~30% less time per game [D: 6.7 rounds], at
  the cost of not testing opening play.

### 7.3 Spending games as data, not verdicts
- **SPSA on the search knobs** at Tier-1 budget. Fishtest-style: ±c_k perturbation, one mirrored pair per iteration,
  5–10 parameters at a time, a few thousand pairs (fishtest median tune 40k games; guidance: stop if values don't move
  after a few thousand). At ~900 g/h, 10,000 games is ~11 h of box time. The tuned values then go through Tier 2
  like any patch [P].
- **Expert Iteration for the policy and the leaf.** The expert is x10, the apprentice a policy+value net.
  - Targets: x10's per-candidate values (tree-policy / completed-Q targets, not just the chosen move: ExIt's TPT +50
    Elo; Gumbel), and search-scored values as in NNUE λ = 1, with GoA's bootstrapped λ as a fallback.
  - Data: full x10 on ~25% of decisions (KataGo's playout cap); keep only a few decisions per game for diversity
    (Ethereal).
  - Use: the apprentice prunes the book's candidate list and orders the samples, which makes x10 cheaper and Tier 1
    and Tier 2 faster.
  - No gating matches between generations (AlphaZero), only Tier 2 on the final candidate.
  - Data scale is the limit. At 92 full-budget games/h, a 24-h run yields ~2,200 games. Decisions per game were not
    measured here, so the number of labels is UNVERIFIED.
- **Cheaper decisions in x10 itself.** Sequential Halving with Gumbel sampling at the root spends samples on
  surviving candidates only and is designed for small budgets (Gumbel paper). Any speed-up to x10 speeds both play
  and every test. It also attacks the 3v3 card-play time over the ~10 s budget.

### 7.4 Expected time per kept improvement (model, `loop_model.py`)
Assumptions:
- Prior over ideas (illustrative, not measured): 25% at −2 points, 35% at 0, 25% at +5, 15% at +10.
- x10 at 92 g/h; Tier 1 k× cheaper.
- The Tier-1 result transfers fully, or 70% of the gain survives (one row).

| Loop | Box-hours / idea | True keeps / idea | Box-hours per true keep | Share of keeps that are false |
|---|---:|---:|---:|---:|
| Today: 96 games, keep if ≥ 55% | 1.0 | 0.26 | 4.1 | **19%** |
| Today: 192 games, keep if ≥ 55% | 2.1 | 0.27 | 7.8 | 8% |
| SPRT(0.50, 0.55) full budget, to verdict | 3.5 | 0.39 | 9.0 | 5% |
| Tiered, Tier 1 10× cheaper | 1.9 | 0.38 | **4.8** | **≈0%** |
| Tiered, Tier 1 20× cheaper | 1.6 | 0.38 | 4.2 | ≈0% |
| Tiered 10×, only 70% of the gain transfers | 2.5 | 0.34 | 7.5 | ≈0% |

Reading the table:
1. At today's effect sizes (+5 to +10 points), **the loop does not change the hours per keep much**: it stays at
   4–8 box-hours. What it changes is that **false keeps go to ≈0** and **~40% more real gains survive** (0.38 vs 0.27
   per idea). False keeps matter because they compound: stacking a −2-point change hides behind later gains.
2. **The saving grows as the engine matures.** Games needed scale as 1/δ². At +2-point effects, full-budget SPRTs cost
   27–44 h each (§1.6), and a 10× cheaper Tier 1 cuts that roughly tenfold for the ideas that fail. Fishtest exists
   because Stockfish lives in that regime.
3. **The biggest lever on "time spent playing games" is not statistics.** It is the cost of one full-budget game
   (92 g/h) and turning games into training data (SPSA, ExIt). Both appear above.

---

## 8. Unverified / caveats
- **GoA throughput** comes from H_TOURNAMENT lines in agents' logs. Thread counts and machine load varied: x:s n = 11,
  x:x n = 29, h:s n = 100, h:h n = 59.
- **Fishtest CPU-years** is a rough estimate. "Stopped" runs (state empty) may include purged or deleted tests.
- **Leela-specific compute** was not found on the page I opened (lczero.org technical explanation): UNVERIFIED.
- **The ExIt total training hardware** is not given in the text I read: UNVERIFIED.
- **NNUE data provenance** ("relabel-BT4" = rescored by Leela's BT4 net) is inferred from file names only.
- **The §7.4 prior** is illustrative, and the Tier-1 speed factor and transfer have to be measured on GoA (§7.1
  calibration).
- **The RESULTS.md parse** (median 192 games, 10 yes / 15 no "Kept" cells) is rough.

## Appendix: files
- Downloads: `…/scratchpad/r3dl/` (fishtest API pages `fr/p1..p80.json`, fishtest code, wiki pages, papers as PDF
  and txt).
- Scripts: `…/scratchpad/r3scripts/`:
  - `fishtest_stats.py`: fishtest pass rates, games, pentanomial variance.
  - `goa_pairs.py` with `goa_pairs.txt`: GoA pair-variance ratio.
  - `rates.py`: GoA games/h by match-up.
  - `power.py`: SE, MDE, SPRT lengths.
  - `trunc_sprt.py`: GoA's truncated SPRT, Monte Carlo.
  - `crn.py`: common-random-number correlation from `swap.log`.
  - `loop_model.py`: §7.4.
