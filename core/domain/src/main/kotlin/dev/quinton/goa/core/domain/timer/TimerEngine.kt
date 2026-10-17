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

/**
 * Pure state machine for the phase timer. Every operation takes a snapshot and returns the next
 * one; time comes from the injected clocks only. Sequence per round: see [TimerPlan].
 *
 * Navigation (next, back, skip) always leaves the timer running with a fresh step; restart keeps
 * the paused/running state. Ended sessions ignore every operation.
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
        )
    }

    fun pause(s: TimerSnapshot): TimerSnapshot {
        if (s.status != TimerStatus.RUNNING) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            status = TimerStatus.PAUSED,
            accumulatedMillis = elapsedInStep(s, now),
            runningSince = null,
        )
    }

    fun resume(s: TimerSnapshot): TimerSnapshot {
        if (s.status != TimerStatus.PAUSED) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(status = TimerStatus.RUNNING, runningSince = now)
    }

    fun togglePause(s: TimerSnapshot): TimerSnapshot = if (s.status == TimerStatus.RUNNING) pause(s) else resume(s)

    fun next(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val now = elapsed.elapsedMillis()
        return if (s.stepIndex >= s.plan.lastIndex) {
            s.moveTo(round = s.round + 1, index = 0, startedAt = now)
        } else {
            s.moveTo(round = s.round, index = s.stepIndex + 1, startedAt = now)
        }
    }

    /** Previous step (across round boundaries); on the very first step it restarts it. */
    fun back(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val now = elapsed.elapsedMillis()
        return when {
            s.stepIndex > 0 -> s.moveTo(round = s.round, index = s.stepIndex - 1, startedAt = now)
            s.round > 1 -> s.moveTo(round = s.round - 1, index = s.plan.lastIndex, startedAt = now)
            else -> s.moveTo(round = 1, index = 0, startedAt = now)
        }
    }

    fun restartStep(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        val now = elapsed.elapsedMillis()
        return s.stamp(now).copy(
            accumulatedMillis = 0,
            runningSince = if (s.status == TimerStatus.RUNNING) now else null,
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
     * has a hero, moves to the next Action slot of the same turn first.
     */
    fun heroResolves(s: TimerSnapshot, seat: Int): TimerSnapshot {
        val slot = s.step.actionSlot
        if (s.status == TimerStatus.ENDED || !s.step.isAction || slot == null) return s
        if (s.actors.none { it.slot == slot }) return s.copy(actors = s.actors + ActionActor(slot, seat))
        val moved = next(s)
        val nextSlot = moved.step.actionSlot
        return if (nextSlot != null && sameTurn(s, moved)) {
            moved.copy(actors = moved.actors.filterNot { it.slot == nextSlot } + ActionActor(nextSlot, seat))
        } else {
            moved
        }
    }

    private fun sameTurn(a: TimerSnapshot, b: TimerSnapshot): Boolean =
        b.step.isAction && a.round == b.round && a.step.turn == b.step.turn

    /** Records the Tie Breaker coin being flipped at the table (cross-team initiative tie). */
    fun flipTieBreaker(s: TimerSnapshot): TimerSnapshot {
        if (s.status == TimerStatus.ENDED) return s
        return s.copy(tieBreaker = (s.tieBreaker ?: Team.BLUE).opponent)
    }

    private fun elapsedInStep(s: TimerSnapshot, now: Long): Long = when (s.status) {
        TimerStatus.RUNNING -> s.accumulatedMillis + (now - s.runningSince!!)
        TimerStatus.PAUSED, TimerStatus.ENDED -> s.accumulatedMillis
    }

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
    }
}
