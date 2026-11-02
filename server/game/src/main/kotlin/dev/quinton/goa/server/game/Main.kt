package dev.quinton.goa.server.game

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.TurnRef
import dev.quinton.goa.server.game.http.gameServerModule
import dev.quinton.goa.server.game.room.GameRooms
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.room.SeatLink
import dev.quinton.goa.server.game.room.SeatSink
import dev.quinton.goa.server.game.store.SqliteGameStore
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main() {
    val config = GameServerConfig.fromEnv()
    val store = SqliteGameStore.open(config.dataDir)
    val engine = loadEngine(config.assetsDir)
    // The other streams join here (docs/ONLINE.md "Streams"): the room core takes the ratings' MatchMapper.
    val rooms: GameRooms = RoomsNotJoined
    val leaderboard: LeaderboardSource = LeaderboardSource { error("The leaderboard stream has not joined yet") }
    runBlocking {
        store.markStartedRooms()
        rooms.restore()
    }
    LoggerFactory.getLogger("goa-game").info(
        "goa-game on {}:{}, data in {}, at most {} rooms, rules build {}, {} running",
        config.host,
        config.port,
        config.dataDir,
        config.maxRooms,
        config.rulesBuild,
        rooms.runningCount,
    )
    val server = embeddedServer(CIO, port = config.port, host = config.host) {
        gameServerModule(config, store, engine, rooms, leaderboard)
    }
    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(SHUTDOWN_GRACE_MILLIS, SHUTDOWN_TIMEOUT_MILLIS)
            store.close()
        },
    )
    server.start(wait = true)
}

private const val SHUTDOWN_GRACE_MILLIS = 1_000L
private const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L

/** Refuses every game until the room core joins: an honest failure, and lobby and accounts still run. */
private object RoomsNotJoined : GameRooms {
    override val runningCount: Int = 0

    override suspend fun restore() = Unit

    override suspend fun start(roomCode: String, setup: GameSetup, players: List<SeatPlayer>, ranked: Boolean): String =
        error("The room core has not joined yet")

    override suspend fun connect(gameId: String, accountId: String, haveEvents: Int, sink: SeatSink): SeatLink? = null

    override suspend fun answer(gameId: String, accountId: String, ticket: Long, choice: Choice) = Unit

    override suspend fun review(gameId: String, accountId: String, rewind: TurnRef?) = Unit

    override suspend fun leave(gameId: String, accountId: String) = Unit

    override suspend fun gamesOf(accountId: String): List<String> = emptyList()
}
