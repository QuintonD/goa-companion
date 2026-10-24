package dev.quinton.goa.feature.sync.server

import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.domain.sync.SyncServerAddress
import dev.quinton.goa.feature.sync.SyncText

/** The Sync server section of the Group screen (ADR-036). */
data class SyncServerUi(
    /** The server's origin; null = no server known for this group on this phone. */
    val url: String? = null,
    /** The address was chosen for this group (not the app's built-in one). */
    val custom: Boolean = false,
    /** The app's built-in server, for "Use the app's server"; null = the build has none. */
    val defaultUrl: String? = null,
    /** "Sync through the server". */
    val enabled: Boolean = true,
    val syncing: Boolean = false,
    /** "Synced 2 min ago", "Not synced yet", "Syncing…", "Off". */
    val status: String? = null,
    /** What went wrong at the last sync, in words. */
    val error: String? = null,
    /** "5 min ago": when it went wrong. */
    val errorAgo: String? = null,
    /** The problem may pass on its own, and the phone keeps trying. */
    val retrying: Boolean = false,
    /** Why the typed address can't be used. */
    val addressError: String? = null,
) {
    /** The address without `https://`, as people type and say it. */
    val host: String? get() = url?.let(SyncServerAddress::display)

    val defaultHost: String? get() = defaultUrl?.let(SyncServerAddress::display)

    val hasProblem: Boolean get() = error != null

    /** Automatic sync runs: a server is known and switched on. */
    val active: Boolean get() = url != null && enabled
}

/** Words for server sync. Pure, so the copy is unit-tested. */
object SyncServerText {
    const val TITLE_NONE = "No sync server"
    const val NONE =
        "This group has no sync server on this phone. Phones sync face to face and by sync file. " +
            "Add a server's address under Advanced."
    const val OFF = "Off: this phone syncs face to face and by sync file only."
    const val AUTOMATIC =
        "Sync now goes through the server. It also syncs on its own when the app opens, shortly after " +
            "every change and every hour. The server only ever sees the group's data locked with the group key."
    const val ADVANCED_HINT =
        "Every phone in the group needs the same server. New invites from this phone carry the address."
    const val NOT_AN_ADDRESS =
        "That isn't a sync server address. Type it like oldboi.example.ts.net:8443 (it must use https)."
    const val RETRYING = "Trying again on its own."

    fun ui(state: GroupState, syncing: Boolean, now: Long, addressError: String? = null): SyncServerUi {
        val server = state.server
        val url = server.url.takeIf { state.inGroup }
        val active = url != null && server.enabled
        val error = server.error?.takeIf { active }
        val syncedAt = server.syncedAt
        return SyncServerUi(
            url = url,
            custom = server.custom,
            defaultUrl = server.defaultUrl,
            enabled = server.enabled,
            syncing = syncing && active,
            status = url?.let {
                when {
                    !server.enabled -> "Off"
                    syncing -> "Syncing…"
                    syncedAt != null -> "Synced ${SyncText.ago(syncedAt, now)}"
                    else -> "Not synced yet"
                }
            },
            error = error?.message,
            errorAgo = error?.let { SyncText.ago(it.at, now) },
            retrying = error?.problem?.transient == true,
            addressError = addressError,
        )
    }
}
