package dev.quinton.goa.tools.catalogimport

import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
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
 * Beyond crops, two things are changed on purpose, each recorded in official/SOURCES.md: the miniatures are toned
 * as the page prints them (the PDF holds them grey and colours them with a blend), and the parts the PDF prints too
 * small for a phone are redrawn after it (design/look/render_parts.py, ADR-058).
 */

/**
 * What the cuts below make: part of the cached PNGs' folder and of each image's manifest key, because neither
 * can see this code. Bump it whenever a cut changes.
 */
const val LOOK_VERSION = 3

/** Output folders of this stage (stale files in them are deleted, see [UI_ASSET_DIRS]). */
val LOOK_ASSET_DIRS = listOf("official/ui", "official/pieces", "official/rules")

/** Long edge of the look's larger pieces: a phone's width at 3×. */
const val LOOK_MAX_EDGE = 1600

/**
 * One finished image and where it came from. [source] keeps the page and object for SOURCES.md; [origin] names
 * what it is made from, for its manifest key, when that isn't the rulebook.
 */
data class LookAsset(val source: RulebookImage, val file: File, val maxEdge: Int, val origin: String? = null)

/** The map art the sea texture is cut from. */
data class SeaArt(val file: File, val url: String)

/** How a part is cleaned up after extraction. */
private enum class Cut {
    /** As printed. */
    AS_IS,

    /** Without the transparent margins of the card layer it is printed on. */
    TRIM,

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
    Part(ui("dashboard-tigerclaw", 17, 6748, 821, 416, "Tigerclaw's dashboard art, p.16"), Cut.AS_IS, LOOK_MAX_EDGE),
    Part(piece("mini-ranged-blue", 8449, 133, "Blue ranged minion miniature, toned as printed, p.6"), Cut.MINI_BLUE),
    Part(piece("mini-melee-blue", 8453, 139, "Blue melee minion miniature, toned as printed, p.6"), Cut.MINI_BLUE),
    Part(piece("mini-heavy-blue", 8457, 147, "Blue heavy minion miniature, toned as printed, p.6"), Cut.MINI_BLUE),
    Part(piece("mini-melee-red", 8463, 123, "Red melee minion miniature, toned as printed, p.6"), Cut.MINI_RED),
    Part(piece("mini-heavy-red", 8464, 151, "Red heavy minion miniature, toned as printed, p.6"), Cut.MINI_RED),
    Part(piece("mini-ranged-red", 8467, 135, "Red ranged minion miniature, toned as printed, p.6"), Cut.MINI_RED),
)

/** Marks a part as redrawn in [RulebookImage.obj]: its page is the one it's drawn after, it has no PDF object. */
const val REDRAWN_OBJ = -1

/**
 * The parts the PDF prints too small for a phone (its banner is 827 x 45), kept as masters in design/look/parts
 * (ADR-058): redrawn after the page by design/look/render_parts.py, or upscaled where it's illustrated, as
 * design/README.md describes. Name to page and what it is.
 */
private val REDRAWN = listOf(
    Triple("banner", 2, "The page banner with its star medallion, redrawn after the page's"),
    Triple("title-plate", 7, "The cards' title plate and gear, redrawn after $CARD_LAYERS, p.6"),
    Triple("textbox-green", 7, "The cards' green text box, redrawn after $CARD_LAYERS, p.6"),
    Triple("textbox-gold", 7, "The cards' gold text box, redrawn after $CARD_LAYERS, p.6"),
    Triple("textbox-blue", 8, "The cards' blue text box, redrawn after $CARD_LAYERS, p.7"),
    Triple("textbox-red", 10, "The cards' red text box, redrawn after $CARD_LAYERS, p.9"),
    Triple("textbox-silver", 13, "The cards' silver text box, redrawn after $CARD_LAYERS, p.12"),
    Triple("corner-tl", 22, "Top left bracket of the dark plate, redrawn after p.21"),
    Triple("corner-tr", 22, "Top right bracket of the dark plate, redrawn after p.21"),
    Triple("corner-bl", 22, "Bottom left bracket of the dark plate, redrawn after p.21"),
    Triple("corner-br", 22, "Bottom right bracket of the dark plate, redrawn after p.21"),
    Triple("dashboard-frame", 17, "The hero dashboard's frame, object 6750, upscaled with Real-ESRGAN x4plus, p.16"),
    Triple("silhouette", 1, "The cover's battlefield silhouette, object 8940, upscaled with Real-ESRGAN x4plus"),
)

object LookAssets {
    /**
     * Builds every part into [dir] (kept between runs: the PDF is pinned, [LOOK_VERSION] names the code) and takes the
     * redrawn ones from [masters].
     */
    fun build(pdf: File, dir: File, seaArt: SeaArt?, masters: File): List<LookAsset> {
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
        return parts + rendered + listOfNotNull(sea) + redrawn(masters)
    }

    /** Each master at its own size: it's already what a phone needs. Its hash is in its manifest key. */
    private fun redrawn(masters: File): List<LookAsset> = REDRAWN.map { (name, page, what) ->
        val file = File(masters, "$name.png")
        check(file.isFile) { "No master for $name: run design/look/render_parts.py" }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val image = ImageIO.read(file)
        LookAsset(
            RulebookImage("official/ui/$name.webp", page, REDRAWN_OBJ, image.width, image.height, what),
            file,
            max(image.width, image.height),
            origin = "design/look@$hash",
        )
    }

    private fun cut(image: BufferedImage, cut: Cut): BufferedImage = when (cut) {
        Cut.AS_IS -> image
        Cut.TRIM -> trim(image)
        Cut.MINI_RED -> miniature(image, RED_TONE)
        Cut.MINI_BLUE -> miniature(image, BLUE_TONE)
    }

    internal fun trim(image: BufferedImage): BufferedImage {
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
        val k = p8.width / PAGE_REFERENCE_WIDTH
        fun crop(name: String, x0: Int, x1: Int) = keep(dir, name) {
            copy(p8, (x0 * k).toInt(), (DIAGRAM_Y0 * k).toInt(), ((x1 - x0) * k).toInt(), (DIAGRAM_H * k).toInt())
        }
        val coin = keep(dir, "coin") { coin(page(COIN_PAGE)) }
        return listOf(
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

    /** Made afresh each run: a quick crop of the map art, whose URL its manifest key names. */
    private fun seaTexture(art: SeaArt, dir: File): LookAsset {
        val out = File(dir, "sea.png")
        val image = ImageIo.load(art.file).awt()
        val scaled = BufferedImage(SEA_W, SEA_H, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(image, 0, 0, SEA_W, SEA_H, SEA_X0, SEA_Y0, SEA_X1, SEA_Y1, null)
        g.dispose()
        ImageIO.write(scaled, "png", out)
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
            origin = art.url,
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
    private const val LUMA_R = 299
    private const val LUMA_G = 587
    private const val LUMA_B = 114
    private const val LUMA_SUM = 1000
    private const val TRIM_ALPHA = 8

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
