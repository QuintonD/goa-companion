package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardAction
import dev.quinton.goa.feature.board.board.BoardTool
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.MapTap
import dev.quinton.goa.feature.board.board.NewPiece
import dev.quinton.goa.feature.board.board.pieceTitle
import dev.quinton.goa.feature.board.board.pieceWhere
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.SpaceText
import dev.quinton.goa.feature.board.ui.PanelPeek
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.cards.CardFocusHost
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.cards.CardSelection
import dev.quinton.goa.web.play.map.BoardSvg
import dev.quinton.goa.web.play.map.DragPolicy
import dev.quinton.goa.web.play.map.HexMark
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.map.MapLine
import dev.quinton.goa.web.play.map.MapNode
import dev.quinton.goa.web.play.map.MapScene
import dev.quinton.goa.web.play.map.MarkStyle
import dev.quinton.goa.web.play.map.PIECE_SIZE
import dev.quinton.goa.web.play.map.ScreenPoint
import dev.quinton.goa.web.play.ui.PlayScaffold
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/** The sheets and dialogs over the board. */
internal enum class BoardOverlay { ADD, PIECES, PUSH, RESET, MORE }

/**
 * A board in the sandbox (PRODUCT §8, the phone's BoardScreen): the map with its pieces, tap to
 * select and move, drag the selected piece (a long press lifts any other, dropping on a piece swaps
 * them), the selection's panel with its card and overlays, Measure, Add, Pieces, Push the lane, Undo
 * and Redo, Flip and Fit. Every change is saved as it happens.
 */
@Composable
fun BoardScreen(boardId: String) {
    val services = LocalServices.current
    val data = services.data
    val keep = rememberRetained("board-keep") { BoardController.Keep() }
    val scope = rememberCoroutineScope()
    val controller = remember(boardId) {
        BoardController(
            boardId = boardId,
            catalog = data.content.catalog,
            maps = data.play.boardMaps,
            boards = data.play.boards,
            settings = data.content.settings,
            scope = scope,
            flushScope = data.scope,
            keep = keep,
        ).also { it.start() }
    }
    DisposableEffect(controller) {
        // Safari may never come back to a page it hid: write the pending change when it is hidden.
        val hidden: (Event) -> Unit = { if (document.asDynamic().visibilityState == "hidden") controller.flush() }
        document.addEventListener("visibilitychange", hidden)
        window.addEventListener("pagehide", hidden)
        onDispose {
            document.removeEventListener("visibilitychange", hidden)
            window.removeEventListener("pagehide", hidden)
            controller.flush()
        }
    }
    when (val state = controller.state) {
        BoardUiState.Loading -> Screen("Board", fill = true) { LoadingState("Opening the board") }

        is BoardUiState.Missing -> Screen("Board") {
            EmptyState("board", "Board not available", state.message) {
                GoaButton("Back to Boards", {
                    services.router.navigate(Route.Boards, replace = true)
                }, ButtonKind.TONAL)
            }
        }

        is BoardUiState.Ready -> BoardContent(state, controller, keep)
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun BoardContent(state: BoardUiState.Ready, controller: BoardController, keep: BoardController.Keep) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val onAction: (BoardAction) -> Unit = controller::onAction
    val landscape = rememberLandscape()
    val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
    val layout =
        remember(state.map, orientation, state.bottomTeam) { HexLayout(state.map, orientation, state.bottomTeam) }
    val battleZones = state.battleZones
    val scene = remember(layout, battleZones) {
        MapScene(state.map, layout, battleZones, layout.laneRegion(state.map, state.geometry))
    }
    val camera = remember { MapCamera() }
    val focus = remember { CardFocusState() }
    var overlay by remember { mutableStateOf<BoardOverlay?>(null) }
    var hidden by remember { mutableStateOf(keep.panelHidden) }
    val selected = state.selected

    // Snackbar hints ("That space is taken: one piece per space.").
    LaunchedEffect(state.hint?.id) {
        val hint = state.hint ?: return@LaunchedEffect
        services.messages.show(hint.text)
        onAction(BoardAction.HintShown(hint.id))
    }
    // Escape leaves a tool, then the selection (the phone's Back).
    DisposableEffect(state.tool, state.selectedId) {
        val onKey: (Event) -> Unit = { event ->
            if ((event as? KeyboardEvent)?.key == "Escape" && overlay == null) {
                if (state.tool != BoardTool.Move) onAction(BoardAction.CancelTool) else onAction(BoardAction.Deselect)
            }
        }
        document.addEventListener("keydown", onKey)
        onDispose { document.removeEventListener("keydown", onKey) }
    }
    // What floats over the map: the tools when nothing is selected.
    LaunchedEffect(selected == null, state.waiting.isEmpty()) {
        camera.updateInsets(bottom = if (selected == null) TOOLS_PX else 0f, end = 0f)
    }
    // Keep the selected piece and what its overlays reach comfortably in view.
    val at = selected?.at
    val reach = state.overlay?.reach.orEmpty()
    val measureTarget = state.measure?.target
    LaunchedEffect(at, reach, measureTarget, camera.unit, hidden) {
        if (at == null || camera.unit == 0f) return@LaunchedEffect
        if (hidden && !camera.userMoved) return@LaunchedEffect
        val region = layout.bounds(reach + listOfNotNull(at, measureTarget), pad = 1.1f) ?: return@LaunchedEffect
        camera.reveal(region, layout.center(at))
    }
    // A minion waits in the tray and taps place it: bring the spaces it can go to into view.
    val targets = state.trayTargets
    LaunchedEffect(state.activeWaitingId, targets, camera.unit) {
        if (targets.isEmpty() || camera.unit == 0f) return@LaunchedEffect
        val region = layout.bounds(targets, pad = 1.1f) ?: return@LaunchedEffect
        camera.reveal(region, region.center)
    }
    // A push moved a Battle Zone: bring the new one into view.
    var shownBattleZones by remember { mutableStateOf(battleZones) }
    LaunchedEffect(battleZones, camera.unit) {
        if (battleZones == shownBattleZones || camera.unit == 0f) return@LaunchedEffect
        val moved = battleZones - shownBattleZones.toSet()
        shownBattleZones = battleZones
        val region =
            layout.bounds(moved.flatMap { state.geometry.zoneSpaces[it].orEmpty() }, pad = 1.1f)
                ?: return@LaunchedEffect
        if (state.trayTargets.isEmpty()) camera.reveal(region, region.center)
    }
    // The panel hidden to see the whole board: fit it, unless the person moved the view.
    val peeking = hidden && selected != null
    LaunchedEffect(peeking) {
        if (peeking && !camera.userMoved && camera.unit > 0f) camera.fit()
    }

    PlayScaffold(
        title = state.board.name,
        actions = {
            IconButton("undo", "Undo", onClick = {
                if (state.canUndo) onAction(BoardAction.Undo)
            }, extraClass = disabledClass(!state.canUndo))
            IconButton("redo", "Redo", onClick = {
                if (state.canRedo) onAction(BoardAction.Redo)
            }, extraClass = disabledClass(!state.canRedo))
            IconButton("more", "More options", onClick = { overlay = BoardOverlay.MORE })
        },
        panelShown = !hidden,
        onPanelShown = {
            hidden = !it
            keep.panelHidden = !it
        },
        peek = selected?.let { PanelPeek(text = pieceWhere(state, it), label = pieceTitle(state, it)) },
        peekActions = { IconButton("close", "Deselect", onClick = { onAction(BoardAction.Deselect) }) },
        panelLabel = "Selected piece",
        map = {
            BoardSvg(
                scene = scene,
                camera = camera,
                pieces = mapPieces(state.pieces, state.heroes, state.selectedId),
                marks = boardMarks(state),
                lines = boardLines(state),
                onTap = { hex, point ->
                    val tap = resolveTap(state, layout, camera, hex, point)
                    if (tap == null) {
                        services.messages.show(
                            "Two pieces are close together. Zoom in, or pick one from Pieces.",
                            "Zoom in",
                        ) {
                            scope.launch { camera.zoomStep(point) }
                        }
                        true
                    } else {
                        controller.tap(tap)
                    }
                },
                label =
                    "The board: ${state.map.name}. " + (selected?.let { "${pieceTitle(state, it)} selected." } ?: ""),
                drag = DragPolicy(
                    pieceAt = { hex ->
                        if (state.tool ==
                            BoardTool.Move
                        ) {
                            BoardRules.occupancy(state.board.state)[hex]?.id
                        } else {
                            null
                        }
                    },
                    selected = { state.selectedId },
                    onDrop = { id, hex -> onAction(BoardAction.Drop(id, hex)) },
                ),
                nodes = spaceNodes(state, layout, camera, controller),
                bottomTeam = state.bottomTeam,
            )
            MapChrome(state, camera, onAction) { overlay = it }
            // VoiceOver hears each move ("Brogan moved 2 spaces, to …"), each undo and redo.
            Div({
                classes("goa-visually-hidden")
                attr("aria-live", "polite")
                attr("role", "status")
            }) { Text(state.announcement?.text.orEmpty()) }
        },
        panel = selected?.let {
            {
                if (state.waiting.isNotEmpty()) WaitingTray(state, onAction)
                SelectionPanel(state, it, onAction, onReadCards = { heroId, cardId ->
                    readTheCards(focus, state.heroes.hero(heroId), cardId) { id ->
                        onAction(BoardAction.ChooseCard(id))
                    }
                })
            }
        },
    )
    when (overlay) {
        BoardOverlay.ADD -> AddSheet(state, onDismiss = { overlay = null }) {
            overlay = null
            onAction(BoardAction.Add(it))
        }

        BoardOverlay.PIECES -> PiecesSheet(state, onDismiss = { overlay = null }) {
            overlay = null
            onAction(BoardAction.Select(it))
        }

        BoardOverlay.PUSH -> PushDialog(state, onDismiss = { overlay = null }) { lane, winner ->
            overlay = null
            onAction(BoardAction.Push(lane, winner))
        }

        BoardOverlay.RESET -> ResetDialog(onDismiss = { overlay = null }) {
            overlay = null
            onAction(BoardAction.ResetToOpening)
        }

        BoardOverlay.MORE -> MoreSheet(state, camera, onDismiss = {
            overlay = null
        }, onPick = { overlay = it }, onAction = onAction)

        null -> Unit
    }
    state.throneCaptured?.let { ThroneDialog(it.winner) { onAction(BoardAction.DismissThrone) } }
    CardFocusHost(focus)
}

/**
 * "Read the cards" (PRODUCT §8): the hero's cards in the card focus viewer, large, swiping from one
 * to the next (opening on the card measured with); tapping the focused card again (or "Measure with
 * …") measures with it, as on the phone (SandboxCards).
 */
internal fun readTheCards(focus: CardFocusState, hero: BoardHero, chosenId: String, onMeasure: (String) -> Unit) {
    val cards = hero.measurable
    if (cards.isEmpty()) return
    focus.open(
        cards.map { FocusCard(it) },
        index = cards.indexOfFirst { it.id == chosenId }.coerceAtLeast(0),
        selection = CardSelection(
            action = { "Measure with ${it.name}" },
            context = "The app measures; it never applies a card's effect.",
            onConfirm = { i -> cards.getOrNull(i)?.let { onMeasure(it.id) } },
        ),
        title = "${hero.name}'s cards",
    )
}

private fun disabledClass(disabled: Boolean): String? = if (disabled) "goa-board-disabled" else null

/** What floats over the map: the Unverified badge, the active tool, Fit / Lane and Flip, and the tools. */
@Composable
private fun MapChrome(
    state: BoardUiState.Ready,
    camera: MapCamera,
    onAction: (BoardAction) -> Unit,
    onOpen: (BoardOverlay) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Div({ classes("goa-board-top") }) {
        Div({ classes("goa-board-top-left") }) {
            if (!state.map.verified) {
                Span({ classes("goa-board-badge") }) {
                    Icon("info", IconSize.SMALL)
                    Text("${state.map.name} · Unverified map")
                }
            }
            ToolBanner(state, onAction)
        }
        Div({ classes("goa-board-map-buttons") }) {
            if (camera.isFit) {
                IconButton("search", "Zoom to the lane", onClick = {
                    scope.launch { camera.animateLane() }
                }, extraClass = "goa-board-map-button")
            } else {
                IconButton("fit", "Fit the whole board", onClick = {
                    scope.launch { camera.animateFit() }
                }, extraClass = "goa-board-map-button")
            }
            IconButton(
                "flip",
                "Flip: ${BoardText.team(state.bottomTeam.opponent)} at the bottom",
                onClick = { onAction(BoardAction.Flip) },
                extraClass = "goa-board-map-button",
            )
        }
    }
    if (state.selected == null) {
        Div({ classes("goa-board-bottom") }) {
            if (state.waiting.isNotEmpty()) WaitingTray(state, onAction)
            if (state.tool == BoardTool.Move) {
                Div({
                    classes("goa-board-tools")
                    attr("role", "toolbar")
                    attr("aria-label", "Board tools")
                }) {
                    ToolButton("add", "Add") { onOpen(BoardOverlay.ADD) }
                    ToolButton("chevron_up", "Push the lane") { onOpen(BoardOverlay.PUSH) }
                    ToolButton("list", "Pieces") { onOpen(BoardOverlay.PIECES) }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: String, label: String, onClick: () -> Unit) {
    Button({
        classes("goa-board-tool")
        attr("type", "button")
        onClick { onClick() }
    }) {
        Icon(icon)
        Span({ classes("t-label-medium") }) { Text(label) }
    }
}

/** Measure's reading, or what Add is about to place, with Done / Cancel. */
@Composable
private fun ToolBanner(state: BoardUiState.Ready, onAction: (BoardAction) -> Unit) {
    val tool = state.tool
    if (tool == BoardTool.Move) return
    val text = when (tool) {
        BoardTool.Measure -> state.measure?.let { m ->
            val line = if (m.straight) "in a straight line" else "not in a straight line"
            "${BoardText.spaces(m.distance)} · $line"
        } ?: "Measure: tap any space"

        is BoardTool.Place -> "Tap an empty space for ${placeLabel(state, tool.piece)}"

        BoardTool.Move -> ""
    }
    Div({
        classes("goa-board-banner")
        attr("role", "status")
    }) {
        Div({ classes("goa-board-banner-text") }) {
            if (tool == BoardTool.Measure && state.measure != null) {
                Span({ classes("goa-board-overline") }) { Text("MEASURED · p.18") }
            }
            P({ classes("t-body-medium") }) { Text(text) }
        }
        GoaButton(
            if (tool ==
                BoardTool.Measure
            ) {
                "Done"
            } else {
                "Cancel"
            },
            { onAction(BoardAction.CancelTool) },
            ButtonKind.TEXT,
        )
    }
}

private fun placeLabel(state: BoardUiState.Ready, piece: NewPiece): String = when (piece) {
    is NewPiece.Hero -> "${state.heroes.hero(piece.heroId).name} (${BoardText.team(piece.team)})"
    is NewPiece.Minion -> "the ${BoardText.team(piece.team)} ${BoardText.minion(piece.type).lowercase()}"
    is NewPiece.Token -> "the token “${piece.label}”"
}

/** The overlays of the selection, the tray's targets and Measure, in map marks. */
private fun boardMarks(state: BoardUiState.Ready): List<HexMark> = buildList {
    val overlay = state.overlay
    overlay?.radius?.forEach { add(HexMark(it, MarkStyle.RADIUS)) }
    overlay?.range?.forEach { add(HexMark(it, MarkStyle.RANGE)) }
    overlay?.fastTravel?.forEach { add(HexMark(it, MarkStyle.FAST_TRAVEL)) }
    overlay?.movement?.forEach { (hex, steps) -> add(HexMark(hex, MarkStyle.MOVEMENT, steps)) }
    (state.trayTargets + overlay?.returnTo.orEmpty()).forEach { add(HexMark(it, MarkStyle.TARGET)) }
    state.selected?.at?.let { add(HexMark(it, MarkStyle.SELECTION)) }
    state.measure?.let { add(HexMark(it.target, MarkStyle.MEASURE)) }
}

private fun boardLines(state: BoardUiState.Ready): List<MapLine> {
    val measure = state.measure ?: return emptyList()
    val from = state.selected?.at ?: return emptyList()
    return listOf(MapLine(listOf(from, measure.target), dashed = !measure.straight))
}

/**
 * What a tap at [point] on [hex] means. A tap on a piece's space is that piece. With nothing
 * selected and the map zoomed out, a tap within reach of one piece selects it; between two pieces
 * about as close it is ambiguous (null) and the screen suggests zooming. Otherwise it is the space.
 */
internal fun resolveTap(
    state: BoardUiState.Ready,
    layout: HexLayout,
    camera: MapCamera,
    hex: Hex,
    point: ScreenPoint,
): MapTap? {
    val occupied = BoardRules.occupancy(state.board.state)
    occupied[hex]?.let { return MapTap.Piece(it.id) }
    val choosing = state.tool == BoardTool.Move && state.selectedId == null && !state.trayActive
    if (!choosing) return MapTap.Space(hex)
    val reach = maxOf(TOUCH_RADIUS_PX, PIECE_SIZE / 2f * camera.scale)
    val near = occupied.entries
        .map { (at, piece) -> piece to (camera.mapToScreen(layout.center(at)) - point).length() }
        .filter { it.second <= reach }
        .sortedBy { it.second }
    return when {
        near.isEmpty() -> MapTap.Space(hex)
        near.size == 1 || near[1].second > near[0].second * AMBIGUITY -> MapTap.Piece(near[0].first.id)
        else -> null
    }
}

/**
 * One button per space in view for VoiceOver (the map itself is one image to it): what stands there,
 * the Zone, its distance from the selected piece and what the overlays say (core:play SpaceText); its
 * activation does what a tap does. Spaces out of view have none (the Pieces sheet reaches any piece).
 */
private fun spaceNodes(
    state: BoardUiState.Ready,
    layout: HexLayout,
    camera: MapCamera,
    controller: BoardController,
): List<MapNode> {
    if (camera.unit == 0f) return emptyList()
    val occupied = BoardRules.occupancy(state.board.state)
    val view = camera.visible
    return state.map.spaces
        .map { it to camera.mapToScreen(layout.center(it.hex)) }
        .filter { (_, p) -> p.x in view.left..view.right && p.y in view.top..view.bottom }
        .sortedWith(compareBy({ (it.second.y / NODE_ROW).toInt() }, { it.second.x }))
        .map { (space, _) ->
            val occupant = occupied[space.hex]
            val action = SpaceText.action(state, space.hex, occupant)
            MapNode(
                key = "${space.q},${space.r}",
                label = SpaceText.describe(state, space.hex, occupant) + (action?.let { ". $it" } ?: ""),
                pressed = occupant != null && occupant.id == state.selectedId,
                onActivate = action?.let {
                    {
                        controller.tap(occupant?.let { MapTap.Piece(it.id) } ?: MapTap.Space(space.hex))
                        Unit
                    }
                },
            )
        }
}

/** The window is wider than tall: the Lane runs left to right and the panel sits at the side. */
@Composable
internal fun rememberLandscape(): Boolean {
    var landscape by remember { mutableStateOf(window.innerWidth > window.innerHeight) }
    DisposableEffect(Unit) {
        val onResize: (Event) -> Unit = { landscape = window.innerWidth > window.innerHeight }
        window.addEventListener("resize", onResize)
        onDispose { window.removeEventListener("resize", onResize) }
    }
    return landscape
}

/** Room the tools take at the bottom of the map (px), so the framing keeps the Lane clear of them. */
private const val TOOLS_PX = 76f
private const val TOUCH_RADIUS_PX = 24f
private const val AMBIGUITY = 1.4f
private const val NODE_ROW = 24f
