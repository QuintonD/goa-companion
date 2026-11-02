package dev.quinton.goa.core.data.online

import dev.quinton.goa.core.data.sync.await
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.LinkAccount
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.Rename
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.feature.board.play.online.OnlineGateway
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.onlineFailure
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * [OnlineGateway] over OkHttp. Neither the token nor any body is ever logged: the client has no logging
 * interceptor, and a failure carries only words for people.
 */
class OkHttpOnlineGateway @Inject constructor(
    @OnlineRestHttp private val client: OkHttpClient,
    private val hosts: GameHosts,
    private val identities: OnlineIdentityStore,
) : OnlineGateway {
    override suspend fun createAccount(name: String): OnlineResult<AccountCreated> = post(
        OnlineRoutes.ACCOUNTS,
        body(CreateAccount.serializer(), CreateAccount(name)),
        AccountCreated.serializer(),
        signedIn = false,
    )

    override suspend fun link(recoveryCode: String): OnlineResult<AccountCreated> = post(
        OnlineRoutes.ACCOUNTS_LINK,
        body(LinkAccount.serializer(), LinkAccount(recoveryCode)),
        AccountCreated.serializer(),
        signedIn = false,
    )

    override suspend fun me(): OnlineResult<Me> = exchange("GET", OnlineRoutes.ME, null) { read(it, Me.serializer()) }

    override suspend fun rename(name: String): OnlineResult<Me> =
        exchange("PATCH", OnlineRoutes.ME, body(Rename.serializer(), Rename(name))) { read(it, Me.serializer()) }

    override suspend fun deleteMe(): OnlineResult<Unit> =
        exchange("DELETE", OnlineRoutes.ME, null) { OnlineResult.Ok(Unit) }

    override suspend fun createRoom(room: CreateRoom): OnlineResult<RoomCreated> =
        post(OnlineRoutes.ROOMS, body(CreateRoom.serializer(), room), RoomCreated.serializer())

    override suspend fun join(code: String): OnlineResult<RoomView> =
        post(OnlineRoutes.join(code), EMPTY_BODY, RoomView.serializer())

    override suspend fun leave(code: String): OnlineResult<RoomView> =
        post(OnlineRoutes.leave(code), EMPTY_BODY, RoomView.serializer())

    override suspend fun myGames(): OnlineResult<MyGames> =
        exchange("GET", OnlineRoutes.MY_GAMES, null) { read(it, MyGames.serializer()) }

    private suspend fun <T> post(
        route: String,
        body: RequestBody,
        answer: DeserializationStrategy<T>,
        signedIn: Boolean = true,
    ): OnlineResult<T> = exchange("POST", route, body, signedIn) { read(it, answer) }

    private fun <T> body(serializer: SerializationStrategy<T>, value: T): RequestBody =
        OnlineProtocol.json.encodeToString(serializer, value).toRequestBody(JSON)

    private fun <T> read(text: String, answer: DeserializationStrategy<T>): OnlineResult<T> = try {
        OnlineResult.Ok(OnlineProtocol.json.decodeFromString(answer, text))
    } catch (_: IllegalArgumentException) {
        // An answer this build can't read: a newer server, as on the socket (docs/ONLINE.md "The client").
        OnlineResult.Refused(Refusal.UPDATE_APP)
    }

    private suspend fun <T> exchange(
        method: String,
        route: String,
        body: RequestBody?,
        signedIn: Boolean = true,
        ok: (String) -> OnlineResult<T>,
    ): OnlineResult<T> {
        val host = hosts.current()
        if (host.tooOld) return OnlineResult.Refused(Refusal.UPDATE_APP)
        val base = host.base ?: return OnlineResult.Failed(host.note)
        val request = Request.Builder().url(base + route).method(method, body)
        if (signedIn) {
            val token = identities.load()?.token ?: return OnlineResult.Refused(Refusal.BAD_TOKEN)
            request.header(AUTHORIZATION, "Bearer $token")
        }
        return try {
            client.newCall(request.build()).await().use { response ->
                val text = withContext(Dispatchers.IO) { response.text() }
                when {
                    text == null -> OnlineResult.Failed()
                    response.isSuccessful -> ok(text)
                    else -> onlineFailure(text, response.header(RETRY_AFTER)?.toIntOrNull())
                }
            }
        } catch (_: IOException) {
            OnlineResult.Failed()
        }
    }

    /** The body, or null when it is larger than any answer here should be. */
    private fun Response.text(): String? {
        val buffer = Buffer()
        body.source().use { source ->
            while (source.read(buffer, READ_CHUNK) != -1L) {
                if (buffer.size > MAX_BODY_BYTES) return null
            }
        }
        return buffer.readUtf8()
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val RETRY_AFTER = "Retry-After"
        const val READ_CHUNK = 16L * 1024
        const val MAX_BODY_BYTES = 1024L * 1024
        val JSON = "application/json".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(JSON)
    }
}
