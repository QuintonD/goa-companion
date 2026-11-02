package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.server.game.serverTest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.Test

class CorsTest {
    private val pages = "https://quintond.github.io"

    @Test
    fun aPreflightFromThePagesSiteIsAllowedForEveryMethodOfTheApi() = serverTest {
        for (method in listOf("GET", "POST", "PATCH", "DELETE")) {
            val response = http.options("/v1/me") {
                header(HttpHeaders.Origin, pages)
                header(HttpHeaders.AccessControlRequestMethod, method)
                header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type,if-none-match")
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
            assertThat(response.headers[HttpHeaders.AccessControlAllowOrigin]).isEqualTo(pages)
            assertThat(response.headers[HttpHeaders.AccessControlAllowMethods]).isEqualTo("GET, POST, PATCH, DELETE")
            assertThat(response.headers[HttpHeaders.AccessControlAllowCredentials]).isNull()
        }
    }

    @Test
    fun aPreflightAlsoWorksOnRoomPathsAndUnderTheMount() = serverTest {
        for (path in listOf("/v1/rooms/ABCDEFGH/join", "/game/v1/rooms", "/game/v1/leaderboard")) {
            val response = http.options(path) {
                header(HttpHeaders.Origin, pages)
                header(HttpHeaders.AccessControlRequestMethod, "POST")
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        }
    }

    @Test
    fun otherOriginsMethodsHeadersAndPathsAreRefused() = serverTest {
        suspend fun status(origin: String?, method: String = "GET", headers: String = "", path: String = "/v1/me") =
            http.options(path) {
                origin?.let { header(HttpHeaders.Origin, it) }
                header(HttpHeaders.AccessControlRequestMethod, method)
                if (headers.isNotEmpty()) header(HttpHeaders.AccessControlRequestHeaders, headers)
            }.status

        assertThat(status("https://evil.example")).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(status("https://quintond.github.io.evil.example")).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(status(null)).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(status(pages, method = "PUT")).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(status(pages, headers = "x-other")).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(status(pages, path = "/v1/nothing")).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    fun anAllowedOriginCanReadAnswersOfEveryStatusAndAnotherGetsNoHeaders() = serverTest {
        val allowed = http.get("/v1/me") { header(HttpHeaders.Origin, pages) }
        assertThat(allowed.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(allowed.headers[HttpHeaders.AccessControlAllowOrigin]).isEqualTo(pages)
        assertThat(allowed.headers[HttpHeaders.AccessControlExposeHeaders]).isEqualTo("ETag, Retry-After")
        assertThat(allowed.headers[HttpHeaders.AccessControlAllowCredentials]).isNull()

        val other = http.get("/v1/health") { header(HttpHeaders.Origin, "https://evil.example") }
        assertThat(other.status).isEqualTo(HttpStatusCode.OK)
        assertThat(other.headers[HttpHeaders.AccessControlAllowOrigin]).isNull()
        assertThat(other.headers[HttpHeaders.Vary]).isEqualTo(HttpHeaders.Origin)
    }

    @Test
    fun preflightsComeBeforeTheRateLimiter() = serverTest(configure = { it.copy(perClientPerMinute = 1) }) {
        repeat(5) {
            val response = http.options("/v1/me") {
                header(HttpHeaders.Origin, pages)
                header(HttpHeaders.AccessControlRequestMethod, "GET")
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        }
        assertThat(http.get("/v1/me") { bearerAuth("x") }.status).isEqualTo(HttpStatusCode.Unauthorized)
        val limited = http.get("/v1/me") { header(HttpHeaders.Origin, pages) }
        assertThat(limited.status).isEqualTo(HttpStatusCode.TooManyRequests)
        // A browser must be able to read the 429 and its Retry-After.
        assertThat(limited.headers[HttpHeaders.AccessControlAllowOrigin]).isEqualTo(pages)
    }
}
