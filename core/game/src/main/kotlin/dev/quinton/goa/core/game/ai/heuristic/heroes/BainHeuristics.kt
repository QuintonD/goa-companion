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
import dev.quinton.goa.core.game.heroes.BainHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max

/**
 * Bain: a support who marks a hero (the Bounty marker costs that hero one more Life counter when
 * defeated) and shoots down clear straight lines. The marker on an enemy is the plan; on Bain it
 * turns his own cards off.
 */
object BainHeuristics : HeroHeuristics {
    override val heroId: String = "bain"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 2,
        aggression = 0.45,
        laneWeight = 0.60,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        DecisionKind.DEFEND -> defend(choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    /** H1 (eval): the marker on an enemy is an extra Life counter at stake; on Bain or his ally it is theirs. */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val bainTeam = state.hero(heroId)?.team ?: return 0.0
        val holder = BatchA.markerHolder(state, BainHero.BOUNTY)?.let { state.hero(it) } ?: return 0.0
        val v = if (holder.team != bainTeam) 4.0 else -4.0
        return BatchA.sign(state, team, heroId) * v
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in GOLD -> gold(ctx, at)
            "bain-silver" -> silver(ctx, at)
            in CROSSBOW -> crossbow(ctx, me, at, choice.cardId)
            in HUNTER -> hunter(ctx, me, at, choice.cardId)
            in CHANCE -> chance(ctx, at, choice.cardId)
            in BLOCK -> greenBlock(ctx, at)
            else -> 0.0
        }
    }

    /**
     * H1 / H18: gold hits an adjacent unit, then marks an enemy hero in radius 4. The marker is
     * worth most when an ally can reach that hero this round. Not when an enemy already wears it.
     */
    private fun gold(ctx: HContext, at: Hex): Double {
        if (!BatchA.besideEnemyUnit(ctx, at)) return 0.0
        val holder = BatchA.markerHolder(ctx.state, BainHero.BOUNTY)?.let { ctx.hero(it) }
        if (holder != null && holder.team != ctx.team) return 0.0
        val best = markTargets(ctx, at).maxOfOrNull { markValue(ctx, it) } ?: return 0.0
        return if (ctx.turn >= 4) best * 0.4 else best
    }

    private fun markTargets(ctx: HContext, at: Hex): List<HeroState> =
        BatchA.enemiesWithin(ctx, at, 4).filter { e -> e.markers.none { it.kind == BainHero.BOUNTY } }

    /** H2: the hero an ally can still reach, and the ultimate's discard on top. */
    private fun markValue(ctx: HContext, e: HeroState): Double {
        val pos = e.at ?: return 0.0
        var v = 3.0
        if (ctx.allies().any { a -> a.at?.let { ctx.distance(it, pos) <= 2 } == true }) v += 3.0
        if (BatchA.ultimateUnlocked(ctx.state, heroId)) v += BatchA.discardValue(ctx, e, orDefeat = false)
        return v
    }

    /**
     * H5: Get over here! pulls a unit on a clear line next to Bain. Worth it when the pulled enemy
     * hero lands where an ally (or Bain's next card) hits them; a minion pull is rarely worth Initiative 13.
     */
    private fun silver(ctx: HContext, at: Hex): Double {
        var hero = false
        var minion = false
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) in 2..4 && BatchA.clearOfObstacles(ctx, at, pos, heroId)) hero = true
        }
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team) continue
            if (ctx.distance(at, pos) in 2..4 && BatchA.clearOfObstacles(ctx, at, pos, heroId)) minion = true
        }
        val allyClose = ctx.allies().any { a -> a.at?.let { ctx.distance(it, at) <= 2 } == true }
        return when {
            hero && allyClose -> 3.5
            hero -> 0.5
            minion -> -2.0
            else -> -6.0
        }
    }

    /**
     * H7: the crossbow needs a straight line with no unit or terrain between (tokens do not count).
     * The general book saw every unit in range; this keeps only the clear lines.
     */
    private fun crossbow(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val reach = ctx.attackReach(me.id, card)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        var best = 0.0
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team || ctx.heavyImmune(m.id)) continue
            if (ctx.distance(at, pos) in 1..reach && BatchA.clearOfUnits(ctx, at, pos)) {
                best = max(best, ctx.minionWorth(m.id))
            }
        }
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) in 1..reach && BatchA.clearOfUnits(ctx, at, pos)) {
                best = max(best, BatchA.heroHit(ctx, me.id, e.id, total, true))
            }
        }
        return BatchA.correction(ctx, me.id, at, card, best)
    }

    /** H8: Hand Crossbow / Hunter-Seeker hit the marked hero in range, or an adjacent unit (both on 3b). */
    private fun hunter(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val reach = ctx.attackReach(me.id, card)
        val adjacent = BatchA.bestAdjacent(ctx, me.id, at, total)
        val marked = ctx.enemies().firstOrNull { e -> e.markers.any { it.kind == BainHero.BOUNTY } }
        val bounty = marked?.at?.takeIf { ctx.distance(at, it) in 1..reach }
            ?.let { BatchA.heroHit(ctx, me.id, marked.id, total, true) } ?: 0.0
        val accurate = if (cardId == "bain-red-3b") adjacent + bounty else max(adjacent, bounty)
        return BatchA.correction(ctx, me.id, at, card, accurate)
    }

    /**
     * H9: the guess discards a card when right and pays coins when wrong. Only an enemy with two
     * or more cards in radius can be asked; with none it fizzles.
     */
    private fun chance(ctx: HContext, at: Hex, cardId: String): Double {
        val radius = BatchA.radiusOf(ctx, heroId, ctx.card(cardId).radius)
        val victims = BatchA.enemiesWithin(ctx, at, radius).filter { it.hand.size >= 2 }
        if (victims.isEmpty()) return -4.0
        val coins = if (cardId == "bain-blue-1") 1.0 else 2.0
        val best = victims.maxOf { v ->
            val colours = guessable(ctx, v).size.coerceAtLeast(1)
            val p = 1.0 / colours
            p * ctx.discardWorth(v) + (1.0 - p) * coins
        }
        return best - 1.4
    }

    /** H4: the green block is the card that saves him while someone else wears the marker. */
    private fun greenBlock(ctx: HContext, at: Hex): Double {
        val holder = BatchA.markerHolder(ctx.state, BainHero.BOUNTY) ?: return 0.0
        if (holder == heroId) return 0.0
        return if (ctx.enemiesWhoCanAttack(at, ctx.team, thisTurnOnly = true).isNotEmpty()) -3.0 else -1.0
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Unit) return 0.0
        val src = d.sourceCardId ?: return 0.0
        // H2: the marker goes to the enemy an ally can reach; never to an ally (Side Quest allows it).
        if (src in GOLD && "Bounty" in d.prompt) {
            val h = ctx.hero(choice.unitId) ?: return 0.0
            return if (h.team == ctx.team) -10.0 else markValue(ctx, h)
        }
        // H12: Drinking Buddies hands a discard back to an ally, not to an enemy.
        if (src in BUDDIES) {
            val h = ctx.hero(choice.unitId) ?: return 0.0
            return if (h.team == ctx.team) 3.0 else -6.0
        }
        return 0.0
    }

    /**
     * H10: the options are the colours that could be in the victim's hand. Name the colour most
     * of the visible hand holds (strength 10 sees it); otherwise the tie order red, blue, gold,
     * green, silver. The victim picked the card, so a count is only a likelihood.
     */
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in CHANCE || choice !is Choice.Option) return 0.0
        val colour = CardColor.entries.firstOrNull { it.name.equals(choice.label, ignoreCase = true) } ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        var count = 0.0
        for (e in BatchA.enemiesWithin(ctx, at, 4)) {
            if (e.hand.size < 2) continue
            ctx.hand(e.id)?.let { hand -> count += hand.count { it.color == colour } }
        }
        return count - TIE_ORDER.indexOf(colour).coerceAtLeast(0) * 0.1
    }

    /**
     * H3: Close Call holds only while another hero wears the marker (the engine's block preview
     * says so); it then hands the marker to Bain, so a numbered defense is the better spend.
     */
    private fun defend(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            "bain-green-1" -> -4.0
            "bain-green-2a" -> -2.0
            else -> 0.0
        }
    }

    // H15: keep the straight-line crossbow and tuck Hand Crossbow (the Initiative item).
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "bain-red-2a" || choice.cardId == "bain-red-3a") 1.5 else 0.0
    }

    /** The colours a guess may name: cards in hand, and facedown cards out of it (the engine's rule). */
    private fun guessable(ctx: HContext, v: HeroState): Set<CardColor> {
        ctx.hand(v.id)?.let { return it.map { c -> c.color }.toSet() }
        return ctx.possibleCards(v.id).map { it.card.color }.toSet()
    }
}

private val GOLD = setOf("bain-gold", "bain-handicap")
private val CROSSBOW = setOf("bain-red-1", "bain-red-2a", "bain-red-3a")
private val HUNTER = setOf("bain-red-2b", "bain-red-3b")
private val CHANCE = setOf("bain-blue-1", "bain-blue-2a", "bain-blue-3a")
private val BUDDIES = setOf("bain-blue-2b", "bain-blue-3b")
private val BLOCK = setOf("bain-green-1", "bain-green-2a", "bain-green-3a")
private val TIE_ORDER = listOf(CardColor.RED, CardColor.BLUE, CardColor.GOLD, CardColor.GREEN, CardColor.SILVER)
