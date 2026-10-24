package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canFastTravel
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.giveMarker
import dev.quinton.goa.core.game.engine.isBasic
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isSkillCard
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.repeatForbidden
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.secondaryAttackTargets
import dev.quinton.goa.core.game.engine.takeCoins
import dev.quinton.goa.core.game.engine.tokensAdjacentTo
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Tigerclaw the Cutpurse. Card text quoted verbatim above each registration
 * (`catalog/heroes/tigerclaw.json`); `TigerclawTest` follows it clause by clause with the FAQ.
 * Readings the text leaves open are in `core/game/rulings/tigerclaw.md`.
 */
object TigerclawHero : HeroModule {
    override val heroId: String = "tigerclaw"
    override val complete: Boolean = true

    /** The Poison marker ({marker_poison}); its strength (1 or 2) is in [dev.quinton.goa.core.game.engine.Marker.data]. */
    const val POISON: String = "marker_poison"
    private const val STRENGTH = "n"

    override fun register(r: Registry) {
        // Blink Strike: "Before the attack: Move 2 spaces in a straight line through an enemy unit; target
        // that unit. (If you cannot make this move, you cannot attack.)"
        r.card("tigerclaw-gold", Blink(heroOnly = false))
        // Nimble Strike (Handicap): "Before the attack: Move 2 spaces in a straight line through an enemy
        // hero; target that hero."
        r.card("tigerclaw-handicap", Blink(heroOnly = true))
        // Blend Into Shadows: "If you are adjacent to terrain, place yourself into a space in radius; if you
        // do, Next turn: You are immune to enemy attack actions."
        r.card("tigerclaw-silver", BlendIntoShadows)
        // Hit and Run: "Target a unit adjacent to you. After the attack: You may move 1 space."
        r.card("tigerclaw-red-1", Strike(moveBefore = false, moveAfter = After.ALWAYS))
        // Combat Reflexes: "Before the attack: You may move 1 space. Target a unit adjacent to you. After the
        // attack: If you did not move before the attack, you may move 1 space."
        r.card("tigerclaw-red-2a", Strike(moveBefore = true, moveAfter = After.IF_NOT_MOVED))
        // Leaping Strike: "Before the attack: You may move 1 space. Target a unit adjacent to you. After the
        // attack: You may move 1 space."
        r.card("tigerclaw-red-3a", Strike(moveBefore = true, moveAfter = After.ALWAYS))
        // Backstab: "Target a unit adjacent to you; if a friendly unit is adjacent to the target, +2 Attack.
        // (A “friendly unit” is another hero or a minion on your team.)"
        r.card("tigerclaw-red-2b", Backstab(ballista = false))
        // Backstab with a Ballista: "Target a unit in range; if a friendly unit is adjacent to the target +2
        // Attack, and the target cannot perform a primary action to defend."
        r.card("tigerclaw-red-3b", Backstab(ballista = true))
        // Light-Fingered: "You may move 1 space. Take 1 coin from an enemy hero adjacent to you; if you do,
        // you may move 1 space."
        r.card("tigerclaw-green-1", Thief(firstUpTo = false, maxCoins = 1, afterMax = 1))
        // Pick Pocket: "Move up to 2 spaces. Take 1 coin from an enemy hero adjacent to you; if you do, you
        // may move 1 space."
        r.card("tigerclaw-green-2a", Thief(firstUpTo = true, maxCoins = 1, afterMax = 1))
        // Master Thief: "Move up to 2 spaces. Take 1 or 2 coins from an enemy hero adjacent to you; if you
        // do, you may move up to 2 spaces."
        r.card("tigerclaw-green-3a", Thief(firstUpTo = true, maxCoins = 2, afterMax = 2))
        // Poisoned Dagger: "Give a hero in range a Poison marker. The hero with a poison marker has -1
        // Initiative, -1 Attack and -1 Defense." Poisoned Dart: "… -2 Initiative, -2 Attack and -2 Defense."
        r.card("tigerclaw-green-2b", Poison(strength = 1))
        r.card("tigerclaw-green-3b", Poison(strength = 2))
        r.behavior(POISON, PoisonMarker)
        // Dodge: "Block a ranged attack."
        r.card("tigerclaw-blue-1", BlockCard(blocksRanged = true))
        // Sidestep: "Block a ranged attack. You may move 1 space."
        r.card("tigerclaw-blue-2a", BlockCard(blocksRanged = true, move = true))
        // Evade: "Block a ranged attack. You may move 1 space. You may retrieve your resolved or discarded
        // basic skill card."
        r.card("tigerclaw-blue-3a", BlockCard(blocksRanged = true, move = true, retrieveBasicSkill = true))
        // Parry: "Block a non-ranged attack. The attacker discards a card, if able."
        r.card("tigerclaw-blue-2b", BlockCard(blocksRanged = false, attacker = AttackerPays.DISCARD))
        // Riposte: "Block a non-ranged attack. The attacker discards a card, or is defeated."
        r.card("tigerclaw-blue-3b", BlockCard(blocksRanged = false, attacker = AttackerPays.DISCARD_OR_DEFEAT))
        // Cloak and Daggers (Ultimate): "After you perform a basic action, you may repeat it once; if you
        // repeat an attack action, you cannot target the same unit."
        r.card("tigerclaw-purple-4", CloakAndDaggers)
    }

    /** Blink Strike / Nimble Strike. */
    private class Blink(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Move 2 spaces in a straight line through an enemy unit (hero)" — mandatory (FAQ: "If Tigerclaw
            // cannot move through the target in a straight line, the effect will fizzle"). Passing through the
            // unit is the only obstacle the text lets him cross: the space between must hold it, the end must be empty.
            val start = a.pos
            val passed: (Hex) -> Hex = { end -> Hex((start.q + end.q) / 2, (start.r + end.r) / 2) }
            val through: (Hex) -> Boolean = { end ->
                val mid = a.g.occupant(passed(end))
                mid != null && a.g.isUnit(mid) && a.isEnemy(mid) && (!heroOnly || a.g.isHero(mid))
            }
            val path = a.g.move(
                MoveSpec(a.heroId, 2, 2, straightLine = true, ignoreObstacles = true, endFilter = through),
                a.heroId,
                "Move 2 spaces in a straight line through an enemy ${if (heroOnly) "hero" else "unit"}",
                optional = false,
                sourceCardId = a.cardId,
            )
            // "target that unit." [APP] Moving through an immune unit is allowed (presence); then there is no target.
            val unit = a.g.occupant(path.first()) ?: a.fizzle("no target")
            val t = a.attackTarget("Target that ${if (heroOnly) "hero" else "unit"}") { it == unit } ?: return
            a.attack(t)
        }
    }

    private object BlendIntoShadows : CardScript {
        override fun primary(a: ActionScope) {
            // "If you are adjacent to terrain" (p.19: water and rocks; a unit that counts as terrain too).
            if (HexGrid.neighbors(a.pos).none { a.g.isTerrain(it) }) a.fizzle("not adjacent to terrain")
            // "place yourself into a space in radius"
            val spaces = a.g.within(a.pos, a.radius).filter { a.g.isEmpty(it, a.heroId) }
            if (spaces.isEmpty() || !a.g.canPlace(a.heroId, a.heroId)) a.fizzle("no space to place Tigerclaw")
            val h = a.chooseSpace("Place yourself into a space in radius", spaces)!!
            a.g.place(a.heroId, h, a.heroId)
            // "if you do, Next turn: You are immune to enemy attack actions."
            a.activeEffect(Timing.NEXT_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
                val owner = src.owner ?: return false
                if (g.heroBodyOf(q.targetId) != owner || q.kind != ActionKind.ATTACK) return false
                val actor = q.actorId ?: return false
                return g.isEnemy(owner, actor)
            }
        }
    }

    private enum class After { ALWAYS, IF_NOT_MOVED }

    /** Hit and Run, Combat Reflexes, Leaping Strike. */
    private class Strike(private val moveBefore: Boolean, private val moveAfter: After) : CardScript {
        override fun primary(a: ActionScope) {
            // "Before the attack: You may move 1 space."
            val moved = moveBefore && a.moveSelf(1, optional = true).isNotEmpty()
            // "Target a unit adjacent to you."
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: [If you did not move before the attack,] you may move 1 space."
            if (moveAfter == After.ALWAYS || !moved) a.moveSelf(1, optional = true)
        }
    }

    /** Backstab / Backstab with a Ballista. */
    private class Backstab(private val ballista: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = (if (ballista) a.rangedAttackTarget() else a.adjacentAttackTarget()) ?: return
            // "if a friendly unit is adjacent to the target" — presence (p.18), you never count (p.17).
            val friendNext = {
                a.g.posOf(t)?.let { p -> a.g.unitsAdjacentTo(p).any { a.isFriendly(it) } } == true
            }
            val backed = friendNext()
            a.attack(
                t,
                bonus = { if (friendNext()) 2 else 0 },
                // Ballista: "and the target cannot perform a primary action to defend."
                noPrimaryDefense = ballista && backed,
            )
        }
    }

    /** Light-Fingered, Pick Pocket, Master Thief. */
    private class Thief(private val firstUpTo: Boolean, private val maxCoins: Int, private val afterMax: Int) :
        CardScript {
        override fun primary(a: ActionScope) {
            // "You may move 1 space." / "Move up to 2 spaces."
            if (firstUpTo) a.moveSelf(0, 2) else a.moveSelf(1, optional = true)
            // "Take 1 (1 or 2) coin(s) from an enemy hero adjacent to you" — mandatory (FAQ: no adjacent enemy
            // hero, the card fizzles and he does not make the final move).
            val heroes = a.unitsWithin(1).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            if (heroes.isEmpty()) a.fizzle("no enemy hero adjacent")
            val v = a.target(a.chooseUnit("Take coins from an enemy hero adjacent to you", heroes)!!)
            val victim = a.g.heroBodyOf(v)!!
            val most = minOf(maxCoins, a.g.hero(victim).coins)
            val n = if (most >= 2) a.chooseNumber("Take how many coins?", (1..most).toList()) else most
            val taken = a.g.takeCoins(a.heroId, victim, n)
            // "if you do, you may move 1 space (up to 2 spaces)." [APP] Taking 0 (no coins) is not "doing" it.
            if (taken <= 0) return
            if (afterMax == 1) a.moveSelf(1, optional = true) else a.moveSelf(0, afterMax)
        }
    }

    private class Poison(private val strength: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Give a hero in range a Poison marker." Any hero (the text does not say enemy), never you (p.10).
            val heroes = a.unitsWithin(a.range).filter { a.g.isHero(it) && a.g.heroBodyOf(it) != a.heroId && a.canTarget(it) }
            if (heroes.isEmpty()) a.fizzle("no hero in range")
            val v = a.target(a.chooseUnit("Give a hero in range a Poison marker", heroes)!!)
            // One Poison marker: giving it again moves it (Game.giveMarker).
            val m = a.g.giveMarker(a.g.heroBodyOf(v)!!, POISON, a.heroId)
            m.data[STRENGTH] = strength.toString()
            a.g.touch()
        }
    }

    /**
     * "The hero with a poison marker has -1 (-2) Initiative, -1 (-2) Attack and -1 (-2) Defense." FAQ: values
     * may drop to 0 and below.
     */
    private object PoisonMarker : Behavior {
        override fun statDelta(g: Game, src: Src, q: StatQuery): Int {
            val holder = src.holder ?: return 0
            if (g.heroBodyOf(q.unitId) != holder) return 0
            if (q.stat != Stat.INITIATIVE && q.stat != Stat.ATTACK && q.stat != Stat.DEFENSE) return 0
            return -(src.marker?.data?.get(STRENGTH)?.toIntOrNull() ?: 1)
        }
    }

    private enum class AttackerPays { NONE, DISCARD, DISCARD_OR_DEFEAT }

    /** Dodge, Sidestep, Evade, Parry, Riposte. */
    private class BlockCard(
        private val blocksRanged: Boolean,
        private val move: Boolean = false,
        private val retrieveBasicSkill: Boolean = false,
        private val attacker: AttackerPays = AttackerPays.NONE,
    ) : CardScript {
        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            // "Block a (non-)ranged attack." [APP] A mandatory step (p.10): when the condition fails the Block
            // fails and the rest of the text is skipped.
            val ok = d.attack.ranged == blocksRanged
            d.block(ok)
            if (!ok) return
            val g = d.g
            // "You may move 1 space."
            if (move && g.canMove(MoveSpec(d.heroId, 1, 1))) {
                g.move(MoveSpec(d.heroId, 1, 1), d.heroId, "You may move 1 space", optional = true, sourceCardId = d.cardId)
            }
            // "You may retrieve your resolved or discarded basic skill card."
            if (retrieveBasicSkill) {
                val cards = d.hero.cards.filter {
                    (it.zone == Zone.RESOLVED || it.zone == Zone.DISCARD) && g.isBasic(it.id) && g.isSkillCard(d.heroId, it.id)
                }.map { it.id }
                if (cards.isNotEmpty()) {
                    val c = g.ask(
                        d.heroId,
                        DecisionKind.CHOOSE_CARD,
                        "You may retrieve your resolved or discarded basic skill card",
                        cards.map { Choice.Card(it) as Choice } + Choice.Decline(),
                        d.cardId,
                    )
                    if (c is Choice.Card) g.retrieve(d.heroId, c.cardId)
                }
            }
            // "The attacker discards a card, if able." / "…, or is defeated."
            if (attacker != AttackerPays.NONE && d.canTarget(d.attack.attackerId)) {
                g.forceDiscard(
                    d.attack.attackerId,
                    d.heroId,
                    orDefeated = attacker == AttackerPays.DISCARD_OR_DEFEAT,
                    sourceCardId = d.cardId,
                )
            }
        }
    }

    private object CloakAndDaggers : CardScript {
        override val hasPrimary: Boolean get() = false

        private val REPEATABLE = setOf(ActionKind.ATTACK, ActionKind.SKILL, ActionKind.MOVEMENT, ActionKind.FAST_TRAVEL, ActionKind.CLEAR)

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                // "After you perform a basic action" (p.9: every action on a gold or silver card), once.
                if (t !is Trigger.AfterAction || t.heroId != owner || !t.basic || t.repeat) return
                if (t.kind !in REPEATABLE) return
                val cardId = t.cardId ?: return
                val at = g.posOf(owner) ?: return
                if (g.repeatForbidden(owner)) return
                // "if you repeat an attack action, you cannot target the same unit."
                val excluded = if (t.kind == ActionKind.ATTACK) t.targets.toSet() else emptySet()
                if (!worthAsking(g, owner, cardId, t, at, excluded)) return
                val c = g.ask(
                    owner,
                    DecisionKind.CONFIRM,
                    "Cloak and Daggers: repeat this basic action once?",
                    listOf(Choice.Yes, Choice.Decline("Don't repeat")),
                    src.cardId,
                    auto = false,
                )
                if (c !is Choice.Yes) return
                g.performAction(owner, cardId, t.kind, t.primary, isRepeat = true, excluded = excluded)
            }
        }

        /** Skip the question when the repeat could not do anything at all. */
        @Suppress("LongParameterList")
        private fun worthAsking(g: Game, owner: String, cardId: String, t: Trigger.AfterAction, at: Hex, excluded: Set<String>) =
            when {
                t.primary -> g.primaryPossible(owner, cardId)
                t.kind == ActionKind.ATTACK -> g.secondaryAttackTargets(owner, cardId).any { it !in excluded }
                t.kind == ActionKind.FAST_TRAVEL -> g.canFastTravel(owner, cardId)
                t.kind == ActionKind.CLEAR -> g.tokensAdjacentTo(at).isNotEmpty()
                else -> true
            }
    }
}
