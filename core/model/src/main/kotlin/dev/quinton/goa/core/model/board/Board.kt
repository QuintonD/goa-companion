package dev.quinton.goa.core.model.board

import dev.quinton.goa.core.model.catalog.SourceRef
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Board sandbox (ADR-032). The map is bundled catalog data (assets/catalog/maps/<id>.json, written
 * by tools/catalog-import, never hand-edited); boards the user sets up are local data.
 */

/** A space on the hex grid in axial coordinates (pointy-top); the cube coordinate s = -q - r. */
@Serializable
data class Hex(val q: Int, val r: Int) {
    val s: Int get() = -q - r

    operator fun plus(other: Hex): Hex = Hex(q + other.q, r + other.r)

    override fun toString(): String = "($q,$r)"
}

@Serializable
enum class MinionType {
    @SerialName("melee")
    MELEE,

    @SerialName("ranged")
    RANGED,

    @SerialName("heavy")
    HEAVY,
}

/** What a printed Spawn point spawns: a team's heroes, or one type of that team's minions. */
@Serializable
enum class SpawnKind {
    @SerialName("hero")
    HERO,

    @SerialName("melee")
    MELEE,

    @SerialName("ranged")
    RANGED,

    @SerialName("heavy")
    HEAVY,
    ;

    val minion: MinionType?
        get() = when (this) {
            HERO -> null
            MELEE -> MinionType.MELEE
            RANGED -> MinionType.RANGED
            HEAVY -> MinionType.HEAVY
        }
}

@Serializable
data class SpawnPoint(val team: Team, val kind: SpawnKind)

@Serializable
enum class ZoneKind {
    /** A team's Throne Zone (hero spawn points; the last Zone of every Lane). */
    @SerialName("throne")
    THRONE,

    /** A Zone of a Lane between the Thrones; one of them is the Lane's Battle Zone. */
    @SerialName("lane")
    LANE,

    /** A Zone outside every Lane. */
    @SerialName("side")
    SIDE,
}

/**
 * A Zone. [team] is the owning team of a Throne Zone, else null. [name] is only set when a
 * rulebook-verified name exists; the UI otherwise names Zones by their place in the Lane.
 */
@Serializable
data class BoardZone(
    val id: String,
    val kind: ZoneKind,
    val team: Team? = null,
    val name: String? = null,
)

/**
 * A Lane: its Zones from Red's Throne Zone to Blue's ([zones].first() / last()), and the Zone where
 * its minions start ([startingBattleZone], the middle one on a single-lane map).
 */
@Serializable
data class BoardLane(
    val id: String,
    val zones: List<String>,
    val startingBattleZone: String,
)

/** A nonterrain space of the map, in [zone], optionally with a printed [spawn] point. */
@Serializable
data class BoardSpace(
    val q: Int,
    val r: Int,
    val zone: String,
    val spawn: SpawnPoint? = null,
) {
    val hex: Hex get() = Hex(q, r)
}

/**
 * A playable map. [spaces] are the nonterrain spaces; [terrain] the printed terrain spaces enclosed
 * by the map (holes). Anything else is off the board. [verified] = checked against the official
 * map art and the rulebook's setup (see `tools/catalog-import/REPORT.md`); an unverified map is
 * badged in the UI.
 */
@Serializable
data class BoardMap(
    val id: String,
    val name: String,
    val lanes: List<BoardLane>,
    val zones: List<BoardZone>,
    val spaces: List<BoardSpace>,
    val terrain: List<Hex> = emptyList(),
    val printing: Int,
    val verified: Boolean,
    val sources: List<SourceRef> = emptyList(),
)

/** A game piece on (or waiting beside) the board. [at] null = waiting in the tray to be placed. */
@Serializable
sealed interface BoardPiece {
    val id: String
    val at: Hex?

    fun moveTo(hex: Hex?): BoardPiece
}

@Serializable
@SerialName("hero")
data class HeroPiece(
    override val id: String,
    override val at: Hex?,
    val heroId: String,
    val team: Team,
) : BoardPiece {
    override fun moveTo(hex: Hex?): BoardPiece = copy(at = hex)
}

@Serializable
@SerialName("minion")
data class MinionPiece(
    override val id: String,
    override val at: Hex?,
    val team: Team,
    val type: MinionType,
) : BoardPiece {
    override fun moveTo(hex: Hex?): BoardPiece = copy(at = hex)
}

/** A token (an obstacle, p.19), labelled by the user, e.g. "Mine" or "Rock". */
@Serializable
@SerialName("token")
data class TokenPiece(
    override val id: String,
    override val at: Hex?,
    val label: String,
    val team: Team? = null,
) : BoardPiece {
    override fun moveTo(hex: Hex?): BoardPiece = copy(at = hex)
}

/** Which heroes a board was set up with, in pick order per team (the opening places them in this order). */
@Serializable
data class BoardLineup(
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
)

/**
 * A position on a map. At most one piece per space (p.19: never more than one obstacle in a space).
 * [battleZones]: the current Battle Zone per lane id.
 */
@Serializable
data class BoardState(
    val mapId: String,
    val pieces: List<BoardPiece> = emptyList(),
    val battleZones: Map<String, String> = emptyMap(),
)

/**
 * A board the user set up, kept on this phone (and in the backup file), never synced. Every change
 * is saved as it happens; [name] defaults to the line-up.
 */
@Serializable
data class SavedBoard(
    val id: String,
    val name: String,
    val lineup: BoardLineup,
    val state: BoardState,
    val createdAt: Long,
    val updatedAt: Long,
)
