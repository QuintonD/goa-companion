package dev.quinton.goa.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import dev.quinton.goa.core.database.entity.DraftRecordEntity
import dev.quinton.goa.core.database.entity.DraftStepEntity
import dev.quinton.goa.core.database.entity.DraftWithSteps
import dev.quinton.goa.core.database.entity.MatchEntity
import dev.quinton.goa.core.database.entity.MatchWithParticipants
import dev.quinton.goa.core.database.entity.ParticipantEntity
import dev.quinton.goa.core.database.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE archived = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeActive(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id = :id")
    fun observe(id: String): Flow<PlayerEntity?>

    @Query("SELECT * FROM players ORDER BY created_at, id")
    suspend fun getAll(): List<PlayerEntity>

    @Upsert
    suspend fun upsert(player: PlayerEntity)

    @Upsert
    suspend fun upsertAll(players: List<PlayerEntity>)

    @Query("UPDATE players SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM players")
    suspend fun deleteAll()
}

@Dao
interface MatchDao {
    @Transaction
    @Query("SELECT * FROM matches ORDER BY played_at DESC, created_at DESC, id DESC")
    fun observeAll(): Flow<List<MatchWithParticipants>>

    @Transaction
    @Query("SELECT * FROM matches WHERE id = :id")
    fun observe(id: String): Flow<MatchWithParticipants?>

    @Transaction
    @Query("SELECT * FROM matches ORDER BY played_at, created_at, id")
    suspend fun getAll(): List<MatchWithParticipants>

    @Upsert
    suspend fun upsertMatch(match: MatchEntity)

    @Upsert
    suspend fun upsertParticipants(participants: List<ParticipantEntity>)

    @Query("DELETE FROM participants WHERE match_id = :matchId")
    suspend fun deleteParticipants(matchId: String)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM matches")
    suspend fun deleteAll()

    /** Replaces the match row and all its seats atomically. */
    @Transaction
    suspend fun upsert(match: MatchEntity, participants: List<ParticipantEntity>) {
        upsertMatch(match)
        deleteParticipants(match.id)
        upsertParticipants(participants)
    }
}

@Dao
interface DraftRecordDao {
    @Transaction
    @Query("SELECT * FROM draft_records ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<DraftWithSteps>>

    @Transaction
    @Query("SELECT * FROM draft_records ORDER BY created_at, id")
    suspend fun getAll(): List<DraftWithSteps>

    @Upsert
    suspend fun upsertDraft(draft: DraftRecordEntity)

    @Upsert
    suspend fun upsertSteps(steps: List<DraftStepEntity>)

    @Query("DELETE FROM draft_steps WHERE draft_id = :draftId")
    suspend fun deleteSteps(draftId: String)

    @Query("DELETE FROM draft_records")
    suspend fun deleteAll()

    /** Replaces the draft row and all its steps atomically. */
    @Transaction
    suspend fun upsert(draft: DraftRecordEntity, steps: List<DraftStepEntity>) {
        upsertDraft(draft)
        deleteSteps(draft.id)
        upsertSteps(steps)
    }
}
