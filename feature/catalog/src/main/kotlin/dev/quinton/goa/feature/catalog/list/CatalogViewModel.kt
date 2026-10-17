package dev.quinton.goa.feature.catalog.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.feature.catalog.model.AttackStyle
import dev.quinton.goa.feature.catalog.model.CatalogBrowser
import dev.quinton.goa.feature.catalog.model.CatalogFilter
import dev.quinton.goa.feature.catalog.model.CatalogSearchIndex
import dev.quinton.goa.feature.catalog.model.CatalogSort
import dev.quinton.goa.feature.catalog.model.HeroListItem
import dev.quinton.goa.feature.catalog.model.visibleProducts
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class CatalogLayout { GRID, LIST }

/** User-controlled view settings (survive while the Heroes tab is on the back stack). */
data class CatalogControls(
    val layout: CatalogLayout = CatalogLayout.GRID,
    val filter: CatalogFilter = CatalogFilter(),
    val sort: CatalogSort = CatalogSort.NAME,
)

/** Options the filter sheet offers, derived from the loaded catalog and owned products. */
data class FilterOptions(
    val complexities: List<Int>,
    val roles: List<Role>,
    val products: List<Product>,
    val attackStyles: List<AttackStyle>,
    /** True when the group set owned products, so "Include heroes you don't own" is meaningful. */
    val hasOwnedProducts: Boolean,
)

sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    /** Catalog failed to load or contains no heroes yet. */
    data class Unavailable(val error: Boolean) : CatalogUiState

    data class Ready(
        val controls: CatalogControls,
        val heroes: List<HeroListItem>,
        /** Heroes in scope before search and filters (owned products respected). */
        val totalInScope: Int,
        val options: FilterOptions,
    ) : CatalogUiState {
        val isFiltered: Boolean get() = heroes.size != totalInScope
    }
}

sealed interface CatalogAction {
    data class Search(val query: String) : CatalogAction

    data class SetLayout(val layout: CatalogLayout) : CatalogAction

    data class SetSort(val sort: CatalogSort) : CatalogAction

    data class SetFilter(val filter: CatalogFilter) : CatalogAction

    data object ClearFilters : CatalogAction

    /** Clears both the search query and the filters. */
    data object ClearAll : CatalogAction
}

private data class Indexed(val catalog: Catalog, val index: CatalogSearchIndex)

private data class Stats(val ratings: Map<String, Rating>, val usage: Map<String, HeroUsage>)

@HiltViewModel
class CatalogViewModel internal constructor(
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    ratingsRepository: RatingsRepository,
    computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        settingsRepository: SettingsRepository,
        ratingsRepository: RatingsRepository,
    ) : this(catalogRepository, settingsRepository, ratingsRepository, Dispatchers.Default)

    private val queryState = MutableStateFlow("")
    private val controls = MutableStateFlow(CatalogControls())

    /** The raw search text, separate from [uiState] so the text field never lags behind typing. */
    val query: StateFlow<String> = queryState.asStateFlow()

    private val indexed = catalogRepository.catalog
        .distinctUntilChanged()
        .map { Indexed(it, CatalogSearchIndex(it.heroes)) }
        .flowOn(computeDispatcher)

    private val stats = combine(
        ratingsRepository.observeHeroRatings().onStart { emit(emptyMap()) },
        ratingsRepository.observeHeroUsage().onStart { emit(emptyMap()) },
    ) { ratings, usage -> Stats(ratings, usage) }
        .catch { emit(Stats(emptyMap(), emptyMap())) }

    private val owned = settingsRepository.settings.map { it.ownedProducts }.distinctUntilChanged()

    val uiState: StateFlow<CatalogUiState> =
        combine(indexed, owned, stats, queryState, controls) { indexed, owned, stats, query, controls ->
            buildState(indexed, owned, stats, query, controls)
        }
            .flowOn(computeDispatcher)
            .catch { emit(CatalogUiState.Unavailable(error = true)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogUiState.Loading)

    fun onAction(action: CatalogAction) {
        when (action) {
            is CatalogAction.Search -> queryState.value = action.query

            is CatalogAction.SetLayout -> controls.update { it.copy(layout = action.layout) }

            is CatalogAction.SetSort -> controls.update { it.copy(sort = action.sort) }

            is CatalogAction.SetFilter -> controls.update { it.copy(filter = action.filter) }

            CatalogAction.ClearFilters -> controls.update { it.copy(filter = it.filter.cleared()) }

            CatalogAction.ClearAll -> {
                queryState.value = ""
                controls.update { it.copy(filter = it.filter.cleared()) }
            }
        }
    }

    private fun buildState(
        indexed: Indexed,
        owned: Set<String>,
        stats: Stats,
        query: String,
        controls: CatalogControls,
    ): CatalogUiState {
        val catalog = indexed.catalog
        if (catalog.heroes.isEmpty()) return CatalogUiState.Unavailable(error = false)
        val products = visibleProducts(catalog, owned, controls.filter.includeUnowned)
        // Drop product selections that are no longer visible (e.g. owned products changed).
        val visibleIds = products.mapTo(mutableSetOf()) { it.id }
        val keptProducts = controls.filter.products.intersect(visibleIds)
        val filter = controls.filter.copy(products = keptProducts)
        val inScope = catalog.heroes.count { owned.isEmpty() || filter.includeUnowned || it.product in owned }
        val heroes = CatalogBrowser.browse(
            catalog = catalog,
            index = indexed.index,
            query = query,
            filter = filter,
            sort = controls.sort,
            ownedProducts = owned,
            ratings = stats.ratings,
            usage = stats.usage,
        )
        return CatalogUiState.Ready(
            controls = controls.copy(filter = filter),
            heroes = heroes,
            totalInScope = inScope,
            options = FilterOptions(
                complexities = catalog.heroes.mapNotNull { it.complexity }.distinct().sorted(),
                roles = Role.entries.filter { role -> catalog.heroes.any { role in it.roles } },
                products = products,
                attackStyles = AttackStyle.entries,
                hasOwnedProducts = owned.isNotEmpty(),
            ),
        )
    }

    private fun CatalogFilter.cleared() = CatalogFilter(includeUnowned = includeUnowned)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
