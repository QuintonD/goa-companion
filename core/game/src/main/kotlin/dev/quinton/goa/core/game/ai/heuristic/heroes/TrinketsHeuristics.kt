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
import dev.quinton.goa.core.game.heroes.TrinketsHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Trinkets (`heroes/trinkets.md`): the Turret is the piece. Both Reds fire only at units in range of
 * him and of the Turret (Steam Discharge: adjacent to it), so with no Turret they have no target.
 * Gold rebuilds it, Silver places or salvages it, Self-Destruct spends it.
 */
object TrinketsHeuristics : HeroHeuristics {
    override val heroId: String = "trinkets"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 3,
        aggression = 0.35,
        laneWeight = 0.7,
        upgradeOrder = listOf("red", "green", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.RESPAWN -> respawn(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val at = BatchDSupport.turret(state) ?: return 0.0
        // The Turret persists; it is the gun. Beside two enemy minions it is the repeat (H4).
        var v = 4.0
        val beside = state.minions.count { m ->
            m.team != self.team && m.at != null && HexGrid.distance(at, m.at!!) == 1
        }
        v += 2.0 * minOf(beside, 2)
        val barriers = BatchDSupport.pieces(state, TrinketsHero.BARRIER).count { piece ->
            state.heroes.any { h ->
                h.team == self.team && h.at != null && HexGrid.distance(piece.at!!, h.at!!) == 1
            }
        }
        v += barriers * 1.5
        return BatchDSupport.sign(state, heroId, team) * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val turret = BatchDSupport.turret(ctx.state) ?: return 0.0
        if (CANNON.none { BatchDSupport.mayHold(ctx, heroId, it) }) return 0.0
        val inBoth = ctx.distance(from, target) in 1..3 && ctx.distance(turret, target) in 1..3
        if (!inBoth) return 0.0
        // H16: the +2 is a line through both him and the Turret, on top of the printed attack.
        val lined = HexGrid.inStraightLine(from, target) && HexGrid.inStraightLine(turret, target)
        return if (lined) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val card = ctx.card(id)
        val turret = BatchDSupport.turret(ctx.state)
        return when (id) {
            GOLD, HANDICAP -> gold(ctx, at, turret, id)

            // H2: Silver rebuilds the gun as well as Gold does, at init 5, and keeps Gold for the move.
            SILVER -> when {
                turret == null -> 4.0
                ctx.distanceToBattleZone(turret) >= 3 -> 2.0
                else -> 0.0
            }

            // H3/H5: in range of both him and the Turret; Steam Discharge: adjacent to the Turret.
            in CANNON, in DISCHARGE -> {
                val real = if (turret == null) {
                    -0.5
                } else {
                    val turretReach = if (id in DISCHARGE) 1 else card.range
                    val reach = ctx.attackReach(ctx.seat, card)
                    val valid = { _: String, pos: Hex ->
                        ctx.distance(at, pos) in 1..reach && ctx.distance(turret, pos) in 1..turretReach
                    }
                    val hits = BatchDSupport.strikeList(ctx, card, valid)
                    // Steam Discharge repeats on a different enemy unit beside the Turret.
                    val extra = if (id in DISCHARGE) hits.drop(1).take(id.repeats()) else emptyList()
                    val repeat = extra.sumOf { maxOf(0.0, it.second) }
                    (hits.firstOrNull()?.second ?: -0.5) + 0.8 * repeat
                }
                BatchDSupport.strikeFix(ctx, card, real)
            }

            // H10: Early Prototype deletes the Turret. Only to escape, and only with Gold to rebuild.
            "trinkets-green-1" -> {
                val gold = ctx.ownHand().any { it.id == GOLD || it.id == HANDICAP }
                if (turret != null && gold && ctx.exposure(ctx.seat, at) > 8.0) 2.0 else -2.5
            }

            "trinkets-green-2b", "trinkets-green-3b" -> BatchDSupport.outlookFix(ctx, card, blast(ctx, id, turret))

            in DISRUPTOR -> {
                // H6/H7: the jolt discards an enemy in the Turret's radius before their primary this turn.
                val radius = card.radius.coerceAtLeast(1)
                val inside = turret?.let { BatchDSupport.enemyHeroesWithin(ctx, it, 1, radius) }.orEmpty()
                val real = if (inside.isEmpty()) -1.5 else 1.0 + inside.take(2).sumOf { ctx.discardWorth(it) } * 0.5
                BatchDSupport.outlookFix(ctx, card, real)
            }

            // H11: no Turret, no first Barrier (it must touch the Turret), so nothing is placed.
            in BARRIER -> if (turret == null) -1.5 else 0.0

            else -> 0.0
        }
    }

    private fun gold(ctx: HContext, at: Hex, turret: Hex?, id: String): Double {
        // H1: the gun comes back. Off the board, or stranded away from the fight, Gold rebuilds it.
        if (turret == null) return 5.0
        if (ctx.distanceToBattleZone(turret) >= 3) return 3.0
        if (id == HANDICAP) return -0.5
        // H13: a Turret already sited leaves Gold the minion it defeats outright (no defense card).
        val minion = ctx.state.minions.filter { m ->
            m.team != ctx.team && m.at != null && ctx.distance(at, m.at!!) == 1 && !ctx.heavyImmune(m.id)
        }.maxOfOrNull { ctx.minionWorth(it.id) }
        return if (minion != null && minion > 0) minion * 0.5 else -0.5
    }

    /** H8: up to two enemy heroes in the Turret's radius discard (or die, on Protocol); the Turret goes. */
    private fun blast(ctx: HContext, id: String, turret: Hex?): Double {
        if (turret == null) return -1.5
        val radius = ctx.card(id).radius.coerceAtLeast(1)
        val inside = BatchDSupport.enemyHeroesWithin(ctx, turret, 1, radius)
        val gold = ctx.ownHand().any { it.id == GOLD || it.id == SILVER }
        val loss = if (gold) 3.0 else 6.0
        if (inside.isEmpty()) return -loss - 1.5
        val gain = inside.sortedByDescending { ctx.discardWorth(it) }.take(2).sumOf { e ->
            if (id == "trinkets-green-3b" && e.hand.isEmpty()) ctx.killWorth(e.id) else ctx.discardWorth(e)
        }
        return gain - loss
    }

    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Option) return 0.0
        val src = d.sourceCardId ?: return 0.0
        val turret = BatchDSupport.turret(ctx.state)
        val label = choice.label
        if (src == GOLD) {
            // H13: no Turret means the place; a Turret already on the minions means the defeat.
            val place = label.startsWith("Move up to 3")
            return when {
                turret == null && place -> 4.0
                turret != null && !place && ctx.distanceToBattleZone(turret) < 3 -> 2.0
                else -> 0.0
            }
        }
        if (src != SILVER) return 0.0
        // H14: place while the gun should stay; salvage-and-walk only when the Turret is on the wrong beach.
        val far = turret != null && ctx.distanceToBattleZone(turret) >= 3
        return when {
            label.startsWith("Place") -> if (far) 0.0 else 1.5
            label.contains("move up to 3") -> if (far) 1.5 else -1.0
            else -> -0.5
        }
    }

    /** H4: the Turret's hex. The general book reads a placement as Trinkets standing there; undo that. */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val hex = choice.hex
        if (d.prompt.startsWith("Place the Turret")) {
            val minions = ctx.state.minions.count { m ->
                m.team != ctx.team && m.at != null && ctx.distance(hex, m.at!!) == 1
            }
            var v = 2.5 * minOf(minions, 2)
            if (ctx.inBattleZone(hex)) v += 1.5
            return BatchDSupport.unplace(ctx, d, hex) + v
        }
        if (d.prompt.startsWith("Place a Barrier")) {
            // H11: a Barrier beside a friendly hero is +1 Defense to them while it stands.
            val friends = ctx.state.heroes.count { h ->
                h.team == ctx.team && h.at != null && ctx.distance(hex, h.at!!) == 1
            }
            return BatchDSupport.unplace(ctx, d, hex) + 1.5 * friends
        }
        return 0.0
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val turret = BatchDSupport.turret(ctx.state) ?: return 0.0
        // H20: the Turret is still there. Come back toward it.
        return (4 - ctx.distance(choice.hex, turret)).coerceAtLeast(0) * 0.4
    }

    private fun String.repeats(): Int = if (this == "trinkets-red-3b") 2 else 1

    private const val GOLD = "trinkets-gold"
    private const val HANDICAP = "trinkets-handicap"
    private const val SILVER = "trinkets-silver"
    private val CANNON = setOf("trinkets-red-1", "trinkets-red-2a", "trinkets-red-3a")
    private val DISCHARGE = setOf("trinkets-red-2b", "trinkets-red-3b")
    private val DISRUPTOR = setOf("trinkets-blue-1", "trinkets-blue-2a", "trinkets-blue-3a")
    private val BARRIER = setOf("trinkets-blue-2b", "trinkets-blue-3b")
}
