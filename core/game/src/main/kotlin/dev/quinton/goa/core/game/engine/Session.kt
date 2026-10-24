package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An answer as recorded in the game's history. */
@Serializable
data class AnswerRecord(val decisionId: Long, val seat: String, val choice: Choice)

/** A persistent (shared-tail) list, newest first: forks share history and events for free. */
internal class Chain<T>(val head: T, val tail: Chain<T>?, val size: Int) {
    companion object {
        fun <T> push(c: Chain<T>?, v: T): Chain<T> = Chain(v, c, (c?.size ?: 0) + 1)

        fun <T> toList(c: Chain<T>?): List<T> {
            val out = ArrayList<T>(c?.size ?: 0)
            var at = c
            while (at != null) {
                out += at.head
                at = at.tail
            }
            out.reverse()
            return out
        }
    }
}

@Serializable
internal class SavedPayload(
    val initial: GameState,
    val answers: List<AnswerRecord>,
)

/**
 * A game in progress (ENGINE.md "Re-execution"): [checkpoint] is the state at the start of the current
 * step; [segment] the answers given since. Every answer re-runs the step from the checkpoint; a run
 * that reaches an unanswered decision stops there and its partly applied copy is what [view] shows.
 */
class EngineSession internal constructor(
    private val data: EngineData,
    override val setup: GameSetup,
    /** The state the game (or position) started from; never mutated. */
    private val initial: GameState,
    private val lenient: Random? = null,
) : GameSession {
    private var checkpoint: GameState = initial.copy()
    private var segment: ArrayList<Choice> = ArrayList()
    private var display: GameState = checkpoint
    private var pendingDecision: Decision? = null
    private var history: Chain<AnswerRecord>? = null
    private var committed: Chain<GameEvent>? = null
    private var partial: List<GameEvent> = emptyList()
    private var eventsCache: List<GameEvent>? = null

    override val pending: Decision? get() = pendingDecision

    override val events: List<GameEvent>
        get() = eventsCache ?: (Chain.toList(committed) + partial).also { eventsCache = it }

    private val eventCount: Int get() = (committed?.size ?: 0) + partial.size

    /** The state shown now (tests, the AI's evaluation, puzzles). Treat as read-only. */
    val state: GameState get() = display

    /** The state at the start of the current step (puzzle solver's transposition key). Read-only. */
    internal val checkpointState: GameState get() = checkpoint

    /** The answers given in the current step so far (with [checkpointState] they fix the position). */
    internal val stepAnswers: List<Choice> get() = segment

    /**
     * Stops the game before a step whose start state matches (a puzzle's deadline): [pending] becomes
     * null as at the end of a game. Checked only between steps; copied by [forkSession].
     */
    internal var haltWhen: ((GameState) -> Boolean)? = null

    internal fun start(): EngineSession {
        advance()
        return this
    }

    private fun advance() {
        eventsCache = null
        var steps = 0
        while (true) {
            // A round has well under a thousand steps without a question; more means a flow bug.
            check(++steps < MAX_STEPS_WITHOUT_DECISION) { "The game made no progress (step ${checkpoint.step})" }
            if (segment.isEmpty() && haltWhen?.invoke(checkpoint) == true) {
                display = checkpoint
                pendingDecision = null
                return
            }
            val work = checkpoint.copy()
            val g = Game(data, work, segment, RunMode.NORMAL, lenient)
            try {
                g.runStep()
                commit(work, g)
                if (work.step == Step.GAME_OVER) {
                    pendingDecision = null
                    return
                }
            } catch (s: Suspend) {
                display = work
                partial = ArrayList(g.events)
                pendingDecision = s.decision
                return
            } catch (_: GameEnded) {
                commit(work, g)
                pendingDecision = null
                return
            }
        }
    }

    private fun commit(work: GameState, g: Game) {
        work.decisions += g.asked
        checkpoint = work
        display = work
        for (e in g.events) committed = Chain.push(committed, e)
        partial = emptyList()
        segment = ArrayList()
    }

    override fun view(seat: String?): GameView = Views.build(data, setup, display, seat)

    override fun answer(decisionId: Long, choice: Choice): AnswerResult {
        val p = pendingDecision ?: return AnswerResult.Rejected("The game is over")
        if (decisionId != p.id) return AnswerResult.Rejected("Stale decision $decisionId (pending ${p.id})")
        if (choice !in p.options) return AnswerResult.Rejected("Not one of the options")
        val before = eventCount
        segment.add(choice)
        history = Chain.push(history, AnswerRecord(decisionId, p.seat, choice))
        advance()
        return AnswerResult.Accepted(events.drop(before))
    }

    override fun save(): SavedGame = SavedGame(
        engineVersion = GameEngine.ENGINE_VERSION,
        setup = setup,
        payload = JSON.encodeToString(SavedPayload.serializer(), SavedPayload(initial, Chain.toList(history))),
    )

    override fun fork(): GameSession = forkSession()

    /** A copy with its own future; shares the immutable history and event chains. */
    fun forkSession(): EngineSession {
        val f = EngineSession(data, setup, initial, lenient)
        f.checkpoint = checkpoint
        f.segment = ArrayList(segment)
        f.display = if (display === checkpoint) f.checkpoint else display.copy()
        f.pendingDecision = pendingDecision
        f.history = history
        f.committed = committed
        f.partial = partial
        f.haltWhen = haltWhen
        // The checkpoint is never mutated in place (each run works on a copy), so sharing it is safe.
        return f
    }

    /**
     * A fork that is answered at once (the puzzle solver): like [forkSession] but shares the displayed
     * state instead of copying it, since the next answer replaces it. Don't read its state before answering.
     */
    internal fun forkForAnswer(): EngineSession {
        val f = EngineSession(data, setup, initial, lenient)
        f.checkpoint = checkpoint
        f.segment = ArrayList(segment)
        f.display = display
        f.pendingDecision = pendingDecision
        f.history = history
        f.committed = committed
        f.partial = partial
        f.haltWhen = haltWhen
        return f
    }

    override fun determinize(viewer: Team, seed: Long): GameSession {
        val rng = Random(seed)
        val base = Determinizer.sample(data, checkpoint, viewer, rng)
        val f = EngineSession(data, setup, base, Random(seed xor 0x5DEECE66DL))
        f.segment = ArrayList(segment)
        f.advance()
        return f
    }

    override fun undoToLastHumanDecision(): Boolean {
        val records = Chain.toList(history)
        val humans = setup.seats.filter { it.controller == Controller.Human }.map { it.heroId }.toSet()
        val index = records.indexOfLast { it.seat in humans }
        if (index < 0) return false
        val replay = replay(data, setup, initial, records.subList(0, index))
        checkpoint = replay.checkpoint
        segment = replay.segment
        display = replay.display
        pendingDecision = replay.pendingDecision
        history = replay.history
        committed = replay.committed
        partial = replay.partial
        eventsCache = null
        return true
    }

    companion object {
        private const val MAX_STEPS_WITHOUT_DECISION = 10_000

        internal val JSON = Json {
            ignoreUnknownKeys = true
            allowStructuredMapKeys = true
        }

        internal fun replay(
            data: EngineData,
            setup: GameSetup,
            initial: GameState,
            answers: List<AnswerRecord>,
        ): EngineSession {
            val s = EngineSession(data, setup, initial).start()
            for (a in answers) {
                val r = s.answer(a.decisionId, a.choice)
                check(r is AnswerResult.Accepted) { "Saved answer ${a.decisionId} no longer fits: $r" }
            }
            return s
        }
    }
}
