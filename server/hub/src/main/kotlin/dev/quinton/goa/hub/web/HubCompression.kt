package dev.quinton.goa.hub.web

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.condition
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.request.path

/**
 * gzip for text on a phone's data plan: pages, htmx fragments, CSS, JS, JSON, SVG and the fonts
 * (images are WebP/PNG already). Never for responses that carry the group key — the invite pages
 * and the join page — so their size can't leak it (BREACH); the sync API is sealed binary anyway.
 */
internal fun Application.hubCompression() {
    install(Compression) {
        gzip {
            priority = 1.0
            minimumSize(MIN_BYTES)
            matchContentType(*COMPRESSIBLE)
            condition { !request.path().let { it.startsWith(INVITE_PREFIX) || it == JOIN_PATH } }
        }
        deflate {
            priority = 0.5
            minimumSize(MIN_BYTES)
            matchContentType(*COMPRESSIBLE)
            condition { !request.path().let { it.startsWith(INVITE_PREFIX) || it == JOIN_PATH } }
        }
    }
}

private val COMPRESSIBLE = arrayOf(
    ContentType.Text.Html,
    ContentType.Text.CSS,
    ContentType.Text.JavaScript,
    ContentType.Text.Plain,
    ContentType.Application.Json,
    ContentType.Image.SVG,
    ContentType("application", "manifest+json"),
    ContentType("font", "ttf"),
)

private const val MIN_BYTES = 1024L
private const val INVITE_PREFIX = "/group/invite"
