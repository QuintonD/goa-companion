package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.match.GameMode

/**
 * Read-only data a game needs besides its state: every card of every hero by id, the maps, the
 * card scripts, and the Wave/Life counter table. Shared by every session and fork of an engine.
 */
class EngineData(
    val catalog: Catalog,
    maps: List<BoardMap>,
    val registry: Registry,
    private val matchRules: MatchRules?,
) {
    val maps: Map<String, BoardMap> = maps.associateBy { it.id }
    private val cards: Map<String, Card>
    private val owner: Map<String, Hero>

    init {
        val c = HashMap<String, Card>()
        val o = HashMap<String, Hero>()
        for (h in catalog.heroes) {
            for (card in h.allCards) {
                c[card.id] = card
                o[card.id] = h
            }
        }
        cards = c
        owner = o
    }

    fun card(id: String): Card = cards[id] ?: error("Unknown card $id")

    fun cardOrNull(id: String): Card? = cards[id]

    fun heroOf(cardId: String): Hero? = owner[cardId]

    fun catalogHero(id: String): Hero = catalog.hero(id) ?: error("Unknown hero $id")

    fun heroName(id: String): String = catalog.hero(id)?.name ?: id

    fun geometry(mapId: String): BoardGeometry = BoardGeometry.of(maps[mapId] ?: error("Unknown map $mapId"))

    /** Wave counters per Lane (p.5 step 4, p.20). */
    fun wavesPerLane(lanes: Int, length: GameLength): Int =
        matchRules?.mode(mode(lanes, length))?.waveCountersPerLane ?: Counters.waves(lanes, length)

    /** Life counters per team for [heroes] in play (p.5 step 5, p.20), with the [APP] fallbacks of RULINGS.md. */
    fun lifeCounters(lanes: Int, length: GameLength, heroes: Int): Int {
        val fromRules = matchRules?.mode(mode(lanes, length))?.let { rule ->
            rule.lifeCountersFor(heroes) ?: rule.lifeCounters.let { brackets ->
                // Outside the listed counts: the nearest listed row ([APP], RULINGS.md "Counters").
                if (heroes < brackets.minOf { it.minHeroes }) {
                    brackets.minBy { it.minHeroes }.counters
                } else {
                    brackets.maxBy { it.maxHeroes }.counters
                }
            }
        }
        return fromRules ?: Counters.life(lanes, length, heroes)
    }

    private fun mode(lanes: Int, length: GameLength): GameMode = when {
        lanes > 1 -> GameMode.DOUBLE_LANE
        length == GameLength.LONG -> GameMode.LONG
        else -> GameMode.QUICK
    }
}

/**
 * The rulebook's counter table, built in so the engine works without `match-setup.json`
 * (`CountersTest` checks it equals the bundled file). p.5: Quick 3 Wave counters, Long 5; Life
 * counters 4/6 (4 players), 5/8 (6 players); p.20: 3 or 5 players use the 4-player Life counters;
 * double lane 2 × 7 Wave counters, Life 6 (6–8 players) / 7 (9–10).
 */
object Counters {
    fun waves(lanes: Int, length: GameLength): Int = when {
        lanes > 1 -> 7
        length == GameLength.LONG -> 5
        else -> 3
    }

    fun life(lanes: Int, length: GameLength, heroes: Int): Int = when {
        lanes > 1 -> if (heroes >= 9) 7 else 6
        length == GameLength.LONG -> if (heroes >= 6) 8 else 6
        else -> if (heroes >= 6) 5 else 4
    }
}
