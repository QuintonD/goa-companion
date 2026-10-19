package dev.quinton.goa.hub.settings

import dev.quinton.goa.core.domain.backup.BackupCodec
import dev.quinton.goa.core.domain.backup.BackupDocument
import dev.quinton.goa.core.domain.backup.BackupFiles
import dev.quinton.goa.core.domain.backup.InvalidBackupException
import dev.quinton.goa.core.domain.backup.summary
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.presentation.group.SyncWords
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.settings.SettingsFeature.BACKUP_PATH
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.store.InvalidGroupDataException
import dev.quinton.goa.hub.web.BACKUP_IMPORT_PATH
import dev.quinton.goa.hub.web.MAX_BODY_BYTES
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.readLimited
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.checkboxField
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p

/** Settings' own per-browser state. */
object SettingsSlots {
    /** A backup file that was read and checked, waiting for "Merge" (or Cancel). */
    val PendingImport = DeviceSlot("backup-import", BackupDocument.serializer())
}

/**
 * Backup and restore on the web (ADR-020 §5, ADR-025 §8): the app's backup file (`BackupFiles`),
 * exported from the group's data plus this browser's settings, and imported with the app's Merge
 * rule (later edit wins, like a sync) after a preview of what would change. Replace isn't offered:
 * the hub holds the whole group's data, and the phones would bring back whatever it wiped.
 */
object WebBackup {
    /** The backup file of the group's data, with this browser's settings as the `settings` extra. */
    fun export(ctx: HubContext, deviceId: String): BackupDocument {
        val settings = SettingsFeature.settings(ctx, deviceId)
            .copy(theme = SettingsFeature.modeOf(SettingsFeature.theme(ctx, deviceId)))
        return BackupFiles.export(
            ctx.groupStore.read(),
            exportedAt = ctx.clock.nowMillis(),
            appVersion = "hub ${hubVersion()}",
            extras = mapOf(BackupFiles.SETTINGS_KEY to BackupFiles.settingsJson(settings)),
        )
    }

    /** What merging [document] would change in the group (records only, as the phone's Merge). */
    fun preview(ctx: HubContext, document: BackupDocument) =
        ctx.groupStore.read().let { before -> SyncMerge.changes(before, BackupFiles.merge(before, document)) }

    /**
     * Merges [document] into the group like a sync. [useSettings]: also take the file's rating
     * constants (a change made now, as on the phone) and its app settings for this browser.
     */
    suspend fun merge(ctx: HubContext, deviceId: String, document: BackupDocument, useSettings: Boolean) {
        ctx.groupStore.mergeIn(BackupFiles.records(document))
        if (!useSettings) return
        val config = BackupFiles.eloConfig(document)
        if (config != ctx.groupStore.data().eloConfig) ctx.groupStore.updateEloConfig { config }
        document.extras[BackupFiles.SETTINGS_KEY]?.let(BackupFiles::settings)?.let { settings ->
            SettingsFeature.store(ctx, deviceId, settings)
        }
    }

    fun fileName(now: Long, ctx: HubContext): String =
        "goa-backup-${DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(now).atZone(ctx.clock.zone))}.json"

    private fun hubVersion(): String = Hub::class.java.`package`?.implementationVersion ?: "dev"
}

internal fun Route.backupRoutes(ctx: HubContext) {
    get(BACKUP_PATH) { call.respondBackupPage(ctx) }
    get("$BACKUP_PATH/export") {
        val document = WebBackup.export(ctx, call.device.id)
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                WebBackup.fileName(document.exportedAt, ctx),
            ).toString(),
        )
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondText(BackupCodec.encode(document), ContentType.Application.Json.withParameter("charset", "utf-8"))
    }
    post(BACKUP_IMPORT_PATH) {
        val text = call.receiveBackupText()
            ?: return@post call.respondBackupPage(ctx, "Choose a backup file (up to 8 MB).", HttpStatusCode.BadRequest)
        val document = try {
            BackupCodec.decode(text)
        } catch (e: InvalidBackupException) {
            return@post call.respondBackupPage(ctx, e.message, HttpStatusCode.UnprocessableEntity)
        }
        ctx.deviceStore.write(call.device.id, SettingsSlots.PendingImport, document)
        call.respondImportPreview(ctx, document)
    }
    post("$BACKUP_PATH/import/confirm") {
        val useSettings = call.receiveParameters()["useSettings"] == "true"
        val id = call.device.id
        val document = ctx.deviceStore.read(id, SettingsSlots.PendingImport)
            ?: return@post call.respondBackupPage(
                ctx,
                "Nothing to import: choose the file again.",
                HttpStatusCode.Conflict,
            )
        ctx.deviceStore.delete(id, SettingsSlots.PendingImport)
        val changes = WebBackup.preview(ctx, document)
        try {
            WebBackup.merge(ctx, id, document, useSettings)
        } catch (e: InvalidGroupDataException) {
            return@post call.respondBackupPage(ctx, e.message, HttpStatusCode.UnprocessableEntity)
        }
        call.respondBackupPage(ctx, done = "Merged: ${SyncWords.summary(changes)}. Ratings recalculated.")
    }
    post("$BACKUP_PATH/import/cancel") {
        ctx.deviceStore.delete(call.device.id, SettingsSlots.PendingImport)
        call.respondBackupPage(ctx)
    }
}

/** The uploaded file's text (the multipart `file` part), or null when there is none or it is over 8 MB. */
private suspend fun ApplicationCall.receiveBackupText(): String? {
    if (request.contentType().withoutParameters() != ContentType.MultiPart.FormData) return null
    val declared = request.header(HttpHeaders.ContentLength)?.toLongOrNull()
    if (declared != null && declared > MAX_BODY_BYTES + MULTIPART_SLACK) return null
    var file: ByteArray? = null
    var read = false
    try {
        receiveMultipart(formFieldLimit = MAX_BODY_BYTES + MULTIPART_SLACK).forEachPart { part ->
            if (!read && part is PartData.FileItem && part.name == "file") {
                read = true
                // Over the limit reads as null: the same 8 MB as the sync API.
                file = withContext(Dispatchers.IO) { readLimited(part.provider().toInputStream(), MAX_BODY_BYTES) }
            }
            part.release()
        }
    } catch (_: IOException) {
        // A body over the multipart limit, or a broken upload: as no file.
        return null
    }
    return file?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
}

private suspend fun ApplicationCall.respondBackupPage(
    ctx: HubContext,
    error: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
    done: String? = null,
) {
    val data = ctx.groupStore.data()
    respondPage(settingsPage("Backup and restore"), status) {
        error?.let { errorState(it) }
        done?.let { notice(it) }
        card {
            h2 { +"Export" }
            p {
                +"Save every player, match and draft of the group, its rating settings and this browser's settings "
                +"to one JSON file: the same backup file as the Android app's, so a phone can import it too."
            }
            p("muted small") {
                +"${count(data.players(includeArchived = true).size, "player")} · "
                +count(data.matches.size, "match", "matches")
                +". Timer presets and draft methods made in this browser aren't in it yet."
            }
            div {
                linkButton("Download backup", "$BACKUP_PATH/export", ButtonStyle.PRIMARY, Icons.DOWNLOAD)
            }
        }
        card {
            h2 { +"Import" }
            p {
                +"Merge a backup file (from the app or from here) into the group. You'll see what would change "
                +"first. Items in both are kept from whichever copy was edited last, like a sync."
            }
            postForm(BACKUP_IMPORT_PATH, classes = "stack") {
                encType = FormEncType.multipartFormData
                div("field") {
                    label {
                        htmlFor = "backup-file"
                        +"Backup file"
                    }
                    input(type = InputType.file, name = "file") {
                        id = "backup-file"
                        accept = "application/json,.json"
                        required = true
                    }
                }
                div { hubButton("Check the file", ButtonStyle.TONAL, icon = "upload") }
            }
            p("muted small") {
                +"Replace isn't offered on the web: the hub holds the whole group's data, and the phones would bring "
                +"back anything it wiped at their next sync."
            }
        }
    }
}

/** The preview before a Merge: what the file holds and what would change in the group. */
private suspend fun ApplicationCall.respondImportPreview(ctx: HubContext, document: BackupDocument) {
    val known = ctx.catalog.catalog.heroes.mapTo(HashSet()) { it.id }
    val summary = document.summary(known)
    val changes = WebBackup.preview(ctx, document)
    respondPage(settingsPage("Import backup?", back = BACKUP_PATH)) {
        card {
            h2 { +"Import backup?" }
            p {
                +"Exported ${day(summary.exportedAt, ctx)} · ${count(summary.players, "player")} · "
                +"${count(summary.matches, "match", "matches")} · ${count(summary.drafts, "draft")}"
                if (summary.hasEloConfig) +" · custom rating settings"
            }
            if (summary.unknownHeroes.isNotEmpty()) {
                val one = summary.unknownHeroes.size == 1
                p("muted small") {
                    val verb = if (one) "isn't" else "aren't"
                    val their = if (one) "Its" else "Their"
                    +"${count(summary.unknownHeroes.size, "hero", "heroes")} in this file $verb in this hub's catalog "
                    +"(${summary.unknownHeroes.joinToString()}). $their seats show as Unknown hero "
                    +"until the hub is updated."
                }
            }
            sectionHeader("What would change in the group")
            p("lead") { +SyncWords.summary(changes) }
            p("muted small") {
                +"Merging adds what's new and keeps the later edit of anything in both. Every phone gets it at its "
                +"next sync."
            }
            postForm("$BACKUP_PATH/import/confirm", classes = "stack") {
                checkboxField(
                    "useSettings",
                    "Use the file's settings: rating settings" +
                        (if (summary.hasEloConfig) " (custom in this file)" else "") +
                        " for the group, and theme, owned products and sounds for this browser. " +
                        "Off keeps the current ones.",
                    checked = false,
                )
                div("actions end") {
                    hubButton("Merge", ButtonStyle.PRIMARY)
                }
            }
            postForm("$BACKUP_PATH/import/cancel", classes = "actions end") {
                hubButton("Cancel", ButtonStyle.TEXT)
            }
        }
    }
}

private fun day(epochMillis: Long, ctx: HubContext): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ctx.clock.zone))

internal fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

private const val MULTIPART_SLACK = 64 * 1024L
