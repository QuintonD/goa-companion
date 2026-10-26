package dev.quinton.goa.web.play.data

import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.data.board.BoardImport
import dev.quinton.goa.core.data.board.BoardJson
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.GameJson
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.data.puzzle.PuzzleProgressJson
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.web.data.Assets
import dev.quinton.goa.web.storage.KeyValueStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The bundled board maps (`catalog/maps/`, the Android app's files) parsed by the phone's strict
 * loader, in the index's order (the core single-lane map first). Like the catalog, a fetch can fail
 * offline before the files were cached: the flow throws and the screen offers Try again.
 */
class WebBoardMapRepository(
    private val read: suspend (path: String) -> String = Assets::text,
) : BoardMapRepository {
    private val lock = Mutex()
    private var cached: List<BoardMap>? = null

    override val maps: Flow<List<BoardMap>> = flow { emit(get()) }

    suspend fun get(): List<BoardMap> = cached ?: lock.withLock {
        cached ?: coroutineScope {
            CatalogFiles.parseMapIndex(read(CatalogFiles.MAPS_INDEX_PATH))
                .map { id -> async { CatalogFiles.parseMap(id, read(CatalogFiles.mapPath(id))) } }
                .awaitAll()
        }.also { cached = it }
    }
}

/**
 * Board-sandbox boards (ADR-033) in IndexedDB, one JSON document per board ([BoardJson], the phone's
 * format) under `boards/<id>`. Local only, never synced, as on the phone. The documents are read once
 * and then kept in memory; every write goes to the store and to the flows at once, so a board open
 * on screen follows a rename, a deletion or an import (a deletion closes it).
 */
class WebBoardRepository(private val store: KeyValueStore) : BoardRepository {
    private val documents = MutableStateFlow<Map<String, String>?>(null)
    private val lock = Mutex()

    private suspend fun loaded(): Map<String, String> = documents.value ?: lock.withLock {
        documents.value ?: store.keys(PREFIX).associate { key ->
            key.removePrefix(PREFIX) to store.get(key).orEmpty()
        }.also { documents.value = it }
    }

    private val all: Flow<Map<String, String>> = documents.onStart { loaded() }.filterNotNull()

    override val boards: Flow<List<SavedBoard>> = all.distinctUntilChanged().map { docs ->
        docs.mapNotNull { (id, json) -> BoardJson.decode(json)?.takeIf { it.id == id } }
            .sortedWith(compareByDescending<SavedBoard> { it.updatedAt }.thenBy { it.id })
    }

    override fun observe(id: String): Flow<SavedBoard?> = all.map { it[id] }.distinctUntilChanged()
        .map { json -> json?.let(BoardJson::decode)?.takeIf { it.id == id } }

    override suspend fun upsert(board: SavedBoard) {
        val json = BoardJson.encode(board)
        loaded()
        lock.withLock {
            store.put(PREFIX + board.id, json)
            documents.value = documents.value.orEmpty() + (board.id to json)
        }
    }

    override suspend fun delete(id: String) {
        loaded()
        lock.withLock {
            store.delete(PREFIX + id)
            documents.value = documents.value.orEmpty() - id
        }
    }

    override suspend fun import(boards: List<SavedBoard>, replace: Boolean) {
        val current = loaded()
        lock.withLock {
            val next = BoardImport.merge(current, BoardImport.latest(boards), replace)
            (current.keys - next.keys).forEach { store.delete(PREFIX + it) }
            next.forEach { (id, json) -> if (current[id] != json) store.put(PREFIX + id, json) }
            documents.value = next
        }
    }

    private companion object {
        const val PREFIX = "boards/"
    }
}

/**
 * The game against the computer in progress ([GameJson], the phone's format) under `game`: local
 * only, not synced. A document this build can't read is [StoredGame.Unreadable] (offered for deletion).
 */
class WebGameSaveRepository(private val store: KeyValueStore) : GameSaveRepository {
    private val stored = MutableStateFlow<StoredGame?>(null)

    override val current: Flow<StoredGame> = stored.onStart {
        if (stored.value == null) stored.value = store.get(KEY)?.let(GameJson::decode) ?: StoredGame.None
    }.filterNotNull()

    override suspend fun save(game: GameInProgress) {
        store.put(KEY, GameJson.encode(game))
        stored.value = StoredGame.Saved(game)
    }

    override suspend fun clear() {
        store.delete(KEY)
        stored.value = StoredGame.None
    }

    private companion object {
        const val KEY = "game"
    }
}

/**
 * Puzzle progress by puzzle id ([PuzzleProgressJson], the phone's format) under `puzzle-progress`:
 * this browser only, as on the phone.
 */
class WebPuzzleProgressRepository(private val store: KeyValueStore) : PuzzleProgressRepository {
    private val stored = MutableStateFlow<Map<String, PuzzleProgress>?>(null)
    private val lock = Mutex()

    private suspend fun loaded(): Map<String, PuzzleProgress> = stored.value ?: lock.withLock {
        stored.value ?: PuzzleProgressJson.decode(store.get(KEY)).also { stored.value = it }
    }

    override val progress: Flow<Map<String, PuzzleProgress>> = stored.onStart { loaded() }.filterNotNull()

    override suspend fun update(puzzleId: String, transform: (PuzzleProgress) -> PuzzleProgress) {
        loaded()
        lock.withLock {
            val all = stored.value.orEmpty()
            val next = all + (puzzleId to transform(all[puzzleId] ?: PuzzleProgress()))
            store.put(KEY, PuzzleProgressJson.encode(next))
            stored.value = next
        }
    }

    override suspend fun reset() {
        lock.withLock {
            store.delete(KEY)
            stored.value = emptyMap()
        }
    }

    private companion object {
        const val KEY = "puzzle-progress"
    }
}
