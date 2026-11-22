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
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max

/**
 * Wuk (`heroes/wuk.md`): the trees are the range, the walls and the teleports. Tree Slam hits an
 * adjacent minion, or spends an adjacent tree to hit in range; the tree-Red hits an adjacent hero or a
 * unit in range beside a tree. Trample runs a straight line through enemies. Trees persist.
 */
object LegacyWukHeuristics : HeroHeuristics {
    override val heroId: String = "wuk"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 2,
        aggression = 0.3,
        laneWeight = 0.7,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(ctx, d, choice)
        DecisionKind.RESPAWN -> respawn(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val trees = BatchDSupport.trees(state)
        // A tree beside an enemy minion is a Red shot; the general book already counts the token itself.
        val useful = trees.count { tree ->
            state.minions.any { m ->
                m.team != self.team && m.at != null && HexGrid.distance(tree, m.at!!) == 1
            }
        }
        return BatchDSupport.sign(state, heroId, team) * minOf(useful, 3) * 2.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (TREE_RED.none { BatchDSupport.mayHold(ctx, heroId, it) }) return 0.0
        // H17: a hex beside his tree and in his range is the shot, whatever printed reach says.
        val nearTree = BatchDSupport.trees(ctx.state).any { HexGrid.distance(it, target) == 1 }
        return if (nearTree && ctx.distance(from, target) in 2..3) 1.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val card = ctx.card(id)
        val trees = BatchDSupport.trees(ctx.state)
        return when (id) {
            // H1/H2: plant until two trees stand near the fight; after that, attack.
            SILVER -> if (trees.count { ctx.distanceToBattleZone(it) <= 2 } < 2) 2.5 else -2.0

            // H3: an adjacent minion (no tree spent), or spend an adjacent tree to hit a unit in range.
            GOLD, HANDICAP -> {
                val tree = trees.any { ctx.distance(at, it) == 1 }
                val range = ctx.attackReach(ctx.seat, card)
                val heroOnly = id == HANDICAP
                val real = BatchDSupport.strikeOn(ctx, card, { uid, pos ->
                    val hero = ctx.heroBody(uid) != null
                    val d = ctx.distance(at, pos)
                    val adjacent = d == 1 && (if (heroOnly) hero else !hero)
                    adjacent || (tree && d in 1..range && (!heroOnly || hero))
                })
                BatchDSupport.strikeFix(ctx, card, real)
            }

            // H4: the tree-Red: a hero adjacent, or a unit in range adjacent to a tree.
            in TREE_RED -> {
                val range = ctx.attackReach(ctx.seat, card)
                val adjacentHero = { uid: String, pos: Hex ->
                    ctx.distance(at, pos) == 1 && ctx.heroBody(uid) != null
                }
                val besideTree = { _: String, pos: Hex ->
                    ctx.distance(at, pos) in 1..range && trees.any { ctx.distance(it, pos) == 1 }
                }
                val hits = BatchDSupport.strikeList(ctx, card) { uid, pos ->
                    adjacentHero(uid, pos) || besideTree(uid, pos)
                }
                var real = hits.firstOrNull()?.second ?: -0.5
                // "Choose one, or both, on different targets": both takes one target from each bullet.
                val first = BatchDSupport.strikeList(ctx, card, adjacentHero).toMap()
                val second = BatchDSupport.strikeList(ctx, card, besideTree).toMap()
                if (id == "wuk-red-3a" && first.isNotEmpty() && second.isNotEmpty()) {
                    real = maxOf(
                        real,
                        BatchASupport.bestDistinctPair(first, second, secondShare = 0.6),
                        BatchASupport.bestDistinctPair(second, first, secondShare = 0.6),
                    )
                }
                BatchDSupport.strikeFix(ctx, card, real)
            }

            // H5: Trample runs a straight line through enemy heroes (discard) and minions (defeat).
            in TRAMPLE -> BatchDSupport.outlookFix(ctx, card, trample(ctx, at, id))

            // H9: Dominance drops the enemy minions beside him from the round's minion battle.
            in DOMINANCE -> {
                val n = BatchDSupport.adjacentMinions(ctx.state, at, ctx.team)
                val count = if (id == "wuk-blue-3b") 2 else 1
                if (n == 0) -1.5 else 2.5 * minOf(n, count)
            }

            // H10: Gifts remove a tree to retrieve a card; with no tree in radius it does nothing.
            in GIFTS -> {
                val radius = card.radius.coerceAtLeast(1)
                val tree = trees.any { ctx.distance(at, it) <= radius }
                val discard = ctx.own()?.cards?.any { it.zone == Zone.DISCARD } == true
                when {
                    !tree -> -1.5
                    discard -> 0.5
                    else -> -1.0
                }
            }

            else -> 0.0
        }
    }

    /** The best straight Trample line: discards on heroes passed, defeats on minions passed. */
    private fun trample(ctx: HContext, at: Hex, id: String): Double {
        val n = (ctx.cardValue(ctx.seat, id, Stat.MOVEMENT) ?: ctx.card(id).movement).coerceAtLeast(0)
        val defeats = if (id == "wuk-red-3b") 2 else 1
        var best = 0.0
        for (dir in HexGrid.DIRECTIONS) {
            val passed = ArrayList<String>()
            for (k in 1..n) {
                val hex = Hex(at.q + dir.q * k, at.r + dir.r * k)
                if (k >= 2 && passed.isNotEmpty() && ctx.game.isEmpty(hex, ctx.seat)) {
                    best = max(best, lineWorth(ctx, passed, defeats))
                }
                ctx.game.occupant(hex)?.let { passed += it }
            }
        }
        return best
    }

    private fun lineWorth(ctx: HContext, passed: List<String>, defeats: Int): Double {
        var v = 0.0
        val minions = ArrayList<Double>()
        for (id in passed) {
            val m = ctx.state.minion(id)
            if (m != null) {
                if (m.team != ctx.team && !ctx.heavyImmune(id)) minions += ctx.minionWorth(id)
                continue
            }
            val hero = ctx.heroBody(id)?.let { ctx.state.hero(it) } ?: continue
            if (hero.team == ctx.team) continue
            v += if (hero.hand.isEmpty()) ctx.killWorth(hero.id) else ctx.discardWorth(hero)
        }
        v += minions.sortedDescending().take(defeats).sumOf { max(0.0, it) }
        return v
    }

    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val hex = choice.hex
        if (d.context.subjectId == null && d.prompt.contains("Tree token")) {
            // H1/H11: a tree beside an enemy minion, inside the fight, never on a minion spawn.
            var v = if (BatchDSupport.adjacentMinions(ctx.state, hex, ctx.team) > 0) 2.0 else 0.0
            v += if (ctx.inBattleZone(hex)) 1.0 else -0.5
            if (BatchDSupport.minionSpawn(ctx, hex)) v -= 3.0
            return BatchDSupport.unplace(ctx, d, hex) + v
        }
        val src = d.sourceCardId ?: return 0.0
        if (src !in TRAMPLE || d.context.subjectId != ctx.seat) return 0.0
        // H19: on the straight Trample move, the line he passes is the prize.
        val at = ctx.own()?.at ?: return 0.0
        val straight = d.options.all { o -> o !is Choice.Space || HexGrid.inStraightLine(at, o.hex) }
        if (!straight || !HexGrid.inStraightLine(at, hex)) return 0.0
        val k = ctx.distance(at, hex)
        val dq = (hex.q - at.q) / k
        val dr = (hex.r - at.r) / k
        val passed = (1 until k).mapNotNull { i -> ctx.game.occupant(Hex(at.q + dq * i, at.r + dr * i)) }
        return lineWorth(ctx, passed, if (src == "wuk-red-3b") 2 else 1)
    }

    /** H6: take the straight line when it crosses a prize. */
    private fun confirm(ctx: HContext, d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        if (src in TRAMPLE) {
            val at = ctx.own()?.at ?: return 0.0
            val line = trample(ctx, at, src)
            return if (choice is Choice.Yes) (if (line > 0.0) line else -0.5) else 0.0
        }
        return 0.0
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val trees = BatchDSupport.trees(ctx.state)
        if (trees.isEmpty()) return 0.0
        // H20: the trees are still there. Respawn toward them.
        return (3 - trees.minOf { ctx.distance(choice.hex, it) }).coerceAtLeast(0) * 0.5
    }

    private const val GOLD = "wuk-gold"
    private const val HANDICAP = "wuk-handicap"
    private const val SILVER = "wuk-silver"
    private val TREE_RED = setOf("wuk-red-1", "wuk-red-2a", "wuk-red-3a")
    private val TRAMPLE = setOf("wuk-red-2b", "wuk-red-3b")
    private val DOMINANCE = setOf("wuk-blue-2b", "wuk-blue-3b")
    private val GIFTS = setOf("wuk-blue-1", "wuk-blue-2a", "wuk-blue-3a")
}
