package dev.quinton.goa.web.group

import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.web.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The group's synced data in this browser (ADR-043): one [SyncDocument] — players, match log,
 * drafts, trivia rounds, tombstones, player merges and the rating constants — stored canonical
 * ([SyncMerge.normalize]) as [SyncJson] under `group/document` in IndexedDB. The phone keeps the same
 * data in Room tables; the rules for writing it (stamps, tombstones, merges) are the repositories'.
 *
 * Every [update] is one transaction: it re-reads the stored document (another tab may have written),
 * applies the change, stores it and only then publishes it, under a lock shared by the tabs of
 * this site (Web Locks where the browser has them). Other tabs hear about it on a
 * `BroadcastChannel` and reload. [changes] emits after each write, whoever made it (the server
 * sync's trigger, like Room's invalidations on the phone).
 */
class GroupDocumentStore(private val store: KeyValueStore, private val tabs: TabChannel = TabChannel.None) {
    private val state = MutableStateFlow<SyncDocument?>(null)
    private val mutex = Mutex()
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = CHANGE_BUFFER)

    init {
        tabs.listen { reloadFromOtherTab() }
    }

    /** The current document; loads it on first collection. Emits again after every change. */
    val document: Flow<SyncDocument> = flow {
        if (state.value == null) load()
        emitAll(state.filterNotNull())
    }

    /** After each change to the stored document (this tab's or another's). No initial emission. */
    val changes: Flow<Unit> = changed.asSharedFlow()

    suspend fun current(): SyncDocument = state.value ?: load()

    /**
     * Runs [transform] on the stored document and stores what it returns ([Change.document],
     * canonicalised), returning [Change.result]. Nothing is written when the document is unchanged.
     */
    suspend fun <T> update(transform: (SyncDocument) -> Change<T>): T = mutex.withLock {
        tabs.locked {
            val before = read()
            val change = transform(before)
            val after = SyncMerge.normalize(change.document)
            if (after != before) {
                store.put(KEY, SyncJson.encodeToString(SyncDocument.serializer(), after))
                state.value = after
                tabs.announce()
                changed.tryEmit(Unit)
            } else {
                state.value = before
            }
            change.result
        }
    }

    /** What an [update] stores, and what it returns to its caller. */
    class Change<T>(val document: SyncDocument, val result: T)

    private suspend fun load(): SyncDocument = mutex.withLock { state.value ?: read().also { state.value = it } }

    /** The stored document; empty when there is none (or it can't be read, which is kept aside). */
    private suspend fun read(): SyncDocument {
        val text = store.get(KEY) ?: return SyncDocument.EMPTY
        return try {
            SyncMerge.normalize(SyncJson.decodeFromString(SyncDocument.serializer(), text))
        } catch (e: IllegalArgumentException) {
            // Never silently drop the group's data: keep the unreadable copy for a later fix.
            store.put("$KEY.unreadable", text)
            store.delete(KEY)
            console.error("The stored group data couldn't be read; kept as group/$KEY.unreadable", e.message)
            SyncDocument.EMPTY
        }
    }

    private suspend fun reloadFromOtherTab() {
        val fresh = mutex.withLock { read() }
        if (fresh != state.value) {
            state.value = fresh
            changed.tryEmit(Unit)
        }
    }

    private companion object {
        const val KEY = "document"
        const val CHANGE_BUFFER = 16
    }
}
