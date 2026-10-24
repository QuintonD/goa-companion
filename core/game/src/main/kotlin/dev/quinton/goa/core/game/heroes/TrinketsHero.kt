package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseUpTo
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inStraightLine
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.radiusOf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.cancelEffect
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.tokensOnBoard
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Trinkets the Scavenger. Card text quoted verbatim above each registration (`catalog/heroes/trinkets.json`);
 * `TrinketsTest` follows it clause by clause with the FAQ. Rulings: `core/game/rulings/trinkets.md`.
 *
 * The Turret (FAQ "What is the Turret?"): "an obstacle and counts as terrain. It's not a unit, minion, or
 * hero"; "not a token and remains in play between rounds" (Ramshackle Turret); its presence is a property,
 * not an Active effect, so it stays when Trinkets' cards change zones, between rounds and if Trinkets is
 * defeated; there is a single Turret, so placing it while it is on the map repositions it. It is a
 * [PieceState] of kind [TURRET] (`token = false`, `removeAtEndOfRound = false`, no team).
 */
object TrinketsHero : HeroModule {
    override val heroId: String = "trinkets"
    override val complete: Boolean = true

    const val TURRET: String = "trinkets_turret"
    const val BARRIER: String = "token_barrier"

    override fun register(r: Registry) {
        r.behavior(TURRET, TurretRules)
        // [APP] The rules we know do not give the number of Barrier tokens in the box: not limited (null).
        r.token(TokenKind(BARRIER, "Barrier", supply = null))
        r.behavior(BARRIER, BarrierRules)
        // Rapid Redeployment: "Choose one — • Move up to 3 spaces and place the Turret into a space adjacent to
        // you; it counts as an obstacle. • Defeat a minion adjacent to you."
        r.card("trinkets-gold", Redeploy(defeatBullet = true))
        // Ramshackle Turret (Handicap): "Move up to 3 spaces and place the Turret into a space adjacent to you; it
        // counts as an obstacle. (The Turret is not a token and remains in play between rounds.)"
        r.card("trinkets-handicap", Redeploy(defeatBullet = false))
        // Salvage Parts: "Choose one — • Place the Turret into a space adjacent to you; it counts as an obstacle.
        // • Remove the Turret; move up to 3 spaces. • Remove the Turret; you may retrieve a discarded card."
        r.card("trinkets-silver", Salvage)
        // Makeshift Minigun / Gatling Gun: "Target a unit in range of both you and the Turret. If the target is in
        // a straight line from you, and in a straight line from the Turret, gain +2 Attack." Supercharged
        // Cannon: "… gain +3 Attack."
        r.card("trinkets-red-1", Cannon(bonus = 2))
        r.card("trinkets-red-2a", Cannon(bonus = 2))
        r.card("trinkets-red-3a", Cannon(bonus = 3))
        // Steam Discharge: "Target a unit in range adjacent to the Turret. May repeat once on a different enemy
        // unit." Flame Belcher: "… May repeat up to two times on different enemy units."
        r.card("trinkets-red-2b", Discharge(repeats = 1))
        r.card("trinkets-red-3b", Discharge(repeats = 2))
        // Early Prototype: "If you are in radius of the Turret, swap with a unit or a token in radius of the Turret,
        // then remove the Turret." Updated Design: "If you are in radius of the Turret, swap with a unit or a
        // token in radius of the Turret." Perfected Design: "If you are in radius of the Turret, Choose one —
        // • Swap with a unit or a token in radius of the Turret. • Place yourself into a space in radius of the
        // Turret."
        r.card("trinkets-green-1", Design(removeAfter = true, placeBullet = false))
        r.card("trinkets-green-2a", Design(removeAfter = false, placeBullet = false))
        r.card("trinkets-green-3a", Design(removeAfter = false, placeBullet = true))
        // Disruptor Jolt / Disruptor Pulse: "This turn: Before any enemy hero in radius of the Turret performs a
        // primary action, that hero discards a card, if able; if they discard a card, deactivate this effect."
        // Disruptor Grid: "… that hero discards a card, or is defeated; if they discard a card, deactivate this
        // effect."
        r.card("trinkets-blue-1", Disruptor(orDefeated = false))
        r.card("trinkets-blue-2a", Disruptor(orDefeated = false))
        r.card("trinkets-blue-3a", Disruptor(orDefeated = true))
        // Self-Destruct: "Up to two enemy heroes in radius of the Turret discard a card, if able. Remove the
        // Turret." Emergency Protocol: "Up to two enemy heroes in radius of the Turret discard a card, or are
        // defeated. Remove the Turret."
        r.card("trinkets-green-2b", SelfDestruct(orDefeated = false))
        r.card("trinkets-green-3b", SelfDestruct(orDefeated = true))
        // Deployable Barrier: "Place up to 2 Barrier tokens in radius, with at least one of them adjacent to the
        // Turret; you and friendly heroes gain +1 Defense for each Barrier token they are adjacent to."
        // Deployable Bastion: "Place up to 3 Barrier tokens …"
        r.card("trinkets-blue-2b", Barrier(count = 2))
        r.card("trinkets-blue-3b", Barrier(count = 3))
        // Unlimited Firepower (Ultimate): "Gain +1 Range and +1 Radius."
        r.card("trinkets-purple-4", Firepower)
    }

    // ---- the Turret ----------------------------------------------------------------------------

    /**
     * The Turret on the board, if any. There is a single Turret (FAQ: "You're limited to the single provided
     * Turret"), and a hero performing one of Trinkets' cards (NebKher's Mind Grip) uses *the* Turret —
     * designer, BGG 3485218: "No he places *the* turret"; "all Trinket's card texts refer to *the* turret,
     * not *a* turret. Nothing in the text prevents Neb from using it, as long as the turret is on the board."
     */
    fun Game.turret(): PieceState? = state.pieces.firstOrNull { it.kind == TURRET && it.at != null }

    /** "it counts as an obstacle" (a piece on its space is one) and, per the FAQ, as terrain. */
    private object TurretRules : Behavior {
        override fun countsAsTerrain(g: Game, src: Src, hex: Hex): Boolean = src.piece?.at == hex
    }

    /** "Place the Turret into a space adjacent to you" (p.18 "Place": an empty space; a single Turret). */
    private fun ActionScope.placeTurretAdjacent() {
        val spaces = HexGrid.neighbors(pos).filter { g.isEmpty(it) }
        if (spaces.isEmpty()) fizzle("no empty space next to you for the Turret")
        val h = chooseSpace("Place the Turret into a space adjacent to you", spaces)!!
        val existing = g.turret()
        if (existing != null) {
            g.place(existing.id, h, heroId)
            return
        }
        // Trinkets' Turret, whoever performs her card (designer, BGG 3485218).
        val trinkets = g.state.heroes.firstOrNull { it.card(cardId) != null }?.id ?: heroId
        val piece = PieceState(
            id = g.state.newId("p"),
            kind = TURRET,
            label = "Turret",
            at = null,
            owner = trinkets,
            team = null,
            token = false,
            unit = false,
            removeAtEndOfRound = false,
        )
        g.state.pieces += piece
        g.touch()
        g.place(piece.id, h, heroId)
    }

    private fun ActionScope.turretPos(): Hex? = g.turret()?.at

    /** [id] is 1..[n] spaces from the Turret ("in radius / in range of the Turret", p.9; FAQ: measured on its own). */
    private fun ActionScope.nearTurret(id: String, n: Int): Boolean {
        val t = turretPos() ?: return false
        val p = g.posOf(id) ?: return false
        return g.distance(t, p) in 1..n
    }

    private class Redeploy(private val defeatBullet: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val minions = if (defeatBullet) {
                a.unitsWithin(1).filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
            } else {
                emptyList()
            }
            val labels = listOf(
                "Move up to 3 spaces and place the Turret into a space adjacent to you",
                "Defeat a minion adjacent to you",
            )
            val pick = if (minions.isEmpty()) 0 else a.chooseOption("Choose one", labels)!!
            if (pick == 1) {
                val m = a.target(a.chooseUnit(labels[1], minions)!!)
                a.g.defeatMinion(m, a.heroId)
                return
            }
            a.moveSelf(0, 3)
            a.placeTurretAdjacent()
        }
    }

    private object Salvage : CardScript {
        override fun primary(a: ActionScope) {
            val canPlace = HexGrid.neighbors(a.pos).any { a.g.isEmpty(it) }
            val hasTurret = a.g.turret() != null
            val labels = listOf(
                "Place the Turret into a space adjacent to you",
                "Remove the Turret; move up to 3 spaces",
                "Remove the Turret; you may retrieve a discarded card",
            )
            val open = labels.indices.filter { if (it == 0) canPlace else hasTurret }
            if (open.isEmpty()) a.fizzle("no space for the Turret and no Turret to remove")
            val pick = if (open.size ==
                1
            ) {
                open.single()
            } else {
                open[a.chooseOption("Choose one", open.map { labels[it] })!!]
            }
            when (pick) {
                0 -> a.placeTurretAdjacent()

                1 -> {
                    a.g.removePiece(a.g.turret()!!.id)
                    a.moveSelf(0, 3)
                }

                else -> {
                    a.g.removePiece(a.g.turret()!!.id)
                    a.mayRetrieve()
                }
            }
        }
    }

    private class Cannon(private val bonus: Int) : CardScript {
        override fun primary(a: ActionScope) {
            if (a.turretPos() == null) a.fizzle("the Turret is not on the board")
            // "in range of both you and the Turret" (FAQ: the range of each measured independently).
            val inBoth: (String) -> Boolean = { a.inRange(it) && a.nearTurret(it, a.range) }
            val t = a.rangedAttackTarget("Target a unit in range of both you and the Turret") { inBoth(it) } ?: return
            a.attack(t, valid = inBoth, bonus = {
                val tp = a.g.posOf(t)
                val turret = a.turretPos()
                if (tp != null && turret != null && a.inStraightLine(t) &&
                    HexGrid.inStraightLine(turret, tp)
                ) {
                    bonus
                } else {
                    0
                }
            })
        }
    }

    private class Discharge(private val repeats: Int) : CardScript {
        override fun primary(a: ActionScope) {
            if (a.turretPos() == null) a.fizzle("the Turret is not on the board")
            val valid: (String) -> Boolean = { a.inRange(it) && a.nearTurret(it, 1) }
            val t = a.rangedAttackTarget("Target a unit in range adjacent to the Turret") { valid(it) } ?: return
            a.attack(t, valid = valid)
            a.repeat(
                times = repeats,
                differentTarget = true,
                prompt = "Repeat on a different enemy unit?",
            )
        }
    }

    private class Design(private val removeAfter: Boolean, private val placeBullet: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "If you are in radius of the Turret," — otherwise the text does nothing.
            val turret = a.g.turret() ?: return
            val tp = turret.at!!
            val r = a.radius
            if (a.g.distance(a.pos, tp) !in 1..r) return
            val swaps = (a.g.unitsOnBoard() + a.g.tokensOnBoard()).filter { id ->
                id != a.heroId && a.g.heroBodyOf(id) != a.heroId && a.nearTurret(id, r) && a.canTarget(id) &&
                    a.g.canSwap(a.heroId, id, a.heroId)
            }
            val places = if (placeBullet && a.g.canPlace(a.heroId, a.heroId)) {
                // Empty for Trinkets herself (p.18 "Place"; Spinning Blade's spaces count as obstacles for her).
                a.g.within(tp, r).filter { it != a.pos && a.g.isEmpty(it, a.heroId) }
            } else {
                emptyList()
            }
            val labels = listOf(
                "Swap with a unit or a token in radius of the Turret",
                "Place yourself into a space in radius of the Turret",
            )
            val pick = when {
                swaps.isEmpty() && places.isEmpty() -> a.fizzle("nothing in radius of the Turret")
                places.isEmpty() -> 0
                swaps.isEmpty() -> 1
                else -> a.chooseOption("Choose one", labels)!!
            }
            if (pick == 1) {
                val h = a.chooseSpace(labels[1], places)!!
                a.g.place(a.heroId, h, a.heroId)
                return
            }
            val t = a.target(a.chooseObject(labels[0], swaps)!!)
            a.g.swap(a.heroId, t, a.heroId)
            // Early Prototype: "then remove the Turret."
            if (removeAfter) a.g.turret()?.let { a.g.removePiece(it.id) }
        }
    }

    /**
     * [APP] "Before any enemy hero … performs a primary action": before each primary action it performs
     * (a repeat is a separate action, p.10; a primary performed by another card's text counts), measured
     * from where the hero stands then — a primary Defense action too (defending with a card whose Primary
     * action is Defense, or a Defense/Skill card, after it is discarded and before its text): the rulebook
     * calls it one (p.14 "since Defense is the Primary action of 'Vampiric Shield'"; quick reference
     * "Defense Action … Primary: Same as above, and, in addition, apply the card text"; "Defense/Skill …
     * perform either Defense or a Skill as a primary action"). Secondary Defense actions are not.
     */
    private class Disruptor(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                if (t !is Trigger.BeforeAction || !t.primary) return
                val owner = src.owner ?: return
                val e = src.effect ?: return
                if (!g.isHero(t.heroId) || !g.isEnemy(owner, t.heroId)) return
                val turret = g.turret()?.at ?: return
                val at = g.posOf(t.heroId) ?: return
                if (g.distance(turret, at) !in 1..g.radiusOf(src) || !g.affectedBy(src, t.heroId)) return
                // "that hero discards a card, if able (or is defeated); if they discard a card, deactivate this
                // effect."
                val discarded = g.forceDiscard(t.heroId, owner, orDefeated, src.cardId)
                if (discarded != null) g.cancelEffect(e)
            }
        }
    }

    private class SelfDestruct(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            if (a.turretPos() == null) a.fizzle("the Turret is not on the board")
            val r = a.radius
            val heroes = a.g.unitsOnBoard().filter {
                a.g.isHero(it) && a.isEnemy(it) && a.nearTurret(it, r) &&
                    a.canTarget(it)
            }
            val picked = a.chooseUpTo(2, "An enemy hero in radius of the Turret discards a card", heroes)
            for (v in picked) if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
            // "Remove the Turret."
            a.g.turret()?.let { a.g.removePiece(it.id) } ?: a.fizzle("the Turret is gone")
        }
    }

    private class Barrier(private val count: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val placed = ArrayList<Hex>()
            for (i in 1..count) {
                // "with at least one of them adjacent to the Turret": the first one placed is (WLOG).
                val turret = a.turretPos()
                val spaces = a.g.within(a.pos, a.radius).filter { h ->
                    a.g.isEmpty(h) && (placed.isNotEmpty() || (turret != null && a.g.distance(h, turret) == 1))
                }
                if (spaces.isEmpty()) return
                val h = a.chooseSpace(
                    if (placed.isEmpty()) {
                        "Place a Barrier token in radius, adjacent to the Turret ($i of up to $count)"
                    } else {
                        "Place a Barrier token in radius ($i of up to $count)"
                    },
                    spaces,
                    optional = true,
                ) ?: return
                a.g.placeToken(BARRIER, a.heroId, h) ?: return
                placed += h
            }
        }
    }

    /**
     * "you and friendly heroes gain +1 Defense for each Barrier token they are adjacent to" — a property of
     * the token while it is on the board (p.19: a token "retains all its properties until that token is
     * removed"; the text has no Active-effect keyword).
     */
    private object BarrierRules : Behavior {
        override fun defenseBonus(g: Game, src: Src, defenderId: String, attackerId: String): Int {
            val p = src.piece ?: return 0
            val owner = p.owner ?: return 0
            val at = p.at ?: return 0
            if (g.heroBodyOf(defenderId) != owner &&
                !(g.isHero(defenderId) && g.isFriendly(owner, defenderId))
            ) {
                return 0
            }
            val d = g.posOf(defenderId) ?: return 0
            return if (g.distance(at, d) == 1) 1 else 0
        }
    }

    private object Firepower : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            /** "Gain +1 Range and +1 Radius" — on the Range and Radius icons of her cards (as an item, p.16). */
            override fun statDelta(g: Game, src: Src, q: StatQuery): Int =
                if (q.unitId == src.owner && q.cardId != null &&
                    (q.stat == Stat.RANGE || q.stat == Stat.RADIUS)
                ) {
                    1
                } else {
                    0
                }
        }
    }
}
