package dev.quinton.goa.server.game

import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.server.game.http.gameServerModule
import dev.quinton.goa.server.game.ratings.LogMatchMapper
import dev.quinton.goa.server.game.ratings.MatchLogLeaderboard
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.room.PlayerNames
import dev.quinton.goa.server.game.room.RoomCore
import dev.quinton.goa.server.game.room.RoomRules
import dev.quinton.goa.server.game.store.NewerDatabase
import dev.quinton.goa.server.game.store.SqliteGameStore
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * The whole server, put together once: `main` runs this and the end-to-end tests run this, so they can't
 * drift apart. [rules], [clock] and [beatMillis] are the only things a test may pass that `main` doesn't.
 */
class GameServer(
    private val config: GameServerConfig,
    engine: GameEngine = loadEngine(config.assetsDir),
    rules: RoomRules = RoomRules(),
    clock: () -> Long = System::currentTimeMillis,
    private val beatMillis: Long = DEFAULT_BEAT_MILLIS,
) {
    val store: SqliteGameStore = openOrWait(config)
    val rooms: RoomCore = RoomCore(
        engine = engine,
        store = store,
        rulesBuild = config.rulesBuild,
        mapper = LogMatchMapper { engine.data.maps[it]?.lanes?.size ?: 1 },
        names = PlayerNames { store.names(listOf(it))[it] },
        rules = rules,
    )
    private val leaderboard: LeaderboardSource = MatchLogLeaderboard(
        matches = store::matches,
        names = store::names,
        heroName = { engine.data.catalog.hero(it)?.name },
        clock = clock,
    )
    private val clocks = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = embeddedServer(CIO, port = config.port, host = config.host) {
        gameServerModule(config, store, engine, rooms, leaderboard, clock)
    }

    /** Running games come back from the store before anyone can connect. */
    fun start(wait: Boolean) {
        runBlocking {
            store.markStartedRooms()
            rooms.restore()
        }
        rooms.launchClocks(clocks, beatMillis)
        LoggerFactory.getLogger("goa-game").info(
            "goa-game on {}:{}, data in {}, at most {} rooms, rules build {}, {} running",
            config.host,
            config.port,
            config.dataDir,
            config.maxRooms,
            config.rulesBuild,
            rooms.runningCount,
        )
        server.start(wait)
    }

    /** The port it listens on: [GameServerConfig.port], or the one it was given for port 0. */
    suspend fun port(): Int = server.engine.resolvedConnectors().first().port

    fun stop() {
        clocks.cancel()
        server.stop(SHUTDOWN_GRACE_MILLIS, SHUTDOWN_TIMEOUT_MILLIS)
        store.close()
    }

    private companion object {
        const val DEFAULT_BEAT_MILLIS = 1_000L
        const val SHUTDOWN_GRACE_MILLIS = 1_000L
        const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L
    }
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
