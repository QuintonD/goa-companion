package dev.quinton.goa.feature.sync.server

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.SyncServerControl
import dev.quinton.goa.core.data.SyncServerOutcome
import dev.quinton.goa.core.data.SyncServerRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * When this phone syncs with the group's sync server (ADR-036), through WorkManager so a sync
 * survives the app closing and the screen that asked for it: when the app comes on screen
 * ([onScreen]) and every [VISIBLE_MINUTES] while it stays there, [AFTER_CHANGE_SECONDS] after the
 * last change to synced data (each change restarts the wait), every hour, right after a new server
 * or group, from "Sync now", and when joining a group ([syncAndWatch], expedited on Android 12+).
 * Automatic runs need a network; transient failures back off exponentially, never sooner than the
 * server's `Retry-After`. Nothing is scheduled unless the phone is in a group with a server
 * switched on; leaving, removing the server or switching it off cancels everything.
 *
 * One-time syncs share the unique work [NOW]; the hourly one is [HOURLY]. A sync asked for while
 * one is running is chained after it (APPEND_OR_REPLACE) rather than cancelling it.
 */
@Singleton
class SyncServerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groups: GroupRepository,
    private val server: SyncServerRepository,
    private val clock: EditClock,
) : SyncServerControl {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)
    private var scope: CoroutineScope? = null
    private var visible: Job? = null
    private var lastOnScreen: Long? = null

    /** Starts watching the group and the synced data; call once, from the Application. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch(logged) {
            groups.state.map { it.group?.groupId to it.server.activeUrl }.distinctUntilChanged().withIndex()
                .collect { (i, at) ->
                    val (group, url) = at
                    if (group != null && url != null) {
                        workManager.enqueueUniquePeriodicWork(HOURLY, ExistingPeriodicWorkPolicy.KEEP, hourlyRequest())
                        // A server set or switched on, or a group joined, while the app runs: sync right away.
                        if (i > 0) enqueue(delaySeconds = 0)
                    } else {
                        workManager.cancelUniqueWork(NOW)
                        workManager.cancelUniqueWork(HOURLY)
                    }
                }
        }
        scope.launch(logged) {
            // One transaction can touch several tables; let a burst settle before (re)scheduling.
            server.localChanges.debounce(COALESCE_MS).collect {
                if (groups.state.first().serverSyncOn) enqueue(delaySeconds = AFTER_CHANGE_SECONDS)
            }
        }
    }

    /**
     * The app's activity came on screen ([shown] = true) or left it. Coming on screen syncs (at
     * most once a minute, so a rotation doesn't), and so does every [VISIBLE_MINUTES] on screen.
     */
    fun onScreen(shown: Boolean) {
        val scope = scope ?: return
        visible?.cancel()
        visible = null
        if (!shown) return
        val now = clock.now()
        val recent = lastOnScreen?.let { now - it < SCREEN_GAP_MS } == true
        visible = scope.launch(logged) {
            if (!recent) {
                lastOnScreen = now
                if (groups.state.first().serverSyncOn) enqueue(delaySeconds = 0)
            }
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(VISIBLE_MINUTES))
                lastOnScreen = clock.now()
                if (groups.state.first().serverSyncOn) enqueue(delaySeconds = 0)
            }
        }
    }

    override fun syncNow() {
        scope?.launch(logged) { enqueue(delaySeconds = 0, needsNetwork = false) }
    }

    override fun syncAndWatch(): Flow<SyncServerOutcome> = flow {
        val request = watchedRequest()
        workManager.enqueueUniqueWork(NOW, policy(workManager), request)
        val done = workManager.getWorkInfoByIdFlow(request.id).filterNotNull().first { it.state.isFinished }
        emit(SyncServerWorker.outcomeOf(done))
    }

    override val running: Flow<Boolean>
        get() = combine(
            workManager.getWorkInfosForUniqueWorkFlow(NOW),
            workManager.getWorkInfosForUniqueWorkFlow(HOURLY),
        ) { now, hourly -> (now + hourly).any { it.state == WorkInfo.State.RUNNING } }.distinctUntilChanged()

    private fun enqueue(delaySeconds: Long, needsNetwork: Boolean = true) {
        workManager.enqueueUniqueWork(NOW, policy(workManager), oneTimeRequest(delaySeconds, needsNetwork))
    }

    companion object {
        private const val TAG = "SyncServer"

        /** Scheduling must never take the app down (a storage failure shows elsewhere). Never logs data. */
        private val logged = CoroutineExceptionHandler { _, e ->
            Log.w(TAG, "Sync server scheduling failed: ${e.javaClass.simpleName}")
        }

        const val NOW = "server-sync"
        const val HOURLY = "server-sync-hourly"
        const val AFTER_CHANGE_SECONDS = 15L
        const val BACKOFF_SECONDS = 30L
        const val VISIBLE_MINUTES = 5L
        private const val SCREEN_GAP_MS = 60_000L
        private const val COALESCE_MS = 1_000L

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** REPLACE resets a pending sync's delay (the debounce); a running one finishes first. */
        private fun policy(workManager: WorkManager): ExistingWorkPolicy {
            val running = runCatching { workManager.getWorkInfosForUniqueWork(NOW).get() }.getOrNull().orEmpty()
                .any { it.state == WorkInfo.State.RUNNING }
            return if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        }

        /**
         * A retry no earlier than the server's `Retry-After` ([delaySeconds]), queued behind the
         * run that asked for it; [attempt] carries the retry count on.
         */
        fun retryLater(workManager: WorkManager, delaySeconds: Long, attempt: Int) {
            workManager.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                oneTimeRequest(delaySeconds, needsNetwork = true, attempt = attempt),
            )
        }

        fun oneTimeRequest(delaySeconds: Long, needsNetwork: Boolean = true, attempt: Int = 0): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncServerWorker>()
                .setConstraints(if (needsNetwork) network else Constraints.NONE)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(workDataOf(SyncServerWorker.KEY_ATTEMPT to attempt))
                .addTag(NOW)
                .build()

        /**
         * Joining: runs at once, even offline (the outcome decides whether the first sync is face to
         * face), expedited where that needs no foreground service (Android 12+), and reports its
         * outcome instead of retrying.
         */
        fun watchedRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncServerWorker>()
            .setInputData(workDataOf(SyncServerWorker.KEY_WATCHED to true))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .addTag(NOW)
            .build()

        fun hourlyRequest(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncServerWorker>(1, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(HOURLY)
            .build()
    }
}
