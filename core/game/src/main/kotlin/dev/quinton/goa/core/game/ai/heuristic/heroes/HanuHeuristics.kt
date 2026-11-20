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
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.heroes.HanuHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Hanu sets an ally up and leaves. The ranged half of his red does not exist unless that ally
 * is already standing on the target (card text). Gold's 3-space leave happens only when the
 * target survives (rulings/hanu.md 1).
 */
object HanuHeuristics : HeroHeuristics {
    override val heroId: String = "hanu"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 2,
        aggression = 0.45,
        laneWeight = 0.50,
        upgradeOrder = listOf("hanu-red-2a", "hanu-red-2b", "hanu-green-2b", "hanu-blue-2b", "red", "green", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_CARD -> hurry(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> swap(ctx, d, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchB.sign(state, team, heroId)
        val immune = state.effects.any { effect ->
            effect.owner == heroId && effect.key == HanuHero.IMMUNE && !effect.spent
        }
        // The journey marker is the public "that hero is immune this turn". No token of his own.
        return if (immune) sign * 4.0 else 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val range = if (BatchB.shown(ctx.state, heroId) { it.startsWith("hanu-red-2") || it.startsWith("hanu-red-3") }) {
            4
        } else {
            3
        }
        val distance = ctx.distance(from, target)
        if (distance > range) return 0.0
        val covered = ctx.state.heroes.any { ally ->
            ally.team == ctx.hero(heroId)?.team && ally.id != heroId &&
                ally.at?.let { HexGrid.distance(it, target) == 1 } == true
        }
        // Printed range already exists. The extra is that the shot only exists beside his ally.
        return if (covered && BatchB.hold(ctx, heroId) { it.id.startsWith("hanu-red") } > 0.2) 6.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return gold(ctx, id) + red(ctx, id) + journey(ctx, id)
    }

    // H1: gold, then the straight 3, only when the tap will not defeat the target.
    private fun gold(ctx: HContext, id: String): Double {
        if (id != "hanu-gold") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        if (!adjacentEnemy(ctx, at) || !escapeLine(ctx, at)) return 0.0
        return 8.0
    }

    // H6: primary red's ranged bullet needs an ally on the enemy. With neither bullet, the card is a walk.
    private fun red(ctx: HContext, id: String): Double {
        if (!id.startsWith("hanu-red")) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val range = ctx.card(id).range.coerceAtLeast(3)
        if (adjacentEnemy(ctx, at)) return 4.0
        val helped = ctx.enemies().any { enemy ->
            val pos = enemy.at ?: return@any false
            ctx.distance(at, pos) <= range && BatchB.allyBeside(ctx, pos)
        }
        return if (helped) 8.0 else -14.0
    }

    // H8: Journey blanks the enemy for the turn. Do not swap someone an ally still has to hit.
    private fun journey(ctx: HContext, id: String): Double {
        if (id != "hanu-blue-1" && id != "hanu-blue-2a" && id != "hanu-blue-3a") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val radius = ctx.card(id).radius.coerceAtLeast(2)
        val victim = ctx.enemies().firstOrNull { enemy ->
            enemy.at?.let { ctx.distance(at, it) <= radius } == true && enemy.id !in ctx.state.turnRecord.resolved
        } ?: return -6.0
        val allyWaiting = ctx.allies().any { ally ->
            ally.unresolved.isNotEmpty() && ally.at?.let { pos ->
                victim.at?.let { ctx.distance(pos, it) <= 2 } == true
            } == true
        }
        return if (allyWaiting) -10.0 else 8.0
    }

    // H3: Hurry up sets one unresolved card to printed 11. Prefer an ally attack that is still slower.
    private fun hurry(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "hanu-silver") return 0.0
        val id = BatchB.cardId(choice) ?: return 0.0
        val card = ctx.card(id)
        val ally = ctx.allies().any { hero -> hero.unresolved.any { it.id == id } }
        return when {
            ally && card.attackCard && card.initiative <= 10 -> 12.0
            !ally && card.initiative >= 12 -> 8.0
            !ally && card.initiative <= 6 -> 6.0
            else -> 0.0
        }
    }

    // H10 / H11: a green swap wants the enemy who is standing on the fight, or two friendlies.
    private fun swap(ctx: HContext, d: Decision, choice: Choice): Double {
        val source = d.sourceCardId.orEmpty()
        val unit = choice as? Choice.Unit
        val enemy = unit?.let { ctx.hero(it.unitId) }?.takeIf { it.team != ctx.team }
        val at = enemy?.at
        if (!source.startsWith("hanu-green") || at == null) return 0.0
        return if (BatchB.enemyMinionWithin(ctx, at, 1) || BatchB.allyBeside(ctx, at)) 8.0 else 0.0
    }

    // H12: do not spend the red or the blue on a poke a defense-1 card already answers.
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        val total = d.context.attackTotal ?: return 0.0
        val id = BatchB.cardId(choice) ?: return 0.0
        val card = ctx.card(id)
        val big = id.startsWith("hanu-red-3") || id.startsWith("hanu-blue")
        return if (big && total <= 2 && ctx.ownHand().any { (it.defense ?: 0) in 1..2 }) -8.0 else 0.0
    }

    private fun adjacentEnemy(ctx: HContext, at: Hex): Boolean =
        BatchB.enemyHeroWithin(ctx, at, 1) || BatchB.enemyMinionWithin(ctx, at, 1)

    private fun escapeLine(ctx: HContext, from: Hex): Boolean {
        for (step in HexGrid.DIRECTIONS) {
            var cursor = from
            var open = true
            for (i in 1..3) {
                cursor = cursor + step
                if (!ctx.game.isEmpty(cursor)) open = false
                if (i == 3 && open && !enemyBeside(ctx, cursor)) return true
            }
        }
        return false
    }

    private fun enemyBeside(ctx: HContext, at: Hex): Boolean = BatchB.enemyHeroWithin(ctx, at, 1)
}
