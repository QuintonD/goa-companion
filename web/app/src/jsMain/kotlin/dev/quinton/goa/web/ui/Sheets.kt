package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * A modal bottom sheet, the iPhone way: slides up over a scrim, a grab handle to drag it down,
 * tap outside or Escape to close, the page behind doesn't scroll, focus moves in and comes back.
 * [dismissible] false while it must stay (work in progress): drag, scrim and Escape do nothing.
 */
@Composable
fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: ContentBuilder<HTMLDivElement>,
) {
    var dragY by remember { mutableStateOf(0.0) }
    ModalFrame(onDismiss = { if (dismissible) onDismiss() }, center = false) {
        Div({
            classes("goa-sheet")
            attr("role", "dialog")
            attr("aria-modal", "true")
            attr("aria-label", title)
            tabIndex(-1)
            if (dragY > 0) style { property("transform", "translateY(${dragY}px)") }
            onClick { it.stopPropagation() }
            ref { sheet ->
                focusWithoutScroll(sheet)
                onDispose { }
            }
        }) {
            Div({
                classes("goa-sheet-handle")
                attr("aria-hidden", "true")
                ref { handle ->
                    val drag = SheetDrag(handle, onMove = { dragY = it }, onRelease = { distance, height ->
                        dragY = 0.0
                        if (dismissible && distance > height * DISMISS_FRACTION) onDismiss()
                    })
                    onDispose { drag.detach() }
                }
            })
            Div({ classes("goa-sheet-header") }) {
                H2({ classes("t-title-large") }) { Text(title) }
                if (dismissible) IconButton("close", "Close", onClick = onDismiss)
            }
            Div({ classes("goa-sheet-body") }, content)
        }
    }
}

/** A centred alert dialog: [title], body [content], and the buttons in [buttons] (right-aligned). */
@Composable
fun AlertDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: ContentBuilder<HTMLDivElement>,
) {
    ModalFrame(onDismiss = onDismiss, center = true) {
        Div({
            classes("goa-dialog")
            attr("role", "alertdialog")
            attr("aria-modal", "true")
            attr("aria-label", title)
            tabIndex(-1)
            onClick { it.stopPropagation() }
            ref { dialog ->
                focusWithoutScroll(dialog)
                onDispose { }
            }
        }) {
            H2({ classes("t-headline-small") }) { Text(title) }
            Div({ classes("goa-dialog-body", "t-body-medium") }, content)
            Div({ classes("goa-button-row") }) { buttons() }
        }
    }
}

/** Scrim + scroll lock + Escape + focus return, shared by sheets and dialogs. */
@Composable
private fun ModalFrame(onDismiss: () -> Unit, center: Boolean, content: @Composable () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(Unit) {
        val previousFocus = document.activeElement as? HTMLElement
        val root = document.documentElement
        root?.classList?.add("goa-locked")
        val onKey: (Event) -> Unit = { event ->
            if ((event as? KeyboardEvent)?.key == "Escape") dismiss()
        }
        document.addEventListener("keydown", onKey)
        onDispose {
            document.removeEventListener("keydown", onKey)
            root?.classList?.remove("goa-locked")
            previousFocus?.let { focusWithoutScroll(it) }
        }
    }
    Div({
        classes("goa-scrim")
        if (center) classes("center")
        onClick { onDismiss() }
    }) { content() }
}

/** Follows a finger (or mouse) dragging the sheet's handle down. */
private class SheetDrag(
    private val handle: HTMLElement,
    private val onMove: (Double) -> Unit,
    private val onRelease: (distance: Double, height: Double) -> Unit,
) {
    private var startY: Double? = null
    private var distance = 0.0

    private val down: (Event) -> Unit = { event ->
        val e = event.asDynamic()
        startY = (e.clientY as Number).toDouble()
        distance = 0.0
        handle.asDynamic().setPointerCapture(e.pointerId)
    }
    private val move: (Event) -> Unit = { event ->
        startY?.let { start ->
            distance = ((event.asDynamic().clientY as Number).toDouble() - start).coerceAtLeast(0.0)
            onMove(distance)
        }
    }
    private val up: (Event) -> Unit = {
        if (startY != null) {
            startY = null
            val sheetHeight = (handle.parentElement as? HTMLElement)?.offsetHeight?.toDouble() ?: 1.0
            onRelease(distance, sheetHeight)
        }
    }

    init {
        handle.addEventListener("pointerdown", down)
        handle.addEventListener("pointermove", move)
        handle.addEventListener("pointerup", up)
        handle.addEventListener("pointercancel", up)
    }

    fun detach() {
        handle.removeEventListener("pointerdown", down)
        handle.removeEventListener("pointermove", move)
        handle.removeEventListener("pointerup", up)
        handle.removeEventListener("pointercancel", up)
    }
}

private const val DISMISS_FRACTION = 0.25
