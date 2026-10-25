package dev.quinton.goa.web.group

import dev.quinton.goa.core.data.sync.RelayHttp
import dev.quinton.goa.core.data.sync.RelayNetworkException
import dev.quinton.goa.core.data.sync.RelayNetworkFailure
import dev.quinton.goa.core.data.sync.RelayRequest
import dev.quinton.goa.core.data.sync.RelayResponse
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await

/**
 * [RelayHttp] over the browser's `fetch` (ADR-043), configured like the phone's OkHttp client: no
 * redirects followed (a 3xx is shown as one, so the bearer token never goes elsewhere), no cookies,
 * no HTTP cache, no referrer; every request ends within [timeoutMs]. The relay allows the Pages origin
 * by CORS (docs/SYNC-SERVER.md), so 401/412/429 and their `ETag`/`Retry-After` are readable here too.
 * A browser can't tell a refused connection from a CORS refusal or an untrusted certificate: they
 * all read as unreachable.
 */
class FetchRelayHttp(private val timeoutMs: Int = DEFAULT_TIMEOUT_MS) : RelayHttp {
    @Suppress("TooGenericExceptionCaught") // fetch rejects with plain JS errors
    override suspend fun <T> exchange(request: RelayRequest, read: suspend (RelayResponse) -> T): T {
        if (request.url.startsWith("http:") && pageIsHttps()) {
            throw RelayNetworkException(RelayNetworkFailure.HTTPS_ONLY)
        }
        val controller: dynamic = js("new AbortController()")
        var timedOut = false
        val timer = setTimeout({
            timedOut = true
            controller.abort()
        }, timeoutMs)
        try {
            val response = try {
                fetch(request.url, init(request, controller.signal)).await()
            } catch (e: CancellationException) {
                controller.abort()
                throw e
            } catch (e: Throwable) {
                throw RelayNetworkException(
                    if (timedOut) RelayNetworkFailure.TIMEOUT else RelayNetworkFailure.UNREACHABLE,
                    e,
                )
            }
            return read(Answer(response) { timedOut })
        } finally {
            clearTimeout(timer)
        }
    }

    private class Answer(private val response: dynamic, private val timedOut: () -> Boolean) : RelayResponse {
        // `redirect: "manual"` hands back an opaque answer with status 0 for any redirect.
        override val code: Int
            get() = if (response.type == "opaqueredirect") HTTP_FOUND else (response.status as Int)

        override fun header(name: String): String? = (response.headers.get(name) as String?)

        @Suppress("TooGenericExceptionCaught")
        override suspend fun body(max: Long): ByteArray? {
            val declared = header("Content-Length")?.toLongOrNull()
            if (declared != null && declared > max) return null
            val stream = response.body ?: return ByteArray(0)
            return try {
                readAll(stream, max.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw RelayNetworkException(
                    if (timedOut()) RelayNetworkFailure.TIMEOUT else RelayNetworkFailure.UNREACHABLE,
                    e,
                )
            }
        }
    }

    private fun init(request: RelayRequest, signal: dynamic): dynamic {
        val headers: dynamic = js("({})")
        request.headers.forEach { (name, value) -> headers[name] = value }
        request.contentType?.let { headers["Content-Type"] = it }
        val init: dynamic = js("({})")
        init.method = request.method
        init.headers = headers
        request.body?.let { init.body = view(it) }
        init.redirect = "manual"
        init.credentials = "omit"
        init.cache = "no-store"
        init.referrerPolicy = "no-referrer"
        init.mode = "cors"
        init.signal = signal
        return init
    }

    private fun pageIsHttps(): Boolean = js("typeof location !== 'undefined' && location.protocol === 'https:'") as Boolean

    companion object {
        /** OkHttp's call timeout on the phone is 90 s with 30 s per read; one bound here. */
        const val DEFAULT_TIMEOUT_MS = 60_000
        private const val HTTP_FOUND = 302
    }
}

private external fun fetch(url: String, init: dynamic): Promise<dynamic>

private external fun setTimeout(handler: () -> Unit, timeout: Int): Int

private external fun clearTimeout(id: Int)
