package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.SyncServerToken
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.domain.sync.VersionGap
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * [SyncServerRepository] over OkHttp, following the "Phone algorithm" in docs/SYNC-SERVER.md:
 *
 * 1. `GET /v1/groups/{groupId}/document`: `404` = no document yet; `200` = the sealed document
 *    and its rev (`ETag`), opened with [SyncCodec.decodeFile] (another document version stops).
 * 2. Merge it in through [SyncRepository.apply], the path an imported sync file takes (it re-reads
 *    this phone's data and writes the merge in one transaction): the result is
 *    `SyncMerge.merge(local, remote)`, or `SyncMerge.normalize(local)` when there is none.
 * 3. Only when the server has no document or its content differs from the merge (compared as
 *    canonical documents, so a fresh envelope salt or `createdAt` never counts): `PUT` the merge,
 *    sealed, with `If-Match: "<rev>"` (or `If-None-Match: *`). `412` = another phone got there
 *    first: back to 1, at most [MAX_ATTEMPTS] tries in all.
 * 4. The outcome (time, rev, or the error in words) goes to `group_sync` for the Group screen.
 *
 * Neither the token nor any body is ever logged: the client has no logging interceptor, cache or
 * cookie jar, and errors carry only words for people.
 */
@Singleton
internal class OkHttpSyncServerRepository @Inject constructor(
    private val groups: DataStoreGroupRepository,
    private val sync: SyncRepository,
    database: GoaDatabase,
    eloConfig: EloConfigRepository,
    private val clock: EditClock,
    @SyncServerHttp private val client: OkHttpClient,
) : SyncServerRepository {
    private val mutex = Mutex()

    override val localChanges: Flow<Unit> = merge(
        database.invalidationTracker.createFlow(*SYNCED_TABLES, emitInitialState = false).map { },
        eloConfig.config.drop(1).map { },
    )

    override suspend fun sync(): SyncServerResult = mutex.withLock {
        val state = groups.state.first()
        val group = state.group
        val server = state.server.activeUrl
        if (group == null || server == null) return SyncServerResult.NotConfigured
        val outcome = try {
            run(group, server) ?: return SyncServerResult.NotConfigured
        } catch (e: Failure) {
            SyncServerResult.Failed(SyncServerError(e.problem, e.message, clock.now()))
        }
        groups.recordServerSync(group.groupId, server, outcome)
        outcome
    }

    /** Null when the group or server changed while this ran: what came back isn't ours to apply. */
    @Suppress("ThrowsCount")
    private suspend fun run(group: GroupCredentials, server: String): SyncServerResult.Synced? {
        val token = SyncServerToken.derive(group)
        val url = "$server/v1/groups/${group.groupId}/document"
        var changes = SyncChanges.NONE
        repeat(MAX_ATTEMPTS) {
            val remote = fetch(url, token, group)
            if (!stillFor(group, server)) return null
            val merged = if (remote == null) {
                sync.export()
            } else {
                apply(remote.document).also { changes += it.changes }.document
            }
            if (remote != null && SyncMerge.normalize(remote.document) == merged) {
                return SyncServerResult.Synced(changes, clock.now(), remote.rev, uploaded = false)
            }
            // Never upload what every phone would refuse to open: it would stop the whole group.
            SyncValidation.problem(merged)?.let { throw Failure(SyncServerProblem.INVALID_DATA, "$LOCAL_FIX\n$it") }
            val body = SyncCodec.encodeFile(merged, group, createdAt = clock.now())
            if (body.size > MAX_UPLOAD_BYTES) throw Failure(SyncServerProblem.TOO_BIG, TOO_BIG)
            when (val stored = store(url, token, body, remote?.rev)) {
                is Stored.Saved -> return SyncServerResult.Synced(changes, clock.now(), stored.rev, uploaded = true)
                Stored.Conflict -> Unit // Another phone uploaded meanwhile: fetch its document and merge again.
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
    } catch (e: Exception) {
        // A database failure rolls the whole merge back.
        throw Failure(SyncServerProblem.SAVE_FAILED, SAVE_FAILED, e)
    }

    private class Remote(val document: SyncDocument, val rev: Long)

    private sealed interface Stored {
        class Saved(val rev: Long?) : Stored

        data object Conflict : Stored
    }

    /** Step 1: the server's document, or null when it has none. */
    @Suppress("ThrowsCount")
    private suspend fun fetch(url: String, token: String, group: GroupCredentials): Remote? {
        val request = Request.Builder().url(url).header(AUTHORIZATION, "Bearer $token").get().build()
        return call(request) { response ->
            when (response.code) {
                HTTP_OK -> {
                    val rev = rev(response) ?: throw Failure(SyncServerProblem.SERVER, NO_REV)
                    val bytes = limited(response, MAX_DOWNLOAD_BYTES)
                        ?: throw Failure(SyncServerProblem.BAD_DOCUMENT, DOWNLOAD_TOO_BIG)
                    Remote(open(bytes, group), rev)
                }

                HTTP_NOT_FOUND -> null

                else -> throw refusal(response.code)
            }
        }
    }

    /** Step 3: the sealed merge, stored only if the server still holds [rev] (null = nothing). */
    private suspend fun store(url: String, token: String, body: ByteArray, rev: Long?): Stored {
        val request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, "Bearer $token")
            .apply { if (rev == null) header("If-None-Match", "*") else header("If-Match", "\"$rev\"") }
            .put(body.toRequestBody(SyncCodec.FILE_MIME_TYPE.toMediaType()))
            .build()
        return call(request) { response ->
            when (response.code) {
                HTTP_OK, HTTP_CREATED -> Stored.Saved(rev(response))
                HTTP_PRECONDITION_FAILED -> Stored.Conflict
                // A PUT is never "not found" on a sync server: something else answers there.
                HTTP_NOT_FOUND -> throw Failure(SyncServerProblem.WRONG_ADDRESS, NOT_A_SERVER)
                else -> throw refusal(response.code)
            }
        }
    }

    /** Sends [request]; [read] runs on the IO dispatcher with the response, which is always closed. */
    private suspend fun <T> call(request: Request, read: (Response) -> T): T {
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw networkFailure(e)
        }
        return response.use {
            withContext(Dispatchers.IO) {
                try {
                    read(it)
                } catch (e: IOException) {
                    throw networkFailure(e)
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun refusal(code: Int): Failure = when {
        code == HTTP_UNAUTHORIZED -> Failure(SyncServerProblem.WRONG_KEY, WRONG_KEY)
        code == HTTP_TOO_BIG -> Failure(SyncServerProblem.TOO_BIG, TOO_BIG)
        code == HTTP_TOO_MANY -> Failure(SyncServerProblem.RATE_LIMITED, RATE_LIMITED)
        code in REDIRECTS -> Failure(SyncServerProblem.WRONG_ADDRESS, REDIRECTED)
        code >= HTTP_SERVER_ERROR -> Failure(SyncServerProblem.SERVER, "The sync server had a problem (error $code).")
        else -> Failure(SyncServerProblem.WRONG_ADDRESS, "$NOT_A_SERVER (error $code)")
    }

    /** `ETag: "<rev>"` → rev; null when missing or not a positive number. */
    private fun rev(response: Response): Long? = response.header("ETag")
        ?.removePrefix("W/")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.toLongOrNull()
        ?.takeIf { it > 0 }

    /** The response body, or null when it is longer than [max] bytes. */
    private fun limited(response: Response, max: Long): ByteArray? {
        val body = response.body
        if (body.contentLength() > max) return null
        val buffer = Buffer()
        body.source().use { source ->
            while (source.read(buffer, READ_CHUNK) != -1L) {
                if (buffer.size > max) return null
            }
        }
        return buffer.readByteArray()
    }

    private fun open(bytes: ByteArray, group: GroupCredentials): SyncDocument = try {
        SyncCodec.decodeFile(bytes, group)
    } catch (e: SyncFileException) {
        throw when {
            e.version == VersionGap.FILE_NEWER -> Failure(SyncServerProblem.VERSION, SERVER_NEWER, e)
            e.version == VersionGap.FILE_OLDER -> Failure(SyncServerProblem.VERSION, SERVER_OLDER, e)
            e.problem == SyncFileProblem.OTHER_GROUP -> Failure(SyncServerProblem.BAD_DOCUMENT, OTHER_GROUP, e)
            e.problem == SyncFileProblem.INVALID -> Failure(SyncServerProblem.BAD_DOCUMENT, "$UNUSABLE\n${e.message}", e)
            else -> Failure(SyncServerProblem.BAD_DOCUMENT, UNREADABLE, e)
        }
    }

    private class Failure(val problem: SyncServerProblem, override val message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private fun networkFailure(e: IOException): Failure = when (e) {
        // Release builds refuse plain HTTP (network_security_config).
        is UnknownServiceException -> Failure(SyncServerProblem.WRONG_ADDRESS, HTTPS_ONLY, e)
        is SSLException -> Failure(SyncServerProblem.WRONG_ADDRESS, CERTIFICATE, e)
        is InterruptedIOException -> Failure(SyncServerProblem.TIMEOUT, TIMEOUT, e)
        else -> Failure(SyncServerProblem.UNREACHABLE, UNREACHABLE, e)
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
        /** Tries in one sync when other phones keep uploading in between (412). */
        const val MAX_ATTEMPTS = 3

        /** The server refuses more than 8 MiB (SYNC-SERVER.md); don't send what it would refuse. */
        const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024

        /** Room tables whose rows are in the [SyncDocument]. */
        val SYNCED_TABLES = arrayOf(
            "players",
            "matches",
            "participants",
            "draft_records",
            "draft_steps",
            "trivia_rounds",
            "deletions",
            "player_merges",
        )

        private const val MAX_DOWNLOAD_BYTES = 16L * 1024 * 1024
        private const val READ_CHUNK = 64L * 1024
        private const val AUTHORIZATION = "Authorization"
        private const val HTTP_OK = 200
        private const val HTTP_CREATED = 201
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_PRECONDITION_FAILED = 412
        private const val HTTP_TOO_BIG = 413
        private const val HTTP_TOO_MANY = 429
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
        private const val REDIRECTED = "The sync server address points somewhere else. Use the server's own address."
        private const val HTTPS_ONLY = "This app only talks to a sync server over HTTPS. Use its https address."
        private const val CERTIFICATE = "The sync server's certificate isn't trusted. Check the address."
        private const val TIMEOUT = "The sync server took too long to answer."
        private const val UNREACHABLE = "Can't reach the sync server. Check the internet connection."
    }
}
