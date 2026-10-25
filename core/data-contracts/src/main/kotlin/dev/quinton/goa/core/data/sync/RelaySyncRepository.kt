package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.SyncCipher
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFileCodec
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncHash
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.SyncServerToken
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.domain.sync.VersionGap
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.format.DateTimeComponents

/**
 * One HTTP exchange with the sync server, whatever does the talking: OkHttp on the phone, `fetch` in
 * the browser (ADR-043). The client follows no redirects, keeps no cache or cookies and logs nothing.
 */
interface RelayHttp {
    /**
     * Sends [request] and hands its response to [read]; the response is closed afterwards. Throws
     * [RelayNetworkException] when no answer came (no connection, timeout, refused certificate…).
     */
    suspend fun <T> exchange(request: RelayRequest, read: suspend (RelayResponse) -> T): T
}

class RelayRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray? = null,
    val contentType: String? = null,
)

interface RelayResponse {
    /** The HTTP status; a redirect the client refused to follow is any 3xx. */
    val code: Int

    /** A response header (case-insensitive), or null. */
    fun header(name: String): String?

    /** The body, or null when it is longer than [max] bytes (reading stops there). */
    suspend fun body(max: Long): ByteArray?
}

/** Why no answer came; the words are [RelaySyncRepository]'s. */
enum class RelayNetworkFailure { UNREACHABLE, TIMEOUT, HTTPS_ONLY, CERTIFICATE }

class RelayNetworkException(val failure: RelayNetworkFailure, cause: Throwable? = null) :
    Exception(failure.name, cause)

/**
 * [SyncServerRepository] following the "Phone algorithm" in docs/SYNC-SERVER.md, shared by the phone
 * (over OkHttp) and the web app (over `fetch`), ADR-043:
 *
 * 1. `GET /v1/groups/{groupId}/document`: `404` = no document yet; `200` = the sealed document
 *    and its rev (`ETag`), opened with [SyncFileCodec.decodeFile] (another document version stops).
 *    When this device's data still hashes to the document both sides held at the last sync, the GET
 *    carries `If-None-Match: "<rev>"` and a `304` ends the sync: neither side has anything new.
 * 2. Merge it in through [SyncRepository.apply], the path an imported sync file takes (it re-reads
 *    this device's data and writes the merge in one transaction): the result is
 *    `SyncMerge.merge(local, remote)`, or `SyncMerge.normalize(local)` when there is none.
 * 3. Only when the server has no document or its content differs from the merge (compared as
 *    canonical documents, so a fresh envelope salt or `createdAt` never counts): `PUT` the merge,
 *    sealed, with `If-Match: "<rev>"` (or `If-None-Match: *`). `412` = another device got there
 *    first: back to 1, at most [MAX_ATTEMPTS] tries in all.
 * 4. The outcome (time, rev, or the error in words) goes to [PrefsGroupRepository.recordServerSync].
 *
 * Neither the token nor any body is ever logged, and errors carry only words for people.
 */
class RelaySyncRepository(
    private val groups: PrefsGroupRepository,
    private val sync: SyncRepository,
    override val localChanges: Flow<Unit>,
    private val clock: EditClock,
    private val http: RelayHttp,
    private val cipher: SyncCipher,
) : SyncServerRepository {
    private val mutex = Mutex()

    override suspend fun sync(): SyncServerResult = mutex.withLock {
        val state = groups.state.first()
        val group = state.group
        val server = state.server.activeUrl
        if (group == null || server == null) return SyncServerResult.NotConfigured
        val outcome = try {
            run(group, server) ?: return SyncServerResult.NotConfigured
        } catch (e: Failure) {
            val error = SyncServerError(e.problem, e.message, clock.now(), e.retryAfterSeconds)
            Done(SyncServerResult.Failed(error), hash = null)
        }
        groups.recordServerSync(group.groupId, server, outcome.result, outcome.hash)
        outcome.result
    }

    /** A sync's outcome and, after a success, the hash of the document both sides now hold. */
    private class Done(val result: SyncServerResult, val hash: String?)

    /** Null when the group or server changed while this ran: what came back isn't ours to apply. */
    @Suppress("ThrowsCount", "CyclomaticComplexMethod")
    private suspend fun run(group: GroupCredentials, server: String): Done? {
        val token = SyncServerToken.derive(group)
        val url = "$server/v1/groups/${group.groupId}/document"
        var changes = SyncChanges.NONE
        // Both sides held this document at the last sync; if this device still does, ask the server
        // for its document only if it has changed since (304 otherwise: nothing to do at all).
        val checkpoint = groups.serverCheckpoint(group.groupId, server)
            ?.takeIf { (_, hash) -> fingerprint(sync.export()) == hash }
        repeat(MAX_ATTEMPTS) { attempt ->
            val known = checkpoint?.takeIf { attempt == 0 }
            val fetched = fetch(url, token, group, ifNoneMatch = known?.first)
            if (!stillFor(group, server)) return null
            if (fetched === Fetched.NotModified) {
                val (rev, hash) = known ?: throw Failure(SyncServerProblem.SERVER, NO_REV)
                return Done(SyncServerResult.Synced(changes, clock.now(), rev, uploaded = false), hash)
            }
            val remote = fetched as? Fetched.Found
            val merged = if (remote == null) {
                sync.export()
            } else {
                apply(remote.document).also { changes += it.changes }.document
            }
            if (remote != null && SyncMerge.normalize(remote.document) == merged) {
                val synced = SyncServerResult.Synced(changes, clock.now(), remote.rev, uploaded = false)
                return Done(synced, fingerprint(merged))
            }
            // Never upload what every device would refuse to open: it would stop the whole group.
            SyncValidation.problem(merged)?.let { throw Failure(SyncServerProblem.INVALID_DATA, "$LOCAL_FIX\n$it") }
            val body = SyncFileCodec.encodeFile(merged, group, createdAt = clock.now(), cipher, withGroupName = false)
            if (body.size > MAX_UPLOAD_BYTES) throw Failure(SyncServerProblem.TOO_BIG, TOO_BIG)
            when (val stored = store(url, token, body, remote?.rev)) {
                is Stored.Saved -> {
                    val synced = SyncServerResult.Synced(changes, clock.now(), stored.rev, uploaded = true)
                    return Done(synced, fingerprint(merged))
                }

                Stored.Conflict -> Unit // Another device uploaded meanwhile: fetch its document and merge again.
            }
        }
        throw Failure(SyncServerProblem.BUSY, BUSY)
    }

    private suspend fun stillFor(group: GroupCredentials, server: String): Boolean {
        val now = groups.state.first()
        return now.group?.groupId == group.groupId && now.server.activeUrl == server
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun apply(remote: SyncDocument): SyncApplyResult = try {
        sync.apply(remote)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // A storage failure rolls the whole merge back.
        throw Failure(SyncServerProblem.SAVE_FAILED, SAVE_FAILED, e)
    }

    private sealed interface Fetched {
        class Found(val document: SyncDocument, val rev: Long) : Fetched

        data object Missing : Fetched

        /** 304: still the rev named in `If-None-Match`. */
        data object NotModified : Fetched
    }

    private sealed interface Stored {
        class Saved(val rev: Long?) : Stored

        data object Conflict : Stored
    }

    /** Step 1: the server's document; with [ifNoneMatch], 304 when it is still that rev. */
    @Suppress("ThrowsCount")
    private suspend fun fetch(url: String, token: String, group: GroupCredentials, ifNoneMatch: Long?): Fetched {
        val headers = buildMap {
            put(AUTHORIZATION, "Bearer $token")
            ifNoneMatch?.let { put("If-None-Match", "\"$it\"") }
        }
        return call(RelayRequest("GET", url, headers)) { response ->
            when (response.code) {
                HTTP_OK -> {
                    val rev = rev(response) ?: throw Failure(SyncServerProblem.SERVER, NO_REV)
                    val bytes = response.body(MAX_DOWNLOAD_BYTES)
                        ?: throw Failure(SyncServerProblem.BAD_DOCUMENT, DOWNLOAD_TOO_BIG)
                    Fetched.Found(open(bytes, group), rev)
                }

                HTTP_NOT_MODIFIED -> Fetched.NotModified

                HTTP_NOT_FOUND -> Fetched.Missing

                else -> throw refusal(response)
            }
        }
    }

    /** Step 3: the sealed merge, stored only if the server still holds [rev] (null = nothing). */
    private suspend fun store(url: String, token: String, body: ByteArray, rev: Long?): Stored {
        val headers = buildMap {
            put(AUTHORIZATION, "Bearer $token")
            if (rev == null) put("If-None-Match", "*") else put("If-Match", "\"$rev\"")
        }
        val request = RelayRequest("PUT", url, headers, body, SyncFileCodec.FILE_MIME_TYPE)
        return call(request) { response ->
            when (response.code) {
                HTTP_OK, HTTP_CREATED -> Stored.Saved(rev(response))

                HTTP_PRECONDITION_FAILED -> Stored.Conflict

                // A PUT is never "not found" on a sync server: something else answers there.
                HTTP_NOT_FOUND -> throw Failure(SyncServerProblem.WRONG_ADDRESS, NOT_A_SERVER)

                else -> throw refusal(response)
            }
        }
    }

    private suspend fun <T> call(request: RelayRequest, read: suspend (RelayResponse) -> T): T = try {
        http.exchange(request, read)
    } catch (e: RelayNetworkException) {
        throw networkFailure(e)
    }

    /** What a status the algorithm doesn't expect means, in words; 429/503 carry their `Retry-After`. */
    @Suppress("CyclomaticComplexMethod")
    private suspend fun refusal(response: RelayResponse): Failure {
        val code = response.code
        val wait = retryAfter(response)
        return when {
            code == HTTP_UNAUTHORIZED -> Failure(SyncServerProblem.WRONG_KEY, WRONG_KEY)

            code == HTTP_TOO_BIG -> Failure(SyncServerProblem.TOO_BIG, TOO_BIG)

            code == HTTP_TOO_MANY -> Failure(SyncServerProblem.RATE_LIMITED, RATE_LIMITED, retryAfterSeconds = wait)

            code == HTTP_UNAVAILABLE && errorCode(response) == RELAY_FULL ->
                Failure(SyncServerProblem.SERVER_FULL, SERVER_FULL, retryAfterSeconds = wait)

            code in REDIRECTS -> Failure(SyncServerProblem.WRONG_ADDRESS, REDIRECTED)

            code >= HTTP_SERVER_ERROR -> Failure(
                SyncServerProblem.SERVER,
                "The sync server had a problem (error $code).",
                retryAfterSeconds = wait,
            )

            else -> Failure(SyncServerProblem.WRONG_ADDRESS, "$NOT_A_SERVER (error $code)")
        }
    }

    /** `Retry-After` in seconds (the relay sends seconds; an HTTP date counts from now). */
    private fun retryAfter(response: RelayResponse): Long? {
        val value = response.header("Retry-After")?.trim() ?: return null
        value.toLongOrNull()?.let { return it.coerceIn(0, MAX_RETRY_AFTER_SECONDS) }
        val at = runCatching {
            DateTimeComponents.Formats.RFC_1123.parse(value).toInstantUsingOffset().toEpochMilliseconds()
        }.getOrNull() ?: return null
        return ((at - clock.now()) / MILLIS).coerceIn(0, MAX_RETRY_AFTER_SECONDS)
    }

    /** The relay's JSON error code (`{"error":"relay_full"}`), read from a small body only. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun errorCode(response: RelayResponse): String? {
        val bytes = try {
            response.body(MAX_ERROR_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return null
        return ERROR_CODE.find(bytes.decodeToString())?.groupValues?.get(1)
    }

    /** `ETag: "<rev>"` → rev; null when missing or not a positive number. */
    private fun rev(response: RelayResponse): Long? = response.header("ETag")
        ?.removePrefix("W/")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.toLongOrNull()
        ?.takeIf { it > 0 }

    private suspend fun open(bytes: ByteArray, group: GroupCredentials): SyncDocument = try {
        SyncFileCodec.decodeFile(bytes, group, cipher)
    } catch (e: SyncFileException) {
        throw when {
            e.version == VersionGap.FILE_NEWER -> Failure(SyncServerProblem.VERSION, SERVER_NEWER, e)

            e.version == VersionGap.FILE_OLDER -> Failure(SyncServerProblem.VERSION, SERVER_OLDER, e)

            e.problem == SyncFileProblem.OTHER_GROUP -> Failure(SyncServerProblem.BAD_DOCUMENT, OTHER_GROUP, e)

            e.problem == SyncFileProblem.INVALID -> Failure(
                SyncServerProblem.BAD_DOCUMENT,
                "$UNUSABLE\n${e.message}",
                e,
            )

            else -> Failure(SyncServerProblem.BAD_DOCUMENT, UNREADABLE, e)
        }
    }

    private class Failure(
        val problem: SyncServerProblem,
        override val message: String,
        cause: Throwable? = null,
        val retryAfterSeconds: Long? = null,
    ) : Exception(message, cause)

    private fun networkFailure(e: RelayNetworkException): Failure = when (e.failure) {
        // Release builds refuse plain HTTP (network_security_config).
        RelayNetworkFailure.HTTPS_ONLY -> Failure(SyncServerProblem.WRONG_ADDRESS, HTTPS_ONLY, e)

        RelayNetworkFailure.CERTIFICATE -> Failure(SyncServerProblem.WRONG_ADDRESS, CERTIFICATE, e)

        RelayNetworkFailure.TIMEOUT -> Failure(SyncServerProblem.TIMEOUT, TIMEOUT, e)

        RelayNetworkFailure.UNREACHABLE -> Failure(SyncServerProblem.UNREACHABLE, UNREACHABLE, e)
    }

    private operator fun SyncChanges.plus(other: SyncChanges) = SyncChanges(
        playersAdded = playersAdded + other.playersAdded,
        playersUpdated = playersUpdated + other.playersUpdated,
        matchesAdded = matchesAdded + other.matchesAdded,
        matchesUpdated = matchesUpdated + other.matchesUpdated,
        matchesRemoved = matchesRemoved + other.matchesRemoved,
        draftsAdded = draftsAdded + other.draftsAdded,
        draftsUpdated = draftsUpdated + other.draftsUpdated,
        draftsRemoved = draftsRemoved + other.draftsRemoved,
        triviaRoundsAdded = triviaRoundsAdded + other.triviaRoundsAdded,
        triviaRoundsUpdated = triviaRoundsUpdated + other.triviaRoundsUpdated,
        triviaRoundsRemoved = triviaRoundsRemoved + other.triviaRoundsRemoved,
        playersMerged = playersMerged + other.playersMerged,
        ratingConstantsChanged = ratingConstantsChanged || other.ratingConstantsChanged,
    )

    companion object {
        /** Tries in one sync when other devices keep uploading in between (412). */
        const val MAX_ATTEMPTS = 3

        /** The server refuses more than 8 MiB (SYNC-SERVER.md); don't send what it would refuse. */
        const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024

        /** SHA-256 of the canonical document's JSON, lowercase hex: equal data, equal hash. */
        fun fingerprint(doc: SyncDocument): String = SyncHash.hex(
            SyncHash.sha256(
                SyncJson.encodeToString(SyncDocument.serializer(), SyncMerge.normalize(doc)).encodeToByteArray(),
            ),
        )

        private const val MAX_DOWNLOAD_BYTES = 16L * 1024 * 1024
        private const val AUTHORIZATION = "Authorization"
        private const val HTTP_OK = 200
        private const val HTTP_CREATED = 201
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_PRECONDITION_FAILED = 412
        private const val HTTP_TOO_BIG = 413
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_UNAVAILABLE = 503
        private const val RELAY_FULL = "relay_full"
        private val ERROR_CODE = Regex(""""error"\s*:\s*"([a-z_]+)"""")
        private const val MAX_ERROR_BYTES = 4L * 1024
        private const val MAX_RETRY_AFTER_SECONDS = 24L * 60 * 60
        private const val MILLIS = 1_000L
        private const val HTTP_SERVER_ERROR = 500
        private val REDIRECTS = 300..399

        private const val LOCAL_FIX =
            "This phone's data needs a fix before it can go to the sync server. Open the match below and correct it."
        private const val TOO_BIG = "The group's data is too big for the sync server (over 8 MB)."
        private const val WRONG_KEY =
            "The sync server holds this group under another key. Scan a new invite from a phone that syncs, " +
                "or check the server address."
        private const val SERVER_NEWER =
            "The sync server has data from a newer version of the app. Update the app on this phone."
        private const val SERVER_OLDER =
            "The sync server has data from an older version of the app. Update the app on the other phones, " +
                "then sync them."
        private const val OTHER_GROUP = "The sync server answered with another group's data. Check the server address."
        private const val UNUSABLE = "The group's data on the sync server can't be used:"
        private const val UNREADABLE = "The group's data on the sync server can't be opened. Sync from another phone."
        private const val DOWNLOAD_TOO_BIG = "The group's data on the sync server is too big for this phone."
        private const val NO_REV = "The sync server's answer was incomplete. Try again later."
        private const val BUSY =
            "Other phones kept syncing at the same moment. Nothing was lost; this phone tries again later."
        private const val SAVE_FAILED = "The group's data couldn't be saved on this phone. Nothing changed."
        private const val NOT_A_SERVER = "There's no sync server at this address. Check it."
        private const val RATE_LIMITED = "The sync server is busy. This phone tries again in a moment."
        private const val SERVER_FULL =
            "The sync server is full: it takes no new groups right now. Ask whoever runs it to make room; " +
                "this phone tries again later. Face-to-face sync still works."
        private const val REDIRECTED = "The sync server address points somewhere else. Use the server's own address."
        private const val HTTPS_ONLY = "This app only talks to a sync server over HTTPS. Use its https address."
        private const val CERTIFICATE = "The sync server's certificate isn't trusted. Check the address."
        private const val TIMEOUT = "The sync server took too long to answer."
        private const val UNREACHABLE = "Can't reach the sync server. Check the internet connection."
    }
}
