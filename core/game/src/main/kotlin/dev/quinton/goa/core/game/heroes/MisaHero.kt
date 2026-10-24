package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.ChoiceQuery
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.distanceTo
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Misa the Samurai. Card text quoted verbatim above each registration (`catalog/heroes/misa.json`);
 * `MisaTest` follows it clause by clause (Misa has no FAQ entries). Readings the text leaves open are in
 * `core/game/rulings/misa.md`.
 */
object MisaHero : HeroModule {
    override val heroId: String = "misa"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Watch How I Soar: "Choose one — • Place yourself into a space at maximum range. • Defeat a minion
        // adjacent to you."
        r.card("misa-gold", Soar)
        // Leaf on the Wind (Handicap): "Place yourself into a space at maximum range."
        r.card("misa-handicap", LeafOnTheWind)
        // Swoop In: "Place yourself into a space in radius adjacent to two or more enemy units; if you do,
        // you may retrieve a discarded card."
        r.card("misa-silver", SwoopIn)
        // Challenge Accepted: "Target a unit adjacent to you. After the attack: This turn: Gain +2 Defense."
        // Matter of Honor: "… +3 Defense." Worthy Opponent: "… +5 Defense."
        r.card("misa-red-1", Challenge(defense = 2))
        r.card("misa-red-2a", Challenge(defense = 3))
        r.card("misa-red-3a", Challenge(defense = 5))
        // Power Shot: "Target a unit in range. After the attack: If the target was at maximum range, you may
        // move it 1 space, to a space farther away from you."
        r.card("misa-red-2b", Shot(atMaxRange = true))
        // Thunder Shot: "Target a unit in range. After the attack: If the target is not adjacent to you, you
        // may move it 1 space, to a space farther away from you."
        r.card("misa-red-3b", Shot(atMaxRange = false))
        // Focus: "Next turn: Before you perform a primary action, you may move 1 space." Discipline: "Next
        // turn: Before you perform a primary action, move up to 2 spaces." Mastery: "… move up to 3 spaces."
        r.card("misa-green-1", Focus(min = 1, max = 1))
        r.card("misa-green-2a", Focus(min = 0, max = 2))
        r.card("misa-green-3a", Focus(min = 0, max = 3))
        // Living Tornado: "Swap two units at maximum radius."
        r.card("misa-green-2b", Tornado(equalDistance = false))
        // Storm Spirit: "Swap two units in radius and at equal distance from you."
        r.card("misa-green-3b", Tornado(equalDistance = true))
        // Sudden Breeze / Gust of Wind / Crushing Squall: "Move up to 3 (4, 5) spaces in a straight line,
        // ignoring obstacles; you may place an enemy unit you moved through into a space adjacent to you."
        r.card("misa-blue-1", Gust(max = 3, placeUnit = true))
        r.card("misa-blue-2a", Gust(max = 4, placeUnit = true))
        r.card("misa-blue-3a", Gust(max = 5, placeUnit = true))
        // Dash and Slash / Death from Above: "Move up to 4 (5) spaces in a straight line, ignoring obstacles;
        // an enemy hero you moved through discards a card, if able."
        r.card("misa-blue-2b", Gust(max = 4, placeUnit = false))
        r.card("misa-blue-3b", Gust(max = 5, placeUnit = false))
        // Power Overwhelming (Ultimate): "Whenever you choose one, you may choose two different options
        // instead, in any order. Each time after you place yourself, an enemy hero adjacent to you discards a
        // card, if able."
        r.card("misa-purple-4", PowerOverwhelming)
    }

    /** Empty spaces exactly at [n] from Misa ("at maximum range"). */
    private fun ActionScope.spacesAt(n: Int): List<Hex> =
        g.within(pos, n).filter { g.distance(pos, it) == n && g.isEmpty(it, heroId) }

    private fun ActionScope.placeAtMaximumRange() {
        val spaces = spacesAt(range)
        if (spaces.isEmpty() || !g.canPlace(heroId, heroId)) fizzle("no empty space at maximum range")
        val h = chooseSpace("Place yourself into a space at maximum range", spaces)!!
        g.place(heroId, h, heroId)
    }

    private fun ActionScope.minionsToDefeat(): List<String> =
        unitsWithin(1).filter { g.isMinion(it) && isEnemy(it) && canTarget(it) }

    private object Soar : CardScript {
        private const val PLACE = 0
        private const val DEFEAT = 1

        override fun primary(a: ActionScope) {
            val placeOk = a.spacesAt(a.range).isNotEmpty() && a.g.canPlace(a.heroId, a.heroId)
            val defeatOk = a.minionsToDefeat().isNotEmpty()
            val plans = ArrayList<Pair<String, List<Int>>>()
            if (placeOk) plans += "Place yourself into a space at maximum range" to listOf(PLACE)
            if (defeatOk) plans += "Defeat a minion adjacent to you" to listOf(DEFEAT)
            // Power Overwhelming: "you may choose two different options instead, in any order."
            if (a.choiceLimit(1) >= 2) {
                if (placeOk) {
                    plans +=
                        "Both: place yourself, then defeat a minion adjacent to you" to listOf(PLACE, DEFEAT)
                }
                if (defeatOk) {
                    plans +=
                        "Both: defeat a minion adjacent to you, then place yourself" to listOf(DEFEAT, PLACE)
                }
            }
            if (plans.isEmpty()) a.fizzle("no option can be done")
            val plan = if (plans.size == 1) {
                plans.single().second
            } else {
                plans[a.chooseOption("Choose one", plans.map { it.first })!!].second
            }
            for (step in plan) {
                if (step == PLACE) {
                    a.placeAtMaximumRange()
                } else {
                    val minions = a.minionsToDefeat()
                    if (minions.isEmpty()) a.fizzle("no minion adjacent")
                    val m = a.target(a.chooseUnit("Defeat a minion adjacent to you", minions)!!)
                    a.g.defeatMinion(m, a.heroId)
                }
            }
        }
    }

    private object LeafOnTheWind : CardScript {
        override fun primary(a: ActionScope) {
            a.placeAtMaximumRange()
        }
    }

    private object SwoopIn : CardScript {
        override fun primary(a: ActionScope) {
            // "a space in radius adjacent to two or more enemy units" — presence (p.18).
            val spaces = a.g.within(a.pos, a.radius).filter { h ->
                a.g.isEmpty(h, a.heroId) && a.g.unitsAdjacentTo(h).count { a.isEnemy(it) } >= 2
            }
            if (spaces.isEmpty() || !a.g.canPlace(a.heroId, a.heroId)) a.fizzle("no space next to two enemy units")
            val h = a.chooseSpace("Place yourself into a space adjacent to two or more enemy units", spaces)!!
            a.g.place(a.heroId, h, a.heroId)
            // "if you do, you may retrieve a discarded card."
            a.mayRetrieve()
        }
    }

    private class Challenge(private val defense: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: This turn: Gain +N Defense."
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun statDelta(g: Game, src: Src, q: StatQuery): Int =
                if (q.stat == Stat.DEFENSE && g.heroBodyOf(q.unitId) == src.owner) defense else 0
        }
    }

    private class Shot(private val atMaxRange: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rangedAttackTarget() ?: return
            val wasAtMax = a.distanceTo(t) == a.range
            val r = a.attack(t)
            if (!r.connected || a.g.posOf(t) == null) return
            // Power Shot: "If the target was at maximum range"; Thunder Shot: "If the target is not adjacent to you".
            if (if (atMaxRange) !wasAtMax else a.isAdjacent(t)) return
            // "you may move it 1 space, to a space farther away from you." (p.17 "Farther")
            val me = a.pos
            val now = a.g.distance(me, a.g.posOf(t)!!)
            val farther: (Hex) -> Boolean = { h -> a.g.distance(me, h) > now }
            if (a.canTarget(t) && a.canMoveOther(t, 1, endFilter = farther)) {
                a.moveOther(t, 1, optional = true, endFilter = farther)
            }
        }
    }

    private class Focus(private val min: Int, private val max: Int) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.NEXT_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                // "Before you perform a primary action" — any, a primary Defense action included (p.14).
                if (t !is Trigger.BeforeAction || t.heroId != owner || !t.primary) return
                if (g.posOf(owner) == null) return
                val spec = MoveSpec(owner, min, max)
                val what = if (min == max) "you may move $max space" else "move up to $max spaces"
                g.move(spec, owner, "Before your primary action, $what", optional = true, sourceCardId = src.cardId)
            }
        }
    }

    private class Tornado(private val equalDistance: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val me = a.pos
            val d: (String) -> Int = { a.g.distance(me, a.g.posOf(it)!!) }
            val units = a.unitsWithin(a.radius).filter { id ->
                (equalDistance || d(id) == a.radius) && a.canTarget(id) &&
                    a.g.canDisplace(id, Displacement.SWAP, a.heroId)
            }
            // "Swap two units at maximum radius" / "in radius and at equal distance from you".
            val firsts = units.filter { u -> units.any { it != u && d(it) == d(u) } }
            if (firsts.isEmpty()) a.fizzle("no two units to swap")
            val first = a.target(a.chooseUnit("Swap two units: choose the first", firsts)!!)
            val second = a.target(
                a.chooseUnit("Choose the unit it swaps with", units.filter { it != first && d(it) == d(first) })!!,
            )
            a.g.swap(first, second, a.heroId)
        }
    }

    private class Gust(private val max: Int, private val placeUnit: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Move up to N spaces in a straight line, ignoring obstacles"
            val path = a.moveSelf(0, max, straightLine = true, ignoreObstacles = true)
            val passed = path.dropLast(1).mapNotNull { a.g.occupant(it) }.filter { id ->
                a.g.isUnit(id) && a.isEnemy(id) && a.canTarget(id)
            }
            if (placeUnit) {
                // "you may place an enemy unit you moved through into a space adjacent to you."
                val units = passed.filter { a.g.canPlace(it, a.heroId) }
                val free = HexGrid.neighbors(a.pos).filter { a.g.isEmpty(it) }
                if (units.isEmpty() || free.isEmpty()) return
                val u =
                    a.chooseUnit("You may place an enemy unit you moved through next to you", units, optional = true)
                        ?: return
                a.target(u)
                val h = a.chooseSpace(
                    "Place ${a.g.name(u)} into a space adjacent to you",
                    free.filter {
                        a.g.isEmpty(it, u)
                    },
                )!!
                a.g.place(u, h, a.heroId)
            } else {
                // "an enemy hero you moved through discards a card, if able."
                val heroes = passed.filter { a.g.isHero(it) }
                val v = a.chooseUnit("An enemy hero you moved through discards a card", heroes) ?: return
                a.discardIfAble(v)
            }
        }
    }

    private object PowerOverwhelming : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            /** "Whenever you choose one, you may choose two different options instead". */
            override fun choiceLimit(g: Game, src: Src, q: ChoiceQuery): Int? =
                if (q.heroId == src.owner && q.printed == 1 && !q.upTo) 2 else null

            /** "Each time after you place yourself, an enemy hero adjacent to you discards a card, if able." */
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.Placed || g.heroBodyOf(t.id) != owner || t.byHeroId != owner || t.from == null) return
                val at = g.posOf(owner) ?: return
                val action = g.currentAction()
                val kind = action?.kind ?: ActionKind.SKILL
                val heroes = g.unitsAdjacentTo(at).filter { id ->
                    g.isHero(id) && g.isEnemy(owner, id) && g.canTarget(id, owner, kind, action?.cardId ?: src.cardId)
                }
                if (heroes.isEmpty()) return
                val v = (
                    g.ask(
                        owner,
                        DecisionKind.CHOOSE_UNIT,
                        "Power Overwhelming: an enemy hero adjacent to you discards a card",
                        heroes.map { Choice.Unit(it) },
                        src.cardId,
                    ) as Choice.Unit
                    ).unitId
                g.forceDiscard(v, owner, orDefeated = false, sourceCardId = src.cardId)
            }
        }
    }
}
