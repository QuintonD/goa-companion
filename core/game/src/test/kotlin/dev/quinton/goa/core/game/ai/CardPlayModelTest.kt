package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.Zone
import org.junit.Test

/** The learned card-play model (ADR-062 part 2): checked-in weights, generic tables, a proper distribution. */
class CardPlayModelTest {
    private val data = TestData.engine.data
    private val ev = Evaluator(data, AiPlayers.TUNED_WITH_NET)
    private val static = StaticPolicy(ev)

    @Test
    fun `the checked-in weights load and name real heroes and cards`() {
        val m = CardPlayModel.CURRENT
        assertThat(m.shared.size).isEqualTo(CardPlayModel.F)
        assertThat(m.shared.any { it != 0f }).isTrue()
        for (h in m.heroes) assertThat(data.catalogHero(h).id).isEqualTo(h)
        for (c in m.cards) assertThat(data.card(c).id).isEqualTo(c)
    }

    @Test
    fun `a guess over the hand sums to one and a hero the tables do not know adds nothing`() {
        val s = TestData.newGame()
        val ctx = ev.context(s.state)
        val hero = s.pending!!.seat
        val pool = s.state.hero(hero)!!.cards.filter { it.zone == Zone.HAND }.map { it.id }.sorted()
        val m = CardPlayModel.CURRENT
        val w = m.weights(static, ctx, hero, pool, 0.15)
        assertThat(w.sum()).isWithin(1e-9).of(1.0)
        assertThat(w.all { it >= 0.15 / pool.size - 1e-12 }).isTrue()
        // The same model without its tables: only the shared weights speak.
        val shared = CardPlayModel(m.shared, emptyList(), FloatArray(0), emptyList(), FloatArray(0))
        val phi = CardPlayModel.features(static, ctx, hero, pool)
        val turn = CardPlayModel.turnCentred(ctx)
        val z = shared.logits(static, ctx, hero, pool)
        for (i in pool.indices) assertThat(z[i]).isWithin(1e-9).of(m.logit(phi[i], null, null, turn))
    }
}
