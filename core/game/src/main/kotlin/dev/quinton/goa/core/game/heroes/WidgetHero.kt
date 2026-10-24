package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.ControlSignal
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameEnded
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.ProbeHit
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isObstacle
import dev.quinton.goa.core.game.engine.isSkillCard
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.performPrimary
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.removeMinion
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.straightLineFrom
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex

/**
 * Widget the Dragonbonded, with Pyro. Card text quoted verbatim above each registration
 * (`catalog/heroes/widget.json`); `WidgetTest` follows it clause by clause. Rulings: `rulings/widget.md`.
 *
 * Pyro is one piece of kind [PYRO]: it "counts as a token, but is not removed at the end of round"
 * (Dragon Bond), so it is an obstacle, Clear removes it, and it stays when Widget is defeated (p.19).
 */
object WidgetHero : HeroModule {
    override val heroId: String = "widget"
    override val complete: Boolean = true

    /** Pyro's piece kind (registry key). */
    const val PYRO: String = "pyro"

    override fun register(r: Registry) {
        r.token(TokenKind(PYRO, "Pyro", supply = 1, removeAtEndOfRound = false))
        // Fight as One: "Target a unit adjacent to you. After the attack: You may perform the primary
        // action on one of your resolved skill cards, targeting a different unit."
        r.card("widget-gold", FightAsOne(heroOnly = false))
        // Follow Along (Handicap): "Target a hero adjacent to you. After the attack: You may perform the
        // primary action on one of your resolved skill cards, targeting a different unit."
        r.card("widget-handicap", FightAsOne(heroOnly = true))
        // Dragon Bond: "Choose one — • Place Pyro into a space in radius. Pyro counts as a token, but is
        // not removed at the end of round. • If Pyro is in play, move both yourself and Pyro up to 2
        // spaces, in any order."
        r.card("widget-silver", DragonBond)
        // Diversionary Strike / Attack / Assault: "Target a unit adjacent to you. After the attack: Move
        // Pyro up to 2 (3, 4) spaces."
        r.card("widget-red-1", Diversionary(pyroMove = 2))
        r.card("widget-red-2a", Diversionary(pyroMove = 3))
        r.card("widget-red-3a", Diversionary(pyroMove = 4))
        // Airborne Attack: "Before the attack: You may swap with Pyro in radius. Target a unit adjacent
        // to you." Airborne Assault: "… After the attack: You may swap with Pyro in radius."
        r.card("widget-red-2b", Airborne(swapAfter = false))
        r.card("widget-red-3b", Airborne(swapAfter = true))
        // Fiery Breath / Flaming Breath: "An enemy hero in range of Pyro and in a straight line from Pyro
        // discards a card, if able." Scorching Breath: "… discards a card, or is defeated."
        r.card("widget-green-1", Breath(orDefeated = false))
        r.card("widget-green-2a", Breath(orDefeated = false))
        r.card("widget-green-3a", Breath(orDefeated = true))
        // Nibble: "Remove an enemy minion in range adjacent to Pyro. Remove Pyro."
        r.card("widget-green-2b", Eat(defeat = false))
        // Gnaw: "Defeat a minion in range adjacent to Pyro. Remove Pyro."
        r.card("widget-green-3b", Eat(defeat = true))
        // All Aboard / Take-Off: "Swap Pyro in range with yourself or with a friendly hero in range."
        r.card("widget-blue-1", Aboard(moveFirst = false))
        r.card("widget-blue-2a", Aboard(moveFirst = false))
        // Safe Landing: "You may move Pyro in range 1 space. Swap Pyro in range with yourself or with a
        // friendly hero in range."
        r.card("widget-blue-3a", Aboard(moveFirst = true))
        // Drag Off: "Move both Pyro, and an enemy unit adjacent to Pyro, either 2 or 3 spaces each, in
        // the same direction." Carry Away: "…, in the same direction, ignoring obstacles."
        r.card("widget-blue-2b", Drag(ignoreObstacles = false))
        r.card("widget-blue-3b", Drag(ignoreObstacles = true))
        // Dragon Knight (Ultimate): "Each time after you perform a movement action, you may perform the
        // primary action on one of your faceup skill cards."
        r.card("widget-purple-4", DragonKnight)
    }

    // ---- Pyro ------------------------------------------------------------------------------------

    /** Pyro when it is in play (on the board). */
    fun pyro(g: Game): PieceState? = g.state.pieces.firstOrNull { it.kind == PYRO && it.at != null }

    private fun ActionScope.pyroOrFizzle(): PieceState = pyro(g) ?: fizzle("Pyro is not in play")

    /** Spaces from [from] to [id] (off the board: never in range). */
    private fun ActionScope.distanceFrom(from: Hex, id: String): Int =
        g.posOf(id)?.let { g.distance(from, it) } ?: Int.MAX_VALUE

    /** Pyro is 1..[n] spaces from you ("Pyro in range / in radius"). */
    private fun ActionScope.pyroWithin(n: Int): PieceState? {
        val p = pyro(g) ?: return null
        val me = hero.at ?: return null
        return p.takeIf { g.distance(me, it.at!!) in 1..n }
    }

    /** "Swap with Pyro in radius" (Airborne): optional. */
    private fun ActionScope.maySwapWithPyro(prompt: String) {
        val p = pyroWithin(radius) ?: return
        if (!g.canSwap(heroId, p.id, heroId)) return
        if (confirm(prompt)) g.swap(heroId, p.id, heroId)
    }

    /** "Move Pyro up to N spaces" (p.11 movement in the card text: through empty spaces). */
    private fun ActionScope.movePyroUpTo(n: Int) {
        val p = pyro(g) ?: return
        g.move(MoveSpec(p.id, 0, n), heroId, "Move Pyro up to $n spaces", optional = true, sourceCardId = cardId)
    }

    // ---- cards -----------------------------------------------------------------------------------

    private class FightAsOne(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            val res = a.attack(t)
            if (!res.connected) return
            // "After the attack: You may perform the primary action on one of your resolved skill cards,
            // targeting a different unit." Offered: faceup resolved skill cards whose action can start
            // without the unit just attacked.
            val excluded = a.targets.toSet() + a.excluded
            val cards = a.g.hero(a.heroId).cards.filter { c ->
                c.zone == Zone.RESOLVED && !c.faceDown && a.g.isSkillCard(a.heroId, c.id) &&
                    a.g.canPerformPrimary(a.heroId, c.id, excluded)
            }.map { it.id }
            val c = a.chooseCard(
                "You may perform the primary action of one of your resolved skill cards (a different unit)",
                cards,
                optional = true,
            ) ?: return
            a.g.performPrimary(a.heroId, c, excluded)
        }
    }

    private object DragonBond : CardScript {
        private const val PLACE = 0
        private const val MOVE_BOTH = 1

        override fun primary(a: ActionScope) {
            val p = pyro(a.g)
            val spaces = placeSpaces(a, p)
            val bullets = buildList {
                if (spaces.isNotEmpty()) add(Choice.Option(PLACE, "Place Pyro into a space in radius"))
                if (p != null) add(Choice.Option(MOVE_BOTH, "Move both yourself and Pyro up to 2 spaces, in any order"))
            }
            // RULINGS 38: a bullet that cannot start is not offered; one left is not asked.
            if (bullets.isEmpty()) a.fizzle("Pyro can't be placed and is not in play")
            val pick = a.g.ask(a.heroId, DecisionKind.CHOOSE_OPTION, "Dragon Bond: choose one", bullets, a.cardId)
            if ((pick as Choice.Option).index == PLACE) {
                val h = a.chooseSpace("Place Pyro into a space in radius", spaces)!!
                if (p != null) a.g.place(p.id, h, a.heroId) else a.g.placeToken(PYRO, a.heroId, h)
                return
            }
            // "If Pyro is in play, move both yourself and Pyro up to 2 spaces, in any order."
            val order = a.chooseOption("Who moves first?", listOf("You first", "Pyro first"))
            if (order == 0) {
                a.g.move(MoveSpec(a.heroId, 0, 2), a.heroId, "Move up to 2 spaces", optional = true, sourceCardId = a.cardId)
                a.movePyroUpTo(2)
            } else {
                a.movePyroUpTo(2)
                a.g.move(MoveSpec(a.heroId, 0, 2), a.heroId, "Move up to 2 spaces", optional = true, sourceCardId = a.cardId)
            }
        }

        /** p.18 "Place": an empty space other than Pyro's own; from the supply or from the board. */
        private fun placeSpaces(a: ActionScope, p: PieceState?): List<Hex> {
            if (p != null && !a.g.canPlace(p.id, a.heroId)) return emptyList()
            val me = a.hero.at ?: return emptyList()
            return a.g.within(me, a.radius).filter { it != p?.at && a.g.isEmpty(it) }
        }
    }

    private class Diversionary(private val pyroMove: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val res = a.attack(t)
            if (!res.connected) return
            a.movePyroUpTo(pyroMove)
        }
    }

    private class Airborne(private val swapAfter: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // The card prints "Before the attack" first: the swap comes before the target is chosen.
            a.maySwapWithPyro("Before the attack: you may swap with Pyro in radius")
            val t = a.adjacentAttackTarget() ?: return
            val res = a.attack(t)
            if (!res.connected || !swapAfter) return
            a.maySwapWithPyro("After the attack: you may swap with Pyro in radius")
        }
    }

    private class Breath(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val p = a.pyroOrFizzle()
            val from = p.at!!
            // "An enemy hero in range of Pyro and in a straight line from Pyro": the card's Range counted
            // from Pyro's space (p.9), along one of the six lines (p.19).
            val victims = a.g.unitsOnBoard().filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.distanceFrom(from, id) in 1..a.range &&
                    HexGrid.inStraightLine(from, a.g.posOf(id)!!) && a.canTarget(id)
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero in range of Pyro and in a straight line from it")
            val v = a.chooseUnit("An enemy hero in range of Pyro, in a straight line from it, discards a card", victims)!!
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
        }
    }

    private class Eat(private val defeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val p = a.pyroOrFizzle()
            val me = a.pos
            // Nibble: "an enemy minion"; Gnaw: "a minion" — p.17 "You can never defeat a friendly unit.
            // No exceptions", so only enemy minions can be defeated (rulings/widget.md).
            val minions = a.g.unitsOnBoard().filter { id ->
                a.g.isMinion(id) && a.isEnemy(id) && a.g.distance(me, a.g.posOf(id)!!) in 1..a.range &&
                    a.g.distance(p.at!!, a.g.posOf(id)!!) == 1 && a.canTarget(id)
            }
            if (minions.isEmpty()) a.fizzle("no enemy minion in range adjacent to Pyro")
            val verb = if (defeat) "Defeat" else "Remove"
            val m = a.target(a.chooseUnit("$verb a minion in range adjacent to Pyro", minions)!!)
            if (defeat) a.g.defeatMinion(m, a.heroId) else a.g.removeMinion(m)
            // "Remove Pyro."
            pyro(a.g)?.let { a.g.removePiece(it.id) }
        }
    }

    private class Aboard(private val moveFirst: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // Safe Landing: "You may move Pyro in range 1 space."
            if (moveFirst) {
                a.pyroWithin(a.range)?.let { p ->
                    a.g.move(MoveSpec(p.id, 1, 1), a.heroId, "You may move Pyro 1 space", optional = true, sourceCardId = a.cardId)
                }
            }
            // "Swap Pyro in range with yourself or with a friendly hero in range."
            val p = a.pyroWithin(a.range) ?: a.fizzle("Pyro is not in range")
            val me = a.pos
            val partners = buildList {
                if (a.g.canSwap(p.id, a.heroId, a.heroId)) add(a.heroId)
                for (id in a.g.unitsOnBoard()) {
                    if (a.g.isHero(id) && a.isFriendly(id) && a.distanceFrom(me, id) in 1..a.range &&
                        a.canTarget(id) && a.g.canSwap(p.id, id, a.heroId)
                    ) {
                        add(id)
                    }
                }
            }
            if (partners.isEmpty()) a.fizzle("nobody can swap with Pyro")
            val who = a.target(a.chooseUnit("Swap Pyro with yourself or with a friendly hero in range", partners)!!)
            a.g.swap(p.id, who, a.heroId)
        }
    }

    /** One way to do Drag Off: Pyro [k] and the unit [j] spaces in direction [dir]; [unitFirst] says the order. */
    private data class DragPlan(val dir: Int, val k: Int, val j: Int, val unitFirst: Boolean)

    private class Drag(private val ignoreObstacles: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val p = a.pyroOrFizzle()
            val pAt = p.at!!
            if (!a.g.canDisplace(p.id, Displacement.MOVE, a.heroId)) a.fizzle("Pyro cannot be moved")
            val units = a.g.unitsAdjacentTo(pAt).filter { id ->
                a.isEnemy(id) && a.canTarget(id) && a.g.canDisplace(id, Displacement.MOVE, a.heroId) &&
                    plans(a, pAt, id).isNotEmpty()
            }
            if (units.isEmpty()) a.fizzle("no enemy unit adjacent to Pyro can be moved with it")
            val u = a.target(a.chooseUnit("Choose an enemy unit adjacent to Pyro", units)!!)
            val uAt = a.g.posOf(u)!!
            val all = plans(a, pAt, u)
            fun end(from: Hex, dir: Int, n: Int) = a.g.straightLineFrom(from, dir, n).last()
            val pyroEnd = a.chooseSpace("Move Pyro 2 or 3 spaces in a straight line", all.map { end(pAt, it.dir, it.k) })!!
            val withPyro = all.filter { end(pAt, it.dir, it.k) == pyroEnd }
            val unitEnd = a.chooseSpace(
                "Move ${a.g.name(u)} 2 or 3 spaces in the same direction",
                withPyro.map { end(uAt, it.dir, it.j) },
            )!!
            val plan = withPyro.first { end(uAt, it.dir, it.j) == unitEnd }
            val movePyro = { moveStraight(a, p.id, plan.dir, plan.k) }
            val moveUnit = { moveStraight(a, u, plan.dir, plan.j) }
            if (plan.unitFirst) {
                moveUnit()
                movePyro()
            } else {
                movePyro()
                moveUnit()
            }
        }

        private fun moveStraight(a: ActionScope, id: String, dir: Int, n: Int) {
            a.g.move(
                MoveSpec(id, n, n, straightLine = true, ignoreObstacles = ignoreObstacles, directions = setOf(dir)),
                a.heroId,
                "Move ${a.g.name(id)} $n spaces",
                optional = false,
                sourceCardId = a.cardId,
            )
        }

        /** Every (direction, Pyro distance, unit distance) both moves can make, in some order. */
        private fun plans(a: ActionScope, pAt: Hex, unit: String): List<DragPlan> {
            val uAt = a.g.posOf(unit) ?: return emptyList()
            val pyroId = pyro(a.g)?.id ?: return emptyList()
            val out = ArrayList<DragPlan>()
            for (dir in HexGrid.DIRECTIONS.indices) {
                for (k in 2..3) {
                    for (j in 2..3) {
                        val pEnd = a.g.straightLineFrom(pAt, dir, k).last()
                        val uEnd = a.g.straightLineFrom(uAt, dir, j).last()
                        val pyroFirst = lineOk(a, pyroId, pAt, dir, k, vacated = null, filled = null) &&
                            lineOk(a, unit, uAt, dir, j, vacated = pAt, filled = pEnd)
                        val unitFirst = lineOk(a, unit, uAt, dir, j, vacated = null, filled = null) &&
                            lineOk(a, pyroId, pAt, dir, k, vacated = uAt, filled = uEnd)
                        if (pyroFirst) {
                            out += DragPlan(dir, k, j, unitFirst = false)
                        } else if (unitFirst) {
                            out += DragPlan(dir, k, j, unitFirst = true)
                        }
                    }
                }
            }
            return out
        }

        /**
         * A straight move of [n] spaces from [start] (p.11: exact distance; ignoring obstacles it may pass
         * them, p.18, but must end in an empty space). [vacated] is the other mover's old space (now
         * empty), [filled] its new one.
         */
        @Suppress("LongParameterList")
        private fun lineOk(a: ActionScope, mover: String, start: Hex, dir: Int, n: Int, vacated: Hex?, filled: Hex?): Boolean {
            val line = a.g.straightLineFrom(start, dir, n)
            for ((i, h) in line.withIndex()) {
                val free = when (h) {
                    filled -> false
                    vacated -> a.g.geo.isSpace(h)
                    else -> !a.g.isObstacle(h, mover)
                }
                val last = i == line.lastIndex
                val ok = if (last || !ignoreObstacles) free else a.g.geo.isOnBoard(h)
                if (!ok) return false
            }
            return true
        }
    }

    private object DragonKnight : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.AfterAction || t.heroId != owner || t.kind != ActionKind.MOVEMENT) return
                if (g.posOf(owner) == null) return
                // "faceup skill cards": the revealed played card, resolved and discarded cards that are not
                // facedown (rulings/widget.md); hand cards are hidden and items are not Action cards in play.
                val cards = g.hero(owner).cards.filter { c ->
                    val faceup = when (c.zone) {
                        Zone.PLAYED -> g.state.revealed
                        Zone.RESOLVED, Zone.DISCARD -> !c.faceDown
                        else -> false
                    }
                    faceup && g.isSkillCard(owner, c.id) && g.canPerformPrimary(owner, c.id, emptySet())
                }.map { it.id }
                if (cards.isEmpty()) return
                val opts = cards.map { Choice.Card(it) as Choice } + Choice.Decline("Don't")
                val c = g.ask(
                    owner,
                    DecisionKind.CHOOSE_CARD,
                    "Dragon Knight: you may perform the primary action of one of your faceup skill cards",
                    opts,
                    src.cardId,
                ) as? Choice.Card ?: return
                g.performPrimary(owner, c.cardId)
            }
        }
    }
}

/**
 * Could [heroId] perform the primary action of [cardId] now without targeting [excluded] (the FAQ's
 * "first instruction" test, Actions.primaryPossible, with a repeat-style exclusion)?
 */
internal fun Game.canPerformPrimary(heroId: String, cardId: String, excluded: Set<String>): Boolean {
    val kind = primaryKind(card(cardId))
    if (kind == ActionKind.DEFENSE || kind == ActionKind.NONE) return false
    if (data.registry.script(cardId)?.hasPrimary != true) return false
    if (!actionAllowed(heroId, cardId, kind, true)) return false
    if (excluded.isEmpty()) return primaryPossible(heroId, cardId)
    val copy = state.copy()
    val probe = Game(data, copy, emptyList(), RunMode.PROBE)
    val before = copy.stamp
    return try {
        probe.performAction(heroId, cardId, kind, primary = true, excluded = excluded, probing = true)
        true
    } catch (_: ProbeHit) {
        true
    } catch (_: GameEnded) {
        true
    } catch (_: ControlSignal) {
        copy.stamp != before
    }
}
