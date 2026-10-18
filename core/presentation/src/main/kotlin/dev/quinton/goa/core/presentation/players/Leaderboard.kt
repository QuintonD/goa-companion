package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating

data class LeaderboardRow(
    val player: Player,
    val rating: Rating,
    /** 1-based rank among players with rated games; null when unrated. */
    val rank: Int?,
    /** Rating change over the last [trendGames] rated games (at most [TREND_GAMES]); null without games. */
    val trend: Double?,
    val trendGames: Int = 0,
    /** This phone's owner ("Which player are you?", group sync). */
    val isMe: Boolean = false,
)

data class PlayersUiState(
    val loading: Boolean = true,
    val ranked: List<LeaderboardRow> = emptyList(),
    val unrated: List<LeaderboardRow> = emptyList(),
    val archived: List<LeaderboardRow> = emptyList(),
    val showArchived: Boolean = false,
    val ratedMatches: Int = 0,
    /** This phone's player (resolved through merges), if chosen. */
    val meId: String? = null,
    /** The phone is in a sync group: the sync action opens the sync sheet directly. */
    val inGroup: Boolean = false,
    /** Pairs of players that might be one person ("Same people?", ADR-031). */
    val samePeople: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && ranked.isEmpty() && unrated.isEmpty() && archived.isEmpty()

    /** "Find duplicates" needs two active players. */
    val canFindDuplicates: Boolean get() = ranked.size + unrated.size >= 2

    /** The "Same people?" card's text, or null when there is nothing to review. */
    val samePeopleText: String?
        get() = when (samePeople) {
            0 -> null
            1 -> "2 players might be the same person"
            else -> "$samePeople pairs of players look like the same person"
        }
}

const val TREND_GAMES = 5

/** The Players tab's leaderboard (Elo, games, W–L, "last N" trend, provisional). */
object Leaderboard {
    /**
     * [players] with archived ones (merged-away ones already hidden); [meId] resolved through the
     * merges. Ranked players by rating (then name), unrated and archived ones by name.
     */
    fun build(
        players: List<Player>,
        snapshot: RatingsSnapshot,
        showArchived: Boolean,
        meId: String? = null,
    ): PlayersUiState {
        val ratings = snapshot.ratings
        fun row(player: Player, rank: Int?): LeaderboardRow {
            val recent = ratings.playerHistory[player.id].orEmpty().takeLast(TREND_GAMES)
            return LeaderboardRow(
                player = player,
                rating = ratings.player(player.id),
                rank = rank,
                trend = recent.takeIf { it.isNotEmpty() }?.sumOf { it.delta },
                trendGames = recent.size,
                isMe = player.id == meId,
            )
        }
        val (archived, active) = players.partition { it.archived }
        val (rated, unrated) = active.partition { ratings.player(it.id).games > 0 }
        val ranked = rated
            .sortedWith(compareByDescending<Player> { ratings.player(it.id).value }.thenBy { it.name.lowercase() })
            .mapIndexed { i, p -> row(p, i + 1) }
        return PlayersUiState(
            loading = false,
            ranked = ranked,
            unrated = unrated.sortedBy { it.name.lowercase() }.map { row(it, null) },
            archived = archived.sortedBy { it.name.lowercase() }.map { row(it, null) },
            showArchived = showArchived,
            ratedMatches = ratings.replayOrder.size,
            meId = meId?.takeIf { id -> players.any { it.id == id } },
        )
    }

    /** "Rank 2, Ann, you, rating 1234, provisional, 3 wins, 1 loss, +24 over the last 4 games". */
    fun spoken(row: LeaderboardRow, archived: Boolean = false): String = buildString {
        val rating = row.rating
        row.rank?.let { append("Rank $it, ") }
        append(row.player.name)
        if (row.isMe) append(", you")
        if (rating.games > 0) {
            append(", rating ${formatRating(rating.value)}")
            if (rating.provisional) append(", provisional")
            append(", ${spokenRecord(rating.wins, rating.losses)}")
            row.trend?.let { append(", ${formatDelta(it)} over the last ${plural(row.trendGames, "game")}") }
        } else {
            append(", no games yet")
        }
        if (archived) append(", archived")
    }
}
