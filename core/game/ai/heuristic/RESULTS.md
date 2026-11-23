# Heuristic player: integration measurements (ADR-085)

The log of the integration round (2026-11-21): the general book v2 (CardSim) merged with the 32 hero
playbooks. Every row is mirrored 2v2 games of `HeuristicTournamentTest` unless it says otherwise:
`AI_H_GAMES` line-ups from `AI_H_FIRST`, each played from both sides. `hN` is the heuristic player at
strength N, `sN` the old search (`AiPlayers.searchDeterministic`). Wilson is the 95% interval of the
first side's win rate. Line-ups 0–95 are where the books were tuned; 300–395 and 600–695 are held out.

## 1. Baseline: the merged branch

| Pair | Line-ups | Games | Win | Wilson | Notes |
|------|----------|-------|-----|--------|-------|
| h10 : s8 | 300–395 | 192 | 47.9% | 41.0–55.0 | merged branch as it came; ended LAST_PUSH 93, LIFE 63, THRONE 36; defeats/game 1.10 vs 1.15 |
| h10 : s8 | 0–95 | 192 | 55.2% | 48.1–62.1 | the tuning line-ups (general v2 alone made 54.7% here) |
| h10 : h10+heroes=0 | 300–395 | 192 | 52.1% | 45.0–59.0 | the 32 playbooks against the general book alone |

General v2 measured with hero stubs made 46.9% on 300–395, so merging the playbooks changed little:
within noise of the general book, and the playbooks beat the bare general book by a non-significant 2 points.

## 2. Reconciling CardSim and the hero playbooks; flaws a–d

| Change | Pair | Line-ups | Games | Win | Wilson |
|--------|------|----------|-------|-----|--------|
| (a) a discard-only card no longer counts minions; (b) a subject-less space is probed (hero or piece) | h10 : s8 | 300–395 | 192 | 46.4% | 39.4–53.4 |
| + corrections of the static estimate silenced inside a look (`HContext.patches`) | h10 : s8 | 300–395 | 192 | 49.5% | 42.5–56.5 |
| the same, head to head | h10 : h10+patchLook=1 | 300–395 | 192 | 47.4% | 40.5–54.4 |

(c) `HeuristicLegalityTest` narrows each real question by the answer the player would give and checks the
answer still comes from the real question's options (strengths 3, 6, 10; over 100 questions): green, as
general v2 said; no change needed.

More silenced inside a look, by hand (the engine resolves them): Brynn's trap, Cutter's plank, dash and
barrage, Dodger's silver, Garrus's pilum, Swift's reload and suppress, Nebkher's gold, Sabina's +3/+4 target
bonus. Kept, because the look cannot see them: Brogan's bash (who played an attack this turn), Emmitt's snare
and punch (initiatives not yet revealed, the Initiative defense), Bain's guess (a hidden hand).

| A/B (768 games, line-ups 1000–1383, head to head) | Win | Wilson | Pairs a/split/b | Kept |
|------|-----|--------|------|------|
| h10 : h10+patchLook=1 (corrections back on inside a look) | 50.0% | 46.5–53.5 | 63/258/63 | silenced (no double count, measured even) |
| h10 : h10+placeProbe=0 (flaw b off) | 50.8% | 47.3–54.3 | 58/262/64 | probe |
| h10+laneFloor=0.85 : h10 (flaw d) | 48.7% | 45.2–52.2 | 22/350/12 | no floor |
| h10+laneFloor=1 : h10 | 48.0% | 44.5–51.6 | 35/329/20 | no floor |

(d) A floor under `HeroStyle.laneWeight` measured slightly worse across all heroes, so the research values
stay (the knob `laneFloor` remains for measurement). Where a hero's own ablation said its weight hurt, the
hero files already carry the default (Cutter, Garrus); Hanu now does too (below).

## 3. Per-hero ablation at strength 8 (the merged book)

`AI_H_ABLATION=all AI_H_GAMES=48 AI_H_STRENGTH=8`: each hero's book against the general book with the
default style, the hero seated on both sides, 96 games per hero.

| Hero | With : without | Win | Wilson |
|------|------|-----|--------|

Mean 50.6%. Below 47%: Arien, Hanu, Misa, Mrak, Sabina, Takahide, Tigerclaw, Whisper, Widget. Re-measured at
192 games (the 96 above plus 96 more line-ups), split into the whole book, its notes with the default style,
and its style alone:

| Hero | full | rules | style |
|------|------|-------|-------|
| arien | 50.0% | 49.5% | 47.9% |
| hanu | 43.8% | 46.4% | 44.8% |
| misa | 46.9% | 47.4% | 50.0% |
| mrak | 49.0% | 50.5% | 50.0% |
| sabina | 47.9% | 47.4% | 51.0% |
| takahide | 47.4% | 49.0% | 50.5% |
| tigerclaw | 50.0% | 50.0% | 50.0% |
| whisper | 44.3% | 47.9% | 51.0% |
| widget | 47.4% | 43.2% | 49.0% |

Fixed: Widget's Nibble/Gnaw and Misa's gold minion bullet were still adding a minion the look already took
(now static-only); Hanu's style 2 / 0.45 / 0.85 hurt on its own and is the default now (notes and upgrade
order kept). Re-measured on 192 fresh games (`AI_H_SKIP=96`): Hanu 47.9% (41.0–55.0), Misa 51.0%
(44.0–58.0), Whisper 54.7% (47.6–61.6; unchanged code, so its 44% was noise), Widget 51.6% (44.5–58.5).
At 96–192 games a hero's interval is ±7–10 points: the table finds a broken book, not a 3-point one.


## 4. Structural strength (toward h10 ≥ 55% against s8 held out)

Development line-ups 1000–1383 (768 games a pair; 300–395 and 600–695 stay held out). Head-to-head pairs
mostly split (the same seat wins both mirrored games); "pairs a/split/b" counts the ones that do not.

Diagnostics (`AI_H_DIAG=h10:s8`, 48 games from 1000): s8 holds far more (10 a game against h10's 2) and
moves less; in the games h10 lost it took fewer minions (6.3 against 7.7); a typical last push is lost at the
round's end minion battle after neither hero took a minion on turn 3; the old s8 dies less with an empty hand.

| Change | Pair | Win | Wilson | Pairs | Kept |
|--------|------|-----|--------|-------|------|
| teammate first: a computer teammate's known faster card resolves before our card's look (G14) | h10 : h10+allyFirst=0 | 51.6% | 48.0–55.1 | 28/340/16 | yes |
| (same, against the old player) | h10 : s8 / h10+allyFirst=0 : s8 | 49.6% / 49.9% | 46.1–53.1 / 46.3–53.4 | | |
| lane worth ×1.4 | h10+lane=1.4 : h10 | 54.9% | 51.4–58.4 | 91/240/53 | yes |
| lane worth ×0.7 | h10+lane=0.7 : h10 | 43.4% | 39.9–46.9 | 49/235/100 | |
| lane ×1.8, ×2.5 against ×1.4 | | 49.5%, 50.1% | | | 1.4 |
| exposure ×1.4, ×0.7 (`fear`) | | 49.6%, 49.6% | | | |
| lethal 0.8, lethalNext 0.4, lastMult 1.5 | | 48.8%, 50.4%, 50.0% | | | |
| CardSim two branched choices (`branch=2`), width 6 | | 49.3%, 51.0% | | | |
| no G17 mixing (`mix=0`) | h10+mix=0 : h10; with lane 1.4 | 52.2%; 50.9% | | 90/221/73; 84/223/77 | no (mixing is for people) |
| **lane ×1.4 against the old player** | h10+lane=1.4 : s8 | **54.3%** | 50.8–57.8 | 99/219/66 | |
| empty-hand caps (`emptyCap=0.9`, `lethalEmpty=0.8`) with lane 1.4 | : s8 | 54.3%, 54.2% | | 100/217/67, 99/218/67 | no (choices barely change) |
| spare ×2 with lane 1.4 | : s8 | 51.8% | 48.3–55.3 | 86/226/72 | no |
| lane threat share 0.5, 0.15 (`oppLane`) | : h10 | 46.0%, 49.1% | | 61/231/92, 66/245/73 | 0.3 |
| lane kill share ×1.4 (`laneShare`) | : h10 | 50.1% | | 6/373/5 | 1.0 |
| push potential 0.6, 0.25 (`pp`) | : h10 | 46.4%, 51.7% | | 56/244/84, 60/277/47 | |
| push potential 0.25 | : s8 | 53.9% | 50.4–57.4 | 102/210/72 | 0.4 (no gain on s8) |

What the diagnostics still show (48 games against s8 at lane 1.4): h10 heroes die with no card to defend
more often than s8's (about 340 against 250 in 768 games). The cases are tactical: a strike from beside an
enemy on turn 3 that leaves the hero empty-handed and in reach for turn 4, the last card spent on turn 3,
a declined defense on turn 4. The book's caps on that risk barely change a choice; a fix needs a turn-4
plan for the empty hand, not a weight.

## 5. Final table (held out)

The book as committed: general v2, the 32 playbooks, flaws a–c fixed, corrections silenced inside a look,
teammate first, lane ×1.4. Mirrored 2v2 games on line-ups 300–395 (and 600–695 for s8), `AI_THREADS=6`.
"Empty" is defeats of a hero with no card left to defend with. Answer times are per question with more than
one option, on oldboi with six games at once (the maximum includes the cold start of the JVM).

| Pair | Line-ups | Games | Win | Wilson | Defeats/game (empty) | How games ended (push / Life / Throne) | Answer ms h-side mean / p95 / max |
|------|----------|-------|-----|--------|------|------|------|
| h10 : s8 | 300–395 | 192 | **55.2%** | 48.1–62.1 | 0.87 (76) / 1.10 (53) | 108 / 40 / 44; h10 won 50 / 23 / 33 | 5.87 / 21.0 / 545 |
| h10 : s8 | 600–695 | 192 | **53.1%** | 46.1–60.1 | 1.01 (80) / 0.93 (52) | 117 / 44 / 31; h10 won 64 / 15 / 23 | 5.53 / 19.3 / 578 |
| h10 : s5 | 300–395 | 192 | 81.8% | 75.7–86.6 | 0.53 (40) / 1.32 (67) | 81 / 39 / 72 | 4.77 / 16.3 / 110 |
| h10 : h8 | 300–395 | 192 | 50.5% | 43.5–57.5 | 0.95 (94) / 0.88 (81) | 117 / 42 / 33 | 4.61 / 16.1 / 126 |
| h8 : h5 | 300–395 | 192 | 60.4% | 53.4–67.1 | 0.59 (57) / 1.23 (119) | 103 / 45 / 44 | 4.66 / 16.1 / 87 |
| h5 : h3 | 300–395 | 192 | 93.2% | 88.8–96.0 | 0.32 (19) / 1.53 (102) | 86 / 52 / 54 | 1.68 / 5.2 / 29 |
| h3 : h1 | 300–395 | 192 | 59.9% | 52.8–66.6 | 0.66 (40) / 1.61 (75) | 101 / 74 / 17 | 0.42 / 1.1 / 29 |
| h10 : s10 | 300–395 | 192 | 15.6% | 11.2–21.4 | 1.20 (97) / 0.95 (56) | 94 / 48 / 50; s10 won 76 / 38 / 48 | 4.94 / 17.5 / 424 |
| 3v3 h10 : s8 | 300–347 | 96 | 49.0% | 39.2–58.8 | 1.89 (88) / 1.65 (50) | 50 / 28 / 18 | 7.09 / 25.4 / 540 |

Both held-out sets together: h10 against s8 208–176 of 384 = 54.2% (Wilson 49.2–59.1). Against the merged branch as it came
(47.9% on 300–395) that is +7 points; against general v2 with stubs (46.9%) +8.

The longest answers above are a cold JVM and six games sharing the cores: a 3v3 h10 mirror on one thread
(`AI_FORMAT=3v3`, 48 games, run twice in one JVM) answered in at most 276 ms on its first pass and 46 ms
on the second (mean 3.3 ms, p95 11 ms). General v2 with stubs made 17.2% against s10; the old 10 still wins
most by the Throne, the gap the brief names, and it knows the enemies' hands as h10 does.

What is left (step 4 found no weight that moves it): the empty-hand defeats; 3v3, where h10 is even with s8;
strengths 9 and 10 add nothing over 8 (h10 : h8 50.5%); and s10.

## After both review rounds (2026-11-21, 2ecb4604)

Held out, line-ups 300–395, 192 mirrored 2v2 games: h10 vs s8 106–86 = 55.2% [48.1–62.1], pairs 49.5–60.9%;
h10 won by last push 54 / Life 23 / Throne 29, s8 by 52 / 24 / 10. Defeats a game h10 0.91, s8 1.13 (with no
card to defend 77 / 63). Answers h10 3.3 / 11.0 / 311 ms (mean / p95 / max), s8 20.0 / 74.7 / 561 ms.
Unchanged from before the review (55.2% on the same line-ups).

## Wave 3 — Track C: search (strength 10 is the hybrid search, 2026-11-22)

Started from REVIEW-2-search.md's best, `x10+pol=1+scope=3+leaf=2` (call it **B**: the full book in the playouts,
every question with a choice searched, turn horizon, K 8 / Ko 4, top 4, the old learned ensemble at the leaves;
52.1% against s10 held out). Code in `ai/hybrid/` (`HybridAi`, `HybridConfig`); strength 10 seats it
(`AiPlayers.SEARCH_10`, the leaf switch `AiPlayers.SEARCH_10_LEAF`). Development line-ups 1000–1063 (128 mirrored
2v2 games a row), `AI_THREADS=3`, oldboi shared with three other agents; Wilson 95% intervals; pairs = pentanomial
(lost both / – / split / – / won both). Times are per question with two or more options, one thread per answer,
three games at once (mean / p95 / max, ms).

### Speed (same answers)

| Change | Check | Effect |
|---|---|---|
| Samples that deal the same position (the step-start state's `StateHash` plus the answers since) are played once, with their weight (`dd`, on) | the same answer fingerprint (`answers hash`) as without it over two full games | 1 624 samples drawn, 428 distinct: after the reveal strength 10 knows every hand, so a post-reveal question is one position. Answer 562 → 118 ms mean, card play 1 286 → 267 ms (4.8×) |
| One runner task per (sample, candidate) instead of per sample | — | the threads stay busy when one position is left |
| Transposition memo (`tt`): a playout's value is kept for every position it passed (key: position, turn ends left, team, public log) for the real turn; it grows between answers, never during one | the same fingerprint; `HybridSearchTest`: memo on/off and 1/3 threads give the same answers | 13.5% of playouts answered from the memo; answer 118 → 94 ms mean |

### Strength (development line-ups, against the current best x)

| Variant | Spec | Opponent | Win | Wilson | Pairs | Card play ms (variant / opponent) | Kept |
|---|---|---|---:|---|---|---|---|
| wider candidates, more samples (**W1**) | B `+K=24+topS=8+topU=6` | B | 56.3% | 47.6–64.5 | 13/0/30/0/21 | 480 / 329 | yes |
| kill extension (ADR-061's, both sides) | B `+kill=1` | B | 50.8% | 42.2–59.3 | 14/0/35/0/15 | 351 / 318 | no |
| W1 against the old search | W1 | s10 | **59.4%** | 50.7–67.5 | 8/0/36/0/20 | 491 / 830 | — |
| enemy replies with variety (**W2**): while an enemy still has to choose its card, each sample is played 4 times, once with the book's reply and 3 times with the enemy's card drawn from the opponent model (softmax τ 2 of the book's static card scores, 15% uniform) | W1 `+rep=4` | W1 | 55.5% | 46.8–63.8 | 9/0/39/0/16 | 638 / 480 | yes |
| two-turn horizon for turn-3 card plays (empty-hand risk) | W1 `+hz3=2` | W1 | 53.1% | 44.5–61.6 | 15/0/30/0/19 | 524 / 471 | — |
| the same on W2 | W2 `+hz3=2` | W2 | 48.4% | 40.0–57.0 | 11/0/44/0/9 | 677 / 588 | no |
| wider still | W2 `+topS=12+topU=8+top=6` | W2 | 52.3% | 43.7–60.8 | 13/0/35/0/16 | 631 / 625 | no |

Not built or not run, and why. Progressive widening, racing and adaptive K by standard error need samples that
differ; after the merge most questions have one position (a post-reveal question's samples are all the same game
for strength 10), so each candidate is one deterministic playout and the only lever left is which candidates are
played. A round-end horizon on turn 4 is what the turn horizon already does (a turn-4 playout runs through the
round's end: minion battle, push, level-ups). Regret matching at card plays (ADR-061) measured nothing against
s10 and every opponent here is deterministic; not retried. Card pruning (`prune`) is built and off.

### Shipped: strength 10 = W2

`x10+pol=1+scope=3+leaf=2+tt=1+K=24+topS=8+topU=6+rep=4` is `AiPlayers.forStrength(10, …)` (`SEARCH_10`, leaf
`SEARCH_10_LEAF = HybridLeaf.NET`). Strengths 1–9 are the book (`HeuristicAi`). No wall clock: the same answers on
every device; `cancelled()` stops it and the book answers.

FINAL_TABLE

## Wave 3 — Track E: a new net generation for the search's leaf (route b, 2026-11-22/23)

The question: the hybrid search scores the end of book-played playouts, a different distribution from the old nets'
training data (old strength 6–8 self-play), so would a net trained on the book's own games make a better leaf than
the checked-in ensemble (`leaf=2`, the mean of `LearnedWeights` 1–3)? **Alone, no: three retrained ensembles all fit
the book's positions better and none plays better at the leaf. Averaged with the checked-in three, yes: the mean of
six nets (`leaf=3`, `HybridLeaf.NET6`) won 56.1% of 512 games against the same search with `leaf=2` (2v2, Wilson
51.7–60.3); in 3v3 59.4% of 96 (49.4–68.7, cut at the slot limit).**

**Data** (`LearnedSelfPlayTest`, now with book and hybrid sides): 9 600 games on the training line-ups only
(`Teams.TRAIN`, games 0–9 599), 2v2 ×1, 3v3 ×1, 4v4 ×0.3. Each team plays a variant drawn per game:
`h10` ×4, `h8`, `h7`, `h6` ×1 each (noisier), `h10+fear=0` ×1.5 and `h10+fear=0+opp=2` ×1.5 (aggressive: ignores
its own exposure, so walking into danger gets punished). About 1.2 games a second at three threads. In those
games h10 won 52%, h8 46%, h7 48%, h6 41%, the aggressive variants 52–53%. Plus 256 games of the search itself
(the first tournament below, stored), for generation 2. Positions: turn boundaries only (`AI_RESOLVED=0`), which is
what a turn-horizon playout's leaf is: 229 025 positions (235 562 with the search's games). Split by line-up as
before (line-up % 10: 8 validation, 9 test). The data and nets live outside git (`~/.goa-qa/trackE/`); every game
replays from its record.

**Nets** (same features and architecture, 374 → 64 → 32 → 1 with the hero and card tables; λ 0.7):

| Ensemble | Start | Teacher | Data | Test log-loss (each seed) | Checked-in nets on the same split |
|---|---|---|---|---|---|
| `w` (w1–w3) | the checked-in nets (`AI_INIT=cur1…3`, lr 1e-4) | hand | book | 0.5688 / 0.5704 / 0.5693 | 0.5882 / 0.5882 / 0.5892 |
| `s` (s1–s3) | random, seeds 1–3 | hand | book | 0.5709 / 0.5691 / 0.5691 | |
| `g` (g1–g3) | the checked-in nets | the checked-in ensemble | book + 256 search games | 0.5651 / 0.5675 / 0.5657 | 0.5891 (cur1) |

The hand evaluation scores 0.607 there and the 12-term fit 0.588: on book positions the checked-in nets are no
better than a logistic fit of the hand terms, and every retrained net is 0.02 better (2v2 0.561 against 0.582,
3v3 0.571 against 0.591). The distribution shift is real.

**Position bench** (Track A's `AiPositionBenchTest`, version 1, copied in with a hook for `net=` specs; puzzles =
solver truth, probe = the old net-based oracle, so it leans to the checked-in nets), all at W2:

| Leaf | puzzles right (271) | probe right (233) | probe wp regret |
|---|---|---|---|
| checked-in ensemble (`leaf=2`) | 83.8% | 70.0% | .0097 |
| `avg(w1,w2,w3)` | 83.0% | 61.8% | .0099 |
| `avg(g1,g2,g3)` | 84.1% | 65.2% | .0091 |
| `avg(s1,s2,s3)` | 83.8% | 63.5% | .0106 |
| `avg(cur1,cur2,cur3,w1,w2,w3)` | 83.8% | 66.5% | .0093 |

The leaf hardly changes a puzzle answer (225–228 of 271); the probe agrees most with the nets its oracle uses. The
bench did not predict the games below (`g` had the lowest probe regret and lost).

**Games** (W2 = `x10+pol=1+scope=3+tt=1+K=24+topS=8+topU=6+rep=4`, the same search on both sides, only the leaf
differs; 2v2 development line-ups, mirrored, `AI_FAMILY=mixed` = the heuristic tournaments' line-ups and seeds,
`AI_THREADS=3`, pentanomial SPRT at 0.55 against 0.50; Wilson 95%):

| Leaf (against W2 with `leaf=2`) | Line-ups | Games | Won | Rate | Wilson | Pairs (lost both / split / won both) | SPRT | Heroes lost a game (W2's) |
|---|---|---|---|---|---|---|---|---|
| `avg(w1,w2,w3)` | 1000–1127 | 256 | 122–134 | 47.7% | 41.6–53.8 | 34 / 66 / 28 | open after 256 | 1.01 (0.80) |
| `avg(g1,g2,g3)` | 1200–1295 | 192 | 86–106 | 44.8% | 37.9–51.9 | 27 / 52 / 17 | **H0** after 192 | 1.11 (0.95) |
| `avg(cur1,cur2,cur3,g1,g2,g3)` | 1400–1527 | 256 | 142–114 | 55.5% | 49.3–61.4 | 29 / 56 / 43 | open after 256 | 1.11 (0.98) |
| the same, fresh line-ups | 1600–1727 | 256 | 145–111 | 56.6% | 50.5–62.6 | 23 / 65 / 40 | fixed 256 | 1.02 (1.01) |
| **both runs (six nets, `leaf=3`)** | | **512** | **287–225** | **56.1%** | **51.7–60.3** | 52 / 121 / 83 (pairs 51.7–60.4) | **H1** (pentanomial, the 512 together) | 1.07 (0.99) |
| six nets, **3v3** | 1000–1047 (3v3 line-ups) | 96 | 57–39 | 59.4% | 49.4–68.7 | 7 / 25 / 16 (pairs 49.9–68.8) | open; stopped at the 2-hour slot | 1.89 (1.93) |

Against the old search, a checkpoint on line-ups no longer clean (held out before wave 3, so labelled, not final):
`W2+leaf=3` against `s10`, 2v2, line-ups 300–363, 128 games: 71–57, **55.5%** (Wilson 46.8–63.8; pairs 48.2–62.7,
8 / 41 / 15), heroes lost a game 0.99 against s10's 1.18. Not run there with `leaf=2`, so it says where the six-net
search stands against the floor, not how much the leaf added; the 65% target against s10 is not reached by the leaf.

The new three alone are not stronger and lose more heroes than the checked-in leaf: a better fit to the book's
results is not a better leaf (as in v0.9–v0.14, "log-loss hardly separates what play separates"; the search picks
the option the leaf overrates). Averaged with the checked-in three they win: the two families were fitted to
different games (old search self-play; the book's and the search's own) and err in different places, so their mean
is overrated in fewer places, the effect ADR-060's three seeds had (63.0% there). The bench did not see it either
(the six-net row above sits between). Heroes lost a game are level (1.07 against 0.99 over the 512 games; with no
card left to defend 176 against 169).

**Wired** (additive; nothing that plays changes until the switch is flipped): `HybridLeaf.NET6` (`leaf=3`) scores
the playout ends with `LearnedNet.ENSEMBLE_6`, the checked-in three and `LearnedWeightsG1..3` (g1–g3, Base64 like
the others, 209 KB of source each; written in `WeightsWriter`'s exact format, checked byte for byte against the
three checked-in files). On the bench `W2+leaf=3` gives exactly the answers of `W2+net=avg(cur1,…,g3)` from the
saved nets (every row equal). The features are extracted once and each net adds one forward pass, so six nets cost
little over three; the bench's answer times do not separate them on this shared machine (upper bounds). In the web
build the three weights add about 0.6 MB of source to `engine-worker.js` (not measured). **For Track C:** strength
10 takes it with `AiPlayers.SEARCH_10_LEAF = HybridLeaf.NET6` (one line); measured on W2 here, not yet on W2 plus
the tree after the reveal; `GameParityTest`'s strength-10 hash changes when the switch flips (re-pin from the JVM,
check Node).

**What was added** (all opt-in, measurement only): `HybridAi.leafOverride` (internal, null in play: a test can put
a saved net at the leaves); `LeafNets` (test: `h…`, `x…+net=avg(a,b,c)`, `W2` as self-play and tournament sides
through `Learned.side`; `cur1..3` name the checked-in nets); `TeamTournamentTest` `AI_FAMILY=mixed` (the heuristic
tournaments' line-ups, refuses 5000–5095); the trainer: warm start from a net (`AI_INIT`), an ensemble teacher
(`AI_TEACHER=avg(cur1,cur2,cur3)`), several runs in one JVM (`AI_TRAIN_RUNS`), `AI_COMPARE`, and rows kept as one
array each (no big copy, so 230 000 positions train in the 1.5 GB run heap). The bench file is Track A's, with a
three-line hook for `net=` specs (resolve against Track A's copy when merging).

To reproduce (from the checkout root; `S` = the swarm scratchpad, `Q=~/.goa-qa/trackE`):

```
AI_SELFPLAY=4800:$Q/data/book-c.jsonl AI_FIRST=4800 AI_THREADS=3 AI_SIZES='2*1,3*1,4*0.3' \
  AI_VARIANTS='h10*4,h8*1,h7*1,h6*1,h10+fear=0*1.5,h10+fear=0+opp=2*1.5' $S/goa-run LearnedSelfPlayTest
AI_NETS=$Q/nets AI_CACHE=$Q/cache AI_TRAIN=$Q/data/book-a.jsonl,… AI_RESOLVED=0 AI_THREADS=3 \
  AI_TRAIN_RUNS='w1:AI_INIT=cur1;AI_LR=1e-4;AI_SEED=1|…' $S/goa-run LearnedTrainTest
AI_NETS=$Q/nets AI_TEAMS='W2+net=avg(w1,w2,w3):W2' AI_SIZE=2 AI_FAMILY=mixed AI_FIRST=1000 AI_N=128 AI_SPRT=0.55 \
  AI_THREADS=3 $S/goa-run TeamTournamentTest
```
