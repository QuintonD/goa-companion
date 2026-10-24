package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.json.Json
import org.junit.Test

class SessionTest {
    private val json = Json { allowStructuredMapKeys = true }

    private fun snap(s: EngineSession): String = json.encodeToString(GameState.serializer(), s.state)

    @Test
    fun `decision ids increase by one and stale or foreign answers are rejected without change`() {
        val s = TestData.newGame()
        val d0 = s.pending!!
        assertThat(d0.id).isEqualTo(0L)
        assertThat(s.answer(5, d0.options.first())).isInstanceOf(AnswerResult.Rejected::class.java)
        assertThat(s.answer(0, Choice.Card("brogan-gold"))).isInstanceOf(AnswerResult.Rejected::class.java)
        val r = s.answer(0, d0.options.first())
        assertThat(r).isInstanceOf(AnswerResult.Accepted::class.java)
        assertThat(s.pending!!.id).isEqualTo(1L)
    }

    @Test
    fun `save and restore mid-card and mid-defense give the same game`() {
        // Arien's Dangerous Current: a choice before the attack, then Wasp's defense.
        val d = pos(engine = TestData.engine) {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-1") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-gold") }
            hero("xargatha", Team.RED, Hex(-1, -2)) { hand("xargatha-gold", "xargatha-red-1") }
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-gold") }
            minion(Team.RED, MinionType.MELEE, Hex(-1, -1))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT) // the enemy hero behind the target (mid-card)
        val mid = TestData.engine.restore(d.s.save()) as EngineSession
        assertThat(snap(mid)).isEqualTo(snap(d.s))
        assertThat(mid.pending).isEqualTo(d.s.pending)
        d.unit("xargatha")
        d.expect(DecisionKind.CHOOSE_CARD, "xargatha")
        d.card("xargatha-gold")
        d.expect(DecisionKind.DEFEND, "wasp")
        val defense = TestData.engine.restore(d.s.save()) as EngineSession
        assertThat(defense.pending).isEqualTo(d.s.pending)
        assertThat(defense.events).isEqualTo(d.s.events)
        d.card("wasp-blue-1")
        defense.answer(defense.pending!!.id, Choice.Card("wasp-blue-1"))
        assertThat(snap(defense)).isEqualTo(snap(d.s))
    }

    @Test
    fun `a fork is independent of its parent`() {
        val s = TestData.newGame()
        val f = s.fork()
        f.answer(f.pending!!.id, Choice.Card("wasp-gold"))
        assertThat(s.pending!!.id).isEqualTo(0L)
        assertThat(s.state.hero("wasp")!!.card("wasp-gold")!!.zone).isEqualTo(Zone.HAND)
        s.answer(0, Choice.Card("wasp-red-1"))
        assertThat((f as EngineSession).state.hero("wasp")!!.card("wasp-gold")!!.zone).isEqualTo(Zone.PLAYED)
    }

    @Test
    fun `undo goes back to the last human decision`() {
        val setup = TestData.firstGame().let {
            it.copy(
                seats = it.seats.map { s ->
                    if (s.team ==
                        Team.BLUE
                    ) {
                        s.copy(controller = Controller.Ai(AiLevel.NORMAL))
                    } else {
                        s
                    }
                },
            )
        }
        val s = TestData.engine.newGame(setup) as EngineSession
        s.answer(0, Choice.Card("wasp-gold")) // human
        s.answer(1, Choice.Card("xargatha-gold")) // human
        s.answer(2, Choice.Card("arien-gold")) // ai
        val before = s.pending!!.id
        assertThat(before).isEqualTo(3L)
        assertThat(s.undoToLastHumanDecision()).isTrue()
        assertThat(s.pending!!.id).isEqualTo(1L)
        assertThat(s.pending!!.seat).isEqualTo("xargatha")
        assertThat(s.state.hero("xargatha")!!.card("xargatha-gold")!!.zone).isEqualTo(Zone.HAND)
    }

    @Test
    fun `determinize hides the other team's facedown card and keeps the viewer's own information`() {
        val s = TestData.newGame()
        s.answer(0, Choice.Card("wasp-gold"))
        s.answer(1, Choice.Card("xargatha-red-1"))
        // Blue is about to play: Red's played cards are hidden from Blue.
        val samples = (1L..40L).map { seed ->
            val det = s.determinize(Team.BLUE, seed) as EngineSession
            assertThat(det.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
            assertThat(det.pending!!.seat).isEqualTo("arien")
            assertThat(det.view("arien")).isEqualTo(s.view("arien"))
            det.state.hero("wasp")!!.unresolved.single().id
        }.toSet()
        assertThat(samples.size).isGreaterThan(1) // not always the real card
        // The real card is one of five equally likely; the sample does not depend on it.
        val other = TestData.newGame()
        other.answer(0, Choice.Card("wasp-silver"))
        other.answer(1, Choice.Card("xargatha-red-1"))
        for (seed in 1L..10L) {
            val a = s.determinize(Team.BLUE, seed) as EngineSession
            val b = other.determinize(Team.BLUE, seed) as EngineSession
            assertThat(snap(a)).isEqualTo(snap(b))
        }
    }
}
