package dev.quinton.goa.web.play.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.RoomSeat
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.online.LobbyUi
import dev.quinton.goa.feature.board.play.online.OnlineLobbyPresenter
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.table.HeroPortrait
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.sync.QrCode
import dev.quinton.goa.web.sync.shareText
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.ErrorState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.rememberGuardedScope
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** What the lobby needs before it can show anything. */
private class LobbyInputs(val playable: List<String>, val heroes: Map<String, BoardHero>, val mapName: (String) -> String)

/**
 * A room's lobby (ADR-056): the seats by team, picking a hero, ready and start, the invite. Seats and heroes are the
 * server's to give, so a pick shows once the room says so. Names are text only: anyone can pick any name.
 */
@Composable
fun OnlineLobbyScreen(code: String) {
    val services = LocalServices.current
    val online = services.data.online
    val loaded = rememberLoad(code) {
        val maps = services.data.play.boardMaps.maps.first().associate { it.id to it.name }
        LobbyInputs(online.playableHeroes(), online.catalog().boardHeroes()) { maps[it] ?: it }
    }
    Screen("Room $code") {
        when (val state = loaded.state) {
            Load.Loading -> LoadingState("Opening the room")

            is Load.Failed -> ErrorState(
                "The room can't be opened",
                "The app's data didn't load. Check the connection and try again.",
                onRetry = loaded.retry,
            )

            is Load.Ready -> Lobby(code, state.value)
        }
    }
}

@Composable
private fun Lobby(code: String, inputs: LobbyInputs) {
    val services = LocalServices.current
    val online = services.data.online
    val router = services.router
    val scope = rememberGuardedScope { services.messages.show("Something went wrong in this room. Open it again.") }
    val presenter = remember {
        OnlineLobbyPresenter(
            code = code,
            gateway = online.gateway,
            connector = online.connector,
            identities = online.identities,
            playableHeroes = inputs.playable,
            catalogVersion = online::catalogVersion,
            scope = scope,
            client = online.client,
        )
    }
    LaunchedEffect(presenter) { presenter.start() }
    val ui by presenter.state.collectAsState()
    var inviting by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(ui.gameId) { ui.gameId?.let { router.navigate(Route.OnlineGame(it), replace = true) } }
    LaunchedEffect(ui.left) { if (ui.left) router.navigate(Route.OnlineHome, replace = true) }
    Div({ classes("goa-w2-form") }) {
        ui.message?.let { message ->
            P({
                classes("t-body-medium", "goa-online-message")
                attr("role", "alert")
            }) { Text(message) }
        }
        val room = ui.room
        if (room == null) {
            if (ui.message == null) {
                LoadingState("Opening the room")
            } else {
                GoaButton("Try again", presenter::start, ButtonKind.TONAL, icon = "refresh")
            }
        } else {
            GoaCard {
                H2({ classes("t-title-large", "goa-w2-tight0", "t-numeric") }) { Text(room.code) }
                P({ classes("t-body-medium", "c-variant") }) {
                    Text(
                        listOf(
                            inputs.mapName(room.mapId),
                            PlayText.length(room.length),
                            "${room.teamSize}v${room.teamSize}",
                            if (room.ranked) "Ranked" else "Friendly",
                        ).joinToString(" · "),
                    )
                }
                GoaButton("Invite", { inviting = true }, ButtonKind.OUTLINED, icon = "g_share", block = true)
            }
            Team.entries.forEach { team -> TeamSeats(team, ui, inputs, presenter) }
            HeroPicker(ui, inputs, presenter)
            Div({ classes("goa-w2-stack") }) {
                val ready = ui.yourSeat?.ready == true
                GoaButton(
                    if (ready) "Not ready after all" else "I'm ready",
                    { presenter.setReady(!ready) },
                    if (ready) ButtonKind.OUTLINED else ButtonKind.FILLED,
                    enabled = ui.canReady,
                    block = true,
                )
                if (ui.isHost) {
                    GoaButton("Start the game", presenter::startGame, enabled = ui.canStart, block = true)
                    P({ classes("t-body-small", "c-variant") }) {
                        Text("${ui.readyCount} of ${ui.seats.size} ready. You start it once every seat is ready.")
                    }
                } else {
                    P({ classes("t-body-small", "c-variant") }) {
                        Text("${ui.readyCount} of ${ui.seats.size} ready. Whoever made the room starts it.")
                    }
                }
                if (!ui.connected) {
                    P({
                        classes("t-body-small", "c-variant")
                        attr("role", "status")
                    }) { Text("Connecting to the room…") }
                }
                GoaButton("Leave this room", { leaving = true }, ButtonKind.TEXT)
            }
        }
    }
    if (inviting) InviteSheet(ui) { inviting = false }
    if (leaving) {
        AlertDialog("Leave this room?", onDismiss = { leaving = false }, buttons = {
            GoaButton("Stay", { leaving = false }, ButtonKind.TEXT)
            GoaButton("Leave", {
                leaving = false
                presenter.leave()
            }, ButtonKind.DANGER)
        }) {
            P { Text(if (ui.isHost) "The next person becomes the host. The last one out closes the room." else "Your seat opens up again.") }
        }
    }
}

@Composable
private fun TeamSeats(team: Team, ui: LobbyUi, inputs: LobbyInputs, presenter: OnlineLobbyPresenter) {
    SectionHeader(BoardText.team(team))
    GoaCard(flush = true) {
        Div({ classes("goa-online-seats") }) {
            ui.seats.filter { it.team == team }.sortedBy { it.index }.forEach { seat ->
                SeatRow(seat, ui, inputs, onSit = { presenter.pickSeat(team, seat.index) })
            }
        }
    }
}

@Composable
private fun SeatRow(seat: RoomSeat, ui: LobbyUi, inputs: LobbyInputs, onSit: () -> Unit) {
    val mine = seat.accountId != null && seat.accountId == ui.you
    val hero = seat.heroId?.let(inputs.heroes::hero)
    val free = seat.accountId == null
    val body: @Composable () -> Unit = {
        if (hero != null) {
            HeroPortrait(hero, seat.team, SEAT_PORTRAIT)
        } else {
            Span({ classes("goa-online-seat-mark") }) { TeamMark(seat.team) }
        }
        Span({ classes("goa-online-player-text") }) {
            Span({ classes("t-title-small", "goa-online-name") }) {
                Text(if (free) "Empty seat" else seat.name.orEmpty())
            }
            Span({ classes("t-body-small", "goa-quiet") }) {
                Text(
                    when {
                        free -> "Tap to sit here"
                        hero != null -> hero.name
                        else -> "Choosing a hero"
                    },
                )
            }
        }
        if (mine) Span({ classes("goa-online-you", "t-label-medium") }) { Text("You") }
        if (seat.ready) {
            Span({ classes("goa-online-ready", "t-label-medium") }) {
                Icon("check", IconSize.SMALL)
                Text("Ready")
            }
        }
    }
    if (free) {
        Button({
            classes("goa-online-seat", "free")
            attr("type", "button")
            attr("aria-label", "Sit in ${BoardText.team(seat.team)} seat ${seat.index + 1}")
            onClick { onSit() }
        }) { body() }
    } else {
        Div({
            classes("goa-online-seat")
            if (mine) classes("mine")
        }) { body() }
    }
}

private const val SEAT_PORTRAIT = 40
private const val PICK_PORTRAIT = 48

/** The heroes still free; your own pick stays lit until you change it. */
@Composable
private fun HeroPicker(ui: LobbyUi, inputs: LobbyInputs, presenter: OnlineLobbyPresenter) {
    val seat = ui.yourSeat ?: return
    val own = seat.heroId
    SectionHeader("Your hero")
    Div({ classes("goa-online-heroes") }) {
        (listOfNotNull(own) + ui.heroes).forEach { id ->
            val hero = inputs.heroes.hero(id)
            val selected = id == own
            Button({
                classes("goa-online-hero")
                if (selected) classes("selected")
                attr("type", "button")
                attr("aria-pressed", selected.toString())
                attr("aria-label", hero.name)
                onClick { presenter.pickHero(if (selected) null else id) }
            }) {
                HeroPortrait(hero, seat.team, PICK_PORTRAIT)
                Span({ classes("t-label-medium") }) { Text(hero.name) }
            }
        }
    }
}

@Composable
private fun InviteSheet(ui: LobbyUi, onDismiss: () -> Unit) {
    val services = LocalServices.current
    BottomSheet("Invite to room ${ui.code}", onDismiss = onDismiss) {
        QrCode(ui.inviteLink, "Invite QR code for room ${ui.code}")
        P({ classes("t-body-medium") }) {
            Text("On an iPhone: point the Camera at the code. Or send the link, or read out the room code: ${ui.code}.")
        }
        P({ classes("t-body-small", "c-variant", "goa-w2-break") }) { Text(ui.inviteLink) }
        Div({ classes("goa-w2-stack") }) {
            GoaButton("Share the link", { shareText(services, ui.inviteLink) }, icon = "g_share", block = true)
            GoaButton("Done", onDismiss, ButtonKind.OUTLINED, block = true)
        }
    }
}
