package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.MAP
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.engine

/**
 * The end of round 1 on the real rules engine with a Minion Battle the person's team loses (Red 3,
 * Blue 4: Red removes one of its melee minions, p.8), then Gydion's level up (one coin). The
 * re-check's "the recap filled the panel and the Minion Battle's Confirm was below the fold".
 */
object EndOfRoundGame {
    /** At Gydion's REMOVE_MINION question (the moment: "End of round 1"). */
    fun session(): EngineSession {
        val s = position(engine, MAP) {
            clearMinions()
            hero("gydion", Team.RED, Hex(1, -2)) { coins = 1 }
            hero("arien", Team.BLUE, Hex(2, -2)) { controller = Controller.Ai(AiLevel.HARD) }
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.RED, MinionType.MELEE, Hex(1, -6))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            phase = PositionPhase.EndOfRound
        }
        check(s.pending?.seat == "gydion" && s.pending?.kind == DecisionKind.REMOVE_MINION) { "No Minion Battle" }
        return s
    }

    /** Answer Gydion's question with its first option, then everyone else's until Gydion is asked again. */
    fun next(s: EngineSession): EngineSession {
        val d = s.pending ?: error("No question")
        s.answer(d.id, d.options.first())
        repeat(MAX_STEPS) {
            val p = s.pending ?: error("No next question")
            if (p.seat == "gydion") return s
            s.answer(p.id, p.options.first())
        }
        error("No next question for Gydion")
    }

    private const val MAX_STEPS = 40
}
