package dev.quinton.goa.feature.board.play.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.MapBase
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.map.ZoneLabels
import dev.quinton.goa.feature.board.ui.BoardPalette
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.boardPalette
import dev.quinton.goa.feature.board.ui.drawMovementCell
import dev.quinton.goa.feature.board.ui.drawRangeCell
import dev.quinton.goa.feature.board.ui.drawSelectionCell
import dev.quinton.goa.feature.board.ui.drawTargetCell
import dev.quinton.goa.feature.board.ui.hexPath
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The game's board, drawn like the sandbox's (the same map layers, pieces and overlay cues): the
 * pending question's spaces and units as tappable targets (dashed teal), the one picked so far
 * ([picked], bright outline) with the path the engine will use to it (green, numbered), the
 * question's context ([DecisionUi.highlight], violet inner outline), the acting hero (bronze
 * outline) and immune minions (a dashed ring). A tap on a space reports it through [onTap], which
 * says whether it did anything; drag pans, pinch zooms, a double tap where nothing happened zooms in.
 */
@Composable
fun GameMapView(
    state: TableState,
    layout: MapLayout,
    viewport: MapViewport,
    picked: Hex?,
    onTap: (Hex) -> Boolean,
    modifier: Modifier = Modifier,
    lane: Rect? = null,
) {
    val palette = boardPalette()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val maxHexPx = with(density) { MAX_HEX.toPx() }
    val minHexPx = with(density) { COMFORTABLE_HEX.toPx() }
    val battleZones = remember(state.battleZones) { state.battleZones.values.toSet() }
    Box(
        modifier
            .clipToBounds()
            .background(palette.canvas)
            .onSizeChanged {
                val size = Size(it.width.toFloat(), it.height.toFloat())
                viewport.setUp(layout, size, lane, state.map.lanes.size < 2, minHexPx, maxHexPx)
            }
            .pointerInput(layout, viewport) {
                mapGestures(layout, viewport, { tap(it) }) { scope.launch { viewport.zoomStep(it) } }
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
            Box(transform) { MapBase(state.map, battleZones, layout, unit, palette) }
            val busy = remember(state.pieces, state.decision, layout) {
                (
                    state.pieces.mapNotNull {
                        it.at
                    } + state.decision?.spaces?.keys.orEmpty()
                    ).distinct().map(layout::center)
            }
            ZoneLabels(state.map, state.geometry, state.battleZones, state.bottomTeam, layout, viewport, palette, busy)
            Box(transform.semantics { isTraversalGroup = true }) {
                DecisionOverlay(state, layout, unit, palette, picked)
                GamePieces(state, layout, unit, palette)
                SpaceNodes(state, layout, viewport, picked) { tap(it) }
            }
        }
    }
}

/** Tap, pan, pinch and double-tap zoom on the map (no dragging of pieces: the engine moves them). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.mapGestures(
    layout: MapLayout,
    viewport: MapViewport,
    onTap: (Hex) -> Boolean,
    onZoom: (Offset) -> Unit,
) {
    var last: LastTap? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var moved = false
        var travelled = 0f
        var upTime = down.uptimeMillis
        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent()
            val held = event.changes.filter { it.pressed }
            when {
                held.isEmpty() -> {
                    upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                    pressed = false
                }

                held.size > 1 -> {
                    moved = true
                    viewport.transform(event.calculateZoom(), event.calculateCentroid(), event.calculatePan())
                    event.changes.forEach { it.consume() }
                }

                else -> {
                    val change = held.first()
                    val delta = change.position - change.previousPosition
                    travelled += delta.getDistance()
                    if (travelled > viewConfiguration.touchSlop) moved = true
                    if (moved) {
                        viewport.panBy(delta)
                        change.consume()
                    }
                }
            }
        }
        if (moved) {
            last = null
            return@awaitEachGesture
        }
        val hex = layout.hexAt(viewport.screenToMap(down.position))
        val previous = last
        val double = previous?.isDoubleWith(upTime, hex, viewConfiguration.doubleTapTimeoutMillis) == true
        if (double) {
            last = null
            onZoom(down.position)
        } else {
            val did = onTap(hex)
            last = LastTap(upTime, hex, inert = !did)
        }
    }
}

/** The last tap; [inert] = it did nothing, so a second tap on the same [hex] right after zooms. */
private data class LastTap(val time: Long, val hex: Hex, val inert: Boolean) {
    fun isDoubleWith(time: Long, hex: Hex, timeout: Long): Boolean =
        inert && hex == this.hex && time - this.time < timeout
}

/** The question on the board: context, targets, the pick and its path, the acting hero, immunity. */
@Composable
private fun DecisionOverlay(state: TableState, layout: MapLayout, unit: Float, palette: BoardPalette, picked: Hex?) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val numberStyle = TextStyle(fontWeight = FontWeight.Bold, color = palette.onMovement)
    val decision = state.decision
    val acting = state.heroes.firstOrNull { it.acting }?.at
    val immune = state.pieces.filter { it.id in state.immune }.mapNotNull { it.at }
    val draw = remember(decision, picked, acting, immune, layout, unit, palette) {
        Modifier.drawWithCache {
            fun cell(hex: Hex) = layout.corners(hex).map { it * unit } to layout.center(hex) * unit
            val numbers = HashMap<Int, TextLayoutResult>()
            fun number(n: Int): TextLayoutResult = numbers.getOrPut(n) {
                measurer.measure(n.toString(), numberStyle.copy(fontSize = (unit * 0.7f).toSp()))
            }
            val mine = decision?.mine == true
            val highlight = decision?.highlight.orEmpty().map(::cell)
            val unitHexes = decision?.units?.keys.orEmpty().mapNotNull { id ->
                state.pieces.firstOrNull { it.id == id }?.at
            }
            val targets = (decision?.spaces?.keys.orEmpty() + unitHexes).map(::cell)
            val path = picked?.let { decision?.paths?.get(it) }.orEmpty().filter { it != picked }
                .mapIndexed { i, hex -> cell(hex) to number(i + 1) }
            val subject = decision?.subject?.let { id -> state.pieces.firstOrNull { it.id == id }?.at }?.let(::cell)
            val pickedCell = picked?.let(::cell)
            val actingCell = acting?.let(::cell)
            val immuneCells = immune.map { layout.center(it) * unit }
            val ring = Stroke(
                width = unit * 0.1f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.16f, unit * 0.12f)),
            )
            onDrawBehind {
                highlight.forEach { (c, m) -> drawRangeCell(c, m, unit, palette) }
                actingCell?.let { (c, m) ->
                    drawPath(hexPath(c, 0.96f, m), palette.measure, style = Stroke(width = unit * 0.14f))
                }
                // The computer's options are not shown as targets: only the question it is answering.
                if (mine) targets.forEach { (c, m) -> drawTargetCell(c, m, unit, palette) }
                path.forEach { (cell, text) ->
                    val (c, m) = cell
                    drawMovementCell(c, m, palette)
                    drawText(text, topLeft = m - Offset(text.size.width / 2f, text.size.height / 2f))
                }
                subject?.let { (c, m) -> drawSelectionCell(c, m, unit, palette) }
                pickedCell?.let { (c, m) -> drawSelectionCell(c, m, unit, palette) }
                immuneCells.forEach { m ->
                    drawCircle(palette.battleBorder, radius = unit * 0.86f, center = m, style = ring)
                }
            }
        }
    }
    Spacer(Modifier.fillMaxSize().then(draw))
}

@Composable
private fun GamePieces(state: TableState, layout: MapLayout, unit: Float, palette: BoardPalette) {
    val density = LocalDensity.current
    val sizePx = PIECE_SIZE * unit
    val sizeDp = with(density) { sizePx.toDp() }
    val half = Offset(sizePx / 2f, sizePx / 2f)
    val heroes = remember(state.heroes) {
        state.heroes.associate {
            it.id to
                BoardHero(it.id, it.name, "", it.portrait, verified = true, cards = emptyList())
        }
    }
    state.pieces.filter { it.at != null }.forEach { piece ->
        key(piece.id) {
            val target = layout.center(piece.at!!) * unit - half
            val position = remember { Animatable(target, Offset.VectorConverter) }
            LaunchedEffect(target) { position.animateTo(target, tween(Motion.LONG_MS, easing = Motion.Emphasized)) }
            PieceToken(
                piece = piece,
                hero = (piece as? HeroPiece)?.let { heroes[it.heroId] },
                size = sizeDp,
                palette = palette,
                modifier = Modifier
                    .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                    .clearAndSetSemantics {},
            )
        }
    }
}

/**
 * One invisible node per space in view for TalkBack and tests: it reads the Zone, what stands there
 * and the option it is, and its click answers with that option (or shows a hero standing there).
 */
@Composable
private fun SpaceNodes(
    state: TableState,
    layout: MapLayout,
    viewport: MapViewport,
    picked: Hex?,
    onTap: (Hex) -> Unit,
) {
    val density = LocalDensity.current
    val unit = viewport.unit
    val side = unit * SPACE_NODE
    val sideDp = with(density) { side.toDp() }
    val occupied = remember(state.pieces) { state.pieces.filter { it.at != null }.associateBy { it.at!! } }
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
    val decision = state.decision?.takeIf { it.mine }
    inView.forEach { space ->
        key(space.hex) {
            val occupant = occupied[space.hex]
            val option = decision?.spaces?.get(space.hex) ?: occupant?.let { decision?.units?.get(it.id) }
            val hero = (occupant as? HeroPiece)?.let { state.hero(it.heroId) }
            val description = describe(state, space.hex, occupant, option)
            val action = when {
                option != null && picked == space.hex -> "Confirm"
                option != null -> "Choose"
                hero != null -> "Show ${hero.name}'s cards"
                else -> null
            }
            val center = layout.center(space.hex) * unit
            Box(
                Modifier
                    .offset { IntOffset((center.x - side / 2f).roundToInt(), (center.y - side / 2f).roundToInt()) }
                    .size(sideDp)
                    .clearAndSetSemantics {
                        contentDescription = description
                        if (picked == space.hex) selected = true
                        if (action != null) {
                            onClick(label = action) {
                                onTap(space.hex)
                                true
                            }
                        }
                    },
            )
        }
    }
}

private fun describe(state: TableState, hex: Hex, occupant: BoardPiece?, option: OptionUi?): String = buildList {
    add(occupant?.let { BoardText.piece(it) { id -> state.hero(id)?.name ?: id } } ?: "Empty space")
    add(BoardText.zone(state.map, state.geometry.spaces[hex]?.zone))
    if (state.battleZones.values.contains(state.geometry.spaces[hex]?.zone)) add("Battle Zone")
    if (occupant != null && occupant.id in state.immune) add("immune")
    if (option != null) add("option: ${option.label}")
    if (state.decision?.highlight?.contains(hex) == true) add("highlighted")
}.joinToString(", ")

/** Diameter of a piece in map units (as on the sandbox). */
private const val PIECE_SIZE = 1.46f

/** A hex as large as this (dp) at the largest zoom. */
private val MAX_HEX = 120.dp

/** Hexes at least this wide when the board frames the Lane. */
private val COMFORTABLE_HEX = 38.dp

/** Side of a space's TalkBack node in map units: under 1.5 neighbouring nodes never overlap. */
private const val SPACE_NODE = 1.46f
