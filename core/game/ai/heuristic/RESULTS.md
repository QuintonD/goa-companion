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

## Wave 3 — Track B (evaluation, 2026-11-22)

HEval is now a named feature vector times weights (`HTerm`, `HEvalWeights`; HEVAL.md). The hand-set weights
reproduce the old evaluation exactly (`GameParityTest` unchanged), and h10 still plays with them. Line-ups are
development (1000+, 2000+) unless they say otherwise. Wilson is the 95% interval. No result below clears the
bar the owner set: an HEval leaf that beats the net leaf head-to-head.

**HEval as h10's own evaluation** (mirrored 2v2, line-ups 1000+):

| Change | Pair | Games | Win | Wilson | Kept |
|--------|------|------:|----:|--------|------|
| outcome-fitted set (`hw.*` = set 1) | : h10 | 128 | 49.2% | 40.7–57.8 | no |
| the same | : s8 (h10 baseline on these line-ups 55.5%, 46.8–63.8) | 128 | 51.6% | 43.0–60.0 | no |
| minion price ×1.4 (`laneDelta=1.4`, REVIEW-2-design §2.2.1) | : h10 | 512 | 51.0% | 46.7–55.3 | no |
| the same | : s8 (same baseline) | 128 | 42.2% | 34.0–50.8 | no |

**HEval as x10's leaf** (`x10+pol=1+scope=3`, line-ups 1000–1031). On these line-ups REVIEW-2-search measured
the hand leaf at 43.8% against s10 and the net leaf at 65.6%.

| Leaf | Against | Games | Win | Wilson | x10 answer ms (mean / p95 / max) |
|------|---------|------:|----:|--------|------|
| set 1, fitted to outcomes (`hevLeaf=1`) | s10 | 64 | 43.8% | 32.3–55.9 | 517 / 2099 / 17378 |
| set 2, distilled from the net (`hevLeaf=2`) | s10 | 64 | 40.6% | 29.5–52.9 | 508 / 2044 / 17082 |
| set 3, every term fitted to outcomes (`hevLeaf=3`) | s10 | 64 | 35.9% | 25.3–48.2 | 547 / 2218 / 14163 |
| hand set, round horizon (`hz=3+hzo=3`), line-ups 1000–1023 | x10 with the net leaf, turn horizon | 48 | 29.2% | 18.2–43.2 | 1423 / 5629 / 23289 |

None of these was continued: all four sit far below the net leaf's 65.6%. A turn-horizon head-to-head of
leaf=0 against leaf=2 was not run, because every leaf=0 variant measured 22–30 points under the net leaf
against s10. The answer times come from a shared, overloaded machine, so read them as upper bounds.

**Positions.** These are predictive scores, not play.

| Measure | Hand HEval | Fitted | Net |
|---------|-----------:|-------:|----:|
| Log-loss of P(win), 1,200 h10 games, 4-fold CV by game | 0.593 | 0.587 | 0.577 |
| Log-loss on 64 x10-vs-s10 games, weights from the h10 games, slope refit | 0.637 | 0.624 (set 1), 0.620 (set 3) | 0.627 |
| x10 leaves (87,568, 32 games): share of searched decisions whose pick matches the net's, held-out fold | 74.5% | 76.1% | — |
| the same: the net's regret of HEval's pick, coins a decision | 1.30 | 1.09 | 0 |

Predicting the outcome better did not make a better leaf. A leaf only has to rank the candidates within one
sample, and those differences are small next to the spread between positions. Within samples the hand terms
explain 53% of the net's variance and the refit 74%: 91% at round-start leaves, 67% at mid-round leaves.
HEVAL.md "Residuals" has where the rest sits.

## Wave 3 — Track B2 (two evaluation terms from the residuals, 2026-11-23)

Two new HEval terms, at weight 0 in the hand set (HEVAL.md "Wave 3 — Track B2"):
- `teamExposure`: each enemy hero's threat charged once, to its best target, through the additive hook
  `HContext.exposureFrom`.
- `lastRace`: the race to empty a match-point Battle Zone.

Every line-up below is development (2520+, 3000+). Wilson gives the 95% interval; "pairs" is the pentanomial
interval over mirrored pairs. Keep decisions are the pentanomial SPRT (p0 0.5, p1 0.55, α = β = 0.05), the same
formula as `Tournament.Sprt.verdict(score)`, run in batches of 24 line-ups. The answer times come from a shared,
loaded machine, so read them as upper bounds.

**Leaf ranking.** The data are 48,144 leaves of x10 with the net leaf against s10: 16 games, line-ups
2520–2527, 2,833 searched decisions. The column is the share of decisions where the weights pick the net's
best candidate.

| Weights | Picks the net's best | Net regret, coins |
|---------|---------------------:|------------------:|
| hand | 71.2% | 1.97 |
| `teamExposure` −1 in place of `exposure` | 71.4% | 2.02 |
| `teamExposure` −0.5 in place of `exposure` | 71.9% | 1.93 |
| `teamExposure` −0.7, `exposure` −0.3 | 71.3% | 2.01 |
| `exposure` −0.4 (control: per-hero, scaled) | 72.4% | 1.84 |
| `lastRace` 0.5 | 71.3% | 1.97 |
| `lastRace` 1 | 71.1% | 1.96 |

Fitted within samples: `exposure` −0.48, `teamExposure` +0.03, `lastRace` −0.32 (held-out regret 1.61).

**Position bench** (Track A's `AiPositionBenchTest`, version 1, puzzles + probe):

| Spec | puzzles right (271) | probe right (233) | probe wp regret |
|------|-----:|-----:|-----:|
| h10 | 77.9% | 51.9% | .0173 |
| h10 + team exposure (`teamExposure` −1, `exposure` 0) | 77.1% | 51.9% | .0176 |
| h10 + team exposure mixed (−0.7 / −0.3) | 77.1% | 51.1% | .0176 |
| h10 + `lastRace` 0.5 | 75.3% (six p36 defends lost) | 52.4% | .0168 |
| x10+pol=1+scope=3+leaf=0 | 82.3% | 62.2% | .0114 |
| the same + team exposure | 81.2% | 59.7% | .0107 |
| the same + team exposure mixed | 82.3% | 60.9% | .0108 |
| the same + `lastRace` 0.5 | 82.7% | 62.2% | .0123 |

**Games** (mirrored 2v2):

| Pair | Line-ups | Games | Win | Wilson | Pairs | SPRT | Kept |
|------|----------|------:|----:|--------|-------|------|------|
| h10 + `lastRace` 0.5 : h10 | 3000–3127 | 256 | 51.2% | 45.1–57.2 | 46.4–55.9 | undecided | no (book keeps set 0) |
| h10 + team exposure mixed : h10 | 3000–3127 | 256 | 48.4% | 42.4–54.5 | 42.6–54.3 | undecided | no |
| h10 + team exposure : h10 | 3000–3127 | 256 | 44.5% | 38.6–50.7 | 39.5–49.5 | H0 | no |
| **x10 leaf=0 + `lastRace` 0.5 : x10 leaf=0** | 3200–3319 | 240 | **57.5%** | 51.2–63.6 | 52.4–62.6 | **H1** | **yes: set 4 `race`, x10's default** |
| x10 leaf=0 + `lastRace` 0.5 : x10 leaf=2 (net) | 3400–3471 | 144 | 38.9% | 31.3–47.0 | 31.4–46.4 | H0 | (the bar is not met) |

In the kept pair, wins by the last push went from 56 to 82 (Throne 15 to 29, Life 31 to 27). Defeats per game
were 0.97 against 0.90. Card plays took 1.25 s against 1.18 s mean (p95 3.7 s for both; max 13.6 s for both).

**What changed in code.**
- `HEvalWeights.RACE` (set 4: hand + `lastRace` 0.5) and `HEvalWeights.searchDefaults`.
- `HybridAi` (Track C's file, additive) seats its book with `hev=4` when its leaf is HEval. A spec's `hev`
  overrides it, and `x10+…+hev=0` is the search as before.
- The book, and so h1–h10 and `GameParityTest`, are unchanged.
- Team exposure stays a weight-0 term for measurement.
