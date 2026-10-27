package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.discard
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.performPrimary
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.game.engine.resolvedCards
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.model.board.Hex

/**
 * Garrus the Gladiator. Card text quoted verbatim above each registration (`catalog/heroes/garrus.json`);
 * `GarrusTest` follows it clause by clause with the FAQ. Readings the text leaves open are in
 * `core/game/rulings/garrus.md`.
 */
object GarrusHero : HeroModule {
    override val heroId: String = "garrus"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Angry Strike: "Target a unit adjacent to you; +1 Attack for every card in your discard."
        r.card("garrus-gold", Angry(heroOnly = false))
        // Angry Swipe (Handicap): "Target a hero adjacent to you; +1 Attack for every card in your discard."
        r.card("garrus-handicap", Angry(heroOnly = true))
        // Chilling Howl: "You may discard one of your resolved cards. This round: Enemy heroes in radius
        // cannot fast travel, or move more than 2 spaces with a movement action."
        r.card("garrus-silver", ChillingHowl)
        // Trace: "Choose one — • Before the attack: If you have one or more cards in the discard, you may move
        // 1 space. Target a hero adjacent to you. • Target a unit adjacent to you." Chase: "… move up to 2
        // spaces. …" Hunt Down: "… move up to 3 spaces. …"
        r.card("garrus-red-1", Hunt(move = 1, upTo = false))
        r.card("garrus-red-2a", Hunt(move = 2, upTo = true))
        r.card("garrus-red-3a", Hunt(move = 3, upTo = true))
        // Blunt Force: "Target a unit adjacent to you. After the attack: You may move 1 space to a space
        // adjacent to an enemy hero; if you do, push that hero 3 spaces, ignoring obstacles." Send Flying:
        // "… You may move up to 2 spaces to a space adjacent to an enemy hero; …"
        r.card("garrus-red-2b", Fling(maxMove = 1))
        r.card("garrus-red-3b", Fling(maxMove = 2))
        // Hold Ground / Make a Stand: "If there are at least two enemy heroes in radius, you may retrieve a
        // discarded card." Battle Ready: "… you may retrieve up to two discarded cards."
        r.card("garrus-green-1", HoldGround(retrieves = 1))
        r.card("garrus-green-2a", HoldGround(retrieves = 1))
        r.card("garrus-green-3a", HoldGround(retrieves = 2))
        // Light Pilum: "An enemy hero in range discards a card, if able. You may move 1 space." Heavy Pilum:
        // "An enemy hero in range discards a card, or is defeated. You may move up to 2 spaces."
        r.card("garrus-green-2b", Pilum(heavy = false))
        r.card("garrus-green-3b", Pilum(heavy = true))
        // Menace: "Move an enemy unit in range 1 space to a space farther away from you." Threaten: "…
        // May repeat once." Terrify: "… May repeat up to two times."
        r.card("garrus-blue-1", Shove(enemy = true, repeats = 0))
        r.card("garrus-blue-2a", Shove(enemy = true, repeats = 1))
        r.card("garrus-blue-3a", Shove(enemy = true, repeats = 2))
        // Form Up!: "Move a friendly unit in range 1 space to a space closer to you. May repeat once."
        // Testudo!: "… May repeat up to two times."
        r.card("garrus-blue-2b", Shove(enemy = false, repeats = 1))
        r.card("garrus-blue-3b", Shove(enemy = false, repeats = 2))
        // Battle Fury (Ultimate): "Each time after one of your resolved cards is discarded, you may perform
        // its primary action."
        r.card("garrus-purple-4", BattleFury)
    }

    private class Angry(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            // "+1 Attack for every card in your discard" (card text: items never apply to it, p.16).
            a.attack(t, bonus = { a.g.discardPile(a.heroId).size })
        }
    }

    private object ChillingHowl : CardScript {
        override fun primary(a: ActionScope) {
            // "You may discard one of your resolved cards." (Battle Fury may act on it at once.)
            val resolved = a.g.resolvedCards(a.heroId)
            if (resolved.isNotEmpty()) {
                a.chooseCard("You may discard one of your resolved cards", resolved, optional = true)
                    ?.let { a.g.discard(a.heroId, it) }
            }
            if (a.hero.at == null) return
            // "This round: Enemy heroes in radius cannot fast travel, or move more than 2 spaces with a movement
            // action."
            a.activeEffect(Timing.THIS_ROUND)
        }

        override val behavior: Behavior = object : Behavior {
            private fun applies(g: Game, src: Src, hero: String): Boolean {
                val owner = src.owner ?: return false
                return g.isHero(hero) && g.isEnemy(owner, hero) && g.inRadiusOf(src, hero) && g.affectedBy(src, hero)
            }

            /** FAQ: checked at the start of the movement action; a hero entering the radius is not slowed. */
            override fun movementCap(g: Game, src: Src, q: MoveQuery): Int? =
                if (q.movementAction && applies(g, src, q.unitId)) 2 else null

            override fun forbidsFastTravel(g: Game, src: Src, heroId: String): Boolean = applies(g, src, heroId)
        }
    }

    /** Trace / Chase / Hunt Down. */
    private class Hunt(private val move: Int, private val upTo: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val heroTarget: (String) -> Boolean = { id -> a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) }
            val hasDiscard = a.g.discardPile(a.heroId).isNotEmpty()
            // Bullet 1 can start when an enemy hero is adjacent now or (with a discard) after the move.
            val reachable = if (hasDiscard) a.g.reach(MoveSpec(a.heroId, 0, move)).keys else setOf(a.pos)
            val first = reachable.any { h -> a.g.unitsAdjacentTo(h).any { it != a.heroId && heroTarget(it) } }
            val second = a.unitsWithin(1).any { a.isEnemy(it) && a.canTarget(it) }
            val pick = when {
                first && second -> a.chooseOption(
                    "Choose one",
                    listOf(
                        if (upTo) {
                            "If you have a card in the discard, move up to $move spaces; target a hero adjacent to you"
                        } else {
                            "If you have a card in the discard, you may move 1 space; target a hero adjacent to you"
                        },
                        "Target a unit adjacent to you",
                    ),
                )

                first -> 0

                second -> 1

                hasDiscard -> a.fizzle("no unit adjacent to you, and no hero next to a space you can move to")

                else -> a.fizzle("no unit adjacent to you, and no card in your discard to move first")
            }
            if (pick == 1) {
                val t = a.adjacentAttackTarget() ?: return
                a.attack(t)
                return
            }
            // "Before the attack: If you have one or more cards in the discard, you may move 1 space (move up
            // to N spaces)."
            if (hasDiscard) {
                if (upTo) a.moveSelf(0, move) else a.moveSelf(1, optional = true)
            }
            // "Target a hero adjacent to you."
            val t = a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) } ?: return
            a.attack(t)
        }
    }

    /** Blunt Force / Send Flying. */
    private class Fling(private val maxMove: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "You may move 1 space (up to 2 spaces) to a space adjacent to an enemy hero" (FAQ: any enemy hero,
            // the attacked one or another; if no such end space, no move). [APP] "if you do" needs a move of 1+.
            val nextToEnemyHero: (Hex) -> Boolean = { h ->
                a.g.unitsAdjacentTo(h).any { it != a.heroId && a.g.isHero(it) && a.isEnemy(it) }
            }
            if (!a.g.reach(MoveSpec(a.heroId, 1, maxMove, endFilter = nextToEnemyHero)).keys.any { it != a.pos }) return
            val path = a.moveSelf(1, maxMove, optional = true, endFilter = nextToEnemyHero)
            if (path.isEmpty()) return
            // "if you do, push that hero 3 spaces, ignoring obstacles." FAQ: one of them if several.
            val heroes = a.unitsWithin(1).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            if (heroes.isEmpty()) a.fizzle("no enemy hero adjacent can be pushed")
            val h = a.chooseUnit("Push an enemy hero adjacent to you 3 spaces, ignoring obstacles", heroes)!!
            a.pushAway(h, 3, ignoreObstacles = true)
        }
    }

    private class HoldGround(private val retrieves: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "If there are at least two enemy heroes in radius" — presence (p.18).
            val heroes = a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.isEnemy(it) }
                .mapNotNull { a.g.heroBodyOf(it) }.distinct()
            if (heroes.size < 2) a.fizzle("fewer than two enemy heroes in radius")
            // "you may retrieve a discarded card" / "up to two discarded cards".
            repeat(retrieves) { a.mayRetrieve() ?: return }
        }
    }

    private class Pilum(private val heavy: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "An enemy hero in range discards a card, if able (or is defeated)." The first instruction:
            // without a hero in range the action cannot be performed (FAQ common).
            val heroes = a.unitsWithin(a.range).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            if (heroes.isEmpty()) a.fizzle("no enemy hero in range")
            val v = a.chooseUnit("An enemy hero in range discards a card", heroes)!!
            if (heavy) a.discardOrDefeat(v) else a.discardIfAble(v)
            // "You may move 1 space." / "You may move up to 2 spaces."
            if (heavy) a.moveSelf(0, 2) else a.moveSelf(1, optional = true)
        }
    }

    /** Menace / Threaten / Terrify (enemy, farther) and Form Up! / Testudo! (friendly, closer). */
    private class Shove(private val enemy: Boolean, private val repeats: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val me = a.pos
            fun endFor(id: String): (Hex) -> Boolean {
                val d0 = a.g.distance(me, a.g.posOf(id)!!)
                // p.17 "Closer" / "Farther": spaces counted on the shortest path, obstacles included.
                return if (enemy) { h -> a.g.distance(me, h) > d0 } else { h -> a.g.distance(me, h) < d0 }
            }
            val units = a.unitsWithin(a.range).filter { id ->
                (if (enemy) a.isEnemy(id) else a.isFriendly(id)) && a.canTarget(id) &&
                    a.canMoveOther(id, 1, endFilter = endFor(id))
            }
            val what = if (enemy) {
                "an enemy unit in range 1 space farther away"
            } else {
                "a friendly unit in range 1 space closer"
            }
            if (units.isEmpty()) a.fizzle("cannot move $what")
            val u = a.chooseUnit("Move $what", units)!!
            a.moveOther(u, 1, endFilter = endFor(u))
            // "May repeat once (up to two times)." Each repeat may pick any unit, the same one included.
            if (repeats > 0) a.repeat(times = repeats)
        }
    }

    private object BattleFury : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                // "Each time after one of your resolved cards is discarded"
                if (t !is Trigger.CardDiscarded || t.heroId != owner || !t.fromResolved) return
                if (g.posOf(owner) == null) return
                val kind = primaryKind(g.card(t.cardId))
                if (kind == ActionKind.DEFENSE || kind == ActionKind.NONE) return
                if (g.hero(owner).card(t.cardId)?.faceDown == true) return
                if (!g.primaryPossible(owner, t.cardId)) return
                // "you may perform its primary action."
                val c = g.ask(
                    owner,
                    DecisionKind.CONFIRM,
                    "Battle Fury: perform the primary action of ${g.cardName(t.cardId)}?",
                    listOf(Choice.Yes, Choice.Decline("Don't")),
                    src.cardId,
                    auto = false,
                )
                if (c is Choice.Yes) g.performPrimary(owner, t.cardId)
            }
        }
    }
}
