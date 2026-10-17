package dev.quinton.goa.core.datastore.timer

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface TimerStoreModule {
    @Binds fun bind(impl: DataStoreTimerStateStore): TimerStateStore
}
