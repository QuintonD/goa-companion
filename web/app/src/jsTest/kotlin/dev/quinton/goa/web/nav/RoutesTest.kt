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
        Route.Draft,
        Route.Players,
        Route.PlayerDetail("p-1"),
        Route.Matches,
        Route.Meta,
        Route.Group,
        Route.PlayerEdit(),
        Route.PlayerEdit("p-1"),
        Route.MatchEdit(),
        Route.MatchEdit("m-1"),
        Route.MatchEdit("m-1", result = true),
        Route.RatingSettings,
        Route.WhoAreYou,
        Route.Join("goa-sync:1?g=6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d&k=AAEC&n=Friday%20%26%20co"),
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
    fun unknownAddressesAreNotFoundRatherThanAGuess() {
        listOf("#/nope", "#/heroes/a/b", "#/heroes/a/cards", "#/settings/x", "#/timer/1", "#/players//x").forEach {
            assertIs<Route.NotFound>(Routes.parse(it), it)
        }
    }

    @Test
    fun anInviteLinkOpensJoiningWithTheInviteUntouched() {
        val invite = "goa-sync:1?g=6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d&k=AAEC&n=Friday%20%26%20co&s=https%3A%2F%2Fa.b"
        // The QR code's link (GroupInvite.link) as an iPhone's Camera opens it.
        assertEquals(Route.Join(invite), Routes.parse("#join=$invite"))
        assertEquals(Route.Group, Route.Join(invite).parent)
        assertEquals(Route.PlayerEdit("p-1"), Routes.parse("#/players/p-1/edit"))
        assertEquals(Route.MatchEdit("m-1", result = true), Routes.parse("#/matches/m-1?result=1"))
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
