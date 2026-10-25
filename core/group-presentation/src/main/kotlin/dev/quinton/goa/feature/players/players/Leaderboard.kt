package dev.quinton.goa.feature.players.players

import dev.quinton.goa.core.data.rating.RatingsSnapshot
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
    /** The last sync with the group's sync server failed for a reason a person must fix (ADR-036). */
    val serverNeedsAttention: Boolean = false,
    /** In a group with a sync server switched on: the sync icon syncs through it (ADR-036). */
    val serverSyncOn: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && ranked.isEmpty() && unrated.isEmpty() && archived.isEmpty()
}

const val TREND_GAMES = 5

/** The Players tab's leaderboard, shared by the phone and the web app (ADR-043). */
object Leaderboard {
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
}
