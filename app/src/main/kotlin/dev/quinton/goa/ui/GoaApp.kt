package dev.quinton.goa.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.feature.catalog.catalogEntries
import dev.quinton.goa.feature.draft.draftEntries
import dev.quinton.goa.feature.players.playersEntries
import dev.quinton.goa.feature.settings.settingsEntries
import dev.quinton.goa.feature.timer.timerEntries

private data class TabSpec(
    val tab: TopLevelTab,
    val label: String,
    val selected: ImageVector,
    val unselected: ImageVector,
)

private val Tabs = listOf(
    TabSpec(TopLevelTab.CATALOG, "Heroes", Icons.Filled.Shield, Icons.Outlined.Shield),
    TabSpec(TopLevelTab.TIMER, "Timer", Icons.Filled.Timer, Icons.Outlined.Timer),
    TabSpec(TopLevelTab.DRAFT, "Draft", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    TabSpec(TopLevelTab.PLAYERS, "Players", Icons.Filled.Groups, Icons.Outlined.Groups),
)

/** App shell: bottom navigation over one Navigation 3 back stack per top-level tab. */
@Composable
fun GoaApp() {
    val navigator = rememberAppNavigator()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tabs.forEach { spec ->
                    val selected = navigator.currentTab == spec.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigator.selectTab(spec.tab) },
                        icon = { Icon(if (selected) spec.selected else spec.unselected, contentDescription = null) },
                        label = { Text(spec.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = navigator.currentStack,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            onBack = { navigator.back() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                catalogEntries(navigator)
                timerEntries(navigator)
                draftEntries(navigator)
                playersEntries(navigator)
                settingsEntries(navigator)
            },
        )
    }
}
