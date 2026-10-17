package dev.quinton.goa.core.data.match

import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.database.dao.DraftRecordDao
import dev.quinton.goa.core.database.dao.MatchDao
import dev.quinton.goa.core.database.participantEntities
import dev.quinton.goa.core.database.stepEntities
import dev.quinton.goa.core.database.toEntity
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The match log (source of truth, ADR-003), newest first. Seat order is preserved. */
@Singleton
internal class RoomMatchRepository @Inject constructor(private val dao: MatchDao) : MatchRepository {
    override fun observeMatches(): Flow<List<Match>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeMatch(id: String): Flow<Match?> = dao.observe(id).map { it?.toModel() }

    override suspend fun upsert(match: Match) = dao.upsert(match.toEntity(), match.participantEntities())

    override suspend fun delete(id: String) = dao.delete(id)
}

/** Finished drafts, newest first. */
@Singleton
internal class RoomDraftRecordRepository @Inject constructor(private val dao: DraftRecordDao) : DraftRecordRepository {
    override fun observeDrafts(): Flow<List<DraftRecord>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun save(record: DraftRecord) = dao.upsert(record.toEntity(), record.stepEntities())
}
