package dev.quinton.goa.feature.sync.server

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult

/**
 * One sync with the group's sync server (ADR-036). A problem that may pass on its own (no
 * connection, a slow, busy or failing server) is retried with exponential backoff, up to
 * [MAX_ATTEMPTS]; one that needs a person (wrong key, versions, too big, refused data) fails at
 * once and waits on the Group screen. The hourly sync and the next change try again either way.
 */
@HiltWorker
class SyncServerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val server: SyncServerRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = outcome(server.sync(), runAttemptCount)

    companion object {
        /** 30 s, 1, 2, 4 … 64 min: about two hours of retries before the hourly sync takes over. */
        const val MAX_ATTEMPTS = 8

        fun outcome(result: SyncServerResult, attempt: Int): Result = when (result) {
            SyncServerResult.NotConfigured, is SyncServerResult.Synced -> Result.success()

            is SyncServerResult.Failed ->
                if (result.error.problem.transient && attempt + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
}
