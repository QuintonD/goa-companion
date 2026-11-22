# HEval: the terms, the fits and the weights (wave 3, Track B)

`HEval` (`core/game/src/main/kotlin/dev/quinton/goa/core/game/ai/heuristic/HEval.kt`) is the book's
hand-written position value in coin-equivalents. The book uses it to rank the options of one decision
(CardSim, Lookahead). The hybrid search `x10` uses it at its leaves. Since wave 3 it is a named
feature vector times a weight vector, `Σ wᵢ · fᵢ` (`HTerm`, `HEvalWeights`). The non-linear parts stay
inside the features: the lane's win probability, the Life ladder and the threat map.

## Terms

Each term is Red minus Blue, in its own unit. `hand` is the weight the book was built and measured with,
so the hand set reproduces the old evaluation exactly (`GameParityTest` unchanged).

| Term | Feature | Hand weight |
|------|---------|------------:|
| `lane` | `60 × (2W − 1)` per lane. W is P(Red wins the lane) from the push recursion, with each side's push potential (0.4 of the designer's share) taken off the other side's minions | 1.4 |
| `laneMatch` | the extra `40 × (2W − 1)` of a lane at match point | 1.4 |
| `margin` | minions on the lane, 1.5 each (3 at match point) | 1 |
| `life` | the Life ladder (60 for the last counter, then 22, 14, 9, 6, 4) | 1 |
| `lifeDanger` | 1 for a team one defeat from losing on Life *(new)* | 0 |
| `pieces` | set-up pieces, the best four per owner | 1 |
| `coins` | coins (×1.3 in rounds 1–2 below level 4) | 1 |
| `levels` | cumulative level cost + 3 a level, +1.5 for a level due, +8 for the Ultimate | 1 |
| `counters` | hero counters (spellbook, wishes) | 1 |
| `hand` | 2.5 per needed card, the spare by turn (3/2/1.5/0.8), −3 per missing card | 1 |
| `handRating` | 0.12 × the ratings of a hand this team may read | 1 |
| `offBoard` | heroes off the board | −8 |
| `presence` | +1 in the Battle Zone, −0.55 a space outside | 1 |
| `opportunity` | attack cards in hand that reach an enemy minion, 1.6 each, at most 4.8 | 2 |
| `laneThreat` | a minion in reach of a card still to play, priced by what its kill does to the lane | 0.3 |
| `zonePull` | +2.5 in the zone the hero should stand in, −0.7 a space | 1 |
| `exposure` | expected loss from the threat map (positive) | −1 |
| `playbook` | the playbooks' `evalTerms` | 1 |
| `oppPool` | attack plays still to come that reach an enemy minion. A hidden hand counts with the pool's weights, and the count stops at the plays left this round (fixes REVIEW-2-design §2.2.4) *(new)* | 0 |
| `access` | minus the actions a hero needs to stand in its zone: walking 3 spaces an action, or Fast Travel (p.11) then walking. An off-board hero counts from its Throne respawn (p.7). Blocking an enemy's Fast Travel now shows here (#13) *(new)* | 0 |
| `laneProj` | the lane from one projected minion battle (#9, #14). Both sides' expected minion kills this round come from the reachable attack plays (half of each). At the round's end the exact a ≥ 2b push and the 2b − a carry-over apply (engine `minionBattle`). The next push's chance comes from the new zone's spawns, not 0.5 *(new)* | 0 |

There is also a scale on the book's minion price, `laneKillDelta` (HFacts, additive hook `laneDelta`, default
1): REVIEW-2-design §2.2.1 notes that the lane's ×1.4 never reached it.

### Knobs

- `hev=N` picks the weight set for the book's own evaluations.
- `hevLeaf=N` picks the set for a search leaf. It defaults to `hev`; the leaf of `HybridAi` calls
  `HEval.evaluateLeaf`.
- `hw.<term>=x` overrides one weight.
- `laneDelta=x` sets the minion-price scale.
- The old knobs keep their meaning: `lane` sets the lane's scale, `opp` multiplies opportunity's 2.0, and
  `oppLane` is the lane-threat share.

Sets: 0 `hand`, 1 `fit`, 2 `distil`, 3 `fitAll` (below).

## Calibration pipeline

1. **Record** (`HEvalCalibrationTest` `record positions`, opt-in `AI_HEVAL_DATA`). Mirrored games on
   development line-ups 2000+. At the first decision of every turn (where a turn-horizon search scores its
   leaves), one row per team:
   - the terms from that team's side, computed on a copy determinized from that team's view exactly as
     x10's leaf is (strength 10's view, ADR-062, `NoPeekingTest` rules);
   - the old net's logit on the same copy;
   - who won.
2. **Fit** (`tools/ai/heval_fit.py`, pure Python). P(win) = σ(Σ vᵢ fᵢ),
   logistic, no intercept (zero-sum).
   - First the slope k of the current evaluation alone; the shipped weights are vᵢ / k, which keeps the
     book's coin scale.
   - Ridge on the standardised coefficients toward k × the hand weights.
   - Games, not rows, are split into four folds. The loss quoted is 4-fold cross-validated, with weights
     from all games at the best ridge. `--distil` fits the terms by least squares to the net's logit.
   - The first folds used crc32 of the game id. For the leaf files, whose game ids differ in one
     character, that put every game in folds 1 and 3. Folds are now the line-up index mod 4, so the two
     mirrored games of a line-up stay together.
3. **Leaf ranking** (`record leaves`, opt-in `AI_HEVAL_LEAVES`; `tools/ai/heval_rank.py`). Every leaf x10
   scores is recorded with its decision, sample and candidate, the net's logit and the terms (hook
   `HybridAi.leafObserver`, null in play). The fit is on differences within a sample, because a leaf only
   has to order the candidates of one sample. It reports, on held-out games, how often the weights pick
   the teacher's best candidate and the teacher's regret.
4. **Residuals** (`AI_HEVAL_ROLL=1` adds context columns and two search-backed teachers;
   `tools/ai/heval_resid.py`). Each leaf gets context columns: hand sizes, minions, waves, zones to win or
   lose, Life, levels, heroes off the board, step and heroes. Each leaf also gets a static-book playout to
   the next round's first card play, scored by hand HEval (`rollHeval`) and by the net (`rollNet`). The
   script fits the terms within samples and breaks the residual down by context.

## Results (development line-ups; RESULTS.md "Wave 3 — Track B" has the tables)

### Fit 1: outcomes

The data are 62,782 positions (both views of the first decision of every turn) from 600 h10 self-play games
and 600 h10-vs-s8 games, line-ups 2000–2299. 4-fold CV log-loss: hand weights 0.593, the cleaned fit
(set 1) 0.587, every term fitted 0.586, the net 0.577.

What the fit says, stable across folds:
- **Lane:** ×1.4 more. The match-point extra falls to ×0.7. Margin turns negative (collinear with the
  lane).
- **Life, coins and hand:** life ×1.2, coins ×1.7, hand ×2, hand rating ×2.
- **Exposure:** ×0.44.
- **Minion opportunity:** the pool-weighted `oppPool` (5 a reaching play) replaces `opportunity`.
- **Off the board:** the flat −8 goes to about 0.
- **Unstable:** `presence`, `zonePull` and `access` swap sign between fits (they are collinear), and so do
  `lifeDanger`, `counters` and `pieces` (hero confounds). Set 1 keeps those at the hand weights.

On 64 x10-vs-s10 games, which the fit never saw, sets 1 and 3 predict outcomes better than the net:
log-loss 0.624 and 0.620, the net 0.627, the hand weights 0.637.

As h10's own evaluation set 1 measured even (49.2% vs h10, 51.6% vs s8 against 55.5%). As x10's leaf it
measured 43.8% vs s10, the same as the hand leaf. Set 3 made 35.9%.

### Distilled: the net's logit

On the same positions, least squares onto the net's logit explains 93% of its variance across positions.
Yet its log-loss is worse than the hand weights' (0.601), and as a leaf it made 40.6% vs s10. The 7% it
misses is where candidates differ.

### Leaf ranking

The data are 87,568 leaves of x10 with the net leaf against s10: 32 games, line-ups 2500–2515, 5,163
searched decisions. The hand weights pick the net's best candidate in 73–75% of held-out decisions, with
regret 1.3–1.4 coins. Fitted within samples: 75–76%, regret about 1.07. By leaf turn, the refit explains 91%
of the net's within-sample variance at round-start leaves (after the minion battle) but only 67% at
mid-round leaves.

### Residuals

The data are 22,725 leaves with context, 8 games, line-ups 2516–2519; the teacher is the net.
- **What the terms explain.** The hand terms explain 53% of the within-sample variance, the refit 74%.
  The wave-3 terms (`defenders`, `minionMod`, `formation`, hand shape) add about 3 points of that.
- **Exposure.** HEval's own within-sample variation is driven by `exposure` (correlation −0.59 to −0.65).
  The net's barely is (−0.09 to −0.23). On positions, exposure ×0.6 moves agreement with the net from 72.6%
  to 73.5%.
- **The last wave.** Lanes on their last wave hold 22% of the leaves and 55% of the residual. There the
  net prices one more own minion in the lane at about 52 coins, HEval at 21. A last-wave margin term adds
  only 0.6 points of R².
- **Life and deaths.** Life and deaths count more for the net. The slope on our hero off the board is −53
  coins for the net and −39 for HEval; the Life correlation is 0.53 against 0.37.
- **Search-backed teachers.** One static-book playout to the next round's start is too noisy within a
  sample: the terms explain 8–15% of `rollNet` and `rollHeval`. A usable search-backed teacher needs many
  playouts per leaf, or the full book.

### Kept

The book keeps the hand weights (`DEFAULT_SET = 0`, `DEFAULT_LEAF_SET = 0`); no fitted set measured even
for h10 and better as a leaf. Sets 1–3 and the new terms stay as knobs for measurement. `laneDelta` stays at
1 (1.4 measured 51.0% vs h10 over 512 games and 42.2% vs s8).

### What would move the leaf next

- **A teacher better than the net.** Several full-book playouts per leaf to the end of the round, or to
  the end of the game, from fixed positions.
- **Structural terms from the residuals:**
  - a team-level exposure, since an enemy plays one card a turn, so the threat should not be charged to
    every hero (REVIEW-2-design §2.2.6);
  - a last-push race at the last wave: who empties the zone first, from the reachable attack plays and
    the a ≥ 2b battle.
- **Read on the position bench first** (Track A), then x10 leaf=0 against leaf=2 by SPRT.
