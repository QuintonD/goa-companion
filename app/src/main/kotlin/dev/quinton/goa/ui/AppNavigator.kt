package dev.quinton.goa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import dev.quinton.goa.core.navigation.CatalogRoute
import dev.quinton.goa.core.navigation.DraftRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.PlayersRoute
import dev.quinton.goa.core.navigation.TimerRoute
import dev.quinton.goa.core.navigation.TopLevelTab

/** One back stack per tab; back on a non-home tab root returns to the Heroes tab. */
class AppNavigator(
    private val stacks: Map<TopLevelTab, NavBackStack<NavKey>>,
    initialTab: TopLevelTab,
    private val onTabChanged: (TopLevelTab) -> Unit,
) : Navigator {
    var currentTab by mutableStateOf(initialTab)
        private set

    val currentStack: NavBackStack<NavKey> get() = stacks.getValue(currentTab)

    fun selectTab(tab: TopLevelTab) {
        if (tab == currentTab) {
            // Re-selecting a tab pops it to its root.
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        currentTab = tab
        onTabChanged(tab)
    }

    override fun navigate(key: NavKey) {
        currentStack.add(key)
    }

    override fun navigateToTab(tab: TopLevelTab, key: NavKey?) {
        val stack = stacks.getValue(tab)
        if (key != null && stack.lastOrNull() != key) stack.add(key)
        currentTab = tab
        onTabChanged(tab)
    }

    override fun back() {
        val stack = currentStack
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            currentTab != TopLevelTab.CATALOG -> selectTab(TopLevelTab.CATALOG)
        }
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator {
    val catalog = rememberNavBackStack(CatalogRoute)
    val timer = rememberNavBackStack(TimerRoute)
    val draft = rememberNavBackStack(DraftRoute)
    val players = rememberNavBackStack(PlayersRoute)
    var savedTab by rememberSaveable { mutableStateOf(TopLevelTab.CATALOG) }
    return remember(catalog, timer, draft, players) {
        AppNavigator(
            stacks = mapOf(
                TopLevelTab.CATALOG to catalog,
                TopLevelTab.TIMER to timer,
                TopLevelTab.DRAFT to draft,
                TopLevelTab.PLAYERS to players,
            ),
            initialTab = savedTab,
            onTabChanged = { savedTab = it },
        )
    }
}
