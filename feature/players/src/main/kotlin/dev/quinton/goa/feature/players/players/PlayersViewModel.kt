package dev.quinton.goa.feature.players.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class LeaderboardRow(
    val player: Player,
    val rating: Rating,
    /** 1-based rank among players with rated games; null when unrated. */
    val rank: Int?,
    /** Rating change over the last [trendGames] rated games (at most [TREND_GAMES]); null without games. */
    val trend: Double?,
    val trendGames: Int = 0,
)

data class PlayersUiState(
    val loading: Boolean = true,
    val ranked: List<LeaderboardRow> = emptyList(),
    val unrated: List<LeaderboardRow> = emptyList(),
    val archived: List<LeaderboardRow> = emptyList(),
    val showArchived: Boolean = false,
    val ratedMatches: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && ranked.isEmpty() && unrated.isEmpty() && archived.isEmpty()
}

const val TREND_GAMES = 5

@HiltViewModel
class PlayersViewModel @Inject constructor(
    players: PlayerRepository,
    ratings: RatingsSnapshotRepository,
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<PlayersUiState> = combine(
        players.observePlayers(includeArchived = true),
        ratings.observeSnapshot(),
        showArchived,
    ) { list, snapshot, archivedVisible -> leaderboard(list, snapshot, archivedVisible) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayersUiState())

    fun toggleArchived() = showArchived.update { !it }

    companion object {
        fun leaderboard(players: List<Player>, snapshot: RatingsSnapshot, showArchived: Boolean): PlayersUiState {
            val ratings = snapshot.ratings
            fun row(player: Player, rank: Int?): LeaderboardRow {
                val recent = ratings.playerHistory[player.id].orEmpty().takeLast(TREND_GAMES)
                return LeaderboardRow(
                    player = player,
                    rating = ratings.player(player.id),
                    rank = rank,
                    trend = recent.takeIf { it.isNotEmpty() }?.sumOf { it.delta },
                    trendGames = recent.size,
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
            )
        }
    }
}
