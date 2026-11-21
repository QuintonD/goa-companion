package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Hanu sets an ally up and leaves. The far half of his red exists only beside a friendly hero
 * (card text), so the general book's printed-range strike is cancelled when no target is legal.
 * Gold's 3-space leave happens only when the target survives (rulings/hanu.md 1).
 */
object HanuHeuristics : HeroHeuristics {
    override val heroId: String = "hanu"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        // Measured (integration, h8 ablation, 192 games): 2 / 0.45 / 0.85 made 43.8%; the default distance,
        // aggression and lane weight with his notes and upgrade order are kept instead.
        upgradeOrder = listOf("hanu-red-2a", "hanu-green-2b", "hanu-blue-2a", "red", "green", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> if (d.sourceCardId == SILVER) hurry(ctx, choice) else 0.0
        else -> 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val hanu = ctx.hero(heroId) ?: return 0.0
        val from = hanu.at ?: return 0.0
        val distance = ctx.distance(from, target)
        if (distance < 2 || distance > FAR_RANGE) return 0.0
        // H6 (enemy view): the far red shot exists only on a hero standing next to one of his allies.
        val covered = ctx.state.heroes.any { ally ->
            ally.team == hanu.team && ally.id != heroId && ally.at?.let { HexGrid.distance(it, target) == 1 } == true
        }
        if (!covered) return 0.0
        // Only the hero-targeting reds shoot a hero beside his ally (Outnumber and Pile On take a minion),
        // each at its own reach.
        return 3.0 * BatchBSupport.hold(ctx, heroId) { it.id in HERO_SHOT && ctx.attackReach(heroId, it) >= distance }
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when {
            id == "hanu-gold" -> gold(ctx, at)
            id.startsWith("hanu-red") -> red(ctx, at, ctx.card(id))
            id in JOURNEY -> journey(ctx, at, ctx.card(id))
            else -> 0.0
        }
    }

    // H1: gold on a hero who will survive it, with a straight 3 that ends away from enemy heroes: tap and leave.
    private fun gold(ctx: HContext, at: Hex): Double {
        val tap = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) == 1 } == true && ctx.handCount(e.id) > 0 }
        if (!tap || !escapeLine(ctx, at)) return 0.0
        return 2.0 + minOf(4.0, ctx.exposure(ctx.seat, at) * 0.3)
    }

    // H6/H7: the red's far bullet needs a friendly hero beside the target. With no legal target it is a walk.
    private fun red(ctx: HContext, at: Hex, card: HCard): Double {
        val reach = ctx.attackReach(ctx.seat, card)
        val adjacent = BatchBSupport.enemyUnits(ctx).any { (_, pos) -> ctx.distance(at, pos) == 1 }
        val far = farTargets(ctx, at, card.id, reach)
        if (!adjacent && far.isEmpty()) return BatchBSupport.deadStrike(ctx, at, card)
        // H6: a hero beside his ally is the team shot the card exists for.
        val teamShot = far.any { ctx.hero(it) != null }
        return if (teamShot) 2.5 else 0.0
    }

    private fun farTargets(ctx: HContext, at: Hex, cardId: String, reach: Int): List<String> {
        val minionsOnly = cardId == "hanu-red-2b" || cardId == "hanu-red-3b"
        val notAdjacent = cardId == "hanu-red-3a"
        return BatchBSupport.enemyUnits(ctx).filter { (id, pos) ->
            val d = ctx.distance(at, pos)
            val kindOk = if (minionsOnly) ctx.state.minion(id) != null else ctx.hero(id) != null
            kindOk && d in 1..reach && (!notAdjacent || d > 1) && BatchBSupport.allyBeside(ctx, pos)
        }.map { it.first }
    }

    // H8: Journey swaps out an enemy who has not acted. Not one an ally still means to hit (immune this turn).
    private fun journey(ctx: HContext, at: Hex, card: HCard): Double {
        val radius = card.radius + ctx.itemBonus(ctx.seat, Stat.RADIUS)
        val victims = ctx.enemies().filter { e ->
            e.at?.let { ctx.distance(at, it) in 1..radius } == true && e.id !in ctx.state.turnRecord.resolved
        }
        if (victims.isEmpty()) return -2.0
        val allyHits = victims.all { v ->
            ctx.allies().any { ally ->
                ally.id !in ctx.state.turnRecord.resolved &&
                    ally.at?.let { a -> v.at?.let { ctx.distance(a, it) <= 2 } } == true
            }
        }
        return if (allyHits) -3.0 else 1.5
    }

    // H3/H4/H5: Hurry up! An ally's slow attack goes to 11; a fast enemy card drops to 11; with the ultimate,
    // the enemy whose next action Hanu then chooses.
    private fun hurry(ctx: HContext, choice: Choice): Double {
        val unit = (choice as? Choice.Unit)?.unitId ?: return 0.0
        val body = ctx.heroBody(unit) ?: return 0.0
        val hero = ctx.hero(body) ?: return 0.0
        val card = hero.unresolved.firstOrNull()?.let { ctx.card(it.id) } ?: return 0.0
        val ally = hero.team == ctx.team
        val ultimate = ctx.own()?.ultimateUnlocked == true
        return when {
            // H4: the Ultimate Trick makes the enemy's next action his.
            !ally && ultimate -> 12.0

            // H3: an ally's attack at 10 or less now acts before the enemy can walk out of it.
            ally && (card.attackCard || card.forcesDiscard) && card.initiative <= HURRY - 1 -> 6.0

            ally && card.initiative >= HURRY -> -4.0

            // H5: a fast enemy card now waits for initiative 11.
            !ally && card.initiative > HURRY -> 4.0

            !ally -> -2.0

            else -> 0.0
        }
    }

    private fun escapeLine(ctx: HContext, from: Hex): Boolean {
        for (step in HexGrid.DIRECTIONS) {
            var cursor = from
            var open = true
            for (i in 1..3) {
                cursor += step
                if (!ctx.game.isEmpty(cursor)) {
                    open = false
                    break
                }
            }
            if (open && !BatchBSupport.enemyHeroWithin(ctx, cursor, 1)) return true
        }
        return false
    }

    private const val SILVER = "hanu-silver"
    private const val HURRY = 11
    private const val FAR_RANGE = 4
    private val JOURNEY = setOf("hanu-blue-1", "hanu-blue-2a", "hanu-blue-3a")
    private val HERO_SHOT = setOf("hanu-red-1", "hanu-red-2a", "hanu-red-3a")
}
