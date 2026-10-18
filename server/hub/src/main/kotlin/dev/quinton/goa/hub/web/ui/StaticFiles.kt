package dev.quinton.goa.hub.web.ui

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * `/static/…`: CSS, JS, fonts and our own icons from the hub's resources (`static/`). URLs carry a
 * content version per file (`?v=`), so browsers may cache them for good and still see a new build
 * at once. A feature keeps its own files under `static/<feature>/` and lists them in
 * [HubPage.styles] / [HubPage.scripts]; nothing here needs editing.
 */
object StaticFiles {
    const val PREFIX = "/static"
    const val RESOURCE_ROOT = "static"

    private val versions = ConcurrentHashMap<String, String>()

    /** The versioned URL of a static file ([name] relative to `static/`). */
    fun url(name: String): String = "$PREFIX/$name?v=${versionOf(name)}"

    /** A hash of the file's bytes (empty for a missing file, which the route answers with 404). */
    fun versionOf(name: String): String = versions.getOrPut(name) {
        val bytes = StaticFiles::class.java.classLoader.getResourceAsStream("$RESOURCE_ROOT/$name")
            ?.use { it.readBytes() } ?: ByteArray(0)
        MessageDigest.getInstance("SHA-256").digest(bytes).take(VERSION_BYTES).joinToString("") { "%02x".format(it) }
    }

    private const val VERSION_BYTES = 6
}
