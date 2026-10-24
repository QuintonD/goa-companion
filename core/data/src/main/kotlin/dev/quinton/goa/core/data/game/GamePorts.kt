package dev.quinton.goa.core.data.game

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.model.match.Team
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Play against the computer (ADR-035, owner: board). The engine, the AI and the Coach are core:game;
 * these are the app-side ports the play screens use: saved games and the AI per level.
 */

/** How long each computer action stays on screen before the next one. */
@Serializable
enum class PlaySpeed {
    @SerialName("slow")
    SLOW,

    @SerialName("normal")
    NORMAL,

    @SerialName("fast")
    FAST,
}

/** The player's choices for one game that are not part of the rules (switchable in the game). */
@Serializable
data class PlayOptions(
    val coach: Boolean = true,
    val speed: PlaySpeed = PlaySpeed.NORMAL,
)

/** An entry in the saved games: a game that can be read, or one whose document can't. */
sealed interface GameEntry {
    val id: String
}

/**
 * A game against the computer as saved after every answer: the engine's [game] plus what the app
 * shows about it. [round]/[turn]/[finished]/[winner] are copies for the Boards list (the engine is
 * the truth). Local only: not synced, not in the backup file.
 */
@Serializable
data class StoredGame(
    override val id: String,
    val game: SavedGame,
    val options: PlayOptions = PlayOptions(),
    /** Times the player took a choice back (shown in the summary). */
    val takeBacks: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val round: Int = 1,
    val turn: Int = 1,
    val finished: Boolean = false,
    val winner: Team? = null,
) : GameEntry

/** A saved game whose document can't be read any more: offered for deletion, never a crash. */
data class UnreadableGame(override val id: String) : GameEntry

/** Saved games against the computer (owner: board): on this phone only. */
interface SavedGameRepository {
    /** Every saved game, most recently played first; unreadable ones last. */
    val games: Flow<List<GameEntry>>

    fun observe(id: String): Flow<GameEntry?>

    suspend fun save(game: StoredGame)

    suspend fun delete(id: String)
}

/**
 * The computer's players by level (core:game `ai/`). [seed] makes a game replayable: the same seed,
 * game and answers give the same choices.
 */
fun interface AiPlayers {
    fun create(level: AiLevel, seed: Long): AiPlayer
}
