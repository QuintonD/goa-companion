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
 * Trinkets builds one Turret and shoots from the overlap. Without it the guns do not fire.
 * The piece is an obstacle, not a token, and it stays when he is defeated.
 */
object TrinketsHeuristics : HeroHeuristics {
    override val heroId: String = "trinkets"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 3,
        aggression = 0.35,
        laneWeight = 0.7,
        upgradeOrder = listOf(
            "trinkets-red-2a",
            "trinkets-green-2a",
            "trinkets-blue-2a",
            "red",
            "green",
            "blue",
        ),
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
        val sign = if (self.team == team) 1.0 else -1.0
        val at = BatchD.turret(state) ?: return 0.0
        var v = 6.0
        val beside = state.minions.count { m ->
            m.team != self.team && m.at != null && HexGrid.distance(at, m.at!!) == 1
        }
        if (beside >= 2) v += 4.0
        val barriers = BatchD.pieces(state, TrinketsHero.BARRIER).count { piece ->
            state.heroes.any { h ->
                h.team == self.team && h.at != null && HexGrid.distance(piece.at!!, h.at!!) == 1
            }
        }
        v += barriers * 2.0
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val turret = BatchD.turret(ctx.state) ?: return 0.0
        val gun = cannon.any { BatchD.mayHold(ctx, heroId, it) }
        if (!gun) return 0.0
        val inBoth = ctx.distance(from, target) in 1..3 && ctx.distance(turret, target) in 1..3
        if (!inBoth) return 0.0
        val lined = HexGrid.inStraightLine(from, target) && HexGrid.inStraightLine(turret, target)
        // H16: the +2 is the line through both him and the Turret, on top of the printed attack.
        return if (lined) 1.5 else 0.6
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val turret = BatchD.turret(ctx.state)
        var v = 0.0
        if (id == "trinkets-gold" || id == "trinkets-handicap") {
            // H1: Gold deploys the gun when it is down, and leaves when it is already sited.
            v += if (turret == null) 8.0 else -1.0
        }
        if (id == "trinkets-silver") {
            // H2: Silver places after the fast cards, once a Turret is already the reason to save Gold.
            v += if (turret == null) 3.0 else 4.0
        }
        if (id in cannon || id in discharge) {
            // H3/H5: both guns fizzle until the Turret is on the board.
            v += if (turret == null) {
                BatchD.FIZZLE
            } else if (shared(ctx, at, turret, id)) {
                6.0
            } else {
                BatchD.FIZZLE
            }
        }
        if (id == "trinkets-green-1") {
            val gold = ctx.ownHand().any { it.id == "trinkets-gold" || it.id == "trinkets-handicap" }
            val exposed = ctx.exposure(ctx.seat, at) > 8.0
            // H10: Early Prototype removes the Turret. Scrap it to live, and only if Gold rebuilds.
            v += if (gold && exposed) 4.0 else -8.0
        }
        if (id == "trinkets-green-2b" || id == "trinkets-green-3b") {
            // H8: the blast is the heroes in the Turret's radius. No Turret, no blast.
            v += blast(ctx, id, turret)
        }
        if (id in disruptor) {
            val radius = ctx.card(id).radius.coerceAtLeast(1)
            val inside = turret != null && heroesNear(ctx, turret, radius) > 0
            // H6: Disruptor still resolves with no Turret, and then it never discards anyone.
            v += if (inside) 5.0 else -6.0
        }
        return v
    }

    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Option) return 0.0
        val src = d.sourceCardId ?: return 0.0
        val turret = BatchD.turret(ctx.state)
        val place = choice.label.contains("place the Turret", ignoreCase = true) ||
            choice.label.contains("Place the Turret")
        val defeat = choice.label.contains("Defeat a minion", ignoreCase = true)
        if (src == "trinkets-gold" || src == "trinkets-handicap") {
            // H13: no Turret means the place. A Turret already on the minions means the defeat.
            return when {
                turret == null && place -> 6.0
                turret != null && defeat -> 4.0
                else -> 0.0
            }
        }
        if (src != "trinkets-silver") return 0.0
        val scrapMove = choice.label.contains("move", ignoreCase = true)
        // H14: place while the gun should stay. Scrap it to walk only when the shot is gone.
        return when {
            turret == null && place -> 4.0
            scrapMove && ctx.own()?.at?.let { ctx.distanceToBattleZone(it) >= 3 } == true -> 3.0
            place -> 2.0
            else -> -2.0
        }
    }

    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        if (d.sourceCardId != "trinkets-gold" && d.sourceCardId != "trinkets-silver" &&
            d.sourceCardId != "trinkets-handicap"
        ) {
            return 0.0
        }
        val minions = ctx.state.minions.count { m ->
            m.team != ctx.team && m.at != null && ctx.distance(choice.hex, m.at!!) == 1
        }
        // H4: the hex that touches two enemy minions is the repeat.
        return minions * 3.0
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val turret = BatchD.turret(ctx.state) ?: return 0.0
        // H20: the Turret is still there. Come back toward it.
        val far = ctx.distance(choice.hex, turret)
        return (4 - far).coerceAtLeast(0) * 0.4
    }

    private fun shared(ctx: HContext, at: Hex, turret: Hex, id: String): Boolean {
        val range = ctx.card(id).range.coerceAtLeast(1)
        val turretRange = if (id in discharge) 1 else range
        fun hit(hex: Hex) = ctx.distance(at, hex) in 1..range && ctx.distance(turret, hex) in 1..turretRange
        if (ctx.enemies().any { e -> e.at?.let(::hit) == true }) return true
        return ctx.state.minions.any { m -> m.team != ctx.team && m.at?.let(::hit) == true }
    }

    private fun blast(ctx: HContext, id: String, turret: Hex?): Double {
        if (turret == null) return BatchD.FIZZLE
        val radius = ctx.card(id).radius.coerceAtLeast(1)
        val n = heroesNear(ctx, turret, radius)
        return if (n > 0) 4.0 * n.coerceAtMost(2) else -8.0
    }

    private fun heroesNear(ctx: HContext, at: Hex, radius: Int): Int =
        ctx.enemies().count { e -> e.at?.let { ctx.distance(at, it) in 1..radius } == true }

    private val cannon = setOf("trinkets-red-1", "trinkets-red-2a", "trinkets-red-3a")
    private val discharge = setOf("trinkets-red-2b", "trinkets-red-3b")
    private val disruptor = setOf("trinkets-blue-1", "trinkets-blue-2a", "trinkets-blue-3a")
}
