package dev.quinton.goa.core.data.game

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.GameFactoryProvider

/** Play against the computer: the engine and the saved games (ADR-035; owner: board). */
@Module
@InstallIn(SingletonComponent::class)
internal interface GameDataModule {
    @Binds fun engine(impl: EngineGameFactoryProvider): GameFactoryProvider

    @Binds fun savedGames(impl: DataStoreSavedGameRepository): SavedGameRepository
}

/**
 * The computer's players and the Coach. **The swap point:** while core:game's `ai/` levels and Coach
 * are being written, every level plays a seeded random legal option ([RandomLegalAi]) and the Coach
 * says nothing ([SilentCoach]). Replace the two right-hand sides with the real implementations
 * (e.g. `AiPlayers { level, seed -> LevelledAi(level, seed) }` and `GameCoach()`); nothing else in
 * the app changes.
 */
@Module
@InstallIn(SingletonComponent::class)
object GameAiModule {
    @Provides fun aiPlayers(): AiPlayers = AiPlayers { _, seed -> RandomLegalAi(seed) }

    @Provides fun coach(): Coach = SilentCoach
}
