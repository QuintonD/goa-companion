package dev.quinton.goa.feature.draft.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.ui.ConcealedTile
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.HeroTile
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.actionName
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.rememberReducedMotion
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.feature.draft.ui.teamName
import kotlin.math.abs

interface BoardActions {
    fun tapHero(heroId: String) {}

    fun confirm() {}

    fun cancelConfirm() {}

    fun swapOut(heroId: String) {}

    fun reroll() {}

    fun undo() {}

    fun acknowledgePrivacy() {}

    fun selectSeat(seatId: String) {}

    fun toggleHandicap(seatId: String) {}

    fun finish(openMatchSetup: Boolean) {}

    fun discard() {}

    fun back() {}

    fun dismissMessage() {}

    fun chooseHeroFor(playerId: String?) {}

    fun assignHero(heroId: String) {}

    fun clearHero(playerId: String) {}

    fun draftPlayer(playerId: String) {}

    fun undoPlayerPick() {}

    companion object {
        val None = object : BoardActions {}
    }
}

@Composable
fun DraftBoardDestination(
    viewModel: DraftBoardViewModel,
    onBack: () -> Unit,
    onFinished: (matchId: String, openMatchSetup: Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.boardEvents.collect { event ->
            when (event) {
                is BoardEvent.Finished -> onFinished(event.matchId, event.openMatchSetup)
                BoardEvent.Closed -> onBack()
            }
        }
    }
    val actions = remember(viewModel) {
        object : BoardActions {
            override fun tapHero(heroId: String) = viewModel.tapHero(heroId)
            override fun confirm() = viewModel.confirm()
            override fun cancelConfirm() = viewModel.cancelConfirm()
            override fun swapOut(heroId: String) = viewModel.swapOut(heroId)
            override fun reroll() = viewModel.reroll()
            override fun undo() = viewModel.undo()
            override fun acknowledgePrivacy() = viewModel.acknowledgePrivacy()
            override fun selectSeat(seatId: String) = viewModel.selectSeat(seatId)
            override fun toggleHandicap(seatId: String) = viewModel.toggleHandicap(seatId)
            override fun finish(openMatchSetup: Boolean) = viewModel.finish(openMatchSetup)
            override fun discard() = viewModel.discard()
            override fun back() = onBack()
            override fun dismissMessage() = viewModel.dismissMessage()
            override fun chooseHeroFor(playerId: String?) = viewModel.chooseHeroFor(playerId)
            override fun assignHero(heroId: String) = viewModel.assignHero(heroId)
            override fun clearHero(playerId: String) = viewModel.clearHero(playerId)
            override fun draftPlayer(playerId: String) = viewModel.draftPlayer(playerId)
            override fun undoPlayerPick() = viewModel.undoPlayerPick()
        }
    }
    DraftBoardScreen(state, actions)
}

@Composable
fun DraftBoardScreen(state: BoardUiState, actions: BoardActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.local.message) {
        state.local.message?.let {
            snackbar.showSnackbar(it)
            actions.dismissMessage()
        }
    }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val board = state.board
    val players = state.playerDraft
    val title = board?.method?.name ?: players?.let { "Player Draft" } ?: "Draft"
    val canUndo = when {
        board != null -> board.canUndo
        players != null -> players.captains.picks.isNotEmpty()
        else -> false
    }
    GoaScreen(
        title = title,
        onBack = actions::back,
        actions = {
            if (state.progress != null) {
                IconButton(
                    onClick = if (board != null) actions::undo else actions::undoPlayerPick,
                    enabled = canUndo && !state.privacyGate,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last step")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Discard draft") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                confirmDiscard = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Unit

                board != null && state.privacyGate -> PrivacyGate(board, actions)

                board != null && board.isComplete -> CompletionContent(state, board, actions)

                board != null -> HeroBoard(state, board, actions)

                players != null -> PlayerDraftContent(state, players, actions)

                else -> EmptyState(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "No draft in progress",
                    message = "Set up players, teams and a method to start one.",
                    modifier = Modifier.align(Alignment.Center),
                    action = { Button(onClick = actions::back) { Text("Set up a draft") } },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (board != null && !board.isComplete && state.local.confirm != null) {
        ConfirmDialog(state, board, state.local.confirm, actions)
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this draft?") },
            text = { Text("Bans and picks so far are thrown away. Nothing is saved to the match log.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.discard()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep drafting") } },
        )
    }
}

/** True while the pool may still be re-rolled or swapped (before the first real choice). */
internal fun DraftSession.poolEditable(): Boolean = !isStarted &&
    method.sequence !is SequenceRule.Lineup &&
    method.pool != PoolRule.All

@Composable
private fun HeroBoard(state: BoardUiState, session: DraftSession, actions: BoardActions) {
    val available = remember(session) { DraftEngine.available(session).toSet() }
    val concealed =
        remember(session) {
            session.history.indices.filter { session.isConcealed(it) }.map { session.history[it].heroId }.toSet()
        }
    val tokens = remember(session) {
        session.plan.map { StepToken("${actionName(it.action)} ${teamGlyph(it.team)}", it.action, it.team, it.hidden) }
    }
    // Tiles grow with the font scale so names and state chips stay legible.
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp * fontScale),
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) { StepBanner(session, state.local.remainingSeconds, state::playerName) }
        item(span = {
            GridItemSpan(maxLineSpan)
        }) { SequenceStrip(tokens, current = session.currentStepIndex, wrap = false) }
        if (session.poolEditable()) {
            item(span = { GridItemSpan(maxLineSpan) }) { PoolEditBanner(session, actions) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { TeamsPanel(state, session, concealed) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val left = session.pool.count { it !in session.bannedIds && it !in session.pickedIds }
            Text(
                "Pool · $left of ${session.pool.size} left",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
            )
        }
        items(session.pool, key = { it }) { heroId ->
            val tileState = tileState(session, heroId, available, concealed)
            HeroTile(
                hero = state.heroes.info(heroId),
                state = tileState,
                onClick = if (tileState == TileState.Available) ({ actions.tapHero(heroId) }) else null,
            )
        }
    }
}

private fun tileState(
    session: DraftSession,
    heroId: String,
    available: Set<String>,
    concealed: Set<String>,
): TileState {
    if (heroId in concealed) return TileState.Available
    session.history.lastOrNull { it.heroId == heroId }?.let { event ->
        return if (event.action == DraftAction.BAN) TileState.Banned(event.team) else TileState.Picked(event.team)
    }
    return if (heroId in available) TileState.Available else TileState.Inactive
}

@Composable
private fun StepBanner(session: DraftSession, remaining: Int?, playerName: (String?) -> String) {
    val step = session.currentStep ?: return
    val run = session.currentRun()
    val color = teamColor(step.team)
    val verb = actionName(step.action) + if (run != null && run.length > 1) " ${run.length}" else ""
    val reduced = rememberReducedMotion()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(2.dp, color),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            AnimatedContent(
                targetState = "${teamLabel(step.team)} — $verb",
                transitionSpec = {
                    val d = if (reduced) 0 else 250
                    (fadeIn(tween(d)) + slideInVertically(tween(d)) { it / 3 }) togetherWith fadeOut(tween(d))
                },
                label = "banner",
            ) { text ->
                Text(text, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val teamRole = if (step.team == session.teamA) "Team A" else "Team B"
                val runText = if (run != null && run.length > 1) " · ${run.position} of ${run.length}" else ""
                val seat = step.seatId?.let { id -> session.seat(id) }
                val pilot = seat?.pilotId?.let { " · for ${playerName(it)}" } ?: ""
                Text(
                    "$teamRole · Step ${(session.currentStepIndex ?: 0) + 1} of ${session.plan.size}$runText$pilot" +
                        if (step.hidden) " · hidden" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (remaining != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    StepClock(remaining)
                }
            }
        }
    }
}

@Composable
private fun StepClock(remaining: Int) {
    val over = remaining < 0
    val text = (if (over) "+" else "") + "%d:%02d".format(abs(remaining) / 60, abs(remaining) % 60)
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics {
                contentDescription =
                    if (over) "Overtime ${abs(remaining)} seconds" else "$remaining seconds left"
            },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun PoolEditBanner(session: DraftSession, actions: BoardActions) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pool of ${session.pool.size} dealt", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Before the first step you can re-roll it, or tap a hero to swap it out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = actions::reroll, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Re-roll")
            }
        }
    }
}

@Composable
private fun TeamsPanel(state: BoardUiState, session: DraftSession, concealed: Set<String>) {
    // Side by side normally; stacked at large font scales.
    if (LocalDensity.current.fontScale > LARGE_FONT) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (team in listOf(session.teamA, session.teamB)) {
                TeamColumn(state, session, team, concealed, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (team in listOf(session.teamA, session.teamB)) {
                TeamColumn(state, session, team, concealed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TeamColumn(
    state: BoardUiState,
    session: DraftSession,
    team: Team,
    concealed: Set<String>,
    modifier: Modifier,
) {
    val color = teamColor(team)
    val active = session.currentStep?.team == team
    val picks = DraftEngine.assignments(session).filter { it.seat.team == team }
    val bans = session.bans.filter { it.team == team }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) color else GoaTheme.colors.engravedBorder),
    ) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    teamLabel(team),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (team == session.teamA) "A" else "B",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription =
                            if (team == session.teamA) "Team A" else "Team B"
                    },
                )
            }
            picks.forEach { (seat, heroId) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                    val hidden = heroId != null && heroId in concealed
                    when {
                        hidden -> ConcealedTile(team, Modifier.size(36.dp))

                        heroId != null -> HeroPortrait(
                            heroId,
                            state.heroes.info(heroId).portrait,
                            null,
                            Modifier.size(36.dp),
                        )

                        else -> Box(
                            Modifier
                                .size(36.dp)
                                .border(1.dp, color.copy(alpha = 0.4f), MaterialTheme.shapes.medium),
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                hidden -> "Hidden pick"
                                heroId != null -> state.heroes.info(heroId).name
                                else -> "Open seat"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = if (heroId ==
                                null
                            ) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            state.playerName(seat.pilotId) + if (seat.handicap) " · H" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (bans.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    bans.forEach { ban ->
                        val hidden = ban.heroId in concealed
                        Text(
                            if (hidden) "Ban: hidden" else "Ban: ${state.heroes.info(ban.heroId).name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(state: BoardUiState, session: DraftSession, heroId: String, actions: BoardActions) {
    val step = session.currentStep ?: return
    val hero = state.heroes.info(heroId)
    val color = teamColor(step.team)
    val ban = step.action == DraftAction.BAN
    AlertDialog(
        onDismissRequest = actions::cancelConfirm,
        icon = {
            HeroPortrait(hero.id, hero.portrait, null, Modifier.size(width = 96.dp, height = 120.dp))
        },
        title = {
            Text(
                if (ban) "Ban ${hero.name}?" else "Pick ${hero.name}?",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (hero.title.isNotEmpty()) Text(hero.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (ban) {
                        "${teamLabel(
                            step.team,
                        )} removes this hero from the draft."
                    } else {
                        "${teamLabel(step.team)} takes this hero."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (session.poolEditable()) {
                    TextButton(onClick = { actions.swapOut(heroId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Swap out of the pool instead")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = actions::confirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("${teamGlyph(step.team)} ${if (ban) "Ban" else "Pick"}") }
        },
        dismissButton = { TextButton(onClick = actions::cancelConfirm) { Text("Cancel") } },
    )
}

@Composable
private fun PrivacyGate(session: DraftSession, actions: BoardActions) {
    val step = session.currentStep ?: return
    val color = teamColor(step.team)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(color.copy(alpha = 0.16f))
                .border(2.dp, color, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            "Pass the phone to ${teamLabel(step.team)}",
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Hidden ${actionName(
                step.action,
            ).lowercase()}: only ${teamName(step.team)} should look. Earlier hidden choices stay covered.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = actions::acknowledgePrivacy,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text("I'm ${teamName(step.team)} — show the board") }
    }
}

internal fun heroLabel(heroes: Map<String, HeroInfo>, id: String?) = id?.let { heroes.info(it).name } ?: "No hero"

private const val LARGE_FONT = 1.5f
