package dev.quinton.goa.hub.web.ui

import java.security.MessageDigest

/**
 * `/static/…`: CSS, JS, fonts and our own icons from the hub's resources (`static/`). URLs carry a
 * content version (`?v=`), so browsers may cache them for good and still see a new build at once.
 */
object StaticFiles {
    const val PREFIX = "/static"
    const val RESOURCE_ROOT = "static"

    /** A hash of the files a page loads first; changes with every build that changes them. */
    val version: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf("app.css", "hub.js", "htmx.min.js", "push.js", "join.js").forEach { name ->
            StaticFiles::class.java.classLoader.getResourceAsStream("$RESOURCE_ROOT/$name")?.use {
                digest.update(it.readBytes())
            }
        }
        digest.digest().take(VERSION_BYTES).joinToString("") { "%02x".format(it) }
    }

    /** The versioned URL of a static file ([name] relative to `static/`). */
    fun url(name: String): String = "$PREFIX/$name?v=$version"

    private const val VERSION_BYTES = 6
}
