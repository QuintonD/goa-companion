package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.model.catalog.CardColor
import java.io.File
import org.junit.Test

/**
 * EFFECTS.md "Authoring a hero": a hero is complete only when every one of its cards (deck, extra
 * golds, Handicap, Ultimate, extras) has a script and is exercised by its test class.
 */
class HeroCoverageTest {
    private val registry = Heroes.registry()

    private fun testSource(heroClass: String): String? {
        val rel = "src/test/kotlin/dev/quinton/goa/core/game/heroes/${heroClass}Test.kt"
        return listOf(File(rel), File("core/game/$rel")).firstOrNull { it.exists() }?.readText()
    }

    @Test
    fun `every card of a complete hero has a script and a test`() {
        for (module in registry.heroModules.filter { it.complete }) {
            val hero = TestData.catalog.hero(module.heroId) ?: error("No catalog hero ${module.heroId}")
            val cls = module::class.simpleName!!.removeSuffix("Hero")
            val source = testSource(cls)
            assertWithMessage("${cls}Test.kt exists").that(source).isNotNull()
            for (card in hero.allCards) {
                assertWithMessage("${card.id} has a script").that(registry.script(card.id)).isNotNull()
                // The Ultimate is set up with the PositionBuilder's ultimate().
                val needle = if (card.color == CardColor.PURPLE) "ultimate()" else "\"${card.id}\""
                assertWithMessage("${card.id} is tested in ${cls}Test").that(source!!.contains(needle)).isTrue()
            }
        }
    }

    @Test
    fun `every hero of the catalog has a module`() {
        val modules = Heroes.all.map { it.heroId }.toSet()
        for (h in TestData.catalog.heroes) assertWithMessage(h.id).that(modules).contains(h.id)
    }
}
