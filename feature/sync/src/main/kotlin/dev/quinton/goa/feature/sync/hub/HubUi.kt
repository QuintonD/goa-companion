package dev.quinton.goa.feature.sync.hub

import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.feature.sync.SyncText

/** The Hub section of the Group screen (ADR-030 §5). */
data class HubUi(
    /** The hub's origin; null = this group has no hub on this phone. */
    val url: String? = null,
    val syncing: Boolean = false,
    /** "Synced 2 min ago", "Not synced yet". */
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
    val host: String? get() = url?.removePrefix("https://")

    val hasProblem: Boolean get() = error != null
}

/** Words for hub sync. Pure, so the copy is unit-tested. */
object HubText {
    const val EXPLAINER = "Your group's server for automatic sync and the iPhone web app."
    const val AUTOMATIC = "Syncs on its own when the app opens, shortly after every change, and every hour."
    const val NOT_AN_ADDRESS = "That isn't a hub address. Type the address your group's hub runs at, " +
        "like goa.your-tailnet.ts.net (it must use https)."
    const val RETRYING = "Trying again on its own."

    fun ui(state: GroupState, syncing: Boolean, now: Long, addressError: String? = null): HubUi {
        val url = state.hubUrl.takeIf { state.inGroup }
        val error = state.hubError?.takeIf { url != null }
        val syncedAt = state.hubSyncedAt
        return HubUi(
            url = url,
            syncing = syncing && url != null,
            status = url?.let {
                when {
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
