package dev.quinton.goa.web.data

import dev.quinton.goa.core.model.catalog.CatalogFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException

/** The site serves the Android app's assets (`app/src/main/assets`) under the same paths. */
private fun shipped(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("process.getBuiltinModule('fs')") // the tests run as ES modules (es2015 target): no require
    return fs.readFileSync("$root/app/src/main/assets/$path", "utf8") as String
}

class WebCatalogRepositoryTest {
    @Test
    fun theWebReadsTheShippedCatalogExactlyAsThePhoneDoes() = runTest {
        val reads = mutableListOf<String>()
        val repository = WebCatalogRepository { path ->
            reads += path
            shipped(path)
        }

        val catalog = repository.catalog.first()

        // Same strict loader, same result: every hero, in the index's order.
        assertEquals(CatalogFiles.load(::shipped), catalog)
        assertEquals(CatalogFiles.parseIndex(shipped(CatalogFiles.INDEX_PATH)).heroes, catalog.heroes.map { it.id })
        assertTrue(catalog.heroes.size > 30, "the shipped catalog has every hero")
        // Each file is fetched once (the heroes in parallel).
        assertEquals(reads.size, reads.toSet().size)
    }

    @Test
    fun offlineBeforeTheFilesWereCachedFailsAndTryAgainFetchesAgain() = runTest {
        var online = false
        val repository = WebCatalogRepository { path ->
            if (!online && path != CatalogFiles.INDEX_PATH) throw AssetUnavailableException(path, "offline")
            shipped(path)
        }

        assertFailsWith<AssetUnavailableException> { repository.get() }
        online = true
        assertTrue(repository.get().heroes.isNotEmpty())
    }

    @Test
    fun theCatalogIsFetchedOnceForEveryScreen() = runTest {
        var indexReads = 0
        val repository = WebCatalogRepository { path ->
            if (path == CatalogFiles.INDEX_PATH) indexReads++
            shipped(path)
        }

        val first = repository.catalog.first()
        val second = repository.catalog.first()

        assertSame(first, second)
        assertEquals(1, indexReads)
    }

    @Test
    fun aHeroFileTheAppDoesNotUnderstandFailsLoudly() = runTest {
        // The importer and the app disagree on the schema: never show a half-read hero.
        val firstHero = CatalogFiles.heroPath(CatalogFiles.parseIndex(shipped(CatalogFiles.INDEX_PATH)).heroes.first())
        val repository = WebCatalogRepository { path ->
            val text = shipped(path)
            if (path == firstHero) text.replaceFirst("{", "{\"newField\": 1, ") else text
        }

        assertFailsWith<SerializationException> { repository.get() }
    }
}
