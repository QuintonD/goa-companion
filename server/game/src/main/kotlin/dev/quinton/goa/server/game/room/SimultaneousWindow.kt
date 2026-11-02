package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step

/** Which run of simultaneous questions: an all-pass turn or a level-up does not stay in the same one. */
data class WindowKey(val round: Int, val turn: Int, val group: String)

/**
 * What one seat has left in a window. [question] is its next question, or null when the seat has nothing
 * more to answer. [hero] is the seat's own hero after its own queued answers, null when none of that
 * applies. [session] is the fork at [question], with placeholders for everyone else: the computer answers
 * on it, so it can't peek at a real lock. [staleFrom] is the first queued answer that no longer fits.
 */
class Forecast internal constructor(
    val question: Decision?,
    val hero: HeroView?,
    val session: GameSession,
    val staleFrom: Int?,
)

/**
 * One run of simultaneous questions (docs/ONLINE.md "Simultaneous questions"). The engine asks seat by
 * seat, people answer at the same time, and no such question can depend on another seat's hidden answer:
 * each seat's question is forecast on a fork where only its own [locks] are real.
 */
class SimultaneousWindow private constructor(
    private val session: EngineSession,
    private val step: Step,
    val key: WindowKey,
    private val locks: Map<String, List<Choice>>,
) {
    private val forecasts = HashMap<String, Forecast>()

    /** The kind others are told about, the same whichever of a seat's questions is really pending. */
    val publicKind: DecisionKind
        get() = if (step == Step.PLAY) DecisionKind.PLAY_CARD else DecisionKind.UPGRADE_CARD

    fun forecast(seat: String): Forecast = forecasts.getOrPut(seat) { walk(seat) }

    /**
     * Walks a fork to [seat]'s next question. The fork halts when the step changes, so an all-pass turn,
     * the reveal and the next round are out of reach. Other seats get a placeholder, never their lock.
     */
    private fun walk(seat: String): Forecast {
        val fork = session.forkHalting { it.step != step }
        val own = locks[seat].orEmpty()
        var used = 0
        var hero: HeroView? = null
        while (true) {
            val d = fork.pending
            if (d == null || d.kind !in KINDS) return Forecast(null, hero, fork, null)
            if (d.seat != seat) {
                fork.answer(d.id, d.options.first())
                continue
            }
            hero = fork.view(seat).heroes.first { it.heroId == seat }
            if (used == own.size) return Forecast(d, hero, fork, null)
            val answered = fork.answer(d.id, own[used])
            if (answered !is AnswerResult.Accepted) return Forecast(d, hero, fork, used)
            used++
            hero = fork.view(seat).heroes.first { it.heroId == seat }
        }
    }

    /** The seats in [order] still to answer here. */
    fun open(order: List<String>): List<String> = order.filter { forecast(it).question != null }

    /** Everyone else: never a seat with a question left, so a seat's number of questions stays private. */
    fun locked(order: List<String>): List<String> = order.filter { forecast(it).question == null }

    companion object {
        const val PLAY = "play"
        const val LEVEL = "level"

        val KINDS: Set<DecisionKind> =
            setOf(DecisionKind.PLAY_CARD, DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE)

        /** The window [session] stands in, or null when its question is an ordinary sequential one. */
        fun keyOf(session: EngineSession): WindowKey? {
            val d = session.pending ?: return null
            val state = session.state
            return when {
                d.kind !in KINDS -> null
                state.step == Step.PLAY -> WindowKey(state.round, state.turn, PLAY)
                state.step == Step.LEVEL_UP -> WindowKey(state.round, state.turn, LEVEL)
                else -> null
            }
        }

        fun of(session: EngineSession, locks: Map<String, List<Choice>> = emptyMap()): SimultaneousWindow? {
            val key = keyOf(session) ?: return null
            return SimultaneousWindow(session, session.state.step, key, locks)
        }

        /** The real question is the forecast one if everything but the engine's question counter matches. */
        fun sameQuestion(a: Decision, b: Decision): Boolean = a.copy(id = 0) == b.copy(id = 0)
    }
}
