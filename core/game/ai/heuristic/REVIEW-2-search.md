# Review round 2: search on top of the heuristics (2026-11-21)

Owner goal: the strongest computer player whatever the device. Question: what does lookahead buy once it is
built on the heuristic book (policy, candidate generator, opponent model) and HEval instead of the old static
policy? Prototype in `core/game/src/main/kotlin/dev/quinton/goa/core/game/ai/hybrid/` (opt-in, not wired into
`AiPlayers`); tournament specs `x10+…` and `y10+…` in `HeuristicTournamentTest` and `HeuristicDiagTest`.

## What was built

**(b) `x10`: heuristic-guided determinized search** (`HybridAi`). For a searched decision:

1. K determinizations from the deciding team's view (`EngineSession.determinizeWith`, strength 10's view:
   enemy hand pools known per ADR-062, the facedown card dealt again from the book's opponent model,
   `model=1`: softmax τ 2 of the hero's own static book card scores, 15% uniform).
2. Candidates: every card at a card play; at another question the book's own answer (h10, its real answer)
   plus the book's best `top` (4) by static score.
3. Each candidate is answered on a fork of the sample and played out to the horizon by the book for every
   seat, enemies included (deterministic best answer: temperature 0, no G17 mixing). `pol=0` is the book with
   looks only at opaque choices (card plays from static scores); `pol=1` is the book exactly as h10 plays it
   (CardSim at card plays, one-ply looks), on the sample.
4. The leaf is scored by HEval (`leaf=0`), the old hand evaluation (`leaf=1`) or the old learned ensemble
   (`leaf=2`), from the deciding team's view. Values are averaged over the samples; all candidates of a
   sample share the determinization and the book's replies (common random numbers); the best mean wins and
   the book's answer keeps a tie.
5. `scope`: 1 card plays only (the rest is h10), 2 card plays and the hero's own action choices
   (action, target, space, bullet, token, confirm, number while it acts), 3 every decision with two or more
   options (defenses, upgrades, respawns, enemies' "may" answers to us too; not ACT_FIRST).

Horizons (`hz` for card plays, `hzo` for the rest): 0 the action, 1 the turn (after turn 4 through the end of
the round), 2 two turns, 3 the round (the next round's first card play is the leaf), 4 the round and the next
round's first turn.

**(a) `y10`: the old strength 10 with the minimal change** (`HybridPlayers.searchWithBook`): the same
`Searcher`, profile, samples (16/6) and learned leaf; three nullable hooks in `LevelProfile`
(`playoutPolicy`, `playModelOn`, `leafOn`) let the book answer inside its playouts (`pol` 0/1; −1 keeps the
old greedy-with-net playouts), deal the enemy's unrevealed card (`model`), and score the leaf (`leaf`).

Non-negotiables: the search reads the real session only through `determinizeWith` (and the public log for
card memory); `NoPeekingTest` now runs both prototypes (facedown card, facedown tokens: same answer);
`HybridSearchTest` checks that a 3-thread `SampleRunner` gives the same answers as the sequential one and that
every answer is a real option. Pure common Kotlin (no `java.*`, threads only through `SampleRunner`).
Additive changes outside `ai/hybrid`: `HeuristicAi.answerOn` (the book's answer on a copy already
determinized; `answer` now calls it, same answers) and `profileEdit`; the three hooks in `Search.kt`.

## Measurements

Mirrored 2v2 on development line-ups 1000–1031 (64 games a row), `AI_THREADS=3`, oldboi shared with other
agents' builds. Wilson 95% intervals; "pairs" is the pentanomial count (a lost both / – / split / – / won both).
Answer times are per question with two or more options, single-threaded per answer (mean / p95 / max, ms),
three games at once.

### Against h10 (the book alone): what each knob does, cheap settings

Base `x10` = K 8 card-play samples, Ko 4 for other decisions, top 4, turn horizon, HEval leaf, `pol=0`
(static book playouts), scope 1 (card plays only), `model=1`.

| Variant | Spec | Win vs h10 | Wilson | Pairs | x10 answer ms | x10 card play ms |
|---------|------|-----------:|--------|-------|---------------|------------------|
| base | `x10` | 64.1% | 51.8–74.7 | 0/0/23/0/9 | 35 / 193 / 1351 | 122 / 328 / 1351 |
| learned leaf | `x10+leaf=2` | 65.6% | 53.4–76.1 | 3/0/16/0/13 | — | 108 / 275 / 719 |
| old hand leaf | `x10+leaf=1` | 51.6% | 39.6–63.4 | 5/0/21/0/6 | — | 101 / 258 / 856 |
| uniform guess of the facedown card | `x10+model=0` | 64.1% | 51.8–74.7 | 5/0/13/0/14 | — | 104 / 269 / 808 |
| 16 samples | `x10+K=16` | 60.9% | 48.7–71.9 | 4/0/17/0/11 | — | 198 / 515 / 1652 |
| round horizon | `x10+hz=3` | 60.9% | 48.7–71.9 | 5/0/15/0/12 | 77 / 465 / 2158 | 268 / 735 / 2158 |
| own action choices searched too | `x10+scope=2` | 68.8% | 56.6–78.8 | 1/0/18/0/13 | 46 / 169 / 879 | 102 / 269 / 879 |
| **the full book in the playouts** | `x10+pol=1` | **78.1%** | 66.6–86.5 | 0/0/14/0/18 | 219 / 1273 / 4943 | 793 / 2336 / 4943 |

h10 answered in 3–7 ms mean in every row. With the full book in the playouts x10 lost 0.33 heroes a game
against h10's 1.63 (empty-handed 12 against 41).

### Against s10 (the old search, ~84% over h10): what closes the gap

| Variant | Spec | Win vs s10 | Wilson | Pairs | Answer ms (mean / p95 / max) | Card play ms | How s10 won (push / Life / Throne) |
|---------|------|-----------:|--------|-------|------------------------------|--------------|------|
| h10 (baseline) | `h10` | 15.6% | 8.7–26.4 | 23/0/8/0/1 | 3.2 / 10.9 / 59 | 7 / 24 / 59 | 34 / 9 / 11 |
| base | `x10` | 23.4% | 14.7–35.1 | 19/0/11/0/2 | 33 / 175 / 1034 | 108 / 288 / 1034 | 32 / 5 / 12 |
| full book | `x10+pol=1` | 25.0% | 16.0–36.8 | 16/0/16/0/0 | 231 / 1290 / 8523 | 801 / 2391 / 8523 | 31 / 7 / 10 |
| (a) s10 with the static book in its playouts | `y10` | 32.8% | 22.6–45.0 | 13/0/17/0/2 | 100 / 381 / 1544 | 199 / 499 / 1544 | 22 / 13 / 8 |
| full book, own actions searched | `x10+pol=1+scope=2` | 35.9% | 25.3–48.2 | 13/0/15/0/4 | 311 / 1319 / 5371 | 788 / 2363 / 5371 | 30 / 8 / 3 |
| full book, every decision searched | `x10+pol=1+scope=3` | 43.8% | 32.3–55.9 | 10/0/16/0/6 | 337 / 1375 / 8331 | 829 / 2512 / 8331 | 24 / 3 / 9 |
| full book, own actions, learned leaf | `x10+pol=1+scope=2+leaf=2` | 48.4% | 36.6–60.4 | 8/0/17/0/7 | 316 / 1296 / 5089 | 789 / 2363 / 5089 | 25 / 5 / 3 |
| **full book, every decision, learned leaf** | `x10+pol=1+scope=3+leaf=2` | **65.6%** | 53.4–76.1 | 5/0/12/0/15 | 344 / 1371 / 8495 | 843 / 2502 / 8495 | 13 / 5 / 4 |
| the same, 16 / 8 samples, top 6 | `…+K=16+Ko=8+top=6` | 56.3% | 44.1–67.7 | 6/0/16/0/10 | 704 / 2759 / 12428 | 1628 / 4819 / 12428 | 16 / 7 / 5 |

s10 answered in 280–310 ms mean, 860–940 ms p95 in every row (its card plays 550–600 / 1100–1200 ms): the best
x10 spends about s10's time per answer (+11% mean; its p95 is higher because a card play tries every card on
every sample). Stopped early (64 games, clearly behind or not informative enough to pay for): none of the
variants above was stopped; `y10+pol=-1` (s10 with only the book's opponent model) was started twice and
yielded to the held-out runs, unmeasured.

### Held out: the best variant against s10

`x10+pol=1+scope=3+leaf=2` (K 8, Ko 4, top 4, turn horizon, the full book in the playouts, every decision
searched, the old learned ensemble at the leaves), mirrored, `AI_THREADS=3`.

| Pair | Line-ups | Games | Win | Wilson | Pairs | Defeats/game (empty-handed) | How games ended (push / Life / Throne); x10 won by | x10 answer ms | x10 card play ms | s10 answer ms |
|------|----------|------:|----:|--------|-------|------|------|------|------|------|
| 2v2 x10 : s10 | 300–395 | 192 | **52.1%** | 45.0–59.0 | 21/0/50/0/25 | 1.01 (72) / 1.10 (63) | 107 / 57 / 28; 58 / 29 / 13 | 341 / 1355 / 7699 | 826 / 2404 / 7699 | 284 / 892 / 5524 |
| 3v3 x10 : s10 | 300–347 | 96 | (running when the agent's session ended; to be filled in) | | | | | | | |

For comparison on the same 2v2 line-ups (RESULTS.md §5): h10 : s10 15.6% (11.2–21.4). The development 65.6%
was 64 games; the held-out 192 put the true value near even with s10 (the two intervals overlap at 53–59%).




## Reading

1. **The book is a good playout policy only in its full form.** Against h10, the same search with the book's
   static scores in the playouts made 64.1%; with the book exactly as h10 plays it (CardSim at every card
   play in the playout, one-ply looks) 78.1%, and it lost a fifth of the heroes. The static book is a worse
   policy than the old search's own playouts: swapping it into s10 (`y10`) made s10 lose to itself 32.8%.
2. **Search breadth is the biggest lever against s10.** With the full book and HEval: card plays only 25.0%,
   plus the hero's own action choices 35.9%, every decision 43.8%. s10's edge over h10 was never its card
   play: it was every target, space, defense and upgrade being played out. h10's one-ply answers there are
   where it loses the lane (s10 won 34 of its 54 games by the last push against h10; 13 of 22 against x10's
   best on the development set).
3. **The leaf is the second lever, and the learned net beats HEval as a leaf.** At the same search, the net
   leaf added 12 points at scope 2 (35.9 → 48.4%) and 22 at scope 3 (43.8 → 65.6% on development, 52.1% held
   out). Against h10 at scope 1 it changed nothing (64.1 / 65.6%): the net's advantage is in how it values the
   lane race, which decides games against s10 and rarely against h10. The old hand evaluation was worse than
   both (51.6% against h10). HEval is a good *policy* evaluation (it ranks the options of one decision) and a
   weaker *position* evaluation across playouts that differ by a whole turn.
4. **More samples, longer horizons and the opponent model bought nothing measurable**, as AI.md "Depth and
   speed" found for the old search: 16 samples 60.9% against 8's 64.1% (h10), and 56.3% against 65.6% (s10) at
   twice the cost; the round horizon 60.9% (h10) at twice the cost; a uniform guess of the facedown card
   64.1%, the same as the book's model. With common random numbers and a deterministic policy, 4–8 samples
   already separate the options; what separates them wrongly is the leaf.
5. **Cost.** The best variant spends about s10's time per answer (mean 341 against 284 ms on loaded oldboi,
   one thread per answer); a card play is ~0.8 s mean and 2.4 s p95 because it plays every card on every
   sample with CardSim inside the playouts. That is 100–300× h10.


## Recommendation

(Written by the orchestrator after the agent's session ended on a network error.) The strongest player found is
search over the heuristics: the full book as the playout policy and opponent model, every decision with two or
more options searched (scope 3), a turn horizon, 8 / 4 samples, and the learned ensemble at the leaves. It is
level with the old s10 on held-out line-ups (52.1%, 45.0–59.0) at about s10's cost, where the book alone makes
15.6%. The next gains are a better leaf (HEval as a position evaluation lags the net by 12–22 points at the
same search) and a faster card play (every card on every sample with CardSim inside the playouts).
