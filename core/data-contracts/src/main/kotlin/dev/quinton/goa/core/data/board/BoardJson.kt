package dev.quinton.goa.core.data.board

import dev.quinton.goa.core.model.board.SavedBoard
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The stored board documents (the phone's DataStore, the web app's IndexedDB). Lenient both ways,
 * so older and newer builds read each other's.
 */
object BoardJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(board: SavedBoard): String = json.encodeToString(SavedBoard.serializer(), board)

    /** The board, or null when the document can't be read (that board alone is lost). */
    fun decode(stored: String): SavedBoard? = try {
        json.decodeFromString(SavedBoard.serializer(), stored)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** A backup's boards brought in (`BoardRepository.import`): the rule both platforms store by. */
object BoardImport {
    /** One board per id: the copy changed last. */
    fun latest(boards: List<SavedBoard>): Map<String, SavedBoard> =
        boards.groupBy { it.id }.mapValues { (_, copies) -> copies.maxBy { it.updatedAt } }

    /**
     * The stored documents (id to JSON) after importing [incoming]: Replace = exactly [incoming];
     * Merge = each id keeps the later `updatedAt` (a document that can't be read loses to the import).
     */
    fun merge(
        current: Map<String, String>,
        incoming: Map<String, SavedBoard>,
        replace: Boolean,
        decode: (String) -> SavedBoard? = BoardJson::decode,
    ): Map<String, String> = if (replace) {
        incoming.mapValues { (_, board) -> BoardJson.encode(board) }
    } else {
        val kept = current.toMutableMap()
        incoming.forEach { (id, board) ->
            val existing = current[id]?.let(decode)
            if (existing == null || board.updatedAt > existing.updatedAt) kept[id] = BoardJson.encode(board)
        }
        kept
    }
}
