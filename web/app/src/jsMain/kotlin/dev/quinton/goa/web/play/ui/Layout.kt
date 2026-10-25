package dev.quinton.goa.web.play.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * The board screens' landscape layout (the panel at the side, the map turned so the Lane runs left
 * to right): a phone held sideways or a wide window. Same query as goa.css `.goa-play-body`.
 */
const val LANDSCAPE_QUERY = "(orientation: landscape) and (min-width: 560px)"

/** True while [LANDSCAPE_QUERY] matches; follows rotation. */
@Composable
fun landscapeQuery(): Boolean {
    val query = remember { window.matchMedia(LANDSCAPE_QUERY) }
    var matches by remember { mutableStateOf(query.matches) }
    DisposableEffect(query) {
        val listener: (Event) -> Unit = { matches = query.matches }
        query.addEventListener("change", listener)
        onDispose { query.removeEventListener("change", listener) }
    }
    return matches
}
