package dev.quinton.goa.web.screens

import androidx.compose.runtime.Composable
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.catalog.CardViewerScreen
import dev.quinton.goa.web.catalog.CatalogScreen
import dev.quinton.goa.web.catalog.HeroDetailScreen
import dev.quinton.goa.web.draft.DraftBoardScreen
import dev.quinton.goa.web.draft.DraftMethodEditScreen
import dev.quinton.goa.web.draft.DraftMethodsScreen
import dev.quinton.goa.web.draft.DraftSetupScreen
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.rules.LevelLadderScreen
import dev.quinton.goa.web.rules.RulesScreen
import dev.quinton.goa.web.rules.RulesTopicScreen
import dev.quinton.goa.web.settings.AboutScreen
import dev.quinton.goa.web.settings.SettingsScreen
import dev.quinton.goa.web.timer.MatchSetupScreen
import dev.quinton.goa.web.timer.PresetEditScreen
import dev.quinton.goa.web.timer.PresetsScreen
import dev.quinton.goa.web.timer.TimerScreen
import dev.quinton.goa.web.trivia.TriviaHomeScreen
import dev.quinton.goa.web.trivia.TriviaLeaderboardScreen
import dev.quinton.goa.web.trivia.TriviaQuizScreen
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

        is Route.CardViewer -> CardViewerScreen(route.heroId, route.cardId)

        is Route.Trivia -> TriviaHomeScreen(route.heroId)

        is Route.TriviaQuiz -> TriviaQuizScreen(route)

        Route.TriviaLeaderboard -> TriviaLeaderboardScreen()

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

        is Route.LevelLadder -> LevelLadderScreen(route.heroIds)

        Route.Settings -> SettingsScreen()

        Route.About -> AboutScreen()

        // Timer tab (W3 table)
        Route.Timer -> TimerScreen()

        is Route.MatchSetup -> MatchSetupScreen(route)

        Route.TimerPresets -> PresetsScreen()

        is Route.TimerPresetEdit -> PresetEditScreen(route.presetId)

        // Draft tab (W3 table)
        Route.Draft -> DraftSetupScreen()

        Route.DraftBoard -> DraftBoardScreen()

        Route.DraftMethods -> DraftMethodsScreen()

        is Route.DraftMethodEdit -> DraftMethodEditScreen(route.methodId)

        // Opened by the table tools, built by other streams (W2 matches and players, W4 board)
        is Route.MatchEdit -> PlaceholderScreen("Match", Placeholder.MATCH_EDIT)

        is Route.PlayerEdit -> PlaceholderScreen("Player", Placeholder.PLAYER_EDIT)

        is Route.NewBoard -> PlaceholderScreen("Board", Placeholder.BOARD)

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
