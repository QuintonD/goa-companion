package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.AttackResult
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.defeatHero
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.hand
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.lowestTier
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Rowenna the Vanguard. Card text quoted verbatim above each registration
 * (`catalog/heroes/rowenna.json`); `RowennaTest` follows it clause by clause with the FAQ. Readings
 * the text leaves open are in `core/game/rulings/rowenna.md`.
 */
object RowennaHero : HeroModule {
    override val heroId: String = "rowenna"
    override val complete: Boolean = true

    private const val LANCE = "rowenna-purple-4"

    override fun register(r: Registry) {
        // Code of Chivalry: "Target a unit adjacent to you. Before the attack: If you target a hero, both
        // you and the target may retrieve a discarded card."
        r.card("rowenna-gold", Chivalry(heroOnly = false))
        // Earn the Spurs (Handicap): "Target a hero adjacent to you. Before the attack: Both you and the
        // target may retrieve a discarded card."
        r.card("rowenna-handicap", Chivalry(heroOnly = true))
        // Throw the Gauntlet: "Place yourself into a space in range adjacent to an enemy hero in range;
        // that hero may move 1 space; if they do, gain 2 coins."
        r.card("rowenna-silver", Gauntlet)
        // Token of Gratitude / Fair Share: "Target a unit adjacent to you. After the attack: A friendly
        // hero in radius gains 1 coin." Paragon of Grace: "… May repeat once on a different target."
        r.card("rowenna-red-1", Gratitude(repeat = false))
        r.card("rowenna-red-2a", Gratitude(repeat = false))
        r.card("rowenna-red-3a", Gratitude(repeat = true))
        // Feat of Bravery: "Target a unit adjacent to you. After the attack: A friendly hero in radius may
        // retrieve a discarded card." Paragon of Valor: "…; if they do, you may repeat once on a different
        // target."
        r.card("rowenna-red-2b", Bravery(repeat = false))
        r.card("rowenna-red-3b", Bravery(repeat = true))
        // Close Quarters / Melee: "After movement, if you are adjacent to an enemy hero, you may Choose one
        // — • Place a friendly minion in radius into a space adjacent to that enemy hero. • Place an enemy
        // minion in radius into a space adjacent to you." Grand Melee: "… Choose up to two times — …"
        r.card("rowenna-green-1", Melee(times = 1))
        r.card("rowenna-green-2a", Melee(times = 1))
        r.card("rowenna-green-3a", Melee(times = 2))
        // Stand Guard / Protect the Weak: "Swap with a friendly unit in range which is adjacent to an enemy
        // hero, or who has a card in the discard." Defend the Innocent: "… You may retrieve a discarded card."
        r.card("rowenna-blue-1", Guard(retrieve = false))
        r.card("rowenna-blue-2a", Guard(retrieve = false))
        r.card("rowenna-blue-3a", Guard(retrieve = true))
        // Opening Shots: "If both you and an enemy hero in radius have no cards in the discard, that hero
        // discards a card, if able." Opening Volley: "… May repeat once."
        r.card("rowenna-green-2b", Opening(repeat = false))
        r.card("rowenna-green-3b", Opening(repeat = true))
        // Accept Surrender: "Defeat an enemy hero adjacent to you with no cards in hand." Glorious Triumph:
        // "…; your friendly heroes gain triple assist coins."
        r.card("rowenna-blue-2b", Surrender(triple = false))
        r.card("rowenna-blue-3b", Surrender(triple = true))
        // Fabled Lance (Ultimate): "All your attack actions gain the "Ranged" subtype, target a unit in
        // range, and count as having a printed Range value of 2."
        r.card(LANCE, FabledLance)
    }

    // ---- attacks, with Fabled Lance ----------------------------------------------------------

    /** Fabled Lance is unlocked for the hero performing (always in effect on the board, p.16). */
    private fun ActionScope.lance(): Boolean = hero.cards.any { it.id == LANCE && it.zone == Zone.ULTIMATE }

    /**
     * "Target a unit (hero) adjacent to you", or with Fabled Lance "target a unit in range" (printed
     * Range 2, items count). [APP] Earn the Spurs keeps "a hero" (rulings/rowenna.md 6).
     */
    private fun ActionScope.rowennaTarget(heroOnly: Boolean): String? {
        val extra: (String) -> Boolean = { !heroOnly || g.isHero(it) }
        val what = if (heroOnly) "a hero" else "a unit"
        return if (lance()) {
            rangedAttackTarget("Target $what in range", rangeValue = range, extra = extra)
        } else {
            adjacentAttackTarget("Target $what adjacent to you", extra = extra)
        }
    }

    private fun ActionScope.rowennaAttack(t: String): AttackResult =
        if (lance()) attack(t, ranged = true, valid = { inRange(it) }) else attack(t)

    private class Chivalry(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rowennaTarget(heroOnly) ?: return
            // "Before the attack: If you target a hero, both you and the target may retrieve a discarded
            // card." Rowenna first, then the target ([APP] rulings/rowenna.md 1); the target chooses its card.
            val targetHero = a.g.heroBodyOf(t)
            if (targetHero != null) {
                a.mayRetrieve()
                a.mayRetrieve(who = targetHero)
            }
            a.rowennaAttack(t)
        }
    }

    private object Gauntlet : CardScript {
        override fun primary(a: ActionScope) {
            val me = a.pos
            fun spacesNext(e: String): List<Hex> = HexGrid.neighbors(a.g.posOf(e)!!).filter {
                a.g.distance(me, it) in 1..a.range && a.g.isEmpty(it)
            }
            val enemies = a.unitsWithin(a.range).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) && spacesNext(id).isNotEmpty()
            }
            if (enemies.isEmpty() ||
                !a.g.canPlace(a.heroId, a.heroId)
            ) {
                a.fizzle("no space next to an enemy hero in range")
            }
            val e = a.target(a.chooseUnit("Choose an enemy hero in range", enemies)!!)
            val to = a.chooseSpace("Place yourself into a space adjacent to ${a.g.name(e)}", spacesNext(e))!!
            a.g.place(a.heroId, to, a.heroId)
            // "that hero may move 1 space; if they do, gain 2 coins." [APP] The hero moves itself (it
            // decides), so it is the mover and the actor of that move (rulings/rowenna.md 2).
            val who = a.g.heroBodyOf(e)!!
            val path = a.g.asActor(who) {
                a.g.move(MoveSpec(e, 1, 1), who, "You may move 1 space", optional = true, sourceCardId = a.cardId)
            }
            if (path.isNotEmpty()) a.g.gainCoins(a.heroId, 2, a.card.name)
        }
    }

    /** "A friendly hero in radius" (FAQ: never you), targetable. */
    private fun ActionScope.friendsInRadius(): List<String> =
        unitsWithin(radius).filter { g.isHero(it) && isFriendly(it) && canTarget(it) }

    private class Gratitude(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rowennaTarget(heroOnly = false) ?: return
            val r = a.rowennaAttack(t)
            if (!r.connected) return
            // "After the attack: A friendly hero in radius gains 1 coin." (mandatory: p.10)
            val friends = a.friendsInRadius()
            if (friends.isEmpty()) a.fizzle("no friendly hero in radius")
            val f = a.target(a.chooseUnit("A friendly hero in radius gains 1 coin", friends)!!)
            a.g.gainCoins(a.g.heroBodyOf(f)!!, 1, a.card.name)
            // Paragon of Grace: "May repeat once on a different target."
            if (repeat) a.repeat(differentTarget = true, prompt = "Repeat once on a different target?")
        }
    }

    private class Bravery(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rowennaTarget(heroOnly = false) ?: return
            val r = a.rowennaAttack(t)
            if (!r.connected) return
            // "After the attack: A friendly hero in radius may retrieve a discarded card" (optional; only a
            // friend with a discarded card can retrieve).
            val friends = a.friendsInRadius().filter { a.g.discardPile(a.g.heroBodyOf(it)!!).isNotEmpty() }
            if (friends.isEmpty()) return
            val f = a.target(a.chooseUnit("A friendly hero in radius may retrieve a discarded card", friends)!!)
            val retrieved = a.mayRetrieve(who = a.g.heroBodyOf(f)!!)
            // Paragon of Valor: "if they do, you may repeat once on a different target."
            if (repeat && retrieved != null) {
                a.repeat(differentTarget = true, prompt = "They retrieved a card: repeat once on a different target?")
            }
        }
    }

    private class Melee(private val times: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "After movement, if you are adjacent to an enemy hero" (presence: immune heroes count, p.18).
            a.movementAction()
            if (a.hero.at == null) return
            if (a.unitsWithin(1).none { a.g.isHero(it) && a.isEnemy(it) }) return
            // "you may Choose one —" / "Choose up to two times —": the same bullet may be chosen twice.
            val limit = a.choiceLimit(times, upTo = times > 1)
            val labels = listOf(
                "Place a friendly minion in radius into a space adjacent to that enemy hero",
                "Place an enemy minion in radius into a space adjacent to you",
            )
            repeat(limit) { i ->
                val heroes = enemyHeroesWithRoom(a)
                val friendly = minionsInRadius(a, friendly = true)
                val enemy = minionsInRadius(a, friendly = false)
                val roomNextToMe = HexGrid.neighbors(a.pos).any { a.g.isEmpty(it) }
                val possible = listOfNotNull(
                    0.takeIf { heroes.isNotEmpty() && friendly.isNotEmpty() },
                    1.takeIf { roomNextToMe && enemy.isNotEmpty() },
                )
                if (possible.isEmpty()) return
                val prompt = if (limit == 1) "You may choose one" else "Choose up to $limit times (${i + 1} of $limit)"
                val pick = a.chooseOption(prompt, possible.map { labels[it] }, optional = true) ?: return
                if (possible[pick] == 0) {
                    val h = a.chooseUnit("Next to which enemy hero?", heroes)!!
                    val m = a.target(a.chooseUnit("Place a friendly minion in radius", friendly)!!)
                    val to = a.chooseSpace(
                        "Place it into a space adjacent to ${a.g.name(h)}",
                        HexGrid.neighbors(a.g.posOf(h)!!).filter { a.g.isEmpty(it) },
                    )!!
                    a.g.place(m, to, a.heroId)
                } else {
                    val m = a.target(a.chooseUnit("Place an enemy minion in radius", enemy)!!)
                    val to = a.chooseSpace(
                        "Place it into a space adjacent to you",
                        HexGrid.neighbors(a.pos).filter { a.g.isEmpty(it) },
                    )!!
                    a.g.place(m, to, a.heroId)
                }
            }
        }

        /** Enemy heroes adjacent to Rowenna with an empty space next to them. */
        private fun enemyHeroesWithRoom(a: ActionScope): List<String> = a.unitsWithin(1).filter { id ->
            a.g.isHero(id) && a.isEnemy(id) && HexGrid.neighbors(a.g.posOf(id)!!).any { a.g.isEmpty(it) }
        }

        private fun minionsInRadius(a: ActionScope, friendly: Boolean): List<String> = a.unitsWithin(
            a.radius,
        ).filter { id ->
            a.g.isMinion(id) && (if (friendly) a.isFriendly(id) else a.isEnemy(id)) && a.canTarget(id) &&
                a.g.canPlace(id, a.heroId)
        }
    }

    private class Guard(private val retrieve: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "a friendly unit in range which is adjacent to an enemy hero, or who has a card in the discard"
            val friends = a.unitsWithin(a.range).filter { id ->
                a.isFriendly(id) && a.canTarget(id) && a.g.canSwap(a.heroId, id, a.heroId) &&
                    (nextToEnemyHero(a, id) || a.g.heroBodyOf(id)?.let { a.g.discardPile(it).isNotEmpty() } == true)
            }
            if (friends.isEmpty()) a.fizzle("no friendly unit to swap with")
            val f = a.target(a.chooseUnit("Swap with a friendly unit in range", friends)!!)
            a.g.swap(a.heroId, f, a.heroId)
            // Defend the Innocent: "You may retrieve a discarded card."
            if (retrieve) a.mayRetrieve()
        }

        private fun nextToEnemyHero(a: ActionScope, id: String): Boolean =
            a.g.unitsAdjacentTo(a.g.posOf(id)!!).any { a.g.isHero(it) && a.isEnemy(it) }
    }

    private class Opening(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val victims = victims(a)
            if (victims.isEmpty()) a.fizzle("the condition is not met")
            val v = a.chooseUnit("An enemy hero in radius with no cards in the discard discards a card", victims)!!
            a.discardIfAble(v)
            // Opening Volley: "May repeat once." (asked only when the condition holds again)
            if (repeat && !a.isRepeat && victims(a).isNotEmpty()) a.repeat()
        }

        /** "If both you and an enemy hero in radius have no cards in the discard" (a status: targeted). */
        private fun victims(a: ActionScope): List<String> {
            if (a.g.discardPile(a.heroId).isNotEmpty()) return emptyList()
            return a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) && a.g.discardPile(a.g.heroBodyOf(id)!!).isEmpty()
            }
        }
    }

    private class Surrender(private val triple: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val victims = a.unitsWithin(1).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) && a.g.hand(a.g.heroBodyOf(id)!!).isEmpty()
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero adjacent with no cards in hand")
            val v = a.target(a.chooseUnit("Defeat an enemy hero adjacent to you with no cards in hand", victims)!!)
            val hero = a.g.heroBodyOf(v)!!
            // p.13: assist coins equal the defeated hero's lowest card Tier; Glorious Triumph triples them.
            val tier = a.g.lowestTier(hero)
            a.g.defeatHero(hero, a.heroId)
            if (!triple) return
            for (f in a.g.state.heroes) {
                if (f.team == a.hero.team && f.id != a.heroId) a.g.gainCoins(f.id, 2 * tier, "triple assist")
            }
        }
    }

    private object FabledLance : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            /** "count as having a printed Range value of 2" — Range items then count (p.16). */
            override fun printedValue(g: Game, src: Src, q: StatQuery): Int? {
                if (q.unitId != src.owner || q.stat != Stat.RANGE) return null
                val card = q.cardId?.let { g.data.cardOrNull(it) } ?: return null
                return if (card.primary == PrimaryAction.ATTACK) 2 else null
            }
        }
    }
}
