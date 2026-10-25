package dev.quinton.goa.web.timer

import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.session.TimerCommand
import dev.quinton.goa.feature.timer.session.TimerSessions
import kotlin.js.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The web app's clock for the timer (ADR-043 "Table, W3"): wall-clock milliseconds that never step
 * backwards within a page. Not `performance.now()`: on iPhones it stands still while the phone sleeps
 * (Safari's monotonic clock doesn't count sleep), so a locked phone would lose the time it was dark.
 * The snapshot's wall anchor rebases it after a reload (TimerSnapshot.rebased).
 */
object WebTimerClock : ElapsedClock, WallClock {
    private var last = 0L

    override fun elapsedMillis(): Long {
        val now = Date.now().toLong()
        if (now > last) last = now
        return last
    }

    override fun nowMillis(): Long = Date.now().toLong()
}

/**
 * Every timer change goes through here: each command runs the pure [TimerEngine] on the stored
 * snapshot ([TimerSessions.apply], shared with the phone's `TimerController`) and writes it back,
 * re-anchored at the current clock. The web app has no alarms or notifications: a page can't run
 * while the iPhone is locked, so [resync] catches up (auto-advance included) when it is seen again.
 *
 * It is also the single writer of the linked match's duration: when a session ends, and again (only
 * if still missing) before an ended session is replaced or cleared.
 */
class WebTimerController(
    private val repository: WebTimerRepository,
    /** The group's match log (W2), read only when a session with a linked match ends. */
    private val matches: () -> MatchRepository,
    private val elapsed: ElapsedClock = WebTimerClock,
    private val wall: WallClock = WebTimerClock,
) {
    private val engine = TimerEngine(elapsed, wall)
    private val lock = Mutex()

    /** The session, re-anchored at the current clock. */
    val snapshot: Flow<TimerSnapshot?> = repository.snapshot.map { it?.let(::rebase) }

    fun readout(snapshot: TimerSnapshot): TimerReadout = engine.readout(snapshot)

    fun cuesCrossed(snapshot: TimerSnapshot, fromElapsed: Long, toElapsed: Long): List<Int> =
        engine.cuesCrossed(snapshot.step, fromElapsed, toElapsed)

    fun timeOutsLeft(s: TimerSnapshot, team: Team): Int = engine.timeOutsLeft(s, team)

    fun elapsedNow(): Long = elapsed.elapsedMillis()

    @OptIn(ExperimentalUuidApi::class)
    suspend fun start(
        preset: TimerPreset,
        actionSlots: Int,
        matchId: String? = null,
        mode: GameMode? = null,
        tieBreaker: Team? = null,
    ): TimerSnapshot = lock.withLock {
        repository.currentSnapshot()?.let { previous -> recordDuration(rebase(previous), onlyIfMissing = true) }
        val endOfRound = repository.rules().endOfRound
        val snapshot = engine.start(
            sessionId = Uuid.random().toString(),
            // Every start path (Timer tab, Match setup) gets tonight's turn length.
            preset = repository.withTonight(preset),
            endOfRound = endOfRound,
            actionSlots = actionSlots,
            matchId = matchId,
            mode = mode,
            tieBreaker = tieBreaker,
        )
        repository.updateSnapshot { snapshot }
        snapshot
    }

    suspend fun perform(command: TimerCommand): TimerSnapshot? = lock.withLock {
        var before: TimerSnapshot? = null
        val updated = repository.updateSnapshot { current ->
            current?.let { rebase(it) }?.also { before = it }?.let { TimerSessions.apply(engine, it, command) }
        }
        if (updated != null && updated.status == TimerStatus.ENDED && before?.status != TimerStatus.ENDED) {
            recordDuration(updated, onlyIfMissing = false)
        }
        updated
    }

    /**
     * Back on the page (reload, unlocking the phone, coming back from another app): re-anchors the
     * session and lets auto-advance catch up on phases that ended while nobody could see them.
     * True when it moved on past a phase that ended unseen (the phone's background alert says so; here
     * the screen does).
     */
    suspend fun resync(): Boolean = lock.withLock {
        var before: TimerSnapshot? = null
        val updated = repository.updateSnapshot { s ->
            s?.let { rebase(it) }?.also { before = it }?.let { engine.advanceDue(it) }
        }
        val previous = before
        previous != null && updated != null && previous.sessionId == updated.sessionId &&
            (previous.round != updated.round || previous.stepIndex != updated.stepIndex)
    }

    /**
     * The id for the new match "Log result" opens from an ended session without a linked match;
     * reserved once and kept in the session, so every Log result tap opens the same match.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun reserveLoggingMatchId(): String? = lock.withLock {
        val updated = repository.updateSnapshot { current ->
            when {
                current == null || current.status != TimerStatus.ENDED -> current
                current.loggingMatchId != null -> current
                else -> current.copy(loggingMatchId = Uuid.random().toString())
            }
        }
        updated?.takeIf { it.status == TimerStatus.ENDED }?.loggingMatchId
    }

    /** Clears the ended session once the match it was being logged as ([matchId]) is saved. */
    suspend fun clearLogged(matchId: String) = lock.withLock {
        val current = repository.currentSnapshot() ?: return@withLock
        if (current.status != TimerStatus.ENDED || current.loggingMatchId != matchId) return@withLock
        repository.updateSnapshot { null }
    }

    /** Clears the session (after the match summary). An ended session's duration is kept first. */
    suspend fun clear() = lock.withLock {
        repository.currentSnapshot()?.let { recordDuration(rebase(it), onlyIfMissing = true) }
        repository.updateSnapshot { null }
    }

    /** Writes the ended session's duration (and mode) into its linked match. */
    private suspend fun recordDuration(s: TimerSnapshot, onlyIfMissing: Boolean) {
        if (s.status != TimerStatus.ENDED) return
        val id = s.matchId ?: return
        try {
            val repo = matches()
            val match = repo.observeMatch(id).first() ?: return
            TimerSessions.durationUpdate(s, match, engine.readout(s).matchElapsedMillis, onlyIfMissing)
                ?.let { repo.upsert(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // The match log is unavailable (storage refused the write): the timer must still end.
            console.warn("Couldn't save the match duration", e)
        }
    }

    private fun rebase(s: TimerSnapshot): TimerSnapshot = s.rebased(elapsed.elapsedMillis(), wall.nowMillis(), null)
}
