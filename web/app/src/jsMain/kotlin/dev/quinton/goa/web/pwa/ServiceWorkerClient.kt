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
        container.register("sw.js").then({ reg: dynamic ->
            registration = reg
            watch(reg, container)
            null
        }, { _: dynamic -> null })
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

    private companion object {
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000.0
    }
}
