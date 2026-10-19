package dev.quinton.goa.hub.draft

import dev.quinton.goa.core.domain.teams.ComplexityFilter
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.HeroInfo
import dev.quinton.goa.core.presentation.draft.StepToken
import dev.quinton.goa.core.presentation.draft.cardKind
import dev.quinton.goa.core.presentation.draft.label
import dev.quinton.goa.core.presentation.draft.teamGlyph
import dev.quinton.goa.core.presentation.draft.teamName
import dev.quinton.goa.hub.catalog.HubCatalog
import kotlinx.html.BUTTON
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.span
import kotlinx.html.ul

/*
 * Small pieces the Draft pages share. Classes only (the CSP forbids inline styles); team identity
 * is always a glyph and a word as well as a colour (DESIGN.md).
 */

/** A container in a team's colour (`--side`); text inside keeps its own colour. */
internal fun teamCss(team: Team): String = if (team == Team.RED) "side-red" else "side-blue"

/** A submit button carrying `op` (the action the hub applies, see [op]). */
internal fun FlowContent.opButton(
    op: String,
    label: String? = null,
    classes: String = "btn btn-tonal",
    enabled: Boolean = true,
    block: BUTTON.() -> Unit = {},
) {
    button(type = ButtonType.submit, classes = classes) {
        name = "op"
        value = op
        if (!enabled) disabled = true
        block()
        label?.let { span { +it } }
    }
}

/**
 * The first control of a form with several buttons: pressing Enter in a field submits this
 * harmless one (`op=apply`) instead of the first real button.
 */
internal fun FlowContent.defaultApply() {
    button(type = ButtonType.submit, classes = "visually-hidden") {
        name = "op"
        value = "apply"
        attributes["tabindex"] = "-1"
        attributes["aria-hidden"] = "true"
        +"Apply"
    }
}

/** A hero's portrait from the bundled catalog (decorative: the name is always next to it). */
internal fun FlowContent.portrait(hero: HeroInfo?, classes: String = "hero-art") {
    val path = hero?.portrait
    if (path != null) {
        img(src = HubCatalog.thumbUrl(path, HubCatalog.THUMB_SMALL), alt = "", classes = classes) {
            // Pool tiles are up to ~100 pt wide: the grid copy on 3× phones.
            attributes["srcset"] =
                "${HubCatalog.thumbUrl(path, HubCatalog.THUMB_SMALL)} ${HubCatalog.THUMB_SMALL}w, " +
                "${HubCatalog.thumbUrl(path, HubCatalog.THUMB_GRID)} ${HubCatalog.THUMB_GRID}w"
            attributes["sizes"] = "96px"
            attributes["loading"] = "lazy"
            attributes["decoding"] = "async"
        }
    } else {
        span("$classes hero-art-empty") { attributes["aria-hidden"] = "true" }
    }
}

/** A covered tile for a choice that is still hidden: the team's shape and a lock, never the hero. */
internal fun FlowContent.concealedArt(team: Team) {
    span("hero-art hero-art-hidden ${teamCss(team)}") {
        attributes["aria-hidden"] = "true"
        +teamGlyph(team)
    }
}

/** "Ban ◆ · Pick ● · …" chips; [current] is highlighted and earlier steps fade. */
internal fun FlowContent.sequenceStrip(tokens: List<StepToken>, current: Int? = null) {
    if (tokens.isEmpty()) return
    ol("sequence") {
        attributes["aria-label"] = "Draft order"
        tokens.forEachIndexed { i, token ->
            val classes = listOfNotNull(
                "step",
                if (token.action == DraftAction.BAN) "step-ban" else "step-pick",
                token.team?.let(::teamCss),
                "done".takeIf { current != null && i < current },
                "current".takeIf { i == current },
            ).joinToString(" ")
            li(classes) {
                if (i == current) attributes["aria-current"] = "step"
                +token.label
                if (token.hidden) {
                    span("step-hidden") {
                        attributes["aria-hidden"] = "true"
                        +" ⦸"
                    }
                    span("visually-hidden") { +", hidden" }
                }
            }
        }
    }
}

/** "★★☆☆ Complex" — the official complexity names. */
internal fun FlowContent.complexity(stars: Int?) {
    if (stars == null) return
    span("stars") {
        attributes["aria-label"] = "Complexity $stars of 4, ${ComplexityFilter.name(stars) ?: ""}"
        span {
            attributes["aria-hidden"] = "true"
            +("★".repeat(stars) + "☆".repeat((4 - stars).coerceAtLeast(0)))
        }
        ComplexityFilter.name(stars)?.let {
            span("stars-name") {
                attributes["aria-hidden"] = "true"
                +" $it"
            }
        }
    }
}

internal fun FlowContent.roleChips(hero: HeroInfo) {
    if (hero.roles.isEmpty()) return
    ul("roles") { hero.roles.forEach { li("role") { +it.label } } }
}

/** The hero peek (the phone's long-press sheet): roles, stats base → max and the starting cards. */
internal fun FlowContent.heroPeek(hero: HeroInfo) {
    div("peek") {
        roleChips(hero)
        statsList(hero.stats)
        if (hero.startingCards.isNotEmpty()) {
            span("peek-heading") { +"Starting cards" }
            ul("cards") {
                hero.startingCards.forEach { card ->
                    li {
                        span("card-name") { +card.name }
                        span("card-kind") { +" · ${cardKind(card)}" }
                    }
                }
            }
        }
    }
}

private fun FlowContent.statsList(stats: HeroStats) {
    val rows = listOf(
        "Attack" to stats.attack,
        "Defense" to stats.defense,
        "Initiative" to stats.initiative,
        "Movement" to stats.movement,
    )
        .mapNotNull { (name, values) -> values?.takeIf { it.isNotEmpty() }?.let { name to it } }
    if (rows.isEmpty()) return
    dl("facts stats") {
        rows.forEach { (name, values) ->
            dt { +name }
            dd { +values.joinToString(" → ") }
        }
    }
}

/** "◆ Red" with the team's class. */
internal fun FlowContent.teamTitle(team: Team, extra: String? = null) {
    span("team-title ${teamCss(team)}") {
        span("team-mark") {
            attributes["aria-hidden"] = "true"
            +teamGlyph(team)
        }
        span { +teamName(team) }
        extra?.let { span("team-extra") { +it } }
    }
}
