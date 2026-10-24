package dev.quinton.goa.core.data.game

import dev.quinton.goa.core.datastore.game.SavedGameStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Games against the computer in `datastore/saved_games`, one JSON document per game (the boards
 * repository's pattern). Reading never fails: a document that can't be decoded is listed as an
 * [UnreadableGame], so the app offers to delete it. Encoding and decoding run on
 * [computeDispatcher], never on the caller's (main) thread.
 */
@Singleton
internal class DataStoreSavedGameRepository internal constructor(
    private val store: SavedGameStore,
    private val computeDispatcher: CoroutineDispatcher,
) : SavedGameRepository {
    @Inject constructor(store: SavedGameStore) : this(store, Dispatchers.Default)

    override val games: Flow<List<GameEntry>> = store.documents
        .distinctUntilChanged()
        .map { documents ->
            val entries = documents.map { (id, json) -> entry(id, json) }
            val readable = entries.filterIsInstance<StoredGame>()
                .sortedWith(compareByDescending<StoredGame> { it.updatedAt }.thenBy { it.id })
            readable + entries.filterIsInstance<UnreadableGame>().sortedBy { it.id }
        }
        .flowOn(computeDispatcher)

    override fun observe(id: String): Flow<GameEntry?> = store.documents
        .map { it[id] }
        .distinctUntilChanged()
        .map { json -> json?.let { entry(id, it) } }
        .flowOn(computeDispatcher)

    private fun entry(id: String, json: String): GameEntry =
        GameJson.decode(json)?.takeIf { it.id == id } ?: UnreadableGame(id)

    override suspend fun save(game: StoredGame) {
        val json = withContext(computeDispatcher) { GameJson.encode(game) }
        store.write(game.id, json)
    }

    override suspend fun delete(id: String) = store.delete(id)
}

/** The stored game documents. Lenient, so a newer build's extra fields don't lose the game. */
internal object GameJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(game: StoredGame): String = json.encodeToString(StoredGame.serializer(), game)

    fun decode(stored: String): StoredGame? = try {
        json.decodeFromString(StoredGame.serializer(), stored)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
