package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.Counters
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import org.junit.Assert.assertThrows
import org.junit.Test

/** Game setup, rulebook pp.5–6 and p.20. */
class SetupTest {
    private val engine = TestData.engine

    @Test
    fun `p6 step 9 - heroes start on their team's hero spawn points in pick order`() {
        val s = TestData.newGame()
        val geo = engine.data.geometry(TestData.FOREST)
        assertThat(s.state.hero("wasp")!!.at).isEqualTo(geo.heroSpawns(Team.RED)[0])
        assertThat(s.state.hero("xargatha")!!.at).isEqualTo(geo.heroSpawns(Team.RED)[1])
        assertThat(s.state.hero("arien")!!.at).isEqualTo(geo.heroSpawns(Team.BLUE)[0])
        assertThat(s.state.hero("brogan")!!.at).isEqualTo(geo.heroSpawns(Team.BLUE)[1])
    }

    @Test
    fun `p6 step 10 - 1 ranged, 4 melee, 1 heavy per team on the starting Battle Zone's spawn points`() {
        val s = TestData.newGame()
        val geo = engine.data.geometry(TestData.FOREST)
        for (team in Team.entries) {
            val mine = s.state.minions.filter { it.team == team }
            assertThat(mine.count { it.type == MinionType.MELEE }).isEqualTo(4)
            assertThat(mine.count { it.type == MinionType.RANGED }).isEqualTo(1)
            assertThat(mine.count { it.type == MinionType.HEAVY }).isEqualTo(1)
            for (m in mine) {
                val space = geo.spaces.getValue(m.at!!)
                assertThat(space.zone).isEqualTo("lane-1-2")
                assertThat(space.spawn!!.team).isEqualTo(team)
                assertThat(space.spawn!!.kind).isEqualTo(SpawnKind.entries.first { it.minion == m.type })
            }
        }
    }

    @Test
    fun `p6 step 11 - starting hand is Gold, Silver and the three Tier I cards, Handicap in the box`() {
        val s = TestData.newGame()
        val wasp = s.state.hero("wasp")!!
        assertThat(
            wasp.hand.map {
                it.id
            },
        ).containsExactly("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-1")
        assertThat(wasp.card("wasp-red-2a")!!.zone).isEqualTo(Zone.DECK)
        assertThat(wasp.card("wasp-purple-4")!!.zone).isEqualTo(Zone.DECK)
        assertThat(wasp.card("wasp-handicap")!!.zone).isEqualTo(Zone.BOX)
    }

    @Test
    fun `p20 - a Handicap hero replaces its gold card with the Handicap card`() {
        val setup = TestData.firstGame().let {
            it.copy(
                seats = it.seats.map { s ->
                    if (s.heroId ==
                        "brogan"
                    ) {
                        s.copy(handicap = true)
                    } else {
                        s
                    }
                },
            )
        }
        val s = engine.newGame(setup) as EngineSession
        val brogan = s.state.hero("brogan")!!
        assertThat(brogan.hand.map { it.id }).contains("brogan-handicap")
        assertThat(brogan.hand.map { it.id }).doesNotContain("brogan-gold")
        assertThat(brogan.card("brogan-gold")!!.zone).isEqualTo(Zone.BOX)
        assertThat(brogan.hand).hasSize(5)
    }

    @Test
    fun `p5 steps 4 and 5 - Quick game with 4 heroes has 3 Wave counters and 4 Life counters per team`() {
        val s = TestData.newGame()
        assertThat(s.state.wavesLeft).containsExactly("lane-1", 3)
        assertThat(s.state.lifeCounters).containsExactly(Team.RED, 4, Team.BLUE, 4)
    }

    @Test
    fun `p5 steps 4 and 5 - Long game with 4 heroes has 5 Wave counters and 6 Life counters per team`() {
        val s = engine.newGame(TestData.firstGame(length = GameLength.LONG)) as EngineSession
        assertThat(s.state.wavesLeft).containsExactly("lane-1", 5)
        assertThat(s.state.lifeCounters[Team.RED]).isEqualTo(6)
    }

    @Test
    fun `p20 - 2 players (1v1 heroes) use the 4-player Life counters`() {
        val setup = GameSetup(
            TestData.FOREST,
            listOf(SeatSetup("wasp", Team.RED, Controller.Human), SeatSetup("brogan", Team.BLUE, Controller.Human)),
            tieBreaker = Team.RED,
        )
        val s = engine.newGame(setup) as EngineSession
        assertThat(s.state.lifeCounters[Team.BLUE]).isEqualTo(4)
    }

    @Test
    fun `p20 - double lane map has two Lanes with 7 Wave counters each and both starting Battle Zones`() {
        val s = engine.newGame(TestData.firstGame(map = TestData.RIVER)) as EngineSession
        assertThat(s.state.wavesLeft).containsExactly("lane-1", 7, "lane-2", 7)
        assertThat(s.state.battleZones).containsExactly("lane-1", "lane-1-2", "lane-2", "lane-2-3")
        assertThat(s.state.minions.map { it.lane }.toSet()).containsExactly("lane-1", "lane-2")
        // [APP] 4 heroes on the double-lane map: the 6–8 player row (RULINGS.md "Counters").
        assertThat(s.state.lifeCounters[Team.RED]).isEqualTo(6)
    }

    @Test
    fun `p5 step 3 - the Tie Breaker coin starts as the setup places it`() {
        val s = engine.newGame(TestData.firstGame(tieBreaker = Team.BLUE)) as EngineSession
        assertThat(s.state.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(s.view(null).tieBreaker).isEqualTo(Team.BLUE)
    }

    @Test
    fun `a hero is picked at most once per game`() {
        val setup = TestData.firstGame().let {
            it.copy(seats = it.seats + SeatSetup("wasp", Team.BLUE, Controller.Human))
        }
        assertThrows(IllegalArgumentException::class.java) { engine.newGame(setup) }
    }

    @Test
    fun `only complete heroes are playable`() {
        // Playable = complete (hero batches add theirs); the four first-game heroes always are.
        val complete = dev.quinton.goa.core.game.heroes.Heroes.all.filter { it.complete }.map { it.heroId }
        assertThat(engine.playableHeroes).containsExactlyElementsIn(complete)
        assertThat(engine.playableHeroes).containsAtLeast("arien", "brogan", "wasp", "xargatha")
        val setup = TestData.firstGame().let {
            it.copy(
                seats =
                    it.seats.dropLast(1) + SeatSetup("dodger", Team.BLUE, Controller.Human),
            )
        }
        assertThrows(IllegalArgumentException::class.java) { engine.newGame(setup) }
    }

    @Test
    fun `playable maps are the verified ones, single lane first`() {
        assertThat(engine.playableMaps).containsExactly("forgotten-island", "across-the-river").inOrder()
    }

    @Test
    fun `the built-in counter table equals match-setup json`() {
        val rules = TestData.matchRules
        for (heroes in 2..6) {
            assertThat(
                Counters.life(1, GameLength.QUICK, heroes),
            ).isEqualTo(rules.mode(GameMode.QUICK).lifeCountersFor(heroes))
            assertThat(
                Counters.life(1, GameLength.LONG, heroes),
            ).isEqualTo(rules.mode(GameMode.LONG).lifeCountersFor(heroes))
        }
        for (heroes in 6..10) {
            assertThat(
                Counters.life(2, GameLength.LONG, heroes),
            ).isEqualTo(rules.mode(GameMode.DOUBLE_LANE).lifeCountersFor(heroes))
        }
        assertThat(Counters.waves(1, GameLength.QUICK)).isEqualTo(rules.mode(GameMode.QUICK).waveCountersPerLane)
        assertThat(Counters.waves(1, GameLength.LONG)).isEqualTo(rules.mode(GameMode.LONG).waveCountersPerLane)
        assertThat(Counters.waves(2, GameLength.LONG)).isEqualTo(rules.mode(GameMode.DOUBLE_LANE).waveCountersPerLane)
    }
}
