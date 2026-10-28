package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.discard
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.swapCards
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The structured fields the resolution flow reads (ADR-046): `CardsChanged.cards` / `discarded` name
 * only cards everyone may see (p.4 "Hidden Information"), `Defended.attackerId` names the attacker.
 * Additive with defaults: old JSON decodes, saves replay with the new fields filled.
 */
class ResolutionEventsTest {
    private val json = Json { allowStructuredMapKeys = true }

    private fun cardName(id: String): String = TestData.catalog.heroes.flatMap {
        it.allCards
    }.first { it.id == id }.name

    @Test
    fun `a defense card and a forced discard are named as cards, with the attacker`() {
        // Tigerclaw's Parry: "Block a non-ranged attack. The attacker discards a card, if able."
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { hand("tigerclaw-blue-2b") }
            hero("arien", Team.RED, Hex(2, -2)) {
                played("arien-gold")
                hand("arien-blue-1", "arien-red-1")
            }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("tigerclaw-blue-2b")
        d.card("arien-red-1")
        val changes = d.s.events.filterIsInstance<GameEvent.CardsChanged>()
        val parry = changes.single { it.heroId == "tigerclaw" }
        assertThat(parry.cards).containsExactly("tigerclaw-blue-2b")
        assertThat(parry.discarded).containsExactly("tigerclaw-blue-2b")
        val forced = changes.single { it.heroId == "arien" }
        assertThat(forced.cards).containsExactly("arien-red-1")
        assertThat(forced.discarded).containsExactly("arien-red-1")
        val defended = d.s.events.filterIsInstance<GameEvent.Defended>().single()
        assertThat(defended.attackerId).isEqualTo("arien")
        assertThat(defended.cardId).isEqualTo("tigerclaw-blue-2b")
    }

    @Test
    fun `a facedown card discarded or retrieved is never named, a faceup one is`() {
        val s = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) {
                resolved(1, "wasp-red-1", faceDown = true)
                resolved(2, "wasp-green-1")
                discard("wasp-blue-1")
                hand("wasp-gold")
            }
            hero("arien", Team.BLUE, Hex(4, -5))
        }
        val g = Game(TestData.engine.data, s.state.copy(), emptyList())
        g.discard("wasp", "wasp-red-1")
        g.discard("wasp", "wasp-green-1")
        g.retrieve("wasp", "wasp-blue-1")
        g.retrieve("wasp", "wasp-red-1")
        val events = g.events.filterIsInstance<GameEvent.CardsChanged>()
        assertThat(events.map { it.text }).containsExactly(
            "Wasp discards a facedown card.",
            "Wasp discards ${cardName("wasp-green-1")}.",
            "Wasp retrieves ${cardName("wasp-blue-1")}.",
            "Wasp retrieves a facedown card.",
        ).inOrder()
        assertThat(events.map { it.cards }).containsExactly(
            emptyList<String>(),
            listOf("wasp-green-1"),
            listOf("wasp-blue-1"),
            emptyList<String>(),
        ).inOrder()
        assertThat(events.map { it.discarded }).containsExactly(
            emptyList<String>(),
            listOf("wasp-green-1"),
            emptyList<String>(),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun `a swap names only the card that is not hidden`() {
        val s = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) {
                discard("wasp-blue-1")
                hand("wasp-gold")
            }
            hero("arien", Team.BLUE, Hex(4, -5))
        }
        val g = Game(TestData.engine.data, s.state.copy(), emptyList())
        // The hand card goes to the discard pile faceup (named), the discarded one to the hand: it was
        // public before the swap, so the line names it too (RULINGS 51).
        g.swapCards("wasp", "wasp-gold", "wasp-blue-1")
        val swap = g.events.filterIsInstance<GameEvent.CardsChanged>().single()
        assertThat(swap.cards).containsExactly("wasp-gold", "wasp-blue-1").inOrder()
        assertThat(swap.discarded).isEmpty()
    }

    @Test
    fun `old event JSON without the new fields decodes with their defaults`() {
        val old = """[
            {"type":"cards","heroId":"wasp","text":"Wasp discards Shock."},
            {"type":"defended","heroId":"wasp","cardId":"wasp-blue-1","total":5,"success":true,"text":"Wasp defends."}
        ]"""
        val events = json.decodeFromString(ListSerializer(GameEvent.serializer()), old)
        assertThat((events[0] as GameEvent.CardsChanged).cards).isEmpty()
        assertThat((events[0] as GameEvent.CardsChanged).discarded).isEmpty()
        assertThat((events[1] as GameEvent.Defended).attackerId).isNull()
        val again = json.decodeFromString(
            ListSerializer(GameEvent.serializer()),
            json.encodeToString(ListSerializer(GameEvent.serializer()), events),
        )
        assertThat(again).isEqualTo(events)
    }

    /**
     * Random 3v3 games on the real engine: every named card is in its sentence (which never names a
     * hidden card, RULINGS 51/53), discards are among the named cards, every defense names its
     * attacker right after that attacker's attack, and a save replays to the same events.
     */
    @Test
    fun `random 3v3 games - named cards are in the sentence, defenses follow their attack, saves replay`() {
        val lineups = listOf(
            listOf("tigerclaw", "wasp", "gydion") to listOf("takahide", "arien", "brogan"),
            listOf("bain", "misa", "sabina") to listOf("trinkets", "silverarrow", "nebkher"),
        )
        var changes = 0
        var defenses = 0
        lineups.forEachIndexed { i, (red, blue) ->
            val setup = GameSetup(
                TestData.FOREST,
                red.map { SeatSetup(it, Team.RED, Controller.Human) } +
                    blue.map { SeatSetup(it, Team.BLUE, Controller.Human) },
                length = GameLength.QUICK,
                tieBreaker = Team.RED,
            )
            val s = TestData.engine.newGame(setup) as EngineSession
            playOut(s, RandomPlayer(SEED + i), maxDecisions = MAX_DECISIONS)
            s.events.forEachIndexed { at, e ->
                when (e) {
                    is GameEvent.CardsChanged -> {
                        changes++
                        e.cards.forEach { id ->
                            assertWithMessage("${e.text} names $id").that(e.text).contains(cardName(id))
                        }
                        assertThat(e.cards).containsAtLeastElementsIn(e.discarded)
                    }

                    is GameEvent.Defended -> {
                        defenses++
                        val attacker = e.attackerId
                        assertWithMessage(e.text).that(attacker).isNotNull()
                        val attack = s.events.subList(0, at).lastOrNull { it is GameEvent.Attacked }
                        assertWithMessage(e.text).that((attack as GameEvent.Attacked).attackerId).isEqualTo(attacker)
                    }

                    else -> Unit
                }
            }
            val restored = TestData.engine.restore(s.save()) as EngineSession
            assertThat(restored.events).isEqualTo(s.events)
        }
        assertThat(changes).isGreaterThan(0)
        assertThat(defenses).isGreaterThan(0)
    }

    private companion object {
        const val SEED = 46L
        const val MAX_DECISIONS = 3_000
    }
}
