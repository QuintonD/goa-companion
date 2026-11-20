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
import dev.quinton.goa.core.game.heroes.IgnatiaHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Ignatia's card is two cards. The coin picks the text; a card with a target on only one face
 * is a walk when the coin shows the other (rulings/ignatia.md). Blue is the star, orange the gear.
 */
object IgnatiaHeuristics : HeroHeuristics {
    override val heroId: String = "ignatia"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.55,
        laneWeight = 0.45,
        upgradeOrder = listOf("ignatia-red-2a", "ignatia-blue-2a", "ignatia-green-2a", "red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> face(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.DEFEND -> defend(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchB.sign(state, team, heroId)
        val equilibrium = BatchB.effect(state, heroId, "ignatia-silver")
        val magma = BatchB.tokens(state, heroId, IgnatiaHero.MAGMA).size
        var value = magma * 1.5
        if (equilibrium) value += 6.0
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val distance = ctx.distance(from, target)
        var threat = 0.0
        val besideBody = BatchB.besideToken(ctx.state, target, IgnatiaHero.MAGMA) ||
            ctx.state.minions.any { it.at?.let { pos -> HexGrid.distance(pos, target) == 1 } == true } ||
            ctx.state.pieces.any { piece ->
                piece.token && piece.at?.let { HexGrid.distance(it, target) == 1 } == true
            }
        if (besideBody && distance <= 4 && BatchB.hold(ctx, heroId) { it.id.startsWith("ignatia-blue") } > 0.2) {
            threat += 8.0
        }
        // Alt red is adjacent or exactly 5. The belt between is not that card.
        val alt = BatchB.hold(ctx, heroId) { it.id == "ignatia-red-2b" || it.id == "ignatia-red-3b" }
        if ((distance == 1 || distance == 5) && alt > 0.15) threat += 5.0
        return threat
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return gold(ctx, id) + silver(ctx, id) + oneFace(ctx, id)
    }

    // H12: gold attack 2. Blue face is the adjacent minion, orange face the hero in range 3.
    private fun gold(ctx: HContext, id: String): Double {
        if (id != "ignatia-gold") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val (minion, hero) = goldFaces(ctx, at)
        return when {
            minion && hero -> 6.0
            minion || hero -> -12.0
            else -> -12.0
        }
    }

    // H3: silver is the control card, and it is also defense 7. Do not spend it into a real attack.
    private fun silver(ctx: HContext, id: String): Double {
        if (id != "ignatia-silver" || ctx.turn != 1) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val exposed = ctx.threatsOn(at, ctx.team).any { it.attackTotal >= 6 }
        return if (exposed) -8.0 else 4.0
    }

    // H1: before the reveal she cannot guarantee the coin, so a single live face is a dead card.
    private fun oneFace(ctx: HContext, id: String): Double {
        val coloured = id.startsWith("ignatia-red") || id.startsWith("ignatia-blue")
        val at = ctx.own()?.at
        if (!coloured || at == null) return 0.0
        if (BatchB.effect(ctx.state, heroId, "ignatia-silver")) return 3.0
        val both = bothFaces(ctx, at, id)
        return if (both) 4.0 else -12.0
    }

    private fun bothFaces(ctx: HContext, at: Hex, id: String): Boolean = when {
        id.startsWith("ignatia-red-2b") || id.startsWith("ignatia-red-3b") ->
            altRed(ctx, at, ctx.card(id).range.coerceAtLeast(5))

        id.startsWith("ignatia-red") -> geometries(ctx, at, ctx.card(id).range.coerceAtLeast(2))
        else -> true
    }

    // H4: Equilibrium offers "Blue:" and "Orange:". Take the face that has a body.
    private fun face(ctx: HContext, d: Decision, choice: Choice): Double {
        if (!d.prompt.startsWith("Equilibrium")) return 0.0
        val option = choice as? Choice.Option ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val (minion, hero) = goldFaces(ctx, at)
        return when {
            option.label.startsWith("Blue") && minion -> 8.0
            option.label.startsWith("Orange") && hero -> 8.0
            option.label.startsWith("Blue") && !minion -> -6.0
            option.label.startsWith("Orange") && !hero -> -6.0
            else -> 0.0
        }
    }

    // H5: on the straight-line face, a unit on a line. On the other face, a unit off it.
    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        val source = d.sourceCardId ?: return 0.0
        if (!source.startsWith("ignatia-red")) return 0.0
        val unit = choice as? Choice.Unit ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val pos = ctx.state.hero(unit.unitId)?.at ?: ctx.state.minion(unit.unitId)?.at ?: return 0.0
        val line = HexGrid.inStraightLine(at, pos)
        val orange = ctx.state.tieBreaker == Team.RED
        return if (orange == line) 4.0 else -2.0
    }

    // H15: silver is defense 7. A small hit should not take it.
    private fun defend(choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return if (id == "ignatia-silver") -6.0 else 0.0
    }

    private fun goldFaces(ctx: HContext, at: Hex): Pair<Boolean, Boolean> {
        val minion = BatchB.enemyMinionWithin(ctx, at, 1)
        val hero = ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(at, it) in 1..3 } == true }
        return minion to hero
    }

    private fun geometries(ctx: HContext, at: Hex, range: Int): Boolean {
        var line = false
        var off = false
        fun mark(pos: Hex) {
            val distance = ctx.distance(at, pos)
            if (distance !in 1..range) return
            if (HexGrid.inStraightLine(at, pos)) line = true else off = true
        }
        ctx.enemies().forEach { enemy -> enemy.at?.let(::mark) }
        ctx.state.minions.forEach { minion -> if (minion.team != ctx.team) minion.at?.let(::mark) }
        return line && off
    }

    private fun altRed(ctx: HContext, at: Hex, range: Int): Boolean {
        val adjacent = BatchB.enemyHeroWithin(ctx, at, 1) || BatchB.enemyMinionWithin(ctx, at, 1)
        val far = ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(at, it) == range } == true } ||
            ctx.state.minions.any { minion ->
                minion.team != ctx.team && minion.at?.let { ctx.distance(at, it) == range } == true
            }
        return adjacent && far
    }
}
