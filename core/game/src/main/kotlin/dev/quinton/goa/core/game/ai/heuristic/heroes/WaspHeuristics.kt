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
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Wasp (`heroes/wasp.md`): a cage and a shove, then the splash or the boomerang. Charged Boomerang
 * and Telekinesis cannot target a straight line from her, and adjacent is a straight line. Shock hits
 * a neighbour and discards a different hero in radius who is not adjacent.
 */
object WaspHeuristics : HeroHeuristics {
    override val heroId: String = "wasp"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 1,
        aggression = 0.45,
        laneWeight = 0.6,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_NUMBER -> number(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        var v = 0.0
        // The dome and the dagger's lock are this turn only, and both are public.
        if (BatchD.effect(state, heroId, setOf(SILVER))) v += 2.0
        if (BatchD.effect(state, heroId, setOf(GOLD, HANDICAP))) v += 1.0
        return BatchD.sign(state, heroId, team) * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (BOOMERANG.none { BatchD.mayHold(ctx, heroId, it) }) return 0.0
        val d = ctx.distance(from, target)
        // H18: the boomerang lands off her lines at 2–3. A straight neighbour is the melee Red's, already in reach.
        return if (d in 2..3 && !HexGrid.inStraightLine(from, target)) 1.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val card = ctx.card(id)
        return when (id) {
            // H4: the boomerang targets units in range that are not on one of her six lines.
            in BOOMERANG -> {
                val reach = ctx.attackReach(ctx.seat, card)
                val valid = { _: String, pos: Hex ->
                    ctx.distance(at, pos) in 2..reach && !HexGrid.inStraightLine(at, pos)
                }
                val hits = BatchD.strikeList(ctx, card, valid)
                var real = hits.firstOrNull()?.second ?: -0.5
                // Thunder Boomerang repeats on a different target after a hero.
                if (id == "wasp-red-3b" && hits.size >= 2) real += 0.6 * maxOf(0.0, hits[1].second)
                BatchD.strikeFix(ctx, card, real)
            }

            // H3: Shock hits a neighbour, then a hero in radius who is not adjacent discards.
            in SHOCK -> shock(ctx, at, id)

            // H5: Magnetic Dagger is an adjacent attack; the lock rides on it.
            GOLD, HANDICAP -> {
                val real = BatchD.strikeOn(ctx, card, { uid, pos ->
                    ctx.distance(at, pos) == 1 && (id == GOLD || ctx.heroBody(uid) != null)
                })
                BatchD.strikeFix(ctx, card, real)
            }

            // H1/H2: the dome splits the map; it matters when an enemy hero is inside and another outside.
            SILVER -> silver(ctx, at, card.radius.coerceAtLeast(2))

            // H8: Telekinesis pulls a unit off her lines to her side. Worth it on a minion she can then hit.
            in TELEKINESIS -> {
                val range = card.range.coerceAtLeast(1)
                val pull = ctx.state.minions.any { m ->
                    m.team != ctx.team && m.at != null && ctx.distance(at, m.at!!) in 2..range &&
                        !HexGrid.inStraightLine(at, m.at!!)
                }
                val attack = ctx.ownHand().any { it.attackCard }
                if (pull && attack) 1.0 else -1.0
            }

            // H6: Kinetic Repulse pushes adjacent enemies; a hero stopped by an obstacle discards.
            in REPULSE -> BatchD.outlookFix(ctx, card, repulse(ctx, at, id))

            else -> 0.0
        }
    }

    private fun shock(ctx: HContext, at: Hex, id: String): Double {
        val card = ctx.card(id)
        val real = BatchD.strikeOn(ctx, card, { _, pos -> ctx.distance(at, pos) == 1 })
        var v = BatchD.strikeFix(ctx, card, real)
        if (real <= 0.0) return v
        val radius = card.radius.coerceAtLeast(2)
        val splash = BatchD.enemyHeroesWithin(ctx, at, 2, radius).maxOfOrNull { e ->
            if (id == "wasp-red-3a" && e.hand.isEmpty()) ctx.killWorth(e.id) else ctx.discardWorth(e)
        }
        if (splash != null) v += splash
        return v
    }

    private fun silver(ctx: HContext, at: Hex, radius: Int): Double {
        val enemies = ctx.enemies().mapNotNull { it.at }
        val inside = enemies.count { ctx.distance(at, it) <= radius }
        val outside = enemies.size - inside
        // H2: with nobody to split, the dome is a wasted card.
        return if (inside > 0 && outside > 0) 1.5 else -1.0
    }

    private fun repulse(ctx: HContext, at: Hex, id: String): Double {
        val far = if (id == "wasp-blue-3b") 4 else 3
        var v = 0.0
        var any = false
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) != 1) continue
            any = true
            if (stopped(ctx, at, pos, far)) v += ctx.discardWorth(e)
        }
        if (!any && BatchD.adjacentMinions(ctx.state, at, ctx.team) == 0) return -1.5
        return v
    }

    /** The push line from [from] through [pos] meets an obstacle within [n] spaces. */
    private fun stopped(ctx: HContext, from: Hex, pos: Hex, n: Int): Boolean {
        val dq = pos.q - from.q
        val dr = pos.r - from.r
        for (k in 1..n) {
            val h = Hex(pos.q + dq * k, pos.r + dr * k)
            if (!ctx.game.isEmpty(h)) return true
        }
        return false
    }

    /** H13: Thunderous Repulse picks the distance that stops a hero on an obstacle, else the shorter. */
    private fun number(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "wasp-blue-3b" || choice !is Choice.Number) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        fun stops(n: Int) = ctx.enemies().any { e ->
            e.at?.let { ctx.distance(at, it) == 1 && stopped(ctx, at, it, n) } == true
        }
        return when {
            choice.value == 3 -> if (stops(3)) 1.0 else 0.3
            stops(choice.value) && !stops(3) -> 1.0
            else -> 0.0
        }
    }

    private const val GOLD = "wasp-gold"
    private const val HANDICAP = "wasp-handicap"
    private const val SILVER = "wasp-silver"
    private val BOOMERANG = setOf("wasp-red-2b", "wasp-red-3b")
    private val TELEKINESIS = setOf("wasp-green-2b", "wasp-green-3b")
    private val SHOCK = setOf("wasp-red-1", "wasp-red-2a", "wasp-red-3a")
    private val REPULSE = setOf("wasp-blue-2b", "wasp-blue-3b")
}
