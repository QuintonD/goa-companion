package dev.quinton.goa.web.catalog

import kotlin.js.Date
import kotlin.math.abs
import kotlin.math.hypot
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * Pinch-zoom and pan for one card scan, plus double-tap to toggle zoom (feature:catalog `Zoomable`):
 * scale 1–5, double tap 2.5×, the point between the fingers stays under them. One finger swipes
 * through the cards and scrolls the page while the scan is fitted (the CSS `touch-action: pan-x
 * pan-y` keeps those native and turns Safari's own page zoom off here); once zoomed, one finger pans
 * the card and the pager holds still ([onZoomed]).
 *
 * Works on the DOM directly: a gesture moves the image every frame without recomposing anything.
 */
class PinchZoom(private val image: HTMLElement, private val onZoomed: (Boolean) -> Unit) {
    private var scale = 1.0
    private var tx = 0.0
    private var ty = 0.0
    private var zoomed = false

    // Gesture start: two fingers (pinch) or one finger on a zoomed card (pan).
    private var startDistance = 0.0
    private var startScale = 1.0
    private var anchorX = 0.0
    private var anchorY = 0.0
    private var panX = 0.0
    private var panY = 0.0
    private var panning = false

    // Double tap: two quick taps close together.
    private var lastTapAt = 0.0
    private var lastTapX = 0.0
    private var lastTapY = 0.0
    private var tapStartX = 0.0
    private var tapStartY = 0.0
    private var moved = false
    private var lastTouchEnd = 0.0

    private val start: (Event) -> Unit = { event ->
        val touches = event.asDynamic().touches
        val count = touches.length as Int
        if (count == 2) {
            event.preventDefault()
            val (x, y) = midpoint(touches)
            startDistance = distance(touches).coerceAtLeast(1.0)
            startScale = scale
            // The card point under the fingers, which stays under them while they move.
            val (cx, cy) = centre()
            anchorX = (x - cx - tx) / scale
            anchorY = (y - cy - ty) / scale
            panning = false
            moved = true
        } else if (count == 1) {
            val touch = touches[0]
            tapStartX = (touch.clientX as Number).toDouble()
            tapStartY = (touch.clientY as Number).toDouble()
            moved = false
            panning = zoomed
            panX = tapStartX
            panY = tapStartY
        }
    }

    private val move: (Event) -> Unit = { event ->
        val touches = event.asDynamic().touches
        val count = touches.length as Int
        if (count == 2) {
            event.preventDefault()
            val (x, y) = midpoint(touches)
            val (cx, cy) = centre()
            scale = (startScale * distance(touches) / startDistance).coerceIn(MIN_SCALE, MAX_SCALE)
            tx = x - cx - scale * anchorX
            ty = y - cy - scale * anchorY
            apply()
        } else if (count == 1) {
            val touch = touches[0]
            val x = (touch.clientX as Number).toDouble()
            val y = (touch.clientY as Number).toDouble()
            if (abs(x - tapStartX) + abs(y - tapStartY) > TAP_SLOP) moved = true
            if (panning) {
                event.preventDefault()
                tx += x - panX
                ty += y - panY
                panX = x
                panY = y
                apply()
            }
        }
    }

    private val end: (Event) -> Unit = { event ->
        val remaining = event.asDynamic().touches.length as Int
        if (remaining == 0) {
            lastTouchEnd = Date.now()
            panning = false
            if (scale < SNAP_BACK) reset() else apply()
            if (!moved) onTap(tapStartX, tapStartY)
        }
    }

    /** A mouse double click toggles too (desktop, iPad with a trackpad). */
    private val doubleClick: (Event) -> Unit = { event ->
        // A finger's double tap is handled on touchend; Safari may still send a dblclick after it.
        if (Date.now() - lastTouchEnd > TOUCH_CLICK_MS) {
            val e = event.asDynamic()
            toggleAt((e.clientX as Number).toDouble(), (e.clientY as Number).toDouble())
        }
    }

    /** Safari's own pinch gesture events: kept off the scan so only this zoom happens. */
    private val gesture: (Event) -> Unit = { it.preventDefault() }

    init {
        val active: dynamic = js("({ passive: false })")
        image.addEventListener("touchstart", start, active)
        image.addEventListener("touchmove", move, active)
        image.addEventListener("touchend", end)
        image.addEventListener("touchcancel", end)
        image.addEventListener("dblclick", doubleClick)
        image.addEventListener("gesturestart", gesture, active)
    }

    fun detach() {
        image.removeEventListener("touchstart", start)
        image.removeEventListener("touchmove", move)
        image.removeEventListener("touchend", end)
        image.removeEventListener("touchcancel", end)
        image.removeEventListener("dblclick", doubleClick)
        image.removeEventListener("gesturestart", gesture)
    }

    /** Back to fitted (another card came into view). */
    fun reset() {
        scale = 1.0
        tx = 0.0
        ty = 0.0
        apply()
    }

    private fun onTap(x: Double, y: Double) {
        val now = Date.now()
        val double = now - lastTapAt < DOUBLE_TAP_MS && abs(x - lastTapX) + abs(y - lastTapY) < DOUBLE_TAP_SLOP
        if (double) {
            lastTapAt = 0.0
            toggleAt(x, y)
        } else {
            lastTapAt = now
            lastTapX = x
            lastTapY = y
        }
    }

    private fun toggleAt(x: Double, y: Double) {
        if (zoomed) {
            reset()
        } else {
            val (cx, cy) = centre()
            // Zoom into the tapped point.
            tx = -(x - cx) * (DOUBLE_TAP_SCALE - 1)
            ty = -(y - cy) * (DOUBLE_TAP_SCALE - 1)
            scale = DOUBLE_TAP_SCALE
            apply()
        }
    }

    private fun apply() {
        val width = image.offsetWidth.toDouble()
        val height = image.offsetHeight.toDouble()
        val maxX = width * (scale - 1) / 2
        val maxY = height * (scale - 1) / 2
        tx = tx.coerceIn(-maxX, maxX)
        ty = ty.coerceIn(-maxY, maxY)
        image.style.transform = if (scale == 1.0) "" else "translate(${tx}px, ${ty}px) scale($scale)"
        val now = scale > ZOOMED
        if (now != zoomed) {
            zoomed = now
            onZoomed(now)
        }
    }

    /** The image's centre on screen, untransformed (its parent doesn't move). */
    private fun centre(): Pair<Double, Double> {
        val rect = (image.parentElement as? HTMLElement ?: image).getBoundingClientRect()
        return (rect.left + rect.width / 2) to (rect.top + rect.height / 2)
    }

    private fun midpoint(touches: dynamic): Pair<Double, Double> {
        val a = touches[0]
        val b = touches[1]
        return ((a.clientX as Number).toDouble() + (b.clientX as Number).toDouble()) / 2 to
            ((a.clientY as Number).toDouble() + (b.clientY as Number).toDouble()) / 2
    }

    private fun distance(touches: dynamic): Double {
        val a = touches[0]
        val b = touches[1]
        return hypot(
            (a.clientX as Number).toDouble() - (b.clientX as Number).toDouble(),
            (a.clientY as Number).toDouble() - (b.clientY as Number).toDouble(),
        )
    }

    private companion object {
        const val MIN_SCALE = 1.0
        const val MAX_SCALE = 5.0
        const val DOUBLE_TAP_SCALE = 2.5
        const val ZOOMED = 1.01
        const val SNAP_BACK = 1.05
        const val DOUBLE_TAP_MS = 300.0
        const val DOUBLE_TAP_SLOP = 30.0
        const val TAP_SLOP = 10.0
        const val TOUCH_CLICK_MS = 800.0
    }
}
