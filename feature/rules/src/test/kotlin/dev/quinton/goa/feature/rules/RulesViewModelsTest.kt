package dev.quinton.goa.feature.rules

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.presentation.rules.LadderUiState
import dev.quinton.goa.core.presentation.rules.RulesTopicUiState
import dev.quinton.goa.core.presentation.rules.RulesUiState
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.feature.rules.data.RulesReferenceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelsTest {
    private val source = RulesReferenceSource { bundledReference }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun <T> ReceiveTurbine<*>.readyOf(type: Class<T>): T {
        while (true) {
            val item = awaitItem()
            if (type.isInstance(item)) return type.cast(item)
        }
    }

    private fun ladder(vararg heroIds: String) =
        LevelLadderViewModel(heroIds.toList(), source, FakeCatalogRepository(ladderCatalog))

    @Test
    fun `ladder quotes only the match heroes' cards that change a defeat`() = runTest {
        ladder("arien", "ursafar").uiState.test {
            val ready = readyOf(LadderUiState.Ready::class.java)
            assertThat(ready.matchHeroesOnly).isTrue()
            assertThat(ready.exceptions.map { it.heroName to it.cardName }).containsExactly("Ursafar" to "Tear")
            assertThat(ready.exceptions.single().label).isEqualTo("Red · Tier III")
            assertThat(ready.exceptions.single().text).contains("that hero spends 1 additional {life} Life counter")
        }
    }

    @Test
    fun `without match heroes the ladder lists every hero's exceptions`() = runTest {
        ladder().uiState.test {
            val ready = readyOf(LadderUiState.Ready::class.java)
            assertThat(ready.matchHeroesOnly).isFalse()
            assertThat(ready.exceptions.map { "${it.heroName} · ${it.cardName} · ${it.label}" }).containsExactly(
                "Bain · Dead or Alive · Gold · Basic",
                "Bain · Side Quest · Handicap · Gold",
                "Gydion · Energy Drain · Spell · Red · Tier III",
                "Rowenna · Glorious Triumph · Blue · Tier III",
                "Ursafar · Tear · Red · Tier III",
            ).inOrder()
        }
    }

    @Test
    fun `picking a level selects it and picking it again clears it`() = runTest {
        val vm = ladder("bain")
        vm.uiState.test {
            assertThat(readyOf(LadderUiState.Ready::class.java).selectedLevel).isNull()
            vm.selectLevel(5)
            assertThat((awaitItem() as LadderUiState.Ready).selectedLevel).isEqualTo(5)
            vm.selectLevel(5)
            assertThat((awaitItem() as LadderUiState.Ready).selectedLevel).isNull()
        }
    }

    @Test
    fun `rules list filters topics by the query`() = runTest {
        val vm = RulesViewModel(source)
        vm.uiState.test {
            val all = readyOf(RulesUiState.Ready::class.java)
            assertThat(all.topics.size).isEqualTo(all.totalTopics)
            vm.setQuery("respawn")
            val filtered = awaitItem() as RulesUiState.Ready
            assertThat(filtered.topics.map { it.id }).contains("hero-respawn")
            assertThat(filtered.topics.size).isLessThan(all.totalTopics)
        }
    }

    @Test
    fun `unknown topic is not found`() = runTest {
        RulesTopicViewModel("no-such-topic", source).uiState.test {
            assertThat(readyOf(RulesTopicUiState.NotFound::class.java)).isEqualTo(RulesTopicUiState.NotFound)
        }
    }
}
