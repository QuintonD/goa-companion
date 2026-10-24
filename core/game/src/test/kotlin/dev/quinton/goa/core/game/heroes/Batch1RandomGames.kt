package dev.quinton.goa.core.game.heroes

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
 * Random-legal full games seating one hero of hero batch 1 (Tigerclaw, Sabina, Dodger, Misa, Garrus)
 * against and beside the four reference heroes: 1v1, 2v1 with Handicap cards, 2v2 Quick and Long, and
 * the double-lane map. Invariants are checked at every decision (as `PropertyTest` does); every game
 * must end with a winner.
 */
internal fun batch1RandomGames(hero: String, games: Int = 120, seed: Long = 1) {
    val engine = ANY_HERO_ENGINE
    val others = listOf("wasp", "xargatha", "arien", "brogan")
    val rng = Random(seed)
    fun seat(id: String, team: Team, handicap: Boolean = false) = SeatSetup(id, team, Controller.Human, handicap)
    repeat(games) { i ->
        val coin = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val o = others.shuffled(rng)
        val mine = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val setup = when (i % 5) {
            0 -> GameSetup(TestData.FOREST, listOf(seat(hero, mine), seat(o[0], mine.opponent)), tieBreaker = coin)

            1 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, mine, true), seat(o[0], mine, true), seat(o[1], mine.opponent)),
                tieBreaker = coin,
            )

            2 -> GameSetup(
                TestData.FOREST,
                listOf(seat(hero, mine), seat(o[0], mine), seat(o[1], mine.opponent), seat(o[2], mine.opponent)),
                length = GameLength.LONG,
                tieBreaker = coin,
            )

            3 -> GameSetup(
                TestData.RIVER,
                listOf(seat(hero, mine), seat(o[0], mine), seat(o[1], mine.opponent), seat(o[2], mine.opponent)),
                tieBreaker = coin,
            )

            else -> GameSetup(
                TestData.FOREST,
                listOf(seat(o[0], mine), seat(hero, mine), seat(o[1], mine.opponent), seat(o[2], mine.opponent)),
                tieBreaker = coin,
            )
        }
        val s = engine.newGame(setup) as EngineSession
        val player = RandomPlayer(seed * 1000 + i)
        var n = 0
        while (true) {
            val d = s.pending ?: break
            check(s, engine)
            assertThat(d.options).isNotEmpty()
            assertWithMessage("distinct options of $d").that(d.options.distinct()).hasSize(d.options.size)
            val r = s.answer(d.id, player.choose(d))
            check(r is AnswerResult.Accepted) { "Rejected $r for $d" }
            n++
            check(n < 30_000) { "Game did not end: $setup" }
        }
        check(s, engine)
        assertWithMessage("winner of game $i ($setup)").that(s.view(null).winner).isNotNull()
    }
}

private fun check(s: EngineSession, engine: dev.quinton.goa.core.game.engine.GameEngine) {
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
    for (m in st.minions) m.at?.let { assertThat(g.geo.isSpace(it)).isTrue() }
}
