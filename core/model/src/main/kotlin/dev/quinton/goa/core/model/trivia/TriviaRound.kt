package dev.quinton.goa.core.model.trivia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What kind of trivia round was played (ADR-032). */
@Serializable
enum class TriviaRoundKind {
    /** The fixed Challenge quiz whose scores compare across players (Challenge board). */
    @SerialName("challenge")
    CHALLENGE,

    /** Any other round the player set up (counts on the Practice board only). */
    @SerialName("practice")
    PRACTICE,
}

/**
 * One finished trivia round, played as a player (ADR-032). The leaderboard is derived from these;
 * like the match log they are synced with the group and kept in the backup file. Rounds are never
 * edited after they are written; a deleted one leaves a tombstone
 * ([dev.quinton.goa.core.model.sync.DeletionKind.TRIVIA_ROUND]).
 *
 * A Challenge counts only when all its questions were answered ([questions] ==
 * [length]); an unfinished Challenge is not written at all.
 */
@Serializable
data class TriviaRound(
    val id: String,
    val playerId: String,
    val kind: TriviaRoundKind,
    /** When the round started (epoch ms). */
    val playedAt: Long,
    /** Questions the round was set up with; 0 = endless. */
    val length: Int,
    /** Questions answered. */
    val questions: Int,
    /** Right answers. */
    val correct: Int,
    val bestStreak: Int,
    /** Summed time from each question appearing to its answer (reading the card scan not included). */
    val answerMillis: Long,
    /** Answered from every possible number ("Recall") instead of four choices. */
    val recall: Boolean = false,
    /** Heroes the round's questions were drawn from. */
    val heroCount: Int,
    /** `dataVersion` of the catalog the questions came from. */
    val catalogVersion: Int,
    /** Last local write (ADR-025). Rounds are immutable, so this is normally [playedAt]'s save time. */
    val updatedAt: Long = playedAt,
)
