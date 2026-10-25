package dev.quinton.goa.web.data

import dev.quinton.goa.web.draft.WebDraftSetupPrefill
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class WebServicesTest {
    @Test
    fun theAppStartsWhileStreamsAreStillStubs() {
        // Created in main() before anything renders: a stub must never throw here.
        val services = WebServices(MemoryKeyValueStore())

        assertIs<WebCatalogRepository>(services.content.catalog)
        assertIs<WebDraftSetupPrefill>(services.table.draftSetupPrefill)
    }

    @Test
    fun aStubSaysWhichStreamOwnsIt() = runTest {
        val services = WebServices(MemoryKeyValueStore())

        val failures = listOf(
            "W2" to assertFailsWith<NotImplementedError> { services.group.matches.observeMatches() },
            "W4" to assertFailsWith<NotImplementedError> { services.play.boardMaps.maps },
        )

        failures.forEach { (stream, error) ->
            assertTrue(error.message.orEmpty().startsWith("$stream: "), "${error.message} names $stream")
        }
    }
}
