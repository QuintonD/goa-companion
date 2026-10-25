package dev.quinton.goa.relay

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory
import java.io.IOException

/** Protocol v1 of docs/SYNC-SERVER.md. */
const val PROTOCOL = 1
val DOCUMENT_TYPE = ContentType("application", "vnd.dev.quinton.goa.sync")

private val log = LoggerFactory.getLogger("goa-relay")
private val GROUP_ID = Regex("[A-Za-z0-9-]{8,64}")
private val TOKEN = Regex("[A-Za-z0-9_-]{16,256}")
private val DOCUMENT_PATH = Regex("/v1/groups/[^/]+/document")
private const val STORE_RETRY_SECONDS = "30"
private const val FULL_RETRY_SECONDS = "3600"

fun Application.relayModule(
    config: RelayConfig,
    store: GroupStore = GroupStore(config.dataDir, config.historyKeep, config.maxGroups),
    limiter: RateLimiter = RateLimiter(config.perClientPerMinute, config.globalPerMinute),
) {
    // One log line per request: method, path template, status, duration. Never bodies, tokens,
    // group ids or client addresses. Healthy health checks (Docker polls every 30 s) log at debug.
    intercept(ApplicationCallPipeline.Monitoring) {
        val started = System.nanoTime()
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header("X-Robots-Tag", "noindex")
        allowOrigin(call, config.corsOrigins)
        try {
            proceed()
        } finally {
            val status = call.response.status()?.value ?: HttpStatusCode.InternalServerError.value
            val millis = (System.nanoTime() - started) / 1_000_000
            val path = template(call.request.path())
            val line = "${call.request.httpMethod.value} $path $status ${millis}ms"
            if (path == "/v1/health" && status == HttpStatusCode.OK.value) log.debug(line) else log.info(line)
        }
    }

    intercept(ApplicationCallPipeline.Plugins) {
        // CORS preflights touch no data and carry no token: answered before the rate limiter, so a
        // browser's sync isn't refused for asking permission first.
        if (call.request.httpMethod == HttpMethod.Options) {
            call.preflight(config.corsOrigins)
            finish()
            return@intercept
        }
        val wait = limiter.acquire(clientKey(call))
        if (wait != null) {
            call.response.header(HttpHeaders.RetryAfter, wait.toString())
            call.error(HttpStatusCode.TooManyRequests, "rate_limited")
            finish()
        }
    }

    install(StatusPages) {
        exception<IOException> { call, cause ->
            log.warn("store unavailable: {}", cause.javaClass.simpleName)
            call.response.header(HttpHeaders.RetryAfter, STORE_RETRY_SECONDS)
            call.error(HttpStatusCode.ServiceUnavailable, "store_unavailable")
        }
        exception<Throwable> { call, cause ->
            log.error("unexpected failure: {}", cause.javaClass.name)
            call.error(HttpStatusCode.InternalServerError, "internal")
        }
    }

    routing {
        get("/v1/health") {
            val ok = store.isAvailable()
            call.respondText(
                """{"ok":$ok,"protocol":$PROTOCOL}""",
                ContentType.Application.Json,
                if (ok) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            )
        }

        route("/v1/groups/{groupId}/document") {
            get {
                val groupId = call.parameters["groupId"].orEmpty()
                if (!GROUP_ID.matches(groupId)) return@get call.error(HttpStatusCode.BadRequest, "bad_group_id")
                if (!store.isAvailable()) return@get call.unavailable()
                val tokenHash = call.bearerToken()?.let(GroupStore::sha256Hex)
                val ifNoneMatch = parseEtags(call.request.headers[HttpHeaders.IfNoneMatch]).orEmpty()
                when (val result = store.get(groupId, tokenHash, ifNoneMatch)) {
                    GetResult.NotFound -> call.error(HttpStatusCode.NotFound, "no_document")
                    GetResult.Unauthorized -> call.unauthorized()
                    is GetResult.NotModified -> {
                        call.response.header(HttpHeaders.ETag, etag(result.rev))
                        call.respondBytes(ByteArray(0), status = HttpStatusCode.NotModified)
                    }
                    is GetResult.Found -> {
                        call.response.header(HttpHeaders.ETag, etag(result.rev))
                        call.respondBytes(result.bytes, DOCUMENT_TYPE)
                    }
                }
            }

            put {
                val groupId = call.parameters["groupId"].orEmpty()
                if (!GROUP_ID.matches(groupId)) return@put call.error(HttpStatusCode.BadRequest, "bad_group_id")
                val token = call.bearerToken() ?: return@put call.unauthorized()
                val precondition = call.precondition()
                    ?: return@put call.error(HttpStatusCode.PreconditionRequired, "precondition_required")
                if ((call.request.contentLength() ?: 0) > config.maxBodyBytes) return@put call.tooLarge()
                if (!store.isAvailable()) return@put call.unavailable()
                val tokenHash = GroupStore.sha256Hex(token)
                store.precheck(groupId, tokenHash, precondition)?.let { return@put call.respondPut(it) }
                // Enforced on the bytes actually received, whatever Content-Length claimed.
                val body = call.receiveChannel().readBuffer(config.maxBodyBytes + 1).readByteArray()
                if (body.size > config.maxBodyBytes) return@put call.tooLarge()
                if (body.isEmpty()) return@put call.error(HttpStatusCode.BadRequest, "empty_body")
                call.respondPut(store.put(groupId, tokenHash, precondition, body))
            }
        }
    }
}

private suspend fun ApplicationCall.respondPut(result: PutResult) = when (result) {
    is PutResult.Created -> {
        response.header(HttpHeaders.ETag, etag(result.rev))
        respondText("""{"rev":${result.rev}}""", ContentType.Application.Json, HttpStatusCode.Created)
    }
    is PutResult.Updated -> {
        response.header(HttpHeaders.ETag, etag(result.rev))
        respondText("""{"rev":${result.rev}}""", ContentType.Application.Json, HttpStatusCode.OK)
    }
    PutResult.Unauthorized -> unauthorized()
    PutResult.PreconditionFailed -> error(HttpStatusCode.PreconditionFailed, "precondition_failed")
    PutResult.Full -> {
        log.warn("refused a new group: the relay is at GOA_RELAY_MAX_GROUPS")
        response.header(HttpHeaders.RetryAfter, FULL_RETRY_SECONDS)
        error(HttpStatusCode.ServiceUnavailable, "relay_full")
    }
}

private suspend fun ApplicationCall.error(status: HttpStatusCode, code: String) =
    respondText("""{"error":"$code"}""", ContentType.Application.Json, status)

private suspend fun ApplicationCall.unauthorized() {
    response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"goa-relay\"")
    error(HttpStatusCode.Unauthorized, "unauthorized")
}

private suspend fun ApplicationCall.tooLarge() {
    // The rest of an oversized body is not read; close rather than drain it.
    response.header(HttpHeaders.Connection, "close")
    error(HttpStatusCode.PayloadTooLarge, "too_large")
}

private suspend fun ApplicationCall.unavailable() {
    response.header(HttpHeaders.RetryAfter, STORE_RETRY_SECONDS)
    error(HttpStatusCode.ServiceUnavailable, "store_unavailable")
}

private fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization]?.trim() ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    return header.substring("Bearer ".length).trim().takeIf { TOKEN.matches(it) }
}

/** `If-Match` wins over `If-None-Match`; only `If-None-Match: *` counts as a PUT precondition. */
private fun ApplicationCall.precondition(): Precondition? {
    val ifMatch = request.headers[HttpHeaders.IfMatch]
    if (ifMatch != null) {
        return if (ifMatch.trim() == "*") Precondition.IfMatch(null) else Precondition.IfMatch(parseEtags(ifMatch).orEmpty())
    }
    return if (request.headers[HttpHeaders.IfNoneMatch]?.trim() == "*") Precondition.IfNoneMatchAny else null
}

/** `"3"`, `"3", "4"` or a bare `3`; anything unparseable simply matches no revision. */
internal fun parseEtags(header: String?): Set<Long>? = header?.split(',')
    ?.mapNotNull { it.trim().removeSurrounding("\"").toLongOrNull()?.takeIf { rev -> rev > 0 } }
    ?.toSet()

internal fun etag(rev: Long) = "\"$rev\""

/** The last `X-Forwarded-For` hop (added by the proxy in front of us), else the peer address. */
internal fun clientKey(call: ApplicationCall): String {
    val forwarded = call.request.headers.getAll(HttpHeaders.XForwardedFor)
        ?.flatMap { it.split(',') }
        ?.map { it.trim() }
        ?.lastOrNull { it.isNotEmpty() }
    return forwarded ?: call.request.local.remoteAddress
}

/**
 * CORS for the web app (ADR-043): a browser page on an allowed origin (`GOA_RELAY_CORS_ORIGINS`,
 * default the GitHub Pages site) may read the relay's answers, every status included, so it sees
 * 401/412/429 and their `ETag`/`Retry-After` like a phone does. Other origins get no CORS headers
 * (the browser then hides the answer). No cookies are involved: the token travels in
 * `Authorization`, so `Access-Control-Allow-Credentials` is never sent.
 */
private fun allowOrigin(call: ApplicationCall, allowed: Set<String>) {
    val origin = call.request.headers[HttpHeaders.Origin] ?: return
    call.response.header(HttpHeaders.Vary, HttpHeaders.Origin)
    if (origin !in allowed) return
    call.response.header(HttpHeaders.AccessControlAllowOrigin, origin)
    call.response.header(HttpHeaders.AccessControlExposeHeaders, CORS_EXPOSED)
}

/**
 * Answers an `OPTIONS` request: `204` with the allowed methods and headers for a preflight from an
 * allowed origin asking for GET or PUT with the protocol's headers on a relay path; `403` otherwise.
 */
private suspend fun ApplicationCall.preflight(allowed: Set<String>) {
    val origin = request.headers[HttpHeaders.Origin]
    val method = request.headers[HttpHeaders.AccessControlRequestMethod]?.trim()
    val headers = request.headers[HttpHeaders.AccessControlRequestHeaders].orEmpty()
        .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    val ok = origin != null && origin in allowed && method in CORS_METHODS &&
        CORS_REQUEST_HEADERS.containsAll(headers) && template(request.path()) != "(other)"
    if (!ok) return error(HttpStatusCode.Forbidden, "cors_refused")
    response.header(HttpHeaders.Vary, "${HttpHeaders.AccessControlRequestMethod}, ${HttpHeaders.AccessControlRequestHeaders}")
    response.header(HttpHeaders.AccessControlAllowMethods, CORS_METHODS.joinToString(", "))
    response.header(HttpHeaders.AccessControlAllowHeaders, CORS_ALLOWED_HEADERS)
    response.header(HttpHeaders.AccessControlMaxAge, CORS_MAX_AGE_SECONDS)
    respondBytes(ByteArray(0), status = HttpStatusCode.NoContent)
}

private val CORS_METHODS = listOf("GET", "PUT")
private const val CORS_ALLOWED_HEADERS = "Authorization, If-Match, If-None-Match, Content-Type"
private val CORS_REQUEST_HEADERS = CORS_ALLOWED_HEADERS.split(", ").map { it.lowercase() }.toSet()
private const val CORS_EXPOSED = "ETag, Retry-After"

/** WebKit caps a cached preflight at 10 minutes; asking for longer gains nothing. */
private const val CORS_MAX_AGE_SECONDS = "600"

internal fun template(path: String): String = when {
    path == "/v1/health" -> path
    DOCUMENT_PATH.matches(path) -> "/v1/groups/{groupId}/document"
    else -> "(other)"
}
