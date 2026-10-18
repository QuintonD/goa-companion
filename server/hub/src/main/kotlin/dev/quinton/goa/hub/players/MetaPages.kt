package dev.quinton.goa.hub.players

import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.players.Dates
import dev.quinton.goa.core.presentation.players.GameNightUi
import dev.quinton.goa.core.presentation.players.HeroMetaRow
import dev.quinton.goa.core.presentation.players.HeroSort
import dev.quinton.goa.core.presentation.players.Meta
import dev.quinton.goa.core.presentation.players.MetaUiState
import dev.quinton.goa.core.presentation.players.MetaView
import dev.quinton.goa.core.presentation.players.PairRow
import dev.quinton.goa.core.presentation.players.StatsPage
import dev.quinton.goa.core.presentation.players.StatsPageRenderer
import dev.quinton.goa.core.presentation.players.StreakRow
import dev.quinton.goa.core.presentation.players.formatPercent
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.label
import dev.quinton.goa.core.presentation.players.plural
import dev.quinton.goa.core.presentation.players.spokenRecord
import dev.quinton.goa.core.presentation.players.winLoss
import dev.quinton.goa.hub.HubCli
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.TopAction
import dev.quinton.goa.hub.web.ui.TopActions
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import dev.quinton.goa.hub.web.ui.teamBadge
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.math.roundToInt
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.caption
import kotlinx.html.div
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

/** The Meta tab (hero meta, group stats) and the shareable stats page (ADR-027). */
internal fun Route.metaRoutes(ctx: HubContext) {
    get(PlayersPaths.META) {
        val view = PlayersData.of(ctx)
        val q = call.request.queryParameters
        val state = Meta.build(view.snapshot, view.byId, view.catalog).copy(
            view = if (q["view"] == "cards") MetaView.CARDS else MetaView.TABLE,
            sort = HeroSort.entries.firstOrNull { it.name == q["sort"] } ?: HeroSort.RANK,
        )
        val share = TopAction("Share stats page", PlayersPaths.STATS_PAGE, Icons.SHARE)
        call.respondPage(playersPage("Players", actions = listOf(share, TopActions.Settings))) {
            sectionTabs(PlayersSection.META)
            meta(state)
        }
    }
    get(PlayersPaths.STATS_PAGE) {
        val view = PlayersData.of(ctx)
        val page = StatsPage.build(
            players = view.players,
            matches = view.matches,
            snapshot = view.snapshot,
            catalog = view.catalog,
            nowMillis = ctx.clock.nowMillis(),
            appVersion = "hub ${HubCli.VERSION ?: "dev"}",
            zone = view.zone,
        )
        // A file to keep or send on: its inline styles need no CSP exception once saved and opened.
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, "goa-stats-${page.generatedIsoDate}.html")
                .toString(),
        )
        call.respondText(StatsPageRenderer.render(page), ContentType.Text.Html.withParameter("charset", "utf-8"))
    }
}

private fun FlowContent.meta(state: MetaUiState) {
    if (state.isEmpty) {
        emptyState(
            "The meta is still forming",
            "Win, pick and ban rates appear once completed matches and drafts are logged.",
        )
        return
    }
    statTiles(
        listOf(
            state.totalMatches.toString() to "Rated matches",
            state.draftsWithBans.toString() to "Drafts with bans",
            state.largerTeamValue to state.largerTeamLabel,
        ),
    )
    state.night?.let { gameNight(it, state.streaks) }
    if (state.winConditions.total > 0) {
        sectionHeader("How games end")
        card { gamesEnd(state) }
    }
    heroRanking(state)
    p("pl-hint") { +state.rankingNote }
    if (state.rivals.isNotEmpty()) {
        sectionHeader("Rivalries")
        pairs(state.rivals, versus = true)
    }
    if (state.duos.isNotEmpty()) {
        sectionHeader("Best teammates")
        pairs(state.duos, versus = false)
    }
    if (state.synergies.isNotEmpty()) {
        sectionHeader("Strongest hero pairs")
        div("card pl-list") {
            state.synergies.forEach { pair ->
                div("pl-row pl-row-compact") {
                    span("pl-row-title pl-grow") { +"${pair.heroA} + ${pair.heroB}" }
                    span("pl-figure") {
                        +"${winLoss(pair.record.wins, pair.record.games)} · ${formatPercent(pair.record.winRate)}"
                    }
                }
            }
        }
    }
    a("/settings/ratings", classes = "btn btn-text pl-left") { +"How ratings are calculated" }
}

private fun FlowContent.gameNight(night: GameNightUi, streaks: List<StreakRow>) {
    sectionHeader("Last game night · ${Dates.night(night.date)}")
    card {
        div("pl-night-head") {
            span("pl-strong") { +plural(night.matches, "match", "matches") }
            span("pl-night-score") {
                teamBadge(Team.RED)
                span("pl-strong") { +" ${night.redWins}" }
                teamBadge(Team.BLUE)
                span("pl-strong") { +" ${night.blueWins}" }
            }
        }
        val movers = night.shownMovers
        if (movers.isNotEmpty()) {
            p("pl-subhead") { +"Biggest movers" }
            div("pl-list") {
                movers.forEach { mover ->
                    div("pl-row pl-row-compact pl-row-flat") {
                        mover.player?.let { playerAvatar(it, "sm") }
                        span("pl-row-title pl-grow") { +mover.name }
                        delta(mover.delta, label = "rating change that night")
                    }
                }
            }
        }
        if (streaks.isNotEmpty()) {
            p("pl-subhead") { +"Streaks" }
            div("pl-streaks") {
                streaks.forEach { s ->
                    span(if (s.won) "pl-streak pl-streak-won" else "pl-streak pl-streak-lost") {
                        s.player?.let { playerAvatar(it, "sm") }
                        span {
                            attributes["aria-hidden"] = "true"
                            +"${s.name} ${if (s.won) "W" else "L"}${s.length}"
                        }
                        srOnly("${s.name}, ${s.length} ${if (s.won) "wins" else "losses"} in a row")
                    }
                }
            }
        }
    }
}

private fun FlowContent.gamesEnd(state: MetaUiState) {
    winConditionBars(state.winConditions)
    val red = state.red
    div("pl-sides") {
        srOnly("Red won ${red.wins} of ${red.games}, Blue won ${red.losses}")
        span("pl-grow") {
            attributes["aria-hidden"] = "true"
            +"Sides"
        }
        span {
            attributes["aria-hidden"] = "true"
            teamBadge(Team.RED)
            +" ${red.wins} (${formatPercent(red.winRate)})  "
            teamBadge(Team.BLUE)
            +" ${red.losses}"
        }
    }
    GameMode.entries.forEach { mode ->
        state.durationByMode[mode]?.let { d ->
            div("pl-line") {
                span("pl-grow") { +"Average ${mode.label()}" }
                span("pl-figure") { +"${d.meanMinutes.roundToInt()} min · ${plural(d.matches, "match", "matches")}" }
            }
        }
    }
}

private fun FlowContent.heroRanking(state: MetaUiState) {
    div("pl-section-head") {
        sectionHeader("Hero ranking")
        nav("pl-toggle") {
            attributes["aria-label"] = "Hero ranking view"
            MetaView.entries.forEach { view ->
                val selected = state.view == view
                val query = if (view == MetaView.CARDS) "?view=cards" else "?sort=${state.sort.name}"
                a(
                    PlayersPaths.META + query,
                    classes = if (selected) "pl-segment-link selected" else "pl-segment-link",
                ) {
                    if (selected) attributes["aria-current"] = "true"
                    +(if (view == MetaView.TABLE) "Table" else "Cards")
                }
            }
        }
    }
    if (state.view == MetaView.TABLE) {
        nav("chip-row pl-sorts") {
            attributes["aria-label"] = "Sort heroes"
            HeroSort.entries.forEach { sort ->
                val selected = state.sort == sort
                a("${PlayersPaths.META}?sort=${sort.name}", classes = if (selected) "chip selected" else "chip") {
                    if (selected) attributes["aria-current"] = "true"
                    attributes["aria-label"] = "Sort by ${sort.label}"
                    +sort.label
                }
            }
        }
        heroTable(state)
    } else {
        if (state.ranked.isNotEmpty()) heroCards(state.ranked, ranked = true)
        if (state.unranked.isNotEmpty()) {
            sectionHeader("Not enough games yet")
            heroCards(state.unranked, ranked = false)
        }
    }
}

private fun FlowContent.heroTable(state: MetaUiState) {
    val rankOf = state.rankOf
    div("card pl-table-wrap") {
        table("pl-table pl-hero-table") {
            caption("visually-hidden") { +"Heroes, sorted by ${state.sort.label}" }
            thead {
                tr {
                    th(classes = "num") {
                        col()
                        +"#"
                    }
                    th {
                        col()
                        +"Hero"
                    }
                    th(classes = "num") {
                        col()
                        +"W–L"
                    }
                    th(classes = "num") {
                        col()
                        +"Win"
                    }
                    th(classes = "num") {
                        col()
                        +"Pick"
                    }
                    th(classes = "num") {
                        col()
                        +"Ban"
                    }
                }
            }
            tbody {
                state.sortedHeroes.forEach { row ->
                    val rank = rankOf[row.heroId]
                    val meta = row.meta
                    tr(if (rank == null) "pl-dim" else null) {
                        td("num pl-rank-cell") { +(rank?.toString() ?: "–") }
                        th {
                            attributes["scope"] = "row"
                            span("pl-hero-cell") {
                                heroPortrait(row.portrait, "xs")
                                span { +row.name }
                            }
                        }
                        td("num") { +winLoss(meta.record.wins, meta.record.games) }
                        td("num pl-strong") { +formatPercent(meta.winRate) }
                        td("num") { +formatPercent(meta.pickRate) }
                        td("num") { +(meta.banRate?.let(::formatPercent) ?: "—") }
                    }
                }
            }
        }
    }
}

private fun kotlinx.html.TH.col() {
    attributes["scope"] = "col"
}

private fun FlowContent.heroCards(rows: List<HeroMetaRow>, ranked: Boolean) {
    div("card pl-list") {
        rows.forEachIndexed { i, row ->
            val meta = row.meta
            div("pl-hero-card") {
                div("pl-row pl-row-flat") {
                    if (ranked) span("pl-rank-num") { +"${i + 1}" }
                    heroPortrait(row.portrait)
                    div("pl-row-text") {
                        span("pl-row-title") { +row.name }
                        span("row-subtitle") {
                            +"${winLoss(meta.record.wins, meta.record.games)} · ${plural(meta.record.games, "game")}"
                        }
                        srOnly(spokenRecord(meta.record.wins, meta.record.losses))
                    }
                    span("pl-elo") {
                        span("pl-elo-value") { +formatPercent(meta.winRate) }
                        span("pl-elo-caption") { +"≥ ${formatPercent(meta.wilsonLower)}" }
                    }
                }
                val share = ((meta.winRate ?: 0.0) * BAR_STEPS).roundToInt().coerceIn(0, BAR_STEPS)
                span("pl-bar pl-bar-wide") {
                    attributes["aria-hidden"] = "true"
                    span("pl-bar-fill w$share")
                }
                div("pl-metrics") {
                    metric("Pick", formatPercent(meta.pickRate))
                    metric("Ban", meta.banRate?.let(::formatPercent) ?: "—")
                    row.rating?.let { metric("Hero Elo", formatRating(it.value)) }
                    val best = row.bestPlayer
                    val record = row.bestRecord
                    if (best != null && record != null) {
                        span("pl-metric") {
                            span("pl-metric-label") { +"Best" }
                            playerAvatar(best, "sm")
                            span { +"${best.name} ${winLoss(record.wins, record.games)}" }
                        }
                    }
                }
                row.winsSummary?.let { p("pl-hint") { +it } }
            }
        }
    }
}

private fun FlowContent.metric(label: String, value: String) {
    span("pl-metric") {
        span("pl-metric-label") { +label }
        span { +value }
    }
}

private fun FlowContent.pairs(rows: List<PairRow>, versus: Boolean) {
    div("card pl-list") {
        rows.forEach { pair ->
            div("pl-row pl-row-compact") {
                val spoken = if (versus) {
                    "${pair.aName} against ${pair.bName}: ${spokenRecord(pair.record.wins, pair.record.losses)}"
                } else {
                    "${pair.aName} and ${pair.bName} together: ${spokenRecord(pair.record.wins, pair.record.losses)}"
                }
                srOnly(spoken)
                pair.a?.let { playerAvatar(it, "sm") }
                span("pl-row-title pl-grow") {
                    attributes["aria-hidden"] = "true"
                    +"${pair.aName} ${if (versus) "vs" else "+"} ${pair.bName}"
                }
                span("pl-figure") {
                    attributes["aria-hidden"] = "true"
                    +"${winLoss(pair.record.wins, pair.record.games)} · ${formatPercent(pair.record.winRate)}"
                }
            }
        }
    }
}
