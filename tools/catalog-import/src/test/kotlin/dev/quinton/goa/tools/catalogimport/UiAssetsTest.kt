package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import com.sksamuel.scrimage.ImmutableImage
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import org.junit.Test

class UiAssetsTest {
    /** A symbol: [draw] in a [size]² box at ([x], [y]) of [canvas]. */
    private fun image(w: Int, h: Int, background: Color?, x: Int, y: Int, size: Int, draw: (Graphics2D, Int) -> Unit) =
        ImmutableImage.fromAwt(
            BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
                val g = createGraphics()
                if (background != null) {
                    g.color = background
                    g.fillRect(0, 0, w, h)
                }
                g.translate(x, y)
                draw(g, size)
                g.dispose()
            },
        )

    private val shapes: Map<String, (Graphics2D, Int) -> Unit> = mapOf(
        "disc" to { g, s ->
            g.color = Color(40, 90, 200)
            g.fillOval(0, 0, s, s)
        },
        "bar" to { g, s ->
            g.color = Color(190, 40, 40)
            g.fillRect(s / 3, 0, s / 3, s)
        },
        "cross" to { g, s ->
            g.color = Color(60, 160, 80)
            g.stroke = BasicStroke(s / 6f)
            g.drawLine(0, 0, s, s)
            g.drawLine(s, 0, 0, s)
        },
    )

    /** The logo: the shape on transparency, with a dark rim hugging it (the logos' outline). */
    private fun logo(name: String) = image(250, 250, null, 10, 10, 230) { g, s ->
        g.translate(-3, -3)
        shapes.getValue(name)(g, s + 6)
        g.composite = java.awt.AlphaComposite.SrcAtop
        g.color = Color.BLACK
        g.fillRect(-10, -10, s + 20, s + 20)
        g.composite = java.awt.AlphaComposite.SrcOver
        g.translate(3, 3)
        shapes.getValue(name)(g, s)
    }

    /** The card back: the same shape, smaller, on parchment. */
    private fun back(name: String) = image(729, 1026, Color(236, 234, 228), 200, 330, 330) { g, s ->
        shapes.getValue(name)(g, s)
    }

    @Test
    fun `each logo matches its own card back`() {
        val checks = DeckSymbols.check(
            shapes.keys.associateWith(::logo),
            shapes.keys.associateWith(::back),
        )
        assertThat(checks.map { it.heroId to it.nearest }).containsExactly("disc" to "disc", "bar" to "bar", "cross" to "cross")
        assertThat(checks.all { it.pass }).isTrue()
    }

    @Test
    fun `a logo whose card back is missing is not accepted`() {
        val checks = DeckSymbols.check(mapOf("disc" to logo("disc")), mapOf("bar" to back("bar"), "cross" to back("cross")))
        assertThat(checks.single().pass).isFalse()
    }

    @Test
    fun `rulebook graphics are named once, on printed pages 5 and 6`() {
        assertThat(RULEBOOK_IMAGES.map { it.out }.toSet()).hasSize(RULEBOOK_IMAGES.size)
        assertThat(RULEBOOK_IMAGES.map { it.obj }.toSet()).hasSize(RULEBOOK_IMAGES.size)
        assertThat(RULEBOOK_IMAGES.map { it.printedPage }.toSet()).containsExactly(5, 6)
        assertThat(RULEBOOK_IMAGES.all { it.out.startsWith("official/board/") && it.out.endsWith(".webp") }).isTrue()
        // Every minion type has both teams' Spawn point symbol.
        listOf("ranged", "melee", "heavy").forEach { type ->
            listOf("red", "blue").forEach { team ->
                assertThat(RULEBOOK_IMAGES.map { it.out }).contains("official/board/spawn-$type-$team.webp")
            }
        }
    }

    @Test
    fun `item icons cover the six item stats`() {
        assertThat(ITEM_ICONS.keys).containsExactly("attack", "defense", "initiative", "movement", "range", "radius")
        assertThat(itemIconPath("radius")).isEqualTo("official/icons/item-radius.webp")
    }
}
