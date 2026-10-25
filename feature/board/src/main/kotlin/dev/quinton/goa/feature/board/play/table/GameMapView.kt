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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.MapBase
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.map.ZoneLabels
import dev.quinton.goa.feature.board.ui.BoardPalette
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.boardPalette
import dev.quinton.goa.feature.board.ui.drawRangeCell
import dev.quinton.goa.feature.board.ui.drawSelectionCell
import dev.quinton.goa.feature.board.ui.drawTargetCell
import dev.quinton.goa.feature.board.ui.hexPath
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The game board: the same map as the sandbox (MapBase, Zone labels, pieces), showing a
 * [TableUi]: heroes ringed in team colour, minions by type (immune ones ringed with a dashed
 * shield ring), tokens by label, the acting hero outlined in bronze. When the person is asked for a
 * space or a piece, every option is a dashed teal space; the tapped one is outlined, with the path
 * the engine will use drawn to it; the decision's other highlights (a card's range) are violet.
 * Pinch zooms, drag pans, double tap zooms in; every space in view is a TalkBack node.
 */
@Composable
fun GameMapView(
    table: TableUi,
    layout: MapLayout,
    viewport: MapViewport,
    onTap: (TableTap) -> Unit,
    modifier: Modifier = Modifier,
    lane: Rect? = null,
) {
    val palette = boardPalette()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(table)
    val tap by rememberUpdatedState(onTap)
    val maxHexPx = with(density) { MAX_HEX.toPx() }
    val minHexPx = with(density) { COMFORTABLE_HEX.toPx() }
    val battleZones = table.battleZones
    Box(
        modifier
            .clipToBounds()
            .background(palette.canvas)
            .onSizeChanged {
                val size = Size(it.width.toFloat(), it.height.toFloat())
                viewport.setUp(layout, size, lane, table.map.lanes.size < 2, minHexPx, maxHexPx)
            }
            .pointerInput(layout, viewport) {
                var lastUp = 0L
                var lastHex: Hex? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = 0f
                    var gesture = false
                    var up = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            up = event.changes.firstOrNull()?.uptimeMillis ?: up
                            break
                        }
                        if (pressed.size > 1) {
                            gesture = true
                            viewport.transform(event.calculateZoom(), event.calculateCentroid(), event.calculatePan())
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            moved += delta.getDistance()
                            if (gesture || moved > viewConfiguration.touchSlop) {
                                gesture = true
                                viewport.panBy(delta)
                                change.consume()
                            }
                        }
                    }
                    if (!gesture) {
                        val hex = layout.hexAt(viewport.screenToMap(down.position))
                        val did = resolve(current, hex)?.let {
                            tap(it)
                            true
                        } ?: false
                        val double = !did && hex == lastHex && up - lastUp < viewConfiguration.doubleTapTimeoutMillis
                        if (double) scope.launch { viewport.zoomStep(down.position) }
                        lastUp = if (double) 0L else up
                        lastHex = hex
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
            Box(transform) { MapBase(table.map, battleZones.toSet(), layout, unit, palette) }
            ZoneLabels(table.map, battleZones, table.bottomTeam, layout, viewport, palette, labelBusy(table, layout))
            Box(transform.semantics { isTraversalGroup = true }) {
                DecisionOverlay(table, layout, unit, palette)
                GamePieces(table, layout, unit, palette)
                GameSpaceNodes(table, layout, viewport, onTap = { tap(it) })
            }
            ActingTag(table, layout, viewport)
        }
    }
}

/**
 * "NOW" over the acting hero's piece, at a fixed size outside the zoom (ADR-041): with the bronze
 * outline of its space it says on the map who is resolving a card. Map furniture: TalkBack hears
 * "acting now" on the space.
 */
@Composable
private fun ActingTag(table: TableUi, layout: MapLayout, viewport: MapViewport) {
    val hex = table.view.acting?.let { id -> table.view.heroes.firstOrNull { it.heroId == id }?.at } ?: return
    val density = LocalDensity.current
    val gap = with(density) { 1.dp.toPx() }
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val at = viewport.mapToScreen(layout.center(hex))
                    val radius = PIECE * viewport.scale / 2f
                    val x = at.x - placeable.width / 2f
                    val y = at.y - radius - placeable.height + gap
                    if (x >= 0f && y >= 0f && x + placeable.width <= viewport.viewport.width) {
                        placeable.place(x.roundToInt(), y.roundToInt())
                    }
                }
            }
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 5.dp)
            .clearAndSetSemantics {},
    ) {
        Text(
            "NOW",
            style = TextStyle(
                fontSize = with(density) { 9.dp.toSp() },
                fontWeight = FontWeight.Bold,
                letterSpacing = with(density) { 0.5.dp.toSp() },
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}

/** What a tap on [hex] means: an option, else a piece (to read about it), else nothing. */
internal fun resolve(table: TableUi, hex: Hex): TableTap? {
    val occupant = table.occupancy[hex]
    if (occupant != null && occupant in table.pieceOptions) return TableTap.Piece(occupant)
    if (hex in table.spaceOptions) return TableTap.Space(hex)
    if (occupant != null) return TableTap.Piece(occupant)
    return null
}

/** Pieces and highlighted spaces: the Zone labels keep clear of them. */
@Composable
private fun labelBusy(table: TableUi, layout: MapLayout): List<Offset> = remember(table.view, table.decision, layout) {
    val hexes = table.occupancy.keys + table.spaceOptions.keys + table.decision?.context?.highlight.orEmpty()
    hexes.distinct().map(layout::center)
}

/** Options, highlights, the selection and its path, the acting hero, immune minions. */
@Composable
private fun DecisionOverlay(table: TableUi, layout: MapLayout, unit: Float, palette: BoardPalette) {
    val decision = table.decision
    val selected = table.selected
    val view = table.view
    val hint = table.hintAt
    val draw = remember(decision, table.asking, selected, hint, view, layout, unit, palette) {
        Modifier.drawWithCache {
            fun cell(hex: Hex) = layout.corners(hex).map { it * unit } to layout.center(hex) * unit
            val highlight = decision?.context?.highlight.orEmpty().map(::cell)
            val options = (table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at)).map(::cell)
            val chosen = when (selected) {
                is Choice.Space -> selected.hex
                is Choice.Unit -> table.at(selected.unitId)
                is Choice.Token -> table.at(selected.tokenId)
                else -> null
            }
            val chosenCell = chosen?.let(::cell)
            val path = (selected as? Choice.Space)?.let { choice ->
                val steps = table.pathTo(choice.hex)
                val from = (table.subject ?: decision?.seat)?.let(table::at)
                val points = listOfNotNull(from) + steps.filter { it != from }
                points.takeIf { it.size > 1 }?.map { layout.center(it) * unit }
            }
            val acting = view.acting?.let(table::at)?.let(::cell)
            val immune = view.minions.filter { it.immune }.map { layout.center(it.at) * unit }
            val hintCell = hint?.let(::cell)
            val dash = PathEffect.dashPathEffect(floatArrayOf(unit * 0.22f, unit * 0.16f))
            onDrawBehind {
                highlight.forEach { (c, m) -> drawRangeCell(c, m, unit, palette) }
                options.forEach { (c, m) -> drawTargetCell(c, m, unit, palette) }
                acting?.let { (c, m) ->
                    drawPath(hexPath(c, 0.98f, m), palette.halo, style = Stroke(width = unit * 0.3f))
                    drawPath(hexPath(c, 0.98f, m), palette.measure, style = Stroke(width = unit * 0.16f))
                }
                if (path != null) {
                    for (i in 0 until path.size - 1) {
                        drawLine(palette.halo, path[i], path[i + 1], strokeWidth = unit * 0.24f, cap = StrokeCap.Round)
                    }
                    for (i in 0 until path.size - 1) {
                        drawLine(
                            palette.measure,
                            path[i],
                            path[i + 1],
                            strokeWidth = unit * 0.12f,
                            cap = StrokeCap.Round,
                        )
                    }
                    path.drop(1).dropLast(1).forEach { drawCircle(palette.measure, unit * 0.12f, it) }
                }
                // A puzzle's hint: a bronze ring of dashes outside the space, apart from the selection.
                hintCell?.let { (c, m) ->
                    drawPath(hexPath(c, 1.0f, m), palette.halo, style = Stroke(width = unit * 0.34f))
                    drawPath(
                        hexPath(c, 1.0f, m),
                        palette.measure,
                        style = Stroke(width = unit * 0.2f, pathEffect = dash, join = StrokeJoin.Round),
                    )
                }
                chosenCell?.let { (c, m) -> drawSelectionCell(c, m, unit, palette) }
                immune.forEach { m ->
                    drawCircle(
                        palette.selection,
                        radius = unit * 0.84f,
                        center = m,
                        style = Stroke(width = unit * 0.1f, pathEffect = dash, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
    Spacer(Modifier.fillMaxSize().then(draw))
}

@Composable
private fun GamePieces(table: TableUi, layout: MapLayout, unit: Float, palette: BoardPalette) {
    val density = LocalDensity.current
    val sizePx = PIECE * unit
    val sizeDp = with(density) { sizePx.toDp() }
    val half = Offset(sizePx / 2f, sizePx / 2f)
    table.pieces.forEach { piece ->
        key(piece.id) {
            val target = layout.center(piece.at!!) * unit - half
            val position = remember { Animatable(target, Offset.VectorConverter) }
            LaunchedEffect(target) { position.animateTo(target, tween(Motion.LONG_MS, easing = Motion.Emphasized)) }
            PieceToken(
                piece = piece,
                hero = (piece as? HeroPiece)?.let { table.hero(it.heroId) },
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
 * One node per space in view for TalkBack and tests: what stands there, the Zone, and whether it
 * is an option; its click does what a tap does.
 */
@Composable
private fun GameSpaceNodes(table: TableUi, layout: MapLayout, viewport: MapViewport, onTap: (TableTap) -> Unit) {
    val density = LocalDensity.current
    val unit = viewport.unit
    val side = unit * SPACE_NODE
    val sideDp: Dp = with(density) { side.toDp() }
    val ordered = remember(layout, table.map) {
        table.map.spaces.sortedWith(
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
    val occupancy = table.occupancy
    inView.forEach { space ->
        key(space.hex) {
            val occupant = occupancy[space.hex]
            val tapResult = resolve(table, space.hex)
            val option = when (tapResult) {
                is TableTap.Piece -> tapResult.pieceId in table.pieceOptions
                is TableTap.Space -> true
                null -> false
            }
            val chosen = when (val s = table.selected) {
                is Choice.Space -> s.hex == space.hex
                is Choice.Unit -> s.unitId == occupant
                is Choice.Token -> s.tokenId == occupant
                else -> false
            }
            val description = buildList {
                add(occupant?.let(table::pieceName) ?: "Empty space")
                add(BoardText.zone(table.map, space.zone))
                if (occupant != null && table.view.minions.any { it.id == occupant && it.immune }) add("immune")
                if (occupant != null && occupant == table.view.acting) add("acting now")
                if (option) add("option")
                if (space.hex in table.decision?.context?.highlight.orEmpty()) add("highlighted")
                if (space.hex == table.hintAt) add("hint")
            }.joinToString(", ")
            val action = when {
                option && chosen -> null
                option -> "Choose"
                occupant != null -> "Read about it"
                else -> null
            }
            val center = layout.center(space.hex) * unit
            Box(
                Modifier
                    .offset { IntOffset((center.x - side / 2f).roundToInt(), (center.y - side / 2f).roundToInt()) }
                    .size(sideDp)
                    .clearAndSetSemantics {
                        contentDescription = description
                        if (chosen) selected = true
                        if (action != null && tapResult != null) {
                            onClick(label = action) {
                                onTap(tapResult)
                                true
                            }
                        }
                    },
            )
        }
    }
}

/** Diameter of a piece in map units (a hex is √3 across its flats); as the sandbox. */
private const val PIECE = 1.46f

/** Side of a space's TalkBack node in map units: neighbours never overlap (see BoardMapView). */
private const val SPACE_NODE = 1.46f

private val MAX_HEX = 120.dp

/** Hexes at least this wide (dp) when the board frames the Lane; as the sandbox. */
private val COMFORTABLE_HEX = 38.dp
