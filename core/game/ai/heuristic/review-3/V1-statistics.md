# V1 — Re-verification of the measurements and their statistics

> Part of the wave-3 audit (2026-11-24); the synthesis is `../REVIEW-3-audit.md`. Paths under `/tmp/claude-1000/` were the
> agents' scratch folders at the time and may be gone.

Auditor V1, 2026-11-24 (01:00–01:20 CET). Read-only. Every number below was recomputed by me from raw logs
(summary lines and per-game `GAME` lines) or read from code at the commit named. Scripts and intermediate files:
`/tmp/claude-1000/-home-quinton-goa-companion/bb386302-b635-4d0f-906f-1e109ea75466/scratchpad/audit/v1/`
(`collect.py` → `rows.json` = all 1,058 tournament summary lines found; `headline.py`, `analysis.py`, `mc.py`,
`hero.py`, `games.py`, `stats.py`, `times.py`). Scratch folders are abbreviated by worktree id
(`t3-0453f4ee` = C2, `t3-09da4030` = C resume, `t3-89cc025f` = C first, `t3-887b9db7` / `t3-b10d277a` = A,
`t3-8fa0fe83` = B2, `t3-f53f8cfc` = C2 resume; full paths `/tmp/claude-1000/-home-quinton--t3-worktrees-goa-companion-<id>/<session>/scratchpad/`).
Track E logs: `~/.goa-qa/trackE/logs/run*.log`, per-game records `~/.goa-qa/trackE/data/x-*.jsonl`.

Notation: W-L = side a's wins-losses; pairs LL/S/WW = mirrored pairs side a lost both / split / won both (no draws
occurred: all 1,058 summary lines have `draws 0` and zero half-point pair buckets, so the pentanomial is a trinomial
here). "pair 95%" = normal interval of the pair mean; LLR = pentanomial GSPRT log-likelihood ratio at p0 0.50,
p1 0.55 (bounds ±2.944).

---

## 1. Headline numbers recomputed from raw data

All headline numbers trace to a raw summary line; every W-L is consistent with its pair counts
(W = 2·WW + S, L = 2·LL + S, games = 2·pairs). Recomputed Wilson and pair intervals match the logs to 0.1.

| Row | W-L | games | rate | Wilson 95% | pairs LL/S/WW | pair 95% | LLR(0.55) | claimed | match | raw source |
|---|---|---|---|---|---|---|---|---|---|---|
| h10 vs s8 dev 1064-1159 (F1) | 135-57 | 192 | 70.3% | 63.5-76.3 | 4/49/43 | 64.6-76.0 | 10.54 | 70.3 | yes | t3-b10d277a tasks/b5d05hnf3.output |
| h10 vs s8 300-395 checkpoint | 135-57 | 192 | 70.3% | 63.5-76.3 | 7/43/46 | 64.1-76.5 | 8.84 | 70.3 | yes | t3-887b9db7 tasks/b6yd3tjp1.output |
| h10 vs s8 600-695 checkpoint | 116-76 | 192 | 60.4% | 53.4-67.1 | 9/58/29 | 54.5-66.4 | 4.31 | 60.4 | yes | t3-887b9db7 runs/final600.txt |
| h10 vs s10 300-395 | 46-146 | 192 | 24.0% | 18.5-30.5 | 55/36/5 | 18.0-29.9 | -15.49 | 24.0 | yes | t3-887b9db7 runs/finals10.txt |
| W2 vs s10 300-347 | 52-44 | 96 | 54.2% | 44.2-63.8 | 8/28/12 | 45.1-63.2 | 0.39 | 54.2 | yes | t3-89cc025f tasks/bjr5qyecp.output |
| W2 vs s10 348-395 | 52-44 | 96 | 54.2% | 44.2-63.8 | 11/22/15 | 43.8-64.5 | 0.30 | 54.2 | yes | t3-89cc025f tasks/b16y7pmwz.output |
| W2 vs s10 300-395 pooled | 104-88 | 192 | 54.2% | 47.1-61.1 | 19/50/27 | 47.3-61.0 | 0.68 | 54.2 | yes | sum |
| W2T vs W2 1100-1227 | 145-111 | 256 | 56.6% | 50.5-62.6 | 20/71/37 | 51.0-62.3 | 2.48 | 56.6 | yes | t3-09da4030 runs/t1.log |
| W2T(leaf0) vs W2(leaf0) 1500-1627 | 143-113 | 256 | 55.9% | 49.7-61.8 | 20/73/35 | 50.3-61.4 | 2.07 | 55.9 | yes | t3-09da4030 runs/tC.log |
| W2T vs s10 dev 1600-1631 | 46-18 | 64 | 71.9% | 59.9-81.4 | 3/12/17 | 60.5-83.3 | 2.86 | 71.9 | yes | t3-09da4030 runs/tS.log (SPRT 0.6, stopped H1) |
| W2T vs s10 600-647 | 57-39 | 96 | 59.4% | 49.4-68.7 | 8/23/17 | 49.5-69.2 | 1.36 | 59.4 | yes | t3-09da4030 runs/f600.log |
| W2T vs s10 648-695 | 52-44 | 96 | 54.2% | 44.2-63.8 | 10/24/14 | 44.2-64.1 | 0.32 | 54.2 | yes | t3-09da4030 runs/f648.log |
| W2T vs s10 600-695 pooled | 109-83 | 192 | 56.8% | 49.7-63.6 | 18/47/31 | 49.8-63.8 | 1.67 | 56.8 | yes | sum |
| W2T vs s10 3v3 300-347 | 59-37 | 96 | 61.5% | 51.5-70.6 | 5/27/16 | 52.7-70.2 | 2.23 | 61.5 | yes | t3-09da4030 runs/f3v3.log |
| W1 vs s10 dev 1000-1063 | 76-52 | 128 | 59.4% | 50.7-67.5 | 8/36/20 | 51.6-67.1 | 2.19 | 59.4 | yes | t3-89cc025f runs/run3.log |
| NET6 vs leaf2 (in W2) 1400-1527 | 142-114 | 256 | 55.5% | 49.3-61.4 | 29/56/43 | 49.0-61.9 | 1.38 | 55.5 | yes | trackE logs/run6.log |
| NET6 vs leaf2 (in W2) 1600-1727 | 145-111 | 256 | 56.6% | 50.5-62.6 | 23/65/40 | 50.7-62.6 | 2.23 | 56.6 | yes | trackE logs/run7.log |
| NET6 vs leaf2 pooled | 287-225 | 512 | 56.1% | 51.7-60.3 | 52/121/83 | 51.7-60.4 | 3.55 | 56.1 | yes | sum |
| NET6 vs leaf2 3v3 1000-1047 | 57-39 | 96 | 59.4% | 49.4-68.7 | 7/25/16 | 49.9-68.8 | 1.49 | 59.4 | yes | trackE logs/run8.log ("so far", stopped by hand) |
| W2+NET6 vs s10 300-363 | 71-57 | 128 | 55.5% | 46.8-63.8 | 8/41/15 | 48.2-62.7 | 1.09 | 55.5 | yes | trackE logs/run9.log |
| W3 vs W0 2000-2031 | 41-23 | 64 | 64.1% | 51.8-74.7 | 3/17/12 | 53.2-74.9 | 1.90 | 64.1 | yes | t3-0453f4ee runs/f1a-2000.txt |
| W3 vs W0 2032-2079 | 54-42 | 96 | 56.2% | 46.3-65.7 | 4/34/10 | 48.8-63.7 | 1.30 | 56.3 | yes | t3-0453f4ee runs/f1a-2032.txt |
| W3 vs W0 pooled | 95-65 | 160 | 59.4% | 51.6-66.7 | 7/51/22 | 53.1-65.6 | 3.36 | 59.4 | yes | sum (notes.md) |
| W3 vs s10 dev 2200-2247 | 69-27 | 96 | 71.9% | 62.2-79.9 | 1/25/22 | 64.3-79.5 | 6.46 | 71.9 | yes | t3-0453f4ee runs/s10-2200.txt |
| W3+NET6 vs W3 2300-2347 | 51-45 | 96 | 53.1% | 43.2-62.8 | 8/29/11 | 44.3-62.0 | 0.15 | 53.1 | yes | t3-0453f4ee runs/net6-2300.txt |
| W3+NET6 vs W3 2348-2395 | 53-43 | 96 | 55.2% | 45.3-64.8 | 7/29/12 | 46.4-64.0 | 0.68 | 55.2 | yes | t3-0453f4ee runs/net6-2348.txt |
| W3+NET6 vs W3 2396-2459 | 78-50 | 128 | 60.9% | 52.3-69.0 | 7/36/21 | 53.3-68.6 | 2.77 | 60.9 | yes | t3-0453f4ee runs/net6-2396.txt |
| W3+NET6 vs W3 pooled | 182-138 | 320 | 56.9% | 51.4-62.2 | 22/94/44 | 52.0-61.7 | 3.56 | 56.9 | yes | sum (notes.md) |
| B2 lastRace, x10 leaf0 3200-3319 | 138-102 | 240 | 57.5% | 51.2-63.6 | 12/78/30 | 52.4-62.6 | 3.66 | 57.5 | yes | t3-8fa0fe83 b2/xrace.0-4.log, xrace.out |
| B2 leaf0+lastRace vs leaf2 3400-3471 | 56-88 | 144 | 38.9% | 31.3-47.0 | 25/38/9 | 31.4-46.4 | -4.64 | 38.9 | yes | t3-8fa0fe83 b2/xleaf2.0-2.log |
| **W3+NET6 vs s10 600-647, running** | 33-19 | 52 | 63.5% | 49.9-75.2 | 3/13/10 | 50.9-76.0 | — | — | — | t3-f53f8cfc runs/s10-600.txt, snapshot 01:16 (26 complete pairs of 48) |

Additional checks on the raw data:
- The C2 per-game `GAME` lines reproduce the summary pair counts exactly for all seven C2 runs (`games.py`).
- Determinism: an aborted first attempt at 2032 (`runs/partial-f1a-2032.txt`, 20 games) and the full rerun agree on
  all 20 outcomes (same line-up, same side) — the measurement is reproducible.
- **Traceability gaps.** (a) The W3 rows (59.4% / 71.9% / 56.9%) appear in no committed RESULTS.md: they exist only in
  C2's scratch `notes.md` and logs. `git grep` on t3/strength-10-search finds no "Track C2" section, yet
  `AiPlayers.SEARCH_10`'s KDoc there cites `RESULTS.md "Wave 3 — Track C2"`. (b) Most wave-1/2 rows survive only as
  summary lines (no per-game data); I could not re-derive them per game, only check internal consistency. (c) The
  W3 "SPRT H1" verdicts were computed after the fact by `combine.py` over separate runs (no `SPRT so far` line in
  any C2 log); the LLR values (3.36, 3.56) recompute correctly.

Verdict on item 1: **no number is mis-transcribed.** The issues are interpretive (sections 2–5), not arithmetic.

---

## 2. The statistics code

Code read on t3/strength-10-search (identical Sprt/Wilson to base 64955c18 except the batching added in wave 3):
`core/game/src/test/kotlin/dev/quinton/goa/core/game/ai/Tournament.kt`.

**LLR formula — correct.** `Sprt.verdict(score)` (Tournament.kt:244–251 at 64955c18) computes
`llr = N·(p1−p0)·(2·mean − p0 − p1) / (2·variance)` with `mean`, `variance` the empirical mean and (biased, /N)
variance of the per-pair score k/4, k ∈ {0..4} (`pairedMeanAndError`, :85–91). Primary source: Michel Van den Bergh,
"A practical introduction to the GSPRT" (https://www.cantate.be/Fishtest/GSPRT_approximation.pdf, opened and read):
eq. (2.1) LLR ≅ ½ (φ1−φ0)(2φ̂−φ0−φ1)/V(φ̂), with V(φ̂) = (Σ a_i² p̂_i − φ̂²)/N and, for paired games, the 5-nomial
a = (1, ¾, ½, ¼, 0), "N is the number of games divided by two". Substituting V gives exactly the code's expression.
Fishtest implements the same as `LLR_alt2` in `server/fishtest/stats/LLRcalc.py` (official-stockfish/fishtest,
master, read via fetch). Stopping bounds `ln((1−β)/α)` / `ln(β/(1−α))` (:253–258) are Wald's.

**Scale — scores, consistent.** p0/p1 are expected scores, not Elo. 0.55 = +34.9 logistic Elo. Fishtest's
production tests are stated in (normalized) Elo with much narrower bounds (`LLR_normalized`), but a score-scale
GSPRT is the same test family (`LLR_logistic` converts Elo to scores first). No scale error.

**Weak spots (all verified in code or logs):**
1. *p1 is chosen per run.* W2T vs s10 dev used `AI_SPRT=0.6` (`t3-09da4030 runS.sh`: `AI_H_FIRST=1600 AI_H_GAMES=64 AI_SPRT_BATCH=16 AI_SPRT=0.6`);
   at the project's standard 0.55 its LLR is 2.86 — not H1. Accepting H1 at p1 = 0.6 says "closer to 60% than to
   50%", and with 2 looks of 16 pairs it is strongly stop-biased (section 5).
2. *Stopping bias is never corrected.* Every reported rate is the naive mean at the stopping time. Monte Carlo
   (`mc.py`, 50–200k replays, batches as used): with SPRT(0.5, 0.55), looks every 24 pairs, true score 0.53 gives
   P(H1) 0.25 and E[estimate | H1] = 0.576; true 0.55 gives P(H1) 0.61 and E[estimate | H1] = 0.580. So an H1 keep
   reported at 56–59% is consistent with a true gain of 0.52–0.55.
3. *Look schedule.* In-code: a look after every 16 line-ups (32 games), stop on the first crossing, cap at
   `AI_H_GAMES` (`Tournament.runMixed`, wave-3 diff; `Teams.mirrored` :120–136). Looking less often than every pair
   is conservative (overshoot), not a validity problem. Pooling separately run chunks (C2 `combine.py`, B2
   `sprt-run.sh`, Track E runs 6+7) is equivalent to extra looks; B2 and E stopped at a crossing; C2's two keeps were
   looked at after each chunk (W3: LLR 1.90 at 32 pairs → 3.36 at 80; NET6 on W3: 0.15 → 0.82 → 3.56). Valid, but
   each "H1" is reported at the first crossing, the most biased point.
4. *Fallback below 8 pairs* to a binomial LLR on games (:245) cannot reach a bound before 8 pairs at p1 0.55, so it
   is harmless. Zero variance (all splits) also falls back; fishtest regularises zero cells (1e-3) instead. Minor.
5. *Wilson on games* (:270–276) treats the two games of a pair as independent. Measured pair variance relative to
   independence (var / (m(1−m)/2)) is 0.67–1.04 across comparisons (`analysis.py` §2), so Wilson is conservative or
   exact here; the printed pair interval is the better one. The pair interval is a plain normal interval: at small
   n it leaves [0,1] (logged "pairs 56.7-110.0%" for a 6-game 3v3 run).

**Mirrored pairs — correct.** `Tournament.play` (:324–356) queues, per spec, two jobs with the same line-up, coin and
seed: a plays Red (the line-up's first team) then Blue (the second); players are seeded by colour
(`spec.seed*2` Red, `*2+1` Blue), so the environment and seeds are identical and the sides are swapped.
`invokeAll` keeps order and `record` closes a pair on every second game (:132–138); failed pairs are dropped as
pairs (:363–380). `Teams.playBatch` does the same (`Teams.kt` :156–185). `runMixed` uses seed 50,000+i and the coin
alternating by i (:306–309); `TeamTournamentTest AI_FAMILY=mixed` reproduces exactly that, so Track E's and Track
C's line-ups are the same games for the same index.

**Missing guard.** Only `TeamTournamentTest` (mixed) and `AiPositionBenchTest` refuse the sealed 5000–5095 range;
`HeuristicTournamentTest` (the harness used for almost every row) has no guard.

---

## 3. Line-up range hygiene

Ranges from RESULTS.md (all branches), run scripts, the logs' `== pair lineups first+n` headers and every
`AI_H_FIRST` / `AI_FIRST` / `run.sh`/`bg.sh` argument in the agents' transcripts (`~/.claude/projects/*goa-companion*`).

| Range | Used by (decision) |
|---|---|
| 0–95 | wave 1: book tuning |
| 300–395 | wave 1: **flaw a–c decisions** (RESULTS §2 rows "h10 : s8 300–395" 46.4% → 49.5%) *and* the wave-1 "held-out" final table; wave 2: x10/y10 held-out (52.1%); wave 3 A checkpoints (h10 vs s8/s10/h9, h9 vs h8, h8 vs h5); C W2 vs s10 checkpoint; C 3v3 300–347, h10 300–347 / 348–353 timing; E W2+NET6 vs s10 300–363 |
| 600–695 | wave 1 final table; wave 3 A checkpoint; C W2T vs s10 checkpoint (56.8%); **C2 resume reruns 600–647 now** |
| 1000–1383 | wave 1 A/B; wave 2 regret probe; A's bench probe file (games 1000–1011); C W1/W2/kill/hz3/wider (1000–1063), hz=3 leaf0 (1000–1031); B HEval-as-leaf (1000–1031); E avg(w) vs leaf2 (1000–1127) **whose games then trained the g-nets** |
| 1064–1287 | A dev (F1/F2/F3/F4/predict, ladder) |
| 1100–1847 | C tree (1100–1227), leaf0 tree (1500–1627), W2T vs s10 dev (1600–1631), td3 (1700–1763), te0 (1800–1847), tc (1300–1315); E 1200–1295, 1400–1527, 1600–1727; A x10 F1 (1160–1175) |
| 2000–2479 | C2 (fresh consecutive blocks: 2000–2079, 2100–2147, 2200–2247, 2300–2459, 3v3 2000–2002) |
| 2500–2527 | B leaves, E2 dev |
| 3000–3631 | B2 (3000–3127 h10, 3200–3319, 3400–3471, 3v3 3600–3631); C2 resume shadow 3000–3003 |
| 5000–5095 | **never run**: no `AI_H_FIRST`/`AI_FIRST` ≥ 5000 for a mixed-line-up test in any transcript, script, log header or stored game (Track E stored games span 1000–1727; self-play `AI_FIRST=4800/6400` is `Teams.TRAIN`, a different generator, `LearnedSelfPlayTest.kt:43`) |

Findings:
- Overlap inside 1000–4999 is between different questions and does not bias a claim, with one exception that I
  checked: the g-nets were trained on games of line-ups 1000–1127; NET6 was then judged on 1400–1727 and 2300–2459
  (disjoint), so no train/test leak in the NET6 claim.
- **300–395 was a decision range already in wave 1** (RESULTS §2) and was then presented as held out (§5). It has
  since been looked at by ≥ 6 tracks. Its numbers are checkpoints at best.
- **600–647 is being reused** for W3+NET6 after W2T was measured there (59.4%, the better half of 600–695). Reuse
  on the same line-ups and seeds makes the two runs directly comparable (good), but it is the third version
  checkpointed on that range; adaptive reuse makes it less than held out.
- Evidence that ranges differ more than the pair SE says: the same h10 code scored 70.3% on 300–395 and 60.4% on
  600–695 (Cochran Q = 5.09, df 1, p = 0.024).
- Nothing tuned on 5000+; it is clean.

---

## 4. Selection bias and the winner's curse

**Variants vs keeps (search line, wave 3; from RESULTS.md and C2 notes):**

| Track | Game-tested variants | Kept | Kept on an interval that includes 50% / without H1 |
|---|---|---|---|
| C | W1, kill, W2, hz3 (W1), hz3 (W2), wider, hz=3 leaf0, W2T, W2T leaf0, td3, te0, tc (12) | W1, W2, W2T | W1 (47.6–64.5), W2 (46.8–63.8); W2T at 256 games LLR 2.48 (open), Wilson low end 50.5 |
| C2 | W3 (F1 book), lw=3, 6.cap (3) | W3, 6.cap (timing) | W3's second chunk alone 46.3–65.7; pooled H1 |
| E | avg(w), avg(g), NET6, NET6 3v3 (4) | NET6 | 3v3 49.4–68.7 (stopped by hand) |
| B2 | lastRace leaf0, teamExposure ×2, lastRace h10 (4) | lastRace (leaf0 only) | — but leaf0 loses 38.9% to the net leaf, so this keep is off the shipped path |
| A | F1, F1+F3, restBook, F2 ×2, predict, G4 off, knowLook, branch=2, width=8 (+bench-only F6/F7/postlude) | F1, F3, knowLook, branch=2 | F3 (+0.5), knowLook (adds nothing at 256: same 145–111 as branch=2 alone) |

About 23 game-tested variants, 9 keeps; 5 of the keeps rest on intervals including 50% or on no proven gain.

**Elo additivity (`analysis.py` §5; Elo = −400·log10(1/p − 1), SE by the delta method from pair SEs):**

| Link | Elo (95%) |
|---|---|
| W2T vs s10, 600–695 | +47.3 ± 49.7 |
| W3 vs W0 (≈W2T), 2000–2079 | +65.9 ± 45.1 |
| W3+NET6 vs W3, 2300–2459 | +48.1 ± 34.4 |
| **Sum → predicted W3+NET6 vs s10** | **+161 ± 75 Elo → 71.7% [62.1–79.6]** |

But every earlier chain in this project over-predicted the next measurement against s10:

| Chain | predicted | measured | gap |
|---|---|---|---|
| W2 vs s10 (300–395) + W2T vs W2 → W2T vs s10 (600–695) | 60.7% | 56.8% | −28 Elo (z −0.69) |
| W1 vs s10 (dev) + W2 vs W1 → W2 vs s10 (300–395) | 64.5% | 54.2% | −75 Elo (z −1.62) |
| W2 vs s10 + NET6 vs leaf2 → W2+NET6 vs s10 (300–363) | 60.1% | 55.5% | −33 Elo (z −0.85) |

3 of 3 in the same direction (combined z ≈ −1.8). Gains measured against a sibling of the same search transfer only
partly to s10 (intransitivity), and the keeps carry the stop bias of section 2. Applying the historical transfer
(roughly a third of the in-family gain) to W3 + NET6 (+114 Elo in-family) gives about +85 Elo → ~62%.

**The running checkpoint** (W3+NET6 vs s10, 600–647; `t3-f53f8cfc runs/s10-600.txt`, still being written): at
01:03 it had 33 `GAME` lines, 16 complete pairs, 18-14 (56.2%); at 01:16, 53 lines, 26 pairs, **33-19 = 63.5%**
(pairs 3/13/10, pair 95% 50.9–76.0). Against the chain's 71.7% that is z ≈ −1.3; against W2T's 59.4% on the same
line-ups z ≈ +0.7. With 48 pairs at the end its 95% half-width will be about ±9 points: it **cannot** show ≥ 65%
held out unless it lands above ~74%.

---

## 5. Why dev and held-out disagree

**W2T vs s10: 71.9% (64, dev 1600–1631) vs 56.8% (192, 600–695).**
- *Stopping bias (main cause).* The dev run was SPRT(0.6), looks at 16 and 32 pairs, stopped H1 at 32 pairs.
  Replaying that procedure with the held-out pair distribution (18/47/31 per 96) as truth: P(H1 at the 2nd look) =
  0.07, and when it stops there the estimate averages 66.8%; E[estimate | any H1] = 65.5%. Observing ≥ 71.9% in 32
  pairs under that distribution has probability 0.009 (`mc.py`).
- *Variance across ranges.* Cochran Q over the three W2T-vs-s10 sets (71.9 / 59.4 / 54.2): Q = 5.41, df 2,
  p = 0.067 — borderline. Without the dev set: 59.4 vs 54.2, p = 0.47.
- *Line-up heterogeneity.* Per-hero permutation tests find no hero effect: p = 0.08 (NET6 vs leaf2, 256 pairs),
  0.57, 0.78, 0.81 (W3+NET6 vs W3), 0.29 (W3 vs W0), 0.72 (W3 vs s10). Each hero's NET6 effect in run 1400 vs run
  1600 correlates at r = 0.03 over 32 heroes: what looks like "lopsided heroes" is noise at these sizes. Mirroring
  cancels a lopsided line-up (it becomes a split pair); it costs power, not bias. Split shares are 47–65%.
- *Opponent / version drift:* none found. s10 is the same code (section 6). No hybrid or book commit lands between
  the dev run (tS.log, 20:56) and the held-out runs (f600 22:08–23:06, f648 to 00:58) except `8479394d` (lint,
  formatting) at 23:57 (`git log t3/resume-implementation-task`); uncommitted changes cannot be ruled out from git.
- *Overfitting:* W2T's knobs were chosen on 1100–1227, not 1600–1631, so the dev checkpoint was not a tuning set;
  overfitting does not explain this gap.

**W3 vs s10: 71.9% (96, dev 2200–2247)** was a fixed-n run (no SPRT, no stop bias). It is consistent with the chain
for W3 (W2T held-out + W3 gain = +113 Elo → 65.6%) within its interval (62.2–79.9), and it is a single 96-game draw.
Given the chain calibration above, ~60–66% held out is the realistic expectation; 71.9% should not be quoted as
W3's strength.

---

## 6. Baseline drift: is "old s10" the same in every comparison?

Yes for waves 1–3, verified in git:
- s10 in every tournament is `AiPlayers.searchDeterministic(10, seed)` → `SearchAi(AiLevel.of(10), profile(10), seed,
  timeCap = false, …)` (`HeuristicTournamentTest.kt:94`, `AiPlayers.searchPlayer`).
- Since the feature branch left main, the s10 path changed in exactly two commits: `6561fa04` (moves the player
  into `searchPlayer`, no logic change) and `3be12ec7` (three nullable `LevelProfile` hooks, default null; the
  playout uses them only when set). On every wave-3 branch the only further change to `Search.kt` is a typealias and
  a `@Suppress`; `LearnedEval.kt` only adds `ENSEMBLE_6` (ENSEMBLE unchanged); no engine or catalog file changed
  (`git diff --name-only 64955c18 t3/strength-10-search`, and the same for B, B2, D, E, E2 branches).
- Caveat on what s10 *is*: the tournament s10 has no clock; the s10 the owner played on main is `forStrength`
  with `timeCap = true` (`budgetMs = 2000`, main `AiPlayers.kt:43–45, 198`). The baseline is at least as strong as
  what the owner judged too weak — a conservative floor, but not literally the same player.
- Drift on the *other* side: C2's W3 was measured against `W0 = W2T + rest=0+escape=0+knowLook=0+branch=1`, a
  reconstruction of the pre-F1 book. Track A confirmed it "as far as I can read every change" (C2 transcript); no
  same-line-up answers-hash check against the original W2T was run. **UNVERIFIED** that W0 ≡ W2T.

---

## 7. Cost of measurement

**Machine time** (sum of JUnit `Time:` lines in each track's logs, deduplicated; runs used 3 game threads, two runs
at a time on 8 cores, so core-hours ≈ 3 × wall; wave-1/2 logs mostly lack `Time:` lines and are omitted):

| Track | wall-h | main decisions bought |
|---|---|---|
| A (both sessions) | 6.0 | F1 (+15.6 pts vs s8), ladder, checkpoints |
| B | 6.0 | HEval as book eval / leaf: rejected |
| B2 | 10.0 | lastRace keep in the HEval-leaf search (4.0 h), leaf0 vs leaf2 H0 (2.0 h), 3v3 hev (3.6 h): none on the shipped path |
| C first | 7.9 | W1, W2 (both kept without significance), 4 rejects, W2 vs s10 checkpoint |
| C resume | 11.3 | W2T keep (2.1 h), leaf0 check (1.7 h), td3 H0 (1.1 h), W2T vs s10 dev (0.7 h), checkpoints 600–695 (2.0 h), 3v3 (2.3 h) |
| C2 | 11.8 | W3 keep (2.0 h), lw=3 reject (2.4 h), W3 vs s10 dev (1.5 h), NET6 on W3 keep (5.2 h), 3v3 timing (0.7 h) |
| D | 2.7 | playbook ablations, Mortimer off in search |
| E | 10.0 (+1.5 self-play, + training) | NET6 keep, two rejects, checkpoint |
| E2 | 3.6 | pending |
| **Total logged** | **≈ 69.5 wall-h ≈ 210 core-h** | |

Hybrid games cost 100–180 s each (C2 per-game mean 138 s on the running checkpoint; `wall 107 s/game` on W2T vs
s10): about 60–90 games an hour per 3-thread run.

**Games a decision truly needs.** Observed per-pair variance of the pair score: 0.072–0.128, typically ~0.10
(independent games would give 0.125). With σ² = 0.10 per pair:

| Goal | pairs | games | at ~75 games/h |
|---|---|---|---|
| 95% interval ±5 pts | 154 | 307 | 4 h |
| 95% interval ±3 pts | 427 | 854 | 11 h |
| detect +5 pts vs 50% (α .05 two-sided, 80% power) | 314 | 628 | 8 h |
| detect +3 pts vs 50% | 872 | 1,744 | 23 h |

Expected SPRT length (Wald, pentanomial drift, σ² 0.10): SPRT(0.50, 0.55) needs ~424 games at true 0.50 or 0.55,
~674 at 0.53 (P(H1) 0.64), ~259 at 0.57; SPRT(0.50, 0.53) needs ~1,180 at its bounds; SPRT(0.50, 0.60) ~106 at
0.50/0.60 but at a true 0.55 it accepts H1 only half the time. The project's caps (256–512 games) are below the ASN
for the effect sizes actually seen (true ~0.52–0.56 after deflation), which is why so many runs ended "open" and
were pooled.

What minimises games for these effects: (1) keep screening at SPRT(0.50, 0.55) but report a bias-corrected or
held-out estimate, never the stopping-time mean; (2) for the owner's real question, test against s10 directly on
untouched line-ups with SPRT(0.60, 0.65) (≈ 380 games at either bound with σ² ≈ 0.09), instead of chaining
in-family gains; (3) a 96-game checkpoint (±9–10 pts) cannot decide 65% and should not be used for that.

---

## What survives and what does not

**Survives (numbers correct, inference sound):**
- All headline numbers are correctly transcribed and arithmetically right (section 1).
- The SPRT LLR is the standard GSPRT/fishtest `LLR_alt2` formula on the right (score) scale; mirroring is
  implemented correctly; outcomes are deterministic and reproducible.
- F1 in the book (70.3% vs s8 dev, 70.3%/60.4% checkpoints): a large, robust gain.
- NET6 > net leaf inside W2 (56.1%, 512 games, H1; two fresh ranges agree, Q p = 0.79) and inside W3 (56.9%, 320,
  H1). True size likely 53–56%.
- W3 > W0 (59.4%, 160, H1) — subject to W0 ≡ W2T being unverified.
- Old s10 did not drift across waves 1–3; 5000–5095 is untouched.
- The search beats s10 (W2 54.2%, W2T 56.8%, W2+NET6 55.5%, W2T 3v3 61.5%: each individually borderline,
  together consistently above 50%).

**Does not survive:**
- W2T vs s10 71.9% (dev): a stop-biased SPRT(0.6) estimate; ~1% tail under the held-out distribution. Not a strength
  figure.
- W3 vs s10 71.9% (dev, 96 games) as W3's held-out strength: unreplicated; chain-predicted ~66% before the
  transfer discount.
- The implied W3+NET6 ≈ 72% vs s10 by Elo additivity: earlier chains over-predicted 3/3 times by 28–75 Elo; the
  running checkpoint reads 63.5% at 26 of 48 pairs.
- W1 and W2 as proven gains (kept on intervals including 50%); "knowLook adds strength" (identical 145–111 without it).
- Any claim that 300–395 / 600–695 are held out: 300–395 was a decision range in wave 1; 600–647 is now on its third
  checkpoint.
- "≥ 65% vs old s10" is not shown by anything measured so far; the best candidate is plausibly ~60–65%.
