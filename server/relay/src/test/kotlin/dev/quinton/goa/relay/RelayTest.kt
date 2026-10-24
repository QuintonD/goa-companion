package dev.quinton.goa.relay

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayTest {
    @TempDir
    lateinit var dir: Path

    private val doc = pathOf(GROUP)

    @Test
    fun `health answers without auth and carries the security headers`() = relay {
        val response = client.get("/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"ok":true,"protocol":1}""", response.bodyAsText())
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("noindex", response.headers["X-Robots-Tag"])
    }

    @Test
    fun `full flow - create, read, update, stale writes and wrong tokens`() = relay {
        val first = client.putDoc(byteArrayOf(1, 2, 3), ifNoneMatch = "*")
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals("\"1\"", first.headers[HttpHeaders.ETag])

        val read = client.getDoc()
        assertEquals(HttpStatusCode.OK, read.status)
        assertEquals("\"1\"", read.headers[HttpHeaders.ETag])
        assertEquals("application/vnd.dev.quinton.goa.sync", read.headers[HttpHeaders.ContentType])
        assertContentEquals(byteArrayOf(1, 2, 3), read.bodyAsBytes())

        val second = client.putDoc(byteArrayOf(4, 5), ifMatch = "\"1\"")
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals("\"2\"", second.headers[HttpHeaders.ETag])

        assertEquals(HttpStatusCode.PreconditionFailed, client.putDoc(byteArrayOf(6), ifMatch = "\"1\"").status)
        assertEquals(HttpStatusCode.PreconditionFailed, client.putDoc(byteArrayOf(6), ifNoneMatch = "*").status)
        assertEquals(HttpStatusCode.PreconditionFailed, client.putDoc(byteArrayOf(6), ifMatch = "\"nonsense\"").status)

        assertEquals(HttpStatusCode.Unauthorized, client.putDoc(byteArrayOf(6), ifMatch = "\"2\"", token = OTHER_TOKEN).status)
        assertEquals(HttpStatusCode.Unauthorized, client.getDoc(token = OTHER_TOKEN).status)
        assertEquals(HttpStatusCode.Unauthorized, client.getDoc(token = null).status)
        val noToken = client.putDoc(byteArrayOf(6), ifMatch = "\"2\"", token = null)
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)
        assertEquals("Bearer realm=\"goa-relay\"", noToken.headers[HttpHeaders.WWWAuthenticate])

        val latest = client.getDoc()
        assertEquals("\"2\"", latest.headers[HttpHeaders.ETag])
        assertContentEquals(byteArrayOf(4, 5), latest.bodyAsBytes())

        val unchanged = client.getDoc(ifNoneMatch = "\"2\"")
        assertEquals(HttpStatusCode.NotModified, unchanged.status)
        assertEquals("\"2\"", unchanged.headers[HttpHeaders.ETag])

        assertEquals(HttpStatusCode.OK, client.putDoc(byteArrayOf(7), ifMatch = "*").status)
        assertEquals("\"3\"", client.getDoc().headers[HttpHeaders.ETag])
    }

    @Test
    fun `an unregistered group is 404 whatever the token`() = relay {
        assertEquals(HttpStatusCode.NotFound, client.getDoc().status)
        assertEquals(HttpStatusCode.NotFound, client.getDoc(token = OTHER_TOKEN).status)
        assertEquals(HttpStatusCode.NotFound, client.getDoc(token = null).status)
        assertEquals(HttpStatusCode.PreconditionFailed, client.putDoc(byteArrayOf(1), ifMatch = "\"1\"").status)
    }

    @Test
    fun `requests are refused before any work`() = relay {
        assertEquals(HttpStatusCode.PreconditionRequired, client.putDoc(byteArrayOf(1)).status)
        assertEquals(HttpStatusCode.BadRequest, client.putDoc(ByteArray(0), ifNoneMatch = "*").status)
        for (bad in listOf("short", "has_underscore-0000", "x".repeat(65), "dots.in.it.0000")) {
            assertEquals(HttpStatusCode.BadRequest, client.get(pathOf(bad)).status, bad)
            assertEquals(HttpStatusCode.BadRequest, client.putDoc(byteArrayOf(1), ifNoneMatch = "*", path = pathOf(bad)).status, bad)
        }
        assertFalse(Files.exists(dir.resolve("groups").resolve(GROUP)))
    }

    @Test
    fun `the 8 MiB limit is exact and holds for chunked bodies`() = relay {
        val limit = RelayConfig.MAX_BODY_BYTES.toInt()
        assertEquals(HttpStatusCode.PayloadTooLarge, client.putDoc(ByteArray(limit + 1), ifNoneMatch = "*").status)
        assertEquals(HttpStatusCode.NotFound, client.getDoc().status)

        val chunked = client.put(doc) {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
            header(HttpHeaders.IfNoneMatch, "*")
            setBody(Chunked(chunk = 1024 * 1024, chunks = 9))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, chunked.status)
        assertEquals(HttpStatusCode.NotFound, client.getDoc().status)

        assertEquals(HttpStatusCode.Created, client.putDoc(ByteArray(limit), ifNoneMatch = "*").status)
        assertEquals(limit, client.getDoc().bodyAsBytes().size)
    }

    @Test
    fun `history keeps the last 20 revisions and no token is stored in the clear`() = relay {
        assertEquals(HttpStatusCode.Created, client.putDoc(byteArrayOf(1), ifNoneMatch = "*").status)
        for (rev in 1..24) {
            assertEquals(HttpStatusCode.OK, client.putDoc(byteArrayOf((rev + 1).toByte()), ifMatch = "\"$rev\"").status)
        }
        val group = dir.resolve("groups").resolve(GROUP)
        val history = group.resolve("history").listDirectoryEntries().map { it.name }.toSet()
        assertEquals((6..25).map { "$it.bin" }.toSet(), history)
        assertContentEquals(byteArrayOf(25), Files.readAllBytes(group.resolve("history/25.bin")))
        assertContentEquals(byteArrayOf(25), Files.readAllBytes(group.resolve("document.bin")))

        val meta = group.resolve("meta.json").readText()
        assertTrue(meta.contains("\"rev\":25"), meta)
        assertTrue(meta.contains(GroupStore.sha256Hex(TOKEN)), meta)
        Files.walk(dir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { file ->
                assertFalse(Files.readAllBytes(file).decodeToString().contains(TOKEN), "token in $file")
                assertFalse(file.name.startsWith(".tmp-"), "temp file left: $file")
            }
        }
    }

    @Test
    fun `a restarted relay on the same directory keeps everything`() {
        relay { assertEquals(HttpStatusCode.Created, client.putDoc(byteArrayOf(9, 9), ifNoneMatch = "*").status) }
        relay {
            val read = client.getDoc()
            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals("\"1\"", read.headers[HttpHeaders.ETag])
            assertContentEquals(byteArrayOf(9, 9), read.bodyAsBytes())
            assertEquals(HttpStatusCode.Unauthorized, client.getDoc(token = OTHER_TOKEN).status)
            assertEquals(HttpStatusCode.OK, client.putDoc(byteArrayOf(1), ifMatch = "\"1\"").status)
        }
    }

    @Test
    fun `concurrent uploads naming the same revision - exactly one wins`() = relay {
        assertEquals(HttpStatusCode.Created, client.putDoc(byteArrayOf(0), ifNoneMatch = "*").status)
        val statuses = coroutineScope {
            (1..16).map { i -> async { client.putDoc(byteArrayOf(i.toByte()), ifMatch = "\"1\"").status } }.awaitAll()
        }
        assertEquals(1, statuses.count { it == HttpStatusCode.OK }, statuses.toString())
        assertEquals(15, statuses.count { it == HttpStatusCode.PreconditionFailed }, statuses.toString())
        assertEquals("\"2\"", client.getDoc().headers[HttpHeaders.ETag])
    }

    @Test
    fun `concurrent first uploads - exactly one registers the group`() = relay {
        val statuses = coroutineScope {
            (1..16).map { i ->
                async { client.putDoc(byteArrayOf(i.toByte()), ifNoneMatch = "*", token = if (i % 2 == 0) TOKEN else OTHER_TOKEN).status }
            }.awaitAll()
        }
        assertEquals(1, statuses.count { it == HttpStatusCode.Created }, statuses.toString())
        assertTrue(statuses.all { it == HttpStatusCode.Created || it == HttpStatusCode.PreconditionFailed || it == HttpStatusCode.Unauthorized })
    }

    @Test
    fun `rate limiting per client and globally answers 429 with Retry-After`() {
        var now = 0L
        relay(limiter = RateLimiter(perClientPerMinute = 3, globalPerMinute = 5) { now }) {
            repeat(3) { assertEquals(HttpStatusCode.OK, client.get("/v1/health") { forwardedFor("1.1.1.1") }.status) }
            val limited = client.get("/v1/health") { forwardedFor("1.1.1.1") }
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("20", limited.headers[HttpHeaders.RetryAfter])
            // The last hop is the client: a spoofed first hop doesn't buy a fresh bucket.
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/health") { forwardedFor("9.9.9.9, 1.1.1.1") }.status)

            repeat(2) { assertEquals(HttpStatusCode.OK, client.get("/v1/health") { forwardedFor("2.2.2.2") }.status) }
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/health") { forwardedFor("3.3.3.3") }.status)

            now += 60_000_000_000L
            assertEquals(HttpStatusCode.OK, client.get("/v1/health") { forwardedFor("1.1.1.1") }.status)
        }
    }

    @Test
    fun `a full relay refuses new groups with 503 but serves existing ones`() =
        relay(config = RelayConfig(dataDir = dir, maxGroups = 1)) {
            assertEquals(HttpStatusCode.Created, client.putDoc(byteArrayOf(1), ifNoneMatch = "*").status)
            val refused = client.putDoc(byteArrayOf(1), ifNoneMatch = "*", path = pathOf(OTHER_GROUP))
            assertEquals(HttpStatusCode.ServiceUnavailable, refused.status)
            assertEquals("3600", refused.headers[HttpHeaders.RetryAfter])
            assertEquals(HttpStatusCode.OK, client.putDoc(byteArrayOf(2), ifMatch = "\"1\"").status)
        }

    @Test
    fun `an unusable data directory answers 503`() {
        val notADirectory = dir.resolve("file")
        Files.writeString(notADirectory, "x")
        relay(config = RelayConfig(dataDir = notADirectory)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/v1/health").status)
            val get = client.getDoc()
            assertEquals(HttpStatusCode.ServiceUnavailable, get.status)
            assertEquals("30", get.headers[HttpHeaders.RetryAfter])
            assertEquals(HttpStatusCode.ServiceUnavailable, client.putDoc(byteArrayOf(1), ifNoneMatch = "*").status)
        }
    }

    private fun relay(
        config: RelayConfig = RelayConfig(dataDir = dir),
        limiter: RateLimiter = RateLimiter(perClientPerMinute = 100_000, globalPerMinute = 100_000),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { relayModule(config, limiter = limiter) }
        block()
    }

    private suspend fun HttpClient.putDoc(
        body: ByteArray,
        ifMatch: String? = null,
        ifNoneMatch: String? = null,
        token: String? = TOKEN,
        path: String = doc,
    ): HttpResponse = put(path) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        ifMatch?.let { header(HttpHeaders.IfMatch, it) }
        ifNoneMatch?.let { header(HttpHeaders.IfNoneMatch, it) }
        setBody(body)
    }

    private suspend fun HttpClient.getDoc(token: String? = TOKEN, ifNoneMatch: String? = null): HttpResponse = get(doc) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        ifNoneMatch?.let { header(HttpHeaders.IfNoneMatch, it) }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.forwardedFor(value: String) =
        header(HttpHeaders.XForwardedFor, value)

    /** A body with no Content-Length, sent in chunks, to prove the limit isn't taken on trust. */
    private class Chunked(private val chunk: Int, private val chunks: Int) : OutgoingContent.WriteChannelContent() {
        override suspend fun writeTo(channel: ByteWriteChannel) {
            val bytes = ByteArray(chunk)
            repeat(chunks) { channel.writeFully(bytes) }
        }
    }

    private companion object {
        const val GROUP = "8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f"
        const val OTHER_GROUP = "0f9e8d7c-6b5a-4938-8271-605f4e3d2c1b"
        val TOKEN = RelayTokenTest.phoneToken(ByteArray(32) { it.toByte() }, GROUP)
        val OTHER_TOKEN = RelayTokenTest.phoneToken(ByteArray(32) { (it + 1).toByte() }, GROUP)

        fun pathOf(groupId: String) = "/v1/groups/$groupId/document"
    }
}
