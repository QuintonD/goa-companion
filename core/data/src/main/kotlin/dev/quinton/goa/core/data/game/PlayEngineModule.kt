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
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.ai.AiCoach
import dev.quinton.goa.core.game.ai.AiPlayers as GameAiPlayers
import dev.quinton.goa.core.game.engine.GameEngine
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** The computer players, one per level (ADR-035). */
fun interface AiPlayers {
    fun forLevel(level: AiLevel): AiPlayer
}

/**
 * The play engine's three entry points (ADR-035): the game factory, the computer players and the
 * coach. **This is the one place to swap in the real engine**, e.g.
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
 * The engine, its AI levels and its coach are bound below; the placeholders after the module remain
 * for builds and tests without them.
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

    private const val AI_SEED = 0x60A_2026L
}

/** No hero is playable yet: New game and the Boards entry show "coming soon". */
internal object ComingSoonGameFactory : GameFactory {
    override val engineVersion: Int = 0
    override val playableHeroes: Set<String> = emptySet()
    override val playableMaps: List<String> = emptyList()

    override fun newGame(setup: GameSetup): GameSession = error("Play against the computer is not in this build yet")

    override fun restore(saved: SavedGame): GameSession = error("Play against the computer is not in this build yet")
}

/** Stand-in AI (tests, builds without the engine): a seeded random legal option. */
internal class RandomLegalAi(private val random: Random = Random(0)) : AiPlayer {
    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice =
        decision.options.random(random)
}

/** A coach with nothing to say. */
internal object QuietCoach : Coach {
    override fun nudge(session: GameSession, decision: Decision, choice: Choice, cancelled: () -> Boolean): CoachNote? =
        null

    override fun turningPoints(saved: SavedGame, seat: String, cancelled: () -> Boolean): List<CoachNote> = emptyList()
}
