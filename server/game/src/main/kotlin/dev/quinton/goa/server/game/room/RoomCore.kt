package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.TurnRef
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.server.game.store.ClosedGame
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow
import dev.quinton.goa.server.game.store.GameStore
import dev.quinton.goa.server.game.store.RunningGame
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** A display name for a restart: the store keeps accounts, the frames want names. Null: a deleted account. */
fun interface PlayerNames {
    suspend fun nameOf(accountId: String): String?
}

/**
 * The running games (ADR-056, docs/ONLINE.md). Each game has its own mutex, because a game session isn't
 * thread-safe; everything else about a game is in [GameRoom]. Time comes from [now] and only [tick] acts
 * on it, so a test can run a game's clocks by hand; the server calls [launchClocks].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomCore(
    private val engine: GameEngine,
    private val store: GameStore,
    private val rulesBuild: String,
    mapper: MatchMapper,
    private val names: PlayerNames = PlayerNames { null },
    private val rules: RoomRules = RoomRules(),
    private val now: () -> Long = System::currentTimeMillis,
    private val computer: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : GameRooms {
    private class Live(val room: GameRoom, val mutex: Mutex = Mutex())

    /** A closed game whose end some people have not heard; they hear it when they next connect. */
    private class Closed(val end: ServerMessage.End, val accounts: MutableSet<String>)

    private val env = RoomEnv(store, rules, mapper, now)
    private val live = ConcurrentHashMap<String, Live>()
    private val closed = LinkedHashMap<String, Closed>()
    private val closedMutex = Mutex()
    private val lastReview = ConcurrentHashMap<String, Long>()

    /** [SeatLink.close] can't suspend: this runs it in place, or after whoever holds the room's mutex. */
    private val closing = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    override val runningCount: Int get() = live.size

    private fun takeover(gameId: String) = Takeover(gameId.hashCode().toLong() xor AiPlayers.APP_SEED, computer)

    private suspend fun <T> withRoom(gameId: String, block: suspend (GameRoom) -> T): T? {
        val entry = live[gameId] ?: return null
        val result = entry.mutex.withLock {
            if (entry.room.ended != null) return null
            block(entry.room).also { retire(entry.room) }
        }
        return result
    }

    private suspend fun retire(room: GameRoom) {
        val end = room.ended ?: return
        live.remove(room.row.id)
        rememberClosed(room.row.id, end, room.unreachedAccounts())
    }

    private suspend fun rememberClosed(gameId: String, end: ServerMessage.End, accounts: Set<String>) {
        if (accounts.isEmpty()) return
        closedMutex.withLock {
            closed[gameId] = Closed(end, accounts.toMutableSet())
            while (closed.size > MAX_CLOSED) closed.remove(closed.keys.first())
        }
    }

    /** Each game on its own: one that can't be brought back must not keep the server, or the others, down. */
    @Suppress("TooGenericExceptionCaught") // whatever a store does here, the server must still come up
    override suspend fun restore() {
        val ids = try {
            store.runningGameIds()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Running games can't be listed, so none are restored", e)
            return
        }
        for (id in ids) restoreGame(id)
        log.info("Restored {} games of {} found", live.size, ids.size)
    }

    /** An answer that no longer fits may throw anything a card script can, a row that can't be read likewise. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun restoreGame(id: String) {
        try {
            restoreOne(store.runningGame(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLException) {
            log.error("Game {} can't be read from the store, so it is skipped: {}", id, e.javaClass.simpleName)
        } catch (e: Exception) {
            voidBroken(id, e)
        } catch (e: StackOverflowError) {
            voidBroken(id, e)
        }
    }

    /** Failures quote what they failed on, which may be a hidden card: only a replay's own words get logged. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun voidBroken(id: String, failure: Throwable) {
        log.warn("Game {} is void: {}", id, (failure as? PastChanged)?.message ?: failure.javaClass.simpleName)
        try {
            val accounts = store.accountsOf(id)
            store.void(id, VoidReason.RULES_CHANGED, now())
            val end = ServerMessage.End(
                id,
                winner = null,
                reason = null,
                rated = false,
                voided = VoidReason.RULES_CHANGED,
            )
            rememberClosed(id, end, accounts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Game {} can't be voided either, so it is skipped: {}", id, e.javaClass.simpleName)
        }
    }

    private suspend fun restoreOne(game: RunningGame) {
        val id = game.game.id
        if (game.game.rulesBuild != rulesBuild) {
            log.info("Game {} began on rules {}, now {}: checking its past", id, game.game.rulesBuild, rulesBuild)
        }
        val replayed = Replay.run(engine, game.game, game.answers)
        val room = GameRoom(game.game, game.seats, playersOf(game.seats), replayed, takeover(id), env)
        room.restoreLocks(game.locks)
        room.begin()
        if (room.ended == null) live[id] = Live(room) else retire(room)
    }

    override suspend fun start(roomCode: String, setup: GameSetup, players: List<SeatPlayer>, ranked: Boolean): String {
        val human = setup.copy(seats = setup.seats.map { it.copy(controller = Controller.Human) })
        val session = engine.newGame(human) as EngineSession
        val id = newId()
        val row = GameRow(
            id = id,
            roomCode = roomCode,
            setup = human,
            start = session.save(),
            rulesBuild = rulesBuild,
            engineVersion = engine.engineVersion,
            ranked = ranked,
            mapId = human.mapId,
            length = human.length,
            createdAt = now(),
            awaySince = now(),
        )
        val seats = human.seats.mapIndexed { i, s ->
            val account = players.first { it.seat == s.heroId }.accountId
            GameSeatRow(id, s.heroId, account, s.team, s.handicap, i)
        }
        store.createGame(row, seats)
        val replayed = Replayed(session, AnswerTally(), Replay.begin(session), 0)
        val room = GameRoom(row, seats, players, replayed, takeover(id), env)
        room.begin()
        live[id] = Live(room)
        return id
    }

    override suspend fun connect(gameId: String, accountId: String, haveEvents: Int, sink: SeatSink): SeatLink? {
        val added = withRoom(gameId) { it.connect(accountId, haveEvents, sink) }
        if (added == null) return tellEnd(gameId, accountId, haveEvents, sink)
        if (added.isEmpty()) return null
        return SeatLink {
            closing.launch { withRoom(gameId) { it.disconnect(added) } }
        }
    }

    /**
     * A person coming back to a game that is over gets how it ended, with the last table and the events they
     * missed: a phone that was away holds a board from before the end, and a freshly opened one holds nothing.
     * From the disk, so a restart doesn't lose it. A seat given up gets nothing, as it does while it runs.
     */
    private suspend fun tellEnd(gameId: String, accountId: String, haveEvents: Int, sink: SeatSink): SeatLink? {
        val unheard = closedMutex.withLock {
            val game = closed[gameId]
            if (game != null && game.accounts.remove(accountId) && game.accounts.isEmpty()) closed.remove(gameId)
            game?.end
        }
        val past = store.closedGame(gameId)
        val own = past?.game?.seats.orEmpty().filter { it.accountId == accountId && !it.left }
        val pruned = past != null && past.voided == null && past.game.answers.isEmpty()
        if (past == null || own.isEmpty() || pruned) {
            unheard?.let(sink::send)
            return unheard?.let { SeatLink {} }
        }
        val session = withContext(computer) { replayed(past) }
        if (session != null) sendLastTables(past, own.map { it.heroId }, session, haveEvents, sink)
        val view = session?.view(null)
        sink.send(ServerMessage.End(gameId, view?.winner, view?.winReason, past.rated, past.voided))
        return SeatLink {}
    }

    /** Null for a game whose past no longer replays: voided for that very reason, so there is no table to show. */
    @Suppress("TooGenericExceptionCaught") // as in restoreOne: an answer that no longer fits may throw anything
    private fun replayed(past: ClosedGame): EngineSession? = try {
        Replay.run(engine, past.game.game, past.game.answers).session
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.info("Game {} can't be shown: {}", past.game.game.id, e.javaClass.simpleName)
        null
    }

    private suspend fun sendLastTables(
        past: ClosedGame,
        own: List<String>,
        session: EngineSession,
        haveEvents: Int,
        sink: SeatSink,
    ) {
        val game = past.game.game
        sink.send(ServerMessage.GameStart(game.id, game.setup, playersOf(past.game.seats), own.toSet(), game.ranked))
        var cursor = haveEvents.coerceIn(0, session.events.size)
        for (seat in own) {
            val events = session.events.drop(cursor)
            cursor += events.size
            val inputs = FrameInputs(
                game.id,
                seq = now(),
                ticket = 0,
                eventsFrom = cursor - events.size,
                events = events,
            )
            sink.send(SeatProjector.table(session, null, seat, inputs).copy(decision = null))
        }
    }

    private suspend fun playersOf(seats: List<GameSeatRow>): List<SeatPlayer> =
        seats.sortedBy { it.seatOrder }.map { s ->
            SeatPlayer(s.heroId, s.accountId, names.nameOf(s.accountId) ?: DEFAULT_NAME, s.team)
        }

    override suspend fun answer(gameId: String, accountId: String, ticket: Long, choice: Choice) {
        withRoom(gameId) { it.answer(accountId, ticket, choice) }
    }

    /** A review can be hundreds of KB: one per person per [RoomRules.reviewEveryMillis], the rest ignored. */
    override suspend fun review(gameId: String, accountId: String, rewind: TurnRef?) {
        val at = now()
        val last = lastReview[accountId]
        if (last != null && at - last < rules.reviewEveryMillis) return
        lastReview[accountId] = at
        withRoom(gameId) { it.review(accountId, rewind) }
    }

    override suspend fun leave(gameId: String, accountId: String) {
        withRoom(gameId) { it.leave(accountId) }
    }

    override suspend fun gamesOf(accountId: String): List<String> {
        val ended = closedMutex.withLock { closed.filterValues { accountId in it.accounts }.keys.toList() }
        return runningOf(accountId) + ended
    }

    override suspend fun runningOf(accountId: String): List<String> =
        live.values.filter { it.room.seats.any { s -> s.accountId == accountId } }
            .filter { entry -> entry.mutex.withLock { entry.room.seatsOf(accountId).isNotEmpty() } }
            .map { it.room.row.id }

    /** One beat of every game's clocks. A game that fails here is left as it was for the next beat. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun tick() {
        for (id in live.keys.toList()) {
            try {
                withRoom(id) { it.tick() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Game {}: the clock failed", id, e)
            }
        }
    }

    /** Runs [tick] until [scope] ends. */
    fun launchClocks(scope: CoroutineScope, everyMillis: Long = TICK_MILLIS): Job = scope.launch {
        while (isActive) {
            delay(everyMillis)
            tick()
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(RoomCore::class.java)
        const val DEFAULT_NAME = "Former player"
        const val MAX_CLOSED = 4096
        const val TICK_MILLIS = 1_000L
    }
}
