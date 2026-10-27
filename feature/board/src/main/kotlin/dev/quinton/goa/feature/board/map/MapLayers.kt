package dev.quinton.goa.feature.board.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.ui.BoardPalette
import dev.quinton.goa.feature.board.ui.drawSpawn
import dev.quinton.goa.feature.board.ui.drawTerrain
import dev.quinton.goa.feature.board.ui.drawWaterCell
import dev.quinton.goa.feature.board.ui.hexPath
import dev.quinton.goa.feature.board.ui.rememberSpawnArt
import kotlin.math.roundToInt

/*
 * The layers every board draws, the sandbox and the game table alike: the map itself, and the Zone
 * labels that float over it at a fixed size.
 */

/**
 * The map itself: water ring, Zone fills, terrain, Zone borders and Spawn points. Built once per
 * map, Battle Zones, layout and size; panning, zooming and dragging never rebuild it.
 */
@Composable
internal fun MapBase(map: BoardMap, battleZones: Set<String>, layout: MapLayout, unit: Float, palette: BoardPalette) {
    val art = rememberSpawnArt()
    val draw = remember(map, battleZones, layout, unit, palette, art) {
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
                spawns.forEach { (spawn, at) -> drawSpawn(spawn, at, unit, palette, art[spawn]) }
            }
        }
    }
    Spacer(Modifier.fillMaxSize().then(draw))
}

/** Which side of its anchor a Zone label sits on, outside the Zone's edge. */
private enum class LabelSide { ABOVE, BELOW, LEFT, RIGHT }

/** A label and the places it may go, best first: each an anchor (map units) and a side. */
private data class ZoneLabel(val text: String, val team: Team?, val places: List<Pair<Offset, LabelSide>>)

/**
 * Labels that stay readable at any zoom: each Lane's Battle Zone ("Lane 1 · Battle Zone" on a
 * double-lane map) and the two Throne Zones, set just outside their Zone's edge. A label takes the
 * first of its places that is fully in view, clear of the map buttons and of every piece and
 * highlighted space; with none free it stays hidden rather than cover what is on the board. [buttons]
 * is where the map buttons are, in the map's pixels (the game table's sit under the hero strip and
 * grow with the font scale); by default the sandbox's Fit and Flip in the top right corner.
 */
@Composable
internal fun ZoneLabels(
    map: BoardMap,
    battleZones: List<String>,
    bottomTeam: Team,
    layout: MapLayout,
    viewport: MapViewport,
    palette: BoardPalette,
    busy: List<Offset>,
    buttons: Rect? = null,
) {
    val labels = remember(map, layout, battleZones, bottomTeam) { zoneLabels(map, battleZones, bottomTeam, layout) }
    val density = LocalDensity.current
    val margin = with(density) { 6.dp.toPx() }
    // The Fit and Flip buttons in the top right corner, unless the screen says where its buttons are.
    val corner = with(density) { Size(64.dp.toPx(), 124.dp.toPx()) }
    val fontScale = density.fontScale
    val labelScale = minOf(fontScale, MAX_LABEL_SCALE) / fontScale
    labels.forEach { label ->
        Row(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val view = viewport.visible
                        val reserved = buttons?.inflate(margin)
                            ?: Rect(viewport.viewport.width - corner.width, 0f, viewport.viewport.width, corner.height)
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
private fun zoneLabels(map: BoardMap, battleZones: List<String>, bottomTeam: Team, layout: MapLayout): List<ZoneLabel> {
    val geometry = BoardGeometry.of(map)
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
            val bottom = zone.team == bottomTeam
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
        map.lanes.forEachIndexed { index, lane ->
            val zoneId = battleZones.getOrNull(index) ?: lane.startingBattleZone
            val hexes = geometry.zoneSpaces[zoneId].orEmpty().map(layout::center).ifEmpty { return@forEachIndexed }
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
