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
            if (waveOn(ctx, "plan")) reposition(ctx, at, played)?.let { return it }
            if (ctx.profile.on("hold", waveOn(ctx, "plan"))) holdLine(ctx, h.id, at, played)?.let { return it }
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

        /**
         * F7 (knob `hold`): we stand in our last zone (the next push against us takes our Throne, or the
         * last wave decides) and we are not ahead on minions. Stay in the zone and take their minions:
         * a hero only when the kill is sure and worth as much.
         */
        private fun holdLine(ctx: HContext, heroId: String, at: Hex, card: HCard): TurnPlan? {
            val zone = ctx.zoneOf(at) ?: return null
            val lane = ctx.lanes().firstOrNull { ctx.battleZone(it) == zone } ?: return null
            val last = stepsToEnemyThrone(ctx, lane, ctx.team.opponent) <= 1 || (ctx.state.wavesLeft[lane] ?: 9) <= 1
            // Track W (`wave.plan`): "not ahead" is the projected battle, not today's count.
            val ahead = if (waveOn(ctx, "plan")) {
                waveView(ctx, lane).state == WaveState.WINNING
            } else {
                ctx.laneMargin(lane) > 0
            }
            if (!last || ahead) return null
            if (!card.attackCard) return TurnPlan(Intent.HOLD_LINE, null, card.id)
            val minion = bestMinion(ctx, heroId, at, card) ?: return null
            val hero = bestHero(ctx, heroId, at, card)
            val sure = hero?.first?.let { ctx.defenseless(it) } == true && hero.second >= minion.second
            return if (sure) null else TurnPlan(Intent.HOLD_LINE, minion.first, card.id)
        }

        /**
         * Track W (`wave.plan`, GENERAL §1 "By lane state", G41): the lane is projected to push against us into a
         * zone this hero is more than one walk from, and the turns left in the round are no more than the walks
         * it needs (or the push is already decided): this movement card goes there now. Earlier in the round the
         * hero farms first; the gold on the way is the look's to take.
         */
        private fun reposition(ctx: HContext, at: Hex, card: HCard): TurnPlan? {
            if (!card.hasMovement || card.attackCard) return null
            for (lane in ctx.lanes()) {
                val bz = ctx.battleZone(lane) ?: continue
                val view = waveView(ctx, lane)
                if (!view.losing) continue
                val into = view.pushInto ?: continue
                val d = ctx.distanceToZone(at, into)
                if (d <= WALK || ctx.distanceToZone(at, bz) > LANE_NEAR) continue
                val walks = (d + WALK - 1) / WALK
                val after = (4 - ctx.turn).coerceAtLeast(0)
                if (view.state == WaveState.LOST || walks > after) return TurnPlan(Intent.RETREAT, null, card.id)
            }
            return null
        }

        private fun attackPlan(ctx: HContext, heroId: String, at: Hex, card: HCard): TurnPlan {
            val bestMinion = bestMinion(ctx, heroId, at, card)
            val bestHero = bestHero(ctx, heroId, at, card)
            val minionWorth = bestMinion?.second ?: 0.0
            val heroWorth = bestHero?.second ?: 0.0
            val killNow = bestHero?.first?.let { ctx.defenseless(it) } == true
            val setup = ctx.profile.twoTurnPlans && bestHero != null && !killNow && heroWorth + 6.0 >= minionWorth &&
                ctx.state.hero(bestHero.first)?.let { stripSetsUp(ctx, it) } == true
            // Track W (`wave.farm`, P3/P6): unless the kill is real, the safe minion is the plan.
            if (waveOn(ctx, "farm") && bestMinion != null && bestHero != null && !killNow && !setup &&
                !realKill(ctx, heroId, bestHero.first, card)
            ) {
                return TurnPlan(if (minionWorth >= 14.0) Intent.PUSH else Intent.FARM, bestMinion.first, card.id)
            }
            return when {
                killNow && heroWorth >= minionWorth -> TurnPlan(Intent.KILL, bestHero?.first, card.id)
                setup -> TurnPlan(Intent.SETUP, bestHero?.first, card.id)
                minionWorth >= 14.0 && minionWorth >= heroWorth -> TurnPlan(Intent.PUSH, bestMinion?.first, card.id)
                heroWorth >= 8.0 && heroWorth >= minionWorth -> TurnPlan(Intent.KILL, bestHero?.first, card.id)
                bestMinion != null -> TurnPlan(Intent.FARM, bestMinion.first, card.id)
                else -> TurnPlan(Intent.DEFEND_LANE, null, card.id)
            }
        }

        /**
         * Track W (P3): the kill this card can make is real: the target has no defense left (G11), the hit beats what
         * it can hold more often than not, or a teammate who can reach it follows up (G14, G60).
         */
        internal fun realKill(ctx: HContext, heroId: String, targetId: String, card: HCard): Boolean {
            val total = card.attack + ctx.itemBonus(heroId, Stat.ATTACK)
            val chance = ctx.waveKillChance(targetId, total, card.ranged)
            if (chance >= REAL_KILL) return true
            val target = ctx.state.hero(targetId) ?: return false
            return chance >= ALLY_KILL && allyHunting(ctx, target)
        }

        private const val WALK = 3
        private const val LANE_NEAR = 6
        private const val REAL_KILL = 0.5
        private const val ALLY_KILL = 0.3

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
