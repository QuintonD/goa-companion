package dev.quinton.goa.core.game.heroes.gydion

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.RandomPlayer
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import kotlinx.serialization.json.Json

/**
 * Seeded random-legal full games seating a batch-8 hero (Gydion, Takahide) against the four reference
 * heroes (and sometimes the other batch-8 hero), with PropertyTest's invariants at every decision.
 * Gydion's Energy Drain regains spent Life counters, so for his team Life counters may go up, but never
 * above the starting count (EFFECTS.md "Known engine limits").
 */
object HeroRandomGames {
    private val engine = ANY_HERO_ENGINE
    private val others = listOf("wasp", "xargatha", "arien", "brogan")
    private val batch = listOf("gydion", "takahide")
    private val json = Json { allowStructuredMapKeys = true }

    private fun seat(id: String, team: Team, handicap: Boolean = false) =
        SeatSetup(id, team, Controller.Human, handicap)

    private fun setup(hero: String, rng: Random): GameSetup {
        val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val pool = (others + (batch - hero)).shuffled(rng)
        val mine = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val other = mine.opponent
        fun line(n: Int): List<SeatSetup> {
            val half = n / 2
            val seats = listOf(seat(hero, mine)) + pool.take(half - 1).map { seat(it, mine) } +
                pool.drop(half - 1).take(half).map { seat(it, other) }
            return seats.sortedBy { if (it.team == Team.RED) 0 else 1 }
        }
        return when (rng.nextInt(5)) {
            0 -> GameSetup(TestData.FOREST, line(2), tieBreaker = coin)

            1 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, mine, handicap = true), seat(pool[0], mine, handicap = true), seat(pool[1], other))
                    .sortedBy { if (it.team == Team.RED) 0 else 1 },
                tieBreaker = coin,
            )

            2 -> GameSetup(TestData.FOREST, line(4), length = GameLength.LONG, tieBreaker = coin)

            3 -> GameSetup(TestData.RIVER, line(4), tieBreaker = coin)

            else -> GameSetup(TestData.FOREST, line(4), tieBreaker = coin)
        }
    }

    private class Watch(val life: MutableMap<Team, Int>, val start: Map<Team, Int>, val waves: MutableMap<String, Int>)

    private fun invariants(s: EngineSession, w: Watch, extra: (GameState) -> Unit) {
        val st = s.state
        val g = Game(engine.data, st, emptyList(), RunMode.PROBE)
        g.occupancy() // one obstacle per space (p.18)
        for (h in st.heroes) {
            h.at?.let { assertWithMessage("${h.id} on a space").that(g.geo.isSpace(it)).isTrue() }
            assertThat(h.coins).isAtLeast(0)
            assertThat(h.level).isIn(1..8)
            val catalog = engine.data.catalogHero(h.id).allCards.map { it.id }.sorted()
            assertWithMessage("cards of ${h.id}").that(h.cards.map { it.id }.sorted()).isEqualTo(catalog)
            assertThat(h.cards.count { it.zone == Zone.PLAYED }).isAtMost(1)
            assertThat(h.cards.count { it.zone == Zone.ULTIMATE }).isAtMost(1)
        }
        for (m in st.minions) m.at?.let { assertThat(g.geo.isSpace(it)).isTrue() }
        val gydionTeam = st.hero("gydion")?.team
        for ((team, n) in st.lifeCounters) {
            if (team == gydionTeam) {
                assertWithMessage("Life counters never above the start").that(n).isAtMost(w.start.getValue(team))
            } else {
                assertWithMessage("Life counters only go down").that(n).isAtMost(w.life.getValue(team))
            }
            w.life[team] = n
        }
        for ((lane, n) in st.wavesLeft) {
            assertWithMessage("Wave counters only go down").that(n).isAtMost(w.waves.getValue(lane))
            w.waves[lane] = n
        }
        extra(st)
    }

    /**
     * Plays [games] games with [hero] seated; [extra] adds hero-specific invariants. Every 10th game is
     * saved and restored at a random point and must replay to the same state.
     */
    fun run(hero: String, games: Int, seed: Long, extra: (GameState, Int) -> Unit) {
        val rng = Random(seed)
        var ended = 0
        repeat(games) { i ->
            val s = engine.newGame(setup(hero, rng)) as EngineSession
            val life = s.state.lifeCounters
            val w = Watch(LinkedHashMap(life), LinkedHashMap(life), LinkedHashMap(s.state.wavesLeft))
            val player = RandomPlayer(seed * 1000 + i)
            val cut = if (i % 10 == 0) rng.nextInt(20, 600) else -1
            var n = 0
            while (true) {
                val d = s.pending ?: break
                invariants(s, w) { extra(it, n) }
                assertThat(d.options).isNotEmpty()
                assertThat(d.options.distinct()).hasSize(d.options.size)
                if (n == cut) {
                    val restored = engine.restore(s.save()) as EngineSession
                    assertThat(json.encodeToString(GameState.serializer(), restored.state))
                        .isEqualTo(json.encodeToString(GameState.serializer(), s.state))
                    assertThat(restored.pending).isEqualTo(s.pending)
                }
                val r = s.answer(d.id, player.choose(d))
                check(r is AnswerResult.Accepted) { "Rejected $r" }
                n++
                check(n < 30_000) { "Game did not end" }
            }
            invariants(s, w) { extra(it, n) }
            if (s.view(null).winner != null) ended++
        }
        assertThat(ended).isEqualTo(games)
    }
}
