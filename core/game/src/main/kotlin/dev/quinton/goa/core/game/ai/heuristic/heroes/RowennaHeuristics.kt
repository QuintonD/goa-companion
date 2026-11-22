package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Rowenna (heroes/rowenna.md) stands in the fight with Defense 6–8 reds, hands coins to allies, and
 * executes an empty hand. Gold lets the target retrieve before the swing, so a fat card in their
 * discard is a heal for them (CCplusplus, BGG 3447239; rulings/rowenna.md 1).
 */
object RowennaHeuristics : HeroHeuristics {
    override val heroId: String = "rowenna"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.60,
        laneWeight = 1.1,
        upgradeOrder = listOf(
            "rowenna-red-2a",
            "rowenna-red-3a",
            "rowenna-green-2a",
            "rowenna-green-3a",
            "red",
            "blue",
            "green",
        ),
        keepDefender = true,
    )

    private val golds = setOf("rowenna-gold", "rowenna-handicap")
    private val executes = setOf("rowenna-blue-2b", "rowenna-blue-3b")
    private val shots = setOf("rowenna-green-2b", "rowenna-green-3b")

    /**
     * H11: the execute, when it is all but certain. The window is an adjacent enemy hero who will have no
     * card in hand when her blue resolves: an empty hand, or the last card about to be played
     * ([HContext.defenseless]), and still beside her at Initiative 10: they act after her, or have already
     * resolved (REVIEW-2-design §5.3). A likelier-than-not execute below that is priced at the card play.
     */
    override fun override(ctx: HContext, d: Decision): Choice? {
        if (d.kind != DecisionKind.PLAY_CARD) return null
        val me = ctx.own() ?: return null
        val card = executes.firstOrNull { BatchC.inHand(me, it) } ?: return null
        val (_, p) = bestExecute(ctx, me, card) ?: return null
        if (p < SURE) return null
        return d.options.firstOrNull { it is Choice.Card && it.cardId == card }
    }

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> target(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    /** Silver places her next to a hero in range 4: beyond Movement 2, but the hit comes a turn later. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchC.mayHold(ctx, heroId, setOf("rowenna-silver"))) return 0.0
        return if (ctx.distance(at, target) in 3..4) 1.5 else 0.0
    }

    /**
     * The execute as a threat: a hero with no defense left ([HContext.defenseless]) standing beside her is
     * defeated by alt blue whatever its Defense, while she may hold it (a known hand is 0 or 1).
     */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex, victimId: String): Double {
        val plain = threatAdjust(ctx, enemyHeroId, target)
        if (enemyHeroId != heroId) return plain
        val at = ctx.hero(heroId)?.at ?: return plain
        if (ctx.distance(at, target) != 1 || !ctx.defenseless(victimId)) return plain
        val hold = BatchBSupport.hold(ctx, heroId) { it.id in executes }
        return plain + hold * THREAT_PLAYS * ctx.killWorth(victimId)
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (id) {
            in executes -> execute(ctx, me, id)

            in shots -> openingShots(ctx, me, at) + BatchC.heroOnlyDiscard(ctx, id, BatchC.radiusOf(ctx, id))

            "rowenna-silver" -> if (BatchC.enemiesWithin(ctx, at, 4).isEmpty()) -2.0 else 0.0

            in golds -> goldTarget(ctx, at)

            else -> 0.0
        }
    }

    /** H7 / H8: both discards must be empty; then it is a free card off them, best on turn 1. */
    private fun openingShots(ctx: HContext, me: HeroState, at: Hex): Double {
        if (me.cards.any { it.zone == Zone.DISCARD }) return -1.5
        val victim = BatchC.enemiesWithin(ctx, at, 3).any { e ->
            e.hand.isNotEmpty() &&
                e.cards.none { it.zone == Zone.DISCARD }
        }
        return when {
            !victim -> -1.5
            ctx.turn == 1 -> 3.0
            else -> 1.5
        }
    }

    /** H1 at the card: gold whose only hero target heals from the discard is worse than it looks. */
    private fun goldTarget(ctx: HContext, at: Hex): Double {
        val adjacent = ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) == 1 } == true }
        if (adjacent.isEmpty()) return 0.0
        val minion = ctx.state.minions.any { m -> BatchC.takeable(ctx, m) && ctx.distance(at, m.at!!) == 1 }
        return if (!minion && adjacent.all { discardHolds(ctx, it) }) -2.5 else 0.0
    }

    private fun target(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.unitId(choice) ?: return 0.0
        if (d.sourceCardId !in golds || !d.prompt.startsWith("Target")) return 0.0
        val hero = ctx.state.hero(id) ?: return 0.0
        // H1: gold lets the target retrieve before the attack. A discard that meets the swing is their card back.
        return if (hero.team != ctx.team && discardHolds(ctx, hero)) -6.0 else 0.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        // H13: gold can retrieve the red she blocks with; keep gold when a cheaper card also holds.
        return if (id in golds && BatchC.meets(d, id) && BatchC.otherMeets(d, id)) -2.5 else 0.0
    }

    /**
     * H11 / H12 at the card play. A likely execute is its kill: chance × [HContext.killWorth]. The card look
     * resolves alt blue on the position as it is now, so it already sees a hand that is empty now (and not
     * that the hero may act first and step away): there the term is the correction for that chance. A hero
     * whose last card is still in hand is invisible to the look, so the whole kill is added. With no
     * window, H12: the execute is also the Defense 6–7 block; do not walk with it before turn 4.
     */
    private fun execute(ctx: HContext, me: HeroState, cardId: String): Double {
        val (victim, p) = bestExecute(ctx, me, cardId) ?: return if (ctx.turn < 4) -4.0 else 0.0
        val kill = ctx.killWorth(victim.id) * (if (cardId == "rowenna-blue-3b") TRIUMPH else 1.0)
        val seen = ctx.looking && victim.hand.isEmpty()
        return if (seen) -(1.0 - p) * kill else p * kill
    }

    /** The adjacent enemy hero the execute most likely defeats, with that chance; null when none can be. */
    private fun bestExecute(ctx: HContext, me: HeroState, cardId: String): Pair<HeroState, Double>? {
        val at = me.at ?: return null
        val init = ctx.card(cardId).initiative + ctx.itemBonus(me.id, Stat.INITIATIVE)
        var best: Pair<HeroState, Double>? = null
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) != 1 || !ctx.defenseless(e.id)) continue
            // Acting first, they may walk off (or put her down) before Initiative 10. Resolved: they stay.
            val first = if (e.id in ctx.state.turnRecord.resolved) 0.0 else ctx.actsBefore(e.id, init)
            val p = 1.0 - first * LEAVES
            if (best == null || p > best.second) best = e to p
        }
        return best
    }

    /** A faceup card in their discard meets gold's Attack 4 + items (public: discards are faceup). */
    private fun discardHolds(ctx: HContext, hero: HeroState): Boolean {
        val at = hero.at ?: return false
        val total = ctx.card("rowenna-gold").attack + ctx.itemBonus(ctx.seat, Stat.ATTACK)
        val bonus = ctx.itemBonus(hero.id, Stat.DEFENSE) + ctx.minionModifier(at, hero.team)
        return hero.cards.any { c ->
            c.zone == Zone.DISCARD && !c.faceDown && (ctx.card(c.id).defense ?: -99) + bonus >= total
        }
    }

    /** At or above this chance the execute is played outright (H11). */
    private const val SURE = 0.8

    /** Share of the heroes who act before her that leave her side first. */
    private const val LEAVES = 0.75

    /** Chance she spends the execute on a defenseless hero beside her, as the enemy's threat. */
    private const val THREAT_PLAYS = 0.8

    /** Glorious Triumph: the kill plus triple assist coins for her allies. */
    private const val TRIUMPH = 1.1
}
