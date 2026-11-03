package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.MinHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * ADR-035 point 3: the AI never reads hidden cards; ADR-062: strength 10 alone knows the cards in the
 * enemies' hands, at the owner's request. Two sessions that look the same to the deciding seat but differ
 * in the other team's hidden cards must get the same answer for the same seed (STRATEGY.md §4.5 "No
 * peeking"; ADR-039):
 * - the facedown played card and facedown tokens: every strength 1–10 and the coach;
 * - which cards are in hand (and so the items): strengths 1–9 and the coach; strength 10 reads them, and
 *   nothing else (the same hands with a different facedown card or token give it the same answer).
 */
class NoPeekingTest {
    private val everyStrength = AiStrength.MIN..AiStrength.MAX
    private val withoutHands = AiStrength.MIN until KNOWS_HANDS

    private fun assertSameChoice(a: EngineSession, b: EngineSession, strengths: IntRange = everyStrength) {
        val da = a.pending!!
        val db = b.pending!!
        assertThat(db.kind).isEqualTo(da.kind)
        assertThat(db.seat).isEqualTo(da.seat)
        assertThat(db.options).isEqualTo(da.options)
        // The test is only meaningful when the seat really sees the same game.
        assertThat(b.view(db.seat)).isEqualTo(a.view(da.seat))
        for (strength in strengths) {
            for (seed in 1L..3L) {
                val ca = AiPlayers.deterministic(strength, seed).choose(a, da)
                val cb = AiPlayers.deterministic(strength, seed).choose(b, db)
                assertThat(cb).isEqualTo(ca)
            }
        }
        // The coach's words too (ADR-051): the same public view, the same suggestions, line for line.
        for (seed in 1L..3L) {
            val sa = AiCoach(TestData.engine, seed, UNHURRIED).suggest(null, a, da)
            val sb = AiCoach(TestData.engine, seed, UNHURRIED).suggest(null, b, db)
            assertThat(sb).isEqualTo(sa)
        }
    }

    private companion object {
        const val UNHURRIED = 3_600_000L
        const val KNOWS_HANDS = 10
    }

    @Test
    fun `the facedown card an enemy played does not change the choice`() {
        val a = TestData.newGame()
        a.answer(0, Choice.Card("wasp-gold"))
        a.answer(1, Choice.Card("xargatha-red-1"))
        val b = TestData.newGame()
        b.answer(0, Choice.Card("wasp-blue-1"))
        b.answer(1, Choice.Card("xargatha-silver"))
        assertSameChoice(a, b)
    }

    /** Wasp at level 2 with the same hand, the upgraded card or a basic one played facedown. */
    @Test
    fun `strength 10 knows the hand but not which card of it was played`() {
        fun build(played: String) = position(TestData.engine, TestData.FOREST) {
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                val hand = listOf("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                played(played)
                hand(*(hand - played).toTypedArray())
                items("wasp-red-2b")
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            hero("arien", Team.BLUE, Hex(2, -3))
            hero("brogan", Team.BLUE, Hex(3, -3))
            phase = PositionPhase.PlayCards(fromSeat = 2)
        }
        assertSameChoice(build("wasp-red-2a"), build("wasp-gold"))
    }

    /** Min's two facedown Mines (a blast and a dud) on swapped spaces. */
    @Test
    fun `facedown tokens do not change the choice`() {
        fun build(blastFirst: Boolean) = position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") }
            hero("min", Team.RED, Hex(-1, -2)) { played("min-gold") }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            val (first, second) = if (blastFirst) MinHero.BLAST to MinHero.DUD else MinHero.DUD to MinHero.BLAST
            token(first, Hex(2, -2), owner = "min", team = Team.RED, faceDown = true)
            token(second, Hex(3, -2), owner = "min", team = Team.RED, faceDown = true)
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertSameChoice(build(blastFirst = true), build(blastFirst = false))
    }

    /** Wasp at level 2: Red upgraded (Stinger item, Defense icon) or Blue upgraded (Defense icon too). */
    private fun upgraded(redUpgrade: Boolean, block: PositionBuilder.() -> Unit = {}): EngineSession =
        position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3))
            hero("brogan", Team.BLUE, Hex(3, -3))
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                if (redUpgrade) {
                    hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                    items("wasp-red-2b")
                } else {
                    hand("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                    items("wasp-blue-2a")
                }
            }
            hero("xargatha", Team.RED, Hex(-1, -1))
            block()
        }

    @Test
    fun `which colour an enemy upgraded is hidden when the item icons match`() {
        val a = upgraded(redUpgrade = true)
        val b = upgraded(redUpgrade = false)
        assertThat(a.state.hero("wasp")!!.card("wasp-red-2a")!!.zone).isEqualTo(Zone.HAND)
        assertThat(b.state.hero("wasp")!!.card("wasp-red-2a")!!.zone).isEqualTo(Zone.DECK)
        assertSameChoice(a, b, withoutHands)
    }

    @Test
    fun `hidden hands do not change a choice after the reveal`() {
        val build: PositionBuilder.() -> Unit = {
            phase = PositionPhase.Resolving(next = "arien")
        }
        val a = position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                played("wasp-silver")
                hand("wasp-gold", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                items("wasp-red-2b")
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            build()
        }
        val b = position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                played("wasp-silver")
                hand("wasp-gold", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                items("wasp-blue-2a")
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            build()
        }
        assertSameChoice(a, b, withoutHands)
    }

    @Test
    fun `only strength 10 is told the enemies' hands`() {
        for (strength in everyStrength) {
            assertThat(AiPlayers.profile(strength).knowHands).isEqualTo(strength == KNOWS_HANDS)
        }
    }

    @Test
    fun `the determinizer keeps every public fact and only consistent upgrade histories`() {
        val a = upgraded(redUpgrade = true)
        val kept = HashSet<String>()
        for (seed in 1L..60L) {
            val det = a.determinize(Team.BLUE, seed) as EngineSession
            assertThat(det.view("arien")).isEqualTo(a.view("arien"))
            val w = det.state.hero("wasp")!!
            val hand = w.cards.filter { it.zone == Zone.HAND }.map { it.id }
            val items = w.cards.filter { it.zone == Zone.ITEM }.map { it.id }
            assertThat(hand).hasSize(5)
            assertThat(items).hasSize(1)
            // Exactly one Tier II card in hand, of the colour whose other Tier II card is the item.
            val tierTwo = hand.filter { it.contains("-2") }
            assertThat(tierTwo).hasSize(1)
            assertThat(items.single().substringBeforeLast('-')).isEqualTo(tierTwo.single().substringBeforeLast('-'))
            assertThat(
                TestData.engine.data.card(items.single()).item,
            ).isEqualTo(dev.quinton.goa.core.model.catalog.Stat.DEFENSE)
            kept += tierTwo.single()
        }
        // Both histories with a Defense item occur: Red (keep 2a) and Blue (keep 2b).
        assertThat(kept).containsAtLeast("wasp-red-2a", "wasp-blue-2b")
    }

    /**
     * With [Determinizer.Options.knowHands] the enemy's hand pool and items are the real ones; the
     * played card is still dealt again from the pool, so the copies of two games that differ only in
     * which card was played are the same, card for card.
     */
    @Test
    fun `knowing hands keeps the pool and still guesses the played card`() {
        fun build(played: String) = position(TestData.engine, TestData.FOREST) {
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                played(played)
                hand(
                    *(
                        listOf(
                            "wasp-gold",
                            "wasp-silver",
                            "wasp-blue-2b",
                            "wasp-green-1",
                            "wasp-red-1",
                        ) - played
                        ).toTypedArray(),
                )
                items("wasp-blue-2a")
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            hero("arien", Team.BLUE, Hex(2, -3))
            hero("brogan", Team.BLUE, Hex(3, -3))
            phase = PositionPhase.PlayCards(fromSeat = 2)
        }
        val a = build("wasp-blue-2b")
        val b = build("wasp-silver")
        val options = Determinizer.Options(knowHands = true)
        val playedSeen = HashSet<String>()
        for (seed in 1L..40L) {
            val da = a.determinizeWith(Team.BLUE, seed, options)
            val db = b.determinizeWith(Team.BLUE, seed, options)
            val wa = da.state.hero("wasp")!!
            val wb = db.state.hero("wasp")!!
            fun cards(h: dev.quinton.goa.core.game.engine.HeroState) =
                h.cards.map { Triple(it.id, it.zone, it.stamp) }
            assertThat(cards(wb)).isEqualTo(cards(wa))
            val pool = wa.cards.filter { it.zone == Zone.HAND || it.zone == Zone.PLAYED }.map { it.id }
            assertThat(pool).containsExactly("wasp-gold", "wasp-silver", "wasp-blue-2b", "wasp-green-1", "wasp-red-1")
            assertThat(wa.cards.filter { it.zone == Zone.ITEM }.map { it.id }).containsExactly("wasp-blue-2a")
            playedSeen += wa.cards.single { it.zone == Zone.PLAYED }.id
        }
        // The played card is a guess from the pool, not the real one.
        assertThat(playedSeen.size).isAtLeast(3)
        // Without the switch the kept Tier II card is guessed too.
        val guessed = (1L..40L).map { seed ->
            a.determinizeWith(Team.BLUE, seed, Determinizer.Options()).state.hero("wasp")!!
                .cards.filter { it.zone == Zone.HAND || it.zone == Zone.PLAYED }.map { it.id }.toSet()
        }.toSet()
        assertThat(guessed.size).isAtLeast(2)
    }
}
