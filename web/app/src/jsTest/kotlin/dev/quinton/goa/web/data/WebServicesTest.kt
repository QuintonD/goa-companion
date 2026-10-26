package dev.quinton.goa.web.data

import dev.quinton.goa.web.draft.WebDraftSetupPrefill
import dev.quinton.goa.web.group.WebMatchRepository
import dev.quinton.goa.web.play.data.WebBoardMapRepository
import dev.quinton.goa.web.play.data.WebBoardRepository
import dev.quinton.goa.web.play.data.WebGameSaveRepository
import dev.quinton.goa.web.play.data.WebPuzzleProgressRepository
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertIs

class WebServicesTest {
    @Test
    fun theAppStartsWhileStreamsAreStillStubs() {
        // Created in main() before anything renders: a stub must never throw here.
        val services = WebServices(MemoryKeyValueStore())

        assertIs<WebCatalogRepository>(services.content.catalog)
        assertIs<WebDraftSetupPrefill>(services.table.draftSetupPrefill)
        assertIs<WebMatchRepository>(services.group.matches)
    }

    @Test
    fun everyStreamHasLandedNoStubIsLeft() {
        val services = WebServices(MemoryKeyValueStore())

        assertIs<WebBoardMapRepository>(services.play.boardMaps)
        assertIs<WebBoardRepository>(services.play.boards)
        assertIs<WebGameSaveRepository>(services.play.gameSave)
        assertIs<WebPuzzleProgressRepository>(services.play.puzzleProgress)
    }
}
