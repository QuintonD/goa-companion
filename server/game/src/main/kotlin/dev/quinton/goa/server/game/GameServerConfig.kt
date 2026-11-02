package dev.quinton.goa.server.game

import java.nio.file.Path
import java.nio.file.Paths

/** Runtime settings; every field has an environment variable (see deploy/game/README.md). */
data class GameServerConfig(
    val dataDir: Path,
    val assetsDir: Path,
    val host: String = "127.0.0.1",
    val port: Int = DEFAULT_PORT,
    val maxRooms: Int = DEFAULT_MAX_ROOMS,
    /** Browser origins allowed to call (CORS) and to open the socket: exact `scheme://host[:port]`. */
    val origins: Set<String> = DEFAULT_ORIGINS,
    /** Hash of the rules sources and catalog, set by the deploy; stamped on each game (docs/ONLINE.md). */
    val rulesBuild: String = "dev",
    /** While this file exists no new room opens and no room starts; running games continue. */
    val drainFile: Path = dataDir.resolve(DEFAULT_DRAIN_FILE),
    val perClientPerMinute: Int = DEFAULT_PER_CLIENT_PER_MINUTE,
    val globalPerMinute: Int = DEFAULT_GLOBAL_PER_MINUTE,
    val accountsPerClientPerHour: Int = DEFAULT_ACCOUNTS_PER_CLIENT_PER_HOUR,
    val accountsGlobalPerHour: Int = DEFAULT_ACCOUNTS_GLOBAL_PER_HOUR,
    /** Frames one person may send per minute over all their sockets. */
    val socketFramesPerMinute: Int = DEFAULT_SOCKET_FRAMES_PER_MINUTE,
    val helloTimeoutMillis: Long = DEFAULT_HELLO_TIMEOUT_MILLIS,
) {
    companion object {
        const val DEFAULT_PORT = 8788
        const val DEFAULT_MAX_ROOMS = 20
        const val DEFAULT_DRAIN_FILE = "drain"
        const val DEFAULT_PER_CLIENT_PER_MINUTE = 120
        const val DEFAULT_GLOBAL_PER_MINUTE = 1200
        const val DEFAULT_ACCOUNTS_PER_CLIENT_PER_HOUR = 10
        const val DEFAULT_ACCOUNTS_GLOBAL_PER_HOUR = 200
        const val DEFAULT_SOCKET_FRAMES_PER_MINUTE = 300
        const val DEFAULT_HELLO_TIMEOUT_MILLIS = 5_000L
        val DEFAULT_ORIGINS = setOf("https://quintond.github.io")

        fun fromEnv(env: Map<String, String> = System.getenv()): GameServerConfig {
            fun int(name: String, default: Int) = env[name]?.toInt() ?: default
            val dataDir = Paths.get(env["GOA_GAME_DATA"] ?: "/var/lib/goa-game")
            return GameServerConfig(
                dataDir = dataDir,
                assetsDir = Paths.get(env["GOA_GAME_ASSETS"] ?: "assets"),
                host = env["GOA_GAME_HOST"] ?: "127.0.0.1",
                port = int("GOA_GAME_PORT", DEFAULT_PORT),
                maxRooms = int("GOA_GAME_MAX_ROOMS", DEFAULT_MAX_ROOMS),
                origins = env["GOA_GAME_ORIGINS"]
                    ?.split(',')
                    ?.map { it.trim().removeSuffix("/") }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    ?: DEFAULT_ORIGINS,
                rulesBuild = env["GOA_GAME_RULES_BUILD"] ?: "dev",
                drainFile = env["GOA_GAME_DRAIN_FILE"]?.let { Paths.get(it) } ?: dataDir.resolve(DEFAULT_DRAIN_FILE),
                perClientPerMinute = int("GOA_GAME_RATE_PER_CLIENT", DEFAULT_PER_CLIENT_PER_MINUTE),
                globalPerMinute = int("GOA_GAME_RATE_GLOBAL", DEFAULT_GLOBAL_PER_MINUTE),
                accountsPerClientPerHour = int("GOA_GAME_ACCOUNTS_PER_CLIENT", DEFAULT_ACCOUNTS_PER_CLIENT_PER_HOUR),
                accountsGlobalPerHour = int("GOA_GAME_ACCOUNTS_GLOBAL", DEFAULT_ACCOUNTS_GLOBAL_PER_HOUR),
                socketFramesPerMinute = int("GOA_GAME_SOCKET_FRAMES", DEFAULT_SOCKET_FRAMES_PER_MINUTE),
            )
        }
    }
}
