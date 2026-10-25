package dev.quinton.goa.core.domain.parity

import dev.quinton.goa.core.domain.catalog.CatalogBrowser
import dev.quinton.goa.core.domain.catalog.CatalogFilter
import dev.quinton.goa.core.domain.catalog.CatalogSearchIndex
import dev.quinton.goa.core.domain.catalog.CatalogSort
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.catalogResults
import dev.quinton.goa.core.domain.catalog.kindLabel
import dev.quinton.goa.core.domain.catalog.matchingParagraph
import dev.quinton.goa.core.domain.catalog.spokenSummary
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hero catalog's search, filters and deck layout give the browser the phone's answers
 * (ADR-043): the same heroes and cards, in the same order, with the same words. Run on the JVM
 * and on Node over the shipped catalog.
 */
class CatalogParityTest {
    private val catalog: Catalog by lazy { CatalogFiles.load { readRepoFile("app/src/main/assets/$it") } }
    private val index by lazy { CatalogSearchIndex(catalog.heroes) }

    @Test
    fun searchFoldsAccentsAndQuotesLikeThePhone() {
        // NFD + dropping combining marks needs Unicode regexes in JavaScript.
        assertEquals("nandu cafe it's", CatalogSearchIndex.normalize("Ñandú Café It’s"))
        assertEquals(listOf("tidal", "blast"), CatalogSearchIndex.tokenize("  Tidal   BLAST "))
    }

    @Test
    fun aNameWithoutItsSpaceStillFindsTheHero() {
        val spaced = catalog.heroes.first { it.name.any { c -> !c.isLetterOrDigit() } || it.name.length > 6 }
        val compact = spaced.name.filter { it.isLetterOrDigit() }.lowercase()
        assertTrue(index.search(compact).any { it.heroId == spaced.id }, "\"$compact\" finds ${spaced.name}")
    }

    @Test
    fun searchesFiltersAndDecksMatchTheJvm() {
        val queries = listOf(
            "", "arien", "fast travel", "ultimate", "brogan ultimate", "block", "coin", "tier ii", "handicap",
            "spell", "defeat a minion", "range", "push", "zz-no-such-word", "it's", "ranged attack",
        )
        val transcript = buildString {
            queries.forEach { query ->
                val heroes = CatalogBrowser.browse(catalog, index, query, CatalogFilter(), CatalogSort.NAME, emptySet())
                val cards = CatalogBrowser.cards(index, query, heroes)
                appendLine("q=$query heroes=${heroes.map { it.hero.id }} hints=${heroes.map { it.hit?.field }}")
                appendLine("  cards=${cards.size} ${cards.take(CARDS_SHOWN).map { "${it.card.id}/${it.quality}" }}")
                cards.take(SNIPPETS_SHOWN).forEach { appendLine("  snippet=${matchingParagraph(it.card.text, query)}") }
            }
            CatalogSort.entries.forEach { sort ->
                val owned = setOf(catalog.products.first().id)
                val results = catalogResults(catalog, index, owned, "", CatalogFilter(), sort)!!
                appendLine("sort=$sort owned ${results.heroes.map { it.hero.id }} of ${results.totalInScope}")
            }
            catalog.heroes.forEach { hero ->
                val deck = DeckLayout.of(hero)
                appendLine("${hero.id} ${deck.groups.map { "${it.kind}:${it.rows.map { r -> r.tier }}" }}")
                deck.orderedCards.forEach { card ->
                    appendLine("  ${card.kindLabel(hero.handicap?.id == card.id)} | ${card.spokenSummary()}")
                }
            }
        }
        assertTranscript(TRANSCRIPT_SHA256, transcript, "catalog")
    }

    private companion object {
        const val CARDS_SHOWN = 12
        const val SNIPPETS_SHOWN = 3

        /** What the JVM gives for the shipped catalog; update with the catalog (the test prints it). */
        const val TRANSCRIPT_SHA256 = "cb52ea6ecc7d050735360d6a8bdbf59e1afb7d63ca29330019ddb4fc9cc9927d"
    }
}
