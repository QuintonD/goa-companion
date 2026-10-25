package dev.quinton.goa.core.data.puzzle

import kotlinx.serialization.Serializable

/**
 * How one puzzle has gone on this phone (PRODUCT §10). Local only: not synced, not in the backup
 * file. Keyed by the puzzle's permanent id (`PuzzleInfo.id`).
 */
@Serializable
data class PuzzleProgress(
    val solved: Boolean = false,
    /** When it was first solved (epoch ms), else null. */
    val solvedAt: Long? = null,
    /** Tries: each start, restart or "Try again" in which a move was made. */
    val attempts: Int = 0,
    /** The most nudges shown in any try (each one stronger than the last). */
    val nudgesUsed: Int = 0,
    /** "Show solution" was opened before it was solved. */
    val solutionShown: Boolean = false,
    /** The last move made in it (epoch ms), 0 = never. */
    val lastPlayedAt: Long = 0,
) {
    val tried: Boolean get() = attempts > 0 || solved
}
