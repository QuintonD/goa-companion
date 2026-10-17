package dev.quinton.goa.core.data.stub

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * TEMPORARY in-memory stand-ins for the players/matches/ratings area until the Room-backed
 * implementations land. The owner deletes this file.
 */

@Singleton
internal class StubPlayerRepository @Inject constructor() : PlayerRepository {
    private val state = MutableStateFlow<List<Player>>(emptyList())

    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> =
        state.map { list -> list.filter { includeArchived || !it.archived } }

    override fun observePlayer(id: String): Flow<Player?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun upsert(player: Player) = state.update { list ->
        list.filterNot { it.id == player.id } + player
    }

    override suspend fun archive(id: String, archived: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
}

@Singleton
internal class StubMatchRepository @Inject constructor() : MatchRepository {
    private val state = MutableStateFlow<List<Match>>(emptyList())

    override fun observeMatches(): Flow<List<Match>> = state

    override fun observeMatch(id: String): Flow<Match?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun upsert(match: Match) = state.update { list -> list.filterNot { it.id == match.id } + match }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }
}

@Singleton
internal class StubDraftRecordRepository @Inject constructor() : DraftRecordRepository {
    private val state = MutableStateFlow<List<DraftRecord>>(emptyList())

    override fun observeDrafts(): Flow<List<DraftRecord>> = state

    override suspend fun save(record: DraftRecord) =
        state.update { list -> list.filterNot { it.id == record.id } + record }
}

internal class StubRatingsRepository @Inject constructor() : RatingsRepository {
    override fun observePlayerRatings(): Flow<Map<String, Rating>> = flowOf(emptyMap())

    override fun observeHeroRatings(): Flow<Map<String, Rating>> = flowOf(emptyMap())

    override fun observePlayerHistory(playerId: String): Flow<List<RatingPoint>> = flowOf(emptyList())

    override fun observeHeroHistory(heroId: String): Flow<List<RatingPoint>> = flowOf(emptyList())

    override fun observeHeroUsage(): Flow<Map<String, HeroUsage>> = flowOf(emptyMap())
}

@Module
@InstallIn(SingletonComponent::class)
internal interface StubPlayersModule {
    @Binds fun players(impl: StubPlayerRepository): PlayerRepository

    @Binds fun matches(impl: StubMatchRepository): MatchRepository

    @Binds fun drafts(impl: StubDraftRecordRepository): DraftRecordRepository

    @Binds fun ratings(impl: StubRatingsRepository): RatingsRepository
}
