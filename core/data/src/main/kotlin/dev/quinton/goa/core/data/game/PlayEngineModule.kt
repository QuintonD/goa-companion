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
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.EnginePuzzleBook
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** The computer players, one per strength 1..10 (ADR-035, ADR-039). */
fun interface AiPlayers {
    fun forStrength(strength: Int): AiPlayer

    /** A level alone (a save from before the slider) plays as its anchor strength. */
    fun forLevel(level: AiLevel): AiPlayer = forStrength(level.strength)
}

/**
 * The play engine's entry points (ADR-035): the rules engine as the game factory, the computer
 * players by strength (ADR-039), the coach and the puzzle book, all on one engine. Screens get them
 * through a `Provider` and only on `Dispatchers.Default`, so waiting for the catalog and maps to load
 * never blocks the main thread.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PlayEngineModule {
    @Provides
    @Singleton
    fun factory(catalog: CatalogRepository, maps: BoardMapRepository): GameFactory =
        // Screens ask for the factory on Dispatchers.Default only (see above), never on the main thread.
        runBlocking { GameEngine(catalog.catalog.first(), maps.maps.first()) }

    /**
     * The ten strengths of core/game/ai (AI.md); one player per request, seeded per strength as on the
     * web. The samples of one answer run on a few background threads ([AiSampleThreads], AI.md "Budgets").
     */
    @Provides
    @Singleton
    fun ai(): AiPlayers = AiPlayers { strength -> GameAiPlayers.forApp(strength, AiSampleThreads) }

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
}

/**
 * Runs the samples of one computer answer on up to three daemon threads (the engine has no threads
 * of its own, ADR-043): a phone's big cores search about [width] times as many samples within the
 * same think time (AI.md "Budgets"). The answers combine in sample order, so they don't depend on the
 * threads. Called from `Dispatchers.Default`, never from these threads.
 */
internal object AiSampleThreads : SampleRunner {
    override val width: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, MAX_THREADS)

    private val pool by lazy {
        Executors.newFixedThreadPool(width) { r -> Thread(r, "ai-samples").apply { isDaemon = true } }
    }

    override fun runAll(tasks: List<() -> Unit>) {
        if (width == 1 || tasks.size <= 1) {
            tasks.forEach { it() }
            return
        }
        pool.invokeAll(tasks.map { t -> Callable { t() } }).forEach { it.get() }
    }

    private const val MAX_THREADS = 3
}
