package dev.quinton.goa.feature.draft.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.selectionRow
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.component.teamTextColor
import dev.quinton.goa.core.designsystem.icon.Destination
import dev.quinton.goa.core.designsystem.icon.icon
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.SeatAssignment
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.ui.PlateBar
import dev.quinton.goa.feature.draft.ui.TeamText
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.plateEdge
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.feature.draft.ui.teamName

/**
 * Finished draft: arrange heroes on seats, then save and hand off to match setup. One bronze Open match setup is pinned
 * under the teams with the other ways out (Log result later, Try on the board, Handicap cards, Deal again) in its
 * menu; on a short screen (landscape) the bar scrolls with the rest. Swapping who plays which hero is a mode of its
 * own: a row's Swap starts it, the bar then says what the next tap does, with Cancel.
 */
@Composable
internal fun CompletionContent(state: BoardUiState, session: DraftSession, actions: BoardActions) {
    var handicaps by rememberSaveable { mutableStateOf(false) }
    val swapping = state.local.selectedSeat?.let { id ->
        DraftEngine.assignments(session).firstOrNull { it.seat.id == id }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bar: @Composable (Modifier) -> Unit = { modifier ->
            if (swapping != null) {
                SwapBar(state, swapping, onCancel = { actions.selectSeat(swapping.seat.id) }, modifier = modifier)
            } else {
                CompletionActions(state, session, actions, onHandicaps = { handicaps = true }, modifier = modifier)
            }
        }
        if (maxHeight >= PINNED_MIN_HEIGHT) {
            Column(Modifier.fillMaxSize()) {
                CompletionList(state, session, actions, swapping, modifier = Modifier.weight(1f))
                PlateBar { bar(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) }
            }
        } else {
            CompletionList(state, session, actions, swapping, modifier = Modifier.fillMaxSize(), footer = bar)
        }
    }
    if (handicaps) HandicapDialog(state, session, actions, onDismiss = { handicaps = false })
}

@Composable
private fun CompletionList(
    state: BoardUiState,
    session: DraftSession,
    actions: BoardActions,
    swapping: SeatAssignment?,
    modifier: Modifier = Modifier,
    footer: (@Composable (Modifier) -> Unit)? = null,
) {
    val assignments = DraftEngine.assignments(session)
    LazyColumn(
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier,
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TitlePlate("Draft complete", Modifier.fillMaxWidth())
                // Only when seats can't be swapped: why not. Swappable rows say so themselves.
                if (!session.canSwapSeats) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        DraftBoardText.completionHint(session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        for (team in listOf(session.teamA, session.teamB)) {
            item(key = team.name) {
                TeamResult(state, session, team, assignments.filter { it.seat.team == team }, actions, swapping)
            }
        }
        if (session.bans.isNotEmpty()) {
            item {
                Text(
                    "Banned: " + session.bans.joinToString(", ") { state.heroes.info(it.heroId).name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (footer != null) item { footer(Modifier) }
    }
}

/** Swap mode's bar: what the next tap does, and Cancel. */
@Composable
private fun SwapBar(
    state: BoardUiState,
    swapping: SeatAssignment,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = swapping.heroId?.let { state.heroes.info(it).name } ?: "this open seat"
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.sm))
        TeamText(
            "Swap $name with another ${teamLabel(swapping.seat.team)} hero",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
    }
}

/**
 * One bronze Open match setup (saves a pending match and opens Match setup); the rest in its menu: Log result later
 * (saves the pending match only), Try on the board, the Handicap cards and, while allowed, Deal again.
 */
@Composable
private fun CompletionActions(
    state: BoardUiState,
    session: DraftSession,
    actions: BoardActions,
    onHandicaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GoaButton(
            onClick = { actions.finish(openMatchSetup = true) },
            enabled = !state.local.saving,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            // The flag is Match setup wherever a screen links to it (Destination).
            Icon(Destination.MATCH_SETUP.icon, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Open match setup")
        }
        Spacer(Modifier.width(Spacing.sm))
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More ways to finish")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Log result later") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.EventNote, contentDescription = null) },
                    enabled = !state.local.saving,
                    onClick = {
                        menu = false
                        actions.finish(openMatchSetup = false)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Try on the board") },
                    leadingIcon = { Icon(Destination.BOARD.icon, contentDescription = null) },
                    onClick = {
                        menu = false
                        // Pick order per team: the board's opening places heroes in that order (p.20).
                        actions.tryOnBoard(
                            red = DraftBoardText.lineUp(session, Team.RED),
                            blue = DraftBoardText.lineUp(session, Team.BLUE),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text("Handicap cards…") },
                    leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    onClick = {
                        menu = false
                        onHandicaps()
                    },
                )
                if (session.poolEditable()) {
                    DropdownMenuItem(
                        text = { Text("Deal again") },
                        leadingIcon = { Icon(Icons.Outlined.Casino, contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.reroll()
                        },
                    )
                }
            }
        }
    }
}

/** Which seats play with their Handicap card: allowed at any count to calibrate skill [RB]. */
@Composable
private fun HandicapDialog(state: BoardUiState, session: DraftSession, actions: BoardActions, onDismiss: () -> Unit) {
    GoaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handicap cards") },
        text = {
            Column {
                DraftEngine.assignments(session).forEach { assignment ->
                    val seat = assignment.seat
                    val hero = assignment.heroId?.let { state.heroes.info(it).name } ?: "Open seat"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Checkbox) { actions.toggleHandicap(seat.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = seat.handicap, onCheckedChange = null)
                        Spacer(Modifier.width(Spacing.sm))
                        TeamMark(seat.team, markSize = 12.dp)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("$hero · ${state.playerName(seat.pilotId)}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Below this height (landscape) the actions scroll with the teams instead of being pinned. */
private val PINNED_MIN_HEIGHT = 560.dp

@Composable
private fun TeamResult(
    state: BoardUiState,
    session: DraftSession,
    team: Team,
    seats: List<SeatAssignment>,
    actions: BoardActions,
    swapping: SeatAssignment?,
) {
    GoaPlate(Modifier.fillMaxWidth().plateEdge(teamColor(team), 1.dp), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(vertical = Spacing.sm)) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamMark(team, markSize = 18.dp)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    teamName(team),
                    style = GoaTextStyles.sectionHead.copy(fontSize = 20.sp, lineHeight = 26.sp),
                    color = teamTextColor(team),
                    modifier = Modifier.weight(1f),
                )
                // The team the coin shows holds the Tie Breaker: said with the coin itself.
                if (team == session.teamA) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${teamName(team)} has the Tie Breaker coin"
                        },
                    ) {
                        OfficialIcon(OfficialArt.tieBreaker(team), Modifier.size(20.dp)) {
                            TeamMark(team, Modifier.matchParentSize())
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "Tie Breaker coin",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Odd counts: the larger team plays with Handicap cards [RB], so its rows offer the card at hand.
            val larger = seats.size > session.seats.count { it.team != team }
            val swappable = session.canSwapSeats && seats.size > 1
            seats.forEachIndexed { i, assignment ->
                if (i > 0) PlateRowDivider(Modifier.padding(horizontal = Spacing.md))
                SeatRow(
                    state,
                    assignment,
                    mode = when {
                        !swappable -> SwapMode.None
                        swapping == null -> SwapMode.Offer
                        swapping.seat.id == assignment.seat.id -> SwapMode.Chosen
                        swapping.seat.team == team -> SwapMode.Target
                        else -> SwapMode.Elsewhere
                    },
                    handicapAtHand = larger,
                    actions = actions,
                )
            }
        }
    }
}

/** A seat row's part in swapping who plays which hero. */
private enum class SwapMode { None, Offer, Chosen, Target, Elsewhere }

@Composable
private fun SeatRow(
    state: BoardUiState,
    assignment: SeatAssignment,
    mode: SwapMode,
    handicapAtHand: Boolean,
    actions: BoardActions,
) {
    val seat = assignment.seat
    val hero = assignment.heroId?.let { state.heroes.info(it) }
    val pilot = state.players[seat.pilotId]
    val name = hero?.name ?: "Open seat"
    val tap: (() -> Unit)? = when (mode) {
        SwapMode.Offer, SwapMode.Chosen, SwapMode.Target -> ({ actions.selectSeat(seat.id) })
        SwapMode.None, SwapMode.Elsewhere -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .selectionRow(mode == SwapMode.Chosen)
            .then(
                if (tap != null) {
                    Modifier.clickable(
                        onClickLabel = when (mode) {
                            SwapMode.Chosen -> "Cancel the swap"
                            SwapMode.Target -> "Swap with this hero"
                            else -> "Swap $name with another hero"
                        },
                        onClick = tap,
                    )
                } else {
                    Modifier
                },
            )
            // The other team's rows step back while a swap waits: they can't take part in it.
            .alpha(if (mode == SwapMode.Elsewhere) ELSEWHERE_ALPHA else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, played by ${state.playerName(seat.pilotId)}" +
                    if (seat.handicap) ", Handicap card" else ""
                if (mode == SwapMode.Chosen) {
                    selected = true
                    stateDescription = "Chosen to swap"
                }
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 56.dp, height = 70.dp)) {
            if (hero != null) {
                HeroPortrait(hero.id, hero.portrait, null, Modifier.matchParentSize(), framed = false)
                if (hero.symbol != null) {
                    HeroSymbol(hero.id, hero.symbol, Modifier.padding(1.dp).size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = GoaTextStyles.plateTitle)
            if (hero != null && hero.title.isNotEmpty()) {
                Text(
                    hero.title,
                    style = GoaTextStyles.printedSmall.copy(fontStyle = FontStyle.Italic, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The team's shape next to the crest: player crests are team-neutral (ADR-070).
                TeamMark(seat.team, markSize = 10.dp)
                Spacer(Modifier.width(Spacing.xs))
                PlayerAvatar(pilot, size = 24.dp)
                Spacer(Modifier.width(Spacing.xs))
                Text(state.playerName(seat.pilotId), style = MaterialTheme.typography.labelLarge)
            }
            // A small chip where the card applies (the larger team) or is already in play; elsewhere the bar's menu
            // has it (Handicap cards…), so it doesn't compete with the hero.
            if (handicapAtHand || seat.handicap) {
                HandicapChip(seat.handicap) { actions.toggleHandicap(seat.id) }
            }
        }
        when (mode) {
            SwapMode.Offer -> SwapButton("Swap", onClick = { actions.selectSeat(seat.id) })

            SwapMode.Target -> SwapButton("Swap here", onClick = { actions.selectSeat(seat.id) }, strong = true)

            SwapMode.Chosen -> Icon(
                Icons.Outlined.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )

            SwapMode.None, SwapMode.Elsewhere -> Unit
        }
    }
}

/** The row's explicit swap: "Swap" to start, "Swap here" on the chosen hero's teammates. */
@Composable
private fun SwapButton(label: String, onClick: () -> Unit, strong: Boolean = false) {
    // Its row already carries the action for TalkBack: the button is the visible handle, not a second node.
    val modifier = Modifier.heightIn(min = 48.dp).clearAndSetSemantics {}
    val content: @Composable RowScope.() -> Unit = {
        Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label)
    }
    val padding = PaddingValues(horizontal = Spacing.md)
    // Quiet until a swap waits: six outlined buttons would compete with Open match setup.
    if (strong) {
        GoaButton(onClick = onClick, modifier = modifier, contentPadding = padding, content = content)
    } else {
        TextButton(onClick = onClick, modifier = modifier, contentPadding = padding, content = content)
    }
}

@Composable
private fun HandicapChip(on: Boolean, onToggle: () -> Unit) {
    GoaFilterChip(
        selected = on,
        onClick = onToggle,
        leadingIcon = if (on) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    ) { Text("Handicap card") }
}

private const val ELSEWHERE_ALPHA = 0.45f
