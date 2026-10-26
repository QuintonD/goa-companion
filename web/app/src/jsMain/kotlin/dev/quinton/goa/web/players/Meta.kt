package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.domain.meta.WinConditionMix
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.feature.players.common.formatPercent
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.label
import dev.quinton.goa.feature.players.common.plural
import dev.quinton.goa.feature.players.common.spokenRecord
import dev.quinton.goa.feature.players.common.winLoss
import dev.quinton.goa.feature.players.meta.GameNightUi
import dev.quinton.goa.feature.players.meta.HeroMetaRow
import dev.quinton.goa.feature.players.meta.HeroSort
import dev.quinton.goa.feature.players.meta.MetaUiState
import dev.quinton.goa.feature.players.meta.MetaView
import dev.quinton.goa.feature.players.meta.PairRow
import dev.quinton.goa.feature.players.meta.StreakRow
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ChipRow
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.LinearProgress
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.Segmented
import kotlin.math.roundToInt
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The Meta tab (MetaContent): the last game night, how games end, sides and length, the hero
 * ranking (compact sortable table or cards, Wilson-ranked), rivalries and duos, hero pairs. All
 * from the match log, built by the shared MetaBoard.
 */
@Composable
fun MetaContent(state: MetaUiState, presenter: PlayersPresenter) {
    val router = LocalServices.current.router
    when {
        state.loading -> LoadingState()

        state.isEmpty -> EmptyState(
            "g_insights",
            "The meta is still forming",
            "Win, pick and ban rates appear once completed matches and drafts are logged.",
        )

        else -> {
            val view = presenter.metaView
            val shown = state.copy(view = view, sort = presenter.heroSort)
            StatRow(
                listOf(
                    state.totalMatches.toString() to "Rated matches",
                    state.draftsWithBans.toString() to "Drafts with bans",
                    (if (state.largerTeam.games == 0) "—" else formatPercent(state.largerTeam.winRate)) to
                        ("Larger team won" + if (state.largerTeam.games > 0) " (${state.largerTeam.games})" else ""),
                ),
            )
            state.night?.let { GameNightCard(it, state.streaks) }
            if (state.winConditions.total > 0) {
                SectionHeader("How games end")
                GamesCard(state)
            }
            HeroHeader(shown, presenter)
            if (view == MetaView.TABLE) {
                HeroCompactTable(shown)
            } else {
                if (state.ranked.isNotEmpty()) HeroCards(state.ranked, ranked = true)
                if (state.unranked.isNotEmpty()) {
                    SectionHeader("Not enough games yet")
                    HeroCards(state.unranked, ranked = false)
                }
            }
            P({ classes("t-body-small", "c-variant") }) {
                Text(
                    "Heroes and pairs are ranked by the 95 % Wilson lower bound of their win rate, so small " +
                        "samples don't top the table. Ranking needs ${plural(state.minSample, "game")}.",
                )
            }
            if (state.rivals.isNotEmpty()) {
                SectionHeader("Rivalries")
                PairsCard(state.rivals, versus = true)
            }
            if (state.duos.isNotEmpty()) {
                SectionHeader("Best teammates")
                PairsCard(state.duos, versus = false)
            }
            if (state.synergies.isNotEmpty()) {
                SectionHeader("Strongest hero pairs")
                GoaCard(flush = true) {
                    state.synergies.forEach { pair ->
                        LineRow(
                            "${pair.heroA} + ${pair.heroB}",
                            "${winLoss(pair.record.wins, pair.record.games)} · ${formatPercent(pair.record.winRate)}",
                        )
                    }
                }
            }
            Div({ classes("goa-w2-spaced") }) {
                GoaButton(
                    "How ratings are calculated",
                    { router.navigate(Route.RatingSettings) },
                    ButtonKind.TEXT,
                    icon = "g_tune",
                )
            }
        }
    }
}

@Composable
private fun GameNightCard(night: GameNightUi, streaks: List<StreakRow>) {
    SectionHeader("Last game night · ${WebDateLabels.night(night.date)}")
    GoaCard {
        Div({ classes("goa-w2-line") }) {
            Span({ classes("t-title-medium", "goa-w2-grow") }) { Text(plural(night.matches, "match", "matches")) }
            Span({ attr("aria-label", "Red ${night.redWins}, Blue ${night.blueWins}") }) {
                TeamBadge(Team.RED, showLabel = false)
                Span({ classes("t-title-medium") }) { Text(" ${night.redWins}  ") }
                TeamBadge(Team.BLUE, showLabel = false)
                Span({ classes("t-title-medium") }) { Text(" ${night.blueWins}") }
            }
        }
        val up = night.movers.filter { it.delta.roundToInt() > 0 }.take(TOP_MOVERS)
        val down = night.movers.filter { it.delta.roundToInt() < 0 }.reversed().take(1)
        if (up.isNotEmpty() || down.isNotEmpty()) {
            P({ classes("t-label-large", "c-variant", "goa-w2-tight") }) { Text("Biggest movers") }
            (up + down).forEach { mover ->
                Div({ classes("goa-w2-line", "goa-w2-min40") }) {
                    PlayerAvatar(mover.player, 28)
                    Span({ classes("t-body-large", "goa-w2-grow") }) { Text(mover.name) }
                    DeltaIndicator(mover.delta, label = "rating change that night")
                }
            }
        }
        if (streaks.isNotEmpty()) {
            P({ classes("t-label-large", "c-variant", "goa-w2-tight") }) { Text("Streaks") }
            Div({ classes("goa-w2-flow") }) {
                streaks.forEach { s ->
                    Span({
                        classes("goa-w2-inline")
                        attr("aria-label", "${s.name}, ${s.length} ${if (s.won) "wins" else "losses"} in a row")
                    }) {
                        PlayerAvatar(s.player, 20)
                        Span({
                            classes("t-label-large", if (s.won) "goa-w2-win" else "c-error")
                            attr("aria-hidden", "true")
                        }) { Text("${s.name} ${if (s.won) "W" else "L"}${s.length}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GamesCard(state: MetaUiState) {
    GoaCard {
        WinConditionBars(state.winConditions)
        Div({ classes("goa-w2-divider") }) {}
        val red = state.red
        Div({
            classes("goa-w2-line")
            attr("aria-label", "Red won ${red.wins} of ${red.games}, Blue won ${red.losses}")
        }) {
            Span({ classes("t-body-medium", "goa-w2-grow") }) { Text("Sides") }
            TeamBadge(Team.RED, showLabel = false)
            Span({ classes("t-label-large") }) { Text(" ${red.wins} (${formatPercent(red.winRate)})   ") }
            TeamBadge(Team.BLUE, showLabel = false)
            Span({ classes("t-label-large") }) { Text(" ${red.losses}") }
        }
        GameMode.entries.forEach { mode ->
            state.durationByMode[mode]?.let { d ->
                Div({ classes("goa-w2-line", "goa-w2-min40") }) {
                    Span({ classes("t-body-medium", "goa-w2-grow") }) { Text("Average ${mode.label()}") }
                    Span({ classes("t-label-large", "c-variant") }) {
                        Text("${d.meanMinutes.roundToInt()} min · ${plural(d.matches, "match", "matches")}")
                    }
                }
            }
        }
    }
}

/** Share of wins per condition as labelled bars ("Not recorded" when no condition was logged). */
@Composable
fun WinConditionBars(mix: WinConditionMix) {
    Div({ classes("goa-w2-bars") }) {
        (WinCondition.entries + listOf(null)).forEach { condition ->
            val count = mix.counts[condition] ?: 0
            if (count == 0 && condition == null) return@forEach
            val name = condition?.label() ?: "Not recorded"
            Div({
                classes("goa-w2-bar")
                attr("aria-label", "$name, $count of ${mix.total}")
            }) {
                Span({
                    classes("t-body-medium", "goa-w2-bar-label")
                    attr("aria-hidden", "true")
                }) { Text(name) }
                Div({ classes("goa-w2-grow") }) { LinearProgress(mix.share(condition) ?: 0.0, name) }
                Span({
                    classes("t-label-large", "goa-w2-bar-count")
                    attr("aria-hidden", "true")
                }) { Text("$count") }
            }
        }
    }
}

@Composable
private fun HeroHeader(state: MetaUiState, presenter: PlayersPresenter) {
    Div({ classes("goa-w2-hero-head") }) {
        H2({ classes("t-section-head", "goa-w2-grow") }) { Text("Hero ranking") }
        Segmented("Hero ranking view", MetaView.entries, state.view, {
            if (it ==
                MetaView.TABLE
            ) {
                "Table"
            } else {
                "Cards"
            }
        }) {
            presenter.metaView = it
        }
    }
    if (state.view == MetaView.TABLE) {
        ChipRow {
            HeroSort.entries.forEach { sort ->
                Chip(sort.label, state.sort == sort, onClick = { presenter.heroSort = sort })
            }
        }
    }
}

/** Compact table: one 48 px line per hero. Unranked heroes are dimmed and have no rank number. */
@Composable
private fun HeroCompactTable(state: MetaUiState) {
    val rankOf = state.ranked.mapIndexed { i, row -> row.heroId to i + 1 }.toMap()
    GoaCard(flush = true) {
        Div({ classes("goa-w2-table") }) {
            Div({
                classes("goa-w2-table-row", "head", "t-label-medium", "c-variant")
                attr("aria-hidden", "true")
            }) {
                Span({ classes("rank") }) { Text("#") }
                Span({ classes("name") }) { Text("Hero") }
                Span({ classes("num", "wl") }) { Text("W–L") }
                Span({ classes("num") }) { Text("Win") }
                Span({ classes("num", "wide") }) { Text("Pick") }
                Span({ classes("num", "wide") }) { Text("Ban") }
            }
            state.sortedHeroes.forEach { row ->
                val rank = rankOf[row.heroId]
                val meta = row.meta
                val description = buildString {
                    rank?.let { append("Rank $it, ") } ?: append("Not ranked yet, ")
                    append("${row.name}, ${spokenRecord(meta.record.wins, meta.record.losses)}")
                    append(", win rate ${formatPercent(meta.winRate)}, pick rate ${formatPercent(meta.pickRate)}")
                    meta.banRate?.let { append(", ban rate ${formatPercent(it)}") }
                    row.rating?.let { append(", hero rating ${formatRating(it.value)}") }
                }
                Div({
                    classes("goa-w2-table-row", "t-label-large")
                    if (rank == null) classes("dim")
                    attr("role", "listitem")
                    attr("aria-label", description)
                }) {
                    Span({
                        classes("rank")
                        if (rank != null) classes("c-primary", "bold")
                        attr("aria-hidden", "true")
                    }) { Text(rank?.toString() ?: "–") }
                    Span({
                        classes("name")
                        attr("aria-hidden", "true")
                    }) {
                        HeroThumb(row.name, row.portrait, 28)
                        Span({ classes("t-body-medium", "goa-w2-ellipsis") }) { Text(row.name) }
                    }
                    Span({
                        classes("num", "wl")
                        attr("aria-hidden", "true")
                    }) { Text(winLoss(meta.record.wins, meta.record.games)) }
                    Span({
                        classes("num", "bold")
                        attr("aria-hidden", "true")
                    }) { Text(formatPercent(meta.winRate)) }
                    Span({
                        classes("num", "wide")
                        attr("aria-hidden", "true")
                    }) { Text(formatPercent(meta.pickRate)) }
                    Span({
                        classes("num", "wide")
                        attr("aria-hidden", "true")
                    }) { Text(meta.banRate?.let { formatPercent(it) } ?: "—") }
                }
            }
        }
    }
}

@Composable
private fun PairsCard(rows: List<PairRow>, versus: Boolean) {
    GoaCard(flush = true) {
        rows.forEach { pair ->
            val joiner = if (versus) "vs" else "+"
            Div({
                classes("goa-w2-pair")
                attr(
                    "aria-label",
                    if (versus) {
                        "${pair.aName} against ${pair.bName}: " + spokenRecord(pair.record.wins, pair.record.losses)
                    } else {
                        "${pair.aName} and ${pair.bName} together: " +
                            spokenRecord(pair.record.wins, pair.record.losses)
                    },
                )
            }) {
                PlayerAvatar(pair.a, 24)
                Span({
                    classes("t-body-large", "goa-w2-grow")
                    attr("aria-hidden", "true")
                }) { Text("${pair.aName} $joiner ${pair.bName}") }
                Span({
                    classes("t-label-large", "c-variant")
                    attr("aria-hidden", "true")
                }) { Text("${winLoss(pair.record.wins, pair.record.games)} · ${formatPercent(pair.record.winRate)}") }
            }
        }
    }
}

@Composable
private fun LineRow(label: String, value: String) {
    Div({ classes("goa-w2-pair") }) {
        Span({ classes("t-body-medium", "goa-w2-grow") }) { Text(label) }
        Span({ classes("t-label-large", "c-variant") }) { Text(value) }
    }
}

@Composable
private fun HeroCards(rows: List<HeroMetaRow>, ranked: Boolean) {
    GoaCard(flush = true) {
        rows.forEachIndexed { i, row -> HeroMetaItem(row, rank = if (ranked) i + 1 else null) }
    }
}

@Composable
private fun HeroMetaItem(row: HeroMetaRow, rank: Int?) {
    val meta = row.meta
    val description = buildString {
        rank?.let { append("Rank $it, ") }
        append(row.name)
        append(", ${spokenRecord(meta.record.wins, meta.record.losses)}")
        append(", win rate ${formatPercent(meta.winRate)}, at least ${formatPercent(meta.wilsonLower)}")
        append(", pick rate ${formatPercent(meta.pickRate)}")
        meta.banRate?.let { append(", ban rate ${formatPercent(it)}") }
        row.rating?.let { append(", hero rating ${formatRating(it.value)}") }
        row.bestPlayer?.let { append(", best pilot ${it.name}") }
    }
    Div({
        classes("goa-w2-hero-card")
        attr("aria-label", description)
    }) {
        Div({
            classes("goa-w2-line")
            attr("aria-hidden", "true")
        }) {
            if (rank !=
                null
            ) {
                Span({ classes("t-label-large", "c-primary", "bold", "goa-w2-rank-num") }) { Text("$rank") }
            }
            HeroThumb(row.name, row.portrait, 40)
            Div({ classes("goa-w2-grow") }) {
                Div({ classes("t-title-medium", "goa-w2-ellipsis") }) { Text(row.name) }
                Div({ classes("t-body-small", "c-variant") }) {
                    Text("${winLoss(meta.record.wins, meta.record.games)} · ${plural(meta.record.games, "game")}")
                }
            }
            Div({ classes("goa-w2-elo") }) {
                Span({ classes("t-title-large") }) { Text(formatPercent(meta.winRate)) }
                Span({ classes("t-label-medium", "c-variant") }) { Text("≥ ${formatPercent(meta.wilsonLower)}") }
            }
        }
        Div({
            classes("goa-w2-tight")
            attr("aria-hidden", "true")
        }) { LinearProgress(meta.winRate ?: 0.0, "Win rate") }
        Div({
            classes("goa-w2-flow", "goa-w2-tight")
            attr("aria-hidden", "true")
        }) {
            Metric("Pick", formatPercent(meta.pickRate))
            Metric("Ban", meta.banRate?.let { formatPercent(it) } ?: "—")
            row.rating?.let { Metric("Hero Elo", formatRating(it.value)) }
            val bestPlayer = row.bestPlayer
            val bestRecord = row.bestRecord
            if (bestPlayer != null && bestRecord != null) {
                Span({ classes("goa-w2-inline") }) {
                    Span({ classes("t-label-medium", "c-variant") }) { Text("Best") }
                    PlayerAvatar(bestPlayer, 20)
                    Span({
                        classes("t-label-large")
                    }) { Text("${bestPlayer.name} ${winLoss(bestRecord.wins, bestRecord.games)}") }
                }
            }
        }
        if (row.winConditions.total > 0) {
            P({
                classes("t-body-small", "c-variant", "goa-w2-tight")
                attr("aria-hidden", "true")
            }) {
                Text(
                    "Wins: " + (WinCondition.entries + listOf(null))
                        .mapNotNull { c -> row.winConditions.counts[c]?.let { "${c?.label() ?: "not recorded"} $it" } }
                        .joinToString(" · "),
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Span({ classes("goa-w2-inline") }) {
        Span({ classes("t-label-medium", "c-variant") }) { Text(label) }
        Span({ classes("t-label-large") }) { Text(value) }
    }
}

private const val TOP_MOVERS = 3
