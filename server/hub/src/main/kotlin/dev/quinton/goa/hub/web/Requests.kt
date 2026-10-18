package dev.quinton.goa.hub.web

import dev.quinton.goa.hub.Hub
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import java.net.URI

/*
 * Who is asking. The hub binds 127.0.0.1 and is published by Tailscale Funnel (or Caddy) on the
 * same machine, so EVERY public request arrives from loopback: loopback is never a sign of trust
 * (no admin shortcuts, no first-run page by address). Forwarded headers are used only to tell
 * clients apart for rate limiting, and the hub's own scheme and host come from the configured
 * public URL, never from request headers.
 */

object ClientAddress {
    fun isLoopback(address: String?): Boolean {
        val a = address?.trim()?.removePrefix("[")?.removeSuffix("]")?.lowercase() ?: return false
        return a == "localhost" || a == "::1" || a == "0:0:0:0:0:0:0:1" || a.startsWith("127.") ||
            a == "::ffff:127.0.0.1"
    }
}

internal val HubKey = AttributeKey<Hub>("goa.hub")

/** The [Hub] this application serves. */
val Application.hub: Hub get() = attributes[HubKey]

val ApplicationCall.hub: Hub get() = application.hub

/** The TCP peer (the local proxy, when published through Funnel or Caddy). */
val ApplicationCall.peerAddress: String get() = request.local.remoteAddress

/**
 * The browser's address for rate limiting, or null when it can't be told: the peer when it isn't
 * loopback; behind a local proxy, the last `X-Forwarded-For` hop (the one the proxy itself
 * appended — Caddy and Tailscale's Go reverse proxy both append — so a client can't forge it by
 * sending its own header); null when a loopback peer sends no `X-Forwarded-For`.
 */
val ApplicationCall.clientKey: String?
    get() {
        if (!ClientAddress.isLoopback(peerAddress)) return peerAddress
        return request.headers.getAll(HttpHeaders.XForwardedFor).orEmpty()
            .flatMap { it.split(',') }.map { it.trim() }.lastOrNull { it.isNotEmpty() }
    }

/** For logs: [clientKey], or the peer. */
val ApplicationCall.clientAddress: String get() = clientKey ?: peerAddress

/**
 * The hub's origin as browsers reach it: the configured public URL (`goa-hub config --public-url`,
 * also taken from the invite at `goa-hub join`). Only without one (local tries) is it derived from
 * the `Host` header.
 */
val ApplicationCall.publicOrigin: String
    get() = hub.config.get().publicUrl
        ?: "${request.local.scheme}://${(request.headers[HttpHeaders.Host] ?: request.local.serverHost).lowercase()}"

/** True when the hub is published over HTTPS (from the public URL). */
val ApplicationCall.isHttps: Boolean get() = hub.config.get().publicUrl?.startsWith("https://") == true

/** An htmx request (`HX-Request: true`): answer with a fragment, not a whole page. */
val ApplicationCall.isHtmx: Boolean get() = request.headers["HX-Request"] == "true"

internal val ApplicationCall.isStateChanging: Boolean
    get() = request.local.method !in setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

/**
 * A state-changing request must come from one of the hub's own pages (ADR-030 §7): its `Origin`
 * must be the configured public URL. Without a public URL (local tries only) the `Origin` host must
 * match the `Host` header. Browsers send `Origin` on every POST; a request without one is accepted
 * only when `Sec-Fetch-Site` says same-origin.
 */
internal fun ApplicationCall.isSameOrigin(publicUrl: String?): Boolean {
    val origin = request.headers[HttpHeaders.Origin]?.lowercase()?.removeSuffix("/")
        ?: return request.headers["Sec-Fetch-Site"] == "same-origin"
    if (origin == "null") return false
    if (publicUrl != null) return origin == publicUrl.lowercase()
    val host = (request.headers[HttpHeaders.Host] ?: request.local.serverHost).lowercase().substringBefore(':')
    val originHost = runCatching { URI(origin).host }.getOrNull()
    return originHost != null && originHost == host
}
