package dev.quinton.goa.feature.sync.hub

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
import dev.quinton.goa.core.data.HubError
import dev.quinton.goa.core.data.HubProblem
import dev.quinton.goa.core.data.HubSyncRepository
import dev.quinton.goa.core.data.HubSyncResult
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.feature.sync.TestGroup
import dev.quinton.goa.feature.sync.groupState
import java.util.UUID
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

/** A hub that answers from a script; counts round trips. */
class FakeHubSyncRepository : HubSyncRepository {
    val script = java.util.concurrent.ConcurrentLinkedQueue<HubSyncResult>()
    private val count = AtomicInteger()
    val calls: Int get() = count.get()
    override val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    override suspend fun sync(): HubSyncResult {
        count.incrementAndGet()
        return script.poll() ?: HubSyncResult.Synced(SyncChanges.NONE, 1)
    }
}

/**
 * WorkManager scheduling of hub sync (ADR-030 §5), on WorkManager's test driver: what is
 * scheduled when, with which constraints and backoff, and the 15 s debounce after changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HubSyncSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val hub = FakeHubSyncRepository()
    private val groups = FakeGroupRepository(groupState().copy(hubUrl = "https://goa.example"))
    private val scheduler = HubSyncScheduler(context, groups, hub)
    private val workManager get() = WorkManager.getInstance(context)

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(context: Context, name: String, params: WorkerParameters): ListenableWorker =
                HubSyncWorker(context, params, hub)
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

    private fun pending(name: String = HubSyncScheduler.NOW) = infos(name).filter {
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
    fun inAGroupWithAHubAnHourlySyncIsScheduledWithANetwork() = runTest {
        started()

        val hourly = pending(HubSyncScheduler.HOURLY).single()
        assertThat(hourly.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(1))
        assertThat(hourly.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        // Starting the process (it may be a background wake-up) is not "the app opened".
        assertThat(infos(HubSyncScheduler.NOW)).isEmpty()
    }

    @Test
    fun openingTheAppSyncsAtOnceWhenOnline() = runTest {
        started()

        scheduler.onAppOpened()
        runCurrent()

        val now = pending().single()
        assertThat(now.initialDelayMillis).isEqualTo(0)
        assertThat(now.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(now.id)
        assertThat(settled(now.id).state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(hub.calls).isEqualTo(1)
    }

    @Test
    fun onScreenTheAppSyncsAtOnceThenEveryMinuteUntilItLeaves() = runTest {
        started()

        scheduler.onForeground()
        runCurrent()
        val first = pending().single()
        assertThat(first.initialDelayMillis).isEqualTo(0)

        // A phone left open at the table hears about the others' matches within a minute.
        advanceTimeBy(HubSyncScheduler.FOREGROUND_POLL_MS + 1)
        val second = pending().single()
        assertThat(second.id).isNotEqualTo(first.id)

        scheduler.onBackground()
        advanceTimeBy(5 * HubSyncScheduler.FOREGROUND_POLL_MS)
        assertThat(pending().single().id).isEqualTo(second.id)
    }

    @Test
    fun thePollLeavesAProblemThatNeedsAPersonAlone() = runTest {
        started()
        groups.current.value = groups.current.value.copy(hubError = HubError(HubProblem.WRONG_KEY, "key", 1))

        scheduler.onForeground()
        runCurrent()
        // Coming on screen still tries once, so a fixed hub shows at once.
        val first = pending().single()
        advanceTimeBy(3 * HubSyncScheduler.FOREGROUND_POLL_MS)
        assertThat(pending().single().id).isEqualTo(first.id)
        scheduler.onBackground()
    }

    @Test
    fun aChangeSyncs15sLaterAndEachNewChangeRestartsTheWait() = runTest {
        started()

        hub.localChanges.emit(Unit)
        hub.localChanges.emit(Unit)
        advanceTimeBy(1_100)
        val first = pending().single()
        assertThat(first.initialDelayMillis).isEqualTo(15_000)
        assertThat(first.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)

        advanceTimeBy(5_000)
        hub.localChanges.emit(Unit)
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
        assertThat(hub.calls).isEqualTo(1)
    }

    @Test
    fun withoutAHubNothingIsScheduledAndRemovingItCancelsEverything() = runTest {
        groups.current.value = groupState()
        started()
        hub.localChanges.emit(Unit)
        advanceTimeBy(1_100)
        scheduler.onAppOpened()
        runCurrent()
        assertThat(pending()).isEmpty()
        assertThat(pending(HubSyncScheduler.HOURLY)).isEmpty()

        groups.setHubUrl("https://goa.example")
        runCurrent()
        assertThat(pending(HubSyncScheduler.HOURLY)).hasSize(1)

        groups.setHubUrl(null)
        runCurrent()
        assertThat(pending()).isEmpty()
        assertThat(pending(HubSyncScheduler.HOURLY)).isEmpty()
    }

    @Test
    fun aNewHubAddressOrGroupSyncsRightAway() = runTest {
        started()

        groups.setHubUrl("https://other.example")
        runCurrent()

        val now = pending().single()
        assertThat(now.initialDelayMillis).isEqualTo(0)
        groups.leave()
        runCurrent()
        groups.join(TestGroup, hubUrl = "https://goa.example")
        runCurrent()
        assertThat(pending()).hasSize(1)
    }

    @Test
    fun syncNowRunsEvenOfflineSoTheOutcomeShows() = runTest {
        started()

        scheduler.syncNow()
        runCurrent()

        assertThat(settled(infos(HubSyncScheduler.NOW).single().id).state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(hub.calls).isEqualTo(1)
    }

    @Test
    fun aPassingProblemIsRetriedWithBackoffAndOneThatNeedsAPersonIsNot() = runTest {
        started()
        hub.script += failed(HubProblem.UNREACHABLE)

        scheduler.syncNow()
        runCurrent()

        val id = infos(HubSyncScheduler.NOW).single().id
        val retried = settled(id) { it.runAttemptCount == 1 && it.state == WorkInfo.State.ENQUEUED }
        assertThat(retried.runAttemptCount).isEqualTo(1)

        hub.script += failed(HubProblem.WRONG_KEY)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(id)
        assertThat(settled(id).state).isEqualTo(WorkInfo.State.FAILED)
        assertThat(hub.calls).isEqualTo(2)
    }

    @Test
    fun everyRequestBacksOffExponentiallyFrom30s() {
        listOf(HubSyncScheduler.oneTimeRequest(0), HubSyncScheduler.hourlyRequest()).forEach { request ->
            assertThat(request.workSpec.backoffPolicy).isEqualTo(BackoffPolicy.EXPONENTIAL)
            assertThat(request.workSpec.backoffDelayDuration).isEqualTo(30_000)
        }
    }

    @Test
    fun workerOutcomes() {
        val synced = HubSyncResult.Synced(SyncChanges.NONE, 1)
        assertThat(HubSyncWorker.outcome(synced, 0)).isEqualTo(Result.success())
        assertThat(HubSyncWorker.outcome(HubSyncResult.NotConfigured, 0)).isEqualTo(Result.success())
        HubProblem.entries.forEach { problem ->
            val expected = if (problem.transient) Result.retry() else Result.failure()
            assertThat(HubSyncWorker.outcome(failed(problem), 0)).isEqualTo(expected)
        }
        val last = HubSyncWorker.MAX_ATTEMPTS - 1
        assertThat(HubSyncWorker.outcome(failed(HubProblem.TIMEOUT), last)).isEqualTo(Result.failure())
    }

    private fun failed(problem: HubProblem) = HubSyncResult.Failed(HubError(problem, problem.name, 1))
}
