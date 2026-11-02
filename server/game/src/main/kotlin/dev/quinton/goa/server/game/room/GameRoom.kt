package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.AnswerRecord
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.online.DecisionClock
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.TurnRef
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.server.game.store.AnswerOrigin
import dev.quinton.goa.server.game.store.GameResult
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow
import dev.quinton.goa.server.game.store.GameStore
import dev.quinton.goa.server.game.store.StoredAnswer
import dev.quinton.goa.server.game.store.StoredLock
import kotlin.coroutines.cancellation.CancellationException
import org.slf4j.LoggerFactory

/** What every room runs on. */
internal class RoomEnv(
    val store: GameStore,
    val rules: RoomRules,
    val mapper: MatchMapper,
    val now: () -> Long,
)

/** One seat's socket: a person with two seats (a double pilot) has one of these per seat. */
internal class SeatConnection(val seat: String, val accountId: String, val sink: SeatSink, var cursor: Int) {
    /** Has not had a table yet, whether or not anything changed. */
    var fresh: Boolean = true
}

/** A queued answer and the forecast it answered, for the comparison when the real question comes. */
internal class LockEntry(val stored: StoredLock, val question: Decision)

/**
 * One running game: the session, the locks, the clocks and who is connected. Not thread-safe:
 * [RoomCore] calls it holding this room's mutex, because a game session isn't either. Every change to a
 * game is on disk before any frame about it is built ([apply], [lock]).
 */
internal class GameRoom(
    val row: GameRow,
    val seats: List<GameSeatRow>,
    val players: List<SeatPlayer>,
    replayed: Replayed,
    private val takeover: Takeover,
    private val env: RoomEnv,
) {
    private var session: EngineSession = replayed.session
    private val tally: AnswerTally = replayed.tally
    private var eventsHash: String = replayed.eventsHash
    private var answers: Long = replayed.answers

    private val order: List<String> = seats.sortedBy { it.seatOrder }.map { it.heroId }
    private val locks = HashMap<String, MutableList<LockEntry>>()
    private val dropped = HashMap<String, Long>()
    private var windowCache: SimultaneousWindow? = null
    private var windowFresh = false

    private val clocks = SeatClocks(env.rules)
    private val connections = ArrayList<SeatConnection>()
    private val left = HashSet<String>()
    private val takenOver = HashSet<String>()
    private var allAwaySince: Long? = env.now()

    private val lastTable = HashMap<String, ServerMessage.Table>()
    private val lastSeq = HashMap<String, Long>()
    private val recent = LinkedHashMap<Long, Choice>()

    /** Set once the game is closed in the store; [RoomCore] then drops this room. */
    var ended: ServerMessage.End? = null
        private set

    /** The accounts that have not heard the end yet, for when they next connect. */
    fun unreachedAccounts(): Set<String> {
        val reached = connections.map { it.accountId }.toSet()
        return seats.filter { it.heroId !in left }.map { it.accountId }.toSet() - reached
    }

    private val window: SimultaneousWindow?
        get() {
            if (!windowFresh) {
                windowCache = SimultaneousWindow.of(session, locks.mapValues { (_, l) -> l.map { it.stored.choice } })
                windowFresh = true
            }
            return windowCache
        }

    private fun changed() {
        windowFresh = false
    }

    private fun seatRow(seat: String): GameSeatRow = seats.first { it.heroId == seat }

    fun seatsOf(accountId: String): List<String> =
        seats.filter { it.accountId == accountId && it.heroId !in left }.map { it.heroId }

    /** Unique across the game's seats, counts only this seat's own questions: it tells no one anything. */
    private fun ticketOf(seat: String): Long {
        val asked = tally.total(seat) + locks[seat].orEmpty().size + (dropped[seat] ?: 0)
        return (asked + 1) * TICKET_STEP + order.indexOf(seat)
    }

    private fun seatOfTicket(ticket: Long): String? = order.getOrNull(ticket.mod(TICKET_STEP).toInt())

    private fun questionOf(seat: String): Decision? {
        val w = window
        if (w != null) return w.forecast(seat).question
        return session.pending?.takeIf { it.seat == seat }
    }

    private fun asked(): Map<String, AskedQuestion> = order.filter { it !in left }.mapNotNull { seat ->
        questionOf(seat)?.let { seat to AskedQuestion(ticketOf(seat), it.kind) }
    }.toMap()

    // ---- answers --------------------------------------------------------------------------------

    /** On disk first, on a fork: if the store fails, the room is as it was. */
    private suspend fun apply(d: Decision, choice: Choice, origin: AnswerOrigin, at: Long) {
        val key = SimultaneousWindow.keyOf(session)
        val next = session.forkSession()
        val result = next.answer(d.id, choice)
        check(result is AnswerResult.Accepted) { "Answer to a forecast question rejected: $result" }
        val hash = GameHashes.events(eventsHash, result.events)
        val state = GameHashes.state(next, result.events)
        val record = AnswerRecord(d.id, d.seat, choice)
        env.store.appendAnswer(StoredAnswer(row.id, answers + 1, record, origin, at, hash, state))
        session = next
        eventsHash = hash
        answers++
        tally.note(d.seat, key)
        changed()
    }

    /** The seat's answer: applied if the game is waiting on it, otherwise queued until it is. */
    private suspend fun submit(seat: String, question: Decision, choice: Choice, origin: AnswerOrigin) {
        val now = env.now()
        val pending = session.pending
        val queued = locks[seat].orEmpty()
        if (pending != null && pending.seat == seat && queued.isEmpty()) {
            apply(pending, choice, origin, now)
            return
        }
        val key = checkNotNull(window) { "Only a simultaneous question can wait" }.key
        val stored = StoredLock(
            gameId = row.id,
            seat = seat,
            ordinal = tally.inWindow(seat, key) + queued.size,
            round = key.round,
            turn = key.turn,
            group = key.group,
            choice = choice,
            origin = origin,
            at = now,
        )
        env.store.putLock(stored)
        locks.getOrPut(seat) { ArrayList() } += LockEntry(stored, question)
        changed()
    }

    private suspend fun dropLocks(seat: String, why: String) {
        log.warn("Game {}: dropping the locks of {}: {}", row.id, seat, why)
        env.store.clearLocks(row.id, seat)
        dropped[seat] = (dropped[seat] ?: 0) + locks.remove(seat).orEmpty().size
        changed()
    }

    /** Rebuilds the locks of a restart: answers already applied or for another window are gone. */
    suspend fun restoreLocks(stored: List<StoredLock>) {
        val key = SimultaneousWindow.keyOf(session)
        for ((seat, list) in stored.groupBy { it.seat }) {
            val live = list.sortedBy { it.ordinal }.filter {
                key != null && it.round == key.round && it.turn == key.turn && it.group == key.group &&
                    it.ordinal >= tally.inWindow(seat, key)
            }
            if (live.size != list.size) env.store.clearLocks(row.id, seat)
            for (lock in live) {
                if (live.size != list.size) env.store.putLock(lock)
                val w = checkNotNull(SimultaneousWindow.of(session, currentLocks(seat)))
                val question = w.forecast(seat).question
                if (question == null || lock.choice !in question.options) {
                    dropLocks(seat, "a saved lock no longer fits")
                    break
                }
                locks.getOrPut(seat) { ArrayList() } += LockEntry(lock, question)
            }
        }
        changed()
    }

    private fun currentLocks(seat: String): Map<String, List<Choice>> =
        mapOf(seat to locks[seat].orEmpty().map { it.stored.choice })

    /**
     * Runs the game up to a question someone has to think about: applies locks when the engine reaches
     * them, answers a lone Pass, and answers for seats given up, at most [COMPUTER_ANSWERS_PER_CALL] of
     * those at once; the next tick continues a game with nobody left in it.
     */
    private suspend fun settle() {
        var computer = 0
        while (ended == null) {
            val pending = session.pending
            if (pending == null) {
                finish()
                return
            }
            if (dropStaleLock() || applyLock(pending) || answerLonePass()) continue
            val seat = order.firstOrNull { it in left && questionOf(it) != null } ?: return
            if (computer++ >= COMPUTER_ANSWERS_PER_CALL) return
            answerByComputer(seat)
        }
    }

    private suspend fun dropStaleLock(): Boolean {
        val w = window ?: return false
        val seat = order.firstOrNull { w.forecast(it).staleFrom != null } ?: return false
        dropLocks(seat, "answer ${w.forecast(seat).staleFrom} no longer fits the forecast")
        return true
    }

    private suspend fun applyLock(pending: Decision): Boolean {
        if (window == null) return false
        val head = locks[pending.seat]?.firstOrNull() ?: return false
        if (!SimultaneousWindow.sameQuestion(head.question, pending) || head.stored.choice !in pending.options) {
            dropLocks(pending.seat, "the real question differs from the forecast")
            return true
        }
        apply(pending, head.stored.choice, head.stored.origin, head.stored.at)
        locks.getValue(pending.seat).removeAt(0)
        if (locks.getValue(pending.seat).isEmpty()) {
            locks.remove(pending.seat)
            env.store.clearLocks(row.id, pending.seat)
        }
        changed()
        return true
    }

    /** Nothing to decide with no cards, so nobody waits for it: everyone can see the hand is empty. */
    private suspend fun answerLonePass(): Boolean {
        for (seat in order) {
            val question = questionOf(seat) ?: continue
            if (question.options == LONE_PASS) {
                submit(seat, question, Choice.Pass, AnswerOrigin.AUTO)
                return true
            }
        }
        return false
    }

    private suspend fun answerByComputer(seat: String) {
        val question = checkNotNull(questionOf(seat))
        val source = window?.forecast(seat)?.session ?: session
        submit(seat, question, takeover.answer(source, question), AnswerOrigin.CLOCK)
    }

    private suspend fun finish() {
        val at = env.now()
        val view = session.view(null)
        val winner = view.winner
        val reason = view.winReason
        val match = if (row.ranked && winner != null && reason != null) {
            env.mapper.matchOf(row, seats, winner, reason, at)
        } else {
            null
        }
        env.store.finish(row.id, GameResult(at, match != null), match)
        publish()
        close(ServerMessage.End(row.id, winner, reason, match != null))
    }

    private fun close(end: ServerMessage.End) {
        ended = end
        connections.forEach { it.sink.send(end) }
    }

    /** The past changed, or nobody came back: closed in the store first, then said in words by the client. */
    suspend fun void(reason: VoidReason) {
        env.store.void(row.id, reason, env.now())
        close(ServerMessage.End(row.id, winner = null, reason = null, rated = false, voided = reason))
    }

    // ---- what people do -------------------------------------------------------------------------

    suspend fun connect(accountId: String, haveEvents: Int, sink: SeatSink): List<SeatConnection> {
        val own = seatsOf(accountId)
        if (own.isEmpty()) return emptyList()
        val now = env.now()
        if (paused(now)) clocks.restart()
        allAwaySince = null
        sink.send(ServerMessage.GameStart(row.id, setup, players, own.toSet(), row.ranked))
        val added = own.map { seat ->
            takenOver -= seat
            clocks.returned(seat)
            SeatConnection(seat, accountId, sink, haveEvents.coerceIn(0, session.events.size))
        }
        connections += added
        settleAndPublish()
        return added
    }

    private val setup get() = row.setup

    /** Anyone connected on a seat may be gone; the last one out starts the away clock. */
    fun disconnect(gone: List<SeatConnection>) {
        if (!connections.removeAll(gone)) return
        val now = env.now()
        for (c in gone) {
            if (connections.none { it.seat == c.seat }) clocks.disconnected(c.seat, now)
        }
        if (connections.isEmpty() && order.any { it !in left }) allAwaySince = now
        publish()
    }

    suspend fun answer(accountId: String, ticket: Long, choice: Choice) {
        val seat = seatOfTicket(ticket)
        if (seat == null || seat in left || seatRow(seat).accountId != accountId) {
            ack(accountId, ticket, false, "Not your seat")
            return
        }
        val question = questionOf(seat)
        if (question == null || ticket != ticketOf(seat)) {
            val same = recent[ticket] == choice
            ack(accountId, ticket, same, if (same) null else "That question has passed")
            return
        }
        if (choice !in question.options) {
            ack(accountId, ticket, false, "Not one of the options")
            return
        }
        @Suppress("TooGenericExceptionCaught") // whatever the disk says: the answer simply did not happen
        try {
            submit(seat, question, choice, AnswerOrigin.HUMAN)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Game {}: could not save an answer", row.id, e)
            ack(accountId, ticket, false, "Could not save the answer, try again")
            return
        }
        remember(ticket, choice)
        ack(accountId, ticket, true, null)
        settleAndPublish()
    }

    private fun remember(ticket: Long, choice: Choice) {
        recent[ticket] = choice
        if (recent.size > RECENT_TICKETS) recent.remove(recent.keys.first())
    }

    private fun ack(accountId: String, ticket: Long, accepted: Boolean, reason: String?) {
        val message = ServerMessage.Ack(row.id, ticket, accepted, reason)
        connections.filter { it.accountId == accountId }.distinctBy { it.sink }.forEach { it.sink.send(message) }
    }

    fun review(accountId: String, rewind: TurnRef?) {
        val own = seatsOf(accountId).toSet()
        if (own.isEmpty()) return
        val message = ServerMessage.Reviewed(
            gameId = row.id,
            reviews = session.review(own),
            pushes = session.events.filterIsInstance<GameEvent.LanePushed>().groupingBy { it.winner }.eachCount(),
            rewind = rewind?.let { session.positionAt(it.round, it.turn, own) },
        )
        connections.filter { it.accountId == accountId }.distinctBy { it.sink }.forEach { it.sink.send(message) }
    }

    /** For good: the computer answers at once from now on, and the result stands for the team. */
    suspend fun leave(accountId: String) {
        val own = seatsOf(accountId)
        if (own.isEmpty()) return
        left += own
        connections.removeAll { it.accountId == accountId }
        if (connections.isEmpty() && order.any { it !in left } && allAwaySince == null) allAwaySince = env.now()
        settleAndPublish()
    }

    // ---- the clock ------------------------------------------------------------------------------

    private fun paused(now: Long): Boolean =
        allAwaySince?.let { now - it >= env.rules.allAwayPauseMillis } == true && order.any { it !in left }

    /** One beat of this game: at most one computer answer per seat whose clock ran out. */
    suspend fun tick() {
        val now = env.now()
        when {
            ended != null -> Unit
            abandoned(now) -> void(VoidReason.ABANDONED)
            !paused(now) -> runClocks(now)
        }
    }

    private fun abandoned(now: Long): Boolean =
        allAwaySince?.let { now - it >= env.rules.abandonMillis } == true && order.any { it !in left }

    private suspend fun runClocks(now: Long) {
        settle()
        if (ended == null) syncClocks(now)
        for ((seat, ticket) in clocks.expired(now)) {
            if (ended == null && questionOf(seat) != null && ticket == ticketOf(seat)) {
                if (connections.none { it.seat == seat }) takenOver += seat
                answerByComputer(seat)
                settle()
            }
        }
        if (ended != null) return
        syncClocks(env.now())
        publish()
    }

    private fun syncClocks(now: Long) {
        clocks.sync(asked(), window, now) { seat -> connections.none { it.seat == seat } }
    }

    /** A new game, or one back from a restart: nobody can have connected yet, so everyone has the whole clock. */
    suspend fun begin() {
        settle()
        if (ended == null) clocks.sync(asked(), window, env.now()) { false }
    }

    suspend fun settleAndPublish() {
        settle()
        if (ended != null) return
        syncClocks(env.now())
        publish()
    }

    // ---- frames ---------------------------------------------------------------------------------

    private fun presence(): Map<String, Presence> = order.associateWith { seat ->
        when {
            seat in left || seat in takenOver -> Presence.COMPUTER
            connections.any { it.seat == seat } -> Presence.HERE
            else -> Presence.AWAY
        }
    }

    private fun clockFor(viewer: String, decision: Decision?): DecisionClock? {
        if (decision == null) return null
        val own = decision.seat == viewer && decision.options.isNotEmpty()
        val deadline = if (window != null && !own) clocks.windowDeadline else clocks.deadline(decision.seat)
        return deadline?.let { DecisionClock(decision.seat, it) }
    }

    /** The table without its cursor: a frame goes out only when this differs, whatever happened privately. */
    private fun table(seat: String): ServerMessage.Table = SeatProjector.table(
        session,
        window,
        seat,
        FrameInputs(row.id, ticket = ticketOf(seat), presence = presence(), clock = { clockFor(seat, it) }),
    )

    private fun publish() {
        val size = session.events.size
        for (seat in connections.map { it.seat }.distinct()) {
            val body = table(seat)
            val changed = body != lastTable[seat]
            val targets = connections.filter { it.seat == seat && (changed || it.fresh || it.cursor < size) }
            if (targets.isEmpty()) continue
            lastTable[seat] = body
            val seq = nextSeq(seat)
            targets.forEach { send(it, body, seq) }
        }
    }

    private fun send(c: SeatConnection, body: ServerMessage.Table, seq: Long) {
        val events = session.events
        c.sink.send(
            body.copy(seq = seq, eventsFrom = c.cursor, events = events.subList(c.cursor, events.size).toList()),
        )
        c.cursor = events.size
        c.fresh = false
    }

    /** Goes up on the seat's own frames only, and across a restart, since it is read off the clock. */
    private fun nextSeq(seat: String): Long {
        val seq = maxOf((lastSeq[seat] ?: 0) + 1, env.now())
        lastSeq[seat] = seq
        return seq
    }

    private companion object {
        val log = LoggerFactory.getLogger(GameRoom::class.java)
        const val TICKET_STEP = 16L
        const val RECENT_TICKETS = 32
        const val COMPUTER_ANSWERS_PER_CALL = 16
        val LONE_PASS = listOf<Choice>(Choice.Pass)
    }
}
