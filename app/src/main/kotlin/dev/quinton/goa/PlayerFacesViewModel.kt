package dev.quinton.goa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.rating.PlayerFaces
import dev.quinton.goa.core.model.player.PlayerFace
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Every player's face (their most-played hero) for the whole app, from the ratings snapshot the Heroes tab already
 * computes at start: derived from the match log, nothing stored (`LocalPlayerFaces`).
 */
@HiltViewModel
class PlayerFacesViewModel @Inject constructor(
    ratings: RatingsSnapshotRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    val faces: StateFlow<Map<String, PlayerFace>> =
        combine(ratings.observeSnapshot(), catalog.catalog) { snapshot, heroes ->
            PlayerFaces.from(snapshot.ratings, heroes)
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyMap())

    private companion object {
        const val STOP_MS = 5_000L
    }
}
