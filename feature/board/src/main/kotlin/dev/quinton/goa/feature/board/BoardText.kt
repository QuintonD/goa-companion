package dev.quinton.goa.feature.board

import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Words the board screens share. Rulebook terms: Battle Zone, Throne Zone, Fast Travel, Spawn point. */
object BoardText {
    fun team(team: Team): String = if (team == Team.RED) "Red" else "Blue"

    fun minion(type: MinionType): String = when (type) {
        MinionType.MELEE -> "Melee minion"
        MinionType.RANGED -> "Ranged minion"
        MinionType.HEAVY -> "Heavy minion"
    }

    fun spawn(kind: SpawnKind): String = when (kind) {
        SpawnKind.HERO -> "hero Spawn point"
        SpawnKind.MELEE -> "melee minion Spawn point"
        SpawnKind.RANGED -> "ranged minion Spawn point"
        SpawnKind.HEAVY -> "heavy minion Spawn point"
    }

    /** "1 space", "3 spaces". */
    fun spaces(count: Int): String = if (count == 1) "1 space" else "$count spaces"

    fun count(count: Int, one: String, many: String = one + "s"): String = "$count ${if (count == 1) one else many}"

    /**
     * A Zone's name. Community names are never shown (ADR-033): a Zone with no verified [name] is
     * named by its place: "Red Throne Zone", "Lane Zone 2" (counted from Red's Throne Zone),
     * "Side Zone 1".
     */
    fun zone(map: BoardMap, zoneId: String?): String {
        val zone = map.zones.firstOrNull { it.id == zoneId } ?: return "Off the board"
        zone.name?.let { return it }
        return when (zone.kind) {
            ZoneKind.THRONE -> zone.team?.let { "${team(it)} Throne Zone" } ?: "Throne Zone"

            ZoneKind.LANE -> {
                val lane = map.lanes.firstOrNull { zone.id in it.zones }
                val index = lane?.zones?.indexOf(zone.id) ?: -1
                val prefix = if (map.lanes.size > 1 && lane != null) "Lane ${map.lanes.indexOf(lane) + 1} · " else ""
                if (index > 0) "${prefix}Lane Zone $index" else "${prefix}Lane Zone"
            }

            ZoneKind.SIDE -> {
                val sides = map.zones.filter { it.kind == ZoneKind.SIDE }
                if (sides.size > 1) "Side Zone ${sides.indexOf(zone) + 1}" else "Side Zone"
            }
        }
    }

    /** What a piece is, for lists and TalkBack: "Arien, Red", "Red Melee minion", "Token Mine". */
    fun piece(piece: BoardPiece, heroName: (String) -> String): String = when (piece) {
        is HeroPiece -> "${heroName(piece.heroId)}, ${team(piece.team)}"
        is MinionPiece -> "${team(piece.team)} ${minion(piece.type)}"
        is TokenPiece -> listOfNotNull("Token ${piece.label}", piece.team?.let(::team)).joinToString(", ")
    }

    /** A new board's name: the line-up, "Arien, Brogan vs Wasp" (or "Empty board"). */
    fun boardName(lineup: BoardLineup, heroName: (String) -> String): String {
        val red = lineup.red.joinToString(", ", transform = heroName)
        val blue = lineup.blue.joinToString(", ", transform = heroName)
        return when {
            red.isEmpty() && blue.isEmpty() -> "Empty board"
            blue.isEmpty() -> red
            red.isEmpty() -> blue
            else -> "$red vs $blue"
        }
    }

    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    /** "Changed today, 14:05", "Changed yesterday, 9:12", "Changed 12 Oct 2026". */
    fun changed(epochMillis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date: LocalDate = at.toLocalDate()
        return when (date) {
            today -> "Changed today, ${at.format(time)}"
            today.minusDays(1) -> "Changed yesterday, ${at.format(time)}"
            else -> "Changed ${at.format(day)}"
        }
    }
}

/** A finished draft's picks per team, in pick order. */
fun DraftRecord.lineup(): BoardLineup {
    val picks = steps.filter { it.action == DraftAction.PICK }
    return BoardLineup(
        red = picks.filter { it.team == Team.RED }.map { it.heroId }.distinct(),
        blue = picks.filter { it.team == Team.BLUE }.map { it.heroId }.distinct(),
    )
}

/** A match's heroes per team, in seat order (seats without a hero are left out). */
fun Match.lineup(): BoardLineup = BoardLineup(
    red = team(Team.RED).mapNotNull { it.heroId }.distinct(),
    blue = team(Team.BLUE).mapNotNull { it.heroId }.distinct(),
)

val BoardLineup.size: Int get() = red.size + blue.size

/**
 * The map a new board opens on (PRODUCT §8): the double-lane map with 8+ heroes when one is bundled,
 * else the single-lane map; [preferred] when it exists.
 */
fun defaultMap(maps: List<BoardMap>, heroes: Int, preferred: String? = null): BoardMap? {
    maps.firstOrNull { it.id == preferred }?.let { return it }
    val doubleLane = maps.firstOrNull { it.lanes.size > 1 }
    if (heroes >= DOUBLE_LANE_HEROES && doubleLane != null) return doubleLane
    return maps.firstOrNull { it.lanes.size == 1 } ?: maps.firstOrNull()
}

private const val DOUBLE_LANE_HEROES = 8

/** New piece ids for [state]: "p1", "p2", … never one already in use. */
fun pieceIds(state: BoardState): () -> String {
    var next = state.pieces.mapNotNull { it.id.removePrefix("p").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
    val used = state.pieces.mapTo(HashSet()) { it.id }
    return {
        var id = "p$next"
        while (id in used) id = "p${++next}"
        next++
        used += id
        id
    }
}
