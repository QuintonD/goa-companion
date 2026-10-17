package dev.quinton.goa.core.data.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.SyncRepository

/** Group sync bindings (ADR-025; owner: sync). */
@Module
@InstallIn(SingletonComponent::class)
internal interface SyncDataModule {
    @Binds fun group(impl: DataStoreGroupRepository): GroupRepository

    @Binds fun sync(impl: RoomSyncRepository): SyncRepository
}
