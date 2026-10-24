package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isAttackCard
import dev.quinton.goa.core.game.engine.isObstacle
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsOnBoard

/**
 * Brynn the Seeker. Card text quoted verbatim above each registration (`catalog/heroes/brynn.json`);
 * `BrynnTest` follows it clause by clause with the FAQ. Rulings: `core/game/rulings/brynn.md`.
 *
 * Her signature check, "a hero who is adjacent to 3 or more obstacles", is [obstacleBound]: the hero's
 * neighbouring spaces that are obstacles (p.18: units, terrain, tokens, other miniatures; the card's
 * reminder "You, other heroes, minions, tokens, and terrain are obstacles"; the water around the map
 * is terrain, p.19). FAQ: checking a unit's status counts as targeting, so the hero must be one the
 * action could target (an immune hero never qualifies).
 */
object BrynnHero : HeroModule {
    override val heroId: String = "brynn"
    override val complete: Boolean = true

    const val ULTIMATE: String = "brynn-purple-4"

    override fun register(r: Registry) {
        // Familiar Ground: "Choose one — • Target a unit adjacent to you. • Target a hero in range who is
        // adjacent to 3 or more obstacles. (You, other heroes, minions, tokens, and terrain are obstacles.)"
        r.card("brynn-gold", FamiliarGround(heroOnlyAdjacent = false))
        // Home Turf (Handicap): "Choose one — • Target a hero adjacent to you. • Target a hero in range who
        // is adjacent to 3 or more obstacles. (…)"
        r.card("brynn-handicap", FamiliarGround(heroOnlyAdjacent = true))
        // Decoy: "Choose up to two times, on different targets — • Move an enemy minion in radius 1 space.
        // • Move an enemy hero in radius who is adjacent to 3 or more obstacles 1 space."
        r.card("brynn-silver", Decoy)
        // High Ground / Elevated Ambush: "Target a unit adjacent to you. If you target a hero adjacent to 3
        // or more obstacles, +2 attack." Peak Precision: "… +2 attack and After the attack: You may
        // retrieve this card."
        r.card("brynn-red-1", HighGround(retrieve = false))
        r.card("brynn-red-2a", HighGround(retrieve = false))
        r.card("brynn-red-3a", HighGround(retrieve = true))
        // Split Attack: "Target a unit in range. If you target a hero who is adjacent to 3 or more
        // obstacles, may repeat once on a different unit adjacent to you." Split Throw: "… may repeat once
        // on a different unit."
        r.card("brynn-red-2b", Split(repeatAdjacentOnly = true))
        r.card("brynn-red-3b", Split(repeatAdjacentOnly = false))
        // Bear Trap / Log Trap: "Choose one — • An enemy hero adjacent to you discards a card, if able.
        // • An enemy hero in radius who is adjacent to 3 or more obstacles discards a card, if able."
        // Deadfall Trap: "… discards a card, or is defeated." (both bullets)
        r.card("brynn-green-1", Trap(orDefeated = false))
        r.card("brynn-green-2a", Trap(orDefeated = false))
        r.card("brynn-green-3a", Trap(orDefeated = true))
        // Tread Lightly: "Swap with either a unit adjacent to you, or with an enemy hero in radius who is
        // adjacent to 3 or more obstacles." Cover Tracks: "… You may move 1 space." Hide Traces: "…
        // Move up to 2 spaces."
        r.card("brynn-blue-1", Tread(After.NONE))
        r.card("brynn-blue-2a", Tread(After.MAY_MOVE_1))
        r.card("brynn-blue-3a", Tread(After.UP_TO_2))
        // True Grit: "You may retrieve a discarded attack card. If an enemy hero in radius is adjacent to 3
        // or more obstacles, move up to 3 spaces." Die Hard: "… move up to 4 spaces."
        r.card("brynn-green-2b", Grit(move = 3))
        r.card("brynn-green-3b", Grit(move = 4))
        // Mountain Guide: "You may move a friendly unit, which is adjacent to you, up to 2 spaces. If an
        // enemy hero in radius is adjacent to 3 or more obstacles, move a different friendly unit in radius
        // up to 2 spaces." Expedition Leader: "… up to 3 spaces. … up to 3 spaces."
        r.card("brynn-blue-2b", Guide(move = 2))
        r.card("brynn-blue-3b", Guide(move = 3))
        // Over the Top (Ultimate): "While you are performing actions, all enemy heroes in play count as
        // being adjacent to 3 or more obstacles." Read by [obstacleBound] (only Brynn's cards ask).
        r.card(ULTIMATE, OverTheTop)
    }

    /**
     * Spaces next to [unitId] that are obstacles now (p.18), for the acting hero's action; spaces that count
     * as obstacles for [forHero] (Brynn) count too — designer, BGG 3482037, on Takahide's Spinning Blade
     * spaces that "count as obstacles for enemy units": "they count for enemy heroes = they count for enemy
     * heroes card effects". Brynn's own space is an obstacle as well (the card's reminder: "You, …").
     */
    fun Game.obstaclesAround(unitId: String, forHero: String? = null): Int {
        val at = posOf(unitId) ?: return 0
        return HexGrid.neighbors(at).count { isObstacle(it) || (forHero != null && isObstacle(it, forHero)) }
    }

    /**
     * "A hero (who is) adjacent to 3 or more obstacles", for this action: [id] is a hero on the board,
     * the action may target it (FAQ: a status check counts as targeting), and it has 3 or more obstacles
     * next to it — or Over the Top makes every enemy hero in play count as such while the performing
     * hero (Brynn) has it unlocked.
     */
    fun ActionScope.obstacleBound(id: String): Boolean {
        if (!g.isHero(id) || g.posOf(id) == null || !canTarget(id)) return false
        if (isEnemy(id) && overTheTop()) return true
        return g.obstaclesAround(id, forHero = heroId) >= OBSTACLES
    }

    /** p.16: an unlocked Ultimate is in effect while its hero is on the board; "while you are performing actions". */
    private fun ActionScope.overTheTop(): Boolean = hero.card(ULTIMATE)?.zone == Zone.ULTIMATE && hero.at != null

    private const val OBSTACLES = 3

    /** An enemy hero in radius adjacent to 3 or more obstacles (the condition of True Grit and Mountain Guide). */
    private fun ActionScope.enemyHeroBoundInRadius(): Boolean =
        unitsWithin(radius).any { g.isHero(it) && isEnemy(it) && obstacleBound(it) }

    private class FamiliarGround(private val heroOnlyAdjacent: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val adjacent: (String) -> Boolean = { a.isAdjacent(it) && (!heroOnlyAdjacent || a.g.isHero(it)) }
            val bound: (String) -> Boolean = { a.inRange(it) && a.obstacleBound(it) }
            fun any(valid: (String) -> Boolean) = a.g.unitsOnBoard().any {
                a.isEnemy(it) && a.canTarget(it) && valid(it)
            }
            val bullets = listOf(adjacent, bound)
            val labels = listOf(
                if (heroOnlyAdjacent) "Target a hero adjacent to you" else "Target a unit adjacent to you",
                "Target a hero in range who is adjacent to 3 or more obstacles",
            )
            // "Choose one —": a bullet with no target is not offered (RULINGS 38).
            val possible = bullets.indices.filter { any(bullets[it]) }
            if (possible.isEmpty()) a.fizzle("no target")
            val pick = if (possible.size == 1) possible.single() else a.chooseOption("Choose one", labels)!!
            val valid = bullets[pick]
            val t = a.attackTarget(labels[pick]) { valid(it) } ?: return
            // A Ranged card: its attack is ranged even on an adjacent target (RULINGS 19).
            a.attack(t, valid = valid)
        }
    }

    private object Decoy : CardScript {
        override fun primary(a: ActionScope) {
            val minion: (String) -> Boolean = { a.g.isMinion(it) }
            val hero: (String) -> Boolean = { a.obstacleBound(it) }
            val bullets = listOf(minion, hero)
            val labels = listOf(
                "Move an enemy minion in radius 1 space",
                "Move an enemy hero in radius who is adjacent to 3 or more obstacles 1 space",
            )

            // A unit Brynn may not move (Brogan's Bulwark) is not offered: the move of 1 space is mandatory.
            fun candidates(i: Int) = a.unitsWithin(a.radius).filter { id ->
                a.isEnemy(id) && id !in a.targets && a.canTarget(id) && bullets[i](id) &&
                    a.g.canDisplace(id, Displacement.MOVE, a.heroId) && a.canMoveOther(id, 1)
            }
            // "Choose up to two times, on different targets" (p.10: a single action; any order, a bullet
            // may be chosen again; "up to": none at all is allowed).
            val times = a.choiceLimit(2, upTo = true)
            for (n in 1..times) {
                val possible = bullets.indices.filter { candidates(it).isNotEmpty() }
                if (possible.isEmpty()) {
                    if (n == 1) a.fizzle("no enemy in radius can be moved")
                    return
                }
                val pick = a.chooseOption(
                    "Choose ($n of up to $times)",
                    possible.map { labels[it] },
                    optional = true,
                ) ?: return
                val i = possible[pick]
                val u = a.chooseUnit(labels[i], candidates(i))!!
                a.moveOther(u, 1)
            }
        }
    }

    private class HighGround(private val retrieve: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            // "If you target a hero adjacent to 3 or more obstacles" — read as the target is chosen.
            val bound = a.obstacleBound(t)
            val r = a.attack(t, bonus = { if (bound) 2 else 0 })
            // Peak Precision: "… and After the attack: You may retrieve this card." (RULINGS 18: not when
            // the attack did not connect.)
            if (retrieve && bound && r.connected && a.hero.card(a.cardId) != null &&
                a.confirm("Peak Precision: you may retrieve this card")
            ) {
                a.g.retrieve(a.heroId, a.cardId)
            }
        }
    }

    private class Split(private val repeatAdjacentOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rangedAttackTarget() ?: return
            val bound = a.obstacleBound(t)
            a.attack(t)
            // "If you target a hero who is adjacent to 3 or more obstacles, may repeat once on a different
            // unit (adjacent to you)."
            if (!bound) return
            if (repeatAdjacentOnly) {
                a.repeat(
                    differentTarget = true,
                    filter = { a.isAdjacent(it) },
                    prompt = "Repeat Split Attack on a different unit adjacent to you?",
                )
            } else {
                a.repeat(differentTarget = true, prompt = "Repeat Split Throw on a different unit?")
            }
        }
    }

    private class Trap(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val adjacent = a.unitsWithin(1).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            val bound = a.unitsWithin(a.radius).filter { a.isEnemy(it) && a.obstacleBound(it) }
            val groups = listOf(adjacent, bound)
            val what = if (orDefeated) "discards a card, or is defeated" else "discards a card, if able"
            val labels = listOf(
                "An enemy hero adjacent to you $what",
                "An enemy hero in radius who is adjacent to 3 or more obstacles $what",
            )
            val possible = groups.indices.filter { groups[it].isNotEmpty() }
            if (possible.isEmpty()) a.fizzle("no enemy hero to choose")
            val pick = if (possible.size == 1) possible.single() else a.chooseOption("Choose one", labels)!!
            val v = a.chooseUnit(labels[pick], groups[pick])!!
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
        }
    }

    private enum class After { NONE, MAY_MOVE_1, UP_TO_2 }

    private class Tread(private val after: After) : CardScript {
        override fun primary(a: ActionScope) {
            // "Swap with either a unit adjacent to you, or with an enemy hero in radius who is adjacent to 3
            // or more obstacles."
            val adjacent = a.unitsWithin(1).filter { a.canTarget(it) }
            val bound = a.unitsWithin(a.radius).filter { a.isEnemy(it) && a.obstacleBound(it) }
            val targets = (adjacent + bound).distinct().filter { a.g.canSwap(a.heroId, it, a.heroId) }
            if (targets.isEmpty()) a.fizzle("nothing to swap with")
            val t = a.target(
                a.chooseUnit(
                    "Swap with a unit adjacent to you, or an enemy hero in radius adjacent to 3 or more obstacles",
                    targets,
                )!!,
            )
            a.g.swap(a.heroId, t, a.heroId)
            when (after) {
                After.NONE -> Unit
                After.MAY_MOVE_1 -> a.moveSelf(1, optional = true)
                After.UP_TO_2 -> a.moveSelf(0, 2)
            }
        }
    }

    private class Grit(private val move: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "You may retrieve a discarded attack card." (A facedown card has no type, p.10.)
            a.mayRetrieve(filter = { a.g.isAttackCard(a.heroId, it) })
            // "If an enemy hero in radius is adjacent to 3 or more obstacles, move up to N spaces."
            if (a.enemyHeroBoundInRadius()) a.moveSelf(0, move)
        }
    }

    private class Guide(private val move: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "You may move a friendly unit, which is adjacent to you, up to N spaces."
            val adjacent = a.unitsWithin(1).filter {
                a.isFriendly(it) && a.canTarget(it) && a.canMoveOther(it, 1, move)
            }
            val first = a.chooseUnit(
                "You may move a friendly unit adjacent to you up to $move spaces",
                adjacent,
                optional = true,
            )
            if (first != null) a.moveOther(first, 0, move)
            // "If an enemy hero in radius is adjacent to 3 or more obstacles, move a different friendly unit
            // in radius up to N spaces."
            if (!a.enemyHeroBoundInRadius()) return
            val others = a.unitsWithin(a.radius).filter {
                it != first && a.isFriendly(it) && a.canTarget(it) && a.canMoveOther(it, 1, move)
            }
            if (others.isEmpty()) a.fizzle("no other friendly unit in radius can move")
            val u = a.chooseUnit("Move a different friendly unit in radius up to $move spaces", others)!!
            a.moveOther(u, 0, move)
        }
    }

    private object OverTheTop : CardScript {
        override val hasPrimary: Boolean get() = false
    }
}
