package dev.quinton.goa.tools.catalogimport

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer

/*
 * The game's own look (ADR-053): the parts of the rulebook the app's frames are made from. Everything
 * here is derived from the pinned PDF by page and object, so the set can be rebuilt and checked.
 * Beyond crops, three things are changed on purpose, each recorded in official/SOURCES.md: the banner
 * is composed of two page regions without a page title, the miniatures are toned as the page prints
 * them (the PDF holds them grey and colours them with a blend), and pieces of the card frame are masked.
 */

/** Output folders of this stage (stale files in them are deleted, see [UI_ASSET_DIRS]). */
val LOOK_ASSET_DIRS = listOf("official/ui", "official/pieces", "official/rules")

/** Long edge of the look's larger pieces: a phone's width at 3×. */
const val LOOK_MAX_EDGE = 1600

/** One finished image and where it came from. [source] keeps the page and object for SOURCES.md. */
data class LookAsset(val source: RulebookImage, val file: File, val maxEdge: Int)

/** How a part is cleaned up after extraction. */
private enum class Cut {
    /** As printed. */
    AS_IS,

    /** Without the transparent margins of the card layer it is printed on. */
    TRIM,

    /** The title plate: the pill and its gear, without the dark frame behind them. */
    TITLE_PLATE,

    /** A corner of the dark card plate: only its teal bracket. */
    BRACKET_TL,
    BRACKET_TR,
    BRACKET_BL,
    BRACKET_BR,

    /** A grey miniature, cut out and toned as the page prints that team. */
    MINI_RED,
    MINI_BLUE,
}

private data class Part(val image: RulebookImage, val cut: Cut, val maxEdge: Int = RULEBOOK_IMAGE_MAX_EDGE)

private fun ui(out: String, page: Int, obj: Int, w: Int, h: Int, what: String) =
    RulebookImage("official/ui/$out.webp", page, obj, w, h, what)

private fun piece(out: String, obj: Int, side: Int, what: String) =
    RulebookImage("official/pieces/$out.webp", MINI_PAGE, obj, side, side, what)

private const val MINI_PAGE = 7
private const val CARD_LAYERS = "Action card anatomy layers"

/** The rulebook parts, by PDF page and image object. Printed page = PDF page - 1. */
private val PARTS: List<Part> = listOf(
    Part(ui("slate", 17, 6747, 840, 421, "The dashboard's dark plate, p.16"), Cut.AS_IS, LOOK_MAX_EDGE),
    Part(ui("corner-tl", 22, 7728, 376, 278, "Top left bracket of the dark card plate, p.21"), Cut.BRACKET_TL),
    Part(ui("corner-tr", 22, 7728, 376, 278, "Top right bracket of the dark card plate, p.21"), Cut.BRACKET_TR),
    Part(ui("corner-bl", 22, 7728, 376, 278, "Bottom left bracket of the dark card plate, p.21"), Cut.BRACKET_BL),
    Part(ui("corner-br", 22, 7728, 376, 278, "Bottom right bracket of the dark card plate, p.21"), Cut.BRACKET_BR),
    Part(ui("title-plate", 7, 1257, 369, 515, "$CARD_LAYERS: the title plate, p.6"), Cut.TITLE_PLATE, LOOK_MAX_EDGE),
    Part(ui("textbox-green", 7, 1260, 416, 561, "$CARD_LAYERS: green text box, p.6"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("textbox-gold", 7, 1299, 402, 543, "$CARD_LAYERS: gold text box, p.6"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("textbox-blue", 8, 2100, 318, 428, "$CARD_LAYERS: blue text box, p.7"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("textbox-red", 10, 2900, 278, 375, "$CARD_LAYERS: red text box, p.9"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("textbox-silver", 13, 4359, 286, 384, "$CARD_LAYERS: silver text box, p.12"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("ribbon-green", 7, 1262, 416, 561, "$CARD_LAYERS: green ribbon, p.6"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("ribbon-gold", 8, 2069, 300, 404, "$CARD_LAYERS: gold ribbon, p.7"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("ribbon-blue", 8, 2102, 318, 428, "$CARD_LAYERS: blue ribbon, p.7"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("ribbon-red", 10, 2902, 278, 375, "$CARD_LAYERS: red ribbon, p.9"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("ribbon-silver", 13, 4363, 286, 384, "$CARD_LAYERS: silver ribbon, p.12"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(ui("life-red", 6, 580, 79, 75, "Red Life counter, unspent, p.5"), Cut.TRIM),
    Part(ui("life-blue", 6, 582, 79, 75, "Blue Life counter, unspent, p.5"), Cut.TRIM),
    Part(ui("life-red-spent", 6, 614, 79, 75, "Red Life counter, spent, p.5"), Cut.TRIM),
    Part(ui("life-blue-spent", 6, 616, 79, 75, "Blue Life counter, spent, p.5"), Cut.TRIM),
    Part(ui("wave", 6, 604, 69, 69, "Wave counter, faceup, p.5"), Cut.TRIM),
    Part(ui("wave-spent", 6, 578, 69, 69, "Wave counter, facedown, p.5"), Cut.TRIM),
    Part(ui("action-attack", 7, 1305, 80, 80, "Attack action symbol, p.6"), Cut.TRIM),
    Part(ui("action-skill", 7, 1266, 82, 82, "Skill action symbol, p.6"), Cut.TRIM),
    Part(ui("action-range", 7, 1268, 82, 82, "Range symbol, p.6"), Cut.TRIM),
    Part(ui("action-defense", 7, 1270, 65, 65, "Defense action symbol, p.6"), Cut.TRIM),
    Part(ui("action-movement", 7, 1272, 65, 65, "Movement action symbol, p.6"), Cut.TRIM),
    Part(ui("dashboard-frame", 17, 6750, 821, 416, "The hero dashboard's frame, p.16"), Cut.AS_IS, LOOK_MAX_EDGE),
    Part(ui("dashboard-tigerclaw", 17, 6748, 821, 416, "Tigerclaw's dashboard art, p.16"), Cut.AS_IS, LOOK_MAX_EDGE),
    Part(ui("silhouette", 1, 8940, 830, 883, "The cover's battlefield silhouette"), Cut.TRIM, LOOK_MAX_EDGE),
    Part(piece("mini-ranged-blue", 8449, 133, "Blue ranged minion miniature, toned as printed, p.6"), Cut.MINI_BLUE),
    Part(piece("mini-melee-blue", 8453, 139, "Blue melee minion miniature, toned as printed, p.6"), Cut.MINI_BLUE),
    Part(piece("mini-heavy-blue", 8457, 147, "Blue heavy minion miniature, toned as printed, p.6"), Cut.MINI_BLUE),
    Part(piece("mini-melee-red", 8463, 123, "Red melee minion miniature, toned as printed, p.6"), Cut.MINI_RED),
    Part(piece("mini-heavy-red", 8464, 151, "Red heavy minion miniature, toned as printed, p.6"), Cut.MINI_RED),
    Part(piece("mini-ranged-red", 8467, 135, "Red ranged minion miniature, toned as printed, p.6"), Cut.MINI_RED),
)

object LookAssets {
    /** Builds every part into [dir] (kept between runs: the PDF is pinned) and returns the finished list. */
    fun build(pdf: File, dir: File, seaArt: File?): List<LookAsset> {
        dir.mkdirs()
        val raw = RulebookImages.extract(pdf, File(dir, "raw"), PARTS.map { it.image }.distinctBy { it.obj })
            .entries.associate { it.key.obj to it.value }
        val parts = PARTS.map { part ->
            val name = part.image.out.substringAfterLast('/').removeSuffix(".webp")
            val out = File(dir, "$name.png")
            if (!out.isFile) ImageIO.write(cut(ImageIO.read(raw.getValue(part.image.obj)), part.cut), "png", out)
            LookAsset(part.image, out, part.maxEdge)
        }
        val rendered = Loader.loadPDF(pdf).use { renderedParts(it, dir) }
        val sea = seaArt?.let { seaTexture(it, dir) }
        return parts + rendered + listOfNotNull(sea)
    }

    private fun cut(image: BufferedImage, cut: Cut): BufferedImage = when (cut) {
        Cut.AS_IS -> image
        Cut.TRIM -> trim(image)
        Cut.TITLE_PLATE -> titlePlate(image)
        Cut.BRACKET_TL -> bracket(image, left = true, top = true)
        Cut.BRACKET_TR -> bracket(image, left = false, top = true)
        Cut.BRACKET_BL -> bracket(image, left = true, top = false)
        Cut.BRACKET_BR -> bracket(image, left = false, top = false)
        Cut.MINI_RED -> miniature(image, RED_TONE)
        Cut.MINI_BLUE -> miniature(image, BLUE_TONE)
    }

    private fun trim(image: BufferedImage): BufferedImage {
        var x0 = image.width
        var y0 = image.height
        var x1 = -1
        var y1 = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr ALPHA_SHIFT > TRIM_ALPHA) {
                    x0 = min(x0, x)
                    y0 = min(y0, y)
                    x1 = max(x1, x)
                    y1 = max(y1, y)
                }
            }
        }
        check(x1 >= 0) { "Nothing to trim to" }
        return copy(image, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    private fun copy(image: BufferedImage, x: Int, y: Int, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(image, -x, -y, null)
        g.dispose()
        return out
    }

    /** The pill and its gear: the card layer has the frame's dark steel behind them. */
    private fun titlePlate(layer: BufferedImage): BufferedImage {
        val plate = copy(layer, PLATE_X, PLATE_Y, PLATE_W, PLATE_H)
        val mask = BufferedImage(plate.width, plate.height, BufferedImage.TYPE_INT_ARGB)
        val g = mask.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        val inset = PLATE_INSET.toDouble()
        val arc = (plate.height - 2 * inset)
        g.fill(
            RoundRectangle2D.Double(
                inset,
                inset,
                plate.width - PLATE_GEAR_ROOM - inset,
                plate.height - 2 * inset,
                arc,
                arc,
            ),
        )
        g.fill(
            Ellipse2D.Double(
                plate.width - PLATE_GEAR.toDouble() - 1,
                1.0,
                PLATE_GEAR.toDouble(),
                plate.height - 2.0,
            ),
        )
        g.dispose()
        return mapPixels(plate) { x, y, argb ->
            val a = (argb ushr ALPHA_SHIFT) * (mask.getRGB(x, y) ushr ALPHA_SHIFT) / FULL
            (a shl ALPHA_SHIFT) or (argb and RGB_MASK)
        }
    }

    /** One corner's teal bracket: the plate's dark ground goes transparent. */
    private fun bracket(plate: BufferedImage, left: Boolean, top: Boolean): BufferedImage {
        val solid = trim(plate)
        val x = if (left) 0 else solid.width - BRACKET
        val y = if (top) BRACKET_TOP else solid.height - BRACKET
        return mapPixels(copy(solid, x, y, BRACKET, BRACKET)) { _, _, argb ->
            val r = (argb shr RED_SHIFT) and FULL
            val b = argb and FULL
            val teal = ((b - r - BRACKET_FLOOR) / BRACKET_RAMP).coerceIn(0f, 1f)
            val a = ((argb ushr ALPHA_SHIFT) * teal).roundToInt()
            (a shl ALPHA_SHIFT) or (argb and RGB_MASK)
        }
    }

    /**
     * The PDF holds a miniature in grey on white and colours it with a blend when the page is drawn:
     * the white goes, the tones stretch to the figure's own range and map shadow, mid, highlight.
     */
    private fun miniature(image: BufferedImage, tone: Tone): BufferedImage {
        val values = ArrayList<Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val p = image.getRGB(x, y)
                if (p ushr ALPHA_SHIFT > TRIM_ALPHA && luma(p) < MINI_WHITE) values += luma(p)
            }
        }
        values.sort()
        val lo = values[(values.size * MINI_LOW).toInt()].toFloat()
        val hi = values[(values.size * MINI_HIGH).toInt()].toFloat()
        val toned = mapPixels(image) { _, _, p ->
            val v = luma(p).toFloat()
            val g = ((v - lo) / (hi - lo)).coerceIn(0f, 1f).pow(MINI_GAMMA)
            val keyed = ((MINI_KEY - v) / MINI_KEY_RAMP).coerceIn(0f, 1f)
            val a = ((p ushr ALPHA_SHIFT) * keyed).roundToInt()
            (a shl ALPHA_SHIFT) or tone.map(g)
        }
        return trim(toned)
    }

    private fun renderedParts(doc: PDDocument, dir: File): List<LookAsset> {
        val renderer = PDFRenderer(doc)
        fun page(index: Int) = renderer.renderImage(index, RENDER_SCALE, ImageType.RGB)
        val p8 = page(PUSH_PAGE)
        val banner = keep(dir, "banner") { banner(p8) }
        val k = p8.width / PAGE_REFERENCE_WIDTH
        fun crop(name: String, x0: Int, x1: Int) = keep(dir, name) {
            copy(p8, (x0 * k).toInt(), (DIAGRAM_Y0 * k).toInt(), ((x1 - x0) * k).toInt(), (DIAGRAM_H * k).toInt())
        }
        val coin = keep(dir, "coin") { coin(page(COIN_PAGE)) }
        return listOf(
            LookAsset(
                RulebookImage(
                    "official/ui/banner.webp",
                    PUSH_PAGE + 1,
                    0,
                    0,
                    0,
                    "The page banner, composed from a page footer's band and a header's medallion, p.8",
                ),
                banner,
                LOOK_MAX_EDGE,
            ),
            LookAsset(
                RulebookImage(
                    "official/ui/coin.webp",
                    COIN_PAGE + 1,
                    0,
                    0,
                    0,
                    "The bronze coin, cut from the page's coin picture, p.5",
                ),
                coin,
                RULEBOOK_IMAGE_MAX_EDGE,
            ),
            LookAsset(
                RulebookImage(
                    "official/rules/push-1.webp",
                    PUSH_PAGE + 1,
                    0,
                    0,
                    0,
                    "Push the Lane diagram 1, a region of the page, p.8",
                ),
                crop("push-1", DIAGRAM_1_X0, DIAGRAM_1_X1),
                LOOK_MAX_EDGE,
            ),
            LookAsset(
                RulebookImage(
                    "official/rules/push-2.webp",
                    PUSH_PAGE + 1,
                    0,
                    0,
                    0,
                    "Push the Lane diagram 2, a region of the page, p.8",
                ),
                crop("push-2", DIAGRAM_2_X0, DIAGRAM_2_X1),
                LOOK_MAX_EDGE,
            ),
        )
    }

    private fun keep(dir: File, name: String, make: () -> BufferedImage): File {
        val out = File(dir, "$name.png")
        if (!out.isFile) ImageIO.write(make(), "png", out)
        return out
    }

    /**
     * The page's banner without a title: the band from the footer, which has none, and the star
     * medallion from the header; the white page around the medallion's rounded corner goes.
     */
    private fun banner(page: BufferedImage): BufferedImage {
        val k = page.width / PAGE_REFERENCE_WIDTH
        val header = band(copy(page, 0, (HEADER_Y0 * k).toInt(), page.width, ((HEADER_Y1 - HEADER_Y0) * k).toInt()))
        val footer = band(copy(page, 0, (FOOTER_Y0 * k).toInt(), page.width, ((FOOTER_Y1 - FOOTER_Y0) * k).toInt()))
        val h = header.height
        val capW = (h * CAP_RATIO).toInt()
        val width = (h * BANNER_ASPECT).toInt()
        val right = (CAP_RIGHT * k).toInt()
        val out = BufferedImage(width, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val bodyEnd = width - capW + CAP_BLEND
        g.drawImage(footer, 0, 0, bodyEnd, h, page.width / BODY_START, 0, page.width / BODY_START + bodyEnd, footer.height, null)
        g.dispose()
        val cap = copy(header, right - capW, 0, capW, h)
        for (y in 0 until h) {
            for (x in 0 until capW) {
                val p = cap.getRGB(x, y)
                val low = min(min((p shr RED_SHIFT) and FULL, (p shr GREEN_SHIFT) and FULL), p and FULL)
                val keyed = ((CAP_KEY - low) / CAP_KEY_RAMP).coerceIn(0f, 1f)
                val blend = (x.toFloat() / CAP_BLEND).coerceIn(0f, 1f)
                val a = keyed * blend
                val under = out.getRGB(width - capW + x, y)
                out.setRGB(width - capW + x, y, over(p, under, a))
            }
        }
        return out
    }

    /** Source-over of opaque [top] at [a] on [under], which may be transparent. */
    private fun over(top: Int, under: Int, a: Float): Int {
        val ua = (under ushr ALPHA_SHIFT) / FULL.toFloat()
        val oa = a + ua * (1 - a)
        if (oa <= 0f) return 0
        fun ch(shift: Int): Int {
            val t = (top shr shift) and FULL
            val u = (under shr shift) and FULL
            return ((t * a + u * ua * (1 - a)) / oa).roundToInt().coerceIn(0, FULL)
        }
        return ((oa * FULL).roundToInt() shl ALPHA_SHIFT) or (ch(RED_SHIFT) shl RED_SHIFT) or
            (ch(GREEN_SHIFT) shl GREEN_SHIFT) or ch(0)
    }

    /** The rows of a region that are banner rather than white page. */
    private fun band(region: BufferedImage): BufferedImage {
        val rows = (0 until region.height).filter { y ->
            val xs = region.width / BAND_FROM until region.width / BAND_TO
            xs.map { luma(region.getRGB(it, y)) }.average() < BAND_LUMA
        }
        return copy(region, 0, rows.first(), region.width, rows.last() - rows.first() + 1)
    }

    private fun coin(page: BufferedImage): BufferedImage {
        val r = COIN_R
        val out = BufferedImage(2 * r, 2 * r, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.clip = Ellipse2D.Double(0.0, 0.0, 2.0 * r, 2.0 * r)
        g.drawImage(page, r - COIN_X, r - COIN_Y, null)
        g.dispose()
        return out
    }

    private fun seaTexture(art: File, dir: File): LookAsset {
        val out = File(dir, "sea.png")
        if (!out.isFile) {
            val image = ImageIo.load(art).awt()
            val scaled = BufferedImage(SEA_W, SEA_H, BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(image, 0, 0, SEA_W, SEA_H, SEA_X0, SEA_Y0, SEA_X1, SEA_Y1, null)
            g.dispose()
            ImageIO.write(scaled, "png", out)
        }
        return LookAsset(
            RulebookImage(
                "official/ui/sea.webp",
                0,
                0,
                0,
                0,
                "Open sea, a region of the Forgotten Island painting (see the map art below)",
            ),
            out,
            SEA_H,
        )
    }

    private fun mapPixels(image: BufferedImage, f: (Int, Int, Int) -> Int): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) out.setRGB(x, y, f(x, y, image.getRGB(x, y)))
        }
        return out
    }

    private fun luma(argb: Int): Int =
        (
            ((argb shr RED_SHIFT) and FULL) * LUMA_R + ((argb shr GREEN_SHIFT) and FULL) * LUMA_G +
                (argb and FULL) * LUMA_B
            ) / LUMA_SUM

    private class Tone(val shadow: IntArray, val mid: IntArray, val high: IntArray) {
        fun map(g: Float): Int {
            val (a, b, t) = if (g < HALF) Triple(shadow, mid, g / HALF) else Triple(mid, high, (g - HALF) / HALF)
            fun ch(i: Int) = (a[i] + (b[i] - a[i]) * t).roundToInt().coerceIn(0, FULL)
            return (ch(0) shl RED_SHIFT) or (ch(1) shl GREEN_SHIFT) or ch(2)
        }
    }

    private val RED_TONE = Tone(intArrayOf(80, 18, 10), intArrayOf(222, 76, 52), intArrayOf(255, 222, 205))
    private val BLUE_TONE = Tone(intArrayOf(8, 50, 86), intArrayOf(40, 150, 200), intArrayOf(214, 240, 250))

    private const val HALF = 0.5f
    private const val FULL = 255
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val RGB_MASK = 0xFFFFFF
    private const val LUMA_R = 299
    private const val LUMA_G = 587
    private const val LUMA_B = 114
    private const val LUMA_SUM = 1000
    private const val TRIM_ALPHA = 8

    private const val PLATE_X = 73
    private const val PLATE_Y = 16
    private const val PLATE_W = 274
    private const val PLATE_H = 43
    private const val PLATE_INSET = 3
    private const val PLATE_GEAR_ROOM = 26
    private const val PLATE_GEAR = 40

    private const val BRACKET = 30
    private const val BRACKET_TOP = 6
    private const val BRACKET_FLOOR = 12f
    private const val BRACKET_RAMP = 18f

    private const val MINI_WHITE = 236
    private const val MINI_LOW = 0.03
    private const val MINI_HIGH = 0.97
    private const val MINI_GAMMA = 1.25f
    private const val MINI_KEY = 244f
    private const val MINI_KEY_RAMP = 10f

    private const val RENDER_SCALE = 6f
    private const val PUSH_PAGE = 8
    private const val COIN_PAGE = 5
    private const val PAGE_REFERENCE_WIDTH = 745f
    private const val HEADER_Y0 = 27
    private const val HEADER_Y1 = 67
    private const val FOOTER_Y0 = 989
    private const val FOOTER_Y1 = 1032
    private const val BAND_FROM = 5
    private const val BAND_TO = 2
    private const val BAND_LUMA = 205.0
    private const val CAP_RATIO = 1.35
    private const val CAP_RIGHT = 712
    private const val CAP_BLEND = 50
    private const val CAP_KEY = 236f
    private const val CAP_KEY_RAMP = 18f
    private const val BODY_START = 6
    private const val BANNER_ASPECT = 411.0 / 52.0
    private const val DIAGRAM_Y0 = 650
    private const val DIAGRAM_H = 235
    private const val DIAGRAM_1_X0 = 45
    private const val DIAGRAM_1_X1 = 350
    private const val DIAGRAM_2_X0 = 382
    private const val DIAGRAM_2_X1 = 682
    private const val COIN_X = 1381
    private const val COIN_Y = 4382
    private const val COIN_R = 108
    private const val SEA_W = 411
    private const val SEA_H = 891
    private const val SEA_X0 = 1320
    private const val SEA_Y0 = 120
    private const val SEA_X1 = 1400
    private const val SEA_Y1 = 400
}
