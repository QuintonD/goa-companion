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
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max

/**
 * Arien: a melee fighter. His primary red takes the adjacent unit and makes the enemy hero on the
 * straight line behind it discard, or be defeated. Spell Break is a threat to hold, not a turn-1 play.
 */
object ArienHeuristics : HeroHeuristics {
    override val heroId: String = "arien"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.60,
        laneWeight = 0.55,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(ctx, d, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        val at = me.at ?: return 0.0
        val unresolved = state.heroes.filter { e ->
            e.team != me.team && e.unresolved.isNotEmpty()
        }
        var v = 0.0
        // H2 (eval): Spell Break in force blanks the non-gold skills of whoever has still to act inside it.
        if (BatchA.effectLive(state, heroId, SILVER)) {
            v += unresolved.count { e -> e.at?.let { HexGrid.distance(at, it) <= 3 } == true } * 1.5
        }
        // H6 (eval): Slippery Ground / Deluge cap the movement of an adjacent enemy who has still to act.
        if (BatchA.effectLive(state, heroId, SLIPPERY)) {
            v += unresolved.count { e -> e.at?.let { HexGrid.distance(at, it) == 1 } == true } * 1.5
        }
        return BatchA.sign(state, team, heroId) * v
    }

    /** H1 (threat): the hex behind a unit he can hit is a discard his printed reach does not show. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(from, target) < 2) return 0.0
        val known = BatchA.played(ctx, heroId)
        val chance = if (known != null) {
            if (known.id in CURRENT) 1.0 else 0.0
        } else {
            BatchA.chancePlays(ctx, heroId) { it.id in CURRENT }
        }
        if (chance <= 0.0) return 0.0
        val n = ctx.possibleCards(heroId).filter { it.card.id in CURRENT }.maxOfOrNull { BEHIND.getValue(it.card.id) }
            ?: return 0.0
        val victimTeam = ctx.hero(heroId)?.team?.let { if (it == Team.RED) Team.BLUE else Team.RED } ?: return 0.0
        val onLine = unitsAround(ctx, from).any { (pos, team) ->
            team == victimTeam && behind(from, pos, target, n)
        }
        return if (onLine) chance * 3.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in CURRENT -> current(ctx, me, at, choice.cardId)
            in SILVER -> silver(ctx, at)
            in GOLD -> gold(ctx)
            in SLIPPERY -> slippery(ctx, at, choice.cardId)
            else -> 0.0
        }
    }

    /**
     * H1: the red takes the adjacent unit and the hero standing behind it discards (or, with no
     * cards, is defeated). The general book saw only the adjacent unit, so this replaces its estimate.
     * At Initiative 8 a hero with cards may step off the line first: half the discard.
     */
    private fun current(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val n = BEHIND.getValue(cardId)
        var best = 0.0
        for (hex in HexGrid.neighbors(at)) {
            val unit = targetValue(ctx, me.id, hex, total) ?: continue
            best = max(best, unit + lineValue(ctx, at, hex, n, stayFactor = true))
        }
        return BatchA.correction(ctx, me.id, at, card, best)
    }

    /** What attacking the enemy unit on [hex] is worth, or null when no enemy unit stands there. */
    private fun targetValue(ctx: HContext, me: String, hex: Hex, total: Int): Double? {
        ctx.enemies().firstOrNull { it.at == hex }?.let { return BatchA.heroHit(ctx, me, it.id, total, false) }
        val m = ctx.state.minions.firstOrNull { it.at == hex && it.team != ctx.team } ?: return null
        return if (ctx.heavyImmune(m.id)) null else ctx.minionWorth(m.id)
    }

    /** The best "discards a card, or is defeated" on a hero in the [n] spaces behind [target]. */
    private fun lineValue(ctx: HContext, at: Hex, target: Hex, n: Int, stayFactor: Boolean): Double {
        var best = 0.0
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (!behind(at, target, pos, n)) continue
            val v = BatchA.discardValue(ctx, e, orDefeat = true)
            val stays = if (!stayFactor || e.hand.isEmpty() || e.id in ctx.state.turnRecord.resolved) 1.0 else 0.5
            best = max(best, v * stays)
        }
        return best
    }

    /**
     * H2 / H3: Spell Break only when an enemy inside radius 3 is likely to resolve a non-gold skill
     * this turn. Initiative 13 is not a reason; holding it is the default.
     */
    private fun silver(ctx: HContext, at: Hex): Double {
        val radius = BatchA.radiusOf(ctx, heroId, 3)
        val near = BatchA.enemiesWithin(ctx, at, radius)
        if (near.isEmpty()) return -6.0
        val p = near.maxOf { e -> BatchA.chancePlays(ctx, e.id) { BatchA.nonGoldSkill(it) } }
        return 12.0 * p - 4.0
    }

    // H15: gold is the finisher, not the turn-1 walk while a Movement 4 card can farm.
    private fun gold(ctx: HContext): Double {
        if (ctx.turn != 1) return 0.0
        return if (ctx.ownHand().any { it.hasMovement && it.movement >= 4 }) -3.0 else 0.0
    }

    /** H6: the cap catches an enemy in it who has still to move this turn. */
    private fun slippery(ctx: HContext, at: Hex, cardId: String): Double {
        val radius = if (cardId == "arien-blue-3b") BatchA.radiusOf(ctx, heroId, 1) else 1
        var v = 0.0
        for (e in BatchA.enemiesWithin(ctx, at, radius)) {
            val p = BatchA.chancePlays(ctx, e.id) { it.hasMovement && it.movement >= 2 && !it.attackCard }
            v += 4.0 * p
        }
        return v
    }

    /**
     * H1 at resolution. The attack target (an adjacent unit) carries the line behind it; the
     * optional "hero behind the target" pick is free value, so it is always taken.
     */
    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in CURRENT || choice !is Choice.Unit) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        if ("behind the target" in d.prompt) {
            val e = ctx.hero(choice.unitId) ?: return 0.0
            return 2.0 + BatchA.discardValue(ctx, e, orDefeat = true)
        }
        val pos = ctx.hero(choice.unitId)?.at ?: ctx.state.minion(choice.unitId)?.at ?: return 0.0
        if (ctx.distance(at, pos) != 1) return 0.0
        return lineValue(ctx, at, pos, BEHIND.getValue(d.sourceCardId!!), stayFactor = false)
    }

    /** H7: the leap lands where the red's line is ready for the next turn. */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in LEAP || choice !is Choice.Space) return 0.0
        val red = ctx.ownHand().firstOrNull { it.id in CURRENT } ?: return 0.0
        val n = BEHIND.getValue(red.id)
        var best = 0.0
        for (hex in HexGrid.neighbors(choice.hex)) {
            if (ctx.state.minions.none { it.at == hex && it.team != ctx.team }) continue
            best = max(best, lineValue(ctx, choice.hex, hex, n, stayFactor = true))
        }
        return best * 0.6
    }

    /** H11: Violent Torrent repeats while another enemy unit is still adjacent. */
    private fun confirm(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "arien-red-3a") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val another = HexGrid.neighbors(at).any { hex ->
            ctx.enemies().any { it.at == hex } || ctx.state.minions.any { it.at == hex && it.team != ctx.team }
        }
        val yes = if (another) 6.0 else -1.0
        return if (choice is Choice.Yes) yes else 0.0
    }

    /**
     * H4: Expert / Master Duelist, when it holds, makes him immune to every other enemy hero's
     * attack this turn (this round). Worth it while others have still to attack him.
     */
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Card || choice.cardId !in DUELIST_IMMUNE) return 0.0
        val total = d.context.attackTotal ?: return 0.0
        val holds = (d.context.defensePreview[choice.cardId] ?: return 0.0) >= total
        if (!holds) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val attacker = d.context.attackerId?.let { ctx.heroBody(it) }
        val resolved = ctx.state.turnRecord.resolved
        val others = ctx.enemiesWhoCanAttack(at, ctx.team, thisTurnOnly = choice.cardId == "arien-blue-2a")
            .filter { it != attacker && (it !in resolved || choice.cardId == "arien-blue-3a") }
            .distinct()
        return others.size * 3.0
    }

    // H13: the primary branches (the line, the immunity). The tucked b-card is the item.
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId in PRIMARY_KEEP) 1.5 else 0.0
    }

    /** [target] lies 1..[n] spaces straight on from [from] through [through]. */
    private fun behind(from: Hex, through: Hex, target: Hex, n: Int): Boolean {
        val dir = HexGrid.straightLineDirection(from, through) ?: return false
        for (k in 1..n) if (BatchA.step(through, dir, k) == target) return true
        return false
    }

    private fun unitsAround(ctx: HContext, at: Hex): List<Pair<Hex, Team>> {
        val out = ArrayList<Pair<Hex, Team>>()
        for (h in ctx.state.heroes) {
            val pos = h.at ?: continue
            if (ctx.distance(at, pos) == 1) out += pos to h.team
        }
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (ctx.distance(at, pos) == 1) out += pos to m.team
        }
        return out
    }
}

private val GOLD = setOf("arien-gold", "arien-handicap")
private val SILVER = setOf("arien-silver")
private val CURRENT = setOf("arien-red-1", "arien-red-2a", "arien-red-3a")
private val DUELIST_IMMUNE = setOf("arien-blue-2a", "arien-blue-3a")
private val SLIPPERY = setOf("arien-blue-2b", "arien-blue-3b")
private val LEAP = setOf("arien-green-1", "arien-green-2a", "arien-green-3a")
private val PRIMARY_KEEP = setOf("arien-red-2a", "arien-red-3a", "arien-blue-2a", "arien-blue-3a")
private val BEHIND = mapOf("arien-red-1" to 2, "arien-red-2a" to 3, "arien-red-3a" to 5)
