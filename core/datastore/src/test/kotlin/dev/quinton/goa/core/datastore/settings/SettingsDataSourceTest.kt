package dev.quinton.goa.core.datastore.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.source(file: File = tmp.newFile("s.preferences_pb").also { it.delete() }) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            produceFile = { file },
        )

    @Test fun defaultsWhenEmpty() = runTest {
        val ds = SettingsDataSource(source())
        assertThat(ds.settings.first()).isEqualTo(AppSettings())
    }

    @Test fun updatesPersistAndEmit() = runTest {
        val ds = SettingsDataSource(source())
        ds.settings.test {
            assertThat(awaitItem()).isEqualTo(AppSettings())
            ds.update { it.copy(theme = ThemeMode.LIGHT, ownedProducts = setOf("core", "arcane")) }
            assertThat(
                awaitItem(),
            ).isEqualTo(AppSettings(theme = ThemeMode.LIGHT, ownedProducts = setOf("core", "arcane")))
            ds.update { it.copy(hapticsEnabled = false, soundsEnabled = false) }
            val last = awaitItem()
            assertThat(last.hapticsEnabled).isFalse()
            assertThat(last.soundsEnabled).isFalse()
            assertThat(last.theme).isEqualTo(ThemeMode.LIGHT)
            ds.update { it.copy(catalogListView = true) }
            assertThat(awaitItem().catalogListView).isTrue()
        }
    }

    @Test fun unknownThemeFallsBackToDefault() = runTest {
        val store = source()
        store.edit { it[SettingsDataSource.Keys.THEME] = "NEON" }
        assertThat(SettingsDataSource(store).settings.first().theme).isEqualTo(ThemeMode.DARK)
    }

    @Test fun clearingOwnedProductsMeansAll() = runTest {
        val ds = SettingsDataSource(source())
        ds.update { it.copy(ownedProducts = setOf("core")) }
        ds.update { it.copy(ownedProducts = emptySet()) }
        assertThat(ds.settings.first().ownedProducts).isEmpty()
    }
}
