package dev.quinton.goa.core.model.draft

import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The persisted outcome of a finished draft — enough for ban-rate stats and to rebuild the
 * pending match. The live session state machine lives in core:domain (DraftEngine).
 */
@Serializable
data class DraftRecord(
    val id: String,
    val createdAt: Long,
    val methodId: String,
    val methodKind: DraftMethodKind,
    val pool: List<String>,
    /** Team on the Tie Breaker coin ("Team A"). */
    val teamA: Team,
    val steps: List<DraftStepRecord>,
    val matchId: String? = null,
)

@Serializable
data class DraftStepRecord(
    val team: Team,
    val action: DraftAction,
    val heroId: String,
)

@Serializable
enum class DraftAction {
    @SerialName("pick")
    PICK,

    @SerialName("ban")
    BAN,
}

@Serializable
enum class DraftMethodKind {
    @SerialName("all_random")
    ALL_RANDOM,

    @SerialName("all_pick")
    ALL_PICK,

    @SerialName("single_draft")
    SINGLE_DRAFT,

    @SerialName("random_draft")
    RANDOM_DRAFT,

    @SerialName("pick_and_ban")
    PICK_AND_BAN,

    /** User-defined sequence (e.g. the group's house draft). */
    @SerialName("custom")
    CUSTOM,
}
