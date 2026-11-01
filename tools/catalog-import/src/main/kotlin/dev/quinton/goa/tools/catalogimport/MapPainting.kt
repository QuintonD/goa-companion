package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardMapArt
import dev.quinton.goa.core.model.board.Hex
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
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
 * in a photo, past the art's edge) is one dark sea, and the art fades into it; the image's own edge is
 * that sea, so nothing past the image can show as a line. Spaces the art doesn't show whole take the
 * missing ground from a space of the same Zone (the printed lattice lines up, hex onto hex). A Zone's
 * edge is printed on the mat as a crack, and a player reads Zones by them (p.11, p.17, p.19), so a donor
 * must have nothing but its own Zone around it: no crack or rock comes along. Spaces the art doesn't
 * show at all are recorded as `uncovered`: the app draws their Spawn points and Zone edges, so no space
 * is ever missing and nothing printed there is made up.
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

    /** A space shows whole when this far out from its centre is on the art, clear of a photo's dark rim. */
    private const val WHOLE = 1.12
    private const val DONOR_CHOICE = 5
    private const val DONOR_Q = 3
    private const val DONOR_R = 7

    /** The one sea past the art on every map, before the app's dimming; the edge of every image. */
    const val SEA = "#163F55"

    /** How far in from the image's border the picture fades into the sea, in hex radii: short of any space. */
    private const val BORDER_FADE = 3.0

    /** Pixels at the very border that are the sea and nothing else, whatever the encoder does beside them. */
    private const val SEA_RIM = 3

    /** How far the art's own edge fades into the sea, in hex radii (the blur that measures it). */
    private const val ART_FADE = 0.6

    /** A photo's rim is left out of a patched space: the blur that finds it, and where its crossfade starts. */
    private const val RIM_BLUR = 10
    private const val RIM_FROM = 0.75f

    /**
     * The picture's overall light: lit ice and sand glared, and red figures sank into the salmon lava. The
     * app's dimming (core:play's DIM_BRIGHTNESS) is set against this.
     */
    private const val LIT_SCALE = 0.92f

    /** Bump when anything here changes what is drawn: the key below names only the inputs. */
    private const val KEY_VERSION = 8
    private const val HEX_CORNERS = 6
    private const val CORNER_STEP = PI / 3
    private const val CORNER_START = -PI / 6
    private const val ROUND = 1000.0
    private const val HEX_STEP_Y = 1.5

    private val json = Json { ignoreUnknownKeys = false }

    @Serializable
    private data class Meta(val key: String, val art: BoardMapArt, val weak: List<Hex> = emptyList())

    /** A map's art record, and the spaces whose ground had no clean donor ([patches]): for the report. */
    data class Painted(val art: BoardMapArt, val weakDonors: List<Hex>)

    private fun plain(q: Int, r: Int) = doubleArrayOf(SQRT3 * (q + r / 2.0), HEX_STEP_Y * r)

    /**
     * Makes (or reuses) `official/maps/<id>.webp` from [source] under [assetsDir]; [work] keeps what
     * the next run may reuse.
     */
    fun paint(map: BoardMap, source: File, spec: MapArt, assetsDir: File, work: File): Painted {
        val path = "official/maps/${map.id}.webp"
        val target = File(assetsDir, path)
        val metaFile = File(work, "${map.id}-art.json")
        val key = "v$KEY_VERSION|${spec.url}|${spec.model}|${spec.anchors}|${spec.mat}|${map.spaces}|" +
            "q${ImageIo.QUALITY}"
        if (target.isFile && metaFile.isFile) {
            val meta = json.decodeFromString(Meta.serializer(), metaFile.readText())
            if (meta.key == key) return Painted(meta.art, meta.weak)
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
        // Every space the art doesn't show whole: one that only just counts as shown still has a corner off the mat.
        val missing = uncovered.toSet() + map.spaces.map { it.hex }.filterNot(::whole)
        val patches = patches(map, missing, ::whole)
        val frame = Frame(w, h, x0, y0, finalScale)
        val out = render(image, transform, frame, map.spaces.map { it.hex } + map.terrain, patches.pairs, ::onArt)
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
            sea = SEA,
            uncovered = uncovered.sortedWith(compareBy<Hex> { it.r }.thenBy { it.q }),
        )
        metaFile.writeText(json.encodeToString(Meta.serializer(), Meta(key, art, patches.weak)))
        return Painted(art, patches.weak)
    }

    private fun round3(v: Double) = (v * ROUND).roundToInt() / ROUND

    private data class Frame(val w: Int, val h: Int, val x0: Double, val y0: Double, val scale: Double)

    /** Each hex to patch with the shown hex whose ground it takes; [weak]: spaces that had no clean donor. */
    internal data class Patches(val pairs: List<Pair<Hex, Hex>>, val weak: List<Hex>)

    /**
     * A space's ground may be copied only from a space with nothing but its own Zone around it: all six
     * neighbours are spaces of that Zone, so neither a printed crack nor a rock is on its edge.
     */
    internal fun cleanDonor(map: BoardMap, hex: Hex): Boolean {
        val spaces = map.spaces.associateBy { it.hex }
        val space = spaces[hex] ?: return false
        return space.spawn == null && hex !in map.terrain && NEIGHBOURS.all { (dq, dr) ->
            val neighbour = Hex(hex.q + dq, hex.r + dr)
            neighbour !in map.terrain && spaces[neighbour]?.zone == space.zone
        }
    }

    /**
     * Each hex of [missing] with the hex whose ground it takes: a clean space of its Zone, varied by place so a
     * run of patches doesn't repeat one. A Zone with none takes the clean space nearest in any Zone, and is
     * reported: its ground may be the wrong kind, but it brings no false Zone edge. A rock takes a rock.
     */
    internal fun patches(map: BoardMap, missing: Set<Hex>, whole: (Hex) -> Boolean): Patches {
        fun distance(a: Hex, b: Hex): Double {
            val pa = plain(a.q, a.r)
            val pb = plain(b.q, b.r)
            return (pa[0] - pb[0]) * (pa[0] - pb[0]) + (pa[1] - pb[1]) * (pa[1] - pb[1])
        }
        val clean = map.spaces.filter { it.hex !in missing && whole(it.hex) && cleanDonor(map, it.hex) }
        val rocks = map.terrain.filter { it !in missing && whole(it) }
        val weak = ArrayList<Hex>()
        val pairs = missing.mapNotNull { hex ->
            val zone = map.spaces.firstOrNull { it.hex == hex }?.zone
            val own = if (zone != null) clean.filter { it.zone == zone }.map { it.hex } else rocks
            if (own.isEmpty() && zone != null) weak += hex
            val donors = own.ifEmpty { clean.map { it.hex } }
            val near = donors.sortedBy { distance(hex, it) }.take(DONOR_CHOICE)
            near.getOrNull(((hex.q * DONOR_Q + hex.r * DONOR_R) % DONOR_CHOICE + DONOR_CHOICE) % max(near.size, 1))
                ?.let { hex to it }
        }
        return Patches(pairs, weak.sortedWith(compareBy<Hex> { it.r }.thenBy { it.q }))
    }

    /**
     * Copies a donor hex's pixels onto each patched hex, hex onto hex, so the printed lattice carries on. What
     * the art shows of the space stays, up to a crossfade short of the art's edge: a photo's rim there is dark.
     */
    private fun applyPatches(rgb: FloatArray, weight: FloatArray, frame: Frame, patches: List<Pair<Hex, Hex>>) {
        val w = frame.w
        val h = frame.h
        val scale = frame.scale
        val source = rgb.copyOf()
        val shown = weight.copyOf()
        val inside = blur(shown, w, h, RIM_BLUR)
        patches.forEach { (hex, donor) ->
            val c = plain(hex.q, hex.r)
            val d = plain(donor.q, donor.r)
            val dx = ((d[0] - c[0]) * scale).roundToInt()
            val dy = ((d[1] - c[1]) * scale).roundToInt()
            forEachPixelOf(frame, hex, PATCH_REACH / scale) { x, y ->
                val sx = x + dx
                val sy = y + dy
                if (sx in 0 until w && sy in 0 until h && shown[sy * w + sx] >= 1f) {
                    val at = y * w + x
                    val from = sy * w + sx
                    val keep = ((inside[at] - RIM_FROM) / (1f - RIM_FROM)).coerceIn(0f, 1f)
                    for (ch in 0 until CHANNELS) {
                        val kept = source[at * CHANNELS + ch] * keep
                        rgb[at * CHANNELS + ch] = kept + source[from * CHANNELS + ch] * (1 - keep)
                    }
                    weight[at] = 1f
                }
            }
        }
    }

    /** Each pixel whose centre is in [hex], [slack] map units wider all round. */
    private inline fun forEachPixelOf(frame: Frame, hex: Hex, slack: Double, action: (Int, Int) -> Unit) {
        val scale = frame.scale
        val c = plain(hex.q, hex.r)
        val xFrom = ((c[0] - SQRT3 / 2 - slack - frame.x0) * scale).toInt().coerceAtLeast(0)
        val xTo = ((c[0] + SQRT3 / 2 + slack - frame.x0) * scale).toInt().coerceAtMost(frame.w - 1)
        val yFrom = ((c[1] - 1 - slack - frame.y0) * scale).toInt().coerceAtLeast(0)
        val yTo = ((c[1] + 1 + slack - frame.y0) * scale).toInt().coerceAtMost(frame.h - 1)
        for (y in yFrom..yTo) {
            for (x in xFrom..xTo) {
                val u = abs(frame.x0 + (x + HALF) / scale - c[0])
                val v = abs(frame.y0 + (y + HALF) / scale - c[1])
                if (u <= SQRT3 / 2 + slack && u / SQRT3 + v <= 1 + slack) action(x, y)
            }
        }
    }

    private fun render(
        image: BufferedImage,
        t: MapOverlay.Transform,
        frame: Frame,
        land: List<Hex>,
        patches: List<Pair<Hex, Hex>>,
        onArt: (Double, Double) -> Boolean,
    ): BufferedImage {
        val w = frame.w
        val h = frame.h
        val scale = frame.scale
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val rgb = FloatArray(w * h * CHANNELS)
        val weight = FloatArray(w * h)
        val sample = FloatArray(CHANNELS)
        val sum = FloatArray(CHANNELS)
        for (y in 0 until h) {
            for (x in 0 until w) {
                sum.fill(0f)
                val at = y * w + x
                val count = forEachSubPixel(frame, x, y) { px, py ->
                    if (!onArt(px, py)) return@forEachSubPixel false
                    val p = t.apply(px, py)
                    catmullRom(pixels, image.width, image.height, p[0], p[1], sample)
                    for (c in 0 until CHANNELS) sum[c] += sample[c]
                    true
                }
                if (count > 0) {
                    for (c in 0 until CHANNELS) rgb[at * CHANNELS + c] = sum[c] / count
                    weight[at] = count.toFloat() / (SUPERSAMPLE * SUPERSAMPLE)
                }
            }
        }
        applyPatches(rgb, weight, frame, patches)
        val opacity = artOpacity(weight, landMask(frame, land), w, h, (ART_FADE * scale).roundToInt())
        val argb = onSea(rgb, opacity, w, h, (BORDER_FADE * scale).toFloat())
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, w, h, argb, 0, w) }
    }

    /** Calls [hit] at each supersample of pixel ([x], [y]), in map units; how many it counted. */
    private inline fun forEachSubPixel(frame: Frame, x: Int, y: Int, hit: (Double, Double) -> Boolean): Int {
        var count = 0
        for (j in 0 until SUPERSAMPLE) {
            for (i in 0 until SUPERSAMPLE) {
                val px = frame.x0 + (x + (i + HALF) / SUPERSAMPLE) / frame.scale
                val py = frame.y0 + (y + (j + HALF) / SUPERSAMPLE) / frame.scale
                if (hit(px, py)) count++
            }
        }
        return count
    }

    /** 1 on every space and rock, softened by a pixel: their ground never fades. */
    private fun landMask(frame: Frame, land: List<Hex>): FloatArray {
        val mask = FloatArray(frame.w * frame.h)
        land.forEach { hex -> forEachPixelOf(frame, hex, 0.0) { x, y -> mask[y * frame.w + x] = 1f } }
        return blur(mask, frame.w, frame.h, 1)
    }

    /**
     * How much of the art each pixel shows: the art's edge (a photo's, or Forgotten Island's diamond of sea)
     * fades out over [radius] px, so it never reads as a line; the spaces and rocks in [land] stay whole.
     */
    internal fun artOpacity(weight: FloatArray, land: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val depth = blur(weight, w, h, radius)
        return FloatArray(weight.size) { i ->
            val inner = ((depth[i] - DEPTH_EDGE) / (1f - DEPTH_EDGE)).coerceIn(0f, 1f)
            max(land[i], inner * inner * (THREE - 2 * inner)) * weight[i].coerceAtMost(1f)
        }
    }

    /**
     * The final picture: [rgb] over [opacity] on [SEA], in the lit scale, fading into the sea over [band] px
     * from the image's border. The outermost pixels are the sea exactly: past them the app's canvas is the same
     * colour (`BoardMapArt.sea`), so the image has no visible edge in any rotation.
     */
    internal fun onSea(rgb: FloatArray, opacity: FloatArray, w: Int, h: Int, band: Float): IntArray {
        val sea = seaChannels()
        return IntArray(w * h) { at ->
            val x = at % w
            val y = at / w
            val fromBorder = min(min(x, w - 1 - x), min(y, h - 1 - y)) - SEA_RIM
            val t = (fromBorder / band).coerceIn(0f, 1f)
            val a = opacity[at] * t * t * (THREE - 2 * t)
            var v = 0
            for (c in 0 until CHANNELS) {
                val mixed = rgb[at * CHANNELS + c] * LIT_SCALE * a + sea[c] * (1 - a)
                v = (v shl BYTE) or mixed.roundToInt().coerceIn(0, MAX_BYTE)
            }
            v
        }
    }

    private fun seaChannels(): FloatArray {
        val v = SEA.removePrefix("#").toInt(HEX_RADIX)
        return floatArrayOf(
            ((v shr RED_SHIFT) and MAX_BYTE).toFloat(),
            ((v shr BYTE) and MAX_BYTE).toFloat(),
            (v and MAX_BYTE).toFloat(),
        )
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
        val a = abs(t)
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

    private const val CHANNELS = 3
    private const val HALF = 0.5f
    private const val THREE = 3f
    private const val HEX_RADIX = 16

    /** A blurred weight at the art's edge. */
    private const val DEPTH_EDGE = 0.5f

    /** A patch reaches this many px past its hex, so two patches leave no hairline between them. */
    private const val PATCH_REACH = 1.0

    private val NEIGHBOURS = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)
    private const val BYTE = 8
    private const val RED_SHIFT = 16
    private const val MAX_BYTE = 255
    private const val CUBIC_A = 1.5f
    private const val CUBIC_B = 2.5f
    private const val CUBIC_C = 2.5f
    private const val CUBIC_D = 4f
}
