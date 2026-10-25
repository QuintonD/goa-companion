package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.SyncServerDefaults

/**
 * The web app's sync server: the group relay on oldboi (docs/SYNC-SERVER.md), the same default as
 * the phones' release builds (`goa.syncServer` in gradle.properties). Plain HTTP to a test relay on
 * this machine is allowed only when the page itself is served from this machine (development and
 * the end-to-end suite), as the phone's debug builds allow it.
 */
object WebSyncDefaults {
    const val DEFAULT_URL = "https://oldboi.taila4d38d.ts.net:8443"

    fun forThisPage(): SyncServerDefaults = SyncServerDefaults(DEFAULT_URL, allowLocalHttp = pageIsLocal())

    private fun pageIsLocal(): Boolean =
        js("typeof location !== 'undefined' && (location.hostname === 'localhost' || location.hostname === '127.0.0.1')")
            as Boolean
}
