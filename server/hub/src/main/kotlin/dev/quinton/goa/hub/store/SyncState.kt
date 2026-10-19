package dev.quinton.goa.hub.store

import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A phone that synced with the hub, as it named itself in the optional `X-Goa-Device-*` headers. */
@Serializable
data class SyncClient(val id: String, val name: String, val lastSyncAt: Long, val syncs: Int)

@Serializable
data class SyncStateData(val lastSyncAt: Long? = null, val clients: List<SyncClient> = emptyList())

/** `sync-state.json`: when phones last synced (for `goa-hub status` and the Group page). Never synced itself. */
class SyncStateStore(private val file: Path) {
    @Synchronized
    fun read(): SyncStateData = AtomicFiles.readOrNull(file)
        ?.let {
            runCatching { StateJson.decodeFromString(SyncStateData.serializer(), it.decodeToString()) }.getOrNull()
        }
        ?: SyncStateData()

    @Synchronized
    fun record(clientId: String?, clientName: String?, now: Long) {
        val state = read()
        val id = clientId?.take(MAX_ID)?.takeIf { it.isNotBlank() } ?: ANONYMOUS
        val name =
            clientName?.trim()?.take(MAX_NAME)?.takeIf { it.isNotBlank() } ?: UNNAMED
        val old = state.clients.firstOrNull { it.id == id }
        val client = SyncClient(id, name, now, (old?.syncs ?: 0) + 1)
        val clients = (listOf(client) + state.clients.filterNot { it.id == id }).take(MAX_CLIENTS)
        AtomicFiles.write(
            file,
            StateJson.encodeToString(SyncStateData.serializer(), SyncStateData(now, clients)).toByteArray(),
        )
    }

    private companion object {
        const val ANONYMOUS = "unnamed"

        /** A phone that didn't give a name (its id is a random install id, meaningless to people). */
        const val UNNAMED = "A phone"
        const val MAX_ID = 64
        const val MAX_NAME = 60
        const val MAX_CLIENTS = 50
        val StateJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
