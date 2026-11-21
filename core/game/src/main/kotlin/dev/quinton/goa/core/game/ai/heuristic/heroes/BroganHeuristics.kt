package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.max

/**
 * Brogan: a tank and pusher. His primary red is a charge: a straight move of 2 or more to a space
 * beside an enemy unit, then the attack. A unit he starts next to is not a charge target.
 */
object BroganHeuristics : HeroHeuristics {
    override val heroId: String = "brogan"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 2,
        aggression = 0.75,
        laneWeight = 0.70,
        upgradeOrder = listOf("blue", "red", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.DEFEND -> defend(choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in CHARGE -> charge(ctx, me, at, choice.cardId)
            "brogan-silver" -> silver(me)
            in BASH -> bash(ctx, at, choice.cardId)
            else -> 0.0
        }
    }

    /**
     * H1: the charge hits only from a straight landing of 2..N next to an enemy unit. The general
     * book counted every unit within N + 1, adjacent ones included; this keeps the real landings.
     * Two landings in different directions are a fork a single body-block cannot stop.
     */
    private fun charge(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        var best = 0.0
        val directions = HashSet<Int>()
        for (land in landings(ctx, cardId)) {
            val v = BatchASupport.bestAdjacent(ctx, me.id, land, total)
            if (v > 0.5) HexGrid.straightLineDirection(at, land)?.let { directions += it }
            best = max(best, v)
        }
        val fork = if (directions.size >= 2) 1.5 else 0.0
        return BatchASupport.correction(ctx, me.id, at, card, best) + fork
    }

    private fun landings(ctx: HContext, cardId: String): Set<Hex> =
        ctx.game.reach(
            MoveSpec(
                heroId,
                2,
                CHARGE_MAX.getValue(cardId),
                straightLine = true,
                endFilter = { BatchASupport.besideEnemyUnit(ctx, it) },
            ),
        ).keys

    /** H2: the landing decides the target; the one beside the best unit to hit. */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in CHARGE || choice !is Choice.Space) return 0.0
        if (d.context.subjectId != null && d.context.subjectId != heroId) return 0.0
        val card = ctx.card(d.sourceCardId!!)
        val total = card.attack + ctx.itemBonus(heroId, Stat.ATTACK)
        return BatchASupport.bestAdjacent(ctx, heroId, choice.hex, total)
    }

    /** H7: Bulwark's retrieve is the heal: a discarded red or blue back in hand. Nothing to take back, hold it. */
    private fun silver(me: HeroState): Double {
        val back = me.cards.any {
            it.zone == Zone.DISCARD &&
                (it.id.startsWith("brogan-red") || it.id.startsWith("brogan-blue"))
        }
        return if (back) 2.5 else -2.5
    }

    /**
     * H5: Shield Bash / Counterattack make an adjacent enemy who played an attack this turn discard
     * (Counterattack: or be defeated). No adjacent enemy, or none likely to attack: it fizzles.
     */
    private fun bash(ctx: HContext, at: Hex, cardId: String): Double {
        val near = BatchASupport.enemiesWithin(ctx, at, 1)
        if (near.isEmpty()) return -3.0
        val orDefeat = cardId == "brogan-blue-3b"
        val best = near.maxOf { e ->
            val p = if (e.id in
                ctx.state.turnRecord.playedAttack
            ) {
                1.0
            } else {
                BatchASupport.chancePlays(ctx, e.id) { it.attackCard }
            }
            p * BatchASupport.discardValue(ctx, e, orDefeat)
        }
        return best - 1.4
    }

    // H15: Bulwark (Defense 4) is the retrieve; block with red or blue when they hold.
    private fun defend(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "brogan-silver") -2.0 else 0.0
    }

    // H12: the push blue (Mighty Punch / Savage Kick) is kept; Shield Bash is tucked for the Attack item.
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "brogan-blue-2a" || choice.cardId == "brogan-blue-3a") 1.5 else 0.0
    }
}

private val CHARGE = setOf("brogan-red-1", "brogan-red-2a", "brogan-red-3a")
private val BASH = setOf("brogan-blue-2b", "brogan-blue-3b")
private val CHARGE_MAX = mapOf("brogan-red-1" to 2, "brogan-red-2a" to 3, "brogan-red-3a" to 4)
