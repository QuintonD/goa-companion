package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.heroes.SnorriHero.Rune
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Snorri (heroes/snorri.md): Inscribe the Runes once, early, and play each card on the turn whose rune
 * powers it. The markers are public (`SnorriHero.placedRunes`), so both sides may read them.
 */
object SnorriHeuristics : HeroHeuristics {
    override val heroId: String = "snorri"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.55,
        laneWeight = 1.0,
        upgradeOrder = listOf(
            "snorri-red-2a",
            "snorri-red-3a",
            "snorri-green-2a",
            "snorri-green-3a",
            "red",
            "green",
            "blue",
        ),
        keepDefender = true,
    )

    private val golds = setOf("snorri-gold", "snorri-handicap")
    private val oaths = setOf("snorri-green-1", "snorri-green-2a", "snorri-green-3a")
    private val casters = setOf("snorri-red-2b", "snorri-red-3b")

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> inscribe(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> target(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    /** §6: no runes past turn 1 is a blank kit. A rune on the board is not a token bonus. */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        if (BatchC.runeCount(me) > 0 || (state.round <= 1 && state.turn <= 1)) return 0.0
        return BatchC.sign(state, heroId, team) * -3.0
    }

    /** His gold under axe is Attack 5 adjacent; under horn it repeats on a second hero in range 2. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val me = ctx.hero(heroId) ?: return 0.0
        val at = me.at ?: return 0.0
        if (!BatchC.mayHold(ctx, heroId, golds)) return 0.0
        val d = ctx.distance(at, target)
        // The horn's repeat reaches range 2 only after a first hit on a unit beside him.
        return when (BatchC.activeRune(me, ctx.state.turn)) {
            Rune.AXE -> if (d == 1) 2.5 else 0.0
            Rune.HORN -> if (d == 2 && besideFoe(ctx, me)) 2.0 else 0.0
            else -> 0.0
        }
    }

    private fun besideFoe(ctx: HContext, me: HeroState): Boolean {
        val at = me.at ?: return false
        return BatchC.foesOf(ctx.state, me.id).any { h -> h.at?.let { ctx.distance(at, it) == 1 } == true } ||
            ctx.state.minions.any { m -> m.team != me.team && m.at?.let { ctx.distance(at, it) == 1 } == true }
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val me = ctx.own() ?: return 0.0
        val runes = BatchC.runeCount(me)
        val rune = BatchC.activeRune(me, ctx.turn)
        return when (id) {
            // H1 / H3: inscribe once, early. Re-inscribing spends a turn the attack needed.
            "snorri-silver" -> if (runes == 0) (if (ctx.turn <= 3) 6.0 else 2.0) else -2.5

            in golds -> goldRune(ctx, rune, id)

            // H11: the oath has no primary; walking with it spends the block.
            in oaths -> if (runes > 0) -2.0 else 0.0

            // H12: the trap makes an enemy hero in radius discard; it never takes a minion.
            "snorri-green-2b", "snorri-green-3b" -> BatchC.heroOnlyDiscard(ctx, id, BatchC.radiusOf(ctx, id))

            else -> 0.0
        }
    }

    /** H4 / H6: the rune decides what gold is for; without one it is Attack 2 adjacent. */
    private fun goldRune(ctx: HContext, rune: Rune?, id: String): Double {
        val at = ctx.own()?.at ?: return 0.0
        val adjacentHero = BatchC.enemiesWithin(ctx, at, 1).isNotEmpty()
        val adjacentMinion = BatchC.minionsWithin(ctx, at, 1, friendly = false).any { BatchC.takeable(ctx, it) }
        return when (rune) {
            null -> -1.5

            Rune.AXE -> if (adjacentHero || adjacentMinion) 2.5 else 0.0

            // The repeat needs a first target adjacent, then a different hero in range.
            Rune.HORN -> if (hornPair(ctx, at, id)) 3.0 else 0.0

            Rune.BIRD -> if (BatchC.minionsWithin(ctx, at, 2, friendly = false).any {
                    BatchC.takeable(ctx, it)
                }
            ) {
                2.0
            } else {
                0.0
            }

            Rune.ANVIL -> if (adjacentHero) 3.0 else 0.0
        }
    }

    /** Rune Sigils under horn: an adjacent target, then a different enemy hero in range (handicap: heroes only). */
    private fun hornPair(ctx: HContext, at: Hex, id: String): Boolean {
        val reach = ctx.attackReach(ctx.seat, ctx.card(id))
        val heroOnly = id == "snorri-handicap"
        val minions = if (heroOnly) {
            emptyList()
        } else {
            BatchC.minionsWithin(ctx, at, 1, friendly = false).filter { BatchC.takeable(ctx, it) }.map { it.id }
        }
        val first = BatchC.enemiesWithin(ctx, at, 1).map { it.id } + minions
        val second = BatchC.enemiesWithin(ctx, at, reach).map { it.id }
        return first.any { a -> second.any { it != a } }
    }

    /**
     * H2: inscription. Gold and the melee red want axe (+3) on the turns he attacks; the slot he spends on
     * silver itself takes the leftover. The one-ply look scores the rest.
     */
    private fun inscribe(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "snorri-silver" || choice !is Choice.Option) return 0.0
        val slot = BatchC.slotNumber(d.prompt) ?: return 0.0
        val label = choice.label
        if (slot == ctx.turn) return if (label == Rune.ANVIL.label) 1.0 else 0.0
        return when (label) {
            Rune.AXE.label -> 2.5
            Rune.HORN.label -> 1.5
            else -> 0.0
        }
    }

    private fun target(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.unitId(choice) ?: return 0.0
        val me = ctx.own() ?: return 0.0
        val rune = BatchC.activeRune(me, ctx.turn)
        val src = d.sourceCardId
        // H6: anvil pays 3 coins for targeting a hero, even when they block.
        if (src == "snorri-gold" && rune == Rune.ANVIL && d.prompt.startsWith("Target")) {
            val hero = ctx.state.hero(id)
            return if (hero != null && hero.team != ctx.team) 3.0 else 0.0
        }
        // H9: axe on the ranged red: an enemy hero next to the target discards, or is defeated.
        if (src in casters && rune == Rune.AXE && d.prompt.startsWith("Target")) {
            val at = BatchC.posOf(ctx, id) ?: return 0.0
            val splash = ctx.enemies().any { e -> e.id != id && e.at?.let { ctx.distance(it, at) == 1 } == true }
            return if (splash) 3.0 else 0.0
        }
        return 0.0
    }

    /** H10: the engine previews whether the oath's rune blocks this attack. A failed oath is a defeat. */
    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (id !in oaths) return 0.0
        return when (d.context.blockPreview[id]) {
            true -> 3.0
            false -> -10.0
            null -> 0.0
        }
    }
}
