package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.heuristic.General
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team

/** Shared checks for this batch. A playbook's note has to change the card the general book would play. */
internal object HeroBookTest {
    fun finish(hero: String, size: Int, strength: Int) {
        val pool = listOf("arien", "brogan", "wasp", "min", "xargatha").filter { it != hero }
        val red = listOf(hero) + pool.take(size - 1)
        val blue = pool.drop(size - 1).take(size)
        val level = AiLevel.of(strength)
        val played = AiHarness.play(
            AiHarness.setup(red, blue, level, level, Team.RED),
            mapOf(Team.RED to HeuristicAi(strength, 11), Team.BLUE to HeuristicAi(strength, 29)),
        )
        check(played.session.pending == null) { "$hero ${size}v$size strength $strength did not finish" }
        check(played.winner != null) { "$hero ${size}v$size strength $strength had no winner" }
    }

    fun chosen(session: EngineSession, decision: Decision, book: HeroHeuristics?): Choice {
        val ctx = HContext(session, decision, decision.team, HeuristicProfile.of(8))
        if (book != null) {
            book.override(ctx, decision)?.let { if (it in decision.options) return it }
        }
        var bestAt = 0
        var best = Double.NEGATIVE_INFINITY
        for (i in decision.options.indices) {
            var score = General.score(ctx, decision, decision.options[i])
            if (book != null) score += book.adjust(ctx, decision, decision.options[i])
            if (score > best) {
                best = score
                bestAt = i
            }
        }
        return decision.options[bestAt]
    }
}
