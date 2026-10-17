package dev.quinton.goa.feature.players.meta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.meta.HeroMeta
import dev.quinton.goa.core.domain.meta.MetaConfig
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.feature.players.common.heroName
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class HeroMetaRow(
    val heroId: String,
    val name: String,
    val portrait: String?,
    val rating: Rating?,
    val meta: HeroMeta,
    val bestPlayer: Player?,
    val bestRecord: WinLoss?,
)

data class SynergyRow(val heroA: String, val heroB: String, val record: WinLoss, val wilsonLower: Double)

data class MetaUiState(
    val loading: Boolean = true,
    val totalMatches: Int = 0,
    val draftsWithBans: Int = 0,
    val ranked: List<HeroMetaRow> = emptyList(),
    val unranked: List<HeroMetaRow> = emptyList(),
    val synergies: List<SynergyRow> = emptyList(),
    val largerTeam: WinLoss = WinLoss.NONE,
    val minSample: Int = MetaConfig().minSample,
) {
    val isEmpty: Boolean get() = !loading && ranked.isEmpty() && unranked.isEmpty()
}

@HiltViewModel
class MetaViewModel @Inject constructor(
    ratings: RatingsSnapshotRepository,
    players: PlayerRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    val uiState: StateFlow<MetaUiState> = combine(
        ratings.observeSnapshot(),
        players.observePlayers(includeArchived = true),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
    ) { snapshot, people, cat -> build(snapshot, people.associateBy { it.id }, cat) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetaUiState())

    companion object {
        fun build(snapshot: RatingsSnapshot, players: Map<String, Player>, catalog: Catalog): MetaUiState {
            val meta = snapshot.meta
            val rows = meta.heroes.map { hero ->
                HeroMetaRow(
                    heroId = hero.heroId,
                    name = catalog.heroName(hero.heroId),
                    portrait = catalog.hero(hero.heroId)?.portrait,
                    rating = snapshot.ratings.heroes[hero.heroId],
                    meta = hero,
                    bestPlayer = hero.bestPlayer?.let { players[it.playerId] },
                    bestRecord = hero.bestPlayer?.record,
                )
            }
            return MetaUiState(
                loading = false,
                totalMatches = meta.totalMatches,
                draftsWithBans = meta.draftsWithBans,
                ranked = rows.filter { it.meta.ranked },
                unranked = rows.filterNot { it.meta.ranked },
                synergies = meta.synergies.take(MAX_SYNERGIES).map {
                    SynergyRow(catalog.heroName(it.heroA), catalog.heroName(it.heroB), it.record, it.wilsonLower)
                },
                largerTeam = meta.largerTeam,
            )
        }

        private const val MAX_SYNERGIES = 5
    }
}
