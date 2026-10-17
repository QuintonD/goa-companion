package dev.quinton.goa

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.FakeSettingsRepository
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
class MainViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun followsTheSavedTheme() = runTest {
        val settings = FakeSettingsRepository(AppSettings(theme = ThemeMode.LIGHT))
        val vm = MainViewModel(settings)
        vm.uiState.test {
            assertThat(awaitItem()).isEqualTo(MainUiState.Ready(ThemeMode.LIGHT))
            settings.update { it.copy(theme = ThemeMode.SYSTEM) }
            assertThat(awaitItem()).isEqualTo(MainUiState.Ready(ThemeMode.SYSTEM))
            settings.update { it.copy(hapticsEnabled = false) } // unrelated change: no new state
            expectNoEvents()
        }
    }
}
