package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.StoredAnswer
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/** The hashes a game's past is checked against on a restart (docs/ONLINE.md "A rules fix"). */
internal object GameHashes {
    private val json = Json { allowStructuredMapKeys = true }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

    /** The rolling hash after [events]: their text is all that is compared, so a new field stays harmless. */
    fun events(previous: String, events: List<GameEvent>): String =
        sha256(
            buildString {
                append(previous)
                for (e in events) append('\n').append(e.text)
            },
        )

    /** The state at the start of a turn, when the answer brought one: null on every other answer. */
    fun state(session: EngineSession, events: List<GameEvent>): String? =
        if (events.any { it is GameEvent.TurnStarted }) {
            sha256(json.encodeToString(GameState.serializer(), session.state))
        } else {
            null
        }
}

/** How many answers each seat has given, overall (for its tickets) and in the window standing open. */
internal class AnswerTally {
    private val total = HashMap<String, Long>()
    private var window: WindowKey? = null
    private val inWindow = HashMap<String, Int>()

    fun total(seat: String): Long = total[seat] ?: 0

    fun inWindow(seat: String, key: WindowKey): Int = if (window == key) inWindow[seat] ?: 0 else 0

    /** Called before an answer with the window it answers, null for a sequential question. */
    fun note(seat: String, key: WindowKey?) {
        total[seat] = total(seat) + 1
        if (key == null) return
        if (window != key) {
            window = key
            inWindow.clear()
        }
        inWindow[seat] = (inWindow[seat] ?: 0) + 1
    }
}

/** A game rebuilt from its answers, up to the question it was waiting on. */
internal class Replayed(val session: EngineSession, val tally: AnswerTally, val eventsHash: String, val answers: Long)

/** Why a game's past no longer stands, in words for the log. */
internal class PastChanged(message: String) : Exception(message)

internal object Replay {
    /** Starts a game's hash chain: the events it opens with. */
    fun begin(session: EngineSession): String = GameHashes.events("", session.events)

    /**
     * Rebuilds [row] answer by answer, comparing each answer's hashes, whichever build wrote them.
     * Throws [PastChanged] when the past differs; anything an answer that no longer fits throws is the
     * same verdict, and [dev.quinton.goa.server.game.room.RoomCore] voids the game.
     */
    @Suppress("ThrowsCount")
    fun run(engine: GameEngine, row: GameRow, answers: List<StoredAnswer>): Replayed {
        val session = engine.restoreFrom(row.start, emptyList()) as EngineSession
        val tally = AnswerTally()
        var hash = begin(session)
        for (a in answers.sortedBy { it.seq }) {
            val key = SimultaneousWindow.keyOf(session)
            val pending = session.pending ?: throw PastChanged("Answer ${a.seq} after the end of the game")
            if (pending.seat != a.record.seat) throw PastChanged("Answer ${a.seq} is for another seat")
            val result = session.answer(a.record.decisionId, a.record.choice)
            if (result !is AnswerResult.Accepted) throw PastChanged("Answer ${a.seq} no longer fits: $result")
            tally.note(a.record.seat, key)
            hash = GameHashes.events(hash, result.events)
            if (hash != a.eventsHash) throw PastChanged("The log differs at answer ${a.seq}")
            if (GameHashes.state(session, result.events) != a.stateHash) {
                throw PastChanged("The state differs at answer ${a.seq}")
            }
        }
        return Replayed(session, tally, hash, answers.size.toLong())
    }
}
