package dev.quinton.goa.web.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.catalog.CardIcons
import dev.quinton.goa.core.domain.catalog.complexityName
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.web.data.Thumbnails
import dev.quinton.goa.web.ui.IconMarkup
import dev.quinton.goa.web.ui.OfficialImg
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Em
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/*
 * The catalog's building blocks on the web (feature:catalog's CatalogComponents and the design
 * system's game components): card text with inline game icons, stars, pills, badges, hero art.
 * Colours come from goa.css tokens through classes (`cc-red` sets the card colour `--cc`).
 */

/** The CSS class that sets `--cc` to the colour of a [CardColor] (goa.css, W1 catalog). */
val CardColor.cssClass: String get() = "cc-${name.lowercase()}"

/**
 * An inline game icon (`game_<token>` from the SVG files) sized to the text around it. [spoken] is
 * what VoiceOver reads; blank = nothing (the printed word follows), null = decorative.
 */
@Composable
fun GameIcon(token: String, spoken: String? = null, extraClass: String? = null) {
    Span({
        classes("goa-gameicon")
        extraClass?.let { classes(it) }
        if (spoken.isNullOrBlank()) {
            attr("aria-hidden", "true")
        } else {
            attr("role", "img")
            attr("aria-label", spoken)
        }
        ref { element ->
            element.innerHTML = IconMarkup["game_$token"] ?: ""
            onDispose { }
        }
    })
}

/**
 * Printed card text (the catalog's markup, [CardText]): icons inline, read as their words (as on the
 * phone, never twice when the printed word follows), reminders in italics, bold keywords bold,
 * paragraphs as paragraphs. [clampLines] keeps it to that many lines (search results).
 */
@Composable
fun CardTextView(text: String, extraClass: String = "t-body-medium", clampLines: Int? = null) {
    val paragraphs = remember(text) { paragraphsOf(CardText.parse(text)) }
    if (clampLines != null) {
        // One block (with line breaks) so the line clamp counts every line.
        Div({
            classes("goa-cardtext", "clamped", extraClass)
            style { property("-webkit-line-clamp", clampLines.toString()) }
        }) {
            paragraphs.forEachIndexed { i, paragraph ->
                if (i > 0) Br()
                Segments(paragraph)
            }
        }
    } else {
        Div({ classes("goa-cardtext", extraClass) }) {
            paragraphs.forEach { paragraph -> P { Segments(paragraph) } }
        }
    }
}

private fun paragraphsOf(segments: List<CardText.Segment>): List<List<CardText.Segment>> {
    val out = mutableListOf(mutableListOf<CardText.Segment>())
    segments.forEach {
        if (it ==
            CardText.Segment.Break
        ) {
            out += mutableListOf<CardText.Segment>()
        } else {
            out.last() += it
        }
    }
    return out.filter { it.isNotEmpty() }
}

@Composable
private fun Segments(segments: List<CardText.Segment>) {
    segments.forEachIndexed { index, segment ->
        when (segment) {
            is CardText.Segment.Text -> Text(segment.value)

            is CardText.Segment.Icon -> {
                val label = CardIcons.label(segment.name)
                if (label == null) {
                    Text(segment.name.replace('_', ' '))
                } else {
                    GameIcon(segment.name, CardIcons.spokenLabel(label, segments.getOrNull(index + 1)))
                }
            }

            is CardText.Segment.Reminder -> Em({ classes("goa-reminder") }) {
                Text("(")
                Segments(CardText.parse(segment.value))
                Text(")")
            }

            is CardText.Segment.Strong -> B { Segments(segment.segments) }

            CardText.Segment.Break -> Br()
        }
    }
}

/** Complexity ★★☆☆ (rulebook 1..4), read as "Complexity 2 of 4, Complex"; [showName] adds the name. */
@Composable
fun ComplexityStars(complexity: Int?, showName: Boolean = false, max: Int = 4) {
    if (complexity == null) return
    val name = complexityName(complexity)
    Span({
        classes("goa-stars")
        attr("role", "img")
        attr("aria-label", "Complexity $complexity of $max" + (name?.let { ", $it" } ?: ""))
    }) {
        repeat(max) { i ->
            Span({
                classes("goa-star")
                if (i < complexity) classes("on")
                ref { element ->
                    element.innerHTML = IconMarkup[if (i < complexity) "star" else "star_outline"] ?: ""
                    onDispose { }
                }
            })
        }
        if (showName && name != null) Span({ classes("goa-stars-name", "t-label-large") }) { Text(name) }
    }
}

/** Small engraved pill for product names and other metadata; [primary] tints it bronze. */
@Composable
fun MetaPill(text: String, primary: Boolean = false) {
    Span({
        classes("goa-pill", "t-label-small")
        if (primary) classes("primary")
    }) { Text(text) }
}

/** "Unverified" — data not yet cross-checked against a second source (DOMAIN.md). */
@Composable
fun UnverifiedBadge(compact: Boolean = false) {
    Span({
        classes("goa-unverified", "t-label-small")
        if (compact) classes("compact")
        attr("role", "img")
        attr("aria-label", "Unverified data, may contain errors")
    }) {
        Span({
            classes("goa-unverified-icon")
            attr("aria-hidden", "true")
            ref { element ->
                element.innerHTML = IconMarkup["unverified"] ?: ""
                onDispose { }
            }
        })
        Span({ attr("aria-hidden", "true") }) { Text("Unverified") }
    }
}

/** The initiative medallion in the card's colour; read as "Initiative 8". */
@Composable
fun InitiativeBadge(initiative: Int?, color: CardColor, large: Boolean = false) {
    if (initiative == null) return
    Span({
        classes("goa-initiative", color.cssClass, if (large) "t-title-large" else "t-title-small")
        if (large) classes("large")
        attr("role", "img")
        attr("aria-label", "Initiative $initiative")
    }) { Text(initiative.toString()) }
}

/**
 * A hero's emblem: the sigil drawn for this app (`sigils/<id>.svg`, tinted bronze), shown while the
 * official art loads, when there is none, and when it can't load (offline before it was cached).
 */
@Composable
fun HeroSigil(heroId: String) {
    Span({
        classes("goa-sigil")
        attr("aria-hidden", "true")
        style { property("--sigil", "url(\"sigils/$heroId.svg\")") }
    })
}

/**
 * The hero's portrait over its sigil: the sigil stays until the image has loaded, and is all that
 * shows if it fails. [eager] for the one image a screen opens with (the hero page header).
 */
@Composable
fun HeroArt(hero: Hero, extraClass: String, alt: String = "", eager: Boolean = false) {
    var loaded by remember(hero.portrait) { mutableStateOf(false) }
    var failed by remember(hero.portrait) { mutableStateOf(false) }
    Div({
        classes("goa-hero-art", extraClass)
        if (loaded) classes("loaded")
        if (alt.isNotEmpty()) {
            attr("role", "img")
            attr("aria-label", alt)
        }
    }) {
        if (!loaded) HeroSigil(hero.id)
        val portrait = hero.portrait
        if (portrait != null && !failed) {
            Img(portrait, alt = "") {
                attr("width", PORTRAIT_WIDTH)
                attr("height", PORTRAIT_HEIGHT)
                attr("decoding", "async")
                if (!eager) attr("loading", "lazy")
                addEventListener("load") { loaded = true }
                addEventListener("error") { failed = true }
            }
        }
    }
}

/** The hero's deck symbol (the emblem on their card backs); decorative unless [alt] is set. */
@Composable
fun HeroSymbol(hero: Hero, extraClass: String, alt: String = "") {
    val symbol = hero.symbol ?: return
    var failed by remember(symbol) { mutableStateOf(false) }
    if (failed) return
    // Its size on screen (goa.css): 30 px on a grid tile, 24 px in a list row, up to 90 px on the hero page.
    val sizes = when (extraClass) {
        "tile" -> "30px"
        "row" -> "24px"
        else -> "90px"
    }
    OfficialImg(symbol, Thumbnails.symbolSrcset(symbol), sizes, alt = alt, onFailed = { failed = true }) {
        classes("goa-hero-symbol", extraClass)
        attr("width", SYMBOL_SIZE)
        attr("height", SYMBOL_SIZE)
        attr("decoding", "async")
        attr("loading", "lazy")
    }
}

private const val PORTRAIT_WIDTH = "512"
private const val PORTRAIT_HEIGHT = "468"
private const val SYMBOL_SIZE = "250"
