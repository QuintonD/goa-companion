package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.noTargetReason
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.game.engine.setPos
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Hanu the Trickster. Card text quoted verbatim above each registration (`catalog/heroes/hanu.json`);
 * `HanuTest` follows it clause by clause with the FAQ. Rulings: `core/game/rulings/hanu.md`.
 */
object HanuHero : HeroModule {
    override val heroId: String = "hanu"
    override val complete: Boolean = true

    /** Behaviour keys of Unexpected Journey's two effects (data [HERO]: the swapped enemy hero). */
    const val IMMUNE: String = "hanu.immune"
    const val SWAP_BACK: String = "hanu.swapback"
    const val SWAP_BACK_MOVE: String = "hanu.swapback.move"
    const val HERO: String = "hero"

    /** [dev.quinton.goa.core.game.engine.GameState.vars] prefix: card id → its stamp when Hurry up! set it to 11. */
    private const val HURRY = "hanu.hurry."

    /** Hanu's [dev.quinton.goa.core.game.engine.HeroState.vars]: heroes whose next action he chooses (Ultimate). */
    private const val CONTROLS = "hanu.controls"

    private const val ULTIMATE = "hanu-purple-4"
    private const val HURRY_INITIATIVE = 11

    override fun register(r: Registry) {
        // Fight and Flight: "Target a unit adjacent to you. If the target is not defeated, After the attack:
        // If able, move 3 spaces in a straight line."
        r.card("hanu-gold", Flight(heroOnly = false))
        // Fight or Flight (Handicap): "Target a hero adjacent to you. If the target is not defeated, After the
        // attack: If able, move 3 spaces in a straight line."
        r.card("hanu-handicap", Flight(heroOnly = true))
        // Hurry up!: "Set the printed Initiative value of an unresolved card of a hero in range to 11, until it
        // is resolved, or otherwise changes state. (This may change the initiative order and tie breakers.)"
        r.card("hanu-silver", HurryUp)
        r.heroRule(heroId, HurryRule)
        // Helping Hand / Even the Odds: "Choose one — • Target a unit adjacent to you. • Target a hero in range,
        // adjacent to your friendly hero." Outnumber: "… • Target a minion in range, adjacent to your friendly
        // hero."
        r.card("hanu-red-1", Helping(Far.HERO, both = false))
        r.card("hanu-red-2a", Helping(Far.HERO, both = false))
        r.card("hanu-red-2b", Helping(Far.MINION, both = false))
        // Trusted Sidekick: "Choose one, or both, in any order — • Target a unit adjacent to you. • Target a hero
        // in range, adjacent to your friendly hero and not adjacent to you." Pile On: "… • Target a minion in
        // range, adjacent to your friendly hero."
        r.card("hanu-red-3a", Helping(Far.HERO_NOT_ADJACENT, both = true))
        r.card("hanu-red-3b", Helping(Far.MINION, both = true))
        // Monkey Trick / Monkey Twist: "Swap two friendly units in radius." Monkey Business: "… You may move 1
        // space."
        r.card("hanu-green-1", Monkey(moveAfter = false))
        r.card("hanu-green-2a", Monkey(moveAfter = false))
        r.card("hanu-green-3a", Monkey(moveAfter = true))
        // Hear Nothing: "Swap with an enemy hero in radius." See Nothing: "… You may move 1 space."
        r.card("hanu-green-2b", SwapEnemy(moveAfter = false))
        r.card("hanu-green-3b", SwapEnemy(moveAfter = true))
        // Unexpected Journey / There and Back Again: "Swap with an enemy hero in radius. This turn: That hero is
        // immune. End of turn: Swap with that hero, regardless of radius and immunity." Safe Travels: "… End of
        // turn: Swap with that hero, regardless of radius and immunity. You may move 1 space."
        r.card("hanu-blue-1", Journey(moveAfter = false))
        r.card("hanu-blue-2a", Journey(moveAfter = false))
        r.card("hanu-blue-3a", Journey(moveAfter = true))
        r.behavior(IMMUNE, Immune)
        r.behavior(SWAP_BACK, SwapBack(moveAfter = false))
        r.behavior(SWAP_BACK_MOVE, SwapBack(moveAfter = true))
        // This Way!: "A friendly hero in radius chooses a distance of 1 or 2; move both of you that number of
        // spaces in the same direction of your choice. (Both must be moved the full distance, or neither one
        // moves.)" That Way!: "… a distance of 1, 2 or 3; …"
        r.card("hanu-blue-2b", ThisWay(maxDistance = 2))
        r.card("hanu-blue-3b", ThisWay(maxDistance = 3))
        // The Ultimate Trick (Ultimate): "You choose the next action, and how it is performed, for a hero you
        // target with the “Hurry Up!”."
        r.card(ULTIMATE, UltimateTrick)
    }

    private class Flight(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            val r = a.attack(t)
            // "If the target is not defeated, After the attack: If able, move 3 spaces in a straight line."
            if (!r.connected || r.defeated) return
            if (a.g.canMove(MoveSpec(a.heroId, 3, 3, straightLine = true))) a.moveSelf(3, straightLine = true)
        }
    }

    // ---- Hurry up! and the Ultimate ------------------------------------------------------------

    private object HurryUp : CardScript {
        override fun primary(a: ActionScope) {
            // "an unresolved card of a hero in range" — any hero (friend or enemy) but Hanu (range counts 1..N).
            val heroes = a.unitsWithin(a.range).filter { id ->
                val body = a.g.heroBodyOf(id)
                body != null && body != a.heroId && a.g.hero(body).unresolved.isNotEmpty() && a.canTarget(id)
            }
            if (heroes.isEmpty()) a.fizzle("no hero in range has an unresolved card")
            val t = a.target(a.chooseUnit("Set the Initiative of a hero's unresolved card to 11", heroes)!!)
            val victim = a.g.heroBodyOf(t)!!
            val cards = a.g.hero(victim).unresolved.map { it.id }
            val c = if (cards.size == 1) cards.single() else a.chooseCard("Which unresolved card?", cards)!!
            // Not an Active effect (no bold keyword, p.17): it lasts as the text says, even if Hanu is defeated.
            a.g.state.vars.keys.removeAll { it.startsWith(HURRY) }
            a.g.state.vars[HURRY + c] = a.g.hero(victim).card(c)!!.stamp.toString()
            a.g.touch()
            a.g.note("${a.g.cardName(c)} of ${a.g.heroName(victim)} now has a printed Initiative of 11.")
            // The Ultimate Trick: remember the hero; Hanu chooses its next action.
            if (a.hero.card(ULTIMATE)?.zone == Zone.ULTIMATE) {
                val set = controlled(a.g, a.heroId) + victim
                a.hero.vars[CONTROLS] = set.joinToString(",")
            }
        }
    }

    /** Hurry up!'s value change: in force while that card stays in the same state (unresolved). */
    private object HurryRule : Behavior {
        override fun printedValue(g: Game, src: Src, q: StatQuery): Int? {
            if (q.stat != Stat.INITIATIVE) return null
            val card = q.cardId ?: return null
            val stamp = g.state.vars[HURRY + card] ?: return null
            val inst = g.heroOrNull(q.unitId)?.card(card) ?: return null
            return if (inst.zone == Zone.PLAYED && inst.stamp.toString() == stamp) HURRY_INITIATIVE else null
        }
    }

    private fun controlled(g: Game, hanu: String): Set<String> =
        g.heroOrNull(hanu)?.vars?.get(CONTROLS)?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun release(g: Game, hanu: String, heroId: String) {
        val left = controlled(g, hanu) - heroId
        val vars = g.hero(hanu).vars
        if (left.isEmpty()) vars.remove(CONTROLS) else vars[CONTROLS] = left.joinToString(",")
    }

    /**
     * [APP] "The next action" of the hero is the action it performs when it next resolves a card (p.7
     * "Perform One Action"), from its choice of action to the end of that action; Hanu makes every choice
     * that hero would make meanwhile. Respawning (before the action, p.7), Defense actions (a response
     * to an attack, p.14) and choices made while another hero's text is being applied (a defender's
     * Defense text making the attacker discard) are not it.
     */
    private object UltimateTrick : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun controls(g: Game, src: Src, heroId: String): String? {
                val hanu = src.owner ?: return null
                if (heroId == hanu || heroId !in controlled(g, hanu)) return null
                if (g.state.acting != heroId || g.posOf(heroId) == null) return null
                // Before the action starts no one is acting; during it the hero is the innermost actor.
                val actor = g.actor
                if (actor != null && actor != heroId) return null
                return hanu
            }

            override fun on(g: Game, src: Src, t: Trigger) {
                val hanu = src.owner ?: return
                when (t) {
                    is Trigger.AfterAction -> {
                        if (t.heroId !in controlled(g, hanu) || t.repeat || g.state.acting != t.heroId) return
                        // Only the outermost action (an action performed inside it is part of it).
                        if (g.currentAction()?.heroId == t.heroId) return
                        release(g, hanu, t.heroId)
                    }

                    is Trigger.HeroDefeated -> {
                        if (t.heroId in controlled(g, hanu) && g.state.acting == t.heroId) release(g, hanu, t.heroId)
                    }

                    else -> Unit
                }
            }
        }
    }

    // ---- attacks -------------------------------------------------------------------------------

    private enum class Far { HERO, HERO_NOT_ADJACENT, MINION }

    private class Helping(private val far: Far, private val both: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val labels = listOf(
                "Target a unit adjacent to you",
                when (far) {
                    Far.HERO -> "Target a hero in range, adjacent to your friendly hero"

                    Far.HERO_NOT_ADJACENT ->
                        "Target a hero in range, adjacent to your friendly hero and not adjacent to you"

                    Far.MINION -> "Target a minion in range, adjacent to your friendly hero"
                },
            )
            val bullets: List<(String) -> Boolean> = listOf({ a.isAdjacent(it) }, { farValid(a, it) })
            fun possible(i: Int) = a.g.unitsOnBoard().any { a.isEnemy(it) && a.canTarget(it) && bullets[i](it) }
            val open = bullets.indices.filter { possible(it) }
            if (open.isEmpty()) a.fizzle(noTargetReason(labels))
            // "Choose one —" (RULINGS 38): only a bullet with a target is offered, and one is not asked.
            val first = if (open.size ==
                1
            ) {
                open.single()
            } else {
                a.chooseOption(if (both) "Choose one, or both" else "Choose one", labels)!!
            }
            attackBullet(a, labels[first], bullets[first])
            if (!both) return
            // "Choose one, or both, in any order": the other bullet may follow (p.10: a single action).
            val second = 1 - first
            if (!possible(second)) return
            if (!a.confirm("Also: ${labels[second]}?", decline = "Only one")) return
            attackBullet(a, labels[second], bullets[second])
        }

        private fun attackBullet(a: ActionScope, label: String, valid: (String) -> Boolean) {
            val t = a.attackTarget(label) { valid(it) } ?: return
            // A Ranged card: its attacks are ranged, even the adjacent one (RULINGS 19).
            a.attack(t, valid = valid)
        }

        /** "in range, adjacent to your friendly hero" — the friendly hero's presence is checked, not targeted. */
        private fun farValid(a: ActionScope, id: String): Boolean {
            if (!a.inRange(id)) return false
            val kindOk = when (far) {
                Far.HERO, Far.HERO_NOT_ADJACENT -> a.g.isHero(id)
                Far.MINION -> a.g.isMinion(id)
            }
            if (!kindOk || (far == Far.HERO_NOT_ADJACENT && a.isAdjacent(id))) return false
            val p = a.g.posOf(id) ?: return false
            return a.g.unitsAdjacentTo(p).any { a.g.isHero(it) && a.isFriendly(it) }
        }
    }

    // ---- swaps ---------------------------------------------------------------------------------

    private class Monkey(private val moveAfter: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Swap two friendly units in radius." ("Friendly" never includes you, p.17.)
            val friends = a.unitsWithin(a.radius).filter { a.isFriendly(it) && a.canTarget(it) }
            val firsts = friends.filter { f -> friends.any { o -> o != f && a.g.canSwap(f, o, a.heroId) } }
            if (firsts.isEmpty()) a.fizzle("no two friendly units in radius to swap")
            val x = a.target(a.chooseUnit("Swap two friendly units in radius: the first", firsts)!!)
            val y = a.target(
                a.chooseUnit(
                    "Swap ${a.g.name(x)} with",
                    friends.filter { it != x && a.g.canSwap(x, it, a.heroId) },
                )!!,
            )
            a.g.swap(x, y, a.heroId)
            if (moveAfter) a.moveSelf(1, optional = true)
        }
    }

    private fun ActionScope.swapEnemyHero(): String {
        val heroes = unitsWithin(radius).filter {
            g.isHero(it) && isEnemy(it) && canTarget(it) && g.canSwap(heroId, it, heroId)
        }
        if (heroes.isEmpty()) fizzle("no enemy hero in radius to swap with")
        val t = target(chooseUnit("Swap with an enemy hero in radius", heroes)!!)
        g.swap(heroId, t, heroId)
        return t
    }

    private class SwapEnemy(private val moveAfter: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            a.swapEnemyHero()
            if (moveAfter) a.moveSelf(1, optional = true)
        }
    }

    private class Journey(private val moveAfter: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.swapEnemyHero()
            // "This turn: That hero is immune." "End of turn: Swap with that hero, …"
            a.activeEffect(Timing.THIS_TURN, key = IMMUNE, data = mapOf(HERO to t))
            a.activeEffect(
                Timing.END_OF_TURN,
                key = if (moveAfter) SWAP_BACK_MOVE else SWAP_BACK,
                data = mapOf(HERO to t),
            )
        }
    }

    /**
     * "That hero is immune" (p.18: it cannot be targeted and is not affected by actions, except its own).
     * The common FAQ: "a unit that is immune during “this turn” is still immune when end of turn resolves".
     */
    private object Immune : Behavior {
        override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
            val who = src.effect?.data?.get(HERO) ?: return false
            return g.heroBodyOf(q.targetId) == who
        }
    }

    private class SwapBack(private val moveAfter: Boolean) : Behavior {
        override fun applyTimed(g: Game, src: Src) {
            val hanu = src.owner ?: return
            val other = src.effect?.data?.get(HERO) ?: return
            if (g.posOf(hanu) == null || g.posOf(other) == null) return
            g.asActor(hanu) {
                // "regardless of radius and immunity" — not of effects that forbid the swap (RULINGS 17).
                if (g.canSwap(hanu, other, hanu)) g.swap(hanu, other, hanu)
                if (moveAfter) {
                    val spec = MoveSpec(hanu, 1, 1)
                    if (g.canMove(
                            spec,
                        )
                    ) {
                        g.move(
                            spec,
                            hanu,
                            "Safe Travels: you may move 1 space",
                            optional = true,
                            sourceCardId = src.cardId,
                        )
                    }
                }
            }
        }
    }

    // ---- This Way! / That Way! -----------------------------------------------------------------

    private class ThisWay(private val maxDistance: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val friends = a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.isFriendly(it) && a.canTarget(it) }
            if (friends.isEmpty()) a.fizzle("no friendly hero in radius")
            val f = a.target(a.chooseUnit("A friendly hero in radius chooses a distance", friends)!!)
            // "A friendly hero in radius chooses a distance of 1 or 2 (or 3)".
            val n = a.chooseNumber(
                "Choose a distance for you and Hanu",
                (1..maxDistance).toList(),
                chooser = a.g.heroBodyOf(f)!!,
            )
            // "move both of you that number of spaces in the same direction of your choice" (p.17 "Direction":
            // a straight line in one of the six directions). "Both must be moved the full distance, or neither."
            val plans = HexGrid.DIRECTIONS.indices.mapNotNull { dir -> plan(a, f, dir, n)?.let { dir to it } }
            if (plans.isEmpty()) {
                a.g.note("Neither ${a.g.heroName(a.heroId)} nor ${a.g.name(f)} can move $n spaces in one direction.")
                return
            }
            val ends = plans.associate { (dir, _) -> step(a.pos, dir, n) to dir }
            val end = a.chooseSpace("Choose the direction (where you will end)", ends.keys)!!
            val dir = ends.getValue(end)
            val order = plans.first { it.first == dir }.second
            for (id in order) {
                a.g.move(
                    MoveSpec(id, n, n, straightLine = true, directions = setOf(dir)),
                    a.heroId,
                    "Move ${a.g.name(id)} $n spaces",
                    optional = false,
                    sourceCardId = a.cardId,
                )
            }
        }

        private fun step(from: Hex, dir: Int, n: Int): Hex =
            Hex(from.q + HexGrid.DIRECTIONS[dir].q * n, from.r + HexGrid.DIRECTIONS[dir].r * n)

        /**
         * The order in which both can make the move (the one in front first), checked on a copy, or null when
         * one of them cannot move [n] spaces in [dir].
         */
        private fun plan(a: ActionScope, friend: String, dir: Int, n: Int): List<String>? {
            val me = a.heroId
            if (!a.g.canDisplace(friend, dev.quinton.goa.core.game.effects.Displacement.MOVE, me) ||
                !a.g.canDisplace(me, dev.quinton.goa.core.game.effects.Displacement.MOVE, me)
            ) {
                return null
            }
            for (order in listOf(listOf(friend, me), listOf(me, friend))) {
                val probe = Game(a.g.data, a.g.state.copy(), emptyList(), RunMode.PROBE)
                val ok = probe.asActor(me) {
                    order.all { id ->
                        val spec = MoveSpec(id, n, n, straightLine = true, directions = setOf(dir))
                        val to = probe.reach(spec).keys.firstOrNull { it != probe.posOf(id) }
                        if (to != null) probe.setPos(id, to)
                        to != null
                    }
                }
                if (ok) return order
            }
            return null
        }
    }
}
