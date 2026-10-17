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
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Ruling
import dev.quinton.goa.core.model.catalog.SourceRef
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.catalog.detail.CardTileGrid
import dev.quinton.goa.feature.catalog.detail.ExtrasSection
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

                // Every Attack card ranged, so the tile shows the "Ranged" pill.
                "test-hero-5" -> hero.copy(
                    cards = hero.cards.map { if (it.primary == PrimaryAction.ATTACK) it.copy(ranged = true) else it },
                )

                else -> hero
            }
        },
    )

    private fun showcase(hero: Hero) = hero.copy(
        aliases = listOf("Test Alias"),
        cards = hero.cards.map { card ->
            when {
                // A "!" card states its Block condition (rulebook p.14), so it is labelled Block.
                card.tier == 3 && card.id.endsWith("a") ->
                    card.copy(item = Stat.DEFENSE, block = true, text = "Block a synthetic attack.")

                card.tier == 3 -> card.copy(item = Stat.DEFENSE)

                // Printed "+" value and a bold keyword, as on real cards (synthetic wording).
                card.tier == 2 -> card.copy(
                    item = Stat.ATTACK,
                    values = card.values.copy(radius = 1, plus = listOf(Stat.ATTACK)),
                    text = "**Test keyword:** ${card.text}",
                )

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
        handicap = hero.handicap?.copy(color = CardColor.GOLD),
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
    ): CatalogUiState.Ready {
        val index = CatalogSearchIndex(catalog.heroes)
        val heroes = CatalogBrowser.browse(catalog, index, query, filter, sort, emptySet(), ratings, usage)
        return CatalogUiState.Ready(
            controls = CatalogControls(layout, filter, sort),
            heroes = heroes,
            totalInScope = catalog.heroes.size,
            options = options,
            cards = CatalogBrowser.cards(index, query, heroes),
        )
    }

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

    /** Two tiles side by side: a long name that wraps next to a short one, with every marker. */
    val longNameTiles: List<Card> = TestCatalog.hero(3).cards.filter { it.tier == 3 }.take(2).mapIndexed { i, card ->
        if (i == 0) {
            card.copy(
                name = "Synthetic Card With a Very Long Name",
                ranged = true,
                block = false,
                item = Stat.DEFENSE,
                values = card.values.copy(range = 1, attack = 5, plus = listOf(Stat.ATTACK)),
            )
        } else {
            card.copy(name = "Short", item = Stat.RADIUS)
        }
    }

    /** A synthetic spellbook: untiered gold pages, two tiers and an Ultimate page. */
    fun spellbook(): DeckLayout {
        val hero = TestCatalog.hero(4)
        fun page(n: Int, color: CardColor, tier: Int?) = Card(
            id = "${hero.id}-spell-page-$n",
            heroId = hero.id,
            name = "Spell Page $n",
            color = color,
            tier = tier,
            primary = PrimaryAction.SKILL,
            text = "Placeholder spell text.",
        )
        return DeckLayout.of(
            hero.copy(
                extras = listOf(
                    page(1, CardColor.GOLD, null),
                    page(2, CardColor.GOLD, null),
                    page(3, CardColor.RED, 1),
                    page(4, CardColor.GREEN, 1),
                    page(5, CardColor.BLUE, 2),
                    page(6, CardColor.PURPLE, 4),
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

    @Test fun searchCardPhraseFirstDark() = shot {
        Catalog(ShotData.ready(query = "reminder text"), query = "reminder text")
    }

    @Test fun searchCardsListLight() = shot(ThemeMode.LIGHT) {
        Catalog(ShotData.ready(query = "red-2a", layout = CatalogLayout.LIST), query = "red-2a")
    }

    @Test fun notInCollectionDark() = shot {
        Catalog(ShotData.ready(query = "zzz").copy(hiddenByCollection = 2), query = "test hero 9")
    }

    @Test fun gridFontScale13() = shot(fontScale = 1.3f) { Catalog(ShotData.ready()) }

    @Test fun deckTilesLongNameDark() = shot {
        CardTileGrid(ShotData.longNameTiles, columns = 2, onCardClick = {}, modifier = Modifier.padding(16.dp))
    }

    @Test fun deckTilesLongNameFontScale13() = shot(fontScale = 1.3f) {
        CardTileGrid(ShotData.longNameTiles, columns = 2, onCardClick = {}, modifier = Modifier.padding(16.dp))
    }

    @Test fun spellbookDark() = shot {
        ExtrasSection(ShotData.spellbook(), onCardClick = {}, modifier = Modifier.padding(horizontal = 16.dp))
    }

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
