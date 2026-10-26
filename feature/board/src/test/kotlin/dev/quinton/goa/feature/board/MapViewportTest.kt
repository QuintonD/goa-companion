package dev.quinton.goa.feature.board

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
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

    /**
     * A Move 3 question at the table (hands-on 2026-10-26, `51-move.png`): the round at a glance and
     * the turn track over the top, the panel over the bottom half, the map buttons at the end side.
     * Every space the hero can move to comes into view, between them, hexes still tappable.
     */
    @Test
    fun aQuestionsOptionsComeIntoViewBetweenTheOverlays() = runBlocking(FrameClock) {
        val view = MapViewport().apply {
            updateInsets(bottom = 397 * px, end = 0f, top = 147 * px)
            setUp(layout, Size(411 * px, 754 * px), layout.laneRegion(map, geometry), true, 38 * px, 120 * px)
        }
        val middle = layout.laneRegion(map, geometry)!!.center
        val centre = map.spaces.map { it.hex }.minBy { (layout.center(it) - middle).getDistance() }
        val options = map.spaces.map { it.hex }.filter { HexGrid.distance(it, centre) <= 3 }
        val region = layout.bounds(options, pad = 1.1f)!!
        val buttons = 64 * px
        view.reveal(region, region.center, shrink = 0.8f, clearEnd = buttons)
        val visible = view.visible
        val half = 0.5f * view.scale
        options.forEach { hex ->
            val at = view.mapToScreen(layout.center(hex))
            assertTrue(
                "option $hex at $at outside $visible",
                at.y - half >= visible.top && at.y + half <= visible.bottom && at.x - half >= visible.left,
            )
        }
        // Hexes stay tappable: about 30 dp across the flats at the least.
        assertTrue("hexes ${view.scale * sqrt(3f) / px} dp", view.scale * sqrt(3f) >= 30 * px)
    }

    @Test
    fun aFewOptionsKeepClearOfTheMapButtons() = runBlocking(FrameClock) {
        val view = opened()
        val buttons = 64 * px
        // Two spaces at the end side of the board, as an initiative tie's two heroes can be.
        val right = map.spaces.map { it.hex }.sortedByDescending { layout.center(it).x }.take(2)
        val region = layout.bounds(right, pad = 1.1f)!!
        view.reveal(region, region.center, shrink = 0.8f, clearEnd = buttons)
        right.forEach { hex ->
            val at = view.mapToScreen(layout.center(hex))
            assertTrue("$hex at $at under the buttons", at.x + 0.5f * view.scale <= view.visible.right - buttons)
        }
    }

    @Test
    fun theSandboxRevealIsUnchangedWithoutShrinkOrButtons() = runBlocking(FrameClock) {
        val view = opened()
        val region = layout.bounds(map.spaces.map { it.hex }.take(1), pad = 1.1f)!!
        view.reveal(region, region.center)
        // Never smaller than comfortable.
        assertTrue(view.scale * sqrt(3f) >= 38 * px - 0.5f)
    }

    /** Runs the reveal's animation to its end at once. */
    private object FrameClock : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += FRAME_NANOS
            return onFrame(nanos)
        }
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
        const val TOOLS_DP = 96f
    }
}
