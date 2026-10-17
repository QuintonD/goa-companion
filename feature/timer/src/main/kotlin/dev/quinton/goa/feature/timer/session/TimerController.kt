package dev.quinton.goa.feature.timer.session

import dagger.Lazy
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.timer.MatchDuration
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.di.TimerClock
import dev.quinton.goa.feature.timer.system.AlertStyle
import dev.quinton.goa.feature.timer.system.BootCountProvider
import dev.quinton.goa.feature.timer.system.TimerNotifier
import dev.quinton.goa.feature.timer.system.TimerScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /** Which team's symbol shows on the coin (null = stop tracking). */
    data class SetTieBreaker(val team: Team?) : TimerCommand

    data class HeroResolves(val seat: Int) : TimerCommand

    data object StartShotClock : TimerCommand

    data object CancelShotClock : TimerCommand

    data class TimeOut(val team: Team) : TimerCommand
}

/**
 * Single entry point for every timer change, from the UI, notification actions, alarms and
 * system broadcasts. Each command runs the pure [TimerEngine] on the persisted snapshot, writes
 * it back (re-anchored at the current clock pair), then re-syncs the exact alarm and the ongoing
 * notification (ADR-007).
 *
 * The controller is also the single writer of the linked match's duration: when a session ends,
 * and again (only if still missing) before an ended session is replaced or cleared.
 */
@Singleton
class TimerController @Inject constructor(
    private val repository: TimerRepository,
    @param:TimerClock private val elapsed: ElapsedClock,
    @param:TimerClock private val wall: WallClock,
    private val scheduler: TimerScheduler,
    private val notifier: TimerNotifier,
    private val boot: BootCountProvider,
    /** Lazy: the startup provider builds the controller at process start; Room isn't needed then. */
    private val matches: Lazy<MatchRepository>,
    /** Sounds and haptics switches, honoured by the background phase-over alert too. */
    private val settings: Lazy<SettingsRepository>,
) {
    private val engine = TimerEngine(elapsed, wall)
    private val mutex = Mutex()

    /** Phase end that last raised a "phase over" alert in this process (dedupes alarm vs resync). */
    private var lastAlertKey: String? = null

    /** True while the timer screen is on screen: in-app cues replace the background alert. */
    @Volatile var uiVisible: Boolean = false

    /** The session on the current boot's timeline (rebased after a reboot). */
    val snapshot: Flow<TimerSnapshot?> = repository.snapshot.map { it?.let(::rebase) }

    fun readout(snapshot: TimerSnapshot) = engine.readout(snapshot)

    fun cuesCrossed(snapshot: TimerSnapshot, fromElapsed: Long, toElapsed: Long): List<Int> =
        engine.cuesCrossed(snapshot.step, fromElapsed, toElapsed)

    /** False when the countdown notification and phase-over alerts can't be shown. */
    fun notificationsEnabled(): Boolean = notifier.canPost()

    suspend fun start(
        preset: TimerPreset,
        actionSlots: Int,
        matchId: String? = null,
        mode: GameMode? = null,
        tieBreaker: Team? = null,
    ): TimerSnapshot = mutex.withLock {
        repository.currentSnapshot()?.let { previous -> recordDuration(rebase(previous), onlyIfMissing = true) }
        notifier.cancelAlert()
        val snapshot = engine.start(
            sessionId = UUID.randomUUID().toString(),
            // Every start path (Timer tab, Match setup) gets tonight's turn length.
            preset = repository.withTonight(preset),
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
        // Any user action means the table has seen it: a stale "Time's up" alert goes away.
        if (command != TimerCommand.AdvanceDue) notifier.cancelAlert()
        var before: TimerSnapshot? = null
        val updated = repository.updateSnapshot { current ->
            current?.let { rebase(it) }?.also { before = it }?.let { apply(it, command) }
        }
        if (updated != null && updated.status == TimerStatus.ENDED && before?.status != TimerStatus.ENDED) {
            recordDuration(updated, onlyIfMissing = false)
        }
        sync(updated)
        updated
    }

    /** Clears the session (after the match summary). An ended session's duration is kept first. */
    suspend fun clear() = mutex.withLock {
        repository.currentSnapshot()?.let { recordDuration(rebase(it), onlyIfMissing = true) }
        repository.updateSnapshot { null }
        notifier.cancelAlert()
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
        val currentKey = stepKey(current, readout.phaseEndElapsed)
        val matches = stepKey == null || stepKey == currentKey
        val due = readout.remainingMillis != null && readout.remainingMillis!! <= 0
        if (!matches || !due) {
            sync(current)
            return@withLock
        }
        val updated = repository.updateSnapshot { s -> s?.let { engine.advanceDue(rebase(it)) } }
        if (updated != null) alertOnce(currentKey, TimerText.endedTitle(current.step, current.round), updated)
        sync(updated)
    }

    /**
     * Re-establishes the alarm and notification (app start, boot, time change, app update,
     * swiped-away notification). If auto-advance moved past a phase end that no alarm reported
     * (e.g. this cold start raced the alarm broadcast), alerts for it once.
     */
    suspend fun resync() = mutex.withLock {
        var before: TimerSnapshot? = null
        val updated = repository.updateSnapshot { s ->
            s?.let { rebase(it) }?.also { before = it }?.let { engine.advanceDue(it) }
        }
        val previous = before
        if (previous != null && updated != null && advanced(previous, updated)) {
            val key = stepKey(previous, engine.readout(previous).phaseEndElapsed)
            alertOnce(key, TimerText.endedTitle(previous.step, previous.round), updated)
        }
        sync(updated)
    }

    private fun advanced(from: TimerSnapshot, to: TimerSnapshot): Boolean =
        from.sessionId == to.sessionId && (from.round != to.round || from.stepIndex != to.stepIndex)

    private suspend fun alertOnce(phaseEndKey: String, endedTitle: String, updated: TimerSnapshot) {
        if (uiVisible || phaseEndKey == lastAlertKey) return
        lastAlertKey = phaseEndKey
        val prefs = settings.get().settings.first()
        val style = AlertStyle.of(sound = prefs.soundsEnabled, haptics = prefs.hapticsEnabled)
        notifier.alertPhaseOver(updated, endedTitle, style)
    }

    /** Writes the ended session's duration (and mode) into its linked match. */
    private suspend fun recordDuration(s: TimerSnapshot, onlyIfMissing: Boolean) {
        if (s.status != TimerStatus.ENDED) return
        val id = s.matchId ?: return
        val repo = matches.get()
        val match = repo.observeMatch(id).first() ?: return
        if (onlyIfMissing && match.durationMin != null) return
        val minutes = MatchDuration.minutes(engine.readout(s).matchElapsedMillis)
        repo.upsert(match.copy(durationMin = minutes, mode = s.mode ?: match.mode))
    }

    private fun rebase(s: TimerSnapshot): TimerSnapshot =
        s.rebased(elapsed.elapsedMillis(), wall.nowMillis(), boot.bootCount())

    private fun apply(s: TimerSnapshot, command: TimerCommand): TimerSnapshot = when (command) {
        TimerCommand.TogglePause -> engine.togglePause(s)
        TimerCommand.Pause -> engine.pause(s)
        TimerCommand.Resume -> engine.resume(s)
        TimerCommand.Next -> engine.next(s, TimerEngine.NEXT_DEBOUNCE_MS)
        TimerCommand.Back -> engine.back(s)
        TimerCommand.Restart -> engine.restartStep(s)
        TimerCommand.SkipToEndOfRound -> engine.skipToEndOfRound(s)
        TimerCommand.EndMatch -> engine.endMatch(s)
        TimerCommand.AdvanceDue -> engine.advanceDue(s)
        TimerCommand.FlipTieBreaker -> engine.flipTieBreaker(s)
        is TimerCommand.HeroResolves -> engine.heroResolves(s, command.seat)
        is TimerCommand.SetTieBreaker -> engine.setTieBreaker(s, command.team)
        TimerCommand.StartShotClock -> engine.startShotClock(s)
        TimerCommand.CancelShotClock -> engine.cancelShotClock(s)
        is TimerCommand.TimeOut -> engine.timeOut(s, command.team)
    }

    fun timeOutsLeft(s: TimerSnapshot, team: Team): Int = engine.timeOutsLeft(s, team)

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
