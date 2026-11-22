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
import dev.quinton.goa.core.game.heroes.WidgetHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Widget (`heroes/widget.md`): two bodies. Pyro is the origin of Breath (straight lines from Pyro) and
 * of the swaps; Nibble/Gnaw eat a minion beside Pyro and remove it; Fight as One copies a resolved skill
 * after the hit. Every Pyro card fizzles while Pyro is off the board.
 */
object WidgetHeuristics : HeroHeuristics {
    override val heroId: String = "widget"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 2,
        aggression = 0.4,
        laneWeight = 0.65,
        upgradeOrder = listOf("blue", "green", "red"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_CARD -> copy(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val pyro = BatchDSupport.pyro(state) ?: return 0.0
        // Pyro on the board is half the kit; beside an enemy minion it is a Nibble or a Gnaw.
        var v = 3.0
        val besideMinion = state.minions.any { m ->
            m.team != self.team && m.at != null && HexGrid.distance(pyro, m.at!!) == 1
        }
        if (besideMinion) v += 1.5
        return BatchDSupport.sign(state, heroId, team) * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val pyro = BatchDSupport.pyro(ctx.state) ?: return 0.0
        if (BREATH.none { BatchDSupport.mayHold(ctx, heroId, it) }) return 0.0
        val d = ctx.distance(pyro, target)
        // H16: Breath is measured from Pyro, which printed reach from Widget does not cover.
        return if (d in 1..3 && HexGrid.inStraightLine(pyro, target)) 1.5 else 0.0
    }

    /** With the victim known: the reach above, priced by what that card costs them (#19), not a flat 1.5. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex, victimId: String): Double {
        val plain = threatAdjust(ctx, enemyHeroId, target)
        if (plain <= 0.0) return plain
        return PlaybookSupport.extraReach(ctx, heroId, victimId, target, BREATH)
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val self = ctx.own() ?: return 0.0
        val id = choice.cardId
        val card = ctx.card(id)
        val pyro = BatchDSupport.pyro(ctx.state)
        return when (id) {
            // H1/H20: Silver brings Pyro back. With Pyro out and well placed it is a Defense 6 to keep.
            SILVER -> when {
                pyro == null -> 4.0
                BatchDSupport.minionSpawn(ctx, pyro) || ctx.distance(at, pyro) > 3 -> 1.5
                else -> -0.5
            }

            // H2: Gold hits a neighbour, then copies a resolved skill on a different unit.
            GOLD, HANDICAP -> {
                val real = BatchDSupport.strikeOn(ctx, card, { uid, pos ->
                    ctx.distance(at, pos) == 1 && (id == GOLD || ctx.heroBody(uid) != null)
                })
                var v = BatchDSupport.strikeFix(ctx, card, real)
                if (real > 0.0 && BatchDSupport.resolvedSkill(ctx, self)) v += 2.0
                v
            }

            // H4: Breath discards a hero in range of Pyro and in a straight line from it.
            in BREATH -> BatchDSupport.outlookFix(ctx, card, breath(ctx, id, pyro))

            // H5: Nibble/Gnaw take a minion in her range beside Pyro, and Pyro leaves.
            // The card look resolves the meal and Pyro's leaving (evalTerms) itself.
            in EAT -> if (ctx.patches) eat(ctx, at, id, pyro) else 0.0

            // H8: the swaps need Pyro in range.
            in ABOARD -> if (pyro == null || ctx.distance(at, pyro) > card.range.coerceAtLeast(1)) -1.5 else 0.0

            // H9: Drag Off needs an enemy unit beside Pyro.
            in DRAG -> {
                val beside = pyro != null && BatchDSupport.adjacentEnemies(ctx.state, pyro, ctx.team) > 0
                if (beside) 0.5 else -1.5
            }

            else -> 0.0
        }
    }

    private fun breath(ctx: HContext, id: String, pyro: Hex?): Double {
        if (pyro == null) return -1.5
        val range = ctx.card(id).range.coerceAtLeast(1)
        val victims = ctx.enemies().filter { e ->
            e.at != null && ctx.distance(pyro, e.at!!) in 1..range && HexGrid.inStraightLine(pyro, e.at!!)
        }
        if (victims.isEmpty()) return -1.5
        return victims.maxOf { e ->
            if (id == "widget-green-3a" && e.hand.isEmpty()) ctx.killWorth(e.id) else ctx.discardWorth(e)
        }
    }

    private fun eat(ctx: HContext, at: Hex, id: String, pyro: Hex?): Double {
        if (pyro == null) return -1.5
        val range = ctx.card(id).range.coerceAtLeast(1)
        val best = ctx.state.minions.filter { m ->
            m.team != ctx.team && m.at != null && ctx.distance(at, m.at!!) in 1..range &&
                ctx.distance(pyro, m.at!!) == 1 && !ctx.heavyImmune(m.id)
        }.maxOfOrNull { ctx.minionWorth(it.id) } ?: return -1.5
        // Nibble removes (no coins); Gnaw defeats. Pyro goes either way; Silver brings it back.
        val coins = if (id == "widget-green-3b") 0.0 else -2.0
        val loss = if (ctx.ownHand().any { it.id == SILVER }) 2.0 else 4.0
        return best + coins - loss
    }

    /** H3: Fight as One copies Breath on a hero first, then a minion meal, then a swap. */
    private fun copy(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        if (d.sourceCardId != GOLD && d.sourceCardId != HANDICAP) return 0.0
        val id = choice.cardId
        return when {
            id in BREATH -> 3.0
            id in EAT -> 2.0
            ctx.card(id).skillCard -> 1.0
            else -> 0.0
        }
    }

    /** H18: Pyro's own hex. Placements are scored as if Widget stood there; take that back. */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val subject = d.context.subjectId
        val pyroMove = subject != null && ctx.state.piece(subject)?.kind == WidgetHero.PYRO
        val pyroPlace = subject == null && d.prompt.startsWith("Place Pyro")
        if (!pyroMove && !pyroPlace) return 0.0
        return BatchDSupport.unplace(ctx, d, choice.hex) + site(ctx, choice.hex)
    }

    /** A Pyro hex: a straight line onto an enemy hero (Breath), beside an enemy minion (Gnaw), not a spawn. */
    private fun site(ctx: HContext, hex: Hex): Double {
        var v = 0.0
        val lined = ctx.enemies().any { e ->
            e.at?.let { ctx.distance(hex, it) in 1..3 && HexGrid.inStraightLine(hex, it) } == true
        }
        if (lined) v += 2.0
        if (BatchDSupport.adjacentMinions(ctx.state, hex, ctx.team) > 0) v += 1.5
        if (BatchDSupport.minionSpawn(ctx, hex)) v -= 4.0
        return v
    }

    private const val GOLD = "widget-gold"
    private const val HANDICAP = "widget-handicap"
    private const val SILVER = "widget-silver"
    private val BREATH = setOf("widget-green-1", "widget-green-2a", "widget-green-3a")
    private val EAT = setOf("widget-green-2b", "widget-green-3b")
    private val ABOARD = setOf("widget-blue-1", "widget-blue-2a", "widget-blue-3a")
    private val DRAG = setOf("widget-blue-2b", "widget-blue-3b")
}
