package dev.quinton.goa.relay

import java.nio.file.Path
import java.nio.file.Paths

/** Runtime settings; every field has an environment variable (see server/relay/README.md). */
data class RelayConfig(
    val dataDir: Path,
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val maxBodyBytes: Long = MAX_BODY_BYTES,
    val historyKeep: Int = HISTORY_KEEP,
    val maxGroups: Int = DEFAULT_MAX_GROUPS,
    val perClientPerMinute: Int = DEFAULT_PER_CLIENT_PER_MINUTE,
    val globalPerMinute: Int = DEFAULT_GLOBAL_PER_MINUTE,
    /** Browser origins allowed to call the relay (CORS, ADR-043): exact `scheme://host[:port]`. */
    val corsOrigins: Set<String> = DEFAULT_CORS_ORIGINS,
) {
    companion object {
        const val DEFAULT_DATA_DIR = "/var/lib/goa-relay"
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8787
        const val MAX_BODY_BYTES = 8L * 1024 * 1024
        const val HISTORY_KEEP = 20
        const val DEFAULT_MAX_GROUPS = 200
        const val DEFAULT_PER_CLIENT_PER_MINUTE = 60
        const val DEFAULT_GLOBAL_PER_MINUTE = 600

        /** The web app on GitHub Pages (https://quintond.github.io/goa-companion/). */
        val DEFAULT_CORS_ORIGINS = setOf("https://quintond.github.io")

        /**
         * `GOA_RELAY_CORS_ORIGINS`: comma-separated origins; unset = [DEFAULT_CORS_ORIGINS], set but
         * empty = no browser may call. A trailing slash is ignored; anything with a path is refused.
         */
        fun corsOrigins(raw: String?): Set<String> {
            if (raw == null) return DEFAULT_CORS_ORIGINS
            return raw.split(',').map { it.trim().removeSuffix("/") }.filter { it.isNotEmpty() }.onEach {
                require(ORIGIN.matches(it)) { "GOA_RELAY_CORS_ORIGINS: $it is not an origin like https://example.org" }
            }.map { it.lowercase() }.toSet()
        }

        private val ORIGIN = Regex("https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?")

        fun fromEnv(env: Map<String, String> = System.getenv()): RelayConfig {
            fun int(name: String, default: Int): Int {
                val raw = env[name]?.trim().orEmpty()
                if (raw.isEmpty()) return default
                return requireNotNull(raw.toIntOrNull()?.takeIf { it > 0 }) { "$name must be a positive integer" }
            }
            return RelayConfig(
                dataDir = Paths.get(env["GOA_RELAY_DATA"]?.takeIf { it.isNotBlank() } ?: DEFAULT_DATA_DIR),
                host = env["GOA_RELAY_HOST"]?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST,
                port = int("GOA_RELAY_PORT", DEFAULT_PORT),
                maxGroups = int("GOA_RELAY_MAX_GROUPS", DEFAULT_MAX_GROUPS),
                perClientPerMinute = int("GOA_RELAY_RATE_PER_CLIENT", DEFAULT_PER_CLIENT_PER_MINUTE),
                globalPerMinute = int("GOA_RELAY_RATE_GLOBAL", DEFAULT_GLOBAL_PER_MINUTE),
                corsOrigins = corsOrigins(env["GOA_RELAY_CORS_ORIGINS"]),
            )
        }
    }
}
