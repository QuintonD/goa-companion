package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.model.sync.GroupSyncPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * [dev.quinton.goa.core.data.GroupRepository] in its own DataStore ([GroupSyncStore]), apart from
 * the app settings. The rules are shared with the web app ([PrefsGroupRepository], ADR-043).
 */
@Singleton
internal class DataStoreGroupRepository @Inject constructor(
    store: GroupSyncStore,
    defaults: SyncServerDefaults,
) : PrefsGroupRepository(StoreAdapter(store), defaults) {
    private class StoreAdapter(private val store: GroupSyncStore) : GroupPrefsStore {
        override val prefs: Flow<GroupSyncPrefs> get() = store.prefs

        override suspend fun edit(transform: (GroupSyncPrefs) -> GroupSyncPrefs) = store.edit(transform)
    }
}
