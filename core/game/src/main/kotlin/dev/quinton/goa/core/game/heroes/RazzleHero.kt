package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.chooseDistance
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.ControlSignal
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameEnded
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.ProbeHit
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canSpawnInto
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.repeatForbidden
import dev.quinton.goa.core.game.engine.setPos
import dev.quinton.goa.core.game.engine.spawnPiece
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex

/**
 * Razzle the Ringmaster. Card text quoted verbatim above each registration (`catalog/heroes/razzle.json`);
 * `RazzleTest` follows it clause by clause with the hero's FAQ. Rulings: `rulings/razzle.md`.
 *
 * "Each of you is the same hero, except when actions are performed": the extra figures are pieces of
 * kind [DOUBLE] with `heroBody = "razzle"` (the engine treats them as Razzle: targets, defense, defeat).
 * [HeroState.at][dev.quinton.goa.core.game.engine.HeroState.at] is always the figure that acts: before
 * Razzle chooses an action she chooses which one of her performs it (`Trigger.ChoosingAction`), and the
 * hero's space and that figure's space are exchanged (bookkeeping, not a swap). Every other figure is
 * then "another one of you" — and, per the FAQ, "another hero".
 */
object RazzleHero : HeroModule {
    override val heroId: String = "razzle"
    override val complete: Boolean = true

    /** The piece kind of Razzle's extra figures. */
    const val DOUBLE: String = "razzle_double"

    /** "Spawn up to 3 more of you": four figures in all (p.19 "Spawn": only from the supply). */
    private const val SUPPLY = 3

    override fun register(r: Registry) {
        r.heroRule(heroId, ActingFigure)
        // Stunt Doubles: "Target a unit adjacent to you. After the attack: Spawn up to 3 more of you in
        // radius; each of you is the same hero, except when actions are performed. If defeated, remove
        // all of you."
        r.card("razzle-gold", StuntDoubles(heroOnly = false))
        // Stunted Doubles (Handicap): "Target a hero adjacent to you. After the attack: Spawn up to 3 more
        // of you in radius; …" (same)
        r.card("razzle-handicap", StuntDoubles(heroOnly = true))
        // Crowd Control: "When used as a defense action, +2 Defense for each other one of you in radius.
        // When used as a skill action, remove all other you in play."
        r.card("razzle-silver", CrowdControl)
        // Phantom Strike: "Target a unit adjacent to you. After the attack: If there is more than one of
        // you in play, you may remove one of you."
        r.card("razzle-red-1", PhantomStrike)
        // Hit and Gone: "Target a unit adjacent to you. After the attack: You may remove one or more of you
        // in play, except the last one of you."
        r.card("razzle-red-2a", Vanish(keepOne = true))
        // Into Thin Air: "Target a unit adjacent to you. After the attack: You may remove one or more of you
        // in play, including all of you. (If all are removed, you are not defeated. Respawn as normal.)"
        r.card("razzle-red-3a", Vanish(keepOne = false))
        // Rummage: "Target a unit adjacent to you. After the attack: If there is another one of you in
        // radius, you may retrieve a discarded card. (All of you share cards in hand, deck, and discard.)"
        r.card("razzle-red-2b", Rummage(perOther = false))
        // Ransack: "Target a unit adjacent to you. After the attack: For each other one of you in radius,
        // you may retrieve a discarded card."
        r.card("razzle-red-3b", Rummage(perOther = true))
        // Tightrope: "After you move, you may move another one of you 1 space."
        r.card("razzle-green-1", Wire(spaces = 1, exact = true))
        // High Wire / Wire Dancers: "After you move, move another one of you up to 2 (3) spaces."
        r.card("razzle-green-2a", Wire(spaces = 2, exact = false))
        r.card("razzle-green-3a", Wire(spaces = 3, exact = false))
        // Theatrics: "Swap with a minion in range."
        r.card("razzle-green-2b", Theatrics(repeat = false))
        // Spectacle: "Swap with a minion in range. May repeat once by another one of you, targeting a
        // different minion."
        r.card("razzle-green-3b", Theatrics(repeat = true))
        // Alleyoop / Group Performance / Team Spirit: "Swap with a friendly hero in range. Move another one
        // of you up to 1 (2, 3) space(s)."
        r.card("razzle-blue-1", Alleyoop(spaces = 1))
        r.card("razzle-blue-2a", Alleyoop(spaces = 2))
        r.card("razzle-blue-3a", Alleyoop(spaces = 3))
        // Magic Trick / Aaaand it's gone!: "Push a unit adjacent to you up to 2 (3) spaces; for every space
        // the target moved, move this one of you one space in the opposite direction."
        r.card("razzle-blue-2b", MagicTrick(push = 2))
        r.card("razzle-blue-3b", MagicTrick(push = 3))
        // Twin Strike (Ultimate): "After you perform a basic attack action, another one of you may repeat
        // it once, targeting a different unit."
        r.card("razzle-purple-4", TwinStrike)
    }

    // ---- figures ---------------------------------------------------------------------------------

    /** The extra figures of [heroId] on the board ("another one of you" while [heroId] acts). */
    fun doubles(g: Game, heroId: String): List<String> =
        g.state.pieces.filter { it.heroBody == heroId && it.at != null }.map { it.id }

    /** Every figure of [heroId] on the board, the acting one ([heroId]) first. */
    fun figures(g: Game, heroId: String): List<String> =
        listOfNotNull(heroId.takeIf { g.heroOrNull(heroId)?.at != null }) + doubles(g, heroId)

    /** Make the figure [doubleId] the one that acts: the hero's and that piece's spaces are exchanged. */
    private fun actWith(g: Game, heroId: String, doubleId: String) {
        val a = g.posOf(heroId) ?: return
        val b = g.posOf(doubleId) ?: return
        g.setPos(heroId, null)
        g.setPos(doubleId, a)
        g.setPos(heroId, b)
        // The figures did not move: their start-of-turn spaces follow the ids ("where that unit was at
        // the start of this turn", "remained in the same space").
        val starts = g.state.turnRecord.startPositions
        if (heroId in starts || doubleId in starts) {
            val sa = starts[heroId]
            val sb = starts[doubleId]
            starts[heroId] = sb
            starts[doubleId] = sa
        }
    }

    /**
     * Remove one figure (p.19 "Remove": no coins, no defeat). Removing the acting figure while others
     * remain leaves Razzle on one of them; removing the last one takes her off the board.
     */
    private fun removeFigure(g: Game, heroId: String, id: String) {
        if (id != heroId) {
            g.removePiece(id)
            return
        }
        val other = doubles(g, heroId).firstOrNull()
        if (other != null) {
            actWith(g, heroId, other)
            g.removePiece(other)
            return
        }
        g.setPos(heroId, null)
        g.emit(GameEvent.Removed(heroId, "${g.heroName(heroId)} is removed from the board (not defeated)."))
    }

    private fun ActionScope.othersWithin(n: Int): List<String> {
        val me = hero.at ?: return emptyList()
        return doubles(g, heroId).filter { id -> g.posOf(id)?.let { g.distance(me, it) in 1..n } == true }
    }

    /** Before choosing an action, Razzle chooses which one of her performs it. */
    private object ActingFigure : Behavior {
        override fun on(g: Game, src: Src, t: Trigger) {
            val owner = src.owner ?: return
            if (t !is Trigger.ChoosingAction || t.heroId != owner) return
            val figs = figures(g, owner)
            if (figs.size < 2) return
            val c = g.ask(
                owner,
                DecisionKind.CHOOSE_UNIT,
                "Which one of you performs the action?",
                figs.map { Choice.Unit(it) },
                t.cardId,
            ) as Choice.Unit
            if (c.unitId == owner) return
            actWith(g, owner, c.unitId)
            g.note("${g.heroName(owner)} acts with the one of her at ${g.posOf(owner)}.")
        }
    }

    /**
     * Could the figure [doubleId] perform this action again ([excluded] not targeted)? Probed on a copy
     * where that figure is the acting one (the FAQ "first instruction" test).
     */
    @Suppress("LongParameterList")
    private fun possibleFrom(
        g: Game,
        heroId: String,
        doubleId: String,
        cardId: String,
        kind: ActionKind,
        primary: Boolean,
        excluded: Set<String>,
    ): Boolean {
        val copy = g.state.copy()
        val h = copy.hero(heroId) ?: return false
        val p = copy.piece(doubleId) ?: return false
        val a = h.at ?: return false
        h.at = p.at
        p.at = a
        val probe = Game(g.data, copy, emptyList(), RunMode.PROBE)
        val before = copy.stamp
        return try {
            probe.performAction(heroId, cardId, kind, primary, isRepeat = true, excluded = excluded, probing = true)
            true
        } catch (_: ProbeHit) {
            true
        } catch (_: GameEnded) {
            true
        } catch (_: ControlSignal) {
            copy.stamp != before
        }
    }

    /** "Another one of you … repeat it once, targeting a different unit": the repeat by another figure. */
    @Suppress("LongParameterList")
    private fun repeatByAnother(
        g: Game,
        heroId: String,
        cardId: String,
        kind: ActionKind,
        primary: Boolean,
        targets: Collection<String>,
        prompt: String,
    ) {
        if (g.repeatForbidden(heroId)) return
        val excluded = targets.toSet()
        val others = doubles(g, heroId).filter { possibleFrom(g, heroId, it, cardId, kind, primary, excluded) }
        if (others.isEmpty()) return
        val c = g.ask(
            heroId,
            DecisionKind.CHOOSE_UNIT,
            prompt,
            others.map { Choice.Unit(it) as Choice } + Choice.Decline("Don't repeat"),
            cardId,
        ) as? Choice.Unit ?: return
        actWith(g, heroId, c.unitId)
        g.note("${g.heroName(heroId)} repeats it with the one of her at ${g.posOf(heroId)}.")
        g.performAction(heroId, cardId, kind, primary, isRepeat = true, excluded = excluded)
    }

    // ---- cards -----------------------------------------------------------------------------------

    private class StuntDoubles(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            val res = a.attack(t)
            if (!res.connected) return
            // "Spawn up to 3 more of you in radius" — from the supply only (p.19 "Spawn").
            var left = SUPPLY - doubles(a.g, a.heroId).size
            while (left > 0) {
                val me = a.hero.at ?: return
                val spaces = a.g.within(me, a.radius).filter { a.g.canSpawnInto(it, a.heroId) }
                val h =
                    a.chooseSpace("Spawn another one of you in radius (up to $left more)", spaces, optional = true)
                        ?: return
                val piece = PieceState(
                    id = a.g.state.newId("rz"),
                    kind = DOUBLE,
                    label = a.g.heroName(a.heroId),
                    at = null,
                    owner = a.heroId,
                    team = a.hero.team,
                    token = false,
                    unit = true,
                    removeAtEndOfRound = false,
                    heroBody = a.heroId,
                )
                a.g.spawnPiece(piece, h)
                left--
            }
        }
    }

    private object CrowdControl : CardScript {
        /** "When used as a skill action, remove all other you in play." */
        override fun primary(a: ActionScope) {
            val others = doubles(a.g, a.heroId)
            if (others.isEmpty()) a.fizzle("there is no other one of you")
            for (id in others) a.g.removePiece(id)
        }

        /** "When used as a defense action, +2 Defense for each other one of you in radius." */
        override fun defense(d: DefenseScope) {
            // The radius is measured from the one of you that was attacked.
            val at = d.g.posOf(d.attack.targetId) ?: return
            val others = figures(d.g, d.heroId).filter { it != d.attack.targetId }
            val n = others.count { id -> d.g.posOf(id)?.let { d.g.distance(at, it) in 1..d.radius } == true }
            d.bonus = 2 * n
        }
    }

    private object PhantomStrike : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val res = a.attack(t)
            if (!res.connected) return
            val figs = figures(a.g, a.heroId)
            if (figs.size < 2) return
            val id = a.chooseUnit("You may remove one of you", figs, optional = true) ?: return
            removeFigure(a.g, a.heroId, id)
        }
    }

    private class Vanish(private val keepOne: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val res = a.attack(t)
            if (!res.connected) return
            var first = true
            while (true) {
                val figs = figures(a.g, a.heroId)
                if (figs.isEmpty() || (keepOne && figs.size < 2)) return
                val prompt = if (first) "You may remove one or more of you" else "Remove another one of you?"
                val id = a.chooseUnit(prompt, figs, optional = true) ?: return
                removeFigure(a.g, a.heroId, id)
                first = false
            }
        }
    }

    private class Rummage(private val perOther: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val res = a.attack(t)
            if (!res.connected) return
            val n = a.othersWithin(a.radius).size
            val times = if (perOther) n else minOf(1, n)
            repeat(times) { a.mayRetrieve() ?: return }
        }
    }

    private class Wire(private val spaces: Int, private val exact: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "After you move": the Movement action first.
            a.movementAction()
            val others = doubles(a.g, a.heroId)
            if (exact) {
                // Tightrope: "you may move another one of you 1 space."
                val movable = others.filter { a.g.canMove(MoveSpec(it, spaces, spaces)) }
                val id =
                    a.chooseUnit("You may move another one of you $spaces space", movable, optional = true) ?: return
                a.moveOther(id, spaces)
                return
            }
            // "move another one of you up to N spaces."
            if (others.isEmpty()) return
            val id = a.chooseUnit("Move another one of you up to $spaces spaces", others)!!
            a.moveOther(id, 0, spaces)
        }
    }

    private class Theatrics(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val minions = minionsInRange(a, a.pos)
            if (minions.isEmpty()) a.fizzle("no minion in range")
            val m = a.target(a.chooseUnit("Swap with a minion in range", minions)!!)
            a.g.swap(a.heroId, m, a.heroId)
            // Spectacle: "May repeat once by another one of you, targeting a different minion."
            if (repeat && !a.isRepeat) {
                repeatByAnother(
                    a.g,
                    a.heroId,
                    a.cardId,
                    a.kind,
                    primary = true,
                    a.targets + a.excluded,
                    "Spectacle: another one of you may repeat it on a different minion",
                )
            }
        }

        private fun minionsInRange(a: ActionScope, from: Hex): List<String> = a.g.unitsOnBoard().filter { id ->
            a.g.isMinion(id) && a.g.posOf(id)?.let { a.g.distance(from, it) in 1..a.range } == true &&
                a.canTarget(id) && a.g.canSwap(a.heroId, id, a.heroId)
        }
    }

    private class Alleyoop(private val spaces: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val me = a.pos
            // "a friendly hero": another hero of the team; the FAQ makes the other ones of you "another
            // hero" while an action is performed, so they count too (rulings/razzle.md).
            val partners = a.g.unitsOnBoard().filter { id ->
                id != a.heroId && a.g.isHero(id) &&
                    (a.isFriendly(id) || a.g.heroBodyOf(id) == a.heroId) &&
                    a.g.posOf(id)?.let { a.g.distance(me, it) in 1..a.range } == true &&
                    a.canTarget(id) && a.g.canSwap(a.heroId, id, a.heroId)
            }
            if (partners.isEmpty()) a.fizzle("no friendly hero in range")
            val f = a.target(a.chooseUnit("Swap with a friendly hero in range", partners)!!)
            a.g.swap(a.heroId, f, a.heroId)
            // "Move another one of you up to N spaces."
            val others = doubles(a.g, a.heroId)
            if (others.isEmpty()) return
            val id = a.chooseUnit(
                "Move another one of you up to $spaces space${if (spaces == 1) "" else "s"}",
                others,
            )!!
            a.moveOther(id, 0, spaces)
        }
    }

    private class MagicTrick(private val push: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val me = a.pos
            val targets = a.g.unitsAdjacentTo(me).filter { id ->
                a.canTarget(id) && a.g.canDisplace(id, Displacement.PUSH, a.heroId)
            }
            if (targets.isEmpty()) a.fizzle("no unit adjacent to push")
            val t = a.target(a.chooseUnit("Push a unit adjacent to you up to $push spaces", targets)!!)
            val dir = HexGrid.straightLineDirection(me, a.g.posOf(t)!!)!!
            val n = a.chooseDistance((0..push).toList(), "Push ${a.g.name(t)} how many spaces?")
            val moved = a.pushAway(t, n)?.moved ?: 0
            // "for every space the target moved, move this one of you one space in the opposite direction."
            val opposite = (dir + HexGrid.DIRECTIONS.size / 2) % HexGrid.DIRECTIONS.size
            repeat(moved) {
                a.g.move(
                    MoveSpec(a.heroId, 1, 1, straightLine = true, directions = setOf(opposite)),
                    a.heroId,
                    "Move 1 space in the opposite direction",
                    optional = false,
                    sourceCardId = a.cardId,
                )
            }
        }
    }

    private object TwinStrike : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.AfterAction || t.heroId != owner || t.kind != ActionKind.ATTACK || !t.basic ||
                    t.repeat
                ) {
                    return
                }
                val card = t.cardId ?: return
                if (g.posOf(owner) == null) return
                repeatByAnother(
                    g,
                    owner,
                    card,
                    ActionKind.ATTACK,
                    t.primary,
                    t.targets,
                    "Twin Strike: another one of you may repeat the attack on a different unit",
                )
            }
        }
    }
}
