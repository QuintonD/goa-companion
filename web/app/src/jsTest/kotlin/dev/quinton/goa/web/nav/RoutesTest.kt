package dev.quinton.goa.web.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoutesTest {
    private val every: List<Route> = listOf(
        Route.Catalog,
        Route.HeroDetail("arien"),
        Route.CardViewer("arien", "arien-gold"),
        Route.Trivia(),
        Route.Trivia("tigerclaw"),
        Route.Boards,
        Route.Board("b1"),
        Route.Play,
        Route.Puzzles,
        Route.Puzzle("p17"),
        Route.Rules,
        Route.RulesTopic("movement"),
        Route.Settings,
        Route.About,
        Route.Timer,
        Route.MatchSetup(),
        Route.MatchSetup(matchId = "m-1"),
        Route.MatchSetup(rematchOf = "m-1", rematchKind = "SWAP_TEAMS"),
        Route.TimerPresets,
        Route.TimerPresetEdit(),
        Route.TimerPresetEdit("table-rules"),
        Route.Draft,
        Route.DraftBoard,
        Route.DraftMethods,
        Route.DraftMethodEdit(),
        Route.DraftMethodEdit("pick-and-ban"),
        Route.Players,
        Route.PlayerDetail("p-1"),
        Route.PlayerEdit(),
        Route.PlayerEdit("p-1"),
        Route.Matches,
        Route.MatchEdit(),
        Route.MatchEdit("m-1", result = true, prefillPlayedAt = 1_760_000_000_000),
        Route.MatchEdit(
            prefillDurationMin = 47,
            prefillMode = "double_lane",
            result = true,
            prefillPlayedAt = 1_760_000_000_000,
            newMatchId = "a b&c",
        ),
        Route.LevelLadder(),
        Route.LevelLadder(listOf("arien", "brogan")),
        Route.NewBoard(listOf("arien", "brogan"), listOf("tigerclaw")),
        Route.Meta,
        Route.Group,
    )

    @Test
    fun everyRouteSurvivesItsOwnHash() {
        every.forEach { route -> assertEquals(route, Routes.parse(Routes.hash(route)), Routes.hash(route)) }
    }

    @Test
    fun anEmptyOrBareHashOpensHeroes() {
        listOf("", "#", "#/", "/").forEach { assertEquals(Route.Catalog, Routes.parse(it), "'$it'") }
    }

    @Test
    fun aTrailingSlashOrMissingHashStillParses() {
        assertEquals(Route.HeroDetail("arien"), Routes.parse("#/heroes/arien/"))
        assertEquals(Route.HeroDetail("arien"), Routes.parse("heroes/arien"))
    }

    @Test
    fun idsAreEncodedAndDecoded() {
        val route = Route.PlayerDetail("Ann & Bo/1")
        assertEquals("#/players/Ann%20%26%20Bo%2F1", Routes.hash(route))
        assertEquals(route, Routes.parse(Routes.hash(route)))
    }

    @Test
    fun theTriviaHeroIsAQueryParameter() {
        assertEquals("#/trivia?hero=brogan", Routes.hash(Route.Trivia("brogan")))
        assertEquals(Route.Trivia(null), Routes.parse("#/trivia?hero="))
    }

    @Test
    fun theTableToolsHaveTheirOwnAddresses() {
        assertEquals("#/timer/setup?match=m-1", Routes.hash(Route.MatchSetup("m-1")))
        assertEquals("#/timer/presets/new", Routes.hash(Route.TimerPresetEdit()))
        assertEquals("#/draft/methods/new", Routes.hash(Route.DraftMethodEdit()))
        // The running draft and a board hide the tab bar (full-screen table tools), as on the phone.
        assertEquals(false, Route.DraftBoard.showsTabBar)
        assertEquals(true, Route.Timer.showsTabBar)
        assertEquals(Route.Draft, Route.DraftBoard.parent)
        assertEquals(Route.TimerPresets, Route.TimerPresetEdit("x").parent)
    }

    @Test
    fun unknownAddressesAreNotFoundRatherThanAGuess() {
        listOf(
            "#/nope", "#/heroes/a/b", "#/heroes/a/cards", "#/settings/x", "#/timer/1", "#/players//x",
            "#/draft/board/x", "#/timer/presets/a/b", "#/matches/a/b", "#/levels/x",
        ).forEach {
            assertIs<Route.NotFound>(Routes.parse(it), it)
        }
    }

    @Test
    fun aMalformedEscapeDoesNotCrash() {
        assertEquals(Route.HeroDetail("%E0%A4%A"), Routes.parse("#/heroes/%E0%A4%A"))
    }

    @Test
    fun screensBelongToTheAndroidTabs() {
        assertEquals(Tab.HEROES, Route.Puzzle("p1").tab)
        assertEquals(Tab.PLAYERS, Route.Group.tab)
        assertEquals(null, Route.Settings.tab)
        assertEquals(null, Route.Rules.tab)
        assertEquals(listOf("Heroes", "Timer", "Draft", "Players"), Tab.entries.map { it.label })
    }
}
