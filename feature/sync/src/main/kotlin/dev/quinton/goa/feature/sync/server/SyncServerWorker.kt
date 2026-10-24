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
import dev.quinton.goa.feature.sync.SyncText

/**
 * One sync with the group's sync server (ADR-036). A problem that may pass on its own (no
 * connection, a slow, busy, full or failing server) is retried with exponential backoff, up to
 * [MAX_ATTEMPTS], and never sooner than the server's `Retry-After`; one that needs a person (wrong
 * key, versions, too big, refused data) fails at once and waits on the Group screen. A watched run
 * (joining) never retries: its outcome decides whether the first sync is face to face. The hourly
 * sync and the next change try again either way.
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

    /** What to do after a run. */
    sealed interface Next {
        data object Done : Next

        data object Fail : Next

        /** WorkManager's own exponential backoff. */
        data object Retry : Next

        /** A fresh run queued no earlier than [seconds] (the server's `Retry-After` beats the backoff). */
        data class RetryAfter(val seconds: Long) : Next
    }

    companion object {
        /** 30 s, 1, 2, 4 … 64 min: about two hours of retries before the hourly sync takes over. */
        const val MAX_ATTEMPTS = 8

        const val KEY_ATTEMPT = "attempt"
        const val KEY_WATCHED = "watched"
        private const val KEY_SYNCED = "synced"
        private const val KEY_RECEIVED = "received"

        @Suppress("ReturnCount")
        fun decide(result: SyncServerResult, attempt: Int, watched: Boolean = false): Next {
            if (result !is SyncServerResult.Failed) return Next.Done
            val error = result.error
            if (!error.problem.transient || watched || attempt + 1 >= MAX_ATTEMPTS) return Next.Fail
            val wait = error.retryAfterSeconds ?: return Next.Retry
            return if (wait > backoffSeconds(attempt)) Next.RetryAfter(wait) else Next.Retry
        }

        /** WorkManager's exponential backoff before the retry after [attempt] (0-based). */
        fun backoffSeconds(attempt: Int): Long =
            SyncServerScheduler.BACKOFF_SECONDS shl attempt.coerceIn(0, MAX_ATTEMPTS)

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
