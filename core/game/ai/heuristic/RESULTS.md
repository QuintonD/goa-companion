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

## Wave 3 — Track D (hero playbooks)

All games are mirrored 2v2 on development line-ups (1000+) unless labelled otherwise. "With : without" seats the
hero on both sides, with its playbook on one side and the general book on the other. 192 games give an interval of
about ±7 points, so most single rows below are noise. Under the wave-3 audit's rule, a keep or switch-off decision
needs an interval clear of 50% (≥ 256 games) or an SPRT verdict.

### What changed (commits c2417904, cf1d9f9a, b2e09ac0 and this one)

- **Five playbooks (REVIEW-2-design §5.3).**
  - Rowenna: the execute is gated on `ctx.defenseless` and her initiative, upgrades toward it and keeps it.
  - Emmitt: Reverse Time is priced by the reversed order.
  - Mortimer: knife Attack is valued against the target's defense pool by `killWorth`; board terms raised to the
    research values; only the next wave's spawns are avoided.
  - Hanu: Hurry up! is valued by the pairs whose order it flips; the Ultimate's steal is priced by the stolen card.
  - Nebkher: Illusion sites and board terms.
- **Hygiene (§5.4, #19).**
  - `PlaybookSupport`: `extraReach` / `extraAttack` (a threat beyond printed reach priced from the possible
    pool, a share not a "may hold" gate), `loss`, `poolShare`, and one `moveOutlook`.
  - Victim-aware `threatAdjust(ctx, enemy, target, victimId)`: additive in HeroHeuristics.kt and HContext.kt,
    default = the old overload.
  - Weighted pool gates for Silverarrow and Takahide; Mrak kills by `killWorth`.
- **Measurement.**
  - LegacyPlaybooks (test side) and `AI_H_VS=legacy`.
  - `AI_H_LEGACY_BOOK`: the whole book against the old one.
  - `AI_H_FIRST`, `AI_H_SKIP` and `AI_H_OUT`.
  - Ablation modes `noeval`, `nothreat`, `noplay` and `nochoose` (built but not run: the work was frozen first).
  - `SearchPlaybookAblationTest` (below).
- **Switched off at strength 10:** `HeroPlaybooks.offAtTen = {mortimer}` (the table below). `HeroPlaybooks.forStrength(s)`
  is now the default playbook function of HeuristicAi and HContext (Track A files, one default argument each) and of
  BookPolicy (Track C). Strengths 1–9 are unchanged. A test that passes its own playbook function is unaffected.
- **`pbooks=0` (HybridAi / BookPolicy, Track C files, additive)** now strips the playbooks from the whole search:
  - candidate order and card values;
  - the opponent model's card weights;
  - every playout seat;
  - the HEval leaf's `evalTerms` and `threatAdjust`. The previous version removed them from the playouts only:
    the leaf's HContext used the registry directly.

  Answers outside the search scope (HybridAi's `book`) keep them.

### The five, measured (with : without at strength 8; current : legacy playbook at strength 10)

| Hero | s8 with : without (192) | s10 current : legacy |
|---|---|---|
| rowenna | 53.1% [46.1–60.1] | 50.0% (192); 51.0% (192, after the upgrade fix); 52.0% [45.8–58.0] (256, 1200+) |
| emmitt | 47.4% [40.5–54.4] | 49.0% [42.0–56.0] |
| mortimer | 48.4% [41.5–55.5] | 55.2% [48.1–62.1], then 49.0% [42.0–56.0] |
| hanu | 44.3% [37.4–51.3] | 41.1% [34.4–48.2] with a card-play Hurry estimate (removed); then 49.5% (192) and 48.8% [42.8–54.9] (256, 1200+) |
| nebkher | 52.6% [45.6–59.5] | 49.5% [42.5–56.5] |

The whole current book against the whole legacy book at strength 10 scored 56.8% [49.7–63.6] (1000+, 192) and
51.2% [45.1–57.2] (1200+, 256). No row is a gain under the audit's rule: the five rewrites measure neutral. The one
clear finding is a loss that was removed (Hanu's card-play estimate).

### All 32 at strength 10 (with : without)

The first column is line-ups 1000+, 192 games each. Every hero under 48% was played again on a fresh set
(`AI_H_SKIP=96`, the next 192 games that seat it): the whole book, then its notes with the default style, then its
style alone.

| Hero | 1000+ (192) | Wilson | fresh set (192) | full / notes / style | Both (384) | Wilson |
|---|---|---|---|---|---|---|
| arien | 45.8% | 38.9–52.9 | 56.8% | 56.8 / 55.2 / 51.6 | 51.3% | 46.3–56.3 |
| bain | 52.6% | 45.6–59.5 | | | | |
| brogan | 55.7% | 48.7–62.6 | | | | |
| brynn | 51.6% | 44.5–58.5 | | | | |
| cutter | 43.8% | 36.9–50.8 | 50.5% | 50.5 / 47.9 / 51.0 | 47.1% | 42.2–52.1 |
| dodger | 47.9% | 41.0–55.0 | 50.5% | 50.5 / 47.4 / 50.0 | 49.2% | 44.3–54.2 |
| emmitt | 52.6% | 45.6–59.5 | | | | |
| garrus | 56.8% | 49.7–63.6 | | | | |
| gydion | 50.0% | 43.0–57.0 | | | | |
| hanu | 51.0% | 44.0–58.0 | | | | |
| ignatia | 45.3% | 38.4–52.4 | 54.2% | 54.2 / 54.2 / 57.8 | 49.7% | 44.8–54.7 |
| min | 47.4% | 40.5–54.4 | 47.9% | 47.9 / 47.4 / 48.4 | 47.7% | 42.7–52.7 |
| misa | 43.2% | 36.4–50.3 | 49.5% | 49.5 / 49.5 / 47.9 | 46.4% | 41.4–51.4 |
| mortimer | 44.8% | 37.9–51.9 | 44.8% | 44.8 / 45.8 / 49.5 | 44.8% | 39.9–49.8 |
| mrak | 48.4% | 41.5–55.5 | | | | |
| nebkher | 56.8% | 49.7–63.6 | | | | |
| razzle | 56.2% | 49.2–63.1 | | | | |
| rowenna | 53.6% | 46.6–60.6 | | | | |
| sabina | 50.0% | 43.0–57.0 | | | | |
| silverarrow | 55.7% | 48.7–62.6 | | | | |
| snorri | 45.8% | 38.9–52.9 | 55.2% | 55.2 / 51.0 / 47.4 | 50.5% | 45.5–55.5 |
| swift | 46.4% | 39.4–53.4 | 46.9% | 46.9 / 48.4 / 53.1 | 46.6% | 41.7–51.6 |
| takahide | 46.9% | 39.9–53.9 | 49.5% | 49.5 / 52.1 / 49.0 | 48.2% | 43.2–53.2 |
| tali | 44.8% | 37.9–51.9 | 50.0% | 50.0 / 47.9 / 51.0 | 47.4% | 42.5–52.4 |
| tigerclaw | 49.5% | 42.5–56.5 | | | | |
| trinkets | 52.6% | 45.6–59.5 | | | | |
| ursafar | 46.9% | 39.9–53.9 | 56.2% | 56.2 / 55.2 / 51.0 | 51.6% | 46.6–56.5 |
| wasp | 51.6% | 44.5–58.5 | | | | |
| whisper | 50.0% | 43.0–57.0 | | | | |
| widget | 54.2% | 47.1–61.1 | | | | |
| wuk | 53.1% | 46.1–60.1 | | | | |
| xargatha | 50.5% | 43.5–57.5 | | | | |

Over all 32 heroes the first column sums to 3075–3069: **50.0% [48.8–51.3] over 6144 games**. At strength 10 the
playbooks add nothing to the book on average. That agrees with REVIEW-2-design §5.1: at 5+, the look discards most of
what they say. The fresh set pulled most of the losers back to ~50% (Arien 45.8 → 56.8, Ignatia 45.3 → 54.2, Snorri
45.8 → 55.2, Ursafar 46.9 → 56.2). Only **Mortimer** loses with an interval clear of 50%: 44.8% twice, 172–212 of
384 = 44.8% [39.9–49.8]. His notes lose (45.8%); his style is neutral (49.5%). He is switched off at strength 10.
Swift (46.6% [41.7–51.6]), Misa (46.4% [41.4–51.4]), Cutter, Tali and Min sit at 46–48% with intervals that
include 50%. They stay on, unproven either way.

### Inside the search (x10, HEval leaf)

`SearchPlaybookAblationTest` (`AI_PB_SEARCH=x10+pol=1+scope=2`): x10 with the HEval leaf (`leaf=0`, the default) head
to head against the same search with `pbooks=0`, which removes the playbooks from candidate order, the opponent model,
the playouts and the leaf. The test runs mirrored 2v2 development line-ups from 2000 in batches of 16 line-ups, with two
pentanomial SPRTs (α = β = 0.05): 55% against 50%, and 45% against 50%.

| Games | With : without | Win | Wilson | Pairs (paired interval) | SPRT 55% | SPRT 45% |
|---|---|---|---|---|---|---|
| 32 | 20–12 | 62.5% | 45.3–77.1 | 0/0/12/0/4 (51.9–73.1) | going on | going on |
| 64 | 39–25 | 60.9% | 48.7–71.9 | 3/0/19/0/10 (50.6–71.3) | going on | going on |
| 96 | 55–41 | 57.3% | 47.3–66.7 | 6/0/29/0/13 (48.6–65.9) | going on | going on |
| **128** | **74–54** | **57.8%** | **49.2–66.0** | 8/0/38/0/18 (50.2–65.4) | going on | **H0: not a loss** |

The run was stopped at 128 games, after the loss test had decided, to free the machine for Tracks A–C (the audit's
instruction). Over those games:

- Defeats per game: 0.81 with the playbooks, 1.01 without. With no card left to defend: 63 against 71.
- Wins by last push / Life / Throne: 45 / 20 / 9 with the playbooks, 32 / 13 / 9 without.
- Wall time: about 150 s a game, x10 against x10, three games at once on the shared machine, so these times are an
  upper bound.

The SPRT rules out a loss: inside the search no playbook set is switched off beyond Mortimer (switched off on the
book's evidence above). A gain is likely but not proven. The Wilson interval includes 50%, though the paired interval
(50.2–65.4) does not, and the 55% test had not decided. Under the audit's rule that is not yet a keep decision.

So the playbooks matter far more inside the search than in the book: 50.0% over 32 heroes as h10, about +8 points
as x10's policy and leaf terms. This run did not separate the playouts from the leaf. A run that only drops the leaf's
`evalTerms` would split them (a one-line change in HybridAi.leaf).

### Tests and lint (this branch)

- **Build of d83cb767 (this track's code before the lint fixes).** `jvmTest --rerun` on `*heuristic*`, `*hybrid*`,
  `*NoPeeking*` and `*Parity*`: 106 tests, 1 failure. The failure is GameParityTest's seeded AI game (the transcript
  hash), which seats no Mortimer, at strengths below 10. It changed with the playbook threat terms of the first two
  Track D commits. The hash is re-pinned to the JVM's `b8a8999d…`. `compileKotlinJs` passed.
- **Lint on Track D files.** detekt and ktlint failures in Emmitt, Hanu, Nebkher, Rowenna and Takahide are fixed
  without changing behaviour (wrapping, constants, Hanu's flip loop as a function).
- **Lint outside Track D, left to its owners.** Failures that predate this track: Search.kt, hybrid/BookPolicy.kt
  lines 40 / 120 / 165, hybrid/HybridAi.kt, hybrid/HybridPlayers.kt, HeuristicTournamentTest, HeuristicRegretTest,
  HeuristicDiagTest, test/HybridAi.kt and NoPeekingTest's import order.
- **Not verified.** The re-run after these fixes never ran. It covered the same tests, `jsNodeTest --tests '*Parity*'`
  (the JS hash against the re-pin), detekt, ktlint and compileKotlinJs. It waited more than 2 hours for the machine's
  build lock and was stopped at the tool's time limit. The integrator's build must run it.

### What is left

- Finish the in-search SPRT (`AI_H_FIRST=2128`, about 30 min a batch), then split playouts from the leaf.
- If the net leaf ships (owner update), repeat the ablation with `leaf=2`: only the playouts then carry the playbooks.
- The 46–48% heroes (Swift, Misa, Cutter, Tali, Min) have no proven loss. They need SPRTs, not more single runs.
- The REVIEW-2-design §5.3/5.4 gaps not touched: set-up positioning, a `style(ctx)` overload, the core gaps.
