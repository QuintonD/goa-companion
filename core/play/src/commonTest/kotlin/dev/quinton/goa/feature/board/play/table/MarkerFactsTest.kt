package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.MarkerView
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.board.readRepoFile
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.play.game.mergedView
import dev.quinton.goa.feature.board.play.review.reviewHero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Snorri's runes and the other markers in the table's words, on a real game of the shipped catalog. */
class MarkerFactsTest {
    private fun asset(path: String) = readRepoFile("app/src/main/assets/$path")

    private val catalog = CatalogFiles.load(::asset)
    private val maps: List<BoardMap> =
        CatalogFiles.parseMapIndex(asset(CatalogFiles.MAPS_INDEX_PATH)).map {
            CatalogFiles.parseMap(it, asset(CatalogFiles.mapPath(it)))
        }
    private val cards = catalog.heroes.flatMap { it.allCards }.associateBy { it.id }

    /** Axe, Bird, Anvil, Horn below Turn slots 1 to 4. */
    private val runes = listOf("axe", "bird", "anvil", "horn").mapIndexed { i, rune ->
        MarkerView("marker_rune_$rune", "snorri", i + 1)
    }

    private fun table(
        turn: Int = 2,
        phase: GamePhase = GamePhase.PLAY_CARDS,
        markers: List<MarkerView> = runes,
        ultimate: Boolean = false,
    ): TableUi {
        val game = GameEngine(catalog, maps).newGame(
            GameSetup(
                mapId = "forgotten-island",
                seats = listOf(
                    SeatSetup("snorri", Team.RED, Controller.Human),
                    SeatSetup("arien", Team.BLUE, Controller.Ai(AiLevel.EASY)),
                ),
                tieBreaker = Team.RED,
            ),
        )
        val view = game.mergedView(setOf("snorri"))
        val heroes = view.heroes.map {
            if (it.heroId == "snorri") it.copy(markers = markers, ultimate = ultimate) else it
        }
        return TableUi(
            map = maps.first { it.id == "forgotten-island" },
            view = view.copy(turn = turn, phase = phase, heroes = heroes),
            heroes = catalog.boardHeroes(),
            cards = cards,
            mine = setOf("snorri"),
        )
    }

    private fun TableUi.snorri(): HeroView = heroView("snorri")!!

    @Test
    fun everyMarkerTheEngineGivesHasWordsAndABundledToken() {
        val names = mapOf(
            "marker_rune_axe" to "Axe rune",
            "marker_rune_bird" to "Bird rune",
            "marker_rune_anvil" to "Anvil rune",
            "marker_rune_horn" to "Horn rune",
            "marker_poison" to "Poison marker",
            "marker_bounty" to "Bounty marker",
        )
        names.forEach { (kind, name) ->
            assertEquals(name, markerName(kind))
            assertTrue(asset(markerArt(kind)).isNotEmpty(), markerArt(kind))
        }
    }

    @Test
    fun theRuneBelowTheCurrentTurnsSlotIsActiveAndTheDashboardShowsEachBelowItsSlot() {
        val table = table(turn = 2)
        val slots = dashboardSlots(table, table.snorri())
        assertEquals(
            listOf(
                SlotMarker("marker_rune_axe", false),
                SlotMarker("marker_rune_bird", true),
                SlotMarker("marker_rune_anvil", false),
                SlotMarker("marker_rune_horn", false),
            ),
            slots.map { it.marker },
        )
        assertEquals("Bird rune, active", spokenSlotMarker(slots[1].marker!!))
    }

    @Test
    fun theSummarySaysWhatIsActiveAndWhatComesNext() {
        val table = table(turn = 2)
        val summary = assertNotNull(slotMarkers(table, table.snorri()))
        assertEquals("marker_rune_bird", summary.activeKind)
        assertEquals("Bird rune active this turn · Next turn: Anvil rune", summary.line)
        assertEquals(1, summary.notes.size)
    }

    @Test
    fun afterTurnFourTheNextRuneIsTheNextRoundsFirst() {
        val table = table(turn = 4)
        assertEquals("Next round, Turn 1: Axe rune", slotMarkers(table, table.snorri())?.next)
    }

    @Test
    fun betweenTurnsNoRuneIsActive() {
        val table = table(turn = 4, phase = GamePhase.END_OF_ROUND)
        val summary = assertNotNull(slotMarkers(table, table.snorri()))
        assertNull(summary.activeKind)
        assertEquals("No rune active between turns", summary.headline)
        assertEquals("Next round, Turn 1: Axe rune", summary.next)
        assertTrue(dashboardSlots(table, table.snorri()).none { it.marker?.active == true })
    }

    @Test
    fun withTheUltimateTheSummaryNamesRuneMastery() {
        val table = table(ultimate = true)
        assertTrue(slotMarkers(table, table.snorri())!!.notes.first().startsWith("Rune Mastery:"))
    }

    @Test
    fun aHeroWithoutSlotMarkersHasNoSummaryAndNoMarkerOnItsSlots() {
        val table = table(markers = listOf(MarkerView("marker_poison", "tigerclaw")))
        assertNull(slotMarkers(table, table.snorri()))
        assertTrue(dashboardSlots(table, table.snorri()).all { it.marker == null })
    }

    @Test
    fun aCardTellsWhetherItHasALineForTheActiveRune() {
        val table = table(turn = 2)
        val snorri = table.snorri()
        // Runecaster prints Horn and Axe lines; Bird is active. Rune Sigils prints all four.
        assertEquals(
            "Bird rune active: this card has no line for it.",
            runeNote(table, snorri, cards.getValue("snorri-red-2b"))?.text,
        )
        assertEquals(
            "Bird rune active: this card has a line for it.",
            runeNote(table, snorri, cards.getValue("snorri-gold"))?.text,
        )
        // Inscribe the Runes prints the marker tokens, not a rune line.
        assertNull(runeNote(table, snorri, cards.getValue("snorri-silver")))
    }

    @Test
    fun beforeInscribingACardsRuneLinesDoNothingAndRuneMasteryIsNamed() {
        val none = table(markers = emptyList())
        assertEquals(
            "No rune is active yet: this card's rune lines do nothing.",
            runeNote(none, none.snorri(), cards.getValue("snorri-gold"))?.text,
        )
        val mastery = table(turn = 2, ultimate = true)
        assertEquals(
            "Bird rune active: this card has no line for it. Rune Mastery can count another rune.",
            runeNote(mastery, mastery.snorri(), cards.getValue("snorri-red-2b"))?.text,
        )
    }

    @Test
    fun theReviewSaysTheMarkersInWords() {
        val table = table(turn = 2)
        assertEquals(
            listOf(
                "Axe rune · below Turn 1",
                "Bird rune · below Turn 2 · active",
                "Anvil rune · below Turn 3",
                "Horn rune · below Turn 4",
            ),
            reviewHero(table, table.snorri(), puzzle = false).markers,
        )
        val poisoned = table(markers = listOf(MarkerView("marker_poison", "arien")))
        assertEquals(
            listOf("Poison marker · from Arien"),
            reviewHero(poisoned, poisoned.snorri(), puzzle = false).markers,
        )
    }
}
