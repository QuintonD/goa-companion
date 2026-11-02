package dev.quinton.goa.feature.board.play.online.leaderboard

import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes

/** The platform's HTTP GET. Null on any failure, a bad status or a timeout; it does not throw. */
fun interface LeaderboardFetcher {
    suspend fun get(url: String): String?
}

/** Keeps the text of the last good file, across launches. */
interface LeaderboardCache {
    suspend fun read(): String?

    suspend fun write(text: String)
}

/** Where a board came from. */
enum class LeaderboardOrigin {
    /** The published file on GitHub Pages. */
    PAGES,

    /** A live game host: Pages could not be read. */
    SERVER,

    /** This device's last good copy: nothing could be reached. */
    LAST_COPY,
}

/** A board and how old it is, from when the server derived it to when it was read. */
data class LeaderboardLoad(val snapshot: LeaderboardSnapshot, val origin: LeaderboardOrigin, val ageMillis: Long)

/**
 * Reads the board (docs/ONLINE.md "Where things run"): the Pages file, each game host, then the last
 * good copy. A file that does not parse, or that is of a newer schema than this app, counts as a failed
 * read. `network.json` is only read once Pages has failed, so a healthy Pages sends oldboi nothing.
 */
class LeaderboardRepository(
    private val fetcher: LeaderboardFetcher,
    private val cache: LeaderboardCache,
    /** The address built into the app (`goa.gameServer`), for when `network.json` cannot help. */
    private val builtInHost: String,
    private val now: () -> Long,
) {
    /** Null when there is no board anywhere yet. */
    suspend fun load(): LeaderboardLoad? =
        fetched(OnlineRoutes.LEADERBOARD_FILE, LeaderboardOrigin.PAGES)
            ?: hosts().firstNotNullOfOrNull { fetched(it + OnlineRoutes.LEADERBOARD, LeaderboardOrigin.SERVER) }
            ?: cache.read()?.let(::snapshotOf)?.let { loaded(it, LeaderboardOrigin.LAST_COPY) }

    private suspend fun fetched(url: String, origin: LeaderboardOrigin): LeaderboardLoad? {
        val text = fetcher.get(url) ?: return null
        val snapshot = snapshotOf(text) ?: return null
        cache.write(text)
        return loaded(snapshot, origin)
    }

    private fun loaded(snapshot: LeaderboardSnapshot, origin: LeaderboardOrigin) =
        LeaderboardLoad(snapshot, origin, (now() - snapshot.generatedAt).coerceAtLeast(0))

    /** The hosts of `network.json` in its order, the built-in address last, each once. */
    private suspend fun hosts(): List<String> {
        val listed = fetcher.get(OnlineRoutes.NETWORK_FILE)?.let(::networkOf)?.games.orEmpty().map { it.origin }
        return (listed + builtInHost).map { it.trimEnd('/') }.distinct()
    }

    private fun snapshotOf(text: String): LeaderboardSnapshot? =
        decoded { OnlineProtocol.json.decodeFromString(LeaderboardSnapshot.serializer(), text) }
            ?.takeIf { it.schema in 1..LeaderboardSnapshot.SCHEMA }

    private fun networkOf(text: String): NetworkInfo? =
        decoded { OnlineProtocol.json.decodeFromString(NetworkInfo.serializer(), text) }
            ?.takeIf { it.schema in 1..NetworkInfo.SCHEMA }

    /** kotlinx.serialization's errors are all [IllegalArgumentException]s. */
    private inline fun <T> decoded(decode: () -> T): T? = try {
        decode()
    } catch (_: IllegalArgumentException) {
        null
    }
}
