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
import dev.quinton.goa.core.game.heroes.UrsafarHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Ursafar (`heroes/ursafar.md`): a zoner who gets angry on a minion, then owns the hill. Every attack
 * is adjacent; Gold's step toward a hero exists only once he is enraged. A Gold or Red that connects,
 * Silver, and every Blue enrage him for the round; Greens do nothing while he is calm.
 */
object LegacyUrsafarHeuristics : HeroHeuristics {
    override val heroId: String = "ursafar"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.45,
        laneWeight = 0.6,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_CARD -> copy(ctx, d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val at = self.at ?: return 0.0
        val fury = self.cards.any { it.id == FURY && it.zone == Zone.ULTIMATE }
        val angry = fury || state.effects.any { e ->
            e.owner == heroId && e.key == UrsafarHero.ENRAGED && BatchDSupport.inForce(state, e)
        }
        val beside = BatchDSupport.adjacentMinions(state, at, self.team)
        // Enraged is the round's engine; calm beside a minion, the enrage is free next swing.
        val v = when {
            angry -> 4.0
            beside > 0 -> 1.5
            else -> 0.0
        }
        return BatchDSupport.sign(state, heroId, team) * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        // H18: once enraged, Gold's step reaches a hero two away. Calm, that step is not offered.
        if (!UrsafarHero.isEnraged(ctx.game, heroId)) return 0.0
        if (!BatchDSupport.mayHold(ctx, heroId, GOLD)) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        return if (ctx.distance(from, target) == 2) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val angry = UrsafarHero.isEnraged(ctx.game, ctx.seat)
        val besideMinion = BatchDSupport.adjacentMinions(ctx.state, at, ctx.team) > 0
        return when (id) {
            // H7: Sniff Out / Eyes / Apex discard a hero in range, and only while enraged.
            in SNIFF -> BatchDSupport.outlookFix(ctx, ctx.card(id), if (!angry) -1.5 else sniff(ctx, at, id))

            // H8: Instinct does nothing calm; enraged it retrieves (or performs) a discarded card.
            in INSTINCT -> when {
                !angry -> -1.5
                ctx.own()?.cards?.any { it.zone == Zone.DISCARD } == true -> 2.0
                else -> -1.0
            }

            // H3: calm, the roar is the enrage; enraged it copies an active card's primary.
            SILVER -> when {
                angry -> 1.0
                besideMinion -> 0.5
                else -> 1.5
            }

            // H2/H10: the one-space Blue is how a calm bear reaches a minion and gets angry.
            in BLUE -> when {
                angry || besideMinion -> 0.0
                BatchDSupport.adjacentEnemies(ctx.state, at, ctx.team) == 0 && near(ctx, at) -> 3.0
                else -> 1.0
            }

            // H1/H4: every swing is adjacent; Gold steps 1 toward a hero only when already enraged.
            in SWING -> swing(ctx, at, id, angry, besideMinion)

            else -> 0.0
        }
    }

    private fun swing(ctx: HContext, at: Hex, id: String, angry: Boolean, besideMinion: Boolean): Double {
        val card = ctx.card(id)
        val lunge = angry && (id == GOLD || id == HANDICAP)
        val heroOnly = id == HANDICAP
        val real = BatchDSupport.strikeOn(ctx, card, { uid, pos ->
            val d = ctx.distance(at, pos)
            val hero = ctx.heroBody(uid) != null
            (!heroOnly || hero) && (d == 1 || (lunge && hero && d == 2))
        })
        var v = BatchDSupport.strikeFix(ctx, card, real)
        // H1: calm and beside a minion, the swing also makes him angry for the rest of the round.
        if (!angry && besideMinion) v += 2.5
        return v
    }

    /** An enemy unit two away: Blue's one step puts him beside it. */
    private fun near(ctx: HContext, at: Hex): Boolean =
        ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) == 2 } == true } ||
            ctx.state.minions.any { m -> m.team != ctx.team && m.at?.let { ctx.distance(at, it) == 2 } == true }

    private fun sniff(ctx: HContext, at: Hex, id: String): Double {
        val range = ctx.card(id).range.coerceAtLeast(1)
        val heroes = BatchDSupport.enemyHeroesWithin(ctx, at, 1, range)
        if (heroes.isEmpty()) return -1.5
        return heroes.maxOf { e ->
            if (id == "ursafar-green-3a" && e.hand.isEmpty()) ctx.killWorth(e.id) else ctx.discardWorth(e)
        }
    }

    /** H16: Silver copies the card whose primary hits; never a card that cannot start (the engine filters). */
    private fun copy(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != SILVER || choice !is Choice.Card) return 0.0
        return if (ctx.card(choice.cardId).attackCard) 3.0 else 0.0
    }

    /** H15: Prey Abundance is the default kill build; Rip once levels and coins are the win. */
    private fun keep(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val lateGame = (ctx.own()?.level ?: 1) >= 4
        return when (choice.cardId) {
            "ursafar-red-2a", "ursafar-red-3a" -> if (lateGame) 0.5 else 1.5
            "ursafar-red-2b", "ursafar-red-3b" -> if (lateGame) 1.5 else 0.0
            "ursafar-green-2a", "ursafar-green-3a" -> 1.0
            else -> 0.0
        }
    }

    private const val GOLD = "ursafar-gold"
    private const val HANDICAP = "ursafar-handicap"
    private const val SILVER = "ursafar-silver"
    private const val FURY = "ursafar-purple-4"
    private val SNIFF = setOf("ursafar-green-1", "ursafar-green-2a", "ursafar-green-3a")
    private val INSTINCT = setOf("ursafar-green-2b", "ursafar-green-3b")
    private val BLUE = setOf(
        "ursafar-blue-1",
        "ursafar-blue-2a",
        "ursafar-blue-2b",
        "ursafar-blue-3a",
        "ursafar-blue-3b",
    )
    private val SWING = setOf(
        GOLD,
        HANDICAP,
        "ursafar-red-1",
        "ursafar-red-2a",
        "ursafar-red-2b",
        "ursafar-red-3a",
        "ursafar-red-3b",
    )
}
