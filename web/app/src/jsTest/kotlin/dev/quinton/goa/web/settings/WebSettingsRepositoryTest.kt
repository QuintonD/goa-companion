package dev.quinton.goa.web.settings

import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class WebSettingsRepositoryTest {
    @Test
    fun freshDeviceHasThePhonesDefaults() = runTest {
        assertEquals(AppSettings(), WebSettingsRepository(MemoryKeyValueStore()).settings.first())
    }

    @Test
    fun anUpdateSurvivesAReload() = runTest {
        val store = MemoryKeyValueStore()
        WebSettingsRepository(store).update {
            it.copy(ownedProducts = setOf("base"), catalogListView = true, timerLayout = TimerLayout.ROUND)
        }

        val reloaded = WebSettingsRepository(store).settings.first()

        assertEquals(setOf("base"), reloaded.ownedProducts)
        assertEquals(true, reloaded.catalogListView)
        assertEquals(TimerLayout.ROUND, reloaded.timerLayout)
    }

    @Test
    fun unreadableStoredSettingsFallBackToDefaults() = runTest {
        val store = MemoryKeyValueStore()
        store.put("settings/app", "{not json")

        assertEquals(AppSettings(), WebSettingsRepository(store).settings.first())
    }

    @Test
    fun aNewerBuildsExtraFieldsDontBreakAnOlderRead() = runTest {
        val store = MemoryKeyValueStore()
        store.put("settings/app", """{"soundsEnabled":false,"someFutureSetting":3}""")

        assertEquals(false, WebSettingsRepository(store).settings.first().soundsEnabled)
    }
}
