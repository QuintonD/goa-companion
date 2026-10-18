package dev.quinton.goa.core.data.trivia

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.TriviaProgressRepository

/** Card-trivia bindings (ADR-028; owner: trivia). */
@Module
@InstallIn(SingletonComponent::class)
internal interface TriviaDataModule {
    @Binds fun progress(impl: DataStoreTriviaProgressRepository): TriviaProgressRepository
}
