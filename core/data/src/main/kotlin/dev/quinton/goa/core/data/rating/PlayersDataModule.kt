package dev.quinton.goa.core.data.rating

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.backup.BackupContributor
import dev.quinton.goa.core.data.backup.BackupRepository
import dev.quinton.goa.core.data.backup.RoomBackupRepository
import dev.quinton.goa.core.data.match.RoomDraftRecordRepository
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RatingsScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RatingsDispatcher

/** Bindings for players, the match log, drafts, ratings and backup (owner: players). */
@Module
@InstallIn(SingletonComponent::class)
internal interface PlayersDataModule {
    @Binds fun players(impl: RoomPlayerRepository): PlayerRepository

    @Binds fun playerMerges(impl: RoomPlayerRepository): PlayerMergeRepository

    @Binds fun matches(impl: RoomMatchRepository): MatchRepository

    @Binds fun drafts(impl: RoomDraftRecordRepository): DraftRecordRepository

    @Binds fun ratings(impl: ReplayRatingsRepository): RatingsRepository

    @Binds fun ratingsSnapshot(impl: ReplayRatingsRepository): RatingsSnapshotRepository

    @Binds fun heroPilots(impl: ReplayRatingsRepository): HeroPilotsRepository

    @Binds fun eloConfig(impl: DataStoreEloConfigRepository): EloConfigRepository

    @Binds fun stampedEloConfig(impl: DataStoreEloConfigRepository): StampedEloConfigStore

    @Binds fun backup(impl: RoomBackupRepository): BackupRepository

    /** Other features add theirs with `@IntoSet`; declared so the set may be empty. */
    @Multibinds fun backupContributors(): Set<BackupContributor>

    companion object {
        /** One replay per process, shared by every collector (ADR-003); the class lives in core:data-contracts. */
        @Provides
        @Singleton
        fun replayRatings(
            matches: MatchRepository,
            drafts: DraftRecordRepository,
            config: EloConfigRepository,
            catalog: CatalogRepository,
            @RatingsScope scope: CoroutineScope,
            @RatingsDispatcher dispatcher: CoroutineDispatcher,
        ): ReplayRatingsRepository = ReplayRatingsRepository(matches, drafts, config, catalog, scope, dispatcher)

        @Provides
        @Singleton
        @RatingsScope
        fun ratingsScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @RatingsDispatcher
        fun ratingsDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        fun editClock(): EditClock = EditClock.System
    }
}
