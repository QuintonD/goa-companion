package dev.quinton.goa.feature.sync.server

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.ServerSyncState
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerOutcome
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.feature.sync.TestGroup
import dev.quinton.goa.feature.sync.groupState
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A sync server that answers from a script; counts syncs. */
class FakeSyncServerRepository : SyncServerRepository {
    val script = ConcurrentLinkedQueue<SyncServerResult>()
    private val count = AtomicInteger()
    val calls: Int get() = count.get()
    override val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    override suspend fun sync(): SyncServerResult {
        count.incrementAndGet()
        return script.poll() ?: SyncServerResult.Synced(SyncChanges.NONE, 1, 1, uploaded = false)
    }
}

/**
 * WorkManager scheduling of server sync (ADR-036), on WorkManager's test driver: what is
 * scheduled when, with which constraints and backoff, the 15 s debounce after changes, the app
 * coming on screen and staying there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncServerSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = FakeSyncServerRepository()
    private val withServer = ServerSyncState(url = "https://goa.example", defaultUrl = "https://goa.example")
    private val groups = FakeGroupRepository(groupState().copy(server = withServer))
    private var now = 1_000_000L
    private val scheduler = SyncServerScheduler(context, groups, server, EditClock { now })
    private val workManager get() = WorkManager.getInstance(context)

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(context: Context, name: String, params: WorkerParameters): ListenableWorker =
                SyncServerWorker(context, params, server)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .build(),
        )
    }

    private fun infos(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    private fun pending(name: String = SyncServerScheduler.NOW) = infos(name).filter {
        it.state == WorkInfo.State.ENQUEUED
    }

    /** CoroutineWorker runs on its own threads: wait for the run to settle. */
    private fun settled(id: UUID, done: (WorkInfo) -> Boolean = { it.state.isFinished }): WorkInfo {
        repeat(200) {
            val info = workManager.getWorkInfoById(id).get()!!
            if (done(info)) return info
            Thread.sleep(25)
        }
        error("Work $id didn't settle")
    }

    private fun TestScope.started() {
        scheduler.start(backgroundScope)
        runCurrent()
    }

    @Test
    fun inAGroupWithAServerAnHourlySyncIsScheduledWithANetwork() = runTest {
        started()

        val hourly = pending(SyncServerScheduler.HOURLY).single()
        assertThat(hourly.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(1))
        assertThat(hourly.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        // Starting the process (it may be a background wake-up) is not "the app came on screen".
        assertThat(infos(SyncServerScheduler.NOW)).isEmpty()
    }

    @Test
    fun comingOnScreenSyncsAtOnceThenEveryFewMinutesButNotOnARotation() = runTest {
        started()

        scheduler.onScreen(true)
        runCurrent()
        val first = pending().single()
        assertThat(first.initialDelayMillis).isEqualTo(0)
        assertThat(first.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(first.id)
        assertThat(settled(first.id).state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(server.calls).isEqualTo(1)

        // A rotation: off screen and back within a second schedules nothing new.
        scheduler.onScreen(false)
        now += 1_000
        scheduler.onScreen(true)
        runCurrent()
        assertThat(pending()).isEmpty()

        // Staying on screen: another sync after a few minutes.
        advanceTimeBy(TimeUnit.MINUTES.toMillis(SyncServerScheduler.VISIBLE_MINUTES) + 1)
        assertThat(pending()).hasSize(1)

        // Off screen: no more.
        scheduler.onScreen(false)
        val queued = pending().single().id
        advanceTimeBy(TimeUnit.MINUTES.toMillis(SyncServerScheduler.VISIBLE_MINUTES * 3))
        assertThat(pending().single().id).isEqualTo(queued)
    }

    @Test
    fun aChangeSyncs15sLaterAndEachNewChangeRestartsTheWait() = runTest {
        started()

        server.localChanges.emit(Unit)
        server.localChanges.emit(Unit)
        advanceTimeBy(1_100)
        val first = pending().single()
        assertThat(first.initialDelayMillis).isEqualTo(15_000)
        assertThat(first.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)

        advanceTimeBy(5_000)
        server.localChanges.emit(Unit)
        advanceTimeBy(1_100)

        // Replaced: cancelled and dropped.
        assertThat(workManager.getWorkInfoById(first.id).get()?.state ?: WorkInfo.State.CANCELLED)
            .isEqualTo(WorkInfo.State.CANCELLED)
        val second = pending().single()
        assertThat(second.id).isNotEqualTo(first.id)
        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        driver.setAllConstraintsMet(second.id)
        driver.setInitialDelayMet(second.id)
        assertThat(settled(second.id).state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(server.calls).isEqualTo(1)
    }

    @Test
    fun withoutAServerOrWithItOffNothingIsScheduledAndSwitchingOffCancelsEverything() = runTest {
        groups.current.value = groupState()
        started()
        server.localChanges.emit(Unit)
        advanceTimeBy(1_100)
        scheduler.onScreen(true)
        runCurrent()
        assertThat(pending()).isEmpty()
        assertThat(pending(SyncServerScheduler.HOURLY)).isEmpty()

        groups.setServerUrl("https://goa.example")
        runCurrent()
        assertThat(pending(SyncServerScheduler.HOURLY)).hasSize(1)

        groups.setServerSyncEnabled(false)
        runCurrent()
        assertThat(pending()).isEmpty()
        assertThat(pending(SyncServerScheduler.HOURLY)).isEmpty()
    }

    @Test
    fun aNewServerOrGroupSyncsRightAway() = runTest {
        started()

        groups.setServerUrl("https://other.example")
        runCurrent()

        assertThat(pending().single().initialDelayMillis).isEqualTo(0)
        groups.leave()
        runCurrent()
        assertThat(pending()).isEmpty()
        groups.join(TestGroup, serverUrl = "https://goa.example")
        runCurrent()
        assertThat(pending()).hasSize(1)
    }

    @Test
    fun syncNowRunsEvenOfflineSoTheOutcomeShows() = runTest {
        started()

        scheduler.syncNow()
        runCurrent()

        assertThat(settled(infos(SyncServerScheduler.NOW).single().id).state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(server.calls).isEqualTo(1)
    }

    @Test
    fun aWatchedSyncRunsAsUniqueWorkAndReportsItsOutcomeWithoutRetrying() = runTest {
        started()
        server.script += SyncServerResult.Synced(SyncChanges(matchesAdded = 2), 1, 1, uploaded = false)

        val outcome = withContext(Dispatchers.Default) { withTimeout(10_000) { scheduler.syncAndWatch().first() } }

        assertThat(outcome).isEqualTo(SyncServerOutcome(synced = true, received = "+2 matches"))
        val request = SyncServerScheduler.watchedRequest()
        assertThat(request.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.NOT_REQUIRED)
        assertThat(infos(SyncServerScheduler.NOW).map { it.state }).containsExactly(WorkInfo.State.SUCCEEDED)

        // Nothing new on the server: synced, nothing received.
        assertThat(withContext(Dispatchers.Default) { scheduler.syncAndWatch().first() })
            .isEqualTo(SyncServerOutcome(synced = true, received = null))

        // Offline: no retry while someone waits; the outcome says so at once.
        server.script += failed(SyncServerProblem.UNREACHABLE)
        val offline = withContext(Dispatchers.Default) { withTimeout(10_000) { scheduler.syncAndWatch().first() } }
        assertThat(offline.synced).isFalse()
        assertThat(server.calls).isEqualTo(3)
        assertThat(pending()).isEmpty()
    }

    @Test
    fun retryAfterLongerThanTheBackoffQueuesTheNextTryNoEarlier() = runTest {
        started()
        server.script += failed(SyncServerProblem.RATE_LIMITED, retryAfter = 600)

        scheduler.syncNow()
        runCurrent()

        val first = infos(SyncServerScheduler.NOW).first()
        assertThat(settled(first.id).state).isEqualTo(WorkInfo.State.SUCCEEDED)
        val next = settledPending()
        assertThat(next.initialDelayMillis).isEqualTo(600_000)
        assertThat(next.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)

        // It runs when its time comes, counting as the second attempt.
        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        driver.setAllConstraintsMet(next.id)
        driver.setInitialDelayMet(next.id)
        assertThat(settled(next.id).state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(server.calls).isEqualTo(2)
    }

    /** The retry queued behind a finished run shows up once that run has settled. */
    private fun settledPending(): WorkInfo {
        repeat(200) {
            pending().firstOrNull()?.let { return it }
            Thread.sleep(25)
        }
        error("No retry was queued")
    }

    @Test
    fun aPassingProblemIsRetriedWithBackoffAndOneThatNeedsAPersonIsNot() = runTest {
        started()
        server.script += failed(SyncServerProblem.UNREACHABLE)

        scheduler.syncNow()
        runCurrent()

        val id = infos(SyncServerScheduler.NOW).single().id
        val retried = settled(id) { it.runAttemptCount == 1 && it.state == WorkInfo.State.ENQUEUED }
        assertThat(retried.runAttemptCount).isEqualTo(1)

        server.script += failed(SyncServerProblem.WRONG_KEY)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(id)
        assertThat(settled(id).state).isEqualTo(WorkInfo.State.FAILED)
        assertThat(server.calls).isEqualTo(2)
    }

    @Test
    fun everyRequestBacksOffExponentiallyFrom30s() {
        listOf(SyncServerScheduler.oneTimeRequest(0), SyncServerScheduler.hourlyRequest()).forEach { request ->
            assertThat(request.workSpec.backoffPolicy).isEqualTo(BackoffPolicy.EXPONENTIAL)
            assertThat(request.workSpec.backoffDelayDuration).isEqualTo(30_000)
        }
    }

    @Test
    fun workerDecisions() {
        val synced = SyncServerResult.Synced(SyncChanges.NONE, 1, 1, uploaded = true)
        assertThat(SyncServerWorker.decide(synced, 0)).isEqualTo(SyncServerWorker.Next.Done)
        assertThat(SyncServerWorker.decide(SyncServerResult.NotConfigured, 0)).isEqualTo(SyncServerWorker.Next.Done)
        SyncServerProblem.entries.forEach { problem ->
            val expected = if (problem.transient) SyncServerWorker.Next.Retry else SyncServerWorker.Next.Fail
            assertThat(SyncServerWorker.decide(failed(problem), 0)).isEqualTo(expected)
            // Someone is waiting on a join: never retry.
            assertThat(SyncServerWorker.decide(failed(problem), 0, watched = true)).isEqualTo(SyncServerWorker.Next.Fail)
        }
        assertThat(SyncServerProblem.entries.filter { it.transient }).containsExactly(
            SyncServerProblem.UNREACHABLE,
            SyncServerProblem.TIMEOUT,
            SyncServerProblem.SERVER,
            SyncServerProblem.RATE_LIMITED,
            SyncServerProblem.SERVER_FULL,
        )
        val last = SyncServerWorker.MAX_ATTEMPTS - 1
        assertThat(SyncServerWorker.decide(failed(SyncServerProblem.TIMEOUT), last)).isEqualTo(SyncServerWorker.Next.Fail)
        // Retry-After: within the backoff, WorkManager's own retry; longer, a run queued no earlier.
        assertThat(SyncServerWorker.backoffSeconds(0)).isEqualTo(30)
        assertThat(SyncServerWorker.backoffSeconds(2)).isEqualTo(120)
        assertThat(SyncServerWorker.decide(failed(SyncServerProblem.SERVER, retryAfter = 30), 0))
            .isEqualTo(SyncServerWorker.Next.Retry)
        assertThat(SyncServerWorker.decide(failed(SyncServerProblem.SERVER, retryAfter = 30), 2))
            .isEqualTo(SyncServerWorker.Next.Retry)
        assertThat(SyncServerWorker.decide(failed(SyncServerProblem.SERVER_FULL, retryAfter = 3600), 0))
            .isEqualTo(SyncServerWorker.Next.RetryAfter(3600))
        assertThat(SyncServerWorker.decide(failed(SyncServerProblem.RATE_LIMITED, retryAfter = 45), 0))
            .isEqualTo(SyncServerWorker.Next.RetryAfter(45))
    }

    private fun failed(problem: SyncServerProblem, retryAfter: Long? = null) =
        SyncServerResult.Failed(SyncServerError(problem, problem.name, 1, retryAfter))
}
