package dev.quinton.goa.feature.board.map

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A tap on a figure's body above its hex finds the figure: a Heavy's head is over the space behind it. */
class StandingFigureTest {
    private val spaces = mapOf(Hex(0, 0) to MapPoint(10f, 10f), Hex(0, -1) to MapPoint(10f, 8.5f))

    private fun center(hex: Hex) = spaces.getValue(hex)

    private val heavy = StandingFigure(Hex(0, 0), heavy = true)
    private val melee = StandingFigure(Hex(0, 0), heavy = false)

    @Test
    fun aHeavysHeadIsItsOwnSpaceAndAMeleesIsLower() {
        val head = MapPoint(10f, 10f - 1.3f)
        assertEquals(Hex(0, 0), figureSpaceAt(head, listOf(heavy), ::center))
        assertNull(figureSpaceAt(head, listOf(melee), ::center))
        assertEquals(Hex(0, 0), figureSpaceAt(MapPoint(10f, 10f - 0.9f), listOf(melee), ::center))
    }

    @Test
    fun besideAboveOrBelowTheBodyIsNotTheFigure() {
        assertNull(figureSpaceAt(MapPoint(10.8f, 9f), listOf(heavy), ::center))
        assertNull(figureSpaceAt(MapPoint(10f, 10f - 1.5f), listOf(heavy), ::center))
        assertNull(figureSpaceAt(MapPoint(10f, 10.2f), listOf(heavy), ::center))
    }

    @Test
    fun whereTwoBodiesOverlapTheOneInFrontWins() {
        val behind = StandingFigure(Hex(0, -1), heavy = true)
        val point = MapPoint(10f, 9f)
        assertEquals(Hex(0, 0), figureSpaceAt(point, listOf(behind, heavy), ::center))
        assertEquals(Hex(0, 0), figureSpaceAt(point, listOf(heavy, behind), ::center))
    }

    @Test
    fun onlyMinionsOnTheMapAreFigures() {
        val on = MinionPiece("a", Hex(1, 1), Team.RED, MinionType.HEAVY)
        val waiting = MinionPiece("b", null, Team.BLUE, MinionType.MELEE)
        assertEquals(listOf(StandingFigure(Hex(1, 1), heavy = true)), standingFigures(listOf(on, waiting)))
    }
}
