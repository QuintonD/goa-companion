package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * Xargatha stands in enemy minions and pays for the Attack in Defense. Siren's Call only
 * targets a unit that is not already adjacent. Cleave's repeat is a different enemy hero.
 */
object XargathaHeuristics : HeroHeuristics {
    override val heroId: String = "xargatha"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.55,
        laneWeight = 0.55,
        upgradeOrder = listOf(
            "xargatha-green-2a",
            "xargatha-red-2a",
            "xargatha-blue-2a",
            "green",
            "red",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CONFIRM -> confirm(d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val sign = if (self.team == team) 1.0 else -1.0
        val at = self.at
        var v = 0.0
        if (at != null) {
            val bodies = BatchD.adjacentEnemies(state, at, self.team)
            val melee = BatchD.adjacentMinions(state, at, self.team, MinionType.MELEE)
            v += bodies * 2.0
            if (melee > 2) v -= (melee - 2) * 2.0
        }
        val gaze = state.effects.any { effect ->
            effect.owner == heroId && !effect.spent && effect.timing == Timing.NEXT_TURN &&
                effect.cardId in gazes
        }
        if (gaze) v += 4.0
        val hug = state.effects.any { effect ->
            effect.owner == heroId && !effect.spent && effect.cardId in constricts
        }
        if (hug && at != null && BatchD.adjacentMinions(state, at, self.team, MinionType.MELEE) > 0) v += 6.0
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchD.mayHold(ctx, heroId, "xargatha-silver")) return 0.0
        val d = ctx.distance(from, target)
        // Silver pulls a unit at 2 or 3 onto a hex beside her. Printed Gold reach is adjacent only.
        return if (d in 2..3) 1.2 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        if (id == "xargatha-silver") {
            val range = ctx.card(id).range.coerceAtLeast(3)
            val pull = BatchD.enemyWithin(ctx, at, 2, range)
            // H1/H2: no unit in range that is not adjacent, and the engine has no target.
            return if (pull) 6.0 else BatchD.FIZZLE
        }
        if (id == "xargatha-gold" || id == "xargatha-handicap") {
            val minion = BatchD.adjacentMinions(ctx.state, at, ctx.team) > 0
            val hero = BatchD.enemyWithin(ctx, at, 1, 1, heroesOnly = true)
            // H3: the minion and a hero are the two hits. One of them is still the card.
            return when {
                minion && hero -> 6.0
                minion || hero -> 2.0
                else -> -4.0
            }
        }
        if (id in slashes) {
            val others = BatchD.adjacentEnemies(ctx.state, at, ctx.team)
            val melee = BatchD.adjacentMinions(ctx.state, at, ctx.team, MinionType.MELEE)
            // H5: the bonus is the other bodies. Past two enemy melee the Defense bill eats the swing.
            return when {
                melee >= 4 -> -4.0
                others >= 2 -> 4.0
                others == 1 -> 2.0
                else -> 0.0
            }
        }
        if (id == "xargatha-green-1") {
            val ranged = BatchD.adjacentMinions(ctx.state, at, ctx.team, MinionType.RANGED) > 0 ||
                ctx.state.minions.any { m ->
                    m.team != ctx.team && m.type == MinionType.RANGED && m.at != null &&
                        ctx.distance(at, m.at!!) in 1..2
                }
            // Charm at tier 1 moves a ranged minion only.
            return if (ranged) 3.0 else -2.0
        }
        if (id in constricts) {
            val melee = BatchD.adjacentMinions(ctx.state, at, ctx.team, MinionType.MELEE) > 0
            // The defeat is end of round, and only while she is still adjacent.
            return if (melee) 5.0 else 0.0
        }
        if (id in gazes) {
            val radius = ctx.card(id).radius.coerceAtLeast(2)
            val caught = BatchD.enemyWithin(ctx, at, 1, radius, heroesOnly = true)
            // Next turn they cannot take a Movement action. This turn it does not stop the attack.
            return if (caught) 3.0 else 0.0
        }
        return 0.0
    }

    private fun confirm(d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        if (src != "xargatha-gold" && src != "xargatha-handicap" && src != "xargatha-red-3b") return 0.0
        // H4: the repeat is a different enemy hero. The engine only asks when one can be targeted.
        return if (choice is Choice.Yes) 3.0 else -1.0
    }

    private val slashes = setOf("xargatha-red-1", "xargatha-red-2a", "xargatha-red-3a")
    private val gazes = setOf("xargatha-blue-1", "xargatha-blue-2a", "xargatha-blue-3a")
    private val constricts = setOf("xargatha-green-2b", "xargatha-green-3b")
}
