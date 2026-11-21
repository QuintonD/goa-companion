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
import dev.quinton.goa.core.game.heroes.gydion.Spellbook
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.min

private const val SILVER = "gydion-silver"
private const val REFILL_AT = 8
private const val THIN_BOOK = 4

/**
 * Gydion fills the spellbook, then casts. An empty book cannot start a cast (rulings/gydion.md 5).
 * The third Wish, once the Archwizard is unlocked, ends the game.
 */
object GydionHeuristics : HeroHeuristics {
    override val heroId: String = "gydion"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 3,
        aggression = 0.42,
        laneWeight = 0.40,
        upgradeOrder = listOf("gydion-green-2b", "gydion-blue-2a", "gydion-red-2a", "green", "blue", "red"),
        keepDefender = true,
    )

    override fun override(ctx: HContext, d: Decision): Choice? = when {
        // H2: silver with Wish ready is the alternate win, not a combat trick.
        d.kind == DecisionKind.PLAY_CARD && wishReady(ctx) ->
            d.options.filterIsInstance<Choice.Card>().firstOrNull { it.cardId == SILVER }

        // H4: the confirm is "cast Wish instead of adding". Yes is the third Wish.
        d.kind == DecisionKind.CONFIRM && d.sourceCardId == SILVER && wishReady(ctx) ->
            d.options.firstOrNull { it is Choice.Yes }

        else -> null
    }

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_CARD -> spell(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> target(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchBSupport.sign(state, team, heroId)
        val book = BatchBSupport.counter(state, heroId, Spellbook.COUNT_IN_BOOK)
        val wish = BatchBSupport.counter(state, heroId, Spellbook.COUNT_WISH)
        // evalTerms: an empty book makes every cast card dead (HEval prices a full book through the counter).
        var value = if (book == 0) -6.0 else 0.0
        // HEval already prices wishCasts at 10 each. This is the "one cast from winning" spike.
        if (wish >= 2 && book >= 2) value += 20.0
        val shield = BatchBSupport.effect(state, heroId, Spellbook.SHIELD)
        val immune = BatchBSupport.effect(state, heroId, Spellbook.INVULNERABILITY)
        value += when {
            shield && immune -> 12.0
            shield || immune -> 8.0
            else -> 0.0
        }
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val distance = ctx.distance(from, target)
        val removed = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_REMOVED)
        val exact = removed + ctx.itemBonus(heroId, Stat.RANGE)
        var threat = 0.0
        // Sunburst hits one exact distance, at attack equal to that distance. Every other distance is safe from it.
        if (exact >= 2 && distance == exact && inBook(ctx, Spellbook.SUNBURST)) {
            threat += min(8.0, exact * 1.5) * BatchBSupport.hold(ctx, heroId) { it.id == "gydion-red-3a" }
        }
        val radius = 3 + ctx.itemBonus(heroId, Stat.RADIUS)
        val standing = ctx.state.heroes.firstOrNull { it.at == target }
        val empty = standing != null && standing.team != ctx.hero(heroId)?.team && ctx.handCount(standing.id) == 0
        if (empty && distance <= radius && inBook(ctx, Spellbook.POWER_WORD_KILL)) {
            threat += 15.0 * BatchBSupport.hold(ctx, heroId) { it.id == "gydion-blue-3a" }
        }
        return threat
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        return deadCast(ctx, id) + prepare(ctx, id) + powerWordCard(ctx, id)
    }

    // H5: a cast whose spell is not in the book cannot start. Silver is the refill, not a cast.
    private fun deadCast(ctx: HContext, id: String): Double {
        val book = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_IN_BOOK)
        val cast = id != SILVER && id.startsWith("gydion-") && id != "gydion-purple-4"
        return if (book == 0 && cast) -20.0 else 0.0
    }

    // H1: turn 1 or 2 with an empty book, Prepare, unless a faster attack already reaches him.
    private fun prepare(ctx: HContext, id: String): Double {
        if (id != SILVER) return 0.0
        val book = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_IN_BOOK)
        if (book == 0) return if (ctx.turn <= 2 && !rushed(ctx)) 25.0 else 12.0
        // Common mistakes: a refill returns Shield and Invulnerability to the book and cancels them.
        val shielded = BatchBSupport.effect(ctx.state, heroId, Spellbook.SHIELD) ||
            BatchBSupport.effect(ctx.state, heroId, Spellbook.INVULNERABILITY)
        if (shielded) return -4.0
        val removed = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_REMOVED)
        // Refill once the book is thin; holding silver all game "to be safe" wastes it.
        return if (removed >= REFILL_AT) 6.0 else -2.0
    }

    // H8: Power Word Kill is a hero kill with no attack total. Play the enchantment over a minion.
    private fun powerWordCard(ctx: HContext, id: String): Double =
        if (id == "gydion-blue-3a" && inBook(ctx, Spellbook.POWER_WORD_KILL) && emptyHandInRadius(ctx)) 18.0 else 0.0

    // The engine asks CHOOSE_CARD ("Choose and cast one spell"), not CHOOSE_OPTION.
    private fun spell(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        if (!id.startsWith("gydion-spell-")) return 0.0
        return spellValue(ctx, id)
    }

    private fun spellValue(ctx: HContext, id: String): Double = when (id) {
        Spellbook.POWER_WORD_KILL -> if (emptyHandInRadius(ctx)) 18.0 else -4.0

        Spellbook.SHIELD -> if (basicIncoming(ctx)) 12.0 else 1.0

        Spellbook.INVULNERABILITY -> if (colouredIncoming(ctx)) 12.0 else 1.0

        Spellbook.ENERGY_DRAIN -> drain(ctx)

        Spellbook.SUNBURST -> sunburst(ctx)

        Spellbook.FIREBALL -> if (fireballTarget(ctx)) 8.0 else -3.0

        Spellbook.BURNING_HANDS -> if (meleeTarget(ctx)) 7.0 else 1.0

        Spellbook.EXPEDITIOUS_RETREAT, Spellbook.DIMENSION_DOOR -> if (outside(ctx)) 10.0 else 1.0

        Spellbook.MIDAS_TOUCH -> midas(ctx)

        Spellbook.FIND_FAMILIAR -> if (needsFuel(ctx)) 4.0 else 0.0

        Spellbook.CREATE_UNDEAD -> if (outside(ctx)) 0.0 else 4.0

        // H6: a plain attack spell is worth its best target, read like any attack card.
        Spellbook.MAGIC_MISSILE, Spellbook.SHOCKING_GRASP, Spellbook.VAMPIRIC_TOUCH -> attackSpell(ctx, id)

        else -> 0.0
    }

    private fun attackSpell(ctx: HContext, id: String): Double {
        val at = ctx.own()?.at ?: return 0.0
        return ctx.strikeFrom(ctx.seat, at, ctx.card(id)).coerceIn(-2.0, 12.0) * 0.5
    }

    // H7: an empty hand in radius is the kill. The spell's printed attack is 0, so the general score is flat.
    private fun target(ctx: HContext, d: Decision, choice: Choice): Double {
        val unit = choice as? Choice.Unit
        val source = d.sourceCardId
        val killing = source == Spellbook.POWER_WORD_KILL || source == "gydion-blue-3a"
        val hero = unit?.let { ctx.hero(it.unitId) }
        if (!killing || hero == null || hero.team == ctx.team || ctx.handCount(hero.id) > 0) return 0.0
        return ctx.killWorth(hero.id)
    }

    // H16: Door and Expeditious Retreat exist to enter the zone. Both are exact; the engine lists legal spaces.
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        val hex = (choice as? Choice.Space)?.hex ?: return 0.0
        val source = d.sourceCardId ?: return 0.0
        val travel = source == Spellbook.DIMENSION_DOOR || source == Spellbook.EXPEDITIOUS_RETREAT
        if (!travel || !outside(ctx)) return 0.0
        return if (ctx.inBattleZone(hex)) 10.0 else 0.0
    }

    // H3 (changed, see gydion.md "Implementation notes"): every Wish counts toward the three, so cast it
    // whenever the book still holds enough to cast from; refill only a thin book.
    private fun confirm(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != SILVER) return 0.0
        val book = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_IN_BOOK)
        val wishNow = book >= THIN_BOOK
        return when (choice) {
            is Choice.Yes -> if (wishNow) 8.0 else -4.0
            else -> if (wishNow) 0.0 else 4.0
        }
    }

    private fun wishReady(ctx: HContext): Boolean {
        val hero = ctx.own() ?: return false
        if (hero.id != heroId) return false
        val casts = hero.counters[Spellbook.COUNT_WISH] ?: 0
        if (casts < 2 || hero.card(Spellbook.ULTIMATE)?.zone != Zone.ULTIMATE) return false
        val book = hero.counters[Spellbook.COUNT_IN_BOOK] ?: 0
        return book >= 2 && hero.card(Spellbook.WISH)?.tag == Spellbook.SPELLBOOK
    }

    private fun rushed(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return ctx.threatsOn(at, ctx.team).any { threat ->
            threat.thisTurn && threat.card.initiative > 13 && threat.attackTotal >= 5
        }
    }

    /** Own book is his. An enemy book is the public complement of the faceup removed pile, after Prepare. */
    private fun inBook(ctx: HContext, spell: String): Boolean {
        val hero = ctx.state.hero(heroId) ?: return false
        val removed = hero.card(spell)?.tag == Spellbook.REMOVED
        if (ctx.seat == heroId) return hero.card(spell)?.tag == Spellbook.SPELLBOOK
        if (removed) return false
        val book = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_IN_BOOK)
        val gone = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_REMOVED)
        return book > 0 && book + gone >= Spellbook.ALL.size
    }

    private fun emptyHandInRadius(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        val radius = 3 + ctx.itemBonus(heroId, Stat.RADIUS)
        return ctx.enemies().any { enemy ->
            enemy.at?.let { ctx.distance(at, it) <= radius } == true && ctx.handCount(enemy.id) == 0
        }
    }

    private fun basicIncoming(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return ctx.threatsOn(at, ctx.team).any { BatchBSupport.basicAttack(it.card) }
    }

    private fun colouredIncoming(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return ctx.threatsOn(at, ctx.team).any { threat ->
            threat.card.color == CardColor.RED || threat.card.color == CardColor.BLUE ||
                threat.card.color == CardColor.GREEN
        }
    }

    private fun drain(ctx: HContext): Double = when {
        !BatchBSupport.lifeSpent(ctx) -> -3.0
        !enemyInRange(ctx, 3) -> -3.0
        ctx.deathsToLose(ctx.team) <= 2 -> 15.0
        else -> 6.0
    }

    private fun sunburst(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val removed = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_REMOVED)
        val exact = removed + ctx.itemBonus(heroId, Stat.RANGE)
        if (exact < 1) return -6.0
        val hit = ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(at, it) == exact } == true } ||
            ctx.state.minions.any { minion ->
                minion.team != ctx.team && minion.at?.let { ctx.distance(at, it) == exact } == true
            }
        return if (hit) min(12.0, 4.0 + exact) else -4.0
    }

    private fun fireballTarget(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return ctx.state.minions.any { minion ->
            val pos = minion.at ?: return@any false
            minion.team != ctx.team && ctx.distance(at, pos) in 2..3 && !friendlyBeside(ctx, pos)
        } || ctx.enemies().any { enemy ->
            val pos = enemy.at ?: return@any false
            ctx.distance(at, pos) in 2..3 && !friendlyBeside(ctx, pos)
        }
    }

    private fun friendlyBeside(ctx: HContext, at: Hex): Boolean =
        ctx.state.minions.any { minion ->
            minion.team == ctx.team && minion.at?.let { ctx.distance(it, at) == 1 } == true
        } || ctx.allies().any { ally -> ally.at?.let { ctx.distance(it, at) == 1 } == true }

    private fun meleeTarget(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return BatchBSupport.enemyMinionWithin(ctx, at, 1) || BatchBSupport.enemyHeroWithin(ctx, at, 1)
    }

    private fun outside(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return true
        return !ctx.inBattleZone(at)
    }

    private fun midas(ctx: HContext): Double {
        val coins = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_REMOVED) / 2
        return if (coins >= 2) coins.toDouble() else -2.0
    }

    private fun needsFuel(ctx: HContext): Boolean {
        val removed = BatchBSupport.counter(ctx.state, heroId, Spellbook.COUNT_REMOVED)
        return removed < 2 && (inBook(ctx, Spellbook.SUNBURST) || inBook(ctx, Spellbook.DIMENSION_DOOR))
    }

    private fun enemyInRange(ctx: HContext, range: Int): Boolean {
        val at = ctx.own()?.at ?: return false
        return BatchBSupport.enemyHeroWithin(ctx, at, range)
    }
}
