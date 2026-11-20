package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.heuristic.General
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.match.Team

/** Positions and full games shared by this batch's playbook tests. */
internal object BatchBFixture {
    fun session(block: PositionBuilder.() -> Unit): EngineSession =
        position(TestData.engine, TestData.FOREST) {
            clearMinions()
            phase = PositionPhase.PlayCards(0)
            block()
        }

    /** General score alone, then the same score plus this hero's adjust. Style stays on both. */
    fun pick(session: EngineSession, book: HeroHeuristics, adjust: Boolean): Choice {
        val decision = checkNotNull(session.pending) { "position has no decision" }
        val ctx = HContext(
            session = session,
            decision = decision,
            team = decision.team,
            profile = HeuristicProfile.of(8),
            playbooks = { id -> if (id == book.heroId) book else HeroPlaybooks.forHero(id) },
        )
        return decision.options.maxBy { option ->
            General.score(ctx, decision, option) + if (adjust) book.adjust(ctx, decision, option) else 0.0
        }
    }

    fun finishes(hero: String) {
        val mates = listOf("arien", "brogan", "wasp")
        val foes = listOf("xargatha", "silverarrow", "cutter")
        for (strength in listOf(5, 10)) {
            for (size in listOf(2, 3)) {
                val red = listOf(hero) + mates.take(size - 1)
                val blue = foes.take(size)
                val level = AiLevel.of(strength)
                val result = AiHarness.play(
                    AiHarness.setup(red, blue, level, level, Team.RED),
                    mapOf(
                        Team.RED to HeuristicAi(strength, 11),
                        Team.BLUE to HeuristicAi(strength, 13),
                    ),
                )
                assertThat(result.session.pending).isNull()
                assertThat(result.winner).isNotNull()
            }
        }
    }
}
