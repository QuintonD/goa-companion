package dev.quinton.goa.core.model.sync

/**
 * This device's raw group-sync values (ADR-025, ADR-036); null = not set. The phone keeps them in
 * DataStore (`datastore/group_sync`), the web app in IndexedDB; the rules over them are shared
 * (`dev.quinton.goa.core.data.sync.PrefsGroupRepository`). None of it is synced or backed up.
 */
@kotlinx.serialization.Serializable
data class GroupSyncPrefs(
    val groupId: String? = null,
    val groupKey: String? = null,
    val groupName: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val mePlayerId: String? = null,
    val phonesJson: String? = null,
    val lastSyncAt: Long? = null,
    /** The group's sync server when it isn't the build's default (an origin); null = the default. */
    val serverUrl: String? = null,
    /** "Sync through the server"; null = on (the default whenever there is a server). */
    val serverEnabled: Boolean? = null,
    /** When this device last synced with the server, and the document's rev then. */
    val serverSyncedAt: Long? = null,
    val serverRev: Long? = null,
    /**
     * SHA-256 of the canonical document this device and the server both held at [serverRev]: when
     * this device's data still hashes the same, a sync may ask for the document only if it changed.
     */
    val serverDocHash: String? = null,
    /** Why the last server sync failed (readable), its kind and when; null after a success. */
    val serverError: String? = null,
    val serverErrorKind: String? = null,
    val serverErrorAt: Long? = null,
) {
    /** The same, without the last server sync's outcome (another group or another server). */
    fun withoutServerStatus() = copy(
        serverSyncedAt = null,
        serverRev = null,
        serverDocHash = null,
        serverError = null,
        serverErrorKind = null,
        serverErrorAt = null,
    )
}
