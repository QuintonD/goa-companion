package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import dev.quinton.goa.core.domain.sync.protocol.SyncDataPort
import java.io.IOException

/** One phone's synced data held in memory; [apply] merges like the real repository does. */
class InMemorySyncData(initial: SyncDocument = SyncDocument.EMPTY, var failApply: Boolean = false) : SyncDataPort {
    var document: SyncDocument = SyncMerge.normalize(initial)
        private set

    /** How many merged documents were written. */
    var writes = 0
        private set

    override suspend fun export(): SyncDocument = document

    override suspend fun apply(incoming: SyncDocument): SyncApplyResult {
        if (failApply) throw IOException("Disk full")
        val next = SyncMerge.merge(document, incoming)
        val changes = SyncMerge.changes(document, next)
        document = next
        writes++
        return SyncApplyResult(next, changes)
    }
}
