package dev.quinton.goa.core.testing

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMatchUpRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.SyncedPhone
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.rating.HeroPilotsRepository
import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.domain.meta.MetaConfig
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.player.PlayerNames
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.sync.PlayerMerge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeCatalogRepository(initial: Catalog? = TestCatalog.catalog) : CatalogRepository {
    val state = MutableStateFlow(initial)
    override val catalog: Flow<Catalog> = state.filterNotNull()
}

/**
 * Players in memory. Like the real repository, lists hide players merged away in [merges] and
 * reading a merged-away id gives the player it joined (share [merges] with a [FakePlayerMergeRepository]).
 */
class FakePlayerRepository(
    initial: List<Player> = emptyList(),
    val merges: MutableStateFlow<List<PlayerMerge>> = MutableStateFlow(emptyList()),
) : PlayerRepository {
    val state = MutableStateFlow(initial)

    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> = combine(state, merges) { list, m ->
        PlayerMerges(m).visible(list).filter { includeArchived || !it.archived }
    }

    override fun observePlayer(id: String): Flow<Player?> = combine(state, merges) { list, m ->
        val resolved = PlayerMerges(m).resolve(id)
        list.firstOrNull { it.id == resolved }
    }

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

    override suspend fun edit(id: String, transform: (Match) -> Match?): Match? {
        val stored = state.value.firstOrNull { it.id == id } ?: return null
        val changed = transform(stored)?.copy(id = id)?.takeIf { it != stored } ?: return null
        upsert(changed)
        return changed
    }

    override suspend fun delete(id: String): Match? {
        val stored = state.value.firstOrNull { it.id == id } ?: return null
        state.update { list -> list.filterNot { it.id == id } }
        return stored
    }

    override suspend fun restore(match: Match): Boolean {
        if (state.value.any { it.id == match.id }) return false
        upsert(match)
        return true
    }
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

    override suspend fun join(group: GroupCredentials, hubUrl: String?, invitedPlayerId: String?) = current.update {
        val same = it.group?.groupId == group.groupId
        it.copy(
            hubUrl = hubUrl ?: it.hubUrl.takeIf { same },
            invitedPlayerId = invitedPlayerId ?: it.invitedPlayerId.takeIf { same },
            group = group,
            phones = if (same) it.phones else emptyList(),
            mePlayerId = if (same) it.mePlayerId else null,
        )
    }

    override suspend fun leave() = current.update {
        it.copy(group = null, phones = emptyList(), lastSyncAt = null, hubUrl = null, invitedPlayerId = null)
    }

    override suspend fun setHubUrl(url: String?) = current.update { it.copy(hubUrl = url) }

    override suspend fun setInvitedPlayer(playerId: String?) = current.update { it.copy(invitedPlayerId = playerId) }

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

/**
 * Merges over a [FakeMatchRepository]'s log, with the real conflict rule. Pass a
 * [FakePlayerRepository]'s `merges` so its lists follow the merges, as the real ones do.
 */
class FakePlayerMergeRepository(
    private val matches: FakeMatchRepository = FakeMatchRepository(),
    val merges: MutableStateFlow<List<PlayerMerge>> = MutableStateFlow(emptyList()),
) : PlayerMergeRepository {
    override fun observeMerges(): Flow<PlayerMerges> = merges.map { PlayerMerges(it) }

    override suspend fun merge(fromId: String, toId: String): PlayerMergeResult {
        val mergedAway = PlayerMerges(merges.value).mergedAway
        if (fromId == toId || fromId in mergedAway || toId in mergedAway) return PlayerMergeResult.NotPossible
        val conflicts = PlayerMerges.conflicts(fromId, toId, matches.state.value)
        if (conflicts.isNotEmpty()) return PlayerMergeResult.PlayedTogether(conflicts)
        merges.update { it + PlayerMerge(fromId, toId, 1) }
        return PlayerMergeResult.Merged
    }
}

/** "Different people" answers in memory. */
class FakePlayerMatchUpRepository(initial: Set<String> = emptySet()) : PlayerMatchUpRepository {
    val state = MutableStateFlow(initial)
    override val differentPeople: Flow<Set<String>> = state

    override suspend fun markDifferent(pairKey: String) = state.update { it + pairKey }

    override suspend fun forgetDifferent() {
        state.value = emptySet()
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
}
