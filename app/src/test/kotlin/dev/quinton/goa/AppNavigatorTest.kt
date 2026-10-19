package dev.quinton.goa

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.CatalogRoute
import dev.quinton.goa.core.navigation.DraftRoute
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.core.navigation.MatchEditRoute
import dev.quinton.goa.core.navigation.MatchLogRoute
import dev.quinton.goa.core.navigation.MatchSetupRoute
import dev.quinton.goa.core.navigation.PlayersRoute
import dev.quinton.goa.core.navigation.TimerPresetsRoute
import dev.quinton.goa.core.navigation.TimerRoute
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.ui.AppNavigator
import org.junit.Test

class AppNavigatorTest {
    private val navigator = AppNavigator(
        stacks = mapOf(
            TopLevelTab.CATALOG to NavBackStack<NavKey>(CatalogRoute),
            TopLevelTab.TIMER to NavBackStack<NavKey>(TimerRoute),
            TopLevelTab.DRAFT to NavBackStack<NavKey>(DraftRoute),
            TopLevelTab.PLAYERS to NavBackStack<NavKey>(PlayersRoute),
        ),
        initialTab = TopLevelTab.CATALOG,
    )

    @Test fun switchingTabsKeepsEachTabsBackStack() {
        navigator.selectTab(TopLevelTab.PLAYERS)
        navigator.navigate(MatchEditRoute(result = true))
        navigator.selectTab(TopLevelTab.CATALOG)
        navigator.navigate(HeroDetailRoute("arien"))
        navigator.selectTab(TopLevelTab.PLAYERS)

        assertThat(
            navigator.currentStack.toList(),
        ).containsExactly(PlayersRoute, MatchEditRoute(result = true)).inOrder()
        assertThat(
            navigator.stack(TopLevelTab.CATALOG).toList(),
        ).containsExactly(CatalogRoute, HeroDetailRoute("arien"))
    }

    @Test fun reselectingTheCurrentTabPopsToItsRoot() {
        navigator.navigate(HeroDetailRoute("arien"))
        navigator.selectTab(TopLevelTab.CATALOG)
        assertThat(navigator.currentStack.toList()).containsExactly(CatalogRoute)
    }

    @Test fun openTimerShowsTheTimerItselfFromAnywhere() {
        navigator.selectTab(TopLevelTab.TIMER)
        navigator.navigate(TimerPresetsRoute)
        navigator.selectTab(TopLevelTab.DRAFT)
        navigator.openTimer()
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.TIMER)
        assertThat(navigator.currentStack.toList()).containsExactly(TimerRoute)
    }

    /** Game night: Draft → Set up match → Start timer landed on Timer presets [88]. */
    @Test fun startingATimerFromSetupShowsTheTimerNotTheScreensLeftUnderneath() {
        navigator.selectTab(TopLevelTab.TIMER)
        navigator.navigate(TimerPresetsRoute)
        navigator.selectTab(TopLevelTab.DRAFT)
        navigator.navigateToTab(TopLevelTab.TIMER, MatchSetupRoute("m2"))
        // Match setup's onTimerStarted.
        navigator.openTimer()

        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.TIMER)
        assertThat(navigator.currentStack.toList()).containsExactly(TimerRoute)
    }

    /** Game night: a new Match setup opened over last game's 5-player setup [101]. */
    @Test fun aNewMatchSetupReplacesAnOlderOneAndWhatWasAboveIt() {
        navigator.navigateToTab(TopLevelTab.TIMER, MatchSetupRoute("m1"))
        navigator.navigate(TimerPresetsRoute)
        navigator.selectTab(TopLevelTab.DRAFT)
        navigator.navigateToTab(TopLevelTab.TIMER, MatchSetupRoute("m2"))

        assertThat(navigator.currentStack.toList()).containsExactly(TimerRoute, MatchSetupRoute("m2")).inOrder()
        navigator.back()
        assertThat(navigator.currentStack.toList()).containsExactly(TimerRoute)
    }

    @Test fun navigatingToATabRootDoesNotDuplicateIt() {
        navigator.navigateToTab(TopLevelTab.PLAYERS, MatchLogRoute)
        navigator.navigate(MatchEditRoute("m1"))
        navigator.navigateToTab(TopLevelTab.PLAYERS, MatchLogRoute)
        assertThat(navigator.currentStack.toList()).containsExactly(PlayersRoute, MatchLogRoute).inOrder()
    }

    /** A double tap on "Try on the board" must not open the same board twice. */
    @Test fun aDoubleTapDoesNotPushTheSameScreenTwice() {
        val board = BoardRoute(red = listOf("arien"), blue = listOf("wasp"))
        navigator.navigate(board)
        navigator.navigate(board)
        assertThat(navigator.currentStack.toList()).containsExactly(CatalogRoute, board).inOrder()
        navigator.navigate(BoardRoute(red = listOf("brogan")))
        assertThat(navigator.currentStack).hasSize(3)
    }

    @Test fun backPopsThenReturnsToHeroes() {
        navigator.navigateToTab(TopLevelTab.PLAYERS, MatchLogRoute)
        navigator.back()
        assertThat(navigator.currentStack.toList()).containsExactly(PlayersRoute)
        navigator.back()
        assertThat(navigator.currentTab).isEqualTo(TopLevelTab.CATALOG)
    }

    @Test fun messagesQueueUntilShown() {
        var opened = false
        navigator.showMessage("Saved", "View") { opened = true }
        navigator.showMessage("Second")
        val first = navigator.messages.first()
        assertThat(first.text).isEqualTo("Saved")
        first.onAction?.invoke()
        navigator.messageShown(first.id)
        assertThat(opened).isTrue()
        assertThat(navigator.messages.map { it.text }).containsExactly("Second")
    }
}
