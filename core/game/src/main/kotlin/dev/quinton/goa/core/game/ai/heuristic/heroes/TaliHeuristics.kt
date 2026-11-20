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
import dev.quinton.goa.core.game.heroes.TaliHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * Tali's a-red is Attack 5/6/7 from hand and 2/3/4 from the discard. Gold's second discard is
 * not printed reach. Ice slows enemy heroes only, and the tokens need not touch each other.
 */
object TaliHeuristics : HeroHeuristics {
    override val heroId: String = "tali"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 1,
        aggression = 0.45,
        laneWeight = 0.55,
        upgradeOrder = listOf(
            "tali-red-2a",
            "tali-red-3a",
            "tali-green-2a",
            "tali-green-3a",
            "tali-blue-2a",
            "tali-blue-3a",
            "red",
            "green",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> ice(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> shot(ctx, d, choice)
        DecisionKind.CHOOSE_OPTION -> colour(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val mine = state.hero(heroId)?.team
        val sign = BatchC.sign(state, heroId, team)
        var ice = 0.0
        for (piece in state.pieces) {
            val at = piece.at ?: continue
            if (piece.owner != heroId || piece.kind != TaliHero.ICE) continue
            val enemies = state.heroes.count { hero ->
                hero.team != mine && hero.at?.let { HexGrid.distance(at, it) == 1 } == true
            }
            ice += if (enemies > 0) 1.5 else 0.4
        }
        var totem = 0.0
        for (piece in state.pieces) {
            val at = piece.at ?: continue
            if (piece.owner != heroId || piece.kind != TaliHero.TOTEM) continue
            val venerated = piece.data["variant"] == "venerated"
            val saved = state.minions.any { minion ->
                minion.team == mine && minion.at?.let { HexGrid.distance(at, it) == 1 } == true &&
                    (venerated || minion.type == MinionType.MELEE)
            }
            if (saved) totem += 5.0
        }
        return sign * (ice.coerceAtMost(6.0) + totem)
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        var v = 0.0
        val iced = ctx.state.pieces.any { piece ->
            piece.owner == heroId && piece.kind == TaliHero.ICE &&
                piece.at?.let { ctx.distance(it, target) == 1 } == true
        }
        if (iced) v += 1.5
        val pair = BatchC.enemiesWithin(ctx, at, 4).size >= 2 && ctx.distance(at, target) in 1..4
        // Gold splashes a second hero in radius 4 when the first discards. That is not an attack range.
        if (pair) v += 2.0
        return v
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return splash(ctx, id) + replay(ctx, id)
    }

    private fun splash(ctx: HContext, id: String): Double {
        if (id != "tali-gold" && id != "tali-handicap") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val radius = if (id == "tali-handicap") 3 else 4
        val second = BatchC.enemiesWithin(ctx, at, radius).size >= 2
        val adjacent = BatchC.enemiesWithin(ctx, at, 1).isNotEmpty()
        // H5: gold on a lone hero is Attack 3 with no splash. Two enemies are the card.
        return when {
            second && adjacent -> 6.0
            second -> 3.0
            else -> -4.0
        }
    }

    private fun replay(ctx: HContext, id: String): Double {
        val hero = ctx.own() ?: return 0.0
        val winter = hero.hand.any { it.id == "tali-red-1" || it.id == "tali-red-2a" || it.id == "tali-red-3a" }
        if (!winter) return 0.0
        // H15: replaying the a-red from the discard loses the +3. Play it from hand.
        return when (id) {
            "tali-silver" -> -4.0
            "tali-red-1", "tali-red-2a", "tali-red-3a" -> 3.0
            else -> 0.0
        }
    }

    private fun ice(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in iceCards) return 0.0
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val enemies = ctx.enemies().count { enemy -> enemy.at?.let { ctx.distance(hex, it) == 1 } == true }
        val blocksAlly = ctx.allies().any { ally ->
            val at = ally.at ?: return@any false
            HexGrid.neighbors(at).count { neighbour ->
                ctx.state.heroes.none { it.at == neighbour } && ctx.state.minions.none { it.at == neighbour }
            } <= 1 && ctx.distance(hex, at) == 1
        }
        // H1: stack Ice on the hero whose initiative has to flip. Do not seal an ally's only exit.
        var v = enemies * 4.0
        if (enemies >= 2) v += 2.0
        if (blocksAlly) v -= 4.0
        return v
    }

    private fun shot(ctx: HContext, d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        if (src != "tali-red-2b" && src != "tali-red-3b") return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val fromDiscard = ctx.own()?.card(src)?.zone == Zone.DISCARD
        val at = ctx.own()?.at ?: return 0.0
        val pos = ctx.state.hero(id)?.at ?: ctx.state.minion(id)?.at ?: return 0.0
        val distance = ctx.distance(at, pos)
        // From the discard the range bullet is off. Only the adjacent hero remains.
        return if (fromDiscard && distance != 1) -6.0 else if (!fromDiscard && distance in 1..2) 2.0 else 0.0
    }

    private fun colour(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "tali-silver" || choice !is Choice.Option) return 0.0
        val hero = ctx.own() ?: return 0.0
        val discard = hero.cards.filter { it.zone == Zone.DISCARD && !it.faceDown }.map { ctx.card(it.id) }
        val named = choice.label
        val best = discard.filter { it.color.name.equals(named, ignoreCase = true) }
        val inHand = hero.hand.map { ctx.card(it.id) }.filter { it.color.name.equals(named, ignoreCase = true) }
        val pool = if (best.isNotEmpty()) best else inHand
        val winter = pool.all { it.id == "tali-red-1" || it.id == "tali-red-2a" || it.id == "tali-red-3a" }
        // H8: name a colour that can be performed. The a-red from the discard has lost its +3.
        return when {
            pool.isEmpty() -> -3.0
            pool.any { it.id == "tali-blue-3a" } -> 5.0
            winter && best.isNotEmpty() -> -2.0
            else -> 2.0
        }
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val winter = id == "tali-red-1" || id == "tali-red-2a" || id == "tali-red-3a"
        if (!winter || !BatchC.meets(d, id)) return 0.0
        val cheaper = d.options.filterIsInstance<Choice.Card>().any { card ->
            card.cardId != id && BatchC.meets(d, card.cardId)
        }
        // H12: the a-red is the 5–7 attack. Spend a blue that still holds.
        return if (cheaper) -4.0 else 1.0
    }

    private val iceCards: Set<String> = setOf("tali-green-1", "tali-green-2a", "tali-green-3a")
}
