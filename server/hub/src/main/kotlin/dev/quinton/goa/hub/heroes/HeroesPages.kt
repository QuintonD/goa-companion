package dev.quinton.goa.hub.heroes

import dev.quinton.goa.core.presentation.catalog.ATTACK_STYLE_NOTE
import dev.quinton.goa.core.presentation.catalog.CATALOG_SEARCH_HINT
import dev.quinton.goa.core.presentation.catalog.CardResult
import dev.quinton.goa.core.presentation.catalog.CatalogFilter
import dev.quinton.goa.core.presentation.catalog.CatalogLayout
import dev.quinton.goa.core.presentation.catalog.CatalogSort
import dev.quinton.goa.core.presentation.catalog.CatalogUiState
import dev.quinton.goa.core.presentation.catalog.HeroListItem
import dev.quinton.goa.core.presentation.catalog.activeFilters
import dev.quinton.goa.core.presentation.catalog.cleared
import dev.quinton.goa.core.presentation.catalog.complexityName
import dev.quinton.goa.core.presentation.catalog.kindLabel
import dev.quinton.goa.core.presentation.catalog.label
import dev.quinton.goa.core.presentation.catalog.matchHint
import dev.quinton.goa.core.presentation.catalog.matchingParagraph
import dev.quinton.goa.core.presentation.catalog.metricLine
import dev.quinton.goa.core.presentation.catalog.rangedLabel
import dev.quinton.goa.core.presentation.catalog.showHeroesLabel
import dev.quinton.goa.core.presentation.catalog.spokenLabel
import dev.quinton.goa.core.presentation.catalog.traitsLine
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.icon
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.summary

/*
 * The Heroes tab: search (as you type, htmx with a short debounce; a plain GET form without JS),
 * filters and sort in the URL, the results as the phone lists them (cards ahead of heroes when they
 * answer the query better), grid or list.
 */

internal fun FlowContent.catalogContent(state: CatalogUiState.Ready, request: CatalogRequest) {
    form(action = HeroesFeature.PATH, method = FormMethod.get, classes = "catalog-form") {
        id = "catalog-form"
        attributes["role"] = "search"
        attributes["hx-get"] = HeroesFeature.PATH
        attributes["hx-target"] = "#results"
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-push-url"] = "true"
        attributes["hx-trigger"] = "input changed delay:250ms from:#q, search from:#q, change, submit"
        div("search-field") {
            icon(Icons.SEARCH)
            label("visually-hidden") {
                htmlFor = "q"
                +CATALOG_SEARCH_HINT
            }
            input(InputType.search, name = "q") {
                id = "q"
                value = request.query
                placeholder = CATALOG_SEARCH_HINT
                attributes["autocomplete"] = "off"
                attributes["enterkeyhint"] = "search"
                attributes["spellcheck"] = "false"
            }
        }
        div("controls-row") {
            filterPanel(state, request)
            label("sort-select") {
                icon("sort")
                span("visually-hidden") { +"Sort by" }
                select {
                    name = "sort"
                    attributes["aria-label"] = "Sort"
                    CatalogSort.entries.forEach { option ->
                        option {
                            value = option.key
                            selected = option == request.sort
                            +"Sort: ${option.label}"
                        }
                    }
                }
            }
        }
    }
    catalogResults(state, request)
}

/** The filter sheet, as a disclosure inside the search form (works without JS). */
private fun FlowContent.filterPanel(state: CatalogUiState.Ready, request: CatalogRequest) {
    val filter = state.controls.filter
    val options = state.options
    details("filters") {
        summary("chip" + if (filter.activeCount > 0) " selected" else "") {
            icon("tune")
            span { +"Filters" }
            span {
                id = "filter-count"
                filterCount(filter)
            }
        }
        div("filter-sheet") {
            div("filter-head") {
                h2 { +"Filters" }
                if (filter.activeCount > 0) {
                    a(request.href(filter = filter.cleared()), classes = "btn btn-text") { +"Clear all" }
                }
            }
            if (options.complexities.isNotEmpty()) {
                filterGroup("Complexity") {
                    options.complexities.forEach { level ->
                        filterCheck("c", level.toString(), level in filter.complexities) {
                            complexityStars(level, "stars small")
                            complexityName(level)?.let { span { +it } }
                        }
                    }
                }
            }
            if (options.roles.isNotEmpty()) {
                filterGroup("Role") {
                    options.roles.forEach { role ->
                        filterCheck("r", role.key, role in filter.roles) { span { +role.label } }
                    }
                }
            }
            filterGroup("Attack cards", ATTACK_STYLE_NOTE) {
                filterRadio("a", "", filter.attackStyle == null) { span { +"Any" } }
                options.attackStyles.forEach { style ->
                    filterRadio("a", style.key, filter.attackStyle == style) { span { +style.label } }
                }
            }
            if (options.products.size > 1) {
                filterGroup("Product") {
                    options.products.forEach { product ->
                        filterCheck("p", product.id, product.id in filter.products) { span { +product.name } }
                    }
                }
            }
            if (options.hasOwnedProducts) {
                // A link, as the phone's switch also clears the product choices.
                val next = filter.copy(includeUnowned = !filter.includeUnowned, products = emptySet())
                a(request.href(filter = next), classes = "switch-row") {
                    attributes["role"] = "switch"
                    attributes["aria-checked"] = filter.includeUnowned.toString()
                    span("switch-text") {
                        span { +"Include products you don't own" }
                        span("row-subtitle") { +"Owned products are set in Settings" }
                    }
                    span(if (filter.includeUnowned) "switch-track on" else "switch-track") {
                        attributes["aria-hidden"] = "true"
                    }
                }
                if (filter.includeUnowned) input(InputType.hidden, name = "all") { value = "1" }
            }
            button(type = ButtonType.submit, classes = "btn btn-primary filter-done") {
                span {
                    id = "show-count"
                    +showHeroesLabel(state.heroes.size)
                }
            }
        }
    }
}

private fun FlowContent.filterCount(filter: CatalogFilter) {
    if (filter.activeCount > 0) +" · ${filter.activeCount}"
}

private fun FlowContent.filterGroup(title: String, note: String? = null, block: FlowContent.() -> Unit) {
    fieldSet("filter-group") {
        legend { +title }
        note?.let { p("filter-note") { +it } }
        div("chip-row") { block() }
    }
}

private fun FlowContent.filterCheck(name: String, value: String, checked: Boolean, label: FlowContent.() -> Unit) {
    label("chip check-chip") {
        input(InputType.checkBox, name = name) {
            this.value = value
            this.checked = checked
        }
        icon(Icons.CHECK, "check-mark")
        label()
    }
}

private fun FlowContent.filterRadio(name: String, value: String, checked: Boolean, label: FlowContent.() -> Unit) {
    label("chip check-chip") {
        input(InputType.radio, name = name) {
            this.value = value
            this.checked = checked
        }
        icon(Icons.CHECK, "check-mark")
        label()
    }
}

/** The results: summary, active filters, cards and heroes (or why nothing matches). Swapped by htmx. */
internal fun FlowContent.catalogResults(state: CatalogUiState, request: CatalogRequest) {
    section("catalog-results") {
        id = "results"
        val ready = state as? CatalogUiState.Ready ?: return@section
        val filter = ready.controls.filter
        val chips = activeFilters(filter, ready.options)
        if (chips.isNotEmpty()) {
            div("chip-row active-filters") {
                chips.forEach { chip ->
                    a(request.href(filter = chip.remove(filter)), classes = "chip selected") {
                        attributes["aria-label"] = "Remove filter ${chip.label}"
                        span { +chip.label }
                        icon(Icons.CLOSE)
                    }
                }
            }
        }
        div("results-bar") {
            p("result-summary") {
                attributes["role"] = "status"
                +ready.resultSummary(request.query)
            }
            viewToggle(ready.controls.layout, request)
        }
        if (ready.heroes.isEmpty()) {
            noResults(ready, request)
            return@section
        }
        val showCards = ready.cards.isNotEmpty()
        if (showCards && ready.cardsFirst) cardResults(ready.cards, request.query)
        if (showCards) resultsHeader("Heroes", ready.heroes.size)
        val grid = ready.controls.layout == CatalogLayout.GRID
        div(if (grid) "hero-grid" else "hero-list") {
            ready.heroes.forEach { item ->
                if (grid) heroTile(item, ready.controls.sort) else heroRow(item, ready.controls.sort)
            }
        }
        if (showCards && !ready.cardsFirst) cardResults(ready.cards, request.query)
    }
}

/** Out-of-band parts of an htmx search answer: the filter count and the sheet's button follow every search. */
internal fun FlowContent.oobCounts(state: CatalogUiState) {
    if (state !is CatalogUiState.Ready) return
    span {
        id = "filter-count"
        attributes["hx-swap-oob"] = "true"
        filterCount(state.controls.filter)
    }
    span {
        id = "show-count"
        attributes["hx-swap-oob"] = "true"
        +showHeroesLabel(state.heroes.size)
    }
}

private fun FlowContent.viewToggle(layout: CatalogLayout, request: CatalogRequest) {
    form(action = "${HeroesFeature.PATH}/view", method = FormMethod.post, classes = "view-toggle") {
        val toList = layout == CatalogLayout.GRID
        input(InputType.hidden, name = "view") { value = if (toList) "list" else "grid" }
        input(InputType.hidden, name = "back") { value = request.href() }
        button(type = ButtonType.submit, classes = "icon-button") {
            val label = if (toList) "Show as list" else "Show as grid"
            attributes["aria-label"] = label
            attributes["title"] = label
            icon(if (toList) "view_list" else "grid_view")
        }
    }
}

private fun FlowContent.noResults(state: CatalogUiState.Ready, request: CatalogRequest) {
    val empty = state.noResults(request.query)
    section("empty-state") {
        span("empty-mark") { attributes["aria-hidden"] = "true" }
        h2 { +empty.title }
        p { +empty.message }
        val href = if (empty.showAllProducts) {
            request.href(filter = state.controls.filter.copy(includeUnowned = true))
        } else {
            request.href(query = "", filter = state.controls.filter.cleared())
        }
        a(href, classes = "btn btn-outlined") { +empty.action }
    }
}

private fun FlowContent.resultsHeader(title: String, count: Int) {
    h2("results-header") {
        span { +title }
        span("count") { +count.toString() }
    }
}

private fun FlowContent.cardResults(cards: List<CardResult>, query: String) {
    resultsHeader("Cards", cards.size)
    div("card-results") {
        cards.forEach { result ->
            val card = result.card
            a(cardLink(result.hero.id, card.id), classes = "card-result ${card.color.css}") {
                div("card-result-text") {
                    span("card-result-name") { +card.name }
                    span("card-result-kind") { +"${result.hero.name} · ${card.kindLabel(result.isHandicap)}" }
                    val snippet = matchingParagraph(card.text, query)
                    if (snippet.isNotBlank()) cardText(snippet, "card-text snippet")
                }
                initiativeBadge(card.initiative, card.color)
            }
        }
    }
}

/** Grid tile: portrait with stars (and Unverified), name, title, roles, pills, footnotes. */
private fun FlowContent.heroTile(item: HeroListItem, sort: CatalogSort) {
    val hero = item.hero
    a("${HeroesFeature.PATH}/${hero.id}", classes = "hero-tile") {
        attributes["aria-label"] = item.spokenLabel(sort)
        div("tile-art") {
            heroPortrait(hero.id, hero.portrait, "tile-portrait", size = PortraitSize.GRID)
            hero.complexity?.let { complexityStars(it, "stars on-art") }
            if (!hero.verified) span("art-badge") { unverifiedBadge(compact = true) }
        }
        div("tile-body") {
            h3("tile-title") { +hero.name }
            p("tile-subtitle") { +hero.title }
            item.traitsLine().takeIf { it.isNotEmpty() }?.let { p("tile-roles") { +it } }
            div("pills") {
                span("meta-pill") { +item.productName }
                item.rangedLabel?.let { span("meta-pill accent") { +it } }
            }
            tileFootnotes(item, sort)
        }
    }
}

/** Compact list row: small portrait, name and title, stars and pills, roles. */
private fun FlowContent.heroRow(item: HeroListItem, sort: CatalogSort) {
    val hero = item.hero
    a("${HeroesFeature.PATH}/${hero.id}", classes = "hero-row") {
        attributes["aria-label"] = item.spokenLabel(sort)
        heroPortrait(hero.id, hero.portrait, "row-portrait")
        div("hero-row-text") {
            p("hero-row-name") {
                span("name") { +hero.name }
                span("title") { +hero.title }
            }
            div("pills") {
                hero.complexity?.let { complexityStars(it, "stars small") }
                span("meta-pill") { +item.productName }
                item.rangedLabel?.let { span("meta-pill accent") { +it } }
                if (!hero.verified) unverifiedBadge(compact = true)
            }
            item.traitsLine().takeIf { it.isNotEmpty() }?.let { p("tile-roles") { +it } }
            tileFootnotes(item, sort)
        }
    }
}

private fun FlowContent.tileFootnotes(item: HeroListItem, sort: CatalogSort) {
    item.metricLine(sort)?.let { p("tile-metric") { +it } }
    item.matchHint()?.let { p("tile-hint") { +it } }
}
