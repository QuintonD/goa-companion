package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.heroes.BainHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Bain: a support who marks a hero and shoots a clear line. The marker is the plan.
 * Solo kills are how the marker is wasted.
 */
object BainHeuristics : HeroHeuristics {
    override val heroId: String = "bain"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 2,
        aggression = 0.45,
        laneWeight = 0.60,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val holder = BatchA.markerHolder(state, BainHero.BOUNTY) ?: return 0.0
        val sign = BatchA.sign(state, team, heroId)
        // H eval: the marker on an enemy is an extra Life. On Bain it turns his own tools off.
        val v = if (holder == heroId) -8.0 else 6.0
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchA.poolHas(ctx, heroId, CROSSBOW)) return 0.0
        val reach = ctx.attackReach(heroId, ctx.card(crossbowId(ctx)))
        if (HexGrid.distance(from, target) !in 1..reach) return 0.0
        if (!BatchA.clearOfUnits(ctx, from, target)) return 0.0
        return if (BatchA.played(ctx, heroId)?.id in CROSSBOW) 10.0 else 6.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            in GOLD -> gold(ctx, choice.cardId)
            "bain-silver" -> silver(ctx)
            in CROSSBOW -> crossbow(ctx, choice.cardId)
            in GREEN_BLOCK -> greenBlock(ctx)
            else -> 0.0
        }
    }

    // H1 / H17: mark on turn 1–2 once a hero in radius can wear it. Do not open on gold with nothing adjacent.
    private fun gold(ctx: HContext, cardId: String): Double {
        val at = ctx.own()?.at ?: return 0.0
        if (ctx.turn == 1 && !BatchA.besideEnemyUnit(ctx, at)) return -6.0
        val holder = BatchA.markerHolder(ctx.state, BainHero.BOUNTY)
        if (holder != null && holder != heroId) return 0.0
        val radius = if (cardId == "bain-handicap") 4 else 4
        val mark = BatchA.enemiesWithin(ctx, at, radius).any { it.id != holder }
        if (!mark) return -4.0
        return if (BatchA.besideEnemyUnit(ctx, at)) 8.0 else 0.0
    }

    // H5: pull only when a clear line already has a unit on it. Initiative 13 sees the board as it is.
    private fun silver(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val pull = targetsInRange(ctx, at, 4).any { hex ->
            BatchA.clearOfObstacles(ctx, at, hex)
        }
        return if (pull) 8.0 else -8.0
    }

    // H7: the crossbow is a clear line. No line, do not play it.
    private fun crossbow(ctx: HContext, cardId: String): Double {
        val at = ctx.own()?.at ?: return 0.0
        val reach = ctx.attackReach(heroId, ctx.card(cardId))
        val shot = targetsInRange(ctx, at, reach).any { hex -> BatchA.clearOfUnits(ctx, at, hex) }
        if (!shot) return -8.0
        val minion = ctx.state.minions.any { m ->
            val pos = m.at ?: return@any false
            m.team != ctx.team && ctx.distance(at, pos) in 1..reach && BatchA.clearOfUnits(ctx, at, pos) &&
                ctx.inBattleZone(pos)
        }
        return if (minion) 10.0 else 4.0
    }

    // H4: the green block is only Movement while nobody else wears the marker.
    private fun greenBlock(ctx: HContext): Double {
        val holder = BatchA.markerHolder(ctx.state, BainHero.BOUNTY)
        return if (holder == null || holder == heroId) -4.0 else 0.0
    }

    // H3: Close Call fails while Bain wears the marker, or while nobody does.
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Card || choice.cardId !in GREEN_BLOCK) return 0.0
        val holder = BatchA.markerHolder(ctx.state, BainHero.BOUNTY)
        if (holder == null || holder == heroId) return -20.0
        val total = d.context.attackTotal ?: return 8.0
        val other = d.options.filterIsInstance<Choice.Card>().any { other ->
            other.cardId !in GREEN_BLOCK && (d.context.defensePreview[other.cardId] ?: -1) >= total
        }
        return if (other) -6.0 else 10.0
    }

    // H2: give the marker to an enemy. Never to an ally while the ultimate would make them discard.
    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in GOLD || choice !is Choice.Unit) return 0.0
        val hero = ctx.hero(choice.unitId) ?: return 0.0
        if (hero.team == ctx.team) return if (BatchA.ultimateUnlocked(ctx.state, heroId)) -10.0 else -4.0
        return 8.0
    }

    // H15: keep the crossbow and tuck the Initiative item on the other red, unless green is the walk.
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "bain-red-2a" || choice.cardId == "bain-red-3a") 1.5 else 0.0
    }

    private fun crossbowId(ctx: HContext): String =
        ctx.possibleCards(heroId).firstOrNull { it.card.id in CROSSBOW }?.card?.id ?: "bain-red-1"

    private fun targetsInRange(ctx: HContext, at: Hex, reach: Int): List<Hex> {
        val out = ArrayList<Hex>()
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) in 1..reach) out += pos
        }
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team != ctx.team && ctx.distance(at, pos) in 1..reach) out += pos
        }
        return out
    }

}

private val GOLD = setOf("bain-gold", "bain-handicap")
private val CROSSBOW = setOf("bain-red-1", "bain-red-2a", "bain-red-3a")
private val GREEN_BLOCK = setOf("bain-green-1", "bain-green-2a", "bain-green-3a")
