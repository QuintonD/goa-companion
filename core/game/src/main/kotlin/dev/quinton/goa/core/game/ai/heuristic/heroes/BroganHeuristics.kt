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
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Brogan: a tank who charges two or more spaces onto a unit. Adjacent is safe from that red.
 * Bulwark is the heal, not a turn-1 action.
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

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        if (!BatchA.effectLive(state, heroId, BULWARK)) return 0.0
        val at = state.hero(heroId)?.at ?: return 0.0
        val sign = BatchA.sign(state, team, heroId)
        val covered = state.heroes.any { e ->
            e.team != state.hero(heroId)?.team && e.at?.let { HexGrid.distance(at, it) in 1..4 } == true
        }
        // H eval: Bulwark is the anchor while someone in radius could still displace him.
        return if (covered) sign * 4.0 else 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (HexGrid.distance(from, target) == 1) return 0.0
        if (!BatchA.poolHas(ctx, heroId, CHARGE)) return 0.0
        val landing = BatchA.besideEnemyUnit(ctx, target) && chargeExists(ctx, from, target)
        if (!landing) return 0.0
        return if (BatchA.played(ctx, heroId)?.id in CHARGE) 12.0 else 8.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            in CHARGE -> charge(ctx, choice.cardId)
            in GOLD -> gold(ctx)
            "brogan-silver" -> silver(ctx)
            else -> 0.0
        }
    }

    // H1: the charge needs a straight landing of at least 2 next to an enemy unit. Adjacent fizzles it.
    private fun charge(ctx: HContext, cardId: String): Double {
        val range = CHARGE_MAX.getValue(cardId)
        val lands = BatchA.straightLandings(ctx, heroId, 2, range) { BatchA.besideEnemyUnit(ctx, it) }
        if (lands.isEmpty()) return -12.0
        val forks = lands.mapNotNull { hex ->
            ctx.own()?.at?.let { HexGrid.straightLineDirection(it, hex) }
        }.distinct().size
        var v = 12.0
        if (forks >= 2) v += 6.0
        return v
    }

    // H6: gold takes the adjacent minion when the charge has no line, and he steps onto that space.
    private fun gold(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        if (!BatchA.besideEnemyUnit(ctx, at)) return 0.0
        val chargeReady = ctx.ownHand().any { it.id in CHARGE } &&
            BatchA.straightLandings(ctx, heroId, 2, 2) { BatchA.besideEnemyUnit(ctx, it) }.isNotEmpty()
        return if (chargeReady) -2.0 else 8.0
    }

    // H7: Bulwark retrieves a red or a blue. Empty discard and nobody to anchor: do not spend it.
    private fun silver(ctx: HContext): Double {
        val discarded = ctx.own()?.cards?.any {
            it.zone == Zone.DISCARD && (it.id.startsWith("brogan-red") || it.id.startsWith("brogan-blue"))
        } == true
        if (discarded) return 8.0
        if (ctx.turn == 1) return -8.0
        return -3.0
    }

    // H2: among charge landings, the one next to a Battle Zone minion.
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in CHARGE || choice !is Choice.Space) return 0.0
        val minion = ctx.state.minions.any { m ->
            val pos = m.at ?: return@any false
            m.team != ctx.team && ctx.distance(choice.hex, pos) == 1 && ctx.inBattleZone(pos)
        }
        return if (minion) 8.0 else 0.0
    }

    // H15: keep the heal. Silver is Defense 4 and the retrieve.
    private fun defend(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "brogan-silver") -4.0 else 0.0
    }

    // H12: the push (blue-2a) before the charge, tucking the Attack item.
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "brogan-blue-2a" || choice.cardId == "brogan-blue-3a") 1.5 else 0.0
    }

    private fun chargeExists(ctx: HContext, from: Hex, landing: Hex): Boolean {
        val dir = HexGrid.straightLineDirection(from, landing) ?: return false
        val n = HexGrid.distance(from, landing)
        if (n !in 2..4) return false
        val step = HexGrid.DIRECTIONS[dir]
        for (i in 1 until n) {
            val hex = Hex(from.q + step.q * i, from.r + step.r * i)
            if (!ctx.game.isEmpty(hex, heroId)) return false
        }
        return ctx.game.isEmpty(landing, heroId)
    }

}

private val GOLD = setOf("brogan-gold", "brogan-handicap")
private val CHARGE = setOf("brogan-red-1", "brogan-red-2a", "brogan-red-3a")
private val BULWARK = setOf("brogan-silver")
private val CHARGE_MAX = mapOf("brogan-red-1" to 2, "brogan-red-2a" to 3, "brogan-red-3a" to 4)
