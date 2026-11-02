package dev.quinton.goa.feature.board.play.online.leaderboard

import dev.quinton.goa.core.online.LeaderRow

enum class LeaderboardTab(val label: String) {
    PLAYERS("Players"),
    HEROES("Heroes"),
}

data class LeaderboardRowUi(
    val rank: Int,
    val id: String,
    val name: String,
    val rating: Int,
    val games: Int,
    val wins: Int,
    /** The signed-in player's own row. */
    val isYou: Boolean,
)

/** What the leaderboard screens draw. */
data class LeaderboardUi(
    val tabs: List<LeaderboardTab> = LeaderboardTab.entries,
    val tab: LeaderboardTab = LeaderboardTab.PLAYERS,
    val rows: List<LeaderboardRowUi> = emptyList(),
    /** "Updated 12 min ago", or the words for an old copy. Null before there is a board. */
    val status: String? = null,
    /** [status] is about this device's last copy: the screen marks it. */
    val stale: Boolean = false,
    /** Why there are no rows, in words; null when there are rows or a first read is under way. */
    val empty: String? = null,
    /** A read is under way; what is on screen stays until it ends. */
    val loading: Boolean = false,
)

/** Pure: the same load, tab and account always give the same state. */
object LeaderboardPresenter {
    /** docs/ONLINE.md "Ratings": the server leaves out anyone with fewer. */
    const val GAMES_TO_APPEAR: Int = 5

    /** Words for the case where [load] is null and nothing is being read. */
    const val NO_BOARD: String = "No leaderboard yet: the server can't be reached"

    private const val MINUTE_MS = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    /** [accountId] is the signed-in player, if any: their row is marked on the Players tab only. */
    fun present(
        load: LeaderboardLoad?,
        tab: LeaderboardTab = LeaderboardTab.PLAYERS,
        accountId: String? = null,
        loading: Boolean = false,
    ): LeaderboardUi {
        if (load == null) {
            return LeaderboardUi(tab = tab, empty = NO_BOARD.takeUnless { loading }, loading = loading)
        }
        val rows = when (tab) {
            LeaderboardTab.PLAYERS -> rows(load.snapshot.players, accountId)
            LeaderboardTab.HEROES -> rows(load.snapshot.heroes, null)
        }
        return LeaderboardUi(
            tab = tab,
            rows = rows,
            status = status(load),
            stale = load.origin == LeaderboardOrigin.LAST_COPY,
            empty = if (rows.isEmpty()) noRows(tab) else null,
            loading = loading,
        )
    }

    private fun rows(rows: List<LeaderRow>, you: String?): List<LeaderboardRowUi> = rows.mapIndexed { i, row ->
        LeaderboardRowUi(
            rank = i + 1,
            id = row.id,
            name = row.name,
            rating = row.rating,
            games = row.games,
            wins = row.wins,
            isYou = row.id == you,
        )
    }

    /** "Updated 12 min ago"; "Last copy, from 3 days ago: the server can't be reached". */
    fun status(load: LeaderboardLoad): String = when (load.origin) {
        LeaderboardOrigin.PAGES, LeaderboardOrigin.SERVER -> "Updated ${ago(load.ageMillis)}"
        LeaderboardOrigin.LAST_COPY -> "Last copy, from ${ago(load.ageMillis)}: the server can't be reached"
    }

    private fun noRows(tab: LeaderboardTab): String = when (tab) {
        LeaderboardTab.PLAYERS -> "No players yet: a player appears after $GAMES_TO_APPEAR rated games"
        LeaderboardTab.HEROES -> "No heroes yet: a hero appears after $GAMES_TO_APPEAR rated games"
    }

    /** "just now", "12 min ago", "3 h ago", "2 days ago", as group sync says it (`SyncWords`). */
    private fun ago(ageMillis: Long): String {
        val minutes = ageMillis.coerceAtLeast(0) / MINUTE_MS
        return when {
            minutes < 1 -> "just now"
            minutes < MINUTES_PER_HOUR -> "$minutes min ago"
            minutes < MINUTES_PER_DAY -> "${minutes / MINUTES_PER_HOUR} h ago"
            else -> (minutes / MINUTES_PER_DAY).let { days -> if (days == 1L) "1 day ago" else "$days days ago" }
        }
    }
}
