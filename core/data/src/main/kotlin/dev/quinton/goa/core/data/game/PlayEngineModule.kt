package dev.quinton.goa.core.data.game

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.game.ai.AiCoach
import dev.quinton.goa.core.game.ai.AiPlayers as GameAiPlayers
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.EnginePuzzleBook
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** The computer players, one per level (ADR-035). */
fun interface AiPlayers {
    fun forLevel(level: AiLevel): AiPlayer
}

/**
 * The play engine's entry points (ADR-035): the rules engine as the game factory, the four computer
 * levels, the coach and the puzzle book, all on one engine. Screens get them through a `Provider` and
 * only on `Dispatchers.Default`, so waiting for the catalog and maps to load never blocks the main
 * thread.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PlayEngineModule {
    @Provides
    @Singleton
    fun factory(catalog: CatalogRepository, maps: BoardMapRepository): GameFactory =
        // Screens ask for the factory on Dispatchers.Default only (see above), never on the main thread.
        runBlocking { GameEngine(catalog.catalog.first(), maps.maps.first()) }

    /** The four levels of core/game/ai (AI.md); one player per request, seeded per level. */
    @Provides
    @Singleton
    fun ai(): AiPlayers = AiPlayers { level -> GameAiPlayers.forLevel(level, AI_SEED + level.ordinal) }

    /** The coach analyses on the game factory's engine (it replays saves for the turning points). */
    @Provides
    @Singleton
    fun coach(factory: GameFactory): Coach = AiCoach(factory)

    /**
     * The bundled puzzles (PRODUCT §10) on the same engine as play (a fresh one if the factory is
     * not the engine): a puzzle is listed once every hero it seats is playable. Screens get it
     * through a `Provider` on `Dispatchers.Default`, as the factory.
     */
    @Provides
    @Singleton
    fun puzzles(factory: GameFactory, catalog: CatalogRepository, maps: BoardMapRepository): PuzzleBook =
        EnginePuzzleBook(
            factory as? GameEngine ?: runBlocking { GameEngine(catalog.catalog.first(), maps.maps.first()) },
        )

    private const val AI_SEED = 0x60A_2026L
}
