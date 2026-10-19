package dev.quinton.goa.hub.heroes

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.presentation.catalog.GameIconLabels
import dev.quinton.goa.core.presentation.catalog.blockMarkerLabel
import dev.quinton.goa.core.presentation.catalog.complexityName
import dev.quinton.goa.core.presentation.catalog.handicapBasis
import dev.quinton.goa.core.presentation.catalog.label
import dev.quinton.goa.core.presentation.catalog.printedValues
import dev.quinton.goa.core.presentation.catalog.spokenIconLabel
import dev.quinton.goa.core.presentation.catalog.spokenSummary
import dev.quinton.goa.core.presentation.catalog.startsIn
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.icon
import kotlinx.html.FlowContent
import kotlinx.html.HtmlBlockTag
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.i
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong

/*
 * Catalog pieces shared by the Heroes, Rules and Card trivia pages: card text with its game icons,
 * card tiles, portraits and the Unverified badge. Styles: static/heroes/heroes.css. Everything
 * shown comes from the catalog through :core:presentation; kotlinx.html escapes all text.
 */

/** Stylesheet every page showing catalog pieces loads. */
const val CATALOG_CSS = "heroes/heroes.css"

/** The glyph file (under `static/heroes/symbols/`) of each game icon token, as the phone draws it. */
internal object GameIconFiles {
    val byToken: Map<String, String> = mapOf(
        "attack" to "sword",
        "defense" to "shield_fill",
        "initiative" to "bolt_fill",
        "movement" to "directions_run_fill",
        "range" to "my_location_fill",
        "radius" to "trip_origin_fill",
        "skill" to "auto_awesome_fill",
        "coin" to "monetization_on_fill",
        "hold" to "pan_tool_fill",
        "fast_travel" to "fast_forward_fill",
        "clear" to "cleaning_services_fill",
        "ranged" to "north_east",
        "block" to "priority_high",
        "item" to "backpack_fill",
        "ultimate" to "workspace_premium_fill",
        "minion" to "person",
        "heavy_minion" to "person_fill",
        "spawn" to "add_location_alt_fill",
        "life" to "favorite_fill",
        "wave" to "waves",
        "tiebreaker_blue" to "toll_fill",
        "tiebreaker_orange" to "toll",
        "marker_bounty" to "paid_fill",
        "marker_poison" to "science_fill",
        "rune_anvil" to "hardware_fill",
        "rune_axe" to "carpenter_fill",
        "rune_bird" to "flutter_dash_fill",
        "rune_horn" to "campaign_fill",
        "rune_anvil_marker" to "hardware",
        "rune_axe_marker" to "carpenter",
        "rune_bird_marker" to "flutter_dash",
        "rune_horn_marker" to "campaign",
        "token_barrier" to "fence_fill",
        "token_blast" to "local_fire_department_fill",
        "token_dud" to "do_not_disturb_on_fill",
        "token_familiar" to "pets",
        "token_glitch" to "broken_image_fill",
        "token_grenade" to "adjust",
        "token_ice" to "ac_unit",
        "token_illusion" to "auto_fix_high_fill",
        "token_magma" to "volcano_fill",
        "token_rock" to "landscape_fill",
        "token_smoke_bomb" to "cloud_fill",
        "token_totem" to "foundation_fill",
        "token_tree" to "park_fill",
        "token_zombie" to "sentiment_very_dissatisfied_fill",
    )
}

/** The token of a card value's glyph. */
internal val Stat.iconToken: String
    get() = when (this) {
        Stat.ATTACK -> "attack"
        Stat.DEFENSE -> "defense"
        Stat.INITIATIVE -> "initiative"
        Stat.MOVEMENT -> "movement"
        Stat.RANGE -> "range"
        Stat.RADIUS -> "radius"
    }

/** The token of a primary action's glyph (Defense/Skill shows the shield, as on the phone). */
internal val PrimaryAction.iconToken: String
    get() = when (this) {
        PrimaryAction.ATTACK -> "attack"
        PrimaryAction.SKILL -> "skill"
        PrimaryAction.DEFENSE, PrimaryAction.DEFENSE_SKILL -> "defense"
        PrimaryAction.MOVEMENT -> "movement"
    }

/** CSS class of a card colour (`cc-red` …): the accent of tiles and dots. */
internal val CardColor.css: String get() = "cc-" + name.lowercase()

/**
 * A game icon glyph tinted with the text colour. [label] null = decorative (the words next to it
 * say it); otherwise it is the icon's accessible name.
 */
fun FlowContent.gameIcon(token: String, label: String? = null) {
    span("gi gi-$token") {
        if (label.isNullOrBlank()) {
            attributes["aria-hidden"] = "true"
        } else {
            attributes["role"] = "img"
            attributes["aria-label"] = label
        }
    }
}

/**
 * Card text as printed ([CardText] markup): icons inline (named for screen readers, once when the
 * printed word follows), reminder text in italics, bold keywords bold, paragraph breaks as breaks.
 * Unknown icon names fall back to their words, never a blank. All text is escaped.
 */
fun FlowContent.cardText(markup: String, classes: String = "card-text") {
    p(classes) { segments(CardText.parse(markup)) }
}

private fun HtmlBlockTag.segments(segments: List<CardText.Segment>) {
    segments.forEachIndexed { index, segment ->
        when (segment) {
            is CardText.Segment.Text -> +segment.value

            is CardText.Segment.Icon -> {
                val label = GameIconLabels.forToken(segment.name)
                if (label == null || segment.name !in GameIconFiles.byToken) {
                    +segment.name.replace('_', ' ')
                } else {
                    gameIcon(segment.name, spokenIconLabel(label, segments.getOrNull(index + 1)).trim())
                }
            }

            is CardText.Segment.Reminder -> i("reminder") { +"(${segment.value})" }

            is CardText.Segment.Strong -> strong { segments(segment.segments) }

            CardText.Segment.Break -> br()
        }
    }
}

/** The "Unverified" pill (data not yet cross-checked against a second source, DOMAIN.md). */
fun FlowContent.unverifiedBadge(compact: Boolean = false) {
    span(if (compact) "badge-pill unverified compact" else "badge-pill unverified") {
        attributes["title"] = "Unverified data"
        icon(Icons.ERROR)
        span { +"Unverified" }
    }
}

/** The "Verified" mark on a hero page. */
fun FlowContent.verifiedBadge() {
    span("badge-pill verified") {
        icon("verified")
        span { +"Verified" }
    }
}

/**
 * A hero's portrait over their sigil (shown while loading and without art). Lazy-loaded with its
 * intrinsic size so the grid doesn't jump; decorative ([alt] empty) when a label names the hero.
 */
fun FlowContent.heroPortrait(
    heroId: String,
    portrait: String?,
    classes: String,
    alt: String = "",
    eager: Boolean = false,
    size: PortraitSize = PortraitSize.SMALL,
) {
    span("portrait $classes") {
        span("sigil ${sigilClass(heroId)}") { attributes["aria-hidden"] = "true" }
        if (portrait != null) {
            val src = when (size) {
                PortraitSize.SMALL -> HubCatalog.thumbUrl(portrait, HubCatalog.THUMB_SMALL)
                PortraitSize.GRID -> HubCatalog.thumbUrl(portrait, HubCatalog.THUMB_GRID)
                PortraitSize.FULL -> HubCatalog.url(portrait)
            }
            img(src = src, alt = alt) {
                if (size == PortraitSize.GRID) {
                    // A phone at 3× picks the full image; 2× and smaller screens the lighter copy.
                    attributes["srcset"] = "$src ${HubCatalog.THUMB_GRID}w, ${HubCatalog.url(portrait)} 512w"
                    attributes["sizes"] = "(min-width: 720px) 220px, 48vw"
                }
                attributes["width"] = PORTRAIT_WIDTH
                attributes["height"] = PORTRAIT_HEIGHT
                attributes["loading"] = if (eager) "eager" else "lazy"
                attributes["decoding"] = "async"
            }
        }
    }
}

/** The class that draws [heroId]'s sigil (one line per hero in `heroes.css`, checked by a test). */
fun sigilClass(heroId: String): String = "sigil-" + heroId.filter { it.isLetterOrDigit() || it == '-' }

/** Which copy of a portrait a place needs: avatars and rows, the hero grid, or the hero page. */
enum class PortraitSize { SMALL, GRID, FULL }

/** Bundled portraits are 512 × 468. */
private const val PORTRAIT_WIDTH = "512"
private const val PORTRAIT_HEIGHT = "468"

/** Bundled card scans are 546 × 768 (spellbook pages may differ; CSS keeps their ratio). */
const val CARD_WIDTH = "546"
const val CARD_HEIGHT = "768"

/** ★★★ for a complexity, read as "Complexity 3 of 4, Very Complex". */
fun FlowContent.complexityStars(level: Int, classes: String = "stars") {
    span(classes) {
        attributes["role"] = "img"
        attributes["aria-label"] = "Complexity $level of 4" + (complexityName(level)?.let { ", $it" } ?: "")
        repeat(level.coerceIn(0, MAX_STARS)) { span("icon icon-star_fill") { attributes["aria-hidden"] = "true" } }
    }
}

private const val MAX_STARS = 8

/** The initiative medallion; nothing without an initiative. */
fun FlowContent.initiativeBadge(initiative: Int?, color: CardColor, large: Boolean = false) {
    if (initiative == null) return
    span(listOfNotNull("initiative", color.css, "large".takeIf { large }).joinToString(" ")) {
        attributes["role"] = "img"
        attributes["aria-label"] = "Initiative $initiative"
        +initiative.toString()
    }
}

/** A value with its glyph: "⚔ 4+", read "Attack 4 plus". */
fun FlowContent.valueChip(stat: Stat, printed: String, spoken: String) {
    span("value-chip") {
        attributes["role"] = "img"
        attributes["aria-label"] = spoken
        gameIcon(stat.iconToken)
        span { +printed }
    }
}

/** The viewer URL of a card. */
fun cardHref(heroId: String, cardId: String): String = "/heroes/$heroId/cards/$cardId"

/** A link to a card: its viewer URL, scrolled to the card (so the viewer opens there without JS too). */
fun cardLink(heroId: String, cardId: String): String = "${cardHref(heroId, cardId)}#card-$cardId"

/**
 * A deck tile, as on the phone: colour accent, name, initiative, primary action, values and
 * markers; [showText] adds the card text (Ultimate tiles, spellbook pages). A Handicap tile says
 * which basic card it is a weaker version of, labelled as derived from the frame colour.
 */
fun FlowContent.cardTile(card: Card, isHandicap: Boolean = false, showText: Boolean = false) {
    a(cardLink(card.heroId, card.id), classes = "card-tile ${card.color.css}") {
        attributes["aria-label"] = card.spokenSummary(isHandicap)
        div("tile-head") {
            span("tile-name") { +card.name }
            initiativeBadge(card.initiative, card.color)
        }
        card.primary?.let { primary ->
            div("tile-primary") {
                gameIcon(primary.iconToken)
                span { +GameIconLabels.forPrimary(primary) }
            }
        }
        if (isHandicap) card.handicapBasis()?.let { div("tile-handicap") { +it } }
        if (showText && card.text.isNotBlank()) cardText(card.text, "card-text tile-text")
        div("tile-markers") {
            card.printedValues().forEach { valueChip(it.stat, it.printed, it.spoken) }
            if (card.ranged) marker("ranged", GameIconLabels.RANGED)
            card.blockMarkerLabel()?.let { marker("block", it) }
            card.item?.let { marker("item", "+1 ${it.label}") }
            card.startsIn()?.let { marker(null, it) }
        }
    }
}

private fun FlowContent.marker(token: String?, text: String) {
    span("marker") {
        token?.let { gameIcon(it) }
        span { +text }
    }
}

/** A card colour dot with its name for screen readers ("Red card"). */
fun FlowContent.colorDot(color: CardColor) {
    span("color-dot ${color.css}") {
        attributes["role"] = "img"
        attributes["aria-label"] = "${color.label} card"
    }
}
