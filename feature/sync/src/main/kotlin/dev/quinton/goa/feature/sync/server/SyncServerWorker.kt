package dev.quinton.goa.feature.sync.server

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.quinton.goa.core.data.SyncServerOutcome
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.sync.SyncServerRetry
import dev.quinton.goa.core.data.sync.SyncServerRetry.Next
import dev.quinton.goa.feature.sync.SyncText

/**
 * One sync with the group's sync server (ADR-036), retried as [SyncServerRetry] decides (shared
 * with the web app, ADR-043): [Next.Retry] is WorkManager's own exponential backoff
 * ([SyncServerScheduler.BACKOFF_SECONDS] doubling), [Next.RetryAfter] a fresh run queued no earlier
 * than the server's `Retry-After`.
 */
@HiltWorker
class SyncServerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val server: SyncServerRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val attempt = runAttemptCount + inputData.getInt(KEY_ATTEMPT, 0)
        val result = server.sync()
        val output = output(result)
        return when (val next = decide(result, attempt, watched = inputData.getBoolean(KEY_WATCHED, false))) {
            Next.Done -> Result.success(output)

            Next.Fail -> Result.failure(output)

            Next.Retry -> Result.retry()

            is Next.RetryAfter -> {
                // Queued behind this run, so this run must succeed: a failed one would cancel it.
                SyncServerScheduler.retryLater(WorkManager.getInstance(applicationContext), next.seconds, attempt + 1)
                Result.success(output)
            }
        }
    }

    companion object {
        const val KEY_ATTEMPT = "attempt"
        const val KEY_WATCHED = "watched"
        private const val KEY_SYNCED = "synced"
        private const val KEY_RECEIVED = "received"

        fun decide(result: SyncServerResult, attempt: Int, watched: Boolean = false): Next =
            SyncServerRetry.decide(result, attempt, watched)

        fun output(result: SyncServerResult): Data {
            val synced = result as? SyncServerResult.Synced
            val received = synced?.changes?.takeUnless { it.isEmpty }?.let(SyncText::summary)
            return workDataOf(KEY_SYNCED to (synced != null), KEY_RECEIVED to received)
        }

        /** The outcome of a finished run, for [SyncServerScheduler.syncAndWatch]. */
        fun outcomeOf(info: WorkInfo): SyncServerOutcome = SyncServerOutcome(
            synced = info.state == WorkInfo.State.SUCCEEDED && info.outputData.getBoolean(KEY_SYNCED, false),
            received = info.outputData.getString(KEY_RECEIVED),
        )
    }
}
