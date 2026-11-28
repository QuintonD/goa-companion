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

## Wave 3 — Track A (policy: the book's decisions, 2026-11-22)

Mirrored 2v2, development line-ups 1064–1159 (96 line-ups, 192 games a row, the same games in every row unless
noted), `AI_THREADS=3` on a shared oldboi. Wilson 95% intervals; pairs = h lost both / split / won both.

### What changed

- **F1, the rest of the turn after the reveal** (`CardSim.RestLook`, profile `turnRest`, knob `rest`). Every look
  at a decision during the resolution (actions, squares, targets, bullets, our defenses) answers the option on a
  fork and plays the turn to its end: every hero still to act resolves its revealed card with its own static book
  (its playbook's `adjust`/`override`), our own first choice after the answer tries the book's best 4, and on
  turn 4 the round ends through the engine (minion battle, push, level-ups) and the look stops at the next card
  play. `HEval` scores where the turn leaves everyone. Every own square is looked at (64, was 6), and G2's square
  trap (`SpaceRules.commitment`) is off in these looks: the attack is played, not guessed. An enemy defense or
  discard: at 10 its book answers from the known hand (ADR-062); at 9 the look stops and prices it from the pool.
- **F3, the last card's escape** (`HContext.escapeSlack`, knob `escape`, on with `turnRest`). A hero entering
  a turn with one card has nothing to defend with; a threat acting after that card misses when the card's
  movement takes it out of reach first. A Silver (no movement) or a slower card cannot escape: the threat map
  prices the defeat in full.
- **Strength 10's own layer** (profile `knowLook`, `lookBranches`). The card play's one-action look no longer stops
  at an enemy's defense: that enemy's book answers it from the known hand (ADR-062), and the action goes on. Two of
  our own choices inside a look try the book's best 4 answers (one below 10).
- **Strengths.** 9 has `turnRest` (F1 + F3); 10 has 9 + `knowHands` + `knowLook` + `lookBranches = 2`. 8 and
  below are unchanged.

### Development measurements (line-ups 1064–1159, 192 games each)

| Variant | Against | Won | Rate | Wilson 95% | Pairs | Defeats/game (no card) | Answer ms mean / p95 / max |
|---|---|---|---|---|---|---|---|
| h10 (start of wave 3) | s8 | 105–87 | 54.7% | 47.6–61.6 | 17/53/26 | 0.94 (79) / 1.13 (73) | 4.0 / 14.3 / 771 |
| h10 + G4 off (F4, `earlyRed=0`) | s8 | 101–91 | 52.6% | 45.6–59.5 | 15/61/20 | 0.96 (83) / 1.16 (73) | 3.2 / 10.8 / 111 |
| **h10 + F1** (`rest=1`) | s8 | 135–57 | **70.3%** | 63.5–76.3 | 4/49/43 | 0.80 (41) / 1.34 (80) | 13.4 / 44.7 / 908 |
| **h10 + F1 + F3** (`rest=1+escape=1`) | s8 | 136–56 | **70.8%** | 64.0–76.8 | 4/48/44 | 0.73 (36) / 1.31 (80) | 14.7 / 50.5 / 918 |
| h10 + F1, other heroes by the one-ply book (`restBook=1`) | s8 | 135–57 | 70.3% | 63.5–76.3 | 6/45/45 | 0.66 (42) / 1.25 (70) | 69.5 / 290 / 5123 |
| h10 + F1 + F2, 4 sampled reveals (`reveal=4`) | s8 | 125–67 | 65.1% | 58.1–71.5 | 6/55/35 | 0.67 (43) / 1.43 (93) | 37.8 / 156 / 969 |
| h10 + F1 + F2, our choices after the action branched (`reveal=4+revealBranch=1`) | s8 | 124–68 | 64.6% | 57.6–71.0 | 11/46/39 | 0.80 (65) / 1.39 (108) | 66.5 / 296 / 1666 |
| h10 + F1 + F3 + opponent card model in the threat map (`predict=1`) | s8 | 135–57 | 70.3% | 63.5–76.3 | 4/49/43 | 0.71 (33) / 1.32 (83) | 21.2 / 77.7 / 1610 |
| h9 (F1 + F3) | h8 | 114–78 | 59.4% | 52.3–66.1 | 12/54/30 | 1.03 (81) / 0.92 (80) | 13.5 / 45.4 / 842 |
| h10 = h9 + `knowHands` | h9 | 98–94 | 51.0% | 44.0–58.0 | 17/60/19 | 1.06 (89) / 1.20 (92) | 13.0 / 44.2 / 614 |
| … + `knowLook` | h9 | 107–85 | 55.7% | 48.7–62.6 | 13/59/24 | 0.98 (77) / 1.19 (94) | 15.2 / 50.6 / 997 |
| … + `branch=2` | h9 | 106–86 | 55.2% | 48.1–62.1 | 14/58/24 | 1.03 (85) / 1.22 (101) | 16.3 / 56.9 / 876 |
| … + `width=8` | h9 | 104–88 | 54.2% | 47.1–61.1 | 15/58/23 | 0.97 (88) / 1.22 (98) | 16.2 / 56.1 / 1064 |

Reading:

1. **F1 is the step.** Playing the rest of the turn after the reveal took h10 from 54.7% to 70.3% against s8 on the
   same 192 games (+15.6 points, intervals disjoint), above the +11.7 ceiling REVIEW-2-losses measured by giving
   own moves to s10, and halved the empty-hand defeats (79 → 41). It costs about 3× per answer (13 ms mean).
   s8's wins by the Throne fell from 13 to 8 and by Life from 23 to 15.
2. **F3 is small but the right way round** (+0.5, defeats 0.80 → 0.73, empty-handed 41 → 36): kept as part of F1.
3. **F2 as built loses 5–6 points** with and without our own later choices branched: sampling the enemies' cards
   from the book and resolving the turn makes the card choice noisier than the one-action look with its exposure
   split by initiative, and empty-hand defeats rose (41 → 65). It stays in the code behind `reveal` (default 0)
   with its opponent model; the cheaper use of the same model, enemy card weights in the threat map (`predict`),
   measured even (70.3% against 70.8%) at 1.4× the cost and is off.
4. **F4 does not hold.** G4 off was +6.3 on 1000–1063 and −2.1 here; over the 320 games together 55.3% against
   54.1%. G4 stays.
5. **F7 (hold-the-zone) is built behind knob `hold`, off.** `TurnPlan.holdLine`: in our last zone (the next push
   against us takes our Throne, or the last wave decides) and not ahead on minions, the intent is HOLD_LINE (stay
   in the zone, `holdPull` −3 for a square outside it; an attack card targets their best minion unless a sure kill is
   worth as much). Under F1 the turn-4 looks already resolve the round-end battle and push through the engine, and
   Throne losses against s8 fell to 8 of 57. On the position bench it changes no answer (below). Not kept on.
6. **The ladder.** 9 over 8 is 59.4%. Hand knowledge alone gave 10 nothing over 9 (51.0%); what separates them is
   using it inside looks (`knowLook` 55.7%) and spending more of the look on our own choices (`branch=2` 55.2%).
   Both went into 10.

### Resumed session: the ladder at 256 games, the knobs on the bench (2026-11-22 afternoon)

Ladder on fresh development line-ups 1160–1287 (128 mirrored line-ups, 256 games each; the audit's ≥ 256 rule):

| Row | Won | Rate | Wilson 95% | Pairs (lost both / split / won both) | Defeats/game (no card) | Answer ms mean / p95 / max |
|---|---|---|---|---|---|---|
| h10 (= 9 + `knowHands` + `knowLook` + `lookBranches=2`, as committed in ee604f05) vs h9 | 145–111 | **56.6%** | 50.5–62.6 | 18 / 75 / 35 | 1.00 (115) / 1.33 (121) | 16.0 / 54.6 / 788 |
| h10 + `branch=2` only (no `knowLook`) vs h9 | 145–111 | 56.6% | 50.5–62.6 | 14 / 83 / 31 | 0.88 (128) / 1.25 (135) | 16.6 / 56.9 / 950 |
| h9 vs h8 | 150–106 | **58.6%** | 52.5–64.5 | 14 / 78 / 36 | 1.05 (122) / 1.00 (125) | 13.9 / 46.9 / 821 |

Both steps of the ladder hold with intervals clear of 50%. `knowLook` adds nothing measurable on top of the two
branched choices (the same 145–111); it stays at 10 because it is the principled use of the known hand and costs
nothing.

Position bench (version 1, sets puzzles + probe; rounds pending the verdict cache build), the same code as the rows
above (`h10` here is the build before ee604f05, so `h10+branch=2+knowLook=1` is today's 10):

| Spec | puzzles right (271) | probe right (233) | probe wp regret | note |
|---|---|---|---|---|
| h5 | 72.7% | 54.5% | .0173 | |
| h8 | 75.6% | 51.9% | .0179 | |
| h9 | 75.6% | 54.1% | .0162 | |
| h10 (before ee604f05) | 78.6% | 54.5% | .0164 | rounds 76.0% (499) |
| h10+branch=2+knowLook=1 (today's 10) | — | — | — | (branch=2: 79.7% / 54.5%; knowLook: 78.2% / 55.8%) |
| h10+rest=0 | 77.9% | 51.9% | .0173 | F1 off |
| h10+escape=0 | 78.6% | 54.5% | .0167 | F3 off |
| h10+finisher=1 (F6) | 78.6% | 54.5% | .0164 | no answer changed |
| h10+hold=1 (F7) | 78.6% | 54.5% | .0164 | no answer changed |
| h10+allyAfter=1 (postlude, REVIEW-2-design #5) | 78.6% | 54.9% | .0166 | one answer |
| h10+earlyRed=0 (F4) | 78.6% | 55.4% | .0159 | two answers |
| h10+width=8 | 78.6% | 54.1% | .0166 | |
| h10+predict=1 | 78.6% | 54.5% | .0166 | |
| h10+reveal=4 (F2) | 79.0% | 55.8% | .0160 | |
| h9+restSpaces=6 | 75.6% | 53.2% | .0172 | |

The bench separates the ladder (h5 → h10 on puzzles) and confirms that F6, F7 and the postlude almost never change
an answer: they stay off (knobs `finisher`, `hold`, `allyAfter`). One or two answers in 233–271 is noise.

**The book inside the search** (x10+pol=1+scope=3 in this build; the probe oracle is s10 with the net, so it leans
toward the net leaf):

| Spec | puzzles right | probe right | probe wp regret | ms an answer |
|---|---|---|---|---|
| x10 leaf=0 (HEval), F1 book | 82.7% | 59.2% | .0128 | 1823 |
| x10 leaf=0, book `rest=0` | 82.3% | 62.7% | .0112 | 466 |
| x10 leaf=2 (net), F1 book | **86.7%** | 70.0% | .0099 | 1669 |
| x10 leaf=2, book `rest=0` | 83.4% | 72.1% | .0090 | 483 |

The search is ahead of the book on both sets (x10 82.7–86.7% of the puzzles against h10's 78.6%). F1 inside the
playouts: with the net leaf it solves 9 more puzzles (solver truth), on the oracle set it is 5 answers further
from the net-based oracle, with HEval even; it costs 3.5–4× per answer. Undecided on positions.

In games (today's book: F1 + `knowLook` + `lookBranches=2`; x10+pol=1+scope=3+leaf=2 with it against the same search
with `+rest=0` in its book), the first 32 of 256 planned games, development line-ups 1160–1175: **20–12 = 62.5%
[45.3–77.1]**, pairs 2 / 8 / 6, defeats a game 0.84 / 1.16. Indicative only (the interval includes 50%). The cost is
the problem: answers 2.33 s mean / 8.7 s p95 / 55.8 s max against 0.69 / 2.8 / 16.1 s, card plays 5.7 s / 19.4 s
/ 55.8 s against 1.6 / 5.1 / 16.1 s on a loaded shared machine (upper bounds), past the ~10 s card-play budget at
p95. The rest of that run was stopped for a cheaper book.

A cheaper F1 book for the playouts, on the bench (x10+pol=1+scope=3+leaf=2 + the book knob; card = the probe's
card-play mean):

| Book knobs | puzzles | probe | probe wp regret | ms an answer | card play |
|---|---|---|---|---|---|
| `rest=0` | 83.4% | 72.5% | .0086 | 539 | 1.2 s |
| none (today's h10 book) | 87.5% | 70.8% | .0108 | 1814 | 4.5 s |
| `branch=1` | 86.7% | 70.8% | .0093 | 1751 | 4.5 s |
| `branch=0` | 85.2% | 67.4% | .0104 | 1060 | 2.8 s |
| **`branch=1+restSpaces=6`** | **87.1%** | 70.4% | .0095 | **970** | **2.4 s** |

Looking at the book's 6 best squares instead of all 64 keeps the puzzle gain at about twice rest=0's cost instead
of 3.5 times; the two branched choices are not the cost. This is the candidate for `AiPlayers.SEARCH_10` (a knob
string, no code). Games of it against `rest=0` inside x10 (dev 1160–1287, 32-game chunks) were started when this
was written and are pending.

### Held-out checkpoints (line-ups 300–395 / 600–695; no longer clean per the audit, so checkpoints, not the final table)

Today's strength 10 (= `h10+branch=2+knowLook=1` in the build these ran on; the same code paths as the committed
profile), mirrored, `AI_THREADS=3`, shared machine (times are upper bounds). The sealed final range 5000+ was not run.

| Row | Line-ups | Games | Won | Rate | Wilson 95% | Pairs (lost both / split / won both) | Before wave 3 | Answer ms mean / p95 / max |
|---|---|---|---|---|---|---|---|---|
| h10 vs s8, 2v2 | 300–395 | 192 | 135–57 | **70.3%** | 63.5–76.3 | 7 / 43 / 46 | 55.2% | 16.4 / 56.3 / 1031 |
| h10 vs s8, 2v2 | 600–695 | 192 | 116–76 | **60.4%** | 53.4–67.1 | 9 / 58 / 29 | 53.1% | 16.5 / 55.8 / 1073 |
| h10 vs s8, 3v3 | 300–347 | 96 | 68–28 | **70.8%** | 61.1–79.0 | 2 / 24 / 22 | — | 28.1 / 95.8 / 1998 |
| h10 vs s10, 2v2 | 300–395 | 192 | 46–146 | 24.0% | 18.5–30.5 | 55 / 36 / 5 | 15.6% | 15.1 / 52.1 / 870 (s10 416 / 1296 / 8195) |
| h10 vs h9 | 300–395 | 192 | 114–78 | 59.4% | 52.3–66.1 | 9 / 60 / 27 | 50.5% (h10:h8) | 15.6 / 53.8 / 884 |
| h9 vs h8 | 300–395 | 192 | 101–91 | 52.6% | 45.6–59.5 | 16 / 59 / 21 | — | 13.8 / 46.5 / 871 |
| h8 vs h5 | 300–395 | 192 | 117–75 | 60.9% | 53.9–67.6 | 12 / 51 / 33 | — | 4.8 / 16.7 / 112 |

Defeats a game, h10 / s8: 0.89 / 1.35 (300–395; with no card to defend 40 / 76), 0.78 / 1.35 (600–695; 38 / 88);
3v3 1.45 / 2.03. The book against s8 gained 15 points on 300–395 and 7 on 600–695; against s10 it gained 8 and is
still far behind (s10 wins 146 of 192): the book alone is not strength 10's answer to the old search; the hybrid
search on top of it is (Track C). The ladder: 10 over 9 holds held-out (59.4%); 9 over 8 is 52.6% here against
58.6% and 59.4% on the two development sets (over all 640 games 365–275 = 57.0%).

### The position bench (`AiPositionBenchTest`, for every track)

Decision quality on fixed positions, before games (wave-3 audit). Opt-in:

```
AI_BENCH=h10,h10+rest=0,x10+pol=1+scope=3+leaf=0 AI_THREADS=3 goa-run AiPositionBenchTest
AI_BENCH_SETS=puzzles,rounds,probe   # default all three
```

It prints per spec, per set and per decision kind: questions answered, the share answered right, the mean regret
and the mean answer time (shared machine: an upper bound). Specs are `HeuristicTournamentTest`'s (h, s, x, y with
`+knob=value`), so a search spec with book knobs (`x10+pol=1+scope=3+leaf=0+rest=0`) is benched the same way.
Version 1 of the sets:

- **puzzles** (ground truth: the puzzle solver, exact). Every player question with a real choice on every winning
  line of the turn puzzles' answer keys, where not every option wins: 271 questions. Right = the goal stays forced.
- **rounds** (ground truth: the puzzle solver). The player questions on the solution lines of the 22 full-round
  puzzles (enemy plays known, ADR-052): 499 questions. The key holds the solution's answer; another answer is
  proved or refuted by the solver at 200,000 expansions. Those verdicts are cached in
  `src/test/resources/bench/rounds-verdicts-v1-e1.txt` (committed, shared by every spec and track; an answer the
  budget cannot settle is counted apart as "unknown", never as right).
- **probe** (ground truth: an ORACLE, a strong opinion, not the truth). 233 positions from h10's games against s8 on
  line-ups 1000–1011 (24 games, each h10 question with a choice kept with chance 0.06), stored as replayable
  answers in `src/test/resources/bench/probe-v1-2v2-1000-1011.jsonl.gz` (18 KB). Every option is valued by the
  regret probe's oracle (REVIEW-2-losses.md: the old strength-10 search with the learned net, 3× card samples, no
  clock, opponent card model, kill extension). Right = the oracle's best option; regret in its coin scale (a few
  won/lost lines make the coin mean noisy) and in win chance (`wpRegret`, the better summary).
  `AI_BENCH_MAKE=<line-ups> AI_H_FIRST=… AI_FORMAT=…` writes another probe file (never line-ups 5000+).

h10 (the book with F1, commit 2f1781d4), the first bench row:

| Set | n | Right | Regret | ms |
|---|---|---|---|---|
| puzzles | 271 | 78.6% | 0.214 | 41 |
| rounds | 499 | 76.0% | 0.240 | 14 |
| probe | 233 | 54.5% | wp 0.0164 (coins 12.6) | 25 |

By kind, the weak spots: puzzles `play_card` 52.9% (17), `choose_space` 68.6% (35), `choose_unit` 70.8% (65);
rounds `play_card` 64.5% (172); probe `choose_space` 38.1% (63, wp regret 0.020), `play_card` 43.9% (57, 0.017).

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

3v3 (held-out checkpoint 300–347, 96 games): W2T against s10 **61.5%** (51.5–70.6; pairs 52.7–70.2, 5/0/27/0/16);
defeats 1.47 a game against s10's 1.91. 3v3 costs more: W2T's card play 1 533 ms mean, 5 330 p95, 21 957 max (s10:
1 379 / 2 855 / 8 720), one thread per answer with three games sharing the loaded machine, so upper bounds; the
maximum is over the ~10 s card-play budget there (six seats: more enemy-reply columns and longer playouts). With the
app's sample threads it falls by about their number (the samples and candidates are independent tasks).

Against the book alone (h10, 300–347, 96 games): W2T **95.8%** (89.8–98.4; pairs 0/0/4/0/44), 0.42 defeats a game
against h10's 1.38. Answer 234 / 943 / 14 426 ms (mean / p95 / max), card play 538 / 1 735 / 14 426 ms, three games
at once on the loaded machine.

Times, one game at a time (line-ups 348–353 against h10, 12 games; the machine still shared, so upper bounds):

| W2T | Answer ms mean / p95 / max | Card play ms mean / p95 / max |
|---|---|---|
| one thread per answer | 216 / 874 / 6 055 | 510 / 1 692 / 6 055 |
| three threads (`thr=3`, the app's `AiSampleThreads` kind of runner) | 103 / 416 / 1 835 | 222 / 708 / 1 835 |

Both runs gave the same answers (the same order-free fingerprint of every answer, 6246851226319491729): the runner's
width changes the time, not the play.

### Summary (Track C, 2026-11-23)

Strength 10 (`AiPlayers.SEARCH_10`) is now W2T: `x10+pol=1+scope=3+leaf=2+tt=1+K=24+topS=8+topU=6+rep=4+td=2+tw=3`,
the leaf `AiPlayers.SEARCH_10_LEAF` (the net today; one switch, the owner ships whichever leaf measures strongest).

| W2T against | Range | Games | Win | Wilson |
|---|---|---:|---:|---|
| W2 (the tree's own gain, net leaf) | dev 1100–1227 | 256 | 56.6% | 50.5–62.6 |
| W2 (the same with the HEval leaf on both sides) | dev 1500–1627 | 256 | 55.9% | 49.7–61.8 |
| s10, 2v2 | dev 1600–1631 | 64 | 71.9% | 59.9–81.4 |
| s10, 2v2 | held-out checkpoint 600–695 | 192 | 56.8% | 49.7–63.6 |
| s10, 3v3 | held-out checkpoint 300–347 | 96 | 61.5% | 51.5–70.6 |
| h10, 2v2 | held-out checkpoint 300–347 | 96 | 95.8% | 89.8–98.4 |

Short of the owner's 65% against s10 in 2v2. Not kept: round horizon (32.8%), depth-3 tree (43.0%, SPRT H0), the
enemy answered by the book in the tree (50.0% at 96), the tree under card plays (too slow). Open: Track A's F1 book
inside the playouts (+12.5 indicative in x10, 3–4× the cost; `branch=1+restSpaces=6` is its cheap form, and
`p.`/`r.` knobs give the root and the playouts different books), Track B's / E's leaf, and 3v3's card-play maximum.

### Strength 10 in AiPlayers

`AiPlayers.forStrength(10, …)` is the hybrid search with `AiPlayers.SEARCH_10` and the leaf
`AiPlayers.SEARCH_10_LEAF`; strengths 1–9 are the book (`HeuristicAi`). No wall clock: the same answers on every
device; `cancelled()` stops it and the book answers. The server's takeover plays strength 4 (the book) and stays
bounded. A lighter search at 9 was not wired: the search reads enemy hands as strength 10 does (ADR-062), so a
strength-9 search needs a book without hand knowledge and its own measurements.

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
check Node). *(Correction, wave 4 track A: this was false when written: no parity test ran `HybridAi` then. Since
wave 4 `theShippedStrengthTenChoosesAlike` does, and it does change with the leaf.)*

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

## Wave 4 (2026-11-24)

Five tracks on the audit's verified defects (`review-3/V2-code.md`), merged on `t3/wave4-integration`: F (search fixes),
A (the apps' strength 10), G (the card play as a matrix game, off by default), P (the last push: `lpx`) and H (expert
iteration: tooling only, no new leaf, NET6 stays). No tournaments were played for any of them (P replayed 12 recorded
games); each was validated by construction and on fixed
positions. Strength 10 (`AiPlayers.SEARCH_10`) is now W3 + NET6 leaf + `6.cap` + F's defaults (`lp` chance leaf, `hide`,
`deal=8` instead of `rep=4`) + P's `lpx`; no budget in `forStrength` or the
tournaments, the app's 2 s only through `AiPlayers.forApp`. The merge itself is the last subsection.

### Wave 4 — Track F: search fixes (strength 10)

Fixes of the verified defects in `review-3/V2-code.md` (audit branch `audit/wave3-ai`), validated by construction and on
a fixed set of positions; **no games were played** for it. Base: 959e4013 (SEARCH_10 = W3 + NET6 leaf + `6.cap`), which
compiled and passed `HybridSearchTest`, `NoPeekingTest` and `GameParityTest` as it was; nothing needed re-pinning
(`GameParityTest` does not run `HybridAi`, V2 §6.4).

#### What changed
- **Leaf on the win-chance scale (V2 §2.4, knob `lp`, on by default).** `LeafChance` (new, `ai/LeafChance.kt`) is the one
  conversion: a finished game is 1 / 0 (a hair, 1e-6 a round, prefers sooner wins and later losses), a learned leaf is the
  mean of its nets' σ(logit) (`PositionEvaluator.chance`, `NetEvaluator` overrides it: an ensemble averages
  probabilities), a coin score (HEval, the old evaluator) goes through `WinChance`'s Platt fit for the game size (fitted
  for the hand evaluation; for HEval the nearest calibrated mapping, not its own fit). The search's leaf returns it, so
  the column mean is an expected chance to win and one finished column no longer swamps the rest. `lp=0` restores the
  coin scale (measurement only). `prior` (off) still mixes the book's coin-scale card values in.
- **Modelled seats do not see the other team's facedown card (V2 §1.3 and §1.2.3, knob `hide`, on by default).** A
  playout seat's card play (`BookPolicy.answer`) and the opponent model (`BookPolicy.playWeights`) are answered on that
  seat's view: every other-team unrevealed played card back in hand, stamps cleared (`HiddenCards`, new; engine helper
  `EngineSession.viewWith`). The hand is then what it was before the facedown choice (ADR-062: pool known, card not).
- **Enemy cards dealt by enumeration, for both seat colours (V2 §2.2–2.3, knob `deal`, SEARCH_10 `deal=8`, replaces
  `rep=4`).** At a card play every enemy whose card this turn we cannot see — played facedown before us (Blue seats) or
  still to choose (Red seats) — gets the opponent model's chances over its hand pool on its own view (the same
  `model=1` softmax as before); the joint deals are enumerated most likely first (an exact beam, `HybridAi.topDeals`), at
  most 8 and until they cover 95% (`dealP`), each column weighted by its exact probability. Played cards are dealt into
  the column's copy; a still-to-choose enemy plays its dealt card when its turn comes in the playout. No drawn replies and
  no seat-colour-dependent model; the book's clairvoyant column-0 reply is gone. `deal=0` restores the old columns.
  Why enumeration over more samples: the noise was in which 3 replies were drawn (and in the multinomial deal counts at
  Blue seats); enumerating the model's mass removes it at a fixed cost, where more samples only shrink it slowly.
- **Per-answer budget (V2 §6.2, §6.6; knob `ms`, `HybridAi(…, budgetMs)`, `AiPlayers.forStrength(…, budgetMs)`).** Off by
  default (answers stay device-independent). With it, the book's own answer is computed first (it is the answer if no
  column finishes), the stop is polled at every playout step and tree node, a cut playout is NaN, and the answer uses only
  the columns every candidate finished (all candidates compared on the same columns). Without a budget answers are
  unchanged. The app wiring is Track A's.
- **Silent fallbacks counted (V2 §6.7).** `HybridAi.fallbacks`: playout answers that threw or were not an option,
  opponent models that threw, static scores that threw, answers the engine refused, whole searches that threw, and
  `deadline` (answers the budget cut). `DecisionSetTest` prints the sums.
- #12 (`6.cap` drops the model's tail, doubles the book reply's share): with deals there is no book-reply column; the cap
  keeps the heaviest deal columns at their exact weights. #15: the root book's card look is now also the budget's answer.

#### Validation (`HybridFixesTest`)
- Aggregation: the V2 §2.4 scenario (A: one lost column of four, three at 88%; B: four at 5%) — the coin-scale mean picks
  B, the chance mean gives A 0.66 and B 0.05; finished games bracket every position.
- Hidden cards: on 60 Red-last-seat card plays of four h10 games, the next Blue seat's FULL reply and its model weights are
  identical whatever card Red put down; with `hide=0` they depended on it in 2 (reply) and 2 (weights) of the 60.
- Deals: `topDeals` is exact and stops at the mass; on Red and Blue card plays the shipped config takes several deal
  columns, all scores in [0, 1], zero fallbacks, and the same answer under two seeds.
- Budget: 60 ms on a round-2 3v3 card play answers legally within 60 + 200 ms (one deadline cut recorded, no search
  fallback); the same answer unbudgeted took 2.9 s.

#### The decision set (`DecisionSetTest`, for every track)
211 card plays with a choice (105 Red seats, 106 Blue): 156 from the 24 stored 2v2 games of the probe bench
(`bench/probe-v1-2v2-1000-1011.jsonl.gz`, h10 vs s8, line-ups 1000–1011) and 55 from two 3v3 h10-vs-h10 games
(`bench/decisions-v1-3v3.jsonl.gz`, 3v3 line-ups 1000–1001), kept by a hash of game and question. A fresh player per
position and spec (as the apps do), one thread per search, three positions in parallel on the shared 8-core machine
(times are ±15% between runs). Run it as
`AI_DECISIONS="old:lp=0+hide=0+deal=0+rep=4;new:;new1:seed=1" AI_THREADS=3 goa-run DecisionSetTest` (specs are knobs
over SEARCH_10; `seed=n` is the noise control; `AI_DECISIONS_MAX` for a smoke run). About 2 s a position a spec.

| | old (959e4013) | new (this track) |
|---|---|---|
| seed-only change flips the card (all / Red / Blue) | 26/211 12.3% / 15/105 14.3% / 11/106 10.4% | **0/211 0.0%** / 0 / 0 |
| columns mean (Red / Blue), max | 4.0 / 6.6, 15 | 6.2 / 6.4, 10 |
| ms mean (Red / Blue) | 1144 / 1890 | 1765 / 1790 |
| ms mean / p95 / max | 1519 / 5077 / 8586 | 1778 / 5764 / 13862 |
| 2v2 ms mean / max; 3v3 ms mean / max | 1199 / 8586; 2427 / 7794 | 1276 / 6117; 3201 / 13862 |
| silent fallbacks (all kinds) | 0 | 0 |

New differs from old on 20/211 (9.5%), less than old differs from itself under another seed (12.3%). One fix at a time
against old: `lp` alone changes 3 (all three are cards old itself flips with the seed), `hide` alone 4 (2), `deal` alone 17
(13 Red; 10 of them old's own seed flips). Examples: 2v2 game 2010 r10t3 (Blue, Garrus, losing): old's mean −868.8 vs
−869.2 is two lost columns averaged with positions; on the chance scale green-3a is 4.9% against silver's 3.7%. 3v3
1001 r2t3 (Red, Razzle): old silver −37.9 vs red-2b −41.2 from 4 columns, the 8 enumerated deals give red-2b 29.1% vs
26.2%. Most other changes are between cards within 0.5 points of chance of each other.

Cost: Red seats now play up to 8 deal columns where they played 4 replies, so Red card plays cost what Blue ones did
(+54% at Red, −5% at Blue, +17% overall). The worst case grew in 3v3 at Red (13.9 s against 6.9 s on one position, 8
columns where it had 4); the budget is the bound for the app.

#### Not verified
Strength in games (no games played: the integrator's measurement decides). HEval's chance mapping is `WinChance`'s fit,
not one of its own. The opponent model itself (static softmax, τ 2, 15% uniform, independent per enemy) is unchanged and
unmeasured as a predictor. JS timing of the budget. NoPeekingTest still covers 2v2 round 1 only (V2 §1.2).

### Wave 4 — Track A: strength 10 as the apps ship it

Fixes for review-3/V2-code.md §6 (#7, #8, #9, #11 app side, #13) and V1 §2. No tournaments; validated by tests.

- **One player per team for the game (#7).** The web worker (`web/engine/.../EngineHost.kt`, `players`) and the
  phone (`feature/board/.../GameViewModel.kt`, `players`) keep one player per (team, strength) until another game
  starts, as `Tournament` does, so `HybridAi`'s memo carries between answers within a turn. The memo key covers
  everything a playout's value depends on across answers (state hash at step start + answers since + decision
  counter, team, both public logs' hash, turn ends left, horizon; the playout book has no randomness at h10 with
  `temp`/`mix` 0; reply columns never use the memo; engine and turn changes reset it). The cap is now 100 000 entries
  (≈150 B each on the JVM, ≈15 MB) and a full memo stops growing for the turn instead of being dropped at the next
  answer. Probe: peak 889 entries (2v2) and 1 321 (3v3) in a turn, so the cap never binds in play.
  `HybridAppTest` "one player for the game keeps its memo…": 24 searched answers equal to a fresh player per answer,
  the memo is non-empty when later answers of a turn start, more memo hits than fresh players.
- **The app's think time (#11, app side).** Track A built a minimal deadline of its own (`HybridConfig.budgetMs`, knob
  `ms`, card plays the budget and other searched questions a third); **at the merge Track F's deadline replaced it**
  (the same knob, `stopOf`: the book's answer first, the stop polled in every playout step and tree node, only columns
  every candidate finished; one budget for every searched answer, as Track A's own times recommend below).
  `AiPlayers.forApp(strength, runner, budgetMs = APP_BUDGET_MS = 2000)` passes it through `HybridAi(…, budgetMs)`;
  `forStrength`/`deterministic` have none. The numbers in this section are Track A's deadline's. **Conflict to settle:** ADR-085's
  addendum records the owner's "no wall clock, regardless of the device"; the brief asked for ADR-047's 2 s. With a
  budget, answers cut short depend on the device (phone ≠ browser then). `HybridAppTest`: an hour's budget gives the
  unbudgeted answers; a 60 ms budget cut 6 of 6 card plays with legal answers and a worst overrun of 14 ms.
- **Times on one thread** (`HybridAppTimesTest`, `AI_APP_MAX=70 AI_APP_TIMES=2:1,3:1`, s10 vs the book at 9 on dev
  line-ups of its own seed, oldboi loaded by other runs (load ≈ 4), 198 s): 2v2 card plays p50 568 ms, p90 3.2 s, p95
  4.1 s, max 5.2 s; other questions p50 32 ms, p95 315 ms. 3v3 card plays p50 1.2 s, p90 6.0 s, p95 7.0 s, max 8.6 s
  (turn 1 of a round is the slow one); other questions p50 81 ms, p90 2.1 s, max 5.6 s. Over 2 s on this machine: 6 of
  21 (2v2) and 9 of 22 (3v3) card plays. Cutting barely changes the answer: with 2 s 22/22 and 20/22 card plays equal
  the unbudgeted answer, with 667 ms (2 s on a phone 3× slower) 21/22 and 19/22. **JS:** the shipped-s10 parity
  transcript took 379 s on Node against 33 s on the JVM (same answers), about 11× slower, so in the browser 2 s buys
  what about 0.18 s buys on this JVM's single thread: the budget would cut most card plays there.
  **Recommendation (not applied):** keep 2 s for card plays on the phone (three threads, ≈2.2×; few answers change
  even when cut), let the non-card questions use the full 2 s rather than a third in 3v3 (their p90 is 2.1 s here,
  the post-reveal tree), and either give the web worker a longer budget (about 5 s) or make the JS build faster
  (Kotlin/JS emulates `Long`, which the state hash and RNG mixing use heavily; unprofiled). Device numbers: the speed
  meter, below.
- **The speed meter measures what ships (#8).** `AiBenchmark` (v3) runs `AiPlayers.forStrength(10)` (`HybridAi`)
  without the budget and counts card plays over `APP_BUDGET_MS`; `AiBenchmarkTest` checks its work counts on three
  threads.
- **JVM↔JS parity of the shipped s10 (#9).** `GameParityTest.theShippedStrengthTenChoosesAlike`: s10 (kept per team)
  against s9 (the book), 30 searched s10 answers (card plays with replies, the post-reveal tree, level-ups), the
  same transcript on the JVM and on Node (hash `ba4dff96…`). No divergence found. It adds ≈6 min to the JS tests.
- **Sealed line-ups.** `HeuristicTournamentTest` refuses line-ups 5000+ unless `AI_UNSEAL=1` (final table only); a
  unit test checks it.
- **NoPeekingTest (#13).** Three new cases on every shipped strength 1–10: 3v3 (`6.cap`) with the enemies' three
  facedown cards different; a human teammate's facedown card; Min's mines placed by the engine's own flow this
  turn (blast on one space or the other). All pass: **no leak found.** The mid-step token hole (§1.2.2) is not
  reachable today: no card asks another seat a question after placing a facedown token in the same step; the mines
  test asserts the tokens are in the step-start state the determinizer shuffles. Not covered: the determinizer's
  order dependence (§1.2.3) and the human teammate's *hand* (read as for a computer teammate, §1.2.4).
- **Not verified:** anything on a device (phone factor, browser timing beyond Node), strength effects of the budget
  in games, memory on the phone (the per-entry size is an estimate; JS entries are larger).

### Wave 4 — Track G: the card play as a matrix game (`eq`)

Question (review-3/R1 §7.1, V2 §3): strength 10 plays the best reply to one predicted enemy card (the book's
softmax). With the enemy hands known (ADR-062) the only unknown before the reveal is the enemy's facedown card,
so each card play is a simultaneous zero-sum matrix game. Does solving it instead of best-replying matter?

**What was built** (all off by default; `eq=1` turns it on):
- `ai/hybrid/CardEquilibrium.kt`: an exact solver (simplex, Bland's rule, deterministic, plain Kotlin, JVM and JS),
  the joint enemy columns, the win-chance conversion, the pick.
- `ai/hybrid/CardEquilibriumSearch.kt`: the matrix. Rows: our cards, the search's own candidates (all legal cards).
  Columns: the opponent model's likeliest joint card plays of the enemy heroes whose card is unknown (per hero the
  book's softmax over its hand, computed with every card back in hand; independent heroes; top `cap` joint plays,
  else 9). An enemy that already played gets its card dealt one-hot by the determinizer on a shared seed; an enemy
  still to play gets it in the playout (`HybridAi.playout`'s new `forced`). Teammates still to play are the book,
  as in the search: one AI plays the team, and the teammate answers after us knowing our card (a sequential team
  choice, so no iterated best response). Cell: one book playout to the search's horizon, valued as our win chance
  (σ(value / mean coin scale of the leaf's nets); a finished game 1/0; Track G's own helper, to be replaced by
  Track F's when merged).
- `HybridAi`: a knob-gated hook (`eq`) before the card-play search, plus `forced` in `playout` and a probe callback.
  A matrix that cannot be built (no card options, fewer than two rows) falls through to the normal search.
- The pick: a pure saddle point is played as it is (the equilibrium is pure; among several saddle rows the model's
  best). Otherwise a card sampled from the equilibrium with the search's seeded RNG, shares under 10% dropped.

**Validation by construction.** `CardEquilibriumTest` (commonTest, JVM + JS): matching pennies, rock-paper-scissors,
a saddle game, dominated rows and columns, a 1/21 sliver dropped, determinism under a fixed seed, 300 random games
(a third on a coarse grid for ties and degenerate vertices) checked against the LP optimality conditions, joint
columns, win chance. `CardEquilibriumSearchTest` (JVM): on real card plays the `eq=1` answer is legal, comes from a
built matrix, and is the same on 1 and 3 threads and on a fresh instance; and a Blue card play builds the identical
matrix and plays the identical card whichever card Red really put down (no peeking).

**Offline numbers (Step 1)**: `CardEquilibriumProbeTest`, h10 vs h10 games on line-ups 1000–1001 (2v2, 120 card
plays) and 1000 (3v3, 60), both colours, every card play with two or more cards. Strength 10 as it ships and strength
10 with `eq=1` answered each (2 threads, timed); the games went on with the book's answers. Numbers from
`eq_report.py` / `eq_extra.py` (Track G scratch) on the `EQ {…}` lines. Payoffs are our win chance.

| | all (180) | 2v2 (120) | 3v3 (60) |
|---|---|---|---|
| matrix size (rows × columns, mean) | 3.6 × 7.8 | 3.6 × 7.8 | 3.7 × 7.8 |
| model probability the columns cover | 0.90 | 0.95 | 0.78 |
| matrix's model best reply = strength 10's card | 92.2% | | |
| pure saddle point | 72.8% | 71.7% | 75.0% |
| equilibrium's likeliest card ≠ strength 10's card | 28.9% | 27.5% | 31.7% |
| eq=1's actual answer ≠ strength 10's | 52 of 180 (28.9%) | | |
| equilibrium entropy, bits (mean; exact / as played) | 0.21 / 0.19 | 0.22 / 0.21 | 0.18 / 0.16 |
| plays where eq mixes (played entropy > 0.5 bit) | 22.2% | | |
| strength 10's exploitability: value − its card's worst column (mean / p90 / max) | 0.0054 / 0.014 / 0.134 | 0.0052 / 0.013 / 0.134 | 0.0056 / 0.015 / 0.051 |
| plays where it exceeds 1 point | 14.4% | 14.2% | 15.0% |
| eq's cost if the model is right: model value of strength 10's card − of eq's mix (mean) | 0.0039 | 0.0028 | 0.0061 |
| dropping shares under 10%: guarantee lost (mean / max) | 0.0001 / 0.005 | | |
| ms per card play, strength 10 (mean / p95 / max) | 1059 / 4019 / 6979 | 815 / 3237 / 6979 | 1545 / 5231 / 6316 |
| ms per card play, eq=1 (mean / p95 / max) | 1382 / 4511 / 6484 | 1040 / 3509 / 5615 | 2065 / 5691 / 6484 |

Context: the best and the worst card differ by 4.8 points of model value on average (median 3.4), the best and the
second by 2.3 (median 1.1); the enemy's card moves a row by 9.2 points on average. Split by matrix type: in the 131
saddle matrices the model's best reply is exploitable by 0.26 points on average (eq costs 0.27 against the model);
in the 49 mixed ones by 1.15 points (eq costs 0.86). The exploitability is concentrated: the 9 worst plays (5%) hold
53% of it, the top ones late in the game (round 4; 13.4 points at the worst). By seat: enemies still to play
(92 plays) 69.6% saddle, exploitability 0.59 points; enemies already down (88) 76.1%, 0.48 points.

**Reading.** The book's best reply is nearly unexploitable on these matrices: half a point of win chance a card play
on average, and the equilibrium gives up about as much (0.4 points) whenever the book's model is right. The games are
mostly pure (73% saddle, 0.2 bits). So on positions, equilibrium play neither gains nor loses measurably; it changes the
card in 29% of plays, mostly between near-equal cards. Exploitability here is a lower bound (the enemy's best reply
is restricted to the model's top columns, 90% of its mass in 2v2, 78% in 3v3) and is judged by the same book playouts
and leaf that make the matrix.

**Time.** eq=1 is within the shipped search's per-card-play envelope: max 6.5 s vs 7.0 s, p95 4.5 s vs 4.0 s, mean
+0.3 s (2 threads, oldboi, shared). It is slower where the enemy still has to play (9 columns vs the search's 4
replies: mean 1.42 s vs 0.87 s) and no slower where the enemy's cards are down.

**Recommendation: do not put `eq=1` in the shipped candidate for the 65%-vs-old-s10 measurement.** The offline
stakes (±0.5 point per card play, either sign depending on how well the book's model predicts old s10) are below what
one decisive measurement can resolve, and against a deterministic opponent the best reply is the right side of that
trade (R1 §1.6). Keep it as a knob for two uses: unpredictability against people (strength 10's card play is a
deterministic best reply a player can learn, V2 §3; eq mixes in 22% of plays at ~0.4 points' cost against the model),
and as the measured reference if the late-game plays where the best reply is exploitable by 5–13 points are taken up
(a restriction of eq to those plays was not built: it would be a knob choice made on 9 positions).

**Not verified:** any game result with `eq=1` (no games were run beyond the probe's book games); whether the book's
softmax is a fair stand-in for old s10's or a human's card play; the JS run of `CardEquilibriumTest` beyond
`:core:game:allTests` (see the track report). Side effect by construction: for card plays, `eq=1` already avoids V2
§2.4 (logit/±1000 mean: it averages win chances) and §1.3 (the enemy's model is computed with our card in hand, and
its columns are enumerated, not drawn), so it is also a cross-check for Track F's fixes of those.

### Wave 4 — Track P: how the old strength 10 still wins the last push

The audit (`review-3/V2-code.md` §5, §7 #4/#5) expected the search to inherit the book's blind spots: `hold` off,
no push before turn 4, rollout looks that optimise HEval under a NET6 search. Old s10's wins over W3+NET6 on
line-ups 600–647 (the audit log `review-3/w3-net6-vs-s10-600-647.log`): last push 17, Life 10, Throne 2. This
track diagnosed from those games, not from new tournaments.

#### Method: replay the lost games, read the deciding turn

Games are deterministic given the line-up and the seeds, so `HeuristicDiagTest` can now replay single games of a
tournament log (`AI_H_DIAG_ONLY=632R,604B`: line-up and side a's colour, the seeds of `Tournament.runMixed`). A
hybrid side a prints its search trace (each searched question: the candidates' mean values, its answer, **the
book's answer**), every move and removal; `AI_H_DIAG_SAVE=<dir>` keeps the whole game (start + answers, 3 KB
gzipped), from which `RecordedGames.at` rebuilds any position without asking a player. Budget used: 12 single-game
replays (8 traced last-push losses: 600R 604B 606B 609R 613R 615R 628B 632R; four of them again with saves). All 8
reproduced the log exactly (same winner, reason and round). Wall time of game runs: about 15 minutes in all.

```
X='x10+pol=1+scope=3+leaf=3+tt=1+K=24+topS=8+topU=6+rep=4+td=2+tw=3+branch=1+restSpaces=6'
AI_H_DIAG="$X:s10" AI_H_DIAG_ONLY=600R,604B,606B,609R,613R,615R,628B,632R AI_H_DIAG_ALL=1 AI_THREADS=3 \
  goa-run HeuristicDiagTest
```

The board: forgotten-island has three lane zones, so the last wave (one Wave counter) is fought in the middle zone
from a fresh 6 against 6, and the round end decides: the side with fewer minions removes the difference and a
side left with none loses the push, which is the game (p.8; a minions against b: a ≥ 2b pushes).

#### What decided each game (x10 = the candidate; "book" = the book's own answer printed by the search)

| Game | Deciding moment | Minions (us–them) | x10 played | The book's answer | Then |
|---|---|---|---|---|---|
| 632R Widget+Bain | R6 T3, Widget's Gold target | 2–4 | Swift (a hero; he defended) | the minion m36 | 1–4 at turn 4's end, push |
| 613R Trinkets+Bain | R5 T3, Bain's card | 4–5 | Silver, Hold | Red 3a, Primary (attack) | Blue took two: 2–5; one kill on T4: 2–4, push |
| 604B Swift+NebKher | R6 T3, Swift's card | 1–2 (B–R) | Red 3a, as a move (no target) | Blue 2a (the move), Red kept for T4 | no attack left for T4: 1–2, push |
| 606B Ursafar+Sabina | R6 T3, Ursafar's card | 2–3 | Blue 2a | Gold | 1–2 at the end, push |
| 628B Wuk+Takahide | R5 T3, both cards | 5–5 | Silver + Hold; Green move | Blue 2a + Primary; Red 2a | Red took two: 3–5; R6 lost mid-turn |
| 615R Emmitt+Arien | R5 T3, Emmitt's Gold target | 5–5 | Widget (a hero; he defended) | the minion m48 | 3–4 at the end → 2–4; R6 lost mid-turn |
| 600R Brogan+Mrak | R7 T1–T2 (4–6), no minion taken all round | 4–6 | Blue (Fast Travel) ×2; Green, Silver | Green ×2; Red 3a ×2 | 2–6, push in R8 |
| 609R Silverarrow+Rowenna | R6 T4, Silverarrow's Red target | 3–5 | Hanu (defeated: Blue to 2 Life) | Hanu (the same) | 1–5 at the end; lost R7 |

Readings:

1. **Six of eight were decided on turn 3 of a last-wave round by the search overriding the book** (632R, 613R,
   604B, 606B, 628B, 615R). In each, the book's answer attacked a minion or kept the attack card for turn 4; the
   search chose a hero, a Silver Hold or a move. The cause is the horizon: a turn-3 playout stops at the first
   question of turn 4 and the NET6 leaf judges a position whose Minion Battle is already decided by counts it
   does not price exactly (its training positions are turn boundaries of whole games; last-wave turn-4 starts are
   rare there). A turn-4 search does not have this problem: its playouts run through the round end.
2. One (600R) was lost on turns 1–2 of the last wave, again with the search choosing the non-attack cards over
   the book's Red cards. The extension below does not reach turns 1–2 (that is `lw=3`, the whole round, tried in
   C2: 52.1% [96], 45 s worst case, not kept).
3. One (609R) is a turn-4 judgement the book shares (a hero kill that left Blue on 2 Life against a minion that
   would have kept 2–4): not clearly wrong by the rules, so no test.
4. **The book was not the blind one in these games.** In every deciding spot the book's own answer was the
   rule-right one. Turning `hold` on would not change those answers (diagnostic below). `hold` (F7) was built in
   wave 3 Track A and left off because it changed no answer on the position bench (RESULTS.md "Development
   measurements", item 5, and the bench table: `h10+hold=1` "no answer changed"), not because it hurt.
5. Presence is not the problem: in last-wave rounds x10's heroes stood in the Battle Zone at turn starts 81 of 120
   times, s10's 76 of 120 (from the trace, `zone.py`).

#### The fix: play the round end on the last wave (`lpx`, on in `SEARCH_10`)

`HybridConfig.lastPushExtension` (knob `lpx`): while a lane has `lastWaves` (1) or fewer Wave counters, a playout
that would stop at the start of turn 4 of the round it started in (turn 3's TURN horizon), or a post-reveal tree
that reaches that point, plays turn 4 and the round end (the Minion Battle and the push) with the book, and the
leaf scores the next round's start or the game's end. Turn-4 searches already did that; turns 1 and 2 are
untouched. The extension depends only on the position, so the memo stays exact (keys are recorded before it, as
for the kill extension) and answers do not depend on threads (tested). `AiPlayers.SEARCH_10` sets `lpx=1` (a
one-line change in Track F's map; the integrator may move it).

Validated by construction on the recorded positions (`LastPushSearchTest`, opt-in `AI_LASTPUSH_DIAG=1` for all;
the search as it played: the log's knobs, the game's seeds; 3 threads):

| Position | Minions | Game (= search without `lpx`, reproduced) | Book | With `lpx` | ms without / with |
|---|---|---|---|---|---|
| 632R R6T3 Widget's target | 2–4 | Swift | m36 | **m36** | 556 / 958 |
| 632R R6T3 Bain's card | 2–4 | Green 2a | Red 2a | **Red 2a** | 758 / 1 377 |
| 632R R6T3 Widget's card | 2–4 | Gold | Gold | Blue 2b | 489 / 2 189 |
| 613R R5T3 Bain's card | 4–5 | Silver | Red 3a | **Red 3a** | 420 / 657 |
| 613R R5T3 Bain's action (after Silver) | 4–5 | Hold | Primary | **Primary** | 205 / 1 180 |
| 604B R6T3 Swift's card | 1–2 | Red 3a | Blue 2a | **Blue 2a** | 1 083 / 1 284 |
| 604B R6T3 NebKher's card | 1–2 | Silver | Green 2b | **Green 2b** | 474 / 732 |
| 606B R6T3 Ursafar's card | 2–3 | Blue 2a | Gold | Blue 2a | 437 / 658 |
| 604B R5T4 NebKher's action (turn 4: unchanged by design) | 4–5 | Hold | Primary | Hold | 190 / 156 |
| 606B R6T2 Sabina's action (turn 2: unchanged by design) | 2–3 | Movement | Primary | Movement | 7 / 10 |

Every recorded answer reproduces without `lpx` (the positions are the games'). With it, six of the eight turn-3
answers become the book's rule-right answer; the other two change to another card or stay. The book with `hold=1`
gives the book's answer in all ten (so `hold` stays off: it would change none of the deciding answers).

Asserted (JVM tests): 632 (the minion, not the hero: 2 against 4 pushes, p.8), 613 (spend the attack card on turn 3),
604 (keep the only attack card: played now Red 3a offers no Primary, checked through the engine), and the memo /
thread determinism of the extension. `LastPushBookTest` pins the book's turn-4 battle arithmetic on a hand-built
last-wave position (behind 2–4: the minion keeps the game, checked through the engine to the round end; level 2–2:
the minion wins the push) at strengths 10, 8 and 5; the book passed both before any change, so no book rule changed.

**Not shown**: that these games are saved. Played out with the book for both sides to the round end, every option
in these positions still loses (the `bookRoundEnd` column of the diagnostic): by turn 3 the round was already lost
against the book's turn 4. The extension removes a verified defect in how the search judges the deciding turn; the
win rate is for the integrator's measurement.

#### Position bench, before and after (`AiPositionBenchTest`)

The book is unchanged, so its bench rows are unchanged by construction (h10: puzzles 78.6%, rounds 76.0%, the
table above). `lpx` can only change a search answered on turn 3 while a lane is on its last wave; the bench now
has `AI_BENCH_LASTWAVE=1` to keep exactly those questions (46 of puzzles + rounds) and `AI_BENCH_MISSES=1` to name
the wrong ones. Everywhere else the two specs answer identically by construction.

```
X='x10+pol=1+scope=3+leaf=3+tt=1+K=24+topS=8+topU=6+rep=4+td=2+tw=3+branch=1+restSpaces=6'
AI_BENCH="$X,$X+lpx=1" AI_BENCH_SETS=puzzles,rounds AI_BENCH_LASTWAVE=1 AI_BENCH_MISSES=1 AI_THREADS=2 \
  goa-run AiPositionBenchTest
```

| Spec | puzzles (last wave, turn 3) | rounds (last wave, turn 3) | ms an answer (puzzles / rounds) |
|---|---|---|---|
| W3+NET6 (as played) | 10/10 | 31/32 right, 4 unknown | 397 / 621 |
| W3+NET6 + `lpx` | 10/10 | 31/33 right, 3 unknown | 698 / 1 527 |

The same number right; one former "unknown" (a choose_action) is now right and one space answer is now wrong:
`r10` ("Nothing Left to Defend With"), a full round whose enemy plays are scripted and known to the solver
(ADR-052). With `lpx` the search plays the enemy's turn 4 with the book instead of judging it, and here the
book's turn 4 is not the scripted one. Both runs also share one miss (`r39`, a choose_unit). Cost: about 1.5–2.5×
on these questions only (two threads, shared machine: upper bounds); on the recorded positions 1.2–4.5× (0.2–2.2 s
with three threads). Worst-case card-play time grows on last-wave turn 3 (V2 §6.2: no wall clock); not measured.

#### What is still open (for the integrator)

- **The win rate.** No tournament was run; the extension is kept on the strength of the recorded positions.
- **Turns 1–2 of the last wave (600R).** The search also preferred non-attack cards over the book's Red cards
  there; the extension does not reach it. The leaf is the lever (Track H), or `lw=3` with a cheaper policy.
- **Interaction with Track F's aggregation fix (V2 §2.4).** The extension reaches more finished games (±1000) at
  turn 3; with the column mean on the logit scale one lost column dominates. With F's win-probability mean this is
  the intended behaviour; without it the search leans to avoiding any immediately lost column.
- **3v3**: the same knob applies (base knob, not `6.`); not checked on a 3v3 position.
- `hold` stays off (it changed none of the ten deciding answers, and none on the bench in wave 3).

### Wave 4 — Track H: expert iteration, the leaf trained on the search's own values

**Question.** Train the leaf on the positions the search queried, with the search's backed-up values as targets
(review-3/R2-cardgames.md §4.3, "expert iteration"), using Track E2's data: the search `W2+leaf=3` re-searched the
turn boundaries of 1 056 stored search games (`~/.goa-qa/trackE2/data/sv-x.tsv`, 26 959 positions; both teams searched
at 26 706). **Answer: no new leaf.** The search's values carry real information that NET6 lacks, but it sits where the
search's turn horizon contains the round-end push, and two training runs could not move a net toward it. Nothing that
plays changed; no game was played except a 2-game smoke run of the recipe.

**The targets first.** A team's value of its own best card is optimistic: Red's plus Blue's value of the same position
is +8.2 coin-equivalents at the median (should be 0), so the trainer now averages the two views in Red's terms before
anything else (`readSearchValues`). Read as σ(v / 37.85) (the six nets' mean coin scale) the values predict the result
*worse* than NET6 (held out 0.656 against 0.622), because a playout that wins inside the turn scores the win
(thousands of coin-equivalents): 4% of positions sit past the old ±12 logit clamp and the reliability table bends
(predicted 0.01 → observed 0.12). Capped at NET6's own 99th-percentile confidence on the training split (|logit|
3.28, `AI_SV_CLAMP`; chosen from the nets, not fitted to results) they are a better predictor than NET6:

| Δ log-loss, search value − NET6 (per-game bootstrap 95%) | Training split (860 games) | Held out (98 games) |
|---|---|---|
| every searched position | −0.0013 (−0.0053 … +0.0031) | −0.0081 (−0.0214 … +0.0077) |
| turn 4 of the round (the playouts include the round-end push) | **−0.0233 (−0.0305 … −0.0159)** | **−0.0300 (−0.0503 … −0.0070)** |
| last push (a lane at its last Wave counter), turn ≥ 3 | **−0.0375 (−0.0556 … −0.0193)** | −0.0654 (−0.1277 … +0.0043) |
| not the last push | −0.0025 (−0.0053 … +0.0002) | −0.0066 (−0.0132 … +0.0011) |

So the search knows more than the leaf exactly where s10 beats us (the last push), and only when its playouts reach
the push; at turns 1–3 a searched value is no better than NET6's static one.

**Training** (`LearnedTrainTest`, warm start from g1–g3, lr 1e-4, the book's games keep λ-returns 0.7 from the NET6
teacher). Searched positions target 0.2 · result + 0.8 · capped search value. λ 0.2: on the training split the capped
values' Brier (0.2050) is within ~0.01–0.02 of the result noise E[V(1−V)] (the best predictor available scores 0.2042),
so under independent errors the result deserves ~0.05–0.15 of the weight; 0.2 hedges for the search's errors being
correlated with NET6's (it is built from NET6 leaves). Two runs, three seeds each, about 4 minutes of training in all:

- **ei1–3**: every position (the searched ones are 10%), early stopping on the validation results. Validation loss was
  flat from epoch 1 (best epochs 2, 1, 6): the stopping rule measures the book's games, so the nets stayed g.
- **es1–3** (`AI_SV_ONLY=1`, now also stops on the searched validation positions against their targets): imitation
  loss improved 0.0010–0.0020 over 10–18 epochs, then stopped.

**Offline, held out** (line-up % 10 = 9; `tools/ai/expert-iteration/offline.py` on the `LearnedTrainTest` dump;
ensembles average logit × coin scale as the leaf does; Δ = per-game paired bootstrap against NET6):

| Leaf | Searched positions (2 468, 98 games): log-loss / Brier / RMS to the search value | Δ log-loss vs NET6 | Book positions (22 989, 960 games) Δ | Late last push (2 639, 746 games) Δ |
|---|---|---|---|---|
| the search value itself | 0.6139 / 0.2126 / 0 | | | |
| NET6 (`leaf=3`) | 0.6220 / 0.2175 / 0.1018 | — | — | — |
| cur1–3 (`leaf=2`) | 0.6287 / 0.2203 / 0.1083 | +0.0067 (−0.0015 … +0.0153) | +0.0140 | +0.0279 |
| g1–3 | 0.6210 / 0.2173 / 0.1061 | −0.0010 (−0.0097 … +0.0074) | −0.0096 | −0.0140 |
| ei1–3 | 0.6202 / 0.2170 / 0.1061 | −0.0018 (−0.0122 … +0.0093) | −0.0159 | −0.0172 |
| es1–3 | 0.6215 / 0.2175 / 0.1053 | −0.0005 (−0.0163 … +0.0157) | −0.0072 | −0.0086 |
| cur1–3 + ei1–3 (NET6 with ei for g) | 0.6215 / 0.2172 / 0.1011 | −0.0005 (−0.0028 … +0.0016) | −0.0043 | −0.0036 |
| cur1–3 + es1–3 | 0.6222 / 0.2176 / 0.1013 | +0.0003 (−0.0071 … +0.0070) | −0.0001 | +0.0020 |
| NET6 + es1–3 (nine) | 0.6208 / 0.2170 / 0.1009 | −0.0012 (−0.0068 … +0.0043) | −0.0039 | −0.0054 |

Calibration (expected calibration error on the searched positions, 10 bins): NET6 0.052, es1–3 0.039, ei1–3 0.045,
cur + es 0.045, the capped search value 0.049. Every net is overconfident at 0.7–0.8 there (0.74 predicted, 0.57
observed). The "late last push" column is mostly book games (all held-out positions with a lane at its last Wave
counter, turn ≥ 3); on the 273 searched ones no net differs from NET6 by more than its interval.

**Reading.** On the leaf's own distribution (searched positions of search games) no new ensemble is distinguishable
from NET6, and none comes closer to the search's values (RMS 0.101–0.106 against 0.102). The gains on the book's
positions are the g family's trait (a better fit to the book's results), which Track E already showed is not a better
leaf. So nothing was wired: no `leaf=4`, no new weights, `SEARCH_10` untouched. Why the nets do not absorb the
search's knowledge: what the search adds is the round-end push played out (turn 4), and the residual between the search
value and NET6 is not predictable from the features with ~22 000 positions (es moved RMS by 0.001). **For the
integrator and Track P:** the measurable gap between search and leaf is at the end of turn 3 of a last-push round (the
leaf judges a position whose push it cannot see); a horizon that reaches the push there (HybridConfig
`lastWaveHorizon`) targets it directly. C2 measured `lw=3` at 52.1% of 96 and did not keep it; this is evidence for that
mechanism, not a game result.

**What was added** (all test/tools, nothing in play): `LearnedTrainTest` "dump held-out logits" (`AI_DUMP`,
`AI_DUMP_FILES`, `AI_DUMP_NETS`: every held-out position and every searched one, with each net's logit, the search
value, turn / round / Wave counters / match points), the search values read as the Red-view mean capped at
`AI_SV_CLAMP`, `AI_SV_ONLY` stopping on the searched positions' targets; `tools/ai/expert-iteration/offline.py` (the
tables above: search-value calibration, held-out log-loss / Brier / RMS / KL with paired per-game bootstrap,
reliability tables, last-push subsets) and `tools/ai/expert-iteration/round.sh` (the next round with no hand work:
self-play with live search values (`AI_SV_LIVE`) on training line-ups from game 20 000 (refuses 4000–5999) → cap fit →
three seeds → dump → tables, with the adoption rule in its header). Nets and dumps: `~/.goa-qa/trackH/` (nets ei1–3,
es1–3; `dump/base.md`, `run1.md`, `run2.md`).

**Not verified:** anything in play (by design); whether more search data (round.sh's self-play, ~250 games an hour)
would let a net learn the push (es's flat imitation curve says not soon); the search values come from W2 + NET6, not
today's SEARCH_10 (W3 + tree + cap), which the next round's default `SPEC=W2` uses.

### Wave 4 — integration (t3/wave4-integration)

Merged in order F (e8a69018), A (521eeece), G (a8035692), P (97eaa202), H (f5f14ae4) onto `feature/ai-wave4`.

- **Deadline (F × A).** Both added the knob `ms`. F's mechanism kept (`HybridAi.stopOf`, book answer first, the stop
  polled in playouts and trees, `deadline` in `HybridAi.fallbacks`); A's own deadline (`late`, `pastDeadline`, `cut`)
  dropped. Kept from A: `forApp` (now `HybridAi(seed, knobs, runner, budgetMs)`), the kept-player hosts (web worker,
  phone), `MEMO_CAP` 100 000 with `remember` (a full memo stops growing, no mid-turn drop), `memoSize`, and its tests,
  re-pointed at `fallbacks["deadline"]`. F's `forStrength(…, budgetMs)` parameter removed: the budget is `forApp`'s
  alone. One budget for every searched answer (F's), not A's third for non-card questions. `HybridAi.stats` stays four
  long: A's fifth entry would have overrun `HeuristicTournamentTest`'s four-slot sum.
- **Equilibrium on F's columns (G × F).** G built its own matrix (one determinization, the model's top joint plays,
  one playout a cell, `forced` cards, its own σ(value / coin scale) conversion). F's deal columns are the same thing
  with more samples, so the port takes the matrix from the search's own values: a column per distinct joint deal
  (`Column.deal`, played facedown + planned), its cell the weighted mean over the samples that dealt it, its weight the
  summed column weight (`CardEquilibriumSearch.matrix`); the cells are F's chance leaf (`LeafChance`), so G's
  conversion (`CardEquilibrium.winChance`), its column enumeration (`jointColumns`, F has `topDeals`) and the playout's
  `forced` (F has `planned`) are deleted. The model-weighted row mean is then exactly the search's own score of that
  card, so the matrix's best reply is SEARCH_10's card (unless `risk`/`prior`/`mm` are set) and `eq=1` costs nothing
  over the search. `eq` needs `deal` > 0 and `lp` (otherwise it does nothing); default 0. G's Step 1 numbers above were
  measured with G's own builder on the pre-F base and are not re-measured with the ported one.
- **Last push on F's search (P × F).** `lpx` kept on in SEARCH_10 and in the knob list beside F's. The playout's
  extension runs inside F's loop, so F's per-step stop (the budget) and its deals cover turn 4 too; deals and the
  playout's `planned` cards apply to the root turn's card plays only, so turn 4's are the book's, as in P. The tree's
  extension playout now gets the stop (`stop = t.cancelled`: a cut is NaN, as for the tree's other playouts) and not
  the column's reply or planned cards (they were turn 3's). The leaf at the end is F's `LeafChance`, which is the
  aggregation P's open item asked for.
  On the recorded last-push positions after the merge (`AI_LASTPUSH_DIAG=1 LastPushSearchTest`, one core): P's spec
  with `lpx` (now with F's `lp` and `hide`, deals off) gives the book's answer on 7 of the 8 turn-3 questions (P had 6:
  632R Widget's card is now Gold, the book's); the shipped SEARCH_10 (with deals) on 6 of 8: it misses 606B Ursafar's
  card (Blue 2a for Gold, as P's spec) and 613R Bain's card (Silver for Red 3a; P's spec, asserted in the test, plays
  Red 3a). Without `lpx`, F's fixes alone already turn 613R's card to Red 3a. Turn-2 and turn-4 controls unchanged.
- **Search values on F's scale (H × F).** `HybridAi.onSearched` (null in play) now takes its means from F's
  `columnScore` without the risk term, and reports them on the coin-equivalent scale the trainer reads
  (`LearnedTrainTest` divides by NET6's coin scale 37.852): with `lp` the mean is over win chances and is reported as
  `LeafChance.toLogit(mean) × the leaf's coin scale` (`NetEvaluator.coinScale`, NET6's 37.85; a non-net leaf uses
  `WinChance.coinScale`, the Platt fit's inverse slope); with `lp=0` the old coin-scale mean. So recordings keep their
  format, but a value is now logit(E[chance]) rather than E[logit × scale]: a column that finished the game counts as
  a probability of 1/0, not ±1000 coin, which is the outlier Track H capped with `AI_SV_CLAMP` (the cap stays). Values
  recorded before the merge (H's `sv-x.tsv`, from W2) are on the old mean; mixing the two in one training run is not
  checked. Answers decided by `eq` are not reported (off by default).
- **Checks on the merged branch (287f15d7, no games).** Gate: `:core:game:jvmTest` 160 suites / 2 189 tests, JS
  `:core:game:jsTest` 15, `:web:app:jsTest` 166, `:web:engine:jsTest` 8, all pass; detekt / ktlint clean;
  `:app:compileDebugKotlin` builds. The shipped-s10 parity transcript changed with F's SEARCH_10 and was re-pinned
  (`b3bfc3bb…`, the JVM and Node gave the same hash; `lpx` did not move it). Decision set (`AI_DECISIONS=
  "new:;new1:seed=1;eq:eq=1" AI_THREADS=3`, 2026-11-25 06:01, oldboi load ≈ 2, merged SEARCH_10 with `lpx`):

  | | merged SEARCH_10 | Track F alone (above) |
  |---|---|---|
  | seed-only change flips the card (all / Red / Blue) | **0/211 0.0%** / 0 / 0 | 0/211 |
  | ms mean (Red / Blue) | 1878 / 1832 | 1765 / 1790 |
  | ms mean / p50 / p95 / max | 1855 / 1284 / 5615 / 13 608 | 1778 / – / 5764 / 13 862 |
  | silent fallbacks (all kinds) | 0 | 0 |

  `eq=1` plays another card than SEARCH_10 on 50/211 (23.7%; Red 24/105, Blue 26/106), at the same cost (mean 1783 ms,
  max 13 806: the matrix reuses the search's values). Track G measured 28.9% with its own builder. `HybridAppTest`
  (one core, night cap): a 60 ms budget cut 6 of 6 card plays, worst overrun 150 ms.

### Wave 4 — the decisive measurement (2026-11-25)

Merged SEARCH_10 (`x10+pol=1+scope=3+tt=1+K=24+topS=8+topU=6+deal=8+td=2+tw=3+branch=1+restSpaces=6+6.cap=8+lpx=1+leaf=3`,
= `AiPlayers.SEARCH_10` + NET6) against old s10 (`searchDeterministic(10)`), 2v2 mirrored, **fresh line-ups 4000–4047**
(nothing tuned or checkpointed on 4000–4999), `AI_SPRT=0.65 AI_SPRT_P0=0.60` (pentanomial GSPRT, α = β = 0.05, batches
of 16 line-ups, at most 192). Raw log: `wave-4/search10-vs-s10-4000-4047.log`.

| | |
|---|---|
| Verdict | **SPRT(0.60, 0.65): H1** after 96 games (48 line-ups), i.e. ≥ 65% accepted at 5% error rates |
| Score | 72–24 = **75.0%**, Wilson 65.5–82.6%; pairs 66.3–83.7% (pentanomial 3/0/18/0/27) |
| Batches | 81.3% (32), 73.4% (64), 75.0% (96) |
| How SEARCH_10 won (72) | last push 36, Throne 19, Life 17 |
| How old s10 won (24) | last push 13, Life 9, Throne 2 (last push 54% of its wins; was 17/29 = 59% at 600–647) |
| Card play mean / p95 / max | 1319 / 4204 / 12 920 ms (old s10 587 / 1185 / 4147); no budget in tournaments |
| Wall time | 4341 s (72 min), 3 games at once |

Caveats: the point estimate of an SPRT that stops early is biased upwards (it stopped on a good stretch), so read the
true score as "≥ 65% with 95% confidence by the test", not "75%"; the Wilson interval is the fixed-sample one and is
only approximate after a sequential stop. 2v2 only: 3v3 was not measured. No app budget: with the 2 s budget
(`forApp`) answers are cut on slow card plays (Track A: 22/22 2v2 and 20/22 3v3 card plays unchanged at 2 s on the JVM;
the browser is ~11x slower). The sealed 5000+ final table is still unrun.

## Efficiency: the same strength 10, faster (2026-11-25, ADR-086)

Branch `t3/ai-efficiency` (from `feature/ai-wave4` ede2c722). Goal: make the shipped strength 10 (`SEARCH_10` + NET6,
75% against the old strength 10 above) cheaper **without changing a single answer**, so its strength needs no new
games: every change is exact by construction and checked on the decision set and the parity transcripts.

**Where the time went** (JFR, 50 decision-set positions, one thread): 94% inside the book's answers in the playouts
(`BookPolicy.answer` → `HeuristicAi.look` → `CardSim.rest`, the rest-of-turn look); of it, the static scoring of every
inner question (`General.score` → `HContext.placement`/`exposure`) ~64% and the engine's step re-runs ~26%; the
learned leaf ~1%, determinization and the memo key under 1%. On Node (the web app) two costs of Kotlin/JS alone came
on top: a property-reference object built on every read of a `by lazy` property (22% of the time) and a JavaScript
stack captured by every engine signal (`Suspend` is an exception: 16%).

**What changed** (none of it changes what is computed, only how often and how):
- `CardSim.ranked`: each option scored once, then the same stable sort (the selector ran on every comparison).
- `HContext`: per-decision memos of pure facts about its state that every option and space asked again — heavy
  immunity, kill and discard worth, goal zone, match point, plays left, a hero's attack/defense/lane cards, item
  bonuses, minion targets, the lane-kill value, movement and Fast Travel destinations, and `placement` itself (kept
  apart while a hero's note is looking, `looking`, which a note may read). The state a context reads is never
  changed while it lives (the existing card and threat caches already relied on it).
- `BoardGeometry.distanceToZone`: a table per map instead of a minimum over the zone's spaces each time.
- `HeroState.handSize` / `hasUnresolved`: counts without building the hand list.
- Engine: a fork no longer copies the start state it immediately replaces; `CardSim` forks that are answered at
  once share the shown state (`forkForAnswer`, as `HybridAi` already did); in a determinized copy a re-run no
  longer rebuilds the card's action options (the Primary probe) to check an answer the session knows was accepted
  on the same start with the same answers before it (`EngineSession.trusted`, `Game.askAlways`; ENGINE.md).
- Kotlin/JS: `HContext.game`/`plan`/`placedHero` and the flow's action availability are plain backing fields, not
  delegates; `HybridAi.choose` and `HeuristicAi.choose` run in `withoutStackTraces` (JS: `Error.stackTraceLimit = 0`
  for the call, restored after; JVM: nothing, its signals never had traces).

**Checks.** The decision set (211 card plays, `DecisionSetTest`, now printing each search's work and a digest of
every candidate's exact score bits) run on the baseline and on this branch side by side, three threads each:
**211/211 answers, score digests and work counts identical.** `GameParityTest` (7 tests) passes on the JVM and on
Node with the pinned transcripts unchanged (shipped strength 10 `b3bfc3bb…`). Gate: `:core:game:allTests` (JVM 160
suites / 2 189 tests, JS 15), `:core:domain:allTests` (423 + 45), `:web:engine:jsTest` (8), `:app:compileDebugKotlin`,
detekt and ktlint of both modules: all pass.

| | baseline (ede2c722) | this branch | |
|---|---|---|---|
| Decision set, JVM, 211 card plays, 3 + 3 threads at once: mean ms | 2 665 | 1 703 | **1.56×** (median per position 1.46×) |
| Shipped-s10 parity game on Node (`theShippedStrengthTenChoosesAlike`) | 258 s | 49.5 s | **5.2×** |
| Old-search parity game on Node (`theSearchingLevelsChooseAlike`) | 47 s | 35 s | 1.3× |
| `GameParityTest` (7 tests) on the JVM, quiet machine, twice each | 36.9 / 36.1 s | 22.8 / 23.0 s | 1.6× |

The decision-set times are both runs sharing the machine (load ≈ 6 of 8 cores), so absolute ms are higher than a
quiet run; the ratio is what counts. The step order of the batches: score-once sort + `HContext` memos + zone table
1.31×; engine forks and trusted re-runs 1.49×; `placement` memo and hand counts 1.54×, final 1.56× (JVM); the two
Kotlin/JS fixes 78 s → 49.5 s on Node (JVM unchanged; 53.5 s in a later run). In the gate's run the same game took 7.6 s on the JVM, so Node is now
about 6.5× slower than the JVM on it, where Track A measured about 11×.

**What it means in play.** Without a budget (tournaments, `forStrength`) the answers are the same, only sooner. With
the app's 2 s budget (`forApp`) fewer card plays are cut short, most of all in the browser, so the app's strength 10
can only get closer to the unbudgeted one measured above.

**Left.** The profile is now flat on the JVM: the book's static scoring of every inner question of the rest-of-turn
look (~40%) and the engine's step re-runs (~40%: re-running a step from its start for every answer is the engine's
design). Within one search the book is almost never asked the same question on the same position twice (3%
measured), so an answer cache would not pay. On Node what is left is mostly string-keyed hash lookups (Kotlin/JS
does not cache a string's hash) and garbage collection.

## Strategy round - Track V (win condition and the team plan, 2026-11-27)

Branch `t3/strategy-plan` from e6174517. The owner: "What is your squad's strengths and what are you playing for; you
will always need to play for economy and get stronger, but your upgrades and mid to late game strategy should be
aligned with your teammates." New file `TeamStrategy.kt`, rules G140–G148 in GENERAL.md §4a.

**What it is.** At every decision, from public facts only (each hero's whole catalog deck, levels, items, Life, lanes,
waves), each team gets: a composition (kill, wave clear, durability, mobility, set-up per hero, from `HCard` numbers
and the hero file's `HeroStyle`); a plan, KILL / PUSH / SCALE, that moves with the Life race (`deathsToLose`), the
lanes (pushes to their Throne, last wave, minion margin) and the level gap; and a job per hero (finisher, stripper,
anchor, pusher, farmer). The enemy's plan is the same function of the same facts.

**Where it acts** (knobs; master `plan`, default on):

| Knob | From | What | Where |
|---|---|---|---|
| `plan.upgrade` | 5 | the item and the kept card by job and the team's gaps (G145–G147); G105's initiative ties from 7 | `CardRules` (Track V's file) |
| `plan.econ` | 5 | the Tier III step at level 5, a due level as certain, the Pity coin (G148) | one line in `HEval.scoreRed` |
| `plan.style` | 7 | aggression, lane weight, keep-defender by job and Life race, pulled toward one team target (G142, G143) | one hook in `HContext.style` |
| `plan.race` | 7 | a death's worth ×(1 ± 0.15) by the Life race, from the deciding team's plan (G144) | one hook in `HContext.killWorth` |
| `plan.size` | 2 | style and race only for teams of at most this many heroes | |

Existing functions changed (shared `HContext`): `style()` (the hook) and `killWorthOf()` (the last line multiplies
by `TeamStrategy.deathScale`); a `teamViews` field in a `// Track V` block. `killWorth` already counted the death cost
at levels 4 and 7 (tier), so no further level-4/7 risk term was added.

**Knob-off proof.** `PlanDigestTest` (opt-in) plays fixed self-play games and hashes every answer. Baseline
(e6174517) and this branch with `plan=0` give the same digest: h9, 9 games (line-ups 2600–2605 2v2, 2600–2602 3v3),
3 578 answers, `61faf79d…`; h5, 2 games, 799 answers, `0cb1aa87…`. The hybrid search's decision set (60 card plays,
`off:plan=0` against `new:`) differs in 2 of 60 answers with the plan on.

**Screen** (`h9:h9+plan=0`, mirrored, the code before `plan.size`):

| Pair | Line-ups | Games | Result | Wilson 95% | Defeats/game on / off |
|---|---|---|---|---|---|
| h9 2v2 | 2600–2663 | 128 | 72–56, 56.3% | 47.6–64.5% | 1.06 / 1.05 |
| h9 3v3 | 2700–2731 | 64 | 22–42, 34.4% | 23.9–46.6% | 2.31 / 1.66 |
| h7 2v2 | 2800–2831 | 64 | 32–32, 50.0% | 38.1–61.9% | 0.86 / 1.08 |

h9 2v2 wins by the last push 39 against 29 and the Throne 16 against 9. The 3v3 result is a significant loss, so
the shipped default gates `plan.style` and `plan.race` to teams of two (`plan.size = 2`). In 2v2 that changes
nothing, so the 2v2 rows above still measure the shipped code. In 3v3 the shipped plan is upgrades and economy only,
and that is **not measured**.

**Why 3v3 lost** (Track M's `StrategyAuditTest` on the screen's own 3v3 games, 2700+32, plan on against off, per game):
kills 1.66 against 2.31, deaths 2.31 against 1.66, empty-hand deaths 1.00 against 0.63, pokes 5.25 against 4.55,
Holds 3.20 against 2.69. The 3v3 plan side fought worse, not more. A replay of the decision set's stored games
(`PlanReplayTest`, no new games) shows where the plan sits in 3v3: SCALE on 52% of card plays (everyone but the anchor
farms, aggression lower), and "hunt" on 19%, almost always when the enemy's heroes are the higher level (their death
costs 2 Life, ours 1), which tells the weaker side to hunt. Both are candidates; this round's budget did not allow
separating them. The designer's line that kills get easier in 3v3 because a third hero farms while two commit
(GENERAL §1) suggests 3v3 wants a finisher and a stripper in every plan, not only in KILL.

**2v2 behaviour** (the same audit on 2600+32): minions taken 7.55 against 7.02, minion coins 15.75 against 14.47,
pushes suffered 1.11 against 1.27, second pushes in a row 0.11 against 0.20, empty-hand deaths 0.44 against 0.55, heroes
already in the new zone after a push we won 0.55 against 0.42 a game.

**Upgrades and levels** (self-play digest games, 9 each, plan on / off): items tucked Defense 59% / 63%, Initiative
28% / 19%, Attack 9% / 14%; colours upgraded red 53 / 54, blue 38 / 34, green 21 / 25. Mean level at the start of
rounds 2–7: on 1.77, 2.68, 3.35, 4.03, 4.54, 5.03; off 1.77, 2.69, 3.28, 3.95, 4.45, 4.95. A level-up scan of every
hero (two partners, an early and a kill position) gave job-consistent keeps: finishers keep the higher attack and
take Initiative, anchors keep the higher defense and take Defense, farmers keep radius or range. Track M found h9
tucks Defense in 65% of items against 31% for the shipped strength 10. The plan moves that only to 59%, because the
base item values (G108: 0.7 a defending card) favour Defense. That is a hand-off: the base values, not the plan.

**Bench** (`AiPositionBenchTest`, h9 / h9+plan=0): probe 54.9% (128/233) / 54.1% (126/233), wp regret 0.0162 both;
puzzles 75.6% both; rounds 95.5% (378/396) both. Flat.

**Cost.** Card play in the screen: h9 13.86 against 12.87 ms (+8%), 3v3 15.34 against 15.04 (+2%), h7 6.21 against
5.82 (+7%). Strength 10's decision set: 2 637 against 2 634 ms a card play, the same work counts. Kits are cached by
hero and style (a catalog fact); the plan is derived once per context and team.

**Tests.** `TeamStrategyTest`: every hero's kit; the opening is SCALE with the most durable hero anchoring; one death
from their defeat is KILL (finisher and stripper, a death of theirs ×1.15, off by knob, master and below 7); one push
from their Throne is PUSH (anchor and pusher, lane weight up); the last push when we can afford a death (×0.85); we
cannot afford one (keep a defender, aggression down); style is the hero file's with the plan off or below 7; 3v3 keeps
style and race off unless `plan.size=3`; determinism; the economy numbers; a finisher (Rowenna) and an anchor (Bain)
keep a different card with the plan than without. Opt-in: `PlanDigestTest` (digest, level curve, upgrade picks),
`PlanReplayTest` (where the plan acts, on stored games).

**Game budget.** Screen 256; digests 31 (h5 2 + 2, h9 18 on the branch and 9 on the baseline); the audit replayed 128
of the screen's own games (the same seeds, so the same games, with counters).
