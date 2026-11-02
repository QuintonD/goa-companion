package dev.quinton.goa.core.data.online

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.online.GameHost
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardFetcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The build's game server (BuildConfig `GAME_SERVER_URL`, Gradle property `goa.gameServer`, null when
 * empty). [allowLocalHttp]: debug builds may speak plain HTTP to a local test server.
 */
data class GameServerDefaults(val url: String?, val allowLocalHttp: Boolean = false)

/** Where to talk to: a base address, or none because every host is closed ([note] may say why). */
data class GameHostChoice(val base: String?, val note: String? = null, val tooOld: Boolean = false)

/** Accepts `https://host[:port][/one-segment]`; plain HTTP only to a local test server, in debug. */
internal fun isGameOrigin(url: String, allowLocalHttp: Boolean): Boolean =
    HTTPS_ORIGIN.matches(url) || (allowLocalHttp && LOCAL_ORIGIN.matches(url))

/**
 * Picks the host from `network.json` (null when it can't be read): its first open host in its order, else
 * the built-in address, unless the file closes that one too. A status other than `closed` is open: `status`
 * is a person's choice and a dead host just fails. A newer file's `minProtocol` says this build is too old.
 */
internal fun chooseHost(info: NetworkInfo?, defaults: GameServerDefaults): GameHostChoice {
    val listed = info?.games.orEmpty()
    val closed = listed.filter { it.status == STATUS_CLOSED }
    val open = listed.filterNot { it in closed }.map(GameHost::origin)
    val closedOrigins = closed.map { it.origin.trimEnd('/') }
    val base = (open + listOfNotNull(defaults.url))
        .map { it.trimEnd('/') }
        .firstOrNull { it !in closedOrigins && isGameOrigin(it, defaults.allowLocalHttp) }
    return GameHostChoice(
        base = base,
        note = closed.firstNotNullOfOrNull(GameHost::note).takeIf { base == null },
        tooOld = info != null && info.minProtocol > OnlineProtocol.VERSION,
    )
}

/**
 * Reads `network.json` when a call needs a host, at most every [REFRESH_MS]. When it can't be read, the last
 * good copy holds, else the built-in address does: Pages being down never stops a running game.
 */
@Singleton
class GameHosts @Inject constructor(
    private val fetcher: LeaderboardFetcher,
    private val defaults: GameServerDefaults,
    private val clock: EditClock,
) {
    private val lock = Mutex()
    private var info: NetworkInfo? = null
    private var readAt: Long? = null

    suspend fun current(): GameHostChoice = lock.withLock {
        val now = clock.now()
        val age = readAt?.let { now - it }
        if (age == null || age >= REFRESH_MS || age < 0) {
            read()?.let { info = it }
            readAt = now
        }
        chooseHost(info, defaults)
    }

    private suspend fun read(): NetworkInfo? {
        val text = fetcher.get(OnlineRoutes.NETWORK_FILE) ?: return null
        // kotlinx.serialization's errors are all IllegalArgumentExceptions.
        val parsed = try {
            OnlineProtocol.json.decodeFromString(NetworkInfo.serializer(), text)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return parsed.takeIf { it.schema in 1..NetworkInfo.SCHEMA }
    }

    private companion object {
        const val REFRESH_MS = 5 * 60_000L
    }
}

private const val STATUS_CLOSED = "closed"
private val HTTPS_ORIGIN = Regex("""https://[A-Za-z0-9.-]+(:\d+)?(/[A-Za-z0-9._-]+)?""")
private val LOCAL_ORIGIN = Regex("""http://(10\.0\.2\.2|localhost|127\.0\.0\.1)(:\d+)?(/[A-Za-z0-9._-]+)?""")
