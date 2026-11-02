package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ApiError
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomView
import kotlinx.serialization.SerializationException

/** How a call to the game server went. */
sealed interface OnlineResult<out T> {
    data class Ok<T>(val value: T) : OnlineResult<T>

    /** The server said no, in a way people can be told ([OnlineWords.refusal]). */
    data class Refused(val reason: Refusal, val retryAfter: Int? = null) : OnlineResult<Nothing>

    /** No answer ([message] null), or an error with words for people. */
    data class Failed(val message: String? = null) : OnlineResult<Nothing>
}

/**
 * The server's REST routes (docs/ONLINE.md "Routes"). Android implements it with OkHttp, the web with
 * fetch; both read the bearer token from the [OnlineIdentityStore] and turn a response that isn't a
 * success into a result with [onlineFailure]. Network errors are [OnlineResult.Failed], never thrown.
 */
interface OnlineGateway {
    suspend fun createAccount(name: String): OnlineResult<AccountCreated>

    suspend fun link(recoveryCode: String): OnlineResult<AccountCreated>

    suspend fun me(): OnlineResult<Me>

    suspend fun rename(name: String): OnlineResult<Me>

    suspend fun deleteMe(): OnlineResult<Unit>

    suspend fun createRoom(room: CreateRoom): OnlineResult<RoomCreated>

    /** Also how the room's creator and someone already in it come in: joining twice changes nothing. */
    suspend fun join(code: String): OnlineResult<RoomView>

    suspend fun leave(code: String): OnlineResult<RoomView>

    suspend fun myGames(): OnlineResult<MyGames>
}

/** A response that isn't a success, from its [body] (an [ApiError]) and its `Retry-After` header. */
fun onlineFailure(body: String, retryAfter: Int?): OnlineResult<Nothing> {
    val error = try {
        OnlineProtocol.json.decodeFromString(ApiError.serializer(), body)
    } catch (_: SerializationException) {
        return OnlineResult.Failed()
    }
    val reason = if (error.error == UNAUTHORIZED) {
        Refusal.BAD_TOKEN
    } else {
        Refusal.entries.firstOrNull { refusalCode(it) == error.error }
    }
    return if (reason != null) {
        OnlineResult.Refused(reason, retryAfter)
    } else {
        // It answered, so "can't be reached" would be untrue: its errors have no words of their own.
        OnlineResult.Failed(error.message ?: wordsFor(error.error))
    }
}

private fun refusalCode(reason: Refusal): String =
    OnlineProtocol.json.encodeToString(Refusal.serializer(), reason).trim('"')

private fun wordsFor(code: String): String = when (code) {
    "bad_name" -> OnlineWords.BAD_NAME
    "bad_recovery_code" -> OnlineWords.BAD_RECOVERY_CODE
    else -> OnlineWords.SERVER_SAID_NO
}

/** REST's word for a token it doesn't know, or none at all; the socket says [Refusal.BAD_TOKEN]. */
private const val UNAUTHORIZED = "unauthorized"

/** The words for a call that didn't work. */
fun OnlineResult<*>.words(): String? = when (this) {
    is OnlineResult.Ok -> null
    is OnlineResult.Refused -> OnlineWords.refusal(reason, retryAfter)
    is OnlineResult.Failed -> message ?: OnlineWords.UNREACHABLE
}

/**
 * The room code in what someone pasted, scanned or tapped: a whole invite link, the fragment of one, or
 * the code alone, in any case, with the spaces and dashes people add. Crockford's confusable letters
 * read as the digits they look like (O as 0; I and L as 1). Null when it holds no valid code.
 */
fun parseInvite(text: String): String? {
    val cut = text.substringAfter(OnlineRoutes.INVITE_FRAGMENT, text.trim())
    val raw = cut.substringBefore('&').trim()
    val code = raw.filterNot { it == '-' || it.isWhitespace() }.uppercase().map { normalised(it) }.joinToString("")
    return code.takeIf { it.length == ROOM_CODE_LENGTH && it.all { c -> c in CROCKFORD } }
}

private fun normalised(c: Char): Char = when (c) {
    'O' -> '0'
    'I', 'L' -> '1'
    else -> c
}

/** The address someone follows to join a room. */
fun inviteLink(code: String): String = OnlineRoutes.INVITE_BASE + code

const val ROOM_CODE_LENGTH: Int = 8
private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
