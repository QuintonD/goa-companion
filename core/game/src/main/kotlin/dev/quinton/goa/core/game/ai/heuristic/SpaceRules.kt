package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.engine.MinionState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType

/**
 * CHOOSE_SPACE, RESPAWN and MINION_RETURN (GENERAL §6). A hero's space is [HContext.placement]:
 * exposure (G43, G52), the projected Battle Zone (G44, G51), minion modifiers (G45, G46), a minion
 * to take, and the central space (G56). This file adds what placement does not hold.
 *
 * Not implemented: G47–G49 and G53 (Fast Travel corridors, straight lines, a teammate's path, spawn
 * camping) need per-card line geometry the general book does not have; hero playbooks own those
 * lines. G55 is a double-lane rotation (no rotation intent is formed yet).
 */
internal object SpaceRules {
    fun score(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Space) return if (o is Choice.Decline) -0.45 else 0.0
        val subject = d.context.subjectId
        // A card's own space with no unit named: a token or piece is not the hero standing there.
        val body = if (subject == null) placed(ctx, d) else ctx.heroBody(subject)
        if (body != null) {
            val owner = ctx.state.hero(body)?.team ?: return 0.0
            // G54: an enemy we place stands where it is worst for them.
            if (owner != ctx.team) return -ctx.placement(body, o.hex)
            return ctx.placement(body, o.hex) + fork(ctx, body, o.hex, d.sourceCardId)
        }
        val minion = subject?.let { ctx.state.minion(it) } ?: return 0.0
        return minionSpace(ctx, minion, o.hex)
    }

    /** Who a subject-less space places: probed on this decision's own view, else the asking hero. */
    private fun placed(ctx: HContext, d: Decision): String? =
        if (ctx.decision?.id == d.id && ctx.profile.on("placeProbe", true)) ctx.placedHero else ctx.heroBody(d.seat)

    /** G2 on the square we would end on: an enemy still to act defeats us there. */
    fun commitment(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Space || !ctx.profile.threatMap) return 0.0
        val subject = d.context.subjectId
        val body = (if (subject == null) placed(ctx, d) else ctx.heroBody(subject)) ?: return 0.0
        if (ctx.state.hero(body)?.team != ctx.team) return 0.0
        if (!unansweredKill(ctx, body, o.hex)) return 0.0
        return -TRAP_SHARE * ctx.killWorth(body) * ctx.profile.knob("spaceFear", 1.0)
    }

    /**
     * G27, G29, G31: the spawn nearest the fight we are going to (placement reads the projected zone
     * and the exposure). G28: Decline only when every spawn is a likely defeat by an enemy still to
     * act; it saves the defeat and costs the turn, and the hero is still off the board next turn. A
     * spawn far from the fight is not a reason to stay off: every spawn is that far.
     */
    fun respawn(ctx: HContext, d: Decision, o: Choice): Double {
        if (o is Choice.Space) return ctx.placement(ctx.seat, o.hex)
        val spaces = d.options.filterIsInstance<Choice.Space>()
        if (spaces.isEmpty()) return 0.0
        val kill = ctx.killWorth(ctx.seat)
        val camped = ctx.profile.threatMap && spaces.all { ctx.exposure(ctx.seat, it.hex) > kill * 0.5 }
        if (!camped) return -1000.0
        return spaces.maxOf { ctx.placement(ctx.seat, it.hex) + ctx.exposure(ctx.seat, it.hex) } - DECLINE_TEMPO
    }

    /**
     * G50 (from strength 5): from [at], a card still in hand threatens both an enemy hero and an
     * enemy minion; they must choose which to save.
     */
    private fun fork(ctx: HContext, heroId: String, at: Hex, playing: String?): Double {
        if (!ctx.profile.turnPlans || heroId != ctx.seat) return 0.0
        for (c in ctx.ownHand()) {
            if (c.id == playing || !(c.attackCard || c.forcesDiscard)) continue
            val reach = ctx.attackReach(heroId, c).coerceAtLeast(1)
            val hero = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) <= reach } == true }
            val minion = c.hitsMinions && ctx.state.minions.any { m ->
                m.team != ctx.team && !ctx.heavyImmune(m.id) && m.at?.let { ctx.distance(at, it) <= reach } == true
            }
            if (hero && minion) return FORK
        }
        return 0.0
    }

    /**
     * Where a minion should stand: the defense it gives or takes from the heroes around it (G100: next
     * to our heroes, away from theirs; G102: any square when the push is decided).
     */
    private fun minionSpace(ctx: HContext, m: MinionState, hex: Hex): Double {
        var v = 0.0
        for (h in ctx.state.heroes) {
            val at = h.at ?: continue
            val dist = ctx.distance(at, hex)
            val effect = when {
                m.type != MinionType.RANGED && dist == 1 -> if (m.team == h.team) 1.0 else -1.0
                m.type == MinionType.RANGED && dist in 1..2 && m.team != h.team -> -1.0
                else -> 0.0
            }
            v += if (h.team == ctx.team) effect else -effect
            if (m.team != ctx.team && h.team == ctx.team && dist == 1) v += 0.35
        }
        return v
    }

    /** A declined respawn: the action is lost (GENERAL §2, tempo of an action). */
    private const val DECLINE_TEMPO = 8.0
    private const val TRAP_SHARE = 0.65
    private const val FORK = 3.0
}
