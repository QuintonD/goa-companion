package dev.quinton.goa.core.data.game

import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameFactoryProvider
import dev.quinton.goa.core.game.engine.GameEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The rules engine (ADR-035), built once from the loaded catalog and the bundled maps (the same
 * the board sandbox uses) on [computeDispatcher], then shared by every game. The counter table is
 * the engine's built-in copy of `match-setup.json` (equal, core:game `SetupTest`).
 */
@Singleton
class EngineGameFactoryProvider internal constructor(
    private val catalogRepository: CatalogRepository,
    private val mapRepository: BoardMapRepository,
    private val computeDispatcher: CoroutineDispatcher,
) : GameFactoryProvider {
    @Inject constructor(catalogRepository: CatalogRepository, mapRepository: BoardMapRepository) :
        this(catalogRepository, mapRepository, Dispatchers.Default)

    private val mutex = Mutex()

    @Volatile
    private var engine: GameFactory? = null

    override suspend fun get(): GameFactory = engine ?: mutex.withLock {
        engine ?: run {
            val catalog = catalogRepository.catalog.first()
            val maps = mapRepository.maps.first()
            withContext(computeDispatcher) { GameEngine(catalog, maps) }.also { engine = it }
        }
    }
}
