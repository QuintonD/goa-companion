package dev.quinton.goa.feature.players.meta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.presentation.players.HeroSort
import dev.quinton.goa.core.presentation.players.Meta
import dev.quinton.goa.core.presentation.players.MetaUiState
import dev.quinton.goa.core.presentation.players.MetaView
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MetaViewModel @Inject constructor(
    ratings: RatingsSnapshotRepository,
    players: PlayerRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    private val choice = MutableStateFlow(MetaView.TABLE to HeroSort.RANK)

    val uiState: StateFlow<MetaUiState> = combine(
        ratings.observeSnapshot(),
        players.observePlayers(includeArchived = true),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
        choice,
    ) { snapshot, people, cat, (view, sort) ->
        Meta.build(snapshot, people.associateBy { it.id }, cat).copy(view = view, sort = sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetaUiState())

    fun setView(view: MetaView) {
        choice.value = view to choice.value.second
    }

    fun setSort(sort: HeroSort) {
        choice.value = choice.value.first to sort
    }
}
