package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.database.participantEntities
import dev.quinton.goa.core.database.stepEntities
import dev.quinton.goa.core.database.toEntity
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.domain.sync.SyncDocument

/**
 * Reads and writes the syncable part of the database as a [SyncDocument] (rating constants
 * excluded: they live in DataStore). Callers run both inside one transaction. Shared by group
 * sync and backup import, so a backup merge and a sync follow exactly the same rules.
 */
internal class LocalSyncData(private val database: GoaDatabase) {
    /** Everything as stored: raw player ids, every hero id, tombstones and merges. */
    suspend fun read() = SyncDocument(
        players = database.playerDao().getAll().map { it.toModel() },
        matches = database.matchDao().getAll().map { it.toModel() },
        drafts = database.draftRecordDao().getAll().map { it.toModel() },
        triviaRounds = database.triviaRoundDao().getAll().map { it.toModel() },
        deletions = database.deletionDao().getAll().map { it.toModel() },
        playerMerges = database.playerDao().getMerges().map { it.toModel() },
    )

    /** Writes only what differs from [local] to [target] (both canonical). */
    suspend fun write(local: SyncDocument, target: SyncDocument) {
        val players = database.playerDao()
        val matches = database.matchDao()
        val drafts = database.draftRecordDao()

        val oldPlayers = local.players.associateBy { it.id }
        target.players.filter { oldPlayers[it.id] != it }.takeIf { it.isNotEmpty() }
            ?.let { changed -> players.upsertAll(changed.map { it.toEntity() }) }

        val newMatchIds = target.matches.mapTo(HashSet()) { it.id }
        local.matches.filter { it.id !in newMatchIds }.forEach { matches.delete(it.id) }
        val oldMatches = local.matches.associateBy { it.id }
        target.matches.filter { oldMatches[it.id] != it }
            .forEach { matches.upsert(it.toEntity(), it.participantEntities()) }

        val newDraftIds = target.drafts.mapTo(HashSet()) { it.id }
        local.drafts.filter { it.id !in newDraftIds }.forEach { drafts.delete(it.id) }
        val oldDrafts = local.drafts.associateBy { it.id }
        target.drafts.filter { oldDrafts[it.id] != it }.forEach { drafts.upsert(it.toEntity(), it.stepEntities()) }

        val rounds = database.triviaRoundDao()
        val newRoundIds = target.triviaRounds.mapTo(HashSet()) { it.id }
        local.triviaRounds.filter { it.id !in newRoundIds }.forEach { rounds.delete(it.id) }
        val oldRounds = local.triviaRounds.associateBy { it.id }
        target.triviaRounds.filter { oldRounds[it.id] != it }.takeIf { it.isNotEmpty() }
            ?.let { changed -> rounds.upsertAll(changed.map { it.toEntity() }) }

        val oldDeletions = local.deletions.toSet()
        target.deletions.filter { it !in oldDeletions }.takeIf { it.isNotEmpty() }
            ?.let { changed -> database.deletionDao().upsertAll(changed.map { it.toEntity() }) }
        val oldMerges = local.playerMerges.toSet()
        target.playerMerges.filter { it !in oldMerges }.takeIf { it.isNotEmpty() }
            ?.let { changed -> players.upsertMerges(changed.map { it.toEntity() }) }
    }

    /** Empties every syncable table (backup Replace). */
    suspend fun clear() {
        database.matchDao().deleteAll()
        database.draftRecordDao().deleteAll()
        database.triviaRoundDao().deleteAll()
        database.playerDao().deleteAll()
        database.playerDao().deleteAllMerges()
        database.deletionDao().deleteAll()
    }
}
