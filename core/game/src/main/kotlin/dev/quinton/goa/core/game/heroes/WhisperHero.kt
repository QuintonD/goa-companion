package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.ChoiceQuery
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.chooseUpTo
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.ActorDefeated
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isBasic
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isInBattleZone
import dev.quinton.goa.core.game.engine.isMinionSpawnPoint
import dev.quinton.goa.core.game.engine.isSpawnPoint
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.trigger
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.game.heroes.batch2.chooseBullets
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Whisper the Outcast. Each card's verbatim text (`catalog/heroes/whisper.json`) is quoted above its
 * registration; `WhisperTest` follows it clause by clause with the hero's FAQ. Readings:
 * `core/game/rulings/whisper.md`.
 */
object WhisperHero : HeroModule {
    override val heroId: String = "whisper"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Swift Justice: "Choose one — • Target a hero in range with an empty discard. After the attack: If
        // able, that hero performs a movement action on the card they defended with, moving full distance in
        // a straight line. • Target a unit adjacent to you."
        r.card("whisper-gold", Justice(heroAdjacentOnly = false))
        // Vile Accusation (Handicap): "… • Target a hero adjacent to you."
        r.card("whisper-handicap", Justice(heroAdjacentOnly = true))
        // Death Seeker: "If an enemy hero in radius has a card in the discard, choose one — • This turn: You
        // are immune to enemy actions. • You may retrieve a discarded card."
        r.card("whisper-silver", DeathSeeker)
        // Crimson Trail: "Before the attack: You may move up to 1 space if there is an empty minion spawn
        // point in radius in the battle zone. Target a unit adjacent to you."
        r.card("whisper-red-1", Trail(perPoint = false, max = 1))
        // Sanguine Path / Blood Pilgrimage: "Before the attack: You may move up to 1 space for every empty
        // minion spawn point in radius in the battle zone, up to a maximum of 2 (3) spaces. Target a unit
        // adjacent to you."
        r.card("whisper-red-2a", Trail(perPoint = true, max = 2))
        r.card("whisper-red-3a", Trail(perPoint = true, max = 3))
        // Blood Fury: "Target a unit adjacent to you. After the attack: If an enemy hero in radius has a card
        // in the discard, may repeat once on a different target." Blood Frenzy: "… repeat up to five times on
        // different targets."
        r.card("whisper-red-2b", Fury(times = 1))
        r.card("whisper-red-3b", Fury(times = 5))
        // Shadow Step / Shadow Walk: "Place yourself into an empty minion spawn point in range in the battle
        // zone." Creeping Shadow: "…, or into a space in range adjacent to such a spawn point."
        r.card("whisper-green-1", Shadow(nextTo = false))
        r.card("whisper-green-2a", Shadow(nextTo = false))
        r.card("whisper-green-3a", Shadow(nextTo = true))
        // Cruel Twist / Sealed Fate: "Swap with a unit adjacent to you, or with an enemy hero in range with a
        // card in the discard. Move up to 2 (3) spaces."
        r.card("whisper-green-2b", Twist(move = 2))
        r.card("whisper-green-3b", Twist(move = 3))
        // Seeds of Fear: "Target an enemy unit in radius occupying a spawn point. Move that unit 1 space."
        // Sprouts of Panic / Blooming Nightmare: "… Move that unit up to 2 (3) spaces."
        r.card("whisper-blue-1", Seeds(max = 1, exact = true))
        r.card("whisper-blue-2a", Seeds(max = 2, exact = false))
        r.card("whisper-blue-3a", Seeds(max = 3, exact = false))
        // Lesser Evil: "An enemy hero in range chooses one — • That hero discards a card, if able. • You may
        // defeat a minion adjacent to you. (Any option can be chosen, even if it would have no effect.)"
        // Greater Good: "… • That hero discards a card, or is defeated. • You defeat up to 3 minions adjacent to you."
        r.card("whisper-blue-2b", Evil(greater = false))
        r.card("whisper-blue-3b", Evil(greater = true))
        // Grim Reaper (Ultimate): "When performing basic actions, you may choose one, or both."
        r.card("whisper-purple-4", GrimReaper)
    }

    // ---- shared --------------------------------------------------------------------------------

    /**
     * "If an enemy hero in radius has a card in the discard": checking a hero's status counts as
     * targeting it (common FAQ), so immune heroes do not count.
     */
    private fun ActionScope.enemyInRadiusWithDiscard(): Boolean =
        unitsWithin(radius).any { id ->
            g.isHero(id) && isEnemy(id) && canTarget(id) &&
                g.heroBodyOf(id)?.let { g.discardPile(it).isNotEmpty() } == true
        }

    /** "Choose one" widened to "one, or both" by Grim Reaper on basic actions. */
    private fun ActionScope.mayChooseBoth(): Boolean = choiceLimit(1) >= 2

    /** An empty minion spawn point in a Battle Zone (p.17 "Empty Spawn point", p.20 "the Battle Zone"). */
    private fun Game.emptyMinionSpawnInBattleZone(h: Hex): Boolean =
        isMinionSpawnPoint(h) && isEmpty(h) && isInBattleZone(h)

    // ---- cards ---------------------------------------------------------------------------------

    private class Justice(private val heroAdjacentOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // • "Target a hero in range with an empty discard."
            val accused: (String) -> Boolean = { id ->
                g.isHero(id) && a.inRange(id) && g.heroBodyOf(id)?.let { g.discardPile(it).isEmpty() } == true
            }
            // • "Target a unit (a hero) adjacent to you."
            val adjacent: (String) -> Boolean = { id -> a.isAdjacent(id) && (!heroAdjacentOnly || g.isHero(id)) }
            val bullets = listOf(accused, adjacent)
            val labels = listOf(
                "Target a hero in range with an empty discard",
                if (heroAdjacentOnly) "Target a hero adjacent to you" else "Target a unit adjacent to you",
            )
            val possible = bullets.map { b ->
                g.unitsOnBoard().any { a.isEnemy(it) && a.canTarget(it, ActionKind.ATTACK) && b(it) }
            }
            val order = a.chooseBullets(labels, possible, a.mayChooseBoth())
            if (order.isEmpty()) a.fizzle("no target")
            for (i in order) {
                val t = a.attackTarget(labels[i], valid = bullets[i]) ?: return
                val victim = g.heroBodyOf(t)
                val r = a.attack(t, valid = bullets[i])
                // "After the attack: If able, that hero performs a movement action on the card they defended
                // with, moving full distance in a straight line."
                if (i == 0 && r.connected && !r.defeated && victim != null) {
                    r.defenseCard?.let { forcedMovement(g, victim, it) }
                }
            }
        }

        /**
         * FAQ: "If they do so and the discarded Card has a movement value, that Hero must then move that
         * distance in a straight line. They would only be unable to do so … if they could not move the full
         * movement value in a straight line in any of the 6 directions." It is that hero's Movement action
         * (it chooses the direction; movement limits and "cannot perform movement actions" apply).
         */
        private fun forcedMovement(g: Game, hero: String, cardId: String) {
            if (g.posOf(hero) == null) return
            val n = g.cardValue(hero, cardId, Stat.MOVEMENT, ActionKind.MOVEMENT) ?: return
            if (n <= 0 || !g.actionAllowed(hero, cardId, ActionKind.MOVEMENT, false)) return
            val cap = g.asActor(hero) {
                g.sources().mapNotNull { (b, s) -> b.movementCap(g, s, MoveQuery(hero, true, hero)) }.minOrNull()
            }
            if (cap != null && cap < n) return
            val spec = MoveSpec(hero, n, n, straightLine = true, movementAction = true)
            try {
                g.asActor(hero) {
                    g.trigger(Trigger.BeforeAction(hero, cardId, ActionKind.MOVEMENT, false, g.isBasic(cardId)))
                    if (g.posOf(hero) != null && g.canMove(spec)) {
                        g.move(spec, hero, "Move $n spaces in a straight line", optional = false, cardId)
                    }
                    g.trigger(
                        Trigger.AfterAction(
                            hero,
                            cardId,
                            ActionKind.MOVEMENT,
                            false,
                            g.isBasic(cardId),
                            false,
                            emptyList(),
                        ),
                    )
                }
            } catch (e: ActorDefeated) {
                if (e.heroId != hero) throw e
            }
        }
    }

    private object DeathSeeker : CardScript {
        override fun primary(a: ActionScope) {
            if (!a.enemyInRadiusWithDiscard()) return
            val labels = listOf("This turn: You are immune to enemy actions", "You may retrieve a discarded card")
            val possible = listOf(true, a.g.discardPile(a.heroId).isNotEmpty())
            for (i in a.chooseBullets(labels, possible, a.mayChooseBoth())) {
                if (i == 0) a.activeEffect(Timing.THIS_TURN) else a.mayRetrieve()
            }
        }

        /** "You are immune to enemy actions" (and their Active effects, p.18), never to your team's. */
        override val behavior: Behavior = object : Behavior {
            override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
                val owner = src.owner ?: return false
                if (g.heroBodyOf(q.targetId) != owner) return false
                val actor = q.actorId ?: return false
                return g.isHero(actor) && g.isEnemy(owner, actor)
            }
        }
    }

    private class Trail(private val perPoint: Boolean, private val max: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Before the attack: You may move up to 1 space (for every) empty minion spawn point in radius in
            // the battle zone (up to a maximum of N spaces)." Counted before moving.
            val points = a.g.within(a.pos, a.radius).count { a.g.emptyMinionSpawnInBattleZone(it) }
            val n = if (perPoint) minOf(points, max) else minOf(points, 1)
            if (n > 0 && a.g.canMove(MoveSpec(a.heroId, 1, n))) a.moveSelf(0, n)
            // "Target a unit adjacent to you."
            val t = a.adjacentAttackTarget() ?: return
            a.attack(t)
        }
    }

    private class Fury(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: If an enemy hero in radius has a card in the discard, (may) repeat once / up
            // to five times on different targets." Checked once, after this attack.
            if (a.isRepeat || !a.enemyInRadiusWithDiscard()) return
            a.repeat(times = times, differentTarget = true, prompt = "Repeat ${a.card.name} on a different target?")
        }
    }

    private class Shadow(private val nextTo: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // "Place yourself into an empty minion spawn point in range in the battle zone" (Creeping Shadow:
            // "or into a space in range adjacent to such a spawn point").
            val spaces = g.within(a.pos, a.range).filter { h ->
                g.emptyMinionSpawnInBattleZone(h) ||
                    (nextTo && g.isEmpty(h) && HexGrid.neighbors(h).any { g.emptyMinionSpawnInBattleZone(it) })
            }
            if (spaces.isEmpty() || !g.canPlace(a.heroId, a.heroId)) a.fizzle("no empty minion spawn point in range")
            val h = a.chooseSpace("Place yourself into a space in range", spaces)!!
            g.place(a.heroId, h, a.heroId)
        }
    }

    private class Twist(private val move: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // "Swap with a unit adjacent to you, or with an enemy hero in range with a card in the discard."
            val adjacent = a.unitsWithin(1)
            val accused = a.unitsWithin(a.range).filter { id ->
                g.isHero(id) && a.isEnemy(id) && g.heroBodyOf(id)?.let { g.discardPile(it).isNotEmpty() } == true
            }
            val targets = (adjacent + accused).distinct().filter {
                a.canTarget(it) && g.canSwap(a.heroId, it, a.heroId)
            }
            if (targets.isEmpty()) a.fizzle("nothing to swap with")
            val t = a.target(a.chooseUnit("Swap with a unit", targets)!!)
            g.swap(a.heroId, t, a.heroId)
            // "Move up to N spaces."
            a.moveSelf(0, move)
        }
    }

    private class Seeds(private val max: Int, private val exact: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // "Target an enemy unit in radius occupying a spawn point" (any spawn point, minion or hero).
            val targets = a.unitsWithin(a.radius).filter { id ->
                a.isEnemy(id) && g.posOf(id)?.let { g.isSpawnPoint(it) } == true && a.canTarget(id) &&
                    (!exact || (g.canDisplace(id, Displacement.MOVE, a.heroId) && a.canMoveOther(id, 1)))
            }
            if (targets.isEmpty()) a.fizzle("no enemy unit in radius on a spawn point")
            val t = a.target(a.chooseUnit("Target an enemy unit in radius occupying a spawn point", targets)!!)
            // "Move that unit 1 space" (mandatory) / "up to N spaces".
            if (exact) a.moveOther(t, 1) else a.moveOther(t, 0, max, optional = true)
        }
    }

    private class Evil(private val greater: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // "An enemy hero in range chooses one —" (FAQ: required, else the card fizzles).
            val heroes = a.unitsWithin(a.range).filter { g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
                .mapNotNull { g.heroBodyOf(it) }.distinct()
            if (heroes.isEmpty()) a.fizzle("no enemy hero in range")
            val v = a.target(a.chooseUnit("An enemy hero in range chooses one", heroes)!!)
            // FAQ: "That Hero then picks one of the two options, regardless of whether they will have any effect."
            val labels = if (greater) {
                listOf(
                    "${g.heroName(v)} discards a card, or is defeated",
                    "${g.heroName(a.heroId)} defeats up to 3 minions adjacent to him",
                )
            } else {
                listOf(
                    "${g.heroName(v)} discards a card, if able",
                    "${g.heroName(a.heroId)} may defeat a minion adjacent to him",
                )
            }
            val pick = a.chooseOption("Choose one", labels, chooser = v)
            if (pick == 0) {
                if (greater) a.discardOrDefeat(v) else a.discardIfAble(v)
                return
            }
            // Friendly minions can never be defeated (p.13); FAQ: the (up to) 3 are chosen together, so a heavy
            // minion still immune when they are chosen cannot be one of them.
            val minions = a.unitsWithin(1).filter { g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
            val chosen = if (greater) {
                a.chooseUpTo(3, "Defeat a minion adjacent to you", minions)
            } else {
                listOfNotNull(a.chooseUnit("You may defeat a minion adjacent to you", minions, optional = true))
            }
            for (m in chosen) {
                a.target(m)
                g.defeatMinion(m, a.heroId)
            }
        }
    }

    private object GrimReaper : CardScript {
        override val hasPrimary: Boolean get() = false

        /** "When performing basic actions, you may choose one, or both." */
        override val behavior: Behavior = object : Behavior {
            override fun choiceLimit(g: Game, src: Src, q: ChoiceQuery): Int? =
                if (q.heroId == src.owner && q.basic) 2 else null
        }
    }
}
