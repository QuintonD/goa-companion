package dev.quinton.goa.core.data.trivia

import androidx.room3.withWriteTransaction
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.database.column
import dev.quinton.goa.core.database.toEntity
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.trivia.TriviaRound
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Finished trivia rounds in Room (ADR-032), oldest first. Like the match log: reads resolve merged
 * players ([PlayerMerges]) so a duplicate's rounds count for the player it was merged into, every
 * write stamps `updatedAt` (later than any earlier copy and any tombstone), and a delete leaves a
 * tombstone in the same transaction so group sync removes the round on every phone (ADR-025).
 */
@Singleton
internal class RoomTriviaRoundRepository @Inject constructor(
    private val database: GoaDatabase,
    private val clock: EditClock = EditClock.System,
) : TriviaRoundRepository {
    private val dao get() = database.triviaRoundDao()

    private val merges: Flow<PlayerMerges> = database.playerDao().observeMerges()
        .map { list -> PlayerMerges(list.map { it.toModel() }) }
        .distinctUntilChanged()

    override fun observeRounds(): Flow<List<TriviaRound>> = combine(dao.observeAll(), merges) { list, m ->
        list.map { entity ->
            val round = entity.toModel()
            val playerId = m.resolve(round.playerId)
            if (playerId == round.playerId) round else round.copy(playerId = playerId)
        }
    }

    override suspend fun add(round: TriviaRound) {
        database.withWriteTransaction {
            val stamp = EditStamp.next(
                clock.now(),
                dao.updatedAt(round.id),
                database.deletionDao().deletedAt(DeletionKind.TRIVIA_ROUND.column(), round.id),
            )
            dao.upsert(round.copy(updatedAt = stamp).toEntity())
        }
    }

    override suspend fun delete(id: String) {
        database.withWriteTransaction {
            val previous = dao.updatedAt(id) ?: return@withWriteTransaction
            val tombstone = database.deletionDao().deletedAt(DeletionKind.TRIVIA_ROUND.column(), id)
            dao.delete(id)
            val deletion = Deletion(DeletionKind.TRIVIA_ROUND, id, EditStamp.next(clock.now(), previous, tombstone))
            database.deletionDao().upsertAll(listOf(deletion.toEntity()))
        }
    }
}
