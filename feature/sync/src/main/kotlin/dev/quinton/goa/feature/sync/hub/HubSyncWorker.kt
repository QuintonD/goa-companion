package dev.quinton.goa.feature.sync.hub

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.quinton.goa.core.data.HubSyncRepository
import dev.quinton.goa.core.data.HubSyncResult

/**
 * One round trip with the group's hub (ADR-030 §5). A problem that may pass on its own (no
 * connection, a slow or failing hub) is retried with exponential backoff, up to [MAX_ATTEMPTS];
 * one that needs a person (wrong key, versions, refused data) fails at once and waits on the
 * Group screen. The hourly sync and the next change try again either way.
 */
@HiltWorker
class HubSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val hub: HubSyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = outcome(hub.sync(), runAttemptCount)

    companion object {
        /** 30 s, 1, 2, 4 … 64 min: about two hours of retries before the hourly sync takes over. */
        const val MAX_ATTEMPTS = 8

        fun outcome(result: HubSyncResult, attempt: Int): Result = when (result) {
            HubSyncResult.NotConfigured, is HubSyncResult.Synced -> Result.success()

            is HubSyncResult.Failed ->
                if (result.error.problem.transient && attempt + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
}
