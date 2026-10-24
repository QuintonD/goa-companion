package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseUpTo
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.objectsWithin
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.activeCards
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.performPrimary
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.removeMinion
import dev.quinton.goa.core.game.engine.resolvedCards
import dev.quinton.goa.core.game.engine.spendLife
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.heroes.batch2.chooseBullets
import dev.quinton.goa.core.model.board.Hex

/**
 * Ursafar the Savage. Each card's verbatim text (`catalog/heroes/ursafar.json`) is quoted above its
 * registration; `UrsafarTest` follows it clause by clause with the hero's FAQ. Readings:
 * `core/game/rulings/ursafar.md`.
 *
 * "This round: You are enraged" is an Active effect of the card ([ENRAGED], This round): Ursafar is
 * enraged while one is in force (it ends at the end of the round, when he is defeated, or when that card
 * changes state, p.17), or always with Unbound Fury. Each card's own "This round" clause comes last, so a
 * card never satisfies its own "If enraged".
 */
object UrsafarHero : HeroModule {
    override val heroId: String = "ursafar"
    override val complete: Boolean = true

    /** Effect key of "This round: You are enraged". */
    const val ENRAGED: String = "ursafar-enraged"
    private const val UNBOUND_FURY = "ursafar-purple-4"

    override fun register(r: Registry) {
        // Claws That Catch: "Before the attack: If enraged, you may move 1 space to a space adjacent to an
        // enemy hero. Target a unit adjacent to you. This round: You are enraged."
        r.card("ursafar-gold", Claws(heroOnly = false))
        // Jaws That Bite (Handicap): "… Target a hero adjacent to you. This round: You are enraged."
        r.card("ursafar-handicap", Claws(heroOnly = true))
        // Angry Roar: "If enraged, perform the primary action on one of your active cards with an active
        // effect. This round: You are enraged."
        r.card("ursafar-silver", AngryRoar)
        // Prey Drive / Prey Abundance: "Target a unit adjacent to you. After the attack: If enraged, and the
        // target was not removed, remove up to 1 enemy minion in radius. This round: You are enraged."
        // Feeding Frenzy: "… remove up to 2 enemy minions in radius. …"
        r.card("ursafar-red-1", Prey(removals = 1))
        r.card("ursafar-red-2a", Prey(removals = 1))
        r.card("ursafar-red-3a", Prey(removals = 2))
        // Rip: "Target a unit adjacent to you. After the attack: If enraged, gain 1 coin. This round: You are
        // enraged." Tear: "… If enraged, gain 2 coins; if you defeated a hero, that hero spends 1 additional
        // Life counter. This round: You are enraged."
        r.card("ursafar-red-2b", Rend(coins = 1, extraLife = false))
        r.card("ursafar-red-3b", Rend(coins = 2, extraLife = true))
        // Sniff Out / Eyes on the Prey: "If enraged, an enemy hero in range discards a card, if able."
        // Apex Predator: "If enraged, an enemy hero in range discards a card, or is defeated."
        r.card("ursafar-green-1", Sniff(orDefeated = false))
        r.card("ursafar-green-2a", Sniff(orDefeated = false))
        r.card("ursafar-green-3a", Sniff(orDefeated = true))
        // Instinctive Reaction: "If enraged, choose one — • Perform the primary action on one of your
        // discarded cards. • You may retrieve a discarded card." Evolutionary Response: "If enraged, choose
        // one, or both — …" (same bullets)
        r.card("ursafar-green-2b", Instinct(both = false))
        r.card("ursafar-green-3b", Instinct(both = true))
        // Prowling Brute: "If enraged, after movement, you may swap with a unit or a token adjacent to you.
        // This round: You are enraged." Rampaging Beast / Unstoppable Force: "…; if you do, move up to 1 (2)
        // additional space(s). This round: You are enraged."
        r.card("ursafar-blue-1", Prowl(extra = 0))
        r.card("ursafar-blue-2a", Prowl(extra = 1))
        r.card("ursafar-blue-3a", Prowl(extra = 2))
        // Cold Ire / Eyes of Flame: "If enraged, gain +1 (+2) Movement. This round: You are enraged."
        r.card("ursafar-blue-2b", Ire(bonus = 1))
        r.card("ursafar-blue-3b", Ire(bonus = 2))
        // Unbound Fury (Ultimate): "You are always enraged, and all your resolved cards count as active."
        r.card(UNBOUND_FURY, UnboundFury)
    }

    // ---- enraged -------------------------------------------------------------------------------

    private fun Game.furyUnlocked(heroId: String): Boolean {
        val h = heroOrNull(heroId) ?: return false
        return h.at != null && h.card(UNBOUND_FURY)?.zone == Zone.ULTIMATE
    }

    /** Is [heroId] enraged now (a "You are enraged" effect in force, or Unbound Fury)? */
    internal fun isEnraged(g: Game, heroId: String): Boolean =
        g.furyUnlocked(heroId) || g.state.effects.any { it.owner == heroId && it.key == ENRAGED && g.inForce(it) }

    private fun ActionScope.enraged(): Boolean = isEnraged(g, heroId)

    /** "This round: You are enraged." */
    private fun ActionScope.becomeEnraged() {
        if (hero.at != null) activeEffect(Timing.THIS_ROUND, key = ENRAGED)
    }

    /** p.17: an Active effect begins with a bold keyword ("This round:", "Next turn:", …). */
    private val ACTIVE_KEYWORD =
        Regex("""\*\*(This turn|This round|Next turn|End of turn|End of round)\b""", RegexOption.IGNORE_CASE)

    private fun hasActiveEffectText(text: String?): Boolean = text != null && ACTIVE_KEYWORD.containsMatchIn(text)

    /** Cards whose Primary action Ursafar may perform now (not one being performed, faceup, possible). */
    private fun ActionScope.performable(ids: List<String>): List<String> {
        val busy = g.actions.map { it.cardId }.toSet() + cardId
        return ids.filter { id ->
            val c = hero.card(id) ?: return@filter false
            val kind = primaryKind(g.card(id))
            !c.faceDown && id !in busy && kind != ActionKind.DEFENSE && kind != ActionKind.NONE &&
                g.data.registry.script(id)?.hasPrimary == true && g.actionAllowed(heroId, id, kind, true) &&
                g.primaryPossible(heroId, id)
        }
    }

    // ---- cards ---------------------------------------------------------------------------------

    private class Claws(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Before the attack: If enraged, you may move 1 space to a space adjacent to an enemy hero."
            if (a.enraged()) {
                val nextToEnemyHero: (Hex) -> Boolean = { h ->
                    a.g.unitsAdjacentTo(h).any { u -> a.g.isHero(u) && a.g.heroBodyOf(u) != a.heroId && a.isEnemy(u) }
                }
                if (a.g.canMove(MoveSpec(a.heroId, 1, 1, endFilter = nextToEnemyHero))) {
                    a.moveSelf(1, optional = true, endFilter = nextToEnemyHero)
                }
            }
            // "Target a unit (a hero) adjacent to you."
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            a.attack(t)
            a.becomeEnraged()
        }
    }

    private object AngryRoar : CardScript {
        override fun primary(a: ActionScope) {
            // "If enraged, perform the primary action on one of your active cards with an active effect."
            // Unbound Fury: "all your resolved cards count as active" — but a card must still have an Active
            // effect (designer, BGG 3458850: "the card has to be both active and have an active effect, which
            // green doesn't have"; the Ultimate allows "resolved cards with active effects … which you have
            // resolved, but didn't activate").
            if (a.enraged()) {
                val active =
                    a.g.activeCards(a.heroId) +
                        if (a.g.furyUnlocked(a.heroId)) {
                            a.g.resolvedCards(a.heroId).filter { hasActiveEffectText(a.g.card(it).text) }
                        } else {
                            emptyList()
                        }
                val cards = a.performable(active.distinct())
                if (cards.isEmpty()) a.fizzle("no active card whose primary action can be performed")
                val c = a.chooseCard("Perform the primary action on one of your active cards", cards)!!
                a.g.performPrimary(a.heroId, c)
            }
            a.becomeEnraged()
        }
    }

    private class Prey(private val removals: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            // "After the attack: If enraged, and the target was not removed, remove up to N enemy minions in
            // radius." Remove gives no coins (FAQ); the minions are picked together (Whisper FAQ on heavies).
            if (r.connected && a.enraged() && a.g.posOf(t) != null) {
                val minions = a.unitsWithin(a.radius).filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
                val what = if (removals == 1) "an enemy minion" else "enemy minions"
                for (m in a.chooseUpTo(removals, "Remove $what in radius", minions)) {
                    a.target(m)
                    a.g.removeMinion(m)
                }
            }
            a.becomeEnraged()
        }
    }

    private class Rend(private val coins: Int, private val extraLife: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val victim = a.g.heroBodyOf(t)
            val r = a.attack(t)
            // "After the attack: If enraged, gain N coin(s)"; Tear: "if you defeated a hero, that hero spends 1
            // additional Life counter" (part of the "If enraged" sentence).
            if (r.connected && a.enraged()) {
                a.g.gainCoins(a.heroId, coins, a.card.name)
                if (extraLife && r.hero && r.defeated && victim != null) {
                    a.g.spendLife(a.g.hero(victim).team, 1, "${a.card.name}: ${a.g.heroName(victim)}")
                }
            }
            a.becomeEnraged()
        }
    }

    private class Sniff(private val orDefeated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            if (!a.enraged()) return
            // "an enemy hero in range discards a card, if able (or is defeated)" — Ursafar picks the hero; the
            // hero must be found, "if able" covers only the discard (RULINGS 53, rulings/ursafar.md 7).
            val victims = a.unitsWithin(a.range).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            if (victims.isEmpty()) a.fizzle("no enemy hero in range")
            val v = a.chooseUnit("An enemy hero in range discards a card", victims)!!
            if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
        }
    }

    private class Instinct(private val both: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            if (!a.enraged()) return
            val labels =
                listOf("Perform the primary action on one of your discarded cards", "You may retrieve a discarded card")
            val order = a.chooseBullets(
                labels,
                listOf(a.performable(a.g.discardPile(a.heroId)).isNotEmpty(), a.g.discardPile(a.heroId).isNotEmpty()),
                both,
            )
            for (i in order) {
                if (a.hero.at == null) return
                if (i == 0) {
                    // "Perform the primary action on one of your discarded cards." (It stays discarded.)
                    val cards = a.performable(a.g.discardPile(a.heroId))
                    if (cards.isEmpty()) a.fizzle("no discarded card whose primary action can be performed")
                    val c = a.chooseCard(labels[0], cards)!!
                    a.g.performPrimary(a.heroId, c)
                } else {
                    a.mayRetrieve()
                }
            }
        }
    }

    private class Prowl(private val extra: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val enraged = a.enraged()
            a.movementAction()
            // "If enraged, after movement, you may swap with a unit or a token adjacent to you; if you do, move
            // up to N additional spaces." (Card-text movement.)
            if (enraged && a.hero.at != null) {
                val targets = a.objectsWithin(1).filter { a.canTarget(it) && a.g.canSwap(a.heroId, it, a.heroId) }
                val t = a.chooseObject("You may swap with a unit or a token adjacent to you", targets, optional = true)
                if (t != null) {
                    a.target(t)
                    a.g.swap(a.heroId, t, a.heroId)
                    if (extra > 0) a.moveSelf(0, extra)
                }
            }
            a.becomeEnraged()
        }
    }

    private class Ire(private val bonus: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "If enraged, gain +N Movement." — this card's Movement action only (FAQ).
            a.movementAction(bonus = if (a.enraged()) bonus else 0)
            a.becomeEnraged()
        }
    }

    /** Read by [isEnraged] and Angry Roar; no hooks of its own. */
    private object UnboundFury : CardScript {
        override val hasPrimary: Boolean get() = false
    }
}
