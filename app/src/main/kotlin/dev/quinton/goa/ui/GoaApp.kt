package dev.quinton.goa.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.icon.GoaIcons
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.feature.catalog.catalogEntries
import dev.quinton.goa.feature.draft.draftEntries
import dev.quinton.goa.feature.players.playersEntries
import dev.quinton.goa.feature.settings.settingsEntries
import dev.quinton.goa.feature.timer.timerEntries

private data class TabSpec(
    val tab: TopLevelTab,
    val label: String,
    @param:DrawableRes val selected: Int,
    @param:DrawableRes val unselected: Int,
)

private val Tabs = listOf(
    TabSpec(TopLevelTab.CATALOG, "Heroes", GoaIcons.NavHeroesSelected, GoaIcons.NavHeroes),
    TabSpec(TopLevelTab.TIMER, "Timer", GoaIcons.NavTimerSelected, GoaIcons.NavTimer),
    TabSpec(TopLevelTab.DRAFT, "Draft", GoaIcons.NavDraftSelected, GoaIcons.NavDraft),
    TabSpec(TopLevelTab.PLAYERS, "Players", GoaIcons.NavPlayersSelected, GoaIcons.NavPlayers),
)

/** Subtle push: content drifts 1/10 of the width while cross-fading (Motion tokens). */
private fun push(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    val enter = fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EmphasizedDecelerate)) +
        slideInHorizontally(tween(Motion.LONG_MS, easing = Motion.Emphasized)) { dir * it / 10 }
    val exit = fadeOut(tween(Motion.SHORT_MS, easing = Motion.EmphasizedAccelerate)) +
        slideOutHorizontally(tween(Motion.LONG_MS, easing = Motion.Emphasized)) { -dir * it / 10 }
    return enter togetherWith exit
}

/** App shell: bottom navigation over one Navigation 3 back stack per top-level tab. */
@Composable
fun GoaApp() {
    val navigator = rememberAppNavigator()
    // Back on another tab's root returns to Heroes (NavDisplay only handles back within a stack).
    BackHandler(enabled = navigator.currentTab != TopLevelTab.CATALOG && navigator.currentStack.size == 1) {
        navigator.back()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { GoaBottomBar(navigator.currentTab, navigator::selectTab) },
    ) { padding ->
        NavDisplay(
            backStack = navigator.currentStack,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            onBack = { navigator.back() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { push(forward = true) },
            popTransitionSpec = { push(forward = false) },
            predictivePopTransitionSpec = { _ -> push(forward = false) },
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

@Composable
private fun GoaBottomBar(current: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    Column {
        EngravedRule()
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Tabs.forEach { spec ->
                val selected = current == spec.tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(spec.tab) },
                    icon = {
                        Icon(
                            painterResource(if (selected) spec.selected else spec.unselected),
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            spec.label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
