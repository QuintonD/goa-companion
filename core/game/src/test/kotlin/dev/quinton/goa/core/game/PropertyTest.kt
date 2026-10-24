package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Thousands of random-legal full games over the playable heroes (each hero at most once per game:
 * 1v1, 2v1 with Handicap cards, 2v2 Quick and Long, and the double-lane map), checking invariants at
 * every decision, and save/restore, fork and determinize equivalence at random points.
 */
class PropertyTest {
    private val engine = TestData.engine
    private val json = Json { allowStructuredMapKeys = true }
    private val heroes = listOf("wasp", "xargatha", "arien", "brogan")

    private fun seat(id: String, team: Team, handicap: Boolean = false) = SeatSetup(
        id,
        team,
        Controller.Human,
        handicap,
    )

    private fun setups(rng: Random): GameSetup {
        val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val shuffled = heroes.shuffled(rng)
        return when (rng.nextInt(6)) {
            0 -> GameSetup(
                TestData.FOREST,
                listOf(seat(shuffled[0], Team.RED), seat(shuffled[1], Team.BLUE)),
                tieBreaker = coin,
            )

            1 -> GameSetup(
                TestData.FOREST,
                listOf(
                    seat(shuffled[0], Team.RED, true),
                    seat(shuffled[1], Team.RED, true),
                    seat(shuffled[2], Team.BLUE),
                ),
                tieBreaker = coin,
            )

            2 -> GameSetup(
                TestData.FOREST,
                listOf(
                    seat(shuffled[0], Team.RED),
                    seat(shuffled[1], Team.RED),
                    seat(shuffled[2], Team.BLUE),
                    seat(shuffled[3], Team.BLUE),
                ),
                length = GameLength.LONG,
                tieBreaker = coin,
            )

            3 -> GameSetup(
                TestData.RIVER,
                listOf(
                    seat(shuffled[0], Team.RED),
                    seat(shuffled[1], Team.RED),
                    seat(shuffled[2], Team.BLUE),
                    seat(shuffled[3], Team.BLUE),
                ),
                tieBreaker = coin,
            )

            else -> GameSetup(
                TestData.FOREST,
                listOf(
                    seat(shuffled[0], Team.RED),
                    seat(shuffled[1], Team.RED),
                    seat(shuffled[2], Team.BLUE),
                    seat(shuffled[3], Team.BLUE),
                ),
                tieBreaker = coin,
            )
        }
    }

    private class Watch(val life: MutableMap<Team, Int>, val waves: MutableMap<String, Int>)

    private fun checkInvariants(s: EngineSession, w: Watch) {
        val st = s.state
        val g = Game(engine.data, st, emptyList(), RunMode.PROBE)
        g.occupancy() // throws on two obstacles in one space (p.18)
        for (h in st.heroes) {
            h.at?.let { assertWithMessage("${h.id} on a space").that(g.geo.isSpace(it)).isTrue() }
            assertThat(h.coins).isAtLeast(0)
            assertThat(h.level).isIn(1..8)
            val catalog = engine.data.catalogHero(h.id).allCards.map { it.id }.sorted()
            assertWithMessage("cards of ${h.id}").that(h.cards.map { it.id }.sorted()).isEqualTo(catalog)
            // Emmitt's Ultimate plays two cards before the reveal (EFFECTS.md "Known engine limits").
            assertThat(h.cards.count { it.zone == Zone.PLAYED }).isAtMost(if (h.id == "emmitt") 2 else 1)
            assertThat(h.cards.count { it.zone == Zone.ULTIMATE }).isAtMost(1)
        }
        for (m in st.minions) m.at?.let { assertThat(g.geo.isSpace(it)).isTrue() }
        for ((team, n) in st.lifeCounters) {
            assertWithMessage("Life counters only go down").that(n).isAtMost(w.life.getValue(team))
            w.life[team] = n
        }
        for ((lane, n) in st.wavesLeft) {
            assertWithMessage("Wave counters only go down").that(n).isAtMost(w.waves.getValue(lane))
            w.waves[lane] = n
        }
    }

    private fun snap(s: EngineSession) = json.encodeToString(GameState.serializer(), s.state)

    @Test
    fun `random legal games keep every invariant and always end with a winner`() {
        val rng = Random(2026)
        var games = 0
        var decisions = 0L
        val reasons = HashMap<WinReason, Int>()
        val start = System.nanoTime()
        repeat(1200) { i ->
            val setup = setups(rng)
            val s = engine.newGame(setup) as EngineSession
            val w = Watch(LinkedHashMap(s.state.lifeCounters), LinkedHashMap(s.state.wavesLeft))
            val player = RandomPlayer(i.toLong())
            var n = 0
            while (true) {
                val d = s.pending ?: break
                checkInvariants(s, w)
                assertThat(d.options).isNotEmpty()
                assertThat(d.options.distinct()).hasSize(d.options.size)
                val r = s.answer(d.id, player.choose(d))
                check(r is AnswerResult.Accepted) { "Rejected $r" }
                n++
                check(n < 30_000) { "Game did not end: $setup" }
            }
            checkInvariants(s, w)
            val v = s.view(null)
            assertThat(v.winner).isNotNull()
            reasons.merge(v.winReason!!, 1, Int::plus)
            games++
            decisions += n
        }
        val seconds = (System.nanoTime() - start) / 1e9
        println("PROPERTY: $games games, $decisions decisions in ${"%.1f".format(seconds)} s; wins by $reasons")
    }

    @Test
    fun `the same answers give the same game, and save and restore or fork at any point change nothing`() {
        val rng = Random(99)
        repeat(60) { i ->
            val setup = setups(rng)
            val a = engine.newGame(setup) as EngineSession
            val b = engine.newGame(setup) as EngineSession
            val pa = RandomPlayer(500L + i)
            val pb = RandomPlayer(500L + i)
            val cut = rng.nextInt(10, 400)
            var n = 0
            var restored: EngineSession? = null
            var forked: EngineSession? = null
            val forkAnswers = ArrayList<Choice>()
            while (a.pending != null) {
                if (n == cut) {
                    restored = engine.restore(a.save()) as EngineSession
                    assertThat(snap(restored)).isEqualTo(snap(a))
                    assertThat(restored.pending).isEqualTo(a.pending)
                    forked = a.forkSession()
                }
                val ca = pa.choose(a.pending!!)
                val cb = pb.choose(b.pending!!)
                assertThat(cb).isEqualTo(ca)
                a.answer(a.pending!!.id, ca)
                b.answer(b.pending!!.id, cb)
                if (n >= cut) {
                    restored!!.answer(restored.pending!!.id, ca)
                    forkAnswers += ca
                }
                n++
            }
            assertThat(b.events.map { it.text }).isEqualTo(a.events.map { it.text })
            if (restored != null) {
                assertThat(snap(restored)).isEqualTo(snap(a))
                // The fork was left alone while the parent played on; now replay the same answers on it.
                for (c in forkAnswers) forked!!.answer(forked.pending!!.id, c)
                assertThat(snap(forked!!)).isEqualTo(snap(a))
            }
        }
    }

    @Test
    fun `determinize keeps the viewer's view and never depends on the hidden cards`() {
        val rng = Random(7)
        repeat(80) { i ->
            val s = engine.newGame(setups(rng)) as EngineSession
            val p = RandomPlayer(900L + i)
            val stop = rng.nextInt(1, 300)
            var n = 0
            while (s.pending != null && n < stop) {
                s.answer(s.pending!!.id, p.choose(s.pending!!))
                n++
            }
            val d = s.pending ?: return@repeat
            val team = d.team
            val det = s.determinize(team, i.toLong()) as EngineSession
            val pend = det.pending
            if (pend != null) assertThat(pend.kind).isEqualTo(d.kind)
            for (h in s.state.heroes.filter { it.team == team }) {
                val mine = s.view(h.id).heroes.filter { it.team == team }
                assertThat(det.view(h.id).heroes.filter { it.team == team }).isEqualTo(mine)
            }
            // Public information of the other team is the same.
            val other = s.view(d.seat).heroes.filter { it.team != team }.map { it.copy(played = null) }
            val otherDet = det.view(d.seat).heroes.filter { it.team != team }.map { it.copy(played = null) }
            assertThat(otherDet).isEqualTo(other)
        }
    }
}
