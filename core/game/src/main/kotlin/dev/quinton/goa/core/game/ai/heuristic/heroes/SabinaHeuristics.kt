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
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Sabina (heroes/sabina.md): a range-2 gun whose bonus needs a hero who played an attack card, and skills
 * that move, swap and shoot off her friendly minions. Stay with the minions; shoot the hero who swung
 * (Cryomancy, BGG 3461122 and 3497427).
 */
object SabinaHeuristics : HeroHeuristics {
    override val heroId: String = "sabina"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 2,
        aggression = 0.50,
        laneWeight = 1.2,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    private val bonusReds = mapOf("sabina-red-1" to 3, "sabina-red-2a" to 3, "sabina-red-3a" to 4)
    private val needsFriendly = setOf(
        "sabina-silver",
        "sabina-green-1",
        "sabina-green-2a",
        "sabina-green-2b",
        "sabina-green-3a",
        "sabina-green-3b",
    )
    private val covering = setOf("sabina-green-2b", "sabina-green-3b")
    private val golds = setOf("sabina-gold", "sabina-handicap")

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        DecisionKind.KEEP_UPGRADE -> upgrade(ctx, choice)
        else -> 0.0
    }

    /** Her skills are on while friendly minions stand in radius 2 (§6 evalTerms, capped at three). */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        val at = me.at ?: return 0.0
        val near = state.minions.count { m ->
            m.team == me.team &&
                m.at?.let { HexGrid.distance(at, it) in 1..2 } == true
        }
        return BatchC.sign(state, heroId, team) * 1.5 * near.coerceAtMost(3)
    }

    /** Close Support / Covering Fire: a hero next to her minion, in radius 3, discards (or dies) without an attack. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val me = ctx.hero(heroId) ?: return 0.0
        val at = me.at ?: return 0.0
        if (ctx.distance(at, target) !in 1..3) return 0.0
        val besideMinion = ctx.state.minions.any { m ->
            m.team == me.team &&
                m.at?.let { ctx.distance(it, target) == 1 } == true
        }
        if (!besideMinion || !BatchC.mayHold(ctx, heroId, covering)) return 0.0
        return 2.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when {
            // H15: silver and the greens act through a friendly minion in radius; without one the primary is dead.
            id in needsFriendly && BatchC.minionsWithin(ctx, at, 3, friendly = true).isEmpty() -> -2.5

            id in covering -> coveringReady(ctx, at, id) + BatchC.heroOnlyDiscard(ctx, id, BatchC.radiusOf(ctx, id))

            id in golds -> goldPush(ctx, at)

            else -> 0.0
        }
    }

    /** H7: the discard (or the kill, on Covering Fire) needs a hero already standing on a friendly minion. */
    private fun coveringReady(ctx: HContext, at: Hex, id: String): Double {
        val radius = ctx.card(id).radius + ctx.itemBonus(heroId, Stat.RADIUS)
        val victims = BatchC.enemiesWithin(ctx, at, radius).filter { e ->
            val pos = e.at ?: return@filter false
            ctx.state.minions.any { m -> m.team == ctx.team && m.at?.let { ctx.distance(it, pos) == 1 } == true }
        }
        if (victims.isEmpty()) return -1.0
        val kill = id == "sabina-green-3b" && victims.any { it.hand.size <= 1 }
        return if (kill) 6.0 else 2.5
    }

    /** H4: gold is the init-12 shot that pushes an adjacent unit off; nothing adjacent, nothing pushed. */
    private fun goldPush(ctx: HContext, at: Hex): Double {
        val adjacent = BatchC.enemiesWithin(ctx, at, 1).isNotEmpty() ||
            BatchC.minionsWithin(ctx, at, 1, friendly = false).any { BatchC.takeable(ctx, it) }
        return if (adjacent) 1.5 else 0.0
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        val bonus = bonusReds[d.sourceCardId] ?: return 0.0
        // A look prices the strike from the engine's own total, the +3/+4 included.
        if (!d.prompt.startsWith("Target") || !ctx.patches) return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val body = ctx.heroBody(id) ?: return 0.0
        // H1: the +3/+4 applies to a hero who played an attack card this turn (public after the reveal).
        if (body !in ctx.state.turnRecord.playedAttack) return 0.0
        val src = ctx.card(d.sourceCardId!!)
        val total = src.attack + ctx.itemBonus(ctx.seat, Stat.ATTACK)
        return ctx.heroStrikeWorth(body, total + bonus, true) - ctx.heroStrikeWorth(body, total, true)
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        // H12: red is her attack and her block. A smaller card that still holds keeps it.
        return if (id.startsWith("sabina-red-") && BatchC.meets(d, id) && BatchC.otherMeets(d, id)) -3.0 else 0.0
    }

    /** H14: behind on the lane, keep the b-red that removes minions; otherwise the a-red that punishes attackers. */
    private fun upgrade(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val behind = ctx.lanes().any { ctx.laneMargin(it) < 0 }
        return when (id) {
            "sabina-red-2b", "sabina-red-3b" -> if (behind) 1.5 else -0.5
            "sabina-red-2a", "sabina-red-3a" -> if (behind) -0.5 else 1.5
            else -> 0.0
        }
    }
}
