package dev.quinton.goa.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import dev.quinton.goa.core.database.entity.DeletionEntity
import dev.quinton.goa.core.database.entity.DraftRecordEntity
import dev.quinton.goa.core.database.entity.DraftStepEntity
import dev.quinton.goa.core.database.entity.DraftWithSteps
import dev.quinton.goa.core.database.entity.MatchEntity
import dev.quinton.goa.core.database.entity.MatchWithParticipants
import dev.quinton.goa.core.database.entity.ParticipantEntity
import dev.quinton.goa.core.database.entity.PlayerEntity
import dev.quinton.goa.core.database.entity.PlayerMergeEntity
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

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun get(id: String): PlayerEntity?

    @Query("SELECT updated_at FROM players WHERE id = :id")
    suspend fun updatedAt(id: String): Long?

    @Query("UPDATE players SET archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM players")
    suspend fun deleteAll()

    @Query("SELECT * FROM player_merges ORDER BY from_id")
    fun observeMerges(): Flow<List<PlayerMergeEntity>>

    @Query("SELECT * FROM player_merges ORDER BY from_id")
    suspend fun getMerges(): List<PlayerMergeEntity>

    @Upsert
    suspend fun upsertMerges(merges: List<PlayerMergeEntity>)

    @Query("DELETE FROM player_merges")
    suspend fun deleteAllMerges()
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

    @Query("SELECT updated_at FROM matches WHERE id = :id")
    suspend fun updatedAt(id: String): Long?

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

    @Query("DELETE FROM draft_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT updated_at FROM draft_records WHERE id = :id")
    suspend fun updatedAt(id: String): Long?

    /** Replaces the draft row and all its steps atomically. */
    @Transaction
    suspend fun upsert(draft: DraftRecordEntity, steps: List<DraftStepEntity>) {
        upsertDraft(draft)
        deleteSteps(draft.id)
        upsertSteps(steps)
    }
}

/** Tombstones of deleted matches and drafts (group sync, ADR-025). */
@Dao
interface DeletionDao {
    @Query("SELECT * FROM deletions ORDER BY kind, id")
    suspend fun getAll(): List<DeletionEntity>

    @Query("SELECT deleted_at FROM deletions WHERE kind = :kind AND id = :id")
    suspend fun deletedAt(kind: String, id: String): Long?

    @Upsert
    suspend fun upsertAll(deletions: List<DeletionEntity>)

    @Query("DELETE FROM deletions")
    suspend fun deleteAll()
}
