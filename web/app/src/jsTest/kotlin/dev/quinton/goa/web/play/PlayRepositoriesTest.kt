package dev.quinton.goa.web.play

import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.web.play.data.WebBoardRepository
import dev.quinton.goa.web.play.data.WebGameSaveRepository
import dev.quinton.goa.web.play.data.WebPuzzleProgressRepository
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import dev.quinton.goa.web.storage.scoped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Boards, the game save and puzzle progress in the browser's store, in the phone's formats and rules. */
class PlayRepositoriesTest {
    private fun board(id: String, updatedAt: Long, name: String = id) = SavedBoard(
        id = id,
        name = name,
        lineup = BoardLineup(listOf("arien"), listOf("wasp")),
        state = BoardState("forgotten-island"),
        createdAt = 1,
        updatedAt = updatedAt,
    )

    @Test
    fun boardsAreKeptNewestFirstAndSurviveAReload() = runTest {
        val store = MemoryKeyValueStore()
        val boards = WebBoardRepository(store.scoped("play/"))
        boards.upsert(board("a", 10))
        boards.upsert(board("b", 20))
        assertEquals(listOf("b", "a"), boards.boards.first().map { it.id })

        val reloaded = WebBoardRepository(store.scoped("play/"))
        assertEquals(listOf("b", "a"), reloaded.boards.first().map { it.id })
        assertEquals("a", reloaded.observe("a").first()?.name)
    }

    @Test
    fun aDeletedBoardIsGoneForAnOpenScreenToo() = runTest {
        val boards = WebBoardRepository(MemoryKeyValueStore())
        boards.upsert(board("a", 10))
        boards.delete("a")
        assertNull(boards.observe("a").first())
        assertEquals(emptyList(), boards.boards.first())
    }

    @Test
    fun importMergesByTheLaterChangeOrReplacesEverything() = runTest {
        val boards = WebBoardRepository(MemoryKeyValueStore())
        boards.upsert(board("a", 10, "mine"))
        boards.upsert(board("b", 30, "mine"))
        boards.import(
            listOf(board("a", 20, "backup"), board("b", 25, "backup"), board("c", 5, "backup")),
            replace = false,
        )
        assertEquals(
            mapOf("a" to "backup", "b" to "mine", "c" to "backup"),
            boards.boards.first().associate { it.id to it.name },
        )
        boards.import(listOf(board("c", 1, "only")), replace = true)
        assertEquals(listOf("only"), boards.boards.first().map { it.name })
    }

    @Test
    fun aGameSaveThatCantBeReadIsOfferedForDeletionNotACrash() = runTest {
        val store = MemoryKeyValueStore()
        store.put("game", "{not json")
        val saves = WebGameSaveRepository(store)
        assertEquals(StoredGame.Unreadable, saves.current.first())
        saves.clear()
        assertEquals(StoredGame.None, saves.current.first())
    }

    @Test
    fun puzzleProgressIsUpdatedAndReset() = runTest {
        val store = MemoryKeyValueStore()
        val progress = WebPuzzleProgressRepository(store)
        progress.update("p01") { it.copy(attempts = it.attempts + 1) }
        progress.update("p01") { it.copy(solved = true) }
        assertEquals(
            PuzzleProgress(solved = true, attempts = 1),
            WebPuzzleProgressRepository(store).progress.first()["p01"],
        )
        progress.reset()
        assertEquals(emptyMap(), WebPuzzleProgressRepository(store).progress.first())
    }
}
