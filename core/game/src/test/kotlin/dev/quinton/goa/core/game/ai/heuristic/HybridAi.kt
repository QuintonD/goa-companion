package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.engine.EngineSession

/**
 * Tests only (REVIEW-2-losses.md, "category swaps"): the heuristic player with one category of
 * questions answered by another player instead, usually the old search at strength 10. Against a fixed
 * opponent it measures how many games a category is worth if the book answered it as well as the
 * search does. Spec `h10/action.move/s10` in [HeuristicTournamentTest].
 */
internal class HybridAi(
    private val base: AiPlayer,
    private val delegate: AiPlayer,
    private val categories: Set<String>,
) : AiPlayer {
    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
        val es = session as? EngineSession
        val use = es != null && decision.options.size > 1 && category(es, decision) in categories
        return if (use) delegate.choose(session, decision, cancelled) else base.choose(session, decision, cancelled)
    }

    companion object {
        /** card, action, move, space, target, unit, opaque, defend, discard, upgrade, minion, respawn, first. */
        fun category(es: EngineSession, d: Decision): String = when (d.kind) {
            DecisionKind.PLAY_CARD -> "card"

            DecisionKind.CHOOSE_ACTION -> "action"

            DecisionKind.CHOOSE_SPACE -> {
                val subject = d.context.subjectId
                val own = subject != null && (subject == d.seat || es.state.hero(subject)?.team == d.team)
                if (own) "move" else "space"
            }

            DecisionKind.CHOOSE_UNIT -> if (d.prompt.startsWith("Target")) "target" else "unit"

            DecisionKind.CHOOSE_OPTION, DecisionKind.CHOOSE_TOKEN, DecisionKind.CONFIRM, DecisionKind.CHOOSE_NUMBER ->
                "opaque"

            DecisionKind.DEFEND -> "defend"

            DecisionKind.CHOOSE_CARD -> "discard"

            DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE -> "upgrade"

            DecisionKind.REMOVE_MINION, DecisionKind.MINION_RETURN -> "minion"

            DecisionKind.RESPAWN -> "respawn"

            DecisionKind.ACT_FIRST -> "first"
        }
    }
}
