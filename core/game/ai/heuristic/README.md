# Writing a hero playbook

The computer player is a general book plus one file per hero (`ADR-085`, contract in
[DESIGN.md](DESIGN.md)). Your file is `heroes/<Name>Heuristics.kt`. It is an `object` that
implements `HeroHeuristics`. Registering is already done. Edit only your file: four people can
fill in four heroes at once.

The general book already plays a legal, lane-aware game for every `DecisionKind`. Your notes
add to that score. They do not re-implement a card. The engine resolves the text. You say when
and why.

Numbers are coin-equivalents, the same units as `STRATEGY.md` §2.2: one coin is 1, a card in
hand is about 2.5, one minion of lane advantage at an even lane is about 15.

## What you may read

`adjust`, `override` and `threatAdjust` receive an `HContext`. It is the deciding team's
determinized view of this one decision. Build nothing from `session.state` of the real game
and nothing from an enemy's `hand` unless it is non-null.

| Call | What it is |
|------|------------|
| `ctx.own()`, `ctx.ownHand()`, `ctx.card(id)` | You, and your real hand, as `HCard` (printed attack, defense, initiative, reach, …). |
| `ctx.hand(id)` | A hand you are allowed to see. Null for a hidden enemy. Strength 10 fills it. |
| `ctx.possibleCards(id)` | Every card public knowledge still allows that hero to hold. Weights of one colour sum to about 1. |
| `ctx.threatsOn(hex, team)` | Enemy cards that reach that space, this turn and the next. |
| `ctx.exposure(heroId, at)` | Expected loss of standing there, in coins. 0 when the strength has no threat map. |
| `ctx.placement(heroId, at)` | How good that space is: the lane, a minion to take, formation, minus exposure. |
| `ctx.distance`, `ctx.distanceToBattleZone`, `ctx.inBattleZone` | Hex distance and the Battle Zone. |
| `ctx.movementDestinations`, `ctx.fastTravelDestinations` | Legal moves. Fast Travel is empty when an enemy unit blocks the zone. |
| `ctx.attackReach(heroId, card)` | Printed reach plus range items, including a "Before the attack: Move". A wall can still stop it. |
| `ctx.minionWorth(id)`, `ctx.heroStrikeWorth(id, total, ranged)` | What taking that target is worth to the answering team. |
| `ctx.killWorth(id)`, `ctx.spare(id)`, `ctx.playsLeft(id)` | A death, and how many cards are left to spend. |
| `ctx.minionModifier(hex, team)` | The p.14 modifier. 0 when this strength is not looking, or forgot. |
| `ctx.life`, `ctx.coins`, `ctx.level`, `ctx.matchPoint`, `ctx.opening` | The race, and whether the push is already decided. |
| `ctx.plan` | The intent derived from the card already played (`Intent.KILL`, `FARM`, `RETREAT`, …). |
| `ctx.style(id)` | The `HeroStyle` the general book is already using. |
| `ctx.defenseless(id)`, `ctx.spareWeight()` | No card left to defend with this turn (an empty hand, or the last card is about to be played), and what a spare is worth on this turn. |
| `ctx.stillToAct(id)` | That hero has a card still to resolve this turn. |
| `ctx.actsBefore(id, init)` | The share of that hero's possible cards that act before the initiative `init` (1 once it has acted). |
| `ctx.exposureSplit(id, before, after, init)` | Exposure as the card play sees it: an enemy acting before `init` meets the hero at `before`, one acting after at `after`. |
| `ctx.defeatBefore(id, at, init)` | The chance an enemy acting before `init` defeats the hero standing at `at`. |
| `ctx.goalZone(id)`, `ctx.nextBattleZone(lane, team)` | Where the hero should stand once the push is decided (lane timing), and where a push moves a lane. |
| `ctx.laneKillValue(lane)`, `ctx.lastLifeWorth()` | One enemy minion fewer on that lane, in lane value; and the last Life counter's worth. |
| `ctx.patches` | False while your `adjust` is added to an engine look (the card play from strength 5, a looked action, target or space): the engine already resolved the card. A note that corrects the general book's static strike or placement estimate returns 0 then. |
| `ctx.placedHero` | Who a card's own "choose a space" (no unit named) puts there: a hero id, or null for a token or piece (a Turret, a Barrier, a grenade). The general book scores a piece's space as nobody's. |

`evalTerms` does not get an `HContext`. It gets the `GameState` and a team. Count only things
that are on the board or on the public dashboard (your tokens, your counters). Do not read a
hidden hand off the state: at strengths below 10 that hand is one sample, and trusting it is
peeking.

## Style

`HeroStyle` is read by the general book before your `adjust` runs. Set it and you have already
changed movement, farming and level-ups.

- `role` — Tank, Fighter, Ranged, Support, Assassin, Pusher, SetUp.
- `idealDistance` — spaces you like between you and the nearest enemy hero.
- `aggression` — 0 farms, 1 hunts heroes. The general book scales a hero-hit by it.
- `laneWeight` — how much standing in the Battle Zone is worth.
- `upgradeOrder` — colour names (`"red"`) or card ids, best first.
- `keepDefender` — hold the best defense card while a spare remains.

## A score

Return coin-equivalents. Positive favours the option. A null `override` leaves the choice to
the scores. Return an option only when it must be played.

```kotlin
override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double {
    if (d.kind != DecisionKind.PLAY_CARD || choice !is Choice.Card) return 0.0
    // Silver is the threat you keep in hand, not the card you spend on turn 1.
    if (ctx.turn == 1 && choice.cardId == "arien-silver") return -2.0
    return 0.0
}
```

```kotlin
override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double {
    if (d.kind != DecisionKind.CHOOSE_UNIT || choice !is Choice.Unit) return 0.0
    val minion = ctx.state.minion(choice.unitId) ?: return 0.0
    // Prefer the ranged minion while it still covers a teammate.
    val covered = ctx.enemies().any { e -> e.at?.let { ctx.distance(it, minion.at!!) in 1..2 } == true }
    return if (minion.type == MinionType.RANGED && covered) 3.0 else 0.0
}
```

## Look, then correct only what the look cannot see

From strength 5 the card play's value is the engine's: each card's action is resolved alone and the
position scored (`CardSim`). Do not re-price a hit, a discard or a minion the card's text already takes;
that is counted twice. Add what the engine cannot see from one action: a hidden hand, who has already
played an attack this turn, an initiative not yet revealed, a set-up that pays next turn, a card to keep.
A correction of the static estimate (`strikeFrom`, a dead strike) belongs behind `if (ctx.patches)`.

## Your own pieces

`evalTerms` is added to the position value, from `team`'s point of view. Positive is good for
that team. A token you own should count for your team and against the other.

```kotlin
override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
    if (heroId != this.heroId) return 0.0
    val mine = state.pieces.count { it.owner == heroId && it.at != null }
    val sign = if (state.hero(heroId)?.team == team) 1.0 else -1.0
    return sign * mine * 1.2
}
```

## A threat the printed reach misses

```kotlin
override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
    if (enemyHeroId != heroId) return 0.0
    val from = ctx.hero(heroId)?.at ?: return 0.0
    // Brogan's line is worse than a plain neighbour. A small extra, not a second rules engine.
    return if (ctx.distance(from, target) <= 2) 1.5 else 0.0
}
```

## Check it

`heroes/<Name>HeuristicsTest.kt`, when you have a real note: the hero finishes a 2v2 and a 3v3
(`AiHarness`), and one built position where the note must fire (`position { }` or
`PositionBuilder`). `AI_H_ABLATION=<heroId>` plays the same strength with your book and without
it. You want the book not to lose. The aim is over 55%.
