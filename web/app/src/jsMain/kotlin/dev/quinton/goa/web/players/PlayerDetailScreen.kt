package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.feature.players.common.formatPercent
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.plural
import dev.quinton.goa.feature.players.common.spokenRecord
import dev.quinton.goa.feature.players.common.winLoss
import dev.quinton.goa.feature.players.player.HeroRecordRow
import dev.quinton.goa.feature.players.player.MergeCandidate
import dev.quinton.goa.feature.players.player.MergeOutcome
import dev.quinton.goa.feature.players.player.OpponentRow
import dev.quinton.goa.feature.players.player.PlayerDetail
import dev.quinton.goa.feature.players.player.PlayerDetailUiState
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** A player's page (PlayerDetailScreen): rating, history chart, heroes, rivals, recent matches, archive, merge. */
@Composable
fun PlayerDetailScreen(playerId: String) {
    val services = LocalServices.current
    val presenter = remember { playersPresenter(services) }
    val group = services.data.group
    val state by remember(playerId) {
        combine(
            group.players.observePlayers(includeArchived = true),
            group.matches.observeMatches(),
            group.ratingsSnapshot.observeSnapshot(),
            presenter.catalog,
        ) { people, log, snapshot, catalog ->
            PlayerDetail.build(playerId, people, log, snapshot, catalog, WebDateLabels)
        }
    }.collectAsState(PlayerDetailUiState())
    val player = state.player
    Screen(
        player?.name ?: "Player",
        actions = { if (player != null) IconLink("g_edit", "Edit player", Route.PlayerEdit(playerId)) },
    ) {
        when {
            state.loading -> LoadingState()

            player == null -> EmptyState("person", "Player not found", "This player no longer exists.")

            else -> {
                Header(state)
                StatRow(
                    listOf(
                        state.rating.games.toString() to "Games",
                        (if (state.rating.games > 0) winLoss(state.rating.wins, state.rating.games) else "—") to
                            "Won–lost",
                        (
                            if (state.rating.games > 0) {
                                formatPercent(state.rating.wins.toDouble() / state.rating.games)
                            } else {
                                "—"
                            }
                            ) to "Win rate",
                        state.doublePilotGames.toString() to "Double pilot",
                    ),
                )
                SectionHeader("Rating history")
                GoaCard {
                    if (state.history.isEmpty()) {
                        EmptyState(
                            "g_insights",
                            "No rated matches yet",
                            "The chart starts with ${player.name}'s first completed match.",
                        )
                    } else {
                        RatingChart(state.history, state.initialRating, state.historyDates)
                        state.peak?.let {
                            P({ classes("t-label-medium", "c-variant", "goa-w2-tight") }) {
                                Text("Peak ${formatRating(it)} · start ${formatRating(state.initialRating)}")
                            }
                        }
                    }
                }
                if (state.heroes.isNotEmpty()) {
                    SectionHeader(if (state.heroesAreBest) "Best heroes" else "Most played")
                    GoaCard(flush = true) { state.heroes.forEach { HeroRecordItem(it) } }
                }
                if (state.winConditions.total > 0) {
                    SectionHeader("How ${player.name} wins")
                    GoaCard { WinConditionBars(state.winConditions) }
                }
                if (state.rivals.isNotEmpty()) {
                    SectionHeader("Head to head")
                    OpponentCard(state.rivals, versus = true)
                }
                if (state.teammates.isNotEmpty()) {
                    SectionHeader("Teammates")
                    OpponentCard(state.teammates, versus = false)
                }
                if (state.totalMatches > state.recentMatches.size) {
                    Div({ classes("goa-section-header", "goa-w2-section-trailing") }) {
                        H2({ classes("t-section-head") }) { Text("Recent matches") }
                        GoaButton("All ${state.totalMatches}", {
                            services.router.navigate(Route.Matches)
                        }, ButtonKind.TEXT)
                    }
                } else {
                    SectionHeader("Recent matches")
                }
                if (state.recentMatches.isEmpty()) {
                    P({ classes("t-body-medium", "c-variant") }) { Text("No matches logged with ${player.name} yet.") }
                }
                Div({ classes("goa-w2-match-list") }) {
                    state.recentMatches.forEach { MatchCard(it, presenter = null) }
                }
                ArchiveAndMerge(state, playerId)
            }
        }
    }
}

@Composable
private fun Header(state: PlayerDetailUiState) {
    val player = state.player ?: return
    val rating = state.rating
    Div({ classes("goa-w2-detail-head") }) {
        PlayerAvatar(player, 72)
        Div({ classes("goa-w2-grow") }) {
            H2({ classes("t-headline-small", "goa-w2-tight0") }) { Text(player.name) }
            Div({ classes("goa-w2-flow") }) {
                state.rank?.let {
                    Span({ classes("t-label-large", "c-variant") }) { Text("Rank $it of ${state.rankedPlayers}") }
                }
                if (rating.games > 0 && rating.provisional) Pill("Provisional")
                if (player.archived) Pill("Archived")
            }
        }
        Div({ classes("goa-w2-elo") }) {
            Span({ classes("t-display-small", "c-primary") }) {
                Text(if (rating.games > 0) formatRating(rating.value) else "—")
            }
            Span({ classes("t-label-medium", "c-variant") }) { Text("Elo") }
        }
    }
}

@Composable
private fun OpponentCard(rows: List<OpponentRow>, versus: Boolean) {
    GoaCard(flush = true) {
        rows.forEach { row ->
            Div({
                classes("goa-w2-pair")
                attr(
                    "aria-label",
                    "${if (versus) "Against" else "With"} ${row.name}: " +
                        spokenRecord(row.record.wins, row.record.losses),
                )
            }) {
                PlayerAvatar(row.player, 28)
                Span({
                    classes("t-body-large", "goa-w2-grow", "goa-w2-ellipsis")
                    attr("aria-hidden", "true")
                }) { Text("${if (versus) "vs" else "with"} ${row.name}") }
                Span({
                    classes("t-label-large", "c-variant")
                    attr("aria-hidden", "true")
                }) { Text("${winLoss(row.record.wins, row.record.games)} · ${formatPercent(row.record.winRate)}") }
            }
        }
    }
}

@Composable
private fun HeroRecordItem(hero: HeroRecordRow) {
    Div({
        classes("goa-w2-pair", "goa-w2-min56")
        attr(
            "aria-label",
            "${hero.name}, ${spokenRecord(
                hero.record.wins,
                hero.record.losses,
            )}, win rate ${formatPercent(hero.record.winRate)}",
        )
    }) {
        HeroThumb(hero.name, hero.portrait, 40)
        Span({
            classes("t-title-medium", "goa-w2-grow", "goa-w2-ellipsis")
            attr("aria-hidden", "true")
        }) { Text(hero.name) }
        Div({
            classes("goa-w2-elo")
            attr("aria-hidden", "true")
        }) {
            Span({ classes("t-title-medium") }) { Text(formatPercent(hero.record.winRate)) }
            Span({ classes("t-label-medium", "c-variant") }) { Text(winLoss(hero.record.wins, hero.record.games)) }
        }
    }
}

/** Archive / restore, and "Merge into…" (MergeDialogs): one person with two players. */
@Composable
private fun ArchiveAndMerge(state: PlayerDetailUiState, playerId: String) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val player = state.player ?: return
    var picking by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<MergeCandidate?>(null) }
    var refused by remember { mutableStateOf<MergeOutcome.PlayedTogether?>(null) }
    Div({ classes("goa-w2-archive") }) {
        GoaButton(
            if (player.archived) "Restore player" else "Archive player",
            { scope.launch { services.data.group.players.archive(playerId, !player.archived) } },
            ButtonKind.OUTLINED,
            icon = if (player.archived) "g_unarchive" else "g_archive",
        )
        P({ classes("t-body-small", "c-variant") }) {
            Text(
                "Archived players are hidden from the leaderboard and pickers. " +
                    "Their matches and ratings stay in the log.",
            )
        }
        if (state.mergeCandidates.isNotEmpty()) {
            GoaButton("Merge into…", { picking = true }, ButtonKind.TEXT, icon = "g_merge")
            P({ classes("t-body-small", "c-variant") }) {
                Text(
                    "Added twice (e.g. on two phones)? Merge this player into the other one: their matches and " +
                        "ratings become one, on every phone after the next sync.",
                )
            }
        }
    }
    if (picking) {
        BottomSheet("Merge ${player.name} into…", onDismiss = { picking = false }) {
            RowList {
                state.mergeCandidates.forEach { candidate ->
                    ListRow(
                        candidate.player.name,
                        subtitle = plural(candidate.matches, "match", "matches"),
                        leading = { PlayerAvatar(candidate.player, 40) },
                        onClick = {
                            picking = false
                            target = candidate
                        },
                    )
                }
            }
        }
    }
    target?.let { into ->
        AlertDialog(
            "Merge into ${into.player.name}?",
            onDismiss = { target = null },
            buttons = {
                GoaButton("Cancel", { target = null }, ButtonKind.TEXT)
                GoaButton("Merge", {
                    target = null
                    scope.launch {
                        val people = services.data.group.players.observePlayers(includeArchived = true).first()
                            .associateBy { it.id }
                        when (val result = services.data.group.playerMerges.merge(playerId, into.player.id)) {
                            PlayerMergeResult.Merged -> {
                                // This player now reads as the other one: show that page instead.
                                services.router.navigate(Route.PlayerDetail(into.player.id), replace = true)
                                services.messages.show(PlayerDetail.mergedMessage(into.player.name))
                            }

                            is PlayerMergeResult.PlayedTogether ->
                                refused = PlayerDetail.playedTogether(result.matches, people, WebDateLabels)

                            PlayerMergeResult.NotPossible -> Unit
                        }
                    }
                }, ButtonKind.DANGER)
            },
        ) {
            P {
                Text(
                    "${player.name} (${plural(state.totalMatches, "match", "matches")}) becomes " +
                        "${into.player.name} (${plural(
                            into.matches,
                            "match",
                            "matches",
                        )}). Their matches and ratings " +
                        "count as one player from now on, and the other phones follow at the next sync. " +
                        "${player.name} disappears from the lists. This can't be undone.",
                )
            }
        }
    }
    refused?.let { outcome ->
        AlertDialog("Can't merge", onDismiss = { refused = null }, buttons = {
            GoaButton("OK", { refused = null }, ButtonKind.TEXT)
        }) {
            P {
                Text(
                    "They played in the same match, so they are two people (one person can't sit on both teams, " +
                        "and teammates are two players):",
                )
            }
            outcome.matches.forEach { P({ classes("goa-w2-tight0") }) { Text("• $it") } }
        }
    }
}
