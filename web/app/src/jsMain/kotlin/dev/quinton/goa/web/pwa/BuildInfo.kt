package dev.quinton.goa.web.pwa

import kotlinx.browser.document

/**
 * This build's id and version, stamped into index.html's `goa-build` / `goa-version` meta tags when
 * the site is assembled (`:web:app:site`). The dev server serves the unstamped page: [isDev].
 */
object BuildInfo {
    val build: String get() = meta("goa-build")
    val version: String get() = meta("goa-version").takeUnless { it.startsWith("__") } ?: "dev"
    val isDev: Boolean get() = build.isEmpty() || build.startsWith("__")

    private fun meta(name: String): String =
        document.querySelector("meta[name=\"$name\"]")?.getAttribute("content").orEmpty()
}
