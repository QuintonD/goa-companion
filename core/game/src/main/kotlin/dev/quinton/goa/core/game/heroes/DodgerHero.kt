package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.battleZone
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isInBattleZone
import dev.quinton.goa.core.game.engine.isMinionSpawnPoint
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.respawnMinion
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Dodger the Warlock. Card text quoted verbatim above each registration (`catalog/heroes/dodger.json`);
 * `DodgerTest` follows it clause by clause with the FAQ. Readings the text leaves open are in
 * `core/game/rulings/dodger.md`.
 */
object DodgerHero : HeroModule {
    override val heroId: String = "dodger"
    override val complete: Boolean = true

    private const val ULTIMATE = "dodger-purple-4"

    override fun register(r: Registry) {
        // Dread Razor: "Choose one — • Target a unit adjacent to you. • If you are adjacent to an empty spawn
        // point in the battle zone, target a unit in range."
        r.card("dodger-gold", Razor(heroOnly = false))
        // Rusty Razor (Handicap): "Choose one — • Target a hero adjacent to you. • If you are adjacent to an
        // empty spawn point in the battle zone, target a hero in range."
        r.card("dodger-handicap", Razor(heroOnly = true))
        // Death Trap: "An enemy hero in radius who is adjacent to an empty spawn point in the battle zone
        // discards a card, if able."
        r.card("dodger-silver", DeathTrap)
        // Littlefinger of Death / Finger of Death: "Choose one — • Target a unit adjacent to you. • Target a
        // hero in range who has one or more cards in the discard."
        r.card("dodger-red-1", Finger(both = false))
        r.card("dodger-red-2a", Finger(both = false))
        // Middlefinger of Death: "Choose one, or both, on different targets — • Target a unit adjacent to you.
        // • Target a hero in range who has one or more cards in the discard."
        r.card("dodger-red-3a", Finger(both = true))
        // Burning Skull: "Target a unit in range. After the attack: Move up to 1 minion adjacent to you 1
        // space, to a space not adjacent to you." Blazing Skull: "… Move up to 2 minions adjacent to you 1
        // space each, to spaces not adjacent to you."
        r.card("dodger-red-2b", Skull(minions = 1))
        r.card("dodger-red-3b", Skull(minions = 2))
        // Dark Ritual: "If there are 2 or more empty spawn points in radius in the battle zone, gain 1 coin."
        // Darker Ritual: "… gain 2 coins." Darkest Ritual: "… gain 2 coins. If you have your Ultimate, gain an
        // Attack item. (Use any spare card from the box with the corresponding icon.)"
        r.card("dodger-green-1", Ritual(coins = 1, item = false))
        r.card("dodger-green-2a", Ritual(coins = 2, item = false))
        r.card("dodger-green-3a", Ritual(coins = 2, item = true))
        // Necromancy: "Respawn a friendly minion in an empty friendly spawn point adjacent to you in the
        // battle zone." Necromastery: "… in an empty friendly spawn point in radius in the battle zone."
        r.card("dodger-green-2b", Necromancy(inRadius = false))
        r.card("dodger-green-3b", Necromancy(inRadius = true))
        // Shield of Decay / Vampiric Shield: "+2 Defense if there are 2 or more empty spawn points in radius
        // in the battle zone." Aegis of Doom: "+4 Defense …"
        r.card("dodger-blue-1", Shield(bonus = 2))
        r.card("dodger-blue-2a", Shield(bonus = 2))
        r.card("dodger-blue-3a", Shield(bonus = 4))
        // Weakness: "This turn: Enemy heroes in radius have -4 Attack. (They can still attack, even with a
        // negative attack value.)" Enfeeblement: "This turn: Enemy heroes in radius have -6 Attack and
        // cannot repeat actions."
        r.card("dodger-blue-2b", Weakness(minus = 4, noRepeat = false))
        r.card("dodger-blue-3b", Weakness(minus = 6, noRepeat = true))
        // Tide of Darkness (Ultimate): "While you are performing an action, all spaces count as if they were
        // in the battle zone and had a friendly minion spawn point." Read by the helpers below, which run
        // only inside Dodger's own actions (her scripts and her Defense text).
        r.card("dodger-purple-4", TideOfDarkness)
    }

    /** Tide of Darkness is unlocked for [heroId] (only Dodger has it; another hero running her card does not). */
    private fun Game.tide(heroId: String): Boolean = hero(heroId).card(ULTIMATE)?.zone == Zone.ULTIMATE

    /**
     * FAQ "What is an empty minion spawn point in the battle zone?": a minion spawn point printed on the
     * board, in a current Battle Zone, with nothing on it. With Tide of Darkness every empty space is one.
     */
    private fun Game.emptySpawnPoint(heroId: String, hex: Hex): Boolean =
        geo.isSpace(hex) && isEmpty(hex) && (tide(heroId) || (isMinionSpawnPoint(hex) && isInBattleZone(hex)))

    private fun Game.emptySpawnPointsWithin(heroId: String, center: Hex, radius: Int): Int =
        within(center, radius).count { emptySpawnPoint(heroId, it) }

    private fun ActionScope.nextToEmptySpawnPoint(at: Hex = pos): Boolean =
        HexGrid.neighbors(at).any { g.emptySpawnPoint(heroId, it) }

    /** Dread Razor / Rusty Razor. */
    private class Razor(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val kind: (String) -> Boolean = { id -> !heroOnly || a.g.isHero(id) }
            val enemy: (String) -> Boolean = { id -> a.isEnemy(id) && a.canTarget(id) && kind(id) }
            val adjacent = a.unitsWithin(1).any(enemy)
            val ranged = a.nextToEmptySpawnPoint() && a.unitsWithin(a.range).any(enemy)
            val what = if (heroOnly) "hero" else "unit"
            val pick = when {
                adjacent && ranged -> a.chooseOption(
                    "Choose one",
                    listOf(
                        "Target a $what adjacent to you",
                        "You are adjacent to an empty spawn point: target a $what in range",
                    ),
                )

                adjacent -> 0

                ranged -> 1

                else -> a.fizzle("no target")
            }
            if (pick == 0) {
                val t = a.adjacentAttackTarget("Target a $what adjacent to you", extra = kind) ?: return
                a.attack(t, valid = { a.isAdjacent(it) })
            } else {
                // "If you are adjacent to an empty spawn point in the battle zone, target a unit in range."
                if (!a.nextToEmptySpawnPoint()) a.fizzle("not adjacent to an empty spawn point")
                val t = a.rangedAttackTarget("Target a $what in range", extra = kind) ?: return
                a.attack(t)
            }
        }
    }

    private object DeathTrap : CardScript {
        override fun primary(a: ActionScope) {
            val victims = a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.canTarget(id) && a.nextToEmptySpawnPoint(a.g.posOf(id)!!)
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero in radius next to an empty spawn point")
            val v = a.chooseUnit("An enemy hero next to an empty spawn point discards a card", victims)!!
            a.discardIfAble(v)
        }
    }

    /** Littlefinger / Finger / Middlefinger of Death. */
    private class Finger(private val both: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val adjacent = targets(a, 0, exclude = null)
            val ranged = targets(a, 1, exclude = null)
            val plans = ArrayList<Pair<String, List<Int>>>()
            if (adjacent.isNotEmpty()) plans += LABELS[0] to listOf(0)
            if (ranged.isNotEmpty()) plans += LABELS[1] to listOf(1)
            // "Choose one, or both, on different targets" in any order (p.10 "Modal Actions").
            val distinctPair = adjacent.isNotEmpty() && ranged.isNotEmpty() &&
                !(adjacent.size == 1 && ranged.size == 1 && adjacent == ranged)
            if (both && distinctPair) {
                plans += "Both: first the unit adjacent to you, then the hero in range" to listOf(0, 1)
                plans += "Both: first the hero in range, then the unit adjacent to you" to listOf(1, 0)
            }
            if (plans.isEmpty()) a.fizzle("no target")
            val plan = if (plans.size == 1) {
                plans.single().second
            } else {
                plans[
                    a.chooseOption(
                        if (both) "Choose one, or both" else "Choose one",
                        plans.map {
                            it.first
                        },
                    )!!,
                ].second
            }
            var first: String? = null
            for (bullet in plan) {
                val t = bulletAttack(a, bullet, exclude = first) ?: return
                if (first == null) first = t
            }
        }

        private fun valid(a: ActionScope, bullet: Int, exclude: String?): (String) -> Boolean = { id ->
            id != exclude && if (bullet == 0) {
                a.isAdjacent(id)
            } else {
                a.g.isHero(id) && a.inRange(id) && a.g.discardPile(a.g.heroBodyOf(id)!!).isNotEmpty()
            }
        }

        private fun targets(a: ActionScope, bullet: Int, exclude: String?): Set<String> =
            a.unitsWithin(maxOf(1, a.range)).filter {
                a.isEnemy(it) && a.canTarget(it) && valid(a, bullet, exclude)(it)
            }
                .toSet()

        private fun bulletAttack(a: ActionScope, bullet: Int, exclude: String?): String? {
            val ok = valid(a, bullet, exclude)
            val prompt = if (bullet == 0) LABELS[0] else LABELS[1]
            val t = if (bullet == 0) {
                a.adjacentAttackTarget(prompt, extra = ok)
            } else {
                a.rangedAttackTarget(prompt, extra = ok)
            } ?: return null
            a.attack(t, valid = ok)
            return t
        }

        private companion object {
            val LABELS = listOf(
                "Target a unit adjacent to you",
                "Target a hero in range who has one or more cards in the discard",
            )
        }
    }

    private class Skull(private val minions: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.rangedAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "Move up to 1 (2) minion(s) adjacent to you 1 space (each), to a space not adjacent to you."
            repeat(minions) { i ->
                val me = a.pos
                val away: (Hex) -> Boolean = { h -> a.g.distance(me, h) > 1 }
                val movable = a.unitsWithin(1).filter { id ->
                    a.g.isMinion(id) && a.canTarget(id) && a.canMoveOther(id, 1, endFilter = away)
                }
                val m = a.chooseUnit(
                    "You may move a minion adjacent to you 1 space, not adjacent to you (${i + 1} of up to $minions)",
                    movable,
                    optional = true,
                ) ?: return
                a.moveOther(m, 1, endFilter = away)
            }
        }
    }

    private class Ritual(private val coins: Int, private val item: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val enough = a.g.emptySpawnPointsWithin(a.heroId, a.pos, a.radius) >= 2
            // "If you have your Ultimate, gain an Attack item" — a sentence of its own, independent of the first.
            val ultimate = item && a.g.tide(a.heroId)
            if (!enough && !ultimate) a.fizzle("fewer than 2 empty spawn points in radius")
            if (enough) a.g.gainCoins(a.heroId, coins, a.card.name)
            if (ultimate) {
                a.hero.spareItems += Stat.ATTACK
                a.g.touch()
                a.g.note("${a.g.heroName(a.heroId)} gains an Attack item.")
            }
        }
    }

    private class Necromancy(private val inRadius: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val g = a.g
            val team = a.hero.team
            val reach = if (inRadius) a.radius else 1
            // FAQ: a friendly minion of any type that was in play this wave and was defeated or removed: a type
            // the Battle Zone has more friendly spawn points of than such minions on the board (p.19 Respawn).
            val options = ArrayList<Triple<String, MinionType, List<Hex>>>()
            for (lane in g.geo.map.lanes.map { it.id }) {
                val zone = g.battleZone(lane)
                // FAQ: directly onto an empty friendly spawn point (any type) in that Battle Zone; with Tide of
                // Darkness every empty space counts as one.
                val spaces = g.within(a.pos, reach).filter { h ->
                    g.isEmpty(h) && (
                        g.tide(a.heroId) ||
                            (
                                g.zoneOf(h) == zone &&
                                    g.geo.spaces[h]?.spawn?.let { it.team == team && it.kind.minion != null } == true
                                )
                        )
                }
                if (spaces.isEmpty()) continue
                for (type in MinionType.entries) {
                    val kind = SpawnKind.entries.first { it.minion == type }
                    val points = g.geo.minionSpawns(zone).count { it.spawn?.team == team && it.spawn?.kind == kind }
                    val present = g.state.minions.count {
                        it.lane == lane && it.team == team && it.type == type &&
                            it.at != null
                    }
                    if (points > present) options += Triple(lane, type, spaces)
                }
            }
            if (options.isEmpty()) a.fizzle("no friendly minion to respawn")
            val laneNames = g.geo.map.lanes.size > 1
            val pick = if (options.size == 1) {
                options.single()
            } else {
                options[
                    a.chooseOption(
                        "Respawn which friendly minion?",
                        options.map { (lane, type, _) ->
                            "A ${Game.minionName(type)} minion" + if (laneNames) " ($lane)" else ""
                        },
                    )!!,
                ]
            }
            val (lane, type, spaces) = pick
            val h = a.chooseSpace("Respawn it in an empty friendly spawn point", spaces)!!
            g.respawnMinion(team, type, lane, h)
        }
    }

    private class Shield(private val bonus: Int) : CardScript {
        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            val at = d.pos ?: return
            if (d.g.emptySpawnPointsWithin(d.heroId, at, d.radius) >= 2) d.bonus = bonus
        }
    }

    private class Weakness(private val minus: Int, private val noRepeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            private fun applies(g: Game, src: Src, unitId: String): Boolean {
                val owner = src.owner ?: return false
                return g.isHero(unitId) && g.isEnemy(owner, unitId) && g.inRadiusOf(src, unitId) &&
                    g.affectedBy(src, unitId)
            }

            override fun statDelta(g: Game, src: Src, q: StatQuery): Int =
                if (q.stat == Stat.ATTACK && applies(g, src, q.unitId)) -minus else 0

            override fun forbidsRepeat(g: Game, src: Src, heroId: String): Boolean = noRepeat && applies(g, src, heroId)
        }
    }

    private object TideOfDarkness : CardScript {
        override val hasPrimary: Boolean get() = false
    }
}
