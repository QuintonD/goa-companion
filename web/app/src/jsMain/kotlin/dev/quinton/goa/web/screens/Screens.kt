package dev.quinton.goa.web.screens

import androidx.compose.runtime.Composable
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.rules.RulesScreen
import dev.quinton.goa.web.rules.RulesTopicScreen
import dev.quinton.goa.web.settings.AboutScreen
import dev.quinton.goa.web.settings.SettingsScreen
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Screen

/**
 * The screen for each route. A stream replaces its placeholder line with its real screen
 * (keep the screen in its own package, e.g. `dev.quinton.goa.web.timer`).
 */
@Composable
fun ScreenFor(route: Route) {
    when (route) {
        // Heroes tab (catalog stream: Catalog, HeroDetail, CardViewer)
        Route.Catalog -> CatalogScreen()

        is Route.HeroDetail -> HeroDetailScreen(route.heroId)

        is Route.CardViewer -> PlaceholderScreen("Card", Placeholder.CARD_VIEWER)

        is Route.Trivia -> PlaceholderScreen("Card trivia", Placeholder.TRIVIA)

        Route.Boards -> PlaceholderScreen(
            "Board",
            Placeholder.BOARDS,
            links = listOf(
                PlaceholderLink("Play against the computer", "play", Route.Play),
                PlaceholderLink("Puzzles", "puzzle", Route.Puzzles),
            ),
        )

        is Route.Board -> PlaceholderScreen("Board", Placeholder.BOARD)

        Route.Play -> PlaceholderScreen("Play", Placeholder.PLAY)

        Route.Puzzles -> PlaceholderScreen("Puzzles", Placeholder.PUZZLES)

        is Route.Puzzle -> PlaceholderScreen("Puzzle", Placeholder.PUZZLE)

        // Shared
        Route.Rules -> RulesScreen()

        is Route.RulesTopic -> RulesTopicScreen(route.topicId)

        Route.Settings -> SettingsScreen()

        Route.About -> AboutScreen()

        // Timer tab
        Route.Timer -> PlaceholderScreen("Timer", Placeholder.TIMER, settingsAction = true)

        // Draft tab
        Route.Draft -> PlaceholderScreen("Draft", Placeholder.DRAFT, settingsAction = true)

        // Players tab
        Route.Players -> PlaceholderScreen(
            "Players",
            Placeholder.PLAYERS,
            settingsAction = true,
            links = listOf(
                PlaceholderLink("Match log", "list", Route.Matches),
                PlaceholderLink("Meta", "chart", Route.Meta),
                PlaceholderLink("Group sync", "sync", Route.Group),
            ),
        )

        is Route.PlayerDetail -> PlaceholderScreen("Player", Placeholder.PLAYERS)

        Route.Matches -> PlaceholderScreen("Match log", Placeholder.MATCHES)

        Route.Meta -> PlaceholderScreen("Meta", Placeholder.META)

        Route.Group -> PlaceholderScreen("Group sync", Placeholder.GROUP)

        is Route.NotFound -> NotFoundScreen()
    }
}

@Composable
private fun NotFoundScreen() {
    val router = LocalServices.current.router
    Screen("Not found") {
        EmptyState(
            "info",
            "This page doesn't exist",
            "The link may be from an older version of the app.",
        ) { GoaButton("Go to Heroes", { router.navigate(Route.Catalog, replace = true) }) }
    }
}
