package dev.quinton.goa.feature.catalog.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.feature.catalog.Fixtures
import dev.quinton.goa.feature.catalog.model.AttackStyle
import dev.quinton.goa.feature.catalog.model.CatalogBrowser
import dev.quinton.goa.feature.catalog.model.CatalogFilter
import dev.quinton.goa.feature.catalog.model.CatalogSearchIndex
import dev.quinton.goa.feature.catalog.model.CatalogSort
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * QA: typing a search with the list scrolled down kept the old scroll position (a lazy grid keeps
 * the first visible item's key in place), so the best result sat above the top of the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CatalogScrollTest {
    @get:Rule val compose = createComposeRule()

    private val catalog = Fixtures.largeCatalog(30)
    private val index = CatalogSearchIndex(catalog.heroes)

    private fun ready(query: String): CatalogUiState.Ready {
        val heroes = CatalogBrowser.browse(catalog, index, query, CatalogFilter(), CatalogSort.NAME, emptySet())
        return CatalogUiState.Ready(
            controls = CatalogControls(CatalogLayout.LIST, CatalogFilter(), CatalogSort.NAME),
            heroes = heroes,
            totalInScope = catalog.heroes.size,
            options = FilterOptions(emptyList(), emptyList(), catalog.products, AttackStyle.entries, false),
            cards = CatalogBrowser.cards(index, query, heroes),
        )
    }

    @Test
    fun `a new query shows the results from the top`() {
        var query by mutableStateOf("")
        compose.setContent {
            GoaTheme {
                CatalogScreen(
                    state = ready(query),
                    query = query,
                    onAction = {},
                    onHeroClick = {},
                    onSettingsClick = {},
                )
            }
        }
        val results =
            hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        compose.onNode(results).performScrollToIndex(25)
        val first = hasContentDescription("Test Hero 1 ", substring = true)
        compose.onNode(first).assertDoesNotExist()

        // Every hero still matches, in the same order: only the reset brings the first one back.
        query = "test"
        compose.waitForIdle()
        compose.onNode(first).assertIsDisplayed()
    }
}
