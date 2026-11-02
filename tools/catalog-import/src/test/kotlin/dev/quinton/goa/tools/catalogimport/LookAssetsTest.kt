package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertThrows
import org.junit.Test

class LookAssetsTest {
    private val clear = 0
    private val red = 0xFFFF0000.toInt()

    private fun image(w: Int, h: Int, pixel: (Int, Int) -> Int) = BufferedImage(
        w,
        h,
        BufferedImage.TYPE_INT_ARGB,
    ).apply {
        for (y in 0 until h) for (x in 0 until w) setRGB(x, y, pixel(x, y))
    }

    private fun master(name: String) = ImageIO.read(File("../../design/look/parts/$name.png"))

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

    /** LookParts.kt, HeroPlates.kt, GoaScaffold.kt and the web's border-images slice these parts by these numbers. */
    @Test
    fun `the redrawn masters keep the sizes the frames slice them by`() {
        val banner = master("banner")
        assertThat(banner.height).isEqualTo(240)
        assertThat(banner.width).isEqualTo(1896)
        val plate = master("title-plate")
        assertThat(plate.width).isEqualTo(525)
        assertThat(plate.height).isEqualTo(149)
        listOf("green", "gold", "blue", "red", "silver").forEach {
            val box = master("textbox-$it")
            assertThat(box.width).isEqualTo(600)
            assertThat(box.height).isEqualTo(240)
        }
        listOf("tl", "tr", "bl", "br").forEach { assertThat(master("corner-$it").width).isEqualTo(64) }
        // HeroState.kt places the Turn slots by fractions of the frame: its shape can't change.
        val frame = master("dashboard-frame")
        assertThat(frame.width.toDouble() / frame.height).isWithin(0.01).of(821.0 / 416)
    }

    /** A plate's gear stays round at its minimum height: the right slice and the rows scale alike at 34 dp. */
    @Test
    fun `the title plate's gear is in its right slice`() {
        val plate = master("title-plate")
        val sliceStart = plate.width - 132
        // Nothing above the pill left of the slice: the middle column, which stretches, holds no cog.
        (0 until 16).forEach { y -> assertThat(plate.getRGB(sliceStart - 2, y) ushr 24).isEqualTo(0) }
        // The top tooth overhangs the pill, as on a card.
        assertThat(plate.getRGB(plate.width - 66, 14) ushr 24).isEqualTo(255)
    }
}
