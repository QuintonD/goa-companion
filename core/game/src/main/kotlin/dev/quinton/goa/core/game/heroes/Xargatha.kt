package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.ActionQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.PassMode
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Xargatha the Changed (Red in the rulebook's first game). Card text quoted verbatim above each
 * registration (`catalog/heroes/xargatha.json`); `XargathaTest` follows it clause by clause with the FAQ.
 */
object XargathaHero : HeroModule {
    override val heroId: String = "xargatha"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Cleave: "Target a unit adjacent to you. After the attack: May repeat once on a different enemy
        // hero. (You may repeat even if the original target was a minion.)"
        r.card("xargatha-gold", Cleave(heroOnly = false))
        // Desperate Cleave (Handicap): "Target a hero adjacent to you. After the attack: May repeat once
        // on a different enemy hero."
        r.card("xargatha-handicap", Cleave(heroOnly = true))
        // Siren's Call: "Target an enemy unit not adjacent to you and in range; if able, move the target
        // up to 3 spaces to a space adjacent to you."
        r.card("xargatha-silver", SirensCall)
        // Threatening Slash / Deadly Swipe / Lethal Spin: "Target a unit adjacent to you. +1 (+2, +3)
        // Attack for each other enemy unit adjacent to you. (Do not count the target when calculating
        // the attack bonus.)"
        r.card("xargatha-red-1", Slash(perEnemy = 1))
        r.card("xargatha-red-2a", Slash(perEnemy = 2))
        r.card("xargatha-red-3a", Slash(perEnemy = 3))
        // Long Thrust: "Target a unit in range. +1 Range for each enemy unit adjacent to you." Rapid
        // Thrusts: "… May repeat once on a different enemy hero."
        r.card("xargatha-red-2b", Thrust(repeat = false))
        r.card("xargatha-red-3b", Thrust(repeat = true))
        // Charm: "Before or after movement, you may move an enemy ranged minion in radius up to 2 spaces."
        // Control: "… an enemy ranged or melee minion …". Dominate: "… an enemy minion in radius up to 2
        // spaces; ignore heavy minion immunity."
        r.card("xargatha-green-1", Charm(setOf(MinionType.RANGED), ignoreHeavy = false))
        r.card("xargatha-green-2a", Charm(setOf(MinionType.RANGED, MinionType.MELEE), ignoreHeavy = false))
        r.card("xargatha-green-3a", Charm(MinionType.entries.toSet(), ignoreHeavy = true))
        // Stone Gaze / Petrifying Stare / Turn into Statues: "Next turn: Enemy heroes in radius count as
        // both heroes and terrain, and cannot perform movement actions. (If you move, the radius “moves” with you.)"
        r.card("xargatha-blue-1", StoneGaze)
        r.card("xargatha-blue-2a", StoneGaze)
        r.card("xargatha-blue-3a", StoneGaze)
        // Constrict: "End of round: Defeat an enemy melee minion adjacent to you. (Before the end of
        // round minion battle.)" Final Embrace: "End of round: Defeat an enemy melee or ranged minion adjacent to you."
        r.card("xargatha-green-2b", Constrict(setOf(MinionType.MELEE)))
        r.card("xargatha-green-3b", Constrict(setOf(MinionType.MELEE, MinionType.RANGED)))
        // Fresh Converts: "If you are adjacent to an enemy minion, you may retrieve a discarded card."
        // Devoted Followers: "If you are adjacent to an enemy unit, you may retrieve a discarded card."
        r.card("xargatha-blue-2b", Converts(minionOnly = true))
        r.card("xargatha-blue-3b", Converts(minionOnly = false))
        // Metamorphosis (Ultimate): "Gain +1 Movement and +1 Initiative for each enemy unit adjacent to
        // you. You may move through obstacles."
        r.card("xargatha-purple-4", Metamorphosis)
    }

    /** Enemy units adjacent to [heroId] (presence: immune units count, p.18). */
    private fun Game.enemiesAdjacent(heroId: String, except: String? = null): Int {
        val at = posOf(heroId) ?: return 0
        return unitsAdjacentTo(at).count { it != except && isEnemy(heroId, it) }
    }

    private class Cleave(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) } else a.adjacentAttackTarget()
            t ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: May repeat once on a different enemy hero."
            a.repeat(differentTarget = true, filter = { a.g.isHero(it) }, prompt = "Repeat on a different enemy hero?")
        }
    }

    private object SirensCall : CardScript {
        override fun primary(a: ActionScope) {
            // "Target an enemy unit not adjacent to you and in range"
            val targets = a.unitsWithin(a.range).filter { id ->
                a.isEnemy(id) && a.g.distance(a.pos, a.g.posOf(id)!!) > 1 && a.canTarget(id)
            }
            if (targets.isEmpty()) a.fizzle("no enemy unit in range and not adjacent")
            val t = a.target(a.chooseUnit("Target an enemy unit not adjacent to you and in range", targets)!!)
            // "if able, move the target up to 3 spaces to a space adjacent to you." (if able: done when it can be)
            val me = a.pos
            val next: (Hex) -> Boolean = { h -> a.g.distance(h, me) == 1 }
            if (a.canMoveOther(t, 1, 3, endFilter = next)) a.moveOther(t, 1, 3, endFilter = next)
        }
    }

    private class Slash(private val perEnemy: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            a.attack(t, bonus = { perEnemy * a.g.enemiesAdjacent(a.heroId, except = t) })
        }
    }

    private class Thrust(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "+1 Range for each enemy unit adjacent to you" — card text, so not an item (p.16).
            val range = a.range + a.g.enemiesAdjacent(a.heroId)
            val t = a.rangedAttackTarget(rangeValue = range) ?: return
            val r = a.attack(t, valid = { a.inRange(it, a.range + a.g.enemiesAdjacent(a.heroId)) })
            if (!r.connected) return
            if (repeat) a.repeat(differentTarget = true, filter = { a.g.isHero(it) }, prompt = "Repeat on a different enemy hero?")
        }
    }

    private class Charm(private val types: Set<MinionType>, private val ignoreHeavy: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Before or after movement, you may move an enemy … minion in radius up to 2 spaces."
            if (!moveMinion(a, "before")) {
                a.movementAction()
                moveMinion(a, "after")
            } else {
                a.movementAction()
            }
        }

        private fun moveMinion(a: ActionScope, whenText: String): Boolean {
            val minions = a.unitsWithin(a.radius).filter { id ->
                val m = a.g.state.minion(id)
                m != null && m.type in types && a.isEnemy(id) && a.canTarget(id, ignoreHeavyImmunity = ignoreHeavy) && a.canMoveOther(id, 1, 2)
            }
            if (minions.isEmpty()) return false
            val m = a.chooseUnit("You may move an enemy minion in radius up to 2 spaces ($whenText your movement)", minions, optional = true)
                ?: return false
            a.moveOther(m, 0, 2)
            return true
        }
    }

    private object StoneGaze : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.NEXT_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            private fun petrified(g: Game, src: Src, id: String): Boolean {
                val owner = src.owner ?: return false
                return g.isHero(id) && g.isEnemy(owner, id) && g.inRadiusOf(src, id) && g.affectedBy(src, id)
            }

            override fun countsAsTerrain(g: Game, src: Src, hex: Hex): Boolean {
                val id = g.occupant(hex) ?: return false
                return petrified(g, src, id)
            }

            /** FAQ: no Movement action, so no Fast Travel through one either; place, swap and card-text moves still work. */
            override fun forbidsAction(g: Game, src: Src, q: ActionQuery): Boolean =
                (q.kind == ActionKind.MOVEMENT || q.kind == ActionKind.FAST_TRAVEL) && petrified(g, src, q.heroId)
        }
    }

    private class Constrict(private val types: Set<MinionType>) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.END_OF_ROUND)
        }

        override val behavior: Behavior = object : Behavior {
            override fun applyTimed(g: Game, src: Src) {
                val owner = src.owner ?: return
                val at = g.posOf(owner) ?: return
                val minions = g.unitsAdjacentTo(at).filter { id ->
                    val m = g.state.minion(id)
                    m != null && m.type in types && g.isEnemy(owner, id) && g.canTarget(id, owner, ActionKind.SKILL, src.cardId)
                }
                if (minions.isEmpty()) return
                val c = g.ask(owner, DecisionKind.CHOOSE_UNIT, "End of round: defeat an enemy minion adjacent to you", minions.map { Choice.Unit(it) }, src.cardId) as Choice.Unit
                g.defeatMinion(c.unitId, owner)
            }
        }
    }

    private class Converts(private val minionOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "If you are adjacent to an enemy minion (unit)" — presence, not a target (p.19).
            val adjacent = a.unitsWithin(1).any { a.isEnemy(it) && (!minionOnly || a.g.isMinion(it)) }
            if (adjacent) a.mayRetrieve()
        }
    }

    private object Metamorphosis : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun statDelta(g: Game, src: Src, q: StatQuery): Int {
                val owner = src.owner ?: return 0
                if (q.unitId != owner || (q.stat != Stat.MOVEMENT && q.stat != Stat.INITIATIVE)) return 0
                return g.enemiesAdjacent(owner)
            }

            /** "You may move through obstacles" (p.18 "Move Through": see Ignoring Obstacles) — her own moves. */
            override fun passMode(g: Game, src: Src, q: MoveQuery): PassMode? =
                if (q.unitId == src.owner && q.actorId == src.owner) PassMode.IGNORE_OBSTACLES else null
        }
    }
}
