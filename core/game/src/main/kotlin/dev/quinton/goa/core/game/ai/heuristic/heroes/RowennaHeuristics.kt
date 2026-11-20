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
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Rowenna stands in the fight, hands coins to allies, and executes only an empty hand.
 * Gold retrieves before the swing, so a fat card in their discard is a heal for them
 * (CCplusplus, BGG 3447239; rulings/rowenna.md 1).
 */
object RowennaHeuristics : HeroHeuristics {
    override val heroId: String = "rowenna"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.60,
        laneWeight = 0.45,
        upgradeOrder = listOf(
            "rowenna-red-2a",
            "rowenna-red-3a",
            "rowenna-blue-2a",
            "rowenna-blue-3a",
            "rowenna-green-2a",
            "rowenna-green-3a",
            "red",
            "blue",
            "green",
        ),
        keepDefender = true,
    )

    override fun override(ctx: HContext, d: Decision): Choice? {
        val card = executeCard(ctx) ?: return null
        val victim = emptyAdjacent(ctx) ?: return null
        // H11: an adjacent hero with no cards is the execute. Gold is not.
        return when (d.kind) {
            DecisionKind.PLAY_CARD ->
                d.options.firstOrNull { it is Choice.Card && it.cardId == card }
            DecisionKind.CHOOSE_UNIT ->
                if (d.sourceCardId == card) {
                    d.options.firstOrNull { it is Choice.Unit && it.unitId == victim }
                } else {
                    null
                }
            else -> null
        }
    }

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> target(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val at = state.hero(heroId)?.at ?: return 0.0
        val sign = BatchC.sign(state, heroId, team)
        val mine = state.hero(heroId)?.team
        val ally = state.heroes.any { hero ->
            hero.team == mine && hero.id != heroId &&
                hero.at?.let { HexGrid.distance(at, it) in 1..3 } == true
        }
        var v = if (ally) 1.0 else 0.0
        val empty = state.heroes.any { hero ->
            hero.team != mine && hero.hand.isEmpty() &&
                hero.at?.let { HexGrid.distance(at, it) == 1 } == true
        }
        if (empty) v += 2.0
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        val held = ctx.state.hero(heroId) ?: return 0.0
        if (BatchC.onDashboard(held, "rowenna-silver")) return 0.0
        val besideEnemy = ctx.state.heroes.any { hero ->
            hero.id != heroId && hero.at?.let { ctx.distance(it, target) == 1 } == true
        }
        // Silver places her adjacent to a hero in range 4. Movement 2 does not.
        return if (besideEnemy && ctx.distance(at, target) <= 4) 3.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return holdExecute(ctx, id) + openingShots(ctx, id) + silver(ctx, id)
    }

    private fun holdExecute(ctx: HContext, id: String): Double {
        if (id != "rowenna-blue-2b" && id != "rowenna-blue-3b") return 0.0
        if (ctx.turn >= 4 || emptyAdjacent(ctx) != null) return 0.0
        // H12: the execute is also Defense 6. Do not walk with it before turn 4.
        return -6.0
    }

    private fun openingShots(ctx: HContext, id: String): Double {
        if (id != "rowenna-green-2b" && id != "rowenna-green-3b") return 0.0
        val self = ctx.own() ?: return 0.0
        val ownDiscard = self.cards.any { it.zone == Zone.DISCARD }
        val at = self.at ?: return 0.0
        val victim = BatchC.enemiesWithin(ctx, at, 3).any { enemy -> enemy.hand.isEmpty() && emptyDiscard(enemy) }
        // H7 / H8: both discards must be empty, and it is a turn-1 strip, not a closer.
        return when {
            ownDiscard || !victim -> -4.0
            ctx.turn == 1 -> 4.0
            else -> 0.0
        }
    }

    private fun silver(ctx: HContext, id: String): Double {
        if (id != "rowenna-silver") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val inRange = BatchC.enemiesWithin(ctx, at, 4)
        if (inRange.isEmpty()) return -3.0
        val canLeave = inRange.all { enemy ->
            val played = enemy.unresolved.firstOrNull()?.let { ctx.card(it.id) }
            played != null && played.hasMovement && played.movement >= 2
        }
        // H3 / H4: silver fizzles when every hero in range 4 can still walk out.
        return if (canLeave) -4.0 else 3.0
    }

    private fun target(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.unitId(choice) ?: return 0.0
        val src = d.sourceCardId ?: return 0.0
        if (src != "rowenna-gold" && src != "rowenna-handicap") return coinRadius(ctx, src)
        val hero = ctx.state.hero(id) ?: return 0.0
        // H1: gold lets them retrieve first. A discard that meets the swing is their card back.
        return if (discardHolds(ctx, hero.id)) -6.0 else 0.0
    }

    private fun coinRadius(ctx: HContext, src: String): Double {
        val radius = when (src) {
            "rowenna-red-1" -> 1
            "rowenna-red-2a", "rowenna-red-3a" -> 3
            else -> return 0.0
        }
        val at = ctx.own()?.at ?: return 0.0
        // H5: the coin is real only while an ally is in the card's radius.
        return if (BatchC.alliesWithin(ctx, at, radius).isNotEmpty()) 1.0 else 0.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (id == "rowenna-gold" && BatchC.meets(d, id)) {
            val cheaper = d.options.filterIsInstance<Choice.Card>().any { card ->
                card.cardId != id && BatchC.meets(d, card.cardId)
            }
            // H13: gold retrieves the red she blocked with. Spend the cheaper card that still holds.
            if (cheaper) return -2.5
        }
        return 0.0
    }

    private fun executeCard(ctx: HContext): String? {
        val h = ctx.own() ?: return null
        return when {
            BatchC.inHand(h, "rowenna-blue-3b") -> "rowenna-blue-3b"
            BatchC.inHand(h, "rowenna-blue-2b") -> "rowenna-blue-2b"
            else -> null
        }
    }

    private fun emptyAdjacent(ctx: HContext): String? {
        val at = ctx.own()?.at ?: return null
        return ctx.enemies().firstOrNull { enemy ->
            enemy.hand.isEmpty() && enemy.at?.let { ctx.distance(at, it) == 1 } == true
        }?.id
    }

    private fun emptyDiscard(hero: HeroState): Boolean = hero.cards.none { it.zone == Zone.DISCARD }

    private fun discardHolds(ctx: HContext, heroId: String): Boolean {
        val hero = ctx.state.hero(heroId) ?: return false
        val at = hero.at ?: return false
        val total = 4 + ctx.itemBonus(ctx.seat, Stat.ATTACK)
        val bonus = ctx.itemBonus(heroId, Stat.DEFENSE) + ctx.minionModifier(at, hero.team)
        return hero.cards.any { card ->
            card.zone == Zone.DISCARD && !card.faceDown &&
                (ctx.card(card.id).defense ?: -1) + bonus >= total
        }
    }
}
