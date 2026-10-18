package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.match.Team
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws a map's hex grid over official map art, for the visual space-by-space check (ADR-033).
 * The grid sits in a flat-top plane (`x = 1.5 q`, `y = √3/2 q + √3 r`, hex radius 1); a
 * transform fitted to [MapArt.anchors] carries it onto the art: a similarity (rotation, scale,
 * translation, optional mirror) for top-down art, a homography for a photo of the mat.
 */
object MapOverlay {
    private val SQRT3 = sqrt(3.0)
    private const val MAX_EDGE = 2000

    fun plane(q: Int, r: Int): DoubleArray = doubleArrayOf(1.5 * q, SQRT3 / 2 * q + SQRT3 * r)

    /** A 3×3 transform (row-major) from the plane to pixels. */
    class Transform(val m: DoubleArray) {
        fun apply(x: Double, y: Double): DoubleArray {
            val w = m[6] * x + m[7] * y + m[8]
            return doubleArrayOf((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
        }
    }

    /** Fits [anchors] (`[q, r, x, y]`), returning the transform and the worst anchor error in pixels. */
    fun fit(anchors: List<List<Double>>, model: String): Pair<Transform, Double> {
        val src = anchors.map { plane(it[0].toInt(), it[1].toInt()) }
        val dst = anchors.map { doubleArrayOf(it[2], it[3]) }
        val t = if (model == "homography") homography(src, dst) else similarity(src, dst)
        return t to error(t, src, dst)
    }

    private fun error(t: Transform, src: List<DoubleArray>, dst: List<DoubleArray>): Double =
        src.indices.maxOf { i -> t.apply(src[i][0], src[i][1]).let { hypot(it[0] - dst[i][0], it[1] - dst[i][1]) } }

    private fun similarity(src: List<DoubleArray>, dst: List<DoubleArray>): Transform = listOf(
        1.0,
        -1.0,
    ).map { mirror ->
        // u = a x - b y' + tx, v = b x + a y' + ty with y' = mirror * y.
        val rows = ArrayList<DoubleArray>()
        val rhs = ArrayList<Double>()
        src.indices.forEach { i ->
            val x = src[i][0]
            val y = src[i][1] * mirror
            rows += doubleArrayOf(x, -y, 1.0, 0.0)
            rhs += dst[i][0]
            rows += doubleArrayOf(y, x, 0.0, 1.0)
            rhs += dst[i][1]
        }
        val p = leastSquares(rows, rhs)
        Transform(doubleArrayOf(p[0], -p[1] * mirror, p[2], p[1], p[0] * mirror, p[3], 0.0, 0.0, 1.0))
    }.minBy { error(it, src, dst) }

    private fun homography(src: List<DoubleArray>, dst: List<DoubleArray>): Transform {
        require(src.size >= 4) { "A homography needs at least 4 anchors" }
        val rows = ArrayList<DoubleArray>()
        val rhs = ArrayList<Double>()
        src.indices.forEach { i ->
            val (x, y) = src[i][0] to src[i][1]
            val (u, v) = dst[i][0] to dst[i][1]
            rows += doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y)
            rhs += u
            rows += doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y)
            rhs += v
        }
        val h = leastSquares(rows, rhs)
        return Transform(h + 1.0)
    }

    /** Solves the normal equations AᵀA p = Aᵀb by Gaussian elimination with partial pivoting. */
    fun leastSquares(rows: List<DoubleArray>, rhs: List<Double>): DoubleArray {
        val n = rows.first().size
        val m = Array(n) { DoubleArray(n + 1) }
        rows.forEachIndexed { k, row ->
            for (i in 0 until n) {
                for (j in 0 until n) m[i][j] += row[i] * row[j]
                m[i][n] += row[i] * rhs[k]
            }
        }
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(m[it][col]) }
            val tmp = m[col]
            m[col] = m[pivot]
            m[pivot] = tmp
            require(abs(m[col][col]) > 1e-12) { "Anchors don't determine the transform" }
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                for (c in col..n) m[r][c] -= f * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    /**
     * Renders [map] over [art] into [out] (PNG, at most [MAX_EDGE] px): every space outlined in its
     * Zone's colour, spawn points as a team-coloured dot with a letter (H hero, M, R, V heavy), the
     * enclosed terrain crossed out. Returns the worst anchor error in pixels of the original art.
     */
    fun render(map: BoardMap, art: File, spec: MapArt, out: File): Double {
        val image = ImageIo.load(art).awt()
        val scale = min(1.0, MAX_EDGE.toDouble() / max(image.width, image.height))
        val (t, err) = fit(spec.anchors.map { listOf(it[0], it[1], it[2] * scale, it[3] * scale) }, spec.model)
        val canvas =
            BufferedImage((image.width * scale).toInt(), (image.height * scale).toInt(), BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, 0, 0, canvas.width, canvas.height, null)
        val unit = hypot(
            t.apply(1.5, SQRT3 / 2)[0] - t.apply(0.0, 0.0)[0],
            t.apply(1.5, SQRT3 / 2)[1] - t.apply(0.0, 0.0)[1],
        )
        g.font = Font(Font.SANS_SERIF, Font.BOLD, max(10, (unit / 4).toInt()))
        g.stroke = BasicStroke(max(1f, (unit / 30).toFloat()))
        val zoneColor = map.zones.mapIndexed { i, z -> z.id to PALETTE[i % PALETTE.size] }.toMap()
        fun hexPath(q: Int, r: Int, size: Double): Path2D.Double {
            val (x, y) = plane(q, r).let { it[0] to it[1] }
            val path = Path2D.Double()
            for (i in 0..5) {
                val a = Math.toRadians(60.0 * i)
                val p = t.apply(x + cos(a) * size, y + sin(a) * size)
                if (i == 0) path.moveTo(p[0], p[1]) else path.lineTo(p[0], p[1])
            }
            path.closePath()
            return path
        }
        map.spaces.forEach { s ->
            g.color = zoneColor.getValue(s.zone)
            g.draw(hexPath(s.q, s.r, OUTLINE))
            val spawn = s.spawn ?: return@forEach
            val c = t.apply(plane(s.q, s.r)[0], plane(s.q, s.r)[1])
            val d = unit / 5
            g.color = if (spawn.team == Team.RED) Color(230, 30, 30) else Color(30, 110, 255)
            g.fillOval((c[0] - d).toInt(), (c[1] - d).toInt(), (2 * d).toInt(), (2 * d).toInt())
            g.color = Color.WHITE
            g.drawString(LETTER.getValue(spawn.kind), (c[0] - d / 2).toFloat(), (c[1] + d * 2.4).toFloat())
        }
        g.color = Color.BLACK
        map.terrain.forEach { h ->
            g.draw(hexPath(h.q, h.r, OUTLINE * 0.8))
            val c = t.apply(plane(h.q, h.r)[0], plane(h.q, h.r)[1])
            val d = unit / 6
            g.drawLine((c[0] - d).toInt(), (c[1] - d).toInt(), (c[0] + d).toInt(), (c[1] + d).toInt())
            g.drawLine((c[0] - d).toInt(), (c[1] + d).toInt(), (c[0] + d).toInt(), (c[1] - d).toInt())
        }
        g.dispose()
        out.parentFile.mkdirs()
        ImageIO.write(canvas, "png", out)
        return err / scale
    }

    private const val OUTLINE = 0.95
    private val LETTER = mapOf(
        SpawnKind.HERO to "H",
        SpawnKind.MELEE to "M",
        SpawnKind.RANGED to "R",
        SpawnKind.HEAVY to "V",
    )
    private val PALETTE = listOf(
        Color(255, 60, 60), Color(255, 210, 0), Color(0, 220, 0), Color(0, 230, 230), Color(255, 0, 255),
        Color(60, 120, 255), Color(255, 140, 0), Color(160, 80, 255), Color(255, 255, 255), Color(140, 255, 140),
        Color(255, 150, 150), Color(150, 150, 255), Color(0, 0, 0),
    )
}
