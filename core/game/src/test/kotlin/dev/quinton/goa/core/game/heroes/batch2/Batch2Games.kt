package dev.quinton.goa.core.game.heroes.batch2

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
 * Seeded random-legal full games seating one batch-2 hero (Whisper, Ursafar, Silverarrow, Bain) against
 * the four reference heroes (hero-batch brief, "Done means" 3): invariants at every decision, and
 * save/restore equivalence at a random cut of some games. The same checks as `PropertyTest`.
 */
object Batch2Games {
    private val engine = ANY_HERO_ENGINE
    private val json = Json { allowStructuredMapKeys = true }
    private val reference = listOf("wasp", "xargatha", "arien", "brogan")

    private fun seat(id: String, team: Team, handicap: Boolean = false) = SeatSetup(
        id,
        team,
        Controller.Human,
        handicap,
    )

    private fun setup(hero: String, rng: Random, others: List<String>): GameSetup {
        val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val o = (reference + others).filter { it != hero }.shuffled(rng)
        val mine = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val theirs = if (mine == Team.RED) Team.BLUE else Team.RED
        return when (rng.nextInt(5)) {
            0 -> GameSetup(TestData.FOREST, listOf(seat(hero, mine), seat(o[0], theirs)), tieBreaker = coin)

            1 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, mine, true), seat(o[0], mine, true), seat(o[1], theirs)),
                tieBreaker = coin,
            )

            2 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, mine), seat(o[0], mine), seat(o[1], theirs), seat(o[2], theirs)),
                length = GameLength.LONG,
                tieBreaker = coin,
            )

            3 -> GameSetup(
                TestData.RIVER,
                listOf(seat(hero, mine), seat(o[0], mine), seat(o[1], theirs), seat(o[2], theirs)),
                tieBreaker = coin,
            )

            else -> GameSetup(
                TestData.FOREST,
                listOf(seat(o[0], theirs), seat(hero, mine), seat(o[1], mine), seat(o[2], theirs)),
                tieBreaker = coin,
            )
        }
    }

    private class Watch(val life: MutableMap<Team, Int>, val waves: MutableMap<String, Int>)

    private fun check(s: EngineSession, w: Watch) {
        val st = s.state
        val g = Game(engine.data, st, emptyList(), RunMode.PROBE)
        g.occupancy() // throws on two obstacles in one space (p.18)
        for (h in st.heroes) {
            h.at?.let { assertWithMessage("${h.id} on a space").that(g.geo.isSpace(it)).isTrue() }
            assertThat(h.coins).isAtLeast(0)
            assertThat(h.level).isIn(1..8)
            val catalog = engine.data.catalogHero(h.id).allCards.map { it.id }.sorted()
            assertWithMessage("cards of ${h.id}").that(h.cards.map { it.id }.sorted()).isEqualTo(catalog)
            assertThat(h.cards.count { it.zone == Zone.PLAYED }).isAtMost(1)
            assertThat(h.cards.count { it.zone == Zone.ULTIMATE }).isAtMost(1)
        }
        assertWithMessage("one Bounty marker").that(
            st.heroes.sumOf { h ->
                h.markers.count { it.kind == "marker_bounty" }
            },
        )
            .isAtMost(1)
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

    /**
     * Play [games] seeded random games with [hero] seated (opponents and allies from the reference
     * heroes plus [others]); every 10th game is saved and restored at a random cut and replayed.
     * Returns the number of decisions answered.
     */
    fun play(hero: String, games: Int, seed: Long, others: List<String> = emptyList()): Long {
        val rng = Random(seed)
        var decisions = 0L
        repeat(games) { i ->
            val su = setup(hero, rng, others)
            val s = engine.newGame(su) as EngineSession
            val w = Watch(LinkedHashMap(s.state.lifeCounters), LinkedHashMap(s.state.wavesLeft))
            val player = RandomPlayer(seed * 1000 + i)
            val cut = if (i % 10 == 0) rng.nextInt(20, 600) else -1
            var restored: EngineSession? = null
            var n = 0
            while (true) {
                val d = s.pending ?: break
                check(s, w)
                assertThat(d.options).isNotEmpty()
                assertThat(d.options.distinct()).hasSize(d.options.size)
                if (n == cut) {
                    restored = engine.restore(s.save()) as EngineSession
                    assertThat(snap(restored)).isEqualTo(snap(s))
                }
                val c = player.choose(d)
                val r = s.answer(d.id, c)
                check(r is AnswerResult.Accepted) { "Rejected $r for $d in $su" }
                restored?.let { it.answer(it.pending!!.id, c) }
                n++
                check(n < 40_000) { "Game did not end: $su" }
            }
            check(s, w)
            assertWithMessage("winner of $su").that(s.view(null).winner).isNotNull()
            restored?.let { assertThat(snap(it)).isEqualTo(snap(s)) }
            decisions += n
        }
        return decisions
    }
}
