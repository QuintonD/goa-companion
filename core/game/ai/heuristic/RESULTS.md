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
