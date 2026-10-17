package dev.quinton.goa.core.data.sync

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.rating.StampedEloConfigStore
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This phone's data as a [SyncDocument], and merging another phone's in (ADR-025). The match log
 * is exported raw (the ids it was logged with, every hero id kept): merges and the Unknown-hero
 * rule apply when reading, so nothing is lost on the way through an older phone.
 *
 * [apply] re-reads the phone's data and writes the merge inside one write transaction, so an edit
 * made during the sync is merged too and a failure leaves everything as it was. The rating
 * constants live in DataStore and are written right after the transaction.
 */
@Singleton
internal class RoomSyncRepository @Inject constructor(
    private val database: GoaDatabase,
    private val eloConfig: StampedEloConfigStore,
    private val clock: EditClock,
) : SyncRepository {
    private val local = LocalSyncData(database)

    override suspend fun export(): SyncDocument {
        val config = eloConfig.stamped()
        return SyncMerge.normalize(database.withReadTransaction { local.read() }.copy(eloConfig = config))
    }

    override suspend fun apply(incoming: SyncDocument): SyncApplyResult {
        val config = eloConfig.stamped()
        val result = database.withWriteTransaction {
            val before = SyncMerge.normalize(local.read().copy(eloConfig = config))
            val merged = SyncMerge.merge(before, incoming)
            local.write(before, merged)
            SyncApplyResult(merged, SyncMerge.changes(before, merged))
        }
        if (result.document.eloConfig != config) eloConfig.applySynced(result.document.eloConfig)
        return result
    }

    override suspend fun exportFile(group: GroupCredentials): ByteArray =
        SyncCodec.encodeFile(export(), group, createdAt = clock.now())

    override suspend fun importFile(bytes: ByteArray, group: GroupCredentials?): SyncApplyResult =
        apply(SyncCodec.decodeFile(bytes, group))
}
