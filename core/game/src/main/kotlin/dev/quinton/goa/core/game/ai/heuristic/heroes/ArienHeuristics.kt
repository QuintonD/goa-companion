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
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Arien: a melee fighter who discards through the line behind a minion and blanks non-gold skills.
 * Spell Break is a threat to hold. The line is the attack.
 */
object ArienHeuristics : HeroHeuristics {
    override val heroId: String = "arien"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.60,
        laneWeight = 0.55,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        val at = me.at ?: return 0.0
        val sign = BatchA.sign(state, team, heroId)
        var v = 0.0
        // H eval: Spell Break blanks whoever is still inside it. The skill filter needs a card profile.
        if (BatchA.effectLive(state, heroId, SILVER)) {
            val caught = state.heroes.count { e ->
                e.team != me.team && e.at?.let { HexGrid.distance(at, it) in 1..3 } == true
            }
            v += caught * 4.0
        }
        // H eval: Slippery Ground / Deluge tax an adjacent enemy who has not left yet.
        if (BatchA.effectLive(state, heroId, SLIPPERY)) {
            val capped = state.heroes.count { e ->
                e.team != me.team && e.at?.let { HexGrid.distance(at, it) == 1 } == true
            }
            v += capped * 3.0
        }
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        var v = 0.0
        // H threat: the line behind a minion is a discard the printed reach does not include.
        if (BatchA.poolHas(ctx, heroId, CURRENT)) {
            val behind = ctx.state.minions.any { m ->
                val at = m.at ?: return@any false
                HexGrid.distance(from, at) == 1 && BatchA.heroBehind(ctx, from, at, behindOf(ctx))?.at == target
            }
            if (behind) v += if (BatchA.played(ctx, heroId)?.id in CURRENT) 10.0 else 6.0
        }
        // Standing in the radius is only a skill tax. Exposure cannot see that the victim is resolving a skill.
        if (BatchA.poolHas(ctx, heroId, SILVER) && HexGrid.distance(from, target) in 1..3) v += 2.0
        return v
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            in SILVER -> silver(ctx)
            in GOLD -> gold(ctx)
            in CURRENT -> current(ctx, choice.cardId)
            else -> 0.0
        }
    }

    // H2 / H3: Spell Break only when a non-gold skill in radius has nothing left but that skill.
    private fun silver(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val radius = 3 + (ctx.own()?.let { ctx.itemBonus(it.id, dev.quinton.goa.core.model.catalog.Stat.RADIUS) } ?: 0)
        val near = BatchA.enemiesWithin(ctx, at, radius)
        if (near.isEmpty()) return -8.0
        if (near.any { BatchA.nonGoldSkillShowing(ctx, it.id) }) return 12.0
        if (near.all { BatchA.onlyAttacksOrMoves(ctx, it.id) }) return -8.0
        return 0.0
    }

    // H15: gold finishes. It is not the turn-1 walk when a Movement 4 card can farm.
    private fun gold(ctx: HContext): Double {
        if (ctx.turn != 1) return 0.0
        val walk = ctx.ownHand().any { it.movement >= 4 && it.hasMovement }
        return if (walk) -6.0 else 0.0
    }

    // H1: primary red is the line through a minion onto a hero, not a plain adjacent poke.
    private fun current(ctx: HContext, cardId: String): Double {
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        val n = BEHIND[cardId] ?: 2
        var best = 0.0
        val units = adjacentEnemyHexes(ctx, at)
        for (hex in units) {
            val behind = BatchA.heroBehind(ctx, at, hex, n) ?: continue
            val weight = when (ctx.handCount(behind.id)) {
                0 -> 14.0
                1 -> 10.0
                else -> 4.0
            }
            if (weight > best) best = weight
        }
        if (best > 0.0) return best
        // H12: the line is one step away and Living Tsunami, or a move, can make it. Hold the red.
        return if (ctx.turn == 1) -2.0 else 0.0
    }

    // H5: Duelist ignores a negative minion modifier. A card one point higher does not.
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Card || choice.cardId !in DUELIST) return 0.0
        val mod = d.context.minionModifier ?: ctx.own()?.at?.let { ctx.minionModifier(it, ctx.team) } ?: return 0.0
        return if (mod < 0) -3.0 * mod else 0.0
    }

    // H11: Violent Torrent repeats when a second enemy unit is still adjacent.
    private fun confirm(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "arien-red-3a" || choice !is Choice.Yes) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val more = adjacentEnemyHexes(ctx, at).size
        return if (more >= 1) 12.0 else -4.0
    }

    // H1 on the optional behind-target: name the hero on the line.
    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in CURRENT || choice !is Choice.Unit) return 0.0
        val hero = ctx.hero(choice.unitId) ?: return 0.0
        return when (ctx.handCount(hero.id)) {
            0 -> 14.0
            1 -> 8.0
            else -> 4.0
        }
    }

    // H7: the leap wants the hex that puts a hero on the red line, not a random space in range.
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in LEAP || choice !is Choice.Space) return 0.0
        val at = choice.hex
        val n = behindOf(ctx)
        val line = ctx.state.minions.any { m ->
            val pos = m.at ?: return@any false
            m.team != ctx.team && HexGrid.distance(at, pos) == 1 &&
                BatchA.heroBehind(ctx, at, pos, n) != null
        }
        return if (line) 8.0 else 0.0
    }

    // H13: primary red and primary blue. The Initiative item is the tuck, so the kept card is the a-path.
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId in PRIMARY_KEEP) 1.5 else 0.0
    }

    private fun behindOf(ctx: HContext): Int {
        val id = ctx.hero(heroId)?.cards?.firstOrNull { it.id in CURRENT && it.zone != Zone.DECK }?.id
        return BEHIND[id] ?: 2
    }

    private fun adjacentEnemyHexes(ctx: HContext, at: Hex): List<Hex> {
        val hexes = ArrayList<Hex>()
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) == 1) hexes += pos
        }
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team != ctx.team && ctx.distance(at, pos) == 1) hexes += pos
        }
        return hexes
    }

}

private val GOLD = setOf("arien-gold", "arien-handicap")
private val SILVER = setOf("arien-silver")
private val CURRENT = setOf("arien-red-1", "arien-red-2a", "arien-red-3a")
private val DUELIST = setOf("arien-blue-1", "arien-blue-2a", "arien-blue-3a")
private val SLIPPERY = setOf("arien-blue-2b", "arien-blue-3b")
private val LEAP = setOf("arien-green-1", "arien-green-2a", "arien-green-3a")
private val PRIMARY_KEEP = setOf("arien-red-2a", "arien-red-3a", "arien-blue-2a", "arien-blue-3a")
private val BEHIND = mapOf("arien-red-1" to 2, "arien-red-2a" to 3, "arien-red-3a" to 5)
