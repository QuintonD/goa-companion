package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.HubError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hub sync for the UI (ADR-030 §5): whether one runs right now, and "Sync now". The app's
 * implementation is feature:sync's `HubSyncScheduler` (WorkManager); screens outside that module
 * (the Players tab's sync icon) reach it through this interface.
 */
interface HubSyncControl {
    val running: Flow<Boolean>

    fun syncNow()

    companion object {
        /** No hub sync (previews and tests that don't need it). */
        val Off: HubSyncControl = object : HubSyncControl {
            override val running: Flow<Boolean> = flowOf(false)

            override fun syncNow() = Unit
        }

        /** How long a tapped sync is waited for before saying it is still going. */
        const val WAIT_MS = 60_000L
    }
}

/** What a sync somebody tapped came to. */
sealed interface HubSyncOutcome {
    data object Synced : HubSyncOutcome

    data class Failed(val error: HubError) : HubSyncOutcome

    /** No answer within the wait; the sync carries on (and retries) on its own. */
    data object StillSyncing : HubSyncOutcome
}

/**
 * "Sync now", then waits for its outcome: the first success or failure the repository records
 * after the tap (both are stamped with [clock], as [HubSyncRepository] does).
 */
suspend fun HubSyncControl.syncAndWait(
    groups: GroupRepository,
    clock: EditClock,
    waitMs: Long = HubSyncControl.WAIT_MS,
): HubSyncOutcome {
    val tappedAt = clock.now()
    syncNow()
    val state = withTimeoutOrNull(waitMs) {
        groups.state.first {
            (it.hubSyncedAt ?: Long.MIN_VALUE) >= tappedAt || (it.hubError?.at ?: Long.MIN_VALUE) >= tappedAt
        }
    } ?: return HubSyncOutcome.StillSyncing
    val error = state.hubError
    return if (error != null && error.at >= tappedAt) HubSyncOutcome.Failed(error) else HubSyncOutcome.Synced
}
