package dev.quinton.goa.core.domain.sync.protocol

import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.SyncDocument

/*
 * The storage side of a sync, shared by the phones and the web app (ADR-043): the Nearby protocol
 * (SyncHost, SyncClient: JVM only) and the sync server client both merge through it. The protocol
 * itself stays in jvmMain.
 */

/**
 * What a sync needs from the phone's storage: its data, and merging a received document in.
 * [apply] must merge with the phone's *current* data and write the result in one transaction, so
 * a phone that drops out mid-sync is left exactly as it was.
 */
interface SyncDataPort {
    suspend fun export(): SyncDocument

    suspend fun apply(incoming: SyncDocument): SyncApplyResult
}

/** The phone's data after a merge ([document], canonical) and what the merge changed on it. */
data class SyncApplyResult(val document: SyncDocument, val changes: SyncChanges)

/**
 * A phone taking part in a sync: its stable install id, the name other phones see and its app
 * version (phones on different versions may know different heroes, so their hero ratings differ).
 */
data class SyncDevice(val deviceId: String, val name: String, val appVersion: String? = null)
