package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.TurnPlay
import dev.quinton.goa.core.game.TurnReview

/**
 * A state the session keeps at a turn boundary for the review (ADR-037). [state] is a committed
 * checkpoint, which is never mutated in place, so keeping it costs a reference; [events] is the
 * number of committed events at that point.
 */
internal class Mark(val kind: Kind, val state: GameState, val events: Int) {
    enum class Kind {
        /** A built position (a puzzle) that does not start at a turn's start. */
        POSITION,

        /** The start of a turn, before the Turn start step. */
        TURN_START,

        /** Just after the reveal (p.7), before the first card resolves. */
        REVEALED,

        /** The round's 4th turn is over: the end of round steps run next (p.8). */
        ROUND_END,
    }

    companion object {
        /** The mark a commit from a step that started at [from] into [next] leaves, if any. */
        fun after(from: Step, next: GameState, events: Int): Mark? = when {
            next.step == Step.TURN_START -> Mark(Kind.TURN_START, next, events)
            from == Step.REVEAL -> Mark(Kind.REVEALED, next, events)
            next.step == Step.EOR_EFFECTS && from != Step.EOR_EFFECTS -> Mark(Kind.ROUND_END, next, events)
            else -> null
        }

        /** The marks of a game's (or position's) start state. */
        fun initial(state: GameState): List<Mark> = when {
            state.step == Step.TURN_START -> listOf(Mark(Kind.TURN_START, state, 0))
            state.step >= Step.EOR_EFFECTS && state.step != Step.GAME_OVER ->
                listOf(Mark(Kind.POSITION, state, 0), Mark(Kind.ROUND_END, state, 0))
            state.revealed -> listOf(Mark(Kind.POSITION, state, 0), Mark(Kind.REVEALED, state, 0))
            else -> listOf(Mark(Kind.POSITION, state, 0))
        }
    }
}

/**
 * Builds [TurnReview]s from the session's marks. Every card it names comes from [Views] built on a
 * kept state for the viewers: the same rules as the live table (p.4), so the review can never show
 * more than the viewers saw at the time.
 */
internal object Review {
    fun turns(
        data: EngineData,
        setup: GameSetup,
        marks: List<Mark>,
        events: List<GameEvent>,
        now: GameState,
        viewers: Set<String>,
    ): List<TurnReview> {
        val out = ArrayList<TurnReview>()
        var open: Open? = null
        for (m in marks) {
            when (m.kind) {
                Mark.Kind.TURN_START, Mark.Kind.POSITION -> {
                    open?.let { out += it.close(m.events, m.state, finished = true) }
                    open = Open(data, setup, events, viewers, m)
                }

                Mark.Kind.REVEALED -> open?.reveal = m

                Mark.Kind.ROUND_END -> open?.roundEnd = m
            }
        }
        open?.let { out += it.close(events.size, now, finished = it.roundEnd != null || now.step == Step.GAME_OVER) }
        return out
    }

    /** The kept start of [round]'s [turn] (or the position the game began from inside it). */
    fun start(marks: List<Mark>, round: Int, turn: Int): GameState? = marks.lastOrNull {
        (it.kind == Mark.Kind.TURN_START || it.kind == Mark.Kind.POSITION) &&
            it.state.round == round && it.state.turn == turn
    }?.state

    /** A turn being collected: its start mark, its reveal and end of round marks once seen. */
    private class Open(
        val data: EngineData,
        val setup: GameSetup,
        val events: List<GameEvent>,
        val viewers: Set<String>,
        val start: Mark,
    ) {
        var reveal: Mark? = null
        var roundEnd: Mark? = null

        /** Closes the turn at event [end]; [after] is a state just past it (its turn record is this turn's). */
        fun close(end: Int, after: GameState, finished: Boolean): TurnReview {
            val revealAt = reveal?.events
            val eorAt = roundEnd?.events
            val turnEnd = eorAt ?: end
            fun slice(from: Int, to: Int) = events.subList(from.coerceIn(0, events.size), to.coerceIn(from, events.size))
            val state = start.state
            return TurnReview(
                round = state.round,
                turn = state.turn,
                plays = reveal?.let { plays(it.state, after) }.orEmpty(),
                opening = slice(start.events, revealAt ?: turnEnd),
                events = if (revealAt != null) slice(revealAt, turnEnd) else emptyList(),
                endOfRound = if (eorAt != null) slice(eorAt, end) else emptyList(),
                finished = finished,
                hasStart = true,
                fromPosition = start.kind == Mark.Kind.POSITION,
            )
        }

        /**
         * Each hero's card as the viewers saw it just after the reveal; ordered as they acted ([after]'s
         * turn record), then those still to act in initiative order, then passes. A position that began
         * after some heroes resolved shows their card on this turn's slot.
         */
        private fun plays(revealed: GameState, after: GameState): List<TurnPlay> {
            val view: GameView = Views.merged(data, setup, revealed, viewers)
            val acted = after.turnRecord.resolved.distinct()
            val order = acted + view.initiativeOrder.filter { it !in acted } +
                view.heroes.map { it.heroId }.filter { it !in acted && it !in view.initiativeOrder }
            return order.mapNotNull { id -> view.heroes.firstOrNull { it.heroId == id } }.map { hero ->
                // No card has this turn's slot at a reveal; a position built mid-turn may have some.
                val onSlot = hero.resolved.firstOrNull { it.slot == revealed.turn }
                TurnPlay(
                    heroId = hero.heroId,
                    team = hero.team,
                    card = hero.played ?: onSlot,
                    alsoPlayed = hero.alsoPlayed,
                    initiative = hero.initiative,
                    resolved = hero.heroId in acted || (hero.played == null && onSlot != null),
                )
            }
        }
    }
}
