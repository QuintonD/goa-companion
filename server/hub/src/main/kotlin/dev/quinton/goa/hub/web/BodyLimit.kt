package dev.quinton.goa.hub.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/*
 * Request bodies are capped before anything reads them. Ktor has no default limit, and `POST /join`
 * is open to the whole internet: without a cap, one request with a few hundred MB of form data
 * fills the heap and wedges the hub. Forms and JSON get [FORM_BODY_LIMIT]; the two routes that take
 * files (the sync API and the backup upload) read their bodies themselves, within 8 MB.
 */

/** Forms, htmx posts and the push API's JSON: far more than any page sends. */
internal const val FORM_BODY_LIMIT = 256 * 1024

/** Paths whose handlers read the body themselves, each within [MAX_BODY_BYTES]. */
private val FILE_BODY_PATHS = setOf(SYNC_PATH, BACKUP_IMPORT_PATH)

internal const val BACKUP_IMPORT_PATH = "/settings/backup/import"

/** The body limit of [call]'s route, or null where the handler applies its own. */
internal fun bodyLimit(call: ApplicationCall): Int? =
    if (call.request.path() in FILE_BODY_PATHS) null else FORM_BODY_LIMIT

/** True when the declared `Content-Length` is already over [call]'s limit (answered with 413 at once). */
internal fun declaresTooLarge(call: ApplicationCall): Boolean {
    val limit = bodyLimit(call) ?: return false
    val declared = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: return false
    return declared > limit
}

/**
 * Reads at most the limit (plus one byte) of every body a handler receives; more is a
 * [PayloadTooLargeException] (413). Covers chunked bodies, which declare no length.
 */
internal fun Application.limitRequestBodies() {
    receivePipeline.intercept(ApplicationReceivePipeline.Before) { body ->
        val channel = body as? ByteReadChannel ?: return@intercept
        val limit = bodyLimit(context) ?: return@intercept
        val bytes = channel.readRemaining(limit + 1L).readByteArray()
        if (bytes.size > limit) throw PayloadTooLargeException(limit.toLong())
        proceedWith(ByteReadChannel(bytes))
    }
}
