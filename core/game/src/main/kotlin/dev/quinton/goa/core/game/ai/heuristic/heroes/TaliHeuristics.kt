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
import dev.quinton.goa.core.game.heroes.TaliHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * Tali (heroes/tali.md): an adjacent fighter whose a-red hits for 5–7 from hand, whose gold splashes a
 * discard onto a second enemy in radius 4, and whose greens place Ice (−1 Initiative to an adjacent
 * enemy hero) or a Totem that saves one friendly minion.
 */
object TaliHeuristics : HeroHeuristics {
    override val heroId: String = "tali"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.50,
        laneWeight = 1.0,
        upgradeOrder = listOf("tali-red-2a", "tali-red-3a", "red", "green", "blue"),
        keepDefender = true,
    )

    private val golds = setOf("tali-gold", "tali-handicap")
    private val iceCards = setOf("tali-green-1", "tali-green-2a", "tali-green-3a")
    private val totemCards = setOf("tali-green-2b", "tali-green-3b")
    private val handReds = setOf("tali-red-1", "tali-red-2a", "tali-red-3a")

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> token(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    /** §6: Ice next to an enemy hero slows them; a Totem next to a minion it can save is that minion, once. */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val mine = state.hero(heroId)?.team ?: return 0.0
        var ice = 0.0
        var totem = 0.0
        for (piece in state.pieces) {
            val at = piece.at ?: continue
            if (piece.owner != heroId) continue
            if (piece.kind == TaliHero.ICE) {
                val touching = state.heroes.any { h ->
                    h.team != mine &&
                        h.at?.let { HexGrid.distance(at, it) == 1 } == true
                }
                ice += if (touching) 1.0 else 0.2
            } else if (piece.kind == TaliHero.TOTEM) {
                val venerated = piece.data["variant"] == "venerated"
                val saves = state.minions.any { m ->
                    m.team == mine && m.at?.let { HexGrid.distance(at, it) == 1 } == true &&
                        (venerated || m.type == MinionType.MELEE)
                }
                if (saves) totem = 4.0
            }
        }
        return BatchC.sign(state, heroId, team) * (ice.coerceAtMost(3.0) + totem)
    }

    /** Gold's splash: a second hero of the target's side in radius 4 may lose a card without being attacked. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(at, target) !in 2..4 || !BatchC.mayHold(ctx, heroId, golds)) return 0.0
        val foesNear = BatchC.foesOf(ctx.state, heroId).any { h -> h.at?.let { ctx.distance(at, it) == 1 } == true }
        return if (foesNear) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when (id) {
            in golds -> splash(ctx, at)
            in totemCards -> if (totemTargets(ctx, at, 2 + 1).isEmpty()) -1.0 else 1.0
            else -> 0.0
        }
    }

    /** H4 / H5: gold on an adjacent hero with a second enemy hero in radius 4 is the card; a lone poke is not. */
    private fun splash(ctx: HContext, at: Hex): Double {
        val adjacent = BatchC.enemiesWithin(ctx, at, 1)
        if (adjacent.isEmpty()) return 0.0
        val second = BatchC.enemiesWithin(ctx, at, 4).size >= 2
        return if (second) 3.0 else -1.0
    }

    private fun token(ctx: HContext, d: Decision, choice: Choice): Double {
        val hex = BatchC.hexOf(choice) ?: return 0.0
        return when (d.sourceCardId) {
            in iceCards -> ice(ctx, hex)
            in totemCards -> totem(ctx, hex)
            else -> 0.0
        }
    }

    /** H1: Ice goes next to enemy heroes (one touching two is better), never into a friendly hero's last exit. */
    private fun ice(ctx: HContext, hex: Hex): Double {
        val enemies = ctx.enemies().count { e -> e.at?.let { ctx.distance(hex, it) == 1 } == true }
        val sealsAlly = (ctx.allies() + listOfNotNull(ctx.own())).any { ally ->
            val at = ally.at ?: return@any false
            ctx.distance(hex, at) == 1 && HexGrid.neighbors(at).count { n -> open(ctx, n) } <= 2
        }
        return 2.0 * enemies + (if (enemies >= 2) 1.0 else 0.0) - (if (sealsAlly) 3.0 else 0.0)
    }

    /** H3: the Totem goes next to a friendly minion it can save that an enemy can reach. */
    private fun totem(ctx: HContext, hex: Hex): Double {
        val venerated = ctx.decision?.sourceCardId == "tali-green-3b"
        val saved = ctx.state.minions.filter { m ->
            m.team == ctx.team && m.at?.let { ctx.distance(hex, it) == 1 } == true &&
                (venerated || m.type == MinionType.MELEE)
        }
        if (saved.isEmpty()) return -1.0
        val threatened = saved.any { m ->
            ctx.enemies().any { e -> e.at?.let { ctx.distance(it, m.at!!) <= 3 } == true }
        }
        return if (threatened) 4.0 else 1.5
    }

    private fun totemTargets(ctx: HContext, at: Hex, reach: Int) = ctx.state.minions.filter { m ->
        m.team == ctx.team && m.at?.let { ctx.distance(at, it) <= reach } == true
    }

    private fun open(ctx: HContext, hex: Hex): Boolean = ctx.game.geo.isSpace(hex) &&
        ctx.state.heroes.none { it.at == hex } && ctx.state.minions.none { it.at == hex } &&
        ctx.state.pieces.none { it.at == hex }

    /** H12: the a-red is the 5–7 hand attack; spend another card that still holds. */
    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return if (id in handReds && BatchC.meets(d, id) && BatchC.otherMeets(d, id)) -3.0 else 0.0
    }
}
