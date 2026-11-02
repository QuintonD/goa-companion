package dev.quinton.goa.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.quinton.goa.SessionState
import dev.quinton.goa.ShellViewModel
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.icon.GoaIcons
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.DraftBoardRoute
import dev.quinton.goa.core.navigation.GameRoute
import dev.quinton.goa.core.navigation.NavSheet
import dev.quinton.goa.core.navigation.OnlineGameRoute
import dev.quinton.goa.core.navigation.OnlineHomeRoute
import dev.quinton.goa.core.navigation.PuzzleRoute
import dev.quinton.goa.core.navigation.SyncFileImportRoute
import dev.quinton.goa.core.navigation.TimerRoute
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.feature.board.boardEntries
import dev.quinton.goa.feature.catalog.catalogEntries
import dev.quinton.goa.feature.draft.draftEntries
import dev.quinton.goa.feature.players.playersEntries
import dev.quinton.goa.feature.rules.rulesEntries
import dev.quinton.goa.feature.settings.settingsEntries
import dev.quinton.goa.feature.sync.syncEntries
import dev.quinton.goa.feature.timer.timerEntries
import dev.quinton.goa.feature.trivia.triviaEntries
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

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
    TabSpec(TopLevelTab.PLAY, "Play", GoaIcons.NavPlaySelected, GoaIcons.NavPlay),
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

/** A request from outside the UI (launcher/notification intent) to show a tab; [id] makes repeats distinct. */
data class TabRequest(val tab: TopLevelTab, val id: Long)

/** A sync file another app handed to this one ([uri], content scheme); opens the import on the Players tab. */
data class SyncFileRequest(val uri: String, val id: Long)

/** An online invite opened from outside the app ([code], a room's); goes to the Play tab's online home. */
data class OnlineInviteRequest(val code: String, val id: Long)

/**
 * App shell: bottom navigation over one Navigation 3 back stack per top-level tab.
 *
 * Every tab's entries are decorated all the time (saveable state + ViewModel store per entry),
 * and only the current tab's are displayed, so switching tabs keeps each tab's screens, scroll
 * positions, half-filled forms and ViewModels; they are dropped only when popped (ADR-020).
 *
 * [coldStart]: a fresh launch, which opens the Timer tab when a session is running or paused.
 */
@Composable
fun GoaApp(
    tabRequests: StateFlow<TabRequest?>? = null,
    syncFileRequests: StateFlow<SyncFileRequest?>? = null,
    onlineInvites: StateFlow<OnlineInviteRequest?>? = null,
    coldStart: Boolean = false,
) {
    val navigator = rememberAppNavigator()
    val shell = hiltViewModel<ShellViewModel>()
    val session by shell.session.collectAsStateWithLifecycle()

    val request = tabRequests?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(request?.id) {
        val tab = request?.tab ?: return@LaunchedEffect
        if (tab == TopLevelTab.TIMER) navigator.openTimer() else navigator.selectTab(tab)
    }
    val syncFile = syncFileRequests?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(syncFile?.id) {
        val file = syncFile ?: return@LaunchedEffect
        navigator.navigateToTab(TopLevelTab.PLAYERS, SyncFileImportRoute(file.uri))
    }
    val invite = onlineInvites?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(invite?.id) {
        val code = invite?.code ?: return@LaunchedEffect
        navigator.navigateToTab(TopLevelTab.PLAY, OnlineHomeRoute(code))
    }
    LaunchedEffect(Unit) {
        val asked = request != null || syncFile != null || invite != null
        if (coldStart && !asked && shell.session.first { it != SessionState.LOADING }.active) {
            navigator.openTimer()
        }
    }

    val provider = entryProvider {
        catalogEntries(navigator)
        timerEntries(navigator)
        draftEntries(navigator)
        playersEntries(navigator)
        settingsEntries(navigator)
        rulesEntries(navigator)
        syncEntries(navigator)
        triviaEntries(navigator)
        boardEntries(navigator)
    }
    val entriesByTab = TopLevelTab.entries.associateWith { tab ->
        key(tab) {
            rememberDecoratedNavEntries(
                backStack = navigator.stack(tab),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                // Keys are per tab, so the same screen open in two tabs never shares state.
                entryProvider = { navKey -> inTab(tab, navKey, provider(navKey)) },
            )
        }
    }

    // The screen under any bottom sheet (e.g. the level ladder over the running timer).
    val entries = entriesByTab.getValue(navigator.currentTab)
    val top = entries.indexOfLast { it.metadata[NavSheet.KEY] != true }
        .let { navigator.currentStack.getOrNull(it) ?: navigator.currentStack.lastOrNull() }
    val onRunningTimer = navigator.currentTab == TopLevelTab.TIMER && top == TimerRoute
    // The running timer, the draft board, a sandbox board, a game against the computer and a puzzle are
    // full-screen table tools: no tab bar to mis-tap next to Next, a pick or the map. System back
    // leaves them (timer → Heroes, draft board → draft setup, board or game → where it was opened from).
    val fullScreenTool = top == DraftBoardRoute || top is BoardRoute || top == GameRoute || top is PuzzleRoute ||
        top is OnlineGameRoute
    val showBottomBar = !(onRunningTimer && session.active) && !fullScreenTool
    val showTimerBar = session.active && !onRunningTimer && !fullScreenTool
    KeepScreenOnWhile(session == SessionState.RUNNING && showTimerBar)

    // Back on another tab's root returns to Heroes (NavDisplay only handles back within a stack).
    BackHandler(enabled = navigator.currentTab != TopLevelTab.CATALOG && navigator.currentStack.size == 1) {
        navigator.back()
    }
    val snackbar = remember { SnackbarHostState() }
    ShellMessages(navigator, snackbar)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = showTimerBar,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val running by shell.runningTimer.collectAsStateWithLifecycle(initialValue = null)
                    running?.let { RunningTimerBar(it, onClick = navigator::openTimer) }
                }
                if (showBottomBar) GoaBottomBar(navigator.currentTab, navigator::selectTab)
            }
        },
    ) { padding ->
        NavDisplay(
            entries = entries,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            onBack = { navigator.back() },
            sceneStrategies = listOf(BottomSheetSceneStrategy()),
            transitionSpec = { push(forward = true) },
            popTransitionSpec = { push(forward = false) },
            predictivePopTransitionSpec = { _ -> push(forward = false) },
        )
    }
}

/** [entry] for [key] with its content key prefixed by the tab. */
private fun inTab(tab: TopLevelTab, key: NavKey, entry: NavEntry<NavKey>): NavEntry<NavKey> =
    NavEntry(key = key, contentKey = "${tab.name}:${entry.contentKey}", metadata = entry.metadata) { entry.Content() }

/** Snackbars for [AppNavigator.showMessage], one at a time. */
@Composable
private fun ShellMessages(navigator: AppNavigator, host: SnackbarHostState) {
    val next = navigator.messages.firstOrNull()
    LaunchedEffect(next?.id) {
        val message = next ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message = message.text,
            actionLabel = message.actionLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        navigator.messageShown(message.id)
    }
}

/** Keeps the display awake (window flag) while [enabled], e.g. a running timer seen from another tab. */
@Composable
private fun KeepScreenOnWhile(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (enabled) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
internal fun GoaBottomBar(current: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    // Five tabs at large text: one size for all, as big as lets "Players" keep its letters in a fifth of the width.
    val widest = (LocalConfiguration.current.screenWidthDp * TAB_LABEL_WIDTH_SHARE).dp
    val theme = MaterialTheme.typography.labelMedium.fontSize
    val labelSize = with(LocalDensity.current) { minOf(theme.toDp(), widest).toSp() }
    GoaPlateTheme {
        // The slate with its steel edge (mock-up 06); the plate is drawn here so it reaches under the system bar.
        Box(Modifier.slatePlate(RectangleShape)) {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                Tabs.forEach { spec ->
                    val selected = current == spec.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelect(spec.tab) },
                        // Stable hook for instrumented tests: "Players" is also an inner tab label.
                        modifier = Modifier.testTag("nav_${spec.tab.name.lowercase()}"),
                        icon = {
                            Icon(
                                painterResource(if (selected) spec.selected else spec.unselected),
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                spec.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = labelSize,
                                maxLines = 1,
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
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
}

/** A tab label's largest size as a share of the screen's width: "Players" in bold is about four times its size wide. */
private const val TAB_LABEL_WIDTH_SHARE = 0.042f
