package dev.quinton.goa.hub.rules

import dev.quinton.goa.core.domain.rules.LadderLookup
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.presentation.rules.ExcerptLine
import dev.quinton.goa.core.presentation.rules.LADDER_HEADERS
import dev.quinton.goa.core.presentation.rules.LadderUiState
import dev.quinton.goa.core.presentation.rules.RulesText
import dev.quinton.goa.core.presentation.rules.RulesTopicUiState
import dev.quinton.goa.core.presentation.rules.RulesUiState
import dev.quinton.goa.core.presentation.rules.defeatNumbers
import dev.quinton.goa.core.presentation.rules.defeatTitle
import dev.quinton.goa.core.presentation.rules.excerptLines
import dev.quinton.goa.core.presentation.rules.ladderCells
import dev.quinton.goa.core.presentation.rules.spokenDefeat
import dev.quinton.goa.core.presentation.rules.spokenRow
import dev.quinton.goa.core.presentation.rules.spokenTableRow
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.heroes.CATALOG_CSS
import dev.quinton.goa.hub.heroes.cardText
import dev.quinton.goa.hub.timer.TimerRoutes
import dev.quinton.goa.hub.web.isHtmx
import dev.quinton.goa.hub.web.tidyUrl
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.respondFragment
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.net.URI
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.ThScope
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul

/**
 * The rules reference (ADR-019): Levels & defeats and the quick-reference topics, quoted verbatim
 * from the bundled `rules-reference.json` exactly as stored (escaped), with printed page numbers.
 * Our own words only ever appear as a labelled "Summary".
 */
object RulesFeature : HubFeature {
    const val PATH = "/rules"
    const val LEVELS_PATH = "/rules/levels"

    private val styles = listOf(CATALOG_CSS, "rules/rules.css")

    override fun Route.routes(ctx: HubContext) {
        get(PATH) { call.rulesPage(ctx) }
        get(LEVELS_PATH) { call.levelsPage(ctx) }
        get("$PATH/{topicId}") { call.topicPage(ctx, call.parameters["topicId"].orEmpty()) }
    }

    @Volatile
    private var loaded: Pair<HubCatalog, RulesReference?>? = null

    /** The bundled reference, parsed once; null if it can't be read (a build defect, tests guard it). */
    fun reference(catalog: HubCatalog): RulesReference? {
        loaded?.let { (c, reference) -> if (c === catalog) return reference }
        val reference = runCatching { RulesReference.parse(catalog.readText(RulesReference.PATH)) }.getOrNull()
        loaded = catalog to reference
        return reference
    }

    private suspend fun ApplicationCall.rulesPage(ctx: HubContext) {
        val query = request.queryParameters["q"].orEmpty().take(MAX_QUERY)
        val searching = isHtmx && request.headers["HX-Target"] == "rules-results"
        val canonical = if (query.isBlank()) PATH else "$PATH?${Parameters.build { append("q", query) }.formUrlEncode()}"
        if ((searching || !isHtmx) && tidyUrl(canonical)) return
        val state = RulesUiState.of(reference(ctx.catalog), query)
        if (searching) {
            respondFragment { rulesResults(state) }
            return
        }
        respondPage(HubPage("Rules", back = "/heroes", styles = styles)) {
            if (state !is RulesUiState.Ready) {
                emptyState("Rules unavailable", RulesText.UNAVAILABLE)
                return@respondPage
            }
            form(action = PATH, method = FormMethod.get, classes = "rules-search") {
                attributes["role"] = "search"
                attributes["hx-get"] = PATH
                attributes["hx-target"] = "#rules-results"
                attributes["hx-swap"] = "outerHTML"
                attributes["hx-push-url"] = "true"
                attributes["hx-trigger"] = "input changed delay:250ms from:#rules-q, search from:#rules-q, submit"
                div("search-field") {
                    icon(Icons.SEARCH)
                    label("visually-hidden") {
                        htmlFor = "rules-q"
                        +"Search rules"
                    }
                    input(InputType.search, name = "q") {
                        id = "rules-q"
                        value = query
                        placeholder = "Search rules"
                        attributes["autocomplete"] = "off"
                        attributes["enterkeyhint"] = "search"
                    }
                }
            }
            rulesResults(state)
        }
    }

    private suspend fun ApplicationCall.topicPage(ctx: HubContext, topicId: String) {
        val state = RulesTopicUiState.of(reference(ctx.catalog), topicId)
        if (state !is RulesTopicUiState.Ready) {
            respondPage(HubPage("Rules", back = PATH, styles = styles), HttpStatusCode.NotFound) {
                emptyState("Topic not found", "This rules topic is not in the bundled reference.", "All rules", PATH)
            }
            return
        }
        val topic = state.topic
        respondPage(HubPage(topic.title, back = PATH, styles = styles)) {
            p("muted rules-pages") { +RulesText.topicPagesLine(topic.pages) }
            topic.summary?.let { summaryNote(it) }
            topic.excerpts.forEachIndexed { i, excerpt ->
                if (i > 0 && excerpt.heading != null) div("rule-divider") { attributes["aria-hidden"] = "true" }
                excerptBlock(excerpt)
            }
            p("muted small") { +RulesText.TOPIC_SOURCE }
        }
    }

    /**
     * Levels & defeats: [level] picks the defeated hero's level (picking it again clears it); [heroes]
     * (comma-separated ids, e.g. from the timer) limits the cards that change a defeat to that match.
     */
    private suspend fun ApplicationCall.levelsPage(ctx: HubContext) {
        val level = request.queryParameters["level"]?.toIntOrNull()
        val heroes = request.queryParameters["heroes"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val state = LadderUiState.of(reference(ctx.catalog), ctx.catalog.catalog, heroes, level)
        if (state !is LadderUiState.Ready) {
            respondPage(HubPage(RulesText.LADDER_TITLE, back = PATH, styles = styles)) {
                emptyState("Rules unavailable", RulesText.UNAVAILABLE)
            }
            return
        }
        if (isHtmx && request.headers["HX-Target"] == "ladder") {
            respondFragment { ladder(state, heroes) }
            return
        }
        // From the running timer (its Levels action), Back returns there, as on the phone.
        val fromTimer = request.headers[HttpHeaders.Referrer]
            ?.let { runCatching { URI(it).path }.getOrNull() } == TimerRoutes.RUN
        val back = if (fromTimer) TimerRoutes.RUN else PATH
        respondPage(HubPage(RulesText.LADDER_TITLE, back = back, styles = styles)) { ladder(state, heroes) }
    }

    private const val MAX_QUERY = 200
}

private fun FlowContent.rulesResults(state: RulesUiState) {
    section("rules-results") {
        id = "rules-results"
        val ready = state as? RulesUiState.Ready ?: return@section
        if (ready.showLadder) {
            a(RulesFeature.LEVELS_PATH, classes = "card ladder-card") {
                icon("military_tech", "ladder-icon")
                span("ladder-text") {
                    span("ladder-title") { +RulesText.LADDER_TITLE }
                    span("muted") { +RulesText.LADDER_SUBTITLE }
                }
                pageTag(RulesText.LADDER_PAGES)
            }
        }
        sectionHeader(ready.topicsHeader)
        if (ready.isEmpty) {
            section("empty-state") {
                h2 { +RulesText.EMPTY_TITLE }
                p { +RulesText.EMPTY_MESSAGE }
            }
        }
        if (ready.topics.isNotEmpty()) {
            ul("topic-list") {
                ready.topics.forEach { topic ->
                    li {
                        a("${RulesFeature.PATH}/${topic.id}", classes = "row topic-row") {
                            attributes["aria-label"] = "${topic.title}, ${RulesText.spokenPages(topic.pages)}"
                            span("row-text") {
                                span("row-title") { +topic.title }
                                span("row-subtitle") { +RulesText.pages(topic.pages) }
                            }
                            icon(Icons.CHEVRON, "row-chevron")
                        }
                    }
                }
            }
        }
        p("muted small") { +RulesText.LIST_SOURCE }
    }
}

/** "p. 7" in a small bronze-outlined tag, read "rulebook page 7". */
private fun FlowContent.pageTag(pages: List<Int>) {
    span("page-tag") {
        attributes["role"] = "img"
        attributes["aria-label"] = RulesText.spokenPages(pages)
        +RulesText.pages(pages)
    }
}

/** Our own words, never a quote: always labelled "Summary". */
private fun FlowContent.summaryNote(text: String) {
    div("summary-note") {
        p("summary-label") { +"Summary" }
        p { +text }
    }
}

/**
 * A verbatim rulebook passage: its printed heading (if any), a bronze quote rule, the text with
 * bullets and numbered steps as printed, any printed table, and the printed page.
 */
private fun FlowContent.excerptBlock(excerpt: RulesExcerpt) {
    section("excerpt") {
        div("excerpt-head") {
            excerpt.heading?.let { h3("excerpt-heading") { +it } } ?: span("excerpt-heading")
            pageTag(listOf(excerpt.page))
        }
        div("excerpt-body") {
            excerptLines(excerpt.text).forEach { line ->
                when (line) {
                    is ExcerptLine.Paragraph -> p { +line.text }

                    is ExcerptLine.Bullet -> p("list-line") {
                        span("list-marker") {
                            attributes["aria-hidden"] = "true"
                            +"•"
                        }
                        span { +line.text }
                    }

                    is ExcerptLine.Numbered -> p("list-line") {
                        span("list-marker") { +"${line.number}." }
                        span { +line.text }
                    }
                }
            }
            if (excerpt.table.isNotEmpty()) excerptTable(excerpt.table)
        }
    }
}

/** A printed table, verbatim: header row first; scrolls sideways when large text makes it wide. */
private fun FlowContent.excerptTable(rows: List<List<String>>) {
    val header = rows.first()
    div("table-scroll") {
        attributes["tabindex"] = "0"
        attributes["role"] = "region"
        attributes["aria-label"] = header.joinToString(", ")
        table("rules-table") {
            thead {
                tr {
                    header.forEach {
                        th {
                            scope = ThScope.col
                            +it
                        }
                    }
                }
            }
            tbody {
                rows.drop(1).forEach { row ->
                    tr {
                        attributes["aria-label"] = spokenTableRow(header, row)
                        row.forEachIndexed { c, cell ->
                            if (c == 0) {
                                th {
                                    scope = ThScope.row
                                    +cell
                                }
                            } else {
                                td { +cell }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Levels & defeats: picker, the picked level's three numbers, the full table, the rules and the cards. */
private fun FlowContent.ladder(state: LadderUiState.Ready, heroes: List<String>) {
    val ladder = state.ladder
    section("ladder") {
        id = "ladder"
        // The top bar names the page; the heading stays for screen readers and the htmx swap.
        h2("visually-hidden") { +RulesText.LADDER_TITLE }
        div("ladder-head") {
            h3("overline") { +"Hero defeated at level" }
            pageTag(ladder.pages)
        }
        nav("level-picker") {
            attributes["aria-label"] = "Hero defeated at level"
            ladder.levels.forEach { row ->
                val selected = row.level == state.selectedLevel
                a(
                    levelHref(state.selectionAfterPicking(row.level), heroes),
                    classes = if (selected) "level selected" else "level",
                ) {
                    attributes["aria-label"] = "Level ${row.level}"
                    attributes["aria-pressed"] = selected.toString()
                    levelSwap()
                    +row.level.toString()
                }
            }
        }
        state.selected?.let { row ->
            div("defeat-result") {
                attributes["role"] = "status"
                attributes["aria-label"] = spokenDefeat(row)
                p("defeat-title") { +defeatTitle(row) }
                div("defeat-numbers") {
                    defeatNumbers(row).forEach { number ->
                        div("defeat-number") {
                            span("big-digit") { +number.value.toString() }
                            span("defeat-label") { +number.label }
                            span("muted small") { +number.detail }
                        }
                    }
                }
            }
        }
        ladderTable(ladder, state.selectedLevel, heroes)
        sectionHeader("When a hero is defeated")
        ladder.defeat.forEach { excerptBlock(it) }
        sectionHeader("Levelling up")
        ladder.levelUp.forEach { excerptBlock(it) }
        sectionHeader(state.cardsHeader)
        excerptBlock(ladder.cardText)
        if (state.exceptions.isEmpty()) p("muted") { +state.noCardsText }
        state.exceptions.forEach { card ->
            div("exception-card") {
                p("exception-title") { +"${card.heroName} · ${card.cardName}" }
                p("muted small") { +card.label }
                cardText(card.text)
            }
        }
    }
}

private fun FlowContent.ladderTable(ladder: LevelLadder, selected: Int?, heroes: List<String>) {
    div("table-scroll") {
        attributes["tabindex"] = "0"
        attributes["role"] = "region"
        attributes["aria-label"] = "Levels 1 to ${ladder.levels.size}"
        table("ladder-table") {
            thead {
                tr {
                    LADDER_HEADERS.forEach { header ->
                        th {
                            scope = ThScope.col
                            header.split('\n').forEachIndexed { i, part ->
                                if (i > 0) br()
                                +part
                            }
                        }
                    }
                }
            }
            tbody {
                ladder.levels.forEach { row ->
                    val next = LadderLookup.nextLevelCost(ladder, row.level)
                    tr(if (row.level == selected) "selected" else null) {
                        attributes["aria-label"] = spokenRow(row, next)
                        ladderCells(ladder, row).forEachIndexed { i, cell ->
                            if (i == 0) {
                                th {
                                    scope = ThScope.row
                                    a(levelHref(if (row.level == selected) null else row.level, heroes)) {
                                        attributes["aria-label"] = "Show level ${row.level}"
                                        levelSwap()
                                        +cell
                                    }
                                }
                            } else {
                                td { +cell }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Picking a level swaps the ladder in place (htmx); without JS it is a plain link. */
private fun kotlinx.html.A.levelSwap() {
    attributes["hx-get"] = href ?: RulesFeature.LEVELS_PATH
    attributes["hx-target"] = "#ladder"
    attributes["hx-swap"] = "outerHTML"
    attributes["hx-push-url"] = "true"
}

private fun levelHref(level: Int?, heroes: List<String>): String {
    val params = Parameters.build {
        level?.let { append("level", it.toString()) }
        if (heroes.isNotEmpty()) append("heroes", heroes.joinToString(","))
    }.formUrlEncode()
    return if (params.isEmpty()) RulesFeature.LEVELS_PATH else "${RulesFeature.LEVELS_PATH}?$params"
}
