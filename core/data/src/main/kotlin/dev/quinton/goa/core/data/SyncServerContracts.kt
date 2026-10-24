package dev.quinton.goa.core.data

import dev.quinton.goa.core.domain.sync.SyncChanges
import kotlinx.coroutines.flow.Flow

/**
 * Sync through the group's sync server (ADR-036, docs/SYNC-SERVER.md; owner: sync). The server is
 * a mailbox holding one sealed document per group; this phone fetches it, opens it, merges it into
 * its own data exactly as an imported sync file is merged, and uploads the merged document sealed
 * again when the server's copy is missing something. Never runs two syncs at once.
 */
interface SyncServerRepository {
    /**
     * One sync with the server. The outcome is stored for the Group screen ([GroupState.server]);
     * [SyncServerResult.NotConfigured] when not in a group or no server is switched on.
     */
    suspend fun sync(): SyncServerResult

    /**
     * Emits after each change to the synced data on this phone (players, matches, drafts, trivia
     * rounds, deletions, merges, rating constants), whoever wrote it. No initial emission.
     */
    val localChanges: Flow<Unit>
}

sealed interface SyncServerResult {
    data object NotConfigured : SyncServerResult

    /** [changes] arrived on this phone; [uploaded] = the server's copy was replaced by the merge. */
    data class Synced(val changes: SyncChanges, val at: Long, val rev: Long?, val uploaded: Boolean) :
        SyncServerResult

    data class Failed(val error: SyncServerError) : SyncServerResult
}

/** Why a server sync failed. [transient] problems retry with backoff; the others wait for a person. */
enum class SyncServerProblem(val transient: Boolean) {
    /** No connection, DNS failure, connection refused. */
    UNREACHABLE(true),
    TIMEOUT(true),

    /** 5xx, or an answer that makes no sense right now. */
    SERVER(true),

    /** 429: too many requests for now. */
    RATE_LIMITED(true),

    /** A redirect, a plain-HTTP address this build won't use, an untrusted certificate, or no sync server there. */
    WRONG_ADDRESS(false),

    /** 401: the server holds this group under another key. */
    WRONG_KEY(false),

    /** The server's document was made by another [dev.quinton.goa.core.domain.sync.SyncDocument.VERSION]. */
    VERSION(false),

    /** 413, or over the server's 8 MiB before sending. */
    TOO_BIG(false),

    /** Other phones kept replacing the document while this one tried (412, three times). */
    BUSY(false),

    /** This phone's own data fails the checks every phone runs on received data; nothing was sent. */
    INVALID_DATA(false),

    /** The server's document can't be opened or used. */
    BAD_DOCUMENT(false),

    /** The merged data couldn't be saved on this phone (nothing changed). */
    SAVE_FAILED(false),
}

/** A failed server sync: what, in words for the Group screen, and when (epoch ms). */
data class SyncServerError(val problem: SyncServerProblem, val message: String, val at: Long)

/**
 * The build's sync server (BuildConfig `SYNC_SERVER_URL`, Gradle property `goa.syncServer`; empty
 * = none) and whether plain HTTP to a local test server is allowed (debug builds only). Bound by
 * the app module.
 */
data class SyncServerDefaults(val defaultUrl: String?, val allowLocalHttp: Boolean = false) {
    companion object {
        val NONE = SyncServerDefaults(defaultUrl = null)
    }
}
