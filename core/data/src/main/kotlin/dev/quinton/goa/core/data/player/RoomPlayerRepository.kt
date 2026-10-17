package dev.quinton.goa.core.data.player

import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.database.dao.PlayerDao
import dev.quinton.goa.core.database.toEntity
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.model.player.Player
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Players in Room. Players are archived, never deleted, so old matches keep their names. */
@Singleton
internal class RoomPlayerRepository @Inject constructor(private val dao: PlayerDao) : PlayerRepository {
    /** Serialises [ensurePlayer] so two quick adds of one name can't both insert. */
    private val ensureLock = Mutex()

    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> =
        (if (includeArchived) dao.observeAll() else dao.observeActive()).map { list -> list.map { it.toModel() } }

    override fun observePlayer(id: String): Flow<Player?> = dao.observe(id).map { it?.toModel() }

    override suspend fun upsert(player: Player) {
        require(player.name.isNotBlank()) { "Player name must not be blank" }
        dao.upsert(player.copy(name = player.name.trim()).toEntity())
    }

    override suspend fun archive(id: String, archived: Boolean) = dao.setArchived(id, archived)

    /** Oldest match first, so a group that already has duplicates keeps using the original. */
    override suspend fun findByName(name: String): Player? {
        val key = PlayerNames.key(name)
        return dao.getAll().firstOrNull { PlayerNames.key(it.name) == key }?.toModel()
    }

    override suspend fun ensurePlayer(candidate: Player): Player = ensureLock.withLock {
        require(candidate.name.isNotBlank()) { "Player name must not be blank" }
        val existing = findByName(candidate.name)
        when {
            existing == null -> candidate.copy(name = candidate.name.trim()).also { upsert(it) }
            existing.archived -> existing.copy(archived = false).also { dao.setArchived(it.id, false) }
            else -> existing
        }
    }
}
