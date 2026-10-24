package dev.quinton.goa.core.data.game

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.game.SavedGame
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
 * The play engine's entry points (ADR-035): the game factory, the computer players, the coach and
 * the puzzle book. **This is the one place to swap in the real engine**, e.g.
 *
 * ```
 * @Provides @Singleton
 * fun factory(catalog: CatalogRepository, maps: BoardMapRepository): GameFactory =
 *     runBlocking { GameEngine(catalog.catalog.first(), maps.maps.first()) }
 * ```
 *
 * Screens get the factory through a `Provider` and only on `Dispatchers.Default`, so a provider that
 * waits for the catalog and maps to load never blocks the main thread.
 *
 * Until the engine, its AI and its coach are offered, the placeholders below seat no hero: the app
 * says play is coming soon.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PlayEngineModule {
    @Provides
    @Singleton
    fun factory(): GameFactory = ComingSoonGameFactory

    @Provides
    @Singleton
    fun ai(): AiPlayers = AiPlayers { FirstOptionAi }

    @Provides
    @Singleton
    fun coach(): Coach = QuietCoach

    /**
     * The bundled puzzles (PRODUCT §10) on the real engine: a puzzle is listed once every hero it
     * seats is playable. Waits for the catalog and maps to load, so screens get it through a
     * `Provider` on `Dispatchers.Default` (never the main thread), as the factory above.
     */
    @Provides
    @Singleton
    fun puzzles(catalog: CatalogRepository, maps: BoardMapRepository): PuzzleBook = runBlocking {
        EnginePuzzleBook(GameEngine(catalog.catalog.first(), maps.maps.first()))
    }
}

/** No hero is playable yet: New game and the Boards entry show "coming soon". */
internal object ComingSoonGameFactory : GameFactory {
    override val engineVersion: Int = 0
    override val playableHeroes: Set<String> = emptySet()
    override val playableMaps: List<String> = emptyList()

    override fun newGame(setup: GameSetup): GameSession = error("Play against the computer is not in this build yet")

    override fun restore(saved: SavedGame): GameSession = error("Play against the computer is not in this build yet")
}

/** Never reached while no hero is playable; answers with the first listed (always legal) option. */
internal object FirstOptionAi : AiPlayer {
    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice =
        decision.options.first()
}

/** A coach with nothing to say. */
internal object QuietCoach : Coach {
    override fun nudge(session: GameSession, decision: Decision, choice: Choice, cancelled: () -> Boolean): CoachNote? =
        null

    override fun turningPoints(saved: SavedGame, seat: String, cancelled: () -> Boolean): List<CoachNote> = emptyList()
}
