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
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * Swift (heroes/swift.md): the rifle shoots straight lines, the shotgun needs an enemy hero next to its
 * target, and gold (Reload!) performs the rightmost resolved card's primary again or defeats an adjacent
 * minion. The general book reads gold as a plain skill; these notes price the copy.
 */
object SwiftHeuristics : HeroHeuristics {
    override val heroId: String = "swift"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.55,
        laneWeight = 0.8,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    private val golds = setOf("swift-gold", "swift-handicap")
    private val shotguns = setOf("swift-red-2b", "swift-red-3b")
    private val rifles = setOf("swift-red-1", "swift-red-2a", "swift-red-3a")
    private val suppress = setOf("swift-green-1", "swift-green-2a", "swift-green-3a")

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> shotgunTarget(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        else -> 0.0
    }

    /** §6: the repeat is live while gold is unplayed and the rightmost resolved card is an attack. */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        val right = BatchC.rightmost(me) ?: return 0.0
        val gold = me.card("swift-gold") ?: return 0.0
        if (gold.zone == Zone.RESOLVED || gold.zone == Zone.DISCARD) return 0.0
        return if (right.startsWith("swift-red-")) BatchC.sign(state, heroId, team) * 2.0 else 0.0
    }

    /** The shotgun discards (or defeats) the hero standing next to its target: not printed reach on that hero. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(at, target) !in 2..4) return 0.0
        if (!BatchC.mayHold(ctx, heroId, shotguns)) return 0.0
        val team = ctx.hero(heroId)?.team
        val nextToUnit = ctx.state.minions.any { m ->
            m.team != team && m.at?.let { ctx.distance(it, target) == 1 && ctx.distance(at, it) <= 2 } == true
        }
        return if (nextToUnit) 2.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return when (id) {
            in golds -> if (ctx.patches) reload(ctx, id == "swift-gold") else 0.0
            in shotguns -> if (shotgunPair(ctx) == null) -2.5 else 1.0
            in suppress -> if (ctx.patches) suppressed(ctx, id) else 0.0
            else -> 0.0
        }
    }

    /**
     * H1 / H2: gold is worth the better of the copied primary (the rightmost resolved attack, judged from
     * here) and an adjacent minion it defeats. The general book scored it as a skill.
     */
    private fun reload(ctx: HContext, minionBullet: Boolean): Double {
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        val right = BatchC.rightmost(me)?.let { ctx.card(it) }
        val copy = when {
            right == null -> 0.0
            right.attackCard -> ctx.strikeFrom(me.id, at, right)
            right.skillCard -> 1.0
            else -> 0.0
        }
        // The handicap card has no minion bullet.
        val minion = if (!minionBullet) {
            0.0
        } else {
            ctx.state.minions.filter { m -> BatchC.takeable(ctx, m) && ctx.distance(at, m.at!!) == 1 }
                .maxOfOrNull { ctx.minionWorth(it.id) } ?: 0.0
        }
        val best = max(copy, minion)
        return min(best, 18.0) - 1.4
    }

    /**
     * H9: Suppress / Pin Down / Killing Ground make an enemy *hero* in radius discard, and only one not
     * adjacent to terrain (rulings/swift.md 7). The general book prices them as a strike on minions too;
     * with no such hero that value is not there.
     */
    private fun suppressed(ctx: HContext, id: String): Double {
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        val card = ctx.card(id)
        val radius = card.radius + ctx.itemBonus(heroId, Stat.RADIUS)
        val open = BatchC.enemiesWithin(ctx, at, radius).any { e ->
            val pos = e.at ?: return@any false
            HexGrid.neighbors(pos).none { ctx.game.geo.isTerrain(it) }
        }
        if (open) return 0.0
        return -ctx.strikeFrom(me.id, at, card).coerceAtLeast(0.0)
    }

    /** The first unit in range 2 with an enemy hero next to it, or null (rulings/swift.md 3). */
    private fun shotgunPair(ctx: HContext): String? {
        val at = ctx.own()?.at ?: return null
        val units = ctx.state.minions.filter { it.team != ctx.team && it.at != null }.map { it.id to it.at!! } +
            ctx.enemies().filter { it.at != null }.map { it.id to it.at!! }
        return units.firstOrNull { (id, pos) ->
            ctx.distance(at, pos) <= 2 &&
                ctx.enemies().any { e -> e.id != id && e.at?.let { ctx.distance(it, pos) == 1 } == true }
        }?.first
    }

    /** H5: a shotgun target with no enemy hero next to it fizzles the action (rulings/swift.md 3). */
    private fun shotgunTarget(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in shotguns || !d.prompt.startsWith("Target")) return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val pos = BatchC.posOf(ctx, id) ?: return 0.0
        val heroBeside = ctx.enemies().any { e -> e.id != id && e.at?.let { ctx.distance(it, pos) == 1 } == true }
        return if (heroBeside) 3.0 else -12.0
    }

    /** H14: with the rifle in hand, do not end her own move next to an enemy hero (it cannot shoot adjacent). */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (!BatchC.movesSelf(ctx, d)) return 0.0
        val hex = BatchC.hexOf(choice) ?: return 0.0
        if (ctx.ownHand().none { it.id in rifles }) return 0.0
        val beside = ctx.enemies().count { e -> e.at?.let { ctx.distance(hex, it) == 1 } == true }
        return -2.0 * beside
    }

    /** H13: red is what gold copies. Spend a blue that still holds when red is her only attack in hand. */
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (!id.startsWith("swift-red-") || !BatchC.meets(d, id)) return 0.0
        val otherAttack = ctx.ownHand().any { it.id != id && it.attackCard }
        val blueHolds = d.options.filterIsInstance<Choice.Card>().any {
            it.cardId.startsWith("swift-blue-") &&
                BatchC.meets(d, it.cardId)
        }
        return if (!otherAttack && blueHolds) -4.0 else 0.0
    }
}
