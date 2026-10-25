package dev.quinton.goa.web.data.stub

import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.SyncServerControl
import dev.quinton.goa.core.data.SyncServerOutcome
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.data.rating.HeroPilotsRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint
import dev.quinton.goa.core.model.trivia.TriviaRound
import kotlinx.coroutines.flow.Flow

// W2 group (feature/web-group) replaces these in GroupData and deletes this file.

private const val W2 = "W2"

object W2PlayerStub : PlayerRepository, PlayerMergeRepository {
    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> = notYet(W2, "PlayerRepository")

    override fun observePlayer(id: String): Flow<Player?> = notYet(W2, "PlayerRepository")

    override suspend fun upsert(player: Player) = notYet(W2, "PlayerRepository")

    override suspend fun archive(id: String, archived: Boolean) = notYet(W2, "PlayerRepository")

    override suspend fun findByName(name: String): Player? = notYet(W2, "PlayerRepository")

    override suspend fun ensurePlayer(candidate: Player): Player = notYet(W2, "PlayerRepository")

    override fun observeMerges(): Flow<PlayerMerges> = notYet(W2, "PlayerMergeRepository")

    override suspend fun merge(fromId: String, toId: String): PlayerMergeResult = notYet(W2, "PlayerMergeRepository")
}

object W2MatchStub : MatchRepository {
    override fun observeMatches(): Flow<List<Match>> = notYet(W2, "MatchRepository")

    override fun observeMatch(id: String): Flow<Match?> = notYet(W2, "MatchRepository")

    override suspend fun upsert(match: Match) = notYet(W2, "MatchRepository")

    override suspend fun delete(id: String) = notYet(W2, "MatchRepository")
}

object W2DraftRecordStub : DraftRecordRepository {
    override fun observeDrafts(): Flow<List<DraftRecord>> = notYet(W2, "DraftRecordRepository")

    override suspend fun save(record: DraftRecord) = notYet(W2, "DraftRecordRepository")
}

object W2TriviaRoundStub : TriviaRoundRepository {
    override fun observeRounds(): Flow<List<TriviaRound>> = notYet(W2, "TriviaRoundRepository")

    override suspend fun add(round: TriviaRound) = notYet(W2, "TriviaRoundRepository")

    override suspend fun delete(id: String) = notYet(W2, "TriviaRoundRepository")
}

object W2EloConfigStub : EloConfigRepository {
    override val config: Flow<EloConfig> get() = notYet(W2, "EloConfigRepository")

    override suspend fun update(transform: (EloConfig) -> EloConfig) = notYet(W2, "EloConfigRepository")

    override suspend fun reset() = notYet(W2, "EloConfigRepository")
}

/** W2 wires `ReplayRatingsRepository` (core:data-contracts) instead: one instance behind all three. */
object W2RatingsStub : RatingsRepository, RatingsSnapshotRepository, HeroPilotsRepository {
    override fun observePlayerRatings(): Flow<Map<String, Rating>> = notYet(W2, "RatingsRepository")

    override fun observeHeroRatings(): Flow<Map<String, Rating>> = notYet(W2, "RatingsRepository")

    override fun observePlayerHistory(playerId: String): Flow<List<RatingPoint>> = notYet(W2, "RatingsRepository")

    override fun observeHeroHistory(heroId: String): Flow<List<RatingPoint>> = notYet(W2, "RatingsRepository")

    override fun observeHeroUsage(): Flow<Map<String, HeroUsage>> = notYet(W2, "RatingsRepository")

    override fun observeSnapshot(): Flow<RatingsSnapshot> = notYet(W2, "RatingsSnapshotRepository")

    override fun observeHeroPilots(heroId: String): Flow<HeroPilots> = notYet(W2, "HeroPilotsRepository")
}

object W2GroupStub : GroupRepository {
    override val state: Flow<GroupState> get() = notYet(W2, "GroupRepository")

    override suspend fun start(name: String): GroupCredentials = notYet(W2, "GroupRepository")

    override suspend fun join(group: GroupCredentials, serverUrl: String?) = notYet(W2, "GroupRepository")

    override suspend fun setServerUrl(url: String?) = notYet(W2, "GroupRepository")

    override suspend fun setServerSyncEnabled(enabled: Boolean) = notYet(W2, "GroupRepository")

    override suspend fun leave() = notYet(W2, "GroupRepository")

    override suspend fun setMe(playerId: String?) = notYet(W2, "GroupRepository")

    override suspend fun setDeviceName(name: String?) = notYet(W2, "GroupRepository")

    override suspend fun recordSync(phones: List<SyncDevice>, at: Long) = notYet(W2, "GroupRepository")
}

object W2SyncStub : SyncRepository, SyncServerRepository, SyncServerControl {
    override suspend fun export(): SyncDocument = notYet(W2, "SyncRepository")

    override suspend fun apply(incoming: SyncDocument): SyncApplyResult = notYet(W2, "SyncRepository")

    override suspend fun exportFile(group: GroupCredentials): ByteArray = notYet(W2, "SyncRepository")

    override suspend fun importFile(bytes: ByteArray, group: GroupCredentials?): SyncApplyResult =
        notYet(W2, "SyncRepository")

    override suspend fun sync(): SyncServerResult = notYet(W2, "SyncServerRepository")

    override val localChanges: Flow<Unit> get() = notYet(W2, "SyncServerRepository")

    override val running: Flow<Boolean> get() = notYet(W2, "SyncServerControl")

    override fun syncNow() = notYet(W2, "SyncServerControl")

    override fun syncAndWatch(): Flow<SyncServerOutcome> = notYet(W2, "SyncServerControl")
}
