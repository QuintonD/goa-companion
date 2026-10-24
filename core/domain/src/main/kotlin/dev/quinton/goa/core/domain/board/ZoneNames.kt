package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team

/**
 * A Zone's name, for the screens and the game log alike. Community names are never shown
 * (ADR-033): a Zone with no verified name is named by its place: "Red Throne Zone", "Lane Zone 2"
 * (counted from Red's Throne Zone), "Side Zone 1". An unknown Zone is "Off the board".
 */
fun BoardMap.zoneName(zoneId: String?): String {
    val zone = zones.firstOrNull { it.id == zoneId } ?: return "Off the board"
    zone.name?.let { return it }
    return when (zone.kind) {
        ZoneKind.THRONE -> zone.team?.let { "${if (it == Team.RED) "Red" else "Blue"} Throne Zone" } ?: "Throne Zone"

        ZoneKind.LANE -> {
            val lane = lanes.firstOrNull { zone.id in it.zones }
            val index = lane?.zones?.indexOf(zone.id) ?: -1
            val prefix = if (lanes.size > 1 && lane != null) "Lane ${lanes.indexOf(lane) + 1} · " else ""
            if (index > 0) "${prefix}Lane Zone $index" else "${prefix}Lane Zone"
        }

        ZoneKind.SIDE -> {
            val sides = zones.filter { it.kind == ZoneKind.SIDE }
            if (sides.size > 1) "Side Zone ${sides.indexOf(zone) + 1}" else "Side Zone"
        }
    }
}
