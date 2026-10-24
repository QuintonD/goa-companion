package dev.quinton.goa.core.game.heroes.wuk

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
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import kotlinx.serialization.json.Json

/**
 * Seeded random-legal full games seating one hero under test against the four reference heroes
 * (brief "Done means" 3), with invariants checked at every decision and save/restore equivalence.
 * Shared by the Wuk, Mrak and Mortimer tests. Some games start at level 8 with the hero's Ultimate
 * unlocked, so Ultimates are played too.
 */
object HeroGames {
    private val others = listOf("arien", "brogan", "wasp", "xargatha")
    private val engine = ANY_HERO_ENGINE
    private val json = Json { allowStructuredMapKeys = true }

    class Summary(val games: Int, val decisions: Long, val cardsPlayed: Set<String>)

    private fun seat(id: String, team: Team, handicap: Boolean = false) =
        SeatSetup(id, team, Controller.Human, handicap)

    private fun setup(hero: String, rng: Random): GameSetup {
        val o = others.shuffled(rng)
        val heroTeam = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
        return when (rng.nextInt(5)) {
            0 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, heroTeam), seat(o[0], heroTeam.opponent)),
                tieBreaker = coin,
            )

            1 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, heroTeam, true), seat(o[0], heroTeam, true), seat(o[1], heroTeam.opponent)),
                tieBreaker = coin,
            )

            2 -> GameSetup(
                TestData.RIVER,
                listOf(
                    seat(hero, heroTeam),
                    seat(o[0], heroTeam),
                    seat(o[1], heroTeam.opponent),
                    seat(o[2], heroTeam.opponent),
                ),
                tieBreaker = coin,
            )

            3 -> GameSetup(
                TestData.FOREST,
                listOf(
                    seat(hero, heroTeam),
                    seat(o[0], heroTeam),
                    seat(o[1], heroTeam.opponent),
                    seat(o[2], heroTeam.opponent),
                ),
                length = GameLength.LONG,
                tieBreaker = coin,
            )

            else -> GameSetup(
                TestData.FOREST,
                listOf(
                    seat(hero, heroTeam),
                    seat(o[0], heroTeam),
                    seat(o[1], heroTeam.opponent),
                    seat(o[2], heroTeam.opponent),
                ),
                tieBreaker = coin,
            )
        }
    }

    /** A 2v2 opening with the hero at level 8 and its Ultimate unlocked. */
    private fun ultimateStart(hero: String, rng: Random): EngineSession {
        val o = others.shuffled(rng)
        val geo = engine.data.geometry(TestData.FOREST)
        val red = geo.heroSpawns(Team.RED)
        val blue = geo.heroSpawns(Team.BLUE)
        val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
        return position(engine, TestData.FOREST) {
            tieBreaker = coin
            hero(hero, Team.RED, red[0]) { ultimate() }
            hero(o[0], Team.RED, red[1])
            hero(o[1], Team.BLUE, blue[0])
            hero(o[2], Team.BLUE, blue[1])
        }
    }

    private class Watch(val life: MutableMap<Team, Int>, val waves: MutableMap<String, Int>)

    private fun verify(s: EngineSession, w: Watch) {
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
        for (m in st.minions) m.at?.let { assertWithMessage("minion on a space").that(g.geo.isSpace(it)).isTrue() }
        for (p in st.pieces) p.at?.let { assertWithMessage("${p.label} on a space").that(g.geo.isSpace(it)).isTrue() }
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

    /** Plays [games] seeded games with [hero]; every game must end with a winner. */
    fun play(hero: String, games: Int, seed: Long): Summary {
        val rng = Random(seed)
        var decisions = 0L
        val played = LinkedHashSet<String>()
        repeat(games) { i ->
            val s = if (i % 4 == 3) ultimateStart(hero, rng) else engine.newGame(setup(hero, rng)) as EngineSession
            val w = Watch(LinkedHashMap(s.state.lifeCounters), LinkedHashMap(s.state.wavesLeft))
            val player = RandomPlayer(seed * 1000 + i)
            val cut = if (i % 5 == 0) rng.nextInt(20, 600) else -1
            var n = 0
            while (true) {
                val d = s.pending ?: break
                verify(s, w)
                assertThat(d.options).isNotEmpty()
                assertThat(d.options.distinct()).hasSize(d.options.size)
                if (n == cut) {
                    val restored = engine.restore(s.save()) as EngineSession
                    assertWithMessage("save/restore at decision $n of game $i").that(snap(restored)).isEqualTo(snap(s))
                    assertThat(restored.pending).isEqualTo(s.pending)
                }
                s.state.heroes.firstOrNull { it.id == hero }?.cards?.filter { it.zone == Zone.PLAYED }
                    ?.forEach { played += it.id }
                val r = s.answer(d.id, player.choose(d))
                check(r is AnswerResult.Accepted) { "Rejected $r for $d" }
                n++
                check(n < 30_000) { "Game $i did not end" }
            }
            verify(s, w)
            assertWithMessage("game $i has a winner").that(s.view(null).winner).isNotNull()
            decisions += n
        }
        return Summary(games, decisions, played)
    }
}
