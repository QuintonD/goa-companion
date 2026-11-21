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
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Takahide (heroes/takahide.md): a support whose three golds cycle. Float (Defense 8, Move 5) is the
 * block; Sting shoots the exact range-3 ring; Strike is Attack 7 adjacent. His greens and blues act on a
 * friendly hero, so he plays them only with an ally in reach (CCplusplus, BGG 3451226).
 */
object TakahideHeuristics : HeroHeuristics {
    override val heroId: String = "takahide"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 2,
        aggression = 0.45,
        laneWeight = 0.9,
        upgradeOrder = listOf("red", "green", "blue"),
        keepDefender = true,
    )

    private const val FLOAT = "takahide-gold"
    private const val STING = "takahide-gold-extra-1"
    private const val STRIKE = "takahide-gold-extra-2"
    private const val HANDICAP = "takahide-handicap"

    private val allyCards = setOf(
        "takahide-green-1",
        "takahide-green-2a",
        "takahide-green-2b",
        "takahide-green-3a",
        "takahide-green-3b",
        "takahide-blue-1",
        "takahide-blue-2a",
        "takahide-blue-2b",
        "takahide-blue-3a",
        "takahide-blue-3b",
    )

    private val stripBlues = setOf("takahide-blue-1", "takahide-blue-2a", "takahide-blue-3a")

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.DEFEND -> defend(d, choice)
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_CARD -> incoming(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        else -> 0.0
    }

    /** §6: Float faceup on the dashboard is the Defense 8 already spent. Which gold is in hand stays hidden. */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        return if (BatchC.spent(me, FLOAT)) BatchC.sign(state, heroId, team) * -2.0 else 0.0
    }

    /** Sting hits only the exact ring; inside it his gold threat is smaller than the general disk says. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        val ring = ctx.card(STING).range + ctx.itemBonus(heroId, Stat.RANGE)
        val pool = ctx.possibleCards(heroId)
        val stingOnly = pool.any { it.card.id == STING } && pool.none { it.card.id == STRIKE || it.card.id == FLOAT }
        return if (stingOnly && ctx.distance(at, target) in 2 until ring) -1.5 else 0.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        // H1: the handicap card's defense is his defeat.
        if (id == HANDICAP) return -40.0
        if (id != FLOAT || !BatchC.meets(d, id)) return 0.0
        // H2: Float meets almost everything; keep it when a cheaper card also holds.
        return if (BatchC.otherMeets(d, id, setOf(HANDICAP))) -3.0 else 1.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (id) {
            FLOAT -> float(ctx, me, at)
            STING -> if (onRing(ctx, at)) 1.0 else -2.5
            "takahide-silver" -> bushido(ctx, me, at)
            in allyCards -> allyInReach(ctx, at, id) + enemyInReach(ctx, id)
            else -> 0.0
        }
    }

    /** H3: Float moves 5 and cycles; spend it to cross the map (holding it as the block is the general book's job). */
    private fun float(ctx: HContext, me: HeroState, at: Hex): Double {
        val far = ctx.distanceToBattleZone(at) >= 3
        val deckGold = me.cards.any { it.id != FLOAT && it.id.startsWith("takahide-gold") && it.zone == Zone.DECK }
        return when {
            far && deckGold -> 3.0
            else -> 0.0
        }
    }

    /** H6: Bushido fixes a gold that has nothing to do this round, when Float is in the deck. */
    private fun bushido(ctx: HContext, me: HeroState, at: Hex): Double {
        val floatInDeck = me.card(FLOAT)?.zone == Zone.DECK
        val idleSting = BatchC.inHand(me, STING) && !onRing(ctx, at)
        val idleStrike = BatchC.inHand(me, STRIKE) && BatchC.enemiesWithin(ctx, at, 1).isEmpty() &&
            BatchC.minionsWithin(ctx, at, 1, friendly = false).none { BatchC.takeable(ctx, it) }
        return if (floatInDeck && (idleSting || idleStrike)) 2.5 else -1.0
    }

    /**
     * H15: the greens and blues act on a friendly hero in range or radius; with none in reach the primary is
     * dead. They are also his Movement 2, so the note bites only once he already stands in the fight.
     */
    private fun allyInReach(ctx: HContext, at: Hex, id: String): Double {
        val card = ctx.card(id)
        val reach = maxOf(card.range, card.radius)
        return if (ctx.inBattleZone(at) && BatchC.alliesWithin(ctx, at, reach).isEmpty()) -1.5 else 0.0
    }

    /** H11: the blues that strip a colour hit an enemy hero in radius only, never a minion. */
    private fun enemyInReach(ctx: HContext, id: String): Double =
        if (id in stripBlues) BatchC.heroOnlyDiscard(ctx, id, BatchC.radiusOf(ctx, id)) else 0.0

    private fun onRing(ctx: HContext, at: Hex): Boolean {
        val ring = ctx.card(STING).range + ctx.itemBonus(heroId, Stat.RANGE)
        return ctx.state.minions.any { m -> BatchC.takeable(ctx, m) && ctx.distance(at, m.at!!) == ring } ||
            ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) == ring } == true }
    }

    /** H4 / H5: after Sting or Strike (or Float's own swap), bring Float back for next round's block. */
    private fun incoming(ctx: HContext, d: Decision, choice: Choice): Double {
        if (!d.prompt.contains("gold card in your deck")) return 0.0
        val id = BatchC.cardId(choice) ?: return 0.0
        if (ctx.own()?.card(id)?.zone != Zone.DECK) return 0.0
        return if (id == FLOAT) 2.0 else 0.0
    }

    /** H13: on turn 4, without Float, do not end his own move next to an enemy hero. */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (ctx.turn != 4 || !BatchC.movesSelf(ctx, d)) return 0.0
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val me = ctx.own() ?: return 0.0
        if (BatchC.inHand(me, FLOAT)) return 0.0
        val beside = ctx.enemies().count { e -> e.at?.let { ctx.distance(hex, it) == 1 } == true }
        return -3.0 * beside
    }
}
