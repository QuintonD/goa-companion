package dev.quinton.goa.feature.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.testing.TestCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Synthetic data only ("Test Hero N"); never real game content. */
object Fixtures {
    /** A [count]-hero catalog where every hero has 21 cards (19 + 2 extras) with varied text. */
    fun largeCatalog(count: Int = 50): Catalog {
        val heroes = (1..count).map { i ->
            val base = TestCatalog.hero(i)
            val cards = base.cards.mapIndexed { j, card ->
                card.copy(
                    name = "Card ${wordFor(i * 31 + j)} ${wordFor(j)}",
                    text = "Target a unit in {range} ${wordFor(i + j)}; it gains {coin} " +
                        "and moves ${j % 4} spaces. ~(Synthetic text $i-$j.)\nDefeat a {minion} ${wordFor(i * j)}.",
                )
            }
            base.copy(
                name = "Test Hero $i ${wordFor(i)}",
                cards = cards,
                extras = listOf(extra(base.id, 1, i), extra(base.id, 2, i)),
            )
        }
        return TestCatalog.catalog.copy(heroes = heroes)
    }

    private fun extra(heroId: String, n: Int, i: Int) = Card(
        id = "$heroId-extra-$n",
        heroId = heroId,
        name = "Extra ${wordFor(i + n)}",
        color = CardColor.OTHER,
        text = "Extra page $n with {skill} ${wordFor(n * i)}.",
    )

    private val Syllables = listOf("ka", "ri", "mo", "tel", "zan", "vor", "lu", "dre", "sha", "pim", "gor", "eth")

    private fun wordFor(seed: Int): String {
        val a = Syllables[Math.floorMod(seed, Syllables.size)]
        val b = Syllables[Math.floorMod(seed / Syllables.size + 3, Syllables.size)]
        return a + b
    }

    fun heroWith(index: Int, transform: Hero.() -> Hero): Hero = TestCatalog.hero(index).transform()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
