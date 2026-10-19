package dev.quinton.goa.hub.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.presentation.catalog.GameIconLabels
import dev.quinton.goa.hub.catalog.HubCatalog
import org.junit.Test

/** Every glyph and sigil the catalog pages use has its CSS line and its file. */
class HeroesStaticTest {
    private fun resource(path: String) = javaClass.classLoader.getResource(path)

    private val css = requireNotNull(resource("static/heroes/heroes.css")).readText()

    @Test
    fun `every card text icon has a label, a glyph file and a class`() {
        CardText.ICONS.forEach { token ->
            assertThat(GameIconLabels.forToken(token)).isNotNull()
            val file = GameIconFiles.byToken[token]
            assertThat(file).isNotNull()
            assertThat(resource("static/heroes/symbols/$file.svg")).isNotNull()
            assertThat(css).contains(".gi-$token { --gi: url(/static/heroes/symbols/$file.svg); }")
        }
    }

    @Test
    fun `every hero has a sigil class and file`() {
        HubCatalog().catalog.heroes.forEach { hero ->
            assertThat(css).contains(".${sigilClass(hero.id)} { --sigil: url(/static/sigils/${hero.id}.svg); }")
            assertThat(resource("static/sigils/${hero.id}.svg")).isNotNull()
        }
    }

    @Test
    fun `every icon class the pages use exists`() {
        Regex("--icon: url[(](/static/[^)]+)[)]").findAll(css).forEach { match ->
            assertThat(resource(match.groupValues[1].removePrefix("/"))).isNotNull()
        }
        listOf(
            "tune", "sort", "grid_view", "view_list", "star_fill", "verified", "help", "expand_more",
            "play_arrow_fill", "restart_alt", "arrow_forward", "chevron_left", "military_tech",
        ).forEach {
            assertThat(css).contains(".icon-$it {")
        }
    }
}
