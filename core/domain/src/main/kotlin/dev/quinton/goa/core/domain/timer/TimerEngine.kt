package dev.quinton.goa.core.domain.timer

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team

/** What the UI and notification show at one instant, derived from a snapshot and the clock. */
data class TimerReadout(
    val round: Int,
    val step: TimerStep,
    val status: TimerStatus,
    val elapsedInStepMillis: Long,
    /** Null for untimed steps; negative = overtime. */
    val remainingMillis: Long?,
    /** Elapsed timestamp at which the running timed step reaches zero; null otherwise. */
    val phaseEndElapsed: Long?,
    val matchElapsedMillis: Long,
    /** "Hurry up" shot clock time left (negative = over), null when none is running. */
    val shotClockRemainingMillis: Long? = null,
) {
    val overtime: Boolean get() = remainingMillis != null && remainingMillis < 0

    /** Fraction of the countdown left, 1 → 0 (0 in overtime, 1 for untimed). */
    val fractionRemaining: Float
        get() {
            val total = step.durationMillis ?: return 1f
            val left = remainingMillis ?: return 1f
            return if (total <= 0) 0f else (left.toFloat() / total).coerceIn(0f, 1f)
        }
}

/** Match length as written to the match log. */
object MatchDuration {
    /** A forgotten timer must not log days of play; 10 h is far beyond any real match. */
    const val MAX_MINUTES = 600

    /** Whole minutes, rounded to nearest, clamped to 1..[MAX_MINUTES]. */
    fun minutes(millis: Long): Int =
        ((millis.coerceAtLeast(0) + HALF_MINUTE_MS) / MINUTE_MS).coerceIn(1L, MAX_MINUTES.toLong()).toInt()

    private const val MINUTE_MS = 60_000L
    private const val HALF_MINUTE_MS = 30_000L
}

/**
 * Pure state machine for the phase timer. Every operation takes a snapshot and returns the next
 * one; time comes from the injected clocks only. Sequence per round: see [TimerPlan].
 *
 * Navigation (next, back, skip, jump) always leaves the timer running with a fresh step, except that
 * Back within [UNDO_WINDOW_MS] of a Next (or a jump) undoes it (the previous step continues where it
 * was, as if Next had never been tapped). Restart keeps the paused/running state. Ended sessions
 * ignore every operation.
 */
class TimerEngine(
    private val elapsed: ElapsedClock,
    private val wall: WallClock,
) {
    fun start(
        sessionId: String,
        preset: TimerPreset,
        endOfRound: List<EndOfRoundStep>,
        actionSlots: Int,
        matchId: String? = null,
        bootCount: Int? = null,
        mode: GameMode? = null,
        tieBreaker: Team? = null,
    ): TimerSnapshot {
        val now = elapsed.elapsedMillis()
        val wallNow = wall.nowMillis()
        val snapshot = TimerSnapshot(
            sessionId = sessionId,
            preset = preset,
            endOfRound = endOfRound,
            actionSlots = actionSlots.coerceAtLeast(1),
            matchId = matchId,
            mode = mode,
            tieBreaker = tieBreaker,
            status = TimerStatus.RUNNING,
            runningSince = now,
            matchStartElapsed = now,
            matchStartWall = wallNow,
            anchorElapsed = now,
            anchorWall = wallNow,
            bootCount = bootCount,
            stepEnteredAt = now,
        )
        require(snapshot.plan.steps.isNotEmpty()) { "A round needs at least one step" }
        return snapshot
    }

    fun readout(s: TimerSnapshot): TimerReadout {
        val now = elapsed.elapsedMillis()
        val inStep = elapsedInStep(s, now)
        val duration = s.step.durationMillis
        return TimerReadout(
            round = s.round,
            step = s.step,
            status = s.status,
            elapsedInStepMillis = inStep,
            remainingMillis = duration?.let { it - inStep },
            phaseEndElapsed = if (s.status == TimerStatus.RUNNING && duration != null) {
                s.runningSince!! - s.accumulatedMillis + duration
            } else {
                null
            },
            matchElapsedMillis = (s.endedElapsed ?: now) - s.matchStartElapsed,
            shotClockRemainingMillis = s.shotClock?.let { it.durationMillis - shotElapsed(it, now) },
        )
    }

    fun pause(s: TimerSnapshot): TimerSnapshot {
        if (s.status != TimerStatus.RUNNING) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            status = TimerStatus.PAUSED,
            accumulatedMillis = elapsedInStep(s, now),
            runningSince = null,
            shotClock = s.shotClock?.let { it.copy(accumulatedMillis = shotElapsed(it, now), runningSince = null) },
        )
    }

    fun resume(s: TimerSnapshot): TimerSnapshot {
        if (s.status != TimerStatus.PAUSED) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            status = TimerStatus.RUNNING,
            runningSince = now,
            shotClock = s.shotClock?.copy(runningSince = now),
        )
    }

    fun togglePause(s: TimerSnapshot): TimerSnapshot = if (s.status == TimerStatus.RUNNING) pause(s) else resume(s)

    /**
     * Next step (the next round after the last one). A user Next passes [debounceMillis]: within
     * that long of the current step being entered (by a tap or auto-advance) the tap is ignored,
     * so a double tap can't skip a phase. The step left behind is kept as the [TimerSnapshot.undo]
     * point.
     */
    fun next(s: TimerSnapshot, debounceMillis: Long = 0): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val now = elapsed.elapsedMillis()
        val entered = s.stepEnteredAt
        if (debounceMillis > 0 && entered != null && now - entered in 0 until debounceMillis) return s
        val undo = undoPoint(s, now)
        val moved = if (s.stepIndex >= s.plan.lastIndex) {
            s.moveTo(round = s.round + 1, index = 0, startedAt = now)
        } else {
            s.moveTo(round = s.round, index = s.stepIndex + 1, startedAt = now)
        }
        return autoPickLastHero(moved).copy(undo = undo)
    }

    /**
     * Jumps straight to step [stepIndex] of the current round (the round view's "Jump to …"). It
     * navigates like Next and Back: the target starts fresh and running, a running Hurry up is
     * cleared, and the step left behind is kept as the [TimerSnapshot.undo] point, so Back right
     * after the jump returns to it with its time. No-op on the current step or outside the round.
     */
    fun jumpTo(s: TimerSnapshot, stepIndex: Int): TimerSnapshot {
        if (s.status == TimerStatus.ENDED || stepIndex !in s.plan.steps.indices || stepIndex == s.stepIndex) return s
        val now = elapsed.elapsedMillis()
        val undo = undoPoint(s, now)
        return autoPickLastHero(s.moveTo(round = s.round, index = stepIndex, startedAt = now)).copy(undo = undo)
    }

    private fun undoPoint(s: TimerSnapshot, now: Long) = UndoPoint(
        round = s.round,
        stepIndex = s.stepIndex,
        status = s.status,
        accumulatedMillis = elapsedInStep(s, now),
        actors = s.actors,
        timeOutsUsed = s.timeOutsUsed,
    )

    /**
     * Within [UNDO_WINDOW_MS] of step time after a Next, restores the step that Next left, with
     * its time continuing as if Next had never been tapped. Otherwise moves to the previous step
     * (across round boundaries) with a fresh timer; on the very first step it restarts it.
     */
    fun back(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val now = elapsed.elapsedMillis()
        val undo = s.undo
        if (undo != null && elapsedInStep(s, now) <= UNDO_WINDOW_MS && undo.stepIndex in s.plan.steps.indices) {
            return undoNext(s, undo, now)
        }
        return when {
            s.stepIndex > 0 -> s.moveTo(round = s.round, index = s.stepIndex - 1, startedAt = now)
            s.round > 1 -> s.moveTo(round = s.round - 1, index = s.plan.lastIndex, startedAt = now)
            else -> s.moveTo(round = 1, index = 0, startedAt = now)
        }
    }

    private fun undoNext(s: TimerSnapshot, undo: UndoPoint, now: Long): TimerSnapshot {
        // Time the new step has run counts for the restored step too, unless that one was paused.
        val carried = if (undo.status == TimerStatus.RUNNING) elapsedInStep(s, now) else 0L
        val status = if (undo.status == TimerStatus.PAUSED) TimerStatus.PAUSED else s.status
        return s.stamp(now).copy(
            round = undo.round,
            stepIndex = undo.stepIndex,
            status = status,
            accumulatedMillis = undo.accumulatedMillis + carried,
            runningSince = if (status == TimerStatus.RUNNING) now else null,
            actors = undo.actors,
            timeOutsUsed = undo.timeOutsUsed,
            stepEnteredAt = now,
            undo = null,
            shotClock = null,
        )
    }

    fun restartStep(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            accumulatedMillis = 0,
            runningSince = if (s.status == TimerStatus.RUNNING) now else null,
            undo = null,
        )
    }

    /** Jumps to the first end-of-round step; no-op when already in the end of round. */
    fun skipToEndOfRound(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val target = s.plan.firstEndOfRoundIndex
        if (target > s.plan.lastIndex || s.stepIndex >= target) return s
        return s.moveTo(round = s.round, index = target, startedAt = elapsed.elapsedMillis())
    }

    fun endMatch(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            status = TimerStatus.ENDED,
            accumulatedMillis = elapsedInStep(s, now),
            runningSince = null,
            endedElapsed = now,
            undo = null,
            shotClock = null,
        )
    }

    /**
     * With auto-advance on, moves past every timed step whose countdown has run out. Each new
     * step starts exactly when the previous one hit zero, so no drift accumulates. Stops at the
     * first untimed step (e.g. a checklist step), which always needs a tap.
     */
    fun advanceDue(s: TimerSnapshot): TimerSnapshot {
        if (!s.preset.autoAdvance || s.status != TimerStatus.RUNNING) return s
        val now = elapsed.elapsedMillis()
        var current = s
        repeat(MAX_AUTO_STEPS) {
            val duration = current.step.durationMillis ?: return current
            val end = current.runningSince!! - current.accumulatedMillis + duration
            if (now < end) return current
            current = if (current.stepIndex >= current.plan.lastIndex) {
                current.moveTo(round = current.round + 1, index = 0, startedAt = end)
            } else {
                current.moveTo(round = current.round, index = current.stepIndex + 1, startedAt = end)
            }.stamp(now)
        }
        return current
    }

    /**
     * Records the hero [seat] resolving now during an Action phase. If the current slot already
     * has a hero, moves to the next Action slot of the same turn first. Naming the first hero of
     * a slot starts that slot's countdown over, so every hero gets the full time from the moment
     * they start resolving (working out who acts first doesn't eat into their time).
     */
    fun heroResolves(s: TimerSnapshot, seat: Int): TimerSnapshot {
        val slot = s.step.actionSlot
        if (s.status == TimerStatus.ENDED || !s.step.isAction || slot == null) return s
        if (s.actors.none { it.slot == slot }) {
            val now = elapsed.elapsedMillis()
            return s.stamp(now).copy(
                actors = s.actors + ActionActor(slot, seat),
                accumulatedMillis = 0,
                runningSince = if (s.status == TimerStatus.RUNNING) now else null,
            )
        }
        val moved = next(s)
        if (moved === s) return s
        val nextSlot = moved.step.actionSlot
        return if (nextSlot != null && sameTurn(s, moved)) {
            moved.copy(actors = moved.actors.filterNot { it.slot == nextSlot } + ActionActor(nextSlot, seat))
        } else {
            moved
        }
    }

    private fun sameTurn(a: TimerSnapshot, b: TimerSnapshot): Boolean =
        b.step.isAction && a.round == b.round && a.step.turn == b.step.turn

    /**
     * On the last Action slot of a turn, when every earlier slot has its hero recorded, the one
     * hero left is the one resolving now.
     */
    private fun autoPickLastHero(s: TimerSnapshot): TimerSnapshot {
        val slot = s.step.actionSlot ?: return s
        if (!s.step.isAction || slot != s.actionSlots - 1 || s.actors.any { it.slot == slot }) return s
        val earlier = s.actors.filter { it.slot < slot }
        if (earlier.map { it.slot }.toSet() != (0 until slot).toSet()) return s
        val left = (0 until s.actionSlots).toSet() - earlier.map { it.seat }.toSet()
        return if (left.size == 1) s.copy(actors = s.actors + ActionActor(slot, left.single())) else s
    }

    /**
     * Records the Tie Breaker coin being flipped at the table (cross-team initiative tie). The
     * side must be known first ([setTieBreaker]); an untracked coin is left alone.
     */
    fun flipTieBreaker(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val team = s.tieBreaker ?: return s
        return s.copy(tieBreaker = team.opponent)
    }

    /** Sets which team's symbol shows on the Tie Breaker coin (start tracking, or correct it). */
    fun setTieBreaker(s: TimerSnapshot, team: Team?): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        return s.copy(tieBreaker = team)
    }

    /** Starts (or restarts) the preset's "Hurry up" shot clock; no-op if the preset has none. */
    fun startShotClock(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val seconds = s.preset.shotClockSeconds ?: return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            shotClock = ShotClock(
                durationMillis = seconds * MILLIS_PER_SECOND,
                runningSince = if (s.status == TimerStatus.RUNNING) now else null,
            ),
        )
    }

    fun cancelShotClock(s: TimerSnapshot): TimerSnapshot =
        if (s.shotClock == null || s.status == TimerStatus.ENDED) s else s.copy(shotClock = null)

    /** Time-outs [team] may still call this round. */
    fun timeOutsLeft(s: TimerSnapshot, team: Team): Int =
        (s.preset.timeOutsPerRound - (s.timeOutsUsed[team] ?: 0)).coerceAtLeast(0)

    /**
     * [team] calls a time-out: the current timed step gets the preset's time-out seconds added
     * (also out of overtime). Ignored on untimed steps or when the team has none left this round.
     */
    fun timeOut(s: TimerSnapshot, team: Team): TimerSnapshot {
        if (s.status == TimerStatus.ENDED || !s.step.timed || timeOutsLeft(s, team) <= 0) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            accumulatedMillis = s.accumulatedMillis - s.preset.timeOutSeconds * MILLIS_PER_SECOND,
            timeOutsUsed = s.timeOutsUsed + (team to (s.timeOutsUsed[team] ?: 0) + 1),
            undo = null,
        )
    }

    private fun elapsedInStep(s: TimerSnapshot, now: Long): Long = when (s.status) {
        TimerStatus.RUNNING -> s.accumulatedMillis + (now - s.runningSince!!)
        TimerStatus.PAUSED, TimerStatus.ENDED -> s.accumulatedMillis
    }

    private fun shotElapsed(c: ShotClock, now: Long): Long =
        c.accumulatedMillis + (c.runningSince?.let { now - it } ?: 0L)

    private fun TimerSnapshot.stamp(now: Long): TimerSnapshot = copy(anchorElapsed = now, anchorWall = wallAt(now))

    /** Wall time corresponding to elapsed [at], based on the current clock pair. */
    private fun wallAt(at: Long): Long = wall.nowMillis() - (elapsed.elapsedMillis() - at)

    private fun TimerSnapshot.moveTo(round: Int, index: Int, startedAt: Long): TimerSnapshot {
        val target = plan.steps[index]
        val sameTurn = round == this.round && target.turn != null && target.turn == step.turn
        return stamp(startedAt).copy(
            round = round,
            stepIndex = index,
            status = TimerStatus.RUNNING,
            runningSince = startedAt,
            accumulatedMillis = 0,
            actors = if (sameTurn) actors else emptyList(),
            stepEnteredAt = startedAt,
            timeOutsUsed = if (round == this.round) timeOutsUsed else emptyMap(),
            undo = null,
            shotClock = null,
        )
    }

    /** Cue thresholds (seconds remaining) crossed while the step's elapsed time went from → to. */
    fun cuesCrossed(step: TimerStep, fromElapsedMillis: Long, toElapsedMillis: Long): List<Int> {
        val duration = step.durationMillis ?: return emptyList()
        if (toElapsedMillis <= fromElapsedMillis) return emptyList()
        return step.cues.distinct().sortedDescending().filter { cue ->
            val at = duration - cue * MILLIS_PER_SECOND
            at >= 0 && fromElapsedMillis < at && at <= toElapsedMillis
        }
    }

    companion object {
        private const val MAX_AUTO_STEPS = 10_000

        /** A user Next is ignored this soon after a step starts (double-tap guard). */
        const val NEXT_DEBOUNCE_MS = 500L

        /** Back undoes the last Next while the new step has run for at most this long. */
        const val UNDO_WINDOW_MS = 15_000L
    }
}
