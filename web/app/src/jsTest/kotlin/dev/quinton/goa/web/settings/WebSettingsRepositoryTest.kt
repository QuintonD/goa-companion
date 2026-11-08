package dev.quinton.goa.web.settings

import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.BoardStyle
import dev.quinton.goa.core.model.settings.CatalogLayout
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
    fun aBoardStyleFromALaterBuildKeepsEveryOtherSetting() = runTest {
        val store = MemoryKeyValueStore()
        WebSettingsRepository(store).update { it.copy(catalogLayout = CatalogLayout.LIST, computerStrength = 8) }
        val key = store.keys().single()
        store.put(key, store.get(key)!!.replace("\"MINIATURES\"", "\"HOLOGRAMS\""))

        val reloaded = WebSettingsRepository(store).settings.first()

        assertEquals(BoardStyle.MINIATURES, reloaded.boardStyle)
        assertEquals(CatalogLayout.LIST, reloaded.catalogLayout)
        assertEquals(8, reloaded.computerStrength)
    }

    @Test
    fun anUpdateSurvivesAReload() = runTest {
        val store = MemoryKeyValueStore()
        WebSettingsRepository(store).update {
            it.copy(ownedProducts = setOf("base"), catalogLayout = CatalogLayout.LIST, timerLayout = TimerLayout.ROUND)
        }

        val reloaded = WebSettingsRepository(store).settings.first()

        assertEquals(setOf("base"), reloaded.ownedProducts)
        assertEquals(CatalogLayout.LIST, reloaded.catalogLayout)
        assertEquals(TimerLayout.ROUND, reloaded.timerLayout)
    }

    @Test
    fun theBoardStyleSurvivesAReloadAndAnOldStoreGetsTheDefault() = runTest {
        val store = MemoryKeyValueStore()
        assertEquals(BoardStyle.MINIATURES, WebSettingsRepository(store).settings.first().boardStyle)

        WebSettingsRepository(store).update { it.copy(boardStyle = BoardStyle.GRID_AND_DISCS) }

        assertEquals(BoardStyle.GRID_AND_DISCS, WebSettingsRepository(store).settings.first().boardStyle)

        store.put("settings/app", """{"soundsEnabled":false}""")
        assertEquals(BoardStyle.MINIATURES, WebSettingsRepository(store).settings.first().boardStyle)
    }

    @Test
    fun anOlderBuildsListSwitchReadsAsTheList() = runTest {
        val store = MemoryKeyValueStore()
        store.put("settings/app", """{"catalogListView":true,"computerStrength":8}""")

        val settings = WebSettingsRepository(store).settings.first()

        assertEquals(CatalogLayout.LIST, settings.catalogLayout)
        assertEquals(8, settings.computerStrength)
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
