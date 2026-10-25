package dev.quinton.goa.web.group

import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.sync.PrefsGroupRepository
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * When the browser syncs with the relay (the phone's SyncServerScheduler rules, adapted): on open
 * and back on screen at most once a minute, 15 s after the last change, transient failures retried
 * with backoff (never before `Retry-After`), nothing outside a group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebSyncServerControlTest {
    private class Signals : BrowserSignals {
        var shown = true
        var online = true
        var onVisible: (Boolean) -> Unit = {}
        var onOnline: () -> Unit = {}

        override fun visible() = shown

        override fun online() = online

        override fun onVisibility(listener: (Boolean) -> Unit) {
            onVisible = listener
        }

        override fun onOnline(listener: () -> Unit) {
            onOnline = listener
        }
    }

    private class Server : SyncServerRepository {
        var calls = 0
        var next: SyncServerResult = SyncServerResult.Synced(SyncChanges.NONE, 0, 1, uploaded = false)
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

        override suspend fun sync(): SyncServerResult {
            calls++
            return next
        }

        override val localChanges: Flow<Unit> = changes
    }

    private class Setup(scope: TestScope) {
        val signals = Signals()
        val server = Server()
        val groups = PrefsGroupRepository(WebGroupPrefsStore(MemoryKeyValueStore()), SyncServerDefaults("https://relay.example.org"))
        val control = WebSyncServerControl(groups, server, scope.backgroundScope, { scope.testScheduler.currentTime }, signals)
    }

    @Test
    fun openingTheAppSyncsOnceEvenWhenTheBrowserSaysShownTwice() = runTest {
        val s = Setup(this)
        s.groups.join(GroupCredentials.create("Friday"))
        s.control.start()
        s.signals.onVisible(true) // iOS: pageshow right after the first check
        runCurrent()
        assertEquals(1, s.server.calls)
    }

    @Test
    fun backOnScreenSyncsAtMostOnceAMinuteAndEveryFiveMinutesWhileShown() = runTest {
        val s = Setup(this)
        s.groups.join(GroupCredentials.create("Friday"))
        s.control.start()
        runCurrent()
        s.signals.onVisible(false)
        s.signals.onVisible(true)
        runCurrent()
        assertEquals(1, s.server.calls) // came back within the minute
        advanceTimeBy(5 * 60_000L + 1)
        runCurrent()
        assertEquals(2, s.server.calls)
    }

    @Test
    fun aChangeSyncsFifteenSecondsAfterTheLastOne() = runTest {
        val s = Setup(this)
        s.groups.join(GroupCredentials.create("Friday"))
        s.control.start()
        runCurrent()
        s.server.changes.emit(Unit)
        advanceTimeBy(10_000)
        s.server.changes.emit(Unit) // a second edit restarts the wait
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, s.server.calls)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, s.server.calls)
    }

    @Test
    fun aTransientFailureRetriesWithBackoffAndNeverBeforeRetryAfter() = runTest {
        val s = Setup(this)
        s.groups.join(GroupCredentials.create("Friday"))
        s.server.next = SyncServerResult.Failed(SyncServerError(SyncServerProblem.RATE_LIMITED, "busy", 0, retryAfterSeconds = 120))
        s.control.start()
        runCurrent()
        assertEquals(1, s.server.calls)
        advanceTimeBy(119_000)
        runCurrent()
        assertEquals(1, s.server.calls)
        s.server.next = SyncServerResult.Failed(SyncServerError(SyncServerProblem.WRONG_KEY, "key", 0))
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, s.server.calls)
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        // A wrong key waits for a person: only the every-5-minutes sync while shown tries again.
        assertEquals(2 + 12, s.server.calls)
    }

    @Test
    fun nothingRunsOutsideAGroupAndJoiningSyncsAtOnce() = runTest {
        val s = Setup(this)
        s.control.start()
        runCurrent()
        assertEquals(0, s.server.calls)
        s.groups.join(GroupCredentials.create("Friday"))
        runCurrent()
        assertEquals(1, s.server.calls)
        assertEquals(true, s.groups.state.first().serverSyncOn)
    }
}
