package dev.quinton.goa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

/** A message queued by [Navigator.showMessage] for the shell's snackbar. */
data class ShellMessage(val id: Long, val text: String, val actionLabel: String?, val onAction: (() -> Unit)?)

/**
 * One back stack per tab (each keeps its screens and their state while another tab is shown);
 * back on a non-home tab root returns to the Heroes tab.
 */
class AppNavigator(
    private val stacks: Map<TopLevelTab, NavBackStack<NavKey>>,
    initialTab: TopLevelTab,
    private val onTabChanged: (TopLevelTab) -> Unit = {},
) : Navigator {
    var currentTab by mutableStateOf(initialTab)
        private set

    val currentStack: NavBackStack<NavKey> get() = stacks.getValue(currentTab)

    fun stack(tab: TopLevelTab): NavBackStack<NavKey> = stacks.getValue(tab)

    /** Messages waiting to be shown, oldest first. */
    val messages = mutableStateListOf<ShellMessage>()
    private var nextMessageId = 0L

    fun selectTab(tab: TopLevelTab) {
        if (tab == currentTab) popToRoot(tab) // Re-selecting a tab pops it to its root.
        switchTo(tab)
    }

    /** Shows the timer itself (notification tap, cold start with a session, the running-timer bar). */
    override fun openTimer() {
        popToRoot(TopLevelTab.TIMER)
        switchTo(TopLevelTab.TIMER)
    }

    /** Pushes [key]; a second tap that would push the same screen again (a double tap) is ignored. */
    override fun navigate(key: NavKey) {
        if (currentStack.lastOrNull() == key) return
        currentStack.add(key)
    }

    override fun navigateToTab(tab: TopLevelTab, key: NavKey?) {
        val stack = stacks.getValue(tab)
        if (key != null) {
            // An older screen of the same kind (e.g. last game's Match setup) goes, with all above it.
            val stale = stack.indexOfFirst { it::class == key::class }
            if (stale >= 0) while (stack.size > maxOf(stale, 1)) stack.removeAt(stack.lastIndex)
            if (stack.lastOrNull() != key) stack.add(key)
        }
        switchTo(tab)
    }

    override fun back() {
        val stack = currentStack
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            currentTab != TopLevelTab.CATALOG -> switchTo(TopLevelTab.CATALOG)
        }
    }

    override fun showMessage(message: String, actionLabel: String?, onAction: (() -> Unit)?) {
        messages += ShellMessage(nextMessageId++, message, actionLabel, onAction)
    }

    fun messageShown(id: Long) {
        messages.removeAll { it.id == id }
    }

    private fun popToRoot(tab: TopLevelTab) {
        val stack = stacks.getValue(tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    private fun switchTo(tab: TopLevelTab) {
        currentTab = tab
        onTabChanged(tab)
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
