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
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.max

/**
 * Emmitt: a support who bends the turn order. His primary red is a big punch the defender
 * answers with Initiative instead of Defense, so a fast card is the block and a slow one is not.
 * The blue traps make a hero who has already resolved a card this turn discard.
 */
object EmmittHeuristics : HeroHeuristics {
    override val heroId: String = "emmitt"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 1,
        aggression = 0.40,
        laneWeight = 0.40,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        else -> 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in PUNCH -> punch(ctx, me, at, choice.cardId)
            in SNARE -> snare(ctx, me, at, choice.cardId)
            in GOLD -> gold(ctx, me, at, choice.cardId)
            else -> 0.0
        }
    }

    /**
     * H1 / H2: Reverse Time. The card look resolves the attack; what it cannot see is the order it flips
     * next turn, which needs the attack to connect (EmmittHero: `if (!res.connected) return`). Turn 1 flips a
     * board he has not read; turn 4 has no next turn this round. Otherwise the flip is priced per ally
     * and enemy from their hands ([flipValue]), times the chance the hit connects.
     */
    private fun gold(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        if (ctx.turn == 1) return TURN_ONE
        if (ctx.turn >= 4) return 0.0
        val connect = connectChance(ctx, me, at, cardId)
        if (connect <= 0.0) return 0.0
        return connect * flipValue(ctx)
    }

    /** The chance gold connects with something adjacent: a minion it may take, or a hero who cannot block it. */
    private fun connectChance(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val total = ctx.card(cardId).attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val heroOnly = cardId == "emmitt-handicap"
        val minion = ctx.state.minions.any { BatchC.takeable(ctx, it) && ctx.distance(at, it.at!!) == 1 }
        if (!heroOnly && minion) return 1.0
        var best = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, 1)) {
            if (e.hand.isEmpty()) return 1.0
            val pos = e.at ?: continue
            val mod = ctx.minionModifier(pos, e.team)
            val items = ctx.itemBonus(e.id, Stat.DEFENSE)
            val hold = BatchASupport.chanceHolds(ctx, e) { c ->
                (c.block && c.blocksMelee) ||
                    (c.canDefend && (c.defense ?: -99) + items + (if (c.ignoresMinionModifiers) 0 else mod) >= total)
            }
            best = max(best, 1.0 - hold)
        }
        return best
    }

    /**
     * Research §evalTerms, as the order next turn: an ally's slow attack (Initiative 7 or less) that reaches
     * an enemy now goes first, worth [PAIR] when that enemy holds no card of 7 or less to leave on, and a
     * share of it when they do (they must spend that card to escape). An enemy's slow attack on an ally
     * with no card of 6 or less goes first too: the same, against us. Allies' hands are known; an enemy's
     * is the possible pool.
     */
    private fun flipValue(ctx: HContext): Double {
        var v = 0.0
        for (ally in ctx.allies()) {
            val a = ally.at ?: continue
            val items = ctx.itemBonus(ally.id, Stat.INITIATIVE)
            val hand = ctx.hand(ally.id).orEmpty()
            val slow = hand.filter { it.attackCard && it.initiative + items <= SLOW }
            for (e in ctx.enemies()) {
                val pos = e.at ?: continue
                val theirs = ctx.itemBonus(e.id, Stat.INITIATIVE)
                if (slow.any { ctx.distance(a, pos) <= ctx.attackReach(ally.id, it) + 1 }) {
                    val escape = BatchASupport.chanceHolds(ctx, e) { it.initiative + theirs <= SLOW }
                    v += PAIR * ((1.0 - escape) + escape * FORCED)
                }
                val hit = BatchASupport.chanceHolds(ctx, e) { c ->
                    c.attackCard && c.initiative + theirs <= SLOW &&
                        ctx.distance(pos, a) <= ctx.attackReach(e.id, c) + 1
                }
                if (hit <= 0.0) continue
                val out = hand.any { it.initiative + items < SLOW }
                v -= PAIR * hit * (if (out) FORCED else 1.0)
            }
        }
        return v
    }

    /**
     * H3: the punch against a hero is decided by the Initiative of the card they defend with
     * (plus Initiative items and minion modifiers). The general book read their Defense; this
     * replaces its estimate for adjacent heroes.
     */
    private fun punch(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        var best = BatchASupport.bestMinionOn(ctx, HexGrid.neighbors(at).toSet())
        for (e in BatchASupport.enemiesWithin(ctx, at, 1)) {
            best = max(best, punchWorth(ctx, e, total) * BatchASupport.aggression(ctx, me.id))
        }
        return BatchASupport.correction(ctx, me.id, at, card, best) + reversePunch(ctx, me, at, card)
    }

    /**
     * H3 in reverse: lower Initiative acts first, so a hero beside him whose card is under the punch's
     * walks before it lands. The card look's flee reads the forward order; this is the reverse one
     * (research rule 3: −8 on the hopeful punch). Not when a minion is the target anyway.
     */
    private fun reversePunch(ctx: HContext, me: HeroState, at: Hex, card: HCard): Double {
        if (!BatchASupport.effectLive(ctx, heroId, GOLD)) return 0.0
        if (ctx.state.minions.any { BatchC.takeable(ctx, it) && ctx.distance(at, it.at!!) == 1 }) return 0.0
        val ours = card.initiative + ctx.itemBonus(me.id, Stat.INITIATIVE)
        var first = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, 1)) {
            if (e.id in ctx.state.turnRecord.resolved) continue
            val theirs = ctx.itemBonus(e.id, Stat.INITIATIVE)
            val winsTie = ctx.state.tieBreaker == e.team
            first = max(
                first,
                BatchASupport.chancePlays(ctx, e.id) { c ->
                    val init = c.initiative + theirs
                    c.hasMovement && (init < ours || (init == ours && winsTie))
                },
            )
        }
        return -PAIR * first
    }

    /** The punch on [e]: a kill unless they hold a defense card whose Initiative total reaches [total]. */
    private fun punchWorth(ctx: HContext, e: HeroState, total: Int): Double {
        if (e.hand.isEmpty()) return ctx.killWorth(e.id)
        val pos = e.at ?: return 0.0
        val items = ctx.itemBonus(e.id, Stat.INITIATIVE)
        val modifier = ctx.minionModifier(pos, e.team)
        // A defense that ignores minion modifiers (Arien's Duelists) ignores them here too.
        val hold = BatchASupport.chanceHolds(ctx, e) { c ->
            val bonus = items + if (c.ignoresMinionModifiers) 0 else modifier
            (c.block && c.blocksMelee) || (c.canDefend && !c.block && c.initiative + bonus >= total)
        }
        val chance = 1.0 - hold
        return chance * ctx.killWorth(e.id) + (1.0 - chance) * ctx.discardWorth(e)
    }

    /**
     * H10: the trap hits an enemy in range who has already resolved a card this turn: forward,
     * the one whose card is faster than this one; in Reverse Time, slower. "If able" on an empty
     * hand does nothing; Time Bomb defeats instead.
     */
    private fun snare(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val ours = card.initiative + ctx.itemBonus(me.id, Stat.INITIATIVE)
        val reverse = BatchASupport.effectLive(ctx, heroId, GOLD)
        val orDefeat = cardId == "emmitt-blue-3a"
        val reach = card.range + ctx.itemBonus(me.id, Stat.RANGE)
        var best = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, reach)) {
            val theirs = ctx.itemBonus(e.id, Stat.INITIATIVE)
            // A tie across teams goes to the team on the Tie Breaker coin, in Reverse Time as well (p.7).
            val winsTie = ctx.state.tieBreaker == e.team
            val p = if (e.id in ctx.state.turnRecord.resolved) {
                1.0
            } else {
                BatchASupport.chancePlays(ctx, e.id) { c ->
                    val init = c.initiative + theirs
                    (if (reverse) init < ours else init > ours) || (init == ours && winsTie)
                }
            }
            best = max(best, p * BatchASupport.discardValue(ctx, e, orDefeat))
        }
        return if (best <= 0.0) -2.0 else best - 1.4
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Unit) return 0.0
        val src = d.sourceCardId ?: return 0.0
        // H3 at resolution: the same Initiative reading on the punch's target.
        if (src in PUNCH) {
            val e = ctx.hero(choice.unitId)?.takeIf { it.team != ctx.team } ?: return 0.0
            val total = ctx.card(src).attack + ctx.itemBonus(d.seat, Stat.ATTACK)
            return punchWorth(ctx, e, total) - ctx.heroStrikeWorth(e.id, total, false)
        }
        // H6: the Glitch is picked by the enemy who can least punish the landing: one who has acted.
        if (src == "emmitt-silver") {
            return if (choice.unitId in ctx.state.turnRecord.resolved) 2.0 else 0.0
        }
        return 0.0
    }
}

private val GOLD = setOf("emmitt-gold", "emmitt-handicap")

/** Reverse Time on turn 1 (research rule 2: −8 on the research scale). */
private const val TURN_ONE = -6.0

/** One ally–enemy pair whose order the flip decides (research §evalTerms: ±8). */
private const val PAIR = 8.0

/** Initiative that acts early in reverse: a slow red, or a green. */
private const val SLOW = 7

/** Share of [PAIR] when the hero holds the card that answers the flip: it must spend it. */
private const val FORCED = 0.3
private val PUNCH = setOf("emmitt-red-1", "emmitt-red-2a", "emmitt-red-3a")
private val SNARE = setOf("emmitt-blue-1", "emmitt-blue-2a", "emmitt-blue-3a")
