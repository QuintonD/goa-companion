package dev.quinton.goa.feature.board

import dev.quinton.goa.feature.board.setup.heroGridColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** New board's hero grid fits every hero without scrolling (PRODUCT §8), tiles as big as that allows. */
class HeroGridFitTest {
    private fun columns(count: Int, height: Float, extra: Float = 0f) = heroGridColumns(
        count = count,
        width = 352f,
        height = height,
        label = 18f,
        spacing = 6f,
        extra = extra,
        minTile = 40f,
        maxTile = 64f,
    )

    @Test
    fun fewHeroesKeepTheBiggestTiles() {
        assertEquals(5, columns(count = 7, height = 360f))
    }

    @Test
    fun allThirtyTwoHeroesFitAPhoneWithMoreColumns() {
        // A 384 dp phone between the teams and Set up: 7 columns of 45 dp tiles, 5 rows.
        val c = columns(count = 32, height = 340f)
        assertEquals(7, c)
        val tile = (352f - (c - 1) * 6f) / c
        val rows = (32 + c - 1) / c
        assertTrue(rows * (tile + 18f) + (rows - 1) * 6f <= 340f)
    }

    @Test
    fun theShowAllRowCountsToo() {
        // 22 owned heroes and "Show all heroes" below them.
        assertEquals(6, columns(count = 22, height = 360f, extra = 54f))
        assertEquals(7, columns(count = 22, height = 330f, extra = 54f))
    }

    @Test
    fun withNoRoomTheSmallestTilesScrollTheLeast() {
        assertEquals(7, columns(count = 32, height = 120f))
    }
}
