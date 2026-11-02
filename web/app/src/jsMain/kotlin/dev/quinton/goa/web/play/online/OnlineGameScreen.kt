package dev.quinton.goa.web.play.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameOverUi
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.OnlineUi
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.online.OnlineGameDriver
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.cards.CardFocusHost
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.game.GameTable
import dev.quinton.goa.web.play.game.HeroResultRow
import dev.quinton.goa.web.play.game.KeepAwake
import dev.quinton.goa.web.play.table.Overline
import dev.quinton.goa.web.play.table.teamCss
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.rememberGuardedScope
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.events.Event

private const val GAME_FAILED = "Something went wrong in this game. It is safe on the server: open it again."

/**
 * An online game (ADR-056): the same table as a game against the computer, drawn from [OnlineGameDriver]'s frames.
 * The connection can drop at any moment; the table stays while the driver gets back. Coming back to the foreground
 * or onto the network, it doesn't wait out its backoff.
 */
@Composable
fun OnlineGameScreen(gameId: String) {
    val services = LocalServices.current
    val online = services.data.online
    val router = services.router
    val scope = rememberGuardedScope { services.messages.show(GAME_FAILED) }
    val driver = remember {
        OnlineGameDriver(
            gameId = gameId,
            connector = online.connector,
            identities = online.identities,
            catalog = online::catalog,
            maps = { services.data.play.boardMaps.maps.first() },
            clock = services.data.clock,
            scope = scope,
            client = online.client,
        )
    }
    LaunchedEffect(driver) { driver.start() }
    ResumeWhenBack(driver::resumeNow)
    val state by driver.state.collectAsState()
    val focus = remember { CardFocusState() }
    var leaving by remember { mutableStateOf(false) }
    val game = when (val s = state) {
        is OnlineState.InGame -> s.game
        is OnlineState.Reconnecting -> s.game
        else -> null
    }
    val play = (game as? GameUiState.Playing)?.play
    KeepAwake(play != null && play.over == null && play.online?.closed == null)
    when {
        state is OnlineState.Connecting -> Screen("Online game") {
            LoadingState("Getting to the table")
            (state as OnlineState.Connecting).note?.let { note ->
                P({
                    classes("t-body-medium", "goa-online-note")
                    attr("role", "status")
                }) { Text(note) }
            }
        }

        state is OnlineState.Refused -> Screen("Online game") {
            val refused = state as OnlineState.Refused
            EmptyState(
                "cloud_off",
                "This game can't be opened",
                OnlineWords.refusal(refused.reason, refused.retryAfterSeconds),
            ) {
                Div({ classes("goa-button-row") }) {
                    GoaButton("Back to Online", { router.navigate(Route.OnlineHome, replace = true) }, ButtonKind.TEXT)
                    if (refused.reason == Refusal.BAD_TOKEN) {
                        GoaButton("Use my recovery code", { router.navigate(Route.OnlineHome, replace = true) })
                    }
                }
            }
        }

        game is GameUiState.Unavailable -> Screen("Online game") {
            EmptyState("board", "This game's map isn't in this build", "Update the app to play it: your seat waits.")
        }

        play == null -> Screen("Online game") { LoadingState("Getting to the table") }

        play.online?.closed != null || (play.online?.rated != null && play.over == null) -> Screen("Game closed") {
            ClosedGame(play.online)
        }

        else -> {
            GameTable(
                play = play,
                onAction = driver::onAction,
                onTap = driver::tap,
                focus = focus,
                onEnd = { leaving = true },
                over = { over, onHero ->
                    Screen("Game over") { OnlineResult(play, over, onHero) }
                },
                overlay = { OnlineBar(play.table, play.online, state is OnlineState.Reconnecting) },
            )
            LaunchedEffect(play.messageId) {
                play.message?.let {
                    services.messages.show(it)
                    driver.onAction(GameAction.MessageShown(play.messageId))
                }
            }
        }
    }
    CardFocusHost(focus)
    if (leaving) {
        AlertDialog(
            "Leave this game?",
            onDismiss = { leaving = false },
            buttons = {
                GoaButton("Stay", { leaving = false }, ButtonKind.TEXT)
                GoaButton("Leave", {
                    leaving = false
                    driver.leave()
                    router.navigate(Route.OnlineHome, replace = true)
                }, ButtonKind.DANGER)
            },
        ) {
            Text(
                "The computer plays your seat to the end; your team still wins or loses. You can't come back to it. " +
                    "To step away for a moment, just close the app: your seat waits.",
            )
        }
    }
}

/** Foreground and network come back (a backgrounded app loses its socket): the driver reconnects at once. */
@Composable
private fun ResumeWhenBack(resume: () -> Unit) {
    DisposableEffect(Unit) {
        val back: (Event) -> Unit = { if (document.asDynamic().visibilityState != "hidden") resume() }
        document.addEventListener("visibilitychange", back)
        window.addEventListener("online", back)
        window.addEventListener("pageshow", back)
        onDispose {
            document.removeEventListener("visibilitychange", back)
            window.removeEventListener("online", back)
            window.removeEventListener("pageshow", back)
        }
    }
}

/** A game that ended without a result: voided by a rules fix, abandoned, or gone from the server. */
@Composable
private fun ClosedGame(online: OnlineUi?) {
    val router = LocalServices.current.router
    EmptyState("flag", "This game is closed", online?.closed ?: "It ended without a result, so it won't count.") {
        GoaButton("Back to Online", { router.navigate(Route.OnlineHome, replace = true) })
    }
}

/** The result, in the rulebook's words, how each hero did, and whether it counted. */
@Composable
private fun OnlineResult(play: PlayUi, over: GameOverUi, onHero: (String) -> Unit) {
    val router = LocalServices.current.router
    Div({ classes("goa-game-over") }) {
        Div({ classes("goa-over-card", teamCss(over.winner)) }) {
            Icon("swords", IconSize.LARGE)
            H2({ classes("goa-title-plate") }) { Text(if (over.youWon) "You won" else "Your team lost") }
            P({ classes("t-title-medium", "goa-over-winner", teamCss(over.winner)) }) {
                TeamMark(over.winner)
                Text(" ${BoardText.team(over.winner)} wins · ${PlayText.winTitle(over.reason)}")
            }
            P({ classes("t-body-medium") }) { Text(PlayText.winReason(over.reason, over.winner)) }
            P({ classes("t-label-large", "goa-quiet") }) {
                Text(
                    listOf(BoardText.count(over.rounds, "round"), ratedWords(play.online)).joinToString(" · "),
                )
            }
        }
        Overline("How each hero did")
        Div({ classes("goa-over-section") }) {
            Team.entries.forEach { team ->
                over.heroes.filter { it.team == team }.forEach { HeroResultRow(play, it, onHero) }
            }
        }
        Div({ classes("goa-over-actions") }) {
            GoaButton("Back to Online", { router.navigate(Route.OnlineHome, replace = true) }, block = true)
            GoaButton(
                "Leaderboard",
                { router.navigate(Route.OnlineLeaderboard, replace = true) },
                ButtonKind.OUTLINED,
                block = true,
            )
        }
    }
}

private fun ratedWords(online: OnlineUi?): String = when {
    online?.rated == true -> "Rated: it counts on the leaderboard"
    online?.ranked == true -> "Not rated: this one doesn't count"
    else -> "A friendly game: not rated"
}
