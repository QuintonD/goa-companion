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

Note (audit, 2026-11-22): W1 and W2 were kept on 128-game results whose intervals include 50%; they are the base of
what follows but not proven gains on their own. Later keep decisions use the pentanomial SPRT (`AI_SPRT=0.55`, p0
0.5, α β 0.05, batches of 16 line-ups, `HeuristicTournamentTest`) or 256 mirrored games.

### W2 against the old search, held out (net leaf)

Line-ups 300–395 (held-out checkpoint; no longer clean, see the audit), 2v2, `AI_THREADS=3`:

| Pair | Line-ups | Games | Win | Wilson | Pairs | Defeats / game (variant / s10) | Answer ms mean / p95 / max | Card play ms | s10 answer ms |
|---|---|---:|---:|---|---|---|---|---|---|
| W2 : s10 | 300–347 | 96 | 54.2% | 44.2–63.8 | 8/0/28/0/12 | 0.92 / 1.34 | 244 / 1115 / 8730 | 666 / 2142 / 8730 | 424 / 1351 / 6892 |
| W2 : s10 | 348–395 | 96 | 54.2% | 44.2–63.8 | 11/0/22/0/15 | 0.78 / 1.29 | 241 / 1112 / 7487 | 650 / 2132 / 7487 | 417 / 1317 / 9590 |
| W2 : s10 | 300–395 | 192 | **54.2%** | 47.1–61.1 | 19/0/50/0/27 | | | | |

Times are upper bounds: oldboi is shared by four agents and three games run at once.

### The owner's ruling and the HEval leaf

The learned net is the old search's ensemble. Strength 10's leaf is one switch (`AiPlayers.SEARCH_10_LEAF`); it
ships whichever leaf measures strongest (Track B's HEval, or a new net added as another `HybridLeaf`). Development
here uses the net leaf (`leaf=2`, the strongest today) and each kept change is re-checked with `leaf=0`.

| Variant (leaf 0) | Spec | Opponent | Line-ups | Win | Wilson | Pairs | Card play ms | Kept |
|---|---|---|---|---:|---|---|---|---|
| round horizon at card plays | W2(leaf 0) `+hz=3` | W2(leaf 0) | 1000–1031 | 32.8% | 22.6–45.0 | 13/0/17/0/2 | 2 501 / 8 739 / 25 519 vs 634 | no (worse, and 25 s max) |

A whole round of book play before the leaf is worse than the turn horizon with HEval: the book's own errors over
four turns outweigh what the leaf no longer has to judge.

### The tree after the reveal (`td`, `tw`, `te`, `tk`, `tc`)

After the reveal strength 10 knows every hand, so a question's samples merge into one position and each candidate
had one deterministic book playout. The tree spends that budget on a minimax over the rest of the turn: after a
candidate, the next `td` questions with more than one option branch — ours to the best for us, the enemy's to the
worst for us (`te` 1; `te` 0 lets the book answer for the enemy) — over the book's own answer plus its best by
static score (`tw` options in all, the book's first so alpha-beta cuts early); forced answers are free; at depth 0
the book plays out to the turn's end (the memo's playout, so the memo is shared) and the leaf scores it. The tree
never branches on a card play (simultaneous and hidden). `tk` limits the kinds that branch; `tc` puts the same tree
under a card play's candidates (after our card and the column's enemy replies). Answers are the same on any runner
and with the memo off (`HybridSearchTest`).

| Variant | Spec | Opponent | Line-ups | Games | Win | Wilson | Pairs | Answer ms (variant / opponent) | Card play ms | Kept |
|---|---|---|---|---:|---:|---|---|---|---|---|
| tree depth 2, width 3 (**W2T**) | W2 `+td=2+tw=3` | W2 | 1100–1227 | 256 | **56.6%** | 50.5–62.6 | 20/0/71/0/37 | 282 / 232 mean, 1 144 / 1 049 p95 | 627 / 630 mean, max 8 291 | yes (256 games, interval above 50%; SPRT still open) |
| the same tree with the HEval leaf | W2(leaf 0) `+td=2+tw=3` | W2(leaf 0) | 1500–1627 | 256 | **55.9%** | 49.7–61.8 (pairs 50.3–61.4) | 20/0/73/0/35 | 225 / 188 mean, 923 / 845 p95 | 508 / 507 mean, max 10 252 | the gain holds with leaf 0 |
| a deeper tree | W2T `+td=3` (depth 3, width 3) | W2T | 1700–1763 | 128 | 43.0% | 34.7–51.6 (pairs 35.2–50.7) | 18/0/37/0/9 | — | 522 / 528 mean, max 7 541 | no (SPRT(0.55) H0) |
| the enemy answered by the book inside the tree | W2T `+te=0` | W2T | 1800–1847 | 96 | 50.0% | 40.2–59.8 | 9/0/30/0/9 | — | — | no (even; stopped at 96 for the checkpoint runs) |
| the tree under card plays too | W2T `+tc=2` | W2T | 1300–1315 | 32 | 46.9% | 30.9–63.6 | 4/0/9/0/3 | — | games ~3× longer | stopped: too slow for the 10 s card-play limit, no sign of a gain |

With the tree both leaves lose fewer heroes: leaf 0 0.84 defeats a game against 1.11 for the same search without
it, 88 against 130 of them with no card left to defend (the empty-hand deaths of REVIEW-2-losses.md).

Depth 3 loses to depth 2: with the enemy minimising at every one of its choices over noisy leaf values, a deeper
tree turns more noise into pessimism. Two choices deep is where the tree pays.

W2T against the old search, development line-ups (a checkpoint):

| Pair | Line-ups | Games | Win | Wilson | Pairs | Defeats / game | Answer ms mean / p95 / max | Card play ms | s10 card play ms |
|---|---|---:|---:|---|---|---|---|---|---|
| W2T : s10 | 1600–1631 | 64 | **71.9%** | 59.9–81.4 (pairs 60.5–83.3) | 3/0/12/0/17 | 0.66 / 1.64 | 289 / 1 169 / 9 750 | 648 / 2 076 / 9 750 | 835 / 1 757 / 13 315 |

SPRT(0.6) accepted H1 after 64 games. For comparison W1 made 59.4% against s10 on development line-ups 1000–1063.

W2T against the old search, held-out checkpoint (600–695; no longer a clean range, see the audit), 2v2:

| Pair | Line-ups | Games | Win | Wilson | Pairs | Defeats / game | Answer ms mean / p95 / max | s10 answer ms |
|---|---|---:|---:|---|---|---|---|---|
| W2T : s10 | 600–647 | 96 | 59.4% | 49.4–68.7 | 8/0/23/0/17 | 0.77 / 1.36 | — | — |
| W2T : s10 | 648–695 | 96 | 54.2% | 44.2–63.8 | 10/0/24/0/14 | 0.80 / 1.07 | 290 / 1 198 / 7 797 | 410 / 1 302 / 9 937 |
| W2T : s10 | 600–695 | 192 | **56.8%** | 49.7–63.6 | 18/0/47/0/31 | | | |

The development checkpoint (71.9% on 64 games) was optimistic; held out, W2T is about 57% against s10 (W2 was
54.2% on 300–395). Short of the owner's 65% target. s10 still wins mostly by the last push (31 of its 44 wins on
648–695).

PENDING: 3v3 300–347 against s10, against h10, and the single- and 3-thread times.

### Strength 10 in AiPlayers

`AiPlayers.forStrength(10, …)` is the hybrid search with `AiPlayers.SEARCH_10` and the leaf
`AiPlayers.SEARCH_10_LEAF`; strengths 1–9 are the book (`HeuristicAi`). No wall clock: the same answers on every
device; `cancelled()` stops it and the book answers. The server's takeover plays strength 4 (the book) and stays
bounded. A lighter search at 9 was not wired: the search reads enemy hands as strength 10 does (ADR-062), so a
strength-9 search needs a book without hand knowledge and its own measurements.
