package dev.quinton.goa.hub.store

import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A phone that synced with the hub, as it named itself in the optional `X-Goa-Device-*` headers. */
@Serializable
data class SyncClient(val id: String, val name: String, val lastSyncAt: Long, val syncs: Int)

@Serializable
data class SyncStateData(val lastSyncAt: Long? = null, val clients: List<SyncClient> = emptyList())

/**
 * `sync-state.json`: when phones last synced (for `goa-hub status` and the Group page). Never synced
 * itself. A phone not seen for [STALE_AFTER_MS] (90 days) drops off the list, so a phone replaced or
 * a row from before phones sent their names (one anonymous "A phone") doesn't stay forever; "Forget"
 * on the Group page removes one now ([forget]); it comes back if that phone syncs again.
 */
class SyncStateStore(private val file: Path) {
    /** Everything stored, stale rows included (see [read] for what to show). */
    @Synchronized
    fun readAll(): SyncStateData = AtomicFiles.readOrNull(file)
        ?.let {
            runCatching { StateJson.decodeFromString(SyncStateData.serializer(), it.decodeToString()) }.getOrNull()
        }
        ?: SyncStateData()

    /** What to show at [now]: phones seen within [STALE_AFTER_MS]. Writes nothing (status may run as another user). */
    @Synchronized
    fun read(now: Long): SyncStateData = readAll().let { it.copy(clients = it.clients.fresh(now)) }

    @Synchronized
    fun record(clientId: String?, clientName: String?, now: Long) {
        val state = readAll()
        val id = clientId?.take(MAX_ID)?.takeIf { it.isNotBlank() } ?: ANONYMOUS
        val name =
            clientName?.trim()?.take(MAX_NAME)?.takeIf { it.isNotBlank() } ?: UNNAMED
        val old = state.clients.firstOrNull { it.id == id }
        val client = SyncClient(id, name, now, (old?.syncs ?: 0) + 1)
        val clients = (listOf(client) + state.clients.filterNot { it.id == id }.fresh(now)).take(MAX_CLIENTS)
        write(SyncStateData(now, clients))
    }

    /** Removes the phone [clientId] from the list; false if it wasn't there. */
    @Synchronized
    fun forget(clientId: String): Boolean {
        val state = readAll()
        if (state.clients.none { it.id == clientId }) return false
        write(state.copy(clients = state.clients.filterNot { it.id == clientId }))
        return true
    }

    private fun write(state: SyncStateData) =
        AtomicFiles.write(file, StateJson.encodeToString(SyncStateData.serializer(), state).toByteArray())

    private fun List<SyncClient>.fresh(now: Long) = filter { now - it.lastSyncAt <= STALE_AFTER_MS }

    companion object {
        /** A phone not seen for this long leaves the list (90 days). */
        const val STALE_AFTER_MS = 90L * 24 * 60 * 60 * 1000
        private const val ANONYMOUS = "unnamed"

        /** A phone that didn't give a name (its id is a random install id, meaningless to people). */
        private const val UNNAMED = "A phone"
        private const val MAX_ID = 64
        private const val MAX_NAME = 60
        private const val MAX_CLIENTS = 50
        private val StateJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
