package dev.quinton.goa.feature.board.play.online.leaderboard

import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.feature.board.play.online.HOST_STATUS_CLOSED

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
 * good copy. A file that does not parse, is of a newer schema than this app, or breaks the limits the
 * publisher holds it to counts as a failed read, so it is never kept: a live host's answer hasn't been
 * through the publisher. `network.json` is only read once Pages has failed, so a healthy Pages sends oldboi
 * nothing.
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
        // The site starts with an empty file that no server made: the server may already have a board.
        val snapshot = snapshotOf(text)?.takeIf { it.generatedAt > 0 } ?: return null
        cache.write(text)
        return loaded(snapshot, origin)
    }

    private fun loaded(snapshot: LeaderboardSnapshot, origin: LeaderboardOrigin) =
        LeaderboardLoad(snapshot, origin, (now() - snapshot.generatedAt).coerceAtLeast(0))

    /** The hosts of `network.json` in its order, the built-in address last, each once; none it has closed. */
    private suspend fun hosts(): List<String> {
        val listed = fetcher.get(OnlineRoutes.NETWORK_FILE)?.let(::networkOf)?.games.orEmpty()
        val closed = listed.filter { it.status == HOST_STATUS_CLOSED }.map { it.origin.trimEnd('/') }
        return (listed.map { it.origin } + builtInHost).map { it.trimEnd('/') }.distinct().filter { it !in closed }
    }

    private fun snapshotOf(text: String): LeaderboardSnapshot? {
        if (text.length > MAX_TEXT_LENGTH) return null
        return decoded { OnlineProtocol.json.decodeFromString(LeaderboardSnapshot.serializer(), text) }
            ?.takeIf { it.schema in 1..LeaderboardSnapshot.SCHEMA && fits(it) }
    }

    private fun fits(board: LeaderboardSnapshot): Boolean {
        val rows = board.players + board.heroes
        return board.logSha256.length <= MAX_FIELD_LENGTH && rows.size <= MAX_ROWS &&
            rows.all { it.id.length in 1..MAX_FIELD_LENGTH && it.name.length in 1..MAX_FIELD_LENGTH }
    }

    private fun networkOf(text: String): NetworkInfo? =
        decoded { OnlineProtocol.json.decodeFromString(NetworkInfo.serializer(), text) }
            ?.takeIf { it.schema in 1..NetworkInfo.SCHEMA }

    /** kotlinx.serialization's errors are all [IllegalArgumentException]s. */
    private inline fun <T> decoded(decode: () -> T): T? = try {
        decode()
    } catch (_: IllegalArgumentException) {
        null
    }

    internal companion object {
        /** As `validate_leaderboard.py`: what the published file can never exceed. */
        const val MAX_TEXT_LENGTH = 512 * 1024
        const val MAX_FIELD_LENGTH = 64

        /** Each board has at most 200 rows; twice that is a server that means something else. */
        const val MAX_ROWS = 400
    }
}
