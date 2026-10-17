package dev.quinton.goa.core.model.match

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Team identity is always Red/Blue in code (rulebook: Red = Atlanteans, Blue = Titans). */
@Serializable
enum class Team {
    @SerialName("red")
    RED,

    @SerialName("blue")
    BLUE,
    ;

    val opponent: Team get() = if (this == RED) BLUE else RED
}

@Serializable
enum class GameMode {
    @SerialName("quick")
    QUICK,

    @SerialName("long")
    LONG,

    @SerialName("double_lane")
    DOUBLE_LANE,
}

/** There are no draws in GoA2 (rulebook). */
@Serializable
enum class WinCondition {
    @SerialName("throne")
    THRONE,

    @SerialName("last_push")
    LAST_PUSH,

    @SerialName("life_counters")
    LIFE_COUNTERS,

    @SerialName("other")
    OTHER,
}

@Serializable
enum class MatchStatus {
    /** Created from a draft or setup; not yet finished. Ignored by ratings. */
    @SerialName("pending")
    PENDING,

    @SerialName("completed")
    COMPLETED,

    /** Abandoned before a result; kept for the record, ignored by ratings. */
    @SerialName("abandoned")
    ABANDONED,
}

/**
 * One hero seat in a match. A player who **double-pilots** (controls two heroes, e.g. to even
 * out an odd player count) appears in two participants with the same [playerId].
 * [handicap] marks a hero playing with its Handicap card (larger team in odd counts).
 */
@Serializable
data class Participant(
    val playerId: String,
    val heroId: String?,
    val team: Team,
    val handicap: Boolean = false,
)

/**
 * The match log is the source of truth: every rating and stat is derived by replaying
 * COMPLETED matches ordered by ([playedAt], [createdAt]). Times are epoch milliseconds.
 */
@Serializable
data class Match(
    val id: String,
    val createdAt: Long,
    val playedAt: Long,
    val status: MatchStatus,
    val mode: GameMode,
    val map: String? = null,
    val winner: Team? = null,
    val winCondition: WinCondition? = null,
    val durationMin: Int? = null,
    val notes: String = "",
    val draftId: String? = null,
    val participants: List<Participant>,
) {
    fun team(team: Team): List<Participant> = participants.filter { it.team == team }

    val playerIds: Set<String> get() = participants.mapTo(linkedSetOf()) { it.playerId }
}
