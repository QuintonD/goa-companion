package dev.quinton.goa.feature.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The board's view on a 384 dp phone (an S25 Ultra at 450 dpi): the opening shows the whole Lane,
 * both teams' heroes included, and closing the selection panel never leaves a band of empty water.
 */
class MapViewportTest {
    private val px = 450f / 160f
    private val map = bundledMaps.first()
    private val geometry = BoardGeometry.of(map)
    private val layout = MapLayout(map, MapOrientation.PORTRAIT, Team.RED)

    /** The map under the top bar, with the Add / Push the lane / Pieces tools over its bottom. */
    private fun opened(): MapViewport = MapViewport().apply {
        updateInsets(bottom = TOOLS_DP * px, end = 0f)
        setUp(layout, Size(384 * px, 726 * px), layout.laneRegion(map, geometry), true, 38 * px, 120 * px)
    }

    @Test
    fun theOpeningShowsEveryHeroSpawnPointWithTappableHexes() {
        val view = opened()
        val visible = view.visible
        val half = 0.73f * view.scale // half a hero piece
        Team.entries.flatMap(geometry::heroSpawns).forEach { hex ->
            val at = view.mapToScreen(layout.center(hex))
            assertTrue(
                "hero spawn $hex at $at outside $visible",
                at.y - half >= visible.top && at.y + half <= visible.bottom,
            )
        }
        // Hexes stay big enough to tap: at least 32 dp across the flats.
        assertTrue(view.scale * sqrt(3f) >= 32 * px)
    }

    @Test
    fun closingThePanelFillsTheSpaceItLeaves() {
        val view = opened()
        // A hero near the bottom selected: the panel covers half the screen and the map moves up.
        view.updateInsets(bottom = 400 * px, end = 0f)
        view.panBy(Offset(0f, -5000f))
        // Deselected: the panel goes, the map reaches down to the tools again.
        view.updateInsets(bottom = TOOLS_DP * px, end = 0f)
        val mapBottom = view.pan.y + view.content.height * view.zoom
        assertTrue("map ends at $mapBottom above ${view.visible.bottom}", mapBottom >= view.visible.bottom - 1f)
    }

    private companion object {
        const val TOOLS_DP = 96f
    }
}
