package dev.quinton.goa.hub.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The hub packages the app's own catalog and images (no second copy in git). */
class HubCatalogTest {
    private val catalog = HubCatalog()

    @Test
    fun `the bundled catalog loads with the app's strict loader and every image it names is there`() {
        val loaded = catalog.catalog
        assertThat(loaded.heroes).isNotEmpty()
        val images = loaded.heroes.flatMap { hero -> listOfNotNull(hero.portrait) + hero.cards.mapNotNull { it.image } }
        assertThat(images).isNotEmpty()
        assertThat(images.filterNot(catalog::exists)).isEmpty()
        for (file in listOf("draft-methods.json", "match-setup.json", "timer-presets.json", "rules-reference.json")) {
            assertThat(catalog.readText("catalog/$file")).startsWith("{")
        }
    }

    @Test
    fun `asset paths can't leave the assets`() {
        assertThat(HubCatalog.clean("official/cards/a.webp")).isEqualTo("official/cards/a.webp")
        assertThat(HubCatalog.clean("../hub.json")).isNull()
        assertThat(HubCatalog.clean("official/../../x")).isNull()
        assertThat(HubCatalog.clean("official/%2e%2e/x")).isNull()
        assertThat(catalog.open("../simplelogger.properties")).isNull()
        assertThat(HubCatalog.url("official/cards/a.webp")).isEqualTo("/assets/official/cards/a.webp")
    }
}
