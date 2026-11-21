package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind

/**
 * The general playbook (ADR-085, GENERAL.md): every [DecisionKind] gets a score in coin-equivalents.
 * A hero playbook adds to it; it does not replace it unless it overrides. One file per kind of
 * question holds its rules, each tagged with its GENERAL.md number:
 *
 * - [PlayRules] PLAY_CARD (G1–G22), and [CardSim] the card's own action through the engine
 * - [ActionRules] CHOOSE_ACTION (G32–G42)
 * - [SpaceRules] CHOOSE_SPACE, RESPAWN, MINION_RETURN (G27–G31, G43–G56, G100–G102)
 * - [UnitRules] CHOOSE_UNIT (G57–G68)
 * - [DefendRules] DEFEND (G77–G84)
 * - [CardRules] CHOOSE_CARD, UPGRADE_CARD, KEEP_UPGRADE (G72–G76, G103–G112)
 * - [MiscRules] ACT_FIRST, CHOOSE_TOKEN, CHOOSE_OPTION, CONFIRM, CHOOSE_NUMBER, REMOVE_MINION
 *   (G23–G26, G69–G71, G85–G99)
 *
 * [score] is the direct scorer (GENERAL "EVAL" and "ADD" rules). A one-ply look scores the position
 * instead and adds [commitment], the "ADD" rules the position does not contain.
 */
object General {
    fun scores(ctx: HContext, d: Decision): DoubleArray = DoubleArray(d.options.size) { score(ctx, d, d.options[it]) }

    fun best(ctx: HContext, d: Decision): Choice {
        val s = scores(ctx, d)
        var bi = 0
        for (i in 1 until s.size) if (s[i] > s[bi]) bi = i
        return d.options[bi]
    }

    fun score(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> PlayRules.score(ctx, choice)

        DecisionKind.CHOOSE_ACTION -> ActionRules.score(ctx, d, choice)

        DecisionKind.CHOOSE_SPACE, DecisionKind.MINION_RETURN -> SpaceRules.score(ctx, d, choice)

        DecisionKind.RESPAWN -> SpaceRules.respawn(ctx, d, choice)

        DecisionKind.CHOOSE_UNIT -> UnitRules.score(ctx, d, choice)

        DecisionKind.DEFEND -> DefendRules.score(ctx, d, choice)

        DecisionKind.CHOOSE_CARD -> CardRules.choose(ctx, d, choice)

        DecisionKind.UPGRADE_CARD -> CardRules.upgrade(ctx, d, choice)

        DecisionKind.KEEP_UPGRADE -> CardRules.keep(ctx, d, choice)

        DecisionKind.REMOVE_MINION -> MiscRules.remove(ctx, choice)

        DecisionKind.CHOOSE_TOKEN -> MiscRules.token(ctx, d, choice)

        DecisionKind.ACT_FIRST -> MiscRules.actFirst(ctx, choice)

        DecisionKind.CONFIRM -> MiscRules.confirm(choice)

        DecisionKind.CHOOSE_NUMBER -> MiscRules.number(choice)

        // G85: every bullet goes through the look; none is preferred blind.
        DecisionKind.CHOOSE_OPTION -> 0.0
    }

    /**
     * What a one-ply look must still add. The look scores the position after this action;
     * it does not see the enemy who acts next, or a kill that lands next turn.
     */
    fun commitment(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> PlayRules.add(ctx, choice, simulated = true)
        DecisionKind.CHOOSE_SPACE, DecisionKind.RESPAWN -> SpaceRules.commitment(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> UnitRules.commitment(ctx, d, choice)
        DecisionKind.CHOOSE_ACTION -> ActionRules.commitment(ctx, d, choice)
        DecisionKind.CONFIRM -> MiscRules.confirmCommitment(ctx, choice)
        else -> 0.0
    }
}
