package dev.quinton.goa.core.data.game

import dev.quinton.goa.core.game.SavedGame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How long each computer action stays on screen before the next one (PRODUCT §9 "Pacing"). */
@Serializable
enum class AiPace(val millis: Long) {
    @SerialName("slow")
    SLOW(2_600L),

    @SerialName("normal")
    NORMAL(1_500L),

    @SerialName("fast")
    FAST(650L),
}

/**
 * A game against the computer in progress (ADR-035): the engine's [SavedGame] plus what only the
 * app tracks. Written after every answer; local only, not synced, not in the backup file.
 */
@Serializable
data class GameInProgress(
    val game: SavedGame,
    /** Take backs used so far: counted in the game-over summary. */
    val takeBacks: Int = 0,
    /** Coach nudges shown so far, and how many of them the player followed (Rethink). */
    val nudges: Int = 0,
    val rethinks: Int = 0,
    val coach: Boolean = true,
    val pace: AiPace = AiPace.NORMAL,
    val startedAt: Long = 0,
)

/** What is stored: nothing, a game, or a document this build can't read (offered for deletion). */
sealed interface StoredGame {
    data object None : StoredGame

    data class Saved(val game: GameInProgress) : StoredGame

    /** Corrupt, or written by a build this one can't read. Never a crash: the app offers to delete it. */
    data object Unreadable : StoredGame
}
