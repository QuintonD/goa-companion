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
) {
    companion object {
        const val DEFAULT_PORT = 8788
        const val DEFAULT_MAX_ROOMS = 20
        val DEFAULT_ORIGINS = setOf("https://quintond.github.io")

        fun fromEnv(env: Map<String, String> = System.getenv()): GameServerConfig = GameServerConfig(
            dataDir = Paths.get(env["GOA_GAME_DATA"] ?: "/var/lib/goa-game"),
            assetsDir = Paths.get(env["GOA_GAME_ASSETS"] ?: "assets"),
            host = env["GOA_GAME_HOST"] ?: "127.0.0.1",
            port = env["GOA_GAME_PORT"]?.toInt() ?: DEFAULT_PORT,
            maxRooms = env["GOA_GAME_MAX_ROOMS"]?.toInt() ?: DEFAULT_MAX_ROOMS,
            origins = env["GOA_GAME_ORIGINS"]
                ?.split(',')
                ?.map { it.trim().removeSuffix("/") }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: DEFAULT_ORIGINS,
            rulesBuild = env["GOA_GAME_RULES_BUILD"] ?: "dev",
        )
    }
}
