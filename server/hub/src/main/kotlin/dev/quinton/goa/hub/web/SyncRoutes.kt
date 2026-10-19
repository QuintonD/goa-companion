package dev.quinton.goa.hub.web

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.HubToken
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.store.InvalidGroupDataException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.InputStream
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * The Android ↔ hub sync API (ADR-030 §4).
 *
 * `POST /api/v1/sync`, `Authorization: Bearer <HubToken>`, body = a sealed sync file
 * (`SyncCodec.encodeFile`, `Content-Type: application/vnd.dev.quinton.goa.sync`). The hub merges
 * it into the group's data and answers `200` with the merged document sealed the same way.
 * `401` wrong token or another group · `409` another `SyncDocument.VERSION` (the text says which
 * side to update) · `422` not a valid sync file or invalid data (the text is `SyncValidation`'s
 * message) · `413` over [MAX_BODY_BYTES] · `503` the hub hasn't joined a group. Idempotent.
 * Bodies are never logged. Optional headers `X-Goa-Device-Id` / `X-Goa-Device-Name` name the
 * phone in "last synced".
 *
 * `GET /api/v1/health` → `200 {"status":"ok"}` (open; for the service manager and Caddy).
 */
internal fun Route.syncRoutes(hub: Hub) {
    get(HEALTH_PATH) {
        call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
    }
    post(SYNC_PATH) {
        val group = hub.membership.group()
        if (group == null) {
            call.respondText(NOT_JOINED, ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
            return@post
        }
        val token = call.request.header(HttpHeaders.Authorization)
            ?.takeIf { it.startsWith(BEARER, ignoreCase = true) }?.substring(BEARER.length)?.trim()
        if (token == null || !HubToken.matches(group, token)) {
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
            call.respondText("Wrong group key.", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
            return@post
        }
        val declared = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        val body = if (declared != null && declared > MAX_BODY_BYTES) {
            null
        } else {
            withContext(Dispatchers.IO) { readLimited(call.receiveChannel().toInputStream(), MAX_BODY_BYTES) }
        }
        if (body == null) {
            call.respondText("The sync data is over 8 MB.", ContentType.Text.Plain, HttpStatusCode.PayloadTooLarge)
            return@post
        }
        call.sync(hub, group, body)
    }
}

private suspend fun ApplicationCall.sync(hub: Hub, group: GroupCredentials, body: ByteArray) {
    envelopeProblem(body, group)?.let { (status, message) ->
        respondText(message, ContentType.Text.Plain, status)
        return
    }
    val incoming = try {
        SyncCodec.decodeFile(body, group)
    } catch (e: SyncFileException) {
        val status = if (e.problem ==
            SyncFileProblem.OTHER_GROUP
        ) {
            HttpStatusCode.Unauthorized
        } else {
            HttpStatusCode.UnprocessableEntity
        }
        respondText(e.message.orEmpty(), ContentType.Text.Plain, status)
        return
    }
    val merged = try {
        val before = hub.groupStore.read()
        hub.groupStore.mergeIn(incoming).also { after ->
            val changes = SyncMerge.changes(before, after)
            log.info("Sync from {}: {}", clientAddress, if (changes.isEmpty) "no changes" else changes.toString())
        }
    } catch (e: InvalidGroupDataException) {
        respondText(e.message.orEmpty(), ContentType.Text.Plain, HttpStatusCode.UnprocessableEntity)
        return
    }
    hub.syncState.record(
        request.header(DEVICE_ID_HEADER),
        request.header(DEVICE_NAME_HEADER)?.let(::deviceName),
        hub.clock.nowMillis(),
    )
    respondBytes(SyncCodec.encodeFile(merged, group, createdAt = hub.clock.nowMillis()), SyncFileType)
}

/**
 * Reads the sealed file's clear envelope first, so another group is a 401 and another document
 * version a 409 (not just "can't open"), with a message saying which side to update.
 */
private fun envelopeProblem(body: ByteArray, group: GroupCredentials): Pair<HttpStatusCode, String>? {
    val envelope = runCatching { SyncJson.parseToJsonElement(body.decodeToString()).jsonObject }.getOrNull()
        ?: return HttpStatusCode.UnprocessableEntity to "This isn't a group sync file."
    val groupId = runCatching { envelope["groupId"]?.jsonPrimitive?.content }.getOrNull()
    val version = runCatching { envelope["documentVersion"]?.jsonPrimitive?.intOrNull }.getOrNull()
        ?: return HttpStatusCode.UnprocessableEntity to "This isn't a group sync file."
    return when {
        groupId != group.groupId -> HttpStatusCode.Unauthorized to "This data is from another group."

        version > SyncDocument.VERSION ->
            HttpStatusCode.Conflict to
                "The app is newer than this hub (sync format $version, hub ${SyncDocument.VERSION}). Update the hub."

        version < SyncDocument.VERSION ->
            HttpStatusCode.Conflict to
                "The app is older than this hub (sync format $version, hub ${SyncDocument.VERSION}). Update the app."

        else -> null
    }
}

/**
 * The phone's name from [DEVICE_NAME_HEADER]: percent-encoded UTF-8, since header values are ASCII
 * (the app sends `Zo%C3%AB`); a value that isn't valid percent-encoding is taken as it is.
 */
internal fun deviceName(header: String): String =
    runCatching { URLDecoder.decode(header.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(header)

/** The stream's bytes, or null if there are more than [limit]. */
internal fun readLimited(input: InputStream, limit: Int): ByteArray? = input.use { stream ->
    val bytes = stream.readNBytes(limit + 1)
    bytes.takeIf { it.size <= limit }
}

internal const val HEALTH_PATH = "/api/v1/health"
internal const val SYNC_PATH = "/api/v1/sync"
internal const val MAX_BODY_BYTES = 8 * 1024 * 1024
internal const val DEVICE_ID_HEADER = "X-Goa-Device-Id"
internal const val DEVICE_NAME_HEADER = "X-Goa-Device-Name"
private const val BEARER = "Bearer "
internal val SyncFileType = ContentType.parse(SyncCodec.FILE_MIME_TYPE)
private val log = LoggerFactory.getLogger("dev.quinton.goa.hub.Sync")
