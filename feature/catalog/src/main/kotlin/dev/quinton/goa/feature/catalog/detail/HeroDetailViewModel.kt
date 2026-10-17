package dev.quinton.goa.feature.catalog.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.feature.catalog.model.DeckLayout
import dev.quinton.goa.feature.catalog.model.HeroLogSummary
import dev.quinton.goa.feature.catalog.model.HeroPilots
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Personal stats for the hero: derived Elo/usage plus a summary of the match log. */
data class HeroPersonalStats(
    val rating: Rating?,
    val usage: HeroUsage?,
    val log: HeroLogSummary,
) {
    val hasGames: Boolean get() = log.games > 0 || (rating?.games ?: 0) > 0 || (usage?.matches ?: 0) > 0
}

sealed interface HeroDetailUiState {
    data object Loading : HeroDetailUiState

    data class NotFound(val heroId: String) : HeroDetailUiState

    data class Ready(
        val hero: Hero,
        val productName: String,
        val deck: DeckLayout,
        val stats: HeroPersonalStats,
    ) : HeroDetailUiState
}

@HiltViewModel(assistedFactory = HeroDetailViewModel.Factory::class)
class HeroDetailViewModel @AssistedInject constructor(
    @Assisted route: HeroDetailRoute,
    catalogRepository: CatalogRepository,
    ratingsRepository: RatingsRepository,
    matchRepository: MatchRepository,
    playerRepository: PlayerRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: HeroDetailRoute): HeroDetailViewModel
    }

    private val heroId = route.heroId

    private val hero = catalogRepository.catalog.map { catalog ->
        catalog.hero(heroId)?.let { hero ->
            Triple(hero, catalog.product(hero.product)?.name ?: hero.product, DeckLayout.of(hero))
        }
    }

    private val stats = combine(
        ratingsRepository.observeHeroRatings().map { it[heroId] }.onStart { emit(null) },
        ratingsRepository.observeHeroUsage().map { it[heroId] }.onStart { emit(null) },
        matchRepository.observeMatches().onStart { emit(emptyList()) },
        playerRepository.observePlayers(includeArchived = true).onStart { emit(emptyList()) },
    ) { rating, usage, matches, players ->
        HeroPersonalStats(rating, usage, HeroPilots.summarize(heroId, matches, players))
    }.catch { emit(HeroPersonalStats(null, null, HeroPilots.summarize(heroId, emptyList(), emptyList()))) }

    val uiState: StateFlow<HeroDetailUiState> = combine(hero, stats) { found, stats ->
        if (found == null) {
            HeroDetailUiState.NotFound(heroId)
        } else {
            HeroDetailUiState.Ready(found.first, found.second, found.third, stats)
        }
    }
        .catch { emit(HeroDetailUiState.NotFound(heroId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HeroDetailUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
