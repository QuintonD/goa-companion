package dev.quinton.goa.feature.timer.session

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.di.TimerClock
import dev.quinton.goa.feature.timer.system.BootCountProvider
import dev.quinton.goa.feature.timer.system.TimerNotifier
import dev.quinton.goa.feature.timer.system.TimerScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** User and system commands on the running session. */
sealed interface TimerCommand {
    data object TogglePause : TimerCommand

    data object Pause : TimerCommand

    data object Resume : TimerCommand

    data object Next : TimerCommand

    data object Back : TimerCommand

    data object Restart : TimerCommand

    data object SkipToEndOfRound : TimerCommand

    data object EndMatch : TimerCommand

    data object AdvanceDue : TimerCommand

    data object FlipTieBreaker : TimerCommand

    data class HeroResolves(val seat: Int) : TimerCommand
}

/**
 * Single entry point for every timer change, from the UI, notification actions, alarms and
 * system broadcasts. Each command runs the pure [TimerEngine] on the persisted snapshot, writes
 * it back, then re-syncs the exact alarm and the ongoing notification (ADR-007).
 */
@Singleton
class TimerController @Inject constructor(
    private val repository: TimerRepository,
    @param:TimerClock private val elapsed: ElapsedClock,
    @param:TimerClock private val wall: WallClock,
    private val scheduler: TimerScheduler,
    private val notifier: TimerNotifier,
    private val boot: BootCountProvider,
) {
    private val engine = TimerEngine(elapsed, wall)
    private val mutex = Mutex()

    /** True while the timer screen is on screen: in-app cues replace the background alert. */
    @Volatile var uiVisible: Boolean = false

    /** The session on the current boot's timeline (rebased after a reboot). */
    val snapshot: Flow<TimerSnapshot?> = repository.snapshot.map { it?.let(::rebase) }

    fun readout(snapshot: TimerSnapshot) = engine.readout(snapshot)

    fun cuesCrossed(snapshot: TimerSnapshot, fromElapsed: Long, toElapsed: Long): List<Int> =
        engine.cuesCrossed(snapshot.step, fromElapsed, toElapsed)

    suspend fun start(
        preset: TimerPreset,
        actionSlots: Int,
        matchId: String? = null,
        mode: GameMode? = null,
        tieBreaker: Team? = null,
    ): TimerSnapshot = mutex.withLock {
        val snapshot = engine.start(
            sessionId = UUID.randomUUID().toString(),
            preset = preset,
            endOfRound = repository.rules().endOfRound,
            actionSlots = actionSlots,
            matchId = matchId,
            bootCount = boot.bootCount(),
            mode = mode,
            tieBreaker = tieBreaker,
        )
        repository.updateSnapshot { snapshot }
        sync(snapshot)
        snapshot
    }

    suspend fun perform(command: TimerCommand): TimerSnapshot? = mutex.withLock {
        val updated = repository.updateSnapshot { current -> current?.let { apply(rebase(it), command) } }
        sync(updated)
        updated
    }

    /** Clears the session (after the match summary is dismissed). */
    suspend fun clear() = mutex.withLock {
        repository.updateSnapshot { null }
        sync(null)
    }

    /**
     * The phase-end alarm fired. Ignores stale alarms (the step changed since scheduling),
     * auto-advances if the preset says so, and alerts unless the timer screen is visible.
     */
    suspend fun onAlarm(stepKey: String?) = mutex.withLock {
        val current = repository.currentSnapshot()?.let(::rebase)
        if (current == null || current.status != TimerStatus.RUNNING) {
            sync(current)
            return@withLock
        }
        val readout = engine.readout(current)
        val matches = stepKey == null || stepKey == stepKey(current, readout.phaseEndElapsed)
        val due = readout.remainingMillis != null && readout.remainingMillis!! <= 0
        if (!matches || !due) {
            sync(current)
            return@withLock
        }
        val endedTitle = TimerText.stepTitle(current.step)
        val updated = repository.updateSnapshot { s -> s?.let { engine.advanceDue(rebase(it)) } }
        if (!uiVisible && updated != null) notifier.alertPhaseOver(updated, endedTitle)
        sync(updated)
    }

    /** Re-establishes the alarm and notification (app start, boot, time change, app update). */
    suspend fun resync() = mutex.withLock {
        val updated = repository.updateSnapshot { s -> s?.let { engine.advanceDue(rebase(it)) } }
        sync(updated)
    }

    private fun rebase(s: TimerSnapshot): TimerSnapshot =
        s.rebased(elapsed.elapsedMillis(), wall.nowMillis(), boot.bootCount())

    private fun apply(s: TimerSnapshot, command: TimerCommand): TimerSnapshot = when (command) {
        TimerCommand.TogglePause -> engine.togglePause(s)
        TimerCommand.Pause -> engine.pause(s)
        TimerCommand.Resume -> engine.resume(s)
        TimerCommand.Next -> engine.next(s)
        TimerCommand.Back -> engine.back(s)
        TimerCommand.Restart -> engine.restartStep(s)
        TimerCommand.SkipToEndOfRound -> engine.skipToEndOfRound(s)
        TimerCommand.EndMatch -> engine.endMatch(s)
        TimerCommand.AdvanceDue -> engine.advanceDue(s)
        TimerCommand.FlipTieBreaker -> engine.flipTieBreaker(s)
        is TimerCommand.HeroResolves -> engine.heroResolves(s, command.seat)
    }

    private fun sync(snapshot: TimerSnapshot?) {
        if (snapshot == null || snapshot.status == TimerStatus.ENDED) {
            scheduler.cancel()
            notifier.cancelOngoing()
            return
        }
        val readout = engine.readout(snapshot)
        val end = readout.phaseEndElapsed
        if (end != null && end > elapsed.elapsedMillis()) {
            scheduler.schedule(end, stepKey(snapshot, end))
        } else {
            scheduler.cancel()
        }
        notifier.showOngoing(snapshot, readout, wall.nowMillis())
    }

    companion object {
        fun stepKey(s: TimerSnapshot, phaseEnd: Long?): String = "${s.sessionId}:${s.round}:${s.stepIndex}:$phaseEnd"
    }
}
