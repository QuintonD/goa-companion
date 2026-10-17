package dev.quinton.goa.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

/*
 * Room schema v1 (exported to core/database/schemas). Enums are stored as their stable
 * serial names ("red", "completed", …) so the database and the JSON backup agree.
 * Players are referenced by id without a foreign key: players are archived, never deleted, and
 * an imported match may name a player that is missing (shown as "Unknown player").
 */

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    @ColumnInfo(name = "avatar_id") val avatarId: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val archived: Boolean,
)

@Entity(
    tableName = "matches",
    indices = [Index(value = ["played_at"])],
)
data class MatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    val status: String,
    val mode: String,
    val map: String?,
    val winner: String?,
    @ColumnInfo(name = "win_condition") val winCondition: String?,
    @ColumnInfo(name = "duration_min") val durationMin: Int?,
    val notes: String,
    @ColumnInfo(name = "draft_id") val draftId: String?,
    /** Team on the Tie Breaker coin at setup ("red"/"blue"); added in schema v2. */
    @ColumnInfo(name = "tie_breaker") val tieBreaker: String? = null,
)

/** One hero seat. A double pilot owns two rows with the same [playerId]. */
@Entity(
    tableName = "participants",
    primaryKeys = ["match_id", "seat_index"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["player_id"]), Index(value = ["hero_id"])],
)
data class ParticipantEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "seat_index") val seatIndex: Int,
    @ColumnInfo(name = "player_id") val playerId: String,
    @ColumnInfo(name = "hero_id") val heroId: String?,
    val team: String,
    val handicap: Boolean,
)

data class MatchWithParticipants(
    @Embedded val match: MatchEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["match_id"])
    val participants: List<ParticipantEntity>,
)

@Entity(tableName = "draft_records")
data class DraftRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "method_id") val methodId: String,
    @ColumnInfo(name = "method_kind") val methodKind: String,
    /** Hero ids as a JSON array. */
    val pool: String,
    @ColumnInfo(name = "team_a") val teamA: String,
    @ColumnInfo(name = "match_id") val matchId: String?,
)

@Entity(
    tableName = "draft_steps",
    primaryKeys = ["draft_id", "step_index"],
    foreignKeys = [
        ForeignKey(
            entity = DraftRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["draft_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DraftStepEntity(
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    val team: String,
    val action: String,
    @ColumnInfo(name = "hero_id") val heroId: String,
)

data class DraftWithSteps(
    @Embedded val draft: DraftRecordEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["draft_id"])
    val steps: List<DraftStepEntity>,
)
