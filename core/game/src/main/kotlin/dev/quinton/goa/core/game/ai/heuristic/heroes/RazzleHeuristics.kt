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
import dev.quinton.goa.core.model.match.Team

/**
 * Razzle (heroes/razzle.md): Attack 2–5 is a poke; the bodies are the plan. Spawn where the figures are
 * not all caught, act with the figure the action wants, vanish the caught one after a red.
 * Which figure acts and which one is removed are opaque questions the one-ply look already answers;
 * these notes only lean them (Cryomancy, BGG 3659764).
 */
object RazzleHeuristics : HeroHeuristics {
    override val heroId: String = "razzle"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 1,
        aggression = 0.40,
        laneWeight = 0.60,
        // §3: red first, then green (two-body move), then blue.
        upgradeOrder = listOf("razzle-red-2a", "razzle-red-3a", "razzle-green-2a", "razzle-green-3a", "red", "green", "blue"),
        keepDefender = true,
    )

    private val golds = setOf("razzle-gold", "razzle-handicap")
    private val removers = setOf("razzle-red-1", "razzle-red-2a", "razzle-red-3a")

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> spawn(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        DecisionKind.RESPAWN -> respawn(ctx, choice)
        else -> 0.0
    }

    /** Extra figures that keep a partner inside silver's radius 3 (the Defense 1 + 2 per figure). */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val figs = BatchC.bodies(state, heroId)
        if (figs.size < 2) return 0.0
        val paired = figs.count { a -> figs.any { b -> b != a && HexGrid.distance(a, b) <= 3 } }
        return BatchC.sign(state, heroId, team) * 2.5 * (paired - 1).coerceAtLeast(0)
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (id !in golds) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        if (ctx.turn != 1 || BatchC.bodies(ctx.state, heroId).size != 1) return 0.0
        val holes = HexGrid.neighbors(at).filter { hex ->
            ctx.game.geo.isSpace(hex) && ctx.state.heroes.none { it.at == hex } && ctx.state.minions.none { it.at == hex }
        }
        if (holes.isEmpty()) return 0.0
        val danger = ctx.killWorth(me.id) * 0.35
        // H1: turn 1, one figure, and every spawn space is already a likely hit: do not open with gold.
        return if (holes.all { ctx.exposure(me.id, it) > danger }) -5.0 else 0.0
    }

    private fun spawn(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in golds || !d.prompt.startsWith("Spawn")) return 0.0
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val beside = ctx.enemies().count { e -> e.at?.let { ctx.distance(it, hex) == 1 } == true }
        // H2: spawn on empty spaces away from the enemy heroes; a figure in their face is a free target.
        return -2.5 * beside + if (beside == 0) 1.0 else 0.0
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.unitId(choice)
        val src = d.sourceCardId
        if (d.prompt.startsWith("Which one of you") && id != null) return actor(ctx, src, id)
        if (src in removers && d.prompt.contains("remove", ignoreCase = true)) return remove(ctx, id)
        return 0.0
    }

    /** H11: with an attack, act with the figure that already stands next to something worth hitting. */
    private fun actor(ctx: HContext, src: String?, id: String): Double {
        val card = src?.let { ctx.card(it) } ?: return 0.0
        if (!card.attackCard) return 0.0
        val at = BatchC.posOf(ctx, id) ?: return 0.0
        val minion = ctx.state.minions.any { m -> BatchC.takeable(ctx, m) && ctx.distance(m.at!!, at) == 1 }
        val hero = ctx.enemies().any { e -> e.at?.let { ctx.distance(it, at) == 1 } == true }
        return if (minion || hero) 1.5 else 0.0
    }

    /** H5: after a red, take away the figure an enemy hero stands next to; keep the rest. */
    private fun remove(ctx: HContext, id: String?): Double {
        if (id == null) return 0.0
        val at = BatchC.posOf(ctx, id) ?: return 0.0
        val caught = ctx.enemies().any { e -> e.at?.let { ctx.distance(it, at) == 1 } == true }
        return if (caught) 3.0 else -1.5
    }

    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (id != "razzle-silver") return 0.0
        // H4: silver's Defense is 1 + 2 per other figure (the engine previews it). When it holds it is the cheap
        // block; alone it is Defense 1 and the vanish is worth more than a doomed block.
        return when {
            BatchC.meets(d, id) && BatchC.bodies(ctx.state, heroId).size > 1 -> 2.5
            !BatchC.meets(d, id) && BatchC.otherMeets(d, id) -> -4.0
            else -> 0.0
        }
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val room = HexGrid.neighbors(hex).count { n ->
            ctx.game.geo.isSpace(n) && ctx.enemies().none { e -> e.at?.let { ctx.distance(it, n) <= 1 } == true }
        }
        // H15: come back where the next gold has empty spawn spaces.
        return 0.4 * room.coerceAtMost(4)
    }
}
