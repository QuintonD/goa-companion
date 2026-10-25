package dev.quinton.goa.web.data.stub

import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.SavedBoard
import kotlinx.coroutines.flow.Flow

// W4 play (feature/web-play) replaces these in PlayData and deletes this file.

private const val W4 = "W4"

object W4BoardMapStub : BoardMapRepository {
    override val maps: Flow<List<BoardMap>> get() = notYet(W4, "BoardMapRepository")
}

object W4BoardStub : BoardRepository {
    override val boards: Flow<List<SavedBoard>> get() = notYet(W4, "BoardRepository")

    override fun observe(id: String): Flow<SavedBoard?> = notYet(W4, "BoardRepository")

    override suspend fun upsert(board: SavedBoard) = notYet(W4, "BoardRepository")

    override suspend fun delete(id: String) = notYet(W4, "BoardRepository")

    override suspend fun import(boards: List<SavedBoard>, replace: Boolean) = notYet(W4, "BoardRepository")
}

object W4GameSaveStub : GameSaveRepository {
    override val current: Flow<StoredGame> get() = notYet(W4, "GameSaveRepository")

    override suspend fun save(game: GameInProgress) = notYet(W4, "GameSaveRepository")

    override suspend fun clear() = notYet(W4, "GameSaveRepository")
}

object W4PuzzleProgressStub : PuzzleProgressRepository {
    override val progress: Flow<Map<String, PuzzleProgress>> get() = notYet(W4, "PuzzleProgressRepository")

    override suspend fun update(puzzleId: String, transform: (PuzzleProgress) -> PuzzleProgress) =
        notYet(W4, "PuzzleProgressRepository")

    override suspend fun reset() = notYet(W4, "PuzzleProgressRepository")
}
