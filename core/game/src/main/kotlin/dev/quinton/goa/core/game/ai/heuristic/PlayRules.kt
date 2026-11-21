package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.max
import kotlin.math.min

/**
 * Which card to play (GENERAL.md §5 and G1–G22). [score] is the static total, below strength 5.
 * [add] is the part a [CardSim] look does not already contain: the round's shape and plans that pay
 * off on a later turn.
 *
 * Inside the look: G1 (a win is a won position), G2 and G7–G9 (the threat split by initiative), G5 and
 * G6 (Gold before Red to farm; a Blue or Green Fast Travel before Red or Gold: colour tempo, the hand's
 * worth and G35 inside the look), G11 (a hand that will be empty is a kill), G12 (a card with nothing to
 * do scores its Hold), G13 (the push is in the lane value), G20 (the respawn is played out).
 * Not implemented: G21 (rotating to the other lane of a double-lane map).
 */
internal object PlayRules {
    fun score(ctx: HContext, choice: Choice): Double = eval(ctx, choice) + add(ctx, choice, simulated = false)

    fun eval(ctx: HContext, choice: Choice): Double {
        if (choice is Choice.Pass) return 0.0 // G22
        if (choice !is Choice.Card) return 0.0
        val h = ctx.own() ?: return 0.0
        val card = ctx.card(choice.cardId)
        val at = h.at ?: return offBoard(ctx, card) // G20
        val outlook = outlook(ctx, h, at, card)
        var v = colourTempo(card, ctx.turn) + outlook
        v += winNow(ctx, h, at, card) // G1
        v += spareCost(ctx, h, card, outlook) // G3
        v += noTarget(card, outlook) // G12
        if (ctx.profile.initiativeReading) v += initiative(ctx, h, at, card) // G7 G9
        return v
    }

    /**
     * Commitments a look must not drop (G4, G10, G14, G15), plus, when [simulated] is false, the
     * rules a [CardSim] look already contains (G2 the trap, G13 the bad push, G18 the even trade).
     * A simulated card also keeps the round's shape here (colour tempo, the spare): the engine
     * sees one action, not the turns after it.
     */
    fun add(ctx: HContext, choice: Choice, simulated: Boolean): Double {
        if (choice !is Choice.Card) return 0.0
        val h = ctx.own() ?: return 0.0
        val card = ctx.card(choice.cardId)
        val at = h.at ?: return 0.0
        var v = 0.0
        if (simulated) {
            v += colourTempo(card, ctx.turn) * ctx.profile.knob("tempo", 1.0)
            v += keepSpare(ctx, h, card) // G3
        } else {
            if (trappedBy(ctx, at, card)) v -= fear(ctx) * ctx.killWorth(h.id) // G2
            v += avoidPush(ctx, h, at, card) // G13
            v += avoidTrade(ctx, h, at, card) // G18
        }
        v += earlyRed(ctx, h, at, card) * ctx.profile.knob("earlyRed", 1.0) // G4
        v += strip(ctx, h, at, card) // G10
        v += teamKill(ctx, h, card) // G14 G15
        v += pressLead(ctx, h, at, card) // G19
        return v
    }

    /**
     * G19 (from 9): we can afford deaths and they cannot; a card that reaches their empty-handed hero
     * is worth more than the kill alone, because the threat keeps them off the minions.
     */
    private fun pressLead(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (!ctx.profile.twoTurnPlans || !(card.attackCard || card.forcesDiscard)) return 0.0
        if (ctx.deathsToLose(h.team) < 3) return 0.0
        val reach = ctx.attackReach(h.id, card).coerceAtLeast(1)
        val target = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.deathsToLose(e.team) <= 1 && ctx.defenseless(e.id) && ctx.distance(at, pos) <= reach
        }
        return if (target) PRESS else 0.0
    }

    /**
     * G3 for a simulated card: the best defense in hand is the spare, kept until turn 4. G16: once no
     * enemy can still attack this round, the spare may be spent.
     */
    private fun keepSpare(ctx: HContext, h: HeroState, card: HCard): Double {
        if (ctx.turn >= 4 || ctx.spare(h.id) - 1 >= 1 || !enemiesHaveAttacks(ctx)) return 0.0
        return if (bestDefense(ctx, h, card)) -spareWeightOf(ctx.turn) * ctx.profile.knob("spare", 1.0) else 0.0
    }

    private fun offBoard(ctx: HContext, card: HCard): Double {
        // G20: the cheapest card that still gets us back. Red and Gold stay in hand.
        var v = if (card.hasMovement) 4.0 else -card.rating
        if (card.color == CardColor.RED) v -= 4.0
        if (card.color == CardColor.GOLD && card.hasMovement) v -= 2.0
        return v
    }

    private fun colourTempo(card: HCard, turn: Int): Double = when (card.color) {
        CardColor.BLUE -> when {
            turn >= 4 -> 0.8
            turn == 3 -> -0.4
            else -> -1.6
        }

        CardColor.RED -> when {
            turn <= 1 -> -1.2
            turn == 2 -> -0.4
            else -> 0.6
        }

        CardColor.GOLD -> if (turn <= 2) 0.45 else 0.2
        CardColor.GREEN, CardColor.SILVER -> if (turn <= 2) 0.65 else 0.15
        else -> 0.0
    }

    private fun outlook(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        val strike = if (card.attackCard || card.forcesDiscard) ctx.strikeFrom(h.id, at, card) else 0.0
        val move = if (card.hasMovement) moveOutlook(ctx, at, card) else 0.0
        val skill = if (card.skillCard && !card.attackCard) skillOutlook(ctx, at, card) else 0.0
        var v = max(strike, max(move, skill))
        if (strike > 0.5 && move > 0.3) v += 0.35
        return v
    }

    private fun moveOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val goal = ctx.goalZone(ctx.seat)
        val far = if (goal != null) ctx.distanceToZone(at, goal) else ctx.distanceToBattleZone(at)
        if (far <= 0) return 0.2
        return min(far * 0.85, card.movement * 0.7)
    }

    private fun skillOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val r = max(card.range, card.radius) + 1
        val near = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) <= r } == true } ||
            ctx.state.minions.any { m ->
                m.team != ctx.team && m.at?.let { ctx.distance(at, it) <= r } == true
            }
        return if (near) 2.2 else 0.25
    }

    /** G1: the last minion at match point, or a hero whose next defeat spends the last Life. */
    private fun winNow(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (!card.attackCard && !card.forcesDiscard) return 0.0
        val reach = ctx.attackReach(h.id, card).coerceAtLeast(1)
        var v = 0.0
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team || ctx.heavyImmune(m.id) || ctx.distance(at, pos) > reach) continue
            if (ctx.minionCount(m.team, m.lane) == 1 && ctx.matchPoint(m.lane)) v = max(v, 28.0)
        }
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) > reach) continue
            if (ctx.defenseless(e.id) && ctx.deathsToLose(e.team) <= 1) v = max(v, 36.0)
        }
        return v
    }

    /**
     * G3: spending the spare before turn 4 costs its insurance, unless this card is a win,
     * a real kill, or the minion that takes the lane. G16 drops the cost once their attacks are gone.
     */
    private fun spareCost(ctx: HContext, h: HeroState, card: HCard, outlook: Double): Double {
        if (ctx.turn >= 4) return 0.0
        if (ctx.spare(h.id) - 1 >= 1) return 0.0
        if (!enemiesHaveAttacks(ctx)) return 0.0
        if (outlook >= 14.0 || card.color == CardColor.RED && ctx.turn >= 3 && outlook >= 8.0) return 0.0
        var cost = spareWeightOf(ctx.turn)
        if (card.canDefend && bestDefense(ctx, h, card)) cost += 2.5
        return -cost
    }

    private fun bestDefense(ctx: HContext, h: HeroState, card: HCard): Boolean {
        val best = ctx.ownHand().filter { it.canDefend }.maxOfOrNull { it.defense ?: 0 } ?: return false
        return card.canDefend && (card.defense ?: 0) >= best
    }

    /** G12: a primary with nothing to do is a Hold waiting to happen. */
    private fun noTarget(card: HCard, outlook: Double): Double =
        if (outlook < 0.8 && !card.hasMovement) -8.0 else 0.0

    /** G7: an attack that loses the initiative race is not an attack. G9: do not win on a lost tie. */
    private fun initiative(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (!card.attackCard && !card.hasMovement) return 0.0
        val incoming = ctx.incomingInitiative(at) ?: return 0.0
        val ours = card.initiative + ctx.itemBonus(h.id, Stat.INITIATIVE)
        return when {
            card.attackCard && ours < incoming -> -min(6.0, ctx.strikeFrom(h.id, at, card) * 0.35)
            ours == incoming && ctx.state.tieBreaker != ctx.team -> -2.5
            card.hasMovement && ours > incoming && ctx.exposure(h.id, at) > 6.0 -> 3.5
            else -> 0.0
        }
    }

    private fun fear(ctx: HContext): Double = when {
        ctx.profile.knowHands -> 1.0
        ctx.profile.initiativeReading -> 0.75
        else -> 0.55
    }

    /** G4: Red on turn 1 or 2, into a hero, when it is not the kill. */
    private fun earlyRed(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (ctx.turn > 2 || card.color != CardColor.RED || !card.attackCard) return 0.0
        val reach = ctx.attackReach(h.id, card).coerceAtLeast(1)
        val heroNear = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) <= reach } == true }
        if (!heroNear) return if (ctx.turn == 1) -2.0 else 0.0
        val kill = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.distance(at, pos) <= reach && ctx.defenseless(e.id)
        }
        return if (kill) 0.0 else -8.0
    }

    /** G10: a discard now so a teammate's turn-4 attack finds an empty hand. */
    private fun strip(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (!card.attackCard && !card.forcesDiscard) return 0.0
        var best = 0.0
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (!stripSetsUp(ctx, e) || !inAttackReach(ctx, h.id, pos, card)) continue
            val bonus = ctx.killWorth(e.id) - 12.0
            if (bonus > best) best = bonus
        }
        return best
    }

    /** G14: in a small team, join the hero a teammate is already hunting. G15: do not be the third. */
    private fun teamKill(ctx: HContext, h: HeroState, card: HCard): Double {
        if (!ctx.profile.focusFire) return 0.0
        if (!card.attackCard && !card.forcesDiscard) return 0.0
        val small = ctx.state.heroes.count { it.team == ctx.team } <= 2
        var best = 0.0
        var saw = false
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (!inAttackReach(ctx, h.id, pos, card) || !allyHunting(ctx, e)) continue
            val thin = ctx.spare(e.id) <= 0 || ctx.defenseless(e.id)
            val bonus = when {
                !small -> crowd(ctx, pos)
                thin -> ctx.killWorth(e.id) * 0.35
                else -> 2.0
            }
            saw = true
            if (bonus > best) best = bonus
        }
        return if (saw && best == 0.0) -8.0 else best
    }

    /** G15: two friendlies are already on the kill, and a minion is still standing. */
    private fun crowd(ctx: HContext, pos: Hex): Double {
        val hunters = ctx.allies().count { ally ->
            val from = ally.at ?: return@count false
            attackCards(ctx, ally).any { ctx.distance(from, pos) <= ctx.attackReach(ally.id, it) + 2 }
        }
        val minionLeft = ctx.state.minions.any { it.team != ctx.team && it.at != null }
        return if (hunters >= 2 && minionLeft) -8.0 else 4.0
    }

    /** G13: do not be the attack that pushes into a zone they respawn heavier in. */
    private fun avoidPush(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (!card.attackCard && !card.forcesDiscard) return 0.0
        val reach = ctx.attackReach(h.id, card).coerceAtLeast(1)
        val hitsTrap = ctx.state.minions.any { m ->
            val pos = m.at ?: return@any false
            m.team != ctx.team && !ctx.heavyImmune(m.id) &&
                ctx.distance(at, pos) <= reach && badPush(ctx, m.lane)
        }
        if (!hitsTrap) return 0.0
        return if (ctx.profile.laneTiming) -15.0 else -6.0
    }

    /** G18: behind on Life, an even hero trade is how the game ends. */
    private fun avoidTrade(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (ctx.deathsToLose(h.team) > 2 || !card.attackCard) return 0.0
        val reach = ctx.attackReach(h.id, card).coerceAtLeast(1)
        val trade = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.distance(at, pos) <= reach && !ctx.defenseless(e.id) && e.hand.isNotEmpty()
        }
        return if (trade) -10.0 else 0.0
    }

    private const val PRESS = 6.0
}
