package dev.quinton.goa.feature.board

import dev.quinton.goa.core.domain.board.zoneName
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.TokenPiece
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
import java.util.UUID

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
    fun zone(map: BoardMap, zoneId: String?): String = map.zoneName(zoneId)

    /** "Lane 1" on a map with several Lanes, else null (the single-lane map's Lane needs no name). */
    fun lane(map: BoardMap, laneId: String?): String? {
        if (map.lanes.size < 2) return null
        val index = map.lanes.indexOfFirst { it.id == laneId }
        return if (index >= 0) "Lane ${index + 1}" else null
    }

    /** "Battle Zone", or "Lane 1 · Battle Zone" on a map with several Lanes (p.20 "Battle Zone 1"). */
    fun battleZone(map: BoardMap, laneId: String?): String =
        lane(map, laneId)?.let { "$it · Battle Zone" } ?: "Battle Zone"

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

/**
 * A new piece id: a fresh UUID, never one used before on any board (an id reused after Remove would
 * let Undo, overlays or the tray mistake one piece for another).
 */
fun newPieceId(): String = UUID.randomUUID().toString()

/**
 * Two positions with the same pieces on the same spaces and the same Battle Zones, whatever the
 * pieces' ids: a board still on [other] is "untouched".
 */
fun BoardState.sameLayoutAs(other: BoardState): Boolean =
    mapId == other.mapId && battleZones == other.battleZones && layoutKey() == other.layoutKey()

private fun BoardState.layoutKey(): List<String> = pieces.map { piece ->
    when (piece) {
        is HeroPiece -> "h ${piece.heroId} ${piece.team} ${piece.at}"
        is MinionPiece -> "m ${piece.team} ${piece.type} ${piece.lane} ${piece.at}"
        is TokenPiece -> "t ${piece.label} ${piece.team} ${piece.at}"
    }
}.sorted()

/**
 * The line-up reordered to [order]'s pick order, team by team, when both name the same heroes (a
 * logged match's seats against its draft's picks); otherwise unchanged.
 */
fun BoardLineup.inOrderOf(order: BoardLineup?): BoardLineup {
    if (order == null) return this
    return BoardLineup(
        red = if (order.red.toSet() == red.toSet()) order.red else red,
        blue = if (order.blue.toSet() == blue.toSet()) order.blue else blue,
    )
}
