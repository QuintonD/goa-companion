package dev.quinton.goa.feature.draft.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.presentation.draft.DraftEnvironment
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

/** The real [DraftEnvironment] (the interface is shared with the hub in `core:presentation`). */
internal class SystemDraftEnvironment @Inject constructor() : DraftEnvironment {
    override fun newId(): String = UUID.randomUUID().toString()

    override fun newSeed(): Long = Random.nextLong()

    override fun nowMillis(): Long = System.currentTimeMillis()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DraftEnvironmentModule {
    @Binds
    fun environment(impl: SystemDraftEnvironment): DraftEnvironment
}
