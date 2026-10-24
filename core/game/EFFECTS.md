# Writing card effects

How to implement a hero for the rules engine (ADR-035). Read [ENGINE.md](ENGINE.md) once for how a
game runs; the rulebook is the pinned PDF text (`D:\.goa-qa\rulebook-text\rulebook.txt`, page images
`D:\.goa-qa\rules\img\p<N>L/R.png` for the jumbled pages), card text is the hero JSON, FAQ rulings
are its `faq`. **Never invent a rule**: where text and FAQ are silent, pick the reading closest to
the text and add an **[APP]** entry to [RULINGS.md](RULINGS.md) with your reason.

The four first-game heroes are complete and are the reference implementations:
`heroes/ArienHero.kt`, `BroganHero.kt`, `WaspHero.kt`, `XargathaHero.kt` and their tests in
`src/test/.../heroes/`.

## Authoring a hero

1. Your file already exists: `core/game/src/main/kotlin/dev/quinton/goa/core/game/heroes/<Hero>Hero.kt`
   (`object <Hero>Hero : HeroModule`, listed in `Heroes.all`). **Edit only your files**: your module,
   your test class, your rows in RULINGS.md. If you truly need a new hook or primitive in a shared
   file, keep it additive, document it here, and say so in your report.
2. Register **every** card id of the hero (deck, extra golds, Handicap, Ultimate, extras such as
   spellbook pages) with `r.card(id, script)`. Quote the verbatim card text in a comment above each
   registration, as the reference heroes do.
3. One test class `heroes/<Hero>Test.kt` with **at least one test per card**, following its text
   clause by clause (each clause asserted: who chooses, which options, what changes), plus a test for
   every FAQ ruling of the hero. Name tests `` `Card Name - clause` `` (no `.`, `:`, `;` in names).
4. Set `complete = true` only when every card has a script and its tests pass; only then is the hero
   in `GameFactory.playableHeroes`.
5. Run `./gradlew :core:game:test :core:game:detekt :core:game:ktlintCheck` (`ktlintFormat` fixes
   style). Use `export GRADLE_USER_HOME='D:\.gradle-home' TMP='D:\.tmp-build' TEMP='D:\.tmp-build'`.

## Card scripts

```kotlin
object ExampleHero : HeroModule {
    override val heroId = "example"
    override val complete = false

    override fun register(r: Registry) {
        // "Target a unit adjacent to you. After the attack: You may move 1 space."
        r.card("example-red-1", script {
            val t = adjacentAttackTarget() ?: return@script   // mandatory: fizzles with no target
            val res = attack(t)
            if (!res.connected) return@script                 // RULINGS 18: no After the attack
            moveSelf(1, optional = true)
        })
        // A card with an Active effect: give the script a behaviour.
        r.card("example-blue-1", object : CardScript {
            override fun primary(a: ActionScope) { a.activeEffect(Timing.THIS_TURN) }
            override val behavior = object : Behavior {
                override fun movementCap(g: Game, src: Src, q: MoveQuery) = …
            }
        })
    }
}
```

- `CardScript.primary(a)` — the Primary action's text (p.7). Not called for a played Defense card.
  For a **Movement** card the engine performs the movement after `primary` returns unless the script
  called `a.movementAction(...)` itself ("before or after movement", "after movement").
- `CardScript.defense(d)` — Defense text (p.14 step 4) for cards whose primary is Defense or
  Defense/Skill. Set `d.bonus`, `d.ignoreMinionModifiers`, `d.block(true/false)`; start effects with
  `d.activeEffect(timing, data)`.
- `CardScript.behavior` — hooks in force while an Active effect of this card is in force, and always
  for an unlocked Ultimate. Registered under the card id.
- `hasPrimary = false` for Ultimates and pure Defense cards.
- An Ultimate whose text acts once when unlocked implements `UltimateUnlock` (Takahide).

`script(behavior, defense) { primary }` builds a `CardScript` from lambdas (`this` is the `ActionScope`).

### Mandatory and optional steps (p.10)

- A **mandatory** step that can't be done: `a.fizzle("reason")` (or let a primitive fizzle: a
  mandatory `moveSelf`, `attackTarget` with no target). The action ends; the card is resolved.
- **"you may"**: `confirm(...)`, `optional = true`, or a choice with Decline. **"up to N"**: min 0
  (or `chooseUpTo`). **"if able"** at the end of a clause: do it when possible, skip silently when
  not (check first, e.g. `canMoveOther`), never fizzle.
- The engine offers the Primary action only if the first instruction can be done (it probes the
  script on a copy: a real question or a board change counts). So write the first instruction first.

### Targets, presence, immunity

- Whenever the text affects a unit or token (attack, move, push, place, swap, discard, give a
  marker), it **targets** it: filter with `a.canTarget(id)` (immunity, heavy minion immunity,
  "cannot be targeted" effects, repeat exclusions) and record it with `a.target(id)` (the attack and
  move helpers do).
- **Presence checks** ("if you are adjacent to a minion") do not target and count immune units (p.18,
  p.19). FAQ (Brynn): checking a unit's *status* counts as targeting.
- "Friendly" never includes you (`a.isFriendly`). "Another unit" never includes you.
- Displacement limits (Bulwark, Magnetic Dagger) are checked by `move`/`push`/`place`/`swap`; use
  `g.canPlace`/`g.canSwap`/`g.canDisplace` when you build option lists.

### Choices

The acting hero chooses unless the text says another hero does: pass `chooser = otherHeroId`.
`chooseUnit`, `chooseToken`, `chooseObject` (unit or token), `chooseSpace`, `chooseCard`,
`chooseOption` ("Choose one —" bullets; ask `choiceLimit(printed)` for Ultimates that widen it),
`chooseNumber`, `confirm`, `chooseUpTo(n, …)`. Options must be listed in a stable order. A single
option is answered automatically (no decision), so tests must not expect a question then.

## Primitives

`ActionScope` (`a`): `g`, `heroId`, `hero`, `cardId`, `card`, `kind`, `primary`, `basic`, `isRepeat`,
`targets`, `notes`, `pos`, `value(stat)`, `range`, `radius` (items included, p.16; card-text bonuses
are added by the script, never items), `movementAction(bonus, ignoreObstacles, straightLine, stepwise)`,
`repeat(times, differentTarget, sameTarget, filter)`, `fizzle`.

Attacks (`Combat.kt`): `attackTarget(prompt, optional) { valid }`, `adjacentAttackTarget()`,
`rangedAttackTarget(rangeValue = …)`, `attack(target, ranged, valid, bonus = { … }, value,
defenseFromInitiative, noPrimaryDefense)` → `AttackResult(connected, defeated, defenseCard, hero…)`.
`attack` performs steps 2–6 of p.14; write "Before the attack" code before it and "After the
attack" code after it. It re-checks the target first (RULINGS 18).

Helpers (`effects/Helpers.kt`): `isEnemy`, `isFriendly`, `isHero`, `isMinion`, `distanceTo`,
`unitsWithin(n)`, `unitsInRadius()`, `unitsInRange()`, `adjacentUnits()`, `tokensWithin(n)`,
`objectsWithin(n)`, `inStraightLine(id)`, `activeEffect(timing, key, data)`, `moveSelf(min, max,
optional, straightLine, ignoreObstacles, stepwise, endFilter)`, `moveOther(id, …, chooser)`,
`canMoveOther`, `pushAway(id, spaces, ignoreObstacles)`, `chooseDistance(values)`,
`discardIfAble(victim, what, filter)`, `discardOrDefeat(victim, …)`, `mayRetrieve(filter, who)`,
`chooseUpTo`, `cardsIn(zone)`; for behaviours: `g.ownerPos(src)`, `g.radiusOf(src)`,
`g.inRadiusOf(src, id, radius)`.

`Game` (`g`), by file:

- **Board** — `isTerrain`, `isObstacle(hex, mover)`, `isEmpty(hex, unit)`, `within(center, n)`,
  `unitsOnBoard`, `tokensOnBoard`, `unitsWithin`, `unitsAdjacentTo`, `tokensAdjacentTo`, `zoneOf`,
  `battleZone(lane)`, `isInBattleZone`, `straightLineFrom`, `reach(MoveSpec)`, `canMove`,
  `move(MoveSpec, chooser, prompt, optional)` (returns the path), `setPos` (bookkeeping only),
  `fastTravelDestinations`, `canFastTravel`, `fastTravel`, `place`, `canPlace`, `swap`, `canSwap`,
  `push(target, from, spaces, ignoreObstacles)` → `PushResult(moved, stoppedByObstacle, to)`,
  `removePiece`, `placeToken(kind, owner, hex, takeFrom)`, `spawnPiece(piece, hex)` / `canSpawnInto`
  (p.19 Spawn: a token on a spawn point is removed first), `isSpawnPoint`, `isMinionSpawnPoint`,
  `isEmptySpawnPoint`, `throneZone`, `canInteract`, `distance`.
- **Cards** — `moveCard(hero, card, zone, slot, keepEffects)`, `hand`, `discardPile`,
  `resolvedCards` (slot order: last = rightmost), `unresolvedCard`, `cardTypeOf`, `cardColorOf`,
  `isAttackCard`, `isSkillCard`, `isDefenseCard`, `lowestTier`, `discard`, `forceDiscard`,
  `retrieve`, `swapCards(hero, a, b, keepEffects)`, `turnFaceDown`, `gainCoins`, `takeCoins`.
- **Units** — `defeatMinion(id, by)`, `removeMinion(id)`, `defeatHero(id, by)`, `spendLife(team, n)`,
  `regainLife(team, n, max)`, `canRespawnMinion(team, type, lane)`, `respawnMinion(...)`,
  `nearestEmptyIn`, `returnMinions`, `respawnSpaces`, `pushLane`, `checkPush`.
- **Effects** — `addEffect(owner, card, timing, action, key, data)`, `cancelEffectsOf`, `cancelEffect`,
  `giveMarker(hero, kind, from, persistent)`, `removeMarker`, `printedValue`, `itemBonus`,
  `statDelta`, `cardValue(hero, card, stat)`, `heavyImmune`, `isImmune`, `canTarget(target, actor,
  kind, card, primary, ranged, ignoreHeavyImmunity, ignoreImmunity)`, `affectedBy(src, unit)`,
  `isBasic`, `win(team, WinReason.CARD_TEXT, text)`, `trigger`, `replacement`.
- **Actions** — `performAction(hero, card, kind, primary, isRepeat, excluded, only, repeatFilter)`,
  `performPrimary(hero, card, excluded)` ("perform the primary action of …"), `actionAllowed`,
  `movementMoves`, `currentAction()`, `asActor(hero) { }`.
- **Flow** — `initiativeOf`, `initiativeOrder`, `orderedHeroes`, `orderedEffects`, `levelUp`.
- **State** — `g.state` (read it freely: `turnRecord` has start positions, who played an attack card,
  cards discarded and heroes resolved this turn, and `notes` for "once per turn"),
  `hero.vars`/`hero.counters` (hero-specific data; counters are shown in the view),
  `effect.data`, `piece.data`, `marker.data`.

## Hooks (`effects/Behavior.kt`)

A `Behavior` is stateless; its `Src` says where it comes from (`EFFECT` with `effect`, `ULTIMATE`,
`MARKER` with `marker`/`holder`, `PIECE` with `piece`, `HERO`) and whose it is (`owner`). Queries must
not change the state; triggers and replacements may, and may ask decisions. For an Active effect,
call `g.affectedBy(src, unit)` before applying it to a unit (p.18: immune to an action type means
immune to its Active effects).

| Hook | Consulted | Combined |
|------|-----------|----------|
| `statDelta(q: StatQuery)` | every icon value read (`cardValue`), initiative, attack and defense totals | summed |
| `printedValue(q)` | the printed value (Hanu "set … to 11", Rowenna "printed Range 2") | last wins |
| `itemMultiplier(hero)` | item bonus (Min "Double your item bonuses") | highest |
| `defenseBonus(defender, attacker)` | defense total, like a minion modifier (Trinkets' barriers) | summed |
| `forbidsAction(q: ActionQuery)` | actions offered, `performPrimary`, movement actions | any |
| `movementCap(q)` | start of a Movement action (p.11) | lowest |
| `forbidsFastTravel(hero)` | Fast Travel | any |
| `forbidsDisplacement(q)` | move, push, place, swap of a unit/token by a hero (or by itself) | any |
| `forbidsRepeat(hero)` | `repeat` (Dodger's Enfeeblement) | any |
| `immune(q: AffectQuery)` | targeting (p.18) | any |
| `forbidsTarget(q)` | targeting without immunity (Min's Smoke bomb line) | any |
| `countsAsObstacle(q: SpaceQuery)` | obstacles and empty spaces, for the mover during the actor's action | any |
| `countsAsTerrain(hex)` | `isTerrain` ("adjacent to terrain") | any |
| `forbidsInteraction(q)` | obstacles and targeting of the actor (NebKher's Crack in Reality) | any |
| `passMode(q: MoveQuery)` | how a move passes obstacles ("may move through obstacles") | ignore wins |
| `reversesInitiative()` | initiative order (Emmitt) | any |
| `extraCardsToPlay(hero)` | Play step (Emmitt's Ultimate) | summed |
| `minionBattle(q)` | Minion Battle participants: add ones that count as minions, drop ones that don't | all |
| `pieceCountsAsMinion(piece, actor)` | minion modifiers, heavy immunity (NebKher's illusions) | any |
| `choiceLimit(q)` | "Choose one / up to N times" (`a.choiceLimit`) | highest |
| `extraLifeCost(q)` | Life counters spent for a defeat (Bounty) | summed |
| `extraDefenseCards(hero)` | cards outside the hand that may defend (Mrak) | union |
| `controls(hero)` | who answers that hero's decisions (Hanu's Ultimate) | first |
| `tokenKind(kind, by)` | the token actually placed (NebKher places Illusions instead) | first |
| `on(t: Trigger)` | the events below | in p.17 order |
| `applyTimed()` | once: End of turn / End of round / Next turn after playing cards effects | in p.17 order |
| `replace(r: Replacement)` | minion defeat (`DefeatOutcome.NOT_REMOVED` / `PREVENTED`), Minion Battle removal, discarding from hand | first handled |

Triggers (`Trigger`): `TurnStarted`, `CardsRevealed`, `BeforeAction` (before any action, after it is
chosen), `AfterAction` (with its targets; also after a repeat), `CardResolved`, `MinionDefeated`
(with its space and the defeater), `HeroDefeated`, `Moved` (path; `push` flag), `Pushed` (spaces,
stopped by an obstacle), `Placed` (from/to, by; Fast Travel included), `Swapped`, `MarkerGiven`,
`CardDiscarded` (from a Turn slot or not), `AttackDone`, `TurnEnding`, `RoundEnding`. To know what is
being performed inside a trigger use `g.currentAction()` (card, kind, basic, targets).

A mandatory step that fails inside a hook or a timed effect stops only that text (`Game.hook`).

## Active effects

`a.activeEffect(Timing.X, key, data)` starts the card's effect (p.17); `key` picks another behaviour
registered with `r.behavior(key, …)` when a card has two effects. Timings: `THIS_TURN`, `THIS_ROUND`,
`NEXT_TURN` (in force the next turn of this round only), `NEXT_TURN_AFTER_PLAYING` / `END_OF_TURN` /
`END_OF_ROUND` (applied once through `applyTimed`), `UNTIL_CANCELLED` (e.g. a state such as "enraged"
kept by a card). The effect records the action type that made it (immunity) and the turn and round.
It is cancelled when its card changes state (not when the played card goes to its Turn slot), when
its owner is defeated, at Retrieve, and when you call `g.cancelEffect`. Radii are always measured
from the owner's current space (`g.inRadiusOf(src, id)` uses `g.radiusOf(src)`, items included).
Starting the same card's effect again replaces it (p.10).

## Tokens, markers and other pieces

- Register a token kind with `r.token(TokenKind(kind, label, supply, removeAtEndOfRound, passable))`
  and its rules with `r.behavior(kind, …)` (the behaviour is in force while the piece is on the board;
  `src.piece`). Place with `g.placeToken(kind, owner, hex, takeFrom)`: it respects the supply (p.18:
  recycle a token of the same kind from the board — ask which first and pass `takeFrom`).
  Tokens are obstacles (p.19), removed at the end of round unless `removeAtEndOfRound = false`, not
  removed when their owner is defeated, removed by Clear. `faceDown = true` hides the kind from the
  other team (Min's mines).
- Markers: `g.giveMarker(hero, kind, from, persistent)`; behaviour under the marker kind
  (`src.marker`, `src.holder`, `src.owner` = giver). Returned at the end of round and on defeat
  unless persistent.
- Other objects (`PieceState`): Pyro (`token = true`, `removeAtEndOfRound = false`), the Turret
  (`token = false`: an obstacle, not cleared), Razzle's doubles (`unit = true`, `heroBody = "razzle"`:
  the piece *is* Razzle — `isHero`, `teamOf`, attacks on it are defended by Razzle, defeating Razzle
  removes every body). Add pieces to `g.state.pieces` and call `g.place`/`g.touch()`.
- Respawning minions by text: check `g.canRespawnMinion` (p.19), then `g.respawnMinion`.

### Razzle

`Trigger.ChoosingAction(heroId, cardId)` fires in `Flow.resolveCard` just before a hero is asked
`CHOOSE_ACTION` (after any respawn). Razzle's hero rule asks there which one of her performs the
action and exchanges the hero's space with that figure's (`HeroState.at` is always the acting figure),
so the actions offered are that figure's. Her scripts treat every other `razzle_double` piece as
"another one of you". Rulings: `rulings/razzle.md`.

## Positions (`position/PositionBuilder.kt`)

```kotlin
val d = pos {                       // tests: Driver(position(engine, map) { … }) with ANY_HERO_ENGINE
    clearMinions()                  // else the opening's minions stay
    round = 2; turn = 3; tieBreaker = Team.BLUE; length = GameLength.LONG
    hero("arien", Team.BLUE, Hex(1, -2)) {            // null space = off the board
        level = 3; coins = 2; controller = Controller.Ai(AiLevel.HARD)
        played("arien-red-1")                         // this turn's card
        hand("arien-gold", "arien-blue-1")            // exactly these (others of the round: discard)
        resolved(1, "arien-silver"); discard("arien-green-1"); items("arien-red-2b")
        marker("marker_poison", from = "tigerclaw"); ultimate(); vars("k" to "v"); counter("charges", 2)
    }
    minion(Team.RED, MinionType.MELEE, Hex(0, -2), lane = "lane-1")
    token("token_rock", Hex(3, -3), owner = "mrak")
    battleZone("lane-1", "lane-1-3"); wavesLeft("lane-1", 2); lifeCounters(Team.RED, 3)
    effect("arien", "arien-silver", Timing.THIS_TURN, ActionKind.SKILL)
    phase = PositionPhase.Resolving(next = "arien")   // or TurnStart, PlayCards(fromSeat), EndOfRound
}
```

Placing a card of a colour moves that colour's starting card out of the hand. The builder checks one
obstacle per space and that every piece is on a space. `Resolving` marks the cards revealed (and
records who played an attack card); `next` makes that hero act first once, else the initiative
order decides. For the app: `position(engine, mapId) { … }` returns an `EngineSession`.

## Testing

- `pos { … }` → `Driver`: `action(hero, ActionType)`, `play`, `space(q, r)`, `unit(id)`, `card(id)`,
  `yes()`, `decline()`, `number(n)`, `option(i)`, `defend(card or null)`, `expect(kind, seat)`,
  `spaces()`, `units()`, `cards()`, `actions()`, `texts()` (event log), `hero(id)`, `at(id)`,
  `zoneOf(hero, card)`. `ANY_HERO_ENGINE` seats heroes not complete yet.
- Forgotten Island coordinates: list them with the map JSON (`app/src/main/assets/catalog/maps/
  forgotten-island.json`). A quiet strip for tests: rows r = −2 and r = −5, q = −1..4, in the
  Battle Zone (`lane-1-2`), no spawn points; terrain at (1,−3), (2,−4), (5,−2), (−2,−1).
- Single-option questions are answered by the engine: assert the outcome, not the question.
- Engine primitives can be tested with test-only scripts on a stub hero's card ids (`CardRulesTest`).
- Never assert on implementation details a rule doesn't fix (option order other than the canonical
  order, event wording beyond the sentence the UI shows).

## The 32 heroes

What each hero needs beyond plain actions, and the framework pieces that cover it. ★ = done.

| Hero | Special mechanics (card text) | Framework pieces |
|------|-------------------------------|------------------|
| ★ Arien | Behind-the-target discard, push after ranged attack, place self with spawn-point limits, swap with minion, Duelist immunity except the attacker, Slippery Ground movement cap and no Fast Travel; Ultimate: once per turn move before an Attack action | `discardOrDefeat`, `pushAway`, `place`/`canPlace`, `swap`, `immune`, `movementCap`, `forbidsFastTravel`, `BeforeAction` + `turnRecord.notes` |
| Bain | Bounty marker (+1 Life counter on defeat; moved by Close Call; Ultimate: discard when given), guessing a hidden card's colour, straight line with no units or terrain between, Vantage Point "Ignore obstacles … +1 movement", "End of turn: may repeat once" | `giveMarker` + `extraLifeCost`, `MarkerGiven`, `chooseCard(chooser = enemy)` + `chooseOption` colours, `movementAction(bonus, ignoreObstacles)`, `END_OF_TURN` effect + `performAction(isRepeat)` |
| ★ Brogan | Move into the target's space, Bulwark (no displacement by enemies), charge 2–4 straight then target, discard-to-attack-in-range, Shield (minion not removed, coins still gained), coins to a friend (more if a hero was defeated), push enemy unit or token, "played an attack card this turn"; Ultimate: counts as heavy minion in the battle, loses the push instead of being removed | `forbidsDisplacement`, `moveSelf(straightLine, endFilter)`, `replace(MinionDefeat)` → `NOT_REMOVED`, `state.heroDefeatedThisRound`, `turnRecord.playedAttack`, `minionBattle` + `replace(BattleRemoval)` + `state.pushDue` |
| Brynn | "Adjacent to 3 or more obstacles" (heroes); Ultimate makes every enemy hero count as such while she acts | count `isObstacle` neighbours; check the Ultimate in your own helper (only Brynn uses it) |
| Cutter | Push into another Zone, grapple to an obstacle in a straight line ignoring immunity, move then target "in the direction of the move", enemy chooses between options, 13 coins win; Ultimate: perform a primary action of the previous Turn slot card | `push` + `zoneOf`, `canTarget(ignoreImmunity)`, `chooseOption(chooser = enemy)`, `win(CARD_TEXT)`, `AfterAction` + `performPrimary` |
| Dodger | Empty spawn points in the Battle Zone, respawn friendly minions (any type, FAQ), −Attack effects, cannot repeat, "gain an Attack item" from the box; Ultimate: every space counts as Battle Zone with a friendly spawn point while she acts | `isEmptySpawnPoint`, `isInBattleZone`, `canRespawnMinion`/`respawnMinion`, `statDelta`, `forbidsRepeat`, `hero.spareItems`; Ultimate in her own helpers |
| ★ Emmitt | Reverse initiative next turn (ignores immunity), Glitch tokens and swap with one the enemy picks, defend with Initiative, "remained in the same space since the last turn", "already resolved a card this turn", place a unit where it started this turn, retrieve cards discarded this turn, swap an unresolved card with a resolved one; Ultimate: play two cards, retrieve one after the reveal | `reversesInitiative`, `placeToken` + `chooseToken(chooser)`, `attack(defenseFromInitiative)`, `turnRecord.startPositions/resolved/discarded`, `swapCards`, `extraCardsToPlay` + `CardsRevealed`; `HeroView.alsoPlayed` |
| Garrus | +1 Attack per discarded card, move enemy "farther away", friendly "closer", This round movement cap 2; Ultimate: after a resolved card is discarded, perform its primary | `discardPile`, `moveOther(endFilter)`, `movementCap`, `CardDiscarded(fromResolved)` + `performPrimary` |
| Gydion | Spellbook of 22 spell cards (extras, start in `Zone.EXTRA` tag `box`), casting = performing a spell card's action, spells removed faceup, Sunburst/Dimension Door grow per removed spell, Wish wins after three casts, regain a Life counter | `moveCard` + `tag` (`spellbook`, `removed`), `performAction(gydion, spellId, kind, primary = true)`, `win(CARD_TEXT)`, `regainLife`; spells have their own `values` |
| Hanu | Set a card's printed Initiative to 11 until it resolves, swap and immunity then swap back at End of turn regardless of immunity, swap two friendly units, move two heroes the same distance and direction; Ultimate: choose another hero's next action | `printedValue`, `immune`, `END_OF_TURN` + `canTarget(ignoreImmunity)`, `swap`, `controls` |
| Ignatia | Text by the Tie Breaker coin, flip it, Magma tokens on spaces moved through, Equilibrium choice; Ultimate: flip the coin and repeat with new targets | `state.tieBreaker`, path from `moveSelf`, `placeToken`, `AfterAction` + `performAction(isRepeat, excluded)` |
| Min | Smoke bomb blocks targeting in a straight line, swap with it to block, facedown mines units may move through (triggered when an enemy hero moves through), Death Grenade at End of turn, double item bonuses, Tier II card as a permanent item, apply another red card's After the attack text; Ultimate: repeat attacks on a different target | `forbidsTarget`, `TokenKind(passable)`, `faceDown`, `Moved` (path; the engine asks paths step by step near such tokens), `END_OF_TURN`, `itemMultiplier`, `moveCard(ITEM)`, shared After-the-attack functions in your file, `AfterAction` + `performAction(isRepeat)` |
| Misa | Place self at maximum range, move before a primary action next turn, move through units ignoring obstacles and place one, swap units at equal distance; Ultimate: choose two options, discard after placing yourself | `place`, `NEXT_TURN` + `BeforeAction`, `moveSelf(straightLine, ignoreObstacles, stepwise)` + path, `choiceLimit`, `Placed` |
| Mortimer | Zombie tokens (not removed at end of round), zombies push from their own space, "choose up to N times", replace a minion with a zombie; Ultimate: up to five times | `TokenKind(removeAtEndOfRound = false)`, `push(from = zombie hex)`, `removeMinion` + `placeToken`, `choiceLimit` |
| Mrak | Rock tokens in a line from the attack direction, exactly three rocks as far as possible, Stone Carapace (discard it instead; defend with it from the dashboard), Rolling Stone moves, secondary Attack action; Ultimate: discard after rocks are placed next to enemy heroes | `placeToken`, `replace(Discard)`, `extraDefenseCards`, custom `reach` with `MoveSpec(ignoreObstacles, straightLine)`, engine secondary attack, `Placed` |
| NebKher | Perform an action of an enemy's previous Turn slot card (tokens become Illusions, no markers), laugh diabolically, Illusion tokens count as friendly melee minions while he acts, name a colour for next turn, Crack in Reality (no interaction across a line); Ultimate: after laughing, enemies in radius discard or are defeated | `performAction(nebkher, enemyCardId, …)`, `tokenKind`, `pieceCountsAsMinion`, `NEXT_TURN_AFTER_PLAYING` + `data`, `forbidsInteraction` |
| ★ Razzle | Up to 3 more of her (`heroBody` pieces), all share cards; remove copies; move another copy; swap; "if defeated, remove all of you"; Ultimate: another copy repeats a basic attack | `PieceState(unit = true, heroBody = "razzle")`, `isHero`/`heroBodyOf`, `defeatHero` removes bodies; choose the acting body yourself (swap the hero's `at` with a body's) |
| Rowenna | Retrieve for both, place self next to an enemy who may move (coins if so), place minions after movement, defeat a hero with no cards (triple assist coins); Ultimate: attacks become Ranged with printed Range 2 | `retrieve`, `place`, `confirm(chooser = enemy)`, `movementAction()` then text, `defeatHero` + extra `gainCoins`, `printedValue(RANGE)` + `attack(ranged = true)` |
| Sabina | +3 Attack if the target played an attack card, remove minions after attack, swap minions; Ultimate: +2 Range/Attack on basic attacks, a pushed enemy hero discards or is defeated | `turnRecord.playedAttack`, `removeMinion`, `swap`, `statDelta`, `Pushed` |
| Silverarrow | Fast Travel inside an attack, This round ignore obstacles on movement actions, End of turn discards; Ultimate: before each action may move 2 straight | `fastTravel(optional = true)`, `passMode(movementAction)`, `END_OF_TURN`, `BeforeAction` |
| Snorri | Rune markers under Turn slots (not removed at end of round or on defeat), "a rune is active" by current turn, rune-dependent Blocks and immunity, swap resolved and hand cards; Ultimate: a second active rune for each action | `giveMarker(persistent = true)` + `marker.data`, `state.turn`, `d.block`, `immune`, `swapCards` |
| Swift | Perform the rightmost resolved card's primary, Bounce repeat when already resolved, coins when enemy minions in radius are defeated next turn, End of turn placement; Ultimate: after resolving a basic card perform its primary (not the same enemy hero twice a turn) | `resolvedCards().last()` + `performPrimary`, `NEXT_TURN` + `MinionDefeated(at)`, `END_OF_TURN`, `CardResolved` + `turnRecord.notes` |
| Takahide | Three golds (two start in the deck), swap a gold with a deck gold (facedown when resolved/discarded), friendly heroes swap their unresolved card with a hand card, empty spaces count as obstacles for enemy units, Handicap is silver and has no Block condition; Ultimate: return silver, take two golds | `StartingCards`/`Setup` already deal the golds, `swapCards`, `turnFaceDown`, `countsAsObstacle(mover)`, `UltimateUnlock` |
| Tali | Ice tokens (−1 Initiative to adjacent enemy heroes), Totem replaces a minion's defeat and is immune, "if this card is not in the discard", a friend names a colour, perform the primary of a discarded card; Ultimate: after a basic attack defeats a minion, a discard of a chosen colour | piece `statDelta`, `replace(MinionDefeat)` → `PREVENTED` + `removePiece`, piece `immune`, `chooseOption(chooser = friend)`, `performPrimary`, `MinionDefeated` + `currentAction()` |
| Tigerclaw | Move 2 straight through an enemy unit then target it, take coins, Poison marker (−1/−2 Initiative, Attack, Defense), Blocks with attacker discards, the target cannot defend with a primary action; Ultimate: repeat basic actions | `moveSelf(straightLine, ignoreObstacles, endFilter)` + path, `takeCoins`, marker `statDelta`, `d.block`, `attack(noPrimaryDefense)`, `AfterAction` + `performAction(isRepeat)` |
| Trinkets | The Turret (not a token, an obstacle, stays between rounds), range "of both you and the Turret", Disruptor before enemy primary actions, Barrier tokens give defense, remove the Turret; Ultimate +1 Range and Radius | `PieceState(token = false, removeAtEndOfRound = false)`, `BeforeAction(primary)` + `forceDiscard`, `defenseBonus`, `statDelta` |
| Ursafar | "Enraged" this round, perform active cards' primary, extra Life counter for a defeated hero, swap after movement, +Movement if enraged; Ultimate: always enraged, every resolved card counts as active | `UNTIL_CANCELLED`/`THIS_ROUND` effect as the state, `performPrimary`, `spendLife`, `movementAction(bonus)` then `swap` |
| ★ Wasp | No swap/place for enemies in radius, Static Barrier, after-attack discards in radius, Block ranged with discards, place a unit not in a straight line next to you, move a unit at the same distance, push up to two enemies with a discard when stopped; Ultimate: after a basic skill defeat a minion and an adjacent hero discards | `forbidsDisplacement`, `countsAsObstacle` (actor), `forceDiscard`, `d.block`, `place`, `MoveSpec(endFilter)`, `repeat(sameTarget)`, `chooseUpTo` + `PushResult.stoppedByObstacle`, `AfterAction(basic)` |
| Whisper | A hero performs a movement action on the card it defended with (full distance straight), empty minion spawn points, enemy chooses an option, "if an enemy hero in radius has a card in the discard"; Ultimate: basic actions choose one or both | `AttackResult.defenseCard` + `asActor(hero) { move(MoveSpec(straightLine, movementAction = true), chooser = hero) }`, `isEmptySpawnPoint`, `chooseOption(chooser = enemy)`, `choiceLimit(basic)` |
| ★ Widget | Pyro (counts as a token, stays between rounds), move/swap Pyro, range from Pyro, perform a resolved skill card's primary on a different target, move Pyro and an enemy together; Ultimate: after a movement action perform a faceup skill card's primary | `PieceState(token = true, removeAtEndOfRound = false)`, `move`/`swap` of the piece, `performPrimary(excluded)`, `AfterAction(kind MOVEMENT)` |
| Wuk | Tree tokens (stay between rounds), remove a tree to attack in range, place a token or unit in range, Trample (straight move ignoring obstacles: heroes moved through discard or are defeated, minions moved through defeated), enemy minions don't count in the Minion Battle; Ultimate: place a tree after resolving a card | `TokenKind(removeAtEndOfRound = false)`, `removePiece`, `place`, `movementAction(straightLine, ignoreObstacles, stepwise)` + path, `minionBattle` (drop participants), `CardResolved` |
| ★ Xargatha | Repeat on a different enemy hero, pull an enemy next to her, +Attack/+Range per adjacent enemy, move enemy minions before or after movement (ignore heavy immunity), Stone Gaze (terrain, no movement actions), End of round minion defeat, retrieve when adjacent; Ultimate: +Movement/+Initiative per adjacent enemy, move through obstacles | `repeat(differentTarget, filter)`, `moveOther(endFilter)`, `attack(bonus)`, `rangedAttackTarget(rangeValue)`, `movementAction()`, `canTarget(ignoreHeavyImmunity)`, `countsAsTerrain`, `forbidsAction`, `END_OF_ROUND` + `applyTimed`, `statDelta`, `passMode` |

### Known engine limits a hero agent may meet

- `PropertyTest` asserts at most one played card per hero and Life counters that only go down; when
  Emmitt or Gydion becomes playable, relax those two checks for them.
- The play UI shows one played card (`HeroView.played`) plus `alsoPlayed`.
- Razzle's "the same hero except when actions are performed" needs the acting body chosen by your
  script; the engine treats `HeroState.at` as the body that acts.
- NebKher performing another hero's card runs that card's script with NebKher as `heroId`; scripts
  should use `a.heroId`/`a.hero` (never a hard-coded hero id) for "you".
