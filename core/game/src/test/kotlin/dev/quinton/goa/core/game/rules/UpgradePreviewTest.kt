package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * ADR-045: the level up's first question (p.15 "choose a … card … with the lowest card Tier") carries,
 * per option, the two cards of the next Tier it would become (`DecisionContext.upgrades`), so the
 * player sees them before choosing the colour. The preview is the engine's own selection: it must be
 * exactly what KEEP_UPGRADE then offers.
 */
class UpgradePreviewTest {
    private val engine = TestData.engine
    private val geo = engine.data.geometry(TestData.FOREST)

    /** [heroId] at level 1 with the coins of every level (28, p.15): seven level ups in one round. */
    private fun allLevels(heroId: String) = pos(engine = engine) {
        hero(heroId, Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 28 }
        hero(if (heroId == "arien") "wasp" else "arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
        phase = PositionPhase.EndOfRound
    }

    private fun levelling(d: Decision?, heroId: String) =
        d != null && d.seat == heroId && (d.kind == DecisionKind.UPGRADE_CARD || d.kind == DecisionKind.KEEP_UPGRADE)

    @Test
    fun `every playable hero - the preview of each option is what Keep then offers, through all seven levels`() {
        for (heroId in engine.playableHeroes) {
            val d = allLevels(heroId)
            var asked = 0
            while (levelling(d.pending, heroId)) {
                val q = d.d
                if (q.kind == DecisionKind.UPGRADE_CARD) {
                    asked++
                    val options = q.options.map { (it as Choice.Card).cardId }
                    assertThat(q.context.upgrades.keys).containsExactlyElementsIn(options)
                    for (option in options) checkOption(d.s, q, option, heroId)
                }
                d.answer(q.options.first())
            }
            // Levels 2–3 and 5–6 ask (three, then two Tier I / II cards); 4 and 7 have one card left;
            // 8 asks nothing.
            assertWithMessage(heroId).that(asked).isEqualTo(4)
            val h = d.hero(heroId)
            assertWithMessage(heroId).that(h.level).isEqualTo(8)
            assertWithMessage(heroId).that(h.cards.count { it.zone == Zone.ULTIMATE }).isEqualTo(1)
        }
    }

    /** Answers [option] on a fork and checks the next question against its preview. */
    private fun checkOption(s: EngineSession, q: Decision, option: String, heroId: String) {
        val preview = q.context.upgrades.getValue(option)
        val card = engine.data.card(option)
        // Every shipped deck has two of each colour per Tier (DOMAIN "Deck").
        assertWithMessage("$heroId $option").that(preview).hasSize(2)
        for (id in preview) {
            val next = engine.data.card(id)
            assertThat(next.color).isEqualTo(card.color)
            assertThat(next.tier).isEqualTo(card.tier!! + 1)
            assertThat(next.color).isIn(listOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE))
        }
        val fork = s.forkSession()
        fork.answer(q.id, Choice.Card(option))
        val keep = fork.pending!!
        assertWithMessage("$heroId $option").that(keep.kind).isEqualTo(DecisionKind.KEEP_UPGRADE)
        assertThat(keep.seat).isEqualTo(heroId)
        assertThat(keep.options).containsExactlyElementsIn(preview.map { Choice.Card(it) }).inOrder()
    }

    @Test
    fun `two levels in one round - the second question previews from the deck as the first left it`() {
        val d = pos(engine = engine) {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 3 } // 1 + 2: level 3
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.EndOfRound
        }
        val first = d.expect(DecisionKind.UPGRADE_CARD, "wasp")
        assertThat(first.context.upgrades).isEqualTo(
            mapOf(
                "wasp-red-1" to listOf("wasp-red-2a", "wasp-red-2b"),
                "wasp-green-1" to listOf("wasp-green-2a", "wasp-green-2b"),
                "wasp-blue-1" to listOf("wasp-blue-2a", "wasp-blue-2b"),
            ),
        )
        d.card("wasp-green-1")
        d.expect(DecisionKind.KEEP_UPGRADE, "wasp")
        d.card("wasp-green-2b")
        val second = d.expect(DecisionKind.UPGRADE_CARD, "wasp")
        assertThat(second.context.upgrades).isEqualTo(
            mapOf(
                "wasp-red-1" to listOf("wasp-red-2a", "wasp-red-2b"),
                "wasp-blue-1" to listOf("wasp-blue-2a", "wasp-blue-2b"),
            ),
        )
    }

    @Test
    fun `two human heroes level up - each question previews only its own hero's deck`() {
        val d = pos(engine = engine) {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 1 }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0]) { coins = 1 }
            phase = PositionPhase.EndOfRound
        }
        val wasp = d.expect(DecisionKind.UPGRADE_CARD, "wasp")
        assertThat(wasp.context.upgrades.values.flatten().all { it.startsWith("wasp-") }).isTrue()
        d.card("wasp-red-1")
        d.card("wasp-red-2a")
        val arien = d.expect(DecisionKind.UPGRADE_CARD, "arien")
        assertThat(arien.context.upgrades.keys).containsExactly("arien-red-1", "arien-green-1", "arien-blue-1")
        assertThat(arien.context.upgrades.values.flatten().all { it.startsWith("arien-") }).isTrue()
    }

    @Test
    fun `a pair already short in the deck - the preview still says exactly what Keep does`() {
        // A built position (no shipped card does this at Tier I): one of Min's red Tier II cards is
        // already an item, so upgrading red has one card left: it goes to hand, nothing is asked.
        val d = pos(engine = engine) {
            hero("min", Team.RED, geo.heroSpawns(Team.RED)[0]) {
                coins = 1
                items("min-red-2a")
                hand("min-red-1", exactly = false)
            }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.EndOfRound
        }
        val q = d.expect(DecisionKind.UPGRADE_CARD, "min")
        assertThat(q.context.upgrades.getValue("min-red-1")).containsExactly("min-red-2b")
        assertThat(q.context.upgrades.getValue("min-green-1")).containsExactly("min-green-2a", "min-green-2b").inOrder()
        d.card("min-red-1")
        assertThat(d.zoneOf("min", "min-red-2b")).isEqualTo(Zone.HAND)
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.KEEP_UPGRADE)
    }

    /** Wasp at level 2 after upgrading [colour] (kept its 2b, the 2a an Initiative item), about to level again. */
    private fun waspUpgraded(colour: String) = pos(engine = engine) {
        hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) {
            level = 2
            coins = 2
            hand("wasp-$colour-2b", exactly = false)
            items("wasp-$colour-2a")
        }
        hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
        phase = PositionPhase.EndOfRound
    }

    @Test
    fun `no peeking - an enemy's samples preview the same whatever Wasp's hidden upgrade history`() {
        // Red 2a and green 2a are both Initiative items: which colour Wasp upgraded is hidden (her hand
        // is, and only the item icon is public, BGG 3590819). Her next question differs (real options
        // and previews), but every sample an enemy (the computer, the coach) draws is the same.
        val green = waspUpgraded("green")
        val red = waspUpgraded("red")
        assertThat(green.expect(DecisionKind.UPGRADE_CARD, "wasp").context.upgrades.keys)
            .containsExactly("wasp-red-1", "wasp-blue-1")
        assertThat(red.expect(DecisionKind.UPGRADE_CARD, "wasp").context.upgrades.keys)
            .containsExactly("wasp-green-1", "wasp-blue-1")
        for (seed in 1L..12L) {
            val a = green.s.determinize(Team.BLUE, seed).pending!!
            val b = red.s.determinize(Team.BLUE, seed).pending!!
            assertThat(a.kind).isEqualTo(DecisionKind.UPGRADE_CARD)
            assertThat(a.options).isEqualTo(b.options)
            assertThat(a.context.upgrades).isEqualTo(b.context.upgrades)
            // Within a sample, the preview is that sample's own deck: what its Keep question offers.
            for ((option, preview) in a.context.upgrades) {
                val fork = green.s.determinize(Team.BLUE, seed) as EngineSession
                fork.answer(a.id, Choice.Card(option))
                assertThat(fork.pending!!.options).containsExactlyElementsIn(preview.map { Choice.Card(it) }).inOrder()
            }
        }
    }

    @Test
    fun `other questions carry no preview`() {
        val d = pos(engine = engine) {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 1 }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.EndOfRound
        }
        d.card("wasp-blue-1")
        assertThat(d.expect(DecisionKind.KEEP_UPGRADE, "wasp").context.upgrades).isEmpty()
        d.card("wasp-blue-2a")
        // Round 2 begins: the play question has no preview either.
        assertThat(d.d.context.upgrades).isEmpty()
    }
}
