package dev.quinton.goa.server.game.ratings

import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.online.LeaderRow
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.server.game.room.LeaderboardSource
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

/** What a deleted account's rows show: its match rows stay in the log (docs/ONLINE.md). */
const val FORMER_PLAYER: String = "Former player"

/** Rated games before a player or a hero appears on the board. */
const val GAMES_TO_APPEAR: Int = 5

/** Rows per board. */
const val BOARD_ROWS: Int = 200

/** `eloConfig` of the snapshot: goes up when [EloConfig.DEFAULT] changes, so every rating moved. */
const val ELO_CONFIG_VERSION: Int = 1

/**
 * The public board, derived from the match log on demand (docs/ONLINE.md "Ratings"). The replay is
 * remembered per log hash, in memory only: nothing here is ever written down, and a new source over the
 * same log gives the same board. Names are read on every call, because a rename or a deleted account
 * changes the board without changing the log.
 */
class MatchLogLeaderboard(
    private val matches: suspend () -> List<Match>,
    /** Account id to display name. An account that is gone shows as [FORMER_PLAYER]. */
    private val names: suspend () -> Map<String, String>,
    /** The catalog's name of a hero; one it no longer has shows its id. */
    private val heroName: (heroId: String) -> String?,
    private val clock: () -> Long,
    private val replay: (List<Match>) -> RatingsResult = { EloCalculator.replay(it, EloConfig.DEFAULT) },
) : LeaderboardSource {
    private class Derived(val logSha256: String, val matches: Int, val players: List<Ranked>, val heroes: List<Ranked>)

    private class Ranked(val id: String, val rating: Int, val games: Int, val wins: Int)

    /** One board: a race at worst repeats a replay, and the hash check throws out an older result. */
    @Volatile
    private var derived: Derived? = null

    override suspend fun snapshot(): LeaderboardSnapshot {
        val log = ratedLog(matches())
        val sha = logSha256(log)
        val board = derived?.takeIf { it.logSha256 == sha } ?: derive(sha, log).also { derived = it }
        val known = names()
        return LeaderboardSnapshot(
            schema = LeaderboardSnapshot.SCHEMA,
            generatedAt = clock(),
            logSha256 = board.logSha256,
            matches = board.matches,
            eloConfig = ELO_CONFIG_VERSION,
            players = board.players.map { it.row(known[it.id] ?: FORMER_PLAYER) },
            heroes = board.heroes.map { it.row(heroName(it.id) ?: it.id) },
        )
    }

    private fun derive(sha: String, log: List<Match>): Derived {
        val ratings = replay(log)
        return Derived(sha, log.size, ranked(ratings.players), ranked(ratings.heroes))
    }

    private fun ranked(ratings: Map<String, Rating>): List<Ranked> = ratings
        .filterValues { it.games >= GAMES_TO_APPEAR }
        .map { (id, rating) -> Ranked(id, rating.value.roundToInt(), rating.games, rating.wins) }
        .sortedWith(compareByDescending<Ranked> { it.rating }.thenBy { it.id })
        .take(BOARD_ROWS)

    private fun Ranked.row(name: String) = LeaderRow(id = id, name = name, rating = rating, games = games, wins = wins)

    companion object {
        /** Every field, nulls and defaults too, in declaration order: one match has one text. */
        private val canonical = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        /**
         * The games that count, in replay order. A row the replay would skip changes neither the
         * board nor the hash; voided and abandoned games never reach the log to begin with.
         */
        fun ratedLog(matches: List<Match>): List<Match> =
            matches.filter { EloCalculator.skipReason(it) == null }.sortedWith(EloCalculator.REPLAY_ORDER)

        /** SHA-256 over one canonical line per match of an ordered log, in lower-case hex. */
        fun logSha256(log: List<Match>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (match in log) {
                digest.update(canonical.encodeToString(Match.serializer(), match).encodeToByteArray())
                digest.update('\n'.code.toByte())
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
