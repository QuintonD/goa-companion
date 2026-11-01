package dev.quinton.goa.web.rules

import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.web.data.AssetUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The site serves the Android app's assets (`app/src/main/assets`) under the same paths. */
private fun shippedAsset(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("process.getBuiltinModule('fs')") // the tests run as ES modules (es2015 target): no require
    return fs.readFileSync("$root/app/src/main/assets/$path", "utf8") as String
}

class RulesWebTest {
    /** The boxes are read off the printed pages by opening words: a reworded reference must not lose them silently. */
    @Test
    fun everyBoxedPassageIsInTheShippedReference() {
        val reference = RulesReference.parse(shippedAsset(RulesReference.PATH))
        val excerpts = reference.topics.flatMap { it.excerpts } + reference.ladder.allExcerpts
        RulesBoxes.entries.forEach { entry ->
            assertTrue(
                excerpts.any {
                    RulesBoxes.of(it) == entry.box && it.page == entry.page && it.text.contains(entry.opening)
                },
                "p.${entry.page} \"${entry.opening}\" is not in the reference",
            )
        }
        val lane = reference.topics.first { it.id == "pushing-the-lane" }.excerpts
        assertEquals(listOf(null, null, RulesBox.BLUE, RulesBox.BLUE), lane.map(RulesBoxes::of))
    }

    @Test
    fun theWebReadsTheShippedRulesReferenceOnceWithThePhonesParser() = runTest {
        var reads = 0
        val source = AssetRulesReferenceSource { path ->
            reads++
            shippedAsset(path)
        }

        val reference = source.load()

        assertEquals(RulesReference.parse(shippedAsset(RulesReference.PATH)), reference)
        assertEquals(8, reference.ladder.levels.size)
        assertSame(reference, source.load())
        assertEquals(1, reads)
    }

    @Test
    fun offlineBeforeTheFileWasCachedFailsAndTryAgainFetchesAgain() = runTest {
        var online = false
        val source = AssetRulesReferenceSource { path ->
            if (!online) throw AssetUnavailableException(path, "offline")
            shippedAsset(path)
        }

        assertFailsWith<AssetUnavailableException> { source.load() }
        online = true
        assertTrue(source.load().topics.isNotEmpty())
    }

    @Test
    fun theLadderQuotesTheCardsOfTheMatchsHeroesOrElseEveryHeros() = runTest {
        val reference = RulesReference.parse(shippedAsset(RulesReference.PATH))
        val catalog = CatalogFiles.load(::shippedAsset)

        val everyone = Ladder.of(reference.ladder, catalog, emptyList())
        assertFalse(everyone.matchHeroesOnly)
        // PRODUCT.md names these as the cards that change Life counters or assist coins.
        val names = everyone.exceptions.map { "${it.heroName} · ${it.cardName}" }
        listOf("Bain · Dead or Alive", "Gydion · Energy Drain").forEach { assertTrue(it in names, "$it in $names") }

        val bain = catalog.heroes.first { it.name == "Bain" }
        val match = Ladder.of(reference.ladder, catalog, listOf(bain.id, bain.id, "not-a-hero"))
        assertTrue(match.matchHeroesOnly)
        assertTrue(match.exceptions.all { it.heroName == "Bain" })
        assertTrue(match.exceptions.isNotEmpty())
    }
}
