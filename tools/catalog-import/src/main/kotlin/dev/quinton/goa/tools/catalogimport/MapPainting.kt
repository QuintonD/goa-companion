package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardMapArt
import dev.quinton.goa.core.model.board.Hex
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * A map's painting as the board draws it (ADR-053): resampled from the art the map import checked
 * space by space (top-down art by similarity, a photo of the mat by homography) into a frame that
 * lines up with the hex layout, so one scale and origin place it. Whatever isn't the mat (the table
 * in a photo, past the art's edge) is filled with a dim blur of the nearest painting. Spaces the art
 * doesn't show are recorded as `uncovered`: their ground is copied from the nearest space of the same
 * Zone (the printed lattice lines up, hex onto hex), and the app draws their Spawn points and Zone edges,
 * so no space is ever missing and nothing printed there is made up.
 */
object MapPainting {
    private val SQRT3 = sqrt(3.0)

    /** Target scale, and the long edge that may lower it (a phone at 3× zooms to about 60 px per unit). */
    private const val PX_PER_UNIT = 48.0
    private const val MAX_EDGE = 2048
    private const val SUPERSAMPLE = 2

    /** Sea around the outermost hexes, in hex radii. */
    private const val MARGIN = 4.5

    /** How much of a space must show on the art: its corners pulled in toward its centre. */
    private const val COVER = 0.45

    /** A donor must show right out to its corners. */
    private const val WHOLE = 1.0
    private const val DONOR_CHOICE = 5
    private const val DONOR_Q = 3
    private const val DONOR_R = 7
    private const val FILL_DIM = 0.72f
    private const val FEATHER = 2
    private const val FILL_BAND = 24
    private const val BAND_SLACK = 0.001f
    private const val KEY_VERSION = 7
    private const val HEX_CORNERS = 6
    private const val CORNER_STEP = PI / 3
    private const val CORNER_START = -PI / 6
    private const val ROUND = 1000.0
    private const val SEA_SHADE = 0.55f
    private const val HEX_STEP_Y = 1.5

    private val json = Json { ignoreUnknownKeys = false }

    @Serializable
    private data class Meta(val key: String, val art: BoardMapArt)

    private fun plain(q: Int, r: Int) = doubleArrayOf(SQRT3 * (q + r / 2.0), HEX_STEP_Y * r)

    /**
     * Makes (or reuses) `official/maps/<id>.webp` from [source] under [assetsDir]; [work] keeps what
     * the next run may reuse. Returns the map's art record.
     */
    fun paint(map: BoardMap, source: File, spec: MapArt, assetsDir: File, work: File): BoardMapArt {
        val path = "official/maps/${map.id}.webp"
        val target = File(assetsDir, path)
        val metaFile = File(work, "${map.id}-art.json")
        val key = "v$KEY_VERSION|${spec.url}|${spec.model}|${spec.anchors}|${spec.mat}|${map.spaces.size}|q${ImageIo.QUALITY}"
        if (target.isFile && metaFile.isFile) {
            val meta = json.decodeFromString(Meta.serializer(), metaFile.readText())
            if (meta.key == key) return meta.art
        }
        val image = ImageIo.load(source).awt()
        val src = spec.anchors.map { plain(it[0].toInt(), it[1].toInt()) }
        val dst = spec.anchors.map { doubleArrayOf(it[2], it[3]) }
        val transform = MapOverlay.fitPoints(src, dst, spec.model)

        val hexes = map.spaces.map { it.hex } + map.terrain
        val centres = hexes.map { plain(it.q, it.r) }
        val x0 = centres.minOf { it[0] } - MARGIN
        val y0 = centres.minOf { it[1] } - MARGIN
        val x1 = centres.maxOf { it[0] } + MARGIN
        val y1 = centres.maxOf { it[1] } + MARGIN
        val scale = min(PX_PER_UNIT, MAX_EDGE / max(x1 - x0, y1 - y0))
        val w = ((x1 - x0) * scale).roundToInt()
        val h = ((y1 - y0) * scale).roundToInt()
        val finalScale = w / (x1 - x0)

        val mat = spec.mat.map { doubleArrayOf(it[0], it[1]) }
        fun onArt(x: Double, y: Double): Boolean {
            if (mat.isNotEmpty() && !inside(mat, x, y)) return false
            val p = transform.apply(x, y)
            return p[0] >= 1 && p[1] >= 1 && p[0] < image.width - 2 && p[1] < image.height - 2
        }

        val uncovered = hexes.filterNot { hex ->
            val c = plain(hex.q, hex.r)
            onArt(c[0], c[1]) && (0 until HEX_CORNERS).all { i ->
                val a = CORNER_START + i * CORNER_STEP
                onArt(c[0] + cos(a) * COVER, c[1] + sin(a) * COVER)
            }
        }
        fun whole(hex: Hex): Boolean {
            val c = plain(hex.q, hex.r)
            return (0 until HEX_CORNERS).all { i ->
                val a = CORNER_START + i * CORNER_STEP
                onArt(c[0] + cos(a) * WHOLE, c[1] + sin(a) * WHOLE)
            }
        }
        val patches = patches(map, uncovered.toSet(), ::whole)
        val frame = Frame(w, h, x0, y0, finalScale)
        val out = render(image, transform, frame, patches, ::onArt)
        val sea = seaColor(out)
        target.parentFile.mkdirs()
        val rendered = File(work, "${map.id}-art.png")
        rendered.parentFile.mkdirs()
        ImageIO.write(out, "png", rendered)
        ImageIo.writeWebp(rendered, target, MAX_EDGE)

        val art = BoardMapArt(
            image = path,
            width = w,
            height = h,
            pxPerUnit = round3(finalScale),
            originX = round3(x0),
            originY = round3(y0),
            sea = sea,
            uncovered = uncovered.sortedWith(compareBy<Hex> { it.r }.thenBy { it.q }),
        )
        metaFile.writeText(json.encodeToString(Meta.serializer(), Meta(key, art)))
        return art
    }

    private fun round3(v: Double) = (v * ROUND).roundToInt() / ROUND

    private data class Frame(val w: Int, val h: Int, val x0: Double, val y0: Double, val scale: Double)

    /** Each space the art doesn't show, with the shown space whose ground it takes. */
    private fun patches(map: BoardMap, uncovered: Set<Hex>, whole: (Hex) -> Boolean): List<Pair<Hex, Hex>> {
        fun distance(a: Hex, b: Hex): Double {
            val pa = plain(a.q, a.r)
            val pb = plain(b.q, b.r)
            return (pa[0] - pb[0]) * (pa[0] - pb[0]) + (pa[1] - pb[1]) * (pa[1] - pb[1])
        }
        val plainGround = map.spaces.filter { it.spawn == null && it.hex !in uncovered && whole(it.hex) }
        val rocks = map.terrain.filter { it !in uncovered && whole(it) }
        return uncovered.mapNotNull { hex ->
            val zone = map.spaces.firstOrNull { it.hex == hex }?.zone
            val candidates = if (zone != null) plainGround.filter { it.zone == zone }.map { it.hex } else rocks
            val donors = candidates.ifEmpty { plainGround.map { it.hex } }
            // One of the nearest few, varied by place, so a run of patches doesn't repeat one space.
            val near = donors.sortedBy { distance(hex, it) }.take(DONOR_CHOICE)
            near.getOrNull(((hex.q * DONOR_Q + hex.r * DONOR_R) % DONOR_CHOICE + DONOR_CHOICE) % max(near.size, 1))
                ?.let { hex to it }
        }
    }

    /** Copies a donor hex's pixels onto each patched hex, hex onto hex, so the printed lattice carries on. */
    private fun applyPatches(rgb: FloatArray, weight: FloatArray, frame: Frame, patches: List<Pair<Hex, Hex>>) {
        val (w, h, x0, y0, scale) = frame
        val source = rgb.copyOf()
        val shown = weight.copyOf()
        patches.forEach { (hex, donor) ->
            val c = plain(hex.q, hex.r)
            val d = plain(donor.q, donor.r)
            val dx = ((d[0] - c[0]) * scale).roundToInt()
            val dy = ((d[1] - c[1]) * scale).roundToInt()
            val yFrom = ((c[1] - 1 - y0) * scale).toInt().coerceAtLeast(0)
            val yTo = ((c[1] + 1 - y0) * scale).toInt().coerceAtMost(h - 1)
            val xFrom = ((c[0] - SQRT3 / 2 - x0) * scale).toInt().coerceAtLeast(0)
            val xTo = ((c[0] + SQRT3 / 2 - x0) * scale).toInt().coerceAtMost(w - 1)
            for (y in yFrom..yTo) {
                for (x in xFrom..xTo) {
                    val u = kotlin.math.abs(x0 + x / scale - c[0])
                    val v = kotlin.math.abs(y0 + y / scale - c[1])
                    if (u > SQRT3 / 2 || u / SQRT3 + v > 1) continue
                    val sx = x + dx
                    val sy = y + dy
                    if (sx !in 0 until w || sy !in 0 until h) continue
                    val at = y * w + x
                    val from = sy * w + sx
                    // What the art does show of this space stays; only the rest takes the donor's ground.
                    if (shown[at] >= 1f || shown[from] < 1f) continue
                    for (ch in 0 until CHANNELS) rgb[at * CHANNELS + ch] = source[from * CHANNELS + ch]
                    weight[at] = 1f
                }
            }
        }
    }

    private fun render(
        image: BufferedImage,
        t: MapOverlay.Transform,
        frame: Frame,
        patches: List<Pair<Hex, Hex>>,
        onArt: (Double, Double) -> Boolean,
    ): BufferedImage {
        val (w, h, x0, y0, scale) = frame
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val ss = SUPERSAMPLE
        val rgb = FloatArray(w * h * CHANNELS)
        val weight = FloatArray(w * h)
        val hits = ss * ss
        val sample = FloatArray(CHANNELS)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var count = 0
                val sum = FloatArray(CHANNELS)
                for (j in 0 until ss) {
                    for (i in 0 until ss) {
                        val px = x0 + (x + (i + HALF) / ss) / scale
                        val py = y0 + (y + (j + HALF) / ss) / scale
                        if (!onArt(px, py)) continue
                        val p = t.apply(px, py)
                        catmullRom(pixels, image.width, image.height, p[0], p[1], sample)
                        for (c in 0 until CHANNELS) sum[c] += sample[c]
                        count++
                    }
                }
                val at = y * w + x
                if (count > 0) {
                    for (c in 0 until CHANNELS) rgb[at * CHANNELS + c] = sum[c] / count
                    weight[at] = count.toFloat() / hits
                }
            }
        }
        applyPatches(rgb, weight, frame, patches)
        // Only the art's outer band feeds the fill, so past the mat it's sea rather than the board's average.
        val band = blur(weight, w, h, FILL_BAND)
        val edgeOnly = FloatArray(weight.size) { if (band[it] < 1f - BAND_SLACK) weight[it] else 0f }
        val filled = pushPull(rgb, edgeOnly, w, h)
        val soft = blur(weight, w, h, FEATHER)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val at = y * w + x
                // Only inside the art's edge: the fill never bleeds over a painted space.
                val a = if (weight[at] >= 1f) soft[at].coerceAtLeast(MIN_FEATHER) else weight[at] * soft[at]
                var v = 0
                for (c in 0 until CHANNELS) {
                    val painted = if (weight[at] > 0f) rgb[at * CHANNELS + c] else 0f
                    val fill = filled[at * CHANNELS + c] * FILL_DIM
                    val mixed = painted * a + fill * (1 - a)
                    v = (v shl BYTE) or mixed.roundToInt().coerceIn(0, MAX_BYTE)
                }
                out.setRGB(x, y, v)
            }
        }
        return out
    }

    /** Smooth fill of the holes: averages down until nothing is empty, then back up. */
    private fun pushPull(rgb: FloatArray, weight: FloatArray, w: Int, h: Int): FloatArray {
        if (w <= 1 && h <= 1) return rgb.copyOf()
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2
        val cRgb = FloatArray(cw * ch * CHANNELS)
        val cWeight = FloatArray(cw * ch)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val at = y * w + x
                val to = (y / 2) * cw + x / 2
                val a = weight[at]
                cWeight[to] += a
                for (c in 0 until CHANNELS) cRgb[to * CHANNELS + c] += rgb[at * CHANNELS + c] * a
            }
        }
        for (i in cWeight.indices) {
            val a = cWeight[i]
            if (a > 0f) {
                for (c in 0 until CHANNELS) cRgb[i * CHANNELS + c] /= a
                cWeight[i] = min(a / PUSH_COUNT, 1f)
            }
        }
        val coarse = pushPull(cRgb, cWeight, cw, ch)
        val out = FloatArray(w * h * CHANNELS)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val at = y * w + x
                val a = weight[at]
                val sx = min((x + HALF) / 2f - HALF, cw - 1f).coerceAtLeast(0f)
                val sy = min((y + HALF) / 2f - HALF, ch - 1f).coerceAtLeast(0f)
                for (c in 0 until CHANNELS) {
                    val up = bilinear(coarse, cw, ch, sx, sy, c)
                    out[at * CHANNELS + c] = rgb[at * CHANNELS + c] * a + up * (1 - a)
                }
            }
        }
        return out
    }

    private fun bilinear(v: FloatArray, w: Int, h: Int, x: Float, y: Float, c: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val fx = x - x0
        val fy = y - y0
        fun at(px: Int, py: Int) = v[(py * w + px) * CHANNELS + c]
        return (at(x0, y0) * (1 - fx) + at(x1, y0) * fx) * (1 - fy) + (at(x0, y1) * (1 - fx) + at(x1, y1) * fx) * fy
    }

    private fun blur(v: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val tmp = FloatArray(v.size)
        val out = FloatArray(v.size)
        val n = 2 * radius + 1
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                for (d in -radius..radius) s += v[y * w + (x + d).coerceIn(0, w - 1)]
                tmp[y * w + x] = s / n
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                for (d in -radius..radius) s += tmp[(y + d).coerceIn(0, h - 1) * w + x]
                out[y * w + x] = s / n
            }
        }
        return out
    }

    private fun catmullRom(px: IntArray, w: Int, h: Int, x: Double, y: Double, out: FloatArray) {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = (x - ix).toFloat()
        val fy = (y - iy).toFloat()
        out.fill(0f)
        for (j in -1..2) {
            val wy = cubic(j - fy)
            val row = (iy + j).coerceIn(0, h - 1) * w
            for (i in -1..2) {
                val p = px[row + (ix + i).coerceIn(0, w - 1)]
                val k = cubic(i - fx) * wy
                out[0] += ((p shr RED_SHIFT) and MAX_BYTE) * k
                out[1] += ((p shr BYTE) and MAX_BYTE) * k
                out[2] += (p and MAX_BYTE) * k
            }
        }
    }

    private fun cubic(t: Float): Float {
        val a = kotlin.math.abs(t)
        return when {
            a < 1f -> (CUBIC_A * a - CUBIC_B) * a * a + 1f
            a < 2f -> ((-HALF * a + CUBIC_C) * a - CUBIC_D) * a + 2f
            else -> 0f
        }
    }

    /** Ray casting; [poly] is in the plain layout's map units. */
    private fun inside(poly: List<DoubleArray>, x: Double, y: Double): Boolean {
        var hit = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) hit = !hit
            j = i
        }
        return hit
    }

    /** The canvas colour past the painting: its edge, darkened. */
    private fun seaColor(image: BufferedImage): String {
        val xs = (0 until image.width step SEA_STEP).flatMap { listOf(it to 0, it to image.height - 1) }
        val ys = (0 until image.height step SEA_STEP).flatMap { listOf(0 to it, image.width - 1 to it) }
        val all = (xs + ys).map { (x, y) -> image.getRGB(x, y) }
        fun ch(shift: Int) = (all.map { (it shr shift) and MAX_BYTE }.average() * SEA_SHADE).roundToInt()
        return "#%02X%02X%02X".format(ch(RED_SHIFT), ch(BYTE), ch(0))
    }

    private const val CHANNELS = 3
    private const val HALF = 0.5f
    private const val MIN_FEATHER = 0.5f
    private const val PUSH_COUNT = 4f
    private const val BYTE = 8
    private const val RED_SHIFT = 16
    private const val MAX_BYTE = 255
    private const val SEA_STEP = 16
    private const val CUBIC_A = 1.5f
    private const val CUBIC_B = 2.5f
    private const val CUBIC_C = 2.5f
    private const val CUBIC_D = 4f
}
