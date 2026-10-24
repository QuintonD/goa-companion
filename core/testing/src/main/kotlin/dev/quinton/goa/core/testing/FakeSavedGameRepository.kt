package dev.quinton.goa.core.testing

import dev.quinton.goa.core.data.game.GameEntry
import dev.quinton.goa.core.data.game.SavedGameRepository
import dev.quinton.goa.core.data.game.StoredGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Saved games against the computer in memory. [writes] counts saves (autosave tests). */
class FakeSavedGameRepository(initial: List<GameEntry> = emptyList()) : SavedGameRepository {
    val state = MutableStateFlow(initial)
    var writes = 0
        private set

    override val games: Flow<List<GameEntry>> = state.map { entries ->
        entries.filterIsInstance<StoredGame>().sortedByDescending { it.updatedAt } +
            entries.filterNot { it is StoredGame }
    }

    override fun observe(id: String): Flow<GameEntry?> = state.map { entries -> entries.firstOrNull { it.id == id } }

    override suspend fun save(game: StoredGame) {
        writes++
        state.update { entries -> entries.filterNot { it.id == game.id } + game }
    }

    override suspend fun delete(id: String) = state.update { entries -> entries.filterNot { it.id == id } }

    fun stored(id: String): StoredGame? = state.value.firstOrNull { it.id == id } as? StoredGame
}
