package dev.quinton.goa.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.theme.DarkColors
import dev.quinton.goa.core.designsystem.theme.DarkExtended
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import dev.quinton.goa.core.testing.bundledAsset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ContrastTest checks tokens; these check text against the art it actually sits on (ADR-053): the
 * slate, the parchment of the title plate and text boxes. The art is sampled at its worst: the lightest
 * pixels under light text, the darkest under dark text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LookArtContrastTest {
    private fun ratio(a: Float, b: Float): Double = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

    /** The luminances of an image's opaque pixels in the fractional box, sorted. */
    private fun luminances(path: String, box: List<Float> = listOf(0f, 0f, 1f, 1f)): List<Float> {
        val bitmap = BitmapFactory.decodeFile(bundledAsset(path).path)
        val out = ArrayList<Float>()
        val xs = (bitmap.width * box[0]).toInt() until (bitmap.width * box[2]).toInt()
        val ys = (bitmap.height * box[1]).toInt() until (bitmap.height * box[3]).toInt()
        for (y in ys) {
            for (x in xs) {
                val argb = bitmap.getPixel(x, y)
                if (argb ushr ALPHA_SHIFT > OPAQUE) out += Color(argb).luminance()
            }
        }
        return out.sorted()
    }

    private fun check(name: String, ink: Color, against: Float, min: Double) {
        val r = ratio(ink.luminance(), against)
        assertWithMessage("$name: ${"%.2f".format(r)}").that(r).isAtLeast(min)
    }

    /** The slate as a plate draws it: 18 % black over the texture. */
    private val slateWorst: Float
        get() {
            val l = luminances(LookArt.SLATE)
            return l[(l.size * WORST_LIGHT).toInt()] * PLATE_SHADE
        }

    @Test fun textOnTheSlate() {
        val x = DarkExtended
        val s = DarkColors
        val against = slateWorst
        check("onSurface", s.onSurface, against, TEXT)
        check("onSurfaceVariant", s.onSurfaceVariant, against, TEXT)
        check("primary", s.primary, against, TEXT)
        check("secondary", s.secondary, against, TEXT)
        check("error", s.error, against, TEXT)
        check("heading", x.heading, against, TEXT)
        check("team red", x.teamRed, against, TEXT)
        check("team blue", x.teamBlue, against, TEXT)
        check("verified", x.verified, against, TEXT)
        check("unverified", x.unverified, against, TEXT)
    }

    @Test fun playerTonesOnTheSlate() {
        val against = slateWorst
        PlayerColors.all.forEach { check("player ${it.name}", it.dark, against, TEXT) }
    }

    /** The middle of the pill, where the words go. */
    @Test fun wordsOnTheTitlePlate() {
        val l = luminances(LookArt.TITLE_PLATE, listOf(0.1f, 0.3f, 0.82f, 0.7f))
        check("onParchment on the plate", DarkExtended.onParchment, l[(l.size * WORST_DARK).toInt()], TEXT)
    }

    /** The parchment of each text box, under its tab and inside its rim. */
    @Test fun wordsInTheTextBoxes() = PlateColor.entries.forEach { color ->
        val l = luminances(LookArt.textBox(color), listOf(0.1f, 0.45f, 0.9f, 0.8f))
        val worst = l[(l.size * WORST_DARK).toInt()]
        check("onParchment in ${color.name}", DarkExtended.onParchment, worst, TEXT)
        check("onParchmentVariant in ${color.name}", DarkExtended.onParchmentVariant, worst, TEXT)
        check("parchmentInk in ${color.name}", DarkExtended.parchmentInk, worst, GRAPHIC)
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0
        const val ALPHA_SHIFT = 24
        const val OPAQUE = 200

        /** Ignoring the odd speck: the worst 1 % of the art. */
        const val WORST_LIGHT = 0.99
        const val WORST_DARK = 0.01
        const val PLATE_SHADE = 0.82f
    }
}
