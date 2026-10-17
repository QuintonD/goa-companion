package dev.quinton.goa.feature.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Ruling
import dev.quinton.goa.core.model.catalog.SourceRef
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.catalog.detail.HeroDetailScreen
import dev.quinton.goa.feature.catalog.detail.HeroDetailUiState
import dev.quinton.goa.feature.catalog.detail.HeroPersonalStats
import dev.quinton.goa.feature.catalog.list.CatalogControls
import dev.quinton.goa.feature.catalog.list.CatalogFilterContent
import dev.quinton.goa.feature.catalog.list.CatalogLayout
import dev.quinton.goa.feature.catalog.list.CatalogScreen
import dev.quinton.goa.feature.catalog.list.CatalogUiState
import dev.quinton.goa.feature.catalog.list.FilterOptions
import dev.quinton.goa.feature.catalog.model.AttackStyle
import dev.quinton.goa.feature.catalog.model.CatalogBrowser
import dev.quinton.goa.feature.catalog.model.CatalogFilter
import dev.quinton.goa.feature.catalog.model.CatalogSearchIndex
import dev.quinton.goa.feature.catalog.model.CatalogSort
import dev.quinton.goa.feature.catalog.model.DeckLayout
import dev.quinton.goa.feature.catalog.model.HeroLogSummary
import dev.quinton.goa.feature.catalog.model.HeroPilotStat
import dev.quinton.goa.feature.catalog.viewer.CardViewerScreen
import dev.quinton.goa.feature.catalog.viewer.CardViewerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic fixture content only — no real game data. */
private object ShotData {
    val catalog: Catalog = TestCatalog.catalog.copy(
        heroes = TestCatalog.heroes.map { hero ->
            when (hero.id) {
                "test-hero-2" -> showcase(hero)

                "test-hero-5" -> hero.copy(
                    cards = hero.cards.map {
                        if (it.color ==
                            CardColor.GOLD
                        ) {
                            it.copy(ranged = true)
                        } else {
                            it
                        }
                    },
                )

                else -> hero
            }
        },
    )

    private fun showcase(hero: Hero) = hero.copy(
        aliases = listOf("Test Alias"),
        cards = hero.cards.map { card ->
            when {
                card.tier == 3 -> card.copy(item = Stat.DEFENSE, block = card.id.endsWith("a"))
                card.tier == 2 -> card.copy(item = Stat.ATTACK, values = card.values.copy(radius = 1))
                else -> card
            }
        },
        faq = listOf(
            Ruling(
                "Synthetic question one about a placeholder card?",
                "Synthetic answer for screenshot tests.",
                "test",
            ),
            Ruling("Synthetic question two?", "Another synthetic answer.", "test"),
        ),
        lore = "Synthetic lore paragraph for layout testing. It is not game text.",
        sources = listOf(SourceRef("Synthetic fixture", url = "test://fixture", retrieved = "2026-10-17")),
    )

    val ratings = mapOf(
        "test-hero-2" to Rating(1268.0, games = 9, wins = 6, provisional = true),
        "test-hero-7" to Rating(1231.0, games = 12, wins = 7, provisional = false),
        "test-hero-4" to Rating(1180.0, games = 5, wins = 2, provisional = true),
    )

    val usage = mapOf(
        "test-hero-2" to
            HeroUsage(
                "test-hero-2",
                matches = 9,
                wins = 6,
                bans = 2,
                pickRate = 0.45,
                winRate = 6 / 9.0,
                banRate = 0.2,
            ),
    )

    val options = FilterOptions(
        complexities = listOf(1, 2, 3, 4),
        roles = catalog.heroes.flatMap { it.roles }.distinct().sortedBy { it.ordinal },
        products = catalog.products,
        attackStyles = AttackStyle.entries,
        hasOwnedProducts = true,
    )

    fun ready(
        query: String = "",
        filter: CatalogFilter = CatalogFilter(),
        sort: CatalogSort = CatalogSort.NAME,
        layout: CatalogLayout = CatalogLayout.GRID,
    ) = CatalogUiState.Ready(
        controls = CatalogControls(layout, filter, sort),
        heroes = CatalogBrowser.browse(
            catalog,
            CatalogSearchIndex(catalog.heroes),
            query,
            filter,
            sort,
            emptySet(),
            ratings,
            usage,
        ),
        totalInScope = catalog.heroes.size,
        options = options,
    )

    val players = listOf(
        Player("p1", "Avery", 0xFF3FC1B0, 1, 0),
        Player("p2", "Blake", 0xFFE26459, 2, 0),
    )

    fun detail(): HeroDetailUiState.Ready {
        val hero = catalog.heroes.first { it.id == "test-hero-2" }
        return HeroDetailUiState.Ready(
            hero = hero,
            productName = "Test Core",
            deck = DeckLayout.of(hero),
            stats = HeroPersonalStats(
                rating = ratings["test-hero-2"],
                usage = usage["test-hero-2"],
                log = HeroLogSummary(
                    games = 9,
                    wins = 6,
                    bestPilots = listOf(HeroPilotStat("p1", players[0], 5, 4), HeroPilotStat("p2", players[1], 4, 2)),
                    allPilots = emptyList(),
                ),
            ),
        )
    }

    fun viewer(): CardViewerUiState.Ready {
        val hero = catalog.heroes.first { it.id == "test-hero-2" }
        val cards = DeckLayout.of(hero).orderedCards
        return CardViewerUiState.Ready(hero, cards, cards.indexOfFirst { it.id == "test-hero-2-red-2a" })
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CatalogScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(mode: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GoaTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Composable
    private fun Catalog(state: CatalogUiState, query: String = "") =
        CatalogScreen(state = state, query = query, onAction = {}, onHeroClick = {}, onSettingsClick = {})

    @Test fun gridDark() = shot { Catalog(ShotData.ready()) }

    @Test fun gridLight() = shot(ThemeMode.LIGHT) { Catalog(ShotData.ready()) }

    @Test fun gridDarkFontScale2() = shot(fontScale = 2f) { Catalog(ShotData.ready()) }

    @Test fun listByEloDark() = shot {
        Catalog(ShotData.ready(sort = CatalogSort.ELO, layout = CatalogLayout.LIST))
    }

    @Test fun listLight() = shot(ThemeMode.LIGHT) { Catalog(ShotData.ready(layout = CatalogLayout.LIST)) }

    @Test fun searchByCardNameDark() = shot { Catalog(ShotData.ready(query = "red-2a"), query = "red-2a") }

    @Test fun filteredWithChipsDark() = shot {
        Catalog(ShotData.ready(filter = CatalogFilter(complexities = setOf(2, 3), attackStyle = AttackStyle.MELEE)))
    }

    @Test fun noResultsDark() = shot { Catalog(ShotData.ready(query = "zzz"), query = "zzz") }

    @Test fun emptyCatalogDark() = shot { Catalog(CatalogUiState.Unavailable(error = false)) }

    @Test fun emptyCatalogLight() = shot(ThemeMode.LIGHT) { Catalog(CatalogUiState.Unavailable(error = false)) }

    private val sheetFilter =
        CatalogFilter(complexities = setOf(3), roles = setOf(ShotData.options.roles[1]), products = setOf("test-core"))

    @Test fun filterSheetDark() = shot {
        CatalogFilterContent(sheetFilter, ShotData.options, 4, {}, {}, {}, Modifier.padding(vertical = 16.dp))
    }

    @Test fun filterSheetLight() = shot(ThemeMode.LIGHT) {
        CatalogFilterContent(sheetFilter, ShotData.options, 4, {}, {}, {}, Modifier.padding(vertical = 16.dp))
    }

    @Test fun filterSheetFontScale2() = shot(fontScale = 2f) {
        CatalogFilterContent(sheetFilter, ShotData.options, 4, {}, {}, {}, Modifier.padding(vertical = 16.dp))
    }

    @Test
    @Config(qualifiers = "w411dp-h2900dp-xxhdpi")
    fun detailDark() = shot { HeroDetailScreen(ShotData.detail(), onBack = {}, onCardClick = { _, _ -> }) }

    @Test
    @Config(qualifiers = "w411dp-h2900dp-xxhdpi")
    fun detailLight() = shot(ThemeMode.LIGHT) {
        HeroDetailScreen(ShotData.detail(), onBack = {}, onCardClick = { _, _ -> })
    }

    @Test fun detailDarkFontScale2() = shot(fontScale = 2f) {
        HeroDetailScreen(ShotData.detail(), onBack = {}, onCardClick = { _, _ -> })
    }

    @Test fun detailNotFoundDark() = shot {
        HeroDetailScreen(HeroDetailUiState.NotFound("x"), onBack = {}, onCardClick = { _, _ -> })
    }

    @Test fun cardViewerDark() = shot { CardViewerScreen(ShotData.viewer(), onBack = {}) }

    @Test fun cardViewerLight() = shot(ThemeMode.LIGHT) { CardViewerScreen(ShotData.viewer(), onBack = {}) }

    @Test fun cardViewerFontScale2() = shot(fontScale = 2f) { CardViewerScreen(ShotData.viewer(), onBack = {}) }
}
