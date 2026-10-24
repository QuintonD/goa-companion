package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.heroes.Heroes
import dev.quinton.goa.core.model.board.BoardMap

/**
 * The rules engine (ADR-035). Built once from the loaded catalog and maps; sessions share it.
 * [matchRules] is `catalog/match-setup.json` when the app has it loaded; the built-in [Counters]
 * table is the same rulebook data otherwise. [allowIncomplete] lets tests seat heroes whose
 * implementation is not complete yet (hero agents' own tests); the app never sets it.
 */
class GameEngine(
    catalog: dev.quinton.goa.core.model.catalog.Catalog,
    maps: List<BoardMap>,
    matchRules: MatchRules? = null,
    registry: Registry = Heroes.registry(),
    private val allowIncomplete: Boolean = false,
) : GameFactory {
    val data: EngineData = EngineData(catalog, maps, registry, matchRules)

    override val playableHeroes: Set<String> =
        registry.heroModules.filter { it.complete && catalog.hero(it.heroId) != null }.map { it.heroId }.toSortedSet()

    /** Verified maps, single lane first (DOMAIN.md "Board map JSON"). */
    override val playableMaps: List<String> =
        maps.filter { it.verified }.sortedBy { it.lanes.size }.map { it.id }

    override fun newGame(setup: GameSetup): GameSession {
        validate(setup)
        return EngineSession(data, setup, Setup.initialState(data, setup)).start()
    }

    /** A game from an arbitrary position (PositionBuilder). */
    fun fromState(setup: GameSetup, state: GameState): EngineSession {
        validate(setup)
        return EngineSession(data, setup, state).start()
    }

    override fun restore(saved: SavedGame): GameSession {
        val payload = EngineSession.JSON.decodeFromString(SavedPayload.serializer(), saved.payload)
        return EngineSession.replay(data, saved.setup, payload.initial, payload.answers)
    }

    private fun validate(setup: GameSetup) {
        require(setup.mapId in data.maps) { "Unknown map ${setup.mapId}" }
        for (s in setup.seats) {
            require(data.catalog.hero(s.heroId) != null) { "Unknown hero ${s.heroId}" }
            require(allowIncomplete || s.heroId in playableHeroes) { "${s.heroId} is not playable yet" }
        }
    }

    companion object {
        /** Bumped when a rules or card change makes old saves replay differently. */
        const val ENGINE_VERSION: Int = 1
    }
}
