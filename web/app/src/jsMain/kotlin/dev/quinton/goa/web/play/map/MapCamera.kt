package dev.quinton.goa.web.play.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.MapPoint
import dev.quinton.goa.feature.board.map.MapRect
import kotlin.math.sqrt
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine

/** A point on screen, CSS px from the map's top left corner. */
data class ScreenPoint(val x: Float, val y: Float) {
    operator fun plus(o: ScreenPoint) = ScreenPoint(x + o.x, y + o.y)

    operator fun minus(o: ScreenPoint) = ScreenPoint(x - o.x, y - o.y)

    operator fun times(f: Float) = ScreenPoint(x * f, y * f)

    fun length(): Float = sqrt(x * x + y * y)
}

/** A rectangle on screen, CSS px. */
data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: ScreenPoint get() = ScreenPoint((left + right) / 2f, (top + bottom) / 2f)
}

/**
 * Zoom and pan of the board, as the phone's MapViewport does it (same framing rules and limits):
 * the map is laid out at [unit] CSS px per map unit (the whole map fits the view) and shown at
 * [zoom] × that, shifted by [pan] px. Screen = pan + zoom × unit × map units.
 *
 * The visible part leaves out what floats over the map ([topInset], [bottomInset], [endInset]: a
 * badge, the bottom panel, the side panel in landscape). The board opens on the Lane (hexes at
 * least [COMFORTABLE_HEX] px across, the map's sides cropped if need be; a map with several Lanes
 * on its whole region); "Fit" shows the whole map; the person's pinch and pan win until one of
 * those is asked for again.
 */
class MapCamera {
    var width by mutableStateOf(0f)
        private set
    var height by mutableStateOf(0f)
        private set
    var unit by mutableStateOf(0f)
        private set
    var zoom by mutableStateOf(1f)
        private set
    var pan by mutableStateOf(ScreenPoint(0f, 0f))
        private set
    var bottomInset by mutableStateOf(0f)
        private set
    var endInset by mutableStateOf(0f)
        private set
    var topInset by mutableStateOf(0f)
        private set
    var mode by mutableStateOf(ViewMode.LANE)
        private set

    /** True once the person pinched, panned or double-tapped since the view was last framed. */
    var userMoved = false
        private set

    private var layout: HexLayout? = null
    private var lane: MapRect? = null
    private var laneMayCrop = true
    private var maxZoom = MIN_MAX_ZOOM
    private var animation = 0

    /** Screen px per map unit right now. */
    val scale: Float get() = unit * zoom

    val isFit: Boolean get() = mode == ViewMode.FIT

    /**
     * Lays [layout] out in a view of [w] × [h] px and frames the [lane] region, the first time and
     * again when the layout or the view's size changes (rotation, flip).
     */
    fun setUp(layout: HexLayout, w: Float, h: Float, lane: MapRect?, laneMayCrop: Boolean) {
        if (w <= 0f || h <= 0f) return
        if (layout === this.layout && w == width && h == height) return
        val sameLayout = layout === this.layout
        this.layout = layout
        this.lane = lane
        this.laneMayCrop = laneMayCrop
        width = w
        height = h
        val oldUnit = unit
        unit = minOf(w / layout.width, h / layout.height)
        // A view the person (or a reveal) moved keeps its scale on screen when the map is resized.
        if (sameLayout && mode == ViewMode.MOVED && oldUnit > 0f) zoom *= oldUnit / unit
        maxZoom = (MAX_HEX / (2f * unit)).coerceAtLeast(MIN_MAX_ZOOM)
        if (zoom > maxZoom) zoom = maxZoom
        if (!sameLayout) {
            userMoved = false
            if (mode == ViewMode.MOVED) mode = ViewMode.LANE
        }
        reframe()
    }

    /** What floats over the map changed; the Lane or Fit view follows unless the person moved it. */
    fun updateInsets(bottom: Float, end: Float, top: Float = topInset) {
        if (bottom == bottomInset && end == endInset && top == topInset) return
        val grew = bottom < bottomInset || end < endInset
        bottomInset = bottom
        endInset = end
        topInset = top
        if (layout == null) return
        reframe()
        if (grew && mode == ViewMode.MOVED) pan = fillPan(pan, zoom)
    }

    private fun reframe() {
        // A new size or inset re-frames at once: an animation towards the old frame stops here.
        animation++
        when (mode) {
            ViewMode.LANE -> lane?.let { applyLane(it) } ?: applyFit()
            ViewMode.FIT -> applyFit()
            ViewMode.MOVED -> pan = clampPan(pan, zoom)
        }
    }

    /** The part of the view the map is seen through (px), insets left out. */
    val visible: ScreenRect get() = ScreenRect(0f, topInset, width - endInset, height - bottomInset)

    private val comfortableZoom: Float get() = if (unit == 0f) 1f else COMFORTABLE_HEX / (SQRT3 * unit)

    private val minZoom: Float get() = minOf(1f, fitZoom(fullMap())) * MIN_ZOOM_SLACK

    private fun fullMap(): MapRect = layout?.let { MapRect(0f, 0f, it.width, it.height) } ?: MapRect(0f, 0f, 1f, 1f)

    private fun fitZoom(region: MapRect): Float {
        val v = visible
        val w = (v.width - 2 * MARGIN).coerceAtLeast(1f)
        val h = (v.height - 2 * MARGIN).coerceAtLeast(1f)
        return minOf(w / (region.width * unit).coerceAtLeast(1f), h / (region.height * unit).coerceAtLeast(1f))
    }

    private fun laneZoom(region: MapRect): Float {
        val fit = fitZoom(region)
        if (!laneMayCrop) return fit.coerceIn(minZoom, maxZoom)
        val length = lengthZoom(region)
        val lane = minOf(comfortableZoom, maxOf(length, comfortableZoom * LANE_SHRINK))
        return maxOf(fit, lane).coerceIn(minZoom, maxZoom)
    }

    private fun lengthZoom(region: MapRect): Float {
        val v = visible
        return if (region.height >= region.width) {
            (v.height - 2 * MARGIN).coerceAtLeast(1f) / (region.height * unit).coerceAtLeast(1f)
        } else {
            (v.width - 2 * MARGIN).coerceAtLeast(1f) / (region.width * unit).coerceAtLeast(1f)
        }
    }

    private fun applyLane(region: MapRect) {
        val z = laneZoom(region)
        zoom = z
        pan = clampPan(centerOn(region.center, z), z)
        mode = ViewMode.LANE
    }

    private fun applyFit() {
        val z = fitZoom(fullMap()).coerceIn(minZoom, maxZoom)
        zoom = z
        pan = centerOn(fullMap().center, z)
        mode = ViewMode.FIT
    }

    private fun centerOn(map: MapPoint, z: Float): ScreenPoint {
        val c = visible.center
        return ScreenPoint(c.x - map.x * unit * z, c.y - map.y * unit * z)
    }

    /** Animated: the whole map. */
    suspend fun animateFit() {
        val z = fitZoom(fullMap()).coerceIn(minZoom, maxZoom)
        userMoved = false
        // The mode first, so a resize while it animates (a panel hiding) re-frames to the same view.
        mode = ViewMode.FIT
        animateTo(z, centerOn(fullMap().center, z))
    }

    /** Animated: the Lane between the hero spawn areas, hexes comfortably large. */
    suspend fun animateLane() {
        val region = lane ?: return animateFit()
        val z = laneZoom(region)
        userMoved = false
        mode = ViewMode.LANE
        animateTo(z, clampPan(centerOn(region.center, z), z))
    }

    /** Map units → screen px. */
    fun mapToScreen(map: MapPoint): ScreenPoint = ScreenPoint(pan.x + map.x * scale, pan.y + map.y * scale)

    /** Screen px → map units. */
    fun screenToMap(screen: ScreenPoint): MapPoint =
        if (scale == 0f) MapPoint(0f, 0f) else MapPoint((screen.x - pan.x) / scale, (screen.y - pan.y) / scale)

    fun panBy(delta: ScreenPoint) {
        animation++
        mode = ViewMode.MOVED
        userMoved = true
        pan = clampPan(pan + delta, zoom)
    }

    /** Pinch or wheel: zooms by [factor] keeping [focus] still, then pans by [delta]. */
    fun transform(factor: Float, focus: ScreenPoint, delta: ScreenPoint = ScreenPoint(0f, 0f)) {
        animation++
        mode = ViewMode.MOVED
        userMoved = true
        val next = (zoom * factor).coerceIn(minZoom, maxZoom)
        val applied = next / zoom
        val panned = focus - (focus - pan) * applied + delta
        zoom = next
        pan = clampPan(panned, next)
    }

    /** Double tap: zoom in around [focus]; at the largest zoom, back to the Lane. */
    suspend fun zoomStep(focus: ScreenPoint) {
        if (zoom >= maxZoom * NEAR_MAX) {
            animateLane()
        } else {
            val next = (zoom * DOUBLE_TAP_FACTOR).coerceAtMost(maxZoom)
            val applied = next / zoom
            animateTo(next, clampPan(focus - (focus - pan) * applied, next))
            mode = ViewMode.MOVED
            userMoved = true
        }
    }

    /**
     * Keeps [region] (map units, e.g. a selected hero and what its overlays reach) comfortably in
     * view: hexes at least [COMFORTABLE_HEX] across, the whole region when it fits at that size,
     * else [focus] in the middle. Does nothing when that already holds.
     */
    suspend fun reveal(region: MapRect, focus: MapPoint) {
        if (unit == 0f) return
        var z = maxOf(zoom, comfortableZoom).coerceAtMost(maxZoom)
        if (!fits(region, z)) z = maxOf(minOf(z, fitZoom(region)), comfortableZoom).coerceIn(minZoom, maxZoom)
        val target = if (fits(region, z)) panToShow(region, z) else centerOn(focus, z)
        val clamped = clampPan(target, z)
        if (z == zoom && (clamped - pan).length() < 1f) return
        animateTo(z, clamped)
        mode = ViewMode.MOVED
    }

    private fun fits(region: MapRect, z: Float): Boolean {
        val v = visible
        return region.width * unit * z <= v.width - 2 * MARGIN && region.height * unit * z <= v.height - 2 * MARGIN
    }

    private fun panToShow(region: MapRect, z: Float): ScreenPoint {
        val v = visible
        val scaled = unit * z
        val cx = (v.center.x - pan.x) / zoom / unit
        val cy = (v.center.y - pan.y) / zoom / unit
        var x = v.center.x - cx * scaled
        var y = v.center.y - cy * scaled
        val left = x + region.left * scaled
        val right = x + region.right * scaled
        val top = y + region.top * scaled
        val bottom = y + region.bottom * scaled
        if (left < v.left + MARGIN) x += v.left + MARGIN - left
        if (right > v.right - MARGIN) x -= right - (v.right - MARGIN)
        if (top < v.top + MARGIN) y += v.top + MARGIN - top
        if (bottom > v.bottom - MARGIN) y -= bottom - (v.bottom - MARGIN)
        return ScreenPoint(x, y)
    }

    /** Eases to [targetZoom] and [targetPan] (at once when the person prefers reduced motion). */
    suspend fun animateTo(targetZoom: Float, targetPan: ScreenPoint) {
        val run = ++animation
        val startZoom = zoom
        val startPan = pan
        if (reducedMotion()) {
            zoom = targetZoom
            pan = targetPan
            return
        }
        val start = frame()
        var now = start
        while (run == animation) {
            val t = ((now - start) / DURATION_MS).toFloat().coerceIn(0f, 1f)
            val e = emphasized(t)
            zoom = startZoom + (targetZoom - startZoom) * e
            pan = ScreenPoint(startPan.x + (targetPan.x - startPan.x) * e, startPan.y + (targetPan.y - startPan.y) * e)
            if (t >= 1f) break
            now = frame()
        }
    }

    private fun clampPan(candidate: ScreenPoint, z: Float): ScreenPoint {
        val v = visible
        val l = layout ?: return candidate
        val w = l.width * unit * z
        val h = l.height * unit * z
        val minX = minOf(v.center.x - w, (v.width - w) / 2f)
        val maxX = maxOf(v.center.x, (v.width - w) / 2f)
        val minY = minOf(v.center.y - h, (v.height - h) / 2f)
        val maxY = maxOf(v.center.y, (v.height - h) / 2f)
        return ScreenPoint(candidate.x.coerceIn(minX, maxX), candidate.y.coerceIn(minY, maxY))
    }

    private fun fillPan(candidate: ScreenPoint, z: Float): ScreenPoint {
        val v = visible
        val l = layout ?: return candidate
        val w = l.width * unit * z
        val h = l.height * unit * z
        val x = if (w >= v.width) candidate.x.coerceIn(v.right - w, v.left) else candidate.x
        val y = if (h >= v.height) candidate.y.coerceIn(v.bottom - h, v.top) else candidate.y
        return clampPan(ScreenPoint(x, y), z)
    }

    enum class ViewMode { LANE, FIT, MOVED }

    companion object {
        private val SQRT3 = sqrt(3f)
        private const val MIN_MAX_ZOOM = 2f
        private const val DOUBLE_TAP_FACTOR = 2f
        private const val NEAR_MAX = 0.95f
        private const val MIN_ZOOM_SLACK = 0.9f
        private const val MARGIN = 24f
        private const val DURATION_MS = 300.0

        /** How far the Lane view may shrink hexes (38 → 32 px) to show the Lane's length. */
        private const val LANE_SHRINK = 0.84f

        /** Hexes at least this wide (CSS px) when the board frames the Lane, as on the phone (dp). */
        const val COMFORTABLE_HEX = 38f

        /** The largest hex (px across) at full zoom. */
        const val MAX_HEX = 120f
    }
}

/** Material's emphasized easing, approximated by a cubic ease-out-in (cubic-bezier(0.2, 0, 0, 1)). */
private fun emphasized(t: Float): Float {
    // Solve the bezier for x = t by bisection; fine for a 300 ms animation.
    var lo = 0f
    var hi = 1f
    var u = t
    repeat(BISECT) {
        val x = bezier(u, 0.2f, 0f)
        if (x < t) lo = u else hi = u
        u = (lo + hi) / 2f
    }
    return bezier(u, 0f, 1f)
}

private fun bezier(u: Float, p1: Float, p2: Float): Float {
    val v = 1f - u
    return 3f * v * v * u * p1 + 3f * v * u * u * p2 + u * u * u
}

private const val BISECT = 18

private suspend fun frame(): Double = suspendCancellableCoroutine { c ->
    val id = window.requestAnimationFrame { time -> c.resumeWith(Result.success(time)) }
    c.invokeOnCancellation { window.cancelAnimationFrame(id) }
}

/** The person asked the system for less motion: views jump instead of easing. */
fun reducedMotion(): Boolean = window.matchMedia("(prefers-reduced-motion: reduce)").matches
