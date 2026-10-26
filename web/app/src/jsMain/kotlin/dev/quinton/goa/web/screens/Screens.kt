package dev.quinton.goa.web.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.catalog.CardViewerScreen
import dev.quinton.goa.web.catalog.CatalogScreen
import dev.quinton.goa.web.catalog.HeroDetailScreen
import dev.quinton.goa.web.draft.DraftBoardScreen
import dev.quinton.goa.web.draft.DraftMethodEditScreen
import dev.quinton.goa.web.draft.DraftMethodsScreen
import dev.quinton.goa.web.draft.DraftSetupScreen
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.players.MatchEditScreen
import dev.quinton.goa.web.players.PlayerDetailScreen
import dev.quinton.goa.web.players.PlayerEditScreen
import dev.quinton.goa.web.players.PlayersHomeScreen
import dev.quinton.goa.web.players.PlayersTab
import dev.quinton.goa.web.players.RatingSettingsScreen
import dev.quinton.goa.web.rules.LevelLadderScreen
import dev.quinton.goa.web.rules.RulesScreen
import dev.quinton.goa.web.rules.RulesTopicScreen
import dev.quinton.goa.web.settings.AboutScreen
import dev.quinton.goa.web.settings.SettingsScreen
import dev.quinton.goa.web.sync.GroupScreen
import dev.quinton.goa.web.sync.JoinLinkScreen
import dev.quinton.goa.web.sync.WhoAreYouScreen
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

        Route.Boards -> dev.quinton.goa.web.play.board.BoardsScreen()

        is Route.Board -> dev.quinton.goa.web.play.board.BoardScreen(route.boardId)

        is Route.NewBoard -> dev.quinton.goa.web.play.board.NewBoardScreen(route)

        is Route.OpenBoard -> dev.quinton.goa.web.play.board.OpenBoardScreen(route)

        Route.Play -> dev.quinton.goa.web.play.game.GameScreen()

        is Route.NewGame -> dev.quinton.goa.web.play.game.NewGameScreen(route)

        Route.Puzzles -> dev.quinton.goa.web.play.puzzles.PuzzleListScreen()

        is Route.Puzzle -> key(route.puzzleId) { dev.quinton.goa.web.play.puzzles.PuzzleScreen(route.puzzleId) }

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

        // Players tab (group stream W2: players, matches, meta, group sync)
        Route.Players -> PlayersHomeScreen(PlayersTab.PLAYERS)

        is Route.PlayerDetail -> PlayerDetailScreen(route.playerId)

        is Route.PlayerEdit -> PlayerEditScreen(route.playerId)

        Route.Matches -> PlayersHomeScreen(PlayersTab.MATCHES)

        is Route.MatchEdit -> MatchEditScreen(route)

        Route.Meta -> PlayersHomeScreen(PlayersTab.META)

        Route.RatingSettings -> RatingSettingsScreen()

        Route.Group -> GroupScreen()

        Route.WhoAreYou -> WhoAreYouScreen()

        is Route.Join -> JoinLinkScreen(route.invite)

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
