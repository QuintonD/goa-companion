package dev.quinton.goa.feature.rules

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.LevelLadderRoute
import dev.quinton.goa.core.navigation.NavSheet
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.RulesRoute
import dev.quinton.goa.core.navigation.RulesTopicRoute
import dev.quinton.goa.feature.rules.ui.LevelLadderSheet
import dev.quinton.goa.feature.rules.ui.RulesScreen
import dev.quinton.goa.feature.rules.ui.RulesTopicScreen

/** Rules reference: topic list, topic page, and the level & defeat ladder as a bottom sheet. */
fun EntryProviderScope<NavKey>.rulesEntries(navigator: Navigator) {
    entry<RulesRoute> {
        val viewModel = hiltViewModel<RulesViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        RulesScreen(
            state = state,
            onQueryChange = viewModel::setQuery,
            onOpenLadder = { navigator.navigate(LevelLadderRoute(showRulesLink = false)) },
            onOpenTopic = { navigator.navigate(RulesTopicRoute(it)) },
            onBack = navigator::back,
        )
    }
    entry<RulesTopicRoute> { key ->
        val viewModel = hiltViewModel<RulesTopicViewModel, RulesTopicViewModel.Factory>(
            creationCallback = { it.create(key.topicId) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        RulesTopicScreen(state = state, onBack = navigator::back)
    }
    entry<LevelLadderRoute>(metadata = NavSheet.metadata) { key ->
        val viewModel = hiltViewModel<LevelLadderViewModel, LevelLadderViewModel.Factory>(
            creationCallback = { it.create(key.heroIds) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LevelLadderSheet(
            state = state,
            onSelectLevel = viewModel::selectLevel,
            onOpenRules = if (key.showRulesLink) {
                {
                    navigator.back()
                    navigator.navigate(RulesRoute)
                }
            } else {
                null
            },
        )
    }
}
