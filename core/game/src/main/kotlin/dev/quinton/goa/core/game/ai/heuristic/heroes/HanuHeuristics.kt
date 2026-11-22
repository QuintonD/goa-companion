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
import dev.quinton.goa.core.game.engine.HeroState
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

    /** With the victim known: the reach above, priced by what that card costs them (#19), not a flat 3.0. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex, victimId: String): Double {
        val plain = threatAdjust(ctx, enemyHeroId, target)
        if (plain <= 0.0) return plain
        return PlaybookSupport.extraReach(ctx, heroId, victimId, target, HERO_SHOT)
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when {
            id == "hanu-gold" -> gold(ctx, at)
            id == SILVER -> hurryOutlook(ctx, at)
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

    /**
     * H3 / H4 / H5: Hurry up! sets a revealed, unresolved card to Initiative 11 (items still add), at
     * Initiative 12, when the order is public (REVIEW-2-design §5.3). Its worth is the pairs of an ally and
     * an enemy whose order it flips: an ally's attack now before the enemy it reaches walks (more when
     * that enemy's card moves), an ally's escape now before the attack on it, and the same pairs against
     * us when the flip goes the other way. A card already at 11 changes nothing. With the Ultimate, the
     * enemy's next action is his: worth what their card would have done, and its movement in his hands.
     */
    private fun hurry(ctx: HContext, choice: Choice): Double {
        val unit = (choice as? Choice.Unit)?.unitId ?: return 0.0
        val body = ctx.heroBody(unit) ?: return 0.0
        return hurryOn(ctx, ctx.hero(body) ?: return 0.0)
    }

    /**
     * Hurry up! at the card play: nothing in the card look prices the order it changes, so the best hurry
     * among heroes in range whose card is already down on this copy (our own team's known, an enemy's from
     * the sample), discounted for the cards still to be played and for the guess.
     */
    private fun hurryOutlook(ctx: HContext, at: Hex): Double {
        val card = ctx.card(SILVER)
        val reach = maxOf(card.range, card.radius) + ctx.itemBonus(ctx.seat, Stat.RANGE)
        val best = ctx.state.heroes.filter { h ->
            h.id != ctx.seat && h.unresolved.isNotEmpty() && h.at?.let { ctx.distance(at, it) <= reach } == true
        }.maxOfOrNull { hurryOn(ctx, it) } ?: return 0.0
        return OUTLOOK * best.coerceAtLeast(0.0)
    }

    private fun hurryOn(ctx: HContext, hero: HeroState): Double {
        val card = hero.unresolved.firstOrNull()?.let { ctx.card(it.id) } ?: return 0.0
        val ally = hero.team == ctx.team
        if (!ally && ctx.own()?.ultimateUnlocked == true) return steal(ctx, hero, card)
        val items = ctx.itemBonus(hero.id, Stat.INITIATIVE)
        val before = card.initiative + items
        val after = HURRY + items
        if (before == after) return NOTHING
        var v = 0.0
        for (other in ctx.state.heroes) {
            if (other.id == hero.id || other.id == ctx.seat || other.team == hero.team) continue
            if (other.at == null || !ctx.stillToAct(other.id)) continue
            val oc = other.unresolved.firstOrNull()?.let { ctx.card(it.id) } ?: continue
            val oi = oc.initiative + ctx.itemBonus(other.id, Stat.INITIATIVE)
            val was = actsFirst(ctx, hero, before, other, oi)
            val now = actsFirst(ctx, hero, after, other, oi)
            if (was == now) continue
            // The pair's order flips. Ours is the hero on our team; it now goes first or now goes second.
            val (us, usCard, them, themCard) = if (ally) {
                Quad(hero, card, other, oc)
            } else {
                Quad(other, oc, hero, card)
            }
            val oursFirst = if (ally) now else !now
            val gain = firstWorth(ctx, us, usCard, them, themCard)
            v += if (oursFirst) gain else -gain
        }
        return v
    }

    /** [a] acting before [b] at these Initiatives; a tie across teams goes to the Tie Breaker coin's team. */
    private fun actsFirst(ctx: HContext, a: HeroState, ai: Int, b: HeroState, bi: Int): Boolean =
        ai > bi || (ai == bi && ctx.state.tieBreaker == a.team)

    /** What our hero [us] gains by acting before their hero [them], each on its revealed card. */
    private fun firstWorth(ctx: HContext, us: HeroState, usCard: HCard, them: HeroState, themCard: HCard): Double {
        val a = us.at ?: return 0.0
        val b = them.at ?: return 0.0
        val d = ctx.distance(a, b)
        var v = 0.0
        // Our attack lands before they walk out of it (a card that moves them is the escape).
        if ((usCard.attackCard || usCard.forcesDiscard) && d <= ctx.attackReach(us.id, usCard)) {
            v += if (themCard.hasMovement) HIT_BEFORE_ESCAPE else HIT_FIRST
        }
        // Their attack would land on us first; now our movement card leaves before it.
        if ((themCard.attackCard || themCard.forcesDiscard) && d <= ctx.attackReach(them.id, themCard) + 1) {
            v += if (usCard.hasMovement) ESCAPE_FIRST else HIT_FIRST * 0.5
        }
        return v
    }

    /** H4: the Ultimate makes their next action his: their strike denied, and their movement his to use. */
    private fun steal(ctx: HContext, e: HeroState, card: HCard): Double {
        val at = e.at ?: return STEAL_BASE
        val strike = if (card.attackCard || card.forcesDiscard) {
            ctx.strikeFrom(e.id, at, card).coerceIn(0.0, STEAL_CAP)
        } else {
            0.0
        }
        return STEAL_BASE + strike + if (card.hasMovement) STEAL_MOVE else 0.0
    }

    private data class Quad(val us: HeroState, val usCard: HCard, val them: HeroState, val themCard: HCard)

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

    /** Share of the best hurry counted at the card play (the order is a guess until the reveal). */
    private const val OUTLOOK = 0.6

    /** A hurry that changes no order: a wasted target. */
    private const val NOTHING = -1.0

    /** Our attack now before the enemy it reaches, whose card moves (research H3: +6). */
    private const val HIT_BEFORE_ESCAPE = 6.0

    /** Our attack, or theirs, before the other when nobody walks. */
    private const val HIT_FIRST = 2.5

    /** Our movement card now before the attack on us (research H5: +4). */
    private const val ESCAPE_FIRST = 4.0

    /** The Ultimate's steal: the action itself, their strike (capped), their movement. */
    private const val STEAL_BASE = 4.0
    private const val STEAL_CAP = 15.0
    private const val STEAL_MOVE = 3.0
    private const val FAR_RANGE = 4
    private val JOURNEY = setOf("hanu-blue-1", "hanu-blue-2a", "hanu-blue-3a")
    private val HERO_SHOT = setOf("hanu-red-1", "hanu-red-2a", "hanu-red-3a")
}
