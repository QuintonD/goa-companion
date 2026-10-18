package dev.quinton.goa.feature.trivia

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.core.navigation.TriviaRoute
import dev.quinton.goa.feature.trivia.home.TriviaHomeScreen
import dev.quinton.goa.feature.trivia.home.TriviaHomeViewModel
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizScreen
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizViewModel

/** Card-number trivia (ADR-028): the home with setup and progress, and a quiz round. */
fun EntryProviderScope<NavKey>.triviaEntries(navigator: Navigator) {
    entry<TriviaRoute> { key ->
        val viewModel = hiltViewModel<TriviaHomeViewModel, TriviaHomeViewModel.Factory>(
            creationCallback = { it.create(key.heroId) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        TriviaHomeScreen(
            state = state,
            onAction = viewModel::onAction,
            onStart = { viewModel.start()?.let(navigator::navigate) },
            onStartHero = { heroId -> viewModel.startHero(heroId)?.let(navigator::navigate) },
            onReset = viewModel::resetProgress,
            onBack = navigator::back,
        )
    }
    entry<TriviaQuizRoute> { key ->
        val viewModel = hiltViewModel<TriviaQuizViewModel, TriviaQuizViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        TriviaQuizScreen(
            state = state,
            onAnswer = viewModel::answer,
            onNext = viewModel::next,
            onStop = viewModel::stop,
            onPractise = {
                val route = viewModel.practiseRoute()
                when {
                    route == null -> Unit
                    // The same facts and setup again (practising a practice round): restart in place.
                    viewModel.isSameRound(route) -> viewModel.playAgain()
                    else -> {
                        navigator.back()
                        navigator.navigate(route)
                    }
                }
            },
            onPlayAgain = viewModel::playAgain,
            onDone = navigator::back,
            onBack = navigator::back,
        )
    }
}
