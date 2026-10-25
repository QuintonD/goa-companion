package dev.quinton.goa.web.trivia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.trivia.LeaderboardRowUi
import dev.quinton.goa.core.data.trivia.LeaderboardTab
import dev.quinton.goa.core.data.trivia.PlayerRoundsUi
import dev.quinton.goa.core.data.trivia.RoundUi
import dev.quinton.goa.core.data.trivia.TriviaLeaderboardModel
import dev.quinton.goa.core.data.trivia.TriviaLeaderboardUiState
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ChipRow
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.ErrorState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.browser.document
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.events.Event

/** The tab and period, kept when the leaderboard is left and returned to with Back. */
private class BoardChoice {
    var tab by mutableStateOf(LeaderboardTab.CHALLENGE)
    var period by mutableStateOf(LeaderboardPeriod.ALL_TIME)
}

/**
 * The trivia leaderboard (ADR-032): Challenge and Practice tabs, All time / Last 30 days, ranked
 * rows from the group's synced rounds; a player's rounds in a sheet, each removable after a
 * confirmation. The shared [TriviaLeaderboardModel] builds the boards.
 */
@Composable
fun TriviaLeaderboardScreen() {
    val services = LocalServices.current
    val group = rememberTriviaGroup()
    var attempt by remember { mutableStateOf(0) }
    // The rounds live in the group's store (W2): if this browser can't read them, say so.
    val reachable = rememberLoad(attempt) { group.rounds.observeRounds().first() }
    Screen("Leaderboard") {
        when (reachable.state) {
            Load.Loading -> LoadingState("Loading the leaderboard")

            is Load.Failed -> ErrorState(
                "The leaderboard isn't available",
                "This browser couldn't read the group's trivia rounds. Try again in a moment.",
                onRetry = { attempt++ },
            )

            is Load.Ready -> Board(group)
        }
    }
}

@Composable
private fun Board(group: TriviaGroupAccess) {
    val services = LocalServices.current
    val choice = rememberRetained("trivia-board") { BoardChoice() }
    val scope = rememberScreenScope { services.messages.show("Couldn't update the leaderboard. Try again.") }
    val model = remember {
        TriviaLeaderboardModel(
            scope,
            group.rounds,
            group.players,
            group.groups,
            services.data.clock,
            initialTab = choice.tab,
            initialPeriod = choice.period,
        )
    }
    val state by model.uiState.collectAsState()
    var confirm by remember { mutableStateOf<String?>(null) }

    // Back in view: "Last 30 days" counts from now.
    DisposableEffect(model) {
        val listener: (Event) -> Unit = {
            if (document.asDynamic().visibilityState == "visible") model.onResume()
        }
        document.addEventListener("visibilitychange", listener)
        onDispose { document.removeEventListener("visibilitychange", listener) }
    }
    val ready = state as? TriviaLeaderboardUiState.Ready
    LaunchedEffect(ready?.message) {
        val message = ready?.message ?: return@LaunchedEffect
        services.messages.show(message)
        model.messageShown()
    }

    when (val current = state) {
        TriviaLeaderboardUiState.Loading -> LoadingState("Loading the leaderboard")

        is TriviaLeaderboardUiState.Ready -> BoardContent(
            state = current,
            onTab = {
                choice.tab = it
                model.selectTab(it)
            },
            onPeriod = {
                choice.period = it
                model.selectPeriod(it)
            },
            onOpenPlayer = model::openPlayer,
        )
    }

    val selected = ready?.selected
    if (ready != null && selected != null) {
        BottomSheet(selected.player.name, onDismiss = model::closePlayer) {
            PlayerRoundsContent(selected, ready.period, onRemove = { confirm = it })
        }
    }
    val round = confirm?.let { id -> selected?.rounds?.firstOrNull { it.id == id } }
    if (round != null) {
        AlertDialog("Remove from leaderboard?", onDismiss = { confirm = null }, buttons = {
            GoaButton("Cancel", { confirm = null }, ButtonKind.TEXT)
            GoaButton("Remove", {
                confirm = null
                model.removeRound(round.id)
            }, ButtonKind.DANGER)
        }) {
            Text(
                "${round.label} comes off the leaderboard here now, and on the group's other phones " +
                    "when they next sync. This can't be undone.",
            )
        }
    }
}

@Composable
private fun BoardContent(
    state: TriviaLeaderboardUiState.Ready,
    onTab: (LeaderboardTab) -> Unit,
    onPeriod: (LeaderboardPeriod) -> Unit,
    onOpenPlayer: (String) -> Unit,
) {
    Div({
        classes("goa-trivia-tabs")
        attr("role", "tablist")
        attr("aria-label", "Board")
    }) {
        LeaderboardTab.entries.forEach { tab ->
            Button({
                type(ButtonType.Button)
                classes("goa-trivia-tab", "t-title-small")
                attr("role", "tab")
                attr("aria-selected", (tab == state.tab).toString())
                onClick { onTab(tab) }
            }) { Text(TriviaText.tabLabel(tab)) }
        }
    }
    Div({ classes("goa-trivia-board-head") }) {
        ChipRow(wrap = true) {
            LeaderboardPeriod.entries.forEach { period ->
                Chip(TriviaText.periodLabel(period), period == state.period, { onPeriod(period) })
            }
        }
        P({ classes("t-body-small", "c-variant") }) {
            Text(
                if (state.tab == LeaderboardTab.CHALLENGE) {
                    "Each player's best Challenge: most right answers, then the quicker time."
                } else {
                    "Every round played as a player, Challenges included: most right answers, then accuracy."
                },
            )
        }
    }
    if (state.rows.isEmpty()) {
        EmptyBoard(state)
    } else {
        Ul({
            classes("goa-trivia-board")
            attr("role", "tabpanel")
        }) {
            state.rows.forEach { row -> Li { RankedRow(row, onClick = { onOpenPlayer(row.player.id) }) } }
        }
    }
}

@Composable
private fun EmptyBoard(state: TriviaLeaderboardUiState.Ready) {
    val challenge = state.tab == LeaderboardTab.CHALLENGE
    when {
        state.olderScores -> EmptyState(
            "trophy",
            if (challenge) "No Challenges in the last 30 days" else "No rounds in the last 30 days",
            "Older scores are under All time. Play a round to get back on the board.",
        )

        challenge -> EmptyState(
            "trophy",
            "Play a Challenge to get on the board",
            "20 questions on every hero, the same quiz for everyone. On Card trivia, pick who's playing and " +
                "tap Challenge.",
        )

        else -> EmptyState(
            "trophy",
            "Play a round as a player to get on the board",
            "Pick who's playing on Card trivia: every round they finish counts here. Guest rounds aren't recorded.",
        )
    }
}

/** One ranked row; at large text the figure moves under the name (the row reflows). */
@Composable
private fun RankedRow(row: LeaderboardRowUi, onClick: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-rank")
        attr("aria-label", "${row.spoken}. See rounds")
        onClick { onClick() }
    }) {
        PlaceBadge(row.place, row.tied)
        PlayerAvatar(row.player)
        Span({ classes("goa-trivia-rank-body") }) {
            Span({ classes("goa-trivia-pick-title") }) {
                Span({ classes("goa-trivia-rank-name", "t-title-medium") }) { Text(row.player.name) }
                if (row.isMe) YouTag()
            }
            Span({ classes("t-body-small", "c-variant") }) { Text(row.detail) }
        }
        Span({
            classes("goa-trivia-rank-main")
            if (row.place == 1) classes("first")
        }) {
            Span({ classes("t-title-large", "t-numeric") }) { Text(row.main) }
            row.mainCaption?.let { Span({ classes("t-label-medium", "c-variant") }) { Text(it) } }
        }
    }
}

/**
 * The place as a number in a medallion (never colour alone): bronze for the top three, the first
 * filled; a shared place carries "=".
 */
@Composable
private fun PlaceBadge(place: Int, tied: Boolean) {
    Span({
        classes("goa-trivia-place", "t-label-large")
        when {
            place == 1 -> classes("first")
            place <= PODIUM -> classes("podium")
        }
        attr("aria-hidden", "true")
    }) { Text(if (tied) "=$place" else "$place") }
}

/**
 * A player's rounds in the period: their best Challenge (the one on the board) first, then the rest
 * newest first, each with "Remove from leaderboard".
 */
@Composable
private fun PlayerRoundsContent(rounds: PlayerRoundsUi, period: LeaderboardPeriod, onRemove: (String) -> Unit) {
    Div({ classes("goa-trivia-rounds-head") }) {
        PlayerAvatar(rounds.player)
        Span({ classes("goa-trivia-pick-text") }) {
            if (rounds.isMe) Span({ classes("goa-trivia-pick-title") }) { YouTag() }
            Span({ classes("t-body-small", "c-variant") }) { Text("Rounds · ${TriviaText.periodLabel(period)}") }
        }
    }
    if (rounds.rounds.isEmpty()) {
        P({ classes("t-body-medium", "c-variant") }) { Text("No rounds in this period.") }
    }
    Ul({ classes("goa-trivia-rounds") }) {
        rounds.rounds.forEach { round -> Li { RoundRow(round, onRemove = { onRemove(round.id) }) } }
    }
}

@Composable
private fun RoundRow(round: RoundUi, onRemove: () -> Unit) {
    Div({
        classes("goa-trivia-round")
        attr("role", "group")
        attr("aria-label", round.spoken)
    }) {
        Div({
            classes("goa-trivia-round-line")
            attr("aria-hidden", "true")
        }) {
            Span({ classes("goa-trivia-round-text") }) {
                Span({
                    classes("t-label-medium", "goa-trivia-round-kind")
                    if (round.kind != "Practice") classes("c-primary") else classes("c-variant")
                }) { Text(round.kind.uppercase()) }
                Span({ classes("t-body-small", "c-variant") }) { Text(round.detail) }
            }
            Span({ classes("t-title-medium", "t-numeric") }) { Text(round.score) }
        }
        Button({
            type(ButtonType.Button)
            classes("goa-button", "text", "goa-trivia-remove")
            attr("aria-label", "Remove from leaderboard: ${round.label}")
            onClick { onRemove() }
        }) {
            Icon("delete", IconSize.SMALL)
            Text("Remove from leaderboard")
        }
    }
}

private const val PODIUM = 3
