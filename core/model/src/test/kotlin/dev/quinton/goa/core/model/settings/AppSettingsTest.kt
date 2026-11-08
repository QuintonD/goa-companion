package dev.quinton.goa.core.model.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class AppSettingsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private fun decode(text: String) = json.decodeFromString(AppSettings.serializer(), text)

    @Test
    fun `an older build's list switch reads as the list`() {
        assertThat(decode("""{"catalogListView":true,"computerStrength":8}"""))
            .isEqualTo(AppSettings(catalogLayout = CatalogLayout.LIST, computerStrength = 8))
        assertThat(decode("""{"catalogListView":false}""").catalogLayout).isEqualTo(CatalogLayout.LARGE_GRID)
    }

    @Test
    fun `the layout wins over the old switch, and round trips`() {
        assertThat(decode("""{"catalogListView":true,"catalogLayout":"SMALL_GRID"}""").catalogLayout)
            .isEqualTo(CatalogLayout.SMALL_GRID)
        val settings = AppSettings(catalogLayout = CatalogLayout.SMALL_GRID, boardStyle = BoardStyle.GRID_AND_DISCS)
        val text = json.encodeToString(AppSettings.serializer(), settings)
        assertThat(text).doesNotContain("catalogListView")
        assertThat(decode(text)).isEqualTo(settings)
    }

    @Test
    fun `an unknown layout falls back to the large grid`() {
        assertThat(decode("""{"catalogLayout":"MOSAIC"}""").catalogLayout).isEqualTo(CatalogLayout.LARGE_GRID)
    }
}
