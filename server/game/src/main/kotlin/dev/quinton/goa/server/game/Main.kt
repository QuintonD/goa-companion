package dev.quinton.goa.server.game

import dev.quinton.goa.server.game.http.gameServerModule
import dev.quinton.goa.server.game.ratings.LogMatchMapper
import dev.quinton.goa.server.game.ratings.MatchLogLeaderboard
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.room.PlayerNames
import dev.quinton.goa.server.game.room.RoomCore
import dev.quinton.goa.server.game.store.SqliteGameStore
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main() {
    val config = GameServerConfig.fromEnv()
    val store = SqliteGameStore.open(config.dataDir)
    val engine = loadEngine(config.assetsDir)
    val rooms = RoomCore(
        engine = engine,
        store = store,
        rulesBuild = config.rulesBuild,
        mapper = LogMatchMapper { engine.data.maps[it]?.lanes?.size ?: 1 },
        names = PlayerNames { store.names(listOf(it))[it] },
    )
    val leaderboard: LeaderboardSource = MatchLogLeaderboard(
        matches = store::matches,
        names = store::names,
        heroName = { engine.data.catalog.hero(it)?.name },
        clock = System::currentTimeMillis,
    )
    runBlocking {
        store.markStartedRooms()
        rooms.restore()
    }
    rooms.launchClocks(CoroutineScope(SupervisorJob() + Dispatchers.Default))
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
