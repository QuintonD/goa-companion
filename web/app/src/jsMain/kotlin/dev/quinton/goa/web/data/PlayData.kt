package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.web.play.data.EngineClient
import dev.quinton.goa.web.play.data.WebBoardMapRepository
import dev.quinton.goa.web.play.data.WebBoardRepository
import dev.quinton.goa.web.play.data.WebGameSaveRepository
import dev.quinton.goa.web.play.data.WebPuzzleProgressRepository
import dev.quinton.goa.web.storage.scoped

/**
 * W4 play (feature/web-play): the board maps, saved boards, the game against the computer in
 * progress and puzzle progress (this browser only, never synced, as on the phone), and the bridge
 * to the engine's Web Workers. Owned by W4: only W4 edits this file ([WebServices]).
 */
class PlayData(services: WebServices) {
    private val store = services.store.scoped("play/")

    val boardMaps: BoardMapRepository = WebBoardMapRepository()

    val boards: BoardRepository = WebBoardRepository(store)

    val gameSave: GameSaveRepository = WebGameSaveRepository(store)

    val puzzleProgress: PuzzleProgressRepository = WebPuzzleProgressRepository(store)

    /** The engine worker: the game in progress, the computer players and the puzzles. */
    val engine: EngineClient = EngineClient()

    /**
     * The coach's own worker, so its look at the person's question never holds up the game (on the
     * phone it runs on another thread); stopped and started afresh when a look is out of date.
     */
    val coach: EngineClient = EngineClient()
}
