package dev.quinton.goa.core.data.online

import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.GameConnection
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.HostUnavailable
import dev.quinton.goa.feature.board.play.online.decodeServerFrame
import dev.quinton.goa.feature.board.play.online.encodeClientFrame
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * [GameConnector] over OkHttp's WebSocket. `Hello` is the first frame, because browsers can't set a header on
 * a WebSocket and a URL gets logged: the token is in neither. The client adds no `Origin`, as native clients
 * must not (docs/ONLINE.md "Routes").
 */
class OkHttpGameConnector @Inject constructor(
    @GameSocketHttp private val client: OkHttpClient,
    private val hosts: GameHosts,
) : GameConnector {
    override suspend fun open(hello: ClientMessage.Hello): GameConnection {
        val host = hosts.current()
        if (host.tooOld) throw HostUnavailable(tooOld = true)
        val base = host.base ?: throw HostUnavailable(tooOld = false, note = host.note)
        val request = Request.Builder().url(base + OnlineRoutes.PLAY).build()
        val frames = Channel<String>(Channel.UNLIMITED)
        val socket = suspendCancellableCoroutine<WebSocket> { continuation ->
            val opened = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (continuation.isActive) continuation.resume(webSocket) { _, value, _ -> value.cancel() }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        frames.trySend(text)
                    }

                    // The server sends text: anything else is a frame this build can't read.
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        frames.trySend(bytes.utf8())
                    }

                    // The driver reconnects whatever the code said; some codes can't be sent back.
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(NORMAL_CLOSURE, null)
                        frames.close()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        frames.close()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        frames.close()
                        if (continuation.isActive) continuation.resumeWithException(IOException("Socket failed", t))
                    }
                },
            )
            continuation.invokeOnCancellation { opened.cancel() }
        }
        val connection = OkHttpGameConnection(socket, frames)
        connection.send(hello)
        return connection
    }
}

private class OkHttpGameConnection(private val socket: WebSocket, private val frames: Channel<String>) :
    GameConnection {
    // Off the main thread: a table is large. A frame this build can't read fails the flow with the
    // SerializationException the driver takes to mean a newer server.
    override val incoming: Flow<ServerMessage> =
        frames.receiveAsFlow().map(::decodeServerFrame).flowOn(Dispatchers.Default)

    override suspend fun send(message: ClientMessage) {
        if (!socket.send(encodeClientFrame(message))) throw IOException("Socket closed")
    }

    override fun close() {
        socket.close(NORMAL_CLOSURE, null)
        frames.close()
    }
}

private const val NORMAL_CLOSURE = 1000
