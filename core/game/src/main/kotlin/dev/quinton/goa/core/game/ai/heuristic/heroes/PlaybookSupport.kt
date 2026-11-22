package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Shared by every playbook (REVIEW-2-design §5.4, #19): a threat beyond printed reach priced from the possible
 * pool, not a flat number behind an all-or-nothing "may hold". Reads the deciding view only: a hidden hand is
 * [HContext.possibleCards], a known one (own team, strength 10) is the real hand.
 */
internal object PlaybookSupport {
    /**
     * The extra danger [enemyId] puts on [victimId] standing at [target] with one of [ids] (a reach the general
     * threat map does not draw): the chance that card is the one they play, about one card in the pool's
     * weight, times what it costs the victim there ([loss]). [bonus] is Attack the text adds; the best card of
     * [ids] in the pool counts.
     */
    fun extraReach(
        ctx: HContext,
        enemyId: String,
        victimId: String,
        target: Hex,
        ids: Set<String>,
        bonus: Int = 0,
    ): Double {
        val victim = ctx.hero(victimId) ?: return 0.0
        if (ctx.hero(enemyId)?.team == victim.team) return 0.0
        val share = BatchASupport.chancePlays(ctx, enemyId) { it.id in ids }
        if (share <= 0.0) return 0.0
        val worst = ctx.possibleCards(enemyId).filter { it.card.id in ids }
            .maxOfOrNull { loss(ctx, enemyId, victim, target, it.card, bonus) } ?: return 0.0
        return PLAYS * share * worst
    }

    /**
     * The same for Attack the text adds to a reach the map already draws (Trinkets' Turret line): only the
     * difference [bonus] makes to what the hit costs.
     */
    fun extraAttack(
        ctx: HContext,
        enemyId: String,
        victimId: String,
        target: Hex,
        ids: Set<String>,
        bonus: Int,
    ): Double {
        val victim = ctx.hero(victimId) ?: return 0.0
        if (ctx.hero(enemyId)?.team == victim.team) return 0.0
        val share = BatchASupport.chancePlays(ctx, enemyId) { it.id in ids }
        if (share <= 0.0) return 0.0
        val worst = ctx.possibleCards(enemyId).filter { it.card.id in ids }.maxOfOrNull {
            loss(ctx, enemyId, victim, target, it.card, bonus) - loss(ctx, enemyId, victim, target, it.card, 0)
        } ?: return 0.0
        return PLAYS * share * worst.coerceAtLeast(0.0)
    }

    /**
     * What [card] of [enemyId] costs [victim] at [target]. An attack: the kill when no defense they may hold
     * meets it (an empty or about-to-empty hand always), else the card they throw. A discard: the card, or
     * the kill on an empty hand when the text defeats.
     */
    fun loss(ctx: HContext, enemyId: String, victim: HeroState, target: Hex, card: HCard, bonus: Int = 0): Double {
        val kill = ctx.killWorth(victim.id)
        val card0 = ctx.discardWorth(victim)
        if (!card.attackCard) {
            if (!card.forcesDiscard && !card.discardOrDefeat) return 0.0
            val empty = victim.hand.isEmpty() || ctx.defenseless(victim.id)
            return if (empty && card.discardOrDefeat) kill else card0
        }
        if (victim.hand.isEmpty() || ctx.defenseless(victim.id)) return kill
        val total = card.attack + ctx.itemBonus(enemyId, Stat.ATTACK) + bonus
        val mod = ctx.minionModifier(target, victim.team)
        val items = ctx.itemBonus(victim.id, Stat.DEFENSE)
        val holds = BatchASupport.chanceHolds(ctx, victim) { c ->
            (card.ranged && c.blocksRanged) || (!card.ranged && c.blocksMelee) ||
                (c.canDefend && (c.defense ?: -99) + items + (if (c.ignoresMinionModifiers) 0 else mod) >= total)
        }
        return (1.0 - holds) * kill + holds * card0
    }

    /**
     * Weight in 0..1 of [heroId]'s possible pool that passes [test]: a share, never an all-or-nothing gate
     * (the review's dead gates: one card outside the set turned the whole term off).
     */
    fun poolShare(ctx: HContext, heroId: String, test: (HCard) -> Boolean): Double {
        val pool = ctx.possibleCards(heroId)
        val total = pool.sumOf { it.weight }
        if (total <= 0.0) return 0.0
        return pool.filter { test(it.card) }.sumOf { it.weight } / total
    }

    /**
     * The general book's movement outlook for a card (General.moveOutlook, private there; kept in step by
     * hand). The one copy the strike corrections of BatchB and BatchD share.
     */
    fun moveOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val far = ctx.distanceToBattleZone(at)
        if (far <= 0) return 0.15
        return minOf(far * 0.4, card.movement * 0.55)
    }

    /** Share of a card's threat the map counts this turn and the next (0.65 + 0.22 there). */
    private const val PLAYS = 0.85
}
