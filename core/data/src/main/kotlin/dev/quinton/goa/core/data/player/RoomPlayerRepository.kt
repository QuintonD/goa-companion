package dev.quinton.goa.core.data.player

import androidx.room3.withWriteTransaction
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.database.toEntity
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.domain.player.PlayerNames
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Players in Room. Players are archived, never deleted, so old matches keep their names. A
 * duplicate merged into another player ([merge]) is hidden from every list, and reading its id
 * gives the player it was merged into (DOMAIN.md *Duplicate players*). Every write stamps
 * `updatedAt` so group sync keeps the latest edit (ADR-025).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomPlayerRepository @Inject constructor(
    private val database: GoaDatabase,
    private val clock: EditClock = EditClock.System,
) : PlayerRepository, PlayerMergeRepository {
    private val dao get() = database.playerDao()

    /** Serialises [ensurePlayer] so two quick adds of one name can't both insert. */
    private val ensureLock = Mutex()

    override fun observeMerges(): Flow<PlayerMerges> =
        dao.observeMerges().map { list -> PlayerMerges(list.map { it.toModel() }) }.distinctUntilChanged()

    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> = combine(
        if (includeArchived) dao.observeAll() else dao.observeActive(),
        observeMerges(),
    ) { list, merges -> merges.visible(list.map { it.toModel() }) }

    override fun observePlayer(id: String): Flow<Player?> =
        observeMerges().flatMapLatest { merges -> dao.observe(merges.resolve(id)) }.map { it?.toModel() }

    override suspend fun upsert(player: Player) {
        require(player.name.isNotBlank()) { "Player name must not be blank" }
        database.withWriteTransaction {
            val stamp = EditStamp.next(clock.now(), dao.updatedAt(player.id))
            dao.upsert(player.copy(name = player.name.trim(), updatedAt = stamp).toEntity())
        }
    }

    override suspend fun archive(id: String, archived: Boolean) {
        database.withWriteTransaction {
            val previous = dao.updatedAt(id) ?: return@withWriteTransaction
            dao.setArchived(id, archived, EditStamp.next(clock.now(), previous))
        }
    }

    /** Oldest match first, so a group that already has duplicates keeps using the original. */
    override suspend fun findByName(name: String): Player? {
        val key = PlayerNames.key(name)
        val merges = PlayerMerges(dao.getMerges().map { it.toModel() })
        return merges.visible(dao.getAll().map { it.toModel() }).firstOrNull { PlayerNames.key(it.name) == key }
    }

    override suspend fun ensurePlayer(candidate: Player): Player = ensureLock.withLock {
        require(candidate.name.isNotBlank()) { "Player name must not be blank" }
        val existing = findByName(candidate.name)
        when {
            existing == null -> candidate.copy(name = candidate.name.trim()).also { upsert(it) }
            existing.archived -> existing.copy(archived = false).also { archive(it.id, false) }
            else -> existing
        }
    }

    override suspend fun merge(fromId: String, toId: String): PlayerMergeResult = database.withWriteTransaction {
        val merges = PlayerMerges(dao.getMerges().map { it.toModel() })
        val from = dao.get(fromId)
        val to = dao.get(toId)
        when {
            from == null || to == null || fromId == toId -> PlayerMergeResult.NotPossible

            fromId in merges.mergedAway || toId in merges.mergedAway -> PlayerMergeResult.NotPossible

            else -> {
                val matches = merges.resolve(database.matchDao().getAll().map { it.toModel() })
                val conflicts = PlayerMerges.conflicts(fromId, toId, matches)
                if (conflicts.isNotEmpty()) {
                    PlayerMergeResult.PlayedTogether(conflicts)
                } else {
                    val previous = dao.getMerges().firstOrNull { it.fromId == fromId }?.mergedAt
                    val merge = PlayerMerge(fromId, toId, EditStamp.next(clock.now(), previous))
                    dao.upsertMerges(listOf(merge.toEntity()))
                    PlayerMergeResult.Merged
                }
            }
        }
    }
}
