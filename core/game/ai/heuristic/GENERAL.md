# General playbook

The general heuristics every hero uses (ADR-085). Hero playbooks add `adjust` / `evalTerms` / `override` on top; a hero with no playbook must still play from this file alone. Another agent turns this into `HEval`, `TurnPlan` and `General.kt`. No Kotlin here.

Units are STRATEGY.md §2.2, kept on purpose: **one coin = 1**, **one minion of projected lane advantage at an even Quick lane ≈ 15**, **a card in hand ≈ 2.5**, **a finished game = ±1000**. Every weight below is in those coin-equivalents.

## How to read a rule

```
G<n> [DecisionKind] FROM <strength> MODE <EVAL|ADD> WHEN <fact HContext can know> THEN <prefer / avoid / override> WEIGHT <coin-equivalents> SOURCE <[RB p.N] / [RULINGS n] / [BGG n] / [URL] / [REASONED]>
```

- **FROM** is the lowest strength whose layer may use the rule (DESIGN.md). Lower strengths either lack the fact (no threat map below 3, no turn plan below 5, no enemy-card reading below 7, no two-turn plan below 9, hands only at 10) or are allowed to slip (section 7). A rule with FROM 1 is the floor.
- **EVAL** — the weight is what a direct scorer adds to that option. A one-ply lookahead that finishes the action and calls `HEval` already contains this term; do not add the weight again.
- **ADD** — add the weight even on top of lookahead. The term is a commitment, a tie-break, or a fact `HEval` does not model.
- **override** — the option scores +1000 (or −1000 to forbid it) ahead of ordinary weights. Use it only for a terminal win or for a defense that cannot succeed.
- Rules inside one `DecisionKind` are ordered by expected impact, highest first. The first rule that overrides wins; otherwise sum the weights of every rule whose WHEN holds.

Rule facts cite the 2nd-printing rulebook or `core/game/RULINGS.md`. A community claim that contradicts those is marked as a contradiction and is not a rule. Card text beats the rulebook (RB p.3); the engine, with its page-referenced tests, beats this file.

## Facts the rules assume (`HContext`)

Open information only, except strength 10's `knowHands` (DESIGN.md, ADR-062). Before the reveal, enemy reasoning uses the **possible pool** (every hand consistent with level, item icons, and cards already seen), weighted, never one sampled hand treated as the truth. Strength 10 may read the real hand. Teammates' facedown cards stay hidden at every strength.

| Predicate | Meaning |
|---|---|
| `turn` | 1..4. `round` is the round index, 1-based. |
| `hand(u)` | Cards in `Zone.HAND`. The card played this turn is `Zone.PLAYED` and is not a defense (`defenseCards` in `Combat.kt` reads HAND only, plus `extraDefenseCards`). |
| `spare(u)` | Before `u` plays on turn `t`: `hand(u) − (5 − t)`. Without discards this is 1: four plays and one insurance card [RB p.7, p.15]. Each defense or forced discard lowers it by 1. |
| `defensePool(u)` | HAND cards that can defend, plus engine extras. After `u` has played this turn, the pool is what is left in HAND. |
| `effectiveInit(card)` | Printed initiative + initiative items + public modifiers (Ice, Poison, an active reverse-initiative effect). Compare these, not printed numbers. |
| `life(team)`, `tier(hero)` | Life counters left. `tier` is the lowest Tier among the hero's red/green/blue cards in play. Gold and Silver "are not considered when checking your lowest card Tier" [RB p.15]. The ladder is the table below [RB p.23, `rules-reference.json`]. |
| `deathsToLose(team)` | `life(team) / tier` of the enemy hero we would actually hit. ≤ 1 means one more defeat of that hero ends the game [RB p.13]. |
| `matchPoint(lane)` | That lane's Wave counter is 1, or its Battle Zone is adjacent to a Throne, or (double lane, after the last Wave on either lane has flipped) the next push changes the zone-count winner [RB p.3, p.8, p.20; RULINGS 32]. |
| `margin(lane)` | Our minions in that Battle Zone minus theirs, using the engine's counts (including "counts as"). |
| `pp(team, lane)` | Push potential: minion-killing actions still available to heroes who can reach that lane's minions this round. Section 2. |
| `projected` | Minion counts minus both sides' `pp`, then the battle arithmetic, then any public end-of-round removal. Section 2. |
| `W(lane)` | Probability we eventually win through this lane. Section 2. |
| `exposure(space)` | `P(defeat)` from enemies still to act this turn (exact card after the reveal; possible pool before it, and for next turn) times the kill value in section 2. |
| `minionMod(space)` | +1 per adjacent friendly melee or heavy, −1 per adjacent enemy melee or heavy, −1 per enemy ranged minion in radius 2. A friendly ranged minion adds 0. An immune heavy still counts [RB p.14, p.18; RULINGS 21]. |
| `ft(zone)` | Fast Travel into or out of a zone is legal only when it is the same zone or an adjacent zone and neither zone contains an enemy hero or minion [RB p.11]. Ask the engine (`fastTravelDestinations`). |
| `steps(team, lane)` | Zone steps from that lane's Battle Zone to the enemy Throne. `stepsAgainst` is steps to our Throne. |
| `intent` | Re-derived at every decision from the same function of the state (no memory). Section 3. |

**Level ladder** (what a kill pays). Costs are the coins to buy that level from the one below; cumulative coins from level 1 are 0, 1, 3, 6, 10, 15, 21, 28.

| Level | Lowest tier | Coins to the killer | Assist each teammate | Life spent | First thing this level adds |
|------:|------------:|--------------------:|---------------------:|-----------:|---|
| 1 | 1 | 1 | 1 | 1 | start, all coloured cards Tier I |
| 2 | 1 | 2 | 1 | 1 | one coloured card Tier II |
| 3 | 1 | 3 | 1 | 1 | two coloured cards Tier II |
| 4 | 2 | 4 | 2 | 2 | all three coloured cards Tier II; death cost doubles |
| 5 | 2 | 5 | 2 | 2 | first Tier III available |
| 6 | 2 | 6 | 2 | 2 | second Tier III |
| 7 | 3 | 7 | 3 | 3 | all three Tier III; death cost triples |
| 8 | 3 | 8 | 3 | 3 | Ultimate |

[RB p.13, p.15, p.23]. Killer coins equal the level; each other friendly hero, on the board or not, gets the assist [RULINGS 24]. A minion defeat pays 2, a heavy 4; a minion removed in the Minion Battle pays no coins [RB p.13, p.8].

---

## 1. Principles

Two races, and they share the same cards. A team wins by pushing a lane into the enemy Throne, by winning the last push, or by the enemy spending their last Life counter [RB p.3]. Across hundreds of games the designer counted kills 53.23%, pushes 46.77% (a double push 22.31%, the final push 24.46%); kill wins are more common in 3v3, and in 55.81% of push wins somebody was one kill from a Life win [BGG 3033553, designer `nichik`, 2023]. About 30% of games reach the last wave [BGG 3491194, designer]. A Throne push follows a positioning error, Life counters follow a lost fight, the last push goes to the team slightly ahead in a long game [BGG 3491194]. Neither race is optional: "It is a lot easier to win the last push if you can afford to die, and the other team cannot" [BGG 3033553].

The priority order below is the intent order. It is a utility sum (section 2), not a first-match flowchart. Hard overrides are only P0 and the "defense cannot succeed" rule in section 6.

| Order | Intent | Take it when |
|---|---|---|
| P0 | Win now | This action pushes at `matchPoint`, spends the enemy's last Life, or completes a printed alternate win. Override. |
| P1 | Survive | An enemy still to act can defeat us and a legal card escapes or answers it. A priced death (section 6, DEFEND) is the only exception, and only from strength 9. |
| P2 | The point | At `matchPoint` or `deathsToLose` ≤ 1, the action that changes `W` or Life outranks farm and poke. |
| P3 | The kill that is real | The target's `defensePool` is empty this turn, or a second attacker acts after us on the same target, or (strength 9) a discard now makes them empty on turn 4 and a teammate can reach them then. |
| P4 | The marginal minion | The minion that flips `projected` from a loss or a tie into a win, then the enemy ranged minion whose radius covers us, then a minion a teammate cannot reach. |
| P5 | The opening | `projected` advantage ≥ 2 and this is not `matchPoint`: spend attacks on heroes, forks, or the next zone. Do not spend the attack that would push early into a bad zone (section 2, heavy). |
| P6 | Farm | A safe minion. It is 2 coins plus its share of `W`, and it is certain [RB p.13]. |
| P7 | The next fight | Move to the projected Battle Zone, block a Fast Travel corridor, stand in a fork, set a piece a teammate uses. |
| P8 | Keep the card | A primary with no target, played only to be Held, is a wasted action. Hold the card for a later turn instead. |

### How the order moves

**By phase.** Rounds 1–2, everyone level ≤ 3: coins ×1.3, Life at the bottom of the schedule, P6 ahead of P3 unless the kill is free. Death costs 1 Life and 1–3 coins. From the moment any enemy is level 4, their kill pays 2 Life and tier-2 assists; from level 5 their cards are Tier III [RB p.23; BGG 3472633, designer: "the actual true bump in power level happens at level 5"]. From level 7 a death spends 3 Life. On turns 3–4, P3 rises: hands are thin and the spare is cheap [BGG 2894296, anwei, playtester].

**By lane state.** Use `projected`, not the current count.

- Advantage ≥ 2: P5. "One minion advantage makes winning the push very likely. Two minion advantage almost guarantees it." [BGG 2899258, designer]. Go to where the next Battle Zone will be. "The easiest way to lose the game is to miscalculate the push direction and get stranded on the wrong side of the board."
- Even: P4 then P6. A fork (P7) that threatens both a hero and a minion is worth more than walking straight at the minion, because the threat itself cuts their push potential [BGG 3511053, community].
- Disadvantage ≥ 2: stop spending attacks in the current zone. Red may be spent on movement to the next zone [BGG 2899258]. One hero with a slow card may take a single minion on the way if it does not strand them.
- Defender's pre-throne on Forgotten Island only: the defender spawns 6 minions and the attacker 5 (4/3 melee, 1 ranged, 1 heavy). Confirmed from `forgotten-island.json`. The rulebook's "more minion spawn points" sentence is about that shape of map [RB p.8]. Narrow Passages' side zones are 5 against 5; the designer said the tower icon is not a minion: "No. Ignore it." [BGG 3527919, `nichik`, 2025-06-19]. Across the River and Magma and Ice spawn 4 against 4 in every zone (`across-the-river.json`, `magma-and-ice.json`). A claim that those maps should gain a defender minion contradicts the designer.
- "Being at your pre-throne zone is almost never a good thing." "Defenders are always at a disadvantage." [BGG 3168047, designer]. The extra minion on Forgotten Island compensates for a zone you can lose the game from; it is not a place to camp. At 2 minions each in the centre, the designer's line is: take one on turn 3, reposition with the long move on turn 4, and let the wave push [BGG 3168047].

**By Life.** `deathsToLose` ≤ 1: P1 and P2 swallow the turn; exposure is multiplied (section 2). We can afford deaths (`deathsToLose` ≥ 3) and they cannot: P3, and a deliberate late death that puts us on the next zone is legal [BGG 2432019, designer]. Both sides on 1–2: do not trade; the side that forces the other to spend an attack on a hero while still taking the marginal minion wins [BGG 3033553].

**By team size.** Push potential starts at about two minion-kills per hero and then falls as heroes share the same minions [BGG 2899258].

| Size | Life, waves (by heroes in play) | What changes |
|---|---|---|
| 2v2 | Quick 4 Life / 3 waves; Long 6 / 5 [RB p.5, p.23] | Each hero is half the team's attacks. One hero hunting while the other farms gets neither the kill nor the push [BGG 3033553]. Both heroes take the same intent. Four attack cards on six minions: `pp` ≈ 0.5 per attack card that can reach. |
| 3v3 | Quick 5 / 3; Long 8 / 5 | Kills get easier because a third hero can farm while two commit. `pp` ≈ 0.35 per reaching attack card: three heroes hunt the six minions two heroes hunted, and 0.35 beat 0.5 in the measured 3v3 games (`AI.md`, "Team sizes"). Two heroes on one target, the third on the marginal minions. |
| 4v4 | Double lane, 6 Life, 7 waves a lane [RB p.20] | Split 2 and 2. `pp` is per lane, among heroes who can reach that lane. A global 0.25 (four heroes, one pool) lost games (`AI.md`). Across the River's lanes touch through `middle-side`, `red-side` and `blue-side`, and at the ends (`lane-1-1`–`lane-2-1`, `lane-1-4`–`lane-2-4`). Rotation is real; Fast Travel into a zone that still holds enemy minions is not [RB p.11]. |
| 5v5 | Double lane, 7 Life, 7 waves a lane | Three heroes on the lane where `stepsAgainst` is smallest, two on the other. A fifth hero on a lane that already has two attackers mostly body-blocks and steals minions. |

**By score.** Comebacks are the normal shape of the game, not a special mode. "Comebacks happen all the time." Higher-level heroes are worth more when killed [BGG 3080063, designer]. The playtest community rarely calls a game early for that reason [BGG 3080063, community]. Translate the deficit into the same coin units:

- Behind on the lane (`W` < 0.35) and Life is not yet the point: P7 to the next zone, then P3 on a level-4+ enemy whose kill is real. Chasing the current zone's last minion and arriving late is how teams get stranded [BGG 2899258, BGG 3498829].
- Behind on Life (`deathsToLose` ≤ 2) and the lane is even: P1, P6, no even hero trade. A level-4+ enemy is the comeback target only when P3 already holds. Farming them for coins is the designer's comeback engine: the leader's death pays more coins and more Life than the trailer is worth.
- Ahead on the lane with an opening: P5. Do not accelerate a push you are not positioned for.
- Ahead on Life: you may spend a death to be in the next zone [BGG 2432019]. They may not. That asymmetry is the whole endgame [BGG 3033553].

**Double lane, directional.** On Across the River the starts are offset: `lane-1` begins at `lane-1-2` (Blue thrones in 2 pushes, Red in 3) and `lane-2` begins at `lane-2-3` (Red thrones in 2, Blue in 3). Each team therefore has an attack lane and a different defend lane. Score a push on the attack lane as a throne race with the throne two steps away; score a loss on the defend lane the same way from their side. A 2–2 split is the default because ignoring either lane gives the throne away. Magma and Ice is symmetric (both lanes start in the middle, two steps either way, 4 minions a side). Last-wave scoring is the zone count across both lanes, most zones between own Throne and both Battle Zones [RB p.20]. A tie does not win: play continues until one push breaks it, or Life does [RULINGS 32; BGG 3499394, designer, 2025-04-21]. A push that would make both teams win at once is undone [RB p.20; RULINGS 32].

---

## 2. Evaluation (`HEval`)

`score(team) = Lane + Life + Σ our heroes − Σ their heroes`, in coin-equivalents. `P(win) ≈ σ(score / 15)`. A terminal position is ±1000, not a large finite number. One-ply lookahead uses this function and then adds only `MODE ADD` rules.

The lane term is `60 × (W − 0.5)` plus a small `1.5 × margin` so a minion that will be killed for coins is not worth zero when it does not change `W`. The **15** for one minion of advantage is the `W` swing, not a second term. Adding 15 per minion on top of `W` double-counts.

### Lane

Quick, three waves, Forgotten Island (throne at offset ±2 from the centre):

```
W(k, w, p) = p × (w = 1 or k+1 = +2 ? 1 : W(k+1, w−1, p))
           + (1−p) × (w = 1 or k−1 = −2 ? 0 : W(k−1, w−1, p))
```

At `p = 0.5`, `W(0,3) = 0.5`. One push makes `W(+1,2) = p(2−p) = 0.75`. `60 × 0.25 = 15`. Long (five waves) uses the same recursion with the throne at ±2 and `w` starting at 5: two ahead thrones, otherwise wave 5 decides. A double-lane map uses the recursion per lane with that lane's real throne distance (`steps`), then sums. Multiply a lane's `(W − 0.5)` by 1.4 when `steps` or `stepsAgainst` is 2 and the other lane is not yet at match point: a throne there ends the match while the long lane still needs another push. [REASONED from the recursion and the map JSON.]

`p = σ(1.1 × projectedAdvantage + 0.3 × presence)`. `presence` is our heroes within one action of the next Battle Zone, minus theirs. `1.1` makes +1 advantage `p ≈ 0.75` and +2 `p ≈ 0.90`, which is the designer's "very likely" / "almost guarantees" [BGG 2899258]. A projection that empties a side is `p ≈ 0.95` (a mid-turn push can still be answered). On turn 4 after every action the projection is the battle itself.

**Push potential.** At round start, two minion-kills per hero, less for a melee hero more than one action (about 5–6 spaces, or two actions at the "3 spaces = one action" rate) from the Battle Zone [BGG 2899258, BGG 2898177]. Exact version: count remaining attacks, "defeat a minion" skills, and removals that the engine can already see will reach a minion, given movement still in hand. A Red or Gold already played without a minion is −1 to that hero [BGG 2899258]. Cap the team's total by the enemy minions actually standing, and discount when several heroes can reach only the same minions:

| Heroes who can reach the lane | Per reaching attack card |
|---|---|
| 2 (2v2, or one lane of a 2–2 split) | 0.50 |
| 3 | 0.35 |
| 4 on one lane | 0.25 |

The 0.35 is the measured 3v3 figure (`AI.md`). The 0.25 is only when four heroes really share one lane; a 2–2 split stays at 0.50 per lane. End-of-round removals that are already public (the target is on the board, the effect is active) count before the battle [RULINGS 28]. Hero playbooks add the ones that depend on a hidden card.

**Heavy and the early push.** The heavy is immune while any friendly non-heavy stands in its Battle Zone, and it still gives the defense modifier [RB p.13; RULINGS 21]. Killing it when it is alone is 4 coins and, if it was the last minion, an immediate push [RB p.8]. On Forgotten Island a mid-round push from the centre into the pre-throne spawns 6 for the defender and 5 for the attacker; the battle then removes one attacker minion and leaves 6 against 4. That is the designer's "minion advantage of two" [BGG 2899258]. Take that heavy only when someone can defeat one more defender minion before the battle. On maps where both sides spawn equally, the gift is zero; the remaining cost is being on the wrong side of the new zone with Fast Travel into it blocked by the fresh minions [RB p.11]. Prefer a push that happens in the Minion Battle, so the attacker gets a full round in the new zone.

**Minion battle arithmetic.** With `a > b ≥ 1`, the loser removes `a − b` and keeps `2b − a`. The lane pushes at the end of the round when `a ≥ 2b` (or `b = 0`, which has already pushed). Displacement does not change the count; a minion left outside the zone walks back [RB p.18, p.8]. The losing team chooses which minions go; the heavy goes last [RB p.8].

### Life, coins, levels

| Term | Weight | Note |
|---|---|---|
| A counter whose loss ends the game | terminal ±1000 | The defeat that spends it, not a coin term. |
| Next counter, when 1 would remain | 22 | |
| When 2 would remain | 14 | |
| When 3 would remain | 9 | |
| When 4 would remain | 6 | |
| When 5 or more would remain | 4 | Early Quick deaths live here. |
| Coin that does not complete a level | 1 | Coins matter at the end of the round [RB p.15]. |
| Coin that completes a level this round | 1.5 | The upgrade arrives now. The Pity coin is paid next round to anyone who did not level, and cannot be spent this round [RB p.8]. |
| Level, beyond the coins paid | +3 | A better card and an item. |
| The level that adds the first Tier III (level 5) | +2 on top of the +3 | Designer: the power bump is there, not at the Ultimate [BGG 3472633]. Tier III also introduces the movement, range and radius items, which Tier II does not [BGG 2896501, Panetierre, playtester]. |
| Ultimate unlocked | +8 | About 2–4% of games [BGG 3472633]. Value it once it is on the board. Do not farm toward it while P0–P2 are available. |
| `deathsToLose` ≤ 1 | ×2 on that team's exposure | The next hit is the game. |

A level-4 kill in 2v2 is 4 coins + 2 assist + two counters. With 4 Life left those counters are 6+9 = 15, plus about 5 of tempo, about 26, against a minion's 2 coins plus a share of the 15. The kill is worth more than two minions exactly when the Life is real, which is the designer's point [BGG 3033553]. Early, with cost 1 and full Life, the same kill is about 4+6+5 = 15 and the minion is competitive: farm.

### Heroes

| Term | Weight |
|---|---|
| Card that will still be played as an action | 2.5 |
| Spare insurance, turns 1 / 2 / 3 / 4 | 3 / 2 / 1.5 / 0.8 |
| Each action we will have to pass | −3 |
| Off the board | −4 per action of travel back to the Battle Zone, minimum −4. A clear Fast Travel hop is one action per zone. "Making the kill usually costs a team two actions … three actions for that hero to return" [BGG 2898177, Panetierre]. |
| Space outside the Battle Zone | −0.55 per space. A path of 3 spaces is one action, a path of 5 is two; obstacles often double the cost [BGG 2898177]. |
| Inside the Battle Zone | +1 |
| Exposure | `P(defeat) × (Life value + killer coins + assists + 5 tempo) + P(forced discard) × spare weight`. After the reveal, this turn's card is weight 0.6 of that (it is known; they may still choose a different action). Next turn's possible card is weight 0.22. A "discard a card, or be defeated" against an empty `defensePool` is a defeat [RB p.13; RULINGS 22]. |
| A hero with an empty `defensePool` now, or after the card they still have to play | exposure counted in full, even where the term is otherwise scaled down. This is the two-turn kill the search never set up (`AI.md`, known weaknesses). FROM 3. |
| Minion opportunity | An attack card that reaches an enemy minion: 3 this turn, 1.5 on a later turn. Small, so it does not double-count `W`. |
| Piece (token, turret, Pyro; not a body) | Best 4 per hero. 1.2 if it survives the round, else `0.8 × turnsLeft / 4`. × `max(0.25, 1 − 0.12 × spaces from the Battle Zone)`. +0.4 within 2 of an enemy hero. Enemy pieces count against us, so Clear has a reason. Hero `evalTerms` add what the piece is for; this floor is why a set-up hero is not worth zero. |
| Several bodies | Best presence and reach, worst exposure. A defeat of any body defeats the hero. |

**Tempo of an action.** A round is four actions, and about two of them can defeat a minion [BGG 2896501]. One wasted action (Hold with a minion in reach, a primary that fizzles, a Fast Travel that does not change zone) is a quarter of the round: value it at **8** when a minion was available (2 coins plus a discounted share of the 15), and at **4** when it only gave up position. A skill that removes a card without spending an attack is worth more than an attack that does the same, because the attack was the scarce one [BGG 2899180, Panetierre]. A heal that retrieves a card is worth the card it returns, minus the partial action spent standing still.

**Initiative as a value.** Do not add a flat "higher initiative is better." A card that acts after the target has left scores 0 for the attack and keeps its other text. A card that acts before every remaining threat scores the destination as safe. Middle initiative is the expensive kind: you act too late to hit and too early to answer [BGG 2908021, community, including playtester Panetierre]. An active effect that reverses initiative this turn swaps every comparison [the effect is public, RB p.17].

**Threats and set-ups, generic.** A card still in hand that can attack is a threat on every space it reaches, at the 0.22 next-turn weight, because the enemy must play around it [Brogan dashboard advice, catalog `advice`]. A fork — one space from which a card in hand threatens both a hero and a minion — takes `max(hero, minion)` plus **3**, the value of making them choose [BGG 3511053]. A piece that is itself a threat origin (the general floor above) is worth keeping; the hero playbook replaces the floor with the real number. Zoning that never deals damage still cuts `pp`: if they cannot stand on their minion, they do not take it [BGG 3779890, Cryomancy, 2026-10-09, the Zoner class: the slow attack's value is the space it denies].

**Colour averages, for strengths that do not read the catalog.** Printed means, Handicap cards excluded, from STRATEGY.md §1.3 [DATA], not remeasured here. Gold initiative 11.1, defense 2.0, attack 3.2. Silver initiative 8.0 (range 1–13), defense 2.9, no movement. Red Tier I/II/III initiative 8.0/8.3/9.0, defense 5.4/5.5/6.1, attack 4.2/4.6/5.0. Green initiative 4.0/3.5/3.0, defense 2.5/2.9/3.5. Blue initiative 9.3/9.9/10.3, defense 4.3/4.8/5.3. Use these at strength 1–2. Strength 3–6 use the stat-bar reading instead (section 5). Strength 7+ use catalog numbers for every card in the possible pool.

---

## 3. Turn planning (`TurnPlan`)

At `PLAY_CARD`, and again at every later decision of the same turn, derive one intent from the state. The same state must produce the same intent (no memory). The intent is the priority in section 1 whose value, under `HEval`, is highest. Later choices follow it until a higher priority becomes true — a revealed card that now kills us, or a minion that is suddenly the last one.

| Intent | Choose it when | What later choices do |
|---|---|---|
| `WIN` | P0 is available on a card in hand. | Every option that completes it. Movement and Hold lose. |
| `SURVIVE` | P1. The card is the escape that acts first, or the card that keeps the answering defense in the pool. | The destination is the space with exposure below the kill value. Decline a respawn that is a certain death. |
| `KILL(u)` | P3 against hero `u` this turn. | Target `u`. Approach only as far as the attack needs. A minion is the target only when `u` has left reach and the minion is P4. |
| `SETUP(u)` | Strength 9+. A discard or chip on `u` this turn leaves their `defensePool` empty on turn 4, and some friendly hero can reach `u` on turn 4 with an attack or a discard-or-defeat. | This turn: the strip, then a safe minion if the strip is already done. Do not spend the finisher's card early. |
| `CLEAR(m)` | `m` is the marginal minion, the lone heavy we are allowed to take, or the ranged minion covering us. | Path to `m`. Do not take a different minion a teammate's intent already names. |
| `PUSH` | `projected` says our battle win is the game, or the advantage is 1 and one more minion makes it 2. | Minions in order of section 6. Stay in the zone. |
| `LET_PUSH` | Advantage ≥ 2, not match point, and another minion would push this round into a zone where `defenderSpawns ≥ attackerSpawns + 1` without a follow-up kill, or would leave us on the wrong side. | Reposition to the next zone. A hero attack or a fork is welcome. The heavy is not. |
| `HOLD_LINE` | We are in our pre-throne, or `stepsAgainst` ≤ 2, and `projected` is a loss if we leave. | Remove enemy minions here. Stand next to our melee. Do not chase a hero out of the zone. |
| `RETREAT` | Disadvantage ≥ 2, or the push is already decided against us. | The next Battle Zone, this turn if a card can reach it. One minion on the way only if it does not cost the move. |
| `FARM` | None of the above, and a minion is safe. | The minion. Leave the turn on a fork if the scores are within 3. |
| `ROTATE` | Double lane: our lane's advantage ≥ 2, it is not match point, and the other lane's `p` rises by more than this lane's `p` falls if we arrive. | Leave via a side zone. Fast Travel only through zones `ft` allows. The last step into a live Battle Zone is a walk. |
| `SET_UP` | A teammate's intent needs a modifier, a body on a line, or a piece, and our card does that better than it farms. | The space or unit that makes their action legal. Their kill is our score too. |
| `BLOCK` | A space we can stand is the corridor they Fast Travel through, or the landing square of a straight-line attack, and exposure there is less than 8. | Stand there. Do not block a teammate's line. |
| `SPAWN_CAMP` | Strength 9+. An enemy is off the board, we can reach a throne spawn they would use before they act, and our `deathsToLose` ≥ 2. | The spawn, or the space that attacks every spawn. If they can kill us there for a Life we cannot spend, the intent is illegal: do not go. |

**Card into intent.** The card is the first step of the intent, not the highest-initiative card that does something.

- `KILL` / `SETUP`: the card whose effective initiative beats the target's likely escape, and which still leaves a defense if they will hit back. On turn 4 the defense constraint drops (the leftover card has no action left).
- `CLEAR` / `PUSH` / `FARM`: a card that reaches the minion. Prefer Gold or a skill that defeats a minion over Red, so Red stays for a later turn [BGG 2432019, designer: attacks early in the round are rarely right].
- `LET_PUSH` / `RETREAT` / `ROTATE`: the movement card. Blue or Green, not Red or Gold, when either can Fast Travel [RB p.11].
- `SURVIVE`: the fastest card that leaves, or the card that keeps the answer in hand.
- `SET_UP` / `BLOCK`: the card whose text does the job; if the text is on a low-initiative card, that is a feature, because the board has stopped moving.
- Off the board: the lowest action-value card. Its only job is the respawn plus a move [RB p.7].

**Same-team commitments**, re-derived, not remembered. Sort friendly heroes by seat. Walk the priority list. A hero takes `KILL(u)` or `SETUP(u)` only if fewer than two friendlies already did (one, in 2v2: both may, because both must). The next hero takes `CLEAR` of a minion nobody has taken. Everyone else takes `FARM`, `ROTATE`, or `BLOCK`. Strength 1–6 skip this and each hero maximises alone; that is the designed gap, and it is how humans beat an uncoordinated bot (section 8).

---

## 4. Team play

Focus fire is the kill. "Hero kills require heroes teaming up and focusing on a single target. If one player is trying to get a kill, while the other is farming minions, you won't get a kill and you will fall behind on minions." [BGG 3033553, designer]. "Sometimes all it takes is to land a single discard skill early, and let your ally finish them off, while you farm." One-on-one attacks into a full hand mostly fail [BGG 3499405, BGG 3474863, community]. Two hits, one of them on turn 4, is the ordinary kill [BGG 2894296]. Damagers exist to force the discard that makes the second hit free [RB p.21].

**Who acts first in a same-team tie.** The team decides, no coin flip, and may not discuss reasons [RB p.7, p.4]. Order:

1. The hero who strips a card, places the modifier, or moves the target, before the hero who delivers the hit that needs it.
2. The hero who must leave a space, before a teammate whose action would push or swap them.
3. The hero whose straight-line attack needs the current geometry, before a teammate who would stand on the line.
4. Otherwise the hero whose intent is earlier in section 1.

With a human teammate the computer states a preference in the form the rulebook allows ("I would really like you to go first") and does not put a reason in the log [RB p.4].

**Keeping a defender.** The team's front — the hero in the enemy melee, or the one whose `exposure` is highest — keeps one card that answers the best attack still in the enemy pool, until turn 4. The hero behind them may spend theirs. A defender held for a teammate (strength 9) means: if an ally is on `SETUP` and will be attacked after the strip, the ally's spare is worth the kill, and our own farm waits. Retrieval that puts a card back into an empty hand is worth the kill value, not 2.5 [BGG 2899180].

**Protecting a low-hand ally.** `defensePool` empty, or `spare` < 0: they die to any attack that reaches them this turn, and to any "discard or be defeated." Stand so that we are the closer target, block the line, or move them if our card can. Do not ask them to walk into the Battle Zone to take a minion.

**Two-turn kills.** Turn A forces a discard (an attack they can answer, or a skill). They play one card a turn, so a hero who has discarded once enters turn 4 with one card, plays it, and has an empty HAND for the whole resolution. Any attack or discard-or-defeat on turn 4 then defeats them, at any initiative. A hero who has discarded twice is empty on turn 3 already. The finisher's job is reach, not a bigger number. The stripper, having spent the hit, goes back to minions [BGG 3033553, BGG 2894296]. Strength 9+ starts these. Strength 7–8 join one that is already on the board (the discard has happened). Strength 1–6 only take a kill that is already empty-handed.

**Zoning.** A hero who can stand where an enemy must choose between a minion and a hit should stand there even when the hit is unlikely. The lost minion is the point [BGG 3511053, BGG 3779890]. Zones that deny Fast Travel are in section 6.

**Splitting lanes.** 4v4 and 5v5 start split, as above. A lane with advantage ≥ 2 that is not at match point can spare one hero down the side zones. A lane at `HOLD_LINE` or `stepsAgainst` ≤ 2 keeps its bodies. Five heroes in one Battle Zone steal each other's minions and block each other's lines; the third attacker on a lane is a `ROTATE` or a `BLOCK`, not a third `KILL`. A 7-player table is not a format the rules ask for; the community game that worked was 4v4 with one person piloting two heroes on different lanes [BGG 3759456, 2026-09-06, community]. The engine plays even teams. Handicap cards are a real card in the deck when the match has them [RB p.20]; treat a Handicap that says the hero is defeated as a card that must never be the defense.

---

## 5. Initiative and card play

**What each colour is for**, as a default a hero with no playbook can use. The means are STRATEGY.md §1.3 [DATA]. The roles are Panetierre's, a credited playtester [BGG 3092335, 2023], and they match the dashboard bars [BGG 3463167, 2024].

| Colour | Initiative | Defense | What it is for |
|---|---|---|---|
| Gold | highest, usually above any Red [RB p.21] | lowest | Initiate a fight, finish an empty hand, take a minion, dodge one space. A turn-4 Gold is often the escape. Fast Travel with it only when respawning and no other card remains that can move. |
| Blue | second, around 9–11 | second, around 4–5 | The spare. Defend with it, or play it on turn 4 to reposition. Movement is usually the long one. |
| Red | middle, around 8–9 | highest, around 5–6 | The attack and the long move. Play it late. It is the card a one-shot has to beat. |
| Green | lowest, and Tier III is slower than Tier I | modest, around 3 | Act after the Golds and Reds have moved. Chase, set the next turn, take the minion they just left. |
| Silver | anywhere, often high | modest, no movement value [BGG 3463167] | The identity skill. The usual throw when a skill says "discard a card," because it cannot flee and often cannot attack. Never the Fast Travel card: it has no movement action unless its own text moves you. |

Vulnerable hands, readable because resolved and discarded cards are open [RB p.4]: Gold+Red both played (no attacks left); Blue+Red both played (no good defense left); only Gold and Silver, or only Green and Silver, remain [BGG 3092335]. "Most heroes will be able to block a basic attack with 3–4 of their 5 cards and a non-basic attack with 1–2. You can dodge a non-basic attack with 2–3 of your cards." [BGG 2521254, designer]. So a Gold attack finishes a thin hand; a Red attack threatens a full one.

**The spare is a property, not the word Blue.** The spare is the card in hand with the best defense against the attacks we can actually suffer, excluding the one card that is our remaining minion-kill if another defense exists. Blue is that card for a typical hero. It is the wrong card when a different colour's defense is higher (Takahide's Gold is the known case; a hero playbook will say so) or when the "defense" is a Block with no number, which answers only the attack type it names (Tigerclaw, Wasp's ranged block). Use `defensePreview` and `blockPreview` once we are the defender; until then use the catalog number plus items plus `minionMod`.

**Stat-bar reading, strength 3–6**, before exact card memory [BGG 3463167, Panetierre]. Compare the attack bar with the defense bar. Equal bars: their Blue answers our Red, their Green answers our Gold. A few dots of attack advantage: our higher-defense Red starts beating every card they hold. A few dots the other way: their Green answers our Red. Each defense item, each tier, and each point of `minionMod` counts like those few dots. "Even the lowest attack hero can threaten the best defense hero who's getting −3 defense from minions." Three initiative items overcome the widest printed initiative gap. Ranged attacks are a bit lower than the melee pattern. This is the designer's intended uncertainty: "Stat heuristics give you a 90% of an answer" [BGG 2887548, designer]. Strength 7+ replace the guess with the catalog once the upgrade history is down to one or two cards. By the end of round 1, four of the five cards have usually been seen [BGG 3463167].

**Round shape.** Plan the round, then pick this turn's card [BGG 2894296]. The default shape, which enemies know and will play around:

1. Turn 1–2: Green, Silver, Gold, spent on a minion, a skill, or a position. Gold initiates only when the target will be empty later or a teammate follows.
2. Turn 3: the approach. Green if we need to land next to someone who will try to leave. Red if the hit is real now.
3. Turn 4: Red if it is still in hand, otherwise Blue to reposition, otherwise Gold to finish or flee.

Leave at least two targets for an attack card, in case one moves [BGG 3559857, Naughty zoot, community]. A primary with no target at the moment we must play it is a Hold waiting to happen; play a different card unless this is the spare being cashed on turn 4 (`AI.md`: a quarter of actions were Holds, and Silver and Gold with nothing to do were the waste).

**After the reveal** the order is known and each enemy's card is known. Re-score movement and targets with those cards. Do not re-pick our card. Range is checked when the target is chosen; if they move afterwards the attack still happens [BGG 3439410, designer]. A ranged attack stays ranged when the target is adjacent, which is what Block cards look at [BGG 3478009, designer; RULINGS 19].

**Playing around their initiative.** To hit a hero who will leave, our effective initiative beats their likely escape (the rulebook's own example is Brogan's Gold before Dodger's Red) [RB p.12]. To step next to a hero who can hit us, our effective initiative loses to theirs, so we answer their final square. A cross-team tie is won by the coin's team, who then flip it [RB p.7; RULINGS 1]. Do not put a kill on a tie we lose. Same-team ties are section 4.

**Mixing.** The default shape is predictable, and a predictor who always knew the old engine's card won 60.9% of games (`AI.md`, the oracle exploiter, 89% of cards named). From strength 7, when the top two legal cards are within 2 coin-equivalents and neither is the only answer to P0 or P1, pick between them with `Random(mix(seed, decision.id, seat))`. Critical choices stay argmax (DESIGN.md: strength 10 is argmax, and defenses and lane-deciding moves never slip). The mix is a tie-break, not noise on the winning push.

**Passing and Holding.** Pass exists only with an empty hand, and then it is mandatory to announce [RB p.7]. Hold is legal on every card and is correct when every other action makes the position worse [RB p.12]. Hold is wrong when a safe minion is in reach: that is the wasted action worth 8.

---

## 6. Rules per `DecisionKind`

Direct scorers add every matching weight. Lookahead scorers add `ΔHEval` plus the `ADD` rules only.

### PLAY_CARD

G1. [PLAY_CARD] FROM 1 MODE ADD WHEN a card in hand wins the game on a line the engine can resolve (the push at `matchPoint`, the last Life, a printed alternate win) and the space is reachable THEN override that card WEIGHT +1000 SOURCE [RB p.3, p.8] [REASONED]

G2. [PLAY_CARD] FROM 1 MODE ADD WHEN after playing card C our `defensePool` cannot answer an attack that an enemy still able to act can deliver before we can leave, and another card either out-inits that attack or keeps the answer in hand THEN avoid C WEIGHT −(kill value from section 2) SOURCE [RB p.13, p.14] [BGG 3559857]

G3. [PLAY_CARD] FROM 1 MODE EVAL WHEN playing C drops `spare` below 1 before turn 4, and the play is not a P0, a P3 kill, or the marginal minion at `matchPoint` THEN avoid C WEIGHT −(the spare weight for this turn: 3 / 2 / 1.5) SOURCE [REASONED from RB p.7, p.15] [BGG 2896955]

G4. [PLAY_CARD] FROM 1 MODE ADD WHEN it is turn 1 or 2, an enemy hero is inside the reach of our Red, and Red is not a P3 kill THEN avoid Red WEIGHT −8 SOURCE [BGG 2432019, designer] [BGG 3559857]

G5. [PLAY_CARD] FROM 5 MODE EVAL WHEN the intent is `CLEAR`, `PUSH`, `FARM` or `SETUP` and Gold or a minion-skill can do it THEN prefer that card over Red WEIGHT +6 SOURCE [BGG 2899258] [BGG 2432019]

G6. [PLAY_CARD] FROM 1 MODE EVAL WHEN the intent needs a Fast Travel and a card other than Red or Gold has a movement action THEN prefer that other card WEIGHT +8 for keeping Red, +4 for keeping Gold SOURCE [RB p.11]

G7. [PLAY_CARD] FROM 3 MODE EVAL WHEN the target will move and our effective initiative does not beat their likely escape card THEN that card's attack value is 0 WEIGHT 0 for the attack, the card keeps its movement value SOURCE [RB p.12] [REASONED]

G8. [PLAY_CARD] FROM 3 MODE EVAL WHEN the intent steps next to an enemy who can still attack, and a lower-initiative card still reaches THEN prefer the lower initiative WEIGHT + the exposure we avoid SOURCE [BGG 3092335] [REASONED]

G9. [PLAY_CARD] FROM 7 MODE ADD WHEN our play wins only by a cross-team initiative tie and the coin shows the enemy team THEN avoid that card WEIGHT −(the value of the action) SOURCE [RB p.7] [RULINGS 1]

G10. [PLAY_CARD] FROM 9 MODE ADD WHEN a discard or chip on enemy `u` this turn leaves `defensePool(u)` empty on turn 4, and a friendly hero can reach `u` on turn 4 THEN prefer the stripping card; that choice is the `SETUP(u)` intent WEIGHT +(kill value − the minion we give up) SOURCE [BGG 2894296] [BGG 3033553]

G11. [PLAY_CARD] FROM 5 MODE EVAL WHEN an enemy's `defensePool` will be empty this turn (they play their last card, or they have none) and we can reach them THEN prefer the attack that reaches them over a non-winning minion WEIGHT +(kill value) SOURCE [RB p.13] [BGG 2894296]

G12. [PLAY_CARD] FROM 1 MODE ADD WHEN card C's primary has no legal target and C has no movement that reaches a minion or the next zone, and another card does THEN avoid C WEIGHT −8 SOURCE [AI.md known weaknesses] [BGG 2896501] [REASONED]

G13. [PLAY_CARD] FROM 5 MODE ADD WHEN `projected` advantage ≥ 2, this is not `matchPoint`, and the card's only job is a minion that would push this round into a zone we cannot stabilize THEN avoid that card WEIGHT −15 SOURCE [BGG 2899258]

G14. [PLAY_CARD] FROM 7 MODE ADD WHEN in 2v2 the teammate's intent is `KILL(u)` or `SETUP(u)` and we can reach `u` THEN the same intent WEIGHT +(kill value − our best minion) SOURCE [BGG 3033553]

G15. [PLAY_CARD] FROM 7 MODE ADD WHEN in 3v3 or bigger two friendlies are already on `KILL(u)` and a minion is unclaimed THEN prefer the minion card WEIGHT +8 SOURCE [BGG 3033553] [REASONED]

G16. [PLAY_CARD] FROM 7 MODE ADD WHEN every enemy attack card is already public (played or discarded) THEN the spare may be spent as an action WEIGHT + its action value, which the spare rule was withholding SOURCE [BGG 3092335] [RB p.4]

G17. [PLAY_CARD] FROM 7 MODE ADD WHEN the top two cards are within 2 and neither is the unique answer to G1 or G2 THEN choose by the decision seed WEIGHT 0 (tie-break only) SOURCE [AI.md oracle result] [REASONED]

G18. [PLAY_CARD] FROM 1 MODE ADD WHEN our `deathsToLose` ≤ 2 and the card is an even hero trade (they keep a defense, we spend ours) THEN avoid it WEIGHT −10 SOURCE [BGG 3080063] [BGG 3033553]

G19. [PLAY_CARD] FROM 9 MODE ADD WHEN our `deathsToLose` ≥ 3, theirs ≤ 1, and a card both farms a safe minion and threatens the empty-hand hero THEN prefer it WEIGHT +6 on top of the kill SOURCE [BGG 3033553] [REASONED]

G20. [PLAY_CARD] FROM 1 MODE ADD WHEN the hero is off the board THEN prefer the lowest action-value card that still has a movement or a Fast Travel WEIGHT +4 versus spending Red or the spare SOURCE [RB p.7] [REASONED]

G21. [PLAY_CARD] FROM 7 MODE ADD WHEN the intent is `ROTATE` and a movement card can reach the side zone this turn THEN prefer it over a minion in a lane we already win WEIGHT +(15 × the gain in the other lane's `p`) SOURCE [REASONED from RB p.20 and the map]

G22. [PLAY_CARD] FROM 1 MODE ADD WHEN the only option is Pass THEN Pass WEIGHT 0 SOURCE [RB p.7]

### ACT_FIRST

G23. [ACT_FIRST] FROM 5 MODE ADD WHEN one tied hero's action makes the other's target legal, dead, or unmodified THEN that hero first WEIGHT +(the kill or the minion the order unlocks) SOURCE [RB p.7] [BGG 2894296]

G24. [ACT_FIRST] FROM 3 MODE ADD WHEN one tied hero is in a space where an enemy still to act can defeat them, and their card can leave THEN that hero first WEIGHT +(their kill value) SOURCE [REASONED]

G25. [ACT_FIRST] FROM 5 MODE ADD WHEN one tied hero's straight-line attack needs a square the other would enter THEN the attacker first WEIGHT +8 SOURCE [REASONED from RB p.18 movement]

G26. [ACT_FIRST] FROM 1 MODE ADD WHEN none of G23–G25 apply THEN the hero whose intent is earlier in section 1 WEIGHT +2 SOURCE [REASONED]

### RESPAWN

G27. [RESPAWN] FROM 1 MODE EVAL WHEN a spawn is empty and no enemy still to act can defeat us there THEN prefer the empty throne spawn closest to the projected Battle Zone WEIGHT +4 per action of travel saved SOURCE [RB p.7] [BGG 2898177]

G28. [RESPAWN] FROM 3 MODE ADD WHEN every offered space is a defeat by an enemy still to act this turn THEN Decline WEIGHT +(kill value − 4) SOURCE [RB p.7] [REASONED]

G29. [RESPAWN] FROM 3 MODE EVAL WHEN a spawn can Fast Travel next turn toward the projected zone and another cannot THEN prefer the one that can WEIGHT +6 SOURCE [RB p.11]

G30. [RESPAWN] FROM 7 MODE ADD WHEN a spawn stands in the zone enemies must Fast Travel through, and G28 does not apply THEN prefer it WEIGHT +6 SOURCE [RB p.11]

G31. [RESPAWN] FROM 5 MODE EVAL WHEN the lane has just pushed or `projected` says it will THEN measure "closest" to the next Battle Zone, not the one we died in WEIGHT +8 for the right zone SOURCE [BGG 2899258]

### CHOOSE_ACTION

G32. [CHOOSE_ACTION] FROM 1 MODE EVAL WHEN the primary achieves the intent and `primaryPreview.noEffect` is null THEN prefer Primary over Hold, Clear, and a Fast Travel that drops the text WEIGHT +(the intent's value) SOURCE [RB p.7, p.9]

G33. [CHOOSE_ACTION] FROM 1 MODE ADD WHEN `primaryPreview.noEffect` is set THEN prefer Movement if it improves the space, else Hold WEIGHT −8 for the dead Primary SOURCE [RULINGS 4] [BGG 2896501]

G34. [CHOOSE_ACTION] FROM 1 MODE EVAL WHEN Fast Travel reaches a zone Movement cannot, the zone is closer to the projected Battle Zone, and the primary is not the intent THEN prefer Fast Travel WEIGHT +(4 per zone gained) SOURCE [RB p.11]

G35. [CHOOSE_ACTION] FROM 1 MODE ADD WHEN the card is Red or Gold and Movement or Primary takes the minion or the kill the intent named THEN avoid Fast Travel WEIGHT −8 on Red, −4 on Gold SOURCE [RB p.11]

G36. [CHOOSE_ACTION] FROM 1 MODE ADD WHEN a Fast Travel destination is not the same zone or an adjacent zone, or either zone holds an enemy hero or minion, including a reading of "ignore obstacles" that invents a new adjacency THEN avoid that destination WEIGHT −1000 SOURCE [RB p.11] [RULINGS 12] [BGG 3796272]

G37. [CHOOSE_ACTION] FROM 3 MODE EVAL WHEN a secondary Attack is offered and Primary cannot reach, and a minion or a P3 target is in that attack THEN prefer Attack WEIGHT +(the target's value) SOURCE [RB p.9]

G38. [CHOOSE_ACTION] FROM 3 MODE EVAL WHEN Clear is offered and an adjacent enemy piece is worth more than the attack's best minion or kill (section 2 piece floor, or the hero's `evalTerms`) THEN Clear while the next token stays above 1, and stop once it does not WEIGHT +(piece value − the attack we give up) SOURCE [RB p.13] [REASONED]

G39. [CHOOSE_ACTION] FROM 1 MODE ADD WHEN a minion we are allowed to take is in reach and the space is safe THEN avoid Hold WEIGHT −8 SOURCE [RB p.12] [AI.md]

G40. [CHOOSE_ACTION] FROM 1 MODE ADD WHEN every other option raises our exposure above the kill value or fizzles THEN Hold WEIGHT + the exposure avoided SOURCE [RB p.12]

G41. [CHOOSE_ACTION] FROM 5 MODE ADD WHEN the intent is `RETREAT`, `LET_PUSH` or `ROTATE` THEN Movement or Fast Travel toward the next zone outranks a minion that is not P4 WEIGHT +8 SOURCE [BGG 2899258]

G42. [CHOOSE_ACTION] FROM 5 MODE ADD WHEN the intent is `HOLD_LINE` and Primary removes an enemy minion in our Battle Zone THEN Primary outranks a hero who still has a defense WEIGHT + the minion's `W` share SOURCE [BGG 3168047]

### CHOOSE_SPACE

G43. [CHOOSE_SPACE] FROM 3 MODE EVAL WHEN two spaces differ in `exposure` THEN prefer the lower WEIGHT +(the difference) SOURCE [REASONED] [BGG 3463167]

G44. [CHOOSE_SPACE] FROM 1 MODE EVAL WHEN a space is in the projected Battle Zone and another is not THEN prefer the zone WEIGHT +4, plus 0.55 per space of difference SOURCE [BGG 2898177]

G45. [CHOOSE_SPACE] FROM 1 MODE EVAL WHEN a space is adjacent to a friendly melee or heavy, or outside every enemy ranged radius 2, and an enemy attack can reach the area THEN prefer it WEIGHT +2 per point of `minionMod` we gain SOURCE [RB p.14] [BGG 2898634]

G46. [CHOOSE_SPACE] FROM 1 MODE EVAL WHEN no enemy attack can reach either space THEN the modifier is only future value WEIGHT +0.5 per point of `minionMod` SOURCE [REASONED]

G47. [CHOOSE_SPACE] FROM 7 MODE ADD WHEN the space is in the zone an off-board or out-of-zone enemy must Fast Travel through, and exposure there is under 8 THEN prefer it WEIGHT +6 SOURCE [RB p.11]

G48. [CHOOSE_SPACE] FROM 7 MODE ADD WHEN the space is the landing or the line of an enemy straight-line attack, and it is not the line of a friendly one THEN prefer it WEIGHT +5 SOURCE [REASONED from card geometry the threat map already computes]

G49. [CHOOSE_SPACE] FROM 5 MODE ADD WHEN the space is on a teammate's straight-line path or on our own hero spawn they need THEN avoid it WEIGHT −8 SOURCE [REASONED]

G50. [CHOOSE_SPACE] FROM 5 MODE ADD WHEN from the space a card still in hand threatens both an enemy hero and an enemy minion THEN prefer it WEIGHT +3 on top of the better target SOURCE [BGG 3511053]

G51. [CHOOSE_SPACE] FROM 5 MODE EVAL WHEN `projected` says the Battle Zone moves this round THEN score distance to the next zone, not the current one WEIGHT +8 for being there, −8 for ending in the zone we are about to leave SOURCE [BGG 2899258] [BGG 3498829]

G52. [CHOOSE_SPACE] FROM 3 MODE ADD WHEN one step leaves a space where an enemy still to act defeats us THEN that step outranks a step toward a minion WEIGHT +(kill value) SOURCE [REASONED]

G53. [CHOOSE_SPACE] FROM 9 MODE ADD WHEN the intent is `SPAWN_CAMP` and the space attacks every throne spawn they can use, and they cannot defeat us there THEN prefer it WEIGHT +4 plus their tempo if they must Decline SOURCE [RB p.7] [REASONED]

G54. [CHOOSE_SPACE] FROM 1 MODE EVAL WHEN we are pushing or placing an enemy THEN prefer the square inside our ranged radius 2 and adjacent to our melee, off their next minion, and against an obstacle when a public effect punishes a stop WEIGHT +2 per modifier point we put on them SOURCE [RB p.14, p.18]

G55. [CHOOSE_SPACE] FROM 7 MODE ADD WHEN the intent is `ROTATE` THEN prefer a space in the side zone that borders the destination lane, over a space deeper in a lane we already win WEIGHT +6 SOURCE [REASONED from the Across the River zone borders]

G56. [CHOOSE_SPACE] FROM 1 MODE EVAL WHEN we have no target this turn THEN a central space in the zone we care about WEIGHT +1 SOURCE [BGG 2897477]

### CHOOSE_UNIT

G57. [CHOOSE_UNIT] FROM 1 MODE EVAL WHEN the choice is an attack target and the target is a hero THEN value = `P(defeat) × kill value + (1 − P(defeat)) × the spare they will throw` WEIGHT that value, and P is 0 when our total fails every card they can still defend with SOURCE [RB p.14] [BGG 2894296]

G58. [CHOOSE_UNIT] FROM 1 MODE EVAL WHEN the target is a minion THEN value = 2 coins (4 if heavy) + the change in `60 × W` from the new margin WEIGHT that value SOURCE [RB p.13, p.8] [REASONED]

G59. [CHOOSE_UNIT] FROM 1 MODE ADD WHEN a hero target's `P(defeat)` is below 0.4, no teammate acts on them after us, and their `defensePool` is not empty, and a minion is legal THEN prefer the minion WEIGHT +(minion value − the poke) SOURCE [BGG 3499405] [BGG 3033553]

G60. [CHOOSE_UNIT] FROM 5 MODE ADD WHEN a teammate acts later this turn on hero `u` and our hit discards a card `u` would have used THEN prefer `u` even if we do not defeat them WEIGHT +(the card they lose, at the spare weight, plus half the kill if the follow-up then defeats them) SOURCE [BGG 2894296] [RB p.21]

G61. [CHOOSE_UNIT] FROM 1 MODE ADD WHEN the minion is the last enemy minion, or the lone heavy, and section 2 allows the push (a follow-up defender kill exists when the new zone spawns more minions for them) THEN prefer it over any hero who does not end the game WEIGHT +(the push's `W` swing) SOURCE [RB p.8] [BGG 2899258]

G62. [CHOOSE_UNIT] FROM 3 MODE ADD WHEN the enemy ranged minion's radius covers a friendly hero and another melee does not change `W` THEN prefer the ranged minion WEIGHT +4 SOURCE [RB p.14] [REASONED]

G63. [CHOOSE_UNIT] FROM 3 MODE ADD WHEN an enemy melee or heavy is adjacent to us or to a hero we attack next THEN prefer removing it over a far minion of the same battle value WEIGHT +2 per modifier point SOURCE [RB p.14]

G64. [CHOOSE_UNIT] FROM 7 MODE ADD WHEN a teammate's intent already names minion `m` and another enemy minion is in reach THEN avoid `m` WEIGHT −8 SOURCE [BGG 2899258]

G65. [CHOOSE_UNIT] FROM 7 MODE ADD WHEN this enemy has already discarded a card this round and their `defensePool` is thin THEN prefer them over a fresh target of equal kill value WEIGHT +6 SOURCE [BGG 2894296] [AI.md]

G66. [CHOOSE_UNIT] FROM 1 MODE EVAL WHEN the choice moves, pushes or swaps a unit THEN score the resulting squares with the CHOOSE_SPACE rules WEIGHT the space delta SOURCE [RB p.18]

G67. [CHOOSE_UNIT] FROM 5 MODE ADD WHEN the target is one of several bodies and defeating any body defeats the hero THEN prefer the body with the worst `minionMod` for them WEIGHT +4 SOURCE [REASONED]

G68. [CHOOSE_UNIT] FROM 9 MODE ADD WHEN the intent is `SETUP(u)` and `u` is legal THEN target `u` rather than a richer minion WEIGHT +(kill value on turn 4, discounted by 0.7 if our finisher's reach is only possible rather than certain) SOURCE [BGG 3033553] [REASONED]

### CHOOSE_TOKEN

G69. [CHOOSE_TOKEN] FROM 3 MODE EVAL WHEN the token is an enemy piece THEN prefer the one with the highest section-2 piece value that we can remove WEIGHT +(that value) SOURCE [REASONED]

G70. [CHOOSE_TOKEN] FROM 5 MODE ADD WHEN the token is ours and a card still in hand needs it on the board THEN avoid removing it unless it blocks a P0 line WEIGHT −(its piece value + 4) SOURCE [REASONED]

G71. [CHOOSE_TOKEN] FROM 1 MODE ADD WHEN the next token is worth under 1 and a Stop option exists THEN Stop WEIGHT +0.5 versus clearing junk SOURCE [RB p.13]

### CHOOSE_CARD

G72. [CHOOSE_CARD] FROM 1 MODE EVAL WHEN we must discard THEN throw the lowest action-value card, and keep the only card that answers a lethal threat still coming this round if another card can be thrown WEIGHT −(the value of the card we keep) SOURCE [RB p.10] [BGG 3092335]

G73. [CHOOSE_CARD] FROM 1 MODE EVAL WHEN we retrieve THEN take the best defense if `spare` ≤ 0, otherwise the highest action value still useful this round WEIGHT +(the card's value) SOURCE [BGG 2899180] [REASONED]

G74. [CHOOSE_CARD] FROM 7 MODE ADD WHEN we name a colour an enemy will discard next turn, after they have played THEN name the colour they are most likely to still be holding, not the colour they are most likely to play WEIGHT +4 when the pool makes that unique SOURCE [REASONED from the timing "after playing cards"]

G75. [CHOOSE_CARD] FROM 7 MODE ADD WHEN the enemy chooses which card to reveal and we are guessing the colour THEN guess the colour they mind losing least, and from strength 7 mix among colours within 2 WEIGHT +2 for the reluctant card SOURCE [REASONED from RB p.4 hidden hand plus the enemy's incentive]

G76. [CHOOSE_CARD] FROM 5 MODE EVAL WHEN we swap or copy THEN keep or copy the card the intent needs, not the higher printed number WEIGHT +(intent value) SOURCE [REASONED]

### DEFEND

G77. [DEFEND] FROM 1 MODE ADD WHEN no card has `defensePreview` ≥ the attack total and no `blockPreview` holds, and a fork agrees THEN Decline. A short defense discards the card and the hero is still defeated WEIGHT −1000 on every failing card SOURCE [RB p.14]

G78. [DEFEND] FROM 1 MODE EVAL WHEN several cards meet the total or block the attack THEN throw the spare first, then the lowest remaining action value, and keep the card the intent still needs this round WEIGHT −(the value of a better card we would have thrown) SOURCE [RB p.14] [BGG 2896955]

G79. [DEFEND] FROM 3 MODE ADD WHEN two sufficient cards are within 2 of action value and one of them has defense text that still does something (immunity, a retrieve, a counter-discard) THEN prefer that card WEIGHT +3 SOURCE [RB p.14]

G80. [DEFEND] FROM 1 MODE EVAL WHEN it is turn 4 and the card is the last one in hand THEN its action value is the 0.8 insurance only, so any success is correct WEIGHT +0 beyond G78, and G3 does not apply SOURCE [REASONED from RB p.7]

G81. [DEFEND] FROM 9 MODE ADD WHEN a sufficient defense exists and all of the following hold: our `deathsToLose` ≥ 2, our level ≤ 3, the card's remaining value this round exceeds Life + coins + 4 tempo, and the respawn square is the next fight THEN Decline WEIGHT +(card value − Life − coins − 4) SOURCE [RB p.13] [BGG 2432019]

G82. [DEFEND] FROM 1 MODE ADD WHEN a Block's condition holds and it is the cheapest success THEN prefer the Block over a numbered card that is our last attack WEIGHT +4 SOURCE [RB p.14] [RULINGS 20]

G83. [DEFEND] FROM 1 MODE EVAL WHEN a lower card meets the total THEN avoid spending a higher one WEIGHT −(the difference in action value) SOURCE [RB p.14: equal or higher succeeds]

G84. [DEFEND] FROM 3 MODE ADD WHEN `defensePreview` is null THEN one-ply the defense text and use the result in G77–G83 WEIGHT the fork's score SOURCE [RULINGS 23]

### CHOOSE_OPTION

G85. [CHOOSE_OPTION] FROM 3 MODE EVAL WHEN the bullets do different things and no preview ranks them THEN one-ply each bullet and take the best `HEval` WEIGHT the delta SOURCE [DESIGN.md lookahead]

G86. [CHOOSE_OPTION] FROM 5 MODE ADD WHEN one bullet is the intent and the other is a lesser farm THEN prefer the intent WHEN their `HEval` is within 4 WEIGHT +4 SOURCE [REASONED]

G87. [CHOOSE_OPTION] FROM 1 MODE ADD WHEN we are choosing and a bullet's preview is a no-effect THEN avoid it WEIGHT −8 SOURCE [RULINGS 38]

G88. [CHOOSE_OPTION] FROM 7 MODE ADD WHEN the enemy is choosing THEN score the set as the option that is least bad for them, not the option that is best for us WEIGHT the min, not the max SOURCE [REASONED]

### CONFIRM

G89. [CONFIRM] FROM 1 MODE EVAL WHEN the optional step's `ΔHEval` is ≥ 0 THEN Yes WEIGHT the delta SOURCE [RB p.10 "you may"]

G90. [CONFIRM] FROM 1 MODE ADD WHEN Yes spends our last answering defense or walks into a defeat THEN Decline WEIGHT −(kill value) SOURCE [RB p.14]

G91. [CONFIRM] FROM 5 MODE ADD WHEN Yes is a repeat against a second target THEN Yes only when that target is a P3 kill or a P4 minion WEIGHT +(that value) SOURCE [RULINGS 37]

### CHOOSE_NUMBER

G92. [CHOOSE_NUMBER] FROM 1 MODE EVAL WHEN the number is a distance THEN choose the distance that lands on the best CHOOSE_SPACE square, which is often not the maximum WEIGHT the space's score SOURCE [REASONED]

G93. [CHOOSE_NUMBER] FROM 1 MODE ADD WHEN every positive distance is worse than staying THEN choose 0 or Decline WEIGHT +(the exposure avoided) SOURCE [REASONED]

G94. [CHOOSE_NUMBER] FROM 5 MODE ADD WHEN a longer push crosses into a zone where a public effect punishes the cross, or puts them in our modifiers THEN prefer that distance over a shorter one of equal modifier WEIGHT +4 SOURCE [RB p.18] [REASONED]

### REMOVE_MINION

G95. [REMOVE_MINION] FROM 1 MODE ADD WHEN the heavy is not the last remaining option THEN do not pick it; the engine already withholds it until last WEIGHT 0 (legality) SOURCE [RB p.8]

G96. [REMOVE_MINION] FROM 1 MODE EVAL WHEN one of our minions is in reach of an enemy hero next round and another is not THEN remove the exposed one, so the kill and its coins are not handed over WEIGHT +4 for keeping the sheltered minion SOURCE [RB p.8, p.13] [REASONED]

G97. [REMOVE_MINION] FROM 3 MODE ADD WHEN one minion is adjacent to a friendly hero and another is not THEN keep the adjacent one WEIGHT +2 SOURCE [RB p.14]

G98. [REMOVE_MINION] FROM 3 MODE ADD WHEN our ranged minion's radius covers an enemy hero and a melee does not THEN keep the ranged minion WEIGHT +4 SOURCE [RB p.14]

G99. [REMOVE_MINION] FROM 7 MODE ADD WHEN an enemy's possible cards gain value from an empty spawn in this Battle Zone THEN keep the minion that sits on a spawn WEIGHT +4 SOURCE [REASONED from public card text]

### MINION_RETURN

G100. [MINION_RETURN] FROM 1 MODE EVAL WHEN several shortest paths exist THEN end adjacent to our heroes and outside enemy reach WEIGHT +2 SOURCE [RB p.18]

G101. [MINION_RETURN] FROM 7 MODE ADD WHEN one end square lies on an enemy straight-line attack and another does not THEN avoid the line WEIGHT +2 SOURCE [REASONED]

G102. [MINION_RETURN] FROM 1 MODE ADD WHEN the lane will push before this minion matters THEN any legal square WEIGHT within ±1 SOURCE [RB p.8]

### UPGRADE_CARD

G103. [UPGRADE_CARD] FROM 1 MODE EVAL WHEN several lowest-tier colours can be upgraded THEN upgrade the colour whose best (kept card + item) is highest; with nothing else known, Red WEIGHT +4 for Red over a tie SOURCE [RB p.15] [REASONED]

G104. [UPGRADE_CARD] FROM 5 MODE ADD WHEN `HeroStyle.upgradeOrder` names a colour THEN follow that order ahead of G103's Red default WEIGHT +6 SOURCE [DESIGN.md `HeroStyle`]

G105. [UPGRADE_CARD] FROM 7 MODE ADD WHEN our Gold is slower than every enemy Gold and an initiative item would pass them, or our Green is faster than every enemy Green and skipping initiative would restore that THEN the colour that fixes it outranks a generic Red WEIGHT +4 SOURCE [BGG 2908021]

G106. [UPGRADE_CARD] FROM 1 MODE ADD WHEN enemy upgrades this round are simultaneous and hidden THEN ignore them; items already on their dashboards from earlier rounds are fair WEIGHT 0 (a constraint) SOURCE [RB p.15] [BGG 3590819, designer]

### KEEP_UPGRADE

G107. [KEEP_UPGRADE] FROM 1 MODE EVAL WHEN one card of the pair is clearly the one the intent plays THEN keep that card and take the item on the other WEIGHT +(the card gap) SOURCE [RB p.15–16]

G108. [KEEP_UPGRADE] FROM 1 MODE EVAL WHEN the item is Defense THEN its value is 0.8 per card that has a defense number; prefer the pair that gives it when we have been defeated or our defense bar is below the enemy attack bar WEIGHT that value SOURCE [RB p.16] [BGG 3463167]

G109. [KEEP_UPGRADE] FROM 1 MODE EVAL WHEN the item is Attack THEN 1.0 per attack card, but FROM 7 any point that does not change whether some enemy card can answer us is worth 0.3 instead of 1.0 WEIGHT the capped value SOURCE [BGG 2908021, RelentIess] [BGG 3474863]

G110. [KEEP_UPGRADE] FROM 5 MODE EVAL WHEN the item is Initiative THEN +1.2 if it makes our Gold strictly faster than every enemy Gold or our Green strictly slower than every enemy Green; −1.0 if it drops us into a tie cluster in the middle of the pack; otherwise +0.4 per card WEIGHT that value SOURCE [BGG 2908021] [BGG 2432019]

G111. [KEEP_UPGRADE] FROM 1 MODE EVAL WHEN the item is Movement, Range or Radius THEN 0.7 per card with that icon, plus 2 if movement is the reason we are more than one action from the Battle Zone, or range is the reason an attack misses by one SOURCE [BGG 2898177] [REASONED]

G112. [KEEP_UPGRADE] FROM 5 MODE ADD WHEN the two cards are within 2 and we are the hero standing in the enemy melee THEN keep the higher defense; when we are the hero behind, keep the higher attack WEIGHT +2 SOURCE [BGG 2908021, Saroule14] [REASONED]

---

## 7. Difficulty

The layers are DESIGN.md's, not a second system. What a human at that stage actually does is the content of the slips. Slips draw from the top three options by this file's own score, never from an option more than 12 coin-equivalents worse at strength 1–2 or 5 worse at strength 3–4, and never from a defense that succeeds or a push that is one legal minion from winning the game. Those two feel broken rather than beginner.

**Strength 1–3 (no threat map below 3; no lookahead below 3; no hero playbook; no turn plan).** This is a player who has read the colours and not much else. They farm a minion they are standing next to, walk toward the Battle Zone, and play a high-initiative card because it "goes first." They do not compute `projected`. Natural mistakes, which the slip budget should actually produce (15–25% at 1–2, 8–12% at 3):

- Stand next to an enemy melee minion, or inside an enemy ranged radius 2. They have not learned modifiers [RB p.14].
- Fast Travel on Red, because it is the biggest movement number [the RB p.11 warning is the lesson they have not learned].
- Attack the hero in front of them who still holds a Blue, and leave the minion [BGG 3499405, the beginner pattern].
- Spend Red on turn 1 while an enemy is in reach [BGG 3559857, the canonical bot failure].
- Play Silver or Gold with no target and then Hold [AI.md].
- Take the centre heavy the moment it is alone [BGG 2899258].
- Walk toward a heavy that is still immune, burning the turn on the approach. The engine will not let them target it; the mistake is the walk.
- Both heroes pick different targets.
- Defend with the first card that works, which is often Red.
- On a level-up, pick at random and lean toward the higher Attack number.

They still push when the last minion is adjacent, and they still defend when a card succeeds. Strength 3 adds the threat map, so the modifier and exposure mistakes shrink, and adds lookahead for options whose text the weights cannot read.

**Strength 4–6 (threat map, lookahead, and from 5 the hero playbooks and turn plans; slips 8–12% then 3–5%).** An honest greedy player. They keep a spare, they will not Fast Travel on Red or Gold, they project the Minion Battle, and they leave a square that kills them this turn. They do not read the enemy's possible cards, so they lose initiative races that are not obvious, they do not set up a two-turn kill, and they do not coordinate: each hero maximises its own intent. Upgrades use G103–G112 without the threshold cap in G109. This is "plans the turn, not the round."

**Strength 7–8 (enemy possible cards, focus fire, card counting).** They name which card you are likely holding, they put two heroes on one target, they block a Fast Travel corridor when it is cheap, and they mix two close cards so the default shape is not a script. They still will not start a `SETUP` that pays off next turn, and they will not hold a defender specifically so a teammate can be the one who lives.

**Strength 9–10.** Two-turn strips, the priced death, the defender kept for an ally, lane timing (`LET_PUSH`, post-push reposition), spawn-camp denial and the occasional camp. Strength 10 reads hands and otherwise plays the same rules at argmax, with G17's seeded tie-break still on. No slip on a P0 or a defense.

A seeded personality at strength 1–6 (aggressive: hero targets +20% of their value; farmer: minions +20%) keeps two bots from playing the identical game. It may not push a choice past the slip caps above.

---

## 8. Anti-exploitation

Humans beat this game's bots in the same handful of ways, and the old search player lost to them the same way (`AI.md`, the owner's 3v3 games: hero kills and the lane, not a tactic the playouts could see across a turn boundary).

| What humans do | Where it shows up | The guard |
|---|---|---|
| Put two heroes on one target, strip on turn 2–3, hit on turn 4 | BGG 2894296, 3033553, 3474863; half the heroes the old 3v3 player lost were empty-handed a turn later | G10, G11, G60, G65, G68. Empty-pool exposure is in `HEval` from strength 3 so we also fear it. |
| Learn the card script and play the answer | The oracle won 60.9% naming 89% of cards (`AI.md`) | G17. Also G12, so we do not play Silver on an empty turn just because the shape says "Silver early." |
| Bait Red on turn 1, then kill the hero who has no defense left | BGG 3559857, the Queller critique | G2, G4. Strength 1–2 are allowed to fall for it; that is the beginner. |
| Stand in the Fast Travel corridor, especially after a respawn | RB p.11 calls this one of the key strategies; the old player never modelled it | G30, G47, G55. |
| Camp the throne spawn, or refuse to respawn into the camp | Not a written guide; it is the rule [RB p.7] plus the old player's gap | G28, G53. We camp only from strength 9 and only when we survive it. |
| Hand us the centre heavy and take the next zone with a two-minion advantage | BGG 2899258 | G13, G61. |
| Push, then fight the next zone while we are still on the wrong side | BGG 2899258, 3498829; post-push reposition was an explicit old-player gap | G31, G41, G51. |
| Ignore our tokens, or Clear the one that matters, because we priced every token the same | `AI.md` set-up heroes | Section 2's piece floor, G38, G69. Hero `evalTerms` replace the floor. |
| Leave a won lane and cross the bridge | The 4v4 session pattern [Reddit, 2022, `r/boardgames` v5yeu2] | G21, G55, the 2–2 split. |
| Stall, because a bot that always takes a minion will push itself into a bad zone | BGG 3168047. Experienced players do not actually stall for long; Pity coins end a true stall | `LET_PUSH` is our version of the patient line. We do not stall when `stepsAgainst` ≤ 2: one mistake loses [BGG 3168047, designer]. |
| Play an unusual card so a static script mis-reads the hand | `AI.md`: the old opponent model was worse than a uniform guess | The possible pool, with a floor of probability on every legal card, from strength 7. Never a single sample. |
| Ask the bot, in effect, to peek | One Queller suggestion was to redraw the bot's card after seeing the humans' [BGG 3559857] | Forbidden. Facedown cards are hidden [RB p.4]. Strength 10's `knowHands` is the owner's switch, not a peek at the card just played beyond what `knowHands` already grants. |

**What we copy from the bots, and what we refuse.**

- Queller for Guards (BGG 3559857, Quitch, 2025) draws a random colour and looks up a role table. Pushers take minions, snipers shoot. It ignores Life and waves on purpose, so a person can run it quickly. Random cards plus no lane is the strength-1 failure mode. Do not copy it. The role idea survives as section 3's intents: a pusher's intent is `CLEAR` / `PUSH`, a finisher's is `KILL` on turn 4.
- Bots of Atlantis (BGG 3607708, electro_cute, file `BOTS OF ATLANTIS.pdf` v1.0, 2026-01-21, 220 downloads) flips a card and then walks a per-hero conditional table. Useful structure, taken from the thread because the PDF itself is behind the site challenge: a "save your skin" rule (our P1), a "turn 4 targeting" rule (our G11), and Fast Travel toward a prioritised space rather than a random legal one (our G34, G44). The author's own leftovers match our guards: Fast Travel still feels wrong to them (2026-04-17, and they were rewriting card selection on 2026-10-13), openings are predictable (our G17), and in 3v3 a bot "keeps running into walls" — Wasp barely moving — which is G44 and G12, goal-directed movement, not a random step. Per-hero tables are the hero playbooks, not this file.
- War of the Ring's Queller (BGG 1692716) and Stonemaier-style automas are first-match priority lists. Guards' real decisions are trades (the minion or the hero, the push or the position) [BGG 3511053: the best move is sometimes to take neither]. Score every option as a sum of coin-equivalents, the way an Infinite Axis utility does [Dave Mark, *Behavioral Mathematics for Game AI*, 2009; Mark and Dill's utility-AI work]. Keep a hard override for exactly two cases: the action that wins the game, and a defense that cannot succeed.

---

## 9. Sources

Rule facts are from the pinned 2nd-printing rulebook, quoted in `app/src/main/assets/catalog/rules-reference.json` (pages are the printed pages), and from `core/game/RULINGS.md`. Map counts are from the shipped JSON under `app/src/main/assets/catalog/maps/`, read 2026-11-20. The ladder is `rules-reference.json` `ladder.levels`. Printed colour means are STRATEGY.md §1.3 [DATA], computed there from the 632-card catalog and not recomputed here. Measured weights from the old player are `core/game/ai/AI.md` (the 0.35 push potential, the empty-hand losses, the oracle). Dashboard advice, where a rule leans on it, is the catalog `advice` field.

Community and designer threads were read 2026-11-20 through `api.geekdo.com/api/articles?threadid=<id>`. The Strategy forum was listed in full (83 threads, forum id 67). "Designer" is the BGG account `nichik`. Playtesters are people credited in the rulebook (RB p.22): Panetierre and anwei are.

### Designer (`nichik`)

- [BGG 2899258](https://boardgamegeek.com/thread/2899258) Ruling the Waves, 2022-07-13. Push potential, openings, the heavy, stranding. GoatRogan (playtester) adds the beach team-fight note.
- [BGG 3033553](https://boardgamegeek.com/thread/3033553) win-condition statistics, 2023-02-22. Kills 53.23%, the focus-fire requirement, last-push Life asymmetry.
- [BGG 3491194](https://boardgamegeek.com/thread/3491194) what the three endings mean; about 30% of games reach the last wave.
- [BGG 3168047](https://boardgamegeek.com/thread/3168047) stalling, 2023-10-09. Defenders are at a disadvantage; the turn-3 take, turn-4 reposition line; Pity coins as the only hard clock.
- [BGG 3080063](https://boardgamegeek.com/thread/3080063) comebacks, 2023-05-08. "Comebacks happen all the time." Higher-level heroes are worth more.
- [BGG 3472633](https://boardgamegeek.com/thread/3472633) the level-5 power bump; Ultimates in about 2–4% of games.
- [BGG 2432019](https://boardgamegeek.com/thread/2432019) 2020. Attacks early are rarely right; the deliberate late death; initiative items.
- [BGG 2521254](https://boardgamegeek.com/thread/2521254) how many cards block a basic and a non-basic attack.
- [BGG 2887548](https://boardgamegeek.com/thread/2887548) stat heuristics are supposed to leave a residue of uncertainty.
- [BGG 3590819](https://boardgamegeek.com/thread/3590819) 2025. The tucked item is hidden; level-up is simultaneous.
- [BGG 3499394](https://boardgamegeek.com/thread/3499394) 2025-04-21. A tied double-lane zone count continues.
- [BGG 3439410](https://boardgamegeek.com/thread/3439410) range is checked when the target is chosen.
- [BGG 3478009](https://boardgamegeek.com/thread/3478009) a ranged card stays ranged when adjacent.
- [BGG 3527919](https://boardgamegeek.com/thread/3527919) 2025-06-16 and 2025-06-19. Narrow Passages' side zones are 5 against 5 if the tower icon is ignored, and the tower icon is ignored. Contradicts the house idea of adding a minion there.
- [BGG 2842314](https://boardgamegeek.com/thread/2842314) 2022. He does not believe a solo mode can match the team game. The same position is on the [Gamefound FAQ](https://gamefound.com/en/projects/wolffdesigna/guardsofatlantis2new/faq) (Wolff Designa, read 2026-11-20): no solo mode, 4 or 6 players recommended, two players each pilot two heroes and the team element changes.

### Playtesters and the long essays

- [BGG 2894296](https://boardgamegeek.com/thread/2894296) anwei, 2022-07-04. Sequencing and card kills. Plan rounds. The Brogan example: the hit has to include turn 4, and the hero with no slow card cannot chase.
- [BGG 3092335](https://boardgamegeek.com/thread/3092335) Panetierre, 2023. Roles of the colours. The vulnerable hands. Gold initiates and finishes; Blue is the spare; Green acts last; Red is the card you are scared to have spent.
- [BGG 2896501](https://boardgamegeek.com/thread/2896501) Panetierre, 2022-07-08. Actions and tempo. A fizzled action is a quarter of the round. Tier III is where skills start winning games.
- [BGG 2896955](https://boardgamegeek.com/thread/2896955) Panetierre, 2022-07-09. The hand as hit points. One discard means an empty hand on turn 4.
- [BGG 2899180](https://boardgamegeek.com/thread/2899180) Panetierre, 2022-07-13. Attacks spend the scarce action; a discard skill does not. Heals and overheals.
- [BGG 2897477](https://boardgamegeek.com/thread/2897477), [2898177](https://boardgamegeek.com/thread/2898177), [2898634](https://boardgamegeek.com/thread/2898634) Panetierre, 2022. Position. Three spaces is an action, five is two. A kill costs the team about two actions and the victim about three to return. Spawned minions swing a square's modifier from −3 to +2.
- [BGG 3463167](https://boardgamegeek.com/thread/3463167) Panetierre. Predicting values from the stat bars, tiers, items and minions. Cool Red versus Warm Red. The player aid that summarises it is BGG file page [295712](https://boardgamegeek.com/filepage/295712) (2025-02-23); the chart built on the earlier essay is file page [254543](https://boardgamegeek.com/filepage/254543) (bcsj, 2023-02-21). Neither file could be downloaded through the public API on 2026-11-20.
- [BGG 2908021](https://boardgamegeek.com/thread/2908021) level-up. Panetierre, GoatRogan (playtester), RelentIess, Saroule14. Initiative at the extremes; attack items only up to a threshold; half the time you are buying the item.

### Other community

- [BGG 3499405](https://boardgamegeek.com/thread/3499405) 2025-04-20. Holding attacks until turn 4 is the beginner answer to 1v1. The grown-up answer is two heroes on one target, and a discard can be worth it without a kill (Deiflex, AegisToast, and others).
- [BGG 3474863](https://boardgamegeek.com/thread/3474863) 2025-03-09. New groups over-farm. Two attacks on one hero are "almost an automatic kill." Attack items are a deterrent, not a minion tool. Throne pushes are rarer than Life and the last wave, and the threat of the throne still decides games.
- [BGG 3511053](https://boardgamegeek.com/thread/3511053) 2025-05-12. The fork. Skills get used once positioning is solved; a lot of winning play is still movement and minions (including a poster with mostly 5v5 games).
- [BGG 3498829](https://boardgamegeek.com/thread/3498829) 2025-04-19. When the enemy has given up the lane, leave someone slow on the last minion and move. The heavy's 4 coins versus the next zone is a real choice.
- [BGG 3779890](https://boardgamegeek.com/thread/3779890) Cryomancy (Mad_Mike), 2026-10-09, comments through 2026-11-04. Six jobs: Zoner, Initiator, Finisher, Support, Pusher, Disruptor. The zoner's slow attack is a territory tool. The finisher waits until turn 3–4. The pusher's extra minion frees a teammate's attack. A Garrus round shape in the comments (Green, Howl, Red, Gold) is hero material, not a general rule.
- [BGG 3790096](https://boardgamegeek.com/thread/3790096) Cryomancy, 2026-10-29. Cutter. General points that survived the hero filter: kill pressure creates wave pressure; an enemy-choice card is worth the minimum they will allow; straight lines die to one body in the way.
- [BGG 3796272](https://boardgamegeek.com/thread/3796272) McGravityHammer, 2026-11-10. A proposed Silverarrow opening that Fast Travels through a non-adjacent zone because her Silver ignores obstacles. The thread's correction is the rule: Fast Travel is a placement into the same or an adjacent zone, and "ignore obstacles on a movement action" does not apply to it [RB p.11, RULINGS 12].
- [BGG 3615591](https://boardgamegeek.com/thread/3615591) 2025-11-21. The 4/8/10-player mats. Superseded for minion counts by BGG 3527919 and the map JSON: equal spawns, tower icons are decoration.
- [BGG 3759456](https://boardgamegeek.com/thread/3759456) 2026-08-27. A 4v4 with one double-pilot, heroes on separate lanes, played as two small fights. Not a rules change.
- [BGG 3607708](https://boardgamegeek.com/thread/3607708) electro_cute, 2025-11-08 through 2026-11-20. Bots of Atlantis. Versions through 2026-01-21 playtested all 32 heroes; the author was still rewriting card selection on 2026-10-13. Testers: 2v2 feels right, 3v3 bots get stuck and still win, openings are predictable, Fast Travel is the weak part.
- [BGG 3559857](https://boardgamegeek.com/thread/3559857) Quitch, 2025-08-15. Queller. AegisToast and Naughty zoot describe what a real opponent would have to do, which is most of sections 3 and 5. The peek-after-reveal idea is rejected in section 8.
- Reddit [`r/boardgames` 1akgd6v](https://www.reddit.com/r/boardgames/comments/1akgd6v/guards_of_atlantis_ii_help/), GalahadB, 2024-02-06. Initiative order Gold, Blue, Red, Green; a discard means a turn-4 death if they kept no escape.
- Reddit [`r/boardgames` 1mrqkx1](https://www.reddit.com/r/boardgames/comments/1mrqkx1/guards_of_atlantis_2_higher_star_heroes/), avrosky, 2025-08-18. The same colour roles, from a group with a few dozen plays. Silver is the card you throw.
- Reddit [`r/boardgames` 1ks199m](https://www.reddit.com/r/boardgames/comments/1ks199m/question_about_the_card_constrict_in_guards_of/), AegisToast, 2025-05-21. Two minion-kills is the default; a third is a real push edge. Hero-specific beyond that.
- Reddit [`r/boardgames` v5yeu2](https://www.reddit.com/r/boardgames/comments/v5yeu2/guards_of_atlantis_ii_4v4_session/), 2022. 4v4 splits 2–2, and a hero leaves a lane that is safe by a couple of minions to cross.

### Not used as rules

Meeple Mountain's review describes defense and "spawn tokens" loosely enough to be the wrong game; it is not cited above. First-printing spotlights are not used. The 1st-printing tower is not a 2nd-printing piece [BGG 3527919].

### Gaps

- The Bots of Atlantis PDF and the Queller PDF were not readable from this machine (the file API returns metadata only; the file page returns the site challenge). Bot behaviour in section 8 is from the threads, not the tables.
- Wolff Designa's Twitch archive is where the designer points for commented play [BGG 3168047, BGG 3080063]. No VOD was transcribed.
- No strategy thread newer than 2026-10-24 except the three in the list (classes, Cutter, the illegal Silverarrow opening). Respawn squares, ranged-minion targeting and Pity-coin timing still have no community write-up; those rules are marked [REASONED].
- Snorri and Gydion have no general-strategy thread. Classes of Atlantis only names their jobs. Their cards belong in the hero playbooks.
- 5v5 and Magma and Ice have map geometry and almost no published play. The split in section 1 is the 4v4 pattern extended by one hero.
- Exact attack-versus-defense pairs are catalog numbers. This file tells the scorer to read `CardProfile` and `defensePreview`, and does not restate them.
