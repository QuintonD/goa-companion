package dev.quinton.goa.feature.board.map

import androidx.compose.animation.core.animateOffsetAsState
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
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
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
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
import dev.quinton.goa.feature.board.ui.hexPath
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** What a touch on the map turned out to be. */
private enum class Gesture { TAP, PAN, PINCH, DRAG }

/** The last tap; [inert] = it changed nothing on the board, so a second tap right after zooms. */
private data class LastTap(val time: Long, val at: Offset, val inert: Boolean) {
    fun isDoubleWith(time: Long, at: Offset, timeout: Long, slop: Float): Boolean =
        inert && time - this.time < timeout && (this.at - at).getDistance() < slop
}

/** A piece being dragged: where it is under the finger (content px). */
private data class Drag(val pieceId: String, val at: Offset)

/** Diameter of a piece in map units (a hex is √3 ≈ 1.73 across its flats). */
internal const val PIECE_SIZE = 1.46f

/**
 * The board: the map drawn in the app's own vector style, the pieces on it, and every touch on it.
 * Tap selects or moves (resolved by [onTap]), drag moves or swaps ([onDrop]), pinch zooms, drag on
 * empty space pans, double tap zooms in. When zoomed out, a tap near a piece selects it; a tap
 * between two close pieces calls [onAmbiguous] instead of guessing.
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
) {
    val palette = boardPalette()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var drag by remember { mutableStateOf<Drag?>(null) }
    val current by rememberUpdatedState(state)
    val tap by rememberUpdatedState(onTap)
    val drop by rememberUpdatedState(onDrop)
    val ambiguous by rememberUpdatedState(onAmbiguous)
    val maxHexPx = with(density) { MAX_HEX.toPx() }
    val touchPx = with(density) { TOUCH_RADIUS.toPx() }
    val doubleTapPx = with(density) { DOUBLE_TAP_SLOP.toPx() }

    Box(
        modifier
            .clipToBounds()
            .background(palette.canvas)
            .onSizeChanged { viewport.setUp(layout, Size(it.width.toFloat(), it.height.toFloat()), maxHexPx) }
            .pointerInput(layout, viewport) {
                var lastTap: LastTap? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val board = current
                    val startPiece = pieceUnder(board, layout, viewport, down.position)
                        ?.takeIf { board.tool == BoardTool.Move }
                    var travelled = 0f
                    var mode = Gesture.TAP
                    var upTime = down.uptimeMillis
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.isEmpty() -> {
                                upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                                active = false
                            }

                            pressed.size > 1 -> {
                                if (mode == Gesture.DRAG) drag = null
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
                                    mode = if (startPiece != null) Gesture.DRAG else Gesture.PAN
                                    if (mode ==
                                        Gesture.DRAG
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                                when (mode) {
                                    Gesture.DRAG -> drag = Drag(startPiece!!.id, viewport.toContent(change.position))
                                    Gesture.PAN, Gesture.PINCH -> viewport.panBy(delta)
                                    Gesture.TAP -> Unit
                                }
                                if (mode != Gesture.TAP) change.consume()
                            }
                        }
                    }
                    val dropped = drag
                    when {
                        mode == Gesture.DRAG && dropped != null -> {
                            drag = null
                            drop(dropped.pieceId, layout.hexAt(dropped.at / viewport.unit))
                            lastTap = null
                        }

                        mode == Gesture.TAP -> {
                            val timeout = viewConfiguration.doubleTapTimeoutMillis
                            if (lastTap?.isDoubleWith(upTime, down.position, timeout, doubleTapPx) == true) {
                                lastTap = null
                                scope.launch { viewport.zoomStep(down.position) }
                            } else {
                                val resolved = resolveTap(current, layout, viewport, down.position, touchPx)
                                if (resolved == null) ambiguous(down.position)
                                val changed = resolved != null && tap(resolved)
                                lastTap = LastTap(upTime, down.position, inert = !changed)
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
            Box(
                Modifier
                    .size(contentSize)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = viewport.zoom
                        scaleY = viewport.zoom
                        translationX = viewport.pan.x
                        translationY = viewport.pan.y
                    }
                    .semantics { isTraversalGroup = true },
            ) {
                MapBase(state, layout, unit, palette)
                MapOverlays(state, layout, unit, palette, drag)
                Pieces(state, layout, unit, palette, drag)
                SpaceNodes(state, layout, unit, onTap = { tap(it) })
            }
            ZoneLabels(state, layout, viewport, palette)
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
    val choosing = state.tool == BoardTool.Move && state.selectedId == null && state.activeWaiting == null
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

@Composable
private fun MapBase(state: BoardUiState.Ready, layout: MapLayout, unit: Float, palette: BoardPalette) {
    val map = state.map
    val battleZones = state.board.state.battleZones.values.toSet().ifEmpty {
        map.lanes.map { it.startingBattleZone }.toSet()
    }
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val geometry = state.geometry
            val laneIndex = map.lanes.flatMap { lane -> lane.zones.mapIndexed { i, zone -> zone to i } }.toMap()
            val fills = map.spaces.groupBy { space ->
                val zone = geometry.zones[space.zone]
                when {
                    space.zone in battleZones -> palette.battle
                    zone?.kind == ZoneKind.THRONE -> palette.throne(zone.team ?: Team.RED)
                    zone?.kind == ZoneKind.SIDE -> palette.side
                    (laneIndex[space.zone] ?: 0) % 2 == 0 -> palette.laneA
                    else -> palette.laneB
                }
            }.mapValues { (_, spaces) ->
                Path().apply { spaces.forEach { addPath(hexPath(layout.corners(it.hex).map { c -> c * unit })) } }
            }
            val outlines = Path().apply {
                map.spaces.forEach {
                    addPath(
                        hexPath(
                            layout.corners(it.hex).map { c ->
                                c *
                                    unit
                            },
                        ),
                    )
                }
            }
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
            val gap = Stroke(width = unit * 0.1f, join = StrokeJoin.Round)
            val border = Stroke(width = unit * 0.16f, cap = StrokeCap.Round)
            val battle = Stroke(width = unit * 0.2f, cap = StrokeCap.Round)
            onDrawBehind {
                fills.forEach { (color, path) -> drawPath(path, color) }
                drawPath(outlines, palette.gap, style = gap)
                map.terrain.forEachIndexed { i, hex ->
                    drawTerrain(
                        layout.corners(hex).map { it * unit },
                        layout.center(hex) * unit,
                        unit,
                        palette,
                        hex.q * 7 + hex.r * 13 + i,
                    )
                }
                drawPath(borders, palette.zoneBorder, style = border)
                drawPath(battleBorders, palette.battleBorder, style = battle)
                map.spaces.forEach { space ->
                    space.spawn?.let { drawSpawn(it, layout.center(space.hex) * unit, unit, palette) }
                }
            }
        },
    )
}

@Composable
private fun MapOverlays(state: BoardUiState.Ready, layout: MapLayout, unit: Float, palette: BoardPalette, drag: Drag?) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val numberStyle = TextStyle(fontWeight = FontWeight.Bold, color = palette.onMovement)
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val overlay = state.overlay
            val numbers = HashMap<Int, TextLayoutResult>()
            fun number(n: Int): TextLayoutResult = numbers.getOrPut(n) {
                measurer.measure(n.toString(), numberStyle.copy(fontSize = (unit * 0.78f).toSp()))
            }
            onDrawBehind {
                fun cell(hex: Hex) = layout.corners(hex).map { it * unit } to layout.center(hex) * unit
                overlay?.radius?.forEach { hex -> cell(hex).let { (c, m) -> drawRadiusCell(c, m, unit, palette) } }
                overlay?.range?.forEach { hex -> cell(hex).let { (c, m) -> drawRangeCell(c, m, unit, palette) } }
                overlay?.fastTravel?.forEach { hex ->
                    cell(hex).let { (c, m) -> drawFastTravelCell(c, m, unit, palette) }
                }
                overlay?.movement?.forEach { (hex, steps) ->
                    val (c, m) = cell(hex)
                    drawMovementCell(c, m, palette)
                    val text = number(steps)
                    drawText(text, topLeft = m - Offset(text.size.width / 2f, text.size.height / 2f))
                }
                state.activeWaiting?.targets?.forEach { hex ->
                    cell(hex).let { (c, m) -> drawTargetCell(c, m, unit, palette) }
                }
                state.selected?.at?.let { hex -> cell(hex).let { (c, m) -> drawSelectionCell(c, m, unit, palette) } }
                state.measure?.let { measure ->
                    val from = state.selected?.at ?: return@let
                    val a = layout.center(from) * unit
                    val b = layout.center(measure.target) * unit
                    drawLine(
                        palette.halo,
                        a,
                        b,
                        strokeWidth = unit * 0.22f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        palette.measure,
                        a,
                        b,
                        strokeWidth = unit * 0.12f,
                        cap = StrokeCap.Round,
                        pathEffect = if (measure.straight) {
                            null
                        } else {
                            PathEffect.dashPathEffect(
                                floatArrayOf(
                                    unit * 0.3f,
                                    unit * 0.2f,
                                ),
                            )
                        },
                    )
                    val (c, _) = cell(measure.target)
                    drawPath(hexPath(c), palette.measure, style = Stroke(width = unit * 0.16f, join = StrokeJoin.Round))
                }
                drag?.let {
                    val hex = layout.hexAt(it.at / unit)
                    if (state.geometry.isSpace(hex)) {
                        val (c, m) = cell(hex)
                        drawPath(hexPath(c, 0.96f, m), palette.selection.copy(alpha = 0.25f))
                        drawPath(hexPath(c, 0.96f, m), palette.selection, style = Stroke(width = unit * 0.14f))
                    }
                }
            }
        },
    )
}

@Composable
private fun Pieces(state: BoardUiState.Ready, layout: MapLayout, unit: Float, palette: BoardPalette, drag: Drag?) {
    val density = LocalDensity.current
    val sizePx = PIECE_SIZE * unit
    val sizeDp = with(density) { sizePx.toDp() }
    // Draw the selected and the dragged piece last, above their neighbours.
    val ordered = state.pieces.filter { it.at != null }.sortedBy {
        (it.id == state.selectedId) ||
            (it.id == drag?.pieceId)
    }
    ordered.forEach { piece ->
        key(piece.id) {
            val target = layout.center(piece.at!!) * unit - Offset(sizePx / 2f, sizePx / 2f)
            val animated by animateOffsetAsState(
                target,
                tween(Motion.MEDIUM_MS, easing = Motion.Emphasized),
                label = "piece",
            )
            val dragging = drag?.pieceId == piece.id
            PieceToken(
                piece = piece,
                hero = (piece as? HeroPiece)?.let { state.heroes.hero(it.heroId) },
                size = sizeDp,
                palette = palette,
                lifted = dragging,
                modifier = Modifier
                    .offset {
                        val at = if (dragging) drag!!.at - Offset(sizePx / 2f, sizePx / 2f) else animated
                        IntOffset(at.x.roundToInt(), at.y.roundToInt())
                    }
                    .clearAndSetSemantics {},
            )
        }
    }
}

/**
 * One invisible node per space for TalkBack and tests: it reads the Zone and what stands there,
 * and its click does what a tap on that space does.
 */
@Composable
private fun SpaceNodes(state: BoardUiState.Ready, layout: MapLayout, unit: Float, onTap: (MapTap) -> Unit) {
    val density = LocalDensity.current
    val side = unit * SPACE_NODE
    val sideDp: Dp = with(density) { side.toDp() }
    val occupied = remember(state.board.state) { BoardRules.occupancy(state.board.state) }
    val ordered = remember(layout, state.map) {
        state.map.spaces.sortedWith(
            compareBy({
                (layout.center(it.hex).y / 1.2f).roundToInt()
            }, { layout.center(it.hex).x }),
        )
    }
    ordered.forEach { space ->
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

/**
 * Labels that stay readable at any zoom: each Lane's Battle Zone and the two Throne Zones, placed at
 * their Zone on screen and kept inside the viewport.
 */
@Composable
private fun ZoneLabels(state: BoardUiState.Ready, layout: MapLayout, viewport: MapViewport, palette: BoardPalette) {
    val map = state.map
    val battle = state.board.state.battleZones.values.toSet().ifEmpty {
        map.lanes.map { it.startingBattleZone }.toSet()
    }
    val labels = remember(map, layout, battle) {
        val geometry = state.geometry
        buildList {
            map.zones.filter { it.kind == ZoneKind.THRONE && it.team != null }.forEach { zone ->
                val hexes = geometry.zoneSpaces[zone.id].orEmpty().ifEmpty { return@forEach }
                // At the Throne Zone's outer edge: the bottom (left) team's lowest (leftmost) space.
                val bottom = zone.team == state.bottomTeam
                val anchor = when (layout.orientation) {
                    MapOrientation.PORTRAIT -> if (bottom) {
                        hexes.maxBy {
                            layout.center(it).y
                        }
                    } else {
                        hexes.minBy { layout.center(it).y }
                    }

                    MapOrientation.LANDSCAPE -> if (bottom) {
                        hexes.minBy {
                            layout.center(it).x
                        }
                    } else {
                        hexes.maxBy { layout.center(it).x }
                    }
                }
                add(
                    ZoneLabel(
                        "${if (zone.team == Team.RED) "RED" else "BLUE"} THRONE",
                        zone.team,
                        layout.center(anchor),
                    ),
                )
            }
            battle.forEach { zoneId ->
                val hexes = geometry.zoneSpaces[zoneId].orEmpty().ifEmpty { return@forEach }
                val free = hexes.filter { geometry.spaces[it]?.spawn == null }.ifEmpty { hexes }
                val edge = when (layout.orientation) {
                    MapOrientation.PORTRAIT -> free.minBy { layout.center(it).x }
                    MapOrientation.LANDSCAPE -> free.minBy { layout.center(it).y }
                }
                add(ZoneLabel("BATTLE ZONE", null, layout.center(edge)))
            }
        }
    }
    val margin = with(LocalDensity.current) { 6.dp.toPx() }
    val fontScale = LocalDensity.current.fontScale
    val labelScale = minOf(fontScale, MAX_LABEL_SCALE) / fontScale
    labels.forEach { label ->
        Row(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val at = viewport.mapToScreen(label.at)
                        // A label whose Zone is out of view goes, rather than crowding the edge.
                        val inView = at.x in 0f..viewport.viewport.width && at.y in 0f..viewport.viewport.height
                        if (inView) {
                            val x = (at.x - placeable.width / 2f).coerceIn(
                                margin,
                                viewport.viewport.width - placeable.width - margin,
                            )
                            val y = (at.y - placeable.height / 2f).coerceIn(
                                margin,
                                viewport.viewport.height - placeable.height - margin,
                            )
                            placeable.place(x.roundToInt(), y.roundToInt())
                        }
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

private const val MAX_LABEL_SCALE = 1.3f

private data class ZoneLabel(val text: String, val team: Team?, val at: Offset)

/** A hex as large as this (dp) at the largest zoom. */
private val MAX_HEX = 120.dp

/** Taps within this distance of a piece select it when the map is zoomed out. */
private val TOUCH_RADIUS = 24.dp

private val DOUBLE_TAP_SLOP = 40.dp

/** The nearer of two pieces must be this much nearer to win a tap. */
private const val AMBIGUITY = 1.4f

/** Side of a space's TalkBack node, in map units (a hex is √3 wide). */
private const val SPACE_NODE = 1.6f
