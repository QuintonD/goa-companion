package dev.quinton.goa.hub.heroes

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.presentation.catalog.CardViewerUiState
import dev.quinton.goa.core.presentation.catalog.DeckGroup
import dev.quinton.goa.core.presentation.catalog.DeckGroupKind
import dev.quinton.goa.core.presentation.catalog.DeckLayout
import dev.quinton.goa.core.presentation.catalog.GameIconLabels
import dev.quinton.goa.core.presentation.catalog.HANDICAP_NOTE
import dev.quinton.goa.core.presentation.catalog.HeroDetailUiState
import dev.quinton.goa.core.presentation.catalog.HeroPersonalStats
import dev.quinton.goa.core.presentation.catalog.NO_RULINGS
import dev.quinton.goa.core.presentation.catalog.SOURCES_FOOTER
import dev.quinton.goa.core.presentation.catalog.SPELLBOOK_NOTE
import dev.quinton.goa.core.presentation.catalog.STAT_SCALE
import dev.quinton.goa.core.presentation.catalog.StatSegment
import dev.quinton.goa.core.presentation.catalog.aliasLine
import dev.quinton.goa.core.presentation.catalog.attackProfile
import dev.quinton.goa.core.presentation.catalog.bars
import dev.quinton.goa.core.presentation.catalog.blockMarkerLabel
import dev.quinton.goa.core.presentation.catalog.complexityName
import dev.quinton.goa.core.presentation.catalog.extraRowTitle
import dev.quinton.goa.core.presentation.catalog.handicapBasis
import dev.quinton.goa.core.presentation.catalog.handicapConflict
import dev.quinton.goa.core.presentation.catalog.kindLabel
import dev.quinton.goa.core.presentation.catalog.label
import dev.quinton.goa.core.presentation.catalog.learnNumbersLabel
import dev.quinton.goa.core.presentation.catalog.multipleGoldNote
import dev.quinton.goa.core.presentation.catalog.percent
import dev.quinton.goa.core.presentation.catalog.plural
import dev.quinton.goa.core.presentation.catalog.printedValues
import dev.quinton.goa.core.presentation.catalog.recordLine
import dev.quinton.goa.core.presentation.catalog.sourceMeta
import dev.quinton.goa.core.presentation.catalog.tierNumeral
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.playerAvatar
import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.blockQuote
import kotlinx.html.dd
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.figure
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary

/*
 * A hero's page (the phone's HeroDetailScreen) and the card viewer. Section order, labels and
 * notes come from :core:presentation, so both apps say the same thing.
 */

internal fun FlowContent.heroDetail(state: HeroDetailUiState.Ready) {
    val hero = state.hero
    heroHeader(hero, state.productName)
    section("hero-section") {
        sectionTitle("Profile")
        statBars(hero)
    }
    section("hero-section") {
        sectionTitle("Your table")
        personalStats(state.stats)
    }
    section("hero-section deck") {
        sectionTitle("Deck", plural(hero.cards.size, "card"))
        state.deck.groups.forEach { deckGroup(it) }
        linkButton(learnNumbersLabel(hero.name), "/trivia?hero=${hero.id}", ButtonStyle.TONAL, icon = "quiz")
    }
    state.deck.handicap?.let { handicap ->
        section("hero-section") {
            sectionTitle("Handicap")
            p("note") { +HANDICAP_NOTE }
            handicapConflict(hero, handicap)?.let { p("note conflict") { +it } }
            div("tile-grid cols-2") { cardTile(handicap, isHandicap = true) }
        }
    }
    if (state.deck.extras.isNotEmpty()) extrasSection(state.deck)
    section("hero-section") {
        sectionTitle("FAQ rulings")
        if (hero.faq.isEmpty()) {
            p("muted") { +NO_RULINGS }
        } else {
            hero.faq.forEach { ruling ->
                details("faq") {
                    summary {
                        icon("help")
                        span("faq-question") { +ruling.question }
                        icon("expand_more", "faq-chevron")
                    }
                    div("faq-answer") {
                        p { +ruling.answer }
                        p("faq-source") { +"Source: ${ruling.source}" }
                    }
                }
            }
        }
    }
    hero.lore?.takeIf { it.isNotBlank() }?.let { lore ->
        section("hero-section") {
            sectionTitle("Lore")
            blockQuote("lore") { +lore }
        }
    }
    section("hero-section") {
        sectionTitle("Sources")
        if (hero.sources.isEmpty()) p("muted") { +"No sources recorded." }
        hero.sources.forEach { source ->
            div("source") {
                p { +source.name }
                sourceMeta(source.url, source.retrieved).takeIf { it.isNotEmpty() }?.let { p("muted small") { +it } }
            }
        }
        p("muted small") { +SOURCES_FOOTER }
    }
}

private fun FlowContent.sectionTitle(title: String, trailing: String? = null) {
    h2("hero-section-title") {
        span { +title }
        trailing?.let { span("count") { +it } }
    }
}

private fun FlowContent.heroHeader(hero: Hero, productName: String) {
    div("hero-header") {
        heroPortrait(hero.id, hero.portrait, "hero-art", alt = "${hero.name} artwork", eager = true)
        div("hero-heading") {
            h1("hero-name") { +hero.name }
            p("hero-title") { +hero.title }
            hero.aliasLine?.let { p("muted small") { +it } }
            div("pills") {
                hero.complexity?.let { level ->
                    span("complexity") {
                        complexityStars(level)
                        complexityName(level)?.let { span { +it } }
                    }
                }
                span("meta-pill") { +productName }
                // App-derived from the Ranged subtype of the deck's Attack cards (DOMAIN.md), kept apart
                // from the official role chips (the rulebook calls no-range attacks "Non-Ranged", p.13).
                hero.attackProfile().pill?.let { span("meta-pill") { +it } }
                if (hero.printing != 2) span("meta-pill") { +"Printing ${hero.printing}" }
                if (hero.verified) verifiedBadge() else unverifiedBadge()
            }
            if (hero.roles.isNotEmpty()) {
                div("pills roles") { hero.roles.forEach { span("role-pill") { +it.label } } }
            }
        }
    }
}

/** Hero Profile bars from 1 ("Very poor") to 8 ("Exceptional"): solid to the base, outlined to the max. */
private fun FlowContent.statBars(hero: Hero) {
    div("stat-bars") {
        hero.stats.bars().forEach { bar ->
            div("stat-bar") {
                attributes["role"] = "img"
                attributes["aria-label"] = bar.spoken
                div("stat-head") {
                    gameIcon(bar.stat.iconToken)
                    span("stat-name") { +bar.stat.label }
                    span("stat-value") { +bar.rangeText }
                }
                div("segments") {
                    bar.segments.forEach { segment ->
                        span(
                            when (segment) {
                                StatSegment.BASE -> "segment base"
                                StatSegment.CONDITIONAL -> "segment conditional"
                                StatSegment.EMPTY -> "segment"
                            },
                        )
                    }
                }
            }
        }
        div("stat-scale") {
            attributes["aria-hidden"] = "true"
            span { +"1 · Very poor" }
            span { +"Exceptional · $STAT_SCALE" }
        }
        div("stat-legend") {
            attributes["aria-hidden"] = "true"
            span("swatch base")
            span { +"Base" }
            span("swatch conditional")
            span { +"Conditional (level-ups, card text)" }
        }
    }
}

private fun FlowContent.personalStats(stats: HeroPersonalStats) {
    div("card stats-card") {
        if (!stats.hasGames) {
            h3 { +HeroPersonalStats.NO_GAMES_TITLE }
            p("muted") { +HeroPersonalStats.NO_GAMES_MESSAGE }
            return@div
        }
        div("metrics") {
            stats.metrics.forEach { metric ->
                p("metric") {
                    span("metric-label") { +metric.label }
                    span("metric-value") { +metric.value }
                    metric.note?.let { span("metric-note") { +it } }
                }
            }
        }
        val pilots = stats.pilots
        if (pilots.isEmpty()) return@div
        val ranked = stats.log.bestPilots.isNotEmpty()
        h3 { +stats.pilotsTitle }
        stats.pilotsNote?.let { p("muted small") { +it } }
        ol(if (ranked) "pilots ranked" else "pilots") {
            pilots.forEach { pilot ->
                li("pilot") {
                    playerAvatar(pilot.player, "sm")
                    span("pilot-name") { +(pilot.player?.name ?: "Unknown player") }
                    span("pilot-record") {
                        span("pilot-rate") { +percent(pilot.winRate) }
                        span("pilot-wl") {
                            attributes["aria-label"] =
                                "${plural(pilot.wins, "win")}, ${plural(pilot.games - pilot.wins, "loss", "losses")}"
                            +pilot.recordLine
                        }
                    }
                }
            }
        }
    }
}

/** One deck colour (or Basic / Ultimate) with its tier rows; Ultimate tiles span the row with their text. */
private fun FlowContent.deckGroup(group: DeckGroup) {
    val accent = when (group.kind) {
        DeckGroupKind.BASIC -> CardColor.GOLD
        DeckGroupKind.RED -> CardColor.RED
        DeckGroupKind.GREEN -> CardColor.GREEN
        DeckGroupKind.BLUE -> CardColor.BLUE
        DeckGroupKind.ULTIMATE -> CardColor.PURPLE
        DeckGroupKind.OTHER -> CardColor.OTHER
    }
    div("deck-group") {
        h3("deck-group-title") {
            span("color-dot ${accent.css}") { attributes["aria-hidden"] = "true" }
            span { +group.kind.title }
            span("count") { +group.cards.size.toString() }
        }
        if (group.kind == DeckGroupKind.BASIC) multipleGoldNote(group.cards)?.let { p("note") { +it } }
        val ultimate = group.kind == DeckGroupKind.ULTIMATE
        group.rows.forEach { row ->
            val tier = row.tier
            if (tier != null && !ultimate) p("tier-label") { +"Tier ${tierNumeral(tier)}" }
            div(if (ultimate) "tile-grid cols-1" else "tile-grid cols-2") {
                row.cards.forEach { cardTile(it, showText = ultimate) }
            }
        }
    }
}

/** The hero's extra cards by tier; titled "Spellbook" for Gydion's spell cards. */
private fun FlowContent.extrasSection(layout: DeckLayout) {
    val spellbook = layout.extrasAreSpellbook
    section("hero-section") {
        sectionTitle(if (spellbook) "Spellbook" else "Extras", "${layout.extras.size} cards")
        if (spellbook) p("note") { +SPELLBOOK_NOTE }
        layout.extraRows.forEach { row ->
            h3("tier-label") { +extraRowTitle(row) }
            div("tile-grid cols-2") { row.cards.forEach { cardTile(it, showText = spellbook) } }
        }
    }
}

// ---- Card viewer -------------------------------------------------------------------------------

/**
 * Every card of the hero in deck order, one per screen: swipe (CSS scroll snap), the arrows, or
 * the keyboard (viewer.js) move between them; without JS the arrows are links. The official scan
 * opens full size on tap (pinch to zoom), with the card's structured details and text below.
 */
internal fun FlowContent.cardViewerContent(state: CardViewerUiState.Ready) {
    val cards = state.cards
    val index = state.initialIndex
    val heroId = state.hero.id
    nav("viewer-bar") {
        attributes["aria-label"] = "Cards"
        val prev = cards.getOrNull(index - 1)
        val next = cards.getOrNull(index + 1)
        val self = cardLink(heroId, cards[index].id)
        viewerArrow(prev?.let { cardLink(heroId, it.id) }, self, "Previous card", "chevron_left", "prev")
        p("viewer-position") {
            id = "viewer-position"
            attributes["aria-live"] = "polite"
            +"${state.hero.name} · ${index + 1} of ${cards.size}"
        }
        viewerArrow(next?.let { cardLink(heroId, it.id) }, self, "Next card", "chevron_right", "next")
    }
    div("viewer-track") {
        id = "viewer-track"
        attributes["tabindex"] = "0"
        attributes["aria-label"] = "${state.hero.name}'s cards"
        attributes["data-hero"] = state.hero.name
        cards.forEachIndexed { i, card ->
            figure("viewer-card ${card.color.css}") {
                id = "card-${card.id}"
                attributes["data-href"] = cardHref(heroId, card.id)
                attributes["data-name"] = card.name
                attributes["data-position"] = "${state.hero.name} · ${i + 1} of ${cards.size}"
                card.image?.let { path ->
                    a(HubCatalog.url(path), classes = "viewer-scan") {
                        attributes["aria-label"] = "Open the card image full size: ${card.name}"
                        img(src = HubCatalog.url(path), alt = card.name) {
                            attributes["width"] = CARD_WIDTH
                            attributes["height"] = CARD_HEIGHT
                            attributes["loading"] = if (i == index) "eager" else "lazy"
                            attributes["decoding"] = "async"
                        }
                    }
                }
                cardDetails(card, state.isHandicap(card))
            }
        }
    }
}

private fun FlowContent.viewerArrow(href: String?, self: String, label: String, icon: String, role: String) {
    // At either end the arrow stays in place, disabled (viewer.js enables it once there is a card that way).
    a(href ?: self, classes = if (href == null) "icon-button disabled" else "icon-button") {
        attributes["data-viewer"] = role
        attributes["aria-label"] = label
        if (href == null) {
            attributes["aria-disabled"] = "true"
            attributes["tabindex"] = "-1"
        }
        icon(icon)
    }
}

/** Structured card info under the scan: actions, values, markers and the text (the phone's CardDetails). */
private fun FlowContent.cardDetails(card: Card, isHandicap: Boolean) {
    div("card card-details") {
        h2("viewer-name") { +card.name }
        p("muted") { +card.kindLabel(isHandicap) }
        dl("details") {
            if (isHandicap) card.handicapBasis()?.let { detail("Handicap") { +it } }
            detail("Initiative") { +(card.initiative?.toString() ?: "—") }
            card.primary?.let { primary ->
                detail("Primary action") {
                    gameIcon(primary.iconToken)
                    span { +GameIconLabels.forPrimary(primary) }
                }
            }
            if (card.secondary.isNotEmpty()) {
                detail("Secondary") {
                    card.secondary.forEach { action ->
                        span("icon-label") {
                            gameIcon(secondaryToken(action))
                            span { +GameIconLabels.forSecondary(action) }
                        }
                    }
                }
            }
            val values = card.printedValues()
            if (values.isNotEmpty()) {
                detail("Values") {
                    values.forEach { value ->
                        span("icon-label") {
                            attributes["role"] = "img"
                            attributes["aria-label"] = value.spoken
                            gameIcon(value.stat.iconToken)
                            span { +"${value.stat.label} ${value.printed}" }
                        }
                    }
                }
            }
            val block = card.blockMarkerLabel()
            if (card.ranged || block != null) {
                detail("Markers") {
                    if (card.ranged) iconLabel("ranged", GameIconLabels.RANGED)
                    block?.let { iconLabel("block", it) }
                }
            }
            card.item?.let { stat -> detail("Item") { iconLabel("item", "+1 ${stat.label}") } }
        }
        if (card.text.isNotBlank()) cardText(card.text)
    }
}

private fun FlowContent.iconLabel(token: String, text: String) {
    span("icon-label") {
        gameIcon(token)
        span { +text }
    }
}

private fun secondaryToken(action: SecondaryAction): String = when (action) {
    SecondaryAction.MOVEMENT -> "movement"
    SecondaryAction.DEFENSE -> "defense"
    SecondaryAction.HOLD -> "hold"
    SecondaryAction.FAST_TRAVEL -> "fast_travel"
    SecondaryAction.CLEAR -> "clear"
    SecondaryAction.ATTACK -> "attack"
}

private fun DL.detail(label: String, value: FlowContent.() -> Unit) {
    dt { +label }
    dd { value() }
}
