package dev.quinton.goa.hub.web

import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.config.HubConfig
import dev.quinton.goa.hub.web.ui.IconRenderer
import dev.quinton.goa.hub.web.ui.StaticFiles
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFileExtension
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Files anyone may fetch (ADR-030 §7): `/static/` (CSS, JS, fonts, our own icons), the service
 * worker, the web app manifest and its icons, and `/.well-known/assetlinks.json`. No game data
 * here: card images and the catalog are under [assetRoutes], for signed-in devices only.
 */
internal fun Route.openStaticRoutes(hub: Hub) {
    get("${StaticFiles.PREFIX}/{path...}") {
        val path = HubCatalog.clean(call.parameters.getAll("path").orEmpty().joinToString("/"))
        val bytes = path?.let { resource("${StaticFiles.RESOURCE_ROOT}/$it") }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        // Versioned URLs (?v=) change with the content; unversioned ones (fonts, icons) revalidate daily.
        val maxAge = if (call.request.queryParameters["v"] != null) IMMUTABLE else DAY
        call.response.header(HttpHeaders.CacheControl, "public, max-age=$maxAge")
        call.respondBytes(bytes, contentTypeOf(path))
    }
    get("/sw.js") {
        val bytes = resource("${StaticFiles.RESOURCE_ROOT}/sw.js") ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.response.header("Service-Worker-Allowed", "/")
        call.respondBytes(bytes, ContentType.Text.JavaScript)
    }
    get("/manifest.webmanifest") {
        call.response.header(HttpHeaders.CacheControl, "public, max-age=$DAY")
        call.respondText(manifest(), ContentType("application", "manifest+json"))
    }
    get("/icons/{name}") {
        val icon = IconRenderer.Icon.byFileName(call.parameters["name"].orEmpty())
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondIcon(icon)
    }
    get("/apple-touch-icon.png") { call.respondIcon(IconRenderer.Icon.APPLE_TOUCH) }
    get("/favicon.ico") { call.respondIcon(IconRenderer.Icon.FAVICON) }
    get("/.well-known/assetlinks.json") {
        call.respondText(assetLinks(hub.config.get()), ContentType.Application.Json)
    }
}

/** The bundled catalog and official images, `/assets/<asset path>`: signed-in devices only (ADR-006). */
internal fun Route.assetRoutes(hub: Hub) {
    get("${HubCatalog.URL_PREFIX}/{path...}") {
        val path = HubCatalog.clean(call.parameters.getAll("path").orEmpty().joinToString("/"))
        val bytes = path?.let { p -> hub.catalog.open(p)?.use { it.readBytes() } }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.header(HttpHeaders.CacheControl, "private, max-age=$WEEK")
        call.respondBytes(bytes, contentTypeOf(path))
    }
}

private suspend fun ApplicationCall.respondIcon(icon: IconRenderer.Icon) {
    val png = IconRenderer.png(icon) ?: return respond(HttpStatusCode.NotFound)
    response.header(HttpHeaders.CacheControl, "public, max-age=$DAY")
    respondBytes(png, ContentType.Image.PNG)
}

private fun resource(path: String): ByteArray? = Hub::class.java.classLoader.getResourceAsStream(path)?.use {
    it.readBytes()
}

private fun contentTypeOf(path: String): ContentType {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "js" -> ContentType.Text.JavaScript.withParameter("charset", "utf-8")
        "css" -> ContentType.Text.CSS.withParameter("charset", "utf-8")
        "svg" -> ContentType.Image.SVG
        "webp" -> ContentType("image", "webp")
        "ttf" -> ContentType("font", "ttf")
        "json" -> ContentType.Application.Json
        "txt", "md" -> ContentType.Text.Plain.withParameter("charset", "utf-8")
        else -> ContentType.defaultForFileExtension(ext)
    }
}

/** The web app manifest: installable from Safari's Share → Add to Home Screen. */
internal fun manifest(): String = buildJsonObject {
    put("name", "Guards of Atlantis")
    put("short_name", "GoA")
    put("description", "The group's Guards of Atlantis II companion")
    put("id", "/")
    put("start_url", "/")
    put("scope", "/")
    put("display", "standalone")
    // "any": the running timer has a landscape layout, as on the phone.
    put("orientation", "any")
    put("background_color", "#0B1622")
    put("theme_color", "#0B1622")
    putJsonArray("icons") {
        IconRenderer.Icon.entries.filter { it.inManifest }.forEach { icon ->
            addJsonObject {
                put("src", "/icons/${icon.fileName}")
                put("sizes", "${icon.size}x${icon.size}")
                put("type", "image/png")
                put("purpose", if (icon.maskable) "maskable" else "any")
            }
        }
        addJsonObject {
            put("src", "/static/icons/brand_mark.svg")
            put("sizes", "any")
            put("type", "image/svg+xml")
            put("purpose", "any")
        }
    }
}.toString()

/** Digital Asset Links: invite links open the Android app where it is installed (ADR-030 §6). */
internal fun assetLinks(config: HubConfig): String {
    val fingerprints = config.androidCertSha256.mapNotNull(HubConfig::normalizeFingerprint)
    if (fingerprints.isEmpty()) return "[]"
    return buildJsonArray {
        addJsonObject {
            putJsonArray("relation") { add("delegate_permission/common.handle_all_urls") }
            putJsonObject("target") {
                put("namespace", "android_app")
                put("package_name", HubConfig.ANDROID_PACKAGE)
                putJsonArray("sha256_cert_fingerprints") { fingerprints.forEach { add(it) } }
            }
        }
    }.toString()
}

private const val DAY = 86_400
private const val WEEK = 7 * DAY
private const val IMMUTABLE = 365 * DAY
