package dev.quinton.goa.web.play.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.ui.PanelPeek
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.Screen
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/**
 * The board screens' frame (the sandbox, a game, a puzzle), as on the phone (HideablePanel): a
 * compact title bar, [top] (the turn track), the map filling what is left, and the
 * [panel] at the bottom in portrait or at the side in landscape (CSS decides: `.goa-play-body`).
 *
 * The panel hides ([panelShown] false) to a one-line peek bar that still says what it is about
 * ([peek]: "● Arien · Choose an action for Dangerous Current", teal when it waits for you), with
 * [peekActions] (Confirm, Deselect) and Show; the map then gets the room. A screen with no panel
 * right now passes null. [panelTitle] (who a question asks) sits on the panel's top bar, beside Hide. [onPanelTall]
 * says whether a shown panel in portrait takes more than about a third of the screen.
 */
@Composable
fun PlayScaffold(
    title: String,
    actions: @Composable () -> Unit,
    panelShown: Boolean,
    onPanelShown: (Boolean) -> Unit,
    peek: PanelPeek?,
    map: @Composable () -> Unit,
    panel: (@Composable () -> Unit)?,
    top: @Composable () -> Unit = {},
    peekActions: @Composable () -> Unit = {},
    panelLabel: String = "Details",
    panelTitle: (@Composable () -> Unit)? = null,
    onPanelTall: (Boolean) -> Unit = {},
) {
    val tall by rememberUpdatedState(onPanelTall)
    Screen(title, actions = actions, flush = true, fill = true) {
        Div({ classes("goa-play") }) {
            top()
            Div({ classes("goa-play-body") }) {
                Div({ classes("goa-play-map") }) { map() }
                if (panel != null) {
                    if (panelShown) {
                        Div({
                            classes("goa-play-panel")
                            attr("role", "region")
                            attr("aria-label", panelLabel)
                            ref { el ->
                                val stop = observePanelShare(el) { tall(it) }
                                onDispose {
                                    stop()
                                    tall(false)
                                }
                            }
                        }) {
                            Div({ classes("goa-play-panel-bar") }) {
                                panelTitle?.let { title -> Div({ classes("goa-play-panel-title") }) { title() } }
                                Button({
                                    classes("goa-play-hide")
                                    attr("type", "button")
                                    attr("aria-label", "Hide the panel, see the whole board")
                                    onClick { onPanelShown(false) }
                                }) {
                                    Span({ classes("goa-play-grip") })
                                    Icon("chevron_down", IconSize.SMALL)
                                    Span({ classes("t-label-medium") }) { Text("Hide") }
                                }
                            }
                            Div({ classes("goa-play-panel-content") }) { panel() }
                        }
                    } else {
                        PeekBar(peek, onShow = { onPanelShown(true) }, actions = peekActions)
                    }
                }
            }
        }
    }
}

/** Tells [onTall] as [panel] (portrait only) grows past or falls under about a third of the screen. */
private fun observePanelShare(panel: HTMLElement, onTall: (Boolean) -> Unit): () -> Unit {
    val callback: () -> Unit = {
        // In the next frame: its answer can resize the panel, and inside the observer that's a reported loop.
        window.requestAnimationFrame {
            val play = panel.closest(".goa-play") as? HTMLElement
            val body = panel.parentElement as? HTMLElement
            val portrait = body != null && window.getComputedStyle(body).flexDirection == "column"
            if (play != null) onTall(portrait && panel.offsetHeight > play.clientHeight * TALL_PANEL_SHARE)
        }
    }
    val observer: dynamic = js("new ResizeObserver(callback)")
    observer.observe(panel)
    return { observer.disconnect() }
}

/**
 * Tells the panel around [question] where the question ends in its content (`--question-end`), so CSS can stop the
 * panel just past it. Nothing outside a panel (a review's replay). Returns how to stop.
 */
fun reportQuestionEnd(question: HTMLElement): () -> Unit {
    val panel = question.closest(".goa-play-panel") as? HTMLElement ?: return {}
    val content = question.closest(".goa-play-panel-content") as? HTMLElement ?: return {}
    var stopped = false
    val callback: () -> Unit = {
        // In the next frame: this resizes the panel, and inside the observer that's a reported loop.
        window.requestAnimationFrame {
            if (!stopped) {
                val end = question.getBoundingClientRect().bottom - content.getBoundingClientRect().top +
                    content.scrollTop
                val value = "${end.toInt()}px"
                if (panel.style.getPropertyValue("--question-end") != value) {
                    panel.style.setProperty("--question-end", value)
                }
            }
        }
    }
    val observer: dynamic = js("new ResizeObserver(callback)")
    observer.observe(question)
    (question.parentElement as? HTMLElement)?.let { observer.observe(it) }
    return {
        stopped = true
        observer.disconnect()
        panel.style.removeProperty("--question-end")
    }
}

private const val TALL_PANEL_SHARE = 0.36

/** The hidden panel's one line: who and what, its actions, Show. A tap on the line shows the panel. */
@Composable
fun PeekBar(peek: PanelPeek?, onShow: () -> Unit, actions: @Composable () -> Unit = {}) {
    Div({
        classes("goa-play-peek")
        if (peek?.attention == true) classes("attention")
        attr("role", "region")
        attr("aria-label", "Panel hidden")
        if (peek?.attention == true) attr("aria-live", "polite")
    }) {
        Button({
            classes("goa-play-peek-line")
            attr("type", "button")
            onClick { onShow() }
        }) {
            if (peek?.attention == true) Span({ classes("goa-play-dot") })
            peek?.label?.let { label ->
                peek.team?.let { TeamMark(it) }
                Span({
                    classes("goa-play-peek-label")
                    peek.team?.let { classes(if (it == Team.RED) "c-team-red" else "c-team-blue") }
                }) { Text(label) }
                Span({ classes("goa-play-peek-sep") }) { Text("·") }
            }
            Span({ classes("goa-play-peek-text") }) { Text(peek?.text.orEmpty()) }
        }
        actions()
        Button({
            classes("goa-button", "tonal", "goa-play-show")
            attr("type", "button")
            onClick { onShow() }
        }) {
            Icon("chevron_up", IconSize.SMALL)
            Text("Show")
        }
    }
}

/** The team's mark (Red's gear, Blue's star; a shape while it loads): team is never colour alone. */
@Composable
fun TeamMark(team: Team) {
    Span({
        classes("goa-team-mark", if (team == Team.RED) "red" else "blue")
        attr("aria-hidden", "true")
    })
}

/**
 * The Tie Breaker coin as printed (ADR-042: Red's gear, Blue's star, rulebook p.5), with the team's
 * shape on a badge at its foot as on the board's pieces (team never by colour alone). While the face
 * loads, or if it can't: the shape on a bronze ring. [big]: New game's coin flip; else the HUD's
 * size. [spoken] null leaves it to the caller's label (the HUD reads the coin in its own line).
 */
@Composable
fun TieBreakerCoin(team: Team, big: Boolean = false, spoken: String? = null) {
    var loaded by remember(team) { mutableStateOf(false) }
    var failed by remember(team) { mutableStateOf(false) }
    Span({
        classes("goa-tb-coin")
        if (big) classes("big")
        if (!loaded) classes("ring")
        if (spoken != null) {
            attr("role", "img")
            attr("aria-label", spoken)
        } else {
            attr("aria-hidden", "true")
        }
    }) {
        if (!failed) {
            Img("official/board/tie-breaker-${team.name.lowercase()}.webp", alt = "", attrs = {
                classes("goa-tb-face")
                if (!loaded) classes("loading")
                attr("decoding", "async")
                addEventListener("load") { loaded = true }
                addEventListener("error") { failed = true }
            })
        }
        Span({ classes("goa-tb-badge") }) { TeamMark(team) }
    }
}
