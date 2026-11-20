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
            var bestMinion: Pair<String, Double>? = null
            for (m in ctx.state.minions) {
                val pos = m.at ?: continue
                if (m.team == ctx.team || ctx.heavyImmune(m.id)) continue
                if (ctx.distance(at, pos) > ctx.attackReach(heroId, card) + 1) continue
                val worth = ctx.minionWorth(m.id)
                if (bestMinion == null || worth > bestMinion.second) bestMinion = m.id to worth
            }
            var bestHero: Pair<String, Double>? = null
            val total = card.attack + ctx.itemBonus(heroId, Stat.ATTACK)
            for (e in ctx.enemies()) {
                val pos = e.at ?: continue
                if (ctx.distance(at, pos) > ctx.attackReach(heroId, card) + 1) continue
                val worth = ctx.heroStrikeWorth(e.id, total, card.ranged)
                if (bestHero == null || worth > bestHero.second) bestHero = e.id to worth
            }
            val minionWorth = bestMinion?.second ?: 0.0
            val heroWorth = bestHero?.second ?: 0.0
            val picked = bestHero
            val killNow = picked?.first?.let { ctx.defenseless(it) } == true
            if (killNow && heroWorth >= minionWorth) {
                return TurnPlan(Intent.KILL, picked.first, card.id)
            }
            if (ctx.profile.twoTurnPlans && picked != null && !killNow && heroWorth + 6.0 >= minionWorth) {
                val enemy = ctx.state.hero(picked.first)
                if (enemy != null && stripSetsUp(ctx, enemy)) return TurnPlan(Intent.SETUP, picked.first, card.id)
            }
            if (minionWorth >= 14.0 && minionWorth >= heroWorth) {
                return TurnPlan(Intent.PUSH, bestMinion?.first, card.id)
            }
            if (heroWorth >= 8.0 && heroWorth >= minionWorth) {
                return TurnPlan(Intent.KILL, bestHero?.first, card.id)
            }
            if (bestMinion != null) return TurnPlan(Intent.FARM, bestMinion.first, card.id)
            return TurnPlan(Intent.DEFEND_LANE, null, card.id)
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
