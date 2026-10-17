package dev.quinton.goa.core.testing

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.player.PlayerNames
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint
import dev.quinton.goa.core.model.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeCatalogRepository(initial: Catalog? = TestCatalog.catalog) : CatalogRepository {
    val state = MutableStateFlow(initial)
    override val catalog: Flow<Catalog> = state.filterNotNull()
}

class FakePlayerRepository(initial: List<Player> = emptyList()) : PlayerRepository {
    val state = MutableStateFlow(initial)

    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> =
        state.map { list -> list.filter { includeArchived || !it.archived } }

    override fun observePlayer(id: String): Flow<Player?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun upsert(player: Player) = state.update { list ->
        list.filterNot { it.id == player.id } + player
    }

    override suspend fun archive(id: String, archived: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }

    override suspend fun findByName(name: String): Player? =
        state.value.sortedBy { it.createdAt }.firstOrNull { PlayerNames.same(it.name, name) }

    override suspend fun ensurePlayer(candidate: Player): Player {
        val existing = findByName(candidate.name)
        return when {
            existing == null -> candidate.copy(name = candidate.name.trim()).also { upsert(it) }

            existing.archived -> existing.copy(archived = false).also { archive(it.id, false) }

            else -> existing
        }
    }
}

class FakeMatchRepository(initial: List<Match> = emptyList()) : MatchRepository {
    val state = MutableStateFlow(initial)

    override fun observeMatches(): Flow<List<Match>> = state

    override fun observeMatch(id: String): Flow<Match?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun upsert(match: Match) = state.update { list -> list.filterNot { it.id == match.id } + match }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }
}

class FakeDraftRecordRepository(initial: List<DraftRecord> = emptyList()) : DraftRecordRepository {
    val state = MutableStateFlow(initial)

    override fun observeDrafts(): Flow<List<DraftRecord>> = state

    override suspend fun save(record: DraftRecord) =
        state.update { list -> list.filterNot { it.id == record.id } + record }
}

class FakeRatingsRepository : RatingsRepository {
    val playerRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    val heroRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    val playerHistory = MutableStateFlow<Map<String, List<RatingPoint>>>(emptyMap())
    val heroHistory = MutableStateFlow<Map<String, List<RatingPoint>>>(emptyMap())
    val heroUsage = MutableStateFlow<Map<String, HeroUsage>>(emptyMap())

    override fun observePlayerRatings(): Flow<Map<String, Rating>> = playerRatings

    override fun observeHeroRatings(): Flow<Map<String, Rating>> = heroRatings

    override fun observePlayerHistory(playerId: String): Flow<List<RatingPoint>> =
        playerHistory.map { it[playerId].orEmpty() }

    override fun observeHeroHistory(heroId: String): Flow<List<RatingPoint>> = heroHistory.map { it[heroId].orEmpty() }

    override fun observeHeroUsage(): Flow<Map<String, HeroUsage>> = heroUsage
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}
