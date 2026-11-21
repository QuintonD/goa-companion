package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * What this hero is doing with the card already played. Derived from the state every time
 * (no memory), so a later decision of the same turn follows the same intent the card play formed.
 */
data class TurnPlan(
    val intent: Intent,
    val targetId: String?,
    val cardId: String?,
) {
    companion object {
        fun derive(ctx: HContext): TurnPlan {
            val h = ctx.own() ?: return TurnPlan(Intent.FARM, null, null)
            val played = h.unresolved.firstOrNull()?.let { ctx.card(it.id) }
            val at = h.at
            if (at == null) return TurnPlan(Intent.RESPAWN, null, played?.id)
            if (played == null) return TurnPlan(Intent.FARM, null, null)
            if (unansweredKill(ctx, h.id, at, played.id) && played.hasMovement) {
                return TurnPlan(Intent.SURVIVE, null, played.id)
            }
            val exposure = ctx.exposure(h.id, at)
            if (exposure > 10.0 && played.hasMovement && !played.attackCard) {
                return TurnPlan(Intent.RETREAT, null, played.id)
            }
            if (ctx.profile.laneTiming && ctx.lanes().any { ctx.laneMargin(it) >= 2 && !ctx.matchPoint(it) } &&
                played.hasMovement && !played.attackCard
            ) {
                return TurnPlan(Intent.LET_PUSH, null, played.id)
            }
            if (ctx.distanceToBattleZone(at) >= 4 && ctx.canFastTravel(h.id)) {
                return TurnPlan(Intent.FAST_TRAVEL, null, played.id)
            }
            if (played.attackCard || played.forcesDiscard) return attackPlan(ctx, h.id, at, played)
            if (played.skillCard) return TurnPlan(Intent.SET_UP, null, played.id)
            if (ctx.inBattleZone(at)) return TurnPlan(Intent.DEFEND_LANE, null, played.id)
            return TurnPlan(Intent.FARM, null, played.id)
        }

        private fun attackPlan(ctx: HContext, heroId: String, at: Hex, card: HCard): TurnPlan {
            val bestMinion = bestMinion(ctx, heroId, at, card)
            val bestHero = bestHero(ctx, heroId, at, card)
            val minionWorth = bestMinion?.second ?: 0.0
            val heroWorth = bestHero?.second ?: 0.0
            val killNow = bestHero?.first?.let { ctx.defenseless(it) } == true
            val setup = ctx.profile.twoTurnPlans && bestHero != null && !killNow && heroWorth + 6.0 >= minionWorth &&
                ctx.state.hero(bestHero.first)?.let { stripSetsUp(ctx, it) } == true
            return when {
                killNow && heroWorth >= minionWorth -> TurnPlan(Intent.KILL, bestHero?.first, card.id)
                setup -> TurnPlan(Intent.SETUP, bestHero?.first, card.id)
                minionWorth >= 14.0 && minionWorth >= heroWorth -> TurnPlan(Intent.PUSH, bestMinion?.first, card.id)
                heroWorth >= 8.0 && heroWorth >= minionWorth -> TurnPlan(Intent.KILL, bestHero?.first, card.id)
                bestMinion != null -> TurnPlan(Intent.FARM, bestMinion.first, card.id)
                else -> TurnPlan(Intent.DEFEND_LANE, null, card.id)
            }
        }

        private fun bestMinion(ctx: HContext, heroId: String, at: Hex, card: HCard): Pair<String, Double>? {
            var best: Pair<String, Double>? = null
            for (m in ctx.state.minions) {
                val pos = m.at ?: continue
                if (m.team == ctx.team || ctx.heavyImmune(m.id) || !card.hitsMinions) continue
                if (ctx.distance(at, pos) > ctx.attackReach(heroId, card) + 1) continue
                val worth = ctx.minionWorth(m.id)
                if (best == null || worth > best.second) best = m.id to worth
            }
            return best
        }

        private fun bestHero(ctx: HContext, heroId: String, at: Hex, card: HCard): Pair<String, Double>? {
            var best: Pair<String, Double>? = null
            val total = card.attack + ctx.itemBonus(heroId, Stat.ATTACK)
            for (e in ctx.enemies()) {
                val pos = e.at ?: continue
                if (ctx.distance(at, pos) > ctx.attackReach(heroId, card) + 1) continue
                val worth = ctx.heroStrikeWorth(e.id, total, card.ranged)
                if (best == null || worth > best.second) best = e.id to worth
            }
            return best
        }
    }
}

/** The job a card is doing this turn. Later choices of the turn prefer the action that serves it. */
enum class Intent {
    KILL,
    CLEAR_MINION,
    PUSH,
    DEFEND_LANE,
    RETREAT,
    FARM,
    FAST_TRAVEL,
    SET_UP,
    RESPAWN,

    /** P0: this action ends the game. */
    WIN,

    /** P1: leave a square an enemy who can still act would defeat us on. */
    SURVIVE,

    /** Strength 9+: a discard now, so turn 4 finds an empty hand. */
    SETUP,

    /** The push is already won. Move to the next zone and do not spend the attack. */
    LET_PUSH,

    /** Our throne is close. Stay and take the minions here. */
    HOLD_LINE,

    /** Double lane: the other lane wants this hero more. */
    ROTATE,

    /** Stand in the Fast Travel corridor, or on a line, when it is safe. */
    BLOCK,

    /** Strength 9+: a throne spawn an off-board enemy would use, when we survive it. */
    SPAWN_CAMP,
}
