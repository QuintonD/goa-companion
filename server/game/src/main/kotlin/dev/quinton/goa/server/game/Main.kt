package dev.quinton.goa.server.game

import dev.quinton.goa.server.game.http.gameServerModule
import dev.quinton.goa.server.game.ratings.LogMatchMapper
import dev.quinton.goa.server.game.ratings.MatchLogLeaderboard
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.room.PlayerNames
import dev.quinton.goa.server.game.room.RoomCore
import dev.quinton.goa.server.game.store.NewerDatabase
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
    val store = openOrWait(config)
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

/**
 * With a newer database there is nothing useful to do, and exiting would only have `restart: unless-stopped`
 * start it again and again. So it says why, now and then, and stays: Docker shows it as unhealthy, and a deploy's
 * health check fails with this in the logs it prints.
 */
private fun openOrWait(config: GameServerConfig): SqliteGameStore {
    try {
        return SqliteGameStore.open(config.dataDir)
    } catch (failure: NewerDatabase) {
        while (true) {
            LoggerFactory.getLogger("goa-game").error(failure.message)
            Thread.sleep(REFUSING_LOG_MILLIS)
        }
    }
}

private const val REFUSING_LOG_MILLIS = 10 * 60_000L
private const val SHUTDOWN_GRACE_MILLIS = 1_000L
private const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L
