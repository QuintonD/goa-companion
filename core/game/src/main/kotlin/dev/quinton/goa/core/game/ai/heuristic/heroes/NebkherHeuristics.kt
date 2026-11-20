package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.heroes.NebkherHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team

/**
 * Nebkher copies a card from his own space, on a later turn. Turn 1 has no previous slot, so
 * gold's copy cannot start and the minion bullet is the only other primary. Illusions are how
 * he gets into range 5 and how red A tags a hero.
 */
object NebkherHeuristics : HeroHeuristics {
    override val heroId: String = "nebkher"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 4,
        aggression = 0.35,
        laneWeight = 0.45,
        upgradeOrder = listOf("nebkher-green-2a", "nebkher-red-2b", "nebkher-blue-2b", "green", "red", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_ACTION -> copied(ctx, d, choice)
        DecisionKind.CHOOSE_OPTION -> colour(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchB.sign(state, team, heroId)
        val illusions = BatchB.tokens(state, heroId, NebkherHero.ILLUSION)
        val hero = state.hero(heroId)?.at
        var value = 0.0
        if (hero != null) {
            val beside = illusions.count { token ->
                state.heroes.any { enemy ->
                    enemy.team != state.hero(heroId)?.team &&
                        enemy.at?.let { HexGrid.distance(it, token.at!!) == 1 } == true
                }
            }
            value += beside * 3.0
        }
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        var threat = 0.0
        if (ctx.distance(from, target) <= 5 && ctx.turn >= 2 &&
            BatchB.hold(ctx, heroId) { it.id == "nebkher-gold" } > 0.2
        ) {
            // Copy range, not an attack of 5. A small extra so the general book does not ignore the halo.
            threat += 3.0
        }
        if (BatchB.besideToken(ctx.state, target, NebkherHero.ILLUSION) && ctx.distance(from, target) <= 4) {
            threat += 4.0
        }
        return threat
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return gold(ctx, id) + green(ctx, id) + laugh(ctx, id)
    }

    // H1: turn 2 or later, copy a previous-slot card that still does something from here. Turn 1, do not.
    private fun gold(ctx: HContext, id: String): Double {
        if (id != "nebkher-gold") return 0.0
        if (ctx.turn <= 1) return -8.0
        val at = ctx.own()?.at ?: return 0.0
        val copy = ctx.enemies().any { enemy ->
            val pos = enemy.at ?: return@any false
            ctx.distance(at, pos) <= 5 && worthCopying(ctx, enemy.id)
        }
        val minion = BatchB.enemyMinionWithin(ctx, at, 1)
        return when {
            copy -> 10.0
            minion -> 6.0
            else -> -6.0
        }
    }

    // H3: out of range, the illusion place is the turn. It is also his movement.
    private fun green(ctx: HContext, id: String): Double {
        if (!id.startsWith("nebkher-green")) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val inRange = ctx.enemies().count { enemy -> enemy.at?.let { ctx.distance(at, it) <= 5 } == true }
        return if (inRange < 2 || ctx.turn == 1) 6.0 else 0.0
    }

    // H6: with the ultimate, the laugh itself discards everyone in radius 5.
    private fun laugh(ctx: HContext, id: String): Double {
        if (id != "nebkher-silver") return 0.0
        val hero = ctx.own() ?: return 0.0
        if (!hero.ultimateUnlocked) return 0.0
        val at = hero.at ?: return 0.0
        val victims = ctx.enemies().count { enemy -> enemy.at?.let { ctx.distance(at, it) <= 5 } == true }
        return if (victims >= 2) 12.0 else -4.0
    }

    // H2: a copied card is performed from Nebkher. Hold is not a copy. Primary beats a blank move.
    private fun copied(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "nebkher-gold") return 0.0
        val act = (choice as? Choice.Action)?.action ?: return 0.0
        return when (act) {
            ActionType.HOLD -> -8.0
            ActionType.PRIMARY -> 6.0
            ActionType.ATTACK -> 4.0
            else -> 0.0
        }
    }

    // H9: name a colour on turns 1–3, never on turn 4 (the trigger does not cross the round).
    private fun colour(ctx: HContext, d: Decision, choice: Choice): Double {
        val source = d.sourceCardId.orEmpty()
        val naming = source.startsWith("nebkher-blue-1") || source.startsWith("nebkher-blue-2a") ||
            source.startsWith("nebkher-blue-3a")
        if (!naming || !d.prompt.startsWith("Name a color")) return 0.0
        if (ctx.turn >= 4) return if (choice is Choice.Decline) 4.0 else -6.0
        val named = (choice as? Choice.Option)?.label?.let(::colourOf) ?: return 0.0
        val useful = ctx.enemies().any { enemy ->
            BatchB.hold(ctx, enemy.id) { it.color == named && (it.defense ?: 0) >= 4 } > 0.25
        }
        return if (useful) 4.0 else 0.0
    }

    private fun worthCopying(ctx: HContext, enemyId: String): Boolean {
        val card = BatchB.previous(ctx, enemyId) ?: return false
        if (card.id.endsWith("hold")) return false
        return card.attackCard || card.forcesDiscard || card.skillCard || card.hasMovement
    }

    private fun colourOf(label: String): CardColor? = when {
        label.contains("Gold", ignoreCase = true) -> CardColor.GOLD
        label.contains("Silver", ignoreCase = true) -> CardColor.SILVER
        label.contains("Red", ignoreCase = true) -> CardColor.RED
        label.contains("Green", ignoreCase = true) -> CardColor.GREEN
        label.contains("Blue", ignoreCase = true) -> CardColor.BLUE
        else -> null
    }
}
