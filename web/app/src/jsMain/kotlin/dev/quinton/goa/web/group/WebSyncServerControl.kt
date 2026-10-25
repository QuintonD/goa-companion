package dev.quinton.goa.web.group

import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.SyncServerControl
import dev.quinton.goa.core.data.SyncServerOutcome
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.sync.SyncServerRetry
import dev.quinton.goa.feature.sync.SyncWords
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * When this browser syncs with the group's sync server (ADR-036, ADR-043): the phone's schedule
 * (`SyncServerScheduler`) as far as a web page can keep it. A page can't sync while it is closed, so
 * there is no hourly background run; instead it syncs when the app opens and comes back on screen
 * (at most once a minute), every [VISIBLE_MINUTES] while on screen, [AFTER_CHANGE_SECONDS] after the
 * last change to synced data (each change restarts the wait), right after a new group or server,
 * from "Sync now", and when joining ([syncAndWatch]). Automatic runs wait for a connection;
 * transient failures back off as [SyncServerRetry] decides (never sooner than `Retry-After`).
 * Nothing runs unless this browser is in a group with a server switched on.
 */
class WebSyncServerControl(
    private val groups: GroupRepository,
    private val server: SyncServerRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val browser: BrowserSignals = BrowserSignals.Live,
) : SyncServerControl {
    private val runningState = MutableStateFlow(false)
    private val runLock = Mutex()
    private var pending: Job? = null
    private var visible: Job? = null
    private var lastOnScreen: Long? = null
    private var started = false

    override val running: Flow<Boolean> = runningState.asStateFlow()

    /** Starts watching the group, the synced data and the page's visibility; call once at start-up. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch(logged) {
            groups.state.map { it.group?.groupId to it.server.activeUrl }.distinctUntilChanged().withIndex()
                .collect { (i, at) ->
                    val (group, url) = at
                    if (group == null || url == null) {
                        pending?.cancel()
                    } else if (i > 0) {
                        // A server set or switched on, or a group joined, while the app runs: sync now.
                        enqueue(delayMs = 0)
                    }
                }
        }
        scope.launch(logged) {
            // One write can follow another at once (an edit, then its draft); let a burst settle.
            server.localChanges.debounce(COALESCE_MS).collect {
                if (groups.state.first().serverSyncOn) enqueue(delayMs = AFTER_CHANGE_SECONDS * MILLIS)
            }
        }
        browser.onVisibility { shown -> onScreen(shown) }
        browser.onOnline { scope.launch(logged) { if (groups.state.first().serverSyncOn) enqueue(delayMs = 0) } }
        onScreen(browser.visible())
    }

    /** The page came on screen ([shown]) or left it: coming back syncs, at most once a minute. */
    fun onScreen(shown: Boolean) {
        visible?.cancel()
        visible = null
        if (!shown) return
        val at = now()
        val recent = lastOnScreen?.let { at - it < SCREEN_GAP_MS } == true
        visible = scope.launch(logged) {
            if (!recent) {
                lastOnScreen = at
                if (groups.state.first().serverSyncOn) enqueue(delayMs = 0)
            }
            while (isActive) {
                delay(VISIBLE_MINUTES * MINUTE_MS)
                lastOnScreen = now()
                if (groups.state.first().serverSyncOn) enqueue(delayMs = 0)
            }
        }
    }

    override fun syncNow() = enqueue(delayMs = 0, needsNetwork = false)

    override fun syncAndWatch(): Flow<SyncServerOutcome> = flow {
        // The run belongs to the app, not to whoever watches: leaving the screen never stops it.
        val run = scope.async(logged) { runOnce(attempt = 0, watched = true, needsNetwork = false) }
        emit(outcomeOf(run.await()))
    }

    /**
     * Replaces a waiting run with one in [delayMs] (the debounce); a run in progress finishes first
     * and the new one follows it.
     */
    private fun enqueue(delayMs: Long, needsNetwork: Boolean = true, attempt: Int = 0) {
        pending?.cancel()
        pending = scope.launch(logged) {
            delay(delayMs)
            scope.launch(logged) { runOnce(attempt, watched = false, needsNetwork = needsNetwork) }
        }
    }

    private suspend fun runOnce(attempt: Int, watched: Boolean, needsNetwork: Boolean): SyncServerResult {
        // An automatic run without a connection waits for the `online` event instead.
        if (needsNetwork && !browser.online()) return SyncServerResult.NotConfigured
        val result = runLock.withLock {
            runningState.value = true
            try {
                server.sync()
            } finally {
                runningState.value = false
            }
        }
        when (val next = SyncServerRetry.decide(result, attempt, watched)) {
            SyncServerRetry.Next.Retry -> enqueue(SyncServerRetry.backoffSeconds(attempt) * MILLIS, attempt = attempt + 1)
            is SyncServerRetry.Next.RetryAfter -> enqueue(next.seconds * MILLIS, attempt = attempt + 1)
            SyncServerRetry.Next.Done, SyncServerRetry.Next.Fail -> Unit
        }
        return result
    }

    companion object {
        const val AFTER_CHANGE_SECONDS = 15L
        const val VISIBLE_MINUTES = 5L
        private const val SCREEN_GAP_MS = 60_000L
        private const val COALESCE_MS = 1_000L
        private const val MILLIS = 1_000L
        private const val MINUTE_MS = 60_000L

        /** Scheduling must never take the app down (a failure shows on the Group screen). Logs no data. */
        private val logged = CoroutineExceptionHandler { _, e ->
            console.warn("Sync server scheduling failed: ${e::class.simpleName}")
        }

        /** What the joining screen says about a watched run ("+3 matches, +1 player", or nothing new). */
        fun outcomeOf(result: SyncServerResult): SyncServerOutcome {
            val synced = result as? SyncServerResult.Synced
            return SyncServerOutcome(
                synced = synced != null,
                received = synced?.changes?.takeUnless { it.isEmpty }?.let(SyncWords::summary),
            )
        }
    }
}

/** What the scheduler needs from the page: visibility and connectivity (fakes in tests). */
interface BrowserSignals {
    fun visible(): Boolean

    fun online(): Boolean

    fun onVisibility(listener: (Boolean) -> Unit)

    fun onOnline(listener: () -> Unit)

    object Live : BrowserSignals {
        override fun visible(): Boolean = js("typeof document === 'undefined' || document.visibilityState !== 'hidden'") as Boolean

        override fun online(): Boolean = js("typeof navigator === 'undefined' || navigator.onLine !== false") as Boolean

        override fun onVisibility(listener: (Boolean) -> Unit) {
            if (js("typeof document === 'undefined'") as Boolean) return
            val handler: (dynamic) -> Unit = { listener(visible()) }
            kotlinx.browser.document.addEventListener("visibilitychange", handler)
            // iOS restores a home-screen app from the back/forward cache without a visibility change.
            kotlinx.browser.window.addEventListener("pageshow", handler)
        }

        override fun onOnline(listener: () -> Unit) {
            if (js("typeof window === 'undefined'") as Boolean) return
            kotlinx.browser.window.addEventListener("online", { _: dynamic -> listener() })
        }
    }
}
