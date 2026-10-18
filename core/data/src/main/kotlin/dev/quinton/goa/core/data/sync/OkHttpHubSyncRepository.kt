package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.HubError
import dev.quinton.goa.core.data.HubProblem
import dev.quinton.goa.core.data.HubSyncRepository
import dev.quinton.goa.core.data.HubSyncResult
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.HubToken
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncValidation
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
 * [HubSyncRepository] over OkHttp (ADR-030 §4, §5): `POST {hub}/api/v1/sync` with the bearer
 * token derived from the group key and this phone's sync file as the body; a 200 carries the
 * merged document sealed the same way, applied through [SyncRepository.importFile]'s path. No
 * body is ever logged; the client has no logging interceptor, cache or cookie jar.
 */
@Singleton
internal class OkHttpHubSyncRepository @Inject constructor(
    private val groups: GroupRepository,
    private val store: GroupSyncStore,
    private val sync: SyncRepository,
    database: GoaDatabase,
    eloConfig: EloConfigRepository,
    private val clock: EditClock,
    @HubHttp private val client: OkHttpClient,
) : HubSyncRepository {
    private val mutex = Mutex()

    override val localChanges: Flow<Unit> = merge(
        database.invalidationTracker.createFlow(*SYNCED_TABLES, emitInitialState = false).map { },
        eloConfig.config.drop(1).map { },
    )

    override suspend fun sync(): HubSyncResult = mutex.withLock {
        val state = groups.state.first()
        val group = state.group
        val hub = state.hubUrl
        if (group == null || hub == null) return HubSyncResult.NotConfigured
        val outcome = try {
            roundTrip(group, hub) ?: return HubSyncResult.NotConfigured
        } catch (e: HubFailure) {
            HubSyncResult.Failed(HubError(e.problem, e.message, clock.now()))
        }
        record(group, hub, outcome)
        outcome
    }

    /** Null when the group or hub changed while the request ran: the reply is no longer ours to apply. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun roundTrip(group: GroupCredentials, hub: String): HubSyncResult.Synced? {
        val local = sync.export()
        // Never send what this phone's own checks would refuse from another phone (as Nearby sync).
        SyncValidation.problem(local)?.let { throw HubFailure(HubProblem.INVALID_DATA, "$LOCAL_FIX\n$it") }
        val body = SyncCodec.encodeFile(local, group, createdAt = clock.now())
        if (body.size > MAX_UPLOAD_BYTES) throw HubFailure(HubProblem.TOO_BIG, TOO_BIG)
        val reply = post(hub, group, body)
        val merged = try {
            SyncCodec.decodeFile(reply, group)
        } catch (e: SyncFileException) {
            throw HubFailure(replyProblem(e), replyMessage(e))
        }
        val now = groups.state.first()
        if (now.group?.groupId != group.groupId || now.hubUrl != hub) return null
        val applied = try {
            sync.apply(merged)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A database failure rolls the whole merge back.
            throw HubFailure(HubProblem.BAD_REPLY, "The hub's data couldn't be saved on this phone. Nothing changed.", e)
        }
        return HubSyncResult.Synced(applied.changes, clock.now())
    }

    private suspend fun post(hub: String, group: GroupCredentials, body: ByteArray): ByteArray {
        val request = Request.Builder()
            .url("$hub$SYNC_PATH")
            .header("Authorization", "Bearer ${HubToken.derive(group)}")
            .post(body.toRequestBody(SyncCodec.FILE_MIME_TYPE.toMediaType()))
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw networkFailure(e)
        }
        return response.use { withContext(Dispatchers.IO) { read(it) } }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun read(response: Response): ByteArray {
        val code = response.code
        return when {
            code == HTTP_OK -> try {
                limited(response, MAX_REPLY_BYTES)
                    ?: throw HubFailure(HubProblem.BAD_REPLY, "The hub's answer is too big for this phone.")
            } catch (e: IOException) {
                throw networkFailure(e)
            }

            code == HTTP_UNAUTHORIZED -> throw HubFailure(HubProblem.WRONG_KEY, WRONG_KEY)

            code == HTTP_CONFLICT -> throw HubFailure(HubProblem.VERSION, text(response) ?: VERSION)

            code == HTTP_TOO_BIG -> throw HubFailure(HubProblem.TOO_BIG, TOO_BIG)

            code == HTTP_UNPROCESSABLE -> throw HubFailure(
                HubProblem.INVALID_DATA,
                "The hub refused this phone's data." + (text(response)?.let { "\n$it" } ?: ""),
            )

            code == HTTP_NOT_FOUND -> throw HubFailure(HubProblem.WRONG_ADDRESS, NO_HUB)

            code in REDIRECTS -> throw HubFailure(HubProblem.WRONG_ADDRESS, REDIRECTED)

            else -> throw HubFailure(HubProblem.SERVER, "The hub had a problem (error $code).")
        }
    }

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

    /** The hub's own words for a refusal (plain text), trimmed to one short paragraph. */
    private fun text(response: Response): String? = runCatching { limited(response, MAX_TEXT_BYTES) }
        .getOrNull()
        ?.toString(Charsets.UTF_8)
        ?.trim()
        ?.take(MAX_TEXT_CHARS)
        ?.takeIf { it.isNotEmpty() }

    private suspend fun record(group: GroupCredentials, hub: String, outcome: HubSyncResult) = store.edit { prefs ->
        // Only the hub and group this sync was for: the user may have changed them meanwhile.
        if (prefs.groupId != group.groupId || prefs.hubUrl != hub) return@edit prefs
        when (outcome) {
            is HubSyncResult.Synced -> prefs.copy(
                hubSyncedAt = outcome.at,
                hubError = null,
                hubErrorKind = null,
                hubErrorAt = null,
            )

            is HubSyncResult.Failed -> prefs.copy(
                hubError = outcome.error.message,
                hubErrorKind = outcome.error.problem.name,
                hubErrorAt = outcome.error.at,
            )

            HubSyncResult.NotConfigured -> prefs
        }
    }

    private class HubFailure(val problem: HubProblem, override val message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private fun networkFailure(e: IOException): HubFailure = when (e) {
        // Release builds refuse plain HTTP (network_security_config).
        is UnknownServiceException -> HubFailure(HubProblem.WRONG_ADDRESS, HTTPS_ONLY, e)
        is SSLException -> HubFailure(HubProblem.WRONG_ADDRESS, CERTIFICATE, e)
        is InterruptedIOException -> HubFailure(HubProblem.TIMEOUT, TIMEOUT, e)
        else -> HubFailure(HubProblem.UNREACHABLE, UNREACHABLE, e)
    }

    private fun replyProblem(e: SyncFileException) = when (e.problem) {
        SyncFileProblem.OTHER_GROUP, SyncFileProblem.CANT_DECRYPT -> HubProblem.WRONG_KEY
        SyncFileProblem.INVALID -> if (" version " in e.message.orEmpty()) HubProblem.VERSION else HubProblem.BAD_REPLY
        SyncFileProblem.NOT_A_SYNC_FILE, SyncFileProblem.NOT_IN_A_GROUP -> HubProblem.BAD_REPLY
    }

    private fun replyMessage(e: SyncFileException) = when (e.problem) {
        SyncFileProblem.OTHER_GROUP -> "This hub syncs another group. Check the hub address."
        SyncFileProblem.CANT_DECRYPT -> WRONG_KEY
        SyncFileProblem.NOT_A_SYNC_FILE, SyncFileProblem.NOT_IN_A_GROUP -> NO_HUB
        SyncFileProblem.INVALID -> when {
            "newer version" in e.message.orEmpty() -> "The hub runs a newer version. Update the app."
            "older version" in e.message.orEmpty() -> "The hub runs an older version. Ask the hub's owner to update it."
            else -> "The hub's data couldn't be used:\n${e.message}"
        }
    }

    companion object {
        const val SYNC_PATH = "/api/v1/sync"

        /** The hub refuses more than 8 MB (ADR-030 §4); don't send what it would refuse. */
        const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024

        /** The merged reply can be bigger than this phone's own data, never wildly so. */
        const val MAX_REPLY_BYTES = 16L * 1024 * 1024

        /** Room tables whose rows are in the [dev.quinton.goa.core.domain.sync.SyncDocument]. */
        val SYNCED_TABLES = arrayOf(
            "players",
            "matches",
            "participants",
            "draft_records",
            "draft_steps",
            "deletions",
            "player_merges",
        )

        private const val MAX_TEXT_BYTES = 4L * 1024
        private const val MAX_TEXT_CHARS = 300
        private const val READ_CHUNK = 64L * 1024
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_TOO_BIG = 413
        private const val HTTP_UNPROCESSABLE = 422
        private val REDIRECTS = 300..399

        private const val LOCAL_FIX =
            "This phone's data needs a fix first. Open each match listed and correct it; the hub syncs after that."
        private const val TOO_BIG = "This phone's data is too big for the hub (over 8 MB)."
        private const val WRONG_KEY =
            "This phone's group key isn't accepted by the hub. Check the hub address, or scan a new invite."
        private const val VERSION =
            "The hub and this app have different versions. Update the app, or ask the hub's owner to update it."
        private const val NO_HUB = "There's no hub at this address. Check it."
        private const val REDIRECTED = "The hub address points somewhere else. Use the hub's own https address."
        private const val HTTPS_ONLY = "This app only talks to a hub over HTTPS. Use the hub's https address."
        private const val CERTIFICATE = "The hub's certificate isn't trusted. Check the hub address."
        private const val TIMEOUT = "The hub took too long to answer."
        private const val UNREACHABLE = "Can't reach the hub. Check the internet connection and the hub address."
    }
}
