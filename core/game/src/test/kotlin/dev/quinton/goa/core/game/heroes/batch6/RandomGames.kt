package dev.quinton.goa.core.game.heroes.batch6

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
 * Random-legal full games seating [hero] against the four reference heroes (brief "Done means" 3):
 * the PropertyTest invariants at every decision, with [maxPlayed] played cards allowed per hero
 * (Emmitt's Ultimate plays two before the reveal).
 */
object RandomGames {
    private val reference = listOf("wasp", "xargatha", "arien", "brogan")

    fun play(hero: String, games: Int, seed: Long, maxPlayed: (String) -> Int = { 1 }): Map<String, Int> {
        val engine = ANY_HERO_ENGINE
        val rng = Random(seed)
        val stats = LinkedHashMap<String, Int>()
        repeat(games) { i ->
            val others = reference.shuffled(rng)
            val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
            val heroTeam = if (rng.nextBoolean()) Team.RED else Team.BLUE
            val seats = when (rng.nextInt(4)) {
                0 -> listOf(SeatSetup(hero, heroTeam, Controller.Human), SeatSetup(others[0], heroTeam.opponent, Controller.Human))
                1 -> listOf(
                    SeatSetup(hero, heroTeam, Controller.Human, handicap = true),
                    SeatSetup(others[0], heroTeam, Controller.Human, handicap = true),
                    SeatSetup(others[1], heroTeam.opponent, Controller.Human),
                )
                else -> listOf(
                    SeatSetup(hero, heroTeam, Controller.Human),
                    SeatSetup(others[0], heroTeam, Controller.Human),
                    SeatSetup(others[1], heroTeam.opponent, Controller.Human),
                    SeatSetup(others[2], heroTeam.opponent, Controller.Human),
                )
            }
            val map = if (seats.size == 4 && rng.nextInt(3) == 0) TestData.RIVER else TestData.FOREST
            val length = if (rng.nextInt(4) == 0) GameLength.LONG else GameLength.QUICK
            val setup = GameSetup(map, seats.sortedBy { if (it.team == Team.RED) 0 else 1 }, length = length, tieBreaker = coin)
            val s = engine.newGame(setup) as EngineSession
            val player = RandomPlayer(seed * 1000 + i)
            var n = 0
            while (true) {
                val d = s.pending ?: break
                check(s, maxPlayed)
                assertThat(d.options).isNotEmpty()
                assertWithMessage("distinct options of $d").that(d.options.distinct()).hasSize(d.options.size)
                val r = s.answer(d.id, player.choose(d))
                check(r is AnswerResult.Accepted) { "Rejected $r" }
                n++
                check(n < 40_000) { "Game did not end: $setup" }
            }
            check(s, maxPlayed)
            val v = s.view(null)
            assertThat(v.winner).isNotNull()
            stats.merge(v.winReason!!.name, 1, Int::plus)
        }
        return stats
    }

    private fun check(s: EngineSession, maxPlayed: (String) -> Int) {
        val st = s.state
        val g = Game(ANY_HERO_ENGINE.data, st, emptyList(), RunMode.PROBE)
        g.occupancy() // throws on two obstacles in one space (p.18)
        for (h in st.heroes) {
            h.at?.let { assertWithMessage("${h.id} on a space").that(g.geo.isSpace(it)).isTrue() }
            assertThat(h.coins).isAtLeast(0)
            assertThat(h.level).isIn(1..8)
            val catalog = ANY_HERO_ENGINE.data.catalogHero(h.id).allCards.map { it.id }.sorted()
            assertWithMessage("cards of ${h.id}").that(h.cards.map { it.id }.sorted()).isEqualTo(catalog)
            assertThat(h.cards.count { it.zone == Zone.PLAYED }).isAtMost(maxPlayed(h.id))
            assertThat(h.cards.count { it.zone == Zone.ULTIMATE }).isAtMost(1)
        }
        for (p in st.pieces) p.at?.let { assertWithMessage("${p.id} on a space").that(g.geo.isSpace(it)).isTrue() }
        for (m in st.minions) m.at?.let { assertThat(g.geo.isSpace(it)).isTrue() }
    }
}
