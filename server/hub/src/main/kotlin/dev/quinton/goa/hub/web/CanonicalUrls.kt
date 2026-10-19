package dev.quinton.goa.hub.web

import io.ktor.http.HttpHeaders
import io.ktor.http.parseQueryString
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect

/**
 * Keeps a page's address tidy when a GET form sends every field (`/heroes?q=&a=&sort=name`):
 * [canonical] is the same state without empty or default parameters. An htmx request is told to
 * push [canonical] instead of the request's URL (`HX-Push-Url`) and goes on; a plain request is
 * redirected to it, and this returns true — the caller then stops.
 */
suspend fun ApplicationCall.tidyUrl(canonical: String): Boolean {
    val wanted = parseQueryString(canonical.substringAfter('?', ""))
    val path = canonical.substringBefore('?')
    if (request.path() == path && request.queryParameters.entries().toSet() == wanted.entries().toSet()) return false
    if (isHtmx) {
        // Call this only for swaps that push their URL (the search forms' hx-push-url).
        response.header(HX_PUSH_URL, canonical)
        return false
    }
    response.header(HttpHeaders.CacheControl, "no-store")
    respondRedirect(canonical)
    return true
}

private const val HX_PUSH_URL = "HX-Push-Url"
