package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseDistance
import dev.quinton.goa.core.game.effects.chooseUpTo
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.ownerPos
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.radiusOf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.cardTypeOf
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.fastTravel
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isBasic
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.resolvedCards
import dev.quinton.goa.core.game.engine.sortedHexes
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex

/**
 * Swift the Sharpshooter. Card text quoted verbatim above each registration
 * (`catalog/heroes/swift.json`); `SwiftTest` follows it clause by clause with the FAQ. Readings the
 * text leaves open are in `core/game/rulings/swift.md`.
 */
object SwiftHero : HeroModule {
    override val heroId: String = "swift"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Reload!: "Choose one — • Perform the primary action of your rightmost resolved card.
        // • Defeat a minion adjacent to you."
        r.card("swift-gold", Reload)
        // Backup (Handicap): "Perform the primary action of your rightmost resolved card. If you perform
        // an Attack action this way, you cannot target minions."
        r.card("swift-handicap", Backup)
        // Bounce: "Move 2 spaces in a straight line, ignoring obstacles; if this card is already resolved
        // as you perform this action, may repeat once."
        r.card("swift-silver", Bounce)
        // Snipe: "Target a unit at maximum range, and in a straight line."
        r.card("swift-red-1", Snipe)
        // Prepared Shot / Killshot: "Target a unit in range, in a straight line, and not adjacent to you."
        r.card("swift-red-2a", PreparedShot)
        r.card("swift-red-3a", PreparedShot)
        // Shotgun: "Target a unit in range. Before the attack: An enemy hero adjacent to the target
        // discards a card, if able." Super-Shotgun: "… discards a card, or is defeated."
        r.card("swift-red-2b", Shotgun(orDefeated = false))
        r.card("swift-red-3b", Shotgun(orDefeated = true))
        // Suppress / Pin Down: "An enemy hero in radius who is not adjacent to terrain discards a card,
        // if able." Killing Ground: "… discards a card, or is defeated."
        r.card("swift-green-1", Suppress(orDefeated = false))
        r.card("swift-green-2a", Suppress(orDefeated = false))
        r.card("swift-green-3a", Suppress(orDefeated = true))
        // Mark for Death: "Move an enemy minion in radius up to 3 spaces to a space in radius. Next turn:
        // The first time an enemy minion in radius is defeated, gain 1 coin."
        r.card("swift-green-2b", Hunt(minions = 1))
        // Hunting Season: "Move up to two enemy minions in radius, up to 3 spaces each, to spaces in
        // radius. Next turn: The first two times an enemy minion in radius is defeated, gain 1 coin."
        r.card("swift-green-3b", Hunt(minions = 2))
        // Steam Jump: "Place yourself into a space in a straight line in radius. Push an enemy unit
        // adjacent to you up to 1 space." Assault Jump: "… up to 2 spaces." Drop Trooper: "… Push up to
        // two enemy units adjacent to you up to 2 spaces."
        r.card("swift-blue-1", Jump(units = 1, push = 1))
        r.card("swift-blue-2a", Jump(units = 1, push = 2))
        r.card("swift-blue-3a", Jump(units = 2, push = 2))
        // Delayed Jump: "End of turn: Place yourself into a space in radius not in a straight line from
        // you." Mobile Scout: "… You may then fast travel, if able."
        r.card("swift-blue-2b", DelayedJump(fastTravel = false))
        r.card("swift-blue-3b", DelayedJump(fastTravel = true))
        // Bullet Time (Ultimate): "After you resolve a basic card, you may perform the primary action on
        // that card; you cannot target the same enemy hero twice in the same turn this way."
        r.card("swift-purple-4", BulletTime)
    }

    // ---- "Perform the primary action of your rightmost resolved card" ---------------------------

    /**
     * The rightmost resolved card (p.7: Turn slots fill left to right) when its primary action can be
     * performed now: faceup (p.10: a facedown card "loses its type, color and actions"), not a Defense
     * card, allowed and its first instruction possible. [APP] The card performing it is skipped: when
     * Bullet Time performs an already resolved Reload!, "your rightmost resolved card" is the one before
     * it (rulings/swift.md 1).
     */
    private fun ActionScope.rightmostCard(): String? = g.resolvedCards(heroId).lastOrNull { it != cardId }

    private fun ActionScope.rightmostPerformable(filter: ((String) -> Boolean)? = null): String? {
        val card = rightmostCard() ?: return null
        if (g.cardTypeOf(heroId, card) == null) return null
        val kind = primaryKind(g.card(card))
        if (kind == ActionKind.DEFENSE || kind == ActionKind.NONE) return null
        if (g.data.registry.script(card)?.hasPrimary != true) return null
        if (!g.actionAllowed(heroId, card, kind, true)) return null
        return card.takeIf { g.primaryPossible(heroId, card, excluded, filter) }
    }

    private fun ActionScope.performRightmost(card: String, filter: ((String) -> Boolean)? = null) {
        g.performAction(
            heroId,
            card,
            primaryKind(g.card(card)),
            primary = true,
            excluded = excluded,
            repeatFilter = filter,
        )
    }

    private object Reload : CardScript {
        override fun primary(a: ActionScope) {
            val rightmost = a.rightmostPerformable()
            val minions = adjacentMinions(a)
            val labels =
                listOf("Perform the primary action of your rightmost resolved card", "Defeat a minion adjacent to you")
            val possible = listOfNotNull(0.takeIf { rightmost != null }, 1.takeIf { minions.isNotEmpty() })
            if (possible.isEmpty()) a.fizzle("no resolved card to perform and no minion adjacent")
            // "Choose one —": a bullet that cannot start is not offered (RULINGS 38).
            val pick = if (possible.size == 1) possible.single() else a.chooseOption("Choose one", labels)
            if (pick == 0) {
                a.performRightmost(rightmost!!)
            } else {
                val m = a.target(a.chooseUnit("Defeat a minion adjacent to you", minions)!!)
                a.g.defeatMinion(m, a.heroId)
            }
        }

        /** p.17 "Defeat": "You can never defeat a friendly unit", so enemy minions only. */
        private fun adjacentMinions(a: ActionScope): List<String> =
            a.unitsWithin(1).filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
    }

    private object Backup : CardScript {
        override fun primary(a: ActionScope) {
            // "If you perform an Attack action this way, you cannot target minions."
            val rightmost = a.rightmostCard()
            val filter: ((String) -> Boolean)? =
                if (rightmost != null && primaryKind(a.g.card(rightmost)) == ActionKind.ATTACK) {
                    { id -> !a.g.isMinion(id) }
                } else {
                    null
                }
            val card =
                a.rightmostPerformable(filter) ?: a.fizzle("no resolved card whose primary action can be performed")
            a.performRightmost(card, filter)
        }
    }

    private object Bounce : CardScript {
        override fun primary(a: ActionScope) {
            // "Move 2 spaces in a straight line, ignoring obstacles" (exact: p.11, mandatory).
            a.moveSelf(2, straightLine = true, ignoreObstacles = true)
            // "if this card is already resolved as you perform this action, may repeat once." (FAQ: when
            // Reload! performs it from a Turn slot.)
            if (a.hero.card(a.cardId)?.zone ==
                Zone.RESOLVED
            ) {
                a.repeat(prompt = "Bounce is already resolved: repeat once?")
            }
        }
    }

    private fun ActionScope.straightLineTargets(min: Int, exactMax: Boolean): (String) -> Boolean = { id ->
        val me = hero.at
        val p = g.posOf(id)
        if (me == null || p == null) {
            false
        } else {
            val d = g.distance(me, p)
            HexGrid.inStraightLine(me, p) && (if (exactMax) d == range else d in min..range)
        }
    }

    private object Snipe : CardScript {
        override fun primary(a: ActionScope) {
            // "at maximum range": exactly the card's Range (items included, p.16).
            val ok = a.straightLineTargets(1, exactMax = true)
            val t = a.attackTarget("Target a unit at maximum range, and in a straight line", valid = ok) ?: return
            a.attack(t, valid = ok)
        }
    }

    private object PreparedShot : CardScript {
        override fun primary(a: ActionScope) {
            val ok = a.straightLineTargets(2, exactMax = false)
            val t = a.attackTarget("Target a unit in range, in a straight line, and not adjacent to you", valid = ok)
                ?: return
            a.attack(t, valid = ok)
        }
    }

    private class Shotgun(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.attackTarget("Target a unit in range") { a.inRange(it) } ?: return
            // "Before the attack: An enemy hero adjacent to the target discards a card, if able / , or is
            // defeated." The enemy hero is a target to find: "if able" makes only the discard optional
            // (goa2-faq, Energy Drain; RULINGS 53), so with no enemy hero next to the target the action
            // stops here, for both cards (p.10; rulings/swift.md 3).
            val tPos = a.g.posOf(t)!!
            val victims = a.g.unitsAdjacentTo(tPos).filter { id ->
                id != t && a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id)
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero adjacent to the target")
            val v = a.chooseUnit("An enemy hero adjacent to the target discards a card", victims)!!
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
            a.attack(t, valid = { a.inRange(it) })
        }
    }

    private class Suppress(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "not adjacent to terrain": no neighbouring space is terrain (water and rocks included, p.19).
            val victims = a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) &&
                    HexGrid.neighbors(a.g.posOf(id)!!).none { a.g.isTerrain(it) }
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero in radius away from terrain")
            val v = a.chooseUnit("An enemy hero in radius who is not adjacent to terrain discards a card", victims)!!
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
        }
    }

    /** Mark for Death and Hunting Season. */
    private class Hunt(private val minions: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Move (up to two) enemy minion(s) in radius up to 3 spaces (each) to a space in radius":
            // "up to" makes the step optional (p.10).
            val me = a.pos
            val inRadius: (Hex) -> Boolean = { h -> a.g.distance(me, h) in 1..a.radius }
            val moved = HashSet<String>()
            repeat(minions) { i ->
                val options = a.unitsWithin(a.radius).filter { id ->
                    id !in moved && a.g.isMinion(id) && a.isEnemy(id) && a.canTarget(id)
                }
                if (options.isEmpty()) return@repeat
                val prompt = if (minions == 1) {
                    "Move an enemy minion in radius up to 3 spaces"
                } else {
                    "Move an enemy minion in radius up to 3 spaces (${i + 1} of up to $minions)"
                }
                // Hunting Season's "up to two" may stop at any count; one minion moving 0 spaces is no move.
                val m = a.chooseUnit(prompt, options, optional = minions > 1) ?: return@repeat
                moved += m
                a.moveOther(m, 0, 3, endFilter = inRadius)
            }
            // "Next turn: The first (two) time(s) an enemy minion in radius is defeated, gain 1 coin."
            a.activeEffect(Timing.NEXT_TURN, data = mapOf(LEFT to "$minions"))
        }

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                if (t !is Trigger.MinionDefeated) return
                val owner = src.owner ?: return
                val e = src.effect ?: return
                val left = e.data[LEFT]?.toInt() ?: 0
                if (left <= 0 || t.team == g.teamOf(owner)) return
                val me = g.ownerPos(src) ?: return
                val at = t.at ?: return
                if (g.distance(me, at) !in 1..g.radiusOf(src)) return
                e.data[LEFT] = "${left - 1}"
                g.touch()
                g.gainCoins(owner, 1, g.cardName(src.cardId!!))
            }
        }
    }

    private class Jump(private val units: Int, private val push: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Place yourself into a space in a straight line in radius."
            val me = a.pos
            val spaces = a.g.within(me, a.radius).filter { HexGrid.inStraightLine(me, it) && a.g.isEmpty(it) }
            if (spaces.isEmpty() || !a.g.canPlace(a.heroId, a.heroId)) a.fizzle("no space to place yourself")
            val to = a.chooseSpace("Place yourself into a space in a straight line in radius", spaces)!!
            a.g.place(a.heroId, to, a.heroId)
            // "Push (up to two) enemy unit(s) adjacent to you up to N spaces." ("up to": optional, p.10.)
            val enemies = a.unitsWithin(1).filter { a.isEnemy(it) && a.canTarget(it) }
            if (enemies.isEmpty()) return
            val picked = if (units == 1) {
                listOf(a.chooseUnit("Push an enemy unit adjacent to you", enemies)!!)
            } else {
                a.chooseUpTo(units, "Push an enemy unit adjacent to you", enemies)
            }
            if (picked.isEmpty()) return
            // p.18: several targets "up to X spaces": the distance is chosen once and all move it.
            val d = a.chooseDistance((0..push).toList(), "Push how many spaces?")
            for (id in picked) a.pushAway(id, d)
        }
    }

    private class DelayedJump(private val fastTravel: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.END_OF_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun applyTimed(g: Game, src: Src) {
                val owner = src.owner ?: return
                val me = g.posOf(owner) ?: return
                // "Place yourself into a space in radius not in a straight line from you." (mandatory: if
                // no such space, the text stops and there is no Fast Travel.)
                val spaces = g.within(me, g.radiusOf(src)).filter { !HexGrid.inStraightLine(me, it) && g.isEmpty(it) }
                if (spaces.isEmpty() || !g.canPlace(owner, owner)) g.fizzle("no space to place yourself")
                val c = g.ask(
                    owner,
                    DecisionKind.CHOOSE_SPACE,
                    "End of turn: place yourself into a space in radius not in a straight line from you",
                    spaces.sortedHexes().map { Choice.Space(it) },
                    src.cardId,
                ) as Choice.Space
                g.place(owner, c.hex, owner)
                // "You may then fast travel, if able."
                if (fastTravel) g.fastTravel(owner, src.cardId, optional = true)
            }
        }
    }

    /**
     * Bullet Time. "you cannot target the same enemy hero twice in the same turn this way": the extra
     * primary action may not target an enemy hero Swift's actions already targeted this turn
     * (rulings/swift.md 4), recorded in the turn notes after each of her actions.
     */
    private object BulletTime : CardScript {
        override val hasPrimary: Boolean get() = false

        private const val NOTE = "swift.bulletTime.targeted:"

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                when (t) {
                    is Trigger.AfterAction -> if (t.heroId == owner) {
                        for (id in t.targets) {
                            val hero = g.heroBodyOf(id) ?: continue
                            if (g.isEnemy(owner, hero)) g.state.turnRecord.notes += NOTE + hero
                        }
                    }

                    is Trigger.CardResolved -> if (t.heroId == owner &&
                        g.isBasic(t.cardId)
                    ) {
                        extraPrimary(g, owner, t.cardId, src)
                    }

                    else -> Unit
                }
            }
        }

        private fun extraPrimary(g: Game, owner: String, cardId: String, src: Src) {
            if (g.posOf(owner) == null || g.cardTypeOf(owner, cardId) == null) return
            val kind = primaryKind(g.card(cardId))
            if (kind == ActionKind.DEFENSE || kind == ActionKind.NONE) return
            if (!g.actionAllowed(owner, cardId, kind, true)) return
            val excluded = g.unitsOnBoard().filter {
                g.heroBodyOf(it)?.let { h -> NOTE + h in g.state.turnRecord.notes } ==
                    true
            }.toSet()
            if (!g.primaryPossible(owner, cardId, excluded)) return
            val c = g.ask(
                owner,
                DecisionKind.CONFIRM,
                "Bullet Time: perform the primary action of ${g.cardName(cardId)} again?",
                listOf(Choice.Yes, Choice.Decline("Don't")),
                src.cardId,
                auto = false,
            )
            if (c !is Choice.Yes) return
            g.performAction(owner, cardId, kind, primary = true, excluded = excluded)
        }
    }

    private const val LEFT = "left"
}
