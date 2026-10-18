package dev.quinton.goa.core.data.player

import dev.quinton.goa.core.data.PlayerMatchUpRepository
import dev.quinton.goa.core.datastore.player.PlayerMatchUpStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** [PlayerMatchUpRepository] in its own DataStore file ([PlayerMatchUpStore]). */
@Singleton
internal class DataStorePlayerMatchUpRepository @Inject constructor(private val store: PlayerMatchUpStore) :
    PlayerMatchUpRepository {
    override val differentPeople: Flow<Set<String>> = store.differentPairs

    override suspend fun markDifferent(pairKey: String) = store.addDifferent(pairKey)

    override suspend fun forgetDifferent() = store.clearDifferent()
}
