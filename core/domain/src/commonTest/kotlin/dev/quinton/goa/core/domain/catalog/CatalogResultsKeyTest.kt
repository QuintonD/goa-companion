package dev.quinton.goa.core.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The result list goes back to the top when what it shows changes, on the phone and on the web alike
 * (the web keyed it on the raw text and jumped to the top on a change of case).
 */
class CatalogResultsKeyTest {
    private val none = CatalogFilter()

    @Test
    fun caseAndPunctuationThatFindTheSameKeepThePlace() {
        assertEquals(
            catalogResultsKey("Arien", none, CatalogSort.NAME),
            catalogResultsKey("  arien ", none, CatalogSort.NAME),
        )
    }

    @Test
    fun anotherSearchFilterOrSortStartsAtTheTop() {
        val key = catalogResultsKey("arien", none, CatalogSort.NAME)
        assertNotEquals(key, catalogResultsKey("brogan", none, CatalogSort.NAME))
        assertNotEquals(key, catalogResultsKey("arien", CatalogFilter(complexities = setOf(1)), CatalogSort.NAME))
        assertNotEquals(key, catalogResultsKey("arien", none, CatalogSort.ELO))
    }
}
