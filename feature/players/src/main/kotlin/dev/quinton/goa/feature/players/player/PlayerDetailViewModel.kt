package dev.quinton.goa.feature.players.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.feature.players.common.heroName
import dev.quinton.goa.feature.players.match.MatchLogViewModel.Companion.toRow
import dev.quinton.goa.feature.players.match.MatchRowUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HeroRecordRow(val heroId: String, val name: String, val portrait: String?, val record: WinLoss)

data class PlayerDetailUiState(
    val loading: Boolean = true,
    val player: Player? = null,
    val rating: Rating = Rating.UNRATED,
    val rank: Int? = null,
    val rankedPlayers: Int = 0,
    val initialRating: Double = Rating.START,
    /** Rating after each rated match, oldest first. */
    val history: List<Double> = emptyList(),
    val peak: Double? = null,
    val doublePilotGames: Int = 0,
    /** Heroes with enough games (best first); falls back to most played when none qualify. */
    val heroes: List<HeroRecordRow> = emptyList(),
    val heroesAreBest: Boolean = false,
    val recentMatches: List<MatchRowUi> = emptyList(),
    val totalMatches: Int = 0,
)

const val RECENT_MATCHES = 5
private const val MAX_HEROES = 5

@HiltViewModel(assistedFactory = PlayerDetailViewModel.Factory::class)
class PlayerDetailViewModel @AssistedInject constructor(
    @Assisted private val playerId: String,
    private val players: PlayerRepository,
    matches: MatchRepository,
    ratings: RatingsSnapshotRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playerId: String): PlayerDetailViewModel
    }

    val uiState: StateFlow<PlayerDetailUiState> = combine(
        players.observePlayers(includeArchived = true),
        matches.observeMatches(),
        ratings.observeSnapshot(),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
    ) { people, log, snapshot, cat -> build(playerId, people, log, snapshot, cat) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerDetailUiState())

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { players.archive(playerId, archived) }
    }

    companion object {
        fun build(
            playerId: String,
            players: List<Player>,
            matches: List<Match>,
            snapshot: RatingsSnapshot,
            catalog: Catalog,
        ): PlayerDetailUiState {
            val byId = players.associateBy { it.id }
            val player = byId[playerId] ?: return PlayerDetailUiState(loading = false)
            val ratings = snapshot.ratings
            val ranked = players.filter { !it.archived && ratings.player(it.id).games > 0 }
                .sortedByDescending { ratings.player(it.id).value }
            val history = ratings.playerHistory[playerId].orEmpty().map { it.value }
            val best = snapshot.meta.bestHeroesByPlayer[playerId].orEmpty()
            fun row(heroId: String, record: WinLoss) =
                HeroRecordRow(heroId, catalog.heroName(heroId), catalog.hero(heroId)?.portrait, record)
            val heroes = if (best.isNotEmpty()) {
                best.take(MAX_HEROES).map { row(it.heroId, it.record) }
            } else {
                ratings.playerHeroRecords
                    .filterKeys { it.playerId == playerId }
                    .entries
                    .sortedWith(compareByDescending<Map.Entry<*, WinLoss>> { it.value.games }.thenByDescending { it.value.wins })
                    .take(MAX_HEROES)
                    .map { row(it.key.heroId, it.value) }
            }
            val mine = matches
                .filter { m -> m.participants.any { it.playerId == playerId } }
                .sortedWith(compareByDescending<Match> { it.playedAt }.thenByDescending { it.createdAt })
            return PlayerDetailUiState(
                loading = false,
                player = player,
                rating = ratings.player(playerId),
                rank = ranked.indexOfFirst { it.id == playerId }.takeIf { it >= 0 }?.plus(1),
                rankedPlayers = ranked.size,
                initialRating = ratings.initialRating,
                history = history,
                peak = history.maxOrNull(),
                doublePilotGames = ratings.doublePilotGames[playerId] ?: 0,
                heroes = heroes,
                heroesAreBest = best.isNotEmpty(),
                recentMatches = mine.take(RECENT_MATCHES).map { it.toRow(byId, catalog, ratings.matchChanges[it.id]) },
                totalMatches = mine.size,
            )
        }
    }
}
