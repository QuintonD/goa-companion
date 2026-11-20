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
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.heroes.TigerclawHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Tigerclaw dashes through a target and leaves. Gold hits only the unit he passes; the landing
 * has to be empty. Blues are the block, and they block one attack type.
 */
object TigerclawHeuristics : HeroHeuristics {
    override val heroId: String = "tigerclaw"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.ASSASSIN,
        idealDistance = 2,
        aggression = 0.65,
        laneWeight = 0.55,
        upgradeOrder = listOf(
            "tigerclaw-green-2a",
            "tigerclaw-red-2b",
            "tigerclaw-blue-2a",
            "green",
            "red",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE -> upgrade(ctx, choice)
        DecisionKind.RESPAWN -> respawn(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val sign = if (self.team == team) 1.0 else -1.0
        var v = 0.0
        for (hero in state.heroes) {
            val strength = BatchD.poisonStrength(hero)
            if (strength == 0) continue
            // Poison on an enemy taxes the card they answer with. Poison on a friend does not.
            v += if (hero.team != self.team) 4.0 * strength else -6.0
        }
        val immune = state.effects.any { effect ->
            effect.owner == heroId && effect.cardId == "tigerclaw-silver" && !effect.spent &&
                (effect.timing == Timing.NEXT_TURN || effect.timing == Timing.THIS_TURN)
        }
        if (immune) v += 4.0
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(from, target) != 1) return 0.0
        val gold = BatchD.mayHold(ctx, heroId, "tigerclaw-gold")
        val handicap = BatchD.mayHold(ctx, heroId, "tigerclaw-handicap")
        if (!gold && !handicap) return 0.0
        val step = Hex(target.q - from.q, target.r - from.r)
        val landing = target + step
        // H18: he hits the unit he passes through. A body on the landing denies the dash.
        return if (ctx.game.isEmpty(landing, heroId)) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        var v = 0.0
        if (id == "tigerclaw-gold" || id == "tigerclaw-handicap") {
            val line = BatchD.blink(ctx, at, ctx.team, ctx.seat, heroOnly = id != "tigerclaw-gold")
            // H1/H2: no empty hex beyond the target, and the dash does not attack. Printed reach is 3.
            v += if (line) 5.0 else BatchD.FIZZLE
        }
        if (id == "tigerclaw-silver") {
            val terrain = BatchD.besideTerrain(ctx, at)
            val follow = ctx.ownHand().any { it.attackCard || it.id.startsWith("tigerclaw-green") }
            // H6/H7: the place is the immunity, and only from beside terrain, with a card left to use it.
            v += when {
                !terrain -> BatchD.FIZZLE
                follow -> 5.0
                else -> -4.0
            }
        }
        if (id in steal) {
            val reach = if (id == "tigerclaw-green-1") 2 else 3
            val coins = ctx.enemies().filter { e ->
                e.at != null && ctx.distance(at, e.at!!) <= reach
            }.sumOf { it.coins }
            // H8: a coin in step range is the farm. No hero there and the take does not happen.
            v += if (coins > 0) 2.0 + coins.coerceAtMost(3).toDouble() else -4.0
        }
        if (id == "tigerclaw-green-2b" || id == "tigerclaw-green-3b") {
            val range = ctx.card(id).range.coerceAtLeast(1)
            val hero = BatchD.enemyWithin(ctx, at, 1, range, heroesOnly = true)
            // H12: the marker is the card. No hero in range and it does not resolve.
            v += if (hero) 4.0 else BatchD.FIZZLE
        }
        if (id.startsWith("tigerclaw-blue")) {
            // H5: Blue stays in hand until it blocks. It is not the turn's skill.
            v -= 1.5
        }
        if (id.startsWith("tigerclaw-red") && id !in backstab && BatchD.enemyWithin(ctx, at, 1, 2)) {
            // H10: Hit and Run is the strike that still has a step afterwards.
            v += 2.0
        }
        return v
    }

    private fun defend(d: Decision, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val id = choice.cardId
        if (!id.startsWith("tigerclaw-blue")) return 0.0
        val ranged = d.context.rangedAttack == true
        val blocksRanged = id == "tigerclaw-blue-1" || id == "tigerclaw-blue-2a" || id == "tigerclaw-blue-3a"
        val matches = ranged == blocksRanged
        // H3/H4/H5: the other Blue does not block, and a failed Block still defeats him.
        return if (matches) 6.0 else -15.0
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Unit) return 0.0
        val src = d.sourceCardId ?: return 0.0
        if (src == "tigerclaw-green-2b" || src == "tigerclaw-green-3b") {
            val ally = ctx.state.hero(choice.unitId)?.team == ctx.team
            // H12: a friend is a legal target and the wrong one.
            return if (ally) -12.0 else 2.0
        }
        if (src !in backstab) return 0.0
        val at = ctx.state.hero(choice.unitId)?.at ?: ctx.state.minion(choice.unitId)?.at ?: return 0.0
        // H11: +2 Attack when a friendly unit, not Tigerclaw, is adjacent to the target.
        return if (friendlyBeside(ctx, at)) 4.0 else 0.0
    }

    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val src = d.sourceCardId ?: return 0.0
        if (src != "tigerclaw-gold" && src != "tigerclaw-handicap") return 0.0
        val far = ctx.enemies().mapNotNull { it.at }.minOfOrNull { ctx.distance(choice.hex, it) } ?: 1
        // H1: land on the side that is farther from the hero who still acts.
        return far * 0.8
    }

    private fun upgrade(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val id = choice.cardId
        var v = 0.0
        val raced = BatchD.enemyGoldInit(ctx) >= 13
        if (raced && id in initiative) v += 3.0
        // H16: Pick Pocket first when Gold already ties. An Initiative item when it does not.
        if (!raced && id == "tigerclaw-green-2a") v += 2.0
        val ranged = BatchD.mostlyRanged(ctx)
        if (ranged && (id == "tigerclaw-blue-2a" || id == "tigerclaw-blue-3a")) v += 4.0
        if (!ranged && (id == "tigerclaw-blue-2b" || id == "tigerclaw-blue-3b")) v += 4.0
        // H15: the Blue that matches the attacks they can still hold is the one that saves him.
        return v
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        // H21: come back beside terrain so Silver can be played.
        return if (BatchD.besideTerrain(ctx, choice.hex)) 1.5 else 0.0
    }

    private fun friendlyBeside(ctx: HContext, at: Hex): Boolean {
        for (hex in HexGrid.neighbors(at)) {
            val id = ctx.game.occupant(hex) ?: continue
            if (id == ctx.seat) continue
            if (ctx.state.hero(id)?.team == ctx.team) return true
            if (ctx.state.minion(id)?.team == ctx.team) return true
        }
        return false
    }

    private val steal = setOf("tigerclaw-green-1", "tigerclaw-green-2a", "tigerclaw-green-3a")
    private val backstab = setOf("tigerclaw-red-2b", "tigerclaw-red-3b")
    private val initiative = setOf("tigerclaw-green-2b", "tigerclaw-red-2a", "tigerclaw-green-3b")
}
