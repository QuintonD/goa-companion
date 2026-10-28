package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.SpaceQuery
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseUpTo
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.distanceTo
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.isHero
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.ownerPos
import dev.quinton.goa.core.game.effects.radiusOf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.UltimateUnlock
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.cancelEffectsOf
import dev.quinton.goa.core.game.engine.cardColorOf
import dev.quinton.goa.core.game.engine.discard
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.hand
import dev.quinton.goa.core.game.engine.isAttackCard
import dev.quinton.goa.core.game.engine.moveCard
import dev.quinton.goa.core.game.engine.turnFaceDown
import dev.quinton.goa.core.game.engine.unresolvedCard
import dev.quinton.goa.core.model.catalog.CardColor

/**
 * Takahide the Warlord. Three gold cards (Float like a Butterfly starts in hand, Sting like a Bee and
 * Strike like a Tiger in the deck: their printed reminders, dealt by `StartingCards`), a silver Handicap.
 * Card text quoted verbatim above each registration (`catalog/heroes/takahide.json`); `TakahideTest`
 * follows it clause by clause. Readings in `core/game/rulings/takahide.md`.
 */
object TakahideHero : HeroModule {
    override val heroId: String = "takahide"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Float like a Butterfly (gold, Movement 5): "Swap this card with a different gold card in your
        // deck. (This card starts the game in your hand.)" — text before movement (p.11).
        r.card("takahide-gold", FloatLikeAButterfly)
        // Sting like a Bee (gold, ranged, Range 3): "Target a unit at maximum range. After the attack: Swap
        // this card with a different gold card in your deck. (This card starts the game in your deck.)"
        r.card("takahide-gold-extra-1", GoldAttack(atMaximumRange = true))
        // Strike like a Tiger (gold): "Target a unit adjacent to you. After the attack: Swap this card with
        // a different gold card in your deck. (This card starts the game in your deck.)"
        r.card("takahide-gold-extra-2", GoldAttack(atMaximumRange = false))
        // Bushido (silver): "Swap your gold card with a different gold card in your deck; if you swap a
        // resolved or discarded card this way, place the new card facedown."
        r.card("takahide-silver", Bushido)
        // Shameful Display (Handicap, silver Block): "(You are defeated.)" — no Block condition (RULINGS 20).
        r.card("takahide-handicap", ShamefulDisplay)
        // Set an Example (radius 3) / Lead from the Front (radius 4): "Target a unit adjacent to you. After
        // the attack: A friendly hero in radius may swap their unresolved card with a card in their hand."
        r.card("takahide-red-1", SetAnExample(orDiscard = false))
        r.card("takahide-red-2a", SetAnExample(orDiscard = false))
        // Hold my Saké: "… may swap their unresolved card with a card in their hand, or in their discard."
        r.card("takahide-red-3a", SetAnExample(orDiscard = true))
        // Spinning Blade: "Target a unit adjacent to you. After the attack: This turn: Empty spaces adjacent
        // to you count as obstacles for enemy units."
        r.card("takahide-red-2b", Blade(inRadius = false))
        // Blade Helix (radius 1): "… This turn: Empty spaces in radius count as obstacles for enemy units."
        r.card("takahide-red-3b", Blade(inRadius = true))
        // Come to Aid (range 3) / Bring the Relief (range 4): "A friendly hero in range may discard a card.
        // If that hero has a card in the discard, you may move up to 3 (4) spaces."
        r.card("takahide-green-1", ComeToAid(Reward.Move(3, ignoreObstacles = false)))
        r.card("takahide-green-2a", ComeToAid(Reward.Move(4, ignoreObstacles = false)))
        // Commit Reserves: "… If that hero has a card in the discard, you may move up to 4 spaces, ignoring obstacles."
        r.card("takahide-green-3a", ComeToAid(Reward.Move(4, ignoreObstacles = true)))
        // Pledge of Allegiance / Loyal Retainer: "A friendly hero in range may discard a card. If that hero
        // has a card in the discard, both you and that hero gain 1 (2) coin(s) and you may retrieve a discarded card."
        r.card("takahide-green-2b", ComeToAid(Reward.Coins(1)))
        r.card("takahide-green-3b", ComeToAid(Reward.Coins(2)))
        // Proven Warrior (radius 3) / Chosen Champion (radius 4): "Choose a card in the discard of a friendly
        // hero in radius. An enemy hero in radius discards a card of the same color, if able."
        r.card("takahide-blue-1", ProvenWarrior(upToTwo = false))
        r.card("takahide-blue-2a", ProvenWarrior(upToTwo = false))
        // The Right Hand: "… Up to two enemy heroes in radius discard a card of the same color, if able."
        r.card("takahide-blue-3a", ProvenWarrior(upToTwo = true))
        // Calculated Risk: "A friendly hero in radius may discard an attack card. If that hero has a card in
        // the discard, that hero may move up to 2 spaces." Tactical Gambit: "… up to 2 spaces, ignoring obstacles."
        r.card("takahide-blue-2b", CalculatedRisk(ignoreObstacles = false))
        r.card("takahide-blue-3b", CalculatedRisk(ignoreObstacles = true))
        // Ready for War (Ultimate): "Return your silver card to your deck and take two gold cards from your
        // deck into your hand. (You now have a total hand size of 6 cards.)"
        r.card("takahide-purple-4", ReadyForWar)
    }

    // ---- the gold cards ----------------------------------------------------------------------------

    /**
     * "Swap [cardId] with a different gold card in your deck" (p.10 "Swap a Card": all card states are
     * swapped). Mandatory: with no other gold in the deck (after Ready for War) the text stops here.
     * Returns the card that came in and the zone it took.
     */
    private fun ActionScope.swapWithDeckGold(cardId: String, prompt: String): Pair<String, Zone> {
        val h = hero
        val out = h.card(cardId) ?: fizzle("this card is not yours")
        val deckGolds = h.cards.filter {
            it.zone == Zone.DECK && it.id != cardId && g.card(it.id).color == CardColor.GOLD
        }.map { it.id }
        if (deckGolds.isEmpty()) fizzle("no other gold card in your deck")
        val incoming = if (deckGolds.size == 1) deckGolds.single() else chooseCard(prompt, deckGolds)!!
        val zone = out.zone
        g.swapStates(heroId, cardId, incoming)
        return incoming to zone
    }

    private object FloatLikeAButterfly : CardScript {
        override fun primary(a: ActionScope) {
            // The swap is the card text: it comes into effect before the movement (p.11), which the
            // engine performs after this (the action is still this card's, Movement 5).
            val (incoming, _) = a.swapWithDeckGold(a.cardId, "Swap this card with a gold card in your deck")
            a.g.emit(swapEvent(a.g, a.heroId, a.cardId, incoming, public = true))
        }
    }

    private class GoldAttack(private val atMaximumRange: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (atMaximumRange) {
                a.attackTarget("Target a unit at maximum range (${a.range})") { a.distanceTo(it) == a.range }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            val valid: ((String) -> Boolean)? = if (atMaximumRange) ({ id -> a.distanceTo(id) == a.range }) else null
            val res = a.attack(t, valid = valid)
            if (!res.connected) return
            // "After the attack: Swap this card with a different gold card in your deck."
            val (incoming, _) = a.swapWithDeckGold(a.cardId, "Swap this card with a gold card in your deck")
            a.g.emit(swapEvent(a.g, a.heroId, a.cardId, incoming, public = true))
        }
    }

    private object Bushido : CardScript {
        override fun primary(a: ActionScope) {
            // "your gold card": a gold card out of the deck. A facedown card has lost its colour (p.10), so a
            // gold placed facedown by an earlier Bushido is not a gold card until it is back in hand.
            val golds = a.hero.cards.filter {
                it.zone != Zone.DECK && it.zone != Zone.BOX && it.zone != Zone.PLAYED &&
                    a.g.cardColorOf(a.heroId, it.id) == CardColor.GOLD
            }.map { it.id }
            if (golds.isEmpty()) a.fizzle("no gold card to swap")
            val mine = if (golds.size == 1) golds.single() else a.chooseCard("Choose your gold card", golds)!!
            val (incoming, zone) = a.swapWithDeckGold(mine, "Swap it with a gold card in your deck")
            // "if you swap a resolved or discarded card this way, place the new card facedown."
            val facedown = zone == Zone.RESOLVED || zone == Zone.DISCARD
            if (facedown) a.g.turnFaceDown(a.heroId, incoming)
            a.g.emit(swapEvent(a.g, a.heroId, mine, incoming, public = zone != Zone.HAND, facedown = facedown))
        }
    }

    private object ShamefulDisplay : CardScript {
        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            d.block(false)
        }
    }

    private object ReadyForWar : CardScript, UltimateUnlock {
        override val hasPrimary: Boolean get() = false

        override fun unlocked(g: Game, heroId: String) {
            val h = g.hero(heroId)
            // "your silver card": Bushido, or the silver Handicap card that replaced it (p.20).
            h.cards.filter { it.zone != Zone.DECK && it.zone != Zone.BOX && g.card(it.id).color == CardColor.SILVER }
                .forEach { g.moveCard(heroId, it.id, Zone.DECK) }
            h.cards.filter { it.zone == Zone.DECK && g.card(it.id).color == CardColor.GOLD }
                .forEach { g.moveCard(heroId, it.id, Zone.HAND) }
            g.emit(
                GameEvent.CardsChanged(
                    heroId,
                    "${g.heroName(heroId)} returns the silver card to the deck and takes the gold cards into hand.",
                ),
            )
        }
    }

    // ---- red: attacks with an ally swap, or obstacles ---------------------------------------------

    private class SetAnExample(private val orDiscard: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            if (!a.attack(t).connected) return
            // "A friendly hero in radius may swap their unresolved card with a card in their hand (, or in
            // their discard)." You choose the hero; that hero chooses the card, or not to swap (p.10).
            fun cards(f: String): List<String> = a.g.hand(f) + if (orDiscard) a.g.discardPile(f) else emptyList()
            val friends = a.friendlyHeroesWithin(a.radius).filter { f ->
                a.g.unresolvedCard(f) != null && cards(f).isNotEmpty()
            }
            if (friends.isEmpty()) return
            val f = a.chooseUnit("A friendly hero in radius may swap their unresolved card", friends, optional = true)
                ?: return
            a.target(f)
            val unresolved = a.g.unresolvedCard(f)!!
            val where = if (orDiscard) "hand or discard" else "hand"
            val c = a.chooseCard("You may swap your unresolved card with a card in your $where", cards(f), true, f)
                ?: return
            a.g.swapStates(f, unresolved, c)
            // A card in play and in hand is faceup; the old unresolved card goes faceup into the discard.
            a.g.hero(f).card(unresolved)!!.faceDown = false
            a.g.hero(f).card(c)!!.faceDown = false
            a.g.emit(
                GameEvent.CardsChanged(
                    f,
                    "${a.g.heroName(f)} swaps ${a.g.cardName(unresolved)} for ${a.g.cardName(c)} (now unresolved).",
                    // Both end faceup: the old card in the discard, the new one unresolved (p.4).
                    cards = listOf(unresolved, c),
                    discarded = listOf(unresolved),
                ),
            )
        }
    }

    private class Blade(private val inRadius: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            if (!a.attack(t).connected) return
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun countsAsObstacle(g: Game, src: Src, q: SpaceQuery): Boolean {
                val owner = src.owner ?: return false
                val mover = q.moverId ?: return false
                if (!g.isUnit(mover) || !g.isEnemy(owner, mover)) return false
                val center = g.ownerPos(src) ?: return false
                val d = g.distance(center, q.hex)
                val reach = if (inRadius) g.radiusOf(src) else 1
                if (d !in 1..reach) return false
                // "Empty spaces": a space of the map with nothing on it (read without hooks: no recursion).
                if (!g.geo.isSpace(q.hex) || g.occupant(q.hex) != null) return false
                return g.affectedBy(src, mover)
            }
        }
    }

    // ---- green: an ally discards, then a reward ----------------------------------------------------

    private sealed interface Reward {
        data class Move(val spaces: Int, val ignoreObstacles: Boolean) : Reward

        data class Coins(val coins: Int) : Reward
    }

    private class ComeToAid(private val reward: Reward) : CardScript {
        override fun primary(a: ActionScope) {
            // "A friendly hero in range may discard a card." You choose the hero (mandatory); it may discard.
            val f = a.chooseFriend(a.range, "A friendly hero in range may discard a card")
            val hand = a.g.hand(f)
            if (hand.isNotEmpty()) {
                a.chooseCard("You may discard a card", hand, optional = true, chooser = f)?.let { a.g.discard(f, it) }
            }
            // "If that hero has a card in the discard" — any card, faceup or facedown (presence).
            if (a.g.discardPile(f).isEmpty()) return
            when (val r = reward) {
                is Reward.Move -> a.moveSelf(0, r.spaces, optional = true, ignoreObstacles = r.ignoreObstacles)

                is Reward.Coins -> {
                    a.g.gainCoins(a.heroId, r.coins, a.card.name)
                    a.g.gainCoins(f, r.coins, a.card.name)
                    a.mayRetrieve()
                }
            }
        }
    }

    // ---- blue: colour discards, ally moves -----------------------------------------------------------

    private class ProvenWarrior(private val upToTwo: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Choose a card in the discard of a friendly hero in radius": a faceup card (a facedown card has
            // no colour, p.10).
            val cards = a.friendlyHeroesWithin(a.radius).flatMap { f ->
                a.g.discardPile(f).filter { a.g.cardColorOf(f, it) != null }
            }
            if (cards.isEmpty()) a.fizzle("no card in the discard of a friendly hero in radius")
            val chosen = a.chooseCard("Choose a card in the discard of a friendly hero in radius", cards)!!
            val color = a.g.card(chosen).color
            val what = "a ${color.name.lowercase()} card"
            // Enemy heroes, not their figures: "up to two enemy heroes" never picks one hero twice (Razzle).
            val enemies = a.unitsWithin(a.radius).filter { a.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
                .mapNotNull { a.g.heroBodyOf(it) }.distinct()
            if (upToTwo) {
                // "Up to two enemy heroes in radius discard a card of the same color, if able."
                val victims = a.chooseUpTo(2, "An enemy hero in radius discards $what", enemies)
                for (v in victims) a.discardIfAble(v, what) { it.color == color }
            } else {
                if (enemies.isEmpty()) a.fizzle("no enemy hero in radius")
                val v = a.chooseUnit("An enemy hero in radius discards $what, if able", enemies)!!
                a.discardIfAble(v, what) { it.color == color }
            }
        }
    }

    private class CalculatedRisk(private val ignoreObstacles: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val f = a.chooseFriend(a.radius, "A friendly hero in radius may discard an attack card")
            val attacks = a.g.hand(f).filter { a.g.isAttackCard(f, it) }
            if (attacks.isNotEmpty()) {
                a.chooseCard("You may discard an attack card", attacks, optional = true, chooser = f)
                    ?.let { a.g.discard(f, it) }
            }
            if (a.g.discardPile(f).isEmpty()) return
            // "that hero may move up to 2 spaces": that hero chooses the move.
            a.moveOther(f, 0, 2, optional = true, ignoreObstacles = ignoreObstacles, chooser = f)
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** Friendly heroes (never you, p.17) 1..[n] away that this action may affect, as hero ids. */
    private fun ActionScope.friendlyHeroesWithin(n: Int): List<String> =
        unitsWithin(n).filter {
            isHero(it) && isFriendly(it) && canTarget(it)
        }.mapNotNull { g.heroBodyOf(it) }.distinct()

    /** "A friendly hero in range/radius …": you choose one; the step is mandatory. */
    private fun ActionScope.chooseFriend(n: Int, prompt: String): String {
        val friends = friendlyHeroesWithin(n)
        if (friends.isEmpty()) fizzle("no friendly hero in reach")
        return target(chooseUnit(prompt, friends)!!)
    }

    /**
     * p.10 "Swap a Card": "all their card states are swapped" (zone, Turn slot, played, facedown), and both
     * cards' Active effects end. Like [dev.quinton.goa.core.game.engine.swapCards] but without naming the
     * cards in the log: a card coming from the deck may stay hidden. A card that enters play after the
     * reveal counts as played (RULINGS 36).
     */
    private fun Game.swapStates(heroId: String, a: String, b: String) {
        val h = hero(heroId)
        val ca = h.card(a)!!
        val cb = h.card(b)!!
        cancelEffectsOf(heroId, a)
        cancelEffectsOf(heroId, b)
        val zone = ca.zone
        val slot = ca.slot
        val played = ca.played
        val faceDown = ca.faceDown
        ca.zone = cb.zone
        ca.slot = cb.slot
        ca.played = cb.played
        ca.faceDown = cb.faceDown
        cb.zone = zone
        cb.slot = slot
        cb.played = played
        cb.faceDown = faceDown
        ca.stamp = ++state.stamp
        cb.stamp = ++state.stamp
        touch()
        for (c in listOf(ca, cb)) {
            if (c.zone == Zone.PLAYED && state.revealed && isAttackCard(heroId, c.id)) {
                state.turnRecord.playedAttack += heroId
            }
        }
    }

    private fun swapEvent(
        g: Game,
        heroId: String,
        out: String,
        incoming: String,
        public: Boolean,
        facedown: Boolean = false,
    ): GameEvent {
        val who = g.heroName(heroId)
        val text = when {
            !public -> "$who swaps the gold card in hand with a gold card from the deck."
            facedown -> "$who swaps ${g.cardName(out)} with a gold card from the deck, placed facedown."
            else -> "$who swaps ${g.cardName(out)} with ${g.cardName(incoming)} from the deck."
        }
        val named = when {
            !public -> emptyList()
            facedown -> listOf(out)
            else -> listOf(out, incoming)
        }
        return GameEvent.CardsChanged(heroId, text, cards = named)
    }
}
