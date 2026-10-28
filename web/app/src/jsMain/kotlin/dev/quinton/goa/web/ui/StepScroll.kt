package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.awaitAnimationFrame
import org.w3c.dom.HTMLElement

/**
 * A screen that swaps its whole body in place (a wizard's next step, a quiz's results) keeps the
 * page's scroll offset, so the new body opened part way down: the player saw the middle of Game
 * options, or the round's misses instead of the score. When [step] changes (not on the first
 * composition, where the router restores the scroll), the page goes back to the top and VoiceOver
 * starts at the title, as after a navigation.
 */
@Composable
fun ScrollToTopOnStep(step: Any) {
    val last = remember { arrayOf(step) }
    LaunchedEffect(step) {
        if (last[0] == step) return@LaunchedEffect
        last[0] = step
        window.awaitAnimationFrame()
        window.scrollTo(0.0, 0.0)
        (document.querySelector(".goa-topbar-title") as? HTMLElement)?.let(::focusWithoutScroll)
    }
}
