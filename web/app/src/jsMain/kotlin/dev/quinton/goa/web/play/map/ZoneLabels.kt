package dev.quinton.goa.web.play.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapPoint
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** Which side of its anchor a Zone label sits on, outside the Zone's edge. */
private enum class LabelSide { ABOVE, BELOW, LEFT, RIGHT }

/** A label and the places it may go, best first: each an anchor (map units) and a side. */
private data class ZoneLabel(val text: String, val team: Team?, val places: List<Pair<MapPoint, LabelSide>>)

/**
 * Labels that stay readable at any zoom, as on the phone: each Lane's Battle Zone ("Lane 1 · Battle
 * Zone" on a double-lane map) and the two Throne Zones, set just outside their Zone's edge. A label
 * takes the first of its places that is in view and clear of every piece and marked space ([busy]);
 * with none free it stays hidden rather than cover what is on the board.
 */
@Composable
internal fun ZoneLabels(scene: MapScene, camera: MapCamera, bottomTeam: Team, busy: List<Hex>) {
    val labels = remember(scene, bottomTeam) { zoneLabels(scene, bottomTeam) }
    val view = camera.visible
    val hexPx = camera.scale * BUSY_REACH
    val busyPoints = busy.distinct().map { camera.mapToScreen(scene.layout.center(it)) }
    labels.forEach { label ->
        val w = (label.text.length * CHAR_PX + if (label.team != null) MARK_PX else 0f) + PAD_PX
        val h = LABEL_PX
        val spot = label.places.firstNotNullOfOrNull { (anchor, side) ->
            val at = camera.mapToScreen(anchor)
            val (x, y) = when (side) {
                LabelSide.ABOVE -> at.x - w / 2f to at.y - h
                LabelSide.BELOW -> at.x - w / 2f to at.y
                LabelSide.LEFT -> at.x - w to at.y - h / 2f
                LabelSide.RIGHT -> at.x to at.y - h / 2f
            }
            val inView = x >= view.left + MARGIN && y >= view.top + MARGIN && x + w <= view.right - MARGIN &&
                y + h <= view.bottom - MARGIN
            // The map buttons (Fit, Flip) in the top right corner, as on the phone.
            val underButtons = x + w > view.right - BUTTONS_W && y < view.top + BUTTONS_H
            val clear = inView && !underButtons && busyPoints.none { p ->
                p.x > x - hexPx && p.x < x + w + hexPx && p.y > y - hexPx && p.y < y + h + hexPx
            }
            (x to y).takeIf { clear }
        } ?: return@forEach
        Span({
            classes("goa-map-label")
            label.team?.let { classes(if (it == Team.RED) "red" else "blue") }
            attr("aria-hidden", "true")
            style {
                property("left", "${n(spot.first)}px")
                property("top", "${n(spot.second)}px")
            }
        }) {
            label.team?.let { Span({ classes("goa-team-mark", if (it == Team.RED) "red" else "blue") }) }
            Text(label.text)
        }
    }
}

private fun zoneLabels(scene: MapScene, bottomTeam: Team): List<ZoneLabel> {
    val map = scene.map
    val layout = scene.layout
    val geometry = BoardGeometry.of(map)
    val portrait = layout.orientation == MapOrientation.PORTRAIT
    val all = geometry.spaces.keys.map(layout::center)
    val middle = MapPoint(all.map { it.x }.average().toFloat(), all.map { it.y }.average().toFloat())
    fun sides(hexes: List<MapPoint>): Map<LabelSide, Pair<MapPoint, LabelSide>> {
        val mid = MapPoint(hexes.map { it.x }.average().toFloat(), hexes.map { it.y }.average().toFloat())
        return mapOf(
            LabelSide.ABOVE to (hexes.minBy { it.y }.let { MapPoint(it.x, it.y - EDGE) } to LabelSide.ABOVE),
            LabelSide.BELOW to (hexes.maxBy { it.y }.let { MapPoint(it.x, it.y + EDGE) } to LabelSide.BELOW),
            LabelSide.LEFT to (hexes.minBy { it.x }.let { MapPoint(it.x - EDGE, mid.y) } to LabelSide.LEFT),
            LabelSide.RIGHT to (hexes.maxBy { it.x }.let { MapPoint(it.x + EDGE, mid.y) } to LabelSide.RIGHT),
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
            val zoneId = scene.battleZones.getOrNull(index) ?: lane.startingBattleZone
            val hexes = geometry.zoneSpaces[zoneId].orEmpty().map(layout::center).ifEmpty { return@forEachIndexed }
            val mid = MapPoint(hexes.map { it.x }.average().toFloat(), hexes.map { it.y }.average().toFloat())
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
private const val MARGIN = 6f

/** The label's size (CSS px; map furniture: it doesn't grow with the text size). */
private const val CHAR_PX = 6.6f
private const val MARK_PX = 12f
private const val PAD_PX = 16f
private const val LABEL_PX = 16f
private const val BUSY_REACH = 0.8f
private const val BUTTONS_W = 64f
private const val BUTTONS_H = 124f
