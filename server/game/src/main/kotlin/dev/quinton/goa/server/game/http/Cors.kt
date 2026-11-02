package dev.quinton.goa.server.game.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText

/**
 * CORS for the PWA, as in the relay (ADR-043): an allowed origin may read every answer, statuses included,
 * so it sees 401/429/503 and their `ETag`/`Retry-After` like a phone does. Other origins get no CORS
 * headers. The token travels in `Authorization`, so `Access-Control-Allow-Credentials` is never sent.
 */
internal fun allowOrigin(call: ApplicationCall, allowed: Set<String>) {
    val origin = call.request.headers[HttpHeaders.Origin] ?: return
    call.response.header(HttpHeaders.Vary, HttpHeaders.Origin)
    if (origin !in allowed) return
    call.response.header(HttpHeaders.AccessControlAllowOrigin, origin)
    call.response.header(HttpHeaders.AccessControlExposeHeaders, CORS_EXPOSED)
}

/** `204` for a preflight from an allowed origin asking for the API's methods and headers; `403` otherwise. */
internal suspend fun ApplicationCall.preflight(allowed: Set<String>) {
    val origin = request.headers[HttpHeaders.Origin]
    val method = request.headers[HttpHeaders.AccessControlRequestMethod]?.trim()
    val headers = request.headers[HttpHeaders.AccessControlRequestHeaders].orEmpty()
        .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    val ok = origin != null && origin in allowed && method in CORS_METHODS &&
        CORS_REQUEST_HEADERS.containsAll(headers) && template(request.path()) != OTHER_PATH
    if (!ok) {
        respondText("""{"error":"cors_refused"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
        return
    }
    response.header(
        HttpHeaders.Vary,
        "${HttpHeaders.AccessControlRequestMethod}, ${HttpHeaders.AccessControlRequestHeaders}",
    )
    response.header(HttpHeaders.AccessControlAllowMethods, CORS_METHODS.joinToString(", "))
    response.header(HttpHeaders.AccessControlAllowHeaders, CORS_ALLOWED_HEADERS)
    response.header(HttpHeaders.AccessControlMaxAge, CORS_MAX_AGE_SECONDS)
    respondBytes(ByteArray(0), status = HttpStatusCode.NoContent)
}

private val CORS_METHODS = listOf("GET", "POST", "PATCH", "DELETE")
private const val CORS_ALLOWED_HEADERS = "Authorization, If-None-Match, Content-Type"
private val CORS_REQUEST_HEADERS = CORS_ALLOWED_HEADERS.split(", ").map { it.lowercase() }.toSet()
private const val CORS_EXPOSED = "ETag, Retry-After"

/** WebKit caps a cached preflight at 10 minutes; asking for longer gains nothing. */
private const val CORS_MAX_AGE_SECONDS = "600"
