package dev.quinton.goa.feature.sync.hub

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.HubSyncRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

/** Hub sync for the UI: whether one runs right now, and "Sync now". */
interface HubSyncControl {
    val running: Flow<Boolean>

    fun syncNow()

    companion object {
        /** No hub sync (previews and tests that don't need it). */
        val Off: HubSyncControl = object : HubSyncControl {
            override val running: Flow<Boolean> = flowOf(false)

            override fun syncNow() = Unit
        }
    }
}

/**
 * When this phone syncs with the group's hub (ADR-030 §5), all through WorkManager so a sync
 * survives the app closing: at app start ([onAppOpened]), 15 s after the last change to synced
 * data (each change replaces the pending sync), every hour, after a new hub address or group,
 * and from "Sync now". Every run needs a network; failures back off exponentially. Nothing is
 * scheduled unless the phone is in a group with a hub; leaving or removing the hub cancels all.
 *
 * One-time syncs share the unique work [NOW]; the hourly one is [HOURLY]. A sync asked for while
 * one is running is chained after it rather than cancelling it.
 */
@Singleton
class HubSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groups: GroupRepository,
    private val hub: HubSyncRepository,
) : HubSyncControl {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)
    private var scope: CoroutineScope? = null

    /** Starts watching the group and the synced data; call once, from the Application. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch(logged) {
            groups.state.map { it.group?.groupId to it.hubUrl }.distinctUntilChanged().withIndex().collect { (i, at) ->
                val (group, hubUrl) = at
                if (group != null && hubUrl != null) {
                    workManager.enqueueUniquePeriodicWork(HOURLY, ExistingPeriodicWorkPolicy.KEEP, hourlyRequest())
                    // A hub set or changed, or a group joined, while the app runs: sync right away.
                    if (i > 0) enqueue(delaySeconds = 0)
                } else {
                    workManager.cancelUniqueWork(NOW)
                    workManager.cancelUniqueWork(HOURLY)
                }
            }
        }
        scope.launch(logged) {
            // One transaction can touch several tables; let a burst settle before (re)scheduling.
            hub.localChanges.debounce(COALESCE_MS).collect {
                if (groups.state.first().hubSyncOn) enqueue(delaySeconds = AFTER_CHANGE_SECONDS)
            }
        }
    }

    /** The app was opened (a cold start of the activity, not a background wake-up). */
    fun onAppOpened() {
        scope?.launch(logged) { if (groups.state.first().hubSyncOn) enqueue(delaySeconds = 0) }
    }

    /** Runs at once, even without a network, so the person who tapped sees the outcome. */
    override fun syncNow() {
        scope?.launch(logged) { enqueue(delaySeconds = 0, needsNetwork = false) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val running: Flow<Boolean>
        get() = combine(
            workManager.getWorkInfosForUniqueWorkFlow(NOW),
            workManager.getWorkInfosForUniqueWorkFlow(HOURLY),
        ) { now, hourly -> (now + hourly).any { it.state == WorkInfo.State.RUNNING } }.distinctUntilChanged()

    private fun enqueue(delaySeconds: Long, needsNetwork: Boolean = true) {
        val running = runCatching { workManager.getWorkInfosForUniqueWork(NOW).get() }.getOrNull().orEmpty()
            .any { it.state == WorkInfo.State.RUNNING }
        // REPLACE resets a pending sync's delay (the debounce); a running one finishes first.
        val policy = if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(NOW, policy, oneTimeRequest(delaySeconds, needsNetwork))
    }

    companion object {
        private const val TAG = "HubSync"

        /** Scheduling must never take the app down (a storage failure shows elsewhere). */
        private val logged = CoroutineExceptionHandler { _, e -> Log.w(TAG, "Hub sync scheduling failed", e) }

        const val NOW = "hub-sync"
        const val HOURLY = "hub-sync-hourly"
        const val AFTER_CHANGE_SECONDS = 15L
        const val BACKOFF_SECONDS = 30L
        private const val COALESCE_MS = 1_000L

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun oneTimeRequest(delaySeconds: Long, needsNetwork: Boolean = true): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HubSyncWorker>()
            .setConstraints(if (needsNetwork) network else Constraints.NONE)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(NOW)
            .build()

        fun hourlyRequest(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<HubSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(HOURLY)
            .build()
    }
}
