package dev.quinton.goa.tools.catalogimport

import com.sksamuel.scrimage.ImmutableImage
import java.io.File
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.graphics.PDXObject
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject

/*
 * Official graphics the app's look is built from (ADR-042), beyond card scans and portraits:
 * the hero deck symbols, the item icons printed at the foot of every card, and the board's
 * symbols from the rulebook (Spawn points, the Tie Breaker coin). Every file comes from a pinned
 * source and is listed in `official/SOURCES.md`.
 */

/** Output folders the UI-asset stage writes (stale files in them are deleted like card images). */
val UI_ASSET_DIRS = listOf("official/icons", "official/board")

/** Long edge of a hero deck symbol (source 250-290 px; shown at up to ~120 dp). */
const val SYMBOL_MAX_EDGE = 256

/** Item icons are 240 × 180 in source 1: kept at that size. */
const val ITEM_ICON_MAX_EDGE = 240

/** Rulebook graphics are small (62-87 px): never upscaled. */
const val RULEBOOK_IMAGE_MAX_EDGE = 128

/**
 * The item icons printed at the bottom of every Action card (the stat a card gives when it
 * becomes an item, rulebook p.16), as published by source 1 (`src/lib/images/item_<name>.png`).
 * Checked by eye against the card scans' item slot (e.g. `arien-red-2a`: hourglass = Initiative).
 * Output name → source file name.
 */
val ITEM_ICONS: Map<String, String> = linkedMapOf(
    "attack" to "item_attack.png",
    "defense" to "item_defense.png",
    "initiative" to "item_initiative.png",
    "movement" to "item_movement.png",
    "range" to "item_range.png",
    "radius" to "item_area.png",
)

fun itemIconPath(stat: String): String = "official/icons/item-$stat.webp"

fun symbolPath(heroId: String): String = "official/heroes/$heroId/symbol.webp"

/**
 * One raster image embedded in the pinned rulebook PDF, addressed by its PDF object number on a
 * PDF page (PDF page N+1 = printed page N). [width]×[height] are the image's own pixels: a
 * re-exported PDF that renumbers or replaces the image fails the extraction instead of shipping
 * the wrong picture (the document is pinned by SHA-256 as well).
 */
data class RulebookImage(
    val out: String,
    val pdfPage: Int,
    val obj: Int,
    val width: Int,
    val height: Int,
    val what: String,
) {
    val printedPage: Int get() = pdfPage - 1
}

/**
 * The rulebook graphics the app uses. Printed page 6 ("Place Hero Miniatures", "Place Minion
 * Miniatures") shows the hero Spawn points (Red's gear, Blue's star) and the six minion Spawn
 * point symbols, in the order of its captions: Ranged, Melee, Heavy; Blue's icy row above Red's
 * copper row. Printed page 5 ("Place the Tie Breaker Coin") shows both faces of the coin.
 */
val RULEBOOK_IMAGES: List<RulebookImage> = listOf(
    RulebookImage("official/board/spawn-hero-red.webp", 7, 1216, 62, 62, "Red hero Spawn point (gear), p.6"),
    RulebookImage("official/board/spawn-hero-blue.webp", 7, 1239, 64, 63, "Blue hero Spawn point (star), p.6"),
    RulebookImage("official/board/spawn-ranged-blue.webp", 7, 1319, 87, 87, "Blue ranged minion Spawn point, p.6"),
    RulebookImage("official/board/spawn-melee-blue.webp", 7, 1315, 87, 87, "Blue melee minion Spawn point, p.6"),
    RulebookImage("official/board/spawn-heavy-blue.webp", 7, 1323, 87, 87, "Blue heavy minion Spawn point, p.6"),
    RulebookImage("official/board/spawn-ranged-red.webp", 7, 1335, 87, 87, "Red ranged minion Spawn point, p.6"),
    RulebookImage("official/board/spawn-melee-red.webp", 7, 1327, 87, 87, "Red melee minion Spawn point, p.6"),
    RulebookImage("official/board/spawn-heavy-red.webp", 7, 1331, 87, 87, "Red heavy minion Spawn point, p.6"),
    RulebookImage("official/board/tie-breaker-red.webp", 6, 608, 76, 76, "Tie Breaker coin, Red side (gear), p.5"),
    RulebookImage("official/board/tie-breaker-blue.webp", 6, 602, 76, 76, "Tie Breaker coin, Blue side (star), p.5"),
)

/** Extracts [RULEBOOK_IMAGES] from the pinned PDF as PNG (with their transparency) into [outDir]. */
object RulebookImages {
    fun extract(pdf: File, outDir: File, images: List<RulebookImage> = RULEBOOK_IMAGES): Map<RulebookImage, File> {
        outDir.mkdirs()
        return Loader.loadPDF(pdf).use { doc ->
            images.associateWith { spec ->
                val target = File(outDir, "p${spec.pdfPage}-${spec.obj}.png")
                if (!target.isFile) {
                    val image = findImage(doc.getPage(spec.pdfPage - 1).resources, spec.obj, depth = 0)
                        ?: error("Rulebook image ${spec.obj} not on PDF page ${spec.pdfPage} (${spec.what})")
                    check(image.width == spec.width && image.height == spec.height) {
                        "Rulebook image ${spec.obj} is ${image.width}×${image.height}, expected " +
                            "${spec.width}×${spec.height} (${spec.what})"
                    }
                    ImageIO.write(image.image, "png", target)
                }
                target
            }
        }
    }

    /** The image XObject numbered [obj] used on the page, looking inside form XObjects too. */
    private fun findImage(resources: PDResources?, obj: Int, depth: Int): PDImageXObject? {
        if (resources == null || depth > MAX_FORM_DEPTH) return null
        for (name: COSName in resources.xObjectNames) {
            val ref = resources.cosObject.getCOSDictionary(COSName.XOBJECT)?.getItem(name)
            val number = (ref as? COSObject)?.key?.number
            val x: PDXObject = resources.getXObject(name) ?: continue
            if (x is PDImageXObject && number == obj.toLong()) return x
            if (x is PDFormXObject) findImage(x.resources, obj, depth + 1)?.let { return it }
        }
        return null
    }

    private const val MAX_FORM_DEPTH = 6
}

/**
 * Source 1 publishes each hero's logo (`src/lib/images/logos/<id>.png`). The printed deck symbol
 * is on every card back (source 2's last scan per hero) and the hero dashboard (rulebook p.6).
 * A logo is accepted as the hero's deck symbol only when, among all heroes' card backs, its own
 * is the closest and clearly so ([MIN_MARGIN]).
 *
 * Both sides are cropped to the symbol (the logo's opaque pixels less its dark outline, the card
 * back's pixels that aren't the pale parchment) and compared on a [GRID]×[GRID] grid: brightness
 * standardised (shape) plus each channel's difference from grey (palette).
 */
object DeckSymbols {
    data class Check(val heroId: String, val nearest: String, val margin: Double) {
        val pass: Boolean get() = nearest == heroId && margin >= MIN_MARGIN
    }

    const val MIN_MARGIN = 1.3

    fun check(logos: Map<String, ImmutableImage>, backs: Map<String, ImmutableImage>): List<Check> {
        val backFeatures = backs.mapValues { backFeature(it.value) }
        return logos.map { (hero, logo) ->
            val f = logoFeature(logo)
            val ranked = backFeatures.map { (other, b) -> other to f.distance(b) }.sortedBy { it.second }
            val best = ranked.first()
            val second = ranked.getOrNull(1)?.second ?: Double.MAX_VALUE
            Check(hero, best.first, if (best.second <= 1e-9) Double.MAX_VALUE else second / best.second)
        }
    }

    /** The logo on parchment, cropped to its opaque pixels less the dark outline around them. */
    fun logoFeature(logo: ImmutableImage): Feature {
        val rgba = Rgba.of(logo)
        val mask = erode(BooleanArray(rgba.size) { rgba.a[it] > ALPHA_SOLID }, rgba.w, rgba.h, LOGO_OUTLINE)
        val box = Box.of(mask, rgba.w) ?: error("Logo has no opaque pixels")
        for (i in 0 until rgba.size) {
            if (!mask[i]) {
                rgba.r[i] = PARCHMENT[0]
                rgba.g[i] = PARCHMENT[1]
                rgba.b[i] = PARCHMENT[2]
            }
        }
        return feature(rgba, box)
    }

    /** The symbol on a card back, cropped to the pixels that aren't the pale parchment. */
    fun backFeature(back: ImmutableImage): Feature {
        val x0 = (BACK_BOX[0] * back.width).toInt()
        val y0 = (BACK_BOX[1] * back.height).toInt()
        val x1 = (BACK_BOX[2] * back.width).toInt()
        val y1 = (BACK_BOX[3] * back.height).toInt()
        val rgba = Rgba.of(back.subimage(x0, y0, x1 - x0, y1 - y0))
        val ink = BooleanArray(rgba.size) {
            val hi = maxOf(rgba.r[it], rgba.g[it], rgba.b[it])
            val lo = minOf(rgba.r[it], rgba.g[it], rgba.b[it])
            !(lo > PARCHMENT_MIN && hi - lo < PARCHMENT_SPREAD)
        }
        val box = Box.of(erode(ink, rgba.w, rgba.h, BACK_SPECKS), rgba.w) ?: Box(0, 0, rgba.w, rgba.h)
        return feature(rgba, box)
    }

    private fun feature(img: Rgba, box: Box): Feature {
        val lum = FloatArray(GRID * GRID)
        val chroma = FloatArray(GRID * GRID * CHANNELS)
        for (cell in 0 until GRID * GRID) {
            val gx = cell % GRID
            val gy = cell / GRID
            val ya = box.y0 + (box.y1 - box.y0) * gy / GRID
            val yb = maxOf(box.y0 + (box.y1 - box.y0) * (gy + 1) / GRID, ya + 1)
            val xa = box.x0 + (box.x1 - box.x0) * gx / GRID
            val xb = maxOf(box.x0 + (box.x1 - box.x0) * (gx + 1) / GRID, xa + 1)
            val sum = DoubleArray(CHANNELS)
            var n = 0
            for (y in ya until yb) {
                for (x in xa until xb) {
                    val i = y * img.w + x
                    sum[0] += img.r[i]
                    sum[1] += img.g[i]
                    sum[2] += img.b[i]
                    n++
                }
            }
            val l = sum.sum() / (CHANNELS * n)
            lum[cell] = l.toFloat()
            for (c in 0 until CHANNELS) chroma[cell * CHANNELS + c] = ((sum[c] / n - l) / CHROMA_SCALE).toFloat()
        }
        return Feature(Feature(lum).standardized().values + chroma)
    }

    /** Shrinks a mask by [steps] pixels (4-neighbourhood; the image edge counts as outside). */
    private fun erode(mask: BooleanArray, w: Int, h: Int, steps: Int): BooleanArray {
        var m = mask
        repeat(steps) {
            val prev = m
            m = BooleanArray(prev.size) { i ->
                val x = i % w
                val y = i / w
                prev[i] && x > 0 && x < w - 1 && y > 0 && y < h - 1 &&
                    prev[i - 1] && prev[i + 1] && prev[i - w] && prev[i + w]
            }
        }
        return m
    }

    private class Rgba(val w: Int, val h: Int) {
        val size = w * h
        val r = IntArray(size)
        val g = IntArray(size)
        val b = IntArray(size)
        val a = IntArray(size)

        companion object {
            fun of(image: ImmutableImage): Rgba = Rgba(image.width, image.height).apply {
                image.pixels().forEach { p ->
                    val i = p.y * w + p.x
                    r[i] = p.red()
                    g[i] = p.green()
                    b[i] = p.blue()
                    a[i] = p.alpha()
                }
            }
        }
    }

    /** Pixel box, end-exclusive. */
    private data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        companion object {
            fun of(mask: BooleanArray, w: Int): Box? {
                var x0 = Int.MAX_VALUE
                var y0 = Int.MAX_VALUE
                var x1 = -1
                var y1 = -1
                mask.forEachIndexed { i, on ->
                    if (on) {
                        x0 = minOf(x0, i % w)
                        y0 = minOf(y0, i / w)
                        x1 = maxOf(x1, i % w)
                        y1 = maxOf(y1, i / w)
                    }
                }
                return if (x1 < 0) null else Box(x0, y0, x1 + 1, y1 + 1)
            }
        }
    }

    private const val GRID = 12
    private const val CHANNELS = 3

    /** Part of a card back that holds the symbol (between the top and bottom ornaments). */
    private val BACK_BOX = floatArrayOf(0.08f, 0.16f, 0.92f, 0.84f)
    private val PARCHMENT = intArrayOf(235, 233, 230)
    private const val ALPHA_SOLID = 128
    private const val LOGO_OUTLINE = 5
    private const val BACK_SPECKS = 2
    private const val PARCHMENT_MIN = 150
    private const val PARCHMENT_SPREAD = 28
    private const val CHROMA_SCALE = 40.0
}
