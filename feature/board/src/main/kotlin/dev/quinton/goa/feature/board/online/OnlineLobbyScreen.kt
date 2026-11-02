package dev.quinton.goa.feature.board.online

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.RoomSeat
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.online.LobbyUi

/** What the lobby can do. */
class OnlineLobbyActions(
    val onSeat: (Team, Int) -> Unit = { _, _ -> },
    val onHero: (String?) -> Unit = {},
    val onReady: (Boolean) -> Unit = {},
    val onStart: () -> Unit = {},
    val onShare: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * A room before its game (ADR-056): the invite as text and as a QR, the seats by team, a hero for yours, ready, and
 * start for the host. What is shown is what the server says: a pick appears when the room comes back with it.
 */
@Composable
fun OnlineLobbyScreen(
    state: LobbyUi,
    heroes: Map<String, BoardHero>,
    mapNames: Map<String, String>,
    actions: OnlineLobbyActions,
    modifier: Modifier = Modifier,
    initialPicking: Boolean = false,
    initialQr: Boolean = false,
) {
    var picking by remember { mutableStateOf(initialPicking) }
    var showQr by remember { mutableStateOf(initialQr) }
    GoaScreen(title = "Room ${spacedCode(state.code)}", onBack = actions.onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.message?.let { words ->
                CardTextBox(PlateColor.GOLD, Modifier.fillMaxWidth(), cap = "Note") {
                    Text(
                        words,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            val room = state.room
            if (room == null) {
                NoRoomYet(state, actions)
            } else {
                Facts(room, mapNames)
                Invite(state, actions, onQr = { showQr = true })
                Team.entries.forEach { team ->
                    TeamSeats(state, room, team, heroes, actions)
                }
                Actions(state, actions, onPick = { picking = true })
            }
        }
    }
    if (picking && state.yourSeat != null) {
        HeroPicker(state, heroes, onPick = actions.onHero, onDismiss = { picking = false })
    }
    if (showQr) {
        QrDialog(state, onDismiss = { showQr = false })
    }
}

/** A code reads back in two groups of four. */
internal fun spacedCode(code: String): String = code.chunked(CODE_GROUP).joinToString(" ")

@Composable
private fun NoRoomYet(state: LobbyUi, actions: OnlineLobbyActions) {
    if (state.message == null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = actions.onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            GoaButton(onClick = actions.onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
        }
    }
}

@Composable
private fun Facts(room: RoomView, mapNames: Map<String, String>) {
    Text(
        listOf(
            mapNames[room.mapId] ?: room.mapId,
            PlayText.length(room.length),
            "${room.teamSize}v${room.teamSize}",
            if (room.ranked) "Ranked" else "Not ranked",
        ).joinToString(" · "),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun Invite(state: LobbyUi, actions: OnlineLobbyActions, onQr: () -> Unit) {
    OnlineSection("Invite")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(
            onClick = { actions.onShare(shareText(state)) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text("Share")
        }
        OutlinedButton(onClick = onQr, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text("QR code")
        }
    }
}

private fun shareText(state: LobbyUi): String =
    "Join my Guards of Atlantis game: ${state.inviteLink}\nOr open Play → Online and enter the room code ${state.code}."

/** One plate per team, dividers inside (DESIGN.md). */
@Composable
private fun TeamSeats(
    state: LobbyUi,
    room: RoomView,
    team: Team,
    heroes: Map<String, BoardHero>,
    actions: OnlineLobbyActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamMark(team, markSize = scaledIconSize(12.dp))
        Spacer(Modifier.width(Spacing.xs))
        OnlineSection(BoardText.team(team))
    }
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        room.seats.filter { it.team == team }.sortedBy { it.index }.forEachIndexed { i, seat ->
            if (i > 0) HorizontalDivider()
            SeatRow(state, room, seat, heroes, actions)
        }
    }
}

@Composable
private fun SeatRow(
    state: LobbyUi,
    room: RoomView,
    seat: RoomSeat,
    heroes: Map<String, BoardHero>,
    actions: OnlineLobbyActions,
) {
    val taken = seat.accountId != null
    val mine = seat.accountId != null && seat.accountId == state.you
    val heroName = seat.heroId?.let { heroes.hero(it).name }
    val spoken = seatWords(seat, mine, heroName, room)
    val canSit = !taken && state.connected
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (mine) Modifier.background(GoaTheme.colors.bronzeGlow) else Modifier)
            .then(if (canSit) Modifier.clickable { actions.onSeat(seat.team, seat.index) } else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (canSit) role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val portrait = Modifier.size(scaledIconSize(36.dp, 1.4f)).border(2.dp, teamColor(seat.team), CircleShape)
        if (seat.heroId != null) {
            HeroPortrait(seat.heroId!!, heroes.hero(seat.heroId!!).portrait, null, portrait, CircleShape, framed = false)
        } else {
            Box(portrait)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    mine -> "${seat.name.orEmpty()} (you)"
                    taken -> seat.name.orEmpty()
                    else -> "Free seat"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (taken) FontWeight.SemiBold else FontWeight.Normal,
                color = if (taken) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOfNotNull(
                    heroName ?: "No hero yet".takeIf { taken },
                    "Tap to sit here".takeIf { canSit },
                    "Host".takeIf { seat.accountId != null && seat.accountId == room.hostAccountId },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (seat.ready) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(scaledIconSize(20.dp)),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text("Ready", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

private fun seatWords(seat: RoomSeat, mine: Boolean, heroName: String?, room: RoomView): String = buildString {
    append(BoardText.team(seat.team))
    append(": ")
    if (seat.accountId == null) {
        append("free seat, tap to sit here")
    } else {
        append(seat.name.orEmpty())
        if (mine) append(" (you)")
        if (seat.accountId == room.hostAccountId) append(", host")
        append(", ")
        append(heroName ?: "no hero yet")
        append(if (seat.ready) ", ready" else ", not ready")
    }
}

@Composable
private fun Actions(state: LobbyUi, actions: OnlineLobbyActions, onPick: () -> Unit) {
    val seat = state.yourSeat
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(
            onClick = onPick,
            enabled = seat != null && state.connected,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (seat?.heroId == null) "Pick your hero" else "Change hero") }
        val ready = seat?.ready == true
        GoaButton(
            onClick = { actions.onReady(!ready) },
            enabled = state.canReady,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (ready) "Not ready after all" else "I'm ready") }
        if (state.isHost) {
            GoaButton(
                onClick = actions.onStart,
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Start the game") }
            Text(
                "${state.readyCount} of ${state.seats.size} ready. The server flips the Tie Breaker coin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "${state.readyCount} of ${state.seats.size} ready. The host starts the game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = actions.onLeave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Leave the room", color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroPicker(
    state: LobbyUi,
    heroes: Map<String, BoardHero>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    BoardSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
            SheetTitle("Pick your hero", "Heroes someone else has picked aren't here.", onDone = onDismiss)
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val current = state.yourSeat?.heroId
                (listOfNotNull(current) + state.heroes).forEach { id ->
                    HeroTile(heroes.hero(id), selected = id == current) {
                        onPick(id)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTile(hero: BoardHero, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(scaledIconSize(TILE_WIDTH, TILE_SCALE))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(Spacing.xs)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroPortrait(
            hero.id,
            hero.portrait,
            null,
            Modifier.fillMaxWidth().aspectRatio(1f),
            shape = CircleShape,
            framed = false,
        )
        Text(
            hero.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QrDialog(state: LobbyUi, onDismiss: () -> Unit) {
    val qr = remember(state.inviteLink) { roomQr(state.inviteLink) }
    GoaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room ${spacedCode(state.code)}") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(Spacing.sm)
                        .clearAndSetSemantics {
                            contentDescription = "QR code to join room ${state.code}"
                        },
                ) {
                    Image(
                        bitmap = qr,
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
                Text(
                    state.code,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "In the app: Play → Online → Scan QR. On an iPhone, point the Camera at it: it opens the web app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
        },
    )
}

private const val SHEET_HEIGHT = 0.9f
private const val CODE_GROUP = 4
private const val TILE_SCALE = 1.4f
private val TILE_WIDTH = 84.dp
