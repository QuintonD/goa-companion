package dev.quinton.goa.feature.catalog

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.CardViewerRoute
import dev.quinton.goa.core.navigation.CatalogRoute
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.SettingsRoute
import dev.quinton.goa.feature.catalog.detail.HeroDetailScreen
import dev.quinton.goa.feature.catalog.detail.HeroDetailViewModel
import dev.quinton.goa.feature.catalog.list.CatalogScreen
import dev.quinton.goa.feature.catalog.list.CatalogViewModel
import dev.quinton.goa.feature.catalog.viewer.CardViewerScreen
import dev.quinton.goa.feature.catalog.viewer.CardViewerViewModel

fun EntryProviderScope<NavKey>.catalogEntries(navigator: Navigator) {
    entry<CatalogRoute> {
        val viewModel = hiltViewModel<CatalogViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val query by viewModel.query.collectAsStateWithLifecycle()
        CatalogScreen(
            state = state,
            query = query,
            onAction = viewModel::onAction,
            onHeroClick = { navigator.navigate(HeroDetailRoute(it)) },
            onSettingsClick = { navigator.navigate(SettingsRoute) },
        )
    }
    entry<HeroDetailRoute> { key ->
        val viewModel = hiltViewModel<HeroDetailViewModel, HeroDetailViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        HeroDetailScreen(
            state = state,
            onBack = navigator::back,
            onCardClick = { heroId, cardId -> navigator.navigate(CardViewerRoute(heroId, cardId)) },
        )
    }
    entry<CardViewerRoute> { key ->
        val viewModel = hiltViewModel<CardViewerViewModel, CardViewerViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        CardViewerScreen(state = state, onBack = navigator::back)
    }
}
