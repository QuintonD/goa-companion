package dev.quinton.goa.hub.web.ui

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * `/static/…`: CSS, JS, fonts and our own icons from the hub's resources (`static/`). URLs carry a
 * content version per file (`?v=`), so browsers may cache them for good and still see a new build
 * at once. A feature keeps its own files under `static/<feature>/` and lists them in
 * [HubPage.styles] / [HubPage.scripts]; nothing here needs editing.
 *
 * Stylesheets are served with their `url(/static/…)` references versioned the same way (fonts,
 * icons, crests), so those are cached for good too; a stylesheet's own version covers them.
 */
object StaticFiles {
    const val PREFIX = "/static"
    const val RESOURCE_ROOT = "static"

    private val versions = ConcurrentHashMap<String, String>()
    private val stylesheets = ConcurrentHashMap<String, ByteArray>()

    /** The versioned URL of a static file ([name] relative to `static/`). */
    fun url(name: String): String = "$PREFIX/$name?v=${versionOf(name)}"

    /** A hash of the file as served (empty for a missing file, which the route answers with 404). */
    fun versionOf(name: String): String = versions.getOrPut(name) {
        val bytes = bytes(name) ?: ByteArray(0)
        MessageDigest.getInstance("SHA-256").digest(bytes).take(VERSION_BYTES).joinToString("") { "%02x".format(it) }
    }

    /** The file as served: as stored, except stylesheets, whose `/static/` references get versions. */
    fun bytes(name: String): ByteArray? {
        if (!name.endsWith(".css")) return raw(name)
        stylesheets[name]?.let { return it }
        val css = raw(name)?.toString(Charsets.UTF_8) ?: return null
        val versioned = STATIC_REF.replace(css) { match ->
            val path = match.groupValues[1]
            if (raw(path) == null) match.value else "url($PREFIX/$path?v=${versionOf(path)})"
        }.toByteArray(Charsets.UTF_8)
        return versioned.also { stylesheets[name] = it }
    }

    private fun raw(name: String): ByteArray? =
        StaticFiles::class.java.classLoader.getResourceAsStream("$RESOURCE_ROOT/$name")?.use { it.readBytes() }

    /** `url(/static/fonts/inter_regular.ttf)`, unquoted, without a query (as app.css writes them). */
    private val STATIC_REF = Regex("""url\($PREFIX/([A-Za-z0-9_./-]+)\)""")

    private const val VERSION_BYTES = 6
}
