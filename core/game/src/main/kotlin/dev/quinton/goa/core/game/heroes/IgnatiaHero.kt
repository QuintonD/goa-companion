package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inStraightLine
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.script
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.removeMinion
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.tokensAdjacentTo
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.match.Team

/**
 * Ignatia the Mad. Card text quoted verbatim above each registration (`catalog/heroes/ignatia.json`);
 * `IgnatiaTest` follows it clause by clause. Readings the text leaves open are in
 * `core/game/rulings/ignatia.md`.
 *
 * Most cards print two texts, one per side of the Tie Breaker coin (p.5 image: the blue star side
 * is the Blue team's, the orange gear side the Red team's): the text matching the coin applies
 * (Chaos Bolt's reminder), Equilibrium lets her pick either for a round.
 */
object IgnatiaHero : HeroModule {
    override val heroId: String = "ignatia"
    override val complete: Boolean = true

    /** The two sides of the Tie Breaker coin as printed on her cards. */
    enum class Side(val label: String) { BLUE("blue"), ORANGE("orange") }

    const val MAGMA = "token_magma"
    private const val EQUILIBRIUM = "ignatia-silver"
    private const val SIDE = "ignatia.side"
    private const val CHAOS_USED = "ignatia.chaosIncarnate"

    override fun register(r: Registry) {
        // [APP] No supply count is known for Magma tokens: not limited (rulings/ignatia.md).
        r.token(TokenKind(MAGMA, "Magma", supply = null))
        // Chaos Bolt: "{tiebreaker_blue}: Target a minion adjacent to you. {tiebreaker_orange}: Target a
        // hero in range. (Apply the text matching the symbol on the Tie Breaker coin.)"
        r.card(
            "ignatia-gold",
            Bolt(
                blue = { a, id -> a.g.isMinion(id) && a.isAdjacent(id) },
                orange = { a, id -> a.g.isHero(id) && a.inRange(id) },
                labels = "Target a minion adjacent to you" to "Target a hero in range",
            ),
        )
        // Unreliable Jolt (Handicap): "{tiebreaker_blue}: Target a hero adjacent to you.
        // {tiebreaker_orange}: Target a hero in range not adjacent to you."
        r.card(
            "ignatia-handicap",
            Bolt(
                blue = { a, id -> a.g.isHero(id) && a.isAdjacent(id) },
                orange = { a, id -> a.g.isHero(id) && a.inRange(id) && !a.isAdjacent(id) },
                labels = "Target a hero adjacent to you" to "Target a hero in range not adjacent to you",
            ),
        )
        // Equilibrium: "This round: Each time you perform or repeat a primary action, you may apply
        // either {tiebreaker_blue} or {tiebreaker_orange} card text, regardless of the Tie Breaker coin."
        r.card(EQUILIBRIUM, script { activeEffect(Timing.THIS_ROUND) })
        // Playing with Fire / Erratic Fireblast: "{tiebreaker_blue}: Target a unit in range not in a
        // straight line. {tiebreaker_orange}: Target a unit in range in a straight line."
        val notLine = { a: ActionScope, id: String -> a.inRange(id) && !a.inStraightLine(id) }
        val line = { a: ActionScope, id: String -> a.inRange(id) && a.inStraightLine(id) }
        val fireLabels = "Target a unit in range not in a straight line" to "Target a unit in range in a straight line"
        r.card("ignatia-red-1", Bolt(notLine, line, fireLabels))
        r.card("ignatia-red-2a", Bolt(notLine, line, fireLabels))
        // Loosely-Aimed Firebolts: "… {tiebreaker_orange}: Target a unit in range in a straight line.
        // May repeat once on a different hero." (the card scan prints the repeat in the orange box)
        r.card("ignatia-red-3a", Bolt(notLine, line, fireLabels, orangeRepeat = Repeat.DIFFERENT_HERO))
        // Crack of Doom: "{tiebreaker_blue}: Target a unit adjacent to you. {tiebreaker_orange}: Target a
        // unit at maximum range."
        val adjacent = { a: ActionScope, id: String -> a.isAdjacent(id) }
        val maxRange = { a: ActionScope, id: String -> a.inRange(id) && a.g.distance(a.pos, a.g.posOf(id)!!) == a.range }
        val doomLabels = "Target a unit adjacent to you" to "Target a unit at maximum range"
        r.card("ignatia-red-2b", Bolt(adjacent, maxRange, doomLabels))
        // Imminent Eruption: "{tiebreaker_blue}: Target a unit adjacent to you. May repeat once on a
        // minion. {tiebreaker_orange}: Target a unit at maximum range."
        r.card("ignatia-red-3b", Bolt(adjacent, maxRange, doomLabels, blueRepeat = Repeat.MINION))
        // Path of Ashes / Cinders / Flames: "{tiebreaker_blue}: Move up to 2 (3, 4) spaces in a straight
        // line. Place a {token_magma} Magma token in each empty space you moved through, or out of.
        // {tiebreaker_orange}: Place up to 2 (3, 4) Magma tokens in radius."
        r.card("ignatia-green-1", Path(2))
        r.card("ignatia-green-2a", Path(3))
        r.card("ignatia-green-3a", Path(4))
        // Abrupt Combustion / Spontaneous Immolation: "{tiebreaker_blue}: An enemy hero in radius adjacent
        // to a token or to a minion discards a card, if able. {tiebreaker_orange}: Remove an enemy minion
        // in radius adjacent to an enemy hero."
        r.card("ignatia-blue-1", Combustion(orDefeated = false, defeat = false))
        r.card("ignatia-blue-2a", Combustion(orDefeated = false, defeat = false))
        // Violent Conflagration: "{tiebreaker_blue}: … discards a card, or is defeated.
        // {tiebreaker_orange}: Defeat an enemy minion in radius adjacent to an enemy hero."
        r.card("ignatia-blue-3a", Combustion(orDefeated = true, defeat = true))
        // Unstable Portal: "{tiebreaker_blue}: Swap with a friendly unit in radius. {tiebreaker_orange}:
        // Swap with an enemy unit in radius."
        r.card("ignatia-green-2b", Portal(gate = false))
        // Chaos Gate: "{tiebreaker_blue}: Swap with a friendly unit in radius. You may move that unit 1
        // space. {tiebreaker_orange}: Swap with an enemy unit in radius. You may move 1 space."
        r.card("ignatia-green-3b", Portal(gate = true))
        // Searing Heat: "{tiebreaker_blue}: Move a friendly hero in radius 2 spaces in a straight line.
        // {tiebreaker_orange}: Move an enemy hero in radius 2 spaces in a straight line."
        r.card("ignatia-blue-2b", Heat(2, 2))
        // Scorching Blaze: "… 2 or 3 spaces in a straight line." (as Searing Heat)
        r.card("ignatia-blue-3b", Heat(2, 3))
        // Chaos Incarnate (Ultimate): "The first time each turn after you perform a primary action, you
        // may flip the Tie Breaker coin; if you do, you may perform that action again, choosing different
        // targets."
        r.card("ignatia-purple-4", ChaosIncarnate)
    }

    /** The side the coin shows: the Blue team's star, or the Red team's orange gear (p.5). */
    fun coinSide(g: Game): Side = if (g.state.tieBreaker == Team.BLUE) Side.BLUE else Side.ORANGE

    /** Equilibrium is in force for [heroId] (whoever performed it: NebKher's Mind Grip too). */
    private fun equilibrium(a: ActionScope): Boolean =
        a.g.state.effects.any { it.owner == a.heroId && it.cardId == EQUILIBRIUM && a.g.inForce(it) }

    /**
     * The text this action applies: the coin's, or with Equilibrium either one ("each time you perform
     * or repeat a primary action": asked once per action). A side whose first instruction can't be done
     * is not offered (RULINGS 38); with no side possible the coin's applies (and stops).
     */
    private fun ActionScope.side(possible: (ActionScope, Side) -> Boolean, labels: Pair<String, String>): Side {
        (notes[SIDE] as? Side)?.let { return it }
        val coin = coinSide(g)
        val side = if (!equilibrium(this)) {
            coin
        } else {
            val ok = Side.entries.filter { possible(this, it) }
            when (ok.size) {
                0 -> coin
                1 -> ok.single()
                else -> {
                    val i = chooseOption(
                        "Equilibrium: apply either text (the coin shows ${coin.label})",
                        listOf("Blue: ${labels.first}", "Orange: ${labels.second}"),
                    )!!
                    Side.entries[i]
                }
            }
        }
        notes[SIDE] = side
        return side
    }

    // ---- attacks -------------------------------------------------------------------------------

    private enum class Repeat { NONE, DIFFERENT_HERO, MINION }

    private class Bolt(
        private val blue: (ActionScope, String) -> Boolean,
        private val orange: (ActionScope, String) -> Boolean,
        private val labels: Pair<String, String>,
        private val blueRepeat: Repeat = Repeat.NONE,
        private val orangeRepeat: Repeat = Repeat.NONE,
    ) : CardScript {
        private fun valid(side: Side) = if (side == Side.BLUE) blue else orange

        private fun targets(a: ActionScope, side: Side): List<String> =
            a.g.unitsOnBoard().filter { a.g.isEnemy(a.heroId, it) && a.canTarget(it) && valid(side)(a, it) }

        override fun primary(a: ActionScope) {
            val side = a.side({ s, sd -> targets(s, sd).isNotEmpty() }, labels)
            val rule = valid(side)
            val prompt = if (side == Side.BLUE) labels.first else labels.second
            val t = a.attackTarget(prompt) { rule(a, it) } ?: return
            a.attack(t, valid = { rule(a, it) })
            if (a.isRepeat) return
            // "May repeat once on a different hero." / "May repeat once on a minion." (p.10: optional)
            when (if (side == Side.BLUE) blueRepeat else orangeRepeat) {
                Repeat.NONE -> Unit
                Repeat.DIFFERENT_HERO -> {
                    val filter = { id: String -> a.g.isHero(id) }
                    if (a.g.unitsOnBoard().any { a.isEnemy(it) && filter(it) && it !in a.targets && a.canTarget(it) }) {
                        a.repeat(differentTarget = true, filter = filter, prompt = "Repeat once on a different hero?")
                    }
                }
                Repeat.MINION -> {
                    val filter = { id: String -> a.g.isMinion(id) }
                    if (a.g.unitsOnBoard().any { a.isEnemy(it) && filter(it) && a.canTarget(it) }) {
                        a.repeat(filter = filter, prompt = "Repeat once on a minion?")
                    }
                }
            }
        }
    }

    // ---- skills --------------------------------------------------------------------------------

    private class Path(private val n: Int) : CardScript {
        private val labels = "Move up to $n spaces in a straight line, Magma behind you" to "Place up to $n Magma tokens in radius"

        private fun emptyInRadius(a: ActionScope) = a.g.within(a.pos, a.radius).filter { a.g.isEmpty(it) }

        override fun primary(a: ActionScope) {
            val side = a.side({ s, sd ->
                if (sd == Side.BLUE) s.canMoveOther(s.heroId, 1, n, straightLine = true) else emptyInRadius(s).isNotEmpty()
            }, labels)
            if (side == Side.BLUE) {
                // "Move up to N spaces in a straight line."
                val start = a.pos
                val path = a.moveSelf(0, n, straightLine = true)
                if (path.isEmpty()) return
                // "Place a Magma token in each empty space you moved through, or out of." (the start and
                // every space before the last one)
                for (h in listOf(start) + path.dropLast(1)) {
                    if (a.g.isEmpty(h)) a.g.placeToken(MAGMA, a.heroId, h)
                }
            } else {
                // "Place up to N Magma tokens in radius."
                for (i in 1..n) {
                    val spaces = emptyInRadius(a)
                    if (spaces.isEmpty()) return
                    val h = a.chooseSpace("Place a Magma token in radius ($i of up to $n)", spaces, optional = true)
                        ?: return
                    a.g.placeToken(MAGMA, a.heroId, h)
                }
            }
        }
    }

    private class Combustion(private val orDefeated: Boolean, private val defeat: Boolean) : CardScript {
        private val labels = "An enemy hero in radius adjacent to a token or to a minion discards a card" to
            (if (defeat) "Defeat" else "Remove") + " an enemy minion in radius adjacent to an enemy hero"

        /** "An enemy hero in radius adjacent to a token or to a minion" (presence of the token or minion, p.19). */
        private fun heroes(a: ActionScope) = a.unitsWithin(a.radius).filter { id ->
            val p = a.g.posOf(id)!!
            a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) &&
                (a.g.tokensAdjacentTo(p).isNotEmpty() || a.g.unitsAdjacentTo(p).any { a.g.isMinion(it) })
        }

        /** "An enemy minion in radius adjacent to an enemy hero" (presence of the hero). */
        private fun minions(a: ActionScope) = a.unitsWithin(a.radius).filter { id ->
            a.g.isMinion(id) && a.isEnemy(id) && a.canTarget(id) &&
                a.g.unitsAdjacentTo(a.g.posOf(id)!!).any { a.g.isHero(it) && a.isEnemy(it) }
        }

        override fun primary(a: ActionScope) {
            val side = a.side({ s, sd -> if (sd == Side.BLUE) heroes(s).isNotEmpty() else minions(s).isNotEmpty() }, labels)
            if (side == Side.BLUE) {
                val v = a.chooseUnit(labels.first, heroes(a)) ?: a.fizzle("no enemy hero in radius next to a token or a minion")
                if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
            } else {
                val m = a.chooseUnit(labels.second, minions(a)) ?: a.fizzle("no enemy minion in radius next to an enemy hero")
                a.target(m)
                if (defeat) a.g.defeatMinion(m, a.heroId) else a.g.removeMinion(m)
            }
        }
    }

    private class Portal(private val gate: Boolean) : CardScript {
        private val labels = "Swap with a friendly unit in radius" to "Swap with an enemy unit in radius"

        private fun units(a: ActionScope, side: Side) = a.unitsWithin(a.radius).filter { id ->
            (if (side == Side.BLUE) a.isFriendly(id) else a.isEnemy(id)) && a.canTarget(id) &&
                a.g.canSwap(a.heroId, id, a.heroId)
        }

        override fun primary(a: ActionScope) {
            val side = a.side({ s, sd -> units(s, sd).isNotEmpty() }, labels)
            val u = a.chooseUnit(if (side == Side.BLUE) labels.first else labels.second, units(a, side))
                ?: a.fizzle("no unit in radius to swap with")
            a.target(u)
            a.g.swap(a.heroId, u, a.heroId)
            if (!gate) return
            if (side == Side.BLUE) {
                // "You may move that unit 1 space."
                if (a.canMoveOther(u, 1)) a.moveOther(u, 1, optional = true)
            } else {
                // "You may move 1 space."
                a.moveSelf(1, optional = true)
            }
        }
    }

    private class Heat(private val min: Int, private val max: Int) : CardScript {
        private val n = if (min == max) "$min" else "$min or $max"
        private val labels = "Move a friendly hero in radius $n spaces in a straight line" to
            "Move an enemy hero in radius $n spaces in a straight line"

        private fun heroes(a: ActionScope, side: Side) = a.unitsWithin(a.radius).filter { id ->
            a.g.isHero(id) && (if (side == Side.BLUE) a.isFriendly(id) else a.isEnemy(id)) && a.canTarget(id) &&
                a.canMoveOther(id, min, max, straightLine = true)
        }

        override fun primary(a: ActionScope) {
            val side = a.side({ s, sd -> heroes(s, sd).isNotEmpty() }, labels)
            val h = a.chooseUnit(if (side == Side.BLUE) labels.first else labels.second, heroes(a, side))
                ?: a.fizzle("no hero in radius can be moved $n spaces in a straight line")
            a.moveOther(h, min, max, straightLine = true)
        }
    }

    // ---- Chaos Incarnate (Ultimate) -------------------------------------------------------------

    private object ChaosIncarnate : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                // A repeat is part of the action it repeats: the Ultimate reacts after the whole action.
                if (t !is Trigger.AfterAction || t.heroId != owner || !t.primary || t.repeat) return
                val cardId = t.cardId ?: return
                // "The first time each turn": that first primary action uses it, whatever she decides.
                if (CHAOS_USED in g.state.turnRecord.notes) return
                g.state.turnRecord.notes += CHAOS_USED
                if (g.posOf(owner) == null) return
                if (!confirm(g, owner, src, "Chaos Incarnate: flip the Tie Breaker coin?")) return
                g.state.tieBreaker = g.state.tieBreaker.opponent
                g.touch()
                g.note("${g.heroName(owner)} flips the Tie Breaker coin: ${Game.teamName(g.state.tieBreaker)} shows.")
                val kind = primaryKind(g.card(cardId))
                if (!g.actionAllowed(owner, cardId, kind, true)) return
                if (!confirm(g, owner, src, "Perform ${g.cardName(cardId)} again, choosing different targets?")) return
                g.performAction(owner, cardId, kind, primary = true, excluded = t.targets.toSet())
            }
        }

        private fun confirm(g: Game, owner: String, src: Src, prompt: String): Boolean = g.ask(
            owner,
            DecisionKind.CONFIRM,
            prompt,
            listOf(Choice.Yes, Choice.Decline("No")),
            src.cardId,
            auto = false,
        ) is Choice.Yes
    }
}
