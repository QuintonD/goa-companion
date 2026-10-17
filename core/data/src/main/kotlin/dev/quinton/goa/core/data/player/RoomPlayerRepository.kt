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

/** Players in Room. Players are archived, never deleted, so old matches keep their names. */
@Singleton
internal class RoomPlayerRepository @Inject constructor(private val dao: PlayerDao) : PlayerRepository {
    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> =
        (if (includeArchived) dao.observeAll() else dao.observeActive()).map { list -> list.map { it.toModel() } }

    override fun observePlayer(id: String): Flow<Player?> = dao.observe(id).map { it?.toModel() }

    override suspend fun upsert(player: Player) {
        require(player.name.isNotBlank()) { "Player name must not be blank" }
        dao.upsert(player.copy(name = player.name.trim()).toEntity())
    }

    override suspend fun archive(id: String, archived: Boolean) = dao.setArchived(id, archived)
}
