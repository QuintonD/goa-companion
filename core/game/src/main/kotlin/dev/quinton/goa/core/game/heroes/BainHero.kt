package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.LifeQuery
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.objectsWithin
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
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.discard
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.giveMarker
import dev.quinton.goa.core.game.engine.hand
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isObstacle
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.noTargetReason
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.removeMarker
import dev.quinton.goa.core.game.engine.repeatForbidden
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.heroes.batch2.chooseBullets
import dev.quinton.goa.core.model.catalog.CardColor

/**
 * Bain the Bounty Hunter. Each card's verbatim text (`catalog/heroes/bain.json`) is quoted above its
 * registration; `BainTest` follows it clause by clause with the hero's FAQ. Readings:
 * `core/game/rulings/bain.md`.
 *
 * The Bounty marker is one marker ([BOUNTY], given by Bain): "A hero with the Bounty marker spends 1
 * additional Life counter when defeated" ([BountyMarker], read at the defeat). It is returned at the end
 * of the round and when its holder is defeated (p.8, p.13), not when Bain is (FAQ).
 */
object BainHero : HeroModule {
    override val heroId: String = "bain"
    override val complete: Boolean = true

    const val BOUNTY: String = "marker_bounty"

    override fun register(r: Registry) {
        r.behavior(BOUNTY, BountyMarker)
        // Dead or Alive: "Target a unit adjacent to you. After the attack: You may give an enemy hero in
        // radius the Bounty marker. A hero with the Bounty marker spends 1 additional Life counter when defeated."
        r.card("bain-gold", DeadOrAlive)
        // Side Quest (Handicap): "If you are adjacent to an enemy unit, give a hero in radius the Bounty
        // marker. A hero with the Bounty marker spends 1 additional Life counter when defeated."
        r.card("bain-handicap", SideQuest)
        // Get over here!: "Target a unit or a token in range and in a straight line, with no obstacles
        // between you and the target. Move the target towards you in a straight line, until you are adjacent."
        r.card("bain-silver", GetOverHere)
        // Light Crossbow / Heavy Crossbow / Arbalest: "Target a unit in range and in a straight line with no
        // units or terrain between you."
        r.card("bain-red-1", Crossbow)
        r.card("bain-red-2a", Crossbow)
        r.card("bain-red-3a", Crossbow)
        // Close Call: "If a hero in play has a Bounty marker, block the attack and that hero gives the
        // marker to you. (The marker's effect is applied to you.)"
        r.card("bain-green-1", Escape(Escape.Mode.TAKE))
        // Narrow Escape: "If a hero in play has a Bounty marker, block the attack and retrieve the marker."
        r.card("bain-green-2a", Escape(Escape.Mode.RETRIEVE))
        // Perfect Getaway: "If a hero in play has a Bounty marker, block the attack."
        r.card("bain-green-3a", Escape(Escape.Mode.BLOCK))
        // A Game of Chance: "An enemy hero in radius with two or more cards in hand chooses one of those
        // cards. Guess that card's color, then reveal it. If you guessed correctly, discard that card;
        // otherwise you gain 1 coin. (You can only guess colors that could be in that player's hand.)"
        // Dead Man's Hand: "… otherwise you gain 2 coins." We're Not Done Yet!: "… otherwise may repeat
        // once or gain 2 coins."
        r.card("bain-blue-1", Chance(coins = 1, mayRepeat = false))
        r.card("bain-blue-2a", Chance(coins = 2, mayRepeat = false))
        r.card("bain-blue-3a", Chance(coins = 2, mayRepeat = true))
        // Hand Crossbow: "Choose one — • Target a hero in range with a Bounty marker. • Target a unit
        // adjacent to you." Hunter-Seeker: "Choose one, or both, on different targets — …" (same bullets)
        r.card("bain-red-2b", Hunter(both = false))
        r.card("bain-red-3b", Hunter(both = true))
        // Vantage Point / High Ground: "Ignore obstacles. If a hero in play has a Bounty marker, +1 (+2) movement."
        r.card("bain-green-2b", Vantage(bonus = 1))
        r.card("bain-green-3b", Vantage(bonus = 2))
        // Drinking Buddies: "You may have a hero in radius retrieve a discarded card. If they do, you may
        // also retrieve a discarded card." Another One!: "… End of turn: May repeat once."
        r.card("bain-blue-2b", Buddies(endOfTurnRepeat = false))
        r.card("bain-blue-3b", Buddies(endOfTurnRepeat = true))
        // A Complicated Profession (Ultimate): "After you give a hero the Bounty marker, that hero discards
        // a card, if able."
        r.card("bain-purple-4", Profession)
    }

    // ---- the Bounty marker ---------------------------------------------------------------------

    /** The hero holding the Bounty marker, if any. */
    private fun Game.bountyHolder(): String? = state.heroes.firstOrNull { h -> h.markers.any { it.kind == BOUNTY } }?.id

    /**
     * "If a hero in play has a Bounty marker" — another hero than [self] (p.10: "If your action is affecting
     * you (your hero), it will say so in the card text"). Designer, BGG 3676852: "Narrow Escape cannot be used
     * by Bain to block an attack if Bain himself has the marker. Otherwise the card would say '...If you, or a
     * hero in play...'."
     */
    private fun Game.bountyInPlay(self: String): String? = bountyHolder()?.takeIf { heroBodyOf(it) != heroBodyOf(self) }

    private fun Game.holdsBounty(heroId: String): Boolean = hero(heroId).markers.any { it.kind == BOUNTY }

    /** "A hero with the Bounty marker spends 1 additional Life counter when defeated." */
    private object BountyMarker : Behavior {
        override fun extraLifeCost(g: Game, src: Src, q: LifeQuery): Int = if (src.holder == q.heroId) 1 else 0
    }

    /**
     * Bain gives [heroId] the marker (one marker: giving it moves it, p.18). [APP] The hero already holding
     * it cannot be given it again (callers leave it out of the options).
     */
    private fun ActionScope.giveBounty(heroId: String) {
        g.giveMarker(heroId, BOUNTY, this.heroId)
    }

    /** Heroes in radius (bodies mapped to their hero), never Bain, never the one already holding the marker. */
    private fun ActionScope.heroesInRadius(enemyOnly: Boolean): List<String> =
        unitsWithin(radius).filter { g.isHero(it) && (!enemyOnly || isEnemy(it)) && canTarget(it) }
            .mapNotNull { g.heroBodyOf(it) }.filter { it != heroId && !g.holdsBounty(it) }.distinct()

    // ---- cards ---------------------------------------------------------------------------------

    private object DeadOrAlive : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: You may give an enemy hero in radius the Bounty marker."
            val h = a.chooseUnit(
                "You may give an enemy hero in radius the Bounty marker",
                a.heroesInRadius(enemyOnly = true),
                optional = true,
            ) ?: return
            a.target(h)
            a.giveBounty(h)
        }
    }

    private object SideQuest : CardScript {
        override fun primary(a: ActionScope) {
            // "If you are adjacent to an enemy unit" — presence, immune units count (p.18).
            if (a.unitsWithin(1).none { a.isEnemy(it) }) return
            // "give a hero in radius the Bounty marker" — any hero, friend or enemy, never Bain (p.10).
            val heroes = a.heroesInRadius(enemyOnly = false)
            if (heroes.isEmpty()) a.fizzle("no hero in radius to give the Bounty marker")
            val h = a.target(a.chooseUnit("Give a hero in radius the Bounty marker", heroes)!!)
            a.giveBounty(h)
        }
    }

    private object GetOverHere : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            val me = a.pos
            // "Target a unit or a token in range and in a straight line, with no obstacles between you and
            // the target."
            val targets = a.objectsWithin(a.range).filter { id ->
                val p = g.posOf(id) ?: return@filter false
                val line = HexGrid.straightLine(me, p) ?: return@filter false
                line.dropLast(1).none { g.isObstacle(it, id) } && a.canTarget(id) &&
                    g.canDisplace(id, Displacement.MOVE, a.heroId)
            }
            if (targets.isEmpty()) a.fizzle("no unit or token in a straight line with no obstacles between")
            val t = a.target(a.chooseObject("Target a unit or a token in range and in a straight line", targets)!!)
            // "Move the target towards you in a straight line, until you are adjacent." (Card-text movement.)
            val p = g.posOf(t)!!
            val n = g.distance(me, p) - 1
            if (n <= 0) return
            val dir = HexGrid.straightLineDirection(p, me)!!
            g.move(
                MoveSpec(t, n, n, straightLine = true, directions = setOf(dir)),
                a.heroId,
                "Move ${g.name(t)} towards you until adjacent",
                optional = false,
                a.cardId,
            )
        }
    }

    /** "in a straight line with no units or terrain between you" (tokens between do not matter). */
    private fun ActionScope.clearShot(id: String): Boolean {
        val me = hero.at ?: return false
        val p = g.posOf(id) ?: return false
        val line = HexGrid.straightLine(me, p) ?: return false
        return line.dropLast(1).none { h -> g.isTerrain(h) || g.occupant(h)?.let { g.isUnit(it) } == true }
    }

    private object Crossbow : CardScript {
        override fun primary(a: ActionScope) {
            val valid: (String) -> Boolean = { a.clearShot(it) }
            val t = a.rangedAttackTarget(
                "Target a unit in range and in a straight line with no units or terrain between you",
                extra = valid,
            ) ?: return
            a.attack(t, valid = valid)
        }
    }

    /** Close Call, Narrow Escape, Perfect Getaway: Block cards (p.14) on the Bounty marker being in play. */
    private class Escape(private val mode: Mode) : CardScript {
        enum class Mode { TAKE, RETRIEVE, BLOCK }

        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            // "If a hero in play has a Bounty marker" — not Bain himself (designer, BGG 3676852).
            val holder = d.g.bountyInPlay(d.heroId)
            d.block(holder != null)
            if (holder == null) return
            val marker = d.g.hero(holder).markers.first { it.kind == BOUNTY }
            when (mode) {
                // "that hero gives the marker to you" (not Bain giving it: A Complicated Profession does not apply).
                Mode.TAKE -> d.g.giveMarker(d.heroId, BOUNTY, marker.from)

                // "retrieve the marker": back to Bain's supply.
                Mode.RETRIEVE -> {
                    d.g.removeMarker(holder, marker)
                    d.g.note("${d.g.heroName(d.heroId)} retrieves the Bounty marker.")
                }

                Mode.BLOCK -> Unit
            }
        }
    }

    private class Chance(private val coins: Int, private val mayRepeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // "An enemy hero in radius with two or more cards in hand chooses one of those cards."
            val victims = a.unitsWithin(a.radius).filter { g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
                .mapNotNull { g.heroBodyOf(it) }.distinct().filter { g.hand(it).size >= 2 }
            if (victims.isEmpty()) a.fizzle("no enemy hero in radius with two or more cards in hand")
            val v = a.target(a.chooseUnit("An enemy hero in radius chooses one of their cards", victims)!!)
            val card = a.chooseCard(
                "Choose one of your cards: ${g.heroName(a.heroId)} guesses its colour",
                g.hand(v),
                chooser = v,
            )!!
            // "Guess that card's color, then reveal it." (Only colours that could be in that hand.)
            val colours = guessable(g, v)
            val guess = colours[a.chooseOption("Guess that card's colour", colours.map(::label))!!]
            g.note(
                "${g.heroName(a.heroId)} guesses ${label(guess).lowercase()}: " +
                    "${g.heroName(v)} reveals ${g.cardName(card)}.",
            )
            // "If you guessed correctly, discard that card"
            if (g.card(card).color == guess) {
                g.discard(v, card)
                return
            }
            // "otherwise you gain N coins" / "otherwise may repeat once or gain 2 coins" (a repeated
            // instance cannot repeat, p.10, so it gains the coins).
            if (mayRepeat && !a.isRepeat && !g.repeatForbidden(a.heroId)) {
                val pick = a.chooseOption("You guessed wrong", listOf("Repeat once", "Gain $coins coins"))
                if (pick == 0) {
                    a.repeat(mandatory = true)
                    return
                }
            }
            g.gainCoins(a.heroId, coins, a.card.name)
        }

        /**
         * The colours Bain may guess: those of [victim]'s cards this round that could be in hand from what
         * is public — cards in hand and facedown cards out of it (p.10: facedown cards hide their colour);
         * faceup played, resolved or discarded cards are known to be elsewhere.
         */
        private fun guessable(g: Game, victim: String): List<CardColor> {
            val h = g.hero(victim)
            val inRound = setOf(Zone.HAND, Zone.PLAYED, Zone.RESOLVED, Zone.DISCARD)
            return h.cards.filter { c ->
                c.zone in inRound && (c.zone == Zone.HAND || c.faceDown || (c.zone == Zone.PLAYED && !g.state.revealed))
            }.map { g.card(it.id).color }.distinct().sortedBy { it.ordinal }
        }

        private fun label(c: CardColor): String = c.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    private class Hunter(private val both: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // • "Target a hero in range with a Bounty marker."  • "Target a unit adjacent to you."
            val bountied: (String) -> Boolean = { id ->
                g.isHero(id) && a.inRange(id) && g.heroBodyOf(id)?.let { g.holdsBounty(it) } == true
            }
            val adjacent: (String) -> Boolean = { a.isAdjacent(it) }
            val bullets = listOf(bountied, adjacent)
            val labels = listOf("Target a hero in range with a Bounty marker", "Target a unit adjacent to you")
            fun targets(i: Int) = g.unitsOnBoard().filter {
                a.isEnemy(it) && a.canTarget(it, ActionKind.ATTACK) &&
                    bullets[i](it)
            }
            val t0 = targets(0)
            val t1 = targets(1)
            // "on different targets": both only when two different units can be targeted.
            val pair = t0.any { x -> t1.any { y -> !same(g, x, y) } }
            val order = a.chooseBullets(labels, listOf(t0.isNotEmpty(), t1.isNotEmpty()), both && pair)
            if (order.isEmpty()) a.fizzle(noTargetReason(labels))
            var previous: String? = null
            for (i in order) {
                val not = previous
                val valid: (String) -> Boolean = { id -> bullets[i](id) && (not == null || !same(g, not, id)) }
                val t = a.attackTarget(labels[i], valid = valid) ?: return
                a.attack(t, valid = valid)
                previous = t
            }
        }

        private fun same(g: Game, x: String, y: String): Boolean =
            x == y || (g.heroBodyOf(x) != null && g.heroBodyOf(x) == g.heroBodyOf(y))
    }

    private class Vantage(private val bonus: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Ignore obstacles. If a hero in play has a Bounty marker, +N movement." (This action only; a hero
            // other than Bain, as on Close Call.)
            a.movementAction(bonus = if (a.g.bountyInPlay(a.heroId) != null) bonus else 0, ignoreObstacles = true)
        }
    }

    private class Buddies(private val endOfTurnRepeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            // "You may have a hero in radius retrieve a discarded card." (Bain picks the hero, never himself;
            // that hero picks the card.)
            val heroes = a.unitsWithin(a.radius).filter { g.isHero(it) && a.canTarget(it) }
                .mapNotNull { g.heroBodyOf(it) }.filter { it != a.heroId && g.discardPile(it).isNotEmpty() }.distinct()
            val h = a.chooseUnit("You may have a hero in radius retrieve a discarded card", heroes, optional = true)
            if (h != null) {
                a.target(h)
                val c = a.chooseCard("Retrieve a discarded card", g.discardPile(h), chooser = h)!!
                g.retrieve(h, c)
                // "If they do, you may also retrieve a discarded card."
                a.mayRetrieve()
            }
            // Another One!: "End of turn: May repeat once." (Not from the repeated instance, p.10.)
            if (endOfTurnRepeat && !a.isRepeat) a.activeEffect(Timing.END_OF_TURN)
        }

        override val behavior: Behavior? = if (endOfTurnRepeat) AnotherOne else null
    }

    private object AnotherOne : Behavior {
        override fun applyTimed(g: Game, src: Src) {
            val owner = src.owner ?: return
            val cardId = src.cardId ?: return
            if (g.posOf(owner) == null || g.repeatForbidden(owner)) return
            val c = g.ask(
                owner,
                DecisionKind.CONFIRM,
                "End of turn: repeat ${g.cardName(cardId)}?",
                listOf(Choice.Yes, Choice.Decline("Don't repeat")),
                cardId,
                auto = false,
            )
            if (c != Choice.Yes) return
            g.performAction(owner, cardId, ActionKind.SKILL, primary = true, isRepeat = true)
        }
    }

    private object Profession : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            /** "After you give a hero the Bounty marker, that hero discards a card, if able." */
            override fun on(g: Game, src: Src, t: Trigger) {
                val owner = src.owner ?: return
                if (t !is Trigger.MarkerGiven || t.kind != BOUNTY || t.byHeroId != owner || t.heroId == owner) return
                g.forceDiscard(t.heroId, owner, orDefeated = false, sourceCardId = src.cardId)
            }
        }
    }
}
