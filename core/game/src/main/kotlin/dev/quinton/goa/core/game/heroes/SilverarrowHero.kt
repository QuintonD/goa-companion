package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.PassMode
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.radiusOf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.fastTravelDestinations
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.repeatForbidden
import dev.quinton.goa.core.game.engine.setPos
import dev.quinton.goa.core.game.engine.trigger
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.unitsWithin
import dev.quinton.goa.core.game.engine.zoneOf

/**
 * Silverarrow the Pathfinder. Each card's verbatim text (`catalog/heroes/silverarrow.json`) is quoted
 * above its registration; `SilverarrowTest` follows it clause by clause with the hero's FAQ.
 * Readings: `core/game/rulings/silverarrow.md`.
 */
object SilverarrowHero : HeroModule {
    override val heroId: String = "silverarrow"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Shoot and Scoot: "Target a unit at maximum range. After the attack: If able, you may fast
        // travel to an adjacent zone."
        r.card("silverarrow-gold", MaxRangeShot(heroOnly = false, scoot = true, rain = false))
        // Keep the Distance (Handicap): "Target a hero at maximum range. After the attack: If able, you
        // may fast travel to an adjacent zone."
        r.card("silverarrow-handicap", MaxRangeShot(heroOnly = true, scoot = true, rain = false))
        // Trailblazer: "You may fast travel, if able. This round: You and friendly heroes in radius may
        // ignore obstacles while performing movement actions."
        r.card("silverarrow-silver", Trailblazer)
        // Snap Shot / Opportunity Shot / Clear Shot: "Choose one — • Target a unit in range, which is not
        // adjacent to any other unit. • Target a unit adjacent to you."
        r.card("silverarrow-red-1", SnapShot)
        r.card("silverarrow-red-2a", SnapShot)
        r.card("silverarrow-red-3a", SnapShot)
        // Long Shot: "Target a unit at maximum range."
        r.card("silverarrow-red-2b", MaxRangeShot(heroOnly = false, scoot = false, rain = false))
        // Rain of Arrows: "Target a unit at maximum range. If you target a hero, repeat once on a different
        // hero; if you do, may repeat once on a minion."
        r.card("silverarrow-red-3b", MaxRangeShot(heroOnly = false, scoot = false, rain = true))
        // Disorient: "Move an enemy unit adjacent to you 1 space; if you do, you may move 1 space."
        r.card("silverarrow-green-1", Disorient)
        // Divert Attention / Lead Astray: "Move an enemy unit adjacent to you up to 2 (3) spaces; if you do,
        // move up to that number of spaces in a straight line."
        r.card("silverarrow-green-2a", Divert(max = 2))
        r.card("silverarrow-green-3a", Divert(max = 3))
        // Fae Healing / Nature's Blessing: "A hero in radius may retrieve a discarded card; if they do, that
        // hero gains 1 (2) coin(s)."
        r.card("silverarrow-green-2b", Healing(coins = 1))
        r.card("silverarrow-green-3b", Healing(coins = 2))
        // Grasping Roots / Entangling Vines / Grappling Branches: "This turn: Enemy heroes in radius cannot
        // fast travel, or move more than 1 space with a movement action."
        r.card("silverarrow-blue-1", Roots)
        r.card("silverarrow-blue-2a", Roots)
        r.card("silverarrow-blue-3a", Roots)
        // Warning Shot: "End of turn: An enemy hero in radius discards a card, if able." Treetop Sentinel:
        // "End of turn: An enemy hero in radius discards a card, or is defeated."
        r.card("silverarrow-blue-2b", Warning(orDefeated = false))
        r.card("silverarrow-blue-3b", Warning(orDefeated = true))
        // Wild Hunt (Ultimate): "Each time before you perform an action, you may move 2 spaces in a
        // straight line."
        r.card("silverarrow-purple-4", WildHunt)
    }

    // ---- shared helpers ------------------------------------------------------------------------

    /** "At maximum range": exactly as far as the card's Range value (items included, p.9, p.16). */
    private fun ActionScope.atMaximumRange(id: String): Boolean {
        val me = hero.at ?: return false
        val p = g.posOf(id) ?: return false
        return range > 0 && g.distance(me, p) == range
    }

    /**
     * A Fast Travel in card text (p.11: "If a card text allows you to Fast Travel as part of another
     * action, this Fast Travel is subject to the same restrictions"): no enemy units in the Zone you
     * leave or enter, an empty destination. It does not replace a Movement action (FAQ: possible "even
     * if she cannot move due to another effect"), so only effects against fast travel or placement stop
     * it. [adjacentOnly]: "to an adjacent zone" leaves the current Zone. Optional ("you may").
     */
    private fun ActionScope.textFastTravel(adjacentOnly: Boolean) {
        val me = hero.at ?: return
        if (g.sources().any { (b, s) -> b.forbidsFastTravel(g, s, heroId) }) return
        if (!g.canDisplace(heroId, Displacement.PLACE, heroId)) return
        val zone = g.zoneOf(me)
        val dests = g.fastTravelDestinations(heroId).filter { !adjacentOnly || g.zoneOf(it) != zone }
        if (dests.isEmpty()) return
        val answer = g.ask(
            heroId,
            DecisionKind.CHOOSE_SPACE,
            if (adjacentOnly) "You may fast travel to an adjacent zone" else "You may fast travel",
            dests.map { Choice.Space(it) as Choice } + Choice.Decline("Don't fast travel"),
            cardId,
            DecisionContext(subjectId = heroId),
        )
        if (answer !is Choice.Space) return
        g.setPos(heroId, answer.hex)
        g.emit(GameEvent.Placed(heroId, me, answer.hex, "${g.heroName(heroId)} fast travels to ${g.spaceName(answer.hex)}."))
        g.trigger(Trigger.Placed(heroId, me, answer.hex, heroId))
    }

    // ---- cards ---------------------------------------------------------------------------------

    /** Shoot and Scoot, Keep the Distance, Long Shot, Rain of Arrows. */
    private class MaxRangeShot(
        private val heroOnly: Boolean,
        private val scoot: Boolean,
        private val rain: Boolean,
    ) : CardScript {
        override fun primary(a: ActionScope) {
            val what = if (heroOnly) "hero" else "unit"
            val valid: (String) -> Boolean = { a.atMaximumRange(it) && (!heroOnly || a.g.isHero(it)) }
            val t = a.attackTarget("Target a $what at maximum range", valid = valid) ?: return
            val r = a.attack(t, valid = valid)
            // "After the attack: If able, you may fast travel to an adjacent zone." (RULINGS 18: not when
            // the attack did not connect.)
            if (scoot && r.connected) a.textFastTravel(adjacentOnly = true)
            if (rain) rainOfArrows(a, t)
        }

        /**
         * "If you target a hero, repeat once on a different hero; if you do, may repeat once on a minion."
         * The first repeat is mandatory (no "may"); it fails when no different hero is at maximum range.
         */
        private fun rainOfArrows(a: ActionScope, first: String) {
            if (a.isRepeat || !a.g.isHero(first) || a.g.repeatForbidden(a.heroId)) return
            val onHero = a.g.performAction(
                a.heroId,
                a.cardId,
                a.kind,
                primary = true,
                isRepeat = true,
                excluded = a.excluded + a.targets,
                repeatFilter = { a.g.isHero(it) },
            ) ?: return
            a.targets.addAll(onHero.targets.filter { it !in a.targets })
            if (onHero.targets.none { a.g.isHero(it) }) return
            if (!a.confirm("Repeat once on a minion?", decline = "Don't repeat")) return
            a.g.performAction(
                a.heroId,
                a.cardId,
                a.kind,
                primary = true,
                isRepeat = true,
                excluded = a.excluded,
                repeatFilter = { a.g.isMinion(it) },
            )
        }
    }

    private object Trailblazer : CardScript {
        override fun primary(a: ActionScope) {
            // "You may fast travel, if able."
            a.textFastTravel(adjacentOnly = false)
            // "This round: You and friendly heroes in radius may ignore obstacles while performing movement actions."
            a.activeEffect(Timing.THIS_ROUND)
        }

        /** Read when a move is set up, i.e. at the start of the movement action (p.11), from her space now. */
        override val behavior: Behavior = object : Behavior {
            override fun passMode(g: Game, src: Src, q: MoveQuery): PassMode? {
                val owner = src.owner ?: return null
                if (!q.movementAction) return null
                val mover = g.heroBodyOf(q.unitId) ?: return null
                val covered = mover == owner ||
                    (g.isFriendly(owner, q.unitId) && g.inRadiusOf(src, q.unitId) && g.affectedBy(src, q.unitId))
                return if (covered) PassMode.IGNORE_OBSTACLES else null
            }
        }
    }

    private object SnapShot : CardScript {
        override fun primary(a: ActionScope) {
            // • "Target a unit in range, which is not adjacent to any other unit." (Presence: any unit,
            // Silverarrow and immune units included.)
            val alone: (String) -> Boolean = { id ->
                val p = a.g.posOf(id)
                p != null && a.inRangeOf(id) && a.g.unitsAdjacentTo(p).isEmpty()
            }
            // • "Target a unit adjacent to you."
            val adjacent: (String) -> Boolean = { a.isAdjacent(it) }
            val bullets = listOf(alone, adjacent)
            val labels = listOf(
                "Target a unit in range, which is not adjacent to any other unit",
                "Target a unit adjacent to you",
            )
            val possible = bullets.indices.filter { i ->
                a.g.unitsOnBoard().any { a.isEnemy(it) && a.canTarget(it, ActionKind.ATTACK) && bullets[i](it) }
            }
            // RULINGS 38: a bullet that cannot start is not offered; with none the attack has no target.
            val pick = when (possible.size) {
                0 -> 0
                1 -> possible.single()
                else -> a.chooseOption("Choose one", labels)!!
            }
            val t = a.attackTarget(labels[pick], valid = bullets[pick]) ?: return
            a.attack(t, valid = bullets[pick])
        }
    }

    private object Disorient : CardScript {
        override fun primary(a: ActionScope) {
            // "Move an enemy unit adjacent to you 1 space" (mandatory).
            val targets = a.unitsWithin(1).filter { id ->
                a.isEnemy(id) && a.canTarget(id) && a.g.canDisplace(id, Displacement.MOVE, a.heroId) &&
                    a.canMoveOther(id, 1)
            }
            if (targets.isEmpty()) a.fizzle("no enemy unit adjacent that can be moved 1 space")
            val t = a.chooseUnit("Move an enemy unit adjacent to you 1 space", targets)!!
            val path = a.moveOther(t, 1)
            // "if you do, you may move 1 space."
            if (path.isNotEmpty()) a.moveSelf(1, optional = true)
        }
    }

    private class Divert(private val max: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Move an enemy unit adjacent to you up to N spaces" — the target is needed; the distance is up to N.
            val targets = a.unitsWithin(1).filter { id ->
                a.isEnemy(id) && a.canTarget(id) && a.g.canDisplace(id, Displacement.MOVE, a.heroId) &&
                    a.canMoveOther(id, 1, max)
            }
            if (targets.isEmpty()) a.fizzle("no enemy unit adjacent that can be moved")
            val t = a.chooseUnit("Move an enemy unit adjacent to you up to $max spaces", targets)!!
            val path = a.moveOther(t, 0, max)
            // "if you do, move up to that number of spaces in a straight line."
            if (path.isNotEmpty()) a.moveSelf(0, path.size, straightLine = true)
        }
    }

    private class Healing(private val coins: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "A hero in radius" (never you, p.10), friend or enemy; Silverarrow chooses it.
            val heroes = a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.canTarget(it) }
                .mapNotNull { a.g.heroBodyOf(it) }.filter { it != a.heroId }.distinct()
            if (heroes.isEmpty()) a.fizzle("no hero in radius")
            val h = a.target(a.chooseUnit("A hero in radius may retrieve a discarded card", heroes)!!)
            // "may retrieve a discarded card" — that hero decides; "if they do, that hero gains N coin(s)".
            if (a.mayRetrieve(who = h) != null) a.g.gainCoins(h, coins, a.card.name)
        }
    }

    private object Roots : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            private fun applies(g: Game, src: Src, hero: String): Boolean {
                val owner = src.owner ?: return false
                if (!g.isHero(hero) || !g.isEnemy(owner, hero)) return false
                return g.inRadiusOf(src, hero, g.radiusOf(src)) && g.affectedBy(src, hero)
            }

            /** FAQ: "movement restrictions are checked at the beginning of the movement" (p.11). */
            override fun movementCap(g: Game, src: Src, q: MoveQuery): Int? =
                if (q.movementAction && applies(g, src, q.unitId)) 1 else null

            override fun forbidsFastTravel(g: Game, src: Src, heroId: String): Boolean = applies(g, src, heroId)
        }
    }

    private class Warning(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.END_OF_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            /** "End of turn: An enemy hero in radius discards a card, if able (or is defeated)." */
            override fun applyTimed(g: Game, src: Src) {
                val owner = src.owner ?: return
                val cardId = src.cardId ?: return
                val at = g.posOf(owner) ?: return
                // Common FAQ: a hero immune "this turn" is still immune at the end of the turn.
                val victims = g.unitsWithin(at, g.radiusOf(src)).filter { id ->
                    g.isHero(id) && g.isEnemy(owner, id) && g.canTarget(id, owner, ActionKind.SKILL, cardId)
                }
                if (victims.isEmpty()) return
                val c = g.ask(
                    owner,
                    DecisionKind.CHOOSE_UNIT,
                    "End of turn: an enemy hero in radius discards a card${if (orDefeated) ", or is defeated" else ""}",
                    victims.map { Choice.Unit(it) },
                    cardId,
                ) as Choice.Unit
                g.forceDiscard(c.unitId, owner, orDefeated = orDefeated, sourceCardId = cardId)
            }
        }
    }

    private object WildHunt : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            /**
             * "Each time before you perform an action" — every action, the Defense action included (p.14),
             * each repeat too (p.10: "Treat each instance of repeat as a separate action"); not a
             * Movement action, so movement limits do not apply ("you may move 2 spaces": exactly 2).
             */
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.BeforeAction || t.heroId != owner) return
                if (g.posOf(owner) == null) return
                val spec = MoveSpec(owner, 2, 2, straightLine = true)
                if (!g.canMove(spec)) return
                g.move(spec, owner, "Wild Hunt: you may move 2 spaces in a straight line", optional = true, src.cardId)
            }
        }
    }

    private fun ActionScope.inRangeOf(id: String): Boolean {
        val me = hero.at ?: return false
        val p = g.posOf(id) ?: return false
        return g.distance(me, p) in 1..range
    }
}
