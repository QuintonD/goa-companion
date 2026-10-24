package dev.quinton.goa.core.data

import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import dev.quinton.goa.core.domain.sync.protocol.SyncDataPort
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
import dev.quinton.goa.core.model.trivia.TriviaRound
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
     * ([dev.quinton.goa.core.data.player.PlayerNames]); archived players included. Null if none.
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

    /**
     * Who the next round is played as (ADR-032): a player id, [GUEST], or null before anyone was
     * picked on this phone (the home then defaults to this phone's "You", else Guest).
     */
    val playingAs: Flow<String?>

    suspend fun setPlayingAs(playerIdOrGuest: String)

    companion object {
        /** [playingAs] for a round played by nobody in particular: not on the leaderboard. */
        const val GUEST = "guest"
    }
}

/**
 * Finished trivia rounds played as a player, the source of the trivia leaderboard (owner: trivia,
 * ADR-032). Synced with the group and kept in the backup file like the match log: writes are stamped,
 * a delete leaves a tombstone.
 */
interface TriviaRoundRepository {
    /** Every round not deleted, player ids resolved through player merges, oldest first. */
    fun observeRounds(): Flow<List<TriviaRound>>

    /** Writes a finished round (stamped `updatedAt`). */
    suspend fun add(round: TriviaRound)

    /** Removes a round from the leaderboard everywhere (tombstone, ADR-025). */
    suspend fun delete(id: String)
}

/** Bundled board maps from `assets/catalog/maps/` (owner: board, ADR-033). Read-only. */
interface BoardMapRepository {
    /** Every bundled map, in catalog order (the core single-lane map first). Emits once loaded. */
    val maps: Flow<List<BoardMap>>
}

/**
 * Boards set up in the board sandbox (owner: board, ADR-033): on this phone and in the backup file,
 * never synced. Every change is written as it happens.
 */
interface BoardRepository {
    /** All boards, most recently changed first. */
    val boards: Flow<List<SavedBoard>>

    fun observe(id: String): Flow<SavedBoard?>

    suspend fun upsert(board: SavedBoard)

    suspend fun delete(id: String)

    /** Backup import: Replace = exactly [boards]; Merge = the later `updatedAt` of each id wins. */
    suspend fun import(boards: List<SavedBoard>, replace: Boolean)
}

/**
 * The game against the computer in progress (owner: board, ADR-035): one at a time, on this phone
 * only (not synced, not in the backup file). Written after every answer.
 */
interface GameSaveRepository {
    /** Emits [StoredGame.None] when no game is in progress; never fails. */
    val current: Flow<StoredGame>

    suspend fun save(game: GameInProgress)

    suspend fun clear()
}

/**
 * Puzzle progress (owner: board, ADR-035, PRODUCT §10): solved, tries, nudges used, solution shown,
 * per puzzle id. On this phone only (not synced, not in the backup file).
 */
interface PuzzleProgressRepository {
    /** Every puzzle with any progress, by id; empty before the first try. Never fails. */
    val progress: Flow<Map<String, PuzzleProgress>>

    /** Rewrites one puzzle's progress in one transaction ([transform] gets the stored one or the default). */
    suspend fun update(puzzleId: String, transform: (PuzzleProgress) -> PuzzleProgress)

    /** Forgets every puzzle's progress. */
    suspend fun reset()
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

/** This phone's group membership and sync bookkeeping (owner: sync). Never synced or backed up. */
interface GroupRepository {
    val state: Flow<GroupState>

    /** Starts a new group with a fresh id and 256-bit key; this phone becomes its first member. */
    suspend fun start(name: String): GroupCredentials

    /**
     * Joins [group] (from an invite). Joining another group forgets the old one's phones, "who am
     * I", sync server and its status. [serverUrl] is the invite's server (ADR-036): null means the
     * build's default, except that re-joining the same group keeps the server this phone has.
     */
    suspend fun join(group: GroupCredentials, serverUrl: String? = null)

    /**
     * The group's sync server ([dev.quinton.goa.core.domain.sync.SyncServerAddress] origin); null
     * or the build's default = use the default. Another server starts with a clean status.
     */
    suspend fun setServerUrl(url: String?)

    /** "Sync through the server" (on by default whenever there is a server). */
    suspend fun setServerSyncEnabled(enabled: Boolean)

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
    /** The group's sync server and how this phone's last sync with it went (ADR-036). */
    val server: ServerSyncState = ServerSyncState(),
) {
    val inGroup: Boolean get() = group != null

    /** In a group with a sync server switched on: automatic server sync runs. */
    val serverSyncOn: Boolean get() = group != null && server.activeUrl != null
}

/** The sync server as this phone sees it (ADR-036); none of it is synced or backed up. */
data class ServerSyncState(
    /** The server in use: the group's own address, else the build's default; null = none known. */
    val url: String? = null,
    /** The address is this phone's choice (or an invite's), not the build's default. */
    val custom: Boolean = false,
    /** The build's default server, for "Use the default"; null = the build has none. */
    val defaultUrl: String? = null,
    /** "Sync through the server". */
    val enabled: Boolean = true,
    /** When this phone last synced with the server (epoch ms); null = not yet (or another group/server). */
    val syncedAt: Long? = null,
    /** The server document's rev after that sync. */
    val rev: Long? = null,
    /** Why the last server sync failed; null when it worked (or none ran yet). */
    val error: SyncServerError? = null,
) {
    /** The server automatic sync uses: null when there is none or it is switched off. */
    val activeUrl: String? get() = url?.takeIf { enabled }
}

@kotlinx.serialization.Serializable
data class SyncedPhone(val deviceId: String, val name: String, val lastSyncedAt: Long)

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
