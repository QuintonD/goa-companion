# Review 2: design of the heuristic player (2026-11-21)

**Scope.** This review covers the whole of `ai/heuristic/` (general book, HEval, CardSim, Lookahead, TurnPlan,
HeuristicProfile and the 32 playbooks). I read it against DESIGN.md, GENERAL.md, the hero research, STRATEGY.md,
AI.md "Known weaknesses" and the old `Evaluator` / `RoundPlanner`.

**The owner's goal (2026-11-21).** The strongest AI possible, whatever device runs it. Compute cost is no longer
a constraint. Search built on the heuristics is allowed.

**Method.** I read the code; I did not change any player code. One diagnostic tournament ran (§3.3). The per-hero
table in §5 comes from a sub-review of every playbook against its research `.md`. I spot-checked the findings that
drive the top 5.

Paths are relative to `core/game/src/main/kotlin/dev/quinton/goa/core/game/ai/heuristic/` unless they say otherwise.

## 0. Headline findings

1. **Rules in the static score stop counting once a look prices the decision.** At strength 5 and above, an option
   that is looked at is worth `look value + General.commitment + hero adjust` (`HeuristicAi.kt:123-125`, `:147`). The
   static `General.score` is used only to order options and as the finishing policy inside looks. Many GENERAL rules
   live only in `score`, so at strength 7 and above they are dead for the real choice:
   - G7/G9 initiative (`PlayRules.kt:36`);
   - all of DefendRules, including keep-cost and the G81 priced death, because every DEFEND is looked at
     (`HeuristicAi.kt:193`);
   - the plan target, G65 thin and G68 setup (`UnitRules.kt:18-21`);
   - the plan bonus (`ActionRules.kt:43`);
   - the G50 fork (`SpaceRules.kt:28`);
   - formation, `idealDistance`, `laneWeight` and the plan pull. These live only in `HContext.placement`, which
     HEval does not read (`HEval.kt:68-89`).

   This is a main reason strengths 9 and 10 add nothing (§3), and why `HeroStyle` and turn-plan changes measure flat.
2. **HEval was never tuned as a whole, and the exposure sweep did not touch it.**
   - The `fear` knob scales only static placement (`HContext.kt:294`). So "exposure ×1.4, ×0.7: 49.6%" in
     RESULTS.md §4 says nothing about the exposure h10 actually decides by (`HEval.kt:87`, `CardSim.kt:236-254`).
   - The one global retune, lane ×1.4, gained 5 points. Points 1.8 and 2.5 tied it. This means the other terms are
     mis-scaled against the lane, not that the lane wanted exactly 1.4 (§2).
3. **The decision horizon is one action.**
   - A card play resolves only our own action, plus a computer teammate that surely acts first (`CardSim.kt:33-68`).
   - Enemy replies are a static threat map whose card weights are uniform over colours (`HContext.kt:611-616`).
   - After the reveal every enemy card is public, but the look still stops at the end of our own action
     (`CardSim.kt:190-203`).

   The old search's single biggest step was exactly this horizon: "every choice played to the end of the turn",
   +120 Elo (AI.md, 7 vs 6). Search on the heuristic policy is the largest available gain (§4, items 1, 2 and 6).
4. **Hand management is greedy.**
   - After a card play, the hand term of HEval is the same for every card choice, except 0.12 × card rating (about
     0.3–0.7). See `HEval.kt:98-110`.
   - So which card stays for turn 4 rests on colour tempo (±1.6) and `keepSpare` (`PlayRules.kt:86-99`), plus whatever
     the exposure and potential terms happen to see.

   That fits the measured weakness: h10 heroes die with an empty hand about 1.5× as often as s8's.
5. **Strength 10's hand knowledge is almost unused.**
   - Knowing hands changes the enemy pool, but not the prediction of which card they will play: the weights stay
     uniform over colours.
   - Looks still stop at an enemy's defense and price it from the pool (`Lookahead.kt:131-139`), although at
     strength 10 that hand is known by design (ADR-062).

## 1. Concept coverage

Grades: **A** modelled well; **B** modelled, with gaps that matter; **C** crude or mostly inert at h10; **D** missing.

**Lane and minion-battle arithmetic, including the round-end battle: B.**
- *How the code models it:*
  - HEval lane is W from the §1.1 recursion. The next push's p is σ(1.1·(margin after the other side's push
    potential)), clamped at a ≥ 2b (`HEval.kt:118-142`).
  - The minion price is the change in W (`HFacts.kt:231-252`), and a push mid-round is valued in
    `pushedWin` (`HFacts.kt:258-263`).
- *Missing or wrong:*
  - When the round is over, the battle is still a sigmoid, not the exact a ≥ 2b push and carry-over of 2b − a
    (STRATEGY §2.4 says apply it). At 5 v 3 it says p = 0.90, but no push happens.
  - The next push after a push is a flat p = 0.5. It ignores the 6 v 5 spawn edge, which is patched separately by
    `badPush` (G13).
  - There is no L4 presence term.
  - `laneKillDelta` is not scaled by `LANE`, while HEval is (§2).

**Push timing (opening, let-push, Heavy timing): C.**
- *How:* `badPush` (`HFacts.kt:266-274`) costs −15/−6 in `UnitRules.commitment`. LET_PUSH intent leaves with +8
  (`ActionRules.kt:47-62`). `minionWorth` takes −4 at margin ≥ 2 (`HContext.kt:350`).
- *Missing:* "Push in the battle, not mid-round" is visible only through `badPush`. HEval cannot see that a mid-round
  push into a zone with heavier defender spawns is bad, because the post-push p is 0.5.

**Throne attack and defence: C.**
- *How:* only through the lane. Match point weighs 100 instead of 60 (strength 9+), and `goalZone` points at the
  current zone.
- *Missing:* HOLD_LINE is never derived (`TurnPlan.kt:88-122`: WIN, CLEAR_MINION, HOLD_LINE, ROTATE, BLOCK and
  SPAWN_CAMP are dead intents). Nothing models "defenders hold the pre-throne zone with more spawns". s10 beats h10
  mostly by the Throne and the last push (RESULTS §5: 76 + 48 of 162 wins).

**Coins, levels, upgrades: B−.**
- *How:* coins (×1.3 early), cumulative level cost + 3 per level, +1.5 when a level is due, Ultimate +8
  (`HEval.kt:69-73`). Upgrades use rating plus item counts plus `upgradeOrder` (`CardRules.kt:45-97`).
- *Missing:*
  - No Pity coin, no "level 5 = Tier III" bump, and no extra risk aversion at level 4 or 7 except through
    `killWorth`.
  - Upgrades are never looked at. Item thresholds (G105, G112) are left to the playbooks; few set them.

**Hand as health; the spare card: C.**
- *How:*
  - HEval hand: 2.5 per needed card, spare 3/2/1.5/0.8, −3 per missing card (`HEval.kt:98-110`).
  - `keepSpare` (`PlayRules.kt:86-89`).
  - The defense pool feeds exposure.
- *Missing:*
  - Card identity barely matters (see 0.4), and there is no plan across the round's turns.
  - At strength 5+, DEFEND is decided by `Lookahead.value` only. DefendRules' keep-cost (attack kept +2.2, Blue
    `keepDefender`) is dropped (`DefendRules.kt:72-79`; `HeuristicAi.kt:193`).

**Initiative reading and card counting: B.**
- *How:*
  - The pool: one weighted card per colour still in the round. Tier is gated by level, and memory covers cards seen
    since the last level-up (`HContext.kt:544-584`).
  - `actsBefore` and `exposureSplit` (`HContext.kt:630-773`) split the card play by who acts first.
  - The Tie Breaker is respected.
- *Missing:*
  - No opponent model: every unrevealed colour is equally likely to be played (0.65/colours, `HContext.kt:611-616`).
  - "Vulnerable hands" (Gold+Red spent, and the rest) are not read.
  - Enemy `opportunity` counts every possible attack candidate as a whole card, ignoring weights
    (`HContext.kt:820-824`), so an enemy with Tier II a/b candidates looks like two attack cards.

**Playing around enemy initiative: B.**
- *How:* in CardSim, an enemy whose card may act first meets us at our origin. `defeatBefore` discounts the strike,
  and `flee` 0.3 discounts a target that may leave (`CardSim.kt:228-254`).
- *Wrong:* `defeatBefore` scales only the pooled strike (`CardSim.kt:254`), not the lane value of a minion our action
  takes, nor the move. A slow card played from a square where we die first is over-valued.

**Focus fire and team coordination at card play: C.**
- *How:*
  - The G14 prelude: a computer teammate whose card surely acts before ours resolves first (`CardSim.kt:81-130`).
  - `heroStrikeWorth` +6/+3.5 when an ally can reach the target (`HContext.kt:384`).
  - `teamKill` and `crowd` (`PlayRules.kt:232-260`).
- *Missing or wrong:*
  - No postlude: a teammate who acts after us is invisible.
  - Worse, a teammate's known PLAYED card is diluted. `scoreCard` sets `revealed = false` (`CardSim.kt:238`), so
    `realPool` does not mark it `playingNow` (`HContext.kt:537-542`). In the enemy's exposure it is one colour in
    k at 0.65/k, not the known card at 0.72.
  - The constant +6/+3.5 then double-counts the part of the follow-up that the enemy's exposure does see.
  - No joint card choice for two computer teammates, and no team intent assignment (GENERAL §3, "Same-team
    commitments").

**Two-turn strip-then-kill: C.**
- *How:*
  - Mostly implicit: an enemy's exposure includes our next-turn threats, `killNext` capped at 0.28 (0.6 when they
    will be empty) (`HContext.kt:669-671`).
  - Explicit layers: `strip` (killWorth − 12, about 4 at levels 1–3 in a Quick 2v2, `PlayRules.kt:219-230`),
    `pressLead` (`:71-80`), +2 in `heroStrikeWorth` (`HContext.kt:385`), and G68 setup (dead, see 0.1).
- *Missing:* a real model of "they enter turn 4 with one card and we have an attack in reach". This is what round
  rollouts (item 6) give for free.

**Empty-hand protection: C.**
- *How:* exposure `emptyNext` (lethal next turn at 0.5, cap 0.6) and the two-hits rule (`HContext.kt:657-685`). The
  respawn decline G28.
- *Missing:* a turn-4 plan. RESULTS §4: the caps "barely change a choice"; the deaths come from a turn-3 strike that
  leaves the hand empty within reach.

**Respawn and spawn camping: C.**
- *How:* respawn at the best `placement`. Decline when every spawn's exposure is above half a kill
  (`SpaceRules.kt:54-62`).
- *Missing:* offensive camping (SPAWN_CAMP is never derived). The off-board penalty is a flat −8, not the actions
  needed to return (STRATEGY H7).

**Fast Travel and blocking it: D.**
- *How:* legal Fast Travel destinations come from the engine, with a tax on Red/Gold (`ActionRules.kt:47-80`).
- *Missing:*
  - HEval has no "actions to reach the zone" term. Presence is −0.55 per space whether or not Fast Travel is open
    (`HContext.kt:309-318`). So blocking an enemy's Fast Travel is worth exactly nothing.
  - G47–G49 and G53 are not implemented (`SpaceRules.kt:14-16`), although the rulebook calls blocking "one of the
    key strategies".

**Zone control: positioning, minion modifiers, ranged zones, forks: B−.**
- *How:*
  - Exposure uses minion modifiers.
  - `formation` gives +0.85 per friendly melee and −1.15 per enemy ranged within 2 (`HContext.kt:848-866`).
  - Fork G50 is +3 (`SpaceRules.kt:68-80`).
  - Opportunity and `laneThreat`.
- *Missing:* formation and fork are static only, so they are dead in looked moves at 7+ (0.1). HEval reads the
  modifiers only through exposure.

**Set-up heroes' pieces: C.**
- *How:* HEval `pieces` counts the best 4 per owner at 1.2, or 0.8 × turns left (`HEval.kt:172-194`). Hero
  `evalTerms` exist for 25/32 heroes.
- *Missing:* `evalTerms` run at about a third of the research values. Piece placement goes through
  `CHOOSE_SPACE`, which at 7+ looks at only the best 6 by the *static* order, so a bad static order hides the right
  site. See §5.

**Double lane, rotation: D.**
- G21 and G55 are not implemented. ROTATE is never derived. Out of scope for the Quick 2v2 measure, but real for 4v4.

## 2. Evaluation (HEval): terms, units, consistency

### 2.1 What each term is worth (Quick 2v2, Forgotten Island; coin-equivalents)

| Term | Where | Typical magnitude |
|---|---|---|
| Lane, `60·1.4·(2W−1)` | `HEval.kt:137-139` | Span ±84 (±140 at match point). One minion at an even lane ≈ **21**. |
| Minion margin, 1.5 a minion | `HEval.kt:139` | small |
| Push potential, 0.2 of a minion per attack card | `HEval.kt:145-158` | ≈ 4 per attack card |
| Opportunity × 2 | `HEval.kt:83`, `HContext.kt:321-332` | 3.2 per reaching attack card, cap 9.6 per hero |
| Lane threat, share 0.3 | `HEval.kt:85`, `HFacts.kt:300-326` | ≤ 0.3 × 15 × 2 ≈ 9 per hero |
| Life ladder | `HContext.kt:951-964` | 4 → 3 counters: 9; 3 → 2: 14; 2 → 1: 22; last: 60 |
| `killWorth` (life + level + assists + 5) | `HContext.kt:440-450` | 1st death of a level 1–3 hero ≈ 16; a level-4 hero ≈ 34; the last ≈ 77 |
| Exposure, pKill · killWorth + pHit · discard | `HEval.kt:87` | 0 to ≈ 30. Unscaled; no knob. |
| Hand | `HEval.kt:98-110` | 2.5 per needed card, spare 3/2/1.5/0.8, rating × 0.12 |
| Coins, levels | `HEval.kt:69-73` | 1 per coin (1.3 early), +3 per level |
| Presence, zone pull | `HContext.kt:309-318`, `HEval.kt:91-96` | +1 in the zone, −0.55 a space; zone pull +2.5 / −0.7 a space (strength 9+) |
| Off board | `HEval.kt:77-79` | −8 flat |
| Pieces, `evalTerms` | `HEval.kt:172-194` | ≈ 1–5 |
| Terminal | `HEval.kt:55` | ±1000 |

### 2.2 Consistency problems

1. **The lane is scaled, its derivatives are not.** `LANE = 1.4` multiplies `HEval.lane` only. `laneKillDelta`
   (`HFacts.kt:250`) still uses 60 / 100 unscaled. It feeds:
   - the static `minionWorth`, which orders the targets the look tries and picks them inside every finishing policy;
   - `laneThreat` inside HEval itself.

   So the static book and the lookahead disagree by 1.4 on what a minion is worth against a hero strike. Inside
   `CardSim.run` the finishing policy is that static book (`CardSim.kt:270-290`), so later choices in a look lean to
   heroes compared with the evaluation that judges them.
2. **The documents' units no longer match the code.**
   - GENERAL.md §2 says the lane is `60 × (W − 0.5)` (span 60) and one minion ≈ 15.
   - The code is `60 × (2W − 1) × 1.4` (span 168), and one minion ≈ 21.
   - Every hero `adjust` was written against "15 a minion", so their weight shrank by 1/1.4 without anyone choosing
     that.
   - Fix the text (DESIGN.md "Scoring", GENERAL §2, README "A score") or the code.
3. **The two ways to lose the game are priced differently.**
   - Losing the last Life counter costs about 77 (60 × 1.15 plus coins and tempo).
   - Losing the lane at match point swings 140 from even.

   Both are a lost game. Against the lane, early deaths are also cheap: the first death (≈ 16) is less than one
   minion of lane (21). Kills win 53% of real games (GENERAL §1). The `lastLife` knob exists but was never measured;
   `lastMult` and `lethal` were (flat). Calibrate the two races together (item 3).
4. **"Minion potential" is counted three times, by three separately tuned terms.**
   - Push potential reduces the projected enemy count (`HEval.kt:145-158`).
   - `opportunity` × 2 (`HEval.kt:83`).
   - `laneThreat` (`HEval.kt:85`).

   A hero with two attack cards near a minion holds about 24 of "potential" (≈ 8 + 6.4 + 9). Spending one card on
   the kill realises ≈ 25 (lane 21 + coins 2.6 + margin 1.5) but drops ≈ 12 of potential. So a kill nets only
   about half its face value. That is the "PP_SCALE 0.4: the full share made the player stop taking minions" effect
   (`HEval.kt:26-30`): one term was tuned down because two others already said the same thing.

   Also, `opportunity` ignores `playsLeft`: on turn 4 with one play left it still counts every attack card in hand.
   And for a hidden enemy it ignores the pool weights (`HContext.kt:820-824`).
5. **The focus-fire bonus is a constant on top of a look.** The `heroStrikeWorth` +6/+3.5 (`HContext.kt:384`) and
   +2 (`:385`) also go into the pooled strike that CardSim adds to HEval (`CardSim.kt:228-233`), while HEval already
   charges the enemy's exposure to our ally. The same follow-up is counted twice, with a fixed size, whether or not
   the ally acts after us or holds an attack.
6. **The team's exposure is over-counted.** An enemy plays one card a turn, but each of our heroes is charged that
   card's threat independently (`HContext.kt:657-685`, per victim). Two heroes standing together pay for one card
   twice. Turn search (item 1) removes this.
7. **Static and look scorers use different features** (0.1). `HContext.placement` has formation, style lane weight,
   ideal distance, plan pull and fork; HEval has none of them. Below strength 7 moves are chosen by placement; from 7
   by HEval. So style is a strength 5–6 feature, and that is why style ablations measure about 50% at 8.
8. **Saturating and inert terms.**
   - The lane p is clamped to 0.95 / 0.05.
   - pKill is capped at 0.92, and each enemy's chance at 0.70 now and 0.28 next turn. Against a known lethal card
     next turn, 0.28 is low; the cap mostly hides the turn-4 empty-hand death.
   - `opportunity` is capped at 4.8.
   - `counter` covers only Gydion's keys.
   - Off board is a flat −8 for any number of turns away.
   - Lookahead's finish builds its HContext without `seen` (card memory) and answers with `General.best`, without
     hero notes (`Lookahead.kt:98-107`). This is a small inconsistency with CardSim's policy, which uses both.

### 2.3 What the ×1.4 discovery implies

- 1.4, 1.8 and 2.5 tie. A 0.7 loses 11 points. So above a threshold the lane decides every conflict, and the other
  terms are only tie-breakers.
- The strength left in HEval is therefore in *how accurate* the other terms are (exposure, potential, hand), not in
  the lane weight.
- Two of the biggest of those terms (exposure and hand) have never been scaled in HEval.
- The fit the old player used (AI.md "Evaluation": a logistic fit of the parts to game outcomes) put exposure near
  0.04× the lane's weight, and minion opportunity at 2.35×, as predictors.
- The heuristic HEval copied the parts at full scale. A fit of the HEval parts against outcomes is cheap and is
  likely to find more than one 5-point step (item 3).

## 3. Strength layers: why 9 and 10 add nothing over 8

### 3.1 Code reading

What 9 and 10 add over 8 is `twoTurnPlans`, `laneTiming` and `knowHands` (10 only), with temperature 0 instead of
0.05 (`HeuristicProfile.kt:126-160`). Each of the three is mostly inert at the strengths that look ahead.

**`twoTurnPlans`.**
- These parts are dead at 5+ (0.1):
  - the SETUP target bonus G68 (`UnitRules.kt:63-66`);
  - the SETUP intent's plan pull (static placement);
  - the G81 priced death (`DefendRules.kt:50-57`).
- The surviving parts are small or rarely gated:
  - `strip` is killWorth − 12, which is ≤ 4 for a level 1–3 hero in Quick 2v2, and only when the target arrives at
    turn 4 with at most one card (`HFacts.kt:155-164`);
  - `pressLead` needs us at ≥ 3 deaths to lose and them at ≤ 1, which is rare in Quick with 4 Life;
  - the +2 in `heroStrikeWorth`.

**`laneTiming`.**
- Alive: the match-point weight 100/60 in HEval and `laneKillDelta`; `badPush` −15 against −6 in
  `UnitRules.commitment`; LET_PUSH +8.
- Small: the zone pull, +2.5 / −0.7 a space, against a lane of ±84.
- Dead in looks: the static half, `avoidPush` (`PlayRules.kt` `add(simulated = false)`) and the plan pull.

**`knowHands`.**
- It changes the pool to the real hand, which does affect `killChance`: it becomes 0 or 1 (`HContext.kt:795`).
- It does not change the prediction of the enemy's card: weights stay uniform over colours.
- Looks still stop at enemy defenses and price them from the pool, which at 10 is the real hand
  (`Lookahead.kt:131-139`). That is reasonable, but it is the same estimate a strength-8 player makes from the
  pool, sharpened only by whether a holding card exists.

### 3.2 What 9 and 10 should be

| Strength | Proposed content |
|---|---|
| 8 | Today's h10 without `knowHands` (it is already roughly that). |
| 9 | + turn search at the card play (item 1), the rest of the turn after the reveal (item 2), the opponent card model (item 7). |
| 10 | + `knowHands`, used: enemy defenses played from the known hand inside looks (item 8), card prediction from the known hand; round rollouts (item 6); joint teammate cards (item 12). Argmax, no G17 mixing: without mixing measured 52.2% head to head (RESULTS §4). |

Each step then adds a capability the step below lacks, as in the old ladder, where 7 / 8 / 9 / 10 were each worth
+60 to +190 Elo (AI.md "Measured strength").

### 3.3 Diagnostic run

128 mirrored games per pair, development line-ups 1400–1463 (`AI_H_FIRST=1400 AI_H_GAMES=64`):

| Pair | Win | Wilson | Pairs (h10 both / split / other both) | Defeats a game, with no card to defend | Answer, mean / p95 |
|---|---|---|---|---|---|
| h10 : h10+knowHands=0 | 68–60, 53.1% | 44.5–61.6% | 12 / 44 / 8 | 1.01 (60) / 1.06 (43) | 4.1 / 15.4 ms against 4.5 / 17.1 ms |
| h10 : h10+twoTurn=0+laneTiming=0 | 64–64, 50.0% | 41.5–58.5% | 13 / 38 / 13 | 0.92 (56) / 0.86 (43) | 3.2 / 10.5 ms against 3.1 / 11.0 ms |

- The two-turn and lane-timing layers together are exactly even. This confirms the code reading.
- Hand knowledge is worth a non-significant +3 points. That is small for what is, in a card game, the largest
  information edge. The old s10's hand knowledge is a large part of why it beats h10 (15.6%).
- In both runs the h10 side lost more heroes with an empty hand (60 against 43, 56 against 43). At 128 games that
  is not significant, but it points the same way as RESULTS §5: the extra layers do not protect the empty hand.

## 4. The decision pipeline: where one sample and one ply mislead

1. **The card play is half a turn.**
   - CardSim resolves our card alone, before anyone else's (`CardSim.kt:57-73`).
   - Enemy replies are the threat map: each possible card, at a fixed weight, may meet us at our origin or at our
     destination. Each threat is a disc of `reach + 1`, with no walls, no Fast Travel and no choice of target.
   - The map cannot see:
     - an enemy who would rather take our minion than hit us;
     - two enemies who need to cooperate for the kill;
     - an enemy whose only good card is the escape;
     - the order in which three cards resolve.

   The old search's 6→7 step (+120 Elo) was exactly "play the turn out". The fix is **turn search**: K
   determinizations from the deciding team's view, every other hero's card from its own book on that copy (the
   opponent model), the whole turn resolved by the books, HEval at the end (item 1).
   - The play is simultaneous, so the other heroes' cards do not depend on our candidate. Choose them once per
     sample, then resolve each of our candidates: about K × (heroes) card choices + K × 5 turns, roughly 0.5–1 s on
     a desktop at K = 16.
   - Inside a sampled world an enemy's defense is answered by its book on the sampled hand. That is ordinary PIMC,
     not peeking: the sample is drawn from public knowledge and averaged over K, so `NoPeekingTest` still holds.
     DESIGN.md's per-look rule "stop at a hidden answer" would be replaced, for this layer, by "average over
     samples".
2. **After the reveal, the look stops early.**
   - Every played card is public, and the rest of the turn is a perfect-information sequence except for the
     defenses.
   - Yet TurnLook ends with our own action (`CardSim.kt:190-203`), and the threat map stands in for cards we can see.
   - Continuing the turn through every remaining actor's *known* card, played by its book, is cheap: at most 3–5
     more actions, with no card guessing. It is the highest gain for the least cost (item 2).
   - Defenses inside it: priced from the pool (as now), or averaged over K sampled hands; at 10, from the known hand.
3. **The round is the real unit of planning.**
   - "You should plan rounds more than you plan turns" (BGG 2894296).
   - Card order across turns 1–4, the spare kept for turn 4, the strip on turn 3 that sets up the turn-4 kill, and
     the end-of-round battle all live beyond the turn.
   - A cheap rollout to the end of the round (every hero by the static book with CardSim off, about 0.3 ms an answer)
     can score the card *kept* or the order of two cards, then evaluate after the minion battle, scored exactly.
   - The old player found deeper playouts lost in v0.10, but its playout policy was the weak `StaticPolicy`. The
     heuristic book beats that policy's level (h10 against s5: 81.8%), so this needs re-measuring, not assuming.
4. **One determinized copy is used only for resolution, and that is the right call** at the current horizon. Pool
   reasoning keeps hidden hands out of the scores, and the copy only answers the engine. Once turn search exists,
   use K copies there, not one.
5. **Where cheap extra lookahead pays most:**
   1. the rest of the turn after the reveal;
   2. the card play as a turn simulation;
   3. DEFEND continued to the end of the turn (which card to throw depends on who still attacks);
   4. turn-4 decisions continued through the minion battle (the exact push);
   5. UPGRADE and KEEP_UPGRADE looked at through next round's opening turn.

## 5. Hero playbooks

### 5.1 How the playbooks reach a decision at h10

At strength 5 and above, a PLAY_CARD correction of the static strike is silenced inside the look on purpose
(`ctx.patches`, 16 sites; `strikeFix`/`correction`/`deadStrike` in 9 + 9 + 4 files). So at h10 a playbook acts only
through:

- `evalTerms` (pieces and counters in HEval);
- `threatAdjust` (exposure);
- `CHOOSE_*` adjustments, which set the static order that decides which 4–8 options are looked at, and the finishing
  policy;
- `override`, used only by Gydion and Rowenna;
- the PLAY_CARD notes for what the engine cannot see.

Most playbooks put their effort into PLAY_CARD strike estimates, which h10 discards. That is the structural reason
the playbooks measured +2 points over the bare general book (RESULTS §1), and every per-hero ablation sits near 50%.

### 5.2 Score per hero

How fully each playbook realises its research, 1–5. From the sub-review; the top-5 claims are spot-checked.

| Hero | Score | Most important gap |
|---|---|---|
| arien | 4 | Violent Torrent's repeat is valued 6 or −1 (`ArienHeuristics.kt:199`), against the research's 15 for a minion, 8 for a hero and 18 for an empty hand; the Gold's slide that strips a defense modifier (H9) is skipped. |
| bain | 3 | The Bounty marker is a flat ±4 (`BainHeuristics.kt:50`). `killWorth` never adds the marked hero's extra Life, so allies do not focus the mark. |
| brogan | 2 | Only the charge is played. The Punch's push is never used to set up a charge; no `threatAdjust` for his lines. |
| brynn | 3 | "Bound" (3+ obstacles) is read only where the hero already stands (`BrynnHeuristics.kt:152-155`); nothing builds the crowd. |
| cutter | 3 | The plank counts only heroes already on a zone border (`CutterHeuristics.kt:99`); no grapple landing. |
| dodger | 3 | Nothing moves her beside an empty Battle Zone spawn (`DodgerHeuristics.kt:32-36`: PLAY_CARD and CHOOSE_OPTION only). |
| emmitt | 2 | Reverse Time is a flat −2 on turn 1, else 0 (`EmmittHeuristics.kt:48`). The reversed order is never priced. |
| garrus | 3 | Howl is +1.5 / +0.5 (`GarrusHeuristics.kt:146`), with no check of what the caged hero loses. |
| gydion | 4 | `spellValue` is 0 for Suggestion, Dominate, Disintegrate and Polymorph (`GydionHeuristics.kt:162`). |
| hanu | 2 | Hurry up! is +6 for any ally attack at Initiative ≤ 10 (`HanuHeuristics.kt:123`), with no comparison against the revealed order. |
| ignatia | 3 | Her face is predicted from the current coin only (`IgnatiaHeuristics.kt:77`), not from ties among revealed cards. |
| min | 3 | CHOOSE_SPACE scores grenades only (`MinHeuristics.kt:37`). `evalTerms` 2.5, against the research's 8. |
| misa | 2 | Gold's reposition is unscored (`MisaHeuristics.kt:66`); no CHOOSE_SPACE branch. |
| mortimer | 2 | Gold is a flat +3 for any zombie beside a target (`MortimerHeuristics.kt:85-96`); spawn avoidance is −2 on any spawn. |
| mrak | 3 | Nothing sets up seismic; the kill is a flat 8 (`MrakHeuristics.kt:96`); reach hard-coded at 4 (`:73`). |
| nebkher | 2 | No Illusion placement or swap scoring (`NebkherHeuristics.kt:35-39`); `evalTerms` 1.5 an Illusion, against the research's 5. |
| razzle | 2 | No `threatAdjust`, and the threat map attacks only from `enemy.at` (`HContext.kt:258-261`), so her extra bodies are invisible. Needs a core change. |
| rowenna | 2 | The execute override needs `hand.isEmpty()` at PLAY_CARD (`RowennaHeuristics.kt:122-127`). The real window is a hero about to play its last card (`ctx.defenseless`) and acting before her. It almost never fires. |
| sabina | 2 | Red's +3/+4 is priced only at CHOOSE_UNIT after the reveal (`SabinaHeuristics.kt:113-124`), never estimated at PLAY_CARD. |
| silverarrow | 2 | `threatAdjust` returns 0 whenever any card in her pool is outside the exact-range ring (`SilverarrowHeuristics.kt:59`), which is nearly always. |
| snorri | 3 | The rune choice is a fixed lean (`SnorriHeuristics.kt:158-161`), not the card played in that slot. |
| swift | 3 | Red-then-Gold sequencing is only an `evalTerms` +2 (`SwiftHeuristics.kt:62`). |
| takahide | 2 | Ally coordination (H7, H9–H12) is missing; `allyInReach` checks only that an ally exists (`TakahideHeuristics.kt:124-128`); the sting-only threat is dead (`:75`). |
| tali | 3 | Ice placement scores any adjacent enemy (`TaliHeuristics.kt:120-127`), not the initiative race it flips. |
| tigerclaw | 4 | The dash landing is not checked against enemies acting after Initiative 13 (`TigerclawHeuristics.kt:75-79`). |
| trinkets | 3.5 | Turret sites count only minions adjacent to the Turret (`TrinketsHeuristics.kt:186-192`), not the Cannon's line. |
| ursafar | 3 | Silver while enraged is a flat +1 (`UrsafarHeuristics.kt:86`). |
| wasp | 3 | Her ranged-block green is never kept: `bestDefense` reads only the printed Defense (`PlayRules.kt` `bestDefense`). |
| whisper | 3 | Gold's shove toward an ally who can finish is not valued (`WhisperHeuristics.kt:70-79`). |
| widget | 3 | Gold's copy is +2 whenever any skill resolved (`WidgetHeuristics.kt:83`), although `breath()` / `eat()` already compute real values. |
| wuk | 4 | Dominance is 2.5 per adjacent minion (`WukHeuristics.kt:120-124`), not the ≈ 15 of the battle it flips. |
| xargatha | 2.5 | Cleave's pair is wrong: with a hero first the repeat looks only for another hero (`XargathaHeuristics.kt:82-83`, `:107-109`), so "minion, then hero" is missed. Static only: silenced at 5+ by `strikeFix`, so it matters below strength 5 and in ordering. |

### 5.3 The five heroes with the most upside at h10

I chose these by where the gap survives the look. Xargatha's bug is real but silenced at 5+, so it is listed but
not top 5.

1. **Rowenna.** The execute is an `override`, alive at every strength.
   - Gate it on `ctx.defenseless(e)` instead of `hand.isEmpty()`, with her Initiative after theirs, or their card
     already resolved.
   - Add the execute threat to `threatAdjust` for a near-empty hero beside her.
   - This is a free kill per round when it fires.
2. **Emmitt.** Reverse Time reorders the whole turn; a one-action look cannot see that.
   - Price the reversed order per ally and per enemy from `actsBefore`, and add an `evalTerms` for "reversed and our
     unresolved cards are low".
   - Better still, item 1 sees it directly.
3. **Mortimer.**
   - Value the zombie gold as `4 + min(3, zombies beside the target)` against the target's defense pool (`holdsAgainst`),
     priced by `killWorth`.
   - Raise the zombie `evalTerms` from 1.5 to the research's 3, and +6 when 3 zombies touch a gold target.
   - Penalise only spawns the next wave uses.
4. **Hanu.** Hurry up! comes up nearly every round, and after the reveal the order is public.
   - Compare the order with and without Initiative 11.
   - Value an ally only when its target would otherwise move first, and an enemy only when its fast card now waits
     behind an ally's attack.
   - Price the Ultimate's steal by the stolen card's own value.
5. **Nebkher.** A set-up hero whose pieces are what HEval sees.
   - Add Illusion placement for CHOOSE_SPACE (within Gold's range, on the swap lines), so the static order puts the
     right sites into the 6 looked spaces.
   - Raise the `evalTerms` from 1.5 to the research's 5.

### 5.4 Cross-cutting patterns

- **Set-up positioning is missing in about 10 books** (Brynn, Dodger, Cutter, Misa, Nebkher, Min, Mortimer, Ignatia,
  Silverarrow). A shared placement term would serve most of them: "next turn's signature play is live from here".
  It belongs in HEval (or `evalTerms`), not in static placement, or the look drops it.
- **Style is static.**
  - `HeroStyle` is cached per hero (`HContext.kt:115-120`), so "after the alternative Red, ideal distance 2" cannot
    be expressed.
  - A `style(ctx)` overload would allow it.
  - Several `laneWeight`s also differ from the research without a note: Rowenna 1.1 against 0.45, Takahide 0.9
    against 0.35.
- **Enemy initiative is ignored by most books.** A shared helper, "does X act before Y, P from the pool (or the
  reveal)", is the biggest single lever for Hanu, Tali, Rowenna, Tigerclaw, Dodger, Ignatia and Silverarrow.
- **Flat values run far below the research.**
  - `threatAdjust` is 1.0–1.5 in 8 files, where a real threat is a kill of about 16–34.
  - Kills are a flat 8 in Mrak, Mortimer and Nebkher, not `ctx.killWorth`.
  - Gates that need the whole pool to qualify are dead (Silverarrow `:59`, Takahide `:75`).
- **Duplicated helpers.** Three strike corrections, two `moveOutlook`s, two `mayHold`s, five copies of "another card
  also meets", and three hand-coded repeats where `BatchASupport.bestDistinctPair` should be the one path.
- **Core gaps that only the core can fix.**
  - The threat map uses one body per hero (Razzle; Nebkher's swaps).
  - Reach is a disc, not an exact range or a straight line (Silverarrow, Takahide, Swift, Snorri, Trinkets).
  - `killWorth` has no per-hero hook (Bain's Bounty).
  - `keepDefender` sees Blue only (Wasp's Block).

## 6. Prioritized improvements (top 20)

**Kind:** S = needs search through the engine beyond today's one ply; H = a pure heuristic or evaluation fix;
M = measurement.

**Impact** is my estimate of the win-rate gain at h10 against s8 and s10: **high** is 5 points or more, **med** is
2–5, **low** is under 2.

**How to measure**, unless it says otherwise: a knob for the change (`h10+name=0/1`), 768 games head to head on
development line-ups from 1000, then against s8 and s10. Held-out sets only for the final table.

**1. Turn search at the card play (PIMC on the heuristic policy).** S, impact high, risk med (cost; opponent-model
bias; strategy fusion).
- *Change:*
  - K determinizations from the team's view.
  - Every other hero's card from its own book on that copy, chosen once per sample because the play is
    simultaneous, with a uniform floor of about 15%.
  - The whole turn resolved by the books; HEval at the end of the turn.
  - Aggregate by the mean, or mean − λ·sd.
  - Threads through `SampleRunner`.
- *Files:* new `TurnSearch.kt`; `HeuristicAi.cardLook`; `HeuristicProfile`.
- *Measure:* h10+turnSearch : h10; then against s8 and s10; answer time.

**2. Finish the turn after the reveal.** S, impact high, risk low.
- *Change:* `TurnLook` and `Lookahead` continue through every remaining actor's known card, played by its book.
  Hidden defenses are priced from the pool, or averaged over K samples.
- *Files:* `CardSim.kt:190-203`, `:209-220`; `Lookahead.finish`; `HeuristicAi.look`.
- *Measure:* the `turnRest` knob; empty-hand defeats per game.

**3. Calibrate HEval as a whole.** M + H, impact high, risk low.
- *Change:*
  - Add knobs on HEval's exposure, hand, life ladder and each potential term. `fear` never reached HEval.
  - Fit the term scales to outcomes on h10 games (port the old `FitTest`: logistic or Texel).
  - Price the two losing races alike: `lastLife` against the match-point lane.
- *Files:* `HEval.kt`, `HContext.kt:440-467`, `:951-964`; a new fit test.
- *Measure:* held-out log-loss, then a tournament.

**4. Make the rules that matter survive the look.** H, impact med, risk low.
- *Change:*
  - Move G68 setup, G65 thin, G50 fork, the plan target, DefendRules keep-cost and G81, and formation / ideal
    distance into `commitment` or HEval terms.
  - Delete the rest, and state in GENERAL which rules are static-only.
- *Files:* `UnitRules.kt:18-66`, `SpaceRules.kt:28`, `DefendRules.kt`, `ActionRules.kt:108-127`,
  `HContext.kt:848-878`, `HEval.kt`.
- *Measure:* a knob per moved rule.

**5. Teammates inside CardSim.** H (light S), impact med–high (3v3 is 49%), risk low.
- *Change:*
  - Mark a computer teammate's PLAYED card as known (`playingNow`) in the scoring context.
  - Add a postlude: a teammate whose known card acts after ours resolves with its book.
  - Drop the constant +6/+3.5/+2 when either look ran.
- *Files:* `CardSim.kt:81-130`, `:236-254`; `HContext.kt:537-542`, `:384-385`.
- *Measure:* h10 : h10+post=0, in 2v2 and 3v3.

**6. Round rollouts for the hand.** S, impact high (empty-hand deaths), risk med (old result: "deeper lost").
- *Change:*
  - At card plays on turns 1–3, roll the round out with the cheap book (CardSim off) for each candidate card, over K
    samples.
  - Apply the exact minion battle and level-ups, then HEval.
  - Score the card kept, not only the card played.
- *Files:* new `RoundRollout.kt`; the HEval round-end path.
- *Measure:* empty defeats per game; win rate.

**7. An opponent card model.** S, impact high, risk med (exploitability; cost).
- *Change:*
  - Predict each enemy's card from its own book (CardSim from its team's view on our copy, softmax plus a floor).
  - At 10 the hand is known; below, average a few sampled hands.
  - Feed the predicted weights to `threatInfo` instead of 0.65/colours.
- *Files:* `HContext.kt:592-619`; new `OpponentModel.kt`.
- *Measure:* the guess rate against the actual card; win rate.

**8. Use strength 10's hand knowledge.** H, impact med, risk low.
- *Change:* with `knowHands`, a look continues through an enemy's DEFEND or discard, answered by its book from the
  known hand. Only the facedown card stays hidden.
- *Files:* `Lookahead.kt:131-139`; `CardSim.run` (`:138-152`).
- *Measure:* h10 : h10+knowLook=0; `NoPeekingTest` at 1–9.

**9. One projected minion battle (STRATEGY L3).** H, impact med–high, risk med (retune).
- *Change:*
  - Replace push potential, `opportunity` × 2 and `laneThreat` with one term: expected kills per hero this round
    from reachable attack cards, limited by `playsLeft` and weighted by the pool.
  - Apply the exact a ≥ 2b push and carry-over when the round is over.
- *Files:* `HEval.kt:83-85`, `:118-158`; `HFacts.kt:300-326`; `HContext.kt:321-332`, `:820-824`.
- *Measure:* minions taken per game; win rate.

**10. Scale consistency.** H, impact low–med, risk low.
- *Change:*
  - Apply `LANE` to `laneKillDelta` (`HFacts.kt:250`).
  - Update the units in GENERAL.md §2, DESIGN.md "Scoring" and README to "≈ 21 a minion".
  - Re-centre the hero notes that assumed 15.
- *Files:* `HFacts.kt`, the docs.
- *Measure:* a `laneDelta` knob.

**11. Blend the whole action by `defeatBefore`.** H, impact med, risk low.
- *Change:* value = (1 − first) · HEval(after) + first · HEval(defeated at the origin, action lost), not just the
  strike.
- *Files:* `CardSim.kt:236-254`.
- *Measure:* a `defeatBlend` knob.

**12. Joint card choice for computer teammates.** S, impact med (3v3), risk med (cost).
- *Change:* enumerate the top 3 × top 3 card pairs and resolve both in initiative order (prelude and postlude).
  Each teammate re-derives the same joint plan deterministically.
- *Files:* `CardSim` / `TurnSearch`.
- *Measure:* 3v3 against s8; 2v2 head to head.

**13. Tempo to the zone, Fast Travel and blocking.** H, impact med, risk low.
- *Change:*
  - An HEval term per hero: actions needed to reach the goal zone, using the engine's `fastTravelDestinations`
    (which enemy units block).
  - Replace the flat off-board −8 with the actions needed to return.
- *Files:* `HContext.kt:309-318`; `HEval.kt:77-89`.
- *Measure:* a knob; how often enemies are blocked.

**14. Lane model v2.** H, impact med, risk med.
- *Change:*
  - The next push's p after a push from the new zone's spawn counts (6 v 5), plus hero presence and levels (STRATEGY
    L4), instead of 0.5.
  - G13 `badPush` then becomes part of HEval.
- *Files:* `HEval.kt:131-141`, `:206-212`; `HFacts.kt:231-292`.
- *Measure:* a knob; how games end.

**15. A real strength ladder.** H, impact low on strength itself, high for the slider; risk low.
- *Change:*
  - 9 = items 1 and 2; 10 = + items 6, 7, 8 and 12.
  - Drop G17 mixing at 10, or keep it only for ties below the search noise.
- *Files:* `HeuristicProfile.kt`; `HeuristicAi.kt:164-171`.
- *Measure:* h10 : h9, h9 : h8 (each step above 55%).

**16. A turn-3 empty-hand rule, until item 6 lands.** H, impact med, risk low.
- *Change:* a card that leaves the hand at or below `playsLeft`, within reach of an unshown enemy attack colour, pays
  P(defeat next turn) × `killWorth` in `PlayRules.add`. Weight it by the pool, not by the capped 0.28.
- *Files:* `PlayRules.kt:46-65`; `HContext.kt:669-671`.
- *Measure:* empty defeats per game.

**17. The top 5 playbooks** (§5.3: Rowenna, Emmitt, Mortimer, Hanu, Nebkher). H, impact med (per hero), risk low.
- *Files:* the five hero files.
- *Measure:* `AI_H_ABLATION`, 192 games each.

**18. A core threat map that knows shapes and bodies.** H, impact low–med, risk med.
- *Change:*
  - `threatsOn` loops over hero bodies (pieces with `heroBody`).
  - `HCard` gets flags for exact range and straight lines, read by `threatsOn`, `opportunity` and `attackReach`.
  - A per-hero `killWorth` hook (Bain's Bounty).
- *Files:* `HContext.kt:184-193`, `:254-265`; `HCard.kt`.
- *Measure:* ablations for Razzle, Silverarrow and Takahide.

**19. Playbook hygiene.** H, impact low–med, risk low.
- *Change:*
  - Put the research values back where flats sit (`threatAdjust` 1–1.5; kills a flat 8 → `ctx.killWorth`).
  - Weight pool gates instead of all-or-nothing.
  - One `bestDistinctPair` and strike-correction path.
  - A `style(ctx)` overload.
  - Fix Xargatha's Cleave pair for strengths below 5.
- *Files:* `heroes/*`; `HeroHeuristics.kt`.
- *Measure:* the `all` ablation at 8.

**20. Upgrades by look.** S (light), impact low–med, risk low.
- *Change:*
  - Score UPGRADE_CARD and KEEP_UPGRADE by HEval after a short look at next round's opening hand.
  - Add per-hero item thresholds (G105, G112: initiative against enemy Golds; Defense on the front liner).
- *Files:* `CardRules.kt:45-97`.
- *Measure:* a knob; the level reached per game.

**Order of work.**
1. Items 3, 4, 5, 10 and 11: cheap, no new machinery, and they make HEval worth searching with.
2. Items 2 and 1: the search.
3. Items 7, 8, 6 and 12 on top of it.
4. Items 9, 13 and 14: evaluation features the search will then exploit.
5. Item 15 re-cuts the slider once there is something to put at 9 and 10.

Playbook work (items 17–19) is parallel and independent.

## 7. Notes on measurement

- The per-hero table in RESULTS.md §3, "Per-hero ablation at strength 8", has a header and no rows. The 32 per-hero
  numbers are missing from the doc.
- `fear`, `spare` and `tempo` scale static or commitment parts only, not HEval. Any conclusion of the form "weight X
  does not matter" from those rows is about the static book.
- Head-to-head pairs mostly split (the same seat wins both mirrored games). Report pair counts, and prefer SPRT on
  pairs for the search items: their effect should be large, and the runs are expensive.
- s10 is the target. Every step should be measured against s10 as well as s8: h10 against s10 is 15.6%, and that is
  the gap the owner's goal names.
