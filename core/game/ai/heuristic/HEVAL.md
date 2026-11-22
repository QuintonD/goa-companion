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
2. **Fit** (`tools/ai/heval_fit.py`, or the Kotlin `fit the terms to outcomes`). P(win) = σ(Σ vᵢ fᵢ),
   logistic, no intercept (zero-sum).
   - First the slope k of the current evaluation alone; the shipped weights are vᵢ / k, which keeps the
     book's coin scale.
   - Ridge on the standardised coefficients toward k × the hand weights.
   - Games, not rows, are split into four folds (crc32 of the game id). The loss quoted is 4-fold
     cross-validated, with weights from all games at the best ridge.
3. **Leaf ranking** (`record leaves`, opt-in `AI_HEVAL_LEAVES`; `tools/ai/heval_rank.py`). Every leaf x10
   scores is recorded with its decision, sample and candidate, the net's logit and the terms (hook
   `HybridAi.leafObserver`, null in play). The fit is on differences within a sample, because a leaf only
   has to order the candidates of one sample.

## Results

(See below; filled in as measured.)
