package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.web.data.stub.W4BoardMapStub
import dev.quinton.goa.web.data.stub.W4BoardStub
import dev.quinton.goa.web.data.stub.W4GameSaveStub
import dev.quinton.goa.web.data.stub.W4PuzzleProgressStub

/**
 * W4 play (feature/web-play): the board maps, saved boards, the game against the computer in
 * progress and puzzle progress (this browser only), and the bridge to the engine's Web Worker.
 * Owned by W4: only W4 edits this file ([WebServices]).
 */
@Suppress("UnusedPrivateProperty", "unused")
class PlayData(private val services: WebServices) {
    val boardMaps: BoardMapRepository = W4BoardMapStub

    val boards: BoardRepository = W4BoardStub

    val gameSave: GameSaveRepository = W4GameSaveStub

    val puzzleProgress: PuzzleProgressRepository = W4PuzzleProgressStub
}
