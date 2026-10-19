package dev.quinton.goa.core.data.match

import androidx.room3.withWriteTransaction
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.database.column
import dev.quinton.goa.core.database.participantEntities
import dev.quinton.goa.core.database.stepEntities
import dev.quinton.goa.core.database.toEntity
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The match log (source of truth, ADR-003), newest first. Seat order is preserved. Reads resolve
 * merged players ([PlayerMerges]), so every screen and the rating replay see one id per person.
 * Every write stamps `updatedAt` (later than the previous edit and any tombstone), and a delete
 * leaves a tombstone in the same transaction, so group sync carries edits and deletions (ADR-025).
 */
@Singleton
internal class RoomMatchRepository @Inject constructor(
    private val database: GoaDatabase,
    private val clock: EditClock = EditClock.System,
) : MatchRepository {
    private val dao get() = database.matchDao()

    private val merges: Flow<PlayerMerges> = database.playerDao().observeMerges()
        .map { list -> PlayerMerges(list.map { it.toModel() }) }
        .distinctUntilChanged()

    override fun observeMatches(): Flow<List<Match>> =
        combine(dao.observeAll(), merges) { list, m -> m.resolve(list.map { it.toModel() }) }

    override fun observeMatch(id: String): Flow<Match?> =
        combine(dao.observe(id), merges) { match, m -> match?.toModel()?.let(m::resolve) }

    override suspend fun upsert(match: Match) {
        database.withWriteTransaction {
            val stamp = EditStamp.next(
                clock.now(),
                dao.updatedAt(match.id),
                database.deletionDao().deletedAt(DeletionKind.MATCH.column(), match.id),
            )
            val stamped = match.copy(updatedAt = stamp)
            dao.upsert(stamped.toEntity(), stamped.participantEntities())
        }
    }

    override suspend fun edit(id: String, transform: (Match) -> Match?): Match? = database.withWriteTransaction {
        val stored = dao.get(id)?.toModel() ?: return@withWriteTransaction null
        val changed = transform(stored)?.copy(id = id)?.takeIf { it != stored } ?: return@withWriteTransaction null
        val stamp = EditStamp.next(
            clock.now(),
            stored.updatedAt,
            database.deletionDao().deletedAt(DeletionKind.MATCH.column(), id),
        )
        val stamped = changed.copy(updatedAt = stamp)
        dao.upsert(stamped.toEntity(), stamped.participantEntities())
        stamped
    }

    override suspend fun delete(id: String): Match? = database.withWriteTransaction {
        val stored = dao.get(id)?.toModel() ?: return@withWriteTransaction null
        val tombstone = database.deletionDao().deletedAt(DeletionKind.MATCH.column(), id)
        dao.delete(id)
        val deletion = Deletion(DeletionKind.MATCH, id, EditStamp.next(clock.now(), stored.updatedAt, tombstone))
        database.deletionDao().upsertAll(listOf(deletion.toEntity()))
        stored
    }

    override suspend fun restore(match: Match): Boolean = database.withWriteTransaction {
        if (dao.updatedAt(match.id) != null) return@withWriteTransaction false
        val stamp = EditStamp.next(
            clock.now(),
            match.updatedAt,
            database.deletionDao().deletedAt(DeletionKind.MATCH.column(), match.id),
        )
        val stamped = match.copy(updatedAt = stamp)
        dao.upsert(stamped.toEntity(), stamped.participantEntities())
        true
    }
}

/** Finished drafts, newest first. Writes are stamped like matches. */
@Singleton
internal class RoomDraftRecordRepository @Inject constructor(
    private val database: GoaDatabase,
    private val clock: EditClock = EditClock.System,
) : DraftRecordRepository {
    private val dao get() = database.draftRecordDao()

    override fun observeDrafts(): Flow<List<DraftRecord>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun save(record: DraftRecord) {
        database.withWriteTransaction {
            val stamp = EditStamp.next(
                clock.now(),
                dao.updatedAt(record.id),
                database.deletionDao().deletedAt(DeletionKind.DRAFT.column(), record.id),
            )
            val stamped = record.copy(updatedAt = stamp)
            dao.upsert(stamped.toEntity(), stamped.stepEntities())
        }
    }
}
