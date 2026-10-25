package dev.quinton.goa.web.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.blockMarkerLabel
import dev.quinton.goa.core.domain.catalog.cardDescription
import dev.quinton.goa.core.domain.catalog.cardValues
import dev.quinton.goa.core.domain.catalog.handicapBasis
import dev.quinton.goa.core.domain.catalog.kindLabel
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.ParchmentPanel
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

private class ViewerDeck(val hero: Hero, val cards: List<Card>, val initialIndex: Int) {
    fun isHandicap(card: Card): Boolean = hero.handicap?.id == card.id
}

/**
 * The card viewer (feature:catalog `CardViewerScreen`, ADR-042's lean viewer): the official scan per
 * page, swiped through every card of the hero in the deck's order (native scroll snapping, so the
 * swipe feels like iOS), pinch or double-tap to zoom, and the card as text one tap away.
 */
@Composable
fun CardViewerScreen(heroId: String, cardId: String) {
    val services = LocalServices.current
    val loaded = rememberLoad(heroId to cardId) {
        val hero = services.data.content.browse.indexed().catalog.hero(heroId)
        val cards = hero?.let { DeckLayout.of(it).orderedCards }.orEmpty()
        if (hero == null || cards.isEmpty()) {
            null
        } else {
            ViewerDeck(hero, cards, cards.indexOfFirst { it.id == cardId }.coerceAtLeast(0))
        }
    }
    var page by remember(heroId, cardId) { mutableStateOf(-1) }
    var textOnly by remember { mutableStateOf(false) }
    val deck = (loaded.state as? Load.Ready)?.value
    val current = deck?.let { it.cards.getOrNull(if (page < 0) it.initialIndex else page) }
    Screen(
        current?.name ?: "Card",
        actions = {
            if (current?.image != null) {
                IconButton(
                    if (textOnly) "image" else "article",
                    if (textOnly) "Show card image" else "Show text only",
                    onClick = { textOnly = !textOnly },
                )
            }
        },
    ) {
        LoadContent(loaded, "Couldn't load this card") { found ->
            if (found == null) {
                EmptyState("article", "Card not found", "This card isn't in the catalog bundled with this build.")
            } else {
                val index = if (page < 0) found.initialIndex else page
                CardPager(found, index, textOnly, onPage = { page = it }, onShowText = { textOnly = true })
            }
        }
    }
}

@Composable
private fun CardPager(deck: ViewerDeck, index: Int, textOnly: Boolean, onPage: (Int) -> Unit, onShowText: () -> Unit) {
    val card = deck.cards[index]
    var pager by remember { mutableStateOf<HTMLElement?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    fun goTo(target: Int) {
        val element = pager ?: return
        if (target !in deck.cards.indices) return
        val options: dynamic = js("({})")
        options.left = target * element.clientWidth
        options.behavior = if (reducedMotion()) "auto" else "smooth"
        element.asDynamic().scrollTo(options)
    }
    ArrowKeys(onPrevious = { goTo(index - 1) }, onNext = { goTo(index + 1) })
    Div({ classes("goa-pager-head") }) {
        IconButton(
            "chevron_left",
            "Previous card",
            onClick = { goTo(index - 1) },
            extraClass = if (index ==
                0
            ) {
                "hidden"
            } else {
                null
            },
        )
        Div({
            classes("goa-pager-caption")
            attr("aria-live", "polite")
        }) {
            Span({
                classes("t-label-large", "goa-kind", card.color.cssClass)
            }) { Text(card.kindLabel(deck.isHandicap(card))) }
            Span({
                classes("t-label-medium", "c-variant")
            }) { Text("${deck.hero.name} · ${index + 1} of ${deck.cards.size}") }
        }
        IconButton(
            "chevron_right",
            "Next card",
            onClick = { goTo(index + 1) },
            extraClass = if (index == deck.cards.lastIndex) "hidden" else null,
        )
    }
    Div({
        classes("goa-pager")
        if (zoomed) classes("zoomed")
        attr("aria-roledescription", "carousel")
        ref { element ->
            pager = element
            // Opens on the card that was tapped, without animating through the ones before it.
            element.scrollLeft = deck.initialIndex * element.clientWidth.toDouble()
            var settle = 0
            val onScroll: (Event) -> Unit = {
                window.clearTimeout(settle)
                settle = window.setTimeout({
                    val width = element.clientWidth.coerceAtLeast(1)
                    val page = kotlin.math.round(element.scrollLeft / width).toInt().coerceIn(0, deck.cards.lastIndex)
                    onPage(page)
                }, SETTLE_MS)
            }
            element.addEventListener("scroll", onScroll)
            onDispose {
                element.removeEventListener("scroll", onScroll)
                pager = null
            }
        }
    }) {
        deck.cards.forEachIndexed { i, pageCard ->
            Div({
                classes("goa-pager-page")
                attr("role", "group")
                attr("aria-roledescription", "card")
                attr("aria-label", "${i + 1} of ${deck.cards.size}: ${pageCard.name}")
                // Off-screen pages are out of reach for VoiceOver and the keyboard (a swipe still scrolls).
                if (i != index) attr("inert", "")
            }) {
                // Neighbours render too (the swipe shows them); far pages stay empty until reached.
                if (kotlin.math.abs(i - index) <= 1) {
                    CardPage(pageCard, deck.isHandicap(pageCard), textOnly, active = i == index, onZoom = {
                        zoomed = it
                    }, onShowText)
                }
            }
        }
    }
}

@Composable
private fun CardPage(
    card: Card,
    isHandicap: Boolean,
    textOnly: Boolean,
    active: Boolean,
    onZoom: (Boolean) -> Unit,
    onShowText: () -> Unit,
) {
    var failed by remember(card.image) { mutableStateOf(false) }
    val designed = card.image == null || failed
    Div({ classes("goa-card-page") }) {
        if (designed) {
            DesignedCard(card, isHandicap)
        } else if (!textOnly) {
            ZoomableScan(card, isHandicap, active, onZoom, onError = { failed = true })
        }
        if (textOnly || designed) {
            CardDetails(card, isHandicap, includeText = !designed)
        } else {
            // The scan shows its values and text: under it only what the card doesn't print.
            NotOnTheCard(card, isHandicap, onShowText)
        }
    }
}

/** The scan at its own aspect ratio (deck cards portrait, spellbook pages landscape), zoomable. */
@Composable
private fun ZoomableScan(
    card: Card,
    isHandicap: Boolean,
    active: Boolean,
    onZoom: (Boolean) -> Unit,
    onError: () -> Unit,
) {
    val image = card.image ?: return
    var zoomed by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<PinchZoom?>(null) }
    LaunchedEffect(active) { if (!active) zoom?.reset() }
    val spell = card.id.startsWith("${card.heroId}-spell-")
    Div({
        classes("goa-zoom")
        if (spell) classes("spell")
        attr("role", "img")
        attr("aria-label", cardDescription(card, isHandicap))
    }) {
        Img(image, alt = "") {
            attr("width", if (spell) SPELL_WIDTH else CARD_WIDTH)
            attr("height", if (spell) SPELL_HEIGHT else CARD_HEIGHT)
            attr("decoding", "async")
            attr("draggable", "false")
            addEventListener("error") { onError() }
            ref { img ->
                val controller = PinchZoom(img) { now ->
                    zoomed = now
                    onZoom(now)
                }
                zoom = controller
                onDispose {
                    controller.detach()
                    zoom = null
                }
            }
        }
        if (!zoomed) {
            Span({
                classes("goa-zoom-hint")
                attr("aria-hidden", "true")
            }) { Icon("zoom", IconSize.SMALL) }
        }
    }
}

/** Structured card info: actions, values, markers and (optionally) the printed text. */
@Composable
private fun CardDetails(card: Card, isHandicap: Boolean, includeText: Boolean) {
    Div({ classes("goa-card", "goa-card-details", card.color.cssClass) }) {
        // Derived from the frame colour, never printed on the card (rulebook p.20).
        val handicapBasis = if (isHandicap) card.handicapBasis() else null
        if (handicapBasis != null) DetailRow("Handicap") { Span({ classes("t-body-medium") }) { Text(handicapBasis) } }
        DetailRow("Initiative") { Span({ classes("t-title-medium") }) { Text(card.initiative?.toString() ?: "—") } }
        card.primary?.let { primary ->
            DetailRow("Primary action") { IconLabel(primary.token, primary.label, accent = true) }
        }
        if (card.secondary.isNotEmpty()) {
            DetailRow("Secondary") { card.secondary.forEach { IconLabel(it.token, it.label) } }
        }
        val values = card.cardValues()
        if (values.isNotEmpty()) {
            DetailRow("Values") {
                values.forEach { value ->
                    Span({
                        classes("goa-icon-label", "t-body-medium")
                        attr("aria-label", value.spoken)
                    }) {
                        GameIcon(statToken(value.stat), extraClass = "accent")
                        Span({ attr("aria-hidden", "true") }) { Text("${value.stat.label} ${value.printed}") }
                    }
                }
            }
        }
        val markers = listOfNotNull(
            ("ranged" to "Ranged").takeIf { card.ranged },
            card.blockMarkerLabel()?.let { "block" to it },
        )
        if (markers.isNotEmpty()) {
            DetailRow("Markers") {
                markers.forEach { (token, text) -> IconLabel(token, text, secondary = true) }
            }
        }
        card.item?.let { stat -> DetailRow("Item") { IconLabel("item", "+1 ${stat.label}", secondary = true) } }
        if (includeText && card.text.isNotBlank()) {
            ParchmentPanel { CardTextView(card.text, "t-printed") }
        }
    }
}

@Composable
private fun DetailRow(label: String, content: @Composable () -> Unit) {
    Div({ classes("goa-detail-row") }) {
        Span({ classes("goa-detail-label", "t-label-large", "c-variant") }) { Text(label) }
        Div({ classes("goa-detail-value") }) { content() }
    }
}

@Composable
private fun IconLabel(token: String, text: String, accent: Boolean = false, secondary: Boolean = false) {
    Span({
        classes("goa-icon-label", "t-body-medium")
        if (secondary) classes("secondary")
    }) {
        GameIcon(token, extraClass = if (accent) "accent" else null)
        Text(text)
    }
}

private val SecondaryAction.token: String
    get() = when (this) {
        SecondaryAction.MOVEMENT -> "movement"
        SecondaryAction.DEFENSE -> "defense"
        SecondaryAction.HOLD -> "hold"
        SecondaryAction.FAST_TRAVEL -> "fast_travel"
        SecondaryAction.CLEAR -> "clear"
        SecondaryAction.ATTACK -> "attack"
    }

/**
 * Under a card scan: what the card doesn't print or doesn't explain (which basic card a Handicap
 * card stands in for, what its item icon gives, p.16), and the way to read it as text.
 */
@Composable
private fun NotOnTheCard(card: Card, isHandicap: Boolean, onShowText: () -> Unit) {
    Div({ classes("goa-not-on-card") }) {
        if (isHandicap) card.handicapBasis()?.let { P({ classes("t-body-medium") }) { Text(it) } }
        card.item?.let { stat ->
            Div({ classes("goa-item-note") }) {
                Img("official/icons/item-${stat.name.lowercase()}.webp", alt = "") {
                    attr("width", "40")
                    attr("height", "30")
                    attr("loading", "lazy")
                }
                P({ classes("t-body-medium", "c-variant") }) {
                    Text("As an item: +1 ${stat.label} (the icon at the foot of the card, p.16)")
                }
            }
        }
        GoaButton("Read the card as text", onShowText, ButtonKind.OUTLINED, icon = "article", block = true)
    }
}

/** Our own rendering of a card for when no scan is bundled (or it can't load): frame, values, text. */
@Composable
private fun DesignedCard(card: Card, isHandicap: Boolean) {
    Div({
        classes("goa-designed-card", card.color.cssClass)
        attr("role", "img")
        attr("aria-label", cardDescription(card, isHandicap))
    }) {
        Div({
            classes("goa-designed-head")
            attr("aria-hidden", "true")
        }) {
            Div {
                P({
                    classes("t-label-medium", "goa-kind", card.color.cssClass)
                }) { Text(card.kindLabel(isHandicap).uppercase()) }
                P({ classes("t-headline-small") }) { Text(card.name) }
            }
            InitiativeBadge(card.initiative, card.color, large = true)
        }
        Div({
            classes("goa-designed-values")
            attr("aria-hidden", "true")
        }) {
            card.primary?.let {
                Span({ classes("goa-designed-primary") }) { GameIcon(it.token, extraClass = "accent") }
            }
            card.cardValues().forEach { value ->
                Span({ classes("goa-value", "t-headline-small") }) {
                    GameIcon(statToken(value.stat))
                    Text(value.printed)
                }
            }
        }
        if (card.text.isNotBlank()) {
            Div({
                classes("goa-designed-text")
                attr("aria-hidden", "true")
            }) { CardTextView(card.text, "t-body-large") }
        }
    }
}

/** Left/right arrow keys page through the cards (a keyboard on an iPad or a desktop). */
@Composable
private fun ArrowKeys(onPrevious: () -> Unit, onNext: () -> Unit) {
    val previous by androidx.compose.runtime.rememberUpdatedState(onPrevious)
    val next by androidx.compose.runtime.rememberUpdatedState(onNext)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val key = (event as? KeyboardEvent)?.key
            val typing = (document.activeElement as? HTMLElement)?.tagName in setOf("INPUT", "TEXTAREA")
            if (!typing && key == "ArrowLeft") previous()
            if (!typing && key == "ArrowRight") next()
        }
        document.addEventListener("keydown", listener)
        onDispose { document.removeEventListener("keydown", listener) }
    }
}

private fun reducedMotion(): Boolean = window.matchMedia("(prefers-reduced-motion: reduce)").matches

/** The swipe has stopped when no scroll event came for this long. */
private const val SETTLE_MS = 90
