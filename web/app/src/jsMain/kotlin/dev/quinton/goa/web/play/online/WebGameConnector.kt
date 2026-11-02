package dev.quinton.goa.web.play.online

import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.GameConnection
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.HostUnavailable
import dev.quinton.goa.feature.board.play.online.decodeServerFrame
import dev.quinton.goa.feature.board.play.online.encodeClientFrame
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.WebSocket

/** The browser's WebSocket as the connector uses it, so the connector is tested on Node without a server. */
interface BrowserSocket {
    var onOpen: () -> Unit
    var onText: (String) -> Unit

    /** The socket is gone, whatever the reason; a browser doesn't say why (and calls this after any error). */
    var onClosed: () -> Unit

    /** Throws when the socket isn't open. */
    fun send(text: String)

    fun close()
}

/** `https://host:8443/game` → `wss://host:8443/game/v1/play`; a test server's `http://` → `ws://`. */
fun gameSocketUrl(base: String): String {
    val trimmed = base.trimEnd('/')
    val address = when {
        trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
        trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
        else -> error("Not a game server address: $trimmed")
    }
    return address + OnlineRoutes.PLAY
}

/**
 * [GameConnector] over the browser's WebSocket (docs/ONLINE.md "Routes"). The token goes in `Hello`, the
 * first frame, never in the address: a browser can't set a header on a socket, and an address gets logged.
 * A frame this build can't read fails `incoming` with [decodeServerFrame]'s SerializationException, which
 * the driver takes to mean a newer server.
 */
class WebGameConnector(
    private val base: suspend () -> String?,
    private val tooOld: suspend () -> Boolean = { false },
    private val socket: (url: String) -> BrowserSocket = ::browserSocket,
) : GameConnector {
    override suspend fun open(hello: ClientMessage.Hello): GameConnection {
        if (tooOld()) throw HostUnavailable(tooOld = true)
        val address = base() ?: throw HostUnavailable(tooOld = false)
        val opened = socket(gameSocketUrl(address))
        val frames = Channel<String>(Channel.UNLIMITED)
        opened.onText = { frames.trySend(it) }
        suspendCancellableCoroutine { waiting ->
            waiting.invokeOnCancellation { opened.close() }
            opened.onOpen = { if (waiting.isActive) waiting.resume(Unit) }
            opened.onClosed = {
                frames.close()
                if (waiting.isActive) waiting.resumeWithException(IllegalStateException("The socket didn't open"))
            }
        }
        opened.send(encodeClientFrame(hello))
        return Connection(opened, frames)
    }

    private class Connection(private val socket: BrowserSocket, private val frames: Channel<String>) : GameConnection {
        override val incoming: Flow<ServerMessage> = frames.receiveAsFlow().map(::decodeServerFrame)

        override suspend fun send(message: ClientMessage) = socket.send(encodeClientFrame(message))

        override fun close() {
            frames.close()
            socket.close()
        }
    }
}

private fun browserSocket(url: String): BrowserSocket = object : BrowserSocket {
    private val socket = WebSocket(url)
    override var onOpen: () -> Unit = {}
    override var onText: (String) -> Unit = {}
    override var onClosed: () -> Unit = {}

    init {
        socket.onopen = { onOpen() }
        // A frame that isn't text can't be one of ours: empty text fails the flow like any unreadable frame.
        socket.onmessage = { onText(it.data as? String ?: "") }
        socket.onclose = { onClosed() }
        socket.onerror = { onClosed() }
    }

    override fun send(text: String) {
        check(socket.readyState == WebSocket.OPEN) { "The socket isn't open" }
        socket.send(text)
    }

    override fun close() = socket.close()
}
