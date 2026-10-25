package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MinionBattleQuery
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseUpTo
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.tokensWithin
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.mayPlaceToken
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.tokensAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Wuk the Grovekeeper. Card text quoted verbatim above each registration (`catalog/heroes/wuk.json`);
 * `WukTest` follows it clause by clause with the FAQ. Readings: `core/game/rulings/wuk.md`.
 */
object WukHero : HeroModule {
    override val heroId: String = "wuk"
    override val complete: Boolean = true

    /** The Tree token kind (`{token_tree}` in the card text). */
    const val TREE: String = "token_tree"

    override fun register(r: Registry) {
        // Mystic Saplings: "Tree tokens are not removed at the end of round." Three Tree tokens in the box
        // (rulings/wuk.md 1); p.18: with none left in the supply, one is first taken from the board.
        r.token(TokenKind(TREE, "Tree", supply = 3, removeAtEndOfRound = false))
        // Tree Slam: "Choose one — • Target a minion adjacent to you. • Remove a Tree token adjacent to
        // you. Target a unit in range."
        r.card("wuk-gold", TreeSlam(heroOnly = false))
        // Makeshift Weapon (Handicap): "Choose one — • Target a hero adjacent to you. • Remove a Tree
        // token adjacent to you. Target a hero in range."
        r.card("wuk-handicap", TreeSlam(heroOnly = true))
        // Mystic Saplings: "Place up to 3 Tree tokens in radius; Tree tokens are not removed at the end
        // of round."
        r.card("wuk-silver", MysticSaplings)
        // Nature's Protector / Nature's Guardian: "Choose one — • Target a hero adjacent to you.
        // • Target a unit in range adjacent to a Tree token."
        r.card("wuk-red-1", Nature(both = false))
        r.card("wuk-red-2a", Nature(both = false))
        // Nature's Champion: "Choose one, or both, on different targets — • Target a hero adjacent to
        // you. • Target a unit in range adjacent to a Tree token."
        r.card("wuk-red-3a", Nature(both = true))
        // Toss Away / Mighty Throw: "Place a token, or an enemy unit, adjacent to you into a space in
        // range." Monstrous Throw: "… May repeat once."
        r.card("wuk-green-1", Toss(repeat = false))
        r.card("wuk-green-2a", Toss(repeat = false))
        r.card("wuk-green-3a", Toss(repeat = true))
        // Gifts of Nature: "Remove a Tree token in radius. You may retrieve a discarded card."
        r.card("wuk-blue-1", Gifts(Retrieve.SELF))
        // Tree of Plenty: "Remove a Tree token in radius. Choose one — • You may retrieve a discarded
        // card. • A friendly hero in radius may retrieve a discarded card."
        r.card("wuk-blue-2a", Gifts(Retrieve.ONE))
        // Abundance: "Remove a Tree token in radius. Choose one or both — • You may retrieve a discarded
        // card. • A friendly hero in radius may retrieve a discarded card."
        r.card("wuk-blue-3a", Gifts(Retrieve.ONE_OR_BOTH))
        // Claim Dominance: "This round: Up to 1 enemy minion adjacent to you does not count towards the
        // minion total during minion battle, regardless of immunity." Assert Dominance: "Up to 2 enemy
        // minions adjacent to you do not count …"
        r.card("wuk-blue-2b", Dominance(1))
        r.card("wuk-blue-3b", Dominance(2))
        // Trample: "If you move in a straight line: You may ignore obstacles; each enemy hero you moved
        // through discards a card, or is defeated; defeat up to one minion you moved through." Angry
        // Stampede: "… defeat up to two minions you moved through."
        r.card("wuk-red-2b", Trample(minions = 1))
        r.card("wuk-red-3b", Trample(minions = 2))
        // Into the Canopy: "Choose one — • Swap with a Tree token in radius. • Swap a friendly unit in
        // radius with a Tree token in radius." Treetop Ride: "Choose up to two times — …" (same bullets)
        r.card("wuk-green-2b", Canopy(times = 1))
        r.card("wuk-green-3b", Canopy(times = 2))
        // March of Nature (Ultimate): "Each time after you resolve a card, you may place a Tree token in
        // radius."
        r.card("wuk-purple-4", MarchOfNature)
    }

    // ---- shared helpers ------------------------------------------------------------------------

    private fun Game.isTree(id: String): Boolean = state.piece(id)?.kind == TREE

    /** Tree tokens 1..[n] spaces from the acting hero that this action may affect. */
    private fun ActionScope.trees(n: Int): List<String> = tokensWithin(n).filter { g.isTree(it) && canTarget(it) }

    /** "adjacent to a Tree token" (presence of a token, p.19). */
    private fun Game.nextToTree(id: String): Boolean {
        val p = posOf(id) ?: return false
        return tokensAdjacentTo(p).any { isTree(it) }
    }

    /** A bullet point (p.10 "Modal Actions"): [index] is its place on the card, top to bottom. */
    private class Bullet(val index: Int, val label: String, val possible: () -> Boolean, val run: () -> Unit)

    /**
     * Ask for one of [bullets]; only bullets that can start are offered and a single one is not asked
     * (RULINGS 38). Null when none is possible.
     */
    private fun ActionScope.pick(prompt: String, bullets: List<Bullet>, optional: Boolean): Bullet? {
        val ok = bullets.filter { it.possible() }
        if (ok.isEmpty()) return null
        if (ok.size == 1 && !optional) return ok.single()
        val opts = ok.map { Choice.Option(it.index, it.label) as Choice } +
            if (optional) listOf(Choice.Decline("Done")) else emptyList()
        val c = g.ask(heroId, DecisionKind.CHOOSE_OPTION, prompt, opts, cardId) as? Choice.Option ?: return null
        return ok.first { it.index == c.index }
    }

    /** "Choose one —": a mandatory step (p.10): with no bullet possible, the action stops. */
    private fun ActionScope.chooseOne(bullets: List<Bullet>): Bullet {
        val b = pick("Choose one", bullets, optional = false) ?: fizzle("no option can be done")
        b.run()
        return b
    }

    /** "Choose one or both —": one bullet, then the other if the hero wants (and it can be done). */
    private fun ActionScope.chooseOneOrBoth(bullets: List<Bullet>) {
        val first = chooseOne(bullets)
        val rest = bullets.filter { it !== first }
        pick("Choose the other option too?", rest, optional = true)?.run()
    }

    /**
     * "Choose up to N times —" (p.10): any bullets, the same one again included, each time only those
     * that can be done; the hero may stop at any time.
     */
    private fun ActionScope.chooseUpToTimes(printed: Int, bullets: List<Bullet>) {
        val limit = choiceLimit(printed, upTo = true)
        for (i in 1..limit) {
            val b = pick("Choose an option ($i of up to $limit)", bullets, optional = true) ?: return
            b.run()
        }
    }

    // ---- cards ---------------------------------------------------------------------------------

    private class TreeSlam(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val adjacent = { id: String -> a.isAdjacent(id) && (if (heroOnly) a.g.isHero(id) else a.g.isMinion(id)) }
            val inRange = { id: String -> a.inRange(id) && (!heroOnly || a.g.isHero(id)) }
            val who = if (heroOnly) "hero" else "unit"
            val first = Bullet(
                0,
                if (heroOnly) "Target a hero adjacent to you" else "Target a minion adjacent to you",
                { a.attackable(adjacent) },
                {
                    val t = a.attackTarget("Target a ${if (heroOnly) "hero" else "minion"} adjacent to you") {
                        adjacent(it)
                    }!!
                    a.attack(t, valid = adjacent)
                },
            )
            val second = Bullet(
                1,
                "Remove a Tree token adjacent to you. Target a $who in range",
                { a.trees(1).isNotEmpty() },
                {
                    // "Remove a Tree token adjacent to you." (mandatory, the bullet's first step)
                    val tree = a.target(a.chooseToken("Remove a Tree token adjacent to you", a.trees(1))!!)
                    a.g.removePiece(tree)
                    // "Target a unit (hero) in range." (mandatory: with none, the action stops here)
                    val t = a.attackTarget("Target a $who in range") { inRange(it) }!!
                    a.attack(t, valid = inRange)
                },
            )
            a.chooseOne(listOf(first, second))
        }
    }

    /** Is there an enemy unit this attack may target that matches [valid]? */
    private fun ActionScope.attackable(valid: (String) -> Boolean, except: Set<String> = emptySet()): Boolean =
        g.unitsOnBoard().any { it !in except && isEnemy(it) && canTarget(it, ActionKind.ATTACK) && valid(it) }

    private object MysticSaplings : CardScript {
        override fun primary(a: ActionScope) {
            // "Place up to 3 Tree tokens in radius" — one at a time, into empty spaces (p.18 "Place"); with
            // the supply empty, a Tree token is first taken from the board (p.18).
            for (i in 1..3) {
                a.g.mayPlaceToken(
                    TREE,
                    a.heroId,
                    a.heroId,
                    a.g.within(a.pos, a.radius),
                    "Place a Tree token in radius ($i of up to 3)",
                    a.cardId,
                ) ?: return
            }
        }
    }

    private class Nature(private val both: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val used = LinkedHashSet<String>()
            val heroAdjacent = { id: String -> a.g.isHero(id) && a.isAdjacent(id) }
            val nearTree = { id: String -> a.inRange(id) && a.g.nextToTree(id) }
            fun bullet(index: Int, label: String, valid: (String) -> Boolean) = Bullet(
                index,
                label,
                // "on different targets" (Nature's Champion): the second attack may not pick the first target.
                { a.attackable(valid, used) },
                {
                    val t = a.attackTarget(label) { it !in used && valid(it) }!!
                    used += t
                    a.attack(t, valid = valid)
                },
            )
            val bullets = listOf(
                bullet(0, "Target a hero adjacent to you", heroAdjacent),
                bullet(1, "Target a unit in range adjacent to a Tree token", nearTree),
            )
            if (both) a.chooseOneOrBoth(bullets) else a.chooseOne(bullets)
        }
    }

    private class Toss(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // Empty for the unit placed (p.18 "Place"; Takahide's blades make spaces obstacles to enemy units).
            fun spaces(t: String) = a.g.within(a.pos, a.range).filter { it != a.g.posOf(t) && a.g.isEmpty(it, t) }
            // "a token, or an enemy unit, adjacent to you" — any token; never a friendly unit.
            val objects = (a.unitsWithin(1).filter { a.isEnemy(it) } + a.tokensWithin(1)).filter {
                a.canTarget(it) && a.g.canPlace(it, a.heroId) && spaces(it).isNotEmpty()
            }
            if (objects.isEmpty()) a.fizzle("no token or enemy unit adjacent can be placed")
            val t = a.target(a.chooseObject("Place a token, or an enemy unit, adjacent to you", objects)!!)
            // "into a space in range" — an empty space (p.18), 1..Range from you.
            val h = a.chooseSpace("Place ${a.g.name(t)} into a space in range", spaces(t))!!
            a.g.place(t, h, a.heroId)
            if (repeat) a.repeat()
        }
    }

    private enum class Retrieve { SELF, ONE, ONE_OR_BOTH }

    private class Gifts(private val retrieve: Retrieve) : CardScript {
        override fun primary(a: ActionScope) {
            // "Remove a Tree token in radius." Mandatory: without one the action stops.
            val trees = a.trees(a.radius)
            if (trees.isEmpty()) a.fizzle("no Tree token in radius")
            val tree = a.target(a.chooseToken("Remove a Tree token in radius", trees)!!)
            a.g.removePiece(tree)
            if (retrieve == Retrieve.SELF) {
                a.mayRetrieve()
                return
            }
            val friends = {
                a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.isFriendly(it) && a.canTarget(it) }
                    .filter { a.g.discardPile(a.g.heroBodyOf(it)!!).isNotEmpty() }
            }
            val bullets = listOf(
                Bullet(0, "You may retrieve a discarded card", { a.g.discardPile(a.heroId).isNotEmpty() }) {
                    a.mayRetrieve()
                },
                Bullet(1, "A friendly hero in radius may retrieve a discarded card", { friends().isNotEmpty() }) {
                    val f = a.target(
                        a.chooseUnit("A friendly hero in radius may retrieve a discarded card", friends())!!,
                    )
                    a.mayRetrieve(who = a.g.heroBodyOf(f)!!)
                },
            )
            // Both bullets are "may": when neither can do anything, the card simply ends.
            if (bullets.none { it.possible() }) return
            if (retrieve == Retrieve.ONE) a.chooseOne(bullets) else a.chooseOneOrBoth(bullets)
        }
    }

    /**
     * Claim / Assert Dominance. [APP] rulings/wuk.md: "adjacent to you" is read at the Minion Battle
     * from Wuk's space then; Wuk picks which (up to [count]); only minion miniatures, not heroes that
     * count as minions (Brogan's Ultimate); "regardless of immunity" = heavy minions too.
     */
    private class Dominance(private val count: Int) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_ROUND)
        }

        override val behavior: Behavior = object : Behavior {
            override fun minionBattle(g: Game, src: Src, q: MinionBattleQuery) {
                val owner = src.owner ?: return
                val at = g.posOf(owner) ?: return
                val team = g.teamOf(owner) ?: return
                val candidates = q.participants.filter { p ->
                    !p.virtual && p.team != team && g.state.minion(p.id)?.at?.let { g.distance(at, it) == 1 } == true
                }.map { it.id }
                val dropped = ArrayList<String>()
                while (dropped.size < count) {
                    val left = candidates.filter { it !in dropped }
                    if (left.isEmpty()) break
                    val opts =
                        left.map { Choice.Unit(it) as Choice } +
                            Choice.Decline(if (dropped.isEmpty()) "None" else "Done")
                    val c = g.ask(
                        owner,
                        DecisionKind.CHOOSE_UNIT,
                        "${g.cardName(src.cardId!!)}: an enemy minion adjacent to you does not count " +
                            "(${dropped.size + 1} of up to $count)",
                        opts,
                        src.cardId,
                    )
                    if (c !is Choice.Unit) break
                    dropped += c.unitId
                }
                if (dropped.isEmpty()) return
                q.participants.removeAll { it.id in dropped }
                g.note(
                    "${dropped.joinToString(", ") {
                        g.name(it)
                    }} do${if (dropped.size == 1) "es" else ""} not count in the Minion Battle.",
                )
            }
        }
    }

    private class Trample(private val minions: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "If you move in a straight line: You may ignore obstacles" — chosen before moving; moving
            // normally (any path) gives no text effect, as a straight move can't pass units without it.
            val straight = a.confirm("Move in a straight line, ignoring obstacles?", decline = "Move normally")
            if (!straight) {
                a.movementAction()
                return
            }
            val path = a.movementAction(ignoreObstacles = true, straightLine = true)
            // The units in the spaces moved through (start and end excluded).
            val through = path.dropLast(1).mapNotNull { a.g.occupant(it) }.filter { a.g.isUnit(it) }
            // "each enemy hero you moved through discards a card, or is defeated" — all of them. Razzle FAQ:
            // during an action each of her figures is "another hero", so every figure moved through makes
            // her discard (figures gone with her defeat are skipped).
            val heroes = through.filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            for (h in heroes) {
                if (a.g.posOf(h) != null) a.discardOrDefeat(h)
            }
            // "defeat up to N minions you moved through" — FAQ: chosen at the same time, so a heavy minion
            // still immune when they are chosen cannot be defeated.
            val candidates = through.filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
            if (candidates.isEmpty()) return
            val picked = a.chooseUpTo(minions, "Defeat a minion you moved through", candidates)
            for (m in picked) {
                a.target(m)
                a.g.defeatMinion(m, a.heroId)
            }
        }
    }

    private class Canopy(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            fun trees() = a.trees(a.radius)
            fun selfSwappable() = trees().filter { a.g.canSwap(a.heroId, it, a.heroId) }
            fun friends() = a.unitsWithin(a.radius).filter { a.isFriendly(it) && a.canTarget(it) }
                .filter { f -> trees().any { a.g.canSwap(f, it, a.heroId) } }
            val bullets = listOf(
                Bullet(0, "Swap with a Tree token in radius", { selfSwappable().isNotEmpty() }) {
                    val t = a.target(a.chooseToken("Swap with a Tree token in radius", selfSwappable())!!)
                    a.g.swap(a.heroId, t, a.heroId)
                },
                Bullet(1, "Swap a friendly unit in radius with a Tree token in radius", { friends().isNotEmpty() }) {
                    val f = a.target(a.chooseUnit("Swap a friendly unit in radius", friends())!!)
                    val options = trees().filter { a.g.canSwap(f, it, a.heroId) }
                    val t = a.target(a.chooseToken("… with a Tree token in radius", options)!!)
                    a.g.swap(f, t, a.heroId)
                },
            )
            if (times == 1) a.chooseOne(bullets) else a.chooseUpToTimes(times, bullets)
        }
    }

    private object MarchOfNature : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.CardResolved || t.heroId != owner) return
                val at = g.posOf(owner) ?: return
                val radius = g.cardValue(owner, src.cardId!!, Stat.RADIUS) ?: 0
                // p.18: with the supply empty, a Tree token is first taken from the board.
                g.mayPlaceToken(
                    TREE,
                    owner,
                    owner,
                    g.within(at, radius),
                    "March of Nature: you may place a Tree token in radius",
                    src.cardId,
                    decline = "Don't place",
                )
            }
        }
    }
}
