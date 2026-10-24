package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.effects.ActionQuery
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.chooseDistance
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.radiusOf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isEmptySpawnPoint
import dev.quinton.goa.core.game.engine.isSpawnPoint
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor

/**
 * Arien the Tidemaster (Blue in the rulebook's first game). Each card's verbatim text
 * (`catalog/heroes/arien.json`) is quoted above its registration; `ArienTest` follows it clause
 * by clause, with the hero's FAQ rulings.
 */
object ArienHero : HeroModule {
    override val heroId: String = "arien"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Noble Blade: "Target a unit adjacent to you. Before the attack: You may move another unit
        // that is adjacent to the target 1 space. (“Another unit” never includes you.)"
        r.card("arien-gold", BladeAttack(anyUnit = true))
        // Ceremonial Blade (Handicap): "Target a hero adjacent to you. Before the attack: You may move
        // a minion that is adjacent to the target 1 space."
        r.card("arien-handicap", BladeAttack(anyUnit = false))
        // Spell Break: "This turn: Enemy heroes in radius cannot perform skill actions, except on gold
        // cards. (Skill is an action type. Other action types are unaffected.)"
        r.card("arien-silver", SpellBreak)
        // Dangerous Current / Raging Stream / Violent Torrent: "Target a unit adjacent to you. Before
        // the attack: Up to 1 enemy hero in any of the 2 (3, 5) spaces in a straight line directly
        // behind the target discards a card, or is defeated." Violent Torrent: "May repeat once on a
        // different unit."
        r.card("arien-red-1", Current(behind = 2, repeat = false))
        r.card("arien-red-2a", Current(behind = 3, repeat = false))
        r.card("arien-red-3a", Current(behind = 5, repeat = true))
        // Rogue Wave / Tidal Blast: "Target a unit in range. After the attack: You may push an enemy
        // unit adjacent to you up to 2 (3) spaces."
        r.card("arien-red-2b", Wave(push = 2))
        r.card("arien-red-3b", Wave(push = 3))
        // Liquid Leap / Magical Current: "Place yourself into a space in range without a spawn point
        // and not adjacent to an empty spawn point." Stranger Tide: "Place yourself into a space in
        // range without a spawn point."
        r.card("arien-green-1", Leap(notNextToEmptySpawn = true))
        r.card("arien-green-2a", Leap(notNextToEmptySpawn = true))
        r.card("arien-green-3a", Leap(notNextToEmptySpawn = false))
        // Arcane Whirlpool: "Swap with an enemy minion in range. (Swap places with the target. This is
        // not movement.)" Ebb and Flow: "Swap with an enemy minion in range; if it was adjacent to
        // you, may repeat once."
        r.card("arien-green-2b", Whirlpool(repeatIfAdjacent = false))
        r.card("arien-green-3b", Whirlpool(repeatIfAdjacent = true))
        // Aspiring Duelist: "Ignore all minion defense modifiers. (This includes your minions, as well
        // as enemy minions.)"
        r.card("arien-blue-1", Duelist(timing = null))
        // Expert Duelist: "Ignore all minion defense modifiers. This turn: You are immune to attack
        // actions of all enemy heroes, except this attacker." Master Duelist: "… This round: …"
        r.card("arien-blue-2a", Duelist(timing = Timing.THIS_TURN))
        r.card("arien-blue-3a", Duelist(timing = Timing.THIS_ROUND))
        // Slippery Ground: "This turn: Enemy heroes adjacent to you cannot fast travel, or move more
        // than 1 space with a movement action." Deluge: "This turn: Enemy heroes in radius cannot fast
        // travel, or move more than 1 space with a movement action." (Radius 1, so items can widen it.)
        r.card("arien-blue-2b", Slippery(useRadius = false))
        r.card("arien-blue-3b", Slippery(useRadius = true))
        // Living Tsunami (Ultimate): "Once per turn, before performing an Attack action, you may move 1 space."
        r.card("arien-purple-4", LivingTsunami)
    }

    /** Noble Blade and Ceremonial Blade. */
    private class BladeAttack(private val anyUnit: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Target a unit (a hero) adjacent to you."
            val t = if (anyUnit) {
                a.adjacentAttackTarget()
            } else {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } ?: return
            // "Before the attack: You may move another unit (a minion) that is adjacent to the target 1 space."
            val tPos = a.g.posOf(t) ?: return
            val movable = a.g.unitsAdjacentTo(tPos).filter { id ->
                a.g.heroBodyOf(id) != a.heroId && (anyUnit || a.g.isMinion(id)) && a.canTarget(id) && a.canMoveOther(id, 1)
            }
            val what = if (anyUnit) "another unit" else "a minion"
            if (movable.isNotEmpty() && a.confirm("Move $what adjacent to the target 1 space?")) {
                val u = a.chooseUnit("Choose the unit to move", movable) ?: return
                a.moveOther(u, 1)
            }
            a.attack(t)
        }
    }

    private object SpellBreak : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun forbidsAction(g: Game, src: Src, q: ActionQuery): Boolean {
                val owner = src.owner ?: return false
                return q.kind == ActionKind.SKILL && q.cardColor != CardColor.GOLD &&
                    g.isHero(q.heroId) && g.isEnemy(owner, q.heroId) && g.inRadiusOf(src, q.heroId) && g.affectedBy(src, q.heroId)
            }
        }
    }

    private class Current(private val behind: Int, private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            // "Before the attack: Up to 1 enemy hero in any of the N spaces in a straight line directly
            // behind the target discards a card, or is defeated." (FAQ: no hero behind is needed.)
            val tPos = a.g.posOf(t)!!
            val dir = HexGrid.straightLineDirection(a.pos, tPos)
            if (dir != null) {
                val step = HexGrid.DIRECTIONS[dir]
                val spaces = (1..behind).map { Hex(tPos.q + step.q * it, tPos.r + step.r * it) }.toSet()
                val victims = a.g.unitsOnBoard().filter { id ->
                    a.g.isHero(id) && a.isEnemy(id) && a.g.posOf(id) in spaces && a.canTarget(id)
                }
                val v = a.chooseUnit("Up to 1 enemy hero behind the target discards a card, or is defeated", victims, optional = true)
                if (v != null) a.discardOrDefeat(v)
            }
            a.attack(t)
            // "May repeat once on a different unit."
            if (repeat) a.repeat(differentTarget = true, prompt = "Repeat Violent Torrent on a different unit?")
        }
    }

    private class Wave(private val push: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rangedAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: You may push an enemy unit adjacent to you up to N spaces."
            val adjacent = a.unitsWithin(1).filter { a.isEnemy(it) && a.canTarget(it) }
            val u = a.chooseUnit("You may push an enemy unit adjacent to you up to $push spaces", adjacent, optional = true) ?: return
            val d = a.chooseDistance((0..push).toList(), "Push ${a.g.name(u)} how many spaces?")
            a.pushAway(u, d)
        }
    }

    private class Leap(private val notNextToEmptySpawn: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Place yourself into a space in range without a spawn point [and not adjacent to an empty spawn point]."
            val spaces = a.g.within(a.pos, a.range).filter { h ->
                a.g.isEmpty(h, a.heroId) && !a.g.isSpawnPoint(h) &&
                    (!notNextToEmptySpawn || HexGrid.neighbors(h).none { n -> a.g.isEmptySpawnPoint(n) })
            }
            if (spaces.isEmpty() || !a.g.canPlace(a.heroId, a.heroId)) a.fizzle("no space to place Arien")
            val h = a.chooseSpace("Place yourself into a space in range", spaces)!!
            a.g.place(a.heroId, h, a.heroId)
        }
    }

    private class Whirlpool(private val repeatIfAdjacent: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Swap with an enemy minion in range."
            val minions = a.unitsWithin(a.range).filter { id ->
                a.g.isMinion(id) && a.isEnemy(id) && a.canTarget(id) && a.g.canSwap(a.heroId, id, a.heroId)
            }
            if (minions.isEmpty()) a.fizzle("no enemy minion in range")
            val m = a.target(a.chooseUnit("Swap with an enemy minion in range", minions)!!)
            val wasAdjacent = a.g.distance(a.pos, a.g.posOf(m)!!) == 1
            a.g.swap(a.heroId, m, a.heroId)
            // Ebb and Flow: "if it was adjacent to you, may repeat once."
            if (repeatIfAdjacent && wasAdjacent) a.repeat(prompt = "It was adjacent: repeat Ebb and Flow?")
        }
    }

    private class Duelist(private val timing: Timing?) : CardScript {
        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            // "Ignore all minion defense modifiers."
            d.ignoreMinionModifiers = true
            // "This turn (round): You are immune to attack actions of all enemy heroes, except this attacker."
            if (timing != null) d.activeEffect(timing, mapOf(ATTACKER to d.attack.attackerId))
        }

        override val behavior: Behavior? = if (timing == null) null else DuelistImmunity
    }

    private const val ATTACKER = "attacker"

    private object DuelistImmunity : Behavior {
        override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
            val owner = src.owner ?: return false
            if (g.heroBodyOf(q.targetId) != owner || q.kind != ActionKind.ATTACK) return false
            val actor = q.actorId ?: return false
            return g.isHero(actor) && g.isEnemy(owner, actor) && actor != src.effect?.data?.get(ATTACKER)
        }
    }

    private class Slippery(private val useRadius: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // Text before movement (p.11 "Unless it says otherwise, the card text comes into effect before movement").
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            private fun applies(g: Game, src: Src, hero: String): Boolean {
                val owner = src.owner ?: return false
                if (!g.isHero(hero) || !g.isEnemy(owner, hero)) return false
                val r = if (useRadius) g.radiusOf(src) else 1
                return g.inRadiusOf(src, hero, r) && g.affectedBy(src, hero)
            }

            /** FAQ: "restricts the movement, by a movement action"; checked at its start (p.11). */
            override fun movementCap(g: Game, src: Src, q: MoveQuery): Int? = if (q.movementAction && applies(g, src, q.unitId)) 1 else null

            override fun forbidsFastTravel(g: Game, src: Src, heroId: String): Boolean = applies(g, src, heroId)
        }
    }

    private object LivingTsunami : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.BeforeAction || t.heroId != owner || t.kind != ActionKind.ATTACK) return
                // "Once per turn": [APP] declining does not use it up.
                if (USED in g.state.turnRecord.notes) return
                if (g.posOf(owner) == null) return
                val spec = MoveSpec(owner, 1, 1)
                if (!g.canMove(spec)) return
                val moved = g.move(spec, owner, "Living Tsunami: you may move 1 space before attacking", optional = true, sourceCardId = src.cardId)
                if (moved.isNotEmpty()) g.state.turnRecord.notes += USED
            }
        }

        private const val USED = "arien-living-tsunami"
    }
}
