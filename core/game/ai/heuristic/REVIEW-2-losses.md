# Review 2: where and why the heuristic player loses (measured)

Empirical review of h10 (ADR-085) for the owner's goal of 2026-11-21, "the strongest AI possible regardless of
the device". Three instruments, all on development line-ups 1000+ (held-out 300–395 / 600–695 untouched):

1. **Regret probe** (`HeuristicRegretTest`, new): an oracle search values every h10 answer and its own best.
2. **Loss diagnostics** (`HeuristicDiagTest`, now parallel, multi-spec, 2v2 and 3v3): how heroes and games are lost.
3. **Category swaps** (`HybridAi`, new, `h10/<categories>/s10` in `HeuristicTournamentTest`): h10 with one category
   of questions answered by the old strength-10 search, against s8. This turns "the oracle disagrees" into games won.

**In short.** h10 loses to s10 on the minion race (1.09 minions a round against 1.36; last push and Throne are 10 of
its 13 losses) and loses heroes with an empty hand (37 of 78 defeats). Both come from where its look stops: a move is
scored after our own action, a card play after our card alone, turn 3 without turn 4. Given the search's answers for
one category at a time, h10 gains most from **own-move squares (+11.7 points against s8)** and **card plays (+8.6)**,
nothing from the action choice, and **+6.3 from switching off one weight (G4, no early Red)**. The fixes are
search built on the book: play the turn out after the reveal with the book's policy (F1), sample the reveal at the
card play (F2), check turn 4's escape (F3). Strengths 9–10 add nothing because their layers are weights inside the
same one-action look.

## 1. Method

**The oracle.** `AiPlayers.profile(10)` (determinized search, learned net at the leaves, enemy hands known as h10
knows them, ADR-062) with 3× its card-play samples (48), no wall clock, and two changes that make it a better judge
than s10 itself: enemies still to play this turn draw their card from the opponent model in each sample (`rm = 1`;
without it they play their static best, one deterministic reply), and the kill extension (`killExt = 1`: a playout
runs on through a next-turn kill either team can make). It analyses the same seat's determinized view
(`Searcher.determinize`, no hidden information), with h10's answer forced into its candidates (`mustInclude`).
h10's play is unchanged: the probe only observes (`HeuristicAi.scored`, a diagnostics hook, records h10's own scores).

**Per question** (4 863 h10 questions with more than one option, 32 games): DecisionKind, hero, round/turn, h10's
answer and scores, the oracle's best, the paired gap and its standard error, a **cross-fitted** gap (best picked on
the even samples, measured on the odd ones and back: no winner's curse), the same in **win chance** (each sample's
value through `WinChance`, so a lopsided or finished position counts little), and a state summary (lanes, Life,
hands, positions). A **second opinion** values every question too: the same search scored by the hand evaluation
(`EvalSource.HAND`, `AiPlayers.TUNED`) instead of the net.

**Robust regret** (the unit of the tables): a question counts only when both oracles say h10's answer is worse by
more than 1 point of win chance; its size is the smaller of the two cross-fitted gaps. Reported in **pp of win
chance per game**, and in **coins** at an even position (`WinChance` slope 0.0294: 1 coin ≈ 0.735 pp).

**Calibration.** In the 16 h10:s8 games the oracle also judged s8's own questions (`AI_H_REGRET_BOTH=1`), and at
every h10 question it valued what h8 would have answered (`AI_H_REGRET_ALT=h8`).

**Runs** (`AI_THREADS=3`, oldboi): h10:s10 line-ups 1000–1007 mirrored (16 games, h10 side probed, 16 min);
h10:s8 1000–1007 (16 games, both sides probed, 25 min). Oracle time per question: 640 ms mean (card play 1.9 s,
action 73 ms, space 235 ms). About 150 h10 questions a game.

```
AI_H_REGRET=s10 AI_H_REGRET_ALT=h8 AI_H_GAMES=8 AI_H_FIRST=1000 AI_THREADS=3 AI_HEAP=1600m \
  goa-build :core:game:jvmTest --rerun --no-daemon --max-workers=1 --tests '*HeuristicRegretTest*'
# replay one question with the oracle's full analysis:
AI_H_REGRET_ONLY=1005:B:s10:248,1000:R:s10:74 … same command
```

`python3 -I tools/ai/regret_report.py <files>.jsonl --examples 20` prints the tables below from the JSON lines
(the field names are in the test's KDoc).

## 2. Noise: what the oracle can and cannot tell

| Check | Result | Reading |
|---|---|---|
| Same oracle, another seed (290 card plays) | same best 97%, cf correlation 0.99, per-question sd 0.40 pp | Sampling noise is negligible. After the reveal every hand is known to strength 10, so 4 samples of a post-reveal question are identical (paired SE median 0.00). |
| Hand-evaluation oracle (all 4 863) | same best 62%, correlation 0.55; of 1 066 "significant" net verdicts, 437 confirmed | **Model noise dominates.** Hence the robust measure. |
| s8's own questions judged by the same oracle | robust 38 pp/game against h10's 81 | Both oracles are the search family s8 belongs to; part of h10's excess is taste, not error (see HOLD below). |
| Per-game regret against the result | won games 87 pp, lost games 77 pp (robust, 12 / 20 games) | **Summed regret does not separate won from lost games.** The table ranks where the book disagrees with search; win-rate impact comes from §5 (swaps) and §4 (how games are lost), not from adding regrets. |
| h10's answer = net oracle's best | 54% (hand oracle 60%); actions 72%, card plays 50%, spaces 31% | Spaces are often near-ties for both players. |
| Known oracle artifacts | Repeat-choice loops ("choose up to three times", Nebkher's Diabolical Laughter, Wuk's Mystic Saplings): the oracle prices every use at −20 to −40 coins against Skip, both evaluators alike | A playout-policy artifact of the search, not an h10 error. Discounted below. |

The two opponent sets agree on the ranking (s10 / s8 games, robust pp/game): acts-where-Hold-wins 11.1 / 13.1,
own move T1–2 8.2 / 8.5, card T3 other 7.9 / 7.8, own move T3–4 5.7 / 8.7, card T3 Red spent 5.3 / 9.2.

## 3. The top 15 sources (robust regret, 32 games)

"s8" is the oracle's robust regret of s8's own questions in the same kind (16 games): close to 0 means the search
family never makes that choice; similar to h10 means the category is hard for everyone (or the oracle is noisy there).

| # | Source (concept) | Questions/game | Regret pp/game | ≈ coins/game | s8 | Proposed fix (section 6) |
|---|---|---|---|---|---|---|
| 1 | Action: h10 acts or moves where holding wins | 5.4 | 12.1 | 16.5 | 1.7 | none alone (swap −0.8 pts, §5); F1 scores Hold as a square |
| 2 | Own move on turns 1–2: the end square | 13.7 | 8.4 | 11.4 | 1.5 | F1 (move swap +11.7 pts) |
| 3 | Card play turn 3 (other than a Red spent) | 9.4 | 7.9 | 10.7 | 9.3 | F2 sampled reveal (card swap +8.6 pts) |
| 4 | Card play turns 1–2 (other) | 20.7 | 7.5 | 10.2 | 8.2 | F2 |
| 5 | Card play turn 3: Red spent where both oracles keep it | 1.7 | 7.3 | 9.9 | 4.1 | F3 turn-4 escape, F2 |
| 6 | Own move on turns 3–4 or with one card: the end square | 9.9 | 7.2 | 9.9 | 1.6 | F1 + F3 |
| 7 | Action: primary / movement / Fast Travel swapped | 38.9 | 7.1 | 9.7 | 5.5 | F1 |
| 8 | Opaque choices (bullet, token, number) | 6.7 | 4.9 (≈1.2 after artifacts) | 6.6 | 0.3 | F5 (mostly oracle artifact) |
| 9 | Card play turns 1–2: Red withheld | 3.1 | 3.9 | 5.3 | 2.1 | F4 G4 off (+6.3 pts measured) |
| 10 | Card play turn 4 (other) | 7.9 | 3.0 | 4.1 | 0.3 | F1 (round-end battle), F2 |
| 11 | Attack target (hero hit where a minion was the take) | 2.0 | 2.8 | 3.8 | 0.5 | F6 a poke is not a minion |
| 12 | Other spaces (tokens, pushes, placements) | 7.4 | 1.9 | 2.6 | 0.4 | F1 |
| 13 | Defend / decline | 2.1 | 1.6 | 2.2 | 0.2 | F3 |
| 14 | Own move: moved where staying put wins | 1.1 | 1.35 | 1.8 | 0.0 | F1 |
| 15 | Upgrade choice | 12.1 | 1.3 | 1.8 | 1.3 | none (search does the same) |

Below these: discards and retrieves 1.3, minion removal 0.45, respawn 0.2, act first 0.03 pp/game. Defending is
not where h10 loses (DefendRules: 82% agreement with the oracle, s8 the same).

By game phase: turn 3 carries 44% of the robust regret (turn 2 25%, turn 1 17%, turn 4 15%), and Life 4–4 (the
first rounds) 45%. By lane: an even or one-off lane (margin −1 to +1) holds 61%. By hero (share of the total, each
seated in 2–4 of the 32 games): Nebkher, Ignatia, Emmitt, Misa 6% each, Snorri, Whisper, Garrus 5%; least Widget,
Tali, Gydion, Dodger, Swift, Trinkets.

### Examples (line-up : h10 side : opponent : decision id; replay with `AI_H_REGRET_ONLY`)

1. **Acts where Hold wins.** `1005:B:s10:248` Garrus R5T3, one card left (Red), Life 2: moves with Testudo! toward
   Dodger (look 12.6 against Hold −26.7); both oracles hold (net −11 against −38, hand +86 coins). `1000:R:s10:74`
   Takahide R2T2 uses his Silver (swap the Gold): look +11.3 against Hold −0.3; oracles Hold by 22 / 26 coins.
   `1007:R:s10:51` Razzle R1T4 with one body's card left moves (22.8 vs 11.6); oracles hold by 25 / 30. A third of
   this regret is Silver primaries (Cutter, Nebkher, Emmitt, Takahide, Snorri, Tigerclaw, Misa); in a quarter of
   these questions h10's look scored the two options within 0.5 (it could not tell them apart).
2. **Move T1–2.** `1003:B:s10:219` Snorri R5T1 Fast Travels to 5,−4 (tied with 5,−3 at 13.8), oracles 7,−8 / 6,−7
   by 42 / 48 coins. `1005:B:s8:311` Wasp R6T1 −3,0 vs −2,0. `1003:B:s8:323` Whisper R7T1 5,−4 vs 6,−7.
3. **Card T3 other.** `1000:B:s10:77` Brogan R2T3 Gold (30.5) over Silver (−1.2); oracles Silver by 29 / 46.
   `1004:B:s8:299` Ignatia R6T3 Blue over Silver. `1005:B:s10:245` Garrus R5T3 Blue (55.3) over Red (37.1) at
   Life 2: the oracles lose the game in most samples after the Blue (−537); h10 still won that game.
4. **Card T1–2 other.** `1003:B:s8:168` Snorri R4T2 Gold over Blue; `1004:B:s8:287` Ignatia R6T2 Red over Blue;
   `1002:R:s8:338` Emmitt R7T2 Gold over Green.
5. **Red spent on T3.** `1004:B:s10:279` Ignatia R6T3: Red 101.0, Green −49.7 in h10's look; oracles Green by 30 /
   53 coins. `1004:B:s10:176` Ignatia R4T3 Red 65.1 over Blue 22.6 (oracles Blue). `1004:B:s8:298` Arien R6T3 Red
   over Silver (28.8 pp). Diagnostics game 1000 R: Takahide spends Red on T3 into a defended Brogan, keeps only his
   Silver (initiative 6) and dies on T4 to Brogan's Red (initiative 8) with no card to defend.
6. **Move T3–4 / one card.** `1005:B:s10:249` Garrus R5T3 to 0,−2 beside Dodger (look 4.6) instead of 0,0
   (−14.6), one card left; oracles 0,0 by 40 / 91 coins. `1003:R:s8:187` Misa R4T3; `1005:B:s10:138` Wasp R3T3
   −4,2 (82.6) against −7,3 (81.8): h10 indifferent by 0.8, the oracles by 37 / 42.
7. **Action other.** `1005:B:s10:188` Garrus R4T3 moves (12.9) instead of the primary (−7.0); oracles primary by 38
   / 57. `1002:R:s8:214` Emmitt R4T4 Gold primary vs movement; `1004:B:s8:84` Ignatia R2T3 movement vs primary.
8. **Opaque.** `1006:R:s8:331` Wuk moves a Tree (Mystic Saplings), `1006:R:s10:168` Nebkher's Laughter: the artifact
   above (every option −20 to −40 against Stop). Real ones are rare: `1003:R:s10:138` Misa holds instead of her
   Silver (oracles primary by 48).
9. **Red withheld T1–2.** `1003:R:s10:154` Misa R4T1 Silver over Red (oracles Red); `1004:R:s8:268` Min R6T2 Gold
   over Red; `1003:B:s8:169` Whisper R4T2 Gold over Red (net) / Silver (hand).
10. **Card T4.** `1004:B:s10:236` Arien R5T4 Red (−13.3) over Gold (−35.3); oracles Gold by 38. `1007:R:s8:216`
    Razzle R4T4 Silver over Red; `1002:B:s10:201` Brynn Gold over Silver.
11. **Target.** `1002:R:s8:334` Emmitt R7T1 hits Gydion (who defends) instead of minion m49; `1004:R:s10:89` Min hits
    Ignatia instead of m6; `1007:B:s8:76` Bain hits a Razzle body instead of m3. Of 63 target questions h10 hit a
    hero 35% of the time; 10 of its 22 target disagreements are hero-over-minion.
12. **Other spaces.** `1000:B:s10:244` Tali places where Skip wins; `1001:B:s8:145` Widget's Turret; `1002:R:s8:260`.
13. **Defend.** `1003:R:s8:341` Misa defends with Green, oracles with Silver; `1002:R:s8:363` Emmitt with his Silver.
14. **Stayed put.** `1007:R:s10:52` Razzle R1T4 3,−8 vs staying; `1005:B:s10:202` Garrus R4T4 with no card left.
15. **Upgrade.** `1007:B:s8:174` Bain keeps Green-2a over Green-2b; `1001:B:s10:114` Widget.

## 4. How games are lost (diagnostics)

`AI_H_DIAG=h10:s10,h10:s8,h10:s10@3v3,h10:s8@3v3 AI_H_GAMES=8 AI_H_FIRST=1000 AI_THREADS=3` (64 games; the 2v2 games
are the probe's games, same seeds).

| Pair | h10 won | Minions taken per round, h10 / opp | Hero kills per game, h10 / opp | h10 defeats with no card | Endings of h10's losses |
|---|---|---|---|---|---|
| 2v2 vs s10 | 3/16 | 1.09 / 1.36 | 0.81 / 0.94 | 7 of 15 | last push 7, Throne 3, Life 3 |
| 2v2 vs s8 | 9/16 | 1.28 / 1.22 | 0.94 / 0.94 | 7 of 15 | last push 4, Life 2, Throne 1 |
| 3v3 vs s10 | 2/16 | 1.38 / 1.53 | 1.62 / 1.69 | 12 of 27 | last push 6, Throne 5, Life 3 |
| 3v3 vs s8 | 7/16 | 1.38 / 1.48 | 1.56 / 1.31 | 11 of 21 | last push 6, Life 2, Throne 1 |

Actions (64 games): h10 primary 48%, movement 41%, Fast Travel 7%, Hold 4%; its opponents 43%, 31%, 9%, 16% (s8 holds
with 35% of its Silvers and 19% of its Greens).

**Empty-hand deaths** (37 of 78 h10 defeats over the 64 games; the rest had a card that could not hold). The
sequence, read off the turn before each one: the hero **attacked on turn 3** with its Red or Gold (9 of 14 in 2v2),
and **defended** on turn 3 as well (11 of 14), so it entered turn 4 holding one card; that card was most often the
**Silver** (6 of 14), which has no movement, or a card slower than the killer's (killer acted first in 9 of 14 in
2v2, 14 of 23 in 3v3). With nothing in hand during turn 4, any attack that reaches is the kill. The card-play look
sees turn 3's action and an averaged threat map (each enemy card at 0.65/colours, next turn at 0.22, the empty-next
cap 0.6), not the turn-4 position: whether the last card can step out of every known enemy attack before it lands.
The oracle sees it only partly (its playout ends at turn 3's end; the kill extension fires only for team kills).

**Throne losses** (3 of 13 in 2v2 vs s10, 5 of 14 in 3v3). Each came after h10 was pushed to its last zone and then
lost the minion count there: game 1005 Red went R3T1 6–5, R3T4 4–4, R4T1 2–4, R4T4 1–3, Throne. The book's
`TurnPlan` never derives **HOLD_LINE** (nor WIN, CLEAR_MINION, BLOCK, ROTATE, SPAWN_CAMP): the intents exist, but
`TurnPlan.derive` has no branch for them, so in our last zone h10 farms, chases and moves as anywhere else. The lane
value is right (match point weights the lane 100 × 1.4), but the plan that turns it into "stay in the zone, take
their minions, keep the heavy's defense" is missing. It does not take the Throne for the same reason in reverse:
no PUSH-to-win intent; the turn-4 look does not resolve the end-of-round minion battle (it scores HEval's sigmoid).

**Last-push losses** (7 of 13 vs s10). The lane falls by minion arithmetic, not by a single blunder: h10 takes 0.27
fewer minions a round than s10 (1.09 against 1.36) over 6–8 rounds, about 2 minions a game, which is the margin of
a last-wave battle (a ≥ 2b pushes). Contributors measured above: actions spent moving (h10 moves on 41% of actions and
holds on 4%, its opponents 31% and 16%; the oracles prefer staying in the zone beside our minions), hero strikes where a minion was the take
(item 11), and on turn 4 a card scored without the round-end battle it decides (item 10). The final minion is
usually taken mid-turn by the enemy (`! widget -> m48 … P lane-1 … LAST_PUSH`) when our last one stands alone.

**Why strengths 9–10 add nothing over 8.** At the same question h8 answers differently 29 times a game (movement
10, card play 7, action 5). Valued by the oracles, those differences net to about nothing: the net oracle says h10's
answers are better by 8.6 pp/game summed, the hand oracle says h8's are better by 2.2 (movement: h10 better by 2.8 /
worse by 1.7; actions +0.1 / −1.5; card play h10 better by 4.2 / 0.5); of 882 valued differences both oracles
favour h10 in 94 and h8 in 65, the rest split or are within 1 pp.
The layers 9–10 add are small weights on top of a one-action look (G81 sacrifice, G19, the SETUP intent, lane timing,
match-point weight, `avoidPush` −15) and `knowHands`; knowing the hands enters only through the threat map's colour
shares (`HContext.threatInfo`: 0.65 × share / colours this turn), i.e. an average over the hand, not the enemy's best
reply. The decisive errors above (the turn after the reveal, turn 4's last card) are the same at 8, 9 and 10.

## 5. Category swaps: what each fix could be worth in games

h10 with one category of questions answered by the old strength-10 search (`HybridAi`), against s8, on the same
128 mirrored games (line-ups 1000–1063, `AI_THREADS=3`, 2 h 20 min in all). The swap is a ceiling for a fix that
answers that category as well as the search does; it is not additive across categories.

```
AI_H_TOURNAMENT="h10:s8,h10+earlyRed=0:s8,h10/action/s10:s8,h10/move/s10:s8,
  h10/opaque.target.defend.discard.space.unit.upgrade.minion.respawn.first/s10:s8,h10/card/s10:s8"
AI_H_GAMES=64 AI_H_FIRST=1000 AI_THREADS=3
```

| h10 variant against s8 | Won | Rate | Wilson 95% | Pairs (a both / split / s8 both) | Δ vs h10 | h10-side answer mean / p95 |
|---|---|---|---|---|---|---|
| h10 (baseline) | 68–60 | 53.1% | 44.5–61.6 | 16 / 36 / 12 | — | 3.7 / 13 ms |
| **own moves by s10** (`move`) | 83–45 | **64.8%** | 56.2–72.6 | 22 / 39 / 3 | **+11.7** | 64 / 458 ms |
| card plays by s10 (`card`) | 79–49 | 61.7% | 53.1–69.7 | 20 / 39 / 5 | +8.6 | 175 / 879 ms |
| `h10+earlyRed=0` (G4 off, no search) | 76–52 | 59.4% | 50.7–67.5 | 21 / 34 / 9 | +6.3 | 3.1 / 10 ms |
| the small categories by s10 (targets, defend, opaque, tokens, upgrades, removal, respawn…) | 76–52 | 59.4% | 50.7–67.5 | 18 / 40 / 6 | +6.3 | 40 / 209 ms |
| actions by s10 (`action`) | 67–61 | 52.3% | 43.7–60.8 | 17 / 33 / 14 | −0.8 | 37 / 168 ms |

(Pairs: mirrored pairs h10 won both / split / s8 won both; the log prints them in the other order, by h10's points.)

What this changes in the ranking of §3:

- **Own moves are worth the most games** (+12), though they rank second and sixth by oracle regret. The search
  chooses the square after playing out the rest of the turn; h10 scores the square after its own action. This is
  F1, and the swap says it is where h10 loses to s10's minion economy and to its empty-hand deaths.
- **Card plays are second** (+9): F2 and F3.
- **The action choice alone is worth nothing** (−0.8), although it is the oracle's largest disagreement (item 1):
  holding where the search holds, with h10 still choosing the squares and cards, does not win games. That
  disagreement is the search family's taste (s8 holds 16% of its actions) or only pays together with the move it
  enables. Item 1 drops out of the fixes on its own; F1 scores Hold as one of the squares instead.
- **G4 (no early Red into a hero) costs about 6 points** by itself: the cheapest fix in this review, one weight.
  It needs a held-out confirmation (128 games: ±8.6 points).
- The small categories together are worth up to +6 (F5–F7).

## 6. Proposed fixes

Ordered by expected gain. "Search on the heuristics" is allowed (owner, 2026-11-21): F1 and F2 are exactly that —
the book's own policy plays the turn, HEval scores it.

**F1. Play out the turn after the reveal (items 1, 2, 6, 7, 10, 12, 14).** `CardSim.after` / `TurnLook` stop at the
end of our own action and score `HEval` with the threat map. After the reveal every played card is public and, at
10, every hand is known; the rest of the turn is nearly deterministic. Change `TurnLook.stop` (CardSim.kt) to run on
to the end of the turn — every remaining hero acts with its own book (`policy` with that hero's playbook), enemy
hidden answers still priced from the pool below 10 (`Lookahead.hiddenAnswer`) — and score at the turn's end; on
turn 4 run through the end-of-round minion battle and push (exact last-push arithmetic instead of HEval's sigmoid).
Look at every own-move square (`SPACE_CAP` 6 → all), and keep Hold/Don't move as a scored option rather than the
floor of G39/G40. Cost: tens of engine answers per option, well inside "any device". Ceiling measured: the move swap,
**+11.7 points** against s8; a book playout rather than the search's should keep most of it: estimate **+6 to +10**.
Do moves first; the action swap says the action choice alone gains nothing.

**F2. Sample the reveal at the card play (items 3, 4, 5, 9, 10).** CardSim resolves our card alone against an averaged
threat map. Instead: K = 16 samples, each enemy's card drawn from its known hand (10) or possible pool (≤ 9) with
weights from that enemy's book (softmax of `General.score` for PLAY_CARD from the enemy seat; the regret probe shows
search with `rm` sampling is ~stable), allies' cards as now (G14), then F1's playout to the end of the turn, mean
HEval (minus a small risk term). This replaces `CardSim.value`'s single action and the 0.65/colours weights.
Ceiling measured: the card swap, **+8.6 points**; estimate **+4 to +7** on top of F1 (they overlap: both are
"play the turn out"), more against s10, whose sampled card plays are what beats h10 today.

**F3. The turn-4 escape check (items 5, 6, 13 and the empty-hand deaths).** When a card play or a turn-3 move leaves
one card for turn 4 (`HContext.threatChances`, the `emptyNext` branch, and `PlayRules.keepSpare`): ask whether that
last card can leave every known enemy attack's reach before it resolves (it has a movement and an initiative above
the attacker's, ties by the coin), and if not price the turn-4 defeat in full (`killWorth`), not capped at 0.6.
Corollary: do not keep the Silver (no movement) as the last card while an enemy who can reach us still holds an
attack; spend it on turn 2–3 (G12's −8 for a Silver with no target is what pushes it last). Measured knobs
`emptyCap`/`lethalEmpty` changed nothing because they scale a probability the look does not compute; this is a
different test. Expected: a third of the 0.41 empty-hand deaths a game (vs s10), about +2 to +3 points; part of it is already
inside the move and card swaps.

**F4. Colour timing (item 9).** G4 (`PlayRules.earlyRed`: −8 for a Red into a hero on turns 1–2) and `colourTempo`
(Red −1.2 / −0.4 on turns 1 / 2, Blue +0.8 on turn 4) hold Red back where both oracles play it: h10 plays Red 13 times
in 384 turn-1 plays, the net oracle's best is Red 37 times; on turn 4 h10 cashes Blue 55 times in 287, the oracle 28. Knob `earlyRed` exists:
`h10+earlyRed=0` won **59.4% against 53.1%** on the same 128 games (+6.3, §5). Confirm on held-out line-ups, then
drop G4 (or keep it only below strength 7, where it is a designed beginner habit); under F2 `colourTempo` should go
too: the sampled turn sees what a Red does.

**F5. Identity Silvers and opaque bullets (items 1, 8).** Mostly the oracle's repeat-choice artifact (Nebkher, Wuk).
What stays: Silver primaries the look scores level with Hold (Cutter, Snorri, Takahide's Gold swap, Emmitt's Glitch
swap where the enemy picks the token) — a tie goes to the first option, PRIMARY. Break exact ties toward Hold in
`HeuristicAi.pick` (look stage), and give Emmitt's `CHOOSE_TOKEN` from the enemy's seat its adversarial answer in his
playbook. Small: ≈ 1–2 pp/game; with F6 and F7 inside the +6.3 ceiling of the small-category swap.

**F6. A poke is not a minion (item 11).** `HContext.heroStrikeWorth` already reads the known hand at 10 (kill chance
0 when a defense holds), but a poke that only strips a card then collects G60's +3.5 / +6 (a teammate can reach) and
the two-turn +2 on top of the discard (2–7): a defended poke reaches 8–15 and beats a minion. Give those bonuses only
when the teammate's follow-up can actually finish with the cards it holds (its own `strikeFrom` on the stripped hand),
and compare against the minion's lane share (`minionWorth`) rather than its coins. Small directly (2.8 pp/game), but it
is one of the minion-race leaks of §4.

**F7. Derive HOLD_LINE and a push-to-win intent (Throne and last push).** `TurnPlan.derive`: when our side of the lane
is our last zone (`ctx.matchPoint` with the throne ours, or `stepsAgainst ≤ 1`) and the projected battle loses,
HOLD_LINE (stay in the zone, enemy minions first, no hero chase out of the zone, Hold is fine); when theirs is, PUSH
with "kill the minion that makes a ≥ 2b". Under F1 the round-end battle is resolved, so this becomes a plan, not a
weight. Expected: part of the 8 Throne losses in 32 s10 games.

**Why not more weights.** RESULTS §4 already tried exposure, lethal, empty-hand and spare weights at 768 games each:
none moved. The probe says why: the errors are where the look's horizon ends (our own action, the end of turn 3), not
in the weights inside it.
