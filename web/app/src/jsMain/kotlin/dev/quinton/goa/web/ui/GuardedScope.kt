package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job

/**
 * [rememberCoroutineScope] for work that can fail (storage, the engine worker): a failed job is reported
 * through [onError]. In the plain scope it would reach the composition and stop the whole page responding.
 */
@Composable
internal fun rememberGuardedScope(onError: (Throwable) -> Unit = {}): CoroutineScope {
    val parent = rememberCoroutineScope()
    return remember(parent) {
        val context = parent.coroutineContext
        CoroutineScope(
            context + SupervisorJob(context.job) + CoroutineExceptionHandler { _, error ->
                console.error("Screen", error)
                onError(error)
            },
        )
    }
}
