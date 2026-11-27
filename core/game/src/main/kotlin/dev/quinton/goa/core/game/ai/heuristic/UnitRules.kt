package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.model.catalog.Stat

/**
 * CHOOSE_UNIT (GENERAL §6, G57–G68). A hero target is [HContext.heroStrikeWorth] (G57: the kill at its
 * chance, otherwise the card they throw; G59 the weak poke; G60 the strip a teammate follows up; G11 an
 * empty hand). A minion is [HContext.minionWorth] (G58 coins and the lane; G61 the last one is the push;
 * G62 the ranged minion covering us; G63 the adjacent one). G66 (a unit we move) is the one-ply look's:
 * that prompt is opaque to these scores.
 */
internal object UnitRules {
    fun score(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Unit) return if (o is Choice.Decline) -0.4 else 0.0
        var v = unitValue(ctx, d, o.unitId)
        if (ctx.profile.turnPlans && o.unitId == ctx.plan.targetId) v += PLAN_TARGET
        v += bodies(ctx, o.unitId)
        v += thin(ctx, o.unitId)
        v += setup(ctx, o.unitId)
        v -= TeamPlay.claimed(ctx, d, o.unitId) // Track T: G64 before the reveal, for a known teammate card
        return v
    }

    /** G13 on the minion itself; G64 a minion a teammate still to act will take. */
    fun commitment(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Unit) return 0.0
        val m = ctx.state.minion(o.unitId) ?: return 0.0
        if (m.team == ctx.team) return 0.0
        var v = 0.0
        if (badPush(ctx, m.lane)) v += if (ctx.profile.laneTiming) -15.0 else -6.0
        if (claimed(ctx, d, o.unitId)) v -= CLAIMED
        return v
    }

    private fun unitValue(ctx: HContext, d: Decision, id: String): Double {
        ctx.state.minion(id)?.let { return ctx.minionWorth(id) }
        val body = ctx.heroBody(id) ?: return 0.4
        val src = d.sourceCardId?.let { ctx.card(it) }
        val total = (src?.attack ?: 0) + ctx.itemBonus(d.seat, Stat.ATTACK)
        return ctx.heroStrikeWorth(body, total, src?.ranged == true)
    }

    /** G67 (from 5): one of several bodies; the body with the worst minion modifier for them. */
    private fun bodies(ctx: HContext, id: String): Double {
        if (!ctx.profile.turnPlans) return 0.0
        val piece = ctx.state.piece(id) ?: return 0.0
        val hero = piece.heroBody?.let { ctx.state.hero(it) } ?: return 0.0
        if (hero.team == ctx.team) return 0.0
        val at = piece.at ?: return 0.0
        return -ctx.minionModifier(at, hero.team) * BODY_PER_POINT
    }

    /** G65 (from 7): a hero who has already thrown a card this round, now short of a spare. */
    private fun thin(ctx: HContext, id: String): Double {
        if (!ctx.profile.focusFire) return 0.0
        val h = ctx.state.hero(id) ?: return 0.0
        if (h.team == ctx.team) return 0.0
        return if (ctx.spare(id) <= 0) THIN else 0.0
    }

    /** G68 (from 9): the hero this turn's strip is setting up. */
    private fun setup(ctx: HContext, id: String): Double {
        if (!ctx.profile.twoTurnPlans || ctx.plan.intent != Intent.SETUP || ctx.plan.targetId != id) return 0.0
        return ctx.killWorth(id) * SETUP_SHARE
    }

    /**
     * G64 (from 7): a teammate who has not acted holds a revealed card that reaches [minionId], and the
     * question offers another enemy minion. Leave it to them.
     */
    private fun claimed(ctx: HContext, d: Decision, minionId: String): Boolean {
        if (!ctx.profile.focusFire || !ctx.state.revealed) return false
        val m = ctx.state.minion(minionId) ?: return false
        val pos = m.at ?: return false
        if (ctx.minionCount(m.team, m.lane) <= 1) return false
        val others = d.options.any { o ->
            o is Choice.Unit && o.unitId != minionId && ctx.state.minion(o.unitId)?.team == m.team
        }
        if (!others) return false
        return ctx.allies().any { ally ->
            val at = ally.at ?: return@any false
            ally.id !in ctx.state.turnRecord.resolved && ally.unresolved.any { c ->
                val card = ctx.card(c.id)
                card.attackCard && ctx.distance(at, pos) <= ctx.attackReach(ally.id, card)
            }
        }
    }

    private const val PLAN_TARGET = 1.8
    private const val BODY_PER_POINT = 2.0
    private const val THIN = 6.0
    private const val SETUP_SHARE = 0.3
    private const val CLAIMED = 8.0
}
