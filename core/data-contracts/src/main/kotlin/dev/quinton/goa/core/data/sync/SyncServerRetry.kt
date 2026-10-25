package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.SyncServerResult

/**
 * When to try a failed server sync again (ADR-036), the same on the phone (WorkManager) and in the
 * browser (ADR-043): a problem that may pass on its own (no connection, a slow, busy, full or
 * failing server) is retried with exponential backoff, up to [MAX_ATTEMPTS], and never sooner than
 * the server's `Retry-After`; one that needs a person (wrong key, versions, too big, refused data)
 * fails at once and waits on the Group screen. A watched run (joining) never retries: its outcome
 * decides whether the first sync is face to face. The hourly sync and the next change try again
 * either way.
 */
object SyncServerRetry {
    /** 30 s, 1, 2, 4 … 64 min: about two hours of retries before the hourly sync takes over. */
    const val MAX_ATTEMPTS = 8

    /** The first retry's wait; each later one doubles it. */
    const val BACKOFF_SECONDS = 30L

    /** What to do after a run. */
    sealed interface Next {
        data object Done : Next

        data object Fail : Next

        /** Exponential backoff ([backoffSeconds]). */
        data object Retry : Next

        /** A fresh run no earlier than [seconds] (the server's `Retry-After` beats the backoff). */
        data class RetryAfter(val seconds: Long) : Next
    }

    @Suppress("ReturnCount")
    fun decide(result: SyncServerResult, attempt: Int, watched: Boolean = false): Next {
        if (result !is SyncServerResult.Failed) return Next.Done
        val error = result.error
        if (!error.problem.transient || watched || attempt + 1 >= MAX_ATTEMPTS) return Next.Fail
        val wait = error.retryAfterSeconds ?: return Next.Retry
        return if (wait > backoffSeconds(attempt)) Next.RetryAfter(wait) else Next.Retry
    }

    /** The exponential backoff before the retry after [attempt] (0-based). */
    fun backoffSeconds(attempt: Int): Long = BACKOFF_SECONDS shl attempt.coerceIn(0, MAX_ATTEMPTS)
}
