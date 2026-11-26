package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import org.junit.Test

/** Strength 11, the all-seeing computer: reads the true state, and plays whole games against strength 10. */
class AllSeeingTest {
    @Test
    fun `strength 11 is seated and its profile sees everything`() {
        assertThat(AiStrength.MAX).isEqualTo(11)
        assertThat(HeuristicProfile.of(11).omniscient).isTrue()
        assertThat(HeuristicProfile.of(10).omniscient).isFalse()
        val ai = AiPlayers.forApp(11) as HybridAi
        assertThat(ai.book.profile.omniscient).isTrue()
        assertThat((AiPlayers.forApp(10) as HybridAi).book.profile.omniscient).isFalse()
    }

    @Test
    fun `the omniscient sample keeps the enemy's facedown card and hand, a normal one may not`() {
        val (red, blue) = AiHarness.mixedLineups(1, seed = 41L)[0]
        val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.RED))
            as EngineSession
        val mover = AiPlayers.deterministic(4, 9L)
        // Answer until the first card play of Red after Blue played facedown, or any card play with Blue's card down.
        var guard = 0
        while (guard++ < 500) {
            val d = s.pending ?: break
            val bluePlayed = s.state.heroes.filter { it.team == Team.BLUE }
                .any { h -> h.cards.any { it.zone == Zone.PLAYED } }
            if (d.kind == DecisionKind.PLAY_CARD && d.team == Team.RED && bluePlayed && !s.state.revealed) break
            assertThat(s.answer(d.id, mover.choose(s, d))).isInstanceOf(AnswerResult.Accepted::class.java)
        }
        fun blueCards(st: GameState) = st.heroes.filter { it.team == Team.BLUE }
            .map { h -> h.cards.map { it.id to it.zone }.sortedBy { it.first } }
        val truth = blueCards(s.state)
        repeat(5) { i ->
            val o = Determinizer.sample(
                s.engineData, s.state, Team.RED, Random(i),
                Determinizer.Options(knowHands = true, omniscient = true),
            )
            assertThat(blueCards(o)).isEqualTo(truth)
        }
    }

    @Test
    fun `the team with a strength 11 seat is asked for its cards last, pick order kept`() {
        fun seat(id: String, team: Team, c: Controller) = SeatSetup(id, team, c)
        val setup = GameSetup(
            TestData.FOREST,
            listOf(
                seat("wasp", Team.RED, Controller.Ai(11)),
                seat("brogan", Team.RED, Controller.Human),
                seat("arien", Team.BLUE, Controller.Human),
                seat("tigerclaw", Team.BLUE, Controller.Ai(4)),
            ),
            tieBreaker = Team.RED,
        )
        assertThat(setup.allSeeingLast().seats.map { it.heroId }).containsExactly("arien", "tigerclaw", "wasp", "brogan")
            .inOrder()
        val s = TestData.engine.newGame(setup) as EngineSession
        assertThat(s.setup.seats.map { it.heroId }).containsExactly("arien", "tigerclaw", "wasp", "brogan").inOrder()
        assertThat(s.state.heroes.map { it.id }).containsExactly("arien", "tigerclaw", "wasp", "brogan").inOrder()
        // Neither or both teams all-seeing: unchanged.
        val plain = setup.copy(seats = setup.seats.map { it.copy(controller = Controller.Ai(10)) })
        assertThat(plain.allSeeingLast()).isSameInstanceAs(plain)
        val both = setup.copy(seats = setup.seats.map { it.copy(controller = Controller.Ai(11)) })
        assertThat(both.allSeeingLast()).isSameInstanceAs(both)
    }

    /**
     * Strength 11 against strength 10, paired (each line-up and coin twice, 11 on each side, so a line-up's
     * side bias cancels), at the app's think time unless `AI_ALL_SEEING_MS` says otherwise. Slow: runs only
     * with `AI_ALL_SEEING=<games>`. Every card play of 11 must see every enemy card of the turn.
     */
    @Test
    fun `strength 11 plays whole games against strength 10`() {
        val games = System.getenv("AI_ALL_SEEING")?.toIntOrNull() ?: return
        val budget = System.getenv("AI_ALL_SEEING_MS")?.toLongOrNull() ?: AiPlayers.APP_BUDGET_MS
        // Extra knobs for 11 only, e.g. AI_ALL_SEEING_KNOBS="tc=2,tw=3".
        val extra = System.getenv("AI_ALL_SEEING_KNOBS").orEmpty().split(",").filter { "=" in it }
            .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().toDouble() }
        val lineups = AiHarness.mixedLineups((games + 1) / 2, seed = 20_261_126L)
        var wins11 = 0
        var blind = 0
        var plays = 0
        val bySide = mutableMapOf(Team.RED to 0, Team.BLUE to 0)
        for (g in 0 until games) {
            val (red, blue) = lineups[g / 2]
            val eleven = if (g % 2 == 0) Team.RED else Team.BLUE
            fun strength(t: Team) = if (t == eleven) 11 else 10
            val players = Team.entries.associateWith { t ->
                if (t == eleven && extra.isNotEmpty()) {
                    HybridAi(AiPlayers.APP_SEED + 11, AiPlayers.knobsFor(11) + extra, budgetMs = budget)
                } else {
                    AiPlayers.forApp(strength(t), budgetMs = budget)
                }
            }
            val coin = if ((g / 2) % 2 == 0) Team.RED else Team.BLUE
            val setup = GameSetup(
                TestData.FOREST,
                red.map { SeatSetup(it, Team.RED, Controller.Ai(strength(Team.RED))) } +
                    blue.map { SeatSetup(it, Team.BLUE, Controller.Ai(strength(Team.BLUE))) },
                length = GameLength.QUICK,
                tieBreaker = coin,
            )
            val r = AiHarness.play(setup, players, observe = { s, d ->
                if (d.team == eleven && d.kind == DecisionKind.PLAY_CARD && !s.state.revealed) {
                    plays++
                    val unseen = s.state.heroes.any { h ->
                        h.team != eleven && h.hand.isNotEmpty() && h.cards.none { it.zone == Zone.PLAYED }
                    }
                    if (unseen) blind++
                }
            })
            if (r.winner == eleven) {
                wins11++
                bySide[eleven] = bySide.getValue(eleven) + 1
            }
            println("ALL_SEEING game $g: $red v $blue, 11 is $eleven, winner ${r.winner}, rounds ${r.rounds}")
            assertThat(r.winner).isNotNull()
        }
        println("ALL_SEEING strength 11 won $wins11 of $games against strength 10 (as Red ${bySide[Team.RED]}, " +
            "as Blue ${bySide[Team.BLUE]}); $blind of $plays card plays before an enemy had played, budget $budget ms, knobs $extra")
        assertThat(blind).isEqualTo(0)
    }
}
