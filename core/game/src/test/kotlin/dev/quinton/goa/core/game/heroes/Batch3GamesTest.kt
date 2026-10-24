package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
import org.junit.Test

/**
 * Random-legal full games seating Min, Rowenna, Tali and Swift (hero batch 3) against the four
 * reference heroes (brief "Done means" 3): no exception, the occupancy rule, cards and coins at
 * every decision, and every game ends with a winner.
 */
class Batch3GamesTest {
    private val engine = TestData.engine
    private val reference = listOf("arien", "brogan", "wasp", "xargatha")
    private val batch = listOf("min", "rowenna", "tali", "swift")

    private fun playable(id: String) = engine.data.registry.module(id)?.complete == true

    private fun setup(rng: Random, hero: String): GameSetup {
        val others = (reference + batch.filter { it != hero && playable(it) }).shuffled(rng)
        val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val seat = { id: String, team: Team, handicap: Boolean -> SeatSetup(id, team, Controller.Human, handicap) }
        return when (rng.nextInt(5)) {
            0 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, Team.RED, false), seat(others[0], Team.BLUE, false)),
                tieBreaker = coin,
            )

            1 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, Team.BLUE, true), seat(others[0], Team.BLUE, true), seat(others[1], Team.RED, false)),
                tieBreaker = coin,
            )

            2 -> GameSetup(
                TestData.RIVER,
                listOf(
                    seat(others[0], Team.RED, false),
                    seat(hero, Team.RED, false),
                    seat(others[1], Team.BLUE, false),
                    seat(others[2], Team.BLUE, false),
                ),
                tieBreaker = coin,
            )

            3 -> GameSetup(
                TestData.FOREST,
                listOf(
                    seat(hero, Team.BLUE, false),
                    seat(others[0], Team.BLUE, false),
                    seat(others[1], Team.RED, false),
                    seat(others[2], Team.RED, false),
                ),
                length = GameLength.LONG,
                tieBreaker = coin,
            )

            else -> GameSetup(
                TestData.FOREST,
                listOf(
                    seat(hero, Team.RED, false),
                    seat(others[0], Team.RED, false),
                    seat(others[1], Team.BLUE, false),
                    seat(others[2], Team.BLUE, false),
                ),
                tieBreaker = coin,
            )
        }
    }

    private fun check(s: EngineSession) {
        val st = s.state
        val g = Game(engine.data, st, emptyList(), RunMode.PROBE)
        g.occupancy() // throws on two obstacles in one space (p.18)
        for (h in st.heroes) {
            h.at?.let { assertWithMessage("${h.id} on a space").that(g.geo.isSpace(it)).isTrue() }
            assertThat(h.coins).isAtLeast(0)
            val catalog = engine.data.catalogHero(h.id).allCards.map { it.id }.sorted()
            assertWithMessage("cards of ${h.id}").that(h.cards.map { it.id }.sorted()).isEqualTo(catalog)
            assertThat(h.cards.count { it.zone == Zone.PLAYED }).isAtMost(1)
        }
        for (p in st.pieces) p.at?.let { assertWithMessage("${p.kind} on a space").that(g.geo.isSpace(it)).isTrue() }
    }

    private fun games(hero: String, n: Int, seed: Long) {
        if (!playable(hero)) return
        val rng = Random(seed)
        val start = System.nanoTime()
        var decisions = 0L
        var checkNanos = 0L
        repeat(n) { i ->
            val setup = setup(rng, hero)
            val s = engine.newGame(setup) as EngineSession
            val player = RandomPlayer(seed * 1000 + i)
            var k = 0
            while (true) {
                val d = s.pending ?: break
                check(s)
                if (k == 150 + 7 * i) { // once per game, at a different point each time
                    val t0 = System.nanoTime()
                    // Hidden information (Min's facedown mines among it) samples without breaking the game.
                    val det = s.determinize(d.team, k.toLong()) as EngineSession
                    check(det)
                    val restored = engine.restore(s.save()) as EngineSession
                    assertThat(restored.pending).isEqualTo(s.pending)
                    checkNanos += System.nanoTime() - t0
                }
                assertThat(d.options).isNotEmpty()
                assertThat(d.options.distinct()).hasSize(d.options.size)
                val r = s.answer(d.id, player.choose(d))
                assertWithMessage("answer rejected in $setup").that(r is AnswerResult.Accepted).isTrue()
                k++
                assertWithMessage("game did not end: $setup").that(k).isLessThan(30_000)
            }
            assertThat(s.view(null).winner).isNotNull()
            decisions += k
        }
        val secs = (System.nanoTime() - start) / 1e9
        val checks = checkNanos / 1e9
        println("BATCH3 $hero: $n games, $decisions decisions in ${"%.1f".format(secs)} s (checks ${"%.1f".format(checks)} s)")
    }

    @Test
    fun `Swift random games`() = games("swift", 200, 31)

    @Test
    fun `Rowenna random games`() = games("rowenna", 200, 32)

    @Test
    fun `Tali random games`() = games("tali", 200, 33)

    @Test
    fun `Min random games`() = games("min", 200, 34)
}
