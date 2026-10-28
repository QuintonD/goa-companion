package dev.quinton.goa.web.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The site's Content-Security-Policy lets the page connect to `goa.syncServer` only (the site task
 * reads gradle.properties): the web app's built-in server must be that same origin, or every sync
 * would be refused by the browser.
 */
class WebSyncDefaultsTest {
    @Test
    fun theBuiltInServerIsTheOneThePolicyAllows() {
        val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
        val fs = js("require('fs')")
        val properties = fs.readFileSync("$root/gradle.properties", "utf8") as String
        val configured = properties.lines().map { it.trim() }
            .single { it.startsWith("goa.syncServer=") }
            .substringAfter('=').trim().trimEnd('/')
        assertEquals(configured, WebSyncDefaults.DEFAULT_URL)
    }
}
