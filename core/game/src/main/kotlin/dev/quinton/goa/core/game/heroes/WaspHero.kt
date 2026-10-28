package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.DisplaceQuery
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.SpaceQuery
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseUpTo
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.inStraightLine
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.objectsWithin
import dev.quinton.goa.core.game.effects.ownerPos
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
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Wasp the Warmaiden (Red in the rulebook's first game). Card text quoted verbatim above each
 * registration (`catalog/heroes/wasp.json`); `WaspTest` follows it clause by clause with the FAQ.
 */
object WaspHero : HeroModule {
    override val heroId: String = "wasp"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Magnetic Dagger: "Target a unit adjacent to you. After the attack: This turn: Enemy units in
        // radius cannot be swapped or placed by themselves or by enemy heroes."
        r.card("wasp-gold", Dagger(heroOnly = false))
        // Crackling Dagger (Handicap): "Target a hero adjacent to you. After the attack: This turn: …" (same)
        r.card("wasp-handicap", Dagger(heroOnly = true))
        // Static Barrier: "This turn: While an enemy hero outside of radius is performing an action,
        // spaces in radius count as obstacles. While an enemy hero in radius is performing an action,
        // spaces outside of radius count as obstacles."
        r.card("wasp-silver", StaticBarrier)
        // Shock / Electrocute: "Target a unit adjacent to you. After the attack: An enemy hero in radius
        // and not adjacent to you discards a card, if able." Electroblast: "… discards a card, or is defeated."
        r.card("wasp-red-1", Shock(orDefeated = false))
        r.card("wasp-red-2a", Shock(orDefeated = false))
        r.card("wasp-red-3a", Shock(orDefeated = true))
        // Charged Boomerang: "Target a unit in range and not in a straight line. (Units adjacent to you
        // are in a straight line from you.)" Thunder Boomerang: "… After the attack: If you targeted a
        // hero, may repeat once on a different target."
        r.card("wasp-red-2b", Boomerang(repeat = false))
        r.card("wasp-red-3b", Boomerang(repeat = true))
        // Stop Projectiles: "Block a ranged attack."
        r.card("wasp-green-1", Projectiles(discard = Discard.NONE))
        // Deflect Projectiles: "Block a ranged attack; if you do, an enemy hero in range, other than the
        // attacker, discards a card, if able." Reflect Projectiles: "… an enemy hero in range discards a card,
        // if able."
        r.card("wasp-green-2a", Projectiles(discard = Discard.NOT_ATTACKER))
        r.card("wasp-green-3a", Projectiles(discard = Discard.ANY))
        // Telekinesis: "Place a unit or a token in range, which is not in a straight line, into a space
        // adjacent to you." Mass Telekinesis: "… May repeat once."
        r.card("wasp-green-2b", Telekinesis(repeat = false))
        r.card("wasp-green-3b", Telekinesis(repeat = true))
        // Lift Up / Control Gravity: "Move a unit, or a token, in radius 1 space, without moving it away
        // from you or closer to you. May repeat once on the same target." Center of Mass: "… May repeat
        // up to two times on the same target."
        r.card("wasp-blue-1", Lift(repeats = 1))
        r.card("wasp-blue-2a", Lift(repeats = 1))
        r.card("wasp-blue-3a", Lift(repeats = 2))
        // Kinetic Repulse: "Push up to 2 enemy units adjacent to you 3 spaces; if a pushed hero is
        // stopped by an obstacle, that hero discards a card, if able." Kinetic Blast: "… 3 or 4 spaces; …"
        r.card("wasp-blue-2b", Repulse(listOf(3)))
        r.card("wasp-blue-3b", Repulse(listOf(3, 4)))
        // High Voltage (Ultimate): "Each time after you perform a basic skill, you may defeat an enemy
        // minion in radius; an enemy hero who was adjacent to that minion discards a card, if able."
        r.card("wasp-purple-4", HighVoltage)
    }

    private class Dagger(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") {
                    a.g.isHero(it)
                }
            } else {
                a.adjacentAttackTarget()
            }
            t ?: return
            val r = a.attack(t)
            if (!r.connected) return
            a.activeEffect(Timing.THIS_TURN)
        }

        /**
         * FAQ: "Wasp's dagger interferes with magical placement abilities – and only if they are
         * controlled by enemy heroes"; enemies outside may still place themselves into the radius.
         */
        override val behavior: Behavior = object : Behavior {
            override fun forbidsDisplacement(g: Game, src: Src, q: DisplaceQuery): Boolean {
                val owner = src.owner ?: return false
                if (q.how != Displacement.SWAP && q.how != Displacement.PLACE) return false
                if (!g.isUnit(q.unitId) || !g.isEnemy(owner, q.unitId) || !g.inRadiusOf(src, q.unitId)) return false
                val by = q.byHeroId ?: return false
                val byThemselves = g.heroBodyOf(q.unitId) == by
                val byEnemyHero = g.isHero(by) && g.isEnemy(owner, by)
                return (byThemselves || byEnemyHero) && g.affectedBy(src, q.unitId)
            }
        }
    }

    /**
     * FAQ: "Static Barrier divides the map into 2 regions … These restrictions only apply when an enemy
     * hero is taking the action."
     */
    private object StaticBarrier : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun countsAsObstacle(g: Game, src: Src, q: SpaceQuery): Boolean {
                val owner = src.owner ?: return false
                val actor = q.actorId ?: return false
                if (!g.isHero(actor) || !g.isEnemy(owner, actor)) return false
                val center = g.ownerPos(src) ?: return false
                val actorAt = g.posOf(actor) ?: return false
                if (!g.affectedBy(src, actor)) return false
                val r = g.radiusOf(src)
                val actorInside = g.distance(center, actorAt) <= r
                val spaceInside = g.distance(center, q.hex) <= r
                return actorInside != spaceInside
            }
        }
    }

    private class Shock(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: An enemy hero in radius and not adjacent to you discards a card, …"
            val victims = a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.g.distance(a.pos, a.g.posOf(id)!!) > 1 && a.canTarget(id)
            }
            val v = a.chooseUnit("An enemy hero in radius and not adjacent to you discards a card", victims) ?: return
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
        }
    }

    private class Boomerang(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t =
                a.rangedAttackTarget("Target a unit in range and not in a straight line") { !a.inStraightLine(it) }
                    ?: return
            val r = a.attack(t, valid = { !a.inStraightLine(it) })
            if (!r.connected) return
            // Thunder Boomerang: "After the attack: If you targeted a hero, may repeat once on a different target."
            if (repeat &&
                r.hero
            ) {
                a.repeat(differentTarget = true, prompt = "You targeted a hero: repeat on a different target?")
            }
        }
    }

    private enum class Discard { NONE, NOT_ATTACKER, ANY }

    private class Projectiles(private val discard: Discard) : CardScript {
        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            // "Block a ranged attack" — FAQ: discarding it against a non-ranged attack fails ("if you do").
            val blocked = d.attack.ranged
            d.block(blocked)
            if (!blocked || discard == Discard.NONE) return
            val me = d.pos ?: return
            val victims = d.g.unitsOnBoard().filter { id ->
                d.g.isHero(id) && d.g.isEnemy(d.heroId, id) &&
                    d.g.posOf(id)!!.let { d.g.distance(me, it) in 1..d.range } &&
                    (discard == Discard.ANY || d.g.heroBodyOf(id) != d.attack.attackerId) && d.canTarget(id)
            }
            val v = d.chooseUnit("An enemy hero in range discards a card, if able", victims) ?: return
            d.g.forceDiscard(v, d.heroId, orDefeated = false, sourceCardId = d.cardId)
        }
    }

    private class Telekinesis(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val adjacent = HexGrid.neighbors(a.pos).filter { a.g.isEmpty(it) }
            // A space can be an obstacle for one unit only (Takahide's Spinning Blade): a target needs
            // an adjacent space it can be placed into.
            val targets = a.objectsWithin(a.range).filter { id ->
                !a.inStraightLine(id) && a.canTarget(id) && a.g.canPlace(id, a.heroId) &&
                    adjacent.any { a.g.isEmpty(it, id) }
            }
            if (targets.isEmpty() || adjacent.isEmpty()) a.fizzle("nothing to place")
            val t = a.target(a.chooseObject("Place a unit or a token in range, not in a straight line", targets)!!)
            val h = a.chooseSpace(
                "Place ${a.g.name(t)} into a space adjacent to you",
                adjacent.filter {
                    a.g.isEmpty(it, t)
                },
            )!!
            a.g.place(t, h, a.heroId)
            if (repeat) a.repeat()
        }
    }

    private class Lift(private val repeats: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val me = a.pos
            fun keepsDistance(id: String): (dev.quinton.goa.core.model.board.Hex) -> Boolean {
                val d = a.g.distance(me, a.g.posOf(id)!!)
                return { h -> a.g.distance(me, h) == d }
            }
            val targets = a.objectsWithin(a.radius).filter { id ->
                a.canTarget(id) && a.g.canMove(MoveSpec(id, 1, 1, endFilter = keepsDistance(id)))
            }
            if (targets.isEmpty()) a.fizzle("nothing in radius can move 1 space")
            // FAQ: "the distance between you and the target must remain unchanged."
            val t = a.target(a.chooseObject("Move a unit, or a token, in radius 1 space", targets)!!)
            a.moveOther(t, 1, endFilter = keepsDistance(t))
            // "May repeat once (up to two times) on the same target."
            a.repeat(times = repeats, sameTarget = true, prompt = "Repeat on the same target?")
        }
    }

    private class Repulse(private val distances: List<Int>) : CardScript {
        override fun primary(a: ActionScope) {
            val enemies = a.unitsWithin(1).filter { a.isEnemy(it) && a.canTarget(it) }
            // "Push up to 2 enemy units adjacent to you N spaces" — p.18: "you choose the distance and
            // then push all those units that many spaces at the same time" (rays from you never cross).
            val picked = a.chooseUpTo(2, "Push an enemy unit adjacent to you", enemies)
            if (picked.isEmpty()) return
            val d = if (distances.size ==
                1
            ) {
                distances.single()
            } else {
                a.chooseNumber("Push them how many spaces?", distances)
            }
            val results = picked.map { it to a.pushAway(it, d) }
            // "if a pushed hero is stopped by an obstacle, that hero discards a card, if able." (FAQ)
            for ((id, res) in results) {
                if (res != null && res.stoppedByObstacle && a.g.isHero(id)) a.discardIfAble(id)
            }
        }
    }

    private object HighVoltage : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.AfterAction || t.heroId != owner || t.kind != ActionKind.SKILL || !t.basic ||
                    !t.primary
                ) {
                    return
                }
                val me = g.posOf(owner) ?: return
                val radius = g.cardValue(owner, src.cardId!!, Stat.RADIUS) ?: 0
                val minions = g.unitsOnBoard().filter { id ->
                    g.isMinion(id) && g.isEnemy(owner, id) && g.distance(me, g.posOf(id)!!) in 1..radius &&
                        g.canTarget(id, owner, ActionKind.SKILL, src.cardId)
                }
                if (minions.isEmpty()) return
                val opts = minions.map { Choice.Unit(it) as Choice } + Choice.Decline("Don't")
                val c = g.ask(
                    owner,
                    DecisionKind.CHOOSE_UNIT,
                    "High Voltage: you may defeat an enemy minion in radius",
                    opts,
                    src.cardId,
                )
                if (c !is Choice.Unit) return
                val wasAdjacent = g.unitsAdjacentTo(g.posOf(c.unitId)!!).filter { g.isHero(it) && g.isEnemy(owner, it) }
                g.defeatMinion(c.unitId, owner)
                val victims = wasAdjacent.filter {
                    g.posOf(it) != null &&
                        g.canTarget(it, owner, ActionKind.SKILL, src.cardId)
                }
                if (victims.isEmpty()) return
                val v = (
                    g.ask(
                        owner,
                        DecisionKind.CHOOSE_UNIT,
                        "An enemy hero who was adjacent to that minion discards a card",
                        victims.map {
                            Choice.Unit(it)
                        },
                        src.cardId,
                    ) as Choice.Unit
                    ).unitId
                g.forceDiscard(v, owner, orDefeated = false, sourceCardId = src.cardId)
            }
        }
    }
}
