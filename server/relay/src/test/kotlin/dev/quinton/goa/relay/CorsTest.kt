package dev.quinton.goa.relay

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The web app (ADR-043) talks to the relay from the GitHub Pages origin: CORS. */
class CorsTest {
    @TempDir
    lateinit var dir: Path

    private val doc = pathOf(GROUP)

    @Test
    fun `a preflight from the Pages site for a PUT with the protocol's headers is allowed`() = relay {
        val response = client.options(doc) {
            header(HttpHeaders.Origin, PAGES)
            header(HttpHeaders.AccessControlRequestMethod, "PUT")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,if-match,content-type")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(PAGES, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("GET, PUT", response.headers[HttpHeaders.AccessControlAllowMethods])
        assertEquals(
            "Authorization, If-Match, If-None-Match, Content-Type",
            response.headers[HttpHeaders.AccessControlAllowHeaders],
        )
        assertEquals("600", response.headers[HttpHeaders.AccessControlMaxAge])
        assertNull(response.headers[HttpHeaders.AccessControlAllowCredentials])
        assertTrue(response.headers.getAll(HttpHeaders.Vary).orEmpty().joinToString().contains("Origin"))

        val get = client.options(doc) {
            header(HttpHeaders.Origin, PAGES)
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "Authorization, If-None-Match")
        }
        assertEquals(HttpStatusCode.NoContent, get.status)
    }

    @Test
    fun `preflights for other origins, methods, headers or paths are refused`() = relay {
        suspend fun preflight(origin: String?, method: String, headers: String = "authorization", path: String = doc) =
            client.options(path) {
                origin?.let { header(HttpHeaders.Origin, it) }
                header(HttpHeaders.AccessControlRequestMethod, method)
                header(HttpHeaders.AccessControlRequestHeaders, headers)
            }

        for (response in listOf(
            preflight("https://evil.example", "PUT"),
            preflight("http://quintond.github.io", "PUT"),
            preflight(null, "PUT"),
            preflight(PAGES, "DELETE"),
            preflight(PAGES, "PUT", headers = "authorization, x-secret"),
            preflight(PAGES, "GET", path = "/v1/admin"),
        )) {
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertNull(response.headers[HttpHeaders.AccessControlAllowMethods])
        }
        assertNull(preflight("https://evil.example", "PUT").headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `answers to the Pages site expose ETag and Retry-After, errors included`() = relay {
        val created = client.put(doc) {
            header(HttpHeaders.Origin, PAGES)
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
            header(HttpHeaders.IfNoneMatch, "*")
            setBody(byteArrayOf(1, 2))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(PAGES, created.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("ETag, Retry-After", created.headers[HttpHeaders.AccessControlExposeHeaders])

        val unauthorized = client.get(doc) {
            header(HttpHeaders.Origin, PAGES)
            header(HttpHeaders.Authorization, "Bearer $OTHER_TOKEN")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertEquals(PAGES, unauthorized.headers[HttpHeaders.AccessControlAllowOrigin])

        val stale = client.put(doc) {
            header(HttpHeaders.Origin, PAGES)
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
            header(HttpHeaders.IfMatch, "\"7\"")
            setBody(byteArrayOf(3))
        }
        assertEquals(HttpStatusCode.PreconditionFailed, stale.status)
        assertEquals(PAGES, stale.headers[HttpHeaders.AccessControlAllowOrigin])

        val other = client.get("/v1/health") { header(HttpHeaders.Origin, "https://evil.example") }
        assertEquals(HttpStatusCode.OK, other.status)
        assertNull(other.headers[HttpHeaders.AccessControlAllowOrigin])

        // Phones send no Origin: nothing changes for them.
        val phone = client.get("/v1/health")
        assertNull(phone.headers[HttpHeaders.AccessControlAllowOrigin])
        assertNull(phone.headers[HttpHeaders.Vary])
    }

    @Test
    fun `a rate-limited browser reads the 429 and its Retry-After, and preflights are not counted`() {
        var now = 0L
        relay(limiter = RateLimiter(perClientPerMinute = 1, globalPerMinute = 100) { now }) {
            repeat(3) {
                val preflight = client.options(doc) {
                    header(HttpHeaders.Origin, PAGES)
                    header(HttpHeaders.AccessControlRequestMethod, "GET")
                }
                assertEquals(HttpStatusCode.NoContent, preflight.status)
            }
            assertEquals(HttpStatusCode.OK, client.get("/v1/health") { header(HttpHeaders.Origin, PAGES) }.status)
            val limited = client.get("/v1/health") { header(HttpHeaders.Origin, PAGES) }
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals(PAGES, limited.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("ETag, Retry-After", limited.headers[HttpHeaders.AccessControlExposeHeaders])
            assertEquals("60", limited.headers[HttpHeaders.RetryAfter])
        }
    }

    @Test
    fun `the allowed origins come from GOA_RELAY_CORS_ORIGINS`() {
        assertEquals(setOf(PAGES), RelayConfig.fromEnv(mapOf()).corsOrigins)
        assertEquals(
            setOf("http://localhost:4173", PAGES),
            RelayConfig.fromEnv(mapOf("GOA_RELAY_CORS_ORIGINS" to " http://localhost:4173/, https://QuintonD.github.io ")).corsOrigins,
        )
        assertEquals(emptySet(), RelayConfig.fromEnv(mapOf("GOA_RELAY_CORS_ORIGINS" to "")).corsOrigins)
        assertThrows<IllegalArgumentException> {
            RelayConfig.fromEnv(mapOf("GOA_RELAY_CORS_ORIGINS" to "https://quintond.github.io/goa-companion"))
        }
    }

    @Test
    fun `a relay with another origin list allows exactly those`() =
        relay(config = RelayConfig(dataDir = dir, corsOrigins = setOf("http://localhost:4173"))) {
            val local = client.options(doc) {
                header(HttpHeaders.Origin, "http://localhost:4173")
                header(HttpHeaders.AccessControlRequestMethod, "PUT")
            }
            assertEquals(HttpStatusCode.NoContent, local.status)
            val pages = client.options(doc) {
                header(HttpHeaders.Origin, PAGES)
                header(HttpHeaders.AccessControlRequestMethod, "PUT")
            }
            assertEquals(HttpStatusCode.Forbidden, pages.status)
        }

    private fun relay(
        config: RelayConfig = RelayConfig(dataDir = dir),
        limiter: RateLimiter = RateLimiter(perClientPerMinute = 100_000, globalPerMinute = 100_000),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { relayModule(config, limiter = limiter) }
        block()
    }

    private companion object {
        const val PAGES = "https://quintond.github.io"
        const val GROUP = "8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f"
        val TOKEN = RelayTokenTest.phoneToken(ByteArray(32) { it.toByte() }, GROUP)
        val OTHER_TOKEN = RelayTokenTest.phoneToken(ByteArray(32) { (it + 1).toByte() }, GROUP)

        fun pathOf(groupId: String) = "/v1/groups/$groupId/document"
    }
}
