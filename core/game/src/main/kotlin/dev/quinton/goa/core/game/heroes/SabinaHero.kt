package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isBasic
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.removeMinion
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Sabina the Commander. Card text quoted verbatim above each registration
 * (`catalog/heroes/sabina.json`); `SabinaTest` follows it clause by clause with the FAQ. Readings the
 * text leaves open are in `core/game/rulings/sabina.md`.
 */
object SabinaHero : HeroModule {
    override val heroId: String = "sabina"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Point Blank Shot: "Target a unit in range. After the attack: If the target is adjacent to you,
        // push it 1 space."
        r.card("sabina-gold", PushShot(heroOnly = false))
        // Bang (Handicap): "Target a hero adjacent to you. After the attack: Push the target 1 space."
        r.card("sabina-handicap", PushShot(heroOnly = true))
        // Back to Back: "Swap with a friendly minion in radius."
        r.card("sabina-silver", BackToBack)
        // Quickdraw / Gunslinger: "Target a unit in range. +3 Attack if the target played an attack card
        // this turn." Dead Shot: "… +4 Attack …"
        r.card("sabina-red-1", Quickdraw(bonus = 3))
        r.card("sabina-red-2a", Quickdraw(bonus = 3))
        r.card("sabina-red-3a", Quickdraw(bonus = 4))
        // Shootout: "Target a unit in range. After the attack: If the target was adjacent to you, remove up
        // to one enemy minion adjacent to you. (You gain no coins for removing a minion, only for defeating.)"
        // Bullet Hell: "… remove up to two enemy minions adjacent to you."
        r.card("sabina-red-2b", Shootout(remove = 1))
        r.card("sabina-red-3b", Shootout(remove = 2))
        // Troop Movement / Marching Orders: "Move a friendly minion in radius 1 space, to a space in radius.
        // May repeat once." Path to Victory: "… May repeat up to two times."
        r.card("sabina-green-1", TroopMovement(repeats = 1))
        r.card("sabina-green-2a", TroopMovement(repeats = 1))
        r.card("sabina-green-3a", TroopMovement(repeats = 2))
        // Close Support: "An enemy hero in radius adjacent to your friendly minion discards a card, if able."
        // Covering Fire: "… discards a card, or is defeated."
        r.card("sabina-green-2b", Support(orDefeated = false))
        r.card("sabina-green-3b", Support(orDefeated = true))
        // Listen Up / Roger Roger: "Swap two minions in radius." Ready and Waiting: "Swap two minions in
        // radius, ignoring heavy minion immunity."
        r.card("sabina-blue-1", SwapMinions(ignoreHeavy = false))
        r.card("sabina-blue-2a", SwapMinions(ignoreHeavy = false))
        r.card("sabina-blue-3a", SwapMinions(ignoreHeavy = true))
        // Steady Advance: "If there are two or more friendly minions in radius, you may retrieve a discarded
        // card; if you do, you may move 1 space." Unwavering Resolve: "…; if you do, move up to 2 spaces."
        r.card("sabina-blue-2b", Advance(moveUpTo2 = false))
        r.card("sabina-blue-3b", Advance(moveUpTo2 = true))
        // Big Sodding Gun (Ultimate): "Your basic attack has +2 Range and +2 Attack. If you push an enemy
        // hero, that hero discards a card, or is defeated."
        r.card("sabina-purple-4", BigSoddingGun)
    }

    /** Point Blank Shot (ranged, Range 1) and Bang. */
    private class PushShot(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.rangedAttackTarget()
            } ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: If the target is adjacent to you, push it 1 space." / "Push the target 1 space."
            if (a.g.posOf(t) == null) return
            if (!heroOnly && !a.isAdjacent(t)) return
            a.pushAway(t, 1)
        }
    }

    private object BackToBack : CardScript {
        override fun primary(a: ActionScope) {
            val minions = a.unitsWithin(a.radius).filter { id ->
                a.g.isMinion(id) && a.isFriendly(id) && a.canTarget(id) && a.g.canSwap(a.heroId, id, a.heroId)
            }
            if (minions.isEmpty()) a.fizzle("no friendly minion in radius")
            val m = a.target(a.chooseUnit("Swap with a friendly minion in radius", minions)!!)
            a.g.swap(a.heroId, m, a.heroId)
        }
    }

    private class Quickdraw(private val bonus: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rangedAttackTarget() ?: return
            // FAQ: "played an attack card" this turn, resolved or not, even if that card is gone (RULINGS 36).
            a.attack(t, bonus = { if (a.g.heroBodyOf(t) in a.g.state.turnRecord.playedAttack) bonus else 0 })
        }
    }

    private class Shootout(private val remove: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rangedAttackTarget() ?: return
            // "If the target was adjacent to you": where it stood when it was attacked.
            val wasAdjacent = a.isAdjacent(t)
            val r = a.attack(t)
            if (!r.connected || !wasAdjacent) return
            // "remove up to one (two) enemy minion(s) adjacent to you." One at a time: a removal may push the Lane.
            repeat(remove) { i ->
                val minions = a.unitsWithin(1).filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
                val m = a.chooseUnit(
                    "You may remove an enemy minion adjacent to you (${i + 1} of up to $remove)",
                    minions,
                    optional = true,
                ) ?: return
                a.target(m)
                a.g.removeMinion(m)
            }
        }
    }

    private class TroopMovement(private val repeats: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Move a friendly minion in radius 1 space, to a space in radius."
            val me = a.pos
            val inRadius: (Hex) -> Boolean = { h -> a.g.distance(me, h) in 1..a.radius }
            val minions = a.unitsWithin(a.radius).filter { id ->
                a.g.isMinion(id) && a.isFriendly(id) && a.canTarget(id) && a.canMoveOther(id, 1, endFilter = inRadius)
            }
            if (minions.isEmpty()) a.fizzle("no friendly minion in radius can move 1 space")
            val m = a.chooseUnit("Move a friendly minion in radius 1 space", minions)!!
            a.moveOther(m, 1, endFilter = inRadius)
            // "May repeat once (up to two times)." Any friendly minion, the same one included.
            a.repeat(times = repeats)
        }
    }

    private class Support(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "An enemy hero in radius adjacent to your friendly minion" — the minion's presence (p.18).
            val victims = a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) &&
                    a.g.unitsAdjacentTo(a.g.posOf(id)!!).any { a.g.isMinion(it) && a.isFriendly(it) }
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero in radius adjacent to a friendly minion")
            val v = a.chooseUnit("An enemy hero in radius adjacent to your friendly minion discards a card", victims)!!
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
        }
    }

    private class SwapMinions(private val ignoreHeavy: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Swap two minions in radius" — any two minions, friendly or enemy.
            val minions = a.unitsWithin(a.radius).filter { id ->
                a.g.isMinion(id) && a.canTarget(id, ignoreHeavyImmunity = ignoreHeavy) &&
                    a.g.canDisplace(id, dev.quinton.goa.core.game.effects.Displacement.SWAP, a.heroId)
            }
            if (minions.size < 2) a.fizzle("fewer than two minions in radius")
            val first = a.target(a.chooseUnit("Swap two minions in radius: choose the first", minions)!!)
            val second = a.target(a.chooseUnit("Choose the minion it swaps with", minions - first)!!)
            a.g.swap(first, second, a.heroId)
        }
    }

    private class Advance(private val moveUpTo2: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "If there are two or more friendly minions in radius" — presence (FAQ common: Steady Advance
            // checks presence, so immune minions count).
            val friends = a.unitsWithin(a.radius).count { a.g.isMinion(it) && a.isFriendly(it) }
            if (friends < 2) a.fizzle("fewer than two friendly minions in radius")
            // "you may retrieve a discarded card; if you do, you may move 1 space (move up to 2 spaces)."
            a.mayRetrieve() ?: return
            if (moveUpTo2) a.moveSelf(0, 2) else a.moveSelf(1, optional = true)
        }
    }

    private object BigSoddingGun : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            /** "Your basic attack has +2 Range and +2 Attack": the Attack and Range icons of a basic Attack card. */
            override fun statDelta(g: Game, src: Src, q: StatQuery): Int {
                val owner = src.owner ?: return 0
                if (g.heroBodyOf(q.unitId) != owner || (q.stat != Stat.RANGE && q.stat != Stat.ATTACK)) return 0
                val card = q.cardId ?: return 0
                return if (g.isBasic(card) && g.card(card).primary == PrimaryAction.ATTACK) 2 else 0
            }

            /** "If you push an enemy hero, that hero discards a card, or is defeated." */
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.Pushed || t.byHeroId != owner) return
                val hero = g.heroBodyOf(t.id) ?: return
                if (!g.isEnemy(owner, hero) || g.posOf(t.id) == null) return
                g.forceDiscard(hero, owner, orDefeated = true, sourceCardId = src.cardId)
            }
        }
    }
}
