package dev.quinton.goa.feature.board.play.table

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.BADGE_LAYER
import dev.quinton.goa.feature.board.map.MapBase
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapPoint
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.map.PaintedMap
import dev.quinton.goa.feature.board.map.ZoneLabels
import dev.quinton.goa.feature.board.map.figureSpaceAt
import dev.quinton.goa.feature.board.map.maxHex
import dev.quinton.goa.feature.board.map.rememberMapPainting
import dev.quinton.goa.feature.board.map.standingFigures
import dev.quinton.goa.feature.board.ui.ArtBoardPalette
import dev.quinton.goa.feature.board.ui.BoardPalette
import dev.quinton.goa.feature.board.ui.CHIP_TEXT
import dev.quinton.goa.feature.board.ui.MiniatureBadge
import dev.quinton.goa.feature.board.ui.PieceLook
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.boardPalette
import dev.quinton.goa.feature.board.ui.drawNumberChip
import dev.quinton.goa.feature.board.ui.drawRangeCell
import dev.quinton.goa.feature.board.ui.drawSelectionCell
import dev.quinton.goa.feature.board.ui.drawTargetCell
import dev.quinton.goa.feature.board.ui.hexPath
import dev.quinton.goa.feature.board.ui.pieceLook
import dev.quinton.goa.feature.board.ui.showsGrid
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The game board: the same map as the sandbox (MapBase, Zone labels, pieces), showing a
 * [TableUi]: heroes ringed in team colour, minions by type (immune ones ringed with a dashed
 * shield ring), tokens by label, the acting hero outlined in bronze. When the person is asked for a
 * space or a piece, every option is a dashed teal space; the tapped one is outlined, with the path
 * the engine will use drawn to it; the decision's other highlights (a card's range) are violet.
 * Pinch zooms, drag pans, double tap zooms in; every space in view is a TalkBack node. A long press
 * on a hero's piece (or another figure of that hero) always goes to [onLongPressHero] with the hero,
 * even when a tap there would answer the question (ADR-044); TalkBack has it as a custom action.
 */
@Composable
fun GameMapView(
    table: TableUi,
    layout: MapLayout,
    viewport: MapViewport,
    onTap: (TableTap) -> Unit,
    modifier: Modifier = Modifier,
    lane: Rect? = null,
    onLongPressHero: ((String) -> Unit)? = null,
    buttons: Rect? = null,
) {
    val painting = rememberMapPainting(table.map)
    val palette = if (painting != null) ArtBoardPalette else boardPalette()
    val look = pieceLook(painted = painting != null)
    val currentLook by rememberUpdatedState(look)
    val haptics = LocalHapticFeedback.current
    val longPress by rememberUpdatedState(onLongPressHero)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(table)
    val tap by rememberUpdatedState(onTap)
    val maxHexPx = with(density) { maxHex(table.map).toPx() }
    val minHexPx = with(density) { COMFORTABLE_HEX.toPx() }
    val battleZones = table.battleZones
    val spotlight = remember(table.decision, table.asking, table.view) { questionSpotlight(table) }
    Box(
        modifier
            .clipToBounds()
            .background(painting?.sea ?: palette.canvas)
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

                    // Pan and zoom until every finger is up.
                    suspend fun AwaitPointerEventScope.track() {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                up = event.changes.firstOrNull()?.uptimeMillis ?: up
                                return
                            }
                            if (pressed.size > 1) {
                                gesture = true
                                viewport.transform(
                                    event.calculateZoom(),
                                    event.calculateCentroid(),
                                    event.calculatePan(),
                                )
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
                    }
                    val heroAt = longPress?.let { heroAt(current, layout.hexAt(viewport.screenToMap(down.position))) }
                    // On a hero's piece a press held still opens that hero, whatever a tap would do there.
                    val released = if (heroAt == null) {
                        track()
                        true
                    } else {
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { track() } != null
                    }
                    if (!released && gesture) track()
                    if (!released && !gesture) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPress?.invoke(heroAt!!)
                        // The rest of the press is the long press's: no tap, no pan.
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        lastUp = 0L
                        lastHex = null
                        return@awaitEachGesture
                    }
                    if (!gesture) {
                        val point = viewport.screenToMap(down.position)
                        val hex = layout.hexAt(point)
                        val figure = if (currentLook == PieceLook.MINIATURE) {
                            figureSpaceAt(MapPoint(point.x, point.y), standingFigures(current.pieces)) { at ->
                                layout.center(at).let { MapPoint(it.x, it.y) }
                            }
                        } else {
                            null
                        }
                        val did = resolveAt(current, hex, figure)?.let {
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
            if (painting != null) {
                PaintedMap(painting, table.map, battleZones.toSet(), layout, viewport, spotlight, showsGrid())
            } else {
                Box(transform) { MapBase(table.map, battleZones.toSet(), layout, unit, palette) }
            }
            ZoneLabels(
                table.map,
                battleZones,
                table.bottomTeam,
                layout,
                viewport,
                palette,
                labelBusy(table, layout),
                buttons = buttons,
                painted = painting != null,
            )
            Box(transform.semantics { isTraversalGroup = true }) {
                DecisionOverlay(table, layout, unit, palette)
                GamePieces(table, layout, unit, palette, look)
                GameSpaceNodes(table, layout, viewport, onTap = { tap(it) }, onHero = longPress)
            }
            ActingTag(table, layout, viewport, palette)
        }
    }
}

/**
 * "NOW", at a fixed size outside the zoom (ADR-041): a tab of the acting hero's ring, in the ring's colour and
 * joined to it, so it can't be read as belonging to the piece on the space above. With the bronze outline of
 * its space it says on the map who is resolving a card. Map furniture: TalkBack hears "acting now" on the space.
 */
@Composable
private fun ActingTag(table: TableUi, layout: MapLayout, viewport: MapViewport, palette: BoardPalette) {
    val hero = table.view.acting?.let { id -> table.view.heroes.firstOrNull { it.heroId == id } } ?: return
    val hex = hero.at ?: return
    val density = LocalDensity.current
    val join = with(density) { 2.dp.toPx() }
    val ring = if (palette.onArt) RING_ON_ART else RING_DRAWN
    val ringWidth = if (palette.onArt) RING_WIDTH_ON_ART else RING_WIDTH_DRAWN
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val at = viewport.mapToScreen(layout.center(hex))
                    val x = at.x - placeable.width / 2f
                    val y = at.y - ring * viewport.scale - placeable.height +
                        maxOf(ringWidth * viewport.scale, join)
                    if (x >= 0f && y >= 0f && x + placeable.width <= viewport.viewport.width) {
                        placeable.place(x.roundToInt(), y.roundToInt())
                    }
                }
            }
            .background(palette.team(hero.team), TabShape)
            .border(1.dp, palette.halo, TabShape)
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clearAndSetSemantics {},
    ) {
        Text(
            "NOW",
            style = TextStyle(
                fontSize = with(density) { 9.dp.toSp() },
                fontWeight = FontWeight.Bold,
                letterSpacing = with(density) { 0.5.dp.toSp() },
            ),
            color = palette.onTeam,
            maxLines = 1,
        )
    }
}

/** The hero whose piece (or other figure, Razzle's doubles) stands on [hex], if any. */
internal fun heroAt(table: TableUi, hex: Hex): String? {
    val occupant = table.occupancy[hex] ?: return null
    if (table.heroView(occupant) != null) return occupant
    return table.view.pieces.firstOrNull { it.id == occupant }?.let(::figureOf)
}

/** What a tap on [hex] means: an option, else a piece (to read about it), else nothing. */
internal fun resolve(table: TableUi, hex: Hex): TableTap? {
    val occupant = table.occupancy[hex]
    if (occupant != null && occupant in table.pieceOptions) return TableTap.Piece(occupant)
    if (hex in table.spaceOptions) return TableTap.Space(hex)
    if (occupant != null) return TableTap.Piece(occupant)
    return null
}

/**
 * A tap at [hex], with [figure] the space of a miniature whose body rises to the finger: the option or piece
 * on the hex under the finger if there is one, else that figure (its head is over the space behind it).
 */
internal fun resolveAt(table: TableUi, hex: Hex, figure: Hex?): TableTap? =
    resolve(table, hex) ?: figure?.let { resolve(table, it) }

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
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val draw = remember(decision, table.asking, selected, hint, table.planned, view, layout, unit, palette) {
        Modifier.drawWithCache {
            fun cell(hex: Hex) = layout.corners(hex).map { it * unit } to layout.center(hex) * unit
            val highlight = decision?.context?.highlight.orEmpty().map(::cell)
            // On a painting a space that a move reaches shows its number in a chip, the rest their dashes.
            val numbered = if (palette.onArt) spacesOnAPath(table) else emptyMap()
            val options = (table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at))
                .filter { it !in numbered }.map(::cell)
            val chipStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = (unit * CHIP_TEXT).toSp())
            val chips = numbered.map { (hex, steps) ->
                Triple(layout.center(hex) * unit, measurer.measure(steps.toString(), chipStyle), hex)
            }
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
            val plannedCells = table.planned.map(::cell)
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
                (listOfNotNull(hintCell) + plannedCells).forEach { (c, m) ->
                    drawPath(hexPath(c, 1.0f, m), palette.halo, style = Stroke(width = unit * 0.34f))
                    drawPath(
                        hexPath(c, 1.0f, m),
                        palette.measure,
                        style = Stroke(width = unit * 0.2f, pathEffect = dash, join = StrokeJoin.Round),
                    )
                }
                chosenCell?.let { (c, m) -> drawSelectionCell(c, m, unit, palette) }
                chips.forEach { (at, text, hex) -> drawNumberChip(text, at, unit, chosen = hex == chosen) }
                immune.forEach { m ->
                    if (palette.onArt) {
                        drawCircle(palette.halo, radius = unit * 0.84f, center = m, style = Stroke(width = unit * 0.2f))
                    }
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
private fun GamePieces(table: TableUi, layout: MapLayout, unit: Float, palette: BoardPalette, look: PieceLook) {
    val density = LocalDensity.current
    val sizePx = PIECE * unit
    val sizeDp = with(density) { sizePx.toDp() }
    val half = Offset(sizePx / 2f, sizePx / 2f)
    // Figures stand: the one nearer the bottom of the screen in front.
    table.pieces.sortedBy { layout.center(it.at!!).y }.forEach { piece ->
        key(piece.id) {
            val target = layout.center(piece.at!!) * unit - half
            val position = remember { Animatable(target, Offset.VectorConverter) }
            LaunchedEffect(target) { position.animateTo(target, tween(Motion.LONG_MS, easing = Motion.Emphasized)) }
            val placed = Modifier.offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
            PieceToken(
                piece = piece,
                hero = (piece as? HeroPiece)?.let { table.hero(it.heroId) },
                size = sizeDp,
                palette = palette,
                look = look,
                onBoard = true,
                modifier = placed.clearAndSetSemantics {},
            )
            if (look == PieceLook.MINIATURE && piece is MinionPiece) {
                MiniatureBadge(piece, sizeDp, placed.zIndex(BADGE_LAYER))
            }
        }
    }
}

/**
 * One node per space in view for TalkBack and tests: what stands there, the Zone, and whether it
 * is an option; its click does what a tap does. A hero's space also has "Show <hero>'s game state"
 * (the long press), so the hero can be read about while a tap there answers the question.
 */
@Composable
private fun GameSpaceNodes(
    table: TableUi,
    layout: MapLayout,
    viewport: MapViewport,
    onTap: (TableTap) -> Unit,
    onHero: ((String) -> Unit)?,
) {
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
                if (space.hex in table.planned) add("an enemy's plan goes here")
            }.joinToString(", ")
            val hero = onHero?.let { heroAt(table, space.hex) }
            val action = when {
                option && chosen -> null
                option -> "Choose"
                hero != null -> showGameState(table.hero(hero).name)
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
                        if (hero != null && onHero != null) {
                            customActions = listOf(
                                CustomAccessibilityAction(showGameState(table.hero(hero).name)) {
                                    onHero(hero)
                                    true
                                },
                            )
                        }
                    },
            )
        }
    }
}

/**
 * What takes the light while a question is open: every space or piece it offers, and the mover's own
 * space, so the moving piece stands in the light too. Empty when it offers nothing on the map (then the
 * Battle Zones keep the light).
 */
private fun questionSpotlight(table: TableUi): Set<Hex> {
    val options = table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at)
    if (options.isEmpty()) return emptySet()
    val mover = (table.subject ?: table.decision?.seat)?.let(table::at)
    return if (table.spaceOptions.keys.any {
            table.pathTo(it).isNotEmpty()
        }
    ) {
        options + listOfNotNull(mover)
    } else {
        options
    }
}

/** The option spaces a move reaches, with how many spaces the engine's path to each takes. */
private fun spacesOnAPath(table: TableUi): Map<Hex, Int> {
    val from = (table.subject ?: table.decision?.seat)?.let(table::at)
    return table.spaceOptions.keys.mapNotNull { hex ->
        table.pathTo(hex).filter { it != from }.size.takeIf { it > 0 }?.let { hex to it }
    }.toMap()
}

/** Diameter of a piece in map units (a hex is √3 across its flats); as the sandbox. */
private const val PIECE = 1.46f

/** A hero's ring: its outer radius and width in map units, on a painting (PieceToken.HeroCoin) and off one. */
private const val RING_ON_ART = 0.81f
private const val RING_WIDTH_ON_ART = 0.1f
private const val RING_DRAWN = 0.69f
private const val RING_WIDTH_DRAWN = 0.15f

private val TabShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

/** Side of a space's TalkBack node in map units: neighbours never overlap (see BoardMapView). */
private const val SPACE_NODE = 1.46f

/** Hexes at least this wide (dp) when the board frames the Lane; as the sandbox. */
private val COMFORTABLE_HEX = 38.dp
