package dev.quinton.goa.feature.draft

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.DraftBoardRoute
import dev.quinton.goa.core.navigation.DraftMethodEditRoute
import dev.quinton.goa.core.navigation.DraftMethodsRoute
import dev.quinton.goa.core.navigation.DraftRoute
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.core.navigation.MatchLogRoute
import dev.quinton.goa.core.navigation.MatchSetupRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.PlayerEditRoute
import dev.quinton.goa.core.navigation.SettingsRoute
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.feature.draft.board.DraftBoardDestination
import dev.quinton.goa.feature.draft.methods.DraftMethodEditDestination
import dev.quinton.goa.feature.draft.methods.DraftMethodEditViewModel
import dev.quinton.goa.feature.draft.methods.DraftMethodsDestination
import dev.quinton.goa.feature.draft.setup.DraftSetupDestination

fun EntryProviderScope<NavKey>.draftEntries(navigator: Navigator) {
    entry<DraftRoute> {
        DraftSetupDestination(
            viewModel = hiltViewModel(),
            onOpenBoard = { navigator.navigate(DraftBoardRoute) },
            onManageMethods = { navigator.navigate(DraftMethodsRoute) },
            onAddPlayer = { navigator.navigate(PlayerEditRoute()) },
            onOpenSettings = { navigator.navigate(SettingsRoute) },
        )
    }
    entry<DraftBoardRoute> {
        DraftBoardDestination(
            viewModel = hiltViewModel(),
            onBack = navigator::back,
            onOpenHero = { navigator.navigate(HeroDetailRoute(it)) },
            onTryOnBoard = { red, blue -> navigator.navigate(BoardRoute(red = red, blue = blue)) },
            onFinished = { matchId, openMatchSetup ->
                navigator.back()
                if (openMatchSetup) {
                    navigator.navigateToTab(TopLevelTab.TIMER, MatchSetupRoute(matchId))
                } else {
                    navigator.showMessage("Saved as a pending match. Log the result under Players › Matches.", "View") {
                        navigator.navigateToTab(TopLevelTab.PLAYERS, MatchLogRoute)
                    }
                }
            },
        )
    }
    entry<DraftMethodsRoute> {
        DraftMethodsDestination(
            viewModel = hiltViewModel(),
            onBack = navigator::back,
            onEdit = { navigator.navigate(DraftMethodEditRoute(it)) },
        )
    }
    entry<DraftMethodEditRoute> { key ->
        DraftMethodEditDestination(
            viewModel = hiltViewModel<DraftMethodEditViewModel, DraftMethodEditViewModel.Factory>(
                creationCallback = { factory -> factory.create(key.methodId) },
            ),
            onBack = navigator::back,
        )
    }
}
