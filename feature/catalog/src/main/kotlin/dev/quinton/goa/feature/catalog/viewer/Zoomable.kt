package dev.quinton.goa.feature.catalog.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/** Zoom state for one card image; 1 = fitted. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > 1.01f

    internal fun transform(zoom: Float, pan: Offset, size: IntSize) {
        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        offset = clamp(offset + pan, size)
    }

    fun toggle(size: IntSize) {
        if (isZoomed) reset() else transform(DOUBLE_TAP_SCALE, Offset.Zero, size)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp(value: Offset, size: IntSize): Offset {
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}

/**
 * Pinch-zoom and pan, plus double-tap to toggle zoom. Single-finger drags pass through to the
 * pager while the image is fitted, so swiping between cards keeps working.
 */
fun Modifier.zoomable(state: ZoomState): Modifier = this
    .pointerInput(state) {
        detectTapGestures(onDoubleTap = { state.toggle(size) })
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pointers = event.changes.count { it.pressed }
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val pinching = pointers > 1 && event.calculateCentroidSize() > 0f
                if (pinching || (state.isZoomed && abs(pan.x) + abs(pan.y) > 0f)) {
                    state.transform(zoom, pan, size)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
    }
