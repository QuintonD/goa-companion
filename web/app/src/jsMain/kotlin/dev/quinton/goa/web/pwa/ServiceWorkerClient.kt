package dev.quinton.goa.web.pwa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.js.Date
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Registers `sw.js` and tells the UI when a new build is waiting ([updateReady]); [applyUpdate]
 * switches to it and reloads. The new build never takes over by itself, so a game in progress isn't
 * reloaded under the person's fingers. Not registered on the dev server ([BuildInfo.isDev]).
 */
class ServiceWorkerClient {
    var updateReady by mutableStateOf(false)
        private set

    /** True once a service worker controls the page: the shell and catalog work offline. */
    var offlineReady by mutableStateOf(false)
        private set

    private var registration: dynamic = null
    private var reloading = false
    private var lastCheck = 0.0

    fun start() {
        val container = window.navigator.asDynamic().serviceWorker
        if (BuildInfo.isDev || container == null || container == undefined) return
        offlineReady = container.controller != null
        container.addEventListener("controllerchange", { _: dynamic ->
            offlineReady = true
            if (reloading) window.location.reload()
        })
        val register = {
            container.register("sw.js").then({ reg: dynamic ->
                registration = reg
                watch(reg, container)
                null
            }, { _: dynamic -> null })
        }
        // A returning visit registers at once (that is the update check). The first visit waits until
        // the first screen has loaded: installing precaches the whole shell (megabytes), which would
        // otherwise share a slow link with the screen the person is looking at.
        if (container.controller != null) register() else afterFirstScreen { register() }
        // Home-screen apps can stay open for days: look for a new build when brought back.
        document.addEventListener("visibilitychange", {
            if (document.asDynamic().visibilityState == "visible") checkForUpdate()
        })
    }

    /** Switches to the waiting build and reloads the page. */
    fun applyUpdate() {
        val waiting = registration?.waiting
        if (waiting == null || waiting == undefined) {
            window.location.reload()
            return
        }
        reloading = true
        val message: dynamic = js("({})")
        message.type = "SKIP_WAITING"
        waiting.postMessage(message)
    }

    /** Asks the browser to look for a new `sw.js` now (at most every 30 minutes unless [force]). */
    fun checkForUpdate(force: Boolean = false) {
        val now = Date.now()
        if (registration == null || (!force && now - lastCheck < CHECK_INTERVAL_MS)) return
        lastCheck = now
        registration.update().catch { _: dynamic -> null }
    }

    private fun watch(reg: dynamic, container: dynamic) {
        if (reg.waiting != null && container.controller != null) updateReady = true
        reg.addEventListener("updatefound", { _: dynamic ->
            val installing = reg.installing
            // An update only if a worker already controlled the page when this one was found: on the
            // first visit the new worker claims the page itself, and WebKit can report "installed"
            // after that claim (a spurious "new version" prompt on an iPhone's first visit).
            val isUpdate = container.controller != null
            installing?.addEventListener("statechange", { _: dynamic ->
                if (isUpdate && installing.state == "installed") updateReady = true
            })
        })
    }

    /**
     * Runs [action] once the page has loaded and the first screen has settled: drawn (no spinner), the
     * images on screen finished and no request finished since the last check (checked every
     * [SETTLE_POLL_MS]; two quiet checks in a row), or after [SETTLE_MAX_MS] whatever happens.
     */
    private fun afterFirstScreen(action: () -> Unit) {
        var quiet = 0
        var waited = 0
        var requests = -1
        fun poll() {
            waited += SETTLE_POLL_MS
            val finished = (window.performance.asDynamic().getEntriesByType("resource").length as? Int) ?: 0
            quiet = if (finished == requests && firstScreenSettled()) quiet + 1 else 0
            requests = finished
            if (quiet >= 2 || waited >= SETTLE_MAX_MS) action() else window.setTimeout({ poll() }, SETTLE_POLL_MS)
        }
        val start = { window.setTimeout({ poll() }, SETTLE_POLL_MS) }
        if (document.readyState.toString() == "complete") {
            start()
        } else {
            window.addEventListener("load", { start() }, js("({ once: true })"))
        }
    }

    private fun firstScreenSettled(): Boolean {
        // The first screen draws after the catalog loads: a spinner or an empty page isn't settled yet.
        if (document.querySelector(".goa-topbar-title") == null || document.querySelector(".goa-spinner") != null) {
            return false
        }
        val height = window.innerHeight
        val images = document.querySelectorAll("img")
        for (i in 0 until images.length) {
            val img = images.item(i).asDynamic()
            val box = img.getBoundingClientRect()
            val onScreen = (box.bottom as Double) > 0 && (box.top as Double) < height && (box.width as Double) > 0
            if (onScreen && img.complete != true) return false
        }
        return true
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000.0
        const val SETTLE_POLL_MS = 500
        const val SETTLE_MAX_MS = 8_000
    }
}
