package dev.quinton.goa.core.data

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import dev.quinton.goa.core.domain.sync.protocol.SyncDataPort
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
import kotlinx.coroutines.flow.Flow

/*
 * Cross-feature contracts. Each interface has exactly one owning implementation (bound with
 * Hilt in the owner's package) and fakes in core:testing. Features depend only on these.
 */

/** Read-only, in-memory catalog loaded from bundled JSON (owner: catalog). */
interface CatalogRepository {
    /** Emits once the catalog is loaded; never re-emits unless the data version changes. */
    val catalog: Flow<Catalog>
}

/** Players (owner: players). */
interface PlayerRepository {
    fun observePlayers(includeArchived: Boolean = false): Flow<List<Player>>

    fun observePlayer(id: String): Flow<Player?>

    suspend fun upsert(player: Player)

    suspend fun archive(id: String, archived: Boolean)

    /**
     * The player with this name, compared trimmed and case-insensitively
     * ([dev.quinton.goa.core.domain.player.PlayerNames]); archived players included. Null if none.
     */
    suspend fun findByName(name: String): Player?

    /**
     * Returns the player called `candidate.name`: the existing one (unarchived if it was
     * archived) when the name is taken, otherwise [candidate] itself, saved with a trimmed name.
     * Never creates a second player with the same name.
     */
    suspend fun ensurePlayer(candidate: Player): Player
}

/** The match log — source of truth for every rating and stat (owner: players). */
interface MatchRepository {
    fun observeMatches(): Flow<List<Match>>

    fun observeMatch(id: String): Flow<Match?>

    suspend fun upsert(match: Match)

    suspend fun delete(id: String)
}

/** Finished drafts, for ban stats and draft history (owner: players/database; used by draft). */
interface DraftRecordRepository {
    fun observeDrafts(): Flow<List<DraftRecord>>

    suspend fun save(record: DraftRecord)
}

/** Derived ratings and meta stats, recomputed from the match log (owner: players). */
interface RatingsRepository {
    fun observePlayerRatings(): Flow<Map<String, Rating>>

    fun observeHeroRatings(): Flow<Map<String, Rating>>

    fun observePlayerHistory(playerId: String): Flow<List<RatingPoint>>

    fun observeHeroHistory(heroId: String): Flow<List<RatingPoint>>

    fun observeHeroUsage(): Flow<Map<String, HeroUsage>>
}

/**
 * Fills the draft setup with a group so it can draft again (owner: draft; used by the players
 * feature's "Rematch → Draft again"). [red]/[blue]: distinct player ids per team. [doublePilot]:
 * who controls two heroes, if anyone; [handicap]: the teams had uneven seats (Handicap cards).
 */
interface DraftSetupPrefill {
    suspend fun prefill(red: List<String>, blue: List<String>, doublePilot: String?, handicap: Boolean)
}

/** App-wide settings in DataStore (owner: settings). */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/**
 * Card-number trivia progress and the last quiz setup (owner: trivia, ADR-028). This phone's own
 * practice record: never synced, not in the backup file (Android Auto Backup still covers it).
 */
interface TriviaProgressRepository {
    /** Mastery per fact key ([dev.quinton.goa.core.domain.trivia.TriviaFact.key]). */
    val progress: Flow<Map<String, FactProgress>>

    /** The setup of the last quiz started, or null before the first one. */
    val lastConfig: Flow<TriviaConfig?>

    /** Stores the new progress of the given facts (merged into what is stored). */
    suspend fun record(updates: Map<String, FactProgress>)

    suspend fun saveConfig(config: TriviaConfig)

    /** Forgets the progress of facts on these heroes' cards; null = everything. */
    suspend fun reset(heroIds: Set<String>? = null)
}

/** Wall-clock time (epoch ms) for the `updatedAt` stamps on local writes (ADR-025). */
fun interface EditClock {
    fun now(): Long

    companion object {
        val System = EditClock { java.lang.System.currentTimeMillis() }
    }
}

/** Duplicate players (owner: players): one person with two ids, typically from two phones. */
interface PlayerMergeRepository {
    fun observeMerges(): Flow<PlayerMerges>

    /**
     * Records that [fromId] is the same person as [toId]. Refused when the two played in the same
     * match ([PlayerMergeResult.PlayedTogether]): one person can't sit on both teams, and two
     * teammates are two people.
     */
    suspend fun merge(fromId: String, toId: String): PlayerMergeResult
}

sealed interface PlayerMergeResult {
    data object Merged : PlayerMergeResult

    /** The matches both played in (resolved, newest last). */
    data class PlayedTogether(val matches: List<Match>) : PlayerMergeResult

    /** A player is missing, already merged away, or both ids are the same. */
    data object NotPossible : PlayerMergeResult
}

/**
 * "Same people?" answers on this phone (owner: players, ADR-031): the pairs it was told are
 * different people, as `PlayerMatchUp.pairKey`s. Never synced, not in the backup file.
 */
interface PlayerMatchUpRepository {
    val differentPeople: Flow<Set<String>>

    suspend fun markDifferent(pairKey: String)

    /** Asks again about every pair. */
    suspend fun forgetDifferent()
}

/** This phone's group membership and sync bookkeeping (owner: sync). Never synced or backed up. */
interface GroupRepository {
    val state: Flow<GroupState>

    /** Starts a new group with a fresh id and 256-bit key; this phone becomes its first member. */
    suspend fun start(name: String): GroupCredentials

    /**
     * Joins [group] (from an invite). Joining another group forgets the old one's phones, hub and
     * "who am I". [hubUrl] and [invitedPlayerId] come from the invite (ADR-030, ADR-031); null
     * keeps what this phone already knows about the same group.
     */
    suspend fun join(group: GroupCredentials, hubUrl: String? = null, invitedPlayerId: String? = null)

    /** The group's hub origin ([dev.quinton.goa.core.domain.sync.HubAddress]); null = no hub. */
    suspend fun setHubUrl(url: String?)

    /** The player a personal invite named; cleared once this phone answers "You're …?". */
    suspend fun setInvitedPlayer(playerId: String?)

    /** Leaves the group: the key is forgotten, all data stays on this phone. */
    suspend fun leave()

    /** Which player this phone belongs to ("Who are you?"); null = not chosen. */
    suspend fun setMe(playerId: String?)

    suspend fun setDeviceName(name: String?)

    /** Remembers that this phone synced with [phones] at [at]. */
    suspend fun recordSync(phones: List<SyncDevice>, at: Long)
}

data class GroupState(
    val group: GroupCredentials?,
    /** This install's stable id (not secret; tells phones apart in "last synced"). */
    val deviceId: String,
    /** The name other phones see, if set here; otherwise the app uses "who am I" or the model. */
    val deviceName: String?,
    val mePlayerId: String?,
    /** Phones this one has synced with, most recent first. */
    val phones: List<SyncedPhone>,
    val lastSyncAt: Long?,
    /** The group's hub origin (ADR-030); null = no hub, Nearby and files only. */
    val hubUrl: String? = null,
    /** The player a personal invite was made for, not yet confirmed (ADR-031). */
    val invitedPlayerId: String? = null,
    /** When this phone last synced with the hub; null = not yet (or another hub/group since). */
    val hubSyncedAt: Long? = null,
    /** Why the last hub sync failed; null when it worked (or none ran yet). */
    val hubError: HubError? = null,
) {
    val inGroup: Boolean get() = group != null

    /** In a group that has a hub: automatic sync runs (ADR-030 §5). */
    val hubSyncOn: Boolean get() = group != null && hubUrl != null
}

@kotlinx.serialization.Serializable
data class SyncedPhone(val deviceId: String, val name: String, val lastSyncedAt: Long)

/**
 * Automatic sync with the group's hub (ADR-030 §5; owner: sync): this phone's data sealed as a
 * sync file, posted to `{hub}/api/v1/sync`, and the hub's merged reply applied exactly like an
 * imported sync file. Never runs two round trips at once.
 */
interface HubSyncRepository {
    /**
     * One round trip with the hub. The outcome is stored in [GroupState.hubSyncedAt] and
     * [GroupState.hubError]; [HubSyncResult.NotConfigured] when not in a group with a hub.
     */
    suspend fun sync(): HubSyncResult

    /**
     * Emits after each change to the synced data on this phone (players, matches, drafts,
     * deletions, merges, rating constants), whoever wrote it. No initial emission.
     */
    val localChanges: Flow<Unit>
}

sealed interface HubSyncResult {
    data object NotConfigured : HubSyncResult

    data class Synced(val changes: SyncChanges, val at: Long) : HubSyncResult

    data class Failed(val error: HubError) : HubSyncResult
}

/** Why a hub sync failed. [transient] problems are retried with backoff; the others need a person. */
enum class HubProblem(val transient: Boolean) {
    /** No connection, DNS failure, connection refused. */
    UNREACHABLE(true),
    TIMEOUT(true),

    /** The hub answered 5xx or something unexpected. */
    SERVER(true),

    /** 404, a redirect, or a plain-HTTP address this build won't use. */
    WRONG_ADDRESS(false),

    /** 401: the hub doesn't take this phone's group key. */
    WRONG_KEY(false),

    /** 409: the hub and the app have different sync data versions. */
    VERSION(false),

    /** 422: the hub refused this phone's data, or this phone's own checks did (before sending). */
    INVALID_DATA(false),

    /** 413, or over the size limit before sending. */
    TOO_BIG(false),

    /** The hub's reply couldn't be opened or used. */
    BAD_REPLY(false),
}

/** A failed hub sync: what, in words for the Group screen, and when (epoch ms). */
data class HubError(val problem: HubProblem, val message: String, val at: Long)

/**
 * This phone's side of group sync (owner: sync): its whole shareable data as a [SyncDocument], and
 * merging a received one in, all in one database transaction ([SyncDataPort]).
 */
interface SyncRepository : SyncDataPort {
    /** "Share sync file": this phone's data sealed with [group]'s key. */
    suspend fun exportFile(group: GroupCredentials): ByteArray

    /** Opens a sync file (throws [dev.quinton.goa.core.domain.sync.SyncFileException]) and merges it in. */
    suspend fun importFile(bytes: ByteArray, group: GroupCredentials?): SyncApplyResult
}
