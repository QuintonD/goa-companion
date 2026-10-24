package dev.quinton.goa.feature.board.map

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp
import dev.quinton.goa.core.designsystem.theme.Motion
import kotlin.math.sqrt

/**
 * Zoom and pan of the map. The map is laid out once at [unit] px per map unit (the whole map fits
 * the viewport) and shown at [zoom] × that, shifted by [pan] (px). Screen = pan + zoom × content,
 * content = map units × [unit].
 *
 * The visible part of the map leaves out what floats over it: [topInset] (the Unverified badge),
 * [bottomInset] (the tools or the selection panel) and [endInset] (the side panel in landscape).
 * The board opens on the Lane ([lane]: hexes at least `minHexPx` across, the map's sides cropped
 * if need be; on a map with several Lanes the whole region, so no Lane's Battle Zone is cut off);
 * "Fit" shows the whole map; the user's pinch and pan win until one of those is asked for again.
 */
@Stable
class MapViewport {
    var viewport by mutableStateOf(Size.Zero)
        private set

    /** Content size at zoom 1, px. */
    var content by mutableStateOf(Size.Zero)
        private set

    var unit by mutableFloatStateOf(0f)
        private set

    var zoom by mutableFloatStateOf(1f)
        private set

    var pan by mutableStateOf(Offset.Zero)
        private set

    var bottomInset by mutableFloatStateOf(0f)
        private set

    var endInset by mutableFloatStateOf(0f)
        private set

    var topInset by mutableFloatStateOf(0f)
        private set

    /** What the view shows now: decides what an inset change or rotation re-frames. */
    var mode by mutableStateOf(ViewMode.LANE)
        private set

    /**
     * True once the person pinched, panned or double-tapped since the view was last framed by Fit,
     * the Lane or a new layout: hiding a panel then leaves their view alone instead of fitting.
     */
    var userMoved = false
        private set

    private var maxZoom = MIN_MAX_ZOOM
    private var maxHexPx = 0f
    private var minHexPx = 0f
    private var layout: MapLayout? = null

    /** The Lane between the hero spawn areas, in map units: what the board opens on. */
    private var lane: Rect? = null
    private var laneMayCrop = true

    /**
     * Lays [layout] out in a viewport of [size] (px) and frames the [lane] region, the first time
     * and again when the layout or the viewport changes (rotation, flip). [minHexPx]: the smallest
     * comfortable hex (across its flats) when framing; [maxHexPx]: the largest at full zoom.
     */
    fun setUp(layout: MapLayout, size: Size, lane: Rect?, laneMayCrop: Boolean, minHexPx: Float, maxHexPx: Float) {
        if (size.width <= 0f || size.height <= 0f) return
        if (layout === this.layout && size == viewport) return
        this.layout = layout
        this.lane = lane
        this.laneMayCrop = laneMayCrop
        this.minHexPx = minHexPx
        this.maxHexPx = maxHexPx
        viewport = size
        unit = minOf(size.width / layout.width, size.height / layout.height)
        content = Size(layout.width * unit, layout.height * unit)
        maxZoom = (maxHexPx / (2f * unit)).coerceAtLeast(MIN_MAX_ZOOM)
        userMoved = false
        reframe()
    }

    /** What floats over the map changed; the Lane or Fit view follows unless the user moved it. */
    fun updateInsets(bottom: Float, end: Float, top: Float = topInset) {
        if (bottom == bottomInset && end == endInset && top == topInset) return
        // The selection panel closed: the view had made room for it, so now fill the space it
        // leaves rather than show a band of empty water where the panel was.
        val grew = bottom < bottomInset || end < endInset
        bottomInset = bottom
        endInset = end
        topInset = top
        if (layout == null) return
        reframe()
        if (grew && mode == ViewMode.MOVED) pan = fillPan(pan, zoom)
    }

    private fun reframe() {
        when (mode) {
            ViewMode.LANE -> lane?.let { applyLane(it) } ?: applyFit()
            ViewMode.FIT -> applyFit()
            ViewMode.MOVED -> pan = clampPan(pan, zoom)
        }
    }

    /** The part of the screen the map is seen through (px), insets left out. */
    val visible: Rect get() = Rect(0f, topInset, viewport.width - endInset, viewport.height - bottomInset)

    /** Zoom at which a hex is [minHexPx] across its flats. */
    private val comfortableZoom: Float get() = if (unit == 0f) 1f else minHexPx / (SQRT3 * unit)

    private val minZoom: Float get() = minOf(1f, fitZoom(fullMap())) * MIN_ZOOM_SLACK

    private fun fullMap(): Rect = layout?.let { Rect(0f, 0f, it.width, it.height) } ?: Rect(0f, 0f, 1f, 1f)

    /** The zoom that fits [region] (map units) into the visible part, with a margin. */
    private fun fitZoom(region: Rect): Float {
        val v = visible
        val w = (v.width - 2 * MARGIN_PX).coerceAtLeast(1f)
        val h = (v.height - 2 * MARGIN_PX).coerceAtLeast(1f)
        return minOf(w / (region.width * unit).coerceAtLeast(1f), h / (region.height * unit).coerceAtLeast(1f))
    }

    /**
     * The Lane view's zoom. When cropping is allowed, only the map's sides are cropped: the Lane's
     * length (both teams' hero spawn points, the starting Battle Zone) stays in view with hexes
     * about [minHexPx] across, a little smaller ([LANE_SHRINK]) if that is what it takes. Else the
     * whole region fits.
     */
    private fun laneZoom(region: Rect): Float {
        val fit = fitZoom(region)
        if (!laneMayCrop) return fit.coerceIn(minZoom, maxZoom)
        val length = lengthZoom(region)
        val lane = minOf(comfortableZoom, maxOf(length, comfortableZoom * LANE_SHRINK))
        return maxOf(fit, lane).coerceIn(minZoom, maxZoom)
    }

    /** The zoom that fits [region]'s longer side (the Lane's length) into the visible part. */
    private fun lengthZoom(region: Rect): Float {
        val v = visible
        return if (region.height >= region.width) {
            (v.height - 2 * MARGIN_PX).coerceAtLeast(1f) / (region.height * unit).coerceAtLeast(1f)
        } else {
            (v.width - 2 * MARGIN_PX).coerceAtLeast(1f) / (region.width * unit).coerceAtLeast(1f)
        }
    }

    private fun applyLane(region: Rect) {
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

    private fun centerOn(map: Offset, z: Float): Offset {
        val v = visible
        return Offset(v.center.x - map.x * unit * z, v.center.y - map.y * unit * z)
    }

    val isFit: Boolean get() = mode == ViewMode.FIT

    /** Animated: the whole map. */
    suspend fun animateFit() {
        val z = fitZoom(fullMap()).coerceIn(minZoom, maxZoom)
        userMoved = false
        animateTo(z, centerOn(fullMap().center, z))
        mode = ViewMode.FIT
    }

    /** Animated: the Lane between the hero spawn areas, hexes comfortably large. */
    suspend fun animateLane() {
        val region = lane ?: return animateFit()
        val z = laneZoom(region)
        userMoved = false
        animateTo(z, clampPan(centerOn(region.center, z), z))
        mode = ViewMode.LANE
    }

    fun toContent(screen: Offset): Offset = (screen - pan) / zoom

    fun toScreen(content: Offset): Offset = pan + content * zoom

    /** Map units → screen px. */
    fun mapToScreen(map: Offset): Offset = toScreen(map * unit)

    /** Screen px → map units. */
    fun screenToMap(screen: Offset): Offset = if (unit == 0f) Offset.Zero else toContent(screen) / unit

    /** Screen px per map unit right now. */
    val scale: Float get() = unit * zoom

    fun panBy(delta: Offset) {
        mode = ViewMode.MOVED
        userMoved = true
        pan = clampPan(pan + delta, zoom)
    }

    /** Pinch: zooms by [factor] keeping [focus] (screen px) still, then pans by [delta]. */
    fun transform(factor: Float, focus: Offset, delta: Offset) {
        mode = ViewMode.MOVED
        userMoved = true
        val next = (zoom * factor).coerceIn(minZoom, maxZoom)
        val applied = next / zoom
        val panned = focus - (focus - pan) * applied + delta
        zoom = next
        pan = clampPan(panned, next)
    }

    /** Double tap: zoom in around [focus]; at the largest zoom, back to the Lane. */
    suspend fun zoomStep(focus: Offset) {
        if (zoom >= maxZoom * 0.95f) {
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
     * view: hexes at least `minHexPx` across, the whole region inside the visible part when it fits
     * at that size, else [focus] (the piece) in the middle. Does nothing when that already holds.
     */
    suspend fun reveal(region: Rect, focus: Offset) {
        if (unit == 0f) return
        var z = maxOf(zoom, comfortableZoom).coerceAtMost(maxZoom)
        if (!fits(region, z)) z = maxOf(minOf(z, fitZoom(region)), comfortableZoom).coerceIn(minZoom, maxZoom)
        val target = if (fits(region, z)) panToShow(region, z) else centerOn(focus, z)
        val clamped = clampPan(target, z)
        if (z == zoom && (clamped - pan).getDistance() < 1f) return
        animateTo(z, clamped)
        mode = ViewMode.MOVED
    }

    private fun fits(region: Rect, z: Float): Boolean {
        val v = visible
        return region.width * unit * z <= v.width - 2 * MARGIN_PX &&
            region.height * unit * z <= v.height - 2 * MARGIN_PX
    }

    /** The smallest pan from the current one that brings [region] inside the visible part at [z]. */
    private fun panToShow(region: Rect, z: Float): Offset {
        val v = visible
        val scaled = unit * z
        // Where the current view's centre (map units) would stay, then nudge to contain the region.
        val centre = (v.center - pan) / zoom / unit
        var x = v.center.x - centre.x * scaled
        var y = v.center.y - centre.y * scaled
        val left = x + region.left * scaled
        val right = x + region.right * scaled
        val top = y + region.top * scaled
        val bottom = y + region.bottom * scaled
        if (left < v.left + MARGIN_PX) x += v.left + MARGIN_PX - left
        if (right > v.right - MARGIN_PX) x -= right - (v.right - MARGIN_PX)
        if (top < v.top + MARGIN_PX) y += v.top + MARGIN_PX - top
        if (bottom > v.bottom - MARGIN_PX) y -= bottom - (v.bottom - MARGIN_PX)
        return Offset(x, y)
    }

    suspend fun animateTo(targetZoom: Float, targetPan: Offset) {
        val startZoom = zoom
        val startPan = pan
        animate(0f, 1f, animationSpec = tween(Motion.LONG_MS, easing = Motion.Emphasized)) { t, _ ->
            zoom = lerp(startZoom, targetZoom, t)
            pan = Offset(lerp(startPan.x, targetPan.x, t), lerp(startPan.y, targetPan.y, t))
        }
    }

    /** Keeps at least half of the visible part over the map. */
    private fun clampPan(candidate: Offset, z: Float): Offset {
        val v = visible
        val w = content.width * z
        val h = content.height * z
        val minX = minOf(v.center.x - w, (v.width - w) / 2f)
        val maxX = maxOf(v.center.x, (v.width - w) / 2f)
        val minY = minOf(v.center.y - h, (v.height - h) / 2f)
        val maxY = maxOf(v.center.y, (v.height - h) / 2f)
        return Offset(candidate.x.coerceIn(minX, maxX), candidate.y.coerceIn(minY, maxY))
    }

    /**
     * [candidate], moved as little as needed so that along each axis where the map is larger than
     * the visible part, the map covers it edge to edge (no empty band); other axes as [clampPan].
     */
    private fun fillPan(candidate: Offset, z: Float): Offset {
        val v = visible
        val w = content.width * z
        val h = content.height * z
        val x = if (w >= v.width) candidate.x.coerceIn(v.right - w, v.left) else candidate.x
        val y = if (h >= v.height) candidate.y.coerceIn(v.bottom - h, v.top) else candidate.y
        return clampPan(Offset(x, y), z)
    }

    enum class ViewMode { LANE, FIT, MOVED }

    private companion object {
        val SQRT3 = sqrt(3f)
        const val MIN_MAX_ZOOM = 2f
        const val DOUBLE_TAP_FACTOR = 2f
        const val MIN_ZOOM_SLACK = 0.9f
        const val MARGIN_PX = 24f

        /** How far the Lane view may shrink hexes (38 → 32 dp) to show the Lane's length. */
        const val LANE_SHRINK = 0.84f
    }
}
