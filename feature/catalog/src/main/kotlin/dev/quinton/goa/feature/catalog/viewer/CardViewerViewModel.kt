package dev.quinton.goa.feature.catalog.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.navigation.CardViewerRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface CardViewerUiState {
    data object Loading : CardViewerUiState

    data object NotFound : CardViewerUiState

    data class Ready(
        val hero: Hero,
        /** Every card of the hero in the same order as the detail screen. */
        val cards: List<Card>,
        val initialIndex: Int,
    ) : CardViewerUiState {
        fun isHandicap(card: Card): Boolean = hero.handicap?.id == card.id

        /**
         * Where [index] sits, counted as the deck page counts (DESIGN.md "Deck count"): the hero's deck cards
         * ("3 of 18"), then the Handicap card on its own ("Handicap"), then any extras ("Spellbook 2 of 12").
         */
        fun position(index: Int): String {
            val deck = hero.cards.size
            val card = cards.getOrNull(index) ?: return ""
            return when {
                isHandicap(card) -> "Handicap"

                index < deck -> "${index + 1} of $deck"

                else -> {
                    val extras = cards.size - deck - (if (hero.handicap != null) 1 else 0)
                    val at = index - deck - (if (hero.handicap != null) 1 else 0) + 1
                    val spellbook = DeckLayout(emptyList(), null, hero.extras).extrasAreSpellbook
                    "${if (spellbook) "Spellbook" else "Extra"} $at of $extras"
                }
            }
        }
    }
}

@HiltViewModel(assistedFactory = CardViewerViewModel.Factory::class)
class CardViewerViewModel @AssistedInject constructor(
    @Assisted route: CardViewerRoute,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: CardViewerRoute): CardViewerViewModel
    }

    val uiState: StateFlow<CardViewerUiState> = catalogRepository.catalog
        .map { catalog ->
            val hero = catalog.hero(route.heroId) ?: return@map CardViewerUiState.NotFound
            val cards = DeckLayout.of(hero).orderedCards
            val index = cards.indexOfFirst { it.id == route.cardId }
            if (cards.isEmpty()) {
                CardViewerUiState.NotFound
            } else {
                CardViewerUiState.Ready(hero, cards, initialIndex = index.coerceAtLeast(0))
            }
        }
        .catch { emit(CardViewerUiState.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CardViewerUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
