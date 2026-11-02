package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.Screen
import org.jetbrains.compose.web.dom.Div

/** The Play tab's first screen (ADR-055, as on the phone): Board, with games and puzzles, and Card trivia. */
@Composable
fun PlayHomeScreen() {
    val router = LocalServices.current.router
    Screen("Play", actions = { IconLink("settings", "Settings", Route.Settings) }) {
        Div({ classes("goa-board-hub") }) {
            EntryCard(
                icon = "board",
                title = "Board",
                text = "The real map for any line-up, games against the computer, and puzzles.",
                enabled = true,
            ) { router.navigate(Route.Boards) }
            EntryCard(
                icon = "trivia",
                title = "Card trivia",
                text = "A quiz on the numbers printed on every hero's cards, with your group's leaderboard.",
                enabled = true,
            ) { router.navigate(Route.Trivia()) }
        }
    }
}
