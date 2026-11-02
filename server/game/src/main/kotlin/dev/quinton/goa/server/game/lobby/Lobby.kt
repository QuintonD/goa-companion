package dev.quinton.goa.server.game.lobby

import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomSeat
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.server.game.room.GameRooms
import dev.quinton.goa.server.game.store.LobbyStore
import dev.quinton.goa.server.game.store.RoomRow
import dev.quinton.goa.server.game.store.RoomSeatRow
import dev.quinton.goa.server.game.store.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why the lobby said no. [code] is what REST sends; a socket can only say [refusal]. */
enum class LobbyError(val code: String, val status: Int, val refusal: Refusal, val retryAfterSeconds: Int? = null) {
    BAD_NAME("bad_name", 400, Refusal.ROOM_TAKEN),
    BAD_RECOVERY_CODE("bad_recovery_code", 401, Refusal.BAD_TOKEN),
    BAD_ROOM("bad_room", 400, Refusal.ROOM_TAKEN),
    BAD_SEAT("bad_seat", 400, Refusal.ROOM_TAKEN),
    BAD_HERO("bad_hero", 400, Refusal.ROOM_TAKEN),
    MAP_UNAVAILABLE("map_unavailable", 400, Refusal.ROOM_TAKEN),
    NO_SUCH_ROOM("no_such_room", 404, Refusal.NO_SUCH_ROOM),
    ROOM_TAKEN("room_taken", 409, Refusal.ROOM_TAKEN),
    NOT_HOST("not_host", 403, Refusal.NOT_HOST),
    NOT_READY("not_ready", 409, Refusal.NOT_READY),
    SETUP_REFUSED("setup_refused", 409, Refusal.ROOM_TAKEN),
    TOO_MANY_GAMES("too_many_games", 409, Refusal.TOO_MANY_GAMES),
    TOO_MANY_ROOMS("too_many_rooms", 409, Refusal.TOO_MANY_ROOMS),
    ROOMS_FULL("rooms_full", 503, Refusal.ROOMS_FULL, retryAfterSeconds = 300),
    DRAINING("draining", 503, Refusal.DRAINING, retryAfterSeconds = 3600),
}

class LobbyRefused(val error: LobbyError) : Exception(error.code)

/**
 * Rooms, from a code to a started game (docs/ONLINE.md "Rules of the mode"). A room is read, changed and
 * written back under one lock, so two people can't take one seat, one hero or one start. [onChange] hears
 * every change, outside the lock, and must not block.
 */
class Lobby(
    private val store: LobbyStore,
    private val engine: GameFactory,
    private val rooms: GameRooms,
    /** Running games at once: what [start] refuses past. */
    private val maxRooms: Int,
    private val draining: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val coin: () -> Boolean = Secrets::coin,
    private val onChange: (RoomView) -> Unit = {},
    /** Open rooms at once: what [create] refuses past, so a waiting room never holds up a game. */
    private val maxOpenRooms: Int = DEFAULT_MAX_OPEN_ROOMS,
) {
    private val lock = Mutex()

    /** Who made each open room, by client key: in memory, so no address is ever on disk. A restart forgets it. */
    private val madeBy = HashMap<String, String>()

    /** [client] is where the call came from: many accounts on one address share one host's limit. */
    suspend fun create(accountId: String, request: CreateRoom, client: String = accountId): RoomView = change {
        if (draining()) throw LobbyRefused(LobbyError.DRAINING)
        if (request.teamSize !in TEAM_SIZES || request.mapId != mapFor(request.teamSize)) {
            throw LobbyRefused(LobbyError.BAD_ROOM)
        }
        if (request.mapId !in engine.playableMaps) throw LobbyRefused(LobbyError.MAP_UNAVAILABLE)
        val now = clock()
        val open = store.openRooms().filter { it.expiresAt > now }
        madeBy.keys.retainAll(open.map { it.code }.toSet())
        if (open.count { it.hostAccount == accountId } >= MAX_OPEN_ROOMS_PER_HOST ||
            madeBy.values.count { it == client } >= MAX_OPEN_ROOMS_PER_HOST
        ) {
            throw LobbyRefused(LobbyError.TOO_MANY_ROOMS)
        }
        requireRoomForGames(accountId)
        if (open.size >= maxOpenRooms) throw LobbyRefused(LobbyError.ROOMS_FULL)
        val seats = Team.entries.flatMap { team -> List(request.teamSize) { RoomSeatRow(team, it) } }
        // The host sits down with the room; joining it afterwards changes nothing.
        val seated = seats.mapIndexed { i, seat -> if (i == 0) seat.copy(accountId = accountId) else seat }
        val code = freshCode()
        madeBy[code] = client
        RoomRow(
            code = code,
            hostAccount = accountId,
            mapId = request.mapId,
            length = request.length,
            teamSize = request.teamSize,
            ranked = request.ranked,
            createdAt = now,
            expiresAt = now + ALONE_MILLIS,
            status = RoomStatus.OPEN,
            seats = seated,
        )
    }

    /** The room as it stands, for anyone holding its code. */
    suspend fun view(rawCode: String): RoomView = lock.withLock { viewOf(liveRoom(rawCode)) }

    suspend fun join(accountId: String, rawCode: String): RoomView = change {
        val room = liveRoom(rawCode)
        val member = room.seats.any { it.accountId == accountId }
        if (room.status == RoomStatus.STARTED && !member) throw LobbyRefused(LobbyError.ROOM_TAKEN)
        if (member) return@change room
        requireRoomForGames(accountId)
        val seat = freeSeat(room) ?: throw LobbyRefused(LobbyError.ROOM_TAKEN)
        room.withSeat(seat.copy(accountId = accountId))
    }

    /** Leaving a room you are not in changes nothing. A game is left over the socket, not here. */
    suspend fun leave(accountId: String, rawCode: String): RoomView = change {
        val room = store.room(Secrets.normalizeCode(rawCode)) ?: throw LobbyRefused(LobbyError.NO_SUCH_ROOM)
        val seat = room.seats.firstOrNull { it.accountId == accountId }
        when {
            room.status == RoomStatus.STARTED && seat != null -> throw LobbyRefused(LobbyError.ROOM_TAKEN)
            room.status != RoomStatus.OPEN || seat == null -> room
            else -> room.without(accountId)
        }
    }

    suspend fun pickSeat(
        accountId: String,
        rawCode: String,
        team: Team,
        index: Int,
        heroId: String?,
    ): RoomView = change {
        val room = openRoom(rawCode)
        val target = room.seats.firstOrNull { it.team == team && it.index == index }
            ?: throw LobbyRefused(LobbyError.BAD_SEAT)
        if (target.accountId != null && target.accountId != accountId) throw LobbyRefused(LobbyError.ROOM_TAKEN)
        if (heroId != null && heroId !in engine.playableHeroes) throw LobbyRefused(LobbyError.BAD_HERO)
        if (heroId != null && room.seats.any { it.heroId == heroId && it.accountId != accountId }) {
            throw LobbyRefused(LobbyError.ROOM_TAKEN)
        }
        val current = room.seats.firstOrNull { it.accountId == accountId }
        if (current == null) requireRoomForGames(accountId)
        if (current == target && target.heroId == heroId) return@change room
        val cleared = current?.let { room.withSeat(it.copy(accountId = null, heroId = null, ready = false)) } ?: room
        cleared.withSeat(target.copy(accountId = accountId, heroId = heroId, ready = false))
    }

    suspend fun ready(accountId: String, rawCode: String, ready: Boolean): RoomView = change {
        val room = openRoom(rawCode)
        val seat = room.seats.firstOrNull { it.accountId == accountId } ?: throw LobbyRefused(LobbyError.NOT_READY)
        if (ready && seat.heroId == null) throw LobbyRefused(LobbyError.NOT_READY)
        room.withSeat(seat.copy(ready = ready))
    }

    /** The host's start. A repeat of a start that went through changes nothing. */
    suspend fun start(accountId: String, rawCode: String): RoomView = change {
        val room = liveRoom(rawCode)
        if (room.hostAccount != accountId) throw LobbyRefused(LobbyError.NOT_HOST)
        if (room.status == RoomStatus.STARTED) return@change room
        // A game begun now could outlast the drain, and a deploy replays it under new rules.
        if (draining()) throw LobbyRefused(LobbyError.DRAINING)
        if (room.seats.any { it.accountId == null || it.heroId == null || !it.ready }) {
            throw LobbyRefused(LobbyError.NOT_READY)
        }
        room.seats.forEach { requireRoomForGames(it.accountId.orEmpty()) }
        if (rooms.runningCount >= maxRooms) throw LobbyRefused(LobbyError.ROOMS_FULL)
        val setup = setupOf(room, if (coin()) Team.RED else Team.BLUE)
        preflight(setup)
        val names = store.names(room.seats.mapNotNull { it.accountId })
        val players = room.seats.map {
            val id = it.accountId.orEmpty()
            SeatPlayer(seat = it.heroId.orEmpty(), accountId = id, name = names[id].orEmpty(), team = it.team)
        }
        val gameId = rooms.start(room.code, setup, players, room.ranked)
        room.copy(status = RoomStatus.STARTED, gameId = gameId)
    }

    /** Closes rooms nobody started in time and tells their watchers. */
    suspend fun sweep() {
        val closed = lock.withLock {
            val now = clock()
            store.openRooms().filter { it.expiresAt <= now }.map { room ->
                val done = room.copy(status = RoomStatus.CLOSED)
                store.putRoom(done)
                viewOf(done)
            }
        }
        closed.forEach(onChange)
    }

    /** Rooms the person sits in, for a rename's new name or a deleted account's empty seat. */
    suspend fun openRoomsOf(accountId: String): List<String> = lock.withLock {
        store.openRooms().filter { room -> room.seats.any { it.accountId == accountId } }.map { it.code }
    }

    /** Tells a room's watchers again after something outside the room changed, such as a name. */
    suspend fun announce(code: String) {
        val view = lock.withLock { store.room(code)?.let { viewOf(it) } }
        if (view != null) onChange(view)
    }

    suspend fun myGames(accountId: String): List<MyGame> {
        val summaries = store.gameSummaries(rooms.gamesOf(accountId))
        val names = store.names(summaries.flatMap { game -> game.seats.map { it.accountId } })
        return summaries.map { game ->
            MyGame(
                gameId = game.gameId,
                roomCode = game.roomCode,
                mapId = game.mapId,
                seats = game.seats.map { SeatPlayer(it.heroId, it.accountId, names[it.accountId].orEmpty(), it.team) },
            )
        }
    }

    /** Open rooms still waiting: what [maxOpenRooms] counts, and `/v1/health` says beside the running games. */
    suspend fun openCount(): Int = store.openRooms().count { it.expiresAt > clock() }

    private suspend fun change(block: suspend () -> RoomRow): RoomView {
        val (view, changed) = lock.withLock {
            val after = block().withExpiry(clock())
            val changed = store.room(after.code) != after
            if (changed) store.putRoom(after)
            viewOf(after) to changed
        }
        if (changed) onChange(view)
        return view
    }

    private suspend fun requireRoomForGames(accountId: String) {
        if (rooms.gamesOf(accountId).size >= MAX_RUNNING_GAMES) throw LobbyRefused(LobbyError.TOO_MANY_GAMES)
    }

    private suspend fun liveRoom(rawCode: String): RoomRow {
        val room = Secrets.normalizeCode(rawCode).takeIf(Secrets::isRoomCode)?.let { store.room(it) }
        val expired = room?.status == RoomStatus.OPEN && room.expiresAt <= clock()
        if (room == null || room.status == RoomStatus.CLOSED || expired) throw LobbyRefused(LobbyError.NO_SUCH_ROOM)
        return room
    }

    private suspend fun openRoom(rawCode: String): RoomRow {
        val room = liveRoom(rawCode)
        if (room.status != RoomStatus.OPEN) throw LobbyRefused(LobbyError.ROOM_TAKEN)
        return room
    }

    private suspend fun freshCode(): String {
        while (true) {
            val code = Secrets.roomCode()
            if (store.room(code) == null) return code
        }
    }

    private suspend fun preflight(setup: GameSetup) {
        try {
            withContext(Dispatchers.Default) { engine.newGame(setup) }
        } catch (_: IllegalArgumentException) {
            throw LobbyRefused(LobbyError.SETUP_REFUSED)
        } catch (_: IllegalStateException) {
            throw LobbyRefused(LobbyError.SETUP_REFUSED)
        }
    }

    private suspend fun viewOf(room: RoomRow): RoomView {
        val names = store.names(room.seats.mapNotNull { it.accountId })
        return RoomView(
            code = room.code,
            hostAccountId = room.hostAccount,
            mapId = room.mapId,
            length = room.length,
            teamSize = room.teamSize,
            ranked = room.ranked,
            seats = room.seats.map {
                RoomSeat(it.team, it.index, it.accountId, it.accountId?.let(names::get), it.heroId, it.ready)
            },
            status = room.status,
            expiresAt = room.expiresAt,
            gameId = room.gameId,
        )
    }

    companion object {
        const val MAX_RUNNING_GAMES = 3
        const val MAX_OPEN_ROOMS_PER_HOST = 3
        const val DEFAULT_MAX_OPEN_ROOMS = 10

        /** An open room with someone besides its host in it. */
        const val OPEN_MILLIS = 2 * 60 * 60_000L

        /** An open room nobody else is in. */
        const val ALONE_MILLIS = 10 * 60_000L
        const val FORGOTTEN_ISLAND = "forgotten-island"
        const val ACROSS_THE_RIVER = "across-the-river"
        val TEAM_SIZES = 1..5

        /** docs/ONLINE.md: 1v1, 2v2, 3v3 on Forgotten Island; 4v4, 5v5 on Across the River. */
        fun mapFor(teamSize: Int): String = if (teamSize <= 3) FORGOTTEN_ISLAND else ACROSS_THE_RIVER

        /** As in `NewGameRules.setup`: seats in pick order per team, Red first; here every seat is a person. */
        fun setupOf(room: RoomRow, tieBreaker: Team): GameSetup = GameSetup(
            mapId = room.mapId,
            seats = room.seats
                .sortedWith(compareBy({ it.team.ordinal }, { it.index }))
                .map { SeatSetup(it.heroId.orEmpty(), it.team, Controller.Human) },
            length = room.length,
            tieBreaker = tieBreaker,
        )

        /** On the emptier team, so people spread out unless they choose otherwise; null when all are taken. */
        private fun freeSeat(room: RoomRow): RoomSeatRow? {
            val team = Team.entries.minByOrNull { team -> room.seats.count { it.team == team && it.accountId != null } }
            return room.seats.firstOrNull { it.team == team && it.accountId == null }
        }

        /**
         * Someone joining gives a room its whole time. Left alone again it goes back to the short clock from
         * then, or two accounts could hold a room for hours by joining and leaving once.
         */
        private fun RoomRow.withExpiry(now: Long): RoomRow {
            val full = createdAt + OPEN_MILLIS
            val company = seats.any { it.accountId != null && it.accountId != hostAccount }
            return when {
                status != RoomStatus.OPEN -> this
                company -> copy(expiresAt = full)
                expiresAt == full -> copy(expiresAt = minOf(full, now + ALONE_MILLIS))
                else -> this
            }
        }

        private fun RoomRow.withSeat(seat: RoomSeatRow): RoomRow =
            copy(seats = seats.map { if (it.team == seat.team && it.index == seat.index) seat else it })

        /** The next person seated becomes host; with nobody left the room closes. */
        private fun RoomRow.without(accountId: String): RoomRow {
            val emptied = seats.map {
                if (it.accountId == accountId) it.copy(accountId = null, heroId = null, ready = false) else it
            }
            val host = if (hostAccount == accountId) emptied.firstNotNullOfOrNull { it.accountId } else hostAccount
            return if (host == null) {
                copy(seats = emptied, status = RoomStatus.CLOSED)
            } else {
                copy(seats = emptied, hostAccount = host)
            }
        }
    }
}
