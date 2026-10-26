package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.BattleParticipant
import dev.quinton.goa.core.game.effects.MinionBattleQuery
import dev.quinton.goa.core.game.effects.Replacement
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/*
 * The game flow (p.7 Turn Sequence, p.8 End of Round, pp.15–16 Leveling Up). One call of [runStep]
 * performs the current [Step] and moves the program counter; the session commits after each.
 */

fun Game.runStep() {
    when (state.step) {
        Step.TURN_START -> turnStart()
        Step.PLAY -> playStep()
        Step.REVEAL -> reveal()
        Step.RESOLVE -> resolveNext()
        Step.END_OF_TURN -> endOfTurn()
        Step.EOR_EFFECTS -> endOfRoundEffects()
        Step.MINION_BATTLE -> minionBattle()
        Step.CLEANUP -> cleanup()
        Step.PUSH -> pushStep()
        Step.RETRIEVE -> retrieveStep()
        Step.LEVEL_UP -> levelUpStep()
        Step.PITY -> pityStep()
        Step.GAME_OVER -> Unit
    }
}

private fun Game.turnStart() {
    if (state.turn == 1) {
        for (h in state.heroes) h.levelledThisRound = false
        state.heroDefeatedThisRound = false
        emit(GameEvent.RoundStarted(state.round, "Round ${state.round} begins."))
    }
    val record = TurnRecord()
    for (id in unitsOnBoard()) record.startPositions[id] = posOf(id)
    for (h in state.heroes) if (h.at == null) record.startPositions[h.id] = null
    state.turnRecord = record
    state.revealed = false
    state.acting = null
    emit(GameEvent.TurnStarted(state.round, state.turn, "Turn ${state.turn} of round ${state.round}."))
    trigger(Trigger.TurnStarted(state.round, state.turn))
    state.step = Step.PLAY
    state.stepSeat = 0
}

/**
 * p.7 "Play One Card": "You must play a card, if able … If you have no cards left in your hand,
 * you must announce that you are passing."
 */
private fun Game.playStep() {
    val h = state.heroes[state.stepSeat]
    val extra = sources().sumOf { (b, s) -> b.extraCardsToPlay(this, s, h.id) }
    for (i in 0..extra) {
        val hand = hand(h.id)
        if (hand.isEmpty()) {
            if (i == 0) {
                ask(h.id, DecisionKind.PLAY_CARD, "You have no cards: pass", listOf(Choice.Pass), auto = false)
                emit(GameEvent.Note("${heroName(h.id)} has no cards and passes."))
            }
            break
        }
        val options =
            hand.map { Choice.Card(it) as Choice } +
                if (i > 0) listOf(Choice.Decline("Play only one card")) else emptyList()
        val answer = ask(
            h.id,
            DecisionKind.PLAY_CARD,
            if (i ==
                0
            ) {
                "Play a card facedown"
            } else {
                "Play another card?"
            },
            options,
            auto = false,
        )
        if (answer !is Choice.Card) break
        moveCard(h.id, answer.cardId, Zone.PLAYED)
        emit(GameEvent.Note("${heroName(h.id)} plays a card facedown."))
    }
    state.stepSeat++
    if (state.stepSeat >= state.heroes.size) state.step = Step.REVEAL
}

/** p.7: "Once all players have either played a card Facedown, or passed, reveal the cards". */
private fun Game.reveal() {
    state.revealed = true
    for (h in state.heroes) {
        val ids = h.cards.filter { it.zone == Zone.PLAYED }.map { it.id }
        if (ids.any { isAttackCard(h.id, it) }) state.turnRecord.playedAttack += h.id
    }
    // In the order the cards will act (ties by the coin, p.7), heroes who passed last.
    val order = initiativeOrder()
    val played = LinkedHashMap<String, String?>()
    for (h in state.heroes.sortedBy { h -> order.indexOf(h.id).let { if (it < 0) order.size + h.seat else it } }) {
        played[h.id] = h.cards.firstOrNull { it.zone == Zone.PLAYED }?.id
    }
    val text = played.entries.joinToString("; ") { (hero, card) ->
        if (card ==
            null
        ) {
            "${heroName(hero)} passes"
        } else {
            "${heroName(hero)} ${cardName(card)} (${initiativeText(hero)})"
        }
    }
    val initiatives = played.keys.mapNotNull { id -> initiativeOf(id)?.let { id to it } }.toMap()
    emit(GameEvent.CardsRevealed(played, "Cards revealed: $text.", initiatives))
    applyDue { e -> e.timing == Timing.NEXT_TURN_AFTER_PLAYING && e.round == state.round && e.turn == state.turn - 1 }
    trigger(Trigger.CardsRevealed)
    state.step = Step.RESOLVE
}

private fun Game.initiativeText(heroId: String): String = initiativeOf(heroId)?.let { "initiative $it" } ?: "no card"

/** Effects ordered for simultaneous application (p.17): the coin's team first, then seat order. */
fun Game.orderedEffects(): List<ActiveEffect> {
    val order = orderedHeroes().map { it.id }
    return state.effects.sortedWith(compareBy({ order.indexOf(it.owner) }, { it.id }))
}

/**
 * Apply every timed Active effect that is [due] now, once each (p.17 "End of turn", "End of round",
 * "Next turn, after playing cards"). p.17: "In cases when multiple Active effects come into effect at
 * the same time … use the same method as when resolving tied initiative (See page 7), but do not flip
 * the Tie Breaker coin": the coin's team first; within a team "they have to decide" (p.7), so the
 * team (the owner, when the effects are all one hero's) picks which applies next. Effects are looked
 * up again before each one: an effect cancelled by an earlier one (its owner was defeated, p.13; its
 * card changed state, p.17) no longer applies; effects made meanwhile wait for the next time.
 */
fun Game.applyDue(due: (ActiveEffect) -> Boolean) {
    val eligible = state.effects.filter { !it.spent && due(it) }.map { it.id }.toHashSet()
    val done = HashSet<Int>()
    while (true) {
        val pending = orderedEffects().filter { it.id in eligible && it.id !in done && !it.spent }
        if (pending.isEmpty()) return
        val team = teamOf(pending.first().owner)
        val same = pending.filter { teamOf(it.owner) == team }
        val next = if (same.size == 1) {
            same.single()
        } else {
            val owners = same.map { it.owner }.distinct()
            val seat = if (owners.size == 1) owners.single() else teamSeat(team ?: Team.RED)
            val labels = same.map { "${heroName(it.owner)}'s ${cardName(it.cardId)}" }
            val pick = ask(
                seat,
                DecisionKind.CHOOSE_OPTION,
                "These effects apply at the same time: which one first?",
                labels.mapIndexed { i, l -> Choice.Option(i, l) },
            ) as Choice.Option
            same[pick.index]
        }
        done += next.id
        applyTimed(next)
    }
}

fun Game.srcOf(e: ActiveEffect): dev.quinton.goa.core.game.effects.Src =
    dev.quinton.goa.core.game.effects.Src(
        dev.quinton.goa.core.game.effects.SrcKind.EFFECT,
        e.key ?: e.cardId,
        e.owner,
        cardId = e.cardId,
        effect = e,
    )

/** p.7 "Check the Initiative": the number on the card, + Initiative items (p.16), + modifiers. */
fun Game.initiativeOf(heroId: String): Int? {
    val cardId = unresolvedCard(heroId) ?: return null
    val printed = printedValue(heroId, cardId, Stat.INITIATIVE) ?: return null
    return printed + itemBonus(heroId, Stat.INITIATIVE) + statDelta(StatQuery(heroId, Stat.INITIATIVE, cardId))
}

private fun Game.lowerActsFirst(): Boolean = sources().any { (b, s) -> b.reversesInitiative(this, s) }

/**
 * The order the unresolved cards would act in now, ties broken by the coin then seat (for the view;
 * the real tie breaks happen in [resolveNext]).
 */
fun Game.initiativeOrder(): List<String> {
    val reversed = lowerActsFirst()
    val ranked = state.heroes.mapNotNull { h -> initiativeOf(h.id)?.let { h to it } }
    return ranked.sortedWith(
        compareBy<Pair<HeroState, Int>>({ if (reversed) it.second else -it.second }, {
            if (it.first.team ==
                state.tieBreaker
            ) {
                0
            } else {
                1
            }
        }, { it.first.seat }),
    ).map { it.first.id }
}

/**
 * p.7 "Tied Initiative": across teams, "One of the tied players on the team whose symbol is currently
 * revealed on the coin acts first. After that player has resolved their card, that player (and only
 * that player!) must flip the Tie Breaker coin"; within a team "they have to decide which one of them
 * will act first. Do not flip". "After the player who acted first has resolved their card, check the
 * Initiative again."
 */
private fun Game.resolveNext() {
    val ranked = state.heroes.mapNotNull { h -> initiativeOf(h.id)?.let { h to it } }
    if (ranked.isEmpty()) {
        state.step = Step.END_OF_TURN
        return
    }
    // A built position may name who acts next (PositionBuilder "acting"); used once.
    state.vars.remove(NEXT_ACTOR)?.let { forced ->
        if (unresolvedCard(forced) != null) {
            resolveCard(forced)
            state.acting = null
            if (state.heroes.none { unresolvedCard(it.id) != null }) state.step = Step.END_OF_TURN
            return
        }
    }
    val reversed = lowerActsFirst()
    val best = if (reversed) ranked.minOf { it.second } else ranked.maxOf { it.second }
    val tied = ranked.filter { it.second == best }.map { it.first }
    val crossTeam = tied.map { it.team }.distinct().size > 1
    val pool = if (crossTeam) tied.filter { it.team == state.tieBreaker } else tied
    val actor = if (pool.size == 1) {
        pool.single().id
    } else {
        val c = ask(
            teamSeat(pool.first().team),
            DecisionKind.ACT_FIRST,
            "Tied initiative: who acts first?",
            pool.map { Choice.Unit(it.id) },
        ) as Choice.Unit
        c.unitId
    }
    resolveCard(actor)
    if (crossTeam) {
        state.tieBreaker = state.tieBreaker.opponent
        note("${heroName(actor)} flips the Tie Breaker coin: ${Game.teamName(state.tieBreaker)} shows.")
    }
    state.acting = null
    if (state.heroes.none { unresolvedCard(it.id) != null }) state.step = Step.END_OF_TURN
}

/** [GameState.vars] key: the hero a built position makes act next. */
const val NEXT_ACTOR: String = "position.nextActor"

/** p.7 "Hero Respawn", "Perform One Action", "Resolve Your Card". */
fun Game.resolveCard(heroId: String) {
    state.acting = heroId
    val h = hero(heroId)
    val played = h.cards.filter { it.zone == Zone.PLAYED }.map { it.id }
    val cardId = if (played.size == 1) {
        played.single()
    } else {
        (
            ask(
                heroId,
                DecisionKind.CHOOSE_CARD,
                "Which card do you resolve first?",
                played.map {
                    Choice.Card(it)
                },
            ) as Choice.Card
            ).cardId
    }
    if (h.at == null && state.pieces.none { it.heroBody == heroId && it.at != null }) {
        val spaces = respawnSpaces(heroId)
        val options = spaces.map { Choice.Space(it) as Choice } + Choice.Decline("Don't respawn")
        val answer = ask(heroId, DecisionKind.RESPAWN, "Respawn in your Throne Zone?", options, cardId, auto = false)
        if (answer is Choice.Space) {
            setPos(heroId, answer.hex)
            val text = "${heroName(heroId)} respawns in ${spaceName(answer.hex)}."
            emit(GameEvent.Placed(heroId, null, answer.hex, text))
            trigger(Trigger.Placed(heroId, null, answer.hex, heroId))
        } else {
            note("${heroName(heroId)} stays off the board; ${cardName(cardId)} is resolved without an action.")
            finishCard(heroId, cardId, played)
            return
        }
    }
    trigger(Trigger.ChoosingAction(heroId, cardId))
    // Options and the reasons for the printed actions left out come from one pass (ADR-041).
    val availability by lazy(LazyThreadSafetyMode.NONE) { actionAvailability(heroId, cardId) }
    val answer = askAlways(
        heroId,
        DecisionKind.CHOOSE_ACTION,
        "Choose an action for ${cardName(cardId)}",
        cardId,
        context = { DecisionContext(unavailable = availability.unavailable, primaryPreview = availability.preview) },
    ) {
        availability.options.map { Choice.Action(it) }
    } as Choice.Action
    val kind = when (answer.action) {
        ActionType.PRIMARY -> primaryKind(card(cardId))
        ActionType.MOVEMENT -> ActionKind.MOVEMENT
        ActionType.FAST_TRAVEL -> ActionKind.FAST_TRAVEL
        ActionType.ATTACK -> ActionKind.ATTACK
        ActionType.CLEAR -> ActionKind.CLEAR
        ActionType.HOLD -> ActionKind.HOLD
    }
    emit(
        GameEvent.ActionChosen(
            heroId,
            cardId,
            answer.action,
            "${heroName(heroId)} uses ${cardName(cardId)}: ${actionLabel(answer.action)}.",
        ),
    )
    performAction(heroId, cardId, kind, primary = answer.action == ActionType.PRIMARY)
    finishCard(heroId, cardId, played)
}

private fun actionLabel(a: ActionType): String = when (a) {
    ActionType.PRIMARY -> "primary action"
    ActionType.MOVEMENT -> "movement"
    ActionType.FAST_TRAVEL -> "fast travel"
    ActionType.ATTACK -> "attack"
    ActionType.CLEAR -> "clear"
    ActionType.HOLD -> "hold"
}

/** "Place your card Faceup on the current Turn slot … the card is now resolved" (p.7). */
private fun Game.finishCard(heroId: String, cardId: String, playedBefore: List<String>) {
    val h = hero(heroId)
    val toResolve = h.cards.filter { it.zone == Zone.PLAYED && (it.id == cardId || it.id !in playedBefore) }
    for (c in toResolve) {
        moveCard(heroId, c.id, Zone.RESOLVED, slot = state.turn, keepEffects = true)
        trigger(Trigger.CardResolved(heroId, c.id))
    }
    state.turnRecord.resolved += heroId
}

/** p.7 "Turn Ends": "All active 'This Turn' effects are canceled"; End of turn text first (p.17). */
private fun Game.endOfTurn() {
    applyDue { e -> e.timing == Timing.END_OF_TURN && e.round == state.round && e.turn == state.turn }
    trigger(Trigger.TurnEnding)
    returnMinions()
    val turn = state.turn
    val round = state.round
    val expired = state.effects.removeAll { e ->
        e.round != round ||
            (e.timing == Timing.THIS_TURN && e.turn == turn) ||
            (e.timing == Timing.END_OF_TURN && e.turn == turn) ||
            (e.timing == Timing.NEXT_TURN && e.turn < turn) ||
            (e.timing == Timing.NEXT_TURN_AFTER_PLAYING && e.turn < turn)
    }
    if (expired) touch()
    if (turn < 4) {
        state.turn = turn + 1
        state.step = Step.TURN_START
    } else {
        state.step = Step.EOR_EFFECTS
    }
}

// ---- end of round (p.8) ----------------------------------------------------------------------

/** "End of round:" text (p.17), before the Minion Battle (e.g. Xargatha's Constrict reminder). */
private fun Game.endOfRoundEffects() {
    emit(GameEvent.Note("End of round ${state.round}."))
    applyDue { e -> e.timing == Timing.END_OF_ROUND && e.round == state.round }
    trigger(Trigger.RoundEnding)
    returnMinions()
    state.step = Step.MINION_BATTLE
}

/**
 * p.8 "Minion Battle": "the team with fewer minions must remove a number of their minions (of their
 * choice) equal to the difference from the Battle Zone. The Heavy minion must be removed last!";
 * "If you need to remove more minions than your team has in the Battle Zone, remove all your
 * remaining minions". p.20: each Lane's battle separately.
 */
private fun Game.minionBattle() {
    for (lane in geo.map.lanes.map { it.id }) {
        val zone = battleZone(lane)
        val participants = state.minions
            .filter { it.lane == lane && it.at?.let(::zoneOf) == zone }
            .map { BattleParticipant(it.id, it.team, it.type) }
            .toMutableList()
        val q = MinionBattleQuery(lane, participants)
        for ((b, s) in sources()) b.minionBattle(this, s, q)
        val red = participants.count { it.team == Team.RED }
        val blue = participants.count { it.team == Team.BLUE }
        if (red == blue) {
            note("Minion Battle: $red against $blue, no effect.")
            continue
        }
        val loser = if (red < blue) Team.RED else Team.BLUE
        val diff = kotlin.math.abs(red - blue)
        note("Minion Battle: Red $red, Blue $blue. ${Game.teamName(loser)} removes $diff.")
        val left = participants.filter { it.team == loser }.toMutableList()
        var replaced = false
        repeat(diff) {
            if (left.isEmpty() || replaced) return@repeat
            val nonHeavy = left.filter { it.type != MinionType.HEAVY }
            val pool = nonHeavy.ifEmpty { left }
            val c = ask(
                teamSeat(loser),
                DecisionKind.REMOVE_MINION,
                "Minion Battle: remove a minion",
                pool.map { Choice.Unit(it.id) },
            ) as Choice.Unit
            val pick = pool.first { it.id == c.unitId }
            left.remove(pick)
            if (pick.virtual) {
                // e.g. Brogan's Ultimate: "if you would be removed, lose the push instead" (pushDue is set).
                replaced = replacement(Replacement.BattleRemoval(pick.id, lane)).handled
            } else {
                state.minions.firstOrNull { it.id == pick.id }?.let {
                    val label = name(it.id)
                    state.minions.remove(it)
                    touch()
                    emit(GameEvent.Removed(it.id, "$label is removed in the Minion Battle."))
                }
            }
        }
        // "As soon as one of the teams loses their last minion, the Lane is pushed" (step 3): the
        // loser has nothing left that counts as a minion in this battle and no minion in the Zone.
        val realLeft = state.minions.any { it.lane == lane && it.team == loser && it.at?.let(::zoneOf) == zone }
        if (!replaced && left.isEmpty() && !realLeft) state.pushDue.putIfAbsent(lane, loser.opponent)
    }
    state.step = Step.CLEANUP
}

/** p.8 "Remove Tokens, Return Markers": "Remove all tokens from the board. Return all markers". */
private fun Game.cleanup() {
    for (p in state.pieces.toList()) if (p.token && p.removeAtEndOfRound && p.at != null) removePiece(p.id)
    for (h in state.heroes) if (h.markers.removeAll { !it.persistent }) touch()
    state.step = Step.PUSH
}

/**
 * p.8 "Push the Lane": "As soon as one of the teams loses their last minion, the Lane is pushed. The
 * team with one or more minions still remaining in the Battle Zone wins the push." Double lane: both
 * Lanes at once; "If both teams would win at the same time as the result of the push, spawn all
 * minions in the Zones they occupied before the push and continue playing" (p.20).
 */
private fun Game.pushStep() {
    val doubleLane = geo.lanes.size > 1
    val before = if (doubleLane) snapshotLanes() else null
    val claims = if (doubleLane) ArrayList<WinClaim>() else null
    var pushed = false
    for (lane in geo.map.lanes.map { it.id }) {
        val winner = state.pushDue.remove(lane) ?: continue
        pushLane(lane, winner, claims)
        pushed = true
    }
    if (claims != null && pushed) countZonesAfterPushes(claims)
    if (claims != null && claims.isNotEmpty()) {
        if (claims.map { it.team }.distinct().size > 1) {
            restoreLanes(before!!)
            note("Both teams would win at once: the minions spawn back where they were and play continues.")
        } else {
            val c = claims.first()
            win(c.team, c.reason, c.text)
        }
    }
    state.step = Step.RETRIEVE
}

private class LaneSnapshot(
    val minions: List<MinionState>,
    val zones: Map<String, String>,
    val waves: Map<String, Int>,
    val last: Set<String>,
)

private fun Game.snapshotLanes() = LaneSnapshot(
    state.minions.map {
        it.copy()
    },
    LinkedHashMap(state.battleZones),
    LinkedHashMap(state.wavesLeft),
    LinkedHashSet(state.lastWaveFlipped),
)

private fun Game.restoreLanes(s: LaneSnapshot) {
    state.minions.clear()
    // Spaces may have been taken meanwhile only by the respawned minions, which are dropped here.
    state.minions += s.minions.map { it.copy() }
    state.battleZones.clear()
    state.battleZones += s.zones
    state.wavesLeft.clear()
    state.wavesLeft += s.waves
    state.lastWaveFlipped.clear()
    state.lastWaveFlipped += s.last
    touch()
}

/**
 * p.8 "Retrieve Cards": all resolved and discarded cards back to hand; "This cancels all your Active
 * effects, including 'Next Turn' effects."
 */
private fun Game.retrieveStep() {
    for (h in state.heroes) {
        for (c in h.cards) {
            if (c.zone == Zone.RESOLVED || c.zone == Zone.DISCARD || c.zone == Zone.PLAYED) {
                c.zone = Zone.HAND
                c.slot = 0
                c.played = false
                c.faceDown = false
                c.stamp = stamp()
            }
        }
    }
    state.effects.clear()
    touch()
    note("Everyone retrieves their cards.")
    state.step = Step.LEVEL_UP
    state.stepSeat = 0
}

private fun Game.levelUpStep() {
    levelUp(state.heroes[state.stepSeat].id)
    state.stepSeat++
    if (state.stepSeat >= state.heroes.size) state.step = Step.PITY
}

/** p.8 "Collect a Pity Coin": "All players who have NOT leveled up this round collect 1 coin." */
private fun Game.pityStep() {
    for (h in state.heroes) if (!h.levelledThisRound) gainCoins(h.id, 1, "pity coin")
    state.round++
    state.turn = 1
    state.step = Step.TURN_START
}

/**
 * pp.15–16 "Leveling Up": mandatory, "as many levels as you can afford"; each level: "Choose a
 * non-basic Action card in your hand with the lowest card Tier or tied for the lowest. Return this
 * card to your deck. Find two Action cards in your deck that have the same color as the chosen card
 * but are one Tier higher. Take one of those cards into your hand and set the other one aside" — it
 * becomes an item. "Once you upgrade all your cards to Tier III, the next time you level up … you
 * unlock the Ultimate card"; "you cannot gain any additional levels, but can still gain coins".
 */
fun Game.levelUp(heroId: String) {
    val h = hero(heroId)
    while (h.level < 8 && h.coins >= h.level) {
        h.coins -= h.level
        h.level++
        h.levelledThisRound = true
        if (h.level == 8) {
            val ult = h.cards.firstOrNull { card(it.id).color == CardColor.PURPLE && it.zone == Zone.DECK }
            if (ult != null) {
                moveCard(heroId, ult.id, Zone.ULTIMATE)
                emit(
                    GameEvent.LevelledUp(
                        heroId,
                        h.level,
                        "${heroName(heroId)} reaches level 8 and unlocks ${cardName(ult.id)}.",
                    ),
                )
                data.registry.script(ult.id)?.let { (it as? UltimateUnlock)?.unlocked(this, heroId) }
                continue
            }
        } else {
            upgradeOnce(heroId)
        }
        emit(GameEvent.LevelledUp(heroId, h.level, "${heroName(heroId)} reaches level ${h.level}."))
    }
}

/** A Ultimate whose text does something once when it is unlocked (Takahide's Ready for War). */
interface UltimateUnlock {
    fun unlocked(g: Game, heroId: String)
}

/**
 * p.15: the hand cards one level up may upgrade: "a non-basic Action card in your hand with the
 * lowest card Tier or tied for the lowest" (Tier I or II; a Tier III card has nothing above it).
 */
internal fun EngineData.upgradeable(h: HeroState): List<String> {
    val candidates = h.cards.filter {
        it.zone == Zone.HAND && card(it.id).color in UPGRADE_COLOURS && (card(it.id).tier ?: 0) in 1..2
    }
    val lowest = candidates.minOfOrNull { card(it.id).tier!! } ?: return emptyList()
    return candidates.filter { card(it.id).tier == lowest }.map { it.id }
}

/**
 * p.15: what upgrading [cardId] offers: "two Action cards in your deck that have the same color as
 * the chosen card but are one Tier higher", in the order [DecisionKind.KEEP_UPGRADE] asks them (fewer
 * than two only if something moved one out of the deck). The one function both the question and its
 * preview ([DecisionContext.upgrades]) read, so they can't differ.
 */
internal fun EngineData.upgradesOf(h: HeroState, cardId: String): List<String> {
    val chosen = card(cardId)
    val tier = chosen.tier ?: return emptyList()
    return h.cards.filter {
        it.zone == Zone.DECK && card(it.id).color == chosen.color && card(it.id).tier == tier + 1
    }.map { it.id }
}

private val UPGRADE_COLOURS = setOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE)

private fun Game.upgradeOnce(heroId: String) {
    val h = hero(heroId)
    val pool = data.upgradeable(h)
    if (pool.isEmpty()) return
    val old = (
        ask(
            heroId,
            DecisionKind.UPGRADE_CARD,
            "Level up: choose a card to upgrade",
            pool.map {
                Choice.Card(it)
            },
            // The asking hero's own deck, which the rule lets them search (p.15): only in their question.
            context = DecisionContext(upgrades = pool.associateWith { data.upgradesOf(h, it) }),
        ) as Choice.Card
        ).cardId
    val next = data.upgradesOf(h, old)
    moveCard(heroId, old, Zone.DECK)
    if (next.isEmpty()) return
    val keep = (
        ask(
            heroId,
            DecisionKind.KEEP_UPGRADE,
            "Level up: take one card into your hand (the other becomes an item)",
            next.map {
                Choice.Card(it)
            },
        ) as Choice.Card
        ).cardId
    moveCard(heroId, keep, Zone.HAND)
    for (other in next - keep) {
        moveCard(heroId, other, Zone.ITEM)
        // "+1 Defense", the stat as the cards print it.
        val stat = card(other).item?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "nothing"
        emit(
            GameEvent.CardsChanged(
                heroId,
                // Which card was tucked is hidden; only its item icon is public (BGG 3590819).
                "${heroName(heroId)} gains an item: +1 $stat.",
            ),
        )
    }
}
