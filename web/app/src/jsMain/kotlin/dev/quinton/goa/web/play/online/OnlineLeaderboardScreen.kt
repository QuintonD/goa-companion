package dev.quinton.goa.web.play.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardLoad
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardPresenter
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRowUi
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardTab
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.rememberGuardedScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/**
 * The public leaderboard (ADR-056): Players and Heroes, from Pages, else a game host, else this device's last copy,
 * which says how old it is. Your own row is marked; names are text only. Everything is worked out by
 * [LeaderboardPresenter], as on the phone.
 */
@Composable
fun OnlineLeaderboardScreen() {
    val services = LocalServices.current
    val online = services.data.online
    val scope = rememberGuardedScope { services.messages.show("The leaderboard couldn't be read.") }
    var load by remember { mutableStateOf<LeaderboardLoad?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(LeaderboardTab.PLAYERS) }
    var you by remember { mutableStateOf<String?>(null) }
    val refresh: () -> Unit = {
        scope.launch {
            loading = true
            try {
                online.leaderboard.load()?.let { load = it }
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) {
        you = online.identities.load()?.accountId
        refresh()
    }
    val ui = LeaderboardPresenter.present(load, tab, you, loading)
    Screen("Leaderboard", actions = { IconButton("refresh", "Read it again", refresh) }) {
        Div({
            classes("goa-trivia-tabs")
            attr("role", "tablist")
            attr("aria-label", "Board")
        }) {
            ui.tabs.forEach { option ->
                Button({
                    type(ButtonType.Button)
                    classes("goa-trivia-tab", "t-title-small")
                    attr("role", "tab")
                    attr("aria-selected", (option == ui.tab).toString())
                    onClick { tab = option }
                }) { Text(option.label) }
            }
        }
        ui.status?.let { status ->
            P({
                classes("t-body-small", "c-variant", "goa-online-status")
                if (ui.stale) classes("stale")
                attr("role", "status")
            }) { Text(status) }
        }
        when {
            ui.rows.isNotEmpty() -> Ul({
                classes("goa-trivia-board", "goa-plate")
                attr("role", "tabpanel")
            }) { ui.rows.forEach { Li { LeaderRow(it) } } }

            ui.empty != null -> EmptyState("trophy", "Nothing on this board", ui.empty.orEmpty())

            else -> LoadingState("Reading the leaderboard")
        }
    }
}

@Composable
private fun LeaderRow(row: LeaderboardRowUi) {
    Div({
        classes("goa-online-rank")
        if (row.isYou) {
            classes("you")
            attr("aria-current", "true")
        }
    }) {
        Span({ classes("goa-online-rank-place", "t-numeric") }) { Text("${row.rank}") }
        Span({ classes("goa-online-player-text") }) {
            Span({ classes("t-title-medium", "goa-online-name") }) { Text(row.name) }
            Span({ classes("t-body-small", "c-variant") }) { Text("${row.games} games · ${row.wins} wins") }
        }
        if (row.isYou) Span({ classes("goa-online-you", "t-label-medium") }) { Text("You") }
        Span({ classes("goa-online-rank-rating", "t-numeric") }) { Text("${row.rating}") }
    }
}
