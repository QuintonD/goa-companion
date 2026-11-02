package dev.quinton.goa.core.data.online

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.datastore.online.OnlineAccountPrefs
import dev.quinton.goa.core.datastore.online.OnlineAccountStore
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.OnlineGateway
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardCache
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardFetcher
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRepository
import javax.inject.Inject

/** [OnlineIdentityStore] on [OnlineAccountStore], which keeps the account out of every backup. */
class DataStoreOnlineIdentityStore @Inject constructor(private val store: OnlineAccountStore) : OnlineIdentityStore {
    override suspend fun load(): OnlineIdentity? =
        store.load()?.let { OnlineIdentity(it.accountId, it.token, it.name) }

    override suspend fun save(identity: OnlineIdentity) =
        store.save(OnlineAccountPrefs(identity.accountId, identity.token, identity.name))

    override suspend fun clear() = store.clear()
}

/** The seams of online games (ADR-056, docs/ONLINE.md "The client"). */
@Module
@InstallIn(SingletonComponent::class)
internal interface OnlineDataModule {
    @Binds fun connector(impl: OkHttpGameConnector): GameConnector

    @Binds fun gateway(impl: OkHttpOnlineGateway): OnlineGateway

    @Binds fun identities(impl: DataStoreOnlineIdentityStore): OnlineIdentityStore

    @Binds fun fetcher(impl: OkHttpLeaderboardFetcher): LeaderboardFetcher

    @Binds fun cache(impl: FileLeaderboardCache): LeaderboardCache
}

@Module
@InstallIn(SingletonComponent::class)
internal object OnlineLeaderboardModule {
    @Provides
    fun leaderboard(
        fetcher: LeaderboardFetcher,
        cache: LeaderboardCache,
        defaults: GameServerDefaults,
        clock: EditClock,
    ): LeaderboardRepository = LeaderboardRepository(fetcher, cache, defaults.url.orEmpty(), clock::now)
}
