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
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.tokensWithin
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isSpawnPoint
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.push
import dev.quinton.goa.core.game.engine.removeMinion
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.tokensAdjacentTo
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex

/**
 * Mortimer the Awakener. Card text quoted verbatim above each registration
 * (`catalog/heroes/mortimer.json`); `MortimerTest` follows it clause by clause with the FAQ.
 * Readings: `core/game/rulings/mortimer.md`.
 */
object MortimerHero : HeroModule {
    override val heroId: String = "mortimer"
    override val complete: Boolean = true

    /** The Zombie token kind (`{token_zombie}` in the card text). */
    const val ZOMBIE: String = "token_zombie"

    override fun register(r: Registry) {
        // Awaken!: "Zombie tokens are not removed at the end of round." The box's Zombie supply is not
        // in our sources: not limited ([APP] rulings/mortimer.md).
        r.token(TokenKind(ZOMBIE, "Zombie", supply = null, removeAtEndOfRound = false))
        // Knife of the Living Dead: "Target a unit adjacent to you. Before the attack: Choose up to
        // three times — • Move a Zombie token in radius 1 space. • Remove a Zombie token adjacent to the
        // target; if you do, gain +1 Attack."
        r.card("mortimer-gold", Knife(heroOnly = false, times = 3))
        // Shallow Graves (Handicap): "Target a hero adjacent to you. Before the attack: Choose up to two
        // times — …" (the same bullets)
        r.card("mortimer-handicap", Knife(heroOnly = true, times = 2))
        // Awaken!: "Place up to 4 Zombie tokens into spaces adjacent to you, or into spawn points in
        // radius. Zombie tokens are not removed at the end of round." (Defense/Skill: also as defense.)
        r.card("mortimer-silver", Awaken)
        // Crawling Dead: "Target a unit adjacent to you. After the attack: Choose one — • Move a Zombie
        // token in radius 1 space. • Another enemy hero in radius, adjacent to a Zombie token, discards a
        // card, if able."
        r.card("mortimer-red-1", AfterDead(times = 1))
        // Walking Dead / Racing Dead: "… After the attack: Choose up to two (three) times — • Move a
        // Zombie token in radius 1 space. • Another enemy hero in radius, adjacent to a Zombie token,
        // discards a card, if able; each enemy hero can only be targeted once."
        r.card("mortimer-red-2a", AfterDead(times = 2))
        r.card("mortimer-red-3a", AfterDead(times = 3))
        // Braains...! / Braaaaaaaains...!: "Target a unit adjacent to you. Before the attack: Choose up to
        // two (three) times — • Move a Zombie token in radius 1 space. • If an enemy hero in radius is
        // adjacent to a Zombie token, retrieve a discarded card."
        r.card("mortimer-red-2b", Brains(times = 2))
        r.card("mortimer-red-3b", Brains(times = 3))
        // Stage Dive: "Choose one — • Move a Zombie token in range 1 space. • Swap with a Zombie token in
        // range." Crowd Drift / Crowd Surf: "Choose up to two (three) times — …" (the same bullets)
        r.card("mortimer-green-1", Crowd(times = 1))
        r.card("mortimer-green-2a", Crowd(times = 2))
        r.card("mortimer-green-3a", Crowd(times = 3))
        // Gathering Horde / Army of Darkness: "Choose up to two (three) times — • Move a Zombie token in
        // range 1 space. • Once per turn: Replace an enemy minion in range adjacent to two or more Zombie
        // tokens with a Zombie token."
        r.card("mortimer-green-2b", Horde(times = 2))
        r.card("mortimer-green-3b", Horde(times = 3))
        // Corpse Slam: "Choose one — • Move a Zombie token in range up to 1 space; that Zombie token may
        // push a unit or a token adjacent to it 1 space. • Move 1 space." Morbid Mosh / Macabre Mayhem:
        // "Choose up to two (three) times — …" (the same bullets)
        r.card("mortimer-blue-1", Mosh(times = 1))
        r.card("mortimer-blue-2a", Mosh(times = 2))
        r.card("mortimer-blue-3a", Mosh(times = 3))
        // Robbing Zombies / Stalking Scalpers: "Choose up to two (three) times — • Move a Zombie token in
        // range up to 1 space. Gain 1 coin. • Move 1 space."
        r.card("mortimer-blue-2b", Robbing(times = 2))
        r.card("mortimer-blue-3b", Robbing(times = 3))
        // Master of Puppets (Ultimate): "Whenever you would choose up to three times, you may choose up to
        // five times instead."
        r.card("mortimer-purple-4", MasterOfPuppets)
    }

    // ---- modal actions (p.10 "Modal Actions") --------------------------------------------------

    /** A bullet point: [index] is its place on the card, top to bottom. */
    private class Bullet(val index: Int, val label: String, val possible: () -> Boolean, val run: () -> Unit)

    /** Offered: bullets that can start (RULINGS 38); a single one is not asked unless [optional]. */
    private fun ActionScope.pick(prompt: String, bullets: List<Bullet>, optional: Boolean): Bullet? {
        val ok = bullets.filter { it.possible() }
        if (ok.isEmpty()) return null
        if (ok.size == 1 && !optional) return ok.single()
        val opts = ok.map { Choice.Option(it.index, it.label) as Choice } +
            if (optional) listOf(Choice.Decline("Done")) else emptyList()
        val c = g.ask(heroId, DecisionKind.CHOOSE_OPTION, prompt, opts, cardId) as? Choice.Option ?: return null
        return ok.first { it.index == c.index }
    }

    /**
     * "Choose one —" ([times] = 1, mandatory: none possible = the step fails, p.10) or "Choose up to N
     * times —": any bullets, the same again included, each time among those that can be done; the
     * hero may stop at any time. Master of Puppets widens three to five ([ActionScope.choiceLimit]).
     */
    private fun ActionScope.choose(times: Int, bullets: List<Bullet>) {
        if (times == 1) {
            val b = pick("Choose one", bullets, optional = false) ?: fizzle("no option can be done")
            b.run()
            return
        }
        val limit = choiceLimit(times, upTo = true)
        for (i in 1..limit) {
            val b = pick("Choose an option ($i of up to $limit)", bullets, optional = true) ?: return
            b.run()
        }
    }

    // ---- zombies -------------------------------------------------------------------------------

    private fun Game.isZombie(id: String?): Boolean = id != null && state.piece(id)?.kind == ZOMBIE

    /** Zombie tokens 1..[n] spaces from the acting hero that this action may affect. */
    private fun ActionScope.zombies(n: Int): List<String> = tokensWithin(n).filter { g.isZombie(it) && canTarget(it) }

    private fun Game.zombiesNextTo(id: String): Int {
        val p = posOf(id) ?: return 0
        return tokensAdjacentTo(p).count { isZombie(it) }
    }

    /** "Move a Zombie token in radius (range) 1 space." */
    private fun ActionScope.moveZombie(index: Int, n: Int, where: String): Bullet {
        fun movable() = zombies(n).filter { g.canDisplace(it, Displacement.MOVE, heroId) && canMoveOther(it, 1) }
        return Bullet(index, "Move a Zombie token in $where 1 space", { movable().isNotEmpty() }) {
            val z = target(chooseToken("Move a Zombie token in $where 1 space", movable())!!)
            moveOther(z, 1)
        }
    }

    /** "Move a Zombie token in range up to 1 space" (a token in range is needed; the distance is 0..1). */
    private fun ActionScope.moveZombieUpTo(label: String, then: (String) -> Unit, index: Int): Bullet =
        Bullet(index, label, { zombies(range).isNotEmpty() }) {
            val z = target(chooseToken("Move a Zombie token in range up to 1 space", zombies(range))!!)
            if (g.canDisplace(z, Displacement.MOVE, heroId)) moveOther(z, 0, 1)
            then(z)
        }

    /** "Move 1 space." */
    private fun ActionScope.moveOne(index: Int): Bullet =
        Bullet(index, "Move 1 space", {
            g.canDisplace(heroId, Displacement.MOVE, heroId) && g.canMove(MoveSpec(heroId, 1, 1))
        }) { moveSelf(1) }

    // ---- cards ---------------------------------------------------------------------------------

    private class Knife(private val heroOnly: Boolean, private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            var bonus = 0
            fun nextToTarget() = a.g.posOf(t)?.let { p ->
                a.g.tokensAdjacentTo(p).filter { a.g.isZombie(it) && a.canTarget(it) }
            }.orEmpty()
            // "Before the attack: Choose up to three (two) times —"
            a.choose(
                times,
                listOf(
                    a.moveZombie(0, a.radius, "radius"),
                    Bullet(1, "Remove a Zombie token adjacent to the target; if you do, gain +1 Attack", {
                        nextToTarget().isNotEmpty()
                    }) {
                        val z = a.target(
                            a.chooseToken("Remove a Zombie token adjacent to the target", nextToTarget())!!,
                        )
                        a.g.removePiece(z)
                        bonus++
                    },
                ),
            )
            a.attack(t, bonus = { bonus })
        }
    }

    private object Awaken : CardScript {
        override fun primary(a: ActionScope) {
            awaken(a.g, a.heroId, a.radius) { prompt, spaces -> a.chooseSpace(prompt, spaces, optional = true) }
        }

        /** p.14: a Defense/Skill card's text applies when it is discarded to defend (FAQ: whatever its value). */
        override fun defense(d: DefenseScope) {
            awaken(d.g, d.heroId, d.radius) { prompt, spaces -> d.chooseSpace(prompt, spaces, optional = true) }
        }

        /** "Place up to 4 Zombie tokens into spaces adjacent to you, or into spawn points in radius." */
        private fun awaken(g: Game, heroId: String, radius: Int, choose: (String, List<Hex>) -> Hex?) {
            for (i in 1..4) {
                val at = g.posOf(heroId) ?: return
                val adjacent = HexGrid.neighbors(at).filter { g.isEmpty(it) }
                val spawns = g.within(at, radius).filter { g.isSpawnPoint(it) && g.isEmpty(it) }
                val spaces = (adjacent + spawns).distinct()
                if (spaces.isEmpty()) return
                val h = choose("Place a Zombie token next to you or on a spawn point in radius ($i of up to 4)", spaces)
                    ?: return
                g.placeToken(ZOMBIE, heroId, h)
            }
        }
    }

    private class AfterDead(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            val targetHero = a.g.heroBodyOf(t)
            val hit = LinkedHashSet<String>()

            // "Another enemy hero in radius, adjacent to a Zombie token" (not the attack's target); Walking
            // and Racing Dead: "each enemy hero can only be targeted once".
            fun victims() = a.unitsWithin(a.radius).filter {
                a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) && a.g.heroBodyOf(it) != targetHero &&
                    a.g.heroBodyOf(it) !in hit && a.g.zombiesNextTo(it) > 0
            }
            // "After the attack: Choose one / up to N times —"
            a.choose(
                times,
                listOf(
                    a.moveZombie(0, a.radius, "radius"),
                    Bullet(1, "Another enemy hero in radius, adjacent to a Zombie token, discards a card", {
                        victims().isNotEmpty()
                    }) {
                        val v = a.target(
                            a.chooseUnit("Another enemy hero next to a Zombie token discards a card", victims())!!,
                        )
                        hit += a.g.heroBodyOf(v)!!
                        a.discardIfAble(v)
                    },
                ),
            )
        }
    }

    private class Brains(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return

            // FAQ (common): checking whether a hero is adjacent counts as targeting it, so immune heroes
            // do not count.
            fun condition() = a.unitsWithin(a.radius).any {
                a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) && a.g.zombiesNextTo(it) > 0
            }
            // "Before the attack: Choose up to two (three) times —"
            a.choose(
                times,
                listOf(
                    a.moveZombie(0, a.radius, "radius"),
                    Bullet(1, "If an enemy hero in radius is adjacent to a Zombie token, retrieve a discarded card", {
                        condition() && a.g.discardPile(a.heroId).isNotEmpty()
                    }) {
                        val c = a.chooseCard("Retrieve a discarded card", a.g.discardPile(a.heroId))!!
                        a.g.retrieve(a.heroId, c)
                    },
                ),
            )
            a.attack(t)
        }
    }

    private class Crowd(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            fun swappable() = a.zombies(a.range).filter { a.g.canSwap(a.heroId, it, a.heroId) }
            a.choose(
                times,
                listOf(
                    a.moveZombie(0, a.range, "range"),
                    Bullet(1, "Swap with a Zombie token in range", { swappable().isNotEmpty() }) {
                        val z = a.target(a.chooseToken("Swap with a Zombie token in range", swappable())!!)
                        a.g.swap(a.heroId, z, a.heroId)
                    },
                ),
            )
        }
    }

    private class Horde(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val once = "mortimer-replace:${a.heroId}"
            fun minions() = a.unitsWithin(a.range).filter {
                a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) && a.g.zombiesNextTo(it) >= 2
            }
            a.choose(
                times,
                listOf(
                    a.moveZombie(0, a.range, "range"),
                    Bullet(1, "Once per turn: Replace an enemy minion in range next to two or more Zombie tokens", {
                        once !in a.g.state.turnRecord.notes && minions().isNotEmpty()
                    }) {
                        a.g.state.turnRecord.notes += once
                        val m = a.target(a.chooseUnit("Replace an enemy minion with a Zombie token", minions())!!)
                        val at = a.g.posOf(m)!!
                        // p.19 "Replace": the minion leaves the board (removed: no coins; a push if it was
                        // the last, p.8), the Zombie token takes its space.
                        a.g.removeMinion(m)
                        if (a.g.isEmpty(at)) a.g.placeToken(ZOMBIE, a.heroId, at)
                    },
                ),
            )
        }
    }

    private class Mosh(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val push = { z: String ->
                // "that Zombie token may push a unit or a token adjacent to it 1 space" (away from it).
                val zp = a.g.posOf(z)
                if (zp != null) {
                    val pushable = (a.g.unitsAdjacentTo(zp) + a.g.tokensAdjacentTo(zp)).filter {
                        it != z && a.g.heroBodyOf(it) != a.heroId && a.canTarget(it) &&
                            a.g.canDisplace(it, Displacement.PUSH, a.heroId)
                    }
                    val o = a.chooseObject(
                        "The Zombie token may push a unit or a token next to it 1 space",
                        pushable,
                        optional = true,
                    )
                    if (o != null) {
                        a.target(o)
                        a.g.push(o, zp, 1, false, a.heroId)
                    }
                }
            }
            a.choose(
                times,
                listOf(
                    a.moveZombieUpTo("Move a Zombie token in range up to 1 space; it may push 1 space", push, 0),
                    a.moveOne(1),
                ),
            )
        }
    }

    private class Robbing(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            a.choose(
                times,
                listOf(
                    a.moveZombieUpTo(
                        "Move a Zombie token in range up to 1 space. Gain 1 coin",
                        { a.g.gainCoins(a.heroId, 1, a.card.name) },
                        0,
                    ),
                    a.moveOne(1),
                ),
            )
        }
    }

    private object MasterOfPuppets : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun choiceLimit(g: Game, src: Src, q: ChoiceQuery): Int? =
                if (q.heroId == src.owner && q.upTo && q.printed == 3) 5 else null
        }
    }
}
