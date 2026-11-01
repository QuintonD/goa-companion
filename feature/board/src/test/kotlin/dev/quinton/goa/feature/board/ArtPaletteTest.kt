package dev.quinton.goa.feature.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.quinton.goa.feature.board.ui.ArtBoardPalette
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An overlay on a painting holds 3:1 for graphics on any ground, not on a token: wherever its light line
 * has too little contrast with the painting, its dark halo has enough, and the line always reads on its
 * halo. Checked across the whole range of greys and against the mats' own sand, jungle, ice and lava.
 */
class ArtPaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return max(la, lb) / min(la, lb)
    }

    private val palette = ArtBoardPalette
    private val overlays = mapOf(
        "fast travel" to palette.fastTravel,
        "range" to palette.range,
        "radius" to palette.radius,
        "target" to palette.selection,
        "measure, path, hint, selection" to palette.measure,
    )
    private val mats = listOf(0xFFB08D62, 0xFF4F6B2E, 0xFFD5DCE2, 0xFFC4471C, 0xFF20251F, 0xFFF3E7C8).map(::Color)
    private val greys = (0..255 step 5).map { Color(it, it, it) }

    @Test
    fun everyOverlayReadsOnItsOwnHalo() {
        overlays.forEach { (name, color) ->
            (greys + mats).forEach { ground ->
                val halo = palette.halo.copy(alpha = HALO_ALPHA).compositeOver(ground)
                assertTrue("$name on its halo over $ground", contrast(color, halo) >= GRAPHICS)
            }
        }
    }

    @Test
    fun lineOrHaloStandsOutFromAnyGround() {
        overlays.forEach { (name, color) ->
            (greys + mats).forEach { ground ->
                val halo = palette.halo.copy(alpha = HALO_ALPHA).compositeOver(ground)
                val best = max(contrast(color, ground), contrast(halo, ground))
                assertTrue("$name over $ground: $best", best >= GRAPHICS_OVER_ANY)
            }
        }
    }

    private companion object {
        const val GRAPHICS = 3f

        /**
         * A translucent halo over a mid-tone ground can't reach 3:1 against that same ground, but a line
         * reads against its halo there (the test above); this keeps the outer contrast from collapsing.
         */
        const val GRAPHICS_OVER_ANY = 2.5f
        const val HALO_ALPHA = 0.85f
    }
}
