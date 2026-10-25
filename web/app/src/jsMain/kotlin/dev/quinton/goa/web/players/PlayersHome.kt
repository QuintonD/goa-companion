package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.plural
import dev.quinton.goa.feature.players.common.spokenRecord
import dev.quinton.goa.feature.players.common.winLoss
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.meta.MetaUiState
import dev.quinton.goa.feature.players.players.LeaderboardRow
import dev.quinton.goa.feature.players.players.PlayersUiState
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.nav.Routes
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.RouteLink
import dev.quinton.goa.web.ui.Screen
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** The Players tab's three views (`PlayersTab` on the phone), each its own address. */
enum class PlayersTab(val label: String, val route: Route) {
    PLAYERS("Players", Route.Players),
    MATCHES("Matches", Route.Matches),
    META("Meta", Route.Meta),
}

private var presenter: PlayersPresenter? = null

/** The app's one [PlayersPresenter]. */
fun playersPresenter(services: AppServices): PlayersPresenter =
    presenter ?: PlayersPresenter(services.data.group, services.data.content.catalog).also { presenter = it }

/**
 * The Players tab (PlayersHomeScreen): leaderboard, match log and meta as tabs, with "your page",
 * the sync icon (a dot when the sync server needs a person) and Settings in the top bar.
 */
@Composable
fun PlayersHomeScreen(tab: PlayersTab) {
    val services = LocalServices.current
    val presenter = remember { playersPresenter(services) }
    val players by remember { presenter.players }.collectAsState(PlayersUiState())
    Screen(
        "Players",
        // Players, Matches and Meta are tabs of one screen: nothing to go back to (the phone's too).
        showBack = false,
        actions = {
            players.meId?.let { me -> IconLink("g_account", "Your player page", Route.PlayerDetail(me)) }
            SyncAction(players)
            IconLink("settings", "Settings", Route.Settings)
        },
    ) {
        PlayersTabs(tab)
        Div({ attr("role", "tabpanel") }) {
            when (tab) {
                PlayersTab.PLAYERS -> LeaderboardContent(players, presenter)

                PlayersTab.MATCHES -> {
                    val log by remember { presenter.matchLog }.collectAsState(MatchLogUiState())
                    MatchLogContent(log, presenter)
                }

                PlayersTab.META -> {
                    val meta by remember { presenter.meta }.collectAsState(MetaUiState())
                    MetaContent(meta, presenter)
                }
            }
        }
        when (tab) {
            PlayersTab.PLAYERS -> if (!players.isEmpty && !players.loading) {
                Fab("g_person_add", "Add player") { services.router.navigate(Route.PlayerEdit()) }
            }

            PlayersTab.MATCHES -> Fab("g_add", "Log a match") { services.router.navigate(Route.MatchEdit()) }

            PlayersTab.META -> Unit
        }
    }
}

/**
 * The sync icon: a problem a person must fix opens the Group screen, where it is explained; with
 * a server it syncs now; otherwise it opens the Group screen (a browser has no face-to-face sync).
 */
@Composable
private fun SyncAction(players: PlayersUiState) {
    val services = LocalServices.current
    val label = when {
        players.serverNeedsAttention -> "Group sync: the sync server needs attention"
        players.serverSyncOn -> "Sync with the sync server"
        players.inGroup -> "Sync with the group"
        else -> "Group sync"
    }
    Span({ classes("goa-w2-badged") }) {
        IconButton("sync", label, onClick = {
            when {
                players.serverNeedsAttention -> services.router.navigate(Route.Group)

                players.serverSyncOn -> {
                    services.data.group.syncServerControl.syncNow()
                    services.messages.show("Syncing with the sync server…", "Group sync") {
                        services.router.navigate(Route.Group)
                    }
                }

                else -> services.router.navigate(Route.Group)
            }
        })
        if (players.serverNeedsAttention) Span({ classes("goa-w2-dot") }) {}
    }
}

/** Players · Matches · Meta: links (each tab has its address), styled as tabs. */
@Composable
private fun PlayersTabs(current: PlayersTab) {
    Div({
        classes("goa-w2-tabs")
        attr("role", "tablist")
    }) {
        val router = LocalServices.current.router
        PlayersTab.entries.forEach { tab ->
            // Switching tabs replaces the entry (Back leaves the Players tab, as on the phone).
            A(Routes.hash(tab.route), {
                classes("goa-w2-tab", "t-title-small")
                attr("role", "tab")
                attr("aria-selected", (tab == current).toString())
                onClick { event ->
                    event.preventDefault()
                    if (tab != current) router.navigate(tab.route, replace = true)
                }
            }) { Text(tab.label) }
        }
    }
}

/** An extended floating action button above the tab bar. */
@Composable
fun Fab(icon: String, label: String, onClick: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-w2-fab", "t-label-large")
        onClick { onClick() }
    }) {
        Icon(icon)
        Span { Text(label) }
    }
}

/** The "Players" tab: Elo leaderboard, then unrated and (optionally) archived players. */
@Composable
private fun LeaderboardContent(state: PlayersUiState, presenter: PlayersPresenter) {
    val router = LocalServices.current.router
    val showArchived by presenter.showArchived.collectAsState()
    when {
        state.loading -> LoadingState()

        state.isEmpty -> EmptyState(
            "g_groups",
            "Gather your guards",
            "Add everyone in your play group. Ratings appear after the first logged match.",
        ) { GoaButton("Add player", { router.navigate(Route.PlayerEdit()) }, icon = "g_person_add") }

        else -> Div({ classes("goa-w2-fab-clear") }) {
            if (state.ranked.isNotEmpty()) {
                SectionWithTrailing("Leaderboard", plural(state.ratedMatches, "rated match", "rated matches"))
                GoaCard(flush = true) {
                    Ul({ classes("goa-list") }) { state.ranked.forEach { LeaderboardItem(it) } }
                }
            }
            if (state.unrated.isNotEmpty()) {
                dev.quinton.goa.web.ui.SectionHeader("No games yet")
                GoaCard(flush = true) {
                    Ul({ classes("goa-list") }) { state.unrated.forEach { LeaderboardItem(it) } }
                }
            }
            if (state.archived.isNotEmpty()) {
                Div({ classes("goa-w2-spaced") }) {
                    GoaButton(
                        if (showArchived) "Hide archived players" else "Show archived players (${state.archived.size})",
                        { presenter.showArchived.value = !presenter.showArchived.value },
                        kind = ButtonKind.TEXT,
                        icon = "g_archive",
                    )
                }
                if (showArchived) {
                    GoaCard(flush = true) {
                        Ul({ classes("goa-list") }) { state.archived.forEach { LeaderboardItem(it, archived = true) } }
                    }
                }
            }
        }
    }
}

/** A section header with a small note on the right ("12 rated matches"). */
@Composable
fun SectionWithTrailing(title: String, trailing: String) {
    Div({ classes("goa-section-header", "goa-w2-section-trailing") }) {
        H2({ classes("t-section-head") }) { Text(title) }
        Span({ classes("t-label-medium", "c-variant") }) { Text(trailing) }
    }
}

@Composable
private fun LeaderboardItem(row: LeaderboardRow, archived: Boolean = false) {
    val rating = row.rating
    val description = buildString {
        row.rank?.let { append("Rank $it, ") }
        append(row.player.name)
        if (row.isMe) append(", you")
        if (rating.games > 0) {
            append(", rating ${formatRating(rating.value)}")
            if (rating.provisional) append(", provisional")
            append(", ${spokenRecord(rating.wins, rating.losses)}")
            row.trend?.let { append(", ${formatDelta(it)} over the last ${plural(row.trendGames, "game")}") }
        } else {
            append(", no games yet")
        }
        if (archived) append(", archived")
    }
    Li {
        RouteLink(Route.PlayerDetail(row.player.id), {
            classes("goa-row", "goa-w2-leader")
            attr("aria-label", description)
        }) {
            Span({
                classes("goa-w2-rank", "t-label-large")
                when (row.rank) {
                    null -> Unit
                    1 -> classes("first")
                    2, 3 -> classes("podium")
                    else -> Unit
                }
                attr("aria-hidden", "true")
            }) { Text(row.rank?.toString() ?: "–") }
            PlayerAvatar(row.player, size = 44)
            Span({
                classes("goa-row-text")
                attr("aria-hidden", "true")
            }) {
                Span({ classes("goa-w2-name-line") }) {
                    Span({
                        classes("goa-row-title", "t-title-medium", "goa-w2-ellipsis")
                        if (archived) classes("c-variant")
                    }) { Text(row.player.name) }
                    if (row.isMe) Span({ classes("goa-w2-you", "t-label-medium") }) { Text("You") }
                }
                Span({ classes("goa-w2-meta-line", "t-body-small", "c-variant") }) {
                    if (rating.games > 0) {
                        Span { Text(winLoss(rating.wins, rating.games)) }
                        DeltaIndicator(row.trend, caption = "last ${row.trendGames}")
                        if (rating.provisional) Pill("Provisional")
                    } else {
                        Span { Text(if (archived) "Archived" else "Log a match to get rated") }
                    }
                }
            }
            if (rating.games > 0) {
                Span({
                    classes("goa-w2-elo")
                    attr("aria-hidden", "true")
                }) {
                    Span({
                        classes("t-title-large")
                        if (row.rank == 1) classes("c-primary")
                    }) { Text(formatRating(rating.value)) }
                    Span({ classes("t-label-medium", "c-variant") }) { Text("Elo") }
                }
            } else {
                Icon("chevron_right", IconSize.SMALL)
            }
        }
    }
}
