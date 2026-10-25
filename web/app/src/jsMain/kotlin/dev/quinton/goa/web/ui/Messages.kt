package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** A short app-wide message (Android's snackbar above the bottom bar); [onAction] runs when [actionLabel] is tapped. */
data class Message(val id: Int, val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** The app's message queue: one at a time, each for a few seconds (longer with an action). */
class Messages {
    private val queue = mutableStateListOf<Message>()
    private var nextId = 1

    val current: Message? get() = queue.firstOrNull()

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        queue.add(Message(nextId++, text, actionLabel, onAction))
    }

    fun dismiss(id: Int) {
        queue.removeAll { it.id == id }
    }
}

/**
 * Bottom-of-screen messages: the queue's current message, or the "Update available" prompt, which
 * stays until the person reloads or closes it (a game in progress is never reloaded by itself).
 */
@Composable
fun SnackbarHost(messages: Messages, updateReady: Boolean, onUpdate: () -> Unit) {
    var updateDismissed by remember { mutableStateOf(false) }
    val message = messages.current
    LaunchedEffect(message?.id) {
        val shown = message ?: return@LaunchedEffect
        delay(if (shown.actionLabel != null) LONG_MS else SHORT_MS)
        messages.dismiss(shown.id)
    }
    Div({
        classes("goa-snackbar-host")
        attr("aria-live", "polite")
    }) {
        when {
            message != null -> Snackbar(message.text, message.actionLabel, onAction = {
                messages.dismiss(message.id)
                message.onAction?.invoke()
            }, onClose = { messages.dismiss(message.id) })

            updateReady && !updateDismissed -> Snackbar(
                "A new version of the app is ready.",
                "Reload",
                onAction = onUpdate,
                onClose = { updateDismissed = true },
            )
        }
    }
}

@Composable
private fun Snackbar(text: String, actionLabel: String?, onAction: () -> Unit, onClose: () -> Unit) {
    Div({
        classes("goa-snackbar")
        attr("role", "status")
    }) {
        Span({ classes("goa-snackbar-text", "t-body-medium") }) { Text(text) }
        actionLabel?.let { GoaButton(it, onAction, ButtonKind.TEXT) }
        IconButton("close", "Dismiss", onClick = onClose)
    }
}

private const val SHORT_MS = 4_000L
private const val LONG_MS = 8_000L
