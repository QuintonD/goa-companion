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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.DisabledReason
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
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
                    TeamSeats(state, room, team, heroes, actions, onPick = { picking = true })
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
        GoaOutlinedButton(
            onClick = { actions.onShare(shareText(state)) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text("Share")
        }
        GoaOutlinedButton(onClick = onQr, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text("QR")
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
    onPick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamMark(team, markSize = scaledIconSize(12.dp))
        Spacer(Modifier.width(Spacing.xs))
        OnlineSection(BoardText.team(team))
    }
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        room.seats.filter { it.team == team }.sortedBy { it.index }.forEachIndexed { i, seat ->
            if (i > 0) PlateRowDivider()
            SeatRow(state, room, seat, heroes, actions, onPick)
        }
    }
}

/** Where a taken seat stands before the start, in the order a player gets through it. */
private enum class SeatState(val label: String) {
    CHOOSE_HERO("Choose hero"),
    NOT_READY("Not ready"),
    READY("Ready"),
}

private fun RoomSeat.state(): SeatState = when {
    heroId == null -> SeatState.CHOOSE_HERO
    ready -> SeatState.READY
    else -> SeatState.NOT_READY
}

/**
 * A seat: portrait (or an empty ring), name, hero and host, and on every taken seat its state at the end ("Choose
 * hero", "Not ready", "Ready"), in words and a mark, never colour alone. Your own seat carries "Change hero" under
 * your name once you have one; a free seat is a target ("+ Tap to sit here").
 */
@Composable
private fun SeatRow(
    state: LobbyUi,
    room: RoomView,
    seat: RoomSeat,
    heroes: Map<String, BoardHero>,
    actions: OnlineLobbyActions,
    onPick: () -> Unit,
) {
    val taken = seat.accountId != null
    val mine = seat.accountId != null && seat.accountId == state.you
    val heroName = seat.heroId?.let { heroes.hero(it).name }
    val spoken = seatWords(seat, mine, heroName, room)
    val canSit = !taken && state.connected
    val side = scaledIconSize(40.dp, 1.4f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (mine) Modifier.background(GoaTheme.colors.bronzeGlow) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (canSit) Modifier.clickable { actions.onSeat(seat.team, seat.index) } else Modifier)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    if (canSit) role = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val portrait = Modifier.size(side).border(2.dp, teamColor(seat.team), CircleShape)
            val heroId = seat.heroId
            when {
                heroId != null -> HeroPortrait(
                    heroId,
                    heroes.hero(heroId).portrait,
                    null,
                    portrait,
                    CircleShape,
                    framed = false,
                )

                taken -> SeatSilhouette(seat.team, side, taken = true)

                else -> SeatSilhouette(seat.team, side, taken = false)
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
                    color = if (taken) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                val sub = listOfNotNull(
                    heroName,
                    "+ Tap to sit here".takeIf { canSit },
                    "Host".takeIf { seat.accountId != null && seat.accountId == room.hostAccountId },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = if (canSit) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                        // A seat you can take is something you can do: teal, and in words.
                        color = if (canSit) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (taken) {
                Spacer(Modifier.width(Spacing.sm))
                SeatStateMark(seat.state())
            }
        }
        if (mine && seat.heroId != null) {
            TextButton(
                onClick = onPick,
                enabled = state.connected,
                modifier = Modifier
                    .padding(start = Spacing.md + side + Spacing.md - Spacing.sm, bottom = Spacing.xs)
                    .heightIn(min = 48.dp),
            ) { Text("Change hero") }
        }
    }
}

/** A taken seat's state: a tick for Ready (teal), an open ring for Not ready, a portrait outline to choose a hero. */
@Composable
private fun SeatStateMark(state: SeatState) {
    val ready = state == SeatState.READY
    val tint = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (state) {
                SeatState.READY -> Icons.Outlined.CheckCircle
                SeatState.NOT_READY -> Icons.Outlined.RadioButtonUnchecked
                SeatState.CHOOSE_HERO -> Icons.Outlined.PersonSearch
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(scaledIconSize(20.dp)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            state.label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            fontWeight = if (ready) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * A seat without a hero: free, a dashed ring in the team's colour around a "+" (tap the row to sit); taken but no hero
 * picked yet, a solid ring around a person's silhouette. Decorative: the row's words say which.
 */
@Composable
private fun SeatSilhouette(team: Team, side: Dp, taken: Boolean) {
    val ring = teamColor(team)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(side)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val radius = size.minDimension / 2f - stroke / 2f
                drawCircle(ring.copy(alpha = if (taken) 0.16f else 0.08f), radius)
                drawCircle(
                    ring,
                    radius,
                    style = Stroke(
                        width = stroke,
                        pathEffect = if (taken) {
                            null
                        } else {
                            PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                            )
                        },
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (taken) Icons.Outlined.Person else Icons.Filled.Add,
            contentDescription = null,
            tint = if (taken) muted else ring,
            modifier = Modifier.size(side * 0.55f),
        )
    }
}

/** One line of what the start waits for: done (a teal tick) or still to come (an open ring), and its words. */
internal data class StartCheck(val done: Boolean, val words: String)

/**
 * What the game's start waits for, as a checklist: seats taken, heroes chosen, everyone ready, and (for everyone but
 * the host) who starts it. Counts come from the room the server sent.
 */
internal fun startChecks(state: LobbyUi): List<StartCheck> {
    val seats = state.room?.seats.orEmpty()
    val taken = seats.filter { it.accountId != null }
    val withHero = taken.count { it.heroId != null }
    val host = seats.firstOrNull { it.accountId != null && it.accountId == state.room?.hostAccountId }?.name
    return listOfNotNull(
        StartCheck(
            taken.size == seats.size,
            if (taken.size == seats.size) "Every seat taken" else "${taken.size} of ${seats.size} seats taken",
        ),
        StartCheck(
            taken.isNotEmpty() && withHero == taken.size,
            if (taken.isNotEmpty() && withHero == taken.size) {
                "Every hero chosen"
            } else {
                "$withHero of ${taken.size} heroes chosen"
            },
        ),
        StartCheck(
            state.everyoneReady,
            if (state.everyoneReady) "Everyone ready" else "${state.readyCount} of ${seats.size} ready",
        ),
        StartCheck(false, "${host ?: "The host"} starts the game").takeUnless { state.isHost },
    )
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

/** The checklist, one TalkBack node, announced politely as the room changes. */
@Composable
private fun StartChecklist(state: LobbyUi) {
    val checks = startChecks(state)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        checks.forEach { check ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (check.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (check.done) "Done:" else "Waiting:",
                    tint = if (check.done) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(scaledIconSize(18.dp)),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    check.words,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (check.done) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * The start checklist, then only this player's next step in bronze: choose a hero, then "I'm ready", then (the host)
 * "Start the game", off with its reason until everyone is ready. Taking it back is a quiet outlined button.
 */
@Composable
private fun Actions(state: LobbyUi, actions: OnlineLobbyActions, onPick: () -> Unit) {
    val seat = state.yourSeat
    val ready = seat?.ready == true
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StartChecklist(state)
        Spacer(Modifier.height(Spacing.xs))
        when {
            seat == null -> Unit

            seat.heroId == null -> GoaButton(
                onClick = onPick,
                enabled = state.connected,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Choose your hero") }

            !ready -> GoaButton(
                onClick = { actions.onReady(true) },
                enabled = state.canReady,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("I'm ready") }

            else -> Unit
        }
        // The host always sees the start (unlit, with its reason, until everyone is ready) and who flips the coin.
        if (state.isHost) {
            GoaButton(
                onClick = actions.onStart,
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Start the game") }
            if (!state.canStart) DisabledReason("Starts when every seat is taken and everyone is ready.")
            Text(
                "You start the game; the server flips the Tie Breaker coin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ready) {
            GoaOutlinedButton(
                onClick = { actions.onReady(false) },
                enabled = state.connected,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Not ready after all") }
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
