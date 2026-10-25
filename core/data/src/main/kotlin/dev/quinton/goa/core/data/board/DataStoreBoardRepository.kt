package dev.quinton.goa.core.data.board

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.datastore.board.BoardStore
import dev.quinton.goa.core.model.board.SavedBoard
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Board-sandbox boards (ADR-033) in `datastore/boards`, one JSON document per board. Local only:
 * never synced; the backup file reads and writes them through [BoardRepository].
 *
 * Reading never fails: a document that can't be decoded loses only that board. Decoding and
 * encoding run on [computeDispatcher] (a board with many pieces is a few KB, a list of them more),
 * never on the caller's (main) thread; unchanged documents are not decoded again.
 */
@Singleton
internal class DataStoreBoardRepository internal constructor(
    private val store: BoardStore,
    private val computeDispatcher: CoroutineDispatcher,
) : BoardRepository {
    @Inject
    constructor(store: BoardStore) : this(store, Dispatchers.Default)

    /** The last decoded board per document text: an autosave of one board re-decodes only that one. */
    private val decoded = object : LinkedHashMap<String, SavedBoard?>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SavedBoard?>?) = size > CACHE_SIZE
    }

    override val boards: Flow<List<SavedBoard>> = store.documents
        .distinctUntilChanged()
        .map { documents ->
            documents.mapNotNull { (id, json) -> decode(json)?.takeIf { it.id == id } }
                .sortedWith(compareByDescending<SavedBoard> { it.updatedAt }.thenBy { it.id })
        }
        .flowOn(computeDispatcher)

    override fun observe(id: String): Flow<SavedBoard?> = store.documents
        .map { it[id] }
        .distinctUntilChanged()
        .map { json -> json?.let(::decode)?.takeIf { it.id == id } }
        .flowOn(computeDispatcher)

    override suspend fun upsert(board: SavedBoard) {
        val json = withContext(computeDispatcher) { BoardJson.encode(board) }
        store.write(board.id, json)
    }

    override suspend fun delete(id: String) = store.delete(id)

    override suspend fun import(boards: List<SavedBoard>, replace: Boolean) {
        val incoming = withContext(computeDispatcher) { BoardImport.latest(boards) }
        withContext(computeDispatcher) {
            store.update { current -> BoardImport.merge(current, incoming, replace, ::decode) }
        }
    }

    private fun decode(json: String): SavedBoard? = synchronized(decoded) {
        if (decoded.containsKey(json)) return decoded[json]
        BoardJson.decode(json).also { decoded[json] = it }
    }

    private companion object {
        const val CACHE_SIZE = 64
        const val LOAD_FACTOR = 0.75f
    }
}

/** Board sandbox bindings (ADR-033; owner: board). */
@Module
@InstallIn(SingletonComponent::class)
internal interface BoardDataModule {
    @Binds fun maps(impl: AssetBoardMapRepository): BoardMapRepository

    @Binds fun boards(impl: DataStoreBoardRepository): BoardRepository
}
