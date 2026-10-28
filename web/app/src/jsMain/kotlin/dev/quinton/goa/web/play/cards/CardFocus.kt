package dev.quinton.goa.web.play.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText as PrintedText
import dev.quinton.goa.feature.board.board.cardKindLabel
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.confirmHint
import dev.quinton.goa.feature.board.ui.cards.confirmLabel
import dev.quinton.goa.feature.board.ui.cards.focusHandicapBasis
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * What confirming a focused card does. [action] names it for the card on show ("Discard Magnetic
 * Dagger"): the Confirm button and the hint "Tap the card again to discard Magnetic Dagger".
 * [onConfirm] gets the index of the confirmed card in the list opened. [context] is the question's
 * own line above the card ("Arien attacks · melee · Attack 6"). [alongside] shows other cards that
 * come with a card, under a label (upgrading: the two cards of the next Tier, p.15).
 */
class CardSelection(
    val action: (Card) -> String,
    val context: String? = null,
    val alongside: (FocusCard) -> Alongside? = { null },
    val onConfirm: (index: Int) -> Unit,
)

/** Cards shown small under a focused card, with what they are. */
class Alongside(val label: String, val cards: List<Card>)

/** An open viewer: the cards, what confirming does (null = view only), the question it belongs to. */
class CardFocusRequest internal constructor(
    val cards: List<FocusCard>,
    val title: String?,
    val selection: CardSelection?,
    val owner: Any?,
) {
    /** False while the question can't be answered (the game is busy): Confirm waits. */
    var canConfirm by mutableStateOf(true)
        internal set
}

/**
 * The card focus viewer's state (DESIGN "Card focus", the phone's CardFocusState): which cards are
 * open and which one shows. One per screen, drawn by [CardFocusHost]:
 *
 * ```
 * val focus = remember { CardFocusState() }
 * CardFocusHost(focus)
 * focus.show(cards)                                                      // read only, swipe between
 * focus.open(cards.map { FocusCard(it) }, index, CardSelection({ "Discard ${it.name}" }) { i -> answer(i) })
 * ```
 * With a selection, the first tap (on a thumbnail) zooms the card and a second tap on it, or its
 * button, answers; ×, the scrim, Escape or an upward swipe close it with nothing chosen.
 */
class CardFocusState {
    var request: CardFocusRequest? by mutableStateOf(null)
        private set

    /** The card on show (an index into the open request's cards). */
    var index by mutableStateOf(0)
        internal set

    val isOpen: Boolean get() = request != null

    val current: FocusCard? get() = request?.cards?.getOrNull(index)

    /** Opens [cards] at [index]; [owner] ties it to a question ([closeFor] closes it when that is gone). */
    fun open(
        cards: List<FocusCard>,
        index: Int = 0,
        selection: CardSelection? = null,
        title: String? = null,
        owner: Any? = null,
    ) {
        if (cards.isEmpty()) return
        request = CardFocusRequest(cards, title, selection, owner)
        this.index = index.coerceIn(0, cards.lastIndex)
    }

    /** Opens plain cards to read, swiping between them. */
    fun show(cards: List<Card>, index: Int = 0, title: String? = null) =
        open(cards.map { FocusCard(it) }, index, title = title)

    /** Closes with nothing chosen. */
    fun close() {
        request = null
    }

    /** Closes the viewer when it was opened for [owner] (its question is gone). */
    fun closeFor(owner: Any?) {
        if (request != null && request?.owner == owner) request = null
    }

    /** While a question is busy (an answer on its way), its Confirm waits. */
    fun setCanConfirm(owner: Any?, can: Boolean) {
        request?.takeIf { it.owner == owner }?.canConfirm = can
    }

    /** Answers with the card on show, if it can be chosen. */
    fun confirm() {
        val open = request ?: return
        val selection = open.selection ?: return
        val card = open.cards.getOrNull(index) ?: return
        if (!card.enabled || !open.canConfirm) return
        val chosen = index
        request = null
        selection.onConfirm(chosen)
    }
}

/**
 * Draws the open viewer over the screen: the cards large in a row you swipe through (scroll-snap,
 * so it feels native on an iPhone), Previous / Next buttons for VoiceOver and the keyboard, the
 * card's words below (its kind, verdict, why it can't be chosen, what choosing it means, its values
 * and its text verbatim) and the Confirm button when the viewer was opened to answer a question.
 */
@Composable
fun CardFocusHost(state: CardFocusState) {
    val request = state.request ?: return
    key(request) { FocusOverlay(state, request) }
}

@Composable
private fun FocusOverlay(state: CardFocusState, request: CardFocusRequest) {
    val current by rememberUpdatedState(state.index)
    var strip by remember { mutableStateOf<HTMLElement?>(null) }
    DisposableEffect(Unit) {
        val root = document.documentElement
        root?.classList?.add("goa-locked")
        val previousFocus = document.activeElement as? HTMLElement
        val onKey: (Event) -> Unit = { event ->
            when ((event as? KeyboardEvent)?.key) {
                "Escape" -> state.close()
                "ArrowRight" -> strip?.let { scrollTo(it, current + 1, request.cards.size, smooth = true) }
                "ArrowLeft" -> strip?.let { scrollTo(it, current - 1, request.cards.size, smooth = true) }
            }
        }
        document.addEventListener("keydown", onKey)
        onDispose {
            document.removeEventListener("keydown", onKey)
            root?.classList?.remove("goa-locked")
            previousFocus?.focus()
        }
    }
    // "Read the card as text" is open: the words get the room, the card steps back (it holds while swiping).
    var asText by remember { mutableStateOf(false) }
    val item = request.cards.getOrNull(state.index) ?: return
    val selection = request.selection
    Div({
        classes("goa-focus-scrim")
        onClick { state.close() }
    }) {
        Div({
            classes("goa-focus")
            if (asText) classes("as-text")
            attr("role", "dialog")
            attr("aria-modal", "true")
            attr("aria-label", request.title ?: item.card.name)
            onClick { it.stopPropagation() }
        }) {
            Div({ classes("goa-focus-top") }) {
                Span({ classes("t-label-large", "goa-focus-title") }) {
                    Text(
                        listOfNotNull(
                            request.title,
                            "${state.index + 1} of ${request.cards.size}".takeIf {
                                request.cards.size >
                                    1
                            },
                        ).joinToString(" · "),
                    )
                }
                Button({
                    classes("goa-icon-button")
                    attr("type", "button")
                    attr("aria-label", "Close")
                    onClick { state.close() }
                }) { Icon("close") }
            }
            Div({
                classes("goa-focus-strip")
                ref { el ->
                    strip = el
                    scrollTo(el, state.index, request.cards.size, smooth = false)
                    val swipe = SwipeUp(el) { state.close() }
                    val onScroll: (Event) -> Unit = {
                        val w = el.clientWidth.coerceAtLeast(1)
                        val i = ((el.scrollLeft + w / 2) / w).toInt().coerceIn(0, request.cards.lastIndex)
                        if (i != state.index) state.index = i
                    }
                    el.addEventListener("scroll", onScroll)
                    onDispose {
                        el.removeEventListener("scroll", onScroll)
                        swipe.detach()
                    }
                }
            }) {
                request.cards.forEachIndexed { i, focusCard ->
                    Div({ classes("goa-focus-slide") }) {
                        Button({
                            classes("goa-focus-card")
                            if (!focusCard.enabled && selection != null) classes("dimmed")
                            attr("type", "button")
                            attr(
                                "aria-label",
                                // The scan is the card's words (the sheet under it no longer retypes them).
                                listOfNotNull(
                                    spokenCard(focusCard.card),
                                    focusCard.caption,
                                    PrintedText.plain(focusCard.card.text).takeIf { it.isNotBlank() },
                                ).joinToString(", ") +
                                    if (selection != null &&
                                        focusCard.enabled
                                    ) {
                                        ". ${selection.action(focusCard.card)}"
                                    } else {
                                        ""
                                    },
                            )
                            onClick {
                                if (i == state.index) {
                                    state.confirm()
                                } else {
                                    strip?.let { scrollTo(it, i, request.cards.size, smooth = true) }
                                }
                            }
                        }) { CardImage(focusCard.card, FOCUS_WIDTH, "goa-focus-image") }
                    }
                }
            }
            if (request.cards.size > 1) {
                Div({ classes("goa-focus-nav") }) {
                    Button({
                        classes("goa-icon-button")
                        attr("type", "button")
                        attr("aria-label", "Previous card")
                        if (state.index == 0) attr("disabled", "")
                        onClick { strip?.let { scrollTo(it, state.index - 1, request.cards.size, smooth = true) } }
                    }) { Icon("back") }
                    Button({
                        classes("goa-icon-button", "flip-x")
                        attr("type", "button")
                        attr("aria-label", "Next card")
                        if (state.index == request.cards.lastIndex) attr("disabled", "")
                        onClick { strip?.let { scrollTo(it, state.index + 1, request.cards.size, smooth = true) } }
                    }) { Icon("back") }
                }
            }
            Div({ classes("goa-focus-details") }) {
                FocusDetails(item, selection?.context, selection?.alongside?.invoke(item), asText) { asText = !asText }
            }
            if (selection != null) {
                Div({ classes("goa-focus-confirm") }) {
                    val can = item.enabled && request.canConfirm
                    val action = selection.action(item.card)
                    if (can) P({ classes("t-body-small", "goa-quiet") }) { Text(confirmHint(action)) }
                    Button({
                        classes("goa-button", "filled", "block")
                        attr("type", "button")
                        if (!can) attr("disabled", "")
                        onClick { state.confirm() }
                    }) { Text(confirmLabel(item, request.canConfirm, action)) }
                }
            } else {
                P({ classes("t-body-small", "goa-quiet", "goa-focus-hint") }) {
                    Text(
                        if (request.cards.size > 1) {
                            "Swipe for the other cards; swipe up or tap outside to close."
                        } else {
                            "Swipe up or tap outside to close."
                        },
                    )
                }
            }
        }
    }
}

/**
 * Under the focused card, only what the card does not print (ADR-041/042: the scan is the content):
 * the question, where it sits (the turn track's "Turn 2 · Active"), the verdict, why it can't be
 * chosen, what choosing it means, the next Tier's cards, the Handicap basis and what it gives as an
 * item; the card's words as text behind "Read the card as text" (collapsed, [asText]).
 */
@Composable
private fun FocusDetails(
    item: FocusCard,
    context: String?,
    alongside: Alongside?,
    asText: Boolean,
    onToggleText: () -> Unit,
) {
    val card = item.card
    context?.let { P({ classes("t-label-large", "goa-quiet") }) { Text(it) } }
    item.caption?.let { P({ classes("t-label-large", "goa-quiet") }) { Text(it) } }
    item.verdict?.let { verdict ->
        Div({ classes("goa-verdict", verdictClass(verdict.holds)) }) {
            if (verdict.holds != null) Icon(if (verdict.holds == true) "check" else "close", IconSize.SMALL)
            Span({ classes("t-title-small") }) { Text(verdict.label) }
            Span({ classes("t-body-small") }) { Text(" · ${verdict.detail}") }
        }
    }
    if (!item.enabled) {
        P({ classes("t-body-medium", "goa-focus-reason") }) {
            Text(item.reason ?: "This card can't be chosen for this question.")
        }
    }
    item.notes.forEach { note ->
        P({ classes("t-body-medium") }) {
            note.item?.let { ItemBadge(it) }
            Text(note.text)
        }
    }
    alongside?.let { more ->
        P({ classes("t-body-small", "goa-quiet") }) { Text(more.label) }
        Div({ classes("goa-focus-related") }) { more.cards.forEach { CardImage(it, RELATED_WIDTH) } }
    }
    focusHandicapBasis(card)?.let { P({ classes("t-body-medium") }) { Text(it) } }
    if (item.itemLine) {
        card.item?.let { stat ->
            P({ classes("t-body-medium") }) {
                ItemBadge(stat)
                Text("As an item: +1 ${PlayText.stat(stat)} (p.16)")
            }
        }
    }
    Button({
        classes("goa-read-as-text", "t-label-large")
        attr("type", "button")
        attr("aria-expanded", asText.toString())
        onClick { onToggleText() }
    }) {
        Icon("article", IconSize.SMALL)
        Span({ classes("goa-read-as-text-label") }) { Text("Read the card as text") }
        Icon(if (asText) "chevron_up" else "chevron_down", IconSize.SMALL)
    }
    if (asText) CardAsText(card)
}

/** The card's printed words as text: name, kind, values, the text verbatim (brought into view when opened). */
@Composable
private fun CardAsText(card: Card) {
    Div({
        classes("goa-card-as-text")
        ref { el ->
            el.asDynamic().scrollIntoView(js("({ block: 'nearest' })"))
            onDispose { }
        }
    }) {
        H2({ classes("t-title-medium", "goa-focus-name") }) { Text(card.name) }
        P({ classes("t-label-large", "goa-quiet") }) { Text(cardKindLabel(card)) }
        Div({ classes("goa-focus-values") }) {
            card.initiative?.let { Value("Initiative $it") }
            card.values.attack?.let { Value("Attack $it") }
            card.values.defense?.let { Value("Defense $it") }
            card.values.movement?.let { Value("Movement $it") }
            card.values.range?.let { Value("Range $it") }
            card.values.radius?.let { Value("Radius $it") }
        }
        if (card.text.isNotBlank()) CardText(card.text)
    }
}

@Composable
private fun Value(text: String) {
    Span({ classes("goa-value", "t-label-large") }) { Text(text) }
}

/**
 * A card's printed text, verbatim, through the catalog's markup parser as the phone's `CardTextView`
 * draws it: icons inline (spoken as their words), bold keywords bold, `~(…)` reminders in italics.
 */
@Composable
fun CardText(text: String) {
    Div({ classes("goa-card-text", "goa-parchment") }) {
        dev.quinton.goa.web.catalog.CardTextView(text, "t-printed-small")
    }
}

/** The item icon with the stat it raises, as words ("+1 Defense" reads beside it). */
@Composable
fun ItemBadge(stat: dev.quinton.goa.core.model.catalog.Stat) {
    Span({
        classes("goa-item-badge")
        attr("aria-hidden", "true")
    }) {
        dev.quinton.goa.web.play.cards.OfficialIcon("official/icons/item-${stat.name.lowercase()}.webp")
    }
}

/** An official icon (item icons, the coin) at the text's size. */
@Composable
fun OfficialIcon(path: String) {
    org.jetbrains.compose.web.dom.Img(path, alt = "", attrs = {
        classes("goa-official-icon")
        attr("loading", "lazy")
    })
}

/** CSS class of a verdict: holds (teal, tick), falls (red, cross), undecided. */
fun verdictClass(holds: Boolean?): String = when (holds) {
    true -> "holds"
    false -> "falls"
    null -> "open"
}

private fun scrollTo(strip: HTMLElement, index: Int, count: Int, smooth: Boolean) {
    if (count == 0) return
    val i = index.coerceIn(0, count - 1)
    val left = i * strip.clientWidth
    val options: dynamic = js("({})")
    options.left = left
    options.behavior = if (smooth) "smooth" else "instant"
    if (smooth) {
        strip.asDynamic().scrollTo(options)
    } else {
        // Laid out on the next frame: then jump to the card first shown.
        window.requestAnimationFrame { strip.scrollLeft = (i * strip.clientWidth).toDouble() }
        strip.scrollLeft = left.toDouble()
    }
}

/** An upward swipe on the cards closes the viewer (the phone's fling away). */
private class SwipeUp(private val element: Element, private val onSwipe: () -> Unit) {
    private var startX = 0.0
    private var startY = 0.0
    private var tracking = false
    private val down: (Event) -> Unit = { e ->
        startX = (e.asDynamic().clientX as Number).toDouble()
        startY = (e.asDynamic().clientY as Number).toDouble()
        tracking = true
    }
    private val up: (Event) -> Unit = { e ->
        if (tracking) {
            val dx = (e.asDynamic().clientX as Number).toDouble() - startX
            val dy = (e.asDynamic().clientY as Number).toDouble() - startY
            if (dy < -SWIPE_PX && kotlin.math.abs(dx) < -dy / 2) onSwipe()
        }
        tracking = false
    }

    init {
        element.addEventListener("pointerdown", down)
        element.addEventListener("pointerup", up)
        element.addEventListener("pointercancel", { tracking = false })
    }

    fun detach() {
        element.removeEventListener("pointerdown", down)
        element.removeEventListener("pointerup", up)
    }
}

private const val SWIPE_PX = 80.0

/** A focused card's width (CSS px); the CSS shrinks it to fit a short screen. */
private const val FOCUS_WIDTH = 300
private const val RELATED_WIDTH = 88
