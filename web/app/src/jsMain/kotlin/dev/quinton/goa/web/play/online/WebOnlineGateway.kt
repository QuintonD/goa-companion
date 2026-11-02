package dev.quinton.goa.web.play.online

import dev.quinton.goa.core.data.sync.RelayHttp
import dev.quinton.goa.core.data.sync.RelayNetworkException
import dev.quinton.goa.core.data.sync.RelayRequest
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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer

private const val MAX_BODY_BYTES = 1024L * 1024
private const val JSON = "application/json"

/**
 * The game server's REST routes over `fetch` ([RelayHttp]: no redirects followed, no cookies, no cache, no
 * referrer). [base] is read on every call, so a host closed in `network.json` stops being called. The bearer
 * token comes from [identities]; no token, or a token the server doesn't know, is [Refusal.BAD_TOKEN].
 */
class WebOnlineGateway(
    private val http: RelayHttp,
    private val identities: OnlineIdentityStore,
    private val base: suspend () -> String?,
) : OnlineGateway {
    override suspend fun createAccount(name: String): OnlineResult<AccountCreated> =
        open("POST", OnlineRoutes.ACCOUNTS, serializer(), encoded(CreateAccount(name)))

    override suspend fun link(recoveryCode: String): OnlineResult<AccountCreated> =
        open("POST", OnlineRoutes.ACCOUNTS_LINK, serializer(), encoded(LinkAccount(recoveryCode)))

    override suspend fun me(): OnlineResult<Me> = signed("GET", OnlineRoutes.ME, serializer())

    override suspend fun rename(name: String): OnlineResult<Me> =
        signed("PATCH", OnlineRoutes.ME, serializer(), encoded(Rename(name)))

    override suspend fun deleteMe(): OnlineResult<Unit> = signed("DELETE", OnlineRoutes.ME, Unit.serializer())

    override suspend fun createRoom(room: CreateRoom): OnlineResult<RoomCreated> =
        signed("POST", OnlineRoutes.ROOMS, serializer(), encoded(room))

    override suspend fun join(code: String): OnlineResult<RoomView> =
        signed("POST", OnlineRoutes.join(code), serializer())

    override suspend fun leave(code: String): OnlineResult<RoomView> =
        signed("POST", OnlineRoutes.leave(code), serializer())

    override suspend fun myGames(): OnlineResult<MyGames> = signed("GET", OnlineRoutes.MY_GAMES, serializer())

    private inline fun <reified T> encoded(body: T): String = OnlineProtocol.json.encodeToString(serializer<T>(), body)

    private suspend fun <T> signed(
        method: String,
        path: String,
        reply: DeserializationStrategy<T>,
        body: String? = null,
    ): OnlineResult<T> {
        val token = identities.load()?.token ?: return OnlineResult.Refused(Refusal.BAD_TOKEN)
        return call(method, path, reply, body, mapOf("Authorization" to "Bearer $token"))
    }

    private suspend fun <T> open(
        method: String,
        path: String,
        reply: DeserializationStrategy<T>,
        body: String? = null,
    ): OnlineResult<T> = call(method, path, reply, body, emptyMap())

    private suspend fun <T> call(
        method: String,
        path: String,
        reply: DeserializationStrategy<T>,
        body: String?,
        headers: Map<String, String>,
    ): OnlineResult<T> {
        val address = base() ?: return OnlineResult.Failed()
        val request = RelayRequest(
            method = method,
            url = address.trimEnd('/') + path,
            headers = headers,
            body = body?.encodeToByteArray(),
            contentType = body?.let { JSON },
        )
        return try {
            http.exchange(request) { response ->
                val text = response.body(MAX_BODY_BYTES)?.decodeToString().orEmpty()
                if (response.code in 200..299) {
                    decoded(text, reply)
                } else {
                    onlineFailure(text, response.header("Retry-After")?.toIntOrNull())
                }
            }
        } catch (_: RelayNetworkException) {
            OnlineResult.Failed()
        }
    }

    /** A body that makes no sense from a server that said yes is no answer at all. Unit has no body. */
    private fun <T> decoded(text: String, reply: DeserializationStrategy<T>): OnlineResult<T> = try {
        @Suppress("UNCHECKED_CAST")
        if (reply == Unit.serializer()) {
            OnlineResult.Ok(Unit as T)
        } else {
            OnlineResult.Ok(OnlineProtocol.json.decodeFromString(reply, text))
        }
    } catch (_: SerializationException) {
        OnlineResult.Failed()
    }
}
