package dev.quinton.goa.feature.board.board

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.map.BoardMapView
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.ui.BoardScaffold
import dev.quinton.goa.feature.board.ui.FitWhileHidden
import dev.quinton.goa.feature.board.ui.HideablePanel
import dev.quinton.goa.feature.board.ui.PanelCover
import dev.quinton.goa.feature.board.ui.PanelPeek
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.boardPalette
import kotlinx.coroutines.launch

/** Sheets and dialogs of the board, hoisted so screenshots can open one. */
enum class BoardOverlay { ADD, PIECES, PUSH, RESET }

/**
 * The board: the map full screen with the Lane top to bottom in portrait (left to right in
 * landscape), opening on the Lane between the hero spawn areas; a bottom panel for the selected
 * piece (a side panel in landscape) that never covers it, and Add / Push the lane / Pieces.
 * [onTap] resolves a tap on the map and reports whether it did anything.
 */
@Composable
fun BoardScreen(
    state: BoardUiState,
    onAction: (BoardAction) -> Unit,
    onTap: (MapTap) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialOverlay: BoardOverlay? = null,
    initialPanelHidden: Boolean = false,
) {
    val ready = state as? BoardUiState.Ready
    var overlay by rememberSaveable { mutableStateOf(initialOverlay) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val viewport = remember { MapViewport() }
    // Back leaves a tool, then the selection, then the board.
    BackHandler(enabled = ready != null && (ready.tool != BoardTool.Move || ready.selectedId != null)) {
        if (ready?.tool != BoardTool.Move) onAction(BoardAction.CancelTool) else onAction(BoardAction.Deselect)
    }
    // Each hint shows once; a newer one replaces it.
    val hint = ready?.hint
    LaunchedEffect(hint?.id) {
        if (hint != null) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(hint.text, withDismissAction = true, duration = SnackbarDuration.Short)
            onAction(BoardAction.HintShown(hint.id))
        }
    }
    BoardScaffold(
        title = ready?.board?.name ?: "Board",
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (ready != null) {
                IconButton(onClick = { onAction(BoardAction.Undo) }, enabled = ready.canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { onAction(BoardAction.Redo) }, enabled = ready.canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Flip the board") },
                            leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                            onClick = {
                                menu = false
                                onAction(BoardAction.Flip)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Reset to opening") },
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                            onClick = {
                                menu = false
                                overlay = BoardOverlay.RESET
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                BoardUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is BoardUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Hexagon,
                    title = "Board not available",
                    message = state.message,
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                    },
                )

                is BoardUiState.Ready -> BoardContent(
                    state = state,
                    viewport = viewport,
                    initialPanelHidden = initialPanelHidden,
                    onAction = onAction,
                    onTap = onTap,
                    onOpen = { overlay = it },
                    onAmbiguous = { at ->
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                "Two pieces are close together. Zoom in, or pick one from Pieces.",
                                actionLabel = "Zoom in",
                                withDismissAction = true,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewport.zoomStep(at)
                        }
                    },
                )
            }
        }
    }
    if (ready != null) {
        when (overlay) {
            BoardOverlay.ADD -> BoardSheet(onDismiss = { overlay = null }) {
                AddContent(
                    ready,
                    onAdd = {
                        overlay = null
                        onAction(BoardAction.Add(it))
                    },
                    onDone = { overlay = null },
                )
            }

            BoardOverlay.PIECES -> BoardSheet(onDismiss = { overlay = null }) {
                PiecesContent(
                    ready,
                    onSelect = {
                        overlay = null
                        onAction(BoardAction.Select(it))
                    },
                    onDone = { overlay = null },
                )
            }

            BoardOverlay.PUSH -> PushDialog(
                ready,
                onPush = { lane, winner ->
                    overlay = null
                    onAction(BoardAction.Push(lane, winner))
                },
                onDismiss = { overlay = null },
            )

            BoardOverlay.RESET -> ConfirmDialog(
                title = "Reset to opening?",
                message = "The board goes back to the opening for this line-up (p.6): every hero and minion on " +
                    "its Spawn point, each Lane's Battle Zone where it starts. Heroes, minions and tokens you " +
                    "added are removed. You can undo it.",
                confirmLabel = "Reset",
                icon = Icons.Outlined.RestartAlt,
                onConfirm = {
                    overlay = null
                    onAction(BoardAction.ResetToOpening)
                },
                onDismiss = { overlay = null },
            )

            null -> Unit
        }
        ready.throneCaptured?.let { notice ->
            ThroneDialog(notice.winner, onDismiss = { onAction(BoardAction.DismissThrone) })
        }
    }
}

@Composable
private fun BoardContent(
    state: BoardUiState.Ready,
    viewport: MapViewport,
    initialPanelHidden: Boolean,
    onAction: (BoardAction) -> Unit,
    onTap: (MapTap) -> Boolean,
    onOpen: (BoardOverlay) -> Unit,
    onAmbiguous: (Offset) -> Unit,
) {
    // Hidden or shown is kept for the board (and survives rotation); it applies to every selection.
    var hidden by rememberSaveable { mutableStateOf(initialPanelHidden) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
        val layout =
            remember(state.map, orientation, state.bottomTeam) { MapLayout(state.map, orientation, state.bottomTeam) }
        // What the board opens on: the Lane between both teams' hero spawn areas, Battle Zones included.
        val lane = remember(layout, state.map) { layout.laneRegion(state.map, state.geometry) }
        val scope = rememberCoroutineScope()
        var cover by remember { mutableStateOf(PanelCover()) }
        var toolsHeight by remember { mutableIntStateOf(0) }
        var badgeBottom by remember { mutableIntStateOf(0) }
        val selected = state.selected
        val panelShown = selected != null
        // The panel is hidden to see the whole board: its peek bar stays.
        val peeking = panelShown && hidden
        val sidePanelWidth = if (landscape) minOf(maxWidth * 0.46f, SIDE_PANEL) else 0.dp
        val sidePadPx = with(LocalDensity.current) { Spacing.sm.toPx() }

        // What floats over the map: the panel (bottom, or right in landscape), its peek bar, or the tools.
        LaunchedEffect(panelShown, cover, toolsHeight, badgeBottom) {
            val bottom = if (panelShown) cover.bottom.toFloat() else toolsHeight.toFloat()
            val end = if (panelShown) cover.end.toFloat() else 0f
            // The badge sits in a row padded by Spacing.sm.
            val top = if (badgeBottom > 0) badgeBottom + sidePadPx else 0f
            viewport.updateInsets(bottom, end, top)
        }
        FitWhileHidden(viewport, peeking)

        // Keep the selected piece and what its overlays reach comfortably in view (big enough to tap);
        // with the panel hidden the whole board is in view, unless the person has moved it.
        val at = selected?.at
        val reach = state.overlay?.reach.orEmpty()
        val measureTarget = state.measure?.target
        LaunchedEffect(at, reach, measureTarget, viewport.unit, viewport.bottomInset, viewport.endInset, peeking) {
            if (at == null || viewport.unit == 0f) return@LaunchedEffect
            if (peeking && !viewport.userMoved) return@LaunchedEffect
            val region = layout.bounds(reach + listOfNotNull(at, measureTarget), pad = 1.1f) ?: return@LaunchedEffect
            viewport.reveal(region, layout.center(at))
        }

        // A minion waits in the tray and taps place it: bring the spaces it can go to into view.
        val targets = state.trayTargets
        LaunchedEffect(state.activeWaitingId, targets, viewport.unit, viewport.bottomInset) {
            if (targets.isEmpty() || viewport.unit == 0f) return@LaunchedEffect
            val region = layout.bounds(targets, pad = 1.1f) ?: return@LaunchedEffect
            viewport.reveal(region, region.center)
        }

        // A push moved a Battle Zone: bring the new one (its minions just spawned) into view.
        val battleZones = state.battleZones
        var shownBattleZones by remember { mutableStateOf(battleZones) }
        LaunchedEffect(battleZones, viewport.unit) {
            if (battleZones == shownBattleZones || viewport.unit == 0f) return@LaunchedEffect
            val moved = battleZones - shownBattleZones.toSet()
            shownBattleZones = battleZones
            val hexes = moved.flatMap { state.geometry.zoneSpaces[it].orEmpty() }
            val region = layout.bounds(hexes, pad = 1.1f) ?: return@LaunchedEffect
            // Not while a minion waits in the tray: its spaces (in that Zone) are revealed above.
            if (state.trayTargets.isEmpty()) viewport.reveal(region, region.center)
        }

        BoardMapView(
            state = state,
            layout = layout,
            viewport = viewport,
            onTap = onTap,
            onDrop = { id, hex -> onAction(BoardAction.Drop(id, hex)) },
            onAmbiguous = onAmbiguous,
            lane = lane,
            modifier = Modifier.fillMaxSize(),
        )

        // TalkBack hears each move ("Brogan moved 2 spaces, to …").
        state.announcement?.let { said ->
            Box(
                Modifier.size(1.dp).semantics {
                    contentDescription = said.text
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }

        // Top: the map's status on the left, view controls on the right, the active tool between.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = if (panelShown && !hidden) sidePanelWidth else 0.dp)
                .padding(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!state.map.verified) {
                    // The badge stays put: the map is framed below it.
                    UnverifiedMap(
                        state.map.name,
                        Modifier.onGloballyPositioned { badgeBottom = it.boundsInParent().bottom.toInt() },
                    )
                }
                ToolBanner(state, onAction)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (viewport.isFit) {
                    MapButton(Icons.Outlined.ZoomIn, "Zoom to the lane") { scope.launch { viewport.animateLane() } }
                } else {
                    MapButton(Icons.Outlined.CenterFocusStrong, "Fit the whole board") {
                        scope.launch { viewport.animateFit() }
                    }
                }
                MapButton(Icons.Outlined.SwapVert, "Flip: ${BoardText.team(state.bottomTeam.opponent)} at the bottom") {
                    onAction(BoardAction.Flip)
                }
            }
        }

        // Bottom (portrait) or right (landscape): the tray, then the panel (or its peek bar) or the tools.
        if (selected != null) {
            val palette = boardPalette()
            HideablePanel(
                landscape = landscape,
                hidden = hidden,
                onHiddenChange = { hidden = it },
                peek = PanelPeek(text = pieceWhere(state, selected), label = pieceTitle(state, selected)),
                sideWidth = sidePanelWidth,
                maxShare = PANEL_MAX,
                onCover = { cover = it },
                peekLeading = {
                    PieceToken(
                        selected,
                        (selected as? HeroPiece)?.let { state.heroes.hero(it.heroId) },
                        scaledIconSize(28.dp, maxScale = 1.4f),
                        palette,
                    )
                },
                peekTrailing = {
                    IconButton(onClick = { onAction(BoardAction.Deselect) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Deselect")
                    }
                },
            ) {
                if (state.waiting.isNotEmpty()) WaitingTray(state, onAction, Modifier.padding(Spacing.sm))
                SelectionPanel(state = state, piece = selected, onAction = onAction)
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { toolsHeight = it.height }
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.waiting.isNotEmpty()) WaitingTray(state, onAction, Modifier.widthIn(max = 560.dp))
                if (state.tool == BoardTool.Move) Toolbar(onOpen, compact = landscape)
            }
        }
    }
}

/** The map isn't checked against the official art and rulebook yet (ADR-033). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnverifiedMap(name: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        FlowRow(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // With large text the badge alone says it; the map name would cover the board.
            if (LocalDensity.current.fontScale <= LARGE_FONT) {
                Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            UnverifiedBadge()
        }
    }
}

/** What a tap does right now, when it isn't the usual select-and-move. */
@Composable
private fun ToolBanner(state: BoardUiState.Ready, onAction: (BoardAction) -> Unit) {
    val tool = state.tool
    val text = when (tool) {
        BoardTool.Measure -> state.measure?.let { measure ->
            val line = if (measure.straight) "in a straight line" else "not in a straight line"
            "${BoardText.spaces(measure.distance)} · $line"
        } ?: "Measure: tap any space"

        is BoardTool.Place -> "Tap an empty space for ${placeLabel(state, tool.piece)}"

        BoardTool.Move -> null
    }
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(start = Spacing.md, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f, fill = false).padding(vertical = Spacing.sm)) {
                    if (tool == BoardTool.Measure && state.measure != null) {
                        Text(
                            "MEASURED · p.18",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        text.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                TextButton(onClick = { onAction(BoardAction.CancelTool) }) {
                    Text(if (tool == BoardTool.Measure) "Done" else "Cancel")
                }
            }
        }
    }
}

private fun placeLabel(state: BoardUiState.Ready, piece: NewPiece): String = when (piece) {
    is NewPiece.Hero -> "${state.heroes.hero(piece.heroId).name} (${BoardText.team(piece.team)})"
    is NewPiece.Minion -> "the ${BoardText.team(piece.team)} ${BoardText.minion(piece.type).lowercase()}"
    is NewPiece.Token -> "the token “${piece.label}”"
}

/** A round button floating over the map (Fit, Flip): shared by the sandbox and the game table. */
@Composable
internal fun MapButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/**
 * Add, Push the lane, Pieces: the board's tools when nothing is selected. [compact] (landscape,
 * where height is scarce) lays each icon beside its label.
 */
@Composable
private fun Toolbar(onOpen: (BoardOverlay) -> Unit, compact: Boolean) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.xs)) {
            ToolButton(Icons.Outlined.AddCircleOutline, "Add", compact, Modifier.weight(1f)) {
                onOpen(BoardOverlay.ADD)
            }
            ToolButton(Icons.Outlined.KeyboardDoubleArrowUp, "Push the lane", compact, Modifier.weight(1f)) {
                onOpen(BoardOverlay.PUSH)
            }
            ToolButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Pieces", compact, Modifier.weight(1f)) {
                onOpen(BoardOverlay.PIECES)
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        modifier = modifier.heightIn(min = if (compact) 48.dp else 56.dp),
    ) {
        val iconContent = @Composable {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(24.dp, 1.4f)),
            )
        }
        if (compact) {
            Row(
                Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                iconContent()
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                iconContent()
                Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** Minions that could not respawn on a taken Spawn point (p.8), placed by tapping a highlighted space. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaitingTray(state: BoardUiState.Ready, onAction: (BoardAction) -> Unit, modifier: Modifier = Modifier) {
    val palette = boardPalette()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                "Waiting to respawn · p.8",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                when {
                    !state.trayActive -> "Deselect the piece, then tap a waiting minion to place it."

                    state.activeWaiting?.targets?.isNotEmpty() == true ->
                        "Its Spawn point is taken. Tap a highlighted space: the nearest empty spaces of its Battle Zone."

                    else -> "Tap an empty space to put it on the board."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(top = Spacing.sm),
            ) {
                state.waiting.forEach { waiting ->
                    val piece = waiting.piece
                    val active = piece.id == state.activeWaitingId && state.trayActive
                    val lane = (piece as? MinionPiece)?.let { BoardText.lane(state.map, state.geometry.laneOf(it)) }
                    val name = listOfNotNull(lane, BoardText.piece(piece) { state.heroes.hero(it).name })
                        .joinToString(" · ")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            if (active) 2.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .selectable(selected = active, role = Role.RadioButton) {
                                onAction(BoardAction.ActivateWaiting(piece.id))
                            },
                    ) {
                        Row(
                            Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PieceToken(
                                piece,
                                (piece as? HeroPiece)?.let {
                                    state.heroes.hero(it.heroId)
                                },
                                28.dp,
                                palette,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private val SIDE_PANEL = 380.dp
private const val PANEL_MAX = 0.5f
private const val LARGE_FONT = 1.3f
