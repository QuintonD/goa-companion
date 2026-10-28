package dev.quinton.goa.feature.board.play.review

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.board.readRepoFile
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.play.game.mergedView
import dev.quinton.goa.feature.board.play.table.SlotState
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.slotBand
import dev.quinton.goa.feature.board.play.table.turnLog
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.confirmHint
import dev.quinton.goa.feature.board.ui.cards.confirmLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The table's words that the phone and the web app both show (ADR-043), on a real game of the shipped
 * catalog, on the JVM and on Node: the review's hero cards, the turn track's bands, the focus viewer's
 * Confirm button. The web drew its own and drifted (no "None yet", "SKIP" for "OUT", no "Wait…").
 */
class ReviewHeroTest {
    private fun asset(path: String) = readRepoFile("app/src/main/assets/$path")

    private val catalog = CatalogFiles.load(::asset)
    private val maps: List<BoardMap> =
        CatalogFiles.parseMapIndex(asset(CatalogFiles.MAPS_INDEX_PATH)).map {
            CatalogFiles.parseMap(it, asset(CatalogFiles.mapPath(it)))
        }
    private val engine = GameEngine(catalog, maps)
    private val cards = catalog.heroes.flatMap { it.allCards }.associateBy { it.id }
    private val heroes = catalog.boardHeroes()

    /** The rulebook's first game (p.5); the person plays [humans]. */
    private fun game(humans: Set<String>): GameSession = engine.newGame(
        GameSetup(
            mapId = "forgotten-island",
            seats = listOf("wasp" to Team.RED, "xargatha" to Team.RED, "arien" to Team.BLUE, "brogan" to Team.BLUE)
                .map { (id, team) -> SeatSetup(id, team, controller(id, humans)) },
            tieBreaker = Team.RED,
        ),
    )

    private fun controller(id: String, humans: Set<String>) =
        if (id in humans) Controller.Human else Controller.Ai(AiLevel.EASY)

    private fun table(game: GameSession, humans: Set<String>) = TableUi(
        map = maps.first { it.id == "forgotten-island" },
        view = game.mergedView(humans),
        heroes = heroes,
        cards = cards,
        mine = humans,
        decision = game.pending,
        turnLog = turnLog(game.events.toList()),
    )

    /** Plays with the Easy computer for every seat until [stop] holds. */
    private fun playUntil(game: GameSession, stop: (GameSession) -> Boolean) {
        val ai = AiPlayers.deterministic(AiLevel.EASY, 1)
        repeat(MAX_DECISIONS) {
            if (stop(game)) return
            val d = game.pending ?: return
            game.answer(d.id, ai.choose(game, d))
        }
        error("never got there")
    }

    @Test
    fun beforeAnyCardIsPlayedEveryHeroSaysSoAndTheFactsAreThePhones() {
        val humans = setOf("wasp")
        val table = table(game(humans), humans)
        assertEquals(GamePhase.PLAY_CARDS, table.view.phase)
        val wasp = table.view.heroes.first { it.heroId == "wasp" }
        val words = reviewHero(table, wasp, puzzle = false)
        assertEquals(listOf("Level", "Coins", "In hand"), words.facts.take(3).map { it.first })
        assertEquals("${wasp.level}", words.facts.first().second)
        assertEquals("No card played yet.", words.noCardInPlay)
        assertNull(words.inPlayNote)
        assertEquals("Your hand", words.handTitle)
        val brogan = table.view.heroes.first { it.heroId == "brogan" }
        assertNull(reviewHero(table, brogan, puzzle = false).handTitle, "another hero's hand stays hidden")
    }

    @Test
    fun aHeroThatActedSaysWhereItsCardWentAndTwoHeroesNameTheirHands() {
        val humans = setOf("wasp", "xargatha")
        val game = game(humans)
        playUntil(game) { g ->
            val v = g.mergedView(humans)
            v.phase == GamePhase.RESOLVE && v.heroes.any { it.heroId in v.actedThisTurn && it.played == null }
        }
        val table = table(game, humans)
        val acted = table.view.heroes.first { it.heroId in table.view.actedThisTurn && it.played == null }
        val resolved = reviewHero(table, acted, puzzle = false).noCardInPlay
        assertEquals("Resolved: on Turn slot ${table.view.turn}.", resolved)
        val waspWords = reviewHero(table, table.view.heroes.first { it.heroId == "wasp" }, puzzle = false)
        assertEquals("Wasp's hand", waspWords.handTitle)
        table.view.heroes.filter { it.played != null && it.initiative != null }.forEach { hero ->
            assertEquals("Initiative ${hero.initiative}", reviewHero(table, hero, puzzle = false).inPlayNote)
        }
    }

    @Test
    fun theTrackBandsAreThePhonesWords() {
        assertEquals(
            listOf("DONE", "NOW", "NEXT", null, "PASS", "OUT"),
            listOf(
                SlotState.DONE,
                SlotState.NOW,
                SlotState.NEXT,
                SlotState.LATER,
                SlotState.PASSED,
                SlotState.SKIPPED,
            ).map(::slotBand),
        )
    }

    @Test
    fun confirmSaysWhyACardCantBeChosen() {
        val card = cards.values.first()
        val option = FocusCard(card, "x", enabled = true)
        assertEquals("Play this card", confirmLabel(option, canConfirm = true, action = "Play this card"))
        assertEquals("Wait…", confirmLabel(option, canConfirm = false, action = "Play this card"))
        val notOffered = option.copy(enabled = false)
        assertEquals("Not an option", confirmLabel(notOffered, canConfirm = true, action = "Play this card"))
        assertEquals("Tap the card again to play this card", confirmHint("Play this card"))
    }

    private companion object {
        const val MAX_DECISIONS = 2_000
    }
}
