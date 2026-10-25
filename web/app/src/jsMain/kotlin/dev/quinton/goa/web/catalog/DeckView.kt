package dev.quinton.goa.web.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.catalog.DeckGroup
import dev.quinton.goa.core.domain.catalog.DeckGroupKind
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.SPELLBOOK_NOTE
import dev.quinton.goa.core.domain.catalog.blockMarkerLabel
import dev.quinton.goa.core.domain.catalog.cardDescription
import dev.quinton.goa.core.domain.catalog.cardValues
import dev.quinton.goa.core.domain.catalog.extraRowTitle
import dev.quinton.goa.core.domain.catalog.handicapBasis
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.multipleGoldNote
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.core.domain.catalog.spokenSummary
import dev.quinton.goa.core.domain.catalog.startsIn
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.RouteLink
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** The deck in rulebook order: Basic, Red, Green, Blue, Ultimate (and anything unexpected). */
@Composable
internal fun DeckSections(hero: Hero, deck: DeckLayout) {
    Section({ attr("aria-label", "Deck") }) {
        SectionTitle("Deck", trailing = plural(hero.cards.size, "card"))
        deck.groups.forEach { group -> DeckGroupSection(hero, group) }
    }
}

@Composable
private fun DeckGroupSection(hero: Hero, group: DeckGroup) {
    Div({ classes("goa-deck-group") }) {
        H3({ classes("goa-deck-group-title", "t-title-medium") }) {
            Span({
                classes("goa-deck-dot", group.kind.color.cssClass)
                attr("aria-hidden", "true")
            })
            Text(group.kind.title)
            Span({ classes("t-label-medium", "c-variant") }) { Text("${group.cards.size}") }
        }
        if (group.kind == DeckGroupKind.BASIC) {
            multipleGoldNote(group.cards)?.let { P({ classes("t-body-small", "c-variant") }) { Text(it) } }
        }
        // The cards themselves, in tier order (I, II A, II B, III A, III B): each scan prints its
        // tier numeral, so the grid doesn't repeat it.
        DeckCardGrid(hero, group.cards)
    }
}

private val DeckGroupKind.color: CardColor
    get() = when (this) {
        DeckGroupKind.BASIC -> CardColor.GOLD
        DeckGroupKind.RED -> CardColor.RED
        DeckGroupKind.GREEN -> CardColor.GREEN
        DeckGroupKind.BLUE -> CardColor.BLUE
        DeckGroupKind.ULTIMATE -> CardColor.PURPLE
        DeckGroupKind.OTHER -> CardColor.OTHER
    }

/** The hero's extra cards by tier; titled "Spellbook" for Gydion's spell cards. */
@Composable
internal fun ExtrasSection(hero: Hero, deck: DeckLayout) {
    if (deck.extras.isEmpty()) return
    val spellbook = deck.extrasAreSpellbook
    Section {
        SectionTitle(if (spellbook) "Spellbook" else "Extras", trailing = "${deck.extras.size} cards")
        if (spellbook) P({ classes("t-body-small", "c-variant") }) { Text(SPELLBOOK_NOTE) }
        deck.extraRows.forEach { row ->
            H3({ classes("goa-extra-row-title", "t-label-large", "c-variant") }) { Text(extraRowTitle(row)) }
            DeckCardGrid(hero, row.cards, spell = spellbook)
        }
    }
}

/**
 * The official card scans in a grid: as many columns as fit (three on a phone held upright, a
 * colour's five in a row on a wider screen), each card with its name under it while the scan is too
 * small to read it (a container query). The scan is the card; VoiceOver hears its full summary and
 * text, and a tap opens the card viewer. A card without a scan (or one that can't load) shows the
 * designed tile instead.
 */
@Composable
internal fun DeckCardGrid(hero: Hero, cards: List<Card>, isHandicap: Boolean = false, spell: Boolean = false) {
    Div({
        classes("goa-deck-grid")
        if (spell) classes("spell")
        attr("role", "list")
    }) {
        cards.forEach { card -> DeckCard(hero, card, isHandicap, spell) }
    }
}

@Composable
private fun DeckCard(hero: Hero, card: Card, isHandicap: Boolean, spell: Boolean) {
    var failed by remember(card.image) { mutableStateOf(false) }
    val image = card.image?.takeUnless { failed }
    Div({
        classes("goa-deck-card")
        attr("role", "listitem")
    }) {
        RouteLink(Route.CardViewer(hero.id, card.id), {
            classes(if (image != null) "goa-scan" else "goa-card-tile", card.color.cssClass)
            attr("aria-label", if (image != null) cardDescription(card, isHandicap) else card.spokenSummary(isHandicap))
        }) {
            if (image != null) {
                Img(image, alt = "") {
                    attr("width", if (spell) SPELL_WIDTH else CARD_WIDTH)
                    attr("height", if (spell) SPELL_HEIGHT else CARD_HEIGHT)
                    attr("loading", "lazy")
                    attr("decoding", "async")
                    addEventListener("error") { failed = true }
                }
            } else {
                CardTileContent(card, isHandicap)
            }
        }
        if (image != null) {
            // A small card's printed name is too small to read, so it is repeated under it; anything the
            // card doesn't print (where a basic card starts, what a Handicap card stands in for) follows.
            Span({
                classes("goa-deck-card-name", "t-label-medium")
                attr("aria-hidden", "true")
            }) { Text(card.name) }
            val note = (if (isHandicap) card.handicapBasis() else null) ?: card.startsIn()
            note?.let {
                Span({
                    classes("goa-deck-card-note", "t-label-small")
                    attr("aria-hidden", "true")
                }) { Text(it) }
            }
        }
    }
}

/**
 * The designed tile for a card without a scan: colour accent, name, initiative, primary action,
 * values and markers; a Handicap tile says which basic card it is a weaker version of (derived from
 * the frame colour, the card prints nothing about it).
 */
@Composable
private fun CardTileContent(card: Card, isHandicap: Boolean) {
    Div({
        classes("goa-card-tile-body")
        attr("aria-hidden", "true")
    }) {
        Div({ classes("goa-card-tile-head") }) {
            Span({ classes("t-title-small") }) { Text(card.name) }
            InitiativeBadge(card.initiative, card.color)
        }
        card.primary?.let { primary ->
            Span({ classes("goa-card-tile-action", "t-label-medium", "c-variant") }) {
                GameIcon(primary.token, extraClass = "accent")
                Text(primary.label)
            }
        }
        if (isHandicap) card.handicapBasis()?.let { Span({ classes("t-label-medium", "accent-text") }) { Text(it) } }
        Div({ classes("goa-card-tile-values") }) {
            card.cardValues().forEach { value ->
                Span({ classes("goa-value", "t-label-large") }) {
                    GameIcon(statToken(value.stat))
                    Text(value.printed)
                }
            }
            if (card.ranged) Marker("ranged", "Ranged")
            card.blockMarkerLabel()?.let { Marker("block", it) }
            card.item?.let { Marker("item", "+1 ${it.label}") }
            card.startsIn()?.let { Marker(null, it) }
        }
    }
}

@Composable
internal fun Marker(token: String?, text: String) {
    Span({ classes("goa-marker", "t-label-small") }) {
        token?.let { GameIcon(it) }
        Text(text)
    }
}

/** The inline icon token for a primary action (Defense / Skill shows the shield, as on the phone). */
internal val PrimaryAction.token: String
    get() = when (this) {
        PrimaryAction.ATTACK -> "attack"
        PrimaryAction.SKILL -> "skill"
        PrimaryAction.DEFENSE, PrimaryAction.DEFENSE_SKILL -> "defense"
        PrimaryAction.MOVEMENT -> "movement"
    }

/** A deck card scan's size in pixels (546 × 768), and a spellbook page's (landscape, 737 × 510). */
internal const val CARD_WIDTH = "546"
internal const val CARD_HEIGHT = "768"
internal const val SPELL_WIDTH = "737"
internal const val SPELL_HEIGHT = "510"
