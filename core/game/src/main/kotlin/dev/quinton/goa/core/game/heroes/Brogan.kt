package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.BattleParticipant
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.DefeatOutcome
import dev.quinton.goa.core.game.effects.DisplaceQuery
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MinionBattleQuery
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Replacement
import dev.quinton.goa.core.game.effects.ReplacementResult
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseDistance
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.radiusOf
import dev.quinton.goa.core.game.effects.tokensWithin
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.battleZone
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.discard
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.hand
import dev.quinton.goa.core.game.engine.isBasic
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor

/**
 * Brogan the Destroyer (Blue in the rulebook's first game). Card text quoted verbatim above each
 * registration (`catalog/heroes/brogan.json`); `BroganTest` follows it clause by clause with the FAQ.
 */
object BroganHero : HeroModule {
    override val heroId: String = "brogan"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Onslaught: "Target a unit adjacent to you. After the attack: Move into the space it occupied, if able."
        r.card("brogan-gold", Onslaught(heroOnly = false))
        // Leap Forward (Handicap): "Target a hero adjacent to you. After the attack: Move into the space it occupied, if able."
        r.card("brogan-handicap", Onslaught(heroOnly = true))
        // Bulwark: "You may retrieve a discarded card. This turn: You and friendly units in radius cannot
        // be moved, pushed, swapped or placed by enemy heroes."
        r.card("brogan-silver", Bulwark)
        // Mad Dash / Bullrush / Furious Charge: "Before the attack: Move 2 (2 or 3; 2, 3, or 4) spaces in
        // a straight line to a space adjacent to an enemy unit, then target that unit. (If you cannot
        // make this move, you cannot attack.)"
        r.card("brogan-red-1", Charge(2, 2))
        r.card("brogan-red-2a", Charge(2, 3))
        r.card("brogan-red-3a", Charge(2, 4))
        // Throwing Axe: "Choose one — • Target a unit adjacent to you. • You may discard a card; if you
        // do, target a unit in range." Throwing Spear: "… • You may discard a card. If you have a card in
        // the discard, target a unit in range."
        r.card("brogan-red-2b", Throw(spear = false))
        r.card("brogan-red-3b", Throw(spear = true))
        // Shield / Bolster: "This round: When any friendly minion in radius is defeated you may discard a
        // silver card. If you do, the minion is not removed. (The enemy hero still gains the coins for
        // defeating the minion.)" Fortify: "… you may discard a basic card. …"
        r.card("brogan-green-1", Shield(basicAny = false))
        r.card("brogan-green-2a", Shield(basicAny = false))
        r.card("brogan-green-3a", Shield(basicAny = true))
        // War Drummer: "A friendly hero in range gains 1 coin; if any hero was defeated this round, that
        // friendly hero gains 3 coins instead." Master Skald: "… 2 coins; … 4 coins instead."
        r.card("brogan-green-2b", Drummer(1, 3))
        r.card("brogan-green-3b", Drummer(2, 4))
        // Brutal Jab / Mighty Punch: "You may move 1 space. Push an enemy unit or a token adjacent to you
        // up to 1 (2) space(s)." Savage Kick: "Move up to 2 spaces. Push an enemy unit or a token
        // adjacent to you up to 2 spaces."
        r.card("brogan-blue-1", Jab(moveUpTo = false, push = 1))
        r.card("brogan-blue-2a", Jab(moveUpTo = false, push = 2))
        r.card("brogan-blue-3a", Jab(moveUpTo = true, push = 2))
        // Shield Bash: "An enemy hero adjacent to you who has played an attack card this turn discards a
        // card, if able." Counterattack: "… discards a card, or is defeated."
        r.card("brogan-blue-2b", Bash(orDefeated = false))
        r.card("brogan-blue-3b", Bash(orDefeated = true))
        // One Man Army (Ultimate): "During minion battle you count as a heavy minion; if you would be
        // removed, lose the push instead."
        r.card("brogan-purple-4", OneManArmy)
    }

    private class Onslaught(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            val space = a.g.posOf(t)!!
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: Move into the space it occupied, if able." FAQ: mandatory if it is empty.
            if (a.g.isEmpty(space, a.heroId)) {
                val spec = MoveSpec(a.heroId, 1, 1, endFilter = { it == space })
                if (a.g.canMove(spec)) a.g.move(spec, a.heroId, "Move into the space it occupied", optional = false, sourceCardId = a.cardId)
            }
        }
    }

    private object Bulwark : CardScript {
        override fun primary(a: ActionScope) {
            // "You may retrieve a discarded card."
            a.mayRetrieve()
            // "This turn: You and friendly units in radius cannot be moved, pushed, swapped or placed by enemy heroes."
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun forbidsDisplacement(g: Game, src: Src, q: DisplaceQuery): Boolean {
                val owner = src.owner ?: return false
                val by = q.byHeroId ?: return false
                if (!g.isHero(by) || !g.isEnemy(owner, by)) return false
                if (!g.isUnit(q.unitId)) return false
                return g.heroBodyOf(q.unitId) == owner || (g.isFriendly(owner, q.unitId) && g.inRadiusOf(src, q.unitId))
            }
        }
    }

    private class Charge(private val min: Int, private val max: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Before the attack: Move N spaces in a straight line to a space adjacent to an enemy unit"
            // (FAQ: mandatory; the move may end next to an immune unit, and then the attack fizzles).
            val adjacentToEnemy = { h: dev.quinton.goa.core.model.board.Hex ->
                a.g.unitsAdjacentTo(h).any { it != a.heroId && a.isEnemy(it) }
            }
            a.moveSelf(min, max, straightLine = true, endFilter = adjacentToEnemy)
            // "then target that unit."
            val t = a.adjacentAttackTarget("Target an enemy unit adjacent to you") ?: return
            a.attack(t)
        }
    }

    private class Throw(private val spear: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val adjacentFirst = a.unitsWithin(1).any { a.isEnemy(it) && a.canTarget(it) }
            val labels = listOf(
                "Target a unit adjacent to you",
                if (spear) {
                    "You may discard a card. If you have a card in the discard, target a unit in range"
                } else {
                    "You may discard a card; if you do, target a unit in range"
                },
            )
            // "Choose one —": a bullet whose first instruction can't be done is not offered (FAQ).
            val pick = if (adjacentFirst) a.chooseOption("Choose one", labels) else 1
            if (pick == 0) {
                val t = a.adjacentAttackTarget() ?: return
                a.attack(t)
                return
            }
            val hand = a.g.hand(a.heroId)
            val discarded = if (hand.isNotEmpty()) {
                a.chooseCard("You may discard a card", hand, optional = true)?.also { a.g.discard(a.heroId, it) }
            } else {
                null
            }
            val may = if (spear) a.g.discardPile(a.heroId).isNotEmpty() else discarded != null
            if (!may) return
            val t = a.rangedAttackTarget(optional = false) ?: return
            a.attack(t)
        }
    }

    private class Shield(private val basicAny: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_ROUND)
        }

        override val behavior: Behavior = object : Behavior {
            override fun replace(g: Game, src: Src, r: Replacement): ReplacementResult {
                if (r !is Replacement.MinionDefeat) return ReplacementResult()
                val owner = src.owner ?: return ReplacementResult()
                val m = g.state.minion(r.minionId) ?: return ReplacementResult()
                if (m.team != g.teamOf(owner) || !g.inRadiusOf(src, r.minionId)) return ReplacementResult()
                val cards = g.hand(owner).filter { id ->
                    val c = g.card(id)
                    if (basicAny) g.isBasic(id) else c.color == CardColor.SILVER && c.tier == null
                }
                if (cards.isEmpty()) return ReplacementResult()
                val what = if (basicAny) "a basic card" else "a silver card"
                val opts = cards.map { Choice.Card(it) as Choice } + Choice.Decline("Let it be removed")
                val c = g.ask(owner, DecisionKind.CHOOSE_CARD, "${g.name(r.minionId)} is defeated: discard $what so it is not removed?", opts, src.cardId, auto = false)
                if (c !is Choice.Card) return ReplacementResult()
                g.discard(owner, c.cardId)
                return ReplacementResult(DefeatOutcome.NOT_REMOVED, handled = true)
            }
        }
    }

    private class Drummer(private val coins: Int, private val coinsIfDefeat: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "A friendly hero in range gains N coins; if any hero was defeated this round, … instead."
            val friends = a.unitsWithin(a.range).filter { a.g.isHero(it) && a.isFriendly(it) && a.canTarget(it) }
                .map { a.g.heroBodyOf(it)!! }.distinct()
            if (friends.isEmpty()) a.fizzle("no friendly hero in range")
            val f = a.target(a.chooseUnit("A friendly hero in range gains coins", friends)!!)
            a.g.gainCoins(f, if (a.g.state.heroDefeatedThisRound) coinsIfDefeat else coins, a.card.name)
        }
    }

    private class Jab(private val moveUpTo: Boolean, private val push: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "You may move 1 space." / "Move up to 2 spaces."
            if (moveUpTo) a.moveSelf(0, 2) else a.moveSelf(1, optional = true)
            // "Push an enemy unit or a token adjacent to you up to N spaces." Mandatory: a target is needed.
            val targets = (a.unitsWithin(1).filter { a.isEnemy(it) } + a.tokensWithin(1)).filter { a.canTarget(it) }
            if (targets.isEmpty()) a.fizzle("nothing adjacent to push")
            val t = a.chooseObject("Push an enemy unit or a token adjacent to you", targets)!!
            val d = a.chooseDistance((0..push).toList(), "Push ${a.g.name(t)} how many spaces?")
            a.pushAway(t, d)
        }
    }

    private class Bash(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "An enemy hero adjacent to you who has played an attack card this turn" (FAQ: whether or
            // not it attacked, and even if that card is gone).
            val played = a.g.state.turnRecord.playedAttack
            val victims = a.unitsWithin(1).filter { a.g.isHero(it) && a.isEnemy(it) && a.g.heroBodyOf(it) in played && a.canTarget(it) }
            if (victims.isEmpty()) a.fizzle("no enemy hero adjacent who played an attack card")
            val v = a.chooseUnit("Choose an enemy hero who played an attack card", victims)!!
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
        }
    }

    private object OneManArmy : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            /** "During minion battle you count as a heavy minion" — in the Battle Zone of that Lane. */
            override fun minionBattle(g: Game, src: Src, q: MinionBattleQuery) {
                val owner = src.owner ?: return
                val at = g.posOf(owner) ?: return
                if (g.zoneOf(at) != g.battleZone(q.lane)) return
                q.participants += BattleParticipant(owner, g.teamOf(owner)!!, MinionType.HEAVY, virtual = true)
            }

            /** "if you would be removed, lose the push instead." */
            override fun replace(g: Game, src: Src, r: Replacement): ReplacementResult {
                if (r !is Replacement.BattleRemoval || r.participantId != src.owner) return ReplacementResult()
                val team = g.teamOf(src.owner)!!
                g.state.pushDue[r.lane] = team.opponent
                g.note("${g.heroName(src.owner)} would be removed: ${Game.teamName(team)} loses the push instead.")
                return ReplacementResult(handled = true)
            }
        }
    }
}
