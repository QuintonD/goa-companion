package dev.quinton.goa.feature.board.map

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp
import dev.quinton.goa.core.designsystem.theme.Motion

/**
 * Zoom and pan of the map. The map is laid out once at [unit] px per map unit (the "Fit" scale,
 * where the whole map fits the viewport above [bottomInset], the tools floating over the map's
 * bottom edge) and shown at [zoom] × that, shifted by [pan] (px).
 * Screen = pan + zoom × content, content = map units × [unit].
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

    /** Height (px) at the bottom covered by floating tools: Fit keeps the map above it. */
    var bottomInset by mutableFloatStateOf(0f)
        private set

    /** Largest zoom: a hex then spans about `maxHexPx` px. */
    private var maxZoom = MIN_MAX_ZOOM
    private var maxHexPx = 0f
    private var layout: MapLayout? = null

    /** The user has zoomed or panned since the last Fit: nothing re-fits by itself then. */
    private var moved = false

    /**
     * Fits [layout] into a viewport of [size] (px) the first time, and again when the layout or the
     * viewport changes (rotation, flip). [maxHexPx]: how big a hex may get at the largest zoom.
     */
    fun setUp(layout: MapLayout, size: Size, maxHexPx: Float) {
        if (size.width <= 0f || size.height <= 0f) return
        if (layout === this.layout && size == viewport) return
        this.layout = layout
        this.maxHexPx = maxHexPx
        viewport = size
        measure()
        fit()
    }

    /** The floating tools' height changed; re-fits unless the user has moved the map. */
    fun updateBottomInset(px: Float) {
        if (px == bottomInset) return
        bottomInset = px
        if (!moved && layout != null) {
            measure()
            fit()
        }
    }

    private fun measure() {
        val layout = layout ?: return
        val usable = (viewport.height - bottomInset).coerceAtLeast(viewport.height * MIN_USABLE)
        unit = minOf(viewport.width / layout.width, usable / layout.height)
        content = Size(layout.width * unit, layout.height * unit)
        maxZoom = (maxHexPx / (2f * unit)).coerceAtLeast(MIN_MAX_ZOOM)
    }

    val isFit: Boolean get() = zoom <= 1.001f

    fun fit() {
        zoom = 1f
        pan = fitPan()
        moved = false
    }

    private fun fitPan(): Offset {
        val usable = (viewport.height - bottomInset).coerceAtLeast(content.height)
        return Offset((viewport.width - content.width) / 2f, (usable - content.height) / 2f)
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
        moved = true
        pan = clampPan(pan + delta, zoom)
    }

    /** Pinch: zooms by [factor] keeping [focus] (screen px) still, then pans by [delta]. */
    fun transform(factor: Float, focus: Offset, delta: Offset) {
        moved = true
        val next = (zoom * factor).coerceIn(MIN_ZOOM, maxZoom)
        val applied = next / zoom
        val panned = focus - (focus - pan) * applied + delta
        zoom = next
        pan = clampPan(panned, next)
    }

    /** Double tap: zoom in around [focus]; at the largest zoom, back to Fit. */
    suspend fun zoomStep(focus: Offset) {
        if (zoom >= maxZoom * 0.95f) {
            animateFit()
        } else {
            moved = true
            val next = (zoom * DOUBLE_TAP_FACTOR).coerceAtMost(maxZoom)
            val applied = next / zoom
            animateTo(next, clampPan(focus - (focus - pan) * applied, next))
        }
    }

    /** Animated Fit. */
    suspend fun animateFit() {
        animateTo(1f, fitPan())
        moved = false
    }

    /** Pans (animated) so [map] (map units) sits at [target] (screen px). */
    suspend fun bringTo(map: Offset, target: Offset) {
        moved = true
        val screen = mapToScreen(map)
        animateTo(zoom, clampPan(pan + (target - screen), zoom))
    }

    suspend fun animateTo(targetZoom: Float, targetPan: Offset) {
        val startZoom = zoom
        val startPan = pan
        animate(0f, 1f, animationSpec = tween(Motion.LONG_MS, easing = Motion.Emphasized)) { t, _ ->
            zoom = lerp(startZoom, targetZoom, t)
            pan = Offset(lerp(startPan.x, targetPan.x, t), lerp(startPan.y, targetPan.y, t))
        }
    }

    /** Keeps at least half of the viewport over the map. */
    private fun clampPan(candidate: Offset, z: Float): Offset {
        val w = content.width * z
        val h = content.height * z
        val minX = minOf(viewport.width / 2f - w, (viewport.width - w) / 2f)
        val maxX = maxOf(viewport.width / 2f, (viewport.width - w) / 2f)
        val minY = minOf(viewport.height / 2f - h, (viewport.height - h) / 2f)
        val maxY = maxOf(viewport.height / 2f, (viewport.height - h) / 2f)
        return Offset(candidate.x.coerceIn(minX, maxX), candidate.y.coerceIn(minY, maxY))
    }

    private companion object {
        const val MIN_ZOOM = 1f
        const val MIN_MAX_ZOOM = 2f
        const val DOUBLE_TAP_FACTOR = 2.2f

        /** Fit never shrinks the map into less than this share of the height. */
        const val MIN_USABLE = 0.5f
    }
}
