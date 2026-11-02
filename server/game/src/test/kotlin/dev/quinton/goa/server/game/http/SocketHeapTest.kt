package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.server.game.FakeRooms
import dev.quinton.goa.server.game.GameServerConfig
import dev.quinton.goa.server.game.board
import dev.quinton.goa.server.game.hello
import dev.quinton.goa.server.game.json
import dev.quinton.goa.server.game.parse
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.store.SqliteGameStore
import dev.quinton.goa.server.game.testEngine
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.InputStream
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * What a socket really costs on the server's heap, with bare TCP clients that add next to nothing to it. Says how
 * much for docs/ONLINE.md "Limits", and fails if 300 sockets no longer fit in what `compose.yaml` gives the heap.
 */
class SocketHeapTest {
    private fun used(): Long {
        repeat(SETTLE_ROUNDS) {
            System.gc()
            Thread.sleep(SETTLE_MILLIS)
        }
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun InputStream.skipHeaders() {
        var tail = 0
        while (tail != HEADER_END) {
            val c = read()
            check(c >= 0) { "The server hung up" }
            tail = tail shl 8 or c
        }
    }

    /** A real upgrade and a real Hello, by hand: a masked text frame is all the server insists on. */
    private fun open(port: Int, helloText: String): Socket {
        val socket = Socket("127.0.0.1", port)
        val out = socket.getOutputStream()
        out.write(
            (
                "GET /v1/play HTTP/1.1\r\nHost: game\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
                ).toByteArray(),
        )
        out.flush()
        socket.getInputStream().skipHeaders()
        val payload = helloText.toByteArray()
        val length = if (payload.size < EXTENDED) {
            byteArrayOf((MASKED or payload.size).toByte())
        } else {
            byteArrayOf((MASKED or EXTENDED).toByte(), (payload.size shr 8).toByte(), payload.size.toByte())
        }
        // A mask of zeros leaves the text as it is.
        out.write(byteArrayOf(TEXT_FRAME.toByte()) + length + ByteArray(4) + payload)
        out.flush()
        // The first bytes of the Welcome: it has been admitted, and holds everything a socket holds.
        val input = socket.getInputStream()
        check(input.read() == TEXT_FRAME) { "No Welcome" }
        return socket
    }

    @Test
    fun threeHundredSocketsFitTheHeap() = runBlocking {
        val dir = Files.createTempDirectory("goa-socket-heap")
        val store = SqliteGameStore.open(dir)
        val config = GameServerConfig(
            dir,
            Paths.get("unused"),
            perClientPerMinute = 100_000,
            globalPerMinute = 100_000,
            accountsPerClientPerHour = 100_000,
            accountsGlobalPerHour = 100_000,
            upgradesPerClientPerMinute = 100_000,
            upgradesGlobalPerMinute = 100_000,
        )
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            gameServerModule(config, store, testEngine, FakeRooms(), LeaderboardSource { board() })
        }.start(wait = false)
        val sockets = ArrayList<Socket>()
        try {
            val port = server.engine.resolvedConnectors().first().port
            val http = HttpClient.newHttpClient()
            // One account each: a person's fifth socket would push out their first.
            val hellos = List(WARM + SOCKETS) {
                val created = http.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v1/accounts"))
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                CreateAccount.serializer().json(CreateAccount("Heap")),
                            ),
                        )
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                val token = AccountCreated.serializer().parse(created.body()).token
                ClientMessage.serializer().json(hello(token))
            }
            // Warm: the first sockets also load classes, which isn't what each one costs.
            hellos.take(WARM).forEach { sockets += open(port, it) }
            val before = used()
            hellos.drop(WARM).forEach { sockets += open(port, it) }
            val after = used()
            val each = (after - before) / SOCKETS
            println("SOCKET HEAP: $each bytes each, ${each * CAP / MEGABYTE} MB for $CAP")
            assertThat(each * CAP).isLessThan(HEAP_BUDGET_BYTES)
        } finally {
            sockets.forEach { it.close() }
            server.stop(0, 0)
            store.close()
            dir.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val WARM = 20
        const val SOCKETS = 200
        const val CAP = 300L
        const val MEGABYTE = 1024L * 1024L
        const val HEAP_BUDGET_BYTES = 128 * MEGABYTE
        const val SETTLE_ROUNDS = 5
        const val SETTLE_MILLIS = 200L
        const val HEADER_END = 0x0D0A0D0A
        const val TEXT_FRAME = 0x81
        const val MASKED = 0x80
        const val EXTENDED = 126
    }
}
