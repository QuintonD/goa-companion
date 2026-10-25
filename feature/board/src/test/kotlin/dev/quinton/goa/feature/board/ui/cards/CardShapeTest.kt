package dev.quinton.goa.feature.board.ui.cards

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.feature.board.bundledAssetFile
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.play.table.CARD_ASPECT
import java.io.File
import kotlin.math.abs
import org.junit.Test

/**
 * The table draws a card at the shape of its scan (CardShape.kt): a landscape scan squeezed into a
 * portrait frame shrank Gydion's spells to a strip with bands above and below. The catalog has no
 * shape field, so the rule (spell cards are landscape, every other card portrait) is checked here
 * against every bundled image: a new hero with landscape cards fails until the rule knows it.
 */
class CardShapeTest {
    @Test
    fun `every bundled card scan has the shape the table draws it at`() {
        val cards = bundledCatalog.heroes.flatMap { it.allCards }.filter { it.image != null }
        assertThat(cards.size).isAtLeast(600)
        for (card in cards) {
            val (w, h) = webpSize(bundledAssetFile(card.image!!))
            val aspect = w.toFloat() / h
            assertWithMessage("${card.id} is $w × $h").that(abs(aspect - cardAspect(card))).isLessThan(0.02f)
        }
    }

    @Test
    fun `Gydion's 22 spell cards are the landscape ones`() {
        val gydion = bundledCatalog.heroes.first { it.id == "gydion" }
        assertThat(gydion.extras).hasSize(22)
        assertThat(gydion.extras.all { it.isSpell }).isTrue()
        assertThat(bundledCatalog.heroes.flatMap { it.cards + listOfNotNull(it.handicap) }.none { it.isSpell }).isTrue()
        assertThat(cardAspect(gydion.extras.first())).isEqualTo(LANDSCAPE_CARD_ASPECT)
        assertThat(cardAspect(gydion.cards.first())).isEqualTo(CARD_ASPECT)
        assertThat(cardAspect(null)).isEqualTo(CARD_ASPECT)
    }

    /** A WebP's pixel size from its header (VP8, VP8L or VP8X chunk). */
    private fun webpSize(file: File): Pair<Int, Int> {
        val b = file.inputStream().use { it.readNBytes(HEADER) }
        fun u8(i: Int) = b[i].toInt() and 0xFF
        fun u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
        fun u24(i: Int) = u16(i) or (u8(i + 2) shl 16)
        check(String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WEBP") { "${file.name} is not a WebP" }
        return when (String(b, 12, 4)) {
            "VP8X" -> (u24(24) + 1) to (u24(27) + 1)

            "VP8 " -> (u16(26) and 0x3FFF) to (u16(28) and 0x3FFF)

            "VP8L" -> {
                val bits = u24(21) or (u8(24) shl 24)
                ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
            }

            else -> error("${file.name}: unknown WebP chunk")
        }
    }

    private companion object {
        const val HEADER = 30
    }
}
