package dev.quinton.goa.feature.board

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession

/**
 * Positions for the table's screenshots: a seeded game (every seat answered by [PlayTestData.Busy])
 * played until the first decision of a kind. Deterministic: the same kind always gives the same position.
 */
object DecisionKindScan {
    /** The session with a [kind] decision pending, or null if none came up in [games] seeded games. */
    fun find(kind: DecisionKind, games: Int = 40, humans: Set<String> = setOf("wasp", "xargatha")): GameSession? {
        val base = PlayTestData.firstGame(human = setOf("wasp", "xargatha", "arien", "brogan"))
        for (seed in 1L..games.toLong()) {
            val session = PlayTestData.engine.newGame(base)
            val player = PlayTestData.Busy(seed)
            var n = 0
            while (n++ < MAX_DECISIONS) {
                val d = session.pending ?: break
                if (d.kind == kind && d.seat in humans) return session
                check(session.answer(d.id, player.choose(d)) is AnswerResult.Accepted)
            }
        }
        return null
    }

    private const val MAX_DECISIONS = 6_000
}
