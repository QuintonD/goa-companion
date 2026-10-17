package dev.quinton.goa.feature.catalog

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.CardViewerRoute
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.FakeRatingsRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.catalog.detail.HeroDetailUiState
import dev.quinton.goa.feature.catalog.detail.HeroDetailViewModel
import dev.quinton.goa.feature.catalog.list.CatalogAction
import dev.quinton.goa.feature.catalog.list.CatalogLayout
import dev.quinton.goa.feature.catalog.list.CatalogUiState
import dev.quinton.goa.feature.catalog.list.CatalogViewModel
import dev.quinton.goa.feature.catalog.model.CatalogFilter
import dev.quinton.goa.feature.catalog.model.CatalogSort
import dev.quinton.goa.feature.catalog.viewer.CardViewerUiState
import dev.quinton.goa.feature.catalog.viewer.CardViewerViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CatalogViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val catalogRepo = FakeCatalogRepository(initial = null)
    private val settings = FakeSettingsRepository()
    private val ratings = FakeRatingsRepository()

    private fun viewModel() = CatalogViewModel(catalogRepo, settings, ratings, main.dispatcher)

    @Test
    fun loadingThenReady() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertThat(awaitItem()).isEqualTo(CatalogUiState.Loading)
            catalogRepo.state.value = TestCatalog.catalog
            val ready = awaitItem() as CatalogUiState.Ready
            assertThat(ready.heroes).hasSize(12)
            assertThat(ready.totalInScope).isEqualTo(12)
            assertThat(ready.options.products).hasSize(2)
            assertThat(ready.options.hasOwnedProducts).isFalse()
        }
    }

    @Test
    fun emptyCatalogIsUnavailableNotACrash() = runTest {
        catalogRepo.state.value = Catalog.EMPTY
        viewModel().uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(CatalogUiState.Unavailable(error = false))
        }
    }

    @Test
    fun searchFiltersAndSortsUpdateState() = runTest {
        catalogRepo.state.value = TestCatalog.catalog
        val vm = viewModel()
        vm.uiState.test {
            awaitReady()
            vm.onAction(CatalogAction.Search("test hero 7"))
            assertThat(vm.query.value).isEqualTo("test hero 7")
            assertThat((expectMostRecentItem() as CatalogUiState.Ready).heroes.map { it.hero.id }).containsExactly("test-hero-7")

            vm.onAction(CatalogAction.Search(""))
            vm.onAction(CatalogAction.SetFilter(CatalogFilter(complexities = setOf(4))))
            val filtered = expectMostRecentItem() as CatalogUiState.Ready
            assertThat(filtered.heroes.all { it.hero.complexity == 4 }).isTrue()
            assertThat(filtered.isFiltered).isTrue()

            vm.onAction(CatalogAction.SetSort(CatalogSort.COMPLEXITY))
            vm.onAction(CatalogAction.SetLayout(CatalogLayout.LIST))
            val controls = (expectMostRecentItem() as CatalogUiState.Ready).controls
            assertThat(controls.sort).isEqualTo(CatalogSort.COMPLEXITY)
            assertThat(controls.layout).isEqualTo(CatalogLayout.LIST)

            vm.onAction(CatalogAction.Search("nothing-matches-this"))
            vm.onAction(CatalogAction.ClearAll)
            val cleared = expectMostRecentItem() as CatalogUiState.Ready
            assertThat(cleared.heroes).hasSize(12)
            assertThat(vm.query.value).isEmpty()
        }
    }

    @Test
    fun ownedProductsScopeTheCatalogAndPruneProductFilters() = runTest {
        catalogRepo.state.value = TestCatalog.catalog
        val vm = viewModel()
        vm.uiState.test {
            awaitReady()
            vm.onAction(CatalogAction.SetFilter(CatalogFilter(products = setOf("test-pack"))))
            settings.state.value = AppSettings(ownedProducts = setOf("test-core"))
            val owned = expectMostRecentItem() as CatalogUiState.Ready
            assertThat(owned.totalInScope).isEqualTo(6)
            assertThat(owned.heroes.map { it.hero.product }.toSet()).containsExactly("test-core")
            assertThat(owned.controls.filter.products).isEmpty()
            assertThat(owned.options.hasOwnedProducts).isTrue()

            vm.onAction(CatalogAction.SetFilter(CatalogFilter(includeUnowned = true)))
            assertThat((expectMostRecentItem() as CatalogUiState.Ready).heroes).hasSize(12)
        }
    }

    @Test
    fun ratingsFlowIntoItems() = runTest {
        catalogRepo.state.value = TestCatalog.catalog
        val vm = viewModel()
        vm.uiState.test {
            awaitReady()
            ratings.heroRatings.value = mapOf("test-hero-2" to Rating(1300.0, 5, 4, provisional = true))
            vm.onAction(CatalogAction.SetSort(CatalogSort.ELO))
            val ready = expectMostRecentItem() as CatalogUiState.Ready
            assertThat(ready.heroes.first().hero.id).isEqualTo("test-hero-2")
            assertThat(ready.heroes.first().rating?.value).isEqualTo(1300.0)
        }
    }
}

private suspend fun ReceiveTurbine<CatalogUiState>.awaitReady(): CatalogUiState.Ready {
    while (true) {
        val item = awaitItem()
        if (item is CatalogUiState.Ready) return item
    }
}

class HeroDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val ana = Player("ana", "Ana", 0xFF3FC1B0, 1, 0)

    private fun match(id: String, winner: Team) = Match(
        id = id,
        createdAt = 0,
        playedAt = 0,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = winner,
        participants = listOf(Participant("ana", "test-hero-1", Team.RED), Participant("x", "test-hero-2", Team.BLUE)),
    )

    @Test
    fun readyWithDeckAndPersonalStats() = runTest {
        val ratings = FakeRatingsRepository().apply {
            heroRatings.value = mapOf("test-hero-1" to Rating(1234.0, 3, 2, provisional = true))
            heroUsage.value = mapOf("test-hero-1" to HeroUsage("test-hero-1", 3, 2, 1, 1.0, 2.0 / 3, 0.25))
        }
        val vm = HeroDetailViewModel(
            HeroDetailRoute("test-hero-1"),
            FakeCatalogRepository(),
            ratings,
            FakeMatchRepository(listOf(match("1", Team.RED), match("2", Team.RED), match("3", Team.BLUE))),
            FakePlayerRepository(listOf(ana)),
        )
        vm.uiState.test {
            val ready = expectMostRecentItem() as HeroDetailUiState.Ready
            assertThat(ready.hero.id).isEqualTo("test-hero-1")
            assertThat(ready.productName).isEqualTo("Test Pack")
            assertThat(ready.deck.orderedCards).hasSize(19)
            assertThat(ready.stats.rating?.value).isEqualTo(1234.0)
            assertThat(ready.stats.usage?.bans).isEqualTo(1)
            assertThat(ready.stats.log.bestPilots.single().player).isEqualTo(ana)
            assertThat(ready.stats.hasGames).isTrue()
        }
    }

    @Test
    fun unknownHeroIsNotFound() = runTest {
        val vm = HeroDetailViewModel(
            HeroDetailRoute("nope"),
            FakeCatalogRepository(),
            FakeRatingsRepository(),
            FakeMatchRepository(),
            FakePlayerRepository(),
        )
        vm.uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(HeroDetailUiState.NotFound("nope"))
        }
    }
}

class CardViewerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun opensAtTheRequestedCardInDetailOrder() = runTest {
        val vm = CardViewerViewModel(CardViewerRoute("test-hero-1", "test-hero-1-green-2b"), FakeCatalogRepository())
        vm.uiState.test {
            val ready = expectMostRecentItem() as CardViewerUiState.Ready
            assertThat(ready.cards).hasSize(19)
            assertThat(ready.cards[ready.initialIndex].id).isEqualTo("test-hero-1-green-2b")
            assertThat(ready.cards.first().id).isEqualTo("test-hero-1-gold")
            assertThat(ready.isHandicap(ready.cards.last())).isTrue()
        }
    }

    @Test
    fun unknownCardFallsBackToFirstAndUnknownHeroIsNotFound() = runTest {
        val unknownCard = CardViewerViewModel(CardViewerRoute("test-hero-1", "missing"), FakeCatalogRepository())
        unknownCard.uiState.test {
            assertThat((expectMostRecentItem() as CardViewerUiState.Ready).initialIndex).isEqualTo(0)
        }
        val unknownHero = CardViewerViewModel(CardViewerRoute("missing", "missing"), FakeCatalogRepository())
        unknownHero.uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(CardViewerUiState.NotFound)
        }
    }
}
