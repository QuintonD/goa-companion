package dev.quinton.goa.feature.catalog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.catalog.AttackStyle
import dev.quinton.goa.core.domain.catalog.CatalogBrowser
import dev.quinton.goa.core.domain.catalog.CatalogFilter
import dev.quinton.goa.core.domain.catalog.CatalogSearchIndex
import dev.quinton.goa.core.domain.catalog.CatalogSort
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.FilterOptions
import dev.quinton.goa.core.domain.catalog.HeroLogSummary
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.ProductKind
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.BundledAssetImages
import dev.quinton.goa.core.testing.bundledAsset
import dev.quinton.goa.core.testing.bundledHero
import dev.quinton.goa.feature.catalog.detail.HeroDetailScreen
import dev.quinton.goa.feature.catalog.detail.HeroDetailUiState
import dev.quinton.goa.feature.catalog.detail.HeroPersonalStats
import dev.quinton.goa.feature.catalog.list.CatalogControls
import dev.quinton.goa.feature.catalog.list.CatalogLayout
import dev.quinton.goa.feature.catalog.list.CatalogScreen
import dev.quinton.goa.feature.catalog.list.CatalogUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Heroes grid and the hero page with the real heroes and their official art (ADR-053), as a
 * player sees them: the fake heroes of [CatalogScreenshotTest] have no art to judge the look by.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OfficialLookScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledAssetImages.install(ApplicationProvider.getApplicationContext())

    private val catalog: Catalog by lazy {
        // The whole bundled roster, as the Heroes tab opens on a phone ("32 heroes"), not a sample.
        val heroes = bundledAsset("catalog/heroes").list().orEmpty().sorted()
            .map { bundledHero(it.removeSuffix(".json")) }
        Catalog(
            dataVersion = 1,
            printing = 2,
            products = heroes.map { it.product }.distinct().map {
                Product(it, it.replaceFirstChar(Char::uppercase), ProductKind.HERO_PACK)
            },
            heroes = heroes,
        )
    }

    private fun ready(
        layout: CatalogLayout = CatalogLayout.GRID,
        query: String = "",
        catalog: Catalog = this.catalog,
    ): CatalogUiState.Ready {
        val index = CatalogSearchIndex(catalog.heroes)
        val heroes = CatalogBrowser.browse(catalog, index, query, CatalogFilter(), CatalogSort.NAME, emptySet())
        return CatalogUiState.Ready(
            controls = CatalogControls(layout, CatalogFilter(), CatalogSort.NAME),
            heroes = heroes,
            totalInScope = heroes.size,
            options = FilterOptions(emptyList(), emptyList(), catalog.products, AttackStyle.entries, false),
            cards = CatalogBrowser.cards(index, query, heroes),
        )
    }

    private fun detail(id: String): HeroDetailUiState.Ready {
        val hero = bundledHero(id)
        return HeroDetailUiState.Ready(
            hero = hero,
            productName = hero.product.replaceFirstChar(Char::uppercase),
            deck = DeckLayout.of(hero),
            stats = HeroPersonalStats(null, null, HeroLogSummary(0, 0, emptyList(), emptyList())),
        )
    }

    private fun shot(mode: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GoaTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledAssetImages.idle }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    @Composable
    private fun Grid(layout: CatalogLayout = CatalogLayout.GRID) =
        CatalogScreen(state = ready(layout), query = "", onAction = {}, onHeroClick = {}, onSettingsClick = {})

    @Composable
    private fun Page(id: String) = HeroDetailScreen(detail(id), onBack = {}, onCardClick = { _, _ -> }, onLogMatch = {})

    @Test fun gridDark() = shot { Grid() }

    @Test fun gridLight() = shot(ThemeMode.LIGHT) { Grid() }

    @Test fun gridFontScale115() = shot(fontScale = 1.15f) { Grid() }

    @Test fun gridFontScale13() = shot(fontScale = 1.3f) { Grid() }

    /** S25 Ultra width: three columns, two roles and "Ranged" beside the stars still fit. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun gridW384() = shot { Grid() }

    @Test fun gridFontScale2() = shot(fontScale = 2f) { Grid() }

    /** The longest name beside a short one, the first row at any width: one line, so one height. */
    private fun longName(fontScale: Float = 1f) {
        val names = listOf("Silverarrow", "Snorri")
        val row = catalog.copy(heroes = (names + "Tigerclaw").map { bundledHero(it.lowercase()) })
        shot(fontScale = fontScale) {
            CatalogScreen(
                state = ready(catalog = row),
                query = "",
                onAction = {},
                onHeroClick = {},
                onSettingsClick = {},
            )
        }
        val heights = names.map {
            compose.onNodeWithContentDescription(it, substring = true).fetchSemanticsNode().boundsInRoot.height
        }
        assertThat(heights.toSet()).hasSize(1)
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun gridLongNameW384() = longName()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun gridLongNameW360() = longName()

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun gridLongNameW384FontScale2() = longName(fontScale = 2f)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun gridLandscape() = shot { Grid() }

    @Test fun listDark() = shot { Grid(CatalogLayout.LIST) }

    /** Cards under their colour's ribbon, then the heroes they belong to. */
    @Test fun searchCardsDark() = shot {
        CatalogScreen(
            state = ready(query = "fast travel"),
            query = "fast travel",
            onAction = {},
            onHeroClick = {},
            onSettingsClick = {},
        )
    }

    /** The one hero with their dashboard's art (rulebook p.16). */
    @Test fun pageTigerclawDark() = shot { Page("tigerclaw") }

    @Test fun pageArienDark() = shot { Page("arien") }

    @Test fun pageArienLight() = shot(ThemeMode.LIGHT) { Page("arien") }

    @Test fun pageGydionDark() = shot { Page("gydion") }

    @Test fun pageMinDark() = shot { Page("min") }

    @Test fun pageArienFontScale2() = shot(fontScale = 2f) { Page("arien") }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun pageArienLandscape() = shot { Page("arien") }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 15_000L
    }
}
