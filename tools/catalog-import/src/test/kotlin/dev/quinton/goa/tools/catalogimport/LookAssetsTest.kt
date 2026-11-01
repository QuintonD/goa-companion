package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import java.awt.image.BufferedImage
import org.junit.Assert.assertThrows
import org.junit.Test

class LookAssetsTest {
    private val clear = 0
    private val red = 0xFFFF0000.toInt()
    private val teal = 0xFF0080C0.toInt()
    private val dark = 0xFF101010.toInt()

    private fun image(w: Int, h: Int, pixel: (Int, Int) -> Int) = BufferedImage(
        w,
        h,
        BufferedImage.TYPE_INT_ARGB,
    ).apply {
        for (y in 0 until h) for (x in 0 until w) setRGB(x, y, pixel(x, y))
    }

    @Test
    fun `trim takes the transparent margins off`() {
        val cut = LookAssets.trim(image(10, 8) { x, y -> if (x in 2..5 && y in 3..4) red else clear })
        assertThat(cut.width).isEqualTo(4)
        assertThat(cut.height).isEqualTo(2)
        assertThat(cut.getRGB(0, 0)).isEqualTo(red)
    }

    @Test
    fun `trim of an empty layer is an error, not an empty asset`() {
        assertThrows(IllegalStateException::class.java) { LookAssets.trim(image(4, 4) { _, _ -> clear }) }
    }

    @Test
    fun `a bracket keeps the teal and drops the plate's dark ground`() {
        val side = LookAssets.BRACKET * 2 + LookAssets.BRACKET_TOP
        val plate = image(side, side) { x, y -> if (x < 4 || y < LookAssets.BRACKET_TOP + 4) teal else dark }
        val bracket = LookAssets.bracket(plate, left = true, top = true)
        assertThat(bracket.width).isEqualTo(LookAssets.BRACKET)
        assertThat(bracket.getRGB(1, 10) ushr 24).isEqualTo(255)
        assertThat(bracket.getRGB(20, 20) ushr 24).isEqualTo(0)
    }

    @Test
    fun `over blends onto a transparent ground without darkening`() {
        assertThat(LookAssets.over(red, 0, 1f)).isEqualTo(red)
        val half = LookAssets.over(red, 0, 0.5f)
        assertThat(half ushr 24).isIn(127..128)
        assertThat((half shr 16) and 0xFF).isEqualTo(255)
    }
}
