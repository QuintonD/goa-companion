package dev.quinton.goa.web.screens

import androidx.compose.runtime.Composable
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen

/**
 * PLACEHOLDERS — the web shell's stand-ins for screens the streams build (ADR-043). They must not
 * ship: each stream replaces its own in `ScreenFor` and deletes its entry here.
 */
enum class Placeholder(val icon: String, val what: String) {
    CARD_VIEWER("nav_heroes", "Card scans with their text"),
    TRIVIA("trivia", "A quiz on the numbers printed on every hero's cards"),
    BOARDS("board", "The real map with heroes, minions and tokens"),
    BOARD("board", "The board"),
    PLAY("play", "The real game against computer opponents, with an optional Coach"),
    PUZZLES("puzzle", "Difficult solver-proven positions with nudges"),
    PUZZLE("puzzle", "A puzzle"),
    RULES("rules", "The level & defeat ladder and the quick-reference topics"),
    TIMER("nav_timer", "Phase countdowns built for the table"),
    DRAFT("nav_draft", "Pick/ban drafting that feeds a match record"),
}

data class PlaceholderLink(val title: String, val icon: String, val route: Route)

@Composable
fun PlaceholderScreen(
    title: String,
    placeholder: Placeholder,
    settingsAction: Boolean = false,
    links: List<PlaceholderLink> = emptyList(),
) {
    Screen(title, actions = { if (settingsAction) IconLink("settings", "Settings", Route.Settings) }) {
        if (links.isNotEmpty()) {
            GoaCard(flush = true) {
                RowList {
                    links.forEach { ListRow(it.title, leadingIcon = it.icon, route = it.route) }
                }
            }
        }
        EmptyState(placeholder.icon, "Coming to the web app", placeholder.what)
    }
}
