package dev.quinton.goa.web.group

import dev.quinton.goa.core.data.sync.GroupPrefsStore
import dev.quinton.goa.core.model.sync.GroupSyncPrefs
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.storage.getJson
import dev.quinton.goa.web.storage.putJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * This browser's group membership and sync bookkeeping ([GroupSyncPrefs]: the group key, "who am
 * I", the server and its last outcome) under `group/prefs` in IndexedDB — the phone's
 * `datastore/group_sync`. Never synced. The rules over it are the shared
 * [dev.quinton.goa.core.data.sync.PrefsGroupRepository].
 */
class WebGroupPrefsStore(private val store: KeyValueStore, private val tabs: TabChannel = TabChannel.None) :
    GroupPrefsStore {
    private val state = MutableStateFlow<GroupSyncPrefs?>(null)
    private val mutex = Mutex()

    init {
        tabs.listen { state.value = mutex.withLock { read() } }
    }

    override val prefs: Flow<GroupSyncPrefs> = flow {
        if (state.value == null) mutex.withLock { if (state.value == null) state.value = read() }
        emitAll(state.filterNotNull())
    }

    override suspend fun edit(transform: (GroupSyncPrefs) -> GroupSyncPrefs) = mutex.withLock {
        tabs.locked {
            val before = read()
            val after = transform(before)
            if (after != before) {
                store.putJson(KEY, after)
                tabs.announce()
            }
            state.value = after
        }
    }

    private suspend fun read(): GroupSyncPrefs = store.getJson<GroupSyncPrefs>(KEY) ?: GroupSyncPrefs()

    private companion object {
        const val KEY = "prefs"
    }
}
