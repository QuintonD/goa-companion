package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import org.junit.Test
import kotlin.random.Random

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
    fun `strength 11 plays whole games against strength 10`() {
        val lineups = AiHarness.mixedLineups(GAMES, seed = 20_261_126L)
        var wins11 = 0
        // Paired: each lineup and coin twice, 11 on each side, so a lineup's side bias cancels.
        for (g in 0 until GAMES) {
            val (red, blue) = lineups[g / 2]
            val elevenRed = g % 2 == 0
            val players = mapOf(
                Team.RED to AiPlayers.forApp(if (elevenRed) 11 else 10, budgetMs = BUDGET_MS),
                Team.BLUE to AiPlayers.forApp(if (elevenRed) 10 else 11, budgetMs = BUDGET_MS),
            )
            val coin = if ((g / 2) % 2 == 0) Team.RED else Team.BLUE
            val r = AiHarness.play(AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, coin), players)
            val elevenTeam = if (elevenRed) Team.RED else Team.BLUE
            if (r.winner == elevenTeam) wins11++
            println("ALL_SEEING game $g: $red v $blue, 11 is $elevenTeam, winner ${r.winner}, rounds ${r.rounds}")
            assertThat(r.winner).isNotNull()
        }
        println("ALL_SEEING strength 11 won $wins11 of $GAMES against strength 10")
    }

    private companion object {
        const val GAMES = 12
        const val BUDGET_MS = 250L
    }
}
