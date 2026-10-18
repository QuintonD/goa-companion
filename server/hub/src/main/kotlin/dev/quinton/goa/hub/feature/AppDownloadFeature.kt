package dev.quinton.goa.hub.feature

import dev.quinton.goa.hub.web.hub
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalPathContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.p

/**
 * "Get the Android app" (ADR-030 §11): the latest signed release APK, copied to
 * `<data>/app/goa.apk` by the deploy script, for signed-in devices only. Still private (ADR-006).
 */
object AppDownloadFeature : HubFeature {
    const val PATH = "/app"
    const val DOWNLOAD_PATH = "/app/goa.apk"
    val ApkType = ContentType("application", "vnd.android.package-archive")

    override fun Route.routes(ctx: HubContext) {
        get(PATH) {
            val apk = call.hub.paths.apk
            call.respondPage(HubPage("Get the Android app", back = "/settings")) {
                if (!Files.isRegularFile(apk)) {
                    emptyState(
                        "No app uploaded yet",
                        "The group's Android app hasn't been put on this hub yet. Ask whoever runs the hub.",
                    )
                    return@respondPage
                }
                val info = ApkInfo.read(apk)
                card {
                    p {
                        +"The Guards of Atlantis companion for Android, built for this group. Install it from the file."
                    }
                    dl("facts") {
                        dt { +"Version" }
                        dd { +versionText(info) }
                        dt { +"Size" }
                        dd { +megabytes(Files.size(apk)) }
                        dt { +"Uploaded" }
                        dd { +date(Files.getLastModifiedTime(apk).toInstant(), ctx.clock.zone) }
                    }
                    linkButton("Download the app", DOWNLOAD_PATH, ButtonStyle.PRIMARY, Icons.DOWNLOAD)
                }
            }
        }
        get(DOWNLOAD_PATH) {
            val apk = call.hub.paths.apk
            if (!Files.isRegularFile(apk)) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val version = ApkInfo.read(apk).versionName?.replace(Regex("[^A-Za-z0-9._-]"), "")
            val fileName = if (version.isNullOrEmpty()) "goa.apk" else "goa-$version.apk"
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    fileName,
                ).toString(),
            )
            call.respond(LocalPathContent(apk, ApkType))
        }
    }

    private fun versionText(info: ApkInfo): String {
        val name = info.versionName ?: return "Unknown"
        return info.versionCode?.let { "$name ($it)" } ?: name
    }

    private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))

    private fun date(instant: Instant, zone: ZoneId) =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK).withZone(zone).format(instant)
}
