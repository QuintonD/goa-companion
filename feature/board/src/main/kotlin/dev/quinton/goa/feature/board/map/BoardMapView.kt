package dev.quinton.goa.feature.board.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardTool
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.MapTap
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ui.BoardPalette
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.boardPalette
import dev.quinton.goa.feature.board.ui.drawFastTravelCell
import dev.quinton.goa.feature.board.ui.drawMovementCell
import dev.quinton.goa.feature.board.ui.drawRadiusCell
import dev.quinton.goa.feature.board.ui.drawRangeCell
import dev.quinton.goa.feature.board.ui.drawSelectionCell
import dev.quinton.goa.feature.board.ui.drawSpawn
import dev.quinton.goa.feature.board.ui.drawTargetCell
import dev.quinton.goa.feature.board.ui.drawTerrain
import dev.quinton.goa.feature.board.ui.drawWaterCell
import dev.quinton.goa.feature.board.ui.hexPath
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** What a touch on the map turned out to be. */
private enum class Gesture { TAP, PAN, PINCH, DRAG }

/** The last tap; [inert] = it did nothing, so a second tap on the same [hex] right after zooms. */
private data class LastTap(val time: Long, val hex: Hex, val inert: Boolean) {
    fun isDoubleWith(time: Long, hex: Hex, timeout: Long): Boolean =
        inert && hex == this.hex && time - this.time < timeout
}

/**
 * The piece being dragged and where it is under the finger (content px). [at] is read only while
 * laying out and drawing, so a drag never recomposes the board. [dropped] remembers where the last
 * drag ended, so the piece settles from there instead of jumping back to where it started.
 */
@Stable
private class DragState {
    var pieceId by mutableStateOf<String?>(null)
    var at by mutableStateOf(Offset.Zero)
    var dropped by mutableStateOf<Dropped?>(null)
    private var serial = 0

    fun end(): Dropped? {
        val id = pieceId ?: return null
        return Dropped(id, at, ++serial).also {
            dropped = it
            pieceId = null
        }
    }
}

private data class Dropped(val pieceId: String, val at: Offset, val serial: Int)

/** Diameter of a piece in map units (a hex is √3 ≈ 1.73 across its flats). */
internal const val PIECE_SIZE = 1.46f

/**
 * The board: the map drawn in the app's own vector style, the pieces on it, and every touch on it.
 * A tap selects or moves (resolved by [onTap]); dragging the selected piece moves it, a long press
 * lifts any piece, dropping on another piece swaps them ([onDrop]); any other drag pans, pinch
 * zooms, a double tap on a space where nothing happened zooms in. When zoomed out, a tap near a
 * piece selects it; a tap between two close pieces calls [onAmbiguous] instead of guessing.
 */
@Composable
fun BoardMapView(
    state: BoardUiState.Ready,
    layout: MapLayout,
    viewport: MapViewport,
    onTap: (MapTap) -> Boolean,
    onDrop: (pieceId: String, hex: Hex) -> Unit,
    onAmbiguous: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    lane: Rect? = null,
) {
    val palette = boardPalette()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val drag = remember { DragState() }
    val current by rememberUpdatedState(state)
    val tap by rememberUpdatedState(onTap)
    val drop by rememberUpdatedState(onDrop)
    val ambiguous by rememberUpdatedState(onAmbiguous)
    val maxHexPx = with(density) { MAX_HEX.toPx() }
    val minHexPx = with(density) { COMFORTABLE_HEX.toPx() }
    val touchPx = with(density) { TOUCH_RADIUS.toPx() }
    val battleZones = remember(state.board.state.battleZones, state.map) { state.battleZones.toSet() }

    Box(
        modifier
            .clipToBounds()
            .background(palette.canvas)
            .onSizeChanged {
                val size = Size(it.width.toFloat(), it.height.toFloat())
                viewport.setUp(layout, size, lane, state.map.lanes.size < 2, minHexPx, maxHexPx)
            }
            .pointerInput(layout, viewport) {
                var lastTap: LastTap? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val board = current
                    val onPiece = pieceUnder(board, layout, viewport, down.position)
                        ?.takeIf { board.tool == BoardTool.Move }
                    // Only the selected piece drags at once; any other piece needs a long press first.
                    val selectedPiece = onPiece?.takeIf { it.id == board.selectedId }
                    var travelled = 0f
                    var mode = Gesture.TAP
                    var upTime = down.uptimeMillis
                    var lastTime = down.uptimeMillis
                    var active = true
                    while (active) {
                        val waitLongPress = mode == Gesture.TAP && onPiece != null && selectedPiece == null
                        val event = if (waitLongPress) {
                            val left = viewConfiguration.longPressTimeoutMillis - (lastTime - down.uptimeMillis)
                            withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            mode = Gesture.DRAG
                            drag.at = viewport.toContent(down.position)
                            drag.pieceId = onPiece!!.id
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            continue
                        }
                        lastTime = event.changes.firstOrNull()?.uptimeMillis ?: lastTime
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.isEmpty() -> {
                                upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                                active = false
                            }

                            pressed.size > 1 -> {
                                if (mode == Gesture.DRAG) drag.pieceId = null
                                mode = Gesture.PINCH
                                viewport.transform(
                                    event.calculateZoom(),
                                    event.calculateCentroid(),
                                    event.calculatePan(),
                                )
                                event.changes.forEach { it.consume() }
                            }

                            else -> {
                                val change = pressed.first()
                                val delta = change.position - change.previousPosition
                                travelled += delta.getDistance()
                                if (mode == Gesture.TAP && travelled > viewConfiguration.touchSlop) {
                                    mode = if (selectedPiece != null) Gesture.DRAG else Gesture.PAN
                                    if (mode == Gesture.DRAG) {
                                        drag.pieceId = selectedPiece!!.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                                when (mode) {
                                    Gesture.DRAG -> drag.at = viewport.toContent(change.position)
                                    Gesture.PAN, Gesture.PINCH -> viewport.panBy(delta)
                                    Gesture.TAP -> Unit
                                }
                                if (mode != Gesture.TAP) change.consume()
                            }
                        }
                    }
                    when (mode) {
                        Gesture.DRAG -> {
                            lastTap = null
                            val dropped = drag.end()
                            if (dropped != null) drop(dropped.pieceId, layout.hexAt(dropped.at / viewport.unit))
                        }

                        Gesture.TAP -> {
                            val hex = layout.hexAt(viewport.screenToMap(down.position))
                            val timeout = viewConfiguration.doubleTapTimeoutMillis
                            if (lastTap?.isDoubleWith(upTime, hex, timeout) == true) {
                                lastTap = null
                                scope.launch { viewport.zoomStep(down.position) }
                            } else {
                                val resolved = resolveTap(current, layout, viewport, down.position, touchPx)
                                if (resolved == null) ambiguous(down.position)
                                val did = resolved != null && tap(resolved)
                                lastTap = LastTap(upTime, hex, inert = resolved != null && !did)
                            }
                        }

                        else -> lastTap = null
                    }
                }
            },
    ) {
        if (viewport.unit > 0f) {
            val unit = viewport.unit
            val contentSize = with(density) { DpSize(viewport.content.width.toDp(), viewport.content.height.toDp()) }
            val transform = Modifier
                .size(contentSize)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = viewport.zoom
                    scaleY = viewport.zoom
                    translationX = viewport.pan.x
                    translationY = viewport.pan.y
                }
            // The map, then the Zone labels (fixed size, below every piece and overlay), then the rest.
            Box(transform) { MapBase(state.map, battleZones, layout, unit, palette) }
            ZoneLabels(state, layout, viewport, palette)
            Box(transform.semantics { isTraversalGroup = true }) {
                MapOverlays(state, layout, unit, palette)
                DropTarget(state.geometry, layout, unit, palette, drag)
                Pieces(state, layout, unit, palette, drag)
                SpaceNodes(state, layout, viewport, onTap = { tap(it) })
            }
        }
    }
}

// ---- Touch resolution --------------------------------------------------------------------

/** The piece whose disc is under [screen], if any (pieces in the tray aren't on the map). */
private fun pieceUnder(
    state: BoardUiState.Ready,
    layout: MapLayout,
    viewport: MapViewport,
    screen: Offset,
): BoardPiece? {
    val hex = layout.hexAt(viewport.screenToMap(screen))
    BoardRules.occupancy(state.board.state)[hex]?.let { return it }
    val radius = PIECE_SIZE / 2f * viewport.scale
    return state.pieces.filter { it.at != null }
        .map { it to (viewport.mapToScreen(layout.center(it.at!!)) - screen).getDistance() }
        .filter { it.second <= radius }
        .minByOrNull { it.second }?.first
}

/**
 * What a tap at [screen] means. A tap on a piece's space is that piece. With nothing selected and
 * the map zoomed out, a tap within [touchPx] of one piece selects it; between two pieces about as
 * close it is ambiguous (null) and the screen suggests zooming. Otherwise it is the space.
 */
internal fun resolveTap(
    state: BoardUiState.Ready,
    layout: MapLayout,
    viewport: MapViewport,
    screen: Offset,
    touchPx: Float,
): MapTap? {
    val hex = layout.hexAt(viewport.screenToMap(screen))
    val occupied = BoardRules.occupancy(state.board.state)
    occupied[hex]?.let { return MapTap.Piece(it.id) }
    val choosing = state.tool == BoardTool.Move && state.selectedId == null && !state.trayActive
    if (!choosing) return MapTap.Space(hex)
    val reach = maxOf(touchPx, PIECE_SIZE / 2f * viewport.scale)
    val near = occupied.entries
        .map { (at, piece) -> piece to (viewport.mapToScreen(layout.center(at)) - screen).getDistance() }
        .filter { it.second <= reach }
        .sortedBy { it.second }
    return when {
        near.isEmpty() -> MapTap.Space(hex)
        near.size == 1 || near[1].second > near[0].second * AMBIGUITY -> MapTap.Piece(near[0].first.id)
        else -> null
    }
}

// ---- Drawing -----------------------------------------------------------------------------

/**
 * The map itself: water ring, Zone fills, terrain, Zone borders and Spawn points. Built once per
 * map, Battle Zones, layout and size; panning, zooming and dragging never rebuild it.
 */
@Composable
private fun MapBase(map: BoardMap, battleZones: Set<String>, layout: MapLayout, unit: Float, palette: BoardPalette) {
    val draw = remember(map, battleZones, layout, unit, palette) {
        Modifier.drawWithCache {
            val geometry = BoardGeometry.of(map)
            val laneIndex = map.lanes.flatMap { lane -> lane.zones.mapIndexed { i, zone -> zone to i } }.toMap()
            fun corners(hex: Hex) = layout.corners(hex).map { it * unit }
            val water = geometry.edge.map { corners(it) }
            val fills = map.spaces.groupBy { space ->
                val zone = geometry.zones[space.zone]
                when {
                    space.zone in battleZones -> palette.battle
                    zone?.kind == ZoneKind.THRONE -> palette.throne(zone.team ?: Team.RED)
                    zone?.kind == ZoneKind.SIDE -> palette.side
                    (laneIndex[space.zone] ?: 0) % 2 == 0 -> palette.laneA
                    else -> palette.laneB
                }
            }.mapValues { (_, spaces) -> Path().apply { spaces.forEach { addPath(hexPath(corners(it.hex))) } } }
            val outlines = Path().apply { map.spaces.forEach { addPath(hexPath(corners(it.hex))) } }
            // Zone borders: every edge between two Zones or at the edge of the board.
            val borders = Path()
            val battleBorders = Path()
            map.spaces.forEach { space ->
                HexGrid.DIRECTIONS.indices.forEach { d ->
                    val neighbour = geometry.spaces[space.hex + HexGrid.DIRECTIONS[d]]
                    if (neighbour == null || neighbour.zone != space.zone) {
                        val (a, b) = layout.edge(space.hex, d)
                        val target = if (space.zone in battleZones) battleBorders else borders
                        target.moveTo(a.x * unit, a.y * unit)
                        target.lineTo(b.x * unit, b.y * unit)
                    }
                }
            }
            val terrain = map.terrain.mapIndexed { i, hex ->
                Triple(
                    corners(hex),
                    layout.center(hex) * unit,
                    hex.q * 7 + hex.r * 13 + i,
                )
            }
            val spawns = map.spaces.mapNotNull { space -> space.spawn?.let { it to layout.center(space.hex) * unit } }
            val gap = Stroke(width = unit * 0.1f, join = StrokeJoin.Round)
            val border = Stroke(width = unit * 0.16f, cap = StrokeCap.Round)
            val battle = Stroke(width = unit * 0.2f, cap = StrokeCap.Round)
            onDrawBehind {
                water.forEach { drawWaterCell(it, palette) }
                fills.forEach { (color, path) -> drawPath(path, color) }
                drawPath(outlines, palette.gap, style = gap)
                terrain.forEach { (c, m, seed) -> drawTerrain(c, m, unit, palette, seed) }
                drawPath(borders, palette.zoneBorder, style = border)
                drawPath(battleBorders, palette.battleBorder, style = battle)
                spawns.forEach { (spawn, at) -> drawSpawn(spawn, at, unit, palette) }
            }
        }
    }
    Spacer(Modifier.fillMaxSize().then(draw))
}

/** Overlays of the selection, the tray's targets and Measure: rebuilt only when one of them changes. */
@Composable
private fun MapOverlays(state: BoardUiState.Ready, layout: MapLayout, unit: Float, palette: BoardPalette) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val numberStyle = TextStyle(fontWeight = FontWeight.Bold, color = palette.onMovement)
    val overlay = state.overlay
    val targets = state.trayTargets
    val selectedAt = state.selected?.at
    val measure = state.measure
    val draw = remember(overlay, targets, selectedAt, measure, layout, unit, palette) {
        Modifier.drawWithCache {
            fun cell(hex: Hex) = layout.corners(hex).map { it * unit } to layout.center(hex) * unit
            val numbers = HashMap<Int, TextLayoutResult>()
            fun number(n: Int): TextLayoutResult = numbers.getOrPut(n) {
                measurer.measure(n.toString(), numberStyle.copy(fontSize = (unit * 0.78f).toSp()))
            }
            val radius = overlay?.radius.orEmpty().map(::cell)
            val range = overlay?.range.orEmpty().map(::cell)
            val fast = overlay?.fastTravel.orEmpty().map(::cell)
            val movement = overlay?.movement.orEmpty().map { (hex, steps) -> cell(hex) to number(steps) }
            val goTo = (targets + overlay?.returnTo.orEmpty()).map(::cell)
            val selected = selectedAt?.let(::cell)
            val line = measure?.let { m -> selectedAt?.let { (layout.center(it) * unit) to cell(m.target) } }
            val dash = PathEffect.dashPathEffect(floatArrayOf(unit * 0.3f, unit * 0.2f))
            onDrawBehind {
                radius.forEach { (c, m) -> drawRadiusCell(c, m, unit, palette) }
                range.forEach { (c, m) -> drawRangeCell(c, m, unit, palette) }
                fast.forEach { (c, m) -> drawFastTravelCell(c, m, unit, palette) }
                movement.forEach { (cell, text) ->
                    val (c, m) = cell
                    drawMovementCell(c, m, palette)
                    drawText(text, topLeft = m - Offset(text.size.width / 2f, text.size.height / 2f))
                }
                goTo.forEach { (c, m) -> drawTargetCell(c, m, unit, palette) }
                selected?.let { (c, m) -> drawSelectionCell(c, m, unit, palette) }
                if (line != null && measure != null) {
                    val (a, target) = line
                    val b = target.second
                    drawLine(palette.halo, a, b, strokeWidth = unit * 0.22f, cap = StrokeCap.Round)
                    drawLine(
                        palette.measure,
                        a,
                        b,
                        strokeWidth = unit * 0.12f,
                        cap = StrokeCap.Round,
                        pathEffect = if (measure.straight) null else dash,
                    )
                    drawPath(
                        hexPath(target.first),
                        palette.measure,
                        style = Stroke(width = unit * 0.16f, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
    Spacer(Modifier.fillMaxSize().then(draw))
}

/** The space under a dragged piece: read in the draw phase, so a drag only redraws this. */
@Composable
private fun DropTarget(
    geometry: BoardGeometry,
    layout: MapLayout,
    unit: Float,
    palette: BoardPalette,
    drag: DragState,
) {
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            if (drag.pieceId == null) return@drawBehind
            val hex = layout.hexAt(drag.at / unit)
            if (!geometry.isSpace(hex)) return@drawBehind
            val corners = layout.corners(hex).map { it * unit }
            val m = layout.center(hex) * unit
            val path = hexPath(corners, 0.96f, m)
            drawPath(path, palette.selection.copy(alpha = 0.25f))
            drawPath(path, palette.selection, style = Stroke(width = unit * 0.14f))
        },
    )
}

@Composable
private fun Pieces(state: BoardUiState.Ready, layout: MapLayout, unit: Float, palette: BoardPalette, drag: DragState) {
    val density = LocalDensity.current
    val sizePx = PIECE_SIZE * unit
    val sizeDp = with(density) { sizePx.toDp() }
    val half = Offset(sizePx / 2f, sizePx / 2f)
    val dragging = drag.pieceId
    // Draw the selected and the dragged piece last, above their neighbours.
    val ordered = state.pieces.filter { it.at != null }.sortedBy { it.id == state.selectedId || it.id == dragging }
    ordered.forEach { piece ->
        key(piece.id) {
            val target = layout.center(piece.at!!) * unit - half
            val position = remember { Animatable(target, Offset.VectorConverter) }
            val dropped = drag.dropped?.takeIf { it.pieceId == piece.id }
            LaunchedEffect(target, dropped?.serial) {
                // A dropped piece settles from where the finger let go, not from where the drag began.
                if (dropped != null) position.snapTo(dropped.at - half)
                position.animateTo(target, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized))
            }
            val lifted = dragging == piece.id
            PieceToken(
                piece = piece,
                hero = (piece as? HeroPiece)?.let { state.heroes.hero(it.heroId) },
                size = sizeDp,
                palette = palette,
                lifted = lifted,
                modifier = Modifier
                    .offset {
                        val at = if (drag.pieceId == piece.id) drag.at - half else position.value
                        IntOffset(at.x.roundToInt(), at.y.roundToInt())
                    }
                    .clearAndSetSemantics {},
            )
        }
    }
}

/**
 * One invisible node per space in view for TalkBack and tests: it reads the Zone and what stands
 * there, and its click does what a tap on that space does. Spaces scrolled out of view have no
 * node, so TalkBack never lands off screen (the Pieces list reaches any piece).
 */
@Composable
private fun SpaceNodes(state: BoardUiState.Ready, layout: MapLayout, viewport: MapViewport, onTap: (MapTap) -> Unit) {
    val density = LocalDensity.current
    val unit = viewport.unit
    val side = unit * SPACE_NODE
    val sideDp: Dp = with(density) { side.toDp() }
    val occupied = remember(state.board.state) { BoardRules.occupancy(state.board.state) }
    val ordered = remember(layout, state.map) {
        state.map.spaces.sortedWith(
            compareBy({ (layout.center(it.hex).y / 1.2f).roundToInt() }, { layout.center(it.hex).x }),
        )
    }
    val inView by remember(ordered, viewport) {
        derivedStateOf {
            val view = viewport.viewport
            ordered.filter { space ->
                val at = viewport.mapToScreen(layout.center(space.hex))
                at.x in 0f..view.width && at.y in 0f..view.height
            }
        }
    }
    inView.forEach { space ->
        key(space.hex) {
            val occupant = occupied[space.hex]
            val description = SpaceText.describe(state, space.hex, occupant)
            val action = SpaceText.action(state, space.hex, occupant)
            val center = layout.center(space.hex) * unit
            Box(
                Modifier
                    .offset { IntOffset((center.x - side / 2f).roundToInt(), (center.y - side / 2f).roundToInt()) }
                    .size(sideDp)
                    .clearAndSetSemantics {
                        contentDescription = description
                        if (occupant != null && occupant.id == state.selectedId) selected = true
                        if (action != null) {
                            onClick(label = action) {
                                onTap(if (occupant != null) MapTap.Piece(occupant.id) else MapTap.Space(space.hex))
                                true
                            }
                        }
                    },
            )
        }
    }
}

/** Which side of its anchor a Zone label sits on, outside the Zone's edge. */
private enum class LabelSide { ABOVE, BELOW, LEFT, RIGHT }

/** A label and the places it may go, best first: each an anchor (map units) and a side. */
private data class ZoneLabel(val text: String, val team: Team?, val places: List<Pair<Offset, LabelSide>>)

/**
 * Labels that stay readable at any zoom: each Lane's Battle Zone ("Lane 1 · Battle Zone" on a
 * double-lane map) and the two Throne Zones, set just outside their Zone's edge. A label takes the
 * first of its places that is fully in view, clear of the map buttons and of every piece and
 * highlighted space; with none free it stays hidden rather than cover what is on the board.
 */
@Composable
private fun ZoneLabels(state: BoardUiState.Ready, layout: MapLayout, viewport: MapViewport, palette: BoardPalette) {
    val map = state.map
    val battle = state.battleZones
    val labels = remember(map, layout, battle, state.bottomTeam) { zoneLabels(state, layout) }
    val overlay = state.overlay
    val targets = state.trayTargets
    val measure = state.measure
    val from = state.selected?.at
    val busy = remember(state.board.state, overlay, targets, measure, from, layout) {
        val hexes =
            state.pieces.mapNotNull { it.at } + targets + overlay?.reach.orEmpty() + overlay?.fastTravel.orEmpty()
        // The Measure line too, sampled every half space.
        val line = if (measure != null && from != null) {
            val a = layout.center(from)
            val b = layout.center(measure.target)
            val n = ((b - a).getDistance() * 2f).roundToInt().coerceAtLeast(1)
            (0..n).map { i -> a + (b - a) * (i / n.toFloat()) }
        } else {
            emptyList()
        }
        hexes.distinct().map(layout::center) + line
    }
    val density = LocalDensity.current
    val margin = with(density) { 6.dp.toPx() }
    // The Fit and Flip buttons in the top right corner.
    val buttons = with(density) { Size(64.dp.toPx(), 124.dp.toPx()) }
    val fontScale = density.fontScale
    val labelScale = minOf(fontScale, MAX_LABEL_SCALE) / fontScale
    labels.forEach { label ->
        Row(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val view = viewport.visible
                        val reserved =
                            Rect(viewport.viewport.width - buttons.width, 0f, viewport.viewport.width, buttons.height)
                        val w = placeable.width.toFloat()
                        val h = placeable.height.toFloat()
                        val spot = label.places.firstNotNullOfOrNull { (anchor, side) ->
                            val at = viewport.mapToScreen(anchor)
                            val (x, y) = when (side) {
                                LabelSide.ABOVE -> at.x - w / 2f to at.y - h
                                LabelSide.BELOW -> at.x - w / 2f to at.y
                                LabelSide.LEFT -> at.x - w to at.y - h / 2f
                                LabelSide.RIGHT -> at.x to at.y - h / 2f
                            }
                            val rect = Rect(x, y, x + w, y + h)
                            val inView = rect.left >= view.left + margin && rect.top >= view.top + margin &&
                                rect.right <= view.right - margin && rect.bottom <= view.bottom - margin
                            val hexPx = viewport.scale * 0.8f
                            val clear = inView && !rect.overlaps(reserved) && busy.none { point ->
                                val p = viewport.mapToScreen(point)
                                p.x > rect.left - hexPx && p.x < rect.right + hexPx &&
                                    p.y > rect.top - hexPx && p.y < rect.bottom + hexPx
                            }
                            rect.takeIf { clear }
                        }
                        spot?.let { placeable.place(it.left.roundToInt(), it.top.roundToInt()) }
                    }
                }
                .background(palette.labelBackground, RoundedCornerShape(50))
                .border(
                    1.dp,
                    (label.team?.let(palette::team) ?: palette.battleBorder).copy(alpha = 0.8f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label.team != null) {
                TeamMark(label.team, color = palette.team(label.team), markSize = 8.dp)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label.text,
                // Map furniture: grows a little with large text, never enough to cover the map.
                style = GoaTextStyles.overline.copy(fontSize = 9.sp * labelScale, lineHeight = 12.sp * labelScale),
                color = label.team?.let(palette::team) ?: palette.label,
                maxLines = 1,
            )
        }
    }
}

/**
 * Where the labels may go. A Throne Zone's label: beyond its outer edge first (below the bottom
 * team's, above the top team's in portrait; left and right in landscape), then beside it. A Battle
 * Zone's: beside it on the side of the map it lies towards, then the other sides.
 */
private fun zoneLabels(state: BoardUiState.Ready, layout: MapLayout): List<ZoneLabel> {
    val map = state.map
    val geometry = state.geometry
    val portrait = layout.orientation == MapOrientation.PORTRAIT
    val all = geometry.spaces.keys.map(layout::center)
    val middle = Offset(all.map { it.x }.average().toFloat(), all.map { it.y }.average().toFloat())
    fun sides(hexes: List<Offset>): Map<LabelSide, Pair<Offset, LabelSide>> {
        val mid = Offset(hexes.map { it.x }.average().toFloat(), hexes.map { it.y }.average().toFloat())
        return mapOf(
            LabelSide.ABOVE to (hexes.minBy { it.y }.let { Offset(it.x, it.y - EDGE) } to LabelSide.ABOVE),
            LabelSide.BELOW to (hexes.maxBy { it.y }.let { Offset(it.x, it.y + EDGE) } to LabelSide.BELOW),
            LabelSide.LEFT to (hexes.minBy { it.x }.let { Offset(it.x - EDGE, mid.y) } to LabelSide.LEFT),
            LabelSide.RIGHT to (hexes.maxBy { it.x }.let { Offset(it.x + EDGE, mid.y) } to LabelSide.RIGHT),
        )
    }
    return buildList {
        map.zones.filter { it.kind == ZoneKind.THRONE && it.team != null }.forEach { zone ->
            val hexes = geometry.zoneSpaces[zone.id].orEmpty().map(layout::center).ifEmpty { return@forEach }
            val bottom = zone.team == state.bottomTeam
            val order = when {
                portrait && bottom -> listOf(LabelSide.BELOW, LabelSide.LEFT, LabelSide.RIGHT)
                portrait -> listOf(LabelSide.ABOVE, LabelSide.LEFT, LabelSide.RIGHT)
                bottom -> listOf(LabelSide.LEFT, LabelSide.BELOW, LabelSide.ABOVE)
                else -> listOf(LabelSide.RIGHT, LabelSide.ABOVE, LabelSide.BELOW)
            }
            val places = sides(hexes)
            add(
                ZoneLabel(
                    "${if (zone.team == Team.RED) "RED" else "BLUE"} THRONE",
                    zone.team,
                    order.map(places::getValue),
                ),
            )
        }
        map.lanes.forEach { lane ->
            val zoneId = state.board.state.battleZones[lane.id] ?: lane.startingBattleZone
            val hexes = geometry.zoneSpaces[zoneId].orEmpty().map(layout::center).ifEmpty { return@forEach }
            val mid = Offset(hexes.map { it.x }.average().toFloat(), hexes.map { it.y }.average().toFloat())
            val order = if (portrait) {
                if (mid.x <= middle.x) {
                    listOf(LabelSide.LEFT, LabelSide.ABOVE, LabelSide.BELOW, LabelSide.RIGHT)
                } else {
                    listOf(LabelSide.RIGHT, LabelSide.ABOVE, LabelSide.BELOW, LabelSide.LEFT)
                }
            } else if (mid.y <= middle.y) {
                listOf(LabelSide.ABOVE, LabelSide.LEFT, LabelSide.RIGHT, LabelSide.BELOW)
            } else {
                listOf(LabelSide.BELOW, LabelSide.LEFT, LabelSide.RIGHT, LabelSide.ABOVE)
            }
            val places = sides(hexes)
            add(ZoneLabel(BoardText.battleZone(map, lane.id).uppercase(), null, order.map(places::getValue)))
        }
    }
}

/** How far outside a Zone's outermost space centre its label starts, in map units (a hex's edge). */
private const val EDGE = 0.95f

private const val MAX_LABEL_SCALE = 1.3f

/** A hex as large as this (dp) at the largest zoom. */
private val MAX_HEX = 120.dp

/** Hexes at least this wide (dp, across the flats) when the board frames the Lane or a selection. */
internal val COMFORTABLE_HEX = 38.dp

/** Taps within this distance of a piece select it when the map is zoomed out. */
private val TOUCH_RADIUS = 24.dp

/** The nearer of two pieces must be this much nearer to win a tap. */
private const val AMBIGUITY = 1.4f

/** Side of a space's TalkBack node, in map units (a hex is √3 wide). */
private const val SPACE_NODE = 1.6f
