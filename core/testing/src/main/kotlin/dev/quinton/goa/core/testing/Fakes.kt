package dev.quinton.goa.core.testing

import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.SyncedPhone
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.data.player.PlayerNames
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.data.rating.HeroPilotsRepository
import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.domain.meta.MetaConfig
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.model.trivia.TriviaRound
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

class FakeHeroPilotsRepository(initial: Map<String, HeroPilots> = emptyMap()) : HeroPilotsRepository {
    val pilots = MutableStateFlow(initial)

    override fun observeHeroPilots(heroId: String): Flow<HeroPilots> = pilots.map {
        it[heroId] ?: HeroPilots(heroId, WinLoss.NONE, MetaConfig().bestWithMinGames, emptyList(), emptyList())
    }

    companion object {
        /** Pilots computed by the real meta stats, as the app does. */
        fun from(matches: List<Match>): FakeHeroPilotsRepository =
            FakeHeroPilotsRepository(MetaStats.compute(matches, emptyList()).pilotsByHero)
    }
}

/** This phone's group (no DataStore). */
class FakeGroupRepository(initial: GroupState = GroupState(null, "this-phone", null, null, emptyList(), null)) :
    GroupRepository {
    val current = MutableStateFlow(initial)
    override val state: Flow<GroupState> = current

    override suspend fun start(name: String): GroupCredentials =
        GroupCredentials.create(name).also { join(it) }

    override suspend fun join(group: GroupCredentials) = current.update {
        val same = it.group?.groupId == group.groupId
        it.copy(
            group = group,
            phones = if (same) it.phones else emptyList(),
            mePlayerId = if (same) it.mePlayerId else null,
        )
    }

    override suspend fun leave() = current.update { it.copy(group = null, phones = emptyList(), lastSyncAt = null) }

    override suspend fun setMe(playerId: String?) = current.update { it.copy(mePlayerId = playerId) }

    override suspend fun setDeviceName(name: String?) = current.update { it.copy(deviceName = name) }

    override suspend fun recordSync(phones: List<SyncDevice>, at: Long) = current.update { s ->
        s.copy(
            phones = phones.map { SyncedPhone(it.deviceId, it.name, at) } +
                s.phones.filterNot { p -> phones.any { it.deviceId == p.deviceId } },
            lastSyncAt = at,
        )
    }
}

/** Merges over a [FakeMatchRepository]'s log, with the real conflict rule. */
class FakePlayerMergeRepository(private val matches: FakeMatchRepository = FakeMatchRepository()) :
    PlayerMergeRepository {
    val merges = MutableStateFlow<List<PlayerMerge>>(emptyList())

    override fun observeMerges(): Flow<PlayerMerges> = merges.map { PlayerMerges(it) }

    override suspend fun merge(fromId: String, toId: String): PlayerMergeResult {
        if (fromId == toId) return PlayerMergeResult.NotPossible
        val conflicts = PlayerMerges.conflicts(fromId, toId, matches.state.value)
        if (conflicts.isNotEmpty()) return PlayerMergeResult.PlayedTogether(conflicts)
        merges.update { it + PlayerMerge(fromId, toId, 1) }
        return PlayerMergeResult.Merged
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

class FakeTriviaProgressRepository(
    initial: Map<String, FactProgress> = emptyMap(),
    config: TriviaConfig? = null,
    playingAs: String? = null,
) : TriviaProgressRepository {
    val state = MutableStateFlow(initial)
    val configState = MutableStateFlow(config)
    override val progress: Flow<Map<String, FactProgress>> = state
    override val lastConfig: Flow<TriviaConfig?> = configState

    override suspend fun record(updates: Map<String, FactProgress>) = state.update { it + updates }

    override suspend fun saveConfig(config: TriviaConfig) {
        configState.value = config
    }

    /** Fact keys are `<cardId>/<stat>` and card ids start with `<heroId>-`. */
    override suspend fun reset(heroIds: Set<String>?) = state.update { map ->
        if (heroIds == null) emptyMap() else map.filterKeys { key -> heroIds.none { key.startsWith("$it-") } }
    }

    val playingAsState = MutableStateFlow(playingAs)
    override val playingAs: Flow<String?> = playingAsState

    override suspend fun setPlayingAs(playerIdOrGuest: String) {
        playingAsState.value = playerIdOrGuest
    }
}

class FakeTriviaRoundRepository(initial: List<TriviaRound> = emptyList()) : TriviaRoundRepository {
    val state = MutableStateFlow(initial)

    override fun observeRounds(): Flow<List<TriviaRound>> = state.map { rounds ->
        rounds.sortedBy { it.playedAt }
    }

    override suspend fun add(round: TriviaRound) = state.update { rounds ->
        rounds.filterNot { it.id == round.id } +
            round
    }

    override suspend fun delete(id: String) = state.update { rounds -> rounds.filterNot { it.id == id } }
}

class FakeBoardMapRepository(maps: List<BoardMap>) : BoardMapRepository {
    override val maps: Flow<List<BoardMap>> = MutableStateFlow(maps)
}

class FakePuzzleProgressRepository(initial: Map<String, PuzzleProgress> = emptyMap()) : PuzzleProgressRepository {
    val state = MutableStateFlow(initial)

    override val progress: Flow<Map<String, PuzzleProgress>> = state

    override suspend fun update(puzzleId: String, transform: (PuzzleProgress) -> PuzzleProgress) =
        state.update { it + (puzzleId to transform(it[puzzleId] ?: PuzzleProgress())) }

    override suspend fun reset() {
        state.value = emptyMap()
    }
}

class FakeGameSaveRepository(initial: StoredGame = StoredGame.None) : GameSaveRepository {
    val state = MutableStateFlow(initial)

    /** Every game written, oldest first. */
    val saves = mutableListOf<GameInProgress>()

    override val current: Flow<StoredGame> = state

    override suspend fun save(game: GameInProgress) {
        saves += game
        state.value = StoredGame.Saved(game)
    }

    override suspend fun clear() {
        state.value = StoredGame.None
    }
}

class FakeBoardRepository(initial: List<SavedBoard> = emptyList()) : BoardRepository {
    val state = MutableStateFlow(initial)

    override val boards: Flow<List<SavedBoard>> = state.map { boards -> boards.sortedByDescending { it.updatedAt } }

    override fun observe(id: String): Flow<SavedBoard?> = state.map { boards -> boards.firstOrNull { it.id == id } }

    override suspend fun upsert(board: SavedBoard) = state.update { boards ->
        boards.filterNot { it.id == board.id } +
            board
    }

    override suspend fun delete(id: String) = state.update { boards -> boards.filterNot { it.id == id } }

    override suspend fun import(boards: List<SavedBoard>, replace: Boolean) = state.update { current ->
        if (replace) {
            boards
        } else {
            (current + boards).groupBy { it.id }.values.map { copies -> copies.maxBy { it.updatedAt } }
        }
    }
}
