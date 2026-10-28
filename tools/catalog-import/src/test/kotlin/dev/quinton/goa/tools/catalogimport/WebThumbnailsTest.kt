package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

/** The web app's list thumbnails: the sizes its `srcset` promises, from the shipped official images. */
class WebThumbnailsTest {
    private val official = File("../../app/src/main/assets/official")

    @Test
    fun `cards become 384 wide upright or landscape and symbols 96 at most, each beside its original`() {
        val source = Files.createTempDirectory("thumbs-in").toFile()
        val out = Files.createTempDirectory("thumbs-out").toFile()
        try {
            listOf("cards/arien-gold.webp", "cards/gydion-spell-banishment.webp", "heroes/arien/symbol.webp")
                .forEach { official.resolve(it).copyTo(source.resolve(it)) }

            WebThumbnails.write(source, out)

            val card = ImageIo.load(out.resolve("cards/arien-gold.webp"))
            assertThat(card.width).isEqualTo(WebThumbnails.CARD_WIDTH)
            assertThat(card.height).isEqualTo(540) // 546 × 768 keeps its shape
            assertThat(ImageIo.load(out.resolve("cards/gydion-spell-banishment.webp")).width)
                .isEqualTo(WebThumbnails.CARD_WIDTH)
            val symbol = ImageIo.load(out.resolve("heroes/arien/symbol.webp"))
            assertThat(maxOf(symbol.width, symbol.height)).isEqualTo(WebThumbnails.SYMBOL_EDGE)
            assertThat(out.resolve("cards/arien-gold.webp").length())
                .isLessThan(official.resolve("cards/arien-gold.webp").length())
        } finally {
            source.deleteRecursively()
            out.deleteRecursively()
        }
    }
}
