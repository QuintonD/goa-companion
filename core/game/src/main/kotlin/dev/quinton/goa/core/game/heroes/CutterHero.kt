package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.chooseDistance
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inStraightLine
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canInteract
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isObstacle
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.performPrimary
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.win
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.model.board.Hex

/**
 * Cutter the Sky Pirate. Card text quoted verbatim above each registration (`catalog/heroes/cutter.json`);
 * `CutterTest` follows it clause by clause with the FAQ. Rulings: `core/game/rulings/cutter.md`.
 */
object CutterHero : HeroModule {
    override val heroId: String = "cutter"
    override val complete: Boolean = true

    private const val WIN_COINS = 13

    override fun register(r: Registry) {
        // Walk the Plank: "Choose one — • Push an enemy hero adjacent to you up to 4 spaces; if that hero is
        // pushed into another zone, that hero discards a card, or is defeated. • Defeat a minion adjacent to you."
        r.card("cutter-gold", Plank(push = 4, defeatBullet = true))
        // Few More Steps (Handicap): "Push an enemy hero adjacent to you up to 3 spaces; if that hero is pushed
        // into another zone, that hero discards a card, or is defeated."
        r.card("cutter-handicap", Plank(push = 3, defeatBullet = false))
        // Grappling Bolt: "Target an obstacle in range and in a straight line, with no obstacles between you;
        // ignore immunity. Move in a straight line towards that obstacle until you are adjacent to it."
        r.card("cutter-silver", Grapple)
        // Daring Strike: "Choose one — • Before the attack: Move 1 space. Target a hero adjacent to you in the
        // direction of the move; +2 Attack. • Target a unit adjacent to you." Bold Thrust: "• Before the
        // attack: Move 1 or 2 spaces in a straight line. …" Fearless Lunge: "… Move 1, 2 or 3 spaces …"
        r.card("cutter-red-1", Lunge(maxMove = 1))
        r.card("cutter-red-2a", Lunge(maxMove = 2))
        r.card("cutter-red-3a", Lunge(maxMove = 3))
        // Evasive Shot: "Target a unit in range and in a straight line. After the attack: Move up to 2 spaces
        // in the opposite direction." Tumble Shot: "… Move up to 3 spaces in the opposite direction."
        r.card("cutter-red-2b", Evasive(back = 2))
        r.card("cutter-red-3b", Evasive(back = 3))
        // Brace for Impact: "Move 3 spaces in a straight line, ignoring obstacles, to a space adjacent to an
        // enemy hero; that hero discards a card, if able." Ramming Speed: "Move 3 or 4 spaces …"
        // Crashland: "Move 3, 4 or 5 spaces …"
        r.card("cutter-green-1", Ram(max = 3))
        r.card("cutter-green-2a", Ram(max = 4))
        r.card("cutter-green-3a", Ram(max = 5))
        // Bombardment / Barrage: "An enemy hero in radius, adjacent to another enemy unit and not adjacent to
        // you, discards a card, if able." Broadside: "… May repeat once on a different target."
        r.card("cutter-blue-1", Bombard(repeat = false))
        r.card("cutter-blue-2a", Bombard(repeat = false))
        r.card("cutter-blue-3a", Bombard(repeat = true))
        // Outmaneuver: "Swap with an enemy minion in radius; you may move that minion up to 2 spaces."
        // Outsmart: "… up to 3 spaces."
        r.card("cutter-green-2b", Outmaneuver(move = 2))
        r.card("cutter-green-3b", Outmaneuver(move = 3))
        // X Marks the Spot: "An enemy hero in radius chooses one — • You place that hero in a space in radius.
        // • You gain 2 coins." A Fistful of Coins: "… • You gain 3 coins. If you have 13 or more coins, you
        // alone win the game."
        r.card("cutter-blue-2b", XMarks(coins = 2, mayWin = false))
        r.card("cutter-blue-3b", XMarks(coins = 3, mayWin = true))
        // Legend of the Skies (Ultimate): "The first time each turn after you perform a primary action, you may
        // perform the primary action of a card in the previous turn slot."
        r.card("cutter-purple-4", Legend)
    }

    private fun step(from: Hex, dir: Int, n: Int): Hex =
        Hex(from.q + HexGrid.DIRECTIONS[dir].q * n, from.r + HexGrid.DIRECTIONS[dir].r * n)

    private class Plank(private val push: Int, private val defeatBullet: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val heroes = a.unitsWithin(1).filter {
                a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) && a.g.canDisplace(it, Displacement.PUSH, a.heroId)
            }
            val minions = if (defeatBullet) {
                a.unitsWithin(1).filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
            } else {
                emptyList()
            }
            val labels = listOf(
                "Push an enemy hero adjacent to you up to $push spaces",
                "Defeat a minion adjacent to you",
            )
            // "Choose one —" (RULINGS 38): a bullet that cannot start is not offered.
            val pick = when {
                heroes.isEmpty() && minions.isEmpty() -> a.fizzle("nothing adjacent to push or defeat")
                minions.isEmpty() -> 0
                heroes.isEmpty() -> 1
                else -> a.chooseOption("Choose one", labels)!!
            }
            if (pick == 1) {
                // "You can never defeat a friendly unit" (p.17): enemy minions only.
                val m = a.target(a.chooseUnit(labels[1], minions)!!)
                a.g.defeatMinion(m, a.heroId)
                return
            }
            val t = a.target(a.chooseUnit("Push an enemy hero adjacent to you", heroes)!!)
            val zoneBefore = a.g.posOf(t)?.let { a.g.zoneOf(it) }
            val n = a.chooseDistance((0..push).toList(), "Push ${a.g.name(t)} how many spaces?")
            val res = a.pushAway(t, n) ?: return
            // "if that hero is pushed into another zone, that hero discards a card, or is defeated."
            val zoneAfter = res.to?.let { a.g.zoneOf(it) }
            if (res.moved > 0 && zoneAfter != zoneBefore) a.discardOrDefeat(t)
        }
    }

    /**
     * FAQ: "Any obstacle (units, tokens, terrain, or other miniatures) can be targeted by Grappling Bolt.
     * This includes immune units as well as the terrain spaces around the edge of the map".
     */
    private object Grapple : CardScript {
        override fun primary(a: ActionScope) {
            val me = a.pos
            val options = LinkedHashMap<Hex, Int>()
            for (dir in HexGrid.DIRECTIONS.indices) {
                for (k in 1..a.range) {
                    val h = step(me, dir, k)
                    if (!a.g.geo.isOnBoard(h)) break
                    // The first obstacle in this line: "no obstacles between you".
                    if (a.g.isObstacle(h, a.heroId)) {
                        if (grappable(a, h)) options[h] = dir
                        break
                    }
                }
            }
            if (options.isEmpty()) a.fizzle("no obstacle in range and in a straight line")
            val h = a.chooseSpace("Target an obstacle in range and in a straight line", options.keys)!!
            a.g.occupant(h)?.let { a.target(it) }
            val dir = options.getValue(h)
            val n = a.g.distance(me, h) - 1
            if (n <= 0) return
            // "Move in a straight line towards that obstacle until you are adjacent to it."
            a.g.move(
                MoveSpec(a.heroId, n, n, straightLine = true, directions = setOf(dir)),
                a.heroId,
                "Move $n space${if (n == 1) "" else "s"} towards the obstacle",
                optional = false,
                sourceCardId = a.cardId,
            )
        }

        /** "ignore immunity" — but not effects that forbid interacting with or targeting it. */
        private fun grappable(a: ActionScope, h: Hex): Boolean {
            val occ = a.g.occupant(h) ?: return a.g.canInteract(h)
            return a.canTarget(occ, ignoreImmunity = true)
        }
    }

    private class Lunge(private val maxMove: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val labels = listOf(
                if (maxMove == 1) {
                    "Move 1 space, then target a hero adjacent to you in the direction of the move (+2 Attack)"
                } else {
                    "Move 1 to $maxMove spaces in a line, then target a hero next in that direction (+2 Attack)"
                },
                "Target a unit adjacent to you",
            )
            val canLunge = a.g.canMove(MoveSpec(a.heroId, 1, maxMove, straightLine = true))
            val adjacent = a.unitsWithin(1).any { a.isEnemy(it) && a.canTarget(it) }
            val pick = when {
                !canLunge && !adjacent -> a.fizzle("no move and no adjacent target")
                !adjacent -> 0
                !canLunge -> 1
                else -> a.chooseOption("Choose one", labels)!!
            }
            if (pick == 1) {
                val t = a.adjacentAttackTarget() ?: return
                a.attack(t)
                return
            }
            // "Before the attack: Move 1 (1 or 2; 1, 2 or 3) space(s) in a straight line." Mandatory.
            val start = a.pos
            a.moveSelf(1, maxMove, straightLine = true)
            val end = a.pos
            val dir = HexGrid.straightLineDirection(start, end) ?: return
            val front = step(end, dir, 1)
            // "Target a hero adjacent to you in the direction of the move; +2 Attack."
            val inFront: (String) -> Boolean = { a.g.isHero(it) && a.g.posOf(it) == front }
            val t =
                a.attackTarget("Target a hero adjacent to you in the direction of the move") { inFront(it) } ?: return
            a.attack(t, valid = inFront, bonus = { 2 })
        }
    }

    private class Evasive(private val back: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val line: (String) -> Boolean = { a.inStraightLine(it) && a.inRange(it) }
            val t = a.rangedAttackTarget("Target a unit in range and in a straight line") { a.inStraightLine(it) }
                ?: return
            val dir = HexGrid.straightLineDirection(a.pos, a.g.posOf(t)!!)!!
            val r = a.attack(t, valid = line)
            if (!r.connected) return
            // "After the attack: Move up to N spaces in the opposite direction."
            val opposite = (dir + HexGrid.DIRECTIONS.size / 2) % HexGrid.DIRECTIONS.size
            a.g.move(
                MoveSpec(a.heroId, 0, back, straightLine = true, directions = setOf(opposite)),
                a.heroId,
                "Move up to $back spaces in the opposite direction",
                optional = true,
                sourceCardId = a.cardId,
            )
        }
    }

    private class Ram(private val max: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Move 3 (or more) spaces in a straight line, ignoring obstacles, to a space adjacent to an enemy
            // hero" — presence (as Brogan's charge, FAQ): an immune hero still makes the space valid.
            val nextToEnemyHero = { h: Hex ->
                a.g.unitsAdjacentTo(h).any { it != a.heroId && a.g.isHero(it) && a.isEnemy(it) }
            }
            a.moveSelf(3, max, straightLine = true, ignoreObstacles = true, endFilter = nextToEnemyHero)
            // "that hero discards a card, if able."
            val heroes = a.unitsWithin(1).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            val v = a.chooseUnit("That hero discards a card, if able", heroes) ?: return
            a.discardIfAble(v)
        }
    }

    private class Bombard(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val victims = a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.g.distance(a.pos, a.g.posOf(id)!!) > 1 && a.canTarget(id) &&
                    nextToAnotherEnemy(a, id)
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero in radius next to another enemy unit and not next to you")
            val v = a.chooseUnit(
                "An enemy hero in radius, adjacent to another enemy unit and not adjacent to you, discards a card",
                victims,
            )!!
            a.discardIfAble(v)
            if (repeat) a.repeat(differentTarget = true, prompt = "Repeat Broadside on a different target?")
        }

        /** "adjacent to another enemy unit" — presence (p.18), any other unit of the enemy team. */
        private fun nextToAnotherEnemy(a: ActionScope, id: String): Boolean {
            val body = a.g.heroBodyOf(id)
            return a.g.unitsAdjacentTo(a.g.posOf(id)!!).any { o ->
                a.g.heroBodyOf(o).let { it == null || it != body } &&
                    a.isEnemy(o)
            }
        }
    }

    private class Outmaneuver(private val move: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val minions = a.unitsWithin(a.radius).filter {
                a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) && a.g.canSwap(a.heroId, it, a.heroId)
            }
            if (minions.isEmpty()) a.fizzle("no enemy minion in radius to swap with")
            val m = a.target(a.chooseUnit("Swap with an enemy minion in radius", minions)!!)
            a.g.swap(a.heroId, m, a.heroId)
            // "you may move that minion up to N spaces."
            if (a.canMoveOther(m, 1, move)) a.moveOther(m, 0, move)
        }
    }

    private class XMarks(private val coins: Int, private val mayWin: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val heroes = a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            if (heroes.isEmpty()) a.fizzle("no enemy hero in radius")
            val t = a.target(a.chooseUnit("An enemy hero in radius chooses", heroes)!!)
            val chooser = a.g.heroBodyOf(t)!!
            val from = a.g.posOf(t)!!
            val spaces = a.g.within(a.pos, a.radius).filter { it != from && a.g.isEmpty(it, t) }
            val canPlace = spaces.isNotEmpty() && a.g.canPlace(t, a.heroId)
            val labels = listOf(
                "${a.g.heroName(a.heroId)} places you in a space in radius",
                "${a.g.heroName(a.heroId)} gains $coins coins",
            )
            // "chooses one —": the enemy hero chooses; a bullet that cannot be done is not offered (RULINGS 38).
            val pick = if (canPlace) a.chooseOption("Choose one", labels, chooser = chooser)!! else 1
            if (pick == 0) {
                val h = a.chooseSpace("Place ${a.g.name(t)} in a space in radius", spaces)!!
                a.g.place(t, h, a.heroId)
                return
            }
            a.g.gainCoins(a.heroId, coins, a.card.name)
            // "If you have 13 or more coins, you alone win the game."
            if (mayWin && a.hero.coins >= WIN_COINS) {
                val team = a.g.teamOf(a.heroId)!!
                a.g.win(
                    team,
                    WinReason.CARD_TEXT,
                    "${a.g.heroName(a.heroId)} has ${a.hero.coins} coins and alone wins the game.",
                )
            }
        }
    }

    /**
     * "The first time each turn after you perform a primary action": the opportunity comes once, after
     * the first primary action of the turn (declining it does not keep it for a later one); a repeated
     * instance is part of that action, so the hook waits for the whole action.
     */
    private object Legend : CardScript {
        override val hasPrimary: Boolean get() = false

        private const val USED = "cutter-legend-of-the-skies"

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.AfterAction || t.heroId != owner || !t.primary || t.repeat) return
                if (USED in g.state.turnRecord.notes) return
                g.state.turnRecord.notes += USED
                if (g.posOf(owner) == null) return
                // "a card in the previous turn slot" — the card resolved last turn (none on turn 1).
                val previous = g.state.turn - 1
                val card = g.hero(owner).cards.firstOrNull {
                    it.zone == Zone.RESOLVED && it.slot == previous && !it.faceDown
                }?.id ?: return
                val kind = primaryKind(g.card(card))
                if (kind == ActionKind.DEFENSE || kind == ActionKind.NONE) return
                if (g.data.registry.script(card)?.hasPrimary != true) return
                if (!g.actionAllowed(owner, card, kind, true) || !g.primaryPossible(owner, card)) return
                val c = g.ask(
                    owner,
                    DecisionKind.CONFIRM,
                    "Legend of the Skies: perform the primary action of ${g.cardName(card)}?",
                    listOf(Choice.Yes, Choice.Decline("Don't")),
                    src.cardId,
                    auto = false,
                )
                if (c is Choice.Yes) g.performPrimary(owner, card)
            }
        }
    }
}
