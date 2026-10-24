package dev.quinton.goa.feature.sync.server

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    fun aSyncRunHereShowsAsRunning() = runTest {
        started()
        val result = scheduler.syncOnce()
        assertThat(result).isInstanceOf(SyncServerResult.Synced::class.java)
        assertThat(server.calls).isEqualTo(1)
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
    fun workerOutcomes() {
        val synced = SyncServerResult.Synced(SyncChanges.NONE, 1, 1, uploaded = true)
        assertThat(SyncServerWorker.outcome(synced, 0)).isEqualTo(Result.success())
        assertThat(SyncServerWorker.outcome(SyncServerResult.NotConfigured, 0)).isEqualTo(Result.success())
        SyncServerProblem.entries.forEach { problem ->
            val expected = if (problem.transient) Result.retry() else Result.failure()
            assertThat(SyncServerWorker.outcome(failed(problem), 0)).isEqualTo(expected)
        }
        val transient = SyncServerProblem.entries.filter { it.transient }
        assertThat(transient).containsExactly(
            SyncServerProblem.UNREACHABLE,
            SyncServerProblem.TIMEOUT,
            SyncServerProblem.SERVER,
            SyncServerProblem.RATE_LIMITED,
        )
        val last = SyncServerWorker.MAX_ATTEMPTS - 1
        assertThat(SyncServerWorker.outcome(failed(SyncServerProblem.TIMEOUT), last)).isEqualTo(Result.failure())
    }

    private fun failed(problem: SyncServerProblem) =
        SyncServerResult.Failed(SyncServerError(problem, problem.name, 1))
}
