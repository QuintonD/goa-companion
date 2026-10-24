package dev.quinton.goa.feature.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.TestCatalog
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
class SettingsViewModelTest {
    private val settings = FakeSettingsRepository()
    private val catalog = FakeCatalogRepository()
    private val ids = TestCatalog.products.map { it.id }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun ReceiveTurbine<SettingsUiState>.ready(): SettingsUiState.Ready {
        var item = awaitItem()
        while (item !is SettingsUiState.Ready || item.products.isEmpty()) item = awaitItem()
        return item
    }

    @Test fun defaultsOwnEverything() = runTest {
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            val s = ready()
            assertThat(s.theme).isEqualTo(ThemeMode.DARK)
            assertThat(s.ownsEverything).isTrue()
            assertThat(s.products.map { it.id }).isEqualTo(ids)
            assertThat(s.products.all { it.owned }).isTrue()
            assertThat(s.products.sumOf { it.heroCount }).isEqualTo(TestCatalog.catalog.heroes.size)
        }
    }

    @Test fun themeAndToggles() = runTest {
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            ready()
            vm.onAction(SettingsAction.SetTheme(ThemeMode.LIGHT))
            vm.onAction(SettingsAction.SetHaptics(false))
            vm.onAction(SettingsAction.SetSounds(false))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(settings.state.value).isEqualTo(
            AppSettings(theme = ThemeMode.LIGHT, hapticsEnabled = false, soundsEnabled = false),
        )
    }

    @Test fun timerLayoutIsShownAndSaved() = runTest {
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            assertThat(ready().timerLayout).isEqualTo(TimerLayout.STEPS)
            vm.onAction(SettingsAction.SetTimerLayout(TimerLayout.ROUND))
            assertThat(ready().timerLayout).isEqualTo(TimerLayout.ROUND)
        }
        assertThat(settings.state.value.timerLayout).isEqualTo(TimerLayout.ROUND)
    }

    @Test fun puzzleNudgesAreOnByDefaultAndSaved() = runTest {
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            assertThat(ready().puzzleNudges).isTrue()
            vm.onAction(SettingsAction.SetPuzzleNudges(false))
            assertThat(ready().puzzleNudges).isFalse()
        }
        assertThat(settings.state.value.puzzleNudges).isFalse()
    }

    @Test fun deselectingFromAllStoresTheRest() = runTest {
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            ready()
            vm.onAction(SettingsAction.ToggleProduct(ids[1]))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(settings.state.value.ownedProducts).containsExactlyElementsIn(ids - ids[1])
    }

    @Test fun reselectingEverythingStoresEmpty() = runTest {
        settings.state.value = AppSettings(ownedProducts = setOf(ids[0]))
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            val s = ready()
            assertThat(s.ownsEverything).isFalse()
            assertThat(s.ownedCount).isEqualTo(1)
            vm.onAction(SettingsAction.ToggleProduct(ids[1]))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(settings.state.value.ownedProducts).isEmpty()
    }

    @Test fun cannotDeselectTheLastProduct() = runTest {
        settings.state.value = AppSettings(ownedProducts = setOf(ids[0]))
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            ready()
            vm.onAction(SettingsAction.ToggleProduct(ids[0]))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(settings.state.value.ownedProducts).containsExactly(ids[0])
    }

    @Test fun ownsEverythingSwitch() = runTest {
        val vm = SettingsViewModel(settings, catalog)
        vm.uiState.test {
            ready()
            vm.onAction(SettingsAction.SetOwnsEverything(false))
            val narrowed = ready()
            assertThat(narrowed.ownsEverything).isFalse()
            // Every product stays ticked; nothing resets to the base game.
            assertThat(narrowed.ownedCount).isEqualTo(ids.size)
            assertThat(settings.state.value.ownedProducts).containsExactlyElementsIn(ids)
            vm.onAction(SettingsAction.SetOwnsEverything(true))
            assertThat(ready().ownsEverything).isTrue()
        }
    }
}
