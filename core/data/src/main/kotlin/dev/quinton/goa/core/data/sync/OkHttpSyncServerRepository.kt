package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.domain.sync.JvmSyncCipher
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * [SyncServerRepository] over OkHttp: the shared relay algorithm ([RelaySyncRepository], the
 * "Phone algorithm" in docs/SYNC-SERVER.md, also run by the web app, ADR-043), sealed with
 * [JvmSyncCipher]. [localChanges] are Room's invalidations of the synced tables and changes to the
 * rating constants.
 *
 * Neither the token nor any body is ever logged: the client has no logging interceptor, cache or
 * cookie jar, and errors carry only words for people.
 */
@Singleton
internal class OkHttpSyncServerRepository @Inject constructor(
    groups: DataStoreGroupRepository,
    sync: SyncRepository,
    database: GoaDatabase,
    eloConfig: EloConfigRepository,
    clock: EditClock,
    @SyncServerHttp client: OkHttpClient,
) : SyncServerRepository by RelaySyncRepository(
    groups = groups,
    sync = sync,
    localChanges = merge(
        database.invalidationTracker.createFlow(*SYNCED_TABLES, emitInitialState = false).map { },
        eloConfig.config.drop(1).map { },
    ),
    clock = clock,
    http = OkHttpRelayHttp(client),
    cipher = JvmSyncCipher,
) {
    companion object {
        /** Tries in one sync when other phones keep uploading in between (412). */
        const val MAX_ATTEMPTS = RelaySyncRepository.MAX_ATTEMPTS

        /** Room tables whose rows are in the [dev.quinton.goa.core.domain.sync.SyncDocument]. */
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
    }
}

/** [RelayHttp] over OkHttp: the response is read on the IO dispatcher and always closed. */
internal class OkHttpRelayHttp(private val client: OkHttpClient) : RelayHttp {
    override suspend fun <T> exchange(request: RelayRequest, read: suspend (RelayResponse) -> T): T {
        val body = request.body?.toRequestBody(request.contentType?.toMediaType())
        val call = Request.Builder()
            .url(request.url)
            .apply { request.headers.forEach { (name, value) -> header(name, value) } }
            .method(request.method, body)
            .build()
        val response = try {
            client.newCall(call).await()
        } catch (e: IOException) {
            throw networkFailure(e)
        }
        return response.use { withContext(Dispatchers.IO) { read(Answer(it)) } }
    }

    private class Answer(private val response: Response) : RelayResponse {
        override val code: Int get() = response.code

        override fun header(name: String): String? = response.header(name)

        override suspend fun body(max: Long): ByteArray? = try {
            limited(max)
        } catch (e: IOException) {
            throw networkFailure(e)
        }

        private fun limited(max: Long): ByteArray? {
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
    }

    private companion object {
        const val READ_CHUNK = 64L * 1024

        fun networkFailure(e: IOException): RelayNetworkException = when (e) {
            // Release builds refuse plain HTTP (network_security_config).
            is UnknownServiceException -> RelayNetworkException(RelayNetworkFailure.HTTPS_ONLY, e)

            is SSLException -> RelayNetworkException(RelayNetworkFailure.CERTIFICATE, e)

            is InterruptedIOException -> RelayNetworkException(RelayNetworkFailure.TIMEOUT, e)

            else -> RelayNetworkException(RelayNetworkFailure.UNREACHABLE, e)
        }
    }
}
