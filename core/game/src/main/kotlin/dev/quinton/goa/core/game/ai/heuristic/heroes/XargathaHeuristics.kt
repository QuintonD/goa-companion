package dev.quinton.goa.core.game.ai.heuristic.heroes

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
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max

/**
 * Xargatha (`heroes/xargatha.md`): she manufactures Attack by standing in the enemy and pays in
 * Defense. The Slash gets +1/+2/+3 per other enemy unit beside her; Long Thrust gets +1 Range per
 * enemy beside her; Cleave repeats on a different enemy hero; Siren's Call pulls a unit to her side.
 */
object XargathaHeuristics : HeroHeuristics {
    override val heroId: String = "xargatha"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.6,
        laneWeight = 0.55,
        upgradeOrder = listOf("green", "red", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CONFIRM -> confirm(d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val at = self.at ?: return 0.0
        var v = 0.0
        // Constrict resolved: the minion beside her at end of round is hers (H8).
        val hug = state.effects.any { e -> e.owner == heroId && !e.spent && e.cardId in CONSTRICT }
        if (hug) {
            val embrace = state.effects.any { it.owner == heroId && !it.spent && it.cardId == "xargatha-green-3b" }
            val types = if (embrace) BOTH else MELEE
            if (types.any { BatchD.adjacentMinions(state, at, self.team, it) > 0 }) v += 5.0
        }
        // Stone Gaze pending: enemies in radius lose their move next turn (H9).
        if (BatchD.effect(state, heroId, GAZE, pending = true)) v += 1.5
        return BatchD.sign(state, heroId, team) * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchD.mayHold(ctx, heroId, SILVER)) return 0.0
        // H17: Siren's Call pulls a unit two or three away onto a hex beside her, at init 3.
        return if (ctx.distance(from, target) in 2..3) 1.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val card = ctx.card(id)
        val around = BatchD.adjacentEnemies(ctx.state, at, ctx.team)
        return when (id) {
            // H1/H2: the pull needs an enemy unit in range that is not adjacent, and a card to use it after.
            SILVER -> silver(ctx, at, card.range.coerceAtLeast(1))

            // H3/H4: Cleave hits a neighbour, then repeats on a different enemy hero.
            GOLD, HANDICAP -> {
                val hits = BatchD.strikeList(ctx, card, { uid, pos ->
                    ctx.distance(at, pos) == 1 && (id == GOLD || ctx.heroBody(uid) != null)
                })
                var real = hits.firstOrNull()?.second ?: -0.5
                val second = hits.drop(1).firstOrNull { ctx.heroBody(it.first) != null }
                if (second != null) real += 0.7 * max(0.0, second.second)
                BatchD.strikeFix(ctx, card, real)
            }

            // H5: the Slash gains the crowd: +1/+2/+3 Attack per other enemy unit beside her.
            in SLASH -> {
                val per = if (id == "xargatha-red-1") 1 else if (id == "xargatha-red-2a") 2 else 3
                val real = BatchD.strikeOn(
                    ctx,
                    card,
                    { _, pos -> ctx.distance(at, pos) == 1 },
                    bonus = { per * (around - 1).coerceAtLeast(0) },
                )
                BatchD.strikeFix(ctx, card, real)
            }

            // H6: Long Thrust's range is 1 plus the enemies beside her (plus a Range item).
            in THRUST -> {
                val range = ctx.attackReach(ctx.seat, card) + around
                val hits = BatchD.strikeList(ctx, card, { _, pos -> ctx.distance(at, pos) in 1..range })
                var real = hits.firstOrNull()?.second ?: -0.5
                val second = hits.drop(1).firstOrNull { ctx.heroBody(it.first) != null }
                if (id == "xargatha-red-3b" && second != null) real += 0.6 * max(0.0, second.second)
                BatchD.strikeFix(ctx, card, real)
            }

            // H8: Constrict defeats a minion beside her at end of round, if she is still there.
            in CONSTRICT -> {
                val types = if (id == "xargatha-green-3b") BOTH else MELEE
                val best = ctx.state.minions.filter { m ->
                    m.team != ctx.team && m.type in types && m.at != null && ctx.distance(at, m.at!!) == 1
                }.maxOfOrNull { ctx.minionWorth(it.id) }
                if (best == null) -1.5 else best * (0.4 + 0.15 * ctx.turn)
            }

            // H9: Stone Gaze takes the next turn's move from enemy heroes in radius.
            in GAZE -> {
                val n = BatchD.enemyHeroesWithin(ctx, at, 1, card.radius.coerceAtLeast(1)).size
                if (n == 0) -1.0 else 1.5 * minOf(n, 2)
            }

            // H11: Fresh Converts retrieves a card only while she is beside an enemy minion (unit, tier III).
            in CONVERTS -> {
                val beside = if (id == "xargatha-blue-3b") {
                    around > 0
                } else {
                    BatchD.adjacentMinions(ctx.state, at, ctx.team) > 0
                }
                val discard = ctx.own()?.cards?.any { it.zone == Zone.DISCARD } == true
                if (beside && discard) 2.0 else -1.5
            }

            else -> 0.0
        }
    }

    private fun silver(ctx: HContext, at: Hex, range: Int): Double {
        val attack = ctx.ownHand().any { it.attackCard }
        val minion = ctx.state.minions.any { m ->
            m.team != ctx.team && m.at != null && ctx.distance(at, m.at!!) in 2..range && !ctx.heavyImmune(m.id)
        }
        val hero = BatchD.enemyHeroesWithin(ctx, at, 2, range).isNotEmpty()
        return when {
            !minion && !hero -> -2.0
            attack && minion -> 2.0
            attack -> 1.0
            else -> 0.0
        }
    }

    /** H4: the repeat on a different enemy hero is free; the engine asks only when one is there. */
    private fun confirm(d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        if (src != GOLD && src != HANDICAP && src != "xargatha-red-3b") return 0.0
        return if (choice is Choice.Yes) 2.0 else 0.0
    }

    /** H14/H15: Deadly Swipe and Control are the default branches. */
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            "xargatha-red-2a", "xargatha-red-3a" -> 1.0
            "xargatha-green-2a", "xargatha-green-3a" -> 1.0
            else -> 0.0
        }
    }

    private const val GOLD = "xargatha-gold"
    private const val HANDICAP = "xargatha-handicap"
    private const val SILVER = "xargatha-silver"
    private val SLASH = setOf("xargatha-red-1", "xargatha-red-2a", "xargatha-red-3a")
    private val THRUST = setOf("xargatha-red-2b", "xargatha-red-3b")
    private val GAZE = setOf("xargatha-blue-1", "xargatha-blue-2a", "xargatha-blue-3a")
    private val CONSTRICT = setOf("xargatha-green-2b", "xargatha-green-3b")
    private val MELEE = setOf(MinionType.MELEE)
    private val BOTH = setOf(MinionType.MELEE, MinionType.RANGED)
    private val CONVERTS = setOf("xargatha-blue-2b", "xargatha-blue-3b")
}
