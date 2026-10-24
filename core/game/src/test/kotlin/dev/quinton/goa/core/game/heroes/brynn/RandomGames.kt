package dev.quinton.goa.core.game.heroes.brynn

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
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/**
 * Random-legal full games seating one hero of batch 4 (Cutter, Trinkets, Hanu, Brynn) with the other
 * three and the four reference heroes, checking the PropertyTest invariants at every decision (one
 * obstacle per space, pieces on spaces, every card still owned, one played card, Life and Wave counters
 * only going down) and that every game ends with a winner.
 */
object RandomGames {
    private val reference = listOf("arien", "brogan", "wasp", "xargatha")
    private val batch = listOf("cutter", "trinkets", "hanu", "brynn")

    fun play(hero: String, games: Int, seed: Long) {
        val rng = Random(seed)
        val engine = ANY_HERO_ENGINE
        repeat(games) { i ->
            val others = (reference + batch.filter { it != hero }).shuffled(rng)
            val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
            val mine = if (rng.nextBoolean()) Team.RED else Team.BLUE
            val setup = when (rng.nextInt(4)) {
                0 -> GameSetup(
                    TestData.FOREST,
                    listOf(seat(hero, mine), seat(others[0], mine.opponent)),
                    tieBreaker = coin,
                )

                1 -> GameSetup(
                    TestData.RIVER,
                    listOf(
                        seat(hero, mine),
                        seat(others[0], mine),
                        seat(others[1], mine.opponent),
                        seat(others[2], mine.opponent),
                    ),
                    tieBreaker = coin,
                )

                2 -> GameSetup(
                    TestData.FOREST,
                    listOf(
                        seat(hero, mine),
                        seat(others[0], mine),
                        seat(others[1], mine.opponent),
                        seat(others[2], mine.opponent),
                    ),
                    length = GameLength.LONG,
                    tieBreaker = coin,
                )

                else -> GameSetup(
                    TestData.FOREST,
                    listOf(
                        seat(hero, mine),
                        seat(others[0], mine),
                        seat(others[1], mine.opponent),
                        seat(others[2], mine.opponent),
                    ),
                    tieBreaker = coin,
                )
            }
            val s = engine.newGame(setup) as EngineSession
            val life = LinkedHashMap(s.state.lifeCounters)
            val waves = LinkedHashMap(s.state.wavesLeft)
            val player = RandomPlayer(seed * 1000 + i)
            var n = 0
            while (true) {
                val d = s.pending ?: break
                check(s, life, waves)
                assertThat(d.options).isNotEmpty()
                assertThat(d.options.distinct()).hasSize(d.options.size)
                val r = s.answer(d.id, player.choose(d))
                check(r is AnswerResult.Accepted) { "Rejected $r" }
                n++
                check(n < 40_000) { "Game did not end: $setup" }
            }
            check(s, life, waves)
            assertWithMessage("game $i ($setup) has a winner").that(s.view(null).winner).isNotNull()
        }
    }

    private fun seat(id: String, team: Team) = SeatSetup(id, team, Controller.Human)

    private fun check(s: EngineSession, life: MutableMap<Team, Int>, waves: MutableMap<String, Int>) {
        val st = s.state
        val g = Game(ANY_HERO_ENGINE.data, st, emptyList(), RunMode.PROBE)
        g.occupancy() // throws on two obstacles in one space (p.18)
        for (h in st.heroes) {
            h.at?.let { assertWithMessage("${h.id} on a space").that(g.geo.isSpace(it)).isTrue() }
            assertThat(h.coins).isAtLeast(0)
            assertThat(h.level).isIn(1..8)
            val catalog = ANY_HERO_ENGINE.data.catalogHero(h.id).allCards.map { it.id }.sorted()
            assertWithMessage("cards of ${h.id}").that(h.cards.map { it.id }.sorted()).isEqualTo(catalog)
            assertThat(h.cards.count { it.zone == Zone.PLAYED }).isAtMost(1)
        }
        for (m in st.minions) m.at?.let { assertThat(g.geo.isSpace(it)).isTrue() }
        for (p in st.pieces) p.at?.let { assertWithMessage("${p.label} on a space").that(g.geo.isSpace(it)).isTrue() }
        for ((team, n) in st.lifeCounters) {
            assertWithMessage("Life counters only go down").that(n).isAtMost(life.getValue(team))
            life[team] = n
        }
        for ((lane, n) in st.wavesLeft) {
            assertWithMessage("Wave counters only go down").that(n).isAtMost(waves.getValue(lane))
            waves[lane] = n
        }
    }
}
